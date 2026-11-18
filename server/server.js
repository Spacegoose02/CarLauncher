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

const zlib = require('node:zlib');

const FORMAT_VERSION = 1;
const BUNDLE_FORMAT = 'carlauncher-bundle';
const SHARE_PREFIX = 'CL1.';
const MAX_WAIT_S = 60;

/**
 * The dashboard modules the editor offers. The names match com.spacegoose.carlauncher.layout.WidgetType in
 * the app. A new module needs only an entry here: validation accepts any UPPER_SNAKE name, so a layout made
 * for a newer launcher still saves, and the editor shows these labels. `maxPerLayout` caps how many a layout
 * may hold. A `modules.json` in the data folder (same shape) adds to or relabels these without a rebuild.
 */
const BUILTIN_MODULES = [
  { type: 'CLOCK', label: 'Clock', description: 'Time and date' },
  { type: 'SPEEDOMETER', label: 'Speed', description: 'GPS speed in mph or km/h' },
  { type: 'MEDIA', label: 'Media', description: 'Now playing, with controls' },
  { type: 'WAVELENGTH_WIDGET', label: 'Wavelength widget', description: "Wavelength's own home-screen widget" },
  { type: 'MAP', label: 'Map', description: 'Waze, Google Maps or the built-in map', maxPerLayout: 1 },
  { type: 'WEATHER', label: 'Weather', description: 'Current conditions, rain soon and alerts' },
  { type: 'SPORTS', label: 'Sports scores', description: 'Live, next and last games for your teams' },
];
const MODULE_RE = /^[A-Z][A-Z0-9_]{1,39}$/;
const THEME_COLORS = ['accent', 'onAccent', 'background', 'surface', 'surfaceVariant', 'text', 'textSecondary'];
const ID_RE = /^[a-z0-9][a-z0-9_-]{0,63}$/;
const HEX_RE = /^#[0-9a-fA-F]{6}$/;
const KINDS = { layouts: validateLayout, themes: validateTheme };

// ---- modules ----------------------------------------------------------------------------------------

let extraModules = { mtime: -1, list: [] };

/** The built-in catalog plus the data folder's modules.json, if any (re-read when it changes). */
function modules() {
  const f = path.join(DATA_DIR, 'modules.json');
  let mtime = 0;
  try { mtime = fs.statSync(f).mtimeMs; } catch {}
  if (mtime !== extraModules.mtime) {
    let list = [];
    if (mtime) {
      try {
        const raw = JSON.parse(fs.readFileSync(f, 'utf8'));
        list = (Array.isArray(raw) ? raw : []).filter((m) => m && typeof m.type === 'string' && MODULE_RE.test(m.type))
          .map((m) => ({
            type: m.type,
            label: typeof m.label === 'string' && m.label.trim() ? m.label.trim().slice(0, 40) : m.type,
            ...(typeof m.description === 'string' ? { description: m.description.slice(0, 120) } : {}),
            ...(Number.isInteger(m.maxPerLayout) && m.maxPerLayout > 0 ? { maxPerLayout: m.maxPerLayout } : {}),
          }));
      } catch (e) {
        console.warn(`ignoring modules.json: ${e.message}`);
      }
    }
    extraModules = { mtime, list };
  }
  const byType = new Map(BUILTIN_MODULES.map((m) => [m.type, m]));
  for (const m of extraModules.list) byType.set(m.type, { ...byType.get(m.type), ...m });
  return [...byType.values()];
}

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
        if (typeof w !== 'string' || !MODULE_RE.test(w)) errors.push(`${rw}: "${w}" is not a module name (like CLOCK or MAP)`);
      });
      rows.push({ widgets: r && Array.isArray(r.widgets) ? r.widgets.slice() : [], weight: rweight });
    });
    columns.push({ rows, weight });
  });
  const used = (Array.isArray(o.columns) ? o.columns : []).flatMap((c) => (c && Array.isArray(c.rows) ? c.rows : []))
    .flatMap((r) => (r && Array.isArray(r.widgets) ? r.widgets : []));
  for (const m of modules()) {
    const n = used.filter((w) => w === m.type).length;
    if (m.maxPerLayout && n > m.maxPerLayout) {
      errors.push(m.maxPerLayout === 1 ? `only one ${m.label.toLowerCase()} per layout` : `at most ${m.maxPerLayout} × ${m.label} per layout`);
    }
  }
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

