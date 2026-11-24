/* 스팀에너지관리 — 소각로 위탁 저장소 (묶음 3-B).
 *
 * 소각로 화면들은 옛 셀 저장소(/tables/steam_cell_value)를 "월 키 + 표 이름 + 칸 키" 로 읽고 썼다.
 * 여기서는 그 호출 모양을 그대로 받아 규격 표(/steam-energy-api/incinerator-*)로 옮긴다 —
 * 화면은 fetch 대신 IncineratorStore.fetch 를 부르기만 하면 된다. (서버의 IncineratorCellStore 와 같은 대응표)
 *
 *   incinerator_contract   (월 키 YYYY-MM)  unit1|일|steam …        → steam_incinerator_contract_daily
 *                                           summary|0|labor         → steam_incinerator_plan_monthly.contractLabor
 *   incinerator_plan_vs    (월 키 YYYY-MM)  labor|plan-m월 …, notes|m월-u1 → 그 월의 행 / break|… → 고른 달의 행
 *   incinerator_operation  (연 키 YYYY)     hours|월|u1-plan …      → steam_incinerator_operation_monthly
 *   incinerator_total_cost (연 키 YYYY)     구분|월|항목            → steam_incinerator_cost_monthly (필드 = 구분-항목 camelCase)
 *
 * 조회 주소에 computed=1 을 붙이면 도급내역의 계산 칸도 함께 준다 (합계를 내는 화면용):
 *   unit{1,2}|일|oper = 스팀량 × 운영단가, unit{1,2}|일|stop = 운휴시간 × 고정비.
 *   결산 1호기 운휴시간이 비어 있고 그 날 고정비가 있으면 도급내역 화면처럼 24 로 본다 (unit1|일|runtime = 24 도 함께 준다).
 *   스팀량이 하루도 없는 달(단가만 기록된 달)은 계산 칸을 내지 않는다 — 그 달은 도급 실적이 없는 달로 본다.
 *
 * 필요: steam-energy-api.js, steam-row-store.js
 */
