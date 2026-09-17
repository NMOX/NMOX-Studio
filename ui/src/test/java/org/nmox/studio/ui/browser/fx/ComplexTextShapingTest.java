package org.nmox.studio.ui.browser.fx;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two classes the Browser's shaping writes at runtime (v2.165.0), proven by
 * RUNNING them: a stand-in for WebKit's {@code WCGraphicsPrismContext} whose
 * {@code drawString} records the x it paints at, rewritten exactly as the
 * installer rewrites the real one, and the generated hook it calls.
 */
class ComplexTextShapingTest {

    // v2.174.0 (arc review): the fit solves for a share the WIDTH HOOK applies, and
    // the hook scales only what `ComplexScripts.shapes` accepts — punctuation keeps
    // WebKit's own width. Summing an unscaled code point into the fit's plain sum
    // solves a different equation than the one that runs, and `clearlyBetter` — the
    // only gate deciding whether a fit replaces a measured constant — would be
    // judging an error nothing ever computes. Tibetan's corpus is a third tsheg
    // (U+0F0B, punctuation), and its constant is 1.00, the one value at which the
    // two formulas agree, which is why no picture could show it.
    @Test
    @DisplayName("the corpus fit skips every code point the width hook does not scale")
    void fitCountsOnlyWhatTheHookScales() throws Exception {
        String src = java.nio.file.Files.readString(java.nio.file.Path.of(
                "src/main/java/org/nmox/studio/ui/browser/fx/ComplexTextShaping.java"));
        int from = src.indexOf("private FontFit.Fit fit(Object pg");
        assertThat(from).as("the fit loop is still where this law lives").isPositive();
        String fit = src.substring(from, src.indexOf("\n        }\n", from));
        assertThat(fit)
                .as("the fit's per-code-point loop must consult the same predicate the width hook does")
                .contains("ComplexScripts.shapes(cp)");
        assertThat(ComplexScripts.shapes(0x0F0B))
                .as("the tsheg is exactly the kind of code point the hook leaves alone").isFalse();
    }

    private static final String FONT = "com/sun/webkit/graphics/WCFont";

    /** A class loader that defines exactly the bytes it is given. */
    private static final class Bytes extends ClassLoader {
        private final Map<String, byte[]> classes = new HashMap<>();

        Bytes() {
            super(ComplexTextShapingTest.class.getClassLoader());
        }

        void add(String internalName, byte[] bytes) {
            classes.put(internalName.replace('/', '.'), bytes);
        }

