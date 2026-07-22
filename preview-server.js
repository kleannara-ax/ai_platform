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
  path.join(__dirname, 'module-dailyreport/src/main/resources/static'),
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
  // ── 코어 메뉴 ──
  { menuId:1, menuCode:'DASHBOARD', menuName:'대시보드', menuType:'MENU', menuUrl:'/dashboard', icon:'dashboard', parentId:null, sortOrder:0, isActive:true, isVisible:true, allowedIps:null, description:'메인 대시보드', children:[] },
  { menuId:2, menuCode:'USER_MGMT', menuName:'사용자 관리', menuType:'MENU', menuUrl:'/users', icon:'users', parentId:null, sortOrder:1, isActive:true, isVisible:true, allowedIps:null, description:'사용자 CRUD', children:[] },
  { menuId:3, menuCode:'MENU_MGMT', menuName:'메뉴 관리', menuType:'MENU', menuUrl:'/menus', icon:'menu', parentId:null, sortOrder:2, isActive:true, isVisible:true, allowedIps:null, description:'메뉴 관리', children:[] },
  { menuId:4, menuCode:'PERM_MGMT', menuName:'접근 권한', menuType:'MENU', menuUrl:'/permissions', icon:'lock', parentId:null, sortOrder:3, isActive:true, isVisible:true, allowedIps:null, description:'접근 권한 설정', children:[] },
  { menuId:5, menuCode:'CODE_MGMT', menuName:'공통코드 관리', menuType:'MENU', menuUrl:'/codes', icon:'code', parentId:null, sortOrder:4, isActive:true, isVisible:true, allowedIps:null, description:'공통코드 관리', children:[] },
  // ── 설비관리시스템 (V15+V21+V26+V29+V30 반영) ──
  { menuId:6, menuCode:'FIRE_MGMT', menuName:'설비관리시스템', menuType:'MENU', menuUrl:'/fire', icon:'tools', parentId:null, sortOrder:5, isActive:true, isVisible:true, allowedIps:null, description:'소방설비와 기타설비를 통합 관리하는 설비관리시스템',
    children: [
      // 공통 메뉴 (설비관리시스템 바로 아래)
      { menuId:62, menuCode:'FIRE_MAP', menuName:'도면 (메인)', menuType:'MENU', menuUrl:'/fire-map.html', parentId:6, sortOrder:1, isActive:true, isVisible:true, allowedIps:null },
      { menuId:67, menuCode:'FIRE_FLOOR', menuName:'층별 도면', menuType:'MENU', menuUrl:'/maps/floor.html', parentId:6, sortOrder:2, isActive:true, isVisible:true, allowedIps:null },
      { menuId:68, menuCode:'FIRE_QR', menuName:'QR코드', menuType:'MENU', menuUrl:'/qr', parentId:6, sortOrder:3, isActive:true, isVisible:true, allowedIps:null, description:'소방설비 QR코드 발급 및 조회' },
      // 소방설비 그룹
      { menuId:69, menuCode:'FIRE_EQUIPMENT_GROUP', menuName:'소방설비', menuType:'MENU', menuUrl:null, parentId:6, sortOrder:4, isActive:true, isVisible:true, allowedIps:null, description:'소방설비 관리 메뉴 그룹',
        children: [
          { menuId:63, menuCode:'FIRE_EXTINGUISHER', menuName:'소화기 목록', menuType:'MENU', menuUrl:'/extinguishers.html', parentId:69, sortOrder:1, isActive:true, isVisible:true, allowedIps:null },
          { menuId:64, menuCode:'FIRE_HYDRANT', menuName:'소화전 목록', menuType:'MENU', menuUrl:'/hydrants.html', parentId:69, sortOrder:2, isActive:true, isVisible:true, allowedIps:null },
          { menuId:65, menuCode:'FIRE_RECEIVER', menuName:'수신기 목록', menuType:'MENU', menuUrl:'/receivers.html', parentId:69, sortOrder:3, isActive:true, isVisible:true, allowedIps:null },
          { menuId:66, menuCode:'FIRE_PUMP', menuName:'소방펌프 목록', menuType:'MENU', menuUrl:'/pumps.html', parentId:69, sortOrder:4, isActive:true, isVisible:true, allowedIps:null },
          { menuId:70, menuCode:'FIRE_SPRINKLER', menuName:'스프링클러 목록', menuType:'MENU', menuUrl:'/sprinklers.html', parentId:69, sortOrder:5, isActive:true, isVisible:true, allowedIps:null },
        ]
      },
      // 기타설비 그룹
      { menuId:71, menuCode:'OTHER_EQUIPMENT_GROUP', menuName:'기타설비', menuType:'MENU', menuUrl:null, parentId:6, sortOrder:5, isActive:true, isVisible:true, allowedIps:null, description:'기타설비 관리 메뉴 그룹',
        children: [
          { menuId:72, menuCode:'OTHER_AIRCON', menuName:'에어컨', menuType:'MENU', menuUrl:'/facility/air-conditioners', parentId:71, sortOrder:1, isActive:true, isVisible:true, allowedIps:null },
          { menuId:73, menuCode:'OTHER_WATER_PURIFIER', menuName:'정수기', menuType:'MENU', menuUrl:'/facility/water-purifiers', parentId:71, sortOrder:2, isActive:true, isVisible:true, allowedIps:null },
          { menuId:74, menuCode:'OTHER_QR', menuName:'QR코드', menuType:'MENU', menuUrl:'/facility/other-qr', parentId:71, sortOrder:3, isActive:true, isVisible:true, allowedIps:null },
        ]
      },
    ]
  },
  // ── PS 지분 검사 (03_menu_data + 04_rename_menu 반영) ──
  { menuId:7, menuCode:'PS_INSP_MGMT', menuName:'PS 지분 검사', menuType:'MENU', menuUrl:'/ps-insp-api/page', icon:'ps_insp', parentId:null, sortOrder:30, isActive:true, isVisible:true, allowedIps:null, description:'PS 지분 검사 (점보롤 지분 검사)', children:[] },
  // ── 세부공장일보 ──
  { menuId:100, menuCode:'DAILY_REPORT', menuName:'세부공장일보', menuType:'CATEGORY', menuUrl:null, icon:'clipboard-list', parentId:null, sortOrder:31, isActive:true, isVisible:true, allowedIps:null, description:'세부공장일보 카테고리',
    children: [
      { menuId:101, menuCode:'DAILY_REPORT_INPUT', menuName:'세부공장일보 입력', menuType:'PAGE', menuUrl:'/dailyreport-api/page', parentId:100, sortOrder:1, isActive:true, isVisible:true, allowedIps:null },
      { menuId:102, menuCode:'DAILY_REPORT_AUTH', menuName:'세부공장일보 컬럼관리', menuType:'PAGE', menuUrl:'/dailyreport-api/page/column-mgmt', parentId:100, sortOrder:2, isActive:true, isVisible:true, allowedIps:null },
    ]
  },
];

