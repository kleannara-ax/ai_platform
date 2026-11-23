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

  const TABLES_BASE = "/tables";   // 도급내역 연동값 조회 주소 모양 (IncineratorStore.fetch 가 받는다)
  const state = {};
  const Api = window.SteamEnergyApi;

  // ── 저장소: 규격 표 (/steam-energy-api/combo-*) ──
  // 화면의 칸 키(항목-일, fuelPrice-설비, 항목-줄번호, combo-plan-행-칸)는 그대로 두고, 여기서 표의 행·필드로 옮긴다.
  const PRODUCTION_FIELDS = ["comboSteam", "comboCondensate", "comboMakeup", "comboDeaerator", "comboProcess", "comboDilution",
    "externalSteam", "externalCondensate", "externalMakeup", "wasteSteam1", "wasteSteam2", "kn20", "kn50",
    "fluidizedSteam", "fluidizedCondensate", "runtime20", "runtime50", "runtimeFluidized",
    // 참고자료 (2026년 10·11월 양식에서 생긴 칸) — 스팀총계·원단위에 넣지 않는다
    "wasteRoomSteam2", "fluidizedTotalSteam", "fluidizedTotalCondensate"];
  const DOWNTIME_FIELDS = ["changeCombo", "changeJesco", "down1", "down2", "disperserSteam", "runtimeMin"];
  const MONTHLY_FIELDS = ["fuelPriceLngBoiler", "fuelPriceFluidized", "fuelPriceCombo", "fuelPriceWaste", "fuelPriceExternal"];
  const RECOVERY_FIELDS = ["lossDate", "lossTime", "lossNote", "lossSteamRate", "lossBaseRate", "lossMinutes"];
  const TEXT_FIELDS = new Set(["changeCombo", "changeJesco", "lossDate", "lossTime", "lossNote"]);
  // 계획 표: 칸 키 접두 → [필드, 첫 칸이 기준연도 1월에서 떨어진 달 수, 첫 칸 번호]
  const PLAN_ROWS = {
    "combo-plan-1-": ["comboBasic", -7, 0], "combo-plan-2-": ["comboSpecial", -7, 0], "combo-plan-4-": ["comboActual", -7, 0],
    "jesco-plan-0-": ["jescoPlan", -8, 0], "jesco-plan-1-": ["jescoActual", -8, 0],
    "lngBoiler-": ["lngBoiler", 0, 1], "lngCombo-": ["lngCombo", 0, 1],
  };
  const stores = {
    production: window.SteamRowStore.create({ path: "combo-daily-productions", keyFields: ["usageDate"], idField: "comboProductionId" }),
    downtime: window.SteamRowStore.create({ path: "combo-daily-downtimes", keyFields: ["usageDate"], idField: "comboDowntimeId" }),
    monthly: window.SteamRowStore.create({ path: "combo-monthly-prices", keyFields: ["yearNo", "monthNo"], idField: "comboPriceId" }),
    recovery: window.SteamRowStore.create({ path: "combo-recovery-losses", keyFields: ["yearNo", "monthNo", "lineNo"], idField: "comboRecoveryId" }),
    plan: window.SteamRowStore.create({ path: "combo-plans", keyFields: ["yearNo", "targetYm"], idField: "comboPlanId" }),
  };

  function upperFirst(text) { return text.charAt(0).toUpperCase() + text.slice(1); }
  function lowerFirst(text) { return text.charAt(0).toLowerCase() + text.slice(1); }
  function shiftMonth(year, offset) {
    const d = new Date(Date.UTC(Number(year), offset, 1));
    return `${d.getUTCFullYear()}-${String(d.getUTCMonth() + 1).padStart(2, "0")}`;
  }
  function monthOffset(year, ym) {
    return (Number(ym.slice(0, 4)) - Number(year)) * 12 + Number(ym.slice(5, 7)) - 1;
  }

  /** 칸 키 → { store, key(행 키), field }. 이 화면이 저장하지 않는 키면 null. */
  function locate(rowKey) {
    const year = Number(yearSelect.value);
    const month = Number(monthSelect.value);
    if (config.key === "etc") {
      for (const prefix of Object.keys(PLAN_ROWS)) {
        if (!rowKey.startsWith(prefix)) continue;
        const indexText = rowKey.slice(prefix.length);
        if (!/^\d+$/.test(indexText)) return null;
        const [field, firstOffset, firstIndex] = PLAN_ROWS[prefix];
        return { store: stores.plan, key: { yearNo: year, targetYm: shiftMonth(year, Number(indexText) - firstIndex + firstOffset) }, field };
      }
      return null;
    }
    if (config.key === "production" && rowKey.startsWith("fuelPrice-")) {
      const field = "fuelPrice" + upperFirst(rowKey.slice("fuelPrice-".length));
      return MONTHLY_FIELDS.includes(field) ? { store: stores.monthly, key: { yearNo: year, monthNo: month }, field } : null;
    }
    const match = /^([A-Za-z0-9]+)-(\d+)$/.exec(rowKey);
    if (!match) return null;
    const field = match[1];
    const index = Number(match[2]);
    if (config.key === "recovery") {
      return RECOVERY_FIELDS.includes(field) ? { store: stores.recovery, key: { yearNo: year, monthNo: month, lineNo: index }, field } : null;
    }
    const fields = config.key === "production" ? PRODUCTION_FIELDS : DOWNTIME_FIELDS;
    if (!fields.includes(field) || index < 1 || index > daysInMonth(year, month)) return null;
    return { store: stores[config.key], key: { usageDate: Api.isoDate(year, month, index) }, field };
  }

  function putState(key, value) {
    if (value !== null && value !== undefined && value !== "") state[key] = String(value);
  }
  // 폐합성소각로 도급내역 — 2호 스팀 구입량(T/D) 의 기본값은 그 페이지 '실 스팀량 기준' 표의 2호기 스팀량에서 온다.
  const CONTRACT_TABLE = "incinerator_contract";
  let contractRows = [];
  let activeSection = "purchase";
  let recoveryRowCount = 12;

  // 스팀생산실적 탭 — 탭마다 [설비 묶음, 그 묶음의 칸들]. 칸 정의는 productionFields.
  const productionSections = [
    { id: "purchase", label: "구매 스팀", groups: [
      ["복합보일러", ["comboSteam", "comboSteamHour", "comboCondensate", "comboMakeup", "comboDeaerator", "comboProcess", "comboDilution"]],
      ["폐합성 소각로", ["wasteSteam1", "wasteSteam1Hour", "wasteSteam2", "wasteSteam2Hour"]],
      ["외부보일러", ["externalSteam", "externalSteamHour", "externalCondensate", "externalMakeup"]],
    ] },
    { id: "own", label: "자체 생산", groups: [
      ["깨끗한나라 보일러", ["kn20", "kn50"]],
      ["유동상", ["fluidizedSteam", "fluidizedCondensate"]],
      ["당사 보일러 가동시간", ["runtime20", "runtime50", "runtimeFluidized"]],
    ] },
    { id: "total", label: "합계 · 연료단가", groups: [
      ["설비별 스팀량", ["viewCombo", "viewWaste", "viewExternal", "viewKn", "viewFluidized"]],
      ["스팀총계", ["totalSteam", "totalSteamHour"]],
    ] },
    { id: "reference", label: "참고자료",
      note: "참고자료입니다. 스팀총계와 스팀 원단위에는 들어가지 않습니다. 회색 칸은 비교용으로 다른 탭의 값을 가져온 것입니다.",
      groups: [
        ["폐합성소각로 기관실유량계", ["wasteRoomSteam2", "viewWaste2", "wasteRoomDiff"]],
        // 비교할 수 있게 합계에 들어가는 값(스팀 생산량 FI-304 · 응축수 FI-07)을 바로 옆에 읽기 전용으로 둔다
        ["유동상 보일러 스팀", ["fluidizedTotalSteam", "viewFluidizedSteam", "fluidizedSteamDiff"]],
        ["유동상 보일러 응축수", ["fluidizedTotalCondensate", "viewFluidizedCondensate", "fluidizedCondensateDiff"]],
      ] },
  ];

  // 칸 → [이름, 단위, 종류]. input 은 저장하는 칸, calc 는 화면에서 계산만 하는 칸.
  const productionFields = {
    comboSteam: ["스팀구매량", "T/D", "input"],
    comboSteamHour: ["스팀구매량", "T/H", "calc"],
    comboCondensate: ["응축수판매량", "㎥/D", "input"],
    comboMakeup: ["보충수판매량", "㎥/D", "input"],
    comboDeaerator: ["탈기기급수량", "㎥/D", "input"],
    comboProcess: ["공정수판매량", "㎥/D", "input"],
    comboDilution: ["희석수판매량", "㎥/D", "input"],
    externalSteam: ["스팀구매량", "T/D", "input"],
    externalSteamHour: ["스팀구매량", "T/H", "calc"],
    externalCondensate: ["응축수판매량", "㎥/D", "input"],
    externalMakeup: ["보충수판매량", "㎥/D", "input"],
    wasteSteam1: ["1호 스팀 구입량", "T/D", "input"],
    wasteSteam1Hour: ["1호 스팀 구입량", "T/H", "calc"],
    wasteSteam2: ["2호 스팀 구입량", "T/D", "input"],
    wasteSteam2Hour: ["2호 스팀 구입량", "T/H", "calc"],
    kn20: ["20톤(#2) 스팀생산량", "T/D", "input"],
    kn50: ["50톤(#3) 스팀생산량", "T/D", "input"],
    fluidizedSteam: ["스팀 생산량", "T/D", "input"],
    fluidizedCondensate: ["응축수", "㎥/D", "input"],
    runtime20: ["20톤(#2)", "H/D", "input"],
    runtime50: ["50톤(#3)", "H/D", "input"],
    runtimeFluidized: ["유동상", "H/D", "input"],
    viewCombo: ["복합보일러", "T/D", "calc"],
    viewWaste: ["폐합성 소각로", "T/D", "calc"],
    viewExternal: ["외부보일러", "T/D", "calc"],
    viewKn: ["깨끗한나라 보일러", "T/D", "calc"],
    viewFluidized: ["유동상", "T/D", "calc"],
    totalSteam: ["합계", "T/D", "calc"],
    totalSteamHour: ["합계", "T/H", "calc"],
    wasteRoomSteam2: ["2호 스팀량", "T/D", "input"],
    viewWaste2: ["2호 스팀 구입량", "T/D", "calc"],
    wasteRoomDiff: ["편차 (구입량 − 기관실)", "T/D", "calc"],
    fluidizedTotalSteam: ["총스팀량 (FI-301)", "T/D", "input"],
    viewFluidizedSteam: ["스팀 생산량 (FI-304)", "T/D", "calc"],
    fluidizedSteamDiff: ["편차 (총스팀량 − 생산량)", "T/D", "calc"],
    fluidizedTotalCondensate: ["응축수 (FI-303)", "㎥/D", "input"],
    viewFluidizedCondensate: ["응축수 (FI-07)", "㎥/D", "calc"],
    fluidizedCondensateDiff: ["편차 (FI-303 − FI-07)", "㎥/D", "calc"],
  };

  // 스팀총계에 들어가는 일곱 칸 (참고자료 칸은 없다)
  const TOTAL_STEAM_FIELDS = ["comboSteam", "externalSteam", "wasteSteam1", "wasteSteam2", "kn20", "kn50", "fluidizedSteam"];

  /** 칸들을 더한다. 값이 든 칸이 하나도 없으면 null (그날은 빈칸으로 둔다). */
  function addFields(fields, day) {
    let total = null;
    fields.forEach((field) => {
      const value = dayValue(field, day);
      if (value !== null) total = (total ?? 0) + value;
    });
    return total;
  }
  function perHour(value) { return value === null ? null : value / 24; }

  // 계산 칸 → 그날 값
  const productionDerived = {
    comboSteamHour: (day) => perHour(dayValue("comboSteam", day)),
    externalSteamHour: (day) => perHour(dayValue("externalSteam", day)),
    wasteSteam1Hour: (day) => perHour(dayValue("wasteSteam1", day)),
    wasteSteam2Hour: (day) => perHour(dayValue("wasteSteam2", day)),
    viewCombo: (day) => dayValue("comboSteam", day),
    viewWaste: (day) => addFields(["wasteSteam1", "wasteSteam2"], day),
    viewExternal: (day) => dayValue("externalSteam", day),
    viewKn: (day) => addFields(["kn20", "kn50"], day),
    viewFluidized: (day) => dayValue("fluidizedSteam", day),
    totalSteam: (day) => addFields(TOTAL_STEAM_FIELDS, day),
    totalSteamHour: (day) => perHour(addFields(TOTAL_STEAM_FIELDS, day)),
    viewWaste2: (day) => dayValue("wasteSteam2", day),
    viewFluidizedSteam: (day) => dayValue("fluidizedSteam", day),
    viewFluidizedCondensate: (day) => dayValue("fluidizedCondensate", day),
    fluidizedSteamDiff: (day) => {
      const total = dayValue("fluidizedTotalSteam", day);
      return total === null ? null : total - (dayValue("fluidizedSteam", day) ?? 0);
    },
    fluidizedCondensateDiff: (day) => {
      const total = dayValue("fluidizedTotalCondensate", day);
      return total === null ? null : total - (dayValue("fluidizedCondensate", day) ?? 0);
    },
    wasteRoomDiff: (day) => {
      const room = dayValue("wasteRoomSteam2", day);
      return room === null ? null : (dayValue("wasteSteam2", day) ?? 0) - room;
    },
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
    // 화면 글자(계산칸)를 되읽을 때 음수 표기 △ 를 - 로 (steam-negative-sign.js)
    const text = (window.SteamNegativeSign ? window.SteamNegativeSign.normalize(value) : String(value ?? "")).replace(/,/g, "").trim();
    if (!text) return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function formatNumber(value, digits = 0, minDigits = 0) {
    if (value === null || value === undefined || !Number.isFinite(Number(value))) return "";
    return new Intl.NumberFormat("ko-KR", { minimumFractionDigits: minDigits, maximumFractionDigits: digits }).format(Number(value));
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

  /** 일별 입력 칸의 값. 손으로 넣은 값이 없으면 연동값(도급내역)을 따른다 — 그 칸이 지금 탭에 없어도 같다. 비었으면 null. */
  function dayValue(field, day) {
    const key = `${field}-${day}`;
    const own = valueOf(key);
    if (own !== null) return own;
    const linked = linkedValue(key);
    return linked ? parseNumber(linked) : null;
  }

  function setCalc(key, value, digits = 0, minDigits = 0) {
    const cell = tableWrap.querySelector(`[data-calc="${CSS.escape(key)}"]`);
    if (cell) cell.textContent = formatNumber(value, digits, minDigits);
  }

  /**
   * 다른 페이지에서 오는 기본값. 지금은 2호 스팀 구입량(T/D) 하나 —
   * 폐합성소각로 도급내역 '실 스팀량 기준' 표의 2호기 스팀량(real|일|steam-2). 연동 칸이 아니면 null, 값이 없으면 "".
   */
  function linkedValue(key) {
    const match = /^wasteSteam2-(\d+)$/.exec(key);
    if (!match) return null;
    const row = contractRows.find((item) => String(item.row_key) === `real|${Number(match[1])}|steam-2`);
    return row ? normalizeEditableValue(row.cell_value) : "";
  }

  const LINK_HINT = "폐합성소각로 도급내역 ▸ 실 스팀량 기준 2호기 스팀량에서 가져옵니다 (여기서 직접 고칠 수 있고, 비우면 다시 가져옵니다)";

  function inputCell(key, className = "", cellClass = "") {
    const own = normalizeEditableValue(state[key]);
    const linked = linkedValue(key);
    // 손으로 넣은 값이 있으면 그 값, 없으면 연동값을 보여 준다.
    const value = own !== "" ? own : (linked ?? "");
    const linkAttrs = linked !== null ? ` title="${escapeHtml(LINK_HINT)}"` : "";
    const linkClass = linked !== null && own === "" ? " combo-linked" : "";
    return `<td${cellClass ? ` class="${cellClass}"` : ""}><input class="combo-input ${className}${linkClass}" type="text" data-key="${escapeHtml(key)}" value="${escapeHtml(value)}"${linkAttrs} /></td>`;
  }

  function calcCell(key, cellClass = "") {
    return `<td class="combo-readonly${cellClass ? ` ${cellClass}` : ""}" data-calc="${escapeHtml(key)}"></td>`;
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

  function normalizeEditableValue(value) {
    return String(value ?? "").replace(/,/g, "").trim();
  }

  /** 조회한 행을 칸 키 → 값 으로 편다. */
  function applyRowsToState() {
    Object.keys(state).forEach((key) => delete state[key]);
    const year = Number(yearSelect.value);
    if (config.key === "production") {
      stores.production.all().forEach((row) => {
        const day = Api.dayOf(row.usageDate);
        PRODUCTION_FIELDS.forEach((field) => putState(`${field}-${day}`, row[field]));
      });
      stores.monthly.all().forEach((row) => {
        MONTHLY_FIELDS.forEach((field) => putState(`fuelPrice-${lowerFirst(field.slice("fuelPrice".length))}`, row[field]));
      });
    } else if (config.key === "downtime") {
      stores.downtime.all().forEach((row) => {
        const day = Api.dayOf(row.usageDate);
        DOWNTIME_FIELDS.forEach((field) => putState(`${field}-${day}`, row[field]));
      });
    } else if (config.key === "recovery") {
      stores.recovery.all().forEach((row) => {
        RECOVERY_FIELDS.forEach((field) => putState(`${field}-${row.lineNo}`, row[field]));
      });
    } else if (config.key === "etc") {
      stores.plan.all().forEach((row) => {
        const offset = monthOffset(year, String(row.targetYm));
        Object.keys(PLAN_ROWS).forEach((prefix) => {
          const [field, firstOffset, firstIndex] = PLAN_ROWS[prefix];
          putState(`${prefix}${offset - firstOffset + firstIndex}`, row[field]);
        });
      });
    }
    recoveryRowCount = Math.max(12, maxRecoveryRowIndex());
  }

  function maxRecoveryRowIndex() {
    return Object.keys(state).reduce((max, key) => {
      const match = key.match(/^loss(?:Date|Time|Note|SteamRate|BaseRate|Minutes)-(\d+)$/);
      return match ? Math.max(max, Number(match[1])) : max;
    }, 0);
  }

  async function fetchCellRows() {
    const year = Number(yearSelect.value);
    const month = Number(monthSelect.value);
    contractRows = [];
    if (config.key === "production") {
      // 도급내역(2호기 실 스팀량)은 규격 표 steam_incinerator_contract_daily 에서 온다 (incinerator-store.js 가 옛 키 모양으로 돌려준다).
      const contract = window.IncineratorStore.fetch(`${TABLES_BASE}/steam_cell_value?page=1&limit=10000&tableNames=${CONTRACT_TABLE}&month=${encodeURIComponent(currentMonthKey())}`)
        .then((response) => (response.ok ? response.json() : { data: [] }))
        .then((body) => (Array.isArray(body.data) ? body.data : []))
        .catch(() => []);
      await Promise.all([stores.production.load({ year, month }), stores.monthly.load({ year, month })]);
      contractRows = await contract;
    } else if (config.key === "downtime") {
      await stores.downtime.load({ year, month });
    } else if (config.key === "recovery") {
      await stores.recovery.load({ year, month });
    } else if (config.key === "etc") {
      await stores.plan.load({ year });
    }
    applyRowsToState();
  }

  async function upsertInput(input) {
    const rowKey = input?.dataset?.key;
    if (!rowKey) return;
    const rawValue = normalizeEditableValue(input.value);
    state[rowKey] = rawValue;
    const target = locate(rowKey);
    if (!target) return;
    // 숫자 칸에 숫자가 아닌 글자를 넣으면 저장하지 않고 비운다.
    const value = TEXT_FIELDS.has(target.field) ? (rawValue === "" ? null : rawValue) : Api.num(rawValue);
    await target.store.save(target.key, { [target.field]: value });
    // 연동 칸을 비우면 다시 도급내역 값으로 돌아간다 — 칸에도 그 값을 보여 주고 다시 계산한다.
    const linked = linkedValue(rowKey);
    if (linked !== null) {
      input.classList.toggle("combo-linked", rawValue === "");
      if (rawValue === "") {
        input.value = linked;
        calculate();
      }
    }
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
    const response = await fetch(`/steam-energy-api/combo-excel/export-excel?year=${encodeURIComponent(yearSelect.value)}`);
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
      const response = await fetch("/steam-energy-api/combo-excel/import-excel", { method: "POST", body: formData });
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

  /** 합계 탭 위쪽 — 설비별 스팀 원단위와 그 달 연료단가. 월 단위 값이라 일별 표와 따로 둔다. */
  function renderProductionFuelTable(section) {
    if (section.id !== "total") return "";
    const columns = [
      ["LNG 보일러", "production-unit-lng-boiler", "fuelPrice-lngBoiler"],
      ["유동상소각로", "production-unit-fluidized", "fuelPrice-fluidized"],
      ["복합보일러", "production-unit-combo", "fuelPrice-combo"],
      ["폐합성소각로", "production-unit-waste", "fuelPrice-waste"],
      ["외부보일러", "production-unit-external", "fuelPrice-external"],
    ];
    return `
      <table class="combo-excel-table combo-production-fuel-table">
        <thead>
          <tr><th>구분</th>${columns.map(([label]) => `<th>${label}</th>`).join("")}<th>합계</th></tr>
        </thead>
        <tbody>
          <tr><th>스팀 원단위 <span class="combo-th-unit">톤/hr</span></th>${columns.map(([_label, unitKey]) => calcCell(unitKey)).join("")}${calcCell("production-unit-total")}</tr>
          <tr><th>${Number(monthSelect.value)}월 연료단가 <span class="combo-th-unit">원/톤</span></th>${columns.map(([_label, _unitKey, priceKey]) => inputCell(priceKey)).join("")}<td class="combo-blank"></td></tr>
        </tbody>
      </table>
    `;
  }

  function renderProduction() {
    const days = daysInMonth(yearSelect.value, monthSelect.value);
    const section = productionSections.find((item) => item.id === activeSection) || productionSections[0];
    // 칸마다 묶음의 첫 칸인지 표시 — 묶음 사이에 굵은 세로줄을 긋는다.
    const columns = section.groups.flatMap(([_label, fields]) => fields.map((field, index) => ({ field, start: index === 0 })));
    const cellOf = ({ field, start }, key) => {
      const cellClass = start ? "combo-group-start" : "";
      return productionFields[field][2] === "calc" ? calcCell(key, cellClass) : inputCell(key, "", cellClass);
    };
    const rows = [];
    for (let day = 1; day <= days; day += 1) {
      rows.push(`<tr data-day="${day}"><th scope="row">${day}</th>${columns.map((column) => cellOf(column, `${column.field}-${day}`)).join("")}</tr>`);
    }
    const summary = (type, label) => `<tr class="combo-summary-row"><th scope="row">${label}</th>${columns.map(({ field, start }) => calcCell(`${type}-${field}`, start ? "combo-group-start" : "")).join("")}</tr>`;
    tableWrap.innerHTML = `
      ${section.note ? `<p class="combo-section-note">${escapeHtml(section.note)}</p>` : ""}
      ${renderProductionFuelTable(section)}
      <table class="combo-excel-table combo-production-table combo-production-${escapeHtml(section.id)}">
        <colgroup><col class="combo-day-col" />${columns.map(() => "<col />").join("")}</colgroup>
        <thead>
          <tr class="combo-group-row">
            <th rowspan="2">일</th>
            ${section.groups.map(([label, fields]) => `<th colspan="${fields.length}" class="combo-group-start">${escapeHtml(label)}</th>`).join("")}
          </tr>
          <tr class="combo-field-row">
            ${columns.map(({ field, start }) => `<th${start ? ' class="combo-group-start"' : ""}>${escapeHtml(productionFields[field][0])}<span class="combo-th-unit">${escapeHtml(productionFields[field][1])}</span></th>`).join("")}
          </tr>
        </thead>
        <tbody>${rows.join("")}${summary("sum", "누계")}${summary("avg", "평균")}</tbody>
      </table>
    `;
  }

  function dailyAverage(field, days) {
    let total = 0;
    for (let day = 1; day <= days; day += 1) total += dayValue(field, day) ?? 0;
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
      const derive = productionDerived[field];
      // 시간당(T/H) 칸은 소수 둘째 자리까지 맞춰 적는다 — 한 열 안에서 자릿수가 들쭉날쭉하지 않게.
      const minDigits = productionFields[field][1] === "T/H" ? 2 : 0;
      let sum = null;                      // 값이 든 날이 하루도 없으면 누계·평균도 빈칸
      for (let day = 1; day <= days; day += 1) {
        const value = derive ? derive(day) : dayValue(field, day);
        if (derive) setCalc(`${field}-${day}`, value, 2, minDigits);
        if (value !== null) sum = (sum ?? 0) + value;
      }
      setCalc(`sum-${field}`, sum, 2, minDigits);
      setCalc(`avg-${field}`, sum === null ? null : sum / days, 2, minDigits);
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
      alert("저장에 실패했습니다." + (error && error.message ? "\n" + error.message : ""));
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
