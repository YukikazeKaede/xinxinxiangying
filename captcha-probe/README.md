# captcha-probe

PC 侧对照实现，用来（1）按指纹取回模型制品、（2）复现设备端流水线的输出、
（3）统计真实样本准确率并标定置信度阈值。

设备端对应实现是 `app/src/main/java/cn/xinxinxiangying/app/CaptchaSolver.java`，
两边的预处理/后处理必须逐行一致，改一边就要同步另一边。

## 装依赖

```powershell
python -m pip install onnxruntime pillow numpy
```

## 取回制品

两个制品按方案附录 A 的指纹固定，并且**只放一份**——就是打进 APK 的那一份，
位置在 `app/src/main/assets/ocr/`（仓库里不重复保存 13 MB 副本）：

```powershell
python captcha-probe/prepare_model.py --wheel toolchain/_downloads/ddddocr-1.6.1-py3-none-any.whl
```

| 文件 | 大小 | sha256 |
| --- | --- | --- |
| `app/src/main/assets/ocr/captcha.onnx` | 13,606,051 B | `b8f2ad9c…0bf68` |
| `app/src/main/assets/ocr/charset.json` | 57,249 B | `342d871c…1b24d` |

脚本默认就写到那里（`--out` 可改），已存在且内容一致时不会重写。
`captcha-probe/artifacts/golden/` 只放可提交的合成样本。

ddddocr wheel 从 PyPI 取（`ddddocr==1.6.1`，75,983,593 B，
sha256 `c7c70f4ae2d0335440ae8b272eea48c9f6888ecef46785fe2311f0c97a133935`）。
脚本只从 wheel 里抽 `ddddocr/common_old.onnx` 和 `ddddocr/charsets.py` 的
`CHARSET_OLD`；`charset.json` 固定用 `json.dumps(..., ensure_ascii=False)` 序列化，
换成紧凑/缩进/ASCII 转义都会改变字节数和哈希。

## 跑识别

```powershell
# 单张
python captcha-probe/probe.py path\to\one.png

# 一整个目录，并与 expected.tsv 逐条比对（不一致时退出码 1，可当闸门）
python captcha-probe/probe.py captcha-probe/artifacts/golden --expected captcha-probe/artifacts/golden/expected.tsv
```

输出每行 `文件名<TAB>识别文本<TAB>置信度[<TAB>OK|DIFF(期望)]`，`--out` 可另存 UTF-8 TSV
（PowerShell 的重定向会写成 UTF-16）。

## 合成基准

`artifacts/golden/` 里的 12 张合成图由脚本生成，可以随时重建：

```powershell
python captcha-probe/make_synthetic.py --count 12
```

它们同时是 `tests/android/CaptchaSolverInstrumentation` 的输入（复制在
`tests/android/assets/ocr/golden/`），用来验证设备端与 PC 端输出一致。
合成图可提交；**真实学校验证码不可提交**。

## 统计真实准确率

```powershell
# 采样到本机被忽略的目录，不要放进仓库
python captcha-probe/collect.py --out .workbuddy/real --count 60
python captcha-probe/probe.py .workbuddy/real --out .workbuddy/predictions.tsv
# 拼成带编号的对照图，人工逐张判读
python captcha-probe/montage.py .workbuddy/real --out .workbuddy/sheet1.png --columns 4 --rows 5
```

判读结果写进 `expected.tsv` 后即可用 `--expected` 复算。实测数据记在
[`docs/CAPTCHA-OCR.md`](../docs/CAPTCHA-OCR.md)。

> `collect.py` 会按 `--base-url` 访问学校验证码接口。只在本人设备上为统计准确率使用，
> 不要把采集到的图片、页面快照或抓包提交到任何仓库。
