package dev.specwriter.domain.conversation;

/**
 * Sorte d'une source que l'agent peut chercher et citer (010, research.md R3). {@code DISCUSSION} :
 * message de l'utilisateur dans la discussion d'une spécification, citable seulement par une
 * proposition de version (011).
 */
public enum SourceKind {
    TICKET, DOC_SECTION, ADR, ARCH_ELEMENT, SPECIFICATION, ATTACHMENT, DISCUSSION, MODULE
}
