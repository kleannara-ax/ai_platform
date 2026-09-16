document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  const pageConfig = {
    "combo-boiler-steam-production.html": { key: "production", title: "스팀생산실적" },
    "combo-boiler-downtime.html": { key: "downtime", title: "복합보일러 운휴 시간" },
    "combo-boiler-recovery-loss.html": { key: "recovery", title: "스팀 회수량 감소 요인" },
    "combo-boiler-etc.html": { key: "etc", title: "기타" },
  };
  const config = pageConfig[page];
  if (!config) return;

  const PERIOD_KEY = "steamlog:shared-period";
  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  const title = document.getElementById("comboPageTitle");
  const subtitle = document.getElementById("comboPageSubtitle");
  const tableWrap = document.getElementById("comboTableWrap");
  const tabRoot = document.querySelector(".combo-tabs");
  const pageHeader = document.querySelector(".landing-page-header");
  const sidebarGroups = Array.from(document.querySelectorAll(".sidebar-group-flat"));
  if (!yearSelect || !monthSelect || !submitButton || !tableWrap) return;

  const CELL_TABLE = "table_cell_value";
  const TABLES_BASE = "/tables";
  const state = {};
  let cellRows = [];
  let activeSection = "combo";
  let recoveryRowCount = 12;

  const productionSections = [
    { id: "combo", label: "복합보일러", groups: [["복합보일러", 7]], fields: ["comboSteam", "comboSteamHour", "comboCondensate", "comboMakeup", "comboDeaerator", "comboProcess", "comboDilution"] },
    { id: "external", label: "외부보일러", groups: [["외부보일러", 4]], fields: ["externalSteam", "externalSteamHour", "externalCondensate", "externalMakeup"] },
    { id: "waste", label: "폐합성 소각로", groups: [["폐합성 소각로", 4]], fields: ["wasteSteam1", "wasteSteam1Hour", "wasteSteam2", "wasteSteam2Hour"] },
    { id: "kn-fluidized", label: "깨끗한나라/유동상", groups: [["깨끗한나라 보일러", 2], ["유동상", 2]], fields: ["kn20", "kn50", "fluidizedSteam", "fluidizedCondensate"] },
    { id: "total", label: "합계", groups: [["스팀총계", 2], ["당사 보일러 가동시간(H/D)", 3]], fields: ["totalSteam", "totalSteamHour", "runtime20", "runtime50", "runtimeFluidized"] },
  ];

  const productionFields = {
    comboSteam: ["스팀구매량(T/D)", "input"],
    comboSteamHour: ["스팀구매량(T/H)", "calc"],
    comboCondensate: ["응축수판매량", "input"],
    comboMakeup: ["보충수판매량", "input"],
    comboDeaerator: ["탈기기급수량", "input"],
    comboProcess: ["공정수판매량", "input"],
    comboDilution: ["희석수판매량", "input"],
    externalSteam: ["스팀구매량(T/D)", "input"],
    externalSteamHour: ["스팀구매량(T/H)", "calc"],
    externalCondensate: ["응축수판매량", "input"],
    externalMakeup: ["보충수판매량", "input"],
    wasteSteam1: ["1호 스팀 구입량(T/D)", "input"],
    wasteSteam1Hour: ["1호 스팀 구입량(T/H)", "calc"],
    wasteSteam2: ["2호 스팀 구입량(T/D)", "input"],
    wasteSteam2Hour: ["2호 스팀 구입량(T/H)", "calc"],
    kn20: ["20톤(#2)", "input"],
    kn50: ["50톤(#3)", "input"],
    fluidizedSteam: ["스팀 생산량", "input"],
    fluidizedCondensate: ["응축수", "input"],
    totalSteam: ["스팀총계(T/D)", "calc"],
    totalSteamHour: ["스팀총계(T/H)", "calc"],
    runtime20: ["20톤(#2) 가동시간", "input"],
    runtime50: ["50톤(#3) 가동시간", "input"],
    runtimeFluidized: ["유동상 가동시간", "input"],
  };

  const sectionConfig = {
    production: productionSections,
    downtime: [
      { id: "changes", label: "변동사항" },
      { id: "runtime", label: "운휴시간" },
      { id: "disperser", label: "디스퍼져/회수량" },
    ],
    recovery: [
      { id: "recovery-main", label: "감소요인" },
    ],
    etc: [
      { id: "combo-plan", label: "복합보일러" },
      { id: "jesco-plan", label: "제스코" },
      { id: "lng", label: "LNG사용량" },
    ],
  };

  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function parseNumber(value) {
    const text = String(value ?? "").replace(/,/g, "").trim();
    if (!text) return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function formatNumber(value, digits = 0) {
    if (value === null || value === undefined || !Number.isFinite(Number(value))) return "";
    return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: digits }).format(Number(value));
  }

  function daysInMonth(year, month) {
    return new Date(Number(year), Number(month), 0).getDate();
  }

  function saveVisibleInputs() {
    tableWrap.querySelectorAll("[data-key]").forEach((input) => {
      state[input.dataset.key] = input.value;
    });
  }

  function valueOf(key) {
    const live = tableWrap.querySelector(`[data-key="${CSS.escape(key)}"]`);
    return parseNumber(live ? live.value : state[key]);
  }

  function numberOf(key) {
    return valueOf(key) ?? 0;
  }

  function setCalc(key, value, digits = 0) {
    const cell = tableWrap.querySelector(`[data-calc="${CSS.escape(key)}"]`);
    if (cell) cell.textContent = formatNumber(value, digits);
  }

  function inputCell(key, className = "") {
    return `<td><input class="combo-input ${className}" type="text" data-key="${escapeHtml(key)}" value="${escapeHtml(state[key] || "")}" /></td>`;
  }

  function calcCell(key) {
    return `<td class="combo-readonly" data-calc="${escapeHtml(key)}"></td>`;
  }

  function loadSavedPeriod() {
    const now = new Date();
    let year = String(now.getFullYear());
    let month = String(now.getMonth() + 1).padStart(2, "0");
    try {
      const parsed = JSON.parse(localStorage.getItem(PERIOD_KEY) || "{}");
      if (/^\d{4}$/.test(String(parsed.year || ""))) year = String(parsed.year);
      if (/^(0[1-9]|1[0-2])$/.test(String(parsed.month || ""))) month = String(parsed.month);
    } catch (_) {}
    return { year, month };
  }

  function persistPeriod() {
    localStorage.setItem(PERIOD_KEY, JSON.stringify({ year: yearSelect.value, month: monthSelect.value }));
  }

  function currentMonthKey() {
    if (config.key === "etc") return `${yearSelect.value}-01`;
    return `${yearSelect.value}-${monthSelect.value}`;
  }

  function pageTableName() {
    return `combo_boiler_${config.key}`;
  }

  function normalizeEditableValue(value) {
    return String(value ?? "").replace(/,/g, "").trim();
  }

  function applyCellRowsToState() {
    Object.keys(state).forEach((key) => delete state[key]);
    const tableName = pageTableName();
    const monthKey = currentMonthKey();
    cellRows
      .filter((row) => row.table_name === tableName && row.month === monthKey)
      .forEach((row) => {
        state[String(row.row_key)] = String(row.cell_value ?? "");
      });
    recoveryRowCount = Math.max(12, maxRecoveryRowIndex());
  }

  function maxRecoveryRowIndex() {
    return Object.keys(state).reduce((max, key) => {
      const match = key.match(/^loss(?:Date|Time|Note|SteamRate|BaseRate|Minutes)-(\d+)$/);
      return match ? Math.max(max, Number(match[1])) : max;
    }, 0);
  }

  async function fetchCellRows() {
    const response = await fetch(`${TABLES_BASE}/${CELL_TABLE}?page=1&limit=5000&month=${encodeURIComponent(currentMonthKey())}`);
    if (!response.ok) throw new Error("Failed to load combo boiler rows");
    const body = await response.json();
    cellRows = Array.isArray(body.data) ? body.data : [];
    applyCellRowsToState();
  }

  function getCellRow(rowKey) {
    const tableName = pageTableName();
    const monthKey = currentMonthKey();
    return cellRows.find((row) =>
      row.table_name === tableName &&
      row.month === monthKey &&
      String(row.row_key) === String(rowKey) &&
      Number(row.col_index) === 0
    ) || null;
  }

  function cacheSavedRow(saved) {
    if (!saved || !saved.id) return;
    const index = cellRows.findIndex((row) => Number(row.id) === Number(saved.id));
    if (index >= 0) {
      cellRows[index] = saved;
    } else {
      cellRows.push(saved);
    }
  }

  async function upsertInput(input) {
    const rowKey = input?.dataset?.key;
    if (!rowKey) return;
    const rawValue = normalizeEditableValue(input.value);
    state[rowKey] = rawValue;
    const existing = getCellRow(rowKey);
    const payload = {
      month: currentMonthKey(),
      year_no: Number(yearSelect.value),
      month_no: Number(monthSelect.value),
      table_name: pageTableName(),
      row_key: rowKey,
      col_index: 0,
      cell_value: rawValue,
    };
    const response = await fetch(`${TABLES_BASE}/${CELL_TABLE}${existing ? `/${existing.id}` : ""}`, {
      method: existing ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error("Failed to save combo boiler cell");
    cacheSavedRow(await response.json());
  }

  function populatePeriod() {
    const saved = loadSavedPeriod();
    const currentYear = new Date().getFullYear();
    for (let year = currentYear + 1; year >= currentYear - 5; year -= 1) {
      const option = document.createElement("option");
      option.value = String(year);
      option.textContent = String(year);
      option.selected = option.value === saved.year;
      yearSelect.appendChild(option);
    }
    for (let month = 1; month <= 12; month += 1) {
      const option = document.createElement("option");
      option.value = String(month).padStart(2, "0");
      option.textContent = `${month}월`;
      option.selected = option.value === saved.month;
      monthSelect.appendChild(option);
    }
    if (config.key === "etc") {
      monthSelect.closest(".landing-filter-box")?.classList.add("combo-year-only-filter");
      monthSelect.style.display = "none";
      monthSelect.previousElementSibling?.classList.add("combo-hidden-period-part");
    }
  }

  function bindSidebar() {
    if (window.CommonSidebar?.isManaged?.()) return;
    sidebarGroups.forEach((group) => {
      const toggle = group.querySelector(".sidebar-parent-toggle");
      const stateName = group.dataset.sidebarState || toggle?.dataset.sidebarState;
      if (!toggle || !stateName) return;
      const key = `steamlog:sidebar:${stateName}`;
      const isOpen = stateName === "combo-boiler-log" || localStorage.getItem(key) === "1" || group.classList.contains("is-open");
      group.classList.toggle("is-open", isOpen);
      toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
      toggle.addEventListener("click", () => {
        const nextOpen = group.classList.toggle("is-open");
        toggle.setAttribute("aria-expanded", nextOpen ? "true" : "false");
        localStorage.setItem(key, nextOpen ? "1" : "0");
      });
    });
  }

  function bindExcelControls() {
    if (!pageHeader || document.getElementById("comboExcelDownload")) return;
    const box = document.createElement("div");
    box.className = "landing-filter-box landing-upload-box";
    box.innerHTML = `
      <span class="landing-filter-title">\uc5d1\uc140</span>
      <button type="button" class="landing-filter-action" id="comboExcelDownload">\ub2e4\uc6b4\ub85c\ub4dc</button>
      <button type="button" class="landing-filter-action" id="comboExcelUpload">\uc5c5\ub85c\ub4dc</button>
      <input type="file" id="comboExcelFile" accept=".xlsx" style="display:none" />
    `;
    pageHeader.appendChild(box);
    const downloadButton = box.querySelector("#comboExcelDownload");
    const uploadButton = box.querySelector("#comboExcelUpload");
    const fileInput = box.querySelector("#comboExcelFile");
    downloadButton?.addEventListener("click", downloadExcel);
    uploadButton?.addEventListener("click", () => fileInput?.click());
    fileInput?.addEventListener("change", async () => {
      const file = fileInput.files?.[0];
      if (!file) return;
      await uploadExcel(file);
      fileInput.value = "";
    });
  }

  async function downloadExcel() {
    const response = await fetch(`/tables/combo-boiler/export-excel?year=${encodeURIComponent(yearSelect.value)}`);
    if (!response.ok) {
      alert("\uc5d1\uc140 \ub2e4\uc6b4\ub85c\ub4dc\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4.");
      return;
    }
    const blob = await response.blob();
    const anchor = document.createElement("a");
    anchor.href = URL.createObjectURL(blob);
    anchor.download = `3. \ubcf5\ud569\ubcf4\uc77c\ub7ec(${yearSelect.value}\ub144) \uc2a4\ud300\uad6c\ub9e4 \ud604\ud669.xlsx`;
    document.body.appendChild(anchor);
    anchor.click();
    anchor.remove();
    URL.revokeObjectURL(anchor.href);
  }

  async function uploadExcel(file) {
    const uploadButton = document.getElementById("comboExcelUpload");
    const originalText = uploadButton?.textContent;
    if (uploadButton) {
      uploadButton.disabled = true;
      uploadButton.textContent = "\uc5c5\ub85c\ub4dc \uc911";
    }
    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("year", yearSelect.value);
      const response = await fetch("/tables/combo-boiler/import-excel", { method: "POST", body: formData });
      if (!response.ok) throw new Error(await window.ExcelUploadErrors?.message(response) || "\uc5d1\uc140 \uc5c5\ub85c\ub4dc\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4.");
      await reload();
    } catch (error) {
      console.error(error);
      alert(error instanceof Error ? error.message : "\uc5d1\uc140 \uc5c5\ub85c\ub4dc\uc5d0 \uc2e4\ud328\ud588\uc2b5\ub2c8\ub2e4.");
    } finally {
      if (uploadButton) {
        uploadButton.disabled = false;
        uploadButton.textContent = originalText || "\uc5c5\ub85c\ub4dc";
      }
    }
  }

  function renderSectionButtons() {
    if (!tabRoot) return;
    const sections = sectionConfig[config.key] || [];
    if (!sections.some((section) => section.id === activeSection)) activeSection = sections[0]?.id || "";
    // 섹션이 1개뿐이면 sub-tab 자체를 표시하지 않음 (중복된 라벨 제거)
    if (sections.length <= 1) {
      tabRoot.innerHTML = "";
      tabRoot.style.display = "none";
      return;
    }
    tabRoot.style.display = "";
    tabRoot.innerHTML = sections.map((section) => `
      <button type="button" class="period-tab-button ${section.id === activeSection ? "active" : ""}" data-section-target="${section.id}">
        ${escapeHtml(section.label)}
      </button>
    `).join("");
  }

  function renderProductionTotalExtra(section) {
    if (section.id !== "total") return "";
    const fuelHeader = `${Number(monthSelect.value)}\uc6d4 \uc5f0\ub8cc\ub2e8\uac00`;
    const rows = [
      ["\u004c\u004e\u0047 \ubcf4\uc77c\ub7ec", "production-unit-lng-boiler", "fuelPrice-lngBoiler"],
      ["\uc720\ub3d9\uc0c1\uc18c\uac01\ub85c", "production-unit-fluidized", "fuelPrice-fluidized"],
      ["\ubcf5\ud569\ubcf4\uc77c\ub7ec", "production-unit-combo", "fuelPrice-combo"],
      ["\ud3d0\ud569\uc131\uc18c\uac01\ub85c", "production-unit-waste", "fuelPrice-waste"],
      ["\uc678\ubd80\ubcf4\uc77c\ub7ec", "production-unit-external", "fuelPrice-external"],
    ];
    return `
      <table class="combo-excel-table combo-production-extra-table">
        <thead>
          <tr>
            <th>\uad6c&nbsp;&nbsp;\ubd84</th>
            <th>\uc2a4\ud300 \uc6d0\ub2e8\uc704 (\ud1a4/hr)</th>
            <th>${escapeHtml(fuelHeader)}</th>
          </tr>
        </thead>
        <tbody>
          ${rows.map(([label, unitKey, priceKey]) => `<tr><th>${label}</th>${calcCell(unitKey)}${inputCell(priceKey)}</tr>`).join("")}
          <tr class="combo-summary-row"><th>\ud569&nbsp;&nbsp;&nbsp;&nbsp;\uacc4</th>${calcCell("production-unit-total")}<td></td></tr>
        </tbody>
      </table>
    `;
  }

  function renderProduction() {
    const days = daysInMonth(yearSelect.value, monthSelect.value);
    const section = productionSections.find((item) => item.id === activeSection) || productionSections[0];
    const rows = [];
    for (let day = 1; day <= days; day += 1) {
      rows.push(`
        <tr data-day="${day}">
          <th scope="row">${day}</th>
          ${section.fields.map((field) => {
            const [label, type] = productionFields[field];
            return type === "calc" ? calcCell(`${field}-${day}`) : inputCell(`${field}-${day}`);
          }).join("")}
        </tr>
      `);
    }
    rows.push(summaryRow(section.fields, "sum", "누계"));
    rows.push(summaryRow(section.fields, "avg", "평균"));
    const colWidths = productionColumnWidths(section);
    tableWrap.innerHTML = `
      <table class="combo-excel-table combo-production-table combo-production-${escapeHtml(section.id)}">
        <colgroup>
          <col class="combo-day-col" />
          ${section.fields.map((field, index) => `<col class="combo-field-col combo-field-${escapeHtml(field)}" style="width:${escapeHtml(colWidths[index] || "1fr")};" />`).join("")}
        </colgroup>
        <thead>
          <tr class="combo-section-title-row">
            <th></th>
            <th colspan="${section.fields.length}">${escapeHtml(section.label)}</th>
          </tr>
          <tr class="combo-section-group-row">
            <th>일자</th>
            ${(section.groups || [[section.label, section.fields.length]]).map(([label, span]) => `<th colspan="${span}">${escapeHtml(label)}</th>`).join("")}
          </tr>
          <tr><th></th>${section.fields.map((field) => `<th>${escapeHtml(productionFields[field][0])}</th>`).join("")}</tr>
        </thead>
        <tbody>${rows.join("")}</tbody>
      </table>
      ${renderProductionTotalExtra(section)}
    `;
  }

  function productionColumnWidths(section) {
    const widthMap = {
      combo: ["11%", "10%", "15%", "15%", "15%", "17%", "17%"],
      external: ["25%", "18%", "28%", "29%"],
      waste: ["25%", "22%", "28%", "25%"],
      "kn-fluidized": ["18%", "18%", "32%", "32%"],
      total: ["18%", "16%", "22%", "22%", "22%"],
    };
    return widthMap[section.id] || section.fields.map(() => `${100 / section.fields.length}%`);
  }

  function summaryRow(fields, type, label) {
    return `<tr class="combo-summary-row"><th scope="row">${label}</th>${fields.map((field) => calcCell(`${type}-${field}`)).join("")}</tr>`;
  }

  function dailyAverage(field, days) {
    let total = 0;
    for (let day = 1; day <= days; day += 1) total += numberOf(`${field}-${day}`);
    return days ? total / days : 0;
  }

  function renderDowntime() {
    const days = daysInMonth(yearSelect.value, monthSelect.value);
    const columns = {
      changes: [
        ["changeCombo", "변동사항(복합보일러)", "text"],
        ["changeJesco", "변동사항(제스코, 유동상 보일러)", "text"],
      ],
      runtime: [
        ["down1", "#1호기 운휴시간(분)", "input"],
        ["down2", "#2호기 운휴시간(분)", "input"],
        ["sumDown", "운휴시간 합계(H)", "calc"],
      ],
      disperser: [
        ["disperserSteam", "디스퍼져 스팀량", "input"],
        ["disperserSteamHour", "시간당 스팀량", "calc"],
        ["runtimeMin", "가동시간(분)", "input"],
        // '스팀 회수 감소량' 컬럼 제거: 원본 엑셀에 없는 임의 수식(스팀량−가동분, 차원 불일치)이었음.
      ],
    }[activeSection] || [];
    tableWrap.innerHTML = `
      <table class="combo-excel-table">
        <thead><tr><th>일자</th>${columns.map(([_key, label]) => `<th>${escapeHtml(label)}</th>`).join("")}</tr></thead>
        <tbody>
          ${Array.from({ length: days }, (_, index) => {
            const day = index + 1;
            return `<tr><th>${day}</th>${columns.map(([key, _label, type]) => type === "calc" ? calcCell(`${key}-${day}`) : inputCell(`${key}-${day}`, type === "text" ? "combo-text-input" : "")).join("")}</tr>`;
          }).join("")}
          <tr class="combo-summary-row"><th>월누계</th>${columns.map(([key, _label, type]) => type === "text" ? "<td></td>" : calcCell(`sum-${key}`)).join("")}</tr>
        </tbody>
      </table>
    `;
  }

  function renderRecovery() {
    const rows = Array.from({ length: recoveryRowCount }, (_, index) => {
      const row = index + 1;
      return `<tr><th>${row}</th>${inputCell(`lossDate-${row}`, "combo-text-input")}${inputCell(`lossTime-${row}`, "combo-text-input")}${inputCell(`lossNote-${row}`, "combo-text-input combo-wide-input")}${inputCell(`lossSteamRate-${row}`)}${inputCell(`lossBaseRate-${row}`)}${inputCell(`lossMinutes-${row}`)}${calcCell(`lossTonHour-${row}`)}${calcCell(`lossTonMinute-${row}`)}${calcCell(`lossAmount-${row}`)}</tr>`;
    }).join("");

    tableWrap.innerHTML = `
      <div class="combo-table-actions">
        <button type="button" class="landing-filter-action combo-add-row-button" id="comboRecoveryAddRow">항목 추가</button>
      </div>
      <table class="combo-excel-table combo-recovery-table">
        <colgroup>
          <col class="combo-recovery-no-col" />
          <col class="combo-recovery-date-col" />
          <col class="combo-recovery-time-col" />
          <col class="combo-recovery-note-col" />
          <col class="combo-recovery-small-col" />
          <col class="combo-recovery-small-col" />
          <col class="combo-recovery-small-col" />
          <col class="combo-recovery-small-col" />
          <col class="combo-recovery-small-col" />
          <col class="combo-recovery-small-col" />
        </colgroup>
        <thead>
          <tr>
            <th rowspan="2">No.</th>
            <th rowspan="2">날짜</th>
            <th rowspan="2">스팀회수 감소시간</th>
            <th rowspan="2">내용</th>
            <th colspan="2">스팀 감소량 기준</th>
            <th rowspan="2">감소시간(min)</th>
            <th rowspan="2">톤/hr</th>
            <th rowspan="2">톤/min</th>
            <th rowspan="2">스팀 감소량</th>
          </tr>
          <tr>
            <th>T/H</th>
            <th>기준</th>
          </tr>
        </thead>
        <tbody>
          ${rows}
          <tr class="combo-summary-row"><th>합계</th><td colspan="6"></td>${calcCell("loss-total-ton-hour")}${calcCell("loss-total-ton-minute")}${calcCell("loss-total-amount")}</tr>
        </tbody>
      </table>
    `;
  }
  function renderEtc() {
    if (activeSection === "lng") {
      tableWrap.innerHTML = `
        <table class="combo-excel-table">
          <thead><tr><th>구분</th>${monthHeaders()}<th>계</th><th>진척률</th></tr></thead>
          <tbody>
            <tr><th>보일러</th>${monthInputs("lngBoiler")}${calcCell("lng-boiler-total")}${calcCell("lng-boiler-progress")}</tr>
            <tr><th>복합</th>${monthInputs("lngCombo")}${calcCell("lng-combo-total")}${calcCell("lng-combo-progress")}</tr>
            <tr class="combo-summary-row"><th>계</th>${Array.from({ length: 12 }, (_, index) => calcCell(`lng-total-${index + 1}`)).join("")}${calcCell("lng-grand-total")}${calcCell("lng-grand-progress")}</tr>
          </tbody>
        </table>
      `;
      return;
    }
    const isJesco = activeSection === "jesco-plan";
    const headers = isJesco ? relativeMonthHeaders(-1, 5, 13) : relativeMonthHeaders(-1, 6, 15);
    tableWrap.innerHTML = planTable(
      activeSection,
      isJesco ? "제스코" : "복합보일러",
      isJesco
        ? ["계획", "실적", "잔량"]
        : [
            { label: "기본", keyIndex: 1 },
            { label: "특약", keyIndex: 2 },
            { label: "소계", keyIndex: 3 },
            { label: "실적", keyIndex: 4 },
            { label: "잔량", keyIndex: 5 }
          ],
      headers
    );
  }
  function monthHeaders() {
    return Array.from({ length: 12 }, (_, index) => `<th>${index + 1}월</th>`).join("");
  }

  function relativeMonthHeaders(startYearOffset, startMonth, count) {
    const baseYear = Number(yearSelect.value) || new Date().getFullYear();
    let year = baseYear + startYearOffset;
    let month = startMonth;
    return Array.from({ length: count }, () => {
      const label = `${String(year).slice(2)}.${month}`;
      month += 1;
      if (month > 12) {
        month = 1;
        year += 1;
      }
      return label;
    });
  }

  function monthInputs(prefix) {
    return Array.from({ length: 12 }, (_, index) => inputCell(`${prefix}-${index + 1}`)).join("");
  }

  function planTable(prefix, heading, rowLabels, headers) {
    const isGroupedComboPlan = prefix === "combo-plan";
    const monthCount = headers.length;
    const subtotalLabel = "소계";
    const remainingLabel = "잔량";
    const planLabel = "계획";
    const leftColumnCount = isGroupedComboPlan ? 2 : 1;
    const headerColumns = isGroupedComboPlan ? "<th>구분</th><th>항목</th>" : "<th>구분</th>";
    return `
      <table class="combo-excel-table" data-plan-table="${prefix}">
        <thead>
          <tr class="combo-section-title-row"><th colspan="${monthCount + leftColumnCount + 1}">${escapeHtml(heading)}</th></tr>
          <tr>${headerColumns}${headers.map((label) => `<th>${escapeHtml(label)}</th>`).join("")}<th>계</th></tr>
        </thead>
        <tbody>
          ${rowLabels.map((rowDef, visualIndex) => {
            const label = typeof rowDef === "string" ? rowDef : rowDef.label;
            const rowKey = typeof rowDef === "string" ? visualIndex : rowDef.keyIndex;
            const summaryRow = [subtotalLabel, remainingLabel].includes(label);
            const leftCells = isGroupedComboPlan
              ? `${visualIndex === 0 ? `<th rowspan="3" class="combo-plan-group-cell">${planLabel}</th>` : ""}<th ${visualIndex > 2 ? 'colspan="2"' : 'class="combo-plan-item-cell"'}>${escapeHtml(label)}</th>`
              : `<th>${escapeHtml(label)}</th>`;
            // 소계 / 잔량 행 은 계산셀 (readonly) — 사용자 흰 입력칸을 두지 않음.
            const cellFn = summaryRow ? calcCell : inputCell;
            return `<tr data-plan-row="${rowKey}" data-plan-label="${escapeHtml(label)}" ${summaryRow ? 'class="combo-summary-row"' : ""}>${leftCells}${Array.from({ length: monthCount }, (_, monthIndex) => cellFn(`${prefix}-${rowKey}-${monthIndex}`)).join("")}${calcCell(`${prefix}-${rowKey}-total`)}</tr>`;
          }).join("")}
        </tbody>
      </table>
    `;
  }
  function calculateProduction() {
    const days = daysInMonth(yearSelect.value, monthSelect.value);
    Object.keys(productionFields).forEach((field) => {
      let sum = 0;
      for (let day = 1; day <= days; day += 1) {
        if (field === "comboSteamHour") setCalc(`${field}-${day}`, numberOf(`comboSteam-${day}`) / 24, 2);
        if (field === "externalSteamHour") setCalc(`${field}-${day}`, numberOf(`externalSteam-${day}`) / 24, 2);
        if (field === "wasteSteam1Hour") setCalc(`${field}-${day}`, numberOf(`wasteSteam1-${day}`) / 24, 2);
        if (field === "wasteSteam2Hour") setCalc(`${field}-${day}`, numberOf(`wasteSteam2-${day}`) / 24, 2);
        if (field === "totalSteam") {
          const total = numberOf(`comboSteam-${day}`) + numberOf(`externalSteam-${day}`) + numberOf(`wasteSteam1-${day}`) + numberOf(`wasteSteam2-${day}`) + numberOf(`kn20-${day}`) + numberOf(`kn50-${day}`) + numberOf(`fluidizedSteam-${day}`);
          setCalc(`${field}-${day}`, total, 2);
        }
        if (field === "totalSteamHour") {
          const total = numberOf(`comboSteam-${day}`) + numberOf(`externalSteam-${day}`) + numberOf(`wasteSteam1-${day}`) + numberOf(`wasteSteam2-${day}`) + numberOf(`kn20-${day}`) + numberOf(`kn50-${day}`) + numberOf(`fluidizedSteam-${day}`);
          setCalc(`${field}-${day}`, total / 24, 2);
        }
        const liveCalc = parseNumber(tableWrap.querySelector(`[data-calc="${CSS.escape(`${field}-${day}`)}"]`)?.textContent);
        sum += liveCalc ?? numberOf(`${field}-${day}`);
      }
      setCalc(`sum-${field}`, sum, 2);
      setCalc(`avg-${field}`, sum / days, 2);
    });
    // 스팀 원단위(톤/hr): Excel D42=(Q39+R39)/24=스팀생산량(20톤+50톤)/24, D43=S39/24=유동상 스팀생산량/24.
    // (가동시간 runtime* 이 아니라 스팀생산량 kn20/kn50/fluidizedSteam 사용)
    const lngUnit = (dailyAverage("kn20", days) + dailyAverage("kn50", days)) / 24;
    const fluidizedUnit = dailyAverage("fluidizedSteam", days) / 24;
    const comboUnit = dailyAverage("comboSteam", days) / 24;
    const wasteUnit = (dailyAverage("wasteSteam1", days) + dailyAverage("wasteSteam2", days)) / 24;
    const externalUnit = dailyAverage("externalSteam", days) / 24;
    setCalc("production-unit-lng-boiler", lngUnit, 2);
    setCalc("production-unit-fluidized", fluidizedUnit, 2);
    setCalc("production-unit-combo", comboUnit, 2);
    setCalc("production-unit-waste", wasteUnit, 2);
    setCalc("production-unit-external", externalUnit, 2);
    setCalc("production-unit-total", lngUnit + fluidizedUnit + comboUnit + wasteUnit + externalUnit, 2);
  }

  function calculateDowntime() {
    const days = daysInMonth(yearSelect.value, monthSelect.value);
    let down1 = 0;
    let down2 = 0;
    let disperser = 0;
    let runtime = 0;
    for (let day = 1; day <= days; day += 1) {
      const d1 = numberOf(`down1-${day}`);
      const d2 = numberOf(`down2-${day}`);
      const steam = numberOf(`disperserSteam-${day}`);
      const runtimeMin = numberOf(`runtimeMin-${day}`);
      down1 += d1;
      down2 += d2;
      disperser += steam;
      runtime += runtimeMin;
      setCalc(`sumDown-${day}`, (d1 + d2) / 60, 2);
      setCalc(`disperserSteamHour-${day}`, steam / 24, 2);
    }
    setCalc("sum-down1", down1 / 60, 2);
    setCalc("sum-down2", down2 / 60, 2);
    setCalc("sum-sumDown", (down1 + down2) / 60, 2);
    setCalc("sum-disperserSteam", disperser, 2);
    setCalc("sum-disperserSteamHour", disperser / 24, 2);
    setCalc("sum-runtimeMin", runtime / 60, 2);
  }

  function calculateRecovery() {
    let totalHour = 0;
    let totalMinute = 0;
    let totalAmount = 0;
    for (let row = 1; row <= recoveryRowCount; row += 1) {
      const hour = numberOf(`lossSteamRate-${row}`) - numberOf(`lossBaseRate-${row}`);
      const minute = hour / 60;
      const amount = numberOf(`lossMinutes-${row}`) * minute;
      totalHour += hour;
      totalMinute += minute;
      totalAmount += amount;
      setCalc(`lossTonHour-${row}`, hour, 2);
      setCalc(`lossTonMinute-${row}`, minute, 4);
      setCalc(`lossAmount-${row}`, amount, 2);
    }
    setCalc("loss-total-ton-hour", totalHour, 2);
    setCalc("loss-total-ton-minute", totalMinute, 4);
    setCalc("loss-total-amount", totalAmount, 2);
  }

  function calculateEtc() {
    calculatePlanTable(activeSection);
    let grand = 0;
    for (let month = 1; month <= 12; month += 1) {
      const total = numberOf(`lngBoiler-${month}`) + numberOf(`lngCombo-${month}`);
      grand += total;
      setCalc(`lng-total-${month}`, total, 2);
    }
    const boiler = Array.from({ length: 12 }, (_, i) => numberOf(`lngBoiler-${i + 1}`)).reduce((a, b) => a + b, 0);
    const combo = Array.from({ length: 12 }, (_, i) => numberOf(`lngCombo-${i + 1}`)).reduce((a, b) => a + b, 0);
    setCalc("lng-boiler-total", boiler, 2);
    setCalc("lng-combo-total", combo, 2);
    setCalc("lng-grand-total", grand, 2);
    setCalc("lng-boiler-progress", grand ? boiler / grand : null, 4);
    setCalc("lng-combo-progress", grand ? combo / grand : null, 4);
    setCalc("lng-grand-progress", grand ? 1 : null, 4);
  }

  function calculatePlanTable(prefix) {
    const root = tableWrap.querySelector(`[data-plan-table="${CSS.escape(prefix)}"]`);
    if (!root) return;
    const rows = Array.from(root.querySelectorAll("[data-plan-row]"));
    const labelKey = (targetLabel) => {
      const row = rows.find((item) => (item.dataset.planLabel || "") === targetLabel);
      return row ? Number(row.dataset.planRow || 0) : -1;
    };
    const basicIndex = labelKey("기본");
    const specialIndex = labelKey("특약");
    const subtotalIndex = labelKey("소계");
    const planIndex = labelKey("계획");
    const actualIndex = labelKey("실적");
    // 계획 값 (월별) — jesco 는 '계획' 행 그대로 사용,
    //                  복합 (기본/특약 분리) 은 기본+특약 합으로 도출.
    function planValueAt(monthIndex) {
      if (planIndex >= 0) return numberOf(`${prefix}-${planIndex}-${monthIndex}`);
      return numberOf(`${prefix}-${basicIndex}-${monthIndex}`) + numberOf(`${prefix}-${specialIndex}-${monthIndex}`);
    }
    rows.forEach((row) => {
      const rowIndex = Number(row.dataset.planRow || 0);
      const label = row.dataset.planLabel || "";
      const valueCells = Array.from(row.querySelectorAll("[data-key], [data-calc]")).slice(0, -1);
      valueCells.forEach((_cell, monthIndex) => {
        if (label === "소계") {
          // 소계 = 기본 + 특약
          setCalc(`${prefix}-${rowIndex}-${monthIndex}`, numberOf(`${prefix}-${basicIndex}-${monthIndex}`) + numberOf(`${prefix}-${specialIndex}-${monthIndex}`), 2);
        }
        if (label === "잔량") {
          // 잔량 = 실적 − 계획 (jesco: '계획' 행 / 복합: 기본+특약)
          setCalc(`${prefix}-${rowIndex}-${monthIndex}`, numberOf(`${prefix}-${actualIndex}-${monthIndex}`) - planValueAt(monthIndex), 2);
        }
      });
      const total = valueCells
        .map((cell) => cell.matches("[data-key]") ? parseNumber(cell.value) : parseNumber(cell.textContent))
        .filter((value) => Number.isFinite(value))
        .reduce((acc, value) => acc + value, 0);
      setCalc(`${prefix}-${rowIndex}-total`, total, 2);
    });
  }
  function calculate() {
    saveVisibleInputs();
    if (config.key === "production") calculateProduction();
    if (config.key === "downtime") calculateDowntime();
    if (config.key === "recovery") calculateRecovery();
    if (config.key === "etc") calculateEtc();
  }

  function render() {
    if (title) title.textContent = config.title;
    if (subtitle) subtitle.textContent = "";
    renderSectionButtons();
    if (config.key === "production") renderProduction();
    if (config.key === "downtime") renderDowntime();
    if (config.key === "recovery") renderRecovery();
    if (config.key === "etc") renderEtc();
    calculate();
  }

  async function reload() {
    persistPeriod();
    await fetchCellRows();
    render();
  }

  populatePeriod();
  bindSidebar();
  bindExcelControls();
  reload().catch((error) => {
    console.error(error);
    render();
  });
  tabRoot?.addEventListener("click", (event) => {
    const button = event.target instanceof HTMLElement ? event.target.closest("[data-section-target]") : null;
    if (!button) return;
    saveVisibleInputs();
    activeSection = button.dataset.sectionTarget || activeSection;
    render();
  });
  tableWrap.addEventListener("click", (event) => {
    const button = event.target instanceof HTMLElement ? event.target.closest("#comboRecoveryAddRow") : null;
    if (!button) return;
    saveVisibleInputs();
    recoveryRowCount += 1;
    render();
  });
  tableWrap.addEventListener("input", calculate);
  tableWrap.addEventListener("change", (event) => {
    const input = event.target instanceof HTMLElement ? event.target.closest("[data-key]") : null;
    if (!(input instanceof HTMLInputElement)) return;
    upsertInput(input).catch((error) => {
      console.error(error);
      alert("저장에 실패했습니다.");
    });
  });
  yearSelect.addEventListener("change", persistPeriod);
  monthSelect.addEventListener("change", persistPeriod);
  submitButton.addEventListener("click", () => {
    reload().catch((error) => {
      console.error(error);
      alert("조회에 실패했습니다.");
    });
  });
});
