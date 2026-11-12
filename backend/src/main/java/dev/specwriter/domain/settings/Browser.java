package dev.specwriter.domain.settings;

/**
 * Navigateur installé sur le poste, où ouvrir spec-writer (013, T057) : identifiant stable
 * ({@code chrome}, {@code firefox}…) et nom affiché.
 */
public record Browser(String id, String name) {
}
