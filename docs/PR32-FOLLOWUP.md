# PR #32 后续修改与验证

## 1. 改动摘要

- 全部学期成绩保留独立快照，切换单学期不再让工具页平均绩点消失；升级时在覆盖旧的全部学期缓存之前保留该快照。
- 成绩刷新保留父页面栈，功能页返回逐级退出，任务根页面恢复工具标签；左上角和系统返回采用同一入口。
- 电费首页填写楼栋和寝室号，按学校实际选项匹配六级信息。匹配完成后捕获预期楼栋/房间 ID，再调用学校原有查询；结果必须与预期 ID 一致才保存。选项重名、超时或匹配失败均保留旧余额。
- 四种 ABI 各出一个 APK，新版更新清单按设备支持顺序选包，旧版 arm64 客户端继续读取顶层字段。

本次超过 400 行的原因：增加六级异步级联匹配和跨页面回归，移除电费 Activity 内已停用的旧门户读取代码，并同时完成用户要求的四架构构建与更新适配。

## 2. 改动文件

下面的行数相对 PR 原始提交 4ed7f2b 统计，不包含本报告。

| 文件 | 增删行 |
| --- | --- |
| `CONTRIBUTING.md` | +2 / -2 |
| `README.md` | +3 / -3 |
| `app/src/main/java/cn/xinxinxiangying/app/AcademicActivity.java` | +1 / -1 |
| `app/src/main/java/cn/xinxinxiangying/app/AcademicParser.java` | +7 / -3 |
| `app/src/main/java/cn/xinxinxiangying/app/AcademicRepository.java` | +17 / -2 |
| `app/src/main/java/cn/xinxinxiangying/app/ActivitiesActivity.java` | +1 / -1 |
| `app/src/main/java/cn/xinxinxiangying/app/DektActivity.java` | +3 / -5 |
| `app/src/main/java/cn/xinxinxiangying/app/ElectricityActivity.java` | +54 / -253 |
| `app/src/main/java/cn/xinxinxiangying/app/ElectricityStore.java` | +9 / -1 |
| `app/src/main/java/cn/xinxinxiangying/app/MainActivity.java` | +6 / -4 |
| `app/src/main/java/cn/xinxinxiangying/app/SchoolLoginActivity.java` | +1 / -1 |
| `app/src/main/java/cn/xinxinxiangying/app/SchoolWebActivity.java` | +69 / -8 |
| `app/src/main/java/cn/xinxinxiangying/app/UpdateChecker.java` | +4 / -3 |
| `build.ps1` | +46 / -23 |
| `docs/BUILD.md` | +8 / -4 |
| `docs/CHANGELOG.md` | +8 / -0 |
| `docs/UPDATES.md` | +19 / -2 |
| `docs/UPDATE_PROMPT.md` | +1 / -1 |
| `tests/android/ElectricityInstrumentation.java` | +101 / -127 |
| `tools/get-onnxruntime.ps1` | +6 / -3 |
| `tools/make-update-json.ps1` | +26 / -2 |
| `update.json.example` | +24 / -2 |
| `app/src/main/assets/electricity-room-match.js` | +132 / -0 |
| `app/src/main/java/cn/xinxinxiangying/app/UpdateArtifact.java` | +17 / -0 |
| `tests/UpdateArtifactTest.java` | +23 / -0 |
| `tests/electricity-room-match.test.cjs` | +119 / -0 |

## 3. 影响面

- 保持包名、版本号、Android 权限、签名配置、更新清单地址与依赖版本。
- 新的全部学期快照和寝室输入信息沿用现有账号隔离存储；清除成绩会同时清除总绩点快照，退出清除仍清除所有账号数据。
- 匹配前三项限定为截图中的“新电费缴纳 / 奉贤校区 / 住宿区”，按名称选择学校返回的真实条目。楼层先尝试房间号提示，最终必须在学校返回的房间列表里找到完整房号，找不到时检查其他实际楼层。
- 充值金额、学校绑定寝室确认与支付由本人完成；不会自动充值或点击支付。
- 支付宝 SDK 支付界面保留其自带防截屏保护；其他应用页面和测试允许截屏、录屏。

