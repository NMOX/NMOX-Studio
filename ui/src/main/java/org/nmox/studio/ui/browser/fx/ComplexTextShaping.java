package org.nmox.studio.ui.browser.fx;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.Instrumentation;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Makes the Browser's WebKit paint Arabic and Indic scripts shaped (v2.165.0).
 *
 * <p>OpenJFX's WebKit never shapes these scripts (ledger 99, measured on every
 * release from 17 to 26): its paint call receives each character's plain glyph.
 * This class leaves OpenJFX's files alone and changes one method in memory. On
 * the Browser's first build it attaches {@link ShapingAgent} to its own process
 * (the launcher's conf allows that with {@code jdk.attach.allowAttachSelf} and
 * {@code EnableDynamicAgentLoading}), opens the JavaFX packages it reads, defines
 * a one-method hook class inside {@code javafx.web}, and retransforms
 * {@code WCGraphicsPrismContext.drawString(WCFont, int[], float[], float, float)}
 * so its first act is to hand the glyphs to that hook. The hook calls
 * {@link ComplexScripts}, which shapes them with JavaFX's own text layout, the
 * one that already shapes these scripts correctly in every JavaFX control.
 * Since v2.167.0 it also rewrites the one call in
 * {@code TextUtilities.createGlyphList} that builds the painted run from
 * advances alone, so a run whose shaped glyphs leave the baseline (vowel marks,
 * Nastaliq) is built from x/y positions instead.
 *
 * <p>Every step refuses quietly and says so once in the log: no JavaFX, a
 * runtime or launcher that does not allow attaching, a JavaFX whose method has
 * changed shape. The Browser then paints exactly as before.
 */
public final class ComplexTextShaping {

    private static final Logger LOG = Logger.getLogger(ComplexTextShaping.class.getName());

    static final String PRISM_PACKAGE = "com.sun.javafx.webkit.prism";
    static final String CONTEXT = "com/sun/javafx/webkit/prism/WCGraphicsPrismContext";
    static final String HOOK = "com/sun/javafx/webkit/prism/NmoxComplexText";
    static final String DRAW = "drawString";
    static final String DRAW_GLYPHS = "(Lcom/sun/webkit/graphics/WCFont;[I[FFF)V";
    static final String RESHAPE = "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)F";
    static final String FONT_IMPL = "com/sun/javafx/webkit/prism/WCFontImpl";
    static final String GLYPH_WIDTH = "getGlyphWidth";
    static final String GLYPH_WIDTH_DESC = "(I)D";
    static final String WIDTH = "(Ljava/lang/Object;I)D";
    static final String TEXT_UTILITIES = "com/sun/javafx/webkit/prism/TextUtilities";
    static final String CREATE_GLYPH_LIST = "createGlyphList";
    static final String TEXT_RUN = "com/sun/javafx/text/TextRun";
    static final String RUN_SHAPE = "shape";
    static final String RUN_SHAPE_DESC = "(I[I[F)V";
    static final String SHAPE_RUN = "(L" + TEXT_RUN + ";I[I[F)V";

    /** What happened, for the log line and the tests. */
    public enum Outcome { INSTALLED, NO_JAVAFX, NO_ATTACH, METHOD_CHANGED, FAILED }

    private static volatile Outcome outcome;

    private ComplexTextShaping() {
    }

    /** Installs once per JVM; later calls return the first answer. Off the EDT. */
    public static synchronized Outcome install() {
        if (outcome == null) {
            outcome = attempt();
            LOG.log(outcome == Outcome.INSTALLED ? Level.FINE : Level.INFO,
                    "complex-script shaping for the Browser: {0}", outcome);
        }
        return outcome;
    }

