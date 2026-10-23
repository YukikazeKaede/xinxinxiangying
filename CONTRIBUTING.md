# 贡献与发布规范

本文件是「心心相应」的协作标准。代码怎么提、版本怎么发、什么绝对不能进仓库，以这里为准。每次改动前先读 [`docs/UPDATE_PROMPT.md`](docs/UPDATE_PROMPT.md)，把提示词交给 DeepSeek。

## 0. 选哪种协作方式

两个人、一个发布账号、一把签名私钥，最省事也最不容易出事的组合是：**同一个仓库 + 协作者权限 + 分支 PR + 维护者统一发版**。

| 方式 | 怎么运作 | 适合 | 问题 |
| --- | --- | --- | --- |
| 协作者 + PR（推荐） | 同学被加为 Write 协作者，在 clone 里开分支提 PR，你评审后 squash 合并 | 2–5 人、需要评审 | 要开分支保护，否则容易直接推到 `main` |
| Fork + PR | 同学 fork 到自己账号，改动推 fork，再向你仓库提 PR | 外部陌生人贡献 | 两个人用多一层同步，`main` 更新要手动拉 |
| 直接 push `main` | 双方都往 `main` 推 | 只有自己一个人 | 冲突和坏提交没人拦，发版容易翻车 |

结论：**用协作者 + PR**。同学有写权限、能直接开分支，你保留合并权和发布权；出问题时 revert 一个 squash 提交就够。

### 设置清单（维护者，一次性）

1. Settings → Collaborators and teams → **Add people**，加同学，权限 **Write**。
2. Settings → General → Pull Requests：只勾 **Allow squash merging**，关掉 merge commit 和 rebase（两种历史格式混用会让 `git log` 变乱）。
3. Settings → Branches → 给 `main` 加保护（或 Ruleset）：
   - Require a pull request before merging
   - Require approvals: 1
   - Dismiss stale approvals
   - 禁止 force push 与删除分支
4. 可选：Settings → Rules → Tags，保护 `v*` 只允许维护者创建，防止误打 tag。
5. 暂不上 GitHub Actions：本仓库没有 Gradle，构建要 JDK 17 + Android SDK + 私钥，CI 收益小，反而要把私钥塞进 Secrets。等有第三个人再考虑。

> 同学只改代码、不发版，**不需要签名私钥**。他用自己的测试密钥构建测试包即可（见 `docs/BUILD.md`），对外发布的 APK 始终由维护者在本机签。

## 1. 目录与真相来源

- 公开仓库 `https://github.com/YukikazeKaede/xinxinxiangying` 是唯一代码主线，默认分支 `main`。
- 构建所需的 `toolchain/`（JDK、Android SDK）和签名材料 `_private/` 只存在于各人本机，被 `.gitignore` 忽略，永不提交；成员自己按 `docs/BUILD.md` 准备。
- 不要在本地维护两份源码。日常开发就在 clone 出来的仓库里做，把 `toolchain/` 放进 clone 根目录即可构建。

## 2. 加入协作者

1. 维护者在 GitHub 仓库页 → **Settings → Collaborators and teams → Add people**，填对方 GitHub 用户名，权限选 **Write**。
2. 对方接受邀请后 clone，并设置提交身份（建议用 GitHub 提供的 noreply 邮箱，避免真实邮箱进入公开历史）：

```powershell
git config user.name "你的名字"
git config user.email "12345678+用户名@users.noreply.github.com"
```

3. 首次贡献建议从 `docs` 或 `tests` 类小改动开始，走一次完整 PR 流程。

权限约定：

| 动作 | 协作者 | 维护者 |
| --- | --- | --- |
| 建分支、提 PR | 可以 | 可以 |
| 合并 PR | 需维护者评审后合并 | 可以 |
| 改版本号、打 tag、发 Release、上传 APK | 不可以 | 可以 |
| 改签名、改仓库设置、删分支 | 不可以 | 可以 |

## 3. 分支与提交

分支从最新 `main` 切出，命名：

```text
feat/<主题>     新功能       feat/timetable-widget-week
fix/<主题>      修 bug       fix/update-sha-check
docs/<主题>     只改文档     docs/contributing
chore/<主题>    构建、脚本   chore/build-ascii
```

提交信息（Conventional Commits，中文描述）：

```text
<type>(<scope>): <一句话，≤50 字>

- 原因：为什么改
- 影响：用户或开发侧能感知的变化
- 验证：跑了什么、结果如何
```

`type` 用 `feat` / `fix` / `docs` / `refactor` / `perf` / `test` / `build` / `chore` / `revert`；`scope` 用 `timetable` / `academic` / `login` / `update` / `ui` / `build` / `docs` / `tests`。

一次提交只做一件事。不要把格式化、重命名和功能改动混在一个 PR 里。

## 4. PR 规范

标题与提交标题同格式。描述模板在 [`.github/pull_request_template.md`](.github/pull_request_template.md)，提 PR 时 GitHub 会自动带出，逐项填完再提交。

