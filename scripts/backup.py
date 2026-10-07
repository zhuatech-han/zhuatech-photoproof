#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""暂停业务写入，同步备份数据库及照片卷；不输出凭证。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse, hashlib, json, os, subprocess, tempfile, zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def invoke(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--project", required=True)
    parser.add_argument("--output", required=True)
    parser.add_argument("--env-file", default=str(ROOT / ".env"))
    args = parser.parse_args()
    import re

    if not re.fullmatch(r"[a-z][a-z0-9-]{2,50}", args.project):
        parser.error("Invalid Compose project")
    target = Path(args.output).resolve()
    target.parent.mkdir(parents=True, exist_ok=True)
    if target.exists():
        raise SystemExit("Refuse to overwrite an existing backup")
    prefix = ["docker", "compose", "--env-file", args.env_file, "-p", args.project]
    ids = invoke(
        prefix + ["ps", "-q", "backend"], stdout=subprocess.PIPE
    ).stdout.strip()
    if not ids:
        raise SystemExit("The named backend must be running")
    with tempfile.TemporaryDirectory(prefix="photoproof-backup-") as temporary:
        directory = Path(temporary)
        database = directory / "database.sql"
        media = directory / "media.tar.gz"
        invoke(prefix + ["stop", "backend"], stdout=subprocess.DEVNULL)
        try:
            with database.open("wb") as stream:
                invoke(
                    prefix
                    + [
                        "exec",
                        "-T",
                        "mysql",
                        "sh",
                        "-c",
                        'MYSQL_PWD="$MYSQL_PASSWORD" exec mysqldump -u"$MYSQL_USER" --single-transaction --no-tablespaces --set-gtid-purged=OFF "$MYSQL_DATABASE"',
                    ],
                    stdout=stream,
                )
            with media.open("wb") as stream:
                invoke(
                    prefix
                    + [
                        "run",
                        "--rm",
                        "--no-deps",
                        "--entrypoint",
                        "tar",
                        "backend",
                        "-C",
                        "/data/media",
                        "-czf",
                        "-",
                        ".",
                    ],
                    stdout=stream,
                    stderr=subprocess.PIPE,
                )
            manifest = {
                "product": "PhotoProof",
                "version": 1,
                "createdAt": datetime.now(timezone.utc).isoformat(),
                "files": {
                    f.name: {
                        "sha256": hashlib.sha256(f.read_bytes()).hexdigest(),
                        "size": f.stat().st_size,
                    }
                    for f in [database, media]
                },
            }
            fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, "wb") as output:
                with zipfile.ZipFile(output, "w", zipfile.ZIP_DEFLATED) as archive:
                    archive.writestr(
                        "manifest.json",
                        json.dumps(manifest, ensure_ascii=False, indent=2),
                    )
                    for f in [database, media]:
                        archive.write(f, f.name)
        finally:
            invoke(prefix + ["start", "backend"], stdout=subprocess.DEVNULL)
    print("Consistent database and media backup created. Keep it private.")


if __name__ == "__main__":
    main()
