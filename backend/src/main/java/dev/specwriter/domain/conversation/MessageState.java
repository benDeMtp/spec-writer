package dev.specwriter.domain.conversation;

/** {@code RUNNING} pendant un tour ; un arrêt, une panne ou un redémarrage donnent {@code INCOMPLETE}. */
public enum MessageState {
    COMPLETE, INCOMPLETE, RUNNING
}
