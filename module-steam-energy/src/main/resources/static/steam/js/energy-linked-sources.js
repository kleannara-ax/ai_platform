/**
 * 에너지 회계비용 페이지들이 함께 쓰는 연동 값.
 *
 * 마감비교(energy-month-compare)와 연료비 배분비율(energy-fuel-ratio)이 같은 값을 쓰므로
 * 한 곳에 둔다. 두 페이지가 따로 들고 있으면 한쪽만 바뀌어 숫자가 어긋난다.
 *
 *  1) 차감비 고정값 — 월마다 바뀌지 않는다 (백만원)
 *  2) 복합보일러 스팀구매 — SRF Boiler 인보이스 Summary(VK) 의
 *     "TOTAL SERVICE CHARGES FOR THE MONTH" (원) 를 백만원으로 바꾼 값
 *  3) 복합 LNG 사용량, 처분부담금 등 — SRF Boiler Client Invoice 1. Utilities(전력 제외) + 2. Other Charge
 *     청구금액 합을 백만원으로 바꾼 값
 */
(function (global) {
  "use strict";

  const LAND_LEASE_FIXED = 6.7;        // 토지 지상권 지료
  const COMPOSITE_INCOME_FIXED = 63.2; // 복합보일러 처분수익 연료비 차감분

  // ── SRF Boiler 인보이스 Summary(VK) ─────────────────────────────────────
  // 저장: table_cell_value (table_name=srf_boiler_invoice_summary, month="YYYY-MM")
  //       row_key = "summary-vk-r{행번호}", col_index = 열번호(0-based) + 1
  // 계산식은 js/srf-boiler-invoice-pages.js 의 calculatedCellValue 와 같아야 한다.
  const SRF_TABLE = "srf_boiler_invoice_summary";
  const VALUE_COLUMNS = [3, 5];        // 금액이 적히는 두 열 (D, F)

  function round(value, digits) {
    const factor = 10 ** digits;
    return Math.round((Number(value) + Number.EPSILON) * factor) / factor;
  }

  function roundup(value, digits) {
    const factor = 10 ** digits;
    const number = Number(value);
    if (!Number.isFinite(number)) return 0;
    return Math.ceil(number * factor) / factor;
  }

  function parseNumber(value) {
    const text = String(value ?? "").replace(/,/g, "").trim();
    if (!text || text === "-") return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  /**
   * 한 달치 인보이스 칸으로 TOTAL SERVICE CHARGES FOR THE MONTH(원)를 계산한다.
   * 인보이스 화면과 같은 순서로 — 저장값이 있으면 저장값, 없으면 계산값.
   *
   * @param {Map<string,string>} stored  "행|열" → 저장값
   * @returns {number|null} 입력이 하나도 없으면 null
   */
  function srfTotalServiceCharges(stored) {
    const raw = (row, col) => {
      const key = `${row}|${col}`;
      if (stored.has(key)) return stored.get(key);
      const calc = calculated(row, col);
      return calc === null ? "" : calc;
    };
    const n = (row, col) => parseNumber(raw(row, col)) ?? 0;
    const hasAny = (rows) => rows.some((row) => VALUE_COLUMNS.some((col) => parseNumber(raw(row, col)) !== null));
    const blankable = (rows, calc) => {
      if (!hasAny(rows)) return "";
      const value = calc();
      return Number.isFinite(value) ? String(value) : "";
    };
    function calculated(row, col) {
      if (row === 13 && col === 3) return blankable([15, 17, 19, 21], () => (round(n(15, 3) * n(17, 3), 0) + n(19, 3)) * n(21, 3));
      if (row === 24 && col === 3) return blankable([26, 28, 30], () => round(n(26, 3) * n(28, 3), 0) * n(30, 3));
      if (row === 39 && col === 5) return blankable([13, 24, 33, 35], () => roundup(n(13, 3) + n(24, 3) + n(33, 3) + n(35, 3), 0));
      return null;
    }
    return parseNumber(raw(39, 5));   // TOTAL SERVICE CHARGES FOR THE MONTH
  }

  // ── SRF Boiler Client Invoice ───────────────────────────────────────────
  // 저장: table_cell_value (table_name=srf_boiler_invoice_client, month="YYYY-MM")
  //       row_key = "{구역}|{순번}|{qty|price|…}", 청구금액 = 양 × 단가 (화면 srf-boiler-invoice-client.html 과 같다)
  // 복합 LNG 사용량, 처분부담금 등 = 1. Utilities(전력 제외) + 2. Other Charge(깨끗한 나라 → 베올리아) 청구금액 합.
  //   3. Other Charge(베올리아 → 깨끗한 나라)는 넣지 않는다.
  const CLIENT_TABLE = "srf_boiler_invoice_client";
  const CLIENT_SECTIONS = new Set(["utilities", "other-out"]);
  const CLIENT_EXCLUDED = new Set(["utilities|0"]);   // · Electricity (전력)

  function clientNonPowerCharges(stored) {
    const byItem = new Map();
    stored.forEach((value, key) => {
      const [section, idx, field] = key.split("|");
      if (!CLIENT_SECTIONS.has(section) || (field !== "qty" && field !== "price")) return;
      const itemKey = `${section}|${idx}`;
      if (CLIENT_EXCLUDED.has(itemKey)) return;
      if (!byItem.has(itemKey)) byItem.set(itemKey, {});
      byItem.get(itemKey)[field] = parseNumber(value);
    });
    let sum = 0;
    let any = false;
    byItem.forEach((item) => {
      if (item.qty == null || item.price == null) return;
      sum += item.qty * item.price;
      any = true;
    });
    return any ? sum : null;
  }

  const srfCache = { year: null, data: null };
  const clientCache = { year: null, data: null };

  /**
   * 한 해의 복합 LNG 사용량, 처분부담금 등(백만원)을 월별로 돌려준다 — { 1: 값, …, 12: 값 }.
   * Client Invoice 를 입력하지 않은 달은 빠진다.
   */
  async function fetchCompositeLngEtc(year) {
    const yearKey = String(year);
    if (clientCache.year === yearKey && clientCache.data) return clientCache.data;
    const out = {};
    try {
      const params = new URLSearchParams({
        fromMonth: `${yearKey}-01`, toMonth: `${yearKey}-12`, tableNames: CLIENT_TABLE, limit: "10000",
      });
      const resp = await fetch(`/tables/table_cell_value?${params.toString()}`);
      if (resp.ok) {
        const payload = await resp.json();
        const byMonth = new Map();
        (payload.data || []).forEach((row) => {
          if (row.table_name !== CLIENT_TABLE) return;
          const monthNo = Number(String(row.month).split("-")[1]);
          if (!(monthNo >= 1 && monthNo <= 12)) return;
          if (!byMonth.has(monthNo)) byMonth.set(monthNo, new Map());
          byMonth.get(monthNo).set(String(row.row_key), String(row.cell_value ?? ""));
        });
        byMonth.forEach((stored, monthNo) => {
          const won = clientNonPowerCharges(stored);
          if (won !== null) out[monthNo] = won / 1000000;   // 원 → 백만원
        });
      }
    } catch (e) {
      console.error("[energy-linked-sources] Client Invoice 조회 실패", e);
    }
    clientCache.year = yearKey;
    clientCache.data = out;
    return out;
  }

  function compositeLngEtcSync(year) {
    return (clientCache.year === String(year) && clientCache.data) ? clientCache.data : {};
  }

  /**
   * 한 해의 복합보일러 스팀구매(백만원)를 월별로 돌려준다 — { 1: 값, …, 12: 값 }.
   * 인보이스를 입력하지 않은 달은 빠진다.
   */
  async function fetchCompositeSteamPurchase(year) {
    const yearKey = String(year);
    if (srfCache.year === yearKey && srfCache.data) return srfCache.data;
    const out = {};
    try {
      const params = new URLSearchParams({
        fromMonth: `${yearKey}-01`, toMonth: `${yearKey}-12`, tableNames: SRF_TABLE, limit: "10000",
      });
      const resp = await fetch(`/tables/table_cell_value?${params.toString()}`);
      if (resp.ok) {
        const payload = await resp.json();
        const byMonth = new Map();
        (payload.data || []).forEach((row) => {
          if (row.table_name !== SRF_TABLE) return;
          const match = /^summary-vk-r(\d+)$/.exec(String(row.row_key));
          if (!match) return;
          const monthNo = Number(String(row.month).split("-")[1]);
          if (!(monthNo >= 1 && monthNo <= 12)) return;
          if (!byMonth.has(monthNo)) byMonth.set(monthNo, new Map());
          const rowIndex = Number(match[1]) - 1;
          const colIndex = Number(row.col_index) - 1;
          byMonth.get(monthNo).set(`${rowIndex}|${colIndex}`, String(row.cell_value ?? ""));
        });
        byMonth.forEach((stored, monthNo) => {
          const won = srfTotalServiceCharges(stored);
          if (won !== null) out[monthNo] = won / 1000000;   // 원 → 백만원
        });
      }
    } catch (e) {
      console.error("[energy-linked-sources] SRF 인보이스 조회 실패", e);
    }
    srfCache.year = yearKey;
    srfCache.data = out;
    return out;
  }

  /** 캐시된 값을 바로 돌려준다 — 미리 fetchCompositeSteamPurchase 를 불러 둬야 한다. */
  function compositeSteamPurchaseSync(year) {
    return (srfCache.year === String(year) && srfCache.data) ? srfCache.data : {};
  }

  function resetCache() {
    srfCache.year = null;
    srfCache.data = null;
    clientCache.year = null;
    clientCache.data = null;
  }

  global.EnergyLinkedSources = {
    LAND_LEASE_FIXED,
    COMPOSITE_INCOME_FIXED,
    fetchCompositeSteamPurchase,
    compositeSteamPurchaseSync,
    fetchCompositeLngEtc,
    compositeLngEtcSync,
    resetCache,
  };
})(window);
