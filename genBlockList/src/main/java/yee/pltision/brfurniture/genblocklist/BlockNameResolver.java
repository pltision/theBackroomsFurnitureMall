/*
 * genBlockList 模块的一部分：不依赖 Minecraft / NeoForge。
 */
package yee.pltision.brfurniture.genblocklist;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 展示名：把一个方块 id 变成"基础名"，例如 {@code level0_wall -> Level0 Wall / Level0墙壁}。
 *
 * <p>基础名里<b>不包含</b>变种后缀（展墙、展墙墙根）。后缀由主模块在生成语言文件时拼接，
 * 所以这里只需要给出"这个贴图叫什么"。</p>
 */
public final class BlockNameResolver {
    /**
     * 推导英文名时使用的词表。名字里出现这些词就整体替换，避免出现 "Moss Concrete Rubble"。
     * 词表命不中就退化成 Title Case，所以新贴图不用改代码也能有像样的名字。
     */
    private static final Map<String, String> WORD_REPLACEMENTS = Map.ofEntries(
            Map.entry("moss", "Mossy"),
            Map.entry("mossy", "Mossy"),
            Map.entry("cracking", "Cracking"),
            Map.entry("cracked", "Cracked"),
            Map.entry("rubble", "Rubble"),
            Map.entry("chara", "Chair"),
            Map.entry("mycelium", "Mycelium"),
            Map.entry("carpet", "Carpet"),
            Map.entry("ceiling", "Ceiling"),
            Map.entry("lamp", "Lamp"),
            Map.entry("light", "Light"),
            Map.entry("pipe", "Pipe"),
            Map.entry("pipes", "Pipes"),
            Map.entry("wall", "Wall"),
            Map.entry("bricks", "Bricks"),
            Map.entry("brick", "Brick"),
            Map.entry("table", "Table"),
            Map.entry("chair", "Chair"),
            Map.entry("plank", "Plank"),
            Map.entry("can", "Can"),
            Map.entry("canned", "Canned"));

    /**
     * 结尾是数字的"编号"，例如 {@code cracking_concrete_1 -> cracking_concrete + 1}。
     *
     * <p>{@code (?<=[a-z])} 这个后顾断言是关键：编号前面必须是字母（也就是 {@code _1} / {@code -1} 里的
     * 字母），否则 {@code level0} 会被当成 {@code level + 0}，推导出 "Level 0" 而不是 "Level0"。
     * 换句话说 {@code level0} 是"词本身带数字"，不是编号。</p>
     */
    private static final Pattern TRAILING_INDEX = Pattern.compile("^(.*)(?<=[a-z])[_-](\\d+)$");

    private final Map<String, LocalizedName> explicitNames;

    private BlockNameResolver(Map<String, LocalizedName> explicitNames) {
        this.explicitNames = explicitNames;
    }

    /**
     * 读取人工维护的展示名表。文件不存在时返回空表（全部靠推导），不抛异常。
     *
     * <p>格式：{@code <方块id>=<中文>|<English>}，{@code #} 开头是注释。
     * 缺 {@code |} 或者两半有任意一半为空的行会被忽略并打印出来，让作者自己发现。</p>
     */
    public static BlockNameResolver loadDisplayNames(Path file) throws IOException {
        Map<String, LocalizedName> names = new LinkedHashMap<>();
        if (!Files.isRegularFile(file)) {
            System.out.println("[genBlockList] 没找到展示名表 " + file + "，全部按 id 自动推导");
            return new BlockNameResolver(Map.of());
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }

        for (String key : properties.stringPropertyNames()) {
            String id = key.trim();
            String value = properties.getProperty(key);
            int separator = value.indexOf('|');
            if (separator < 0) {
                System.out.println("[genBlockList] 忽略展示名表里的这一行（缺少 '|'）：" + key + "=" + value);
                continue;
            }
            String zhCn = value.substring(0, separator).trim();
            String enUs = value.substring(separator + 1).trim();
            if (id.isEmpty() || zhCn.isEmpty() || enUs.isEmpty()) {
                System.out.println("[genBlockList] 忽略展示名表里的这一行（id / 中文 / 英文有空值）：" + key + "=" + value);
                continue;
            }
            names.put(id, new LocalizedName(zhCn, enUs));
        }
        return new BlockNameResolver(Map.copyOf(names));
    }

