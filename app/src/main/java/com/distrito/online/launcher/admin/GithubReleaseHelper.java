package com.distrito.online.launcher.admin;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Busca a última release publicada no GitHub (repo distritocontact-tech/drtp)
 * usando o token pessoal salvo só no aparelho do admin (GithubTokenManager).
 * É só uma conveniência pro admin não precisar copiar o link do APK na mão
 * toda vez — o que efetivamente manda a atualização pros players é o
 * AccountDirectory#publishUpdate, que escreve no Firestore.
 */
public final class GithubReleaseHelper {

    private static final String TAG = "DTRP-GithubRelease";
    private static final String RELEASES_URL =
            "https://api.github.com/repos/distritocontact-tech/drtp/releases/latest";

    private GithubReleaseHelper() {}

    public static class ReleaseInfo {
        public String tagName;
        public String apkDownloadUrl;
    }

    public interface Callback {
        void onResult(ReleaseInfo info, Exception error);
    }

    public static void fetchLatestRelease(String token, Callback callback) {
        new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(RELEASES_URL).openConnection();
                conn.setConnectTimeout(10_000);
                conn.setReadTimeout(10_000);
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                if (token != null && !token.trim().isEmpty()) {
                    conn.setRequestProperty("Authorization", "token " + token.trim());
                }
                conn.connect();

                if (conn.getResponseCode() >= 400) {
                    callback.onResult(null, new Exception("GitHub retornou " + conn.getResponseCode()
                            + " — confira se o token é válido e tem acesso ao repositório."));
                    return;
                }

                String body;
                try (InputStream in = conn.getInputStream()) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[8192];
                    int read;
                    while ((read = in.read(data)) != -1) buffer.write(data, 0, read);
                    body = buffer.toString("UTF-8");
                }
                conn.disconnect();

                JSONObject json = new JSONObject(body);
                ReleaseInfo info = new ReleaseInfo();
                info.tagName = json.optString("tag_name", null);

                JSONArray assets = json.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject asset = assets.getJSONObject(i);
                        String name = asset.optString("name", "");
                        if (name.toLowerCase().endsWith(".apk")) {
                            info.apkDownloadUrl = asset.optString("browser_download_url", null);
                            break;
                        }
                    }
                }
                callback.onResult(info, null);
            } catch (Exception e) {
                Log.e(TAG, "Falha ao buscar release do GitHub", e);
                callback.onResult(null, e);
            }
        }, "github-release-fetch-thread").start();
    }
}
