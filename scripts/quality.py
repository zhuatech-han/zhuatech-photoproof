#!/usr/bin/env python3
# Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
"""真实MySQL HTTP与照片验证，所有客户为独立TEST档案。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。"""
import argparse, hashlib, io, json, secrets, time, zipfile
from pathlib import Path
import requests
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument("--url", default="http://127.0.0.1:8125")
parser.add_argument("--state", default=str(ROOT / "output/quality-state.json"))
parser.add_argument("--report", default=str(ROOT / "output/mysql-quality.json"))
args = parser.parse_args()
URL = args.url.rstrip("/")
results = []


def check(name, value):
    if not value:
        raise AssertionError(name)
    results.append(name)


class HTTP:
    def __init__(self):
        self.session = requests.Session()
        self.csrf = None

    def call(self, path, method="GET", body=None, status=200, files=None, raw=False):
        headers = {}
        if method != "GET":
            if self.csrf is None:
                self.csrf = self.session.get(URL + "/api/auth/csrf", timeout=20).json()
            headers[self.csrf["header"]] = self.csrf["token"]
        r = self.session.request(
            method,
            URL + "/api" + path,
            json=body if files is None else None,
            files=files,
            headers=headers,
            timeout=40,
        )
        check(method + " " + path + " " + str(status), r.status_code == status)
        if raw:
            return r
        return r.json() if r.content else None

    def login(self, user, password):
        data = self.call(
            "/auth/login", "POST", {"username": user, "password": password}
        )
        self.csrf = None
        return data


def fixture(n):
    """创建原创几何测试样片，无客户或外部图片资料。"""
    colors = [
        ("#ddccb6", "#546047", "#a66045"),
        ("#d9ded3", "#606d53", "#c0a575"),
        ("#dbc1b2", "#7e624a", "#e9ded0"),
        ("#ddd9c8", "#566777", "#b87d5a"),
        ("#bfcbbf", "#4e5c41", "#ded0b4"),
        ("#cfccc6", "#756653", "#ddbc96"),
    ]
    bg, vase, accent = colors[n % 6]
    im = Image.new("RGB", (1200, 900), bg)
    d = ImageDraw.Draw(im)
    d.rectangle((0, 600, 1200, 900), fill=accent)
    d.ellipse((250, 700, 940, 850), fill="#aaa08c")
    d.rounded_rectangle((480, 280, 740, 760), radius=110, fill=vase)
    d.ellipse((480, 250, 740, 320), fill="#434638")
    d.line([(610, 280), (550, 100), (510, 50)], fill="#3b4637", width=8)
    d.line([(600, 220), (700, 90), (820, 40)], fill="#3b4637", width=8)
    d.ellipse((480, 55, 560, 145), fill="#7b8562")
    d.ellipse((740, 15, 850, 95), fill="#7b8562")
    d.text((35, 35), "TEST PHOTO / ORIGINAL GEOMETRIC FIXTURE", fill="#434638")
    out = io.BytesIO()
    im.save(out, "JPEG", quality=90)
    return out.getvalue()


