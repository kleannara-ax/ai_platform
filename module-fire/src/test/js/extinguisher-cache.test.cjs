// Run with: node --test module-fire/src/test/js/extinguisher-cache.test.cjs
// Synthetic fixtures only. No browser, DB, production credentials or external packages.
const test = require('node:test');
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const path = require('node:path');
const html = fs.readFileSync(path.resolve(__dirname, '../../main/resources/static/extinguishers.html'), 'utf8');
const code = html.slice(html.indexOf('function invalidateInventorySnapshot()'), html.indexOf('function updateTypeOptions('));
function fixture(count = 1000) {
  const content = [], statuses = {};
  for (let id = 1; id <= count; id++) {
    content.push({extinguisherId: id, serialNumber: `TEST-${id}`, buildingId: id % 2 + 1,
      floorId: id % 3 + 1, buildingName: `가상동${id % 2 + 1}`, floorName: `${id % 3 + 1}층`,
      extinguisherType: id % 2 ? '분말소화기' : '이산화탄소소화기', note: '가상(검증) [목록]', lastInspectionDate: null});
    statuses[id] = id % 3 === 0 ? ['inspect'] : id % 3 === 1 ? ['urgent'] : ['planned'];
  }
  return {inventory: {content, totalElements: count, types: ['분말소화기','이산화탄소소화기'], asOfDate: '2026-10-23'}, statuses};
}
function environment(data = fixture()) {
  let now = Date.parse('2026-10-23T00:00:00Z'), calls = 0;
  const select = {value: ''};
  const Clock = class extends Date { static now() {return now;} };
  const context = vm.createContext({Intl, Date: Clock, console, API: {req: async () => {
    calls++; return {ok: true, json: async () => ({success:true, data})};
  }}, document: {getElementById: () => select}});
  vm.runInContext(`let inventorySnapshot=null, inventorySnapshotPromise=null, inventoryGeneration=0, buildingOptionsPromise=null;
    const inventoryCacheMs=300000, listPageSize=200; let currentStatusType=null;
    let currentSort={key:'serialNumber',direction:'asc'}; ${code}`, context);
  return {context, select, run: source => vm.runInContext(source, context), calls:()=>calls, tick:ms=>{now+=ms;}};
}
test('1000 rows: full sorting occurs before page slicing, with no missing or duplicate IDs', () => {
  const e=environment(); e.context.snapshot=fixture();
  e.run("currentSort.direction='desc'");
  assert.equal(e.run("queryCachedInventory(snapshot,'',[],'',0).content[0].serialNumber"),'TEST-1000');
  assert.equal(e.run("queryCachedInventory(snapshot,'',[],'',1).content[0].serialNumber"),'TEST-800');
  const ids=e.run("Array.from({length:5},(_,page)=>queryCachedInventory(snapshot,'',[],'',page).content.map(r=>r.extinguisherId)).flat()");
  assert.equal(new Set(ids).size,1000);
  assert.equal(ids[999],1);
});
test('all three status toggles keep whole-inventory counters and restore the full list', () => {
  const e=environment(); e.context.snapshot=fixture();
  const before=JSON.stringify(e.run("queryCachedInventory(snapshot,'',[],'',0).summary"));
  for(const status of ['inspect','planned','urgent']) {
    e.context.selectedStatus=status;e.run('currentStatusType=selectedStatus');
    const on=e.run("queryCachedInventory(snapshot,'',[],'',0)");
    assert.equal(on.totalMatching,1000);assert.ok(on.totalElements<1000);
    assert.equal(JSON.stringify(on.summary),before);
    e.run('currentStatusType=null');
    assert.equal(e.run("queryCachedInventory(snapshot,'',[],'',0).totalElements"),1000);
  }
});
test('type, building, floor and keyword filters apply globally; export equals concatenated pages', () => {
  const e=environment(); e.context.snapshot=fixture();e.select.value='이산화탄소소화기';
  assert.equal(e.run("queryCachedInventory(snapshot,'',[],'',0).totalElements"),500);
  assert.equal(e.run("queryCachedInventory(snapshot,'', [1,2], '',0).totalElements"),500);
  assert.equal(e.run("queryCachedInventory(snapshot,'', [2], '',0).totalElements"),0);
  assert.equal(e.run("queryCachedInventory(snapshot,'가상(검증) [목록]', [], '',0).totalElements"),500);
  assert.equal(e.run("queryCachedInventory(snapshot,'가상%', [], '',0).totalElements"),500);
  assert.equal(e.run("queryCachedInventory(snapshot,'이산화탄소소화_', [], '',0).totalElements"),500);
  const pages=e.run("Array.from({length:3},(_,page)=>queryCachedInventory(snapshot,'',[],'',page).content.map(r=>r.extinguisherId)).flat()");
  const exported=e.run("queryCachedInventory(snapshot,'',[],'',0,true).content.map(r=>r.extinguisherId)");
  assert.deepEqual([...pages],[...exported]);
});
test('status definitions are taken from the server, not recomputed from browser dates', () => {
  const e=environment();const s=fixture(1);s.statuses[1]=[];e.context.snapshot=s;
  e.run("currentStatusType='inspect'");
  assert.equal(e.run("queryCachedInventory(snapshot,'',[],'',0).totalElements"),0);
});
test('concurrent initial requests coalesce and ordinary interactions reuse the full snapshot', async () => {
  const e=environment();
  await e.run('Promise.all([getInventorySnapshot(),getInventorySnapshot(),getInventorySnapshot()])');
  await e.run('getInventorySnapshot()');assert.equal(e.calls(),1);
  await e.run('getInventorySnapshot(true)');assert.equal(e.calls(),2);
});
test('five-minute expiry and Korea midnight both cause a fresh snapshot', async () => {
  const e=environment();await e.run('getInventorySnapshot()');
  e.tick(300001);await e.run('getInventorySnapshot()');assert.equal(e.calls(),2);
  const midnight=environment();midnight.tick(15*3600000-1000);await midnight.run('getInventorySnapshot()');
  midnight.tick(2000);await midnight.run('getInventorySnapshot()');assert.equal(midnight.calls(),2);
});
test('partial or failed snapshots are not cached; retry remains possible', async () => {
  const broken=fixture(1);broken.inventory.totalElements=2;const e=environment(broken);
  await assert.rejects(e.run('getInventorySnapshot()'));
  assert.equal(e.run('inventorySnapshot'),null);
  broken.inventory.totalElements=1;await e.run('getInventorySnapshot()');assert.equal(e.calls(),2);
});
test('empty inventory remains empty and page indices clamp safely', () => {
  const e=environment();e.context.snapshot=fixture(0);
  const result=e.run("queryCachedInventory(snapshot,'',[],'',99)");
  assert.equal(result.totalElements,0);assert.equal(result.number,0);assert.equal(result.content.length,0);
});
