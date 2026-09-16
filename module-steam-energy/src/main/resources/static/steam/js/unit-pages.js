window.UnitPageSaveCoordinator = window.UnitPageSaveCoordinator || (() => {
  const flushers = new Set();
  let activeFlush = Promise.resolve();

  return {
    registerFlush(flushFn) {
      if (typeof flushFn !== "function") return () => {};
      flushers.add(flushFn);
      return () => flushers.delete(flushFn);
    },
    async flushAll() {
      activeFlush = activeFlush.then(async () => {
        for (const flushFn of flushers) {
          await flushFn();
        }
      });
      return activeFlush;
    },
  };
})();

document.addEventListener("DOMContentLoaded", () => {
  const params = new URLSearchParams(window.location.search);
  const sidebarGroups = Array.from(document.querySelectorAll(".sidebar-group-flat"));
  const yearSelect = document.getElementById("filterYear");
  const monthSelect = document.getElementById("filterMonth");
  const submitButton = document.getElementById("filterSubmit");
  const sidebarCollapseToggle = document.querySelector("[data-sidebar-collapse-toggle]");
  const machineButtons = Array.from(document.querySelectorAll("[data-machine-target]"));
  const machinePanels = Array.from(document.querySelectorAll("[data-machine-panel]"));
  const yearlyMachineButtons = Array.from(document.querySelectorAll("[data-yearly-machine-target]"));
  const yearlyMachinePanels = Array.from(document.querySelectorAll("[data-yearly-machine-panel]"));
  const periodButtons = Array.from(document.querySelectorAll(".period-tab-button[data-period-target]"));
  const periodPanels = Array.from(document.querySelectorAll("[data-period-panel]"));
  const chartToggleButtons = Array.from(document.querySelectorAll("[data-chart-toggle]"));
  const chartPanels = Array.from(document.querySelectorAll("[data-chart-panel]"));
  const chartPeriodPanels = Array.from(document.querySelectorAll("[data-chart-period-panel]"));
  const today = new Date();
  const periodStorageKey = "steamlog:shared-period";
  const sidebarStateKeyPrefix = "steamlog:sidebar:";
  const sidebarCollapsedKey = "steamlog:sidebar:collapsed";

  function resolveSidebarStateKey(sidebarGroup, sidebarToggle) {
    const explicit = sidebarGroup?.dataset.sidebarState || sidebarToggle?.dataset.sidebarState;
    if (explicit) return `${sidebarStateKeyPrefix}${explicit}`;
    const label = sidebarToggle?.textContent?.trim().replace(/\s+/g, "-").toLowerCase() || "default";
    return `${sidebarStateKeyPrefix}${label}`;
  }

  if (params.get("embedded") === "1") {
    document.body.classList.add("embedded-mode");
  }

  const commonSidebarManaged = Boolean(window.CommonSidebar?.isManaged?.());

  if (!commonSidebarManaged && localStorage.getItem(sidebarCollapsedKey) === "1") {
    document.body.classList.add("sidebar-collapsed");
  }

  if (!commonSidebarManaged && sidebarCollapseToggle) {
    sidebarCollapseToggle.addEventListener("click", () => {
      const collapsed = document.body.classList.toggle("sidebar-collapsed");
      localStorage.setItem(sidebarCollapsedKey, collapsed ? "1" : "0");
      sidebarCollapseToggle.setAttribute("aria-pressed", collapsed ? "true" : "false");
    });
    sidebarCollapseToggle.setAttribute(
      "aria-pressed",
      document.body.classList.contains("sidebar-collapsed") ? "true" : "false",
    );
  }

  if (!commonSidebarManaged) {
    sidebarGroups.forEach((group) => {
      const toggle = group.querySelector(".sidebar-parent-toggle");
      if (!toggle) return;
      const key = resolveSidebarStateKey(group, toggle);
      const isOpen = localStorage.getItem(key) === "1" || group.classList.contains("is-open") || toggle.classList.contains("active");
      group.classList.toggle("is-open", isOpen);
      toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
      toggle.addEventListener("click", () => {
        const nextOpen = group.classList.toggle("is-open");
        toggle.setAttribute("aria-expanded", nextOpen ? "true" : "false");
        localStorage.setItem(key, nextOpen ? "1" : "0");
      });
    });
  }

  if (yearSelect && monthSelect && submitButton) {
    let sharedYear = String(today.getFullYear());
    let sharedMonth = String(today.getMonth() + 1).padStart(2, "0");
    try {
      const raw = localStorage.getItem(periodStorageKey);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (/^\d{4}$/.test(String(parsed?.year || ""))) sharedYear = String(parsed.year);
        if (/^(0[1-9]|1[0-2])$/.test(String(parsed?.month || ""))) sharedMonth = String(parsed.month);
      }
    } catch (_) {}

    const currentYear = today.getFullYear();
    for (let year = currentYear + 1; year >= currentYear - 5; year -= 1) {
      const option = document.createElement("option");
      option.value = String(year);
      option.textContent = String(year);
      if (String(year) === sharedYear) option.selected = true;
      yearSelect.appendChild(option);
    }

    for (let month = 1; month <= 12; month += 1) {
      const option = document.createElement("option");
      const value = String(month).padStart(2, "0");
      option.value = value;
      option.textContent = value;
      if (value === sharedMonth) option.selected = true;
      monthSelect.appendChild(option);
    }

    const persistPeriod = () => {
      localStorage.setItem(periodStorageKey, JSON.stringify({
        year: yearSelect.value,
        month: monthSelect.value,
      }));
    };

    const syncLabel = () => {
      submitButton.textContent = `${yearSelect.value}.${monthSelect.value}`;
    };

    yearSelect.addEventListener("change", () => {
      persistPeriod();
      syncLabel();
    });
    monthSelect.addEventListener("change", () => {
      persistPeriod();
      syncLabel();
    });
    submitButton.addEventListener("click", persistPeriod);
    syncLabel();
  }

  if (machineButtons.length && machinePanels.length) {
    const activatePanel = (target) => {
      machineButtons.forEach((button) => {
        const active = button.dataset.machineTarget === target;
        button.classList.toggle("active", active);
        button.setAttribute("aria-selected", active ? "true" : "false");
      });

      machinePanels.forEach((panel) => {
        if (panel.classList.contains("machine-panel-static")) {
          panel.classList.add("active");
          panel.style.display = "";
          return;
        }
        const active = panel.dataset.machinePanel === target;
        panel.classList.toggle("active", active);
        panel.style.display = active ? "" : "none";
      });
    };

    machineButtons.forEach((button) => {
      button.addEventListener("click", async () => {
        await window.UnitPageSaveCoordinator.flushAll();
        activatePanel(button.dataset.machineTarget);
      });
    });

    activatePanel(machineButtons[0].dataset.machineTarget);
  }

  if (periodButtons.length && periodPanels.length) {
    let activePeriod = periodButtons[0].dataset.periodTarget;
    let chartVisible = false;

    const syncChartPeriodPanels = () => {
      chartPeriodPanels.forEach((panel) => {
        const active = panel.dataset.chartPeriodPanel === activePeriod;
        panel.classList.toggle("active", active);
        panel.style.display = active ? "" : "none";
      });
    };

    const syncPeriodVisibility = () => {
      periodPanels.forEach((panel) => {
        const active = panel.dataset.periodPanel === activePeriod;
        panel.classList.toggle("active", active && !chartVisible);
        panel.style.display = active && !chartVisible ? "" : "none";
      });
    };

    const activatePeriod = (target) => {
      activePeriod = target;
      periodButtons.forEach((button) => {
        const active = button.dataset.periodTarget === target;
        button.classList.toggle("active", active);
        button.setAttribute("aria-selected", active ? "true" : "false");
      });

      syncPeriodVisibility();
      syncChartPeriodPanels();
    };

    const setChartVisible = (visible) => {
      chartVisible = visible;
      chartToggleButtons.forEach((button) => {
        button.classList.toggle("active", chartVisible);
        button.setAttribute("aria-pressed", chartVisible ? "true" : "false");
        button.setAttribute("aria-selected", chartVisible ? "true" : "false");
      });
      chartPanels.forEach((panel) => {
        panel.classList.toggle("active", chartVisible);
        panel.style.display = chartVisible ? "" : "none";
      });
      syncPeriodVisibility();
      if (chartVisible) syncChartPeriodPanels();
    };

    periodButtons.forEach((button) => {
      button.addEventListener("click", async () => {
        await window.UnitPageSaveCoordinator.flushAll();
        activatePeriod(button.dataset.periodTarget);
      });
    });

    chartToggleButtons.forEach((button) => {
      button.addEventListener("click", async () => {
        await window.UnitPageSaveCoordinator.flushAll();
        setChartVisible(!chartVisible);
      });
    });

    activatePeriod(activePeriod);
    setChartVisible(false);
  }

  if (yearlyMachineButtons.length && yearlyMachinePanels.length) {
    const activateYearlyPanel = (target) => {
      yearlyMachineButtons.forEach((button) => {
        const active = button.dataset.yearlyMachineTarget === target;
        button.classList.toggle("active", active);
        button.setAttribute("aria-selected", active ? "true" : "false");
      });

      yearlyMachinePanels.forEach((panel) => {
        const active = panel.dataset.yearlyMachinePanel === target;
        panel.classList.toggle("active", active);
        panel.style.display = active ? "" : "none";
      });
    };

    yearlyMachineButtons.forEach((button) => {
      button.addEventListener("click", async () => {
        await window.UnitPageSaveCoordinator.flushAll();
        activateYearlyPanel(button.dataset.yearlyMachineTarget);
      });
    });

    activateYearlyPanel(yearlyMachineButtons[0].dataset.yearlyMachineTarget);
  }

  applyUnitHeaderUnits();
  bindUnitExcelControls();

  // 2행(그룹) 헤더 표: 스크롤 고정(sticky) 시 2번째(세부) 행이 1번째(그룹) 행 위로
  // 겹치지 않도록, 세부 행의 top 을 그룹 행 높이만큼 내려 고정한다.
  // CSS 가 모든 thead th 에 top:0 !important 를 주므로 인라인 !important 로 덮어쓴다.
  // 숨겨진 패널은 높이가 0이라 건너뛰고, 표시될 때(탭 전환) 다시 동기화한다.
  function syncStickyHeaderOffsets() {
    document.querySelectorAll(".data-table").forEach((table) => {
      const rows = table.querySelectorAll("thead tr");
      if (rows.length < 2) return;
      const h = Math.round(rows[0].getBoundingClientRect().height);
      if (h <= 0) return;
      rows[1].querySelectorAll("th").forEach((th) => {
        th.style.setProperty("top", h + "px", "important");
      });
    });
  }
  let stickySyncQueued = false;
  function scheduleStickySync() {
    if (stickySyncQueued) return;
    stickySyncQueued = true;
    // 탭 전환은 비동기 렌더라 패널이 표시된 뒤 측정되도록 약간 지연.
    setTimeout(() => { stickySyncQueued = false; syncStickyHeaderOffsets(); }, 80);
  }
  scheduleStickySync();
  setTimeout(syncStickyHeaderOffsets, 400);
  window.addEventListener("resize", scheduleStickySync);
  // 스크롤 시(capture: 어떤 컨테이너 스크롤이든 포착) 직접 동기화 — sticky 가 실제로
  // 필요한 순간이며 대상 패널이 반드시 표시 상태라 높이 측정이 정확하다. 동일 값이면 무동작.
  document.addEventListener("scroll", syncStickyHeaderOffsets, true);
  document.addEventListener("click", (event) => {
    if (event.target.closest(".machine-tab-button, .period-tab-button, .yearly-machine-tab-button, .landing-filter-action")) {
      scheduleStickySync();
    }
  });

  function setHeaderTexts(selector, headers) {
    const table = document.querySelector(selector);
    if (!table) return;
    table.querySelectorAll("thead th").forEach((th, index) => {
      if (headers[index]) th.textContent = headers[index];
    });
  }

  function setGroupedHeaders(selector, firstHeader, groups) {
    const table = document.querySelector(selector);
    const thead = table?.querySelector("thead");
    if (!thead) return;
    const top = [`<th rowspan="2">${firstHeader}</th>`];
    const bottom = [];
    groups.forEach((group) => {
      top.push(`<th colspan="${group.children.length}">${group.label}</th>`);
      group.children.forEach((child) => bottom.push(`<th>${child}</th>`));
    });
    thead.innerHTML = `<tr>${top.join("")}</tr><tr>${bottom.join("")}</tr>`;
  }

  function applyUnitHeaderUnits() {
    const page = window.location.pathname.split("/").pop();
    const ton = "톤";
    const kg = "kg";
    const wonPerTon = "원/톤";
    const millionWon = "백만원";
    const unitTonTon = "톤/톤";

    if (page === "unit-steam-unit.html") {
      setHeaderTexts('[data-unit-monthly="pm2"]', ["일자", `Main 스팀(${ton})`, `Coater 스팀(${ton})`, `스팀량 합계(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`, "생산지종"]);
      setHeaderTexts('[data-unit-monthly="pm3"]', ["일자", `Main 스팀(${ton})`, `Coater 스팀(${ton})`, `디스퍼져 스팀량(${ton})`, `환기시스템 스팀량(${ton})`, `스팀량 합계(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`, "생산지종"]);
      setHeaderTexts('[data-unit-monthly="tm"]', ["일자", `TM-3 스팀(${ton})`, `TM-3 생산량(${kg})`, `TM-3 원단위(${unitTonTon})`, `TM-4 스팀(${ton})`, `TM-4 생산량(${kg})`, `TM-4 원단위(${unitTonTon})`, `TM-5 스팀(${ton})`, `TM-5 생산량(${kg})`, `TM-5 원단위(${unitTonTon})`]);
      setGroupedHeaders('[data-unit-monthly="tm"]', "일자", [
        { label: "TM-3", children: [`스팀량(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`] },
        { label: "TM-4", children: [`스팀량(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`] },
        { label: "TM-5", children: [`스팀량(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`] },
      ]);
      setHeaderTexts('[data-machine-panel="paper-total"] .unit-table', ["일자", `제지 스팀량(${ton})`, `제지 생산량(${kg})`, `제지 원단위(${unitTonTon})`]);
      setHeaderTexts('[data-machine-panel="tissue-total"] .unit-table', ["일자", `화장지 스팀량(${ton})`, `화장지 생산량(${kg})`, `화장지 원단위(${unitTonTon})`]);
      setHeaderTexts('[data-machine-panel="other-total"] .unit-table', ["일자", `총 스팀량(${ton})`, `총 생산량(${kg})`, `총 원단위(${unitTonTon})`, `디스퍼져 총량(FI 122)`, `TOC 스팀량(환경관리)`, `PICA-121 Vent(${ton})`]);
      setHeaderTexts('[data-yearly-table="pm2"]', ["월", `Main 스팀량(${ton})`, `Coater 스팀량(${ton})`, `스팀량합계(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`]);
      setHeaderTexts('[data-yearly-table="pm3"]', ["월", `Main 스팀량(${ton})`, `Coater 스팀량(${ton})`, `디스퍼져 스팀량(${ton})`, `환기시스템 스팀량(${ton})`, `스팀량 합계(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`]);
      setHeaderTexts('[data-yearly-table="tm"]', ["월", `TM-3 스팀량(${ton})`, `TM-3 생산량(${kg})`, `TM-3 원단위(${unitTonTon})`, `TM-4 스팀량(${ton})`, `TM-4 생산량(${kg})`, `TM-4 원단위(${unitTonTon})`, `TM-5 스팀량(${ton})`, `TM-5 생산량(${kg})`, `TM-5 원단위(${unitTonTon})`]);
      setGroupedHeaders('[data-yearly-table="tm"]', "월", [
        { label: "TM-3", children: [`스팀량(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`] },
        { label: "TM-4", children: [`스팀량(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`] },
        { label: "TM-5", children: [`스팀량(${ton})`, `생산량(${kg})`, `원단위(${unitTonTon})`] },
      ]);
      setHeaderTexts('[data-yearly-table="paper-total"]', ["월", `스팀량(${ton})`, `생산량(${kg})`, `제지 원단위(${unitTonTon})`]);
      setHeaderTexts('[data-yearly-table="tissue-total"]', ["월", `스팀량(${ton})`, `생산량(${kg})`, `화장지 원단위(${unitTonTon})`]);
    }

    if (page === "unit-steam-usage.html") {
      setGroupedHeaders('[data-machine-panel="tm"] .runtime-table', "일", [
        { label: "TM-3", children: [`스팀량(${ton})`, "가동시간(hr)", "원단위(톤/hr)"] },
        { label: "TM-4", children: [`스팀량(${ton})`, "가동시간(hr)", "원단위(톤/hr)"] },
        { label: "TM-5", children: [`스팀량(${ton})`, "가동시간(hr)", "원단위(톤/hr)"] },
        { label: "화장지 총합", children: [`스팀량(${ton})`, "가동시간(hr)", "원단위(톤/hr)"] },
      ]);
      setGroupedHeaders('[data-yearly-machine-panel="tm"] .runtime-table', "월", [
        { label: "TM-3", children: [`스팀량(${ton})`, "가동시간(hr)", "원단위(톤/hr)"] },
        { label: "TM-4", children: [`스팀량(${ton})`, "가동시간(hr)", "원단위(톤/hr)"] },
        { label: "TM-5", children: [`스팀량(${ton})`, "가동시간(hr)", "원단위(톤/hr)"] },
        { label: "화장지 총합", children: [`스팀량(${ton})`, "가동시간(hr)", "원단위(톤/hr)"] },
      ]);
      document.querySelectorAll(".runtime-table th").forEach((th) => {
        if (Number(th.colSpan) > 1) return;
        const text = th.textContent.trim();
        if (text === "스팀량" || text === "총 스팀량" || ["TM-3", "TM-4", "TM-5", "디스퍼져", "Ventilation"].includes(text)) th.textContent = `${text}(${ton})`;
        if (text === "평균 가동시간") th.textContent = `${text}(hr)`;
      });
    }

    if (page === "unit-lng-cost.html") {
      setHeaderTexts('[data-cell-table="lng_m_boiler"]', ["일자", `LNG (20${ton}-1)`, `LNG (20${ton}-2)`, `LNG (50${ton})`, `LNG (60${ton})`, `LNG 사용합계(${ton})`, `단가(${wonPerTon})`, `비용(${millionWon})`]);
      setHeaderTexts('[data-cell-table="lng_y_boiler"]', ["월", `LNG (20${ton}-1)`, `LNG (20${ton}-2)`, `LNG (50${ton})`, `LNG (60${ton})`, `LNG 사용합계(${ton})`, `단가(${wonPerTon})`, `비용(${millionWon})`]);
      // 버너 표(lng_m_burner / lng_y_burner)는 제지·화장지 2행 그룹 헤더를 HTML에 직접 두고,
      // 단위(톤/원·톤/백만원)는 아래 .lpg-table th forEach 에서 자동 부여한다.
      document.querySelectorAll(".lpg-table th, .lpg-mix-table th").forEach((th) => {
        const text = th.textContent.trim();
        if (/^LNG|^PM|^TM|PM\+TM/.test(text) && !/[()]/.test(text)) th.textContent = `${text}(${ton})`;
        if (!/[()]/.test(text) && (text.includes("사용합계") || text.includes("합계") || text.includes("계"))) th.textContent = `${text.replace(/\(.+\)$/, "")}(${ton})`;
        if (text === "단가") th.textContent = `단가(${wonPerTon})`;
        if (text === "비용" || text === "총비용") th.textContent = `${text}(${millionWon})`;
      });
    }

    if (page === "unit-machine-summary.html") {
      const titles = document.querySelectorAll(".table-card h3");
      if (titles[0]) titles[0].textContent = "5. 호기별 스팀생산량 배분 (누계)";
      if (titles[1]) titles[1].textContent = "6. 호기별 연료비용 배분 (누계)";
      if (titles[2]) titles[2].textContent = "7. 호기별 연료 사용비용 (일별)";
      setHeaderTexts('[data-summary-table="steam-allocation"]', ["구분", "단위", "제지2", "제지3", "화장지3", "화장지4", "화장지5", "누계"]);
      setHeaderTexts('[data-summary-table="fuel-allocation"]', ["구분", "단위", "제지2", "제지3", "화장지3", "화장지4", "화장지5", "누계"]);
      setHeaderTexts('[data-summary-table="fuel-daily"]', ["일자", "제지2(백만원)", "제지3(백만원)", "화장지3(백만원)", "화장지4(백만원)", "화장지5(백만원)", "합계(백만원)"]);
    }
  }

  function bindUnitExcelControls() {
    const page = window.location.pathname.split("/").pop();
    const unitExcelPages = new Set([
      "unit-steam-unit.html",
      "unit-steam-usage.html",
      "unit-lng-cost.html",
      "unit-flow-daily.html",
      "unit-machine-summary.html",
    ]);
    if (!unitExcelPages.has(page) || !yearSelect) return;

    const header = document.querySelector(".landing-page-header");
    if (!header) return;

    let box = document.querySelector(".landing-upload-box");
    if (!box) {
      box = document.createElement("div");
      box.className = "landing-filter-box landing-upload-box";
      box.innerHTML = `
        <span class="landing-filter-title">엑셀</span>
        <button type="button" class="landing-filter-action" id="unitExcelDownloadButton">다운로드</button>
        <button type="button" class="landing-filter-action" id="unitExcelUploadButton">업로드</button>
        <input type="file" id="unitExcelFileInput" accept=".xls,.xlsx" hidden />
      `;
      header.appendChild(box);
    } else {
      const title = box.querySelector(".landing-filter-title");
      if (title) title.textContent = "엑셀";
      if (!document.getElementById("unitExcelDownloadButton")) {
        const downloadButton = document.createElement("button");
        downloadButton.type = "button";
        downloadButton.className = "landing-filter-action";
        downloadButton.id = "unitExcelDownloadButton";
        downloadButton.textContent = "다운로드";
        const uploadButton = document.getElementById("unitExcelUploadButton");
        box.insertBefore(downloadButton, uploadButton || null);
      }
      if (!document.getElementById("unitExcelUploadButton")) {
        const uploadButton = document.createElement("button");
        uploadButton.type = "button";
        uploadButton.className = "landing-filter-action";
        uploadButton.id = "unitExcelUploadButton";
        uploadButton.textContent = "업로드";
        box.appendChild(uploadButton);
      }
      if (!document.getElementById("unitExcelFileInput")) {
        const fileInput = document.createElement("input");
        fileInput.type = "file";
        fileInput.id = "unitExcelFileInput";
        fileInput.accept = ".xls,.xlsx";
        fileInput.hidden = true;
        box.appendChild(fileInput);
      }
    }

    if (page === "unit-steam-unit.html") return;

    const downloadButton = document.getElementById("unitExcelDownloadButton");
    const uploadButton = document.getElementById("unitExcelUploadButton");
    const fileInput = document.getElementById("unitExcelFileInput");
    if (!downloadButton || downloadButton.dataset.unitExcelBound === "true") return;
    downloadButton.dataset.unitExcelBound = "true";
    uploadButton.dataset.unitExcelBound = "true";

    downloadButton.addEventListener("click", async () => {
      await window.UnitPageSaveCoordinator.flushAll();
      try {
        const response = await fetch(`/tables/unit-usage/export-excel?year=${encodeURIComponent(yearSelect.value)}`);
        if (!response.ok) throw new Error("엑셀 다운로드에 실패했습니다.");
        const blob = await response.blob();
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement("a");
        anchor.href = url;
        anchor.download = `원단위 일지 누계(${yearSelect.value}년).xls`;
        document.body.appendChild(anchor);
        anchor.click();
        anchor.remove();
        URL.revokeObjectURL(url);
      } catch (error) {
        alert(error instanceof Error ? error.message : "엑셀 다운로드 중 오류가 발생했습니다.");
      }
    });

    uploadButton.addEventListener("click", async () => {
      await window.UnitPageSaveCoordinator.flushAll();
      fileInput?.click();
    });

    fileInput?.addEventListener("change", async () => {
      const file = fileInput.files?.[0];
      if (!file) return;
      const originalText = uploadButton.textContent;
      uploadButton.disabled = true;
      uploadButton.textContent = "업로드 중";
      try {
        const formData = new FormData();
        formData.append("file", file);
        formData.append("year", yearSelect.value);
        const response = await fetch("/tables/unit-usage/import-excel", { method: "POST", body: formData });
        if (!response.ok) throw new Error("엑셀 업로드에 실패했습니다.");
        window.location.reload();
      } catch (error) {
        alert(error instanceof Error ? error.message : "엑셀 업로드 중 오류가 발생했습니다.");
      } finally {
        uploadButton.disabled = false;
        uploadButton.textContent = originalText || "업로드";
        fileInput.value = "";
      }
    });
  }
});