## 4. 验证记录

- Windows PowerShell 5.1 调用 `build.ps1`：四种架构构建、ZIP 对齐与签名校验通过；私钥由本机已有包装脚本提供，未提交到仓库。
- `node --test tests/*.test.cjs`：全部通过，新增寝室匹配 14 项，包括六级真实选项、Vue 2/3、索引 0、重复轮询、已绑定寝室重选、楼层回退、重名、缺失、超时、变更后拒绝查询和来源限制。
- Java 主机测试：AcademicParser、ActivityFeed、DektParser、DektRecordParser、ElectricityParser、SchoolCatalog、SessionPolicy、TimetableModel、TimetableParser、UpdateArtifact，共 446 项断言通过。
- `tests/android/build-test.py`：仪器测试编译通过。
- 专用空白模拟器 `ElectricityInstrumentation`：修复文档提交前的 history 回调竞态后，36 项断言连续两次通过。覆盖多次学期切换、旧总成绩迁移、账号隔离、工具页绩点、成绩刷新后原栈保留、左上角返回和系统返回、电费两输入框、六级匹配与余额返回、错误保留缓存、清除缓存、页面默认截图。
- `tests/screenshot-policy.test.ps1`：10 项规则用例与实际源码检查通过；所有构建继续调用固定检查。
- 四个实际 APK：各自恰好包含指定 ABI 的两份 `.so`，原生库为 deflate，资源表保持 stored，无重复 ZIP 条目，模型未变化。
- `tools/make-update-json.ps1 -AllAbis`：实际四包生成清单成功，大小与 SHA-256 一致，顶层指向 arm64；缺包和错误架构的负向检查均拒绝生成。
- 正式 0.8.0 与新包证书摘要一致；专用模拟器安装正式 0.8.0 后，以新 arm64 包覆盖安装成功。
- 未验证：真实学校登录后的在线寝室查询、真实余额与充值到账；ARM/32 位及 x86 的物理设备运行；新版本真实线上更新下载链路。本次没有发布 Release 或替换线上清单。

复现验收：打开工具页并记下总绩点 → 学分绩点中切换多个学期 → 左上角或系统返回 → 工具页仍显示原总绩点。进入宿舍电费填写楼栋、寝室号并查询 → 核对学校匹配的六级名称和房间 → 返回显示余额；输入不存在的编号时保留上次结果。充值只在本人核对房间和金额后操作。

## 5. 文档更新

README、CHANGELOG 的未发布条目、BUILD、UPDATES、UPDATE_PROMPT、贡献发布命名与更新清单样例已同步。

## 6. 版本建议

本次为 PR 后续修改，不发版。Manifest 仍是 versionName 0.8.0 / versionCode 26；正式发版由维护者统一递增。CHANGELOG 已在“未发布”记录本次内容。

## 7. Git

在 `fix/campus-portal-login` 分支追加维护者提交，保留成员原始提交。原工作区的未提交修改保持原状。分开记录页面修复与四架构构建更新，最终在 PR #32 继续评审；本次不合并。

## 8. 发布物料

本次仅提供本地测试包：`dist/pr32-test/` 下四个 `xinxin-pr32-<abi>.apk` 与 SHA256SUMS.txt。它们使用正式同一签名，但不是新版本 Release。正式发布文件名和多架构更新清单生成步骤见 UPDATES。

## 9. 风险与回滚

学校前端结构或选项名称变化会导致匹配停止并提示，不会猜选首项或覆盖旧余额。旧版已被单学期数据覆盖、且从未保留全部学期结果的缓存，需先读取一次全部学期成绩，无法仅从单学期数据还原总绩点。回滚时按顺序 revert 本次维护者追加的提交；既有完整快照及旧寝室结构仍可读取。

## 10. 待确认项

真实学校匹配和充值需本人验收。PR 原贡献者的 CLA 同意仍需按贡献规范确认后再合并。
