package com.distrito.online.launcher.admin;

import android.util.Log;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Descobre o IP público do aparelho (usado pra registrar na conta do
 * jogador e pra checar/bater contra a lista de IPs banidos). Roda em
 * background e nunca derruba o fluxo do app se falhar — sem internet
 * de verdade pra outras coisas, o resto do launcher já não funciona
 * mesmo.
 */
public final class IpAddressHelper {

    private static final String TAG = "DTRP-IpHelper";
    private static final String IP_ENDPOINT = "https://api.ipify.org?format=text";

    private IpAddressHelper() {}

    public interface Callback {
        void onResult(String ip);
    }

    public static void fetchPublicIp(Callback callback) {
        new Thread(() -> {
            String ip = null;
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(IP_ENDPOINT).openConnection();
                conn.setConnectTimeout(8_000);
                conn.setReadTimeout(8_000);
                conn.connect();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    ip = reader.readLine();
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.w(TAG, "Não foi possível obter o IP público", e);
            }
            callback.onResult(ip);
        }, "ip-lookup-thread").start();
    }

    /** Transforma "191.32.10.5" em "191_32_10_5" pra usar como ID de documento no Firestore. */
    public static String sanitizeForDocId(String ip) {
        if (ip == null) return null;
        return ip.trim().replace(".", "_").replace(":", "-");
    }
}
