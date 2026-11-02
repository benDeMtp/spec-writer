package dev.specwriter.domain.specification;

/** Exigence fonctionnelle ({@code EF-nn}) ou technique ({@code ET-nn}) (FR-010). */
public record Requirement(String code, Category category, String statement) {

    public enum Category {
        FUNCTIONAL,
        TECHNICAL
    }
}
