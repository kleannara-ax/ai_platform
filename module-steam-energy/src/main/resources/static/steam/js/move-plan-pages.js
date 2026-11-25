/* ============================================================
   move-plan-pages.js — 이동계획 수립 화면 (energy-move-plan.html)

   · 저장하는 것: 호기별 생산계획, 보일러 기준값, 월 기준값, 일자별 운휴 (/steam-energy-api/move-plan-*)
   · 가져다 쓰는 것 (다른 화면의 값, 읽기만):
       전년 종합 원단위        /energy-ratio-steam-units        (연료비 배분비율 › 종합 원단위 › 기타 스팀량 포함)
       보일러별 일 실적        /combo-daily-productions         (복합보일러 › 스팀생산실적)
       폐합성 2호기 실 스팀량  /incinerator-contract-dailies    (스팀생산실적의 2호 칸이 비어 있을 때)
       유동상 도급 스팀량      /fluidized-contract-dailies      (정산 톤/시간 기본값)
       LNG 사용량              /lng-daily-usages                (스팀 톤당 LNG 사용량 기본값)
       단가                    /prices/year/{연도}              (단가 입력)
   · 계산은 move-plan-calc.js 가 한다. 필요 스팀량·배분량·비용은 저장하지 않는다.
   ============================================================ */
