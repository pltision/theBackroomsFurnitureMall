package yee.pltision.brfurniture.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ModelFile;
import net.neoforged.neoforge.common.data.ExistingFileHelper;
import yee.pltision.brfurniture.BrFurniture;
import yee.pltision.brfurniture.blocks.BlockManager;
import yee.pltision.brfurniture.blocks.BlockVariants;
import yee.pltision.brfurniture.blocks.ModBlockRegistry;

/**
 * 生成方块状态与模型。
 *
 * <p>两种形态，两种生成方式：</p>
 * <ul>
 *     <li><b>实心方块</b>：六面同一张贴图的 cube，模型是 {@code models/block/<id>.json}；</li>
 *     <li><b>展墙 / 展墙墙根</b>：以手写模板 {@code brfurniture:block/exhibition_wall}
 *         或 {@code brfurniture:block/exhibition_wall_brace} 为父模型，把模板里的
 *         {@code #1}（贴图槽）替换成这个方块的贴图，再用 {@code horizontalBlock}
 *         按 FACING 生成 blockstate。</li>
 * </ul>
 *
 * <p>模板原本叫 {@code texture_wall} / {@code texture_wall_root}，已经重命名成
 * {@code exhibition_wall} / {@code exhibition_wall_brace}，所以这里直接用新名字。</p>
 *
 * <h2>关于带斜杠的注册名</h2>
 * <p>展墙的注册名是 {@code exhibition_wall/level0_wall}，所以模型路径也会带一层目录。
 * {@code ModelProvider#extendWithFolder} 只在<b>名字里没有斜杠</b>时才补
 * {@code block/} / {@code item/} 前缀，带斜杠的名字会被当成"已经是完整路径"。
 * 所以这里对展墙显式写 {@code block/<...>} 与 {@code item/<...>}，
 * 生成的引用就是 {@code brfurniture:block/exhibition_wall/level0_wall}，
 * 与 blockstate 里写的完全一致。</p>
 *
 * <p>另外不能对带斜杠的名字用 {@code simpleBlockItem}：它按"取模型路径最后一段"来命名物品模型，
 * {@code exhibition_wall/level0_wall} 会退化成 {@code item/level0_wall.json}，和 blockstate
 * 引用的 {@code item/exhibition_wall/level0_wall} 对不上，物品就会变成紫黑方块。
 * 所以这里显式生成物品模型。</p>
 */
public class BrBlockStateProvider extends BlockStateProvider {
    private final BlockManager blocks;

    public BrBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        this(output, existingFileHelper, BlockManager.INSTANCE);
    }

    public BrBlockStateProvider(PackOutput output, ExistingFileHelper existingFileHelper, BlockManager blocks) {
        super(output, BrFurniture.MODID, existingFileHelper);
        this.blocks = blocks;
    }

    @Override
    protected void registerStatesAndModels() {
        for (ModBlockRegistry entry : blocks.registeredBlocks()) {
            for (BlockVariants variant : entry.registeredVariants()) {
                Block block = entry.block(variant).get();

                switch (variant) {
                    case SOLID -> {
                        // 实心方块的注册名没有斜杠，BlockModelProvider 会自动补 block/ 前缀。
                        ModelFile cube = cubeAll(block);
                        simpleBlock(block, cube);
                        simpleBlockItem(block, cube);
                    }
                    case EXHIBITION_WALL, EXHIBITION_WALL_BRACE -> {
                        String relativePath = variant.blockPath(entry.path());
                        ModelFile model = models()
                                .withExistingParent("block/" + relativePath, modLoc("block/" + variant.modelTemplate()))
                                .texture("1", modLoc("block/" + entry.path()));
                        horizontalBlock(block, model);
                        // 物品模型和方块模型同名，只是分别落在 models/item 与 models/block 下。
                        // withExistingParent 只接受 ResourceLocation，所以把模型的 id 取出来当父模型。
                        itemModels().withExistingParent("item/" + relativePath, model.getLocation());
                    }
                }
            }
        }
    }
}
