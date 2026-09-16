/*
 * 설비별 단가(연/월별) 공용 모듈.
 *  - 등록 단가는 table_cell_value 에 저장: table_name="unit_price",
 *    month=연도(String), row_key=항목코드, col_index=월(1~12), cell_value=단가.
 *  - daily 페이지(unit-lng-cost / unit-flow-daily)의 단가 셀은
 *    month="YYYY-MM", row_key="01".."31"(일), col_index=단가컬럼 에 일자별 동일값 저장.
 *  - "단가 적용": 등록된 (연,월) 단가를 그 달 전체 일자 셀에 일괄 upsert.
 */
(function (global) {
  "use strict";

  var TABLES_BASE = "/tables";
  var CELL_TABLE = "table_cell_value";
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

  // 등록 단가 조회 (해당 연도 전체) → { itemCode: { monthNo: "value" } }
  //  table 인자 생략 시 기본 REGISTRY_TABLE(=unit_price). daily 호출은 그대로 기본값 사용.
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

  // 등록 단가 저장(upsert). table 생략 시 기본 REGISTRY_TABLE.
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
  //  targets = 한 단가가 들어가는 칸들. key(일) 은 그 페이지가 쓰는 row_key 형식이다.
  //   - LNG 연료 사용 비용 : lng_m_boiler 5열 / lng_m_burner 6열, row_key "01".."31"
  //   - 유동상 세부 운영내역: fluidized_detail_main, row_key = 일자열(일+2) + 행번호, col 0
  //   - 폐합성 도급내역     : incinerator_contract, row_key "섹션|일|필드", col 1
  //  복합보일러 단가는 아직 연결하지 않는다.
  var PRICE_LINKS = [
    { code: "lng_unit", label: "LNG 연료 사용 비용 단가(보일러·버너)", targets: [
      { table: "lng_m_boiler", col: 5, key: function (d) { return pad2(d); } },
      { table: "lng_m_burner", col: 6, key: function (d) { return pad2(d); } },
    ] },
    { code: "fluid_steam", label: "세부 운영내역 KNE 스팀단가", targets: [
      { table: "fluidized_detail_main", col: 0, key: function (d) { return columnLabel(d + 2) + "11"; } },
    ] },
    { code: "fluid_fixed_hr", label: "세부 운영내역 KNE 운휴시간 단가", targets: [
      { table: "fluidized_detail_main", col: 0, key: function (d) { return columnLabel(d + 2) + "13"; } },
    ] },
    // 폐합성 운영단가는 도급내역의 결산 기준 표와 실 스팀량 기준 표에 같은 값으로 들어간다.
    { code: "waste1_steam", label: "도급내역 1호기 운영단가", targets: [
      { table: "incinerator_contract", col: 1, key: function (d) { return "unit1|" + d + "|unit"; } },
      { table: "incinerator_contract", col: 1, key: function (d) { return "real|" + d + "|unit-1"; } },
    ] },
    { code: "waste2_steam", label: "도급내역 2호기 운영단가", targets: [
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
    link.targets.forEach(function (target) {
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
    return runLimited(tasks, 6).then(function () {
      return { applied: days, cells: tasks.length, label: link.label };
    });
  }

  global.UnitPrice = {
    ITEMS: ITEMS,
    REGISTRY_TABLE: REGISTRY_TABLE,
    PRICE_LINKS: PRICE_LINKS,
    priceLink: priceLink,
    applyPriceLink: applyPriceLink,
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
