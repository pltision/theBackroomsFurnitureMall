package yee.pltision.brfurniture.blocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;
import yee.pltision.brfurniture.BrFurniture;

import java.nio.file.Files;
import java.util.*;
import java.util.function.Function;

/**
 * 把「方块列表」变成真正注册的方块。
 *
 * <p>主类在构造时通过 {@link #putBlock} / {@link #putExhibitionWall} / {@link #putExhibitionWallBrace}
 * 声明"这个 id 用哪个方块类、哪套属性"，这里再统一注册。没声明的 id 直接用默认值，
 * 所以加一个贴图不需要动任何 Java 代码。</p>
 *
 * <h2>防御性编程</h2>
 * <p>配置文件在这个阶段可能包含：无效的资源路径、内部重名、和已有注册名冲突、
 * 以及资源包层面的问题（id 过长等）。这里的原则是<b>能跳过就跳过、绝不抛异常</b>，
 * 把过程中发现的问题收集成 {@link BlockWarning} 交给客户端提示玩家。</p>
 */
public final class BlockManager {
    /** 全局单例，供主类与 datagen 使用。 */
    public static final BlockManager INSTANCE = new BlockManager();

    /** id 长度上限，超了就跳过：Minecraft 的路径本身允许更长，但那种名字一定是写错了。 */
    private static final int MAX_ID_LENGTH = 200;

    /** 默认属性：和约定的一样，木质、踩上去是音符盒的贝斯音。 */
    private static final Function<BlockBehaviour.Properties, BlockBehaviour.Properties> DEFAULT_PROPERTIES =
            properties -> properties
                    .instrument(NoteBlockInstrument.BASS)
                    .strength(2.0F, 3.0F)
                    .sound(SoundType.WOOD);

    private final Map<String, Function<BlockBehaviour.Properties, ? extends Block>> solidFactories = new LinkedHashMap<>();
    private final Map<String, BlockBehaviour.Properties> solidProperties = new LinkedHashMap<>();
    private final Map<String, Function<BlockBehaviour.Properties, ? extends Block>> exhibitionWallFactories = new LinkedHashMap<>();
    private final Map<String, BlockBehaviour.Properties> exhibitionWallProperties = new LinkedHashMap<>();
    private final Map<String, Function<BlockBehaviour.Properties, ? extends Block>> exhibitionWallBraceFactories = new LinkedHashMap<>();
    private final Map<String, BlockBehaviour.Properties> exhibitionWallBraceProperties = new LinkedHashMap<>();

    private final List<BlockWarning> warnings = new ArrayList<>();

    private BlockListConfig config;
    private BlockListConfig.BlockList blockList;
    private DeferredRegister.Blocks blockRegister;
    private DeferredRegister.Items itemRegister;
    private List<ModBlockRegistry> registered = List.of();
    private boolean registeredOnce;

    private BlockManager() {}

    // ------------------------------------------------------------------
    // 声明阶段：在主类构造里调用
    // ------------------------------------------------------------------

    /**
     * 覆盖 id 对应的实心方块。需要在 {@link #register()} 之前调用。
     *
     * <pre>{@code
     * putBlock("level0_wall", Block::new);
     * putBlock("level0_wall", BlockBehaviour.Properties.of().strength(4.0F));
     * // 或者一次给完：
     * putBlock("level0_wall", Block::new, BlockBehaviour.Properties.of().strength(4.0F));
     * }</pre>
     */
    public void putBlock(String id, Function<BlockBehaviour.Properties, ? extends Block> factory) {
        solidFactories.put(requireId(id), Objects.requireNonNull(factory, "factory"));
    }

    /** 覆盖 id 对应实心方块的属性。 */
    public void putBlock(String id, BlockBehaviour.Properties properties) {
        solidProperties.put(requireId(id), Objects.requireNonNull(properties, "properties"));
    }

    /** 一次覆盖 id 对应实心方块的方块类与属性。 */
    public void putBlock(String id, Function<BlockBehaviour.Properties, ? extends Block> factory, BlockBehaviour.Properties properties) {
        putBlock(id, factory);
        putBlock(id, properties);
    }

    /** 覆盖 id 对应的展墙方块。 */
    public void putExhibitionWall(String id, Function<BlockBehaviour.Properties, ? extends Block> factory) {
        exhibitionWallFactories.put(requireId(id), Objects.requireNonNull(factory, "factory"));
    }

