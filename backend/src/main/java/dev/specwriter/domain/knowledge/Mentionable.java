package dev.specwriter.domain.knowledge;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Document de la connaissance du projet qu'une mention @ peut désigner (007, data-model.md) : un
 * élément par document, référence sans {@code #}, taille indexée totale pour estimer la place.
 */
public record Mentionable(MentionKind kind, String ref, String title, String path, int size) {

    private static final String DOCUMENTS_PREFIX = "docs:";

    public Mentionable {
        Objects.requireNonNull(kind);
        Objects.requireNonNull(ref);
        path = path == null || path.isBlank() ? ref : path;
        title = title == null || title.isBlank() ? fileName(path) : title.strip();
        size = Math.max(0, size);
    }

    /** Élément indexé de tête d'un document, sa sorte déduite de sa référence. */
    public static Mentionable of(KnowledgeKind kind, String ref, String title, int size) {
        String path = ref.startsWith(DOCUMENTS_PREFIX) && ref.indexOf('/') > 0 ? ref.substring(ref.indexOf('/') + 1)
                : ref;
        MentionKind sort = kind == KnowledgeKind.ADR ? MentionKind.ADR
                : ref.startsWith(DOCUMENTS_PREFIX) ? MentionKind.DOCUMENT : MentionKind.PAGE;
        return new Mentionable(sort, ref, kind == KnowledgeKind.ADR || sort == MentionKind.PAGE ? title
                : documentTitle(title, path), path, size);
    }

    /** Morceau indexé, sans son corps : sorte, référence, titre, longueur du corps. */
    public record Part(KnowledgeKind kind, String ref, String title, int length) {
    }

    /**
     * Catalogue d'un projet d'après ses morceaux indexés (research.md R3) : un élément par document,
     * dans l'ordre de première apparition. Section d'un dossier : référence avant le dernier
     * {@code #} ; section Antora : avant le premier ; un ADR garde sa référence. Le titre d'une page
     * est celui de sa section de tête.
     */
    public static List<Mentionable> catalogue(List<Part> parts) {
        Map<String, Part> heads = new LinkedHashMap<>();
        Map<String, Integer> sizes = new LinkedHashMap<>();
        for (Part p : parts) {
            String doc = documentRef(p.kind(), p.ref());
            Part head = heads.get(doc);
            if (head == null || p.ref().equals(doc) && !head.ref().equals(doc)) {
                heads.put(doc, p);
            }
            sizes.merge(doc, p.length(), Integer::sum);
        }
        List<Mentionable> out = new ArrayList<>();
        heads.forEach((doc, head) -> out.add(of(head.kind(), doc, head.title(), sizes.get(doc))));
        return out;
    }

    /** Référence du document d'un morceau indexé. */
    public static String documentRef(KnowledgeKind kind, String ref) {
        if (kind == KnowledgeKind.ADR) {
            return ref;
        }
        int hash = ref.startsWith(DOCUMENTS_PREFIX) ? ref.lastIndexOf('#') : ref.indexOf('#');
        return hash > 0 ? ref.substring(0, hash) : ref;
    }

    /**
     * Noms acceptés après @ (research.md R2) : nom de fichier et chemin, avec et sans extension ;
     * titre d'une page ou d'un ADR ; « ADR-n », « ADR n », « ADRn ».
     */
    public List<String> names() {
        Set<String> out = new LinkedHashSet<>();
        String file = fileName(path);
        out.add(file);
        out.add(withoutExtension(file));
        out.add(path);
        out.add(withoutExtension(path));
        if (kind != MentionKind.DOCUMENT) {
            out.add(title);
        }
        if (kind == MentionKind.ADR) {
            OptionalInt number = AdrNumber.of(ref, title);
            if (number.isPresent()) {
                out.add("ADR-" + number.getAsInt());
                out.add("ADR " + number.getAsInt());
                out.add("ADR" + number.getAsInt());
            }
        }
        out.removeIf(String::isBlank);
        return new ArrayList<>(out);
    }

    /** Nom montré et inséré par défaut : fichier sans extension, titre d'une page, « ADR-n ». */
    public String name() {
        return switch (kind) {
            case DOCUMENT, ATTACHMENT -> withoutExtension(fileName(path));
            case PAGE -> title;
            case ADR -> {
                OptionalInt number = AdrNumber.of(ref, title);
                yield number.isPresent() ? "ADR-" + number.getAsInt() : title;
            }
        };
    }

    /** Comparaison sans casse ni accents, caractère pour caractère (les positions sont gardées). */
    public static String key(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            out.append(fold(text.charAt(i)));
        }
        return out.toString();
    }

    static char fold(char c) {
        if (c < 128) {
            return Character.toLowerCase(c);
        }
        String base = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return base.length() == 1 ? base.toLowerCase(Locale.ROOT).charAt(0) : Character.toLowerCase(c);
    }

    static String fileName(String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }

    static String withoutExtension(String name) {
        int dot = name.lastIndexOf('.');
        int slash = name.lastIndexOf('/');
        return dot > slash + 1 ? name.substring(0, dot) : name;
    }

    /** Titre d'un document de dossier d'après celui d'une section (« Titre — Partie », 006). */
    private static String documentTitle(String sectionTitle, String path) {
        if (sectionTitle == null || sectionTitle.isBlank()) {
            return withoutExtension(fileName(path));
        }
        int dash = sectionTitle.indexOf(" — ");
        return dash > 0 ? sectionTitle.substring(0, dash) : sectionTitle;
    }
}
