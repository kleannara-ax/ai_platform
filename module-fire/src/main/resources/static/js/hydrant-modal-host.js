(function(){
  const HOST_ID = 'fwHydModalHost';
  const FRAME_ID = 'fwHydModalFrame';
  const SHELL_ID = 'fwHydModalShell';
  const CLOSE_ID = 'fwHydModalClose';
  const CLOSE_RE = /^fireweb:(outdoor-edit-close|hydrant-(details|inspect|edit)-close|hydrant-inspect-saved)$/;
  let activeRequest = null;
  let closingReason = null;

  function notifyClose(request, reason) {
    try {
      request.onClose?.({
        reason,
        mode: request.mode,
        options: request.options
      });
    } catch (err) {
      console.error(err);
    }

    document.dispatchEvent(new CustomEvent('fireweb:hydrant-modal-close', {
      detail: {
        reason,
        mode: request.mode,
        options: request.options
      }
    }));
  }

  function ensureHost() {
    let modalEl = document.getElementById(HOST_ID);
    if (modalEl) return modalEl;

    modalEl = document.createElement('div');
    modalEl.id = HOST_ID;
    modalEl.className = 'modal fade action-embed-modal';
    modalEl.tabIndex = -1;
    modalEl.setAttribute('aria-hidden', 'true');
    modalEl.style.background = 'transparent';
    modalEl.style.padding = '8px';
    modalEl.style.overflow = 'hidden';
    modalEl.innerHTML = [
      '<div class="modal-dialog modal-xl" style="margin:0 auto;max-width:min(1240px, calc(100vw - 16px));height:min(94vh, calc(100vh - 16px));overflow:hidden;">',
      `  <div id="${SHELL_ID}" class="modal-content" style="position:relative;height:100%;background:transparent;border:0;box-shadow:none;border-radius:18px;overflow:hidden;">`,
      `    <button type="button" id="${CLOSE_ID}" aria-label="닫기" style="position:absolute;top:8px;right:8px;z-index:2;width:30px;height:30px;border:0;border-radius:999px;background:transparent;color:#475569;font-size:20px;line-height:1;cursor:pointer;">×</button>`,
      '    <div class="modal-body" style="padding:0;height:100%;background:transparent;overflow:hidden;">',
      `      <iframe id="${FRAME_ID}" title="소화전 작업" style="width:100%;height:100%;border:0;background:transparent;display:block;"></iframe>`,
      '    </div>',
      '  </div>',
      '</div>'
    ].join('');
    document.body.appendChild(modalEl);

    modalEl.addEventListener('hidden.bs.modal', () => {
      const frame = document.getElementById(FRAME_ID);
      if (frame) frame.src = 'about:blank';

      if (!activeRequest) return;
      const request = activeRequest;
      activeRequest = null;
      notifyClose(request, closingReason || 'dismiss');
      closingReason = null;
    });

    window.addEventListener('message', (ev) => {
      const msg = String(ev.data || '');
      if (!CLOSE_RE.test(msg)) return;

      closingReason = msg;
      bootstrap.Modal.getOrCreateInstance(modalEl).hide();
    });

    document.getElementById(CLOSE_ID)?.addEventListener('click', () => {
      closingReason = 'dismiss';
      bootstrap.Modal.getOrCreateInstance(modalEl).hide();
    });
    return modalEl;
  }

  function buildQuery(options) {
    const query = new URLSearchParams();
    const mode = options?.mode || 'details';

    if (mode === 'inspect') {
      query.set('embedInspect', '1');
      if (options?.id != null) query.set('inspect', String(options.id));
      return query;
    }

    if (mode === 'details') {
      query.set('embedDetails', '1');
      if (options?.id != null) query.set('details', String(options.id));
      return query;
    }

    query.set('embedEdit', '1');
    if (options?.add) {
      query.set('add', '1');
    } else if (options?.id != null) {
      query.set('edit', String(options.id));
    }

    ['hydrantType', 'operationType', 'buildingId', 'floorId', 'buildingName', 'floorName', 'x', 'y', 'noMap']
      .forEach((key) => {
        const value = options?.[key];
        if (value !== undefined && value !== null && value !== '') {
          query.set(key, String(value));
        }
      });

    return query;
  }

  function open(options) {
    const modalEl = ensureHost();
    const frame = document.getElementById(FRAME_ID);
    if (!frame) return;

    closingReason = null;
    frame.src = `/hydrants.html?${buildQuery(options).toString()}`;
    activeRequest = {
      mode: options?.mode || 'details',
      options: { ...(options || {}) },
      onClose: typeof options?.onClose === 'function' ? options.onClose : null
    };

    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  window.FireWebHydrantModal = { open };
})();
