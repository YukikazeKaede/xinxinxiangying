# 0.7.0 验证记录

版本：`versionCode 19` / `versionName 0.7.0`。基线：`v0.6.12`（`4c85e9a1f896a59c771c469c09486155ae749338`）。

## 整合范围

第二课堂功能来自本机旧源码副本。该副本基于首次整理后的 0.6.8 源码，不能整目录覆盖当前仓库。本次只迁入新增学分、活动、报名模块及其入口、路由和测试，保留 0.6.12 主线。

修复了重复报名触发、账号切换后的旧结果写入、跨账号清除学分缓存、手机时区影响报名状态、搜索清空后的数量提示及错误响应覆盖活动缓存。第二课堂两个页面保留 `FLAG_SECURE`。

没有新增权限、第三方依赖或存储迁移。新增独立的账号缓存，不改既有登录、更新和签名实现。整合涉及多组新增功能和测试，因此文件数及行数超过普通单项修改建议。

## 自动检查

- JavaScript：8 个 `*.test.cjs` 文件全部通过；Node 汇总 10/10。其中活动脚本包含 13 个合成场景，覆盖成功、拒绝、异常响应、重复提交、活动 ID 不一致和域名限制。
- Java：6 组共 291 项断言通过：AcademicParser 42、SchoolCatalog 51、TimetableParser 49、TimetableModel 80、DektParser 26、ActivityFeed 43。
- 修复 3 份旧仪器测试对 `AppSession.invalidate` 的过期调用；全部 8 份仪器测试源码编译通过。
- `git diff --check` 通过。

脚本测试命令：

```powershell
$tests = @(Get-ChildItem tests -File -Filter '*.test.cjs' | ForEach-Object FullName)
node --test @tests
```

Java 测试使用 JDK 17 和本机 `toolchain/json-test.jar`，编译 Parser、Model、Catalog、ActivityFeed 与 `tests/*Test.java` 后运行对应测试入口。

## Android 运行验证

使用雷电 14 的全新独立实例和合成账号，不接入真实学校账号。正式包与测试包的 DEX、清单、资源及 assets 逐项字节相同，仅签名不同。

| 测试 | 结果 | 覆盖 |
| --- | --- | --- |
| SecondClassroomInstrumentation | 25 项通过 | 账号与学生类型隔离、旧账号写入拒绝、当前缓存清除、学分展开、搜索、报名状态、结束状态、防截屏、真实 WebView 中的合成报名响应与双触发只提交一次 |
| WeekSwipeInstrumentation | 38 项通过 | 窄屏、切周、首末周、取消、短拖、动画及滚动位置等 0.6.12 回归 |
| CampusSummaryInstrumentation | 11 项通过 | 教学周、今日课程与工具页摘要 |
| PaymentCodeReuseInstrumentation | 45 项通过 | 使用合成页面验证付款码运行环境复用与清理 |

第二课堂预览模式另运行一次，保留合成缓存与入口。工具页布局已用 Android 截图核对。Computer 能定位模拟器窗口，但该窗口捕获返回黑屏，未据此宣称完成视觉验证。

```powershell
adb -s <独立测试设备> shell am instrument -w cn.xinxinxiangying.app.test/.SecondClassroomInstrumentation
adb -s <独立测试设备> shell am instrument -w cn.xinxinxiangying.app.test/.WeekSwipeInstrumentation
adb -s <独立测试设备> shell am instrument -w cn.xinxinxiangying.app.test/.CampusSummaryInstrumentation
adb -s <独立测试设备> shell am instrument -w cn.xinxinxiangying.app.test/.PaymentCodeReuseInstrumentation
```

仪器包与测试应用需使用同一测试签名。不要在保存真实账号或数据的设备上运行合成测试。

## 发布包

- 原 `build.ps1` 正式签名构建通过；包名、minSdk 24、targetSdk 36 不变。
- APK v2/v3 签名、ZIP 对齐及 CRC 检查通过；`resources.arsc` 保持 stored。
- 签名证书 SHA-256 与 0.6.12 一致：`149754a0b3a6ddd6b755ac36fc749639562347112a8794004891e03b0d557bb5`。
- 包内 LICENSE、LICENSE-MIT-LEGACY、NOTICE 与源文件逐字节一致；两份第二课堂脚本与最终源文件一致。
- `TimetableScreen`、`TimetableModel`、`PaymentCodeWebView`、`SchoolWebActivity`、`SchoolLoginActivity`、`AppSession`、`LocalProfile` 与 0.6.12 基线一致。
- 正式 APK：182009 字节，SHA-256 `120645409934e1a04f5fd1bec86160470a45743817f76369ee46faf81cdc23b9`。
- 在保留正式 0.6.12 的独立实例上覆盖安装 0.7.0 成功，首次安装时间不变，新版进程正常启动；空白账号基线按预期进入学校登录页。
- `update.json` 通过 `tools/make-update-json.ps1` 从最终 APK 自动生成，版本、地址、大小、SHA-256 与 CHANGELOG 说明核对一致。

## 未验证

真实校园网下的第二课堂页面、学校接口和实际报名结果；实际报名可能改变学校业务数据，本次全部使用合成响应。未在真机验证覆盖安装、完整应用内更新链路、无障碍及不同厂商系统体验。学校接口或页面变化仍可能需要适配。

其余旧仪器测试完成编译，未计入本次运行通过数量。
