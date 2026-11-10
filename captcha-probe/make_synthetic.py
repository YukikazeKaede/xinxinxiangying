"""生成可提交的**合成**验证码样本，作为设备端流水线的回归基准。

为什么不直接用学校页面上的图：仓库红线不允许提交学校页面快照（见
CONTRIBUTING.md §6），所以 ``captcha-probe/artifacts/golden/`` 里只放这里
生成的合成图；真实样本只留在本机被忽略的目录里，用来统计准确率。

合成图刻意贴近学校那张图的特征：92×34、浅色底纹、彩色字符、细干扰线、
轻微旋转。字符集用数字 + 大小写字母，长度 4。

    python captcha-probe/make_synthetic.py --count 12 --out captcha-probe/artifacts/golden

同时写出 ``expected.tsv``（文件名 <TAB> 文本），供 probe.py 与仪器测试对照。
"""
import argparse
import random
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT = 92, 34
ALPHABET = "0123456789abcdefghijkmnpqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ"
FONTS = ("arial.ttf", "arialbd.ttf", "times.ttf", "georgia.ttf", "cour.ttf", "verdana.ttf")
BACKGROUNDS = ((232, 243, 228), (240, 233, 245), (233, 240, 247), (247, 243, 227))
INK = ((36, 48, 88), (94, 40, 40), (32, 84, 52), (70, 46, 96), (28, 72, 78))
FONT_DIRECTORY = Path("C:/Windows/Fonts")


def load_font(size):
    for name in FONTS:
        path = FONT_DIRECTORY / name
        if path.is_file():
            return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def draw_sample(text, seed):
    random.seed(seed)
    image = Image.new("RGB", (WIDTH, HEIGHT), random.choice(BACKGROUNDS))
    draw = ImageDraw.Draw(image)
    # 底纹：几条浅色斜线，模拟学校那张图的纹理。
    for _ in range(random.randint(4, 7)):
        x0, y0 = random.randint(-20, WIDTH), random.randint(-10, HEIGHT)
        draw.line(
            (x0, y0, x0 + random.randint(20, 70), y0 + random.randint(-16, 16)),
            fill=tuple(random.randint(205, 235) for _ in range(3)),
            width=1,
        )
    step = WIDTH // (len(text) + 1)
    for index, character in enumerate(text):
        glyph = Image.new("RGBA", (WIDTH, HEIGHT), (0, 0, 0, 0))
        glyph_draw = ImageDraw.Draw(glyph)
        font = load_font(random.randint(24, 29))
        x = step * (index + 1) - 10 + random.randint(-3, 3)
        y = random.randint(-4, 2)
        glyph_draw.text((x, y), character, font=font, fill=random.choice(INK) + (255,))
        glyph = glyph.rotate(
            random.uniform(-7, 7), resample=Image.BICUBIC, center=(x + 8, y + 14)
        )
        image = Image.alpha_composite(image.convert("RGBA"), glyph).convert("RGB")
    draw = ImageDraw.Draw(image)
    # 前景干扰线：压过字符，制造和真实样本同类的遮挡。
    for _ in range(random.randint(1, 3)):
        y0, y1 = random.randint(4, HEIGHT - 4), random.randint(4, HEIGHT - 4)
        draw.line(
            (0, y0, WIDTH, y1),
            fill=tuple(random.randint(120, 190) for _ in range(3)),
            width=1,
        )
    return image


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--count", type=int, default=12)
    parser.add_argument("--out", default=str(Path(__file__).resolve().parent / "artifacts/golden"))
    parser.add_argument("--seed", type=int, default=20261110)
    args = parser.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    for stale in out.glob("synthetic*.png"):
        stale.unlink()

    rows = []
    for index in range(args.count):
        random.seed(args.seed + index)
        text = "".join(random.choice(ALPHABET) for _ in range(4))
        name = f"synthetic{index + 1:02d}.png"
        draw_sample(text, args.seed + index).save(out / name)
        rows.append(f"{name}\t{text}")
    (out / "expected.tsv").write_text("\n".join(rows) + "\n", encoding="utf-8")
    print(f"{len(rows)} sample(s) -> {out}")


if __name__ == "__main__":
    main()
