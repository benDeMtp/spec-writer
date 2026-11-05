package dev.specwriter.domain.llm;

import java.util.List;

/** Ce qui est commun au besoin et au ticket, ce qui diffère, et une suggestion qui n'engage pas l'utilisateur. */
public record IssueComparison(List<String> common, List<String> different, Suggestion suggestion) {

    public static final int MAX_POINTS = 8;

    public enum Suggestion { SAME, DIFFERENT }

    public IssueComparison {
        common = common == null ? List.of() : common.stream().limit(MAX_POINTS).toList();
        different = different == null ? List.of() : different.stream().limit(MAX_POINTS).toList();
    }
}
