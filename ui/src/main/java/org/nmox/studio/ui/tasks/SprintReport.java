package org.nmox.studio.ui.tasks;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The sprint's closing report (v2.37.0) — the review-and-retro sibling
 * of {@link StandupReport}: what shipped this sprint, what stayed open,
 * who is still blocked, where the clocked time went, the retro notes,
 * and the velocity line the archived sprints make possible. Markdown,
 * one click, clipboard-bound. Pure so every rule is a unit test, and
 * sections with nothing to say are OMITTED — the Standup's own law.
 */
final class SprintReport {

    private SprintReport() {
    }

    static String build(TaskBoard board, long nowMillis, ZoneId zone) {
        if (!board.hasSprint()) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        LocalDate start = LocalDate.ofInstant(Instant.ofEpochMilli(board.sprintStart()), zone);
        LocalDate end = LocalDate.ofInstant(Instant.ofEpochMilli(board.sprintEnd()), zone);
        b.append("# Sprint ").append(board.sprintName())
                .append(" — ").append(start).append(" … ").append(end).append("\n");

        long windowEnd = board.sprintEnd() + 24L * 60 * 60 * 1000;
        List<String> done = new java.util.ArrayList<>();
        List<String> open = new java.util.ArrayList<>();
        long clockedMs = 0;
        for (TaskBoard.Column c : board.columns()) {
            for (TaskBoard.Card card : c.cards()) {
                if (card.done() > 0 && card.done() >= board.sprintStart()
                        && card.done() < windowEnd) {
                    done.add(card.title());
                } else if (card.done() == 0) {
                    open.add(card.title());
                }
                for (long[] session : card.sessions()) {
                    long s = Math.max(session[0], board.sprintStart());
                    long e = session[1] == 0 ? nowMillis : session[1];
                    e = Math.min(e, windowEnd);
                    if (e > s) {
                        clockedMs += e - s;
                    }
                }
            }
        }
        if (!done.isEmpty()) {
            b.append("\n## Done — ").append(done.size()).append("\n\n");
            for (String t : done) {
                b.append("- ").append(t).append("\n");
            }
        }
        if (!open.isEmpty()) {
            b.append("\n## Open at close — ").append(open.size()).append("\n\n");
            for (String t : open) {
                b.append("- ").append(t).append("\n");
            }
        }
        StringBuilder blockers = new StringBuilder();
        for (TaskBoard.Column c : board.columns()) {
            for (TaskBoard.Card card : c.cards()) {
                if (card.done() == 0 && !card.blockOwner().isEmpty()) {
                    blockers.append("- ").append(card.title())
                            .append(" — ").append(card.blockOwner())
                            .append(" · ").append(card.blockAction()).append("\n");
                }
            }
        }
        if (blockers.length() > 0) {
            b.append("\n## Still blocked\n\n").append(blockers);
        }
        if (clockedMs > 0) {
            b.append("\n## Time\n\nClocked this sprint: ")
                    .append(hours(clockedMs)).append("\n");
        }
        if (!board.retro().isEmpty()) {
            b.append("\n## Retro\n\n").append(board.retro().strip()).append("\n");
        }
        List<TaskBoard.ClosedSprint> history = board.sprintHistory();
        if (!history.isEmpty()) {
            b.append("\n## Velocity\n\n");
            int shown = Math.min(3, history.size());
            for (int i = history.size() - shown; i < history.size(); i++) {
                TaskBoard.ClosedSprint cs = history.get(i);
                b.append("- ").append(cs.name()).append(": ")
                        .append(cs.done()).append(" done\n");
            }
            b.append("- ").append(board.sprintName()).append(" (this sprint): ")
                    .append(done.size()).append(" done\n");
        }
        return b.toString();
    }

    private static String hours(long ms) {
        long minutes = ms / 60_000;
        return (minutes / 60) + "h " + String.format("%02dm", minutes % 60);
    }
}
