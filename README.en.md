[简体中文](README.md) | [English](README.en.md)


<img src="frontend/public/brand/logo.jpg" width="64" alt="ZhiHua Technology">

# PhotoProof · Client Photo Proofing and Final Delivery System

**ZhiHua Technology (Shanghai Rujing Zhihua Information Technology Co., Ltd.)** · [Official website](https://www.zhuatech.cn/)

Java 21 / Spring Boot / Vue 3 / MySQL 8. **Public source for non-commercial personal learning and exchange only. Written authorization is required for commercial use.**

## Overview and use cases

PhotoProof helps photographers, studios and commercial product photography teams manage client selections, extra-photo quotes, retouch revisions and final delivery. Clients view actual watermarked proofs on a phone or computer, choose within package limits and leave retouch notes. Staff upload independent final versions. Downloads open only after the client approves all latest versions and verified external receipts cover the amount due.

Suitable for portrait, wedding, event and product photography within one operating business. A solo photographer can perform staff tasks; selection and approval still require the client portal. No second staff approver is required, and staff cannot impersonate client confirmation. Multiple studios belong to one business; this is not a multi-company SaaS.

## Implemented capabilities

| Area | Implemented behavior |
| --- | --- |
| Clients and projects | Create/edit/disable clients; atomic CSV import; project, photographer, retoucher, shoot category and date |
| Packages and selection | Minimum/included/maximum picks, base and extra-photo prices; per-photo choices, selected-only filter, notes, explicit quote acknowledgment and frozen rounds |
| Actual media | JPEG/PNG uploads, 10MB and 24MP limits, 100 proofs/project and bounded aggregate storage; private random keys and actual server-rendered watermarked previews/thumbnails |
| Retouch review | Immutable versions for selected proofs; every selection needs a final before review; exact latest-version approval or changes; preserved historical rounds and files |
| Receipts and delivery | Verified external receipts/refunds and error reversals; reference-chain and amount protections; full-payment/approval gating, original finals and ZIP, client close |
| Administration | Real staff/client accounts, fixed client binding, strong passwords, disable/reset; roles, permission-name API, menus, studios, categories and bounded settings |
| Scope and sharing | All studios, own studio, assigned projects or linked client; temporary single-project link, eight-digit PIN, QR and immediate revocation |
| Reports and maintenance | Currency-separated totals, CSV export, project events/audit; consistent database/media backup and isolated restore; Chinese/English UI |

**A payment record does not execute a transfer or prove bank settlement.** No payment gateway, email/SMS, AI retouching, RAW/video or cloud storage integration is implemented.

## Workflow

Draft → Selecting → Retouching → Client review → Ready → Delivered → Closed. Changes return to retouching. Before delivery, staff may reopen selection with a reason while retaining old rounds. Receipts above the base price require an actual recorded refund first. Cancellation or expiry blocks client gallery access while preserving history and payments. Downloaded files cannot be remotely recalled.

## Architecture

Same-origin Nginx serves Vue and proxies the Spring Boot API. Spring Security provides server sessions and CSRF; JPA transactions and Flyway use MySQL 8.4. Original files, watermarked previews and thumbnails live in a private persistent media volume; database rows carry business state and integrity information. Client responses do not contain private disk keys. Every download rechecks identity, project, expiry, exact current versions, approvals and net receipts.

Critical writes use explicit versions and an instance write lock, intended for small studios. Distributed operation and high-concurrency performance are not claimed. See [architecture](docs/architecture.md), [security](docs/security.md) and [API reference](docs/api.md).

## Repository structure

```text
backend/src/main/java/cn/zhuatech/photoproof/  # business, access, storage, HTTP
backend/src/main/resources/db/migration/     # versioned MySQL schema
backend/src/test/                            # real HTTP/JPA/file tests
frontend/src/                               # Vue staff/client UI and tests
frontend/public/brand/                      # original logo and contact images
docs/                                      # architecture, operation, security
scripts/                                   # private initialization, QA, backup/restore
compose.yaml                               # MySQL, private media, backend, frontend
.env.example                               # configuration names; no credentials
```

## Requirements and startup

Recommended: Docker Engine 27+, Compose v2, at least 4GB available memory and enough photo storage. Native builds use Java 21, Maven 3.9, Node 24.19.0 and npm. Auxiliary QA scripts require Python 3.11+. Spring Boot 4.0.7, Vue 3.5.40 and Vite 8.1.5 are pinned in pom.xml/package-lock.json.

```sh
python3 scripts/init-env.py
docker compose -p photoproof up --build -d
docker compose -p photoproof ps
```

Open http://127.0.0.1:8125 . Read ADMIN_USERNAME and ADMIN_PASSWORD from your private local `.env`. Initialization creates independent random strong passwords and refuses to overwrite an existing file. No shared demo password is provided. Add clients, create linked client accounts, create a project, upload proofs and publish. See [operation guide](docs/operation.md).

## Database initialization and persistent data

A new MySQL volume applies V1__photo_schema.sql automatically: 17 application tables plus Flyway history. Bootstrap creates only the administrator, six roles, permissions, menus, one studio, shoot types and four settings. **No clients, projects, photos or payments are seeded.** Screenshot TEST records are created through actual QA and do not appear automatically on normal deployment. Restart does not overwrite account passwords or business records. Do not edit already-applied migrations.

mysql-data stores the database; media-data stores actual photos. Losing either volume can corrupt the business. Use `docker compose -p photoproof stop` for routine stopping. Do not delete volumes to solve startup issues.

## Configuration

| Setting | Meaning |
| --- | --- |
| MYSQL_ROOT_PASSWORD / DATABASE_PASSWORD | Independent database credentials generated locally |
| ADMIN_USERNAME / ADMIN_PASSWORD | Empty-database bootstrap only; later changes through account administration |
| WEB_PORT / BIND_ADDRESS | Defaults 8125 and 127.0.0.1; configure trusted HTTPS and restricted networking before external access |
| COOKIE_SECURE | true behind HTTPS; false only for local HTTP testing |
| DATABASE_URL / DATABASE_USER | Optional external MySQL; use VERIFY_IDENTITY and a trusted CA in production |
| MEDIA_ROOT | Backend setting; Compose mounts /data/media from a private persistent volume |

Admin settings: gallery_days 1–365, link_hours 1–168, max_job_mb 10–4096 (default 512), watermark 1–32 ASCII characters. Changes apply to future publication, shares or uploads; existing previews are not silently replaced. Studios support CNY, USD, EUR, GBP and HKD with two decimals; currency/time zone become immutable once projects exist.

## Tests

```sh
mvn -B -f backend/pom.xml spotless:check test package
cd frontend
npm ci --no-audit --no-fund
npm run format:check
npm run lint
npm test
npm run build
cd ..
python3 scripts/release-check.py
```

The backend has 33 real HTTP/JPA/file tests for workflow, exact money, scope, versions, concurrent selection, administrator recovery, expiry/revocation and file/download controls. Eleven frontend tests cover CSV, current-version selection, CSRF and no retries of unknown writes. Format, ESLint, builds and image-contained tests must pass; Docker Maven builds do not skip tests.

Run the following only in an isolated test instance. It creates TEST clients, accounts and original geometric proof fixtures. **Do not run it against production.** Random temporary credentials remain in ignored output files and must not be uploaded.

```sh
python3 -m venv .venv
.venv/bin/python -m pip install -r scripts/requirements-quality.txt
.venv/bin/python scripts/quality.py --url http://127.0.0.1:8125
.venv/bin/python scripts/verify-persistence.py --url http://127.0.0.1:8125 --report output/restart-quality.json
```

## Deployment, backup and restore

Self-host with your domain, trusted HTTPS reverse proxy, restricted network and capacity monitoring; set COOKIE_SECURE=true. Do not expose MySQL or media directories. Staff and client portals share the deployment address. A phone's 127.0.0.1 refers to the phone, not your workstation: share links must use a client-reachable domain or LAN address.

```sh
python3 scripts/backup.py --project photoproof --output private-backups/photoproof.zip
python3 scripts/restore.py private-backups/photoproof.zip --project photoproof-recovery --env-file .env.recovery
```

Backup temporarily stops backend writes and restarts it, capturing database and media together. Backups contain private material and must be protected. Prepare a private .env.recovery with a different port and independent MySQL credentials. Restore refuses existing containers/volumes and verifies archive hashes and safe media paths. Verify health, projects, roles, audit and original hashes afterward; see [deployment guide](docs/deployment.md).

## Actual running screenshots

All screenshots show actual implemented pages with TEST records and original geometric sample fixtures. They contain no real customer material, passwords, keys or share credentials. Design mockups are not presented as implemented screens.

### Sign in

Staff or client sign-in; no public default password.

![Sign in](docs/screenshots/login.jpg)

### Photography projects

Projects, clients, states, assignments and amounts.

![Photography projects](docs/screenshots/projects.jpg)

### Client selection

Actual watermarked proofs, limits, retouch notes and extra-photo quote.

![Client selection](docs/screenshots/client-selection.jpg)

### Final delivery

Latest-version previews, client approval and change requests.

![Final delivery](docs/screenshots/client-review.jpg)

### Retouching workspace

Selected proofs, client notes and immutable final versions.

![Retouching workspace](docs/screenshots/retouching.jpg)

### Account management

Staff/client identity, role, studio, status and password management.

![Account management](docs/screenshots/accounts.jpg)

### Roles and permissions

Roles with all-studio, own-studio, assigned or client scope.

![Roles and permissions](docs/screenshots/roles.jpg)

### Client management

Client records, edits, disable and atomic CSV import.

![Client management](docs/screenshots/clients.jpg)

### External payments

Verified receipts, refunds and correction reversals.

![External payments](docs/screenshots/payments.jpg)

### Business reports

Amounts due and net receipts grouped by currency.

![Business reports](docs/screenshots/reports.jpg)

### Studio settings

Studios, shoot types, menus and bounded parameters.

![Studio settings](docs/screenshots/settings.jpg)

### Mobile selection

Actual client selection at a 390×844 viewport.

![Mobile selection](docs/screenshots/mobile-selection.jpg)

### Final file download

After full receipts and approval, the browser streams the current final files.

![Final file download](docs/screenshots/client-delivery.jpg)

## Known limitations

- JPEG/PNG only: 10MB/file, 24MP, 10,000 pixels/side, 100 proofs/project. No RAW, HEIC, video, face recognition or AI retouching; use external retouch software.
- Previews are re-encoded; universal EXIF orientation/color-profile handling is not claimed. Original bytes and metadata remain intact; check privacy and orientation before uploading. Watermarks cannot prevent screenshots.
- Payments are external fact records. No online payment, invoice/tax system, certified electronic signature, settlement guarantee or refund execution.
- One operating business, multiple studios. No tenant-isolated SaaS, SSO/MFA, distributed rate limiting, outbound email/SMS or object-storage SDK.
- Directory queries cap at 10,000 records. Storage quotas include history; soft deletion does not free historical bytes. ZIPs cap at 500MB; larger deliveries use individual downloads. No automatic cleanup or recall of downloaded files.
- Isolated tests do not replace your security, capacity, browser/device or business-rule acceptance. Production readiness is not claimed.

## License, feedback and contact

Personal learning, research and non-commercial exchange only. Commercial deployment, client delivery, resale and SaaS operation require written authorization from **Shanghai Rujing Zhihua Information Technology Co., Ltd.** The [LICENSE](LICENSE) controls. This is public source with non-commercial restrictions, not MIT, Apache or an OSI-standard open-source license. Third-party components retain their original licenses; see [third-party notices](docs/third-party.md).

Provide sanitized reproduction steps and environment details in issues; never attach photos, customer records, passwords, sessions or databases. Contributions must preserve attribution and license and include focused changes and verification. Report security issues privately through the website or contacts.

Contact ZhiHua Technology for commercial source licensing, branding, private deployment, client migration, selection/delivery customization, notification/payment/storage adapters and integration. These are consulting services, not claims of completed integrations in this version.

- Website: [https://www.zhuatech.cn/](https://www.zhuatech.cn/)
- Email: [han@zhuatech.cn](mailto:han@zhuatech.cn)
- Email: [jack@zhuatech.cn](mailto:jack@zhuatech.cn)
- WhatsApp: [+86 17521234993](https://wa.me/8617521234993)
