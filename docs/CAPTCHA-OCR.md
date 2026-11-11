# 本机验证码识别

学校统一认证页有时会要求 4 位验证码（`#captchaResponse` + `#captchaImg`）。本功能在**设备本地**识别这张图并回填，全程不联网、不上传学号密码，也没有服务端 OCR。

**默认开启**，可在学校页面的「更多 → 验证码辅助」里关闭。关闭时不会创建推理会话、不会加载模型，行为与 0.7.6 一致。

## 组件

| 文件 | 作用 |
| --- | --- |
| `app/src/main/assets/ocr/captcha.onnx` | ddddocr `common_old.onnx` 权重，13,606,051 B |
| `app/src/main/assets/ocr/charset.json` | 8,210 项字符表，index 0 是 CTC blank |
| `app/src/main/assets/ocr/selftest/` | 12 张合成样本 + `expected.tsv`，供「本机自检」（约 30 KB） |
| `app/src/main/java/cn/xinxinxiangying/app/CaptchaSolver.java` | 预处理 + 推理 + CTC 解码 + 校验 |
| `app/src/main/assets/school-login.js` | 命中 `challenge` 时把验证码图取成 data URL 放进 `window.__xinxinCaptchaImage` |
| `app/src/main/assets/school-captcha.js` | 第二次注入：填回输入框并点一次学校自己的提交按钮 |
| `SchoolWebActivity.java` | `handleChallenge()` 串起取图、识别、回填、熔断、兜底弹窗 |
| `tools/get-onnxruntime.ps1` | 从 Maven Central 取固定版本 ORT AAR 并校验 SHA-256 |
| `captcha-probe/` | PC 侧对照实现与准确率统计工具（见该目录 README） |
| `tests/android/CaptchaSolverInstrumentation.java` | 设备端流水线回归（方案 V1） |

## 数据流

```text
school-login.js  发现可见且为空的验证码输入框
   → <img> canvas.toDataURL('image/png')
   → window.__xinxinCaptchaImage（返回值和状态机都不变，老调用方零改动）
SchoolWebActivity.handleChallenge()
   → evaluateJavascript 读回 data URL
   → 工作线程 CaptchaSolver.inspect(byte[])
        BitmapFactory → 等比缩放到高 64（BILINEAR）→ ITU-R 601 灰度
        → /255 → NCHW [1,1,64,W] → ORT run → argmax → 去重复 → 丢 blank
   → 校验：长度 == 4、字符属于 [0-9A-Za-z]、置信度 ≥ 0.50
   → school-captcha.js 填入并 submit.click()（绝不 form.submit()）
```

模型宽度是动态的（`[1,1,64,image_width]`），每张图按原图比例现算宽度。写死宽度会让黄金样本之外的图全部走样。

## 实机复盘：首版为什么填不上

0.8.0 的首个构建在真机上完全不自动填写。定位到三个叠加原因，都已修掉：

| # | 现象 | 根因 | 处置 |
| --- | --- | --- | --- |
| 1 | 取不到图，直接转人工 | 学校那张验证码图是 `document.ready` 之后才异步加载的，第一次读 `window.__xinxinCaptchaImage` 必然是空串；而“转人工”会走 `waitForUser`，它把自动流程停掉并不再调度，于是永远没有第二次机会 | JS 侧给图片挂一次性 `load` 回调，加载完再写入窗口变量；原生侧最多等 8 × 250 ms 重读 |
| 2 | 用户根本没有开关可开 | 两个开关默认 `false`，而「更多」按钮在自动同步时是隐藏的（`if(autoSync)more.setVisibility(View.GONE)`），验证码恰恰出现在同步流程里 | 两个开关改为默认开启，保留「更多 → 验证码辅助」作为关闭入口 |
| 3 | 即使填了也不生效 | 学校页把验证码模板放在 `#hidenCaptchaDiv`，再由 `document.ready` 拷进 `#cpatchaDiv`，同一个 id 出现两份；按文档顺序取第一个可能取到隐藏模板 | JS 改用 `querySelectorAll` + 只认第一个**可见**元素（取图和回填都这样挑） |

