'use strict';
const { test, before, after } = require('node:test');
const assert = require('node:assert');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), 'clcfg-'));
process.env.DATA_DIR = dataDir;
process.env.ADMIN_TOKEN = 'secret';
const { createServer, initData, validateLayout } = require('../server.js');

let server;
let base;
before(async () => {
  initData();
  server = createServer();
  await new Promise((r) => server.listen(0, '127.0.0.1', r));
  base = `http://127.0.0.1:${server.address().port}`;
});
after(() => { server.close(); fs.rmSync(dataDir, { recursive: true, force: true }); });

const auth = { Authorization: 'Bearer secret', 'Content-Type': 'application/json' };

test('first start seeds the built-in layouts and some themes', async () => {
  const cfg = await (await fetch(`${base}/api/config`)).json();
  assert.strictEqual(cfg.formatVersion, 1);
  assert.deepStrictEqual(cfg.layouts.map((l) => l.id).sort(), ['map_left', 'map_right', 'no_map']);
  assert.ok(cfg.themes.length >= 2);
});

test('seeded layouts pass validation', () => {
  for (const f of fs.readdirSync(path.join(dataDir, 'layouts'))) {
    const l = JSON.parse(fs.readFileSync(path.join(dataDir, 'layouts', f), 'utf8'));
    assert.deepStrictEqual(validateLayout(l, l.id), l);
  }
});

test('saving needs the admin token', async () => {
  const body = JSON.stringify({ name: 'X', columns: [{ rows: [{ widgets: ['CLOCK'] }] }] });
  const res = await fetch(`${base}/api/layouts/x`, { method: 'PUT', body, headers: { 'Content-Type': 'application/json' } });
  assert.strictEqual(res.status, 401);
});

test('a layout round-trips and fills in default weights', async () => {
  const body = JSON.stringify({ name: 'Media only', columns: [{ rows: [{ widgets: ['MEDIA'] }] }] });
  const put = await fetch(`${base}/api/layouts/media_only`, { method: 'PUT', body, headers: auth });
  assert.strictEqual(put.status, 201);
  const got = await (await fetch(`${base}/api/layouts/media_only`)).json();
  assert.deepStrictEqual(got, { id: 'media_only', name: 'Media only', columns: [{ rows: [{ widgets: ['MEDIA'], weight: 1 }], weight: 1 }] });
  const del = await fetch(`${base}/api/layouts/media_only`, { method: 'DELETE', headers: auth });
  assert.strictEqual(del.status, 204);
});

test('bad layouts are refused with reasons', async () => {
  const body = JSON.stringify({ name: 'Bad', columns: [{ rows: [{ widgets: ['MAP', 'toaster!'] }, { widgets: ['MAP'] }] }] });
  const res = await fetch(`${base}/api/layouts/bad`, { method: 'PUT', body, headers: auth });
  assert.strictEqual(res.status, 400);
  const err = await res.json();
  assert.ok(err.errors.some((e) => e.includes('toaster!')));
  assert.ok(err.errors.some((e) => e.includes('one map')));
});

test('themes are validated and colours upper-cased', async () => {
  const theme = { name: 'Red', dark: true, colors: { accent: '#ff0000', onAccent: '#000000', background: '#000000', surface: '#111111', surfaceVariant: '#222222', text: '#FFFFFF', textSecondary: '#AAAAAA' } };
  const res = await fetch(`${base}/api/themes/red`, { method: 'PUT', body: JSON.stringify(theme), headers: auth });
  assert.strictEqual(res.status, 201);
  assert.strictEqual((await res.json()).colors.accent, '#FF0000');
  const bad = await fetch(`${base}/api/themes/red`, { method: 'PUT', body: JSON.stringify({ ...theme, colors: { accent: 'red' } }), headers: auth });
  assert.strictEqual(bad.status, 400);
});

test('ids with path tricks are refused', async () => {
  const res = await fetch(`${base}/api/layouts/..%2Fsecret`);
  assert.strictEqual(res.status, 400);
});

test('the editor page is served', async () => {
  const res = await fetch(`${base}/`);
  assert.strictEqual(res.status, 200);
  assert.match(await res.text(), /Car Launcher config/);
});

test('modules come from the catalog, and new module names are accepted', async () => {
  const meta = await (await fetch(`${base}/api/meta`)).json();
  assert.ok(meta.modules.some((m) => m.type === 'WEATHER' && m.label));
  assert.ok(meta.widgetTypes.includes('MAP'));
  const body = JSON.stringify({ name: 'Future', columns: [{ rows: [{ widgets: ['OBD_GAUGE', 'WEATHER', 'WEATHER'] }] }] });
  const put = await fetch(`${base}/api/layouts/future`, { method: 'PUT', body, headers: auth });
  assert.strictEqual(put.status, 201);
  await fetch(`${base}/api/layouts/future`, { method: 'DELETE', headers: auth });
});

test('modules.json in the data folder adds modules and limits', async () => {
  fs.writeFileSync(path.join(dataDir, 'modules.json'), JSON.stringify([{ type: 'RADAR', label: 'Radar', maxPerLayout: 1 }]));
  try {
    const meta = await (await fetch(`${base}/api/meta`)).json();
    assert.ok(meta.modules.some((m) => m.type === 'RADAR' && m.label === 'Radar'));
    const body = JSON.stringify({ name: 'Two radars', columns: [{ rows: [{ widgets: ['RADAR', 'RADAR'] }] }] });
    const res = await fetch(`${base}/api/layouts/radars`, { method: 'PUT', body, headers: auth });
    assert.strictEqual(res.status, 400);
    assert.match((await res.json()).error, /one radar/);
  } finally {
    fs.unlinkSync(path.join(dataDir, 'modules.json'));
  }
});

