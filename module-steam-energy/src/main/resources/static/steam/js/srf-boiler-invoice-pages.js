document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "srf-boiler-invoice.html") return;

  const PERIOD_KEY = "steamlog:shared-period";
  const CELL_TABLE = "table_cell_value";
  const SUMMARY_TABLE = "srf_boiler_invoice_summary";
  const TABLES_BASE = "/tables";
  const yearSelect = document.getElementById("invoiceYear");
  const monthSelect = document.getElementById("invoiceMonth");
  const searchButton = document.getElementById("invoiceSearch");
  const printButton = document.getElementById("invoicePrint");
  const tableShell = document.getElementById("invoiceTableShell");
  const sheetLabel = document.getElementById("invoiceSheetLabel");
  if (!yearSelect || !monthSelect || !searchButton || !tableShell) return;

  // 인쇄: 입력 중인 셀의 미저장 값이 잘리지 않도록 blur 후 print.
  printButton?.addEventListener("click", () => {
    if (document.activeElement instanceof HTMLElement) document.activeElement.blur();
    setTimeout(() => window.print(), 50);
  });

  const now = new Date();
  const saved = readPeriod();
  const state = {
    year: Number(saved.year) || now.getFullYear(),
    month: String(saved.month || now.getMonth() + 1).padStart(2, "0"),
    cellRows: [],
  };

  initPeriodControls();
  bindEvents();
  loadSummary();

  function monthKey() {
    return `${state.year}-${state.month}`;
  }

  function initPeriodControls() {
    const currentYear = now.getFullYear();
    yearSelect.innerHTML = "";
    for (let year = currentYear - 5; year <= currentYear + 2; year += 1) {
      const option = document.createElement("option");
      option.value = String(year);
      option.textContent = `${year}\uB144`;
      yearSelect.appendChild(option);
    }
    monthSelect.innerHTML = "";
    for (let month = 1; month <= 12; month += 1) {
      const option = document.createElement("option");
      option.value = String(month).padStart(2, "0");
      option.textContent = `${month}\uC6D4`;
      monthSelect.appendChild(option);
    }
    yearSelect.value = String(state.year);
    monthSelect.value = state.month;
  }

  function bindEvents() {
    searchButton.addEventListener("click", () => {
      state.year = Number(yearSelect.value);
      state.month = monthSelect.value;
      writePeriod();
      loadSummary();
    });
    yearSelect.addEventListener("change", writePeriodFromSelects);
    monthSelect.addEventListener("change", writePeriodFromSelects);
    tableShell.addEventListener("change", handleCellChange);
    tableShell.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && event.target instanceof HTMLInputElement) {
        event.target.blur();
      }
    });
    window.addEventListener("storage", (event) => {
      if (event.key !== PERIOD_KEY) return;
      const period = readPeriod();
      if (!period.year || !period.month) return;
      state.year = Number(period.year);
      state.month = String(period.month).padStart(2, "0");
      yearSelect.value = String(state.year);
      monthSelect.value = state.month;
      loadSummary();
    });
  }

  function writePeriodFromSelects() {
    state.year = Number(yearSelect.value);
    state.month = monthSelect.value;
    writePeriod();
  }

  function readPeriod() {
    try {
      return JSON.parse(localStorage.getItem(PERIOD_KEY) || "{}");
    } catch (error) {
      return {};
    }
  }

  function writePeriod() {
    localStorage.setItem(PERIOD_KEY, JSON.stringify({ year: state.year, month: state.month }));
  }

  async function loadSummary() {
    tableShell.innerHTML = `<div class="invoice-empty">\uB85C\uB529 \uC911</div>`;
    try {
      const [summaryResponse, cellsResponse] = await Promise.all([
        fetch(`/tables/srf-boiler-invoice/summary?year=${encodeURIComponent(state.year)}&month=${encodeURIComponent(Number(state.month))}`),
        fetch(`${TABLES_BASE}/${CELL_TABLE}?page=1&limit=2000&month=${encodeURIComponent(monthKey())}`),
      ]);
      const payload = await summaryResponse.json();
      if (!summaryResponse.ok) throw new Error(payload.message || "\uB370\uC774\uD130\uB97C \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.");
      const cellsPayload = cellsResponse.ok ? await cellsResponse.json() : { data: [] };
      state.cellRows = Array.isArray(cellsPayload?.data)
        ? cellsPayload.data.filter((row) => row.table_name === SUMMARY_TABLE)
        : [];
      renderTable(payload);
      if (sheetLabel) sheetLabel.textContent = payload.sheet || "Summary(VK)";
    } catch (error) {
      tableShell.innerHTML = `<div class="invoice-empty">${escapeHtml(error.message || "\uB370\uC774\uD130\uB97C \uBD88\uB7EC\uC624\uC9C0 \uBABB\uD588\uC2B5\uB2C8\uB2E4.")}</div>`;
    }
  }

  function renderTable(payload) {
    const columns = Array.isArray(payload.columns) ? payload.columns : [];
    const rows = Array.isArray(payload.rows) ? payload.rows : [];
    if (!rows.length) {
      tableShell.innerHTML = `<div class="invoice-empty">\uD45C\uC2DC\uD560 \uB370\uC774\uD130\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4.</div>`;
      return;
    }
    const colgroup = columns.map((column) => `<col style="width:${Number(column.width) || 48}px">`).join("");
    const body = rows.map((row) => {
      const cells = Array.isArray(row.cells) ? row.cells : [];
      const height = Number(row.height) || 20;
      const printH = Math.max(8, Math.round(height * 0.7));
      const trStyle = `height:${height}px;--print-row-h:${printH}px`;
      if (isWideTitleRow(Number(row.index))) {
        const titleCell = cells.find((cell) => String(cell?.value ?? "").trim()) || cells[0] || {};
        return `<tr style="${trStyle}">${wideTitleCellHtml(titleCell)}</tr>`;
      }
      if (isLeftTotalMergeRow(Number(row.index))) {
        return `<tr style="${trStyle}">${leftTotalMergeRowHtml(cells)}</tr>`;
      }
      if (isBodyTotalMergeRow(Number(row.index))) {
        return `<tr style="${trStyle}">${bodyTotalMergeRowHtml(cells)}</tr>`;
      }
      if (isLabelMergeRow(Number(row.index))) {
        return `<tr style="${trStyle}">${labelMergeRowHtml(cells)}</tr>`;
      }
      return `<tr style="${trStyle}">${cells.map(cellHtml).join("")}</tr>`;
    }).join("");
    tableShell.innerHTML = `<table class="invoice-table" data-no-resize><colgroup>${colgroup}</colgroup><tbody>${body}</tbody></table>`;
  }

  function wideTitleCellHtml(cell) {
    const value = cell?.value ?? "";
    const style = cell?.style || "";
    return `<td class="invoice-wide-title" colspan="7" style="${escapeAttribute(style)}">${escapeHtml(value)}</td>`;
  }

  function labelMergeRowHtml(cells) {
    const rowIndex = Number(cells[0]?.row ?? cells[1]?.row ?? 0);
    if (isUnitMergeRow(rowIndex)) {
      return [
        cellHtml(cells[0] || {}),
        mergedCellHtml(cells[1] || {}, 2),
        cellHtml(cells[3] || {}),
        mergedCellHtml(cells[4] || {}, 3),
      ].join("");
    }
    return [
      cellHtml(cells[0] || {}),
      mergedCellHtml(cells[1] || {}, 2),
      cellHtml(cells[3] || {}),
      cellHtml(cells[4] || {}),
      cellHtml(cells[5] || {}),
      cellHtml(cells[6] || {}),
    ].join("");
  }

  function leftTotalMergeRowHtml(cells) {
    return [
      mergedCellHtml(cells[0] || {}, 5),
      cellHtml(cells[5] || {}),
      cellHtml(cells[6] || {}),
    ].join("");
  }

  function bodyTotalMergeRowHtml(cells) {
    return [
      cellHtml(cells[0] || {}),
      mergedCellHtml(cells[1] || {}, 4),
      cellHtml(cells[5] || {}),
      cellHtml(cells[6] || {}),
    ].join("");
  }

  function mergedCellHtml(cell, colspan) {
    const rowIndex = Number(cell?.row ?? 0);
    const columnIndex = Number(cell?.col ?? 0);
    const value = formatCellValue(valueForCell(rowIndex, columnIndex, cell?.value ?? ""), cell?.format || "", false);
    const style = normalizedCellStyle(cell, rowIndex, columnIndex);
    const editable = isEditableCell(cell, rowIndex, columnIndex);
    const autoDate = autoDateCellValue(rowIndex, columnIndex) !== null;
    const calcAttrs = cell?.formula ? ` data-calc-row="${rowIndex}" data-calc-col="${columnIndex}" data-format="${escapeAttribute(cell?.format || "")}"` : "";
    const classes = [
      cell?.formula ? "formula-cell" : "",
      editable ? "editable-cell" : "",
      autoDate ? "auto-date-cell" : "",
    ].filter(Boolean).join(" ");
    if (editable) {
      return `
        <td class="${classes}" colspan="${colspan}" style="${escapeAttribute(style)}">
          <input class="invoice-cell-input" type="text" value="${escapeHtml(value)}" data-row="${rowIndex}" data-col="${columnIndex}" />
        </td>
      `;
    }
    return `<td class="${classes}" colspan="${colspan}" style="${escapeAttribute(style)}"${calcAttrs}>${escapeHtml(value)}</td>`;
  }

  function isWideTitleRow(rowIndex) {
    return [0, 2, 11].includes(rowIndex);
  }

  function isLeftTotalMergeRow(rowIndex) {
    return [45, 48, 59, 65].includes(rowIndex);
  }

  function isBodyTotalMergeRow(rowIndex) {
    return [39, 41, 43, 61, 63].includes(rowIndex);
  }

  function isLabelMergeRow(rowIndex) {
    return [
      4, 6, 8,
      13, 15, 17, 19, 21,
      24, 26, 28, 30,
      33, 35, 36, 37,
      39, 41, 43,
      50, 52, 54, 56, 58,
      61, 63,
    ].includes(rowIndex);
  }

  function isUnitMergeRow(rowIndex) {
    return [
      15, 19, 21,
      26, 30,
      35, 36, 37,
      50, 52, 54, 56, 58,
      61, 63,
    ].includes(rowIndex);
  }

  function cellHtml(cell) {
    const rowIndex = Number(cell?.row ?? 0);
    const columnIndex = Number(cell?.col ?? 0);
    const value = formatCellValue(valueForCell(rowIndex, columnIndex, cell?.value ?? ""), cell?.format || "", false);
    const style = normalizedCellStyle(cell, rowIndex, columnIndex);
    const editable = isEditableCell(cell, rowIndex, columnIndex);
    const autoDate = autoDateCellValue(rowIndex, columnIndex) !== null;
    const calcAttrs = cell?.formula ? ` data-calc-row="${rowIndex}" data-calc-col="${columnIndex}" data-format="${escapeAttribute(cell?.format || "")}"` : "";
    const classes = [
      cell?.formula ? "formula-cell" : "",
      editable ? "editable-cell" : "",
      autoDate ? "auto-date-cell" : "",
    ].filter(Boolean).join(" ");
    if (editable) {
      return `
        <td class="${classes}" style="${escapeAttribute(style)}">
          <input class="invoice-cell-input" type="text" value="${escapeHtml(value)}" data-row="${rowIndex}" data-col="${columnIndex}" />
        </td>
      `;
    }
    return `<td class="${classes}" style="${escapeAttribute(style)}"${calcAttrs}>${escapeHtml(value)}</td>`;
  }

  function rowKey(rowIndex) {
    return `summary-vk-r${rowIndex + 1}`;
  }

  function isForcedReadonlyCell(rowIndex, columnIndex) {
    return false;
  }

  function isEditableCell(cell, rowIndex, columnIndex) {
    return Boolean(cell?.editable) && isMarkedInputCell(rowIndex, columnIndex);
  }

  function isMarkedInputCell(rowIndex, columnIndex) {
    return [
      "17:3", "19:3", "21:3", "26:3", "28:3", "30:3", "36:3", "37:3", "43:5", "50:3",
    ].includes(`${rowIndex}:${columnIndex}`);
  }

  // Excel 원본 (Summary(VK) 시트) 셀별 fill 색상. xlrd 로 추출한 값을 직접 박음.
  // 키: "row:col" (0-indexed). 값: CSS color.
  const EXCEL_FILL_MAP = {
    "4:3":"#FFFF99","4:5":"#FFFF99","6:3":"#FFFF99","8:3":"#FFFF99",
    "13:3":"#CCFFFF","19:3":"#FFFF99","21:3":"#FFFF99","24:3":"#CCFFFF",
    "30:3":"#FFFF99","33:3":"#FFFF99","39:5":"#FFFF00","45:5":"#CCFFFF",
    "48:5":"#CCFFFF","50:3":"#FFFF99","52:3":"#FFFF99","54:3":"#FFFF99",
    "56:3":"#FFFF99","58:3":"#FFFF99","59:5":"#CCFFFF","65:5":"#CCFFFF",
  };

  function excelFillFor(rowIndex, columnIndex) {
    return EXCEL_FILL_MAP[`${rowIndex}:${columnIndex}`] || "#ffffff";
  }

  function normalizedCellStyle(cell, rowIndex, columnIndex) {
    let style = cell?.style || "";
    // 서버가 emit 한 background 제거 → JS 매핑으로 덮어쓰기
    style = String(style).replace(/background(-color)?\s*:[^;]*;/gi, "");
    const fill = excelFillFor(rowIndex, columnIndex);
    style += `background:${fill};`;
    const autoDate = autoDateCellValue(rowIndex, columnIndex) !== null;
    if (isEditableCell(cell, rowIndex, columnIndex) || cell?.formula || autoDate) {
      style = removeBorderStyles(style);
      const width = isThickBorderCell(rowIndex, columnIndex) ? 1.4 : 0.7;
      style += `border:${width}px solid #000;`;
    }
    return style;
  }

  function removeBorderStyles(style) {
    return String(style || "")
      .replace(/border-(top|right|bottom|left):[^;]*;/g, "")
      .replace(/border:[^;]*;/g, "");
  }

  function isThickBorderCell(rowIndex, columnIndex) {
    return columnIndex === 5 && [39, 45, 48, 59, 65].includes(rowIndex);
  }

  function storedCell(rowIndex, columnIndex) {
    return state.cellRows.find((row) => row.row_key === rowKey(rowIndex) && Number(row.col_index || 0) === columnIndex + 1) || null;
  }

  function valueForCell(rowIndex, columnIndex, fallback) {
    const autoDateValue = autoDateCellValue(rowIndex, columnIndex);
    if (autoDateValue !== null) return autoDateValue;
    const calculatedValue = calculatedCellValue(rowIndex, columnIndex);
    if (calculatedValue !== null) return calculatedValue;
    const stored = storedCell(rowIndex, columnIndex);
    if (stored) return String(stored.cell_value ?? "");
    if (isValueColumn(columnIndex)) return "";
    return String(fallback ?? "");
  }

  function isValueColumn(columnIndex) {
    return columnIndex === 3 || columnIndex === 5;
  }

  function autoDateCellValue(rowIndex, columnIndex) {
    const firstDay = new Date(state.year, Number(state.month) - 1, 1);
    const lastDay = new Date(state.year, Number(state.month), 0);
    const invoiceDate = new Date(lastDay);
    invoiceDate.setDate(invoiceDate.getDate() + 10);
    if (rowIndex === 4 && columnIndex === 3) return formatDate(firstDay);
    if (rowIndex === 4 && columnIndex === 5) return formatDate(lastDay);
    if (rowIndex === 6 && columnIndex === 3) return formatDate(invoiceDate);
    if (rowIndex === 8 && columnIndex === 3) return String(lastDay.getDate());
    return null;
  }

  function formatDate(date) {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, "0");
    const day = String(date.getDate()).padStart(2, "0");
    return `${year}-${month}-${day}`;
  }

  function calculatedCellValue(rowIndex, columnIndex) {
    if (rowIndex === 13 && columnIndex === 3) return blankableCalc([15, 17, 19, 21], () => (round(n(15, 3) * n(17, 3), 0) + n(19, 3)) * n(21, 3));
    if (rowIndex === 24 && columnIndex === 3) return blankableCalc([26, 28, 30], () => round(n(26, 3) * n(28, 3), 0) * n(30, 3));
    if (rowIndex === 39 && columnIndex === 5) return blankableCalc([13, 24, 33, 35], () => roundup(n(13, 3) + n(24, 3) + n(33, 3) + n(35, 3), 0));
    if (rowIndex === 41 && columnIndex === 5) return blankableCalc([39], () => round(n(39, 5) * 0.1, 0));
    if (rowIndex === 45 && columnIndex === 5) return blankableCalc([39, 41, 43], () => n(39, 5) + n(41, 5) + n(43, 5));
    if (rowIndex === 48 && columnIndex === 5) return blankableCalc([50, 52, 54], () => roundup(n(50, 3) + n(52, 3) + n(54, 3), 0));
    if (rowIndex === 59 && columnIndex === 5) return blankableCalc([39, 48], () => n(39, 5) - n(48, 5));
    if (rowIndex === 61 && columnIndex === 5) return blankableCalc([59], () => round(n(59, 5) * 0.1, 0));
    if (rowIndex === 63 && columnIndex === 5) return blankableCalc([56], () => n(56, 3));
    if (rowIndex === 65 && columnIndex === 5) return blankableCalc([59, 61, 63], () => n(59, 5) + n(61, 5) - n(63, 5));
    return null;
  }

  function blankableCalc(dependencyRows, calculator) {
    const hasAnyValue = dependencyRows.some((rowIndex) => hasNumberValue(rowIndex, 3) || hasNumberValue(rowIndex, 5));
    if (!hasAnyValue) return "";
    const value = calculator();
    return Number.isFinite(value) ? String(value) : "";
  }

  function hasNumberValue(rowIndex, columnIndex) {
    return parseNumber(rawCellValue(rowIndex, columnIndex)) !== null;
  }

  function n(rowIndex, columnIndex) {
    return parseNumber(rawCellValue(rowIndex, columnIndex)) ?? 0;
  }

  function rawCellValue(rowIndex, columnIndex) {
    const autoDateValue = autoDateCellValue(rowIndex, columnIndex);
    if (autoDateValue !== null) return autoDateValue;
    const stored = storedCell(rowIndex, columnIndex);
    if (stored) return String(stored.cell_value ?? "");
    const calculatedValue = calculatedCellValue(rowIndex, columnIndex);
    if (calculatedValue !== null) return calculatedValue;
    return "";
  }

  function parseNumber(value) {
    const text = String(value ?? "").replace(/,/g, "").trim();
    if (!text || text === "-") return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function round(value, digits) {
    const factor = 10 ** digits;
    return Math.round((Number(value) + Number.EPSILON) * factor) / factor;
  }

  function roundup(value, digits) {
    const factor = 10 ** digits;
    const number = Number(value);
    if (!Number.isFinite(number)) return 0;
    return Math.ceil(number * factor) / factor;
  }

  function formatCellValue(value, format, editing) {
    const text = String(value ?? "").trim();
    if (!text || editing) return text;
    const normalizedFormat = String(format || "").toLowerCase();
    if (normalizedFormat.includes("m/") || normalizedFormat.includes("d/") || normalizedFormat.includes("yy")) return text;
    const numericText = text.replace(/,/g, "");
    if (!/^-?\d+(\.\d+)?$/.test(numericText)) return text;
    const number = Number(numericText);
    if (!Number.isFinite(number)) return text;
    const decimalMatch = normalizedFormat.match(/0\.([0#]+)/);
    const digits = decimalMatch ? decimalMatch[1].length : 0;
    return new Intl.NumberFormat("en-US", {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }).format(number);
  }

  async function handleCellChange(event) {
    const input = event.target;
    if (!(input instanceof HTMLInputElement) || !input.classList.contains("invoice-cell-input")) return;
    const rowIndex = Number(input.dataset.row);
    const columnIndex = Number(input.dataset.col);
    if (!Number.isFinite(rowIndex) || !Number.isFinite(columnIndex)) return;
    const existing = storedCell(rowIndex, columnIndex);
    const payload = {
      month: monthKey(),
      year_no: state.year,
      month_no: Number(state.month),
      table_name: SUMMARY_TABLE,
      row_key: rowKey(rowIndex),
      col_index: columnIndex + 1,
      cell_value: input.value,
    };
    const response = await fetch(existing ? `${TABLES_BASE}/${CELL_TABLE}/${existing.id}` : `${TABLES_BASE}/${CELL_TABLE}`, {
      method: existing ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!response.ok) return;
    const saved = await response.json();
    const index = state.cellRows.findIndex((row) => row.id === saved.id);
    if (index >= 0) state.cellRows[index] = saved;
    else state.cellRows.push(saved);
    renderCalculatedCells();
  }

  function renderCalculatedCells() {
    tableShell.querySelectorAll("[data-calc-row][data-calc-col]").forEach((cell) => {
      const rowIndex = Number(cell.dataset.calcRow);
      const columnIndex = Number(cell.dataset.calcCol);
      const format = cell.dataset.format || "";
      cell.textContent = formatCellValue(valueForCell(rowIndex, columnIndex, ""), format, false);
    });
  }

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function escapeAttribute(value) {
    return String(value ?? "").replace(/"/g, "&quot;");
  }
});
