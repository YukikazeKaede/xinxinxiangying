# 源码公开范围与许可

本项目后续新增作品及修改按授权范围采用源码可用许可，完整范围见 [LICENSING.md](LICENSING.md)。本文件沿用历史路径，公开源码不表示允许任意商业使用；此前 MIT 授权继续有效。

提交 `app/`、`tests/`、`tools/`、`docs/`、`build.ps1`、`README.md`、`LICENSE`、`LICENSE-MIT-LEGACY`、`.gitignore` 和 `update.json.example`。APK 中的 `assets/licenses/` 保留新许可原文、原 MIT 声明及适用范围说明。

不要上传真实课表、成绩、考试安排、学校网页快照、个人截图、抓包、Cookie、登录票据、密码、环境变量配置、数据库或签名私钥。测试数据应使用合成账号和合成响应。

`_private/`、`toolchain/`、`build/`、`dist/` 和本机编辑器、代理会话目录均不进入源码仓库。APK 如需发布，应在单独检查后上传到 Releases，而不是提交到源码目录。

此前准备公开源码时删除了本地个人导出、页面快照、调试分析和历史归档。签名私钥保留在被忽略的本机目录中，以便维护现有应用的升级签名；它不是公开源码的一部分。普通文件删除不等于磁盘安全擦除，也不会清理设备、云备份或已公开的 Git 历史。

学校公开服务地址、Android 包名以及本项目 GitHub 更新地址属于运行配置，保留在源码中。仓库历史中的提交者邮箱属于 Git 元数据，修改当前文件不会删除历史元数据。

## 支付宝官方 SDK

本轮经用户批准的依赖例外是官方 `com.alipay.sdk:alipaysdk-android:15.8.42`，仅用于校园卡 H5 转原生支付及返回回调。它不是本项目原创开源代码，也不随本项目许可证重新授权；依 SDK 自身条款使用。发布坐标及其 POM 中的发行方和许可链接可在 [Maven Central](https://repo.maven.apache.org/maven2/com/alipay/sdk/alipaysdk-android/15.8.42/alipaysdk-android-15.8.42.pom) 核对，开发文档在 [支付宝文档中心](https://opendocs.alipay.com/common/02km9l)。发布 APK 包含 SDK 编译代码及资源，维护者发布前须核对 SDK 适用条款与隐私披露。

原始 AAR、提取的 classes.jar 和资源均放在忽略的 `toolchain/alipay/`，不复制到公开源码目录。来源、固定摘要和构建步骤在 [BUILD.md](BUILD.md)；处理数据及使用时机在 [PRIVACY.md](PRIVACY.md)。没有引入 Gradle 或 Maven 构建工具，不改变本项目自身代码的许可范围。
