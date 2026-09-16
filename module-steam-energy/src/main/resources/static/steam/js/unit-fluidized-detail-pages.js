document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "unit-fluidized-detail.html") return;

  const CELL_TABLE = "table_cell_value";
  const MAIN_TABLE = "fluidized_detail_main";
  const SUMMARY_TABLE = "fluidized_detail_summary";
  const TABLES_BASE = "/tables";
  const KNE_EXTRA_COST_KEY = "KNE_EXTRA_COST";

  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  const mainWrap = document.getElementById("detailMainWrap");
  const summaryWrap = document.getElementById("detailSummaryWrap");
  const viewToolbar = document.querySelector(".landing-page-header #detailViewToolbar") || document.getElementById("detailViewToolbar");
  const viewCaption = document.getElementById("detailViewCaption");
  const pageTitle = document.querySelector(".landing-page-copy h1");
  const filterBox = document.querySelector(".landing-filter-box");
  const filterTitle = document.querySelector(".landing-filter-title");
  const pageHeader = document.querySelector(".landing-page-header");

  const cellRowMap = new Map();
  // 새로운 섹션 기반 사용자 정의 행 (인라인 삽입).
  // section: 메인 표 내 인접 행 그룹. 각 섹션의 totalRow 셀에 자동 합산.
  const ALLOWED_SECTIONS = {
    kne:   { label: "KNE 지급비용", rows: [11, 12, 13, 14, 15, 16], totalRow: 17, aSpanRow: 11 },
    srf:   { label: "소각재",      rows: [49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63], totalRow: 64, aSpanRow: 49 },
    power: { label: "전력",        rows: [65, 66, 67, 68, 69, 70, 71, 72], totalRow: 73, aSpanRow: 65 },
    water: { label: "용수",        rows: [74, 75, 76, 77, 78, 79], totalRow: 80, aSpanRow: 74 },
    other: { label: "기타",        rows: [81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92, 93], totalRow: 95, aSpanRow: 81 },
  };
  // 동적 추가 행 캐시: monthKey -> Map("section|rowId" -> { section, rowId, label, insertAfter, days })
  const customRowsByMonth = new Map();
  let addRowSelectMode = false;
  let detailData = null;
  let loaded = false;
  let loadedMonthKey = "";
  let loadedViewKey = "";
  let activeView = "operation";
  const detailViewCache = new Map();
  const dirtyInputs = new Set();
  const pendingSaveTimers = new WeakMap();

  function syncStickyOffsets() {
    if (!pageHeader) return;
    const headerHeight = Math.ceil(pageHeader.getBoundingClientRect().height || 0);
    if (!headerHeight) return;
    document.body.style.setProperty("--detailframe-header-height", `${headerHeight}px`);
    document.body.style.setProperty("--detailframe-sticky-top", `${headerHeight + 6}px`);
    document.body.style.setProperty("--detailframe-header-offset", `${headerHeight + 42}px`);
  }

  const DETAIL_VIEWS = {
    all: {
      label: "\uC804\uCCB4 \uD45C \uC870\uD68C",
      description: "\uC804\uCCB4 \uD45C\uB97C \uC870\uD68C \uC911\uC785\uB2C8\uB2E4.",
      rows: null,
      showSummary: true,
    },
    operation: {
      label: "\uAC00\uB3D9",
      description: "\uAC00\uB3D9\uC2DC\uAC04\uBD80\uD130 KNE \uC9C0\uAE09\uBE44\uC6A9\uAE4C\uC9C0 \uD45C\uC2DC \uC911\uC785\uB2C8\uB2E4.",
      rows: [4, 17],
      showSummary: false,
    },
    consumables: {
      label: "\uC18C\uBAA8\uD488",
      description: "\uD0C4\uC0B0\uC554\uBAA8\uB284\uBD80\uD130 \uC77C \uC57D\uD488 \uC0AC\uC6A9\uB7C9\uAE4C\uC9C0 \uD45C\uC2DC \uC911\uC785\uB2C8\uB2E4.",
      rows: [18, 44],
      showSummary: false,
    },
    srf: {
      label: "SRF \uC18C\uAC01\uC7AC",
      description: "SRF\uBD80\uD130 SRF.\uD3D0\uAE30\uBB3C \uC18C\uACC4\uAE4C\uC9C0 \uD45C\uC2DC \uC911\uC785\uB2C8\uB2E4.",
      rows: [45, 64],
      showSummary: false,
    },
    "power-water": {
      label: "\uC804\uB825/\uC6A9\uC218",
      description: "\uC804\uB825\uB7C9(KW)\uBD80\uD130 \uC6A9\uC218 \uC0AC\uC6A9\uB7C9\uAE4C\uC9C0 \uD45C\uC2DC \uC911\uC785\uB2C8\uB2E4.",
      rows: [65, 80],
      showSummary: false,
    },
    other: {
      label: "\uAE30\uD0C0",
      description: "\uAE30\uD0C0 \uD56D\uBAA9\uACFC \uD558\uB2E8 \uCD1D \uBE44\uC6A9 \uAD6C\uAC04\uC744 \uD45C\uC2DC \uC911\uC785\uB2C8\uB2E4.",
      rows: [81, 96],
      showSummary: false,
    },
  };

  function initializeDetailframeChrome() {
    if (pageTitle) pageTitle.textContent = "세부 운영내역";
    if (filterBox) filterBox.setAttribute("aria-label", "조회 필터");
    if (filterTitle) filterTitle.textContent = "조회";
    if (submitButton) submitButton.textContent = "조회";

    const nav = document.querySelector(".detailframe-sidebar-nav");
    if (!nav) return;
  }

  function applyLocalizedChromeText() {
    if (pageTitle) pageTitle.textContent = "세부 운영내역";
    if (filterBox) filterBox.setAttribute("aria-label", "조회 영역");
    if (filterTitle) filterTitle.textContent = "조회";
    if (submitButton) submitButton.textContent = "조회";
  }

  function applyUnicodeChromeText() {
    if (pageTitle) pageTitle.textContent = "\uC138\uBD80 \uC6B4\uC601\uB0B4\uC5ED";
    if (filterBox) filterBox.setAttribute("aria-label", "\uC870\uD68C \uC601\uC5ED");
    if (filterTitle) filterTitle.textContent = "\uC870\uD68C";
    if (submitButton) submitButton.textContent = "\uC870\uD68C";
  }

  function syncFilterButtonLabel() {
    if (submitButton) submitButton.textContent = "\uC870\uD68C";
  }

  initializeDetailframeChrome();
  applyLocalizedChromeText();
  applyUnicodeChromeText();

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function escapeAttr(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/"/g, "&quot;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;");
  }

  function colToNumber(col) {
    let result = 0;
    for (let i = 0; i < col.length; i += 1) {
      result = result * 26 + (col.charCodeAt(i) - 64);
    }
    return result;
  }

  function numberToCol(col) {
    let value = col;
    let label = "";
    while (value > 0) {
      const remainder = (value - 1) % 26;
      label = String.fromCharCode(65 + remainder) + label;
      value = Math.floor((value - 1) / 26);
    }
    return label;
  }

  function decodeRef(ref) {
    const match = /^([A-Z]+)(\d+)$/.exec(ref);
    if (!match) return null;
    return { colLabel: match[1], col: colToNumber(match[1]), row: Number(match[2]) };
  }

  function parseRange(range) {
    const [start, end] = range.split(":").map(decodeRef);
    return { start, end };
  }

  // 메인 표에서 제거하는 행 (예: 16=추가비용). renderTable 에서 skip + merge rowspan 보정.
  const HIDDEN_MAIN_ROWS = new Set([16]);

  // 재고 그룹 { 반입행, 사용행, 재고행 }
  // 서버 FluidizedDetailWorkbookService.INVENTORY_ROW_GROUPS 와 동일해야 한다.
  const INVENTORY_GROUPS = [
    { inbound: 18, usage: 19, inventory: 20 },  // 탄산암모늄
    { inbound: 21, usage: 22, inventory: 23 },  // 청관제
    { inbound: 24, usage: 25, inventory: 26 },  // 활성탄
    { inbound: 27, usage: 28, inventory: 29 },  // 가성소다
    { inbound: 30, usage: 31, inventory: 32 },  // 소석회
    { inbound: 33, usage: 34, inventory: 35 },  // 소금
    { inbound: 36, usage: 37, inventory: 38 },  // 경유
    { inbound: 46, usage: 47, inventory: 48 },  // SRF (사용 = 소각량 9행)
  ];
  const SRF_BURN_ROW = 9;
  const SRF_USAGE_ROW = 47;

  // 유동상 운전일지에서 입력하는 행 (fluidized-daily-log-pages.js 의 DETAIL_MAP 과 동일).
  // 이 화면에서는 입력을 막고 별도 색으로 표시한다. 값 변경은 운전일지에서만 한다.
  const LOG_LINKED_ROWS = new Set([
    4, 6, 8, 9,                                     // 가동시간 · 스팀 생산량 · 슬러지 · SRF 소각량
    18, 19, 21, 22, 24, 25, 27, 28, 30, 31,         // 약품 반입/사용
    33, 34, 36, 37, 39, 40, 46,                     // 소금 · 경유 · 규사 · SRF 반입
    65, 66, 67, 68, 69, 70, 71, 72,                 // 전력 사용량
    74, 75, 76, 77, 78, 79,                         // 용수
  ]);
  // 전월 이월 재고 (서버 렌더 결과에서 역산). key = `${month}|${재고행}`, 값 없으면 null.
  const inventoryStarts = new Map();

  function buildMergeMaps(merges) {
    const mergeStarts = new Map();
    const mergedCells = new Set();
    merges.forEach((mergeRef) => {
      const [start, end] = mergeRef.split(":").map(decodeRef);
      const key = `${start.row}:${start.col}`;
      const isKnePaymentMerge = start.row === 11 && end.row === 16 && start.col === 1 && end.col === 1;
      // merge 범위에 hidden row 가 포함되면 rowspan 에서 차감
      let hiddenInRange = 0;
      for (let r = start.row; r <= end.row; r += 1) {
        if (HIDDEN_MAIN_ROWS.has(r)) hiddenInRange += 1;
      }
      mergeStarts.set(key, {
        rowspan: end.row - start.row + 1 + (isKnePaymentMerge ? 1 : 0) - hiddenInRange,
        colspan: end.col - start.col + 1,
      });
      for (let row = start.row; row <= end.row; row += 1) {
        for (let col = start.col; col <= end.col; col += 1) {
          if (row === start.row && col === start.col) continue;
          mergedCells.add(`${row}:${col}`);
        }
      }
    });
    return { mergeStarts, mergedCells };
  }

  function parseNumber(value) {
    if (value === null || value === undefined) return 0;
    const raw = String(value).replace(/,/g, "").trim();
    if (!raw) return 0;
    const parsed = Number(raw);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  function isNumericValue(value) {
    if (value === null || value === undefined) return false;
    const raw = String(value).replace(/,/g, "").trim();
    if (!raw) return false;
    return Number.isFinite(Number(raw));
  }

  function formatDisplayValue(value, digits = 0, dashZero = false) {
    if (!isNumericValue(value)) return value ?? "";
    if (parseNumber(value) === 0 && dashZero) return "-";
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }).format(parseNumber(value));
  }

  function displayDigitsForCellRef(cellRef) {
    const decoded = decodeRef(cellRef);
    if (!decoded) return 0;
    const row = decoded.row;
    const col = decoded.colLabel;
    const isDay = isDayColumn(col);

    if (col === "AI") {
      if ([12, 13, 14, 15, 16, 17, 21, 22, 23, 29, 30, 31, 32, 33, 34, 35, 36, 37, 38, 44, 45, 65, 66, 67, 68, 69, 70, 71, 72, 80].includes(row)) return 0;
      if ([6, 18, 19, 20, 24, 25, 26].includes(row)) return 1;
      return 2;
    }
    if (col === "AJ") {
      if (row === 48 || (row >= 65 && row <= 75) || row === 78 || row === 94) return 2;
      return 0;
    }
    if (col === "AK") return row === 48 ? 2 : 0;
    if (isDay || col === "AH") {
      if ([4, 5].includes(row)) return 1;
      if ([46, 47, 48].includes(row)) return 2;
      if (row >= 50 && row <= 63) return 2;
      if (row >= 81 && row <= 93) return 2;
      if (col === "AH" && [11, 20, 49, 96, 97].includes(row)) return 2;
    }
    return 0;
  }

  function zeroDisplaysAsDashForCellRef(cellRef) {
    const decoded = decodeRef(cellRef);
    if (!decoded) return false;
    const col = decoded.colLabel;
    return col !== "AJ" && col !== "AK";
  }

  function isDirectUnitPriceRow(row) {
    // row 6 (스팀 생산량(톤)) 의 단가 입력 차단 — 사용자 요구
    return [
      18, 21, 22, 24, 25, 27, 28, 30, 33, 36, 40,
      49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60, 61, 62,
      71, 72, 74, 76, 77, 78, 79, 81, 82, 83, 84, 85, 86, 87, 88, 89, 90, 91, 92,
    ].includes(row);
  }

  function isDirectCostRow(row) {
    return row === 15;
  }

  function formatValue(value) {
    if (value === null || value === undefined) return "";
    return String(value);
  }

  function isEditableCell(row, colLabel) {
    if (row === 2) return false;
    if (row === 1 || row === 3 || row === 100 || row === 101) return false;
    if (row === 10) return false;
    if (row === 43 || row === 44) return false;
    if (row === 16) return false;
    if (colLabel === "AJ") return isDirectUnitPriceRow(row);
    if (colLabel === "AK") return isDirectCostRow(row);
    if (colToNumber(colLabel) >= colToNumber("AH")) return false;
    if (row >= 102) return false;
    if (colLabel === "A" || colLabel === "B") return false;
    if (LOG_LINKED_ROWS.has(row)) return false; // 유동상 운전일지에서 입력
    if (row === 5) return false;
    if (row === 7) return false;
    if (row === 12 || row === 14 || row === 46) return false;
    if (row === 20 || row === 23 || row === 26 || row === 29 || row === 32 || row === 35 || row === 38 || row === 45 || row === 47 || row === 48) return false;
    if (row === 63 || row === 64 || row === 73 || row === 80 || row === 93 || row === 94 || row === 95 || row === 96) return false;
    return true;
  }

  function monthKey() {
    return `${yearSelect?.value || "2025"}-${monthSelect?.value || "10"}`;
  }

  function yearValue() {
    return Number(yearSelect?.value || "2025");
  }

  function monthValue() {
    return Number(monthSelect?.value || "10");
  }

  function daysInMonth() {
    const year = yearValue();
    const month = monthValue();
    return new Date(year, month, 0).getDate();
  }

  function isDayColumn(colLabel) {
    const colNumber = colToNumber(colLabel);
    return colNumber >= colToNumber("C") && colNumber <= colToNumber("AG");
  }

  function isAvailableDayColumn(colLabel) {
    if (!isDayColumn(colLabel)) return true;
    return colToNumber(colLabel) - colToNumber("C") + 1 <= daysInMonth();
  }

  function buildCellKey(tableName, rowKey, colIndex, month = monthKey()) {
    return `${month}::${tableName}::${rowKey}::${colIndex}`;
  }

  function setCellRow(row) {
    if (!row) return;
    const key = buildCellKey(row.table_name, row.row_key, row.col_index, row.month);
    cellRowMap.set(key, row);
  }

  function clearMonthRows(month) {
    Array.from(cellRowMap.keys()).forEach((key) => {
      if (key.startsWith(`${month}::`)) {
        cellRowMap.delete(key);
      }
    });
  }

  async function fetchPaged(tableName, month) {
    // 최적화: limit 5000 으로 키워 보통 단일 요청에 다 들어옴.
    // 2페이지 이상 필요할 때만 나머지를 병렬 fetch.
    const LIMIT = 5000;
    const url = (pageNo) => `${TABLES_BASE}/${tableName}?page=${pageNo}&limit=${LIMIT}&month=${encodeURIComponent(month)}`;
    const first = await fetch(url(1));
    if (!first.ok) return [];
    const firstPayload = await first.json();
    const data = (firstPayload.data || []).slice();
    const total = firstPayload.total || data.length;
    const limit = firstPayload.limit || LIMIT;
    const totalPages = Math.max(1, Math.ceil(total / limit));
    if (totalPages > 1) {
      const pageNumbers = Array.from({ length: totalPages - 1 }, (_, i) => i + 2);
      const responses = await Promise.all(pageNumbers.map((pn) => fetch(url(pn))));
      for (const res of responses) {
        if (!res.ok) continue;
        const payload = await res.json();
        data.push(...(payload.data || []));
      }
    }
    return data;
  }

  function getCellRow(tableName, rowKey, colIndex) {
    return cellRowMap.get(buildCellKey(tableName, rowKey, colIndex)) || null;
  }

  function getStoredValue(tableName, rowKey, colIndex) {
    return getCellRow(tableName, rowKey, colIndex)?.cell_value ?? "";
  }

  function getCellRawValue(section, tableName, cellRef) {
    const direct = section.cells[cellRef];
    if (direct !== undefined && direct !== null && String(direct).trim() !== "") {
      return direct;
    }
    const storedValue = getStoredValue(tableName, cellRef, 0);
    if (storedValue !== "") return storedValue;
    return "";
  }

  function getEditableRawValue(tableName, cellRef) {
    const storedValue = getStoredValue(tableName, cellRef, 0);
    return storedValue !== "" ? storedValue : "";
  }

  function getRenderedValue(section, tableName, cellRef) {
    const decoded = decodeRef(cellRef);
    if (tableName === MAIN_TABLE && decoded?.row === 16 && decoded.col >= colToNumber("C") && decoded.col <= colToNumber("AJ")) return "";
    if (tableName === MAIN_TABLE && cellRef === "AJ17") return "";
    return getCellRawValue(section, tableName, cellRef);
  }

  function buildInput(section, tableName, cellRef, rawValue, displayValue) {
    const normalizedRawValue = normalizeEditableValue(rawValue);
    const alignClass = isNumericValue(displayValue) || isNumericValue(rawValue) ? "excel-align-number" : "excel-align-text";
    const visibleValue = displayValue !== undefined && displayValue !== null && String(displayValue).trim() !== ""
      ? displayValue
      : formatDisplayValue(rawValue, displayDigitsForCellRef(cellRef), zeroDisplaysAsDashForCellRef(cellRef));
    return `<input class="detail-cell-input ${alignClass}" data-table-name="${tableName}" data-row-key="${cellRef}" data-col-index="0" data-raw-value="${escapeAttr(normalizedRawValue)}" value="${escapeAttr(visibleValue)}">`;
  }

  function normalizeEditableValue(value) {
    return String(value ?? "").replace(/,/g, "").trim();
  }

  function editableRawValue(input) {
    return document.activeElement === input ? normalizeEditableValue(input.value) : (input.dataset.rawValue ?? normalizeEditableValue(input.value));
  }

  function setLocalRenderedCell(section, cellRef, value) {
    if (!section?.cells) return;
    section.cells[cellRef] = value;
  }

  function readLocalNumber(section, tableName, cellRef) {
    const input = document.querySelector(`.detail-cell-input[data-table-name="${tableName}"][data-row-key="${cellRef}"]`);
    if (input) return parseNumber(editableRawValue(input));
    return parseNumber(getRenderedValue(section, tableName, cellRef));
  }

  function readLocalRaw(section, tableName, cellRef) {
    const input = document.querySelector(`.detail-cell-input[data-table-name="${tableName}"][data-row-key="${cellRef}"]`);
    if (input) return editableRawValue(input);
    return String(getRenderedValue(section, tableName, cellRef) ?? "").trim();
  }

  function hasLocalValue(section, tableName, cellRef) {
    return readLocalRaw(section, tableName, cellRef).replace(/,/g, "").trim() !== "";
  }

  function updateReadonlyCell(cellRef, value) {
    const cell = document.querySelector(`[data-cell-ref="${cellRef}"]`);
    if (!cell) return;
    cell.textContent = formatDisplayValue(value, displayDigitsForCellRef(cellRef), zeroDisplaysAsDashForCellRef(cellRef));
  }

  function setRenderedReadonlyCell(cellRef, value) {
    setLocalRenderedCell(detailData.main, cellRef, value ?? "");
    updateReadonlyCell(cellRef, value ?? "");
  }

  function recalcDailyOperationCell(colLabel) {
    if (!detailData?.main || !isDayColumn(colLabel) || !isAvailableDayColumn(colLabel)) return;
    const runRef = `${colLabel}4`;
    const stopRef = `${colLabel}5`;
    const steamQtyRef = `${colLabel}6`;
    const steamCostRef = `${colLabel}12`;
    const fixedCostRef = `${colLabel}14`;

    if (hasLocalValue(detailData.main, MAIN_TABLE, runRef)) {
      setRenderedReadonlyCell(stopRef, 24 - readLocalNumber(detailData.main, MAIN_TABLE, runRef));
    } else {
      setRenderedReadonlyCell(stopRef, "");
    }

    if (hasLocalValue(detailData.main, MAIN_TABLE, steamQtyRef)) {
      setRenderedReadonlyCell(
        steamCostRef,
        readLocalNumber(detailData.main, MAIN_TABLE, steamQtyRef) * readLocalNumber(detailData.main, MAIN_TABLE, `${colLabel}11`)
      );
    } else {
      setRenderedReadonlyCell(steamCostRef, "");
    }

    if (hasLocalValue(detailData.main, MAIN_TABLE, stopRef)) {
      setRenderedReadonlyCell(
        fixedCostRef,
        readLocalNumber(detailData.main, MAIN_TABLE, stopRef) * readLocalNumber(detailData.main, MAIN_TABLE, `${colLabel}13`)
      );
    } else {
      setRenderedReadonlyCell(fixedCostRef, "");
    }
  }

  function recalcRowTotal(rowNumber) {
    if (!detailData?.main) return;
    let total = 0;
    let count = 0;
    for (let day = 1; day <= daysInMonth(); day += 1) {
      const cellRef = `${numberToCol(day + 2)}${rowNumber}`;
      if (!hasLocalValue(detailData.main, MAIN_TABLE, cellRef)) continue;
      total += readLocalNumber(detailData.main, MAIN_TABLE, cellRef);
      count += 1;
    }
    setRenderedReadonlyCell(`AH${rowNumber}`, count > 0 ? total : "");
    const averageCount = rowNumber === 14 ? daysInMonth() : count;
    setRenderedReadonlyCell(`AI${rowNumber}`, count > 0 && averageCount > 0 ? total / averageCount : "");
  }

  // 서버 렌더 결과의 1일 값에서 전월 이월 재고를 역산해 보관한다.
  //  이월 = 1일 재고 − 1일 반입 + 1일 사용  (1일 재고가 비어 있으면 이월 없음 = null)
  //  자체 재계산은 이 관계를 그대로 유지하므로 다시 캡처해도 값이 달라지지 않는다.
  function captureInventoryStarts() {
    if (!detailData?.main?.cells) return;
    const firstCol = numberToCol(3); // 1일 = C열
    INVENTORY_GROUPS.forEach((group) => {
      const key = `${monthKey()}|${group.inventory}`;
      if (inventoryStarts.has(key)) return;
      const rendered = String(detailData.main.cells[`${firstCol}${group.inventory}`] ?? "").trim();
      if (!isNumericValue(rendered)) {
        inventoryStarts.set(key, null);
        return;
      }
      const inbound = parseNumber(detailData.main.cells[`${firstCol}${group.inbound}`] ?? "");
      const usage = parseNumber(detailData.main.cells[`${firstCol}${group.usage}`] ?? "");
      inventoryStarts.set(key, parseNumber(rendered) - inbound + usage);
    });
  }

  function hasInventoryActivity(group) {
    for (let day = 1; day <= daysInMonth(); day += 1) {
      const colLabel = numberToCol(day + 2);
      if (hasLocalValue(detailData.main, MAIN_TABLE, `${colLabel}${group.inbound}`)) return true;
      if (hasLocalValue(detailData.main, MAIN_TABLE, `${colLabel}${group.usage}`)) return true;
    }
    return false;
  }

  // 재고 = 전일 재고 + 반입 − 사용 (서버 applyInventoryCarryOver 와 동일 규칙)
  function recalcInventoryGroup(group) {
    if (!detailData?.main) return;
    const days = daysInMonth();
    const start = inventoryStarts.get(`${monthKey()}|${group.inventory}`) ?? null;

    if (start === null && !hasInventoryActivity(group)) {
      for (let day = 1; day <= 31; day += 1) {
        setRenderedReadonlyCell(`${numberToCol(day + 2)}${group.inventory}`, "");
      }
      setRenderedReadonlyCell(`AH${group.inventory}`, "");
      setRenderedReadonlyCell(`AI${group.inventory}`, "");
      return;
    }

    let running = start === null ? 0 : start;
    let total = 0;
    for (let day = 1; day <= days; day += 1) {
      const colLabel = numberToCol(day + 2);
      running += readLocalNumber(detailData.main, MAIN_TABLE, `${colLabel}${group.inbound}`)
        - readLocalNumber(detailData.main, MAIN_TABLE, `${colLabel}${group.usage}`);
      setRenderedReadonlyCell(`${colLabel}${group.inventory}`, running);
      total += running;
    }
    for (let day = days + 1; day <= 31; day += 1) {
      setRenderedReadonlyCell(`${numberToCol(day + 2)}${group.inventory}`, "");
    }
    setRenderedReadonlyCell(`AH${group.inventory}`, total);
    setRenderedReadonlyCell(`AI${group.inventory}`, days > 0 ? total / days : "");
  }

  // SRF 사용(47행) = 소각량(9행). 서버 applySrfUsageFromBurnRow 와 동일.
  function syncSrfUsage(colLabel) {
    if (!detailData?.main || !isDayColumn(colLabel) || !isAvailableDayColumn(colLabel)) return;
    const burnRef = `${colLabel}${SRF_BURN_ROW}`;
    const usageRef = `${colLabel}${SRF_USAGE_ROW}`;
    setRenderedReadonlyCell(usageRef, hasLocalValue(detailData.main, MAIN_TABLE, burnRef)
      ? readLocalNumber(detailData.main, MAIN_TABLE, burnRef)
      : "");
  }

  function recalcOperationCostTotals() {
    if (!detailData?.main) return;
    const operationCost = readLocalNumber(detailData.main, MAIN_TABLE, "AH12");
    const fixedCost = readLocalNumber(detailData.main, MAIN_TABLE, "AH14");
    const improvementCost = readLocalNumber(detailData.main, MAIN_TABLE, "AK15");
    const total = operationCost + fixedCost + improvementCost;
    setLocalRenderedCell(detailData.main, "AK12", operationCost);
    setLocalRenderedCell(detailData.main, "AK14", fixedCost);
    updateReadonlyCell("AK12", operationCost);
    updateReadonlyCell("AK14", fixedCost);
    setLocalRenderedCell(detailData.main, "AK17", total);
    updateReadonlyCell("AK17", total);
  }

  // 각 섹션의 소계 행 AK{totalRow} (비용 컬럼) 에 custom 행 합을 반영한다.
  // (이전: AH{totalRow} 였지만 사용자 요구로 AK 비용 컬럼으로 변경)
  // 서버 응답값 = 이미 custom 합이 포함된 상태. base = server - 현재 custom 합 으로 캐시.
  function recalcSectionTotals() {
    if (!detailData?.main) return;
    const rows = getCustomRowsForMonth();
    Object.entries(ALLOWED_SECTIONS).forEach(([section, def]) => {
      const totalRef = `AK${def.totalRow}`;
      const cell = document.querySelector(`[data-cell-ref="${totalRef}"]`);
      if (!cell) return;
      let extra = 0;
      rows.forEach((entry) => { if (entry.section === section) extra += customRowSum(entry); });
      if (cell.dataset.baseValue === undefined) {
        const serverValue = parseNumber(detailData.main.cells[totalRef] ?? "");
        cell.dataset.baseValue = String(serverValue - extra);
      }
      const base = parseNumber(cell.dataset.baseValue || "0");
      const next = base + extra;
      cell.textContent = formatDisplayValue(next, displayDigitsForCellRef(totalRef), zeroDisplaysAsDashForCellRef(totalRef));
    });
  }

  function recalcAfterInput(input) {
    if (!input || input.dataset.tableName !== MAIN_TABLE) return;
    const rowKey = input.dataset.rowKey || "";
    const decoded = decodeRef(rowKey);
    // 반입/사용을 고치면 그 품목의 재고 행을 즉시 다시 계산한다.
    if (decoded) {
      const group = INVENTORY_GROUPS.find((item) => item.inbound === decoded.row || item.usage === decoded.row);
      if (group) {
        recalcRowTotal(decoded.row);
        recalcInventoryGroup(group);
        return;
      }
      // SRF 소각량 → SRF 사용(47행) → SRF 재고(48행)
      if (decoded.row === SRF_BURN_ROW) {
        syncSrfUsage(decoded.colLabel);
        recalcRowTotal(SRF_BURN_ROW);
        recalcRowTotal(SRF_USAGE_ROW);
        recalcInventoryGroup(INVENTORY_GROUPS[INVENTORY_GROUPS.length - 1]);
        return;
      }
    }
    if (decoded && [4, 6, 11, 13].includes(decoded.row)) {
      recalcDailyOperationCell(decoded.colLabel);
      [5, 7, 12, 14].forEach(recalcRowTotal);
      recalcOperationCostTotals();
      return;
    }
    if (["AH12", "AH14", "AK15"].includes(rowKey)) {
      recalcOperationCostTotals();
    }
  }

  // ─── 사용자 정의 추가 행 (인라인) ─────────────────────────────────
  function customRowKey(section, rowId, kind, day) {
    if (kind === "label") return `custom|${section}|${rowId}|label`;
    if (kind === "insertAfter") return `custom|${section}|${rowId}|insertAfter`;
    return `custom|${section}|${rowId}|d${day}`;
  }

  function parseCustomRowKey(rowKey) {
    if (!rowKey || !rowKey.startsWith("custom|")) return null;
    const parts = rowKey.split("|");
    if (parts.length !== 4) return null;
    const [, section, rowId, suffix] = parts;
    if (!ALLOWED_SECTIONS[section]) return null;
    if (suffix === "label") return { section, rowId, kind: "label" };
    if (suffix === "insertAfter") return { section, rowId, kind: "insertAfter" };
    const dayMatch = /^d(\d{1,2})$/.exec(suffix);
    if (!dayMatch) return null;
    const day = Number(dayMatch[1]);
    if (day < 1 || day > 31) return null;
    return { section, rowId, kind: "day", day };
  }

  function getCustomRowsForMonth(month = monthKey()) {
    if (!customRowsByMonth.has(month)) customRowsByMonth.set(month, new Map());
    return customRowsByMonth.get(month);
  }

  function rebuildCustomRowsFromCells(month = monthKey()) {
    const rows = new Map();
    cellRowMap.forEach((row) => {
      if (row.month !== month) return;
      if (row.table_name !== MAIN_TABLE) return;
      const parsed = parseCustomRowKey(String(row.row_key));
      if (!parsed) return;
      const key = `${parsed.section}|${parsed.rowId}`;
      if (!rows.has(key)) {
        rows.set(key, { section: parsed.section, rowId: parsed.rowId, label: "", insertAfter: null, days: {} });
      }
      const entry = rows.get(key);
      if (parsed.kind === "label") entry.label = String(row.cell_value ?? "");
      else if (parsed.kind === "insertAfter") {
        const n = Number(String(row.cell_value ?? "").trim());
        if (Number.isFinite(n)) entry.insertAfter = n;
      } else if (parsed.kind === "day") entry.days[parsed.day] = String(row.cell_value ?? "");
    });
    customRowsByMonth.set(month, rows);
  }

  function customRowSum(entry) {
    let total = 0;
    const limit = daysInMonth();
    for (let d = 1; d <= limit; d += 1) {
      const v = entry.days[d];
      if (v === undefined || v === null || String(v).trim() === "") continue;
      const n = Number(String(v).replace(/,/g, ""));
      if (Number.isFinite(n)) total += n;
    }
    return total;
  }

  async function saveCustomCell(section, rowId, kind, day, value) {
    const rowKey = customRowKey(section, rowId, kind, day);
    const existing = getCellRow(MAIN_TABLE, rowKey, 0);
    const payload = {
      month: monthKey(),
      year_no: yearValue(),
      month_no: monthValue(),
      table_name: MAIN_TABLE,
      row_key: rowKey,
      col_index: 0,
      cell_value: String(value ?? "").trim(),
    };
    const saved = await upsertCell(payload, existing?.id);
    if (saved) setCellRow(saved);
  }

  async function deleteCustomRow(section, rowId) {
    const rows = getCustomRowsForMonth();
    rows.delete(`${section}|${rowId}`);
    const month = monthKey();
    const prefix = `custom|${section}|${rowId}|`;
    const ids = [];
    cellRowMap.forEach((row, key) => {
      if (row.month !== month) return;
      if (row.table_name !== MAIN_TABLE) return;
      if (!String(row.row_key).startsWith(prefix)) return;
      ids.push({ id: row.id, key });
    });
    for (const { id, key } of ids) {
      cellRowMap.delete(key);
      try { await fetch(`${TABLES_BASE}/${CELL_TABLE}/${id}`, { method: "DELETE" }); } catch (_) {}
    }
    // 행 제거 + 섹션 A rowspan 감소
    const tr = document.querySelector(`tr.detail-custom-row[data-custom-id="${section}|${rowId}"]`);
    if (tr) {
      tr.remove();
      bumpSectionARowspan(section, -1);
    }
    recalcSectionTotals();
  }

  function newRowId() {
    return `r${Date.now()}${Math.floor(Math.random() * 1000)}`;
  }

  function buildCustomRowTr(entry) {
    const section = entry.section;
    const days = daysInMonth();
    const cells = [];
    // A 컬럼은 섹션의 rowspan 으로 이미 점유됨 (예: A11:A17 KNE) — custom row 는 A 셀 없이 B 부터 시작.
    cells.push(`<td class="excel-cell-col-b excel-sticky-col-b excel-editable-cell excel-align-text detail-custom-label-cell"><input class="detail-cell-input excel-align-text custom-row-label" data-section="${section}" data-row-id="${entry.rowId}" value="${escapeAttr(entry.label || "")}" placeholder="항목명 입력" style="width:100%;font-weight:600;" /></td>`);
    for (let d = 1; d <= 31; d += 1) {
      const colLabel = numberToCol(d + 2);
      if (d > days) {
        cells.push(`<td class="excel-day-col excel-readonly-cell"></td>`);
        continue;
      }
      const raw = entry.days[d] ?? "";
      const display = isNumericValue(raw) ? formatDisplayValue(raw, 0, false) : raw;
      cells.push(`<td class="excel-day-col excel-editable-cell excel-align-number" data-col-label="${colLabel}"><input class="detail-cell-input excel-align-number custom-row-day" data-section="${section}" data-row-id="${entry.rowId}" data-day="${d}" data-raw-value="${escapeAttr(String(raw))}" value="${escapeAttr(display)}" /></td>`);
    }
    // AH(합계)/AI(평균)는 비워두고, AK(비용)에 행 합계 표시 — 사용자 요구
    cells.push(`<td class="excel-cell-col-ah excel-readonly-cell"></td>`);
    cells.push(`<td class="excel-cell-col-ai excel-readonly-cell"></td>`);
    cells.push(`<td class="excel-cell-col-aj excel-readonly-cell excel-align-text"><button type="button" class="detail-custom-delete" data-section="${section}" data-row-id="${entry.rowId}" title="이 행을 삭제합니다">✕ 삭제</button></td>`);
    cells.push(`<td class="excel-cell-col-ak excel-readonly-cell excel-align-number" data-custom-sum="${section}|${entry.rowId}">${escapeHtml(formatDisplayValue(customRowSum(entry), 0, false))}</td>`);
    return `<tr class="detail-custom-row" data-custom-id="${section}|${entry.rowId}" data-section="${section}" data-insert-after="${entry.insertAfter ?? ""}">${cells.join("")}</tr>`;
  }

  function insertCustomRowTr(entry) {
    const mainTable = document.querySelector(".excel-table-fluidized_detail_main tbody");
    if (!mainTable) return null;
    let anchor = null;
    mainTable.querySelectorAll("tr").forEach((tr) => {
      if (anchor) return;
      const refCell = tr.querySelector(`[data-cell-ref$="${entry.insertAfter}"]`);
      if (!refCell) return;
      const m = /\d+$/.exec(refCell.getAttribute("data-cell-ref") || "");
      if (m && Number(m[0]) === entry.insertAfter) anchor = tr;
    });
    if (!anchor) return null;
    let lastSibling = anchor;
    while (lastSibling.nextElementSibling
      && lastSibling.nextElementSibling.classList.contains("detail-custom-row")
      && Number(lastSibling.nextElementSibling.dataset.insertAfter) === entry.insertAfter) {
      lastSibling = lastSibling.nextElementSibling;
    }
    const tmp = document.createElement("tbody");
    tmp.innerHTML = buildCustomRowTr(entry);
    const tr = tmp.firstElementChild;
    lastSibling.insertAdjacentElement("afterend", tr);
    // 섹션 A 컬럼의 rowspan 1 증가 (A 셀이 rowspan 으로 점유 상태이므로 새 행이 그 안에 들어가게).
    bumpSectionARowspan(entry.section, +1);
    return tr;
  }

  function bumpSectionARowspan(section, delta) {
    const def = ALLOWED_SECTIONS[section];
    if (!def || !def.aSpanRow) return;
    const aCell = document.querySelector(`.excel-table-fluidized_detail_main [data-cell-ref="A${def.aSpanRow}"]`);
    if (!aCell) return;
    const cur = Number(aCell.getAttribute("rowspan") || "1");
    const next = Math.max(1, cur + delta);
    if (next === 1) aCell.removeAttribute("rowspan"); else aCell.setAttribute("rowspan", String(next));
  }

  function renderAllCustomRows() {
    // 메인 표가 새로 렌더된 후 호출. 모든 entry 를 DOM 에 삽입.
    document.querySelectorAll("tr.detail-custom-row").forEach((tr) => tr.remove());
    // base value 캐시 초기화
    document.querySelectorAll("[data-cell-ref]").forEach((cell) => { delete cell.dataset.baseValue; });
    const rows = Array.from(getCustomRowsForMonth().values()).sort((a, b) => {
      if ((a.insertAfter ?? 0) !== (b.insertAfter ?? 0)) return (a.insertAfter ?? 0) - (b.insertAfter ?? 0);
      return a.rowId.localeCompare(b.rowId);
    });
    rows.forEach((entry) => insertCustomRowTr(entry));
    bindCustomRowInputs();
    recalcSectionTotals();
  }

  function bindCustomRowInputs() {
    document.querySelectorAll(".custom-row-label").forEach((input) => {
      if (input.dataset.bound === "true") return;
      input.dataset.bound = "true";
      let timer;
      input.addEventListener("input", () => {
        const entry = getCustomRowsForMonth().get(`${input.dataset.section}|${input.dataset.rowId}`);
        if (entry) entry.label = input.value;
        if (timer) clearTimeout(timer);
        timer = setTimeout(() => saveCustomCell(input.dataset.section, input.dataset.rowId, "label", null, input.value), 200);
      });
      input.addEventListener("blur", () => saveCustomCell(input.dataset.section, input.dataset.rowId, "label", null, input.value));
    });
    document.querySelectorAll(".custom-row-day").forEach((input) => {
      if (input.dataset.bound === "true") return;
      input.dataset.bound = "true";
      let timer;
      const persist = () => {
        const section = input.dataset.section;
        const rowId = input.dataset.rowId;
        const day = Number(input.dataset.day);
        const value = normalizeEditableValue(input.value);
        const entry = getCustomRowsForMonth().get(`${section}|${rowId}`);
        if (entry) entry.days[day] = value;
        const sumCell = document.querySelector(`[data-custom-sum="${section}|${rowId}"]`);
        if (sumCell && entry) sumCell.textContent = formatDisplayValue(customRowSum(entry), 0, false);
        recalcSectionTotals();
        input.dataset.rawValue = value;
        saveCustomCell(section, rowId, "day", day, value);
      };
      input.addEventListener("input", () => { if (timer) clearTimeout(timer); timer = setTimeout(persist, 200); });
      input.addEventListener("focus", () => { input.value = input.dataset.rawValue ?? normalizeEditableValue(input.value); });
      input.addEventListener("blur", () => {
        if (timer) { clearTimeout(timer); timer = null; }
        persist();
        input.value = formatDisplayValue(input.value, 0, false);
      });
    });
    document.querySelectorAll(".detail-custom-delete").forEach((btn) => {
      if (btn.dataset.bound === "true") return;
      btn.dataset.bound = "true";
      btn.addEventListener("click", () => {
        if (!confirm("이 행을 삭제하시겠습니까?")) return;
        deleteCustomRow(btn.dataset.section, btn.dataset.rowId);
      });
    });
  }

  async function createCustomRow(section, insertAfter) {
    const rowId = newRowId();
    const entry = { section, rowId, label: "", insertAfter, days: {} };
    getCustomRowsForMonth().set(`${section}|${rowId}`, entry);
    await saveCustomCell(section, rowId, "label", null, "");
    await saveCustomCell(section, rowId, "insertAfter", null, String(insertAfter));
    insertCustomRowTr(entry);
    bindCustomRowInputs();
    recalcSectionTotals();
  }

  // 선택 모드 — 추가 가능한 행 하단 보더를 강조하고 클릭 시 insert.
  function enterAddRowMode() {
    if (addRowSelectMode) return;
    addRowSelectMode = true;
    document.body.classList.add("detail-add-row-mode");
    const sectionByRow = new Map();
    Object.entries(ALLOWED_SECTIONS).forEach(([section, def]) => {
      def.rows.forEach((row) => sectionByRow.set(row, section));
    });
    document.querySelectorAll(".excel-table-fluidized_detail_main tbody tr").forEach((tr) => {
      // 행 번호: 첫 셀의 data-cell-ref 에서 추출.
      let rowNumber = null;
      const refCell = tr.querySelector("[data-cell-ref]");
      if (refCell) {
        const m = /\d+$/.exec(refCell.getAttribute("data-cell-ref") || "");
        if (m) rowNumber = Number(m[0]);
      }
      if (rowNumber === null) return;
      const section = sectionByRow.get(rowNumber);
      if (!section) return;
      tr.classList.add("detail-row-addable-active");
      tr.dataset.addableSection = section;
      tr.dataset.addableRow = String(rowNumber);
      const onClick = (event) => {
        event.preventDefault();
        event.stopPropagation();
        exitAddRowMode();
        closeAddModal();
        createCustomRow(section, rowNumber);
      };
      tr.__detailAddClickHandler = onClick;
      tr.addEventListener("click", onClick, true);
    });
  }

  function exitAddRowMode() {
    if (!addRowSelectMode) return;
    addRowSelectMode = false;
    document.body.classList.remove("detail-add-row-mode");
    document.querySelectorAll(".detail-row-addable-active").forEach((tr) => {
      tr.classList.remove("detail-row-addable-active");
      delete tr.dataset.addableSection;
      delete tr.dataset.addableRow;
      if (tr.__detailAddClickHandler) {
        tr.removeEventListener("click", tr.__detailAddClickHandler, true);
        delete tr.__detailAddClickHandler;
      }
    });
    if (typeof removeSelectBanner === "function") removeSelectBanner();
  }

  // ─── 모달 ────────────────────────────────────────────────────────
  // 모달은 단순 안내. 닫혀도 선택 모드는 유지 → 사용자가 강조된 행 보더를 직접 클릭해 삽입.
  // 선택 모드 종료는: ① 행 클릭(삽입 완료) ② ESC 키 ③ 항목 추가 버튼 재클릭(토글) ④ 부동 배너의 "선택 취소".
  let modalEl = null;
  let selectBannerEl = null;

  function ensureSelectBanner() {
    if (selectBannerEl) return selectBannerEl;
    selectBannerEl = document.createElement("div");
    selectBannerEl.className = "detail-add-select-banner";
    selectBannerEl.innerHTML = `
      <span>선택 모드 — 추가할 행을 클릭하세요</span>
      <button type="button" class="detail-add-select-cancel">선택 취소 (ESC)</button>
    `;
    document.body.appendChild(selectBannerEl);
    selectBannerEl.querySelector(".detail-add-select-cancel").addEventListener("click", exitAddRowMode);
    return selectBannerEl;
  }
  function removeSelectBanner() {
    if (!selectBannerEl) return;
    selectBannerEl.remove();
    selectBannerEl = null;
  }

  function openAddModal() {
    if (addRowSelectMode) {
      // 이미 선택 모드면 토글로 종료
      exitAddRowMode();
      return;
    }
    if (modalEl) return;
    modalEl = document.createElement("div");
    modalEl.className = "detail-add-modal-backdrop";
    modalEl.innerHTML = `
      <div class="detail-add-modal" role="dialog" aria-modal="true">
        <h3>추가할 위치를 선택하세요</h3>
        <p>추가할 수 있는 칸 아래 선을 클릭해주세요. 추가 가능 영역이 강조 표시됩니다.<br>
        <span style="color:#1f4f82;font-weight:600;">닫기를 눌러도 선택 모드는 유지됩니다.</span></p>
        <div class="detail-add-modal-actions">
          <button type="button" class="detail-add-modal-close">닫기</button>
        </div>
      </div>`;
    document.body.appendChild(modalEl);
    modalEl.querySelector(".detail-add-modal-close").addEventListener("click", closeAddModal);
    modalEl.addEventListener("click", (e) => { if (e.target === modalEl) closeAddModal(); });
    enterAddRowMode();
    ensureSelectBanner();
  }
  function closeAddModal() {
    if (!modalEl) return;
    modalEl.remove();
    modalEl = null;
    // 선택 모드는 유지 — 사용자가 행 클릭 또는 ESC 로 종료.
  }

  // ESC 키로 선택 모드 종료
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && addRowSelectMode) {
      exitAddRowMode();
      closeAddModal();
    }
  });

  const addRowButton = document.getElementById("detailAddRowButton");
  addRowButton?.addEventListener("click", openAddModal);
  // ─────────────────────────────────────────────────────────────────────

  function resolveCellClasses(rowNumber, colLabel) {
    const classes = [`excel-cell-col-${colLabel.toLowerCase()}`];
    const colNumber = colToNumber(colLabel);
    if (colLabel === "A") classes.push("excel-sticky-col-a");
    if (colLabel === "B") classes.push("excel-sticky-col-b");
    if (colNumber >= colToNumber("C") && colNumber <= colToNumber("AG")) classes.push("excel-day-col");
    if (rowNumber === 1) classes.push("excel-title");
    if (rowNumber === 3 || rowNumber === 101) classes.push("excel-head");
    if ([43, 44, 63, 72, 79, 93, 94].includes(rowNumber)) classes.push("excel-subtotal");
    if ([95, 96].includes(rowNumber)) classes.push("excel-grand-total");
    if ([6, 8, 11, 17, 45, 48, 64, 73, 80].includes(rowNumber) && colLabel === "A") classes.push("excel-section");
    if (rowNumber === 39 && colLabel === "C") classes.push("excel-note");
    return classes.join(" ");
  }

  function isRowVisible(rowNumber, rowRange) {
    if (!rowRange) return true;
    return rowNumber === 3 || (rowNumber >= rowRange[0] && rowNumber <= rowRange[1]);
  }

  function renderTable(section, tableName, rowRange = null) {
    const { start, end } = parseRange(section.range);
    const { mergeStarts, mergedCells } = buildMergeMaps(section.merges || []);
    const rows = [];

    for (let row = start.row; row <= end.row; row += 1) {
      if (tableName === MAIN_TABLE && HIDDEN_MAIN_ROWS.has(row)) continue;
      if (tableName === MAIN_TABLE && row === 1) continue;
      if (row === 2) continue;
      if (!isRowVisible(row, rowRange)) continue;
      const cells = [];
      for (let col = start.col; col <= end.col; col += 1) {
        const mergeKey = `${row}:${col}`;
        if (mergedCells.has(mergeKey)) continue;

        const colLabel = numberToCol(col);
        const cellRef = `${colLabel}${row}`;
        const merge = mergeStarts.get(mergeKey);
        const attrs = [];
        if (merge?.rowspan > 1) attrs.push(`rowspan="${merge.rowspan}"`);
        if (merge?.colspan > 1) attrs.push(`colspan="${merge.colspan}"`);
        attrs.push(`data-cell-ref="${cellRef}"`);

        const value = getRenderedValue(section, tableName, cellRef);
        const editable = isEditableCell(row, colLabel) && isAvailableDayColumn(colLabel);
        const editableRawValue = editable ? getEditableRawValue(tableName, cellRef) : "";
        const cellClasses = resolveCellClasses(row, colLabel);
        const alignClass = colLabel === "A" || colLabel === "B" || !isNumericValue(value) ? "excel-align-text" : "excel-align-number";
        const content = editable
          ? buildInput(section, tableName, cellRef, editableRawValue || value, value)
          : escapeHtml(formatDisplayValue(value, displayDigitsForCellRef(cellRef), zeroDisplaysAsDashForCellRef(cellRef)));

        const linkedClass = LOG_LINKED_ROWS.has(row) && isDayColumn(colLabel) ? " excel-log-linked-cell" : "";
        const tdClasses = (editable ? `${cellClasses} ${alignClass} excel-editable-cell` : `${cellClasses} ${alignClass} excel-readonly-cell`) + linkedClass;
        cells.push(`<td class="${tdClasses}" ${attrs.join(" ")}>${content}</td>`);
      }
      rows.push(`<tr>${cells.join("")}</tr>`);
    }

    return `<table class="excel-table excel-table-${tableName}"><tbody>${rows.join("")}</tbody></table>`;
  }

  function syncViewButtons() {
    if (viewCaption) {
      viewCaption.textContent = DETAIL_VIEWS[activeView]?.description || "";
    }
    viewToolbar?.querySelectorAll("[data-detail-view]").forEach((button) => {
      button.classList.toggle("is-active", button.dataset.detailView === activeView);
    });
  }

  async function upsertCell(payload, existingId) {
    const url = existingId ? `${TABLES_BASE}/${CELL_TABLE}/${existingId}` : `${TABLES_BASE}/${CELL_TABLE}`;
    const response = await fetch(url, {
      method: existingId ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(existingId ? payload : { table_name: CELL_TABLE, ...payload }),
    });
    if (!response.ok) return null;
    return response.json();
  }

  async function saveCell(input) {
    if (!input) return;
    const tableName = input.dataset.tableName;
    const rowKey = input.dataset.rowKey;
    if (!tableName || !rowKey) return;

    const payload = {
      month: monthKey(),
      year_no: yearValue(),
      month_no: monthValue(),
      table_name: tableName,
      row_key: rowKey,
      col_index: 0,
      cell_value: editableRawValue(input),
    };

    const existing = getCellRow(tableName, rowKey, 0);
    const saved = await upsertCell(payload, existing?.id);
    if (!saved) return;

    setCellRow(saved);
    input.dataset.rawValue = normalizeEditableValue(saved.cell_value);
  }

  function queueSaveInput(input, delay = 150) {
    dirtyInputs.add(input);
    const existingTimer = pendingSaveTimers.get(input);
    if (existingTimer) clearTimeout(existingTimer);
    const timer = setTimeout(async () => {
      pendingSaveTimers.delete(input);
      if (!dirtyInputs.has(input)) return;
      dirtyInputs.delete(input);
      await saveCell(input);
    }, delay);
    pendingSaveTimers.set(input, timer);
  }

  async function flushDirtyInputs() {
    if (!dirtyInputs.size) return;
    const inputs = Array.from(dirtyInputs);
    dirtyInputs.clear();
    inputs.forEach((input) => {
      const timer = pendingSaveTimers.get(input);
      if (timer) {
        clearTimeout(timer);
        pendingSaveTimers.delete(input);
      }
    });
    for (const input of inputs) {
      await saveCell(input);
    }
  }

  function bindInputs() {
    document.querySelectorAll(".detail-cell-input").forEach((input) => {
      if (input.dataset.bound === "true") return;
      input.dataset.bound = "true";

      input.addEventListener("input", () => {
        recalcAfterInput(input);
        queueSaveInput(input);
      });

      input.addEventListener("focus", () => {
        input.value = input.dataset.rawValue ?? normalizeEditableValue(input.value);
      });

      input.addEventListener("blur", () => {
        input.dataset.rawValue = normalizeEditableValue(input.value);
        input.value = formatDisplayValue(input.value, displayDigitsForCellRef(input.dataset.rowKey || ""), zeroDisplaysAsDashForCellRef(input.dataset.rowKey || ""));
      });

      input.addEventListener("change", async () => {
        dirtyInputs.add(input);
        recalcAfterInput(input);
        await flushDirtyInputs();
        input.dataset.rawValue = normalizeEditableValue(input.value);
        input.value = formatDisplayValue(input.value, displayDigitsForCellRef(input.dataset.rowKey || ""), zeroDisplaysAsDashForCellRef(input.dataset.rowKey || ""));
        recalcAfterInput(input);
      });
    });
  }

  async function ensureLoaded() {
    const currentMonth = monthKey();
    const cacheKey = `${currentMonth}::${activeView}`;
    if (loaded && detailData?.main && loadedMonthKey === currentMonth && loadedViewKey === activeView) {
      return;
    }
    const cached = detailViewCache.get(cacheKey);
    if (cached?.main) {
      detailData = cached;
      loaded = true;
      loadedMonthKey = currentMonth;
      loadedViewKey = activeView;
      return;
    }
    let renderedResponse = null;

    try {
      renderedResponse = await fetch(
        `${TABLES_BASE}/fluidized-detail/rendered?month=${encodeURIComponent(currentMonth)}&view=${encodeURIComponent(activeView)}`
      );
    } catch (error) {
      console.error("Failed to load fluidized detail data", error);
    }

    if (renderedResponse?.ok) {
      try {
        detailData = await renderedResponse.json();
        // 서버가 다시 계산한 값이므로 전월 이월 재고 캐시를 새로 잡는다.
        INVENTORY_GROUPS.forEach((group) => inventoryStarts.delete(`${currentMonth}|${group.inventory}`));
        if (detailData?.main) detailViewCache.set(cacheKey, detailData);
      } catch (error) {
        console.error("Failed to parse fluidized detail render response", error);
        detailData = null;
      }
    }

    try {
      const rows = await fetchPaged(CELL_TABLE, currentMonth);
      clearMonthRows(currentMonth);
      rows.forEach(setCellRow);
    } catch (error) {
      console.error("Failed to load fluidized detail cell rows", error);
    }

    loaded = true;
    loadedMonthKey = currentMonth;
    loadedViewKey = activeView;
  }

  async function renderAll() {
    syncStickyOffsets();
    syncViewButtons();
    if (!loaded || loadedMonthKey !== monthKey() || loadedViewKey !== activeView) {
      mainWrap.innerHTML = `<div class="detail-loading">로딩 중</div>`;
      summaryWrap.innerHTML = "";
      await ensureLoaded();
    }
    if (!detailData?.main) {
      mainWrap.innerHTML = "";
      summaryWrap.innerHTML = "";
      return;
    }
    captureInventoryStarts();
    const selectedView = DETAIL_VIEWS[activeView] || DETAIL_VIEWS.all;
    mainWrap.innerHTML = renderTable(detailData.main, MAIN_TABLE, selectedView.rows);
    summaryWrap.innerHTML = selectedView.showSummary ? renderTable(detailData.summary, SUMMARY_TABLE) : "";
    syncViewButtons();
    bindInputs();
    rebuildCustomRowsFromCells();
    renderAllCustomRows();
    syncStickyOffsets();
  }

  yearSelect?.addEventListener("change", async () => {
    syncFilterButtonLabel();
    await flushDirtyInputs();
  });

  monthSelect?.addEventListener("change", async () => {
    syncFilterButtonLabel();
    await flushDirtyInputs();
  });

  submitButton?.addEventListener("click", async () => {
    syncFilterButtonLabel();
    await flushDirtyInputs();
    loaded = false;
    await ensureLoaded();
    await renderAll();
    syncFilterButtonLabel();
  });

  viewToolbar?.addEventListener("click", async (event) => {
    const button = event.target.closest("[data-detail-view]");
    if (!button) return;
    const nextView = button.dataset.detailView;
    if (!DETAIL_VIEWS[nextView] || nextView === activeView) return;
    await flushDirtyInputs();
    activeView = nextView;
    loaded = false;
    syncViewButtons();
    await renderAll();
  });

  window.addEventListener("resize", syncStickyOffsets);
  if (window.ResizeObserver && pageHeader) {
    const headerObserver = new ResizeObserver(syncStickyOffsets);
    headerObserver.observe(pageHeader);
  }
  syncStickyOffsets();
  renderAll();
  syncFilterButtonLabel();
});
