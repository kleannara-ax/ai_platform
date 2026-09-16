/*
 * 유동상 운전일지 (현장 작성 원본 양식 / 일단위)
 *  - 엑셀 "유동상 운전일지" 의 하루치 시트(1~31) 폼을 그대로 옮긴 입력 페이지.
 *  - 세부 운영내역과 공유하는 항목(소각량·스팀·약품·전력·용수)은 세부 운영내역 셀
 *    (fluidized_detail_main / flow_m_fluid_incinerator) 을 직접 읽고 쓴다. DETAIL_MAP 참고.
 *    → 기존 세부 운영내역 데이터가 그대로 보이고, 운전일지 입력은 세부 운영내역에 즉시 반영된다.
 *  - 나머지(근무자·결재·전일지침·소각재·특이사항)만 table_cell_value
 *    (table_name="fluidized_daily_log", row_key=일, col_index=항목번호) 에 저장한다.
 *  - col_index 는 항목 고정 번호이므로 절대 재배치하지 말 것 (COL 참고).
 */
document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "fluidized-daily-log.html") return;

  const CELL_TABLE = "table_cell_value";
  const LOG_TABLE = "fluidized_daily_log";
  // 세부 운영내역과 공유하는 저장소
  //  - fluidized_detail_main    : row_key = 셀 참조("C18"), col_index = 0. 열 = 일자+2 (C=1일 … AG=31일)
  //  - flow_m_fluid_incinerator : row_key = 2자리 일자, col_index = 0  (세부 7행 스팀 송기량)
  // DETAIL_MAP 에 있는 항목은 운전일지 전용 테이블에 따로 저장하지 않고 세부 운영내역 셀을 직접 읽고 쓴다.
  // → 과거 세부 운영내역 데이터가 운전일지에 그대로 보이고, 운전일지 입력은 즉시 세부 운영내역에 반영된다.
  const DETAIL_TABLE = "fluidized_detail_main";
  const FLOW_TABLE = "flow_m_fluid_incinerator";
  const TON = 0.001; // 운전일지 KG → 세부 운영내역 톤
  const TABLES_BASE = "/tables";
  const PERIOD_KEY = "steamlog:shared-period";

  // ── 항목 정의 ────────────────────────────────────────────────
  const CHEMICALS = ["탄산암모늄", "청관제", "활성탄", "가성소다", "소석회", "소 금", "경 유", "SRF", "규 사"];
  const POWERS = ["MAIN", "ASH 저장조", "BAG FILTER", "SCRUBBER", "활성탄 공급", "소석회 공급", "요소수 공급", "F.D", "활성탄 BAG", "소석회 BAG"];
  const UTILS = ["재이용수", "복류수", "응축수", "보일러 급수", "폐 수", "연 수"];

  // col_index 고정 번호. (약품 20~46 / 전력 60~79 / 유틸 90~101)
  const COL = {
    workerAm: 1, workerPm: 2, workerNight: 3,
    apprLeader: 4, apprAssistant: 5, apprManager: 6,
    srf: 10, sludge: 11,
    steamProd: 12, steamSend: 13, runHours: 14,
    chemPrev: (i) => 20 + i * 3,
    chemIn: (i) => 21 + i * 3,
    chemUse: (i) => 22 + i * 3,
    ashFly: 50, ashBottom: 51, incombDay: 52, incombNight: 53,
    powerPrev: (i) => 60 + i * 2,
    powerToday: (i) => 61 + i * 2,
    utilPrev: (i) => 90 + i * 2,
    utilToday: (i) => 91 + i * 2,
    // 110~112 는 특이사항이 쓰던 번호 — 양식에서 제외되어 비워 둔다.
    // 함수율: 교대 s(0 오전 /1 오후 /2 야간) × 탈수기 m(0~4)
    moistTime: (s, m) => 200 + s * 10 + m * 2,
    moistValue: (s, m) => 201 + s * 10 + m * 2,
  };

  const MOIST_SHIFTS = ["오 전", "오 후", "야 간"];
  const MOIST_MACHINES = ["1번 탈수기", "2번 탈수기", "3번 탈수기", "4번 탈수기", "5번 탈수기"];

  // ── 세부 운영내역 매핑 ───────────────────────────────────────
  // 약품 i → 세부 운영내역 행 (반입 / 사용). SRF 사용은 세부에서 소각량(9행)으로 산출되므로 같은 행을 쓴다.
  const CHEM_DETAIL_ROWS = [
    { in: 18, use: 19 },                                    // 탄산암모늄
    { in: 21, use: 22 },                                    // 청관제
    { in: 24, use: 25 },                                    // 활성탄
    { in: 27, use: 28 },                                    // 가성소다
    { in: 30, use: 31 },                                    // 소석회
    { in: 33, use: 34 },                                    // 소 금
    { in: 36, use: 37 },                                    // 경 유(L)
    { in: 46, inScale: TON, use: 9, useScale: TON },        // SRF (반입 46행 / 사용 = 소각량 9행)
    { in: 39, use: 40 },                                    // 규 사
  ];
  // 전력: MAIN … F.D (활성탄 BAG / 소석회 BAG 는 세부 운영내역에 항목 없음)
  const POWER_DETAIL_ROWS = [65, 66, 67, 68, 69, 70, 71, 72];
  const UTIL_DETAIL_ROWS = [74, 75, 76, 77, 78, 79];

  // col → { row, scale } (scale: 운전일지 값 × scale = 세부 저장값)
  const DETAIL_MAP = new Map();
  DETAIL_MAP.set(COL.runHours, { row: 4 });
  DETAIL_MAP.set(COL.steamProd, { row: 6 });
  DETAIL_MAP.set(COL.sludge, { row: 8, scale: TON });
  DETAIL_MAP.set(COL.srf, { row: 9, scale: TON });
  CHEM_DETAIL_ROWS.forEach((def, i) => {
    DETAIL_MAP.set(COL.chemIn(i), { row: def.in, scale: def.inScale });
    DETAIL_MAP.set(COL.chemUse(i), { row: def.use, scale: def.useScale });
  });
  UTIL_DETAIL_ROWS.forEach((row, i) => DETAIL_MAP.set(COL.utilToday(i), { row }));

  function columnLetter(index) {
    let n = index;
    let label = "";
    while (n > 0) {
      const remainder = (n - 1) % 26;
      label = String.fromCharCode(65 + remainder) + label;
      n = Math.floor((n - 1) / 26);
    }
    return label;
  }

  function detailRef(day, row) {
    return `${columnLetter(day + 2)}${row}`;
  }

  function roundNoise(value) {
    return Math.round(value * 1e6) / 1e6;
  }

  // 세부 저장값 → 운전일지 표시값
  function fromDetail(raw, scale) {
    const text = String(raw ?? "").trim();
    if (!text) return "";
    const number = Number(text.replace(/,/g, ""));
    if (!Number.isFinite(number)) return text;
    return String(roundNoise(scale ? number / scale : number));
  }

  // 운전일지 입력값 → 세부 저장값
  function toDetail(raw, scale) {
    const text = String(raw ?? "").replace(/,/g, "").trim();
    if (!text) return "";
    const number = Number(text);
    if (!Number.isFinite(number)) return text;
    return String(roundNoise(scale ? number * scale : number));
  }

  // 소수 자릿수 (표시용)
  const D0 = 0, D1 = 1, D2 = 2;

  const yearSelect = document.getElementById("fdlYear");
  const monthSelect = document.getElementById("fdlMonth");
  const daySelect = document.getElementById("fdlDay");
  const prevDayButton = document.getElementById("fdlPrevDay");
  const nextDayButton = document.getElementById("fdlNextDay");
  const reloadButton = document.getElementById("fdlReload");
  const saveButton = document.getElementById("fdlSave");
  const printButton = document.getElementById("fdlPrint");
  const uploadButton = document.getElementById("fdlUpload");
  const uploadInput = document.getElementById("fdlUploadFile");
  const downloadButton = document.getElementById("fdlDownload");
  const messageBox = document.getElementById("fdlMsg");
  const sheetDate = document.getElementById("fdlSheetDate");
  if (!yearSelect || !monthSelect || !daySelect) return;

  // 운전일지 전용 값: key = `${monthKey}|${day}|${col}`
  const cache = new Map();
  const rowIds = new Map();
  // 세부 운영내역 공유 값: key = `${monthKey}|${cellRef}` / `${monthKey}|${day}`
  const detailCache = new Map();
  const detailIds = new Map();
  const flowCache = new Map();
  const flowIds = new Map();
  const loadedMonths = new Set();
  let messageTimer = null;
  let activeTab = "log"; // log | chem | etc | moisture
  // 저장 버튼을 누를 때까지 모아두는 변경분. key = `${monthKey}|${day}|${col}`
  const pending = new Map();

  // ── 공통 유틸 ────────────────────────────────────────────────
  function escapeHtml(value) {
    return String(value ?? "")
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function parseNumber(value) {
    if (value === null || value === undefined) return null;
    const text = String(value).replace(/,/g, "").trim();
    if (!text) return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function formatNumber(value, digits = 0) {
    if (value === null || value === undefined || value === "") return "";
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return String(value);
    return new Intl.NumberFormat("ko-KR", { minimumFractionDigits: 0, maximumFractionDigits: digits }).format(numeric);
  }

  function daysInMonth(monthKey) {
    const [year, month] = monthKey.split("-").map(Number);
    return new Date(year, month, 0).getDate();
  }

  function shiftMonth(monthKey, delta) {
    const [year, month] = monthKey.split("-").map(Number);
    const date = new Date(year, month - 1 + delta, 1);
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, "0")}`;
  }

  function currentMonthKey() {
    return `${yearSelect.value}-${monthSelect.value}`;
  }

  function currentDay() {
    return Number(daySelect.value || 1);
  }

  function cacheKey(monthKey, day, col) {
    return `${monthKey}|${day}|${col}`;
  }

  function detailCacheKey(monthKey, ref) {
    return `${monthKey}|${ref}`;
  }

  function flowCacheKey(monthKey, day) {
    return `${monthKey}|${day}`;
  }

  function getValue(monthKey, day, col) {
    if (day < 1) return "";
    // 스팀 송기량 = 세부 운영내역 7행 (flow_m_fluid_incinerator 링크 셀)
    if (col === COL.steamSend) return flowCache.get(flowCacheKey(monthKey, day)) ?? "";
    const mapped = DETAIL_MAP.get(col);
    if (mapped) {
      const stored = detailCache.get(detailCacheKey(monthKey, detailRef(day, mapped.row))) ?? "";
      return fromDetail(stored, mapped.scale);
    }
    return cache.get(cacheKey(monthKey, day, col)) ?? "";
  }

  function getNumber(monthKey, day, col) {
    return parseNumber(getValue(monthKey, day, col));
  }

  function setMessage(text, kind) {
    if (!messageBox) return;
    messageBox.textContent = text;
    messageBox.className = `fdl-msg${kind ? ` ${kind}` : ""}`;
    if (messageTimer) window.clearTimeout(messageTimer);
    if (text) messageTimer = window.setTimeout(() => { messageBox.textContent = ""; }, 1800);
  }

  // ── 전일(월 경계 포함) 좌표 ──────────────────────────────────
  function previousDayRef(monthKey, day) {
    if (day > 1) return { monthKey, day: day - 1 };
    const prevMonth = shiftMonth(monthKey, -1);
    return { monthKey: prevMonth, day: daysInMonth(prevMonth) };
  }

  // ── 약품 재고 체인 (전일 재고량 이월) ────────────────────────
  // stocks[day][i] = 그 날의 재고량. 저장된 "전일 재고량" 이 있으면 그것을, 없으면 전일 계산값을 사용.
  const stockCacheByMonth = new Map();

  function computeStocks(monthKey, depth = 0) {
    if (stockCacheByMonth.has(monthKey)) return stockCacheByMonth.get(monthKey);
    let seed = CHEMICALS.map(() => null);
    if (depth < 1 && loadedMonths.has(shiftMonth(monthKey, -1))) {
      const prevMonth = shiftMonth(monthKey, -1);
      const prevStocks = computeStocks(prevMonth, depth + 1);
      seed = prevStocks[daysInMonth(prevMonth)] || seed;
    }
    const total = daysInMonth(monthKey);
    const stocks = [];
    const carry = seed.slice();
    for (let day = 1; day <= total; day += 1) {
      const row = CHEMICALS.map((_label, i) => {
        const storedPrev = getNumber(monthKey, day, COL.chemPrev(i));
        const prev = storedPrev !== null ? storedPrev : carry[i];
        const inbound = getNumber(monthKey, day, COL.chemIn(i));
        const used = getNumber(monthKey, day, COL.chemUse(i));
        if (prev === null && inbound === null && used === null) return null;
        return (prev || 0) + (inbound || 0) - (used || 0);
      });
      row.forEach((value, i) => { if (value !== null) carry[i] = value; });
      stocks[day] = row;
    }
    stockCacheByMonth.set(monthKey, stocks);
    return stocks;
  }

  function invalidateStocks() {
    stockCacheByMonth.clear();
  }

  // 전일 재고량 자동값 (저장값 우선)
  function autoChemPrev(monthKey, day, i) {
    const stored = getNumber(monthKey, day, COL.chemPrev(i));
    if (stored !== null) return stored;
    const ref = previousDayRef(monthKey, day);
    if (!loadedMonths.has(ref.monthKey)) return null;
    const stocks = computeStocks(ref.monthKey);
    return stocks[ref.day]?.[i] ?? null;
  }

  function chemStock(monthKey, day, i) {
    const prev = autoChemPrev(monthKey, day, i);
    const inbound = getNumber(monthKey, day, COL.chemIn(i));
    const used = getNumber(monthKey, day, COL.chemUse(i));
    if (prev === null && inbound === null && used === null) return null;
    return (prev || 0) + (inbound || 0) - (used || 0);
  }

  // 전일 지침 / 전일 사용량 자동값
  function autoCarry(monthKey, day, storedCol, sourceCol) {
    const stored = getNumber(monthKey, day, storedCol);
    if (stored !== null) return stored;
    const ref = previousDayRef(monthKey, day);
    if (!loadedMonths.has(ref.monthKey)) return null;
    return getNumber(ref.monthKey, ref.day, sourceCol);
  }

  function autoPowerPrev(monthKey, day, i) {
    return autoCarry(monthKey, day, COL.powerPrev(i), COL.powerToday(i));
  }

  function autoUtilPrev(monthKey, day, i) {
    return autoCarry(monthKey, day, COL.utilPrev(i), COL.utilToday(i));
  }

  function powerUsed(monthKey, day, i) {
    const today = getNumber(monthKey, day, COL.powerToday(i));
    const prev = autoPowerPrev(monthKey, day, i);
    if (today === null && prev === null) {
      // 지침 입력이 없으면 세부 운영내역에 이미 있는 사용량을 그대로 보여준다.
      const row = POWER_DETAIL_ROWS[i];
      if (row === undefined) return null;
      return parseNumber(detailCache.get(detailCacheKey(monthKey, detailRef(day, row))) ?? "");
    }
    return (today || 0) - (prev || 0);
  }

  function incombTotal(monthKey, day) {
    const dayShift = getNumber(monthKey, day, COL.incombDay);
    const nightShift = getNumber(monthKey, day, COL.incombNight);
    if (dayShift === null && nightShift === null) return null;
    return (dayShift || 0) + (nightShift || 0);
  }

  function burnTotal(monthKey, day) {
    const srf = getNumber(monthKey, day, COL.srf);
    const sludge = getNumber(monthKey, day, COL.sludge);
    if (srf === null && sludge === null) return null;
    return (srf || 0) + (sludge || 0);
  }

  // 누계: 해당 월 1일 ~ 조회일 합산
  function accumulate(monthKey, upto, valueOf) {
    let total = null;
    for (let day = 1; day <= upto; day += 1) {
      const value = valueOf(monthKey, day);
      if (value === null) continue;
      total = (total || 0) + value;
    }
    return total;
  }

  function accumulateCol(monthKey, upto, col) {
    return accumulate(monthKey, upto, (mk, day) => getNumber(mk, day, col));
  }

  // ── 표 렌더링 ────────────────────────────────────────────────
  function inputCell(col, options = {}) {
    const attrs = [
      `data-col="${col}"`,
      `data-digits="${options.digits ?? D0}"`,
      'inputmode="decimal"',
      options.auto ? `data-auto-kind="${options.auto}"` : "",
      'autocomplete="off"',
    ].filter(Boolean).join(" ");
    return `<td><input ${attrs} /></td>`;
  }

  function calcCell(key, options = {}) {
    return `<td class="calc" data-calc="${key}" data-digits="${options.digits ?? D0}"></td>`;
  }

  function renderBurn() {
    return `
      <table class="fdl-table">
        <colgroup><col style="width:22%" /><col style="width:26%" /><col style="width:26%" /><col style="width:26%" /></colgroup>
        <thead><tr><th>구 분</th><th>SRF소각량</th><th>SLUDGE 소각량</th><th>합 계</th></tr></thead>
        <tbody>
          <tr>
            <th class="rowhead">금 일</th>
            ${inputCell(COL.srf)}
            ${inputCell(COL.sludge)}
            ${calcCell("burnTotal")}
          </tr>
          <tr class="total-row">
            <th class="rowhead">누 계</th>
            ${calcCell("burnSrfAcc")}
            ${calcCell("burnSludgeAcc")}
            ${calcCell("burnTotalAcc")}
          </tr>
        </tbody>
      </table>`;
  }

  function renderBoiler() {
    return `
      <table class="fdl-table">
        <colgroup><col style="width:22%" /><col style="width:26%" /><col style="width:26%" /><col style="width:26%" /></colgroup>
        <thead><tr><th>구 분</th><th>스팀 생산량</th><th>스팀 송기량</th><th>가동시간</th></tr></thead>
        <tbody>
          <tr>
            <th class="rowhead">금 일</th>
            ${inputCell(COL.steamProd, { digits: D1 })}
            ${inputCell(COL.steamSend, { digits: D1 })}
            ${inputCell(COL.runHours, { digits: D1 })}
          </tr>
          <tr class="total-row">
            <th class="rowhead">누 계</th>
            ${calcCell("steamProdAcc", { digits: D1 })}
            ${calcCell("steamSendAcc", { digits: D1 })}
            ${calcCell("runHoursAcc", { digits: D1 })}
          </tr>
        </tbody>
      </table>`;
  }

  function renderChem() {
    const rows = CHEMICALS.map((label, i) => `
      <tr>
        <th class="rowhead">${escapeHtml(label)}</th>
        ${inputCell(COL.chemPrev(i), { auto: "chemPrev" })}
        ${inputCell(COL.chemIn(i))}
        ${inputCell(COL.chemUse(i))}
        ${calcCell(`chemStock:${i}`)}
        ${calcCell(`chemInAcc:${i}`)}
        ${calcCell(`chemUseAcc:${i}`)}
      </tr>`).join("");
    return `
      <table class="fdl-table">
        <colgroup><col style="width:19%" /><col style="width:13.5%" /><col style="width:13.5%" /><col style="width:13.5%" /><col style="width:13.5%" /><col style="width:13.5%" /><col style="width:13.5%" /></colgroup>
        <thead>
          <tr><th>품 명</th><th>전일 재고량</th><th>입고량</th><th>사용량</th><th>재고량</th><th>입고량 누계</th><th>사용량 누계</th></tr>
        </thead>
        <tbody>${rows}</tbody>
      </table>`;
  }

  function renderAsh() {
    return `
      <table class="fdl-table">
        <colgroup><col style="width:34%" /><col style="width:33%" /><col style="width:33%" /></colgroup>
        <thead><tr><th>구 분</th><th>반출량</th><th>누계</th></tr></thead>
        <tbody>
          <tr>
            <th class="rowhead">비산재</th>
            ${inputCell(COL.ashFly, { digits: D2 })}
            ${calcCell("ashFlyAcc", { digits: D2 })}
          </tr>
          <tr>
            <th class="rowhead">바닥재</th>
            ${inputCell(COL.ashBottom, { digits: D2 })}
            ${calcCell("ashBottomAcc", { digits: D2 })}
          </tr>
        </tbody>
      </table>
      <table class="fdl-table" style="margin-top:6px;">
        <colgroup><col style="width:20%" /><col style="width:20%" /><col style="width:20%" /><col style="width:20%" /><col style="width:20%" /></colgroup>
        <thead><tr><th>구분</th><th>주간</th><th>야간</th><th>합계</th><th>누계</th></tr></thead>
        <tbody>
          <tr>
            <th class="rowhead">불연물</th>
            ${inputCell(COL.incombDay)}
            ${inputCell(COL.incombNight)}
            ${calcCell("incombTotal")}
            ${calcCell("incombAcc")}
          </tr>
        </tbody>
      </table>`;
  }

  function renderPower() {
    const rows = POWERS.map((label, i) => `
      <tr>
        <th class="rowhead">${escapeHtml(label)}</th>
        ${inputCell(COL.powerPrev(i), { digits: D1, auto: "powerPrev" })}
        ${inputCell(COL.powerToday(i), { digits: D1 })}
        ${calcCell(`powerUse:${i}`, { digits: D1 })}
        ${calcCell(`powerAcc:${i}`, { digits: D1 })}
      </tr>`).join("");
    return `
      <table class="fdl-table">
        <colgroup><col style="width:24%" /><col style="width:19%" /><col style="width:19%" /><col style="width:19%" /><col style="width:19%" /></colgroup>
        <thead><tr><th>구 분</th><th>전일지침</th><th>금일지침</th><th>사용량</th><th>누 계</th></tr></thead>
        <tbody>${rows}</tbody>
      </table>`;
  }

  function renderUtil() {
    const rows = UTILS.map((label, i) => `
      <tr>
        <th class="rowhead">${escapeHtml(label)}</th>
        ${inputCell(COL.utilPrev(i), { digits: D1, auto: "utilPrev" })}
        ${inputCell(COL.utilToday(i), { digits: D1 })}
        ${calcCell(`utilAcc:${i}`, { digits: D1 })}
      </tr>`).join("");
    return `
      <table class="fdl-table">
        <colgroup><col style="width:28%" /><col style="width:24%" /><col style="width:24%" /><col style="width:24%" /></colgroup>
        <thead><tr><th>구 분</th><th>전일 사용량</th><th>금일 사용량</th><th>누 계</th></tr></thead>
        <tbody>
          ${rows}
          <tr class="total-row">
            <th class="rowhead">계</th>
            ${calcCell("utilPrevSum", { digits: D1 })}
            ${calcCell("utilTodaySum", { digits: D1 })}
            ${calcCell("utilAccSum", { digits: D1 })}
          </tr>
        </tbody>
      </table>`;
  }

  function renderTables() {
    document.getElementById("fdlBurn").innerHTML = renderBurn();
    document.getElementById("fdlBoiler").innerHTML = renderBoiler();
    document.getElementById("fdlChem").innerHTML = renderChem();
    document.getElementById("fdlAsh").innerHTML = renderAsh();
    document.getElementById("fdlPower").innerHTML = renderPower();
    document.getElementById("fdlUtil").innerHTML = renderUtil();
    bindTableInputs();
  }

  // ── 값 표시 / 계산 ───────────────────────────────────────────
  function headerFields() {
    return [
      ["fdlWorkerAm", COL.workerAm], ["fdlWorkerPm", COL.workerPm], ["fdlWorkerNight", COL.workerNight],
      ["fdlApprLeader", COL.apprLeader], ["fdlApprAssistant", COL.apprAssistant], ["fdlApprManager", COL.apprManager],
    ];
  }

  function fillInputs() {
    const monthKey = currentMonthKey();
    const day = currentDay();

    headerFields().forEach(([id, col]) => {
      const field = document.getElementById(id);
      if (field) field.value = getValue(monthKey, day, col);
    });

    document.querySelectorAll(".fdl-table input[data-col]").forEach((input) => {
      if (document.activeElement === input) return;
      const col = Number(input.dataset.col);
      const digits = Number(input.dataset.digits || 0);
      const stored = getValue(monthKey, day, col);
      if (stored !== "") {
        input.dataset.autoValue = "";
        input.value = formatNumber(stored, digits) || stored;
        input.classList.remove("is-auto");
        return;
      }
      const auto = autoValueFor(input, monthKey, day);
      input.dataset.autoValue = auto === null ? "" : String(auto);
      input.value = auto === null ? "" : formatNumber(auto, digits);
      input.classList.toggle("is-auto", auto !== null);
    });
  }

  function autoValueFor(input, monthKey, day) {
    const kind = input.dataset.autoKind;
    if (!kind) return null;
    const col = Number(input.dataset.col);
    if (kind === "chemPrev") {
      const i = Math.floor((col - 20) / 3);
      const ref = previousDayRef(monthKey, day);
      if (!loadedMonths.has(ref.monthKey)) return null;
      return computeStocks(ref.monthKey)[ref.day]?.[i] ?? null;
    }
    if (kind === "powerPrev") {
      const i = Math.floor((col - 60) / 2);
      const ref = previousDayRef(monthKey, day);
      if (!loadedMonths.has(ref.monthKey)) return null;
      return getNumber(ref.monthKey, ref.day, COL.powerToday(i));
    }
    if (kind === "utilPrev") {
      const i = Math.floor((col - 90) / 2);
      const ref = previousDayRef(monthKey, day);
      if (!loadedMonths.has(ref.monthKey)) return null;
      return getNumber(ref.monthKey, ref.day, COL.utilToday(i));
    }
    return null;
  }

  function computeAll() {
    const monthKey = currentMonthKey();
    const day = currentDay();
    const values = {};

    values.burnTotal = burnTotal(monthKey, day);
    values.burnSrfAcc = accumulateCol(monthKey, day, COL.srf);
    values.burnSludgeAcc = accumulateCol(monthKey, day, COL.sludge);
    values.burnTotalAcc = accumulate(monthKey, day, burnTotal);

    values.steamProdAcc = accumulateCol(monthKey, day, COL.steamProd);
    values.steamSendAcc = accumulateCol(monthKey, day, COL.steamSend);
    values.runHoursAcc = accumulateCol(monthKey, day, COL.runHours);

    CHEMICALS.forEach((_label, i) => {
      values[`chemStock:${i}`] = chemStock(monthKey, day, i);
      values[`chemInAcc:${i}`] = accumulateCol(monthKey, day, COL.chemIn(i));
      values[`chemUseAcc:${i}`] = accumulateCol(monthKey, day, COL.chemUse(i));
    });

    values.ashFlyAcc = accumulateCol(monthKey, day, COL.ashFly);
    values.ashBottomAcc = accumulateCol(monthKey, day, COL.ashBottom);
    values.incombTotal = incombTotal(monthKey, day);
    values.incombAcc = accumulate(monthKey, day, incombTotal);

    POWERS.forEach((_label, i) => {
      values[`powerUse:${i}`] = powerUsed(monthKey, day, i);
      values[`powerAcc:${i}`] = accumulate(monthKey, day, (mk, d) => powerUsed(mk, d, i));
    });

    let utilPrevSum = null;
    let utilTodaySum = null;
    let utilAccSum = null;
    UTILS.forEach((_label, i) => {
      const accumulated = accumulateCol(monthKey, day, COL.utilToday(i));
      values[`utilAcc:${i}`] = accumulated;
      const prev = autoUtilPrev(monthKey, day, i);
      const today = getNumber(monthKey, day, COL.utilToday(i));
      if (prev !== null) utilPrevSum = (utilPrevSum || 0) + prev;
      if (today !== null) utilTodaySum = (utilTodaySum || 0) + today;
      if (accumulated !== null) utilAccSum = (utilAccSum || 0) + accumulated;
    });
    values.utilPrevSum = utilPrevSum;
    values.utilTodaySum = utilTodaySum;
    values.utilAccSum = utilAccSum;

    document.querySelectorAll(".fdl-table td[data-calc]").forEach((cell) => {
      const value = values[cell.dataset.calc];
      cell.textContent = value === null || value === undefined ? "" : formatNumber(value, Number(cell.dataset.digits || 0));
    });
  }

  function refresh() {
    invalidateStocks();
    fillInputs();
    computeAll();
    if (activeTab === "chem") renderChemSummary();
    if (activeTab === "etc") renderEtcSummary();
    if (activeTab === "moisture") renderMoisture();
  }

  // ── 저장 ────────────────────────────────────────────────────
  async function upsert(payload, existingId) {
    const url = existingId ? `${TABLES_BASE}/${CELL_TABLE}/${existingId}` : `${TABLES_BASE}/${CELL_TABLE}`;
    const response = await fetch(url, {
      method: existingId ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!response.ok) throw new Error("저장에 실패했습니다.");
    return response.json();
  }

  function periodFields(monthKey) {
    const [year, month] = monthKey.split("-");
    return { month: monthKey, year_no: Number(year), month_no: Number(month) };
  }

  // 세부 운영내역 셀 저장 (row_key = 셀 참조, col_index = 0)
  async function saveDetailCell(monthKey, day, row, value) {
    const ref = detailRef(day, row);
    const key = detailCacheKey(monthKey, ref);
    if (value === "") detailCache.delete(key);
    else detailCache.set(key, value);

    const saved = await upsert({
      ...periodFields(monthKey),
      table_name: DETAIL_TABLE,
      row_key: ref,
      col_index: 0,
      cell_value: value === "" ? null : value,
    }, detailIds.get(key));
    if (saved && saved.id) detailIds.set(key, saved.id);
  }

  // 스팀 송기량 저장 (세부 7행에 연결된 flow 셀)
  async function saveFlowCell(monthKey, day, value) {
    const key = flowCacheKey(monthKey, day);
    if (value === "") flowCache.delete(key);
    else flowCache.set(key, value);

    const saved = await upsert({
      ...periodFields(monthKey),
      table_name: FLOW_TABLE,
      row_key: String(day).padStart(2, "0"),
      col_index: 0,
      cell_value: value === "" ? null : value,
    }, flowIds.get(key));
    if (saved && saved.id) flowIds.set(key, saved.id);
  }

  // 전력 사용량(= 금일지침 − 전일지침)을 세부 운영내역 전력량 행에 반영
  async function syncPowerUsage(monthKey, day, i) {
    const row = POWER_DETAIL_ROWS[i];
    if (row === undefined) return;
    const used = powerUsed(monthKey, day, i);
    await saveDetailCell(monthKey, day, row, used === null ? "" : String(roundNoise(used)));
  }

  async function saveCell(col, value, dayOverride) {
    const monthKey = currentMonthKey();
    const day = dayOverride ?? currentDay();
    invalidateStocks();

    if (col === COL.steamSend) {
      await saveFlowCell(monthKey, day, value);
      return;
    }
    const mapped = DETAIL_MAP.get(col);
    if (mapped) {
      await saveDetailCell(monthKey, day, mapped.row, toDetail(value, mapped.scale));
      return;
    }

    const key = cacheKey(monthKey, day, col);
    if (value === "") cache.delete(key);
    else cache.set(key, value);

    const saved = await upsert({
      ...periodFields(monthKey),
      table_name: LOG_TABLE,
      row_key: String(day),
      col_index: col,
      cell_value: value === "" ? null : value,
    }, rowIds.get(key));
    if (saved && saved.id) rowIds.set(key, saved.id);

    // 지침을 고치면 세부 운영내역의 전력 사용량도 함께 갱신
    for (let i = 0; i < POWER_DETAIL_ROWS.length; i += 1) {
      if (col === COL.powerPrev(i) || col === COL.powerToday(i)) {
        await syncPowerUsage(monthKey, day, i);
        break;
      }
    }
  }

  // ── 저장 대기 (저장 버튼을 누르기 전까지 화면에만 반영) ─────
  function stageValue(col, value, dayOverride) {
    const monthKey = currentMonthKey();
    const day = dayOverride ?? currentDay();

    // 계산·표시가 즉시 갱신되도록 로컬 캐시에 먼저 반영한다.
    if (col === COL.steamSend) {
      const key = flowCacheKey(monthKey, day);
      if (value === "") flowCache.delete(key); else flowCache.set(key, value);
    } else {
      const mapped = DETAIL_MAP.get(col);
      if (mapped) {
        const key = detailCacheKey(monthKey, detailRef(day, mapped.row));
        const converted = toDetail(value, mapped.scale);
        if (converted === "") detailCache.delete(key); else detailCache.set(key, converted);
      } else {
        const key = cacheKey(monthKey, day, col);
        if (value === "") cache.delete(key); else cache.set(key, value);
      }
    }
    invalidateStocks();

    pending.set(`${monthKey}|${day}|${col}`, { monthKey, day, col, value });
    updateSaveState();
  }

  function updateSaveState() {
    if (!saveButton) return;
    const count = pending.size;
    saveButton.disabled = count === 0;
    saveButton.textContent = count === 0 ? "저장" : `저장 (${count})`;
    document.body.classList.toggle("fdl-dirty", count > 0);
    if (count > 0) setMessage(`미저장 ${count}건`);
  }

  async function saveAll() {
    if (!pending.size) return true;
    saveButton.disabled = true;
    setMessage("저장 중");
    try {
      for (const item of Array.from(pending.values())) {
        if (item.monthKey !== currentMonthKey()) continue;
        await saveCell(item.col, item.value, item.day);
      }
      pending.clear();
      updateSaveState();
      setMessage("저장 완료", "ok");
      refresh();
      return true;
    } catch (error) {
      setMessage(error.message || "저장 실패", "err");
      window.alert(error.message || "저장에 실패했습니다.");
      return false;
    } finally {
      saveButton.disabled = pending.size === 0;
    }
  }

  // 저장하지 않은 변경을 버리고 서버 값으로 되돌린다.
  async function discardPending() {
    pending.clear();
    updateSaveState();
    const monthKey = currentMonthKey();
    [monthKey, shiftMonth(monthKey, -1)].forEach((key) => {
      loadedMonths.delete(key);
      [cache, rowIds, detailCache, detailIds, flowCache, flowIds].forEach((store) => {
        Array.from(store.keys()).forEach((entry) => { if (entry.startsWith(`${key}|`)) store.delete(entry); });
      });
    });
    await load();
  }

  /** 다른 날짜·탭·페이지로 넘어가기 전 확인. 계속해도 되면 true */
  async function confirmLeave() {
    if (!pending.size) return true;
    if (window.confirm(`저장하지 않은 변경이 ${pending.size}건 있습니다.\n\n[확인] 저장하고 계속\n[취소] 저장 여부를 다시 묻습니다`)) {
      return await saveAll();
    }
    if (window.confirm("저장하지 않고 진행할까요?\n입력한 내용이 사라집니다.")) {
      await discardPending();
      return true;
    }
    return false;
  }

  saveButton?.addEventListener("click", () => { void saveAll(); });
  window.addEventListener("beforeunload", (event) => {
    if (!pending.size) return;
    event.preventDefault();
    event.returnValue = "";
  });

  function handleFieldCommit(input, col) {
    const raw = String(input.value ?? "").replace(/,/g, "").trim();
    const stored = getValue(currentMonthKey(), currentDay(), col);
    // 전일 이월 자동값을 그대로 둔 경우: 저장하지 않고 자동값 상태를 유지한다.
    const autoValue = input.dataset.autoValue || "";
    if (stored === "" && autoValue !== "" && raw === autoValue) {
      refresh();
      return;
    }
    if (raw === stored) {
      refresh();
      return;
    }
    stageValue(col, raw);
    input.classList.add("is-edited");
    refresh();
  }

  function bindTableInputs() {
    document.querySelectorAll(".fdl-table input[data-col]").forEach((input) => {
      const col = Number(input.dataset.col);
      input.addEventListener("focus", () => {
        const stored = getValue(currentMonthKey(), currentDay(), col);
        input.value = stored !== "" ? stored : (input.dataset.autoValue || "");
      });
      input.addEventListener("blur", () => {
        handleFieldCommit(input, col);
      });
      input.addEventListener("keydown", (event) => {
        if (event.key === "Enter") input.blur();
      });
    });
  }

  function bindHeaderFields() {
    headerFields().forEach(([id, col]) => {
      const field = document.getElementById(id);
      if (!field) return;
      field.addEventListener("change", () => {
        const raw = String(field.value ?? "").trim();
        if (raw === getValue(currentMonthKey(), currentDay(), col)) return;
        stageValue(col, raw);
        field.classList.add("is-edited");
      });
    });
  }

  // ── 조회 / 로딩 ─────────────────────────────────────────────
  async function fetchMonth(monthKey) {
    if (loadedMonths.has(monthKey)) return;
    const limit = 1000;
    let pageNo = 1;
    let totalPages = 1;
    const tableNames = [LOG_TABLE, DETAIL_TABLE, FLOW_TABLE].join(",");
    while (pageNo <= totalPages) {
      const url = `${TABLES_BASE}/${CELL_TABLE}?page=${pageNo}&limit=${limit}` +
        `&month=${encodeURIComponent(monthKey)}&tableNames=${encodeURIComponent(tableNames)}`;
      const response = await fetch(url);
      if (!response.ok) throw new Error("데이터를 불러오지 못했습니다.");
      const payload = await response.json();
      const rows = Array.isArray(payload?.data) ? payload.data : [];
      rows.forEach((row) => {
        const value = row.cell_value === null || row.cell_value === undefined ? "" : String(row.cell_value);
        if (row.table_name === LOG_TABLE) {
          const key = cacheKey(monthKey, Number(row.row_key), Number(row.col_index));
          if (value !== "") cache.set(key, value);
          rowIds.set(key, row.id);
          return;
        }
        if (row.table_name === DETAIL_TABLE && Number(row.col_index) === 0) {
          const key = detailCacheKey(monthKey, String(row.row_key));
          if (value !== "") detailCache.set(key, value);
          detailIds.set(key, row.id);
          return;
        }
        if (row.table_name === FLOW_TABLE && Number(row.col_index) === 0) {
          const key = flowCacheKey(monthKey, Number(row.row_key));
          if (value !== "") flowCache.set(key, value);
          flowIds.set(key, row.id);
        }
      });
      const total = Number(payload?.total || rows.length);
      totalPages = Math.max(1, Math.ceil(total / Number(payload?.limit || limit)));
      pageNo += 1;
    }
    loadedMonths.add(monthKey);
    invalidateStocks();
  }

  async function load() {
    setMessage("불러오는 중");
    try {
      const monthKey = currentMonthKey();
      await fetchMonth(shiftMonth(monthKey, -1));
      await fetchMonth(monthKey);
      updateSheetDate();
      refresh();
      setMessage("");
    } catch (error) {
      setMessage(error.message || "조회 실패", "err");
    }
  }

  function updateSheetDate() {
    if (!sheetDate) return;
    const day = String(currentDay()).padStart(2, "0");
    sheetDate.textContent = `${yearSelect.value}년 ${Number(monthSelect.value)}월 ${Number(day)}일`;
  }

  function populateDays(preferredDay) {
    const total = daysInMonth(currentMonthKey());
    const target = Math.min(Math.max(Number(preferredDay || 1), 1), total);
    daySelect.innerHTML = "";
    for (let day = 1; day <= total; day += 1) {
      const option = document.createElement("option");
      option.value = String(day);
      option.textContent = `${day}일`;
      option.selected = day === target;
      daySelect.appendChild(option);
    }
  }

  function loadSavedPeriod() {
    const today = new Date();
    let year = String(today.getFullYear());
    let month = String(today.getMonth() + 1).padStart(2, "0");
    try {
      const raw = localStorage.getItem(PERIOD_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (/^\d{4}$/.test(String(parsed?.year || ""))) year = String(parsed.year);
        if (/^\d{2}$/.test(String(parsed?.month || ""))) month = String(parsed.month);
      }
    } catch (_error) { /* 기본값 사용 */ }
    return { year, month };
  }

  function persistPeriod() {
    localStorage.setItem(PERIOD_KEY, JSON.stringify({ year: yearSelect.value, month: monthSelect.value }));
  }

  function populatePeriodSelects() {
    const saved = loadSavedPeriod();
    const currentYear = new Date().getFullYear();
    yearSelect.innerHTML = "";
    monthSelect.innerHTML = "";
    for (let value = currentYear + 1; value >= currentYear - 5; value -= 1) {
      const option = document.createElement("option");
      option.value = String(value);
      option.textContent = `${value}년`;
      option.selected = option.value === saved.year;
      yearSelect.appendChild(option);
    }
    for (let value = 1; value <= 12; value += 1) {
      const option = document.createElement("option");
      option.value = String(value).padStart(2, "0");
      option.textContent = `${value}월`;
      option.selected = option.value === saved.month;
      monthSelect.appendChild(option);
    }
    const today = new Date();
    const isThisMonth = Number(yearSelect.value) === today.getFullYear() && Number(monthSelect.value) === today.getMonth() + 1;
    populateDays(isThisMonth ? today.getDate() : 1);
  }

  function moveDay(delta) {
    const total = daysInMonth(currentMonthKey());
    const next = currentDay() + delta;
    if (next >= 1 && next <= total) {
      daySelect.value = String(next);
      updateSheetDate();
      refresh();
      return;
    }
    const monthKey = shiftMonth(currentMonthKey(), delta);
    const [year, month] = monthKey.split("-");
    if (!Array.from(yearSelect.options).some((option) => option.value === year)) return;
    yearSelect.value = year;
    monthSelect.value = month;
    persistPeriod();
    populateDays(delta > 0 ? 1 : daysInMonth(monthKey));
    void load();
  }

  // 조회 조건을 바꾸기 전 상태 — 확인에서 취소하면 되돌린다.
  let lastPeriod = { year: yearSelect.value, month: monthSelect.value, day: daySelect.value };
  function rememberPeriod() {
    lastPeriod = { year: yearSelect.value, month: monthSelect.value, day: daySelect.value };
  }
  function restorePeriod() {
    yearSelect.value = lastPeriod.year;
    monthSelect.value = lastPeriod.month;
    populateDays(Number(lastPeriod.day));
    daySelect.value = lastPeriod.day;
    updateSheetDate();
  }

  yearSelect.addEventListener("change", async () => {
    if (!(await confirmLeave())) { restorePeriod(); return; }
    persistPeriod();
    populateDays(currentDay());
    rememberPeriod();
    await load();
  });
  monthSelect.addEventListener("change", async () => {
    if (!(await confirmLeave())) { restorePeriod(); return; }
    persistPeriod();
    populateDays(currentDay());
    rememberPeriod();
    await load();
  });
  daySelect.addEventListener("change", async () => {
    if (!(await confirmLeave())) { restorePeriod(); return; }
    rememberPeriod();
    updateSheetDate();
    refresh();
  });
  prevDayButton?.addEventListener("click", async () => {
    if (!(await confirmLeave())) return;
    moveDay(-1);
    rememberPeriod();
  });
  nextDayButton?.addEventListener("click", async () => {
    if (!(await confirmLeave())) return;
    moveDay(1);
    rememberPeriod();
  });
  reloadButton?.addEventListener("click", async () => {
    if (!(await confirmLeave())) return;
    [currentMonthKey(), shiftMonth(currentMonthKey(), -1)].forEach((monthKey) => {
      loadedMonths.delete(monthKey);
      [cache, rowIds, detailCache, detailIds, flowCache, flowIds].forEach((store) => {
        Array.from(store.keys()).forEach((key) => { if (key.startsWith(`${monthKey}|`)) store.delete(key); });
      });
    });
    await load();
  });

  // ── 탭 / 월 요약 ────────────────────────────────────────────
  const tabsRoot = document.getElementById("fdlTabs");
  const chemSummaryRoot = document.getElementById("fdlChemSummary");
  const etcSummaryRoot = document.getElementById("fdlEtcSummary");
  const moistureRoot = document.getElementById("fdlMoisture");

  // 요약(약품) 행 구성 — 세부 운영내역 약품 블록과 동일 순서. i = CHEMICALS 인덱스.
  const CHEM_SUMMARY_ROWS = [
    { i: 0, label: "탄산암모늄", unit: "KG", stock: true },
    { i: 1, label: "청관제", unit: "KG", stock: true },
    { i: 2, label: "활성탄", unit: "KG", stock: true },
    { i: 3, label: "가성소다", unit: "KG", stock: true },
    { i: 4, label: "소석회", unit: "KG", stock: true },
    { i: 5, label: "소금", unit: "KG", stock: true },
    { i: 6, label: "경유", unit: "L", stock: true },
    { i: 8, label: "규사", unit: "KG", stock: false },
    { i: 7, label: "SRF", unit: "KG", stock: true },
  ];

  // 월 요약 표 껍데기 (열 폭 고정 + 상단/좌측 틀고정).
  // table-layout:fixed 는 첫 행 셀 폭을 기준으로 잡으므로 헤더에 폭을 직접 지정하고,
  // 표 전체 폭도 열 폭 합계로 명시해야 남는 폭이 특정 열에 몰리지 않는다.
  const SUM_NAME_W = 104;
  const SUM_DAY_W = 64;
  const SUM_TOTAL_W = 82;

  // 카드 폭에 여유가 있으면 일자 열을 넓혀 채우고, 모자라면 SUM_DAY_W 를 유지한 채 가로 스크롤한다.
  const SUM_DAY_MAX = 90;
  function resolveDayWidth(root, days, kindWidth) {
    const available = root?.clientWidth || 0;
    if (!available) return SUM_DAY_W;
    const fixed = SUM_NAME_W + kindWidth + SUM_TOTAL_W * 2;
    const fit = Math.floor((available - fixed - 2) / days);
    return Math.min(SUM_DAY_MAX, Math.max(SUM_DAY_W, fit));
  }

  // 요약 표 영역을 화면 아래까지 채워 바깥 스크롤이 생기지 않게 한다.
  function fitSummaryHeight(wrap) {
    if (!wrap) return;
    const top = wrap.getBoundingClientRect().top;
    wrap.style.maxHeight = `${Math.max(240, Math.floor(window.innerHeight - top - 16))}px`;
    // 아래쪽 여백(카드/본문 패딩)만큼 남으면 바깥 스크롤이 생기므로 실제 넘친 만큼 한 번 더 줄인다.
    const overflow = document.documentElement.scrollHeight - window.innerHeight;
    if (overflow > 0) {
      const corrected = parseFloat(wrap.style.maxHeight) - overflow;
      wrap.style.maxHeight = `${Math.max(240, Math.floor(corrected))}px`;
    }
  }

  function monthGridTable(dayList, body, kindWidth, dayWidth) {
    const dayW = dayWidth || SUM_DAY_W;
    const width = SUM_NAME_W + kindWidth + dayList.length * dayW + SUM_TOTAL_W * 2;
    const colgroup = `
      <colgroup>
        <col style="width:${SUM_NAME_W}px" />
        <col style="width:${kindWidth}px" />
        ${dayList.map(() => `<col style="width:${dayW}px" />`).join("")}
        <col style="width:${SUM_TOTAL_W}px" />
        <col style="width:${SUM_TOTAL_W}px" />
      </colgroup>`;
    const head = `
      <thead>
        <tr>
          <th class="name-col" style="width:${SUM_NAME_W}px">구 분</th>
          <th class="kind-col" style="width:${kindWidth}px;left:${SUM_NAME_W}px"></th>
          ${dayList.map((day) => `<th style="width:${dayW}px">${day}</th>`).join("")}
          <th class="total-head" style="width:${SUM_TOTAL_W}px">합계</th>
          <th class="total-head" style="width:${SUM_TOTAL_W}px">평균</th>
        </tr>
      </thead>`;
    return `<table class="fdl-sum-table" data-no-resize style="width:${width}px">${colgroup}${head}<tbody>${body}</tbody></table>`;
  }

  function renderChemSummary() {
    if (!chemSummaryRoot) return;
    const monthKey = currentMonthKey();
    const days = daysInMonth(monthKey);
    const dayList = Array.from({ length: days }, (_unused, index) => index + 1);

    const body = CHEM_SUMMARY_ROWS.map((def) => {
      const kinds = def.stock ? ["in", "use", "stock"] : ["in", "use"];
      return kinds.map((kind, kindIndex) => {
        const nameCell = kindIndex === 0
          ? `<th class="name-col" rowspan="${kinds.length}">${escapeHtml(def.label)}<br>(${def.unit})</th>`
          : "";
        const kindLabel = kind === "in" ? "반입" : (kind === "use" ? "사용" : "재고");

        let cells = "";
        let total = null;
        if (kind === "stock") {
          cells = dayList.map((day) => {
            const value = chemStock(monthKey, day, def.i);
            if (value !== null) total = (total || 0) + value;
            return `<td class="calc">${value === null ? "" : formatNumber(value, D0)}</td>`;
          }).join("");
          // 재고는 일별 잔량이라 합계를 쓰지 않고 평균(일평균 재고)만 표기한다.
          cells += `<td class="total-cell"></td>`;
          cells += `<td class="total-cell">${total === null ? "" : formatNumber(total / days, D0)}</td>`;
        } else {
          const col = kind === "in" ? COL.chemIn(def.i) : COL.chemUse(def.i);
          cells = dayList.map((day) => {
            const raw = getValue(monthKey, day, col);
            const number = parseNumber(raw);
            if (number !== null) total = (total || 0) + number;
            return `<td class="calc">${escapeHtml(number === null ? raw : formatNumber(number, D0))}</td>`;
          }).join("");
          cells += `<td class="total-cell">${total === null ? "" : formatNumber(total, D0)}</td>`;
          cells += `<td class="total-cell">${total === null ? "" : formatNumber(total / days, D0)}</td>`;
        }
        return `<tr>${nameCell}<th class="kind-col">${kindLabel}</th>${cells}</tr>`;
      }).join("");
    }).join("");

    chemSummaryRoot.innerHTML = monthGridTable(dayList, body, 52, resolveDayWidth(chemSummaryRoot.parentElement, days, 52));
    fitSummaryHeight(chemSummaryRoot.parentElement);
  }

  // 요약(그외) 행 구성 — 엑셀 "요약(그외)" 시트와 동일.
  // get 이 null 을 돌려주는 항목은 운전일지에 대응 입력이 아직 없는 자리다.
  const ETC_SUMMARY_ROWS = [
    { group: "가동시간", label: "", digits: D1, get: (m, d) => getNumber(m, d, COL.runHours) },
    { group: "소각량", label: "SRF", digits: D0, get: (m, d) => getNumber(m, d, COL.srf) },
    { group: "소각량", label: "미계근", digits: D0, get: () => null },
    { group: "소각량", label: "합계", digits: D0, get: (m, d) => getNumber(m, d, COL.srf) },
    { group: "소각량", label: "Sludge", digits: D0, get: (m, d) => getNumber(m, d, COL.sludge) },
    { group: "소각량", label: "혼합연료", digits: D1, get: () => null },
    { group: "소각량", label: "자동호퍼", digits: D0, get: () => null },
    { group: "스팀량", label: "FIT-301", digits: D1, get: (m, d) => getNumber(m, d, COL.steamProd) },
    { group: "스팀량", label: "FIT-304", digits: D1, get: (m, d) => getNumber(m, d, COL.steamSend) },
    { group: "소각재", label: "비산재(반출)", digits: D2, get: (m, d) => getNumber(m, d, COL.ashFly) },
    { group: "소각재", label: "바닥재(발생)", digits: D0, get: (m, d) => incombTotal(m, d) },
    { group: "소각재", label: "바닥재(반출)", digits: D2, get: (m, d) => getNumber(m, d, COL.ashBottom) },
    { group: "유틸리티", label: "재이용수", digits: D1, get: (m, d) => getNumber(m, d, COL.utilToday(0)) },
    { group: "유틸리티", label: "복류수", digits: D1, get: (m, d) => getNumber(m, d, COL.utilToday(1)) },
    { group: "유틸리티", label: "응축수", digits: D1, get: (m, d) => getNumber(m, d, COL.utilToday(2)) },
    { group: "유틸리티", label: "보일러급수", digits: D1, get: (m, d) => getNumber(m, d, COL.utilToday(3)) },
    { group: "유틸리티", label: "연수", digits: D1, get: (m, d) => getNumber(m, d, COL.utilToday(5)) },
    { group: "유틸리티", label: "폐수", digits: D1, get: (m, d) => getNumber(m, d, COL.utilToday(4)) },
    { group: "전력량", label: "Main", digits: D1, get: (m, d) => powerUsed(m, d, 0) },
    { group: "전력량", label: "Ash 밴트필터", digits: D1, get: (m, d) => powerUsed(m, d, 1) },
    { group: "전력량", label: "백필터", digits: D1, get: (m, d) => powerUsed(m, d, 2) },
    { group: "전력량", label: "스크러버", digits: D1, get: (m, d) => powerUsed(m, d, 3) },
    { group: "전력량", label: "활성탄공급", digits: D1, get: (m, d) => powerUsed(m, d, 4) },
    { group: "전력량", label: "소석회공급", digits: D1, get: (m, d) => powerUsed(m, d, 5) },
    { group: "전력량", label: "요소수", digits: D1, get: (m, d) => powerUsed(m, d, 6) },
    { group: "TMS OVER", label: "NOX OVER", digits: D2, get: () => null },
  ];

  function renderEtcSummary() {
    if (!etcSummaryRoot) return;
    const monthKey = currentMonthKey();
    const days = daysInMonth(monthKey);
    const dayList = Array.from({ length: days }, (_unused, index) => index + 1);

    const body = ETC_SUMMARY_ROWS.map((def, index) => {
      const isGroupStart = index === 0 || ETC_SUMMARY_ROWS[index - 1].group !== def.group;
      const span = ETC_SUMMARY_ROWS.filter((row) => row.group === def.group).length;
      const nameCell = isGroupStart
        ? `<th class="name-col" rowspan="${span}">${escapeHtml(def.group)}</th>`
        : "";

      let total = null;
      const cells = dayList.map((day) => {
        const value = def.get(monthKey, day);
        if (value !== null && value !== undefined) total = (total || 0) + value;
        return `<td class="calc">${value === null || value === undefined ? "" : formatNumber(value, def.digits)}</td>`;
      }).join("");

      return `<tr>${nameCell}<th class="kind-col">${escapeHtml(def.label)}</th>${cells}` +
        `<td class="total-cell">${total === null ? "" : formatNumber(total, def.digits)}</td>` +
        `<td class="total-cell">${total === null ? "" : formatNumber(total / days, def.digits)}</td></tr>`;
    }).join("");

    etcSummaryRoot.innerHTML = monthGridTable(dayList, body, 96, resolveDayWidth(etcSummaryRoot.parentElement, days, 96));
    fitSummaryHeight(etcSummaryRoot.parentElement);
  }

  // ── 함수율 ─────────────────────────────────────────────────
  // 엑셀 "함수율" 시트와 같은 구성: 날짜 · 근무 · 탈수기 5대(취득시간/함수율) · 근무 평균 · 일 평균
  let moistView = "day"; // day | month

  function moistShiftAverage(monthKey, day, shift) {
    let sum = 0;
    let count = 0;
    MOIST_MACHINES.forEach((_label, machine) => {
      const value = getNumber(monthKey, day, COL.moistValue(shift, machine));
      if (value === null) return;
      sum += value;
      count += 1;
    });
    return count ? sum / count : null;
  }

  function moistDayAverage(monthKey, day) {
    let sum = 0;
    let count = 0;
    MOIST_SHIFTS.forEach((_label, shift) => {
      const value = moistShiftAverage(monthKey, day, shift);
      if (value === null) return;
      sum += value;
      count += 1;
    });
    return count ? sum / count : null;
  }

  const MOIST_NAME_W = 104;
  const MOIST_KIND_W = 56;
  const MOIST_AVG_W = 82;
  const MOIST_DATA_MIN = 74;

  // 카드 폭에 맞춰 데이터 열(취득시간/함수율 10칸)을 늘려 가로를 꽉 채운다.
  function moistDataWidth(root) {
    const available = root?.clientWidth || 0;
    const fixed = MOIST_NAME_W + MOIST_KIND_W + MOIST_AVG_W * 2;
    const count = MOIST_MACHINES.length * 2;
    if (!available) return MOIST_DATA_MIN;
    return Math.max(MOIST_DATA_MIN, Math.floor((available - fixed - 2) / count));
  }

  function moistColgroup(dataWidth) {
    return `
      <colgroup>
        <col style="width:${MOIST_NAME_W}px" />
        <col style="width:${MOIST_KIND_W}px" />
        ${MOIST_MACHINES.map(() => `<col style="width:${dataWidth}px" /><col style="width:${dataWidth}px" />`).join("")}
        <col style="width:${MOIST_AVG_W}px" />
        <col style="width:${MOIST_AVG_W}px" />
      </colgroup>`;
  }

  function moistHead(dataWidth) {
    return `
      <thead>
        <tr>
          <th class="name-col" rowspan="2" style="width:${MOIST_NAME_W}px">날 짜</th>
          <th class="kind-col" rowspan="2" style="width:${MOIST_KIND_W}px;left:${MOIST_NAME_W}px">근무</th>
          ${MOIST_MACHINES.map((label) => `<th colspan="2">${escapeHtml(label)}</th>`).join("")}
          <th class="total-head" rowspan="2" style="width:${MOIST_AVG_W}px">근무 평균</th>
          <th class="total-head" rowspan="2" style="width:${MOIST_AVG_W}px">일 평균</th>
        </tr>
        <tr>
          ${MOIST_MACHINES.map(() => `<th style="width:${dataWidth}px">취득시간</th><th style="width:${dataWidth}px">함수율(%)</th>`).join("")}
        </tr>
      </thead>`;
  }

  // editable = true 면 입력칸, false 면 표시 전용
  function moistDayRows(monthKey, day, editable, showDate) {
    const dayAverage = moistDayAverage(monthKey, day);
    return MOIST_SHIFTS.map((shiftLabel, shift) => {
      const dateCell = shift === 0
        ? `<th class="name-col" rowspan="3">${showDate ? `${Number(monthKey.split("-")[1])}/${day}` : `${monthKey}-${String(day).padStart(2, "0")}`}</th>`
        : "";
      const cells = MOIST_MACHINES.map((_label, machine) => {
        const timeCol = COL.moistTime(shift, machine);
        const valueCol = COL.moistValue(shift, machine);
        const timeValue = getValue(monthKey, day, timeCol);
        const rawValue = getValue(monthKey, day, valueCol);
        if (!editable) {
          return `<td class="calc">${escapeHtml(timeValue)}</td><td class="calc">${escapeHtml(rawValue)}</td>`;
        }
        return `<td><input class="moist-text" data-moist-col="${timeCol}" data-moist-day="${day}" placeholder="--:--" autocomplete="off" value="${escapeHtml(timeValue)}" /></td>` +
          `<td><input data-moist-col="${valueCol}" data-moist-day="${day}" inputmode="decimal" autocomplete="off" value="${escapeHtml(rawValue)}" /></td>`;
      }).join("");
      const shiftAverage = moistShiftAverage(monthKey, day, shift);
      const dayCell = shift === 0
        ? `<td class="total-cell" rowspan="3" data-moist-day-avg="${day}">${dayAverage === null ? "" : formatNumber(dayAverage, D2)}</td>`
        : "";
      return `<tr>${dateCell}<th class="kind-col">${escapeHtml(shiftLabel)}</th>${cells}` +
        `<td class="total-cell" data-moist-shift-avg="${day}:${shift}">${shiftAverage === null ? "" : formatNumber(shiftAverage, D2)}</td>${dayCell}</tr>`;
    }).join("");
  }

  // skipCorrection: 세로 스크롤바가 생기면서 폭이 줄어든 경우 한 번만 다시 그린다.
  function renderMoisture(skipCorrection) {
    if (!moistureRoot) return;
    const monthKey = currentMonthKey();
    const body = moistView === "day"
      ? moistDayRows(monthKey, currentDay(), true, false)
      : Array.from({ length: daysInMonth(monthKey) }, (_unused, index) => index + 1)
          .map((day) => moistDayRows(monthKey, day, true, true)).join("");

    const dataWidth = moistDataWidth(moistureRoot.parentElement);
    const tableWidth = MOIST_NAME_W + MOIST_KIND_W + MOIST_MACHINES.length * 2 * dataWidth + MOIST_AVG_W * 2;
    moistureRoot.innerHTML = `
      <div class="fdl-subtabs">
        <button type="button" class="fdl-subtab${moistView === "day" ? " is-active" : ""}" data-moist-view="day">일간</button>
        <button type="button" class="fdl-subtab${moistView === "month" ? " is-active" : ""}" data-moist-view="month">월간</button>
      </div>
      <table class="fdl-sum-table" data-no-resize style="width:${tableWidth}px">
        ${moistColgroup(dataWidth)}${moistHead(dataWidth)}<tbody>${body}</tbody>
      </table>`;

    fitSummaryHeight(moistureRoot.parentElement);
    bindMoistInputs();

    if (!skipCorrection) {
      const wrap = moistureRoot.parentElement;
      const table = moistureRoot.querySelector(".fdl-sum-table");
      if (table && wrap && table.getBoundingClientRect().width > wrap.clientWidth + 1) renderMoisture(true);
    }
  }

  function bindMoistInputs() {
    moistureRoot.querySelectorAll("input[data-moist-col]").forEach((input) => {
      const col = Number(input.dataset.moistCol);
      const day = Number(input.dataset.moistDay);
      input.addEventListener("keydown", (event) => {
        if (event.key === "Enter") input.blur();
      });
      input.addEventListener("blur", () => {
        const raw = String(input.value ?? "").trim();
        if (raw === getValue(currentMonthKey(), day, col)) return;
        stageValue(col, raw, day);
        input.classList.add("is-edited");
        updateMoistAverages(day);
      });
    });
  }

  function updateMoistAverages(day) {
    const monthKey = currentMonthKey();
    MOIST_SHIFTS.forEach((_label, shift) => {
      const cell = moistureRoot.querySelector(`[data-moist-shift-avg="${day}:${shift}"]`);
      if (!cell) return;
      const value = moistShiftAverage(monthKey, day, shift);
      cell.textContent = value === null ? "" : formatNumber(value, D2);
    });
    const dayCell = moistureRoot.querySelector(`[data-moist-day-avg="${day}"]`);
    if (dayCell) {
      const value = moistDayAverage(monthKey, day);
      dayCell.textContent = value === null ? "" : formatNumber(value, D2);
    }
  }

  moistureRoot?.addEventListener("click", (event) => {
    const button = event.target.closest("[data-moist-view]");
    if (!button) return;
    moistView = button.dataset.moistView;
    renderMoisture();
  });

  function setTab(key) {
    activeTab = key;
    tabsRoot?.querySelectorAll(".fdl-tab").forEach((button) => {
      button.classList.toggle("is-active", button.dataset.fdlTab === key);
    });
    document.querySelectorAll("[data-fdl-panel]").forEach((panel) => {
      panel.hidden = panel.dataset.fdlPanel !== key;
    });
    if (key === "chem") renderChemSummary();
    if (key === "etc") renderEtcSummary();
    if (key === "moisture") renderMoisture();
  }

  // ── 엑셀 일괄 업로드 ────────────────────────────────────────
  // 파일명에서 연/월을 읽어 선택한 월과 다르면 먼저 확인을 받는다.
  function monthFromFileName(name) {
    const text = String(name || "");
    const withYear = text.match(/(20\d{2})\s*[.\-_년\s]*\s*(\d{1,2})\s*월/);
    if (withYear) return { year: Number(withYear[1]), month: Number(withYear[2]) };
    const yearMonth = text.match(/(20\d{2})[.\-_]?(0[1-9]|1[0-2])(?!\d)/);
    if (yearMonth) return { year: Number(yearMonth[1]), month: Number(yearMonth[2]) };
    const monthOnly = text.match(/(\d{1,2})\s*월/);
    if (monthOnly) return { year: null, month: Number(monthOnly[1]) };
    return null;
  }

  function confirmFileMonth(fileName) {
    const parsed = monthFromFileName(fileName);
    const year = Number(yearSelect.value);
    const month = Number(monthSelect.value);
    if (!parsed) {
      return window.confirm(`파일명에서 월을 확인하지 못했습니다.

${fileName}

${year}년 ${month}월 로 업로드할까요?`);
    }
    const yearMatches = parsed.year === null || parsed.year === year;
    if (yearMatches && parsed.month === month) return true;
    const fileLabel = parsed.year === null ? `${parsed.month}월` : `${parsed.year}년 ${parsed.month}월`;
    return window.confirm(
      `파일명은 ${fileLabel} 인데 선택한 조회 월은 ${year}년 ${month}월 입니다.

${fileName}

선택한 ${year}년 ${month}월 로 업로드할까요?`
    );
  }

  async function uploadWorkbook(file) {
    const form = new FormData();
    form.append("file", file);
    form.append("year", String(Number(yearSelect.value)));
    form.append("month", String(Number(monthSelect.value)));

    const response = await fetch(`${TABLES_BASE}/fluidized-daily-log/import-excel`, { method: "POST", body: form });
    if (!response.ok) {
      let message = "업로드에 실패했습니다.";
      try {
        const payload = await response.json();
        if (payload?.message) message = payload.message;
      } catch (_error) { /* 기본 메시지 사용 */ }
      throw new Error(message);
    }
    return response.json();
  }

  uploadButton?.addEventListener("click", () => uploadInput?.click());
  uploadInput?.addEventListener("change", async () => {
    const file = uploadInput.files?.[0];
    uploadInput.value = "";
    if (!file) return;
    if (!(await confirmLeave())) return;
    if (!confirmFileMonth(file.name)) return;

    uploadButton.disabled = true;
    setMessage("업로드 중");
    try {
      const result = await uploadWorkbook(file);
      // 업로드한 월을 다시 읽어온다.
      [currentMonthKey(), shiftMonth(currentMonthKey(), -1)].forEach((monthKey) => {
        loadedMonths.delete(monthKey);
        [cache, rowIds, detailCache, detailIds, flowCache, flowIds].forEach((store) => {
          Array.from(store.keys()).forEach((key) => { if (key.startsWith(`${monthKey}|`)) store.delete(key); });
        });
      });
      await load();
      const warnings = Array.isArray(result?.warnings) ? result.warnings : [];
      setMessage(`${result?.days ?? 0}일 반영`, "ok");
      if (warnings.length) {
        window.alert("업로드는 끝났지만 확인할 점이 있습니다.\n\n- " + warnings.join("\n- "));
      }
    } catch (error) {
      setMessage(error.message || "업로드 실패", "err");
      window.alert(error.message || "업로드에 실패했습니다.");
    } finally {
      uploadButton.disabled = false;
    }
  });

  // ── 엑셀 다운로드 ───────────────────────────────────────────
  // 원본 서식에 선택한 월의 값을 채워 내려받는다.
  downloadButton?.addEventListener("click", async () => {
    // 저장되지 않은 값은 파일에 담기지 않으므로 먼저 확인한다.
    if (!(await confirmLeave())) return;
    downloadButton.disabled = true;
    setMessage("내려받는 중");
    try {
      const year = Number(yearSelect.value);
      const month = Number(monthSelect.value);
      const response = await fetch(`${TABLES_BASE}/fluidized-daily-log/export-excel?year=${year}&month=${month}`);
      if (!response.ok) throw new Error("다운로드에 실패했습니다.");
      const blob = await response.blob();
      const link = document.createElement("a");
      link.href = URL.createObjectURL(blob);
      link.download = `유동상 운전일지 (${year}년 ${month}월).xlsx`;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.setTimeout(() => URL.revokeObjectURL(link.href), 1000);
      setMessage("다운로드 완료", "ok");
    } catch (error) {
      setMessage(error.message || "다운로드 실패", "err");
      window.alert(error.message || "다운로드에 실패했습니다.");
    } finally {
      downloadButton.disabled = false;
    }
  });

  // ── 인쇄 (A4 가로 1장) ──────────────────────────────────────
  // 인쇄 영역 = A4 가로(297×210mm) − 여백 8mm. 용지에 맞는 축소 배율을 계산해 CSS 변수로 넘긴다.
  function printDailySheet() {
    const previousTab = activeTab;
    if (previousTab !== "log") setTab("log");

    const sheet = document.querySelector('[data-fdl-panel="log"]');
    if (!sheet) return;
    const mm = 96 / 25.4;
    const pageWidth = (210 - 16) * mm;   // A4 세로 인쇄 폭
    const pageHeight = (297 - 16) * mm;  // A4 세로 인쇄 높이

    // 인쇄용 폭(980px)을 잠시 적용한 상태에서 실제 크기를 재고 축소 배율을 구한다.
    document.body.classList.add("fdl-print-measure");
    const sheetWidth = sheet.scrollWidth;
    const sheetHeight = sheet.scrollHeight;
    document.body.classList.remove("fdl-print-measure");

    const scale = Math.min(pageWidth / sheetWidth, pageHeight / sheetHeight, 1) * 0.99;
    document.body.style.setProperty("--fdl-print-scale", String(Math.floor(scale * 1000) / 1000));

    window.print();

    if (previousTab !== "log") setTab(previousTab);
  }

  printButton?.addEventListener("click", printDailySheet);

  let summaryResizeTimer = null;
  window.addEventListener("resize", () => {
    if (activeTab === "log") return;
    if (summaryResizeTimer) window.clearTimeout(summaryResizeTimer);
    summaryResizeTimer = window.setTimeout(() => {
      if (activeTab === "chem") renderChemSummary();
      if (activeTab === "etc") renderEtcSummary();
      if (activeTab === "moisture") renderMoisture();
    }, 150);
  });

  tabsRoot?.addEventListener("click", async (event) => {
    const button = event.target.closest(".fdl-tab");
    if (!button || button.dataset.fdlTab === activeTab) return;
    if (!(await confirmLeave())) return;
    setTab(button.dataset.fdlTab);
  });

  populatePeriodSelects();
  renderTables();
  bindHeaderFields();
  void load();
});
