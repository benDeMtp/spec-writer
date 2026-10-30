package dev.specwriter.domain.consent;

import java.time.Instant;

/** Accord ou retrait d'accord ; l'historique est conservé (data-model.md, ConsentRecord). */
public record ConsentRecord(ConsentService service, boolean granted, Instant at) {
}
