package dev.specwriter.domain.sources;

/**
 * Qualification d'un module du périmètre que la discussion a établie (feature 013, T056) : par
 * exemple « uta est en Quarkus, il n'est pas impacté ». {@code module} : nom ou chemin du module,
 * tel que le périmètre le donne. Une correction ne retire jamais un module : il reste listé, avec sa
 * nouvelle qualification.
 */
public record ModuleCorrection(String module, Qualification qualification, String justification) {

    public static final int MAX_JUSTIFICATION = 500;
}
