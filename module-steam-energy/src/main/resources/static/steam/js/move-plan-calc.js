/* ============================================================
   move-plan-calc.js — 운영 최적화 계산 (화면과 떨어진 순수 계산)

   생산계획(호기별 생산량) × 원단위 = 월 필요 스팀량
     → 날짜별 필요 스팀량 (호기별 가동시간 비례, 실적이 있는 날은 실적)
     → 보일러 배분 (폐합성 → 복합 → 유동상 → LNG 순서, 하루 최대·최소 운용량 안에서)
     → 예상 비용 (기존 화면의 정산 규칙)

   DOM·네트워크를 쓰지 않는다. 브라우저에서는 window.MovePlanCalc, Node 에서는 require 로 쓴다
   (확인: node module-steam-energy/src/test/js/move-plan-calc.test.js).
   ============================================================ */
(function (root, factory) {
  if (typeof module === "object" && module.exports) module.exports = factory();
  else root.MovePlanCalc = factory();
})(typeof self !== "undefined" ? self : this, function () {
  "use strict";

  /** 생산설비 — down: 일자별 운휴시간 필드, unit: 전년 종합 원단위 필드(energy-ratio-steam-units) */
  const MACHINES = [
    { code: "PM-2", label: "제지2", down: "downPm2", unit: "unitPm2" },
    { code: "PM-3", label: "제지3", down: "downPm3", unit: "unitPm3" },
    { code: "TM-3", label: "화장지3", down: "downTm3", unit: "unitTm3" },
    { code: "TM-4", label: "화장지4", down: "downTm4", unit: "unitTm4" },
    { code: "TM-5", label: "화장지5", down: "downTm5", unit: "unitTm5" },
  ];

  /**
   * 보일러 — 배열 순서가 배분 순서다 (앞에서부터 채운다).
   * stop: 일자별 보일러 정비 운휴시간 필드, idle: 일자별 우리 사정 운휴시간 필드(수동), actual: 스팀생산실적(combo-daily-productions) 필드,
   * idleFee: 우리 사정으로 세우면 운휴시간당 고정비가 나가는 보일러
   */
  const BOILERS = [
    { code: "WASTE1", label: "폐합성 1호기", short: "폐합성1", stop: "stopWaste1", idle: "idleWaste1", actual: "wasteSteam1", idleFee: true },
    { code: "WASTE2", label: "폐합성 2호기", short: "폐합성2", stop: "stopWaste2", idle: "idleWaste2", actual: "wasteSteam2", idleFee: true },
    { code: "COMBO", label: "복합보일러", short: "복합", stop: "stopCombo", idle: "idleCombo", actual: "comboSteam", idleFee: false },
    { code: "FLUID", label: "유동상", short: "유동상", stop: "stopFluid", idle: "idleFluid", actual: "fluidizedSteam", idleFee: true },
    { code: "LNG20", label: "LNG 20톤(#2)", short: "LNG 20톤", stop: "stopLng20", idle: "idleLng20", actual: "kn20", idleFee: false, lng: true },
    { code: "LNG50", label: "LNG 50톤(#3)", short: "LNG 50톤", stop: "stopLng50", idle: "idleLng50", actual: "kn50", idleFee: false, lng: true },
  ];

  const EPS = 1e-6;

  function daysInMonth(year, month) {
    return new Date(year, month, 0).getDate();
  }

  /** 숫자면 그 값, 아니면 null (빈 글자·null·NaN → null) */
  function num(value) {
    if (value === null || value === undefined || value === "") return null;
    const n = typeof value === "number" ? value : Number(String(value).replace(/,/g, ""));
    return Number.isFinite(n) ? n : null;
  }

  function clampHours(value) {
    const n = num(value);
    if (n === null) return 0;
    return Math.min(24, Math.max(0, n));
  }

  /** 0 보다 큰 값들 가운데 큰 쪽 10% 의 평균 — 하루 최대 공급량 기본값 (한두 번 튄 최대값에 끌려가지 않게) */
  function topDecileAverage(values) {
    const v = (values || []).map(num).filter((n) => n !== null && n > 0).sort((a, b) => a - b);
    if (!v.length) return null;
    const top = v.slice(Math.floor(v.length * 0.9));
    return top.reduce((a, b) => a + b, 0) / top.length;
  }

  /**
   * 한 달 계산.
   *
   * input = {
   *   year, month,
   *   machines: { "PM-2": { productionTon, runtimeHours, unit }, … },       // unit = 적용 원단위
   *   boilers:  { WASTE1: { use, maxTonDay, minTonDay, steamUnitPrice, idleHourPrice }, … },
   *   settings: { actualUntilDay, adjust, waste2BillCapTon, comboGuaranteeTon, comboBscPrice, comboAscPrice,
   *               fluidBillTonHour, fluidImprovementCost, lngM3PerTon, lngUnitPrice },
   *   days:     { 1: { downPm2, …, stopWaste1, …, idleWaste1, … }, … },     // 시간(0~24). idle* = 우리 사정 운휴(수동)
   *   actuals:  { 1: { WASTE1, WASTE2, COMBO, FLUID, LNG20, LNG50, EXT, fluidRunHours,          // 톤, 없으면 null
   *                    idleHours: { WASTE1, WASTE2, FLUID } }, … }                              // 도급내역에 적힌 운휴시간
   * }
   */
  function computeMonth(input) {
    const year = input.year;
    const month = input.month;
    const dim = daysInMonth(year, month);
    const settings = input.settings || {};
    const dayInput = input.days || {};
    const actualInput = input.actuals || {};

    // ── 1. 호기별 월 필요 스팀량과 시간당 스팀량 ──
    const machines = MACHINES.map((m) => {
      const src = (input.machines || {})[m.code] || {};
      const productionTon = num(src.productionTon);
      const unit = num(src.unit);
      const steamTon = productionTon !== null && unit !== null ? productionTon * unit : null;
      let runHours = 0;
      for (let d = 1; d <= dim; d += 1) runHours += 24 - clampHours((dayInput[d] || {})[m.down]);
      return {
        code: m.code, label: m.label, productionTon, unit, steamTon,
        planHours: num(src.runtimeHours), runHours,
        ratePerHour: steamTon !== null && runHours > 0 ? steamTon / runHours : 0,
        unplaced: steamTon !== null && steamTon > 0 && runHours <= 0,   // 한 달 내내 운휴로 골라 넣을 날이 없다
      };
    });
    const demandTarget = machines.some((m) => m.steamTon !== null)
      ? machines.reduce((sum, m) => sum + (m.steamTon || 0), 0) : null;

    // ── 2. 실적이 있는 날 ──
    const actualTotals = {};
    let lastActualDay = 0;
    for (let d = 1; d <= dim; d += 1) {
      const a = actualInput[d];
      if (!a) continue;
      let total = 0;
      let has = false;
      for (const key of ["WASTE1", "WASTE2", "COMBO", "FLUID", "LNG20", "LNG50", "EXT"]) {
        const v = num(a[key]);
        if (v === null) continue;
        has = true;
        total += v;
      }
      if (!has) continue;
      actualTotals[d] = total;
      if (total > 0) lastActualDay = d;
    }
    const cutoffSetting = num(settings.actualUntilDay);
    const actualUntilDay = cutoffSetting === null ? lastActualDay : Math.min(dim, Math.max(0, Math.round(cutoffSetting)));
    const isActualDay = (d) => d <= actualUntilDay && actualTotals[d] !== undefined;

    // ── 3. 날짜별 필요 스팀량 (가동시간 비례) ──
    const modelDemand = {};
    const machineDemand = {};
    for (let d = 1; d <= dim; d += 1) {
      const row = dayInput[d] || {};
      machineDemand[d] = {};
      let sum = 0;
      for (const m of machines) {
        const down = MACHINES.find((x) => x.code === m.code).down;
        const v = m.ratePerHour * (24 - clampHours(row[down]));
        machineDemand[d][m.code] = v;
        sum += v;
      }
      modelDemand[d] = sum;
    }
    let actualSum = 0;
    let planModelSum = 0;
    for (let d = 1; d <= dim; d += 1) {
      if (isActualDay(d)) actualSum += actualTotals[d];
      else planModelSum += modelDemand[d];
    }
    // 남은 날 보정: 월 필요 스팀량에서 실적을 뺀 나머지를 남은 날에 같은 비율로 나눈다.
    const adjust = settings.adjust !== false;
    let adjustFactor = 1;
    if (adjust && demandTarget !== null && actualSum > 0 && planModelSum > EPS) {
      adjustFactor = Math.max(0, (demandTarget - actualSum) / planModelSum);
    }

    // ── 4. 보일러 기준값 ──
    const boilers = BOILERS.map((b) => {
      const src = (input.boilers || {})[b.code] || {};
      const maxTonDay = Math.max(0, num(src.maxTonDay) || 0);
      return {
        ...b,
        use: src.use !== false && maxTonDay > 0,
        maxTonDay,
        minTonDay: Math.min(maxTonDay, Math.max(0, num(src.minTonDay) || 0)),
        steamUnitPrice: num(src.steamUnitPrice),
        idleHourPrice: num(src.idleHourPrice),
      };
    });
    const waste2Cap = num(settings.waste2BillCapTon);
    const fluidBillTonHour = num(settings.fluidBillTonHour);

    // ── 5. 날짜별 배분 ──
    const days = [];
    for (let d = 1; d <= dim; d += 1) {
      const row = dayInput[d] || {};
      const actual = isActualDay(d);
      const alloc = {};
      const status = {};
      const idleHours = {};
      const availHours = {};   // 정비를 뺀 시간
      const manualIdle = {};   // 일별 표에서 고른 우리 사정 운휴시간
      const runHours = {};     // 정비·우리 사정 운휴를 뺀, 배분에 쓰는 시간
      let demand;
      let shortage = 0;
      let ext = 0;

      for (const b of boilers) {
        availHours[b.code] = 24 - clampHours(row[b.stop]);
        manualIdle[b.code] = Math.min(clampHours(row[b.idle]), availHours[b.code]);
        runHours[b.code] = availHours[b.code] - manualIdle[b.code];
      }

      if (actual) {
        const a = actualInput[d] || {};
        for (const b of boilers) alloc[b.code] = Math.max(0, num(a[b.code]) || 0);
        ext = Math.max(0, num(a.EXT) || 0);
        demand = actualTotals[d];
      } else {
        demand = modelDemand[d] * adjustFactor;
        const on = [];
        for (const b of boilers) {
          alloc[b.code] = 0;
          if (!b.use || runHours[b.code] <= 0) continue;
          const frac = runHours[b.code] / 24;
          const cap = b.maxTonDay * frac;
          on.push({ code: b.code, cap, min: Math.min(b.minTonDay * frac, cap) });
        }
        // 최소 운용량을 다 채울 수 없으면 뒤 순서 보일러부터 세운다 (우리 사정 운휴).
        let minSum = on.reduce((s, x) => s + x.min, 0);
        while (minSum > demand + EPS) {
          let dropIndex = -1;
          for (let i = on.length - 1; i >= 0; i -= 1) {
            if (on[i].min > EPS) { dropIndex = i; break; }
          }
          if (dropIndex < 0) break;
          minSum -= on[dropIndex].min;
          on.splice(dropIndex, 1);
        }
        let rest = demand;
        for (const x of on) {
          const v = Math.min(x.min, Math.max(0, rest));
          alloc[x.code] = v;
          rest -= v;
        }
        for (const x of on) {
          if (rest <= EPS) break;
          const add = Math.min(x.cap - alloc[x.code], rest);
          alloc[x.code] += add;
          rest -= add;
        }
        shortage = rest > EPS ? rest : 0;
      }

      // 유동상 정산 가동시간: 실적일에 가동시간 실적이 있으면 그 값, 아니면 돌린 날의 가용시간
      const actualFluidHours = actual ? num((actualInput[d] || {}).fluidRunHours) : null;
      const fluidRunHours = actualFluidHours !== null
        ? Math.min(24, Math.max(0, actualFluidHours)) : (alloc.FLUID > EPS ? runHours.FLUID : 0);

      const recordedIdle = actual ? ((actualInput[d] || {}).idleHours || {}) : {};
      const idleRecorded = {};
      for (const b of boilers) {
        // 스팀을 보내지 않았어도 보일러를 돌린 시간(유동상 가동시간 실적)은 운휴가 아니다.
        const ranHours = b.code === "FLUID" ? fluidRunHours : 0;
        // 고른 우리 사정 운휴시간 + 스팀을 받지 않은 나머지 시간
        const auto = b.idleFee && alloc[b.code] <= EPS ? Math.max(0, runHours[b.code] - ranHours) : 0;
        let idle = b.idleFee ? manualIdle[b.code] + auto : 0;
        // 실적일: 도급내역에 운휴시간이 적혀 있으면 그 값이 실제 정산이다. 이 화면에서 정비로 고른 시간은 그래도 뺀다.
        const recorded = b.idleFee ? num(recordedIdle[b.code]) : null;
        idleRecorded[b.code] = recorded !== null;
        if (recorded !== null) idle = Math.min(Math.max(0, recorded), availHours[b.code]);
        if (alloc[b.code] > EPS) status[b.code] = "run";
        else if (availHours[b.code] <= 0) status[b.code] = "maint";        // 보일러 정비 — 고정비 없음
        else if (manualIdle[b.code] >= availHours[b.code] - EPS) status[b.code] = "idle";   // 일별 표에서 고른 우리 사정 운휴
        else if (idle > EPS) status[b.code] = b.use ? "idle" : "unused";   // 우리 사정 운휴 · 미가동 — 고정비 발생
        else status[b.code] = "off";
        idleHours[b.code] = idle;
      }
      // 생산계획이 없는 달의 남은 날은 계획을 세우지 않은 날이다 — 배분도 운휴 고정비도 계산하지 않는다.
      const noPlan = !actual && demandTarget === null;
      if (noPlan) {
        for (const b of boilers) {
          idleHours[b.code] = 0;
          status[b.code] = availHours[b.code] <= 0 ? "maint" : manualIdle[b.code] > EPS ? "idle" : "off";
        }
      }

      const total = boilers.reduce((s, b) => s + alloc[b.code], 0) + ext;
      const scaled = {};
      for (const m of machines) scaled[m.code] = machineDemand[d][m.code] * (actual ? 1 : adjustFactor);
      days.push({
        day: d, isActual: actual, noPlan, demandModel: modelDemand[d], demand, machineDemand: scaled,
        alloc, ext, total, shortage, status, idleHours, idleRecorded, availHours, manualIdle, runHours, fluidRunHours,
        lng: alloc.LNG20 + alloc.LNG50,
        waste2BillTon: waste2Cap !== null ? Math.min(alloc.WASTE2, waste2Cap) : alloc.WASTE2,
      });
    }

    // ── 6. 월 합계와 비용 ──
    const sumOf = (pick) => days.reduce((s, x) => s + pick(x), 0);
    const totalSteam = sumOf((x) => x.total);
    const planSum = sumOf((x) => (x.isActual ? 0 : x.total));
    const shortageSum = sumOf((x) => x.shortage);
    const lngM3PerTon = num(settings.lngM3PerTon);
    const lngUnitPrice = num(settings.lngUnitPrice);

    const comboTons = sumOf((x) => x.alloc.COMBO);
    const guaranteeFull = num(settings.comboGuaranteeTon);
    const bscPrice = num(settings.comboBscPrice);
    const ascPrice = num(settings.comboAscPrice);
    const hasGuarantee = guaranteeFull !== null && guaranteeFull > 0;
    // 복합보일러를 정비로 세운 시간은 보일러 쪽 사정이라 그만큼 보증량을 줄인다 (우리 사정이 아니면 기본 금액이 없다).
    const comboMaintHours = sumOf((x) => 24 - x.availHours.COMBO);
    const guarantee = hasGuarantee ? guaranteeFull * (1 - comboMaintHours / (dim * 24)) : null;
    const combo = {
      tons: comboTons,
      guaranteeFull: hasGuarantee ? guaranteeFull : null,
      maintHours: comboMaintHours,
      guarantee: hasGuarantee ? guarantee : null,
      shortfall: hasGuarantee ? Math.max(0, guarantee - comboTons) : 0,   // 보증량 미달 — 쓰지 않아도 내는 양
      excess: hasGuarantee ? Math.max(0, comboTons - guarantee) : 0,
      bscTons: hasGuarantee ? guarantee : comboTons,
      bscPrice, ascPrice,
      bscCost: bscPrice === null ? null : bscPrice * (hasGuarantee ? guarantee : comboTons),
      ascCost: !hasGuarantee ? 0 : (ascPrice === null ? null : ascPrice * Math.max(0, comboTons - guarantee)),
    };

    const boilerTotals = boilers.map((b) => {
      const tons = sumOf((x) => x.alloc[b.code]);
      const idle = sumOf((x) => x.idleHours[b.code]);
      const maintHours = sumOf((x) => 24 - x.availHours[b.code]);
      const runDays = days.filter((x) => x.alloc[b.code] > EPS).length;
      let billTons = tons;
      let steamCost = null;
      let extraCost = 0;
      const notes = [];
      if (b.code === "WASTE2") {
        billTons = sumOf((x) => x.waste2BillTon);
        if (waste2Cap !== null && billTons < tons - EPS) notes.push(`하루 ${fmt(waste2Cap)}톤까지만 정산`);
      }
      if (b.code === "FLUID" && fluidBillTonHour !== null) {
        billTons = sumOf((x) => x.fluidRunHours) * fluidBillTonHour;
        notes.push(`가동 ${fmt(sumOf((x) => x.fluidRunHours))}시간 × ${fluidBillTonHour.toFixed(1)}톤/시간으로 정산`);
      }
      if (b.code === "COMBO") {
        steamCost = combo.bscCost === null || combo.ascCost === null ? null : combo.bscCost + combo.ascCost;
        billTons = combo.bscTons + combo.excess;
        if (combo.maintHours > EPS && hasGuarantee) notes.push(`보일러 정비 ${fmt(combo.maintHours)}시간만큼 보증량을 줄임 (${fmt(combo.guaranteeFull)} → ${fmt(combo.guarantee)}톤)`);
        if (combo.shortfall > EPS) notes.push(`보증량 ${fmt(combo.guarantee)}톤에 ${fmt(combo.shortfall)}톤 미달 — 보증량만큼 정산`);
        else if (combo.excess > EPS) notes.push(`보증량 초과 ${fmt(combo.excess)}톤은 ASC 단가`);
      } else if (b.lng) {
        const m3 = lngM3PerTon === null ? null : tons * lngM3PerTon;
        steamCost = m3 === null || lngUnitPrice === null ? null : m3 * lngUnitPrice;
        if (tons > EPS && m3 !== null) notes.push(`LNG ${fmt(m3)} N㎥`);
      } else {
        steamCost = b.steamUnitPrice === null ? (billTons > EPS ? null : 0) : billTons * b.steamUnitPrice;
      }
      if (b.code === "FLUID") extraCost = Math.max(0, num(settings.fluidImprovementCost) || 0);
      const idleCost = !b.idleFee ? 0 : (b.idleHourPrice === null ? (idle > EPS ? null : 0) : idle * b.idleHourPrice);
      const cost = steamCost === null || idleCost === null ? null : steamCost + idleCost + extraCost;
      return {
        code: b.code, label: b.label, short: b.short, use: b.use, idleFee: b.idleFee,
        maxTonDay: b.maxTonDay, minTonDay: b.minTonDay,
        tons, billTons, runDays, idleHours: idle, maintHours,
        steamCost, idleCost, extraCost, cost,
        costPerTon: cost !== null && tons > EPS ? cost / tons : null,
        notes,
      };
    });
    const extTons = sumOf((x) => x.ext);
    const costKnown = boilerTotals.every((b) => b.cost !== null);
    const costTotal = boilerTotals.reduce((s, b) => s + (b.cost || 0), 0);

    return {
      year, month, daysInMonth: dim,
      machines, demandTarget,
      actualUntilDay, lastActualDay, actualSum, planSum, adjust, adjustFactor,
      totalSteam, diff: demandTarget === null ? null : totalSteam - demandTarget,
      shortageSum, lngTons: sumOf((x) => x.lng), extTons,
      days, boilers: boilerTotals, combo,
      costTotal, costKnown, costPerTon: totalSteam > EPS ? costTotal / totalSteam : null,
    };
  }

  function fmt(value) {
    return Math.round(value).toLocaleString("ko-KR");
  }

  return { MACHINES, BOILERS, daysInMonth, num, topDecileAverage, computeMonth };
});
