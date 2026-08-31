package com.distrito.online.launcher;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

import com.distrito.online.launcher.admin.AccountDirectory;

/**
 * Checa (via o manifest.json publicado nas releases do GitHub) se
 * existe uma versão do APK mais nova que a instalada, e — se o player
 * aceitar — baixa o APK novo e abre o instalador do Android.
 *
 * Diferente do "data" (arquivos do jogo), o APK em si NUNCA pode ser
 * trocado em silêncio: o Android sempre exige a tela nativa de
 * instalação/confirmação. O que dá pra automatizar é só baixar o
 * arquivo e já abrir essa tela pro player, em vez de mandar ele pro
 * navegador manualmente.
 */
public class AppUpdateManager {

    private static final String TAG = "AppUpdateManager";

    private final Context context;

    public AppUpdateManager(Context context) {
        this.context = context.getApplicationContext();
    }

    public interface DownloadCallback {
        void onProgress(int percent);
        void onSuccess(File apkFile);
        void onError(Exception e);
    }

    public interface UpdateCheckCallback {
        void onResult(boolean updateAvailable, String apkUrl);
    }

    private static final String MANIFEST_URL =
            "https://github.com/distritocontact-tech/drtp/releases/download/meta/manifest.json";

    /**
     * Checa primeiro se o admin publicou um link de atualização pelo
     * painel (Firestore config/app_update) — isso deixa o admin mandar
     * um APK novo pros players na hora, sem depender do bot de release
     * do GitHub. Se não tiver nada publicado lá (ou o versionCode não for
     * maior que o instalado), cai pro manifest.json do GitHub como
     * antes. Roda em background; se nada estiver acessível, reporta que
     * não há atualização.
     */
    public void checkForUpdate(UpdateCheckCallback callback) {
        new AccountDirectory().fetchPublishedUpdate((apkUrl, versionCode, versionName) -> {
            if (apkUrl != null && !apkUrl.isEmpty() && isUpdateAvailable(versionCode)) {
                callback.onResult(true, apkUrl);
                return;
            }
            checkGithubManifestForUpdate(callback);
        });
    }

    private void checkGithubManifestForUpdate(UpdateCheckCallback callback) {
        new Thread(() -> {
            try {
                org.json.JSONObject manifest = fetchManifest();
                int remoteVersionCode = manifest.optInt("app_version_code", -1);
                String apkUrl = manifest.optString("apk_url", null);
                boolean available = isUpdateAvailable(remoteVersionCode) && apkUrl != null && !apkUrl.isEmpty();
                callback.onResult(available, apkUrl);
            } catch (Exception e) {
                Log.w(TAG, "Não foi possível checar manifest.json (offline / GitHub fora do ar?)", e);
                callback.onResult(false, null);
            }
        }, "app-update-check-thread").start();
    }

    private org.json.JSONObject fetchManifest() throws IOException, org.json.JSONException {
        HttpURLConnection conn = (HttpURLConnection) new URL(MANIFEST_URL).openConnection();
        conn.setInstanceFollowRedirects(true);
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(10_000);
        conn.connect();
        try (InputStream in = conn.getInputStream()) {
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] data = new byte[8192];
            int read;
            while ((read = in.read(data)) != -1) buffer.write(data, 0, read);
            return new org.json.JSONObject(buffer.toString("UTF-8"));
        } finally {
            conn.disconnect();
        }
    }

    /** true se o versionCode remoto (vindo do manifest) é maior que o instalado. */
    public boolean isUpdateAvailable(int remoteVersionCode) {
        if (remoteVersionCode <= 0) return false;
        try {
            int current = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionCode;
            return remoteVersionCode > current;
        } catch (Exception e) {
            Log.w(TAG, "Não foi possível ler o versionCode instalado", e);
            return false;
        }
    }

    /** Baixa o APK em background para a pasta de cache do app. */
    public void downloadApk(String apkUrl, DownloadCallback callback) {
        new Thread(() -> {
            File dest = new File(context.getCacheDir(), "distrito-update.apk");
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(apkUrl).openConnection();
                conn.setInstanceFollowRedirects(true);
                conn.setConnectTimeout(15_000);
                conn.setReadTimeout(30_000);
                conn.connect();

                long total = conn.getContentLengthLong();
                long downloaded = 0;
                try (InputStream in = conn.getInputStream();
                     OutputStream out = new FileOutputStream(dest)) {
                    byte[] buffer = new byte[64 * 1024];
                    int read;
                    int lastPercent = -1;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                        downloaded += read;
                        if (total > 0) {
                            int percent = (int) Math.min(100, (downloaded * 100L) / total);
                            if (percent != lastPercent) {
                                lastPercent = percent;
                                callback.onProgress(percent);
                            }
                        }
                    }
                } finally {
                    conn.disconnect();
                }
                callback.onSuccess(dest);
            } catch (IOException e) {
                Log.e(TAG, "Falha ao baixar APK de atualização", e);
                callback.onError(e);
            }
        }, "apk-update-download-thread").start();
    }

    /** Abre a tela nativa do Android para instalar o APK já baixado. */
    public void promptInstall(File apkFile) {
        Uri uri = FileProvider.getUriForFile(context, context.getPackageName() + ".provider", apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
