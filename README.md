# 心心相应

面向上海应用技术大学本科生的独立 Android 校园工具。原生 Java 实现，无第三方运行时依赖，课表和教务数据保存在本机。

> 非学校官方项目，与上海应用技术大学无隶属关系。学校页面、接口和登录流程可能随时调整；请遵守学校账号使用规定，风险自负。

## 功能

- **课表**：按周查看、当前周高亮、学期与开学日期设置、调课/停课、课前提醒、桌面小组件、分享与备份。
- **教务同步**：登录学校账号后，自动读取课表、成绩（学分绩点）与考试安排，按账号保存在本机；某一项失败不会清空已有数据。
- **工具页**：考试安排、学分绩点、校园卡、付款码、宿舍电费等学校网页服务入口。
- **宿舍电费**：自动选择新电费缴纳、奉贤校区和住宿区，成功后仅显示楼栋、楼层与房间选择；失败时保留完整表单。
- **个人资料**：姓名在同步课表后显示于学号上方；点击头像可从系统选图并仅保存在本机。工具与个人页不再显示已保存教务页面入口。
- **应用内更新**：可配置 HTTPS 更新清单，发现新版本后弹窗提示、下载、校验 SHA-256 并交给系统安装器。
- **研究生入口**：学校页面验证当前学号后，可选择先进入应用，避免被课表识别阻塞；也可继续在学校页面导入课表。真实研究生登录和课表兼容性尚待账号持有人验收。

## 隐私与安全

- 不包含统计、广告或远程埋点；除学校网站和应用更新清单外，不主动连接其他服务器。
- 学校账号密码只用于学校统一认证。勾选“记住密码”后，使用 Android Keystore 加密保存在本机；不写入日志、备份或更新清单。
- 课表、成绩、考试、登录 Cookie 均保存在应用私有目录；退出登录或清除数据会删除本机记录。
- 姓名与头像按账号保存在应用私有存储，不上传；退出清除时一并删除。自定义头像无需新增相册或存储权限。
- 应用更新只接受 HTTPS 清单，下载完成后校验清单中的 SHA-256，再调用系统安装器；安装未知来源应用需要用户授权。
- 仓库和构建产物不包含任何真实学号、姓名、成绩、Cookie 或签名私钥。测试使用合成账号（`SYNTHETIC_*`）。

## 构建

要求：

- Windows PowerShell 5.1 或 PowerShell 7
- JDK 17
- Android SDK：`aapt2`、`zipalign`、`d8`、`apksigner`、`android.jar`（API 36）

仓库不提交 `toolchain/`。默认目录布局：

```text
toolchain/
  jdk/                       # 解压后的 JDK 17（其下有 <jdk-17>/bin/javac.exe）
  sdk/
    android-16/              # build-tools 目录
      aapt2.exe
      zipalign.exe
      core-lambda-stubs.jar
      lib/d8.jar
      lib/apksigner.jar
    android-36/android.jar
```

也可以设置 `JAVA_HOME`，或用 `-JavaHome` 指定 JDK。构建脚本当前按上面的相对路径查找 Android 工具；如需其他位置，可修改 `build.ps1` 顶部的 `$tools` 和 `$androidJar`。

签名材料不进入仓库。首次构建前自行生成密钥并设置环境变量：

```powershell
keytool -genkeypair -keystore my-release.p12 -storetype PKCS12 `
  -alias xinxin -keyalg RSA -keysize 2048 -validity 10000

$env:XINXIN_KEYSTORE = "D:\keys\my-release.p12"
$env:XINXIN_KEYSTORE_PASSWORD = "your-strong-password"

.\build.ps1
```

输出：`dist/心心相应-<versionName>.apk`。脚本会依次执行资源编译、aapt2 链接、javac、d8、ZIP 对齐、apksigner 签名与校验。

> 更新安装要求新旧 APK 使用同一签名。发布后请妥善备份私钥；丢失私钥将无法覆盖升级。

## 应用内更新

在 `app/src/main/res/values/strings.xml` 中配置：

```xml
<string name="update_manifest_url" translatable="false">https://example.com/update.json</string>
```

留空时“检查更新”只提示未配置，不会联网。清单格式见 [`update.json.example`](update.json.example)，可用 [`tools/make-update-json.ps1`](tools/make-update-json.ps1) 从 APK 自动生成版本、大小和 SHA-256。托管、发版和灰度细节见 [`docs/UPDATES.md`](docs/UPDATES.md)。

## 目录

```text
app/                     Android 源码（Java、assets、res、Manifest）
build.ps1                无 Gradle 构建脚本
tools/                   更新清单与构建辅助脚本
tests/                   解析器、模型、脚本与仪器测试（合成数据）
docs/                    构建、隐私、更新与版本说明
CONTRIBUTING.md          协作、评审与发版规范
update.json.example      更新清单模板
```

## 测试

`tests/` 中的 Java/CJS/仪器测试使用合成学校响应和合成账号，不包含真实学校凭据。运行方式取决于本机工具链；仓库不附带模拟器或学校页面快照。

## 贡献

分支、提交、PR、评审与发版规则见 [`CONTRIBUTING.md`](CONTRIBUTING.md)。每次改动前把 [`docs/UPDATE_PROMPT.md`](docs/UPDATE_PROMPT.md) 里的提示词连同需求一起交给 DeepSeek，按它给出的交付报告提 PR，保持口径一致。

## 许可证

[MIT](LICENSE)。本项目不包含学校官方素材、原版应用代码或反编译产物。
