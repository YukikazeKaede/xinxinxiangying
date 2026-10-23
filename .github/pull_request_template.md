## 改了什么

<!-- 1–3 条，用户视角；这就是改完后 docs/CHANGELOG.md 里那一行 -->

## 为什么

<!-- 现象、issue 编号或使用场景 -->

## 影响面

- 用户可见：
- 兼容性：minSdk 24 / targetSdk 36 是否受影响
- 数据与权限：是否新增权限、是否改存储结构、是否需要迁移
- 是否涉及登录 / 更新 / 签名链路（涉及则需维护者本人验证）

## 验证

- [ ] `.\build.ps1` 通过
- [ ] 相关测试通过：<!-- 写出用例名，如 tests/TimetableParserTest.java、tests/timetable-extract.test.cjs -->
- [ ] 手工路径：<!-- 可复现的步骤 -->
- [ ] 未验证项与原因：<!-- 没有就写「无」 -->
- [ ] 旧签名可覆盖安装（发版 PR 必填）

## 版本

- [ ] 本 PR 不改 versionCode / versionName（发版 PR 除外）

## 自查

- [ ] 没有提交 `_private/`、`toolchain/`、`dist/`、`*.apk`、`*.p12`、`update.json`
- [ ] 没有真实学号、姓名、成绩、课表、Cookie、抓包、截图
- [ ] 没有与需求无关的重构、改名、批量格式化
- [ ] 涉及的文档已同步（CHANGELOG / BUILD / UPDATES / PRIVACY / README）
- [ ] 提交信息符合 `<type>(<scope>): <一句话>` 格式
