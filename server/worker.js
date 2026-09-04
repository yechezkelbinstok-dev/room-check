// Room Check sync server - a Cloudflare Worker over a KV namespace.
//
// It holds nights, nothing else: no accounts, no roster, no history beyond what the clients send.
// Both clients stay offline-first and treat their own copy as the truth; this only reconciles.
// Every write is merged into what is already stored using the shared cell-by-cell rule, so two
// people marking at the same moment both keep their marks rather than the later save winning.

import { merge } from './merge.js';

const CORS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'Content-Type, Authorization',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
};

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json', ...CORS } });

/** date -> when the server last touched it, so a client can ask only for what it is missing. */
const INDEX = 'idx';

async function readIndex(env) {
  return (await env.NIGHTS.get(INDEX, 'json')) || {};
}

export default {
  async fetch(request, env) {
    if (request.method === 'OPTIONS') return new Response(null, { headers: CORS });

    const token = (request.headers.get('Authorization') || '').replace(/^Bearer\s+/i, '');
    if (!env.SYNC_TOKEN || token !== env.SYNC_TOKEN) return json({ error: 'unauthorized' }, 401);
    if (request.method !== 'POST') return json({ error: 'use POST' }, 405);

    const url = new URL(request.url);
    const body = await request.json().catch(() => ({}));
    const now = Date.now();

    if (url.pathname.endsWith('/pull')) {
      const since = Number(body.since) || 0;
      const idx = await readIndex(env);
      const nights = {};
      // only the nights that changed since this client last asked - a full history every time
      // would grow without bound and be sent over a phone connection at 11pm
      for (const [date, at] of Object.entries(idx)) {
        if (at > since) {
          const n = await env.NIGHTS.get('n:' + date, 'json');
          if (n) nights[date] = n;
        }
      }
      return json({ now, nights });
    }

    if (url.pathname.endsWith('/push')) {
      const incoming = body.nights || {};
      const idx = await readIndex(env);
      const merged = {};
      for (const [date, night] of Object.entries(incoming)) {
        if (!/^\d{4}-\d{2}-\d{2}$/.test(date)) continue;
        const stored = await env.NIGHTS.get('n:' + date, 'json');
        const out = merge(stored, night);
        await env.NIGHTS.put('n:' + date, JSON.stringify(out));
        idx[date] = now;
        merged[date] = out;
      }
      if (Object.keys(merged).length) await env.NIGHTS.put(INDEX, JSON.stringify(idx));
      // handing the merged night straight back saves the client a second round trip to find out
      // what its edit turned into once someone else's was folded in
      return json({ now, nights: merged });
    }

    return json({ error: 'not found' }, 404);
  },
};
