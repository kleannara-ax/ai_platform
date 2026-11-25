/* 이동계획 수립 계산(move-plan-calc.js) 확인.
 *
 * 실행:  node module-steam-energy/src/test/js/move-plan-calc.test.js
 * (gradle 테스트에는 묶여 있지 않다 — 계산 규칙을 고쳤을 때 손으로 돌린다. Node 18 이상.)
 *
 * 기준 자료: "복합보일러 스팀구매현황_11월 계획_v3_20261109.xlsx" 의 스팀생산실적(11월) 시트.
 *   1~9일 실적 + 10~30일 사람이 세운 계획, 시트 아래 계산 블록(스팀사용량·소각로 운영비).
 */
"use strict";
const assert = require("node:assert/strict");
const path = require("node:path");
const calc = require(path.join(__dirname, "../../main/resources/static/steam/js/move-plan-calc.js"));

let passed = 0;
function test(name, fn) {
  try {
    fn();
    passed += 1;
    console.log("  ok  " + name);
  } catch (error) {
    console.error("FAIL  " + name);
    console.error(error);
    process.exitCode = 1;
  }
}
const near = (actual, expected, tolerance, label) =>
  assert.ok(Math.abs(actual - expected) <= tolerance, `${label || ""} ${actual} ≉ ${expected} (±${tolerance})`);

const BOILERS = {
  WASTE1: { use: false, maxTonDay: 0, idleHourPrice: 50700 },
  WASTE2: { maxTonDay: 384, minTonDay: 336, steamUnitPrice: 11700, idleHourPrice: 96000 },
  COMBO: { maxTonDay: 1704, minTonDay: 0 },
  FLUID: { maxTonDay: 432, minTonDay: 300, steamUnitPrice: 18760, idleHourPrice: 160000 },
  LNG20: { maxTonDay: 480 },
  LNG50: { maxTonDay: 1200 },
};
const SETTINGS = {
  waste2BillCapTon: 336, comboGuaranteeTon: 41200, comboBscPrice: 19910, comboAscPrice: 12650,
  fluidBillTonHour: 21.1, fluidImprovementCost: 30000000, lngM3PerTon: 66, lngUnitPrice: 638.13,
};
const NOV_MACHINES = {
  "PM-2": { productionTon: 4349, runtimeHours: 333, unit: 2.21 },
  "PM-3": { productionTon: 26990, runtimeHours: 675, unit: 1.9307 },
  "TM-3": { productionTon: 656, runtimeHours: 305, unit: 2.3076 },
  "TM-4": { productionTon: 914, runtimeHours: 401, unit: 2.5072 },
  "TM-5": { productionTon: 2401, runtimeHours: 700, unit: 1.691 },
};

/** 하루 필요량이 정확히 demand 가 되는 한 달 (2026년 6월, 30일) — 제지3 한 호기만 */
function flatMonth(demandPerDay, overrides) {
  return calc.computeMonth(Object.assign({
    year: 2026, month: 6,
    machines: { "PM-3": { productionTon: demandPerDay * 30, unit: 1 } },
    boilers: BOILERS, settings: SETTINGS, days: {}, actuals: {},
  }, overrides || {}));
}

