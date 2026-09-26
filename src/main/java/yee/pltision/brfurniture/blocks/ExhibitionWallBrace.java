package yee.pltision.brfurniture.blocks;

import com.mojang.serialization.MapCodec;

/**
 * 展墙墙根：展墙底下那一圈踢脚。
 *
 * <p>模型比展墙多两个元素（贴地的横条 + 斜撑），但方块行为完全一样：
 * 同样朝四个水平方向、同样的 1/16 厚贴墙碰撞箱。所以这里直接继承
 * {@link ExhibitionWall}，只换掉 codec —— 模型由 datagen 用
 * {@code brfurniture:block/exhibition_wall_brace} 模板生成。</p>
 */
public class ExhibitionWallBrace extends ExhibitionWall {
    public static final MapCodec<ExhibitionWallBrace> CODEC = simpleCodec(ExhibitionWallBrace::new);

    public ExhibitionWallBrace(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends net.minecraft.world.level.block.HorizontalDirectionalBlock> codec() {
        return CODEC;
    }
}
