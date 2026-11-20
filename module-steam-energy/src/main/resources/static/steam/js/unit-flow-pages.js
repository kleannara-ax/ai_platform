/* 유동상/소각로/외부/복합 일일 사용량 — 규격 표 steam_fuel_daily_usage (/steam-energy-api/fuel-daily-usages) 를 읽고 쓴다.
 *  - 2026-11 규격 전환: 옛 셀 저장소(flow_m_fluid_incinerator / flow_m_combined / flow_m_boiler) 대신 일자별 행 하나.
 *    화면 표·열 구조는 그대로 두고 (tableName, colIndex) 를 컬럼 이름으로 바꿔 저장한다 (FIELD_MAP).
 *  - 저장은 칸 단위가 아니라 그 날의 행 전체 업서트(POST). 비용·스팀구매비·총합 표는 화면 계산만 한다.
 *  - '단가 적용'은 서버 POST /prices/apply-daily (유동상·폐합성). 복합·외부보일러 단가는 직접 입력.
 */
document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "unit-flow-daily.html") return;

  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  if (!yearSelect || !monthSelect) return;

  const API = window.SteamEnergyApi;
  if (!API) { console.error("steam-energy-api.js 가 로드되지 않았습니다."); return; }
  const DAILY_ROW_COUNT = 31;
  // 일자('YYYY-MM-DD') → steam_fuel_daily_usage 행 (GET /steam-energy-api/fuel-daily-usages 응답)
  const rowsByDate = new Map();
  let loaded = false;
  let loadedMonth = "";

  // 화면 표(옛 논리 표 이름 유지)·열 → 규격 표 컬럼. 비용·스팀구매비·총합 표는 계산만 하고 저장하지 않는다.
  const FIELD_MAP = {
    flow_m_fluid_incinerator: { 0: "fluidSteamTon", 1: "fluidUnitPrice", 3: "incineratorSteamTon", 4: "incineratorUnitPrice" },
    flow_m_combined: { 0: "combinedSteamTon", 1: "combinedUnitPrice", 3: "combinedDeduction" },
    flow_m_boiler: { 0: "extBoilerSteamTon", 1: "extBoilerUnitPrice", 3: "extBoilerDeduction" },
    flow_m_summary: {},
  };
  const SAVED_FIELDS = Object.values(FIELD_MAP).flatMap((m) => Object.values(m));
  function fieldOf(tableName, colIndex) { return (FIELD_MAP[tableName] || {})[Number(colIndex)] || null; }
  const dirtyInputs = new Set();
  const pendingSaveTimers = new WeakMap();

  const MONTHLY_READONLY = {
    // 단가 칸(유동상 col1 / 폐합성 col4 / 복합 col1 / 외부 col1)은 입력 가능해야 한다.
    // 비용(계산) 칸만 읽기전용: fluid=비용 2·5, combined/boiler=비용 2·스팀구매비 4.
    flow_m_fluid_incinerator: [2, 5],
    flow_m_combined: [2, 4],
    flow_m_boiler: [2, 4],
    flow_m_summary: [0, 1, 2, 3, 4],
  };

  function monthKey(year = yearSelect.value, month = monthSelect.value) {
    return `${year}-${month}`;
  }

  function isoDateOfDay(day) {
    return API.isoDate(Number(yearSelect.value), Number(monthSelect.value), Number(day));
  }

  function daysInMonth() {
    return new Date(Number(yearSelect.value), Number(monthSelect.value), 0).getDate();
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

  function formatOne(value) {
    if (!Number.isFinite(value)) return "";
    return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 1, minimumFractionDigits: 1 }).format(value);
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

  function isReadonly(tableName, colIndex) {
    return (MONTHLY_READONLY[tableName] || []).includes(colIndex);
  }

  async function fetchMonthRows() {
    const year = Number(yearSelect.value);
    const month = Number(monthSelect.value);
    return API.get(`/fuel-daily-usages?year=${year}&month=${month}`);
  }

  // 화면 값: 규격 표 행의 컬럼 값 (없으면 빈 문자열). month 인자는 옛 호출 모양을 맞추기 위한 것으로 쓰지 않는다.
  function getCellValue(tableName, _month, rowKey, colIndex) {
    const field = fieldOf(tableName, colIndex);
    if (!field) return "";
    const row = rowsByDate.get(isoDateOfDay(rowKey));
    const value = row ? row[field] : null;
    return value === null || value === undefined ? "" : String(value);
  }

  // 그 날의 저장 대상 입력칸 전부(세 표)를 모아 행 하나로 보낸다 — 행 전체 업서트.
  //  changedInput: 지금 저장을 일으킨 칸 — 포커스 여부와 상관없이 현재 입력값을 쓴다.
  function buildRowRequest(rowKey, changedInput) {
    const usageDate = isoDateOfDay(rowKey);
    const base = rowsByDate.get(usageDate) || {};
    const body = { usageDate };
    SAVED_FIELDS.forEach((field) => { body[field] = base[field] === undefined ? null : base[field]; });
    Object.keys(FIELD_MAP).forEach((tableName) => {
      Object.entries(FIELD_MAP[tableName]).forEach(([col, field]) => {
        const input = document.querySelector(`input[data-table-name="${tableName}"][data-row-key="${rowKey}"][data-col-index="${col}"]`);
        if (input) body[field] = API.num(input === changedInput ? normalizeEditableValue(input.value) : editableRawValue(input));
      });
    });
    return body;
  }

  async function saveCell(input) {
    if (!input || input.readOnly || input.classList.contains("calc-input")) return;
    const tableName = input.dataset.tableName;
    const rowKey = input.dataset.rowKey;
    if (!fieldOf(tableName, input.dataset.colIndex) || !rowKey || rowKey === "total" || rowKey === "avg") return;
    if (Number(rowKey) > daysInMonth()) return;

    try {
      const saved = await API.post("/fuel-daily-usages", buildRowRequest(rowKey, input));
      rowsByDate.set(saved.usageDate, saved);
      const field = fieldOf(tableName, input.dataset.colIndex);
      const value = saved[field];
      input.dataset.rawValue = value === null || value === undefined ? "" : String(value);
    } catch (error) {
      console.error("일일 사용량 저장 실패", error);
      window.alert(`저장 실패: ${error.message || error}`);
    }
  }

  function buildInput(tableName, rowKey, colIndex, value, forceReadonly = false) {
    const ro = forceReadonly || isReadonly(tableName, colIndex);
    const displayValue = ro ? value : formatEditableValue(value);
    return `<input class="table-edit-input unit-align-number${ro ? " calc-input" : ""}" data-table-name="${tableName}" data-row-mode="daily" data-row-key="${rowKey}" data-col-index="${colIndex}" data-raw-value="${escapeAttr(normalizeEditableValue(value))}" value="${escapeAttr(displayValue)}"${ro ? ' readonly tabindex="-1"' : ""}>`;
  }

  function buildReadonlyInput(tableName, rowKey, colIndex, value) {
    return `<input class="table-edit-input unit-align-number calc-input" data-table-name="${tableName}" data-row-mode="daily" data-row-key="${rowKey}" data-col-index="${colIndex}" value="${escapeAttr(value)}" readonly tabindex="-1">`;
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

  function renderMonthlyTable(table) {
    const tableName = table.dataset.cellTable;
    const tbody = table.querySelector("tbody");
    const totalCols = table.querySelectorAll("thead th").length;
    if (!tbody || !tableName || totalCols < 2) return;

    const rows = [];
    const maxDays = daysInMonth();
    for (let day = 1; day <= DAILY_ROW_COUNT; day += 1) {
      const rowKey = String(day).padStart(2, "0");
      const inactiveDay = day > maxDays;
      const cells = [`<td>${rowKey}</td>`];
      for (let col = 0; col < totalCols - 1; col += 1) {
        cells.push(`<td>${buildInput(tableName, rowKey, col, getCellValue(tableName, monthKey(), rowKey, col), inactiveDay)}</td>`);
      }
      rows.push(`<tr data-row-key="${rowKey}"${inactiveDay ? ' class="invalid-day-row"' : ""}>${cells.join("")}</tr>`);
    }
    const totalCells = [`<td>합계</td>`];
    for (let col = 0; col < totalCols - 1; col += 1) {
      totalCells.push(`<td>${buildReadonlyInput(tableName, "total", col, "")}</td>`);
    }
    rows.push(`<tr data-row-key="total" class="summary-row">${totalCells.join("")}</tr>`);
    const avgCells = [`<td>평균</td>`];
    for (let col = 0; col < totalCols - 1; col += 1) {
      avgCells.push(`<td>${buildReadonlyInput(tableName, "avg", col, "")}</td>`);
    }
    rows.push(`<tr data-row-key="avg" class="summary-row">${avgCells.join("")}</tr>`);
    tbody.innerHTML = rows.join("");
  }

  function getInput(row, colIndex) {
    return row.querySelector(`input[data-col-index="${colIndex}"]`);
  }

  function setCalc(row, colIndex, value, digits = 0) {
    const input = getInput(row, colIndex);
    if (!input) return;
    input.value = value === "" || value === null || value === undefined ? "" : (digits > 0 ? formatOne(value) : formatInt(value));
    input.classList.add("unit-align-number");
    alignDataCell(input.closest("td"));
  }

  function recalcSourceRow(row) {
    const tableName = row.querySelector("input")?.dataset.tableName;
    if (!tableName) return;

    if (tableName === "flow_m_fluid_incinerator") {
      const fluidSteam = parseNumber(getInput(row, 0)?.value);
      const fluidPrice = parseNumber(getInput(row, 1)?.value);
      const incineratorSteam = parseNumber(getInput(row, 3)?.value);
      const incineratorPrice = parseNumber(getInput(row, 4)?.value);
      setCalc(row, 2, fluidSteam > 0 || fluidPrice > 0 ? (fluidSteam * fluidPrice) / 1000000 : "", 1);
      setCalc(row, 5, incineratorSteam > 0 || incineratorPrice > 0 ? (incineratorSteam * incineratorPrice) / 1000000 : "", 1);
      return;
    }

    if (tableName === "flow_m_combined" || tableName === "flow_m_boiler") {
      const steam = parseNumber(getInput(row, 0)?.value);
      const price = parseNumber(getInput(row, 1)?.value);
      const deduction = parseNumber(getInput(row, 3)?.value);
      const cost = steam > 0 || price > 0 ? (steam * price) / 1000000 : 0;
      setCalc(row, 2, cost > 0 || deduction > 0 ? cost : "", 1);
      setCalc(row, 4, cost > 0 || deduction > 0 ? cost - deduction : "", 1);
    }
  }

  function recalcSummaryRow(rowKey) {
    const summaryRow = document.querySelector(`input[data-table-name="flow_m_summary"][data-row-key="${rowKey}"]`)?.closest("tr");
    if (!summaryRow) return;

    const fluidCost = parseNumber(document.querySelector(`input[data-table-name="flow_m_fluid_incinerator"][data-row-key="${rowKey}"][data-col-index="2"]`)?.value);
    const incineratorCost = parseNumber(document.querySelector(`input[data-table-name="flow_m_fluid_incinerator"][data-row-key="${rowKey}"][data-col-index="5"]`)?.value);
    const combinedPurchase = parseNumber(document.querySelector(`input[data-table-name="flow_m_combined"][data-row-key="${rowKey}"][data-col-index="4"]`)?.value);
    const boilerPurchase = parseNumber(document.querySelector(`input[data-table-name="flow_m_boiler"][data-row-key="${rowKey}"][data-col-index="4"]`)?.value);

    setCalc(summaryRow, 0, fluidCost > 0 ? fluidCost : "", 1);
    setCalc(summaryRow, 1, incineratorCost > 0 ? incineratorCost : "", 1);
    setCalc(summaryRow, 2, combinedPurchase > 0 ? combinedPurchase : "", 1);
    setCalc(summaryRow, 3, boilerPurchase > 0 ? boilerPurchase : "", 1);
    const total = fluidCost + incineratorCost + combinedPurchase + boilerPurchase;
    setCalc(summaryRow, 4, total > 0 ? total : "", 1);
  }

  function recalcAll() {
    const maxDays = daysInMonth();
    ["flow_m_fluid_incinerator", "flow_m_combined", "flow_m_boiler"].forEach((tableName) => {
      document.querySelectorAll(`input[data-table-name="${tableName}"]`).forEach((input) => {
        const row = input.closest("tr");
        if (!row || Number(row.dataset.rowKey) > maxDays) return;
        if (row) recalcSourceRow(row);
      });
    });
    for (let day = 1; day <= maxDays; day += 1) {
      recalcSummaryRow(String(day).padStart(2, "0"));
    }
    recalcMonthlySummaries();
  }

  function recalcMonthlySummaries() {
    const maxDays = daysInMonth();
    document.querySelectorAll('[data-cell-table][data-row-mode="daily"]').forEach((table) => {
      const tableName = table.dataset.cellTable;
      const totalRow = table.querySelector('tr[data-row-key="total"]');
      const avgRow = table.querySelector('tr[data-row-key="avg"]');
      if (!tableName || !totalRow || !avgRow) return;
      const colCount = table.querySelectorAll("thead th").length - 1;
      for (let col = 0; col < colCount; col += 1) {
        let total = 0;
        let hasValue = false;
        for (let day = 1; day <= maxDays; day += 1) {
          const rowKey = String(day).padStart(2, "0");
          const value = document.querySelector(`input[data-table-name="${tableName}"][data-row-key="${rowKey}"][data-col-index="${col}"]`)?.value ?? "";
          if (String(value).trim() !== "") hasValue = true;
          total += parseNumber(value);
        }
        const digits = [2, 4].includes(col) && ["flow_m_combined", "flow_m_boiler", "flow_m_summary"].includes(tableName) ? 1
          : [2, 5].includes(col) && tableName === "flow_m_fluid_incinerator" ? 1
          : 0;
        setCalc(totalRow, col, hasValue ? total : "", digits);
        setCalc(avgRow, col, hasValue ? total / maxDays : "", digits);
      }
    });
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

  function bindInputs() {
    document.querySelectorAll(".table-edit-input").forEach((input) => {
      if (input.dataset.bound === "true") return;
      input.dataset.bound = "true";

      input.addEventListener("input", () => {
        const row = input.closest("tr");
        if (row) {
          recalcSourceRow(row);
          recalcSummaryRow(input.dataset.rowKey);
        }
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

  async function ensureLoaded(force = false) {
    const currentMonth = monthKey();
    if (!force && loaded && loadedMonth === currentMonth) return;
    let rows = [];
    try {
      rows = await fetchMonthRows();
    } catch (error) {
      console.error("일일 사용량 조회 실패", error);
      window.alert(`조회 실패: ${error.message || error}`);
    }
    rowsByDate.clear();
    rows.forEach((row) => rowsByDate.set(row.usageDate, row));
    loaded = true;
    loadedMonth = currentMonth;
  }

  // '단가 적용': 단가 입력에 등록된 이번 달 유동상(fluid_steam)·폐합성(waste2_steam) 단가를
  // 서버가 그 달 전체 일자의 단가 칸에 넣는다 (POST /prices/apply-daily). 복합·외부보일러 단가는 여기서 직접 입력한다.
  const APPLY_PRICE_ITEMS = [
    { code: "fluid_steam", label: "유동상 단가" },
    { code: "waste2_steam", label: "폐합성소각로 단가" },
  ];
  async function applyRegisteredPrices() {
    const year = Number(yearSelect.value);
    const month = Number(monthSelect.value);
    if (!year || !month) { window.alert("조회 연/월을 먼저 선택하세요."); return; }
    if (!window.confirm(`${year}년 ${month}월 등록 단가(유동상·폐합성소각로)를 이 달 전체 일자의 단가 칸에 적용할까요?\n(해당 월 기존 단가는 덮어씁니다)`)) return;
    const button = document.getElementById("applyPriceBtn");
    const prevText = button?.textContent;
    if (button) { button.disabled = true; button.textContent = "적용 중…"; }
    await flushDirtyInputs();
    const applied = [];
    const skipped = [];
    for (const item of APPLY_PRICE_ITEMS) {
      try {
        const result = await API.post("/prices/apply-daily", { yearNo: year, monthNo: month, itemCode: item.code });
        if (result && result.hasDailyTarget) applied.push(`${item.label} ${result.appliedDays}일`);
        else skipped.push(item.label);
      } catch (error) {
        skipped.push(`${item.label}(${error.message || "실패"})`);
      }
    }
    if (button) { button.disabled = false; button.textContent = prevText; }
    await ensureLoaded(true);
    document.querySelectorAll('[data-cell-table][data-row-mode="daily"]').forEach(renderMonthlyTable);
    recalcAll();
    bindInputs();
    applyDataAlignment();
    if (!applied.length) {
      window.alert("이 달에 등록된 단가가 없습니다.\n먼저 '단가 입력' 페이지에서 등록하세요.\n" + skipped.join(", "));
    } else if (skipped.length) {
      window.alert(`적용: ${applied.join(", ")}\n미등록: ${skipped.join(", ")}`);
    }
  }
  document.getElementById("applyPriceBtn")?.addEventListener("click", applyRegisteredPrices);

  async function render() {
    await ensureLoaded();
    document.querySelectorAll('[data-cell-table][data-row-mode="daily"]').forEach(renderMonthlyTable);
    recalcAll();
    bindInputs();
    applyDataAlignment();
  }

  window.UnitPageSaveCoordinator?.registerFlush(flushDirtyInputs);

  // 조회 연도·월을 바꾸면 버튼을 누르지 않아도 바로 조회한다.
  // (입력 중이던 값은 먼저 저장하고 넘어간다 — 바꾸기 전 달의 수정분이 사라지지 않게)
  async function applyPeriod() {
    await flushDirtyInputs();
    await render();
  }
  yearSelect.addEventListener("change", applyPeriod);
  monthSelect.addEventListener("change", applyPeriod);
  submitButton?.addEventListener("click", applyPeriod);

  render();
});
