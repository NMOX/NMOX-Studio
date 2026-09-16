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
}
