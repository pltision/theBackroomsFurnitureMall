package yee.pltision.brfurniture.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.neoforged.fml.ModLoadingIssue;
import yee.pltision.brfurniture.BrFurniture;
import yee.pltision.brfurniture.blocks.BlockWarning;

/**
 * "方块列表有问题"的提示界面。
 *
 * <p>方块列表加载阶段的防御性编程保证了游戏不会崩，但玩家需要知道"哪些东西被跳过了"。
 * 这个界面把加载期间收集到的问题逐条列出来（带行号），并提示
 * "删除 config/brfurniture/blocks.txt 可能解决问题"。</p>
 *
 * <p>它是通过 NeoForge 的 {@code IConfigScreenFactory} 扩展点挂上去的（见
 * {@link BrFurnitureClient}），所以入口是"模组列表 → 后室家具店 → 配置"。
 * 之所以不用 {@code ConfigurationScreen} 的警告栏，是因为 21.1 的
 * {@code ConfigurationScreen} 没有公开的 <i>插入自定义警告</i> API，
 * 硬挤进去要依赖内部类；而 {@code IConfigScreenFactory} 是官方公开、稳定的接口。</p>
 *
 * <p>配色规则：整个文件级别的问题（读不出来、格式坏了）用红色，某一行的内容问题用普通色。</p>
 */
public class BlockWarningsScreen extends Screen {
    private static final int ROW_HEIGHT = 12;
    private static final int PADDING = 12;
    private static final int BUTTON_HEIGHT = 20;
    private static final int COLOR_TITLE = 0xFFFF5555;
    private static final int COLOR_TEXT = 0xFFE0E0E0;
    private static final int COLOR_FILE_LEVEL = 0xFFFF8080;
    private static final int COLOR_HINT = 0xFFFFC14D;

    private final Screen parent;
    private final List<BlockWarning> warnings;

    /** 已经按界面宽度折好行的文本，每行带自己的颜色。 */
    private final List<Line> lines = new ArrayList<>();

    private int scrollOffset;
    private int maxScroll;
    private int textAreaTop;
    private int textAreaHeight;

    public BlockWarningsScreen(Screen parent, List<BlockWarning> warnings) {
        super(Component.translatable("brfurniture.warningscreen.title"));
        this.parent = parent;
        this.warnings = List.copyOf(warnings);
    }

    @Override
    protected void init() {
        lines.clear();
        scrollOffset = 0;

        int textWidth = Math.max(80, width - 2 * PADDING - 8);
        addWrapped(Component.translatable("brfurniture.warningscreen.summary", warnings.size()), textWidth, COLOR_TEXT);

        for (BlockWarning warning : warnings) {
            ModLoadingIssue issue = warning.issue();
            Component rendered = Component.translatable(issue.translationKey(), issue.translationArgs().toArray());
            // 整个文件的问题用红色，单行的问题用普通色。
            int color = warning.internal() ? COLOR_FILE_LEVEL : COLOR_TEXT;
            addWrapped(Component.literal("- ").append(rendered), textWidth, color);
            // 行号 + 原文，方便玩家直接定位到配置文件里的那一行。
            String source = warning.describeSource();
            if (!source.isEmpty()) {
                addWrapped(Component.literal("    " + source), textWidth, 0xFFA0A0A0);
            }
        }

        lines.add(new Line(FormattedCharSequence.EMPTY, COLOR_TEXT));
        addWrapped(Component.translatable("brfurniture.modloadingissue.blocks.hint"), textWidth, COLOR_HINT);

        int buttonsTop = height - BUTTON_HEIGHT - PADDING;
        textAreaTop = PADDING + ROW_HEIGHT + 6;
        textAreaHeight = Math.max(ROW_HEIGHT, buttonsTop - 8 - textAreaTop);
        maxScroll = Math.max(0, lines.size() * ROW_HEIGHT - textAreaHeight);

        int buttonWidth = Math.min(160, Math.max(100, (width - 3 * PADDING) / 2));
        int totalWidth = buttonWidth * 2 + PADDING;
        int left = width / 2 - totalWidth / 2;
        addRenderableWidget(Button.builder(Component.translatable("brfurniture.warningscreen.continue"), button -> onClose())
                .bounds(left, buttonsTop, buttonWidth, BUTTON_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("brfurniture.warningscreen.open_config"), button -> openConfigFolder())
                .bounds(left + buttonWidth + PADDING, buttonsTop, buttonWidth, BUTTON_HEIGHT)
                .build());
    }

