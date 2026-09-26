/*
 * genBlockList 模块的一部分：不依赖 Minecraft / NeoForge。
 */
package yee.pltision.brfurniture.genblocklist;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 命令行入口。Gradle 任务 {@code :genBlockList:generateDefaultBlockIds} 会调用它，
 * 也可以直接 {@code java -cp ... yee.pltision.brfurniture.genblocklist.GenerateDefaultBlockIds --help}。
 */
public final class GenerateDefaultBlockIds {
    private static final String DEFAULT_CLASS_NAME = "DefaultBlockIds";

    private GenerateDefaultBlockIds() {}

    public static void main(String[] args) throws Exception {
        Arguments arguments = Arguments.parse(args);
        if (arguments.help) {
            System.out.printf("""
                    用法: GenerateDefaultBlockIds --textures <贴图目录> --output <java 源码根目录> [选项]
                    
                      --textures <dir>        方块贴图根目录（递归扫描，相对路径即方块 id）
                      --output <dir>           生成文件的 java 源码根目录，例如 src/codegen/java
                      --display-names <file>   展示名表（内置的默认表），格式 <方块id>=<中文>|<English>
                      --display-names-override <file>
                                               覆盖用的展示名表。存在时优先于 --display-names，
                                               用来实现"config 里的文件覆盖内置资源"。
                      --package <name>         生成类的包名（默认 %s）
                      --class <name>           生成类的类名（默认 %s）
                    %n""", arguments.packageName, arguments.className);
            return;
        }

        List<String> ids = BlockIdScanner.scan(arguments.textureDir);

        // 内置默认表 + 可选的覆盖表。覆盖表是"在其之上改几个名字"，不是整份替换：
        // 合并语义见 BlockNameResolver#merge。
        BlockNameResolver base = BlockNameResolver.loadDisplayNames(arguments.displayNamesFile, false);
        BlockNameResolver override = BlockNameResolver.loadDisplayNames(arguments.displayNamesOverrideFile, true);
        BlockNameResolver resolver = BlockNameResolver.merge(base, override);
        if (!override.explicitNames().isEmpty()) {
            System.out.println("[genBlockList] config 里的展示名表覆盖了 " + override.explicitNames().size() + " 个 id");
        }

        List<BlockNameResolver.LocalizedName> names = new ArrayList<>(ids.size());
        int derived = 0;
        for (String id : ids) {
            names.add(resolver.resolve(id));
            if (!resolver.hasExplicitName(id)) {
                derived++;
            }
        }
        if (derived > 0) {
            System.out.println("[genBlockList] 有 " + derived + " 个 id 没有在展示名表里登记，名字是按 id 推导出来的：");
            for (String id : ids) {
                if (!resolver.hasExplicitName(id)) {
                    System.out.println("  - " + id + " -> " + resolver.resolve(id).zhCn() + " / " + resolver.resolve(id).enUs());
                }
            }
        }

        String sourceDescription = "assets/brfurniture/textures/block/block/*.png";
        Path written = new DefaultBlockIdsWriter(arguments.packageName, arguments.className)
                .write(arguments.outputDir, ids, names, sourceDescription);
        System.out.println("[genBlockList] 完成: " + written);
    }

    /** 极简参数解析：不引入任何依赖。 */
    private static final class Arguments {
        private Path textureDir;
        private Path outputDir;
        /** 内置的默认展示名表（src/main/resources 里的那份）。 */
        private Path displayNamesFile;
        /** 可选：覆盖用的展示名表（config/brfurniture 里的那份）。 */
        private Path displayNamesOverrideFile;
        private String packageName = "yee.pltision.brfurniture.codegen";
        private String className = DEFAULT_CLASS_NAME;
        private boolean help;

        static Arguments parse(String[] args) {
            Arguments arguments = new Arguments();
            for (int i = 0; i < args.length; i++) {
                String key = args[i];
                switch (key) {
                    case "--help", "-h" -> arguments.help = true;
                    case "--textures" -> arguments.textureDir = Path.of(next(args, ++i, key));
                    case "--output" -> arguments.outputDir = Path.of(next(args, ++i, key));
                    case "--display-names" -> arguments.displayNamesFile = Path.of(next(args, ++i, key));
                    case "--display-names-override" -> arguments.displayNamesOverrideFile = Path.of(next(args, ++i, key));
                    case "--package" -> arguments.packageName = next(args, ++i, key);
                    case "--class" -> arguments.className = next(args, ++i, key);
                    default -> throw new IllegalArgumentException("未知参数: " + key);
                }
            }
            if (arguments.help) {
                return arguments;
            }
            if (arguments.textureDir == null) {
                throw new IllegalArgumentException("缺少 --textures");
            }
            if (arguments.outputDir == null) {
                throw new IllegalArgumentException("缺少 --output");
            }
            if (arguments.displayNamesFile == null) {
                // 没传就假定是工程里的默认位置；文件在不在交给 loadDisplayNames 判断
                // （它会把"没有展示名表"当成合法状态：全部按 id 推导）。
                arguments.displayNamesFile = Path.of("src", "main", "resources", "assets", "brfurniture", "block_names.properties");
            }
            return arguments;
        }

        private static String next(String[] args, int index, String key) {            if (index >= args.length) {
                throw new IllegalArgumentException(key + " 后面缺少取值");
            }
            return args[index];
        }
    }
}
