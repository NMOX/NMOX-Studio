package org.nmox.studio.ui.browser.devtools;

import java.util.List;

/**
 * The JavaScript half of the Browser DevTools, kept as Java string
 * constants so the pure-core tests can pin the load-bearing properties
 * without a JS engine (there is none in unit tests — Nashorn is gone,
 * and we deliberately add no JS engine dependency; the scripts execute
 * only inside the WebView).
 *
 * <p>What the tests pin: the {@code __nmoxDevInstalled} idempotence
 * guard, that the console wrapper preserves and still calls the
 * original, that the caps ({@code 8000} chars per console line,
 * {@code 500} URL chars, DOM depth {@code 30} / {@code 5000} nodes /
 * {@code 200} attr chars, Vue depth {@code 25} / {@code 2000}
 * components / {@code 2000} value chars, Svelte scan {@code 20000}
 * elements / {@code 200} locations per file, storage {@code 500}) are
 * embedded as literals, that the Vue walker recognizes both
 * version markers ({@code __vue_app__}/{@code __vueParentComponent}
 * for Vue 3, {@code __vue__} for Vue 2), and that the Svelte scan
 * reads the dev-mode {@code __svelte_meta} marker.
 *
 * <p>Everything is ES5-shaped (WebKit in WebView is modern, but ES5
 * keeps us off the edge) and every script is a single self-invoking
 * expression returning a string, because {@code executeScript} hands
 * back the completion value.
 */
public final class DevScripts {

    private DevScripts() {
    }

    /**
     * Installed once per successful page load, AFTER the Java bridge
     * object is set as {@code window.nmoxBridge}. Idempotent via the
     * {@code window.__nmoxDevInstalled} guard. Wraps console.* (the
     * originals are preserved and still called), window.onerror +
     * unhandledrejection, and fetch/XMLHttpRequest for the Network tab.
     * All caps enforced page-side BEFORE the string crosses the bridge
     * (the Java models cap again — belt and braces).
     */
    public static final String INSTALL =
            "(function(){\n"
            + "if(window.__nmoxDevInstalled){return 'already';}\n"
            + "window.__nmoxDevInstalled=true;\n"
            + "var B=window.nmoxBridge;\n"
            + "if(!B){return 'no-bridge';}\n"
            + "var CAP=8000;\n"
            + "function cap(s){s=''+s;return s.length>CAP?s.slice(0,CAP)+'\\u2026[truncated]':s;}\n"
            + "function safeReplacer(){var seen=[];return function(k,v){\n"
            + " if(typeof v==='function'){return '[function]';}\n"
            + " if(v&&typeof v==='object'){if(seen.indexOf(v)>=0){return '[circular]';}seen.push(v);}\n"
            + " return v;};}\n"
            + "function fmt(args){var out=[];\n"
            + " for(var i=0;i<args.length;i++){var a=args[i];\n"
            + "  if(typeof a==='string'){out.push(a);continue;}\n"
            + "  try{var s=JSON.stringify(a,safeReplacer());out.push(s===undefined?(''+a):s);}\n"
            + "  catch(e){out.push(''+a);}}\n"
            + " return cap(out.join(' '));}\n"
            + "var levels=['log','info','warn','error','debug'];\n"
            + "for(var li=0;li<levels.length;li++){(function(level){\n"
            + " var orig=console[level]?console[level].bind(console):null;\n"
            + " console[level]=function(){\n"
            + "  try{B.log(level,fmt(arguments));}catch(e){}\n"
            + "  if(orig){orig.apply(null,arguments);}};})(levels[li]);}\n"
            + "window.addEventListener('error',function(e){\n"
            + " try{B.err(cap((e.message||'error')+' ('+(e.filename||'')+':'+(e.lineno||0)+')'));}catch(x){}});\n"
            + "window.addEventListener('unhandledrejection',function(e){\n"
            + " var r=e&&e.reason;\n"
            + " try{B.err(cap('Unhandled rejection: '+(r&&r.message?r.message:r)));}catch(x){}});\n"
            + "function report(m,u,s,ok,d,z){\n"
            + " try{B.net(JSON.stringify({m:(''+m).slice(0,20),u:(''+u).slice(0,500),s:s,ok:!!ok,d:d,z:z}));}catch(e){}}\n"
            + "var origFetch=window.fetch;\n"
            + "if(origFetch){window.fetch=function(input,init){\n"
            + " var url=(typeof input==='string')?input:((input&&input.url)||'');\n"
            + " var method=(init&&init.method)||(input&&input.method)||'GET';\n"
            + " var t0=Date.now();\n"
            + " return origFetch.apply(this,arguments).then(function(res){\n"
            + "  var len=null;\n"
            + "  try{len=res.headers&&res.headers.get?res.headers.get('content-length'):null;}catch(e){}\n"
            + "  report(method,url,res.status,res.ok,Date.now()-t0,len?+len:-1);\n"
            + "  return res;\n"
            + " },function(err){report(method,url,0,false,Date.now()-t0,-1);throw err;});};}\n"
            + "if(window.XMLHttpRequest){\n"
            + " var XO=XMLHttpRequest.prototype.open;var XS=XMLHttpRequest.prototype.send;\n"
            + " XMLHttpRequest.prototype.open=function(method,url){\n"
            + "  this.__nmoxM=''+method;this.__nmoxU=''+url;return XO.apply(this,arguments);};\n"
            + " XMLHttpRequest.prototype.send=function(){\n"
            + "  var xhr=this;var t0=Date.now();\n"
            + "  xhr.addEventListener('loadend',function(){\n"
            + "   var size=-1;\n"
            + "   try{var len=xhr.getResponseHeader('content-length');\n"
            + "    if(len){size=+len;}else if(xhr.response&&xhr.response.size){size=xhr.response.size;}}catch(e){}\n"
            + "   report(xhr.__nmoxM||'GET',xhr.__nmoxU||'',xhr.status,(xhr.status>=200&&xhr.status<400),Date.now()-t0,size);});\n"
            + "  return XS.apply(this,arguments);};}\n"
            + "return 'installed';})()";

