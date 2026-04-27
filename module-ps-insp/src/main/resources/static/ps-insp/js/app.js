/**
 * PS 후면 지분 검사 도구 - Application JavaScript v8.6.1
 * Canvas-based threshold inspection pipeline
 *
 * 1단계: 이미지 업로드 & 전처리 (리사이즈, 크기검증)
 * 2단계: 캔버스 준비 & 그레이스케일 변환
 * 3단계: 임계값 이진화 (threshold)
 * 4단계: Connected Component 분석 (BFS)
 * 5단계: 마킹 결과 시각화
 * 6단계: 품질 지표 계산 & UI 카드 업데이트
 * 7단계: 수동 보정 (추가/제외)
 * 8단계: 결과 이미지 캡처 & 서버 전송
 * 9단계: 서버 저장
 * 10단계: MES 결과 전송
 * 11단계: 이력 조회 시 이미지 표시
 */

(function () {
  'use strict';

  // ══════════════════════════════════════════════
  // Constants & State
  // ══════════════════════════════════════════════
  var BASE = window.location.origin;
  var token = '';
  var urlUserId = '';   // USERID 파라미터 (MES 등 외부 연동 시 로그인 생략용)
  var currentHistoryPage = 0;
  var historyPageSize = 20;
  var currentDetailPage = 0;
  var detailPageSize = 20;

  // 이력 테이블 검색 조건 (날짜 + 바코드)
  var dtSearchParams = { indBcd: '', dateFrom: '', dateTo: '' };

  // Image processing state
  var grayscaleData = null;     // Uint8ClampedArray (brightness 0~255)
  var originalImageData = null; // original RGBA ImageData
  var canvasWidth = 0;
  var canvasHeight = 0;
  var autoComponents = [];      // detected components [{id, size, centroid, bbox, source:'auto'}]
  var manualComponents = [];    // manually added [{id, centroid, source:'manual'}]
  var removedAutoIds = new Set();
  var isManualMode = false;
  var inspectionStartedAt = null;
  var msrmDate = null;

  var MANUAL_COMPONENT_RADIUS = 8;

  // ── 한국 시간(KST) 유틸리티 ──
  function toKSTISOString(date) {
    if (!date) date = new Date();
    var kst = new Date(date.getTime() + (9 * 60 * 60 * 1000)); // UTC+9
    return kst.toISOString().replace('Z', '');
  }
  function nowKST() {
    return toKSTISOString(new Date());
  }

  var MAX_IMAGE_DIMENSION = 2200;
  var RESIZE_TARGET = 1600;
  var MAX_FILE_SIZE = 900 * 1024 * 1024; // 900MB
  var DEFAULT_THRESHOLD = 115;
  var THRESHOLD_STORAGE_KEY = 'jri_threshold';
  var THRESHOLD_DATE_KEY = 'jri_threshold_date'; // 임계값 저장 날짜 (YYYY-MM-DD)
  var DEFAULT_PPM_LIMIT = 0; // 0 = 비활성 (기준값 미설정)

  // 임계값 잠금 상태
  var thresholdLocked = true;
  var lastThreshold = DEFAULT_THRESHOLD;

  // PPM 기준값 (DB에서 로드하여 캐시)
  var _cachedPpmLimit = null; // null = 아직 로드하지 않음

  /**
   * PPM 기준값 조회 (캐시 우선, DB에서 비동기 로드)
   * 동기적으로 캐시된 값을 반환합니다.
   * 최초 호출 시 서버에서 로드합니다.
   */
  function getPpmLimit() {
    if (_cachedPpmLimit !== null) return _cachedPpmLimit;
    // 캐시 미스: localStorage 폴백 (서버 로드 전까지 임시)
    try {
      var v = localStorage.getItem('jri_ppm_limit');
      if (v !== null) { var n = parseFloat(v); if (!isNaN(n) && n > 0) return n; }
    } catch (e) { }
    return DEFAULT_PPM_LIMIT;
  }

  /**
   * 서버에서 PPM 기준값 로드 (비동기)
   * 앱 초기화 시 호출됩니다.
   */
  function loadPpmLimitFromServer() {
    api('GET', '/ps-insp-api/config/ppm-limit')
      .then(function (res) {
        if (res.success && res.data) {
          var limit = parseFloat(res.data.ppmLimit) || 0;
          _cachedPpmLimit = limit;
          // localStorage에도 동기화 (오프라인 폴백용)
          try { localStorage.setItem('jri_ppm_limit', String(limit)); } catch (e) { }
          console.log('[PS-INSP] PPM 기준값 서버에서 로드:', limit);
        }
      })
      .catch(function (e) {
        console.warn('[PS-INSP] PPM 기준값 서버 로드 실패 (localStorage 폴백 사용):', e.message);
      });
  }

  /**
   * PPM 기준값을 서버(DB)에 저장
   * 권한 검증은 서버에서 로그인 사용자 ID 기반으로 수행 (PPM_ADMIN 코드)
   * @param {number} val PPM 기준값
   * @returns {Promise}
   */
  function savePpmLimitToServer(val) {
    return api('POST', '/ps-insp-api/config/ppm-limit', {
      ppmLimit: val
    }).then(function (res) {
      if (res.success) {
        _cachedPpmLimit = val;
        // localStorage에도 동기화
        try { localStorage.setItem('jri_ppm_limit', String(val)); } catch (e) { }
        console.log('[PS-INSP] PPM 기준값 서버 저장 완료:', val);
      }
      return res;
    });
  }

  // ══════════════════════════════════════════════
  // PPM 권한자 관리 API
  // ══════════════════════════════════════════════

  /**
   * PPM 수정 권한자 목록 조회
   * @returns {Promise<{adminIds: string[], isAdmin: boolean, currentUser: string}>}
   */
  function loadPpmAdmins() {
    return api('GET', '/ps-insp-api/config/ppm-admins')
      .then(function (res) {
        if (res.success && res.data) return res.data;
        return { adminIds: [], isAdmin: false, currentUser: '' };
      })
      .catch(function () {
        return { adminIds: [], isAdmin: false, currentUser: '' };
      });
  }

  /**
   * PPM 수정 권한자 목록 업데이트
   * @param {string[]} adminIds 권한자 ID 배열
   * @returns {Promise}
   */
  function savePpmAdmins(adminIds) {
    return api('POST', '/ps-insp-api/config/ppm-admins', {
      adminIds: adminIds
    });
  }

  // ── localStorage 임계값 저장/복원 ──
  // 자정(00:00) 지나면 자동 리셋 → DEFAULT_THRESHOLD(115)
  function getTodayKST() {
    var now = new Date();
    var kst = new Date(now.getTime() + (9 * 60 * 60 * 1000));
    return kst.toISOString().substring(0, 10); // 'YYYY-MM-DD'
  }

  function saveThresholdToStorage(val) {
    try {
      localStorage.setItem(THRESHOLD_STORAGE_KEY, String(val));
      localStorage.setItem(THRESHOLD_DATE_KEY, getTodayKST());
    } catch (e) { }
  }

  function loadThresholdFromStorage() {
    try {
      var savedDate = localStorage.getItem(THRESHOLD_DATE_KEY);
      var today = getTodayKST();

      // 자정 리셋: 저장 날짜가 오늘이 아니면 임계값 초기화
      if (savedDate && savedDate !== today) {
        localStorage.removeItem(THRESHOLD_STORAGE_KEY);
        localStorage.removeItem(THRESHOLD_DATE_KEY);
        console.log('[PS-INSP] 자정 경과 → 임계값 리셋 (저장일: ' + savedDate + ', 오늘: ' + today + ')');
        return null;
      }

      var stored = localStorage.getItem(THRESHOLD_STORAGE_KEY);
      if (stored !== null) {
        var v = parseInt(stored);
        if (v >= 1 && v <= 255) return v;
      }
    } catch (e) { }
    return null; // 저장값 없음 → 호출부에서 DEFAULT_THRESHOLD 사용
  }

  // ══════════════════════════════════════════════
  // Token Extraction
  // ══════════════════════════════════════════════
  function extractToken() {
    var params = new URLSearchParams(window.location.search);
    if (params.has('_t')) token = params.get('_t');
    if (!token) {
      try {
        var raw = localStorage.getItem('fireweb_user');
        if (raw) { var u = JSON.parse(raw); if (u && u.token) token = u.token; }
      } catch (e) { }
    }
    // USERID 파라미터 추출 (MES 등 외부 시스템 연동 시 JWT 없이 인증)
    if (params.has('USERID')) urlUserId = params.get('USERID').trim();
  }

  // ══════════════════════════════════════════════
  // API Helper
  // ══════════════════════════════════════════════
  /**
   * API 호출 헬퍼
   * - JWT 토큰이 있으면 Authorization 헤더에 포함
   * - USERID 파라미터가 URL에 있으면 API 경로에 쿼리로 추가 (서버 자동 인증)
   */
  function api(method, path, body) {
    var opts = { method: method, headers: {} };
    if (token) opts.headers['Authorization'] = 'Bearer ' + token;
    if (body && !(body instanceof FormData)) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    } else if (body instanceof FormData) {
      opts.body = body;
    }

    // USERID 파라미터가 있고 JWT 토큰이 없으면 → API URL에 USERID 쿼리 추가
    var apiUrl = BASE + path;
    if (urlUserId && !token) {
      var sep = path.indexOf('?') >= 0 ? '&' : '?';
      apiUrl = BASE + path + sep + 'USERID=' + encodeURIComponent(urlUserId);
    }

    return fetch(apiUrl, opts).then(function (res) {
      var httpStatus = res.status;
      if (httpStatus === 401) {
        // USERID 모드에서는 "로그인" 안내 대신 사용자 ID 오류 안내
        if (urlUserId && !token) {
          toast('USERID 인증에 실패했습니다. 사용자 ID를 확인해주세요.', 'error');
        } else {
          toast('인증이 만료되었습니다. 다시 로그인해주세요.', 'error');
        }
        try { window.parent.postMessage({ type: 'FIRE_AUTH_EXPIRED' }, '*'); } catch (e) { }
        throw new Error('Unauthorized');
      }
      if (httpStatus === 403) {
        toast('접근 권한이 없습니다. 관리자에게 PS_INSP_MGMT 권한을 요청하세요.', 'error');
        throw new Error('Forbidden: PS_INSP_MGMT 권한 필요');
      }
      return res.json().then(function (json) {
        if (httpStatus >= 400) {
          console.error('[PS-INSP] API 에러 (HTTP ' + httpStatus + '):', method, path, json);
        }
        return json;
      }).catch(function (parseErr) {
        console.error('[PS-INSP] API 응답 파싱 실패 (HTTP ' + httpStatus + '):', method, path, parseErr);
        throw new Error('서버 응답 파싱 실패 (HTTP ' + httpStatus + ')');
      });
    });
  }

  // ══════════════════════════════════════════════
  // Init
  // ══════════════════════════════════════════════
  function init() {
    extractToken();
    checkHealth();
    setupImageUpload();
    setupThresholdControls();
    applyUrlParams();
    restoreThreshold();   // URL 파라미터 → localStorage → 115 우선순위
    autoFillOperator();
    loadPpmLimitFromServer(); // DB에서 PPM 기준값 로드
  }

  /**
   * 임계값 복원 우선순위:
   *   1) URL ?THRESHOLD=xxx  (applyUrlParams에서 이미 적용됨)
   *   2) localStorage 저장값
   *   3) DEFAULT_THRESHOLD (115)
   */
  function restoreThreshold() {
    var params = new URLSearchParams(window.location.search);
    if (params.get('THRESHOLD')) return; // URL 파라미터 우선 → 이미 적용됨
    var stored = loadThresholdFromStorage();
    if (stored !== null) {
      syncThresholdUI(stored);
      lastThreshold = stored;
    }
  }

  // ══════════════════════════════════════════════
  // URL 파라미터 → 폼 필드 자동 매핑
  // 예: ?IND_BCD=26228J0041&LOT_NO=6228J30035&MATNR=H3SM1240&MATNR_NM=PS필릴1240&USERID=inspector01&USERNM=김철수
  // ══════════════════════════════════════════════
  function applyUrlParams() {
    var params = new URLSearchParams(window.location.search);
    var mapping = {
      'IND_BCD':   'f_indBcd',
      'LOT_NO':    'f_lotnr',
      'MATNR':     'f_matnr',
      'MATNR_NM':  'f_matnrNm',
      'WERKS':     'f_werks',           // hidden field에 저장
      'USERID':    'f_operatorId',
      'USERNM':    'f_operatorNm',
      'INSP_ITEM_GRP_CD': 'f_inspItemGrpCd', // hidden field에 저장
      'THRESHOLD': null  // 별도 처리 (아래 참조)
    };

    Object.keys(mapping).forEach(function (paramKey) {
      var value = params.get(paramKey);
      if (value && mapping[paramKey]) {
        var el = document.getElementById(mapping[paramKey]);
        if (el) el.value = value;
      }
    });

    // THRESHOLD → 슬라이더 + 숫자입력 + 표시값 모두 동기화
    var thVal = params.get('THRESHOLD');
    if (thVal) {
      var v = syncThresholdUI(thVal);
      lastThreshold = v;
    }

    // ── tab 파라미터: 지정된 탭으로 자동 전환 ──
    // 예: ?tab=history-table → 이력 테이블 탭 활성화
    //     ?tab=history       → 검사 이력 탭 활성화
    //     ?tab=inspection    → 검사 실행 탭 활성화 (기본값)
    var tabParam = params.get('tab');
    if (tabParam) {
      // 사용자 편의를 위한 탭 별칭 매핑
      var tabAliasMap = {
        'history-table': 'detail-table',  // ?tab=history-table → 이력 테이블
        'detail-table':  'detail-table',
        'detail':        'detail-table',
        'table':         'detail-table',
        'history':       'history',
        'inspection':    'inspection'
      };
      var resolvedTab = tabAliasMap[tabParam] || tabParam;
      // 유효한 탭인지 확인 후 전환
      var tabContent = document.getElementById('tab-' + resolvedTab);
      if (tabContent) {
        switchTab(resolvedTab);
      }
    }

    // ── IND_BCD 파라미터: 개별바코드로 자동 검색 ──
    // 예: ?tab=history-table&IND_BCD=26228J0039 → 이력 테이블에서 바코드 자동 검색
    //     ?tab=history&IND_BCD=26228J0039       → 검사 이력에서 바코드 자동 검색
    var searchParam = params.get('IND_BCD') || params.get('search'); // IND_BCD 우선, search 하위호환
    if (searchParam) {
      var activeTabName = tabParam ? (({ 'history-table': 'detail-table', 'detail-table': 'detail-table', 'detail': 'detail-table', 'table': 'detail-table', 'history': 'history', 'inspection': 'inspection' })[tabParam] || tabParam) : null;

      if (activeTabName === 'detail-table') {
        // 이력 테이블 탭: 바코드 검색 필드에 자동 입력 후 검색
        var dtKeyword = document.getElementById('dt_keyword');
        if (dtKeyword) dtKeyword.value = searchParam;
        dtSearchParams.indBcd = searchParam;
        // 탭 전환 시 이미 loadDetailTable()이 호출되므로 별도 호출 불필요
        // (dtSearchParams가 설정되었으므로 필터링된 결과를 반환)
      } else if (activeTabName === 'history') {
        // 검사 이력 탭: 검색어 입력 후 검색
        var hKeyword = document.getElementById('h_keyword');
        if (hKeyword) hKeyword.value = searchParam;
        // searchHistory()는 switchTab('history') 이후 로드된 데이터와 별개로 실행
        setTimeout(function () { searchHistory(); }, 200);
      }
    }
  }

  function autoFillOperator() {
    try {
      var raw = localStorage.getItem('fireweb_user');
      if (raw) {
        var u = JSON.parse(raw);
        // URL 파라미터가 이미 채워져 있으면 덮어쓰지 않음
        if (u.loginId) { var el = document.getElementById('f_operatorId'); if (el && !el.value) el.value = u.loginId; }
        if (u.userName) { var el2 = document.getElementById('f_operatorNm'); if (el2 && !el2.value) el2.value = u.userName; }
      }
    } catch (e) { }
  }

  // ══════════════════════════════════════════════
  // Health Check
  // ══════════════════════════════════════════════
  function checkHealth() {
    var badge = document.getElementById('healthBadge');
    api('GET', '/ps-insp-api/health')
      .then(function (data) {
        if (data.success) { badge.textContent = '서비스 정상'; badge.className = 'health-badge ok'; }
        else { badge.textContent = '서비스 오류'; badge.className = 'health-badge fail'; }
      })
      .catch(function () { badge.textContent = '연결 실패'; badge.className = 'health-badge fail'; });
  }

  // ══════════════════════════════════════════════
  // Tab Switching
  // ══════════════════════════════════════════════
  window.switchTab = function (tabName) {
    document.querySelectorAll('.tab').forEach(function (t) { t.classList.remove('active'); });
    document.querySelectorAll('.tab-content').forEach(function (c) { c.classList.remove('active'); });
    var tabBtn = document.querySelector('.tab[data-tab="' + tabName + '"]');
    var tabContent = document.getElementById('tab-' + tabName);
    if (tabBtn) tabBtn.classList.add('active');
    if (tabContent) tabContent.classList.add('active');
    if (tabName === 'history') loadHistory();
    if (tabName === 'detail-table') loadDetailTable();
  };

  // ══════════════════════════════════════════════
  // 1단계: Image Upload & Preprocessing
  // ══════════════════════════════════════════════
  function setupImageUpload() {
    // 카메라 촬영 input
    var cameraInput = document.getElementById('f_imageCamera');
    if (cameraInput) {
      cameraInput.addEventListener('change', function () {
        if (this.files && this.files[0]) handleImageUpload(this.files[0]);
      });
    }

    // 갤러리 선택 input
    var galleryInput = document.getElementById('f_imageGallery');
    if (galleryInput) {
      galleryInput.addEventListener('change', function () {
        if (this.files && this.files[0]) handleImageUpload(this.files[0]);
      });
    }

    // Drag & drop (업로드 영역 전체)
    var uploadArea = document.getElementById('uploadArea');
    if (uploadArea) {
      ['dragenter', 'dragover'].forEach(function (ev) {
        uploadArea.addEventListener(ev, function (e) {
          e.preventDefault();
          uploadArea.style.borderColor = 'var(--primary)';
          uploadArea.style.background = 'var(--primary-light)';
        });
      });
      ['dragleave', 'drop'].forEach(function (ev) {
        uploadArea.addEventListener(ev, function (e) {
          e.preventDefault();
          uploadArea.style.borderColor = '';
          uploadArea.style.background = '';
        });
      });
      uploadArea.addEventListener('drop', function (e) {
        e.preventDefault();
        if (e.dataTransfer.files.length > 0) {
          handleImageUpload(e.dataTransfer.files[0]);
        }
      });
    }
  }

  function handleImageUpload(file) {
    if (file.size > MAX_FILE_SIZE) {
      toast('파일 크기가 900MB를 초과합니다.', 'error');
      return;
    }
    if (!file.type.startsWith('image/')) {
      toast('이미지 파일만 업로드 가능합니다.', 'error');
      return;
    }

    inspectionStartedAt = nowKST();
    msrmDate = nowKST();

    var reader = new FileReader();
    reader.onload = function (e) {
      var img = new Image();
      img.onload = function () {
        processImage(img);
      };
      img.src = e.target.result;
    };
    reader.readAsDataURL(file);
  }

  function processImage(img) {
    var w = img.width;
    var h = img.height;

    // Auto-resize if too large
    if (w > MAX_IMAGE_DIMENSION || h > MAX_IMAGE_DIMENSION) {
      var scale = RESIZE_TARGET / Math.max(w, h);
      w = Math.round(w * scale);
      h = Math.round(h * scale);
    }

    canvasWidth = w;
    canvasHeight = h;

    // 2단계: Prepare canvases & grayscale
    prepareCanvasWithImage(img, w, h);

    // Show canvas area, hide upload
    document.getElementById('uploadArea').style.display = 'none';
    document.getElementById('canvasArea').style.display = 'block';
    document.getElementById('qualityCard').style.display = 'block';
    document.getElementById('actionBar').style.display = 'flex';

    // ❼ 새 이미지 업로드 시: 잠금 ON + force로 1회 실행
    setThresholdLocked(true);
    applyThreshold(true);
  }

  // ══════════════════════════════════════════════
  // 2단계: Canvas Prepare & Grayscale
  // ══════════════════════════════════════════════
  function prepareCanvasWithImage(img, w, h) {
    var origCanvas = document.getElementById('originalCanvas');
    var binCanvas = document.getElementById('binaryCanvas');
    var mrkCanvas = document.getElementById('markerCanvas');

    // Set canvas dimensions
    [origCanvas, binCanvas, mrkCanvas].forEach(function (c) {
      c.width = w;
      c.height = h;
    });

    // Draw original
    var origCtx = origCanvas.getContext('2d');
    origCtx.drawImage(img, 0, 0, w, h);
    originalImageData = origCtx.getImageData(0, 0, w, h);

    // Convert to grayscale
    grayscaleData = convertToGrayscale(originalImageData);
  }

  function convertToGrayscale(imageData) {
    var data = imageData.data;
    var len = data.length / 4;
    var gray = new Uint8ClampedArray(len);
    for (var i = 0; i < len; i++) {
      var idx = i * 4;
      gray[i] = Math.round(0.299 * data[idx] + 0.587 * data[idx + 1] + 0.114 * data[idx + 2]);
    }
    return gray;
  }

  // ══════════════════════════════════════════════
  // 3단계: Threshold Binarization + 잠금 기능
  // 임계값 범위: 최솟값 0 고정, 최댓값만 사용자 조절 (디폴트 115)
  // → 밝기 0 ~ thresholdMax 인 픽셀을 객체(지분)로 검출
  //
  // 잠금 5중 방어:
  //   1층: HTML disabled 속성 (슬라이더 & ±1 버튼 & 숫자입력)
  //   2층: CSS pointer-events:none + cursor-not-allowed
  //   3층: slider input/change 이벤트 → 값 원복
  //   4층: adjustThreshold() → return
  //   5층: applyThreshold() → force 없으면 return
  // ══════════════════════════════════════════════

  // 현재 임계값 읽기 (슬라이더 기준)
  function getThresholdValue() {
    var slider = document.getElementById('thresholdSlider');
    return slider ? (parseInt(slider.value) || DEFAULT_THRESHOLD) : DEFAULT_THRESHOLD;
  }

  // 모든 임계값 UI 동기화 (슬라이더, 숫자입력, 표시값)
  function syncThresholdUI(val) {
    var v = Math.max(1, Math.min(255, parseInt(val) || DEFAULT_THRESHOLD));
    var slider = document.getElementById('thresholdSlider');
    var numInput = document.getElementById('thresholdNumInput');
    if (slider) slider.value = v;
    if (numInput) numInput.value = v;
    updateThresholdDisplay(v);
    return v;
  }

  function setupThresholdControls() {
    var slider = document.getElementById('thresholdSlider');
    var numInput = document.getElementById('thresholdNumInput');
    if (!slider) return;

    // 방어 3층: slider input 이벤트
    slider.addEventListener('input', function (e) {
      if (thresholdLocked) {
        e.preventDefault();
        e.stopPropagation();
        slider.value = lastThreshold;
        updateThresholdLockUI();
        toast('임계값이 잠금 상태입니다. 잠금 해제 후 조정하세요.', 'error');
        return;
      }
      var sv = syncThresholdUI(this.value);
      saveThresholdToStorage(sv);
      if (grayscaleData) applyThreshold();
    });

    // 방어 3층: slider change 이벤트
    slider.addEventListener('change', function (e) {
      if (thresholdLocked) {
        e.preventDefault();
        e.stopPropagation();
        slider.value = lastThreshold;
        updateThresholdLockUI();
        return;
      }
    });

    // 숫자 직접 입력
    if (numInput) {
      numInput.addEventListener('change', function () {
        if (thresholdLocked) {
          numInput.value = lastThreshold;
          return;
        }
        var v = syncThresholdUI(this.value);
        saveThresholdToStorage(v);
        if (grayscaleData) applyThreshold();
      });
      numInput.addEventListener('keydown', function (e) {
        if (e.key === 'Enter') {
          e.preventDefault();
          if (thresholdLocked) {
            numInput.value = lastThreshold;
            return;
          }
          var v = syncThresholdUI(this.value);
          saveThresholdToStorage(v);
          if (grayscaleData) applyThreshold();
        }
      });
    }

    // 초기 잠금 UI 적용
    updateThresholdLockUI();
  }

  // ── 임계값 표시 업데이트 ──
  function updateThresholdDisplay(val) {
    var el = document.getElementById('thresholdDisplay');
    if (el) el.textContent = '0 ~ ' + val;
  }

  // ── ±1 버튼 핸들러 (방어 4층) ──
  window.adjustThreshold = function (delta) {
    if (thresholdLocked) {
      updateThresholdLockUI();
      toast('임계값이 잠겨 있어 조정할 수 없습니다.', 'error');
      return;
    }
    var cur = getThresholdValue();
    var v = syncThresholdUI(cur + delta);
    saveThresholdToStorage(v);
    if (grayscaleData) applyThreshold();
  };

  // ── 잠금 토글 (❸) ──
  window.toggleThresholdLock = function () {
    // 이미지 없이 잠금 해제 시도 → 안내 메시지
    if (thresholdLocked && !grayscaleData) {
      toast('이미지를 먼저 업로드한 후 잠금 해제하세요.', 'error');
      return;
    }
    thresholdLocked = !thresholdLocked;
    updateThresholdLockUI();
    if (thresholdLocked) {
      lastThreshold = getThresholdValue();
      saveThresholdToStorage(lastThreshold);
      toast('임계값이 잠금 처리되었습니다. (값: 0 ~ ' + lastThreshold + ')', 'info');
    } else {
      toast('임계값 조절이 잠금 해제되었습니다. 슬라이더 또는 숫자 입력으로 조절하세요.', 'success');
    }
  };

  // ── 잠금 강제 설정 (이미지 업로드 시 호출) ──
  function setThresholdLocked(locked) {
    thresholdLocked = locked;
    if (locked) lastThreshold = getThresholdValue();
    updateThresholdLockUI();
  }

  // ── 잠금 UI 전체 업데이트 ──
  function updateThresholdLockUI() {
    var badge = document.getElementById('thresholdLockStatus');
    var btn = document.getElementById('thresholdLockButton');
    var slider = document.getElementById('thresholdSlider');
    var numInput = document.getElementById('thresholdNumInput');
    var btnMinus = document.getElementById('btnThresholdMinus');
    var btnPlus = document.getElementById('btnThresholdPlus');
    var hasImage = !!grayscaleData;
    var canAdjust = !thresholdLocked && hasImage;

    // 상태 뱃지
    if (badge) {
      if (thresholdLocked) {
        badge.className = 'threshold-lock-badge locked';
        badge.innerHTML = '&#128274; 잠금';
      } else {
        badge.className = 'threshold-lock-badge unlocked';
        badge.innerHTML = '&#128275; 변경 가능';
      }
    }

    // 토글 버튼
    if (btn) {
      if (thresholdLocked) {
        btn.className = 'btn-threshold-lock locked';
        btn.innerHTML = '&#128275; 잠금 해제';
      } else {
        btn.className = 'btn-threshold-lock unlocked';
        btn.innerHTML = '&#128274; 잠금';
      }
    }

    // 방어 1층 + 2층
    if (slider) slider.disabled = !canAdjust;
    if (numInput) numInput.disabled = !canAdjust;
    if (btnMinus) btnMinus.disabled = !canAdjust;
    if (btnPlus) btnPlus.disabled = !canAdjust;

    // 디스플레이 값 동기화
    updateThresholdDisplay(getThresholdValue());
  }

  function applyThreshold(force) {
    if (!grayscaleData || !originalImageData) return;

    // 방어 5층: 잠금 상태에서 force 없으면 차단
    if (thresholdLocked && !force) {
      var slider0 = document.getElementById('thresholdSlider');
      if (slider0) slider0.value = lastThreshold;
      updateThresholdDisplay(lastThreshold);
      updateThresholdLockUI();
      return;
    }

    var thresholdMax = getThresholdValue();
    var thresholdMin = 0; // 최솟값 0 고정 (변경 불가)
    var w = canvasWidth;
    var h = canvasHeight;
    var total = w * h;

    // Create binary mask and display data
    var mask = new Uint8Array(total);
    var displayData = new Uint8ClampedArray(originalImageData.data.length);

    for (var i = 0; i < total; i++) {
      var idx = i * 4;
      if (grayscaleData[i] >= thresholdMin && grayscaleData[i] <= thresholdMax) {
        // Object pixel
        mask[i] = 1;
        displayData[idx] = 255;
        displayData[idx + 1] = 255;
        displayData[idx + 2] = 255;
        displayData[idx + 3] = 255;
      } else {
        // Background - keep original color
        mask[i] = 0;
        displayData[idx] = originalImageData.data[idx];
        displayData[idx + 1] = originalImageData.data[idx + 1];
        displayData[idx + 2] = originalImageData.data[idx + 2];
        displayData[idx + 3] = originalImageData.data[idx + 3];
      }
    }

    // Draw to binary canvas
    var binCanvas = document.getElementById('binaryCanvas');
    var binCtx = binCanvas.getContext('2d');
    var imgData = new ImageData(displayData, w, h);
    binCtx.putImageData(imgData, 0, 0);

    // 4단계: Connected Component Analysis
    autoComponents = extractComponents(mask, w, h);
    removedAutoIds = new Set();
    manualComponents = [];
    isManualMode = false;
    updateManualModeUI();

    // 5~6단계: Draw markers & compute metrics
    updateCombinedComponents();
  }

  // ══════════════════════════════════════════════
  // 4단계: Connected Component (BFS 4-direction)
  // ══════════════════════════════════════════════
  function extractComponents(mask, w, h) {
    var visited = new Uint8Array(w * h);
    var components = [];
    var timestamp = Date.now();
    var dx = [0, 0, -1, 1];
    var dy = [-1, 1, 0, 0];

    for (var y = 0; y < h; y++) {
      for (var x = 0; x < w; x++) {
        var pos = y * w + x;
        if (mask[pos] === 1 && !visited[pos]) {
          // BFS
          var queue = [pos];
          visited[pos] = 1;
          var pixels = [];
          var minX = x, maxX = x, minY = y, maxY = y;
          var sumX = 0, sumY = 0;
          var head = 0;

          while (head < queue.length) {
            var cur = queue[head++];
            var cx = cur % w;
            var cy = (cur - cx) / w;
            pixels.push(cur);
            sumX += cx;
            sumY += cy;
            if (cx < minX) minX = cx;
            if (cx > maxX) maxX = cx;
            if (cy < minY) minY = cy;
            if (cy > maxY) maxY = cy;

            for (var d = 0; d < 4; d++) {
              var nx = cx + dx[d];
              var ny = cy + dy[d];
              if (nx >= 0 && nx < w && ny >= 0 && ny < h) {
                var npos = ny * w + nx;
                if (mask[npos] === 1 && !visited[npos]) {
                  visited[npos] = 1;
                  queue.push(npos);
                }
              }
            }
          }

          var size = pixels.length;
          if (size >= 2) { // filter noise (1 pixel)
            components.push({
              id: 'auto-' + timestamp + '-' + components.length,
              size: size,
              centroid: { x: Math.round(sumX / size), y: Math.round(sumY / size) },
              bbox: { minX: minX, minY: minY, maxX: maxX, maxY: maxY },
              source: 'auto'
            });
          }
        }
      }
    }

    return components;
  }

  // ══════════════════════════════════════════════
  // 5단계: Marker Visualization
  // ══════════════════════════════════════════════
  function getCombinedComponents() {
    var combined = [];
    autoComponents.forEach(function (c) {
      if (!removedAutoIds.has(c.id)) combined.push(c);
    });
    manualComponents.forEach(function (c) { combined.push(c); });
    return combined;
  }

  function updateCombinedComponents() {
    var combined = getCombinedComponents();
    drawMarkers(combined);
    computeQualityMetrics(combined);
    renderComponentList(combined);
  }

  function drawMarkers(combined) {
    var canvas = document.getElementById('markerCanvas');
    var ctx = canvas.getContext('2d');
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    combined.forEach(function (comp, idx) {
      var cx = comp.centroid.x;
      var cy = comp.centroid.y;

      if (comp.source === 'auto') {
        // Red marker - fit to actual detected size
        var eqR = Math.sqrt(comp.size / Math.PI); // 실제 등가 반지름
        var r = Math.max(1.5, Math.min(6, eqR));  // 최소 1.5px, 최대 6px

        // 바운딩 박스 대신 검출 영역에 딱 맞는 원 테두리만 표시
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, 2 * Math.PI);
        ctx.strokeStyle = 'rgba(220,38,38,0.85)';
        ctx.lineWidth = 1;
        ctx.stroke();

        // 중심점 (작은 점)
        ctx.beginPath();
        ctx.arc(cx, cy, Math.max(0.8, r * 0.35), 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(220,38,38,0.7)';
        ctx.fill();
      } else {
        // Green marker for manual (also smaller)
        ctx.beginPath();
        ctx.arc(cx, cy, 4, 0, 2 * Math.PI);
        ctx.strokeStyle = 'rgba(5,150,105,0.9)';
        ctx.lineWidth = 1;
        ctx.stroke();
        ctx.beginPath();
        ctx.arc(cx, cy, 1, 0, 2 * Math.PI);
        ctx.fillStyle = 'rgba(16,185,129,0.8)';
        ctx.fill();
      }

      // Number label (smaller, only show for small count)
      if (combined.length <= 50) {
        ctx.fillStyle = 'rgba(0,0,0,0.6)';
        ctx.font = '9px sans-serif';
        ctx.fillText(String(idx + 1), cx + (comp.source === 'auto' ? 4 : 6), cy - 3);
      }
    });
  }

  // ══════════════════════════════════════════════
  // 6단계: Quality Metrics
  // ══════════════════════════════════════════════
  function computeQualityMetrics(combined) {
    var totalCount = combined.length;
    var totalPixels = canvasWidth * canvasHeight;

    // Object pixel count (sum of all component sizes)
    var objectPixelCount = 0;
    var sizes = [];
    var autoCount = 0;
    var manualCount = 0;
    var halfW = canvasWidth / 2;
    var halfH = canvasHeight / 2;
    var qTL = 0, qTR = 0, qBL = 0, qBR = 0;
    var b3 = 0, b5 = 0, b7 = 0, b7p = 0;

    combined.forEach(function (c) {
      var s = c.size || (Math.PI * MANUAL_COMPONENT_RADIUS * MANUAL_COMPONENT_RADIUS);
      objectPixelCount += s;
      sizes.push(s);
      if (c.source === 'auto') autoCount++;
      else manualCount++;

      var cx = c.centroid.x;
      var cy = c.centroid.y;
      if (cx < halfW && cy < halfH) qTL++;
      else if (cx >= halfW && cy < halfH) qTR++;
      else if (cx < halfW && cy >= halfH) qBL++;
      else qBR++;

      var eqR = Math.sqrt(s / Math.PI);
      if (eqR <= 3) b3++;
      else if (eqR <= 5) b5++;
      else if (eqR <= 7) b7++;
      else b7p++;
    });

    var coverageRatio = totalPixels > 0 ? objectPixelCount / totalPixels : 0;
    var coveragePPM = coverageRatio * 1000000;
    var densityCount = totalCount;
    var densityRatio = totalPixels > 0 ? totalCount / totalPixels : 0;

    // Size uniformity (1 - CV)
    var meanSize = sizes.length > 0 ? sizes.reduce(function (a, b) { return a + b; }, 0) / sizes.length : 0;
    var variance = 0;
    if (sizes.length > 1) {
      sizes.forEach(function (s) { variance += (s - meanSize) * (s - meanSize); });
      variance /= sizes.length;
    }
    var stdSize = Math.sqrt(variance);
    var cv = meanSize > 0 ? stdSize / meanSize : 0;
    var sizeUniformity = Math.max(0, 1 - cv);

    // Distribution uniformity (1 - CV of quadrants)
    var quadArr = [qTL, qTR, qBL, qBR];
    var qMean = totalCount / 4;
    var qVar = 0;
    if (totalCount > 0) {
      quadArr.forEach(function (q) { qVar += (q - qMean) * (q - qMean); });
      qVar /= 4;
    }
    var qStd = Math.sqrt(qVar);
    var qCV = qMean > 0 ? qStd / qMean : 0;
    var distUniformity = Math.max(0, 1 - qCV);

    var removedAutoCount = removedAutoIds.size;
    var manualAddedCount = manualComponents.length;
    var manualRemovedCount = removedAutoCount;

    // Store metrics for saving
    window._lastMetrics = {
      thresholdMax: getThresholdValue(),
      totalCount: totalCount,
      coverageRatio: coverageRatio,
      densityCount: densityCount,
      densityRatio: densityRatio,
      sizeUniformityScore: sizeUniformity,
      distributionUniformityScore: distUniformity,
      meanSize: meanSize,
      stdSize: stdSize,
      autoCount: autoCount,
      manualCount: manualCount,
      removedAutoCount: removedAutoCount,
      bucketUpTo3: b3,
      bucketUpTo5: b5,
      bucketUpTo7: b7,
      bucketOver7: b7p,
      quadrantTopLeft: qTL,
      quadrantTopRight: qTR,
      quadrantBottomLeft: qBL,
      quadrantBottomRight: qBR,
      objectPixelCount: objectPixelCount,
      totalPixels: totalPixels,
      manualAddedCount: manualAddedCount,
      manualRemovedCount: manualRemovedCount
    };

    renderQualitySummary(window._lastMetrics, coveragePPM);
  }

  function renderQualitySummary(m, ppm) {
    var summary = document.getElementById('qualitySummary');
    if (!summary) return;
    summary.innerHTML = ''
      + qItem('총 지분수', m.totalCount, 'blue')
      + qItem('후면 지분 값(PPM)', ppm.toFixed(1), 'orange')
      + qItem('밀도 지분수', m.densityCount, 'green')
      + qItem('자동 / 수동', m.autoCount + ' / ' + m.manualCount, 'blue')
      + qItem('크기 균일도', (m.sizeUniformityScore * 100).toFixed(1) + '%', 'green')
      + qItem('분포 균일도', (m.distributionUniformityScore * 100).toFixed(1) + '%', 'green')
      + qItem('평균 크기 (px)', m.meanSize.toFixed(1), 'blue')
      + qItem('삭제 자동검출', m.removedAutoCount, 'red');

    // Bucket bar
    var bucketEl = document.getElementById('bucketBar');
    if (bucketEl) {
      bucketEl.innerHTML = ''
        + bItem('~3px 이하', m.bucketUpTo3)
        + bItem('~5px 이하', m.bucketUpTo5)
        + bItem('~7px 이하', m.bucketUpTo7)
        + bItem('7px 초과', m.bucketOver7);
    }

    // Quadrant
    var qGrid = document.getElementById('quadrantGrid');
    if (qGrid) {
      qGrid.innerHTML = ''
        + '<div class="quadrant-cell"><small>좌상</small>' + m.quadrantTopLeft + '</div>'
        + '<div class="quadrant-cell"><small>우상</small>' + m.quadrantTopRight + '</div>'
        + '<div class="quadrant-cell"><small>좌하</small>' + m.quadrantBottomLeft + '</div>'
        + '<div class="quadrant-cell"><small>우하</small>' + m.quadrantBottomRight + '</div>';
    }
  }

  function qItem(label, value, color) {
    return '<div class="quality-item"><div class="qlabel">' + label + '</div><div class="qvalue ' + color + '">' + value + '</div></div>';
  }
  function bItem(label, count) {
    return '<div class="bucket-item"><div class="blabel">' + label + '</div><div class="bcount">' + count + '</div></div>';
  }

  function renderComponentList(combined) {
    var tbody = document.getElementById('componentListBody');
    if (!tbody) return;
    if (combined.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty-msg">검출된 객체가 없습니다.</td></tr>';
      return;
    }
    tbody.innerHTML = combined.map(function (c, idx) {
      var typeBadge = c.source === 'auto'
        ? '<span class="badge-auto">자동</span>'
        : '<span class="badge-manual">수동</span>';
      var size = c.size || Math.round(Math.PI * MANUAL_COMPONENT_RADIUS * MANUAL_COMPONENT_RADIUS);
      return '<tr>'
        + '<td>' + (idx + 1) + '</td>'
        + '<td>' + typeBadge + '</td>'
        + '<td>(' + c.centroid.x + ', ' + c.centroid.y + ')</td>'
        + '<td>' + size + '</td>'
        + '<td><button class="btn-icon" title="제외" onclick="removeComponent(\'' + c.id + '\')" style="color:var(--red);">&#10006;</button></td>'
        + '</tr>';
    }).join('');
  }

  // ══════════════════════════════════════════════
  // 7단계: Manual Correction
  // ══════════════════════════════════════════════
  window.toggleManualAddMode = function () {
    isManualMode = !isManualMode;
    updateManualModeUI();

    if (isManualMode) {
      // Enable click on markerCanvas
      var wrapper = document.getElementById('canvasWrapper');
      wrapper.classList.add('manual-mode');
      var mrkCanvas = document.getElementById('markerCanvas');
      mrkCanvas.addEventListener('click', onManualClick);
    } else {
      var wrapper2 = document.getElementById('canvasWrapper');
      wrapper2.classList.remove('manual-mode');
      var mrkCanvas2 = document.getElementById('markerCanvas');
      mrkCanvas2.removeEventListener('click', onManualClick);
    }
  };

  function updateManualModeUI() {
    var btn = document.getElementById('btnManualAdd');
    var badge = document.getElementById('manualModeBadge');
    if (btn) {
      if (isManualMode) { btn.classList.add('active-mode'); btn.textContent = '수동 추가 종료'; }
      else { btn.classList.remove('active-mode'); btn.textContent = '수동 추가'; }
    }
    if (badge) badge.style.display = isManualMode ? 'inline-block' : 'none';
  }

  function onManualClick(e) {
    if (!isManualMode) return;
    var canvas = document.getElementById('markerCanvas');
    var rect = canvas.getBoundingClientRect();
    var scaleX = canvas.width / rect.width;
    var scaleY = canvas.height / rect.height;
    var x = Math.round((e.clientX - rect.left) * scaleX);
    var y = Math.round((e.clientY - rect.top) * scaleY);
    addManualComponent(x, y);
  }

  function addManualComponent(x, y) {
    manualComponents.push({
      id: 'manual-' + Date.now() + '-' + manualComponents.length,
      size: Math.round(Math.PI * MANUAL_COMPONENT_RADIUS * MANUAL_COMPONENT_RADIUS),
      centroid: { x: x, y: y },
      bbox: null,
      source: 'manual'
    });
    updateCombinedComponents();
  }

  window.removeComponent = function (id) {
    if (id.startsWith('auto-')) {
      removedAutoIds.add(id);
    } else {
      manualComponents = manualComponents.filter(function (c) { return c.id !== id; });
    }
    updateCombinedComponents();
  };

  window.resetImage = function () {
    document.getElementById('uploadArea').style.display = '';
    document.getElementById('canvasArea').style.display = 'none';
    document.getElementById('qualityCard').style.display = 'none';
    document.getElementById('actionBar').style.display = 'none';
    document.getElementById('saveResult').style.display = 'none';
    var fi1 = document.getElementById('f_imageCamera');
    var fi2 = document.getElementById('f_imageGallery');
    if (fi1) fi1.value = '';
    if (fi2) fi2.value = '';
    grayscaleData = null;
    originalImageData = null;
    autoComponents = [];
    manualComponents = [];
    removedAutoIds = new Set();
    isManualMode = false;
    updateManualModeUI();
    // 잠금 초기 상태로 복원
    setThresholdLocked(true);
  };

  // ══════════════════════════════════════════════
  // 8단계: Capture Images
  // ══════════════════════════════════════════════
  function captureCanvasBlob(canvas, maxDim, quality) {
    return new Promise(function (resolve) {
      var w = canvas.width;
      var h = canvas.height;
      var scale = 1;
      if (Math.max(w, h) > maxDim) {
        scale = maxDim / Math.max(w, h);
      }
      var outW = Math.round(w * scale);
      var outH = Math.round(h * scale);

      var tmpCanvas = document.createElement('canvas');
      tmpCanvas.width = outW;
      tmpCanvas.height = outH;
      var ctx = tmpCanvas.getContext('2d');
      ctx.drawImage(canvas, 0, 0, outW, outH);
      tmpCanvas.toBlob(function (blob) { resolve(blob); }, 'image/jpeg', quality || 0.75);
    });
  }

  function captureOriginalImageBlob() {
    return captureCanvasBlob(document.getElementById('originalCanvas'), 1024, 0.75);
  }

  function captureResultImageBlob() {
    var binCanvas = document.getElementById('binaryCanvas');
    var mrkCanvas = document.getElementById('markerCanvas');
    var w = binCanvas.width;
    var h = binCanvas.height;

    var merged = document.createElement('canvas');
    merged.width = w;
    merged.height = h;
    var ctx = merged.getContext('2d');
    ctx.drawImage(binCanvas, 0, 0);
    ctx.drawImage(mrkCanvas, 0, 0);

    return captureCanvasBlob(merged, 1024, 0.75);
  }

  // ══════════════════════════════════════════════
  // Form: Clear
  // ══════════════════════════════════════════════
  window.clearForm = function () {
    ['f_matnr', 'f_matnrNm', 'f_lotnr', 'f_indBcd',
      'f_operatorId', 'f_operatorNm'].forEach(function (id) {
      var el = document.getElementById(id);
      if (el) el.value = '';
    });
    // hidden fields 기본값 복원
    var werksEl = document.getElementById('f_werks');
    if (werksEl) werksEl.value = '';
    var grpEl = document.getElementById('f_inspItemGrpCd');
    if (grpEl) grpEl.value = 'COV_INS';

    // 초기화 시에도 localStorage 임계값은 유지 (검사자가 조정한 값 보존)
    var restoredTh = loadThresholdFromStorage() || DEFAULT_THRESHOLD;
    syncThresholdUI(restoredTh);
    lastThreshold = restoredTh;
    setThresholdLocked(true);
    resetImage();
    var ec = document.getElementById('existsCheck');
    if (ec) ec.style.display = 'none';
    autoFillOperator();
  };

  // ══════════════════════════════════════════════
  // Check Exists
  // ══════════════════════════════════════════════
  window.checkExists = function () {
    var matnr = val('f_matnr');
    var lotnr = val('f_lotnr');
    var indBcd = val('f_indBcd');
    if (!matnr || !lotnr || !indBcd) {
      toast('자재코드, LOT, 바코드를 모두 입력해주세요.', 'error');
      return;
    }
    var ec = document.getElementById('existsCheck');
    api('GET', '/ps-insp-api/inspections/check-exists?matnr=' + enc(matnr) + '&lotnr=' + enc(lotnr) + '&indBcd=' + enc(indBcd))
      .then(function (res) {
        if (res.success && res.data) {
          if (res.data.exists) {
            ec.innerHTML = '&#9888; 동일한 자재+LOT+바코드 조합이 이미 존재합니다 (ID: ' + (res.data.record ? res.data.record.inspectionId : '') + '). 저장 시 업데이트됩니다.';
            ec.className = 'exists-check warn';
          } else {
            ec.innerHTML = '&#10004; 중복 없음. 새로운 검사 결과로 저장됩니다.';
            ec.className = 'exists-check ok';
          }
          ec.style.display = 'block';
        }
      })
      .catch(function (e) { toast('중복 체크 실패: ' + e.message, 'error'); });
  };

  // ══════════════════════════════════════════════
  // 9단계: Save Inspection
  // ══════════════════════════════════════════════
  window.saveInspection = function () {
    // Validate required fields
    var matnr = val('f_matnr');
    var lotnr = val('f_lotnr');
    var indBcd = val('f_indBcd');
    var operatorId = val('f_operatorId');
    if (!matnr) { toast('자재코드(MATNR)는 필수입니다.', 'error'); return; }
    if (!lotnr) { toast('LOT 번호는 필수입니다.', 'error'); return; }
    if (!indBcd) { toast('개별바코드(IND_BCD)는 필수입니다.', 'error'); return; }
    if (!operatorId) { toast('검사자 ID는 필수입니다.', 'error'); return; }
    if (!grayscaleData) { toast('이미지를 먼저 업로드하여 검사를 실행해주세요.', 'error'); return; }

    var m = window._lastMetrics;
    if (!m) { toast('검사 결과가 없습니다. 이미지를 먼저 분석해주세요.', 'error'); return; }

    // PPM 기준값 초과 체크
    var ppmValue = m.coverageRatio * 1000000;
    var ppmLimit = getPpmLimit();
    if (ppmLimit > 0 && ppmValue > ppmLimit) {
      showPpmAlert(ppmValue, ppmLimit);
      return; // 팝업에서 전송/재측정 선택 대기
    }

    doSaveInspection();
  };

  // PPM 기준 초과 알림 표시
  function showPpmAlert(ppmValue, ppmLimit) {
    var modal = document.getElementById('ppmAlertModal');
    var msg = document.getElementById('ppmAlertMsg');
    var detail = document.getElementById('ppmAlertDetail');
    if (!modal) return;
    msg.innerHTML = '후면 지분 값이 기준값을 <strong>초과</strong>했습니다.';
    detail.innerHTML = ''
      + '<div class="ppm-alert-row"><span>측정값</span><strong style="color:var(--red);">' + ppmValue.toFixed(1) + ' PPM</strong></div>'
      + '<div class="ppm-alert-row"><span>기준값</span><strong>' + ppmLimit.toFixed(1) + ' PPM</strong></div>'
      + '<div class="ppm-alert-row"><span>초과량</span><strong style="color:var(--red);">+' + (ppmValue - ppmLimit).toFixed(1) + ' PPM</strong></div>';
    modal.style.display = 'flex';
  }

  window.ppmAlertAction = function (action) {
    document.getElementById('ppmAlertModal').style.display = 'none';
    if (action === 'send') {
      doSaveInspection(); // 기준 초과 인지 후 저장 진행
    } else {
      toast('재측정을 진행해 주세요. 이미지를 변경하거나 임계값을 조정하세요.', 'info');
    }
  };

  function doSaveInspection() {
    var m = window._lastMetrics;
    var matnr = val('f_matnr');
    var lotnr = val('f_lotnr');
    var indBcd = val('f_indBcd');

    var btnSave = document.getElementById('btnSave');
    if (btnSave) { btnSave.disabled = true; btnSave.textContent = '저장 중...'; }

    var metadata = {
      inspItemGrpCd: val('f_inspItemGrpCd') || 'COV_INS',
      matnr: matnr,
      matnrNm: val('f_matnrNm'),
      werks: val('f_werks'),
      lotnr: lotnr,
      indBcd: indBcd,
      inspectedAt: inspectionStartedAt || nowKST(),
      msrmDate: msrmDate || nowKST(),
      thresholdMax: m.thresholdMax,
      totalCount: m.totalCount,
      coverageRatio: m.coverageRatio,
      densityCount: m.densityCount,
      densityRatio: m.densityRatio,
      sizeUniformityScore: m.sizeUniformityScore,
      distributionUniformityScore: m.distributionUniformityScore,
      meanSize: m.meanSize,
      stdSize: m.stdSize,
      autoCount: m.autoCount,
      manualCount: m.manualCount,
      removedAutoCount: m.removedAutoCount,
      bucketUpTo3: m.bucketUpTo3,
      bucketUpTo5: m.bucketUpTo5,
      bucketUpTo7: m.bucketUpTo7,
      bucketOver7: m.bucketOver7,
      quadrantTopLeft: m.quadrantTopLeft,
      quadrantTopRight: m.quadrantTopRight,
      quadrantBottomLeft: m.quadrantBottomLeft,
      quadrantBottomRight: m.quadrantBottomRight,
      objectPixelCount: m.objectPixelCount,
      totalPixels: m.totalPixels,
      manualAddedCount: m.manualAddedCount,
      manualRemovedCount: m.manualRemovedCount,
      operatorId: val('f_operatorId'),
      operatorNm: val('f_operatorNm'),
      status: 'COMPLETED'
    };

    // Capture images and send
    Promise.all([captureOriginalImageBlob(), captureResultImageBlob()])
      .then(function (blobs) {
        var fd = new FormData();
        fd.append('metadata', JSON.stringify(metadata));
        if (blobs[0]) fd.append('originalImage', blobs[0], 'original.jpg');
        if (blobs[1]) fd.append('resultImage', blobs[1], 'result.jpg');
        return api('POST', '/ps-insp-api/inspections', fd);
      })
      .then(function (res) {
        console.log('[PS-INSP] 저장 API 응답:', JSON.stringify(res));
        if (res.success && res.data) {
          // 서버 응답 데이터를 메인으로 사용 (DB에서 생성된 실제 값)
          var serverData = res.data;
          var displayId = serverData.inspectionId;
          if (serverData.isUpdate) {
            toast('기존 검사 결과가 갱신되었습니다. (ID: ' + displayId + ', 차수: ' + (serverData.seq || '-') + ')', 'success');
          } else {
            toast('검사 결과가 저장되었습니다. (ID: ' + displayId + ')', 'success');
          }
          showSaveResult(serverData);
          // 저장 성공 시 현재 임계값을 localStorage에 확정 저장
          saveThresholdToStorage(getThresholdValue());
          // 10단계: MES 전송
          sendToMes(serverData);
          // 저장 후 이력 데이터 갱신 (캐시 초기화)
          refreshHistoryData();
        } else if (res.success && !res.data) {
          // 서버가 success=true지만 data가 없는 경우 (비정상)
          console.warn('[PS-INSP] 서버 응답에 data 필드 누락:', res);
          var fallbackData = {};
          Object.keys(metadata).forEach(function(k) { fallbackData[k] = metadata[k]; });
          if (m) {
            fallbackData.totalCount = m.totalCount;
            fallbackData.coverageRatio = m.coverageRatio;
            fallbackData.autoCount = m.autoCount;
            fallbackData.manualCount = m.manualCount;
            fallbackData.thresholdMax = m.thresholdMax;
          }
          toast('검사 결과가 저장되었습니다. (서버 응답 확인 필요)', 'success');
          showSaveResult(fallbackData);
          saveThresholdToStorage(getThresholdValue());
          sendToMes(fallbackData);
          refreshHistoryData();
        } else {
          // 저장 실패
          var msg = res.message || '저장 실패';
          if (res.code) msg = '[' + res.code + '] ' + msg;
          if (res.data && typeof res.data === 'object') {
            var errs = Object.entries(res.data).map(function (e) { return e[0] + ': ' + e[1]; });
            if (errs.length) msg += ' (' + errs.join(', ') + ')';
          }
          console.error('[PS-INSP] 저장 실패:', res);
          toast(msg, 'error');
        }
      })
      .catch(function (e) {
        console.error('[PS-INSP] 저장 오류:', e);
        toast('저장 오류: ' + e.message, 'error');
      })
      .finally(function () {
        if (btnSave) { btnSave.disabled = false; btnSave.textContent = '검사 결과 저장'; }
      });
  };

  /**
   * 저장 성공 후 이력 데이터를 갱신합니다.
   * 현재 활성 탭이 이력/테이블이면 즉시 새로고침,
   * 아니면 다음 탭 전환 시 자동 로드됩니다.
   */
  function refreshHistoryData() {
    var activeTab = document.querySelector('.tab.active');
    if (activeTab) {
      var tabName = activeTab.getAttribute('data-tab');
      if (tabName === 'history') loadHistory(currentHistoryPage);
      if (tabName === 'detail-table') loadDetailTable(currentDetailPage);
    }
  }

  function showSaveResult(data) {
    var el = document.getElementById('saveResult');
    var body = document.getElementById('saveResultBody');
    if (!el || !body) return;
    // coverageRatio가 0~1 소수인 경우와 이미 PPM 단위인 경우 모두 처리
    var covRatio = data.coverageRatio;
    var covPPM = '-';
    if (covRatio != null) {
      var ppmVal = covRatio < 1 ? covRatio * 1000000 : covRatio;
      covPPM = ppmVal.toFixed(1) + ' PPM';
    }
    var thMax = data.thresholdMax != null ? data.thresholdMax : getThresholdValue();
    var displayId = data.inspectionId != null ? data.inspectionId : '(저장 완료)';
    body.innerHTML = '<div class="result-summary">'
      + '<div class="result-item"><div class="label">검사 ID</div><div class="value blue">' + displayId + '</div></div>'
      + '<div class="result-item"><div class="label">총 지분수</div><div class="value green">' + (data.totalCount || 0) + '</div></div>'
      + '<div class="result-item"><div class="label">후면 지분 값(PPM)</div><div class="value orange">' + covPPM + '</div></div>'
      + '<div class="result-item"><div class="label">최대 임계값</div><div class="value blue">' + thMax + '</div></div>'
      + '</div>'
      + '<div style="font-size:13px;color:var(--text2);">'
      + '<b>자재코드:</b> ' + esc(data.matnr || '-') + ' | '
      + '<b>LOT:</b> ' + esc(data.lotnr || '-') + ' | '
      + '<b>바코드:</b> ' + esc(data.indBcd || '-') + ' | '
      + '<b>검사자:</b> ' + esc(data.operatorNm || '-') + ' | '
      + '<b>자동:</b> ' + (data.autoCount || 0) + ' | '
      + '<b>수동:</b> ' + (data.manualCount || 0)
      + (data.isUpdate ? ' <span class="badge badge-warning">업데이트</span>' : ' <span class="badge badge-active">신규</span>')
      + '</div>';
    el.style.display = 'block';
  }

  // ══════════════════════════════════════════════
  // 10단계: MES Send
  // ══════════════════════════════════════════════
  function sendToMes(data) {
    if (!data.indBcd || data.coverageRatio == null) return;
    var ppmValue = Math.round(data.coverageRatio * 1000000);
    api('POST', '/ps-insp-api/mes/send-result', { IND_BCD: data.indBcd, RST_VAL: ppmValue })
      .then(function (res) {
        if (res.success) {
          var rsMsg = (res.data && res.data.rsMsg) ? ' (' + res.data.rsMsg + ')' : '';
          toast('MES 전송 완료' + rsMsg, 'info');
        } else {
          var detail = (res.data && res.data.rsMsg) ? res.data.rsMsg : (res.message || '');
          toast('MES 전송 실패: ' + detail, 'error');
        }
      })
      .catch(function () { /* silently ignore */ });
  }

  // ══════════════════════════════════════════════
  // 11단계: History with Images (검사 이력 탭)
  // ══════════════════════════════════════════════
  window.loadHistory = function (page) {
    if (page === undefined) page = 0;
    currentHistoryPage = page;
    var container = document.getElementById('historyCardsBody');
    var pag = document.getElementById('pagination');
    if (!container) return;
    container.innerHTML = '<div class="empty-msg">불러오는 중...</div>';
    if (pag) pag.innerHTML = '';

    api('GET', '/ps-insp-api/inspections?page=' + page + '&size=' + historyPageSize)
      .then(function (res) {
        if (res.success && res.data) {
          var items = res.data.content || [];
          var totalPages = res.data.totalPages || 0;
          renderHistoryCards(items, container);
          renderPagination(totalPages, page, pag, 'loadHistory');
        } else {
          container.innerHTML = '<div class="empty-msg">조회 실패</div>';
        }
      })
      .catch(function (e) {
        container.innerHTML = '<div class="empty-msg">오류: ' + esc(e.message) + '</div>';
      });
  };

  window.searchHistory = function () {
    var keyword = val('h_keyword');
    var type = document.getElementById('h_searchType') ? document.getElementById('h_searchType').value : 'indBcd';
    if (!keyword) { loadHistory(); return; }
    var container = document.getElementById('historyCardsBody');
    var pag = document.getElementById('pagination');
    if (!container) return;
    container.innerHTML = '<div class="empty-msg">검색 중...</div>';
    if (pag) pag.innerHTML = '';

    api('GET', '/ps-insp-api/inspections/search?type=' + enc(type) + '&keyword=' + enc(keyword) + '&page=0&size=50')
      .then(function (res) {
        if (res.success && res.data) {
          renderHistoryCards(res.data.content || [], container);
        } else {
          container.innerHTML = '<div class="empty-msg">검색 결과 없음</div>';
        }
      })
      .catch(function (e) {
        container.innerHTML = '<div class="empty-msg">오류: ' + esc(e.message) + '</div>';
      });
  };

  function renderHistoryCards(items, container) {
    if (items.length === 0) {
      container.innerHTML = '<div class="empty-msg">검사 이력이 없습니다.</div>';
      return;
    }
    container.innerHTML = items.map(function (i) {
      var covPPM = i.coverageRatio != null ? (i.coverageRatio * 1000000).toFixed(1) + ' PPM' : '-';
      var dt = i.inspectedAt ? String(i.inspectedAt).replace('T', ' ').substring(0, 19) : '-';
      var stBadge = i.status === 'COMPLETED'
        ? '<span class="badge badge-active">완료</span>'
        : '<span class="badge badge-inactive">' + esc(i.status || '-') + '</span>';

      var origImg = i.originalImagePath
        ? '<img class="history-card-img" src="' + esc(i.originalImagePath) + '" alt="원본" onclick="openImageModal(\'' + esc(i.originalImagePath) + '\')" onerror="this.outerHTML=\'<div class=history-card-img-placeholder>이미지 없음</div>\'">'
        : '<div class="history-card-img-placeholder">원본 없음</div>';
      var resImg = i.resultImagePath
        ? '<img class="history-card-img" src="' + esc(i.resultImagePath) + '" alt="결과" onclick="openImageModal(\'' + esc(i.resultImagePath) + '\')" onerror="this.outerHTML=\'<div class=history-card-img-placeholder>이미지 없음</div>\'">'
        : '<div class="history-card-img-placeholder">결과 없음</div>';

      return '<div class="history-card">'
        + '<div>' + origImg + '<div style="text-align:center;font-size:11px;color:var(--text3);margin-top:4px;">검사 전 (원본)</div></div>'
        + '<div>' + resImg + '<div style="text-align:center;font-size:11px;color:var(--text3);margin-top:4px;">검사 후 (마킹)</div></div>'
        + '<div class="history-card-info">'
        + '<div class="hci-title">' + esc(i.indBcd || '-') + ' ' + stBadge + '</div>'
        + '<div class="hci-row"><b>자재코드:</b> ' + esc(i.matnr || '-') + '</div>'
        + '<div class="hci-row"><b>자재명:</b> ' + esc(i.matnrNm || '-') + '</div>'
        + '<div class="hci-row"><b>LOT:</b> ' + esc(i.lotnr || '-') + ' (차수: ' + esc(i.indBcdSeq || '-') + ')</div>'
        + '<div class="hci-row"><b>총 지분수:</b> <strong>' + (i.totalCount || 0) + '</strong></div>'
        + '<div class="hci-row"><b>후면 지분 값(PPM):</b> <strong style="color:var(--orange);">' + covPPM + '</strong></div>'
        + '<div class="hci-row"><b>자동:</b> ' + (i.autoCount || 0) + ' | <b>수동:</b> ' + (i.manualCount || 0) + '</div>'
        + '<div class="hci-row"><b>최대임계값:</b> ' + (i.thresholdMax != null ? i.thresholdMax : '-') + '</div>'
        + '<div class="hci-row"><b>검사자:</b> ' + esc(i.operatorNm || '-') + '</div>'
        + '<div class="hci-row" style="font-size:12px;color:var(--text3);">' + dt + '</div>'

        + '</div>'
        + '</div>';
    }).join('');
  }

  function renderPagination(totalPages, currentPage, container, funcName) {
    if (!container || totalPages <= 1) return;
    var html = '';
    html += '<button ' + (currentPage === 0 ? 'disabled' : '') + ' onclick="' + funcName + '(' + (currentPage - 1) + ')">&laquo; 이전</button>';
    var start = Math.max(0, currentPage - 3);
    var end = Math.min(totalPages, start + 7);
    for (var p = start; p < end; p++) {
      html += '<button class="' + (p === currentPage ? 'active' : '') + '" onclick="' + funcName + '(' + p + ')">' + (p + 1) + '</button>';
    }
    html += '<button ' + (currentPage >= totalPages - 1 ? 'disabled' : '') + ' onclick="' + funcName + '(' + (currentPage + 1) + ')">다음 &raquo;</button>';
    container.innerHTML = html;
  }



  // ══════════════════════════════════════════════
  // 이력 테이블 탭 (Detail Table)
  // - 날짜 시작일/종료일 + 바코드 부분일치 검색
  // - 기간 미설정 시 전체 기간 조회
  // - 전체 48 컬럼 (요구사항 완전 일치)
  // ══════════════════════════════════════════════
  var DETAIL_COLSPAN = 47; // thead colspan 합계 (삭제 컬럼 제거됨)

  window.loadDetailTable = function (page) {
    if (page === undefined) page = 0;
    currentDetailPage = page;
    var tbody = document.getElementById('detailTableBody');
    var pag = document.getElementById('dtPagination');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="' + DETAIL_COLSPAN + '" class="empty-msg">불러오는 중...</td></tr>';
    if (pag) pag.innerHTML = '';

    var queryParts = ['page=' + page, 'size=' + detailPageSize];
    if (dtSearchParams.indBcd) queryParts.push('indBcd=' + enc(dtSearchParams.indBcd));
    if (dtSearchParams.dateFrom) queryParts.push('dateFrom=' + enc(dtSearchParams.dateFrom));
    if (dtSearchParams.dateTo) queryParts.push('dateTo=' + enc(dtSearchParams.dateTo));

    api('GET', '/ps-insp-api/inspections?' + queryParts.join('&'))
      .then(function (res) {
        if (res.success && res.data) {
          renderDetailRows(res.data.content || [], tbody);
          renderPagination(res.data.totalPages || 0, page, pag, 'loadDetailTable');
        } else {
          tbody.innerHTML = '<tr><td colspan="' + DETAIL_COLSPAN + '" class="empty-msg">조회 실패</td></tr>';
        }
      })
      .catch(function (e) {
        tbody.innerHTML = '<tr><td colspan="' + DETAIL_COLSPAN + '" class="empty-msg">오류: ' + esc(e.message) + '</td></tr>';
      });
  };

  window.searchDetailTable = function () {
    dtSearchParams.indBcd = val('dt_keyword');
    dtSearchParams.dateFrom = val('dt_dateFrom');
    dtSearchParams.dateTo = val('dt_dateTo');
    loadDetailTable(0);
  };

  window.resetDetailSearch = function () {
    var kwEl = document.getElementById('dt_keyword');
    var dfEl = document.getElementById('dt_dateFrom');
    var dtEl = document.getElementById('dt_dateTo');
    if (kwEl) kwEl.value = '';
    if (dfEl) dfEl.value = '';
    if (dtEl) dtEl.value = '';
    dtSearchParams = { indBcd: '', dateFrom: '', dateTo: '' };
    loadDetailTable(0);
  };

  function renderDetailRows(items, tbody) {
    if (items.length === 0) {
      tbody.innerHTML = '<tr><td colspan="' + DETAIL_COLSPAN + '" class="empty-msg">검사 이력이 없습니다.</td></tr>';
      return;
    }
    window._detailData = items;

    tbody.innerHTML = items.map(function (i) {
      var covPPM = i.coverageRatio != null ? (i.coverageRatio * 1000000).toFixed(1) : '-';
      var inspDt = i.inspectedAt ? String(i.inspectedAt).replace('T', ' ').substring(0, 19) : '-';
      var msrmDt = i.msrmDate ? String(i.msrmDate).replace('T', ' ').substring(0, 19) : '-';
      var crDt = i.createdAt ? String(i.createdAt).replace('T', ' ').substring(0, 19) : '-';
      var stBadge = i.status === 'COMPLETED'
        ? '<span class="badge badge-active">완료</span>'
        : '<span class="badge badge-inactive">' + esc(i.status || '-') + '</span>';

      var origLink = i.originalImagePath
        ? '<a href="' + esc(i.originalImagePath) + '" target="_blank" style="color:var(--primary);">보기</a>' : '-';
      var resLink = i.resultImagePath
        ? '<a href="' + esc(i.resultImagePath) + '" target="_blank" style="color:var(--primary);">보기</a>' : '-';

      return '<tr>'
        // ── 식별 정보 (7) ──
        + '<td>' + i.inspectionId + '</td>'
        + '<td>' + (i.seq != null ? i.seq : '-') + '</td>'
        + '<td><code>' + esc(i.matnr || '-') + '</code></td>'
        + '<td>' + esc(i.matnrNm || '-') + '</td>'
        + '<td>' + esc(i.lotnr || '-') + '</td>'
        + '<td><code>' + esc(i.indBcd || '-') + '</code></td>'
        + '<td>' + esc(i.indBcdSeq || '-') + '</td>'
        // ── 핵심 지표 (4) ──
        + '<td><b>' + covPPM + '</b></td>'
        + '<td><b>' + (i.totalCount || 0) + '</b></td>'
        + '<td>' + (i.densityCount || 0) + '</td>'
        + '<td>' + fmtDec(i.densityRatio) + '</td>'
        // ── 카운트 상세 (5) ──
        + '<td>' + (i.autoCount || 0) + '</td>'
        + '<td>' + (i.manualCount || 0) + '</td>'
        + '<td>' + (i.removedAutoCount || 0) + '</td>'
        + '<td>' + (i.manualAddedCount || 0) + '</td>'
        + '<td>' + (i.manualRemovedCount || 0) + '</td>'
        // ── 균일도 · 통계 (4) ──
        + '<td>' + fmtDec(i.sizeUniformityScore) + '</td>'
        + '<td>' + fmtDec(i.distributionUniformityScore) + '</td>'
        + '<td>' + fmtDec(i.meanSize) + '</td>'
        + '<td>' + fmtDec(i.stdSize) + '</td>'
        // ── 크기 버킷 (4) ──
        + '<td>' + (i.bucketUpTo3 || 0) + '</td>'
        + '<td>' + (i.bucketUpTo5 || 0) + '</td>'
        + '<td>' + (i.bucketUpTo7 || 0) + '</td>'
        + '<td>' + (i.bucketOver7 || 0) + '</td>'
        // ── 사분면 (4) ──
        + '<td>' + (i.quadrantTopLeft || 0) + '</td>'
        + '<td>' + (i.quadrantTopRight || 0) + '</td>'
        + '<td>' + (i.quadrantBottomLeft || 0) + '</td>'
        + '<td>' + (i.quadrantBottomRight || 0) + '</td>'
        // ── 픽셀 (2) ──
        + '<td>' + (i.objectPixelCount || 0) + '</td>'
        + '<td>' + (i.totalPixels || 0) + '</td>'
        // ── 검사 조건 (3) ──
        + '<td>' + (i.thresholdMax != null ? i.thresholdMax : '-') + '</td>'
        + '<td style="font-size:11px;white-space:nowrap;">' + inspDt + '</td>'
        + '<td style="font-size:11px;white-space:nowrap;">' + msrmDt + '</td>'
        // ── 관리 정보 (6) ──
        + '<td>' + esc(i.operatorId || '-') + '</td>'
        + '<td>' + esc(i.operatorNm || '-') + '</td>'
        + '<td>' + esc(i.werks || '-') + '</td>'
        + '<td>' + (i.prcSeqno != null ? i.prcSeqno : '-') + '</td>'
        + '<td><code>' + esc(i.inspItemGrpCd || '-') + '</code></td>'
        + '<td>' + esc(i.deviceId || '-') + '</td>'
        // ── 상태 (2) ──
        + '<td>' + stBadge + '</td>'
        + '<td style="font-size:11px;white-space:nowrap;">' + crDt + '</td>'
        // ── 이미지 (6) ──
        + '<td>' + origLink + '</td>'
        + '<td style="font-size:10px;">' + esc(i.originalImageName || '-') + '</td>'
        + '<td style="font-size:10px;">' + esc(i.originalImageDir || '-') + '</td>'
        + '<td>' + resLink + '</td>'
        + '<td style="font-size:10px;">' + esc(i.resultImageName || '-') + '</td>'
        + '<td style="font-size:10px;">' + esc(i.resultImageDir || '-') + '</td>'

        + '</tr>';
    }).join('');
  }

  function fmtDec(v) {
    if (v == null) return '-';
    var n = parseFloat(v);
    return isNaN(n) ? '-' : n.toFixed(4);
  }

  // ══════════════════════════════════════════════
  // 엑셀 내보내기 (.xlsx) - SheetJS 라이브러리 사용
  // ══════════════════════════════════════════════
  window.exportExcel = function () {
    var items = window._detailData;
    if (!items || items.length === 0) {
      toast('내보낼 데이터가 없습니다. 먼저 조회해주세요.', 'error');
      return;
    }
    if (typeof XLSX === 'undefined') {
      toast('엑셀 라이브러리를 불러오는 중입니다. 잠시 후 다시 시도해주세요.', 'error');
      return;
    }

    // 카테고리 행
    var categoryRow = [
      '식별 정보','식별 정보','식별 정보','식별 정보','식별 정보','식별 정보','식별 정보',
      '핵심 지표','핵심 지표','핵심 지표','핵심 지표',
      '카운트 상세','카운트 상세','카운트 상세','카운트 상세','카운트 상세',
      '균일도·통계','균일도·통계','균일도·통계','균일도·통계',
      '크기 버킷','크기 버킷','크기 버킷','크기 버킷',
      '사분면','사분면','사분면','사분면',
      '픽셀','픽셀',
      '검사 조건','검사 조건','검사 조건',
      '관리 정보','관리 정보','관리 정보','관리 정보','관리 정보','관리 정보',
      '상태','상태',
      '이미지','이미지','이미지','이미지','이미지','이미지'
    ];

    var headerRow = [
      'ID','차수','자재코드','자재명','LOT','바코드','바코드차수',
      '후면 지분 값(PPM)','총 지분','밀도(개)','밀도(%)',
      '자동','수동','제외','수동+','수동-',
      '크기균일','분포균등','평균크기','표준편차',
      '≤3px','≤5px','≤7px','>7px',
      '좌상','우상','좌하','우하',
      '객체px','전체px',
      '임계값','검사일시','측정일시',
      '검사자ID','검사자명','플랜트','처리순번','검사항목','장치ID',
      '상태','등록일시',
      '원본','원본 파일명','원본 저장경로','결과','결과 파일명','결과 저장경로'
    ];

    var aoa = [categoryRow, headerRow];

    items.forEach(function (i) {
      var covPPM = i.coverageRatio != null ? parseFloat((i.coverageRatio * 1000000).toFixed(1)) : '';
      var inspDt = i.inspectedAt ? String(i.inspectedAt).replace('T', ' ').substring(0, 19) : '';
      var msrmDt = i.msrmDate ? String(i.msrmDate).replace('T', ' ').substring(0, 19) : '';
      var crDt = i.createdAt ? String(i.createdAt).replace('T', ' ').substring(0, 19) : '';

      aoa.push([
        i.inspectionId || '', i.seq || '', i.matnr || '', i.matnrNm || '',
        i.lotnr || '', i.indBcd || '', i.indBcdSeq || '',
        covPPM, i.totalCount || 0, i.densityCount || 0, fmtDecNum(i.densityRatio),
        i.autoCount || 0, i.manualCount || 0, i.removedAutoCount || 0,
        i.manualAddedCount || 0, i.manualRemovedCount || 0,
        fmtDecNum(i.sizeUniformityScore), fmtDecNum(i.distributionUniformityScore),
        fmtDecNum(i.meanSize), fmtDecNum(i.stdSize),
        i.bucketUpTo3 || 0, i.bucketUpTo5 || 0, i.bucketUpTo7 || 0, i.bucketOver7 || 0,
        i.quadrantTopLeft || 0, i.quadrantTopRight || 0, i.quadrantBottomLeft || 0, i.quadrantBottomRight || 0,
        i.objectPixelCount || 0, i.totalPixels || 0,
        i.thresholdMax != null ? i.thresholdMax : '', inspDt, msrmDt,
        i.operatorId || '', i.operatorNm || '', i.werks || '',
        i.prcSeqno != null ? i.prcSeqno : '', i.inspItemGrpCd || '', i.deviceId || '',
        i.status || '', crDt,
        i.originalImagePath || '', i.originalImageName || '', i.originalImageDir || '',
        i.resultImagePath || '', i.resultImageName || '', i.resultImageDir || ''
      ]);
    });

    // SheetJS로 xlsx 생성
    var ws = XLSX.utils.aoa_to_sheet(aoa);

    // 카테고리 행 머지 셀 설정
    var merges = [];
    var col = 0;
    var mergeGroups = [7, 4, 5, 4, 4, 4, 2, 3, 6, 2, 6]; // 카테고리별 컬럼 수
    mergeGroups.forEach(function (span) {
      if (span > 1) merges.push({ s: { r: 0, c: col }, e: { r: 0, c: col + span - 1 } });
      col += span;
    });
    ws['!merges'] = merges;

    // 컬럼 너비 자동 설정
    var colWidths = headerRow.map(function (h) { return { wch: Math.max(h.length * 2, 10) }; });
    ws['!cols'] = colWidths;

    var wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, ws, 'PS 후면 지분검사 이력');


    var now_kst = new Date(new Date().getTime() + (9 * 60 * 60 * 1000));
    var dateStr_kst = now_kst.getUTCFullYear()
      + ('0' + (now_kst.getUTCMonth() + 1)).slice(-2)
      + ('0' + now_kst.getUTCDate()).slice(-2)
      + '_' + ('0' + now_kst.getUTCHours()).slice(-2)
      + ('0' + now_kst.getUTCMinutes()).slice(-2);
    XLSX.writeFile(wb, 'PS_후면지분검사_이력_' + dateStr_kst + '.xlsx');
    toast('엑셀 파일(.xlsx)이 다운로드됩니다.', 'success');
  };

  function fmtDecNum(v) {
    if (v == null) return '';
    var n = parseFloat(v);
    return isNaN(n) ? '' : parseFloat(n.toFixed(4));
  }

  // ══════════════════════════════════════════════
  // 지표 주석 접기/펼치기
  // ══════════════════════════════════════════════
  window.toggleAnnotation = function () {
    var btn = document.getElementById('annotationToggle');
    var body = document.getElementById('annotationBody');
    if (!btn || !body) return;
    var isOpen = body.classList.toggle('open');
    btn.classList.toggle('open', isOpen);
    btn.innerHTML = '<span class="annotation-icon" id="annotationIcon">&#9662;</span> '
      + (isOpen ? '지표 설명 접기' : '지표 설명 보기');
  };

  // ══════════════════════════════════════════════
  // Image Modal
  // ══════════════════════════════════════════════
  window.openImageModal = function (src) {
    var modal = document.getElementById('imageModal');
    var img = document.getElementById('modalImage');
    if (modal && img) {
      img.src = src;
      modal.style.display = 'flex';
    }
  };

  window.closeImageModal = function (e) {
    if (e.target.id === 'imageModal') {
      document.getElementById('imageModal').style.display = 'none';
    }
  };

  // ══════════════════════════════════════════════
  // ADMIN 설정 (PPM 기준값 + 권한자 관리 - 공통코드 DB 연동)
  //
  // 권한 체계:
  //   - PPM_ADMIN 코드에 등록된 사용자만 기준값/권한자 수정 가능
  //   - 로그인 토큰으로 자동 인증 (비밀번호 불필요)
  // ══════════════════════════════════════════════

  // 관리자 설정 모달 내부 상태
  var _adminModalState = {
    isAdmin: false,
    currentUser: '',
    adminIds: []
  };

  window.openAdminSettings = function () {
    var modal = document.getElementById('adminSettingsModal');
    if (!modal) return;
    modal.style.display = 'flex';

    // 로딩 표시
    var adminListEl = document.getElementById('adminListContainer');
    if (adminListEl) adminListEl.innerHTML = '<div class="empty-msg">불러오는 중...</div>';

    // PPM 기준값 + 권한자 정보를 동시에 로드
    Promise.all([
      api('GET', '/ps-insp-api/config/ppm-limit'),
      loadPpmAdmins()
    ]).then(function (results) {
      var ppmRes = results[0];
      var adminData = results[1];

      // PPM 기준값 반영
      var ppmInput = document.getElementById('adminPpmLimit');
      if (ppmRes.success && ppmRes.data) {
        var cur = parseFloat(ppmRes.data.ppmLimit) || 0;
        _cachedPpmLimit = cur;
        if (ppmInput) ppmInput.value = cur > 0 ? cur : '';
        _adminModalState.isAdmin = !!ppmRes.data.isAdmin;
      } else {
        var cur2 = getPpmLimit();
        if (ppmInput) ppmInput.value = cur2 > 0 ? cur2 : '';
      }

      // 권한자 목록 반영
      _adminModalState.currentUser = adminData.currentUser || '';
      _adminModalState.adminIds = adminData.adminIds || [];
      _adminModalState.isAdmin = adminData.isAdmin || _adminModalState.isAdmin;

      renderAdminSettingsUI();
    }).catch(function (e) {
      console.warn('[PS-INSP] 관리자 설정 로드 실패:', e);
      var ppmInput = document.getElementById('adminPpmLimit');
      var cur3 = getPpmLimit();
      if (ppmInput) ppmInput.value = cur3 > 0 ? cur3 : '';
      renderAdminSettingsUI();
    });
  };

  /**
   * 관리자 설정 모달 UI 렌더링
   * 권한자인 경우: 편집 가능 / 비권한자: 읽기 전용
   */
  function renderAdminSettingsUI() {
    var isAdmin = _adminModalState.isAdmin;
    var adminIds = _adminModalState.adminIds;
    var currentUser = _adminModalState.currentUser;

    // 현재 사용자 표시
    var userInfoEl = document.getElementById('adminCurrentUser');
    if (userInfoEl) {
      if (currentUser) {
        userInfoEl.innerHTML = '<b>' + esc(currentUser) + '</b>'
          + (isAdmin
            ? ' <span class="badge badge-active">권한자</span>'
            : ' <span class="badge badge-inactive">조회 전용</span>');
      } else {
        userInfoEl.innerHTML = '<span style="color:var(--text3);">로그인 정보 없음</span>';
      }
    }

    // PPM 입력 필드 권한 처리
    var ppmInput = document.getElementById('adminPpmLimit');
    var btnSavePpm = document.getElementById('btnSavePpm');
    if (ppmInput) ppmInput.disabled = !isAdmin;
    if (btnSavePpm) btnSavePpm.disabled = !isAdmin;

    // 권한자 목록 렌더링
    var adminListEl = document.getElementById('adminListContainer');
    if (!adminListEl) return;

    if (adminIds.length === 0) {
      adminListEl.innerHTML = '<div class="empty-msg">등록된 권한자가 없습니다.</div>';
    } else {
      var html = '<ul class="admin-id-list">';
      adminIds.forEach(function (id) {
        var isSelf = (id === currentUser);
        html += '<li class="admin-id-item">';
        html += '<span class="admin-id-name">' + esc(id) + '</span>';
        if (isSelf) html += ' <span class="badge badge-active" style="font-size:10px;">나</span>';
        if (isAdmin && !isSelf) {
          html += ' <button class="btn-icon admin-id-remove" title="삭제" onclick="removeAdminId(\'' + esc(id) + '\')">&times;</button>';
        }
        html += '</li>';
      });
      html += '</ul>';
      adminListEl.innerHTML = html;
    }

    // 권한자 추가 입력 영역
    var addSection = document.getElementById('adminAddSection');
    if (addSection) addSection.style.display = isAdmin ? 'flex' : 'none';

    // 비권한자 안내 메시지
    var noAuthMsg = document.getElementById('adminNoAuthMsg');
    if (noAuthMsg) noAuthMsg.style.display = isAdmin ? 'none' : 'block';
  }

  window.closeAdminSettings = function () {
    document.getElementById('adminSettingsModal').style.display = 'none';
    // 입력 필드 초기화
    var addInput = document.getElementById('adminAddId');
    if (addInput) addInput.value = '';
  };

  /** PPM 기준값 저장 (권한자 인증은 서버에서 토큰으로 자동 수행) */
  window.saveAdminPpmLimit = function () {
    if (!_adminModalState.isAdmin) {
      toast('PPM 기준값 수정 권한이 없습니다.', 'error');
      return;
    }

    var ppmVal = parseFloat(val('adminPpmLimit')) || 0;
    var btnSave = document.getElementById('btnSavePpm');
    if (btnSave) { btnSave.disabled = true; btnSave.textContent = '저장 중...'; }

    savePpmLimitToServer(ppmVal)
      .then(function (res) {
        if (res.success) {
          if (ppmVal > 0) {
            toast('기준값이 ' + ppmVal.toFixed(1) + ' PPM으로 설정되었습니다.', 'success');
          } else {
            toast('PPM 기준값 알림이 비활성화되었습니다.', 'info');
          }
        } else {
          toast(res.message || '기준값 저장 실패', 'error');
        }
      })
      .catch(function (e) {
        toast('기준값 저장 오류: ' + e.message, 'error');
      })
      .finally(function () {
        if (btnSave) { btnSave.disabled = false; btnSave.textContent = '저장'; }
      });
  };

  /** 권한자 ID 추가 */
  window.addAdminId = function () {
    var input = document.getElementById('adminAddId');
    var newId = input ? input.value.trim() : '';
    if (!newId) { toast('추가할 사용자 ID를 입력해주세요.', 'error'); return; }
    if (_adminModalState.adminIds.indexOf(newId) >= 0) {
      toast('이미 등록된 권한자입니다: ' + newId, 'error');
      return;
    }

    var newList = _adminModalState.adminIds.slice();
    newList.push(newId);

    savePpmAdmins(newList)
      .then(function (res) {
        if (res.success && res.data) {
          _adminModalState.adminIds = res.data.adminIds || newList;
          renderAdminSettingsUI();
          if (input) input.value = '';
          toast('권한자가 추가되었습니다: ' + newId, 'success');
        } else {
          toast(res.message || '권한자 추가 실패', 'error');
        }
      })
      .catch(function (e) {
        toast('권한자 추가 오류: ' + e.message, 'error');
      });
  };

  /** 권한자 ID 삭제 */
  window.removeAdminId = function (id) {
    if (id === _adminModalState.currentUser) {
      toast('자기 자신은 삭제할 수 없습니다.', 'error');
      return;
    }
    if (!confirm('권한자 "' + id + '"를 삭제하시겠습니까?')) return;

    var newList = _adminModalState.adminIds.filter(function (a) { return a !== id; });

    savePpmAdmins(newList)
      .then(function (res) {
        if (res.success && res.data) {
          _adminModalState.adminIds = res.data.adminIds || newList;
          renderAdminSettingsUI();
          toast('권한자가 삭제되었습니다: ' + id, 'success');
        } else {
          toast(res.message || '권한자 삭제 실패', 'error');
        }
      })
      .catch(function (e) {
        toast('권한자 삭제 오류: ' + e.message, 'error');
      });
  };

  /** 하위호환: saveAdminSettings (이전 버전 호출 시 PPM 저장으로 리다이렉트) */
  window.saveAdminSettings = function () {
    window.saveAdminPpmLimit();
  };

  // ══════════════════════════════════════════════
  // Utilities
  // ══════════════════════════════════════════════
  function val(id) { var el = document.getElementById(id); return el ? el.value.trim() : ''; }
  function enc(s) { return encodeURIComponent(s); }
  function esc(s) {
    if (!s) return '';
    var d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
  }

  window.toast = function (msg, type) {
    type = type || 'info';
    var c = document.getElementById('toasts');
    if (!c) return;
    var t = document.createElement('div');
    t.className = 'toast toast-' + type;
    t.textContent = msg;
    c.appendChild(t);
    setTimeout(function () {
      t.style.opacity = '0';
      t.style.transition = 'opacity .3s';
      setTimeout(function () { t.remove(); }, 300);
    }, 3000);
  };

  // ══════════════════════════════════════════════
  // DOM Ready
  // ══════════════════════════════════════════════
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }

})();
