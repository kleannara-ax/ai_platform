document.addEventListener("DOMContentLoaded", () => {
  const page = window.location.pathname.split("/").pop();
  if (page !== "unit-fluidized-monthly.html") return;

  const yearSelect = document.getElementById("filterYear");
  const submitButton = document.getElementById("filterSubmit");
  const sectionTabs = document.getElementById("monthlySectionTabs");
  const subtabs = document.getElementById("monthlySubtabs");
  const subtabLabel = document.getElementById("monthlySubtabLabel");
  const contentRoot = document.getElementById("monthlyContentRoot");
  const pageTitle = document.getElementById("monthlyPageTitle");
  const sidebarGroups = Array.from(document.querySelectorAll(".sidebar-group-flat"));

  if (!yearSelect || !submitButton || !sectionTabs || !subtabs || !contentRoot) return;

  const periodStorageKey = "steamlog:shared-period";
  const sections = [
    {
      id: "operating",
      label: "\uC6B4\uC601\uD604\uD669",
      subtabs: [
        { id: "incinerator", label: "\uC720\uB3D9\uC0C1 \uC18C\uAC01\uB85C" },
        { id: "contract-unit", label: "\uB3C4\uAE09\uBE44 \uC6D0\uB2E8\uC704" },
        { id: "operating-unit", label: "\uC6B4\uC601\uBE44 \uC6D0\uB2E8\uC704" },
      ],
    },
    {
      id: "chemicals",
      label: "\uC57D\uD488\uC0AC\uC6A9\uB7C9",
      subtabs: [
        { id: "chem-main", label: "\uC8FC\uC694 4\uC885" },
        { id: "chem-aux", label: "\uBCF4\uC870 7\uC885 / \uD569\uACC4" },
        { id: "chem-price", label: "\uB2E8\uAC00\uD45C" },
      ],
    },
    {
      id: "utilities",
      label: "\uC720\uD2F8\uB9AC\uD2F0 \uC0AC\uC6A9\uB7C9",
      subtabs: [
        { id: "util-power", label: "SRF \uC218\uC785\uAE08 / \uC6B4\uC601\uBE44\uC6A9" },
        { id: "util-waste", label: "\uD3D0\uAE30\uBB3C \uCC98\uB9AC\uBE44" },
        { id: "util-summary", label: "\uC6B4\uC601\uBE44 / \uC6D0\uB2E8\uC704" },
      ],
    },
    {
      id: "charts",
      label: "\uCC28\uD2B8",
      subtabs: [{ id: "charts-main", label: "\uCC28\uD2B8" }],
    },
  ];

  let activeSection = "operating";
  let activeSubtab = "incinerator";

  function applyChromeText() {
    document.title = "\uC6D4\uBCC4 \uC6B4\uC601\uB0B4\uC5ED";
    if (pageTitle) pageTitle.textContent = "\uC6D4\uBCC4 \uC6B4\uC601\uB0B4\uC5ED";
    submitButton.textContent = "\uC870\uD68C";
  }

  function normalizeSidebar() {
    const fluidizedGroup = document.querySelector(".sidebar-group-flat[data-sidebar-state='fluidized-log']");
    const fluidizedToggle = fluidizedGroup?.querySelector(".sidebar-parent-toggle");
    if (!fluidizedGroup || !fluidizedToggle) return;
    fluidizedGroup.classList.add("is-open");
    fluidizedToggle.classList.add("active");
    fluidizedToggle.setAttribute("aria-expanded", "true");
  }

  function loadSavedPeriod() {
    const today = new Date();
    let year = String(today.getFullYear());
    let month = String(today.getMonth() + 1).padStart(2, "0");
    try {
      const raw = localStorage.getItem(periodStorageKey);
      if (raw) {
        const parsed = JSON.parse(raw);
        if (/^\d{4}$/.test(String(parsed?.year || ""))) year = String(parsed.year);
        if (/^(0[1-9]|1[0-2])$/.test(String(parsed?.month || ""))) month = String(parsed.month);
      }
    } catch (_) {}
    return { year, month };
  }

  function populatePeriodSelects() {
    const saved = loadSavedPeriod();
    const currentYear = new Date().getFullYear();
    yearSelect.innerHTML = "";

    for (let value = currentYear + 1; value >= currentYear - 5; value -= 1) {
      const option = document.createElement("option");
      option.value = String(value);
      option.textContent = String(value);
      option.selected = option.value === saved.year;
      yearSelect.appendChild(option);
    }
  }

  function persistPeriod() {
    const saved = loadSavedPeriod();
    localStorage.setItem(periodStorageKey, JSON.stringify({
      year: yearSelect.value,
      month: saved.month,
    }));
  }

  function activeSectionMeta() {
    return sections.find((item) => item.id === activeSection) || sections[0];
  }

  function buildViewParams() {
    return {
      year: yearSelect.value,
      section: activeSection,
      sub: activeSubtab,
    };
  }

  async function loadView() {
    persistPeriod();
    if (typeof window.renderFluidizedMonthlyView !== "function") return;
    await window.renderFluidizedMonthlyView({
      root: contentRoot,
      ...buildViewParams(),
    });
  }

  function renderSubtabs() {
    const meta = activeSectionMeta();
    subtabs.innerHTML = "";
    meta.subtabs.forEach((item) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `period-tab-button${item.id === activeSubtab ? " active" : ""}`;
      button.textContent = item.label;
      button.addEventListener("click", () => {
        activeSubtab = item.id;
        renderSubtabs();
        loadView();
      });
      subtabs.appendChild(button);
    });
    if (subtabLabel) subtabLabel.textContent = `${meta.label} \uD558\uC704 \uBA54\uB274`;
  }

  function renderSectionTabs() {
    sectionTabs.innerHTML = "";
    sections.forEach((item) => {
      const button = document.createElement("button");
      button.type = "button";
      button.className = `period-tab-button${item.id === activeSection ? " active" : ""}`;
      button.textContent = item.label;
      button.addEventListener("click", () => {
        activeSection = item.id;
        activeSubtab = item.subtabs[0].id;
        renderSectionTabs();
        renderSubtabs();
        loadView();
      });
      sectionTabs.appendChild(button);
    });
  }

  function bindSidebar() {
    if (window.CommonSidebar?.isManaged?.()) return;
    sidebarGroups.forEach((group) => {
      const toggle = group.querySelector(".sidebar-parent-toggle");
      const state = group.dataset.sidebarState || toggle?.dataset.sidebarState;
      if (!toggle || !state) return;
      const key = `steamlog:sidebar:${state}`;
      const saved = localStorage.getItem(key);
      const isOpen = saved === "1";
      group.classList.toggle("is-open", isOpen);
      toggle.setAttribute("aria-expanded", isOpen ? "true" : "false");
      toggle.addEventListener("click", () => {
        const nextOpen = group.classList.toggle("is-open");
        toggle.setAttribute("aria-expanded", nextOpen ? "true" : "false");
        localStorage.setItem(key, nextOpen ? "1" : "0");
      });
    });
  }

  applyChromeText();
  normalizeSidebar();
  populatePeriodSelects();
  bindSidebar();
  renderSectionTabs();
  renderSubtabs();
  loadView();

  yearSelect.addEventListener("change", () => {
    persistPeriod();
    submitButton.textContent = "\uC870\uD68C";
  });
  submitButton.addEventListener("click", () => {
    loadView();
  });
});
