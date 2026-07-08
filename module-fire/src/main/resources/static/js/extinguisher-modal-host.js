(function(){
  const HOST_ID = 'fwExtModalHost';
  const FRAME_ID = 'fwExtModalFrame';
  const CLOSE_ID = 'fwExtModalClose';
  const CLOSE_RE = /^fireweb:ext-(edit|details|inspect)-close$/;
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

    document.dispatchEvent(new CustomEvent('fireweb:ext-modal-close', {
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
    modalEl.className = 'action-embed-host action-embed-modal';
    modalEl.tabIndex = -1;
    modalEl.setAttribute('aria-hidden', 'true');
    Object.assign(modalEl.style, {
      position: 'fixed',
      inset: '0',
      zIndex: '1055',
      display: 'none',
      width: '100vw',
      height: '100vh',
      padding: '8px',
      overflow: 'hidden',
      background: 'rgba(17,24,39,.35)',
      backdropFilter: 'blur(4px)',
      boxSizing: 'border-box'
    });
    modalEl.innerHTML = [
      '<div class="modal-dialog modal-xl" style="margin:0;width:100%;max-width:none;height:100%;overflow:hidden;pointer-events:none;">',
      `  <div class="modal-content" style="position:relative;width:100%;height:100%;background:transparent;border:0;box-shadow:none;border-radius:0;overflow:hidden;pointer-events:none;">`,
      '    <div class="modal-body" style="padding:0;width:100%;height:100%;background:transparent;overflow:hidden;pointer-events:none;">',
      `      <iframe id="${FRAME_ID}" title="소화기 작업" style="width:100%;height:100%;border:0;background:transparent;display:block;pointer-events:auto;"></iframe>`,
      '    </div>',
      '  </div>',
      '</div>'
    ].join('');
    document.body.appendChild(modalEl);

    function closeHost(reason = 'dismiss') {
      const frame = document.getElementById(FRAME_ID);
      modalEl.style.display = 'none';
      modalEl.setAttribute('aria-hidden', 'true');
      if (frame) frame.src = 'about:blank';

      if (!activeRequest) return;
      const request = activeRequest;
      activeRequest = null;
      notifyClose(request, reason);
      closingReason = null;
    }

    window.addEventListener('message', (ev) => {
      const msg = String(ev.data || '');
      if (!CLOSE_RE.test(msg)) return;

      closingReason = msg;
      closeHost(msg);
    });

    modalEl.addEventListener('click', (ev) => {
      if (ev.target === modalEl) closeHost('dismiss');
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

    ['buildingId', 'floorId', 'buildingName', 'floorName', 'x', 'y', 'noMap'].forEach((key) => {
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
    frame.src = `/extinguishers.html?${buildQuery(options).toString()}`;
    activeRequest = {
      mode: options?.mode || 'details',
      options: { ...(options || {}) },
      onClose: typeof options?.onClose === 'function' ? options.onClose : null
    };

    modalEl.style.display = 'block';
    modalEl.setAttribute('aria-hidden', 'false');
  }

  window.FireWebExtinguisherModal = { open };
})();
