/* 오늘 일지 등록 — 어제(KST) 하루치를 한 번에 넣는 화면. 2026-11 규격 전환.
 *  - 호기별 실적 POST /steam-energy-api/unit-daily-usages (PM-2·PM-3·TM-3·4·5, 가동시간·비고 포함)
 *  - 공통값 POST /unit-daily-commons, LNG POST /lng-daily-usages, 스팀 구매 POST /fuel-daily-usages
 *  행 전체 업서트라 저장 직전에 그 날 행을 읽어 두고, 이 화면에서 비워 둔 칸은 기존 값을 그대로 둔다
 *  (단가처럼 다른 화면이 넣는 값도 유지). 한 묶음의 입력이 모두 비어 있으면 그 묶음은 보내지 않는다.
 */
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

  const API = window.SteamEnergyApi;
  if (!API) { console.error("steam-energy-api.js 가 로드되지 않았습니다."); return; }
  const UNIT_ROW_FIELDS = ["mainSteam", "coaterSteam", "ventilationSteam", "steam", "productionKg", "productionType",
    "runtimeHours", "disperserRuntimeHours", "ventRuntimeHours", "note"];
  const COMMON_ROW_FIELDS = ["disperserTotal", "tocSteam", "pica121Vent"];
  const LNG_ROW_FIELDS = ["boiler20t1", "boiler20t2", "boiler50t", "boiler60t", "boilerCombined", "boilerNewIncinerator", "boilerUnitPrice",
    "burnerPm3", "burnerTm3", "burnerTm4", "burnerTm5", "burnerUnitPrice", "mixPm2", "mixPm3", "mixTm3", "mixTm4", "mixTm5"];
  const FUEL_ROW_FIELDS = ["fluidSteamTon", "fluidUnitPrice", "incineratorSteamTon", "incineratorUnitPrice",
    "combinedSteamTon", "combinedUnitPrice", "combinedDeduction", "extBoilerSteamTon", "extBoilerUnitPrice", "extBoilerDeduction"];

  let today = null;
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
    fillForm();
    bindNumberFormatting();
    saveButton.disabled = false;
    setSaveButtonLabel("저장");
  }

  // ── 규격 API 저장 ─────────────────────────────────────────────
  function isoToday() { return `${today.year}-${today.month}-${today.day}`; }
  function monthQuery() { return `year=${Number(today.year)}&month=${Number(today.month)}`; }
  // 입력칸 → 보낼 값. 비어 있으면 undefined(= 기존 값 유지).
  function numberOf(id) { const v = getInputValue(id); return v === "" ? undefined : parseNumber(v); }
  function textOf(id) { const v = getInputValue(id); return v === "" ? undefined : v; }
  function anyFilled(values) { return Object.values(values).some((v) => v !== undefined); }

  function mergeRow(fieldNames, base, values, keys) {
    const body = { ...keys };
    fieldNames.forEach((f) => { body[f] = base && base[f] !== undefined ? base[f] : null; });
    Object.entries(values).forEach(([f, v]) => { if (v !== undefined) body[f] = v; });
    return body;
  }

  async function freshRow(path, pick) {
    const rows = await API.get(path);
    return rows.find(pick) || null;
  }

  // 호기별 실적 — 그 호기 입력이 하나라도 있을 때만. 다른 화면이 넣은 값은 최신 행에서 가져와 유지한다.
  async function saveMachine(machine, values) {
    if (!anyFilled(values)) return;
    const usageDate = isoToday();
    const base = await freshRow(`/unit-daily-usages?${monthQuery()}&machineCode=${encodeURIComponent(machine)}`, (r) => r.usageDate === usageDate);
    await API.post("/unit-daily-usages", mergeRow(UNIT_ROW_FIELDS, base, values, { usageDate, machineCode: machine }));
  }

  async function saveCommon(values) {
    if (!anyFilled(values)) return;
    const usageDate = isoToday();
    const base = await freshRow(`/unit-daily-commons?${monthQuery()}`, (r) => r.usageDate === usageDate);
    await API.post("/unit-daily-commons", mergeRow(COMMON_ROW_FIELDS, base, values, { usageDate }));
  }

  async function saveLng(values) {
    if (!anyFilled(values)) return;
    const usageDate = isoToday();
    const base = await freshRow(`/lng-daily-usages?${monthQuery()}`, (r) => r.usageDate === usageDate);
    await API.post("/lng-daily-usages", mergeRow(LNG_ROW_FIELDS, base, values, { usageDate }));
  }

  async function saveFuel(values) {
    if (!anyFilled(values)) return;
    const usageDate = isoToday();
    const base = await freshRow(`/fuel-daily-usages?${monthQuery()}`, (r) => r.usageDate === usageDate);
    await API.post("/fuel-daily-usages", mergeRow(FUEL_ROW_FIELDS, base, values, { usageDate }));
  }

  async function saveAll() {
    setSaveButtonLabel("저장 중");
    saveButton.disabled = true;
    try {
      updateTodayContext();

      await saveMachine("PM-2", {
        mainSteam: numberOf("pm2MainSteam"), coaterSteam: numberOf("pm2CoaterSteam"),
        productionKg: numberOf("pm2Production"), productionType: textOf("pm2ProductionType"),
        runtimeHours: numberOf("usagePm2Runtime"), note: textOf("usagePm2Note"),
      });
      // PM-3 가동시간: 하나의 입력을 M+C · 디스퍼져 · Vent 가동시간 세 곳에 같은 값으로
      const pm3Runtime = numberOf("usagePm3McRuntime");
      await saveMachine("PM-3", {
        mainSteam: numberOf("pm3MainSteam"), coaterSteam: numberOf("pm3CoaterSteam"), ventilationSteam: numberOf("pm3VentSteam"),
        productionKg: numberOf("pm3Production"), productionType: textOf("pm3ProductionType"),
        runtimeHours: pm3Runtime, disperserRuntimeHours: pm3Runtime, ventRuntimeHours: pm3Runtime, note: textOf("usagePm3McNote"),
      });
      await saveMachine("TM-3", { steam: numberOf("tm3Steam"), productionKg: numberOf("tm3Production"), runtimeHours: numberOf("usageTm3Runtime") });
      await saveMachine("TM-4", { steam: numberOf("tm4Steam"), productionKg: numberOf("tm4Production"), runtimeHours: numberOf("usageTm4Runtime") });
      await saveMachine("TM-5", { steam: numberOf("tm5Steam"), productionKg: numberOf("tm5Production"), runtimeHours: numberOf("usageTm5Runtime") });

      await saveCommon({ disperserTotal: numberOf("pm3DisperserSteam"), tocSteam: numberOf("tocSteam"), pica121Vent: numberOf("pica121Vent") });

      await saveLng({
        boiler20t1: numberOf("lngBoiler1"), boiler20t2: numberOf("lngBoiler2"), boiler50t: numberOf("lngBoiler3"), boiler60t: numberOf("lngBoiler4"),
        burnerPm3: numberOf("lngBurnerPm3"), burnerTm3: numberOf("lngBurnerTm3"), burnerTm4: numberOf("lngBurnerTm4"), burnerTm5: numberOf("lngBurnerTm5"),
        mixPm2: numberOf("lngMixPm2"), mixPm3: numberOf("lngMixPm3"), mixTm3: numberOf("lngMixTm3"), mixTm4: numberOf("lngMixTm4"), mixTm5: numberOf("lngMixTm5"),
      });

      await saveFuel({
        fluidSteamTon: numberOf("flowFluidAmount"), incineratorSteamTon: numberOf("flowIncineratorAmount"),
        combinedSteamTon: numberOf("flowCombinedAmount"), combinedDeduction: numberOf("flowCombinedDeduction"),
        extBoilerSteamTon: numberOf("flowBoilerAmount"), extBoilerDeduction: numberOf("flowBoilerDeduction"),
      });

      setSaveButtonLabel("저장 완료");
    } catch (error) {
      setSaveButtonLabel("저장");
      alert(error instanceof Error ? `저장 실패: ${error.message}` : "저장에 실패했습니다.");
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
