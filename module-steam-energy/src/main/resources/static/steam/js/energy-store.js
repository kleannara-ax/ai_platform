/* 에너지 회계비용 화면 공용 저장소 — 규격 표(/steam-energy-api/energy-*) 를 옛 셀 좌표 (row_key, col_index) 로 읽고 쓴다.
 *
 * 화면 스크립트는 예전처럼 "row_key|col_index" 로 값을 다루고, 여기서 energy-cell-map.js(생성 파일) 로
 * (표 alias, 필드, 연, 월) 로 바꿔 행 단위 POST 를 보낸다. 2026-11 규격 전환.
 *
 *  열 규약(colRule):
 *   eap / efr : 1 = 전년 12월 → (연-1, 12), 2..13 = 1..12월
 *   efp / efs : 1..12 = 1..12월
 *   emc       : 0 = 전년 12월 → (연-1, 12), 1..12, 13 = 다음해 1월 → (연+1, 1)
 *   emcNote   : 연도 비고 (월 없음, col 0)
 *  합계·평균 열(14/15 등)은 매핑되지 않아 저장되지 않는다.
 *
 *  사용:
 *   const store = EnergyStore.forLegacy("energy_fuel_ratio");
 *   await store.load(2025);                       // 그 해 + 이웃 연도(전년 12월·다음해 1월) 행을 읽어 둔다
 *   store.cells()  → [{row_key, col_index, cell_value}]   // 옛 조회 응답 모양
 *   await store.set("t01|r01", 2, "123.4")       // 한 칸 저장 (행 전체 POST) → {row_key, col_index, cell_value}
 *   await store.setMany([{row_key, col_index, cell_value}, …])   // 여러 칸을 행 단위로 모아 저장
 *   EnergyStore.fetchLegacy("energy_month_compare", 2025) → Map("row_key|col_index" → 값 문자열)  // 다른 화면 읽기용
 */
