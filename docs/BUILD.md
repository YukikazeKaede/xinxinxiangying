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

## 本机验证码识别的推理运行时

可选的验证码识别需要在设备本地跑一个 ONNX 模型，因此有第二个经用户批准的依赖例外：官方 `com.microsoft.onnxruntime:onnxruntime-android:1.31.1`（MIT）。它不是一般性允许新增依赖，也不引入 Gradle 或 Maven 构建工具。

- 坐标：`https://repo.maven.apache.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/1.31.1/`
- 固定 AAR SHA-256：`d4d50660300ed2c4f38ffdfd5ab5bfa3a22f995f282dda24f42ea3ea55467b49`
- 缓存：`toolchain/onnxruntime/1.31.1/`（`classes.jar` 与 `arm64-v8a/*.so`），二进制不进入源码仓库
- `build.ps1` 调用 `tools/get-onnxruntime.ps1`，每次校验摘要后重新提取；首次需要联网，之后可用校验通过的缓存离线构建

`aapt2` 不会打包 native 库，所以 `.so` 在补 dex 的同一个 `ZipArchive` 更新块里注入，之后仍由 `Write-AlignedZip` 统一补 4 字节对齐。**注入必须在 `Write-AlignedZip` 之前**，否则 `zipalign -c -v 4` 会失败。

Manifest 显式写 `android:extractNativeLibs="true"`：此时 `.so` 可以 deflate 压缩存放（约 12.3 MB 而不是 34.2 MB），4 字节对齐就够。**不要**改成 `false`——那要求 `.so` 不压缩且按 4096 字节页对齐，现有 `Write-AlignedZip` 只做 4 字节，APK 也会涨到 40 MB 以上。

当前只打 `arm64-v8a`。要兼容 32 位需在 `tools/get-onnxruntime.ps1` 的 `$abis` 里加 `armeabi-v7a`（约 +8.8 MB）。模型与字符表放在 `app/src/main/assets/ocr/`，按 `aapt2 link -A` 整体打包；`AssetManager.openFd` 对压缩条目不可用，运行时一律用 `open()` 读流。机制与实测数据见 [CAPTCHA-OCR.md](CAPTCHA-OCR.md)。

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
- **`aapt2` 报 `failed to open directory` 且路径确实存在**：Windows `aapt2` 打不开绝对路径中含非 ASCII 字符的目录（例如克隆路径里有中文）。相对路径或纯 ASCII 路径可以，用目录联接（`mklink /J`）从纯 ASCII 路径进入工程即可。
- **PowerShell 脚本报 `检索不到变量` 或行为诡异**：先确认脚本是纯 ASCII。Windows PowerShell 5.1 会把无 BOM 的 UTF-8 当 ANSI 解码，脚本里出现中文注释就可能把下一行吞掉，这也是本仓库脚本保持纯 ASCII 的原因。
