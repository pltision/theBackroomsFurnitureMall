# 后室家具店 the Backrooms Furniture Mall

NeoForge 1.21.1 模组，**只添加方块与家具等资产，不添加机制**。当前实现了「方块阶段」，家具阶段待做。

- 游戏内模组名：后室家具店 Backrooms Furniture
- mod id：`brfurniture`
- 缩写：BrFM
- 主包：`yee.pltision.brfurniture`
- 主类：`BrFurniture`

---

## 一句话原理

**贴图就是方块。** `src/main/resources/assets/brfurniture/textures/block/block/` 下每有一个 `xxx.png`，就自动得到三个方块：

| 形态 | 注册 id | 展示名（例） |
| --- | --- | --- |
| 实心方块 | `brfurniture:xxx` | Level0墙壁 / Level0 Wall |
| 展墙 | `brfurniture:exhibition_wall/xxx` | Level0墙壁展墙 / Level0 Wall Exhibition Wall |
| 展墙墙根 | `brfurniture:exhibition_wall_brace/xxx` | Level0墙壁展墙墙根 / Level0 Wall Exhibition Wall Brace |

**加一个贴图不需要写任何 Java 代码。**

---

## 流程

```
1. 前置贴图      src/main/resources/assets/brfurniture/textures/block/block/*.png
                          │  构建脚本把它镜像到 build/blockTextures/…/textures/block/（见下）
                          │
2. 生成方块列表  gradlew :genBlockList:generateDefaultBlockIds
                          │  扫描贴图 + 读 block_names.properties
                          ▼
                src/codegen/java/.../codegen/DefaultBlockIds.java   （硬编码默认列表，gitignore）
                          │
3. runData       gradlew runData
                          │  注册每个 id 的三个方块 → 生成 blockstate / 模型 / 语言文件
                          ▼
                src/generated/resources/**
                          │
4. build / runClient
```

> `genBlockList` 是**独立的、不依赖 Minecraft / NeoForge** 的模块，所以它可以单独跑、单独测。
> 生成物放在 `src/codegen` 而不是 `src/generated`，因为 `runData` 会清空 `src/generated`，
> 而默认方块列表是 datagen 的**输入**，不能和输出放一起。

```bash
gradlew :genBlockList:generateDefaultBlockIds   # 只刷新默认方块列表
gradlew runData                                 # 生成资源（会自动先刷新方块列表）
gradlew build                                   # 打包
gradlew runClient                               # 起客户端
```

---

## 贴图放在哪、为什么要镜像一次

**贴图源**只有一份，按内容分组：

```
src/main/resources/assets/brfurniture/textures/block/
  block/                            <- 会自动生成三个方块的贴图（生成器扫这一棵子树）
    concrete.png
    level0/wall.png                 <- 也可以再分子目录
  exhibition_wall/wall_plank.png    <- 展墙模板模型用
  normal/  level0/  pipes/  can/    <- 家具与其它内容预留
```

多一层 `block/` 是为了"方块 / 未来的家具 / …"分开，跟创造模式页签的分法是同一个思路。

### 递归扫描：目录层级 = 方块 id = 贴图路径

生成器**递归**扫描 `block/` 这棵子树，用"相对路径去掉 `.png`"当方块 id：

```
block/concrete.png        -> concrete
block/level0/wall.png     -> level0/wall
```

这正好对上 Minecraft 的贴图路径规则：注册名 `brfurniture:level0/wall` 引用的贴图就是
`assets/brfurniture/textures/block/level0/wall.png`。所以"目录层级"和"贴图路径"是同一件事，
子目录是真正有意义的，不需要额外约定。子目录名同样要满足 Minecraft 的 path 规则
（小写字母、数字、`_ - . /`），不合法会被报出来并跳过，而不是生成一个注册就会失败的名字。

### 但目录本身 Minecraft 不认，所以要镜像

Minecraft 解析 `brfurniture:block/xxx` 时**只会**去找
`assets/brfurniture/textures/block/xxx.png` —— 注意这里没有第二层 `block/`。
`textures/block/block/` 只是源码里的分组，游戏不会读。
所以构建时做一次路径映射（`build.gradle` 里的 `syncBlockTextures`，**递归**镜像、保留层级）：

```
源:   src/main/resources/…/textures/block/block/**/*.png
镜像: build/blockTextures/assets/brfurniture/textures/block/**/*.png     <- 注册成额外资源根
```

