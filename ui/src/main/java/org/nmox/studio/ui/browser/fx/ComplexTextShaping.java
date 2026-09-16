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

    /** What happened, for the log line and the tests. */
    enum Outcome { INSTALLED, NO_JAVAFX, NO_ATTACH, METHOD_CHANGED, FAILED }

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
            Class<?> hook = MethodHandles.privateLookupIn(context, MethodHandles.lookup()).defineClass(hookClass());
            hook.getField("SHAPER").set(null, new PrismBridge(web.getClassLoader()));
            Transformer transformer = new Transformer();
            inst.addTransformer(transformer, true);
            try {
                inst.retransformClasses(context);
            } finally {
                inst.removeTransformer(transformer);
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
        return (Instrumentation) agent.getField("instrumentation").get(null);
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

    private static final class Transformer implements ClassFileTransformer {
        volatile boolean applied;

        @Override
        public byte[] transform(Module module, ClassLoader loader, String className, Class<?> redefined,
                ProtectionDomain domain, byte[] bytes) {
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
        private final Field locationX;
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
            locationX = Class.forName("com.sun.javafx.geom.Point2D", false, fx).getField("x");
            locationX.setAccessible(true);
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
                int[][] table = table(font);
                if (table == null || !anyShapeable(glyphs, table)) {
                    return 0f;
                }
                Object pg = platformFont.invoke(font);
                return ComplexScripts.reshape(glyphs, advances, g -> charFor(table, g),
                        text -> shape(text, pg), table[2][0]);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                return 0f; // the page still paints, unshaped, as it always did
            }
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
            int total = 0;
            for (int[] r : ComplexScripts.RANGES) {
                total += r[1] - r[0] + 1;
            }
            long[] pairs = new long[total];
            int n = 0;
            for (int[] r : ComplexScripts.RANGES) {
                for (int cp = r[0]; cp <= r[1]; cp++) {
                    int g = (Integer) charToGlyph.invoke(mapper, cp);
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
            int blank = (Integer) charToGlyph.invoke(mapper, (int) ' ');
            return new int[][]{Arrays.copyOf(codes, kept), Arrays.copyOf(chars, kept), {blank}};
        }

        private static int charFor(int[][] table, int glyph) {
            int at = Arrays.binarySearch(table[0], glyph);
            return at >= 0 ? table[1][at] : -1;
        }

        private static boolean anyShapeable(int[] glyphs, int[][] table) {
            for (int g : glyphs) {
                if (Arrays.binarySearch(table[0], g) >= 0) {
                    return true;
                }
            }
            return false;
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
                float[] adv = new float[total];
                int[] code = new int[total];
                int k = 0;
                for (Object list : lists) {
                    float base = (Float) locationX.get(location.invoke(list));
                    int count = (Integer) glyphCount.invoke(list);
                    float runWidth = (Float) width.invoke(list);
                    for (int g = 0; g < count; g++) {
                        float at = (Float) posX.invoke(list, g);
                        float next = g + 1 < count ? (Float) posX.invoke(list, g + 1) : runWidth;
                        code[k] = (Integer) glyphCode.invoke(list, g);
                        x[k] = base + at;
                        adv[k] = Math.abs(next - at);
                        k++;
                    }
                }
                Integer[] order = new Integer[total];
                for (int j = 0; j < total; j++) {
                    order[j] = j;
                }
                Arrays.sort(order, (a, b) -> Float.compare(x[a], x[b]));
                int[] glyphs = new int[total];
                float[] advances = new float[total];
                for (int j = 0; j < total; j++) {
                    glyphs[j] = code[order[j]];
                    advances[j] = adv[order[j]];
                }
                return new ComplexScripts.Shaped(glyphs, advances);
            } catch (ReflectiveOperationException | RuntimeException ex) {
                return null;
            }
        }
    }
}
