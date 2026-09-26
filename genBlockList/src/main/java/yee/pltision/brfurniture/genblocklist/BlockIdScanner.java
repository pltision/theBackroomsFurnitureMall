/*
 * genBlockList 模块的一部分：不依赖 Minecraft / NeoForge。
 */
package yee.pltision.brfurniture.genblocklist;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 递归扫描方块贴图目录，把"相对路径去掉 .png"当作方块 id。
 *
 * <p>递归而不是只扫一层，是为了让目录树真的有意义：{@code textures/block/block/} 下面
 * 可以再按主题分子目录，子目录名会进入方块 id。例如：</p>
 *
 * <pre>
 * textures/block/block/concrete.png          -&gt; concrete
 * textures/block/block/level0/wall.png       -&gt; level0/wall
 * textures/block/block/level0/ceiling.png    -&gt; level0/ceiling
 * </pre>
 *
 * <p>这正好对上 Minecraft 的贴图路径规则：注册名 {@code brfurniture:level0/wall}
 * 引用的贴图就是 {@code assets/brfurniture/textures/block/level0/wall.png}。
 * 所以"目录层级"和"贴图路径"是同一件事，不需要额外约定。</p>
 *
 * <p>子目录名同样要满足 Minecraft 的 path 规则（小写字母、数字、{@code _ - . /}），
 * 所以中文目录名或大写字母会被报出来并跳过，而不是生成一个注册就会失败的名字。</p>
 */
public final class BlockIdScanner {
    private static final String TEXTURE_SUFFIX = ".png";

    /**
     * 能被当成方块 id 的一段路径。Minecraft 的 path 规则是 {@code [a-z0-9_.-/]+}，
     * 这里对每一段额外要求首尾都是字母或数字，免得生成出 {@code _foo_/bar} 这种一看就有问题的名字。
     */
    private static final Pattern VALID_SEGMENT = Pattern.compile("[a-z0-9](?:[a-z0-9_.-]*[a-z0-9])?");

    private BlockIdScanner() {}

    /**
     * @param textureDir 方块贴图根目录（例如 {@code .../textures/block/block}）
     * @return 去掉了 {@code .png} 的方块 id（可能带斜杠），按字典序排序，保证多次生成结果完全一致
     */
    public static List<String> scan(Path textureDir) throws IOException {
        Objects.requireNonNull(textureDir, "textureDir");
        if (!Files.isDirectory(textureDir)) {
            throw new IOException("贴图目录不存在或不是目录: " + textureDir);
        }

        // LinkedHashMap 而不是 List：递归时顺便记下"这个 id 来自哪个文件"，
        // 这样重名时能报出两个具体路径，而不是只报一个 id。
        Map<String, Path> byId = new LinkedHashMap<>();
        List<String> skipped = new ArrayList<>();
        List<String> duplicated = new ArrayList<>();

        Files.walkFileTree(textureDir, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (!attrs.isRegularFile()) {
                    return FileVisitResult.CONTINUE;
                }
                String fileName = file.getFileName().toString();
                // 大小写不敏感地识别 .png，Windows 上有人会存成 .PNG。
                String lower = fileName.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(TEXTURE_SUFFIX) || lower.equals(TEXTURE_SUFFIX)) {
                    return FileVisitResult.CONTINUE;
                }

                String relative = textureDir.relativize(file).toString().replace('\\', '/');
                String id = relative.substring(0, relative.length() - TEXTURE_SUFFIX.length());
                if (!isValidId(id)) {
                    skipped.add(relative + " (路径不是合法的方块 id，每一段只允许小写字母、数字、_ - .)");
                    return FileVisitResult.CONTINUE;
                }

                Path previous = byId.putIfAbsent(id, file);
                if (previous != null) {
                    duplicated.add(id + "  (" + textureDir.relativize(previous) + " 与 " + relative + ")");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                // 单个文件读不了不应该让整个生成失败，记下来继续走。
                skipped.add(file + " (读取失败: " + exc.getMessage() + ")");
                return FileVisitResult.CONTINUE;
            }
        });

        if (!skipped.isEmpty()) {
            System.out.println("[genBlockList] 忽略了 " + skipped.size() + " 个贴图：");
            skipped.forEach(name -> System.out.println("  - " + name));
        }
        if (!duplicated.isEmpty()) {
            System.out.println("[genBlockList] 有 " + duplicated.size() + " 组贴图映射到同一个方块 id（只保留先遇到的）：");
            duplicated.forEach(name -> System.out.println("  - " + name));
        }

        List<String> ids = new ArrayList<>(byId.keySet());
        ids.sort(String::compareTo);
        if (ids.isEmpty()) {
            throw new IOException("在 " + textureDir + " 里没找到任何有效的 .png 方块贴图");
        }
        return List.copyOf(ids);
    }

    /**
     * 校验一个可能带斜杠的 id：每一段都要满足 Minecraft 的 path 规则。
     * 空段（连续斜杠、首尾斜杠）直接判不合法。
     */
    static boolean isValidId(String id) {
        if (id.isEmpty() || id.startsWith("/") || id.endsWith("/")) {
            return false;
        }
        for (String segment : id.split("/", -1)) {
            if (!VALID_SEGMENT.matcher(segment).matches()) {
                return false;
            }
        }
        return true;
    }
}
