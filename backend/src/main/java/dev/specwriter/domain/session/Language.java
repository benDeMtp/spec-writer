package dev.specwriter.domain.session;

/** Langue d'une session et de la spécification produite (FR-015). */
public enum Language {
    FR("fr"),
    EN("en");

    private final String code;

    Language(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static Language fromCode(String code) {
        for (Language l : values()) {
            if (l.code.equalsIgnoreCase(code)) {
                return l;
            }
        }
        throw new IllegalArgumentException("Langue non prise en charge : " + code);
    }
}
