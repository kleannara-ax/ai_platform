/*
 * 폐합성소각로 운전일지 — 원본 엑셀 "폐합성소각로운전일지(YYYY년 MM월).xlsx" 의 시트별 월 표.
 *  - 저장소: table_cell_value (table_name="waste_incin_log", row_key=일, col_index=항목 고정번호)
 *  - col_index 는 항목 고정 번호이므로 재배치 금지. 시트별 구간:
 *      소각량 1~29 / 스팀량 100~ / 급수량 200~ / 전력 300~ / 폐기물 400~ /
 *      약품 500~ / TMS 600~ / 외부반입 700~ / 폐기물발생 800~
 *      ※ 스팀량(적산) 은 별도 번호 없음 — 스팀량의 생산량(100~102) 을 그대로 누적한다.
 *  - 저장은 수동. 입력은 화면에만 반영되고 저장 버튼을 눌러야 서버에 기록된다.
 *
 *  행 종류(kind)
 *    input  : 입력 칸                                     (col)
 *    text   : 문자 입력 칸 (합계/평균 없음)                 (col)
 *    sum    : 입력값 합                                    (parts:[col])
 *    stock  : 재고 — 1일은 입력, 2일부터 전일재고+반입-사용   (col, plus:[], minus:[])
 *    calc   : 임의 계산                                    (calc(ctx))
 *    cumsum : 적산 — 1일부터 해당일까지 누적                 (col)
 */
