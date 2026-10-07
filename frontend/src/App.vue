<!-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2 -->
<script setup>
import { ref, computed, onMounted, nextTick } from "vue";
import {
  Camera,
  LayoutGrid,
  ImagePlus,
  Check,
  Heart,
  ArrowLeft,
  ArrowRight,
  Plus,
  Search,
  LogOut,
  Settings,
  Users,
  Wallet,
  ChartNoAxesCombined,
  Shield,
  Clock,
  Download,
  X,
  Copy,
  Link,
  Upload,
  Eye,
  Trash2,
} from "@lucide/vue";
import QRCode from "qrcode";
import { api, resetApi, download } from "./api.js";
import { currentFinals, parseCsv, csvCell, money } from "./domain.js";
const lang = ref("zh"),
  me = ref(null),
  page = ref("jobs"),
  rows = ref([]),
  detail = ref(null),
  directory = ref({ clients: [], workers: [], types: [] }),
  adminLists = ref({}),
  busy = ref(false),
  notice = ref(""),
  error = ref(""),
  search = ref(""),
  filter = ref("ALL"),
  pageNo = ref(1),
  dialog = ref(null),
  form = ref({}),
  share = ref(null),
  qr = ref(""),
  grants = ref([]),
  tab = ref("photos"),
  sort = ref("new"),
  login = ref({ username: "", password: "", pin: "" }),
  shareToken = ref(
    location.hash.startsWith("#gallery/") ? location.hash.slice(9) : "",
  ),
  preview = ref(null),
  uploadInput = ref(null),
  batch = ref([]),
  uploadPhoto = ref(null),
  settingTab = ref("departments");
const zh = computed(() => lang.value === "zh");
const t = (a, b) => (zh.value ? a : b);
const has = (code) => me.value?.permissions.includes(code);
const isClient = computed(() => me.value && me.value.kind !== "STAFF");
const j = computed(() => detail.value?.job);
const selected = computed(
  () => detail.value?.selection?.filter((x) => x.selected) || [],
);
const finals = computed(() =>
  currentFinals(detail.value?.media, j.value?.roundNumber),
);
const proofs = computed(
  () => detail.value?.media?.filter((x) => x.kind === "PROOF") || [],
);
const selectedIds = computed(
  () => new Set(selected.value.map((s) => s.photoId)),
);
const finalMap = computed(
  () => new Map(finals.value.map((f) => [f.photoId, f])),
);
const roleLabel = (value) => {
  const names = [
    "管理员 / Administrator",
    "工作室经理 / Studio manager",
    "摄影师 / Photographer",
    "修图人员 / Retoucher",
    "收款登记 / Cash desk",
    "客户 / Client",
    "主工作室 / Main studio",
  ];
  if (!names.includes(value)) return value || "";
  const parts = value.split(" / ");
  return parts[zh.value ? 0 : 1];
};
const scopeLabel = (value) =>
  ({
    ALL: t("全部工作室", "All studios"),
    DEPARTMENT: t("所在工作室", "Own studio"),
    ASSIGNED: t("本人指派项目", "Assigned projects"),
    CLIENT: t("绑定客户项目", "Linked client projects"),
  })[value] || value;
const permissionLabel = (code) =>
  ({
    jobs: t("项目管理", "Projects"),
    clients: t("客户资料", "Clients"),
    upload: t("上传样片", "Proof upload"),
    edit: t("修图交付", "Retouching"),
    release: t("发布与交付", "Release delivery"),
    cash: t("登记收款退款", "Payments"),
    reports: t("经营报告", "Reports"),
    users: t("管理账号", "Accounts"),
    roles: t("管理角色", "Roles"),
    settings: t("工作室设置", "Settings"),
    audit: t("查看记录", "Audit trail"),
    portal: t("客户选片审阅", "Client portal"),
  })[code] || code;
const onlyPicked = ref(false);
const shownProofs = computed(() =>
  proofs.value.filter(
    (m) =>
      (!onlyPicked.value || selectedIds.value.has(m.id)) &&
      (!isClient.value ||
        !["REVIEW", "READY", "DELIVERED", "CLOSED"].includes(j.value?.state) ||
        selectedIds.value.has(m.id)),
  ),
);
const stateNames = {
  DRAFT: ["草稿", "Draft"],
  SELECTING: ["待选片", "Selecting"],
  RETOUCHING: ["修图中", "Retouching"],
  REVIEW: ["客户审阅", "Client review"],
  READY: ["待交付", "Ready"],
  DELIVERED: ["已交付", "Delivered"],
  CLOSED: ["已归档", "Closed"],
  CANCELLED: ["已取消", "Cancelled"],
};
const state = (s) => stateNames[s]?.[zh.value ? 0 : 1] || s;
const cashName = (s) =>
  ({
    RECEIPT: t("收款", "Receipt"),
    REFUND: t("退款", "Refund"),
    REVERSAL: t("冲销", "Reversal"),
  })[s] || s;
