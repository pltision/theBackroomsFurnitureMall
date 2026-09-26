package yee.pltision.brfurniture.blocks;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModLoadingIssue;
import yee.pltision.brfurniture.BrFurniture;
import yee.pltision.brfurniture.codegen.DefaultBlockIds;

/**
 * 读取 / 创建 {@code config/brfurniture/blocks.txt}。
 *
 * <p>设计前提：<b>这个阶段无论输入多离谱都不能让游戏崩溃</b>。所以：</p>
 * <ul>
 *     <li>文件不存在 -> 写一份默认文件（按"配置缺失就创建默认配置"的惯例），并且用内置列表；</li>
 *     <li>文件读不了 / 编码坏了 / 内容全是垃圾 -> 记一条警告，退回内置列表；</li>
 *     <li>单行有问题 -> 跳过这一行，继续读后面的行；</li>
 *     <li>所有异常都在这里被吃掉，只有"哪些东西有问题"会变成警告交给上层去提示玩家。</li>
 * </ul>
 *
 * <p>文件格式：一行一个方块 id（相对 {@code brfurniture} 命名空间，例如 {@code level0_wall}），
 * 空行与 {@code #} 开头的行会被忽略。</p>
 */
public final class BlockListConfig {
    /** 相对游戏目录的配置文件路径。 */
    public static final String CONFIG_RELATIVE_PATH = "brfurniture/blocks.txt";
    /** 配置文件大小上限：一个方块列表不该有 1 MiB，超过就当成写错了，避免读一个巨大的文件。 */
    private static final long MAX_FILE_SIZE = 1024L * 1024L;
    /** 行数上限，顺便给"格式写错导致文件爆炸"兜个底。 */
    private static final int MAX_LINES = 100_000;

    private static final String FILE_HEADER = """
            # 后室家具店 the Backrooms Furniture Mall - 方块列表
            # Backrooms Furniture block list
            #
            # 一行一个方块 id，相对于 brfurniture 命名空间，例如：
            #     level0_wall
            # 会生成 brfurniture:level0_wall、brfurniture:exhibition_wall/level0_wall、
            # brfurniture:exhibition_wall_brace/level0_wall 三个方块。
            #
            # 以 # 开头的行和空行会被忽略。
            # 删除本文件可以恢复内置的默认列表。
            """;

    private final Logger logger;
    private final Path configFile;

    public BlockListConfig(Logger logger, Path configDir) {
        this.logger = logger;
        this.configFile = configDir.resolve(CONFIG_RELATIVE_PATH);
    }

    public Path configFile() {
        return configFile;
    }

