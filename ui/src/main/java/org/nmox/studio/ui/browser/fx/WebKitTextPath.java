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
        return match(KNOWN, platform, sha256);
    }

    /**
     * The entry whose platform AND bytes both match, or null.
     *
     * <p>Takes its candidates so the choice can be made over two entries for one
     * platform, which is what an OpenJFX patch bump adds and what this release's
     * table cannot yet show: two such entries name the SAME library path, so a
     * walk of the table reaches the first while the file on disk hashes as the
     * second — and the first's offsets inside the second's image are an arbitrary
     * call and an arbitrary write.
     */
    static Build match(List<Build> builds, String platform, String sha256) {
        for (Build build : builds) {
            if (build.platform().equals(platform) && build.sha256().equalsIgnoreCase(sha256)) {
                return build;
            }
        }
        return null;
    }

    /** The known build of the WebKit library this runtime carries, or null when it is not one. */
    static Build knownBuild(Path javaHome) {
        return knownBuild(javaHome, platform(System.getProperty("os.name", ""), System.getProperty("os.arch", "")));
    }

    /**
     * The same, for a named platform — so the rule that decides whether a runtime is
     * known can be exercised on a host that is not that platform. Linux has no entry
     * in {@code KNOWN}, so a Linux test of the one-argument form never reaches this
     * loop at all, and the property it proves (an unknown library keeps the repaired
     * simple path) would be proven on macOS only.
     */
    static Build knownBuild(Path javaHome, String platform) {
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
                // the entry that matched the BYTES, never the one whose path we walked
                Build found = match(platform, sha256(library));
                if (found != null) {
                    return found;
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
     * What a switch attempt left behind — because "it did not work" is two different
     * states and the caller must treat them differently.
     *
     * <p>The repaired simple path corrects widths that WebKit's own complex path
     * already measures exactly, so installing it over a switched WebKit is worse
     * than either alone: the same runs get shaped and re-measured twice. Only
     * {@link #UNTOUCHED} may fall back to it.
     */
    enum Switched {
        /** The setter ran and the state reads {@code Auto}: WebKit shapes for itself. */
        ON,
        /** Nothing was written (or it was written and put back): the simple path is free to be repaired. */
        UNTOUCHED,
        /** The setter ran and the state does not read back as written: leave WebKit alone. */
        UNCONFIRMED
    }

    /**
     * Whether the simple path may still be repaired after an attempt — true only for
     * {@link Switched#UNTOUCHED}.
     *
     * <p>The law in one place, because its two false cases look nothing alike and
     * both used to be a bare {@code false}: after {@link Switched#ON} WebKit shapes
     * and measures for itself, and after {@link Switched#UNCONFIRMED} it may be
     * doing so. Repairing over either measures every complex run twice.
     */
    static boolean repairable(Switched switched) {
        return switched == Switched.UNTOUCHED;
    }

    /**
     * Sets the code path to {@code Auto} through WebKit's own setter, only when the
     * state reads {@code Simple} first, and reads it back to confirm.
     *
     * <p>Needs the foreign-function API (JDK 22 and later) and native access. A
     * refusal BEFORE the setter runs leaves WebKit exactly as it was
     * ({@link Switched#UNTOUCHED}). Once the setter has run the byte may have
     * changed, so a failure past that point puts {@code Simple} back and only
     * reports {@code UNTOUCHED} when that is confirmed — otherwise
     * {@link Switched#UNCONFIRMED}, which no caller may repair over.
     *
     * <p>What this cannot catch: a wrong {@code setterOffset} calls an arbitrary
     * address, which is a segmentation fault and not a Java throwable. The sha256
     * pin on the library is the only thing standing between a user and that.
     */
    static Switched switchOn(Path javaHome, Build build) {
        boolean wrote = false;
        try {
            Foreign ffm = new Foreign();
            Object arena = ffm.globalArena.invoke(null);
            Object lookup = ffm.libraryLookup.invoke(null, javaHome.resolve(build.library()), arena);
            java.util.Optional<?> anchor = (java.util.Optional<?>) ffm.find.invoke(lookup, build.anchor());
            if (anchor.isEmpty()) {
                LOG.info("WebKit text path: the anchor symbol is missing; staying on the simple path");
                return Switched.UNTOUCHED;
            }
            long[] at = addresses(build, (Long) ffm.address.invoke(anchor.get()));
            Object state = ffm.reinterpret.invoke(ffm.ofAddress.invoke(null, at[1]), 1L);
            byte before = (Byte) ffm.getByte.invoke(state, ffm.javaByte, 0L);
            if (before == AUTO) {
                // already switched on in this JVM: the caller must not repair over it
                LOG.info("WebKit text path: already on WebKit's own path; nothing to do");
                return Switched.UNCONFIRMED;
            }
            if (before != SIMPLE) {
                LOG.log(Level.INFO, "WebKit text path: unexpected code path {0}; left alone", before);
                return Switched.UNTOUCHED;
            }
            MethodHandle setter = ffm.downcall(ffm.ofAddress.invoke(null, at[0]));
            wrote = true;
            setter.invokeWithArguments(AUTO);
            byte after = (Byte) ffm.getByte.invoke(state, ffm.javaByte, 0L);
            if (after != AUTO) {
                // The byte was written and does not read back as written, so this build
                // is not where we think it is. Writing AGAIN to put Simple back would be
                // a second write at an address already shown to be wrong — the one thing
                // not to do. Stop, and let the caller know not to repair over it.
                LOG.log(Level.WARNING, "WebKit text path: the code-path byte was written and reads {0}; "
                        + "the simple-path repair is NOT installed, because repairing a path WebKit may "
                        + "already be shaping would measure every complex run twice", after);
                return Switched.UNCONFIRMED;
            }
            return Switched.ON;
        } catch (Throwable ex) { // NOPMD: any refusal of native access keeps the simple path
            if (ex instanceof VirtualMachineError vme && !(ex instanceof StackOverflowError)) {
                throw vme;
            }
            LOG.log(Level.INFO, "WebKit text path could not be switched on; staying on the simple path", ex);
            return wrote ? Switched.UNCONFIRMED : Switched.UNTOUCHED;
        }
    }

    /** The foreign-function API reached reflectively: this module compiles for Java 21. */
    /** Package-private so a test can prove every java.lang.foreign signature still resolves. */
    static final class Foreign {
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
