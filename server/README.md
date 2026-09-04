# Room Check sync

The app and the website each keep their own copy of every night and work with no signal at all.
This little server is what lets those copies find each other, so a check marked on the website
shows up on the phone and the other way round.

It stores nights and nothing else — no accounts, no roster, no names beyond what is in a night.

## What it costs

Nothing. Cloudflare's free tier covers this many times over: a night is a few kilobytes and there
are a few hundred a year.

## Setting it up

You need a free Cloudflare account. From this folder:

```sh
npm install -g wrangler        # once
wrangler login                 # opens a browser
wrangler kv namespace create NIGHTS
```

That last command prints an `id`. Put it in `wrangler.toml` where it says `PUT_THE_KV_ID_HERE`.

Then pick a sync password — any long-ish string, it is what stops strangers reading the sheet —
and set it:

```sh
wrangler secret put SYNC_TOKEN
wrangler deploy
```

`deploy` prints a URL like `https://room-check-sync.<your-name>.workers.dev`.

## Pointing the app and the site at it

In **Settings → Sync**, on both the app and the website, put in:

- **Server address** — that URL
- **Sync password** — the `SYNC_TOKEN` you set

Then **Sync now**. After that it syncs on its own: when the app is opened, when the website tab
comes back to the front, and a few seconds after marking stops.

Leave the address blank and nothing changes — each device keeps to itself, exactly as before.

## How conflicts are settled

Not by last-save-wins. That would mean two people walking different rooms at the same time, each
saving a full night, and whoever saved second erasing the other's rooms.

Instead every night carries a timestamp per *cell* — one mark, one note, one excusal — and merging
happens cell by cell: the later edit of each individual cell wins, and cells nobody touched are
left alone. Marking Room 1 on the phone while someone marks Room 5 on the website leaves both.

That rule is written three times — `android/.../Merge.kt`, `server/merge.js`, and inside
`index.html` — because the three run in different places. All three are pinned to the same cases in
`fixtures.json`, and both `merge.test.mjs` and the app's `MergeFixturesTest` run against that file,
so the copies cannot quietly drift into disagreeing.

```sh
node merge.test.mjs      # the shared cases against the JavaScript merge
node worker.test.mjs     # the server itself, against an in-memory KV
```

## A note on what is exposed

Anyone with the URL *and* the password can read and write the sheet. That is the whole security
model — it is deliberately the same shape as the site's own password. Use a password you would not
mind being the only thing standing between the internet and the sheet, and don't reuse one.
