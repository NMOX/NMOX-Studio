package org.nmox.studio.ui.browser.fx;

import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Turns on WebKit's own text shaping in the Browser (v2.172.0).
 *
 * <p>OpenJFX's WebKit has a complete complex-text path: it hands each string
 * to {@code WCFontImpl.getTextRuns}, which lays it out with JavaFX's text
 * engine, and paints the shaped glyphs it gets back, measured exactly. It never
 * takes that path, because {@code FontCascade.cpp} starts the port with
 * {@code s_codePath = CodePath::Simple} ({@code #if PLATFORM(JAVA)}), which
 * sends every string down the per-character path whatever script it is in.
 * {@link ComplexTextShaping} has repaired that simple path from the outside
 * since v2.165.0, with widths WebKit can only estimate one glyph at a time.
 *
 * <p>For a WebKit library whose exact bytes are known, this class sets the code
 * path to {@code Auto} with WebKit's own {@code FontCascade::setCodePath},
 * after reading the value it guards and finding {@code Simple} there, and reads
 * {@code Auto} back afterwards. WebKit then shapes, measures, selects and wraps
 * complex text itself: form fields, bold and italic runs, justified lines and
 * decomposed accents all come out right, which no estimate could. The library
 * is identified by SHA-256; any other build, platform or runtime keeps the
 * repaired simple path.
 *
 * <p>The complex path carries one OpenJFX defect of its own: the native glue
 * builds the string it passes to {@code getTextRuns} with
 * {@code makeString(characters, characters.size())}, which appends the length
 * as decimal digits ({@code "سال"} arrives as {@code "سال3"}), so every shaped
 * string grew by the width of those digits. {@link #stripLengthSuffix} removes
 * them; it is installed only together with the switch, for the same known
 * builds, because on a fixed library a string that truly ends in its own
 * length would lose characters.
 */
final class WebKitTextPath {

    private static final Logger LOG = Logger.getLogger(WebKitTextPath.class.getName());

    /** {@code enum class CodePath : uint8_t { Auto, Simple, Complex, SimpleWithGlyphOverflow }}. */
    static final byte AUTO = 0;
    static final byte SIMPLE = 1;

    /**
     * One WebKit library whose code-path switch has been located, read from its
     * disassembly. Offsets are relative to {@code anchor}, an exported symbol the
     * loader can find: the setter is {@code FontCascade::setCodePath} and the
     * state is the {@code s_codePath} byte that setter writes. {@code jmods} is
     * the sha256 of the OpenJFX jmods archive the library came from, the pin the
     * release lanes download by; {@code WebKitTextPathTest} holds the two equal,
     * so a JavaFX bump cannot ship with offsets measured on another build.
     */
    record Build(String platform, String jmods, String sha256, String library, String anchor,
            long setterOffset, long stateOffset) {
    }

    /**
     * The builds the release bundles: OpenJFX 26.0.2's {@code javafx.web} jmods,
     * pinned by {@code packaging/tools/bundle-jre.sh} and the Windows release lane.
     * macOS arm64: the setter and its getter sit just before
     * {@code characterRangeCodePath} ({@code strb w0,[x8,#0xf58]} on
     * {@code 0x675af58}), anchored on an exported JNI entry. Windows x64 exports
     * the setter by name ({@code movb %cl, 0x501b26e(%rip)}). The Linux build
     * reads no such byte anywhere near its font code, so it keeps the simple path.
     *
     * <p>The library paths are where {@code jlink} puts each jmod's native library:
     * it drops the first path segment and then, on Windows only, files a {@code .dll}
     * under {@code bin} — so the jmod's {@code lib/javafx/jfxwebkit.dll} becomes
     * {@code bin/javafx/jfxwebkit.dll}, while {@code lib/libjfxwebkit.dylib} and
     * {@code lib/libjfxwebkit.so} stay under {@code lib}. v2.172.0 shipped
     * {@code bin/jfxwebkit.dll} and so never found the Windows library.
     */
    static final List<Build> KNOWN = List.of(
            new Build("osx-aarch64", "ed6ac7d8d056b29fa221edb029ed232eb54f3a7068c4d4e1304faf99f8d93285",
                    "fb270b8c231f71095f773a368ed67939d0c8d12dd17fa1841e11caf3edc749fd",
                    "lib/libjfxwebkit.dylib", "Java_com_sun_webkit_WebPage_twkInitWebCore",
                    0x1c405acL - 0xa938cL, 0x675af58L - 0xa938cL),
            new Build("windows-x64", "8554a293273eac20d172c18455fcf154a54b7879d3f00de28344ebefd8978672",
                    "1fc1f628b312f38a5fd9c8463bfbd1e3d89d3c4416fe25818f04ea1f8efdeef7",
                    "bin/javafx/jfxwebkit.dll", "?setCodePath@FontCascade@WebCore@@SAXW4CodePath@12@@Z",
                    0L, 0x185a2db64L - 0x180a128f0L));

    private WebKitTextPath() {
    }

    /** {@code osx-aarch64}, {@code windows-x64}, {@code linux-x64}…, in OpenJFX's own spelling. */
    static String platform(String osName, String osArch) {
        String os = osName.toLowerCase(Locale.ROOT);
        String family = os.startsWith("mac") ? "osx" : os.startsWith("windows") ? "windows" : os.startsWith("linux") ? "linux" : os;
        String arch = osArch.toLowerCase(Locale.ROOT);
        String bits = arch.equals("aarch64") || arch.equals("arm64") ? "aarch64"
                : arch.equals("amd64") || arch.equals("x86_64") ? "x64" : arch;
        return family + "-" + bits;
    }

    /** The known build for this platform and library hash, or null. */
    static Build match(String platform, String sha256) {
        for (Build build : KNOWN) {
            if (build.platform().equals(platform) && build.sha256().equalsIgnoreCase(sha256)) {
                return build;
            }
        }
        return null;
    }

    /** The known build of the WebKit library this runtime carries, or null when it is not one. */
    static Build knownBuild(Path javaHome) {
        String platform = platform(System.getProperty("os.name", ""), System.getProperty("os.arch", ""));
        for (Build build : KNOWN) {
            if (!build.platform().equals(platform)) {
                continue;
            }
            Path library = javaHome.resolve(build.library());
            if (!Files.isRegularFile(library)) {
                // this platform has a known build and no library where it should be:
                // say so, because the only other sign is text that stays unshaped
                LOG.log(Level.INFO, "WebKit text path: no WebKit library at {0}; staying on the simple path", library);
                continue;
            }
            try {
                if (match(platform, sha256(library)) != null) {
                    return build;
                }
                LOG.log(Level.FINE, "WebKit text path: {0} is not a build this release knows", library);
            } catch (IOException ex) {
                LOG.log(Level.FINE, "could not read " + library, ex);
            }
        }
        return null;
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            for (int n; (n = in.read(buffer)) > 0; ) {
                digest.update(buffer, 0, n);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * The string WebKit meant to lay out: {@code text} without the decimal length
     * the native glue appended to it. The suffix is recognised by what it must be,
     * the length of what precedes it; anything else is returned unchanged.
     */
    static String stripLengthSuffix(String text) {
        if (text == null) {
            return null;
        }
        int n = text.length();
        for (int digits = 1; digits <= 10 && digits <= n; digits++) {
            String length = Integer.toString(n - digits);
            if (length.length() == digits && text.endsWith(length)) {
                return text.substring(0, n - digits);
            }
        }
        return text;
    }

    /** Where the setter and the state byte are, from the anchor's loaded address. */
    static long[] addresses(Build build, long anchorAddress) {
        return new long[]{anchorAddress + build.setterOffset(), anchorAddress + build.stateOffset()};
    }

    /**
     * Sets the code path to {@code Auto} through WebKit's own setter, only when the
     * state reads {@code Simple} first; true when it reads {@code Auto} after.
     * Needs the foreign-function API (JDK 22 and later) and native access; any
     * refusal leaves WebKit as it was and answers false.
     */
    static boolean switchOn(Path javaHome, Build build) {
        try {
            Foreign ffm = new Foreign();
            Object arena = ffm.globalArena.invoke(null);
            Object lookup = ffm.libraryLookup.invoke(null, javaHome.resolve(build.library()), arena);
            java.util.Optional<?> anchor = (java.util.Optional<?>) ffm.find.invoke(lookup, build.anchor());
            if (anchor.isEmpty()) {
                LOG.info("WebKit text path: the anchor symbol is missing; staying on the simple path");
                return false;
            }
            long[] at = addresses(build, (Long) ffm.address.invoke(anchor.get()));
            Object state = ffm.reinterpret.invoke(ffm.ofAddress.invoke(null, at[1]), 1L);
            byte before = (Byte) ffm.getByte.invoke(state, ffm.javaByte, 0L);
            if (before != SIMPLE) {
                LOG.log(Level.INFO, "WebKit text path: unexpected code path {0}; left alone", before);
                return false;
            }
            MethodHandle setter = ffm.downcall(ffm.ofAddress.invoke(null, at[0]));
            setter.invokeWithArguments(AUTO);
            byte after = (Byte) ffm.getByte.invoke(state, ffm.javaByte, 0L);
            if (after != AUTO) {
                LOG.log(Level.INFO, "WebKit text path: the setter did not take ({0})", after);
                return false;
            }
            return true;
        } catch (Throwable ex) { // NOPMD: any refusal of native access keeps the simple path
            if (ex instanceof VirtualMachineError vme && !(ex instanceof StackOverflowError)) {
                throw vme;
            }
            LOG.log(Level.INFO, "WebKit text path could not be switched on; staying on the simple path", ex);
            return false;
        }
    }

    /** The foreign-function API reached reflectively: this module compiles for Java 21. */
    private static final class Foreign {
        final Method globalArena;
        final Method libraryLookup;
        final Method find;
        final Method address;
        final Method ofAddress;
        final Method reinterpret;
        final Method getByte;
        final Object javaByte;
        private final Class<?> linkerClass;
        private final Class<?> segmentClass;
        private final Class<?> layoutClass;
        private final Class<?> descriptorClass;

        Foreign() throws ReflectiveOperationException {
            Class<?> arena = Class.forName("java.lang.foreign.Arena");
            Class<?> symbolLookup = Class.forName("java.lang.foreign.SymbolLookup");
            segmentClass = Class.forName("java.lang.foreign.MemorySegment");
            layoutClass = Class.forName("java.lang.foreign.ValueLayout");
            Class<?> ofByte = Class.forName("java.lang.foreign.ValueLayout$OfByte");
            linkerClass = Class.forName("java.lang.foreign.Linker");
            descriptorClass = Class.forName("java.lang.foreign.FunctionDescriptor");
            globalArena = arena.getMethod("global");
            libraryLookup = symbolLookup.getMethod("libraryLookup", Path.class, arena);
            find = symbolLookup.getMethod("find", String.class);
            address = segmentClass.getMethod("address");
            ofAddress = segmentClass.getMethod("ofAddress", long.class);
            reinterpret = segmentClass.getMethod("reinterpret", long.class);
            getByte = segmentClass.getMethod("get", ofByte, long.class);
            javaByte = layoutClass.getField("JAVA_BYTE").get(null);
        }

        /** {@code void setCodePath(uint8_t)}. */
        MethodHandle downcall(Object function) throws ReflectiveOperationException {
            Object linker = linkerClass.getMethod("nativeLinker").invoke(null);
            Class<?> memoryLayout = Class.forName("java.lang.foreign.MemoryLayout");
            Object layouts = java.lang.reflect.Array.newInstance(memoryLayout, 1);
            java.lang.reflect.Array.set(layouts, 0, javaByte);
            Object descriptor = descriptorClass.getMethod("ofVoid", layouts.getClass()).invoke(null, layouts);
            Class<?> options = Class.forName("java.lang.foreign.Linker$Option");
            Object noOptions = java.lang.reflect.Array.newInstance(options, 0);
            return (MethodHandle) linkerClass.getMethod("downcallHandle", segmentClass, descriptorClass, noOptions.getClass())
                    .invoke(linker, function, descriptor, noOptions);
        }
    }
}
