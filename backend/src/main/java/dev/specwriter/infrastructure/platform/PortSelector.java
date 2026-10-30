package dev.specwriter.infrastructure.platform;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.ServerSocket;

/** Choix du port d'écoute local : le port configuré s'il est libre, sinon un port libre (R14). */
public final class PortSelector {

    public static final int DEFAULT_PORT = 47615;

    private PortSelector() {
    }

    public static int choose(int preferred) {
        if (preferred > 0 && isFree(preferred)) {
            return preferred;
        }
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException("Aucun port local libre", e);
        }
    }

    private static boolean isFree(int port) {
        try (ServerSocket socket = new ServerSocket(port, 1, InetAddress.getLoopbackAddress())) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
