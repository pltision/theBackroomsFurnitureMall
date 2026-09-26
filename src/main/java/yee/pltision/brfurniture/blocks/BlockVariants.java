package yee.pltision.brfurniture.blocks;

/**
 * 一种方块贴图能生成出的所有"形态"。
 *
 * <p>每加一种变种，只要在这里加一个实例，并且：</p>
 * <ol>
 *     <li>在 {@link BlockManager} 里加一个对应的 {@code putXxx}/{@code registerXxx}，</li>
 *     <li>在 datagen 的 {@code BlockStateProvider} 里给它生成模型。</li>
 * </ol>
 *
 * <p>{@code null} 的 {@code namespaceSegment} 表示"这个形态就是那个实心方块本身"，
 * 注册 id 直接用基础 id；其它形态的注册 id 是
 * {@code brfurniture:<namespaceSegment>/<基础id>}，例如 {@code brfurniture:exhibition_wall/level0_wall}。</p>
 */
public enum BlockVariants {
    /** 实心方块，注册 id 就是 {@code brfurniture:<id>}。 */
    SOLID(null, "", ""),
    /** 展墙，注册 id 是 {@code brfurniture:exhibition_wall/<id>}。 */
    EXHIBITION_WALL("exhibition_wall", "展墙", " Exhibition Wall"),
    /** 展墙墙根，注册 id 是 {@code brfurniture:exhibition_wall_brace/<id>}。 */
    EXHIBITION_WALL_BRACE("exhibition_wall_brace", "展墙墙根", " Exhibition Wall Brace");

    private final String namespaceSegment;
    private final String zhCnSuffix;
    private final String enUsSuffix;

    BlockVariants(String namespaceSegment, String zhCnSuffix, String enUsSuffix) {
        this.namespaceSegment = namespaceSegment;
        this.zhCnSuffix = zhCnSuffix;
        this.enUsSuffix = enUsSuffix;
    }

    /** {@return 注册 id 里斜杠前面的那一段，实心方块返回 {@code null}} */
    public String namespaceSegment() {
        return namespaceSegment;
    }

    /** {@return 中文展示名后缀，和基础名直接相连（"Level0墙壁" + "展墙"）} */
    public String zhCnSuffix() {
        return zhCnSuffix;
    }

    /** {@return 英文展示名后缀，带前导空格（"Level0 Wall" + " Exhibition Wall"）} */
    public String enUsSuffix() {
        return enUsSuffix;
    }

    /** {@return 生成模型 / 贴图时用的模板名，实心方块用它自己的贴图，没有模板} */
    public String modelTemplate() {
        return namespaceSegment;
    }

    /**
     * 把基础 id 拼成注册 id。注意 Minecraft 的 path 允许斜杠，
     * 所以 {@code exhibition_wall/level0_wall} 是合法且可用的注册名。
     */
    public String blockPath(String basePath) {
        return namespaceSegment == null ? basePath : namespaceSegment + "/" + basePath;
    }
}