    /**
     * Serializes the live DOM from documentElement into bounded JSON:
     * per node {@code t} tag, {@code i} id, {@code c} class string,
     * {@code a} attr summaries, {@code p} child-index path from the
     * root (the re-resolvable node address), {@code k} children.
     * Bounds: depth 30, 5000 nodes total, each attr value 200 chars;
     * past a cap an honest "…N more" placeholder node appears.
     */
    public static final String DOM_SNAPSHOT =
            "(function(){\n"
            + "var MAX_DEPTH=30,MAX_NODES=5000,ATTR_CAP=200;\n"
            + "var count=0;\n"
            + "function more(n){return {t:'\\u2026'+n+' more',p:[]};}\n"
            + "function ser(el,depth,path){\n"
            + " if(count>=MAX_NODES){return null;}\n"
            + " count++;\n"
            + " var o={t:el.tagName?(''+el.tagName).toLowerCase(java.util.Locale.ROOT):'#node',p:path};\n"
            + " if(el.id){o.i=(''+el.id).slice(0,ATTR_CAP);}\n"
            + " if(el.className&&typeof el.className==='string'&&el.className){o.c=el.className.slice(0,ATTR_CAP);}\n"
            + " var attrs=[];\n"
            + " if(el.attributes){\n"
            + "  for(var i=0;i<el.attributes.length&&i<20;i++){var a=el.attributes[i];\n"
            + "   if(a.name==='id'||a.name==='class'){continue;}\n"
            + "   attrs.push(a.name+'=\"'+(''+a.value).slice(0,ATTR_CAP)+'\"');}}\n"
            + " if(attrs.length){o.a=attrs;}\n"
            + " var kids=[];\n"
            + " if(el.children&&el.children.length){\n"
            + "  if(depth>=MAX_DEPTH){kids.push(more(el.children.length));}\n"
            + "  else{for(var j=0;j<el.children.length;j++){\n"
            + "   var k=ser(el.children[j],depth+1,path.concat(j));\n"
            + "   if(k===null){kids.push(more(el.children.length-j));break;}\n"
            + "   kids.push(k);}}}\n"
            + " if(kids.length){o.k=kids;}\n"
            + " return o;}\n"
            + "var root=document.documentElement;\n"
            + "if(!root){return '{}';}\n"
            + "return JSON.stringify(ser(root,0,[])||{});})()";