const mockRoles = [
  { code: 'ROLE_ADMIN',             codeName: '관리자',           sortOrder: 1 },
  { code: 'ROLE_MANAGER',           codeName: 'PS 지분 검사 매니저', sortOrder: 2 },
  { code: 'ROLE_USER',              codeName: '일반 사용자',       sortOrder: 3 },
  { code: 'ROLE_FACILITY_MANAGER',  codeName: '시설관리',          sortOrder: 4 },
  { code: 'ROLE_FIRE_MANAGER',      codeName: '소방시설관리',       sortOrder: 5 },
  { code: 'ROLE_EQUIPMENT_MANAGER', codeName: '기타시설관리',       sortOrder: 6 },
];

const mockDepts = [
  { code: 'DEPT001', codeName: '경영지원팀' },
  { code: 'DEPT002', codeName: '기술개발팀' },
  { code: 'DEPT003', codeName: '영업팀' },
];

const mockUsers = [
  // 기존 플랫폼 사용자
  { userId:1, loginId:'admin', userName:'관리자', email:'admin@company.com', phone:'010-1234-5678', role:'ROLE_ADMIN', enabled:true, deptName:'경영지원팀', deptCode:'DEPT001', position:'부장', jobTitle:'팀장', employeeNo:'EMP-0001', joinDate:'2020-01-15', officePhone:'02-1234-5678', internalExt:'1001' },
  { userId:2, loginId:'manager01', userName:'김매니저', email:'manager@company.com', phone:'010-2345-6789', role:'ROLE_MANAGER', enabled:true, deptName:'기술개발팀', deptCode:'DEPT002', position:'과장', jobTitle:'팀원', employeeNo:'EMP-0002', joinDate:'2021-03-01', officePhone:'02-1234-5679', internalExt:'1002' },
  { userId:3, loginId:'user01', userName:'이사용자', email:'user01@company.com', phone:'010-3456-7890', role:'ROLE_USER', enabled:true, deptName:'영업팀', deptCode:'DEPT003', position:'대리', jobTitle:'팀원', employeeNo:'EMP-0003', joinDate:'2022-06-15', officePhone:'02-1234-5680', internalExt:'1003' },
  { userId:4, loginId:'fire01', userName:'박소방', email:'fire01@company.com', phone:'010-4567-8901', role:'ROLE_FIRE_MANAGER', enabled:true, deptName:'경영지원팀', deptCode:'DEPT001', position:'과장', jobTitle:'소방안전담당', employeeNo:'EMP-0004', joinDate:'2021-09-01', officePhone:'02-1234-5681', internalExt:'1004' },
  { userId:5, loginId:'user02', userName:'최직원', email:'user02@company.com', phone:'010-5678-9012', role:'ROLE_USER', enabled:false, deptName:'기술개발팀', deptCode:'DEPT002', position:'사원', jobTitle:'팀원', employeeNo:'EMP-0005', joinDate:'2023-01-10', officePhone:'02-1234-5682', internalExt:'1005' },
  // 세부공장일보 담당자 (seed 데이터 기반)
  { userId:101, loginId:'kim',   userName:'김완중 팀장',      email:'kim@company.com',    phone:'010-6001-0001', role:'ROLE_USER', enabled:true, deptName:'생산팀',       deptCode:'DEPT010', position:'팀장', jobTitle:'생산팀장',       employeeNo:'EMP-0101', joinDate:'2018-03-01', officePhone:'02-1234-6001', internalExt:'6001' },
  { userId:102, loginId:'park',  userName:'박지권 책임',      email:'park@company.com',   phone:'010-6002-0001', role:'ROLE_USER', enabled:true, deptName:'환경에너지팀', deptCode:'DEPT011', position:'책임', jobTitle:'환경에너지담당', employeeNo:'EMP-0102', joinDate:'2019-05-01', officePhone:'02-1234-6002', internalExt:'6002' },
  { userId:103, loginId:'yoo',   userName:'유동현 책임',      email:'yoo@company.com',    phone:'010-6003-0001', role:'ROLE_USER', enabled:true, deptName:'생산팀',       deptCode:'DEPT010', position:'책임', jobTitle:'수율담당',       employeeNo:'EMP-0103', joinDate:'2019-08-01', officePhone:'02-1234-6003', internalExt:'6003' },
  { userId:104, loginId:'jung',  userName:'정상엽 책임',      email:'jung@company.com',   phone:'010-6004-0001', role:'ROLE_USER', enabled:true, deptName:'환경팀',       deptCode:'DEPT012', position:'책임', jobTitle:'환경담당',       employeeNo:'EMP-0104', joinDate:'2020-01-15', officePhone:'02-1234-6004', internalExt:'6004' },
  { userId:105, loginId:'jang',  userName:'장석환 선임',      email:'jang@company.com',   phone:'010-6005-0001', role:'ROLE_USER', enabled:true, deptName:'생산팀',       deptCode:'DEPT010', position:'선임', jobTitle:'장기재고담당',   employeeNo:'EMP-0105', joinDate:'2021-02-01', officePhone:'02-1234-6005', internalExt:'6005' },
  { userId:106, loginId:'lee',   userName:'이도형 사원',      email:'lee@company.com',    phone:'010-6006-0001', role:'ROLE_USER', enabled:true, deptName:'생산팀',       deptCode:'DEPT010', position:'사원', jobTitle:'장기재고담당',   employeeNo:'EMP-0106', joinDate:'2022-03-01', officePhone:'02-1234-6006', internalExt:'6006' },
  { userId:107, loginId:'choi',  userName:'최민우 사원',      email:'choi@company.com',   phone:'010-6007-0001', role:'ROLE_USER', enabled:true, deptName:'환경에너지팀', deptCode:'DEPT011', position:'사원', jobTitle:'전력에너지담당', employeeNo:'EMP-0107', joinDate:'2022-07-01', officePhone:'02-1234-6007', internalExt:'6007' },
  { userId:108, loginId:'energy',userName:'환경에너지팀 반장', email:'energy@company.com', phone:'010-6008-0001', role:'ROLE_USER', enabled:true, deptName:'환경에너지팀', deptCode:'DEPT011', position:'반장', jobTitle:'보일러운영담당', employeeNo:'EMP-0108', joinDate:'2017-06-01', officePhone:'02-1234-6008', internalExt:'6008' },
];

// ── 로그인 세션 관리 (토큰 → 사용자 매핑) ──
const tokenToUser = {};

const mockCodeGroups = [
  { groupId:1, groupCode:'ROLE', groupName:'역할', description:'사용자 역할 분류', sortOrder:1, isActive:true, codeCount:6 },
  { groupId:2, groupCode:'DEPT', groupName:'부서', description:'부서 코드', sortOrder:2, isActive:true, codeCount:3 },
  { groupId:3, groupCode:'USER_STATUS', groupName:'사용자 상태', description:'사용자 활성/비활성 상태', sortOrder:3, isActive:true, codeCount:2 },
];

