package com.distrito.online.launcher.admin;

/** Representa um doc de users/{uid} no Firestore, pra exibir na lista do painel admin. */
public class PlayerAccount {

    public String uid;
    public String email;
    public String name;
    public String ip;
    public long createdAt;
    public long lastHeartbeat;
    public boolean banned;
    public String banReason;

    public boolean isOnline() {
        return lastHeartbeat > 0
                && (System.currentTimeMillis() - lastHeartbeat) < AdminConfig.ONLINE_THRESHOLD_MS;
    }
}