    /**
     * Walks a detected Vue app into bounded JSON {@code {v:2|3,
     * r:[roots]}}; per component {@code n} name, {@code p} props,
     * {@code s} state, {@code d} DOM child-index path of its root
     * element, {@code k} children. Vue 3 is found via {@code
     * __vue_app__} roots (fallback: {@code __vueParentComponent}
     * walked up), Vue 2 via {@code __vue__} roots walked down through
     * {@code $children}. Bounds: component depth 25, 2000 components,
     * each value safe-stringified and capped at 2000 chars.
     */
    public static final String VUE_SNAPSHOT =
            "(function(){\n"
            + "var MAX_DEPTH=25,MAX_COMP=2000,VAL_CAP=2000,MAX_SCAN=20000;\n"
            + "var count=0;\n"
            + "function safe(v){\n"
            + " try{var seen=[];\n"
            + "  var s=JSON.stringify(v,function(k,x){\n"
            + "   if(typeof x==='function'){return '[function]';}\n"
            + "   if(x&&typeof x==='object'){if(seen.indexOf(x)>=0){return '[circular]';}seen.push(x);}\n"
            + "   return x;});\n"
            + "  if(s===undefined){s=''+v;}\n"
            + "  return (''+s).slice(0,VAL_CAP);\n"
            + " }catch(e){return '[unserializable]';}}\n"
            + "function domPath(el){var p=[];\n"
            + " while(el&&el.parentElement){\n"
            + "  var idx=Array.prototype.indexOf.call(el.parentElement.children,el);\n"
            + "  if(idx<0){return [];}\n"
            + "  p.unshift(idx);el=el.parentElement;}\n"
            + " return p;}\n"
            + "function bag(obj){var out={};\n"
            + " if(!obj){return out;}\n"
            + " try{var keys=Object.keys(obj);\n"
            + "  for(var i=0;i<keys.length&&i<100;i++){out[keys[i]]=safe(obj[keys[i]]);}}catch(e){}\n"
            + " return out;}\n"
            + "function name3(c){var t=c.type||{};\n"
            + " if(t.name){return t.name;}\n"
            + " if(t.__name){return t.__name;}\n"
            + " if(t.__file){var f=(''+t.__file).split(/[\\\\/]/).pop();return f.replace(/\\.\\w+$/,'');}\n"
            + " return 'Anonymous';}\n"
            + "function walk3(c,depth){\n"
            + " if(!c||count>=MAX_COMP||depth>MAX_DEPTH){return null;}\n"
            + " count++;\n"
            + " var node={n:name3(c),p:bag(c.props),s:{},k:[]};\n"
            + " var ss=bag(c.setupState);for(var q in ss){node.s[q]=ss[q];}\n"
            + " if(c.data&&typeof c.data==='object'){var dd=bag(c.data);for(var w in dd){node.s[w]=dd[w];}}\n"
            + " var el=null;\n"
            + " try{\n"
            + "  if(c.vnode&&c.vnode.el&&c.vnode.el.nodeType===1){el=c.vnode.el;}\n"
            + "  else if(c.subTree&&c.subTree.el&&c.subTree.el.nodeType===1){el=c.subTree.el;}\n"
            + " }catch(e){}\n"
            + " node.d=el?domPath(el):[];\n"
            + " kids3(c.subTree,node.k,depth,0);\n"
            + " return node;}\n"
            + "function kids3(vnode,out,depth,hops){\n"
            + " if(!vnode||count>=MAX_COMP||hops>200){return;}\n"
            + " if(vnode.component){var ch=walk3(vnode.component,depth+1);if(ch){out.push(ch);}return;}\n"
            + " var kids=vnode.children;\n"
            + " if(Object.prototype.toString.call(kids)==='[object Array]'){\n"
            + "  for(var i=0;i<kids.length;i++){\n"
            + "   if(kids[i]&&typeof kids[i]==='object'){kids3(kids[i],out,depth,hops+1);}}}}\n"
            + "function name2(v){\n"
            + " return (v.$options&&(v.$options.name||v.$options._componentTag))||'Anonymous';}\n"
            + "function walk2(v,depth){\n"
            + " if(!v||count>=MAX_COMP||depth>MAX_DEPTH){return null;}\n"
            + " count++;\n"
            + " var node={n:name2(v),p:bag(v.$props),s:bag(v.$data),k:[],\n"
            + "  d:(v.$el&&v.$el.nodeType===1)?domPath(v.$el):[]};\n"
            + " var kids=v.$children||[];\n"
            + " for(var i=0;i<kids.length;i++){var c=walk2(kids[i],depth+1);if(c){node.k.push(c);}}\n"
            + " return node;}\n"
            + "var roots=[];var version=null;\n"
            + "var all=document.querySelectorAll('*');\n"
            + "var appSeen='';\n"
            + "for(var i=0;i<all.length&&i<MAX_SCAN;i++){var el=all[i];\n"
            + " if(el.__vue_app__&&!appSeen){appSeen=''+(el.__vue_app__.version||'3');}\n"
            + " if(el.__vue_app__&&el.__vue_app__._instance){\n"
            + "  version=3;var r=walk3(el.__vue_app__._instance,0);if(r){roots.push(r);}}\n"
            + " else if(el.__vue__&&!el.__vue__.$parent){\n"
            + "  if(version===null){version=2;}var r2=walk2(el.__vue__,0);if(r2){roots.push(r2);}}}\n"
            + "if(!roots.length){\n"
            + " var seenTop=[];\n"
            + " for(var j=0;j<all.length&&j<MAX_SCAN;j++){\n"
            + "  var pc=all[j].__vueParentComponent;\n"
            + "  if(pc){var top=pc;var hop=0;\n"
            + "   while(top.parent&&hop<200){top=top.parent;hop++;}\n"
            + "   if(seenTop.indexOf(top)<0){seenTop.push(top);version=3;\n"
            + "    var r3=walk3(top,0);if(r3){roots.push(r3);}}}}}\n"
            // a PRODUCTION Vue build exposes neither app._instance nor
            // __vueParentComponent (both are dev/devtools-gated), so the
            // tree is genuinely unreachable — report the app we DID see so
            // the pane can say "prod build" instead of "no Vue" (the
            // v1.206.0 gauntlet find; official Vue DevTools is limited the
            // same way)
            + "return JSON.stringify({v:version,r:roots,"
            + "prod:(roots.length===0?appSeen:'')});})()";

