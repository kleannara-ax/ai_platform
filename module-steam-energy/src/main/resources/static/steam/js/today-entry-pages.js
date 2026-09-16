document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "today-entry.html") return;
  const group = document.querySelector(".sidebar-group-flat");
  const toggle = document.querySelector(".sidebar-parent-toggle");
  const sidebarStateKeyPrefix = "steamlog:sidebar:";

  function resolveSidebarStateKey(sidebarGroup, sidebarToggle) {
    const explicit = sidebarGroup?.dataset.sidebarState || sidebarToggle?.dataset.sidebarState;
    if (explicit) return `${sidebarStateKeyPrefix}${explicit}`;
    const label = sidebarToggle?.textContent?.trim().replace(/\s+/g, "-").toLowerCase() || "default";
    return `${sidebarStateKeyPrefix}${label}`;
  }

  if (group && toggle) {
    const key = resolveSidebarStateKey(group, toggle);
    const isOpen = localStorage.getItem(key) === "1";
    group.classList.toggle("is-open", isOpen);
    toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
    toggle.addEventListener("click", () => {
      const nextOpen = group.classList.toggle("is-open");
      toggle.setAttribute("aria-expanded", nextOpen ? "true" : "false");
      localStorage.setItem(key, nextOpen ? "1" : "0");
    });
  }

  const saveButton = document.getElementById("todayEntrySaveButton");
  const dateLabel = document.getElementById("todayEntryDateLabel");
  if (saveButton) saveButton.textContent = "저장";

  const unitRows = [];
  const rawRows = [];
  const cellRows = [];

  let today = null;
  let monthKey = "";
  let dayKey = "";
  let rowKey = "";
  const NUMERIC_INPUT_IDS = new Set([
    "pm2MainSteam", "pm2CoaterSteam", "pm2Production",
    "pm3MainSteam", "pm3CoaterSteam", "pm3DisperserSteam", "pm3VentSteam", "pm3Production",
    "tm3Steam", "tm3Production", "tm4Steam", "tm4Production", "tm5Steam", "tm5Production",
    "tocSteam", "pica121Vent",
    "usagePm2Runtime", "usagePm3McRuntime",
    "usageTm3Runtime", "usageTm4Runtime", "usageTm5Runtime",
    "lngBoiler1", "lngBoiler2", "lngBoiler3", "lngBoiler4",
    "lngBurnerPm3", "lngBurnerTm3", "lngBurnerTm4", "lngBurnerTm5",
    "lngMixPm2", "lngMixPm3", "lngMixTm3", "lngMixTm4", "lngMixTm5",
    "flowFluidAmount", "flowIncineratorAmount",
    "flowCombinedAmount", "flowCombinedDeduction",
    "flowBoilerAmount", "flowBoilerDeduction"
  ]);

  // 입력 대상 날짜 = 어제(한국시간 기준). 오늘 어제 데이터를 등록하는 페이지이므로.
  function getKstParts() {
    // KST 벽시계 시각을 UTC 필드로 표현하도록 +9h 이동 후 하루 빼서 어제 날짜를 구한다.
    const kst = new Date(Date.now() + 9 * 60 * 60 * 1000);
    kst.setUTCDate(kst.getUTCDate() - 1);
    const pad = (n) => String(n).padStart(2, "0");
    return {
      year: String(kst.getUTCFullYear()),
      month: pad(kst.getUTCMonth() + 1),
      day: pad(kst.getUTCDate()),
    };
  }

  function setSaveButtonLabel(label) {
    if (!saveButton) return;
    saveButton.textContent = label;
  }

  function updateTodayContext() {
    today = getKstParts();
    monthKey = `${today.year}-${today.month}`;
    dayKey = String(Number(today.day));
    rowKey = today.day;

    if (dateLabel) {
      dateLabel.textContent = `${today.year}.${today.month}.${today.day}`;
    }
  }

  function parseNumber(value) {
    const cleaned = String(value ?? "").replace(/,/g, "").trim();
    if (!cleaned) return null;
    const parsed = Number(cleaned);
    return Number.isFinite(parsed) ? parsed : null;
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

  function isNumericInputId(id) {
    return NUMERIC_INPUT_IDS.has(id);
  }

  function normalizeDayValue(day) {
    const numeric = Number(day);
    if (Number.isFinite(numeric) && numeric > 0) {
      return String(numeric);
    }
    return String(day ?? "").trim();
  }

  async function fetchMonthRows(tableName) {
    const rows = [];
    let pageNo = 1;
    let totalPages = 1;

    while (pageNo <= totalPages) {
      const response = await fetch(`tables/${tableName}?month=${encodeURIComponent(monthKey)}&page=${pageNo}&limit=500`);
      if (!response.ok) break;
      const payload = await response.json();
      const data = payload.data || [];
      const total = payload.total || data.length;
      const limit = payload.limit || 500;
      totalPages = Math.max(1, Math.ceil(total / limit));
      rows.push(...data);
      pageNo += 1;
    }

    return rows;
  }

  function findLastMatching(rows, predicate) {
    for (let index = rows.length - 1; index >= 0; index -= 1) {
      if (predicate(rows[index])) return rows[index];
    }
    return null;
  }

  function findPreferredDayRow(rows, extraPredicate = () => true) {
    const exact = findLastMatching(rows, (row) =>
      row.month === monthKey &&
      String(row.day) === dayKey &&
      extraPredicate(row)
    );
    if (exact) return exact;
    return findLastMatching(rows, (row) =>
      row.month === monthKey &&
      normalizeDayValue(row.day) === dayKey &&
      extraPredicate(row)
    );
  }

  function findUnit(machine) {
    return findPreferredDayRow(unitRows, (row) => row.machine_no === machine);
  }

  function findRaw() {
    return findPreferredDayRow(rawRows);
  }

  function findCell(tableName, colIndex) {
    return findLastMatching(cellRows, (row) =>
      row.month === monthKey &&
      row.table_name === tableName &&
      String(row.row_key) === rowKey &&
      Number(row.col_index) === colIndex
    );
  }

  function setInputValue(id, value) {
    const input = document.getElementById(id);
    if (!input) return;
    const nextValue = value ?? "";
    if (isNumericInputId(id)) {
      input.dataset.rawValue = normalizeEditableValue(nextValue);
    }
    input.value = isNumericInputId(id) ? formatEditableValue(nextValue) : nextValue;
  }

  function getInputValue(id) {
    const input = document.getElementById(id);
    if (!input) return "";
    const raw = input.value?.trim() || "";
    if (!isNumericInputId(id)) return raw;
    return document.activeElement === input ? normalizeEditableValue(raw) : (input.dataset.rawValue ?? normalizeEditableValue(raw));
  }

  function bindNumberFormatting() {
    NUMERIC_INPUT_IDS.forEach((id) => {
      const input = document.getElementById(id);
      if (!input || input.dataset.formatBound === "true") return;
      input.dataset.formatBound = "true";
      input.addEventListener("focus", () => {
        input.value = input.dataset.rawValue ?? normalizeEditableValue(input.value);
      });
      input.addEventListener("blur", () => {
        input.dataset.rawValue = normalizeEditableValue(input.value);
        input.value = formatEditableValue(input.value);
      });
      input.addEventListener("change", () => {
        input.dataset.rawValue = normalizeEditableValue(input.value);
        input.value = formatEditableValue(input.value);
      });
    });
  }

  async function upsert(tableName, payload, existingId) {
    const url = existingId ? `tables/${tableName}/${existingId}` : `tables/${tableName}`;
    const response = await fetch(url, {
      method: existingId ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(existingId ? payload : { table_name: tableName, ...payload }),
    });
    if (!response.ok) {
      throw new Error("저장에 실패했습니다.");
    }
    return response.json();
  }

  async function saveUnit(machine, fieldMap) {
    const existing = findUnit(machine);
    const payload = {
      month: monthKey,
      year_no: Number(today.year),
      month_no: Number(today.month),
      day: dayKey,
      machine_no: machine,
    };

    let hasValue = false;
    Object.entries(fieldMap).forEach(([field, config]) => {
      const rawValue = getInputValue(config.id);
      const value = config.type === "number" ? parseNumber(rawValue) : (rawValue || null);
      if (value !== null && value !== "") hasValue = true;
      payload[field] = value;
    });

    if (!existing && !hasValue) return;

    const saved = await upsert("unit_usage", payload, existing?.id);
    const index = unitRows.findIndex((row) => row.id === saved.id);
    if (index >= 0) unitRows[index] = saved;
    else unitRows.push(saved);
  }

  async function saveRaw(fieldMap) {
    const existing = findRaw();
    const payload = {
      month: monthKey,
      year_no: Number(today.year),
      month_no: Number(today.month),
      day: dayKey,
    };

    let hasValue = false;
    Object.entries(fieldMap).forEach(([field, id]) => {
      const value = parseNumber(getInputValue(id));
      if (value !== null) hasValue = true;
      payload[field] = value;
    });

    if (!existing && !hasValue) return;

    const saved = await upsert("unit_usage_raw", payload, existing?.id);
    const index = rawRows.findIndex((row) => row.id === saved.id);
    if (index >= 0) rawRows[index] = saved;
    else rawRows.push(saved);
  }

  async function saveCellValue(tableName, colIndex, value) {
    const existing = findCell(tableName, colIndex);
    const cellValue = (value === undefined || value === null || value === "") ? null : String(value);
    if (!existing && cellValue === null) return;

    const payload = {
      month: monthKey,
      year_no: Number(today.year),
      month_no: Number(today.month),
      table_name: tableName,
      row_key: rowKey,
      col_index: colIndex,
      cell_value: cellValue,
    };

    const saved = await upsert("table_cell_value", payload, existing?.id);
    const index = cellRows.findIndex((row) => row.id === saved.id);
    if (index >= 0) cellRows[index] = saved;
    else cellRows.push(saved);
  }

  function saveCell(tableName, colIndex, inputId) {
    return saveCellValue(tableName, colIndex, getInputValue(inputId) || null);
  }

  function fillForm() {
    document.querySelectorAll("input").forEach((input) => {
      input.value = "";
      delete input.dataset.rawValue;
    });
  }

  async function load() {
    saveButton.disabled = true;
    setSaveButtonLabel("불러오는 중");
    updateTodayContext();

    const [units, raws, cells] = await Promise.all([
      fetchMonthRows("unit_usage"),
      fetchMonthRows("unit_usage_raw"),
      fetchMonthRows("table_cell_value"),
    ]);

    unitRows.length = 0;
    rawRows.length = 0;
    cellRows.length = 0;
    unitRows.push(...units);
    rawRows.push(...raws);
    cellRows.push(...cells);
    fillForm();
    bindNumberFormatting();

    saveButton.disabled = false;
    setSaveButtonLabel("저장");
  }

  async function saveAll() {
    setSaveButtonLabel("저장 중");
    saveButton.disabled = true;
    try {
      updateTodayContext();

      await saveUnit("PM-2", {
        main_steam: { id: "pm2MainSteam", type: "number" },
        coater_steam: { id: "pm2CoaterSteam", type: "number" },
        production: { id: "pm2Production", type: "number" },
        production_type: { id: "pm2ProductionType", type: "string" },
      });

      await saveUnit("PM-3", {
        main_steam: { id: "pm3MainSteam", type: "number" },
        coater_steam: { id: "pm3CoaterSteam", type: "number" },
        ventilation_steam: { id: "pm3VentSteam", type: "number" },
        production: { id: "pm3Production", type: "number" },
        production_type: { id: "pm3ProductionType", type: "string" },
      });

      await saveUnit("TM-3", {
        steam: { id: "tm3Steam", type: "number" },
        production: { id: "tm3Production", type: "number" },
      });
      await saveUnit("TM-4", {
        steam: { id: "tm4Steam", type: "number" },
        production: { id: "tm4Production", type: "number" },
      });
      await saveUnit("TM-5", {
        steam: { id: "tm5Steam", type: "number" },
        production: { id: "tm5Production", type: "number" },
      });

      await saveRaw({
        disperser_total: "pm3DisperserSteam",
        toc_steam: "tocSteam",
        pica121_vent: "pica121Vent",
      });

      await saveCell("usage_m_pm2", 1, "usagePm2Runtime");
      await saveCell("usage_m_pm2", 3, "usagePm2Note");
      // PM-3 가동시간: 하나의 입력을 M+C(pm3a col1) · 디스퍼져(pm3b col1) · Vent(pm3b col5) 세 곳에 동일 저장
      const pm3Runtime = getInputValue("usagePm3McRuntime") || null;
      await saveCellValue("usage_m_pm3a", 1, pm3Runtime);
      await saveCellValue("usage_m_pm3b", 1, pm3Runtime);
      await saveCellValue("usage_m_pm3b", 5, pm3Runtime);
      await saveCell("usage_m_pm3a", 3, "usagePm3McNote");
      await saveCell("usage_m_tm", 1, "usageTm3Runtime");
      await saveCell("usage_m_tm", 4, "usageTm4Runtime");
      await saveCell("usage_m_tm", 7, "usageTm5Runtime");
      // 화장지 계 가동시간 = TM-3~5 가동시간 합 (입력 하나라도 있으면 저장)
      const tmRuntimes = ["usageTm3Runtime", "usageTm4Runtime", "usageTm5Runtime"].map((id) => parseNumber(getInputValue(id)));
      const hasTmRuntime = tmRuntimes.some((v) => v !== null);
      const tissueRuntime = hasTmRuntime ? tmRuntimes.reduce((sum, v) => sum + (v || 0), 0) : null;
      await saveCellValue("usage_m_tissue", 1, tissueRuntime);

      await saveCell("lng_m_boiler", 0, "lngBoiler1");
      await saveCell("lng_m_boiler", 1, "lngBoiler2");
      await saveCell("lng_m_boiler", 2, "lngBoiler3");
      await saveCell("lng_m_boiler", 3, "lngBoiler4");

      await saveCell("lng_m_burner", 0, "lngBurnerPm3");
      await saveCell("lng_m_burner", 1, "lngBurnerTm3");
      await saveCell("lng_m_burner", 2, "lngBurnerTm4");
      await saveCell("lng_m_burner", 3, "lngBurnerTm5");

      await saveCell("lng_m_mix", 0, "lngMixPm2");
      await saveCell("lng_m_mix", 1, "lngMixPm3");
      await saveCell("lng_m_mix", 3, "lngMixTm3");
      await saveCell("lng_m_mix", 4, "lngMixTm4");
      await saveCell("lng_m_mix", 5, "lngMixTm5");

      await saveCell("flow_m_fluid_incinerator", 0, "flowFluidAmount");
      await saveCell("flow_m_fluid_incinerator", 3, "flowIncineratorAmount");

      await saveCell("flow_m_combined", 0, "flowCombinedAmount");
      await saveCell("flow_m_combined", 3, "flowCombinedDeduction");

      await saveCell("flow_m_boiler", 0, "flowBoilerAmount");
      await saveCell("flow_m_boiler", 3, "flowBoilerDeduction");

      setSaveButtonLabel("저장 완료");
    } catch (error) {
      setSaveButtonLabel("저장");
      alert(error instanceof Error ? error.message : "저장에 실패했습니다.");
    } finally {
      saveButton.disabled = false;
      if (saveButton.textContent === "저장 완료") {
        window.setTimeout(() => {
          setSaveButtonLabel("저장");
        }, 1200);
      }
    }
  }

  saveButton?.addEventListener("click", () => {
    void saveAll();
  });

  // ===== 지종 드롭다운 (스팀사용 원단위 페이지와 동일: SC / ACB / KB) =====
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

  ["pm2ProductionType", "pm3ProductionType"].forEach((id) => {
    const input = document.getElementById(id);
    if (!input) return;
    input.setAttribute("autocomplete", "off");
    input.addEventListener("focus", () => openProductionTypeDropdown(input));
    input.addEventListener("click", () => openProductionTypeDropdown(input));
    input.addEventListener("blur", () => closeProductionTypeDropdown(input));
  });

  void load();
});
