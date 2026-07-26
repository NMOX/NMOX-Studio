# Tutorial: Migrating from Postman (and the browser)

API Studio reads the files you already have: a Postman collection, a
devtools HAR capture, a curl command, a `.http` file, an OpenAPI spec.
This walk takes a real Postman export end to end — and shows the one
thing NMOX Studio does differently on purpose: **secrets land in your
OS keychain, never in a committable file.**

## Before you start

Export your collection from Postman: collection ▸ … ▸ Export ▸
**Collection v2.1**. (A v1 export or an *environment* file is refused
with the fix spelled out — re-export as v2.1.)

## Steps

1. **Open API Studio** (⌥⌘8) and press **Import… ▸ Postman
   Collection…**. Pick your exported `.json`.

2. **Check what arrived.** Folders keep their identity as
   "Folder / Request" names. Postman's `{{variables}}` import
   *verbatim* — they are API Studio's own syntax — and collection
   variables join your active environment without clobbering anything
   you already set. `:id` path variables become `{{id}}`.

3. **Look at the Auth tab of a request that had a bearer token.** The
   token is *there* — but it came in through the keychain-backed Auth
   field, not a header row. Commit `.nmoxapi.json` freely; the secret
   is not in it. Anything the import could not represent (multipart
   bodies, scripts) is named in the status line, never silently
   mangled.

4. **Import a browser capture.** In devtools' Network tab, "Save all
   as HAR", then **Import… ▸ HAR capture…**. Only your XHR/fetch
   traffic imports (page assets are counted out loud), session
   cookies are dropped — a captured cookie is a credential — and a
   recorded `Authorization` either moves to the keychain
   (Bearer/Basic) or is dropped and counted (anything opaque).

5. **Send one.** Pick an imported request, resolve `{{baseUrl}}` in
   your environment if needed, press **Send** — and read the
   security-header grade on the Standards tab while you're there.

6. **Go the other way.** **Import… ▸ Export collection to .http…**
   writes the whole collection in the REST Client dialect for any
   editor or CI runner. Auth is deliberately not in the file; each
   authed request carries a comment naming what to re-add.

## What you just learned

- Migration is one menu: curl / `.http` / OpenAPI / Postman / HAR in,
  `.http` out.
- The secrets law holds at every border: keychain in, keychain stays.
- Refusals are named, never silent — if something didn't import, the
  status line says what and why.
