// Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2

import test from "node:test";
import assert from "node:assert/strict";
import { currentFinals, parseCsv, csvCell } from "./domain.js";
test("latest finals belong to current round and preserve exact asset id", () => {
  assert.deepEqual(
    currentFinals(
      [
        { id: 1, photoId: 8, kind: "FINAL", roundNumber: 1, revisionNumber: 2 },
        { id: 2, photoId: 8, kind: "FINAL", roundNumber: 2, revisionNumber: 1 },
        { id: 3, photoId: 8, kind: "FINAL", roundNumber: 2, revisionNumber: 2 },
        { id: 4, kind: "PROOF", roundNumber: 2 },
      ],
      2,
    ).map((x) => x.id),
    [3],
  );
});
test("quoted client import preserves comma and doubled quotes", () => {
  assert.deepEqual(
    parseCsv(
      'code,name,contact,departmentId\r\nC01,"Client, studio","a""b",1',
    )[0],
    {
      code: "C01",
      name: "Client, studio",
      contact: 'a"b',
      departmentId: 1,
      enabled: true,
    },
  );
});
test("malformed quoting and partial rows rejected before import", () => {
  for (const s of [
    'code,name,contact,departmentId\nC01,"Unclosed,x,1',
    'code,name,contact,departmentId\nC01,"closed"junk,x,1',
    "code,name,contact,departmentId\nC01,Name,x",
    "wrong\nC01,N,x,1",
  ])
    assert.throws(() => parseCsv(s));
});
test("CSV guard covers hidden prefix formula injection", () => {
  for (const s of ["=2+3", "\t@evil", "\ufeff+2", "\u200b-2"])
    assert.ok(csvCell(s).startsWith("\"'"));
  assert.equal(csvCell('normal "label"'), '"normal ""label"""');
});