(function (global) {
  "use strict";

  // steam-energy-api.js 가 이 파일보다 뒤에 실려도 되게, 부를 때 찾는다
  var Api = {};
  ["get", "num", "isoDate", "dayOf", "monthOf"].forEach(function (name) {
    Api[name] = function () { return global.SteamEnergyApi[name].apply(null, arguments); };
  });
  var CONTRACT = "incinerator_contract", PLAN_VS = "incinerator_plan_vs",
      OPERATION = "incinerator_operation", TOTAL_COST = "incinerator_total_cost";
  var TABLES = [CONTRACT, PLAN_VS, OPERATION, TOTAL_COST];

  var CONTRACT_KEYS = {
    "unit1|steam": "u1Steam", "unit1|unit": "u1Unit", "unit1|runtime": "u1Runtime", "unit1|fixed": "u1Fixed",
    "unit2|steam": "u2Steam", "unit2|unit": "u2Unit", "unit2|runtime": "u2Runtime", "unit2|fixed": "u2Fixed",
    "real|steam-1": "realSteam1", "real|unit-1": "realUnit1", "real|steam-2": "realSteam2", "real|unit-2": "realUnit2",
  };
  var PLAN_GRID_KEYS = { "labor|plan": "laborPlan", "labor|actual": "laborActual", "operation|plan": "operationPlan",
    "operation|actual": "operationActual", "total|plan": "totalPlan" };
  var PLAN_BREAK_KEYS = { "break|m-plan": "brkMonthPlan", "break|m-actual": "brkMonthActual", "break|y-plan": "brkYtdPlan",
    "break|y-actual": "brkYtdActual", "break|prev-actual": "brkPrevActual", "break|note": "brkNote" };
  var OPERATION_KEYS = { "hours|u1-plan": "u1Plan", "hours|u1-actual": "u1Actual", "hours|u2-plan": "u2Plan", "hours|u2-actual": "u2Actual",
    "metric|steam-plan": "steamPlan", "metric|steam-actual": "steamActual", "metric|rate-plan": "ratePlan",
    "metric|rate-actual": "rateActual", "metric|cost-plan": "costPlan", "metric|cost-actual": "costActual" };
  var COST_ITEMS = [
    "op-burn|burn-1", "op-burn|burn-2", "op-burn|rate-1", "op-burn|rate-2", "op-cost|unit", "op-cost|deduct",
    "self-srf|in-qty", "self-srf|in-rev", "self-srf|floor-recycle", "self-srf|floor-bury", "self-srf|fly-solid", "self-srf|fly-bury",
    "self-srf|cost-floor", "self-srf|cost-fly", "self-srf|cost-misc",
    "self-util|p1", "self-util|p2", "self-util|prate", "self-util|naoh-qty", "self-util|naoh-unit", "self-util|water",
    "self-util|waste-charge", "self-util|etc", "self-total|total", "self-total|burn-rate", "self-total|steam-rate",
    "mat-oil|u1", "mat-oil|u2", "mat-oil|unit", "mat-oil|ck-qty", "mat-oil|ck-unit",
    "mat-chem|u-qty", "mat-chem|u-unit", "mat-chem|s-qty", "mat-chem|s-unit",
    "mat-misc|a-qty", "mat-misc|a-unit", "mat-misc|k-qty", "mat-misc|k-unit",
  ];
  var TEXT_FIELDS = { noteU1: 1, noteU2: 1, brkNote: 1 };
  var UNIT1_RUNTIME_DEFAULT = 24;

  function camel(text) {
    return text.split(/[-|]/).map(function (w, i) { return i === 0 ? w : w.charAt(0).toUpperCase() + w.slice(1); }).join("");
  }
  var COST_KEYS = {};
  COST_ITEMS.forEach(function (key) { COST_KEYS[key] = camel(key); });

  var stores = {
    contract: global.SteamRowStore.create({ path: "incinerator-contract-dailies", keyFields: ["usageDate"], idField: "contractDailyId" }),
    plan: global.SteamRowStore.create({ path: "incinerator-plan-monthlies", keyFields: ["yearNo", "monthNo"], idField: "planMonthlyId" }),
    operation: global.SteamRowStore.create({ path: "incinerator-operation-monthlies", keyFields: ["yearNo", "monthNo"], idField: "operationMonthlyId" }),
    cost: global.SteamRowStore.create({ path: "incinerator-cost-monthlies", keyFields: ["yearNo", "monthNo"], idField: "costMonthlyId" }),
  };

  // ── 조회: 한 해치를 한 번에 받아 잠깐 재사용한다 (화면이 12개월을 따로 물어도 요청은 한 번) ──
  var cache = new Map();   // "path|year" → { at, promise }
  var CACHE_MS = 3000;
  function yearRows(path, year) {
    var key = path + "|" + year;
    var hit = cache.get(key);
    if (hit && Date.now() - hit.at < CACHE_MS) return hit.promise;
    var promise = Api.get("/" + path + "?year=" + year).catch(function (e) { cache.delete(key); throw e; });
    cache.set(key, { at: Date.now(), promise: promise });
    return promise;
  }
  function clearCache() { cache.clear(); }

  function has(v) { return v !== null && v !== undefined; }

  function cell(out, table, monthKey, rowKey, value) {
    if (!has(value) || value === "") return;
    out.push({ id: table + "#" + monthKey + "#" + rowKey, month: monthKey, table_name: table, row_key: rowKey, col_index: 1, cell_value: String(value) });
  }

  /** 옛 형식 행 목록. monthKey: 'YYYY-MM' (도급내역·사업계획대비) 또는 'YYYY' (가동실적·총 운영비용). */
  async function legacyRows(table, monthKey, computed) {
    var out = [];
    var year = Number(String(monthKey).slice(0, 4));
    if (!Number.isFinite(year) || year < 2000) return out;
    var month = /^\d{4}-\d{2}$/.test(monthKey) ? Number(monthKey.slice(5, 7)) : null;
    if (table === CONTRACT && month) {
      var pair = await Promise.all([yearRows("incinerator-contract-dailies", year), yearRows("incinerator-plan-monthlies", year)]);
      var monthRows = pair[0].filter(function (row) { return Api.monthOf(row.usageDate) === month; });
      // 계산 칸은 스팀량이 하루라도 들어 있는 달에만 낸다. 단가만 있는 달(단가 입력이 그 달 모든 날에 기록)은
      // 도급 실적이 아직 없는 달이므로, 합계 화면이 고정비만으로 금액을 만들지 않게 한다.
      var hasSteam = monthRows.some(function (row) {
        return ["u1Steam", "u2Steam", "realSteam1", "realSteam2"].some(function (f) { return has(row[f]) && Number(row[f]) > 0; });
      });
      if (!hasSteam) computed = false;
      monthRows.forEach(function (row) {
        var day = Api.dayOf(row.usageDate);
        Object.keys(CONTRACT_KEYS).forEach(function (key) {
          var parts = key.split("|");
          cell(out, table, monthKey, parts[0] + "|" + day + "|" + parts[1], row[CONTRACT_KEYS[key]]);
        });
        if (computed) {
          [["unit1", "u1"], ["unit2", "u2"]].forEach(function (unit) {
            var steam = row[unit[1] + "Steam"], price = row[unit[1] + "Unit"], fixed = row[unit[1] + "Fixed"], runtime = row[unit[1] + "Runtime"];
            if (unit[0] === "unit1" && !has(runtime) && has(fixed)) {
              runtime = UNIT1_RUNTIME_DEFAULT;
              cell(out, table, monthKey, "unit1|" + day + "|runtime", runtime);
            }
            if (has(steam) && has(price)) cell(out, table, monthKey, unit[0] + "|" + day + "|oper", Number(steam) * Number(price));
            if (has(runtime) && has(fixed)) cell(out, table, monthKey, unit[0] + "|" + day + "|stop", Number(runtime) * Number(fixed));
          });
        }
      });
      pair[1].forEach(function (row) { if (row.monthNo === month) cell(out, table, monthKey, "summary|0|labor", row.contractLabor); });
    } else if (table === PLAN_VS && month) {
      (await yearRows("incinerator-plan-monthlies", year)).forEach(function (row) {
        var n = row.monthNo;
        Object.keys(PLAN_GRID_KEYS).forEach(function (key) { cell(out, table, monthKey, key + "-m" + n, row[PLAN_GRID_KEYS[key]]); });
        cell(out, table, monthKey, "notes|m" + n + "-u1", row.noteU1);
        cell(out, table, monthKey, "notes|m" + n + "-u2", row.noteU2);
        if (n === month) Object.keys(PLAN_BREAK_KEYS).forEach(function (key) { cell(out, table, monthKey, key, row[PLAN_BREAK_KEYS[key]]); });
      });
    } else if (table === OPERATION && !month) {
      (await yearRows("incinerator-operation-monthlies", year)).forEach(function (row) {
        Object.keys(OPERATION_KEYS).forEach(function (key) {
          var parts = key.split("|");
          cell(out, table, monthKey, parts[0] + "|" + row.monthNo + "|" + parts[1], row[OPERATION_KEYS[key]]);
        });
      });
    } else if (table === TOTAL_COST && !month) {
      (await yearRows("incinerator-cost-monthlies", year)).forEach(function (row) {
        COST_ITEMS.forEach(function (key) {
          var parts = key.split("|");
          cell(out, table, monthKey, parts[0] + "|" + row.monthNo + "|" + parts[1], row[COST_KEYS[key]]);
        });
      });
    }
    return out;
  }

  /** 칸 키 → { store, scope, key, field }. 저장하지 않는 키(계산 칸 등)면 null. */
  function locate(table, monthKey, rowKey) {
    var year = Number(String(monthKey).slice(0, 4));
    var month = /^\d{4}-\d{2}$/.test(monthKey) ? Number(monthKey.slice(5, 7)) : null;
    var m;
    if (table === CONTRACT && month) {
      if (rowKey === "summary|0|labor") return { store: stores.plan, scope: { year: year }, key: { yearNo: year, monthNo: month }, field: "contractLabor" };
      m = /^(unit1|unit2|real)\|(\d+)\|(.+)$/.exec(rowKey);
      if (!m) return null;
      var field = CONTRACT_KEYS[m[1] + "|" + m[3]];
      var day = Number(m[2]);
      if (!field || day < 1 || day > new Date(year, month, 0).getDate()) return null;
      return { store: stores.contract, scope: { year: year, month: month }, key: { usageDate: Api.isoDate(year, month, day) }, field: field };
    }
    if (table === PLAN_VS && month) {
      var n = month, f = PLAN_BREAK_KEYS[rowKey];
      if (!f) {
        m = /^([a-z]+\|[a-z]+)-m(\d{1,2})$/.exec(rowKey);
        if (m) { f = PLAN_GRID_KEYS[m[1]]; n = Number(m[2]); }
        else {
          m = /^notes\|m(\d{1,2})-u([12])$/.exec(rowKey);
          if (m) { f = m[2] === "1" ? "noteU1" : "noteU2"; n = Number(m[1]); }
        }
      }
      if (!f || n < 1 || n > 12) return null;
      return { store: stores.plan, scope: { year: year }, key: { yearNo: year, monthNo: n }, field: f };
    }
    if ((table === OPERATION || table === TOTAL_COST) && !month) {
      m = /^([a-z-]+)\|(\d+)\|(.+)$/.exec(rowKey);
      if (!m) return null;
      var keys = table === OPERATION ? OPERATION_KEYS : COST_KEYS;
      var mf = keys[m[1] + "|" + m[3]];
      var mn = Number(m[2]);
      if (!mf || mn < 1 || mn > 12) return null;
      return { store: table === OPERATION ? stores.operation : stores.cost, scope: { year: year }, key: { yearNo: year, monthNo: mn }, field: mf };
    }
    return null;
  }

  /** 칸 하나 저장. 저장하지 않는 키면 아무 일도 하지 않는다. 옛 형식 행을 돌려준다. */
  async function saveCell(table, monthKey, rowKey, rawValue) {
    var text = rawValue === null || rawValue === undefined ? "" : String(rawValue);
    var target = locate(table, monthKey, rowKey);
    if (target) {
      var value = TEXT_FIELDS[target.field] ? (text.trim() === "" ? null : text) : Api.num(text);
      var patch = {};
      patch[target.field] = value;
      await target.store.save(target.key, patch, target.scope);
      clearCache();
    }
    return { id: table + "#" + monthKey + "#" + rowKey, month: monthKey, table_name: table, row_key: rowKey, col_index: 1, cell_value: text };
  }

  function response(ok, body) {
    return { ok: ok, status: ok ? 200 : 500, json: async function () { return body; }, text: async function () { return JSON.stringify(body); } };
  }

  function monthKeysBetween(from, to) {
    var keys = [];
    var y = Number(from.slice(0, 4)), m = Number(from.slice(5, 7));
    var ey = Number(to.slice(0, 4)), em = Number(to.slice(5, 7));
    while (y < ey || (y === ey && m <= em)) {
      keys.push(y + "-" + String(m).padStart(2, "0"));
      m += 1;
      if (m > 12) { m = 1; y += 1; }
    }
    return keys;
  }

  /**
   * 옛 셀 API 호출과 같은 모양으로 부른다.
   *   GET  /tables/steam_cell_value?month=YYYY-MM&tableNames=incinerator_contract[&computed=1]   (fromMonth/toMonth 도 가능)
   *   POST /tables/steam_cell_value            body { month, table_name, row_key, cell_value }
   *   PATCH /tables/steam_cell_value/{id}      body 같음
   * 소각로 표가 아닌 요청은 원래 fetch 로 넘긴다.
   */
  async function storeFetch(url, init) {
    var method = (init && init.method ? String(init.method) : "GET").toUpperCase();
    try {
      if (method === "GET") {
        var query = new URLSearchParams(String(url).split("?")[1] || "");
        var names = (query.get("tableNames") || "").split(",").map(function (s) { return s.trim(); }).filter(Boolean);
        if (!names.length || !names.every(function (n) { return TABLES.indexOf(n) >= 0; })) return global.fetch(url, init);
        var months = query.get("month") ? [query.get("month")] : monthKeysBetween(query.get("fromMonth") || "", query.get("toMonth") || "");
        var computed = query.get("computed") === "1";
        var jobs = [];
        names.forEach(function (name) { months.forEach(function (mk) { jobs.push(legacyRows(name, mk, computed)); }); });
        var lists = await Promise.all(jobs);
        return response(true, { data: [].concat.apply([], lists) });
      }
      var body = JSON.parse((init && init.body) || "{}");
      if (TABLES.indexOf(body.table_name) < 0) return global.fetch(url, init);
      return response(true, await saveCell(body.table_name, String(body.month), String(body.row_key), body.cell_value));
    } catch (e) {
      console.error("[IncineratorStore]", e);
      return response(false, { message: e && e.message ? e.message : String(e) });
    }
  }

  global.IncineratorStore = { fetch: storeFetch, legacyRows: legacyRows, saveCell: saveCell, clearCache: clearCache };
})(window);
