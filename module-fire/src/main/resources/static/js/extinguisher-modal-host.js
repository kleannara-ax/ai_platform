(function(){
  const HOST_ID = 'fwExtModalHost';
  const FRAME_ID = 'fwExtModalFrame';
  const CLOSE_RE = /^fireweb:ext-(edit|details|inspect)-close$/;
  let activeRequest = null;

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
    modalEl.className = 'modal fade action-embed-modal';
    modalEl.tabIndex = -1;
    modalEl.setAttribute('aria-hidden', 'true');
    modalEl.innerHTML = [
      '<div class="modal-dialog modal-xl modal-dialog-centered modal-dialog-scrollable">',
      '  <div class="modal-content" style="background:transparent;border:0;box-shadow:none;border-radius:14px;overflow:hidden;">',
      '    <div class="modal-body" style="padding:0;background:transparent;">',
      `      <iframe id="${FRAME_ID}" title="소화기 작업" style="width:100%;height:min(88vh,1100px);border:0;background:transparent;display:block;"></iframe>`,
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
      notifyClose(request, 'dismiss');
    });

    window.addEventListener('message', (ev) => {
      const msg = String(ev.data || '');
      if (!CLOSE_RE.test(msg)) return;

      const request = activeRequest;
      activeRequest = null;
      bootstrap.Modal.getOrCreateInstance(modalEl).hide();
      if (request) notifyClose(request, msg);
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

    frame.src = `/extinguishers.html?${buildQuery(options).toString()}`;
    activeRequest = {
      mode: options?.mode || 'details',
      options: { ...(options || {}) },
      onClose: typeof options?.onClose === 'function' ? options.onClose : null
    };

    bootstrap.Modal.getOrCreateInstance(modalEl).show();
  }

  window.FireWebExtinguisherModal = { open };
})();
