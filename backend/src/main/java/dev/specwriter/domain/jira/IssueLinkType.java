package dev.specwriter.domain.jira;

import java.util.Locale;

/** Type de lien entre tickets ({@code issueLinkType}), par exemple « Relates » (FR-035). */
public record IssueLinkType(String id, String name, String inward, String outward) {

    /** Le type « relates to » : par son nom ou son libellé sortant. */
    public boolean relates() {
        return name != null && name.toLowerCase(Locale.ROOT).startsWith("relate")
                || outward != null && outward.toLowerCase(Locale.ROOT).startsWith("relates to");
    }
}
