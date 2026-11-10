"""PC 侧对照实现：复现方案 §1.1 的预处理 / 后处理，给设备端 Java 做逐行参照。

用法：

    python captcha-probe/probe.py captcha-probe/artifacts/golden
    python captcha-probe/probe.py <某目录> --expected <目录>/expected.tsv
    python captcha-probe/probe.py <单张图片>

输出每行：``文件名<TAB>识别文本<TAB>置信度``。给了 --expected 时追加判定列
``OK/DIFF``，并在结尾打印汇总；不完全一致时退出码为 1，方便当闸门用。

预处理（与附录 B 一致，顺序不能换）：

    RGB → 等比缩放到 高 64（宽 = round(原宽 × 64 / 原高)，BILINEAR）
    → 转灰度（ITU-R 601：.299R + .587G + .114B）
    → 像素 / 255.0
    → [1, 1, 64, tw]

后处理：argmax(类维) → 去掉连续重复 → 丢掉 index 0（CTC blank）→ 查 charset。
"""
import argparse
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
# 模型只放一份，就是打进 APK 的那一份；这里直接读它，避免仓库里出现两个 13 MB 副本。
ASSETS = ROOT.parent / "app/src/main/assets/ocr"
ARTIFACTS = ROOT / "artifacts"
DEFAULT_MODEL = ASSETS / "captcha.onnx"
DEFAULT_CHARSET = ASSETS / "charset.json"
INPUT_NAME = "input1"
HEIGHT = 64
ALLOWED = set("0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ")
IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"}


class Solver:
    def __init__(self, model_path=DEFAULT_MODEL, charset_path=DEFAULT_CHARSET):
        import json

        import onnxruntime as ort

        # 输出形状 {1,-1} 与 {seq,1,8210} 不匹配是模型自带的，会刷满日志，
        # 这里只在 really-bad 级别报错，屏蔽掉那条已知告警。
        ort.set_default_logger_severity(3)
        options = ort.SessionOptions()
        options.intra_op_num_threads = 1
        self.session = ort.InferenceSession(
            str(model_path), options, providers=["CPUExecutionProvider"]
        )
        self.input_name = self.session.get_inputs()[0].name or INPUT_NAME
        self.charset = json.loads(Path(charset_path).read_text(encoding="utf-8"))
        # 输出形状是 [seq, 1, 8210]，seq 是动态维（ORT 报成字符串 seqlen），
        # 所以类维只能在第一次推理后核对，不能在载入时核对。
        self.output_dim = None

    def preprocess(self, image):
        image = image.convert("RGB")
        width, height = image.size
        target_width = max(1, int(round(width * (HEIGHT / height))))
        image = image.resize((target_width, HEIGHT), Image.BILINEAR).convert("L")
        array = np.asarray(image, dtype=np.float32) / 255.0
        return array[None, None, :, :], target_width

    def run(self, image):
        tensor, _ = self.preprocess(image)
        output = self.session.run(None, {self.input_name: tensor})[0]
        if self.output_dim is None:
            self.output_dim = int(output.shape[-1])
            if self.output_dim != len(self.charset):
                raise SystemExit(
                    f"charset has {len(self.charset)} entries but the model emits"
                    f" {self.output_dim}"
                )
        return self.decode(output)

    def decode(self, output):
        """output: [seq, 1, classes] → (text, confidence)。"""
        logits = output[:, 0, :]
        indices = logits.argmax(axis=-1)
        # softmax 只需要每步最大那一类的概率，逐行算即可，别把整张 8210 列矩阵展开。
        probabilities = _softmax_max(logits)
        text = []
        scores = []
        previous = None
        for step, index in enumerate(indices):
            index = int(index)
            if index != previous and index != 0:
                text.append(self.charset[index])
                scores.append(float(probabilities[step]))
            previous = index
        confidence = float(np.mean(scores)) if scores else 0.0
        return "".join(text), confidence


def _softmax_max(logits):
    shifted = logits - logits.max(axis=1, keepdims=True)
    exponentials = np.exp(shifted)
    total = exponentials.sum(axis=1)
    return exponentials.max(axis=1) / total


def is_valid(text, length=4):
    return len(text) == length and all(character in ALLOWED for character in text)


def read_expected(path):
    expected = {}
    for line in Path(path).read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        name, text = line.split("\t")[:2]
        expected[name] = text.strip()
    return expected


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("target", help="图片、或装着图片的目录")
    parser.add_argument("--expected", help="expected.tsv")
    parser.add_argument("--model", default=str(DEFAULT_MODEL))
    parser.add_argument("--charset", default=str(DEFAULT_CHARSET))
    parser.add_argument("--out", help="把结果写成 UTF-8 TSV（PowerShell 的重定向会是 UTF-16）")
    args = parser.parse_args()

    target = Path(args.target)
    if not target.exists():
        raise SystemExit(f"not found: {target}")
    if not Path(args.model).is_file():
        raise SystemExit(f"model missing: {args.model} (run prepare_model.py first)")
    if not Path(args.charset).is_file():
        raise SystemExit(f"charset missing: {args.charset} (run prepare_model.py first)")

    solver = Solver(args.model, args.charset)
    paths = (
        [target]
        if target.is_file()
        else sorted(p for p in target.rglob("*") if p.suffix.lower() in IMAGE_SUFFIXES)
    )
    if not paths:
        raise SystemExit(f"no images under {target}")

    expected = read_expected(args.expected) if args.expected else None
    matched = graded = 0
    failed = []
    rows = []
    for path in paths:
        text, confidence = solver.run(Image.open(path))
        row = f"{path.name}\t{text}\t{confidence:.4f}"
        if expected is not None:
            want = expected.get(path.name)
            if want is None:
                row += "\t?"
            else:
                graded += 1
                ok = text.lower() == want.lower()
                matched += int(ok)
                row += "\tOK" if ok else f"\tDIFF({want})"
                if not ok:
                    failed.append(path.name)
        rows.append(row)
        print(row)
    if args.out:
        Path(args.out).write_text("\n".join(rows) + "\n", encoding="utf-8")

    if expected is not None:
        print(f"# {matched}/{graded} matched", file=sys.stderr)
        if graded != len(paths):
            print(
                f"# {len(paths) - graded} image(s) have no expected row",
                file=sys.stderr,
            )
        if failed:
            print("# mismatched: " + ", ".join(failed), file=sys.stderr)
        return 0 if matched == graded == len(paths) else 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
