#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""只恢复到没有容器或数据卷的独立Compose项目，拒绝覆盖现有数据。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse, hashlib, io, json, re, subprocess, tarfile, tempfile, time, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def invoke(args, **kwargs):
    return subprocess.run(args, cwd=ROOT, check=True, **kwargs)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("backup")
    parser.add_argument("--project", required=True)
    parser.add_argument("--env-file", required=True)
    args = parser.parse_args()
    if not re.fullmatch(r"[a-z][a-z0-9-]{2,50}", args.project):
        parser.error("Invalid Compose project")
    prefix = ["docker", "compose", "--env-file", args.env_file, "-p", args.project]
    if invoke(prefix + ["ps", "-aq"], stdout=subprocess.PIPE).stdout.strip():
        raise SystemExit("Refuse to overwrite an existing Compose project")
    for name in ["mysql-data", "media-data"]:
        if (
            subprocess.run(
                ["docker", "volume", "inspect", args.project + "_" + name],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL,
            ).returncode
            == 0
        ):
            raise SystemExit("Refuse to overwrite an existing volume")
    with zipfile.ZipFile(args.backup) as archive:
        if (
            set(archive.namelist()) != {"manifest.json", "database.sql", "media.tar.gz"}
            or len(archive.namelist()) != 3
        ):
            raise SystemExit("Invalid backup members")
        if any(x.file_size > 6 * 1024**3 for x in archive.infolist()):
            raise SystemExit("Backup member too large")
        manifest = json.loads(archive.read("manifest.json"))
        data = {name: archive.read(name) for name in ["database.sql", "media.tar.gz"]}
        if manifest.get("product") != "PhotoProof" or manifest.get("version") != 1:
            raise SystemExit("Incompatible backup")
        for name, content in data.items():
            entry = manifest["files"][name]
            if (
                len(content) != entry["size"]
                or hashlib.sha256(content).hexdigest() != entry["sha256"]
            ):
                raise SystemExit("Backup hash mismatch")
        with tarfile.open(fileobj=io.BytesIO(data["media.tar.gz"]), mode="r:gz") as tar:
            total = 0
            for entry in tar:
                name = entry.name.removeprefix("./")
                parts = Path(name).parts
                if entry.isdir() and name in ["", ".", "original", "preview", "thumb"]:
                    continue
                if (
                    not entry.isfile()
                    or len(parts) != 2
                    or parts[0] not in ["original", "preview", "thumb"]
                    or not re.fullmatch(r"[0-9a-f-]{36}", parts[1])
                ):
                    raise SystemExit("Unsafe media archive")
                total += entry.size
                if total > 12 * 1024**3:
                    raise SystemExit("Media archive too large")
        # Parse and verify both archives before creating any containers or volumes.
        invoke(prefix + ["build"], stdout=subprocess.DEVNULL)
        invoke(prefix + ["up", "-d", "mysql"], stdout=subprocess.DEVNULL)
        ready = False
        for _ in range(60):
            if (
                subprocess.run(
                    prefix
                    + [
                        "exec",
                        "-T",
                        "mysql",
                        "sh",
                        "-c",
                        'MYSQL_PWD="$MYSQL_PASSWORD" mysql -u"$MYSQL_USER" -N -e "SELECT 1" "$MYSQL_DATABASE"',
                    ],
                    cwd=ROOT,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.DEVNULL,
                ).returncode
                == 0
            ):
                ready = True
                break
            time.sleep(2)
        if not ready:
            raise SystemExit(
                "Restore database did not become ready; keep this isolated project for diagnosis"
            )
        invoke(
            prefix
            + [
                "exec",
                "-T",
                "mysql",
                "sh",
                "-c",
                'MYSQL_PWD="$MYSQL_PASSWORD" exec mysql -u"$MYSQL_USER" "$MYSQL_DATABASE"',
            ],
            input=data["database.sql"],
            stdout=subprocess.DEVNULL,
        )
        invoke(
            prefix
            + [
                "run",
                "--rm",
                "--no-deps",
                "-T",
                "--entrypoint",
                "tar",
                "backend",
                "-C",
                "/data/media",
                "-xzf",
                "-",
            ],
            input=data["media.tar.gz"],
            stdout=subprocess.DEVNULL,
        )
        invoke(prefix + ["up", "-d", "backend", "frontend"], stdout=subprocess.DEVNULL)
    print(
        "Restored database and media into the new isolated project. Verify health, records and original file hashes."
    )


if __name__ == "__main__":
    main()
