// The same checks as worker.test.mjs, run against the pasteable single file, so what is handed
// over is what was tested rather than something that looks like it.
import worker from './worker.single.js';
const kv = new Map();
const env = { SYNC_TOKEN: 'secret', NIGHTS: {
  async get(k, t) { const v = kv.get(k); return v == null ? null : (t === 'json' ? JSON.parse(v) : v); },
  async put(k, v) { kv.set(k, v); } } };
const call = (path, body, token = 'secret') => worker.fetch(new Request('https://x' + path, {
  method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: JSON.stringify(body) }), env)
  .then(r => r.json().then(j => ({ status: r.status, ...j })));
let bad = 0;
const check = (n, c, x = '') => { if (!c) { bad++; console.log('FAIL ' + n + ' ' + x); } };
const night = d => ({ marks: {}, tonight: [], notes: {}, closed: false, ts: {}, ...d });

check('rejects a bad token', (await call('/pull', {}, 'nope')).status === 401);
await call('/push', { nights: { '2026-09-04': night({ marks: { '1115': { p1: 'in' } }, ts: { 'm:1115:p1': 1000 } }) } });
const pushed = await call('/push', { nights: { '2026-09-04': night({ marks: { '1115': { p20: 'in' } }, ts: { 'm:1115:p20': 1001 } }) } });
const got = pushed.nights['2026-09-04'].marks['1115'];
check('both devices\' marks survive', got.p1 === 'in' && got.p20 === 'in', JSON.stringify(got));
const all = await call('/pull', { since: 0 });
check('a fresh device pulls everything', all.nights['2026-09-04'].marks['1115'].p1 === 'in');
check('nothing new after the cursor', Object.keys((await call('/pull', { since: all.now })).nights).length === 0);
console.log(bad ? bad + ' failing' : 'single-file worker: all checks pass');
process.exit(bad ? 1 : 0);