test("11월 계획서와 같은 조건 — 월 합계와 비용", () => {
  const pm2Down = [1, 5, 6, 7, 8, 13, 14, 15, 20, 21, 22, 26, 27, 28, 29];
  const actualRows = {   // [복합, 폐합성2, LNG20, LNG50, 유동상]
    1: [1544, 352, 0, 10, 0], 2: [1769, 371, 140, 0, 0], 3: [1769, 375, 180, 0, 0], 4: [1749, 371, 60, 310, 0],
    5: [1287, 376, 0, 2, 0], 6: [1563, 387, 0, 0, 0], 7: [1699, 391, 12, 0, 0], 8: [1705, 385, 3, 11, 0], 9: [1725, 400, 0, 360, 0],
  };
  const days = {};
  const actuals = {};
  for (let d = 1; d <= 30; d += 1) {
    days[d] = { downPm2: pm2Down.includes(d) ? 24 : 0, stopFluid: d <= 9 ? 24 : 0 };
    if (actualRows[d]) {
      const [combo, w2, k20, k50, fluid] = actualRows[d];
      actuals[d] = { COMBO: combo, WASTE2: w2, LNG20: k20, LNG50: k50, FLUID: fluid, WASTE1: 0 };
    }
  }
  const r = calc.computeMonth({
    year: 2026, month: 11, machines: NOV_MACHINES, boilers: BOILERS,
    settings: Object.assign({ actualUntilDay: 9, adjust: true }, SETTINGS), days, actuals,
  });
  const by = Object.fromEntries(r.boilers.map((b) => [b.code, b]));

  near(r.demandTarget, 69585.17, 2, "월 필요 스팀량");           // 시트 M49 (시트 원단위는 소수 자리가 더 길다)
  near(r.actualSum, 19306, 0.5, "1~9일 실적");
  near(r.totalSteam, r.demandTarget, 0.5, "실적 + 계획 = 월 필요량 (남은 날 보정)");
  assert.equal(r.actualUntilDay, 9);
  // 평소 날: 폐합성 384, 복합 1,704, 유동상 432 를 다 채우고 남는 양만 LNG (사람 계획 190)
  const normal = r.days[15];   // 16일
  assert.equal(Math.round(normal.alloc.WASTE2), 384);
  assert.equal(Math.round(normal.alloc.COMBO), 1704);
  assert.equal(Math.round(normal.alloc.FLUID), 432);
  near(normal.lng, 190, 10, "평소 날 LNG");
  // 제지2 운휴 날: LNG 없이, 유동상은 최소 운용량, 나머지는 복합
  const down = r.days[13];     // 14일
  assert.equal(down.lng, 0);
  assert.equal(Math.round(down.alloc.FLUID), 300);
  near(down.alloc.COMBO, 1392, 60, "제지2 운휴 날 복합");
  // 월 합계가 사람 계획과 1% 안쪽
  near(by.COMBO.tons, 46978, 470, "복합");
  near(by.WASTE2.tons, 11472, 1, "폐합성 2호기");
  near(by.FLUID.tons, 7804, 100, "유동상");
  near(r.lngTons, 3220, 100, "LNG");
  // 비용: 시트 아래 "3. 소각로 운영비" 블록과 같다
  assert.equal(Math.round(by.WASTE1.cost), 36504000);            // 720 h × 50,700
  assert.equal(Math.round(by.WASTE2.billTons), 10080);           // 14 t/h × 24 × 30 (하루 336톤 상한)
  assert.equal(Math.round(by.WASTE2.cost), 117936000);
  assert.equal(Math.round(by.FLUID.billTons * 10) / 10, 10634.4);   // 21.1 × 24 × 21일
  assert.equal(Math.round(by.FLUID.cost), 229501344);            // 199,501,344 + 설비개선비 30,000,000
  assert.equal(by.FLUID.idleHours, 0);                           // 1~9일은 보일러 정비 — 고정비 없음
});

test("순서대로 채운다 — 폐합성 → 복합 → 유동상 → LNG", () => {
  const r = flatMonth(1000);
  const d = r.days[0];
  // 유동상은 최소 운용량(300), 폐합성은 최대(384), 나머지는 복합
  assert.deepEqual([d.alloc.WASTE2, d.alloc.COMBO, d.alloc.FLUID, d.lng].map(Math.round), [384, 316, 300, 0]);
  const full = flatMonth(2800).days[0];
  assert.deepEqual([full.alloc.WASTE2, full.alloc.COMBO, full.alloc.FLUID, full.alloc.LNG20, full.alloc.LNG50].map(Math.round), [384, 1704, 432, 280, 0]);
  const over = flatMonth(3200).days[0];
  assert.deepEqual([over.alloc.LNG20, over.alloc.LNG50].map(Math.round), [480, 200]);   // 20톤을 다 채운 뒤 50톤
});

