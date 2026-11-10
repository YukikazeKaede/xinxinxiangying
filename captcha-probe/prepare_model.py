"""从 ddddocr wheel 里提取 captcha.onnx 与 charset.json，并校验指纹。

方案 PLAN-CAPTCHA-OCR-20261110.md 附录 A 只给了文件名、大小和 sha256，
没有把二进制放进仓库。这个脚本负责按指纹取回同一份制品：

    python captcha-probe/prepare_model.py --wheel <ddddocr wheel 路径>

输出（默认 captcha-probe/artifacts/）：

    captcha.onnx    13,606,051 B  sha256 b8f2ad9c…0bf68
    charset.json        57,249 B  sha256 342d871c…1b24d

charset.json 由 ddddocr 的 CHARSET_OLD 按 ``json.dumps(..., ensure_ascii=False)``
序列化得到；换一种序列化（紧凑、缩进、ASCII 转义）字节数和哈希都会变，
所以这里固定这一种，便于和方案附录 A 对表。
"""
import argparse
import ast
import hashlib
import json
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent
# 产物直接落在 APK 用的那份 assets 里，仓库只保留一个 13 MB 的副本。
ARTIFACTS = ROOT.parent / "app/src/main/assets/ocr"

MODEL_NAME = "common_old.onnx"
MODEL_ENTRY = "ddddocr/" + MODEL_NAME
CHARSET_ENTRY = "ddddocr/charsets.py"

MODEL_SIZE = 13606051
MODEL_SHA256 = "b8f2ad9cbc1f2e3922a6cb9459e30824e7e2467f3fb4fd61420640e34ea0bf68"
CHARSET_SIZE = 57249
CHARSET_SHA256 = "342d871c34fd467420cd39b58a4624b505da53114e77a69b43b48b8a3ee1b24d"
EXPECTED_CLASSES = 8210


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def read_charset(archive):
    source = archive.read(CHARSET_ENTRY).decode("utf-8")
    for node in ast.parse(source).body:
        if isinstance(node, ast.Assign) and getattr(node.targets[0], "id", "") == "CHARSET_OLD":
            charset = ast.literal_eval(node.value)
            break
    else:
        raise SystemExit("CHARSET_OLD not found in " + CHARSET_ENTRY)
    if len(charset) != EXPECTED_CLASSES:
        raise SystemExit(f"charset length {len(charset)} != {EXPECTED_CLASSES}")
    if charset[0] != "":
        raise SystemExit("charset[0] must be the CTC blank")
    return json.dumps(charset, ensure_ascii=False).encode("utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--wheel", required=True, help="ddddocr-<version>-py3-none-any.whl")
    parser.add_argument("--out", default=str(ARTIFACTS))
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()

    wheel = Path(args.wheel)
    if not wheel.is_file():
        raise SystemExit(f"wheel not found: {wheel}")
    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(wheel) as archive:
        model = archive.read(MODEL_ENTRY)
        charset = read_charset(archive)

    for label, blob, size, digest in (
        ("captcha.onnx", model, MODEL_SIZE, MODEL_SHA256),
        ("charset.json", charset, CHARSET_SIZE, CHARSET_SHA256),
    ):
        print(f"{label}: {len(blob)} B  sha256 {sha256(blob)}")
        if len(blob) != size or sha256(blob) != digest:
            raise SystemExit(
                f"{label} does not match the pinned fingerprint "
                f"(expected {size} B / {digest})"
            )
        target = out / label
        if target.is_file() and not args.force and target.read_bytes() == blob:
            print(f"  unchanged: {target}")
            continue
        target.write_bytes(blob)
        print(f"  written: {target}")
    print("OK: artifacts match appendix A of the plan")


if __name__ == "__main__":
    sys.exit(main())
