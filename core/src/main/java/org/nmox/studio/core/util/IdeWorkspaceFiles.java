package org.nmox.studio.core.util;

/**
 * Tells the IDE's own per-project bookkeeping apart from the user's
 * source (v1.281.0, the Task Rack persona walk).
 *
 * <p>Every studio persists its workspace beside the project it belongs
 * to: {@code .nmoxrack.json} (the rack patch), {@code .nmoxapi.json},
 * {@code .nmoxdb.json}, {@code .nmoxweb3.json}, {@code .nmoxinfra.json},
 * {@code .nmoxblocks.json}. Those files are the IDE talking to itself.
 * They live in the project directory so they can be committed and
 * shared, but nobody EDITS them the way they edit source — they appear
 * because a window saved, and a save-on-change lane that treats them as
 * source fires the user's test suite for something the user never did.
 *
 * <p>The walk caught this live: pressing Save Patch in the Task Rack
 * wrote {@code .nmoxrack.json}, REFLEX's "code" filter includes
 * {@code json}, and the resulting pipeline launch raised a Workspace
 * Trust prompt. API Studio and DB Studio save the same way on ordinary
 * edits, so an armed REFLEX would have kept re-firing all session.
 *
 * <p>The rule is the naming convention itself, so a studio added later
 * is covered the day it ships — {@code IdeWorkspaceFileGateTest} fails
 * the build if any workspace filename in the product stops matching it.
 */
public final class IdeWorkspaceFiles {

    private static final String PREFIX = ".nmox";
    private static final String SUFFIX = ".json";

    private IdeWorkspaceFiles() {
    }

    /**
     * Is this a file the IDE writes for itself?
     *
     * @param fileName a bare file name, not a path
     * @return true for the studios' own workspace files
     */
    public static boolean isOwn(String fileName) {
        return fileName != null
                && fileName.startsWith(PREFIX)
                && fileName.endsWith(SUFFIX);
    }
}
