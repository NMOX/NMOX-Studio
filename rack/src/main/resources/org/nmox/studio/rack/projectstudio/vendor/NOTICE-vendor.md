# Vendored classic library attribution

The `.js` files in this directory are unmodified, pinned production
builds of the classic web libraries the Classic Kit (File → Classic
Kit…) and the "Classic Web (jQuery)" template ship into projects.
Each was fetched from its official home (code.jquery.com for jQuery,
the project's GitHub release for Knockout, cdnjs or jsDelivr mirrors of
the official npm releases for the rest), verified against
its in-file banner/version marker, and is redistributed under its MIT
(or MIT-style) license. Only the supported jQuery 3.x line is bundled —
1.x/2.x are end-of-life and deliberately absent.

| File | Library | Version | License |
|---|---|---|---|
| jquery-3.7.1.min.js | jQuery | 3.7.1 | MIT (banner in file; https://jquery.org/license/) |
| mootools-core-1.6.0-compat.min.js | MooTools Core (compat build) | 1.6.0 | MIT-style (banner in file; https://github.com/mootools/mootools-core) |
| prototype-1.7.3.js | Prototype | 1.7.3 | MIT-style (banner in file; https://github.com/prototypejs/prototype/blob/master/LICENSE) |
| backbone-1.6.1.min.js | Backbone.js | 1.6.1 | MIT (https://github.com/jashkenas/backbone/blob/master/LICENSE) |
| underscore-1.13.8.min.js | Underscore.js | 1.13.8 | MIT (https://github.com/jashkenas/underscore/blob/master/LICENSE) |
| knockout-3.5.3.js | Knockout | 3.5.3 (production/minified build) | MIT (https://github.com/knockout/knockout/blob/master/LICENSE) |

## Fetch record (2026-07-05; five rows refreshed 2026-11-13, marked)

| File | Source URL | Bytes | SHA-256 |
|---|---|---|---|
| jquery-3.7.1.min.js | https://code.jquery.com/jquery-3.7.1.min.js | 87533 | fc9a93dd241f6b045cbff0481cf4e1901becd0e12fb45166a8f17f95823f0b1a |
| mootools-core-1.6.0-compat.min.js | https://cdnjs.cloudflare.com/ajax/libs/mootools/1.6.0/mootools-core-compat.min.js | 127518 | 07bec3e5fa672d0e0e028a72e4e26a0269906687519fd5f5761d42805e61a31f |
| prototype-1.7.3.js | https://cdnjs.cloudflare.com/ajax/libs/prototype/1.7.3/prototype.js | 199803 | 46bc7c7b853bf69ab0b165153453f7c1e84bf6982fe8adb6245088a5f3de8360 |
| backbone-1.6.1.min.js (2026-11-13) | https://cdnjs.cloudflare.com/ajax/libs/backbone.js/1.6.1/backbone-min.js | 25690 | 2d42cb138dcf8277903341663010cff4f56da441c6adaf8aa76c0b58579ec4e7 |
| underscore-1.13.8.min.js (2026-11-13) | https://cdnjs.cloudflare.com/ajax/libs/underscore.js/1.13.8/underscore-umd-min.js | 19668 | 89e99999dfa83ee9fb88e58beb7820961dbfcf391cee2f361ffd4a3489309c69 |
| knockout-3.5.3.js (2026-11-13) | https://github.com/knockout/knockout/releases/download/v3.5.3/knockout-3.5.3.js | 68705 | 07885e55bd3eb047aa4fece214c0360ded1f181ca4aba798f4b46909a9f58d89 |
| alpinejs-3.17.5.min.js (2026-11-13) | https://cdn.jsdelivr.net/npm/alpinejs@3.17.5/dist/cdn.min.js | 55935 | b4036e9e9ac22fa6f6ae1ba292e015e1f3e9c85d0d79e73e4c8eb9bb139b522e |
| htmx-2.0.11.min.js (2026-11-13) | https://cdn.jsdelivr.net/npm/htmx.org@2.0.11/dist/htmx.min.js | 52182 | d6fdc75f204e6bdefa99b69bf1e6d4ac69b8a364f77929f45c13476b4000f717 |

The 2026-11-13 rows were each fetched twice, from the mirror above and
from the package's npm registry tarball (`backbone-min.js`,
`underscore-umd-min.js`, `build/output/knockout-latest.js`,
`dist/cdn.min.js`, `dist/htmx.min.js`), and the two copies are
byte-identical. Underscore 1.13.8 is a security release
(CVE-2026-27601, GHSA-qpx9-hpmf-5gmw: unbounded recursion in
`_.flatten` and `_.isEqual`). Knockout moved off cdnjs: its
`knockout-latest.min.js` is cdnjs's own re-minification and is not
byte-identical to the build the Knockout team publishes (measured for
3.5.1: 67224 bytes on cdnjs, 68249 in the npm tarball), so the file here
is now the release asset itself.

Held on purpose: jQuery 3.7.1 is the newest 3.x (jQuery 4.0.0 is a new
major that removes APIs classic code uses), htmx 2.0.x (htmx 4 is
published under npm's `next` tag), and MooTools 1.6.0 / Prototype 1.7.3,
which are their projects' final releases.

To refresh: bump a version here, re-download from the recorded source,
update bytes + SHA-256, and keep `VendorResourcesTest` markers in step —
it pins each file's name/version marker against corruption.