    /**
     * Maps a running dev-build Svelte app back to its sources: dev mode
     * plants {@code __svelte_meta} ({@code {loc:{file,line,column}}}) on
     * every element a component renders — that source mapping is ALL a
     * runtime inspector can offer, because the Svelte compiler compiles
     * components away (no instances, props, or state exist at runtime,
     * and a production build carries no marker at all). Scans up to
     * {@code 20000} elements, groups them by {@code loc.file}, and
     * returns JSON {@code {files:[{file,count,locs:[{line,column,
     * path:[childIndexPath]}]}],total:N}} with locations capped at
     * {@code 200} per file (count keeps the honest total). No marker
     * found is the empty answer {@code {files:[],total:0}}.
     */
    public static final String SVELTE_SNAPSHOT =
            "(function(){\n"
            + "var MAX_SCAN=20000,LOC_CAP=200;\n"
            + "function domPath(el){var p=[];\n"
            + " while(el&&el.parentElement){\n"
            + "  var idx=Array.prototype.indexOf.call(el.parentElement.children,el);\n"
            + "  if(idx<0){return [];}\n"
            + "  p.unshift(idx);el=el.parentElement;}\n"
            + " return p;}\n"
            + "var all=document.querySelectorAll('*');\n"
            + "var files=[];var byFile={};var total=0;\n"
            + "for(var i=0;i<all.length&&i<MAX_SCAN;i++){var el=all[i];\n"
            + " var m=el.__svelte_meta;\n"
            + " if(!m||!m.loc||!m.loc.file){continue;}\n"
            + " total++;\n"
            + " var f=(''+m.loc.file).slice(0,500);\n"
            + " var g=byFile[f];\n"
            + " if(!g){g={file:f,count:0,locs:[]};byFile[f]=g;files.push(g);}\n"
            + " g.count++;\n"
            + " if(g.locs.length<LOC_CAP){\n"
            + "  g.locs.push({line:+m.loc.line||0,column:+m.loc.column||0,path:domPath(el)});}}\n"
            + "return JSON.stringify({files:files,total:total});})()";

