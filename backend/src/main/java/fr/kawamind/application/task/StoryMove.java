package fr.kawamind.application.task;

/**
 * Ce qui est arrivé à la story de la feature (023, research R8), rendu à l'agent avec la tâche :
 * déplacée, déjà au bon statut, laissée (projet sans réglage) ou en échec, avec un message.
 */
public record StoryMove(String key, Outcome status, String to, String message) {

    public enum Outcome { MOVED, ALREADY, SKIPPED, FAILED }
}
