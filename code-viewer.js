/**
 * Platform Code Viewer Server
 * - GitHub 스타일 파일 트리 + 소스코드 미리보기
 * - API: /viewer-api/tree, /viewer-api/file?path=...
 * - UI: /viewer (메인 페이지)
 */
const http = require('http');
const fs = require('fs');
const path = require('path');
const url = require('url');

const PORT = 3000;
const PROJECT_ROOT = __dirname;
const PROJECT_NAME = 'platform';

// 제외할 디렉토리/파일
const IGNORE_DIRS = new Set(['.git', 'node_modules', '.gradle', 'build', '.wrangler', 'dist', '.idea', '.vscode']);
const IGNORE_FILES = new Set(['.DS_Store', 'Thumbs.db']);
// 바이너리 확장자
const BINARY_EXT = new Set(['.jar', '.class', '.png', '.jpg', '.jpeg', '.gif', '.ico', '.bmp', '.webp', '.svg',
  '.woff', '.woff2', '.ttf', '.eot', '.pdf', '.zip', '.tar', '.gz', '.pack', '.idx', '.rev', '.JPG', '.PNG']);

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
};

// ── 파일 트리 빌드 ──
function buildTree(dirPath, relativePath = '') {
  const entries = [];
  let items;
  try { items = fs.readdirSync(dirPath, { withFileTypes: true }); } catch(e) { return entries; }

  // 정렬: 디렉토리 먼저, 그 안에서 이름순
  items.sort((a, b) => {
    if (a.isDirectory() && !b.isDirectory()) return -1;
    if (!a.isDirectory() && b.isDirectory()) return 1;
    return a.name.localeCompare(b.name);
  });

  for (const item of items) {
    if (IGNORE_DIRS.has(item.name) || IGNORE_FILES.has(item.name)) continue;

    const fullPath = path.join(dirPath, item.name);
    const relPath = relativePath ? `${relativePath}/${item.name}` : item.name;

    if (item.isDirectory()) {
      const children = buildTree(fullPath, relPath);
      entries.push({
        name: item.name,
        path: relPath,
        type: 'directory',
        children,
        count: countFiles(children),
      });
    } else {
      const ext = path.extname(item.name).toLowerCase();
      let size = 0;
      try { size = fs.statSync(fullPath).size; } catch(e) {}
      entries.push({
        name: item.name,
        path: relPath,
        type: 'file',
        ext: ext,
        size,
        binary: BINARY_EXT.has(ext) || BINARY_EXT.has(path.extname(item.name)),
      });
    }
  }
  return entries;
}

function countFiles(children) {
  let count = 0;
  for (const c of children) {
    if (c.type === 'file') count++;
    else count += c.count || 0;
  }
  return count;
}

// ── 파일 내용 읽기 ──
function readFileContent(relPath) {
  const safePath = path.normalize(relPath).replace(/^(\.\.[\/\\])+/, '');
  const fullPath = path.join(PROJECT_ROOT, safePath);

  // 프로젝트 루트 안에 있는지 확인
  if (!fullPath.startsWith(PROJECT_ROOT)) {
    return { error: 'Access denied', status: 403 };
  }

  if (!fs.existsSync(fullPath)) {
    return { error: 'File not found', status: 404 };
  }

  const stat = fs.statSync(fullPath);
  if (stat.isDirectory()) {
    return { error: 'Is a directory', status: 400 };
  }

  const ext = path.extname(fullPath).toLowerCase();
  const origExt = path.extname(fullPath);

  if (BINARY_EXT.has(ext) || BINARY_EXT.has(origExt)) {
    // 이미지는 base64로
    if (['.png','.jpg','.jpeg','.gif','.bmp','.webp','.svg','.ico','.PNG','.JPG','.JPEG'].includes(origExt) ||
        ['.png','.jpg','.jpeg','.gif','.bmp','.webp','.svg','.ico'].includes(ext)) {
      const mime = MIME[ext] || MIME[origExt.toLowerCase()] || 'application/octet-stream';
      const data = fs.readFileSync(fullPath);
      return {
        content: null,
        binary: true,
        image: true,
        imageData: `data:${mime};base64,${data.toString('base64')}`,
        size: stat.size,
        name: path.basename(fullPath),
      };
    }
    return {
      content: null,
      binary: true,
      image: false,
      size: stat.size,
      name: path.basename(fullPath),
    };
  }

  // 텍스트 파일 - 최대 500KB
  if (stat.size > 500 * 1024) {
    return {
      content: '(파일이 너무 큽니다: ' + (stat.size / 1024).toFixed(1) + 'KB)',
      binary: false,
      size: stat.size,
      name: path.basename(fullPath),
      truncated: true,
    };
  }

  const content = fs.readFileSync(fullPath, 'utf8');
  return {
    content,
    binary: false,
    size: stat.size,
    name: path.basename(fullPath),
    lines: content.split('\n').length,
  };
}

