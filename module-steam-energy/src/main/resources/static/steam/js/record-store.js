/* 스팀에너지관리 — 사고·정비 이력 · 유동상 설비개선 저장소 (묶음 3-E).
 *
 * 이 화면들은 옛 셀 저장소에 "표 이름 steam_cell_value, 월 키 YYYY-00, 키 = 접두어:번호, col_index = 항목" 으로
 * 한 건을 여러 칸에 나눠 저장했다. 여기서는 그 호출 모양(조회 · 칸 저장 · 행 삭제)을 그대로 받아
 * 규격 표(한 건 한 행)로 옮긴다 — 화면은 fetch 대신 RecordStore.fetch 를 부르기만 하면 된다.
 *
 *   accident-record: · maintenance-record:                        (유동상)  → steam_incident
 *   srf-accident-record:                                          (SRF)     → steam_incident
 *   incinerator-accident-record: · incinerator-maintenance-record: (소각로) → steam_incident
 *   improvement-monthly:  → steam_fluidized_improvement_payment
 *   improvement-item:     → steam_fluidized_improvement_item
 *
 *   GET    /tables/steam_cell_value?month=YYYY-00
 *   POST   /tables/steam_cell_value          body { month, row_key, col_index, cell_value }
 *   PATCH  /tables/steam_cell_value/{id}     body 같음
 *   DELETE /tables/steam_cell_value/row-group?month=YYYY-00&rowKey=접두어:번호   (그 기록 한 건만 지운다)
 *
 * 필요: steam-energy-api.js, steam-row-store.js
 */
