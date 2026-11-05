package db.migration;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.jboss.logging.Logger;

/**
 * Fiche générale (feature 002, research.md R25) : pour chaque projet, la fiche fonctionnelle, la
 * fiche technique et les réponses aux questions Q-n sont réunies dans la colonne {@code context},
 * sous « ## Fonctionnel », « ## Technique » et « ## Réponses de clarification ». Une fiche seule,
 * sans réponse, est gardée telle quelle ; l'empreinte de chaque fiche est recalculée. Au-delà de 20 000 caractères, la fiche est tronquée et un
 * avertissement est journalisé (sans contenu). Puis les colonnes de la fiche technique, l'origine de
 * la fiche et la table des questions sont supprimées. En Java pour pouvoir journaliser.
 */
public class V11__general_context extends BaseJavaMigration {

    static final int MAX_LENGTH = 20_000;

    private static final Logger LOG = Logger.getLogger(V11__general_context.class);

    private record Sheet(String id, String functional, String functionalAt, String technical, String technicalAt) {
    }

    @Override
    public void migrate(Context context) throws SQLException {
        Connection c = context.getConnection();
        List<Sheet> sheets = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("""
                select id, context, context_updated_at, technical_context, technical_context_updated_at
                from project order by id""")) {
            while (r.next()) {
                sheets.add(new Sheet(r.getString(1), text(r.getString(2)), r.getString(3), text(r.getString(4)),
                        r.getString(5)));
            }
        }
        try (PreparedStatement update = c.prepareStatement(
                "update project set context = ?, context_hash = ?, context_updated_at = ? where id = ?")) {
            for (Sheet sheet : sheets) {
                List<String> answers = answers(c, sheet.id());
                // une fiche seule, sans réponse, est gardée telle quelle ; seule son empreinte est recalculée
                String merged = sheet.technical().isBlank() && answers.isEmpty() ? sheet.functional().strip()
                        : merge(sheet.functional(), sheet.technical(), answers);
                if (merged.length() > MAX_LENGTH) {
                    LOG.warnf("Projet %s : fiche générale tronquée à 20 000 caractères (%d avant la fusion)",
                            sheet.id(), merged.length());
                    merged = merged.substring(0, MAX_LENGTH);
                }
                update.setString(1, merged);
                update.setString(2, merged.isEmpty() ? "" : sha256(merged));
                update.setString(3, sheet.technical().isBlank() ? sheet.functionalAt()
                        : latest(sheet.functionalAt(), sheet.technicalAt()));
                update.setString(4, sheet.id());
                update.executeUpdate();
            }
        }
        try (Statement s = c.createStatement()) {
            s.execute("DROP TABLE project_context_question");
            for (String column : List.of("technical_context", "technical_context_hash", "technical_context_origin",
                    "technical_context_updated_at", "context_origin")) {
                s.execute("ALTER TABLE project DROP COLUMN " + column);
            }
        }
    }

    /** Réponses Q-n données (une question passée n'a pas de réponse), dans l'ordre des numéros. */
    private static List<String> answers(Connection c, String projectId) throws SQLException {
        List<String> out = new ArrayList<>();
        try (PreparedStatement p = c.prepareStatement("""
                select number, label, answer from project_context_question
                where project_id = ? and state = 'ANSWERED' and trim(answer) <> '' order by number""")) {
            p.setString(1, projectId);
            try (ResultSet r = p.executeQuery()) {
                while (r.next()) {
                    out.add("- Q-" + r.getInt(1) + " " + r.getString(2).strip() + " " + r.getString(3).strip());
                }
            }
        }
        return out;
    }

    static String merge(String functional, String technical, List<String> answers) {
        List<String> parts = new ArrayList<>();
        if (!functional.isBlank()) {
            parts.add("## Fonctionnel\n\n" + functional.strip());
        }
        if (!technical.isBlank()) {
            parts.add("## Technique\n\n" + technical.strip());
        }
        if (!answers.isEmpty()) {
            parts.add("## Réponses de clarification\n\n" + String.join("\n", answers));
        }
        return String.join("\n\n", parts);
    }

    /** Date ISO-8601 la plus récente ; les dates sont écrites au même format, comparables comme textes. */
    private static String latest(String a, String b) {
        if (a == null) {
            return b;
        }
        return b == null || a.compareTo(b) >= 0 ? a : b;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }
}
