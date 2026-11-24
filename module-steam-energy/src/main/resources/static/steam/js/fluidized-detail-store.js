/* 스팀에너지관리 — 유동상 세부 운영내역 저장소 (묶음 4-B).
 *
 * 세부 운영내역 화면 · 유동상 운전일지 화면은 옛 셀 저장소(/tables/steam_cell_value)를
 * "표 이름 fluidized_detail_main, 월 키 YYYY-MM, 키 = 엑셀 셀 주소, col_index 0" 으로 읽고 썼다.
 * 여기서는 그 호출 모양을 그대로 받아 규격 표 3개로 옮긴다 — 화면은 fetch 대신 FluidizedDetailStore.fetch 를 부르기만 하면 된다.
 *
 *   {C~AG}{행}   (일 = 열 번호 − 2)            → steam_fluidized_detail_daily   (/fluidized-detail-dailies)
 *   AJ{행} · AK15 · AK16 · C40 의 글자          → steam_fluidized_detail_month   (/fluidized-detail-months)
 *   custom|{구역}|{행}|label · insertAfter · d{일} → steam_fluidized_detail_custom_row (/fluidized-detail-custom-rows)
 *
 * 행 번호 → 필드 표는 서버의 service/table/FluidizedDetailFields.java 와 같아야 한다.
 *
 * 필요: steam-energy-api.js, steam-row-store.js
 */
