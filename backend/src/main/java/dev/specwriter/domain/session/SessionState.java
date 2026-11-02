package dev.specwriter.domain.session;

/** Cycle de vie d'une session (data-model.md, Session). */
public enum SessionState {
    QUESTIONING,
    READY_TO_GENERATE,
    GENERATED
}