/**
 * A fingerprint of everything the launcher downloads. It changes whenever a file is saved, deleted or
 * edited by hand, so the launcher can ask "anything new since X?" and the server can answer cheaply.
 */
function revision() {
  const h = crypto.createHash('sha1');
  for (const kind of Object.keys(KINDS)) {
    let files = [];
    try { files = fs.readdirSync(dir(kind)).filter((f) => f.endsWith('.json')).sort(); } catch {}
    for (const f of files) {
      h.update(`${kind}/${f}\0`);
      try { h.update(fs.readFileSync(path.join(dir(kind), f))); } catch {}
      h.update('\0');
    }
  }
  return h.digest('hex').slice(0, 16);
}

// Launchers waiting for a change (long polling). Each entry is called with the new revision.
const waiters = new Set();
let lastSeen = { at: null, rev: null };
let pollTimer = null;

/** Wakes waiting launchers if the files changed. Saves call it directly; a timer catches hand edits. */
function notifyIfChanged() {
  if (!waiters.size) return;
  const rev = revision();
  for (const w of [...waiters]) w(rev);
}

function addWaiter(fn) {
  waiters.add(fn);
  if (!pollTimer) pollTimer = setInterval(notifyIfChanged, 3000).unref();
}

function removeWaiter(fn) {
  waiters.delete(fn);
  if (!waiters.size && pollTimer) { clearInterval(pollTimer); pollTimer = null; }
}

function writeOne(kind, id, obj) {
  const f = path.join(dir(kind), `${id}.json`);
  const tmp = `${f}.${crypto.randomBytes(4).toString('hex')}.tmp`;
  fs.writeFileSync(tmp, JSON.stringify(obj, null, 2) + '\n');
  fs.renameSync(tmp, f);
  setImmediate(notifyIfChanged);
}