    /** 覆盖 id 对应的展墙属性。 */
    public void putExhibitionWall(String id, BlockBehaviour.Properties properties) {
        exhibitionWallProperties.put(requireId(id), Objects.requireNonNull(properties, "properties"));
    }

    /** 一次覆盖 id 对应的展墙方块类与属性。 */
    public void putExhibitionWall(String id, Function<BlockBehaviour.Properties, ? extends Block> factory, BlockBehaviour.Properties properties) {
        putExhibitionWall(id, factory);
        putExhibitionWall(id, properties);
    }

    /** 覆盖 id 对应的展墙墙根方块。 */
    public void putExhibitionWallBrace(String id, Function<BlockBehaviour.Properties, ? extends Block> factory) {
        exhibitionWallBraceFactories.put(requireId(id), Objects.requireNonNull(factory, "factory"));
    }

    /** 覆盖 id 对应的展墙墙根属性。 */
    public void putExhibitionWallBrace(String id, BlockBehaviour.Properties properties) {
        exhibitionWallBraceProperties.put(requireId(id), Objects.requireNonNull(properties, "properties"));
    }

    /** 一次覆盖 id 对应的展墙墙根方块类与属性。 */
    public void putExhibitionWallBrace(String id, Function<BlockBehaviour.Properties, ? extends Block> factory, BlockBehaviour.Properties properties) {
        putExhibitionWallBrace(id, factory);
        putExhibitionWallBrace(id, properties);
    }

    // ------------------------------------------------------------------
    // 注册阶段
    // ------------------------------------------------------------------

    /**
     * 读取配置、创建 {@link DeferredRegister} 并完成注册。
     *
     * <p>整个过程不会抛异常：出问题只会变成警告。</p>
     */
    public void register() {
        if (registeredOnce) {
            throw new IllegalStateException("BlockManager 只能注册一次");
        }
        registeredOnce = true;

        Logger logger = BrFurniture.LOGGER;
        config = new BlockListConfig(logger, BrFurniture.configDirectory());
        blockList = config.read();
        warnings.addAll(blockList.warnings());

        logger.info("[BrFM] 方块列表来源：{}，有效 id {} 个",
                blockList.usedDefaults() ? "内置默认列表" : config.configFile(),
                blockList.entries().size());
        blockList.entries().forEach(entry -> logger.debug("[BrFM]   {}", entry.path()));

        blockRegister = DeferredRegister.createBlocks(BrFurniture.MODID);
        itemRegister = DeferredRegister.createItems(BrFurniture.MODID);

        List<ModBlockRegistry> result = new ArrayList<>(blockList.entries().size());
        for (BlockListEntry entry : blockList.entries()) {
            // 先检查贴图在不在、名字有没有被占，不合格的形态直接不注册：
            // 既避免 DeferredRegister 抛异常，也避免 datagen 因为找不到贴图整场失败。
            Validation validation = validate(entry);
            if (validation.allowed().isEmpty()) {
                // 贴图缺失时 validate 已经给出精确原因了，这里不再补一句笼统的。
                if (!validation.reasonReported()) {
                    logger.warn("[BrFM] 跳过方块 id {}：它所有形态的注册名都不可用", entry.path());
                }
                continue;
            }
            result.add(create(entry, validation.allowed()));
        }
        registered = List.copyOf(result);
        logger.info("[BrFM] 已注册 {} 个方块 id，共 {} 个方块",
                registered.size(), registered.stream().mapToInt(r -> r.blocks().size()).sum());

        if (!warnings.isEmpty()) {
            logger.warn("[BrFM] 方块列表发现了 {} 个问题，详情见警告界面或日志上方", warnings.size());
            warnings.forEach(warning -> logger.warn("[BrFM]   {}", warning.issue()));
        }
    }

    /** 把 DeferredRegister 挂到 mod 事件总线上，触发真正的注册。 */
    public void register(IEventBus modEventBus) {
        requireRegistered();
        blockRegister.register(modEventBus);
        itemRegister.register(modEventBus);
    }

