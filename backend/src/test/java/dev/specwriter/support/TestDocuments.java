package dev.specwriter.support;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Jeu de documents d'un dossier SharePoint synchronisé (006, quickstart.md), généré à la demande :
 * aucun document réel n'est versionné. Les fichiers Word et Excel sont des OOXML minimaux, les PDF
 * sont produits par PDFBox.
 */
public final class TestDocuments {

    private TestDocuments() {
    }

    /** Écrit tout le jeu sous {@code root} et le renvoie. */
    public static Path writeAll(Path root) throws IOException {
        docx(root.resolve("Specs/Aurora.docx"), "Aurora", List.of(
                "H1:Contexte", "Aurora publie l'IHM des agents.",
                "H2:Exigences", "L'authentification passe par GAIA.",
                "TABLE:Composant|Version;nginx|1.27",
                "H1:Exploitation", "Les journaux partent vers Elastic."));
        xlsx(root.resolve("Suivi.xlsx"), Map.of(
                "Jalons", List.of(List.of("Jalon", "Date"), List.of("Recette", "2026-12-01")),
                "Risques", List.of(List.of("Risque", "Niveau"), List.of("Quota Graph", "moyen"))));
        pdf(root.resolve("Exploitation.pdf"), "Exploitation", List.of("Supervision par Prometheus.",
                "Sauvegarde quotidienne des volumes."));
        Files.createDirectories(root);
        Files.writeString(root.resolve("Accueil.html"), """
                <html><head><title>Accueil</title><script>alert(1)</script></head>
                <body><h1>Accueil</h1><p>Bienvenue sur le portail Aurora.</p>
                <h2>Contacts</h2><p>Equipe Relai.</p></body></html>""");
        docx(root.resolve("Decisions/0017-ihm-nginx.docx"), "17. IHM publiée par nginx", List.of(
                "Statut : accepté", "H1:Contexte", "L'IHM est servie par nginx avec OIDC.",
                "H1:Décision", "Une image nginx par environnement."));
        pdf(root.resolve("Archives/2023.pdf"), "Archives 2023", List.of("Ancien bilan."));
        Files.writeString(root.resolve("vieux.doc"), "binaire");
        docx(root.resolve("~$Aurora.docx"), "verrou", List.of("verrou"));
        protectedOffice(root.resolve("protege.docx"));
        scannedPdf(root.resolve("scan.pdf"));
        return root;
    }

    /**
     * Document Word minimal. Lignes : {@code H1:titre}, {@code H2:titre}, {@code TABLE:a|b;c|d}, sinon
     * un paragraphe.
     */
    public static void docx(Path file, String title, List<String> lines) throws IOException {
        StringBuilder body = new StringBuilder();
        for (String line : lines) {
            if (line.startsWith("H1:") || line.startsWith("H2:")) {
                body.append("<w:p><w:pPr><w:pStyle w:val=\"Heading").append(line.charAt(1)).append("\"/></w:pPr>")
                        .append(run(line.substring(3))).append("</w:p>");
            } else if (line.startsWith("TABLE:")) {
                body.append("<w:tbl>");
                for (String row : line.substring(6).split(";")) {
                    body.append("<w:tr>");
                    for (String cell : row.split("\\|")) {
                        body.append("<w:tc><w:p>").append(run(cell)).append("</w:p></w:tc>");
                    }
                    body.append("</w:tr>");
                }
                body.append("</w:tbl>");
            } else {
                body.append("<w:p>").append(run(line)).append("</w:p>");
            }
        }
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("[Content_Types].xml", "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\"/>");
        entries.put("word/document.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>"
                + body + "</w:body></w:document>");
        entries.put("docProps/core.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\""
                + " xmlns:dc=\"http://purl.org/dc/elements/1.1/\"><dc:title>" + escape(title)
                + "</dc:title></cp:coreProperties>");
        zip(file, entries);
    }