(function () {
  "use strict";

  const Api = window.SteamEnergyApi;
  const Calc = window.MovePlanCalc;
  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  if (!Api || !Calc || !yearSelect || !monthSelect) return;

  const $ = (id) => document.getElementById(id);
  const num = Calc.num;
  const PERIOD_KEY = "steamlog:shared-period";
  const MACHINES = Calc.MACHINES;
  const BOILERS = Calc.BOILERS;

  /** 실적도 단가도 없을 때 쓰는 값 */
  const FALLBACK = {
    maxTonDay: { WASTE1: 0, WASTE2: 384, COMBO: 1704, FLUID: 432, LNG20: 480, LNG50: 1200 },
    minTonDay: { WASTE1: 0, WASTE2: 336, COMBO: 0, FLUID: 300, LNG20: 0, LNG50: 0 },
    waste2BillCapTon: 336,
    fluidBillTonHour: 21.1,
    lngM3PerTon: 66,
  };
  /** 최근 실적에서 하루 최대를 잡는 보일러 (LNG 는 남는 양을 받는 보일러라 설비 용량을 쓴다) */
  const CAP_FROM_ACTUAL = ["WASTE2", "COMBO", "FLUID"];
  const STEAM_PRICE_CODE = { WASTE2: "waste2_steam", FLUID: "fluid_steam" };
  const IDLE_PRICE_CODE = { WASTE1: "waste1_fixed_hr", WASTE2: "waste2_fixed_hr", FLUID: "fluid_fixed_hr" };
  const DAY_FIELDS = MACHINES.map((m) => m.down).concat(BOILERS.map((b) => b.stop));
  const MONTH_FIELDS = ["actualUntilDay", "adjustYn", "waste2BillCapTon", "comboGuaranteeTon", "comboBscPrice", "comboAscPrice",
    "fluidBillTonHour", "fluidImprovementCost", "lngM3PerTon", "lngUnitPrice", "note"];

  let activeTab = "daily";
  let current = null;   // { year, month, yd, prev, resolved, result }
  const yearCache = new Map();

  // ───────────────────────── 공통 ─────────────────────────
  const fmt0 = (v) => (v === null || v === undefined || !Number.isFinite(v) ? "" : Math.round(v).toLocaleString("ko-KR"));
  const fmtN = (v, digits) => (v === null || v === undefined || !Number.isFinite(v) ? ""
    : v.toLocaleString("ko-KR", { minimumFractionDigits: digits, maximumFractionDigits: digits }));
  /** 입력칸에 보여 줄 저장값 — 뒤의 0 을 뗀다 */
  const plain = (v, digits) => {
    const n = num(v);
    if (n === null) return "";
    return String(Number(n.toFixed(digits === undefined ? 6 : digits)));
  };
  const esc = (s) => String(s === null || s === undefined ? "" : s)
    .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  const iso = (y, m, d) => Api.isoDate(y, m, d);

  function showMessage(text, kind) {
    const box = $("mpMessage");
    if (!box) return;
    if (!text) { box.hidden = true; box.textContent = ""; return; }
    box.hidden = false;
    box.className = "mp-message" + (kind ? " mp-message-" + kind : "");
    box.textContent = text;
  }

  // ───────────────────────── 불러오기 ─────────────────────────
  function loadYear(year) {
    if (!yearCache.has(year)) yearCache.set(year, fetchYear(year));
    return yearCache.get(year);
  }

  async function fetchYear(year) {
    const failed = [];
    const get = (path) => Api.get(path).catch((error) => {
      console.warn("[move-plan] 불러오기 실패", path, error);
      failed.push(path);
      return [];
    });
    const [machines, boilers, months, days, production, contract, fluidContract, lng, prices, units] = await Promise.all([
      get(`/move-plan-machines?year=${year}`),
      get(`/move-plan-boilers?year=${year}`),
      get(`/move-plan-months?year=${year}`),
      get(`/move-plan-days?year=${year}`),
      get(`/combo-daily-productions?year=${year}`),
      get(`/incinerator-contract-dailies?year=${year}`),
      get(`/fluidized-contract-dailies?year=${year}`),
      get(`/lng-daily-usages?year=${year}`),
      get(`/prices/year/${year}`),
      get(`/energy-ratio-steam-units?year=${year}`),
    ]);
    const byDate = (rows, field) => {
      const map = new Map();
      (rows || []).forEach((row) => map.set(String(row[field || "usageDate"]).slice(0, 10), row));
      return map;
    };
    const priceMap = new Map();
    (prices || []).forEach((p) => priceMap.set(`${p.itemCode}|${p.monthNo}`, num(p.priceValue)));
    return {
      year, failed,
      machines: new Map((machines || []).map((r) => [`${r.monthNo}|${r.machineCode}`, r])),
      boilers: new Map((boilers || []).map((r) => [`${r.monthNo}|${r.boilerCode}`, r])),
      months: new Map((months || []).map((r) => [r.monthNo, r])),
      days: byDate(days, "planDate"),
      production: byDate(production),
      contract: byDate(contract),
      fluidContract: byDate(fluidContract),
      lng: byDate(lng),
      prices: priceMap,
      units: new Map((units || []).map((r) => [r.monthNo, r])),
    };
  }

  /** 그 날의 보일러별 실적. 값이 하나도 없으면 null. */
  function actualOf(yd, date) {
    const p = yd.production.get(date);
    if (!p) return null;
    let waste2 = num(p.wasteSteam2);
    if (waste2 === null) {
      const c = yd.contract.get(date);   // 스팀생산실적의 2호 칸이 비어 있으면 도급내역 실 스팀량을 따른다
      if (c) waste2 = num(c.realSteam2);
    }
    const contract = yd.contract.get(date) || {};
    const fluidContract = yd.fluidContract.get(date) || {};
    const a = {
      WASTE1: num(p.wasteSteam1), WASTE2: waste2, COMBO: num(p.comboSteam), FLUID: num(p.fluidizedSteam),
      LNG20: num(p.kn20), LNG50: num(p.kn50), EXT: num(p.externalSteam), fluidRunHours: num(p.runtimeFluidized),
      // 도급내역 화면에 적힌 운휴시간 — 있으면 그 날의 고정비는 이 시간으로 계산한다
      idleHours: { WASTE1: num(contract.u1Runtime), WASTE2: num(contract.u2Runtime), FLUID: num(fluidContract.stopHours) },
    };
    const has = ["WASTE1", "WASTE2", "COMBO", "FLUID", "LNG20", "LNG50", "EXT"].some((k) => a[k] !== null);
    return has ? a : null;
  }

  /** 단가 입력 값 — 그 달에 없으면 가장 가까운 앞 달 (최대 24개월) */
  function priceOf(code, year, month, yd, prev) {
    let y = year;
    let m = month;
    for (let i = 0; i < 24; i += 1) {
      const source = y === yd.year ? yd : (prev && y === prev.year ? prev : null);
      if (!source) break;
      const v = source.prices.get(`${code}|${m}`);
      if (v !== null && v !== undefined) return { value: v, exact: i === 0, ym: `${y}-${String(m).padStart(2, "0")}` };
      m -= 1;
      if (m < 1) { m = 12; y -= 1; }
    }
    return { value: null, exact: false, ym: null };
  }

  /** 계획 달 직전 12개월의 실적 요약 — 하루 최대 기본값과 정산 기준 기본값의 근거 */
  function recentStats(year, month, yd, prev) {
    const daily = {};
    const monthly = {};
    BOILERS.forEach((b) => { daily[b.code] = []; monthly[b.code] = new Map(); });
    let fluidBill = 0, fluidHours = 0, lngM3 = 0, lngSteam = 0;
    let y = year;
    let m = month;
    for (let i = 0; i < 12; i += 1) {
      m -= 1;
      if (m < 1) { m = 12; y -= 1; }
      const source = y === yd.year ? yd : (prev && y === prev.year ? prev : null);
      if (!source) continue;
      const dim = Calc.daysInMonth(y, m);
      for (let d = 1; d <= dim; d += 1) {
        const date = iso(y, m, d);
        const a = actualOf(source, date);
        if (a) {
          BOILERS.forEach((b) => {
            const v = a[b.code];
            if (v === null || v <= 0) return;
            daily[b.code].push(v);
            const key = `${y}-${m}`;
            monthly[b.code].set(key, (monthly[b.code].get(key) || 0) + v);
          });
          const contractRow = source.fluidContract.get(date);
          const billed = contractRow ? num(contractRow.steamTon) : null;
          if (billed !== null && billed > 0 && a.fluidRunHours !== null && a.fluidRunHours > 0) {
            fluidBill += billed;
            fluidHours += a.fluidRunHours;
          }
          const lngRow = source.lng.get(date);
          const steam = (a.LNG20 || 0) + (a.LNG50 || 0);
          if (lngRow && steam > 0) {
            const gas = (num(lngRow.boiler20t1) || 0) + (num(lngRow.boiler20t2) || 0) + (num(lngRow.boiler50t) || 0);
            if (gas > 0) { lngM3 += gas; lngSteam += steam; }
          }
        }
      }
    }
    const stats = { topAverage: {}, monthMax: {}, days: {} };
    BOILERS.forEach((b) => {
      stats.topAverage[b.code] = Calc.topDecileAverage(daily[b.code]);
      stats.days[b.code] = daily[b.code].length;
      const sums = Array.from(monthly[b.code].values());
      stats.monthMax[b.code] = sums.length ? Math.max.apply(null, sums) : null;
    });
    stats.fluidBillTonHour = fluidHours > 0 ? fluidBill / fluidHours : null;
    stats.lngM3PerTon = lngSteam > 0 ? lngM3 / lngSteam : null;
    return stats;
  }

  /** 저장값 + 기본값 → 계산에 넣을 값 */
  function resolve(year, month, yd, prev) {
    const dim = Calc.daysInMonth(year, month);
    const stats = recentStats(year, month, yd, prev);
    const lastYearUnits = prev ? prev.units : new Map();
    const unitRow = lastYearUnits.get(month) || {};

    const machines = {};
    MACHINES.forEach((m) => {
      const saved = yd.machines.get(`${month}|${m.code}`) || {};
      let lastYear = num(unitRow[m.unit]);
      let source = lastYear !== null && lastYear > 0 ? "lastYear" : null;
      if (!source) {
        // 전년 같은 달 값이 없으면(그 달에 안 돌린 호기) 전년에 값이 있는 달의 평균
        const values = [];
        lastYearUnits.forEach((row) => { const v = num(row[m.unit]); if (v !== null && v > 0) values.push(v); });
        lastYear = values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
        source = lastYear !== null ? "lastYearAverage" : null;
      }
      const override = num(saved.unitOverride);
      machines[m.code] = {
        productionTon: num(saved.productionTon), runtimeHours: num(saved.runtimeHours), planKind: saved.planKind || null,
        unitOverride: override, lastYearUnit: lastYear, lastYearSource: source,
        unit: override !== null ? override : lastYear,
      };
    });

    const boilers = {};
    BOILERS.forEach((b) => {
      const saved = yd.boilers.get(`${month}|${b.code}`) || {};
      const top = stats.topAverage[b.code];
      const defaults = {
        use: b.code !== "WASTE1",
        maxTonDay: CAP_FROM_ACTUAL.includes(b.code) && top ? Math.round(top) : FALLBACK.maxTonDay[b.code],
        maxFromActual: CAP_FROM_ACTUAL.includes(b.code) && !!top,
        minTonDay: FALLBACK.minTonDay[b.code],
        steamUnitPrice: STEAM_PRICE_CODE[b.code] ? priceOf(STEAM_PRICE_CODE[b.code], year, month, yd, prev).value : null,
        idleHourPrice: IDLE_PRICE_CODE[b.code] ? priceOf(IDLE_PRICE_CODE[b.code], year, month, yd, prev).value : null,
      };
      const pick = (field) => (num(saved[field]) !== null ? num(saved[field]) : defaults[field]);
      boilers[b.code] = {
        saved, defaults,
        use: saved.useYn === "Y" ? true : saved.useYn === "N" ? false : defaults.use,
        maxTonDay: pick("maxTonDay"), minTonDay: pick("minTonDay"),
        steamUnitPrice: pick("steamUnitPrice"), idleHourPrice: pick("idleHourPrice"),
      };
    });

    const savedMonth = yd.months.get(month) || {};
    const bsc = priceOf("comb_bsc", year, month, yd, prev).value;
    const bscFactor = priceOf("comb_bsc_factor", year, month, yd, prev).value;
    const asc = priceOf("comb_asc", year, month, yd, prev).value;
    const ascFactor = priceOf("comb_asc_factor", year, month, yd, prev).value;
    const settingDefaults = {
      waste2BillCapTon: FALLBACK.waste2BillCapTon,
      comboGuaranteeTon: priceOf("comb_bsc_guarantee", year, month, yd, prev).value,
      comboBscPrice: bsc === null ? null : Math.round(bsc * (bscFactor === null ? 1 : bscFactor)),
      comboAscPrice: asc === null ? null : Math.round(asc * (ascFactor === null ? 1 : ascFactor)),
      fluidBillTonHour: stats.fluidBillTonHour !== null ? Number(stats.fluidBillTonHour.toFixed(1)) : FALLBACK.fluidBillTonHour,
      fluidBillFromActual: stats.fluidBillTonHour !== null,
      fluidImprovementCost: 0,
      lngM3PerTon: stats.lngM3PerTon !== null ? Number(stats.lngM3PerTon.toFixed(1)) : FALLBACK.lngM3PerTon,
      lngFromActual: stats.lngM3PerTon !== null,
      lngUnitPrice: priceOf("lng_unit", year, month, yd, prev).value,
    };
    const pickSetting = (field) => (num(savedMonth[field]) !== null ? num(savedMonth[field]) : settingDefaults[field]);
    const settings = {
      saved: savedMonth, defaults: settingDefaults,
      actualUntilDay: num(savedMonth.actualUntilDay),
      adjust: savedMonth.adjustYn !== "N",
      waste2BillCapTon: pickSetting("waste2BillCapTon"),
      comboGuaranteeTon: pickSetting("comboGuaranteeTon"),
      comboBscPrice: pickSetting("comboBscPrice"),
      comboAscPrice: pickSetting("comboAscPrice"),
      fluidBillTonHour: pickSetting("fluidBillTonHour"),
      fluidImprovementCost: pickSetting("fluidImprovementCost"),
      lngM3PerTon: pickSetting("lngM3PerTon"),
      lngUnitPrice: pickSetting("lngUnitPrice"),
    };

    const days = {};
    const actuals = {};
    for (let d = 1; d <= dim; d += 1) {
      const date = iso(year, month, d);
      days[d] = yd.days.get(date) || {};
      const a = actualOf(yd, date);
      if (a) actuals[d] = a;
    }
    return { year, month, machines, boilers, settings, days, actuals, stats };
  }

  function compute(year, month, yd, prev) {
    const resolved = resolve(year, month, yd, prev);
    return { resolved, result: Calc.computeMonth(resolved) };
  }

  // ───────────────────────── 저장 ─────────────────────────
  async function saveMachine(code, field, value) {
    const { year, month, yd } = current;
    const key = `${month}|${code}`;
    const row = Object.assign({ yearNo: year, monthNo: month, machineCode: code }, yd.machines.get(key) || {});
    row[field] = value;
    yd.machines.set(key, row);
    refresh();
    await persist(() => Api.post("/move-plan-machines", {
      yearNo: year, monthNo: month, machineCode: code,
      productionTon: num(row.productionTon), runtimeHours: num(row.runtimeHours),
      planKind: row.planKind || null, unitOverride: num(row.unitOverride),
    }));
  }

  async function saveBoiler(code, field, value) {
    const { year, month, yd } = current;
    const key = `${month}|${code}`;
    const row = Object.assign({ yearNo: year, monthNo: month, boilerCode: code }, yd.boilers.get(key) || {});
    row[field] = value;
    yd.boilers.set(key, row);
    refresh();
    await persist(() => Api.post("/move-plan-boilers", {
      yearNo: year, monthNo: month, boilerCode: code,
      useYn: row.useYn || null, maxTonDay: num(row.maxTonDay), minTonDay: num(row.minTonDay),
      steamUnitPrice: num(row.steamUnitPrice), idleHourPrice: num(row.idleHourPrice),
    }));
  }

  async function saveMonthSetting(field, value) {
    const { year, month, yd } = current;
    const row = Object.assign({ yearNo: year, monthNo: month }, yd.months.get(month) || {});
    row[field] = value;
    yd.months.set(month, row);
    refresh();
    const body = { yearNo: year, monthNo: month };
    MONTH_FIELDS.forEach((f) => {
      body[f] = f === "adjustYn" || f === "note" ? (row[f] || null) : num(row[f]);
    });
    await persist(() => Api.post("/move-plan-months", body));
  }

  async function saveDay(day, field, hours, focusKey) {
    const { year, month, yd } = current;
    const date = iso(year, month, day);
    const row = Object.assign({ planDate: date }, yd.days.get(date) || {});
    row[field] = hours;
    yd.days.set(date, row);
    refresh(focusKey);
    const body = { planDate: date, note: row.note || null };
    DAY_FIELDS.forEach((f) => { body[f] = num(row[f]); });
    await persist(() => Api.post("/move-plan-days", body));
  }

  async function persist(call) {
    try {
      await call();
      showMessage("");
    } catch (error) {
      console.error(error);
      showMessage("저장하지 못했습니다: " + (error && error.message ? error.message : error) + " — 화면의 값은 저장되지 않은 상태입니다.", "error");
    }
  }

  // ───────────────────────── 그리기: 일별 ─────────────────────────
  function refresh(focusKey) {
    if (!current) return;
    // 표를 다시 그리면 입력칸이 새로 만들어진다 — 지금 포커스가 있는 칸을 기억했다가 되돌린다 (Tab 으로 이어 넣을 수 있게).
    const active = document.activeElement;
    const keepKey = focusKey || (active instanceof HTMLElement && active.dataset ? active.dataset.focusKey : null);
    const { year, month, yd, prev } = current;
    const out = compute(year, month, yd, prev);
    current.resolved = out.resolved;
    current.result = out.result;
    renderSummary();
    renderMachines();
    renderBoilers();
    renderSettings();
    renderControls();
    renderDays();
    renderCost();
    if (keepKey) {
      const input = document.querySelector(`[data-focus-key="${keepKey}"]`);
      if (input) { input.focus(); if (typeof input.select === "function") input.select(); }
    }
  }

  function renderSummary() {
    const { result, month } = current;
    const box = $("mpSummary");
    const cards = [];
    const hasPlan = result.demandTarget !== null;
    cards.push(card("월 필요 스팀량", hasPlan ? fmt0(result.demandTarget) + " 톤" : "—",
      hasPlan ? "생산량 × 원단위" : "생산계획이 없습니다"));
    if (result.actualUntilDay > 0 && result.actualSum > 0) {
      cards.push(card(`실적 누계 (1~${result.actualUntilDay}일)`, fmt0(result.actualSum) + " 톤", "복합보일러 › 스팀생산실적"));
      cards.push(card("남은 날 계획", fmt0(result.planSum) + " 톤",
        result.adjust && Math.abs(result.adjustFactor - 1) > 0.0005 ? `보정 ×${result.adjustFactor.toFixed(3)}` : ""));
    }
    cards.push(card(`${month}월 합계`, fmt0(result.totalSteam) + " 톤",
      !hasPlan ? "" : Math.abs(result.diff) < 0.5 ? "필요 스팀량과 같음"
        : `필요 스팀량보다 ${fmt0(Math.abs(result.diff))} 톤 ${result.diff > 0 ? "많음" : "적음"}`));
    const lngShare = result.totalSteam > 0 ? (result.lngTons / result.totalSteam) * 100 : 0;
    cards.push(card("LNG 보일러", fmt0(result.lngTons) + " 톤", `전체의 ${lngShare.toFixed(1)}%`, result.lngTons > 0.5 ? "warn" : "good"));
    if (result.shortageSum > 0.5) {
      cards.push(card("공급 부족", fmt0(result.shortageSum) + " 톤", "모든 보일러를 최대로 돌려도 모자랍니다", "bad"));
    }
    cards.push(card("예상 비용", result.costTotal > 0 ? fmtN(result.costTotal / 1e6, 1) + " 백만원" : "—",
      result.costKnown ? (result.costPerTon ? `스팀 톤당 ${fmt0(result.costPerTon)} 원` : "") : "단가가 없는 항목은 빠져 있습니다"));
    box.innerHTML = cards.join("");
    function card(label, value, sub, tone) {
      return `<div class="mp-stat${tone ? " mp-stat-" + tone : ""}"><span class="mp-stat-label">${esc(label)}</span>`
        + `<strong class="mp-stat-value">${esc(value)}</strong><span class="mp-stat-sub">${esc(sub || "")}</span></div>`;
    }
  }

  function renderMachines() {
    const { result, resolved, year, month } = current;
    const rows = result.machines.map((m) => {
      const r = resolved.machines[m.code];
      const lastYearText = r.lastYearUnit === null ? "" : fmtN(r.lastYearUnit, 4);
      const tag = r.lastYearSource === "lastYearAverage" ? ' <span class="mp-tag">연평균</span>' : "";
      const hoursDiff = m.planHours !== null && Math.abs(m.runHours - m.planHours) >= 24;
      return `<tr>
        <th>${esc(m.label)}${r.planKind ? ` <span class="mp-tag mp-tag-soft">${esc(r.planKind)}</span>` : ""}</th>
        <td>${input("machine", m.code, "productionTon", plain(r.productionTon, 2), "")}</td>
        <td>${input("machine", m.code, "runtimeHours", plain(r.runtimeHours, 1), "")}</td>
        <td class="computed${hoursDiff ? " mp-warn-text" : ""}" title="일별 계획에서 고른 운휴를 뺀 가동시간">${fmt0(m.runHours)}</td>
        <td class="computed" title="${year - 1}년 ${month}월 종합 원단위 (기타 스팀량 포함)">${lastYearText}${tag}</td>
        <td>${input("machine", m.code, "unitOverride", plain(r.unitOverride), lastYearText)}</td>
        <td class="computed mp-strong">${m.steamTon === null ? "" : fmt0(m.steamTon)}</td>
      </tr>`;
    });
    const production = result.machines.reduce((s, m) => s + (m.productionTon || 0), 0);
    rows.push(`<tr class="summary-row"><th>합계</th><td>${production ? fmt0(production) : ""}</td><td></td><td></td>
      <td>${result.demandTarget !== null && production ? fmtN(result.demandTarget / production, 4) : ""}</td><td></td>
      <td>${result.demandTarget === null ? "" : fmt0(result.demandTarget)}</td></tr>`);
    $("mpMachineTable").innerHTML = `<thead><tr>
      <th>호기</th><th>생산량(톤)</th><th>계획 가동시간(Hr)</th><th>운휴 반영<br>가동시간(Hr)</th>
      <th>${year - 1}년 ${month}월<br>원단위</th><th>원단위<br>직접 입력</th><th>필요 스팀량(톤)</th>
    </tr></thead><tbody>${rows.join("")}</tbody>`;

    const averaged = MACHINES.filter((m) => resolved.machines[m.code].lastYearSource === "lastYearAverage").map((m) => m.label);
    const missing = MACHINES.filter((m) => resolved.machines[m.code].unit === null && resolved.machines[m.code].productionTon !== null).map((m) => m.label);
    const unplaced = result.machines.filter((m) => m.unplaced).map((m) => m.label);
    const notes = [`원단위는 연료비 배분비율 › 종합 원단위의 ${year - 1}년 ${month}월 값(기타 스팀량 포함)입니다. 직접 입력하면 그 값이 우선합니다.`];
    if (averaged.length) notes.push(`${averaged.join(", ")}: ${year - 1}년 ${month}월 값이 없어 ${year - 1}년 평균을 썼습니다.`);
    if (missing.length) notes.push(`${missing.join(", ")}: 전년 원단위가 없습니다. 원단위를 직접 입력하세요.`);
    if (unplaced.length) notes.push(`${unplaced.join(", ")}: 한 달 내내 운휴로 골라 스팀량을 넣을 날이 없습니다.`);
    // 계획 가동시간과 하루 넘게 차이 나는 호기 — 운휴일을 고르라는 안내
    const gaps = result.machines.filter((m) => m.planHours !== null && m.productionTon !== null && m.runHours - m.planHours >= 24)
      .map((m) => `${m.label} 약 ${Math.round((m.runHours - m.planHours) / 24)}일`);
    if (gaps.length) notes.push(`계획 가동시간보다 길게 잡혀 있습니다 — 일별 계획에서 운휴일을 고르세요: ${gaps.join(", ")}.`);
    $("mpMachineHint").textContent = notes.join(" ");
  }

  function input(kind, code, field, value, placeholder, extra) {
    return `<input type="text" inputmode="decimal" class="mp-input" data-kind="${kind}" data-code="${esc(code)}" data-field="${field}"`
      + ` data-focus-key="${kind}-${esc(code)}-${field}" value="${esc(value)}" placeholder="${esc(placeholder || "")}"${extra || ""} />`;
  }

  function renderBoilers() {
    const { resolved } = current;
    const rows = BOILERS.map((b, index) => {
      const r = resolved.boilers[b.code];
      const stat = resolved.stats;
      const top = stat.topAverage[b.code];
      const monthMax = stat.monthMax[b.code];
      const reference = top ? `${fmt0(top)} / ${fmt0(monthMax)}` : "";
      const steamCell = b.code === "COMBO" ? `<td class="computed mp-muted">BSC · ASC</td>`
        : b.lng ? `<td class="computed mp-muted">LNG 단가</td>`
        : `<td>${input("boiler", b.code, "steamUnitPrice", plain(r.saved.steamUnitPrice), fmt0(r.defaults.steamUnitPrice))}</td>`;
      const idleCell = b.idleFee
        ? `<td>${input("boiler", b.code, "idleHourPrice", plain(r.saved.idleHourPrice), fmt0(r.defaults.idleHourPrice))}</td>`
        : `<td class="computed mp-muted">없음</td>`;
      return `<tr class="${r.use ? "" : "mp-row-off"}">
        <th>${index + 1}</th>
        <th class="mp-left">${esc(b.label)}</th>
        <td><input type="checkbox" class="mp-check-input" data-kind="boiler-use" data-code="${b.code}" ${r.use ? "checked" : ""} aria-label="${esc(b.label)} 배분" /></td>
        <td>${input("boiler", b.code, "maxTonDay", plain(r.saved.maxTonDay), fmt0(r.defaults.maxTonDay))}</td>
        <td>${input("boiler", b.code, "minTonDay", plain(r.saved.minTonDay), fmt0(r.defaults.minTonDay))}</td>
        ${steamCell}${idleCell}
        <td class="computed" title="직전 12개월 일 실적 상위 10% 평균 / 월 합계 최대">${reference}</td>
      </tr>`;
    });
    $("mpBoilerTable").innerHTML = `<thead><tr>
      <th>순서</th><th>보일러</th><th>배분</th><th>하루 최대<br>(톤/일)</th><th>하루 최소<br>운용량(톤/일)</th>
      <th>스팀단가<br>(원/톤)</th><th>운휴 고정비<br>(원/시간)</th><th>직전 12개월 실적<br>일 상위 10% 평균 / 월 최대</th>
    </tr></thead><tbody>${rows.join("")}</tbody>`;
  }

  function renderSettings() {
    const s = current.resolved.settings;
    const d = s.defaults;
    const cell = (label, field, placeholder, title) => `<th class="mp-left" title="${esc(title || "")}">${esc(label)}</th>`
      + `<td>${input("setting", "", field, plain(s.saved[field]), placeholder)}</td>`;
    $("mpSettingTable").innerHTML = `<thead><tr><th colspan="4">정산 기준 — 비운 칸은 단가 입력·최근 실적 값을 씁니다</th></tr></thead><tbody>
      <tr>${cell("폐합성 2호기 정산 상한(톤/일)", "waste2BillCapTon", fmt0(d.waste2BillCapTon), "도급내역 화면과 같이 하루 이 양까지만 스팀비를 정산합니다")}
          ${cell("복합 보증량(톤/월)", "comboGuaranteeTon", fmt0(d.comboGuaranteeTon), "단가 입력의 BSC 보증량. 이보다 덜 써도 보증량만큼 BSC 를 냅니다")}</tr>
      <tr>${cell("복합 BSC 적용단가(원/톤)", "comboBscPrice", fmt0(d.comboBscPrice), "단가 입력의 BSC 단가 × FACTOR")}
          ${cell("복합 ASC 적용단가(원/톤)", "comboAscPrice", fmt0(d.comboAscPrice), "보증량을 넘는 양에 쓰는 단가 (ASC 단가 × FACTOR)")}</tr>
      <tr>${cell("유동상 정산 스팀량(톤/시간)", "fluidBillTonHour", fmtN(d.fluidBillTonHour, 1), d.fluidBillFromActual ? "직전 12개월 도급 스팀량 ÷ 가동시간" : "실적이 없어 기본값을 씁니다")}
          ${cell("유동상 설비개선비(원/월)", "fluidImprovementCost", "0", "")}</tr>
      <tr>${cell("LNG 스팀 톤당 사용량(N㎥/톤)", "lngM3PerTon", fmtN(d.lngM3PerTon, 1), d.lngFromActual ? "직전 12개월 LNG 사용량 ÷ LNG 보일러 스팀량" : "실적이 없어 기본값을 씁니다")}
          ${cell("LNG 단가(원/N㎥)", "lngUnitPrice", fmtN(d.lngUnitPrice, 2), "단가 입력의 LNG 단가")}</tr>
    </tbody>`;
  }

  function renderControls() {
    const { result, resolved } = current;
    const select = $("mpActualUntil");
    const saved = resolved.settings.actualUntilDay;
    const options = [`<option value="">자동 — ${result.lastActualDay > 0 ? `실적이 있는 ${result.lastActualDay}일까지` : "실적 없음"}</option>`,
      `<option value="0">실적을 쓰지 않음</option>`];
    for (let d = 1; d <= result.daysInMonth; d += 1) options.push(`<option value="${d}">${d}일까지</option>`);
    select.innerHTML = options.join("");
    select.value = saved === null ? "" : String(saved);
    $("mpAdjust").checked = resolved.settings.adjust;
  }

  function renderDays() {
    const { result, resolved } = current;
    const showExt = result.extTons > 0.5;
    const head1 = [`<th rowspan="2">일</th>`, `<th rowspan="2">구분</th>`,
      `<th colspan="${MACHINES.length}">설비 운휴시간</th>`, `<th rowspan="2">필요 스팀량<br>(T/D)</th>`];
    const head2 = MACHINES.map((m) => `<th>${esc(m.label)}</th>`);
    BOILERS.forEach((b, index) => {
      head1.push(`<th colspan="2">${index + 1}. ${esc(b.short)}</th>`);
      head2.push(`<th title="보일러 정비 운휴시간 — 고정비가 없습니다">정비</th>`, `<th>T/D</th>`);
    });
    if (showExt) head1.push(`<th rowspan="2">외부<br>보일러</th>`);
    head1.push(`<th rowspan="2">합계<br>(T/D)</th>`, `<th rowspan="2">부족<br>(T/D)</th>`);

    const body = result.days.map((x) => {
      const row = resolved.days[x.day] || {};
      const anyDown = MACHINES.some((m) => num(row[m.down]) > 0);
      const cells = [`<th class="mp-day${anyDown ? " mp-day-down" : ""}">${x.day}</th>`,
        `<td class="mp-kind">${x.isActual ? "실적" : x.noPlan ? "계획 없음" : "계획"}</td>`];
      MACHINES.forEach((m) => cells.push(hoursCell(x.day, m.down, row[m.down], "down", `${m.label} 운휴시간`)));
      cells.push(`<td class="computed mp-num mp-demand">${x.noPlan ? "" : fmt0(x.demand)}</td>`);
      BOILERS.forEach((b) => {
        cells.push(hoursCell(x.day, b.stop, row[b.stop], "maint", `${b.label} 정비 운휴시간`));
        const status = x.status[b.code];
        const value = x.alloc[b.code];
        let text = value > 0.05 ? fmt0(value) : "";
        let cls = "computed mp-num";
        let title = "";
        const recorded = x.idleRecorded[b.code] ? " (도급내역에 적힌 운휴시간)" : "";
        if (status === "maint") { text = "정비"; cls += " mp-cell-maint"; title = "보일러 정비 — 고정비 없음"; }
        else if (status === "idle") { text = "운휴"; cls += " mp-cell-idle"; title = `우리 사정 운휴 ${fmt0(x.idleHours[b.code])}시간 — 고정비 발생${recorded}`; }
        else if (status === "unused") { text = "미가동"; cls += " mp-cell-unused"; title = `배분하지 않는 보일러 — 운휴 ${fmt0(x.idleHours[b.code])}시간 고정비 발생${recorded}`; }
        else if (b.lng && value > 0.05) { cls += " mp-cell-lng"; title = "LNG 보일러 가동"; }
        cells.push(`<td class="${cls}" title="${esc(title)}">${text}</td>`);
      });
      if (showExt) cells.push(`<td class="computed mp-num">${x.ext > 0.05 ? fmt0(x.ext) : ""}</td>`);
      cells.push(`<td class="computed mp-num mp-strong">${x.noPlan ? "" : fmt0(x.total)}</td>`);
      cells.push(`<td class="computed mp-num${x.shortage > 0.5 ? " mp-cell-short" : ""}">${x.shortage > 0.5 ? fmt0(x.shortage) : ""}</td>`);
      return `<tr class="${x.isActual ? "mp-row-actual" : "mp-row-plan"}">${cells.join("")}</tr>`;
    });

    const dim = result.daysInMonth;
    const summary = (label, pick, digits) => {
      const cells = [`<th colspan="2">${label}</th>`];
      MACHINES.forEach((m) => {
        const hours = result.days.reduce((s, x) => s + (num((resolved.days[x.day] || {})[m.down]) || 0), 0);
        cells.push(`<td>${label === "합계" && hours > 0 ? fmt0(hours) : ""}</td>`);
      });
      cells.push(`<td>${fmtN(pick(result.days.reduce((s, x) => s + x.demand, 0)), digits)}</td>`);
      BOILERS.forEach((b) => {
        const stop = result.days.reduce((s, x) => s + (num((resolved.days[x.day] || {})[b.stop]) || 0), 0);
        const tons = result.days.reduce((s, x) => s + x.alloc[b.code], 0);
        cells.push(`<td>${label === "합계" && stop > 0 ? fmt0(stop) : ""}</td>`, `<td>${tons > 0.05 ? fmtN(pick(tons), digits) : ""}</td>`);
      });
      if (showExt) cells.push(`<td>${fmtN(pick(result.extTons), digits)}</td>`);
      cells.push(`<td>${fmtN(pick(result.totalSteam), digits)}</td>`);
      cells.push(`<td>${result.shortageSum > 0.5 ? fmtN(pick(result.shortageSum), digits) : ""}</td>`);
      return `<tr class="summary-row">${cells.join("")}</tr>`;
    };
    body.push(summary("합계", (v) => v, 0), summary("일 평균 (T/D)", (v) => v / dim, 0), summary("시간 평균 (T/H)", (v) => v / dim / 24, 1));

    $("mpDayTable").innerHTML = `<thead><tr>${head1.join("")}</tr><tr>${head2.join("")}</tr></thead><tbody>${body.join("")}</tbody>`;
  }

  function hoursCell(day, field, value, kind, label) {
    const n = num(value);
    const on = n !== null && n > 0;
    return `<td class="mp-hours-cell mp-hours-${kind}${on ? " is-on" : ""}"><input type="text" inputmode="numeric" class="mp-hours"`
      + ` data-kind="day" data-day="${day}" data-field="${field}" data-focus-key="${day}-${field}"`
      + ` value="${on ? plain(n) : ""}" aria-label="${day}일 ${esc(label)}" title="${day}일 ${esc(label)} — 누르면 24시간, 한 번 더 누르면 해제" /></td>`;
  }

  function renderCost() {
    const { result } = current;
    const rows = result.boilers.map((b) => {
      const muted = b.tons < 0.05 && !(b.cost > 0);
      return `<tr class="${muted ? "mp-row-off" : ""}">
        <th class="mp-left">${esc(b.label)}</th>
        <td class="computed mp-num">${b.tons > 0.05 ? fmt0(b.tons) : ""}</td>
        <td class="computed mp-num">${b.billTons > 0.05 ? fmt0(b.billTons) : ""}</td>
        <td class="computed mp-num">${money(b.steamCost)}</td>
        <td class="computed mp-num${b.idleHours > 0 ? " mp-cell-idle" : ""}">${b.idleFee && b.idleHours > 0 ? fmt0(b.idleHours) : ""}</td>
        <td class="computed mp-num">${b.idleFee ? money(b.idleCost) : ""}</td>
        <td class="computed mp-num">${b.extraCost > 0 ? fmt0(b.extraCost) : ""}</td>
        <td class="computed mp-num mp-strong">${b.cost === null ? "단가 없음" : (b.cost > 0 ? fmtN(b.cost / 1e6, 1) : "")}</td>
        <td class="computed mp-num">${b.costPerTon ? fmt0(b.costPerTon) : ""}</td>
        <td class="computed mp-left mp-note">${esc(b.notes.join(" · "))}</td>
      </tr>`;
    });
    const sum = (pick) => result.boilers.reduce((s, b) => s + (pick(b) || 0), 0);
    rows.push(`<tr class="summary-row"><th>합계</th>
      <td>${fmt0(sum((b) => b.tons) + result.extTons)}</td><td></td><td>${fmt0(sum((b) => b.steamCost))}</td>
      <td>${blankZero(sum((b) => (b.idleFee ? b.idleHours : 0)))}</td><td>${blankZero(sum((b) => b.idleCost))}</td>
      <td>${blankZero(sum((b) => b.extraCost))}</td>
      <td>${fmtN(result.costTotal / 1e6, 1)}</td><td>${result.costPerTon ? fmt0(result.costPerTon) : ""}</td>
      <td class="mp-left">${result.costKnown ? "" : "단가가 없는 항목은 합계에서 빠졌습니다"}</td></tr>`);
    $("mpCostTable").innerHTML = `<thead><tr>
      <th>보일러</th><th>스팀량(톤)</th><th>정산량(톤)</th><th>스팀비(원)</th><th>우리 사정<br>운휴(시간)</th><th>운휴 고정비(원)</th>
      <th>설비개선비(원)</th><th>합계(백만원)</th><th>톤당(원/톤)</th><th>비고</th>
    </tr></thead><tbody>${rows.join("")}</tbody>`;
    function money(v) {
      if (v === null) return "단가 없음";
      return v > 0 ? fmt0(v) : "";
    }
    function blankZero(v) {
      return v > 0.5 ? fmt0(v) : "";
    }
  }

  // ───────────────────────── 그리기: 월별 ─────────────────────────
  function renderMonthly() {
    if (!current) return;
    const { year, yd, prev } = current;
    $("mpMonthlyTitle").textContent = `${year}년 월별 요약`;
    const totals = { paper: 0, tissue: 0, demand: 0, waste: 0, combo: 0, fluid: 0, lng: 0, ext: 0, total: 0, cWaste: 0, cCombo: 0, cFluid: 0, cLng: 0, cost: 0 };
    const rows = [];
    for (let month = 1; month <= 12; month += 1) {
      const { result } = compute(year, month, yd, prev);
      const actualDays = result.days.filter((x) => x.isActual).length;
      const hasPlan = result.demandTarget !== null;
      if (!hasPlan && !actualDays) {
        rows.push(`<tr class="mp-row-off mp-month-row" data-month="${month}"><th>${month}월</th><td class="mp-kind">—</td>${"<td></td>".repeat(17)}</tr>`);
        continue;
      }
      const kind = !hasPlan ? "실적" : actualDays === 0 ? "계획" : actualDays >= result.daysInMonth ? "실적" : `실적 ${result.actualUntilDay}일 + 계획`;
      const byCode = {};
      result.boilers.forEach((b) => { byCode[b.code] = b; });
      const machine = {};
      result.machines.forEach((m) => { machine[m.code] = m.productionTon || 0; });
      const paper = machine["PM-2"] + machine["PM-3"];
      const tissue = machine["TM-3"] + machine["TM-4"] + machine["TM-5"];
      const waste = byCode.WASTE1.tons + byCode.WASTE2.tons;
      const cWaste = (byCode.WASTE1.cost || 0) + (byCode.WASTE2.cost || 0);
      const cLng = (byCode.LNG20.cost || 0) + (byCode.LNG50.cost || 0);
      totals.paper += paper; totals.tissue += tissue; totals.demand += result.demandTarget || 0;
      totals.waste += waste; totals.combo += byCode.COMBO.tons; totals.fluid += byCode.FLUID.tons;
      totals.lng += result.lngTons; totals.ext += result.extTons; totals.total += result.totalSteam;
      totals.cWaste += cWaste; totals.cCombo += byCode.COMBO.cost || 0; totals.cFluid += byCode.FLUID.cost || 0;
      totals.cLng += cLng; totals.cost += result.costTotal;
      const share = result.totalSteam > 0 ? (result.lngTons / result.totalSteam) * 100 : 0;
      rows.push(`<tr class="mp-month-row${month === current.month ? " is-current" : ""}" data-month="${month}">
        <th>${month}월</th><td class="mp-kind">${esc(kind)}</td>
        <td class="computed mp-num">${paper ? fmt0(paper) : ""}</td><td class="computed mp-num">${tissue ? fmt0(tissue) : ""}</td>
        <td class="computed mp-num mp-strong">${hasPlan ? fmt0(result.demandTarget) : ""}</td>
        <td class="computed mp-num">${fmt0(waste)}</td><td class="computed mp-num">${fmt0(byCode.COMBO.tons)}</td>
        <td class="computed mp-num">${fmt0(byCode.FLUID.tons)}</td>
        <td class="computed mp-num${result.lngTons > 0.5 ? " mp-cell-lng" : ""}">${fmt0(result.lngTons)}</td>
        <td class="computed mp-num">${result.extTons > 0.5 ? fmt0(result.extTons) : ""}</td>
        <td class="computed mp-num mp-strong">${fmt0(result.totalSteam)}</td>
        <td class="computed mp-num">${!hasPlan ? "" : Math.abs(result.diff) < 0.5 ? "0" : (result.diff > 0 ? "+" : "−") + fmt0(Math.abs(result.diff))}</td>
        <td class="computed mp-num">${share.toFixed(1)}%</td>
        <td class="computed mp-num">${fmtN(cWaste / 1e6, 1)}</td><td class="computed mp-num">${fmtN((byCode.COMBO.cost || 0) / 1e6, 1)}</td>
        <td class="computed mp-num">${fmtN((byCode.FLUID.cost || 0) / 1e6, 1)}</td><td class="computed mp-num">${fmtN(cLng / 1e6, 1)}</td>
        <td class="computed mp-num mp-strong">${fmtN(result.costTotal / 1e6, 1)}</td>
        <td class="computed mp-num">${result.costPerTon ? fmt0(result.costPerTon) : ""}</td>
      </tr>`);
    }
    const share = totals.total > 0 ? (totals.lng / totals.total) * 100 : 0;
    rows.push(`<tr class="summary-row"><th>합계</th><td></td>
      <td>${fmt0(totals.paper)}</td><td>${fmt0(totals.tissue)}</td><td>${fmt0(totals.demand)}</td>
      <td>${fmt0(totals.waste)}</td><td>${fmt0(totals.combo)}</td><td>${fmt0(totals.fluid)}</td><td>${fmt0(totals.lng)}</td>
      <td>${totals.ext > 0.5 ? fmt0(totals.ext) : ""}</td><td>${fmt0(totals.total)}</td><td></td><td>${share.toFixed(1)}%</td>
      <td>${fmtN(totals.cWaste / 1e6, 1)}</td><td>${fmtN(totals.cCombo / 1e6, 1)}</td><td>${fmtN(totals.cFluid / 1e6, 1)}</td>
      <td>${fmtN(totals.cLng / 1e6, 1)}</td><td>${fmtN(totals.cost / 1e6, 1)}</td>
      <td>${totals.total > 0 ? fmt0(totals.cost / totals.total) : ""}</td></tr>`);
    $("mpMonthlyTable").innerHTML = `<thead>
      <tr><th rowspan="2">월</th><th rowspan="2">구분</th><th colspan="2">생산량(톤)</th><th rowspan="2">필요 스팀량<br>(톤)</th>
        <th colspan="6">보일러별 스팀량(톤)</th><th rowspan="2">필요 대비<br>(톤)</th><th rowspan="2">LNG<br>비중</th>
        <th colspan="5">예상 비용(백만원)</th><th rowspan="2">톤당<br>(원/톤)</th></tr>
      <tr><th>제지</th><th>화장지</th><th>폐합성</th><th>복합</th><th>유동상</th><th>LNG</th><th>외부</th><th>합계</th>
        <th>폐합성</th><th>복합</th><th>유동상</th><th>LNG</th><th>합계</th></tr>
    </thead><tbody>${rows.join("")}</tbody>`;
  }

  // ───────────────────────── 이벤트 ─────────────────────────
  function bindEvents() {
    document.querySelectorAll("[data-mp-tab]").forEach((button) => {
      button.addEventListener("click", () => setTab(button.dataset.mpTab));
    });
    $("filterSubmit").addEventListener("click", () => reload());
    yearSelect.addEventListener("change", () => reload());
    monthSelect.addEventListener("change", () => reload());

    // 값 입력칸: 생산계획·보일러 기준값·정산 기준
    $("mpDaily").addEventListener("change", (event) => {
      const el = event.target;
      if (!(el instanceof HTMLInputElement) && !(el instanceof HTMLSelectElement)) return;
      const kind = el.dataset.kind;
      const data = Object.assign({}, el.dataset);
      const value = el.value;
      const checked = el.checked;
      const id = el.id;
      setTimeout(() => {
        if (kind === "machine") {
          saveMachine(data.code, data.field, Api.num(value));
        } else if (kind === "boiler") {
          saveBoiler(data.code, data.field, Api.num(value));
        } else if (kind === "boiler-use") {
          saveBoiler(data.code, "useYn", checked ? "Y" : "N");
        } else if (kind === "setting") {
          saveMonthSetting(data.field, Api.num(value));
        } else if (kind === "day") {
          const hours = Api.num(value);
          saveDay(Number(data.day), data.field, hours === null || hours <= 0 ? null : Math.min(24, hours), null);
        } else if (id === "mpActualUntil") {
          saveMonthSetting("actualUntilDay", value === "" ? null : Number(value));
        } else if (id === "mpAdjust") {
          saveMonthSetting("adjustYn", checked ? "Y" : "N");
        }
      }, 0);
    });
    // 운휴 칸: 누르면 24시간 ↔ 해제. 눌러서 24 가 된 칸은 글자가 선택돼 있어 숫자를 치면 그 시간으로 바뀐다.
    $("mpDayTable").addEventListener("mousedown", (event) => {
      const cell = event.target instanceof Element ? event.target.closest(".mp-hours-cell") : null;
      if (!cell) return;
      const el = cell.querySelector(".mp-hours");
      if (!el) return;
      event.preventDefault();
      const day = Number(el.dataset.day);
      const field = el.dataset.field;
      const focusKey = el.dataset.focusKey;
      // 다른 칸에 치던 시간이 있으면 먼저 저장한다 (표를 다시 그리면 치던 값이 사라지므로).
      const active = document.activeElement;
      if (active instanceof HTMLInputElement && active.classList.contains("mp-hours") && active !== el) active.blur();
      const saved = num(((current.yd.days.get(iso(current.year, current.month, day)) || {})[field]));
      const on = saved !== null && saved > 0;
      saveDay(day, field, on ? null : 24, on ? null : focusKey);
    });
    $("mpDayTable").addEventListener("keydown", (event) => {
      const el = event.target;
      if (!(el instanceof HTMLInputElement) || !el.classList.contains("mp-hours")) return;
      if (event.key === "Enter") { event.preventDefault(); el.blur(); }
      if (event.key === " ") {
        event.preventDefault();
        const on = (Api.num(el.value) || 0) > 0;
        saveDay(Number(el.dataset.day), el.dataset.field, on ? null : 24, el.dataset.focusKey);
      }
    });
    $("mpDaily").addEventListener("keydown", (event) => {
      if (event.key === "Enter" && event.target instanceof HTMLInputElement && event.target.classList.contains("mp-input")) event.target.blur();
    });
    $("mpMonthlyTable").addEventListener("click", (event) => {
      const row = event.target instanceof Element ? event.target.closest(".mp-month-row") : null;
      if (!row) return;
      monthSelect.value = String(row.dataset.month).padStart(2, "0");
      activeTab = "daily";
      reload();
    });

    const uploadButton = $("mpPlanUpload");
    const fileInput = $("mpPlanFile");
    uploadButton.addEventListener("click", () => fileInput.click());
    fileInput.addEventListener("change", async () => {
      const file = fileInput.files && fileInput.files[0];
      if (!file) return;
      await uploadPlan(file);
      fileInput.value = "";
    });
  }

  async function uploadPlan(file) {
    const button = $("mpPlanUpload");
    const original = button.textContent;
    button.disabled = true;
    button.textContent = "올리는 중";
    try {
      const form = new FormData();
      form.append("file", file);
      form.append("year", yearSelect.value);
      const response = await fetch("/steam-energy-api/move-plan-excel/import-production-plan", { method: "POST", body: form });
      if (!response.ok) throw new Error((await window.ExcelUploadErrors?.message(response)) || "생산계획 엑셀을 올리지 못했습니다.");
      const json = await response.json();
      const data = json && json.data ? json.data : {};
      yearCache.clear();
      await reload();
      const months = (data.months || []).join(", ");
      showMessage(`'${data.sheetName || ""}' 시트에서 ${(data.months || []).length}개 달(${months})의 생산계획을 읽었습니다. 새로 ${data.inserted || 0}건, 바꿈 ${data.updated || 0}건.`, "ok");
    } catch (error) {
      console.error(error);
      showMessage(error instanceof Error ? error.message : "생산계획 엑셀을 올리지 못했습니다.", "error");
    } finally {
      button.disabled = false;
      button.textContent = original;
    }
  }

  function setTab(tab) {
    activeTab = tab;
    document.querySelectorAll("[data-mp-tab]").forEach((button) => {
      const on = button.dataset.mpTab === tab;
      button.classList.toggle("active", on);
      button.setAttribute("aria-pressed", on ? "true" : "false");
    });
    $("mpDaily").hidden = tab !== "daily";
    $("mpMonthly").hidden = tab !== "monthly";
    // 월별 요약은 한 해를 보므로 월 고르기를 숨긴다
    monthSelect.style.display = tab === "monthly" ? "none" : "";
    const dot = monthSelect.previousElementSibling;
    if (dot && dot.classList.contains("landing-filter-dot")) dot.style.display = tab === "monthly" ? "none" : "";
    if (tab === "monthly") renderMonthly();
  }

  function populatePeriod() {
    let saved = {};
    try { saved = JSON.parse(localStorage.getItem(PERIOD_KEY) || "{}") || {}; } catch (e) { saved = {}; }
    const now = new Date();
    const currentYear = now.getFullYear();
    const savedYear = saved.year || String(currentYear);
    const savedMonth = saved.month || String(now.getMonth() + 1).padStart(2, "0");
    for (let year = currentYear + 1; year >= currentYear - 5; year -= 1) {
      const option = document.createElement("option");
      option.value = String(year);
      option.textContent = String(year);
      option.selected = option.value === savedYear;
      yearSelect.appendChild(option);
    }
    for (let month = 1; month <= 12; month += 1) {
      const option = document.createElement("option");
      option.value = String(month).padStart(2, "0");
      option.textContent = `${month}월`;
      option.selected = option.value === savedMonth;
      monthSelect.appendChild(option);
    }
  }

  async function reload() {
    const year = Number(yearSelect.value);
    const month = Number(monthSelect.value);
    try { localStorage.setItem(PERIOD_KEY, JSON.stringify({ year: yearSelect.value, month: monthSelect.value })); } catch (e) { /* 저장 못 해도 화면은 쓴다 */ }
    try {
      const [yd, prev] = await Promise.all([loadYear(year), loadYear(year - 1)]);
      current = { year, month, yd, prev };
      const failed = yd.failed.concat(prev.failed);
      showMessage(failed.length ? `일부 자료를 불러오지 못했습니다 (${failed.length}건). 해당 값은 빈 값으로 계산했습니다.` : "", failed.length ? "error" : "");
      refresh();
      setTab(activeTab);
    } catch (error) {
      console.error(error);
      showMessage("자료를 불러오지 못했습니다: " + (error && error.message ? error.message : error), "error");
    }
  }

  populatePeriod();
  bindEvents();
  reload();
})();
