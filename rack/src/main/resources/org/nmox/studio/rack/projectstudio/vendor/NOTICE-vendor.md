# Vendored classic library attribution

The `.js` files in this directory are unmodified, pinned production
builds of the classic web libraries the Classic Kit (File → Classic
Kit…) and the "Classic Web (jQuery)" template ship into projects.
Each was fetched from its official home (code.jquery.com for jQuery,
cdnjs mirrors of the official releases for the rest), verified against
its in-file banner/version marker, and is redistributed under its MIT
(or MIT-style) license. Only the supported jQuery 3.x line is bundled —
1.x/2.x are end-of-life and deliberately absent.

| File | Library | Version | License |
|---|---|---|---|
| jquery-3.7.1.min.js | jQuery | 3.7.1 | MIT (banner in file; https://jquery.org/license/) |
| mootools-core-1.6.0-compat.min.js | MooTools Core (compat build) | 1.6.0 | MIT-style (banner in file; https://github.com/mootools/mootools-core) |
| prototype-1.7.3.js | Prototype | 1.7.3 | MIT-style (banner in file; https://github.com/prototypejs/prototype/blob/master/LICENSE) |
| backbone-1.6.0.min.js | Backbone.js | 1.6.0 | MIT (https://github.com/jashkenas/backbone/blob/master/LICENSE) |
| underscore-1.13.7.min.js | Underscore.js | 1.13.7 | MIT (https://github.com/jashkenas/underscore/blob/master/LICENSE) |
| knockout-3.5.1.js | Knockout | 3.5.1 (production/minified build) | MIT (https://github.com/knockout/knockout/blob/master/LICENSE) |

## Fetch record (2026-07-05)

| File | Source URL | Bytes | SHA-256 |
|---|---|---|---|
| jquery-3.7.1.min.js | https://code.jquery.com/jquery-3.7.1.min.js | 87533 | fc9a93dd241f6b045cbff0481cf4e1901becd0e12fb45166a8f17f95823f0b1a |
| mootools-core-1.6.0-compat.min.js | https://cdnjs.cloudflare.com/ajax/libs/mootools/1.6.0/mootools-core-compat.min.js | 127518 | 07bec3e5fa672d0e0e028a72e4e26a0269906687519fd5f5761d42805e61a31f |
| prototype-1.7.3.js | https://cdnjs.cloudflare.com/ajax/libs/prototype/1.7.3/prototype.js | 199803 | 46bc7c7b853bf69ab0b165153453f7c1e84bf6982fe8adb6245088a5f3de8360 |
| backbone-1.6.0.min.js | https://cdnjs.cloudflare.com/ajax/libs/backbone.js/1.6.0/backbone-min.js | 25673 | e13ceba154c3897f282d3106d6b015b1c5f699ad69b4517fd1cd490fd08190eb |
| underscore-1.13.7.min.js | https://cdnjs.cloudflare.com/ajax/libs/underscore.js/1.13.7/underscore-umd-min.js | 19571 | c12dae551a8773812f3c6b682e8e66ecf71c729bf7e83f295747c0773e76cabb |
| knockout-3.5.1.js | https://cdnjs.cloudflare.com/ajax/libs/knockout/3.5.1/knockout-latest.min.js | 67224 | 8c9cadf2d340b9a5ff3a7f4601116ddc451747986b3b3d02cb1d6c5f2165d553 |
| alpinejs-3.14.9.min.js | https://cdn.jsdelivr.net/npm/alpinejs@3.14.9/dist/cdn.min.js | 44758 | 3ed1eed252488921df65e363d6715deb04d7f92aaedb9e52199fdf73cb1e0ad3 |
| htmx-2.0.4.min.js | https://cdn.jsdelivr.net/npm/htmx.org@2.0.4/dist/htmx.min.js | 50917 | e209dda5c8235479f3166defc7750e1dbcd5a5c1808b7792fc2e6733768fb447 |

To refresh: bump a version here, re-download from the recorded source,
update bytes + SHA-256, and keep `VendorResourcesTest` markers in step —
it pins each file's name/version marker against corruption.
