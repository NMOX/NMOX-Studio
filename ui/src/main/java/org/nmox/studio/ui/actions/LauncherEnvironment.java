package org.nmox.studio.ui.actions;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Takes a variable the launcher set for the IDE's own process back out of
 * the environment every process the IDE starts inherits.
 *
 * <p>There are two copies of the environment a child can inherit, and both
 * have to lose it (measured on JDK 25 with a probe that spawned
 * {@code /bin/sh -c 'echo $CFProcessPath'} both ways):
 * <ul>
 *   <li>the process's NATIVE environment, which a {@code ProcessBuilder}
 *       whose {@code environment()} was never touched passes on as it is —
 *       removed with libc's {@code unsetenv}, through the foreign-function
 *       API. The ui module compiles to Java 21 bytecode, where that API is
 *       still a preview, so it is reached reflectively, as
 *       {@code WebKitTextPath} reaches it; the bundled runtime is 25.</li>
 *   <li>the JDK's own snapshot ({@code java.lang.ProcessEnvironment}), taken
 *       the first time anything asked {@code System.getenv}, which is where
 *       a {@code ProcessBuilder.environment()} copy comes from — removed by
 *       reflection, which the launcher conf's
 *       {@code --add-opens=java.base/java.lang=ALL-UNNAMED} permits.</li>
 * </ul>
 *
 * Every failure is caught and answered with {@code false}, never thrown:
 * the caller logs it, and the IDE starts regardless.
 */
final class LauncherEnvironment {

    private static final Logger LOG = Logger.getLogger(LauncherEnvironment.class.getName());

    private LauncherEnvironment() {
    }

    /** Removes {@code name} from both environments; true when neither holds it any longer. */
    static boolean forget(String name) {
        boolean nativeGone = unsetNative(name);
        boolean javaGone = removeFromSnapshot(name);
        return nativeGone && javaGone;
    }

    static boolean unsetNative(String name) {
        try {
            Class<?> linkerClass = Class.forName("java.lang.foreign.Linker");
            Class<?> lookupClass = Class.forName("java.lang.foreign.SymbolLookup");
            Class<?> segmentClass = Class.forName("java.lang.foreign.MemorySegment");
            Class<?> layoutClass = Class.forName("java.lang.foreign.ValueLayout");
            Class<?> memoryLayout = Class.forName("java.lang.foreign.MemoryLayout");
            Class<?> descriptorClass = Class.forName("java.lang.foreign.FunctionDescriptor");
            Class<?> optionClass = Class.forName("java.lang.foreign.Linker$Option");
            Class<?> arenaClass = Class.forName("java.lang.foreign.Arena");

            Object linker = linkerClass.getMethod("nativeLinker").invoke(null);
            Object lookup = linkerClass.getMethod("defaultLookup").invoke(linker);
            Optional<?> symbol = (Optional<?>) lookupClass.getMethod("find", String.class).invoke(lookup, "unsetenv");
            if (symbol.isEmpty()) {
                return false;
            }
            Object args = Array.newInstance(memoryLayout, 1);
            Array.set(args, 0, layoutClass.getField("ADDRESS").get(null));
            Object descriptor = descriptorClass.getMethod("of", memoryLayout, args.getClass())
                    .invoke(null, layoutClass.getField("JAVA_INT").get(null), args);
            Object noOptions = Array.newInstance(optionClass, 0);
            MethodHandle unsetenv = (MethodHandle) linkerClass
                    .getMethod("downcallHandle", segmentClass, descriptorClass, noOptions.getClass())
                    .invoke(linker, symbol.get(), descriptor, noOptions);
            Object arena = arenaClass.getMethod("ofConfined").invoke(null);
            try {
                Object cName = arenaClass.getMethod("allocateFrom", String.class).invoke(arena, name);
                int rc = (int) unsetenv.invoke(cName);
                return rc == 0;
            } finally {
                arenaClass.getMethod("close").invoke(arena);
            }
        } catch (Throwable t) { // any Linkage/reflection/native failure: answer, do not throw
            LOG.log(Level.INFO, "unsetenv(" + name + ") unavailable in this runtime", t);
            return false;
        }
    }

    static boolean removeFromSnapshot(String name) {
        try {
            Class<?> env = Class.forName("java.lang.ProcessEnvironment");
            Field field = env.getDeclaredField("theEnvironment");
            field.setAccessible(true);
            Map<?, ?> snapshot = (Map<?, ?>) field.get(null);
            snapshot.keySet().removeIf(key -> name.equals(String.valueOf(key)));
            return System.getenv(name) == null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.log(Level.INFO, "the JDK's environment snapshot could not be edited", e);
            return System.getenv(name) == null;
        }
    }
}
