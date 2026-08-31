package com.distrito.online.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Guarda um "retrato" (manifesto) da pasta com.distrito.online logo após a
 * instalação: caminho relativo -> tamanho + SHA-256 de cada arquivo. Esse
 * manifesto fica FORA da pasta do jogo (pra quem mexer nos arquivos do jogo
 * não conseguir editar o manifesto junto) e é assinado com HMAC-SHA256 pra
 * dificultar que alguém forje um manifesto "combinando" com arquivos
 * adulterados.
 *
 * Depois disso, verify() pode ser chamado a qualquer momento (ex: toda vez
 * que o launcher abre) pra descobrir quais arquivos foram apagados ou
 * modificados desde a instalação. Só esses arquivos entram na lista de
 * "precisa reinstalar" — o resto da pasta não é tocado.
 *
 * Importante ser honesto sobre o limite disso: num aparelho com root, dá
 * pra editar QUALQUER coisa, inclusive burlar esse verificador. O que dá
 * pra garantir de verdade é: (1) nenhum OUTRO app consegue ler/escrever
 * nesses arquivos (isso o Android já garante via sandbox, já que a pasta
 * fica em armazenamento interno privado do app), e (2) qualquer alteração
 * feita pelo próprio player nos arquivos é detectada e revertida na
 * próxima verificação.
 */
public class FileIntegrityManager {

    private static final String TAG = "FileIntegrity";

    private static final String INTEGRITY_DIR_NAME = ".integrity";
    private static final String MANIFEST_FILE_NAME = "manifest.json";

    private static final String PREFS = "integrity_state";
    private static final String KEY_PENDING_REPAIR = "pending_repair_json";

    // Chave usada apenas para assinar o manifesto localmente (detectar
    // adulteração do próprio manifesto), não é um segredo de rede.
    private static final String HMAC_SEED = "distrito-online-integrity-v1";

    private final Context context;
    private final File installDir;
    private final File manifestFile;

    public FileIntegrityManager(Context context, File installDir) {
        this.context = context.getApplicationContext();
        this.installDir = installDir;
        File integrityDir = new File(context.getFilesDir(), INTEGRITY_DIR_NAME);
        if (!integrityDir.exists()) integrityDir.mkdirs();
        this.manifestFile = new File(integrityDir, MANIFEST_FILE_NAME);
    }

    /** Resultado de uma checagem de integridade. */
    public static class VerifyResult {
        public final List<String> corruptedOrMissing;
        public final boolean manifestMissing;

        VerifyResult(List<String> corruptedOrMissing, boolean manifestMissing) {
            this.corruptedOrMissing = corruptedOrMissing;
            this.manifestMissing = manifestMissing;
        }

        public boolean isClean() {
            return corruptedOrMissing.isEmpty() && !manifestMissing;
        }
    }

    /**
     * Varre installDir inteiro, calcula hash de cada arquivo e grava um
     * manifesto novo (assinado). Chamar isso logo depois de uma instalação
     * ou reparo bem-sucedido — nunca a partir de dados que vieram do
     * player, sempre a partir do que acabamos de extrair/gravar nós mesmos.
     */
    public void rebuildManifestForFiles(List<String> relativePaths) throws IOException {
        JSONObject entries = loadRawManifestObject();
        for (String rel : relativePaths) {
            File f = new File(installDir, rel);
            if (!f.exists()) continue;
            try {
                entries.put(rel, sha256Hex(f) + ":" + f.length());
            } catch (Exception e) {
                throw new IOException("Falha ao gerar hash de " + rel, e);
            }
        }
        writeSignedManifest(entries);
    }

    /** Constrói o manifesto do zero varrendo toda a pasta instalada. */
    public void rebuildFullManifest() throws IOException {
        List<String> all = new ArrayList<>();
        collectRelativePaths(installDir, installDir, all);
        JSONObject entries = new JSONObject();
        for (String rel : all) {
            File f = new File(installDir, rel);
            try {
                entries.put(rel, sha256Hex(f) + ":" + f.length());
            } catch (Exception e) {
                throw new IOException("Falha ao gerar hash de " + rel, e);
            }
        }
        writeSignedManifest(entries);
    }

    /**
     * Compara o estado atual da pasta com o manifesto assinado. Retorna os
     * caminhos relativos que estão faltando, mudaram de conteúdo, ou cujo
     * manifesto foi adulterado (nesse último caso, TODOS os arquivos
     * conhecidos entram na lista, já que não dá mais pra confiar em nenhum
     * hash individual).
     */
    public VerifyResult verify() {
        JSONObject entries;
        try {
            entries = readVerifiedManifestObject();
        } catch (SecurityException tampered) {
            Log.w(TAG, "Manifesto adulterado ou corrompido, forçando reparo total");
            List<String> everything = new ArrayList<>();
            collectRelativePaths(installDir, installDir, everything);
            return new VerifyResult(everything, true);
        } catch (IOException noManifest) {
            return new VerifyResult(new ArrayList<>(), true);
        }

        List<String> bad = new ArrayList<>();
        Iterator<String> keys = entries.keys();
        while (keys.hasNext()) {
            String rel = keys.next();
            File f = new File(installDir, rel);
            String expected = entries.optString(rel, null);
            if (expected == null) continue;

            if (!f.exists()) {
                bad.add(rel);
                continue;
            }
            try {
                String[] parts = expected.split(":");
                String expectedHash = parts[0];
                long expectedSize = parts.length > 1 ? Long.parseLong(parts[1]) : -1;
                if (expectedSize >= 0 && f.length() != expectedSize) {
                    bad.add(rel);
                    continue;
                }
                if (!sha256Hex(f).equals(expectedHash)) {
                    bad.add(rel);
                }
            } catch (Exception e) {
                bad.add(rel);
            }
        }
        return new VerifyResult(bad, false);
    }

