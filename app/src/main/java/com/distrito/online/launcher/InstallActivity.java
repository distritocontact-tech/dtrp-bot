package com.distrito.online.launcher;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.distrito.online.R;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Tela de instalação (segunda imagem de referência): baixa os dois
 * arquivos de dados do jogo (part00 + part01), junta e extrai tudo na
 * pasta "com.distrito.online" dentro do armazenamento do app.
 *
 * Regra de segurança/consistência: a instalação só é considerada válida
 * se as DUAS partes baixarem completas e a extração terminar sem erro.
 * Enquanto isso não acontece, o app fica marcado como "instalação em
 * andamento" numa flag persistida (SharedPreferences). Se o processo for
 * morto no meio do caminho (o usuário fecha o app, o sistema mata o
 * processo, queda de conexão etc.) e o player abrir o app de novo, essa
 * flag ainda vai estar "em andamento" — nesse caso TODOS os arquivos
 * (parciais ou não) são apagados e a instalação recomeça do zero. Isso
 * evita ficar com uma pasta de jogo corrompida/incompleta rodando.
 */
public class InstallActivity extends BaseLauncherActivity {

    private static final String TAG = "InstallActivity";

    private static final String PART_00_URL =
            "https://github.com/distritocontact-tech/Datadtrpmob/releases/download/untagged-b5d52a2150db72f2800a/distrito-data.part00.bin";
    private static final String PART_01_URL =
            "https://github.com/distritocontact-tech/Datadtrpmob/releases/download/untagged-b5d52a2150db72f2800a/distrito-data.part01.bin";

    private static final String PREFS = "install_state";
    private static final String KEY_STATE = "state";
    private static final String STATE_IN_PROGRESS = "in_progress";
    private static final String STATE_COMPLETE = "complete";

    private static final String INSTALL_FOLDER_NAME = "com.distrito.online";
    private static final String PARTS_FOLDER_NAME = "distrito_parts";

