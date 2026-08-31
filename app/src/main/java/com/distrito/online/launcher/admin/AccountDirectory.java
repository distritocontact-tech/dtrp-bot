package com.distrito.online.launcher.admin;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ponto único de acesso ao Firestore pra tudo relacionado a contas,
 * bans, "entrada fechada" e link de atualização publicado pelo admin.
 *
 * A restrição de "só o admin escreve nas coleções administrativas" é
 * garantida de verdade pelas Firestore Security Rules (ver
 * firestore.rules na raiz do projeto) — o que esse código faz do lado
 * do app é só usar a API; se alguém tentar escrever sem ser o admin, o
 * Firestore rejeita a operação mesmo que a pessoa mexa no APK.
 */
public class AccountDirectory {

    private static final String TAG = "DTRP-AccountDirectory";

    private final FirebaseFirestore db;
    private String currentIp;
    private final Handler heartbeatHandler = new Handler(Looper.getMainLooper());
    private Runnable heartbeatRunnable;
    private String heartbeatUid;

    public AccountDirectory() {
        db = FirebaseFirestore.getInstance();
    }

    public interface AccessCallback {
        /** allowed=false + reason preenchido quando a conta ou o IP estão banidos. */
        void onResult(boolean allowed, @Nullable String reason);
    }

    public interface EntryStateCallback {
        void onResult(boolean closed, @Nullable String message);
    }

    public interface UsersCallback {
        void onResult(List<PlayerAccount> accounts);
    }

    public interface UpdateInfoCallback {
        void onResult(@Nullable String apkUrl, int versionCode, @Nullable String versionName);
    }

    public interface SimpleCallback {
        void onDone(boolean success, @Nullable Exception error);
    }

    // ---------------------------------------------------------------
    // Cadastro / sincronização da conta ao logar
    // ---------------------------------------------------------------

    /**
     * Cria (na primeira vez) ou atualiza o doc users/{uid} com os dados
     * atuais da conta + IP do aparelho, e então checa se a conta ou o IP
     * estão banidos antes de deixar o player seguir pro jogo.
     */
    public void syncAccountAndCheckAccess(FirebaseUser user, AccessCallback callback) {
        if (user == null) {
            callback.onResult(true, null);
            return;
        }
        IpAddressHelper.fetchPublicIp(ip -> {
            currentIp = ip;

            Map<String, Object> data = new HashMap<>();
            data.put("email", user.getEmail());
            data.put("name", user.getDisplayName());
            data.put("ip", ip);
            data.put("lastLoginAt", FieldValue.serverTimestamp());
            data.put("lastHeartbeat", System.currentTimeMillis());

            upsertUserDoc(user.getUid(), data);

            checkAccess(user.getUid(), ip, callback);
        });
    }

    private void upsertUserDoc(String uid, Map<String, Object> data) {
        Map<String, Object> withCreatedAtIfNew = new HashMap<>(data);
        db.collection(AdminConfig.COLLECTION_USERS).document(uid).get()
                .addOnCompleteListener(task -> {
                    if (task.isSuccessful() && task.getResult() != null && !task.getResult().exists()) {
                        withCreatedAtIfNew.put("createdAt", FieldValue.serverTimestamp());
                    }
                    db.collection(AdminConfig.COLLECTION_USERS).document(uid)
                            .set(withCreatedAtIfNew, com.google.firebase.firestore.SetOptions.merge());
                });
    }

    /** Checa se o uid está banido ou se o IP atual está na lista de banidos. */
    public void checkAccess(String uid, @Nullable String ip, AccessCallback callback) {
        db.collection(AdminConfig.COLLECTION_USERS).document(uid).get()
                .addOnCompleteListener(userTask -> {
                    boolean userBanned = userTask.isSuccessful() && userTask.getResult() != null
                            && Boolean.TRUE.equals(userTask.getResult().getBoolean("banned"));
                    String userBanReason = userTask.isSuccessful() && userTask.getResult() != null
                            ? userTask.getResult().getString("banReason") : null;

                    if (userBanned) {
                        callback.onResult(false, userBanReason != null ? userBanReason : "Conta banida.");
                        return;
                    }

                    if (ip == null) {
                        callback.onResult(true, null);
                        return;
                    }

                    String ipDocId = IpAddressHelper.sanitizeForDocId(ip);
                    db.collection(AdminConfig.COLLECTION_IP_BANS).document(ipDocId).get()
                            .addOnCompleteListener(ipTask -> {
                                boolean ipBanned = ipTask.isSuccessful() && ipTask.getResult() != null
                                        && ipTask.getResult().exists();
                                String ipBanReason = ipBanned ? ipTask.getResult().getString("reason") : null;
                                callback.onResult(!ipBanned, ipBanReason != null ? ipBanReason : "IP banido.");
                            });
                });
    }

    // ---------------------------------------------------------------
    // Heartbeat (status online / offline)
    // ---------------------------------------------------------------

    /** Chame no onResume() da tela principal (logada). Escreve lastHeartbeat a cada 30s. */
    public void startHeartbeat(String uid) {
        stopHeartbeat();
        heartbeatUid = uid;
        heartbeatRunnable = new Runnable() {
            @Override
            public void run() {
                if (heartbeatUid == null) return;
                db.collection(AdminConfig.COLLECTION_USERS).document(heartbeatUid)
                        .set(java.util.Collections.singletonMap("lastHeartbeat", System.currentTimeMillis()),
                                com.google.firebase.firestore.SetOptions.merge());
                heartbeatHandler.postDelayed(this, AdminConfig.HEARTBEAT_INTERVAL_MS);
            }
        };
        heartbeatHandler.post(heartbeatRunnable);
    }

