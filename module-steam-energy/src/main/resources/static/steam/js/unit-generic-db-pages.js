document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (!["unit-steam-usage.html", "unit-lng-cost.html"].includes(page)) return;

  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  if (!yearSelect || !monthSelect) return;

  const CELL_TABLE = "table_cell_value";
  const UNIT_TABLE = "unit_usage";
  const RAW_TABLE = "unit_usage_raw";
  const DAILY_ROW_COUNT = 31;

  const cellRows = [];
  const unitRows = [];
  const rawRows = [];
  const cellIndex = new Map();
  const unitIndex = new Map();
  const rawIndex = new Map();
  let loaded = false;

  const CELL_TABLE_NAMES_BY_PAGE = {
    "unit-lng-cost.html": ["lng_m_boiler", "lng_m_burner"],
    "unit-steam-usage.html": [
      "usage_m_pm2",
      "usage_m_pm3a",
      "usage_m_pm3b",
      "usage_m_tm",
      "usage_m_paper",
      "usage_m_tissue",
      "usage_m_other",
    ],
  };

  function cellKey(tableName, month, rowKey, colIndex) {
    return `${tableName}|${month}|${rowKey}|${Number(colIndex)}`;
  }

  function unitKey(month, day, machineNo) {
    return `${month}|${normalizeDayValue(day)}|${machineNo}`;
  }

  function rawKey(month, day) {
    return `${month}|${normalizeDayValue(day)}`;
  }

  function indexCellRow(row) {
    if (!row) return;
    cellIndex.set(cellKey(row.table_name, row.month, row.row_key, row.col_index), row);
  }

  function indexUnitRow(row) {
    if (!row) return;
    unitIndex.set(unitKey(row.month, row.day, row.machine_no), row);
  }

  function indexRawRow(row) {
    if (!row) return;
    rawIndex.set(rawKey(row.month, row.day), row);
  }

  function cellTableNamesParam() {
    const names = CELL_TABLE_NAMES_BY_PAGE[page];
    return names && names.length ? names.join(",") : "";
  }
  const loadedCellYears = new Set();
  const loadedUnitYears = new Set();
  const loadedRawYears = new Set();
  const loadedCellMonths = new Set();
  const loadedUnitMonths = new Set();
  const loadedRawMonths = new Set();
  const dirtyInputs = new Set();
  const pendingSaveTimers = new WeakMap();
  let yearlyRenderTimer = null;

  const MONTHLY_READONLY = {
    "unit-steam-usage.html": {
      usage_m_pm2: [0, 2],
      usage_m_pm3a: [0, 2],
      usage_m_pm3b: [0, 2, 3, 4, 6],
      usage_m_tm: [0, 2, 3, 4, 6, 7, 9, 10, 11, 12],
      usage_m_paper: [0, 1, 2],
      usage_m_tissue: [0, 1, 2],
      usage_m_other: [0],
    },
    "unit-lng-cost.html": {
      lng_m_boiler: [4, 6],
      lng_m_burner: [4, 5, 7, 8],
    },
  };

  function monthKey(year = yearSelect.value, month = monthSelect.value) {
    return `${year}-${month}`;
  }

  function monthMeta(year = yearSelect.value, month = monthSelect.value) {
    return {
      month: `${year}-${month}`,
      year_no: Number(year),
      month_no: Number(month),
    };
  }

  function daysInMonth(year = Number(yearSelect.value), month = Number(monthSelect.value)) {
    return new Date(year, month, 0).getDate();
  }

  function normalizeDayValue(day) {
    const numeric = Number(day);
    if (Number.isFinite(numeric) && numeric > 0) {
      return String(numeric);
    }
    return String(day ?? "").trim();
  }

  function parseNumber(value) {
    if (value === null || value === undefined) return 0;
    const cleaned = String(value).replace(/[% ,]/g, "").trim();
    if (!cleaned) return 0;
    const number = Number(cleaned);
    return Number.isFinite(number) ? number : 0;
  }

  function formatInt(value) {
    if (!Number.isFinite(value)) return "";
    return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0, minimumFractionDigits: 0 }).format(value);
  }

  function formatDec(value, digits = 2) {
    if (!Number.isFinite(value)) return "";
    return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(value);
  }

  function formatPercent(value, digits = 1) {
    if (!Number.isFinite(value)) return "";
    return `${new Intl.NumberFormat("ko-KR", { maximumFractionDigits: digits, minimumFractionDigits: digits }).format(value)}%`;
  }

  function usageRate(steam, run) {
    return run > 0 ? steam / run : null;
  }

  function escapeAttr(value) {
    return String(value ?? "").replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function normalizeEditableValue(value) {
    return String(value ?? "").replace(/,/g, "").trim();
  }

  function formatEditableValue(value) {
    const normalized = normalizeEditableValue(value);
    if (!normalized) return "";
    if (!/^[-+]?\d*(\.\d+)?$/.test(normalized)) return String(value ?? "");
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(Number(normalized));
  }

  function editableRawValue(input) {
    return document.activeElement === input ? normalizeEditableValue(input.value) : (input.dataset.rawValue ?? normalizeEditableValue(input.value));
  }

  function isReadonly(tableName, rowMode, colIndex) {
    if (rowMode === "yearly") return true;
    return ((MONTHLY_READONLY[page] || {})[tableName] || []).includes(colIndex);
  }

  async function fetchPaged(tableName, month = "", range = {}, extra = {}) {
    const data = [];
    let pageNo = 1;
    let totalPages = 1;
    while (pageNo <= totalPages) {
      const params = new URLSearchParams({ page: String(pageNo), limit: "2000" });
      if (month) params.set("month", month);
      if (range.fromMonth) params.set("fromMonth", range.fromMonth);
      if (range.toMonth) params.set("toMonth", range.toMonth);
      if (extra.tableNames) params.set("tableNames", extra.tableNames);
      const response = await fetch(`tables/${tableName}?${params.toString()}`);
      if (!response.ok) break;
      const payload = await response.json();
      const pageData = payload.data || [];
      const total = payload.total || pageData.length;
      const limit = payload.limit || 2000;
      totalPages = Math.max(1, Math.ceil(total / limit));
      data.push(...pageData);
      pageNo += 1;
    }
    return data;
  }

  function rowIdentity(tableName, row) {
    if (tableName === CELL_TABLE) return `${row.table_name}::${row.month}::${row.row_key}::${row.col_index}`;
    if (tableName === UNIT_TABLE) return `${row.month}::${row.day}::${row.machine_no}`;
    return `${row.month}::${row.day}`;
  }

  function mergeRows(targetRows, tableName, rows) {
    if (!rows.length) return;
    const indexMap = new Map(targetRows.map((row, index) => [rowIdentity(tableName, row), index]));
    rows.forEach((row) => {
      const key = rowIdentity(tableName, row);
      const existingIndex = indexMap.get(key);
      if (existingIndex === undefined) {
        indexMap.set(key, targetRows.length);
        targetRows.push(row);
      } else {
        targetRows[existingIndex] = row;
      }
      if (tableName === CELL_TABLE) indexCellRow(row);
      else if (tableName === UNIT_TABLE) indexUnitRow(row);
      else if (tableName === RAW_TABLE) indexRawRow(row);
    });
  }

  async function ensureSteamMonthLoaded(targetMonth = monthKey()) {
    if (page !== "unit-steam-usage.html") return;
    const cellNames = cellTableNamesParam();
    const tasks = [];
    if (!loadedCellMonths.has(targetMonth)) {
      tasks.push(fetchPaged(CELL_TABLE, targetMonth, {}, { tableNames: cellNames }).then((rows) => {
        mergeRows(cellRows, CELL_TABLE, rows);
        loadedCellMonths.add(targetMonth);
      }));
    }
    if (!loadedUnitMonths.has(targetMonth)) {
      tasks.push(fetchPaged(UNIT_TABLE, targetMonth).then((rows) => {
        mergeRows(unitRows, UNIT_TABLE, rows);
        loadedUnitMonths.add(targetMonth);
      }));
    }
    if (!loadedRawMonths.has(targetMonth)) {
      tasks.push(fetchPaged(RAW_TABLE, targetMonth).then((rows) => {
        mergeRows(rawRows, RAW_TABLE, rows);
        loadedRawMonths.add(targetMonth);
      }));
    }
    await Promise.all(tasks);
  }

  async function ensureLngYearLoaded(year = yearSelect.value) {
    if (page !== "unit-lng-cost.html") return;
    if (loadedCellYears.has(String(year))) return;
    const rows = await fetchPaged(
      CELL_TABLE,
      "",
      { fromMonth: `${year}-01`, toMonth: `${year}-12` },
      { tableNames: cellTableNamesParam() }
    );
    mergeRows(cellRows, CELL_TABLE, rows);
    loadedCellYears.add(String(year));
  }

  async function ensureSteamYearLoaded(year = yearSelect.value) {
    if (page !== "unit-steam-usage.html") return;
    const yearKey = String(year);
    const range = { fromMonth: `${yearKey}-01`, toMonth: `${yearKey}-12` };

    const cellNames = cellTableNamesParam();
    const tasks = [];
    if (!loadedCellYears.has(yearKey)) {
      tasks.push(fetchPaged(CELL_TABLE, "", range, { tableNames: cellNames }).then((rows) => {
        mergeRows(cellRows, CELL_TABLE, rows);
        loadedCellYears.add(yearKey);
      }));
    }
    if (!loadedUnitYears.has(yearKey)) {
      tasks.push(fetchPaged(UNIT_TABLE, "", range).then((rows) => {
        mergeRows(unitRows, UNIT_TABLE, rows);
        loadedUnitYears.add(yearKey);
      }));
    }
    if (!loadedRawYears.has(yearKey)) {
      tasks.push(fetchPaged(RAW_TABLE, "", range).then((rows) => {
        mergeRows(rawRows, RAW_TABLE, rows);
        loadedRawYears.add(yearKey);
      }));
    }
    await Promise.all(tasks);
  }

  function getCellRow(tableName, month, rowKey, colIndex) {
    return cellIndex.get(cellKey(tableName, month, rowKey, colIndex)) || null;
  }

  function getCellValue(tableName, month, rowKey, colIndex) {
    return getCellRow(tableName, month, rowKey, colIndex)?.cell_value ?? "";
  }

  async function upsertCell(payload, existingId) {
    const url = existingId ? `tables/${CELL_TABLE}/${existingId}` : `tables/${CELL_TABLE}`;
    const method = existingId ? "PATCH" : "POST";
    const response = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(existingId ? payload : { table_name: CELL_TABLE, ...payload }),
    });
    if (!response.ok) return null;
    return response.json();
  }

  async function saveCell(input) {
    if (!input || input.readOnly || input.classList.contains("calc-input")) return;
    const tableName = input.dataset.tableName;
    const rowKey = input.dataset.rowKey;
    const colIndex = Number(input.dataset.colIndex);
    if (!tableName || !rowKey || !Number.isFinite(colIndex)) return;

    const payload = {
      ...monthMeta(),
      table_name: tableName,
      row_key: rowKey,
      col_index: colIndex,
      cell_value: editableRawValue(input),
    };
    const existing = getCellRow(tableName, payload.month, rowKey, colIndex);
    const saved = await upsertCell(payload, existing?.id);
    if (!saved) return;

    const index = cellRows.findIndex((row) => row.id === saved.id);
    if (index >= 0) cellRows[index] = saved;
    else cellRows.push(saved);
    indexCellRow(saved);
    input.dataset.rawValue = normalizeEditableValue(saved.cell_value);
  }

  function findUnit(month, day, machine) {
    return unitIndex.get(unitKey(month, day, machine)) || null;
  }

  function findRaw(month, day) {
    return rawIndex.get(rawKey(month, day)) || null;
  }

  function steamMetric(month, day) {
    const pm2 = findUnit(month, day, "PM-2") || {};
    const pm3 = findUnit(month, day, "PM-3") || {};
    const tm3 = findUnit(month, day, "TM-3") || {};
    const tm4 = findUnit(month, day, "TM-4") || {};
    const tm5 = findUnit(month, day, "TM-5") || {};
    const raw = findRaw(month, day) || {};

    const pm2Steam = Number(pm2.main_steam || 0) + Number(pm2.coater_steam || 0);
    const pm3McSteam = Number(pm3.main_steam || 0) + Number(pm3.coater_steam || 0);
    const disperser = Number(raw.disperser_total || 0);
    const ventilation = Number(pm3.ventilation_steam || 0);
    const tm3Steam = Number(tm3.steam || 0);
    const tm4Steam = Number(tm4.steam || 0);
    const tm5Steam = Number(tm5.steam || 0);
    const paperSteam = pm2Steam + pm3McSteam + disperser + ventilation;
    const tissueSteam = tm3Steam + tm4Steam + tm5Steam;

    return { pm2Steam, pm3McSteam, disperser, ventilation, tm3Steam, tm4Steam, tm5Steam, paperSteam, tissueSteam, totalSteam: paperSteam + tissueSteam };
  }

  function buildInput(tableName, rowMode, rowKey, colIndex, value, forceReadonly = false) {
    const ro = forceReadonly || isReadonly(tableName, rowMode, colIndex);
    const displayValue = ro ? value : formatEditableValue(value);
    return `<input class="table-edit-input unit-align-number${ro ? " calc-input" : ""}" data-table-name="${tableName}" data-row-mode="${rowMode}" data-row-key="${rowKey}" data-col-index="${colIndex}" data-raw-value="${escapeAttr(normalizeEditableValue(value))}" value="${escapeAttr(displayValue)}"${ro ? ' readonly tabindex="-1"' : ""}>`;
  }

  function isNumericText(value) {
    const text = String(value ?? "").replace(/[% ,]/g, "").trim();
    return /^-?\d+(\.\d+)?$/.test(text);
  }

  function alignDataCell(cell) {
    if (!(cell instanceof HTMLElement)) return;
    cell.classList.remove("unit-align-number", "unit-align-text");
    if (cell.cellIndex === 0) {
      cell.classList.add("unit-align-text");
      return;
    }
    const table = cell.closest("table");
    const headerText = table?.querySelector(`thead th:nth-child(${cell.cellIndex + 1})`)?.textContent?.trim() || "";
    const isTextColumn = /비고|구분|월|일/.test(headerText);
    const input = cell.querySelector("input");
    if (input instanceof HTMLInputElement) {
      input.classList.remove("unit-align-number", "unit-align-text");
      const className = isTextColumn ? "unit-align-text" : "unit-align-number";
      input.classList.add(className);
      cell.classList.add(className);
      return;
    }
    cell.classList.add(!isTextColumn && isNumericText(cell.textContent) ? "unit-align-number" : "unit-align-text");
  }

  function applyDataAlignment() {
    document.querySelectorAll(".data-table th").forEach((cell) => {
      cell.classList.remove("unit-align-number");
      cell.classList.add("unit-align-text");
    });
    document.querySelectorAll(".data-table td").forEach(alignDataCell);
  }

  function getLeafColumnCount(table) {
    const headerRows = Array.from(table.querySelectorAll("thead tr"));
    if (headerRows.length <= 1) return table.querySelectorAll("thead th").length;
    const bottomCount = headerRows[headerRows.length - 1].querySelectorAll("th").length;
    const rowSpans = headerRows
      .slice(0, -1)
      .reduce((count, row) => count + Array.from(row.querySelectorAll("th")).filter((th) => Number(th.rowSpan || 1) > 1).length, 0);
    return bottomCount + rowSpans;
  }

  function summaryDigits(tableName, colIndex) {
    if (tableName === "usage_m_pm2" && colIndex === 2) return 2;
    if (tableName === "usage_m_pm3a" && colIndex === 2) return 2;
    if (tableName === "usage_m_pm3b" && [2, 5].includes(colIndex)) return 2;
    if (tableName === "usage_m_tm" && [2, 5, 8, 10, 11].includes(colIndex)) return 2;
    if (tableName === "usage_m_paper" && [1, 2].includes(colIndex)) return 2;
    if (tableName === "usage_m_tissue" && [1, 2].includes(colIndex)) return 2;
    if (tableName === "lng_m_boiler" && colIndex === 6) return 2;
    if (tableName === "lng_m_burner" && [7, 8].includes(colIndex)) return 2;
    return 0;
  }

  function buildSummaryRow(tableName, rowKey, label, colCount) {
    const cells = [`<td>${label}</td>`];
    for (let col = 0; col < colCount; col += 1) {
      cells.push(`<td>${buildInput(tableName, "daily", rowKey, col, "", true)}</td>`);
    }
    return `<tr data-row-key="${rowKey}" class="summary-row">${cells.join("")}</tr>`;
  }

  function getInput(row, col) {
    return row.querySelector(`input[data-col-index="${col}"]`);
  }

  function setCalc(row, col, value, digits = 0) {
    const input = getInput(row, col);
    if (!input) return;
    input.value = value === "" || value === null || value === undefined ? "" : (digits > 0 ? formatDec(value, digits) : formatInt(value));
    input.classList.add("unit-align-number");
    alignDataCell(input.closest("td"));
  }

  function monthlyUsageValue(tableName, rowKey, colIndex) {
    const month = monthKey();
    const stored = getCellValue(tableName, month, rowKey, colIndex);
    const m = steamMetric(month, rowKey);

    if (tableName === "usage_m_pm2" && colIndex === 0) return m.pm2Steam || "";
    if (tableName === "usage_m_pm3a" && colIndex === 0) return m.pm3McSteam || "";
    if (tableName === "usage_m_pm3b" && colIndex === 0) return m.disperser || "";
    if (tableName === "usage_m_pm3b" && colIndex === 3) return m.ventilation || "";
    if (tableName === "usage_m_tm" && colIndex === 0) return m.tm3Steam || "";
    if (tableName === "usage_m_tm" && colIndex === 3) return m.tm4Steam || "";
    if (tableName === "usage_m_tm" && colIndex === 6) return m.tm5Steam || "";
    if (tableName === "usage_m_tm" && colIndex === 9) return m.tissueSteam || "";
    if (tableName === "usage_m_paper" && colIndex === 0) return m.paperSteam || "";
    if (tableName === "usage_m_tissue" && colIndex === 0) return m.tissueSteam || "";
    if (tableName === "usage_m_other" && colIndex === 0) return m.totalSteam || "";
    return stored;
  }

  function yearlyUsageAggregate(monthNo) {
    const month = `${yearSelect.value}-${String(monthNo).padStart(2, "0")}`;
    const a = { pm2Steam: 0, pm3McSteam: 0, disperser: 0, ventilation: 0, tm3Steam: 0, tm4Steam: 0, tm5Steam: 0, paperSteam: 0, tissueSteam: 0, totalSteam: 0, pm2Run: 0, pm3Run: 0, dispRun: 0, ventRun: 0, tm3Run: 0, tm4Run: 0, tm5Run: 0 };
    const maxDays = daysInMonth(Number(yearSelect.value), monthNo);
    for (let day = 1; day <= maxDays; day += 1) {
      const key = String(day).padStart(2, "0");
      const m = steamMetric(month, key);
      a.pm2Steam += m.pm2Steam;
      a.pm3McSteam += m.pm3McSteam;
      a.disperser += m.disperser;
      a.ventilation += m.ventilation;
      a.tm3Steam += m.tm3Steam;
      a.tm4Steam += m.tm4Steam;
      a.tm5Steam += m.tm5Steam;
      a.paperSteam += m.paperSteam;
      a.tissueSteam += m.tissueSteam;
      a.totalSteam += m.totalSteam;
      a.pm2Run += parseNumber(getCellValue("usage_m_pm2", month, key, 1));
      a.pm3Run += parseNumber(getCellValue("usage_m_pm3a", month, key, 1));
      a.dispRun += parseNumber(getCellValue("usage_m_pm3b", month, key, 1));
      a.ventRun += parseNumber(getCellValue("usage_m_pm3b", month, key, 5));
      a.tm3Run += parseNumber(getCellValue("usage_m_tm", month, key, 1));
      a.tm4Run += parseNumber(getCellValue("usage_m_tm", month, key, 4));
      a.tm5Run += parseNumber(getCellValue("usage_m_tm", month, key, 7));
    }
    a.pm2Unit = a.pm2Run > 0 ? a.pm2Steam / a.pm2Run : 0;
    a.pm3Unit = a.pm3Run > 0 ? a.pm3McSteam / a.pm3Run : 0;
    a.dispUnit = a.dispRun > 0 ? a.disperser / a.dispRun : 0;
    a.ventUnit = a.ventRun > 0 ? a.ventilation / a.ventRun : 0;
    a.paperUnit = a.pm2Unit + a.pm3Unit + a.dispUnit;
    a.paperAvgRun = a.paperUnit > 0 ? a.paperSteam / a.paperUnit : 0;
    a.tm3Unit = a.tm3Run > 0 ? a.tm3Steam / a.tm3Run : 0;
    a.tm4Unit = a.tm4Run > 0 ? a.tm4Steam / a.tm4Run : 0;
    a.tm5Unit = a.tm5Run > 0 ? a.tm5Steam / a.tm5Run : 0;
    a.tissueRun = a.tm3Run + a.tm4Run + a.tm5Run;
    a.tissueUnit = a.tm3Unit + a.tm4Unit + a.tm5Unit;
    return a;
  }

  function yearlyUsageRatio(monthNo) {
    const a = yearlyUsageAggregate(monthNo);
    const total = a.totalSteam || 0;
    const ratioOf = (value) => (total > 0 ? (value / total) * 100 : 0);
    return {
      pm2: ratioOf(a.pm2Steam),
      pm3: ratioOf(a.pm3McSteam + a.disperser + a.ventilation),
      tm3: ratioOf(a.tm3Steam),
      tm4: ratioOf(a.tm4Steam),
      tm5: ratioOf(a.tm5Steam),
      total: ratioOf(a.totalSteam),
    };
  }

  function sumMonth(tableName, monthNo, colIndex) {
    const month = `${yearSelect.value}-${String(monthNo).padStart(2, "0")}`;
    let total = 0;
    const maxDays = daysInMonth(Number(yearSelect.value), monthNo);
    for (let day = 1; day <= maxDays; day += 1) {
      total += parseNumber(getCellValue(tableName, month, String(day).padStart(2, "0"), colIndex));
    }
    return total;
  }

  function firstMonthValue(tableName, monthNo, colIndex) {
    const month = `${yearSelect.value}-${String(monthNo).padStart(2, "0")}`;
    const maxDays = daysInMonth(Number(yearSelect.value), monthNo);
    for (let day = 1; day <= maxDays; day += 1) {
      const value = getCellValue(tableName, month, String(day).padStart(2, "0"), colIndex);
      if (String(value ?? "").trim() !== "") return value;
    }
    return "";
  }

  function yearlyValues(tableName, monthNo) {
    if (page === "unit-steam-usage.html") {
      const a = yearlyUsageAggregate(monthNo);
      if (tableName === "usage_y_pm2") return [a.pm2Steam, a.pm2Run, a.pm2Unit, ""];
      if (tableName === "usage_y_pm3a") return [a.pm3McSteam, a.pm3Run, a.pm3Unit, ""];
      if (tableName === "usage_y_pm3b") return [a.disperser, a.dispRun, a.dispUnit, a.ventilation, a.ventRun, a.ventUnit];
      if (tableName === "usage_y_tm") return [a.tm3Steam, a.tm3Run, a.tm3Unit, a.tm4Steam, a.tm4Run, a.tm4Unit, a.tm5Steam, a.tm5Run, a.tm5Unit, a.tissueSteam, a.tissueRun, a.tissueUnit];
      if (tableName === "usage_y_paper") return [a.paperSteam, a.paperAvgRun, a.paperUnit];
      if (tableName === "usage_y_tissue") return [a.tissueSteam, a.tissueRun, a.tissueUnit];
      return [];
    }

    if (tableName === "lng_y_boiler") {
      const c0 = sumMonth("lng_m_boiler", monthNo, 0);
      const c1 = sumMonth("lng_m_boiler", monthNo, 1);
      const c2 = sumMonth("lng_m_boiler", monthNo, 2);
      const c3 = sumMonth("lng_m_boiler", monthNo, 3);
      const total = c0 + c1 + c2 + c3;
      const price = firstMonthValue("lng_m_boiler", monthNo, 5);
      return [c0, c1, c2, c3, total, price, total * parseNumber(price) / 1000000];
    }
    if (tableName === "lng_y_burner") {
      const pm3 = sumMonth("lng_m_burner", monthNo, 0);
      const tm3 = sumMonth("lng_m_burner", monthNo, 1);
      const tm4 = sumMonth("lng_m_burner", monthNo, 2);
      const tm5 = sumMonth("lng_m_burner", monthNo, 3);
      const tissue = tm3 + tm4 + tm5;
      const total = pm3 + tissue;
      const price = firstMonthValue("lng_m_burner", monthNo, 6);
      const cost = total * parseNumber(price) / 100000000;
      const totalCost = cost + parseNumber(yearlyValues("lng_y_boiler", monthNo)[6]);
      return [pm3, tm3, tm4, tm5, tissue, total, price, cost, totalCost];
    }
    if (tableName === "lng_y_mix") {
      const pm2 = sumMonth("lng_m_mix", monthNo, 0);
      const pm3 = sumMonth("lng_m_mix", monthNo, 1);
      const tm3 = sumMonth("lng_m_mix", monthNo, 3);
      const tm4 = sumMonth("lng_m_mix", monthNo, 4);
      const tm5 = sumMonth("lng_m_mix", monthNo, 5);
      const pmTotal = pm2 + pm3;
      const tmTotal = tm3 + tm4 + tm5;
      return [pm2, pm3, pmTotal, tm3, tm4, tm5, tmTotal, pmTotal + tmTotal];
    }
    return [];
  }

  function computeAutoTicks(seriesList, tickCount = 6) {
    const allValues = seriesList.flatMap((series) => series.values || []);
    const maxValue = Math.max(0, ...allValues.map((value) => Number(value) || 0));
    if (maxValue <= 0) {
      return [0, 1, 2, 3, 4, 5];
    }
    const roughStep = maxValue / Math.max(1, tickCount - 1);
    const magnitude = 10 ** Math.floor(Math.log10(roughStep));
    const normalized = roughStep / magnitude;
    let step = magnitude;
    if (normalized > 5) step = 10 * magnitude;
    else if (normalized > 2) step = 5 * magnitude;
    else if (normalized > 1) step = 2 * magnitude;
    const top = Math.ceil(maxValue / step) * step;
    const ticks = [];
    for (let value = 0; value <= top; value += step) ticks.push(value);
    if (ticks.length < 2) ticks.push(step);
    return ticks;
  }

  function buildChartPath(values, min, max, width, height, leftPad, topPad, chartWidth, chartHeight) {
    return values.map((value, index) => {
      const x = leftPad + (index * chartWidth) / Math.max(1, values.length - 1);
      const ratio = max === min ? 0 : ((Number(value) || 0) - min) / (max - min);
      const y = topPad + chartHeight - ratio * chartHeight;
      return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    }).join(" ");
  }

  function formatChartValue(value) {
    return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 }).format(Number(value) || 0);
  }


  function positionChartTooltip(container, tooltip, event) {
    if (!container || !tooltip) return;
    const bounds = container.getBoundingClientRect();
    const tooltipWidth = tooltip.offsetWidth || 0;
    const tooltipHeight = tooltip.offsetHeight || 0;
    const minLeft = 8;
    const minTop = 8;
    const maxLeft = Math.max(minLeft, bounds.width - tooltipWidth - 8);
    const maxTop = Math.max(minTop, bounds.height - tooltipHeight - 8);
    const nextLeft = Math.min(maxLeft, Math.max(minLeft, event.clientX - bounds.left + 12));
    const nextTop = Math.min(maxTop, Math.max(minTop, event.clientY - bounds.top - tooltipHeight - 12));
    tooltip.style.left = `${nextLeft}px`;
    tooltip.style.top = `${nextTop}px`;
  }
  function renderLineChart(container, config) {
    if (!container) return;
    const width = 1180;
    const height = 400;
    const leftPad = 72;
    const rightPad = 24;
    const topPad = 56;
    const bottomPad = 54;
    const chartWidth = width - leftPad - rightPad;
    const chartHeight = height - topPad - bottomPad;
    const labels = config.labels || [];
    const ticks = config.ticks?.length ? config.ticks : computeAutoTicks(config.series);
    const min = 0;
    const max = ticks[ticks.length - 1];

    const grid = ticks.map((tick) => {
      const ratio = max === min ? 0 : (tick - min) / (max - min);
      const y = topPad + chartHeight - ratio * chartHeight;
      return `<g><line x1="${leftPad}" y1="${y}" x2="${width - rightPad}" y2="${y}" class="chart-grid-line" /><text x="${leftPad - 10}" y="${y + 4}" class="chart-axis-label" text-anchor="end">${tick}</text></g>`;
    }).join("");

    const xAxisLabels = labels.map((label, index) => {
      const x = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
      return `<text x="${x}" y="${height - 18}" class="chart-axis-label chart-x-axis-label" text-anchor="middle">${label}</text>`;
    }).join("");

    const seriesPaths = config.series.map((item) => {
      const path = buildChartPath(item.values, min, max, width, height, leftPad, topPad, chartWidth, chartHeight);
      return `<path d="${path}" class="chart-line" style="stroke:${item.color}" />`;
    }).join("");

    const points = config.series.map((item) => (
      item.values.map((value, index) => {
        const x = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
        const ratio = max === min ? 0 : ((Number(value) || 0) - min) / (max - min);
        const y = topPad + chartHeight - ratio * chartHeight;
        return `<circle cx="${x}" cy="${y}" r="3.5" class="chart-point" style="fill:${item.color}" />`;
      }).join("")
    )).join("");

    const hoverColumns = labels.map((_, index) => {
      const previousX = index === 0 ? leftPad : leftPad + ((index - 1) * chartWidth) / Math.max(1, labels.length - 1);
      const currentX = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
      const nextX = index === labels.length - 1 ? leftPad + chartWidth : leftPad + ((index + 1) * chartWidth) / Math.max(1, labels.length - 1);
      const startX = index === 0 ? currentX - ((nextX - currentX) / 2) : (previousX + currentX) / 2;
      const endX = index === labels.length - 1 ? currentX + ((currentX - previousX) / 2) : (currentX + nextX) / 2;
      return `<rect x="${startX}" y="${topPad}" width="${Math.max(16, endX - startX)}" height="${chartHeight}" class="chart-hover-column" data-index="${index}" />`;
    }).join("");

    const legend = config.series.map((item, index) => {
      const x = 18 + (index % 3) * 220;
      const y = 10 + Math.floor(index / 3) * 20;
      return `<g transform="translate(${x}, ${y})"><line x1="0" y1="8" x2="18" y2="8" class="chart-line" style="stroke:${item.color}" /><text x="24" y="12" class="chart-legend-label">${item.label}</text></g>`;
    }).join("");

    container.innerHTML = `
      <div class="chart-tooltip" hidden></div>
      <svg class="chart-svg" viewBox="0 0 ${width} ${height}" preserveAspectRatio="xMidYMid meet" aria-label="${config.title}">
        <g>${grid}</g>
        <line x1="${leftPad}" y1="${topPad + chartHeight}" x2="${width - rightPad}" y2="${topPad + chartHeight}" class="chart-axis-line" />
        <line x1="${leftPad}" y1="${topPad}" x2="${leftPad}" y2="${topPad + chartHeight}" class="chart-axis-line" />
        <g>${seriesPaths}</g>
        <g>${points}</g>
        <g>${xAxisLabels}</g>
        <g>${hoverColumns}</g>
        <g>${legend}</g>
      </svg>
    `;

    const tooltip = container.querySelector(".chart-tooltip");
    container.querySelectorAll(".chart-hover-column").forEach((target) => {
      target.addEventListener("mouseenter", () => {
        const index = Number(target.dataset.index);
        const rows = config.series.map((item) => (
          `<div class="chart-tooltip-row"><span class="chart-tooltip-swatch" style="background:${item.color}"></span><span>${item.label}</span><strong>${formatChartValue(item.values[index])}</strong></div>`
        )).join("");
        tooltip.innerHTML = `<div class="chart-tooltip-title">${labels[index]}</div>${rows}`;
        tooltip.hidden = false;
      });
      target.addEventListener("mousemove", (event) => {
        if (!tooltip) return;
        positionChartTooltip(container, tooltip, event);
      });
      target.addEventListener("mouseleave", () => {
        tooltip.hidden = true;
      });
    });
    container.addEventListener("mouseleave", () => {
      if (tooltip) tooltip.hidden = true;
    });

  }

  function renderLngCharts() {
    if (page !== "unit-lng-cost.html") return;
    const monthlyPaperWrap = document.querySelector('[data-chart-wrap="monthly-lng-paper"]');
    const monthlyTissueWrap = document.querySelector('[data-chart-wrap="monthly-lng-tissue"]');
    const yearlyPaperWrap = document.querySelector('[data-chart-wrap="yearly-lng-paper"]');
    const yearlyTissueWrap = document.querySelector('[data-chart-wrap="yearly-lng-tissue"]');

    if (monthlyPaperWrap || monthlyTissueWrap) {
      const maxDays = daysInMonth();
      const dayLabels = Array.from({ length: maxDays }, (_, index) => index + 1);
      const monthlyPaperSeries = [{ label: "PM-3", color: "#1f4b99", values: [] }];
      const monthlyTissueSeries = [
        { label: "TM-3", color: "#1f4b99", values: [] },
        { label: "TM-4", color: "#d97706", values: [] },
        { label: "TM-5", color: "#059669", values: [] },
      ];
      for (let day = 1; day <= maxDays; day += 1) {
        const rowKey = String(day).padStart(2, "0");
        monthlyPaperSeries[0].values.push(parseNumber(getCellValue("lng_m_burner", monthKey(), rowKey, 0)));
        monthlyTissueSeries[0].values.push(parseNumber(getCellValue("lng_m_burner", monthKey(), rowKey, 1)));
        monthlyTissueSeries[1].values.push(parseNumber(getCellValue("lng_m_burner", monthKey(), rowKey, 2)));
        monthlyTissueSeries[2].values.push(parseNumber(getCellValue("lng_m_burner", monthKey(), rowKey, 3)));
      }
      if (monthlyPaperWrap) {
        renderLineChart(monthlyPaperWrap, { title: "Monthly Burner PM-3 LNG", labels: dayLabels, series: monthlyPaperSeries });
      }
      if (monthlyTissueWrap) {
        renderLineChart(monthlyTissueWrap, { title: "Monthly Tissue LNG", labels: dayLabels, series: monthlyTissueSeries });
      }
    }

    const yearLabels = Array.from({ length: 12 }, (_, index) => `${index + 1}\uC6D4`);
    const yearlyPaperSeries = [{ label: "PM-3", color: "#1f4b99", values: [] }];
    const yearlyTissueSeries = [
      { label: "TM-3", color: "#1f4b99", values: [] },
      { label: "TM-4", color: "#d97706", values: [] },
      { label: "TM-5", color: "#059669", values: [] },
    ];
    for (let monthNo = 1; monthNo <= 12; monthNo += 1) {
      yearlyPaperSeries[0].values.push(sumMonth("lng_m_burner", monthNo, 0));
      yearlyTissueSeries[0].values.push(sumMonth("lng_m_burner", monthNo, 1));
      yearlyTissueSeries[1].values.push(sumMonth("lng_m_burner", monthNo, 2));
      yearlyTissueSeries[2].values.push(sumMonth("lng_m_burner", monthNo, 3));
    }
    if (yearlyPaperWrap) {
      renderLineChart(yearlyPaperWrap, { title: "Yearly Burner PM-3 LNG", labels: yearLabels, series: yearlyPaperSeries });
    }
    if (yearlyTissueWrap) {
      renderLineChart(yearlyTissueWrap, { title: "Yearly Tissue LNG", labels: yearLabels, series: yearlyTissueSeries });
    }
  }

  function renderSteamUsageCharts() {
    if (page !== "unit-steam-usage.html") return;

    const monthlyPaperWrap = document.querySelector('[data-chart-wrap="monthly-usage-paper"]');
    const monthlyTissueWrap = document.querySelector('[data-chart-wrap="monthly-usage-tissue"]');
    const yearlyPaperWrap = document.querySelector('[data-chart-wrap="yearly-usage-paper"]');
    const yearlyTissueWrap = document.querySelector('[data-chart-wrap="yearly-usage-tissue"]');

    if (monthlyPaperWrap || monthlyTissueWrap) {
      const maxDays = daysInMonth();
      const dayLabels = Array.from({ length: maxDays }, (_, index) => index + 1);
      const monthlyPaperSeries = [
        { label: "PM-2", color: "#1f4b99", values: [] },
        { label: "PM-3(M+C)", color: "#d97706", values: [] },
        { label: "\uB514\uC2A4\uD37C\uC838", color: "#059669", values: [] },
        { label: "Ventilation", color: "#dc2626", values: [] },
      ];
      const monthlyTissueSeries = [
        { label: "TM-3", color: "#1f4b99", values: [] },
        { label: "TM-4", color: "#d97706", values: [] },
        { label: "TM-5", color: "#059669", values: [] },
      ];

      for (let day = 1; day <= maxDays; day += 1) {
        const rowKey = String(day).padStart(2, "0");
        const metric = steamMetric(monthKey(), rowKey);
        monthlyPaperSeries[0].values.push(metric.pm2Steam);
        monthlyPaperSeries[1].values.push(metric.pm3McSteam);
        monthlyPaperSeries[2].values.push(metric.disperser);
        monthlyPaperSeries[3].values.push(metric.ventilation);
        monthlyTissueSeries[0].values.push(metric.tm3Steam);
        monthlyTissueSeries[1].values.push(metric.tm4Steam);
        monthlyTissueSeries[2].values.push(metric.tm5Steam);
      }

      if (monthlyPaperWrap) {
        renderLineChart(monthlyPaperWrap, { title: "Monthly Paper Steam Unit (ton/hr)", labels: dayLabels, series: monthlyPaperSeries });
      }
      if (monthlyTissueWrap) {
        renderLineChart(monthlyTissueWrap, { title: "Monthly Tissue Steam Usage", labels: dayLabels, series: monthlyTissueSeries });
      }
    }

    const yearLabels = Array.from({ length: 12 }, (_, index) => `${index + 1}\uC6D4`);
    const yearlyPaperSeries = [
      { label: "PM-2", color: "#1f4b99", values: [] },
      { label: "PM-3(M+C)", color: "#d97706", values: [] },
      { label: "\uB514\uC2A4\uD37C\uC838", color: "#059669", values: [] },
      { label: "Ventilation", color: "#dc2626", values: [] },
    ];
    const yearlyTissueSeries = [
      { label: "TM-3", color: "#1f4b99", values: [] },
      { label: "TM-4", color: "#d97706", values: [] },
      { label: "TM-5", color: "#059669", values: [] },
    ];

    for (let monthNo = 1; monthNo <= 12; monthNo += 1) {
      const aggregate = yearlyUsageAggregate(monthNo);
      yearlyPaperSeries[0].values.push(aggregate.pm2Steam);
      yearlyPaperSeries[1].values.push(aggregate.pm3McSteam);
      yearlyPaperSeries[2].values.push(aggregate.disperser);
      yearlyPaperSeries[3].values.push(aggregate.ventilation);
      yearlyTissueSeries[0].values.push(aggregate.tm3Steam);
      yearlyTissueSeries[1].values.push(aggregate.tm4Steam);
      yearlyTissueSeries[2].values.push(aggregate.tm5Steam);
    }

    if (yearlyPaperWrap) {
      renderLineChart(yearlyPaperWrap, { title: "Yearly Paper Steam Unit (ton/hr)", labels: yearLabels, series: yearlyPaperSeries });
    }
    if (yearlyTissueWrap) {
      renderLineChart(yearlyTissueWrap, { title: "Yearly Tissue Steam Usage", labels: yearLabels, series: yearlyTissueSeries });
    }
  }
  function renderMonthlyTable(table) {
    const tableName = table.dataset.cellTable;
    const tbody = table.querySelector("tbody");
    const totalCols = getLeafColumnCount(table);
    if (!tbody || !tableName || totalCols < 2) return;
    const rows = [];
    const maxDays = daysInMonth();
    for (let day = 1; day <= DAILY_ROW_COUNT; day += 1) {
      const rowKey = String(day).padStart(2, "0");
      const inactiveDay = day > maxDays;
      const cells = [`<td>${rowKey}</td>`];
      for (let col = 0; col < totalCols - 1; col += 1) {
        const value = page === "unit-steam-usage.html" ? monthlyUsageValue(tableName, rowKey, col) : getCellValue(tableName, monthKey(), rowKey, col);
        cells.push(`<td>${buildInput(tableName, "daily", rowKey, col, value, inactiveDay)}</td>`);
      }
      rows.push(`<tr data-row-key="${rowKey}"${inactiveDay ? ' class="invalid-day-row"' : ""}>${cells.join("")}</tr>`);
    }
    rows.push(buildSummaryRow(tableName, "total", "합계", totalCols - 1));
    rows.push(buildSummaryRow(tableName, "avg", "평균", totalCols - 1));
    tbody.innerHTML = rows.join("");
  }

  function renderYearlyTable(table) {
    const tableName = table.dataset.cellTable;
    const tbody = table.querySelector("tbody");
    const totalCols = getLeafColumnCount(table);
    if (!tbody || !tableName || totalCols < 2) return;
    const rows = [];
    for (let monthNo = 1; monthNo <= 12; monthNo += 1) {
      const values = yearlyValues(tableName, monthNo);
      const cells = [`<td>${monthNo}월</td>`];
      for (let col = 0; col < totalCols - 1; col += 1) {
        cells.push(`<td>${buildInput(tableName, "yearly", String(monthNo).padStart(2, "0"), col, values[col] ?? "")}</td>`);
      }
      rows.push(`<tr>${cells.join("")}</tr>`);
    }
    tbody.innerHTML = rows.join("");
  }

  function renderPm3MergedTable(table) {
    const rowMode = table.dataset.rowMode;
    const tbody = table.querySelector("tbody");
    if (!tbody) return;
    const rows = [];
    const count = rowMode === "yearly" ? 12 : DAILY_ROW_COUNT;
    const maxDays = rowMode === "yearly" ? 12 : daysInMonth();

    for (let index = 1; index <= count; index += 1) {
      const rowKey = String(index).padStart(2, "0");
      const label = rowMode === "yearly" ? `${index}\uC6D4` : rowKey;
      const inactiveDay = rowMode !== "yearly" && index > maxDays;
      const pm3aValues = rowMode === "yearly" ? yearlyValues("usage_y_pm3a", index) : [
        monthlyUsageValue("usage_m_pm3a", rowKey, 0),
        getCellValue("usage_m_pm3a", monthKey(), rowKey, 1),
        getCellValue("usage_m_pm3a", monthKey(), rowKey, 2),
        getCellValue("usage_m_pm3a", monthKey(), rowKey, 3),
      ];
      const pm3bValues = rowMode === "yearly" ? yearlyValues("usage_y_pm3b", index) : [
        monthlyUsageValue("usage_m_pm3b", rowKey, 0),
        getCellValue("usage_m_pm3b", monthKey(), rowKey, 1),
        getCellValue("usage_m_pm3b", monthKey(), rowKey, 2),
        monthlyUsageValue("usage_m_pm3b", rowKey, 3),
        getCellValue("usage_m_pm3b", monthKey(), rowKey, 4),
        getCellValue("usage_m_pm3b", monthKey(), rowKey, 5),
      ];
      const tableA = rowMode === "yearly" ? "usage_y_pm3a" : "usage_m_pm3a";
      const tableB = rowMode === "yearly" ? "usage_y_pm3b" : "usage_m_pm3b";
      const cells = [
        `<td>${label}</td>`,
        `<td>${buildInput(tableA, rowMode, rowKey, 0, pm3aValues[0] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableA, rowMode, rowKey, 1, pm3aValues[1] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableA, rowMode, rowKey, 2, pm3aValues[2] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableA, rowMode, rowKey, 3, pm3aValues[3] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableB, rowMode, rowKey, 0, pm3bValues[0] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableB, rowMode, rowKey, 1, pm3bValues[1] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableB, rowMode, rowKey, 2, pm3bValues[2] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableB, rowMode, rowKey, 3, pm3bValues[3] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableB, rowMode, rowKey, 4, pm3bValues[4] ?? "", inactiveDay)}</td>`,
        `<td>${buildInput(tableB, rowMode, rowKey, 5, pm3bValues[5] ?? "", inactiveDay)}</td>`,
      ];
      rows.push(`<tr data-row-key="${rowKey}" data-merged-table="usage_pm3"${inactiveDay ? ' class="invalid-day-row"' : ""}>${cells.join("")}</tr>`);
    }
    if (rowMode === "daily") {
      const buildMergedSummary = (rowKey, label) => [
        `<td>${label}</td>`,
        `<td>${buildInput("usage_m_pm3a", rowMode, rowKey, 0, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3a", rowMode, rowKey, 1, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3a", rowMode, rowKey, 2, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3a", rowMode, rowKey, 3, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3b", rowMode, rowKey, 0, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3b", rowMode, rowKey, 1, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3b", rowMode, rowKey, 2, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3b", rowMode, rowKey, 3, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3b", rowMode, rowKey, 4, "", true)}</td>`,
        `<td>${buildInput("usage_m_pm3b", rowMode, rowKey, 5, "", true)}</td>`,
      ];
      rows.push(`<tr data-row-key="total" data-merged-table="usage_pm3" class="summary-row">${buildMergedSummary("total", "합계").join("")}</tr>`);
      rows.push(`<tr data-row-key="avg" data-merged-table="usage_pm3" class="summary-row">${buildMergedSummary("avg", "평균").join("")}</tr>`);
    }
    tbody.innerHTML = rows.join("");
  }

  // 총합(제지+화장지[+전체]) 병합 테이블. 소스 테이블명(usage_m_paper/tissue/other)을
  // 각 셀의 data-table-name 으로 유지해 기존 재계산/연동 로직이 그대로 동작하게 한다.
  function renderTotalMergedTable(table) {
    const rowMode = table.dataset.rowMode;
    const tbody = table.querySelector("tbody");
    if (!tbody) return;
    const rows = [];

    if (rowMode === "yearly") {
      // 연단위: 제지 + 화장지 (전체 총합 열 없음). yearlyValues 가 이미 완성값 배열 반환.
      // readonly 연단위 셀은 자동 포맷이 없으므로 여기서 정수/소수 포맷을 적용한다.
      const fmt = (v, digits) => {
        const n = Number(v);
        if (v === "" || v == null || !Number.isFinite(n) || n === 0) return "";
        return digits > 0 ? formatDec(n, digits) : formatInt(n);
      };
      for (let monthNo = 1; monthNo <= 12; monthNo += 1) {
        const rk = String(monthNo).padStart(2, "0");
        const p = yearlyValues("usage_y_paper", monthNo);   // [steam, avgRun, unit]
        const t = yearlyValues("usage_y_tissue", monthNo);  // [steam, run, unit]
        const cells = [
          `<td>${monthNo}월</td>`,
          `<td>${buildInput("usage_y_paper", "yearly", rk, 0, fmt(p[0], 0))}</td>`,
          `<td>${buildInput("usage_y_paper", "yearly", rk, 1, fmt(p[1], 2))}</td>`,
          `<td>${buildInput("usage_y_paper", "yearly", rk, 2, fmt(p[2], 2))}</td>`,
          `<td>${buildInput("usage_y_tissue", "yearly", rk, 0, fmt(t[0], 0))}</td>`,
          `<td>${buildInput("usage_y_tissue", "yearly", rk, 1, fmt(t[1], 2))}</td>`,
          `<td>${buildInput("usage_y_tissue", "yearly", rk, 2, fmt(t[2], 2))}</td>`,
        ];
        rows.push(`<tr data-row-key="${rk}" data-merged-table="usage_total">${cells.join("")}</tr>`);
      }
      tbody.innerHTML = rows.join("");
      return;
    }

    // 월단위: 제지 스팀량 · (평균가동·원단위 계산) | 화장지 스팀량 · (가동·원단위 계산) | 총 스팀량
    const maxDays = daysInMonth();
    for (let index = 1; index <= DAILY_ROW_COUNT; index += 1) {
      const rk = String(index).padStart(2, "0");
      const inactive = index > maxDays;
      const cells = [
        `<td>${rk}</td>`,
        `<td>${buildInput("usage_m_paper", "daily", rk, 0, monthlyUsageValue("usage_m_paper", rk, 0), inactive)}</td>`,
        `<td>${buildInput("usage_m_paper", "daily", rk, 1, "", inactive)}</td>`,
        `<td>${buildInput("usage_m_paper", "daily", rk, 2, "", inactive)}</td>`,
        `<td>${buildInput("usage_m_tissue", "daily", rk, 0, monthlyUsageValue("usage_m_tissue", rk, 0), inactive)}</td>`,
        `<td>${buildInput("usage_m_tissue", "daily", rk, 1, "", inactive)}</td>`,
        `<td>${buildInput("usage_m_tissue", "daily", rk, 2, "", inactive)}</td>`,
        `<td>${buildInput("usage_m_other", "daily", rk, 0, monthlyUsageValue("usage_m_other", rk, 0), inactive)}</td>`,
      ];
      rows.push(`<tr data-row-key="${rk}" data-merged-table="usage_total"${inactive ? ' class="invalid-day-row"' : ""}>${cells.join("")}</tr>`);
    }
    // 합계/평균 행: 표시 전용(usage_total + 위치 인덱스)로 두어 요약 재계산이 위치로 접근 가능하게.
    const buildTotalSummary = (rk, label) => {
      const c = [`<td>${label}</td>`];
      for (let pos = 0; pos < 7; pos += 1) c.push(`<td>${buildInput("usage_total", "daily", rk, pos, "", true)}</td>`);
      return c;
    };
    rows.push(`<tr data-row-key="total" data-merged-table="usage_total" class="summary-row">${buildTotalSummary("total", "합계").join("")}</tr>`);
    rows.push(`<tr data-row-key="avg" data-merged-table="usage_total" class="summary-row">${buildTotalSummary("avg", "평균").join("")}</tr>`);
    tbody.innerHTML = rows.join("");
  }

  // 총합 병합 행 재계산(월단위): 제지 평균가동·원단위, 화장지 가동·원단위 (스팀/총스팀은 렌더시 확정)
  function recalcMergedTotalRow(row) {
    if (!getMergedInput(row, "usage_m_paper", 0)) return; // 연단위는 완성값이라 재계산 불필요
    const rowKey = row.dataset.rowKey;
    const src = (tn, col) => parseNumber(document.querySelector(`input[data-table-name="${tn}"][data-row-key="${rowKey}"][data-col-index="${col}"]`)?.value.trim() ?? "");
    const setMerged = (tn, col, value, digits) => {
      const input = getMergedInput(row, tn, col);
      if (!input) return;
      input.value = value === "" ? "" : (digits > 0 ? formatDec(value, digits) : formatInt(value));
      input.classList.add("unit-align-number");
      alignDataCell(input.closest("td"));
    };
    // 제지: 원단위 = pm2Unit + pm3Unit + dispUnit (각 <7), 평균가동 = 스팀/원단위
    const paperUnit = [src("usage_m_pm2", 2), src("usage_m_pm3a", 2), src("usage_m_pm3b", 2)]
      .filter((v) => Number.isFinite(v) && v < 7).reduce((s, v) => s + v, 0);
    const paperSteam = parseNumber(getMergedInput(row, "usage_m_paper", 0)?.value.trim() ?? "");
    setMerged("usage_m_paper", 1, paperUnit > 0 ? paperSteam / paperUnit : "", 2);
    setMerged("usage_m_paper", 2, paperUnit > 0 ? paperUnit : "", 2);
    // 화장지: 가동 = tm3+tm4+tm5 run, 원단위 = tm3Unit+tm4Unit+tm5Unit (각 <7)
    const tissueRun = src("usage_m_tm", 1) + src("usage_m_tm", 4) + src("usage_m_tm", 7);
    const tissueUnit = [src("usage_m_tm", 2), src("usage_m_tm", 5), src("usage_m_tm", 8)]
      .filter((v) => Number.isFinite(v) && v < 7).reduce((s, v) => s + v, 0);
    setMerged("usage_m_tissue", 1, tissueRun > 0 ? tissueRun : "", 2);
    setMerged("usage_m_tissue", 2, tissueUnit > 0 ? tissueUnit : "", 2);
  }

  function renderYearlyRatioTable(table) {
    const ratioType = table.dataset.yearlyRatioTable;
    const tbody = table.querySelector("tbody");
    if (!ratioType || !tbody) return;

    const rows = [];
    for (let monthNo = 1; monthNo <= 12; monthNo += 1) {
      const ratio = yearlyUsageRatio(monthNo);
      const cells = [`<td>${monthNo}\uC6D4</td>`];
      if (ratioType === "summary") {
        cells.push(`<td>${formatPercent(ratio.pm2)}</td>`);
        cells.push(`<td>${formatPercent(ratio.pm3)}</td>`);
        cells.push(`<td>${formatPercent(ratio.tm3)}</td>`);
        cells.push(`<td>${formatPercent(ratio.tm4)}</td>`);
        cells.push(`<td>${formatPercent(ratio.tm5)}</td>`);
        cells.push(`<td>${formatPercent(ratio.total)}</td>`);
      }
      rows.push(`<tr>${cells.join("")}</tr>`);
    }
    tbody.innerHTML = rows.join("");
  }

  function getMergedInput(row, tableName, colIndex) {
    return row.querySelector(`input[data-table-name="${tableName}"][data-col-index="${colIndex}"]`);
  }

  function recalcMergedPm3Row(row) {
    const rowMode = getMergedInput(row, "usage_m_pm3a", 0) ? "daily" : "yearly";
    const tableA = rowMode === "daily" ? "usage_m_pm3a" : "usage_y_pm3a";
    const tableB = rowMode === "daily" ? "usage_m_pm3b" : "usage_y_pm3b";

    const steamA = parseNumber(getMergedInput(row, tableA, 0)?.value.trim() ?? "");
    const runA = parseNumber(getMergedInput(row, tableA, 1)?.value.trim() ?? "");
    const unitA = getMergedInput(row, tableA, 2);
    if (unitA) unitA.value = formatDec(usageRate(steamA, runA), 2);

    const disp = parseNumber(getMergedInput(row, tableB, 0)?.value.trim() ?? "");
    const dispRun = parseNumber(getMergedInput(row, tableB, 1)?.value.trim() ?? "");
    const dispUnit = getMergedInput(row, tableB, 2);
    if (dispUnit) dispUnit.value = formatDec(usageRate(disp, dispRun), 2);

    const vent = parseNumber(getMergedInput(row, tableB, 3)?.value.trim() ?? "");
    const ventRun = parseNumber(getMergedInput(row, tableB, 4)?.value.trim() ?? "");
    const ventUnit = getMergedInput(row, tableB, 5);
    if (ventUnit) ventUnit.value = formatDec(usageRate(vent, ventRun), 2);
  }

  function recalcUsageRow(row) {
    const tableName = row.querySelector("input")?.dataset.tableName;
    if (!tableName) return;

    if (tableName === "usage_m_pm2" || tableName === "usage_y_pm2") {
      const steam = parseNumber(getInput(row, 0)?.value.trim() ?? "");
      const run = parseNumber(getInput(row, 1)?.value.trim() ?? "");
      setCalc(row, 2, usageRate(steam, run), 2);
      return;
    }

    if (tableName === "usage_m_pm3a" || tableName === "usage_y_pm3a") {
      const steam = parseNumber(getInput(row, 0)?.value.trim() ?? "");
      const run = parseNumber(getInput(row, 1)?.value.trim() ?? "");
      setCalc(row, 2, usageRate(steam, run), 2);
      return;
    }

    if (tableName === "usage_m_pm3b" || tableName === "usage_y_pm3b") {
      const disp = parseNumber(getInput(row, 0)?.value.trim() ?? "");
      const dispRun = parseNumber(getInput(row, 1)?.value.trim() ?? "");
      const vent = parseNumber(getInput(row, 4)?.value.trim() ?? "");
      const ventRun = parseNumber(getInput(row, 5)?.value.trim() ?? "");
      setCalc(row, 2, usageRate(disp, dispRun), 2);
      setCalc(row, 6, usageRate(vent, ventRun), 2);
      return;
    }

    if (tableName === "usage_m_tm" || tableName === "usage_y_tm") {
      const tm3 = parseNumber(getInput(row, 0)?.value.trim() ?? "");
      const tm3Run = parseNumber(getInput(row, 1)?.value.trim() ?? "");
      const tm4 = parseNumber(getInput(row, 3)?.value.trim() ?? "");
      const tm4Run = parseNumber(getInput(row, 4)?.value.trim() ?? "");
      const tm5 = parseNumber(getInput(row, 6)?.value.trim() ?? "");
      const tm5Run = parseNumber(getInput(row, 7)?.value.trim() ?? "");
      const tm3Unit = usageRate(tm3, tm3Run);
      const tm4Unit = usageRate(tm4, tm4Run);
      const tm5Unit = usageRate(tm5, tm5Run);
      setCalc(row, 2, tm3Unit, 2);
      setCalc(row, 5, tm4Unit, 2);
      setCalc(row, 8, tm5Unit, 2);
      const totalSteam = tm3 + tm4 + tm5;
      const totalRun = tm3Run + tm4Run + tm5Run;
      const totalUnit = (tm3Run > 0 ? tm3Unit : 0) + (tm4Run > 0 ? tm4Unit : 0) + (tm5Run > 0 ? tm5Unit : 0);
      setCalc(row, 9, totalSteam > 0 ? totalSteam : "");
      setCalc(row, 10, totalRun > 0 ? totalRun : "", 2);
      setCalc(row, 11, totalUnit > 0 ? totalUnit : "", 2);
      return;
    }

    if (tableName === "usage_m_paper" || tableName === "usage_y_paper") {
      const rowKey = row.querySelector("input")?.dataset.rowKey;
      const rowMode = row.querySelector("input")?.dataset.rowMode;
      const prefix = rowMode === "daily" ? "usage_m_" : "usage_y_";
      const pm2Unit = parseNumber(document.querySelector(`input[data-table-name="${prefix}pm2"][data-row-key="${rowKey}"][data-col-index="2"]`)?.value.trim() ?? "");
      const pm3Unit = parseNumber(document.querySelector(`input[data-table-name="${prefix}pm3a"][data-row-key="${rowKey}"][data-col-index="2"]`)?.value.trim() ?? "");
      const dispUnit = parseNumber(document.querySelector(`input[data-table-name="${prefix}pm3b"][data-row-key="${rowKey}"][data-col-index="2"]`)?.value.trim() ?? "");
      const unit = [pm2Unit, pm3Unit, dispUnit]
        .filter((value) => Number.isFinite(value) && value < 7)
        .reduce((sum, value) => sum + value, 0);
      const steam = parseNumber(getInput(row, 0)?.value.trim() ?? "");
      setCalc(row, 1, unit > 0 ? steam / unit : "", 2);
      setCalc(row, 2, unit > 0 ? unit : "", 2);
      return;
    }

    if (tableName === "usage_m_tissue" || tableName === "usage_y_tissue") {
      const rowKey = row.querySelector("input")?.dataset.rowKey;
      const rowMode = row.querySelector("input")?.dataset.rowMode;
      const prefix = rowMode === "daily" ? "usage_m_" : "usage_y_";
      const tm3Run = parseNumber(document.querySelector(`input[data-table-name="${prefix}tm"][data-row-key="${rowKey}"][data-col-index="1"]`)?.value.trim() ?? "");
      const tm4Run = parseNumber(document.querySelector(`input[data-table-name="${prefix}tm"][data-row-key="${rowKey}"][data-col-index="4"]`)?.value.trim() ?? "");
      const tm5Run = parseNumber(document.querySelector(`input[data-table-name="${prefix}tm"][data-row-key="${rowKey}"][data-col-index="7"]`)?.value.trim() ?? "");
      const tm3Unit = parseNumber(document.querySelector(`input[data-table-name="${prefix}tm"][data-row-key="${rowKey}"][data-col-index="2"]`)?.value.trim() ?? "");
      const tm4Unit = parseNumber(document.querySelector(`input[data-table-name="${prefix}tm"][data-row-key="${rowKey}"][data-col-index="5"]`)?.value.trim() ?? "");
      const tm5Unit = parseNumber(document.querySelector(`input[data-table-name="${prefix}tm"][data-row-key="${rowKey}"][data-col-index="8"]`)?.value.trim() ?? "");
      const totalRun = tm3Run + tm4Run + tm5Run;
      const totalUnit = [tm3Unit, tm4Unit, tm5Unit]
        .filter((value) => Number.isFinite(value) && value < 7)
        .reduce((sum, value) => sum + value, 0);
      setCalc(row, 1, totalRun > 0 ? totalRun : "", 2);
      setCalc(row, 2, totalUnit > 0 ? totalUnit : "", 2);
    }
  }

  function recalcLngRow(row) {
    const tableName = row.querySelector("input")?.dataset.tableName;
    if (!tableName) return;
    if (tableName === "lng_m_boiler" || tableName === "lng_y_boiler") {
      const total = parseNumber(getInput(row, 0)?.value.trim() ?? "") + parseNumber(getInput(row, 1)?.value.trim() ?? "") + parseNumber(getInput(row, 2)?.value.trim() ?? "") + parseNumber(getInput(row, 3)?.value.trim() ?? "");
      setCalc(row, 4, total > 0 ? total : "");
      const price = parseNumber(getInput(row, 5)?.value.trim() ?? "");
      setCalc(row, 6, total > 0 || price > 0 ? total * price / 1000000 : "", 2);
      return;
    }
    if (tableName === "lng_m_burner" || tableName === "lng_y_burner") {
      const pm3 = parseNumber(getInput(row, 0)?.value.trim() ?? "");
      const tm3 = parseNumber(getInput(row, 1)?.value.trim() ?? "");
      const tm4 = parseNumber(getInput(row, 2)?.value.trim() ?? "");
      const tm5 = parseNumber(getInput(row, 3)?.value.trim() ?? "");
      const tissue = tm3 + tm4 + tm5;
      const total = pm3 + tissue;
      setCalc(row, 4, tissue > 0 ? tissue : "");
      setCalc(row, 5, total > 0 ? total : "");
      const price = parseNumber(getInput(row, 6)?.value.trim() ?? "");
      const cost = total * price / 100000000;
      setCalc(row, 7, total > 0 || price > 0 ? cost : "", 2);
      const rowKey = row.querySelector("input")?.dataset.rowKey;
      const rowMode = row.querySelector("input")?.dataset.rowMode;
      const boilerTable = tableName.startsWith("lng_y_") ? "lng_y_boiler" : "lng_m_boiler";
      const boilerCost = parseNumber(document.querySelector(`input[data-table-name="${boilerTable}"][data-row-key="${rowKey}"][data-row-mode="${rowMode}"][data-col-index="6"]`)?.value.trim() ?? "");
      setCalc(row, 8, boilerCost > 0 || cost > 0 ? boilerCost + cost : "", 2);
      return;
    }
    if (tableName === "lng_m_mix" || tableName === "lng_y_mix") {
      const pm2 = parseNumber(getInput(row, 0)?.value.trim() ?? "");
      const pm3 = parseNumber(getInput(row, 1)?.value.trim() ?? "");
      const tm3 = parseNumber(getInput(row, 3)?.value.trim() ?? "");
      const tm4 = parseNumber(getInput(row, 4)?.value.trim() ?? "");
      const tm5 = parseNumber(getInput(row, 5)?.value.trim() ?? "");
      const pmTotal = pm2 + pm3;
      const tmTotal = tm3 + tm4 + tm5;
      setCalc(row, 2, pmTotal > 0 ? pmTotal : "");
      setCalc(row, 6, tmTotal > 0 ? tmTotal : "");
      setCalc(row, 7, pmTotal > 0 || tmTotal > 0 ? pmTotal + tmTotal : "");
    }
  }

  function recalcRow(row) {
    if (row.dataset.mergedTable === "usage_pm3") {
      recalcMergedPm3Row(row);
      return;
    }
    if (row.dataset.mergedTable === "usage_total") {
      recalcMergedTotalRow(row);
      return;
    }
    const tableName = row.querySelector("input")?.dataset.tableName || "";
    if (tableName.startsWith("usage_")) recalcUsageRow(row);
    if (tableName.startsWith("lng_")) recalcLngRow(row);
  }

  function isVisibleTable(table) {
    const periodPanel = table.closest("[data-period-panel]");
    const machinePanel = table.closest("[data-machine-panel]");
    const yearlyPanel = table.closest("[data-yearly-machine-panel]");
    return (!periodPanel || periodPanel.classList.contains("active")) &&
      (!machinePanel || machinePanel.classList.contains("active")) &&
      (!yearlyPanel || yearlyPanel.classList.contains("active"));
  }

  function recalcAll(options = {}) {
    const tableSelector = options.visibleOnly
      ? "[data-cell-table], [data-merged-table]"
      : "[data-cell-table], [data-merged-table]";
    document.querySelectorAll(tableSelector).forEach((table) => {
      if (options.visibleOnly && !isVisibleTable(table)) return;
      table.querySelectorAll("tbody tr").forEach(recalcRow);
    });
    recalcMonthlySummaries(options);
  }

  function recalcMonthlySummaries(options = {}) {
    const maxDays = daysInMonth();
    document.querySelectorAll('[data-cell-table][data-row-mode="daily"]').forEach((table) => {
      if (options.visibleOnly && !isVisibleTable(table)) return;
      const tableName = table.dataset.cellTable;
      const totalRow = table.querySelector('tr[data-row-key="total"]');
      const avgRow = table.querySelector('tr[data-row-key="avg"]');
      if (!tableName || !totalRow || !avgRow) return;
      const colCount = table.querySelectorAll("thead th").length - 1;
      for (let col = 0; col < colCount; col += 1) {
        let total = 0;
        let hasNumeric = false;
        for (let day = 1; day <= maxDays; day += 1) {
          const rowKey = String(day).padStart(2, "0");
          const value = document.querySelector(`input[data-table-name="${tableName}"][data-row-key="${rowKey}"][data-col-index="${col}"]`)?.value ?? "";
          const normalized = normalizeEditableValue(value).replace(/%/g, "");
          if (!normalized || !/^[-+]?\d*(\.\d+)?$/.test(normalized)) continue;
          total += Number(normalized);
          hasNumeric = true;
        }
        const digits = summaryDigits(tableName, col);
        setCalc(totalRow, col, hasNumeric ? total : "", digits);
        setCalc(avgRow, col, hasNumeric ? total / maxDays : "", digits);
      }
    });

    document.querySelectorAll('[data-merged-table="usage_pm3"][data-row-mode="daily"]').forEach((table) => {
      if (options.visibleOnly && !isVisibleTable(table)) return;
      const totalRow = table.querySelector('tr[data-row-key="total"]');
      const avgRow = table.querySelector('tr[data-row-key="avg"]');
      if (!totalRow || !avgRow) return;
      const specs = [
        ["usage_m_pm3a", 0], ["usage_m_pm3a", 1], ["usage_m_pm3a", 2], ["usage_m_pm3a", 3],
        ["usage_m_pm3b", 0], ["usage_m_pm3b", 1], ["usage_m_pm3b", 2], ["usage_m_pm3b", 3], ["usage_m_pm3b", 4], ["usage_m_pm3b", 5],
      ];
      specs.forEach(([tableName, colIndex], position) => {
        let total = 0;
        let hasNumeric = false;
        for (let day = 1; day <= maxDays; day += 1) {
          const rowKey = String(day).padStart(2, "0");
          const value = document.querySelector(`input[data-table-name="${tableName}"][data-row-key="${rowKey}"][data-col-index="${colIndex}"]`)?.value ?? "";
          const normalized = normalizeEditableValue(value).replace(/%/g, "");
          if (!normalized || !/^[-+]?\d*(\.\d+)?$/.test(normalized)) continue;
          total += Number(normalized);
          hasNumeric = true;
        }
        const digits = summaryDigits(tableName, colIndex);
        setCalc(totalRow, position, hasNumeric ? total : "", digits);
        setCalc(avgRow, position, hasNumeric ? total / maxDays : "", digits);
      });
    });

    // 총합 병합(월단위) 합계/평균 행
    document.querySelectorAll('[data-merged-table="usage_total"][data-row-mode="daily"]').forEach((table) => {
      if (options.visibleOnly && !isVisibleTable(table)) return;
      const totalRow = table.querySelector('tr[data-row-key="total"]');
      const avgRow = table.querySelector('tr[data-row-key="avg"]');
      if (!totalRow || !avgRow) return;
      const specs = [
        ["usage_m_paper", 0], ["usage_m_paper", 1], ["usage_m_paper", 2],
        ["usage_m_tissue", 0], ["usage_m_tissue", 1], ["usage_m_tissue", 2],
        ["usage_m_other", 0],
      ];
      const digitsByPos = [0, 2, 2, 0, 2, 2, 0];
      specs.forEach(([tableName, colIndex], position) => {
        let total = 0;
        let hasNumeric = false;
        for (let day = 1; day <= maxDays; day += 1) {
          const rowKey = String(day).padStart(2, "0");
          const value = document.querySelector(`input[data-table-name="${tableName}"][data-row-key="${rowKey}"][data-col-index="${colIndex}"]`)?.value ?? "";
          const normalized = normalizeEditableValue(value).replace(/%/g, "");
          if (!normalized || !/^[-+]?\d*(\.\d+)?$/.test(normalized)) continue;
          total += Number(normalized);
          hasNumeric = true;
        }
        const digits = digitsByPos[position];
        setCalc(totalRow, position, hasNumeric ? total : "", digits);
        setCalc(avgRow, position, hasNumeric ? total / maxDays : "", digits);
      });
    });
  }

  function renderYearlyTable(table) {
    const tableName = table.dataset.cellTable;
    const tbody = table.querySelector("tbody");
    const totalCols = getLeafColumnCount(table);
    if (!tbody || !tableName || totalCols < 2) return;
    const rows = [];
    for (let monthNo = 1; monthNo <= 12; monthNo += 1) {
      const values = yearlyValues(tableName, monthNo);
      const cells = [`<td>${monthNo}\uC6D4</td>`];
      for (let col = 0; col < totalCols - 1; col += 1) {
        cells.push(`<td>${buildInput(tableName, "yearly", String(monthNo).padStart(2, "0"), col, values[col] ?? "")}</td>`);
      }
      rows.push(`<tr>${cells.join("")}</tr>`);
    }
    tbody.innerHTML = rows.join("");
  }

  function renderYearlyTables() {
    document.querySelectorAll('[data-cell-table][data-row-mode="yearly"]').forEach(renderYearlyTable);
    document.querySelectorAll('[data-merged-table="usage_pm3"][data-row-mode="yearly"]').forEach(renderPm3MergedTable);
    document.querySelectorAll('[data-merged-table="usage_total"][data-row-mode="yearly"]').forEach(renderTotalMergedTable);
    document.querySelectorAll("[data-yearly-ratio-table]").forEach(renderYearlyRatioTable);
    recalcAll();
    renderLngCharts();
    renderSteamUsageCharts();
  }

  function recalcLinked(input) {
    const tableName = input.dataset.tableName;
    const rowKey = input.dataset.rowKey;
    const rowMode = input.dataset.rowMode;
    if (tableName === "lng_m_boiler") {
      const burnerRow = document.querySelector(`input[data-table-name="lng_m_burner"][data-row-key="${rowKey}"][data-row-mode="${rowMode}"]`)?.closest("tr");
      if (burnerRow) recalcRow(burnerRow);
    }
    if (page === "unit-steam-usage.html") {
      ["usage_m_paper", "usage_m_tissue", "usage_m_other"].forEach((target) => {
        const row = document.querySelector(`input[data-table-name="${target}"][data-row-key="${rowKey}"][data-row-mode="daily"]`)?.closest("tr");
        if (row) recalcRow(row);
      });
    }
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

  function queueSaveInput(input, delay = 120) {
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

  function queueYearlyRender() {
    if (yearlyRenderTimer) clearTimeout(yearlyRenderTimer);
    yearlyRenderTimer = setTimeout(() => {
      yearlyRenderTimer = null;
      renderYearlyTables();
    }, 120);
  }

  function bindInputs() {
    document.querySelectorAll(".table-edit-input").forEach((input) => {
      if (input.dataset.bound === "true") return;
      input.dataset.bound = "true";
      input.addEventListener("input", () => {
        const row = input.closest("tr");
        if (row) recalcRow(row);
        recalcLinked(input);
        if (page === "unit-lng-cost.html" || page === "unit-steam-usage.html") queueYearlyRender();
        else renderYearlyTables();
        queueSaveInput(input);
      });
      input.addEventListener("focus", () => {
        if (input.readOnly || input.classList.contains("calc-input")) return;
        input.value = input.dataset.rawValue ?? normalizeEditableValue(input.value);
      });
      input.addEventListener("blur", () => {
        if (input.readOnly || input.classList.contains("calc-input")) return;
        input.dataset.rawValue = normalizeEditableValue(input.value);
        input.value = formatEditableValue(input.value);
      });
      input.addEventListener("change", async () => {
        dirtyInputs.add(input);
        await flushDirtyInputs();
        input.dataset.rawValue = normalizeEditableValue(input.value);
        input.value = formatEditableValue(input.value);
        renderYearlyTables();
      });
      input.addEventListener("keydown", async (event) => {
        if (event.key !== "Tab") return;
        event.preventDefault();
        const editable = Array.from(document.querySelectorAll(".table-edit-input")).filter((item) => !item.readOnly && !item.classList.contains("calc-input"));
        const current = editable.indexOf(input);
        if (current < 0) return;
        dirtyInputs.add(input);
        await flushDirtyInputs();
        const next = editable[event.shiftKey ? current - 1 : current + 1];
        if (next) {
          next.focus();
          next.select?.();
        }
      });
    });
  }

  async function ensureLoaded() {
    if (loaded) return;
    const [cells, units, raws] = await Promise.all([
      (page === "unit-lng-cost.html" || page === "unit-steam-usage.html") ? Promise.resolve([]) : fetchPaged(CELL_TABLE),
      Promise.resolve([]),
      Promise.resolve([]),
    ]);
    if (cells.length) cellRows.push(...cells);
    if (units.length) unitRows.push(...units);
    if (raws.length) rawRows.push(...raws);
    loaded = true;
    if (page === "unit-lng-cost.html") {
      await ensureLngYearLoaded(yearSelect.value);
    }
    if (page === "unit-steam-usage.html") {
      await ensureSteamMonthLoaded(monthKey());
    }
  }

  async function render() {
    await ensureLoaded();
    if (page === "unit-lng-cost.html") {
      await ensureLngYearLoaded(yearSelect.value);
    }
    const visibleTables = (selector) => {
      const tables = Array.from(document.querySelectorAll(selector));
      const visible = tables.filter((table) => {
        const periodPanel = table.closest("[data-period-panel]");
        const machinePanel = table.closest("[data-machine-panel]");
        const yearlyPanel = table.closest("[data-yearly-machine-panel]");
        return (!periodPanel || periodPanel.classList.contains("active")) &&
          (!machinePanel || machinePanel.classList.contains("active")) &&
          (!yearlyPanel || yearlyPanel.classList.contains("active"));
      });
      return visible.length ? visible : tables;
    };
    const needsYearData = page !== "unit-steam-usage.html" ||
      Boolean(document.querySelector('[data-period-panel="yearly"].active')) ||
      Boolean(document.querySelector('[data-chart-panel].active'));
    if (page === "unit-steam-usage.html") {
      if (needsYearData) await ensureSteamYearLoaded(yearSelect.value);
      else await ensureSteamMonthLoaded(monthKey());
    }
    visibleTables('[data-cell-table][data-row-mode="daily"]').forEach(renderMonthlyTable);
    visibleTables('[data-cell-table][data-row-mode="yearly"]').forEach(renderYearlyTable);
    visibleTables('[data-merged-table="usage_pm3"][data-row-mode="daily"]').forEach(renderPm3MergedTable);
    visibleTables('[data-merged-table="usage_pm3"][data-row-mode="yearly"]').forEach(renderPm3MergedTable);
    visibleTables('[data-merged-table="usage_total"][data-row-mode="daily"]').forEach(renderTotalMergedTable);
    visibleTables('[data-merged-table="usage_total"][data-row-mode="yearly"]').forEach(renderTotalMergedTable);
    visibleTables("[data-yearly-ratio-table]").forEach(renderYearlyRatioTable);
    recalcAll({ visibleOnly: page === "unit-steam-usage.html" && !needsYearData });
    renderLngCharts();
    if (needsYearData) renderSteamUsageCharts();
    bindInputs();
    applyDataAlignment();
  }

  window.UnitPageSaveCoordinator?.registerFlush(flushDirtyInputs);

  yearSelect.addEventListener("change", async () => {
    await flushDirtyInputs();
  });
  monthSelect.addEventListener("change", async () => {
    await flushDirtyInputs();
  });
  submitButton?.addEventListener("click", async () => {
    await flushDirtyInputs();
    await render();
  });
  document.querySelectorAll(".period-tab-button, .machine-tab-button, .yearly-machine-tab-button").forEach((button) => {
    button.addEventListener("click", () => {
      window.setTimeout(() => {
        render();
      }, 0);
    });
  });
  render();
});




