#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""核对重启/恢复的数据库快照、审计原记录与实际照片散列。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import hashlib, json
from pathlib import Path
import quality as q

state = json.loads(Path(q.args.state).read_text())
env = dict(
    line.split("=", 1)
    for line in (q.ROOT / ".env").read_text().splitlines()
    if "=" in line and not line.startswith("#")
)
admin = q.HTTP()
admin.login(state["adminUsername"], env["ADMIN_PASSWORD"])
client = q.HTTP()
client.login(state["clientUsername"], state["testPassword"])
for key, id_ in [("delivery", state["jobId"]), ("selecting", state["activeJobId"])]:
    q.check(
        "persisted full job snapshot " + key,
        admin.call("/jobs/" + str(id_)) == state["snapshots"][key],
    )
for key, path in [
    ("accounts", "/admin/users"),
    ("roles", "/admin/roles"),
    ("clients", "/clients"),
]:
    q.check("persisted directory " + key, admin.call(path) == state[key])
audit = {row["id"]: row for row in admin.call("/audit")}
for event in state["audit"]:
    q.check("immutable audit " + str(event["id"]), audit.get(event["id"]) == event)
for id_, hash_ in state["hashes"].items():
    q.check(
        "original photo SHA256 " + id_,
        hashlib.sha256(
            client.call("/media/" + id_ + "/original", raw=True).content
        ).hexdigest()
        == hash_,
    )
Path(q.args.report).write_text(
    json.dumps(
        {
            "url": q.URL,
            "checks": len(q.results),
            "passed": q.results,
            "snapshots": 5,
            "originalFiles": len(state["hashes"]),
        },
        ensure_ascii=False,
        indent=2,
    )
)
print("PASS: " + str(len(q.results)) + " persisted record/file checks")