const pageNames = {
  jobs: ["摄影项目", "Projects"],
  edit: ["修图工作台", "Retouching"],
  clients: ["客户", "Clients"],
  cash: ["收款与退款", "Payments"],
  reports: ["经营报告", "Reports"],
  portal: ["我的画廊", "My galleries"],
  users: ["登录账号", "Accounts"],
  roles: ["角色与权限", "Roles"],
  settings: ["工作室设置", "Settings"],
  audit: ["操作记录", "Audit log"],
};
const title = computed(
  () => pageNames[page.value]?.[zh.value ? 0 : 1] || page.value,
);
const icons = {
  jobs: LayoutGrid,
  edit: Camera,
  clients: Users,
  cash: Wallet,
  reports: ChartNoAxesCombined,
  portal: Camera,
  users: Users,
  roles: Shield,
  settings: Settings,
  audit: Clock,
};
const currentMode = computed(() =>
  isClient.value
    ? "portal"
    : page.value === "cash" ||
        (page.value === "reports" && !has("jobs") && has("cash"))
      ? "cash"
      : page.value === "edit"
        ? "edit"
        : "jobs",
);
const filtered = computed(() => {
  const query = search.value.toLowerCase();
  let r = rows.value.filter((x) =>
    JSON.stringify(x).toLowerCase().includes(query),
  );
  if (
    ["jobs", "edit", "cash", "portal"].includes(page.value) &&
    filter.value !== "ALL"
  )
    r = r.filter((x) => x.job.state === filter.value);
  if (sort.value === "new") r = [...r].reverse();
  return r;
});
const visibleRows = computed(() =>
  filtered.value.slice((pageNo.value - 1) * 10, pageNo.value * 10),
);
const errorNames = {
  STORAGE_WRITE_FAILED: [
    "存储写入失败，请管理员检查空间与权限",
    "Storage write failed. Check space and permissions.",
  ],
  PREVIEW_FAILED: [
    "水印预览生成失败，请检查服务器字体和存储",
    "Preview creation failed. Check server fonts and storage.",
  ],
  ZIP_LIMIT: [
    "ZIP超过500MB，请逐张下载",
    "ZIP exceeds 500MB. Download individual files.",
  ],
  PHOTO_INVALID: [
    "照片不存在、已移除或不属于此项目",
    "Photo is missing, removed or outside this project.",
  ],
  SOURCE_INVALID: [
    "请核对所引用的有效收款或退款",
    "Check the referenced receipt or refund.",
  ],
  REVERSAL_AMOUNT: [
    "冲销金额须与原记录一致",
    "Reversal amount must match the original record.",
  ],
  OLD_PASSWORD_INVALID: ["当前密码不正确", "Current password is incorrect."],
  FILE_PATH_INVALID: [
    "文件路径校验失败，请联系管理员",
    "File path validation failed. Contact the administrator.",
  ],
  RESOURCE_LIMIT: [
    "数据超过单次查询限制，请联系管理员",
    "Data exceeds the query limit. Contact the administrator.",
  ],
  LOGIN_FAILED: ["账号或密码不正确", "Incorrect username or password."],
  LOGIN_THROTTLED: [
    "尝试过多，请5分钟后重试",
    "Too many attempts. Try again in 5 minutes.",
  ],
  UNAUTHENTICATED: [
    "登录已失效，请重新登录",
    "Session expired. Sign in again.",
  ],
  BAD_CREDENTIALS: ["账号或密码不正确", "Incorrect username or password."],
  INVALID_CREDENTIALS: ["账号或密码不正确", "Incorrect username or password."],
  FORBIDDEN: [
    "当前账号没有此权限",
    "Your account does not have this permission.",
  ],
  CLIENT_ONLY: [
    "请使用客户账号进行确认",
    "Sign in with a client account to confirm.",
  ],
  OUT_OF_SCOPE: [
    "此记录不在你的权限范围内",
    "This record is outside your access scope.",
  ],
  VERSION_CONFLICT: [
    "记录已更新，请刷新后重新操作",
    "This record changed. Refresh before continuing.",
  ],
  RESULT_UNKNOWN: [
    "网络中断，操作结果未知。请刷新核对，勿重复提交",
    "Connection lost. The result is unknown. Refresh and check before submitting again.",
  ],
  NETWORK_ERROR: [
    "连接失败，请检查网络后刷新",
    "Connection failed. Check your network and refresh.",
  ],
  INVALID_INPUT: ["请检查必填字段和格式", "Check required fields and formats."],
  INVALID_AMOUNT: [
    "金额必须非负且最多两位小数",
    "Enter a non-negative amount with up to two decimals.",
  ],
  PASSWORD_WEAK: [
    "密码须12–72字节，包含大小写字母和数字",
    "Password must be 12–72 bytes with upper/lower case letters and numbers.",
  ],
  CONFLICT: [
    "编号或账号重复，或记录已有业务关联",
    "Duplicate identifier or a protected business reference.",
  ],
  LAST_ADMIN: [
    "必须保留一个有效管理员",
    "At least one active administrator must remain.",
  ],
  SELECTION_LIMIT: ["已达到最多选片数量", "You reached the selection limit."],
  SELECTION_COUNT: [
    "选片数量不符合套餐范围",
    "Selection count is outside the package limits.",
  ],
  PAYMENT_REQUIRED: [
    "尚未足额收款，请先核对外部收款",
    "Full payment has not been recorded.",
  ],
  CLIENT_APPROVAL_REQUIRED: [
    "客户尚未认可全部最新修图版本",
    "The client must approve all current final versions.",
  ],
  FINALS_REQUIRED: [
    "每张选中照片都需要修图文件",
    "Upload a final version for every selected photo.",
  ],
  GALLERY_UNAVAILABLE: [
    "画廊已过期、取消或停用，请联系摄影师",
    "Gallery expired, cancelled or disabled. Contact your photographer.",
  ],
  SHARE_INVALID: [
    "分享链接或访问码无效、已过期或撤销",
    "The link or access code is invalid, expired or revoked.",
  ],
  SHARE_EXPIRED: ["分享已过期或撤销", "The share has expired or was revoked."],
  TRY_LATER: [
    "尝试次数过多，请5分钟后重试",
    "Too many attempts. Try again in 5 minutes.",
  ],
  FILE_TOO_LARGE: [
    "单张照片不能超过10MB",
    "Each photo must be 10MB or smaller.",
  ],
  IMAGE_FORMAT: [
    "仅支持真实JPEG/PNG，扩展名须一致",
    "Use a real JPEG/PNG with a matching extension.",
  ],
  IMAGE_DIMENSIONS: [
    "照片最多2400万像素，单边不超过10000像素",
    "Up to 24 megapixels and 10,000 pixels per side.",
  ],
  JOB_STORAGE_LIMIT: [
    "项目存储已达到限制，请联系管理员",
    "Project storage limit reached. Contact the administrator.",
  ],
  FILENAME_DUPLICATE: [
    "此项目中已有同名样片",
    "A proof with this filename already exists.",
  ],
  PHOTO_LIMIT: [
    "每个项目最多100张样片",
    "A project supports up to 100 proofs.",
  ],
  APPROVED_VERSION_LOCKED: [
    "已获客户认可的文件不能替换",
    "An approved final version cannot be replaced.",
  ],
  CURRENT_VERSION_REQUIRED: [
    "请选择当前轮次的最新版本",
    "Choose the latest version in the current round.",
  ],
  DOWNLOAD_LOCKED: [
    "交付尚未开放，或收款状态已变化",
    "Download is locked or the payment status changed.",
  ],
  REFUND_FIRST: [
    "已收款高于套餐基础价，请先实际退款并登记",
    "Refund and record excess payment before reopening selection.",
  ],
  OVERPAYMENT: [
    "不能超过项目应收金额",
    "The receipt cannot exceed the amount due.",
  ],
  REFUND_LIMIT: [
    "退款不能超过该笔收款的剩余金额",
    "Refund exceeds the remaining receipt amount.",
  ],
  REFUND_DEPENDENCY: [
    "此收款有退款引用，请保留资金链",
    "This receipt has refund references and cannot be reversed.",
  ],
  REFERENCE_DUPLICATE: [
    "此项目已登记相同凭证号",
    "This reference is already recorded for the project.",
  ],
  FILE_MISSING: [
    "文件缺失，请联系管理员核对存储",
    "File missing. Ask the administrator to check storage.",
  ],
  FILE_CORRUPT: [
    "原文件校验异常，请联系管理员",
    "File integrity check failed. Contact the administrator.",
  ],
  CSV_INVALID: [
    "CSV格式错误，请核对引号与列数",
    "Invalid CSV quoting or column count.",
  ],
  CSV_HEADER: [
    "表头须为code,name,contact,departmentId",
    "Required header: code,name,contact,departmentId.",
  ],
  IMPORT_LIMIT: ["导入1–300行，文件不超过1MB", "Import 1–300 rows, up to 1MB."],
  DRAFT_REQUIRED: [
    "仅草稿项目可以执行此操作",
    "This operation requires a draft project.",
  ],
  PHOTOS_REQUIRED: [
    "样片数量不足，请先上传",
    "Upload enough proofs before publishing.",
  ],
  PROOFS_FROZEN: ["已发布样片不能修改", "Published proofs are frozen."],
  ROLE_KIND_MISMATCH: [
    "客户角色与员工角色不能混用",
    "Client and staff role types must match the account.",
  ],
  IDENTITY_LOCKED: [
    "已关联的身份和编号不能修改",
    "Linked identities and identifiers are immutable.",
  ],
  REFERENCE_PROTECTED: [
    "记录已关联历史业务，不能更改归属",
    "Historical business references protect this record.",
  ],
  WORKER_PERMISSION_INVALID: [
    "摄影师需上传权限，修图人员需修图权限",
    "Photographer needs upload permission; retoucher needs retouch permission.",
  ],
  CLIENT_SCOPE_INVALID: [
    "客户停用或工作室不匹配",
    "Client is disabled or the studio does not match.",
  ],
  DEPARTMENT_DISABLED: ["工作室已停用", "Studio is disabled."],
};
function message(e) {
  if (e.row)
    return (
      t("第 ", "Row ") +
      e.row +
      t(" 行：", ": ") +
      message({ message: e.message })
    );
  return (
    errorNames[e.message]?.[zh.value ? 0 : 1] ||
    t(
      "操作未完成，请刷新核对后重试。原因：",
      "Operation failed. Refresh and check. Code: ",
    ) + e.message
  );
}
function clearPrivate() {
  rows.value = [];
  detail.value = null;
  directory.value = { clients: [], workers: [], types: [] };
  adminLists.value = {};
  dialog.value = null;
  share.value = null;
  qr.value = "";
  grants.value = [];
  preview.value = null;
  batch.value = [];
  form.value = {};
  login.value.password = "";
  login.value.pin = "";
  resetApi();
}
async function run(fn, success = "") {
  if (busy.value) return;
  busy.value = true;
  error.value = "";
  notice.value = "";
  try {
    await fn();
    if (success) notice.value = success;
  } catch (e) {
    error.value = message(e);
    if (e.status === 401) {
      clearPrivate();
      me.value = null;
    } else if (e.status === 403) {
      detail.value = null;
      preview.value = null;
      rows.value = [];
      dialog.value = null;
    }
  } finally {
    busy.value = false;
  }
}
// Preserve login input through identity reset; never retain it after success/failure.
async function loginSubmit() {
  const credentials = { ...login.value };
  const token = shareToken.value;
  await run(async () => {
    clearPrivate();
    me.value = token
      ? await api("/share/exchange", {
          method: "POST",
          body: { token, pin: credentials.pin },
        })
      : await api("/auth/login", { method: "POST", body: credentials });
    shareToken.value = "";
    history.replaceState(null, "", location.pathname);
    resetApi();
    page.value = isClient.value ? "portal" : me.value.menus[0]?.code || "jobs";
    await load();
  });
}
async function logout() {
  await run(async () => {
    await api("/auth/logout", { method: "POST" });
    clearPrivate();
    me.value = null;
    notice.value = "";
  });
}
async function load() {
  pageNo.value = 1;
  rows.value = [];
  onlyPicked.value = false;
  detail.value = null;
  preview.value = null;
  share.value = null;
  grants.value = [];
  if (["jobs", "edit", "cash", "portal"].includes(page.value))
    rows.value = await api("/jobs?mode=" + currentMode.value);
  else if (page.value === "reports") {
    const report = await api("/reports");
    adminLists.value.totals = report.totals;
    rows.value = report.jobs;
  } else if (page.value === "settings")
    rows.value = await api("/admin/" + settingTab.value);
  else if (page.value === "roles") {
    rows.value = await api("/admin/roles");
    adminLists.value.permissions = await api("/admin/permissions");
  } else if (page.value === "users") {
    adminLists.value = {
      ...adminLists.value,
      ...(await api("/admin-options")),
    };
    rows.value = await api("/admin/users");
  } else
    rows.value = await api(
      page.value === "users" ? "/admin/users" : "/" + page.value,
    );
}
async function navigate(code) {
  if (busy.value) return;
  page.value = code;
  search.value = "";
  filter.value = "ALL";
  await run(load);
}
async function openJob(id) {
  await run(async () => {
    detail.value = await api("/jobs/" + id + "?mode=" + currentMode.value);
    tab.value = currentMode.value === "cash" ? "cash" : "photos";
    if (has("release") && !isClient.value)
      grants.value = await api("/jobs/" + id + "/shares");
  });
}
async function refreshDetail() {
  detail.value = await api(
    "/jobs/" + j.value.id + "?mode=" + currentMode.value,
  );
  if (has("release") && !isClient.value)
    grants.value = await api("/jobs/" + j.value.id + "/shares");
}
function fmt(value, currency = j.value?.currency || "CNY") {
  return money(value, currency, lang.value);
}
function date(value) {
  return value
    ? new Date(value).toLocaleString(zh.value ? "zh-CN" : "en-US", {
        hour12: false,
      })
    : "—";
}
function selection(photoId) {
  return detail.value?.selection?.find((s) => s.photoId === photoId);
}
async function pick(m) {
  await run(async () => {
    detail.value = await api("/jobs/" + j.value.id + "/selection", {
      method: "PUT",
      body: {
        version: j.value.version,
        photoId: m.id,
        selected: !selectedIds.value.has(m.id),
        note: selection(m.id)?.note || "",
      },
    });
  });
}
function ask(action) {
  form.value = {
    note: "",
    days: 30,
    amount: detail.value.quote,
    confirmed: false,
  };
  dialog.value = action;
}
async function doAction() {
  const action = dialog.value;
  await run(
    async () => {
      detail.value = await api("/jobs/" + j.value.id + "/actions/" + action, {
        method: "POST",
        body: { ...form.value, version: j.value.version },
      });
      dialog.value = null;
    },
    t("已保存", "Saved"),
  );
}
function noteFor(m) {
  form.value = {
    photoId: m.id,
    note: selection(m.id)?.note || "",
    selected: selectedIds.value.has(m.id),
  };
  dialog.value = "note";
}
async function saveNote() {
  await run(async () => {
    detail.value = await api("/jobs/" + j.value.id + "/selection", {
      method: "PUT",
      body: { ...form.value, version: j.value.version },
    });
    dialog.value = null;
  });
}
function reviewFor(m, decision) {
  form.value = { mediaId: m.id, decision, note: "" };
  dialog.value = "clientReview";
}
async function saveReview() {
  await run(async () => {
    detail.value = await api("/jobs/" + j.value.id + "/reviews", {
      method: "POST",
      body: { ...form.value, version: j.value.version },
    });
    dialog.value = null;
  });
}
async function jobForm(existing = false) {
  await run(async () => {
    directory.value = await api("/directory");
    form.value = existing
      ? { ...j.value }
      : {
          title: "",
          clientId: "",
          photographerId: "",
          editorId: "",
          shootType: directory.value.types[0]?.code || "",
          shootDate: new Date().toLocaleDateString("en-CA"),
          minSelect: 1,
          includedCount: 5,
          maxSelect: 20,
          baseAmount: "0.00",
          extraPrice: "0.00",
        };
    dialog.value = existing ? "editJob" : "newJob";
  });
}
async function saveJob() {
  await run(
    async () => {
      const id = dialog.value === "editJob" ? j.value.id : null;
      const saved = await api("/jobs" + (id ? "/" + id : ""), {
        method: id ? "PUT" : "POST",
        body: form.value,
      });
      dialog.value = null;
      rows.value = await api("/jobs?mode=" + currentMode.value);
      detail.value = await api(
        "/jobs/" + saved.job.id + "?mode=" + currentMode.value,
      );
    },
    t("项目已保存", "Project saved"),
  );
}
const workers = computed(() =>
  directory.value.workers.filter(
    (w) =>
      w.departmentId ===
      directory.value.clients.find((c) => c.id === Number(form.value.clientId))
        ?.departmentId,
  ),
);
async function triggerUpload(photo = null) {
  uploadPhoto.value = photo;
  await nextTick();
  uploadInput.value?.click();
}
async function uploadFiles(event) {
  const files = [...event.target.files];
  event.target.value = "";
  if (!files.length) return;
  batch.value = files.map((f) => ({ name: f.name, state: "waiting" }));
  await run(
    async () => {
      for (let i = 0; i < files.length; i++) {
        const f = files[i];
        const body = new FormData();
        body.append("file", f);
        const params = new URLSearchParams({
          version: String(j.value.version),
          kind: uploadPhoto.value ? "FINAL" : "PROOF",
        });
        if (uploadPhoto.value) params.set("photoId", uploadPhoto.value.id);
        try {
          detail.value = await api("/jobs/" + j.value.id + "/media?" + params, {
            method: "POST",
            body,
          });
          batch.value[i].state = "done";
        } catch (e) {
          batch.value[i].state = message(e);
          for (let n = i + 1; n < files.length; n++)
            batch.value[n].state = t("未上传", "Not uploaded");
          throw e;
        }
      }
    },
    t("上传完成", "Upload complete"),
  );
}
async function removeProof(m) {
  form.value = { mediaId: m.id };
  dialog.value = "removeProof";
}
async function confirmRemove() {
  await run(async () => {
    detail.value = await api(
      "/jobs/" +
        j.value.id +
        "/media/" +
        form.value.mediaId +
        "?version=" +
        j.value.version,
      { method: "DELETE" },
    );
    dialog.value = null;
  });
}
async function createShare() {
  await run(async () => {
    share.value = await api("/jobs/" + j.value.id + "/shares", {
      method: "POST",
      body: { version: j.value.version },
    });
    share.value.url = location.origin + "/#gallery/" + share.value.token;
    qr.value = await QRCode.toDataURL(share.value.url, {
      width: 220,
      margin: 2,
    });
    dialog.value = "share";
    await refreshDetail();
  });
}
async function revoke(g) {
  await run(
    async () => {
      detail.value = await api(
        "/jobs/" +
          j.value.id +
          "/shares/" +
          g.id +
          "?version=" +
          j.value.version,
        { method: "DELETE" },
      );
      grants.value = await api("/jobs/" + j.value.id + "/shares");
    },
    t("分享已撤销", "Share revoked"),
  );
}
async function copyShare() {
  await run(
    async () => {
      await navigator.clipboard.writeText(
        share.value.url +
          "\n" +
          t("访问码：", "Access code: ") +
          share.value.pin,
      );
    },
    t("已复制链接和访问码", "Link and access code copied"),
  );
}
function cashForm(kind = "RECEIPT", source = null) {
  form.value = {
    kind,
    sourceId: source?.id || "",
    amount:
      kind === "REVERSAL"
        ? source.amount
        : kind === "RECEIPT"
          ? Math.max(
              0,
              Number(j.value.amountDue) - Number(detail.value.paid),
            ).toFixed(2)
          : "",
    reference: "",
    note: "",
  };
  dialog.value = "cashEntry";
}
async function saveCash() {
  await run(
    async () => {
      detail.value = await api("/jobs/" + j.value.id + "/cash", {
        method: "POST",
        body: { ...form.value, version: j.value.version },
      });
      dialog.value = null;
    },
    t("已登记外部资金事实", "External transaction recorded"),
  );
}
async function adminForm(kind, row = null) {
  await run(async () => {
    if (kind === "users") {
      adminLists.value = {
        ...adminLists.value,
        ...(await api("/admin-options")),
      };
    }
    if (kind === "clients" && has("settings"))
      adminLists.value.departments = await api("/admin/departments");
    const defaults = {
      users: {
        username: "",
        displayName: "",
        kind: "STAFF",
        roleId: "",
        departmentId: me.value.departmentId,
        clientId: "",
        password: "",
        enabled: true,
      },
      clients: {
        code: "",
        name: "",
        contact: "",
        departmentId: me.value.departmentId,
        enabled: true,
      },
      roles: { name: "", scope: "DEPARTMENT", permissions: [] },
      departments: {
        name: "",
        zone: "Asia/Shanghai",
        currency: "CNY",
        enabled: true,
      },
      dictionaries: { code: "", name: "", nameEn: "", enabled: true },
    };
    form.value = row
      ? {
          ...row,
          permissions: row.permissions ? [...row.permissions] : undefined,
          password: "",
        }
      : { ...defaults[kind] };
    dialog.value = "admin:" + kind;
  });
}
async function saveAdmin() {
  const kind = dialog.value.slice(6);
  await run(
    async () => {
      await api(
        (kind === "clients" ? "/clients" : "/admin/" + kind) +
          (form.value.id ? "/" + form.value.id : ""),
        { method: form.value.id ? "PUT" : "POST", body: form.value },
      );
      dialog.value = null;
      me.value = await api("/auth/me");
      await load();
    },
    t("已保存", "Saved"),
  );
}
async function switchSetting(kind) {
  settingTab.value = kind;
  await run(load);
}
async function importClients(event) {
  const file = event.target.files[0];
  event.target.value = "";
  if (!file) return;
  await run(async () => {
    if (file.size > 1000000) throw new Error("IMPORT_LIMIT");
    form.value = { rows: parseCsv(await file.text()) };
    dialog.value = "clientImport";
  });
}
async function confirmImport() {
  await run(
    async () => {
      await api("/clients/import", { method: "POST", body: form.value.rows });
      dialog.value = null;
      await load();
    },
    t("客户已全部导入", "All clients imported"),
  );
}