    /** Classeur Excel minimal : chaînes partagées pour la première ligne, en ligne pour les autres. */
    public static void xlsx(Path file, Map<String, List<List<String>>> sheets) throws IOException {
        Map<String, String> entries = new LinkedHashMap<>();
        StringBuilder workbook = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\""
                + " xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\"><sheets>");
        StringBuilder rels = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">");
        StringBuilder shared = new StringBuilder();
        int strings = 0;
        int n = 0;
        for (Map.Entry<String, List<List<String>>> sheet : new java.util.TreeMap<>(sheets).entrySet()) {
            n++;
            workbook.append("<sheet name=\"").append(escape(sheet.getKey())).append("\" sheetId=\"").append(n)
                    .append("\" r:id=\"rId").append(n).append("\"/>");
            rels.append("<Relationship Id=\"rId").append(n)
                    .append("\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\"")
                    .append(" Target=\"worksheets/sheet").append(n).append(".xml\"/>");
            StringBuilder data = new StringBuilder();
            int r = 0;
            for (List<String> row : sheet.getValue()) {
                r++;
                data.append("<row r=\"").append(r).append("\">");
                for (String value : row) {
                    if (r == 1) {
                        shared.append("<si><t>").append(escape(value)).append("</t></si>");
                        data.append("<c t=\"s\"><v>").append(strings++).append("</v></c>");
                    } else {
                        data.append("<c t=\"inlineStr\"><is><t>").append(escape(value)).append("</t></is></c>");
                    }
                }
                data.append("</row>");
            }
            entries.put("xl/worksheets/sheet" + n + ".xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\"><sheetData>"
                    + data + "</sheetData></worksheet>");
        }
        entries.put("xl/workbook.xml", workbook.append("</sheets></workbook>").toString());
        entries.put("xl/_rels/workbook.xml.rels", rels.append("</Relationships>").toString());
        entries.put("xl/sharedStrings.xml", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">" + shared + "</sst>");
        zip(file, entries);
    }

    /** PDF d'une page par texte, titre dans les propriétés du document. */
    public static void pdf(Path file, String title, List<String> pages) throws IOException {
        Files.createDirectories(file.getParent());
        try (PDDocument doc = new PDDocument()) {
            doc.getDocumentInformation().setTitle(title);
            for (String text : pages) {
                PDPage page = new PDPage();
                doc.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(doc, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 700);
                    content.showText(text);
                    content.endText();
                }
            }
            doc.save(file.toFile());
        }
    }

    /** PDF dont l'ouverture exige un mot de passe. */
    public static void encryptedPdf(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            StandardProtectionPolicy policy = new StandardProtectionPolicy("proprietaire", "secret", new AccessPermission());
            policy.setEncryptionKeyLength(128);
            doc.protect(policy);
            doc.save(file.toFile());
        }
    }

    /** PDF sans texte, comme un document scanné. */
    public static void scannedPdf(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        try (PDDocument doc = new PDDocument()) {
            doc.addPage(new PDPage());
            doc.save(file.toFile());
        }
    }

    /** Document Office protégé par mot de passe : un conteneur OLE, pas un zip. */
    public static void protectedOffice(Path file) throws IOException {
        Files.createDirectories(file.getParent());
        byte[] content = new byte[512];
        content[0] = (byte) 0xD0;
        content[1] = (byte) 0xCF;
        content[2] = 0x11;
        content[3] = (byte) 0xE0;
        Files.write(file, content);
    }

    /** Bombe zip : un {@code word/document.xml} de {@code megabytes} Mo, très compressé. */
    public static void zipBomb(Path file, int megabytes) throws IOException {
        Files.createDirectories(file.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            byte[] block = new byte[1024 * 1024];
            java.util.Arrays.fill(block, (byte) ' ');
            for (int i = 0; i < megabytes; i++) {
                zip.write(block);
            }
            zip.closeEntry();
        }
    }

    /** Zip dont le contenu XML est fourni tel quel. */
    public static void zip(Path file, Map<String, String> entries) throws IOException {
        Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(e.getKey()));
                zip.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
    }

    private static String run(String text) {
        return "<w:r><w:t xml:space=\"preserve\">" + escape(text) + "</w:t></w:r>";
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
