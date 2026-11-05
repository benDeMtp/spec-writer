package dev.specwriter.domain.llm;

import java.util.List;

/** Tickets qui traitent du même problème, les plus proches d'abord, avec la raison du rapprochement. */
public record RelatedIssuesJudgement(List<Related> related) {

    public static final int MAX_RELATED = 5;

    public record Related(String key, String reason) {
    }

    public RelatedIssuesJudgement {
        related = related == null ? List.of() : List.copyOf(related);
    }
}
