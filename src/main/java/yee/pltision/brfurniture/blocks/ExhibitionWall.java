package yee.pltision.brfurniture.blocks;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * 展墙：贴在墙上的 1/16 厚装饰板，可以朝四个水平方向。
 *
 * <p>原实现（{@code example_code/TextureWall.java}）用 joml 的 {@code Vector3d}/{@code Quaterniond}
 * 旋转出一个 1/16 厚的方盒。这里继承了它的行为与碰撞箱数值，但：</p>
 * <ul>
 *     <li>不再为了算四个常数去依赖 joml —— 直接按四个方向写出结果，少一层换算；</li>
 *     <li>{@code codec()} 用父类的 {@code simpleCodec(this::create)}，<b>不再借用 {@code CocoaBlock::new}</b>
 *         （那会让上游在反序列化出可可块，是个隐藏 bug）；</li>
 *     <li>补上了 {@code getShape}：只覆盖碰撞箱的话，选中框和光照剔除会不一致。</li>
 * </ul>
 *
 * <p>四个碰撞箱的取值和原实现一致：都是以 {@code (0.5, 0.5, 0.5)} 为中心、把
 * {@code [0,0,0] - [1,1,0.0625]} 绕 Y 轴每次转 -90° 得到的。</p>
 */
public class ExhibitionWall extends HorizontalDirectionalBlock {
    public static final MapCodec<ExhibitionWall> CODEC = simpleCodec(ExhibitionWall::new);

    /** 板子厚度，1 像素。 */
    protected static final double THICKNESS = 1.0D / 16.0D;

    /** 按 {@code north, east, south, west} 的顺序存四个方向的形状。 */
    private static final VoxelShape[] SHAPES = createShapes();

    public ExhibitionWall(Properties properties) {
        super(properties);
        registerDefaultState(defaultBlockState().setValue(FACING, Direction.NORTH));
    }

    private static VoxelShape[] createShapes() {
        List<VoxelShape> shapes = new ArrayList<>(4);
        // 以方块中心为原点，把 [0,0,0]-[1,1,1/16] 绕 Y 轴转四次，取四个方向的包围盒。
        // 初始（绕 Y 轴 0°）：北面贴墙，占据 z ∈ [0, 1/16]。
        shapes.add(Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 1.0D, THICKNESS));
        // 逆时针转 90°（朝东的墙）：x ∈ [1-1/16, 1]。
        shapes.add(Shapes.box(1.0D - THICKNESS, 0.0D, 0.0D, 1.0D, 1.0D, 1.0D));
        // 再转 90°（朝南）：z ∈ [1-1/16, 1]。
        shapes.add(Shapes.box(0.0D, 0.0D, 1.0D - THICKNESS, 1.0D, 1.0D, 1.0D));
        // 再转 90°（朝西）：x ∈ [0, 1/16]。
        shapes.add(Shapes.box(0.0D, 0.0D, 0.0D, THICKNESS, 1.0D, 1.0D));
        return shapes.toArray(VoxelShape[]::new);
    }

    /** {@return FACING 对应的形状下标 */
    protected static int shapeIndex(BlockState state) {
        return switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPES[shapeIndex(state)];
    }

//    @Override
//    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
//        return SHAPES[shapeIndex(state)];
//    }

    protected boolean useShapeForLightOcclusion(BlockState state) {
        return true;
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // 玩家面对的墙在玩家的反方向，所以取反。
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    // rotate / mirror 不需要在这里重写：父类 HorizontalDirectionalBlock 已经按 FACING
    // 正确地转了方向。1.21.1 里 BlockBehaviour#rotate(BlockState, Rotation) 已经过时，
    // 父类的实现才是当前正确的做法，自己再写一遍反而会踩到过时 API。
}
