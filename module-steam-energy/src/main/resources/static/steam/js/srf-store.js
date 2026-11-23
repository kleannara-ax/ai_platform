/* 스팀에너지관리 — SRF 보일러 인보이스 저장소 (묶음 3-C).
 *
 * SRF Boiler invoice 화면들은 옛 셀 저장소(/tables/steam_cell_value)를 "월 키 + 표 이름 + 칸 키" 로 읽고 썼다.
 * 여기서는 그 호출 모양을 그대로 받아 규격 표(/steam-energy-api/srf-invoice-*)로 옮긴다 —
 * 화면은 fetch 대신 SrfStore.fetch 를 부르기만 하면 된다.
 *
 *   srf_boiler_invoice_summary      (YYYY-MM)  summary-vk-r{엑셀 행} / col 4·6      → steam_srf_invoice_summary
 *   srf_boiler_invoice_client       (YYYY-MM)  {구분}|{줄}|{qty,price,note,label,unit} → steam_srf_invoice_client_item
 *   srf_boiler_invoice_operation    (YYYY-MM)  operation-r{일+4} / col 12 (전력량)     → steam_srf_invoice_operation_daily
 *   srf_boiler_invoice_monthly      (YYYY = 6월이 속한 해)  {steam,ops,etc}|{칸}|{항목} → steam_srf_invoice_monthly   (실제 연/월)
 *   srf_boiler_invoice_accumulated  (YYYY = 6월이 속한 해)  {칸}|{contract,bsc,asc}     → steam_srf_invoice_accumulated (실제 연/월)
 *   srf_boiler_invoice_bsc          (YYYY)     rate                                    → steam_srf_invoice_bsc
 *   srf_boiler_invoice_adjustment   (YYYY)     {표}|{항목}|{coef,base,present}          → steam_srf_invoice_adjustment
 *
 * 칸 번호(회계연도): 0~6 → 그 해 6~12월, 7~11 → 다음 해 1~5월.
 *
 * 필요: steam-energy-api.js, steam-row-store.js
 */