test("최소 운용량을 못 채우면 뒤 순서 보일러를 세우고 고정비가 나간다", () => {
  const r = flatMonth(500);   // 336 + 300 > 500 → 유동상을 세운다
  const d = r.days[0];
  assert.equal(d.alloc.FLUID, 0);
  assert.equal(d.status.FLUID, "idle");
  assert.equal(d.idleHours.FLUID, 24);
  assert.deepEqual([d.alloc.WASTE2, d.alloc.COMBO].map(Math.round), [384, 116]);
  const fluid = r.boilers.find((b) => b.code === "FLUID");
  assert.equal(fluid.idleHours, 24 * 30);
  assert.equal(fluid.idleCost, 24 * 30 * 160000);
});

test("보일러 정비로 세운 날은 고정비가 없고, 모자란 양은 다음 보일러가 받는다", () => {
  const days = { 1: { stopFluid: 24 }, 2: { stopWaste2: 24 }, 3: { stopCombo: 12 } };
  const r = flatMonth(2500, { days });
  const [d1, d2, d3, d4] = r.days;
  assert.equal(d1.status.FLUID, "maint");
  assert.equal(d1.idleHours.FLUID, 0);
  assert.equal(Math.round(d1.lng), 2500 - 384 - 1704);            // 유동상 몫이 LNG 로
  assert.equal(d2.status.WASTE2, "maint");
  assert.equal(d2.idleHours.WASTE2, 0);
  assert.equal(Math.round(d3.alloc.COMBO), 852);                  // 12시간만 가동 → 하루 최대의 절반
  assert.equal(Math.round(d4.lng), 0);                            // 384 + 1704 + 412 = 2500
  assert.equal(r.boilers.find((b) => b.code === "FLUID").idleHours, 0);
  assert.equal(r.boilers.find((b) => b.code === "WASTE2").idleHours, 0);
});

test("폐합성 1호기 — 돌리지 않아도 운휴 고정비가 매일 나가고, 정비로 고른 날만 빠진다", () => {
  const r = flatMonth(2000, { days: { 10: { stopWaste1: 24 } } });
  const waste1 = r.boilers.find((b) => b.code === "WASTE1");
  assert.equal(waste1.tons, 0);
  assert.equal(waste1.idleHours, 24 * 29);
  assert.equal(waste1.cost, 24 * 29 * 50700);
});

test("복합 보증량에 못 미치면 보증량만큼 BSC 를 내고, 넘으면 넘는 양은 ASC", () => {
  const low = flatMonth(1000);    // 복합 316 × 30 = 9,480톤 < 41,200
  assert.ok(low.combo.shortfall > 31000);
  assert.equal(low.combo.bscCost, 41200 * 19910);
  assert.equal(low.combo.ascCost, 0);
  const high = flatMonth(2600);   // 복합 1,704 × 30 = 51,120톤
  near(high.combo.excess, 51120 - 41200, 1, "초과량");
  near(high.combo.ascCost, (51120 - 41200) * 12650, 20000, "ASC");
  // 보증량을 비우면 쓴 만큼만 BSC
  const none = flatMonth(1000, { settings: Object.assign({}, SETTINGS, { comboGuaranteeTon: null }) });
  near(none.combo.bscCost, 316 * 30 * 19910, 20000, "보증량 없음");
});

test("복합보일러를 정비로 세운 시간만큼 보증량을 줄인다 (보일러 사정이면 기본 금액이 없다)", () => {
  const days = {};
  for (let d = 1; d <= 15; d += 1) days[d] = { stopCombo: 24 };   // 한 달의 절반
  const r = flatMonth(1000, { days });
  assert.equal(r.combo.maintHours, 15 * 24);
  near(r.combo.guarantee, 41200 / 2, 1e-6, "줄인 보증량");
  near(r.combo.bscCost, (41200 / 2) * 19910, 1, "BSC");
});