function removeOne(kind, id) {
  const f = path.join(dir(kind), `${id}.json`);
  if (!fs.existsSync(f)) return false;
  fs.unlinkSync(f);
  setImmediate(notifyIfChanged);
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

// ---- sharing ----------------------------------------------------------------------------------------

const slug = (s) => String(s || '').toLowerCase().replace(/[^a-z0-9]+/g, '_').replace(/^_+|_+$/g, '').slice(0, 64) || 'untitled';

/** Layouts and themes in one file, for downloading, backing up and sharing. */
function bundle(pick) {
  const choose = (kind) => {
    const all = readAll(kind);
    if (!pick || (!pick.layouts && !pick.themes)) return all;
    const ids = pick[kind];
    return ids ? all.filter((x) => ids.includes(x.id)) : [];
  };
  return { format: BUNDLE_FORMAT, formatVersion: FORMAT_VERSION, exportedAt: new Date().toISOString(), layouts: choose('layouts'), themes: choose('themes') };
}

/** A share code is the bundle, compressed, as one line of text: easy to paste into a chat or another server. */
function encodeShare(b) {
  const compact = { format: BUNDLE_FORMAT, formatVersion: b.formatVersion, layouts: b.layouts, themes: b.themes };
  return SHARE_PREFIX + zlib.deflateRawSync(Buffer.from(JSON.stringify(compact))).toString('base64url');
}

function decodeShare(code) {
  const body = String(code).trim().replace(/\s+/g, '');
  if (!body.startsWith(SHARE_PREFIX)) throw new ValidationError(['that is not a Car Launcher share code (they start with CL1.)']);
  try {
    const raw = zlib.inflateRawSync(Buffer.from(body.slice(SHARE_PREFIX.length), 'base64url'), { maxOutputLength: 1024 * 1024 });
    return JSON.parse(raw.toString('utf8'));
  } catch {
    throw new ValidationError(['that share code is damaged or incomplete; copy it again']);
  }
}

/** Sorts whatever was pasted or uploaded into layouts and themes: a bundle, a share code, one item, or a list. */
function importItems(body) {
  if (body && typeof body === 'object' && !Array.isArray(body) && typeof body.code === 'string') body = decodeShare(body.code);
  else if (typeof body === 'string') body = decodeShare(body);
  const out = [];
  const add = (kind, o) => out.push({ kind, o });
  const classify = (o) => {
    if (o && typeof o === 'object' && Array.isArray(o.columns)) add('layouts', o);
    else if (o && typeof o === 'object' && o.colors && typeof o.colors === 'object') add('themes', o);
    else add(null, o);
  };
  if (Array.isArray(body)) body.forEach(classify);
  else if (body && typeof body === 'object' && (Array.isArray(body.layouts) || Array.isArray(body.themes))) {
    (body.layouts || []).forEach((o) => add('layouts', o));
    (body.themes || []).forEach((o) => add('themes', o));
  } else classify(body);
  if (!out.length) throw new ValidationError(['nothing to import']);
  if (out.length > 200) throw new ValidationError(['at most 200 layouts and themes in one import']);
  return out;
}

const sameContent = (a, b) => JSON.stringify({ ...a, id: '' }) === JSON.stringify({ ...b, id: '' });

/**
 * Saves imported items. `conflict` says what to do when the id is taken by something different:
 * "copy" (default) saves it under a new id, "replace" overwrites, "skip" leaves the existing one.
 * Something identical to what is already there is never duplicated.
 */
function runImport(body, conflict) {
  const report = { added: [], replaced: [], unchanged: [], skipped: [], failed: [] };
  for (const { kind, o } of importItems(body)) {
    const label = (o && typeof o.name === 'string' && o.name) || (o && o.id) || 'item';
    if (!kind) { report.failed.push({ name: String(label), errors: ['not a layout or a theme'] }); continue; }
    let id = o && typeof o.id === 'string' && ID_RE.test(o.id) ? o.id : slug(o && o.name);
    let clean;
    try { clean = KINDS[kind](o, id); } catch (e) {
      if (!(e instanceof ValidationError)) throw e;
      report.failed.push({ kind, name: String(label), errors: e.errors });
      continue;
    }
    const existing = readOne(kind, id);
    const entry = { kind, id, name: clean.name };
    if (existing && sameContent(existing, clean)) { report.unchanged.push(entry); continue; }
    if (existing && conflict === 'skip') { report.skipped.push(entry); continue; }
    if (existing && conflict !== 'replace') {
      const base = id.replace(/_\d+$/, '').slice(0, 60);
      let n = 2;
      while (readOne(kind, `${base}_${n}`)) n++;
      id = `${base}_${n}`;
      clean = { ...clean, id };
      entry.id = id;
      entry.renamedFrom = o.id;
    }
    writeOne(kind, id, clean);
    (existing && conflict === 'replace' ? report.replaced : report.added).push(entry);
  }
  return report;
}

function idList(v) {
  if (v === null) return undefined;
  return v.split(',').map((x) => x.trim()).filter((x) => ID_RE.test(x));
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

function readBody(req, limit = 256 * 1024) {
  return new Promise((resolve, reject) => {
    let size = 0;
    const chunks = [];
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) {
        reject(new ValidationError(['body is too large']));
        req.destroy();
      } else chunks.push(c);
    });
    req.on('end', () => {
      const text = Buffer.concat(chunks).toString('utf8');
      // A share code pasted as the whole body is fine too.
      if (text.trim().startsWith(SHARE_PREFIX)) return resolve(text.trim());
      try {
        resolve(JSON.parse(text || 'null'));
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
      'Access-Control-Allow-Methods': 'GET, PUT, POST, DELETE, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization, If-None-Match',
    });
  }
  if (p === '/healthz') return send(res, 200, { ok: true });

  // Everything the launcher needs, in one request. With ?wait=N and the revision it already has (in
  // If-None-Match or ?since=), the request waits up to N seconds for a change, so edits reach the car
  // within a second or two without it polling hard. Unchanged: 304 with no body.
  if (p === '/api/config' && req.method === 'GET') {
    const since = (url.searchParams.get('since') || req.headers['if-none-match'] || '').replace(/^W\//, '').replace(/"/g, '').trim();
    const wait = Math.min(Math.max(Number(url.searchParams.get('wait')) || 0, 0), MAX_WAIT_S);
    let rev = revision();
    if (since && since === rev && wait > 0) {
      rev = await new Promise((resolve) => {
        let timer;
        const done = (r) => { clearTimeout(timer); removeWaiter(onChange); resolve(r); };
        const onChange = (r) => { if (r !== since) done(r); };
        timer = setTimeout(() => done(since), wait * 1000);
        res.on('close', () => { if (!res.writableEnded) done(null); });
        addWaiter(onChange);
      });
      if (rev === null) return undefined; // the launcher hung up
    }
    lastSeen = { at: new Date().toISOString(), rev };
    const etag = { ETag: `"${rev}"` };
    if (since && since === rev) {
      res.writeHead(304, { ...etag, 'Cache-Control': 'no-store', 'Access-Control-Allow-Origin': '*' });
      return res.end();
    }
    return send(res, 200, { formatVersion: FORMAT_VERSION, revision: rev, layouts: readAll('layouts'), themes: readAll('themes') }, etag);
  }
  if (p === '/api/meta' && req.method === 'GET') {
    const mods = modules();
    return send(res, 200, {
      formatVersion: FORMAT_VERSION, modules: mods, widgetTypes: mods.map((m) => m.type), themeColors: THEME_COLORS, auth: !!ADMIN_TOKEN,
    });
  }
  // For the editor: is a car connected and listening for changes right now?
  if (p === '/api/status' && req.method === 'GET') {
    return send(res, 200, { revision: revision(), listening: waiters.size, lastSync: lastSeen.at, lastSyncRevision: lastSeen.rev });
  }

  if (p === '/api/export' && req.method === 'GET') {
    const b = bundle({ layouts: idList(url.searchParams.get('layouts')), themes: idList(url.searchParams.get('themes')) });
    const headers = {};
    if (url.searchParams.get('download') !== null) {
      const one = b.layouts.length + b.themes.length === 1 ? (b.layouts[0] || b.themes[0]).id : `carlauncher-${b.exportedAt.slice(0, 10)}`;
      headers['Content-Disposition'] = `attachment; filename="${one}.json"`;
    }
    return send(res, 200, b, headers);
  }
  if (p === '/api/share' && req.method === 'GET') {
    const b = bundle({ layouts: idList(url.searchParams.get('layouts')), themes: idList(url.searchParams.get('themes')) });
    if (!b.layouts.length && !b.themes.length) return send(res, 404, { error: 'nothing to share' });
    return send(res, 200, { code: encodeShare(b), layouts: b.layouts.length, themes: b.themes.length });
  }
  if (p === '/api/share/decode' && req.method === 'POST') {
    const items = importItems(await readBody(req, 1024 * 1024));
    return send(res, 200, {
      layouts: items.filter((x) => x.kind === 'layouts').map((x) => x.o),
      themes: items.filter((x) => x.kind === 'themes').map((x) => x.o),
    });
  }
  if (p === '/api/import' && req.method === 'POST') {
    if (!authorized(req)) return send(res, 401, { error: 'admin token required' });
    const conflict = url.searchParams.get('conflict') || 'copy';
    if (!['copy', 'replace', 'skip'].includes(conflict)) return send(res, 400, { error: 'conflict must be copy, replace or skip' });
    return send(res, 200, runImport(await readBody(req, 1024 * 1024), conflict));
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

/** Started as root (as in Docker): give the data folder to PUID:PGID and carry on as that user. */
function dropPrivileges() {
  if (typeof process.getuid !== 'function' || process.getuid() !== 0 || !process.env.PUID) return;
  const uid = Number(process.env.PUID);
  const gid = Number(process.env.PGID || process.env.PUID);
  if (!Number.isInteger(uid) || !Number.isInteger(gid) || uid === 0) return;
  fs.mkdirSync(DATA_DIR, { recursive: true });
  const chown = (p) => {
    fs.chownSync(p, uid, gid);
    if (fs.statSync(p).isDirectory()) for (const f of fs.readdirSync(p)) chown(path.join(p, f));
  };
  chown(DATA_DIR);
  process.setgroups([gid]);
  process.setgid(gid);
  process.setuid(uid);
}

if (require.main === module) {
  dropPrivileges();
  initData();
  createServer().listen(PORT, () => {
    console.log(`Car Launcher config server on port ${PORT}, data in ${DATA_DIR}${ADMIN_TOKEN ? ', editing needs the admin token' : ''}`);
  });
}

module.exports = {
  createServer, initData, validateLayout, validateTheme, ValidationError, modules, revision, encodeShare, decodeShare, BUILTIN_MODULES,
};