    /**
     * Walks a running dev-build Angular app into bounded JSON
     * {@code {v:'17.0.x', prod:'', r:[roots]}}; per component {@code n}
     * class name, {@code s} instance state (own enumerable fields,
     * safe-stringified), {@code dir} directive class names on the same
     * host, {@code d} DOM child-index path of the host element,
     * {@code k} children (nesting derived from nearest-ancestor host,
     * linked via a temporary {@code __nmoxNgIdx} marker that is
     * deleted before the script returns). Detection is two-step and
     * the honesty mirrors the Vue pane's: Angular stamps
     * {@code ng-version} on the root host in dev AND prod builds, but
     * only a DEV build exposes {@code window.ng.getComponent} — so
     * "Angular present, tree unreachable" is reported as
     * {@code prod:version} (a production build strips the debug API;
     * the official Angular DevTools is limited the same way), never as
     * "no Angular". Bounds: scan {@code 20000} elements, {@code 2000}
     * components, {@code 20} directives per host, each value capped at
     * {@code 2000} chars.
     */
    public static final String ANGULAR_SNAPSHOT =
            "(function(){\n"
            + "var MAX_COMP=2000,VAL_CAP=2000,MAX_SCAN=20000,DIR_CAP=20;\n"
            + "var verEl=document.querySelector('[ng-version]');\n"
            + "var version=verEl?(''+verEl.getAttribute('ng-version')).slice(0,20):'';\n"
            + "var NG=window.ng;\n"
            + "var usable=!!(NG&&typeof NG.getComponent==='function');\n"
            + "if(!verEl&&!usable){return JSON.stringify({v:'',prod:'',r:[]});}\n"
            + "if(!usable){return JSON.stringify({v:version,prod:(version||'unknown'),r:[]});}\n"
            + "function safe(v){\n"
            + " try{var seen=[];\n"
            + "  var s=JSON.stringify(v,function(k,x){\n"
            + "   if(typeof x==='function'){return '[function]';}\n"
            + "   if(x&&typeof x==='object'){if(seen.indexOf(x)>=0){return '[circular]';}seen.push(x);}\n"
            + "   return x;});\n"
            + "  if(s===undefined){s=''+v;}\n"
            + "  return (''+s).slice(0,VAL_CAP);\n"
            + " }catch(e){return '[unserializable]';}}\n"
            + "function bag(obj){var out={};\n"
            + " if(!obj){return out;}\n"
            + " try{var keys=Object.keys(obj);\n"
            + "  for(var i=0;i<keys.length&&i<100;i++){\n"
            // __ngContext__ is Angular's own render-tree plumbing, not
            // component state — showing it reads as a leak (seen live)
            + "   if(keys[i]==='__ngContext__'){continue;}\n"
            + "   out[keys[i]]=safe(obj[keys[i]]);}}catch(e){}\n"
            + " return out;}\n"
            + "function domPath(el){var p=[];\n"
            + " while(el&&el.parentElement){\n"
            + "  var idx=Array.prototype.indexOf.call(el.parentElement.children,el);\n"
            + "  if(idx<0){return [];}\n"
            + "  p.unshift(idx);el=el.parentElement;}\n"
            + " return p;}\n"
            + "function cname(x){\n"
            + " try{return (''+((x&&x.constructor&&x.constructor.name)||'Anonymous')).slice(0,200);}\n"
            + " catch(e){return 'Anonymous';}}\n"
            + "var all=document.querySelectorAll('*');\n"
            + "var hosts=[];var nodes=[];\n"
            + "for(var i=0;i<all.length&&i<MAX_SCAN&&hosts.length<MAX_COMP;i++){var el=all[i];\n"
            + " var c=null;try{c=NG.getComponent(el);}catch(e){}\n"
            + " if(!c){continue;}\n"
            + " var dirs=[];\n"
            + " try{if(typeof NG.getDirectives==='function'){var ds=NG.getDirectives(el)||[];\n"
            + "  for(var j=0;j<ds.length&&j<DIR_CAP;j++){dirs.push(cname(ds[j]));}}}catch(e){}\n"
            + " el.__nmoxNgIdx=hosts.length;\n"
            + " hosts.push(el);\n"
            + " nodes.push({n:cname(c),s:bag(c),dir:dirs,d:domPath(el),k:[]});}\n"
            + "var roots=[];\n"
            + "for(var a=0;a<hosts.length;a++){\n"
            + " var p=hosts[a].parentElement;var parentIdx=-1;\n"
            + " while(p){if(p.__nmoxNgIdx!==undefined){parentIdx=p.__nmoxNgIdx;break;}\n"
            + "  p=p.parentElement;}\n"
            + " if(parentIdx>=0){nodes[parentIdx].k.push(nodes[a]);}\n"
            + " else{roots.push(nodes[a]);}}\n"
            + "for(var z=0;z<hosts.length;z++){try{delete hosts[z].__nmoxNgIdx;}catch(e){}}\n"
            + "return JSON.stringify({v:version,prod:'',r:roots});})()";