test("실적일의 운휴시간 — 도급내역에 적힌 값이 있으면 그 값으로 고정비를 계산한다", () => {
  const actuals = {
    1: { COMBO: 1700, WASTE2: 0, FLUID: 0, idleHours: { WASTE2: 0, FLUID: 5 } },     // 기록: 2호기 고정비 없음, 유동상 5시간
    2: { COMBO: 1700, WASTE2: 0, FLUID: 0, idleHours: { WASTE2: null, FLUID: null } }, // 기록 없음 → 0 이면 하루 종일 운휴
    3: { COMBO: 1700, WASTE2: 300, FLUID: 400, idleHours: { WASTE2: 3 } },            // 돌렸지만 3시간은 운휴로 정산
  };
  const r = flatMonth(2000, { actuals, days: { 1: { stopFluid: 22 } }, settings: Object.assign({ actualUntilDay: 3 }, SETTINGS) });
  assert.equal(r.days[0].idleHours.WASTE2, 0);
  assert.equal(r.days[0].idleHours.FLUID, 2);          // 기록 5시간이어도 정비로 고른 22시간을 빼면 2시간까지
  assert.equal(r.days[1].idleHours.WASTE2, 24);
  assert.equal(r.days[1].idleHours.FLUID, 24);
  assert.equal(r.days[2].idleHours.WASTE2, 3);
  assert.equal(r.days[0].status.WASTE2, "off");
});

test("모든 보일러를 최대로 돌려도 모자라면 부족량으로 남긴다", () => {
  const r = flatMonth(4500);   // 384 + 1704 + 432 + 480 + 1200 = 4200
  assert.equal(Math.round(r.days[0].shortage), 300);
  assert.equal(Math.round(r.shortageSum), 300 * 30);
});

test("실적 반영 — 실적이 있는 날은 실적, 남은 날은 월 필요량에 맞춰 보정", () => {
  const actuals = {};
  for (let d = 1; d <= 10; d += 1) actuals[d] = { COMBO: 1500, WASTE2: 380, FLUID: 400, LNG20: 0, LNG50: 0, WASTE1: 0 };
  const r = flatMonth(2000, { actuals });   // 목표 60,000 / 실적 22,800 → 남은 20일에 37,200
  assert.equal(r.actualUntilDay, 10);
  assert.equal(r.actualSum, 22800);
  near(r.adjustFactor, 37200 / 40000, 1e-9, "보정 계수");
  near(r.totalSteam, 60000, 1e-6, "합계");
  assert.equal(r.days[0].isActual, true);
  assert.equal(r.days[0].alloc.COMBO, 1500);
  // 보정을 끄면 남은 날은 가동시간 비례 값 그대로
  const off = flatMonth(2000, { actuals, settings: Object.assign({ adjust: false }, SETTINGS) });
  assert.equal(off.adjustFactor, 1);
  near(off.days[20].demand, 2000, 1e-6, "보정 없음");
  // 실적을 쓰지 않도록 고르면 전부 계획
  const ignore = flatMonth(2000, { actuals, settings: Object.assign({ actualUntilDay: 0 }, SETTINGS) });
  assert.equal(ignore.days.filter((x) => x.isActual).length, 0);
  // 유동상을 돌렸지만(가동시간 실적) 스팀을 보내지 않은 날은 운휴가 아니다
  const warmup = flatMonth(2000, { actuals: { 1: { COMBO: 1700, WASTE2: 380, FLUID: 0, fluidRunHours: 22 } } });
  assert.equal(warmup.days[0].idleHours.FLUID, 2);
  assert.equal(warmup.days[0].fluidRunHours, 22);
});