    private static Outcome attempt() {
        if (!FxAvailability.available()) {
            return Outcome.NO_JAVAFX;
        }
        Module web = ModuleLayer.boot().findModule("javafx.web").orElse(null);
        Module graphics = ModuleLayer.boot().findModule("javafx.graphics").orElse(null);
        if (web == null || graphics == null) {
            return Outcome.NO_JAVAFX;
        }
        Instrumentation inst;
        try {
            inst = attachSelf();
        } catch (ReflectiveOperationException | IOException | RuntimeException | LinkageError ex) {
            LOG.log(Level.FINE, "attaching the shaping agent was refused", ex);
            return Outcome.NO_ATTACH;
        }
        if (inst == null || !inst.isRetransformClassesSupported()) {
            return Outcome.NO_ATTACH;
        }
        try {
            Module ours = ComplexTextShaping.class.getModule();
            inst.redefineModule(web, Set.of(), Map.of(),
                    Map.of(PRISM_PACKAGE, Set.of(ours), "com.sun.webkit.graphics", Set.of(ours)),
                    Set.of(), Map.of());
            inst.redefineModule(graphics, Set.of(), Map.of(),
                    Map.of("com.sun.javafx.font", Set.of(ours), "com.sun.javafx.scene.text", Set.of(ours),
                            "com.sun.javafx.geom", Set.of(ours)),
                    Set.of(), Map.of());
            Class<?> context = Class.forName(CONTEXT.replace('/', '.'), false, web.getClassLoader());
            if (!hasGlyphDraw(context)) {
                return Outcome.METHOD_CHANGED;
            }
            Class<?> fontImpl = Class.forName(FONT_IMPL.replace('/', '.'), false, web.getClassLoader());
            Class<?> hook = MethodHandles.privateLookupIn(context, MethodHandles.lookup()).defineClass(hookClass());
            Class<?> utilities = Class.forName(TEXT_UTILITIES.replace('/', '.'), false, web.getClassLoader());
            PrismBridge bridge = new PrismBridge(web.getClassLoader());
            hook.getField("SHAPER").set(null, bridge);
            hook.getField("WIDTHS").set(null, (Function<Object[], Object>) bridge::width);
            hook.getField("PLACER").set(null, (Function<Object[], Object>) bridge::place);
            Transformer transformer = new Transformer();
            inst.addTransformer(transformer, true);
            try {
                // widths first: a paint that runs between the two would still be
                // measured and painted consistently, just unshaped; placement
                // before the paint, so shaping only lifts glyphs once a lifted
                // run can be built
                inst.retransformClasses(fontImpl);
                inst.retransformClasses(utilities);
                bridge.placementReady = transformer.placementApplied;
                inst.retransformClasses(context);
            } finally {
                inst.removeTransformer(transformer);
            }
            if (!transformer.placementApplied) {
                LOG.info("complex-script shaping: glyphs cannot leave the baseline; vowelled and Nastaliq runs stay unshaped");
            }
            if (!transformer.widthsApplied) {
                LOG.info("complex-script shaping: WebKit's glyph widths could not be adjusted; shaped words keep a gap");
            }
            return transformer.applied ? Outcome.INSTALLED : Outcome.METHOD_CHANGED;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError
                | java.lang.instrument.UnmodifiableClassException ex) {
            LOG.log(Level.INFO, "complex-script shaping could not be installed", ex);
            return Outcome.FAILED;
        }
    }

    private static boolean hasGlyphDraw(Class<?> context) {
        return Arrays.stream(context.getDeclaredMethods()).anyMatch(m -> m.getName().equals(DRAW)
                && m.getParameterCount() == 5 && m.getParameterTypes()[1] == int[].class
                && m.getParameterTypes()[2] == float[].class);
    }

    /** Writes the agent jar and attaches it; returns the JVM's Instrumentation. */
    private static Instrumentation attachSelf() throws ReflectiveOperationException, IOException {
        Module attach = ModuleLayer.boot().findModule("jdk.attach").orElse(null);
        if (attach == null) {
            return null;
        }
        Path jar = Files.createTempFile("nmox-shaping-agent", ".jar");
        jar.toFile().deleteOnExit();
        Manifest manifest = new Manifest();
        Attributes main = manifest.getMainAttributes();
        main.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        main.putValue("Agent-Class", ShapingAgent.class.getName());
        main.putValue("Can-Retransform-Classes", "true");
        main.putValue("Can-Redefine-Classes", "true");
        String entry = ShapingAgent.class.getName().replace('.', '/') + ".class";
        try (OutputStream out = Files.newOutputStream(jar);
                JarOutputStream jos = new JarOutputStream(out, manifest);
                InputStream in = ComplexTextShaping.class.getClassLoader().getResourceAsStream(entry)) {
            if (in == null) {
                return null;
            }
            jos.putNextEntry(new JarEntry(entry));
            in.transferTo(jos);
            jos.closeEntry();
        }
        Class<?> vm = Class.forName("com.sun.tools.attach.VirtualMachine", true, attach.getClassLoader());
        Object machine = vm.getMethod("attach", String.class).invoke(null, Long.toString(ProcessHandle.current().pid()));
        try {
            vm.getMethod("loadAgent", String.class).invoke(machine, jar.toString());
        } finally {
            vm.getMethod("detach").invoke(machine);
        }
        Class<?> agent = ClassLoader.getSystemClassLoader().loadClass(ShapingAgent.class.getName());
        return (Instrumentation) agent.getMethod("claim").invoke(null);
    }

