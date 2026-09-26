package yee.pltision.brfurniture.blocks;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * 一个贴图（基础 id）注册出来的所有方块。
 *
 * <p>数据结构就是需求里说的 {@code "level0_wall": {solidBlock, exhibitionWall, exhibitionWallBrace}}
 * 再加上对应的 {@link BlockItem}：创造物品栏和 datagen 都只需要遍历这张表。</p>
 *
 * @param path        基础 id
 * @param blocks      各形态的方块，按 {@link BlockVariants} 的定义顺序
 * @param blockItems  各形态的方块物品
 * @param sourceLine  这个 id 来自配置文件的哪一行（{@link BlockWarning#NO_LINE} 表示内置列表）
 */
public record ModBlockRegistry(
        String path,
        Map<BlockVariants, DeferredBlock<? extends Block>> blocks,
        Map<BlockVariants, DeferredItem<BlockItem>> blockItems,
        int sourceLine) {

    public ModBlockRegistry {
        blocks = Map.copyOf(blocks);
        blockItems = Map.copyOf(blockItems);
    }

    /** 用枚举当 key，保证遍历顺序和 {@link BlockVariants} 一致。 */
    public static Map<BlockVariants, DeferredBlock<? extends Block>> newBlockMap() {
        return new EnumMap<>(BlockVariants.class);
    }

    public static Map<BlockVariants, DeferredItem<BlockItem>> newItemMap() {
        return new EnumMap<>(BlockVariants.class);
    }

    public DeferredBlock<? extends Block> block(BlockVariants variant) {
        return blocks.get(variant);
    }

    /** {@return 某个形态的注册 id，没有注册这个形态时为空 */
    public Optional<ResourceLocation> id(BlockVariants variant) {
        return Optional.ofNullable(blocks.get(variant)).map(DeferredBlock::getId);
    }

    /** {@return 某个形态的方块物品，没有注册这个形态时为空 */
    public Optional<DeferredItem<BlockItem>> item(BlockVariants variant) {
        return Optional.ofNullable(blockItems.get(variant));
    }

    /**
     * {@return 这个 id 的方块物品，按 {@link BlockVariants} 的顺序}。
     * 创造物品栏按这个顺序摆，玩家看到的就是"实心、展墙、展墙墙根"。
     */
    public List<DeferredItem<BlockItem>> itemsInOrder() {
        return java.util.Arrays.stream(BlockVariants.values())
                .map(blockItems::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    /** {@return 已注册的形态，按 {@link BlockVariants} 的顺序} */
    public List<BlockVariants> registeredVariants() {
        return java.util.Arrays.stream(BlockVariants.values())
                .filter(blocks::containsKey)
                .toList();
    }
}
