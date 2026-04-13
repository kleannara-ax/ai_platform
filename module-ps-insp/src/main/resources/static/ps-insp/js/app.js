/**
 * PS 지분 검사 도구 - Application JavaScript
 * AI Platform PS-INSP Module Frontend (v7.0.0)
 *
 * iframe 내부에서 동작하며, 부모 SPA(index.html)에서 JWT 토큰을 URL 파라미터로 전달받습니다.
 */

(function () {
  'use strict';

  // ══════════════════════════════════════════════
  // Constants & State
  // ══════════════════════════════════════════════
  var BASE = window.location.origin;
  var token = '';
  var currentHistoryPage = 0;
  var historyPageSize = 20;
  var currentDetailPage = 0;
  var detailPageSize = 20;

  // ══════════════════════════════════════════════
  // Token Extraction (URL 파라미터 _t 에서 JWT 추출)
  // ══════════════════════════════════════════════
  function extractToken() {
    var params = new URLSearchParams(window.location.search);
    if (params.has('_t')) {
      token = params.get('_t');
    }
    // fallback: 부모 SPA localStorage
    if (!token) {
      try {
        var raw = localStorage.getItem('fireweb_user');
        if (raw) {
          var u = JSON.parse(raw);
          if (u && u.token) token = u.token;
        }
      } catch (e) { /* ignore */ }
    }
  }

  // ══════════════════════════════════════════════
  // API Helper
  // ══════════════════════════════════════════════
  function api(method, path, body) {
    var opts = { method: method, headers: {} };
    if (token) opts.headers['Authorization'] = 'Bearer ' + token;
    if (body && !(body instanceof FormData)) {
      opts.headers['Content-Type'] = 'application/json';
      opts.body = JSON.stringify(body);
    } else if (body instanceof FormData) {
      opts.body = body;
    }
    return fetch(BASE + path, opts).then(function (res) {
      if (res.status === 401) {
        toast('인증이 만료되었습니다. 다시 로그인해주세요.', 'error');
        // 부모 SPA에 알림
        try { window.parent.postMessage({ type: 'FIRE_AUTH_EXPIRED' }, '*'); } catch (e) { }
        throw new Error('Unauthorized');
      }
      return res.json();
    });
  }

  // ══════════════════════════════════════════════
  // Init
  // ══════════════════════════════════════════════
  function init() {
    extractToken();
    checkHealth();
    setupDragDrop('origDropZone', 'f_origImage', 'origPreview', 'origPlaceholder');
    setupDragDrop('resultDropZone', 'f_resultImage', 'resultPreview', 'resultPlaceholder');

    // Auto-fill operator from parent user
    try {
      var raw = localStorage.getItem('fireweb_user');
      if (raw) {
        var u = JSON.parse(raw);
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
        if (data.success) {
          badge.textContent = '서비스 정상';
          badge.className = 'health-badge ok';
        } else {
          badge.textContent = '서비스 오류';
          badge.className = 'health-badge fail';
        }
      })
      .catch(function () {
        badge.textContent = '연결 실패';
        badge.className = 'health-badge fail';
      });
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
    if (tabName === 'history') {
      loadHistory();
    }
    if (tabName === 'detail-table') {
      loadDetailTable();
    }
  };

  // ══════════════════════════════════════════════
  // Image Preview & Drag-Drop
  // ══════════════════════════════════════════════
  window.previewImage = function (input, previewId) {
    var preview = document.getElementById(previewId);
    var placeholder = preview.previousElementSibling || preview.parentElement.querySelector('.upload-placeholder');
    if (input.files && input.files[0]) {
      var reader = new FileReader();
      reader.onload = function (e) {
        preview.src = e.target.result;
        preview.style.display = 'block';
        if (placeholder) placeholder.style.display = 'none';
      };
      reader.readAsDataURL(input.files[0]);
    }
  };

  function setupDragDrop(dropZoneId, fileInputId, previewId, placeholderId) {
    var zone = document.getElementById(dropZoneId);
    if (!zone) return;
    ['dragenter', 'dragover'].forEach(function (ev) {
      zone.addEventListener(ev, function (e) {
        e.preventDefault();
        zone.style.borderColor = 'var(--primary)';
        zone.style.background = 'var(--primary-light)';
      });
    });
    ['dragleave', 'drop'].forEach(function (ev) {
      zone.addEventListener(ev, function (e) {
        e.preventDefault();
        zone.style.borderColor = '';
        zone.style.background = '';
      });
    });
    zone.addEventListener('drop', function (e) {
      e.preventDefault();
      var fileInput = document.getElementById(fileInputId);
      if (e.dataTransfer.files.length > 0) {
        fileInput.files = e.dataTransfer.files;
        previewImage(fileInput, previewId);
      }
    });
  }

  // ══════════════════════════════════════════════
  // Form: Clear
  // ══════════════════════════════════════════════
  window.clearForm = function () {
    var fields = ['f_matnr', 'f_matnrNm', 'f_lotnr', 'f_indBcd', 'f_werks', 'f_inspItemGrpCd',
      'f_thresholdMax', 'f_totalCount', 'f_coverageRatio', 'f_densityCount', 'f_densityRatio',
      'f_sizeUniformityScore', 'f_distributionUniformityScore', 'f_meanSize', 'f_stdSize',
      'f_autoCount', 'f_manualCount', 'f_removedAutoCount',
      'f_bucketUpTo3', 'f_bucketUpTo5', 'f_bucketUpTo7', 'f_bucketOver7',
      'f_quadrantTopLeft', 'f_quadrantTopRight', 'f_quadrantBottomLeft', 'f_quadrantBottomRight',
      'f_objectPixelCount', 'f_totalPixels', 'f_manualAddedCount', 'f_manualRemovedCount'];
    fields.forEach(function (id) {
      var el = document.getElementById(id);
      if (el) {
        if (id === 'f_inspItemGrpCd') el.value = 'COV_INS';
        else if (id === 'f_thresholdMax') el.value = '115';
        else el.value = id.indexOf('Count') >= 0 || id.indexOf('bucket') >= 0 || id.indexOf('quadrant') >= 0 || id.indexOf('Pixel') >= 0 || id.indexOf('Ratio') >= 0 || id.indexOf('Score') >= 0 || id.indexOf('Size') >= 0 ? '0' : '';
      }
    });
    // Reset images
    ['origPreview', 'resultPreview'].forEach(function (pid) {
      var p = document.getElementById(pid);
      if (p) { p.style.display = 'none'; p.src = ''; }
    });
    ['origPlaceholder', 'resultPlaceholder'].forEach(function (pid) {
      var p = document.getElementById(pid);
      if (p) p.style.display = '';
    });
    ['f_origImage', 'f_resultImage'].forEach(function (fid) {
      var f = document.getElementById(fid);
      if (f) f.value = '';
    });
    // Hide results
    var sr = document.getElementById('saveResult');
    if (sr) sr.style.display = 'none';
    var ec = document.getElementById('existsCheck');
    if (ec) ec.style.display = 'none';
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
            ec.innerHTML = '&#9888; 동일한 자재+LOT+바코드 조합이 이미 존재합니다 (ID: ' + res.data.inspectionId + '). 저장 시 업데이트됩니다.';
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
  // Save Inspection
  // ══════════════════════════════════════════════
  window.saveInspection = function () {
    var metadata = buildMetadata();
    if (!metadata) return;

    var btnSave = document.getElementById('btnSave');
    if (btnSave) { btnSave.disabled = true; btnSave.textContent = '저장 중...'; }

    var origFile = document.getElementById('f_origImage').files[0];
    var resultFile = document.getElementById('f_resultImage').files[0];

    var promise;
    if (origFile || resultFile) {
      // Multipart upload
      var fd = new FormData();
      fd.append('metadata', JSON.stringify(metadata));
      if (origFile) fd.append('originalImage', origFile);
      if (resultFile) fd.append('resultImage', resultFile);
      promise = api('POST', '/ps-insp-api/inspections', fd);
    } else {
      // JSON only
      promise = api('POST', '/ps-insp-api/inspections', metadata);
    }

    promise.then(function (res) {
      if (res.success && res.data) {
        toast('검사 결과가 저장되었습니다. (ID: ' + res.data.inspectionId + ')', 'success');
        showSaveResult(res.data);
      } else {
        var msg = res.message || '저장 실패';
        if (res.data && typeof res.data === 'object') {
          var errs = Object.entries(res.data).map(function (e) { return e[0] + ': ' + e[1]; });
          if (errs.length) msg = errs.join(', ');
        }
        toast(msg, 'error');
      }
    })
      .catch(function (e) { toast('저장 오류: ' + e.message, 'error'); })
      .finally(function () {
        if (btnSave) { btnSave.disabled = false; btnSave.textContent = '검사 결과 저장'; }
      });
  };

  function buildMetadata() {
    var matnr = val('f_matnr');
    var lotnr = val('f_lotnr');
    var indBcd = val('f_indBcd');
    if (!matnr || !lotnr || !indBcd) {
      toast('자재코드, LOT, 바코드는 필수입니다.', 'error');
      return null;
    }
    return {
      inspItemGrpCd: val('f_inspItemGrpCd') || 'COV_INS',
      matnr: matnr,
      matnrNm: val('f_matnrNm'),
      werks: val('f_werks'),
      lotnr: lotnr,
      indBcd: indBcd,
      inspectedAt: new Date().toISOString(),
      thresholdMax: intVal('f_thresholdMax', 115),
      totalCount: intVal('f_totalCount', 0),
      coverageRatio: floatVal('f_coverageRatio', 0),
      densityCount: intVal('f_densityCount', 0),
      densityRatio: floatVal('f_densityRatio', 0),
      sizeUniformityScore: floatVal('f_sizeUniformityScore', 0),
      distributionUniformityScore: floatVal('f_distributionUniformityScore', 0),
      meanSize: floatVal('f_meanSize', 0),
      stdSize: floatVal('f_stdSize', 0),
      autoCount: intVal('f_autoCount', 0),
      manualCount: intVal('f_manualCount', 0),
      removedAutoCount: intVal('f_removedAutoCount', 0),
      bucketUpTo3: intVal('f_bucketUpTo3', 0),
      bucketUpTo5: intVal('f_bucketUpTo5', 0),
      bucketUpTo7: intVal('f_bucketUpTo7', 0),
      bucketOver7: intVal('f_bucketOver7', 0),
      quadrantTopLeft: intVal('f_quadrantTopLeft', 0),
      quadrantTopRight: intVal('f_quadrantTopRight', 0),
      quadrantBottomLeft: intVal('f_quadrantBottomLeft', 0),
      quadrantBottomRight: intVal('f_quadrantBottomRight', 0),
      objectPixelCount: intVal('f_objectPixelCount', 0),
      totalPixels: intVal('f_totalPixels', 0),
      manualAddedCount: intVal('f_manualAddedCount', 0),
      manualRemovedCount: intVal('f_manualRemovedCount', 0),
      operatorId: val('f_operatorId'),
      operatorNm: val('f_operatorNm'),
      status: 'COMPLETED'
    };
  }

  function showSaveResult(data) {
    var el = document.getElementById('saveResult');
    var body = document.getElementById('saveResultBody');
    if (!el || !body) return;
    var covPct = data.coverageRatio != null ? (data.coverageRatio * 100).toFixed(2) + '%' : '-';
    body.innerHTML = '<div class="result-summary">'
      + '<div class="result-item"><div class="label">검사 ID</div><div class="value blue">' + data.inspectionId + '</div></div>'
      + '<div class="result-item"><div class="label">총 지분수</div><div class="value green">' + (data.totalCount || 0) + '</div></div>'
      + '<div class="result-item"><div class="label">지분 커버리지</div><div class="value orange">' + covPct + '</div></div>'
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
  // History - Load
  // ══════════════════════════════════════════════
  window.loadHistory = function (page) {
    if (page === undefined) page = 0;
    currentHistoryPage = page;
    var tbody = document.getElementById('historyTableBody');
    var pag = document.getElementById('pagination');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="14" class="empty-msg">불러오는 중...</td></tr>';
    if (pag) pag.innerHTML = '';

    api('GET', '/ps-insp-api/inspections?page=' + page + '&size=' + historyPageSize)
      .then(function (res) {
        if (res.success && res.data) {
          var items = res.data.content || [];
          var totalElements = res.data.totalElements || 0;
          var totalPages = res.data.totalPages || 0;
          renderHistoryRows(items, tbody);
          renderPagination(totalPages, page, pag);
        } else {
          tbody.innerHTML = '<tr><td colspan="14" class="empty-msg">조회 실패</td></tr>';
        }
      })
      .catch(function (e) {
        tbody.innerHTML = '<tr><td colspan="14" class="empty-msg">오류: ' + esc(e.message) + '</td></tr>';
      });
  };

  // History - Search
  window.searchHistory = function () {
    var keyword = val('h_keyword');
    var type = document.getElementById('h_searchType') ? document.getElementById('h_searchType').value : 'indBcd';
    if (!keyword) { loadHistory(); return; }
    var tbody = document.getElementById('historyTableBody');
    var pag = document.getElementById('pagination');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="14" class="empty-msg">검색 중...</td></tr>';
    if (pag) pag.innerHTML = '';

    api('GET', '/ps-insp-api/inspections/search?type=' + enc(type) + '&keyword=' + enc(keyword) + '&page=0&size=50')
      .then(function (res) {
        if (res.success && res.data) {
          var items = res.data.content || [];
          renderHistoryRows(items, tbody);
        } else {
          tbody.innerHTML = '<tr><td colspan="14" class="empty-msg">검색 결과 없음</td></tr>';
        }
      })
      .catch(function (e) {
        tbody.innerHTML = '<tr><td colspan="14" class="empty-msg">오류: ' + esc(e.message) + '</td></tr>';
      });
  };

  function renderHistoryRows(items, tbody) {
    if (items.length === 0) {
      tbody.innerHTML = '<tr><td colspan="14" class="empty-msg">검사 이력이 없습니다.</td></tr>';
      return;
    }
    tbody.innerHTML = items.map(function (i) {
      var covPct = i.coverageRatio != null ? (i.coverageRatio * 100).toFixed(2) + '%' : '-';
      var dt = i.inspectedAt ? String(i.inspectedAt).replace('T', ' ').substring(0, 19) : '-';
      var stBadge = i.status === 'COMPLETED'
        ? '<span class="badge badge-active">완료</span>'
        : '<span class="badge badge-inactive">' + esc(i.status || '-') + '</span>';
      return '<tr>'
        + '<td>' + i.inspectionId + '</td>'
        + '<td><code>' + esc(i.matnr || '-') + '</code></td>'
        + '<td>' + esc(i.matnrNm || '-') + '</td>'
        + '<td>' + esc(i.lotnr || '-') + '</td>'
        + '<td><code>' + esc(i.indBcd || '-') + '</code></td>'
        + '<td>' + esc(i.indBcdSeq || '-') + '</td>'
        + '<td><b>' + (i.totalCount || 0) + '</b></td>'
        + '<td>' + covPct + '</td>'
        + '<td>' + (i.autoCount || 0) + '</td>'
        + '<td>' + (i.manualCount || 0) + '</td>'
        + '<td>' + stBadge + '</td>'
        + '<td>' + esc(i.operatorNm || '-') + '</td>'
        + '<td style="font-size:12px;">' + dt + '</td>'
        + '<td><button class="btn-icon" title="삭제" onclick="deleteInspection(' + i.inspectionId + ')" style="color:var(--red);">&#128465;</button></td>'
        + '</tr>';
    }).join('');
  }

  function renderPagination(totalPages, currentPage, container) {
    if (!container || totalPages <= 1) return;
    var html = '';
    html += '<button ' + (currentPage === 0 ? 'disabled' : '') + ' onclick="loadHistory(' + (currentPage - 1) + ')">&laquo; 이전</button>';
    var start = Math.max(0, currentPage - 3);
    var end = Math.min(totalPages, start + 7);
    for (var p = start; p < end; p++) {
      html += '<button class="' + (p === currentPage ? 'active' : '') + '" onclick="loadHistory(' + p + ')">' + (p + 1) + '</button>';
    }
    html += '<button ' + (currentPage >= totalPages - 1 ? 'disabled' : '') + ' onclick="loadHistory(' + (currentPage + 1) + ')">다음 &raquo;</button>';
    container.innerHTML = html;
  }

  // History - Delete
  window.deleteInspection = function (id) {
    if (!confirm('검사 결과 (ID: ' + id + ')를 삭제하시겠습니까?')) return;
    api('DELETE', '/ps-insp-api/inspections/' + id)
      .then(function (res) {
        if (res.success) {
          toast('삭제되었습니다.', 'success');
          loadHistory(currentHistoryPage);
        } else {
          toast(res.message || '삭제 실패', 'error');
        }
      })
      .catch(function (e) { toast('삭제 오류: ' + e.message, 'error'); });
  };

  // ══════════════════════════════════════════════
  // Detail Table - Load (이력 테이블)
  // ══════════════════════════════════════════════
  window.loadDetailTable = function (page) {
    if (page === undefined) page = 0;
    currentDetailPage = page;
    var tbody = document.getElementById('detailTableBody');
    var pag = document.getElementById('dtPagination');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="38" class="empty-msg">불러오는 중...</td></tr>';
    if (pag) pag.innerHTML = '';

    api('GET', '/ps-insp-api/inspections?page=' + page + '&size=' + detailPageSize)
      .then(function (res) {
        if (res.success && res.data) {
          var items = res.data.content || [];
          var totalPages = res.data.totalPages || 0;
          renderDetailRows(items, tbody);
          renderDetailPagination(totalPages, page, pag);
        } else {
          tbody.innerHTML = '<tr><td colspan="38" class="empty-msg">조회 실패</td></tr>';
        }
      })
      .catch(function (e) {
        tbody.innerHTML = '<tr><td colspan="38" class="empty-msg">오류: ' + esc(e.message) + '</td></tr>';
      });
  };

  // Detail Table - Search
  window.searchDetailTable = function () {
    var keyword = val('dt_keyword');
    var type = document.getElementById('dt_searchType') ? document.getElementById('dt_searchType').value : 'indBcd';
    if (!keyword) { loadDetailTable(); return; }
    var tbody = document.getElementById('detailTableBody');
    var pag = document.getElementById('dtPagination');
    if (!tbody) return;
    tbody.innerHTML = '<tr><td colspan="38" class="empty-msg">검색 중...</td></tr>';
    if (pag) pag.innerHTML = '';

    api('GET', '/ps-insp-api/inspections/search?type=' + enc(type) + '&keyword=' + enc(keyword) + '&page=0&size=50')
      .then(function (res) {
        if (res.success && res.data) {
          var items = res.data.content || [];
          renderDetailRows(items, tbody);
        } else {
          tbody.innerHTML = '<tr><td colspan="38" class="empty-msg">검색 결과 없음</td></tr>';
        }
      })
      .catch(function (e) {
        tbody.innerHTML = '<tr><td colspan="38" class="empty-msg">오류: ' + esc(e.message) + '</td></tr>';
      });
  };

  function renderDetailRows(items, tbody) {
    if (items.length === 0) {
      tbody.innerHTML = '<tr><td colspan="38" class="empty-msg">검사 이력이 없습니다.</td></tr>';
      return;
    }
    tbody.innerHTML = items.map(function (i) {
      var covPct = i.coverageRatio != null ? (i.coverageRatio * 100).toFixed(4) + '%' : '-';
      var dt = i.inspectedAt ? String(i.inspectedAt).replace('T', ' ').substring(0, 19) : '-';
      var ct = i.createdAt ? String(i.createdAt).replace('T', ' ').substring(0, 19) : '-';
      var stBadge = i.status === 'COMPLETED'
        ? '<span class="badge badge-active">완료</span>'
        : '<span class="badge badge-inactive">' + esc(i.status || '-') + '</span>';
      return '<tr>'
        + '<td>' + i.inspectionId + '</td>'
        + '<td>' + (i.seq != null ? i.seq : '-') + '</td>'
        + '<td><code>' + esc(i.inspItemGrpCd || '-') + '</code></td>'
        + '<td><code>' + esc(i.matnr || '-') + '</code></td>'
        + '<td>' + esc(i.matnrNm || '-') + '</td>'
        + '<td>' + esc(i.werks || '-') + '</td>'
        + '<td>' + esc(i.lotnr || '-') + '</td>'
        + '<td><code>' + esc(i.indBcd || '-') + '</code></td>'
        + '<td>' + esc(i.indBcdSeq || '-') + '</td>'
        + '<td>' + (i.thresholdMax != null ? i.thresholdMax : '-') + '</td>'
        + '<td><b>' + (i.totalCount || 0) + '</b></td>'
        + '<td>' + covPct + '</td>'
        + '<td>' + (i.densityCount || 0) + '</td>'
        + '<td>' + fmtDec(i.densityRatio) + '</td>'
        + '<td>' + fmtDec(i.sizeUniformityScore) + '</td>'
        + '<td>' + fmtDec(i.distributionUniformityScore) + '</td>'
        + '<td>' + fmtDec(i.meanSize) + '</td>'
        + '<td>' + fmtDec(i.stdSize) + '</td>'
        + '<td>' + (i.autoCount || 0) + '</td>'
        + '<td>' + (i.manualCount || 0) + '</td>'
        + '<td>' + (i.removedAutoCount || 0) + '</td>'
        + '<td>' + (i.bucketUpTo3 || 0) + '</td>'
        + '<td>' + (i.bucketUpTo5 || 0) + '</td>'
        + '<td>' + (i.bucketUpTo7 || 0) + '</td>'
        + '<td>' + (i.bucketOver7 || 0) + '</td>'
        + '<td>' + (i.quadrantTopLeft || 0) + '</td>'
        + '<td>' + (i.quadrantTopRight || 0) + '</td>'
        + '<td>' + (i.quadrantBottomLeft || 0) + '</td>'
        + '<td>' + (i.quadrantBottomRight || 0) + '</td>'
        + '<td>' + (i.objectPixelCount || 0) + '</td>'
        + '<td>' + (i.totalPixels || 0) + '</td>'
        + '<td>' + (i.manualAddedCount || 0) + '</td>'
        + '<td>' + (i.manualRemovedCount || 0) + '</td>'
        + '<td>' + esc(i.operatorId || '-') + '</td>'
        + '<td>' + esc(i.operatorNm || '-') + '</td>'
        + '<td>' + stBadge + '</td>'
        + '<td style="font-size:12px;white-space:nowrap;">' + dt + '</td>'
        + '<td style="font-size:12px;white-space:nowrap;">' + ct + '</td>'
        + '</tr>';
    }).join('');
  }

  function renderDetailPagination(totalPages, currentPage, container) {
    if (!container || totalPages <= 1) return;
    var html = '';
    html += '<button ' + (currentPage === 0 ? 'disabled' : '') + ' onclick="loadDetailTable(' + (currentPage - 1) + ')">&laquo; 이전</button>';
    var start = Math.max(0, currentPage - 3);
    var end = Math.min(totalPages, start + 7);
    for (var p = start; p < end; p++) {
      html += '<button class="' + (p === currentPage ? 'active' : '') + '" onclick="loadDetailTable(' + p + ')">' + (p + 1) + '</button>';
    }
    html += '<button ' + (currentPage >= totalPages - 1 ? 'disabled' : '') + ' onclick="loadDetailTable(' + (currentPage + 1) + ')">다음 &raquo;</button>';
    container.innerHTML = html;
  }

  function fmtDec(v) {
    if (v == null) return '-';
    var n = parseFloat(v);
    return isNaN(n) ? '-' : n.toFixed(4);
  }

  // ══════════════════════════════════════════════
  // Utilities
  // ══════════════════════════════════════════════
  function val(id) {
    var el = document.getElementById(id);
    return el ? el.value.trim() : '';
  }
  function intVal(id, def) {
    var v = parseInt(val(id), 10);
    return isNaN(v) ? def : v;
  }
  function floatVal(id, def) {
    var v = parseFloat(val(id));
    return isNaN(v) ? def : v;
  }
  function enc(s) { return encodeURIComponent(s); }
  function esc(s) {
    if (!s) return '';
    var d = document.createElement('div');
    d.textContent = s;
    return d.innerHTML;
  }

  // Toast
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
