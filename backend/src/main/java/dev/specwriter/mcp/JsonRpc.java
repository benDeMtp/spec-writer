package dev.specwriter.mcp;

import java.io.PrintStream;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** JSON-RPC 2.0 sur stdio : un message par ligne, rien d'autre sur stdout (MCP stdio). */
final class JsonRpc {

    static final int PARSE_ERROR = -32700;
    static final int INVALID_REQUEST = -32600;
    static final int METHOD_NOT_FOUND = -32601;
    static final int INVALID_PARAMS = -32602;

    static final JsonMapper JSON = JsonMapper.builder().build();

    private final PrintStream out;

    JsonRpc(PrintStream out) {
        this.out = out;
    }

    void result(JsonNode id, JsonNode result) {
        ObjectNode message = envelope(id);
        message.set("result", result);
        send(message);
    }

    void error(JsonNode id, int code, String text) {
        ObjectNode message = envelope(id);
        ObjectNode error = message.putObject("error");
        error.put("code", code);
        error.put("message", text);
        send(message);
    }

    private static ObjectNode envelope(JsonNode id) {
        ObjectNode message = JSON.createObjectNode();
        message.put("jsonrpc", "2.0");
        message.set("id", id == null ? JSON.nullNode() : id);
        return message;
    }

    private synchronized void send(ObjectNode message) {
        // une ligne par message : le JSON compact ne contient pas de saut de ligne
        out.println(JSON.writeValueAsString(message));
        out.flush();
    }
}
