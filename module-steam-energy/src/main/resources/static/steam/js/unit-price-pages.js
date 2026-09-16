/* 단가 입력 페이지 — 엑셀 "2. 단가 등록 (관리자)" 시트 구조를 반영한 확장판.
 *  - 시설별 세부 단가 항목을 연/월(1~12) 그리드로 등록. 저장 테이블: unit_price_detail.
 *  - 단가를 고치면 연동 대상 페이지의 그 달 전체 일자에 일괄 적용 (unit-price-common.js 의 PRICE_LINKS).
 *    LNG → LNG 연료 사용 비용 / 유동상 → 세부 운영내역 KNE / 폐합성 1·2호기 → 도급내역. 복합보일러는 미연결.
 *  - 신설소각로: 항목/DB만 존재, 아직 아무것도 연결하지 않음.
 *  - "자동계산" 항목은 화면에서 직접 입력하지 않음(계산식 안내만 표시).
 */
document.addEventListener("DOMContentLoaded", () => {
  if ((window.location.pathname.split("/").pop() || "") !== "unit-price.html") return;
  const UP = window.UnitPrice;
  if (!UP) { console.error("UnitPrice 공용 모듈이 로드되지 않았습니다."); return; }

  const DETAIL_TABLE = "unit_price_detail";
  const SHARED_PERIOD_KEY = "steamlog:shared-period";
  const MONTHS = Array.from({ length: 12 }, (_, i) => i + 1);

  // 엑셀 "2. 단가 등록 (관리자)" 시트의 시설·항목 구성.
  //  method: "direct"(직접입력) | "auto"(자동계산, 화면 입력 불가)
  const GROUPS = [
    {
      facility: "LNG",
      items: [
        { code: "lng_unit", label: "LNG 단가", unit: "원/㎥", method: "direct" },
      ],
    },
    {
      facility: "복합보일러",
      items: [
        { code: "comb_bsc",          label: "BSC 단가",                    unit: "원/톤", method: "direct" },
        { code: "comb_asc",          label: "ASC 단가",                    unit: "원/톤", method: "direct" },
        { code: "comb_bsc_guarantee",label: "BSC 보증량",                  unit: "",      method: "direct" },
        { code: "comb_lng_cost",     label: "(차감) 복합 LNG 사용비용",     unit: "",      method: "auto", note: "복합 LNG 사용량 × 단가" },
        { code: "comb_disposal",     label: "(차감) 처분부담금",            unit: "",      method: "direct" },
        { code: "comb_depreciation", label: "(차감) 감가상각 + 토지지상권 지료", unit: "", method: "direct" },
      ],
    },
    // 폐합성소각로는 1·2호기 단가가 서로 달라 호기별로 따로 등록한다.
    {
      facility: "폐합성소각로 1호기",
      items: [
        { code: "waste1_steam",      label: "스팀단가",          unit: "원/톤", method: "direct" },
        { code: "waste1_fixed_hr",   label: "고정단가",          unit: "원/HR", method: "direct" },
      ],
    },
    {
      facility: "폐합성소각로 2호기",
      items: [
        { code: "waste2_steam",      label: "스팀단가",          unit: "원/톤", method: "direct" },
        { code: "waste2_fixed_hr",   label: "고정단가",          unit: "원/HR", method: "direct" },
      ],
    },
    {
      facility: "유동상소각로",
      items: [
        { code: "fluid_steam",      label: "스팀단가",          unit: "원/톤", method: "direct" },
        { code: "fluid_fixed_hr",   label: "고정단가",          unit: "원/HR", method: "direct" },
      ],
    },
    {
      facility: "신설소각로",
      badge: "미연결",
      items: [
        { code: "newinc_steam",      label: "스팀단가",   unit: "원/톤", method: "direct" },
        { code: "newinc_fixed_cost", label: "고정비용",   unit: "",      method: "direct" },
      ],
    },
  ];
  const ALL_ITEMS = GROUPS.reduce((a, g) => a.concat(g.items), []);
  const COLSPAN = 2 + MONTHS.length; // 항목 + 일괄 + 12개월

  const yearSelect = document.getElementById("priceYear");
  const reloadBtn = document.getElementById("priceReload");
  const headRow = document.getElementById("priceHeadRow");
  const body = document.getElementById("priceBody");
  const msg = document.getElementById("priceMsg");

  function readSharedYear() {
    try { const p = JSON.parse(localStorage.getItem(SHARED_PERIOD_KEY) || "{}"); return Number(p.year) || new Date().getFullYear(); }
    catch (e) { return new Date().getFullYear(); }
  }
  let currentYear = readSharedYear();

  (function initYears() {
    const now = new Date().getFullYear();
    for (let y = now - 5; y <= now + 2; y += 1) {
      const opt = document.createElement("option");
      opt.value = String(y); opt.textContent = `${y}년`;
      yearSelect.appendChild(opt);
    }
    yearSelect.value = String(currentYear);
  })();

  function setMsg(text, ok) { msg.textContent = text || ""; msg.className = "up-msg " + (ok ? "ok" : "err"); }

  // ── 천단위 구분 기호 ────────────────────────────────────────
  //  화면에는 18,760 으로 보이고 입력·저장은 숫자만 쓴다.
  function rawNumber(value) { return String(value == null ? "" : value).replace(/[,\s]/g, ""); }
  function withCommas(value) {
    const raw = rawNumber(value);
    if (raw === "") return "";
    const number = Number(raw);
    if (!Number.isFinite(number)) return String(value);
    return number.toLocaleString("ko-KR", { maximumFractionDigits: 4 });
  }
  function showFormatted(input) { if (input) input.value = withCommas(input.value); }

  // 입력 중에는 숫자 그대로, 벗어나면 콤마 표기로.
  body.addEventListener("focusin", (e) => {
    const input = e.target.closest("input[data-role]");
    if (input) input.value = rawNumber(input.value);
  });
  body.addEventListener("focusout", (e) => {
    const input = e.target.closest("input[data-role]");
    if (input) showFormatted(input);
  });
  function esc(s) { return String(s).replace(/[&<>"]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c])); }

  function buildTable() {
    // 헤더: 항목 · 일괄 뒤에 1~12월
    MONTHS.forEach((m) => {
      const th = document.createElement("th");
      th.textContent = `${m}월`;
      headRow.appendChild(th);
    });

    const rows = [];
    GROUPS.forEach((g) => {
      const badge = g.badge ? ` <span class="grp-badge">${esc(g.badge)}</span>` : "";
      rows.push(`<tr class="group-row"><td class="group-cell" colspan="${COLSPAN}">${esc(g.facility)}${badge}</td></tr>`);
      g.items.forEach((item) => {
        const isAuto = item.method === "auto";
        const methodBadge = isAuto
          ? `<span class="mtd mtd-auto" title="${esc(item.note || "자동계산")}">자동</span>`
          : `<span class="mtd mtd-direct">직접</span>`;
        const unit = item.unit ? ` <span class="up-unit">${esc(item.unit)}</span>` : "";
        // 다른 페이지 단가 칸과 연동되는 항목 표시
        const link = UP.priceLink(item.code);
        const linkBadge = link
          ? ` <span class="mtd mtd-link" title="이 단가를 고치면 해당 월 ${esc(link.label)} 에 일괄 적용됩니다">연동</span>`
          : "";
        const note = isAuto && item.note ? `<div class="auto-note">${esc(item.note)}</div>` : "";
        const dis = isAuto ? " disabled" : "";
        const bulk = `<td class="bulk-col"><input data-role="bulk" data-code="${item.code}" inputmode="decimal" placeholder="—"${dis}></td>`;
        const months = MONTHS.map((m) =>
          `<td><input data-role="month" data-code="${item.code}" data-month="${m}" inputmode="decimal"${dis}></td>`
        ).join("");
        rows.push(`<tr class="item-row${isAuto ? " auto-row" : ""}">
          <th class="item-col" scope="row"><span class="item-label">${esc(item.label)}${unit}</span> ${methodBadge}${linkBadge}${note}</th>
          ${bulk}${months}
        </tr>`);
      });
    });
    body.innerHTML = rows.join("");
  }

  function monthInput(code, m) { return body.querySelector(`input[data-role="month"][data-code="${code}"][data-month="${m}"]`); }

  async function load() {
    reloadBtn.disabled = true;
    setMsg("불러오는 중…", true);
    try {
      const reg = await UP.fetchRegistered(currentYear, DETAIL_TABLE);
      ALL_ITEMS.forEach((item) => {
        MONTHS.forEach((m) => {
          const input = monthInput(item.code, m);
          if (input) input.value = withCommas(UP.registeredValue(reg, item.code, m) ?? "");
        });
        const bulk = body.querySelector(`input[data-role="bulk"][data-code="${item.code}"]`);
        if (bulk) bulk.value = "";
      });
      setMsg("", true);
    } catch (e) {
      setMsg("불러오기 실패", false);
    } finally {
      reloadBtn.disabled = false;
    }
  }

  async function saveMonth(code, m, value) {
    try {
      await UP.saveRegistered(currentYear, code, m, value, DETAIL_TABLE);
      const link = UP.priceLink(code);
      if (!link) { setMsg("저장됨", true); return; }
      // 연동 항목은 그 달 대상 페이지 전체 일자에 함께 반영한다.
      setMsg(`저장됨 · ${m}월 ${link.label} 반영 중…`, true);
      const result = await UP.applyPriceLink(code, currentYear, m, value);
      setMsg(result.applied
        ? `저장됨 · ${m}월 ${link.label} ${result.applied}일 적용`
        : "저장됨", true);
    } catch (e) { setMsg("저장 실패", false); }
  }

  // 개별 월 저장 (change/blur)
  body.addEventListener("change", (e) => {
    const input = e.target.closest('input[data-role="month"]');
    if (!input || input.disabled) return;
    saveMonth(input.dataset.code, Number(input.dataset.month), rawNumber(input.value));
  });

  // 일괄: 값 입력 후 blur/Enter → 12개월 채우고 저장
  async function applyBulk(input) {
    if (input.disabled) return;
    const code = input.dataset.code;
    const raw = rawNumber(input.value);
    if (raw === "") return;
    setMsg("일괄 적용 중…", true);
    for (const m of MONTHS) {
      const mi = monthInput(code, m);
      if (mi) mi.value = withCommas(raw);
    }
    try {
      await Promise.all(MONTHS.map((m) => UP.saveRegistered(currentYear, code, m, raw, DETAIL_TABLE)));
      input.value = "";

      const link = UP.priceLink(code);
      if (!link) { setMsg("일괄 적용·저장 완료", true); return; }
      // 연동 항목은 12개월 각각의 대상 페이지에도 일괄 반영한다.
      for (const m of MONTHS) {
        setMsg(`${link.label} 반영 중… (${m}/12월)`, true);
        await UP.applyPriceLink(code, currentYear, m, raw);
      }
      setMsg(`일괄 적용·저장 완료 · ${link.label} 12개월 반영`, true);
    } catch (e) { setMsg("일괄 저장 실패", false); }
  }
  body.addEventListener("change", (e) => {
    const bulk = e.target.closest('input[data-role="bulk"]');
    if (bulk) applyBulk(bulk);
  });
  body.addEventListener("keydown", (e) => {
    const bulk = e.target.closest('input[data-role="bulk"]');
    if (bulk && e.key === "Enter") { e.preventDefault(); applyBulk(bulk); }
  });

  reloadBtn.addEventListener("click", () => {
    currentYear = Number(yearSelect.value) || new Date().getFullYear();
    try {
      const prev = JSON.parse(localStorage.getItem(SHARED_PERIOD_KEY) || "{}");
      localStorage.setItem(SHARED_PERIOD_KEY, JSON.stringify({ year: currentYear, month: prev.month || "01" }));
    } catch (e) {}
    load();
  });

  buildTable();
  load();
});
