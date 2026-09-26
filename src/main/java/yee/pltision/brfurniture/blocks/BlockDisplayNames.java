package yee.pltision.brfurniture.blocks;

import yee.pltision.brfurniture.codegen.DefaultBlockIds;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 方块展示名的"基础名"查表 + 推导。
 *
 * <p>基础名是"一个贴图叫什么"，不含变种后缀；语言文件里的最终名字由
 * {@code 基础名 + BlockVariants 的后缀} 拼出来（见 datagen 的 {@code BlockLanguageProvider}）。</p>
 *
 * <p>数据来源：{@code src/codegen/block_names.properties}，由 genBlockList 模块读进去、
 * 写进 {@link DefaultBlockIds}。这样一来，加一个贴图只需要改 properties 再跑构建，
 * 中英文的键结构完全一致。</p>
 */
public final class BlockDisplayNames {
    /** 推导英文名时的词表，和 genBlockList 里的保持一致；命不中就退化成 Title Case。 */
    private static final Map<String, String> WORD_REPLACEMENTS = Map.ofEntries(
            Map.entry("moss", "Mossy"),
            Map.entry("cracking", "Cracking"),
            Map.entry("rubble", "Rubble"),
            Map.entry("chara", "Chair"));

    /**
     * 结尾是数字的"编号"。{@code (?<=[a-z])} 后顾断言保证编号前面是字母，
     * 否则 {@code level0} 会被切成 {@code level + 0}，推导出 "Level 0"。
     * 这个正则必须和 genBlockList 里的 {@code BlockNameResolver#TRAILING_INDEX} 保持一致。
     */
    private static final Pattern TRAILING_INDEX = Pattern.compile("^(.*)(?<=[a-z])[_-](\\d+)$");

    private BlockDisplayNames() {}

    /**
     * {@return 某个方块 id 的基础中文名}。没登记就退回英文推导结果，
     * 免得语言文件里出现空的展示名。
     */
    public static String zhCn(String path) {
        String name = DefaultBlockIds.BASE_NAMES_ZH_CN.get(path);
        return name != null ? name : deriveEnglish(path);
    }

    /**
     * {@return 某个方块 id 的基础英文名}。没登记就按 id 推导。
     */
    public static String enUs(String path) {
        String name = DefaultBlockIds.BASE_NAMES_EN_US.get(path);
        return name != null ? name : deriveEnglish(path);
    }

    /**
     * {@code level0_wall -> Level0 Wall}、{@code cracking_concrete_1 -> Cracking Concrete 1}。
     *
     * <p>方块 id 可以带斜杠（对应贴图子目录），按 {@code /} 拆段分别推导再拼起来：
     * {@code level0/wall -> Level0 Wall}。这与 genBlockList 的 {@code BlockNameResolver#deriveEnglish}
     * 是同一套规则——这里只是 {@code DefaultBlockIds} 没有该 id 时的兜底，
     * 两边规则不一致会让"生成的名字"和"运行时的名字"对不上。</p>
     */
    public static String deriveEnglish(String path) {
        String normalized = path.replace('-', '_').toLowerCase(Locale.ROOT);

        StringBuilder builder = new StringBuilder();
        for (String segment : normalized.split("/+")) {
            if (segment.isEmpty()) {
                continue;
            }
            String words = deriveSegment(segment);
            if (words.isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(words);
        }
        return builder.length() == 0 ? path : builder.toString();
    }

    /** 推导单段路径（不含斜杠）的英文名。 */
    private static String deriveSegment(String segment) {
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
            if (builder.length() > 0) {
                builder.append(' ');
            }
            String replacement = WORD_REPLACEMENTS.get(word);
            builder.append(replacement != null ? replacement
                    : Character.toUpperCase(word.charAt(0)) + word.substring(1));
        }
        if (!index.isEmpty()) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(index);
        }
        return builder.toString();
    }

    /**
     * 某个方块 + 某个形态的最终展示名。
     *
     * @param path    基础 id
     * @param variant 形态
     * @param zhCn    true 取中文，false 取英文
     */
    public static String name(String path, BlockVariants variant, boolean zhCn) {
        return (zhCn ? zhCn(path) : enUs(path)) + (zhCn ? variant.zhCnSuffix() : variant.enUsSuffix());
    }
}
