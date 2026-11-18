// Car Launcher config server: stores dashboard layouts and themes as JSON files and serves a web editor.
// No dependencies, so the Docker image is just Node. See README.md.
'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const PORT = Number(process.env.PORT || 8080);
const DATA_DIR = path.resolve(process.env.DATA_DIR || path.join(__dirname, 'data'));
const ADMIN_TOKEN = process.env.ADMIN_TOKEN || '';
const PUBLIC_DIR = path.join(__dirname, 'public');
const SEED_DIR = path.join(__dirname, 'seed');

const FORMAT_VERSION = 1;
// Must match com.spacegoose.carlauncher.layout.WidgetType in the app.
const WIDGET_TYPES = ['CLOCK', 'SPEEDOMETER', 'MEDIA', 'WAVELENGTH_WIDGET', 'MAP'];
const THEME_COLORS = ['accent', 'onAccent', 'background', 'surface', 'surfaceVariant', 'text', 'textSecondary'];
const ID_RE = /^[a-z0-9][a-z0-9_-]{0,63}$/;
const HEX_RE = /^#[0-9a-fA-F]{6}$/;
const KINDS = { layouts: validateLayout, themes: validateTheme };

// ---- validation -------------------------------------------------------------------------------------

function isWeight(w) {
  return typeof w === 'number' && Number.isFinite(w) && w > 0 && w <= 100;
}

function checkName(o, errors) {
  if (typeof o.name !== 'string' || !o.name.trim()) errors.push('name is required');
  else if (o.name.length > 80) errors.push('name is too long (80 characters at most)');
}

/** Returns a cleaned copy of the layout, or throws with every problem found. */
function validateLayout(o, id) {
  const errors = [];
  if (!o || typeof o !== 'object' || Array.isArray(o)) throw new ValidationError(['body must be a JSON object']);
  checkName(o, errors);
  const columns = [];
  if (!Array.isArray(o.columns) || o.columns.length === 0) errors.push('columns must be a non-empty list');
  else if (o.columns.length > 6) errors.push('at most 6 columns');
  else o.columns.forEach((c, ci) => {
    const where = `column ${ci + 1}`;
    const weight = c && c.weight === undefined ? 1 : c && c.weight;
    if (!isWeight(weight)) errors.push(`${where}: weight must be a number above 0`);
    const rows = [];
    if (!c || !Array.isArray(c.rows) || c.rows.length === 0) errors.push(`${where}: rows must be a non-empty list`);
    else if (c.rows.length > 8) errors.push(`${where}: at most 8 rows`);
    else c.rows.forEach((r, ri) => {
      const rw = `${where}, row ${ri + 1}`;
      const rweight = r && r.weight === undefined ? 1 : r && r.weight;
      if (!isWeight(rweight)) errors.push(`${rw}: weight must be a number above 0`);
      if (!r || !Array.isArray(r.widgets) || r.widgets.length === 0) errors.push(`${rw}: needs at least one module`);
      else if (r.widgets.length > 4) errors.push(`${rw}: at most 4 modules`);
      else r.widgets.forEach((w) => {
        if (!WIDGET_TYPES.includes(w)) errors.push(`${rw}: unknown module "${w}"`);
      });
      rows.push({ widgets: r && Array.isArray(r.widgets) ? r.widgets.slice() : [], weight: rweight });
    });
    columns.push({ rows, weight });
  });
  const maps = (o.columns || []).flatMap((c) => (c && c.rows) || []).flatMap((r) => (r && r.widgets) || [])
    .filter((w) => w === 'MAP').length;
  if (maps > 1) errors.push('only one map per layout');
  if (errors.length) throw new ValidationError(errors);
  return { id, name: o.name.trim(), columns };
}

function validateTheme(o, id) {
  const errors = [];
  if (!o || typeof o !== 'object' || Array.isArray(o)) throw new ValidationError(['body must be a JSON object']);
  checkName(o, errors);
  if (typeof o.dark !== 'boolean') errors.push('dark must be true or false');
  const colors = {};
  if (!o.colors || typeof o.colors !== 'object') errors.push('colors is required');
  else THEME_COLORS.forEach((k) => {
    const v = o.colors[k];
    if (typeof v !== 'string' || !HEX_RE.test(v)) errors.push(`colors.${k} must be a colour like #1A2B3C`);
    else colors[k] = v.toUpperCase();
  });
  if (errors.length) throw new ValidationError(errors);
  return { id, name: o.name.trim(), dark: o.dark, colors };
}

class ValidationError extends Error {
  constructor(errors) {
    super(errors.join('; '));
    this.errors = errors;
  }
}

// ---- storage ----------------------------------------------------------------------------------------

function dir(kind) {
  return path.join(DATA_DIR, kind);
}

function readAll(kind) {
  const d = dir(kind);
  return fs.readdirSync(d)
    .filter((f) => f.endsWith('.json'))
    .map((f) => {
      try {
        return JSON.parse(fs.readFileSync(path.join(d, f), 'utf8'));
      } catch (e) {
        console.warn(`skipping ${kind}/${f}: ${e.message}`);
        return null;
      }
    })
    .filter(Boolean)
    .sort((a, b) => String(a.name).localeCompare(String(b.name)));
}

function readOne(kind, id) {
  const f = path.join(dir(kind), `${id}.json`);
  return fs.existsSync(f) ? JSON.parse(fs.readFileSync(f, 'utf8')) : null;
}