    private void addWrapped(Component text, int maxWidth, int color) {
        for (FormattedCharSequence line : font.split(text, maxWidth)) {
            lines.add(new Line(line, color));
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        // 半透明底板，保证在模糊背景上也读得清。
        graphics.fill(PADDING / 2, PADDING / 2, width - PADDING / 2, height - PADDING / 2, 0xC0000000);

        graphics.drawCenteredString(font, title, width / 2, PADDING, COLOR_TITLE);

        int firstVisible = scrollOffset / ROW_HEIGHT;
        int lastVisible = Math.min(lines.size(), firstVisible + textAreaHeight / ROW_HEIGHT + 1);
        int y = textAreaTop - scrollOffset % ROW_HEIGHT;
        for (int i = firstVisible; i < lastVisible; i++) {
            Line line = lines.get(i);
            graphics.drawString(font, line.text(), PADDING + 4, y, line.color(), false);
            y += ROW_HEIGHT;
        }

        // 滚动条，只在内容超出一屏时画。
        if (maxScroll > 0) {
            int trackTop = textAreaTop;
            int trackHeight = textAreaHeight;
            int totalHeight = lines.size() * ROW_HEIGHT;
            int barHeight = Math.max(16, trackHeight * trackHeight / totalHeight);
            int barTop = trackTop + (trackHeight - barHeight) * scrollOffset / maxScroll;
            graphics.fill(width - PADDING / 2 - 6, trackTop, width - PADDING / 2 - 3, trackTop + trackHeight, 0x60FFFFFF);
            graphics.fill(width - PADDING / 2 - 6, barTop, width - PADDING / 2 - 3, barTop + barHeight, 0xC0FFFFFF);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (maxScroll > 0) {
            scrollOffset = Mth.clamp((int) (scrollOffset - scrollY * ROW_HEIGHT * 2), 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // 上下 / 翻页 / Home / End，键盘也能看完整列表。
        switch (keyCode) {
            case org.lwjgl.glfw.GLFW.GLFW_KEY_DOWN -> {
                scrollOffset = Mth.clamp(scrollOffset + ROW_HEIGHT, 0, maxScroll);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_UP -> {
                scrollOffset = Mth.clamp(scrollOffset - ROW_HEIGHT, 0, maxScroll);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN -> {
                scrollOffset = Mth.clamp(scrollOffset + textAreaHeight, 0, maxScroll);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP -> {
                scrollOffset = Mth.clamp(scrollOffset - textAreaHeight, 0, maxScroll);
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_END -> {
                scrollOffset = maxScroll;
                return true;
            }
            case org.lwjgl.glfw.GLFW.GLFW_KEY_HOME -> {
                scrollOffset = 0;
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    /** 打开 {@code config/brfurniture} 目录，方便玩家直接删掉 blocks.txt。 */
    private void openConfigFolder() {
        try {
            var directory = BrFurniture.blockListFile().getParent();
            net.minecraft.Util.getPlatform().openUri(directory.toUri());
        } catch (RuntimeException e) {
            BrFurniture.LOGGER.warn("[BrFM] 打开配置目录失败", e);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.setScreen(parent);
        }
    }

    /** 一行文本 + 它的颜色。 */
    private record Line(FormattedCharSequence text, int color) {}
}
