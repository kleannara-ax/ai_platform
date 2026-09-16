document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "unit-fluidized-srf-inbound.html") return;

  const CELL_TABLE = "table_cell_value";
  const SRF_TABLE = "fluidized_srf_inbound";
  const TABLES_BASE = "/tables";
  const PERIOD_KEY = "steamlog:shared-period";
  const LEGACY_PERIOD_KEY = "steamlog:fluidized-srf-inbound-period";
  const VENDORS = [
    { key: "daeil", label: "대일인터내셔널", rate: 33000 },
    { key: "greenplus", label: "그린플러스", rate: 35000 },
    { key: "daerim", label: "대림종합개발", rate: 33000 },
    { key: "kj", label: "케이제이환경", rate: 30000 },
    { key: "taechang", label: "태창", rate: 35000 },
    { key: "pyeongil", label: "평일", rate: 35000 },
    { key: "km", label: "케이엠", rate: 35000 },
    { key: "duje", label: "두제", rate: 43000 },
    { key: "sejong", label: "세종스틸", rate: 33000 },
    { key: "liena", label: "리에나", rate: 37000 },
  ];

  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  const excelUploadButton = document.getElementById("srfExcelUpload");
  const excelFileInput = document.getElementById("srfExcelFileInput");
  const tableWrap = document.getElementById("srfInboundTableWrap");
  const sidebarGroups = Array.from(document.querySelectorAll(".sidebar-group-flat"));
  let pendingTabMove = null;
  let cellRows = [];

  if (!yearSelect || !monthSelect || !submitButton || !tableWrap) return;

  function escapeHtml(value) {
    return String(value)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function parseNumber(value) {
    if (value === null || value === undefined) return null;
    if (typeof value === "number") return Number.isFinite(value) ? value : null;
    const text = String(value).replace(/,/g, "").trim();
    if (!text) return null;
    const parsed = Number(text);
    return Number.isFinite(parsed) ? parsed : null;
  }

  function formatNumber(value, digits = 0) {
    if (value === null || value === undefined || value === "") return "";
    const numeric = Number(value);
    if (!Number.isFinite(numeric)) return "";
    return new Intl.NumberFormat("ko-KR", {
      minimumFractionDigits: 0,
      maximumFractionDigits: digits,
    }).format(numeric);
  }

  function sum(values) {
    const numbers = values.filter((value) => Number.isFinite(value));
    if (!numbers.length) return null;
    return numbers.reduce((acc, value) => acc + value, 0);
  }

  function currentMonthKey() {
    return `${yearSelect.value}-${monthSelect.value}`;
  }

  function daysInMonth(year, month) {
    return new Date(Number(year), Number(month), 0).getDate();
  }

  function rowKeyForEntry(dayKey) {
    return `entry:${dayKey}`;
  }

  function vendorColIndex(vendorKey) {
    return VENDORS.findIndex((vendor) => vendor.key === vendorKey) + 1;
  }

  function getCellRow(rowKey, colIndex) {
    return cellRows.find((row) =>
      row.table_name === SRF_TABLE &&
      row.month === currentMonthKey() &&
      String(row.row_key) === String(rowKey) &&
      Number(row.col_index) === Number(colIndex)
    ) || null;
  }

  function getCellValue(rowKey, colIndex) {
    return String(getCellRow(rowKey, colIndex)?.cell_value ?? "");
  }

  function setCellRow(saved) {
    const index = cellRows.findIndex((row) => row.id === saved.id);
    if (index >= 0) {
      cellRows[index] = saved;
    } else {
      cellRows.push(saved);
    }
  }

  async function fetchRows() {
    const rows = [];
    let pageNo = 1;
    let totalPages = 1;
    const limit = 1000;
    while (pageNo <= totalPages) {
      const response = await fetch(`${TABLES_BASE}/${CELL_TABLE}?page=${pageNo}&limit=${limit}&month=${encodeURIComponent(currentMonthKey())}`);
      if (!response.ok) {
        cellRows = [];
        return;
      }
      const payload = await response.json();
      const pageData = Array.isArray(payload?.data) ? payload.data : [];
      rows.push(...pageData);
      const total = Number(payload?.total || pageData.length);
      const pageLimit = Number(payload?.limit || limit);
      totalPages = Math.max(1, Math.ceil(total / pageLimit));
      pageNo += 1;
    }
    cellRows = rows.filter((row) => row.table_name === SRF_TABLE);
  }

  async function upsertCell(rowKey, colIndex, rawValue) {
    const existing = getCellRow(rowKey, colIndex);
    const payload = {
      month: currentMonthKey(),
      year_no: Number(yearSelect.value),
      month_no: Number(monthSelect.value),
      table_name: SRF_TABLE,
      row_key: rowKey,
      col_index: colIndex,
      cell_value: rawValue,
    };
    const response = await fetch(existing ? `${TABLES_BASE}/${CELL_TABLE}/${existing.id}` : `${TABLES_BASE}/${CELL_TABLE}`, {
      method: existing ? "PATCH" : "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload),
    });
    if (!response.ok) return;
    const saved = await response.json();
    if (saved?.id) setCellRow(saved);
  }

  function buildMonthState(year, month) {
    const totalDays = daysInMonth(year, month);
    const entries = {};
    for (let day = 1; day <= totalDays; day += 1) {
      const dayKey = String(day).padStart(2, "0");
      entries[dayKey] = {};
      VENDORS.forEach((vendor) => {
        entries[dayKey][vendor.key] = getCellValue(rowKeyForEntry(dayKey), vendorColIndex(vendor.key));
      });
    }

    const rates = {};
    VENDORS.forEach((vendor) => {
      rates[vendor.key] = getCellValue("rate", vendorColIndex(vendor.key));
    });

    return { rates, entries };
  }

  function loadSavedPeriod() {
    const today = new Date();
    let year = String(today.getFullYear());
    let month = String(today.getMonth() + 1).padStart(2, "0");
    try {
      const raw = localStorage.getItem(PERIOD_KEY);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (/^\d{4}$/.test(String(parsed?.year || ""))) year = String(parsed.year);
        if (/^\d{2}$/.test(String(parsed?.month || ""))) month = String(parsed.month);
      } else {
        const legacyRaw = localStorage.getItem(LEGACY_PERIOD_KEY);
        if (legacyRaw) {
          const parsed = JSON.parse(legacyRaw);
          if (/^\d{4}$/.test(String(parsed?.year || ""))) year = String(parsed.year);
          if (/^\d{2}$/.test(String(parsed?.month || ""))) month = String(parsed.month);
        }
      }
    } catch (_) {}
    return { year, month };
  }

  function persistPeriod() {
    localStorage.setItem(PERIOD_KEY, JSON.stringify({
      year: yearSelect.value,
      month: monthSelect.value,
    }));
  }

  function populatePeriodSelects() {
    const saved = loadSavedPeriod();
    const currentYear = new Date().getFullYear();
    yearSelect.innerHTML = "";
    monthSelect.innerHTML = "";

    for (let value = currentYear + 1; value >= currentYear - 5; value -= 1) {
      const option = document.createElement("option");
      option.value = String(value);
      option.textContent = String(value);
      option.selected = option.value === saved.year;
      yearSelect.appendChild(option);
    }

    for (let value = 1; value <= 12; value += 1) {
      const option = document.createElement("option");
      option.value = String(value).padStart(2, "0");
      option.textContent = `${value}월`;
      option.selected = option.value === saved.month;
      monthSelect.appendChild(option);
    }
  }

  function bindSidebar() {
    if (window.CommonSidebar?.isManaged?.()) return;
    sidebarGroups.forEach((group) => {
      const toggle = group.querySelector(".sidebar-parent-toggle");
      const state = group.dataset.sidebarState || toggle?.dataset.sidebarState;
      if (!toggle || !state) return;
      const key = `steamlog:sidebar:${state}`;
      const saved = localStorage.getItem(key);
      const isOpen = saved === "1" || group.classList.contains("is-open");
      group.classList.toggle("is-open", isOpen);
      toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
      toggle.addEventListener("click", () => {
        const nextOpen = group.classList.toggle("is-open");
        toggle.setAttribute("aria-expanded", nextOpen ? "true" : "false");
        localStorage.setItem(key, nextOpen ? "1" : "0");
      });
    });
  }

  function inputHtml(kind, keyA, keyB, value) {
    const rawValue = parseNumber(value);
    return `
      <input
        class="srf-input"
        type="text"
        inputmode="numeric"
        data-kind="${kind}"
        data-key-a="${keyA}"
        data-key-b="${keyB}"
        data-raw-value="${escapeHtml(rawValue === null ? "" : String(rawValue))}"
        value="${escapeHtml(formatNumber(parseNumber(value), 0))}"
      />
    `;
  }

  function calcHtml(value, digits = 0) {
    return `<span class="srf-calc">${escapeHtml(formatNumber(value, digits))}</span>`;
  }

  function buildFocusSignature(target) {
    if (!(target instanceof HTMLInputElement || target instanceof HTMLButtonElement || target instanceof HTMLSelectElement)) return null;
    return {
      tag: target.tagName,
      kind: target.dataset.kind || "",
      keyA: target.dataset.keyA || "",
      keyB: target.dataset.keyB || "",
      id: target.id || "",
    };
  }

  function sameFocusSignature(target, signature) {
    if (!signature || !(target instanceof HTMLElement)) return false;
    return target.tagName === signature.tag
      && (target.dataset.kind || "") === signature.kind
      && (target.dataset.keyA || "") === signature.keyA
      && (target.dataset.keyB || "") === signature.keyB
      && (target.id || "") === signature.id;
  }

  function getTabbables() {
    return Array.from(document.querySelectorAll("input:not([disabled]):not([tabindex='-1']), select:not([disabled]):not([tabindex='-1']), button:not([disabled]):not([tabindex='-1'])"))
      .filter((element) => element instanceof HTMLElement && element.offsetParent !== null);
  }

  function restorePendingTabMove() {
    if (!pendingTabMove) return;
    const move = pendingTabMove;
    pendingTabMove = null;
    requestAnimationFrame(() => {
      const tabbables = getTabbables();
      const currentIndex = tabbables.findIndex((element) => sameFocusSignature(element, move.signature));
      if (currentIndex < 0) return;
      const nextIndex = currentIndex + (move.forward ? 1 : -1);
      const nextTarget = tabbables[nextIndex];
      if (nextTarget instanceof HTMLElement) {
        nextTarget.focus();
        if (nextTarget instanceof HTMLInputElement) {
          nextTarget.select();
        }
      }
    });
  }

  function renderTable() {
    const year = yearSelect.value;
    const month = monthSelect.value;
    const state = buildMonthState(year, month);
    const totalDays = daysInMonth(year, month);
    let cumulative = 0;
    const totalsByVendor = {};
    VENDORS.forEach((vendor) => {
      totalsByVendor[vendor.key] = 0;
    });

    const bodyRows = [];
    for (let day = 1; day <= totalDays; day += 1) {
      const dayKey = String(day).padStart(2, "0");
      const date = new Date(Number(year), Number(month) - 1, day);
      const dailyValues = VENDORS.map((vendor) => {
        const value = parseNumber(state.entries[dayKey]?.[vendor.key]);
        if (Number.isFinite(value)) {
          totalsByVendor[vendor.key] += value;
        }
        return value;
      });
      const dayTotal = sum(dailyValues) ?? 0;
      cumulative += dayTotal;
      bodyRows.push(`
        <tr${day === 1 ? ' class="is-selected"' : ""}>
          ${day === 1 ? `<th class="sticky-col product-col" rowspan="${totalDays + 3}" scope="rowgroup">SRF<br />(비성형)</th>` : ""}
          <th class="sticky-col-2 date-col" scope="row">${escapeHtml(`${date.getMonth() + 1}/${date.getDate()}`)}</th>
          ${VENDORS.map((vendor) => `
            <td>${inputHtml("entry", dayKey, vendor.key, state.entries[dayKey]?.[vendor.key] ?? "")}</td>
          `).join("")}
          <td>${calcHtml(dayTotal)}</td>
          <td>${calcHtml(cumulative)}</td>
        </tr>
      `);
    }

    const totalQty = sum(Object.values(totalsByVendor)) ?? 0;
    const rates = VENDORS.map((vendor) => parseNumber(state.rates[vendor.key]));
    const costs = VENDORS.map((vendor, index) => {
      const qty = totalsByVendor[vendor.key];
      const rate = rates[index];
      return Number.isFinite(qty) && Number.isFinite(rate) ? (qty * rate) / 1000 : null;
    });
    const totalCost = sum(costs) ?? 0;
    const avgRate = totalQty > 0 ? (totalCost / totalQty) * 1000 : null;

    const summaryRow = `
      <tr class="summary-row">
        <th class="sticky-col-2 date-col" scope="row">합계</th>
        ${VENDORS.map((vendor) => `<td>${calcHtml(totalsByVendor[vendor.key])}</td>`).join("")}
        <td>${calcHtml(totalQty)}</td>
        <td></td>
      </tr>
    `;

    const rateRow = `
      <tr class="rate-row">
        <th class="sticky-col-2 date-col" scope="row">처리단가</th>
        ${VENDORS.map((vendor) => `<td>${inputHtml("rate", vendor.key, "", state.rates[vendor.key] ?? "")}</td>`).join("")}
        <td>${calcHtml(avgRate)}</td>
        <td></td>
      </tr>
    `;

    const costRow = `
      <tr class="cost-row">
        <th class="sticky-col-2 date-col" scope="row">처리비용</th>
        ${costs.map((cost) => `<td>${calcHtml(cost)}</td>`).join("")}
        <td>${calcHtml(totalCost)}</td>
        <td></td>
      </tr>
    `;

    tableWrap.innerHTML = `
      <table class="srf-workbook-table">
        <thead>
          <tr>
            <th class="sticky-col product-col">품명</th>
            <th class="sticky-col-2 date-col">구분</th>
            ${VENDORS.map((vendor) => `<th>${escapeHtml(vendor.label)}</th>`).join("")}
            <th>합계</th>
            <th>누계</th>
          </tr>
        </thead>
        <tbody>
          ${bodyRows.join("")}
          ${summaryRow}
          ${rateRow}
          ${costRow}
        </tbody>
      </table>
    `;
  }

  async function reloadAndRender() {
    await fetchRows();
    renderTable();
  }

  async function boot() {
    populatePeriodSelects();
    bindSidebar();
    await reloadAndRender();
  }

  yearSelect.addEventListener("change", () => {
    submitButton.textContent = "조회";
  });
  monthSelect.addEventListener("change", () => {
    submitButton.textContent = "조회";
  });
  submitButton.addEventListener("click", async () => {
    persistPeriod();
    await reloadAndRender();
  });
  yearSelect.addEventListener("change", persistPeriod);
  monthSelect.addEventListener("change", persistPeriod);

  async function importExcel(file) {
    if (!file) return;
    const originalLabel = excelUploadButton?.textContent || "업로드";
    if (excelUploadButton) {
      excelUploadButton.disabled = true;
      excelUploadButton.textContent = "업로드 중";
    }
    try {
      const formData = new FormData();
      formData.append("file", file);
      formData.append("year", yearSelect.value);
      formData.append("month", monthSelect.value);
      const response = await fetch("/tables/fluidized-summary/import-excel", {
        method: "POST",
        body: formData,
      });
      if (!response.ok) throw new Error("엑셀 업로드에 실패했습니다.");
      await reloadAndRender();
      alert("엑셀 업로드가 완료되었습니다.");
    } finally {
      if (excelUploadButton) {
        excelUploadButton.disabled = false;
        excelUploadButton.textContent = originalLabel;
      }
      if (excelFileInput) excelFileInput.value = "";
    }
  }

  excelUploadButton?.addEventListener("click", () => {
    excelFileInput?.click();
  });

  excelFileInput?.addEventListener("change", async (event) => {
    const file = event.target.files?.[0];
    try {
      await importExcel(file);
    } catch (error) {
      alert(error instanceof Error ? error.message : "엑셀 업로드에 실패했습니다.");
    }
  });

  tableWrap.addEventListener("change", async (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    const parsed = parseNumber(target.value);
    const rawValue = parsed === null ? "" : String(parsed);
    target.dataset.rawValue = rawValue;

    if (target.dataset.kind === "entry" && target.dataset.keyA && target.dataset.keyB) {
      await upsertCell(rowKeyForEntry(target.dataset.keyA), vendorColIndex(target.dataset.keyB), rawValue);
    } else if (target.dataset.kind === "rate" && target.dataset.keyA) {
      await upsertCell("rate", vendorColIndex(target.dataset.keyA), rawValue);
    } else {
      return;
    }

    target.value = parsed === null ? "" : formatNumber(parsed);
    renderTable();
    restorePendingTabMove();
  });

  tableWrap.addEventListener("focusin", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    target.value = target.dataset.rawValue ?? target.value.replace(/,/g, "").trim();
  });

  tableWrap.addEventListener("focusout", (event) => {
    const target = event.target;
    if (!(target instanceof HTMLInputElement)) return;
    const parsed = parseNumber(target.value);
    target.dataset.rawValue = parsed === null ? "" : String(parsed);
    target.value = parsed === null ? "" : formatNumber(parsed);
  });

  tableWrap.addEventListener("keydown", (event) => {
    const target = event.target;
    if (event.key !== "Tab" || !(target instanceof HTMLElement)) return;
    const signature = buildFocusSignature(target);
    if (!signature) return;
    pendingTabMove = {
      forward: !event.shiftKey,
      signature,
    };
  });

  boot();
});
