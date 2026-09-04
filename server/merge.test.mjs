// Runs the shared fixtures against the JavaScript merge. The Kotlin side runs the same file.
import { merge } from './merge.js';
import { readFileSync } from 'fs';

const { cases } = JSON.parse(readFileSync(new URL('./fixtures.json', import.meta.url)));
let bad = 0;
for (const c of cases) {
  for (const [order, [a, b]] of [['a,b', [c.local, c.remote]], ['b,a', [c.remote, c.local]]]) {
    const got = merge(structuredClone(a), structuredClone(b));
    const want = c.expect;
    if (JSON.stringify(sortDeep(got)) !== JSON.stringify(sortDeep(want))) {
      bad++;
      console.log(`FAIL (${order}) ${c.name}\n  got  ${JSON.stringify(sortDeep(got))}\n  want ${JSON.stringify(sortDeep(want))}`);
    }
  }
}
function sortDeep(v) {
  if (Array.isArray(v)) return [...v].sort();
  if (v && typeof v === 'object') {
    return Object.fromEntries(Object.keys(v).sort().map(k => [k, sortDeep(v[k])]));
  }
  return v;
}
console.log(bad ? `${bad} failing` : `all ${cases.length} cases agree, both orders`);
process.exit(bad ? 1 : 0);
