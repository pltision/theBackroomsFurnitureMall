/*
 * genBlockList 模块的一部分：不依赖 Minecraft / NeoForge。
 */
package yee.pltision.brfurniture.genblocklist;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 扫描 {@code assets/brfurniture/textures/block/} 下的方块贴图，把文件名当作方块 id。
 *
 * <p>只扫描该目录的<b>直接子文件</b>：{@code textures/block/} 下面还有 {@code normal/}、{@code can/}、
 * {@code level0/}、{@code exhibition_wall/} 等子目录，它们是"将来给家具或变种用的贴图"，
 * 不是一个个独立的实心方块，所以不会（也不应该）出现在默认方块列表里。</p>
 */
public final class BlockIdScanner {
    private static final String TEXTURE_SUFFIX = ".png";

    /**
     * 能被当成方块 id 的文件名。Minecraft 的 path 规则是 {@code [a-z0-9_.-]+}，
     * 这里额外要求首尾都是字母或数字，免得生成出 {@code _foo_} 这种一看就有问题的 id。
     */
    private static final Pattern VALID_ID = Pattern.compile("[a-z0-9](?:[a-z0-9_.-]*[a-z0-9])?");

    private BlockIdScanner() {}

    /**
     * @param textureDir {@code assets/brfurniture/textures/block} 目录
     * @return 去掉了 {@code .png} 的方块 id，按字典序排序，保证多次生成结果完全一致
     */
    public static List<String> scan(Path textureDir) throws IOException {
        Objects.requireNonNull(textureDir, "textureDir");
        if (!Files.isDirectory(textureDir)) {
            throw new IOException("贴图目录不存在或不是目录: " + textureDir);
        }

        List<String> ids = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(textureDir)) {
            for (Path file : stream) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                // 大小写不敏感地识别 .png，Windows 上有人会存成 .PNG。
                String fileName = file.getFileName().toString();
                String lower = fileName.toLowerCase(Locale.ROOT);
                if (!lower.endsWith(TEXTURE_SUFFIX) || lower.equals(TEXTURE_SUFFIX)) {
                    continue;
                }
                String id = fileName.substring(0, fileName.length() - TEXTURE_SUFFIX.length());
                if (!VALID_ID.matcher(id).matches()) {
                    skipped.add(fileName + " (文件名不是合法的方块 id)");
                    continue;
                }
                ids.add(id);
            }
        }

        if (!skipped.isEmpty()) {
            System.out.println("[genBlockList] 忽略了 " + skipped.size() + " 个贴图：");
            skipped.forEach(name -> System.out.println("  - " + name));
        }

        ids.sort(String::compareTo);
        if (ids.isEmpty()) {
            throw new IOException("在 " + textureDir + " 里没找到任何 .png 方块贴图");
        }
        return List.copyOf(ids);
    }
}
