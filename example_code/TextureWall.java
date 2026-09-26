package yee.pltision.brgb.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.joml.*;

import java.lang.Math;

public class TextureWall extends HorizontalDirectionalBlock {
    public static final MapCodec<CocoaBlock> CODEC = simpleCodec(CocoaBlock::new);
    public TextureWall(Properties properties) {
        super(properties);
    }

    public static VoxelShape[] createShape() {
        Vector3d start=new Vector3d(),end=new Vector3d(1,1,1/16d);
        Vector3d move=new Vector3d(1/2d,1/2d,1/2d);
        Quaterniond rotate=new Quaterniond().rotateY(-Math.PI/2);

        VoxelShape[] shapes=new VoxelShape[4];
        for(int i=0;i<4;i++){
            double minX = Math.min(start.x, end.x);
            double minY = Math.min(start.y, end.y);
            double minZ = Math.min(start.z, end.z);
            double maxX = Math.max(start.x, end.x);
            double maxY = Math.max(start.y, end.y);
            double maxZ = Math.max(start.z, end.z);
            shapes[i] = Shapes.create(minX, minY, minZ, maxX, maxY, maxZ);
            start.sub(move).rotate(rotate).add(move);
            end.sub(move).rotate(rotate).add(move);
        }
        return shapes;
    }
    public static final VoxelShape[] SHAPES=createShape();


    @Override
    protected @NotNull VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(FACING)){
            case EAST -> {
                return SHAPES[1];
            }
            case SOUTH ->{
                return SHAPES[2];
            }
            case WEST -> {
                return SHAPES[3];
            }
            default -> {
                return SHAPES[0];
            }
        }
    }

    @Override
    protected @NotNull MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(HorizontalDirectionalBlock.FACING, context.getHorizontalDirection().getOpposite());
    }
}