这一个目录同时被两处使用：

- `processResources` 打进 jar → 贴图落在正确的 `assets/brfurniture/textures/block/` 下；
- `runData` 的 `--existing` → datagen 的模型校验能找到贴图
  （少了这一项就会报 `Texture ... does not exist in any known resource pack`）。

源文件只有一份，没有需要手工同步的副本。

源文件只有一份，没有需要手工同步的副本。

---

## 方块列表配置文件

游戏内的列表来自 `config/brfurniture/blocks.txt`（开发环境是 `run/config/brfurniture/blocks.txt`）。

- **文件不存在** → 按惯例自动创建一份默认配置，并使用内置列表；
- **文件存在** → 完全覆盖内置列表；
- 一行一个 id，空行与 `#` 开头的行忽略；
- 两种写法等价：`level0_wall` 与 `brfurniture:level0_wall`。

**加载这个文件无论写得多离谱都不会让游戏崩溃。** 出问题的行会被跳过并收集成警告，
之后在「模组列表 → 后室家具店 → 配置」里显示警告界面，进入世界时也会在聊天栏提示一次。
界面里会同时提示"删除 `config/brfurniture/blocks.txt` 可以恢复默认列表"。

已实现的校验：

| 情况 | 处理 |
| --- | --- |
| 不是合法资源路径（大写、非法字符、空路径……） | 跳过该行 |
| 命名空间不是 `brfurniture` | 跳过该行 |
| 文件内部重复 | 保留第一次出现 |
| 与已有注册名冲突（原版或别的模组占了 block / item 注册表） | 跳过该形态 |
| id 过长（> 200 字符） | 跳过 |
| **贴图不存在**（`textures/block/<id>.png` 找不到） | 跳过该 id，并报出缺哪个文件 |
| 文件读不出来 / 编码坏了 / 超过 1 MiB / 超过 10 万行 | 记警告，退回内置列表 |
| 文件存在但一行有效内容都没有 | 记警告，退回内置列表（**不覆盖玩家写的文件**） |

> **贴图缺失这一条很重要。** 模型是"六面都贴 `brfurniture:block/<id>`"，
> 如果配置里留着一个已经没有贴图的 id，datagen 会在注册完之后才抛
> `Texture ... does not exist in any known resource pack` —— 错误信息指不到是配置文件哪一行，
> 而且整个 `runData` 直接失败。现在这种情况会被提前拦成一条警告，`runData` 照常通过。
>
> 注意 `config/brfurniture/blocks.txt` **一旦存在就不会自动重写**，
> 所以"删了贴图但没删配置里那一行"是很常见的状态。想按当前贴图重建配置，
> 删掉 `config/brfurniture/blocks.txt` 再跑一次即可。

> 有个 1.21.1 的坑值得记一笔：`ResourceLocation.isValidPath("")` 返回 **true**
> （它的实现是个长度 0 的循环），所以 `brfurniture:` 这种"只有冒号"的写法必须自己挡掉，
> 否则会造出路径为空的 id，在注册阶段抛异常把游戏带崩。

---

## 目录结构

```
genBlockList/                        独立代码生成模块（不依赖 MC）
  src/main/java/.../genblocklist/
    GenerateDefaultBlockIds.java     命令行入口（Gradle 任务调用）
    BlockIdScanner.java              递归扫描 textures/block/block/**/*.png，相对路径即方块 id
    BlockNameResolver.java           读展示名表 + 按 id 推导名字
    DefaultBlockIdsWriter.java       写出 DefaultBlockIds.java

src/codegen/
  block_names.properties             展示名表：<id>=<中文>|<English>
  java/                              生成物 DefaultBlockIds.java（gitignore）

src/main/java/yee/pltision/brfurniture/
  BrFurniture.java                   主类：声明覆盖项 → 读配置 → 注册 → 接 datagen
  blocks/
    BlockVariants.java               三种形态（id 规则、名字后缀）
    BlockListConfig.java             读 / 建 config/brfurniture/blocks.txt，防御性校验
    BlockListEntry.java              一条有效记录（含来源行号）
    BlockWarning.java                一条问题的载体（包着 FML 的 ModLoadingIssue）
    BlockManager.java                读配置 → 校验 → DeferredRegister 注册
    ModBlockRegistry.java            一个 id 的全部方块 + 方块物品
    BlockDisplayNames.java           基础名查表 + 推导 + 拼接变种后缀
    ModCreativeTabs.java             创造物品栏（方块按每组连续排列）
    ExhibitionWall.java              展墙（1/16 厚、四向）
    ExhibitionWallBrace.java         展墙墙根（继承展墙，只换模型与 codec）
  datagen/
    BrDataGenerators.java            GatherDataEvent 入口
    BrBlockStateProvider.java        blockstate + 方块/物品模型
    BrLanguageProvider.java          en_us / zh_cn（同一套逻辑，布尔切换）
  client/
    BrFurnitureClient.java           注册配置界面扩展点
    BlockWarningsScreen.java         方块列表问题警告界面
    BrPlayerNotifier.java            进世界时聊天栏提示一次

src/main/resources/assets/brfurniture/textures/block/
  block/                             会自动生成三个方块的贴图（递归扫描这棵子树）
  exhibition_wall/wall_plank.png     展墙模板模型用的贴图
  normal/ level0/ pipes/ can/        家具与其它内容预留
src/main/resources/assets/brfurniture/models/block/
  exhibition_wall*.json              展墙与墙根的模板模型（手写）
src/generated/resources/             datagen 产物（blockstate / 模型 / 语言文件）
build/blockTextures/                 贴图镜像目录（构建产物，见上面「贴图放在哪」）
example_code/                        参考用的旧代码，不参与编译
```

