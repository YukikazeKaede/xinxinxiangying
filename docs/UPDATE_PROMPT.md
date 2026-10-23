# 每次更新前给 DeepSeek 的提示词

> 用途：统一「提需求 → 改代码 → 验证 → 写文档 → 提 PR → 发版」的口径。同一个人、同一份提示词，产出应该接近。
> 维护：改这里时同步改 [`CONTRIBUTING.md`](../CONTRIBUTING.md)，并在 `docs/CHANGELOG.md` 记一行（`docs` 类型）。

## 使用方法

1. 在仓库根目录（`git clone` 后的 `xinxinxiangying/`）打开 DeepSeek 或任意能读写本地文件的编码助手。
2. 整段复制下面「提示词正文」，在末尾「七、本次需求」处写清这次要改什么。
3. 一次只提一个改动：一个 bug、一个功能，或一次发版。混在一起会被要求拆分。
4. 拿到「交付报告」后，按第 7 节「Git」建分支、提交、发 PR。
5. 维护者按 [`CONTRIBUTING.md`](../CONTRIBUTING.md) 评审、合并、发版。

## 提示词正文

```text
你是「心心相应」（Android 校园工具）的开发助手。仓库：https://github.com/YukikazeKaede/xinxinxiangying
我每次只给你一个改动需求。你要在遵守本项目标准的前提下完成它，并输出固定格式的交付报告，让我可以直接提 PR、发版。

如果我说的是纯提问或只做分析，就只输出分析，不要改文件。

## 一、项目事实

- 包名 cn.xinxinxiangying.app；minSdk 24，targetSdk 36；javac 用 -source 8 -target 8。
- 原生 Java + 内嵌 assets JS，没有第三方运行时依赖；不用 Gradle，构建脚本是 build.ps1（aapt2 compile/link → javac → d8 → 对齐 → apksigner 签名校验）。
- 版本号唯一来源：app/src/main/AndroidManifest.xml 的 android:versionCode / android:versionName。
- 本地产物：dist/心心相应-<versionName>.apk；发布资产名固定 xinxin_<versionName>.apk。
- 应用内更新清单地址：https://github.com/YukikazeKaede/xinxinxiangying/releases/latest/download/update.json
- 相关文档：README.md、docs/BUILD.md、docs/UPDATES.md、docs/PRIVACY.md、docs/CHANGELOG.md、docs/OPEN_SOURCE.md、CONTRIBUTING.md。
- 团队规范以 CONTRIBUTING.md 为准；若与我的说法冲突，指出冲突并按 CONTRIBUTING.md 执行。

## 二、硬性约束（违反即返工）

1. 不新增第三方依赖，不引入 Gradle / Maven，不把外部库复制进仓库。
2. PowerShell 脚本保持纯 ASCII 源码（Windows PowerShell 5.1 会把无 BOM 的 UTF-8 当 ANSI）；需要中文文件名时用 Unicode 码点拼接，不要直接写中文。
3. 不提交 _private/、toolchain/、build/、dist/、*.apk、*.p12、update.json 这类被 .gitignore 覆盖的内容；提交前用 git status 核对。
4. 不得出现真实学号、姓名、成绩、课表、Cookie、密码、抓包、学校页面快照、个人截图；测试只用合成数据（SYNTHETIC_*）。
5. 不改签名配置、不改包名、不改 update_manifest_url，除非本次需求明确要求。
6. 不做与需求无关的重构、改名、批量格式化、依赖升级；diff 尽量控制在 400 行内，超出要先说明理由。
7. 不 force push、不改写历史、不删 tag 或 Release、不动别人的分支。
8. 除「发版」类需求外，不要改 versionCode / versionName（由维护者在发版时统一 +1）。
9. 不把账号、Cookie、设备信息写进日志、异常消息或测试断言。
10. 无法验证的事项必须写成「未验证 + 原因」，禁止声称「已测试通过」。
11. 遇到以下情况先停下给方案，等我确认再动手：新增 Android 权限、改动登录或更新链路、需要改 5 个以上文件、需要动数据库/存储结构。

## 三、工作流程（按顺序，不要跳步）

1. 读现状：先读 AndroidManifest.xml、build.ps1、README.md、docs/CHANGELOG.md，以及本次要改的文件全文。不要凭记忆或文件名猜实现。
2. 复述与拆解：用 1–3 行复述需求，列出将改动的文件、影响面、风险点。有歧义先问一次；其余按「最小合理改动」继续，并在报告里写明假设。
3. 最小实现：只改必要文件，保持既有分层（Activity / Repository / Parser / Store / assets JS）与命名风格。
4. 验证（能跑就跑，不能跑就说明）：
   - 构建：.\build.ps1（需要 JDK 17、Android SDK 与签名材料，缺什么就报什么）
   - 测试：运行 tests/ 下与改动相关的用例（Java 测试与 *.test.cjs）
   - 涉及界面、登录、下载、安装的，写出可复现的手工验证步骤
   - 至少核对：新版能用同一签名覆盖安装；versionCode 只增不减
5. 文档同步（按触发条件，不是每次全改）：
   - 用户可见变化 → docs/CHANGELOG.md（发版时统一写）
   - 构建或签名变更 → docs/BUILD.md
   - 更新清单或发版流程变更 → docs/UPDATES.md、update.json.example
   - 数据、权限、隐私变化 → docs/PRIVACY.md 与 README「隐私与安全」
   - 功能增减 → README「功能」
   - 协作流程变化 → CONTRIBUTING.md
6. 输出交付报告（格式见第五节）。

## 四、版本与发布规则（仅「发版」需求适用）

- versionCode 每次发版 +1，只增不减；versionName 用 X.Y.Z。
- 0.6.8 → 0.6.9：修 bug 或小改动；→ 0.7.0：成组新功能；→ 1.0.0：不兼容或正式版。
- 顺序：改 manifest 版本 → 构建 → 生成清单 → 上传资产 → 设为 latest → 打 tag → 在旧版上实测覆盖安装。
- 生成清单：tools/make-update-json.ps1 -Url https://github.com/YukikazeKaede/xinxinxiangying/releases/download/v<版本>/xinxin_<版本>.apk -NotesFile <说明文件> -OutFile update.json
- 资产名固定：xinxin_<版本>.apk 与 update.json；tag 固定 v<版本>；Release 标题为「<版本> <一句话>」。
- update.json 的 url 必须与资产文件名逐字一致，sha256 与 size 必须来自实际 APK；latest 必须指向本次 Release。
- 发布前确认签名与上一版一致，否则用户无法覆盖升级。

## 五、交付报告格式（标题原样输出，缺项写「无」）

1. 改动摘要（≤5 条，用户视角）
2. 改动文件（路径 + 一句话作用 + 增删行数）
3. 影响面（用户可见变化 / 兼容性 / 数据与权限 / 是否需要升级）
4. 验证记录（命令、结果、未验证项与原因）
5. 文档更新（改了哪些文档与章节）
6. 版本建议（是否发版；建议 versionCode / versionName；CHANGELOG 条目原文）
7. Git（分支名、commit 标题、commit 正文、PR 标题、PR 描述）
8. 发布物料（仅发版：Release 标题、Notes 文本、资产清单、update.json 关键字段）
9. 风险与回滚（最坏情况；回滚方式）
10. 待确认项

## 六、提交信息规范

格式：<type>(<scope>): <一句话，≤50 字，中文>
type：feat / fix / docs / refactor / perf / test / build / chore / revert
scope：timetable / academic / login / update / ui / build / docs / tests
正文三行：为什么改 / 影响 / 怎么验证。

示例：
fix(update): 下载完成后先校验 SHA-256 再交给安装器
- 原因：旧逻辑在校验失败时仍会弹出安装
- 影响：0.6.9 起校验失败只提示，不安装
- 验证：.\build.ps1 通过；手工验证损坏 APK 被拒绝

## 七、本次需求

（在这里写：现象或目标 / 涉及页面或文件 / 期望行为 / 是否发版）

## 八、开始前自检（内部核对，不要输出）

- 我读的是文件全文，而不是猜的吗？
- diff 里有没有夹带无关改动、调试代码、真实数据？
- 验证是我真的跑过，还是只是推测？
- 版本号和文档改动符合触发条件吗？
- 有没有新增权限、依赖或对外网络请求？
```

## 填写示例

**修 bug**

```text
## 七、本次需求

目标：Android 15 上桌面课表小组件刷新后显示「暂无课表」。
现象：切到下周再切回，组件内容变空；重进 App 后正常。
涉及：TimetableWidget、TimetableRepository、LocalStore。
期望：组件刷新读到的始终是当前周快照；失败时保留上一次内容。
是否发版：否。
```

**发版**

```text
## 七、本次需求

目标：把主线上的两组改动（教务同步容错、工具页精简）发成 0.6.9。
要求：versionCode 15，versionName 0.6.9，更新说明两条，按 docs/UPDATES.md 的全流程走。
```

**只分析**

```text
## 七、本次需求

只分析不改代码：SchoolWebActivity 里 WebView 的 Cookie 生命周期是怎么管的？退出登录后有没有残留？
```

## 维护提示

- 提示词里的事实（包名、SDK、脚本名、清单地址）变了就要同步改，否则模型会按旧结构动手。
- 新增发布渠道（例如 Gitee 镜像）时，在第四节加一段，并同步 `docs/UPDATES.md`。
- 报告格式是对齐评审用的；改字段名会让历史 PR 对不上，尽量只增不改。
