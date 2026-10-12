// Drives the Worker's own handler against an in-memory KV: no Cloudflare account needed to know
// the protocol works and that two devices pushing at once keep both sets of marks.
import worker from './worker.js';

const kv = new Map();
const env = {
  SYNC_TOKEN: 'secret',
  NIGHTS: {
    async get(k, t) { const v = kv.get(k); return v == null ? null : (t === 'json' ? JSON.parse(v) : v); },
    async put(k, v) { kv.set(k, v); },
  },
};
const call = (path, body, token = 'secret') => worker.fetch(new Request('https://x' + path, {
  method: 'POST', headers: { Authorization: 'Bearer ' + token }, body: JSON.stringify(body),
}), env).then(r => r.json().then(j => ({ status: r.status, ...j })));

let bad = 0;
const check = (name, cond, extra = '') => { if (!cond) { bad++; console.log('FAIL ' + name + ' ' + extra); } };

// a wrong token gets nothing
check('rejects a bad token', (await call('/pull', {}, 'nope')).status === 401);

// phone marks Room 1
const night = d => ({ marks: {}, tonight: [], notes: {}, closed: false, ts: {}, ...d });
await call('/push', { nights: { '2026-09-04': night({ marks: { '1115': { p1: 'in', p2: 'out' } }, ts: { 'm:1115:p1': 1000, 'm:1115:p2': 1000 } }) } });

// website marks Room 5 at the same moment, having never seen the phone's marks
const pushed = await call('/push', { nights: { '2026-09-04': night({ marks: { '1115': { p20: 'in' } }, ts: { 'm:1115:p20': 1001 } }) } });
const got = pushed.nights['2026-09-04'].marks['1115'];
check('both devices\' marks survive', got.p1 === 'in' && got.p2 === 'out' && got.p20 === 'in', JSON.stringify(got));

// a third device pulls from scratch and sees everything
const all = await call('/pull', { since: 0 });
check('a fresh device pulls the merged night', JSON.stringify(all.nights['2026-09-04'].marks['1115']) === JSON.stringify(got));

// and pulling again with the cursor it was given returns nothing new
const none = await call('/pull', { since: all.now });
check('nothing new after the cursor', Object.keys(none.nights).length === 0, JSON.stringify(none.nights));

// a later correction from one device wins everywhere
await call('/push', { nights: { '2026-09-04': night({ marks: { '1115': { p1: 'out' } }, ts: { 'm:1115:p1': 5000 } }) } });
const after = await call('/pull', { since: 0 });
check('a later correction wins', after.nights['2026-09-04'].marks['1115'].p1 === 'out');

// a bad date key is ignored rather than stored
await call('/push', { nights: { 'not-a-date': night({}) } });
check('rubbish date keys are ignored', !(await call('/pull', { since: 0 })).nights['not-a-date']);

// everyone's standing settings ride as the night dated 0000-00-00, one note cell per "pid.field":
// a later "back in town" from one device clears what an earlier one turned on
await call('/push', { nights: { '0000-00-00': night({ notes: { 'p31.away': '1', 'p11.reason': 'Home sick' }, ts: { 'n:p31.away': 2000, 'n:p11.reason': 2000 } }) } });
const back = await call('/push', { nights: { '0000-00-00': night({ ts: { 'n:p31.away': 3000 } }) } });
const pn = back.nights['0000-00-00'].notes;
check('people record merges cell by cell', pn['p31.away'] === undefined && pn['p11.reason'] === 'Home sick', JSON.stringify(pn));
const pulledPeople = (await call('/pull', { since: 0 })).nights['0000-00-00'];
check('people record comes back on a pull', pulledPeople && pulledPeople.notes['p11.reason'] === 'Home sick');

console.log(bad ? bad + ' failing' : 'server: all checks pass');
process.exit(bad ? 1 : 0);
