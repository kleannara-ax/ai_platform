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

  global.SteamRowStore = { create: create, enqueue: enqueue };
})(window);