// ── 언어 감지 ──
function detectLanguage(filename) {
  const ext = path.extname(filename).toLowerCase();
  const map = {
    '.java': 'java', '.kt': 'kotlin', '.kts': 'kotlin',
    '.js': 'javascript', '.ts': 'typescript', '.tsx': 'tsx', '.jsx': 'jsx',
    '.py': 'python', '.rb': 'ruby', '.go': 'go', '.rs': 'rust',
    '.html': 'html', '.htm': 'html', '.xml': 'xml',
    '.css': 'css', '.scss': 'scss', '.less': 'less',
    '.json': 'json', '.yaml': 'yaml', '.yml': 'yaml',
    '.md': 'markdown', '.sql': 'sql',
    '.sh': 'bash', '.bat': 'batch',
    '.gradle': 'groovy', '.groovy': 'groovy',
    '.properties': 'properties', '.conf': 'nginx',
    '.toml': 'toml', '.ini': 'ini',
    '.dockerfile': 'dockerfile',
  };
  const basename = path.basename(filename).toLowerCase();
  if (basename === 'dockerfile') return 'dockerfile';
  if (basename === 'gradlew') return 'bash';
  if (basename === '.gitignore') return 'gitignore';
  if (basename === '.env.example' || basename === '.env') return 'bash';
  return map[ext] || 'plaintext';
}

// ── 프로젝트 통계 ──
function getProjectStats(tree) {
  const stats = { totalFiles: 0, totalDirs: 0, byExt: {}, byModule: {} };
  function walk(nodes, module = '') {
    for (const n of nodes) {
      if (n.type === 'directory') {
        stats.totalDirs++;
        const mod = module || n.name;
        walk(n.children, mod);
      } else {
        stats.totalFiles++;
        const ext = n.ext || 'other';
        stats.byExt[ext] = (stats.byExt[ext] || 0) + 1;
        if (module) {
          stats.byModule[module] = (stats.byModule[module] || 0) + 1;
        }
      }
    }
  }
  walk(tree);
  return stats;
}

// ── 검색 ──
function searchFiles(tree, query) {
  const results = [];
  const q = query.toLowerCase();
  function walk(nodes) {
    for (const n of nodes) {
      if (n.type === 'file') {
        if (n.name.toLowerCase().includes(q) || n.path.toLowerCase().includes(q)) {
          results.push({ name: n.name, path: n.path, ext: n.ext });
        }
      } else {
        walk(n.children);
      }
    }
  }
  walk(tree);
  return results.slice(0, 100);
}