顺带加固的三处：

- 每次识别结束都用 Toast 报出结果与失败原因（同步流程里状态栏是隐藏的，否则用户什么都看不到）。
- `fallbackChallenge` 在手动浏览路径上把 `manualLoginSubmitted` 复位，下一个页面周期还能再试一次。
- 页面版本变化时不再“静默返回”，也不会把新页面的 challenge 判成失败。

## 本机自检

「更多 → 验证码辅助 → 本机自检」会用随包的 12 张合成样本（`assets/ocr/selftest/`，共 30 KB）在设备上跑完整流水线，Toast 报告 `通过 n/12`，失败时给出第一例的期望值与实际值。

这条设计是为了回答「模型和预处理在这台机器上到底能不能用」——它等价于方案 §7 的 V1，但不需要 adb、不需要真人验证码。若自检通过而自动填写仍失败，问题一定在页面接线（选择器、提交、加载时序），不在模型。

## 熔断规则

- 识别结果不可用（长度≠4、含非法字符、置信度 < 0.50）时**不提交**，直接转人工。
- 单次登录最多自动填 **2** 次；再多就只显示人工入口。
- 取图最多等约 2 秒（8 次 × 250 ms）；等不到就转人工，不会无限等。
- 任何异常（模型载入失败、Bitmap 解码失败、canvas 被跨域污染、缺 `.so` 的 `LinkageError`）都退回人工，不重试猜测。
- 学校登录失败计数在服务端，所以这里宁可不猜也不循环。

## 兜底弹窗

「验证码放大输入」独立于识别开关：把学校验证码放大 3 倍显示在原生对话框里，输入 4 位后回填并提交。不依赖模型，0 体积代价。

## 准确率实测（PC 侧，非设备）

`captcha-probe/` 用同一套预处理跑真实样本，人工逐张判读作为 ground truth：

| 样本 | 数量 | 结果（大小写不敏感） |
| --- | --- | --- |
| 学校真实验证码 | 60 | **58/60 = 96.7%** |
| 合成基准（随测试包发布） | 12 | 12/12 |

- 唯一确定的错误：`real034` 人工读 `KBcF`，模型给 `Kbce`（置信度 0.7700）。
- 一张字形歧义：`real043` 人工读 `QKp3`，模型给 `okp3`（置信度 0.9117）；该校字体里 Q 的尾巴很短，人也可能读成 o。
- 置信度分布：识别正确的样本最低 0.7904，唯一的错误是 0.7700。样本量只有一个错误，**阈值 0.78 恰好能分开，但这不具统计意义**，所以没把它当闸门，`MIN_CONFIDENCE` 保持 0.50 只用来拦“几乎没看清”的输出。
- 速度（PC，单线程）：模型载入 281 ms，单张推理 6.29 ms。设备端未实测。

样本本身是学校页面内容，只存在本机被忽略的目录里（`captcha-probe/collect.py` 默认输出到外部路径），不提交、不截图进仓库。随测试包发布的 12 张是 `make_synthetic.py` 生成的合成图。

## 与方案文档的三处偏差

1. **charset 里本来就有大写字母**。方案 §1.2 说“词表只有数字+小写，大写结构性无法输出”，不成立：`CHARSET_OLD` 含 `A–Z`，合成样本上模型确实输出大写（`jHbC`、`b1V8`）。真实学校样本上模型基本折叠成小写，但校验必须允许大写、比对必须大小写不敏感。
2. **回传方式**。方案 §P1 建议把 `challenge` 的返回值改成对象携带 payload。这里改用 `window.__xinxinCaptchaImage` 回传、返回值仍是 `'challenge'`：既不破坏既有状态机，也天然满足“开关关闭时行为与 0.7.6 完全一致”。
3. **注入顺序**。方案 §6.2 要求 `.so` 在 `Write-AlignedZip` **之前**注入，§8 风险表却写成“之后”。以 §6.2 为准——注入在补 dex 的同一个归档块里，之后统一走 `Write-AlignedZip` 对齐，`zipalign -c -v 4` 才能过。