(function (global) {
  "use strict";

  var RS = global.SteamRowStore;
  var SUMMARY = "srf_boiler_invoice_summary", CLIENT = "srf_boiler_invoice_client", OPERATION = "srf_boiler_invoice_operation",
      MONTHLY = "srf_boiler_invoice_monthly", ACCUMULATED = "srf_boiler_invoice_accumulated",
      BSC = "srf_boiler_invoice_bsc", ADJUSTMENT = "srf_boiler_invoice_adjustment";

  // Summary(VK): [엑셀 행, col_index, 필드]
  var SUMMARY_ITEMS = [
    [16, 4, "bscRate"], [18, 4, "bscFactor"], [20, 4, "afbscrBsc"], [22, 4, "bscSteamAmount"],
    [27, 4, "ascRate"], [29, 4, "ascFactor"], [31, 4, "ascSteamAmount"],
    [34, 4, "others0"], [36, 4, "others1"], [37, 4, "others2"], [38, 4, "others3"],
    [44, 6, "payableAdjust"], [51, 4, "liquidatedDamage"], [53, 4, "ascRefundAnnual"],
    [55, 4, "utilitiesDeduction"], [57, 4, "otherDeduction1"], [59, 4, "otherDeduction2"],
  ];
  var CLIENT_FIELDS = { qty: "qty", price: "unitPrice", note: "note", label: "itemLabel", unit: "unitLabel" };
  var CLIENT_TEXT = { note: 1, itemLabel: 1, unitLabel: 1 };
  var MONTHLY_FIELDS = {
    "steam|steam-qty": "steamQty", "steam|steam-unit": "steamUnit", "steam|power-qty": "powerQty", "steam|power-unit": "powerUnit",
    "steam|lng-qty": "lngQty", "steam|lng-unit": "lngUnit",
    "ops|process-qty": "processQty", "ops|process-unit": "processUnit", "ops|soft-qty": "softQty", "ops|soft-unit": "softUnit",
    "ops|soften-qty": "softenQty", "ops|soften-unit": "softenUnit", "ops|life-qty": "lifeQty", "ops|life-unit": "lifeUnit",
    "etc|special": "etcSpecial", "etc|bsc7": "etcBsc7", "etc|srfShare": "etcSrfShare", "etc|inspect": "etcInspect",
    "etc|over7": "etcOver7", "etc|airOver": "etcAirOver", "etc|waste": "etcWaste",
  };
  var ACC_FIELDS = { contract: "contractVolume", bsc: "bscVolume", asc: "ascVolume" };
  var ADJ_FIELDS = { coef: "coefficient", base: "baseValue", present: "presentValue" };

  var stores = {
    summary: RS.create({ path: "srf-invoice-summaries", keyFields: ["yearNo", "monthNo"], idField: "invoiceSummaryId" }),
    client: RS.create({ path: "srf-invoice-client-items", keyFields: ["yearNo", "monthNo", "section", "lineNo"], idField: "clientItemId" }),
    operation: RS.create({ path: "srf-invoice-operation-dailies", keyFields: ["usageDate"], idField: "invoiceOperationId" }),
    monthly: RS.create({ path: "srf-invoice-monthlies", keyFields: ["yearNo", "monthNo"], idField: "invoiceMonthlyId" }),
    accumulated: RS.create({ path: "srf-invoice-accumulateds", keyFields: ["yearNo", "monthNo"], idField: "invoiceAccumulatedId" }),
    bsc: RS.create({ path: "srf-invoice-bscs", keyFields: ["yearNo"], idField: "invoiceBscId" }),
    adjustment: RS.create({ path: "srf-invoice-adjustments", keyFields: ["yearNo", "section", "itemCode"], idField: "invoiceAdjustmentId" }),
  };

  function ym(monthKey) {
    var m = /^(\d{4})-(\d{2})$/.exec(monthKey);
    return m ? { year: Number(m[1]), month: Number(m[2]) } : null;
  }
  function yearOf(monthKey) { return /^\d{4}$/.test(monthKey) ? Number(monthKey) : null; }

  /** 회계연도(6월 시작) 칸 번호 ↔ 실제 연/월 */
  function fiscalToCalendar(fiscalYear, idx) {
    return idx <= 6 ? { year: fiscalYear, month: idx + 6 } : { year: fiscalYear + 1, month: idx - 6 };
  }
  function calendarToFiscalIdx(fiscalYear, year, month) {
    if (year === fiscalYear && month >= 6) return month - 6;
    if (year === fiscalYear + 1 && month <= 5) return month + 6;
    return -1;
  }
  async function fiscalRows(path, fiscalYear) {
    var pair = await Promise.all([RS.cachedGet(path, fiscalYear), RS.cachedGet(path, fiscalYear + 1)]);
    return pair[0].concat(pair[1]);
  }

  async function rows(table, monthKey) {
    var out = [];
    var p = ym(monthKey), y = yearOf(monthKey);
    var Api = global.SteamEnergyApi;
    if (table === SUMMARY && p) {
      (await RS.cachedGet("srf-invoice-summaries", p.year)).forEach(function (row) {
        if (row.monthNo !== p.month) return;
        SUMMARY_ITEMS.forEach(function (item) { out.push({ rowKey: "summary-vk-r" + item[0], col: item[1], value: row[item[2]] }); });
      });
    } else if (table === CLIENT && p) {
      (await RS.cachedGet("srf-invoice-client-items", p.year)).forEach(function (row) {
        if (row.monthNo !== p.month) return;
        Object.keys(CLIENT_FIELDS).forEach(function (f) {
          out.push({ rowKey: row.section + "|" + row.lineNo + "|" + f, col: 1, value: row[CLIENT_FIELDS[f]] });
        });
      });
    } else if (table === OPERATION && p) {
      (await RS.cachedGet("srf-invoice-operation-dailies", p.year, p.month)).forEach(function (row) {
        out.push({ rowKey: "operation-r" + (Api.dayOf(row.usageDate) + 4), col: 12, value: row.powerKwh });
      });
    } else if (table === MONTHLY && y) {
      (await fiscalRows("srf-invoice-monthlies", y)).forEach(function (row) {
        var idx = calendarToFiscalIdx(y, row.yearNo, row.monthNo);
        if (idx < 0) return;
        Object.keys(MONTHLY_FIELDS).forEach(function (key) {
          var parts = key.split("|");
          out.push({ rowKey: parts[0] + "|" + idx + "|" + parts[1], col: 1, value: row[MONTHLY_FIELDS[key]] });
        });
      });
    } else if (table === ACCUMULATED && y) {
      (await fiscalRows("srf-invoice-accumulateds", y)).forEach(function (row) {
        var idx = calendarToFiscalIdx(y, row.yearNo, row.monthNo);
        if (idx < 0) return;
        Object.keys(ACC_FIELDS).forEach(function (f) { out.push({ rowKey: idx + "|" + f, col: 1, value: row[ACC_FIELDS[f]] }); });
      });
    } else if (table === BSC && y) {
      (await RS.cachedGet("srf-invoice-bscs", y)).forEach(function (row) { out.push({ rowKey: "rate", col: 1, value: row.bscRate }); });
    } else if (table === ADJUSTMENT && y) {
      (await RS.cachedGet("srf-invoice-adjustments", y)).forEach(function (row) {
        Object.keys(ADJ_FIELDS).forEach(function (f) {
          out.push({ rowKey: row.section + "|" + row.itemCode + "|" + f, col: 1, value: row[ADJ_FIELDS[f]] });
        });
      });
    }
    return out;
  }

  function locate(table, monthKey, rowKey, col) {
    var p = ym(monthKey), y = yearOf(monthKey), m, i;
    var Api = global.SteamEnergyApi;
    if (table === SUMMARY && p) {
      for (i = 0; i < SUMMARY_ITEMS.length; i += 1) {
        if (rowKey === "summary-vk-r" + SUMMARY_ITEMS[i][0] && col === SUMMARY_ITEMS[i][1]) {
          return { store: stores.summary, scope: { year: p.year }, key: { yearNo: p.year, monthNo: p.month }, field: SUMMARY_ITEMS[i][2] };
        }
      }
      return null;
    }
    if (table === CLIENT && p) {
      m = /^(utilities|other-out|other-in)\|(\d+)\|(qty|price|note|label|unit)$/.exec(rowKey);
      if (!m) return null;
      var cf = CLIENT_FIELDS[m[3]];
      return { store: stores.client, scope: { year: p.year, month: p.month },
        key: { yearNo: p.year, monthNo: p.month, section: m[1], lineNo: Number(m[2]) }, field: cf, text: !!CLIENT_TEXT[cf] };
    }
    if (table === OPERATION && p) {
      m = /^operation-r(\d+)$/.exec(rowKey);
      if (!m || col !== 12) return null;
      var day = Number(m[1]) - 4;
      if (day < 1 || day > new Date(p.year, p.month, 0).getDate()) return null;
      return { store: stores.operation, scope: { year: p.year, month: p.month }, key: { usageDate: Api.isoDate(p.year, p.month, day) }, field: "powerKwh" };
    }
    if (table === MONTHLY && y) {
      m = /^(steam|ops|etc)\|(\d+)\|(.+)$/.exec(rowKey);
      if (!m) return null;
      var mf = MONTHLY_FIELDS[m[1] + "|" + m[3]];
      if (!mf || Number(m[2]) > 11) return null;
      var c1 = fiscalToCalendar(y, Number(m[2]));
      return { store: stores.monthly, scope: { year: c1.year }, key: { yearNo: c1.year, monthNo: c1.month }, field: mf };
    }
    if (table === ACCUMULATED && y) {
      m = /^(\d+)\|(contract|bsc|asc)$/.exec(rowKey);
      if (!m || Number(m[1]) > 11) return null;
      var c2 = fiscalToCalendar(y, Number(m[1]));
      return { store: stores.accumulated, scope: { year: c2.year }, key: { yearNo: c2.year, monthNo: c2.month }, field: ACC_FIELDS[m[2]] };
    }
    if (table === BSC && y) {
      return rowKey === "rate" ? { store: stores.bsc, scope: { year: y }, key: { yearNo: y }, field: "bscRate" } : null;
    }
    if (table === ADJUSTMENT && y) {
      m = /^(afbsc|afbsc-rebase|afasc)\|([a-z-]+)\|(coef|base|present)$/.exec(rowKey);
      if (!m) return null;
      return { store: stores.adjustment, scope: { year: y }, key: { yearNo: y, section: m[1], itemCode: m[2] }, field: ADJ_FIELDS[m[3]] };
    }
    return null;
  }

  global.SrfStore = RS.cellShim({
    tables: [SUMMARY, CLIENT, OPERATION, MONTHLY, ACCUMULATED, BSC, ADJUSTMENT],
    rows: rows,
    locate: locate,
  });
})(window);
