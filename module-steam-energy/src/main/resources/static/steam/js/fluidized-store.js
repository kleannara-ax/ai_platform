/* 스팀에너지관리 — 유동상 운영 저장소 (묶음 3-D).
 *
 * 유동상 운영 화면들은 옛 셀 저장소(/tables/steam_cell_value)를 "월 키 + 표 이름 + 칸 키 + 칸 번호" 로 읽고 썼다.
 * 여기서는 그 호출 모양을 그대로 받아 규격 표(/steam-energy-api/fluidized-*)로 옮긴다 (서버의 FluidizedCellStore 와 같은 대응표).
 *
 *   fluidized_plan_actual    (YYYY-MM)  operating|power|waste|srf_income / col 0 → steam_fluidized_month 의 계획 4칸
 *   fluidized_plan_summary   (YYYY-MM)  monthly-summary / col 0                 → summaryRemark
 *   fluidized_year_note      (YYYY-01)  note-월 / col 0                         → 그 월의 monthNote
 *   fluidized_contract_cost  (YYYY-MM)  cost / col 0·1·2                        → laborCost · operatingPayCost · improvementPayCost
 *   fluidized_monthly_cache  (YYYY)     contractCost|operatingCost / col = 월   → contractCost · operatingCost (화면 계산값)
 *   fluidized_contract_daily (YYYY-MM)  일(두 자리) / col 0·2·4·5               → steam_fluidized_contract_daily
 *   fluidized_srf_inbound    (YYYY-MM)  entry:일 / col 1~10                     → steam_fluidized_srf_inbound_daily
 *                                       rate / col 1~10                         → steam_fluidized_srf_rate
 *
 * 월별 운영내역 화면용 도우미:
 *   FluidizedStore.monthInputs.load(year)                 → { "01": { improvementCost: "…", … }, … }  (값 없는 칸은 "")
 *   FluidizedStore.monthInputs.save(year, month, key, 값)  직접 입력 6개 중 하나를 저장
 *   FluidizedStore.saveMonthlyCache(year, records)        도급비·총 운영비(화면 계산값)를 달라진 달만 저장
 *
 * 필요: steam-energy-api.js, steam-row-store.js
 */