def main():
    env = dict(
        line.split("=", 1)
        for line in (ROOT / ".env").read_text().splitlines()
        if "=" in line and not line.startswith("#")
    )
    admin = HTTP()
    profile = admin.login(env["ADMIN_USERNAME"], env["ADMIN_PASSWORD"])
    roles = admin.call("/admin/roles")
    role = {r["name"].split(" / ")[-1]: r["id"] for r in roles}
    suffix = secrets.token_hex(4)
    password = "Aa9" + secrets.token_hex(18)

    def client(code, name, dept=1):
        return admin.call(
            "/clients",
            "POST",
            {
                "code": code + suffix,
                "name": "TEST " + name,
                "contact": "TEST — no real contact",
                "departmentId": dept,
                "enabled": True,
            },
        )

    c = client("C", "陶器产品摄影 / Ceramic product shoot")
    other = client("O", "其他客户 / Another client")

    def account(name, roleName, kind="STAFF", clientId=None, dept=1):
        return admin.call(
            "/admin/users",
            "POST",
            {
                "username": name + suffix,
                "displayName": "TEST " + name,
                "password": password,
                "kind": kind,
                "clientId": clientId,
                "departmentId": dept,
                "roleId": role[roleName],
                "enabled": True,
            },
        )

    ca = account("client", "Client", "CLIENT", c["id"])
    oa = account("other", "Client", "CLIENT", other["id"])
    ea = account("retoucher", "Retoucher")
    cashierA = account("cashdesk", "Cash desk")
    unassignedA = account("unassigned", "Retoucher")
    customer, otherSession, editor, cashier, unassigned = (HTTP() for _ in range(5))
    for session, acc in [
        (customer, ca),
        (otherSession, oa),
        (editor, ea),
        (cashier, cashierA),
        (unassigned, unassignedA),
    ]:
        session.login(acc["username"], password)
    detail = admin.call(
        "/jobs",
        "POST",
        {
            "title": "TEST 陶器产品摄影 · Ceramic collection",
            "clientId": c["id"],
            "photographerId": profile["id"],
            "editorId": ea["id"],
            "shootType": "COMMERCIAL",
            "shootDate": "2026-10-07",
            "minSelect": 2,
            "includedCount": 3,
            "maxSelect": 5,
            "baseAmount": "600.01",
            "extraPrice": "80.02",
        },
    )
    jobId = detail["job"]["id"]

    def action(session, name, body={}):
        nonlocal detail
        detail = session.call(
            f"/jobs/{jobId}/actions/{name}",
            "POST",
            {"version": detail["job"]["version"], **body},
        )
        return detail

    def upload(kind, name, bytes_, photo=None, session=admin):
        nonlocal detail
        params = f'version={detail["job"]["version"]}&kind={kind}' + (
            f"&photoId={photo}" if photo else ""
        )
        detail = session.call(
            f"/jobs/{jobId}/media?" + params,
            "POST",
            files={"file": (name, bytes_, "image/jpeg")},
        )
        return detail["media"][-1]["id"]

    fixtures = ROOT / "output/fixtures"
    fixtures.mkdir(parents=True, exist_ok=True)
    ids = []
    for n in range(6):
        data = fixture(n)
        name = f"TEST-ceramic-{n+1:02}.jpg"
        (fixtures / name).write_bytes(data)
        ids.append(upload("PROOF", name, data))
    check("private storage keys absent", "fileKey" not in json.dumps(detail))
    action(admin, "publish")
    otherSession.call(f"/jobs/{jobId}?mode=portal", status=403)
    unassigned.call(f"/jobs/{jobId}?mode=edit", status=403)
    cashier.call(f"/media/{ids[0]}/preview", status=403)
    customer.call(f"/media/{ids[0]}/original", status=409)
    preview = customer.call(f"/media/{ids[0]}/preview", raw=True)
    check(
        "watermarked preview differs from original",
        hashlib.sha256(preview.content).hexdigest()
        != hashlib.sha256(fixture(0)).hexdigest(),
    )
    check(
        "preview decodes real JPEG",
        Image.open(io.BytesIO(preview.content)).format == "JPEG",
    )
    for photo in ids[:4]:
        detail = customer.call(
            f"/jobs/{jobId}/selection",
            "PUT",
            {
                "version": detail["job"]["version"],
                "photoId": photo,
                "selected": True,
                "note": "TEST 请保留陶器纹理，调整曝光 / Keep texture, adjust exposure",
            },
        )
    check("exact additional selection quote", str(detail["quote"]) == "680.03")
    action(customer, "submit", {"confirmed": True, "amount": "680.03"})
    fids = []
    for n, photo in enumerate(ids[:4]):
        fids.append(
            upload("FINAL", f"TEST-final-{n+1:02}.jpg", fixture(n), photo, editor)
        )
    customer.call(f"/media/{fids[0]}/preview", status=409)
    action(editor, "review")
    detail = customer.call(
        f"/jobs/{jobId}/reviews",
        "POST",
        {
            "version": detail["job"]["version"],
            "mediaId": fids[0],
            "decision": "CHANGES",
            "note": "TEST 第一张再提亮 / Lift exposure on first image",
        },
    )
    newer = upload("FINAL", "TEST-final-01-v2.jpg", fixture(1), ids[0], editor)
    fids[0] = newer
    action(editor, "review")
    for f in fids:
        detail = customer.call(
            f"/jobs/{jobId}/reviews",
            "POST",
            {
                "version": detail["job"]["version"],
                "mediaId": f,
                "decision": "ACCEPT",
                "note": "",
            },
        )
    check("all exact latest finals accepted", detail["job"]["state"] == "READY")
    admin.call(
        f"/jobs/{jobId}/actions/release",
        "POST",
        {"version": detail["job"]["version"]},
        status=409,
    )
    detail = cashier.call(
        f"/jobs/{jobId}/cash",
        "POST",
        {
            "version": detail["job"]["version"],
            "kind": "RECEIPT",
            "amount": "680.03",
            "reference": "TEST verified receipt " + suffix,
            "note": "TEST external receipt, no actual transfer",
        },
    )
    action(admin, "release")
    zipResponse = customer.call(f"/jobs/{jobId}/download", raw=True)
    archive = zipfile.ZipFile(io.BytesIO(zipResponse.content))
    check("ZIP contains four exact current finals", len(archive.namelist()) == 4)
    check(
        "ZIP files safe basenames",
        all("/" not in n and "\\\\" not in n for n in archive.namelist()),
    )
    hashes = {}
    for f in fids:
        response = customer.call(f"/media/{f}/original", raw=True)
        hashes[str(f)] = hashlib.sha256(response.content).hexdigest()
        check(
            "original attachment and no-store " + str(f),
            "attachment" in response.headers.get("Content-Disposition", "")
            and "no-store" in response.headers.get("Cache-Control", ""),
        )
    grants = admin.call(
        f"/jobs/{jobId}/shares", "POST", {"version": detail["job"]["version"]}
    )
    detail = admin.call(f"/jobs/{jobId}")
    guest = HTTP()
    guest.call(
        "/share/exchange", "POST", {"token": grants["token"], "pin": grants["pin"]}
    )
    check("share has only one job", len(guest.call("/jobs?mode=portal")) == 1)
    detail = admin.call(
        f'/jobs/{jobId}/shares/{grants["id"]}?version={detail["job"]["version"]}',
        "DELETE",
    )
    guest.call("/auth/me", status=401)
    # Keep a second genuine project in selecting state for GUI selection verification.
    active = admin.call(
        "/jobs",
        "POST",
        {
            "title": "TEST 新季陶器选片 · Spring ceramics",
            "clientId": c["id"],
            "photographerId": profile["id"],
            "editorId": ea["id"],
            "shootType": "COMMERCIAL",
            "shootDate": "2026-10-08",
            "minSelect": 2,
            "includedCount": 3,
            "maxSelect": 5,
            "baseAmount": "600.01",
            "extraPrice": "80.02",
        },
    )
    activeId = active["job"]["id"]
    for n in range(6):
        active = admin.call(
            f'/jobs/{activeId}/media?version={active["job"]["version"]}&kind=PROOF',
            "POST",
            files={"file": (f"TEST-ceramic-{n+1:02}.jpg", fixture(n), "image/jpeg")},
        )
    active = admin.call(
        f"/jobs/{activeId}/actions/publish",
        "POST",
        {"version": active["job"]["version"]},
    )
    state = {
        "url": URL,
        "jobId": jobId,
        "activeJobId": activeId,
        "adminUsername": env["ADMIN_USERNAME"],
        "clientUsername": ca["username"],
        "editorUsername": ea["username"],
        "cashUsername": cashierA["username"],
        "testPassword": password,
        "finalIds": fids,
        "hashes": hashes,
        "snapshots": {
            "delivery": admin.call(f"/jobs/{jobId}"),
            "selecting": admin.call(f"/jobs/{activeId}"),
        },
        "accounts": admin.call("/admin/users"),
        "roles": admin.call("/admin/roles"),
        "clients": admin.call("/clients"),
        "audit": admin.call("/audit"),
    }
    target = Path(args.state)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(json.dumps(state, ensure_ascii=False, indent=2))
    target.chmod(0o600)
    Path(args.report).write_text(
        json.dumps(
            {
                "checks": len(results),
                "passed": results,
                "businessData": "TEST only",
                "jobId": jobId,
                "activeJobId": activeId,
            },
            ensure_ascii=False,
            indent=2,
        )
    )
    print(
        f"PASS: {len(results)} checks; actual photos, selection, revisions, receipts, delivery and revoked share verified."
    )


if __name__ == "__main__":
    main()