验证要求：

- 能在本机构建的，贴出 `.\build.ps1` 和相关测试的结果。
- 构建环境不全（没有 Android SDK 或签名材料）时，允许只做静态验证 + Node 测试（`node tests/timetable-extract.test.cjs`，或 `node --test tests/`），但必须在「未验证项与原因」写清楚，由维护者在合并前补一次构建。
- 不要为了凑勾选去改 `build.ps1` 绕过签名；签名缺失是环境问题，不是代码问题。

合并规则：

- 至少 1 名维护者评审通过；涉及登录、更新、签名、隐私的改动必须维护者本人验证。
- 用 **Squash and merge**，squash 后的标题沿用 PR 标题，正文保留「原因 / 影响 / 验证」。
- 合并后删除远端分支。`main` 建议开启分支保护：禁止直接 push、要求 PR、要求状态检查通过。

## 5. 版本与发版

版本号唯一来源是 `app/src/main/AndroidManifest.xml`：

- `versionCode` 每次发版 +1，只增不减，用于判断是否需要更新。
- `versionName` 用 `X.Y.Z`：第三位修 bug，第二位加功能，第一位表示不兼容或正式版。
- 协作者的 PR 不改版本号；维护者在发版时统一改，避免每个 PR 都冲突。

发版步骤（维护者执行）：

1. 合并本版所有 PR，确认 `main` 可构建。
2. 改 `AndroidManifest.xml` 的 `versionCode` / `versionName`。
3. 在 `docs/CHANGELOG.md` 顶部加一条，面向用户写，不写内部实现细节。
4. 构建并验证：`.\build.ps1` → 在旧版本设备上实测覆盖安装。
5. 建 GitHub Release，tag 用 `v<versionName>`（如 `v0.6.9`），标题「<versionName> <一句话>」。
6. 上传资产，文件名固定：
   - `xinxin_<versionName>.apk`
   - `update.json`
7. 用脚本生成清单，不要手写：

```powershell
.\tools\make-update-json.ps1 `
  -Url "https://github.com/YukikazeKaede/xinxinxiangying/releases/download/v0.6.9/xinxin_0.6.9.apk" `
  -NotesFile .\notes-0.6.9.txt `
  -OutFile .\update.json
```

8. 把该 Release 设为 **latest**，否则 `releases/latest/download/update.json` 会指向旧版。
9. 在旧版本上走一遍「检查更新 → 下载 → SHA-256 → 系统安装器 → 覆盖安装」。
10. 备份私钥与本次 APK；保留上一版 APK 以便回滚。

发版检查清单：

- [ ] `versionCode` 比线上大 1，`versionName` 与 tag、APK 名、update.json 三处一致
- [ ] APK 用与线上相同的私钥签名
- [ ] update.json 的 `url` 与资产文件名逐字一致（GitHub 不能重命名资产）
- [ ] `sha256`、`size` 来自实际 APK，没有手填
- [ ] Release 已设为 latest
- [ ] CHANGELOG 已更新，Notes 与 CHANGELOG 该条一致
- [ ] 旧版本实测覆盖安装成功

## 6. 红线

以下内容一律不允许出现在提交、PR、Issue、截图或 Release 里：

- 真实学号、姓名、成绩、课表、考试安排、校园卡信息
- 学校页面快照、抓包文件、Cookie、登录票据、密码
- 签名私钥、`keystore.properties`、任何密钥与密码
- `_private/`、`toolchain/`、`build/`、`dist/`、`*.apk`、`update.json` 等被忽略的构建与本机材料

测试数据只用合成账号（`SYNTHETIC_*`）和合成响应。提交前检查：

```powershell
git status --short
git diff --cached --stat
```

另外：

- 不 force push `main`，不改写已推送历史，不删他人的 tag 或 Release。
- 不新增第三方依赖，不引入 Gradle；构建链路保持 `build.ps1`。
- PowerShell 脚本保持纯 ASCII 源码，中文文件名用码点拼接。
- 涉及新增权限、改动登录或更新链路时，先在 Issue 或 PR 里说明方案再动手。

## 7. 分工建议

| 范围 | 主要文件 |
| --- | --- |
| 课表解析与展示 | `TimetableParser.java`、`TimetableModel.java`、`TimetableScreen.java`、`assets/timetable-*.js` |
| 教务同步 | `AcademicActivity.java`、`AcademicParser.java`、`AcademicRepository.java`、`assets/academic-api.js` |
| 登录与会话 | `SchoolWebActivity.java`、`SchoolLoginActivity.java`、`SchoolAccount.java`、`AppSession.java` |
| 应用内更新 | `UpdateChecker.java`、`UpdateFileProvider.java`、`docs/UPDATES.md` |
| 构建与发布 | `build.ps1`、`tools/*.ps1`、`docs/BUILD.md` |

改一个范围时，尽量不顺手改另一个范围，减少评审成本。