    /**
     * 读取方块列表。永远返回一个可用的结果：即使文件完全读不出来，也会退回内置默认列表。
     */
    public BlockList read() {
        List<BlockWarning> warnings = new ArrayList<>();

        boolean exists;
        try {
            exists = Files.isRegularFile(configFile);
        } catch (RuntimeException e) {
            // 极端的文件系统异常（权限、非法路径）也不该让游戏挂掉。
            logger.warn("[BrFM] 检查方块列表配置时出错：{}", configFile, e);
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.unexpected", String.valueOf(e)),
                    "", BlockWarning.NO_LINE, true));
            return BlockList.fallback(warnings);
        }

        if (!exists) {
            List<BlockListEntry> defaults = fallbackEntries();
            if (writeDefaultFile(defaults, warnings)) {
                logger.info("[BrFM] 没有找到方块列表配置，已创建默认配置 {}", configFile);
            }
            return new BlockList(defaults, warnings, true, false);
        }

        List<BlockListEntry> entries = readFile(warnings);
        if (entries.isEmpty()) {
            // 文件存在但没有任何有效行（被清空了、或者全是注释）：用默认列表，但绝不覆盖玩家写的文件。
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.empty"),
                    "", BlockWarning.NO_LINE, true));
            return new BlockList(fallbackEntries(), warnings, false, true);
        }
        return new BlockList(entries, warnings, false, false);
    }

    private List<BlockListEntry> readFile(List<BlockWarning> warnings) {
        String content;
        try {
            if (Files.size(configFile) > MAX_FILE_SIZE) {
                warnings.add(BlockWarning.create(
                        ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.too_large", MAX_FILE_SIZE),
                        "", BlockWarning.NO_LINE, true));
                return List.of();
            }
            content = decodeUtf8(Files.readAllBytes(configFile));
        } catch (IOException | RuntimeException e) {
            logger.warn("[BrFM] 读取方块列表配置失败，将使用内置默认列表：{}", configFile, e);
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.unreadable", configFile.toString())
                            .withCause(e),
                    "", BlockWarning.NO_LINE, true));
            return List.of();
        }

        String[] lines = content.split("\r\n|\n|\r", -1);
        Map<String, BlockListEntry> entries = new LinkedHashMap<>();
        for (int i = 0; i < lines.length && i < MAX_LINES; i++) {
            String raw = lines[i];
            String trimmed = raw.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            int lineNumber = i + 1;
            BlockListEntry entry = parseLine(trimmed, lineNumber, warnings);
            if (entry == null) {
                continue;
            }
            // 文件内部重名：保留第一次出现的位置，后面的跳过。
            if (entries.putIfAbsent(entry.path(), entry) != null) {
                warnings.add(BlockWarning.create(
                        ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.duplicate_id", entry.path()),
                        trimmed, lineNumber, false));
            }
        }
        logger.debug("[BrFM] readFile 结束：{} 行 -> {} 条有效 id", lines.length, entries.size());
        if (lines.length > MAX_LINES) {
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.too_many_lines", MAX_LINES),
                    "", BlockWarning.NO_LINE, true));
        }
        return List.copyOf(entries.values());
    }

    /**
     * 校验并解析一行。任何不合格的地方都返回 {@code null}（=跳过这一行），同时记一条警告。
     *
     * <p>写法有两种，含义完全相同：</p>
     * <ul>
     *     <li>{@code level0_wall} —— 相对 id，省略 {@code brfurniture:} 前缀（推荐，也是默认配置文件的写法）；</li>
     *     <li>{@code brfurniture:level0_wall} —— 全名。</li>
     * </ul>
     *
     * <p><b>注意不能直接把 {@code level0_wall} 丢给 {@code ResourceLocation.tryParse} 来判断命名空间</b>：
     * 没有冒号时原版会把它当成 {@code minecraft:level0_wall}，于是每一行都会被误判成
     * "不属于本模组"。所以这里先按冒号拆开，只在"确实写了命名空间"时才比较命名空间。</p>
     */
    private BlockListEntry parseLine(String line, int lineNumber, List<BlockWarning> warnings) {
        int colon = line.indexOf(ResourceLocation.NAMESPACE_SEPARATOR);
        String namespace = BrFurniture.MODID;
        String path = line;
        if (colon >= 0) {
            namespace = line.substring(0, colon);
            path = line.substring(colon + 1);
        }

        if (!ResourceLocation.isValidNamespace(namespace) || namespace.isEmpty()) {
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.invalid_id", line,
                            "命名空间 \"" + namespace + "\" 不合法"),
                    line, lineNumber, false));
            return null;
        }
        // 注意 isValidPath 对空字符串返回 true（它的实现是一个长度 0 的循环），
        // 所以 "brfurniture:" 这种"只有冒号"的写法必须自己挡掉。不挡的话
        // ResourceLocation.fromNamespaceAndPath 会造出路径为空的 id，
        // 后面注册阶段就会抛异常 —— 而"加载配置绝不能让游戏崩溃"是硬要求。
        if (path.isEmpty() || !ResourceLocation.isValidPath(path)) {
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.invalid_id", line,
                            describeInvalid(line)),
                    line, lineNumber, false));
            return null;
        }
        if (!BrFurniture.MODID.equals(namespace)) {
            // 允许写 "brfurniture:level0_wall"，但不允许指到别人的命名空间去。
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.foreign_namespace", line, namespace),
                    line, lineNumber, false));
            return null;
        }

        // 到这里 namespace 与 path 都已经通过原版的规则校验，所以这个构造不会抛异常；
        // 仍然用 try/catch 兜一下，保证"加载配置绝不会让游戏崩溃"这个前提。
        ResourceLocation id;
        try {
            id = ResourceLocation.fromNamespaceAndPath(namespace, path);
        } catch (RuntimeException e) {
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.invalid_id", line, String.valueOf(e.getMessage())),
                    line, lineNumber, false));
            return null;
        }

        logger.debug("[BrFM] 第 {} 行解析成功: {} -> {}", lineNumber, line, id);
        return new BlockListEntry(id.getPath(), line, lineNumber);
    }

    private static String describeInvalid(String line) {
        // 复用原版的规则，尽量说出"到底是哪一段不合法"。
        String path = line;
        int colon = line.indexOf(ResourceLocation.NAMESPACE_SEPARATOR);
        if (colon >= 0) {
            String namespace = line.substring(0, colon);
            path = line.substring(colon + 1);
            if (namespace.isEmpty() || !ResourceLocation.isValidNamespace(namespace)) {
                return "命名空间 \"" + namespace + "\" 不合法";
            }
        }
        if (path.isEmpty()) {
            return "路径为空（冒号后面什么都没有）";
        }
        if (!ResourceLocation.isValidPath(path)) {
            return "路径 \"" + path + "\" 不合法（只允许小写字母、数字、_ - . /）";
        }
        return "无法解析为资源路径";
    }

    /**
     * 把内置列表写成一份默认配置文件。写失败只记警告，不影响游戏启动。
     *
     * @return 是否真的写出了文件
     */
    private boolean writeDefaultFile(List<BlockListEntry> defaults, List<BlockWarning> warnings) {
        StringBuilder builder = new StringBuilder(FILE_HEADER);
        for (BlockListEntry entry : defaults) {
            builder.append('\n').append(entry.path());
        }
        builder.append('\n');
        try {
            Files.createDirectories(configFile.getParent());
            // 先写同目录下的临时文件再原子替换，避免写到一半崩溃留下半个配置文件。
            Path temp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
            Files.writeString(temp, builder.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
            try {
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicUnsupported) {
                Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException | RuntimeException e) {
            logger.warn("[BrFM] 创建默认方块列表配置失败，本次将直接使用内置列表：{}", configFile, e);
            warnings.add(BlockWarning.create(
                    ModLoadingIssue.warning("brfurniture.modloadingissue.blocks.create_failed", configFile.toString())
                            .withCause(e),
                    "", BlockWarning.NO_LINE, true));
            return false;
        }
    }

    /** 严格按 UTF-8 解码，坏字节直接报错而不是替换成 {@code ?}，并且顺手去掉 BOM。 */
    private static String decodeUtf8(byte[] bytes) throws CharacterCodingException {
        int offset = 0;
        if (bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB && (bytes[2] & 0xFF) == 0xBF) {
            offset = 3;
        }
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        return decoder.decode(java.nio.ByteBuffer.wrap(bytes, offset, bytes.length - offset)).toString();
    }

    /** 内置默认列表，按 id 排序，顺序稳定。 */
    private static List<BlockListEntry> fallbackEntries() {
        List<BlockListEntry> entries = new ArrayList<>(DefaultBlockIds.IDS.size());
        for (String id : DefaultBlockIds.IDS) {
            entries.add(new BlockListEntry(id, id, BlockWarning.NO_LINE));
        }
        return List.copyOf(entries);
    }

    /**
     * 加载结果。
     *
     * @param entries          有效的方块 id（顺序 = 玩家文件里的顺序，或内置列表顺序）
     * @param warnings         过程中积攒的问题，交给客户端提示玩家
     * @param usedDefaults     true = 这次用的是内置默认列表（配置缺失）
     * @param configWasEmpty   true = 配置文件存在但没有任何有效行
     */
    public record BlockList(List<BlockListEntry> entries, List<BlockWarning> warnings, boolean usedDefaults, boolean configWasEmpty) {
        static BlockList fallback(List<BlockWarning> warnings) {
            return new BlockList(fallbackEntries(), warnings, true, false);
        }

        public boolean hasProblems() {
            return !warnings.isEmpty();
        }
    }
}