// ── 메인 UI HTML ──
function getViewerHTML() {
  return `<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>${PROJECT_NAME} - Source Code Viewer</title>
<link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/styles/github-dark.min.css">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/@fortawesome/fontawesome-free@6.4.0/css/all.min.css">
<link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/marked@12.0.0/lib/marked.min.js">
<style>
:root {
  --bg-primary: #0d1117;
  --bg-secondary: #161b22;
  --bg-tertiary: #21262d;
  --border: #30363d;
  --text-primary: #e6edf3;
  --text-secondary: #8b949e;
  --text-muted: #6e7681;
  --accent: #58a6ff;
  --accent-hover: #79c0ff;
  --green: #3fb950;
  --yellow: #d29922;
  --red: #f85149;
  --purple: #bc8cff;
  --sidebar-width: 320px;
  --header-height: 56px;
}
* { margin:0; padding:0; box-sizing:border-box; }
body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Noto Sans KR', Helvetica, Arial, sans-serif;
  background: var(--bg-primary);
  color: var(--text-primary);
  overflow: hidden;
  height: 100vh;
}

/* ── Header ── */
.header {
  height: var(--header-height);
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 16px;
  z-index: 100;
}
.header .logo {
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-primary);
  white-space: nowrap;
}
.header .logo i { color: var(--accent); }
.header .search-box {
  flex: 1;
  max-width: 500px;
  position: relative;
}
.header .search-box input {
  width: 100%;
  padding: 8px 12px 8px 36px;
  border-radius: 6px;
  border: 1px solid var(--border);
  background: var(--bg-primary);
  color: var(--text-primary);
  font-size: 14px;
  outline: none;
}
.header .search-box input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px rgba(88,166,255,0.15);
}
.header .search-box i {
  position: absolute;
  left: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--text-muted);
  font-size: 13px;
}
.header .stats {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.header .stats span i { margin-right: 4px; }

/* ── Search Results Dropdown ── */
.search-results {
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 8px;
  margin-top: 4px;
  max-height: 400px;
  overflow-y: auto;
  z-index: 200;
  display: none;
  box-shadow: 0 8px 24px rgba(0,0,0,0.4);
}
.search-results.show { display: block; }
.search-results .sr-item {
  padding: 8px 14px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
  font-size: 13px;
  border-bottom: 1px solid var(--border);
}
.search-results .sr-item:last-child { border-bottom: none; }
.search-results .sr-item:hover { background: var(--bg-tertiary); }
.search-results .sr-item .sr-name { color: var(--text-primary); font-weight: 600; }
.search-results .sr-item .sr-path { color: var(--text-muted); font-size: 12px; margin-left: auto; }

/* ── Layout ── */
.layout {
  display: flex;
  height: calc(100vh - var(--header-height));
}

/* ── Sidebar ── */
.sidebar {
  width: var(--sidebar-width);
  min-width: var(--sidebar-width);
  background: var(--bg-secondary);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sidebar-header {
  padding: 12px 16px;
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  font-weight: 600;
  color: var(--text-secondary);
}
.sidebar-header i { color: var(--accent); }
.tree-container {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}
.tree-container::-webkit-scrollbar { width: 6px; }
.tree-container::-webkit-scrollbar-track { background: transparent; }
.tree-container::-webkit-scrollbar-thumb { background: var(--border); border-radius: 3px; }

/* Tree items */
.tree-item {
  display: flex;
  align-items: center;
  padding: 3px 8px 3px 0;
  cursor: pointer;
  font-size: 13px;
  white-space: nowrap;
  user-select: none;
  color: var(--text-primary);
  transition: background 0.1s;
}
.tree-item:hover { background: var(--bg-tertiary); }
.tree-item.active { background: rgba(88,166,255,0.1); color: var(--accent); }
.tree-item .indent { display: inline-block; width: 16px; flex-shrink: 0; }
.tree-item .chevron {
  width: 16px;
  text-align: center;
  font-size: 10px;
  color: var(--text-muted);
  flex-shrink: 0;
  transition: transform 0.15s;
}
.tree-item .chevron.open { transform: rotate(90deg); }
.tree-item .icon {
  width: 20px;
  text-align: center;
  font-size: 13px;
  flex-shrink: 0;
  margin-right: 6px;
}
.tree-item .icon.dir { color: var(--accent); }
.tree-item .icon.file-java { color: #f89820; }
.tree-item .icon.file-js { color: #f7df1e; }
.tree-item .icon.file-html { color: #e44d26; }
.tree-item .icon.file-css { color: #264de4; }
.tree-item .icon.file-json { color: var(--yellow); }
.tree-item .icon.file-md { color: var(--accent); }
.tree-item .icon.file-sql { color: var(--purple); }
.tree-item .icon.file-yml { color: var(--red); }
.tree-item .icon.file-gradle { color: #02303a; }
.tree-item .icon.file-xml { color: var(--green); }
.tree-item .icon.file-img { color: var(--green); }
.tree-item .icon.file-default { color: var(--text-muted); }
.tree-item .name { overflow: hidden; text-overflow: ellipsis; }
.tree-item .badge {
  margin-left: auto;
  padding: 0 6px;
  font-size: 10px;
  border-radius: 10px;
  background: var(--bg-tertiary);
  color: var(--text-muted);
  flex-shrink: 0;
}
.tree-children { display: none; }
.tree-children.open { display: block; }

/* ── Content Area ── */
.content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.content-header {
  padding: 10px 20px;
  background: var(--bg-secondary);
  border-bottom: 1px solid var(--border);
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  min-height: 44px;
}
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 4px;
  flex-wrap: wrap;
}
.breadcrumb span { color: var(--text-muted); }
.breadcrumb a {
  color: var(--accent);
  text-decoration: none;
  cursor: pointer;
}
.breadcrumb a:hover { text-decoration: underline; }
.file-info {
  margin-left: auto;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--text-secondary);
  white-space: nowrap;
}
.file-info span i { margin-right: 4px; }

.content-body {
  flex: 1;
  overflow: auto;
  padding: 0;
}
.content-body::-webkit-scrollbar { width: 8px; height: 8px; }
.content-body::-webkit-scrollbar-track { background: var(--bg-primary); }
.content-body::-webkit-scrollbar-thumb { background: var(--border); border-radius: 4px; }

/* Code view */
.code-wrapper {
  display: flex;
  min-width: fit-content;
}
.line-numbers {
  padding: 16px 0;
  text-align: right;
  user-select: none;
  background: var(--bg-secondary);
  border-right: 1px solid var(--border);
  position: sticky;
  left: 0;
  z-index: 1;
}
.line-numbers span {
  display: block;
  padding: 0 12px 0 16px;
  font-size: 13px;
  line-height: 20px;
  color: var(--text-muted);
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', Consolas, monospace;
}
.line-numbers span:hover { color: var(--text-primary); cursor: pointer; }
.code-content {
  flex: 1;
  padding: 16px;
  overflow-x: auto;
}
.code-content pre {
  margin: 0;
  font-size: 13px;
  line-height: 20px;
  font-family: 'SF Mono', 'Fira Code', 'Cascadia Code', Consolas, monospace;
  background: transparent !important;
  padding: 0 !important;
}
.code-content code {
  background: transparent !important;
  font-family: inherit;
}

/* Welcome / empty state */
.welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  color: var(--text-secondary);
  padding: 40px;
}
.welcome i.big-icon { font-size: 64px; color: var(--border); margin-bottom: 24px; }
.welcome h2 { font-size: 22px; color: var(--text-primary); margin-bottom: 10px; }
.welcome p { font-size: 14px; line-height: 1.7; max-width: 500px; }
.welcome .module-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 12px;
  margin-top: 24px;
  width: 100%;
  max-width: 600px;
}
.welcome .module-card {
  padding: 16px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 8px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.2s;
}
.welcome .module-card:hover { border-color: var(--accent); }
.welcome .module-card h4 { font-size: 14px; color: var(--text-primary); margin-bottom: 4px; }
.welcome .module-card p { font-size: 12px; color: var(--text-muted); margin: 0; }

/* Binary / image preview */
.binary-info, .image-preview {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px;
  text-align: center;
  height: 100%;
}
.binary-info i { font-size: 48px; color: var(--text-muted); margin-bottom: 16px; }
.binary-info h3 { color: var(--text-primary); margin-bottom: 8px; }
.binary-info p { color: var(--text-secondary); }
.image-preview img { max-width: 90%; max-height: 70vh; border-radius: 8px; border: 1px solid var(--border); }
.image-preview .img-label { margin-top: 12px; color: var(--text-muted); font-size: 13px; }

/* Markdown */
.markdown-body {
  padding: 32px;
  max-width: 900px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--text-primary);
}
.markdown-body h1, .markdown-body h2, .markdown-body h3 {
  border-bottom: 1px solid var(--border);
  padding-bottom: 8px;
  margin: 24px 0 16px;
}
.markdown-body h1 { font-size: 28px; }
.markdown-body h2 { font-size: 22px; }
.markdown-body h3 { font-size: 18px; }
.markdown-body p { margin: 12px 0; }
.markdown-body code {
  background: var(--bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: 'SF Mono', Consolas, monospace;
}
.markdown-body pre {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: 8px;
  padding: 16px;
  overflow-x: auto;
  margin: 16px 0;
}
.markdown-body pre code {
  background: transparent;
  padding: 0;
  font-size: 13px;
  line-height: 1.5;
}
.markdown-body table {
  border-collapse: collapse;
  width: 100%;
  margin: 16px 0;
}
.markdown-body th, .markdown-body td {
  border: 1px solid var(--border);
  padding: 8px 12px;
  text-align: left;
}
.markdown-body th { background: var(--bg-secondary); }
.markdown-body a { color: var(--accent); }
.markdown-body ul, .markdown-body ol { padding-left: 24px; margin: 12px 0; }
.markdown-body li { margin: 4px 0; }
.markdown-body blockquote {
  border-left: 3px solid var(--accent);
  padding: 4px 16px;
  color: var(--text-secondary);
  margin: 16px 0;
  background: var(--bg-secondary);
  border-radius: 0 4px 4px 0;
}
.markdown-body hr { border: none; border-top: 1px solid var(--border); margin: 24px 0; }
.markdown-body img { max-width: 100%; border-radius: 8px; }

/* Resize handle */
.resize-handle {
  width: 3px;
  cursor: col-resize;
  background: transparent;
  transition: background 0.2s;
  flex-shrink: 0;
}
.resize-handle:hover, .resize-handle.active { background: var(--accent); }

/* Responsive */
@media (max-width: 768px) {
  .sidebar { width: 260px; min-width: 260px; }
  .header .stats { display: none; }
}
</style>
</head>
<body>

<div class="header">
  <div class="logo">
    <i class="fas fa-code-branch"></i>
    <span>${PROJECT_NAME}</span>
  </div>
  <div class="search-box">
    <i class="fas fa-search"></i>
    <input type="text" id="searchInput" placeholder="파일 검색... (파일명 또는 경로)" autocomplete="off">
    <div class="search-results" id="searchResults"></div>
  </div>
  <div class="stats" id="headerStats">
    <span><i class="fas fa-file-code"></i> <span id="statFiles">-</span> files</span>
    <span><i class="fas fa-folder"></i> <span id="statDirs">-</span> dirs</span>
  </div>
</div>

<div class="layout">
  <div class="sidebar" id="sidebar">
    <div class="sidebar-header">
      <i class="fas fa-sitemap"></i>
      <span>File Explorer</span>
    </div>
    <div class="tree-container" id="treeContainer"></div>
  </div>
  <div class="resize-handle" id="resizeHandle"></div>
  <div class="content">
    <div class="content-header" id="contentHeader">
      <div class="breadcrumb" id="breadcrumb">
        <a onclick="showWelcome()"><i class="fas fa-home"></i></a>
      </div>
      <div class="file-info" id="fileInfo"></div>
    </div>
    <div class="content-body" id="contentBody">
      <div class="welcome" id="welcomeView"></div>
    </div>
  </div>
</div>

<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/highlight.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/java.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/groovy.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/kotlin.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/sql.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/yaml.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/properties.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/bash.min.js"></script>
<script src="https://cdnjs.cloudflare.com/ajax/libs/highlight.js/11.9.0/languages/nginx.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/marked@12.0.0/marked.min.js"></script>
<script>
let treeData = [];
let projectStats = {};
let currentPath = null;

// ── Init ──
async function init() {
  const res = await fetch('/viewer-api/tree');
  const data = await res.json();
  treeData = data.tree;
  projectStats = data.stats;

  document.getElementById('statFiles').textContent = projectStats.totalFiles;
  document.getElementById('statDirs').textContent = projectStats.totalDirs;

  renderTree(treeData, document.getElementById('treeContainer'), 0);
  showWelcome();

  // URL hash 처리
  if (location.hash) {
    const filePath = decodeURIComponent(location.hash.slice(1));
    if (filePath) openFile(filePath);
  }
}

// ── Render Tree ──
function renderTree(nodes, container, depth) {
  for (const node of nodes) {
    if (node.type === 'directory') {
      const item = document.createElement('div');
      item.className = 'tree-item';
      item.innerHTML =
        '<span class="indent" style="width:' + (depth * 16) + 'px"></span>' +
        '<span class="chevron"><i class="fas fa-chevron-right"></i></span>' +
        '<span class="icon dir"><i class="fas fa-folder"></i></span>' +
        '<span class="name">' + esc(node.name) + '</span>' +
        '<span class="badge">' + node.count + '</span>';

      const childDiv = document.createElement('div');
      childDiv.className = 'tree-children';

      item.addEventListener('click', (e) => {
        e.stopPropagation();
        const isOpen = childDiv.classList.toggle('open');
        item.querySelector('.chevron').classList.toggle('open', isOpen);
        const folderIcon = item.querySelector('.icon i');
        folderIcon.className = isOpen ? 'fas fa-folder-open' : 'fas fa-folder';
        if (isOpen && childDiv.children.length === 0) {
          renderTree(node.children, childDiv, depth + 1);
        }
      });

      container.appendChild(item);
      container.appendChild(childDiv);

      // 첫 번째 레벨은 자동 펼침
      if (depth === 0) {
        childDiv.classList.add('open');
        item.querySelector('.chevron').classList.add('open');
        item.querySelector('.icon i').className = 'fas fa-folder-open';
        renderTree(node.children, childDiv, depth + 1);
      }
    } else {
      const item = document.createElement('div');
      item.className = 'tree-item';
      item.dataset.path = node.path;
      const iconClass = getFileIconClass(node.name, node.ext);
      const iconFA = getFileIcon(node.name, node.ext);
      item.innerHTML =
        '<span class="indent" style="width:' + (depth * 16) + 'px"></span>' +
        '<span class="chevron"></span>' +
        '<span class="icon ' + iconClass + '"><i class="' + iconFA + '"></i></span>' +
        '<span class="name">' + esc(node.name) + '</span>';

      item.addEventListener('click', (e) => {
        e.stopPropagation();
        openFile(node.path);
      });

      container.appendChild(item);
    }
  }
}

function getFileIconClass(name, ext) {
  const e = (ext || '').toLowerCase();
  if (e === '.java') return 'file-java';
  if (e === '.js') return 'file-js';
  if (e === '.html' || e === '.htm') return 'file-html';
  if (e === '.css' || e === '.scss') return 'file-css';
  if (e === '.json') return 'file-json';
  if (e === '.md') return 'file-md';
  if (e === '.sql') return 'file-sql';
  if (e === '.yml' || e === '.yaml') return 'file-yml';
  if (e === '.gradle') return 'file-gradle';
  if (e === '.xml') return 'file-xml';
  if (['.png','.jpg','.jpeg','.gif','.svg','.ico'].includes(e)) return 'file-img';
  return 'file-default';
}

function getFileIcon(name, ext) {
  const e = (ext || '').toLowerCase();
  if (e === '.java') return 'fab fa-java';
  if (e === '.js') return 'fab fa-js';
  if (e === '.html' || e === '.htm') return 'fab fa-html5';
  if (e === '.css' || e === '.scss') return 'fab fa-css3-alt';
  if (e === '.json') return 'fas fa-brackets-curly';
  if (e === '.md') return 'fab fa-markdown';
  if (e === '.sql') return 'fas fa-database';
  if (e === '.yml' || e === '.yaml') return 'fas fa-file-code';
  if (e === '.gradle') return 'fas fa-cog';
  if (e === '.xml') return 'fas fa-file-code';
  if (['.png','.jpg','.jpeg','.gif','.svg','.ico'].includes(e)) return 'fas fa-image';
  if (e === '.properties') return 'fas fa-sliders-h';
  if (e === '.sh' || name === 'gradlew') return 'fas fa-terminal';
  if (e === '.bat') return 'fas fa-terminal';
  return 'fas fa-file';
}

// ── Open File ──
async function openFile(filePath) {
  currentPath = filePath;
  location.hash = encodeURIComponent(filePath);

  // 활성 표시
  document.querySelectorAll('.tree-item.active').forEach(el => el.classList.remove('active'));
  const target = document.querySelector('.tree-item[data-path="' + CSS.escape(filePath) + '"]');
  if (target) {
    target.classList.add('active');
    target.scrollIntoView({ block: 'nearest' });
  }

  // 브레드크럼
  const parts = filePath.split('/');
  let bc = '<a onclick="showWelcome()"><i class="fas fa-home"></i></a>';
  let accumulated = '';
  for (let i = 0; i < parts.length; i++) {
    accumulated += (i > 0 ? '/' : '') + parts[i];
    bc += ' <span>/</span> ';
    if (i === parts.length - 1) {
      bc += '<span style="color:var(--text-primary);font-weight:600">' + esc(parts[i]) + '</span>';
    } else {
      bc += '<a>' + esc(parts[i]) + '</a>';
    }
  }
  document.getElementById('breadcrumb').innerHTML = bc;

  // 파일 내용 로드
  const body = document.getElementById('contentBody');
  body.innerHTML = '<div style="padding:40px;text-align:center;color:var(--text-muted)"><i class="fas fa-spinner fa-spin" style="font-size:24px"></i><p style="margin-top:12px">Loading...</p></div>';

  try {
    const res = await fetch('/viewer-api/file?path=' + encodeURIComponent(filePath));
    const data = await res.json();

    if (data.error) {
      body.innerHTML = '<div class="binary-info"><i class="fas fa-exclamation-triangle" style="color:var(--red)"></i><h3>Error</h3><p>' + esc(data.error) + '</p></div>';
      return;
    }

    // 파일 정보
    const info = document.getElementById('fileInfo');
    info.innerHTML = '';
    if (data.size != null) {
      info.innerHTML += '<span><i class="fas fa-weight-hanging"></i>' + formatSize(data.size) + '</span>';
    }
    if (data.lines != null) {
      info.innerHTML += '<span><i class="fas fa-list-ol"></i>' + data.lines + ' lines</span>';
    }
    if (data.language) {
      info.innerHTML += '<span><i class="fas fa-code"></i>' + data.language + '</span>';
    }

    if (data.binary && data.image) {
      // 이미지 미리보기
      body.innerHTML = '<div class="image-preview"><img src="' + data.imageData + '" alt="' + esc(data.name) + '"><div class="img-label"><i class="fas fa-image"></i> ' + esc(data.name) + ' (' + formatSize(data.size) + ')</div></div>';
    } else if (data.binary) {
      // 바이너리 파일
      body.innerHTML = '<div class="binary-info"><i class="fas fa-file-archive"></i><h3>' + esc(data.name) + '</h3><p>바이너리 파일 (' + formatSize(data.size) + ')<br>미리보기를 지원하지 않습니다.</p></div>';
    } else if (data.language === 'markdown') {
      // 마크다운 렌더링
      const md = typeof marked !== 'undefined' ? marked.parse(data.content) : data.content;
      body.innerHTML = '<div class="markdown-body">' + md + '</div>';
      // 마크다운 내 코드 블록 하이라이팅
      body.querySelectorAll('pre code').forEach(block => hljs.highlightElement(block));
    } else {
      // 소스코드
      const lines = data.content.split('\\n');
      const lineNums = lines.map((_, i) => '<span>' + (i + 1) + '</span>').join('');

      let highlighted;
      if (data.language && data.language !== 'plaintext') {
        try {
          highlighted = hljs.highlight(data.content, { language: data.language }).value;
        } catch(e) {
          highlighted = esc(data.content);
        }
      } else {
        highlighted = esc(data.content);
      }

      body.innerHTML =
        '<div class="code-wrapper">' +
          '<div class="line-numbers">' + lineNums + '</div>' +
          '<div class="code-content"><pre><code>' + highlighted + '</code></pre></div>' +
        '</div>';
    }
  } catch(e) {
    body.innerHTML = '<div class="binary-info"><i class="fas fa-exclamation-triangle" style="color:var(--red)"></i><h3>Error</h3><p>' + e.message + '</p></div>';
  }
}

// ── Welcome ──
function showWelcome() {
  currentPath = null;
  location.hash = '';
  document.querySelectorAll('.tree-item.active').forEach(el => el.classList.remove('active'));
  document.getElementById('breadcrumb').innerHTML = '<a onclick="showWelcome()"><i class="fas fa-home"></i></a>';
  document.getElementById('fileInfo').innerHTML = '';

  const modules = [
    { name: 'core', icon: 'fa-shield-halved', desc: '인증, 보안, 메뉴, 권한', color: '#58a6ff', path: 'core' },
    { name: 'module-common', icon: 'fa-users', desc: '부서, 프로필, 공통코드', color: '#3fb950', path: 'module-common' },
    { name: 'module-fire', icon: 'fa-fire', desc: '소방시설 관리', color: '#f85149', path: 'module-fire' },
    { name: 'module-ps-insp', icon: 'fa-clipboard-check', desc: 'PS 점검', color: '#d29922', path: 'module-ps-insp' },
    { name: 'app', icon: 'fa-rocket', desc: '실행 모듈', color: '#bc8cff', path: 'app' },
    { name: 'api', icon: 'fa-book', desc: 'API 문서', color: '#79c0ff', path: 'api' },
    { name: 'sql', icon: 'fa-database', desc: 'DB 스키마/마이그레이션', color: '#f0883e', path: 'sql' },
    { name: 'docs', icon: 'fa-file-lines', desc: '배포/운영 가이드', color: '#8b949e', path: 'docs' },
  ];

  let cards = modules.map(m =>
    '<div class="module-card" onclick="expandModule(\\'' + m.path + '\\')" style="border-left:3px solid ' + m.color + '">' +
      '<h4><i class="fas ' + m.icon + '" style="color:' + m.color + ';margin-right:6px"></i>' + m.name + '</h4>' +
      '<p>' + m.desc + '</p>' +
    '</div>'
  ).join('');

  document.getElementById('contentBody').innerHTML =
    '<div class="welcome">' +
      '<i class="fas fa-code-branch big-icon"></i>' +
      '<h2>${PROJECT_NAME} Source Code</h2>' +
      '<p>Spring Boot 멀티모듈 프로젝트의 전체 소스코드를 탐색할 수 있습니다.<br>좌측 트리에서 파일을 선택하거나, 아래 모듈을 클릭하세요.</p>' +
      '<div class="module-grid">' + cards + '</div>' +
    '</div>';
}

function expandModule(modulePath) {
  // 해당 모듈의 트리 아이템을 찾아서 펼치기
  const treeItems = document.querySelectorAll('.tree-item');
  for (const item of treeItems) {
    const nameSpan = item.querySelector('.name');
    if (nameSpan && nameSpan.textContent === modulePath) {
      const childDiv = item.nextElementSibling;
      if (childDiv && childDiv.classList.contains('tree-children') && !childDiv.classList.contains('open')) {
        item.click();
      }
      item.scrollIntoView({ block: 'center' });
      break;
    }
  }
}

// ── Search ──
let searchTimeout;
document.addEventListener('DOMContentLoaded', () => {
  const input = document.getElementById('searchInput');
  const results = document.getElementById('searchResults');

  input.addEventListener('input', () => {
    clearTimeout(searchTimeout);
    const q = input.value.trim();
    if (!q) { results.classList.remove('show'); return; }
    searchTimeout = setTimeout(async () => {
      const res = await fetch('/viewer-api/search?q=' + encodeURIComponent(q));
      const data = await res.json();
      if (data.results.length === 0) {
        results.innerHTML = '<div style="padding:16px;text-align:center;color:var(--text-muted)">검색 결과가 없습니다</div>';
      } else {
        results.innerHTML = data.results.map(r =>
          '<div class="sr-item" onclick="openFile(\\'' + r.path.replace(/'/g, "\\\\'") + '\\');document.getElementById(\\'searchResults\\').classList.remove(\\'show\\');document.getElementById(\\'searchInput\\').value=\\'\\';">' +
            '<i class="' + getFileIcon(r.name, r.ext) + '" style="color:var(--text-muted)"></i>' +
            '<span class="sr-name">' + esc(r.name) + '</span>' +
            '<span class="sr-path">' + esc(r.path) + '</span>' +
          '</div>'
        ).join('');
      }
      results.classList.add('show');
    }, 200);
  });

  input.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') { results.classList.remove('show'); input.blur(); }
  });

  document.addEventListener('click', (e) => {
    if (!e.target.closest('.search-box')) results.classList.remove('show');
  });
});

// ── Resize Handle ──
document.addEventListener('DOMContentLoaded', () => {
  const handle = document.getElementById('resizeHandle');
  const sidebar = document.getElementById('sidebar');
  let isResizing = false;

  handle.addEventListener('mousedown', (e) => {
    isResizing = true;
    handle.classList.add('active');
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';
  });

  document.addEventListener('mousemove', (e) => {
    if (!isResizing) return;
    const newWidth = Math.max(200, Math.min(600, e.clientX));
    sidebar.style.width = newWidth + 'px';
    sidebar.style.minWidth = newWidth + 'px';
  });

  document.addEventListener('mouseup', () => {
    if (isResizing) {
      isResizing = false;
      handle.classList.remove('active');
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    }
  });
});

// ── Utils ──
function esc(s) {
  if (!s) return '';
  return s.replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

function formatSize(bytes) {
  if (bytes < 1024) return bytes + ' B';
  if (bytes < 1024*1024) return (bytes/1024).toFixed(1) + ' KB';
  return (bytes/1024/1024).toFixed(1) + ' MB';
}

// ── Keyboard shortcuts ──
document.addEventListener('keydown', (e) => {
  // Ctrl+K or Cmd+K → focus search
  if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
    e.preventDefault();
    document.getElementById('searchInput').focus();
  }
});

init();
</script>
</body>
</html>`;
}


