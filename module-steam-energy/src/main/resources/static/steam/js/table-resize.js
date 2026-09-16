/* 표 행/열 크기 조절 - 전역 적용.
   .data-table / 그 외 일반 <table> 의 column / row 크기를 핸들 드래그로 조절.

   설계:
   - 표 grid 시뮬레이션 (colspan/rowspan 고려) — 각 cell 의 정확한 시작 column index 계산.
   - 컬럼 핸들 부착 위치:
     * thead 가 있으면 마지막 thead row 의 colspan=1 cell 들 (그룹 헤더 제외)
     * thead 가 없으면 첫 tbody row 의 cells
   - 행 핸들: tbody 의 각 row 의 첫 cell 아래에.
   - resize 시 다른 column/row 는 inline width/height 으로 lock 하여 영향 차단.
*/
(function () {
  if (typeof document === "undefined") return;

  const MIN_COL_WIDTH = 24;
  const MIN_ROW_HEIGHT = 18;
  const COL_HANDLE_CLASS = "col-resize-handle";
  const ROW_HANDLE_CLASS = "row-resize-handle";
  const COL_INDEX_ATTR = "data-col-start";

  function ensureRelative(cell) {
    if (!cell) return;
    const position = window.getComputedStyle(cell).position;
    if (position === "static") cell.style.position = "relative";
  }

  function allRows(table) {
    const rows = [];
    if (table.tHead) rows.push(...Array.from(table.tHead.rows));
    Array.from(table.tBodies || []).forEach((tb) => rows.push(...Array.from(tb.rows)));
    if (table.tFoot) rows.push(...Array.from(table.tFoot.rows));
    return rows;
  }

  function bodyRows(table) {
    const rows = [];
    Array.from(table.tBodies || []).forEach((tb) => rows.push(...Array.from(tb.rows)));
    return rows;
  }

  /** 표 grid 시뮬레이션 — colspan/rowspan 모두 고려해서 각 cell 의 (시작col, 끝col) 계산.
   *  반환: Map<HTMLTableCellElement, { startCol, endCol }>  (모두 0-based, inclusive). */
  function buildColumnMap(table) {
    const map = new Map();
    const occupied = []; // occupied[rowIdx][colIdx] = true
    const rows = allRows(table);
    rows.forEach((row, rowIdx) => {
      if (!occupied[rowIdx]) occupied[rowIdx] = [];
      let col = 0;
      Array.from(row.cells).forEach((cell) => {
        while (occupied[rowIdx][col]) col += 1;
        const colspan = cell.colSpan || 1;
        const rowspan = cell.rowSpan || 1;
        map.set(cell, { startCol: col, endCol: col + colspan - 1 });
        for (let r = rowIdx; r < rowIdx + rowspan; r += 1) {
          if (!occupied[r]) occupied[r] = [];
          for (let c = col; c < col + colspan; c += 1) {
            occupied[r][c] = true;
          }
        }
        col += colspan;
      });
    });
    return map;
  }

  function totalColumnCount(map) {
    let max = -1;
    map.forEach((v) => { if (v.endCol > max) max = v.endCol; });
    return max + 1;
  }

  function attachColumnHandles(table) {
    const map = buildColumnMap(table);
    // 핸들 부착 대상 row 결정
    let handleRow = null;
    if (table.tHead && table.tHead.rows.length > 0) {
      handleRow = table.tHead.rows[table.tHead.rows.length - 1]; // 마지막 thead row
    } else if (table.tBodies && table.tBodies[0] && table.tBodies[0].rows[0]) {
      handleRow = table.tBodies[0].rows[0];
    }
    if (!handleRow) return;

    Array.from(handleRow.cells).forEach((cell) => {
      if (cell.colSpan && cell.colSpan > 1) return; // colspan>1 헤더는 그룹용, 핸들 부착 안 함
      if (cell.querySelector(`:scope > .${COL_HANDLE_CLASS}`)) return;
      const info = map.get(cell);
      if (!info) return;
      ensureRelative(cell);
      cell.setAttribute(COL_INDEX_ATTR, String(info.startCol));
      const handle = document.createElement("div");
      handle.className = COL_HANDLE_CLASS;
      handle.setAttribute("aria-hidden", "true");
      cell.appendChild(handle);
      handle.addEventListener("pointerdown", (event) => startColResize(event, cell, table, info.startCol));
    });
  }

  function attachRowHandles(table) {
    bodyRows(table).forEach((row) => {
      const firstCell = row.firstElementChild;
      if (!firstCell) return;
      if (firstCell.querySelector(`:scope > .${ROW_HANDLE_CLASS}`)) return;
      ensureRelative(firstCell);
      const handle = document.createElement("div");
      handle.className = ROW_HANDLE_CLASS;
      handle.setAttribute("aria-hidden", "true");
      firstCell.appendChild(handle);
      handle.addEventListener("pointerdown", (event) => startRowResize(event, row));
    });
  }

  function applyCellWidth(cell, width) {
    cell.style.setProperty("width", `${width}px`, "important");
    cell.style.setProperty("min-width", `${width}px`, "important");
    cell.style.setProperty("max-width", `${width}px`, "important");
  }

  /** 특정 column index 의 모든 cell 들 (rowspan 시작 cell 만 — 1개 row 1개 cell). */
  function cellsAtColumn(table, colIndex, map) {
    const result = [];
    map.forEach((info, cell) => {
      // colspan=1 인 cell 만 — colspan>1 은 그룹/병합 cell 이라 폭 통제 의미 없음
      const colspan = cell.colSpan || 1;
      if (colspan !== 1) return;
      if (info.startCol === colIndex) result.push(cell);
    });
    return result;
  }

  function applyColumnWidth(table, colIndex, width, map) {
    cellsAtColumn(table, colIndex, map).forEach((cell) => applyCellWidth(cell, width));
    // <colgroup><col> 의 해당 col 도 폭 변경
    const colgroup = table.querySelector(":scope > colgroup");
    if (colgroup && colgroup.children[colIndex]) {
      colgroup.children[colIndex].style.setProperty("width", `${width}px`, "important");
    }
  }

  /** 변경 column 외의 모든 cell / col / 표를 inline lock. */
  function lockOthers(table, targetCol, map) {
    // (1) 표 자체: 현재 표 폭을 inline px 로 박음.
    //     - max-content 로 강제하면 width:100% 였던 표가 콘텐츠 폭으로 즉시 축소되어 사용자에게 깜빡임.
    //     - 대신 현재 보이는 폭을 유지하고, onMove 에서 delta 만큼 표 width 도 직접 갱신.
    //     - min-width:0, max-width:none 으로 외부 제약 해제.
    const tableWidth = table.getBoundingClientRect().width;
    table.style.setProperty("table-layout", "fixed", "important");
    table.style.setProperty("width", `${tableWidth}px`, "important");
    table.style.setProperty("min-width", "0", "important");
    table.style.setProperty("max-width", "none", "important");
    // (2) colgroup col 들 (target 제외)
    const colgroup = table.querySelector(":scope > colgroup");
    if (colgroup) {
      Array.from(colgroup.children).forEach((col, idx) => {
        if (idx === targetCol) return;
        if (col.style.getPropertyValue("width")) return;
        // 해당 col 의 첫 cell width 추정
        const sample = cellsAtColumn(table, idx, map)[0];
        const w = sample ? sample.getBoundingClientRect().width : 0;
        if (w) col.style.setProperty("width", `${w}px`, "important");
      });
    }
    // (3) 모든 cell — colspan=1 인 cell 만 (그룹 cell 은 자식 합산이므로 직접 폭 lock 안 함)
    map.forEach((info, cell) => {
      if ((cell.colSpan || 1) !== 1) return;
      if (info.startCol === targetCol) return;
      if (cell.style.getPropertyValue("width")) return;
      const w = cell.getBoundingClientRect().width;
      if (w) applyCellWidth(cell, w);
    });
  }

  function startColResize(event, handleCell, table, targetCol) {
    if (event.button !== undefined && event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();
    const handle = event.currentTarget;
    const pointerId = event.pointerId;
    const startX = event.clientX;
    const startWidth = handleCell.getBoundingClientRect().width;
    const map = buildColumnMap(table);
    // lockOthers 는 첫 실제 드래그 시점에만 호출 — 단순 클릭만 했을 때 표 폭이
    // max-content 로 강제되어 줄어 보이는 부작용 방지.
    let locked = false;
    let startTableWidth = 0;
    document.body.classList.add("table-resizing-col");
    try { handle.setPointerCapture && handle.setPointerCapture(pointerId); } catch (_e) {}

    function onMove(e) {
      const delta = e.clientX - startX;
      if (delta === 0) return;
      if (!locked) {
        startTableWidth = table.getBoundingClientRect().width;
        lockOthers(table, targetCol, map);
        locked = true;
      }
      const newWidth = Math.max(MIN_COL_WIDTH, startWidth + delta);
      const actualDelta = newWidth - startWidth;
      applyColumnWidth(table, targetCol, newWidth, map);
      // 표 width 도 delta 만큼 직접 갱신 (column 변경량만큼 표가 늘거나 줄어듦)
      table.style.setProperty("width", `${startTableWidth + actualDelta}px`, "important");
    }
    function onUp() {
      document.body.classList.remove("table-resizing-col");
      handle.removeEventListener("pointermove", onMove);
      handle.removeEventListener("pointerup", onUp);
      handle.removeEventListener("pointercancel", onUp);
      try { handle.releasePointerCapture && handle.releasePointerCapture(pointerId); } catch (_e) {}
    }
    handle.addEventListener("pointermove", onMove);
    handle.addEventListener("pointerup", onUp);
    handle.addEventListener("pointercancel", onUp);
  }

  function lockSiblingRows(row, table) {
    allRows(table).forEach((other) => {
      if (other === row) return;
      const h = other.getBoundingClientRect().height;
      if (!h) return;
      if (!other.style.getPropertyValue("height")) {
        other.style.setProperty("height", `${h}px`, "important");
      }
      Array.from(other.cells).forEach((cell) => {
        if (cell.style.getPropertyValue("height")) return;
        const ch = cell.getBoundingClientRect().height;
        if (ch) cell.style.setProperty("height", `${ch}px`, "important");
      });
    });
  }

  function applyRowHeight(row, height) {
    row.style.setProperty("height", `${height}px`, "important");
    Array.from(row.cells).forEach((cell) => {
      cell.style.setProperty("height", `${height}px`, "important");
    });
    row.querySelectorAll("input, select, textarea").forEach((field) => {
      field.style.setProperty("min-height", "0", "important");
      field.style.setProperty("height", "100%", "important");
    });
  }

  function startRowResize(event, row) {
    if (event.button !== undefined && event.button !== 0) return;
    event.preventDefault();
    event.stopPropagation();
    const handle = event.currentTarget;
    const pointerId = event.pointerId;
    const startY = event.clientY;
    const startHeight = row.getBoundingClientRect().height;
    const table = row.closest("table");
    let locked = false;
    document.body.classList.add("table-resizing-row");
    try { handle.setPointerCapture && handle.setPointerCapture(pointerId); } catch (_e) {}

    function onMove(e) {
      const delta = e.clientY - startY;
      if (delta === 0) return;
      if (!locked && table) { lockSiblingRows(row, table); locked = true; }
      const newHeight = Math.max(MIN_ROW_HEIGHT, startHeight + delta);
      applyRowHeight(row, newHeight);
    }
    function onUp() {
      document.body.classList.remove("table-resizing-row");
      handle.removeEventListener("pointermove", onMove);
      handle.removeEventListener("pointerup", onUp);
      handle.removeEventListener("pointercancel", onUp);
      try { handle.releasePointerCapture && handle.releasePointerCapture(pointerId); } catch (_e) {}
    }
    handle.addEventListener("pointermove", onMove);
    handle.addEventListener("pointerup", onUp);
    handle.addEventListener("pointercancel", onUp);
  }

  function attachAll() {
    document.querySelectorAll("table:not([data-no-resize])").forEach((table) => {
      attachColumnHandles(table);
      attachRowHandles(table);
    });
  }

  let scheduled = false;
  function scheduleAttach() {
    if (scheduled) return;
    scheduled = true;
    requestAnimationFrame(() => {
      scheduled = false;
      attachAll();
    });
  }

  function init() {
    attachAll();
    const observer = new MutationObserver(scheduleAttach);
    observer.observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
