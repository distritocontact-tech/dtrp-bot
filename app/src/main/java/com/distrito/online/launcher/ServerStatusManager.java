package com.distrito.online.launcher;

import android.os.Handler;
import android.os.Looper;

import java.io.ByteArrayOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

/**
 * Consulta o status real do servidor usando o protocolo de query do
 * SA-MP/open.mp (pacote UDP "SAMP" + opcode 'i' = info). Isso é o mesmo
 * protocolo que qualquer monitor de servidor (samp.rocks, o launcher
 * oficial, etc) usa — não precisa de nenhuma API HTTP própria, só
 * conversar direto com a porta do servidor.
 *
 * Uso:
 *   new ServerStatusManager("00.00.00.00", 7777).fetch(result -> { ... });
 *
 * O callback SEMPRE roda na main thread.
 */
public class ServerStatusManager {

    public static class Result {
        public final boolean success;
        public final String serverName;
        public final int players;
        public final int maxPlayers;

        Result(boolean success, String serverName, int players, int maxPlayers) {
            this.success = success;
            this.serverName = serverName;
            this.players = players;
            this.maxPlayers = maxPlayers;
        }
    }

    public interface Callback {
        void onResult(Result result);
    }

    private final String host;
    private final int port;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public ServerStatusManager(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void fetch(Callback callback) {
        new Thread(() -> {
            Result result = query();
            mainHandler.post(() -> callback.onResult(result));
        }, "server-query-thread").start();
    }

    private Result query() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setSoTimeout(3000);

            InetAddress address = InetAddress.getByName(host);

            ByteArrayOutputStream request = new ByteArrayOutputStream();
            request.write('S');
            request.write('A');
            request.write('M');
            request.write('P');
            byte[] ipParts = address.getAddress();
            request.write(ipParts, 0, ipParts.length);
            request.write(port & 0xFF);
            request.write((port >> 8) & 0xFF);
            request.write('i'); // opcode: informações gerais do servidor

            byte[] requestBytes = request.toByteArray();
            DatagramPacket requestPacket = new DatagramPacket(requestBytes, requestBytes.length, address, port);
            socket.send(requestPacket);

            byte[] responseBuffer = new byte[2048];
            DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
            socket.receive(responsePacket);

            return parseInfoResponse(responsePacket.getData());
        } catch (Exception e) {
            return new Result(false, null, 0, 0);
        }
    }

    /**
     * Layout do pacote de resposta 'i':
     * "SAMP"(4) + ip(4) + port(2) + opcode(1) +
     * password(1) + players(2, LE) + maxplayers(2, LE) +
     * [len(4, LE) + hostname] + [len(4, LE) + gamemode] + [len(4, LE) + language]
     */
    private Result parseInfoResponse(byte[] data) {
        int offset = 11; // pula "SAMP" + ip + port + opcode
        offset += 1; // password
        int players = readUint16LE(data, offset);
        offset += 2;
        int maxPlayers = readUint16LE(data, offset);
        offset += 2;

        int hostnameLen = readInt32LE(data, offset);
        offset += 4;
        String hostname = new String(data, offset, hostnameLen, StandardCharsets.UTF_8);

        return new Result(true, hostname, players, maxPlayers);
    }

    private int readUint16LE(byte[] data, int offset) {
        return (data[offset] & 0xFF) | ((data[offset + 1] & 0xFF) << 8);
    }

    private int readInt32LE(byte[] data, int offset) {
        return (data[offset] & 0xFF)
                | ((data[offset + 1] & 0xFF) << 8)
                | ((data[offset + 2] & 0xFF) << 16)
                | ((data[offset + 3] & 0xFF) << 24);
    }
}
