/**
 * 통합 프리뷰 서버
 * - Spring Boot 백엔드 없이 프론트엔드 UI를 미리볼 수 있도록 정적 파일 서빙
 * - 각 모듈의 static 리소스를 실제 Spring Boot와 동일한 URL 구조로 매핑
 * - API 요청은 Mock 응답 반환
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 3000;

// ── MIME Types ──
const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js':   'application/javascript; charset=utf-8',
  '.css':  'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.jpeg': 'image/jpeg',
  '.gif':  'image/gif',
  '.svg':  'image/svg+xml',
  '.ico':  'image/x-icon',
  '.woff': 'font/woff',
  '.woff2':'font/woff2',
  '.ttf':  'font/ttf',
  '.pdf':  'application/pdf',
};

// ── Static Roots (Spring Boot가 서빙하는 것과 동일한 구조) ──
const STATIC_DIRS = [
  path.join(__dirname, 'app/src/main/resources/static'),
  path.join(__dirname, 'module-fire/src/main/resources/static'),
  path.join(__dirname, 'module-ps-insp/src/main/resources/static'),
];
const TEMPLATE_DIR = path.join(__dirname, 'module-ps-insp/src/main/resources/templates');
const TEST_UI_DIR = path.join(__dirname, 'test-ui');

// ── Mock API Responses ──
const mockUser = {
  userId: 1, loginId: 'admin', userName: '관리자',
  email: 'admin@company.com', phone: '010-1234-5678',
  role: 'ROLE_ADMIN', enabled: true
};

const mockMenus = [
  { menuId:1, menuCode:'DASHBOARD', menuName:'대시보드', menuType:'MENU', menuUrl:'/dashboard', icon:'dashboard', parentId:null, sortOrder:0, isActive:true, isVisible:true, allowedIps:null, description:'메인 대시보드', children:[] },
  { menuId:2, menuCode:'USER_MGMT', menuName:'사용자 관리', menuType:'MENU', menuUrl:'/users', icon:'users', parentId:null, sortOrder:1, isActive:true, isVisible:true, allowedIps:null, description:'사용자 CRUD', children:[] },
  { menuId:3, menuCode:'MENU_MGMT', menuName:'메뉴 관리', menuType:'MENU', menuUrl:'/menus', icon:'menu', parentId:null, sortOrder:2, isActive:true, isVisible:true, allowedIps:null, description:'메뉴 관리', children:[] },
  { menuId:4, menuCode:'PERM_MGMT', menuName:'접근 권한', menuType:'MENU', menuUrl:'/permissions', icon:'lock', parentId:null, sortOrder:3, isActive:true, isVisible:true, allowedIps:null, description:'접근 권한 설정', children:[] },
  { menuId:5, menuCode:'CODE_MGMT', menuName:'공통코드 관리', menuType:'MENU', menuUrl:'/codes', icon:'code', parentId:null, sortOrder:4, isActive:true, isVisible:true, allowedIps:null, description:'공통코드 관리', children:[] },
  { menuId:6, menuCode:'FIRE_MGMT', menuName:'소방시설관리', menuType:'MENU', menuUrl:'/fire', icon:'fire', parentId:null, sortOrder:5, isActive:true, isVisible:true, allowedIps:null, description:'소방시설 관리',
    children: [
      { menuId:61, menuCode:'FIRE_DASHBOARD', menuName:'소방 대시보드', menuType:'MENU', menuUrl:'/fire/dashboard', parentId:6, sortOrder:0, isActive:true, isVisible:true, allowedIps:null },
      { menuId:62, menuCode:'FIRE_MAP', menuName:'도면 (메인)', menuType:'MENU', menuUrl:'/fire-map.html', parentId:6, sortOrder:1, isActive:true, isVisible:true, allowedIps:null },
      { menuId:63, menuCode:'FIRE_EXTINGUISHER', menuName:'소화기 목록', menuType:'MENU', menuUrl:'/extinguishers.html', parentId:6, sortOrder:2, isActive:true, isVisible:true, allowedIps:null },
      { menuId:64, menuCode:'FIRE_HYDRANT', menuName:'소화전 목록', menuType:'MENU', menuUrl:'/hydrants.html', parentId:6, sortOrder:3, isActive:true, isVisible:true, allowedIps:null },
      { menuId:65, menuCode:'FIRE_RECEIVER', menuName:'수신기 목록', menuType:'MENU', menuUrl:'/receivers.html', parentId:6, sortOrder:4, isActive:true, isVisible:true, allowedIps:null },
      { menuId:66, menuCode:'FIRE_PUMP', menuName:'소방펌프 목록', menuType:'MENU', menuUrl:'/pumps.html', parentId:6, sortOrder:5, isActive:true, isVisible:true, allowedIps:null },
      { menuId:67, menuCode:'FIRE_FLOOR', menuName:'층별 도면', menuType:'MENU', menuUrl:'/maps/floor.html', parentId:6, sortOrder:6, isActive:true, isVisible:true, allowedIps:null },
      { menuId:68, menuCode:'FIRE_QR', menuName:'QR코드', menuType:'MENU', menuUrl:'/qr', parentId:6, sortOrder:7, isActive:true, isVisible:true, allowedIps:null },
    ]
  },
];

const mockRoles = [
  { code: 'ROLE_ADMIN', codeName: '관리자', sortOrder: 1 },
  { code: 'ROLE_MANAGER', codeName: '매니저', sortOrder: 2 },
  { code: 'ROLE_USER', codeName: '사용자', sortOrder: 3 },
  { code: 'ROLE_FIRE_MANAGER', codeName: '소방관리자', sortOrder: 4 },
];

const mockDepts = [
  { code: 'DEPT001', codeName: '경영지원팀' },
  { code: 'DEPT002', codeName: '기술개발팀' },
  { code: 'DEPT003', codeName: '영업팀' },
];

const mockUsers = [
  { userId:1, loginId:'admin', userName:'관리자', email:'admin@company.com', phone:'010-1234-5678', role:'ROLE_ADMIN', enabled:true, deptName:'경영지원팀', deptCode:'DEPT001', position:'부장', jobTitle:'팀장', employeeNo:'EMP-0001', joinDate:'2020-01-15', officePhone:'02-1234-5678', internalExt:'1001' },
  { userId:2, loginId:'manager01', userName:'김매니저', email:'manager@company.com', phone:'010-2345-6789', role:'ROLE_MANAGER', enabled:true, deptName:'기술개발팀', deptCode:'DEPT002', position:'과장', jobTitle:'팀원', employeeNo:'EMP-0002', joinDate:'2021-03-01', officePhone:'02-1234-5679', internalExt:'1002' },
  { userId:3, loginId:'user01', userName:'이사용자', email:'user01@company.com', phone:'010-3456-7890', role:'ROLE_USER', enabled:true, deptName:'영업팀', deptCode:'DEPT003', position:'대리', jobTitle:'팀원', employeeNo:'EMP-0003', joinDate:'2022-06-15', officePhone:'02-1234-5680', internalExt:'1003' },
  { userId:4, loginId:'fire01', userName:'박소방', email:'fire01@company.com', phone:'010-4567-8901', role:'ROLE_FIRE_MANAGER', enabled:true, deptName:'경영지원팀', deptCode:'DEPT001', position:'과장', jobTitle:'소방안전담당', employeeNo:'EMP-0004', joinDate:'2021-09-01', officePhone:'02-1234-5681', internalExt:'1004' },
  { userId:5, loginId:'user02', userName:'최직원', email:'user02@company.com', phone:'010-5678-9012', role:'ROLE_USER', enabled:false, deptName:'기술개발팀', deptCode:'DEPT002', position:'사원', jobTitle:'팀원', employeeNo:'EMP-0005', joinDate:'2023-01-10', officePhone:'02-1234-5682', internalExt:'1005' },
];

const mockCodeGroups = [
  { groupId:1, groupCode:'ROLE', groupName:'역할', description:'사용자 역할 분류', sortOrder:1, isActive:true, codeCount:4 },
  { groupId:2, groupCode:'DEPT', groupName:'부서', description:'부서 코드', sortOrder:2, isActive:true, codeCount:3 },
  { groupId:3, groupCode:'USER_STATUS', groupName:'사용자 상태', description:'사용자 활성/비활성 상태', sortOrder:3, isActive:true, codeCount:2 },
];

const mockPermissions = mockRoles.map(r => ({
  role: r.code,
  roleDescription: r.codeName,
  menuIds: r.code === 'ROLE_ADMIN' ? [1,2,3,4,5,6,61,62,63,64,65,66,67,68] :
           r.code === 'ROLE_MANAGER' ? [1,2,5,6,61,62,63,64,65,66,67,68] :
           r.code === 'ROLE_FIRE_MANAGER' ? [1,6,61,62,63,64,65,66,67,68] :
           [1,6,61]
}));

// ── Helper: Mock PS-INSP Inspection Data (20건) ──
function _mockInspections() {
  var matnrs = ['MAT-001','MAT-002','MAT-003','MAT-004','MAT-005'];
  var matnrNms = ['PE필름 A등급','PP필름 B등급','PET필름 C등급','PE필름 특수','PP필름 고급'];
  var operators = [
    {id:'admin', nm:'관리자'}, {id:'kim01', nm:'김검사'}, {id:'lee02', nm:'이품질'},
    {id:'park03', nm:'박기술'}, {id:'choi04', nm:'최현장'}
  ];
  var statuses = ['COMPLETED','COMPLETED','COMPLETED','COMPLETED','COMPLETED','COMPLETED','COMPLETED','COMPLETED','IN_PROGRESS','COMPLETED'];
  var mesStatuses = ['SENT','SENT','SENT','SENT','PENDING','SENT','FAILED','SENT','PENDING','SENT'];

  var items = [];
  for (var i = 1; i <= 20; i++) {
    var mi = (i - 1) % matnrs.length;
    var oi = (i - 1) % operators.length;
    var totalCount = 5 + Math.floor(Math.random() * 80);
    var autoCount = Math.max(1, totalCount - Math.floor(Math.random() * 6));
    var manualCount = totalCount - autoCount;
    var removedAutoCount = Math.floor(Math.random() * 4);
    var totalPixels = 2560000;
    var objectPixelCount = Math.floor(Math.random() * 3000) + 50;
    var coverageRatio = objectPixelCount / totalPixels;
    var densityCount = Math.max(0, Math.floor(totalCount * 0.4 + Math.random() * totalCount * 0.3));
    var threshold = 110 + Math.floor(Math.random() * 20);
    var meanSize = 3 + Math.random() * 15;
    var stdSize = 1 + Math.random() * 5;
    var day = String(1 + Math.floor((i - 1) / 3)).padStart(2, '0');
    var hour = String(8 + (i % 10)).padStart(2, '0');
    var minute = String(Math.floor(Math.random() * 60)).padStart(2, '0');
    var inspectedAt = '2025-05-' + day + 'T' + hour + ':' + minute + ':00';
    var msrmDate = inspectedAt;

    items.push({
      inspectionId: i,
      seq: ((i - 1) % 3) + 1,
      indBcd: 'IND-2025-' + String(i).padStart(3, '0'),
      indBcdSeq: String(((i - 1) % 3) + 1),
      lotNo: 'LOT-' + String.fromCharCode(65 + (i % 5)) + String(100 + i),
      lotnr: 'LOT-' + String.fromCharCode(65 + (i % 5)) + String(100 + i),
      matnr: matnrs[mi],
      matnrNm: matnrNms[mi],
      coverageRatio: coverageRatio,
      totalCount: totalCount,
      densityCount: densityCount,
      densityRatio: totalPixels > 0 ? densityCount / totalPixels : 0,
      autoCount: autoCount,
      manualCount: manualCount,
      removedAutoCount: removedAutoCount,
      manualAddedCount: manualCount,
      manualRemovedCount: removedAutoCount,
      sizeUniformityScore: 0.6 + Math.random() * 0.35,
      distributionUniformityScore: 0.5 + Math.random() * 0.45,
      meanSize: meanSize,
      stdSize: stdSize,
      bucketUpTo3: Math.floor(totalCount * 0.15 + Math.random() * 5),
      bucketUpTo5: Math.floor(totalCount * 0.30 + Math.random() * 5),
      bucketUpTo7: Math.floor(totalCount * 0.25 + Math.random() * 5),
      bucketOver7: Math.floor(totalCount * 0.10 + Math.random() * 3),
      quadrantTopLeft: Math.floor(totalCount * 0.2 + Math.random() * 5),
      quadrantTopRight: Math.floor(totalCount * 0.25 + Math.random() * 5),
      quadrantBottomLeft: Math.floor(totalCount * 0.25 + Math.random() * 5),
      quadrantBottomRight: Math.floor(totalCount * 0.2 + Math.random() * 5),
      objectPixelCount: objectPixelCount,
      totalPixels: totalPixels,
      thresholdMax: threshold,
      inspectedAt: inspectedAt,
      msrmDate: msrmDate,
      createdAt: inspectedAt,
      operatorId: operators[oi].id,
      operatorNm: operators[oi].nm,
      werks: 'P100',
      prcSeqno: i,
      inspItemGrpCd: 'COV_INS',
      deviceId: 'TAB-' + String(1 + (i % 3)).padStart(2, '0'),
      status: statuses[(i - 1) % statuses.length],
      mesSendStatus: mesStatuses[(i - 1) % mesStatuses.length],
      originalImagePath: null,
      originalImageName: null,
      originalImageDir: null,
      resultImagePath: null,
      resultImageName: null,
      resultImageDir: null
    });
  }
  return items;
}

// ── Helper: Filter Inspections by query params ──
function _filterInspections(items, query) {
  var dateFrom = query.dateFrom || '';
  var dateTo = query.dateTo || '';
  var indBcd = query.indBcd || '';
  var keyword = query.keyword || '';
  var type = query.type || '';

  return items.filter(function (i) {
    // 날짜 필터 (inspectedAt 기준, YYYY-MM-DD 비교)
    if (dateFrom) {
      var d = (i.inspectedAt || '').substring(0, 10);
      if (d < dateFrom) return false;
    }
    if (dateTo) {
      var d = (i.inspectedAt || '').substring(0, 10);
      if (d > dateTo) return false;
    }
    // 바코드 부분일치 (이력 테이블)
    if (indBcd && i.indBcd) {
      if (i.indBcd.indexOf(indBcd) === -1) return false;
    }
    // 키워드 검색 (검사 이력 탭)
    if (keyword && type) {
      var target = '';
      if (type === 'indBcd') target = i.indBcd || '';
      else if (type === 'matnr') target = i.matnr || '';
      else if (type === 'lotnr') target = (i.lotnr || i.lotNo || '');
      else if (type === 'operatorNm') target = i.operatorNm || '';
      if (target.indexOf(keyword) === -1) return false;
    }
    return true;
  });
}

// ── Helper: JSON Response ──
function jsonRes(res, data, status = 200) {
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
  res.end(JSON.stringify(data));
}
function apiOk(res, data) { jsonRes(res, { success: true, data }); }
function apiErr(res, msg, status = 400) { jsonRes(res, { success: false, message: msg }, status); }

// ── Helper: Serve Static File ──
function serveFile(filePath, res) {
  if (!fs.existsSync(filePath)) return false;
  const stat = fs.statSync(filePath);
  if (stat.isDirectory()) {
    // Try index.html inside directory
    const indexPath = path.join(filePath, 'index.html');
    if (fs.existsSync(indexPath)) { filePath = indexPath; }
    else return false;
  }
  const ext = path.extname(filePath).toLowerCase();
  const contentType = MIME[ext] || 'application/octet-stream';
  res.writeHead(200, { 'Content-Type': contentType });
  fs.createReadStream(filePath).pipe(res);
  return true;
}

// ── Helper: Build SSO Error HTML (에러 안내 + 로그인 페이지 자동 이동) ──
function buildErrorHtml(errorMessage) {
  const escaped = (errorMessage || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
  return `<!DOCTYPE html>
<html lang="ko"><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1.0">
<title>SSO 로그인 실패</title>
<style>*{margin:0;padding:0;box-sizing:border-box;}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Noto Sans KR',sans-serif;background:linear-gradient(135deg,#1e3a5f 0%,#3b82f6 100%);display:flex;align-items:center;justify-content:center;min-height:100vh;}.card{background:#fff;border-radius:16px;padding:48px 40px;width:420px;box-shadow:0 20px 60px rgba(0,0,0,.2);text-align:center;color:#0f172a;}.card h2{font-size:20px;font-weight:700;margin-bottom:12px;color:#dc2626;}.error-msg{color:#475569;font-size:15px;font-weight:500;margin-bottom:20px;word-break:break-word;line-height:1.6;}.countdown{color:#94a3b8;font-size:13px;margin-bottom:24px;}.btn{display:inline-block;padding:10px 24px;border-radius:8px;font-weight:600;font-size:14px;text-decoration:none;background:#3b82f6;color:#fff;}.btn:hover{background:#2563eb;}</style>
</head><body>
<div class="card">
  <h2>SSO 로그인 실패</h2>
  <p class="error-msg">${escaped}</p>
  <p class="countdown" id="countdown">3초 후 로그인 페이지로 이동합니다...</p>
  <a class="btn" href="/index.html">로그인 페이지로 바로 이동</a>
</div>
<script>
(function(){
  var sec=3;var el=document.getElementById('countdown');
  var timer=setInterval(function(){sec--;if(sec<=0){clearInterval(timer);window.location.replace('/index.html');}else{el.textContent=sec+'초 후 로그인 페이지로 이동합니다...';}},1000);
})();
</script></body></html>`;
}

// ── HTTP Server ──
const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;
  const method = req.method;

  // ── CORS Headers ──
  res.setHeader('Access-Control-Allow-Origin', '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,PUT,PATCH,DELETE,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type,Authorization,X-Refresh-Token');
  if (method === 'OPTIONS') { res.writeHead(204); res.end(); return; }

  // Collect body for POST/PUT/PATCH
  let body = '';
  req.on('data', chunk => { body += chunk; });
  req.on('end', () => {
    let jsonBody = null;
    try { if (body) jsonBody = JSON.parse(body); } catch(e) {}

    // ══════════════════════════════════════════
    //  Mock API Endpoints
    // ══════════════════════════════════════════

    // ── /api/login/sendEncData (application/x-www-form-urlencoded) ──
    // SSO 검증 성공 Mock → 인라인 HTML 직접 반환 (토큰 저장 + 메인페이지 이동)
    if (pathname === '/api/login/sendEncData' && method === 'POST') {
      let encData = null;
      // form-urlencoded 파싱
      if (body && !jsonBody) {
        const params = new URLSearchParams(body);
        encData = params.get('encData');
      }
      // JSON도 하위호환 지원
      if (!encData && jsonBody && jsonBody.encData) {
        encData = jsonBody.encData;
      }
      if (!encData || !encData.trim()) {
        return apiErr(res, 'encData는 필수입니다.', 400);
      }

      // Mock: encData 값에 따라 다양한 시나리오 테스트
      // - 'error:...' → SSO 검증 실패 (returnCode ≠ 0, returnDesc 안내 후 로그인 페이지 이동)
      // - 'unknown_user' → SSO 검증 성공이나 아이디 미존재 (로그인 페이지 이동)
      // - 그 외 → SSO 검증 성공 + 로그인 처리
      if (encData.startsWith('error:')) {
        const returnDesc = encData.substring(6) || 'SSO 인증 세션이 만료되었습니다.';
        const errorHtml = buildErrorHtml(returnDesc);
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end(errorHtml);
      }
      if (encData === 'unknown_user') {
        const errorHtml = buildErrorHtml('아이디가 존재하지 않습니다');
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
        return res.end(errorHtml);
      }

      // Mock: SSO 검증 성공 → 인라인 HTML로 토큰 전달 (302 리다이렉트 대신)
      const mockToken = 'mock-sso-access-token-' + Date.now();
      const mockRefresh = 'mock-sso-refresh-token-' + Date.now();
      const mockSproId = 'admin';
      const html = `<!DOCTYPE html>
<html lang="ko"><head><meta charset="UTF-8"><title>SSO 로그인 처리 중...</title>
<style>*{margin:0;padding:0;box-sizing:border-box;}body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;background:linear-gradient(135deg,#1e3a5f,#3b82f6);display:flex;align-items:center;justify-content:center;min-height:100vh;}.card{background:#fff;border-radius:16px;padding:48px 40px;width:420px;box-shadow:0 20px 60px rgba(0,0,0,.2);text-align:center;color:#0f172a;}.card h2{font-size:20px;font-weight:700;margin-bottom:8px;}.card p{font-size:14px;color:#475569;}.spinner{width:40px;height:40px;border:4px solid #e2e8f0;border-top:4px solid #3b82f6;border-radius:50%;animation:spin .8s linear infinite;margin:0 auto 20px;}@keyframes spin{0%{transform:rotate(0)}100%{transform:rotate(360deg)}}</style>
</head><body>
<div class="card"><div class="spinner"></div><h2>SSO 로그인 처리 중</h2><p id="s">인증 정보를 확인하고 있습니다...</p></div>
<script>
(async function(){
  var T='${mockToken}',R='${mockRefresh}',S='${mockSproId}';
  try {
    document.getElementById('s').textContent='사용자 정보를 조회하는 중...';
    var u=null;
    try{var r=await fetch('/api/auth/me',{headers:{'Authorization':'Bearer '+T}});var d=await r.json();if(d.success&&d.data)u=d.data;}catch(e){}
    if(!u)u={loginId:S,userName:S,role:'ROLE_USER'};
    sessionStorage.setItem('auth',JSON.stringify({token:T,refreshTk:R,currentUser:u,currentPage:'dashboard'}));
    var fr=(u.role||'').replace('ROLE_','');
    localStorage.setItem('fireweb_user',JSON.stringify({loginId:u.loginId,userName:u.userName,role:fr,token:T,canManage:u.role==='ROLE_ADMIN'||u.role==='ROLE_FIRE_MANAGER'}));
    document.getElementById('s').textContent='로그인 완료! 메인 페이지로 이동합니다...';
    window.location.replace('/index.html');
  }catch(e){document.getElementById('s').textContent='오류: '+e.message;}
})();
</script></body></html>`;
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(html);
    }

    // Health
    if (pathname === '/api/health') {
      return apiOk(res, { status: 'UP', timestamp: new Date().toISOString() });
    }

    // Login
    if (pathname === '/api/auth/login' && method === 'POST') {
      const { loginId, password } = jsonBody || {};
      const user = mockUsers.find(u => u.loginId === loginId);
      if (user) {
        return apiOk(res, {
          accessToken: 'mock-jwt-access-token-' + Date.now(),
          refreshToken: 'mock-jwt-refresh-token-' + Date.now(),
          tokenType: 'Bearer'
        });
      }
      return apiErr(res, '아이디 또는 비밀번호가 올바르지 않습니다.', 401);
    }

    // Refresh Token
    if (pathname === '/api/auth/refresh' && method === 'POST') {
      return apiOk(res, {
        accessToken: 'mock-jwt-access-token-refreshed-' + Date.now(),
        refreshToken: 'mock-jwt-refresh-token-refreshed-' + Date.now(),
        tokenType: 'Bearer'
      });
    }

    // Me
    if (pathname === '/api/auth/me') {
      return apiOk(res, mockUser);
    }

    // My IP
    if (pathname === '/api/core/menus/my-ip') {
      return apiOk(res, { ip: '127.0.0.1' });
    }

    // Menus - role
    if (pathname.startsWith('/api/core/menus/role/')) {
      return apiOk(res, mockMenus);
    }

    // Menus - tree
    if (pathname === '/api/core/menus/tree' || pathname === '/api/core/menus') {
      return apiOk(res, mockMenus);
    }

    // Menus - list
    if (pathname === '/api/core/menus/list/all' || pathname === '/api/core/menus/list') {
      const flat = [];
      function flatten(nodes) {
        nodes.forEach(n => { flat.push({...n, children:undefined}); if(n.children) flatten(n.children); });
      }
      flatten(mockMenus);
      return apiOk(res, flat);
    }

    // Menu CRUD
    if (pathname.match(/^\/api\/core\/menus\/\d+$/) && method === 'PUT') {
      return apiOk(res, { menuId: parseInt(pathname.split('/').pop()), ...jsonBody });
    }
    if (pathname === '/api/core/menus' && method === 'POST') {
      return apiOk(res, { menuId: 100, ...jsonBody });
    }
    if (pathname.match(/^\/api\/core\/menus\/\d+$/) && method === 'DELETE') {
      return apiOk(res, null);
    }

    // Permissions
    if (pathname === '/api/core/permissions/roles' && method === 'GET') {
      return apiOk(res, mockPermissions);
    }
    if (pathname === '/api/core/permissions/roles' && method === 'PUT') {
      return apiOk(res, jsonBody);
    }

    // Codes - lookup
    if (pathname === '/api/codes/lookup/ROLE') {
      return apiOk(res, mockRoles);
    }
    if (pathname === '/api/codes/lookup/DEPT') {
      return apiOk(res, mockDepts);
    }

    // Codes - groups
    if (pathname === '/api/codes/groups' && method === 'GET') {
      return apiOk(res, mockCodeGroups);
    }
    if (pathname.match(/^\/api\/codes\/groups\/\d+$/) && method === 'GET') {
      const gid = parseInt(pathname.split('/').pop());
      const g = mockCodeGroups.find(x => x.groupId === gid);
      if (g) return apiOk(res, { ...g, details: mockRoles.map((r,i) => ({ codeId:i+1, code:r.code, codeName:r.codeName, sortOrder:r.sortOrder, isActive:true })) });
      return apiErr(res, 'Not found', 404);
    }
    if (pathname === '/api/codes/groups' && method === 'POST') {
      return apiOk(res, { groupId: 100, ...jsonBody });
    }
    if (pathname.match(/^\/api\/codes\/groups\/\d+$/) && method === 'PUT') {
      return apiOk(res, jsonBody);
    }
    if (pathname.match(/^\/api\/codes\/groups\/\d+$/) && method === 'DELETE') {
      return apiOk(res, null);
    }
    if (pathname.match(/^\/api\/codes\/groups\/\d+\/details$/) && method === 'POST') {
      return apiOk(res, { codeId: 100, ...jsonBody });
    }
    if (pathname.match(/^\/api\/codes\/details\/\d+$/) && (method === 'PUT' || method === 'DELETE')) {
      return apiOk(res, jsonBody || null);
    }

    // Users - integrated
    if (pathname === '/api/integrated/users' && method === 'GET') {
      return apiOk(res, { content: mockUsers, totalElements: mockUsers.length });
    }
    if (pathname === '/api/integrated/users' && method === 'POST') {
      // 신규 사용자는 항상 ROLE_USER(사용자) 역할로 생성
      const newUserId = 100 + mockUsers.length;
      const newUser = {
        userId: newUserId,
        loginId: jsonBody.loginId,
        userName: jsonBody.userName,
        email: jsonBody.email || null,
        phone: jsonBody.phone || null,
        role: 'ROLE_USER',
        enabled: true,
        deptCode: jsonBody.deptCode || null,
        deptName: jsonBody.deptCode ? (mockDepts.find(d => d.code === jsonBody.deptCode) || {}).codeName || jsonBody.deptCode : null,
        position: jsonBody.position || null,
        jobTitle: jsonBody.jobTitle || null,
        employeeNo: jsonBody.employeeNo || null,
        joinDate: jsonBody.joinDate || null,
        officePhone: jsonBody.officePhone || null,
        internalExt: jsonBody.internalExt || null
      };
      mockUsers.push(newUser);
      return apiOk(res, newUser);
    }
    if (pathname.match(/^\/api\/integrated\/users\/\d+$/) && method === 'PUT') {
      return apiOk(res, jsonBody);
    }
    if (pathname.match(/^\/api\/integrated\/users\/\d+\/role/) && method === 'PATCH') {
      return apiOk(res, null);
    }
    if (pathname.match(/^\/api\/integrated\/users\/\d+\/(enable|disable)$/) && method === 'PATCH') {
      return apiOk(res, null);
    }

    // Core Users
    if (pathname === '/api/core/users' && method === 'GET') {
      return apiOk(res, { content: mockUsers, totalElements: mockUsers.length });
    }
    if (pathname.match(/^\/api\/core\/users\/\d+$/) && method === 'GET') {
      const uid = parseInt(pathname.split('/').pop());
      const u = mockUsers.find(x => x.userId === uid);
      return u ? apiOk(res, u) : apiErr(res, 'User not found', 404);
    }

    // Fire API - Dashboard Stats
    if (pathname === '/fire-api/dashboard/stats') {
      return apiOk(res, { totalEquipment: 42, extinguisherCount: 20, hydrantCount: 10, receiverCount: 7, pumpCount: 5, buildingCount: 3 });
    }

    // Fire API - Lists
    if (pathname.startsWith('/fire-api/extinguishers')) {
      return apiOk(res, { content: [], totalElements: 20 });
    }
    if (pathname.startsWith('/fire-api/hydrants')) {
      return apiOk(res, { content: [], totalElements: 10 });
    }
    if (pathname.startsWith('/fire-api/pumps')) {
      return apiOk(res, { content: [], totalElements: 5 });
    }
    if (pathname.startsWith('/fire-api/receivers')) {
      return apiOk(res, { content: [], totalElements: 7 });
    }

    // PS-INSP API — complete mock endpoints
    if (pathname.startsWith('/ps-insp-api/')) {

      // ── PS-INSP Page (Thymeleaf template served as static HTML) ──
      if (pathname === '/ps-insp-api/page') {
        const tplPath = path.join(TEMPLATE_DIR, 'ps-insp/index.html');
        if (fs.existsSync(tplPath)) {
          let html = fs.readFileSync(tplPath, 'utf8');
          html = html.replace(/\s+th:[a-z-]+="[^"]*"/g, '');
          res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
          return res.end(html);
        }
      }

      // ── Health ──
      if (pathname === '/ps-insp-api/health') {
        return apiOk(res, { status: 'UP', timestamp: new Date().toISOString() });
      }

      // ── Config: PPM Limit ──
      if (pathname === '/ps-insp-api/config/ppm-limit' && method === 'GET') {
        return apiOk(res, { ppmLimit: 500, updatedAt: new Date().toISOString() });
      }
      if (pathname === '/ps-insp-api/config/ppm-limit' && method === 'POST') {
        return apiOk(res, { ppmLimit: (jsonBody && jsonBody.ppmLimit) || 500, updatedAt: new Date().toISOString() });
      }

      // ── Config: PPM Admins ──
      if (pathname === '/ps-insp-api/config/ppm-admins' && method === 'GET') {
        return apiOk(res, ['admin', 'manager01']);
      }
      if (pathname === '/ps-insp-api/config/ppm-admins' && method === 'POST') {
        return apiOk(res, (jsonBody && jsonBody.admins) || []);
      }

      // ── Inspections: check-exists ──
      if (pathname === '/ps-insp-api/inspections/check-exists') {
        return apiOk(res, { exists: false });
      }

      // ── Inspections: by-barcode ──
      if (pathname === '/ps-insp-api/inspections/by-barcode') {
        return apiOk(res, { content: [], totalElements: 0, totalPages: 0, number: 0, size: 50 });
      }

      // ── Inspections: search ──
      if (pathname === '/ps-insp-api/inspections/search') {
        var all = _filterInspections(_mockInspections(), parsedUrl.query);
        var pg = parseInt(parsedUrl.query.page) || 0;
        var sz = parseInt(parsedUrl.query.size) || 10;
        var slice = all.slice(pg * sz, pg * sz + sz);
        return apiOk(res, { content: slice, totalElements: all.length, totalPages: Math.ceil(all.length / sz), number: pg, size: sz });
      }

      // ── Inspections: list (paginated) ──
      if (pathname === '/ps-insp-api/inspections' && method === 'GET') {
        var all = _filterInspections(_mockInspections(), parsedUrl.query);
        var pg = parseInt(parsedUrl.query.page) || 0;
        var sz = parseInt(parsedUrl.query.size) || 10;
        var slice = all.slice(pg * sz, pg * sz + sz);
        return apiOk(res, { content: slice, totalElements: all.length, totalPages: Math.ceil(all.length / sz), number: pg, size: sz });
      }

      // ── Inspections: create (multipart or JSON) ──
      if (pathname === '/ps-insp-api/inspections' && method === 'POST') {
        return apiOk(res, {
          inspectionId: 1000 + Math.floor(Math.random() * 9000),
          indBcd: 'IND-MOCK-001',
          lotNo: 'LOT-MOCK-001',
          matnr: 'MAT-MOCK-001',
          ppmValue: 123.45,
          qualityResult: 'OK',
          createdAt: new Date().toISOString()
        });
      }

      // ── Inspections: update MES status ──
      if (pathname.match(/^\/ps-insp-api\/inspections\/\d+\/mes-status$/) && method === 'PATCH') {
        return apiOk(res, { updated: true });
      }

      // ── MES: send-result ──
      if (pathname === '/ps-insp-api/mes/send-result' && method === 'POST') {
        return apiOk(res, { resultCode: '0', resultMessage: 'MES 전송 성공 (Mock)', timestamp: new Date().toISOString() });
      }

      // ── Catch-all for unknown ps-insp-api paths ──
      return apiOk(res, { message: 'PS-INSP Mock API', path: pathname });
    }

    // ── Catch-all for unknown API paths ──
    if (pathname.startsWith('/api/') || pathname.startsWith('/fire-api/')) {
      return apiOk(res, null);
    }

    // ══════════════════════════════════════════
    //  Static File Serving
    // ══════════════════════════════════════════

    // Root → SPA index.html
    if (pathname === '/' || pathname === '/index.html') {
      return serveFile(path.join(STATIC_DIRS[0], 'index.html'), res);
    }

    // SSO Callback
    if (pathname === '/sso-callback.html') {
      return serveFile(path.join(STATIC_DIRS[0], 'sso-callback.html'), res);
    }

    // Test UI
    if (pathname === '/test-ui' || pathname === '/test-ui/' || pathname === '/test-ui/index.html') {
      return serveFile(path.join(TEST_UI_DIR, 'index.html'), res);
    }

    // Try each static directory
    for (const dir of STATIC_DIRS) {
      const filePath = path.join(dir, pathname);
      if (serveFile(filePath, res)) return;
    }

    // PS-INSP static files
    const psStaticPath = path.join(__dirname, 'module-ps-insp/src/main/resources/static', pathname);
    if (serveFile(psStaticPath, res)) return;

    // Favicon fallback
    if (pathname === '/favicon.ico') {
      const favPath = path.join(STATIC_DIRS[0], 'favicon.ico');
      if (serveFile(favPath, res)) return;
    }

    // 404
    res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
    res.end(`<!DOCTYPE html><html><body style="font-family:sans-serif;padding:40px;background:#0f172a;color:#e2e8f0">
      <h1 style="color:#ef4444">404 Not Found</h1>
      <p>요청한 경로를 찾을 수 없습니다: <code>${pathname}</code></p>
      <hr style="border-color:#334155">
      <p><a href="/" style="color:#3b82f6">홈으로 이동</a> | <a href="/test-ui" style="color:#3b82f6">API Tester</a></p>
    </body></html>`);
  });
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('');
  console.log('╔═══════════════════════════════════════════════════════════╗');
  console.log('║         AI Platform - Preview Server Started             ║');
  console.log('╠═══════════════════════════════════════════════════════════╣');
  console.log(`║  Main App (SPA)   : http://0.0.0.0:${PORT}/               ║`);
  console.log(`║  API Tester       : http://0.0.0.0:${PORT}/test-ui        ║`);
  console.log('║                                                           ║');
  console.log('║  Mock API Endpoints:                                      ║');
  console.log('║    POST /api/auth/login   - 로그인 (admin/아무비밀번호)  ║');
  console.log('║    GET  /api/health       - 서버 상태 확인               ║');
  console.log('║    GET  /api/auth/me      - 현재 사용자 정보             ║');
  console.log('║                                                           ║');
  console.log('║  Note: Backend DB 없이 Mock 데이터로 동작합니다.        ║');
  console.log('╚═══════════════════════════════════════════════════════════╝');
  console.log('');
});
