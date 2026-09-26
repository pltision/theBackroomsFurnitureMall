package yee.pltision.brfurniture.blocks;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import yee.pltision.brfurniture.BrFurniture;

/**
 * 创造模式物品栏。
 *
 * <p>两个页签：</p>
 * <ul>
 *     <li>{@code brfurniture:blocks} → "后室家具店：方块"，本阶段所有方块都在这里；</li>
 *     <li>{@code brfurniture:furniture} → "后室家具店：家具"，家具阶段再用，现在先建出来占位。</li>
 * </ul>
 *
 * <p>方块按"每个贴图一组"连续排列：一个贴图的实心方块、展墙、展墙墙根挨在一起，
 * 然后接下一个贴图。</p>
 *
 * <h2>为什么没有"每贴图占满一整行 9 格"的补位</h2>
 * <p>原本想在每组末尾补空气物品凑满 9 格。这在 NeoForge 21.1 上<b>做不到</b>：</p>
 * <ul>
 *     <li>{@code EventHooks#onCreativeModeTabBuildContents} 与
 *         {@code CreativeModeTab.ItemDisplayBuilder#accept} 都硬性要求
 *         {@code stack.getCount() == 1}，否则直接抛 {@code IllegalArgumentException}；</li>
 *     <li>而空气的 {@code maxStackSize} 是 1，{@code new ItemStack(Items.AIR)} 的 count 恒为 <b>0</b>
 *         （从物品构造 ItemStack 时会按 maxStackSize 截断，{@code setCount(1)} 也会被截回 0，
 *         {@code isEmpty()} 恒为 true）。</li>
 * </ul>
 * <p>所以"补空气"必然崩溃（实测过）。页签里也没法插入真正意义上的空槽。
 * 需要视觉对齐的话，只能另外注册一个隐形占位物品来补位——那会给模组引入一个非方块物品，
 * 目前的取舍是不加。</p>
 *
 * <p>位置刻意不用 {@code withTabsBefore/withTabsAfter}：如果指定的锚点页签也间接依赖本页签，
 * NeoForge 的页签排序会出现环（{@code CyclePresentException}），注册阶段直接失败。
 * 现在两个页签都排在最后。</p>
 */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BrFurniture.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> BLOCKS = TABS.register("blocks",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.brfurniture.blocks"))
                    .icon(ModCreativeTabs::blocksTabIcon)
                    .displayItems((parameters, output) -> fillBlockItems(output))
                    .build());

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> FURNITURE = TABS.register("furniture",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.brfurniture.furniture"))
                    // 家具阶段会换成真正的家具物品；1.21.1 还没有木椅这类物品，先用物品展示框占位。
                    .icon(() -> new ItemStack(Items.ITEM_FRAME))
                    .displayItems((parameters, output) -> {
                        // 家具阶段再往这里放东西；现在保持空页签。
                    })
                    .build());

    private ModCreativeTabs() {}

    /**
     * 方块页签的图标：优先用 {@code brfurniture:level0_wall} 的方块物品，
     * 它不存在（被配置删掉 / 被冲突跳过）时退回屏障方块。
     */
    private static ItemStack blocksTabIcon() {
        return BlockManager.INSTANCE.registeredBlock("level0_wall")
                .flatMap(entry -> entry.item(BlockVariants.SOLID))
                .map(item -> new ItemStack(item.get()))
                .orElseGet(() -> new ItemStack(Blocks.BARRIER));
    }

    /** 把已注册的方块按"每个贴图一组"的顺序放进页签。 */
    private static void fillBlockItems(CreativeModeTab.Output output) {
        for (ModBlockRegistry entry : BlockManager.INSTANCE.registeredBlocks()) {
            for (var item : entry.itemsInOrder()) {
                // 新造的 ItemStack 默认 count 是 1，满足创造页签对 count 的硬性要求。
                output.accept(new ItemStack(item.get()));
            }
        }
    }
}