(function (global) {
  "use strict";

  var RS = global.SteamRowStore;
  var TABLE = "fluidized_detail_main";
  var DAILY = "fluidized-detail-dailies", MONTH = "fluidized-detail-months", CUSTOM = "fluidized-detail-custom-rows";

  // 일자 열(C~AG)의 입력 행 → 일별 필드
  var DAY_ROWS = {
    4: "runHours", 6: "steamProducedTon", 8: "sludgeBurnTon", 9: "srfBurnTon", 11: "steamUnitPrice", 13: "stopHourUnitPrice",
    18: "ammoniumCarbonateInKg", 19: "ammoniumCarbonateUseKg", 21: "boilerChemInKg", 22: "boilerChemUseKg", 24: "activatedCarbonInKg",
    25: "activatedCarbonUseKg", 27: "causticSodaInKg", 28: "causticSodaUseKg", 30: "slakedLimeInKg", 31: "slakedLimeUseKg", 33: "saltInKg",
    34: "saltUseKg", 36: "dieselInL", 37: "dieselUseL", 39: "silicaSandInKg", 40: "silicaSandUseKg", 41: "activatedCarbonExtraInKg",
    42: "emergencyDieselInL", 46: "srfInTon", 49: "flyAshGeneratedTon", 50: "flyAshOut01Ton", 51: "flyAshOut02Ton", 52: "flyAshOut03Ton",
    53: "flyAshOut04Ton", 54: "flyAshOut05Ton", 55: "flyAshOut06Ton", 56: "flyAshOut07Ton", 57: "flyAshOut08Ton", 58: "bottomAshGeneratedTon",
    59: "bottomAshOut01Ton", 60: "bottomAshOut02Ton", 61: "bottomAshOut03Ton", 62: "bottomAshOut04Ton", 65: "powerMainKwh",
    66: "powerAshVentFilterKwh", 67: "powerBagFilterKwh", 68: "powerScrubberKwh", 69: "powerCarbonFeedKwh", 70: "powerLimeFeedKwh",
    71: "powerUreaKwh", 72: "powerFdFanKwh", 74: "reuseWaterTon", 75: "riverbedWaterTon", 76: "condensateTon", 77: "boilerFeedWaterTon",
    78: "wasteWaterTon", 79: "softWaterTon", 81: "sludgeOutsource01Ton", 82: "sludgeOutsource02Ton", 83: "sludgeOutsource03Ton",
    84: "sludgeOutsource04Ton", 85: "sludgeOutsource05Ton", 86: "sludgeOutsource06Ton", 87: "sludgeOutsource07Ton", 88: "sludgeOutsource08Ton",
    89: "sludgeOutsource09Ton", 90: "sludgeOutsource10Ton", 91: "sludgeOutsource11Ton", 92: "inspectionFeeQty",
  };
  // 단가 열(AJ)의 입력 행 → 월별 필드
  var PRICE_ROWS = {
    6: "steamProducedUnitPrice", 18: "ammoniumCarbonateUnitPrice", 21: "boilerChemInUnitPrice", 22: "boilerChemUseUnitPrice",
    24: "activatedCarbonInUnitPrice", 25: "activatedCarbonUseUnitPrice", 27: "causticSodaInUnitPrice", 28: "causticSodaUseUnitPrice",
    30: "slakedLimeUnitPrice", 33: "saltUnitPrice", 36: "dieselUnitPrice", 40: "silicaSandUseUnitPrice", 46: "srfUnitPrice",
    49: "flyAshGeneratedUnitPrice", 50: "flyAshOut01UnitPrice", 51: "flyAshOut02UnitPrice", 52: "flyAshOut03UnitPrice", 53: "flyAshOut04UnitPrice",
    54: "flyAshOut05UnitPrice", 55: "flyAshOut06UnitPrice", 56: "flyAshOut07UnitPrice", 57: "flyAshOut08UnitPrice",
    58: "bottomAshGeneratedUnitPrice", 59: "bottomAshOut01UnitPrice", 60: "bottomAshOut02UnitPrice", 61: "bottomAshOut03UnitPrice",
    62: "bottomAshOut04UnitPrice", 65: "powerUnitPrice", 71: "powerUreaUnitPrice", 72: "powerFdFanUnitPrice", 74: "reuseWaterUnitPrice",
    76: "condensateUnitPrice", 77: "boilerFeedWaterUnitPrice", 78: "wasteWaterUnitPrice", 79: "softWaterUnitPrice", 81: "sludgeOutsource01UnitPrice",
    82: "sludgeOutsource02UnitPrice", 83: "sludgeOutsource03UnitPrice", 84: "sludgeOutsource04UnitPrice", 85: "sludgeOutsource05UnitPrice",
    86: "sludgeOutsource06UnitPrice", 87: "sludgeOutsource07UnitPrice", 88: "sludgeOutsource08UnitPrice", 89: "sludgeOutsource09UnitPrice",
    90: "sludgeOutsource10UnitPrice", 91: "sludgeOutsource11UnitPrice", 92: "inspectionFeeUnitPrice",
  };
  // 비용 열(AK)의 입력 행 → 월별 필드
  var COST_ROWS = { 15: "improvementCost", 16: "extraCost" };
  var NOTE_ROW = 40, NOTE_FIELD = "silicaSandUseNote";   // 규사 사용 줄 1일 칸(C40)의 글자

  var stores = {
    daily: RS.create({ path: DAILY, keyFields: ["usageDate"], idField: "detailDailyId" }),
    month: RS.create({ path: MONTH, keyFields: ["yearNo", "monthNo"], idField: "detailMonthId" }),
    custom: RS.create({ path: CUSTOM, keyFields: ["yearNo", "monthNo", "section", "rowKey"], idField: "detailCustomRowId" }),
  };

  function ym(monthKey) {
    var m = /^(\d{4})-(\d{2})$/.exec(String(monthKey || ""));
    return m ? { year: Number(m[1]), month: Number(m[2]) } : null;
  }
  function columnLabel(index) {
    var label = "";
    for (var n = index; n > 0; n = Math.floor((n - 1) / 26)) label = String.fromCharCode(65 + ((n - 1) % 26)) + label;
    return label;
  }
  function columnNumber(label) {
    var n = 0;
    for (var i = 0; i < label.length; i += 1) n = n * 26 + (label.charCodeAt(i) - 64);
    return n;
  }
  function pad2(n) { return String(n).padStart(2, "0"); }
  function has(v) { return v !== null && v !== undefined && v !== ""; }

  /** 그 달의 칸 목록 — [{ rowKey, value }] (값 없는 칸은 없다. 추가 행의 label 은 비어 있어도 낸다) */
  async function rows(monthKey) {
    var p = ym(monthKey), out = [];
    if (!p) return out;
    var Api = global.SteamEnergyApi;
    var got = await Promise.all([RS.cachedGet(DAILY, p.year, p.month), RS.cachedGet(MONTH, p.year, p.month), RS.cachedGet(CUSTOM, p.year, p.month)]);
    var firstDayNote = false;
    got[0].forEach(function (row) {
      var day = Api.dayOf(row.usageDate), col = columnLabel(day + 2);
      Object.keys(DAY_ROWS).forEach(function (r) {
        var value = row[DAY_ROWS[r]];
        if (!has(value)) return;
        out.push({ rowKey: col + r, value: value });
        if (day === 1 && Number(r) === NOTE_ROW) firstDayNote = true;
      });
    });
    got[1].forEach(function (row) {
      if (row.monthNo !== p.month) return;
      Object.keys(PRICE_ROWS).forEach(function (r) { if (has(row[PRICE_ROWS[r]])) out.push({ rowKey: "AJ" + r, value: row[PRICE_ROWS[r]] }); });
      Object.keys(COST_ROWS).forEach(function (r) { if (has(row[COST_ROWS[r]])) out.push({ rowKey: "AK" + r, value: row[COST_ROWS[r]] }); });
      if (!firstDayNote && has(row[NOTE_FIELD])) out.push({ rowKey: "C" + NOTE_ROW, value: row[NOTE_FIELD] });
    });
    got[2].forEach(function (row) {
      if (row.monthNo !== p.month) return;
      var prefix = "custom|" + row.section + "|" + row.rowKey + "|";
      out.push({ rowKey: prefix + "label", value: row.label === null || row.label === undefined ? "" : row.label });
      if (has(row.insertAfter)) out.push({ rowKey: prefix + "insertAfter", value: row.insertAfter });
      for (var d = 1; d <= 31; d += 1) if (has(row["day" + pad2(d)])) out.push({ rowKey: prefix + "d" + d, value: row["day" + pad2(d)] });
    });
    return out;
  }

  /** 칸 하나의 저장 자리 — { store, scope, key, field, text? } 또는 null (저장하지 않는 칸) */
  function locate(monthKey, rowKey) {
    var p = ym(monthKey);
    if (!p) return null;
    var scope = { year: p.year, month: p.month };
    var c = /^custom\|([^|]+)\|([^|]+)\|(label|insertAfter|d(\d{1,2}))$/.exec(rowKey);
    if (c) {
      var field = c[3] === "label" ? "label" : c[3] === "insertAfter" ? "insertAfter" : "day" + pad2(Number(c[4]));
      if (c[4] && !(Number(c[4]) >= 1 && Number(c[4]) <= 31)) return null;
      return { store: stores.custom, scope: scope, key: { yearNo: p.year, monthNo: p.month, section: c[1], rowKey: c[2] }, field: field, text: field === "label" };
    }
    var m = /^([A-Z]{1,2})(\d{1,3})$/.exec(rowKey);
    if (!m) return null;
    var col = columnNumber(m[1]), r = Number(m[2]);
    if (col >= 3 && col <= 33) {
      var day = col - 2;
      if (!DAY_ROWS[r] || day > new Date(p.year, p.month, 0).getDate()) return null;
      return { store: stores.daily, scope: scope, key: { usageDate: global.SteamEnergyApi.isoDate(p.year, p.month, day) }, field: DAY_ROWS[r] };
    }
    var monthField = m[1] === "AJ" ? PRICE_ROWS[r] : m[1] === "AK" ? COST_ROWS[r] : null;
    return monthField ? { store: stores.month, scope: scope, key: { yearNo: p.year, monthNo: p.month }, field: monthField } : null;
  }

  function legacyRow(monthKey, rowKey, value) {
    return { id: TABLE + "#" + monthKey + "#" + rowKey + "#0", month: monthKey, table_name: TABLE, row_key: rowKey, col_index: 0,
      cell_value: value === null || value === undefined ? "" : String(value) };
  }

  /** 칸 하나 저장. 돌려주는 값은 옛 형식의 행. */
  async function saveCell(monthKey, rowKey, rawValue) {
    var text = rawValue === null || rawValue === undefined ? "" : String(rawValue);
    var target = locate(String(monthKey), String(rowKey));
    if (target) {
      var patch = {};
      var number = global.SteamEnergyApi.num(text);
      // 규사 사용 줄 1일 칸에 글자를 넣으면 월별 비고로 받는다 (숫자면 그 날의 값)
      if (rowKey === "C" + NOTE_ROW && text.trim() !== "" && number === null) {
        var p = ym(monthKey);
        patch[NOTE_FIELD] = text.trim();
        await stores.month.save({ yearNo: p.year, monthNo: p.month }, patch, target.scope);
      } else {
        patch[target.field] = target.text ? (text.trim() === "" ? null : text) : (target.field === "insertAfter" && number !== null ? Math.round(number) : number);
        await target.store.save(target.key, patch, target.scope);
      }
      RS.clearCache();
    }
    return legacyRow(String(monthKey), String(rowKey), text);
  }

  /** 추가 행 삭제 — 그 행의 아무 칸이나 지우라고 하면 행 전체를 지운다 (화면은 행을 없앨 때만 칸 삭제를 부른다). */
  async function deleteCell(id) {
    var parts = String(id).split("#");
    var p = ym(parts[1]);
    var c = /^custom\|([^|]+)\|([^|]+)\|/.exec(parts[2] || "");
    if (parts[0] !== TABLE || !p || !c) return 0;
    await RS.enqueue(async function () {});          // 앞서 보낸 저장이 끝난 뒤에
    RS.clearCache();
    var hit = (await RS.cachedGet(CUSTOM, p.year, p.month)).filter(function (row) {
      return row.monthNo === p.month && row.section === c[1] && row.rowKey === c[2];
    });
    for (var i = 0; i < hit.length; i += 1) await global.SteamEnergyApi.del("/" + CUSTOM + "/" + hit[i].detailCustomRowId);
    RS.clearCache();
    return hit.length;
  }

  function response(ok, body) {
    return { ok: ok, status: ok ? 200 : 500, json: async function () { return body; }, text: async function () { return JSON.stringify(body); } };
  }

  async function storeFetch(url, init) {
    var method = (init && init.method ? String(init.method) : "GET").toUpperCase();
    var text = String(url);
    try {
      if (method === "GET") {
        var query = new URLSearchParams(text.split("?")[1] || "");
        var names = (query.get("tableNames") || TABLE).split(",").map(function (s) { return s.trim(); }).filter(Boolean);
        if (!ym(query.get("month")) || names.length !== 1 || names[0] !== TABLE) return global.fetch(url, init);
        var data = (await rows(query.get("month"))).map(function (c) { return legacyRow(query.get("month"), c.rowKey, c.value); });
        return response(true, { data: data, total: data.length, page: 1, limit: Math.max(data.length, 1) });
      }
      if (method === "DELETE") {
        var id = decodeURIComponent(text.split("?")[0].split("/").pop());
        if (id.indexOf(TABLE + "#") !== 0) return global.fetch(url, init);
        return response(true, { ok: true, deleted: await deleteCell(id) });
      }
      var body = JSON.parse((init && init.body) || "{}");
      if (body.table_name !== TABLE || !ym(body.month)) return global.fetch(url, init);
      return response(true, await saveCell(body.month, body.row_key, body.cell_value));
    } catch (e) {
      console.error("[FluidizedDetailStore]", e);
      return response(false, { message: e && e.message ? e.message : String(e) });
    }
  }

  global.FluidizedDetailStore = { TABLE: TABLE, fetch: storeFetch, rows: rows, locate: locate, saveCell: saveCell, deleteCell: deleteCell };
})(window);
