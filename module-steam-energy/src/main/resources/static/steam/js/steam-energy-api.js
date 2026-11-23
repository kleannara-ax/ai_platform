/* 스팀에너지관리 — 규격 API(/steam-energy-api/**) 호출 공통.
 *
 * 응답은 core 의 ApiResponse { success, code, message, data } 로 감싸져 온다. 여기서 data 만 꺼내 주고,
 * 실패(HTTP 오류 또는 success=false)는 message 를 담은 Error 로 던진다.
 * 토큰 헤더와 401 재발급은 platform-iframe-compat.js 가 fetch 를 가로채 처리한다.
 */
(function (global) {
  "use strict";

  var BASE = "/steam-energy-api";

  async function call(method, path, body) {
    var init = { method: method, headers: { "Accept": "application/json" } };
    if (body !== undefined) {
      init.headers["Content-Type"] = "application/json";
      init.body = JSON.stringify(body);
    }
    var response = await fetch(BASE + path, init);
    var json = null;
    try { json = await response.json(); } catch (e) { json = null; }
    if (!response.ok || !json || json.success === false) {
      var message = (json && json.message) ? json.message : ("HTTP " + response.status);
      var error = new Error(message);
      error.status = response.status;
      error.body = json;
      throw error;
    }
    return json.data;
  }

  function pad2(n) { return String(n).padStart(2, "0"); }

  /** (연, 월, 일) → 'YYYY-MM-DD' */
  function isoDate(year, month, day) {
    return String(year) + "-" + pad2(month) + "-" + pad2(day);
  }

  /** 'YYYY-MM-DD' → 일(숫자) */
  function dayOf(isoDateText) {
    return Number(String(isoDateText || "").slice(8, 10));
  }

  /** 'YYYY-MM-DD' → 월(숫자) */
  function monthOf(isoDateText) {
    return Number(String(isoDateText || "").slice(5, 7));
  }

  /** 숫자 입력값 → BigDecimal 로 보낼 값. 빈 값은 null. 쉼표·공백 제거. */
  function num(value) {
    if (value === null || value === undefined) return null;
    var text = String(value).replace(/[,\s]/g, "");
    if (text === "" || text === "-") return null;
    var n = Number(text);
    return Number.isFinite(n) ? n : null;
  }

  /** 행 목록 → Map(키 → 행). keyFn 기본: usageDate */
  function indexBy(rows, keyFn) {
    var map = new Map();
    (rows || []).forEach(function (row) { map.set(keyFn ? keyFn(row) : row.usageDate, row); });
    return map;
  }

  global.SteamEnergyApi = {
    get: function (path) { return call("GET", path); },
    post: function (path, body) { return call("POST", path, body); },
    put: function (path, body) { return call("PUT", path, body); },
    del: function (path) { return call("DELETE", path); },
    isoDate: isoDate,
    dayOf: dayOf,
    monthOf: monthOf,
    num: num,
    indexBy: indexBy,
  };
})(window);
