package dev.specwriter.domain.session;

import java.time.Instant;
import java.util.UUID;

/**
 * Ligne de la liste des sessions (FR-017). Une session dont la sauvegarde est endommagée reste
 * listée, marquée illisible, sans empêcher l'accès aux autres (edge case « fichier corrompu »).
 */
public record SessionListing(UUID id, String name, SessionState state, Instant updatedAt, boolean readable) {
}
