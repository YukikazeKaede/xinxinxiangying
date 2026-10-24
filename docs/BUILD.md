# 构建说明

本项目不使用 Gradle，`build.ps1` 直接调用 JDK 与 Android SDK 命令：

1. `aapt2 compile` 编译 `app/src/main/res`；
2. `aapt2 link` 生成 APK（`resources.arsc` 以 ZIP stored 方式写入并对齐）；
3. `javac` 编译 Java 源码；
4. `d8` 生成 `classes.dex`；
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
