package yee.pltision.brfurniture.datagen;

import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;
import yee.pltision.brfurniture.BrFurniture;
import yee.pltision.brfurniture.blocks.BlockDisplayNames;
import yee.pltision.brfurniture.blocks.BlockManager;
import yee.pltision.brfurniture.blocks.BlockVariants;
import yee.pltision.brfurniture.blocks.ModBlockRegistry;

/**
 * 生成语言文件。
 *
 * <p>中英共用同一套逻辑（用继承复用代码），区别只有两个：</p>
 * <ul>
 *     <li>取哪一列基础名（{@code BASE_NAMES_ZH_CN} / {@code BASE_NAMES_EN_US}）；</li>
 *     <li>拼哪个后缀（中文 {@code 展墙}，英文 {@code  Exhibition Wall}，英文后缀自带空格）。</li>
 * </ul>
 *
 * <p>所以翻译条目的键结构永远是一样的：{@code block.brfurniture.<注册路径>}，
 * 例如 {@code block.brfurniture.level0_wall}、{@code block.brfurniture.exhibition_wall/level0_wall}。</p>
 */
public class BrLanguageProvider extends LanguageProvider {
    private final BlockManager blocks;
    private final boolean zhCn;

    public BrLanguageProvider(PackOutput output, String locale, boolean zhCn) {
        this(output, locale, zhCn, BlockManager.INSTANCE);
    }

    public BrLanguageProvider(PackOutput output, String locale, boolean zhCn, BlockManager blocks) {
        super(output, BrFurniture.MODID, locale);
        this.zhCn = zhCn;
        this.blocks = blocks;
    }

    @Override
    protected void addTranslations() {
        addStaticTranslations();
        addBlockTranslations();
    }

    /** 物品栏、配置界面、以及加载警告的文案。 */
    private void addStaticTranslations() {
        if (zhCn) {
            add("itemGroup.brfurniture.blocks", "后室家具店：方块");
            add("itemGroup.brfurniture.furniture", "后室家具店：家具");

            add("brfurniture.configuration.title", "后室家具店：配置");

            add("brfurniture.modloadingissue.blocks.unreadable", "后室家具店无法读取方块列表配置 %s，已回退到内置列表。");
            add("brfurniture.modloadingissue.blocks.invalid_id", "后室家具店跳过了无效的方块 ID：%s（%s）");
            add("brfurniture.modloadingissue.blocks.duplicate_id", "后室家具店跳过了重复的方块 ID：%s");
            add("brfurniture.modloadingissue.blocks.registry_conflict", "后室家具店跳过了已被占用的方块 ID：%s（%s 注册表里已有同名条目）");
            add("brfurniture.modloadingissue.blocks.foreign_namespace", "后室家具店跳过了不属于本模组的 ID：%s（命名空间是 %s）");
            add("brfurniture.modloadingissue.blocks.id_too_long", "后室家具店跳过了过长的方块 ID：%s（路径长度 %s，上限 %s）");
            add("brfurniture.modloadingissue.blocks.empty", "后室家具店的方块列表配置里没有任何有效行，已回退到内置列表。");
            add("brfurniture.modloadingissue.blocks.too_large", "后室家具店的方块列表配置超过 %s 字节，已忽略其内容并回退到内置列表。");
            add("brfurniture.modloadingissue.blocks.too_many_lines", "后室家具店的方块列表配置超过 %s 行，超出的部分已忽略。");
            add("brfurniture.modloadingissue.blocks.create_failed", "后室家具店无法创建默认的方块列表配置 %s，本次将直接使用内置列表。");
            add("brfurniture.modloadingissue.blocks.unexpected", "后室家具店读取方块列表时出现意外错误：%s");
            add("brfurniture.modloadingissue.blocks.hint", "删除 config/brfurniture/blocks.txt 可以恢复默认的方块列表，从而解决以上问题。");

            add("brfurniture.warningscreen.title", "后室家具店：方块列表有问题");
            add("brfurniture.warningscreen.summary", "有 %s 个问题，下面这些问题对应的方块已经被跳过：");
            add("brfurniture.warningscreen.continue", "我知道了，继续");
            add("brfurniture.warningscreen.open_config", "打开配置文件夹");
            add("brfurniture.warningscreen.chat_prefix", "[后室家具店] ");
            add("brfurniture.warningscreen.chat_count", "方块列表有 %s 个问题，详情可以在“模组列表 → 后室家具店 → 配置”里查看。");
        } else {
            add("itemGroup.brfurniture.blocks", "Backrooms Furniture: Blocks");
            add("itemGroup.brfurniture.furniture", "Backrooms Furniture: Furniture");

            add("brfurniture.configuration.title", "Backrooms Furniture Configs");

            add("brfurniture.modloadingissue.blocks.unreadable", "Backrooms Furniture could not read the block list config %s and fell back to the built-in list.");
            add("brfurniture.modloadingissue.blocks.invalid_id", "Backrooms Furniture skipped an invalid block id: %s (%s)");
            add("brfurniture.modloadingissue.blocks.duplicate_id", "Backrooms Furniture skipped a duplicated block id: %s");
            add("brfurniture.modloadingissue.blocks.registry_conflict", "Backrooms Furniture skipped an already occupied block id: %s (an entry with the same name exists in the %s registry)");
            add("brfurniture.modloadingissue.blocks.foreign_namespace", "Backrooms Furniture skipped an id that belongs to another namespace: %s (namespace is %s, expected brfurniture)");
            add("brfurniture.modloadingissue.blocks.id_too_long", "Backrooms Furniture skipped an overlong block id: %s (path length %s, limit %s)");
            add("brfurniture.modloadingissue.blocks.empty", "The block list config of Backrooms Furniture contains no usable line, so the built-in list was used.");
            add("brfurniture.modloadingissue.blocks.too_large", "The block list config of Backrooms Furniture is larger than %s bytes; its content was ignored and the built-in list was used.");
            add("brfurniture.modloadingissue.blocks.too_many_lines", "The block list config of Backrooms Furniture has more than %s lines; the extra lines were ignored.");
            add("brfurniture.modloadingissue.blocks.create_failed", "Backrooms Furniture could not create the default block list config %s; the built-in list will be used this time.");
            add("brfurniture.modloadingissue.blocks.unexpected", "Backrooms Furniture hit an unexpected error while reading the block list: %s");
            add("brfurniture.modloadingissue.blocks.hint", "Deleting config/brfurniture/blocks.txt restores the default block list and usually fixes the problems above.");

            add("brfurniture.warningscreen.title", "Backrooms Furniture: block list problems");
            add("brfurniture.warningscreen.summary", "%s problem(s) were found. The blocks listed below were skipped:");
            add("brfurniture.warningscreen.continue", "Got it, continue");
            add("brfurniture.warningscreen.open_config", "Open config folder");
            add("brfurniture.warningscreen.chat_prefix", "[Backrooms Furniture] ");
            add("brfurniture.warningscreen.chat_count", "The block list has %s problem(s). See \"Mods -> Backrooms Furniture -> Config\" for details.");
        }
    }

    /** 每个方块 id × 每个已注册形态 的展示名。 */
    private void addBlockTranslations() {
        for (ModBlockRegistry entry : blocks.registeredBlocks()) {
            for (BlockVariants variant : entry.registeredVariants()) {
                String path = entry.id(variant).orElseThrow().getPath();
                add("block.brfurniture." + path, BlockDisplayNames.name(entry.path(), variant, zhCn));
            }
        }
    }
}