function writeOne(kind, id, obj) {
  const f = path.join(dir(kind), `${id}.json`);
  const tmp = `${f}.${crypto.randomBytes(4).toString('hex')}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n');
  fs.renameSync(tmp, f);
}

function removeOne(kind, id) {
  const f = path.join(dir(kind), `${id}.json`);
  if (!fs.existsSync(f)) return false;
  fs.unlinkSync(f);
  return true;
}

/** First start: create the data folders and copy in the launcher's built-in layouts and a few themes. */
function initData() {
  for (const kind of Object.keys(KINDS)) {
    const d = dir(kind);
    const fresh = !fs.existsSync(d);
    fs.mkdirSync(d, { recursive: true });
    if (!fresh) continue;
    const seed = path.join(SEED_DIR, kind);
    if (!fs.existsSync(seed)) continue;
    for (const f of fs.readdirSync(seed)) fs.copyFileSync(path.join(seed, f), path.join(d, f));
  }
}

// ---- http -------------------------------------------------------------------------------------------

function send(res, status, body, headers = {}) {
  const json = typeof body !== 'string';
  const data = json ? JSON.stringify(body, null, 2) : body;
  res.writeHead(status, {
    'Content-Type': json ? 'application/json; charset=utf-8' : 'text/plain; charset=utf-8',
    'Cache-Control': 'no-store',
    'Access-Control-Allow-Origin': '*',
    ...headers,
  });
  res.end(data);
}

function authorized(req) {
  if (!ADMIN_TOKEN) return true;
  const h = req.headers.authorization || '';
  const given = Buffer.from(h.startsWith('Bearer ') ? h.slice(7) : '');
  const want = Buffer.from(ADMIN_TOKEN);
  return given.length === want.length && crypto.timingSafeEqual(given, want);
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > 256 * 1024) {
        reject(new ValidationError(['body is too large']));
        req.destroy();
      } else chunks.push(c);
    });
    req.on('end', () => {
      try {
        resolve(JSON.parse(Buffer.concat(chunks).toString('utf8') || 'null'));
      } catch {
        reject(new ValidationError(['body is not valid JSON']));
      }
    });
    req.on('error', reject);
  });
}

const MIME = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.svg': 'image/svg+xml' };

function serveStatic(req, res, pathname) {
  const rel = pathname === '/' ? 'index.html' : pathname.slice(1);
  const f = path.join(PUBLIC_DIR, path.normalize(rel));
  if (!f.startsWith(PUBLIC_DIR + path.sep) || !fs.existsSync(f) || !fs.statSync(f).isFile()) {
    return send(res, 404, { error: 'not found' });
  }
  res.writeHead(200, { 'Content-Type': MIME[path.extname(f)] || 'application/octet-stream', 'Cache-Control': 'no-cache' });
  fs.createReadStream(f).pipe(res);
}

async function handle(req, res) {
  const url = new URL(req.url, 'http://localhost');
  const p = url.pathname.replace(/\/+$/, '') || '/';

  if (req.method === 'OPTIONS') {
    return send(res, 204, '', {
      'Access-Control-Allow-Methods': 'GET, PUT, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    });
  }
  if (p === '/healthz') return send(res, 200, { ok: true });

  // Everything the launcher needs, in one request.
  if (p === '/api/config' && req.method === 'GET') {
    return send(res, 200, { formatVersion: FORMAT_VERSION, layouts: readAll('layouts'), themes: readAll('themes') });
  }
  if (p === '/api/meta' && req.method === 'GET') {
    return send(res, 200, { formatVersion: FORMAT_VERSION, widgetTypes: WIDGET_TYPES, themeColors: THEME_COLORS, auth: !!ADMIN_TOKEN });
  }

  const m = p.match(/^\/api\/(layouts|themes)(?:\/([^/]+))?$/);
  if (m) {
    const [, kind, rawId] = m;
    const id = rawId && decodeURIComponent(rawId);
    if (id !== undefined && !ID_RE.test(id)) {
      return send(res, 400, { error: 'id must be lowercase letters, digits, - or _ (64 at most)' });
    }
    if (req.method === 'GET') {
      if (!id) return send(res, 200, readAll(kind));
      const one = readOne(kind, id);
      return one ? send(res, 200, one) : send(res, 404, { error: 'not found' });
    }
    if (!authorized(req)) return send(res, 401, { error: 'admin token required' });
    if (req.method === 'PUT' && id) {
      const clean = KINDS[kind](await readBody(req), id);
      const existed = !!readOne(kind, id);
      writeOne(kind, id, clean);
      return send(res, existed ? 200 : 201, clean);
    }
    if (req.method === 'DELETE' && id) {
      return removeOne(kind, id) ? send(res, 204, '') : send(res, 404, { error: 'not found' });
    }
    return send(res, 405, { error: 'method not allowed' });
  }

  if (req.method === 'GET' && !p.startsWith('/api/')) return serveStatic(req, res, p);
  return send(res, 404, { error: 'not found' });
}

function createServer() {
  return http.createServer((req, res) => {
    handle(req, res).catch((e) => {
      if (e instanceof ValidationError) return send(res, 400, { error: e.message, errors: e.errors });
      console.error(e);
      send(res, 500, { error: 'server error' });
    });
  });
}

if (require.main === module) {
  initData();
  createServer().listen(PORT, () => {
    console.log(`Car Launcher config server on port ${PORT}, data in ${DATA_DIR}${ADMIN_TOKEN ? ', editing needs the admin token' : ''}`);
  });
}

module.exports = { createServer, initData, validateLayout, validateTheme, ValidationError, WIDGET_TYPES };
