# 构建说明

本项目不使用 Gradle，`build.ps1` 直接调用 JDK 与 Android SDK 命令：

构建开始时先运行 `tools/check-screenshot-policy.ps1`：支付宝 SDK 支付界面保留其自带防截屏保护，其余应用自有页面与测试允许截屏、录屏。自有源码新增截屏限制或测试临时修改截屏标志时，构建直接失败；这一检查持续适用于后续所有版本。

运行 `powershell -NoProfile -File tests/screenshot-policy.test.ps1` 可验证该规则：覆盖正常页面、SDK 例外、窗口标志、最近任务、Surface、设备策略，以及测试临时修改标志等合成场景。

1. 下载/校验固定支付宝 SDK，再用 `aapt2 compile` 分别编译应用与 SDK 资源；
2. `aapt2 link` 生成 APK（`resources.arsc` 以 ZIP stored 方式写入并对齐）；
3. `javac` 以 SDK classes.jar 为 classpath 编译源码；
4. `d8` 将应用与 SDK 一起生成 dex；
5. .NET `ZipArchive` 把 dex 追加到 APK；
6. 自定义对齐逻辑规范化 Windows aapt2 生成的嵌套 assets 路径，并执行 `zipalign -c 4`；
7. `apksigner` 签名并校验。

## 依赖

- Windows PowerShell 5.1 或 PowerShell 7
- JDK 17
- Android SDK：`aapt2`、`zipalign`、`d8`、`apksigner`、`android.jar`（API 36）

默认目录：

```text
toolchain/
  jdk/
    <jdk-17>/                # 解压后的 JDK 17（其下有 bin/javac.exe）
  sdk/
    android-16/
      aapt2.exe
      zipalign.exe
      core-lambda-stubs.jar
      lib/d8.jar
      lib/apksigner.jar
    android-36/android.jar
```

也可用 `-JavaHome` 指定 JDK；Android 工具路径在 `build.ps1` 顶部的 `$tools` 与 `$androidJar`。仓库不提交 `toolchain/`，请从本机 Android SDK 复制对应文件。

## 支付 SDK 特例

本次经用户明确批准，为校园卡支付返回接入官方 `com.alipay.sdk:alipaysdk-android:15.8.42`，不是一般引入新依赖的许可。SDK 从 Maven Central 的发布坐标取得，未运行 Maven 或 Gradle。`build.ps1` 调用 `tools/get-alipay-sdk.ps1`，每次验证 AAR SHA-256 后重新提取 classes.jar 与资源；首次需要联网，此后可用完整性校验通过的缓存离线构建。

- 官方 SDK 坐标：`https://repo.maven.apache.org/maven2/com/alipay/sdk/alipaysdk-android/15.8.42/`
- 固定 AAR SHA-256：`a6c301708b59a3a7cda843cf02e7b3b0b44afd96819f0a18890322e9868abf28`
- 缓存：`toolchain/alipay/15.8.42/`，二进制不进入源码仓库；不手改提取文件。
- `aapt2 --extra-packages com.alipay.sdk` 生成 SDK 资源类，SDK 资源与应用资源分别编译，避免同名 values 编译产物相互覆盖。
- Manifest 手工声明支付组件、支付宝单包可见性和可选 legacy HTTP 运行库；没有自动合并 SDK AAR 的权限，没有新增 Android 权限。`queries` 包查询不是 `QUERY_ALL_PACKAGES` 权限。
- SDK 升级、摘要变更或新增权限均须重新评审，不自动追随 latest。Windows PowerShell 源码保持 ASCII。

## 签名

签名私钥和密码不进入仓库。任选一种方式提供：

```powershell
# 方式一：参数
.\build.ps1 -Keystore D:\keys\my-release.p12 -KeyAlias xinxin

# 方式二：环境变量
$env:XINXIN_KEYSTORE = "D:\keys\my-release.p12"
$env:XINXIN_KEYSTORE_PASSWORD = "your-strong-password"
.\build.ps1
```

未提供时脚本会尝试 `_private/signing/development.p12`（该目录被 `.gitignore` 忽略）。密码只从 `XINXIN_KEYSTORE_PASSWORD` 读取，并通过 `apksigner` 的 `env:` 语法传入，不出现在命令行。

生成新密钥：

```powershell
keytool -genkeypair -keystore my-release.p12 -storetype PKCS12 `
  -alias xinxin -keyalg RSA -keysize 2048 -validity 10000
```

> 覆盖升级要求签名一致。发布后备份私钥；不要把 `.p12`、`keystore.properties` 或密码提交到任何仓库。

## 输出

`dist/心心相应-<versionName>.apk`，版本号读取自 `app/src/main/AndroidManifest.xml`。

## 常见问题

- **Android 14 安装报 `-124 ... resources.arsc ...`**：构建链必须由 `aapt2 link -o` 直接产出 APK，不能用 .NET 重新打包资源；当前脚本已按此实现。
- **Windows PowerShell 5.1 中文文件名乱码**：脚本用 Unicode 码点拼出输出文件名，保持 ASCII 源码。修改时不要直接写中文文件名。
- **嵌套 assets 无法读取**：部分 Windows aapt2 会在 APK 内写入反斜杠。对齐步骤将 assets 路径的本地头与中央目录同步改为 `/`，保留文件内容、CRC、压缩方式及资源表的 stored 状态；`assets/licenses/` 下的许可文件可按标准路径读取。
- **签名失败**：确认 `XINXIN_KEYSTORE_PASSWORD` 与 `-KeyAlias` 正确；私钥不要放在会被提交的目录。
