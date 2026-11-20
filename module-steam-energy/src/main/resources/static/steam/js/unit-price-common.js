/*
 * 설비별 단가(연/월별) 공용 모듈.
 *  - 등록 단가(단가 입력 페이지)는 규격 표 steam_price 에 저장한다: GET /steam-energy-api/prices/year/{year},
 *    POST /steam-energy-api/prices (연·월·항목코드 업서트). 2026-11 규격 전환.
 *  - 단가 연동(applyPriceLink): 단가를 고치면 그 달 전체 일자에 일괄 적용한다.
 *      · 전환된 일별 표(LNG·스팀 구매 일일 사용량)는 서버가 한다: POST /steam-energy-api/prices/apply-daily
 *      · 아직 전환되지 않은 화면(세부 운영내역·도급내역·Invoice Summary)은 옛 셀 저장소에 칸 단위로 쓴다.
 *  - [옛 경로] ITEMS / fetchRegistered / saveRegistered / applyToDaily / wireApplyButton 은 유동상·소각로·복합 일일 사용량
 *    화면이 아직 옛 셀 저장소를 쓰는 동안만 남긴다 (그 화면 전환 때 제거).
 */
(function (global) {
  "use strict";

  var TABLES_BASE = "/tables";
  var CELL_TABLE = "steam_cell_value";
  var REGISTRY_TABLE = "unit_price";

  // 연료 단가 6종. table/col = daily 그리드 셀 위치, todayInput = today-entry 입력 id.
  var ITEMS = [
    { code: "lng_boiler",       label: "LNG 보일러 단가",   unit: "원/N㎥", table: "lng_m_boiler",             col: 5, todayInput: "lngBoilerPrice" },
    { code: "lng_burner",       label: "LNG 버너 단가",     unit: "원/N㎥", table: "lng_m_burner",             col: 6, todayInput: "lngBurnerPrice" },
    { code: "flow_fluid",       label: "유동상 단가",        unit: "원/톤",  table: "flow_m_fluid_incinerator", col: 1, todayInput: "flowFluidPrice" },
    { code: "flow_incinerator", label: "폐합성소각로 단가",  unit: "원/톤",  table: "flow_m_fluid_incinerator", col: 4, todayInput: "flowIncineratorPrice" },
    { code: "flow_combined",    label: "복합 단가",          unit: "원/톤",  table: "flow_m_combined",          col: 1, todayInput: "flowCombinedPrice" },
    { code: "flow_boiler",      label: "외부보일러 단가",    unit: "원/톤",  table: "flow_m_boiler",            col: 1, todayInput: "flowBoilerPrice" },
  ];

  function pad2(n) { n = Number(n); return (n < 10 ? "0" : "") + n; }
  function daysInMonth(year, month) { return new Date(Number(year), Number(month), 0).getDate(); }

  // [옛 경로] 셀 저장소의 등록 단가 조회 → { itemCode: { monthNo: "value" } } — 유동상·소각로·복합 일일 사용량 전환 때 제거.
  function fetchRegistered(year, table) {
    var tbl = table || REGISTRY_TABLE;
    var url = TABLES_BASE + "/" + CELL_TABLE + "?page=1&limit=100000&month=" +
      encodeURIComponent(String(year)) + "&tableNames=" + tbl;
    return fetch(url)
      .then(function (r) { return r.ok ? r.json() : { data: [] }; })
      .then(function (j) {
        var map = {};
        (j.data || []).forEach(function (row) {
          if (row.table_name !== tbl) return;
          var code = row.row_key;
          var m = Number(row.col_index);
          (map[code] = map[code] || {})[m] = row.cell_value;
        });
        return map;
      });
  }

  // [옛 경로] 셀 저장소에 등록 단가 저장 — 위와 같이 제거 예정.
  function saveRegistered(year, code, monthNo, value, table) {
    return fetch(TABLES_BASE + "/" + CELL_TABLE, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        month: String(year), year_no: Number(year), month_no: Number(monthNo),
        table_name: table || REGISTRY_TABLE, row_key: code, col_index: Number(monthNo),
        cell_value: (value == null ? "" : String(value).replace(/[,\s]/g, "")),
      }),
    }).then(function (r) { if (!r.ok) throw new Error("저장 실패"); return r.json(); });
  }

  // ── 규격 API (steam_price) ──────────────────────────────────────
  function api() {
    if (!global.SteamEnergyApi) throw new Error("steam-energy-api.js 가 로드되지 않았습니다.");
    return global.SteamEnergyApi;
  }

  // 등록 단가 조회 (해당 연도 전체) → { itemCode: { monthNo: priceValue } }  (값이 비면 칸 없음)
  function fetchPrices(year) {
    return api().get("/prices/year/" + encodeURIComponent(String(year))).then(function (rows) {
      var map = {};
      (rows || []).forEach(function (row) {
        if (row.priceValue == null) return;
        (map[row.itemCode] = map[row.itemCode] || {})[Number(row.monthNo)] = row.priceValue;
      });
      return map;
    });
  }

  // 등록 단가 저장(업서트). meta = { facility, label, unit } — 항목의 시설·이름·단위(표에 같이 저장).
  function savePrice(year, code, monthNo, value, meta) {
    var clean = value == null ? "" : String(value).replace(/[,\s]/g, "");
    var n = clean === "" ? null : Number(clean);
    if (clean !== "" && !Number.isFinite(n)) return Promise.reject(new Error("숫자가 아닙니다: " + value));
    meta = meta || {};
    return api().post("/prices", {
      yearNo: Number(year), monthNo: Number(monthNo), itemCode: code,
      facility: meta.facility || "-", itemName: meta.label || code, unit: meta.unit || null,
      inputType: "DIRECT", priceValue: n,
    });
  }

  // 등록 단가를 그 달 일별 표(LNG·스팀 구매 일일 사용량)에 서버가 일괄 적용 → { appliedDays, hasDailyTarget }
  function applyPriceDaily(year, month, code) {
    return api().post("/prices/apply-daily", { yearNo: Number(year), monthNo: Number(month), itemCode: code });
  }

  function registeredValue(reg, code, monthNo) {
    var v = (reg && reg[code]) ? reg[code][Number(monthNo)] : undefined;
    return (v == null || v === "") ? null : v;
  }

  // daily 그리드: (연,월)의 등록 단가를 그 달 전체 일자 단가 셀에 일괄 upsert.
  //  items: ITEMS 부분집합. 반환: { applied: 적용항목수, skipped: 미등록항목 [label...] }
  function applyToDaily(items, year, month, reg) {
    var ym = String(year) + "-" + pad2(month);
    var days = daysInMonth(year, month);
    var tasks = [];
    var applied = 0;
    var skipped = [];
    items.forEach(function (item) {
      var v = registeredValue(reg, item.code, month);
      if (v == null) { skipped.push(item.label); return; }
      applied += 1;
      for (var d = 1; d <= days; d++) {
        tasks.push(fetch(TABLES_BASE + "/" + CELL_TABLE, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            month: ym, year_no: Number(year), month_no: Number(month),
            table_name: item.table, row_key: pad2(d), col_index: item.col,
            cell_value: String(v),
          }),
        }));
      }
    });
    return Promise.all(tasks).then(function () { return { applied: applied, skipped: skipped }; });
  }

  // ── 단가 연동 ─────────────────────────────────────────────────────
  //  단가 입력 페이지에서 단가를 고칠 때만 그 달 전체 일자에 일괄 적용한다.
  //  이후 사용자가 대상 페이지에서 특정 일자를 직접 고치면 그 값은 그대로 둔다
  //  (여기서는 단가를 다시 고치기 전까지 아무것도 덮어쓰지 않는다).
  //
  //  daily = 전환된 일별 표(steam_lng_daily_usage·steam_fuel_daily_usage)에 서버가 넣는다 (apply-daily).
  //   - lng_unit → LNG 보일러·버너 단가, fluid_steam → 유동상 단가, waste2_steam → 폐합성 단가
  //  targets = 아직 옛 셀 저장소를 쓰는 화면의 칸들. key(일) 은 그 페이지가 쓰는 row_key 형식이다.
  //   - 유동상 세부 운영내역: fluidized_detail_main, row_key = 일자열(일+2) + 행번호, col 0
  //   - 폐합성 도급내역     : incinerator_contract, row_key "섹션|일|필드", col 1
  //
  //  monthTargets 는 일자별이 아니라 그 달에 한 칸만 있는 자리다(Invoice Summary(VK)).
  //  row_key 는 인보이스 화면과 같은 "summary-vk-r{행번호}", col 은 col_index(=열+1) 그대로다.
  var SUMMARY_TABLE = "srf_boiler_invoice_summary";
  var PRICE_LINKS = [
    // daily: true = 전환된 일별 표에 서버(apply-daily)가 넣는 항목. targets 는 아직 옛 셀 저장소를 쓰는 화면.
    { code: "lng_unit", label: "LNG 연료 사용 비용 단가(보일러·버너)", daily: true },
    // 복합보일러 BSC·ASC → Invoice Summary(VK) 의 단가 칸(Won/ton steam)과 ESCALATION FACTOR 칸.
    //   1. BSC = (ROUND(BSC 단가 x BSC FACTOR) + AFBSCr) x STEAM SUPPLY AMOUNT   (원본 D14)
    //   2. ASC = ROUND(ASC 단가 x ASC FACTOR) x STEAM SUPPLY AMOUNT              (원본 D25)
    { code: "comb_bsc", label: "Invoice Summary(VK) BASE SERVICE CHARGE", monthTargets: [
      { table: SUMMARY_TABLE, rowKey: "summary-vk-r16", col: 4 },
    ] },
    { code: "comb_bsc_factor", label: "Invoice Summary(VK) BSC ESCALATION FACTOR", monthTargets: [
      { table: SUMMARY_TABLE, rowKey: "summary-vk-r18", col: 4 },
    ] },
    { code: "comb_bsc_guarantee", label: "Invoice Summary(VK) BSC STEAM SUPPLY AMOUNT", monthTargets: [
      { table: SUMMARY_TABLE, rowKey: "summary-vk-r22", col: 4 },
    ] },
    { code: "comb_asc", label: "Invoice Summary(VK) ADDITIONAL SERVICE CHARGE", monthTargets: [
      { table: SUMMARY_TABLE, rowKey: "summary-vk-r27", col: 4 },
    ] },
    { code: "comb_asc_factor", label: "Invoice Summary(VK) ASC ESCALATION FACTOR", monthTargets: [
      { table: SUMMARY_TABLE, rowKey: "summary-vk-r29", col: 4 },
    ] },
    { code: "fluid_steam", label: "유동상 단가 · 세부 운영내역 KNE 스팀단가", daily: true, targets: [
      { table: "fluidized_detail_main", col: 0, key: function (d) { return columnLabel(d + 2) + "11"; } },
    ] },
    { code: "fluid_fixed_hr", label: "세부 운영내역 KNE 운휴시간 단가", targets: [
      { table: "fluidized_detail_main", col: 0, key: function (d) { return columnLabel(d + 2) + "13"; } },
    ] },
    // 폐합성 운영단가는 도급내역의 결산 기준 표와 실 스팀량 기준 표에 같은 값으로 들어간다.
    // (1호기 스팀단가는 단가 입력에서 뺐다 — 도급내역 1호기 운영단가는 그 페이지에서 직접 넣는다)
    { code: "waste2_steam", label: "폐합성소각로 단가 · 도급내역 2호기 운영단가", daily: true, targets: [
      { table: "incinerator_contract", col: 1, key: function (d) { return "unit2|" + d + "|unit"; } },
      { table: "incinerator_contract", col: 1, key: function (d) { return "real|" + d + "|unit-2"; } },
    ] },
    // 고정단가 → 운휴시간당 고정비(원/시간). 결산 기준 표에만 있다.
    { code: "waste1_fixed_hr", label: "도급내역 1호기 운휴시간당 고정비", targets: [
      { table: "incinerator_contract", col: 1, key: function (d) { return "unit1|" + d + "|fixed"; } },
    ] },
    { code: "waste2_fixed_hr", label: "도급내역 2호기 운휴시간당 고정비", targets: [
      { table: "incinerator_contract", col: 1, key: function (d) { return "unit2|" + d + "|fixed"; } },
    ] },
  ];

  function priceLink(code) {
    for (var i = 0; i < PRICE_LINKS.length; i++) {
      if (PRICE_LINKS[i].code === code) return PRICE_LINKS[i];
    }
    return null;
  }

  /** 1→A, 2→B … 세부 표의 일자 열은 (일 + 2) 번째 열이다. */
  function columnLabel(index) {
    var label = "";
    var remaining = Number(index);
    while (remaining > 0) {
      var mod = (remaining - 1) % 26;
      label = String.fromCharCode(65 + mod) + label;
      remaining = Math.floor((remaining - 1) / 26);
    }
    return label;
  }

  /** 동시 요청 수를 제한해 순차에 가깝게 실행 (일자 수 × 월 수 만큼 POST 가 생기므로) */
  function runLimited(tasks, limit) {
    var index = 0;
    function next() {
      if (index >= tasks.length) return Promise.resolve();
      return tasks[index++]().then(next);
    }
    var runners = [];
    for (var i = 0; i < Math.min(limit || 6, tasks.length); i++) runners.push(next());
    return Promise.all(runners);
  }

  /**
   * 단가 1건을 연동 대상 페이지의 해당 월 전체 일자에 적용.
   * 연동 대상이 아니거나 값이 비었으면 아무것도 하지 않는다.
   * @returns Promise<{ applied: 일수, cells: 저장 칸 수, label: string }>
   */
  function applyPriceLink(code, year, month, value) {
    var link = priceLink(code);
    var clean = value == null ? "" : String(value).replace(/[,\s]/g, "");
    if (!link || clean === "") return Promise.resolve({ applied: 0, cells: 0, label: link ? link.label : "" });

    var ym = String(year) + "-" + pad2(month);
    var days = daysInMonth(year, month);
    var tasks = [];
    // 전환된 일별 표는 서버가 한 번에 넣는다 (등록 단가는 이미 저장된 상태여야 한다).
    var dailyStep = link.daily
      ? applyPriceDaily(year, month, code).then(function (res) { return res && res.appliedDays ? res.appliedDays : 0; })
      : Promise.resolve(0);
    function post(target, rowKey) {
      return function () {
        return fetch(TABLES_BASE + "/" + CELL_TABLE, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({
            month: ym, year_no: Number(year), month_no: Number(month),
            table_name: target.table,
            row_key: rowKey,
            col_index: target.col,
            cell_value: clean,
          }),
        }).then(function (r) { if (!r.ok) throw new Error("단가 연동 저장 실패"); return r; });
      };
    }
    // 그 달에 한 칸만 있는 자리(Invoice Summary(VK)) — 일자별로 쓰지 않는다.
    (link.monthTargets || []).forEach(function (target) {
      tasks.push(post(target, target.rowKey));
    });
    if (link.monthTargets && !link.targets && !link.daily) {
      return runLimited(tasks, 6).then(function () {
        return { applied: 1, cells: tasks.length, label: link.label, monthly: true };
      });
    }
    (link.targets || []).forEach(function (target) {
      for (var d = 1; d <= days; d++) {
        (function (day) {
          tasks.push(function () {
            return fetch(TABLES_BASE + "/" + CELL_TABLE, {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({
                month: ym, year_no: Number(year), month_no: Number(month),
                table_name: target.table,
                row_key: target.key(day),
                col_index: target.col,
                cell_value: clean,
              }),
            }).then(function (r) { if (!r.ok) throw new Error("단가 연동 저장 실패"); return r; });
          });
        })(d);
      }
    });
    return dailyStep.then(function (dailyDays) {
      return runLimited(tasks, 6).then(function () {
        return { applied: days, cells: tasks.length + dailyDays, label: link.label };
      });
    });
  }

  global.UnitPrice = {
    ITEMS: ITEMS,
    REGISTRY_TABLE: REGISTRY_TABLE,
    PRICE_LINKS: PRICE_LINKS,
    priceLink: priceLink,
    applyPriceLink: applyPriceLink,
    fetchPrices: fetchPrices,
    savePrice: savePrice,
    applyPriceDaily: applyPriceDaily,
    pad2: pad2,
    daysInMonth: daysInMonth,
    fetchRegistered: fetchRegistered,
    saveRegistered: saveRegistered,
    registeredValue: registeredValue,
    applyToDaily: applyToDaily,
    itemsForTables: function (tableNames) {
      return ITEMS.filter(function (it) { return tableNames.indexOf(it.table) !== -1; });
    },

    // daily 페이지(filterYear/filterMonth 사용)의 "단가 적용" 버튼 배선.
    //  buttonId 버튼 클릭 → 조회 연/월의 등록단가를 그 달 전체 일자 단가 셀에 적용 후 새로고침.
    wireApplyButton: function (buttonId, tables) {
      var btn = document.getElementById(buttonId);
      if (!btn) return;
      var items = ITEMS.filter(function (it) { return tables.indexOf(it.table) !== -1; });
      btn.addEventListener("click", function () {
        var yearSel = document.getElementById("filterYear");
        var monthSel = document.getElementById("filterMonth");
        var year = Number(yearSel && yearSel.value);
        var month = Number(monthSel && monthSel.value);
        if (!year || !month) { alert("조회 연/월을 먼저 선택하세요."); return; }
        if (!confirm(year + "년 " + month + "월 등록 단가를 이 달 전체 일자의 단가 칸에 적용할까요?\n(해당 월 기존 단가는 덮어씁니다)")) return;
        var prev = btn.textContent;
        btn.disabled = true; btn.textContent = "적용 중…";
        fetchRegistered(year)
          .then(function (reg) { return applyToDaily(items, year, month, reg); })
          .then(function (res) {
            if (!res.applied) {
              btn.disabled = false; btn.textContent = prev;
              alert("이 달에 등록된 단가가 없습니다.\n먼저 '단가 입력' 페이지에서 등록하세요.");
              return;
            }
            try { localStorage.setItem("steamlog:shared-period", JSON.stringify({ year: year, month: pad2(month) })); } catch (e) {}
            btn.textContent = "적용됨 · 새로고침";
            global.location.reload();
          })
          .catch(function () { btn.disabled = false; btn.textContent = prev; alert("단가 적용 실패"); });
      });
    },
  };
})(window);
