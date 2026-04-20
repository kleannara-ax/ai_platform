const http = require('http');
const fs = require('fs');
const path = require('path');

const PORT = 8080;
const BACKEND_HOST = '127.0.0.1';
const BACKEND_PORT = 8081;
const STATIC_ROOT = path.join(__dirname, 'module-ps-insp/src/main/resources/static/ps-insp');
const TEMPLATE_ROOT = path.join(__dirname, 'module-ps-insp/src/main/resources/templates/ps-insp');

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js':   'application/javascript; charset=utf-8',
  '.css':  'text/css; charset=utf-8',
  '.json': 'application/json',
  '.png':  'image/png',
  '.jpg':  'image/jpeg',
  '.svg':  'image/svg+xml',
};

/**
 * Proxy a request to the Spring Boot backend (port 8081).
 * Forwards all headers, method, path, query string, and request body.
 */
function proxyToBackend(clientReq, clientRes) {
  const fullUrl = clientReq.url; // includes query string
  const options = {
    hostname: BACKEND_HOST,
    port: BACKEND_PORT,
    path: fullUrl,
    method: clientReq.method,
    headers: Object.assign({}, clientReq.headers, {
      host: `${BACKEND_HOST}:${BACKEND_PORT}`
    }),
  };

  const proxyReq = http.request(options, (proxyRes) => {
    clientRes.writeHead(proxyRes.statusCode, proxyRes.headers);
    proxyRes.pipe(clientRes, { end: true });
  });

  proxyReq.on('error', (err) => {
    console.error('[PROXY] Backend connection error:', err.message);
    clientRes.writeHead(502, { 'Content-Type': 'application/json' });
    clientRes.end(JSON.stringify({
      success: false,
      code: 502,
      message: 'Backend server unavailable (port ' + BACKEND_PORT + '): ' + err.message
    }));
  });

  clientReq.pipe(proxyReq, { end: true });
}

const server = http.createServer((req, res) => {
  const urlPath = req.url.split('?')[0];

  // ── Serve index.html for root URLs ──
  if (urlPath === '/' || urlPath === '/ps-insp' || urlPath === '/ps-insp/') {
    let html = fs.readFileSync(path.join(TEMPLATE_ROOT, 'index.html'), 'utf8');
    html = html.replace(/\s+th:[a-z]+="[^"]*"/g, '');
    res.writeHead(200, {'Content-Type': 'text/html; charset=utf-8'});
    res.end(html);
    return;
  }

  // ── Serve static files ──
  if (urlPath.startsWith('/ps-insp/')) {
    const filePath = path.join(STATIC_ROOT, urlPath.replace('/ps-insp/', ''));
    if (fs.existsSync(filePath)) {
      const ext = path.extname(filePath);
      res.writeHead(200, {'Content-Type': MIME[ext] || 'application/octet-stream'});
      res.end(fs.readFileSync(filePath));
      return;
    }
  }

  // ── Proxy ALL API calls to Spring Boot backend ──
  if (urlPath.startsWith('/ps-insp-api/') || urlPath.startsWith('/api/')) {
    proxyToBackend(req, res);
    return;
  }

  // ── Proxy uploaded image paths ──
  if (urlPath.startsWith('/uploads/')) {
    proxyToBackend(req, res);
    return;
  }

  res.writeHead(404);
  res.end('Not Found');
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Preview server running on http://0.0.0.0:${PORT}`);
  console.log(`API proxy -> http://${BACKEND_HOST}:${BACKEND_PORT}`);
});
