document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "unit-machine-summary.html") return;

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
  let loadedYear = "";

  const CELL_TABLE_NAMES = [
    "lng_m_boiler",
    "lng_m_burner",
    "flow_m_fluid_incinerator",
    "flow_m_combined",
    "flow_m_boiler",
  ];

  function cellKey(tableName, month, rowKey, colIndex) {
    return `${tableName}|${month}|${rowKey}|${Number(colIndex)}`;
  }

  function unitKey(month, day, machineNo) {
    return `${month}|${normalizeDayValue(day)}|${machineNo}`;
  }

  function rawKey(month, day) {
    return `${month}|${normalizeDayValue(day)}`;
  }

  function rebuildIndexes() {
    cellIndex.clear();
    unitIndex.clear();
    rawIndex.clear();
    cellRows.forEach((row) => cellIndex.set(cellKey(row.table_name, row.month, row.row_key, row.col_index), row));
    unitRows.forEach((row) => unitIndex.set(unitKey(row.month, row.day, row.machine_no), row));
    rawRows.forEach((row) => rawIndex.set(rawKey(row.month, row.day), row));
  }

  const STEAM_ROWS = [
    { label: "스팀 생산량 누계", unit: "톤" },
    { label: "스팀 생산비율", unit: "%" },
    { label: "LNG 보일러", unit: "톤" },
    { label: "석탄 보일러", unit: "백만원" },
    { label: "폐합성소각로 2기", unit: "" },
    { label: "복합보일러", unit: "백만원" },
    { label: "외부보일러", unit: "백만원" },
    { label: "연료비", unit: "" },
  ];

  const FUEL_ROWS = [
    { label: "스팀 생산량 누계", unit: "톤" },
    { label: "스팀 생산비율", unit: "%" },
    { label: "LNG", unit: "백만원" },
    { label: "석탄", unit: "백만원" },
    { label: "복합보일러", unit: "백만원" },
    { label: "외부보일러", unit: "백만원" },
    { label: "연료비", unit: "백만원" },
    { label: "연료비 원단위", unit: "천원/톤·제품" },
  ];

  STEAM_ROWS.splice(
    0,
    STEAM_ROWS.length,
    { label: "\uC2A4\uD300 \uC0DD\uC0B0\uB7C9 \uB204\uACC4", unit: "\uD1A4" },
    { label: "\uC2A4\uD300 \uC0DD\uC0B0\uBE44\uC728", unit: "%" },
    { label: "LNG \uBCF4\uC77C\uB7EC", unit: "\uD1A4" },
    { label: "\uC11D\uD0C4 \uBCF4\uC77C\uB7EC", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uD3D0\uD569\uC131\uC18C\uAC01\uB85C 2\uAE30", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uBCF5\uD569\uBCF4\uC77C\uB7EC", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uC678\uBD80\uBCF4\uC77C\uB7EC", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uC5F0\uB8CC\uBE44", unit: "\uBC31\uB9CC\uC6D0" },
  );

  FUEL_ROWS.splice(
    0,
    FUEL_ROWS.length,
    { label: "\uC2A4\uD300 \uC0DD\uC0B0\uB7C9 \uB204\uACC4", unit: "\uD1A4" },
    { label: "\uC2A4\uD300 \uC0DD\uC0B0\uBE44\uC728", unit: "%" },
    { label: "LNG", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uC11D\uD0C4", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uBCF5\uD569\uBCF4\uC77C\uB7EC", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uC678\uBD80\uBCF4\uC77C\uB7EC", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uC5F0\uB8CC\uBE44", unit: "\uBC31\uB9CC\uC6D0" },
    { label: "\uC5F0\uB8CC\uBE44 \uC6D0\uB2E8\uC704", unit: "\uCC9C\uC6D0/\uD1A4\u00B7\uC81C\uD488" },
  );

  function monthKey(year = yearSelect.value, month = monthSelect.value) {
    return `${year}-${month}`;
  }

  function daysInMonth(year = Number(yearSelect.value), month = Number(monthSelect.value)) {
    return new Date(year, month, 0).getDate();
  }

  function normalizeDayValue(day) {
    const numeric = Number(day);
    if (Number.isFinite(numeric) && numeric > 0) return String(numeric);
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
    return new Intl.NumberFormat("ko-KR", {
      maximumFractionDigits: 0,
      minimumFractionDigits: 0,
    }).format(value);
  }

  function formatOne(value) {
    if (!Number.isFinite(value)) return "";
    return new Intl.NumberFormat("ko-KR", {
      maximumFractionDigits: 1,
      minimumFractionDigits: 1,
    }).format(value);
  }

  function formatPercent(value) {
    if (!Number.isFinite(value)) return "";
    return `${formatOne(value)}%`;
  }

  async function fetchPaged(tableName, range = {}, extra = {}) {
    const data = [];
    let pageNo = 1;
    let totalPages = 1;
    const params = new URLSearchParams({ page: String(pageNo), limit: "2000" });
    if (range.fromMonth) params.set("fromMonth", range.fromMonth);
    if (range.toMonth) params.set("toMonth", range.toMonth);
    if (extra.tableNames) params.set("tableNames", extra.tableNames);
    while (pageNo <= totalPages) {
      params.set("page", String(pageNo));
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

  function getCellValue(tableName, month, rowKey, colIndex) {
    return cellIndex.get(cellKey(tableName, month, rowKey, colIndex))?.cell_value ?? "";
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
    const pm3Steam =
      Number(pm3.main_steam || 0) +
      Number(pm3.coater_steam || 0) +
      Number(raw.disperser_total || 0) +
      Number(pm3.ventilation_steam || 0);
    const tm3Steam = Number(tm3.steam || 0);
    const tm4Steam = Number(tm4.steam || 0);
    const tm5Steam = Number(tm5.steam || 0);
    const totalSteam = pm2Steam + pm3Steam + tm3Steam + tm4Steam + tm5Steam;

    const pm2Prod = Number(pm2.production || 0);
    const pm3Prod = Number(pm3.production || 0);
    const tm3Prod = Number(tm3.production || 0);
    const tm4Prod = Number(tm4.production || 0);
    const tm5Prod = Number(tm5.production || 0);
    const totalProd = pm2Prod + pm3Prod + tm3Prod + tm4Prod + tm5Prod;

    return {
      pm2Steam,
      pm3Steam,
      tm3Steam,
      tm4Steam,
      tm5Steam,
      totalSteam,
      pm2Prod,
      pm3Prod,
      tm3Prod,
      tm4Prod,
      tm5Prod,
      totalProd,
    };
  }

  function monthTotals() {
    const month = monthKey();
    const totals = {
      pm2Steam: 0,
      pm3Steam: 0,
      tm3Steam: 0,
      tm4Steam: 0,
      tm5Steam: 0,
      totalSteam: 0,
      pm2Prod: 0,
      pm3Prod: 0,
      tm3Prod: 0,
      tm4Prod: 0,
      tm5Prod: 0,
      totalProd: 0,
      lngUsage: 0,
      lngCost: 0,
      fluidCost: 0,
      incineratorSteam: 0,
      combinedSteam: 0,
      combinedCost: 0,
      boilerSteam: 0,
      boilerCost: 0,
    };

    const maxDays = daysInMonth();
    for (let day = 1; day <= maxDays; day += 1) {
      const rowKey = String(day).padStart(2, "0");
      const m = steamMetric(month, rowKey);

      totals.pm2Steam += m.pm2Steam;
      totals.pm3Steam += m.pm3Steam;
      totals.tm3Steam += m.tm3Steam;
      totals.tm4Steam += m.tm4Steam;
      totals.tm5Steam += m.tm5Steam;
      totals.totalSteam += m.totalSteam;
      totals.pm2Prod += m.pm2Prod;
      totals.pm3Prod += m.pm3Prod;
      totals.tm3Prod += m.tm3Prod;
      totals.tm4Prod += m.tm4Prod;
      totals.tm5Prod += m.tm5Prod;
      totals.totalProd += m.totalProd;

      totals.lngUsage +=
        parseNumber(getCellValue("lng_m_boiler", month, rowKey, 4)) +
        parseNumber(getCellValue("lng_m_burner", month, rowKey, 5));
      totals.lngCost += parseNumber(getCellValue("lng_m_burner", month, rowKey, 8));

      totals.fluidCost += parseNumber(getCellValue("flow_m_fluid_incinerator", month, rowKey, 2));
      totals.incineratorSteam += parseNumber(getCellValue("flow_m_fluid_incinerator", month, rowKey, 3));
      totals.combinedSteam += parseNumber(getCellValue("flow_m_combined", month, rowKey, 0));
      totals.combinedCost += parseNumber(getCellValue("flow_m_combined", month, rowKey, 4));
      totals.boilerSteam += parseNumber(getCellValue("flow_m_boiler", month, rowKey, 0));
      totals.boilerCost += parseNumber(getCellValue("flow_m_boiler", month, rowKey, 4));
    }

    return totals;
  }

  function steamRatios(totals) {
    const total = totals.totalSteam;
    if (total <= 0) return [0, 0, 0, 0, 0];
    return [
      totals.pm2Steam / total,
      totals.pm3Steam / total,
      totals.tm3Steam / total,
      totals.tm4Steam / total,
      totals.tm5Steam / total,
    ];
  }

  function splitByRatios(total, ratios) {
    if (!total) return [0, 0, 0, 0, 0];
    return ratios.map((ratio) => total * ratio);
  }

  function buildSteamAllocationRows(totals) {
    const ratios = steamRatios(totals);
    const rows = [];

    rows.push([
      formatInt(totals.pm2Steam),
      formatInt(totals.pm3Steam),
      formatInt(totals.tm3Steam),
      formatInt(totals.tm4Steam),
      formatInt(totals.tm5Steam),
      formatInt(totals.totalSteam),
    ]);
    rows.push(ratios.map((value) => formatPercent(value * 100)).concat(formatPercent(100)));
    rows.push(splitByRatios(totals.lngUsage, ratios).map(formatOne).concat(formatInt(totals.lngUsage)));
    rows.push(splitByRatios(totals.fluidCost, ratios).map(formatOne).concat(formatOne(totals.fluidCost)));
    rows.push(
      splitByRatios(totals.incineratorSteam, ratios).map(formatOne).concat(formatInt(totals.incineratorSteam))
    );
    rows.push(splitByRatios(totals.combinedSteam, ratios).map(formatOne).concat(formatInt(totals.combinedSteam)));
    rows.push(splitByRatios(totals.boilerSteam, ratios).map(formatOne).concat(formatInt(totals.boilerSteam)));

    const columnSums = [0, 1, 2, 3, 4].map(
      (index) =>
        parseNumber(rows[2][index]) +
        parseNumber(rows[3][index]) +
        parseNumber(rows[5][index]) +
        parseNumber(rows[6][index])
    );
    const totalSum =
      totals.lngUsage + totals.fluidCost + totals.incineratorSteam + totals.combinedSteam + totals.boilerSteam;
    rows.push(columnSums.map(formatOne).concat(formatOne(totalSum)));
    return rows;
  }

  function buildFuelAllocationRows(totals) {
    const ratios = steamRatios(totals);
    const rows = [];

    rows.push([
      formatInt(totals.pm2Steam),
      formatInt(totals.pm3Steam),
      formatInt(totals.tm3Steam),
      formatInt(totals.tm4Steam),
      formatInt(totals.tm5Steam),
      formatInt(totals.totalSteam),
    ]);
    rows.push(ratios.map((value) => formatPercent(value * 100)).concat(formatPercent(100)));
    rows.push(splitByRatios(totals.lngCost, ratios).map(formatOne).concat(formatOne(totals.lngCost)));
    rows.push(splitByRatios(totals.fluidCost, ratios).map(formatOne).concat(formatOne(totals.fluidCost)));
    rows.push(splitByRatios(totals.combinedCost, ratios).map(formatOne).concat(formatOne(totals.combinedCost)));
    rows.push(splitByRatios(totals.boilerCost, ratios).map(formatOne).concat(formatOne(totals.boilerCost)));

    const sumCols = [0, 1, 2, 3, 4].map(
      (index) =>
        parseNumber(rows[2][index]) +
        parseNumber(rows[3][index]) +
        parseNumber(rows[4][index]) +
        parseNumber(rows[5][index])
    );
    const sumTotal = totals.lngCost + totals.fluidCost + totals.combinedCost + totals.boilerCost;
    rows.push(sumCols.map(formatOne).concat(formatOne(sumTotal)));

    const fuelUnitCols = [
      totals.pm2Prod > 0 ? (sumCols[0] * 1000000) / totals.pm2Prod : 0,
      totals.pm3Prod > 0 ? (sumCols[1] * 1000000) / totals.pm3Prod : 0,
      totals.tm3Prod > 0 ? (sumCols[2] * 1000000) / totals.tm3Prod : 0,
      totals.tm4Prod > 0 ? (sumCols[3] * 1000000) / totals.tm4Prod : 0,
      totals.tm5Prod > 0 ? (sumCols[4] * 1000000) / totals.tm5Prod : 0,
      totals.totalProd > 0 ? (sumTotal * 1000000) / totals.totalProd : 0,
    ];
    rows.push(fuelUnitCols.map(formatOne));
    return rows;
  }

  function buildDailyFuelRows() {
    const month = monthKey();
    const rows = [];

    const maxDays = daysInMonth();
    for (let day = 1; day <= maxDays; day += 1) {
      const rowKey = String(day).padStart(2, "0");
      const m = steamMetric(month, rowKey);
      const totalSteam = m.totalSteam;
      const ratios =
        totalSteam > 0
          ? [m.pm2Steam / totalSteam, m.pm3Steam / totalSteam, m.tm3Steam / totalSteam, m.tm4Steam / totalSteam, m.tm5Steam / totalSteam]
          : [0, 0, 0, 0, 0];

      const totalFuelCost =
        parseNumber(getCellValue("lng_m_burner", month, rowKey, 8)) +
        parseNumber(getCellValue("flow_m_fluid_incinerator", month, rowKey, 2)) +
        parseNumber(getCellValue("flow_m_combined", month, rowKey, 4)) +
        parseNumber(getCellValue("flow_m_boiler", month, rowKey, 4));

      const split = ratios.map((ratio) => totalFuelCost * ratio);
      rows.push({
        day: rowKey,
        values: split.map(formatOne).concat(formatOne(totalFuelCost)),
      });
    }

    return rows;
  }

  function renderAllocationTable(refId, rowDefs, valueRows) {
    const tbody = document.querySelector(`[data-summary-table="${refId}"] tbody`);
    if (!tbody) return;

    const rows = rowDefs.map((rowDef, index) => {
      const values = valueRows[index] || ["", "", "", "", "", ""];
      return `
        <tr>
          <td class="summary-label-cell">${rowDef.label}</td>
          <td class="summary-unit-cell">${rowDef.unit}</td>
          <td class="summary-number-cell">${values[0] ?? ""}</td>
          <td class="summary-number-cell">${values[1] ?? ""}</td>
          <td class="summary-number-cell">${values[2] ?? ""}</td>
          <td class="summary-number-cell">${values[3] ?? ""}</td>
          <td class="summary-number-cell">${values[4] ?? ""}</td>
          <td class="summary-number-cell">${values[5] ?? ""}</td>
        </tr>
      `;
    });

    tbody.innerHTML = rows.join("");
    applySummaryAlignment();
  }

  function renderDailyTable(rows) {
    const tbody = document.querySelector('[data-summary-table="fuel-daily"] tbody');
    if (!tbody) return;

    tbody.innerHTML = rows
      .map(
        (row) => `
          <tr>
            <td class="summary-day-cell">${row.day}</td>
            <td class="summary-number-cell">${row.values[0] ?? ""}</td>
            <td class="summary-number-cell">${row.values[1] ?? ""}</td>
            <td class="summary-number-cell">${row.values[2] ?? ""}</td>
            <td class="summary-number-cell">${row.values[3] ?? ""}</td>
            <td class="summary-number-cell">${row.values[4] ?? ""}</td>
            <td class="summary-number-cell">${row.values[5] ?? ""}</td>
          </tr>
        `
      )
      .join("");
    applySummaryAlignment();
  }

  function applySummaryAlignment() {
    document.querySelectorAll(".summary-ref-table th").forEach((cell) => {
      cell.classList.remove("unit-align-number");
      cell.classList.add("unit-align-text");
    });
    document.querySelectorAll(".summary-label-cell, .summary-unit-cell, .summary-day-cell").forEach((cell) => {
      cell.classList.remove("unit-align-number");
      cell.classList.add("unit-align-text");
    });
    document.querySelectorAll(".summary-number-cell").forEach((cell) => {
      cell.classList.remove("unit-align-text");
      cell.classList.add("unit-align-number");
    });
  }

  async function ensureLoaded() {
    const year = String(yearSelect.value || "");
    if (loaded && loadedYear === year) return;
    const range = year ? { fromMonth: `${year}-01`, toMonth: `${year}-12` } : {};
    const [cells, units, raws] = await Promise.all([
      fetchPaged(CELL_TABLE, range, { tableNames: CELL_TABLE_NAMES.join(",") }),
      fetchPaged(UNIT_TABLE, range),
      fetchPaged(RAW_TABLE, range),
    ]);
    cellRows.length = 0;
    unitRows.length = 0;
    rawRows.length = 0;
    cellRows.push(...cells);
    unitRows.push(...units);
    rawRows.push(...raws);
    rebuildIndexes();
    loaded = true;
    loadedYear = year;
  }

  async function render() {
    await ensureLoaded();
    const totals = monthTotals();
    renderAllocationTable("steam-allocation", STEAM_ROWS, buildSteamAllocationRows(totals));
    renderAllocationTable("fuel-allocation", FUEL_ROWS, buildFuelAllocationRows(totals));
    renderDailyTable(buildDailyFuelRows());
  }

  submitButton?.addEventListener("click", render);
  render();
});