    /**
     * Reads localStorage, sessionStorage, and document.cookie into one
     * JSON snapshot; every key/value capped at 500 chars, 500 entries
     * per area. Read-only in v1 — edit/delete deliberately absent.
     */
    public static final String STORAGE_SNAPSHOT =
            "(function(){\n"
            + "var CAP=500;\n"
            + "function grab(st){var out=[];\n"
            + " for(var i=0;i<st.length&&i<500;i++){var k=st.key(i);\n"
            + "  out.push({k:(''+k).slice(0,CAP),v:(''+st.getItem(k)).slice(0,CAP)});}\n"
            + " return out;}\n"
            + "var l=[],s=[],c=[];\n"
            + "try{l=grab(window.localStorage);}catch(e){}\n"
            + "try{s=grab(window.sessionStorage);}catch(e){}\n"
            + "try{var raw=document.cookie||'';\n"
            + " if(raw){var parts=raw.split(/;\\s*/);\n"
            + "  for(var i=0;i<parts.length&&i<500;i++){c.push((''+parts[i]).slice(0,CAP));}}\n"
            + "}catch(e){}\n"
            + "return JSON.stringify({l:l,s:s,c:c});})()";

    /** Marker prefix a failed REPL evaluation returns (rendered red, not thrown). */
    public static final String EVAL_ERROR_MARKER = "__NMOX_EVAL_ERR__:";

    /**
     * Wraps a user REPL expression, INLINED so the host compiles it —
     * never {@code window.eval}: a page whose Content-Security-Policy
     * omits {@code 'unsafe-eval'} (Hacker News, GitHub, most serious
     * sites) refuses in-page eval with an EvalError, which made the
     * console dead on exactly the pages worth inspecting (v1.276.0,
     * the Browser-persona walk; the DOM/Network/Storage panes never
     * noticed because their executeScript payloads are host-compiled
     * top-level scripts, which unsafe-eval does not gate). The result
     * is safe-stringified (cycle-guarded, functions and circulars
     * named honestly) and capped at 8000 chars; a RUNTIME error comes
     * back as a marker-prefixed string so the Java side renders it
     * red. A SYNTAX error in the user's text now fails the whole
     * compile instead — the caller's error callback retries
     * {@link #statementScript} before reporting.
     */
    public static String evalScript(String userExpression) {
        return "(function(){\n"
                + "try{\n"
                + " var r=(\n" + userExpression + "\n);\n"
                + " if(r===undefined){return 'undefined';}\n"
                + " var s;\n"
                + " if(typeof r==='string'){s=r;}\n"
                + " else{var seen=[];\n"
                + "  s=JSON.stringify(r,function(k,v){\n"
                + "   if(typeof v==='function'){return '[function]';}\n"
                + "   if(v&&typeof v==='object'){if(seen.indexOf(v)>=0){return '[circular]';}seen.push(v);}\n"
                + "   return v;});\n"
                + "  if(s===undefined){s=''+r;}}\n"
                + " s=''+s;\n"
                + " return s.length>8000?s.slice(0,8000)+'\\u2026[truncated]':s;\n"
                + "}catch(e){return " + quote(EVAL_ERROR_MARKER) + "+e;}})()";
    }

    /**
     * The statement-form fallback for console input that is not a
     * single expression ({@code var x = 1}, {@code if (...) ...}):
     * the text runs as a host-compiled function body and the result is
     * honestly {@code undefined}. Tried by the caller only after the
     * expression form fails to compile.
     */
    public static String statementScript(String userText) {
        return "(function(){\n"
                + "try{\n"
                + userText + "\n"
                + ";return 'undefined';\n"
                + "}catch(e){return " + quote(EVAL_ERROR_MARKER) + "+e;}})()";
    }

