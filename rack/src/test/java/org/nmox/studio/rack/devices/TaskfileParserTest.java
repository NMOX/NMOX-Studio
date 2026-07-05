package org.nmox.studio.rack.devices;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The static task enumeration DYNAMO's TASK knob rides on: exactly what
 * the Gruntfile/gulpfile declares, tolerant of garbage, and never a
 * process spawned.
 */
class TaskfileParserTest {

    // ---------------- Gruntfiles ----------------

    @Test
    @DisplayName("registerTask names are listed, multi-arg forms included")
    void gruntRegisterTask() {
        String source = """
                module.exports = function (grunt) {
                  grunt.registerTask('default', ['uglify']);
                  grunt.registerTask("build", "Production build", ['uglify', 'cssmin']);
                  grunt.registerTask('deploy', 'push it', function () { return true; });
                };
                """;
        assertThat(TaskfileParser.gruntTasks(source))
                .containsExactly("default", "build", "deploy");
    }

    @Test
    @DisplayName("loadNpmTasks contributes the plugin's task, prefixes stripped")
    void gruntLoadNpmTasks() {
        String source = """
                grunt.loadNpmTasks('grunt-contrib-uglify');
                grunt.loadNpmTasks('grunt-contrib-watch');
                grunt.loadNpmTasks('grunt-shell');
                grunt.registerTask('default', ['uglify']);
                """;
        assertThat(TaskfileParser.gruntTasks(source))
                .containsExactly("uglify", "watch", "shell", "default");
    }

    @Test
    @DisplayName("CoffeeScript's paren-less registerTask parses too")
    void gruntCoffeeScriptForm() {
        String source = """
                module.exports = (grunt) ->
                  grunt.registerTask 'default', ['coffee']
                  grunt.registerTask "test", ['mochaTest']
                """;
        assertThat(TaskfileParser.gruntTasks(source))
                .containsExactly("default", "test");
    }

    @Test
    @DisplayName("Duplicate declarations are listed once, first appearance wins")
    void gruntDeduplicates() {
        String source = """
                grunt.loadNpmTasks('grunt-contrib-uglify');
                grunt.registerTask('uglify', ['somethingelse']);
                grunt.registerTask('default', ['uglify']);
                """;
        assertThat(TaskfileParser.gruntTasks(source))
                .containsExactly("uglify", "default");
    }

    // ---------------- gulpfiles ----------------

    @Test
    @DisplayName("gulp v3 task declarations are listed")
    void gulpV3Tasks() {
        String source = """
                var gulp = require('gulp');
                gulp.task('styles', function () { return gulp.src('scss/*'); });
                gulp.task("scripts", ['styles'], function () {});
                gulp.task('default', ['styles', 'scripts']);
                """;
        assertThat(TaskfileParser.gulpTasks(source))
                .containsExactly("styles", "scripts", "default");
    }

    @Test
    @DisplayName("gulp v4 export forms are listed: exports.name and export const")
    void gulpV4Exports() {
        String source = """
                const { series, parallel } = require('gulp');
                function clean(cb) { cb(); }
                exports.clean = clean;
                exports.build = series(clean, parallel(css, js));
                exports.default = exports.build;
                """;
        assertThat(TaskfileParser.gulpTasks(source))
                .containsExactly("clean", "build", "default");

        String esm = """
                import gulp from 'gulp';
                export const styles = () => gulp.src('scss/*');
                export const watch = () => gulp.watch('scss/*', styles);
                """;
        assertThat(TaskfileParser.gulpTasks(esm))
                .containsExactly("styles", "watch");
    }

    @Test
    @DisplayName("Comparisons and identifiers that merely contain the keywords do not count")
    void lookalikesRejected() {
        String source = """
                if (exports.built === true) { console.log('done'); }
                myregisterTask('nope');
                const gulptask = 'not a call';
                """;
        assertThat(TaskfileParser.gulpTasks(source)).isEmpty();
        assertThat(TaskfileParser.gruntTasks(source)).isEmpty();
    }

    @Test
    @DisplayName("Garbage input yields an empty list, never an exception")
    void garbageTolerated() {
        for (String garbage : List.of("", "<<<%%% not js at all",
                "grunt.registerTask(", "gulp.task('unclosed",
                "exports.", "export const ")) {
            assertThat(TaskfileParser.gruntTasks(garbage)).as("grunt: " + garbage).isEmpty();
            assertThat(TaskfileParser.gulpTasks(garbage)).as("gulp: " + garbage).isEmpty();
        }
    }

    @Test
    @DisplayName("The classic-demo shape lists uglify, watch, default and build")
    void classicDemoGruntfile() {
        String source = """
                module.exports = function (grunt) {
                  grunt.initConfig({
                    uglify: { dist: { files: { 'dist/app.min.js': ['js/app.js'] } } },
                    watch: { scripts: { files: ['js/*.js'], tasks: ['uglify'] } }
                  });
                  grunt.loadNpmTasks('grunt-contrib-uglify');
                  grunt.loadNpmTasks('grunt-contrib-watch');
                  grunt.registerTask('default', ['uglify']);
                  grunt.registerTask('build', ['uglify']);
                };
                """;
        assertThat(TaskfileParser.gruntTasks(source))
                .containsExactly("uglify", "watch", "default", "build");
    }
}
