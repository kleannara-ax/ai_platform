document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  const params = new URLSearchParams(window.location.search);
  const requestedTab = params.get("tab");
  const requestedPage = params.get("page");

  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  const summaryExcelDownloadButton = document.getElementById("summaryExcelDownload");
  const summaryExcelUploadButton = document.getElementById("summaryExcelUpload");
  const summaryExcelFileInput = document.getElementById("summaryExcelFileInput");
  const isSummaryPage = page === "unit-fluidized-summary.html" || requestedPage === "unit-fluidized-summary.html" || Boolean(summaryExcelDownloadButton);
  if (!isSummaryPage) return;
  if (!yearSelect || !monthSelect) return;

  const CELL_TABLE = "table_cell_value";
  const cellRows = [];
  const detailCellsByMonth = new Map();
  let loaded = false;
  let loadedDetailYears = "";
  let excelUploadInFlight = false;
  let activeTabKey = "plan";
  const dirtyInputs = new Set();
  const pendingSaveTimers = new WeakMap();

  const PLAN_TABLE = "fluidized_plan_actual";
  const SUMMARY_TABLE = "fluidized_plan_summary";
  const COMPARE_TABLE = "fluidized_year_compare";
  const NOTE_TABLE = "fluidized_year_note";
  const CONTRACT_TABLE = "fluidized_contract_daily";
  const COST_TABLE = "fluidized_contract_cost";

  const PLAN_ROWS = [
    { key: "operating", label: "\uC6B4\uC601\uBE44" },
    { key: "power", label: "\uC804\uB825\uBE44" },
    { key: "waste", label: "\uD3D0\uAE30\uBB3C\uCC98\uB9AC\uBE44" },
    { key: "srf_income", label: "SRF\uC218\uC785\uAE08" },
    { key: "total", label: "\uCD1D \uAE08\uC561" },
  ];

  function yearValue() {
    return yearSelect.value || String(new Date().getFullYear());
  }

  function monthValue() {
    return monthSelect.value || "01";
  }

  function monthKey(year = yearValue(), month = monthValue()) {
    return `${year}-${month}`;
  }

  function daysInMonth(year = Number(yearValue()), month = Number(monthValue())) {
    return new Date(year, month, 0).getDate();
  }

  function yearAnchorKey(year = yearValue()) {
    return `${year}-01`;
  }

  function monthLabel(monthIndex) {
    return `${monthIndex + 1}\uC6D4`;
  }

  function currentYearShort() {
    return yearValue().slice(2);
  }

  function previousMonthIndex() {
    const month = Number(monthValue());
    return month > 1 ? month - 2 : 11;
  }

  function previousMonthKey() {
    const year = Number(yearValue());
    const month = Number(monthValue());
    if (month > 1) return `${year}-${String(month - 1).padStart(2, "0")}`;
    return `${year - 1}-12`;
  }

  function escapeAttr(value) {
    return String(value ?? "").replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
  }

  function parseNumber(value) {
    if (value === null || value === undefined) return 0;
    const cleaned = String(value).replace(/,/g, "").replace(/%/g, "").trim();
    if (!cleaned) return 0;
    const number = Number(cleaned);
    return Number.isFinite(number) ? number : 0;
  }

  function normalizeEditableValue(value) {
    return String(value ?? "").replace(/,/g, "").trim();
  }

  function formatEditableValue(value) {
    const normalized = normalizeEditableValue(value);
    if (!normalized) return "";
    const number = Number(normalized);
    if (!Number.isFinite(number)) return normalized;
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(number);
  }

  function editableRawValue(input) {
    return document.activeElement === input ? normalizeEditableValue(input.value) : (input.dataset.rawValue ?? normalizeEditableValue(input.value));
  }

  function formatNumber(value, digits = 2) {
    if (!Number.isFinite(value)) return "";
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: digits,
    }).format(value);
  }

  function formatInt(value) {
    if (!Number.isFinite(value)) return "";
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 0,
    }).format(value);
  }

  function formatRate(value) {
    if (!Number.isFinite(value)) return "";
    return `${new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: 2,
    }).format(value * 100)}%`;
  }

  function formatSigned(value, digits = 2) {
    const sign = value > 0 ? "+" : "";
    return `${sign}${formatNumber(value, digits)}`;
  }

  async function fetchPaged(tableName, options = {}) {
    // 최적화: limit 5000 으로 키워 단일 요청에 보통 다 들어가도록 함.
    // 2페이지 이상 필요할 때만 나머지 페이지를 병렬 fetch.
    const LIMIT = 5000;
    const buildUrl = (pageNo) => {
      const params = new URLSearchParams({ limit: String(LIMIT), page: String(pageNo) });
      if (options.fromMonth) params.set("fromMonth", options.fromMonth);
      if (options.toMonth) params.set("toMonth", options.toMonth);
      return `tables/${tableName}?${params.toString()}`;
    };
    const first = await fetch(buildUrl(1));
    if (!first.ok) return [];
    const firstPayload = await first.json();
    const data = (firstPayload.data || []).slice();
    const total = firstPayload.total || data.length;
    const limit = firstPayload.limit || LIMIT;
    const totalPages = Math.max(1, Math.ceil(total / limit));
    if (totalPages > 1) {
      const pageNumbers = Array.from({ length: totalPages - 1 }, (_, i) => i + 2);
      const responses = await Promise.all(pageNumbers.map((pn) => fetch(buildUrl(pn))));
      for (const res of responses) {
        if (!res.ok) continue;
        const payload = await res.json();
        data.push(...(payload.data || []));
      }
    }
    return data;
  }

  function getCellRow(tableName, targetMonth, rowKey, colIndex) {
    return cellRows.find((row) =>
      row.table_name === tableName &&
      row.month === targetMonth &&
      String(row.row_key) === String(rowKey) &&
      Number(row.col_index) === colIndex
    ) || null;
  }

  function getCellValue(tableName, targetMonth, rowKey, colIndex) {
    return getCellRow(tableName, targetMonth, rowKey, colIndex)?.cell_value ?? "";
  }

  async function upsertCell(payload, existingId) {
    const url = existingId ? `tables/${CELL_TABLE}/${existingId}` : `tables/${CELL_TABLE}`;
    const response = await fetch(url, {
      method: existingId ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(existingId ? payload : { table_name: CELL_TABLE, ...payload }),
    });
    if (!response.ok) return null;
    return response.json();
  }

  async function saveCell(input) {
    if (!input || input.readOnly || input.classList.contains("calc-input")) return;
    const tableName = input.dataset.tableName;
    const targetMonth = input.dataset.monthKey;
    const rowKey = input.dataset.rowKey;
    const colIndex = Number(input.dataset.colIndex);
    if (!tableName || !targetMonth || !rowKey || !Number.isFinite(colIndex)) return;

    const payload = {
      month: targetMonth,
      year_no: Number(targetMonth.slice(0, 4)),
      month_no: Number(targetMonth.slice(5, 7)),
      table_name: tableName,
      row_key: rowKey,
      col_index: colIndex,
      cell_value: editableRawValue(input),
    };
    const existing = getCellRow(tableName, targetMonth, rowKey, colIndex);
    const saved = await upsertCell(payload, existing?.id);
    if (!saved) return;

    const index = cellRows.findIndex((row) => row.id === saved.id);
    if (index >= 0) cellRows[index] = saved;
    else cellRows.push(saved);
    input.dataset.rawValue = normalizeEditableValue(saved.cell_value);
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

  function getPlanValueForYear(rowKey, monthIndex, kindIndex, targetYear) {
    if (kindIndex === 1) return getActualValueForYear(rowKey, monthIndex, targetYear);
    if (String(rowKey) === "total") {
      return PLAN_ROWS
        .filter((row) => row.key !== "total")
        .reduce((sum, row) => sum + getPlanValueForYear(row.key, monthIndex, kindIndex, targetYear), 0);
    }
    const targetMonth = monthKey(String(targetYear), String(monthIndex + 1).padStart(2, "0"));
    const saved = getCellValue(PLAN_TABLE, targetMonth, rowKey, kindIndex);
    if (saved !== "") return parseNumber(saved);
    return 0;
  }

  function getPlanInputValue(rowKey, monthIndex, kindIndex) {
    if (kindIndex === 1) return getActualValueForYear(rowKey, monthIndex, yearValue());
    if (String(rowKey) === "total") {
      return PLAN_ROWS
        .filter((row) => row.key !== "total")
        .reduce((sum, row) => sum + (getPlanInputValue(row.key, monthIndex, kindIndex) || 0), 0);
    }
    const targetMonth = monthKey(yearValue(), String(monthIndex + 1).padStart(2, "0"));
    const saved = getCellValue(PLAN_TABLE, targetMonth, rowKey, kindIndex);
    return saved !== "" ? parseNumber(saved) : null;
  }

  function detailNumber(targetMonth, cellRef) {
    const value = detailCellsByMonth.get(targetMonth)?.[cellRef];
    return parseNumber(value);
  }

  function detailSum(targetMonth, startRow, endRow) {
    let total = 0;
    for (let row = startRow; row <= endRow; row += 1) {
      total += detailNumber(targetMonth, `AK${row}`);
    }
    return total;
  }

  function getActualValueForYear(rowKey, monthIndex, targetYear) {
    const targetMonth = monthKey(String(targetYear), String(monthIndex + 1).padStart(2, "0"));
    if (String(rowKey) === "operating") return detailNumber(targetMonth, "AK16") / 1000000;
    // 전력비: 세부 운영내역 row 65 (전력량(KW) Main) 기준. 서버에서 AK65 에 저장.
    if (String(rowKey) === "power") return detailNumber(targetMonth, "AK65") / 1000000;
    // 폐기물처리비: Excel '1.운영실적'!AD7 = SUM('3.세부 운영내역'!AK49:AK62)/10^6
    // (소각재 r49 ~ 바닥재반출 r62. SRF 반입/사용/재고 r46~48 은 별도 행에서 처리)
    if (String(rowKey) === "waste") return detailSum(targetMonth, 49, 62) / 1000000;
    // SRF 수입금 실적: 세부 운영내역 row 46 (SRF(KG) 반입)의 일별 합계 / 10^6 (음수 포함)
    if (String(rowKey) === "srf_income") return detailNumber(targetMonth, "AK46") / 1000000;
    if (String(rowKey) === "total") {
      return PLAN_ROWS
        .filter((row) => row.key !== "total")
        .reduce((sum, row) => sum + getActualValueForYear(row.key, monthIndex, targetYear), 0);
    }
    return 0;
  }

  function getSummaryNoteValue() {
    const saved = getCellValue(SUMMARY_TABLE, monthKey(), "monthly-summary", 0);
    return saved !== "" ? saved : "";
  }

  function getNoteValue(monthIndex) {
    const saved = getCellValue(NOTE_TABLE, yearAnchorKey(), `note-${String(monthIndex + 1).padStart(2, "0")}`, 0);
    return saved !== "" ? saved : "";
  }

  function getDailyValue(dayIndex, type, colIndex) {
    const rowKey = String(dayIndex + 1).padStart(2, "0");
    const saved = getCellValue(CONTRACT_TABLE, monthKey(), rowKey, colIndex);
    if (saved !== "") return parseNumber(saved);
    return null;
  }

  function getCostValue(type, colIndex) {
    const saved = getCellValue(COST_TABLE, monthKey(), "cost", colIndex);
    if (saved !== "") return parseNumber(saved);
    return null;
  }

  function isNumericEditableCell(tableName, rowKey, colIndex, readonly) {
    if (readonly) return false;
    if (tableName === NOTE_TABLE) return false;
    if (tableName === SUMMARY_TABLE && String(rowKey) === "monthly-summary" && Number(colIndex) === 0) return false;
    return true;
  }

  function buildInput(tableName, targetMonth, rowKey, colIndex, value, readonly = false, cssClass = "") {
    const display = isNumericEditableCell(tableName, rowKey, colIndex, readonly)
      ? formatEditableValue(value)
      : (typeof value === "number" ? formatNumber(value) : String(value ?? ""));
    const classes = ["table-edit-input"];
    if (readonly) classes.push("calc-input");
    if (cssClass) classes.push(cssClass);
    return `<input class="${classes.join(" ")}" data-table-name="${tableName}" data-month-key="${targetMonth}" data-row-key="${rowKey}" data-col-index="${colIndex}" data-raw-value="${escapeAttr(normalizeEditableValue(value))}" value="${escapeAttr(display)}"${readonly ? ' readonly tabindex="-1"' : ""}>`;
  }

  function renderPlanTable() {
    const months = Array.from({ length: 12 }, (_, index) => index);
    const headerTop = months.map((index) => `<th colspan="3">${monthLabel(index)}</th>`).join("") + `<th colspan="3">\uD569 \uACC4</th>`;
    const headerBottom = Array.from({ length: 13 }, () => `<th>\uACC4\uD68D</th><th>\uC2E4\uC801</th><th>\uC99D\uAC10</th>`).join("");

    const body = PLAN_ROWS.map((row) => {
      const cells = months.map((index) => {
        const targetMonth = monthKey(yearValue(), String(index + 1).padStart(2, "0"));
        const plan = getPlanInputValue(row.key, index, 0);
        const actual = getPlanInputValue(row.key, index, 1);
        const delta = parseNumber(actual) - parseNumber(plan);
        const readonlyPlan = row.key === "total";
        return [
          `<td>${buildInput(PLAN_TABLE, targetMonth, row.key, 0, formatNumber(plan), readonlyPlan)}</td>`,
          `<td>${buildInput(PLAN_TABLE, targetMonth, row.key, 1, formatNumber(actual), true)}</td>`,
          `<td>${buildInput(PLAN_TABLE, targetMonth, row.key, 2, formatNumber(delta), true)}</td>`,
        ].join("");
      }).join("");

      const totalPlan = months.reduce((sum, index) => sum + getPlanValueForYear(row.key, index, 0, yearValue()), 0);
      const totalActual = months.reduce((sum, index) => sum + getPlanValueForYear(row.key, index, 1, yearValue()), 0);
      const totalDelta = totalActual - totalPlan;

      return `<tr>
        <td>${escapeHtml(row.label)}</td>
        ${cells}
        <td>${buildInput(PLAN_TABLE, yearAnchorKey(), `${row.key}-sum`, 0, formatNumber(totalPlan), true)}</td>
        <td>${buildInput(PLAN_TABLE, yearAnchorKey(), `${row.key}-sum`, 1, formatNumber(totalActual), true)}</td>
        <td>${buildInput(PLAN_TABLE, yearAnchorKey(), `${row.key}-sum`, 2, formatNumber(totalDelta), true)}</td>
      </tr>`;
    }).join("");

    document.getElementById("planTableTitle").textContent = `1. ${yearValue()}년 유동상 소각로 위탁 운영비용 사업계획대비 실적 (단위: 백만원)`;
    document.getElementById("planTableWrap").innerHTML = `
      <table class="data-table fluidized-plan-table">
        <colgroup>
          <col class="plan-col-label">
          ${Array.from({ length: 39 }, () => `<col class="plan-col-value">`).join("")}
        </colgroup>
        <thead>
          <tr><th rowspan="2">\uAD6C \uBD84</th>${headerTop}</tr>
          <tr>${headerBottom}</tr>
        </thead>
        <tbody>${body}</tbody>
      </table>
    `;
  }

  function renderSummaryTable() {
    const monthIndex = Number(monthValue()) - 1;
    const prevIndex = previousMonthIndex();
    const totalPlan = PLAN_ROWS.reduce((sum, row) => sum + getPlanValueForYear(row.key, monthIndex, 0, yearValue()), 0);
    const totalActual = PLAN_ROWS.reduce((sum, row) => sum + getPlanValueForYear(row.key, monthIndex, 1, yearValue()), 0);
    const totalDelta = totalActual - totalPlan;
    const accumulatedPlan = PLAN_ROWS.reduce((sum, row) => sum + Array.from({ length: monthIndex + 1 }, (_, i) => getPlanValueForYear(row.key, i, 0, yearValue())).reduce((a, b) => a + b, 0), 0);
    const accumulatedActual = PLAN_ROWS.reduce((sum, row) => sum + Array.from({ length: monthIndex + 1 }, (_, i) => getPlanValueForYear(row.key, i, 1, yearValue())).reduce((a, b) => a + b, 0), 0);
    const accumulatedDelta = accumulatedActual - accumulatedPlan;
    const previousActual = PLAN_ROWS.reduce((sum, row) => sum + getPlanValueForYear(row.key, prevIndex, 1, yearValue()), 0);
    const compareDelta = totalActual - previousActual;
    const compareRate = previousActual !== 0 ? compareDelta / previousActual : 0;

    document.getElementById("summaryTableTitle").textContent = `\uC694\uC57D (${Number(monthValue())}\uC6D4 \uAE30\uC900)`;
    document.getElementById("summaryTableWrap").innerHTML = `
      <table class="data-table">
        <thead>
          <tr>
            <th>\uAD6C\uBD84</th>
            <th>\`${currentYearShort()}\uB144 ${Number(monthValue())}\uC6D4 \uACC4\uD68D</th>
            <th>\`${currentYearShort()}\uB144 ${Number(monthValue())}\uC6D4 \uC2E4\uC801</th>
            <th>\`${currentYearShort()}\uB144 ${Number(monthValue())}\uC6D4 \uC99D\uAC10</th>
            <th>'${currentYearShort()}\uB144 \uB204\uACC4 \uACC4\uD68D</th>
            <th>'${currentYearShort()}\uB144 \uB204\uACC4 \uC2E4\uC801</th>
            <th>'${currentYearShort()}\uB144 \uB204\uACC4 \uC99D\uAC10</th>
            <th>\`${currentYearShort()}\uB144 ${prevIndex + 1}\uC6D4 \uC2E4\uC801</th>
            <th>\`${currentYearShort()}\uB144 ${prevIndex + 1}\uC6D4 \uB300\uBE44 \uC99D\uAC10</th>
            <th>\`${currentYearShort()}\uB144 ${prevIndex + 1}\uC6D4 \uB300\uBE44 \uC99D\uAC10\uB960</th>
            <th>\uBE44\uACE0</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>\uC704\uD0C1\uC6B4\uC601\uBE44</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 1, formatNumber(totalPlan), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 2, formatNumber(totalActual), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 3, formatNumber(totalDelta), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 4, formatNumber(accumulatedPlan), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 5, formatNumber(accumulatedActual), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 6, formatNumber(accumulatedDelta), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 7, formatNumber(previousActual), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 8, formatNumber(compareDelta), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 9, formatRate(compareRate), true)}</td>
            <td>${buildInput(SUMMARY_TABLE, monthKey(), "monthly-summary", 0, getSummaryNoteValue())}</td>
          </tr>
        </tbody>
      </table>
    `;
  }

  function renderCompareTable() {
    // 실적비교 (원본 엑셀 sheet1 section 2 / rows 16-21).
    // 계획대비실적표의 각 월 "실적" 총금액(= operating + power + waste + srf_income, 백만원)을
    // 전년/금년 두 행으로 자동 채우고, 세 번째 행은 증감(= 금년 - 전년).
    // colgroup 으로 컬럼 폭을 고정하고, 라벨/값 셀에 명시적 클래스를 부여해 nth-child 기반
    // 정렬 규칙으로 인한 행 스타일 불일치를 차단.
    const months = Array.from({ length: 12 }, (_, index) => index);
    const currentYear = Number(yearValue());
    const previousYear = currentYear - 1;

    const previousActuals = months.map((index) => getPlanValueForYear("total", index, 1, previousYear));
    const currentActuals = months.map((index) => getPlanValueForYear("total", index, 1, currentYear));
    const deltas = months.map((index) => currentActuals[index] - previousActuals[index]);
    // 원본 엑셀 sheet1 R19-R21 에는 마지막 "합계" 컬럼이 존재. 12개월 sum 으로 추가.
    const sumOf = (arr) => arr.reduce((acc, v) => acc + (Number.isFinite(v) ? v : 0), 0);
    const previousTotal = sumOf(previousActuals);
    const currentTotal = sumOf(currentActuals);
    const deltaTotal = currentTotal - previousTotal;

    const formatCell = (value) => Number.isFinite(value) && value !== 0 ? formatNumber(value) : "";
    const valueCells = (values) => values.map((value) => `<td class="compare-cell-value">${formatCell(value)}</td>`).join("");

    document.getElementById("compareTableTitle").textContent = `2. ${yearValue()}년 유동상 소각로 위탁 운영비용 전년도 실적 및 비교 (단위: 백만원)`;
    document.getElementById("compareTableWrap").innerHTML = `
      <table class="data-table fluidized-compare-table">
        <colgroup>
          <col class="compare-col-label-primary">
          <col class="compare-col-label-secondary">
          ${months.map(() => `<col class="compare-col-month">`).join("")}
          <col class="compare-col-month">
        </colgroup>
        <thead>
          <tr>
            <th colspan="2">구 분</th>
            ${months.map((index) => `<th>${monthLabel(index)}</th>`).join("")}
            <th>합계</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td class="compare-cell-label" rowspan="3">유동상 소각로 (합계)</td>
            <td class="compare-cell-label">${previousYear}실적</td>
            ${valueCells(previousActuals)}
            <td class="compare-cell-value"><strong>${formatCell(previousTotal)}</strong></td>
          </tr>
          <tr>
            <td class="compare-cell-label">${currentYear}실적</td>
            ${valueCells(currentActuals)}
            <td class="compare-cell-value"><strong>${formatCell(currentTotal)}</strong></td>
          </tr>
          <tr>
            <td class="compare-cell-label">증감</td>
            ${valueCells(deltas)}
            <td class="compare-cell-value"><strong>${formatCell(deltaTotal)}</strong></td>
          </tr>
        </tbody>
      </table>
    `;
  }

  function renderNoteTable() {
    // 월별 특이사항 — 사용자 직접 입력. NOTE_TABLE 의 row_key=`note-MM`, col_index=0 에 저장.
    const months = Array.from({ length: 12 }, (_, index) => index);
    document.getElementById("noteTableWrap").innerHTML = `
      <table class="data-table">
        <thead>
          <tr><th>월</th><th>특이사항</th></tr>
        </thead>
        <tbody>
          ${months.map((index) => `
            <tr>
              <td>${monthLabel(index)}</td>
              <td>${buildInput(NOTE_TABLE, yearAnchorKey(), `note-${String(index + 1).padStart(2, "0")}`, 0, getNoteValue(index), false, "fluidized-note-input")}</td>
            </tr>
          `).join("")}
        </tbody>
      </table>
    `;
  }

  function renderContractTable() {
    // 도급비용 daily (원본 엑셀 sheet2 rows 1-39).
    // 입력칸: T/D(col 0), 스팀단가(col 2), 운휴시간(col 4), 운휴시간당 고정비(col 5).
    // 계산칸: T/hr(col 1), 스팀 운영비용(col 3), 운휴 운영비용(col 6), 총 운영비용(col 7).
    // DB 의 fluidized_contract_daily 테이블과 양방향 연결.
    //  - 렌더 시: getCellValue 로 저장된 값을 input 에 표시
    //  - 입력 시: bindInputs 의 queueSaveInput -> saveCell 가 자동 POST
    const maxDays = daysInMonth();
    const cell = (rowKey, colIndex, readonly, inactive) => {
      const stored = getCellValue(CONTRACT_TABLE, monthKey(), rowKey, colIndex);
      const ro = readonly || inactive;
      return `<td>${buildInput(CONTRACT_TABLE, monthKey(), rowKey, colIndex, stored, ro)}</td>`;
    };

    const dayRows = Array.from({ length: 31 }, (_, index) => {
      const dayLabel = `${index + 1}일`;
      const rowKey = String(index + 1).padStart(2, "0");
      const inactive = index + 1 > maxDays;
      return `<tr${inactive ? ' class="invalid-day-row"' : ""}>
        <td>${dayLabel}</td>
        ${cell(rowKey, 0, false, inactive)}
        ${cell(rowKey, 1, true,  inactive)}
        ${cell(rowKey, 2, false, inactive)}
        ${cell(rowKey, 3, true,  inactive)}
        ${cell(rowKey, 4, false, inactive)}
        ${cell(rowKey, 5, false, inactive)}
        ${cell(rowKey, 6, true,  inactive)}
        ${cell(rowKey, 7, true,  inactive)}
      </tr>`;
    }).join("");

    const summaryCells = (rowKey) => Array.from({ length: 8 }, (_, c) => cell(rowKey, c, true, false)).join("");

    document.getElementById("contractTableTitle").textContent = `1. ${yearValue()}년 ${Number(monthValue())}월 소각로 도급비용`;
    document.getElementById("contractTableWrap").innerHTML = `
      <table class="data-table fluidized-contract-table">
        <colgroup>
          <col class="contract-col-day">
          <col class="contract-col-num">
          <col class="contract-col-num">
          <col class="contract-col-num">
          <col class="contract-col-num">
          <col class="contract-col-num">
          <col class="contract-col-num">
          <col class="contract-col-num">
          <col class="contract-col-total">
        </colgroup>
        <thead>
          <tr>
            <th rowspan="4">일별</th>
            <th colspan="7">유동상 소각로</th>
            <th rowspan="4">총 운영비용(원)</th>
          </tr>
          <tr>
            <th colspan="7">스팀 구매량 / 운휴시간</th>
          </tr>
          <tr>
            <th colspan="2">스팀 생산량</th>
            <th rowspan="2">스팀단가<br>(원/톤)</th>
            <th rowspan="2">운영비용(원)</th>
            <th rowspan="2">운휴시간(Hr)</th>
            <th rowspan="2">운휴시간당<br>고정비(원/시간)</th>
            <th rowspan="2">운영비용(원)</th>
          </tr>
          <tr>
            <th>(T/D)</th>
            <th>(T/hr)</th>
          </tr>
        </thead>
        <tbody>
          ${dayRows}
          <tr class="summary-row">
            <td>합 계</td>
            ${summaryCells("sum")}
          </tr>
          <tr class="summary-row">
            <td>평 균</td>
            ${summaryCells("avg")}
          </tr>
        </tbody>
      </table>
    `;
  }

  function renderCostTable() {
    // 도급비용 지급현황 (원본 엑셀 sheet2 rows 41-43).
    // 입력칸: 인건비(col 0), 운영비(col 1), 설비개선비용(col 2).
    // 계산칸: 총 지급비용(col 3).
    // DB 의 fluidized_contract_cost 테이블과 양방향 연결.
    const cell = (colIndex, readonly) => {
      const stored = getCellValue(COST_TABLE, monthKey(), "cost", colIndex);
      return `<td>${buildInput(COST_TABLE, monthKey(), "cost", colIndex, stored, readonly)}</td>`;
    };

    document.getElementById("costTableWrap").innerHTML = `
      <div style="text-align:right; margin-bottom:8px;">[단위 : 원]</div>
      <table class="data-table fluidized-cost-table">
        <thead>
          <tr>
            <th>설비명</th>
            <th>인건비</th>
            <th>운영비</th>
            <th>설비개선비용</th>
            <th>총 지급비용</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td>유동상소각로</td>
            ${cell(0, false)}
            ${cell(1, false)}
            ${cell(2, false)}
            ${cell(3, true)}
          </tr>
        </tbody>
      </table>
    `;
  }

  function renderMetricCards() {
    const monthIndex = Number(monthValue()) - 1;
    const prevIndex = previousMonthIndex();
    const totalPlan = PLAN_ROWS.reduce((sum, row) => sum + getPlanValueForYear(row.key, monthIndex, 0, yearValue()), 0);
    const totalActual = PLAN_ROWS.reduce((sum, row) => sum + getPlanValueForYear(row.key, monthIndex, 1, yearValue()), 0);
    const totalDelta = totalActual - totalPlan;
    const accumulatedPlan = PLAN_ROWS.reduce((sum, row) => sum + Array.from({ length: monthIndex + 1 }, (_, i) => getPlanValueForYear(row.key, i, 0, yearValue())).reduce((a, b) => a + b, 0), 0);
    const accumulatedActual = PLAN_ROWS.reduce((sum, row) => sum + Array.from({ length: monthIndex + 1 }, (_, i) => getPlanValueForYear(row.key, i, 1, yearValue())).reduce((a, b) => a + b, 0), 0);
    const accumulatedDelta = accumulatedActual - accumulatedPlan;
    const previousActual = PLAN_ROWS.reduce((sum, row) => sum + getPlanValueForYear(row.key, prevIndex, 1, yearValue()), 0);
    const compareDelta = totalActual - previousActual;
    const compareRate = previousActual !== 0 ? compareDelta / previousActual : 0;

    const contractRows = Array.from({ length: daysInMonth() }, (_, index) => {
      const td = getDailyValue(index, "td", 0);
      const unitPrice = getDailyValue(index, "unit", 2);
      const stopHours = getDailyValue(index, "stop", 4);
      const fixedCost = getDailyValue(index, "fixed", 5);
      return {
        operationCost: td * unitPrice,
        stopCost: stopHours * fixedCost,
      };
    });
    const contractOperation = contractRows.reduce((sum, row) => sum + row.operationCost, 0);
    const contractStop = contractRows.reduce((sum, row) => sum + row.stopCost, 0);
    const contractTotal = contractOperation + contractStop;

    const monthlyActualEl = document.getElementById("metricMonthlyActual");
    const monthlyDeltaEl = document.getElementById("metricMonthlyDelta");
    const accumulatedActualEl = document.getElementById("metricAccumulatedActual");
    const accumulatedDeltaEl = document.getElementById("metricAccumulatedDelta");
    const previousActualEl = document.getElementById("metricPreviousActual");
    const compareRateEl = document.getElementById("metricCompareRate");
    const contractTotalEl = document.getElementById("metricContractTotal");
    const contractDetailEl = document.getElementById("metricContractDetail");

    if (monthlyActualEl) monthlyActualEl.textContent = `${formatNumber(totalActual)} \uBC31\uB9CC\uC6D0`;
    if (monthlyDeltaEl) monthlyDeltaEl.textContent = `\uACC4\uD68D \uB300\uBE44 ${formatSigned(totalDelta)} \uBC31\uB9CC\uC6D0`;
    if (accumulatedActualEl) accumulatedActualEl.textContent = `${formatNumber(accumulatedActual)} \uBC31\uB9CC\uC6D0`;
    if (accumulatedDeltaEl) accumulatedDeltaEl.textContent = `\uB204\uACC4 \uC99D\uAC10 ${formatSigned(accumulatedDelta)} \uBC31\uB9CC\uC6D0`;
    if (previousActualEl) previousActualEl.textContent = `${formatNumber(previousActual)} \uBC31\uB9CC\uC6D0`;
    if (compareRateEl) compareRateEl.textContent = `\uC804\uC6D4 \uB300\uBE44 ${formatSigned(compareDelta)} \uBC31\uB9CC\uC6D0 · ${formatRate(compareRate)}`;
    if (contractTotalEl) contractTotalEl.textContent = `${formatInt(contractTotal)} \uC6D0`;
    if (contractDetailEl) contractDetailEl.textContent = `\uC6B4\uC601\uBE44 ${formatInt(contractOperation)} \uC6D0 + \uC6B4\uD734\uBE44 ${formatInt(contractStop)} \uC6D0`;
  }

  function bindInputs() {
    document.querySelectorAll(".table-edit-input").forEach((input) => {
      if (input.dataset.bound === "true") return;
      input.dataset.bound = "true";
      const numericEditable = isNumericEditableCell(
        input.dataset.tableName,
        input.dataset.rowKey,
        Number(input.dataset.colIndex),
        input.readOnly
      );

      if (numericEditable) {
        input.addEventListener("focus", () => {
          input.value = input.dataset.rawValue ?? normalizeEditableValue(input.value);
        });
      }

      input.addEventListener("input", () => {
        queueSaveInput(input);
      });

      input.addEventListener("change", async () => {
        if (numericEditable) {
          input.dataset.rawValue = normalizeEditableValue(input.value);
          input.value = formatEditableValue(input.value);
        }
        dirtyInputs.add(input);
        await flushDirtyInputs();
        renderAll();
      });

      if (numericEditable) {
        input.addEventListener("blur", () => {
          input.dataset.rawValue = normalizeEditableValue(input.value);
          input.value = formatEditableValue(input.value);
        });
      }
    });
  }

  async function ensureLoaded() {
    const requiredDetailYears = `${Number(yearValue()) - 1},${yearValue()}`;
    if (loaded && loadedDetailYears === requiredDetailYears) return;
    if (!loaded) {
      const targetYear = Number(yearValue());
      const rows = await fetchPaged(CELL_TABLE, {
        fromMonth: monthKey(String(targetYear - 1), "01"),
        toMonth: monthKey(String(targetYear), "12"),
      });
      cellRows.push(...rows);
      loaded = true;
    }
    await fetchDetailActuals(Number(yearValue()));
    loadedDetailYears = requiredDetailYears;
  }

  async function fetchDetailActuals(targetYear) {
    try {
      const response = await fetch(`/tables/fluidized-summary/detail-actuals?year=${encodeURIComponent(targetYear)}`, {
        cache: "no-store",
      });
      if (!response.ok) return;
      const payload = await response.json();
      const months = payload.months || {};
      Object.entries(months).forEach(([targetMonth, cells]) => {
        detailCellsByMonth.set(targetMonth, cells || {});
      });
    } catch (error) {
      // Keep the page usable even if actuals are unavailable.
    }
  }

  async function fetchRenderedDetail(targetMonth) {
    if (detailCellsByMonth.has(targetMonth)) return;
    try {
      const response = await fetch(`/tables/fluidized-detail/rendered?month=${encodeURIComponent(targetMonth)}&view=all`, {
        cache: "no-store",
      });
      if (!response.ok) {
        detailCellsByMonth.set(targetMonth, {});
        return;
      }
      const payload = await response.json();
      detailCellsByMonth.set(targetMonth, {
        ...(payload.main?.cells || {}),
        ...(payload.summary?.cells || {}),
      });
    } catch (error) {
      detailCellsByMonth.set(targetMonth, {});
    }
  }

  async function ensureDetailYear(year) {
    const months = Array.from({ length: 12 }, (_, index) => monthKey(String(year), String(index + 1).padStart(2, "0")));
    await Promise.all(months.map((targetMonth) => fetchRenderedDetail(targetMonth)));
  }

  function resetLoadedState() {
    loaded = false;
    loadedDetailYears = "";
    cellRows.length = 0;
    detailCellsByMonth.clear();
  }

  function renderAll() {
    renderMetricCards();
    renderPlanTable();
    renderSummaryTable();
    renderCompareTable();
    renderNoteTable();
    renderContractTable();
    renderCostTable();
    document.getElementById("planTableTitle").textContent = `1. ${yearValue()}\uB144 \uC720\uB3D9\uC0C1 \uC18C\uAC01\uB85C \uC704\uD0C1 \uC6B4\uC601\uBE44\uC6A9 \uC0AC\uC5C5\uACC4\uD68D\uB300\uBE44 \uC2E4\uC801 (\uB2E8\uC704: \uBC31\uB9CC\uC6D0)`;
    document.getElementById("compareTableTitle").textContent = `2. ${yearValue()}\uB144 \uC720\uB3D9\uC0C1 \uC18C\uAC01\uB85C \uC704\uD0C1 \uC6B4\uC601\uBE44\uC6A9 \uC804\uB144\uB3C4 \uC2E4\uC801 \uBC0F \uBE44\uAD50 (\uB2E8\uC704: \uCC9C\uC6D0)`;
    document.getElementById("contractTableTitle").textContent = `1. ${yearValue()}\uB144 ${Number(monthValue())}\uC6D4 \uC18C\uAC01\uB85C \uB3C4\uAE09 \uBE44\uC6A9`;
    bindInputs();
  }

  function showRequestedPanel(target) {
    const normalized = ["plan", "compare", "contract"].includes(target) ? target : "plan";
    activeTabKey = normalized;
    document.querySelectorAll("[data-period-panel]").forEach((panel) => {
      const active = panel.dataset.periodPanel === normalized;
      panel.classList.toggle("active", active);
      panel.style.display = active ? "" : "none";
    });
    document.querySelectorAll(".period-tab-button[data-period-target]").forEach((button) => {
      button.classList.toggle("active", button.dataset.periodTarget === normalized);
    });
  }

  document.querySelectorAll(".period-tab-button[data-period-target]").forEach((button) => {
    button.addEventListener("click", () => {
      showRequestedPanel(button.dataset.periodTarget);
    });
  });

  window.UnitPageSaveCoordinator?.registerFlush(flushDirtyInputs);

  async function loadAndRender() {
    await ensureLoaded();
    renderAll();
    showRequestedPanel(requestedTab || "plan");
  }

  async function downloadSummaryExcel() {
    // 활성 탭에 따라 endpoint 분기:
    //   plan     -> /tables/fluidized-summary/export-excel (전체 워크북)
    //   compare  -> /tables/fluidized-summary/export-compare-excel?year (실적비교 단일시트)
    //   contract -> /tables/fluidized-summary/export-contract-excel?year&month (도급비용 단일시트)
    let endpoint, filename;
    if (activeTabKey === "compare") {
      endpoint = `/tables/fluidized-summary/export-compare-excel?year=${encodeURIComponent(yearValue())}`;
      filename = `유동상_실적비교_${yearValue()}.xlsx`;
    } else if (activeTabKey === "contract") {
      endpoint = `/tables/fluidized-summary/export-contract-excel?year=${encodeURIComponent(yearValue())}&month=${encodeURIComponent(monthValue())}`;
      filename = `유동상_도급비용_${yearValue()}_${monthValue()}.xlsx`;
    } else {
      endpoint = `/tables/fluidized-summary/export-excel?year=${encodeURIComponent(yearValue())}&month=${encodeURIComponent(monthValue())}`;
      filename = `유동상_운영내역_${yearValue()}_${monthValue()}.xlsx`;
    }
    const response = await fetch(endpoint);
    if (!response.ok) { throw new Error(await window.ExcelUploadErrors?.message(response) || "엑셀 다운로드에 실패했습니다."); }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  async function importSummaryExcel(file) {
    if (!file || excelUploadInFlight) return;
    excelUploadInFlight = true;
    const originalLabel = summaryExcelUploadButton?.textContent;
    if (summaryExcelUploadButton) {
      summaryExcelUploadButton.disabled = true;
      summaryExcelUploadButton.textContent = "업로드 중";
    }

    try {
      await flushDirtyInputs();
      const formData = new FormData();
      formData.append("file", file);
      formData.append("year", yearValue());
      formData.append("month", monthValue());

      let endpoint = "/tables/fluidized-summary/import-excel";
      if (activeTabKey === "compare") endpoint = "/tables/fluidized-summary/import-compare-excel";
      else if (activeTabKey === "contract") endpoint = "/tables/fluidized-summary/import-contract-excel";
      const response = await fetch(endpoint, {
        method: "POST",
        body: formData,
      });
      if (!response.ok) { throw new Error(await window.ExcelUploadErrors?.message(response) || "엑셀 업로드에 실패했습니다."); }

      resetLoadedState();
      await loadAndRender();
      alert("엑셀 업로드를 반영했습니다.");
    } catch (error) {
      alert(error instanceof Error ? error.message : "엑셀 업로드 중 오류가 발생했습니다.");
    } finally {
      excelUploadInFlight = false;
      if (summaryExcelUploadButton) {
        summaryExcelUploadButton.disabled = false;
        summaryExcelUploadButton.textContent = originalLabel || "업로드";
      }
      if (summaryExcelFileInput) {
        summaryExcelFileInput.value = "";
      }
    }
  }

  yearSelect.addEventListener("change", async () => {
    await flushDirtyInputs();
  });

  monthSelect.addEventListener("change", async () => {
    await flushDirtyInputs();
  });

  submitButton?.addEventListener("click", async () => {
    await flushDirtyInputs();
    await ensureLoaded();
    renderAll();
  });

  summaryExcelDownloadButton?.addEventListener("click", async (event) => {
    event.preventDefault();
    event.stopImmediatePropagation();
    await flushDirtyInputs();
    try {
      await downloadSummaryExcel();
    } catch (error) {
      alert(error instanceof Error ? error.message : "엑셀 다운로드 중 오류가 발생했습니다.");
    }
  }, true);

  summaryExcelUploadButton?.addEventListener("click", async (event) => {
    event.preventDefault();
    event.stopImmediatePropagation();
    await flushDirtyInputs();
    summaryExcelFileInput?.click();
  }, true);

  summaryExcelFileInput?.addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    await importSummaryExcel(file);
  });

  loadAndRender();
});