(function (global) {
  "use strict";

  const API = () => {
    if (!global.SteamEnergyApi) throw new Error("steam-energy-api.js 가 로드되지 않았습니다.");
    return global.SteamEnergyApi;
  };
  const MAP = () => {
    if (!global.EnergyCellMap) throw new Error("energy-cell-map.js 가 로드되지 않았습니다.");
    return global.EnergyCellMap;
  };

  /** 옛 col_index → { yearOffset, month } (매핑 없으면 null) */
  function targetOf(rule, col) {
    col = Number(col);
    if (rule === "eap" || rule === "efr") {
      if (col === 1) return { yearOffset: -1, month: 12 };
      if (col >= 2 && col <= 13) return { yearOffset: 0, month: col - 1 };
      return null;
    }
    if (rule === "efp" || rule === "efs") {
      return col >= 1 && col <= 12 ? { yearOffset: 0, month: col } : null;
    }
    if (rule === "emc") {
      if (col === 0) return { yearOffset: -1, month: 12 };
      if (col >= 1 && col <= 12) return { yearOffset: 0, month: col };
      if (col === 13) return { yearOffset: 1, month: 1 };
      return null;
    }
    if (rule === "emcNote") return col === 0 ? { yearOffset: 0, month: null } : null;
    throw new Error("unknown colRule " + rule);
  }

  /** 행 (연, 월) → 기준 연도 year 의 옛 col_index (그 해 시트에 자리가 없으면 null) */
  function colIndexOf(rule, year, rowYear, month) {
    if (rule === "eap" || rule === "efr") {
      if (rowYear === year && month != null) return month + 1;
      if (rowYear === year - 1 && month === 12) return 1;
      return null;
    }
    if (rule === "efp" || rule === "efs") return rowYear === year && month != null ? month : null;
    if (rule === "emc") {
      if (rowYear === year && month != null) return month;
      if (rowYear === year - 1 && month === 12) return 0;
      if (rowYear === year + 1 && month === 1) return 13;
      return null;
    }
    if (rule === "emcNote") return rowYear === year ? 0 : null;
    throw new Error("unknown colRule " + rule);
  }

  function valueText(v) {
    if (v === null || v === undefined) return null;
    return String(v);
  }

  function rowKeyOf(row) { return `${row.yearNo}|${row.monthNo == null ? "" : row.monthNo}`; }

  /** 표(alias)의 연도 행 조회 → Map("year|month" → 행) */
  async function fetchRows(alias, years) {
    const table = MAP().tables[alias];
    const map = new Map();
    const list = table.grain === "year" ? years.filter((y, i, a) => a.indexOf(y) === i) : years;
    await Promise.all(list.map(async (year) => {
      const rows = await API().get(`${table.path}?year=${year}`);
      (rows || []).forEach((row) => map.set(rowKeyOf(row), row));
    }));
    return map;
  }

  /** 다른 화면이 옛 조회 모양으로 읽을 때: Map("row_key|col_index" → 값 문자열) */
  async function fetchLegacy(legacyTable, year) {
    const legacy = MAP().legacy[legacyTable] || {};
    const aliases = [...new Set(Object.values(legacy).map(([alias]) => alias))];
    const rowsByAlias = {};
    await Promise.all(aliases.map(async (alias) => {
      rowsByAlias[alias] = await fetchRows(alias, [year - 1, year, year + 1]);
    }));
    const out = new Map();
    Object.entries(legacy).forEach(([rowKey, [alias, field]]) => {
      const rule = MAP().tables[alias].colRule;
      rowsByAlias[alias].forEach((row) => {
        const col = colIndexOf(rule, year, row.yearNo, row.monthNo == null ? null : row.monthNo);
        if (col === null) return;
        const v = valueText(row[field]);
        if (v !== null && v !== "") out.set(`${rowKey}|${col}`, v);
      });
    });
    return out;
  }

  function forLegacy(legacyTable) {
    const legacy = MAP().legacy[legacyTable];
    if (!legacy) throw new Error("매핑 없는 논리 표: " + legacyTable);
    const aliases = [...new Set(Object.values(legacy).map(([alias]) => alias))];
    const state = { year: null, rows: {} };   // rows[alias] = Map("year|month" → 행)
    // 쓰기는 한 줄로 세운다 — 입력 저장(POST)과 계산값 저장(PUT)이 같은 행을 동시에 고치면 DB 가 거부한다
    let writeQueue = Promise.resolve();
    function enqueue(work) {
      const run = writeQueue.then(work);
      writeQueue = run.catch(() => {});
      return run;
    }

    function entryOf(rowKey) {
      const m = legacy[rowKey];
      if (!m) return null;
      return { alias: m[0], field: m[1], rule: MAP().tables[m[0]].colRule, grain: MAP().tables[m[0]].grain, fields: MAP().tables[m[0]].fields };
    }

    async function load(year) {
      state.year = Number(year);
      await Promise.all(aliases.map(async (alias) => {
        state.rows[alias] = await fetchRows(alias, [state.year - 1, state.year, state.year + 1]);
      }));
    }

    function cells() {
      const out = [];
      Object.entries(legacy).forEach(([rowKey, [alias, field]]) => {
        const rule = MAP().tables[alias].colRule;
        (state.rows[alias] || new Map()).forEach((row) => {
          const col = colIndexOf(rule, state.year, row.yearNo, row.monthNo == null ? null : row.monthNo);
          if (col === null) return;
          const v = valueText(row[field]);
          if (v === null || v === "") return;
          out.push({ table_name: legacyTable, row_key: rowKey, col_index: col, cell_value: v });
        });
      });
      return out;
    }

    function get(rowKey, col) {
      const e = entryOf(rowKey);
      if (!e) return null;
      const t = targetOf(e.rule, col);
      if (!t) return null;
      const row = (state.rows[e.alias] || new Map()).get(`${state.year + t.yearOffset}|${t.month == null ? "" : t.month}`);
      return row ? valueText(row[e.field]) : null;
    }

    // (alias, year, month) 행 하나를 최신으로 읽어 필드를 바꾼 뒤 POST
    async function saveRow(alias, yearNo, monthNo, changes) {
      const table = MAP().tables[alias];
      const fresh = await fetchRows(alias, [yearNo]);
      const key = `${yearNo}|${monthNo == null ? "" : monthNo}`;
      const base = fresh.get(key) || {};
      const body = { yearNo };
      if (table.grain !== "year") body.monthNo = monthNo;
      Object.keys(table.fields).forEach((f) => { body[f] = base[f] === undefined ? null : base[f]; });
      Object.entries(changes).forEach(([f, v]) => { body[f] = v; });
      const saved = await API().post(table.path, body);
      if (!state.rows[alias]) state.rows[alias] = new Map();
      state.rows[alias].set(key, saved);
      return saved;
    }

    function parseValue(e, value) {
      if (value === null || value === undefined || value === "") return null;
      if (e.fields[e.field] === "text") return String(value);
      const n = API().num(value);
      if (n === null) throw new Error(`숫자가 아닙니다: ${value}`);
      return n;
    }

    function set(rowKey, col, value) { return enqueue(() => setNow(rowKey, col, value)); }
    async function setNow(rowKey, col, value) {
      const e = entryOf(rowKey);
      if (!e) throw new Error(`저장할 수 없는 칸입니다: ${rowKey} (매핑 없음 — 계산 전용)`);
      const t = targetOf(e.rule, col);
      if (!t) throw new Error(`저장할 수 없는 열입니다: ${rowKey} / ${col} (합계·평균 열)`);
      const saved = await saveRow(e.alias, state.year + t.yearOffset, t.month, { [e.field]: parseValue(e, value) });
      return { table_name: legacyTable, row_key: rowKey, col_index: Number(col), cell_value: valueText(saved[e.field]) };
    }

    /** 여러 칸을 표·연도 단위로 묶어 한 번에 저장(PUT /year, clearMissing=false). 매핑 없는 칸은 건너뛴다. 반환: 저장된 칸 목록 */
    function setMany(cells) { return enqueue(() => setManyNow(cells)); }
    async function setManyNow(cells) {
      // alias|year → { monthNo → changes }
      const groups = new Map();
      const targets = [];
      (cells || []).forEach((c) => {
        const e = entryOf(c.row_key);
        if (!e) return;
        const t = targetOf(e.rule, c.col_index);
        if (!t) return;
        const yearNo = state.year + t.yearOffset;
        const key = `${e.alias}|${yearNo}`;
        if (!groups.has(key)) groups.set(key, { alias: e.alias, yearNo, months: new Map() });
        const g = groups.get(key);
        const mk = t.month == null ? "" : String(t.month);
        if (!g.months.has(mk)) g.months.set(mk, {});
        g.months.get(mk)[e.field] = parseValue(e, c.cell_value);
        targets.push({ cell: c, alias: e.alias, yearNo, month: t.month, field: e.field });
      });
      for (const g of groups.values()) {
        const table = MAP().tables[g.alias];
        const fresh = await fetchRows(g.alias, [g.yearNo]);
        if (table.grain === "year") {
          // 연도 표는 한 행뿐 — 행 전체 POST
          const changes = g.months.get("") || {};
          await saveRow(g.alias, g.yearNo, null, changes);
          continue;
        }
        const rows = [];
        g.months.forEach((changes, mk) => {
          const monthNo = Number(mk);
          const base = fresh.get(`${g.yearNo}|${monthNo}`) || {};
          const body = { yearNo: g.yearNo, monthNo };
          Object.keys(table.fields).forEach((f) => { body[f] = base[f] === undefined ? null : base[f]; });
          Object.entries(changes).forEach(([f, v]) => { body[f] = v; });
          rows.push(body);
        });
        await API().put(`${table.path}/year`, { year: g.yearNo, clearMissing: false, rows });
        const after = await fetchRows(g.alias, [g.yearNo]);
        if (!state.rows[g.alias]) state.rows[g.alias] = new Map();
        after.forEach((row, k) => state.rows[g.alias].set(k, row));
      }
      return targets.map((t) => {
        const row = (state.rows[t.alias] || new Map()).get(`${t.yearNo}|${t.month == null ? "" : t.month}`);
        return { table_name: legacyTable, row_key: t.cell.row_key, col_index: Number(t.cell.col_index), cell_value: row ? valueText(row[t.field]) : null };
      });
    }

    /** 그 해 월 행의 값을 모두 비운다 (옛 '초기화' = by-table 삭제). 이웃 연도 행·연도 비고는 그대로. */
    function clearYear(year) { return enqueue(() => clearYearNow(year)); }
    async function clearYearNow(year) {
      for (const alias of aliases) {
        const table = MAP().tables[alias];
        if (table.grain === "year") continue;
        await API().put(`${table.path}/year`, { year: Number(year), clearMissing: true, rows: [] });
      }
      await load(year);
    }

    return { legacyTable, aliases, load, cells, get, set, setMany, clearYear, year: () => state.year };
  }

  global.EnergyStore = { forLegacy, fetchLegacy, targetOf, colIndexOf };
})(window);