document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "waste-incinerator-log.html") return;

  const CELL_TABLE = "table_cell_value";
  const LOG_TABLE = "waste_incin_log";
  // 다른 스팀 페이지와 동일 (platform-iframe-compat.js 가 /steam/api/tables 로 재작성)
  const TABLES_BASE = "/tables";
  const PERIOD_KEY = "steamlog:shared-period";

  // ── 시트 구성 (원본 행 순서 그대로) ──────────────────────────
  const SHEETS = [
    {
      key: "burn", title: "소각량", layout: "days-cols", aggregates: ["sum", "avg"],
      blocks: [{ rows: [
        { group: "폐1", label: "자폐", kind: "input", col: 1 },
        { group: "폐1", label: "재활용", kind: "input", col: 2 },
        { group: "폐1", label: "합계", kind: "sum", parts: [1, 2] },
        { group: "폐1", label: "발생량", kind: "input", col: 3 },
        { group: "폐1", label: "가동시간", kind: "input", col: 4, digits: 1 },
        { group: "폐1", label: "외폐재고", kind: "input", col: 5 },

        { group: "폐2", label: "자폐", kind: "input", col: 11 },
        { group: "폐2", label: "재활용", kind: "input", col: 12 },
        { group: "폐2", label: "합계", kind: "sum", parts: [11, 12] },
        { group: "폐2", label: "발생량", kind: "input", col: 13 },
        { group: "폐2", label: "가동시간", kind: "input", col: 14, digits: 1 },
        { group: "폐2", label: "자폐재고", kind: "input", col: 15 },

        { group: "합계", label: "자폐", kind: "sum", parts: [1, 11], total: true },
        { group: "합계", label: "재활용", kind: "sum", parts: [2, 12], total: true },
        { group: "합계", label: "외폐", kind: "input", col: 21, total: true },
      ] }],
    },
    {
      key: "steam", title: "스팀량", layout: "days-cols", aggregates: ["avg"], itemWidth: 96,
      blocks: [
        { rows: [
          { label: "시간당", kind: "calc", digits: 1, calc: (ctx) => ctx.div(ctx.v(100), 24) },
          { label: "폐1", kind: "input", col: 100 },
          { label: "폐2-1", kind: "input", col: 101 },
          { label: "폐2-2", kind: "input", col: 102 },
          { label: "폐2-T", kind: "sum", parts: [101, 102] },
          { label: "Total", kind: "sum", parts: [100, 101, 102], total: true },
          { label: "기관", kind: "input", col: 103 },
          { label: "저/고", kind: "text", col: 104 },
        ] },
        { rows: [
          { label: "1호기", kind: "calc", id: "steamUnit1", calc: (ctx) => ctx.sub(ctx.v(103), ctx.v(101)) },
          { label: "2호기", kind: "calc", id: "steamUnit2", calc: (ctx) => ctx.add(ctx.v(101), ctx.v(102)) },
          { label: "합계", kind: "calc", total: true, calc: (ctx) => ctx.add(ctx.r("steamUnit1"), ctx.r("steamUnit2")) },
        ] },
      ],
    },
    {
      key: "steamTotal", title: "스팀량(적산)", layout: "days-rows", dateLabel: "날짜",
      columns: [
        { group: "폐#1", label: "적산량", kind: "cumsum", col: 100 },
        { group: "폐#1", label: "생산량", kind: "input", col: 100 },
        { group: "폐#2 1차", label: "적산량", kind: "cumsum", col: 101 },
        { group: "폐#2 1차", label: "생산량", kind: "input", col: 101 },
        { group: "폐#2 2차", label: "적산량", kind: "cumsum", col: 102 },
        { group: "폐#2 2차", label: "생산량", kind: "input", col: 102 },
      ],
      note: "생산량은 스팀량 시트와 같은 값입니다. 적산량은 1일부터 누적됩니다.",
    },
    {
      key: "water", title: "급수량", layout: "days-cols", aggregates: ["avg"], itemWidth: 96,
      blocks: [{ rows: [
        { label: "폐1-1", kind: "input", col: 200 },
        { label: "폐1-2", kind: "input", col: 201 },
        { label: "폐#1-T", kind: "sum", parts: [200, 201], total: true },
        { label: "폐2-1", kind: "input", col: 202 },
        { label: "폐2-2", kind: "input", col: 203 },
        { label: "폐#2-T", kind: "sum", parts: [202, 203], total: true },
      ] }],
    },
    {
      key: "power", title: "전력", layout: "days-cols", aggregates: ["avg"], itemWidth: 96,
      blocks: [{ rows: [
        { label: "지침", kind: "input", col: 300 },
        { label: "폐#1", kind: "input", col: 301 },
        { label: "폐#2", kind: "input", col: 302 },
        { label: "EP", kind: "input", col: 303 },
        { label: "Total", kind: "sum", parts: [301, 302, 303], total: true },
      ] }],
    },
    {
      key: "waste", title: "폐기물", layout: "days-cols", aggregates: ["sum", "avg"],
      blocks: [{ rows: [
        { group: "비산재", label: "폐#1", kind: "input", col: 400 },
        { group: "비산재", label: "폐#2", kind: "input", col: 401 },
        { group: "비산재", label: "Total", kind: "sum", parts: [400, 401], total: true },
        { group: "바닥재", label: "폐#1", kind: "input", col: 402 },
        { group: "바닥재", label: "폐#2", kind: "input", col: 403 },
        { group: "바닥재", label: "Total", kind: "sum", parts: [402, 403], total: true },
      ] }],
    },
    {
      key: "chemical", title: "약품", layout: "days-cols", aggregates: ["sum", "avg"],
      blocks: [{ rows: [
        { group: "요소수", label: "반입", kind: "input", col: 500 },
        { group: "요소수", label: "사용", kind: "input", col: 501 },
        { group: "요소수", label: "재고", kind: "stock", col: 502, plus: [500], minus: [501] },

        { group: "청관재", label: "반입", kind: "input", col: 503 },
        { group: "청관재", label: "사용", kind: "input", col: 504 },
        { group: "청관재", label: "재고", kind: "stock", col: 505, plus: [503], minus: [504] },

        { group: "소금", label: "반입", kind: "input", col: 506 },
        { group: "소금", label: "사용", kind: "input", col: 507 },
        { group: "소금", label: "유동상", kind: "input", col: 508 },
        { group: "소금", label: "재고", kind: "stock", col: 509, plus: [506], minus: [507, 508] },

        { group: "가성", label: "반입", kind: "input", col: 510 },
        { group: "가성", label: "사용", kind: "input", col: 511 },
        { group: "가성", label: "재고", kind: "stock", col: 512, plus: [510], minus: [511] },

        { group: "활성탄", label: "반입", kind: "input", col: 513 },
        { group: "활성탄", label: "사용", kind: "input", col: 514 },
        { group: "활성탄", label: "재고", kind: "stock", col: 515, plus: [513], minus: [514] },

        { group: "부생유", label: "반입", kind: "input", col: 516 },
        { group: "부생유", label: "폐1", kind: "input", col: 517 },
        { group: "부생유", label: "폐2", kind: "input", col: 518 },
        { group: "부생유", label: "슬1", kind: "input", col: 519 },
        { group: "부생유", label: "슬2", kind: "input", col: 520 },
        { group: "부생유", label: "재고량", kind: "stock", col: 521, plus: [516], minus: [517, 518, 519, 520] },

        { group: "크링커", label: "측벽", kind: "input", col: 522 },
        { group: "크링커", label: "낙차", kind: "input", col: 523 },
        { group: "크링커", label: "반입", kind: "input", col: 524 },
        { group: "크링커", label: "사용", kind: "input", col: 525 },
        { group: "크링커", label: "재고", kind: "stock", col: 526, plus: [524], minus: [525] },
        // 원본: =사용*2*1000/소각량!발생량(폐1)
        { group: "크링커", label: "투입비", kind: "calc", digits: 1, noAggregate: true,
          calc: (ctx) => ctx.div(ctx.mul(ctx.v(525), 2000), ctx.v(3)) },
      ] }],
    },
    {
      key: "tms", title: "TMS", layout: "days-cols", aggregates: ["sum"],
      blocks: [{ rows: [
        { group: "폐#1", label: "분진", kind: "input", col: 600 },
        { group: "폐#1", label: "CO", kind: "input", col: 601 },
        { group: "폐#1", label: "Nox", kind: "input", col: 602 },
        { group: "폐#1", label: "Sox", kind: "input", col: 603 },
        { group: "폐#1", label: "HCl", kind: "input", col: 604 },
        { group: "폐#1", label: "온도", kind: "input", col: 605 },
        { group: "폐#2", label: "분진", kind: "input", col: 606 },
        { group: "폐#2", label: "CO", kind: "input", col: 607 },
        { group: "폐#2", label: "Nox", kind: "input", col: 608 },
        { group: "폐#2", label: "Sox", kind: "input", col: 609 },
        { group: "폐#2", label: "HCl", kind: "input", col: 610 },
        { group: "폐#2", label: "온도", kind: "input", col: 611 },
      ] }],
    },
    {
      key: "inbound", title: "외부반입", layout: "days-rows", dateLabel: "업체명", footerSum: true,
      columns: [
        { label: "보노아", kind: "input", col: 700 },
        { label: "스라이브", kind: "input", col: 701 },
        { label: "나투라", kind: "input", col: 702 },
        { label: "대한제지", kind: "input", col: 703 },
        { label: "신성", kind: "input", col: 704 },
        { label: "그린이엔텍", kind: "input", col: 705 },
        { label: "금보산업", kind: "input", col: 706 },
        { label: "청원es", kind: "input", col: 707 },
        { label: "티와이", kind: "input", col: 708 },
        { label: "재운산업", kind: "input", col: 709 },
        { label: "현진RC", kind: "input", col: 710 },
        { label: "중앙이엔비", kind: "input", col: 711 },
        { label: "주원", kind: "input", col: 712 },
        { label: "태창", kind: "input", col: 713 },
        { label: "누계", kind: "sum", total: true,
          parts: [700, 701, 702, 703, 704, 705, 706, 707, 708, 709, 710, 711, 712, 713] },
      ],
    },
    {
      key: "generated", title: "폐기물발생", layout: "days-cols", aggregates: ["sum"], itemWidth: 96,
      blocks: [{ rows: [
        { label: "폐목재", kind: "input", col: 800 },
        { label: "생불", kind: "input", col: 801 },
        { label: "폐합성", kind: "input", col: 802 },
        { label: "리젝트", kind: "input", col: 803 },
        { label: "라가", kind: "input", col: 804 },
        { label: "재고량", kind: "input", col: 805, total: true },
      ] }],
    },
  ];

  const D2 = 2;

  const yearSelect = document.getElementById("wilYear");
  const monthSelect = document.getElementById("wilMonth");
  const reloadButton = document.getElementById("wilReload");
  const saveButton = document.getElementById("wilSave");
  const messageBox = document.getElementById("wilMsg");
  const tabsRoot = document.getElementById("wilTabs");
  const panelsRoot = document.getElementById("wilPanels");
  const uploadButton = document.getElementById("wilUpload");
  const uploadInput = document.getElementById("wilUploadFile");
  const downloadButton = document.getElementById("wilDownload");
  if (!yearSelect || !monthSelect || !panelsRoot || !tabsRoot) return;

  const cache = new Map();   // `${monthKey}|${day}|${col}` → 값
  const rowIds = new Map();
  const pending = new Map(); // 저장 대기
  const loadedMonths = new Set();
  let messageTimer = null;
  let activeKey = SHEETS[0].key;

  SHEETS.forEach((sheet) => {
    (sheet.blocks || []).forEach((block, blockIndex) => {
      block.hasGroup = block.rows.some((row) => row.group);
      block.rows.forEach((row, rowIndex) => { row.uid = `${sheet.key}-${blockIndex}-${rowIndex}`; });
    });
    (sheet.columns || []).forEach((column, index) => { column.uid = `${sheet.key}-c${index}`; });
  });

  function sheetByKey(key) { return SHEETS.find((sheet) => sheet.key === key); }

  // ── 공통 ────────────────────────────────────────────────────
  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  function parseNumber(value) {
    if (value === null || value === undefined) return null;
    const text = String(value).replace(/,/g, "").trim();
    if (!text) return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function formatNumber(value, digits = D2) {
    if (value === null || value === undefined || value === "") return "";
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return String(value);
    return new Intl.NumberFormat("ko-KR", { minimumFractionDigits: 0, maximumFractionDigits: digits }).format(numeric);
  }

  function currentMonthKey() { return `${yearSelect.value}-${monthSelect.value}`; }

  function daysInMonth(monthKey) {
    const [year, month] = monthKey.split("-").map(Number);
    return new Date(year, month, 0).getDate();
  }

  function cacheKey(monthKey, day, col) { return `${monthKey}|${day}|${col}`; }

  function getValue(monthKey, day, col) { return cache.get(cacheKey(monthKey, day, col)) ?? ""; }

  function getNumber(monthKey, day, col) { return parseNumber(getValue(monthKey, day, col)); }

  function setMessage(text, kind) {
    if (!messageBox) return;
    messageBox.textContent = text;
    messageBox.className = `wil-msg${kind ? ` ${kind}` : ""}`;
    if (messageTimer) window.clearTimeout(messageTimer);
    if (text && kind === "ok") messageTimer = window.setTimeout(() => { messageBox.textContent = ""; }, 1800);
  }

  // ── 값 계산 ─────────────────────────────────────────────────
  // 값이 하나도 없으면 null(빈칸) 을 유지한다.
  function addAll(values) {
    let total = null;
    values.forEach((value) => { if (value !== null && value !== undefined) total = (total || 0) + value; });
    return total;
  }

  function makeEvaluator(monthKey) {
    const memo = new Map();
    const byId = new Map();
    SHEETS.forEach((sheet) => (sheet.blocks || []).forEach((block) => block.rows.forEach((row) => {
      if (row.id && !byId.has(row.id)) byId.set(row.id, row);
    })));

    function value(def, day) {
      if (!def) return null;
      const key = `${def.uid || def.label}|${day}`;
      if (memo.has(key)) return memo.get(key);
      memo.set(key, null); // 순환 참조 방지
      let result = null;
      if (def.kind === "input" || def.kind === "text") {
        result = getNumber(monthKey, day, def.col);
      } else if (def.kind === "sum") {
        result = addAll(def.parts.map((col) => getNumber(monthKey, day, col)));
      } else if (def.kind === "cumsum") {
        let total = null;
        for (let each = 1; each <= day; each += 1) {
          const daily = getNumber(monthKey, each, def.col);
          if (daily !== null) total = (total || 0) + daily;
        }
        result = total;
      } else if (def.kind === "stock") {
        const stored = getNumber(monthKey, day, def.col);
        if (stored !== null || day <= 1) {
          result = stored;
        } else {
          const prev = value(def, day - 1);
          const plus = addAll((def.plus || []).map((col) => getNumber(monthKey, day, col)));
          const minus = addAll((def.minus || []).map((col) => getNumber(monthKey, day, col)));
          result = prev === null && plus === null && minus === null
            ? null
            : (prev || 0) + (plus || 0) - (minus || 0);
        }
      } else if (def.kind === "calc") {
        result = def.calc(makeContext(day));
      }
      memo.set(key, result);
      return result;
    }

    function makeContext(day) {
      return {
        day,
        v: (col) => getNumber(monthKey, day, col),
        r: (id) => value(byId.get(id), day),
        add: (...values) => addAll(values),
        sub: (a, b) => (a === null && b === null ? null : (a || 0) - (b || 0)),
        mul: (a, b) => (a === null || b === null ? null : a * b),
        div: (a, b) => (a === null || !b ? null : a / b),
      };
    }

    return { value };
  }

  function rowTotal(evaluator, def, days) {
    let total = null;
    for (let day = 1; day <= days; day += 1) {
      const value = evaluator.value(def, day);
      if (value === null) continue;
      total = (total || 0) + value;
    }
    return total;
  }

  function rowAverage(evaluator, def, days) {
    let total = null;
    let count = 0;
    for (let day = 1; day <= days; day += 1) {
      const value = evaluator.value(def, day);
      if (value === null) continue;
      total = (total || 0) + value;
      count += 1;
    }
    return count ? total / count : null;
  }

  // ── 표 그리기 ───────────────────────────────────────────────
  const GROUP_W = 64, ITEM_W = 88, TOTAL_W = 88, DAY_MIN = 62;

  /** 화면 표시용 서식 (입력칸을 벗어날 때 원래 표기로 되돌린다) */
  function formatCellValue(raw, digits, isText) {
    if (isText) return raw;
    const number = parseNumber(raw);
    return number === null ? raw : formatNumber(number, Number.isFinite(digits) ? digits : D2);
  }

  function inputCell(monthKey, day, col, digits, isText) {
    const raw = getValue(monthKey, day, col);
    const number = isText ? null : parseNumber(raw);
    const shown = number === null ? raw : formatNumber(number, digits);
    return `<td><input data-wil-col="${col}" data-wil-day="${day}"${isText ? " data-wil-text=\"1\"" : ""} ` +
      `data-wil-digits="${digits}" inputmode="${isText ? "text" : "decimal"}" autocomplete="off" ` +
      `value="${escapeHtml(shown)}" /></td>`;
  }

  function renderDaysCols(sheet, root, monthKey, days, skipCorrection) {
    const dayList = Array.from({ length: days }, (_unused, index) => index + 1);
    const evaluator = makeEvaluator(monthKey);
    const itemW = sheet.itemWidth || ITEM_W;
    const aggregates = sheet.aggregates || [];
    const available = root.parentElement?.clientWidth || 0;

    const tables = sheet.blocks.map((block) => {
      const labelW = (block.hasGroup ? GROUP_W : 0) + itemW;
      const width = available
        ? Math.max(DAY_MIN, Math.floor((available - labelW - TOTAL_W * aggregates.length - 2) / days))
        : DAY_MIN;

      const colgroup = "<colgroup>" +
        (block.hasGroup ? `<col style="width:${GROUP_W}px" />` : "") +
        `<col style="width:${itemW}px" />` +
        dayList.map(() => `<col style="width:${width}px" />`).join("") +
        aggregates.map(() => `<col style="width:${TOTAL_W}px" />`).join("") +
        "</colgroup>";

      const head = "<thead><tr>" +
        `<th class="group-col" colspan="${block.hasGroup ? 2 : 1}" style="width:${labelW}px">구 분</th>` +
        dayList.map((day) => `<th style="width:${width}px">${day}</th>`).join("") +
        aggregates.map((kind) => `<th class="total-head">${kind === "sum" ? "합계" : "평균"}</th>`).join("") +
        "</tr></thead>";

      const body = block.rows.map((def, index) => {
        const isGroupStart = block.hasGroup && (index === 0 || block.rows[index - 1].group !== def.group);
        const span = block.hasGroup ? block.rows.filter((row) => row.group === def.group).length : 0;
        const groupCell = isGroupStart ? `<th class="group-col" rowspan="${span}">${escapeHtml(def.group)}</th>` : "";
        const digits = def.digits ?? D2;
        const editable = def.kind === "input" || def.kind === "text";

        const cells = dayList.map((day) => {
          if (editable) return inputCell(monthKey, day, def.col, digits, def.kind === "text");
          if (def.kind === "stock" && day === 1) return inputCell(monthKey, day, def.col, digits, false);
          const value = evaluator.value(def, day);
          return `<td class="calc">${value === null ? "" : formatNumber(value, digits)}</td>`;
        }).join("");

        const aggregateCells = aggregates.map((kind) => {
          if (def.kind === "text" || def.noAggregate) return "<td class=\"total-cell\"></td>";
          const value = kind === "sum" ? rowTotal(evaluator, def, days) : rowAverage(evaluator, def, days);
          return `<td class="total-cell">${value === null ? "" : formatNumber(value, digits)}</td>`;
        }).join("");

        const itemLeft = block.hasGroup ? GROUP_W : 0;
        return `<tr class="${def.total ? "sum-row" : ""}">${groupCell}` +
          `<th class="item-col" style="left:${itemLeft}px">${escapeHtml(def.label)}</th>${cells}${aggregateCells}</tr>`;
      }).join("");

      const tableWidth = labelW + days * width + TOTAL_W * aggregates.length;
      return `<table class="wil-table" data-no-resize style="width:${tableWidth}px">${colgroup}${head}<tbody>${body}</tbody></table>`;
    }).join("<div class=\"wil-gap\"></div>");

    root.innerHTML = tables;

    if (!skipCorrection) {
      const wrap = root.parentElement;
      const table = root.querySelector(".wil-table");
      if (table && wrap && table.getBoundingClientRect().width > wrap.clientWidth + 1) {
        renderDaysCols(sheet, root, monthKey, days, true);
      }
    }
  }

  function renderDaysRows(sheet, root, monthKey, days) {
    const evaluator = makeEvaluator(monthKey);
    const columns = sheet.columns;
    const hasGroup = columns.some((column) => column.group);
    const dateLabel = sheet.dateLabel || "날짜";

    const head = hasGroup
      ? `<tr><th class="group-col" rowspan="2">${escapeHtml(dateLabel)}</th>` +
        columns.map((column, index) => {
          if (index > 0 && columns[index - 1].group === column.group) return "";
          const span = columns.filter((other) => other.group === column.group).length;
          return `<th colspan="${span}">${escapeHtml(column.group || "")}</th>`;
        }).join("") + "</tr><tr>" +
        columns.map((column) => `<th class="${column.total ? "total-head" : ""}">${escapeHtml(column.label)}</th>`).join("") +
        "</tr>"
      : `<tr><th class="group-col">${escapeHtml(dateLabel)}</th>` +
        columns.map((column) => `<th class="${column.total ? "total-head" : ""}">${escapeHtml(column.label)}</th>`).join("") +
        "</tr>";

    const body = Array.from({ length: days }, (_unused, index) => index + 1).map((day) => {
      const cells = columns.map((column) => {
        if (column.kind === "input") return inputCell(monthKey, day, column.col, column.digits ?? D2, false);
        const value = evaluator.value(column, day);
        return `<td class="${column.total ? "total-cell" : "calc"}">${value === null ? "" : formatNumber(value, column.digits ?? D2)}</td>`;
      }).join("");
      return `<tr><th class="group-col">${day}</th>${cells}</tr>`;
    }).join("");

    const footer = sheet.footerSum
      ? "<tr class=\"sum-row\"><th class=\"group-col\">합계</th>" +
        columns.map((column) => {
          const total = rowTotal(evaluator, column, days);
          return `<td class="total-cell">${total === null ? "" : formatNumber(total, column.digits ?? D2)}</td>`;
        }).join("") + "</tr>"
      : "";

    const DATE_W = 76, COL_W = 100;
    const colgroup = `<colgroup><col style="width:${DATE_W}px" />` +
      columns.map(() => `<col style="width:${COL_W}px" />`).join("") + "</colgroup>";
    const tableWidth = DATE_W + columns.length * COL_W;

    root.innerHTML = (sheet.note ? `<p class="wil-note">${escapeHtml(sheet.note)}</p>` : "") +
      `<table class="wil-table wil-table-rows" data-no-resize style="width:${tableWidth}px">` +
      `${colgroup}<thead>${head}</thead><tbody>${body}${footer}</tbody></table>`;
    stickyHeadRows(root);
  }

  // 헤더가 두 줄인 표(그룹 + 항목)는 둘째 줄을 첫 줄 높이만큼 내려서 함께 고정한다.
  function stickyHeadRows(root) {
    root.querySelectorAll("table.wil-table").forEach((table) => {
      const rows = table.tHead ? Array.from(table.tHead.rows) : [];
      if (rows.length < 2) return;
      let offset = 0;
      rows.forEach((row) => {
        Array.from(row.cells).forEach((cell) => { cell.style.top = `${offset}px`; });
        offset += Math.round(row.getBoundingClientRect().height);
      });
    });
  }

  function renderActive(skipCorrection) {
    const sheet = sheetByKey(activeKey);
    const root = panelsRoot.querySelector(`[data-wil-body="${activeKey}"]`);
    if (!sheet || !root) return;
    const monthKey = currentMonthKey();
    const days = daysInMonth(monthKey);
    if (sheet.layout === "days-rows") renderDaysRows(sheet, root, monthKey, days);
    else renderDaysCols(sheet, root, monthKey, days, skipCorrection);
    bindInputs(root);
    fitHeight(root.parentElement);
  }

  function fitHeight(wrap) {
    if (!wrap) return;
    const top = wrap.getBoundingClientRect().top;
    wrap.style.maxHeight = `${Math.max(240, Math.floor(window.innerHeight - top - 16))}px`;
    const overflow = document.documentElement.scrollHeight - window.innerHeight;
    if (overflow > 0) {
      wrap.style.maxHeight = `${Math.max(240, Math.floor(parseFloat(wrap.style.maxHeight) - overflow))}px`;
    }
  }

  // 값이 바뀌면 표를 다시 그리는데, 그때 방금 방향키로 옮겨간 칸도 같이 없어진다.
  // 그래서 어디로 이동하려던 것인지(relatedTarget) 기억해 두었다가 다시 그린 뒤 그 칸에 포커스를 돌려준다.
  function restoreFocus(root, moveTo) {
    if (!moveTo) return;
    const next = root.querySelector(
      `input[data-wil-col="${moveTo.col}"][data-wil-day="${moveTo.day}"]`);
    if (!next) return;
    next.focus();
    if (typeof next.select === "function") next.select();
  }

  function bindInputs(root) {
    root.querySelectorAll("input[data-wil-col]").forEach((input) => {
      const col = Number(input.dataset.wilCol);
      const day = Number(input.dataset.wilDay);
      input.addEventListener("focus", () => { input.value = getValue(currentMonthKey(), day, col); });
      input.addEventListener("keydown", (event) => { if (event.key === "Enter") input.blur(); });
      // blur 대신 focusout — 이동해 갈 칸(relatedTarget)을 알 수 있다.
      input.addEventListener("focusout", (event) => {
        const isText = input.dataset.wilText === "1";
        const raw = isText
          ? String(input.value ?? "").trim()
          : String(input.value ?? "").replace(/,/g, "").trim();

        // 값이 그대로면 다시 그리지 않는다. (방향키·탭으로 지나가기만 한 경우)
        if (raw === getValue(currentMonthKey(), day, col)) {
          input.value = formatCellValue(raw, Number(input.dataset.wilDigits), isText);
          return;
        }

        const to = event.relatedTarget;
        const moveTo = to && to.dataset && to.dataset.wilCol
          ? { col: to.dataset.wilCol, day: to.dataset.wilDay }
          : null;

        stageValue(day, col, raw);
        renderActive();
        const again = root.querySelector(`input[data-wil-col="${col}"][data-wil-day="${day}"]`);
        if (again) again.classList.add("is-edited");
        restoreFocus(root, moveTo);
      });
    });
  }

  // ── 저장 (수동) ─────────────────────────────────────────────
  function stageValue(day, col, value) {
    const monthKey = currentMonthKey();
    const key = cacheKey(monthKey, day, col);
    if (value === "") cache.delete(key);
    else cache.set(key, value);
    pending.set(key, { monthKey, day, col, value });
    updateSaveState();
  }

  function updateSaveState() {
    if (!saveButton) return;
    const count = pending.size;
    saveButton.disabled = count === 0;
    saveButton.textContent = count === 0 ? "저장" : `저장 (${count})`;
    document.body.classList.toggle("wil-dirty", count > 0);
    if (count > 0) setMessage(`미저장 ${count}건`);
  }

  async function upsert(payload, existingId) {
    const url = existingId ? `${TABLES_BASE}/${CELL_TABLE}/${existingId}` : `${TABLES_BASE}/${CELL_TABLE}`;
    const response = await fetch(url, {
      method: existingId ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error("저장에 실패했습니다.");
    return response.json();
  }

  async function saveAll() {
    if (!pending.size) return true;
    saveButton.disabled = true;
    setMessage("저장 중");
    try {
      for (const item of Array.from(pending.values())) {
        const [year, month] = item.monthKey.split("-");
        const key = cacheKey(item.monthKey, item.day, item.col);
        const saved = await upsert({
          month: item.monthKey,
          year_no: Number(year),
          month_no: Number(month),
          table_name: LOG_TABLE,
          row_key: String(item.day),
          col_index: item.col,
          cell_value: item.value === "" ? null : item.value,
        }, rowIds.get(key));
        if (saved && saved.id) rowIds.set(key, saved.id);
      }
      pending.clear();
      updateSaveState();
      setMessage("저장 완료", "ok");
      renderActive();
      return true;
    } catch (error) {
      setMessage(error.message || "저장 실패", "err");
      window.alert(error.message || "저장에 실패했습니다.");
      return false;
    } finally {
      saveButton.disabled = pending.size === 0;
    }
  }

  function forgetMonth(monthKey) {
    loadedMonths.delete(monthKey);
    [cache, rowIds].forEach((store) => {
      Array.from(store.keys()).forEach((key) => { if (key.startsWith(`${monthKey}|`)) store.delete(key); });
    });
  }

  async function discardPending() {
    pending.clear();
    updateSaveState();
    forgetMonth(currentMonthKey());
    await load();
  }

  async function confirmLeave() {
    if (!pending.size) return true;
    if (window.confirm(`저장하지 않은 변경이 ${pending.size}건 있습니다.\n\n[확인] 저장하고 계속\n[취소] 저장 여부를 다시 묻습니다`)) {
      return await saveAll();
    }
    if (window.confirm("저장하지 않고 진행할까요?\n입력한 내용이 사라집니다.")) {
      await discardPending();
      return true;
    }
    return false;
  }

  // ── 조회 ────────────────────────────────────────────────────
  async function fetchMonth(monthKey) {
    if (loadedMonths.has(monthKey)) return;
    const limit = 1000;
    let pageNo = 1;
    let totalPages = 1;
    while (pageNo <= totalPages) {
      const url = `${TABLES_BASE}/${CELL_TABLE}?page=${pageNo}&limit=${limit}` +
        `&month=${encodeURIComponent(monthKey)}&tableNames=${encodeURIComponent(LOG_TABLE)}`;
      const response = await fetch(url);
      if (!response.ok) throw new Error("데이터를 불러오지 못했습니다.");
      const payload = await response.json();
      const rows = Array.isArray(payload?.data) ? payload.data : [];
      rows.filter((row) => row.table_name === LOG_TABLE).forEach((row) => {
        const key = cacheKey(monthKey, Number(row.row_key), Number(row.col_index));
        const value = row.cell_value === null || row.cell_value === undefined ? "" : String(row.cell_value);
        if (value !== "") cache.set(key, value);
        rowIds.set(key, row.id);
      });
      const total = Number(payload?.total || rows.length);
      totalPages = Math.max(1, Math.ceil(total / Number(payload?.limit || limit)));
      pageNo += 1;
    }
    loadedMonths.add(monthKey);
  }

  async function load() {
    setMessage("불러오는 중");
    try {
      await fetchMonth(currentMonthKey());
      renderActive();
      setMessage("");
    } catch (error) {
      setMessage(error.message || "조회 실패", "err");
    }
  }

  // ── 엑셀 일괄 업로드 ────────────────────────────────────────
  // 파일명에서 연/월을 읽어 선택한 월과 다르면 먼저 확인을 받는다. (유동상 운전일지와 같은 방식)
  function monthFromFileName(name) {
    const text = String(name || "");
    const withYear = text.match(/(20\d{2})\s*[.\-_년\s]*\s*(\d{1,2})\s*월/);
    if (withYear) return { year: Number(withYear[1]), month: Number(withYear[2]) };
    const yearMonth = text.match(/(20\d{2})[.\-_]?(0[1-9]|1[0-2])(?!\d)/);
    if (yearMonth) return { year: Number(yearMonth[1]), month: Number(yearMonth[2]) };
    const monthOnly = text.match(/(\d{1,2})\s*월/);
    if (monthOnly) return { year: null, month: Number(monthOnly[1]) };
    return null;
  }

  function confirmFileMonth(fileName) {
    const parsed = monthFromFileName(fileName);
    const year = Number(yearSelect.value);
    const month = Number(monthSelect.value);
    if (!parsed) {
      return window.confirm(`파일명에서 월을 확인하지 못했습니다.\n\n${fileName}\n\n${year}년 ${month}월 로 업로드할까요?`);
    }
    const yearMatches = parsed.year === null || parsed.year === year;
    if (yearMatches && parsed.month === month) return true;
    const fileLabel = parsed.year === null ? `${parsed.month}월` : `${parsed.year}년 ${parsed.month}월`;
    return window.confirm(
      `파일명은 ${fileLabel} 인데 선택한 조회 월은 ${year}년 ${month}월 입니다.\n\n${fileName}\n\n` +
      `선택한 ${year}년 ${month}월 로 업로드할까요?`
    );
  }

  async function uploadWorkbook(file) {
    const form = new FormData();
    form.append("file", file);
    form.append("year", String(Number(yearSelect.value)));
    form.append("month", String(Number(monthSelect.value)));

    const response = await fetch(`${TABLES_BASE}/waste-incinerator-log/import-excel`, { method: "POST", body: form });
    if (!response.ok) {
      let message = "업로드에 실패했습니다.";
      try {
        const payload = await response.json();
        if (payload?.message) message = payload.message;
      } catch (_error) { /* 기본 메시지 사용 */ }
      throw new Error(message);
    }
    return response.json();
  }

  uploadButton?.addEventListener("click", () => uploadInput?.click());
  uploadInput?.addEventListener("change", async () => {
    const file = uploadInput.files?.[0];
    uploadInput.value = "";
    if (!file) return;
    if (!(await confirmLeave())) return;
    if (!confirmFileMonth(file.name)) return;

    uploadButton.disabled = true;
    setMessage("업로드 중");
    try {
      const result = await uploadWorkbook(file);
      forgetMonth(currentMonthKey());
      await load();
      const warnings = Array.isArray(result?.warnings) ? result.warnings : [];
      setMessage(`${result?.sheets ?? 0}개 시트 반영`, "ok");
      if (warnings.length) {
        window.alert("업로드는 끝났지만 확인할 점이 있습니다.\n\n- " + warnings.join("\n- "));
      }
    } catch (error) {
      setMessage(error.message || "업로드 실패", "err");
      window.alert(error.message || "업로드에 실패했습니다.");
    } finally {
      uploadButton.disabled = false;
    }
  });

  // ── 엑셀 다운로드 ───────────────────────────────────────────
  // 원본 서식(원본 폴더의 폐합성소각로운전일지)에 선택한 월의 값을 채워 내려받는다.
  downloadButton?.addEventListener("click", async () => {
    // 저장하지 않은 값은 파일에 담기지 않으므로 먼저 확인한다.
    if (!(await confirmLeave())) return;
    downloadButton.disabled = true;
    setMessage("내려받는 중");
    try {
      const year = Number(yearSelect.value);
      const month = Number(monthSelect.value);
      const response = await fetch(`${TABLES_BASE}/waste-incinerator-log/export-excel?year=${year}&month=${month}`);
      if (!response.ok) {
        let message = "다운로드에 실패했습니다.";
        try {
          const payload = await response.json();
          if (payload?.message) message = payload.message;
        } catch (_error) { /* 기본 메시지 사용 */ }
        throw new Error(message);
      }
      const blob = await response.blob();
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `폐합성소각로 운전일지 (${year}년 ${month}월).xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      URL.revokeObjectURL(link.href);
      setMessage("내려받기 완료", "ok");
    } catch (error) {
      setMessage(error.message || "다운로드 실패", "err");
      window.alert(error.message || "다운로드에 실패했습니다.");
    } finally {
      downloadButton.disabled = false;
    }
  });

  // ── 탭 / 패널 ───────────────────────────────────────────────
  function buildTabsAndPanels() {
    tabsRoot.innerHTML = SHEETS.map((sheet) =>
      `<button type="button" class="wil-tab${sheet.key === activeKey ? " is-active" : ""}" ` +
      `data-wil-tab="${sheet.key}">${escapeHtml(sheet.title)}</button>`).join("");
    panelsRoot.innerHTML = SHEETS.map((sheet) =>
      `<section class="wil-sheet" data-wil-panel="${sheet.key}"${sheet.key === activeKey ? "" : " hidden"}>` +
      `<div class="wil-scroll"><div data-wil-body="${sheet.key}"></div></div></section>`).join("");
  }

  tabsRoot.addEventListener("click", (event) => {
    const button = event.target.closest(".wil-tab");
    if (!button || button.disabled) return;
    activeKey = button.dataset.wilTab;
    tabsRoot.querySelectorAll(".wil-tab").forEach((tab) => tab.classList.toggle("is-active", tab === button));
    panelsRoot.querySelectorAll("[data-wil-panel]").forEach((panel) => {
      panel.hidden = panel.dataset.wilPanel !== activeKey;
    });
    renderActive();
  });

  // ── 기간 선택 ───────────────────────────────────────────────
  function loadSavedPeriod() {
    const today = new Date();
    let year = String(today.getFullYear());
    let month = String(today.getMonth() + 1).padStart(2, "0");
    try {
      const raw = localStorage.getItem(PERIOD_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (/^\d{4}$/.test(String(parsed?.year || ""))) year = String(parsed.year);
        if (/^\d{2}$/.test(String(parsed?.month || ""))) month = String(parsed.month);
      }
    } catch (_error) { /* 기본값 사용 */ }
    return { year, month };
  }

  function persistPeriod() {
    localStorage.setItem(PERIOD_KEY, JSON.stringify({ year: yearSelect.value, month: monthSelect.value }));
  }

  function populatePeriodSelects() {
    const saved = loadSavedPeriod();
    const currentYear = new Date().getFullYear();
    yearSelect.innerHTML = "";
    monthSelect.innerHTML = "";
    for (let value = currentYear + 1; value >= currentYear - 5; value -= 1) {
      const option = document.createElement("option");
      option.value = String(value);
      option.textContent = `${value}년`;
      option.selected = option.value === saved.year;
      yearSelect.appendChild(option);
    }
    for (let value = 1; value <= 12; value += 1) {
      const option = document.createElement("option");
      option.value = String(value).padStart(2, "0");
      option.textContent = `${value}월`;
      option.selected = option.value === saved.month;
      monthSelect.appendChild(option);
    }
  }

  let lastPeriod = { year: "", month: "" };
  function rememberPeriod() { lastPeriod = { year: yearSelect.value, month: monthSelect.value }; }
  function restorePeriod() { yearSelect.value = lastPeriod.year; monthSelect.value = lastPeriod.month; }

  yearSelect.addEventListener("change", async () => {
    if (!(await confirmLeave())) { restorePeriod(); return; }
    persistPeriod(); rememberPeriod(); await load();
  });
  monthSelect.addEventListener("change", async () => {
    if (!(await confirmLeave())) { restorePeriod(); return; }
    persistPeriod(); rememberPeriod(); await load();
  });
  reloadButton?.addEventListener("click", async () => {
    if (!(await confirmLeave())) return;
    forgetMonth(currentMonthKey());
    await load();
  });
  saveButton?.addEventListener("click", () => { void saveAll(); });
  window.addEventListener("beforeunload", (event) => {
    if (!pending.size) return;
    event.preventDefault();
    event.returnValue = "";
  });

  let resizeTimer = null;
  window.addEventListener("resize", () => {
    if (resizeTimer) window.clearTimeout(resizeTimer);
    resizeTimer = window.setTimeout(() => renderActive(), 150);
  });

  buildTabsAndPanels();
  populatePeriodSelects();
  rememberPeriod();
  void load();
});