    private ProgressBar progressBar;
    private TextView txtCurrentFile;
    private TextView txtBytesProgress;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.install_screen);
        playEntryAnimationIfPending(findViewById(android.R.id.content));

        progressBar = findViewById(R.id.progress_bar);
        txtCurrentFile = findViewById(R.id.txt_current_file);
        txtBytesProgress = findViewById(R.id.txt_bytes_progress);
        progressBar.setMax(10000);

        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        String state = prefs.getString(KEY_STATE, null);

        if (STATE_IN_PROGRESS.equals(state)) {
            // A última tentativa foi interrompida no meio: descarta tudo
            // (partes baixadas e qualquer coisa já extraída) e recomeça.
            wipeEverything();
        }

        prefs.edit().putString(KEY_STATE, STATE_IN_PROGRESS).apply();
        startInstall();
    }

    @Override
    protected void onDestroy() {
        cancelled = true;
        super.onDestroy();
    }

    private void startInstall() {
        new Thread(() -> {
            try {
                File partsDir = new File(getFilesDir(), PARTS_FOLDER_NAME);
                if (!partsDir.exists()) partsDir.mkdirs();

                File part00 = new File(partsDir, "distrito-data.part00.bin");
                File part01 = new File(partsDir, "distrito-data.part01.bin");

                long total00 = remoteContentLength(PART_00_URL);
                long total01 = remoteContentLength(PART_01_URL);
                long grandTotal = Math.max(0, total00) + Math.max(0, total01);

                downloadFile(PART_00_URL, part00, "distrito-data.part00.bin", 0, grandTotal);
                if (cancelled) return;
                downloadFile(PART_01_URL, part01, "distrito-data.part01.bin", Math.max(0, total00), grandTotal);
                if (cancelled) return;

                extractParts(part00, part01);
                if (cancelled) return;

                // tudo certo: limpa as partes baixadas (não precisamos mais
                // delas) e marca a instalação como concluída.
                deleteRecursive(partsDir);
                getSharedPreferences(PREFS, MODE_PRIVATE)
                        .edit().putString(KEY_STATE, STATE_COMPLETE).apply();

                mainHandler.post(this::goToGame);
            } catch (Exception e) {
                Log.e(TAG, "Falha na instalação, revertendo tudo", e);
                wipeEverything();
                mainHandler.post(() -> {
                    if (!isFinishing()) {
                        // Instalação falhou: volta pra tela anterior pra o
                        // player poder tentar de novo (o botão volta a
                        // aparecer como "Instalação necessária").
                        onBackPressed();
                    }
                });
            }
        }, "distrito-install-thread").start();
    }

    private long remoteContentLength(String urlStr) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlStr).openConnection();
            connection.setInstanceFollowRedirects(true);
            connection.setRequestMethod("HEAD");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(15_000);
            connection.connect();
            return connection.getContentLengthLong();
        } catch (IOException e) {
            return -1;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void downloadFile(String urlStr, File dest, String displayName,
                               long bytesAlreadyCountedFromOtherParts, long grandTotal) throws IOException {
        mainHandler.post(() -> txtCurrentFile.setText("Baixando: " + displayName));

        HttpURLConnection connection = (HttpURLConnection) new URL(urlStr).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(30_000);
        connection.connect();

        long contentLength = connection.getContentLengthLong();
        long effectiveTotal = grandTotal > 0 ? grandTotal
                : Math.max(1, bytesAlreadyCountedFromOtherParts + Math.max(0, contentLength));

        try (InputStream in = connection.getInputStream();
             OutputStream out = new FileOutputStream(dest)) {

            byte[] buffer = new byte[64 * 1024];
            long readSoFar = 0;
            int read;
            long lastUiUpdate = 0;

            while (!cancelled && (read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                readSoFar += read;

                long now = System.currentTimeMillis();
                if (now - lastUiUpdate > 60) { // ~16fps é suficiente pra uma barra de progresso
                    lastUiUpdate = now;
                    long totalDownloaded = bytesAlreadyCountedFromOtherParts + readSoFar;
                    postProgress(totalDownloaded, effectiveTotal);
                }
            }
            postProgress(bytesAlreadyCountedFromOtherParts + readSoFar, effectiveTotal);
        } finally {
            connection.disconnect();
        }

        if (cancelled) {
            throw new IOException("Download cancelado (activity destruída)");
        }
    }

    private void postProgress(long downloaded, long total) {
        int progress = total > 0 ? (int) Math.min(10000, (downloaded * 10000L) / total) : 0;
        String downloadedMb = String.format("%.1f", downloaded / (1024f * 1024f));
        String totalMb = String.format("%.1f", total / (1024f * 1024f));
        mainHandler.post(() -> {
            progressBar.setProgress(progress);
            txtBytesProgress.setText(downloadedMb + " MB de " + totalMb + " MB");
        });
    }

    /**
     * Junta part00 + part01 (nessa ordem) num único arquivo e extrai o
     * conteúdo (assumindo um .zip) para a pasta com.distrito.online.
     */
    private void extractParts(File part00, File part01) throws IOException {
        mainHandler.post(() -> txtCurrentFile.setText("Extraindo arquivos..."));

        File combined = new File(part00.getParentFile(), "distrito-data.combined.bin");
        try (OutputStream out = new FileOutputStream(combined)) {
            copyStream(part00, out);
            copyStream(part01, out);
        }

        File installDir = new File(getFilesDir(), INSTALL_FOLDER_NAME);
        deleteRecursive(installDir);
        if (!installDir.mkdirs()) {
            throw new IOException("Não foi possível criar a pasta de instalação");
        }

        try (ZipInputStream zip = new ZipInputStream(new java.io.FileInputStream(combined))) {
            ZipEntry entry;
            byte[] buffer = new byte[64 * 1024];
            while (!cancelled && (entry = zip.getNextEntry()) != null) {
                File outFile = new File(installDir, entry.getName());

                // proteção básica contra "zip slip" (entradas tentando
                // escrever fora da pasta de instalação)
                if (!outFile.getCanonicalPath().startsWith(installDir.getCanonicalPath() + File.separator)) {
                    throw new IOException("Entrada de arquivo inválida no pacote: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    outFile.mkdirs();
                    continue;
                }

                File parent = outFile.getParentFile();
                if (parent != null && !parent.exists()) parent.mkdirs();

                try (OutputStream fos = new FileOutputStream(outFile)) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        fos.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
            }
        }

        //noinspection ResultOfMethodCallIgnored
        combined.delete();
    }

    private void copyStream(File src, OutputStream out) throws IOException {
        try (InputStream in = new java.io.FileInputStream(src)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
    }

    /**
     * Apaga partes baixadas e qualquer coisa já extraída, e reseta a flag
     * de estado — usado tanto quando detectamos uma instalação anterior
     * interrompida quanto quando a instalação atual falha no meio.
     */
    private void wipeEverything() {
        deleteRecursive(new File(getFilesDir(), PARTS_FOLDER_NAME));
        deleteRecursive(new File(getFilesDir(), INSTALL_FOLDER_NAME));
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_STATE).apply();
    }

    private void deleteRecursive(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) deleteRecursive(child);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private void goToGame() {
        // TODO: seguir o fluxo normal do launcher depois de instalado
        // (abrir a SAMP/main activity, ou voltar pro ConnectActivity que
        // agora vai detectar que a instalação está completa).
        navigateWithFade(new android.content.Intent(this, ConnectActivity.class));
    }
}
