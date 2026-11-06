package dev.specwriter.infrastructure.docs;

import dev.specwriter.domain.port.DocumentationReader.UnreadReason;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Texte des documents Word ({@code .docx}) et des classeurs Excel ({@code .xlsx}) lu directement
 * dans le format OOXML (006, research.md R1) : un zip de fichiers XML. Lecture sans DTD ni entité
 * externe, et bornée : {@link #MAX_UNCOMPRESSED} octets décompressés par document,
 * {@link #MAX_CELLS} cellules par classeur.
 */
final class OoxmlText {

    static final long MAX_UNCOMPRESSED = 50L * 1024 * 1024;
    static final int MAX_CELLS = 50_000;

    private static final byte[] OLE = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};
    private static final Pattern HEADING = Pattern.compile("(?:heading|titre|title)\\s*(\\d)?");
    private static final XMLInputFactory XML = secureFactory();

    private OoxmlText() {
    }

    static Extracted docx(Path file, int maxChars) throws ExtractionException {
        try (Package pkg = Package.open(file)) {
            Text out = new Text(maxChars);
            String firstHeading = pkg.read("word/document.xml", in -> document(in, out));
            return new Extracted(title(pkg, firstHeading, file), out.toString());
        }
    }

    static Extracted xlsx(Path file, int maxChars) throws ExtractionException {
        try (Package pkg = Package.open(file)) {
            List<String[]> sheets = pkg.read("xl/workbook.xml", OoxmlText::sheets);
            Map<String, String> targets = pkg.has("xl/_rels/workbook.xml.rels")
                    ? pkg.read("xl/_rels/workbook.xml.rels", OoxmlText::relationships)
                    : Map.of();
            List<String> shared = pkg.has("xl/sharedStrings.xml") ? pkg.read("xl/sharedStrings.xml", OoxmlText::shared)
                    : List.of();
            Text out = new Text(maxChars);
            int[] cells = {0};
            for (String[] sheet : sheets) {
                String target = targets.get(sheet[1]);
                if (target == null) {
                    continue;
                }
                String entry = target.startsWith("/") ? target.substring(1) : "xl/" + target;
                if (!pkg.has(entry)) {
                    continue;
                }
                List<String> rows = pkg.read(entry, in -> rows(in, shared, cells));
                if (!rows.isEmpty()) {
                    out.block("## " + sheet[0]);
                    out.block(String.join("\n", rows));
                }
            }
            return new Extracted(title(pkg, null, file), out.toString());
        }
    }

    // ---------- Word ----------

    /** Renvoie le premier titre du document, ou {@code null}. */
    private static String document(XMLStreamReader xml, Text out) throws XMLStreamException, ExtractionException {
        String firstHeading = null;
        StringBuilder paragraph = null;
        String style = null;
        boolean inText = false;
        int tables = 0;
        List<String> cells = null;
        StringBuilder cell = null;
        List<String> tableRows = new ArrayList<>();
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (xml.getLocalName()) {
                    case "p" -> {
                        paragraph = new StringBuilder();
                        style = null;
                    }
                    case "pStyle" -> style = attribute(xml, "val");
                    case "t" -> inText = true;
                    case "tab" -> append(paragraph, " ");
                    case "br" -> append(paragraph, "\n");
                    case "tbl" -> tables++;
                    case "tr" -> cells = new ArrayList<>();
                    case "tc" -> cell = new StringBuilder();
                    default -> {
                        // autres éléments : leur texte seul compte
                    }
                }
            } else if (event == XMLStreamConstants.CHARACTERS && inText) {
                append(paragraph, xml.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                switch (xml.getLocalName()) {
                    case "t" -> inText = false;
                    case "p" -> {
                        String text = paragraph == null ? "" : paragraph.toString().strip();
                        if (tables > 0 && cell != null) {
                            if (!text.isEmpty()) {
                                cell.append(cell.isEmpty() ? "" : " ").append(text);
                            }
                        } else if (!text.isEmpty()) {
                            int level = level(style);
                            if (level > 0 && firstHeading == null) {
                                firstHeading = text;
                            }
                            out.block(level > 0 ? "#".repeat(level) + " " + text : text);
                        }
                        paragraph = null;
                    }
                    case "tc" -> {
                        if (cells != null && cell != null) {
                            cells.add(cell.toString().replace("|", "\\|"));
                        }
                        cell = null;
                    }
                    case "tr" -> {
                        if (cells != null && cells.stream().anyMatch(c -> !c.isBlank())) {
                            tableRows.add("| " + String.join(" | ", cells) + " |");
                        }
                        cells = null;
                    }
                    case "tbl" -> {
                        tables--;
                        if (tables == 0 && !tableRows.isEmpty()) {
                            out.block(String.join("\n", tableRows));
                            tableRows.clear();
                        }
                    }
                    default -> {
                        // rien
                    }
                }
            }
        }
        return firstHeading;
    }

    /** Niveau de titre d'un style de paragraphe ({@code Heading2}, {@code Titre1}, {@code Title}), 0 sinon. */
    private static int level(String style) {
        if (style == null) {
            return 0;
        }
        Matcher m = HEADING.matcher(style.toLowerCase(Locale.ROOT));
        if (!m.matches()) {
            return 0;
        }
        int level = m.group(1) == null ? 1 : Integer.parseInt(m.group(1));
        return level < 1 ? 0 : Math.min(level, 3);
    }

    private static void append(StringBuilder paragraph, String text) {
        if (paragraph != null) {
            paragraph.append(text);
        }
    }

    // ---------- Excel ----------

    /** Feuilles dans l'ordre du classeur : nom et identifiant de relation. */
    private static List<String[]> sheets(XMLStreamReader xml) throws XMLStreamException {
        List<String[]> out = new ArrayList<>();
        while (xml.hasNext()) {
            if (xml.next() == XMLStreamConstants.START_ELEMENT && "sheet".equals(xml.getLocalName())) {
                out.add(new String[] {attribute(xml, "name"), attribute(xml, "id")});
            }
        }
        return out;
    }

    private static Map<String, String> relationships(XMLStreamReader xml) throws XMLStreamException {
        Map<String, String> out = new HashMap<>();
        while (xml.hasNext()) {
            if (xml.next() == XMLStreamConstants.START_ELEMENT && "Relationship".equals(xml.getLocalName())) {
                out.put(attribute(xml, "Id"), attribute(xml, "Target"));
            }
        }
        return out;
    }

    /** Chaînes partagées ; le texte phonétique ({@code rPh}) est ignoré. */
    private static List<String> shared(XMLStreamReader xml) throws XMLStreamException {
        List<String> out = new ArrayList<>();
        StringBuilder current = null;
        boolean inText = false;
        int phonetic = 0;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (xml.getLocalName()) {
                    case "si" -> current = new StringBuilder();
                    case "rPh" -> phonetic++;
                    case "t" -> inText = phonetic == 0;
                    default -> {
                        // rien
                    }
                }
            } else if (event == XMLStreamConstants.CHARACTERS && inText && current != null) {
                current.append(xml.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                switch (xml.getLocalName()) {
                    case "si" -> out.add(current == null ? "" : current.toString());
                    case "rPh" -> phonetic--;
                    case "t" -> inText = false;
                    default -> {
                        // rien
                    }
                }
            }
        }
        return out;
    }

    private static List<String> rows(XMLStreamReader xml, List<String> shared, int[] cells)
            throws XMLStreamException, ExtractionException {
        List<String> out = new ArrayList<>();
        List<String> row = null;
        String type = null;
        StringBuilder value = null;
        boolean inValue = false;
        while (xml.hasNext()) {
            int event = xml.next();
            if (event == XMLStreamConstants.START_ELEMENT) {
                switch (xml.getLocalName()) {
                    case "row" -> row = new ArrayList<>();
                    case "c" -> {
                        type = attribute(xml, "t");
                        value = new StringBuilder();
                        if (++cells[0] > MAX_CELLS) {
                            throw new ExtractionException(UnreadReason.TOO_LARGE);
                        }
                    }
                    case "v", "t" -> inValue = true;
                    default -> {
                        // rien
                    }
                }
            } else if (event == XMLStreamConstants.CHARACTERS && inValue && value != null) {
                value.append(xml.getText());
            } else if (event == XMLStreamConstants.END_ELEMENT) {
                switch (xml.getLocalName()) {
                    case "v", "t" -> inValue = false;
                    case "c" -> {
                        String cell = cell(type, value == null ? "" : value.toString(), shared).strip();
                        if (row != null && !cell.isEmpty()) {
                            row.add(cell.replace("|", "\\|"));
                        }
                        value = null;
                    }
                    case "row" -> {
                        if (row != null && !row.isEmpty()) {
                            out.add("| " + String.join(" | ", row) + " |");
                        }
                        row = null;
                    }
                    default -> {
                        // rien
                    }
                }
            }
        }
        return out;
    }

    private static String cell(String type, String raw, List<String> shared) {
        if ("s".equals(type)) {
            try {
                int i = Integer.parseInt(raw.strip());
                return i >= 0 && i < shared.size() ? shared.get(i) : "";
            } catch (NumberFormatException e) {
                return "";
            }
        }
        if ("b".equals(type)) {
            return "1".equals(raw.strip()) ? "VRAI" : "FAUX";
        }
        return raw;
    }

    // ---------- Commun ----------

    /** Titre des propriétés du document, sinon premier titre, sinon nom du fichier sans extension. */
    private static String title(Package pkg, String firstHeading, Path file) throws ExtractionException {
        if (pkg.has("docProps/core.xml")) {
            String title = pkg.read("docProps/core.xml", OoxmlText::coreTitle);
            if (title != null && !title.isBlank()) {
                return title.strip();
            }
        }
        if (firstHeading != null) {
            return firstHeading;
        }
        return PdfText.baseName(file);
    }

    private static String coreTitle(XMLStreamReader xml) throws XMLStreamException {
        while (xml.hasNext()) {
            if (xml.next() == XMLStreamConstants.START_ELEMENT && "title".equals(xml.getLocalName())) {
                return xml.getElementText();
            }
        }
        return null;
    }

    private static String attribute(XMLStreamReader xml, String localName) {
        for (int i = 0; i < xml.getAttributeCount(); i++) {
            if (localName.equals(xml.getAttributeLocalName(i))) {
                return xml.getAttributeValue(i);
            }
        }
        return null;
    }

    private static XMLInputFactory secureFactory() {
        XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_REPLACING_ENTITY_REFERENCES, false);
        return factory;
    }

    /** Lecture d'une partie XML du paquet. */
    private interface PartReader<T> {
        T read(XMLStreamReader xml) throws XMLStreamException, ExtractionException;
    }

    /** Paquet OOXML ouvert, avec un budget d'octets décompressés partagé par toutes ses parties. */
    private static final class Package implements AutoCloseable {
        private final ZipFile zip;
        private long budget = MAX_UNCOMPRESSED;

        private Package(ZipFile zip) {
            this.zip = zip;
        }

        static Package open(Path file) throws ExtractionException {
            try (InputStream in = Files.newInputStream(file)) {
                byte[] head = in.readNBytes(OLE.length);
                if (java.util.Arrays.equals(head, OLE)) {
                    // un document Office protégé par mot de passe est un conteneur OLE chiffré
                    throw new ExtractionException(UnreadReason.PROTECTED);
                }
            } catch (IOException e) {
                throw new ExtractionException(UnreadReason.UNREADABLE);
            }
            try {
                return new Package(new ZipFile(file.toFile()));
            } catch (ZipException e) {
                throw new ExtractionException(UnreadReason.UNREADABLE);
            } catch (IOException e) {
                throw new ExtractionException(UnreadReason.UNAVAILABLE);
            }
        }

        boolean has(String name) {
            return zip.getEntry(name) != null;
        }

        <T> T read(String name, PartReader<T> reader) throws ExtractionException {
            ZipEntry entry = zip.getEntry(name);
            if (entry == null) {
                throw new ExtractionException(UnreadReason.UNREADABLE);
            }
            try (InputStream in = new Bounded(zip.getInputStream(entry), this)) {
                XMLStreamReader xml = XML.createXMLStreamReader(in);
                try {
                    return reader.read(xml);
                } finally {
                    xml.close();
                }
            } catch (TooLarge e) {
                throw new ExtractionException(UnreadReason.TOO_LARGE);
            } catch (XMLStreamException e) {
                if (e.getNestedException() instanceof TooLarge || e.getCause() instanceof TooLarge) {
                    throw new ExtractionException(UnreadReason.TOO_LARGE);
                }
                throw new ExtractionException(UnreadReason.UNREADABLE);
            } catch (IOException e) {
                throw new ExtractionException(UnreadReason.UNREADABLE);
            }
        }

        @Override
        public void close() {
            try {
                zip.close();
            } catch (IOException ignored) {
                // lecture terminée : rien à faire
            }
        }
    }

    private static final class TooLarge extends IOException {
        private static final long serialVersionUID = 1L;
    }

    /** Flux décompressé qui s'arrête au budget du paquet (bombe zip). */
    private static final class Bounded extends FilterInputStream {
        private final Package pkg;

        Bounded(InputStream in, Package pkg) {
            super(in);
            this.pkg = pkg;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                spend(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                spend(n);
            }
            return n;
        }

        private void spend(long n) throws TooLarge {
            pkg.budget -= n;
            if (pkg.budget < 0) {
                throw new TooLarge();
            }
        }
    }

    /** Texte produit, par blocs séparés d'une ligne vide, borné à {@code maxChars} caractères. */
    private static final class Text {
        private final StringBuilder out = new StringBuilder();
        private final int maxChars;

        Text(int maxChars) {
            this.maxChars = maxChars;
        }

        void block(String text) throws ExtractionException {
            if (!out.isEmpty()) {
                out.append("\n\n");
            }
            out.append(text);
            if (out.length() > maxChars) {
                throw new ExtractionException(UnreadReason.TOO_LARGE);
            }
        }

        @Override
        public String toString() {
            return out.toString();
        }
    }
}
