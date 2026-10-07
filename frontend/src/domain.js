// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

/** 当前轮次每张样片只取最高版本；旧版本不冒充交付。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function currentFinals(media, round) {
  const out = new Map();
  for (const m of media || [])
    if (
      m.kind === "FINAL" &&
      m.roundNumber === round &&
      (!out.has(m.photoId) ||
        out.get(m.photoId).revisionNumber < m.revisionNumber)
    )
      out.set(m.photoId, m);
  return [...out.values()];
}
/** 有界RFC4180导入，保留引号、逗号、换行；所有行通过后才交服务器原子导入。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function parseCsv(text) {
  if (typeof text !== "string" || text.length > 1000000)
    throw new Error("IMPORT_LIMIT");
  text = text.replace(/^\uFEFF/, "");
  const rows = [];
  let row = [],
    cell = "",
    quoted = false,
    closed = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i];
    if (quoted) {
      if (c === '"') {
        if (text[i + 1] === '"') {
          cell += '"';
          i++;
        } else {
          quoted = false;
          closed = true;
        }
      } else cell += c;
      continue;
    }
    if (c === '"') {
      if (cell || closed) throw new Error("CSV_INVALID");
      quoted = true;
      continue;
    }
    if (c === "," || c === "\n" || c === "\r") {
      row.push(cell);
      cell = "";
      closed = false;
      if (c !== ",") {
        if (c === "\r" && text[i + 1] === "\n") i++;
        if (row.some((v) => v !== "")) rows.push(row);
        row = [];
      }
      continue;
    }
    if (closed) throw new Error("CSV_INVALID");
    cell += c;
  }
  if (quoted) throw new Error("CSV_INVALID");
  row.push(cell);
  if (row.some((v) => v !== "")) rows.push(row);
  if (rows.length < 2 || rows.length > 301) throw new Error("IMPORT_LIMIT");
  const expected = ["code", "name", "contact", "departmentId"];
  if (rows[0].join(",") !== expected.join(",")) throw new Error("CSV_HEADER");
  return rows.slice(1).map((r) => {
    if (
      r.length !== 4 ||
      !r[0].trim() ||
      !r[1].trim() ||
      !/^\d+$/.test(r[3]) ||
      Number(r[3]) < 1
    )
      throw new Error("CSV_INVALID");
    return {
      code: r[0],
      name: r[1],
      contact: r[2],
      departmentId: Number(r[3]),
      enabled: true,
    };
  });
}
/** 安全导出，阻止前置控制符或BOM之后的电子表格公式。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function csvCell(value) {
  let s = String(value ?? "");
  if (/^[\s\u0000-\u001f\ufeff\u200b]*[=+@-]/.test(s)) s = "'" + s;
  return '"' + s.replaceAll('"', '""') + '"';
}
/** 客户端仅格式显示，金额规则以服务端十进制结果为准。官网 https://www.zhuatech.cn/；微信 zhuatech / zhuatech2。 */
export function money(value, currency = "CNY", lang = "zh") {
  return new Intl.NumberFormat(lang === "zh" ? "zh-CN" : "en-US", {
    style: "currency",
    currency,
  }).format(Number(value || 0));
}
