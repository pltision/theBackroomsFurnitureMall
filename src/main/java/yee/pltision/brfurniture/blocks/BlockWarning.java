package yee.pltision.brfurniture.blocks;

import net.neoforged.fml.ModLoadingIssue;

import java.util.List;

/**
 * 一条"方块列表有问题"的记录。
 *
 * <p>用 {@link ModLoadingIssue} 而不是自己造一套，是因为它就是 NeoForge / FML 用来积攒
 * "加载期间的警告"的类型：它自带 {@code severity}、翻译键与参数，也带 {@code affectedPath}，
 * 界面上可以直接按语言文件渲染。</p>
 *
 * @param issue          交给 FML 的警告/错误对象
 * @param source         出问题的那一行原文，用来在界面上告诉玩家"是哪一行"
 * @param sourceLine     行号，{@code 100+} 的哨兵值表示"不是某一行的错"（例如整个文件读不出来）
 * @param internal       true = 文件本身的问题（读不了、格式不对），false = 文件内容与某个方块 id 有关
 */
public record BlockWarning(ModLoadingIssue issue, String source, int sourceLine, boolean internal) {
    public static final int NO_LINE = 100;

    public BlockWarning {
        source = source == null ? "" : source;
    }

    public static BlockWarning create(ModLoadingIssue issue, String source, int sourceLine, boolean internal) {
        return new BlockWarning(issue, source, sourceLine, internal);
    }

    /** {@return 带行号的原文，形如 {@code 12: level0_wall}}，没有行号时只返回原文 */
    public String describeSource() {
        if (source.isEmpty()) {
            return "";
        }
        return sourceLine == NO_LINE ? source : sourceLine + ": " + source;
    }

    /** {@return 这条警告涉及的所有译文参数，方便统一渲染 */
    public List<Object> arguments() {
        return issue.translationArgs();
    }
}
