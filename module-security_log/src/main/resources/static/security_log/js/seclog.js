/* =========================================================================
 * SECURITY_LOG 공통 프론트엔드 스크립트 (무빌드: 순수 JS)
 *  - 플랫폼 로그인 세션(JWT, localStorage 'fireweb_user')을 재사용한다
 *    (module-safety 의 safety.js / module-kims 의 kims.js 와 동일 패턴)
 *  - 데이터는 항상 /security_log-api/** 에서 가져온다
 * ========================================================================= */
const SECLOG = (() => {
  const API = '/security_log-api';
  const PLATFORM_KEY = 'fireweb_user';

  const session = () => {
    try { return JSON.parse(localStorage.getItem(PLATFORM_KEY) || '{}') || {}; }
    catch (e) { return {}; }
  };
  const getToken = () => session().token || '';
  const getRoles = () => {
    const s = session();
    const list = Array.isArray(s.roles) && s.roles.length ? s.roles : (s.role ? [s.role] : []);
    return list.map(r => String(r).replace(/^ROLE_/, ''));
  };
  const hasAny = (...roles) => getRoles().some(r => roles.includes(r));
  const isAdmin = () => hasAny('ADMIN');
  const canWrite = () => hasAny('ADMIN', 'MANAGER');

  const requireAuth = () => {
    if (!getToken()) {
      document.body.innerHTML =
        '<div style="padding:60px;text-align:center;font-family:sans-serif;color:#64748b">' +
        '<h3>로그인이 필요합니다</h3><p>플랫폼에 로그인한 뒤 메뉴에서 다시 열어 주세요.</p>' +
        '<a href="/" target="_top">플랫폼으로 이동</a></div>';
      return false;
    }
    return true;
  };

  async function parse(res) {
    if (res.status === 401) throw new Error('로그인이 만료되었습니다. 플랫폼에서 다시 로그인하세요. (HTTP 401)');
    if (res.status === 403) throw new Error('이 작업을 수행할 권한이 없습니다. (HTTP 403)');
    if (res.status === 413) throw new Error('파일이 너무 커서 서버가 받지 못했습니다 (HTTP 413). 웹서버 업로드 크기 제한을 확인하세요.');
    let json = null;
    try { json = await res.json(); } catch (e) { /* 본문 없음 */ }
    if (!res.ok || (json && json.success === false)) {
      throw new Error((json && json.message) ? json.message : ('요청 실패 (HTTP ' + res.status + ')'));
    }
    return json ? json.data : null;
  }

  async function api(path, { method = 'GET', body = null } = {}) {
    const headers = { 'Authorization': 'Bearer ' + getToken() };
    if (body !== null) headers['Content-Type'] = 'application/json; charset=utf-8';
    const res = await fetch(API + path, {
      method, headers, cache: 'no-store',
      body: body !== null ? JSON.stringify(body) : undefined,
    });
    return parse(res);
  }

  async function upload(path, formData) {
    const res = await fetch(API + path, {
      method: 'POST', headers: { 'Authorization': 'Bearer ' + getToken() }, body: formData,
    });
    return parse(res);
  }

  // ---- 표시 유틸 ----
  const esc = (v) => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  const fmtDt = (v) => v ? String(v).replace('T', ' ').slice(0, 16) : '-';
  const fmtNum = (n) => (n ?? 0).toLocaleString('ko-KR');
  const fmtSize = (b) => {
    if (!b && b !== 0) return '-';
    if (b < 1024) return b + ' B';
    if (b < 1024 * 1024) return (b / 1024).toFixed(1) + ' KB';
    return (b / 1024 / 1024).toFixed(1) + ' MB';
  };
  const fmtYm = (ym) => ym && ym.length === 6 ? ym.slice(0, 4) + '년 ' + ym.slice(4) + '월' : (ym || '');
  const currentYm = () => { const d = new Date(Date.now() + 9 * 3600000); return d.getUTCFullYear() + String(d.getUTCMonth() + 1).padStart(2, '0'); };

  // 위험도: 탐지 룰마다 관리자가 지정. 아래 의미·예시·권장 대응은 룰 위험도를 정할 때 쓰는 공통 기준 (README '위험도 기준')
  const SEVERITY = {
    CRITICAL: { label: '심각', cls: 'sev-critical',
      meaning: '침해가 이미 일어났거나 로그·증적이 훼손될 수 있는 행위',
      examples: '로그 삭제·감사 설정 변경, 해킹도구·악성코드 흔적, SQL Injection, 보안그룹 전체 개방',
      action: '당일 확인, 침해사고 대응 절차 검토' },
    HIGH: { label: '높음', cls: 'sev-high',
      meaning: '공격 시도이거나 계정·권한·데이터에 영향을 주는 중요 변경',
      examples: '반복 인증 실패(무차별 대입), 관리자 계정·권한 변경, 허용 외 IP 접속 시도, DB 구조 변경·대량 반출',
      action: '3일 이내 확인, 정상 업무인지 담당자 소명' },
    MEDIUM: { label: '보통', cls: 'sev-medium',
      meaning: '정상 업무일 수 있지만 기록과 확인이 필요한 행위',
      examples: '특권 계정 로그인, 보안 정책 변경, 데이터 다운로드·반출, 취약점 스캐너 접근',
      action: '월 점검 때 검토, 승인·작업 내역과 대조' },
    LOW: { label: '낮음', cls: 'sev-low',
      meaning: '참고용 이벤트 (단독으로는 위험이 낮음)',
      examples: '심야 시간대 접근, 사용자 정보 변경, 웹 서버 오류(5xx) 다발',
      action: '추이 확인, 반복되면 위험도 상향 검토' },
  };
  const REVIEW = {
    PENDING: { label: '미검토', cls: 'rv-pending' },
    CONFIRMED: { label: '이상징후 확인', cls: 'rv-confirmed' },
    RESOLVED: { label: '조치완료', cls: 'rv-resolved' },
    FALSE_POSITIVE: { label: '오탐', cls: 'rv-fp' },
  };
  const STATUS = {
    ANALYZED: { label: '검토대기', cls: 'st-analyzed' },
    REVIEWED: { label: '검토완료', cls: 'st-reviewed' },
  };
  const LOG_TYPES = [
    ['LINUX', 'Linux/Unix 시스템'], ['WINDOWS', 'Windows 이벤트'], ['WEB', '웹서버'], ['WAS', 'WAS'],
    ['DB', 'DBMS'], ['NETWORK', '방화벽/네트워크'], ['APP', '업무 애플리케이션'],
    ['SECURITY', '정보보호솔루션 (VPN/WAF/DLP/EPP)'], ['CLOUD', '클라우드 콘솔 (AWS CloudTrail)'], ['ETC', '기타'],
  ];
  // 접속 행위 위험 표시
  const RISK_FLAGS = {
    FAIL_MANY: { label: '로그인 실패 5회↑', cls: 'sev-high' },
    OFF_HOUR: { label: '업무시간 외 접속', cls: 'sev-medium' },
    EXTERNAL_IP: { label: '외부 IP', cls: 'sev-high' },
    MULTI_IP: { label: '다중 IP(3개↑)', cls: 'sev-low' },
    PRIV_ACCOUNT: { label: '특권/공용 계정', cls: 'sev-critical' },
    IP_DENIED: { label: '허용 외 IP 접속 시도', cls: 'sev-critical' },
  };
  const riskBadges = (list) => (list || []).length === 0 ? '<span class="text-muted-m">-</span>'
    : list.map(f => `<span class="badge-sev ${RISK_FLAGS[f]?.cls || 'sev-none'} me-1">${RISK_FLAGS[f]?.label || f}</span>`).join('');

  // ---- 상단 탭 (모든 목록 화면 공통) ----
  const TABS = [
    ['dashboard', 'dashboard.html', 'fa-gauge-high', '대시보드'],
    ['status', 'status.html', 'fa-table-cells', '월별 점검 현황'],
    ['logs', 'index.html', 'fa-list', '접속·보안 로그'],
    ['systems', 'systems.html', 'fa-server', '점검 대상 시스템'],
    ['admin', 'rules.html', 'fa-gear', '관리'],
  ];
  // 관리 페이지 하위 메뉴
  const ADMIN_MENU = [
    ['rules', 'rules.html', 'fa-sliders', '탐지 룰셋 편집'],
    ['internal-ip', 'internal-ip.html', 'fa-network-wired', '내부 IP 설정'],
  ];
  /** 서버 시각(한국시간, 시간대 표기 없음) → epoch ms. 브라우저 시간대와 관계없이 정확하게 계산 */
  const kstTime = (v) => {
    const t = String(v).replace(' ', 'T');
    return new Date(/[zZ]|[+-]\d{2}:?\d{2}$/.test(t) ? t : t + '+09:00').getTime();
  };
  /** 지금 한국시간 'YYYY-MM-DD' */
  const kstToday = () => new Date(Date.now() + 9 * 3600000).toISOString().slice(0, 10);
  const ago = (v) => {
    if (!v) return '-';
    const d = (Date.now() - kstTime(v)) / 1000;
    if (d < 60) return '방금';
    if (d < 3600) return Math.floor(d / 60) + '분 전';
    if (d < 86400) return Math.floor(d / 3600) + '시간 전';
    return Math.floor(d / 86400) + '일 전';
  };
  /**
   * @param active 상단 탭 키
   * @param sub    관리 페이지 하위 메뉴 키 (active = 'admin' 일 때)
   */
  function renderTabs(active, sub) {
    const nav = document.getElementById('slTabs');
    if (!nav) return;
    nav.innerHTML = TABS.map(([k, href, ic, label]) =>
      `<a href="${href}" class="${k === active ? 'active' : ''}"${k === active ? ' aria-current="page"' : ''}><i class="fas ${ic}"></i> ${label}</a>`).join('');
    if (active === 'admin') {
      const sn = document.createElement('nav');
      sn.className = 'sl-subnav';
      sn.id = 'adminMenu';
      sn.setAttribute('aria-label', '관리 메뉴');
      sn.innerHTML = '<span class="sn-title"><i class="fas fa-gear"></i> 관리</span>' + ADMIN_MENU.map(([k, href, ic, label]) =>
        `<a href="${href}" class="${k === sub ? 'active' : ''}"${k === sub ? ' aria-current="page"' : ''}><i class="fas ${ic}"></i> ${label}</a>`).join('');
      nav.after(sn);
    }
  }

  // ---- 점검 대상 시스템 (select 채우기) ----
  async function fillSystemSelect(sel, { selected = null, withDirect = true } = {}) {
    const list = await api('/systems?activeOnly=true');
    const groups = {};
    list.forEach(s => (groups[s.category] = groups[s.category] || []).push(s));
    let h = '<option value="">시스템 선택</option>';
    Object.entries(groups).forEach(([cat, arr]) => {
      h += `<optgroup label="${esc(cat)}">` + arr.map(s =>
        `<option value="${s.systemId}" data-logtype="${s.defaultLogType}" ${String(selected) === String(s.systemId) ? 'selected' : ''}>${esc(s.systemName)}</option>`).join('') + '</optgroup>';
    });
    if (withDirect) h += '<option value="__DIRECT__">직접 입력 (목록에 없는 시스템)</option>';
    sel.innerHTML = h;
    return list;
  }

  const shiftYm = (ym, d) => {
    let y = +ym.slice(0, 4), m = +ym.slice(4) + d;
    if (m < 1) { m = 12; y--; } if (m > 12) { m = 1; y++; }
    return y + String(m).padStart(2, '0');
  };
  // data-sev: 마우스를 올리면 위험도 기준 팝오버 표시
  const sevBadge = (s) => s ? `<span class="badge-sev ${SEVERITY[s]?.cls || ''}" data-sev="${s}">${SEVERITY[s]?.label || s}</span>` : '<span class="badge-sev sev-none" data-sev-help>탐지없음</span>';
  const reviewBadge = (s) => `<span class="badge-rv ${REVIEW[s]?.cls || ''}">${REVIEW[s]?.label || s}</span>`;
  const statusBadge = (s) => `<span class="badge-st ${STATUS[s]?.cls || ''}">${STATUS[s]?.label || s}</span>`;

  // ---- 토스트 ----
  function toast(msg, type = 'success') {
    let box = document.getElementById('toastBox');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toastBox';
      box.className = 'toast-box';
      document.body.appendChild(box);
    }
    const el = document.createElement('div');
    el.className = 'toast-item toast-' + type;
    el.innerHTML = `<i class="fas ${type === 'error' ? 'fa-circle-exclamation' : 'fa-circle-check'}"></i> ${esc(msg)}`;
    box.appendChild(el);
    setTimeout(() => { el.classList.add('hide'); setTimeout(() => el.remove(), 300); }, type === 'error' ? 5000 : 2500);
  }

  const qs = (name) => new URLSearchParams(location.search).get(name);
  const go = (page, params) => {
    const q = params ? '?' + new URLSearchParams(params).toString() : '';
    location.href = page + q;
  };

  // ---- 위험도 기준 팝오버 ([data-sev] 배지·카드, [data-sev-help] 머리글 아이콘에 마우스를 올리거나 포커스) ----
  const SEV_ORDER = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW'];
  const SEV_SEL = '[data-sev],[data-sev-help]';
  /** 머리글 옆 (i) 아이콘 */
  const sevHelp = (label = '') => `${label}<i class="fas fa-circle-info sev-help-ic" data-sev-help tabindex="0" role="button" aria-label="위험도 기준 보기"></i>`;
  const sp = { el: null, target: null, showTimer: null, hideTimer: null, rules: null, loading: null };
  function loadSevRules() {
    if (!sp.loading) {
      sp.loading = api('/rules?useYn=Y&size=500')
        .then(d => { sp.rules = (d && d.content) || []; })
        .catch(() => { sp.rules = []; sp.failed = true; });
    }
    return sp.loading;
  }
  function sevPopHtml(active, counts) {
    const by = {};
    (sp.rules || []).forEach(r => (by[r.severity] = by[r.severity] || []).push(r));
    const rows = SEV_ORDER.map((k, i) => {
      const v = SEVERITY[k], list = by[k] || [], on = k === active;
      let ruleInfo = '';
      if (on) {
        ruleInfo = sp.rules === null ? '<div class="sp-rules text-muted-m">적용 룰 불러오는 중...</div>'
          : sp.failed ? '<div class="sp-rules text-muted-m">룰 목록을 불러오지 못했습니다.</div>'
          : list.length === 0 ? '<div class="sp-rules text-muted-m">이 위험도로 지정된 사용 중 룰이 없습니다.</div>'
          : `<div class="sp-rules"><span class="sp-rules-t">이 위험도로 지정된 룰 (사용 중 ${list.length}개)</span><ul>${list.slice(0, 6).map(r =>
              `<li>${esc(r.ruleName)}${r.thresholdCount > 1 ? ` <em>${fmtNum(r.thresholdCount)}건 이상</em>` : ''}${r.scope === 'SYSTEM' ? ` <em>${esc(r.systemName || '시스템')} 전용</em>` : ''}</li>`).join('')}</ul>${list.length > 6 ? `<span class="text-muted-m">외 ${list.length - 6}개</span>` : ''}</div>`;
      }
      return `<div class="sp-row${on ? ' active' : ''}">
        <div class="sp-lv"><span class="badge-sev ${v.cls}">${v.label}</span>${sp.rules === null || sp.failed ? '' : `<small>룰 ${list.length}개</small>`}${counts ? `<small class="sp-cnt">이 항목 ${fmtNum(counts[i] || 0)}건</small>` : ''}</div>
        <div class="sp-body"><b>${v.meaning}</b><div class="sp-ex">예) ${v.examples}</div>
          <div class="sp-act"><i class="fas fa-clock"></i> 권장 대응: ${v.action}</div>${ruleInfo}</div></div>`;
    }).join('');
    return `<div class="sp-head"><i class="fas fa-scale-balanced"></i> 위험도 기준</div>${rows}
      <div class="sp-foot">
        <div><i class="fas fa-circle-info"></i> 위험도는 <b>탐지 룰마다 관리자가 지정</b>하고, 탐지 건은 그 룰의 위험도를 그대로 받습니다 (탐지 시점 값 보관).</div>
        <div>· 업로드의 <b>최고 위험도</b> = 그 파일 탐지 건 중 가장 높은 위험도 · 임계 건수가 있는 룰은 그 건수 이상일 때만 탐지</div>
        <div>· 접속 행위의 <b>위험 표시</b>(로그인 실패 5회↑, 외부 IP 등)는 위험도와 별개입니다.</div>
        <a href="rules.html" class="sp-link">관리 › 탐지 룰셋 편집에서 룰별 위험도 보기 <i class="fas fa-angle-right"></i></a>
      </div>`;
  }
  function placeSevPop() {
    const t = sp.target, p = sp.el, m = 8;
    if (!t || !p) return;
    const r = t.getBoundingClientRect();
    p.style.left = '0px'; p.style.top = '0px';
    const w = p.offsetWidth, h = p.offsetHeight, vw = window.innerWidth, vh = window.innerHeight;
    let top = r.bottom + 6;
    if (top + h > vh - m) top = Math.max(m, r.top - h - 6);
    const left = Math.max(m, Math.min(r.left + r.width / 2 - w / 2, vw - w - m));
    p.style.left = left + 'px'; p.style.top = top + 'px';
  }
  function renderSevPop() {
    const t = sp.target;
    if (!t) return;
    const counts = t.dataset.sevCounts ? t.dataset.sevCounts.split(',').map(Number) : null;
    sp.el.innerHTML = sevPopHtml(t.dataset.sev || null, counts);
    sp.el.classList.add('show');
    placeSevPop();
  }
  function showSevPop(t) {
    clearTimeout(sp.hideTimer);
    if (!sp.el) {
      sp.el = document.createElement('div');
      sp.el.id = 'sevPop';
      sp.el.className = 'sev-pop';
      sp.el.setAttribute('role', 'tooltip');
      document.body.appendChild(sp.el);
      sp.el.addEventListener('mouseenter', () => clearTimeout(sp.hideTimer));
      sp.el.addEventListener('mouseleave', hideSevPopSoon);
    }
    if (sp.target && sp.target !== t) sp.target.removeAttribute('aria-describedby');
    sp.target = t;
    t.setAttribute('aria-describedby', 'sevPop');
    renderSevPop();
    if (sp.rules === null) loadSevRules().then(() => { if (sp.target === t) renderSevPop(); });
  }
  function hideSevPop() {
    clearTimeout(sp.showTimer); clearTimeout(sp.hideTimer);
    if (sp.el) sp.el.classList.remove('show');
    if (sp.target) sp.target.removeAttribute('aria-describedby');
    sp.target = null;
  }
  function hideSevPopSoon() { clearTimeout(sp.hideTimer); sp.hideTimer = setTimeout(hideSevPop, 180); }
  const inPop = (n) => !!(n && sp.el && sp.el.contains(n));
  document.addEventListener('mouseover', (e) => {
    const t = e.target.closest ? e.target.closest(SEV_SEL) : null;
    if (!t) return;
    clearTimeout(sp.hideTimer);
    if (t === sp.target) return;
    clearTimeout(sp.showTimer);
    sp.showTimer = setTimeout(() => showSevPop(t), 120);
  });
  document.addEventListener('mouseout', (e) => {
    const t = e.target.closest ? e.target.closest(SEV_SEL) : null;
    if (!t || (e.relatedTarget && (t.contains(e.relatedTarget) || inPop(e.relatedTarget)))) return;
    clearTimeout(sp.showTimer);
    if (sp.target) hideSevPopSoon();
  });
  document.addEventListener('focusin', (e) => {
    const t = e.target.closest ? e.target.closest(SEV_SEL) : null;
    if (t) showSevPop(t);
  });
  document.addEventListener('focusout', (e) => {
    const t = e.target.closest ? e.target.closest(SEV_SEL) : null;
    if (t && !inPop(e.relatedTarget)) hideSevPopSoon();
  });
  // 터치 화면: (i) 아이콘을 누르면 열고 닫기
  document.addEventListener('click', (e) => {
    const t = e.target.closest ? e.target.closest('.sev-help-ic') : null;
    if (!t) return; // 배지·막대는 행 클릭(상세 이동)을 막지 않음
    e.preventDefault(); e.stopPropagation();
    if (sp.target === t && sp.el.classList.contains('show')) hideSevPop(); else showSevPop(t);
  }, true);
  document.addEventListener('keydown', (e) => { if (e.key === 'Escape' && sp.target) hideSevPop(); });
  window.addEventListener('scroll', (e) => { if (sp.target && !inPop(e.target)) hideSevPop(); }, true);
  window.addEventListener('resize', () => { if (sp.target) hideSevPop(); });

  return {
    kstTime, kstToday,
    api, upload, requireAuth, isAdmin, canWrite, getRoles, session,
    esc, fmtDt, fmtNum, fmtSize, fmtYm, currentYm,
    SEVERITY, REVIEW, STATUS, LOG_TYPES, sevBadge, sevHelp, reviewBadge, statusBadge,
    RISK_FLAGS, riskBadges,
    renderTabs, fillSystemSelect, shiftYm, ago,
    toast, qs, go,
  };
})();
