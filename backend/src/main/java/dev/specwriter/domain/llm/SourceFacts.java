package dev.specwriter.domain.llm;

import java.util.List;

/** Sortie de {@code summarizeProjectSources} : des faits, chacun avec ses sources (contracts/llm-port.md de 002). */
public record SourceFacts(List<Fact> facts) {

    public enum Category { VOCABULARY, ACTOR, BUSINESS_RULE, CONSTRAINT, CONVENTION }

    /** Un fait extrait d'une page ou d'un ticket ; {@code refs} cite l'URL de la page ou la clé du ticket. */
    public record Fact(Category category, String statement, List<String> refs) {

        public Fact {
            refs = refs == null ? List.of() : List.copyOf(refs);
        }
    }

    public SourceFacts {
        facts = facts == null ? List.of() : List.copyOf(facts);
    }
}