        /** Child-first: the test classpath carries the REAL JavaFX classes of these names. */
        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                byte[] b = classes.get(name);
                if (b == null) {
                    return super.loadClass(name, resolve);
                }
                Class<?> c = findLoadedClass(name);
                return c != null ? c : defineClass(name, b, 0, b.length);
            }
        }
    }

    static byte[] emptyClass(String name) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, name, null, "java/lang/Object", null);
        constructor(cw);
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static void constructor(ClassWriter cw) {
        MethodVisitor init = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        init.visitCode();
        init.visitVarInsn(Opcodes.ALOAD, 0);
        init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
        init.visitInsn(Opcodes.RETURN);
        init.visitMaxs(0, 0);
        init.visitEnd();
    }

    /** WebKit's context in miniature: both drawString overloads record where they paint. */
    static byte[] context() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, ComplexTextShaping.CONTEXT, null,
                "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "lastX", "F", null, null).visitEnd();
        constructor(cw);
        MethodVisitor glyphs = cw.visitMethod(Opcodes.ACC_PUBLIC, "drawString", ComplexTextShaping.DRAW_GLYPHS, null, null);
        glyphs.visitCode();
        glyphs.visitVarInsn(Opcodes.FLOAD, 4);
        glyphs.visitFieldInsn(Opcodes.PUTSTATIC, ComplexTextShaping.CONTEXT, "lastX", "F");
        glyphs.visitInsn(Opcodes.RETURN);
        glyphs.visitMaxs(0, 0);
        glyphs.visitEnd();
        MethodVisitor text = cw.visitMethod(Opcodes.ACC_PUBLIC, "drawString",
                "(L" + FONT + ";Ljava/lang/String;ZIIFF)V", null, null);
        text.visitCode();
        text.visitVarInsn(Opcodes.FLOAD, 6);
        text.visitFieldInsn(Opcodes.PUTSTATIC, ComplexTextShaping.CONTEXT, "lastX", "F");
        text.visitInsn(Opcodes.RETURN);
        text.visitMaxs(0, 0);
        text.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** WebKit's font in miniature: getGlyphWidth answers glyph * 2, as JavaFX's own width would. */
    static byte[] fontImpl() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, ComplexTextShaping.FONT_IMPL, null,
                "java/lang/Object", null);
        constructor(cw);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, ComplexTextShaping.GLYPH_WIDTH,
                ComplexTextShaping.GLYPH_WIDTH_DESC, null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ILOAD, 1);
        mv.visitInsn(Opcodes.ICONST_2);
        mv.visitInsn(Opcodes.IMUL);
        mv.visitInsn(Opcodes.I2D);
        mv.visitInsn(Opcodes.DRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    private static Object[] loadFont(boolean[] applied) throws Exception {
        Bytes loader = new Bytes();
        loader.add(ComplexTextShaping.HOOK, ComplexTextShaping.hookClass());
        loader.add(ComplexTextShaping.FONT_IMPL, ComplexTextShaping.rewriteWidths(fontImpl(), applied));
        Class<?> font = loader.loadClass(ComplexTextShaping.FONT_IMPL.replace('/', '.'));
        Class<?> hook = loader.loadClass(ComplexTextShaping.HOOK.replace('/', '.'));
        return new Object[]{font, hook};
    }

    @Test
    @DisplayName("the rewritten glyph width answers the hook's width, and the font's own when the hook says NaN or is unset")
    void rewrittenWidthAsksTheHookFirst() throws Exception {
        boolean[] applied = {false};
        Object[] loaded = loadFont(applied);
        Class<?> font = (Class<?>) loaded[0];
        Class<?> hook = (Class<?>) loaded[1];
        assertThat(applied[0]).isTrue();
        Object instance = font.getConstructor().newInstance();
        java.lang.reflect.Method width = font.getMethod(ComplexTextShaping.GLYPH_WIDTH, int.class);
        assertThat((Double) width.invoke(instance, 21)).isEqualTo(42d); // no hook yet: the font's own
        AtomicReference<Object[]> seen = new AtomicReference<>();
        hook.getField("WIDTHS").set(null, (Function<Object[], Object>) args -> {
            seen.set(args);
            return (Integer) args[1] == 7 ? 3.5d : Double.NaN;
        });
        assertThat((Double) width.invoke(instance, 7)).isEqualTo(3.5d);
        assertThat(seen.get()).containsExactly(instance, 7);
        assertThat((Double) width.invoke(instance, 8)).isEqualTo(16d);  // NaN: the font's own
    }

    private record Loaded(Class<?> context, Class<?> font, Class<?> hook, boolean applied) {
    }

    private static Loaded load() throws Exception {
        Bytes loader = new Bytes();
        boolean[] applied = {false};
        loader.add(FONT, emptyClass(FONT));
        loader.add(ComplexTextShaping.HOOK, ComplexTextShaping.hookClass());
        loader.add(ComplexTextShaping.CONTEXT, ComplexTextShaping.rewrite(context(), applied));
        return new Loaded(loader.loadClass(ComplexTextShaping.CONTEXT.replace('/', '.')),
                loader.loadClass(FONT.replace('/', '.')),
                loader.loadClass(ComplexTextShaping.HOOK.replace('/', '.')), applied[0]);
    }

    private static float paintGlyphs(Loaded l, Object font, int[] glyphs, float[] advances, float x) throws Exception {
        Object ctx = l.context().getConstructor().newInstance();
        l.context().getMethod("drawString", l.font(), int[].class, float[].class, float.class, float.class)
                .invoke(ctx, font, glyphs, advances, x, 0f);
        return l.context().getField("lastX").getFloat(null);
    }

    @Test
    @DisplayName("the rewritten glyph draw hands font, glyphs and advances to the shaper and paints at x plus its answer")
    void rewrittenDrawMovesByTheShapersAnswer() throws Exception {
        Loaded l = load();
        assertThat(l.applied()).isTrue();
        AtomicReference<Object[]> seen = new AtomicReference<>();
        l.hook().getField("SHAPER").set(null, (Function<Object[], Object>) args -> {
            seen.set(args);
            return 12.5f;
        });
        Object font = l.font().getConstructor().newInstance();
        int[] glyphs = {1, 2};
        float[] advances = {3f, 4f};
        assertThat(paintGlyphs(l, font, glyphs, advances, 100f)).isEqualTo(112.5f);
        assertThat(seen.get()).containsExactly(font, glyphs, advances);
    }

    @Test
    @DisplayName("until a shaper is set, the rewritten draw paints exactly where it always did")
    void withoutAShaperNothingMoves() throws Exception {
        Loaded l = load();
        assertThat(paintGlyphs(l, l.font().getConstructor().newInstance(), new int[]{1}, new float[]{1f}, 40f))
                .isEqualTo(40f);
    }

    @Test
    @DisplayName("only the glyph-array draw is rewritten; the string draw is left as JavaFX wrote it")
    void theStringDrawIsUntouched() throws Exception {
        Loaded l = load();
        l.hook().getField("SHAPER").set(null, (Function<Object[], Object>) args -> {
            throw new AssertionError("the string overload must not call the shaper");
        });
        Object ctx = l.context().getConstructor().newInstance();
        l.context().getMethod("drawString", l.font(), String.class, boolean.class, int.class, int.class,
                float.class, float.class).invoke(ctx, null, "x", false, 0, 1, 7f, 0f);
        assertThat(l.context().getField("lastX").getFloat(null)).isEqualTo(7f);
    }

    @Test
    @DisplayName("a class without the glyph draw reports nothing applied, so the installer can say so")
    void aChangedClassIsReported() {
        boolean[] applied = {true};
        applied[0] = false;
        ComplexTextShaping.rewrite(emptyClass(ComplexTextShaping.CONTEXT), applied);
        assertThat(applied[0]).isFalse();
    }

    /** JavaFX's TextRun in miniature: each shape overload records what it was built from. */
    static byte[] textRun() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, ComplexTextShaping.TEXT_RUN, null,
                "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC, "advances", "[F", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "positions", "[F", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "gids", "[I", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC, "count", "I", null, null).visitEnd();
        constructor(cw);
        MethodVisitor adv = cw.visitMethod(Opcodes.ACC_PUBLIC, ComplexTextShaping.RUN_SHAPE,
                ComplexTextShaping.RUN_SHAPE_DESC, null, null);
        adv.visitCode();
        adv.visitVarInsn(Opcodes.ALOAD, 0);
        adv.visitVarInsn(Opcodes.ALOAD, 3);
        adv.visitFieldInsn(Opcodes.PUTFIELD, ComplexTextShaping.TEXT_RUN, "advances", "[F");
        adv.visitInsn(Opcodes.RETURN);
        adv.visitMaxs(0, 0);
        adv.visitEnd();
        MethodVisitor pos = cw.visitMethod(Opcodes.ACC_PUBLIC, ComplexTextShaping.RUN_SHAPE, "(I[I[F[I)V", null, null);
        pos.visitCode();
        pos.visitVarInsn(Opcodes.ALOAD, 0);
        pos.visitVarInsn(Opcodes.ALOAD, 3);
        pos.visitFieldInsn(Opcodes.PUTFIELD, ComplexTextShaping.TEXT_RUN, "positions", "[F");
        pos.visitVarInsn(Opcodes.ALOAD, 0);
        pos.visitVarInsn(Opcodes.ALOAD, 2);
        pos.visitFieldInsn(Opcodes.PUTFIELD, ComplexTextShaping.TEXT_RUN, "gids", "[I");
        pos.visitVarInsn(Opcodes.ALOAD, 0);
        pos.visitVarInsn(Opcodes.ILOAD, 1);
        pos.visitFieldInsn(Opcodes.PUTFIELD, ComplexTextShaping.TEXT_RUN, "count", "I");
        pos.visitInsn(Opcodes.RETURN);
        pos.visitMaxs(0, 0);
        pos.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /** WebKit's TextUtilities in miniature: createGlyphList builds a run from the advances, as the real one does. */
    static byte[] textUtilities() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, ComplexTextShaping.TEXT_UTILITIES, null,
                "java/lang/Object", null);
        constructor(cw);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, ComplexTextShaping.CREATE_GLYPH_LIST,
                "([I[FFF)L" + ComplexTextShaping.TEXT_RUN + ";", null, null);
        mv.visitCode();
        mv.visitTypeInsn(Opcodes.NEW, ComplexTextShaping.TEXT_RUN);
        mv.visitInsn(Opcodes.DUP);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, ComplexTextShaping.TEXT_RUN, "<init>", "()V", false);
        mv.visitVarInsn(Opcodes.ASTORE, 4);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitInsn(Opcodes.ARRAYLENGTH);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, ComplexTextShaping.TEXT_RUN, ComplexTextShaping.RUN_SHAPE,
                ComplexTextShaping.RUN_SHAPE_DESC, false);
        mv.visitVarInsn(Opcodes.ALOAD, 4);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("the rewritten glyph-list build takes positions from the placer when it has them, advances otherwise")
    void rewrittenGlyphListTakesPositionsFromThePlacer() throws Exception {
        Bytes loader = new Bytes();
        boolean[] applied = {false};
        loader.add(ComplexTextShaping.TEXT_RUN, textRun());
        loader.add(ComplexTextShaping.HOOK, ComplexTextShaping.hookClass());
        loader.add(ComplexTextShaping.TEXT_UTILITIES, ComplexTextShaping.rewritePlacement(textUtilities(), applied));
        assertThat(applied[0]).isTrue();
        Class<?> utilities = loader.loadClass(ComplexTextShaping.TEXT_UTILITIES.replace('/', '.'));
        Class<?> run = loader.loadClass(ComplexTextShaping.TEXT_RUN.replace('/', '.'));
        Class<?> hook = loader.loadClass(ComplexTextShaping.HOOK.replace('/', '.'));
        java.lang.reflect.Method build = utilities.getMethod(ComplexTextShaping.CREATE_GLYPH_LIST,
                int[].class, float[].class, float.class, float.class);
        int[] glyphs = {1, 2};
        float[] advances = {3f, 4f};

        Object plain = build.invoke(null, glyphs, advances, 0f, 0f); // no placer yet
        assertThat(run.getField("advances").get(plain)).isSameAs(advances);
        assertThat(run.getField("positions").get(plain)).isNull();

        int[] laid = {7, 8, 9};  // shaping took more glyphs than WebKit painted
        float[] lifted = {0f, -5f, 3f, 2f, 7f, 0f, 9f, 0f};
        AtomicReference<Object[]> seen = new AtomicReference<>();
        hook.getField("PLACER").set(null, (Function<Object[], Object>) args -> {
            seen.set(args);
            return args[0] == glyphs ? new Object[]{laid, lifted} : null;
        });
        Object placed = build.invoke(null, glyphs, advances, 0f, 0f);
        assertThat(seen.get()).containsExactly(glyphs, advances);
        assertThat(run.getField("positions").get(placed)).isSameAs(lifted);
        assertThat(run.getField("gids").get(placed)).isSameAs(laid);
        assertThat(run.getField("count").getInt(placed)).isEqualTo(3);
        assertThat(run.getField("advances").get(placed)).isNull();

        float[] other = {9f};
        Object unplaced = build.invoke(null, new int[]{5}, other, 0f, 0f); // the placer has nothing for it
        assertThat(run.getField("advances").get(unplaced)).isSameAs(other);
        assertThat(run.getField("positions").get(unplaced)).isNull();
    }

    @Test
    @DisplayName("a TextUtilities without that call reports nothing applied")
    void aChangedGlyphListBuildIsReported() {
        boolean[] applied = {false};
        ComplexTextShaping.rewritePlacement(emptyClass(ComplexTextShaping.TEXT_UTILITIES), applied);
        assertThat(applied[0]).isFalse();
    }

    /** WebKit's font in miniature, the text-runs half: getTextRuns records the string it lays out. */
    static byte[] fontWithTextRuns(boolean withMethod) {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER, ComplexTextShaping.FONT_IMPL, null,
                "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "laidOut", "Ljava/lang/String;", null, null).visitEnd();
        constructor(cw);
        if (withMethod) {
            MethodVisitor runs = cw.visitMethod(Opcodes.ACC_PUBLIC, ComplexTextShaping.TEXT_RUNS,
                    ComplexTextShaping.TEXT_RUNS_DESC, null, null);
            runs.visitCode();
            runs.visitVarInsn(Opcodes.ALOAD, 1);
            runs.visitFieldInsn(Opcodes.PUTSTATIC, ComplexTextShaping.FONT_IMPL, "laidOut", "Ljava/lang/String;");
            runs.visitInsn(Opcodes.ACONST_NULL);
            runs.visitInsn(Opcodes.ARETURN);
            runs.visitMaxs(0, 0);
            runs.visitEnd();
        }
        cw.visitEnd();
        return cw.toByteArray();
    }

    @Test
    @DisplayName("the rewritten getTextRuns lays out the stripper's answer, and the string as given until a stripper is set")
    void rewrittenTextRunsLayOutTheStrippedString() throws Exception {
        boolean[] applied = {false};
        Bytes loader = new Bytes();
        loader.add(ComplexTextShaping.HOOK, ComplexTextShaping.hookClass());
        loader.add(ComplexTextShaping.FONT_IMPL, ComplexTextShaping.rewriteTextRuns(fontWithTextRuns(true), applied));
        // the return type, stood in too: reflection resolves it, and the real one needs JavaFX's JDK
        loader.add("com/sun/webkit/graphics/WCTextRun", emptyClass("com/sun/webkit/graphics/WCTextRun"));
        Class<?> font = loader.loadClass(ComplexTextShaping.FONT_IMPL.replace('/', '.'));
        Class<?> hook = loader.loadClass(ComplexTextShaping.HOOK.replace('/', '.'));
        assertThat(applied[0]).isTrue();
        Object instance = font.getConstructor().newInstance();
        java.lang.reflect.Method runs = font.getMethod(ComplexTextShaping.TEXT_RUNS, String.class);
        java.lang.reflect.Field laidOut = font.getField("laidOut");
        runs.invoke(instance, "\u0633\u0627\u06443");
        assertThat(laidOut.get(null)).isEqualTo("\u0633\u0627\u06443"); // no stripper yet
        hook.getField("STRIPPER").set(null, (Function<String, String>) WebKitTextPath::stripLengthSuffix);
        runs.invoke(instance, "\u0633\u0627\u06443");
        assertThat(laidOut.get(null)).isEqualTo("\u0633\u0627\u0644");
    }

    @Test
    @DisplayName("a font without getTextRuns reports nothing applied, so the native path is not switched on")
    void aFontWithoutTextRunsIsReported() {
        boolean[] applied = {false};
        ComplexTextShaping.rewriteTextRuns(fontWithTextRuns(false), applied);
        assertThat(applied[0]).isFalse();
    }

    @Test
    @DisplayName("the strip-only transformer rewrites getTextRuns and leaves the widths, the glyph list and the paint alone")
    void stripOnlyTransformerTouchesTextRunsAlone() throws Exception {
        ComplexTextShaping.Transformer strip = new ComplexTextShaping.Transformer(false);
        byte[] font = strip.transform(null, null, ComplexTextShaping.FONT_IMPL, null, null, fontWithTextRuns(true));
        assertThat(font).isNotNull();
        assertThat(strip.textRunsApplied).isTrue();
        assertThat(strip.widthsApplied).isFalse();
        assertThat(strip.transform(null, null, ComplexTextShaping.CONTEXT, null, null, context())).isNull();
        assertThat(strip.transform(null, null, ComplexTextShaping.TEXT_UTILITIES, null, null, emptyClass(ComplexTextShaping.TEXT_UTILITIES))).isNull();
        // the repair keeps the strip when asked, since a retransform replays the original bytes
        ComplexTextShaping.Transformer repair = new ComplexTextShaping.Transformer(true);
        repair.stripRuns = true;
        repair.transform(null, null, ComplexTextShaping.FONT_IMPL, null, null, fontWithTextRuns(true));
        assertThat(repair.textRunsApplied).isTrue();
        ComplexTextShaping.Transformer plain = new ComplexTextShaping.Transformer(true);
        plain.transform(null, null, ComplexTextShaping.FONT_IMPL, null, null, fontWithTextRuns(true));
        assertThat(plain.textRunsApplied).isFalse();
    }
}
