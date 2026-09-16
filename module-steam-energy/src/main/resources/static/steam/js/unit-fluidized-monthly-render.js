(() => {
  const MONTHS = [
    { key: "01", label: "\u0031\uc6d4" },
    { key: "02", label: "\u0032\uc6d4" },
    { key: "03", label: "\u0033\uc6d4" },
    { key: "04", label: "\u0034\uc6d4" },
    { key: "05", label: "\u0035\uc6d4" },
    { key: "06", label: "\u0036\uc6d4" },
    { key: "07", label: "\u0037\uc6d4" },
    { key: "08", label: "\u0038\uc6d4" },
    { key: "09", label: "\u0039\uc6d4" },
    { key: "10", label: "\u0031\u0030\uc6d4" },
    { key: "11", label: "\u0031\u0031\uc6d4" },
    { key: "12", label: "\u0031\u0032\uc6d4" },
  ];

  const STORE_KEY = "steamlog:fluidized-monthly-workbook:v2";
  const SRF_INBOUND_STORE_KEY = "steamlog:fluidized-srf-inbound:v1";
  const FRAME_STYLE_ID = "fluidized-monthly-workbook-style";
  const DETAIL_RENDER_CACHE = new Map();

  const OPERATING_INPUT_KEYS = [
    "sludgeBurn",
    "srfBurn",
    "srfInbound",
    "steamProduced",
    "runHours",
    "stopHours",
  ];

  const CONTRACT_INPUT_KEYS = [
    "steamOperatingCost",
    "fixedCost",
    "improvementCost",
    "deductionCost",
    "extraPayment",
  ];

  const OPERATING_UNIT_INPUT_KEYS = ["operatingCost"];

  const CHEM_A_KEYS = [
    "g1BuyQty", "g1UseQty", "g1BuyCost", "g1UseCost",
    "g2BuyQty", "g2UseQty", "g2BuyCost", "g2UseCost",
    "g3BuyQty", "g3UseQty", "g3BuyCost", "g3UseCost",
    "g4BuyQty", "g4UseQty", "g4BuyCost", "g4UseCost",
  ];

  const CHEM_B_KEYS = [
    "g1BuyQty", "g1UseQty", "g1BuyCost", "g1UseCost",
    "g2BuyQty", "g2UseQty", "g2BuyCost", "g2UseCost",
    "g3BuyQty", "g3UseQty", "g3BuyCost", "g3UseCost",
    "g4BuyQty", "g4UseQty", "g4BuyCost", "g4UseCost",
    "g5BuyQty", "g5BuyCost",
    "g6BuyQty", "g6BuyCost",
    "g7BuyQty", "g7BuyCost",
  ];

  const UTILITIES_INPUT_KEYS = [
    "srfRevenue",
    "powerUsage",
    "powerRate",
    "recycleWaterUsage",
    "recycleWaterRate",
    "condensateUsage",
    "condensateRate",
    "wastewaterQty",
    "wastewaterRate",
    "flyAshQty",
    "flyAshCost",
    "bottomAshQty",
    "bottomAshCost",
    "soilQty",
    "soilCost",
  ];

  const OPERATING_INCINERATOR_COLUMNS = [
    { key: "sludgeBurn", label: "\uc2ac\ub7ec\uc9c0 \uc18c\uac01\ub7c9(\ud1a4)", digits: 1, editable: true },
    { key: "srfBurn", label: "SRF \uc18c\uac01\ub7c9(\ud1a4)", digits: 1, editable: true },
    { key: "totalBurn", label: "\uc804\uccb4 \uc18c\uac01\ub7c9(\ud1a4)", digits: 1, editable: false },
    { key: "srfInbound", label: "SRF \ubc18\uc785\ub7c9(\ud1a4)", digits: 1, editable: true },
    { key: "steamProduced", label: "\uc2a4\ud300 \uc0dd\uc0b0\ub7c9(\ud1a4)", digits: 1, editable: true },
    { key: "steamSent", label: "\uc2a4\ud300 \uc1a1\uae30\ub7c9(\ud1a4)", digits: 0, editable: false },
    { key: "runHours", label: "\uac00\ub3d9\uc2dc\uac04(Hr)", digits: 1, editable: true },
    { key: "stopHours", label: "\uc6b4\ud734\uc2dc\uac04(Hr)", digits: 1, editable: true },
  ];

  const CONTRACT_COLUMNS = [
    { key: "steamOperatingCost", label: "\uac00\ub3d9\uc2dc\uac04 \uc6b4\uc601\ube44(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "fixedCost", label: "\uc6b4\ud734\uc2dc\uac04 \uace0\uc815\ube44(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "improvementCost", label: "\uc124\ube44\uac1c\uc120\ube44\uc6a9(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "deductionCost", label: "\ucc28\uac10\ube44(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "extraPayment", label: "\ucd94\uac00 \uc9c0\uae09\ubd84(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "contractCost", label: "\ub3c4\uae09\ube44\uc6a9(\ucc9c\uc6d0)", digits: 3, editable: false },
    { key: "contractBurnUnit", label: "\uc18c\uac01 \uc6d0\ub2e8\uc704(\ucc9c\uc6d0/\ud1a4)", digits: 3, editable: false },
    { key: "contractSteamUnit", label: "\uc2a4\ud300 \uc6d0\ub2e8\uc704(\ucc9c\uc6d0/\ud1a4)", digits: 3, editable: false },
  ];

  const OPERATING_UNIT_COLUMNS = [
    { key: "operatingCost", label: "\uc6b4\uc601\ube44\uc6a9(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "operatingBurnUnit", label: "\uc18c\uac01 \uc6d0\ub2e8\uc704(\ucc9c\uc6d0/\ud1a4)", digits: 3, editable: false },
    { key: "operatingSteamUnit", label: "\uc2a4\ud300 \uc6d0\ub2e8\uc704(\ucc9c\uc6d0/\ud1a4)", digits: 3, editable: false },
  ];

  // \uc5d1\uc140(\uc6d0\ubcf8/2. \uc720\ub3d9\uc0c1 \uc6b4\uc601\ub0b4\uc5ed) \uc2dc\ud2b8 '3-1. \uc6d4\ubcc4 \uc6b4\uc601\ub0b4\uc5ed' \ud589 21-22 \uae30\uc900
  const CHEM_A_GROUPS = [
    { label: "\uacbd\uc720 (124)", prefix: "g1" },
    { label: "\ud0c4\uc0b0\uc554\ubaa8\ub284", prefix: "g2" },
    { label: "\uccad\uad00\uc81c", prefix: "g3" },
    { label: "\ud65c\uc131\ud0c4", prefix: "g4" },
  ];

  // \uc5d1\uc140 \uc2dc\ud2b8 \ud589 38-39 \uae30\uc900 \u2014 \uc55e 4\uac1c full, \ub4a4 3\uac1c pair(\uad6c\uc785\ub7c9/\uad6c\uc785\ube44\uc6a9\ub9cc)
  const CHEM_B_GROUPS = [
    { label: "\uc18c\uae08", prefix: "g1", type: "full" },
    { label: "\uac00\uc131\uc18c\ub2e4", prefix: "g2", type: "full" },
    { label: "\uc18c\uc11d\ud68c", prefix: "g3", type: "full" },
    { label: "\uaddc\uc0ac", prefix: "g4", type: "full" },
    { label: "\ud65c\uc131\ud0c4 (\ub2e4\uc774\uc625\uc2e0\uce21\uc815\ub300\ube44)", prefix: "g5", type: "pair" },
    { label: "\ube44\uc0c1\ubc1c\uc804\uae30\uc6a9 \uacbd\uc720", prefix: "g6", type: "pair" },
    { label: "\uc5f4\ud48d\uae30\uc6a9 \ub4f1\uc720", prefix: "g7", type: "pair" },
  ];

  const UTILITIES_COLUMNS = [
    { key: "srfInbound", label: "SRF \ubc18\uc785\ub7c9(\ud1a4)", digits: 3, editable: false },
    { key: "srfRevenue", label: "\uc218\uc785\uae08(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "powerUsage", label: "\uc804\ub825\uc0ac\uc6a9\ub7c9", digits: 0, editable: true },
    { key: "powerRate", label: "\uc804\ub825\ub2e8\uac00", digits: 3, editable: true },
    { key: "powerCost", label: "\uc0ac\uc6a9\ube44\uc6a9(\ucc9c\uc6d0)", digits: 3, editable: false },
    { key: "recycleWaterUsage", label: "\uc7ac\uc774\uc6a9\uc218", digits: 3, editable: true },
    { key: "recycleWaterRate", label: "\ucc98\ub9ac\ub2e8\uac00", digits: 3, editable: true },
    { key: "recycleWaterCost", label: "\uc0ac\uc6a9\ube44\uc6a9(\ucc9c\uc6d0)", digits: 3, editable: false },
    { key: "condensateUsage", label: "\ubcf5\ub958\uc218", digits: 3, editable: true },
    { key: "condensateRate", label: "\ucc98\ub9ac\ub2e8\uac00", digits: 3, editable: true },
    { key: "condensateCost", label: "\uc0ac\uc6a9\ube44\uc6a9(\ucc9c\uc6d0)", digits: 3, editable: false },
    { key: "wastewaterQty", label: "\ud3d0\uc218", digits: 3, editable: true },
    { key: "wastewaterRate", label: "\ucc98\ub9ac\ub2e8\uac00", digits: 3, editable: true },
    { key: "wastewaterCost", label: "\uc0ac\uc6a9\ube44\uc6a9(\ucc9c\uc6d0)", digits: 3, editable: false },
    { key: "flyAshQty", label: "\ube44\uc0b0\uc7ac \ucc98\ub9ac\ub7c9", digits: 3, editable: true },
    { key: "flyAshCost", label: "\ube44\uc0b0\uc7ac \ucc98\ub9ac\uae08\uc561(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "bottomAshQty", label: "\ubc14\ub2e5\uc7ac \ucc98\ub9ac\ub7c9", digits: 3, editable: true },
    { key: "bottomAshCost", label: "\ubc14\ub2e5\uc7ac \ucc98\ub9ac\uae08\uc561(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "soilQty", label: "\ud3d0\ud1a0\uc0ac \ucc98\ub9ac\ub7c9", digits: 3, editable: true },
    { key: "soilCost", label: "\ud3d0\ud1a0\uc0ac \ucc98\ub9ac\uae08\uc561(\ucc9c\uc6d0)", digits: 3, editable: true },
    { key: "surcharge", label: "\ud3d0\uae30\ubb3c\ucc98\ubd84\ubd80\ub2f4\uae08(\ucc9c\uc6d0)", digits: 3, editable: false },
    { key: "ownCost", label: "\uc790\uccb4 \uc6b4\uc601\ube44(\ucc9c\uc6d0)", digits: 3, editable: false },
    { key: "ownBurnUnit", label: "\uc790\uccb4 \uc6b4\uc601\ub2e8\uac00(\ucc9c\uc6d0/\ud1a4)", digits: 3, editable: false },
    { key: "ownSteamUnit", label: "\uc790\uccb4 \uc6b4\uc601\ub2e8\uac00(\ucc9c\uc6d0/\uc2a4\ud300\ud1a4)", digits: 3, editable: false },
    { key: "processCost", label: "\ucc98\ub9ac \uc6d0\uac00(\ucc9c\uc6d0)", digits: 3, editable: false },
    { key: "processBurnUnit", label: "\ucc98\ub9ac \uc6d0\ub2e8\uc704(\ucc9c\uc6d0/\ud1a4)", digits: 3, editable: false },
    { key: "processSteamUnit", label: "\ucc98\ub9ac \uc6d0\ub2e8\uc704(\ucc9c\uc6d0/\uc2a4\ud300\ud1a4)", digits: 3, editable: false },
  ];

  function ensureStyles() {
    if (document.getElementById(FRAME_STYLE_ID)) return;
    const style = document.createElement("style");
    style.id = FRAME_STYLE_ID;
    style.textContent = `
      .monthly-sheet-shell {
        padding: 16px;
        color: var(--inc-text, #173a54);
      }
      .monthly-table-stack {
        display: flex;
        flex-direction: column;
        gap: 16px;
      }
      .monthly-table-card {
        border: 1px solid rgba(120, 181, 217, 0.24);
        border-radius: 12px;
        background: #fff;
        box-shadow: 0 8px 18px rgba(23, 65, 95, 0.06);
        overflow: hidden;            /* 카드 모서리 둥글게 유지 */
      }
      .monthly-table-scroll {
        width: 100%;
        max-width: 100%;
        /* 표가 컨테이너에 안 들어갈 때만 가로 스크롤. 세로 스크롤은 차단(자동 승격 방지). */
        overflow-x: auto;
        overflow-y: hidden;
      }
      .monthly-table-card--narrow {
        max-width: 360px;
        align-self: flex-start;
      }
      .monthly-pricelist-table {
        width: 100%;
        min-width: 0;
      }
      .monthly-pricelist-table td {
        text-align: right;
      }
      .monthly-table-title {
        margin: 0;
        padding: 12px 16px 6px;
        font-size: 14px;
        font-weight: 700;
        color: var(--inc-text-strong, #1f3b57);
      }
      .monthly-workbook-table {
        width: 100%;
        /* min-width 제거 — 표가 자연폭을 강제하지 않도록.
           셀의 min-width 만으로 너무 좁아지는 것은 방지. 그래도 컨테이너에 안 들어가는
           경우(28컬럼 약품B 등)에만 wrap 의 가로 스크롤이 나타남. */
        border-collapse: collapse;
        table-layout: auto;
        font: 500 11.5px Inter, "Apple SD Gothic Neo", sans-serif;
        line-height: 1.3;
        color: var(--inc-text, #173a54);
      }
      .monthly-workbook-table th,
      .monthly-workbook-table td {
        border: 1px solid #dde6ee;
        padding: 5px 6px;
        vertical-align: middle;
        text-align: center;
        word-break: keep-all;
        min-width: 52px;
      }
      .monthly-workbook-table thead th {
        white-space: normal;        /* 헤더 텍스트는 줄바꿈 허용 → 자연폭 줄어듦 */
        overflow-wrap: anywhere;    /* 괄호 안 긴 단어도 어디서든 줄바꿈 (자연폭 더 줄어듦) */
        word-break: break-word;
        line-height: 1.2;
      }
      .monthly-workbook-table tbody th,
      .monthly-workbook-table tbody td {
        white-space: nowrap;        /* 본문 숫자는 한 줄 유지 */
      }
      .monthly-workbook-table thead th {
        line-height: 1.25;
      }
      .monthly-workbook-table thead th {
        background: linear-gradient(180deg, #fafcff 0%, #eef3f9 100%);
        font-weight: 700;
        color: var(--inc-text-strong, #1f3b57);
        border-bottom: 1.5px solid rgba(120, 181, 217, .4);
      }
      .monthly-workbook-table .monthly-year-head {
        min-width: 64px;
      }
      .monthly-workbook-table .monthly-month-col {
        min-width: 64px;
        background: var(--inc-bg-header, #f3f7fa);
        font-weight: 600;
        text-align: left;
        color: var(--inc-text-strong, #1f3b57);
      }
      .monthly-workbook-table thead .monthly-month-col {
        background: linear-gradient(180deg, #fafcff 0%, #eef3f9 100%);
      }
      .monthly-workbook-table tbody tr.is-selected .monthly-month-col,
      .monthly-workbook-table tbody tr.is-selected td {
        background: #fff8d4;
      }
      .monthly-workbook-table tbody tr.monthly-summary-row th,
      .monthly-workbook-table tbody tr.monthly-summary-row td {
        background: var(--inc-bg-summary, #fff8e3);
        font-weight: 700;
        color: #7c5a00;
      }
      .monthly-workbook-table tbody tr.monthly-average-row th,
      .monthly-workbook-table tbody tr.monthly-average-row td {
        background: #f4f7fa;
        font-weight: 700;
      }
      /* 좁은 화면에서 폰트와 패딩 축소 */
      @media (max-width: 1200px) {
        .monthly-workbook-table { font-size: 11px; }
        .monthly-workbook-table th,
        .monthly-workbook-table td { padding: 4px 5px; }
      }
      .monthly-input {
        width: 100%;
        min-width: 60px;
        max-width: 100%;
        border: 1px solid #b7d7ef;
        border-radius: 6px;
        background: #eef8ff;
        padding: 5px 6px;
        text-align: right;
        color: #173a54;
        font: inherit;
        font-weight: 700;
        box-sizing: border-box;
      }
      .monthly-input:focus {
        outline: 2px solid rgba(86, 167, 226, 0.26);
        border-color: #66b7e7;
        background: #f8fcff;
      }
      .monthly-calc {
        display: block;
        min-width: 60px;
        border-radius: 6px;
        background: #f4f7fa;
        padding: 5px 6px;
        text-align: right;
        font-weight: 700;
        color: #486172;
      }
      .monthly-placeholder {
        padding: 26px 22px;
        border: 1px dashed rgba(124, 177, 212, 0.48);
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.9);
        color: #597184;
        font-size: 14px;
        line-height: 1.7;
      }
      .monthly-chart-grid {
        display: grid;
        grid-template-columns: repeat(2, minmax(0, 1fr));
        gap: 18px;
      }
      .monthly-chart-card {
        border: 1px solid rgba(120, 181, 217, 0.24);
        border-radius: 16px;
        background: rgba(255, 255, 255, 0.96);
        box-shadow: 0 14px 28px rgba(23, 65, 95, 0.08);
        padding: 14px 16px 16px;
        min-width: 0;
      }
      .monthly-chart-title {
        margin: 0 0 10px;
        font-size: 15px;
        font-weight: 800;
        color: #173a54;
      }
      .monthly-chart-wrap {
        position: relative;
        min-height: 280px;
      }
      .monthly-chart-svg {
        display: block;
        width: 100%;
        height: auto;
        overflow: visible;
      }
      .monthly-chart-axis,
      .monthly-chart-grid-line {
        stroke: rgba(93, 124, 148, 0.34);
        stroke-width: 1;
      }
      .monthly-chart-grid-line {
        stroke-dasharray: 4 4;
      }
      .monthly-chart-line {
        fill: none;
        stroke-width: 3;
        stroke-linecap: round;
        stroke-linejoin: round;
      }
      .monthly-chart-point {
        stroke: #ffffff;
        stroke-width: 1.5;
      }
      .monthly-chart-bar {
        opacity: 0.78;
      }
      .monthly-chart-label,
      .monthly-chart-legend {
        fill: #486172;
        font-size: 12px;
        font-weight: 700;
      }
      .monthly-chart-hover {
        fill: transparent;
        pointer-events: all;
      }
      .monthly-chart-tooltip {
        position: absolute;
        z-index: 10;
        min-width: 150px;
        border: 1px solid rgba(102, 151, 184, 0.28);
        border-radius: 12px;
        background: rgba(255, 255, 255, 0.98);
        box-shadow: 0 14px 30px rgba(23, 65, 95, 0.16);
        padding: 10px 12px;
        color: #173a54;
        font-size: 12px;
        pointer-events: none;
      }
      .monthly-chart-tooltip-title {
        margin-bottom: 6px;
        font-weight: 900;
      }
      .monthly-chart-tooltip-row {
        display: flex;
        align-items: center;
        justify-content: space-between;
        gap: 12px;
        white-space: nowrap;
      }
      .monthly-chart-tooltip-row + .monthly-chart-tooltip-row {
        margin-top: 4px;
      }
      .monthly-chart-tooltip-label {
        display: inline-flex;
        align-items: center;
        gap: 6px;
      }
      .monthly-chart-swatch {
        width: 9px;
        height: 9px;
        border-radius: 999px;
        flex: 0 0 auto;
      }
      @media (max-width: 980px) {
        .monthly-chart-grid {
          grid-template-columns: 1fr;
        }
      }
    `;
    document.head.appendChild(style);
  }

  function loadStore() {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (!raw) return {};
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch (_) {
      return {};
    }
  }

  function loadJsonStore(key) {
    try {
      const raw = localStorage.getItem(key);
      if (!raw) return {};
      const parsed = JSON.parse(raw);
      return parsed && typeof parsed === "object" ? parsed : {};
    } catch (_) {
      return {};
    }
  }

  function saveStore(store) {
    localStorage.setItem(STORE_KEY, JSON.stringify(store));
  }

  function ensureMonthObject(keys) {
    return keys.reduce((acc, key) => {
      acc[key] = "";
      return acc;
    }, {});
  }

  function createYearState() {
    const operating = {};
    const contract = {};
    const operatingUnit = {};
    const chemA = {};
    const chemB = {};
    const utilities = {};

    MONTHS.forEach(({ key }) => {
      operating[key] = ensureMonthObject(OPERATING_INPUT_KEYS);
      contract[key] = ensureMonthObject(CONTRACT_INPUT_KEYS);
      operatingUnit[key] = ensureMonthObject(OPERATING_UNIT_INPUT_KEYS);
      chemA[key] = ensureMonthObject(CHEM_A_KEYS);
      chemB[key] = ensureMonthObject(CHEM_B_KEYS);
      utilities[key] = ensureMonthObject(UTILITIES_INPUT_KEYS);
    });

    return { operating, contract, operatingUnit, chemA, chemB, utilities };
  }

  function ensureYearState(year) {
    const store = loadStore();
    if (!store[year]) {
      store[year] = createYearState();
      saveStore(store);
    }
    return store[year];
  }

  function getYearState(year) {
    const store = loadStore();
    if (!store[year]) {
      store[year] = createYearState();
      saveStore(store);
    }
    return store[year];
  }

  function updateYearValue(year, sectionName, month, key, rawValue) {
    const store = loadStore();
    if (!store[year]) store[year] = createYearState();
    if (!store[year][sectionName]) store[year][sectionName] = {};
    if (!store[year][sectionName][month]) store[year][sectionName][month] = {};
    store[year][sectionName][month][key] = rawValue;
    saveStore(store);
  }

  function parseNumber(value) {
    if (value === null || value === undefined) return null;
    if (typeof value === "number") return Number.isFinite(value) ? value : null;
    const text = String(value).replace(/,/g, "").trim();
    if (!text) return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function formatNumber(value, digits = 0) {
    if (value === null || value === undefined || value === "") return "";
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return "";
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: digits,
    }).format(numeric);
  }

  function sum(values) {
    const numbers = values.filter((value) => Number.isFinite(value));
    if (!numbers.length) return null;
    return numbers.reduce((acc, value) => acc + value, 0);
  }

  function average(values) {
    const numbers = values.filter((value) => Number.isFinite(value));
    if (!numbers.length) return null;
    return numbers.reduce((acc, value) => acc + value, 0) / numbers.length;
  }

  function divide(numerator, denominator, scale = 1) {
    if (!Number.isFinite(numerator) || !Number.isFinite(denominator) || denominator === 0) return null;
    return numerator / denominator / scale;
  }

  async function fetchRenderedDetailMonth(monthKey) {
    if (DETAIL_RENDER_CACHE.has(monthKey)) {
      return DETAIL_RENDER_CACHE.get(monthKey);
    }
    const promise = fetch(`/tables/fluidized-detail/rendered?month=${encodeURIComponent(monthKey)}&view=all`, {
      cache: "no-store",
    })
      .then((response) => {
        if (!response.ok) {
          DETAIL_RENDER_CACHE.delete(monthKey);
          return null;
        }
        return response.json();
      })
      .catch(() => {
        DETAIL_RENDER_CACHE.delete(monthKey);
        return null;
      });
    DETAIL_RENDER_CACHE.set(monthKey, promise);
    return promise;
  }

  function detailCell(detail, ref) {
    return detail?.main?.cells?.[ref];
  }

  function detailNumber(detail, ref, scale = 1) {
    const value = parseNumber(detailCell(detail, ref));
    if (!Number.isFinite(value)) return null;
    return value / scale;
  }

  function getSrfInboundSummary(year, month) {
    const monthKey = `${year}-${month}`;
    const monthState = loadJsonStore(SRF_INBOUND_STORE_KEY)[monthKey];
    if (!monthState || typeof monthState !== "object") return null;

    const vendorTotals = {};
    let hasQty = false;

    Object.values(monthState.entries || {}).forEach((entryRow) => {
      if (!entryRow || typeof entryRow !== "object") return;
      Object.entries(entryRow).forEach(([vendorKey, rawValue]) => {
        const qty = parseNumber(rawValue);
        if (!Number.isFinite(qty)) return;
        vendorTotals[vendorKey] = (vendorTotals[vendorKey] || 0) + qty;
        hasQty = true;
      });
    });

    let totalCost = 0;
    let hasCost = false;
    Object.entries(vendorTotals).forEach(([vendorKey, qty]) => {
      const rate = parseNumber(monthState.rates?.[vendorKey]);
      if (!Number.isFinite(qty) || !Number.isFinite(rate)) return;
      totalCost += (qty * rate) / 1000;
      hasCost = true;
    });

    const totalQty = Object.values(vendorTotals).reduce((acc, qty) => acc + qty, 0);
    return {
      totalQty: hasQty ? totalQty : null,
      totalCost: hasCost ? totalCost : null,
    };
  }

  function isDetailLinkedField(sectionName, key) {
    const linked = {
      operating: new Set(["sludgeBurn", "srfBurn", "srfInbound", "steamProduced", "steamSent", "runHours", "stopHours"]),
      contract: new Set(["steamOperatingCost", "fixedCost"]),
      operatingUnit: new Set(["operatingCost"]),
      chemA: new Set(CHEM_A_KEYS),
      chemB: new Set(CHEM_B_KEYS),
      utilities: new Set([
        "srfRevenue",
        "powerUsage", "powerRate",
        "recycleWaterUsage", "recycleWaterRate",
        "condensateUsage", "condensateRate",
        "wastewaterRate",
        "flyAshQty", "flyAshCost",
        "bottomAshQty", "bottomAshCost",
        // soilQty/soilCost 는 엑셀에서도 detail 참조 없음 → user input
      ]),
    };
    return linked[sectionName]?.has(key) || false;
  }

  function computeOperatingRow(state, year, month, detail) {
    const contract = state.contract[month];
    const sludgeBurn = detailNumber(detail, "AH8");
    const srfBurn = detailNumber(detail, "AH9");
    const srfSummary = getSrfInboundSummary(year, month);
    const totalBurn = Number.isFinite(sludgeBurn) || Number.isFinite(srfBurn)
      ? (sludgeBurn || 0) + (srfBurn || 0)
      : null;
    const steamProduced = detailNumber(detail, "AH6");

    const steamOperatingCost = detailNumber(detail, "AK12", 1000);
    const fixedCost = detailNumber(detail, "AK14", 1000);
    const improvementCost = parseNumber(contract.improvementCost);
    const deductionCost = parseNumber(contract.deductionCost);
    const extraPayment = parseNumber(contract.extraPayment);
    const contractCost = (
      Number.isFinite(steamOperatingCost) ||
      Number.isFinite(fixedCost) ||
      Number.isFinite(improvementCost) ||
      Number.isFinite(deductionCost) ||
      Number.isFinite(extraPayment)
    )
      ? (steamOperatingCost || 0) + (fixedCost || 0) + (improvementCost || 0) - (deductionCost || 0) + (extraPayment || 0)
      : null;

    // 운영비용(천원): Excel '3-1'!R14 = '3.세부'!AK96/1000 (총비용). (기존 AK95=기타소계≈0 오참조 교정)
    const operatingCost = detailNumber(detail, "AK96", 1000);

    return {
      sludgeBurn,
      srfBurn,
      totalBurn,
      srfInbound: srfSummary?.totalQty ?? detailNumber(detail, "AH45"),
      steamProduced,
      steamSent: detailNumber(detail, "AH7"),
      runHours: detailNumber(detail, "AH4"),
      stopHours: detailNumber(detail, "AH5"),
      steamOperatingCost,
      fixedCost,
      improvementCost,
      deductionCost,
      extraPayment,
      contractCost,
      contractBurnUnit: divide(contractCost, totalBurn),
      contractSteamUnit: divide(contractCost, steamProduced),
      operatingCost,
      operatingBurnUnit: divide(operatingCost, totalBurn),
      operatingSteamUnit: divide(operatingCost, steamProduced),
    };
  }

  function computeChemARow(state, month, detail) {
    // Excel '3-1. 월별 운영내역' 약품A 매핑(10월 행 기준): 반입=짝수행, 사용=홀수행.
    //  g1 경유(36/37), g2 탄산암모늄(18/19), g3 청관제(21/22), g4 활성탄(24/25).
    //  (기존 코드가 한 행씩 위(-1)를 참조해 탄산암모늄 비용칸에 운영비합계(AK17)가 섞이던 버그 교정)
    return {
      g1BuyQty: detailNumber(detail, "AH36"),
      g1UseQty: detailNumber(detail, "AH37"),
      g1BuyCost: detailNumber(detail, "AK36"),
      g1UseCost: detailNumber(detail, "AK37"),
      g2BuyQty: detailNumber(detail, "AH18"),
      g2UseQty: detailNumber(detail, "AH19"),
      g2BuyCost: detailNumber(detail, "AK18"),
      g2UseCost: detailNumber(detail, "AK19"),
      g3BuyQty: detailNumber(detail, "AH21"),
      g3UseQty: detailNumber(detail, "AH22"),
      g3BuyCost: detailNumber(detail, "AK21"),
      g3UseCost: detailNumber(detail, "AK22"),
      g4BuyQty: detailNumber(detail, "AH24"),
      g4UseQty: detailNumber(detail, "AH25"),
      g4BuyCost: detailNumber(detail, "AK24"),
      g4UseCost: detailNumber(detail, "AK25"),
    };
  }

  function computeChemATotals(values) {
    return {
      purchaseQty: sum([values.g1BuyQty, values.g2BuyQty, values.g3BuyQty, values.g4BuyQty]),
      useQty: sum([values.g1UseQty, values.g2UseQty, values.g3UseQty, values.g4UseQty]),
      purchaseCost: sum([values.g1BuyCost, values.g2BuyCost, values.g3BuyCost, values.g4BuyCost]),
      useCost: sum([values.g1UseCost, values.g2UseCost, values.g3UseCost, values.g4UseCost]),
    };
  }

  function computeChemBRow(state, year, month, detail) {
    // 약품B 매핑(세부 운영내역 행, 반입 짝수/사용 홀수): g1 소금(33/34), g2 가성소다(27/28),
    //  g3 소석회(30/31), g4 규사(39/40), g5 활성탄(41), g6 경유비상(42), g7 등유-열풍기(43).
    //  (기존 코드가 한 행씩 위(-1)를 참조하던 버그 교정 — 재고행을 반입으로 잘못 읽고 있었음)
    const values = {
      g1BuyQty: detailNumber(detail, "AH33"),
      g1UseQty: detailNumber(detail, "AH34"),
      g1BuyCost: detailNumber(detail, "AK33"),
      g1UseCost: detailNumber(detail, "AK34"),
      g2BuyQty: detailNumber(detail, "AH27"),
      g2UseQty: detailNumber(detail, "AH28"),
      g2BuyCost: detailNumber(detail, "AK27"),
      g2UseCost: detailNumber(detail, "AK28"),
      g3BuyQty: detailNumber(detail, "AH30"),
      g3UseQty: detailNumber(detail, "AH31"),
      g3BuyCost: detailNumber(detail, "AK30"),
      g3UseCost: detailNumber(detail, "AK31"),
      g4BuyQty: detailNumber(detail, "AH39"),
      g4UseQty: detailNumber(detail, "AH40"),
      g4BuyCost: detailNumber(detail, "AK39"),
      g4UseCost: detailNumber(detail, "AK40"),
      g5BuyQty: detailNumber(detail, "AH41"),
      g5BuyCost: detailNumber(detail, "AK41"),
      g6BuyQty: detailNumber(detail, "AH42"),
      g6BuyCost: detailNumber(detail, "AK42"),
      g7BuyQty: detailNumber(detail, "AH43"),
      g7BuyCost: detailNumber(detail, "AK43"),
    };

    const chemA = computeChemATotals(computeChemARow(state, month, detail));
    const operating = computeOperatingRow(state, year, month, detail);
    const localPurchaseQty = sum([
      values.g1BuyQty, values.g2BuyQty, values.g3BuyQty, values.g4BuyQty,
      values.g5BuyQty, values.g6BuyQty, values.g7BuyQty,
    ]);
    const localUseQty = sum([
      values.g1UseQty, values.g2UseQty, values.g3UseQty, values.g4UseQty,
    ]);
    const localPurchaseCost = sum([
      values.g1BuyCost, values.g2BuyCost, values.g3BuyCost, values.g4BuyCost,
      values.g5BuyCost, values.g6BuyCost, values.g7BuyCost,
    ]);
    const localUseCost = sum([
      values.g1UseCost, values.g2UseCost, values.g3UseCost, values.g4UseCost,
    ]);

    const totalPurchaseQty = sum([chemA.purchaseQty, localPurchaseQty]);
    const totalUseQty = sum([chemA.useQty, localUseQty]);
    const totalPurchaseCost = sum([chemA.purchaseCost, localPurchaseCost]);
    const totalUseCost = sum([chemA.useCost, localUseCost]);

    return {
      ...values,
      totalPurchaseQty,
      totalUseQty,
      totalPurchaseCost,
      totalUseCost,
      burnUnit: divide(totalPurchaseCost, operating.totalBurn, 1000),
      steamUnit: divide(totalPurchaseCost, operating.steamProduced, 1000),
      totalPurchaseCostThousands: divide(totalPurchaseCost, 1, 1000),
    };
  }

  function computeUtilitiesRow(state, year, month, detail) {
    const row = state.utilities[month];
    const operating = computeOperatingRow(state, year, month, detail);
    const chemB = computeChemBRow(state, year, month, detail);
    const srfSummary = getSrfInboundSummary(year, month);

    // 엑셀 시트 '3. 세부 운영내역' 셀 매핑 (10월 행 수식 기준)
    // 전력량(KW) r65: AH65=사용량, AJ65=단가
    const powerUsage = detailNumber(detail, "AH65");
    const powerRate = detailNumber(detail, "AJ65");
    // 용수(재이용수) r74: AH74=사용량, AJ74=처리단가
    const recycleWaterUsage = detailNumber(detail, "AH74");
    const recycleWaterRate = detailNumber(detail, "AJ74");
    // 복류수 r75: AH75=사용량. 단가는 엑셀 K67=H67 (재이용수 단가와 동일)
    const condensateUsage = detailNumber(detail, "AH75");
    const condensateRate = recycleWaterRate;
    // 폐수 r78: AJ78=처리단가, 사용량은 user input — 입력 없으면 null (자동 7748 채움 제거)
    const wastewaterQty = parseNumber(row.wastewaterQty);
    const wastewaterRate = detailNumber(detail, "AJ78");
    // 비산재 처리 r50-r57 (조은/씨엠디/대광/아이케이/연경/느티/모두그린/주영)
    //  (소각재 r49 는 비산재가 아니며, 주영 r57 누락이던 -1 오프셋 교정)
    const flyAshQty = sum([
      detailNumber(detail, "AH50"),
      detailNumber(detail, "AH51"),
      detailNumber(detail, "AH52"),
      detailNumber(detail, "AH53"),
      detailNumber(detail, "AH54"),
      detailNumber(detail, "AH55"),
      detailNumber(detail, "AH56"),
      detailNumber(detail, "AH57"),
    ]);
    const flyAshCost = sum([
      detailNumber(detail, "AK50", 1000),
      detailNumber(detail, "AK51", 1000),
      detailNumber(detail, "AK52", 1000),
      detailNumber(detail, "AK53", 1000),
      detailNumber(detail, "AK54", 1000),
      detailNumber(detail, "AK55", 1000),
      detailNumber(detail, "AK56", 1000),
      detailNumber(detail, "AK57", 1000),
    ]);
    // 바닥재 처리 r61-r62 (새롬산업/삼영에스엔알) — 엑셀 10월 SUM(AH61:AH62)
    const bottomAshQty = sum([detailNumber(detail, "AH61"), detailNumber(detail, "AH62")]);
    const bottomAshCost = sum([detailNumber(detail, "AK61", 1000), detailNumber(detail, "AK62", 1000)]);
    // 폐토사 — 엑셀 10월에 detail 참조 없음 (user input)
    const soilQty = parseNumber(row.soilQty);
    const soilCost = parseNumber(row.soilCost);
    // SRF 수입금 r46 — AK46/1000 (음수 → 양수 변환 위해 -1000 multiplier)
    const srfRevenue = srfSummary?.totalCost ?? detailNumber(detail, "AK46", -1000);
    const sludgeBurn = operating.sludgeBurn;

    const powerCost = Number.isFinite(powerUsage) && Number.isFinite(powerRate)
      ? (powerUsage * powerRate) / 1000
      : null;
    const recycleWaterCost = Number.isFinite(recycleWaterUsage) && Number.isFinite(recycleWaterRate)
      ? (recycleWaterUsage * recycleWaterRate) / 1000
      : null;
    const condensateCost = Number.isFinite(condensateUsage) && Number.isFinite(condensateRate)
      ? (condensateUsage * condensateRate) / 1000
      : null;
    const wastewaterCost = Number.isFinite(wastewaterQty) && Number.isFinite(wastewaterRate)
      ? (wastewaterQty * wastewaterRate) / 1000
      : null;
    const surcharge = Number.isFinite(sludgeBurn) ? sludgeBurn * 1.192 * 10 : null;

    const ownCost = (
      Number.isFinite(srfRevenue) ||
      Number.isFinite(powerCost) ||
      Number.isFinite(recycleWaterCost) ||
      Number.isFinite(condensateCost) ||
      Number.isFinite(wastewaterCost) ||
      Number.isFinite(flyAshCost) ||
      Number.isFinite(bottomAshCost) ||
      Number.isFinite(surcharge)
    )
      ? -(srfRevenue || 0) +
        (powerCost || 0) +
        (recycleWaterCost || 0) +
        (condensateCost || 0) +
        (wastewaterCost || 0) +
        (flyAshCost || 0) +
        (bottomAshCost || 0) +
        (surcharge || 0)
      : null;

    const processCost = (
      Number.isFinite(ownCost) ||
      Number.isFinite(soilCost) ||
      Number.isFinite(chemB.totalPurchaseCostThousands)
    )
      ? (ownCost || 0) + (soilCost || 0) + (chemB.totalPurchaseCostThousands || 0)
      : null;

    return {
      srfInbound: operating.srfInbound,
      srfRevenue,
      powerUsage,
      powerRate,
      powerCost,
      recycleWaterUsage,
      recycleWaterRate,
      recycleWaterCost,
      condensateUsage,
      condensateRate,
      condensateCost,
      wastewaterQty,
      wastewaterRate,
      wastewaterCost,
      flyAshQty,
      flyAshCost,
      bottomAshQty,
      bottomAshCost,
      soilQty,
      soilCost,
      surcharge,
      ownCost,
      ownBurnUnit: divide(ownCost, operating.totalBurn),
      ownSteamUnit: divide(ownCost, operating.steamProduced),
      processCost,
      processBurnUnit: divide(processCost, operating.totalBurn),
      processSteamUnit: divide(processCost, operating.steamProduced),
    };
  }

  function buildSummaryRow(records, columns, type) {
    const values = {};
    columns.forEach((column) => {
      const list = records.map((record) => record.values[column.key]).filter((value) => Number.isFinite(value));
      values[column.key] = type === "total" ? sum(list) : average(list);
    });
    return values;
  }

  function inputHtml(sectionName, month, key, value, digits) {
    return `
      <input
        class="monthly-input"
        type="text"
        inputmode="decimal"
        data-section="${sectionName}"
        data-month="${month}"
        data-key="${key}"
        data-digits="${digits}"
        value="${escapeHtml(formatNumber(parseNumber(value), digits))}"
      />
    `;
  }

  function calcHtml(value, digits) {
    return `<span class="monthly-calc">${escapeHtml(formatNumber(value, digits))}</span>`;
  }

  function formatChartValue(value) {
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return "";
    return new Intl.NumberFormat("ko-KR", { maximumFractionDigits: 0 }).format(numeric);
  }

  function computeChartTicks(items) {
    const values = items
      .flatMap((item) => item.values || [])
      .map((value) => Number(value))
      .filter((value) => Number.isFinite(value) && value > 0);
    const maxValue = values.length ? Math.max(...values) : 1;
    const magnitude = 10 ** Math.floor(Math.log10(maxValue));
    const normalized = maxValue / magnitude;
    let step = magnitude;
    if (normalized <= 2) step = magnitude / 2;
    else if (normalized <= 5) step = magnitude;
    else step = magnitude * 2;
    const top = Math.max(step, Math.ceil(maxValue / step) * step);
    const ticks = [];
    for (let value = 0; value <= top; value += step) ticks.push(value);
    return ticks.length >= 2 ? ticks : [0, top];
  }

  function chartY(value, max, topPad, chartHeight) {
    const numeric = Number(value);
    const ratio = max > 0 && Number.isFinite(numeric) ? numeric / max : 0;
    return topPad + chartHeight - (ratio * chartHeight);
  }

  function buildChartLine(values, max, leftPad, topPad, chartWidth, chartHeight) {
    return values.map((value, index) => {
      const x = leftPad + (index * chartWidth) / Math.max(1, values.length - 1);
      const y = chartY(value, max, topPad, chartHeight);
      return `${index === 0 ? "M" : "L"} ${x.toFixed(2)} ${y.toFixed(2)}`;
    }).join(" ");
  }

  function renderMonthlyChart({ title, labels, bars = [], lines = [] }) {
    const width = 920;
    const height = 360;
    const leftPad = 74;
    const rightPad = 28;
    const topPad = 46;
    const bottomPad = 58;
    const chartWidth = width - leftPad - rightPad;
    const chartHeight = height - topPad - bottomPad;
    const allSeries = [...bars, ...lines];
    const ticks = computeChartTicks(allSeries);
    const max = ticks[ticks.length - 1] || 1;
    const groupWidth = chartWidth / Math.max(1, labels.length);
    const barWidth = Math.min(36, Math.max(14, groupWidth * 0.48));

    const grid = ticks.map((tick) => {
      const y = chartY(tick, max, topPad, chartHeight);
      return `
        <line x1="${leftPad}" y1="${y}" x2="${width - rightPad}" y2="${y}" class="monthly-chart-grid-line" />
        <text x="${leftPad - 10}" y="${y + 4}" class="monthly-chart-label" text-anchor="end">${escapeHtml(formatChartValue(tick))}</text>
      `;
    }).join("");

    const barShapes = bars.map((series) => series.values.map((value, index) => {
      const xCenter = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
      const y = chartY(value, max, topPad, chartHeight);
      const h = topPad + chartHeight - y;
      return `<rect x="${xCenter - (barWidth / 2)}" y="${y}" width="${barWidth}" height="${Math.max(0, h)}" rx="4" class="monthly-chart-bar" style="fill:${series.color}" />`;
    }).join("")).join("");

    const lineShapes = lines.map((series) => {
      const path = buildChartLine(series.values, max, leftPad, topPad, chartWidth, chartHeight);
      const points = series.values.map((value, index) => {
        const x = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
        const y = chartY(value, max, topPad, chartHeight);
        return `<circle cx="${x}" cy="${y}" r="4" class="monthly-chart-point" style="fill:${series.color}" />`;
      }).join("");
      return `<path d="${path}" class="monthly-chart-line" style="stroke:${series.color}" />${points}`;
    }).join("");

    const xLabels = labels.map((label, index) => {
      const x = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
      return `<text x="${x}" y="${height - 22}" class="monthly-chart-label" text-anchor="middle">${escapeHtml(label)}</text>`;
    }).join("");

    const hoverColumns = labels.map((_, index) => {
      const previousX = index === 0 ? leftPad : leftPad + ((index - 1) * chartWidth) / Math.max(1, labels.length - 1);
      const currentX = leftPad + (index * chartWidth) / Math.max(1, labels.length - 1);
      const nextX = index === labels.length - 1 ? leftPad + chartWidth : leftPad + ((index + 1) * chartWidth) / Math.max(1, labels.length - 1);
      const startX = index === 0 ? currentX - ((nextX - currentX) / 2) : (previousX + currentX) / 2;
      const endX = index === labels.length - 1 ? currentX + ((currentX - previousX) / 2) : (currentX + nextX) / 2;
      return `<rect x="${startX}" y="${topPad}" width="${Math.max(16, endX - startX)}" height="${chartHeight}" class="monthly-chart-hover" data-index="${index}" />`;
    }).join("");

    const legend = allSeries.map((series, index) => {
      const x = leftPad + (index % 3) * 230;
      const y = 12 + Math.floor(index / 3) * 20;
      return `
        <g transform="translate(${x}, ${y})">
          <rect x="0" y="2" width="12" height="12" rx="3" style="fill:${series.color}" />
          <text x="18" y="13" class="monthly-chart-legend">${escapeHtml(series.label)}</text>
        </g>
      `;
    }).join("");

    return `
      <article class="monthly-chart-card">
        <h3 class="monthly-chart-title">${escapeHtml(title)}</h3>
        <div class="monthly-chart-wrap" data-monthly-chart>
          <div class="monthly-chart-tooltip" hidden></div>
          <svg class="monthly-chart-svg" viewBox="0 0 ${width} ${height}" preserveAspectRatio="xMidYMid meet">
            <g>${grid}</g>
            <line x1="${leftPad}" y1="${topPad + chartHeight}" x2="${width - rightPad}" y2="${topPad + chartHeight}" class="monthly-chart-axis" />
            <line x1="${leftPad}" y1="${topPad}" x2="${leftPad}" y2="${topPad + chartHeight}" class="monthly-chart-axis" />
            <g>${barShapes}</g>
            <g>${lineShapes}</g>
            <g>${xLabels}</g>
            <g>${hoverColumns}</g>
            <g>${legend}</g>
          </svg>
        </div>
      </article>
    `;
  }

  function bindMonthlyChartTooltips(root, chartConfigs) {
    const wraps = Array.from(root.querySelectorAll("[data-monthly-chart]"));
    wraps.forEach((wrap, chartIndex) => {
      const tooltip = wrap.querySelector(".monthly-chart-tooltip");
      const config = chartConfigs[chartIndex];
      if (!tooltip || !config) return;
      const series = [...(config.bars || []), ...(config.lines || [])];
      wrap.querySelectorAll(".monthly-chart-hover").forEach((target) => {
        target.addEventListener("mouseenter", () => {
          const index = Number(target.dataset.index || 0);
          const rows = series.map((item) => `
            <div class="monthly-chart-tooltip-row">
              <span class="monthly-chart-tooltip-label"><span class="monthly-chart-swatch" style="background:${item.color}"></span>${escapeHtml(item.label)}</span>
              <strong>${escapeHtml(formatChartValue(item.values[index]))}</strong>
            </div>
          `).join("");
          tooltip.innerHTML = `<div class="monthly-chart-tooltip-title">${escapeHtml(config.labels[index])}</div>${rows}`;
          tooltip.hidden = false;
        });
        target.addEventListener("mousemove", (event) => {
          const bounds = wrap.getBoundingClientRect();
          const nextLeft = Math.min(Math.max(8, event.clientX - bounds.left + 12), Math.max(8, bounds.width - tooltip.offsetWidth - 8));
          const nextTop = Math.min(Math.max(8, event.clientY - bounds.top - tooltip.offsetHeight - 12), Math.max(8, bounds.height - tooltip.offsetHeight - 8));
          tooltip.style.left = `${nextLeft}px`;
          tooltip.style.top = `${nextTop}px`;
        });
        target.addEventListener("mouseleave", () => {
          tooltip.hidden = true;
        });
      });
    });
  }

  function renderCharts(year, operatingRecords, state, detailByMonth) {
    const labels = MONTHS.map((month) => month.label);
    const chemARecords = MONTHS.map((month) => computeChemARow(state, month.key, detailByMonth[month.key]));
    const chemBRecords = MONTHS.map((month) => computeChemBRow(state, year, month.key, detailByMonth[month.key]));
    const values = (records, key) => records.map((record) => Number(record.values?.[key] ?? record[key]) || 0);
    const chartConfigs = [
      {
        title: "\uc6d4\ubcc4 \uc18c\uac01\ub7c9(\ud1a4/\uc6d4)",
        labels,
        bars: [{ label: "\uc804\uccb4 \uc18c\uac01\ub7c9", color: "#70ad47", values: values(operatingRecords, "totalBurn") }],
        lines: [
          { label: "\uc2ac\ub7ec\uc9c0 \uc18c\uac01\ub7c9", color: "#4472c4", values: values(operatingRecords, "sludgeBurn") },
          { label: "SRF \uc18c\uac01\ub7c9", color: "#ed7d31", values: values(operatingRecords, "srfBurn") },
          { label: "\uac00\ub3d9\uc2dc\uac04", color: "#a5a5a5", values: values(operatingRecords, "runHours") },
        ],
      },
      {
        title: "\uc6d4\ubcc4 \uc2a4\ud300\ub7c9(\ud1a4/\uc6d4)",
        labels,
        lines: [
          { label: "\uc2a4\ud300 \uc0dd\uc0b0\ub7c9", color: "#4472c4", values: values(operatingRecords, "steamProduced") },
          { label: "\uc2a4\ud300 \uc1a1\uae30\ub7c9", color: "#ed7d31", values: values(operatingRecords, "steamSent") },
        ],
      },
      {
        title: "\uc6d4\ubcc4 \uc57d\ud488 \uad6c\ub9e4\ube44\uc6a9(\uc6d0/\uc6d4)",
        labels,
        lines: [
          { label: "\uccad\uad00\uc81c", color: "#4472c4", values: values(chemARecords, "g3BuyCost") },
          { label: "\ud65c\uc131\ud0c4", color: "#ed7d31", values: values(chemARecords, "g4BuyCost") },
          { label: "\uc18c\uae08", color: "#a5a5a5", values: values(chemBRecords, "g1BuyCost") },
          { label: "\uaddc\uc0ac", color: "#ffc000", values: values(chemBRecords, "g4BuyCost") },
          { label: "\uacbd\uc720", color: "#5b9bd5", values: values(chemARecords, "g1BuyCost") },
        ],
      },
      {
        title: "\uc6d4\ubcc4 \ud0c4\uc0b0\uc554\ubaa8\ub284 \uad6c\ub9e4\ube44\uc6a9(\uc6d0/\uc6d4)",
        labels,
        lines: [
          { label: "\ud0c4\uc0b0\uc554\ubaa8\ub284", color: "#4472c4", values: values(chemARecords, "g2BuyCost") },
        ],
      },
    ];

    return {
      html: `<div class="monthly-chart-grid">${chartConfigs.map((config) => renderMonthlyChart(config)).join("")}</div>`,
      chartConfigs,
    };
  }

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderMetricTable({ title, year, selectedMonth, records, columns, sectionName }) {
    const totalRow = buildSummaryRow(records, columns, "total");
    const averageRow = buildSummaryRow(records, columns, "average");

    return `
      <section class="monthly-table-card">
        <h3 class="monthly-table-title">${escapeHtml(title)}</h3>
        <div class="monthly-table-scroll"><table class="monthly-workbook-table">
          <thead>
            <tr>
              <th class="monthly-year-head monthly-month-col" rowspan="2">${escapeHtml(year)}\ub144\ub3c4</th>
              <th colspan="${columns.length}">${escapeHtml(title)}</th>
            </tr>
            <tr>
              ${columns.map((column) => `<th>${escapeHtml(column.label)}</th>`).join("")}
            </tr>
          </thead>
          <tbody>
            ${records.map((record) => `
              <tr${record.month === selectedMonth ? ' class="is-selected"' : ""}>
                <th scope="row" class="monthly-month-col">${escapeHtml(record.label)}</th>
                ${columns.map((column) => {
                  if (column.editable && !isDetailLinkedField(sectionName, column.key)) {
                    const raw = getYearState(year)[sectionName][record.month][column.key];
                    return `<td>${inputHtml(sectionName, record.month, column.key, raw, column.digits)}</td>`;
                  }
                  return `<td>${calcHtml(record.values[column.key], column.digits)}</td>`;
                }).join("")}
              </tr>
            `).join("")}
            <tr class="monthly-summary-row">
              <th scope="row" class="monthly-month-col">\ud569\uacc4</th>
              ${columns.map((column) => `<td>${calcHtml(totalRow[column.key], column.digits)}</td>`).join("")}
            </tr>
            <tr class="monthly-average-row">
              <th scope="row" class="monthly-month-col">\ud3c9\uade0</th>
              ${columns.map((column) => `<td>${calcHtml(averageRow[column.key], column.digits)}</td>`).join("")}
            </tr>
          </tbody>
        </table></div>
      </section>
    `;
  }

  // 엑셀 시트 행 21-36 — 주요 약품 4종 (각 구입량/사용량/구입비용/사용비용)
  // 엑셀에는 요약 합계 컬럼 없음 — 합계/평균은 row 로만 표시
  function renderChemATable(year, selectedMonth, state, detailByMonth) {
    const records = MONTHS.map((monthMeta) => {
      const values = computeChemARow(state, monthMeta.key, detailByMonth[monthMeta.key]);
      return {
        month: monthMeta.key,
        label: monthMeta.label,
        values,
      };
    });

    const sumOf = (key) => sum(records.map((r) => r.values[key]).filter((v) => Number.isFinite(v)));
    const avgOf = (key) => average(records.map((r) => r.values[key]).filter((v) => Number.isFinite(v)));

    return `
      <section class="monthly-table-card">
        <h3 class="monthly-table-title">2. 약품 사용량 (실사용량 및 비용)</h3>
        <div class="monthly-table-scroll"><table class="monthly-workbook-table">
          <thead>
            <tr>
              <th rowspan="2" class="monthly-month-col">${escapeHtml(year)}년도</th>
              ${CHEM_A_GROUPS.map((group) => `<th colspan="4">${escapeHtml(group.label)}</th>`).join("")}
            </tr>
            <tr>
              ${CHEM_A_GROUPS.map(() => `
                <th>구입량</th>
                <th>사용량</th>
                <th>구입비용</th>
                <th>사용비용</th>
              `).join("")}
            </tr>
          </thead>
          <tbody>
            ${records.map((record) => `
              <tr${record.month === selectedMonth ? ' class="is-selected"' : ""}>
                <th scope="row" class="monthly-month-col">${escapeHtml(record.label)}</th>
                ${CHEM_A_GROUPS.map((group) => `
                  <td>${calcHtml(record.values[`${group.prefix}BuyQty`], 3)}</td>
                  <td>${calcHtml(record.values[`${group.prefix}UseQty`], 3)}</td>
                  <td>${calcHtml(record.values[`${group.prefix}BuyCost`], 0)}</td>
                  <td>${calcHtml(record.values[`${group.prefix}UseCost`], 0)}</td>
                `).join("")}
              </tr>
            `).join("")}
            <tr class="monthly-summary-row">
              <th scope="row" class="monthly-month-col">합계</th>
              ${CHEM_A_GROUPS.map((group) => `
                <td>${calcHtml(sumOf(`${group.prefix}BuyQty`), 3)}</td>
                <td>${calcHtml(sumOf(`${group.prefix}UseQty`), 3)}</td>
                <td>${calcHtml(sumOf(`${group.prefix}BuyCost`), 0)}</td>
                <td>${calcHtml(sumOf(`${group.prefix}UseCost`), 0)}</td>
              `).join("")}
            </tr>
            <tr class="monthly-average-row">
              <th scope="row" class="monthly-month-col">평균</th>
              ${CHEM_A_GROUPS.map((group) => `
                <td>${calcHtml(avgOf(`${group.prefix}BuyQty`), 3)}</td>
                <td>${calcHtml(avgOf(`${group.prefix}UseQty`), 3)}</td>
                <td>${calcHtml(avgOf(`${group.prefix}BuyCost`), 0)}</td>
                <td>${calcHtml(avgOf(`${group.prefix}UseCost`), 0)}</td>
              `).join("")}
            </tr>
          </tbody>
        </table></div>
      </section>
    `;
  }

  // 엑셀 시트 col S-T (rows 21-29) — 약품 단가 조견표
  const CHEM_PRICE_LIST = [
    { name: "경유", price: 1099.09 },
    { name: "탄산암모늄", price: 380 },
    { name: "청관제", price: 1300 },
    { name: "활성탄", price: 1650 },
    { name: "소금", price: 140 },
    { name: "가성소다", price: 460 },
    { name: "소석회", price: 580 },
    { name: "규사", price: 145 },
  ];

  function renderChemPriceCard() {
    return `
      <section class="monthly-table-card monthly-table-card--narrow">
        <h3 class="monthly-table-title">약품 단가 조견표</h3>
        <div class="monthly-table-scroll"><table class="monthly-workbook-table monthly-pricelist-table">
          <thead>
            <tr><th>약품명</th><th>단가 (원/Kg)</th></tr>
          </thead>
          <tbody>
            ${CHEM_PRICE_LIST.map((item) => `
              <tr><th scope="row">${escapeHtml(item.name)}</th><td>${calcHtml(item.price, 2)}</td></tr>
            `).join("")}
          </tbody>
        </table></div>
      </section>
    `;
  }

  function renderChemBTable(year, selectedMonth, state, detailByMonth) {
    const records = MONTHS.map((monthMeta) => ({
      month: monthMeta.key,
      label: monthMeta.label,
      values: computeChemBRow(state, year, monthMeta.key, detailByMonth[monthMeta.key]),
    }));

    return `
      <section class="monthly-table-card">
        <h3 class="monthly-table-title">2-1. 약품 사용량 (보조 7종) — 합계 및 원단위</h3>
        <div class="monthly-table-scroll"><table class="monthly-workbook-table">
          <thead>
            <tr>
              <th rowspan="2" class="monthly-month-col">${escapeHtml(year)}\ub144\ub3c4</th>
              ${CHEM_B_GROUPS.map((group) => `<th colspan="${group.type === "full" ? 4 : 2}">${escapeHtml(group.label)}</th>`).join("")}
              <th rowspan="2">\ucd1d \uad6c\uc785\ub7c9</th>
              <th rowspan="2">\ucd1d \uc0ac\uc6a9\ub7c9</th>
              <th rowspan="2">\ucd1d \uad6c\uc785\ube44\uc6a9</th>
              <th rowspan="2">\ucd1d \uc0ac\uc6a9\ube44\uc6a9</th>
              <th rowspan="2">\uc57d\ud488\ube44 \uc6d0\ub2e8\uc704(\ucc9c\uc6d0/\ud1a4)</th>
              <th rowspan="2">\uc57d\ud488\ube44 \uc6d0\ub2e8\uc704(\ucc9c\uc6d0/\uc2a4\ud300\ud1a4)</th>
            </tr>
            <tr>
              ${CHEM_B_GROUPS.map((group) => group.type === "full"
                ? "<th>\uad6c\uc785\ub7c9</th><th>\uc0ac\uc6a9\ub7c9</th><th>\uad6c\uc785\ube44\uc6a9</th><th>\uc0ac\uc6a9\ube44\uc6a9</th>"
                : "<th>\uad6c\uc785\ub7c9</th><th>\uad6c\uc785\ube44\uc6a9</th>").join("")}
            </tr>
          </thead>
          <tbody>
            ${records.map((record) => `
              <tr${record.month === selectedMonth ? ' class="is-selected"' : ""}>
                <th scope="row" class="monthly-month-col">${escapeHtml(record.label)}</th>
                ${CHEM_B_GROUPS.map((group) => group.type === "full" ? `
                  <td>${calcHtml(record.values[`${group.prefix}BuyQty`], 3)}</td>
                  <td>${calcHtml(record.values[`${group.prefix}UseQty`], 3)}</td>
                  <td>${calcHtml(record.values[`${group.prefix}BuyCost`], 0)}</td>
                  <td>${calcHtml(record.values[`${group.prefix}UseCost`], 0)}</td>
                ` : `
                  <td>${calcHtml(record.values[`${group.prefix}BuyQty`], 3)}</td>
                  <td>${calcHtml(record.values[`${group.prefix}BuyCost`], 0)}</td>
                `).join("")}
                <td>${calcHtml(record.values.totalPurchaseQty, 3)}</td>
                <td>${calcHtml(record.values.totalUseQty, 3)}</td>
                <td>${calcHtml(record.values.totalPurchaseCost, 0)}</td>
                <td>${calcHtml(record.values.totalUseCost, 0)}</td>
                <td>${calcHtml(record.values.burnUnit, 3)}</td>
                <td>${calcHtml(record.values.steamUnit, 3)}</td>
              </tr>
            `).join("")}
            <tr class="monthly-summary-row">
              <th scope="row" class="monthly-month-col">\ud569\uacc4</th>
              ${CHEM_B_GROUPS.map((group) => group.type === "full" ? `
                <td>${calcHtml(sum(records.map((record) => record.values[`${group.prefix}BuyQty`]).filter((value) => Number.isFinite(value))), 3)}</td>
                <td>${calcHtml(sum(records.map((record) => record.values[`${group.prefix}UseQty`]).filter((value) => Number.isFinite(value))), 3)}</td>
                <td>${calcHtml(sum(records.map((record) => record.values[`${group.prefix}BuyCost`]).filter((value) => Number.isFinite(value))), 0)}</td>
                <td>${calcHtml(sum(records.map((record) => record.values[`${group.prefix}UseCost`]).filter((value) => Number.isFinite(value))), 0)}</td>
              ` : `
                <td>${calcHtml(sum(records.map((record) => record.values[`${group.prefix}BuyQty`]).filter((value) => Number.isFinite(value))), 3)}</td>
                <td>${calcHtml(sum(records.map((record) => record.values[`${group.prefix}BuyCost`]).filter((value) => Number.isFinite(value))), 0)}</td>
              `).join("")}
              <td>${calcHtml(sum(records.map((record) => record.values.totalPurchaseQty).filter((value) => Number.isFinite(value))), 3)}</td>
              <td>${calcHtml(sum(records.map((record) => record.values.totalUseQty).filter((value) => Number.isFinite(value))), 3)}</td>
              <td>${calcHtml(sum(records.map((record) => record.values.totalPurchaseCost).filter((value) => Number.isFinite(value))), 0)}</td>
              <td>${calcHtml(sum(records.map((record) => record.values.totalUseCost).filter((value) => Number.isFinite(value))), 0)}</td>
              <td>${calcHtml(average(records.map((record) => record.values.burnUnit).filter((value) => Number.isFinite(value))), 3)}</td>
              <td>${calcHtml(average(records.map((record) => record.values.steamUnit).filter((value) => Number.isFinite(value))), 3)}</td>
            </tr>
            <tr class="monthly-average-row">
              <th scope="row" class="monthly-month-col">\ud3c9\uade0</th>
              ${CHEM_B_GROUPS.map((group) => group.type === "full" ? `
                <td>${calcHtml(average(records.map((record) => record.values[`${group.prefix}BuyQty`]).filter((value) => Number.isFinite(value))), 3)}</td>
                <td>${calcHtml(average(records.map((record) => record.values[`${group.prefix}UseQty`]).filter((value) => Number.isFinite(value))), 3)}</td>
                <td>${calcHtml(average(records.map((record) => record.values[`${group.prefix}BuyCost`]).filter((value) => Number.isFinite(value))), 0)}</td>
                <td>${calcHtml(average(records.map((record) => record.values[`${group.prefix}UseCost`]).filter((value) => Number.isFinite(value))), 0)}</td>
              ` : `
                <td>${calcHtml(average(records.map((record) => record.values[`${group.prefix}BuyQty`]).filter((value) => Number.isFinite(value))), 3)}</td>
                <td>${calcHtml(average(records.map((record) => record.values[`${group.prefix}BuyCost`]).filter((value) => Number.isFinite(value))), 0)}</td>
              `).join("")}
              <td>${calcHtml(average(records.map((record) => record.values.totalPurchaseQty).filter((value) => Number.isFinite(value))), 3)}</td>
              <td>${calcHtml(average(records.map((record) => record.values.totalUseQty).filter((value) => Number.isFinite(value))), 3)}</td>
              <td>${calcHtml(average(records.map((record) => record.values.totalPurchaseCost).filter((value) => Number.isFinite(value))), 0)}</td>
              <td>${calcHtml(average(records.map((record) => record.values.totalUseCost).filter((value) => Number.isFinite(value))), 0)}</td>
              <td>${calcHtml(average(records.map((record) => record.values.burnUnit).filter((value) => Number.isFinite(value))), 3)}</td>
              <td>${calcHtml(average(records.map((record) => record.values.steamUnit).filter((value) => Number.isFinite(value))), 3)}</td>
            </tr>
          </tbody>
        </table></div>
      </section>
    `;
  }

  // 27 컬럼 단일 표 → 의미 단위 3개 카드로 분할 (가로 폭 압축)
  const UTILITIES_GROUPS = [
    {
      title: "3-1. SRF 수입금 / 유틸리티 자체 운영비용",
      headerLabel: "자체 운영비용 (천원)",
      keys: [
        "srfInbound", "srfRevenue",
        "powerUsage", "powerRate", "powerCost",
        "recycleWaterUsage", "recycleWaterRate", "recycleWaterCost",
        "condensateUsage", "condensateRate", "condensateCost",
        "wastewaterQty", "wastewaterRate", "wastewaterCost",
      ],
    },
    {
      title: "3-2. 폐기물 처리비 / 처분 부담금",
      headerLabel: "폐기물 처리 (천원)",
      keys: [
        "flyAshQty", "flyAshCost",
        "bottomAshQty", "bottomAshCost",
        "soilQty", "soilCost",
        "surcharge",
      ],
    },
    {
      title: "3-3. 자체 운영비 / 총 처리 원단위",
      headerLabel: "원단위 요약 (천원)",
      keys: [
        "ownCost", "ownBurnUnit", "ownSteamUnit",
        "processCost", "processBurnUnit", "processSteamUnit",
      ],
    },
  ];

  // sub: util-power(기본, 3-1) / util-waste(3-2) / util-summary(3-3)
  function renderUtilitiesTable(year, selectedMonth, state, detailByMonth, sub) {
    const records = MONTHS.map((monthMeta) => ({
      month: monthMeta.key,
      label: monthMeta.label,
      values: computeUtilitiesRow(state, year, monthMeta.key, detailByMonth[monthMeta.key]),
    }));
    const colByKey = new Map(UTILITIES_COLUMNS.map((c) => [c.key, c]));
    const totalRow = buildSummaryRow(records, UTILITIES_COLUMNS, "total");
    const averageRow = buildSummaryRow(records, UTILITIES_COLUMNS, "average");

    const renderCard = (group) => {
      const cols = group.keys.map((k) => colByKey.get(k)).filter(Boolean);
      return `
        <section class="monthly-table-card">
          <h3 class="monthly-table-title">${escapeHtml(group.title)}</h3>
          <div class="monthly-table-scroll"><table class="monthly-workbook-table">
            <thead>
              <tr>
                <th class="monthly-month-col" rowspan="2">${escapeHtml(year)}년도</th>
                <th colspan="${cols.length}">${escapeHtml(group.headerLabel)}</th>
              </tr>
              <tr>
                ${cols.map((column) => `<th>${escapeHtml(column.label)}</th>`).join("")}
              </tr>
            </thead>
            <tbody>
              ${records.map((record) => `
                <tr${record.month === selectedMonth ? ' class="is-selected"' : ""}>
                  <th scope="row" class="monthly-month-col">${escapeHtml(record.label)}</th>
                  ${cols.map((column) => {
                    if (column.editable && !isDetailLinkedField("utilities", column.key)) {
                      const raw = state.utilities[record.month][column.key];
                      return `<td>${inputHtml("utilities", record.month, column.key, raw, column.digits)}</td>`;
                    }
                    return `<td>${calcHtml(record.values[column.key], column.digits)}</td>`;
                  }).join("")}
                </tr>
              `).join("")}
              <tr class="monthly-summary-row">
                <th scope="row" class="monthly-month-col">합계</th>
                ${cols.map((column) => `<td>${calcHtml(totalRow[column.key], column.digits)}</td>`).join("")}
              </tr>
              <tr class="monthly-average-row">
                <th scope="row" class="monthly-month-col">평균</th>
                ${cols.map((column) => `<td>${calcHtml(averageRow[column.key], column.digits)}</td>`).join("")}
              </tr>
            </tbody>
          </table></div>
        </section>
      `;
    };

    const subToIdx = { "util-power": 0, "util-waste": 1, "util-summary": 2 };
    const idx = sub in subToIdx ? subToIdx[sub] : 0;
    return `
      <div class="monthly-table-stack">
        ${renderCard(UTILITIES_GROUPS[idx])}
      </div>
    `;
  }

  function notifyHeight(root) {
    requestAnimationFrame(() => {
      const height = Math.max(
        document.body.scrollHeight,
        document.documentElement.scrollHeight,
        root.scrollHeight
      );
      window.parent.postMessage({
        type: "fluidized-monthly-frame-size",
        height,
      }, window.location.origin);
    });
  }

  window.renderFluidizedMonthlyView = async function renderFluidizedMonthlyView({
    root,
    year,
    month,
    section,
    sub,
  }) {
    if (!root) return;
    ensureStyles();
    ensureYearState(year);
    root.__monthlyViewState = { year, month, section, sub };

    async function paint() {
      const state = getYearState(year);
      const detailEntries = await Promise.all(MONTHS.map(async (monthMeta) => ([
        monthMeta.key,
        await fetchRenderedDetailMonth(`${year}-${monthMeta.key}`),
      ])));
      const detailByMonth = Object.fromEntries(detailEntries);
      const operatingRecords = MONTHS.map((monthMeta) => ({
        month: monthMeta.key,
        label: monthMeta.label,
        values: computeOperatingRow(state, year, monthMeta.key, detailByMonth[monthMeta.key]),
      }));

      // 외부 페이지(예: energy-month-compare)에서 참조 가능하도록 DB 에 계산값 저장.
      // table_cell_value(table_name=fluidized_monthly_cache, month=YYYY, col_index=1..12, row_key=contractCost|operatingCost).
      // 렌더가 자주 발생하므로 디바운스로 묶어서 1초 후 한 번만 POST.
      try {
        if (window.__fmcCacheUpsertTimer) clearTimeout(window.__fmcCacheUpsertTimer);
        window.__fmcCacheUpsertTimer = setTimeout(() => {
          window.__fmcCacheUpsertTimer = null;
          const tasks = [];
          operatingRecords.forEach((rec) => {
            const monthNum = Number(rec.month);
            if (!Number.isFinite(monthNum) || monthNum < 1 || monthNum > 12) return;
            const cc = rec.values.contractCost;
            const oc = rec.values.operatingCost;
            const baseRow = { month: String(year), year_no: Number(year), month_no: monthNum, table_name: "fluidized_monthly_cache" };
            if (Number.isFinite(cc)) {
              tasks.push(fetch("/tables/table_cell_value", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ ...baseRow, row_key: "contractCost", col_index: monthNum, cell_value: String(cc) }),
              }));
            }
            if (Number.isFinite(oc)) {
              tasks.push(fetch("/tables/table_cell_value", {
                method: "POST", headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ ...baseRow, row_key: "operatingCost", col_index: monthNum, cell_value: String(oc) }),
              }));
            }
          });
          // fire-and-forget
          Promise.allSettled(tasks).catch(() => {});
        }, 500);
      } catch (e) { /* ignore */ }

      let content = "";
      let chartConfigs = [];
      if (section === "operating") {
        if (sub === "incinerator") {
          content = renderMetricTable({
            title: "\u0031. \uc6b4\uc601\ud604\ud669",
            year,
            selectedMonth: month,
            records: operatingRecords,
            columns: OPERATING_INCINERATOR_COLUMNS,
            sectionName: "operating",
          });
        } else if (sub === "contract-unit") {
          content = renderMetricTable({
            title: "\u0031-\u0031. \ub3c4\uae09\ube44 \uc6d0\ub2e8\uc704",
            year,
            selectedMonth: month,
            records: operatingRecords,
            columns: CONTRACT_COLUMNS,
            sectionName: "contract",
          });
        } else {
          content = renderMetricTable({
            title: "\u0031-\u0032. \uc6b4\uc601\ube44 \uc6d0\ub2e8\uc704",
            year,
            selectedMonth: month,
            records: operatingRecords,
            columns: OPERATING_UNIT_COLUMNS,
            sectionName: "operatingUnit",
          });
        }
      } else if (section === "chemicals") {
        let chemBody = "";
        if (sub === "chem-aux") {
          chemBody = renderChemBTable(year, month, state, detailByMonth);
        } else if (sub === "chem-price") {
          chemBody = renderChemPriceCard();
        } else {
          // chem-main (기본) — 주요 4종
          chemBody = renderChemATable(year, month, state, detailByMonth);
        }
        content = `<div class="monthly-table-stack">${chemBody}</div>`;
      } else if (section === "utilities") {
        content = renderUtilitiesTable(year, month, state, detailByMonth, sub);
      } else {
        const charts = renderCharts(year, operatingRecords, state, detailByMonth);
        content = charts.html;
        chartConfigs = charts.chartConfigs;
      }

      root.innerHTML = `
        <div class="monthly-sheet-shell">
          ${content}
        </div>
      `;

      if (chartConfigs.length) bindMonthlyChartTooltips(root, chartConfigs);
      notifyHeight(root);
    }

    root.__monthlyPaint = paint;

    if (!root.__monthlyBound) {
      root.addEventListener("change", (event) => {
        const target = event.target;
        if (!(target instanceof HTMLInputElement) || !target.dataset.section || !target.dataset.month || !target.dataset.key) {
          return;
        }
        const digits = Number(target.dataset.digits || 0);
        const parsed = parseNumber(target.value);
        const rawValue = parsed === null ? "" : String(parsed);
        updateYearValue(root.__monthlyViewState.year, target.dataset.section, target.dataset.month, target.dataset.key, rawValue);
        target.value = parsed === null ? "" : formatNumber(parsed, digits);
        if (typeof root.__monthlyPaint === "function") root.__monthlyPaint();
      });
      root.__monthlyBound = true;
    }

    await paint();
  };
})();