(function (global) {
  "use strict";

  var RS = global.SteamRowStore;
  var LEGACY_TABLE = "steam_cell_value";

  var TABLES = {
    incident: {
      path: "incidents", idField: "incidentId",
      cols: { 1: "occurDate", 2: "reason", 3: "actionTaken", 4: "downtimeHours", 5: "lossAmount", 6: "note", 7: "sortOrder" },
      numeric: { sortOrder: 1 },
    },
    payment: {
      path: "fluidized-improvement-payments", idField: "improvementPaymentId",
      cols: { 1: "payMonth", 2: "paymentAmount", 3: "spentAmount", 4: "vendorName", 5: "usageDesc", 6: "sortOrder" },
      numeric: { paymentAmount: 1, spentAmount: 1, sortOrder: 1 },
    },
    item: {
      path: "fluidized-improvement-items", idField: "improvementItemId",
      cols: { 1: "category", 2: "itemNo", 3: "itemName", 4: "planCost", 5: "actualCost", 6: "status", 7: "remark", 8: "sortOrder" },
      numeric: { planCost: 1, actualCost: 1, sortOrder: 1 },
    },
  };
  // 접두어 → [표, 시설, 종류]
  var PREFIXES = {
    "accident-record:": ["incident", "FLUIDIZED", "ACCIDENT"],
    "maintenance-record:": ["incident", "FLUIDIZED", "MAINTENANCE"],
    "srf-accident-record:": ["incident", "SRF", "ACCIDENT"],
    "incinerator-accident-record:": ["incident", "INCINERATOR", "ACCIDENT"],
    "incinerator-maintenance-record:": ["incident", "INCINERATOR", "MAINTENANCE"],
    "improvement-monthly:": ["payment"],
    "improvement-item:": ["item"],
  };
  Object.keys(TABLES).forEach(function (name) {
    var t = TABLES[name];
    t.store = RS.create({ path: t.path, keyFields: ["yearNo", "recordKey"], idField: t.idField });
  });

  function kindOf(rowKey) {
    var colon = String(rowKey || "").indexOf(":");
    return colon < 0 ? null : PREFIXES[String(rowKey).slice(0, colon + 1)] || null;
  }
  function yearOf(monthKey) {
    var m = /^(\d{4})-00$/.exec(String(monthKey || ""));
    return m ? Number(m[1]) : null;
  }
  function legacyRow(monthKey, rowKey, col, value) {
    return { id: "record#" + monthKey + "#" + rowKey + "#" + col, month: monthKey, table_name: LEGACY_TABLE,
      row_key: rowKey, col_index: col, cell_value: value === null || value === undefined ? "" : String(value) };
  }

  /** 그 해의 모든 기록을 옛 형식 칸 목록으로 편다. */
  async function legacyRows(monthKey) {
    var year = yearOf(monthKey);
    var out = [];
    if (!year) return out;
    var names = Object.keys(TABLES);
    var lists = await Promise.all(names.map(function (name) { return RS.cachedGet(TABLES[name].path, year); }));
    names.forEach(function (name, i) {
      var cols = TABLES[name].cols;
      lists[i].forEach(function (row) {
        Object.keys(cols).forEach(function (col) {
          var value = row[cols[col]];
          if (value === null || value === undefined || value === "") return;
          out.push(legacyRow(monthKey, row.recordKey, Number(col), value));
        });
      });
    });
    return out;
  }

  /** 칸 하나 저장 — 그 기록의 행이 없으면 만든다. */
  async function saveCell(monthKey, rowKey, col, rawValue) {
    var year = yearOf(monthKey);
    var kind = kindOf(rowKey);
    var text = rawValue === null || rawValue === undefined ? "" : String(rawValue);
    if (year && kind) {
      var table = TABLES[kind[0]];
      var field = table.cols[Number(col)];
      if (field) {
        var patch = {};
        if (table.numeric[field]) {
          var n = global.SteamEnergyApi.num(text);
          patch[field] = field === "sortOrder" && n !== null ? Math.round(n) : n;
        } else {
          patch[field] = text.trim() === "" ? null : text;
        }
        if (kind[1]) { patch.facility = kind[1]; patch.kind = kind[2]; }
        await table.store.save({ yearNo: year, recordKey: String(rowKey) }, patch, { year: year });
        RS.clearCache();
      }
    }
    return legacyRow(String(monthKey), String(rowKey), Number(col), text);
  }

  /** 기록 한 건 삭제 (소프트 삭제). */
  async function deleteRecord(monthKey, rowKey) {
    var year = yearOf(monthKey);
    var kind = kindOf(rowKey);
    if (!year || !kind) return 0;
    var table = TABLES[kind[0]];
    await RS.enqueue(async function () {});          // 앞서 보낸 저장이 끝난 뒤에
    RS.clearCache();
    var rows = await RS.cachedGet(table.path, year);
    var hit = rows.filter(function (row) { return row.recordKey === rowKey; });
    for (var i = 0; i < hit.length; i += 1) await global.SteamEnergyApi.del("/" + table.path + "/" + hit[i][table.idField]);
    RS.clearCache();
    return hit.length;
  }

  function response(ok, body) {
    return { ok: ok, status: ok ? 200 : 500, json: async function () { return body; }, text: async function () { return JSON.stringify(body); } };
  }

  async function storeFetch(url, init) {
    var method = (init && init.method ? String(init.method) : "GET").toUpperCase();
    var text = String(url);
    var query = new URLSearchParams(text.split("?")[1] || "");
    try {
      if (method === "GET") {
        if (!yearOf(query.get("month"))) return global.fetch(url, init);
        return response(true, { data: await legacyRows(query.get("month")) });
      }
      if (method === "DELETE") {
        if (text.indexOf("/row-group") < 0 || !yearOf(query.get("month"))) return global.fetch(url, init);
        return response(true, { deleted: await deleteRecord(query.get("month"), query.get("rowKey")) });
      }
      var body = JSON.parse((init && init.body) || "{}");
      if (!yearOf(body.month) || !kindOf(body.row_key)) return global.fetch(url, init);
      return response(true, await saveCell(body.month, body.row_key, body.col_index, body.cell_value));
    } catch (e) {
      console.error("[RecordStore]", e);
      return response(false, { message: e && e.message ? e.message : String(e) });
    }
  }

  global.RecordStore = { fetch: storeFetch, legacyRows: legacyRows, saveCell: saveCell, deleteRecord: deleteRecord };
})(window);
