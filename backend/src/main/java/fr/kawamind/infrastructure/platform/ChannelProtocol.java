package fr.kawamind.infrastructure.platform;

import java.util.Map;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/**
 * Messages du canal de l'instance (019, contracts/instance-socket.md) : une ligne JSON par message,
 * en UTF-8, de 4 Kio au plus.
 */
final class ChannelProtocol {

    static final String SOCKET = "instance.sock";
    static final int MAX_LINE = 4096;
    static final int VERSION = 1;
    static final String OPEN = "open";

    enum Status { OPENED, PRINT, STOPPING, UNSUPPORTED }

    record Reply(Status status, String url) {
    }

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private ChannelProtocol() {
    }

    static String openRequest() {
        return JSON.writeValueAsString(Map.of("version", VERSION, "command", OPEN));
    }

    /** Vrai pour une demande d'ouverture de la version connue ; faux pour tout le reste. */
    static boolean isOpenRequest(String line) {
        try {
            Map<?, ?> m = JSON.readValue(line, Map.class);
            return m.get("version") instanceof Number v && v.intValue() == VERSION && OPEN.equals(m.get("command"));
        } catch (JacksonException e) {
            return false;
        }
    }

    static String reply(Status status, String url) {
        return url == null ? JSON.writeValueAsString(Map.of("status", status.name()))
                : JSON.writeValueAsString(Map.of("status", status.name(), "url", url));
    }

    static Reply parseReply(String line) {
        try {
            Map<?, ?> m = JSON.readValue(line, Map.class);
            Status status = Status.valueOf(String.valueOf(m.get("status")));
            return new Reply(status, m.get("url") instanceof String u ? u : null);
        } catch (JacksonException | IllegalArgumentException e) {
            return new Reply(Status.UNSUPPORTED, null);
        }
    }
}