// ══════════════════════════════════════
//  HTTP Server
// ══════════════════════════════════════
const server = http.createServer((req, res) => {
  const parsedUrl = url.parse(req.url, true);
  const pathname = parsedUrl.pathname;

  // CORS
  res.setHeader('Access-Control-Allow-Origin', '*');

  // ── API: 파일 트리 ──
  if (pathname === '/viewer-api/tree') {
    const tree = buildTree(PROJECT_ROOT);
    const stats = getProjectStats(tree);
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    return res.end(JSON.stringify({ tree, stats }));
  }

  // ── API: 파일 내용 ──
  if (pathname === '/viewer-api/file') {
    const filePath = parsedUrl.query.path;
    if (!filePath) {
      res.writeHead(400, { 'Content-Type': 'application/json' });
      return res.end(JSON.stringify({ error: 'path parameter required' }));
    }
    const result = readFileContent(filePath);
    result.language = detectLanguage(filePath);
    res.writeHead(result.status || 200, { 'Content-Type': 'application/json; charset=utf-8' });
    return res.end(JSON.stringify(result));
  }

  // ── API: 검색 ──
  if (pathname === '/viewer-api/search') {
    const query = parsedUrl.query.q || '';
    const tree = buildTree(PROJECT_ROOT);
    const results = searchFiles(tree, query);
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    return res.end(JSON.stringify({ results }));
  }

  // ── 메인 뷰어 페이지 ──
  if (pathname === '/' || pathname === '/viewer' || pathname === '/viewer/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
    return res.end(getViewerHTML());
  }

  // ── 404 ──
  res.writeHead(404, { 'Content-Type': 'text/html; charset=utf-8' });
  res.end('<h1>404 Not Found</h1><p><a href="/">Go to Code Viewer</a></p>');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log('');
  console.log('╔═══════════════════════════════════════════════════════════╗');
  console.log('║        Platform Source Code Viewer Started               ║');
  console.log('╠═══════════════════════════════════════════════════════════╣');
  console.log('║  URL: http://0.0.0.0:' + PORT + '/                             ║');
  console.log('║                                                           ║');
  console.log('║  Features:                                                ║');
  console.log('║    - 파일 트리 탐색 (GitHub 스타일)                      ║');
  console.log('║    - 소스코드 하이라이팅 (Java, JS, SQL, YAML 등)       ║');
  console.log('║    - 마크다운 렌더링                                     ║');
  console.log('║    - 이미지 미리보기                                     ║');
  console.log('║    - 파일 검색 (Ctrl+K)                                  ║');
  console.log('║    - 사이드바 리사이즈                                   ║');
  console.log('╚═══════════════════════════════════════════════════════════╝');
  console.log('');
});