function exportCsv() {
  let headers, data;
  if (["jobs", "edit", "cash", "portal", "reports"].includes(page.value)) {
    headers = ["id", "title", "client", "state", "currency", "due", "paid"];
    data = filtered.value.map((x) => [
      x.job.id,
      x.job.title,
      x.clientName,
      x.job.state,
      x.job.currency,
      x.job.amountDue,
      x.paid,
    ]);
  } else if (page.value === "clients") {
    headers = ["code", "name", "contact", "departmentId"];
    data = filtered.value.map((x) => [
      x.code,
      x.name,
      x.contact,
      x.departmentId,
    ]);
  } else return;
  const content =
    "\ufeff" +
    [headers, ...data].map((r) => r.map(csvCell).join(",")).join("\r\n");
  const url = URL.createObjectURL(
    new Blob([content], { type: "text/csv;charset=utf-8" }),
  );
  const a = document.createElement("a");
  a.href = url;
  a.download = "photoproof-" + page.value + ".csv";
  a.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
}
async function getFile(m) {
  await run(() => download("/media/" + m.id + "/original", m.filename));
}
async function getZip() {
  await run(() =>
    download(
      "/jobs/" + j.value.id + "/download",
      "photoproof-" + j.value.id + ".zip",
    ),
  );
}
async function passwordSave() {
  await run(
    async () => {
      await api("/auth/password", {
        method: "POST",
        body: {
          oldPassword: form.value.oldPassword,
          newPassword: form.value.newPassword,
        },
      });
      clearPrivate();
      me.value = null;
    },
    t("密码已修改，请重新登录", "Password changed. Sign in again."),
  );
}
onMounted(async () => {
  await run(async () => {
    try {
      me.value = await api("/auth/me");
      page.value = isClient.value
        ? "portal"
        : me.value.menus[0]?.code || "jobs";
      await load();
    } catch (e) {
      if (e.status !== 401) throw e;
    }
  });
});
</script>

