/* 스팀에너지관리 — 규격 표 행 저장소 (묶음 3 화면 공용).
 *
 * 규격 API 는 "행 전체 업서트" 다. 화면은 칸 하나를 고치므로, 저장할 때마다 그 범위의 최신 행을 다시 읽어
 * 고친 칸만 바꾼 행 전체를 POST 한다 (다른 화면·다른 사용자가 넣은 칸을 지우지 않게).
 * 모든 저장은 한 줄로 세워 차례로 보낸다 — 같은 행을 동시에 고치면 DB 가 거부한다.
 *
 *   const store = SteamRowStore.create({ path: "combo-daily-productions", keyFields: ["usageDate"] });
 *   await store.load({ year: 2026, month: 3 });      // month 생략 = 한 해
 *   store.get({ usageDate: "2026-03-05" });          // 행 또는 null
 *   await store.save({ usageDate: "2026-03-05" }, { comboSteam: 123 });
 *
 * 필요: steam-energy-api.js
 */
(function (global) {
  "use strict";

  var META = { createdAt: 1, updatedAt: 1, isUpdate: 1 };
  var queue = Promise.resolve();

  function enqueue(job) {
    var run = queue.then(job, job);
    queue = run.catch(function () {});
    return run;
  }

  function create(def) {
    // steam-energy-api.js 가 이 파일보다 뒤에 실려도 되게, 부를 때 찾는다
    var Api = { get: function (p) { return global.SteamEnergyApi.get(p); }, post: function (p, b) { return global.SteamEnergyApi.post(p, b); } };
    var keyFields = def.keyFields;
    var rows = new Map();
    var scope = null;

    function keyOf(row) {
      return keyFields.map(function (f) { return String(row[f]); }).join("|");
    }

    function url(s) {
      return "/" + def.path + "?year=" + s.year + (s.month ? "&month=" + s.month : "");
    }

    function index(list) {
      var map = new Map();
      (list || []).forEach(function (row) { map.set(keyOf(row), row); });
      return map;
    }

    async function load(s) {
      scope = { year: Number(s.year), month: s.month ? Number(s.month) : null };
      rows = index(await Api.get(url(scope)));
      return store;
    }

    function get(key) {
      return rows.get(keyOf(key)) || null;
    }

    function all() {
      return Array.from(rows.values());
    }

    /**
     * 행 하나의 일부 칸을 고친다. patch = { 필드: 값(null 이면 비움) }. 저장된 행을 돌려준다.
     * saveScope 를 주면 load 한 범위 대신 그 범위에서 최신 행을 읽는다 (load 없이 저장만 할 때).
     */
    function save(key, patch, saveScope) {
      var s = saveScope ? { year: Number(saveScope.year), month: saveScope.month ? Number(saveScope.month) : null } : scope;
      return enqueue(async function () {
        var fresh = index(await Api.get(url(s)));
        var body = {};
        var base = fresh.get(keyOf(key)) || {};
        Object.keys(base).forEach(function (f) {
          if (!META[f] && f !== def.idField) body[f] = base[f];
        });
        keyFields.forEach(function (f) { body[f] = key[f]; });
        Object.keys(patch).forEach(function (f) { body[f] = patch[f]; });
        var saved = await Api.post("/" + def.path, body);
        if (s === scope) {
          rows = fresh;
          rows.set(keyOf(saved), saved);
        }
        return saved;
      });
    }

    var store = { load: load, get: get, all: all, save: save, scope: function () { return scope; } };
    return store;
  }

  // ── 조회 재사용: 같은 범위를 잠깐(3초) 다시 묻지 않는다 (화면이 12개월을 따로 물어도 요청은 한 번) ──
  var cache = new Map();   // "path|year|month" → { at, promise }
  var CACHE_MS = 3000;
  function cachedGet(path, year, month) {
    var key = path + "|" + year + "|" + (month || "");
    var hit = cache.get(key);
    if (hit && Date.now() - hit.at < CACHE_MS) return hit.promise;
    var promise = global.SteamEnergyApi.get("/" + path + "?year=" + year + (month ? "&month=" + month : ""))
      .catch(function (e) { cache.delete(key); throw e; });
    cache.set(key, { at: Date.now(), promise: promise });
    return promise;
  }
  function clearCache() { cache.clear(); }

  function monthKeysBetween(from, to) {
    var keys = [];
    var y = Number(from.slice(0, 4)), m = Number(from.slice(5, 7));
    var ey = Number(to.slice(0, 4)), em = Number(to.slice(5, 7));
    if (!(y >= 2000 && ey >= 2000 && m >= 1 && em >= 1)) return keys;
    while (y < ey || (y === ey && m <= em)) {
      keys.push(y + "-" + String(m).padStart(2, "0"));
      m += 1;
      if (m > 12) { m = 1; y += 1; }
    }
    return keys;
  }

  /**
   * 옛 셀 저장소(/tables/steam_cell_value) 호출 모양을 그대로 받아 규격 표로 옮기는 껍데기를 만든다.
   * 화면은 fetch 대신 반환된 fetch 를 부르기만 하면 된다.
   *
   *   def.tables                       이 껍데기가 맡는 옛 논리 표 이름들 (나머지 요청은 원래 fetch 로 넘긴다)
   *   def.rows(table, monthKey, query) → Promise<[{ rowKey, col, value }]>   그 월 키의 칸 목록 (값 없는 칸은 걸러 준다)
   *   def.locate(table, monthKey, rowKey, col) → { store, scope, key, field, text? } | null   저장할 자리 (null = 저장하지 않는 칸)
   *
   *   GET   /tables/steam_cell_value?month=…&tableNames=…   (fromMonth / toMonth 도 가능)
   *   POST  /tables/steam_cell_value          body { month, table_name, row_key, col_index, cell_value }
   *   PATCH /tables/steam_cell_value/{id}     body 같음
   */
  function cellShim(def) {
    function has(v) { return v !== null && v !== undefined && v !== ""; }
    function row(table, monthKey, rowKey, col, value) {
      return { id: table + "#" + monthKey + "#" + rowKey + "#" + col, month: monthKey, table_name: table,
        row_key: rowKey, col_index: col, cell_value: value === null || value === undefined ? "" : String(value) };
    }
    async function legacyRows(table, monthKey, query) {
      var list = await def.rows(table, String(monthKey), query || new URLSearchParams());
      return (list || []).filter(function (c) { return has(c.value); })
        .map(function (c) { return row(table, String(monthKey), c.rowKey, c.col === undefined ? 1 : c.col, c.value); });
    }
    async function saveCell(table, monthKey, rowKey, col, rawValue) {
      var text = rawValue === null || rawValue === undefined ? "" : String(rawValue);
      var target = def.locate(table, String(monthKey), String(rowKey), Number(col));
      if (target) {
        var patch = {};
        patch[target.field] = target.text ? (text.trim() === "" ? null : text) : global.SteamEnergyApi.num(text);
        await target.store.save(target.key, patch, target.scope);
        clearCache();
      }
      return row(table, String(monthKey), String(rowKey), Number(col), text);
    }
    function response(ok, body) {
      return { ok: ok, status: ok ? 200 : 500, json: async function () { return body; }, text: async function () { return JSON.stringify(body); } };
    }
    async function shimFetch(url, init) {
      var method = (init && init.method ? String(init.method) : "GET").toUpperCase();
      try {
        if (method === "GET") {
          var query = new URLSearchParams(String(url).split("?")[1] || "");
          var names = (query.get("tableNames") || "").split(",").map(function (s) { return s.trim(); }).filter(Boolean);
          if (!names.length || !names.every(function (n) { return def.tables.indexOf(n) >= 0; })) return global.fetch(url, init);
          var months = query.get("month") ? [query.get("month")] : monthKeysBetween(query.get("fromMonth") || "", query.get("toMonth") || "");
          var jobs = [];
          names.forEach(function (name) { months.forEach(function (mk) { jobs.push(legacyRows(name, mk, query)); }); });
          var data = [].concat.apply([], await Promise.all(jobs));
          // 한 번에 다 준다 — 화면의 쪽 넘김 반복(total ÷ limit)이 한 쪽으로 끝나게
          return response(true, { data: data, total: data.length, page: 1, limit: Math.max(data.length, 1) });
        }
        var body = JSON.parse((init && init.body) || "{}");
        if (def.tables.indexOf(body.table_name) < 0) return global.fetch(url, init);
        return response(true, await saveCell(body.table_name, body.month, body.row_key, body.col_index === undefined ? 1 : body.col_index, body.cell_value));
      } catch (e) {
        console.error("[SteamRowStore.cellShim]", e);
        return response(false, { message: e && e.message ? e.message : String(e) });
      }
    }
    return { fetch: shimFetch, legacyRows: legacyRows, saveCell: saveCell };
  }

  global.SteamRowStore = { create: create, enqueue: enqueue, cachedGet: cachedGet, clearCache: clearCache, cellShim: cellShim };
})(window);
