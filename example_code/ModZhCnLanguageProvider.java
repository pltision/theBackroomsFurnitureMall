package com.tbbk.datagen;

import com.tbbk.Tbbk;
import net.minecraft.data.PackOutput;
import net.neoforged.neoforge.common.data.LanguageProvider;

public class ModZhCnLanguageProvider extends LanguageProvider {
    public ModZhCnLanguageProvider(PackOutput output) {
        super(output, Tbbk.MODID, "zh_cn");
    }

    @Override
    protected void addTranslations() {
        add("block.tbbk.test_block", "测试方块");
        add("item.tbbk.test_item", "测试物品");
        add("resourcepack.tbbk.i10n", "后室破局：模组汉化");
        add("resourcepack.tbbk.i10n.description", "需要置于 Mod Resources 上方");
        // gen:lang-zh
        add("block.tbbk.gentest_block", "Gentest Block");
        add("item.tbbk.gentest_item", "Gentest Item");
        add("itemGroup.tbbk.tbbk_block_tab", "后室破局：方块");
        add("itemGroup.tbbk.tbbk_item_tab", "后室破局：物品");
    }
}