## 体积

| 项 | 原始 | APK 内（deflate） |
| --- | --- | --- |
| `captcha.onnx` | 13,606,051 B | ≈ 6.77 MB |
| `libonnxruntime.so` (arm64-v8a) | 34,208,680 B | ≈ 12.29 MB |
| `libonnxruntime4j_jni.so` | 122,576 B | ≈ 35 KB |
| `charset.json` | 57,249 B | ≈ 22 KB |

APK 由 1.2 MiB 变为 **20.06 MiB**。0.8.0 仅发布 `arm64-v8a`。后续构建按用户要求分别生成 `arm64-v8a`、`armeabi-v7a`、`x86`、`x86_64` 四个独立 APK，各自只带对应原生库；实际体积和命令见 [BUILD.md](BUILD.md)。

## 构建

`build.ps1` 新增三处，全部是追加：

1. 调用 `tools/get-onnxruntime.ps1` 取固定版本 ORT（校验 SHA-256，缓存到忽略的 `toolchain/onnxruntime/1.31.1/`）；
2. `javac -cp` 与 `d8` 输入加上 ORT 的 `classes.jar`；
3. 补 dex 的同一个 `ZipArchive` 块里注入 `lib/<abi>/*.so`，之后仍由 `Write-AlignedZip` 对齐。

`AndroidManifest.xml` 增加 `android:extractNativeLibs="true"`：此时 `.so` 允许 deflate 压缩存放（12.29 MB 而不是 34.2 MB），4 字节对齐就够。**不要**改成 `false`——那要求不压缩且 4096 页对齐，现有 `Write-AlignedZip` 只做 4 字节。

## 风险与回滚

| 风险 | 处置 |
| --- | --- |
| 学校改版改成区分大小写 | 立即失效。默认开启但可关闭；关闭即回退人工 |
| 命中率不足 | 可关闭；按 `captcha-probe/collect.py` 继续累计真实样本，低于 60% 该关掉默认值 |
| ORT 会话常驻内存（约 60~90 MB RSS） | 懒加载，`onDestroy` 关闭；`CaptchaSolver.close()` |
| assets 被压缩后 `openFd` 不可用 | 一律用 `AssetManager.open()` 流式拷贝到 `filesDir/ocr/` |
| 模型随包分发的许可 | MIT，见 `assets/licenses/NOTICE-CAPTCHA-OCR.txt` |

彻底移除：删 `CaptchaSolver.java`、`assets/ocr/`（含 `selftest/`）、`assets/school-captcha.js`、`tests/android/CaptchaSolverInstrumentation.java`，还原 `school-login.js` 的取图块、`build.ps1` 三处、manifest 的 `extractNativeLibs`。无数据库与协议变更。

## 验证状态

已跑：`build.ps1` 全链路（含 `zipalign -c -v 4`、`apksigner verify`）、APK 内 `lib/arm64-v8a/*.so` 与 `assets/ocr/*`（含 `selftest/`）、`node tests/captcha-login.test.cjs`（13/13）、`node tests/login-sync.test.cjs` 与其他 10 个套件、仪器测试 APK 编译打包、PC 侧 60 张真实样本（96.7%）+ 12 张合成样本（12/12）。

未跑：设备端 `CaptchaSolverInstrumentation` 与真机自动填写（本机没有 arm64 设备或模拟器）。方案 §7 的 V1 仍**未判定**——设备端 `Bitmap.createScaledBitmap` 的插值与 PIL 的 BILINEAR 不保证逐像素一致。

现在的补验路径有两条，任选：

1. **不连电脑**：学校页面「更多 → 验证码辅助 → 本机自检」，Toast 报 `通过 12/12` 即说明本机流水线可用。
2. **连电脑**：`adb shell am instrument -w cn.xinxinxiangying.app.test/.CaptchaSolverInstrumentation`。