---

## 覆盖某个方块的实现

默认值是：实心方块 `Block::new`，展墙 `ExhibitionWall::new`，属性是木质
（`NoteBlockInstrument.BASS`、`strength(2.0F, 3.0F)`、`SoundType.WOOD`，展墙/墙根额外 `noOcclusion()`）。

想改就在 `BrFurniture` 构造里声明，必须在读取配置之前：

```java
putBlock("level0_wall", Block::new, BlockBehaviour.Properties.of().strength(4.0F));
putExhibitionWall("level0_wall", BlockBehaviour.Properties.of().noOcclusion());
putExhibitionWallBrace("level0_wall", CustomBrace::new);
```

---

## 创造物品栏

- `brfurniture:blocks` → 后室家具店：方块。方块按"每个贴图一组"连续排列
  （实心方块、展墙、展墙墙根挨在一起，然后接下一个贴图）；
- `brfurniture:furniture` → 后室家具店：家具。家具阶段的占位页签。

方块页签的图标优先用 `brfurniture:level0_wall` 的方块物品，它不存在时退回屏障方块。

**没有做"每贴图占满一整行 9 格、末尾补空气"的排版**，因为 NeoForge 21.1 做不到：

- `EventHooks#onCreativeModeTabBuildContents` 和 `CreativeModeTab.ItemDisplayBuilder#accept`
  都硬性要求每个物品堆 `count == 1`，否则抛 `IllegalArgumentException`；
- 空气的 `maxStackSize` 是 1，所以 `new ItemStack(Items.AIR)` 的 count 恒为 **0**
  （`setCount(1)` 也会被截回 0，`isEmpty()` 恒为 true），塞进去必然崩。

要视觉对齐只能另外注册一个隐形占位物品来补位，那会给模组引入一个非方块物品，目前的取舍是不加。

---

## 改展示名

编辑 `src/codegen/block_names.properties`：

```properties
level0_wall=Level0墙壁|Level0 Wall
```

格式是 `<方块id>=<中文>|<English>`，填的是"基础名"，**不要**带"展墙 / Exhibition Wall"这类变种后缀
（后缀由 `BlockVariants` 统一拼接，中英数据结构完全一致）。
没登记的 id 会用 `genBlockList` 按 id 自动推导（`moss_concrete_rubble` → `Mossy Concrete Rubble`）。

改完跑一次构建即可，语言文件由 datagen 重新生成。

---

## 构建环境说明（重要）

本工程的 Gradle 用户目录**放在工程内部**（`.gradle-home/`，已 gitignore），
这样构建不需要写 `~/.gradle`，在受限/沙箱环境里也能直接跑，不用额外申请工作区外权限。

```bat
gradlew-internal.bat runData
gradlew-internal.bat build
```

脚本会把 `GRADLE_USER_HOME` 指到 `.gradle-home/`，并自动挑一个 JDK 21 当 `JAVA_HOME`。
PowerShell 版是 `gradlew-internal.ps1`。用普通的 `gradlew.bat` 也可以，只是会回到 `~/.gradle`。