// ── 역할별 메뉴 접근 권한 (운영 DB V21+V26+V29+V30 최종 기준) ──
// 설비관리시스템 공통: 6(FIRE_MGMT), 62(도면), 67(층별도면)
// 소방설비: 69(소방설비그룹), 63(소화기), 64(소화전), 65(수신기), 66(소방펌프), 70(스프링클러)
// 소방QR: 68(QR코드)
// 기타설비: 71(기타설비그룹), 72(에어컨), 73(정수기), 74(기타QR)
// PS 지분 검사: 7(PS_INSP_MGMT)
const _allFacilityMenus = [6,62,67,68,69,63,64,65,66,70,71,72,73,74];
const _fireOnlyMenus    = [6,62,67,68,69,63,64,65,66,70];         // 공통도면+소방QR+소방설비
const _otherOnlyMenus   = [6,62,67,71,72,73,74];                  // 공통도면+기타설비+기타QR (소방QR 제외)
const mockPermissions = mockRoles.map(r => ({
  role: r.code,
  roleDescription: r.codeName,
  // 세부공장일보(100,101,102)는 모든 역할에 개방 — 실제 접근은 cell_auth 프론트 필터링으로 제어
  menuIds: r.code === 'ROLE_ADMIN'             ? [1,2,3,4,5, ..._allFacilityMenus, 7, 100,101,102] :
           r.code === 'ROLE_MANAGER'           ? [1,2,5, 7, 100,101,102] :
           r.code === 'ROLE_USER'              ? [1, 7, 100,101,102] :
           r.code === 'ROLE_FACILITY_MANAGER'  ? [1, ..._allFacilityMenus, 100,101,102] :
           r.code === 'ROLE_FIRE_MANAGER'      ? [1, ..._fireOnlyMenus, 100,101,102] :
           r.code === 'ROLE_EQUIPMENT_MANAGER' ? [1, ..._otherOnlyMenus, 100,101,102] :
           [1]
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
  for (var i = 1; i <= 70; i++) {
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
        if (!user.enabled) {
          return apiErr(res, '비활성화된 계정입니다. 관리자에게 문의하세요.', 403);
        }
        const accessToken = 'mock-jwt-' + loginId + '-' + Date.now();
        const refreshToken = 'mock-refresh-' + loginId + '-' + Date.now();
        // 토큰 → 사용자 매핑 저장 (로그인 세션)
        tokenToUser[accessToken] = user;
        tokenToUser[refreshToken] = user;
        return apiOk(res, {
          accessToken: accessToken,
          refreshToken: refreshToken,
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

    // Me — 토큰에서 실제 로그인 사용자를 찾아 반환
    if (pathname === '/api/auth/me') {
      const authHeader = req.headers['authorization'] || '';
      const bearerToken = authHeader.replace('Bearer ', '');
      const loggedInUser = tokenToUser[bearerToken] || null;
      if (loggedInUser) {
        return apiOk(res, loggedInUser);
      }
      // 토큰에서 loginId 추출 시도 (mock-jwt-{loginId}-{timestamp} 형식)
      const tokenMatch = bearerToken.match(/^mock-jwt-(.+?)-\d+$/);
      if (tokenMatch) {
        const foundUser = mockUsers.find(u => u.loginId === tokenMatch[1]);
        if (foundUser) {
          tokenToUser[bearerToken] = foundUser; // 캐시
          return apiOk(res, foundUser);
        }
      }
      // fallback: admin
      return apiOk(res, mockUser);
    }

    // My IP
    if (pathname === '/api/core/menus/my-ip') {
      return apiOk(res, { ip: '127.0.0.1' });
    }

    // Menus - role
    if (pathname.startsWith('/api/core/menus/role/')) {
      // 역할별 메뉴 필터링: mockPermissions의 menuIds 기준으로 트리 필터
      var reqRole = decodeURIComponent(pathname.split('/').pop());
      var rolePerm = mockPermissions.find(function(p){ return p.role === reqRole; });
      var allowedIds = rolePerm ? rolePerm.menuIds : [1]; // 기본: 대시보드만

      function filterMenuTree(nodes, ids) {
        var result = [];
        nodes.forEach(function(node) {
          // 이 노드 자체가 허용되었거나, 자식 중 허용된 게 있으면 포함
          var childFiltered = node.children ? filterMenuTree(node.children, ids) : [];
          if (ids.includes(node.menuId) || childFiltered.length > 0) {
            var copy = Object.assign({}, node);
            copy.children = childFiltered;
            result.push(copy);
          }
        });
        return result;
      }
      var filteredMenus = filterMenuTree(mockMenus, allowedIds);
      return apiOk(res, filteredMenus);
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

    // Codes - lookup (아키텍처 Rule 5: /common-api/ prefix)
    if (pathname === '/common-api/codes/lookup/ROLE') {
      return apiOk(res, mockRoles);
    }
    if (pathname === '/common-api/codes/lookup/DEPT') {
      return apiOk(res, mockDepts);
    }

    // Codes - groups
    if (pathname === '/common-api/codes/groups' && method === 'GET') {
      return apiOk(res, mockCodeGroups);
    }
    if (pathname.match(/^\/common-api\/codes\/groups\/\d+$/) && method === 'GET') {
      const gid = parseInt(pathname.split('/').pop());
      const g = mockCodeGroups.find(x => x.groupId === gid);
      if (g) return apiOk(res, { ...g, details: mockRoles.map((r,i) => ({ codeId:i+1, code:r.code, codeName:r.codeName, sortOrder:r.sortOrder, isActive:true })) });
      return apiErr(res, 'Not found', 404);
    }
    if (pathname === '/common-api/codes/groups' && method === 'POST') {
      return apiOk(res, { groupId: 100, ...jsonBody });
    }
    if (pathname.match(/^\/common-api\/codes\/groups\/\d+$/) && method === 'PUT') {
      return apiOk(res, jsonBody);
    }
    if (pathname.match(/^\/common-api\/codes\/groups\/\d+$/) && method === 'DELETE') {
      return apiOk(res, null);
    }
    if (pathname.match(/^\/common-api\/codes\/groups\/\d+\/details$/) && method === 'POST') {
      return apiOk(res, { codeId: 100, ...jsonBody });
    }
    if (pathname.match(/^\/common-api\/codes\/details\/\d+$/) && (method === 'PUT' || method === 'DELETE')) {
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

      // ── Inspections: by-barcode (정확 매칭) ──
      if (pathname === '/ps-insp-api/inspections/by-barcode') {
        var bcd = parsedUrl.query.indBcd || '';
        var matched = bcd ? _mockInspections().filter(function (i) { return i.indBcd === bcd; }) : [];
        var pg = parseInt(parsedUrl.query.page) || 0;
        var sz = parseInt(parsedUrl.query.size) || 50;
        var slice = matched.slice(pg * sz, pg * sz + sz);
        return apiOk(res, { content: slice, totalElements: matched.length, totalPages: Math.ceil(matched.length / sz), number: pg, size: sz });
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

    // ══════════════════════════════════════════
    //  세부공장일보 Mock API (dailyreport-api)
    // ══════════════════════════════════════════
    if (pathname.startsWith('/dailyreport-api/')) {

      // ── 세부공장일보 페이지 라우팅 (clean URL → 실제 HTML) ──
      if (pathname === '/dailyreport-api/page') {
        var drPagePath = path.join(__dirname, 'module-dailyreport/src/main/resources/static/dailyreport/index.html');
        if (serveFile(drPagePath, res)) return;
      }
      if (pathname === '/dailyreport-api/page/column-mgmt') {
        var drColMgmtPath = path.join(__dirname, 'module-dailyreport/src/main/resources/static/dailyreport/cell-auth-admin.html');
        if (serveFile(drColMgmtPath, res)) return;
      }

      // ── 세부공장일보 사용자 목록 (core_user 기반) ──
      var drUsers = [
        { userId:1, loginId:'admin', userName:'관리자',           department:'공장관리부',   position:'부장', role:'ADMIN' },
        { userId:2, loginId:'kim',   userName:'김완중 팀장',      department:'생산팀',       position:'팀장', role:'USER' },
        { userId:3, loginId:'park',  userName:'박지권 책임',      department:'환경에너지팀', position:'책임', role:'USER' },
        { userId:4, loginId:'yoo',   userName:'유동현 책임',      department:'생산팀',       position:'책임', role:'USER' },
        { userId:5, loginId:'jung',  userName:'정상엽 책임',      department:'환경팀',       position:'책임', role:'USER' },
        { userId:6, loginId:'jang',  userName:'장석환 선임',      department:'생산팀',       position:'선임', role:'USER' },
        { userId:7, loginId:'lee',   userName:'이도형 사원',      department:'생산팀',       position:'사원', role:'USER' },
        { userId:8, loginId:'choi',  userName:'최민우 사원',      department:'환경에너지팀', position:'사원', role:'USER' },
        { userId:9, loginId:'energy',userName:'환경에너지팀 반장', department:'환경에너지팀', position:'반장', role:'USER' },
      ];

      // ── 현재 로그인 사용자 결정 (쿼리파라미터 또는 기본값 admin) ──
      var drLoginId = parsedUrl.query.loginId || parsedUrl.query._loginId || 'admin';
      var drCurrentUser = drUsers.find(function(u){return u.loginId===drLoginId;});
      // drUsers에 없는 플랫폼 사용자(manager01 등)는 게스트로 처리 (일보 편집 권한 없음)
      if (!drCurrentUser) {
        drCurrentUser = { userId:0, loginId:drLoginId, userName:drLoginId, department:'기타', position:'-', role:'GUEST' };
      }

      // ── 세부공장일보 셀 권한 (in-memory store) ──
      if (!global._drCellAuths) {
        // 운영 배포 초기 상태: 빈 배열 — admin이 접근권한 페이지에서 직접 배정
        global._drCellAuths = [];
        global._drNextAuthId = 1;
      }

      // ── 셀 데이터 (in-memory store) ──
      if (!global._drCellData) { global._drCellData = {}; }

      // ── /view/my-permissions (cell_auth 기반 접근 판정) ──
      if (pathname === '/dailyreport-api/view/my-permissions') {
        // admin은 core_menu_permission.canAdmin으로 전체 접근
        var isAdmin = drCurrentUser.loginId === 'admin';
        // cell_auth에 활성 레코드가 있는 사용자만 입력 접근 가능
        var hasActiveCellAuth = global._drCellAuths.some(function(a) {
          return a.userId === drCurrentUser.userId && a.isActive;
        });
        var canAccessInput = isAdmin || hasActiveCellAuth;
        var canWriteInput = hasActiveCellAuth;
        var canAccessAuth = isAdmin || hasActiveCellAuth;  // admin 또는 cell_auth 보유자
        return jsonRes(res, {
          success: true, code: 200, message: 'SUCCESS',
          data: {
            userId: drCurrentUser.userId,
            loginId: drCurrentUser.loginId,
            permissions: { canAccessInput: canAccessInput, canWriteInput: canWriteInput, canAccessAuth: canAccessAuth }
          }
        });
      }

      // ── /users (AI플랫폼 등록 사용자 목록 — 셀 권한 등록용 드롭다운) ──
      // 프로덕션: EntityManager native query로 core_user 테이블 전체 조회
      // Mock: drUsers(일보 모듈의 core_user 매핑)를 반환
      if (pathname === '/dailyreport-api/users' && method === 'GET') {
        var userList = drUsers.map(function(u) {
          return { userId: u.userId, loginId: u.loginId, userName: u.userName, department: u.department, position: u.position };
        });
        return jsonRes(res, userList);
      }

      // ── /view/render?reportDate=... ──
      if (pathname === '/dailyreport-api/view/render') {
        var reportDate = parsedUrl.query.reportDate || '2024-07-20';
        var savedData = global._drCellData[reportDate] || {};

        // 셀 editable 계산 함수
        function isCellEditable(cell, loginId) {
          if (cell.cellType === 'HEADER' || cell.cellType === 'READONLY') return false;
          if (cell.isLocked) return false;
          if (!cell.ownerIds) return false;
          var owners = cell.ownerIds.split(/\s+/);
          return owners.some(function(o){return o.toLowerCase()===loginId.toLowerCase();});
        }

        // 실제 seed 데이터 기반 셀 생성
        function buildCells(tableCode, seedCells) {
          return seedCells.map(function(c) {
            var key = tableCode + '__' + c.excelCoord;
            var val = (savedData[key] !== undefined) ? savedData[key] : c.cellValue;
            var editable = isCellEditable(c, drCurrentUser.loginId);
            return Object.assign({}, c, { tableCode: tableCode, cellValue: val, editable: editable });
          });
        }

        // 셀 seed 데이터 (02_seed_data.sql 기반)
        var tbl1Cells = [
          {rowIndex:0,colIndex:0,excelCoord:'B5',cellValue:'생산지표',cellType:'HEADER',rowSpan:2,colSpan:3,isLocked:1},
          {rowIndex:0,colIndex:3,excelCoord:'E5',cellValue:'최종\n목표',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:4,excelCoord:'F5',cellValue:"'24년\n월평균",cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:5,excelCoord:'G5',cellValue:"'25년\n월평균",cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:6,excelCoord:'H5',cellValue:"'25년\n12월",cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:7,excelCoord:'I5',cellValue:"'26년",cellType:'HEADER',rowSpan:1,colSpan:7,isLocked:1},
          {rowIndex:0,colIndex:14,excelCoord:'P5',cellValue:'비고 사항',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:7,excelCoord:'I6',cellValue:'1월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:8,excelCoord:'J6',cellValue:'2월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:9,excelCoord:'K6',cellValue:'3월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:10,excelCoord:'L6',cellValue:'4월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:11,excelCoord:'M6',cellValue:'5월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:12,excelCoord:'N6',cellValue:'6월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:13,excelCoord:'O6',cellValue:'7월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:0,excelCoord:'B7',cellValue:'제지3 평균선속(m/분)',cellType:'READONLY',rowSpan:1,colSpan:3,isLocked:1},
          {rowIndex:2,colIndex:3,excelCoord:'E7',cellValue:'640',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:4,excelCoord:'F7',cellValue:'583.5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:5,excelCoord:'G7',cellValue:'587',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:6,excelCoord:'H7',cellValue:'588',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:7,excelCoord:'I7',cellValue:'597',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:8,excelCoord:'J7',cellValue:'584',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:9,excelCoord:'K7',cellValue:'577',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:10,excelCoord:'L7',cellValue:'597',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:11,excelCoord:'M7',cellValue:'597',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:12,excelCoord:'N7',cellValue:'594',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:13,excelCoord:'O7',cellValue:'DRS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:14,excelCoord:'P7',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:0,excelCoord:'B8',cellValue:'초지5 생산량(톤/日)',cellType:'READONLY',rowSpan:1,colSpan:3,isLocked:1},
          {rowIndex:3,colIndex:3,excelCoord:'E8',cellValue:'85',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:4,excelCoord:'F8',cellValue:'83.8',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:5,excelCoord:'G8',cellValue:'76',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:6,excelCoord:'H8',cellValue:'83.5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:7,excelCoord:'I8',cellValue:'80.4',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:8,excelCoord:'J8',cellValue:'85.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:9,excelCoord:'K8',cellValue:'79.9',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:10,excelCoord:'L8',cellValue:'83.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:11,excelCoord:'M8',cellValue:'83',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:12,excelCoord:'N8',cellValue:'79.5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:13,excelCoord:'O8',cellValue:'SAP',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:14,excelCoord:'P8',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:0,excelCoord:'B9',cellValue:'수율(%)',cellType:'READONLY',rowSpan:3,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:1,excelCoord:'C9',cellValue:'PS',cellType:'READONLY',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:2,excelCoord:'D9',cellValue:'완제품',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:3,excelCoord:'E9',cellValue:'91',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:4,excelCoord:'F9',cellValue:'97.7',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:5,excelCoord:'G9',cellValue:'99.2',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:6,excelCoord:'H9',cellValue:'98.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:7,excelCoord:'I9',cellValue:'98.7',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:8,excelCoord:'J9',cellValue:'97.2',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:9,excelCoord:'K9',cellValue:'101.5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:10,excelCoord:'L9',cellValue:'101.8',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:11,excelCoord:'M9',cellValue:'99.8',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:12,excelCoord:'N9',cellValue:'98.7',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:13,excelCoord:'O9',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'yoo',ownerNames:'유동현 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:4,colIndex:14,excelCoord:'P9',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:2,excelCoord:'D10',cellValue:'코팅제외',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:3,excelCoord:'E10',cellValue:'78',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:4,excelCoord:'F10',cellValue:'83.8',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:5,excelCoord:'G10',cellValue:'85.2',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:6,excelCoord:'H10',cellValue:'84.1',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:7,excelCoord:'I10',cellValue:'84.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:8,excelCoord:'J10',cellValue:'83.5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:9,excelCoord:'K10',cellValue:'87.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:10,excelCoord:'L10',cellValue:'88.2',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:11,excelCoord:'M10',cellValue:'86.3',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:12,excelCoord:'N10',cellValue:'84.7',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:13,excelCoord:'O10',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'yoo',ownerNames:'유동현 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:5,colIndex:14,excelCoord:'P10',cellValue:'- 완제품내 코팅 비율 14.0%',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:1,excelCoord:'C11',cellValue:'화장지',cellType:'READONLY',rowSpan:1,colSpan:2,isLocked:1},
          {rowIndex:6,colIndex:3,excelCoord:'E11',cellValue:'63.5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:4,excelCoord:'F11',cellValue:'63.5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:5,excelCoord:'G11',cellValue:'64.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:6,excelCoord:'H11',cellValue:'61.1',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:7,excelCoord:'I11',cellValue:'63.3',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:8,excelCoord:'J11',cellValue:'63.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:9,excelCoord:'K11',cellValue:'63.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:10,excelCoord:'L11',cellValue:'69.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:11,excelCoord:'M11',cellValue:'74.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:12,excelCoord:'N11',cellValue:'74.4',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:13,excelCoord:'O11',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'yoo',ownerNames:'유동현 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:6,colIndex:14,excelCoord:'P11',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:0,excelCoord:'B12',cellValue:'고지감량율(%)',cellType:'READONLY',rowSpan:1,colSpan:3,isLocked:1},
          {rowIndex:7,colIndex:3,excelCoord:'E12',cellValue:'-',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:4,excelCoord:'F12',cellValue:'15.8',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:5,excelCoord:'G12',cellValue:'14.8',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:6,excelCoord:'H12',cellValue:'12.7',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:7,excelCoord:'I12',cellValue:'11.2',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:8,excelCoord:'J12',cellValue:'11.8',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:9,excelCoord:'K12',cellValue:'13',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:10,excelCoord:'L12',cellValue:'14.2',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:11,excelCoord:'M12',cellValue:'15.9',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:12,excelCoord:'N12',cellValue:'16',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:13,excelCoord:'O12',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:14,excelCoord:'P12',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:0,excelCoord:'B13',cellValue:'슬러지원단위',cellType:'READONLY',rowSpan:2,colSpan:2,isLocked:1},
          {rowIndex:8,colIndex:2,excelCoord:'D13',cellValue:'제   지',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:3,excelCoord:'E13',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'jung',ownerNames:'정상엽 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:8,colIndex:4,excelCoord:'F13',cellValue:'89',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:5,excelCoord:'G13',cellValue:'91',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:6,excelCoord:'H13',cellValue:'94',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:7,excelCoord:'I13',cellValue:'99',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:8,excelCoord:'J13',cellValue:'104',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:9,excelCoord:'K13',cellValue:'96',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:10,excelCoord:'L13',cellValue:'84',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:11,excelCoord:'M13',cellValue:'82',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:12,excelCoord:'N13',cellValue:'84',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:13,excelCoord:'O13',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'jung',ownerNames:'정상엽 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:8,colIndex:14,excelCoord:'P13',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:2,excelCoord:'D14',cellValue:'화장지',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:3,excelCoord:'E14',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'jung',ownerNames:'정상엽 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:9,colIndex:4,excelCoord:'F14',cellValue:'76',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:5,excelCoord:'G14',cellValue:'64',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:6,excelCoord:'H14',cellValue:'81',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:7,excelCoord:'I14',cellValue:'58',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:8,excelCoord:'J14',cellValue:'68',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:9,excelCoord:'K14',cellValue:'50',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:10,excelCoord:'L14',cellValue:'46',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:11,excelCoord:'M14',cellValue:'53',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:12,excelCoord:'N14',cellValue:'62',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:13,excelCoord:'O14',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'jung',ownerNames:'정상엽 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:9,colIndex:14,excelCoord:'P14',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
        ];

        // TBL_INVENTORY abbreviated (key rows)
        var tbl2Cells = [
          {rowIndex:0,colIndex:0,excelCoord:'B19',cellValue:'구 분',cellType:'HEADER',rowSpan:2,colSpan:2,isLocked:1},
          {rowIndex:0,colIndex:2,excelCoord:'D19',cellValue:'기준',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:3,excelCoord:'E19',cellValue:'적정재고',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:4,excelCoord:'F19',cellValue:"'25년\n12월",cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:5,excelCoord:'G19',cellValue:"'26년",cellType:'HEADER',rowSpan:1,colSpan:7,isLocked:1},
          {rowIndex:0,colIndex:12,excelCoord:'N19',cellValue:'비 고',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:5,excelCoord:'G20',cellValue:'1월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:6,excelCoord:'H20',cellValue:'2월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:7,excelCoord:'I20',cellValue:'3월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:8,excelCoord:'J20',cellValue:'4월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:9,excelCoord:'K20',cellValue:'5월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:10,excelCoord:'L20',cellValue:'6월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:11,excelCoord:'M20',cellValue:'7월',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:12,excelCoord:'N20',cellValue:null,cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:0,excelCoord:'B21',cellValue:'제지 재공품',cellType:'READONLY',rowSpan:4,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:1,excelCoord:'C21',cellValue:'밀롤창고',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:2,excelCoord:'D21',cellValue:'톤',cellType:'READONLY',rowSpan:4,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:3,excelCoord:'E21',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'kim',ownerNames:'김완중 팀장',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:2,colIndex:4,excelCoord:'F21',cellValue:'3826',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:5,excelCoord:'G21',cellValue:'3043',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:6,excelCoord:'H21',cellValue:'3296',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:7,excelCoord:'I21',cellValue:'2196',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:8,excelCoord:'J21',cellValue:'3037',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:9,excelCoord:'K21',cellValue:'3711',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:10,excelCoord:'L21',cellValue:'3006',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:11,excelCoord:'M21',cellValue:'MES',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:12,excelCoord:'N21',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:1,excelCoord:'C22',cellValue:'카타대기',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:3,excelCoord:'E22',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'kim',ownerNames:'김완중 팀장',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:3,colIndex:4,excelCoord:'F22',cellValue:'320',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:5,excelCoord:'G22',cellValue:'315',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:6,excelCoord:'H22',cellValue:'549',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:7,excelCoord:'I22',cellValue:'648',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:8,excelCoord:'J22',cellValue:'1360',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:9,excelCoord:'K22',cellValue:'1121',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:10,excelCoord:'L22',cellValue:'1110',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:11,excelCoord:'M22',cellValue:'MES',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:12,excelCoord:'N22',cellValue:'- 제지 카타 동시 가동/운휴에 따른 재공 증가',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:1,excelCoord:'C23',cellValue:'미포장',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:3,excelCoord:'E23',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'kim',ownerNames:'김완중 팀장',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:4,colIndex:4,excelCoord:'F23',cellValue:'212',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:5,excelCoord:'G23',cellValue:'764',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:6,excelCoord:'H23',cellValue:'702',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:7,excelCoord:'I23',cellValue:'149',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:8,excelCoord:'J23',cellValue:'86',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:9,excelCoord:'K23',cellValue:'173',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:10,excelCoord:'L23',cellValue:'266',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:11,excelCoord:'M23',cellValue:'MES',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:12,excelCoord:'N23',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:1,excelCoord:'C24',cellValue:'포장후 물류입고전',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:3,excelCoord:'E24',cellValue:'',cellType:'DATA',freqCode:'event',freqLabel:'발생 시',ownerIds:'kim',ownerNames:'김완중 팀장',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:5,colIndex:4,excelCoord:'F24',cellValue:'83',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:5,excelCoord:'G24',cellValue:'139',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:6,excelCoord:'H24',cellValue:'151',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:7,excelCoord:'I24',cellValue:'88',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:8,excelCoord:'J24',cellValue:'58',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:9,excelCoord:'K24',cellValue:'288',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:10,excelCoord:'L24',cellValue:'423',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:11,excelCoord:'M24',cellValue:'MES',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:12,excelCoord:'N24',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:0,excelCoord:'B25',cellValue:'장기재고',cellType:'READONLY',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:1,excelCoord:'C25',cellValue:'3개월 초과',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:2,excelCoord:'D25',cellValue:'톤',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:3,excelCoord:'E25',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:4,excelCoord:'F25',cellValue:'4354',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:6,colIndex:5,excelCoord:'G25',cellValue:'4372',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:6,colIndex:6,excelCoord:'H25',cellValue:'4005',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:6,colIndex:7,excelCoord:'I25',cellValue:'4236',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:6,colIndex:8,excelCoord:'J25',cellValue:'3761',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:6,colIndex:9,excelCoord:'K25',cellValue:'3404',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:6,colIndex:10,excelCoord:'L25',cellValue:'3120',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:11,excelCoord:'M25',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'jang lee',ownerNames:'장석환 선임, 이도형 사원',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:6,colIndex:12,excelCoord:'N25',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:1,excelCoord:'C26',cellValue:'6개월 초과',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:2,excelCoord:'D26',cellValue:'톤',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:3,excelCoord:'E26',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:4,excelCoord:'F26',cellValue:'917',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:7,colIndex:5,excelCoord:'G26',cellValue:'980',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:7,colIndex:6,excelCoord:'H26',cellValue:'786',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:7,colIndex:7,excelCoord:'I26',cellValue:'915',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:7,colIndex:8,excelCoord:'J26',cellValue:'957',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:7,colIndex:9,excelCoord:'K26',cellValue:'1543',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:7,colIndex:10,excelCoord:'L26',cellValue:'1130',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:11,excelCoord:'M26',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'jang lee',ownerNames:'장석환 선임, 이도형 사원',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:7,colIndex:12,excelCoord:'N26',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:0,excelCoord:'B27',cellValue:'야적현황',cellType:'READONLY',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:8,colIndex:1,excelCoord:'C27',cellValue:'제지',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:2,excelCoord:'D27',cellValue:'톤',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:3,excelCoord:'E27',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:4,excelCoord:'F27',cellValue:'489',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:5,excelCoord:'G27',cellValue:'239',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:6,excelCoord:'H27',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:7,excelCoord:'I27',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:8,excelCoord:'J27',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:9,excelCoord:'K27',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:10,excelCoord:'L27',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:11,excelCoord:'M27',cellValue:'WMS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:8,colIndex:12,excelCoord:'N27',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:9,colIndex:1,excelCoord:'C28',cellValue:'생활',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:2,excelCoord:'D28',cellValue:'팔레트',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:3,excelCoord:'E28',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:4,excelCoord:'F28',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:5,excelCoord:'G28',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:6,excelCoord:'H28',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:7,excelCoord:'I28',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:8,excelCoord:'J28',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:9,excelCoord:'K28',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:10,excelCoord:'L28',cellValue:'0',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:11,excelCoord:'M28',cellValue:'WMS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:9,colIndex:12,excelCoord:'N28',cellValue:null,cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
        ];

        // TBL_ENERGY
        var tbl3Cells = [
          {rowIndex:0,colIndex:0,excelCoord:'B34',cellValue:'구분',cellType:'HEADER',rowSpan:2,colSpan:2,isLocked:1},
          {rowIndex:0,colIndex:2,excelCoord:'D34',cellValue:'목표',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:3,excelCoord:'E34',cellValue:'6월 실적',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:4,excelCoord:'F34',cellValue:'7월 현재',cellType:'HEADER',rowSpan:1,colSpan:2,isLocked:1},
          {rowIndex:1,colIndex:4,excelCoord:'F35',cellValue:'계 획',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:5,excelCoord:'G35',cellValue:'실 적',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:0,excelCoord:'B36',cellValue:'전력',cellType:'READONLY',rowSpan:3,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:1,excelCoord:'C36',cellValue:'제   지',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:2,excelCoord:'D36',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'choi',ownerNames:'최민우 사원',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:2,colIndex:3,excelCoord:'E36',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:4,excelCoord:'F36',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'choi',ownerNames:'최민우 사원',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:2,colIndex:5,excelCoord:'G36',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:1,excelCoord:'C37',cellValue:'화장지',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:2,excelCoord:'D37',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'choi',ownerNames:'최민우 사원',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:3,colIndex:3,excelCoord:'E37',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:4,excelCoord:'F37',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'choi',ownerNames:'최민우 사원',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:3,colIndex:5,excelCoord:'G37',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:1,excelCoord:'C38',cellValue:'화)초지5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:2,excelCoord:'D38',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'choi',ownerNames:'최민우 사원',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:4,colIndex:3,excelCoord:'E38',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:4,excelCoord:'F38',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'choi',ownerNames:'최민우 사원',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:4,colIndex:5,excelCoord:'G38',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:0,excelCoord:'B39',cellValue:'연료',cellType:'READONLY',rowSpan:3,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:1,excelCoord:'C39',cellValue:'제   지',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:2,excelCoord:'D39',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:5,colIndex:3,excelCoord:'E39',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:4,excelCoord:'F39',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:5,colIndex:5,excelCoord:'G39',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:1,excelCoord:'C40',cellValue:'화장지',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:2,excelCoord:'D40',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:6,colIndex:3,excelCoord:'E40',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:4,excelCoord:'F40',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:6,colIndex:5,excelCoord:'G40',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:1,excelCoord:'C41',cellValue:'화)초지5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:2,excelCoord:'D41',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:7,colIndex:3,excelCoord:'E41',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:7,colIndex:4,excelCoord:'F41',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:7,colIndex:5,excelCoord:'G41',cellValue:'EIS',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
        ];

        // TBL_BOILER
        var tbl4Cells = [
          {rowIndex:0,colIndex:0,excelCoord:'J34',cellValue:'구분',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:1,excelCoord:'K34',cellValue:'목표',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:2,excelCoord:'L34',cellValue:'7월단가\n(천원/톤)',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:3,excelCoord:'M34',cellValue:'5월 실적',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:4,excelCoord:'N34',cellValue:'6월 실적',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:0,colIndex:5,excelCoord:'O34',cellValue:'7월',cellType:'HEADER',rowSpan:1,colSpan:2,isLocked:1},
          {rowIndex:0,colIndex:7,excelCoord:'Q34',cellValue:'비 고',cellType:'HEADER',rowSpan:2,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:5,excelCoord:'O35',cellValue:'계 획',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:1,colIndex:6,excelCoord:'P35',cellValue:'실 적',cellType:'HEADER',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:0,excelCoord:'J36',cellValue:'LNG보일러',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:1,excelCoord:'K36',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:2,colIndex:2,excelCoord:'L36',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:2,colIndex:3,excelCoord:'M36',cellValue:'2.4',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:4,excelCoord:'N36',cellValue:'0.4',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:2,colIndex:5,excelCoord:'O36',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:2,colIndex:6,excelCoord:'P36',cellValue:'',cellType:'DATA',freqCode:'daily',freqLabel:'매일',ownerIds:'energy',ownerNames:'환경에너지팀 반장',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:2,colIndex:7,excelCoord:'Q36',cellValue:'복합보일러 운휴...',cellType:'READONLY',rowSpan:5,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:0,excelCoord:'J37',cellValue:'유동상소각로',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:1,excelCoord:'K37',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:3,colIndex:2,excelCoord:'L37',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:3,colIndex:3,excelCoord:'M37',cellValue:'15.3',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:3,colIndex:4,excelCoord:'N37',cellValue:'14.7',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:3,colIndex:5,excelCoord:'O37',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:3,colIndex:6,excelCoord:'P37',cellValue:'',cellType:'DATA',freqCode:'daily',freqLabel:'매일',ownerIds:'energy',ownerNames:'환경에너지팀 반장',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:4,colIndex:0,excelCoord:'J38',cellValue:'복합보일러',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:1,excelCoord:'K38',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:4,colIndex:2,excelCoord:'L38',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:4,colIndex:3,excelCoord:'M38',cellValue:'56.8',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:4,colIndex:4,excelCoord:'N38',cellValue:'52.5',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:4,colIndex:5,excelCoord:'O38',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:4,colIndex:6,excelCoord:'P38',cellValue:'',cellType:'DATA',freqCode:'daily',freqLabel:'매일',ownerIds:'energy',ownerNames:'환경에너지팀 반장',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:5,colIndex:0,excelCoord:'J39',cellValue:'폐합성소각로',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:1,excelCoord:'K39',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:5,colIndex:2,excelCoord:'L39',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:5,colIndex:3,excelCoord:'M39',cellValue:'10.2',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:5,colIndex:4,excelCoord:'N39',cellValue:'11.6',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:5,colIndex:5,excelCoord:'O39',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:5,colIndex:6,excelCoord:'P39',cellValue:'',cellType:'DATA',freqCode:'daily',freqLabel:'매일',ownerIds:'energy',ownerNames:'환경에너지팀 반장',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:6,colIndex:0,excelCoord:'J40',cellValue:'합  계',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:1,excelCoord:'K40',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:6,colIndex:2,excelCoord:'L40',cellValue:'',cellType:'DATA',freqCode:'yearly',freqLabel:'매년',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:6,colIndex:3,excelCoord:'M40',cellValue:'84.7',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},{rowIndex:6,colIndex:4,excelCoord:'N40',cellValue:'79.2',cellType:'READONLY',rowSpan:1,colSpan:1,isLocked:1},
          {rowIndex:6,colIndex:5,excelCoord:'O40',cellValue:'',cellType:'DATA',freqCode:'monthly',freqLabel:'매월',ownerIds:'park',ownerNames:'박지권 책임',isLocked:0,rowSpan:1,colSpan:1},
          {rowIndex:6,colIndex:6,excelCoord:'P40',cellValue:'',cellType:'DATA',freqCode:'daily',freqLabel:'매일',ownerIds:'energy',ownerNames:'환경에너지팀 반장',isLocked:0,rowSpan:1,colSpan:1},
        ];

        var renderData = {
          report: { reportId: 3, reportDate: reportDate, title: reportDate + ' 세부공장일보', status: 'DRAFT' },
          tables: {
            'TBL_PRODUCTION_INDEX': { tableCode:'TBL_PRODUCTION_INDEX', tableName:'주요 생산 지표 현황', sortOrder:1, rowCount:10, colCount:15, cells: buildCells('TBL_PRODUCTION_INDEX', tbl1Cells) },
            'TBL_INVENTORY': { tableCode:'TBL_INVENTORY', tableName:'제지 재공품 및 야적현황', sortOrder:2, rowCount:10, colCount:13, cells: buildCells('TBL_INVENTORY', tbl2Cells) },
            'TBL_ENERGY': { tableCode:'TBL_ENERGY', tableName:'에너지 원단위', sortOrder:3, rowCount:8, colCount:6, cells: buildCells('TBL_ENERGY', tbl3Cells) },
            'TBL_BOILER': { tableCode:'TBL_BOILER', tableName:'보일러 운영 현황', sortOrder:4, rowCount:7, colCount:8, cells: buildCells('TBL_BOILER', tbl4Cells) },
          },
          remarks: [{ remarkId:1, content:'공용 보기 화면 테스트 데이터입니다.', category:'GENERAL' }],
          images: [],
          permissions: { canAccessInput:true, canWriteInput:true, canAccessAuth: drCurrentUser.loginId === 'admin' || global._drCellAuths.some(function(a){ return a.userId === drCurrentUser.userId && a.isActive; }) }
        };
        return jsonRes(res, { success: true, code: 200, message: 'SUCCESS', data: renderData });
      }

      // ── /reports/:id/cells (POST - 셀 저장) ──
      if (pathname.match(/^\/dailyreport-api\/reports\/\d+\/cells$/) && method === 'POST') {
        var reportDate2 = parsedUrl.query.reportDate || '2024-07-20';
        if (!global._drCellData[reportDate2]) global._drCellData[reportDate2] = {};
        var payload = jsonBody || {};
        var tc = payload.tableCode || '';
        (payload.cells || []).forEach(function(c) {
          var key = tc + '__' + (c.excelCoord || c.coord || '');
          if (!key.includes('__')) return;
          global._drCellData[reportDate2][key] = c.cellValue;
        });
        return jsonRes(res, { success:true, saved: (payload.cells||[]).length });
      }

      // ── /reports/:id/remarks (POST - 특이사항 저장) ──
      if (pathname.match(/^\/dailyreport-api\/reports\/\d+\/remarks$/) && method === 'POST') {
        return jsonRes(res, { success:true });
      }

      // ── /cell-auths (GET - 목록) ──
      if (pathname === '/dailyreport-api/cell-auths' && method === 'GET') {
        var userId = parsedUrl.query.userId ? parseInt(parsedUrl.query.userId) : null;
        var tableCode = parsedUrl.query.tableCode || null;
        var filtered = global._drCellAuths.filter(function(a){
          if (userId && a.userId !== userId) return false;
          if (tableCode && a.tableCode !== tableCode) return false;
          return true;
        });
        return jsonRes(res, { data: filtered });
      }

      // ── /cell-auths (POST - 등록) ──
      if (pathname === '/dailyreport-api/cell-auths' && method === 'POST') {
        var newAuth = Object.assign({ authId: global._drNextAuthId++, isActive:true, grantedBy:drCurrentUser.userId }, jsonBody);
        newAuth.cellCoords = typeof newAuth.cellCoords === 'object' ? JSON.stringify(newAuth.cellCoords) : newAuth.cellCoords;
        var usr = drUsers.find(function(u){return u.userId===newAuth.userId;});
        if(usr){newAuth.loginId=usr.loginId;newAuth.userName=usr.userName;}
        global._drCellAuths.push(newAuth);
        return jsonRes(res, { data: newAuth });
      }

      // ── /cell-auths/:id (PUT - 수정) ──
      if (pathname.match(/^\/dailyreport-api\/cell-auths\/\d+$/) && method === 'PUT') {
        var authId = parseInt(pathname.split('/').pop());
        var auth = global._drCellAuths.find(function(a){return a.authId===authId;});
        if(auth){
          Object.assign(auth, jsonBody);
          auth.cellCoords = typeof auth.cellCoords === 'object' ? JSON.stringify(auth.cellCoords) : auth.cellCoords;
          // userId 변경 시 loginId/userName 갱신
          var usr = drUsers.find(function(u){return u.userId===auth.userId;});
          if(usr){auth.loginId=usr.loginId;auth.userName=usr.userName;}
          return jsonRes(res, { data: auth });
        }
        return apiErr(res, 'Not found', 404);
      }

      // ── /cell-auths/:id/deactivate (PATCH) ──
      if (pathname.match(/^\/dailyreport-api\/cell-auths\/\d+\/deactivate$/) && method === 'PATCH') {
        var authId2 = parseInt(pathname.split('/')[3]);
        var auth2 = global._drCellAuths.find(function(a){return a.authId===authId2;});
        if(auth2){ auth2.isActive=false; return jsonRes(res, { data: auth2 }); }
        return apiErr(res, 'Not found', 404);
      }

      // ── /cell-auths/:id (DELETE) ──
      if (pathname.match(/^\/dailyreport-api\/cell-auths\/\d+$/) && method === 'DELETE') {
        var authId3 = parseInt(pathname.split('/').pop());
        global._drCellAuths = global._drCellAuths.filter(function(a){return a.authId!==authId3;});
        return jsonRes(res, { success: true });
      }

      // ── /accounts (GET - 사용자 목록) ──
      if (pathname === '/dailyreport-api/accounts') {
        return jsonRes(res, { data: drUsers });
      }

      // ── Catch-all dailyreport-api ──
      return jsonRes(res, { message: 'Daily Report Mock API', path: pathname });
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

    // Daily Report static files
    const drStaticPath = path.join(__dirname, 'module-dailyreport/src/main/resources/static', pathname);
    if (serveFile(drStaticPath, res)) return;

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
  console.log('║  세부공장일보 (Daily Report):                             ║');
  console.log('║    GET /dailyreport-api/view/my-permissions               ║');
  console.log('║    GET /dailyreport-api/view/render?reportDate=...        ║');
  console.log('║    * /cell-auths CRUD (GET/POST/PUT/PATCH/DELETE)         ║');
  console.log('║                                                           ║');
  console.log('║  Note: Backend DB 없이 Mock 데이터로 동작합니다.        ║');
  console.log('╚═══════════════════════════════════════════════════════════╝');
  console.log('');
});