    /** Chame no onPause()/onDestroy() da tela principal. */
    public void stopHeartbeat() {
        if (heartbeatRunnable != null) {
            heartbeatHandler.removeCallbacks(heartbeatRunnable);
            heartbeatRunnable = null;
        }
        heartbeatUid = null;
    }

    // ---------------------------------------------------------------
    // Painel admin: lista de contas
    // ---------------------------------------------------------------

    public void fetchAllUsers(UsersCallback callback) {
        db.collection(AdminConfig.COLLECTION_USERS)
                .orderBy("lastHeartbeat", Query.Direction.DESCENDING)
                .get()
                .addOnCompleteListener(task -> {
                    List<PlayerAccount> result = new ArrayList<>();
                    if (task.isSuccessful() && task.getResult() != null) {
                        for (DocumentSnapshot doc : task.getResult().getDocuments()) {
                            PlayerAccount acc = new PlayerAccount();
                            acc.uid = doc.getId();
                            acc.email = doc.getString("email");
                            acc.name = doc.getString("name");
                            acc.ip = doc.getString("ip");
                            acc.banned = Boolean.TRUE.equals(doc.getBoolean("banned"));
                            acc.banReason = doc.getString("banReason");
                            Long hb = doc.getLong("lastHeartbeat");
                            acc.lastHeartbeat = hb != null ? hb : 0L;
                            com.google.firebase.Timestamp created = doc.getTimestamp("createdAt");
                            acc.createdAt = created != null ? created.toDate().getTime() : 0L;
                            result.add(acc);
                        }
                    } else if (task.getException() != null) {
                        Log.e(TAG, "Falha ao listar contas", task.getException());
                    }
                    callback.onResult(result);
                });
    }

    public void banUser(String uid, String reason, SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("banned", true);
        data.put("banReason", reason);
        data.put("bannedAt", FieldValue.serverTimestamp());
        db.collection(AdminConfig.COLLECTION_USERS).document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(t -> callback.onDone(t.isSuccessful(), t.getException()));
    }

    public void unbanUser(String uid, SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("banned", false);
        data.put("banReason", FieldValue.delete());
        db.collection(AdminConfig.COLLECTION_USERS).document(uid)
                .set(data, com.google.firebase.firestore.SetOptions.merge())
                .addOnCompleteListener(t -> callback.onDone(t.isSuccessful(), t.getException()));
    }

    public void banIp(String ip, String reason, SimpleCallback callback) {
        String docId = IpAddressHelper.sanitizeForDocId(ip);
        if (docId == null || docId.isEmpty()) {
            callback.onDone(false, new IllegalArgumentException("IP inválido"));
            return;
        }
        Map<String, Object> data = new HashMap<>();
        data.put("ip", ip);
        data.put("reason", reason);
        data.put("bannedAt", FieldValue.serverTimestamp());
        db.collection(AdminConfig.COLLECTION_IP_BANS).document(docId)
                .set(data)
                .addOnCompleteListener(t -> callback.onDone(t.isSuccessful(), t.getException()));
    }

    public void unbanIp(String ip, SimpleCallback callback) {
        String docId = IpAddressHelper.sanitizeForDocId(ip);
        if (docId == null || docId.isEmpty()) {
            callback.onDone(false, new IllegalArgumentException("IP inválido"));
            return;
        }
        db.collection(AdminConfig.COLLECTION_IP_BANS).document(docId).delete()
                .addOnCompleteListener(t -> callback.onDone(t.isSuccessful(), t.getException()));
    }

    // ---------------------------------------------------------------
    // "Fechar entrada" (só no APK)
    // ---------------------------------------------------------------

    public void setEntryClosed(boolean closed, String message, SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("closed", closed);
        data.put("message", message);
        docRef(AdminConfig.DOC_APP_CONTROL).set(data)
                .addOnCompleteListener(t -> callback.onDone(t.isSuccessful(), t.getException()));
    }

    public void getEntryState(EntryStateCallback callback) {
        docRef(AdminConfig.DOC_APP_CONTROL).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                Boolean closed = task.getResult().getBoolean("closed");
                String message = task.getResult().getString("message");
                callback.onResult(Boolean.TRUE.equals(closed), message);
            } else {
                callback.onResult(false, null);
            }
        });
    }

    // ---------------------------------------------------------------
    // Link de atualização do APK publicado pelo admin
    // ---------------------------------------------------------------

    public void publishUpdate(String apkUrl, int versionCode, String versionName, SimpleCallback callback) {
        Map<String, Object> data = new HashMap<>();
        data.put("apkUrl", apkUrl);
        data.put("versionCode", versionCode);
        data.put("versionName", versionName);
        data.put("publishedAt", FieldValue.serverTimestamp());
        docRef(AdminConfig.DOC_APP_UPDATE).set(data)
                .addOnCompleteListener(t -> callback.onDone(t.isSuccessful(), t.getException()));
    }

    public void fetchPublishedUpdate(UpdateInfoCallback callback) {
        docRef(AdminConfig.DOC_APP_UPDATE).get().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null && task.getResult().exists()) {
                String apkUrl = task.getResult().getString("apkUrl");
                Long versionCode = task.getResult().getLong("versionCode");
                String versionName = task.getResult().getString("versionName");
                callback.onResult(apkUrl, versionCode != null ? versionCode.intValue() : -1, versionName);
            } else {
                callback.onResult(null, -1, null);
            }
        });
    }

    private com.google.firebase.firestore.DocumentReference docRef(String path) {
        return db.document(path);
    }
}