test("설비 운휴 — 그 호기의 스팀량을 가동한 날에 몰아 준다 (월 합계는 그대로)", () => {
  const days = {};
  for (let d = 1; d <= 15; d += 1) days[d] = { downPm2: 24 };
  const r = calc.computeMonth({
    year: 2026, month: 6,
    machines: { "PM-2": { productionTon: 3000, unit: 2 }, "PM-3": { productionTon: 30000, unit: 2 } },
    boilers: BOILERS, settings: SETTINGS, days, actuals: {},
  });
  near(r.days[0].demand, 2000, 1e-6, "제지2 운휴 날");            // 제지3 60,000 / 30
  near(r.days[20].demand, 2000 + 6000 / 15, 1e-6, "제지2 가동 날");
  near(r.totalSteam, 66000, 1e-6, "월 합계");
  assert.equal(r.machines[0].runHours, 15 * 24);
  // 한 달 내내 운휴로 고르면 넣을 날이 없다고 알려 준다
  const all = {};
  for (let d = 1; d <= 30; d += 1) all[d] = { downPm2: 24 };
  const none = calc.computeMonth({
    year: 2026, month: 6, machines: { "PM-2": { productionTon: 3000, unit: 2 } },
    boilers: BOILERS, settings: SETTINGS, days: all, actuals: {},
  });
  assert.equal(none.machines[0].unplaced, true);
});

test("생산계획이 없는 달 — 실적만 보여 주고 남은 날은 계산하지 않는다", () => {
  const r = calc.computeMonth({
    year: 2026, month: 6, machines: {}, boilers: BOILERS, settings: SETTINGS, days: {},
    actuals: { 1: { COMBO: 1500, WASTE2: 380, FLUID: 400 } },
  });
  assert.equal(r.demandTarget, null);
  assert.equal(r.totalSteam, 2280);
  assert.equal(r.days[1].noPlan, true);
  assert.equal(r.days[1].idleHours.FLUID, 0);
  // 실적일(1일)의 폐합성 1호기 고정비만 남는다
  assert.equal(r.boilers.find((b) => b.code === "WASTE1").idleHours, 24);
  assert.equal(r.boilers.find((b) => b.code === "FLUID").idleHours, 0);
});

test("우리 사정 운휴(일별 표에서 고름) — 그 보일러는 배분에서 빠지고 운휴 고정비가 나간다", () => {
  // 하루 2,100톤: 1일 폐합성2 종일, 2일 유동상 12시간, 3일 복합 종일을 우리 사정 운휴로 고른다
  const r = flatMonth(2100, { days: { 1: { idleWaste2: 24 }, 2: { idleFluid: 12 }, 3: { idleCombo: 24 } } });
  const d1 = r.days[0];
  assert.equal(d1.alloc.WASTE2, 0);
  assert.equal(d1.status.WASTE2, "idle");
  assert.equal(d1.idleHours.WASTE2, 24);                 // 고른 24시간이 그대로 고정비
  near(d1.alloc.COMBO + d1.alloc.FLUID + d1.alloc.LNG20 + d1.alloc.LNG50, 2100, 0.01, "다른 보일러가 받는다");
  const d2 = r.days[1];
  assert.ok(d2.alloc.FLUID <= 216 + 1e-6, "12시간만 돌리면 하루 최대의 절반까지");
  assert.equal(d2.idleHours.FLUID, 12);                  // 돌린 날도 고른 12시간은 고정비
  assert.equal(d2.fluidRunHours, 12);                    // 정산 가동시간도 12시간
  const d3 = r.days[2];
  assert.equal(d3.alloc.COMBO, 0);
  assert.equal(d3.status.COMBO, "idle");
  assert.equal(d3.idleHours.COMBO, 0);                   // 복합은 시간당 고정비가 없다 (보증량 BSC 로 낸다)
  assert.equal(r.combo.maintHours, 0);                   // 우리 사정이라 보증량을 줄이지 않는다
  // 정비와 같이 고르면 정비가 먼저 — 정비 시간에는 고정비가 없다
  const m = flatMonth(2100, { days: { 1: { stopWaste2: 24, idleWaste2: 24 } } });
  assert.equal(m.days[0].status.WASTE2, "maint");
  assert.equal(m.days[0].idleHours.WASTE2, 0);
});

test("하루 최대 기본값 — 0 보다 큰 실적의 상위 10% 평균", () => {
  const values = [];
  for (let i = 1; i <= 100; i += 1) values.push(i);
  assert.equal(calc.topDecileAverage(values.concat([0, null, ""])), 95.5);
  assert.equal(calc.topDecileAverage([]), null);
});

console.log(`\n${passed} passed${process.exitCode ? ", some FAILED" : ""}`);
