package dev.specwriter.domain.conversation;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.util.List;

/** Besoin et points établis proposés par l'agent, à relire avant de démarrer une spécification (010, US2). */
public record NeedDraft(String need, List<EstablishedPoint> points) {

    public static final int MAX_POINTS = 10;

    public NeedDraft {
        need = need == null ? "" : need.strip();
        points = points == null ? List.of() : List.copyOf(points);
        if (points.size() > MAX_POINTS) {
            throw new DomainException(ErrorCode.NEED_DRAFT_INVALID, "10 points établis au plus.");
        }
    }
}
