/* 스팀에너지관리 — 폐합성소각로 운전일지 저장소 (묶음 4-A).
 *
 * 운전일지 화면은 옛 셀 저장소(/tables/steam_cell_value)를 "표 이름 waste_incin_log, 월 키 YYYY-MM, 키 = 일, col_index = 항목 번호" 로
 * 읽고 썼다. 여기서는 그 호출 모양을 그대로 받아 규격 표 steam_waste_incin_daily_log (하루 한 행, 항목 = 컬럼)로 옮긴다 —
 * 화면은 fetch 대신 WasteLogStore.fetch 를 부르기만 하면 된다.
 *
 * 항목 번호 → 필드 표는 서버의 service/table/WasteIncinLogFields.java 와 같아야 한다.
 *
 * 필요: steam-energy-api.js, steam-row-store.js
 */
(function (global) {
  "use strict";

  var RS = global.SteamRowStore;
  var LOG_TABLE = "waste_incin_log";
  var PATH = "waste-incin-daily-logs";

  // 항목 번호(col_index) → 필드
  var FIELDS = {
    1: "burnU1Self", 2: "burnU1Recycle", 3: "burnU1Generated", 4: "burnU1RunHours", 5: "burnU1ExtStock", 11: "burnU2Self", 12: "burnU2Recycle",
    13: "burnU2Generated", 14: "burnU2RunHours", 15: "burnU2SelfStock", 21: "burnExtTotal", 100: "steamU1", 101: "steamU2First", 102: "steamU2Second",
    103: "steamAgency", 104: "steamPressure", 200: "waterU1First", 201: "waterU1Second", 202: "waterU2First", 203: "waterU2Second", 300: "powerMeter",
    301: "powerU1", 302: "powerU2", 303: "powerEp", 400: "flyAshU1", 401: "flyAshU2", 402: "bottomAshU1", 403: "bottomAshU2", 500: "ureaIn",
    501: "ureaUse", 502: "ureaStock", 503: "boilerChemIn", 504: "boilerChemUse", 505: "boilerChemStock", 506: "saltIn", 507: "saltUse",
    508: "saltFluidized", 509: "saltStock", 510: "causticIn", 511: "causticUse", 512: "causticStock", 513: "carbonIn", 514: "carbonUse",
    515: "carbonStock", 516: "oilIn", 517: "oilU1", 518: "oilU2", 519: "oilSludge1", 520: "oilSludge2", 521: "oilStock", 522: "clinkerSideWall",
    523: "clinkerDrop", 524: "clinkerIn", 525: "clinkerUse", 526: "clinkerStock", 600: "tmsU1Dust", 601: "tmsU1Co", 602: "tmsU1Nox", 603: "tmsU1Sox",
    604: "tmsU1Hcl", 605: "tmsU1Temp", 606: "tmsU2Dust", 607: "tmsU2Co", 608: "tmsU2Nox", 609: "tmsU2Sox", 610: "tmsU2Hcl", 611: "tmsU2Temp",
    700: "inboundVendor01", 701: "inboundVendor02", 702: "inboundVendor03", 703: "inboundVendor04", 704: "inboundVendor05", 705: "inboundVendor06",
    706: "inboundVendor07", 707: "inboundVendor08", 708: "inboundVendor09", 709: "inboundVendor10", 710: "inboundVendor11", 711: "inboundVendor12",
    712: "inboundVendor13", 713: "inboundVendor14", 800: "genWood", 801: "genSaengbul", 802: "genPlastic", 803: "genReject", 804: "genRagger",
    805: "genStock",
  };
  var TEXT_FIELDS = { steamPressure: 1 };

  var store = RS.create({ path: PATH, keyFields: ["usageDate"], idField: "wasteIncinLogId" });

  function ym(monthKey) {
    var m = /^(\d{4})-(\d{2})$/.exec(monthKey);
    return m ? { year: Number(m[1]), month: Number(m[2]) } : null;
  }

  async function rows(table, monthKey) {
    var p = ym(monthKey), out = [];
    if (table !== LOG_TABLE || !p) return out;
    var Api = global.SteamEnergyApi;
    (await RS.cachedGet(PATH, p.year, p.month)).forEach(function (row) {
      var day = String(Api.dayOf(row.usageDate));
      Object.keys(FIELDS).forEach(function (col) { out.push({ rowKey: day, col: Number(col), value: row[FIELDS[col]] }); });
    });
    return out;
  }

  function locate(table, monthKey, rowKey, col) {
    var p = ym(monthKey), field = FIELDS[col], day = Number(rowKey);
    if (table !== LOG_TABLE || !p || !field) return null;
    if (!(day >= 1 && day <= new Date(p.year, p.month, 0).getDate())) return null;
    return { store: store, scope: { year: p.year, month: p.month },
      key: { usageDate: global.SteamEnergyApi.isoDate(p.year, p.month, day) }, field: field, text: !!TEXT_FIELDS[field] };
  }

  var shim = RS.cellShim({ tables: [LOG_TABLE], rows: rows, locate: locate });
  global.WasteLogStore = { fetch: shim.fetch, FIELDS: FIELDS };
})(window);