test('config has a revision, answers 304 when unchanged, and a waiting request wakes on a save', async () => {
  const first = await fetch(`${base}/api/config`);
  const { revision } = await first.json();
  assert.match(revision, /^[0-9a-f]{16}$/);
  assert.strictEqual(first.headers.get('etag'), `"${revision}"`);

  const same = await fetch(`${base}/api/config`, { headers: { 'If-None-Match': `"${revision}"` } });
  assert.strictEqual(same.status, 304);

  const started = Date.now();
  const waiting = fetch(`${base}/api/config?wait=20&since=${revision}`);
  await new Promise((r) => setTimeout(r, 150));
  const status = await (await fetch(`${base}/api/status`)).json();
  assert.strictEqual(status.listening, 1);
  const body = JSON.stringify({ name: 'Pushed', columns: [{ rows: [{ widgets: ['CLOCK'] }] }] });
  await fetch(`${base}/api/layouts/pushed`, { method: 'PUT', body, headers: auth });
  const res = await waiting;
  assert.strictEqual(res.status, 200);
  const cfg = await res.json();
  assert.notStrictEqual(cfg.revision, revision);
  assert.ok(cfg.layouts.some((l) => l.id === 'pushed'));
  assert.ok(Date.now() - started < 5000);
  await fetch(`${base}/api/layouts/pushed`, { method: 'DELETE', headers: auth });
});

test('a waiting request with no change times out with 304', async () => {
  const { revision } = await (await fetch(`${base}/api/config`)).json();
  const res = await fetch(`${base}/api/config?wait=1`, { headers: { 'If-None-Match': `"${revision}"` } });
  assert.strictEqual(res.status, 304);
});

test('hand edits to the data folder also wake a waiting launcher', async () => {
  const { revision } = await (await fetch(`${base}/api/config`)).json();
  const waiting = fetch(`${base}/api/config?wait=20&since=${revision}`);
  await new Promise((r) => setTimeout(r, 100));
  const f = path.join(dataDir, 'themes', 'handmade.json');
  fs.writeFileSync(f, JSON.stringify({ id: 'handmade', name: 'Handmade', dark: true, colors: { accent: '#FF0000', onAccent: '#000000', background: '#000000', surface: '#111111', surfaceVariant: '#222222', text: '#FFFFFF', textSecondary: '#AAAAAA' } }));
  try {
    const res = await waiting;
    assert.strictEqual(res.status, 200);
    assert.ok((await res.json()).themes.some((t) => t.id === 'handmade'));
  } finally {
    fs.unlinkSync(f);
  }
});

test('export, share code and import round-trip', async () => {
  const exp = await fetch(`${base}/api/export?layouts=map_left&themes=midnight&download`);
  assert.match(exp.headers.get('content-disposition'), /attachment/);
  const b = await exp.json();
  assert.strictEqual(b.format, 'carlauncher-bundle');
  assert.deepStrictEqual(b.layouts.map((l) => l.id), ['map_left']);
  assert.deepStrictEqual(b.themes.map((t) => t.id), ['midnight']);

  const all = await (await fetch(`${base}/api/export`)).json();
  assert.ok(all.layouts.length >= 3 && all.themes.length >= 3);

  const { code } = await (await fetch(`${base}/api/share?layouts=map_left&themes=midnight`)).json();
  assert.match(code, /^CL1\./);
  const decoded = await (await fetch(`${base}/api/share/decode`, { method: 'POST', body: code })).json();
  assert.strictEqual(decoded.layouts[0].id, 'map_left');

  // Importing the same things again changes nothing.
  const again = await (await fetch(`${base}/api/import`, { method: 'POST', body: JSON.stringify({ code }), headers: auth })).json();
  assert.strictEqual(again.unchanged.length, 2);
  assert.strictEqual(again.added.length, 0);

  // A different theme under a taken id is saved as a copy by default.
  const changed = { ...b.themes[0], colors: { ...b.themes[0].colors, accent: '#00FF00' } };
  const copy = await (await fetch(`${base}/api/import`, { method: 'POST', body: JSON.stringify(changed), headers: auth })).json();
  assert.strictEqual(copy.added[0].id, 'midnight_2');
  const skip = await (await fetch(`${base}/api/import?conflict=skip`, { method: 'POST', body: JSON.stringify(changed), headers: auth })).json();
  assert.strictEqual(skip.skipped.length, 1);
  await fetch(`${base}/api/themes/midnight_2`, { method: 'DELETE', headers: auth });

  // Bad items are reported, good ones still saved.
  const mixed = [{ name: 'Fresh one', columns: [{ rows: [{ widgets: ['CLOCK'] }] }] }, { name: 'Broken', colors: { accent: 'red' } }, 42];
  const r = await (await fetch(`${base}/api/import`, { method: 'POST', body: JSON.stringify(mixed), headers: auth })).json();
  assert.deepStrictEqual(r.added.map((x) => x.id), ['fresh_one']);
  assert.strictEqual(r.failed.length, 2);
  await fetch(`${base}/api/layouts/fresh_one`, { method: 'DELETE', headers: auth });
});

test('import needs the admin token and rejects junk codes', async () => {
  const noAuth = await fetch(`${base}/api/import`, { method: 'POST', body: '{}' });
  assert.strictEqual(noAuth.status, 401);
  const junk = await fetch(`${base}/api/import`, { method: 'POST', body: 'CL1.notreal', headers: auth });
  assert.strictEqual(junk.status, 400);
});
