/* =========================================================================
 * 개인정보 위수탁 모듈 - 목록 표 열 너비·순서 사용자 편집 (무빌드 순수 JS)
 *
 *  - 열 제목을 끌어서 좌우 순서 변경 (HTML5 드래그)
 *  - 열 제목 오른쪽 경계를 끌어서 너비 조정 (마우스/터치, 더블클릭: 그 열만 기본값)
 *  - [열 설정] 메뉴: ◀ ▶ 로 순서 이동, 너비(px) 직접 입력, 순서·너비 초기화
 *  - 키보드: 열 제목에 포커스 후 Alt+Shift+←/→ 로 순서 이동, 경계에 포커스 후 ←/→ 로 너비 조정
 *  - 설정은 이 브라우저(localStorage)에 로그인 사용자별·표별로 저장 (서버·DB 변경 없음)
 *
 *  사용: PiTable.enhance(tableEl, { id: 'files' })          // 표를 그린 직후
 *        PiTable.refresh(tableEl)                           // tbody 를 다시 그린 뒤 (순서 재적용)
 *  고정 열: 체크박스 열, 제목이 빈 버튼 열, cols 정의의 fixed:true, .pi-col-sticky-end 는 움직이지 않음
 * ========================================================================= */
const PiTable = (() => {
  const VERSION = 'v1';
  let notifyFn = null;
  let drag = null;   // { table, key }

  const loginId = () => {
    try { return (JSON.parse(localStorage.getItem('fireweb_user') || '{}') || {}).loginId || 'anon'; } catch (e) { return 'anon'; }
  };
  const storeKey = (id, kind) => `personalinfo.table.${loginId()}.${id}.${kind}.${VERSION}`;
  const load = (k) => { try { return JSON.parse(localStorage.getItem(k) || 'null'); } catch (e) { return null; } };
  const save = (k, v) => { try { localStorage.setItem(k, JSON.stringify(v)); } catch (e) { /* 저장 불가(사생활 보호 모드 등) */ } };
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const heads = (t) => (t && t.tHead && t.tHead.rows[0] ? [...t.tHead.rows[0].cells] : []);
  const thOf = (t, key) => heads(t).find((th) => th.dataset.col === key);
  const isFixed = (th) => !!th && th.dataset.fixed === '1';
  const defOf = (st, key) => (st.defs ? st.defs.find((d) => d.key === key) : null);
  const minOf = (st, key) => { const d = defOf(st, key); return d && d.min ? d.min : 48; };
  const MAX_W = 900;

  // ───────── 순서 ─────────
  /** 기본(원래) 순서의 열 키 */
  const defaultKeys = (t) => heads(t).slice().sort((a, b) => a.dataset.ci - b.dataset.ci).map((th) => th.dataset.col);

  /** 저장된 순서를 반영한 최종 순서. 고정 열은 원래 자리, 움직이는 열은 저장 순서 (새로 생긴 열은 원래 앞 열 뒤에) */
  function desiredKeys(t) {
    const st = t._pi;
    const def = defaultKeys(t);
    const movableDef = def.filter((k) => !isFixed(thOf(t, k)));
    const saved = load(storeKey(st.id, 'order'));
    let movable = Array.isArray(saved) ? saved.filter((k) => movableDef.includes(k)) : movableDef.slice();
    movableDef.forEach((k, i) => {
      if (movable.includes(k)) return;
      const prev = movableDef.slice(0, i).reverse().find((p) => movable.includes(p));
      movable.splice(prev ? movable.indexOf(prev) + 1 : 0, 0, k);
    });
    let m = 0;
    return def.map((k) => (isFixed(thOf(t, k)) ? k : movable[m++]));
  }

  /** 모든 행의 셀을 열 순서대로 다시 배치 (colspan 이 있는 안내 행은 건너뜀) */
  function applyOrder(t) {
    if (!t || !t._pi) return;
    const hs = heads(t);
    const n = hs.length;
    const ciOf = {};
    hs.forEach((th) => { ciOf[th.dataset.col] = th.dataset.ci; });
    const seq = desiredKeys(t).map((k) => ciOf[k]);
    [...t.rows].forEach((tr) => {
      const cells = [...tr.cells];
      if (cells.length !== n) return;
      // 새로 그린 행은 원래 순서로 그려지므로 위치 = 원래 순번
      cells.forEach((c, i) => { if (c.dataset.ci === undefined) c.dataset.ci = String(i); });
      const byCi = {};
      cells.forEach((c) => { byCi[c.dataset.ci] = c; });
      if (seq.every((ci, i) => cells[i] === byCi[ci])) return;
      seq.forEach((ci) => { if (byCi[ci]) tr.appendChild(byCi[ci]); });
    });
  }

  function movableOrder(t) {
    return heads(t).filter((th) => !isFixed(th)).map((th) => th.dataset.col);
  }

  function saveOrder(t, order) {
    save(storeKey(t._pi.id, 'order'), order);
    applyOrder(t);
  }

  /** key 열을 target 열의 앞(after=false)/뒤(after=true)로 */
  function moveTo(t, key, targetKey, after) {
    if (key === targetKey || isFixed(thOf(t, targetKey))) return;
    const order = movableOrder(t).filter((k) => k !== key);
    const idx = order.indexOf(targetKey);
    if (idx < 0) return;
    order.splice(after ? idx + 1 : idx, 0, key);
    saveOrder(t, order);
  }

  /** key 열을 좌(-1)/우(+1)로 한 칸 */
  function moveBy(t, key, delta) {
    const order = movableOrder(t);
    const i = order.indexOf(key);
    const j = i + delta;
    if (i < 0 || j < 0 || j >= order.length) return false;
    [order[i], order[j]] = [order[j], order[i]];
    saveOrder(t, order);
    return true;
  }

  // ───────── 너비 ─────────
  function savedWidths(st) {
    const v = load(storeKey(st.id, 'widths'));
    if (v && typeof v === 'object') return v;
    if (st.legacyWidthKey) {   // 이전 버전(계약 목록 너비) 설정 이어받기
      const l = load(st.legacyWidthKey);
      if (l && typeof l === 'object') return l;
    }
    return {};
  }

  /** 정의(cols)가 있거나 사용자가 너비를 바꾼 표는 고정 레이아웃 + 열별 너비, 아니면 자동 너비 */
  function applyWidths(t) {
    const st = t._pi;
    const saved = st.widths;
    const hs = heads(t);
    if (!st.defs && !Object.keys(saved).length) {
      t.classList.remove('pi-tbl-fixed');
      hs.forEach((th) => { th.style.width = ''; });
      t.style.width = '';
      t.style.minWidth = '';
      return;
    }
    let total = 0;
    hs.forEach((th) => {
      const k = th.dataset.col;
      const d = defOf(st, k);
      const w = Math.max(minOf(st, k), Math.min(MAX_W, Number(saved[k]) || (d ? d.width : 0) || Number(th.dataset.w) || 120));
      th.style.width = w + 'px';
      total += w;
    });
    t.classList.add('pi-tbl-fixed');
    t.style.width = total + 'px';   // 합계 너비 (넘치면 가로 스크롤)
    t.style.minWidth = '100%';
  }

  /** 자동 너비 표를 처음 조정할 때: 현재 화면 너비를 모든 열의 시작값으로 잡는다 */
  function snapshot(t) {
    const st = t._pi;
    heads(t).forEach((th) => {
      const w = Math.round(th.getBoundingClientRect().width);
      th.dataset.w = String(w || 120);
      if (!st.defs && !st.widths[th.dataset.col]) st.widths[th.dataset.col] = w || 120;
    });
  }

  function setWidth(t, key, w, persist) {
    const st = t._pi;
    if (!st.defs && !t.classList.contains('pi-tbl-fixed')) snapshot(t);
    const width = Math.max(minOf(st, key), Math.min(MAX_W, Math.round(w)));
    st.widths[key] = width;
    applyWidths(t);
    if (persist) save(storeKey(st.id, 'widths'), st.widths);
    return width;
  }

  const currentWidth = (t, key) => {
    const th = thOf(t, key);
    return th ? Math.round(parseFloat(th.style.width) || th.getBoundingClientRect().width) : 120;
  };

  // ───────── 열 제목 이벤트 ─────────
  function bindHeader(t, th) {
    const key = th.dataset.col;
    const name = th.dataset.label || '열';
    // 너비 조정 핸들
    const handle = document.createElement('span');
    handle.className = 'pi-col-resizer';
    handle.setAttribute('role', 'separator');
    handle.setAttribute('aria-orientation', 'vertical');
    handle.setAttribute('aria-label', name + ' 열 너비 조정');
    handle.tabIndex = 0;
    handle.title = '끌어서 너비 조정 · 더블클릭: 기본값';
    handle.draggable = false;
    th.appendChild(handle);

    const start = (startX) => {
      const startW = currentWidth(t, key);
      t.classList.add('pi-resizing');
      handle.classList.add('active');
      const move = (x) => setWidth(t, key, startW + (x - startX), false);
      const onMouse = (e) => { e.preventDefault(); move(e.clientX); };
      const onTouch = (e) => { if (e.touches[0]) move(e.touches[0].clientX); };
      const end = () => {
        t.classList.remove('pi-resizing');
        handle.classList.remove('active');
        document.removeEventListener('mousemove', onMouse);
        document.removeEventListener('mouseup', end);
        document.removeEventListener('touchmove', onTouch);
        document.removeEventListener('touchend', end);
        save(storeKey(t._pi.id, 'widths'), t._pi.widths);
      };
      document.addEventListener('mousemove', onMouse);
      document.addEventListener('mouseup', end);
      document.addEventListener('touchmove', onTouch, { passive: true });
      document.addEventListener('touchend', end);
    };
    handle.addEventListener('mousedown', (e) => { e.preventDefault(); e.stopPropagation(); start(e.clientX); });
    handle.addEventListener('touchstart', (e) => { e.stopPropagation(); if (e.touches[0]) start(e.touches[0].clientX); }, { passive: true });
    handle.addEventListener('click', (e) => e.stopPropagation());
    handle.addEventListener('dblclick', (e) => {   // 이 열만 기본값
      e.stopPropagation();
      delete t._pi.widths[key];
      applyWidths(t);
      save(storeKey(t._pi.id, 'widths'), t._pi.widths);
    });
    handle.addEventListener('keydown', (e) => {
      if (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight') return;
      e.preventDefault();
      e.stopPropagation();
      setWidth(t, key, currentWidth(t, key) + (e.shiftKey ? 50 : 10) * (e.key === 'ArrowLeft' ? -1 : 1), true);
    });

    if (isFixed(th)) return;
    // 순서 변경: 끌어서 놓기
    th.draggable = true;
    th.tabIndex = 0;
    th.title = (th.title ? th.title + ' · ' : '') + '끌어서 열 순서 변경 (Alt+Shift+←/→)';
    th.setAttribute('aria-roledescription', '순서 변경 가능한 열 제목');
    const clearMarks = () => heads(t).forEach((h) => h.classList.remove('pi-drop-before', 'pi-drop-after'));
    th.addEventListener('dragstart', (e) => {
      drag = { table: t, key };
      th.classList.add('pi-dragging');
      e.dataTransfer.effectAllowed = 'move';
      try { e.dataTransfer.setData('text/plain', name); } catch (err) { /* IE 계열 */ }
    });
    th.addEventListener('dragend', () => { th.classList.remove('pi-dragging'); clearMarks(); drag = null; });
    th.addEventListener('dragover', (e) => {
      if (!drag || drag.table !== t || drag.key === key) return;
      e.preventDefault();
      e.dataTransfer.dropEffect = 'move';
      const r = th.getBoundingClientRect();
      const after = e.clientX > r.left + r.width / 2;
      clearMarks();
      th.classList.add(after ? 'pi-drop-after' : 'pi-drop-before');
    });
    th.addEventListener('dragleave', () => th.classList.remove('pi-drop-before', 'pi-drop-after'));
    th.addEventListener('drop', (e) => {
      if (!drag || drag.table !== t) return;
      e.preventDefault();
      const after = th.classList.contains('pi-drop-after');
      const from = drag.key;
      clearMarks();
      moveTo(t, from, key, after);
      thOf(t, from)?.focus({ preventScroll: true });
    });
    th.addEventListener('keydown', (e) => {
      if (!(e.altKey && e.shiftKey) || (e.key !== 'ArrowLeft' && e.key !== 'ArrowRight')) return;
      e.preventDefault();
      if (moveBy(t, key, e.key === 'ArrowLeft' ? -1 : 1)) thOf(t, key)?.focus();
    });
  }

  // ───────── [열 설정] 메뉴 ─────────
  function buildTools(t, st) {
    let host = st.tools;
    if (host === false) return;
    if (typeof host === 'string') host = document.getElementById(host);
    let box;
    if (host) {
      host.innerHTML = '';
      box = document.createElement('div');
      box.className = 'dropdown pi-tbl-tools-inline';
      host.appendChild(box);
    } else {
      const anchor = t.closest('.table-responsive') || t;
      const prev = anchor.previousElementSibling;
      if (prev && prev.classList.contains('pi-tbl-tools')) prev.remove();
      box = document.createElement('div');
      box.className = 'dropdown pi-tbl-tools';
      anchor.parentNode.insertBefore(box, anchor);
    }
    box.innerHTML = `
      <button type="button" class="btn btn-sm btn-light text-nowrap pi-tbl-tools-btn" data-bs-toggle="dropdown" data-bs-auto-close="outside" data-bs-popper-config='{"strategy":"fixed"}' 
        aria-expanded="false" title="열 순서·너비 바꾸기"><i class="fas fa-table-columns me-1"></i>열 설정</button>
      <div class="dropdown-menu dropdown-menu-end p-2 pi-colmenu" role="dialog" aria-label="열 순서·너비 설정"></div>`;
    const btn = box.querySelector('button');
    const menu = box.querySelector('.pi-colmenu');
    const render = () => {
      const hs = heads(t).filter((th) => !isFixed(th));
      menu.innerHTML = `
        <div class="pi-colmenu-head"><span>열 순서·너비 <small class="text-muted">(이 브라우저에 저장)</small></span>
          <button type="button" class="btn btn-sm btn-outline-danger pi-colmenu-reset"><i class="fas fa-rotate-left me-1"></i>초기화</button></div>
        <ol class="pi-colmenu-list">${hs.map((th, i) => `
          <li data-k="${esc(th.dataset.col)}">
            <span class="pi-colmenu-name" title="${esc(th.dataset.label)}">${esc(th.dataset.label)}</span>
            <span class="pi-colmenu-ctrl">
              <button type="button" class="btn btn-sm btn-outline-secondary" data-mv="-1" ${i === 0 ? 'disabled' : ''} aria-label="${esc(th.dataset.label)} 왼쪽으로">◀</button>
              <button type="button" class="btn btn-sm btn-outline-secondary" data-mv="1" ${i === hs.length - 1 ? 'disabled' : ''} aria-label="${esc(th.dataset.label)} 오른쪽으로">▶</button>
              <input type="number" class="form-control form-control-sm" min="${minOf(st, th.dataset.col)}" max="${MAX_W}" step="10"
                value="${currentWidth(t, th.dataset.col)}" aria-label="${esc(th.dataset.label)} 너비(px)" title="너비(px)">
            </span>
          </li>`).join('')}</ol>
        <div class="pi-colmenu-foot"><small class="text-muted">열 제목을 끌어 옮기고, 오른쪽 경계를 끌어 너비를 바꿀 수도 있습니다.</small></div>`;
      menu.querySelectorAll('button[data-mv]').forEach((b) => b.addEventListener('click', () => {
        const k = b.closest('li').dataset.k;
        moveBy(t, k, Number(b.dataset.mv));
        render();
        menu.querySelector(`li[data-k="${CSS.escape(k)}"] button[data-mv="${b.dataset.mv}"]:not(:disabled)`)?.focus();
      }));
      menu.querySelectorAll('input[type=number]').forEach((inp) => inp.addEventListener('change', () => {
        const k = inp.closest('li').dataset.k;
        inp.value = setWidth(t, k, Number(inp.value) || minOf(st, k), true);
      }));
      menu.querySelector('.pi-colmenu-reset').addEventListener('click', () => {
        reset(t);
        render();
        if (notifyFn) notifyFn('열 순서와 너비를 기본값으로 되돌렸습니다.');
      });
    };
    btn.addEventListener('click', render);   // 열 때마다 현재 상태로
    btn.addEventListener('shown.bs.dropdown', () => {
      const top = menu.getBoundingClientRect().top;
      menu.style.maxHeight = Math.max(220, window.innerHeight - top - 12) + 'px';
    });
  }

  // ───────── 공개 함수 ─────────
  function enhance(t, opts = {}) {
    if (!t || !t.tHead || !opts.id) return null;
    const st = { id: opts.id, defs: opts.cols || null, tools: opts.tools, legacyWidthKey: opts.legacyWidthKey || null, widths: {} };
    t._pi = st;
    const used = new Set();
    heads(t).forEach((th, i) => {
      th.dataset.ci = String(i);
      const text = (th.dataset.label || th.textContent || '').replace(/\s+/g, ' ').trim();
      let key = th.dataset.col || text || '_' + i;
      while (used.has(key)) key += '_' + i;
      used.add(key);
      th.dataset.col = key;
      if (!th.dataset.label) th.dataset.label = text;
      const d = defOf(st, key);
      if (!text || th.querySelector('input') || (d && d.fixed) || th.classList.contains('pi-col-sticky-end')) th.dataset.fixed = '1';
    });
    t.classList.add('pi-tbl-custom');
    st.widths = savedWidths(st);
    applyOrder(t);
    applyWidths(t);
    heads(t).forEach((th) => bindHeader(t, th));
    buildTools(t, st);
    return t;
  }

  function refresh(t) {
    if (t && t._pi) applyOrder(t);
  }

  function reset(t) {
    if (!t || !t._pi) return;
    const st = t._pi;
    localStorage.removeItem(storeKey(st.id, 'order'));
    localStorage.removeItem(storeKey(st.id, 'widths'));
    if (st.legacyWidthKey) localStorage.removeItem(st.legacyWidthKey);
    st.widths = {};
    applyOrder(t);
    applyWidths(t);
  }

  return { enhance, refresh, reset, setNotify: (fn) => { notifyFn = fn; } };
})();
