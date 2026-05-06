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

const PORT = 8080;

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
    // SSO 검증 → sproId로 사용자 조회 → JWT 토큰 발급 Mock
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
      // Mock: SSO 검증 성공으로 가정 → admin 사용자로 로그인 처리
      return jsonRes(res, {
        success: true,
        code: 200,
        message: 'SSO 인증 및 로그인 처리 완료',
        data: {
          accessToken: 'mock-sso-access-token-' + Date.now(),
          refreshToken: 'mock-sso-refresh-token-' + Date.now(),
          tokenType: 'Bearer',
          expiresIn: 3600,
          sproId: 'admin',
          resultMessage: 'SSO 인증 및 로그인 처리 완료'
        },
        timestamp: new Date().toISOString()
      });
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
      return apiOk(res, { userId: 100, ...jsonBody });
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

    // PS-INSP API
    if (pathname.startsWith('/ps-insp-api/')) {
      if (pathname === '/ps-insp-api/page') {
        // Serve ps-insp template
        const tplPath = path.join(TEMPLATE_DIR, 'ps-insp/index.html');
        if (fs.existsSync(tplPath)) {
          let html = fs.readFileSync(tplPath, 'utf8');
          html = html.replace(/\s+th:[a-z]+="[^"]*"/g, '');
          res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
          return res.end(html);
        }
      }
      return apiOk(res, { message: 'PS-INSP Mock API' });
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
