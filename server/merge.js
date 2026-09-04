// The same reconciliation rule as the app's Merge.kt, written once and used by both the sync
// server and the website. Two implementations of a merge rule that disagree are worse than none,
// so the pair is pinned to a shared set of cases in server/fixtures.json.

export function markKey(sid, pid) { return "m:" + sid + ":" + pid; }
export function tonightKey(pid) { return "t:" + pid; }
export function noteKey(pid) { return "n:" + pid; }
/** An extra round walked on this night only. */
export function slotKey(sid) { return "s:" + sid; }
/** This round goes on this night's picture. */
export function sheetKey(sid) { return "h:" + sid; }
export const CLOSED_KEY = "c";

function emptyNight() {
  return { marks: {}, tonight: [], notes: {}, closed: false, slots: [], sheet: [], ts: {} };
}

/** Every cell either side knows anything about. */
function keysOf(n) {
  const out = new Set();
  for (const sid of Object.keys(n.marks || {}))
    for (const pid of Object.keys(n.marks[sid] || {})) out.add(markKey(sid, pid));
  for (const pid of n.tonight || []) out.add(tonightKey(pid));
  for (const pid of Object.keys(n.notes || {})) out.add(noteKey(pid));
  for (const sid of n.slots || []) out.add(slotKey(sid));
  for (const sid of n.sheet || []) out.add(sheetKey(sid));
  out.add(CLOSED_KEY);
  for (const k of Object.keys(n.ts || {})) out.add(k);
  return out;
}

/** The cell's value, or null when empty - unmarking is a value too, not an absence of one. */
export function valueOf(n, key) {
  if (key === CLOSED_KEY) return n.closed ? "1" : null;
  if (key.startsWith("m:")) {
    const i = key.indexOf(":", 2);
    const sid = key.slice(2, i), pid = key.slice(i + 1);
    return (n.marks && n.marks[sid] && n.marks[sid][pid]) || null;
  }
  if (key.startsWith("t:")) return (n.tonight || []).includes(key.slice(2)) ? "1" : null;
  if (key.startsWith("n:")) return (n.notes || {})[key.slice(2)] ?? null;
  if (key.startsWith("s:")) return (n.slots || []).includes(key.slice(2)) ? "1" : null;
  if (key.startsWith("h:")) return (n.sheet || []).includes(key.slice(2)) ? "1" : null;
  return null;
}

export function setValue(n, key, value) {
  if (key === CLOSED_KEY) { n.closed = value !== null; return; }
  if (key.startsWith("m:")) {
    const i = key.indexOf(":", 2);
    const sid = key.slice(2, i), pid = key.slice(i + 1);
    n.marks[sid] = n.marks[sid] || {};
    if (value === null) delete n.marks[sid][pid]; else n.marks[sid][pid] = value;
    return;
  }
  if (key.startsWith("t:")) {
    const pid = key.slice(2);
    const at = n.tonight.indexOf(pid);
    if (value !== null && at < 0) n.tonight.push(pid);
    if (value === null && at >= 0) n.tonight.splice(at, 1);
    return;
  }
  if (key.startsWith("n:")) {
    const pid = key.slice(2);
    if (value === null || value === "") delete n.notes[pid]; else n.notes[pid] = value;
    return;
  }
  if (key.startsWith("s:") || key.startsWith("h:")) {
    const list = key.startsWith("s:") ? n.slots : n.sheet;
    const sid = key.slice(2), at = list.indexOf(sid);
    if (value !== null && at < 0) list.push(sid);
    if (value === null && at >= 0) list.splice(at, 1);
  }
}

/**
 * The later edit of each cell wins. Ties fall back to comparing the values, so merging a pair in
 * either order lands in the same place - without that two devices can swap edits forever.
 */
export function merge(local, remote) {
  const out = emptyNight();
  const keys = [...new Set([...keysOf(local || emptyNight()), ...keysOf(remote || emptyNight())])].sort();
  const L = local || emptyNight(), R = remote || emptyNight();
  for (const key of keys) {
    const lt = (L.ts || {})[key] || 0;
    const rt = (R.ts || {})[key] || 0;
    const lv = valueOf(L, key);
    const rv = valueOf(R, key);
    const takeRemote = lt !== rt ? rt > lt : (rv || "") > (lv || "");
    const value = takeRemote ? rv : lv;
    setValue(out, key, value);
    const stamp = Math.max(lt, rt);
    if (stamp > 0 || value !== null) out.ts[key] = stamp;
  }
  // drop slots that ended up with nothing in them, so an empty night stays empty
  for (const sid of Object.keys(out.marks)) if (!Object.keys(out.marks[sid]).length) delete out.marks[sid];
  out.tonight.sort(); out.slots.sort(); out.sheet.sort();
  return out;
}
