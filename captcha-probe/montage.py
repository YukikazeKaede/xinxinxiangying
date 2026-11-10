"""把一批样本拼成带编号的对照图，方便人工判读。

    python captcha-probe/montage.py <目录> --out <输出.png> --columns 4 --zoom 3

每张图左上角标编号（对应文件名里的数字），拼图只用于**本机**人工核对，
输出请放在被 .gitignore 覆盖的目录里。
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw

IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".bmp"}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("directory")
    parser.add_argument("--out", required=True)
    parser.add_argument("--columns", type=int, default=4)
    parser.add_argument("--rows", type=int, default=5)
    parser.add_argument("--zoom", type=int, default=3)
    parser.add_argument("--start", type=int, default=0, help="跳过前 N 张")
    args = parser.parse_args()

    paths = sorted(
        p for p in Path(args.directory).rglob("*") if p.suffix.lower() in IMAGE_SUFFIXES
    )
    paths = paths[args.start : args.start + args.columns * args.rows]
    if not paths:
        raise SystemExit("no images")

    zoom = args.zoom
    label = 16
    cell_width, cell_height = 92 * zoom, 34 * zoom + label
    sheet = Image.new("RGB", (args.columns * cell_width, args.rows * cell_height), "white")
    draw = ImageDraw.Draw(sheet)
    for slot, path in enumerate(paths):
        column, row = slot % args.columns, slot // args.columns
        x, y = column * cell_width, row * cell_height
        image = Image.open(path).convert("RGB")
        width, height = image.size
        image = image.resize((width * zoom, height * zoom), Image.NEAREST)
        sheet.paste(image, (x, y + label))
        draw.text((x + 3, y + 3), f"{path.stem[4:] or path.stem}", fill="black")
        draw.rectangle(
            [x, y, x + cell_width - 1, y + cell_height - 1], outline="lightgray"
        )
    sheet.save(args.out)
    print(f"{len(paths)} image(s) -> {args.out}", file=sys.stderr)


if __name__ == "__main__":
    main()