    /**
     * 预检：每个形态的注册 id 是否可用。不可用的形态会被排除掉，并记警告。
     *
     * @param allowed        可以注册的形态
     * @param reasonReported true = 已经给出过精确原因（例如贴图缺失），
     *                       调用方不必再补一句笼统的"所有形态都不可用"
     */
    private record Validation(List<BlockVariants> allowed, boolean reasonReported) {}

    private Validation validate(BlockListEntry entry) {
        List<BlockVariants> allowed = new ArrayList<>(BlockVariants.values().length);

        // 贴图缺失意味着"整个 id 都不可用"，所以在形态循环之前统一查一次，
        // 同一个 id 只报一条警告。
        if (!textureExists(entry.path())) {
            BrFurniture.LOGGER.error("[BrFM] 跳过方块 id {}：贴图 {} 不存在（方块模型六个面都贴这张图）",
                    entry.path(), BlockVariants.texturePath(entry.path()) + ".png");
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.texture_missing",
                            entry.path(), BlockVariants.texturePath(entry.path())),
                    entry.sourceLine(), entry.lineNumber(), false));
            return new Validation(List.of(), true);
        }

        for (BlockVariants variant : BlockVariants.values()) {
            ResourceLocation id = entry.id(variant);

            // 方块的注册名有没有被原版或别的模组占用（本模组自己还没注册，所以不会误报自己）。
            if (BuiltInRegistries.BLOCK.containsKey(id)) {
                warnings.add(BlockWarning.create(
                        ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.registry_conflict", id.toString(), "block"),
                        entry.sourceLine(), entry.lineNumber(), false));
                continue;
            }
            // 物品同理：方块物品注册在 ITEM 注册表里，同名物品冲突同样会让注册失败。
            if (BuiltInRegistries.ITEM.containsKey(id)) {
                warnings.add(BlockWarning.create(
                        ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.registry_conflict", id.toString(), "item"),
                        entry.sourceLine(), entry.lineNumber(), false));
                continue;
            }
            if (id.getPath().length() > MAX_ID_LENGTH) {
                warnings.add(BlockWarning.create(
                        ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.id_too_long", id.toString(), id.getPath().length(), MAX_ID_LENGTH),
                        entry.sourceLine(), entry.lineNumber(), false));
                continue;
            }
            allowed.add(variant);
        }
        return new Validation(allowed, false);
    }

    /**
     * 检查这个 id 的贴图在不在模组资源里。
     *
     * <p>为什么要在注册阶段查：方块模型是"六面都贴 {@code brfurniture:block/<id>}"，
     * 贴图不存在时 datagen 会在注册完所有方块之后才抛
     * {@code Texture ... does not exist in any known resource pack}，
     * 错误信息指不到 {@code blocks.txt} 的哪一行，整个 runData 直接失败。
     * 提前查出来就能给出行号和缺哪个文件，并把它降级成"跳过这个 id"。</p>
     *
     * <p>"查不了"和"查到了、不存在"必须区分开：查不了（拿不到模组文件信息、
     * 或者查找过程抛异常）时返回 {@code true} 并打日志，宁可放过一个坏 id，
     * 也不能因为环境差异把好方块全跳掉——但一定要留日志，否则保护会静默失效。</p>
     */
    private static boolean textureExists(String path) {
        var modFileInfo = ModList.get().getModFileById(BrFurniture.MODID);
        if (modFileInfo == null) {
            BrFurniture.LOGGER.warn("[BrFM] 拿不到模组文件信息，跳过贴图存在性检查（id={}）", path);
            return true;
        }
        try {
            // findResource 的路径相对模组根目录，所以要带上 assets/<modid>/ 前缀。
            String resource = "assets/" + BrFurniture.MODID + "/textures/"
                    + BlockVariants.texturePath(path) + ".png";
            return Files.isRegularFile(modFileInfo.getFile().findResource(resource));
        } catch (RuntimeException e) {
            BrFurniture.LOGGER.warn("[BrFM] 检查贴图 {} 时出错，按存在处理", path, e);
            return true;
        }
    }

    /** 真正创建方块与方块物品。 */
    private ModBlockRegistry create(BlockListEntry entry, List<BlockVariants> allowed) {
        Map<BlockVariants, DeferredBlock<? extends Block>> blocks = ModBlockRegistry.newBlockMap();
        Map<BlockVariants, DeferredItem<BlockItem>> items = ModBlockRegistry.newItemMap();

        String path = entry.path();
        for (BlockVariants variant : allowed) {
            Function<BlockBehaviour.Properties, ? extends Block> factory = switch (variant) {
                case SOLID -> solidFactories.getOrDefault(path, Block::new);
                case EXHIBITION_WALL -> exhibitionWallFactories.getOrDefault(path, ExhibitionWall::new);
                case EXHIBITION_WALL_BRACE -> exhibitionWallBraceFactories.getOrDefault(path, ExhibitionWallBrace::new);
            };
            BlockBehaviour.Properties properties = switch (variant) {
                case SOLID -> solidProperties.get(path);
                case EXHIBITION_WALL -> exhibitionWallProperties.get(path);
                case EXHIBITION_WALL_BRACE -> exhibitionWallBraceProperties.get(path);
            };
            if (properties == null) {
                properties = defaultProperties(variant);
            }

            DeferredBlock<? extends Block> block = blockRegister.registerBlock(variant.blockPath(path), factory, properties);
            blocks.put(variant, block);
            // 展墙/墙根也是"手持能放的方块"，用一个普通 BlockItem 就够了；
            // 它们的碰撞箱很薄，但物品形态就是普通方块物品。
            items.put(variant, itemRegister.registerSimpleBlockItem(block));
        }
        return new ModBlockRegistry(path, blocks, items, entry.lineNumber());
    }

    /**
     * 没被主类覆盖时使用的属性。
     *
     * <p>展墙和墙根额外加了 {@code noOcclusion}：它们是 1/16 厚的贴墙方块，
     * 不开这个会让相邻面被错误剔除。属性每次现造一份，避免多个方块共享同一个可变对象。</p>
     */
    private static BlockBehaviour.Properties defaultProperties(BlockVariants variant) {
        BlockBehaviour.Properties properties = DEFAULT_PROPERTIES.apply(BlockBehaviour.Properties.of());
        if (variant != BlockVariants.SOLID) {
            properties.noOcclusion();
        }
        return properties;
    }

    private static String requireId(String id) {
        Objects.requireNonNull(id, "id");
        String trimmed = id.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("方块 id 不能为空");
        }
        return trimmed;
    }

    private void requireRegistered() {
        if (!registeredOnce) {
            throw new IllegalStateException("BlockManager.register() 还没有被调用");
        }
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** {@return 已注册的方块，顺序和配置文件一致} */
    public List<ModBlockRegistry> registeredBlocks() {
        return registered;
    }

    public Optional<ModBlockRegistry> registeredBlock(String path) {
        return registered.stream().filter(entry -> entry.path().equals(path)).findFirst();
    }

    /** {@return 配置文件路径，供日志与界面显示 */
    public Optional<BlockListConfig> config() {
        return Optional.ofNullable(config);
    }

    public Optional<BlockListConfig.BlockList> blockList() {
        return Optional.ofNullable(blockList);
    }

    /** {@return 本次加载发现的所有问题，按发现顺序 */
    public List<BlockWarning> warnings() {
        return List.copyOf(warnings);
    }

    /** {@return 去掉重复翻译键+参数之后的问题列表，界面直接用它渲染 */
    public List<ModLoadingIssue> issues() {
        Map<String, ModLoadingIssue> unique = new LinkedHashMap<>();
        for (BlockWarning warning : warnings) {
            ModLoadingIssue issue = warning.issue();
            unique.putIfAbsent(issue.translationKey() + "|" + issue.translationArgs(), issue);
        }
        return List.copyOf(unique.values());
    }

    public boolean hasProblems() {
        return !warnings.isEmpty();
    }

    /** {@return 所有已注册形态的注册 id，调试与检查用 */
    public List<ResourceLocation> allBlockIds() {
        return registered.stream()
                .flatMap(entry -> entry.registeredVariants().stream().map(entry::id).flatMap(Optional::stream))
                .toList();
    }

    /** {@return 注册 id -> 方块物品 的映射，调试用 */
    public Map<ResourceLocation, Item> itemMap() {
        Map<ResourceLocation, Item> map = new LinkedHashMap<>();
        for (ModBlockRegistry entry : registered) {
            entry.blockItems().forEach((variant, item) -> map.put(item.getId(), item.get()));
        }
        return map;
    }
}
