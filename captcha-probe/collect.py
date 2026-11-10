"""采集真实验证码样本，用于统计识别准确率与标定置信度阈值。

方案 §8 的风险条目要求「上线前累计 ≥50 张真实样本统计，低于 60% 不上」。
这个脚本只做采样，不做识别；识别交给 probe.py。

    python captcha-probe/collect.py --out <本机目录> --count 60

注意：样本是学校页面的真实内容，**只能落在本机被忽略的目录里**
（``.workbuddy/``、``_private/``、``toolchain/`` 都在 .gitignore 中），
不得提交、不得截图进仓库或 PR。仓库里只保留 expected.tsv 这类纯文本标注
和人工构造的合成样本。
"""
import argparse
import http.cookiejar
import random
import sys
import time
import urllib.request
from pathlib import Path

LOGIN = "/authserver/login"
CAPTCHA = "/authserver/captcha.html?ts={ts}"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="https://authserver.sit.edu.cn")
    parser.add_argument("--out", required=True)
    parser.add_argument("--count", type=int, default=60)
    parser.add_argument("--min-bytes", type=int, default=500)
    args = parser.parse_args()

    out = Path(args.out)
    out.mkdir(parents=True, exist_ok=True)
    # 一次会话、共用 cookie：真实页面也是这么取图的，能覆盖到会带干扰的那个分支。
    jar = http.cookiejar.CookieJar()
    opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    opener.addheaders = [("User-Agent", "Mozilla/5.0 (Linux; Android 14)")]
    opener.open(args.base_url + LOGIN, timeout=30).read()

    saved = 0
    for index in range(args.count):
        url = args.base_url + CAPTCHA.format(ts=random.randint(1, 9999))
        try:
            data = opener.open(url, timeout=30).read()
        except Exception as error:  # noqa: BLE001 - 采样失败不该中断整批
            print(f"skip {index}: {error}", file=sys.stderr)
            continue
        if len(data) < args.min_bytes or not data.startswith(b"\xff\xd8"):
            print(f"skip {index}: not a JPEG ({len(data)} B)", file=sys.stderr)
            continue
        (out / f"real{index + 1:03d}.jpg").write_bytes(data)
        saved += 1
        time.sleep(0.2)
    print(f"saved {saved} captcha image(s) to {out}")


if __name__ == "__main__":
    main()
