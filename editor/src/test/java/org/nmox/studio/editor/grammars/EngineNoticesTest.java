package org.nmox.studio.editor.grammars;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import org.eclipse.tm4e.core.TMException;
import org.eclipse.tm4e.core.internal.oniguruma.OnigRegExp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * joni's remarks on vendored patterns stay out of the session log, and a
 * pattern it refuses is still an exception (3.5.5).
 */
class EngineNoticesTest {

    /** A pattern joni simplifies and says so: C++'s grammar has it 25 times. */
    private static final String REMARKED = "(?<!\\w)(template)(?:\\s+)?(<)";

    private final List<LogRecord> heard = new ArrayList<>();
    private final Handler listener = new Handler() {
        @Override
        public void publish(LogRecord record) {
            heard.add(record);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    };
    private Logger logger;
    private Level before;

    @BeforeEach
    void listen() {
        logger = Logger.getLogger(EngineNotices.LOGGER);
        before = logger.getLevel();
        logger.setLevel(null);
        logger.addHandler(listener);
        logger.setUseParentHandlers(false);
    }

    @AfterEach
    void restore() {
        logger.removeHandler(listener);
        logger.setUseParentHandlers(true);
        logger.setLevel(before);
    }

    @Test
    @DisplayName("the engine remarks on the pattern, and after quiet() it does not")
    void theRemarkIsNotLogged() {
        new OnigRegExp(REMARKED);
        assertThat(heard).as("the control: this pattern is one joni remarks on")
                .anyMatch(r -> r.getLevel() == Level.WARNING && String.valueOf(r.getMessage()).contains("nested repeat"));

        heard.clear();
        EngineNotices.quiet();
        new OnigRegExp(REMARKED);
        assertThat(heard).isEmpty();
    }

    @Test
    @DisplayName("a pattern the engine refuses is still thrown")
    void aRefusalIsNotHidden() {
        EngineNotices.quiet();
        assertThatThrownBy(() -> new OnigRegExp("(?<=a\\s*)b"))
                .isInstanceOf(TMException.class).hasMessageContaining("look-behind");
    }

    @Test
    @DisplayName("a level someone set on purpose is left alone")
    void anExplicitLevelStands() {
        logger.setLevel(Level.WARNING);
        EngineNotices.quiet();
        assertThat(logger.getLevel()).isEqualTo(Level.WARNING);
        new OnigRegExp(REMARKED);
        assertThat(heard).as("a maintainer who asked for the notes gets them").isNotEmpty();
    }

    @Test
    @DisplayName("the logger is held, so the level is not collected with it")
    void theLoggerIsHeld() throws Exception {
        Logger held = EngineNotices.quiet();
        assertThat(held).isSameAs(Logger.getLogger(EngineNotices.LOGGER));
        var field = EngineNotices.class.getDeclaredField("held");
        field.setAccessible(true);
        assertThat(java.lang.reflect.Modifier.isStatic(field.getModifiers())).isTrue();
        assertThat(field.get(null)).as("a static field, not a local").isSameAs(held);
    }

    @Test
    @DisplayName("it runs when the module starts")
    void registeredAtStart() throws Exception {
        String started = Files.readString(
                Path.of("target/classes/META-INF/namedservices/Modules/Start/java.lang.Runnable"), StandardCharsets.UTF_8);
        assertThat(started.lines().map(String::strip)).contains(EngineNotices.class.getName());
    }
}
