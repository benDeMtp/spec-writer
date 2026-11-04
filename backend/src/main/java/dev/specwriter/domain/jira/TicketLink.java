package dev.specwriter.domain.jira;

import java.util.Objects;

/**
 * Lien d'un ticket indexé vers un autre ticket, éventuellement d'un autre projet (FR-044,
 * research.md R29) : son parent, une de ses sous-tâches, ou un lien explicite avec son type, son
 * sens et le libellé Jira de ce sens (« blocks », « is blocked by »).
 */
public record TicketLink(Kind kind, String targetKey, String linkType, Direction direction, String label) {

    /** Nature du lien. */
    public enum Kind {
        PARENT,
        SUBTASK,
        LINK
    }

    /** Sens d'un lien explicite, vu du ticket qui le porte. */
    public enum Direction {
        OUTWARD,
        INWARD
    }

    static final int MAX_LABEL = 100;

    public TicketLink {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(targetKey);
        if (kind == Kind.LINK) {
            Objects.requireNonNull(direction);
            linkType = linkType == null ? "" : linkType;
            label = label == null ? "" : label.length() > MAX_LABEL ? label.substring(0, MAX_LABEL) : label;
        } else {
            linkType = "";
            direction = null;
            label = null;
        }
    }

    public static TicketLink parent(String key) {
        return new TicketLink(Kind.PARENT, key, null, null, null);
    }

    public static TicketLink subtask(String key) {
        return new TicketLink(Kind.SUBTASK, key, null, null, null);
    }

    public static TicketLink link(String type, Direction direction, String label, String key) {
        return new TicketLink(Kind.LINK, key, type, direction, label);
    }
}