    /**
     * Positions (or creates) the single reusable highlight overlay —
     * an absolutely positioned 2px-outline div with a reserved id —
     * over the element addressed by {@code path} (child indexes from
     * documentElement). A stale path hides the overlay and answers
     * "gone" (fail soft, never a throw).
     */
    public static String highlight(List<Integer> path) {
        return "(function(){\n"
                + "var path=" + pathJson(path) + ";\n"
                + "var el=document.documentElement;\n"
                + "for(var i=0;i<path.length&&el;i++){el=el.children[path[i]];}\n"
                + "var ov=document.getElementById('__nmox_hl');\n"
                + "if(!ov){ov=document.createElement('div');ov.id='__nmox_hl';\n"
                + " ov.style.cssText='position:absolute;pointer-events:none;z-index:2147483647;"
                + "border:2px solid #4a90d9;box-sizing:border-box;';\n"
                + " if(document.body){document.body.appendChild(ov);}else{return 'no-body';}}\n"
                + "if(!el||!el.getBoundingClientRect){ov.style.display='none';return 'gone';}\n"
                + "var r=el.getBoundingClientRect();\n"
                + "ov.style.display='block';\n"
                + "ov.style.left=(r.left+window.scrollX)+'px';\n"
                + "ov.style.top=(r.top+window.scrollY)+'px';\n"
                + "ov.style.width=r.width+'px';\n"
                + "ov.style.height=r.height+'px';\n"
                + "return 'ok';})()";
    }

    /** Hides the highlight overlay if present. */
    public static final String UNHIGHLIGHT =
            "(function(){var ov=document.getElementById('__nmox_hl');"
            + "if(ov){ov.style.display='none';}return 'ok';})()";

    /** The page's own idea of its URL — truthful across redirects. */
    public static final String PAGE_URL = "document.location.href";

    /**
     * Arms inspect-pick (v1.357.0): one capture-phase click listener
     * that swallows the click, records the clicked element's
     * child-index path (the {@link #DOM_SNAPSHOT} convention —
     * documentElement is the empty path) into
     * {@code window.__nmoxPickResult}, and disarms itself. The cursor
     * flips to crosshair while armed so the mode is visible.
     */
    public static final String PICK_ARM =
            "(function(){\n"
            + "if(window.__nmoxPickCancel){return 'armed';}\n"
            + "window.__nmoxPickResult='';\n"
            + "var prev=document.documentElement.style.cursor;\n"
            + "document.documentElement.style.cursor='crosshair';\n"
            + "function path(el){var p=[];\n"
            + " while(el&&el.parentElement){var kids=el.parentElement.children;\n"
            + "  for(var i=0;i<kids.length;i++){if(kids[i]===el){p.unshift(i);break;}}\n"
            + "  el=el.parentElement;}\n"
            + " return p;}\n"
            + "function disarm(){document.removeEventListener('click',onClick,true);\n"
            + " document.documentElement.style.cursor=prev;\n"
            + " window.__nmoxPickCancel=null;}\n"
            + "function onClick(ev){ev.preventDefault();ev.stopPropagation();\n"
            + " disarm();\n"
            + " window.__nmoxPickResult=JSON.stringify(path(ev.target));}\n"
            + "window.__nmoxPickCancel=disarm;\n"
            + "document.addEventListener('click',onClick,true);\n"
            + "return 'armed';})()";

    /** One poll of the pick result; reads AND clears (empty = none yet). */
    public static final String PICK_POLL =
            "(function(){var r=window.__nmoxPickResult||'';"
            + "window.__nmoxPickResult='';return r;})()";

    /** Disarms an armed pick without consuming a click. */
    public static final String PICK_CANCEL =
            "(function(){if(window.__nmoxPickCancel){window.__nmoxPickCancel();}"
            + "return 'ok';})()";

    /**
     * Which stylesheet rules MATCH the element at {@code path} —
     * the page's own cascade answer ({@code el.matches}), so
     * write-back never guesses specificity. Returns a JSON array of
     * {@code {h: sheetHref-or-empty, s: selectorText}} in cascade
     * order (grouping rules like @media descended into; cross-origin
     * sheets, whose cssRules throw, skipped). Capped at 200 rules.
     */
    public static String matchedRules(List<Integer> path) {
        return "(function(){\n"
                + "var path=" + pathJson(path) + ";\n"
                + "var el=document.documentElement;\n"
                + "for(var i=0;i<path.length&&el;i++){el=el.children[path[i]];}\n"
                + "if(!el||!el.matches){return '[]';}\n"
                + "var out=[];\n"
                + "function collect(rules,href){\n"
                + " for(var r=0;r<rules.length&&out.length<200;r++){var rule=rules[r];\n"
                + "  if(rule.selectorText){\n"
                + "   try{if(el.matches(rule.selectorText)){out.push({h:href,s:''+rule.selectorText});}}catch(e){}\n"
                + "  } else if(rule.cssRules){collect(rule.cssRules,href);}}}\n"
                + "var sheets=document.styleSheets;\n"
                + "for(var s=0;s<sheets.length;s++){\n"
                + " var rules;try{rules=sheets[s].cssRules;}catch(e){continue;}\n"
                + " if(rules){collect(rules,sheets[s].href||'');}}\n"
                + "return JSON.stringify(out);})()";
    }

