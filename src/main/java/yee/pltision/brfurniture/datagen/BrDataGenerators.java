package yee.pltision.brfurniture.datagen;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.data.event.GatherDataEvent;

/**
 * datagen 入口：由 {@code runData} 触发，把模型 / 方块状态 / 语言文件生成到
 * {@code src/generated/resources}。
 *
 * <p>为什么生成物不放在 {@code src/something}：这个项目的 {@code src/generated} 会被
 * {@code runData} 清空再重建，属于"纯生成物"；而默认方块列表（{@code DefaultBlockIds}）
 * 是"生成器的输入"，必须放在 {@code src/codegen} 才不会互相打架。</p>
 */
public final class BrDataGenerators {
    private BrDataGenerators() {}

    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        var generator = event.getGenerator();
        var output = generator.getPackOutput();
        var existingFileHelper = event.getExistingFileHelper();

        // 方块状态 + 方块/物品模型。
        generator.addProvider(event.includeClient(), new BrBlockStateProvider(output, existingFileHelper));
        // 展示名（中英各一套，数据结构完全一致）。
        generator.addProvider(event.includeClient(), new BrLanguageProvider(output, "en_us", false));
        generator.addProvider(event.includeClient(), new BrLanguageProvider(output, "zh_cn", true));
    }
}
