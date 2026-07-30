package org.nmox.studio.ui.browser.devtools;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The JS sources execute only inside the WebView (no JS engine exists
 * in unit tests — Nashorn is gone and we deliberately add none), so
 * these tests pin the load-bearing STRUCTURAL properties of the
 * scripts: the idempotence guard, original-console preservation, the
 * cap literals, both Vue version markers, and the quoting/builder
 * behavior that IS plain Java.
 */
class DevScriptsTest {

    @Test
    @DisplayName("INSTALL is idempotent via the __nmoxDevInstalled guard")
    void installIdempotenceGuard() {
        assertThat(DevScripts.INSTALL).contains("if(window.__nmoxDevInstalled){return 'already';}");
        assertThat(DevScripts.INSTALL).contains("window.__nmoxDevInstalled=true;");
    }

    @Test
    @DisplayName("the console wrapper preserves and still calls the original")
    void consoleWrapperPreservesOriginal() {
        assertThat(DevScripts.INSTALL).contains("console[level].bind(console)");
        assertThat(DevScripts.INSTALL).contains("orig.apply(null,arguments)");
        // all five levels wrapped
        assertThat(DevScripts.INSTALL).contains("['log','info','warn','error','debug']");
    }

    @Test
    @DisplayName("INSTALL embeds the caps: 8000 console chars, 500 URL, 20 method")
    void installCaps() {
        assertThat(DevScripts.INSTALL).contains("var CAP=8000;");
        assertThat(DevScripts.INSTALL).contains(".slice(0,500)");
        assertThat(DevScripts.INSTALL).contains(".slice(0,20)");
        assertThat(DevScripts.INSTALL).contains("[truncated]");
    }

    @Test
    @DisplayName("INSTALL wraps fetch, XHR, onerror, and unhandledrejection")
    void installCoverage() {
        assertThat(DevScripts.INSTALL).contains("window.fetch=function");
        assertThat(DevScripts.INSTALL).contains("XMLHttpRequest.prototype.open=function");
        assertThat(DevScripts.INSTALL).contains("XMLHttpRequest.prototype.send=function");
        assertThat(DevScripts.INSTALL).contains("addEventListener('error'");
        assertThat(DevScripts.INSTALL).contains("addEventListener('unhandledrejection'");
        // the bridge methods it calls
        assertThat(DevScripts.INSTALL).contains("B.log(");
        assertThat(DevScripts.INSTALL).contains("B.net(");
        assertThat(DevScripts.INSTALL).contains("B.err(");
    }

    @Test
    @DisplayName("DOM snapshot embeds depth 30, 5000 nodes, 200 attr chars")
    void domSnapshotCaps() {
        assertThat(DevScripts.DOM_SNAPSHOT).contains("MAX_DEPTH=30");
        assertThat(DevScripts.DOM_SNAPSHOT).contains("MAX_NODES=5000");
        assertThat(DevScripts.DOM_SNAPSHOT).contains("ATTR_CAP=200");
        assertThat(DevScripts.DOM_SNAPSHOT).contains("' more'"); // the honest placeholder
    }

    @Test
    @DisplayName("Vue snapshot embeds depth 25, 2000 components, 2000 value chars")
    void vueSnapshotCaps() {
        assertThat(DevScripts.VUE_SNAPSHOT).contains("MAX_DEPTH=25");
        assertThat(DevScripts.VUE_SNAPSHOT).contains("MAX_COMP=2000");
        assertThat(DevScripts.VUE_SNAPSHOT).contains("VAL_CAP=2000");
    }

    @Test
    @DisplayName("Vue snapshot recognizes both version markers (and the fallback)")
    void vueVersionMarkers() {
        assertThat(DevScripts.VUE_SNAPSHOT).contains("__vue_app__");   // Vue 3
        assertThat(DevScripts.VUE_SNAPSHOT).contains("__vue__");        // Vue 2
        assertThat(DevScripts.VUE_SNAPSHOT).contains("__vueParentComponent"); // Vue 3 fallback
        // name resolution chain for Vue 3 components
        assertThat(DevScripts.VUE_SNAPSHOT).contains("t.name");
        assertThat(DevScripts.VUE_SNAPSHOT).contains("t.__name");
        assertThat(DevScripts.VUE_SNAPSHOT).contains("t.__file");
        assertThat(DevScripts.VUE_SNAPSHOT).contains("'Anonymous'");
        // Vue 2 naming + walk
        assertThat(DevScripts.VUE_SNAPSHOT).contains("_componentTag");
        assertThat(DevScripts.VUE_SNAPSHOT).contains("$children");
    }

    @Test
    @DisplayName("storage snapshot embeds the 500-char cap and all three areas")
    void storageSnapshot() {
        assertThat(DevScripts.STORAGE_SNAPSHOT).contains("CAP=500");
        assertThat(DevScripts.STORAGE_SNAPSHOT).contains("localStorage");
        assertThat(DevScripts.STORAGE_SNAPSHOT).contains("sessionStorage");
        assertThat(DevScripts.STORAGE_SNAPSHOT).contains("document.cookie");
    }

    @Test
    @DisplayName("quote() makes untrusted text a safe JS string literal")
    void quoteEscapes() {
        assertThat(DevScripts.quote("plain")).isEqualTo("\"plain\"");
        assertThat(DevScripts.quote("a\"b")).isEqualTo("\"a\\\"b\"");
        assertThat(DevScripts.quote("a\\b")).isEqualTo("\"a\\\\b\"");
        assertThat(DevScripts.quote("a\nb\tc\rd")).isEqualTo("\"a\\nb\\tc\\rd\"");
        assertThat(DevScripts.quote("</script>")).isEqualTo("\"\\u003c/script>\"");
        assertThat(DevScripts.quote("x" + (char) 2)).isEqualTo("\"x\\u0002\"");
        assertThat(DevScripts.quote(null)).isEqualTo("\"\"");
        // U+2028/9 are JS line terminators — must not survive raw
        assertThat(DevScripts.quote("a" + (char) 0x2028)).isEqualTo("\"a\\u2028\"");
    }

    @Test
    @DisplayName("evalScript embeds the quoted expression and the error marker")
    void evalScriptShape() {
        String js = DevScripts.evalScript("document.title + \"!\"");
        assertThat(js).contains("window.eval(\"document.title + \\\"!\\\"\")");
        assertThat(js).contains(DevScripts.EVAL_ERROR_MARKER);
        assertThat(js).contains("8000"); // result cap
        assertThat(js).contains("[circular]");
        assertThat(js).contains("[function]");
    }

    @Test
    @DisplayName("highlight() embeds the path and the single reusable overlay")
    void highlightShape() {
        String js = DevScripts.highlight(List.of(1, 0, 3));
        assertThat(js).contains("var path=[1,0,3];");
        assertThat(js).contains("__nmox_hl");
        assertThat(js).contains("2px solid");
        assertThat(js).contains("pointer-events:none");
        assertThat(js).contains("'gone'"); // stale path fails soft
        // hostile path values clamp; null path is empty
        assertThat(DevScripts.highlight(null)).contains("var path=[];");
        assertThat(DevScripts.highlight(List.of(-5))).contains("var path=[0];");
    }

    @Test
    @DisplayName("computedStyle() asks for exactly the curated StyleSummary keys")
    void computedStyleUsesCuratedKeys() {
        String js = DevScripts.computedStyle(List.of(0));
        for (String key : StyleSummary.KEYS) {
            assertThat(js).contains("'" + key + "'");
        }
        assertThat(js).contains("var path=[0];");
    }
}
