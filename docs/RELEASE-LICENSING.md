# 0.6.11 许可迁移发布说明

本次版本为 `0.6.11` / `versionCode 17`，发布日期为 2026-10-24（Asia/Shanghai）。原 MIT 版本基准为 `v0.6.10` / `9ccebef289041b057cdc079ab0e3fa7c326a657c`；许可迁移提交为 `1c17e46f771bbb8db8e8d83cd9ff3c2774688089`，经 PR #14 合并。实际版本号以 AndroidManifest.xml 为准。

## 发布前核对

- 核对原 MIT 声明与多位贡献者的权属、第三方条件；未取得的新商业再许可权利不能写入商业授权范围。保留原标签、Release 及迁移前其他 MIT 代码的权利。
- 确定新版本、日期及许可迁移提交 SHA，确认迁移后的新贡献有 [CLA](CLA.md) 明确同意记录。维护者签署商业合同前应使用真实法律主体，核对合同中的授权范围和适用法律。
- 对照 [LICENSING.md](LICENSING.md) 检查 README、发行说明与商业咨询入口；不要声称所有旧代码已禁止商用。
- `app/src/main/assets/licenses/` 中 LICENSE 和 LICENSE-MIT-LEGACY 应分别与根目录同名文件逐字节相同。现有 build.ps1 的 assets 打包流程会将其加入 APK；从最终 APK 解压核对文本与 NOTICE。
- 维护者使用原正式签名构建新版本，按 CONTRIBUTING.md 实测覆盖安装，再生成真实 update.json；测试签名包不能作为正式更新发布。
- 按实际版本创建新 tag 和 Release，发布下列公告；不要重写或替换旧 MIT 版本。若继续用 MIT 发布旧分支补丁，另行记录其许可。

## Release 公告正文

本版开始采用新的源码可用许可模式：由维护者有权按新条款提供的新增作品及后续修改，依 PolyForm Noncommercial License 1.0.0 发布。条款范围内的非商业用途、列明的个人用途及机构用途可按该许可使用；超出许可范围的商业使用，须另行取得有权授权方的书面许可。

此前已按 MIT 发布的代码继续享有原许可，包括 v0.6.10（提交 9ccebef289041b057cdc079ab0e3fa7c326a657c）及其他迁移前合法按 MIT 发布的代码。本次变更不追溯撤销其使用、修改、分发和商业化权利。第三方内容仍按各自许可处理。

完整条款与范围见仓库 LICENSE、LICENSE-MIT-LEGACY 和 docs/LICENSING.md。商业授权咨询请通过项目 GitHub Issue 联系维护者，公开 Issue 中不要提交保密或个人资料。
