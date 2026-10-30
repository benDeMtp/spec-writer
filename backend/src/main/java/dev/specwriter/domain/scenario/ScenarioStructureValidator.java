package dev.specwriter.domain.scenario;

import dev.specwriter.domain.specification.SpecificationContent;
import dev.specwriter.domain.specification.StructureViolations;
import dev.specwriter.domain.specification.UseCase;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Contrôle de structure des scénarios, règles 1 à 7 de contracts/scenario-format.md (FR-012). */
public final class ScenarioStructureValidator {

    public static final int MAX_FIELD = 1_000;
    private static final Pattern CODE = Pattern.compile("SC-\\d{2}-\\d{2}");

    private ScenarioStructureValidator() {
    }

    public static StructureViolations validate(SpecificationContent content) {
        Set<String> useCases = content.useCases().stream().map(UseCase::code).collect(Collectors.toSet());
        Set<String> codes = new HashSet<>();
        List<String> out = new ArrayList<>();
        for (AcceptanceScenario s : content.scenarios()) {
            String c = s.code();
            if (blank(s.asA())) {
                out.add(c + " : « En tant que » est obligatoire");
            }
            if (s.when().isEmpty() || s.when().stream().allMatch(ScenarioStructureValidator::blank)) {
                out.add(c + " : « Quand » doit avoir au moins une entrée");
            } else if (s.when().stream().anyMatch(ScenarioStructureValidator::blank)) {
                out.add(c + " : « Quand » contient une entrée vide");
            }
            if (s.then().isEmpty() || s.then().stream().allMatch(ScenarioStructureValidator::blank)) {
                out.add(c + " : « Alors » doit avoir au moins une entrée");
            } else if (s.then().stream().anyMatch(ScenarioStructureValidator::blank)) {
                out.add(c + " : « Alors » contient une entrée vide");
            }
            if (s.given().stream().anyMatch(ScenarioStructureValidator::blank)) {
                out.add(c + " : « Étant donné » contient une entrée vide");
            }
            if (!useCases.contains(s.useCaseCode())) {
                out.add(c + " : le use case référencé n'existe pas");
            }
            if (c == null || !CODE.matcher(c).matches()) {
                out.add(c + " : le code doit suivre SC-<nn>-<nn>");
            } else if (!codes.add(c)) {
                out.add(c + " : code en double");
            }
            if (blank(s.title())) {
                out.add(c + " : le titre est obligatoire");
            }
            if (tooLong(s)) {
                out.add(c + " : un champ dépasse 1 000 caractères");
            }
        }
        return new StructureViolations(out);
    }

    private static boolean tooLong(AcceptanceScenario s) {
        List<String> fields = new ArrayList<>(List.of(nz(s.title()), nz(s.asA())));
        fields.addAll(s.given());
        fields.addAll(s.when());
        fields.addAll(s.then());
        return fields.stream().anyMatch(f -> f.length() > MAX_FIELD);
    }

    private static String nz(String value) {
        return value == null ? "" : value;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
