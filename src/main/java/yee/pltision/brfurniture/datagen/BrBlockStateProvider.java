package yee.pltision.brfurniture.datagen;

import net.minecraft.data.PackOutput;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.client.model.generators.BlockStateProvider;
import net.neoforged.neoforge.client.model.generators.ConfiguredModel;
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
 * <h2>为什么物品模型都显式写 "item/" 前缀</h2>
 * <p>{@code ModelProvider#extendWithFolder} 只在名字里<b>没有斜杠</b>时才补
 * {@code block/} / {@code item/} 前缀；带斜杠的名字会被当成"已经是完整路径"。
 * 方块 id 可以带斜杠（例如 {@code level0/wall}，对应贴图子目录），这时
 * {@code simpleBlockItem(block, model)} 会把它当成完整路径传给 item provider，
 * 结果生成到 {@code models/level0/wall.json}，而 blockstate 期望的是
 * {@code models/item/level0/wall.json} —— 进游戏就会报
 * {@code Unable to load model: 'brfurniture:item/level0/wall'}。</p>
 *
 * <p>所以这里对<b>所有</b>形态都显式写 {@code block/<路径>} 与 {@code item/<路径>}，
 * 不依赖 provider 的自动补前缀，行为就和 id 里有没有斜杠无关了。</p>
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
                String relativePath = variant.blockPath(entry.path());

                switch (variant) {
                    case SOLID -> {
                        // 实心方块的模型：六面同一张贴图的 cube。
                        //
                        // 这里刻意不用 BlockStateProvider.cubeAll(block)：它内部把"注册路径"直接
                        // 丢给 models().cubeAll(...)，而 ModelProvider#extendWithFolder 只在名字里
                        // 没有斜杠时才补 block/ 前缀。id 是 level0/wall 这种带斜杠的时候，
                        // 模型会被建到 models/level0/wall.json，blockstate 也引用
                        // brfurniture:level0/wall —— 两边都少了 block/ 这一层，进游戏加载失败。
                        // 所以模型名一律显式写成 "block/" + 注册路径。
                        ModelFile cube = models()
                                .withExistingParent("block/" + relativePath, mcLoc("block/cube_all"))
                                .texture("all", modLoc("block/" + entry.path()));
                        getVariantBuilder(block).partialState().setModels(new ConfiguredModel(cube));
                        // 物品模型同理，显式写 "item/" + 路径，不依赖 provider 自动补前缀。
                        itemModels().withExistingParent("item/" + relativePath, cube.getLocation());
                    }
                    case EXHIBITION_WALL, EXHIBITION_WALL_BRACE -> {
                        ModelFile model = models()
                                .withExistingParent("block/" + relativePath, modLoc("block/" + variant.modelTemplate()))
                                .texture("1", modLoc("block/" + entry.path()));
                        // horizontalBlock 直接吃 ModelFile，按 FACING 生成四个朝向，不受路径影响。
                        horizontalBlock(block, model);
                        // 物品模型和方块模型同名，只是分别落在 models/item 与 models/block 下。
                        itemModels().withExistingParent("item/" + relativePath, model.getLocation());
                    }
                }
            }
        }
    }
}
