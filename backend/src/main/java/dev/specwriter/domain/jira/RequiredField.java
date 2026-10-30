package dev.specwriter.domain.jira;

import dev.specwriter.domain.error.DomainException;
import dev.specwriter.domain.error.ErrorCode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

/**
 * Champ obligatoire d'un type de ticket, sans valeur par défaut, que la spécification ne remplit pas
 * (FR-036b). {@code kind} décide du formulaire et de la forme de la valeur envoyée à Jira.
 */
public record RequiredField(String id, String name, Kind kind, List<Option> options) {

    public enum Kind { TEXT, NUMBER, DATE, OPTION, OPTIONS, UNSUPPORTED }

    public record Option(String id, String label) {
    }

    public RequiredField {
        options = List.copyOf(options);
    }

    /** Valeur au format attendu par l'API REST v2 de Jira, à partir de la saisie de l'utilisateur. */
    public Object toJiraValue(String input) {
        String value = input == null ? "" : input.strip();
        if (value.isEmpty()) {
            throw invalid("est obligatoire");
        }
        return switch (kind) {
            case TEXT -> value;
            case NUMBER -> number(value);
            case DATE -> date(value);
            case OPTION -> Map.of("id", option(value));
            case OPTIONS -> List.of(Map.of("id", option(value)));
            case UNSUPPORTED -> throw invalid("ne peut pas être rempli depuis spec-writer ; renseignez-le dans Jira");
        };
    }

    private Number number(String value) {
        try {
            // pas d'opérateur ternaire : il convertirait l'entier en double (3 deviendrait 3.0)
            if (value.contains(".")) {
                return Double.parseDouble(value);
            }
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw invalid("doit être un nombre");
        }
    }

    private String date(String value) {
        try {
            return LocalDate.parse(value).toString();
        } catch (DateTimeParseException e) {
            throw invalid("doit être une date (AAAA-MM-JJ)");
        }
    }

    private String option(String value) {
        return options.stream().map(Option::id).filter(value::equals).findFirst()
                .orElseThrow(() -> invalid("doit être une des valeurs proposées"));
    }

    private DomainException invalid(String reason) {
        return new DomainException(ErrorCode.VALIDATION_FAILED, "Le champ Jira « " + name + " » " + reason + ".");
    }
}
