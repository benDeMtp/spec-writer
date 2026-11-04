package dev.specwriter.domain.llm;

import dev.specwriter.domain.project.ContextSheet;
import java.util.List;

/** Sortie de {@code summarizeProjectSources} : des faits sourcés, chacun rangé dans une fiche (R16 de 002). */
public record SourceFacts(List<Fact> facts) {

    public enum Category {
        VISION(ContextSheet.FUNCTIONAL),
        ACTOR(ContextSheet.FUNCTIONAL),
        VOCABULARY(ContextSheet.FUNCTIONAL),
        BUSINESS_RULE(ContextSheet.FUNCTIONAL),
        FUNCTIONAL_CONSTRAINT(ContextSheet.FUNCTIONAL),
        ARCHITECTURE(ContextSheet.TECHNICAL),
        TECHNICAL_CONSTRAINT(ContextSheet.TECHNICAL),
        CODE_CONVENTION(ContextSheet.TECHNICAL),
        INTEGRATION(ContextSheet.TECHNICAL);

        private final ContextSheet sheet;

        Category(ContextSheet sheet) {
            this.sheet = sheet;
        }

        public ContextSheet sheet() {
            return sheet;
        }
    }

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
