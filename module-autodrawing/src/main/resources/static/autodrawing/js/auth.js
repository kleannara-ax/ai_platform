/* ============================================================
   auth.js — 클라이언트 측 인증 관리 (AI Platform JWT 연동 버전)
   
   - 플랫폼 JWT 토큰 기반 로그인/로그아웃
   - /api/auth/login → JWT 토큰 발급
   - /api/auth/me → 세션 확인
   - API 호출 시 Authorization: Bearer {token} 헤더 자동 첨부
   ============================================================ */

const Auth = (() => {
  let _currentUser = null;

  /** 내부: JWT 토큰 저장소 */
  function _getToken() { return localStorage.getItem('ad_jwt_token'); }
  function _setToken(token, refreshToken) {
    localStorage.setItem('ad_jwt_token', token);
    if (refreshToken) localStorage.setItem('ad_jwt_refresh', refreshToken);
  }
  function _clearTokens() {
    localStorage.removeItem('ad_jwt_token');
    localStorage.removeItem('ad_jwt_refresh');
    localStorage.removeItem('ad_session'); // 하위호환
  }

  /** 공통 fetch 헬퍼: JWT 토큰 자동 첨부 */
  async function _authFetch(url, options = {}) {
    const token = _getToken();
    if (token) {
      options.headers = options.headers || {};
      options.headers['Authorization'] = `Bearer ${token}`;
    }
    return fetch(url, options);
  }

  /** 로그인 시도 — 플랫폼 JWT 인증 */
  async function login(userId, password) {
    const res = await fetch('/api/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ loginId: userId, password: password }),
    });
    const data = await res.json();
    if (data.success && data.data) {
      // JWT 토큰 저장
      _setToken(data.data.accessToken, data.data.refreshToken);
      // 사용자 정보 조회
      const user = await checkSession();
      if (user) {
        // 이전 계정/팀의 프로젝트 캐시 제거
        try {
          Object.keys(localStorage)
            .filter(k => k === 'autodrawing_projects' || k.startsWith('autodrawing_projects_'))
            .forEach(k => localStorage.removeItem(k));
        } catch (e) { /* ignore */ }
        return { success: true, user: user };
      }
      return { success: true, user: { id: userId, name: userId } };
    }
    return { success: false, error: data.message || '로그인에 실패했습니다.' };
  }

  /** 로그아웃 */
  async function logout() {
    _currentUser = null;
    _clearTokens();
    // 프로젝트 캐시 제거
    try {
      Object.keys(localStorage)
        .filter(k => k === 'autodrawing_projects' || k.startsWith('autodrawing_projects_'))
        .forEach(k => localStorage.removeItem(k));
    } catch (e) { /* ignore */ }
  }

  /** 세션 확인 — /api/auth/me (JWT 기반) */
  async function checkSession() {
    const token = _getToken();
    if (!token) return null;
    try {
      const res = await _authFetch('/api/auth/me');
      const data = await res.json();
      if (data.success && data.data) {
        _currentUser = {
          id: data.data.loginId || data.data.userId,
          name: data.data.userName || data.data.name || data.data.loginId,
          role: (data.data.roles && data.data.roles.includes('ROLE_ADMIN')) ? 'master' : 'user',
          teamId: 'default',
          teamName: '기본팀',
        };
        return _currentUser;
      }
    } catch (e) { /* ignore */ }
    _clearTokens();
    _currentUser = null;
    return null;
  }

  /** 현재 사용자 */
  function currentUser() { return _currentUser; }

  /** 팀 목록 조회 (Master용 — 플랫폼에서는 부서 관리로 대체) */
  async function getTeams() {
    return { success: true, teams: [{ id: 'default', name: '기본팀', description: '' }] };
  }

  /** 사용자 목록 조회 (Master용 — 플랫폼 사용자 관리 API 연동) */
  async function getUsers() {
    try {
      const res = await _authFetch('/api/users');
      return await res.json();
    } catch (e) {
      return { success: false, error: e.message };
    }
  }

  /** 사용자 생성 (Master용) */
  async function createUser(userData) {
    try {
      const res = await _authFetch('/api/users', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(userData),
      });
      return await res.json();
    } catch (e) {
      return { success: false, error: e.message };
    }
  }

  /** 사용자 삭제 (Master용) */
  async function deleteUser(userId) {
    try {
      const res = await _authFetch(`/api/users/${encodeURIComponent(userId)}`, { method: 'DELETE' });
      return await res.json();
    } catch (e) {
      return { success: false, error: e.message };
    }
  }

  /** 사용자 수정 (Master용) */
  async function updateUser(userId, updates) {
    try {
      const res = await _authFetch(`/api/users/${encodeURIComponent(userId)}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(updates),
      });
      return await res.json();
    } catch (e) {
      return { success: false, error: e.message };
    }
  }

  /** 팀 생성 (Master용) */
  async function createTeam(teamData) {
    return { success: true, team: teamData };
  }

  /** 팀 삭제 (Master용) */
  async function deleteTeam(teamId) {
    return { success: true };
  }

  /** 공개: JWT 인증 fetch (다른 모듈에서 사용) */
  function authFetch(url, options) { return _authFetch(url, options); }

  return {
    login, logout, checkSession, currentUser,
    getTeams, getUsers, createUser, deleteUser, updateUser,
    createTeam, deleteTeam,
    authFetch,
  };
})();