    /**
     * Live preview for style write-back: sets the inline style on the
     * element at {@code path} so the page shows the tweak instantly,
     * before (and independent of) the source write.
     */
    /**
     * Installs (or replaces) the Motion pane's preview stylesheet — a
     * single {@code <style id="__nmox_motion">} holding the timeline's
     * current {@code @keyframes} block (v2.12.0). Idempotent per call;
     * {@link #clearMotionPreview()} removes it.
     */
    public static String injectMotionKeyframes(String cssBlock) {
        return "(function(){\n"
                + "var s=document.getElementById('__nmox_motion');\n"
                + "if(!s){s=document.createElement('style');s.id='__nmox_motion';\n"
                + "document.documentElement.appendChild(s);}\n"
                + "s.textContent=" + quote(cssBlock) + ";\n"
                + "return 'ok';})()";
    }

    /** Removes the Motion preview stylesheet and nothing else. */
    public static String clearMotionPreview() {
        return "(function(){var s=document.getElementById('__nmox_motion');\n"
                + "if(s&&s.parentNode){s.parentNode.removeChild(s);}return 'ok';})()";
    }

    public static String applyInlineStyle(List<Integer> path, String property, String value) {
        return "(function(){\n"
                + "var path=" + pathJson(path) + ";\n"
                + "var el=document.documentElement;\n"
                + "for(var i=0;i<path.length&&el;i++){el=el.children[path[i]];}\n"
                + "if(!el||!el.style){return 'gone';}\n"
                + "el.style.setProperty(" + quote(property) + "," + quote(value) + ");\n"
                + "return 'ok';})()";
    }

    /**
     * Reads the {@link StyleSummary#KEYS curated 15} computed-style
     * properties of the element at {@code path} as one JSON object;
     * a stale path answers "{}".
     */
    public static String computedStyle(List<Integer> path) {
        StringBuilder keys = new StringBuilder("[");
        for (int i = 0; i < StyleSummary.KEYS.size(); i++) {
            if (i > 0) {
                keys.append(',');
            }
            keys.append('\'').append(StyleSummary.KEYS.get(i)).append('\'');
        }
        keys.append(']');
        return "(function(){\n"
                + "var path=" + pathJson(path) + ";\n"
                + "var el=document.documentElement;\n"
                + "for(var i=0;i<path.length&&el;i++){el=el.children[path[i]];}\n"
                + "if(!el){return '{}';}\n"
                + "var cs;try{cs=window.getComputedStyle(el);}catch(e){return '{}';}\n"
                + "var keys=" + keys + ";\n"
                + "var out={};\n"
                + "for(var j=0;j<keys.length;j++){\n"
                + " try{out[keys[j]]=''+cs.getPropertyValue(keys[j]);}catch(e){}}\n"
                + "return JSON.stringify(out);})()";
    }

    /**
     * JSON/JS string-literal quoting for embedding untrusted text
     * (the REPL input is the USER's own expression, but it still must
     * not be able to escape its string literal by accident).
     */
    public static String quote(String text) {
        StringBuilder sb = new StringBuilder(text == null ? 2 : text.length() + 2);
        sb.append('"');
        if (text != null) {
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                switch (c) {
                    case '"': sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    case '<': sb.append("\\u003c"); break; // no accidental </script>
                    case '\u2028': sb.append("\\u2028"); break;
                    case '\u2029': sb.append("\\u2029"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private static String pathJson(List<Integer> path) {
        StringBuilder sb = new StringBuilder("[");
        if (path != null) {
            for (int i = 0; i < path.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Integer v = path.get(i);
                sb.append(v == null ? 0 : Math.max(0, v));
            }
        }
        return sb.append(']').toString();
    }
}