第一次用内部目录时，可以把已有缓存搬进来（可选，不搬就自己下载）：

```powershell
New-Item -ItemType Directory -Force .gradle-home | Out-Null
Copy-Item -Recurse -Force "$env:USERPROFILE\.gradle\caches" .gradle-home\
Copy-Item -Recurse -Force "$env:USERPROFILE\.gradle\wrapper" .gradle-home\
```

`.gradle-home` 约 3.4 GB，已被 gitignore。

> 上面这套只影响**本地开发**。GitHub Actions 上用的是普通的 `./gradlew`，
> 让 `gradle/actions/setup-gradle` 正常缓存 `~/.gradle`。

---

## CI / 发布

两个 workflow，都在 `.github/workflows/`：

| 文件 | 触发 | 做什么 |
| --- | --- | --- |
| `ci.yml` | push 到 `master`、任何 PR | `runData` → `build`，传一个 jar artifact |
| `release.yml` | 推任意 tag | 校验 tag 名 → `runData` → `build` → 建 GitHub Release |

**发布流程：tag 名就是版本号，不带 `v` 前缀。**

```bash
git tag 0.1
git push origin 0.1
```

这会发布版本 `0.1`：jar 叫 `brfurniture-0.1.jar`，
`META-INF/neoforge.mods.toml` 里的 `version` 也是 `0.1`。

实现方式：`build.gradle` 里

```groovy
version = project.findProperty('releaseVersion') ?: mod_version
```

发布时 CI 传 `-PreleaseVersion=${GITHUB_REF_NAME}`，平时用 `gradle.properties` 的 `mod_version`。
`mods.toml` 的模板展开也用 `project.version`，所以 jar 名和模组元数据不会对不上。

`release.yml` 里有一个 `Validate version` 步骤，只接受 `[0-9]+(\.[0-9]+)*`
（`0.1`、`1.2.3` 可以；`v0.1`、`0.1-beta` 会直接失败）。这是为了早点发现写错的 tag，
而不是发布一个版本号诡异的 jar。

**为什么两个 workflow 都先跑 `runData`**：`src/generated/resources`
（blockstate / 模型 / 语言文件）是 datagen 产物。如果它没有被提交进仓库，
只跑 `build` 会因为缺资源而产出不完整的 jar；显式重跑一遍能保证产物完整。
顺带也把"datagen 能不能过"纳入了 CI —— 典型的坑是贴图删了但
`config/brfurniture/blocks.txt` 还留着那一行，这种问题只有跑 datagen 才会暴露。

两个 workflow 都会在收尾时检查 `git diff -- src/generated`，
如果生成物和仓库不一致就发一条警告（说明有人忘了提交 datagen 产物），但不会让构建失败。

> `runData` 写入的是 `src/generated/resources`（仓库里的那份）。
> 所以**请把 `src/generated/resources` 提交进仓库**，否则本地拉下来直接 `build` 会缺资源。

---

## 已知取舍

- **`src/generated/resources` 是 datagen 的产物，建议提交进仓库**。
  `runData` 会重建它，并且会<b>删掉这次没有生成的旧文件</b>；
  所以如果你临时改小了 `blocks.txt` 再跑 datagen，生成物会跟着变少。
  改回配置后重新跑一次 `runData` 即可恢复。
- **创造栏没有做整行补位**，原因见上面「创造物品栏」一节（NeoForge 21.1 硬性要求
  物品堆 `count == 1`，而空气堆的 count 恒为 0）。
- **英文变种名会有点啰嗦**：`level0_wall` 的基础名本身是 "Level0 Wall"，
  所以展墙叫 "Level0 Wall Exhibition Wall"。这是"基础名 + 统一后缀"这个数据结构的直接结果，
  想更好看就得给每个变种单独写一个英文名。
- **`BlockVariants` 目前是写死的枚举**。再加变种需要同时改枚举、`BlockManager` 的注册分支、
  datagen 的模型分支。之所以不给它做插件式扩展，是因为三个变种各自的代码/模型差异很大，
  抽象成本高于收益。
- **警告界面挂在 `IConfigScreenFactory` 扩展点上**（模组列表 → 后室家具店 → 配置）。
  21.1 的 `ConfigurationScreen` 没有公开的"插入自定义警告"API，硬塞要依赖内部类，
  所以用官方的扩展点自己实现一个 Screen。
- **家具还没做**。`brfurniture:furniture` 页签已经建好占位，物品展示框当临时图标。
