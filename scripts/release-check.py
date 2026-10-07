#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""发布前品牌、许可、双语链接和真实截图文件检查，不读取私有环境。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib, re
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def need(ok, label):
    if not ok:
        raise SystemExit("FAIL: " + label)


qr = {
    "wechat-zhuatech.png": "a1205aeec110016ca889693892250a11d449489f64d27c714816b73c3fc645e1",
    "wechat-zhuatech2.png": "98df6f15d17f94b88bc8bc115262b264fab0cfb5e6ca9443aaaf4143c5275215",
}
for name, hash_ in qr.items():
    for folder in ["docs/images", "frontend/public/brand"]:
        need(
            hashlib.sha256((ROOT / folder / name).read_bytes()).hexdigest() == hash_,
            "original contact image " + folder + "/" + name,
        )
for filename in ["README.md", "README.en.md"]:
    text = (ROOT / filename).read_text()
    need(
        "[简体中文](README.md) | [English](README.en.md)" in text,
        "language switch " + filename,
    )
    need("https://www.zhuatech.cn/" in text, "website " + filename)
    refs = re.findall(r'!\[[^\]]*\]\(([^)]+)\)|<img[^>]+src="([^"]+)"', text)
    screenshots = []
    for group in refs:
        target = next(x for x in group if x)
        need(not target.startswith(("http", "data:")), "local screenshot " + target)
        file = ROOT / target
        need(file.is_file(), "image exists " + target)
        if target.startswith("docs/screenshots/"):
            need(
                file.read_bytes().startswith(b"\xff\xd8\xff"),
                "actual JPEG screenshot " + target,
            )
            need(file.stat().st_size > 10000, "nonempty screenshot " + target)
            screenshots.append(target)
    need(len(set(screenshots)) >= 6, "at least six running screenshots " + filename)
    # Validate local documentation links while leaving mail/website targets to manual review.
    for target in re.findall(r"(?<!!)\[[^\]]*\]\(([^)]+)\)", text):
        if not target.startswith(("https:", "http:", "mailto:", "#")):
            need((ROOT / target.split("#")[0]).is_file(), "document link " + target)
cn = (ROOT / "README.md").read_text()
en = (ROOT / "README.en.md").read_text()
license_ = (ROOT / "LICENSE").read_text()
need(
    "上海如静知华信息科技有限公司" in cn
    and "Shanghai Rujing Zhihua Information Technology Co., Ltd." in en,
    "company names",
)
need(all(name in cn for name in qr), "both Chinese QRs")
need("wechat-" not in en and "二维码" not in en, "English has no WeChat images")
need(
    all(
        value in en
        for value in [
            "han@zhuatech.cn",
            "jack@zhuatech.cn",
            "https://wa.me/8617521234993",
        ]
    ),
    "English contact channels",
)
need(
    "non-commercial" in en.lower()
    and "非商业" in cn
    and "未经书面授权不得商用" in license_,
    "non-commercial license consistency",
)
for path in [
    *ROOT.glob("backend/src/**/*.java"),
    *ROOT.glob("frontend/src/*.js"),
    *ROOT.glob("frontend/src/*.vue"),
    *ROOT.glob("scripts/*.py"),
]:
    text = path.read_text()
    need(
        "https://www.zhuatech.cn/" in text and "zhuatech2" in text,
        "source attribution " + str(path.relative_to(ROOT)),
    )
for secret in [
    ".env",
    "output/quality-state.json",
    "private-backups/qa-consistent.zip",
]:
    need(
        secret in [".env"]
        or (ROOT / ".gitignore").read_text().find(secret.split("/")[0] + "/") >= 0,
        "private directory ignored " + secret,
    )
print(
    "PASS: bilingual links, current screenshot files, original contact images, attribution and license checks"
)
