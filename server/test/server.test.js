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
  const body = JSON.stringify({ name: 'Bad', columns: [{ rows: [{ widgets: ['MAP', 'TOASTER'] }, { widgets: ['MAP'] }] }] });
  const res = await fetch(`${base}/api/layouts/bad`, { method: 'PUT', body, headers: auth });
  assert.strictEqual(res.status, 400);
  const err = await res.json();
  assert.ok(err.errors.some((e) => e.includes('TOASTER')));
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