    /** Marca uma lista de arquivos como "precisa baixar de novo". */
    public void markPendingRepair(List<String> relativePaths) {
        JSONObject obj = new JSONObject();
        try {
            for (String rel : relativePaths) obj.put(rel, true);
        } catch (JSONException ignored) {}
        prefs().edit().putString(KEY_PENDING_REPAIR, obj.toString()).apply();
    }

    public List<String> getPendingRepair() {
        List<String> out = new ArrayList<>();
        String raw = prefs().getString(KEY_PENDING_REPAIR, null);
        if (raw == null) return out;
        try {
            JSONObject obj = new JSONObject(raw);
            Iterator<String> keys = obj.keys();
            while (keys.hasNext()) out.add(keys.next());
        } catch (JSONException ignored) {}
        return out;
    }

    public boolean hasPendingRepair() {
        return !getPendingRepair().isEmpty();
    }

    public void clearPendingRepair() {
        prefs().edit().remove(KEY_PENDING_REPAIR).apply();
    }

    /**
     * Deixa os arquivos como somente-leitura para o próprio app. Não
     * impede alguém com acesso root/depurador, mas evita que qualquer
     * gerenciador de arquivos comum (ou o próprio processo do jogo, por
     * engano) sobrescreva algo sem querer.
     */
    public void lockDownPermissions(File dir) {
        if (dir == null || !dir.exists()) return;
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                lockDownPermissions(child);
            } else {
                //noinspection ResultOfMethodCallIgnored
                child.setWritable(false, false);
                //noinspection ResultOfMethodCallIgnored
                child.setReadable(true, false);
            }
        }
    }

    // ---- internals ----------------------------------------------------

    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private void collectRelativePaths(File root, File dir, List<String> out) {
        File[] children = dir.listFiles();
        if (children == null) return;
        for (File child : children) {
            if (child.isDirectory()) {
                collectRelativePaths(root, child, out);
            } else {
                String rel = root.toURI().relativize(child.toURI()).getPath();
                out.add(rel);
            }
        }
    }

    private JSONObject loadRawManifestObject() {
        try {
            return readVerifiedManifestObject();
        } catch (Exception e) {
            return new JSONObject();
        }
    }

    /** Lê o manifesto e valida a assinatura HMAC. Lança SecurityException se adulterado. */
    private JSONObject readVerifiedManifestObject() throws IOException {
        if (!manifestFile.exists()) throw new FileNotFoundException("no manifest yet");

        String raw = readFileAsString(manifestFile);
        try {
            JSONObject wrapper = new JSONObject(raw);
            String entriesJson = wrapper.getString("entries");
            String signature = wrapper.getString("hmac");

            String expectedSig = hmac(entriesJson);
            if (!constantTimeEquals(expectedSig, signature)) {
                throw new SecurityException("manifest signature mismatch");
            }
            return new JSONObject(entriesJson);
        } catch (JSONException e) {
            throw new SecurityException("manifest parse error");
        }
    }

    private void writeSignedManifest(JSONObject entries) throws IOException {
        String entriesJson = entries.toString();
        String sig = hmac(entriesJson);
        JSONObject wrapper = new JSONObject();
        try {
            wrapper.put("entries", entriesJson);
            wrapper.put("hmac", sig);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        try (java.io.FileWriter fw = new java.io.FileWriter(manifestFile)) {
            fw.write(wrapper.toString());
        }
    }

    private String hmac(String data) throws IOException {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            String key = HMAC_SEED + ":" + context.getPackageName();
            mac.init(new SecretKeySpec(key.getBytes("UTF-8"), "HmacSHA256"));
            byte[] result = mac.doFinal(data.getBytes("UTF-8"));
            return Base64.encodeToString(result, Base64.NO_WRAP);
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) return false;
        int diff = 0;
        for (int i = 0; i < a.length(); i++) diff |= a.charAt(i) ^ b.charAt(i);
        return diff == 0;
    }

    private static String sha256Hex(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : digest.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException(e);
        }
    }

    private static String readFileAsString(File f) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (java.io.FileReader fr = new java.io.FileReader(f);
             java.io.BufferedReader br = new java.io.BufferedReader(fr)) {
            char[] buf = new char[8192];
            int read;
            while ((read = br.read(buf)) != -1) sb.append(buf, 0, read);
        }
        return sb.toString();
    }
}
