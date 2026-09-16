document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "srf-boiler-invoice-operation.html") return;

  const PERIOD_KEY = "steamlog:shared-period";
  const CELL_TABLE = "table_cell_value";
  const OPERATION_TABLE = "srf_boiler_invoice_operation";
  const TABLES_BASE = "/tables";
  const yearSelect = document.getElementById("invoiceYear");
  const monthSelect = document.getElementById("invoiceMonth");
  const searchButton = document.getElementById("invoiceSearch");
  const tableShell = document.getElementById("invoiceTableShell");
  const sheetLabel = document.getElementById("invoiceSheetLabel");
  if (!yearSelect || !monthSelect || !searchButton || !tableShell) return;

  const now = new Date();
  const saved = readPeriod();
  const state = {
    year: Number(saved.year) || now.getFullYear(),
    month: String(saved.month || now.getMonth() + 1).padStart(2, "0"),
    cellRows: [],
    payload: null,
  };

  initPeriodControls();
  bindEvents();
  loadOperation();

  function monthKey() {
    return `${state.year}-${state.month}`;
  }

  function initPeriodControls() {
    const currentYear = now.getFullYear();
    yearSelect.innerHTML = "";
    for (let year = currentYear - 5; year <= currentYear + 2; year += 1) {
      const option = document.createElement("option");
      option.value = String(year);
      option.textContent = `${year}년`;
      yearSelect.appendChild(option);
    }
    monthSelect.innerHTML = "";
    for (let month = 1; month <= 12; month += 1) {
      const option = document.createElement("option");
      option.value = String(month).padStart(2, "0");
      option.textContent = `${month}월`;
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
      loadOperation();
    });
    yearSelect.addEventListener("change", writePeriodFromSelects);
    monthSelect.addEventListener("change", writePeriodFromSelects);
    tableShell.addEventListener("change", handleCellChange);
    tableShell.addEventListener("input", renderCalculatedCells);
    tableShell.addEventListener("keydown", (event) => {
      if (event.key === "Enter" && event.target instanceof HTMLInputElement) event.target.blur();
    });
    window.addEventListener("storage", (event) => {
      if (event.key !== PERIOD_KEY) return;
      const period = readPeriod();
      if (!period.year || !period.month) return;
      state.year = Number(period.year);
      state.month = String(period.month).padStart(2, "0");
      yearSelect.value = String(state.year);
      monthSelect.value = state.month;
      loadOperation();
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

  async function loadOperation() {
    tableShell.innerHTML = `<div class="invoice-empty">로딩 중</div>`;
    try {
      const [operationResponse, cellsResponse] = await Promise.all([
        fetch(`/tables/srf-boiler-invoice/operation?year=${encodeURIComponent(state.year)}&month=${encodeURIComponent(Number(state.month))}`),
        fetch(`${TABLES_BASE}/${CELL_TABLE}?page=1&limit=2000&month=${encodeURIComponent(monthKey())}`),
      ]);
      const payload = await operationResponse.json();
      if (!operationResponse.ok) throw new Error(payload.message || "데이터를 불러오지 못했습니다.");
      const cellsPayload = cellsResponse.ok ? await cellsResponse.json() : { data: [] };
      state.cellRows = Array.isArray(cellsPayload?.data)
        ? cellsPayload.data.filter((row) => row.table_name === OPERATION_TABLE)
        : [];
      state.payload = payload;
      renderTable(payload);
      if (sheetLabel) sheetLabel.textContent = payload.sheet || "운영내역";
    } catch (error) {
      tableShell.innerHTML = `<div class="invoice-empty">${escapeHtml(error.message || "데이터를 불러오지 못했습니다.")}</div>`;
    }
  }

  function renderTable(payload) {
    const columns = Array.isArray(payload.columns) ? payload.columns : [];
    const rows = Array.isArray(payload.rows) ? payload.rows : [];
    if (!rows.length) {
      tableShell.innerHTML = `<div class="invoice-empty">표시할 데이터가 없습니다.</div>`;
      return;
    }
    // 컬럼 폭을 px 가 아닌 % 로 변환하여 컨테이너 폭에 비례하도록 반응형 처리.
    const totalColWidth = columns.reduce((sum, c) => sum + (Number(c.width) || 64), 0) || 1;
    const colgroup = columns.map((column) => {
      const pct = ((Number(column.width) || 64) / totalColWidth * 100).toFixed(3);
      return `<col style="width:${pct}%">`;
    }).join("");
    // row 0 (제목) / row 1 (빈 행) 은 화면에서 제외. tr 자체를 렌더링하지 않음.
    const visibleRows = rows.filter((row) => {
      const idx = Number(row.index);
      return idx !== 0 && idx !== 1;
    });
    const body = visibleRows.map((row) => {
      const cells = Array.isArray(row.cells) ? row.cells : [];
      return `<tr>${cells.map((cell) => cellHtml(cell, payload)).join("")}</tr>`;
    }).join("");
    tableShell.innerHTML = `<table class="invoice-table"><colgroup>${colgroup}</colgroup><tbody>${body}</tbody></table>`;
    renderCalculatedCells();
  }

  // 헤더 행(원본 row 2) 의 특정 컬럼에 다른 페이지 출처를 툴팁으로 안내.
  //   col 3 -> 복합보일러 스팀생산실적 의 스팀구매량(T/D)
  //   col 5 -> 복합보일러 스팀생산실적 의 공정수판매량
  //   col 7 -> 복합보일러 스팀생산실적 의 탈기기급수량
  const HEADER_SOURCE_HINTS = {
    "2-3": "복합보일러 스팀생산실적 ▸ 스팀구매량 (T/D) 에서 자동으로 가져옵니다",
    "2-5": "복합보일러 스팀생산실적 ▸ 공정수판매량 (㎥/D) 에서 자동으로 가져옵니다",
    "2-7": "복합보일러 스팀생산실적 ▸ 탈기기급수량 (㎥/D) 에서 자동으로 가져옵니다",
  };

  function headerSourceHint(rowIndex, columnIndex) {
    return HEADER_SOURCE_HINTS[`${rowIndex}-${columnIndex}`] || "";
  }

  function cellHtml(cell, payload) {
    const rowIndex = Number(cell?.row ?? 0);
    const columnIndex = Number(cell?.col ?? 0);
    if (isCoveredByMerge(rowIndex, columnIndex, payload)) return "";
    const merge = startingMerge(rowIndex, columnIndex, payload);
    const value = formatCellValue(valueForCell(rowIndex, columnIndex, cell?.value ?? ""), cell?.format || "", false);
    const style = normalizedCellStyle(cell, value);
    const editable = Boolean(cell?.editable);
    const linked = isLinkedCell(rowIndex, columnIndex);
    const calcAttrs = cell?.formula ? ` data-calc-row="${rowIndex}" data-calc-col="${columnIndex}" data-format="${escapeAttribute(cell?.format || "")}"` : "";
    const spanAttrs = `${merge?.rowspan > 1 ? ` rowspan="${merge.rowspan}"` : ""}${merge?.colspan > 1 ? ` colspan="${merge.colspan}"` : ""}`;
    const hint = headerSourceHint(rowIndex, columnIndex);
    const titleAttr = hint ? ` title="${escapeAttribute(hint)}"` : "";
    const classes = [
      cell?.formula ? "formula-cell" : "",
      editable ? "editable-cell" : "",
      linked ? "linked-cell" : "",
      hint ? "linked-source-header" : "",
    ].filter(Boolean).join(" ");
    if (editable) {
      return `
        <td class="${classes}" style="${escapeAttribute(style)}"${spanAttrs}${titleAttr}>
          <input class="invoice-cell-input" type="text" value="${escapeHtml(value)}" data-row="${rowIndex}" data-col="${columnIndex}" />
        </td>
      `;
    }
    return `<td class="${classes}" style="${escapeAttribute(style)}"${spanAttrs}${calcAttrs}${titleAttr}>${escapeHtml(value)}</td>`;
  }

  function startingMerge(rowIndex, columnIndex, payload) {
    return (payload.merges || []).find((merge) => Number(merge.row) === rowIndex && Number(merge.col) === columnIndex) || null;
  }

  function isCoveredByMerge(rowIndex, columnIndex, payload) {
    return (payload.merges || []).some((merge) => {
      const row = Number(merge.row);
      const col = Number(merge.col);
      const rowspan = Number(merge.rowspan) || 1;
      const colspan = Number(merge.colspan) || 1;
      if (rowIndex === row && columnIndex === col) return false;
      return rowIndex >= row && rowIndex < row + rowspan && columnIndex >= col && columnIndex < col + colspan;
    });
  }

  function isLinkedCell(rowIndex, columnIndex) {
    // 복합보일러 스팀생산실적과 연동되는 셀:
    //  col 3 = 스팀생산량 (comboSteam)
    //  col 5 = 공정수 사용량 (comboProcess)
    //  col 7 = 탈기기급수량 (comboDeaerator)
    return rowIndex >= 4 && rowIndex <= 34 && [3, 5, 7].includes(columnIndex);
  }

  // 엑셀 cell 의 inline style (배경색, 테두리, 폰트) 을 모두 버리고,
  // 텍스트 정렬만 결정. 나머지는 CSS 클래스 기반으로 처리하여 일반 표처럼 보이게 한다.
  function normalizedCellStyle(cell, value) {
    return `text-align:${isNumericValue(value) ? "right" : "center"};`;
  }

  function rowKey(rowIndex) {
    return `operation-r${rowIndex + 1}`;
  }

  function storedCell(rowIndex, columnIndex) {
    return state.cellRows.find((row) => row.row_key === rowKey(rowIndex) && Number(row.col_index || 0) === columnIndex + 1) || null;
  }

  function valueForCell(rowIndex, columnIndex, fallback) {
    // 링크된 셀 (col 3/5/7) 은 서버가 combo 값을 fallback 으로 내려주므로 stored 무시.
    // (과거 editable 이었던 시기에 srf_boiler_invoice_operation 에 저장된 잔재 무시)
    if (isLinkedCell(rowIndex, columnIndex)) return String(fallback ?? "");
    const calculated = calculatedCellValue(rowIndex, columnIndex);
    if (calculated !== null) return calculated;
    const stored = storedCell(rowIndex, columnIndex);
    if (stored) return String(stored.cell_value ?? "");
    return String(fallback ?? "");
  }

  function calculatedCellValue(rowIndex, columnIndex) {
    if (rowIndex >= 4 && rowIndex <= 34) {
      if (columnIndex === 4) return blankableCalc([rowIndex, 3], () => n(rowIndex, 3) / 24);
      if (columnIndex === 8) return blankableCalc([rowIndex, 3], () => n(rowIndex, 3) * 0.7);
      if (columnIndex === 6) return blankableCalc([rowIndex, 7, rowIndex, 8], () => n(rowIndex, 7) - n(rowIndex, 8));
    }
    if (rowIndex === 35 && columnIndex >= 3 && columnIndex <= 11) {
      if (columnIndex === 4) {
        const totalSteam = sumColumn(3);
        const days = new Date(state.year, Number(state.month), 0).getDate();
        return totalSteam === "" ? "" : totalSteam / 24 / days;
      }
      const sum = sumColumn(columnIndex);
      return sum === "" ? "" : sum;
    }
    return null;
  }

  function sumColumn(columnIndex) {
    let sum = 0;
    let hasValue = false;
    for (let rowIndex = 4; rowIndex <= 34; rowIndex += 1) {
      const value = parseNumber(rawCellValue(rowIndex, columnIndex));
      if (value === null) continue;
      sum += value;
      hasValue = true;
    }
    return hasValue ? sum : "";
  }

  function blankableCalc(dependency, calculator) {
    let hasValue = false;
    for (let index = 0; index < dependency.length; index += 2) {
      if (parseNumber(rawCellValue(dependency[index], dependency[index + 1])) !== null) {
        hasValue = true;
        break;
      }
    }
    if (!hasValue) return "";
    const value = calculator();
    return Number.isFinite(value) ? value : "";
  }

  function rawCellValue(rowIndex, columnIndex) {
    const calculated = calculatedCellValue(rowIndex, columnIndex);
    if (calculated !== null) return calculated;
    const stored = storedCell(rowIndex, columnIndex);
    if (stored) return String(stored.cell_value ?? "");
    const cell = findPayloadCell(rowIndex, columnIndex);
    return String(cell?.value ?? "");
  }

  function findPayloadCell(rowIndex, columnIndex) {
    const rows = Array.isArray(state.payload?.rows) ? state.payload.rows : [];
    const row = rows.find((item) => Number(item.index) === rowIndex);
    return (row?.cells || []).find((cell) => Number(cell.row) === rowIndex && Number(cell.col) === columnIndex) || null;
  }

  function n(rowIndex, columnIndex) {
    return parseNumber(rawCellValue(rowIndex, columnIndex)) ?? 0;
  }

  function parseNumber(value) {
    const text = String(value ?? "").replace(/,/g, "").trim();
    if (!text || text === "-") return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function isNumericValue(value) {
    const text = String(value ?? "").replace(/,/g, "").trim();
    return /^-?\d+(\.\d+)?$/.test(text);
  }

  function formatCellValue(value, format, editing) {
    const text = String(value ?? "").trim();
    if (!text || editing) return text;
    const numericText = text.replace(/,/g, "");
    if (!/^-?\d+(\.\d+)?$/.test(numericText)) return text;
    const number = Number(numericText);
    if (!Number.isFinite(number)) return text;
    const normalizedFormat = String(format || "").toLowerCase();
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
      table_name: OPERATION_TABLE,
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
    const index = state.cellRows.findIndex((row) => Number(row.id) === Number(saved.id));
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
