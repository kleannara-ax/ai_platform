document.addEventListener("DOMContentLoaded", () => {
  const pageKind = document.body.dataset.pageKind;
  if (pageKind !== "unit") return;

  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  const uploadYearSelect = document.getElementById("uploadYear");
  const unitExcelDownloadButton = document.getElementById("unitExcelDownloadButton");
  const unitExcelUploadButton = document.getElementById("unitExcelUploadButton");
  const unitExcelFileInput = document.getElementById("unitExcelFileInput");
  if (!yearSelect || !monthSelect) return;

  const DB_TABLE_UNIT = "unit_usage";
  const DB_TABLE_UNIT_RAW = "unit_usage_raw";
  const unitRows = [];
  const rawRows = [];
  let loaded = false;
  let loadedYear = "";
  const dirtyInputs = new Set();
  const pendingSaveTimers = new WeakMap();
  let uploadInFlight = false;

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

  function findLastMatching(rows, predicate) {
    for (let index = rows.length - 1; index >= 0; index -= 1) {
      if (predicate(rows[index])) return rows[index];
    }
    return null;
  }

  function findPreferredDayRow(rows, month, day, extraPredicate = () => true) {
    const normalizedDay = normalizeDayValue(day);
    const exact = findLastMatching(rows, (row) =>
      row.month === month &&
      String(row.day) === normalizedDay &&
      extraPredicate(row)
    );
    if (exact) return exact;
    return findLastMatching(rows, (row) =>
      row.month === month &&
      normalizeDayValue(row.day) === normalizedDay &&
      extraPredicate(row)
    );
  }

  function asNumber(value) {
    const cleaned = String(value ?? "").replace(/,/g, "").trim();
    if (!cleaned) return null;
    const num = Number(cleaned);
    return Number.isFinite(num) ? num : null;
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

  function format(value, digits = 0) {
    if (value === null || value === undefined || value === "") return "";
    const num = Number(value);
    if (!Number.isFinite(num)) return "";
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: digits,
      maximumFractionDigits: digits,
    }).format(num);
  }

  function editableRawValue(input) {
    return document.activeElement === input ? normalizeEditableValue(input.value) : (input.dataset.rawValue ?? normalizeEditableValue(input.value));
  }

  async function downloadUnitExcel() {
    const year = uploadYearSelect?.value || yearSelect.value;
    const response = await fetch(`/tables/unit-usage/export-excel?year=${encodeURIComponent(year)}`);
    if (!response.ok) { throw new Error(await window.ExcelUploadErrors?.message(response) || "엑셀 업로드에 실패했습니다."); }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${year}_?먮떒???쇱?.xls`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(url);
  }

  const DAILY_ROW_COUNT = 31;

  async function fetchAllPages(tableName, options = {}) {
    const rows = [];
    let page = 1;
    let totalPages = 1;

    while (page <= totalPages) {
      const params = new URLSearchParams({ page: String(page), limit: "500" });
      if (options.fromMonth) params.set("fromMonth", options.fromMonth);
      if (options.toMonth) params.set("toMonth", options.toMonth);
      if (options.month) params.set("month", options.month);
      const response = await fetch(`tables/${tableName}?${params.toString()}`);
      if (!response.ok) break;
      const payload = await response.json();
      const data = payload.data || [];
      const total = payload.total || data.length;
      const limit = payload.limit || 200;
      totalPages = Math.max(1, Math.ceil(total / limit));
      rows.push(...data);
      page += 1;
    }

    return rows;
  }

  function resetLoadedState() {
    unitRows.length = 0;
    rawRows.length = 0;
    loaded = false;
    loadedYear = "";
  }

  function findUnit(month, day, machine) {
    return findPreferredDayRow(unitRows, month, day, (row) => row.machine_no === machine);
  }

  function findRaw(month, day) {
    return findPreferredDayRow(rawRows, month, day);
  }

  async function upsert(tableName, payload, existingId) {
    const url = existingId ? `tables/${tableName}/${existingId}` : `tables/${tableName}`;
    const method = existingId ? "PATCH" : "POST";
    const response = await fetch(url, {
      method,
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(existingId ? payload : { table_name: tableName, ...payload }),
    });
    if (!response.ok) return null;
    return response.json();
  }

  function metricForDay(month, day) {
    const pm2 = findUnit(month, day, "PM-2") || {};
    const pm3 = findUnit(month, day, "PM-3") || {};
    const tm3 = findUnit(month, day, "TM-3") || {};
    const tm4 = findUnit(month, day, "TM-4") || {};
    const tm5 = findUnit(month, day, "TM-5") || {};
    const raw = findRaw(month, day) || {};

    const pm2Main = Number(pm2.main_steam || 0);
    const pm2Coater = Number(pm2.coater_steam || 0);
    // 원단위(톤/톤) = 스팀(톤) / 생산량(톤) = 스팀(톤) * 1000 / 생산량(kg).
    // 원본 엑셀 1번 시트와 단위 일치 (헤더에 톤/톤 명시되어 있음).
    const TON_PER_KG = 1000;
    const pm2Steam = pm2Main + pm2Coater;
    const pm2Prod = Number(pm2.production || 0);
    const pm2Unit = pm2Prod ? (pm2Steam * TON_PER_KG) / pm2Prod : 0;

    const pm3Main = Number(pm3.main_steam || 0);
    const pm3Coater = Number(pm3.coater_steam || 0);
    const disperser = Number(raw.disperser_total || 0);
    const vent = Number(pm3.ventilation_steam || 0);
    const pm3Steam = pm3Main + pm3Coater + disperser + vent;
    const pm3Prod = Number(pm3.production || 0);
    const pm3Unit = pm3Prod ? (pm3Steam * TON_PER_KG) / pm3Prod : 0;

    const tm3Steam = Number(tm3.steam || 0);
    const tm3Prod = Number(tm3.production || 0);
    const tm3Unit = tm3Prod ? (tm3Steam * TON_PER_KG) / tm3Prod : 0;
    const tm4Steam = Number(tm4.steam || 0);
    const tm4Prod = Number(tm4.production || 0);
    const tm4Unit = tm4Prod ? (tm4Steam * TON_PER_KG) / tm4Prod : 0;
    const tm5Steam = Number(tm5.steam || 0);
    const tm5Prod = Number(tm5.production || 0);
    const tm5Unit = tm5Prod ? (tm5Steam * TON_PER_KG) / tm5Prod : 0;

    const paperSteam = pm2Steam + pm3Steam;
    const paperProd = pm2Prod + pm3Prod;
    const paperUnit = paperProd ? (paperSteam * TON_PER_KG) / paperProd : 0;
    const tissueSteam = tm3Steam + tm4Steam + tm5Steam;
    const tissueProd = tm3Prod + tm4Prod + tm5Prod;
    const tissueUnit = tissueProd ? (tissueSteam * TON_PER_KG) / tissueProd : 0;
    const totalSteam = paperSteam + tissueSteam;
    const totalProd = paperProd + tissueProd;
    const totalUnit = totalProd ? (totalSteam * TON_PER_KG) / totalProd : 0;

    return {
      pm2,
      pm3,
      tm3,
      tm4,
      tm5,
      raw,
      pm2Main,
      pm2Coater,
      pm2Steam,
      pm2Prod,
      pm2Unit,
      pm3Main,
      pm3Coater,
      disperser,
      vent,
      pm3Steam,
      pm3Prod,
      pm3Unit,
      tm3Steam,
      tm3Prod,
      tm3Unit,
      tm4Steam,
      tm4Prod,
      tm4Unit,
      tm5Steam,
      tm5Prod,
      tm5Unit,
      paperSteam,
      paperProd,
      paperUnit,
      tissueSteam,
      tissueProd,
      tissueUnit,
      totalSteam,
      totalProd,
      totalUnit,
    };
  }

  function aggregateMonth(year, monthNumber) {
    const month = monthKey(year, String(monthNumber).padStart(2, "0"));
    const totals = {
      pm2Main: 0,
      pm2Coater: 0,
      pm2Steam: 0,
      pm2Prod: 0,
      pm3Main: 0,
      pm3Coater: 0,
      disperser: 0,
      vent: 0,
      pm3Steam: 0,
      pm3Prod: 0,
      paperSteam: 0,
      paperProd: 0,
      tm3Steam: 0,
      tm3Prod: 0,
      tm4Steam: 0,
      tm4Prod: 0,
      tm5Steam: 0,
      tm5Prod: 0,
      tissueSteam: 0,
      tissueProd: 0,
    };

    const maxDays = daysInMonth(Number(year), monthNumber);
    for (let day = 1; day <= maxDays; day += 1) {
      const m = metricForDay(month, day);
      totals.pm2Main += m.pm2Main;
      totals.pm2Coater += m.pm2Coater;
      totals.pm2Steam += m.pm2Steam;
      totals.pm2Prod += m.pm2Prod;
      totals.pm3Main += m.pm3Main;
      totals.pm3Coater += m.pm3Coater;
      totals.disperser += m.disperser;
      totals.vent += m.vent;
      totals.pm3Steam += m.pm3Steam;
      totals.pm3Prod += m.pm3Prod;
      totals.paperSteam += m.paperSteam;
      totals.paperProd += m.paperProd;
      totals.tm3Steam += m.tm3Steam;
      totals.tm3Prod += m.tm3Prod;
      totals.tm4Steam += m.tm4Steam;
      totals.tm4Prod += m.tm4Prod;
      totals.tm5Steam += m.tm5Steam;
      totals.tm5Prod += m.tm5Prod;
      totals.tissueSteam += m.tissueSteam;
      totals.tissueProd += m.tissueProd;
    }

    return {
      label: `${monthNumber}\uC6D4`,
      // 원단위(톤/톤) = 스팀(톤) * 1000 / 생산량(kg). 헤더 단위와 일치시킴.
      pm2Unit: totals.pm2Prod ? (totals.pm2Steam * 1000) / totals.pm2Prod : 0,
      pm3Unit: totals.pm3Prod ? (totals.pm3Steam * 1000) / totals.pm3Prod : 0,
      paperUnit: totals.paperProd ? (totals.paperSteam * 1000) / totals.paperProd : 0,
      tm3Unit: totals.tm3Prod ? (totals.tm3Steam * 1000) / totals.tm3Prod : 0,
      tm4Unit: totals.tm4Prod ? (totals.tm4Steam * 1000) / totals.tm4Prod : 0,
      tm5Unit: totals.tm5Prod ? (totals.tm5Steam * 1000) / totals.tm5Prod : 0,
      tissueUnit: totals.tissueProd ? (totals.tissueSteam * 1000) / totals.tissueProd : 0,
      ...totals,
    };
  }

  function inputCell(value, meta = "", forceReadonly = false) {
    const displayValue = forceReadonly ? (value ?? "") : formatEditableValue(value);
    return `<input class="table-edit-input" value="${displayValue}" data-raw-value="${normalizeEditableValue(value)}" ${meta}${forceReadonly ? ' readonly tabindex="-1"' : ""}>`;
  }

  function cell(value) {
    return `<span>${value ?? ""}</span>`;
  }

  const PRODUCTION_TYPE_OPTIONS = ["SC", "ACB", "KB"];
  let productionTypeDropdown = null;
  let productionTypeActiveInput = null;
  let productionTypeReposition = null;

  function ensureProductionTypeDropdown() {
    if (productionTypeDropdown) return productionTypeDropdown;
    productionTypeDropdown = document.createElement("div");
    productionTypeDropdown.className = "prodtype-dropdown";
    productionTypeDropdown.hidden = true;
    productionTypeDropdown.innerHTML = PRODUCTION_TYPE_OPTIONS
      .map((value) => `<button type="button" class="prodtype-option" data-value="${value}">${value}</button>`)
      .join("");
    document.body.appendChild(productionTypeDropdown);
    productionTypeDropdown.addEventListener("mousedown", (event) => {
      const button = event.target.closest(".prodtype-option");
      if (!button || !productionTypeActiveInput) return;
      event.preventDefault();
      const input = productionTypeActiveInput;
      input.value = button.dataset.value;
      input.dataset.rawValue = button.dataset.value;
      input.dispatchEvent(new Event("input", { bubbles: true }));
      input.dispatchEvent(new Event("change", { bubbles: true }));
      closeProductionTypeDropdown(input);
      input.blur();
    });
    return productionTypeDropdown;
  }

  function positionProductionTypeDropdown(input) {
    if (!productionTypeDropdown || !input) return;
    const rect = input.getBoundingClientRect();
    productionTypeDropdown.style.left = `${rect.left}px`;
    productionTypeDropdown.style.top = `${rect.bottom + 2}px`;
    productionTypeDropdown.style.minWidth = `${rect.width}px`;
  }

  function openProductionTypeDropdown(input) {
    ensureProductionTypeDropdown();
    productionTypeActiveInput = input;
    productionTypeDropdown.hidden = false;
    positionProductionTypeDropdown(input);
    if (productionTypeReposition) return;
    productionTypeReposition = () => positionProductionTypeDropdown(productionTypeActiveInput);
    window.addEventListener("scroll", productionTypeReposition, true);
    window.addEventListener("resize", productionTypeReposition);
  }

  function closeProductionTypeDropdown(input) {
    if (!productionTypeDropdown) return;
    if (input && productionTypeActiveInput && input !== productionTypeActiveInput) return;
    productionTypeDropdown.hidden = true;
    productionTypeActiveInput = null;
  }

  function getNumericInput(row, field, index = 0) {
    const matches = row.querySelectorAll(`[data-field="${field}"]`);
    const input = matches[index];
    return asNumber(input?.value) ?? 0;
  }

  function setCellText(row, cellIndex, value) {
    const cellElement = row.children[cellIndex];
    if (cellElement) {
      cellElement.textContent = value;
      alignUnitCell(cellElement);
    }
  }

  function isNumericText(value) {
    const text = String(value ?? "").replace(/,/g, "").trim();
    return /^-?\d+(\.\d+)?$/.test(text);
  }

  function alignUnitCell(cellElement) {
    if (!(cellElement instanceof HTMLElement)) return;
    cellElement.classList.remove("unit-align-number", "unit-align-text");
    if (cellElement.cellIndex === 0) {
      cellElement.classList.add("unit-align-text");
      return;
    }
    const input = cellElement.querySelector("input");
    if (input instanceof HTMLInputElement) {
      input.classList.remove("unit-align-number", "unit-align-text");
      const className = input.dataset.kind === "unit-text" ? "unit-align-text" : "unit-align-number";
      input.classList.add(className);
      cellElement.classList.add(className);
      return;
    }
    cellElement.classList.add(isNumericText(cellElement.textContent) ? "unit-align-number" : "unit-align-text");
  }

  function applyUnitTableAlignment() {
    document.querySelectorAll(".unit-table th").forEach((cellElement) => {
      cellElement.classList.remove("unit-align-number");
      cellElement.classList.add("unit-align-text");
    });
    document.querySelectorAll(".unit-table td").forEach(alignUnitCell);
  }

  function updatePm2Row(row) {
    const main = getNumericInput(row, "main_steam");
    const coater = getNumericInput(row, "coater_steam");
    const production = getNumericInput(row, "production");
    const steam = main + coater;
    // 원단위(톤/톤) = 스팀(톤) * 1000 / 생산량(kg).
    const unit = production ? (steam * 1000) / production : 0;
    setCellText(row, 3, format(steam));
    setCellText(row, 5, format(unit, 2));
  }

  function updatePm3Row(row) {
    const main = getNumericInput(row, "main_steam");
    const coater = getNumericInput(row, "coater_steam");
    const disperser = getNumericInput(row, "disperser_total");
    const vent = getNumericInput(row, "ventilation_steam");
    const production = getNumericInput(row, "production");
    const steam = main + coater + disperser + vent;
    // 원단위(톤/톤) = 스팀(톤) * 1000 / 생산량(kg).
    const unit = production ? (steam * 1000) / production : 0;
    setCellText(row, 5, format(steam));
    setCellText(row, 7, format(unit, 2));
  }

  function updateTmRow(row) {
    const tm3Steam = getNumericInput(row, "steam", 0);
    const tm3Prod = getNumericInput(row, "production", 0);
    const tm4Steam = getNumericInput(row, "steam", 1);
    const tm4Prod = getNumericInput(row, "production", 1);
    const tm5Steam = getNumericInput(row, "steam", 2);
    const tm5Prod = getNumericInput(row, "production", 2);
    // 원단위(톤/톤) = 스팀(톤) * 1000 / 생산량(kg).
    const tm3Unit = tm3Prod ? (tm3Steam * 1000) / tm3Prod : 0;
    const tm4Unit = tm4Prod ? (tm4Steam * 1000) / tm4Prod : 0;
    const tm5Unit = tm5Prod ? (tm5Steam * 1000) / tm5Prod : 0;
    setCellText(row, 3, format(tm3Unit, 2));
    setCellText(row, 6, format(tm4Unit, 2));
    setCellText(row, 9, format(tm5Unit, 2));
  }

  function syncMonthlySummaryTables() {
    const pm2Rows = Array.from(document.querySelectorAll('[data-unit-monthly="pm2"] tbody tr'));
    const pm3Rows = Array.from(document.querySelectorAll('[data-unit-monthly="pm3"] tbody tr'));
    const tmRows = Array.from(document.querySelectorAll('[data-unit-monthly="tm"] tbody tr'));
    const paperBody = document.querySelector('[data-machine-panel="paper-total"] tbody');
    const tissueBody = document.querySelector('[data-machine-panel="tissue-total"] tbody');
    const totalBody = document.querySelector('[data-machine-panel="other-total"] tbody');
    if (!paperBody || !tissueBody || !totalBody) return;

    const paperRows = [];
    const tissueRows = [];
    const totalRows = [];

    pm2Rows.forEach((pm2Row, index) => {
      const pm3Row = pm3Rows[index];
      const tmRow = tmRows[index];
      if (!pm3Row || !tmRow) return;

      const day = pm2Row.children[0].textContent;
      const inactiveDay = pm2Row.classList.contains("invalid-day-row");
      const pm2Steam = asNumber(pm2Row.children[3].textContent) ?? 0;
      const pm2Prod = getNumericInput(pm2Row, "production");
      const pm3Steam = asNumber(pm3Row.children[5].textContent) ?? 0;
      const pm3Prod = getNumericInput(pm3Row, "production");
      const paperSteam = pm2Steam + pm3Steam;
      const paperProd = pm2Prod + pm3Prod;
      // 원단위(톤/톤) = 스팀(톤) * 1000 / 생산량(kg).
      const paperUnit = paperProd ? (paperSteam * 1000) / paperProd : 0;

      const tm3Steam = getNumericInput(tmRow, "steam", 0);
      const tm3Prod = getNumericInput(tmRow, "production", 0);
      const tm4Steam = getNumericInput(tmRow, "steam", 1);
      const tm4Prod = getNumericInput(tmRow, "production", 1);
      const tm5Steam = getNumericInput(tmRow, "steam", 2);
      const tm5Prod = getNumericInput(tmRow, "production", 2);
      const tissueSteam = tm3Steam + tm4Steam + tm5Steam;
      const tissueProd = tm3Prod + tm4Prod + tm5Prod;
      const tissueUnit = tissueProd ? (tissueSteam * 1000) / tissueProd : 0;
      const totalSteam = paperSteam + tissueSteam;
      const totalProd = paperProd + tissueProd;
      const totalUnit = totalProd ? (totalSteam * 1000) / totalProd : 0;
      const disperserValue = pm3Row.querySelector('[data-field="disperser_total"]')?.value ?? "";
      const currentTotalRow = totalBody.children[index];
      const tocValue = currentTotalRow?.querySelector('[data-field="toc_steam"]')?.value ?? "";
      const picaValue = currentTotalRow?.querySelector('[data-field="pica121_vent"]')?.value ?? "";

      paperRows.push(`<tr${inactiveDay ? ' class="invalid-day-row"' : ""}><td>${day}</td><td>${format(paperSteam)}</td><td>${format(paperProd)}</td><td>${format(paperUnit, 2)}</td></tr>`);
      tissueRows.push(`<tr${inactiveDay ? ' class="invalid-day-row"' : ""}><td>${day}</td><td>${format(tissueSteam)}</td><td>${format(tissueProd)}</td><td>${format(tissueUnit, 2)}</td></tr>`);
      totalRows.push(`<tr data-day="${day}"${inactiveDay ? ' class="invalid-day-row"' : ""}><td>${day}</td><td>${format(totalSteam)}</td><td>${format(totalProd)}</td><td>${format(totalUnit, 2)}</td><td>${disperserValue}</td><td>${inputCell(tocValue, 'data-kind="raw" data-field="toc_steam"', inactiveDay)}</td><td>${inputCell(picaValue, 'data-kind="raw" data-field="pica121_vent"', inactiveDay)}</td></tr>`);
    });

    paperBody.innerHTML = paperRows.join("");
    tissueBody.innerHTML = tissueRows.join("");
    totalBody.innerHTML = totalRows.join("");
  }

  function renderUnitMonthly() {
    const currentMonth = monthKey();
    const pm2Body = document.querySelector('[data-unit-monthly="pm2"] tbody');
    const pm3Body = document.querySelector('[data-unit-monthly="pm3"] tbody');
    const tmBody = document.querySelector('[data-unit-monthly="tm"] tbody');
    const paperBody = document.querySelector('[data-machine-panel="paper-total"] tbody');
    const tissueBody = document.querySelector('[data-machine-panel="tissue-total"] tbody');
    const totalBody = document.querySelector('[data-machine-panel="other-total"] tbody');
    if (!pm2Body || !pm3Body || !tmBody || !paperBody || !tissueBody || !totalBody) return;

    const pm2Rows = [];
    const pm3Rows = [];
    const tmRows = [];
    const paperRows = [];
    const tissueRows = [];
    const totalRows = [];

    const maxDays = daysInMonth();
    for (let day = 1; day <= DAILY_ROW_COUNT; day += 1) {
      const m = metricForDay(currentMonth, day);
      const d = String(day).padStart(2, "0");
      const inactiveDay = day > maxDays;

      pm2Rows.push(`<tr data-day="${d}"${inactiveDay ? ' class="invalid-day-row"' : ""}><td>${d}</td><td>${inputCell(m.pm2.main_steam ?? "", 'data-kind="unit" data-machine="PM-2" data-field="main_steam"', inactiveDay)}</td><td>${inputCell(m.pm2.coater_steam ?? "", 'data-kind="unit" data-machine="PM-2" data-field="coater_steam"', inactiveDay)}</td><td>${cell(format(m.pm2Steam))}</td><td>${inputCell(m.pm2.production ?? "", 'data-kind="unit" data-machine="PM-2" data-field="production"', inactiveDay)}</td><td>${cell(format(m.pm2Unit, 2))}</td><td>${inputCell(m.pm2.production_type ?? "", 'data-kind="unit-text" data-machine="PM-2" data-field="production_type" placeholder="직접입력" autocomplete="off"', inactiveDay)}</td></tr>`);

      pm3Rows.push(`<tr data-day="${d}"${inactiveDay ? ' class="invalid-day-row"' : ""}><td>${d}</td><td>${inputCell(m.pm3.main_steam ?? "", 'data-kind="unit" data-machine="PM-3" data-field="main_steam"', inactiveDay)}</td><td>${inputCell(m.pm3.coater_steam ?? "", 'data-kind="unit" data-machine="PM-3" data-field="coater_steam"', inactiveDay)}</td><td>${inputCell(m.raw.disperser_total ?? "", 'data-kind="raw" data-field="disperser_total"', inactiveDay)}</td><td>${inputCell(m.pm3.ventilation_steam ?? "", 'data-kind="unit" data-machine="PM-3" data-field="ventilation_steam"', inactiveDay)}</td><td>${cell(format(m.pm3Steam))}</td><td>${inputCell(m.pm3.production ?? "", 'data-kind="unit" data-machine="PM-3" data-field="production"', inactiveDay)}</td><td>${cell(format(m.pm3Unit, 2))}</td><td>${inputCell(m.pm3.production_type ?? "", 'data-kind="unit-text" data-machine="PM-3" data-field="production_type" placeholder="직접입력" autocomplete="off"', inactiveDay)}</td></tr>`);

      tmRows.push(`<tr data-day="${d}"${inactiveDay ? ' class="invalid-day-row"' : ""}><td>${d}</td><td>${inputCell(m.tm3.steam ?? "", 'data-kind="unit" data-machine="TM-3" data-field="steam"', inactiveDay)}</td><td>${inputCell(m.tm3.production ?? "", 'data-kind="unit" data-machine="TM-3" data-field="production"', inactiveDay)}</td><td>${cell(format(m.tm3Unit, 2))}</td><td>${inputCell(m.tm4.steam ?? "", 'data-kind="unit" data-machine="TM-4" data-field="steam"', inactiveDay)}</td><td>${inputCell(m.tm4.production ?? "", 'data-kind="unit" data-machine="TM-4" data-field="production"', inactiveDay)}</td><td>${cell(format(m.tm4Unit, 2))}</td><td>${inputCell(m.tm5.steam ?? "", 'data-kind="unit" data-machine="TM-5" data-field="steam"', inactiveDay)}</td><td>${inputCell(m.tm5.production ?? "", 'data-kind="unit" data-machine="TM-5" data-field="production"', inactiveDay)}</td><td>${cell(format(m.tm5Unit, 2))}</td></tr>`);

      paperRows.push(`<tr><td>${d}</td><td>${format(m.paperSteam)}</td><td>${format(m.paperProd)}</td><td>${format(m.paperUnit, 2)}</td></tr>`);
      tissueRows.push(`<tr><td>${d}</td><td>${format(m.tissueSteam)}</td><td>${format(m.tissueProd)}</td><td>${format(m.tissueUnit, 2)}</td></tr>`);
      totalRows.push(`<tr${inactiveDay ? ' class="invalid-day-row"' : ""}><td>${d}</td><td>${format(m.totalSteam)}</td><td>${format(m.totalProd)}</td><td>${format(m.totalUnit, 2)}</td><td>${cell(format(m.disperser))}</td><td>${inputCell(m.raw.toc_steam ?? "", 'data-kind="raw" data-field="toc_steam"', inactiveDay)}</td><td>${inputCell(m.raw.pica121_vent ?? "", 'data-kind="raw" data-field="pica121_vent"', inactiveDay)}</td></tr>`);
    }

    pm2Body.innerHTML = pm2Rows.join("");
    pm3Body.innerHTML = pm3Rows.join("");
    tmBody.innerHTML = tmRows.join("");
    paperBody.innerHTML = paperRows.join("");
    tissueBody.innerHTML = tissueRows.join("");
    totalBody.innerHTML = totalRows.join("");
    syncMonthlySummaryTables();
    bindMonthlyInputs();
    applyUnitTableAlignment();
  }

  function renderYearly() {
    const pm2Body = document.querySelector('[data-yearly-table="pm2"] tbody');
    const pm3Body = document.querySelector('[data-yearly-table="pm3"] tbody');
    const tmBody = document.querySelector('[data-yearly-table="tm"] tbody');
    const paperBody = document.querySelector('[data-yearly-table="paper-total"] tbody');
    const tissueBody = document.querySelector('[data-yearly-table="tissue-total"] tbody');
    if (!pm2Body || !pm3Body || !tmBody || !paperBody || !tissueBody) return;

    const pm2Rows = [];
    const pm3Rows = [];
    const tmRows = [];
    const paperRows = [];
    const tissueRows = [];

    for (let month = 1; month <= 12; month += 1) {
      const total = aggregateMonth(yearSelect.value, month);
      const label = `${month}\uC6D4`;

      pm2Rows.push(`<tr><td>${label}</td><td>${format(total.pm2Main)}</td><td>${format(total.pm2Coater)}</td><td>${format(total.pm2Steam)}</td><td>${format(total.pm2Prod)}</td><td>${format(total.pm2Unit, 3)}</td></tr>`);
      pm3Rows.push(`<tr><td>${label}</td><td>${format(total.pm3Main)}</td><td>${format(total.pm3Coater)}</td><td>${format(total.disperser)}</td><td>${format(total.vent)}</td><td>${format(total.pm3Steam)}</td><td>${format(total.pm3Prod)}</td><td>${format(total.pm3Unit, 3)}</td></tr>`);
      tmRows.push(`<tr><td>${label}</td><td>${format(total.tm3Steam)}</td><td>${format(total.tm3Prod)}</td><td>${format(total.tm3Unit, 3)}</td><td>${format(total.tm4Steam)}</td><td>${format(total.tm4Prod)}</td><td>${format(total.tm4Unit, 3)}</td><td>${format(total.tm5Steam)}</td><td>${format(total.tm5Prod)}</td><td>${format(total.tm5Unit, 3)}</td></tr>`);
      paperRows.push(`<tr><td>${label}</td><td>${format(total.paperSteam)}</td><td>${format(total.paperProd)}</td><td>${format(total.paperUnit, 3)}</td></tr>`);
      tissueRows.push(`<tr><td>${label}</td><td>${format(total.tissueSteam)}</td><td>${format(total.tissueProd)}</td><td>${format(total.tissueUnit, 3)}</td></tr>`);
    }

    pm2Body.innerHTML = pm2Rows.join("");
    pm3Body.innerHTML = pm3Rows.join("");
    tmBody.innerHTML = tmRows.join("");
    paperBody.innerHTML = paperRows.join("");
    tissueBody.innerHTML = tissueRows.join("");
    applyUnitTableAlignment();
  }

  function buildPath(values, min, max, width, height, leftPad, topPad, chartWidth, chartHeight) {
    const points = values.map((value, index) => {
      const x = leftPad + (index * chartWidth) / Math.max(1, values.length - 1);
      const ratio = max === min ? 0 : (value - min) / (max - min);
      const y = topPad + chartHeight - ratio * chartHeight;
      return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    });
    return points.join(" ");
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
    for (let value = 0; value <= top; value += step) {
      ticks.push(value);
    }
    if (ticks.length < 2) {
      ticks.push(step);
    }
    return ticks;
  }

  function formatChartValue(value) {
    const numeric = Number(value) || 0;
    return new Intl.NumberFormat("ko-KR", {
      maximumFractionDigits: 0,
    }).format(numeric);
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
    const labels = config.labels || Array.from({ length: daysInMonth() }, (_, index) => index + 1);
    const ticks = config.ticks?.length ? config.ticks : computeAutoTicks(config.series);
    const min = 0;
    const max = ticks[ticks.length - 1];

    const grid = ticks.map((tick) => {
      const ratio = (tick - min) / (max - min);
      const y = topPad + chartHeight - ratio * chartHeight;
      return `<g><line x1="${leftPad}" y1="${y}" x2="${width - rightPad}" y2="${y}" class="chart-grid-line" /><text x="${leftPad - 10}" y="${y + 4}" class="chart-axis-label" text-anchor="end">${tick}</text></g>`;
    }).join("");

    const xAxisLabels = labels.map((label, index) => {
      const x = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
      return `<text x="${x}" y="${height - 18}" class="chart-axis-label chart-x-axis-label" text-anchor="middle">${label}</text>`;
    }).join("");

    const series = config.series.map((item) => {
      const path = buildPath(item.values, min, max, width, height, leftPad, topPad, chartWidth, chartHeight);
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
      const previousX = index === 0
        ? leftPad
        : leftPad + ((index - 1) * chartWidth) / Math.max(1, labels.length - 1);
      const currentX = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
      const nextX = index === labels.length - 1
        ? leftPad + chartWidth
        : leftPad + ((index + 1) * chartWidth) / Math.max(1, labels.length - 1);
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
        <g>${series}</g>
        <g>${points}</g>
        <g>${xAxisLabels}</g>
        <g>${hoverColumns}</g>
        <g>${legend}</g>
      </svg>
    `;

    const tooltip = container.querySelector(".chart-tooltip");
    const hoverTargets = container.querySelectorAll(".chart-hover-column");

    function hideTooltip() {
      if (!tooltip) return;
      tooltip.hidden = true;
    }

    hoverTargets.forEach((target) => {
      target.addEventListener("mouseenter", () => {
        const index = Number(target.dataset.index);
        const title = labels[index];
        const rows = config.series.map((item) => (
          `<div class="chart-tooltip-row"><span class="chart-tooltip-swatch" style="background:${item.color}"></span><span>${item.label}</span><strong>${formatChartValue(item.values[index])}</strong></div>`
        )).join("");
        tooltip.innerHTML = `<div class="chart-tooltip-title">${title}</div>${rows}`;
        tooltip.hidden = false;
      });
      target.addEventListener("mousemove", (event) => {
        if (!tooltip) return;
        positionChartTooltip(container, tooltip, event);
      });

      target.addEventListener("mouseleave", hideTooltip);
    });

    container.addEventListener("mouseleave", hideTooltip);
  }

  function renderSteamCharts() {
    const monthlyPaperWrap = document.querySelector('[data-chart-wrap="monthly-paper"]');
    const monthlyTissueWrap = document.querySelector('[data-chart-wrap="monthly-tissue"]');
    const yearlyPaperWrap = document.querySelector('[data-chart-wrap="yearly-paper"]');
    const yearlyTissueWrap = document.querySelector('[data-chart-wrap="yearly-tissue"]');
    const summaryPaperWrap = document.querySelector('[data-chart-wrap="paper"]');
    const summaryTissueWrap = document.querySelector('[data-chart-wrap="tissue"]');
    const maxDays = daysInMonth();
    const monthLabels = Array.from({ length: maxDays }, (_, index) => index + 1);
    const yearLabels = Array.from({ length: 12 }, (_, index) => `${index + 1}\uC6D4`);

    if (monthlyPaperWrap || monthlyTissueWrap) {
      const currentMonth = monthKey();
      const monthlyPaperSeries = [
        { label: "PM-2 Main", color: "#1f4b99", values: [] },
        { label: "PM-3 Main", color: "#0b7a75", values: [] },
        { label: "PM-3 Disperser", color: "#d97706", values: [] },
        { label: "PM-2 Coater", color: "#7c3aed", values: [] },
        { label: "PM-3 Coater", color: "#dc2626", values: [] },
        { label: "PM-3 Vent", color: "#059669", values: [] },
      ];
      const monthlyTissueSeries = [
        { label: "TM-3", color: "#1f4b99", values: [] },
        { label: "TM-4", color: "#d97706", values: [] },
        { label: "TM-5", color: "#059669", values: [] },
      ];

      for (let day = 1; day <= maxDays; day += 1) {
        const m = metricForDay(currentMonth, day);
        monthlyPaperSeries[0].values.push(m.pm2Main);
        monthlyPaperSeries[1].values.push(m.pm3Main);
        monthlyPaperSeries[2].values.push(m.disperser);
        monthlyPaperSeries[3].values.push(m.pm2Coater);
        monthlyPaperSeries[4].values.push(m.pm3Coater);
        monthlyPaperSeries[5].values.push(m.vent);
        monthlyTissueSeries[0].values.push(m.tm3Steam);
        monthlyTissueSeries[1].values.push(m.tm4Steam);
        monthlyTissueSeries[2].values.push(m.tm5Steam);
      }

      if (monthlyPaperWrap) {
        renderLineChart(monthlyPaperWrap, {
          title: "Monthly Paper Steam",
          labels: monthLabels,
          series: monthlyPaperSeries,
        });
      }

      if (monthlyTissueWrap) {
        renderLineChart(monthlyTissueWrap, {
          title: "Monthly Tissue Steam",
          labels: monthLabels,
          series: monthlyTissueSeries,
        });
      }
    }

    const yearlyPaperSeries = [
      { label: "PM-2 Main", color: "#1f4b99", values: [] },
      { label: "PM-3 Main", color: "#0b7a75", values: [] },
      { label: "PM-3 Disperser", color: "#d97706", values: [] },
      { label: "PM-2 Coater", color: "#7c3aed", values: [] },
      { label: "PM-3 Coater", color: "#dc2626", values: [] },
      { label: "PM-3 Vent", color: "#059669", values: [] },
    ];

    const yearlyTissueSeries = [
      { label: "TM-3", color: "#1f4b99", values: [] },
      { label: "TM-4", color: "#d97706", values: [] },
      { label: "TM-5", color: "#059669", values: [] },
    ];

    for (let month = 1; month <= 12; month += 1) {
      const m = aggregateMonth(yearSelect.value, month);
      yearlyPaperSeries[0].values.push(m.pm2Main);
      yearlyPaperSeries[1].values.push(m.pm3Main);
      yearlyPaperSeries[2].values.push(m.disperser);
      yearlyPaperSeries[3].values.push(m.pm2Coater);
      yearlyPaperSeries[4].values.push(m.pm3Coater);
      yearlyPaperSeries[5].values.push(m.vent);
      yearlyTissueSeries[0].values.push(m.tm3Steam);
      yearlyTissueSeries[1].values.push(m.tm4Steam);
      yearlyTissueSeries[2].values.push(m.tm5Steam);
    }

    if (yearlyPaperWrap) {
      renderLineChart(yearlyPaperWrap, {
        title: "Yearly Paper Steam",
        labels: yearLabels,
        series: yearlyPaperSeries,
      });
    }

    if (yearlyTissueWrap) {
      renderLineChart(yearlyTissueWrap, {
        title: "Yearly Tissue Steam",
        labels: yearLabels,
        series: yearlyTissueSeries,
      });
    }

    if (summaryPaperWrap) {
      renderLineChart(summaryPaperWrap, {
        title: "Summary Paper Steam",
        labels: yearLabels,
        series: yearlyPaperSeries,
      });
    }

    if (summaryTissueWrap) {
      renderLineChart(summaryTissueWrap, {
        title: "Summary Tissue Steam",
        labels: yearLabels,
        series: yearlyTissueSeries,
      });
    }
  }
  async function saveInput(input) {
    const row = input.closest("tr");
    const day = row?.dataset.day;
    if (!day) return;

    const currentMonth = monthKey();
    const kind = input.dataset.kind;
    const field = input.dataset.field;
    const normalizedDay = normalizeDayValue(day);

    if (kind === "unit" || kind === "unit-text") {
      const machine = input.dataset.machine;
      const existing = findUnit(currentMonth, normalizedDay, machine);
      const meta = monthMeta();
      const payload = {
        ...meta,
        day: normalizedDay,
        machine_no: machine,
        [field]: kind === "unit-text" ? input.value : asNumber(editableRawValue(input)),
      };
      const saved = await upsert(DB_TABLE_UNIT, payload, existing?.id);
      if (saved) {
        const index = unitRows.findIndex((rowItem) => rowItem.id === saved.id);
        if (index >= 0) unitRows[index] = saved;
        else unitRows.push(saved);
      }
    }

    if (kind === "raw") {
      const existing = findRaw(currentMonth, normalizedDay);
      const meta = monthMeta();
      const payload = {
        ...meta,
        day: normalizedDay,
        [field]: asNumber(editableRawValue(input)),
      };
      const saved = await upsert(DB_TABLE_UNIT_RAW, payload, existing?.id);
      if (saved) {
        const index = rawRows.findIndex((rowItem) => rowItem.id === saved.id);
        if (index >= 0) rawRows[index] = saved;
        else rawRows.push(saved);
      }
    }

    const table = input.closest("table");
    const mode = table?.dataset.unitMonthly;
    const rowElement = input.closest("tr");
    if (mode === "pm2" && rowElement) updatePm2Row(rowElement);
    if (mode === "pm3" && rowElement) updatePm3Row(rowElement);
    if (mode === "tm" && rowElement) updateTmRow(rowElement);
    syncMonthlySummaryTables();
    renderYearly();
    renderSteamCharts();
    bindMonthlyInputs();
    applyUnitTableAlignment();
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
      await saveInput(input);
    }
  }

  window.UnitPageSaveCoordinator?.registerFlush(flushDirtyInputs);

  function queueSaveInput(input, delay = 120) {
    dirtyInputs.add(input);
    const existingTimer = pendingSaveTimers.get(input);
    if (existingTimer) clearTimeout(existingTimer);

    const timer = setTimeout(async () => {
      pendingSaveTimers.delete(input);
      if (!dirtyInputs.has(input)) return;
      dirtyInputs.delete(input);
      await saveInput(input);
    }, delay);

    pendingSaveTimers.set(input, timer);
  }

  function bindMonthlyInputs() {
    const editableInputs = Array.from(document.querySelectorAll('[data-period-panel="monthly"] .table-edit-input'));

    editableInputs.forEach((input, index) => {
      if (input.dataset.bound === "true") return;
      input.dataset.bound = "true";

      input.addEventListener("input", () => {
        const rowElement = input.closest("tr");
        const table = input.closest("table");
        const mode = table?.dataset.unitMonthly;
        if (mode === "pm2" && rowElement) updatePm2Row(rowElement);
        if (mode === "pm3" && rowElement) updatePm3Row(rowElement);
        if (mode === "tm" && rowElement) updateTmRow(rowElement);
        syncMonthlySummaryTables();
        queueSaveInput(input);
      });

      input.addEventListener("change", () => {
        if (input.dataset.kind !== "unit-text") {
          input.dataset.rawValue = normalizeEditableValue(input.value);
          input.value = formatEditableValue(input.value);
        }
        dirtyInputs.add(input);
        void flushDirtyInputs();
      });

      if (input.dataset.kind !== "unit-text") {
        input.addEventListener("focus", () => {
          input.value = input.dataset.rawValue ?? normalizeEditableValue(input.value);
        });

        input.addEventListener("blur", () => {
          input.dataset.rawValue = normalizeEditableValue(input.value);
          input.value = formatEditableValue(input.value);
        });
      }

      if (input.dataset.field === "production_type") {
        input.addEventListener("focus", () => openProductionTypeDropdown(input));
        input.addEventListener("click", () => openProductionTypeDropdown(input));
        input.addEventListener("blur", () => closeProductionTypeDropdown(input));
        input.addEventListener("keydown", (event) => {
          if (event.key === "Escape") closeProductionTypeDropdown(input);
        });
      }

      input.addEventListener("keydown", async (event) => {
        if (event.key !== "Tab") return;
        event.preventDefault();
        const editableOnly = Array.from(document.querySelectorAll('[data-period-panel="monthly"] .table-edit-input'))
          .filter((item) => !item.readOnly);
        const currentIndex = editableOnly.indexOf(input);
        if (currentIndex < 0) return;
        const nextIndex = event.shiftKey ? currentIndex - 1 : currentIndex + 1;
        dirtyInputs.add(input);
        await flushDirtyInputs();
        const refreshedInputs = Array.from(document.querySelectorAll('[data-period-panel="monthly"] .table-edit-input'))
          .filter((item) => !item.readOnly);
        const target = refreshedInputs[nextIndex];
        if (target) {
          target.focus();
          target.select?.();
        }
      });
    });
  }

  async function ensureLoaded() {
    const year = String(yearSelect.value);
    if (loaded && loadedYear === year) return;
    const range = { fromMonth: `${year}-01`, toMonth: `${year}-12` };
    const [units, raws] = await Promise.all([fetchAllPages(DB_TABLE_UNIT, range), fetchAllPages(DB_TABLE_UNIT_RAW, range)]);
    unitRows.length = 0;
    rawRows.length = 0;
    unitRows.push(...units);
    rawRows.push(...raws);
    loaded = true;
    loadedYear = year;
  }

  async function render() {
    await ensureLoaded();
    renderUnitMonthly();
    renderYearly();
    renderSteamCharts();
    applyUnitTableAlignment();
  }

  async function importUnitExcel(file) {
    if (!file || uploadInFlight) return;
    uploadInFlight = true;
    if (unitExcelUploadButton) {
      unitExcelUploadButton.disabled = true;
      unitExcelUploadButton.textContent = "Uploading...";
    }

    try {
      await flushDirtyInputs();
      const formData = new FormData();
      formData.append("file", file);
      formData.append("year", uploadYearSelect?.value || yearSelect.value);

      const response = await fetch("/tables/unit-usage/import-excel", {
        method: "POST",
        body: formData,
      });

      if (!response.ok) { throw new Error(await window.ExcelUploadErrors?.message(response) || "엑셀 업로드에 실패했습니다."); }

      resetLoadedState();
      await render();
      alert("?묒? ?낅줈?쒓? ?꾨즺?섏뿀?듬땲??");
    } catch (error) {
      alert(error instanceof Error ? error.message : "?묒? ?낅줈??以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.");
    } finally {
      uploadInFlight = false;
      if (unitExcelUploadButton) {
        unitExcelUploadButton.disabled = false;
        unitExcelUploadButton.textContent = "엑셀 업로드";
      }
      if (unitExcelFileInput) {
        unitExcelFileInput.value = "";
      }
    }
  }

  const flushSelectors = [
    "[data-machine-target]",
    "[data-yearly-machine-target]",
    "[data-period-target]",
    "#filterSubmit",
    "#filterYear",
    "#filterMonth",
  ];

  document.querySelectorAll(flushSelectors.join(",")).forEach((element) => {
    element.addEventListener("click", () => {
      void flushDirtyInputs();
    });
    element.addEventListener("change", () => {
      void flushDirtyInputs();
    });
  });

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

  unitExcelUploadButton?.addEventListener("click", async () => {
    await flushDirtyInputs();
    unitExcelFileInput?.click();
  });

  unitExcelDownloadButton?.addEventListener("click", async () => {
    await flushDirtyInputs();
    try {
      await downloadUnitExcel();
    } catch (error) {
      alert(error instanceof Error ? error.message : "?묒? ?ㅼ슫濡쒕뱶 以??ㅻ쪟媛 諛쒖깮?덉뒿?덈떎.");
    }
  });

  unitExcelFileInput?.addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    await importUnitExcel(file);
  });

  if (uploadYearSelect && yearSelect.options.length) {
    uploadYearSelect.innerHTML = yearSelect.innerHTML;
    uploadYearSelect.value = yearSelect.value;
  }

  render();
});