    /**
     * 取一个方块 id 的基础名：优先用展示名表，没有就推导。
     *
     * @return 中英双语基础名，永远不为 {@code null}
     */
    public LocalizedName resolve(String id) {
        Objects.requireNonNull(id, "id");
        LocalizedName explicit = explicitNames.get(id);
        if (explicit != null) {
            return explicit;
        }
        return derive(id);
    }

    /** {@return 展示名表里显式给出的 id，用来提示作者"哪些是推导出来的"} */
    public boolean hasExplicitName(String id) {
        return explicitNames.containsKey(id);
    }

    /**
     * 按 id 推导一个中英双语基础名。中文没有可靠的推导方式，所以直接用英文名兜底，
     * 并把这件事交给展示名表去修正。
     */
    public static LocalizedName derive(String id) {
        return new LocalizedName(deriveEnglish(id), deriveEnglish(id));
    }

    /**
     * {@code level0_wall -> Level0 Wall}、{@code cracking_concrete_1 -> Cracking Concrete 1}、
     * {@code moss_concrete_rubble -> Mossy Concrete Rubble}。
     *
     * <p>方块 id 里可以带斜杠（对应贴图子目录），所以按 {@code /} 拆成几段分别推导再拼起来：
     * {@code level0/wall -> Level0 Wall}、{@code level0/ceiling_1 -> Level0 Ceiling 1}。
     * 不能直接把 {@code /} 换成 {@code _} 再整体处理——那样结尾编号会被整串当成一个词，
     * 推导结果会错。</p>
     */
    public static String deriveEnglish(String id) {
        String normalized = id.replace('-', '_').toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return id;
        }

        StringBuilder builder = new StringBuilder();
        for (String segment : normalized.split("/+")) {
            if (segment.isEmpty()) {
                continue;
            }
            String words = deriveSegment(segment);
            if (words.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(words);
        }
        return builder.isEmpty() ? id : builder.toString();
    }

    /** 推导单段路径（不含斜杠）的英文名。 */
    private static String deriveSegment(String segment) {
        // 先把结尾编号切下来，免得 index 影响后面的分词（cracking_concrete_1）。
        String index = "";
        Matcher matcher = TRAILING_INDEX.matcher(segment);
        if (matcher.matches() && !matcher.group(1).isEmpty()) {
            segment = matcher.group(1);
            index = matcher.group(2);
        }

        StringBuilder builder = new StringBuilder();
        for (String word : segment.split("_+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            String replacement = WORD_REPLACEMENTS.get(word);
            if (replacement != null) {
                builder.append(replacement);
            } else {
                builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }

        // level0 这种"单词里带数字"的写法已经在上面的分词里保留了原样，这里只补回结尾编号。
        if (!index.isEmpty()) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(index);
        }
        return builder.toString();
    }

    /** 一个方块 id 的基础名，不含变种后缀。 */
    public record LocalizedName(String zhCn, String enUs) {
        public LocalizedName {
            Objects.requireNonNull(zhCn, "zhCn");
            Objects.requireNonNull(enUs, "enUs");
        }
    }

    /** 给测试 / 调试用：列出一份 id 的推导结果。 */
    public static void main(String[] args) throws IOException {
        List<String> ids = BlockIdScanner.scan(Path.of(args.length > 0 ? args[0] : "."));
        BlockNameResolver resolver = loadDisplayNames(Path.of(args.length > 1 ? args[1] : "block_names.properties"));
        for (String id : ids) {
            System.out.println(id + " -> " + resolver.resolve(id));
        }
    }
}
