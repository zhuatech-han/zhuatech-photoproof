// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
import test from "node:test";
import assert from "node:assert/strict";
import { api, resetApi, download } from "./api.js";
const originalFetch = globalThis.fetch;
test.afterEach(() => {
  globalThis.fetch = originalFetch;
  resetApi();
});
test("JSON mutation uses actual CSRF header name from bootstrap", async () => {
  const seen = [];
  globalThis.fetch = async (path, options) => {
    seen.push([path, options]);
    return path.endsWith("/csrf")
      ? new Response(
          JSON.stringify({ header: "X-CSRF-TOKEN", token: "TEST-only" }),
          { status: 200 },
        )
      : new Response('{"ok":true}', { status: 200 });
  };
  await api("/jobs", { method: "POST", body: { title: "TEST" } });
  assert.equal(seen.length, 2);
  assert.equal(seen[1][1].headers["X-CSRF-TOKEN"], "TEST-only");
  assert.equal(seen[1][1].body, '{"title":"TEST"}');
});
test("network failure during write is unknown result and is never replayed", async () => {
  let mutations = 0;
  globalThis.fetch = async (path) => {
    if (path.endsWith("/csrf"))
      return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
    mutations++;
    throw new Error("lost");
  };
  await assert.rejects(
    api("/jobs", { method: "POST", body: {} }),
    (e) => e.message === "RESULT_UNKNOWN",
  );
  assert.equal(mutations, 1);
});
test("identity reset obtains a new CSRF token for the new session", async () => {
  let bootstraps = 0;
  globalThis.fetch = async (path) =>
    path.endsWith("/csrf")
      ? (bootstraps++, new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}'))
      : new Response("{}");
  await api("/first", { method: "POST" });
  resetApi();
  await api("/second", { method: "POST" });
  assert.equal(bootstraps, 2);
});
test("failed upload response preserves business code and does not retry", async () => {
  let calls = 0;
  globalThis.fetch = async (path) => {
    if (path.endsWith("/csrf"))
      return new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}');
    calls++;
    return new Response('{"code":"VERSION_CONFLICT"}', { status: 409 });
  };
  await assert.rejects(
    api("/jobs/1/media", { method: "POST", body: new FormData() }),
    (e) => e.status === 409 && e.message === "VERSION_CONFLICT",
  );
  assert.equal(calls, 1);
});

test("atomic import failure preserves the offending CSV row", async () => {
  globalThis.fetch = async (path) =>
    path.endsWith("/csrf")
      ? new Response('{"header":"X-CSRF-TOKEN","token":"TEST"}')
      : new Response('{"code":"CONFLICT","row":3}', { status: 409 });
  await assert.rejects(
    api("/clients/import", { method: "POST", body: [] }),
    (e) => e.message === "CONFLICT" && e.row === 3,
  );
});

test("large downloads are preflighted and saved through native URL without reading a blob", async () => {
  const before = globalThis.document;
  const seen = [];
  let clicked = false;
  const link = {
    click() {
      clicked = true;
    },
    remove() {},
  };
  globalThis.document = {
    createElement: () => link,
    body: { appendChild() {} },
  };
  globalThis.fetch = async (path, options) => {
    seen.push([path, options.method]);
    return new Response(null, { status: 200 });
  };
  try {
    await download("/jobs/1/download", "TEST.zip");
    assert.deepEqual(seen, [["/api/jobs/1/download", "HEAD"]]);
    assert.equal(link.href, "/api/jobs/1/download");
    assert.equal(clicked, true);
  } finally {
    globalThis.document = before;
  }
});
test("denied download preflight never opens an error document as a file", async () => {
  const before = globalThis.document;
  let clicked = false;
  globalThis.document = {
    createElement: () => ({
      click() {
        clicked = true;
      },
    }),
  };
  globalThis.fetch = async () => new Response(null, { status: 409 });
  try {
    await assert.rejects(
      download("/jobs/1/download", "TEST.zip"),
      (e) => e.message === "DOWNLOAD_LOCKED",
    );
    assert.equal(clicked, false);
  } finally {
    globalThis.document = before;
  }
});
