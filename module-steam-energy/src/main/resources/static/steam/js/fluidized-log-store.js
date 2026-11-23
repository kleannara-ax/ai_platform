/* 스팀에너지관리 — 유동상 운전일지 저장소 (묶음 4-C).
 *
 * 운전일지 화면은 옛 셀 저장소(/tables/steam_cell_value)에서 두 논리 표를 읽고 썼다:
 *   fluidized_daily_log    (월 키 YYYY-MM, 키 = 일, col_index = 항목 번호) → steam_fluidized_daily_log (/fluidized-daily-logs, 하루 한 행)
 *   fluidized_detail_main  (세부 운영내역과 같이 쓰는 칸, 키 = 셀 주소)   → fluidized-detail-store.js 가 맡는다
 * 여기서는 그 호출 모양을 그대로 받아 규격 표로 옮긴다 — 화면은 fetch 대신 FluidizedLogStore.fetch 를 부르기만 하면 된다.
 *
 * 항목 번호 → 필드 표는 서버의 service/table/FluidizedDailyLogFields.java 와 같아야 한다.
 *
 * 필요: steam-energy-api.js, steam-row-store.js, fluidized-detail-store.js
 */
(function (global) {
  "use strict";

  var RS = global.SteamRowStore;
  var Detail = global.FluidizedDetailStore;
  var LOG_TABLE = "fluidized_daily_log";
  var PATH = "fluidized-daily-logs";

  // 항목 번호(col_index) → 필드
  var FIELDS = {
    1: "workerAm", 2: "workerPm", 3: "workerNight", 4: "apprLeader", 5: "apprAssistant", 6: "apprManager", 20: "chemAmmoniumCarbonatePrev",
    23: "chemBoilerAgentPrev", 26: "chemActivatedCarbonPrev", 29: "chemCausticSodaPrev", 32: "chemSlakedLimePrev", 35: "chemSaltPrev",
    38: "chemDieselPrev", 41: "chemSrfPrev", 44: "chemSilicaSandPrev", 50: "ashFlyOut", 51: "ashBottomOut", 52: "incombustibleDay",
    53: "incombustibleNight", 60: "powerMainPrev", 61: "powerMainToday", 62: "powerAshSiloPrev", 63: "powerAshSiloToday", 64: "powerBagFilterPrev",
    65: "powerBagFilterToday", 66: "powerScrubberPrev", 67: "powerScrubberToday", 68: "powerCarbonFeedPrev", 69: "powerCarbonFeedToday",
    70: "powerLimeFeedPrev", 71: "powerLimeFeedToday", 72: "powerUreaFeedPrev", 73: "powerUreaFeedToday", 74: "powerFdPrev", 75: "powerFdToday",
    76: "powerCarbonBagPrev", 77: "powerCarbonBagToday", 78: "powerLimeBagPrev", 79: "powerLimeBagToday", 90: "utilReuseWaterPrev",
    92: "utilRiverWaterPrev", 94: "utilCondensatePrev", 96: "utilBoilerFeedPrev", 98: "utilWasteWaterPrev", 100: "utilSoftWaterPrev",
    200: "moistAm1Time", 201: "moistAm1Value", 202: "moistAm2Time", 203: "moistAm2Value", 204: "moistAm3Time", 205: "moistAm3Value",
    206: "moistAm4Time", 207: "moistAm4Value", 208: "moistAm5Time", 209: "moistAm5Value", 210: "moistPm1Time", 211: "moistPm1Value",
    212: "moistPm2Time", 213: "moistPm2Value", 214: "moistPm3Time", 215: "moistPm3Value", 216: "moistPm4Time", 217: "moistPm4Value",
    218: "moistPm5Time", 219: "moistPm5Value", 220: "moistNight1Time", 221: "moistNight1Value", 222: "moistNight2Time", 223: "moistNight2Value",
    224: "moistNight3Time", 225: "moistNight3Value", 226: "moistNight4Time", 227: "moistNight4Value", 228: "moistNight5Time",
    229: "moistNight5Value",
  };
  // 글자 그대로 두는 항목 (근무자 · 결재 · 함수율 취득시간)
  var TEXT_FIELDS = { workerAm: 1, workerPm: 1, workerNight: 1, apprLeader: 1, apprAssistant: 1, apprManager: 1, moistAm1Time: 1, moistAm2Time: 1, moistAm3Time: 1, moistAm4Time: 1, moistAm5Time: 1, moistPm1Time: 1, moistPm2Time: 1, moistPm3Time: 1, moistPm4Time: 1, moistPm5Time: 1, moistNight1Time: 1, moistNight2Time: 1, moistNight3Time: 1, moistNight4Time: 1, moistNight5Time: 1 };

  var store = RS.create({ path: PATH, keyFields: ["usageDate"], idField: "fluidizedLogId" });

  function ym(monthKey) {
    var m = /^(\d{4})-(\d{2})$/.exec(monthKey);
    return m ? { year: Number(m[1]), month: Number(m[2]) } : null;
  }

  async function rows(table, monthKey) {
    if (table === Detail.TABLE) {
      return (await Detail.rows(monthKey)).map(function (c) { return { rowKey: c.rowKey, col: 0, value: c.value }; });
    }
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
    if (table === Detail.TABLE) return col === 0 ? Detail.locate(monthKey, rowKey) : null;
    var p = ym(monthKey), field = FIELDS[col], day = Number(rowKey);
    if (table !== LOG_TABLE || !p || !field) return null;
    if (!(day >= 1 && day <= new Date(p.year, p.month, 0).getDate())) return null;
    return { store: store, scope: { year: p.year, month: p.month },
      key: { usageDate: global.SteamEnergyApi.isoDate(p.year, p.month, day) }, field: field, text: !!TEXT_FIELDS[field] };
  }

  var shim = RS.cellShim({ tables: [LOG_TABLE, Detail.TABLE], rows: rows, locate: locate });
  global.FluidizedLogStore = { fetch: shim.fetch, FIELDS: FIELDS };
})(window);
