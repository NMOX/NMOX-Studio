# Tutorial: API Studio

API Studio is a Postman-style REST workbench built into the IDE. You
build requests, run assertions against the response, and — uniquely —
every response is graded against the web's security-header standards.

![A live 200 in 331ms — and the Standards tab grading the response's security headers](../images/api-studio.png)

## Open it

`⌥⌘8`, or the **API Studio** tab (open by default on first launch).

## Steps

1. **Make a request.** In the request builder, set the method to `GET`
   and the URL to `https://httpbin.org/json`. Press **Send**. The
   response body arrives pretty-printed; the status line shows the code,
   time, and size. (A runaway response can't hurt you — bodies stream
   through an 8 MB cap.)

2. **Add an assertion.** On the **Tests** tab add `Status is 200` and
   `Body contains slideshow`. Send again — each assertion shows a green
   ✓ or red ✗ with the actual value.

3. **Read the security grade.** Open the **Standards** tab. API Studio
   grades HSTS, CSP, X-Content-Type-Options, clickjacking protection,
   Referrer-Policy and more, and gives a letter grade — the check a 2026
   web developer runs at securityheaders.com, built into every send.

4. **Use a variable.** Create an environment with `base =
   https://httpbin.org`, then set a request URL to `{{base}}/get`.
   Switch environments to repoint every request at once. If the rack has
   a live dev server, API Studio even offers its URL as `{{baseUrl}}`.

5. **Add auth safely.** On the **Auth** tab pick Bearer or Basic and
   enter a token. The token is **never** written to the committable
   `.nmoxapi.json` — it lives in the OS keychain, keyed to the request.

## What you just learned

- Requests, environments, and assertions persist per-project in
  `.nmoxapi.json` (secrets excluded).
- The security grade turns "did it work" into "is it safe."
- Sends are cancellable (the Send button becomes **Cancel**) and never
  block the rest of the IDE.

## Next

- Point a request at a running rack server via the `{{baseUrl}}` offer.
- See [DB Studio](db-studio.md) for the database equivalent.
