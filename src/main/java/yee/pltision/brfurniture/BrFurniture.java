package yee.pltision.brfurniture;

import com.mojang.logging.LogUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingIssue;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;
import yee.pltision.brfurniture.blocks.BlockListConfig;
import yee.pltision.brfurniture.blocks.BlockManager;
import yee.pltision.brfurniture.blocks.BlockWarning;
import yee.pltision.brfurniture.blocks.ModCreativeTabs;
import yee.pltision.brfurniture.datagen.BrDataGenerators;

import java.nio.file.Path;
import java.util.List;

/**
 * 后室家具店 Backrooms Furniture —— 主类 / 模组入口。
 *
 * <h2>这个模组做什么</h2>
 * <p>只添加方块与家具这类"资产"，不添加机制。当前只实现方块阶段。</p>
 *
 * <h2>方块是怎么来的</h2>
 * <p>所有方块都由「方块列表」驱动：</p>
 * <ol>
 *     <li>{@code :genBlockList} 扫描 {@code assets/brfurniture/textures/block/*.png}，
 *         生成 {@code yee.pltision.brfurniture.codegen.DefaultBlockIds}（硬编码的默认列表）；</li>
 *     <li>运行时读取 {@code config/brfurniture/blocks.txt}；文件不在就用默认列表并创建一份；</li>
 *     <li>列表里每个 id 生成"实心方块 / 展墙 / 展墙墙根"三种形态并注册。</li>
 * </ol>
 *
 * <h2>想改某个方块</h2>
 * <p>在构造方法里写：</p>
 * <pre>{@code
 * putBlock("level0_wall", Block::new, BlockBehaviour.Properties.of().strength(4.0F));
 * putExhibitionWall("level0_wall", BlockBehaviour.Properties.of().noOcclusion());
 * }</pre>
 * <p>没写的 id 一律用默认值（木质属性 + {@code Block::new} / {@code ExhibitionWall::new}），
 * 所以"加一个贴图"不需要改任何 Java 代码。</p>
 */
@Mod(BrFurniture.MODID)
public class BrFurniture {
    /** 模组 id。 */
    public static final String MODID = "brfurniture";

    /** 全局日志。 */
    public static final Logger LOGGER = LogUtils.getLogger();

    /**
     * 方块管理器。它在构造方法里完成"读取配置 + 注册方块"，
     * 之后 datagen 和创造物品栏都从这里取已注册的方块。
     */
    private static final BlockManager BLOCKS = BlockManager.INSTANCE;

    /**
     * 加载方块列表时发现的问题（原始记录，带来源行号）。
     *
     * <p>存成静态字段，是因为客户端要等到"打开模组列表"或者"进入世界"时才有机会提示玩家，
     * 那时候构造方法早就跑完了。</p>
     */
    private static List<BlockWarning> blockWarnings = List.of();

    public BrFurniture(IEventBus modEventBus, ModContainer modContainer) {
        // 1) 声明"哪些 id 用自定义实现"。想让某个方块用别的类或别的属性，就在这里覆盖。
        //    这里刻意把 15 个 id 全部走默认值，示范"不改代码也能出方块"。
        //
        //    注意：下面这几行是注释掉的示例，不是漏写。
        // putBlock("level0_wall", Block::new, BlockBehaviour.Properties.of());
        // putExhibitionWall("level0_wall", Block::new, BlockBehaviour.Properties.of());
        // putExhibitionWallBrace("level0_wall", Block::new, BlockBehaviour.Properties.of());

        // 2) 读配置 + 注册方块。这一步不会抛异常，出问题只会变成警告。
        BLOCKS.register();
        blockWarnings = BLOCKS.warnings();

        // 3) 把 DeferredRegister 挂上事件总线。
        BLOCKS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);

        // 4) datagen（只在 runData 时触发）。
        modEventBus.addListener(BrDataGenerators::gatherData);

        if (blockWarnings.isEmpty()) {
            LOGGER.info("[BrFM] 方块列表加载完成，没有发现问题");
        } else {
            LOGGER.warn("[BrFM] 方块列表加载完成，共 {} 个问题；客户端会在警告界面里提示，也可以删除 {} 恢复默认列表",
                    blockWarnings.size(), blockListFile());
        }
    }

    // ------------------------------------------------------------------
    // 给 datagen / 客户端 / 调试用的查询接口
    // ------------------------------------------------------------------

    /** {@return 方块管理器（只读用途）} */
    public static BlockManager blocks() {
        return BLOCKS;
    }

    /** {@return 方块列表加载时发现的问题（带来源行号），可能为空 */
    public static List<BlockWarning> blockWarnings() {
        return blockWarnings;
    }

    /** {@return 去重后的 FML 问题列表，直接给界面/日志用 */
    public static List<ModLoadingIssue> blockIssues() {
        return BLOCKS.issues();
    }

    /** {@return 配置文件所在目录（{@code config/}），必要时会创建 */
    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    /** {@return 方块列表配置文件的完整路径 */
    public static Path blockListFile() {
        return configDirectory().resolve(BlockListConfig.CONFIG_RELATIVE_PATH);
    }

    // ------------------------------------------------------------------
    // 覆盖方块实现的快捷方法（就是 BlockManager.INSTANCE 的转发）
    // ------------------------------------------------------------------

    /** @see BlockManager#putBlock(String, java.util.function.Function) */
    public static void putBlock(String id, java.util.function.Function<BlockBehaviour.Properties, ? extends Block> factory) {
        BLOCKS.putBlock(id, factory);
    }

    /** @see BlockManager#putBlock(String, BlockBehaviour.Properties) */
    public static void putBlock(String id, BlockBehaviour.Properties properties) {
        BLOCKS.putBlock(id, properties);
    }

    /** @see BlockManager#putBlock(String, java.util.function.Function, BlockBehaviour.Properties) */
    public static void putBlock(String id, java.util.function.Function<BlockBehaviour.Properties, ? extends Block> factory, BlockBehaviour.Properties properties) {
        BLOCKS.putBlock(id, factory, properties);
    }

    /** @see BlockManager#putExhibitionWall(String, java.util.function.Function) */
    public static void putExhibitionWall(String id, java.util.function.Function<BlockBehaviour.Properties, ? extends Block> factory) {
        BLOCKS.putExhibitionWall(id, factory);
    }

    /** @see BlockManager#putExhibitionWall(String, BlockBehaviour.Properties) */
    public static void putExhibitionWall(String id, BlockBehaviour.Properties properties) {
        BLOCKS.putExhibitionWall(id, properties);
    }

    /** @see BlockManager#putExhibitionWallBrace(String, java.util.function.Function) */
    public static void putExhibitionWallBrace(String id, java.util.function.Function<BlockBehaviour.Properties, ? extends Block> factory) {
        BLOCKS.putExhibitionWallBrace(id, factory);
    }

    /** @see BlockManager#putExhibitionWallBrace(String, BlockBehaviour.Properties) */
    public static void putExhibitionWallBrace(String id, BlockBehaviour.Properties properties) {
        BLOCKS.putExhibitionWallBrace(id, properties);
    }
}