    /**
     * The hook, generated so no class of ours has to live in a JavaFX package:
     * {@code public static volatile Function SHAPER} and
     * {@code static float reshape(Object font, Object glyphs, Object advances)}
     * that returns 0 until SHAPER is set.
     */
    static byte[] hookClass() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V21, Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL | Opcodes.ACC_SUPER, HOOK, null,
                "java/lang/Object", null);
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE, "SHAPER",
                "Ljava/util/function/Function;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE, "WIDTHS",
                "Ljava/util/function/Function;", null, null).visitEnd();
        cw.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC | Opcodes.ACC_VOLATILE, "PLACER",
                "Ljava/util/function/Function;", null, null).visitEnd();
        // static void shapeRun(TextRun run, int n, int[] glyphs, float[] advances):
        // when PLACER answers {int[] glyphs, float[] positions}, the run is built
        // from those; else from the advances as before
        MethodVisitor pv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "shapeRun", SHAPE_RUN, null, null);
        pv.visitCode();
        Label asBefore = new Label();
        pv.visitFieldInsn(Opcodes.GETSTATIC, HOOK, "PLACER", "Ljava/util/function/Function;");
        pv.visitVarInsn(Opcodes.ASTORE, 4);
        pv.visitVarInsn(Opcodes.ALOAD, 4);
        pv.visitJumpInsn(Opcodes.IFNULL, asBefore);
        pv.visitVarInsn(Opcodes.ALOAD, 4);
        pv.visitInsn(Opcodes.ICONST_2);
        pv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        pv.visitInsn(Opcodes.DUP);
        pv.visitInsn(Opcodes.ICONST_0);
        pv.visitVarInsn(Opcodes.ALOAD, 2);
        pv.visitInsn(Opcodes.AASTORE);
        pv.visitInsn(Opcodes.DUP);
        pv.visitInsn(Opcodes.ICONST_1);
        pv.visitVarInsn(Opcodes.ALOAD, 3);
        pv.visitInsn(Opcodes.AASTORE);
        pv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Function", "apply",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        pv.visitVarInsn(Opcodes.ASTORE, 5);
        pv.visitVarInsn(Opcodes.ALOAD, 5);
        pv.visitJumpInsn(Opcodes.IFNULL, asBefore);
        pv.visitVarInsn(Opcodes.ALOAD, 5);
        pv.visitTypeInsn(Opcodes.CHECKCAST, "[Ljava/lang/Object;");
        pv.visitVarInsn(Opcodes.ASTORE, 6);
        pv.visitVarInsn(Opcodes.ALOAD, 6);
        pv.visitInsn(Opcodes.ICONST_0);
        pv.visitInsn(Opcodes.AALOAD);
        pv.visitTypeInsn(Opcodes.CHECKCAST, "[I");
        pv.visitVarInsn(Opcodes.ASTORE, 7);
        pv.visitVarInsn(Opcodes.ALOAD, 0);
        pv.visitVarInsn(Opcodes.ALOAD, 7);
        pv.visitInsn(Opcodes.ARRAYLENGTH);
        pv.visitVarInsn(Opcodes.ALOAD, 7);
        pv.visitVarInsn(Opcodes.ALOAD, 6);
        pv.visitInsn(Opcodes.ICONST_1);
        pv.visitInsn(Opcodes.AALOAD);
        pv.visitTypeInsn(Opcodes.CHECKCAST, "[F");
        pv.visitInsn(Opcodes.ACONST_NULL);
        pv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TEXT_RUN, RUN_SHAPE, "(I[I[F[I)V", false);
        pv.visitInsn(Opcodes.RETURN);
        pv.visitLabel(asBefore);
        pv.visitVarInsn(Opcodes.ALOAD, 0);
        pv.visitVarInsn(Opcodes.ILOAD, 1);
        pv.visitVarInsn(Opcodes.ALOAD, 2);
        pv.visitVarInsn(Opcodes.ALOAD, 3);
        pv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, TEXT_RUN, RUN_SHAPE, RUN_SHAPE_DESC, false);
        pv.visitInsn(Opcodes.RETURN);
        pv.visitMaxs(0, 0);
        pv.visitEnd();
        // static double width(Object font, int glyph): NaN until WIDTHS is set
        MethodVisitor wv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "width", WIDTH, null, null);
        wv.visitCode();
        wv.visitFieldInsn(Opcodes.GETSTATIC, HOOK, "WIDTHS", "Ljava/util/function/Function;");
        wv.visitVarInsn(Opcodes.ASTORE, 2);
        wv.visitVarInsn(Opcodes.ALOAD, 2);
        Label widths = new Label();
        wv.visitJumpInsn(Opcodes.IFNONNULL, widths);
        wv.visitLdcInsn(Double.NaN);
        wv.visitInsn(Opcodes.DRETURN);
        wv.visitLabel(widths);
        wv.visitVarInsn(Opcodes.ALOAD, 2);
        wv.visitInsn(Opcodes.ICONST_2);
        wv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        wv.visitInsn(Opcodes.DUP);
        wv.visitInsn(Opcodes.ICONST_0);
        wv.visitVarInsn(Opcodes.ALOAD, 0);
        wv.visitInsn(Opcodes.AASTORE);
        wv.visitInsn(Opcodes.DUP);
        wv.visitInsn(Opcodes.ICONST_1);
        wv.visitVarInsn(Opcodes.ILOAD, 1);
        wv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
        wv.visitInsn(Opcodes.AASTORE);
        wv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Function", "apply",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        wv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Double");
        wv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Double", "doubleValue", "()D", false);
        wv.visitInsn(Opcodes.DRETURN);
        wv.visitMaxs(0, 0);
        wv.visitEnd();
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "reshape", RESHAPE, null, null);
        mv.visitCode();
        mv.visitFieldInsn(Opcodes.GETSTATIC, HOOK, "SHAPER", "Ljava/util/function/Function;");
        mv.visitVarInsn(Opcodes.ASTORE, 3);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        Label ready = new Label();
        mv.visitJumpInsn(Opcodes.IFNONNULL, ready);
        mv.visitInsn(Opcodes.FCONST_0);
        mv.visitInsn(Opcodes.FRETURN);
        mv.visitLabel(ready);
        mv.visitVarInsn(Opcodes.ALOAD, 3);
        mv.visitInsn(Opcodes.ICONST_3);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        for (int k = 0; k < 3; k++) {
            mv.visitInsn(Opcodes.DUP);
            mv.visitInsn(Opcodes.ICONST_0 + k);
            mv.visitVarInsn(Opcodes.ALOAD, k);
            mv.visitInsn(Opcodes.AASTORE);
        }
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE, "java/util/function/Function", "apply",
                "(Ljava/lang/Object;)Ljava/lang/Object;", true);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/Float");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Float", "floatValue", "()F", false);
        mv.visitInsn(Opcodes.FRETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Rewrites {@code drawString(WCFont, int[], float[], float x, float y)} so
     * it starts with {@code x += NmoxComplexText.reshape(font, glyphs, advances)}.
     * No branch is added, so the method's existing stack map frames stay valid.
     */
    static byte[] rewrite(byte[] original, boolean[] applied) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!DRAW.equals(name) || !DRAW_GLYPHS.equals(descriptor) || (access & Opcodes.ACC_STATIC) != 0) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 1);
                        super.visitVarInsn(Opcodes.ALOAD, 2);
                        super.visitVarInsn(Opcodes.ALOAD, 3);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "reshape", RESHAPE, false);
                        super.visitVarInsn(Opcodes.FLOAD, 4);
                        super.visitInsn(Opcodes.FADD);
                        super.visitVarInsn(Opcodes.FSTORE, 4);
                        applied[0] = true;
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    /**
     * Rewrites {@code WCFontImpl.getGlyphWidth(int)} so it starts with
     * {@code double w = NmoxComplexText.width(this, glyph); if (w == w) return w;}
     * — NaN falls through to the font's own width. The one branch gets its
     * frame spelled out (locals this, glyph, w; empty stack), so frames are
     * expanded for the whole class.
     */
    static byte[] rewriteWidths(byte[] original, boolean[] applied) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!GLYPH_WIDTH.equals(name) || !GLYPH_WIDTH_DESC.equals(descriptor)
                        || (access & Opcodes.ACC_STATIC) != 0) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        super.visitVarInsn(Opcodes.ALOAD, 0);
                        super.visitVarInsn(Opcodes.ILOAD, 1);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "width", WIDTH, false);
                        super.visitVarInsn(Opcodes.DSTORE, 2);
                        super.visitVarInsn(Opcodes.DLOAD, 2);
                        super.visitVarInsn(Opcodes.DLOAD, 2);
                        super.visitInsn(Opcodes.DCMPL);
                        Label fontOwn = new Label();
                        super.visitJumpInsn(Opcodes.IFNE, fontOwn);
                        super.visitVarInsn(Opcodes.DLOAD, 2);
                        super.visitInsn(Opcodes.DRETURN);
                        super.visitLabel(fontOwn);
                        super.visitFrame(Opcodes.F_NEW, 3, new Object[]{FONT_IMPL, Opcodes.INTEGER, Opcodes.DOUBLE},
                                0, new Object[0]);
                        applied[0] = true;
                    }
                };
            }
        }, ClassReader.EXPAND_FRAMES);
        return writer.toByteArray();
    }

    /**
     * Rewrites {@code TextUtilities.createGlyphList} so the one call that
     * builds its run from advances, {@code TextRun.shape(int, int[], float[])},
     * goes to {@code NmoxComplexText.shapeRun} with the same arguments. The
     * stack is the same before and after, so no frame changes.
     */
    static byte[] rewritePlacement(byte[] original, boolean[] applied) {
        ClassReader reader = new ClassReader(original);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                if (!CREATE_GLYPH_LIST.equals(name)) {
                    return mv;
                }
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String callee, String desc, boolean itf) {
                        if (opcode == Opcodes.INVOKEVIRTUAL && TEXT_RUN.equals(owner) && RUN_SHAPE.equals(callee)
                                && RUN_SHAPE_DESC.equals(desc)) {
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK, "shapeRun", SHAPE_RUN, false);
                            applied[0] = true;
                            return;
                        }
                        super.visitMethodInsn(opcode, owner, callee, desc, itf);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    static final class Transformer implements ClassFileTransformer {
        volatile boolean applied;
        volatile boolean widthsApplied;
        volatile boolean placementApplied;

        @Override
        public byte[] transform(Module module, ClassLoader loader, String className, Class<?> redefined,
                ProtectionDomain domain, byte[] bytes) {
            if (FONT_IMPL.equals(className)) {
                try {
                    boolean[] done = {false};
                    byte[] out = rewriteWidths(bytes, done);
                    widthsApplied = done[0];
                    return done[0] ? out : null;
                } catch (RuntimeException ex) {
                    LOG.log(Level.INFO, "could not rewrite " + className, ex);
                    return null;
                }
            }
            if (TEXT_UTILITIES.equals(className)) {
                try {
                    boolean[] done = {false};
                    byte[] out = rewritePlacement(bytes, done);
                    placementApplied = done[0];
                    return done[0] ? out : null;
                } catch (RuntimeException ex) {
                    LOG.log(Level.INFO, "could not rewrite " + className, ex);
                    return null;
                }
            }
            if (!CONTEXT.equals(className)) {
                return null;
            }
            try {
                boolean[] done = {false};
                byte[] out = rewrite(bytes, done);
                applied = done[0];
                return done[0] ? out : null;
            } catch (RuntimeException ex) {
                LOG.log(Level.INFO, "could not rewrite " + className, ex);
                return null;
            }
        }
    }

    /**
     * The shaper the hook calls: reads a WebKit font's glyph mapping and asks
     * JavaFX's own text layout to shape a run. Never throws into the paint.
     */
    /**
     * A font's shaped runs, least recently used first out (v2.171.0). WebKit
     * paints the same words on every repaint, and shaping each run is a JavaFX
     * text layout through reflection; before this cache a scroll re-shaped every
     * visible word, and the slowest frames on a long Arabic or Hindi page took
     * two to three times as long as on the same page unshaped. A run the shaper
     * refused is remembered as refused. Shaped runs are never written to, so
     * sharing one between paints is safe.
     */
    static final class ShapeCache {
        static final int CAPACITY = 2048;
        private static final ComplexScripts.Shaped REFUSED = new ComplexScripts.Shaped(new int[0], new float[0]);
        private final Map<String, ComplexScripts.Shaped> runs;

        ShapeCache(int capacity) {
            runs = new java.util.LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ComplexScripts.Shaped> eldest) {
                    return size() > capacity;
                }
            };
        }

        /** The run shaped for {@code text}, shaping it once; null when the shaper refused it. */
        synchronized ComplexScripts.Shaped get(String text, Function<String, ComplexScripts.Shaped> shaper) {
            ComplexScripts.Shaped known = runs.get(text);
            if (known == null) {
                known = shaper.apply(text);
                runs.put(text, known == null ? REFUSED : known);
            }
            return known == REFUSED ? null : known;
        }

        synchronized int size() {
            return runs.size();
        }
    }

    static final class PrismBridge implements Function<Object[], Object> {

        private final Method platformFont;
        private final Method fontResource;
        private final Method glyphMapper;
        private final Method charToGlyph;
        private final Method createLayout;
        private final Method runs;
        private final Method glyphCount;
        private final Method glyphCode;
        private final Method posX;
        private final Method width;
        private final Method location;
        private final Method charOffset;
        /** Per WebKit font and glyph: the width WebKit should measure. */
        private final Map<Object, Map<Integer, Double>> widths = new WeakHashMap<>();
        /** Per WebKit font: the runs it has shaped, most recent last. */
        private final Map<Object, ShapeCache> shaped = new WeakHashMap<>();
        /** Per WebKit font: whether its joined Arabic letters leave the baseline. */
        private final Map<Object, Boolean> stacking = new WeakHashMap<>();
        private final Field locationX;
        private final Field locationY;
        private final Method posY;
        /** Set once the glyph-list builder can take positions; until then no run leaves the baseline. */
        volatile boolean placementReady;
        /**
         * The offsets of the run this thread's paint just shaped, keyed by that
         * paint's glyph array: {@code drawString} hands the same array to
         * {@code createGlyphList} straight after, so identity finds it.
         */
        private final ThreadLocal<Object[]> pending = new ThreadLocal<>();
        /** Which way this JavaFX's layout measures y, once a kasra has said: +1 down, -1 up. */
        private volatile float yDirection;
        /** Per WebKit font: sorted glyph codes, their characters, and a blank glyph. */
        private final Map<Object, int[][]> tables = new WeakHashMap<>();

        PrismBridge(ClassLoader fx) throws ReflectiveOperationException {
            Class<?> wcFont = Class.forName("com.sun.webkit.graphics.WCFont", false, fx);
            platformFont = open(wcFont.getMethod("getPlatformFont"));
            Class<?> pgFont = Class.forName("com.sun.javafx.font.PGFont", false, fx);
            fontResource = open(pgFont.getMethod("getFontResource"));
            Class<?> resource = Class.forName("com.sun.javafx.font.FontResource", false, fx);
            glyphMapper = open(resource.getMethod("getGlyphMapper"));
            Class<?> mapper = Class.forName("com.sun.javafx.font.CharToGlyphMapper", false, fx);
            charToGlyph = open(mapper.getMethod("charToGlyph", int.class));
            Class<?> utilities = Class.forName(PRISM_PACKAGE + ".TextUtilities", false, fx);
            createLayout = open(utilities.getDeclaredMethod("createLayout", String.class, Object.class));
            Class<?> layout = Class.forName("com.sun.javafx.scene.text.TextLayout", false, fx);
            runs = open(layout.getMethod("getRuns"));
            Class<?> glyphList = Class.forName("com.sun.javafx.scene.text.GlyphList", false, fx);
            glyphCount = open(glyphList.getMethod("getGlyphCount"));
            glyphCode = open(glyphList.getMethod("getGlyphCode", int.class));
            posX = open(glyphList.getMethod("getPosX", int.class));
            width = open(glyphList.getMethod("getWidth"));
            location = open(glyphList.getMethod("getLocation"));
            charOffset = open(glyphList.getMethod("getCharOffset", int.class));
            posY = open(glyphList.getMethod("getPosY", int.class));
            Class<?> point = Class.forName("com.sun.javafx.geom.Point2D", false, fx);
            locationX = point.getField("x");
            locationX.setAccessible(true);
            locationY = point.getField("y");
            locationY.setAccessible(true);
        }

        private static Method open(Method m) {
            m.setAccessible(true);
            return m;
        }

        @Override
        public Object apply(Object[] args) {
            try {
                Object font = args[0];
                int[] glyphs = (int[]) args[1];
                float[] advances = (float[]) args[2];
                if (font == null || glyphs == null || advances == null) {
                    return 0f;
                }
                pending.remove();
                int[][] table = table(font);
                if (table == null || !anyShapeable(glyphs, table)) {
                    return 0f;
                }
                Object pg = platformFont.invoke(font);
                ShapeCache cache = shapeCache(font);
                Function<String, ComplexScripts.Shaped> shaper = text -> cache.get(text, t -> shape(t, pg));
                if (!placementReady) {
                    return ComplexScripts.reshape(glyphs, advances, g -> charFor(table, g), shaper, table[2][0]);
                }
                ComplexScripts.Laid laid = ComplexScripts.layout(glyphs, advances, g -> charFor(table, g),
                        shaper, table[2][0]);
                if (laid == null || !laid.changed()) {
                    return 0f;
                }
                remember(glyphs, laid);
                return laid.origin(); // the run's positions start at zero; the call moves to where it begins
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                return 0f; // the page still paints, unshaped, as it always did
            }
        }

        private ShapeCache shapeCache(Object font) {
            synchronized (shaped) {
                return shaped.computeIfAbsent(font, f -> new ShapeCache(ShapeCache.CAPACITY));
            }
        }

        /** Holds a laid-out run for the glyph-list build that follows on this thread. */
        void remember(int[] glyphs, ComplexScripts.Laid laid) {
            pending.set(new Object[]{glyphs, laid});
        }

        /**
         * The placement answer: {glyphs, advances} to {the laid-out glyphs, their
         * x/y positions} for the run this thread just shaped, or null to build the
         * run from its advances as before. Never throws.
         */
        Object place(Object[] args) {
            Object[] mine = pending.get();
            if (mine == null || args == null || args.length < 1 || mine[0] != args[0]) {
                return null;
            }
            pending.remove();
            ComplexScripts.Laid laid = (ComplexScripts.Laid) mine[1];
            return new Object[]{laid.glyphs(), ComplexScripts.positions(laid.xs(), laid.rises(), laid.width())};
        }

        /** The hook's width answer: {font, glyph} to a Double, NaN for the font's own. Never throws. */
        Object width(Object[] args) {
            try {
                Object font = args[0];
                int glyph = (Integer) args[1];
                int[][] table = font == null ? null : table(font);
                if (table == null) {
                    return Double.NaN;
                }
                int cp = charFor(table, glyph);
                if (cp < 0 || !ComplexScripts.shapes(cp) && !ComplexScripts.combining(cp)) {
                    return Double.NaN; // a Latin letter keeps the font's own width, with no work done
                }
                Map<Integer, Double> known;
                synchronized (widths) {
                    known = widths.computeIfAbsent(font, f -> new java.util.HashMap<>());
                }
                synchronized (known) {
                    Double cached = known.get(glyph);
                    if (cached != null) {
                        return cached;
                    }
                }
                Object pg = platformFont.invoke(font);
                String letter = new String(Character.toChars(cp));
                String tatweel = String.valueOf(ComplexScripts.TATWEEL);
                double w = ComplexScripts.measuredWidth(cp,
                        c -> textWidth(tatweel + letter + tatweel, pg) - 2 * textWidth(tatweel, pg),
                        c -> textWidth(tatweel + letter, pg) - textWidth(tatweel, pg),
                        c -> firstAdvance(letter, pg),
                        stacks(font, pg));
                synchronized (known) {
                    known.put(glyph, w);
                }
                return w;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                return Double.NaN;
            }
        }

        /**
         * How wide JavaFX lays {@code text} out, or NaN: the same measure the
         * width constants were calibrated with (a word's laid-out width), so a
         * letter drawn as several glyphs, or glyphs out of order, measure right.
         */
        private double textWidth(String text, Object pg) {
            try {
                double total = 0d;
                for (Object list : (Object[]) runs.invoke(createLayout.invoke(null, text, pg))) {
                    total += (Float) width.invoke(list);
                }
                return total;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return Double.NaN;
            }
        }

        /**
         * The advance of the first glyph JavaFX gives a lone character, or NaN:
         * the plain measure the Indic share was calibrated against in v2.166.0.
         * A lone vowel sign's laid-out width would count the dotted circle a
         * layout draws in front of it and measure the sign twice over.
         */
        private double firstAdvance(String text, Object pg) {
            try {
                for (Object list : (Object[]) runs.invoke(createLayout.invoke(null, text, pg))) {
                    int count = (Integer) glyphCount.invoke(list);
                    float runWidth = (Float) width.invoke(list);
                    for (int g = 0; g < count; g++) {
                        if ((Integer) charOffset.invoke(list, g) == 0) {
                            float at = (Float) posX.invoke(list, g);
                            float next = g + 1 < count ? (Float) posX.invoke(list, g + 1) : runWidth;
                            return Math.abs(next - at);
                        }
                    }
                }
                return Double.NaN;
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return Double.NaN;
            }
        }

        /**
         * Whether a font's joined Arabic letters leave the baseline (Nastaliq):
         * four behs shaped together, offsets read. Once per font.
         */
        private boolean stacks(Object font, Object pg) {
            synchronized (stacking) {
                Boolean known = stacking.get(font);
                if (known != null) {
                    return known;
                }
            }
            boolean climbs = false;
            try {
                for (Object list : (Object[]) runs.invoke(createLayout.invoke(null, "\u0628\u0628\u0628\u0628", pg))) {
                    int count = (Integer) glyphCount.invoke(list);
                    for (int g = 0; g < count && !climbs; g++) {
                        climbs = (Float) posY.invoke(list, g) != 0f;
                    }
                }
            } catch (ReflectiveOperationException | RuntimeException ex) {
                climbs = false;
            }
            synchronized (stacking) {
                stacking.put(font, climbs);
            }
            return climbs;
        }

        private int[][] table(Object font) throws ReflectiveOperationException {
            synchronized (tables) {
                int[][] table = tables.get(font);
                if (table == null) {
                    table = buildTable(font);
                    tables.put(font, table);
                }
                return table;
            }
        }

        private int[][] buildTable(Object font) throws ReflectiveOperationException {
            Object pg = platformFont.invoke(font);
            if (pg == null) {
                return null;
            }
            Object mapper = glyphMapper.invoke(fontResource.invoke(pg));
            java.util.function.IntUnaryOperator lookup = cp -> {
                try {
                    return (Integer) charToGlyph.invoke(mapper, cp);
                } catch (ReflectiveOperationException ex) {
                    return 0;
                }
            };
            return glyphTable(lookup);
        }

        /**
         * A font's reverse map for the shaped scripts: glyph codes sorted, the
         * character each stands for beside it (the first, when two share a
         * glyph), and the glyph that draws a space.
         */
        static int[][] glyphTable(java.util.function.IntUnaryOperator charToGlyph) {
            int total = 0;
            for (int[] r : ComplexScripts.RANGES) {
                total += r[1] - r[0] + 1;
            }
            long[] pairs = new long[total];
            int n = 0;
            for (int[] r : ComplexScripts.RANGES) {
                for (int cp = r[0]; cp <= r[1]; cp++) {
                    int g = charToGlyph.applyAsInt(cp);
                    if (g != 0) {
                        pairs[n++] = ((long) g << 32) | cp;
                    }
                }
            }
            long[] sorted = Arrays.copyOf(pairs, n);
            Arrays.sort(sorted);
            int[] codes = new int[n];
            int[] chars = new int[n];
            int kept = 0;
            for (long p : sorted) {
                int g = (int) (p >>> 32);
                if (kept > 0 && codes[kept - 1] == g) {
                    continue; // two characters share a glyph: keep the first
                }
                codes[kept] = g;
                chars[kept] = (int) p;
                kept++;
            }
            return new int[][]{Arrays.copyOf(codes, kept), Arrays.copyOf(chars, kept), {charToGlyph.applyAsInt(' ')}};
        }

        static int charFor(int[][] table, int glyph) {
            int at = Arrays.binarySearch(table[0], glyph);
            return at >= 0 ? table[1][at] : -1;
        }

        /**
         * Whether a paint has anything to shape: a letter or mark of the shaped
         * scripts, or a combining mark. The table also maps plain Latin, Greek
         * and Cyrillic letters (v2.170.0), so being in it is not enough, and an
         * English paint returns here without laying anything out.
         */
        static boolean anyShapeable(int[] glyphs, int[][] table) {
            for (int g : glyphs) {
                int cp = charFor(table, g);
                if (cp >= 0 && (ComplexScripts.shapes(cp) || ComplexScripts.combining(cp))) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Shaped glyphs sorted left to right by where the layout placed them,
         * offsets travelling with them, each advance the distance to the next
         * glyph in that order and the last one's to {@code end}. The layout's
         * own order is logical: with marks in it, a mark sits between two
         * letters that are not neighbours on screen (v2.167.0).
         */
        static ComplexScripts.Shaped visualOrder(int[] code, float[] x, float[] y, float end) {
            int total = code.length;
            Integer[] order = new Integer[total];
            for (int j = 0; j < total; j++) {
                order[j] = j;
            }
            Arrays.sort(order, (a, b) -> Float.compare(x[a], x[b]));
            int[] glyphs = new int[total];
            float[] advances = new float[total];
            float[] rises = new float[total];
            for (int j = 0; j < total; j++) {
                glyphs[j] = code[order[j]];
                float next = j + 1 < total ? x[order[j + 1]] : end;
                advances[j] = Math.max(0f, next - x[order[j]]);
                rises[j] = y[order[j]];
            }
            return new ComplexScripts.Shaped(glyphs, advances, rises);
        }

        /**
         * +1 when this JavaFX's layout reports offsets downward as it draws
         * them, -1 when it reports them upward. Measured, not assumed per
         * platform: on macOS JavaFX 26 hands CoreText's upward offsets on
         * unchanged, and its own Text node paints a kasra above its letter. A
         * kasra sits below in every Arabic font, so its sign answers; a font
         * that places no marks answers nothing, and down is kept until one does.
         */
        private float downward(Object pg) throws ReflectiveOperationException {
            float known = yDirection;
            if (known != 0f) {
                return known;
            }
            Object layout = createLayout.invoke(null, "\u0628\u0650", pg);
            for (Object list : (Object[]) runs.invoke(layout)) {
                int count = (Integer) glyphCount.invoke(list);
                for (int g = 0; g < count; g++) {
                    if ((Integer) charOffset.invoke(list, g) == 1) {
                        float kasra = ComplexScripts.downwardFrom((Float) posY.invoke(list, g));
                        yDirection = kasra;
                        return kasra == 0f ? 1f : kasra;
                    }
                }
            }
            return 1f;
        }

        private ComplexScripts.Shaped shape(String text, Object pg) {
            try {
                Object layout = createLayout.invoke(null, text, pg);
                Object[] lists = (Object[]) runs.invoke(layout);
                int total = 0;
                for (Object list : lists) {
                    total += (Integer) glyphCount.invoke(list);
                }
                float[] x = new float[total];
                float[] y = new float[total];
                float end = 0f;
                float[] lineY = new float[total];
                boolean lifted = false;
                int[] code = new int[total];
                int k = 0;
                for (Object list : lists) {
                    Object where = location.invoke(list);
                    float base = (Float) locationX.get(where);
                    float baseY = (Float) locationY.get(where);
                    int count = (Integer) glyphCount.invoke(list);
                    end = Math.max(end, base + (Float) width.invoke(list));
                    for (int g = 0; g < count; g++) {
                        code[k] = (Integer) glyphCode.invoke(list, g);
                        x[k] = base + (Float) posX.invoke(list, g);
                        y[k] = (Float) posY.invoke(list, g);
                        lineY[k] = baseY;
                        lifted |= y[k] != 0f;
                        k++;
                    }
                }
                // asked only for a run that leaves the baseline: a Hindi page, whose
                // fonts place no kasra, re-asked on every shape call before v2.171.0
                float down = lifted ? downward(pg) : 1f;
                for (int j = 0; j < total; j++) {
                    y[j] = lineY[j] + down * y[j];
                }
                return visualOrder(code, x, y, end);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return null;
            }
        }
    }
}