(function (global) {
  "use strict";

  var RS = global.SteamRowStore;
  var PLAN = "fluidized_plan_actual", SUMMARY = "fluidized_plan_summary", NOTE = "fluidized_year_note",
      COST = "fluidized_contract_cost", CACHE = "fluidized_monthly_cache",
      CONTRACT = "fluidized_contract_daily", SRF = "fluidized_srf_inbound";

  var PLAN_FIELDS = { operating: "planOperating", power: "planPower", waste: "planWaste", srf_income: "planSrfIncome" };
  var COST_FIELDS = { 0: "laborCost", 1: "operatingPayCost", 2: "improvementPayCost" };
  var CACHE_FIELDS = { contractCost: "contractCost", operatingCost: "operatingCost" };
  var CONTRACT_FIELDS = { 0: "steamTon", 2: "steamUnitPrice", 4: "stopHours", 5: "fixedCostPerHour" };
  var MONTH_INPUT_KEYS = ["improvementCost", "deductionCost", "extraPayment", "wastewaterQty", "soilQty", "soilCost"];
  var SLOTS = 10;

  function pad2(n) { return String(n).padStart(2, "0"); }
  function kgField(slot) { return "vendor" + pad2(slot) + "Kg"; }
  function rateField(slot) { return "vendor" + pad2(slot) + "Rate"; }
  function ym(monthKey) {
    var m = /^(\d{4})-(\d{2})$/.exec(monthKey);
    return m ? { year: Number(m[1]), month: Number(m[2]) } : null;
  }

  var stores = {
    month: RS.create({ path: "fluidized-months", keyFields: ["yearNo", "monthNo"], idField: "fluidizedMonthId" }),
    contract: RS.create({ path: "fluidized-contract-dailies", keyFields: ["usageDate"], idField: "fluidizedContractId" }),
    srfInbound: RS.create({ path: "fluidized-srf-inbound-dailies", keyFields: ["usageDate"], idField: "srfInboundId" }),
    srfRate: RS.create({ path: "fluidized-srf-rates", keyFields: ["yearNo", "monthNo"], idField: "srfRateId" }),
  };

  async function rows(table, monthKey) {
    var out = [];
    var Api = global.SteamEnergyApi;
    var p = ym(monthKey);
    if (table === CACHE) {
      if (!/^\d{4}$/.test(monthKey)) return out;
      (await RS.cachedGet("fluidized-months", Number(monthKey))).forEach(function (row) {
        Object.keys(CACHE_FIELDS).forEach(function (key) { out.push({ rowKey: key, col: row.monthNo, value: row[CACHE_FIELDS[key]] }); });
      });
      return out;
    }
    if (!p) return out;
    if (table === PLAN || table === SUMMARY || table === NOTE || table === COST) {
      (await RS.cachedGet("fluidized-months", p.year)).forEach(function (row) {
        // 월별 특이사항은 그 해 1월 키에 note-월 로 모아 준다
        if (table === NOTE) {
          if (p.month === 1) out.push({ rowKey: "note-" + pad2(row.monthNo), col: 0, value: row.monthNote });
          return;
        }
        if (row.monthNo !== p.month) return;
        if (table === PLAN) Object.keys(PLAN_FIELDS).forEach(function (key) { out.push({ rowKey: key, col: 0, value: row[PLAN_FIELDS[key]] }); });
        else if (table === SUMMARY) out.push({ rowKey: "monthly-summary", col: 0, value: row.summaryRemark });
        else Object.keys(COST_FIELDS).forEach(function (col) { out.push({ rowKey: "cost", col: Number(col), value: row[COST_FIELDS[col]] }); });
      });
    } else if (table === CONTRACT) {
      (await RS.cachedGet("fluidized-contract-dailies", p.year)).forEach(function (row) {
        if (Api.monthOf(row.usageDate) !== p.month) return;
        var day = pad2(Api.dayOf(row.usageDate));
        Object.keys(CONTRACT_FIELDS).forEach(function (col) { out.push({ rowKey: day, col: Number(col), value: row[CONTRACT_FIELDS[col]] }); });
      });
    } else if (table === SRF) {
      var pair = await Promise.all([RS.cachedGet("fluidized-srf-inbound-dailies", p.year), RS.cachedGet("fluidized-srf-rates", p.year)]);
      pair[0].forEach(function (row) {
        if (Api.monthOf(row.usageDate) !== p.month) return;
        var rowKey = "entry:" + pad2(Api.dayOf(row.usageDate));
        for (var slot = 1; slot <= SLOTS; slot += 1) out.push({ rowKey: rowKey, col: slot, value: row[kgField(slot)] });
      });
      pair[1].forEach(function (row) {
        if (row.monthNo !== p.month) return;
        for (var slot = 1; slot <= SLOTS; slot += 1) out.push({ rowKey: "rate", col: slot, value: row[rateField(slot)] });
      });
    }
    return out;
  }

  function monthTarget(year, month, field, text) {
    if (!field || month < 1 || month > 12) return null;
    return { store: stores.month, scope: { year: year }, key: { yearNo: year, monthNo: month }, field: field, text: !!text };
  }

  function locate(table, monthKey, rowKey, col) {
    var Api = global.SteamEnergyApi;
    var p = ym(monthKey), m;
    if (table === CACHE) {
      return /^\d{4}$/.test(monthKey) ? monthTarget(Number(monthKey), col, CACHE_FIELDS[rowKey]) : null;
    }
    if (!p) return null;
    if (table === PLAN) return col === 0 ? monthTarget(p.year, p.month, PLAN_FIELDS[rowKey]) : null;
    if (table === SUMMARY) return col === 0 && rowKey === "monthly-summary" ? monthTarget(p.year, p.month, "summaryRemark", true) : null;
    if (table === NOTE) {
      m = /^note-(\d{2})$/.exec(rowKey);
      return m && col === 0 ? monthTarget(p.year, Number(m[1]), "monthNote", true) : null;
    }
    if (table === COST) return rowKey === "cost" ? monthTarget(p.year, p.month, COST_FIELDS[col]) : null;
    var lastDay = new Date(p.year, p.month, 0).getDate();
    if (table === CONTRACT) {
      m = /^(\d{1,2})$/.exec(rowKey);
      if (!m || !CONTRACT_FIELDS[col] || Number(m[1]) < 1 || Number(m[1]) > lastDay) return null;
      return { store: stores.contract, scope: { year: p.year, month: p.month }, key: { usageDate: Api.isoDate(p.year, p.month, Number(m[1])) }, field: CONTRACT_FIELDS[col] };
    }
    if (table === SRF) {
      if (!(col >= 1 && col <= SLOTS)) return null;
      if (rowKey === "rate") return { store: stores.srfRate, scope: { year: p.year, month: p.month }, key: { yearNo: p.year, monthNo: p.month }, field: rateField(col) };
      m = /^entry:(\d{1,2})$/.exec(rowKey);
      if (!m || Number(m[1]) < 1 || Number(m[1]) > lastDay) return null;
      return { store: stores.srfInbound, scope: { year: p.year, month: p.month }, key: { usageDate: Api.isoDate(p.year, p.month, Number(m[1])) }, field: kgField(col) };
    }
    return null;
  }

  var shim = RS.cellShim({ tables: [PLAN, SUMMARY, NOTE, COST, CACHE, CONTRACT, SRF], rows: rows, locate: locate });

  // ── 월별 운영내역 화면: 직접 입력 6개 ──
  var monthInputs = {
    keys: MONTH_INPUT_KEYS,
    /** 그 해의 직접 입력값 — { "01": { improvementCost: "값", … }, … }. 앞서 보낸 저장이 끝난 뒤에 읽는다. */
    load: async function (year) {
      await RS.enqueue(async function () {});
      RS.clearCache();
      var out = {};
      (await RS.cachedGet("fluidized-months", Number(year))).forEach(function (row) {
        var values = {};
        MONTH_INPUT_KEYS.forEach(function (key) { values[key] = row[key] === null || row[key] === undefined ? "" : String(row[key]); });
        out[pad2(row.monthNo)] = values;
      });
      return out;
    },
    save: function (year, month, key, rawValue) {
      if (MONTH_INPUT_KEYS.indexOf(key) < 0) return Promise.resolve(null);
      var patch = {};
      patch[key] = global.SteamEnergyApi.num(rawValue);
      return stores.month.save({ yearNo: Number(year), monthNo: Number(month) }, patch, { year: Number(year) })
        .then(function (saved) { RS.clearCache(); return saved; });
    },
  };

  /**
   * 도급비·총 운영비(천원)를 달라진 달만 저장한다. records = [{ month: 1~12, contractCost, operatingCost }].
   * 화면을 그릴 때마다 불리므로, 저장된 값과 같으면 아무 요청도 보내지 않는다.
   */
  async function saveMonthlyCache(year, records) {
    var y = Number(year);
    var existing = {};
    (await RS.cachedGet("fluidized-months", y)).forEach(function (row) { existing[row.monthNo] = row; });
    var same = function (a, b) {
      var an = a === null || a === undefined ? null : Number(a), bn = b === null || b === undefined ? null : Number(b);
      if (an === null || bn === null) return an === bn;
      return Math.abs(an - bn) < 1e-6;
    };
    var jobs = [];
    (records || []).forEach(function (rec) {
      var month = Number(rec.month);
      if (!(month >= 1 && month <= 12)) return;
      var patch = {};
      var row = existing[month] || {};
      ["contractCost", "operatingCost"].forEach(function (key) {
        var value = Number.isFinite(rec[key]) ? rec[key] : null;
        if (value === null) return;                     // 계산할 수 없는 달은 저장된 값을 건드리지 않는다
        if (value === 0 && (row[key] === null || row[key] === undefined)) return;   // 자료가 없는 달의 0 은 저장하지 않는다
        if (!same(row[key], value)) patch[key] = value;
      });
      if (Object.keys(patch).length) jobs.push(stores.month.save({ yearNo: y, monthNo: month }, patch, { year: y }));
    });
    if (!jobs.length) return 0;
    await Promise.all(jobs);
    RS.clearCache();
    return jobs.length;
  }

  global.FluidizedStore = {
    fetch: shim.fetch, legacyRows: shim.legacyRows, saveCell: shim.saveCell,
    monthInputs: monthInputs, saveMonthlyCache: saveMonthlyCache,
  };
})(window);
