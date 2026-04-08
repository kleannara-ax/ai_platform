(function () {
  const root = document.getElementById("adminEquipmentPage");
  if (!root) return;

  const config = {
    apiBase: root.dataset.apiBase || "",
    idField: root.dataset.idField || "id",
    title: root.dataset.title || "",
    singular: root.dataset.singular || "",
    checklist: []
  };

  const checklistEl = document.getElementById("inspectionChecklistData");
  const query = new URLSearchParams(window.location.search || "");
  if (checklistEl?.textContent) {
    try {
      config.checklist = JSON.parse(checklistEl.textContent);
    } catch (_) {
      config.checklist = [];
    }
  }

  const state = {
    items: [],
    peers: { receivers: [], pumps: [] },
    selectedCoord: null,
    selectedDetailId: null,
    itemModal: null,
    detailModal: null,
    inspectModal: null,
    currentDetail: null,
    activeStatusFilter: null,
    initialActionHandled: false
  };

  const canManage = String(getUser()?.role || "").toUpperCase() === "ADMIN";

  function getPendingAction() {
    if (query.get("add") === "1") return { type: "add", id: "" };
    if (query.get("details")) return { type: "details", id: query.get("details") };
    if (query.get("inspect")) return { type: "inspect", id: query.get("inspect") };
    if (query.get("edit")) return { type: "edit", id: query.get("edit") };
    return null;
  }

  function clearPendingAction() {
    const url = new URL(window.location.href);
    url.searchParams.delete("add");
    url.searchParams.delete("details");
    url.searchParams.delete("inspect");
    url.searchParams.delete("edit");
    url.searchParams.delete("x");
    url.searchParams.delete("y");
    url.searchParams.delete("buildingName");
    url.searchParams.delete("floorName");
    url.searchParams.delete("buildingId");
    url.searchParams.delete("floorId");
    window.history.replaceState({}, "", url.pathname + url.search);
  }

  function isEmbeddedMode() {
    return query.get("embedEdit") === "1" || query.get("embedInspect") === "1" || query.get("embedDetails") === "1";
  }

  function readQueryCoord() {
    const rawX = query.get("x");
    const rawY = query.get("y");
    if (rawX == null || rawY == null || rawX === "" || rawY === "") return null;
    const x = Number(rawX);
    const y = Number(rawY);
    return Number.isFinite(x) && Number.isFinite(y) ? { x: x, y: y } : null;
  }

  function applyEmbeddedShell() {
    if (!isEmbeddedMode()) return;
    document.body.style.background = "transparent";
    document.body.style.overflow = "hidden";
    document.body.style.padding = "0";
    document.querySelector("header")?.style.setProperty("display", "none");
    if (root) root.style.display = "none";
    const style = document.createElement("style");
    style.textContent = [
      "body{background:transparent!important;}",
      ".modal-backdrop{display:none!important;}",
      ".modal{background:transparent!important;}",
      ".modal-dialog{margin:.35rem auto!important;}",
      ".modal-content{box-shadow:0 14px 36px rgba(15,23,42,.18)!important;}"
    ].join("");
    document.head.appendChild(style);
  }

  function postEmbedClose(message) {
    if (!isEmbeddedMode()) return;
    try {
      window.parent?.postMessage(message, "*");
    } catch (_) {}
  }

  function getUser() {
    try {
      return JSON.parse(localStorage.getItem("fireweb_user") || localStorage.getItem("fw_user") || "null");
    } catch (_) {
      return null;
    }
  }

  function ensureAuthenticated() {
    const user = getUser();
    const role = String(user?.role || "").toUpperCase();
    if (role !== "ADMIN" && role !== "USER") {
      location.replace("/login.html?returnUrl=" + encodeURIComponent(location.pathname));
      return false;
    }
    return true;
  }

  function escapeHtml(value) {
    return String(value ?? "").replace(/[&<>"']/g, function (char) {
      return { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[char];
    });
  }

  function formatDateTime(date, time) {
    const dateText = date || "-";
    const timeText = time ? String(time).slice(0, 5) : "";
    return timeText ? (dateText + " " + timeText) : dateText;
  }

  function statusLabel(status) {
    switch (String(status || "").toUpperCase()) {
      case "NORMAL": return "정상";
      case "MAINTENANCE": return "요정비";
      case "FAULTY": return "불량";
      default: return "미점검";
    }
  }

  function statusClass(status) {
    switch (String(status || "").toUpperCase()) {
      case "NORMAL": return "fw-ok";
      case "MAINTENANCE": return "fw-maint";
      case "FAULTY": return "fw-bad";
      default: return "fw-wait";
    }
  }

  function statusBadge(status) {
    return '<span class="fw-status ' + statusClass(status) + '">' + statusLabel(status) + "</span>";
  }

  async function apiFetch(path, options) {
    const opts = window.FireWebCsrf?.applyOptions(options || {}) || (options || {});
    const response = await fetch(path, opts);
    if (response.status === 401) {
      location.replace("/login.html?returnUrl=" + encodeURIComponent(location.pathname));
      throw new Error("로그인이 필요합니다.");
    }
    if (response.status === 403) {
      throw new Error("관리자만 수행할 수 있습니다.");
    }
    const json = await response.json().catch(() => null);
    if (!response.ok || !json?.ok) {
      throw new Error(json?.message || "요청 처리에 실패했습니다.");
    }
    return json.data;
  }

  async function loadPeers() {
    const [receivers, pumps] = await Promise.all([
      apiFetch("/fire-api/receivers?size=500&page=0", { method: "GET" }),
      apiFetch("/fire-api/pumps?size=500&page=0", { method: "GET" })
    ]);
    state.peers.receivers = Array.isArray(receivers?.content) ? receivers.content : [];
    state.peers.pumps = Array.isArray(pumps?.content) ? pumps.content : [];
  }

  async function loadList() {
    const q = document.getElementById("searchInput")?.value?.trim() || "";
    const params = new URLSearchParams({ page: "0", size: "200" });
    if (q) params.set("q", q);
    const data = await apiFetch(config.apiBase + "?" + params.toString(), { method: "GET" });
    state.items = Array.isArray(data?.content) ? data.content : [];
    renderSummary();
    renderTable();
  }

  function isWaitingInspection(item) {
    const today = new Date();
    today.setHours(0, 0, 0, 0);
    if (!item.lastInspectionDate) return true;
    const last = new Date(item.lastInspectionDate + "T00:00:00");
    if (Number.isNaN(last.getTime())) return true;
    const diffDays = Math.floor((today.getTime() - last.getTime()) / (1000 * 60 * 60 * 24));
    return diffDays >= 30;
  }

  function filteredItems() {
    return state.items.filter(function (item) {
      return !state.activeStatusFilter
        || (state.activeStatusFilter === "waiting" && isWaitingInspection(item))
        || (state.activeStatusFilter === "maintenance" && String(item.lastInspectionStatus || "").toUpperCase() === "MAINTENANCE")
        || (state.activeStatusFilter === "faulty" && String(item.lastInspectionStatus || "").toUpperCase() === "FAULTY");
    });
  }

  function syncStatusButtons() {
    const mappings = [
      ["btnStatusWaiting", "waiting"],
      ["btnStatusMaintenance", "maintenance"],
      ["btnStatusFaulty", "faulty"]
    ];
    mappings.forEach(function (entry) {
      const button = document.getElementById(entry[0]);
      if (!button) return;
      const active = state.activeStatusFilter === entry[1];
      button.style.outline = active ? "3px solid rgba(255,255,255,.65)" : "none";
      button.style.transform = active ? "translateY(-1px)" : "none";
      button.style.boxShadow = active ? "0 10px 18px rgba(15,23,42,.22)" : "";
    });
  }

  function renderSummary() {
    const total = state.items.length;
    const waiting = state.items.filter(isWaitingInspection).length;
    const maintenance = state.items.filter(item => String(item.lastInspectionStatus || "").toUpperCase() === "MAINTENANCE").length;
    const faulty = state.items.filter(item => String(item.lastInspectionStatus || "").toUpperCase() === "FAULTY").length;
    document.getElementById("totalCount").textContent = String(total);
    document.getElementById("countWaiting").textContent = "(" + waiting + ")";
    const maintenanceEl = document.getElementById("countMaintenance");
    if (maintenanceEl) maintenanceEl.textContent = "(" + maintenance + ")";
    document.getElementById("countFaulty").textContent = "(" + faulty + ")";
    syncStatusButtons();
  }

  function renderTable() {
    const tbody = document.getElementById("listBody");
    if (!tbody) return;
    const rows = filteredItems();
    if (!rows.length) {
      tbody.innerHTML = '<tr><td colspan="8" class="text-center text-muted py-5">등록된 항목이 없습니다.</td></tr>';
      return;
    }
    tbody.innerHTML = rows.map(item => {
      const id = item[config.idField];
      return '' +
        '<tr class="clickable-row" data-id="' + escapeHtml(id) + '">' +
        '<td>' + escapeHtml(item.serialNumber || "-") + '</td>' +
        '<td>' + escapeHtml(item.buildingName || "-") + '</td>' +
        '<td>' + escapeHtml(item.floorName || "옥외") + '</td>' +
        '<td>' + escapeHtml(item.lastInspectionDate || "-") + '</td>' +
        '<td>' + escapeHtml(item.lastInspectorName || "-") + '</td>' +
        '<td>' + statusBadge(item.lastInspectionStatus) + '</td>' +
        '<td class="text-truncate" style="max-width:240px;" title="' + escapeHtml(item.locationDescription || "") + '">' + escapeHtml(item.locationDescription || "-") + '</td>' +
        '<td class="text-end"><div class="action-group">' +
        (canManage
          ? '<button type="button" class="btn btn-sm btn-fw-inspect js-inspect" data-id="' + escapeHtml(id) + '">점검</button>' +
            '<button type="button" class="btn btn-sm btn-fw-edit js-edit" data-id="' + escapeHtml(id) + '">수정</button>' +
            '<button type="button" class="btn btn-sm btn-fw-delete js-delete" data-id="' + escapeHtml(id) + '">삭제</button>'
          : '<span class="text-muted small">조회 전용</span>') +
        '</div></td>' +
        '</tr>';
    }).join("");
  }

  function setSelectedCoord(x, y) {
    document.getElementById("coordX").value = x != null ? Number(x).toFixed(2) : "";
    document.getElementById("coordY").value = y != null ? Number(y).toFixed(2) : "";
    document.getElementById("coordText").textContent = x != null && y != null
      ? "선택 좌표: X " + Number(x).toFixed(2) + "%, Y " + Number(y).toFixed(2) + "%"
      : "옥외 도면에서 위치를 선택해 주세요.";
  }

  function normalizeFloorName(value) {
    return String(value || "")
      .trim()
      .toLowerCase()
      .replace(/\s+/g, "")
      .replace(/\uCE35/g, "f")
      .replace(/\uBC18\uC9C0\uD558/g, "b")
      .replace(/\uC9C0\uD558/g, "b")
      .replace(/\uC625\uC678/g, "outdoor");
  }

  function normalizeBuildingName(value) {
    return String(value || "")
      .trim()
      .toLowerCase()
      .replace(/\s+/g, "")
      .replace(/[.,_\-]/g, "");
  }

  function resolvePlanImagePathByName(buildingName, floorName) {
    const building = normalizeBuildingName(buildingName);
    const floor = normalizeFloorName(floorName);
    if (!building && !floor) return "/images/drone_photo.JPG";
    if (building.includes("\uC625\uC678") || building.includes("outdoor") || floor === "outdoor") return "/images/drone_photo.JPG";
    if (building.includes("\uBCF5\uC9C0\uAD00") || building.includes("bokji")) {
      if (floor === "b1") return "/images/bokji_B1.png";
      if (floor === "1f") return "/images/bokji_1F.png";
      if (floor === "2f") return "/images/bokji_2F.png";
      if (floor === "3f") return "/images/bokji_3F.png";
    }
    if ((building.includes("\uAD00\uB9AC\uB3D9") || building.includes("gwanri")) && floor === "1f") return "/images/gwanri_1F.png";
    if (building.includes("\uC81C\uC9C01,2\uD638\uAE30") || building.includes("jeji12")) {
      if (floor === "1f") return "/images/jeji1,2_1F.PNG";
      if (floor === "2f") return "/images/jeji1,2_2F.PNG";
    }
    if (building.includes("\uC81C\uC9C03\uD638\uAE30") || building.includes("jeji3")) {
      if (floor === "1f") return "/images/jeji3_1F.PNG";
      if (floor === "2f") return "/images/jeji3_2F.PNG";
    }
    if (building.includes("\uD328\uB4DC\uB3D9") || building.includes("pad")) {
      if (floor === "1f") return "/images/pad_1F.PNG";
      if (floor === "2f") return "/images/pad_2F.PNG";
    }
    if (building.includes("\uC2EC\uBA74\uD384\uD37C") || building.includes("palpa") || building.includes("pulper")) {
      if (floor === "1f") return "/images/palpa_1F.PNG";
      if (floor === "2f") return "/images/palpa_2F.PNG";
    }
    if (building.includes("\uD654\uC7A5\uC9C03,6\uD638\uAE30") || building.includes("tissue13") || building.includes("tissue36")) {
      if (floor === "1f") return "/images/tissue1,3_1F.PNG";
      if (floor === "2f") return "/images/tissue1,3_2F.PNG";
    }
    if (building.includes("\uD654\uC7A5\uC9C04,5\uD638\uAE30") || building.includes("tissue45")) {
      if (floor === "b1") return "/images/tissue4,5_B1.PNG";
      if (floor === "1f") return "/images/tissue4,5_1F.PNG";
      if (floor === "2f") return "/images/tissue4,5_2F.PNG";
      if (floor === "3f") return "/images/tissue4,5_3F.PNG";
    }
    if ((building.includes("\uAE30\uC800\uADC0\uB3D9") || building.includes("diaper")) && floor === "1f") return "/images/diaper_1F.png";
    return "/images/drone_photo.JPG";
  }

  function updatePlanImage(containerId, buildingName, floorName, onReady) {
    const container = document.getElementById(containerId);
    const img = container?.querySelector("img");
    if (!img) {
      if (typeof onReady === "function") onReady();
      return;
    }
    const path = resolvePlanImagePathByName(buildingName, floorName);
    const finalize = function () {
      if (typeof onReady === "function") onReady();
    };
    img.onload = finalize;
    img.onerror = finalize;
    img.src = path;
    if (img.complete) finalize();
  }

  function rerenderDetailMap() {
    if (!state.currentDetail) return;
    requestAnimationFrame(function () {
      renderDetailMap(state.currentDetail);
    });
  }

  function getPlanNaturalSize(container) {
    const img = container?.querySelector("img");
    if (img?.naturalWidth > 0 && img?.naturalHeight > 0) {
      return { width: img.naturalWidth, height: img.naturalHeight };
    }
    return { width: 1955, height: 985 };
  }

  function getPlanDisplayRect(container) {
    if (!container) return null;
    const rect = container.getBoundingClientRect();
    const natural = getPlanNaturalSize(container);
    if (!rect.width || !rect.height || !natural.width || !natural.height) return null;
    const scale = Math.min(rect.width / natural.width, rect.height / natural.height);
    const drawWidth = natural.width * scale;
    const drawHeight = natural.height * scale;
    return {
      rect: rect,
      drawWidth: drawWidth,
      drawHeight: drawHeight,
      offsetX: (rect.width - drawWidth) / 2,
      offsetY: (rect.height - drawHeight) / 2
    };
  }

  function renderMapMarkers() {
    const planWrap = document.getElementById("planWrap");
    const img = planWrap?.querySelector("img");
    const layer = document.getElementById("mapMarkerLayer");
    if (!layer || !planWrap || !img) return;

    const draw = function () {
      layer.innerHTML = "";
      const display = getPlanDisplayRect(planWrap);
      if (!display) return;
      img.style.cssText =
        "position:absolute;left:" + display.offsetX + "px;top:" + display.offsetY + "px;width:" + display.drawWidth + "px;height:" + display.drawHeight + "px;transform:none;object-fit:contain;display:block;";
      const currentItemId = String(document.getElementById("itemId")?.value || "");
      const items = []
        .concat(state.peers.receivers.map(function (item) { return { item: item, kind: "receiver" }; }))
        .concat(state.peers.pumps.map(function (item) { return { item: item, kind: "pump" }; }));

      items.forEach(function (entry) {
        const source = entry.item || {};
        const itemId = String(entry.kind === "receiver" ? source.receiverId : source.pumpId);
        const useSelected = currentItemId && itemId === currentItemId && state.selectedCoord;
        const x = useSelected ? Number(state.selectedCoord.x) : Number(source.x);
        const y = useSelected ? Number(state.selectedCoord.y) : Number(source.y);
        if (!Number.isFinite(x) || !Number.isFinite(y)) return;
        const marker = document.createElement("button");
        marker.type = "button";
        marker.className = "map-marker marker-" + entry.kind + (itemId === currentItemId ? " marker-current" : "");
        marker.dataset.kind = entry.kind;
        marker.dataset.id = itemId;
        marker.style.left = (display.offsetX + (display.drawWidth * (x / 100))).toFixed(2) + "px";
        marker.style.top = (display.offsetY + (display.drawHeight * (y / 100))).toFixed(2) + "px";
        marker.innerHTML = "<span></span>";
        layer.appendChild(marker);
      });

      if (state.selectedCoord) {
        const iconPath = config.idField === "receiverId" ? "/images/receiver.png" : "/images/pump.png";
        const marker = document.createElement("div");
        marker.className = "map-marker marker-selected marker-selected-highlight";
        marker.style.pointerEvents = "none";
        marker.style.zIndex = "4";
        marker.style.left = (display.offsetX + (display.drawWidth * (Number(state.selectedCoord.x) / 100))).toFixed(2) + "px";
        marker.style.top = (display.offsetY + (display.drawHeight * (Number(state.selectedCoord.y) / 100))).toFixed(2) + "px";
        marker.innerHTML =
          '<span class="marker-selected-ring"></span>' +
          '<img src="' + escapeHtml(iconPath) + '" alt="" class="marker-selected-icon" />';
        layer.appendChild(marker);
      }
    };

    if (img.complete) draw();
    img.onload = draw;
    setTimeout(draw, 0);
  }

  function renderDetailMap(detail) {
    const wrap = document.getElementById("detailPlanWrap");
    const img = wrap?.querySelector("img");
    const layer = document.getElementById("detailMapMarkerLayer");
    if (!wrap || !img || !layer) {
      if (layer) layer.innerHTML = "";
      return;
    }
    const nx = Number(detail?.x);
    const ny = Number(detail?.y);
    const hasCoord = Number.isFinite(nx) && Number.isFinite(ny);
    const iconPath = config.idField === "receiverId" ? "/images/receiver.png" : "/images/pump.png";

    const draw = function () {
      layer.innerHTML = "";
      const rect = wrap.getBoundingClientRect();
      const cw = rect.width || 0;
      const ch = rect.height || 0;
      const iw = img.naturalWidth || 0;
      const ih = img.naturalHeight || 0;
      if (!cw || !ch || !iw || !ih) return;
      const scale = Math.min(cw / iw, ch / ih);
      const w = iw * scale;
      const h = ih * scale;
      const ox = (cw - w) / 2;
      const oy = (ch - h) / 2;
      img.style.cssText = "position:absolute;left:" + ox + "px;top:" + oy + "px;width:" + w + "px;height:" + h + "px;transform:none;object-fit:contain;display:block;";
      if (!hasCoord) return;
      const left = ox + w * (Math.max(0, Math.min(100, nx)) / 100);
      const top = oy + h * (Math.max(0, Math.min(100, ny)) / 100);
      const marker = document.createElement("div");
      const markerSize = Math.max(24, Math.min(44, Math.min(w, h) * 0.05));
      marker.style.cssText = "position:absolute;left:" + left.toFixed(2) + "px;top:" + top.toFixed(2) + "px;width:" + markerSize.toFixed(1) + "px;height:" + markerSize.toFixed(1) + "px;transform:translate(-50%,-50%);pointer-events:none;z-index:4;filter:drop-shadow(0 0 10px rgba(220,38,38,.9)) drop-shadow(0 0 18px rgba(251,191,36,.75));";
      marker.innerHTML =
        "<span style=\"position:absolute;left:50%;top:50%;width:150%;height:150%;transform:translate(-50%,-50%);border:3px solid rgba(251,191,36,.95);border-radius:999px;box-shadow:0 0 0 2px rgba(15,23,42,.85),0 0 16px rgba(251,191,36,.9);\"></span>" +
        "<img src=\"" + iconPath + "\" alt=\"\" style=\"position:relative;width:100%;height:100%;object-fit:contain;display:block;background:transparent;z-index:1;\" />";
      layer.appendChild(marker);
    };

    if (img.complete) draw();
    img.onload = draw;
    setTimeout(draw, 0);
  }

  function ensureDetailZoomModal() {
    if (document.getElementById("detailImageZoomModal")) return;
    const host = document.createElement("div");
    host.innerHTML = '' +
      '<div class="modal fade" id="detailImageZoomModal" tabindex="-1" aria-hidden="true">' +
      '  <div class="modal-dialog modal-dialog-centered" style="max-width: min(96vw, 1600px);">' +
      '    <div class="modal-content bg-dark">' +
      '      <div class="modal-body p-3">' +
      '        <div id="detailImageZoomStage" class="position-relative mx-auto" style="width:100%; height:min(88vh,1200px);">' +
      '          <button type="button" class="btn-close btn-close-white" data-bs-dismiss="modal" aria-label="Close" style="position:absolute;top:8px;right:8px;z-index:4;"></button>' +
      '          <img id="detailImageZoomTarget" src="" alt="zoom" style="width:100%;height:100%;object-fit:contain;display:block;" />' +
      '          <div id="detailImageZoomMarkerLayer" style="position:absolute;inset:0;pointer-events:none;z-index:3;"></div>' +
      '        </div>' +
      '      </div>' +
      '    </div>' +
      '  </div>' +
      '</div>';
    document.body.appendChild(host.firstElementChild);
    document.getElementById("detailImageZoomStage")?.addEventListener("click", function () {
      bootstrap.Modal.getOrCreateInstance(document.getElementById("detailImageZoomModal")).hide();
    });
    document.getElementById("detailImageZoomTarget")?.addEventListener("click", function () {
      bootstrap.Modal.getOrCreateInstance(document.getElementById("detailImageZoomModal")).hide();
    });
    document.getElementById("detailImageZoomModal")?.addEventListener("click", function (e) {
      if (e.target && e.target.id === "detailImageZoomModal") {
        bootstrap.Modal.getOrCreateInstance(document.getElementById("detailImageZoomModal")).hide();
      }
    });
    document.getElementById("detailImageZoomModal")?.addEventListener("shown.bs.modal", renderDetailZoomMarker);
  }

  function renderDetailZoomMarker() {
    const target = document.getElementById("detailImageZoomTarget");
    const stage = document.getElementById("detailImageZoomStage");
    const layer = document.getElementById("detailImageZoomMarkerLayer");
    if (!target || !stage || !layer) return;
    layer.innerHTML = "";
    const rawMarkerX = String(target.dataset.markerX || "").trim();
    const rawMarkerY = String(target.dataset.markerY || "").trim();
    if (!rawMarkerX || !rawMarkerY) return;
    const markerX = parseFloat(rawMarkerX);
    const markerY = parseFloat(rawMarkerY);
    if (!Number.isFinite(markerX) || !Number.isFinite(markerY)) return;
    const rect = stage.getBoundingClientRect();
    const iw = target.naturalWidth || 0;
    const ih = target.naturalHeight || 0;
    if (!rect.width || !rect.height || !iw || !ih) return;
    const scale = Math.min(rect.width / iw, rect.height / ih);
    const drawWidth = iw * scale;
    const drawHeight = ih * scale;
    const offsetX = (rect.width - drawWidth) / 2;
    const offsetY = (rect.height - drawHeight) / 2;
    const left = offsetX + drawWidth * (markerX / 100);
    const top = offsetY + drawHeight * (markerY / 100);
    const marker = document.createElement("div");
    marker.style.cssText = "position:absolute;left:" + left.toFixed(2) + "px;top:" + top.toFixed(2) + "px;width:44px;height:44px;transform:translate(-50%,-50%);pointer-events:none;z-index:4;filter:drop-shadow(0 0 10px rgba(220,38,38,.9)) drop-shadow(0 0 18px rgba(251,191,36,.75));";
    marker.innerHTML =
      "<span style=\"position:absolute;left:50%;top:50%;width:150%;height:150%;transform:translate(-50%,-50%);border:3px solid rgba(251,191,36,.95);border-radius:999px;box-shadow:0 0 0 2px rgba(15,23,42,.85),0 0 16px rgba(251,191,36,.9);\"></span>" +
      "<img src=\"" + escapeHtml(target.dataset.markerIcon || "") + "\" alt=\"\" style=\"position:relative;width:100%;height:100%;object-fit:contain;display:block;background:transparent;z-index:1;\" />";
    layer.appendChild(marker);
  }

  function openDetailImageZoom(sourceEl) {
    const src = sourceEl?.getAttribute("data-zoom-src") || sourceEl?.getAttribute("src");
    if (!src) return;
    ensureDetailZoomModal();
    const target = document.getElementById("detailImageZoomTarget");
    if (!target) return;
    target.dataset.markerX = sourceEl?.dataset.markerX || "";
    target.dataset.markerY = sourceEl?.dataset.markerY || "";
    target.dataset.markerIcon = sourceEl?.dataset.markerIcon || "";
    target.onload = renderDetailZoomMarker;
    target.src = src;
    bootstrap.Modal.getOrCreateInstance(document.getElementById("detailImageZoomModal")).show();
    setTimeout(renderDetailZoomMarker, 0);
  }

  function renderDetailLayout(detail) {
    const detailModal = document.getElementById("detailModal");
    const detailBody = detailModal?.querySelector(".modal-body");
    const qrType = config.idField === "receiverId" ? "rcv" : "pmp";
    const markerIcon = config.idField === "receiverId" ? "/images/receiver.png" : "/images/pump.png";
    const qrUrl = detail.qrKey ? ("/fire-api/qr/image?type=" + encodeURIComponent(qrType) + "&id=" + encodeURIComponent(detail.qrKey)) : "";
    const planImagePath = resolvePlanImagePathByName(detail.buildingName, detail.floorName);
    const photoPath = detail.imagePath || (Array.isArray(detail.inspections) ? ((detail.inspections.find(function (row) { return row.imagePath; }) || {}).imagePath || "") : "");
    const coordText = detail.x != null && detail.y != null ? (Number(detail.x).toFixed(2) + "%, " + Number(detail.y).toFixed(2) + "%") : "-";
    document.getElementById("detailTitle").textContent = (detail.serialNumber || config.singular) + " 상세";
    if (!detailBody) return;

    detailBody.innerHTML = '' +
      '<div class="d-flex justify-content-end gap-2 mb-3">' +
      '  <input type="date" class="form-control form-control-sm" id="exportFromDate" style="max-width:160px;" />' +
      '  <input type="date" class="form-control form-control-sm" id="exportToDate" style="max-width:160px;" />' +
      '  <button type="button" class="btn btn-outline-success" id="detailExportBtn">CSV 다운로드</button>' +
      '  <button type="button" class="btn btn-primary" id="detailInspectBtn"' + (canManage ? "" : ' style="display:none;"') + '>점검</button>' +
      '  <button type="button" class="btn btn-outline-primary" id="detailEditBtn"' + (canManage ? "" : ' style="display:none;"') + '>수정</button>' +
      '</div>' +
      '<div class="container-fluid px-0">' +
      '  <div class="row g-4">' +
      '    <div class="col-md-6 d-flex flex-column gap-3">' +
      '      <div class="fw-edit-section">' +
      '        <div class="fw-edit-section-title"><strong>설비 정보</strong></div>' +
      '        <div class="fw-detail-grid">' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">관리번호</div><div class="fw-detail-item-value">' + escapeHtml(detail.serialNumber || "-") + '</div></div>' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">상태</div><div class="fw-detail-item-value">' + statusBadge(detail.lastInspectionStatus) + '</div></div>' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">건물</div><div class="fw-detail-item-value">' + escapeHtml(detail.buildingName || "-") + '</div></div>' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">층</div><div class="fw-detail-item-value">' + escapeHtml(detail.floorName || "옥외") + '</div></div>' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">최근 점검</div><div class="fw-detail-item-value">' + escapeHtml(formatDateTime(detail.lastInspectionDate, detail.lastInspectionTime)) + '</div></div>' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">점검자</div><div class="fw-detail-item-value">' + escapeHtml(detail.lastInspectorName || "-") + '</div></div>' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">도면 좌표</div><div class="fw-detail-item-value">' + escapeHtml(coordText) + '</div></div>' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">상세 위치</div><div class="fw-detail-item-value">' + escapeHtml(detail.locationDescription || "-") + '</div></div>' +
      '          <div class="fw-detail-item"><div class="fw-detail-item-label">비고</div><div class="fw-detail-item-value">' + escapeHtml(detail.note || "-") + '</div></div>' +
      '        </div>' +
      '      </div>' +
      '      <div class="fw-edit-section">' +
      '        <div class="fw-edit-section-title"><strong>점검 이력</strong><span>최근 12건</span></div>' +
      '        <div class="fw-history-wrap">' +
      '          <table class="table table-sm fw-history-table">' +
      '            <thead><tr><th style="width:14%;">점검일시</th><th style="width:14%;">점검자</th><th style="width:22%;">결과 / 메모</th><th>점검 항목</th></tr></thead>' +
      '            <tbody id="detailHistoryBody"><tr><td colspan="4" class="text-center text-muted py-4">점검 이력이 없습니다.</td></tr></tbody>' +
      '          </table>' +
      '        </div>' +
      '      </div>' +
      '    </div>' +
      '    <div class="col-md-6 d-flex flex-column gap-3">' +
      '      <div class="fw-edit-section"><div class="fw-edit-section-title"><strong>사진</strong></div>' +
             (photoPath
               ? '<div class="fw-media-box text-center"><img src="' + escapeHtml(photoPath) + '" alt="photo" class="img-fluid rounded shadow js-detail-zoomable" data-zoom-src="' + escapeHtml(photoPath) + '" style="max-width:100%;max-height:260px;object-fit:contain;" /></div>'
               : '<div class="fw-empty-box">등록된 사진이 없습니다.</div>') +
      '      </div>' +
      '      <div class="fw-edit-section"><div class="fw-edit-section-title"><strong>QR코드</strong></div>' +
             (qrUrl
               ? '<div class="fw-media-box text-center"><img src="' + escapeHtml(qrUrl) + '" alt="qr" class="img-fluid rounded shadow js-detail-zoomable" data-zoom-src="' + escapeHtml(qrUrl) + '" style="max-width:220px;max-height:220px;object-fit:contain;" /></div>'
               : '<div class="fw-empty-box">QR 정보가 없습니다.</div>') +
      '      </div>' +
      '      <div class="fw-edit-section"><div class="fw-edit-section-title"><strong>도면상 위치</strong></div>' +
             (planImagePath
               ? '<div id="detailPlanWrap" class="fw-media-box position-relative" style="height:260px;overflow:hidden;">' +
                   '<img src="' + escapeHtml(planImagePath) + '" alt="plan" class="js-detail-zoomable" data-zoom-src="' + escapeHtml(planImagePath) + '" data-marker-x="' + escapeHtml(detail.x ?? "") + '" data-marker-y="' + escapeHtml(detail.y ?? "") + '" data-marker-icon="' + escapeHtml(markerIcon) + '" style="position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);max-width:100%;max-height:100%;width:auto;height:auto;display:block;" />' +
                   '<div id="detailMapMarkerLayer" style="position:absolute;inset:0;pointer-events:none;z-index:3;"></div>' +
                 '</div>'
               : '<div class="fw-empty-box">도면 정보가 없습니다.</div>') +
      '      </div>' +
      '    </div>' +
      '  </div>' +
      '</div>';

    const exportFromDate = document.getElementById("exportFromDate");
    const exportToDate = document.getElementById("exportToDate");
    if (exportFromDate && !exportFromDate.value) {
      exportFromDate.value = detail.inspections?.length ? (detail.inspections[detail.inspections.length - 1]?.inspectionDate || "") : "";
    }
    if (exportToDate) {
      exportToDate.value = detail.inspections?.length ? (detail.inspections[0]?.inspectionDate || "") : "";
    }
    updatePlanImage("detailPlanWrap", detail.buildingName, detail.floorName, function () {
      renderDetailMap(detail);
    });
    renderHistory(detail.inspections || []);
    document.getElementById("detailExportBtn")?.addEventListener("click", downloadInspectionCsv);
    document.getElementById("detailInspectBtn")?.addEventListener("click", function () {
      if (!canManage || !state.selectedDetailId) return;
      openInspectModal(state.selectedDetailId);
    });
    document.getElementById("detailEditBtn")?.addEventListener("click", function () {
      if (!canManage) return;
      const current = state.items.find(function (item) { return String(item[config.idField]) === String(state.selectedDetailId); });
      if (current) {
        state.detailModal.hide();
        openItemModal(current);
      }
    });
    detailBody.querySelectorAll(".js-detail-zoomable").forEach(function (el) {
      el.addEventListener("click", function () {
        openDetailImageZoom(el);
      });
    });
  }

  function openItemModal(item) {
    const coordOverride = readQueryCoord();
    const queryBuildingName = query.get("buildingName") || "";
    document.getElementById("itemId").value = item?.[config.idField] || "";
    document.getElementById("buildingName").value = item?.buildingName || queryBuildingName || "";
    document.getElementById("locationDescription").value = item?.locationDescription || "";
    document.getElementById("note").value = item?.note || "";
    document.getElementById("modalTitle").textContent = item ? config.singular + " 수정" : config.singular + " 등록";
    state.selectedCoord = coordOverride || (item?.x != null && item?.y != null ? { x: Number(item.x), y: Number(item.y) } : null);
    setSelectedCoord(state.selectedCoord?.x, state.selectedCoord?.y);
    updatePlanImage("planWrap", item?.buildingName || queryBuildingName, item?.floorName || query.get("floorName"), renderMapMarkers);
    const historySection = document.getElementById("itemHistorySection");
    const historyBody = document.getElementById("itemHistoryBody");
    if (item?.[config.idField]) {
      if (historySection) historySection.style.display = canManage ? "" : "none";
      apiFetch(config.apiBase + "/" + encodeURIComponent(item[config.idField]), { method: "GET" })
        .then(function (detail) {
          renderEditableHistory(detail.inspections || []);
        })
        .catch(function () {
          if (historyBody) {
            historyBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">점검 이력을 불러오지 못했습니다.</td></tr>';
          }
        });
    } else {
      if (historySection) historySection.style.display = "none";
      if (historyBody) {
        historyBody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">점검 이력이 없습니다.</td></tr>';
      }
    }
    state.itemModal.show();
  }

  async function saveItem() {
    await window.FireWebCsrf?.ensureToken?.();
    const body = {
      buildingName: document.getElementById("buildingName").value.trim(),
      x: document.getElementById("coordX").value || null,
      y: document.getElementById("coordY").value || null,
      locationDescription: document.getElementById("locationDescription").value.trim() || null,
      note: document.getElementById("note").value.trim() || null
    };
    if (!body.buildingName) throw new Error("건물을 입력해 주세요.");
    if (!body.x || !body.y) throw new Error("옥외 도면에서 위치를 선택해 주세요.");
    const itemId = document.getElementById("itemId").value;
    if (itemId) body[config.idField] = Number(itemId);
    await apiFetch(config.apiBase, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body)
    });
    state.itemModal.hide();
    await loadPeers();
    await loadList();
  }

  async function deleteItem(id) {
    if (!confirm(config.singular + "를 삭제하시겠습니까?")) return;
    await window.FireWebCsrf?.ensureToken?.();
    await apiFetch(config.apiBase + "/" + encodeURIComponent(id), { method: "DELETE" });
    await loadPeers();
    await loadList();
  }

  async function openDetail(id) {
    state.selectedDetailId = id;
    const detail = await apiFetch(config.apiBase + "/" + encodeURIComponent(id), { method: "GET" });
    state.currentDetail = detail;
    renderDetailLayout(detail);
    state.detailModal.show();
    updatePlanImage("detailPlanWrap", detail?.buildingName, detail?.floorName, function () {
      renderDetailMap(detail);
    });
  }

  function renderDetail(detail) {
    document.getElementById("detailTitle").textContent = (detail.serialNumber || config.singular) + " 상세";
    document.getElementById("detailSerial").textContent = detail.serialNumber || "-";
    document.getElementById("detailBuilding").textContent = detail.buildingName || "-";
    document.getElementById("detailFloor").textContent = detail.floorName || "옥외";
    document.getElementById("detailStatus").innerHTML = statusBadge(detail.lastInspectionStatus);
    document.getElementById("detailNote").textContent = detail.note || "-";
    document.getElementById("detailLastInspection").textContent = formatDateTime(detail.lastInspectionDate, detail.lastInspectionTime);
    document.getElementById("detailLastInspector").textContent = detail.lastInspectorName || "-";
    const exportFromDate = document.getElementById("exportFromDate");
    const exportToDate = document.getElementById("exportToDate");
    const detailInspectBtn = document.getElementById("detailInspectBtn");
    const detailEditBtn = document.getElementById("detailEditBtn");
    if (detailInspectBtn) detailInspectBtn.style.display = canManage ? "" : "none";
    if (detailEditBtn) detailEditBtn.style.display = canManage ? "" : "none";
    if (exportFromDate && !exportFromDate.value) {
      exportFromDate.value = detail.inspections?.length ? (detail.inspections[detail.inspections.length - 1]?.inspectionDate || "") : "";
    }
    if (exportToDate) {
      exportToDate.value = detail.inspections?.length ? (detail.inspections[0]?.inspectionDate || "") : "";
    }
    updatePlanImage("detailPlanWrap", detail.buildingName, detail.floorName, function () {
      renderDetailMap(detail);
    });
    renderHistory(detail.inspections || []);
  }

  function checklistOptions(selected) {
    const current = String(selected || "").toUpperCase();
    return '' +
      '<option value="NORMAL"' + (current === "NORMAL" ? " selected" : "") + '>정상</option>' +
      '<option value="MAINTENANCE"' + (current === "MAINTENANCE" ? " selected" : "") + '>요정비</option>' +
      '<option value="FAULTY"' + (current === "FAULTY" ? " selected" : "") + '>불량</option>';
  }

  function buildChecklistEditor(row) {
    const byKey = new Map((row.checklistItems || []).map(function (item) {
      return [String(item.itemKey || ""), item];
    }));
    return config.checklist.map(function (item) {
      const found = byKey.get(String(item.key)) || {};
      return '' +
        '<div class="mb-2">' +
        '<label class="form-label small mb-1">' + escapeHtml(item.label) + '</label>' +
        '<select class="form-select form-select-sm js-hist-item" data-item-key="' + escapeHtml(item.key) + '" data-item-label="' + escapeHtml(item.label) + '">' +
        checklistOptions(found.result || "") +
        '</select>' +
        '</div>';
    }).join("");
  }

  function renderHistory(inspections) {
    const tbody = document.getElementById("detailHistoryBody");
    if (!tbody) return;
    if (!inspections.length) {
      tbody.innerHTML = '<tr><td colspan="4" class="text-center text-muted py-4">점검 이력이 없습니다.</td></tr>';
      return;
    }
    tbody.innerHTML = inspections.slice(0, 12).map(row => {
      const itemSummary = Array.isArray(row.checklistItems) && row.checklistItems.length
        ? row.checklistItems.map(item => '<div><span class="detail-history-label">' + escapeHtml(item.itemLabel) + '</span> <span class="detail-history-result">' + escapeHtml(statusLabel(item.result)) + '</span></div>').join("")
        : '<span class="text-muted">-</span>';
      const photoLink = row.imagePath
        ? '<a href="' + escapeHtml(row.imagePath) + '" target="_blank" rel="noopener">사진 보기</a>'
        : '<span class="text-muted">-</span>';
      return '' +
        '<tr>' +
        '<td>' + escapeHtml(formatDateTime(row.inspectionDate, row.inspectionTime)) + '</td>' +
        '<td>' + escapeHtml(row.inspectorName || "-") + '</td>' +
        '<td><div class="mb-2">' + statusBadge(row.inspectionStatus) + '</div><div class="small text-muted">' + escapeHtml(row.note || "-") + '</div></td>' +
        '<td>' + itemSummary + '<div class="small mt-2">' + photoLink + '</div></td>' +
        "</tr>";
    }).join("");
  }

  function renderEditableHistory(inspections) {
    const tbody = document.getElementById("itemHistoryBody");
    if (!tbody) return;
    const rows = inspections.slice(0, 12).map(function (row) {
      const photoLink = row.imagePath
        ? '<a href="' + escapeHtml(row.imagePath) + '" target="_blank" rel="noopener">사진 보기</a>'
        : '<span class="text-muted">-</span>';
      return '' +
        '<tr data-inspection-id="' + escapeHtml(row.inspectionId) + '">' +
        '<td><div class="d-flex gap-2"><input type="date" class="form-control form-control-sm js-hist-date" value="' + escapeHtml(row.inspectionDate || "") + '"><input type="time" class="form-control form-control-sm js-hist-time" value="' + escapeHtml(row.inspectionTime ? String(row.inspectionTime).slice(0, 5) : "") + '"></div></td>' +
        '<td><input type="text" class="form-control form-control-sm js-hist-inspector" value="' + escapeHtml(row.inspectorName || "") + '"></td>' +
        '<td><div class="mb-2">' + statusBadge(row.inspectionStatus) + '</div><textarea class="form-control form-control-sm js-hist-note" rows="3">' + escapeHtml(row.note || "") + '</textarea><div class="small mt-2">' + photoLink + '</div></td>' +
        '<td>' + buildChecklistEditor(row) + '<div class="text-end mt-2"><button type="button" class="btn btn-sm btn-fw-edit js-history-save">저장</button></div></td>' +
        '</tr>';
    });
    rows.unshift(
      '<tr data-inspection-id="">' +
      '<td><div class="d-flex gap-2"><input type="date" class="form-control form-control-sm js-hist-date" value=""><input type="time" class="form-control form-control-sm js-hist-time" value=""></div></td>' +
      '<td><input type="text" class="form-control form-control-sm js-hist-inspector" value=""></td>' +
      '<td><div class="mb-2"><span class="fw-status fw-wait">신규</span></div><textarea class="form-control form-control-sm js-hist-note" rows="3"></textarea><div class="small mt-2 text-muted">새 점검 이력 추가</div></td>' +
      '<td>' + buildChecklistEditor({ checklistItems: [] }) + '<div class="text-end mt-2"><button type="button" class="btn btn-sm btn-primary js-history-save">추가</button></div></td>' +
      '</tr>'
    );
    tbody.innerHTML = rows.join("");
  }

  function renderChecklistForm() {
    const container = document.getElementById("inspectChecklistRows");
    if (!container) return;
    container.innerHTML = config.checklist.map(item => {
      return '' +
        '<div class="inspect-row">' +
        '<div class="inspect-row-label">' +
        '<div class="inspect-row-title">' + escapeHtml(item.label) + '</div>' +
        '<div class="inspect-row-desc">' + escapeHtml(item.description || "") + '</div>' +
        '</div>' +
        '<select class="form-select inspect-result" data-key="' + escapeHtml(item.key) + '" data-label="' + escapeHtml(item.label) + '">' +
        '<option value="">선택</option>' +
        '<option value="NORMAL">정상</option>' +
        '<option value="MAINTENANCE">요정비</option>' +
        '<option value="FAULTY">불량</option>' +
        '</select>' +
        '</div>';
    }).join("");
  }

  function openInspectModal(id) {
    const user = getUser();
    const current = state.currentDetail && String(state.currentDetail[config.idField]) === String(id)
      ? state.currentDetail
      : state.items.find(function (item) { return String(item[config.idField]) === String(id); }) || null;
    document.getElementById("inspectTargetId").value = String(id);
    document.getElementById("inspectTargetName").textContent = current?.serialNumber || config.singular;
    document.getElementById("inspectDate").value = new Date().toISOString().slice(0, 10);
    document.getElementById("inspectTime").value = new Date().toTimeString().slice(0, 5);
    document.getElementById("inspectInspector").value = user?.displayName || user?.username || "";
    document.getElementById("inspectNote").value = "";
    document.getElementById("inspectPhoto").value = "";
    renderChecklistForm();
    state.inspectModal.show();
  }

  async function saveInspection() {
    await window.FireWebCsrf?.ensureToken?.();
    const targetId = document.getElementById("inspectTargetId").value;
    const items = Array.from(document.querySelectorAll(".inspect-result")).map(select => ({
      itemKey: select.dataset.key,
      itemLabel: select.dataset.label,
      result: select.value
    }));
    if (items.some(item => !item.result)) {
      throw new Error("모든 점검 항목 결과를 선택해 주세요.");
    }
    const detail = await apiFetch(config.apiBase + "/" + encodeURIComponent(targetId) + "/inspect", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        items: items,
        inspectionTime: document.getElementById("inspectTime").value || null,
        note: document.getElementById("inspectNote").value.trim() || null
      })
    });
    const file = document.getElementById("inspectPhoto").files?.[0];
    const inspectionId = detail?.inspections?.[0]?.inspectionId;
    if (file && inspectionId) {
      const formData = new FormData();
      formData.append("file", file);
      await apiFetch(config.apiBase + "/" + encodeURIComponent(targetId) + "/inspections/" + encodeURIComponent(inspectionId) + "/image", {
        method: "POST",
        body: formData
      });
    }
    state.inspectModal.hide();
    await loadPeers();
    await loadList();
    await openDetail(targetId);
  }

  async function handleInitialAction() {
    if (state.initialActionHandled) return;
    state.initialActionHandled = true;
    const pendingAction = getPendingAction();
    if (!pendingAction) return;
    if (pendingAction.type === "add") {
      if (!canManage) return;
      openItemModal(null);
      return;
    }
    if (!pendingAction.id) return;
    if (pendingAction.type === "details") {
      await openDetail(pendingAction.id);
      return;
    }
    if (pendingAction.type === "inspect") {
      if (canManage) {
        openInspectModal(pendingAction.id);
      } else {
        await openDetail(pendingAction.id);
      }
      return;
    }
    if (pendingAction.type === "edit") {
      const current = state.items.find(item => String(item[config.idField]) === String(pendingAction.id));
      if (canManage && current) {
        openItemModal(current);
      } else if (current) {
        await openDetail(pendingAction.id);
      }
    }
  }

  function downloadInspectionCsv() {
    if (!state.selectedDetailId) {
      alert("상세 데이터를 먼저 선택해 주세요.");
      return;
    }
    const from = document.getElementById("exportFromDate")?.value || "";
    const to = document.getElementById("exportToDate")?.value || "";
    if (!from || !to) {
      alert("조회 시작일과 종료일을 선택해 주세요.");
      return;
    }
    if (from > to) {
      alert("조회 시작일은 종료일보다 늦을 수 없습니다.");
      return;
    }
    const url = config.apiBase + "/" + encodeURIComponent(state.selectedDetailId) + "/inspections/export?from=" +
      encodeURIComponent(from) + "&to=" + encodeURIComponent(to);
    window.open(url, "_blank");
  }

  function exportAllCsv() {
    if (!canManage) return;
    const from = document.getElementById("filterDateFrom")?.value || "";
    const to = document.getElementById("filterDateTo")?.value || "";
    if (!from || !to) {
      alert("조회 시작일과 종료일을 선택해 주세요.");
      return;
    }
    if (from > to) {
      alert("조회 시작일은 종료일보다 늦을 수 없습니다.");
      return;
    }
    const url = config.apiBase + "/inspections/export-all?from=" + encodeURIComponent(from) + "&to=" + encodeURIComponent(to);
    window.open(url, "_blank");
  }

  async function saveHistoryRow(button) {
    await window.FireWebCsrf?.ensureToken?.();
    const row = button.closest("tr[data-inspection-id]");
    const currentItemId = document.getElementById("itemId")?.value || "";
    const targetId = currentItemId || state.selectedDetailId;
    if (!row || !targetId) return;
    const inspectionId = row.getAttribute("data-inspection-id") || "";
    const inspectionDate = row.querySelector(".js-hist-date")?.value || "";
    const inspectionTime = row.querySelector(".js-hist-time")?.value || "";
    const inspectorName = row.querySelector(".js-hist-inspector")?.value?.trim() || "";
    const note = row.querySelector(".js-hist-note")?.value?.trim() || "";
    const items = Array.from(row.querySelectorAll(".js-hist-item")).map(function (select) {
      return {
        itemKey: select.getAttribute("data-item-key") || "",
        itemLabel: select.getAttribute("data-item-label") || "",
        result: select.value || ""
      };
    });
    if (!inspectionDate) {
      throw new Error("점검일을 입력해 주세요.");
    }
    if (!inspectorName) {
      throw new Error("점검자를 입력해 주세요.");
    }
    if (items.some(function (item) { return !item.result; })) {
      throw new Error("모든 점검 항목 결과를 선택해 주세요.");
    }
    button.disabled = true;
    try {
      const isNew = !inspectionId;
      await apiFetch(
        isNew
          ? config.apiBase + "/" + encodeURIComponent(targetId) + "/inspections"
          : config.apiBase + "/" + encodeURIComponent(targetId) + "/inspections/" + encodeURIComponent(inspectionId),
        {
        method: isNew ? "POST" : "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          inspectionDate: inspectionDate,
          inspectionTime: inspectionTime || null,
          inspectorName: inspectorName,
          items: items,
          note: note || null
        })
      });
      await loadList();
      const pendingAction = getPendingAction();
      if (pendingAction?.id) {
        clearPendingAction();
        if (pendingAction.type === "details") {
          await openDetail(pendingAction.id);
        } else if (pendingAction.type === "inspect") {
          if (canManage) {
            openInspectModal(pendingAction.id);
          } else {
            await openDetail(pendingAction.id);
          }
        } else if (pendingAction.type === "edit") {
          const current = state.items.find(item => String(item[config.idField]) === String(pendingAction.id));
          if (canManage && current) {
            openItemModal(current);
          } else if (current) {
            await openDetail(pendingAction.id);
          }
        }
      }
      if (state.detailModal && document.getElementById("detailModal")?.classList.contains("show")) {
        await openDetail(targetId);
      }
      const detail = await apiFetch(config.apiBase + "/" + encodeURIComponent(targetId), { method: "GET" });
      renderEditableHistory(detail.inspections || []);
    } finally {
      button.disabled = false;
    }
  }

  function bindMapEvents() {
    const planWrap = document.getElementById("planWrap");
    const markerLayer = document.getElementById("mapMarkerLayer");
    if (!planWrap || !markerLayer) return;
    planWrap.addEventListener("click", function (event) {
      if (event.target.closest(".map-marker")) return;
      const display = getPlanDisplayRect(planWrap);
      if (!display) return;
      const px = event.clientX - display.rect.left;
      const py = event.clientY - display.rect.top;
      const x = ((px - display.offsetX) / display.drawWidth) * 100;
      const y = ((py - display.offsetY) / display.drawHeight) * 100;
      state.selectedCoord = { x: Math.max(0, Math.min(100, x)), y: Math.max(0, Math.min(100, y)) };
      setSelectedCoord(state.selectedCoord.x, state.selectedCoord.y);
      renderMapMarkers();
    });
    markerLayer.addEventListener("click", function (event) {
      const marker = event.target.closest(".map-marker");
      if (!marker) return;
      event.preventDefault();
      const kind = marker.dataset.kind;
      const id = marker.dataset.id;
      const source = kind === "receiver" ? state.peers.receivers : state.peers.pumps;
      const found = source.find(item => String(kind === "receiver" ? item.receiverId : item.pumpId) === String(id));
      if (!found) return;
      state.selectedCoord = { x: Number(found.x), y: Number(found.y) };
      setSelectedCoord(state.selectedCoord.x, state.selectedCoord.y);
      renderMapMarkers();
    });
  }

  function bindEvents() {
    document.getElementById("searchForm")?.addEventListener("submit", async function (event) {
      event.preventDefault();
      await loadList();
    });
    document.getElementById("btnSearch")?.addEventListener("click", loadList);
    document.getElementById("btnReset")?.addEventListener("click", async function () {
      document.getElementById("searchInput").value = "";
      const filterDateFrom = document.getElementById("filterDateFrom");
      const filterDateTo = document.getElementById("filterDateTo");
      const today = new Date().toISOString().slice(0, 10);
      if (filterDateFrom) filterDateFrom.value = today;
      if (filterDateTo) filterDateTo.value = today;
      state.activeStatusFilter = null;
      await loadList();
    });
    document.getElementById("btnExportAll")?.addEventListener("click", exportAllCsv);
    document.getElementById("btnStatusWaiting")?.addEventListener("click", function () {
      state.activeStatusFilter = state.activeStatusFilter === "waiting" ? null : "waiting";
      renderSummary();
      renderTable();
    });
    document.getElementById("btnStatusMaintenance")?.addEventListener("click", function () {
      state.activeStatusFilter = state.activeStatusFilter === "maintenance" ? null : "maintenance";
      renderSummary();
      renderTable();
    });
    document.getElementById("btnStatusFaulty")?.addEventListener("click", function () {
      state.activeStatusFilter = state.activeStatusFilter === "faulty" ? null : "faulty";
      renderSummary();
      renderTable();
    });
    document.getElementById("addBtn")?.addEventListener("click", function () {
      if (!canManage) return;
      openItemModal(null);
    });
    document.getElementById("saveBtn")?.addEventListener("click", async function () {
      try {
        await saveItem();
      } catch (error) {
        alert(error.message || "저장에 실패했습니다.");
      }
    });
    document.getElementById("detailInspectBtn")?.addEventListener("click", function () {
      if (!canManage) return;
      if (state.selectedDetailId) openInspectModal(state.selectedDetailId);
    });
    document.getElementById("detailEditBtn")?.addEventListener("click", function () {
      if (!canManage) return;
      const current = state.items.find(item => String(item[config.idField]) === String(state.selectedDetailId));
      if (current) {
        state.detailModal.hide();
        openItemModal(current);
      }
    });
    document.getElementById("detailExportBtn")?.addEventListener("click", downloadInspectionCsv);
    document.getElementById("inspectSaveBtn")?.addEventListener("click", async function () {
      try {
        await saveInspection();
      } catch (error) {
        alert(error.message || "점검 저장에 실패했습니다.");
      }
    });
    document.getElementById("listBody")?.addEventListener("click", async function (event) {
      const inspectBtn = event.target.closest(".js-inspect");
      if (inspectBtn) {
        if (!canManage) return;
        event.stopPropagation();
        openInspectModal(inspectBtn.dataset.id);
        return;
      }
      const editBtn = event.target.closest(".js-edit");
      if (editBtn) {
        if (!canManage) return;
        event.stopPropagation();
        const current = state.items.find(item => String(item[config.idField]) === String(editBtn.dataset.id));
        openItemModal(current || null);
        return;
      }
      const deleteBtn = event.target.closest(".js-delete");
      if (deleteBtn) {
        if (!canManage) return;
        event.stopPropagation();
        try {
          await deleteItem(deleteBtn.dataset.id);
        } catch (error) {
          alert(error.message || "삭제에 실패했습니다.");
        }
        return;
      }
      const row = event.target.closest("tr.clickable-row");
      if (row && !event.target.closest("button,a,input,select,textarea,label")) {
        await openDetail(row.dataset.id);
      }
    });
    document.getElementById("itemHistoryBody")?.addEventListener("click", async function (event) {
      const saveBtn = event.target.closest(".js-history-save");
      if (!saveBtn) return;
      if (!canManage) return;
      try {
        await saveHistoryRow(saveBtn);
      } catch (error) {
        alert(error.message || "점검 이력 저장에 실패했습니다.");
      }
    });
    bindMapEvents();
    window.addEventListener("resize", function () {
      renderMapMarkers();
      rerenderDetailMap();
    });
    document.getElementById("detailModal")?.addEventListener("shown.bs.modal", rerenderDetailMap);
    document.getElementById("itemModal")?.addEventListener("hidden.bs.modal", function () {
      postEmbedClose("fireweb:equipment-edit-close");
    });
    document.getElementById("inspectModal")?.addEventListener("hidden.bs.modal", function () {
      postEmbedClose("fireweb:equipment-inspect-close");
    });
    document.getElementById("detailModal")?.addEventListener("hidden.bs.modal", function () {
      postEmbedClose("fireweb:equipment-details-close");
    });
  }

  async function init() {
    if (!ensureAuthenticated()) return;
    state.itemModal = new bootstrap.Modal(document.getElementById("itemModal"));
    state.detailModal = new bootstrap.Modal(document.getElementById("detailModal"));
    state.inspectModal = new bootstrap.Modal(document.getElementById("inspectModal"));
    const today = new Date().toISOString().slice(0, 10);
    const filterDateFrom = document.getElementById("filterDateFrom");
    const filterDateTo = document.getElementById("filterDateTo");
    if (filterDateFrom && !filterDateFrom.value) filterDateFrom.value = today;
    if (filterDateTo && !filterDateTo.value) filterDateTo.value = today;
    document.getElementById("pageTitle").textContent = config.title;
    document.getElementById("pageLead").textContent = config.title + "의 위치와 점검 이력을 관리합니다.";
    document.getElementById("singularLabel").textContent = config.singular;
    const addBtn = document.getElementById("addBtn");
    if (addBtn && !canManage) addBtn.style.display = "none";
    const exportAllBtn = document.getElementById("btnExportAll");
    if (exportAllBtn && !canManage) exportAllBtn.style.display = "none";
    applyEmbeddedShell();
    bindEvents();
    try {
      await loadPeers();
      await loadList();
      await handleInitialAction();
    } catch (error) {
      alert(error.message || "목록을 불러오지 못했습니다.");
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