<template>
  <div :class="['app', { 'client-app': isClient }]">
    <div v-if="!me" class="login-shell">
      <section class="login-art">
        <div class="wordmark">
          <Camera :size="28" /> PhotoProof<span>BY ZHIHUA</span>
        </div>
        <div class="aperture"><i /><i /><i /></div>
        <div class="art-copy">
          <small>{{ t("从选片到交付", "FROM PROOF TO DELIVERY") }}</small>
          <h1>
            {{ t("让每一次交付，", "Every selection.") }}<br />{{
              t("有清楚的确认。", "Clearly confirmed.")
            }}
          </h1>
          <p>
            {{
              t(
                "客户选片 · 修图审阅 · 文件交付",
                "Client selection · Retouch review · Final delivery",
              )
            }}
          </p>
        </div>
      </section>
      <section class="login-panel">
        <button class="lang-button" @click="lang = zh ? 'en' : 'zh'">
          {{ zh ? "English" : "中文" }}
        </button>
        <div class="login-box">
          <Camera class="login-camera" :size="34" />
          <h2>
            {{
              shareToken
                ? t("打开你的画廊", "Open your gallery")
                : t("登录工作室", "Sign in to your studio")
            }}
          </h2>
          <p>
            {{
              shareToken
                ? t(
                    "输入摄影师提供的访问码",
                    "Enter the access code from your photographer",
                  )
                : t(
                    "使用摄影师、修图人员或客户账号",
                    "Use your staff or client account",
                  )
            }}
          </p>
          <form @submit.prevent="loginSubmit">
            <template v-if="!shareToken"
              ><label
                >{{ t("账号", "Username")
                }}<input
                  v-model="login.username"
                  autocomplete="username"
                  required
                  maxlength="60" /></label
              ><label
                >{{ t("密码", "Password")
                }}<input
                  v-model="login.password"
                  type="password"
                  autocomplete="current-password"
                  required
                  maxlength="128" /></label></template
            ><label v-else
              >{{ t("8位访问码", "8-digit access code")
              }}<input
                v-model="login.pin"
                inputmode="numeric"
                pattern="[0-9]{8}"
                maxlength="8"
                autocomplete="off"
                required
            /></label>
            <p v-if="error" class="error" role="alert">{{ error }}</p>
            <p v-if="notice" class="notice" role="status">{{ notice }}</p>
            <button class="primary full" :disabled="busy">
              {{
                busy
                  ? t("正在连接…", "Connecting…")
                  : shareToken
                    ? t("进入画廊", "Enter gallery")
                    : t("登录", "Sign in")
              }}<ArrowRight :size="17" />
            </button>
          </form>
          <footer>
            <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
              ><img src="/brand/logo.jpg" alt="知华科技" />{{
                t("知华科技", "ZhiHua Technology")
              }}</a
            ><span>{{
              t(
                "源码学习版 · 商用需授权",
                "Non-commercial source · Commercial license required",
              )
            }}</span>
          </footer>
        </div>
      </section>
    </div>
    <template v-else>
      <aside v-if="!isClient" class="sidebar">
        <a
          class="brand"
          href="https://www.zhuatech.cn/"
          target="_blank"
          rel="noopener"
          ><Camera :size="25" /><strong>PhotoProof</strong
          ><small>ZHIHUA TECHNOLOGY</small></a
        >
        <div class="side-caption">{{ t("工作室", "WORKSPACE") }}</div>
        <nav>
          <button
            v-for="m in me.menus"
            :key="m.code"
            :class="{ active: page === m.code }"
            :disabled="busy"
            @click="navigate(m.code)"
          >
            <component :is="icons[m.code] || Settings" :size="18" /><span>{{
              zh ? m.name : m.nameEn
            }}</span>
          </button>
        </nav>
        <div class="side-bottom">
          <a href="https://www.zhuatech.cn/" target="_blank" rel="noopener"
            ><img src="/brand/logo.jpg" alt="知华科技" />{{
              t("联系知华科技", "Contact ZhiHua")
            }}</a
          ><small>{{
            t(
              "商业授权 / 定制 / 部署",
              "Licensing / Customization / Deployment",
            )
          }}</small>
        </div>
      </aside>
      <main>
        <header class="topbar">
          <div class="crumb">
            <Camera v-if="isClient" :size="22" /><strong v-if="isClient"
              >PhotoProof</strong
            ><span v-else>{{ t("工作室 / ", "Workspace / ") }}{{ title }}</span>
          </div>
          <div class="identity">
            <button class="text-button" @click="lang = zh ? 'en' : 'zh'">
              {{ zh ? "EN" : "中文" }}</button
            ><span>{{ me.displayName }}</span
            ><button
              v-if="me.kind !== 'SHARE'"
              :title="t('修改密码', 'Change password')"
              @click="
                form = { oldPassword: '', newPassword: '' };
                dialog = 'password';
              "
            >
              <Shield :size="17" /></button
            ><button
              :title="t('退出登录', 'Sign out')"
              :disabled="busy"
              @click="logout"
            >
              <LogOut :size="18" />
            </button>
          </div>
        </header>
        <div class="content">
          <div
            v-if="error && !dialog"
            class="error global-message"
            role="alert"
          >
            {{ error
            }}<button class="text-button" @click="error = ''">×</button>
          </div>
          <div v-if="notice" class="notice global-message" role="status">
            {{ notice
            }}<button class="text-button" @click="notice = ''">×</button>
          </div>
          <template v-if="!detail"
            ><section class="heading">
              <div>
                <small>{{
                  isClient
                    ? t("客户空间", "CLIENT SPACE")
                    : t("摄影业务", "STUDIO OPERATIONS")
                }}</small>
                <h1>{{ title }}</h1>
              </div>
              <div class="actions">
                <button :disabled="busy" @click="run(load)">
                  <Clock :size="16" />{{ t("刷新", "Refresh") }}</button
                ><button
                  v-if="
                    [
                      'jobs',
                      'edit',
                      'cash',
                      'portal',
                      'reports',
                      'clients',
                    ].includes(page)
                  "
                  @click="exportCsv"
                >
                  <Download :size="16" />{{ t("导出", "Export") }}</button
                ><button
                  v-if="page === 'jobs' && has('jobs')"
                  class="primary"
                  :disabled="busy"
                  @click="jobForm()"
                >
                  <Plus :size="17" />{{ t("新建项目", "New project") }}</button
                ><button
                  v-if="['clients', 'users', 'roles'].includes(page)"
                  class="primary"
                  :disabled="busy"
                  @click="adminForm(page)"
                >
                  <Plus :size="17" />{{ t("新增", "Add") }}
                </button>
              </div>
            </section>
            <section v-if="page === 'reports'" class="report-grid">
              <article
                v-for="(value, currency) in adminLists.totals"
                :key="currency"
                class="report-card"
              >
                <small
                  >{{ currency }} · {{ value.jobs }}
                  {{ t("个项目", "projects") }}</small
                >
                <h2>{{ fmt(value.paid, currency) }}</h2>
                <p>{{ t("净收款", "Net receipts") }}</p>
                <div>
                  {{ t("应收", "Amount due") }}
                  <b>{{ fmt(value.due, currency) }}</b>
                </div>
              </article>
              <p
                v-if="!Object.keys(adminLists.totals || {}).length"
                class="empty"
              >
                {{ t("还没有经营数据", "No business data yet") }}
              </p>
            </section>
            <section v-if="page === 'settings'" class="settings-tabs">
              <button
                v-for="[key, cn, en] in [
                  ['departments', '工作室', 'Studios'],
                  ['dictionaries', '拍摄分类', 'Shoot types'],
                  ['settings', '系统参数', 'Parameters'],
                  ['menus', '菜单', 'Menus'],
                ]"
                :key="key"
                :class="{ active: settingTab === key }"
                @click="switchSetting(key)"
              >
                {{ t(cn, en) }}</button
              ><button
                v-if="['departments', 'dictionaries'].includes(settingTab)"
                class="primary"
                @click="adminForm(settingTab)"
              >
                <Plus :size="16" />{{ t("新增", "Add") }}
              </button>
            </section>
            <section class="list-toolbar">
              <label class="search"
                ><Search :size="17" /><input
                  v-model="search"
                  :placeholder="
                    t('搜索名称、客户或编号', 'Search name, client or ID')
                  "
                  @input="pageNo = 1" /></label
              ><select
                v-if="['jobs', 'edit', 'cash', 'portal'].includes(page)"
                v-model="filter"
                @change="pageNo = 1"
              >
                <option value="ALL">{{ t("全部状态", "All statuses") }}</option>
                <option
                  v-for="s in Object.keys(stateNames)"
                  :key="s"
                  :value="s"
                >
                  {{ state(s) }}
                </option></select
              ><select v-model="sort" @change="pageNo = 1">
                <option value="new">{{ t("最新优先", "Newest first") }}</option>
                <option value="old">
                  {{ t("最早优先", "Oldest first") }}
                </option></select
              ><label v-if="page === 'clients'" class="button file-label"
                ><Upload :size="16" />{{ t("导入CSV", "Import CSV")
                }}<input
                  type="file"
                  accept=".csv,text/csv"
                  :disabled="busy"
                  @change="importClients"
              /></label>
            </section>
            <section
              v-if="
                ['jobs', 'edit', 'cash', 'portal', 'reports'].includes(page)
              "
              :class="isClient ? 'gallery-cards' : 'table-panel'"
            >
              <template v-if="isClient"
                ><button
                  v-for="r in visibleRows"
                  :key="r.job.id"
                  class="gallery-card"
                  @click="openJob(r.job.id)"
                >
                  <div class="gallery-cover">
                    <Camera :size="45" /><span>{{ r.job.shootType }}</span>
                  </div>
                  <div>
                    <span :class="['status', r.job.state]">{{
                      state(r.job.state)
                    }}</span>
                    <h2>{{ r.job.title }}</h2>
                    <p>
                      {{ r.photoCount }} {{ t("张照片", "photos") }} ·
                      {{ r.job.shootDate }}
                    </p>
                    <small v-if="r.expired">{{
                      t(
                        "已过期，请联系摄影师",
                        "Expired. Contact your photographer.",
                      )
                    }}</small>
                  </div>
                  <ArrowRight :size="20" /></button
              ></template>
              <table v-else>
                <thead>
                  <tr>
                    <th>{{ t("项目 / 客户", "Project / Client") }}</th>
                    <th>{{ t("拍摄日期", "Shoot date") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th>{{ t("照片 / 选片", "Proofs / Picks") }}</th>
                    <th>{{ t("应收 / 已收", "Due / Paid") }}</th>
                    <th>{{ t("负责人", "Assigned to") }}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in visibleRows" :key="r.job.id">
                    <td>
                      <button class="table-link" @click="openJob(r.job.id)">
                        {{ r.job.title }}</button
                      ><small>{{ r.clientName }} · #{{ r.job.id }}</small>
                    </td>
                    <td>{{ r.job.shootDate }}</td>
                    <td>
                      <span :class="['status', r.job.state]">{{
                        state(r.job.state)
                      }}</span
                      ><small v-if="r.expired" class="danger">{{
                        t("画廊过期", "Gallery expired")
                      }}</small>
                    </td>
                    <td>{{ r.photoCount }} / {{ r.selected }}</td>
                    <td>
                      {{ fmt(r.job.amountDue, r.job.currency)
                      }}<small>{{ fmt(r.paid, r.job.currency) }}</small>
                    </td>
                    <td>{{ page === "edit" ? r.editor : r.photographer }}</td>
                    <td>
                      <button
                        class="icon-button"
                        :title="t('打开项目', 'Open project')"
                        @click="openJob(r.job.id)"
                      >
                        <ArrowRight :size="18" />
                      </button>
                    </td>
                  </tr>
                </tbody>
              </table>
              <div v-if="!filtered.length" class="empty">
                <Camera :size="38" />
                <h3>{{ t("还没有项目", "No projects yet") }}</h3>
                <p>
                  {{
                    isClient
                      ? t(
                          "摄影师发布项目后会显示在这里",
                          "Your projects appear here when published.",
                        )
                      : t(
                          "先添加客户，再创建项目并上传样片",
                          "Add a client, then create a project and upload proofs.",
                        )
                  }}
                </p>
                <button
                  v-if="page === 'jobs' && has('clients')"
                  @click="navigate('clients')"
                >
                  {{ t("添加客户", "Add a client") }}<ArrowRight :size="15" />
                </button>
              </div>
            </section>
            <section v-else class="table-panel">
              <table>
                <thead>
                  <tr v-if="page === 'clients'">
                    <th>{{ t("客户", "Client") }}</th>
                    <th>{{ t("联系方式", "Contact") }}</th>
                    <th>{{ t("工作室", "Studio") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th />
                  </tr>
                  <tr v-else-if="page === 'users'">
                    <th>{{ t("账号", "Account") }}</th>
                    <th>{{ t("身份 / 角色", "Kind / Role") }}</th>
                    <th>{{ t("工作室", "Studio") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th />
                  </tr>
                  <tr v-else-if="page === 'roles'">
                    <th>{{ t("角色", "Role") }}</th>
                    <th>{{ t("数据范围", "Data scope") }}</th>
                    <th>{{ t("权限", "Permissions") }}</th>
                    <th />
                  </tr>
                  <tr v-else-if="page === 'audit'">
                    <th>{{ t("时间", "Time") }}</th>
                    <th>{{ t("操作人", "Actor") }}</th>
                    <th>{{ t("操作", "Action") }}</th>
                    <th>{{ t("对象 / 工作室", "Object / Studio") }}</th>
                  </tr>
                  <tr v-else>
                    <th>{{ t("名称 / 编码", "Name / Code") }}</th>
                    <th>{{ t("配置", "Configuration") }}</th>
                    <th>{{ t("状态", "Status") }}</th>
                    <th />
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="r in visibleRows" :key="r.id">
                    <template v-if="page === 'clients'"
                      ><td>
                        <b>{{ roleLabel(r.name) }}</b
                        ><small>{{ r.code }}</small>
                      </td>
                      <td>{{ r.contact || "—" }}</td>
                      <td>
                        {{
                          roleLabel(
                            adminLists.departments?.find(
                              (x) => x.id === r.departmentId,
                            )?.name,
                          ) || "#" + r.departmentId
                        }}
                      </td>
                      <td>
                        {{
                          r.enabled
                            ? t("启用", "Active")
                            : t("停用", "Disabled")
                        }}
                      </td>
                      <td>
                        <button @click="adminForm('clients', r)">
                          {{ t("编辑", "Edit") }}
                        </button>
                      </td></template
                    ><template v-else-if="page === 'users'"
                      ><td>
                        <b>{{ r.displayName }}</b
                        ><small>{{ r.username }}</small>
                      </td>
                      <td>
                        {{
                          r.kind === "STAFF"
                            ? t("员工", "Staff")
                            : t("客户", "Client")
                        }}
                        /
                        {{
                          roleLabel(
                            adminLists.roles?.find((x) => x.id === r.roleId)
                              ?.name,
                          ) || r.roleId
                        }}
                      </td>
                      <td>
                        {{
                          roleLabel(
                            adminLists.departments?.find(
                              (x) => x.id === r.departmentId,
                            )?.name,
                          ) || "#" + r.departmentId
                        }}
                      </td>
                      <td>
                        {{
                          r.enabled
                            ? t("启用", "Active")
                            : t("停用", "Disabled")
                        }}
                      </td>
                      <td>
                        <button @click="adminForm('users', r)">
                          {{ t("管理", "Manage") }}
                        </button>
                      </td></template
                    ><template v-else-if="page === 'roles'"
                      ><td>
                        <b>{{ roleLabel(r.name) }}</b>
                      </td>
                      <td>{{ scopeLabel(r.scope) }}</td>
                      <td>
                        <span
                          v-for="c in r.permissions"
                          :key="c"
                          class="permission-tag"
                          >{{ permissionLabel(c) }}</span
                        >
                      </td>
                      <td>
                        <button @click="adminForm('roles', r)">
                          {{ t("编辑", "Edit") }}
                        </button>
                      </td></template
                    ><template v-else-if="page === 'audit'"
                      ><td>{{ date(r.createdAt) }}</td>
                      <td>{{ r.actor }}</td>
                      <td>{{ r.action }}</td>
                      <td>
                        {{ r.objectId }} / #{{ r.departmentId }}
                      </td></template
                    ><template v-else
                      ><td>
                        <b>{{ r.name || r.code }}</b
                        ><small>{{ r.code }}</small>
                      </td>
                      <td>{{ r.value || r.zone || r.nameEn }}</td>
                      <td>
                        {{
                          r.enabled == null
                            ? "—"
                            : r.enabled
                              ? t("启用", "Active")
                              : t("停用", "Disabled")
                        }}
                      </td>
                      <td>
                        <button @click="adminForm(settingTab, r)">
                          {{ t("编辑", "Edit") }}
                        </button>
                      </td></template
                    >
                  </tr>
                </tbody>
              </table>
              <div v-if="!filtered.length" class="empty">
                {{ t("暂无记录", "No records") }}
              </div>
            </section>
            <div class="pagination">
              <span>{{ filtered.length }} {{ t("条记录", "records") }}</span
              ><button :disabled="pageNo <= 1" @click="pageNo--">
                <ArrowLeft :size="16" /></button
              ><span
                >{{ pageNo }} /
                {{ Math.max(1, Math.ceil(filtered.length / 10)) }}</span
              ><button
                :disabled="pageNo * 10 >= filtered.length"
                @click="pageNo++"
              >
                <ArrowRight :size="16" />
              </button>
            </div>
            <p v-if="page === 'clients'" class="muted small">
              {{
                t(
                  "导入表头：code,name,contact,departmentId；最多300行，全批成功后保存。",
                  "Import header: code,name,contact,departmentId. Up to 300 rows; the whole batch is atomic.",
                )
              }}
            </p>
          </template>
          <template v-else>
            <button class="back-button" :disabled="busy" @click="run(load)">
              <ArrowLeft :size="17" />{{ t("返回列表", "Back to list") }}
            </button>
            <section class="job-heading">
              <div>
                <small
                  >{{ detail.clientName }} · {{ j.shootDate }} · #{{
                    j.id
                  }}</small
                >
                <h1>{{ j.title }}</h1>
                <span :class="['status', j.state]">{{ state(j.state) }}</span
                ><span class="muted"
                  >{{ t("第", "Round ") }}{{ j.roundNumber }}{{ t("轮", "") }} ·
                  {{ t("到期", "Expires") }} {{ date(j.galleryUntil) }}</span
                >
              </div>
              <div class="actions">
                <button :disabled="busy" @click="run(refreshDetail)">
                  {{ t("刷新", "Refresh") }}</button
                ><button
                  v-if="!isClient && j.state === 'DRAFT' && has('jobs')"
                  @click="jobForm(true)"
                >
                  {{ t("编辑套餐", "Edit package") }}</button
                ><button
                  v-if="has('release') && j.state === 'DRAFT'"
                  class="primary"
                  :disabled="busy"
                  @click="ask('publish')"
                >
                  {{ t("发布样片", "Publish proofs") }}</button
                ><button
                  v-if="has('edit') && j.state === 'RETOUCHING'"
                  class="primary"
                  :disabled="busy"
                  @click="ask('review')"
                >
                  {{ t("发送客户审阅", "Send for review") }}</button
                ><button
                  v-if="has('release') && j.state === 'READY'"
                  class="primary"
                  :disabled="busy"
                  @click="ask('release')"
                >
                  {{ t("开放交付", "Release delivery") }}</button
                ><button
                  v-if="isClient && detail.downloadable"
                  class="primary"
                  :disabled="busy"
                  @click="getZip"
                >
                  <Download :size="17" />{{ t("下载全部", "Download all") }}
                </button>
              </div>
            </section>
            <section class="job-metrics">
              <div>
                <small>{{ t("已选 / 最多", "Selected / Limit") }}</small
                ><strong
                  >{{ detail.selected }}
                  <span>/ {{ j.maxSelect }}</span></strong
                >
              </div>
              <div>
                <small>{{ t("套餐含片", "Included photos") }}</small
                ><strong>{{ j.includedCount }}</strong>
              </div>
              <div>
                <small>{{ t("加选每张", "Per extra photo") }}</small
                ><strong>{{ fmt(j.extraPrice) }}</strong>
              </div>
              <div>
                <small>{{
                  j.state === "SELECTING"
                    ? t("当前费用", "Current quote")
                    : t("项目应收", "Amount due")
                }}</small
                ><strong>{{
                  fmt(j.state === "SELECTING" ? detail.quote : j.amountDue)
                }}</strong>
              </div>
              <div>
                <small>{{ t("已登记净收款", "Recorded net receipts") }}</small
                ><strong>{{ fmt(detail.paid) }}</strong>
              </div>
            </section>
            <section v-if="!isClient" class="detail-tabs">
              <button
                v-for="[key, cn, en] in [
                  ['photos', '照片与修图', 'Photos & finals'],
                  ['cash', '收款记录', 'Payments'],
                  ['history', '项目记录', 'History'],
                  ['share', '分享与期限', 'Sharing & expiry'],
                ]"
                v-show="key !== 'cash' || has('cash')"
                :key="key"
                :class="{ active: tab === key }"
                @click="tab = key"
              >
                {{ t(cn, en) }}
              </button>
            </section>
            <template v-if="isClient || tab === 'photos'"
              ><div
                v-if="
                  !isClient &&
                  !has('jobs') &&
                  !has('edit') &&
                  !has('upload') &&
                  !has('release')
                "
                class="empty"
              >
                {{
                  t(
                    "收款账号无照片权限，请查看收款记录",
                    "This account has no photo access. Open Payments.",
                  )
                }}
              </div>
              <template v-else
                ><section class="photo-toolbar">
                  <div>
                    <h2>
                      {{
                        isClient &&
                        ["REVIEW", "READY", "DELIVERED", "CLOSED"].includes(
                          j.state,
                        )
                          ? t("修图交付", "Final images")
                          : t("样片", "Proofs")
                      }}
                    </h2>
                    <p v-if="j.state === 'SELECTING'">
                      {{ t("请选择", "Select ") }}{{ j.minSelect }}–{{
                        j.maxSelect
                      }}{{
                        t(
                          "张，提交后锁定选片与费用",
                          " photos. Submission freezes the selection and quote.",
                        )
                      }}
                    </p>
                    <p v-else-if="j.state === 'RETOUCHING'">
                      {{
                        isClient
                          ? t(
                              "选片已提交，等待摄影师完成修图",
                              "Selection submitted. Your photographer is retouching.",
                            )
                          : t(
                              "为每张选中样片上传修图版本，再发送客户审阅",
                              "Upload a final for every selected proof, then send for review.",
                            )
                      }}
                    </p>
                    <p v-else-if="j.state === 'REVIEW' || j.state === 'READY'">
                      {{
                        t(
                          "客户认可全部最新版本后才能交付",
                          "Delivery requires client approval of all current versions.",
                        )
                      }}
                    </p>
                  </div>
                  <button
                    v-if="j.state === 'DRAFT' && has('upload')"
                    class="primary"
                    :disabled="busy"
                    @click="triggerUpload()"
                  >
                    <ImagePlus :size="18" />{{
                      t("上传样片", "Upload proofs")
                    }}</button
                  ><button
                    v-if="isClient && j.state === 'SELECTING'"
                    class="primary"
                    :disabled="busy || selected.length < j.minSelect"
                    @click="ask('submit')"
                  >
                    {{ t("确认选片", "Confirm selection")
                    }}<ArrowRight :size="17" /></button
                  ><button
                    v-if="isClient && j.state === 'DELIVERED'"
                    :disabled="busy"
                    @click="ask('close')"
                  >
                    {{ t("确认收讫并归档", "Confirm receipt & close") }}
                  </button>
                </section>
                <ul v-if="batch.length" class="upload-results">
                  <li v-for="f in batch" :key="f.name">
                    {{ f.name }}
                    <span>{{
                      f.state === "done"
                        ? t("已上传", "Uploaded")
                        : f.state === "waiting"
                          ? t("待上传", "Waiting")
                          : f.state
                    }}</span>
                  </li>
                </ul>
                <input
                  ref="uploadInput"
                  class="hidden"
                  type="file"
                  accept="image/jpeg,image/png"
                  :multiple="!uploadPhoto"
                  @change="uploadFiles"
                />
                <div v-if="!proofs.length" class="empty upload-empty">
                  <ImagePlus :size="40" />
                  <h3>{{ t("尚未上传样片", "No proofs uploaded") }}</h3>
                  <p>
                    JPEG / PNG ·
                    {{
                      t(
                        "每张最多10MB，最多100张",
                        "Up to 10MB each, 100 proofs per project",
                      )
                    }}
                  </p>
                </div>
                <div v-else class="photo-filters">
                  <button
                    :class="{ active: !onlyPicked }"
                    @click="onlyPicked = false"
                  >
                    {{ t("全部照片", "All photos") }}</button
                  ><button
                    :class="{ active: onlyPicked }"
                    @click="onlyPicked = true"
                  >
                    {{ t("已选照片", "Selected photos") }} ({{
                      selected.length
                    }})
                  </button>
                </div>
                <section v-if="proofs.length" class="photo-grid">
                  <article
                    v-for="m in shownProofs"
                    :key="m.id"
                    :class="['photo-card', { picked: selectedIds.has(m.id) }]"
                  >
                    <div class="photo-image">
                      <img
                        :src="
                          '/api/media/' +
                          ((isClient &&
                          ['REVIEW', 'READY', 'DELIVERED', 'CLOSED'].includes(
                            j.state,
                          )
                            ? finalMap.get(m.id)?.id
                            : m.id) || m.id) +
                          '/thumb'
                        "
                        :alt="m.filename"
                        loading="lazy"
                        @click="
                          preview =
                            isClient &&
                            finalMap.get(m.id) &&
                            ['REVIEW', 'READY', 'DELIVERED', 'CLOSED'].includes(
                              j.state,
                            )
                              ? finalMap.get(m.id)
                              : m
                        "
                      /><button
                        v-if="isClient && j.state === 'SELECTING'"
                        :class="[
                          'pick-button',
                          { chosen: selectedIds.has(m.id) },
                        ]"
                        :disabled="busy"
                        :aria-label="
                          t('选择照片 ', 'Select photo ') + m.filename
                        "
                        @click="pick(m)"
                      >
                        <Check v-if="selectedIds.has(m.id)" :size="20" /><Heart
                          v-else
                          :size="20"
                        /></button
                      ><span
                        v-else-if="selectedIds.has(m.id)"
                        class="photo-badge"
                        ><Check :size="14" />{{ t("已选", "Selected") }}</span
                      ><button
                        v-if="j.state === 'DRAFT' && has('upload')"
                        class="delete-photo"
                        :disabled="busy"
                        :title="t('移除样片', 'Remove proof')"
                        @click="removeProof(m)"
                      >
                        <Trash2 :size="16" />
                      </button>
                    </div>
                    <div class="photo-info">
                      <b>{{ m.filename }}</b
                      ><small
                        >{{ m.width }} × {{ m.height }} · #{{ m.id }}</small
                      >
                      <p v-if="selection(m.id)?.note" class="retouch-note">
                        {{ selection(m.id).note }}
                      </p>
                      <button
                        v-if="isClient && j.state === 'SELECTING'"
                        class="text-button"
                        @click="noteFor(m)"
                      >
                        {{ t("修图备注", "Retouch note") }}</button
                      ><template v-if="!isClient && selectedIds.has(m.id)"
                        ><div v-if="finalMap.get(m.id)" class="final-status">
                          <span
                            >v{{ finalMap.get(m.id).revisionNumber }} ·
                            {{
                              finalMap.get(m.id).review?.decision === "ACCEPT"
                                ? t("客户已认可", "Approved")
                                : finalMap.get(m.id).review?.decision ===
                                    "CHANGES"
                                  ? t("需要修改", "Changes requested")
                                  : t("待审阅", "Awaiting review")
                            }}</span
                          ><button
                            class="text-button"
                            @click="preview = finalMap.get(m.id)"
                          >
                            <Eye :size="15" />
                          </button>
                          <p v-if="finalMap.get(m.id).review?.note">
                            {{ finalMap.get(m.id).review.note }}
                          </p>
                        </div>
                        <button
                          v-if="
                            j.state === 'RETOUCHING' &&
                            has('edit') &&
                            finalMap.get(m.id)?.review?.decision !== 'ACCEPT'
                          "
                          :disabled="busy"
                          @click="triggerUpload(m)"
                        >
                          <Upload :size="15" />{{
                            t("上传修图", "Upload final")
                          }}</button
                        ><button
                          v-if="has('edit') || has('upload')"
                          class="text-button"
                          :disabled="busy"
                          @click="getFile(m)"
                        >
                          {{ t("取样片原文件", "Download source proof") }}
                        </button></template
                      ><template
                        v-if="
                          isClient &&
                          finalMap.get(m.id) &&
                          ['REVIEW', 'READY', 'DELIVERED', 'CLOSED'].includes(
                            j.state,
                          )
                        "
                        ><small
                          >v{{ finalMap.get(m.id).revisionNumber }} ·
                          {{
                            finalMap.get(m.id).review?.decision === "ACCEPT"
                              ? t("已认可", "Approved")
                              : t("待认可", "Awaiting approval")
                          }}</small
                        >
                        <div
                          v-if="['REVIEW', 'READY'].includes(j.state)"
                          class="photo-actions"
                        >
                          <button
                            :disabled="busy"
                            @click="reviewFor(finalMap.get(m.id), 'CHANGES')"
                          >
                            {{ t("要求修改", "Request changes") }}</button
                          ><button
                            class="primary"
                            :disabled="
                              busy ||
                              finalMap.get(m.id).review?.decision === 'ACCEPT'
                            "
                            @click="reviewFor(finalMap.get(m.id), 'ACCEPT')"
                          >
                            <Check :size="15" />{{ t("认可", "Approve") }}
                          </button>
                        </div>
                        <button
                          v-if="detail.downloadable"
                          :disabled="busy"
                          @click="getFile(finalMap.get(m.id))"
                        >
                          <Download :size="15" />{{ t("下载", "Download") }}
                        </button></template
                      >
                    </div>
                  </article>
                </section>
              </template></template
            >
            <template v-else-if="tab === 'cash'"
              ><section class="photo-toolbar">
                <div>
                  <h2>{{ t("外部收款记录", "External payments") }}</h2>
                  <p>
                    {{
                      t(
                        "仅登记已实际核实的收款、退款或错误登记冲销；系统不实际转账",
                        "Record verified external receipts, refunds or corrections. No money is transferred.",
                      )
                    }}
                  </p>
                </div>
                <button
                  class="primary"
                  :disabled="
                    busy ||
                    j.state === 'CANCELLED' ||
                    Number(detail.paid) >= Number(j.amountDue)
                  "
                  @click="cashForm()"
                >
                  <Plus :size="16" />{{ t("登记收款", "Record receipt") }}
                </button>
              </section>
              <section class="table-panel">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("类型 / 凭证", "Type / Reference") }}</th>
                      <th>{{ t("金额", "Amount") }}</th>
                      <th>{{ t("说明", "Note") }}</th>
                      <th>{{ t("操作人 / 时间", "Actor / Time") }}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="c in detail.cash" :key="c.id">
                      <td>
                        <b>{{ cashName(c.kind) }}</b
                        ><small
                          >{{ c.reference }}
                          <span v-if="c.sourceId"
                            >→ #{{ c.sourceId }}</span
                          ></small
                        >
                      </td>
                      <td>{{ fmt(c.amount) }}</td>
                      <td>{{ c.note }}</td>
                      <td>
                        {{ c.actor }}<small>{{ date(c.createdAt) }}</small>
                      </td>
                      <td>
                        <template
                          v-if="
                            c.kind !== 'REVERSAL' &&
                            !detail.cash.some(
                              (x) =>
                                x.kind === 'REVERSAL' && x.sourceId === c.id,
                            )
                          "
                          ><button
                            v-if="c.kind === 'RECEIPT'"
                            @click="cashForm('REFUND', c)"
                          >
                            {{ t("退款", "Refund") }}</button
                          ><button @click="cashForm('REVERSAL', c)">
                            {{ t("冲销误记", "Reverse error") }}
                          </button></template
                        ><span
                          v-else-if="c.kind !== 'REVERSAL'"
                          class="muted"
                          >{{ t("已冲销", "Reversed") }}</span
                        >
                      </td>
                    </tr>
                  </tbody>
                </table>
                <div v-if="!detail.cash.length" class="empty">
                  {{ t("尚未登记资金记录", "No payment records yet") }}
                </div>
              </section></template
            >
            <template v-else-if="tab === 'history'"
              ><section class="history-list">
                <article v-for="e in [...detail.events].reverse()" :key="e.id">
                  <Clock :size="18" />
                  <div>
                    <b>{{ e.action }}</b>
                    <p>{{ e.note || "—" }}</p>
                    <small
                      >{{ e.actor }} · {{ date(e.createdAt) }} ·
                      {{ t("轮次", "Round") }} {{ e.roundNumber }} ·
                      {{ state(e.fromState) }} → {{ state(e.toState) }}</small
                    >
                  </div>
                </article>
              </section>
              <h3>{{ t("历史修图版本", "Final version history") }}</h3>
              <section class="table-panel">
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("文件", "File") }}</th>
                      <th>{{ t("轮次 / 版本", "Round / Version") }}</th>
                      <th>{{ t("客户审阅", "Client review") }}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    <tr
                      v-for="m in detail.media?.filter(
                        (m) => m.kind === 'FINAL',
                      )"
                      :key="m.id"
                    >
                      <td>{{ m.filename }}</td>
                      <td>{{ m.roundNumber }} / v{{ m.revisionNumber }}</td>
                      <td>{{ m.review?.decision || "—" }}</td>
                      <td>
                        <button @click="preview = m">
                          {{ t("预览", "Preview") }}
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </section></template
            >
            <template v-else-if="tab === 'share'"
              ><section class="share-panel">
                <div>
                  <h2>{{ t("客户入口", "Client access") }}</h2>
                  <p>
                    {{
                      t(
                        "客户可以使用绑定账号登录，或使用限时单项目链接与8位访问码",
                        "Clients can sign in with their linked account or use a temporary one-project link with an 8-digit code.",
                      )
                    }}
                  </p>
                  <button
                    v-if="
                      has('release') &&
                      !['DRAFT', 'CANCELLED'].includes(j.state) &&
                      !detail.expired
                    "
                    class="primary"
                    :disabled="busy"
                    @click="createShare"
                  >
                    <Link :size="17" />{{
                      t("生成分享链接", "Create share link")
                    }}
                  </button>
                </div>
                <table>
                  <thead>
                    <tr>
                      <th>{{ t("分享编号", "Share ID") }}</th>
                      <th>{{ t("到期时间", "Expires") }}</th>
                      <th>{{ t("状态", "Status") }}</th>
                      <th />
                    </tr>
                  </thead>
                  <tbody>
                    <tr v-for="g in grants" :key="g.id">
                      <td>#{{ g.id }}</td>
                      <td>{{ date(g.expiresAt) }}</td>
                      <td>
                        {{
                          g.enabled
                            ? t("有效 / 限时", "Enabled / Time limited")
                            : t("已撤销", "Revoked")
                        }}
                      </td>
                      <td>
                        <button
                          v-if="g.enabled && has('release')"
                          :disabled="busy"
                          @click="revoke(g)"
                        >
                          {{ t("撤销", "Revoke") }}
                        </button>
                      </td>
                    </tr>
                  </tbody>
                </table>
              </section>
              <div v-if="has('release')" class="project-controls">
                <button
                  v-if="['RETOUCHING', 'REVIEW', 'READY'].includes(j.state)"
                  :disabled="busy"
                  @click="ask('reopen')"
                >
                  {{ t("重新开放选片", "Reopen selection") }}</button
                ><button
                  v-if="!['DRAFT', 'CLOSED', 'CANCELLED'].includes(j.state)"
                  :disabled="busy"
                  @click="ask('extend')"
                >
                  {{ t("延长画廊期限", "Extend gallery") }}</button
                ><button
                  v-if="!['DELIVERED', 'CLOSED', 'CANCELLED'].includes(j.state)"
                  class="danger-button"
                  :disabled="busy"
                  @click="ask('cancel')"
                >
                  {{ t("取消项目", "Cancel project") }}
                </button>
              </div></template
            >
          </template>
          <footer class="page-footer">
            <span>PhotoProof · {{ t("知华科技", "ZhiHua Technology") }}</span
            ><a
              href="https://www.zhuatech.cn/"
              target="_blank"
              rel="noopener"
              >{{
                t(
                  "商业授权或深度定制开发请联系知华科技",
                  "Contact ZhiHua for commercial licensing or customization",
                )
              }}</a
            >
          </footer>
        </div>
      </main>
    </template>
    <div v-if="preview" class="lightbox" @click.self="preview = null">
      <button
        class="lightbox-close"
        :title="t('关闭预览', 'Close preview')"
        @click="preview = null"
      >
        <X :size="24" /></button
      ><img
        :src="'/api/media/' + preview.id + '/preview'"
        :alt="preview.filename"
      />
      <div>
        {{ preview.filename }} ·
        {{
          preview.kind === "FINAL"
            ? "v" + preview.revisionNumber
            : t("样片", "Proof")
        }}
      </div>
    </div>
    <div v-if="dialog" class="modal-overlay">
      <section
        class="modal"
        role="dialog"
        aria-modal="true"
        :aria-label="dialog"
      >
        <button
          class="modal-close"
          :disabled="busy"
          :title="t('关闭', 'Close')"
          @click="
            dialog = null;
            share = null;
            qr = '';
          "
        >
          <X :size="21" />
        </button>
        <form
          v-if="dialog === 'newJob' || dialog === 'editJob'"
          @submit.prevent="saveJob"
        >
          <h2>{{ t("摄影项目与套餐", "Project & package") }}</h2>
          <div class="form-grid">
            <label class="span2"
              >{{ t("项目名称", "Project title")
              }}<input v-model="form.title" required maxlength="160" /></label
            ><label
              >{{ t("客户", "Client")
              }}<select v-model="form.clientId" required>
                <option value="">{{ t("选择客户", "Select client") }}</option>
                <option
                  v-for="c in directory.clients"
                  :key="c.id"
                  :value="c.id"
                >
                  {{ c.name }} · {{ c.code }}
                </option>
              </select></label
            ><label
              >{{ t("拍摄类型", "Shoot type")
              }}<select v-model="form.shootType" required>
                <option
                  v-for="c in directory.types"
                  :key="c.id"
                  :value="c.code"
                >
                  {{ zh ? c.name : c.nameEn }}
                </option>
              </select></label
            ><label
              >{{ t("拍摄日期", "Shoot date")
              }}<input v-model="form.shootDate" type="date" required /></label
            ><label
              >{{ t("摄影师", "Photographer")
              }}<select v-model="form.photographerId" required>
                <option value="">{{ t("请选择", "Choose") }}</option>
                <option
                  v-for="w in workers.filter((w) =>
                    w.permissions.includes('upload'),
                  )"
                  :key="w.id"
                  :value="w.id"
                >
                  {{ w.name }}
                </option>
              </select></label
            ><label
              >{{ t("修图人员", "Retoucher")
              }}<select v-model="form.editorId" required>
                <option value="">{{ t("请选择", "Choose") }}</option>
                <option
                  v-for="w in workers.filter((w) =>
                    w.permissions.includes('edit'),
                  )"
                  :key="w.id"
                  :value="w.id"
                >
                  {{ w.name }}
                </option>
              </select></label
            ><label
              >{{ t("最少选片", "Minimum picks")
              }}<input
                v-model="form.minSelect"
                type="number"
                min="1"
                max="100"
                required /></label
            ><label
              >{{ t("套餐含片", "Included picks")
              }}<input
                v-model="form.includedCount"
                type="number"
                :min="form.minSelect"
                max="100"
                required /></label
            ><label
              >{{ t("最多选片", "Maximum picks")
              }}<input
                v-model="form.maxSelect"
                type="number"
                :min="form.includedCount"
                max="100"
                required /></label
            ><label
              >{{ t("套餐价格", "Base price")
              }}<input
                v-model="form.baseAmount"
                type="number"
                min="0"
                max="999999999.99"
                step="0.01"
                required /></label
            ><label
              >{{ t("加选每张", "Price per extra photo")
              }}<input
                v-model="form.extraPrice"
                type="number"
                min="0"
                max="999999999.99"
                step="0.01"
                required
            /></label>
          </div>
          <p class="muted small">
            {{
              t(
                "币种取自客户工作室，发布后套餐与样片锁定。",
                "Currency comes from the client studio. Publishing locks the package and proofs.",
              )
            }}
          </p>
          <button class="primary full" :disabled="busy">
            {{ t("保存项目", "Save project") }}
          </button>
        </form>
        <form v-else-if="dialog === 'note'" @submit.prevent="saveNote">
          <h2>{{ t("修图备注", "Retouch note") }}</h2>
          <label
            >{{ t("希望调整的细节", "Requested adjustments")
            }}<textarea v-model="form.note" maxlength="1000" rows="5" /></label
          ><button class="primary full" :disabled="busy">
            {{ t("保存备注", "Save note") }}
          </button>
        </form>
        <form
          v-else-if="dialog === 'clientReview'"
          @submit.prevent="saveReview"
        >
          <h2>
            {{
              form.decision === "ACCEPT"
                ? t("认可此修图版本", "Approve this final version")
                : t("要求修改", "Request changes")
            }}
          </h2>
          <p>
            {{
              t(
                "本次确认只针对当前文件版本。",
                "This confirmation applies to the exact current file version.",
              )
            }}
          </p>
          <label
            >{{ t("说明", "Note")
            }}<textarea
              v-model="form.note"
              :required="form.decision === 'CHANGES'"
              maxlength="1000"
              rows="4"
            /></label
          ><button class="primary full" :disabled="busy">
            {{ t("确认提交", "Confirm") }}
          </button>
        </form>
        <form v-else-if="dialog === 'cashEntry'" @submit.prevent="saveCash">
          <h2>{{ cashName(form.kind) }}</h2>
          <p>
            {{
              t(
                "请先核实实际外部交易。冲销只用于错误登记，不代替实际退款。",
                "Verify the external transaction first. Reversal corrects a wrong record; it does not replace a real refund.",
              )
            }}
          </p>
          <label
            >{{ t("金额", "Amount")
            }}<input
              v-model="form.amount"
              type="number"
              min="0.01"
              max="999999999.99"
              step="0.01"
              :readonly="form.kind === 'REVERSAL'"
              required /></label
          ><label
            >{{ t("外部凭证 / 更正编号", "External reference / Correction ID")
            }}<input v-model="form.reference" maxlength="120" required /></label
          ><label
            >{{ t("说明", "Note")
            }}<textarea
              v-model="form.note"
              maxlength="600"
              required
              rows="3"
            /></label
          ><button class="primary full" :disabled="busy">
            {{ t("登记已核实事实", "Record verified transaction") }}
          </button>
        </form>
        <template v-else-if="dialog === 'share'"
          ><h2>{{ t("分享客户画廊", "Share client gallery") }}</h2>
          <div class="share-qr">
            <img
              :src="qr"
              :alt="t('客户画廊二维码', 'Client gallery QR code')"
            />
          </div>
          <label
            >{{ t("链接", "Link")
            }}<input :value="share?.url" readonly /></label
          ><label
            >{{ t("访问码", "Access code")
            }}<input :value="share?.pin" readonly
          /></label>
          <p class="muted small">
            {{
              t(
                "凭据仅显示一次，请安全传给客户。到期：",
                "Credentials are shown once. Send them securely. Expires: ",
              )
            }}{{ date(share?.expiresAt) }}
          </p>
          <button class="primary full" :disabled="busy" @click="copyShare">
            <Copy :size="17" />{{
              t("复制链接和访问码", "Copy link & access code")
            }}
          </button></template
        >
        <form v-else-if="dialog === 'password'" @submit.prevent="passwordSave">
          <h2>{{ t("修改密码", "Change password") }}</h2>
          <label
            >{{ t("当前密码", "Current password")
            }}<input
              v-model="form.oldPassword"
              type="password"
              autocomplete="current-password"
              required /></label
          ><label
            >{{ t("新密码", "New password")
            }}<input
              v-model="form.newPassword"
              type="password"
              autocomplete="new-password"
              required
              minlength="12"
              maxlength="72"
          /></label>
          <p class="muted small">
            {{
              t(
                "12–72字节，含大小写字母和数字。修改后所有旧会话失效。",
                "12–72 bytes with upper/lower case letters and numbers. Existing sessions become invalid.",
              )
            }}
          </p>
          <button class="primary full" :disabled="busy">
            {{ t("保存并退出", "Save & sign out") }}
          </button>
        </form>
        <form
          v-else-if="dialog === 'clientImport'"
          @submit.prevent="confirmImport"
        >
          <h2>
            {{ t("确认导入客户", "Confirm client import") }} ·
            {{ form.rows.length }}
          </h2>
          <p>
            {{
              t(
                "核对编号、名称与工作室，所有行成功才会保存。",
                "Check codes, names and studios. The whole batch must succeed before anything is saved.",
              )
            }}
          </p>
          <div class="import-preview">
            <table>
              <thead>
                <tr>
                  <th>{{ t("编号", "Code") }}</th>
                  <th>{{ t("名称", "Name") }}</th>
                  <th>{{ t("工作室", "Studio") }}</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(row, index) in form.rows" :key="index">
                  <td>{{ row.code }}</td>
                  <td>{{ row.name }}</td>
                  <td>#{{ row.departmentId }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <button class="primary full" :disabled="busy">
            {{ t("确认导入", "Confirm import") }}
          </button>
        </form>
        <form
          v-else-if="dialog.startsWith('admin:')"
          @submit.prevent="saveAdmin"
        >
          <h2>{{ t("管理记录", "Manage record") }}</h2>
          <div class="form-grid">
            <template v-if="['users', 'clients'].includes(dialog.slice(6))"
              ><label
                >{{
                  dialog === "admin:users"
                    ? t("登录账号", "Username")
                    : t("客户编号", "Client code")
                }}<input
                  v-model="form[dialog === 'admin:users' ? 'username' : 'code']"
                  :readonly="!!form.id"
                  required
                  :maxlength="60" /></label
              ><label
                >{{ t("名称", "Name")
                }}<input
                  v-model="
                    form[dialog === 'admin:users' ? 'displayName' : 'name']
                  "
                  required
                  maxlength="120" /></label
              ><label v-if="dialog === 'admin:clients'" class="span2"
                >{{ t("联系方式", "Contact")
                }}<input v-model="form.contact" maxlength="160" /></label
              ><label
                >{{ t("工作室", "Studio")
                }}<select
                  v-if="adminLists.departments?.length"
                  v-model="form.departmentId"
                  required
                  :disabled="dialog === 'admin:clients' && !!form.id"
                >
                  <option
                    v-for="d in adminLists.departments"
                    :key="d.id"
                    :value="d.id"
                  >
                    {{ d.name }}
                  </option></select
                ><input
                  v-else
                  v-model="form.departmentId"
                  type="number"
                  readonly /></label
              ><template v-if="dialog === 'admin:users'"
                ><label
                  >{{ t("身份", "Account kind")
                  }}<select
                    v-model="form.kind"
                    :disabled="!!form.id"
                    @change="form.roleId = ''"
                  >
                    <option value="STAFF">{{ t("员工", "Staff") }}</option>
                    <option value="CLIENT">{{ t("客户", "Client") }}</option>
                  </select></label
                ><label
                  >{{ t("角色", "Role")
                  }}<select v-model="form.roleId" required>
                    <option value="">{{ t("请选择", "Choose") }}</option>
                    <option
                      v-for="r in adminLists.roles?.filter((r) =>
                        form.kind === 'CLIENT'
                          ? r.scope === 'CLIENT'
                          : r.scope !== 'CLIENT',
                      )"
                      :key="r.id"
                      :value="r.id"
                    >
                      {{ roleLabel(r.name) }}
                    </option>
                  </select></label
                ><label v-if="form.kind === 'CLIENT'"
                  >{{ t("绑定客户", "Linked client")
                  }}<select
                    v-model="form.clientId"
                    :disabled="!!form.id"
                    required
                  >
                    <option value="">{{ t("请选择", "Choose") }}</option>
                    <option
                      v-for="c in adminLists.clients?.filter(
                        (c) => c.departmentId === Number(form.departmentId),
                      )"
                      :key="c.id"
                      :value="c.id"
                    >
                      {{ c.name }}
                    </option>
                  </select></label
                ><label class="span2"
                  >{{
                    form.id
                      ? t("新密码（留空不变）", "New password (blank to keep)")
                      : t("初始密码", "Initial password")
                  }}<input
                    v-model="form.password"
                    type="password"
                    autocomplete="new-password"
                    :required="!form.id"
                    maxlength="72" /></label></template
            ></template>
            <template v-else-if="dialog === 'admin:roles'"
              ><label
                >{{ t("角色名称", "Role name")
                }}<input v-model="form.name" required maxlength="120" /></label
              ><label
                >{{ t("数据范围", "Data scope")
                }}<select
                  v-model="form.scope"
                  @change="
                    form.permissions =
                      form.scope === 'CLIENT'
                        ? ['portal']
                        : form.permissions.filter((c) => c !== 'portal')
                  "
                >
                  <option
                    v-for="s in ['ALL', 'DEPARTMENT', 'ASSIGNED', 'CLIENT']"
                    :key="s"
                    :value="s"
                  >
                    {{ scopeLabel(s) }}
                  </option>
                </select></label
              >
              <fieldset class="span2 permission-box">
                <legend>{{ t("业务权限", "Permissions") }}</legend>
                <label
                  v-for="p in adminLists.permissions?.filter((p) =>
                    form.scope === 'CLIENT'
                      ? p.code === 'portal'
                      : p.code !== 'portal',
                  )"
                  :key="p.id"
                  class="checkbox"
                  ><input
                    v-model="form.permissions"
                    type="checkbox"
                    :value="p.code"
                  />{{ permissionLabel(p.code) }}</label
                >
              </fieldset></template
            >
            <template v-else-if="dialog === 'admin:departments'"
              ><label class="span2"
                >{{ t("工作室名称", "Studio name")
                }}<input v-model="form.name" required maxlength="120" /></label
              ><label
                >{{ t("时区", "Time zone")
                }}<input
                  v-model="form.zone"
                  required
                  maxlength="80"
                  placeholder="Asia/Shanghai" /></label
              ><label
                >{{ t("币种", "Currency")
                }}<select v-model="form.currency">
                  <option
                    v-for="c in ['CNY', 'USD', 'EUR', 'GBP', 'HKD']"
                    :key="c"
                  >
                    {{ c }}
                  </option>
                </select></label
              ></template
            >
            <template v-else-if="dialog === 'admin:settings'"
              ><label class="span2"
                >{{ form.code
                }}<input v-model="form.value" required maxlength="40"
              /></label>
              <p class="span2 muted small">
                {{
                  t(
                    "gallery_days 1–365天；link_hours 1–168小时；max_job_mb 10–4096MB；watermark 1–32个英文字符。",
                    "gallery_days: 1–365; link_hours: 1–168; max_job_mb: 10–4096; watermark: 1–32 ASCII characters.",
                  )
                }}
              </p></template
            >
            <template v-else
              ><label
                >{{ t("编码", "Code")
                }}<input
                  v-model="form.code"
                  :readonly="!!form.id"
                  required
                  maxlength="60" /></label
              ><label
                >{{ t("中文名称", "Chinese name")
                }}<input v-model="form.name" required maxlength="120" /></label
              ><label
                >{{ t("英文名称", "English name")
                }}<input
                  v-model="form.nameEn"
                  required
                  maxlength="120" /></label
              ><label v-if="dialog === 'admin:menus'"
                >{{ t("排序", "Order")
                }}<input
                  v-model="form.position"
                  type="number"
                  min="0"
                  max="100"
                  required /></label
            ></template>
            <label v-if="form.enabled != null" class="checkbox span2"
              ><input v-model="form.enabled" type="checkbox" />{{
                t("启用", "Enabled")
              }}</label
            >
          </div>
          <button class="primary full" :disabled="busy">
            {{ t("保存", "Save") }}
          </button>
        </form>
        <form
          v-else-if="dialog === 'removeProof'"
          @submit.prevent="confirmRemove"
        >
          <h2>{{ t("移除这张样片？", "Remove this proof?") }}</h2>
          <p>
            {{
              t(
                "它将不再出现在本项目，历史文件保留。",
                "It will no longer appear in the project. Historical bytes remain stored.",
              )
            }}
          </p>
          <button class="danger-button full" :disabled="busy">
            {{ t("确认移除", "Confirm removal") }}
          </button>
        </form>
        <form v-else @submit.prevent="doAction">
          <h2>
            {{
              {
                publish: t("发布样片", "Publish proofs"),
                submit: t("确认选片与费用", "Confirm selection & quote"),
                review: t("发送客户审阅", "Send for client review"),
                release: t("开放文件交付", "Release final delivery"),
                close: t("确认收讫并归档", "Confirm receipt & close"),
                reopen: t("重新开放选片", "Reopen selection"),
                cancel: t("取消项目", "Cancel project"),
                extend: t("延长画廊期限", "Extend gallery"),
              }[dialog]
            }}
          </h2>
          <p v-if="dialog === 'publish'">
            {{
              t(
                "发布后锁定套餐与样片。客户可登录或使用分享链接选片。",
                "Publishing locks the package and proofs. Clients can sign in or use a share link to select.",
              )
            }}
          </p>
          <template v-if="dialog === 'submit'"
            ><div class="quote-summary">
              <span
                >{{ selected.length }}
                {{ t("张选片", "selected photos") }}</span
              ><strong>{{ fmt(detail.quote) }}</strong
              ><small
                >{{ t("套餐含", "Includes ") }}{{ j.includedCount
                }}{{ t("张，加选每张", " photos; extra photos ")
                }}{{ fmt(j.extraPrice) }}</small
              >
            </div>
            <label class="checkbox"
              ><input v-model="form.confirmed" type="checkbox" required />{{
                t(
                  "我确认选片及费用，提交后锁定。",
                  "I confirm the selection and quote. Submission freezes both.",
                )
              }}</label
            ></template
          >
          <p v-if="dialog === 'review'">
            {{
              t(
                "客户将审阅当前轮次的最新修图版本。",
                "The client will review the latest finals in this round.",
              )
            }}
          </p>
          <p v-if="dialog === 'release'">
            {{
              t(
                "需要客户认可全部最新版本并足额收款。开放后客户可下载原文件。",
                "All latest finals need client approval and full payment. Clients can then download original final files.",
              )
            }}
          </p>
          <p v-if="dialog === 'close'">
            {{
              t(
                "确认已经收到交付文件，此项目将归档。",
                "Confirm you received the final files. This project will be closed.",
              )
            }}
          </p>
          <template v-if="['reopen', 'extend', 'cancel'].includes(dialog)"
            ><p v-if="dialog === 'reopen'">
              {{
                t(
                  "旧轮次与文件保留。已收款高于套餐基础价时须先登记实际退款。",
                  "Previous rounds and files remain. Record an actual refund first if receipts exceed the base price.",
                )
              }}
            </p>
            <p v-if="dialog === 'cancel'">
              {{
                t(
                  "客户将不能访问此画廊，账目和历史保留。",
                  "The gallery becomes inaccessible to the client. Payments and history remain.",
                )
              }}
            </p>
            <label v-if="dialog === 'extend'"
              >{{ t("从今天起有效天数", "Days from today")
              }}<input
                v-model="form.days"
                type="number"
                min="1"
                max="365"
                required /></label
            ><label
              >{{ t("原因", "Reason")
              }}<textarea
                v-model="form.note"
                required
                maxlength="1000"
                rows="4"
              /></label></template
          ><button class="primary full" :disabled="busy">
            {{ t("确认", "Confirm") }}
          </button>
        </form>
        <p v-if="error" class="error" role="alert">{{ error }}</p>
      </section>
    </div>
  </div>
</template>
