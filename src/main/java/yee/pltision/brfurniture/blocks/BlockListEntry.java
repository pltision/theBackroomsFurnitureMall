package yee.pltision.brfurniture.blocks;

import net.minecraft.resources.ResourceLocation;
import yee.pltision.brfurniture.BrFurniture;

/**
 * 方块列表里的一条有效记录：一个基础 id，以及它是从配置文件的哪一行来的。
 *
 * <p>保留行号是为了在警告界面上直接指出"第 12 行的 xxx 有问题"。</p>
 *
 * @param path       基础 id，相对 {@code brfurniture} 命名空间，例如 {@code level0_wall}
 * @param sourceLine 原始行内容
 * @param lineNumber 行号（{@link BlockWarning#NO_LINE} 表示来自内置默认列表）
 */
public record BlockListEntry(String path, String sourceLine, int lineNumber) {
    public BlockListEntry {
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("方块 id 不能为空");
        }
        sourceLine = sourceLine == null ? path : sourceLine;
    }

    /** {@return 实心方块的注册 id，例如 {@code brfurniture:level0_wall}} */
    public static ResourceLocation id(String path) {
        return id(path, BlockVariants.SOLID);
    }

    /** {@return 某个形态的注册 id，例如 {@code brfurniture:exhibition_wall/level0_wall}} */
    public static ResourceLocation id(String path, BlockVariants variant) {
        return ResourceLocation.fromNamespaceAndPath(BrFurniture.MODID, variant.blockPath(path));
    }

    public ResourceLocation id(BlockVariants variant) {
        return id(path, variant);
    }
}
