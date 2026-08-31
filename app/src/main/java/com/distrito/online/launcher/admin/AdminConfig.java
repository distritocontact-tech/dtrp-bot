package com.distrito.online.launcher.admin;

/**
 * Configuração central do sistema de admin. Só o e-mail listado aqui
 * (verificado tanto no app quanto — de verdade — nas Firestore Security
 * Rules do projeto) consegue abrir o painel e escrever nas coleções
 * administrativas. Mudar esse valor no APK sozinho NÃO dá acesso a
 * ninguém: as rules do Firestore são a barreira real, isso aqui só
 * evita mostrar o botão do painel pra quem não pode usar.
 */
public final class AdminConfig {

    private AdminConfig() {}

    /** Único e-mail com acesso ao painel admin. */
    public static final String ADMIN_EMAIL = "carlosmagno123b@gmail.com";

    // Firestore: coleção com um doc por jogador (uid do Firebase Auth).
    public static final String COLLECTION_USERS = "users";

    // Firestore: coleção com um doc por IP banido (id = ip com "." trocado por "_").
    public static final String COLLECTION_IP_BANS = "ip_bans";

    // Firestore: doc único com o estado de "entrada fechada" do APK.
    public static final String DOC_APP_CONTROL = "config/app_control";

    // Firestore: doc único com o link/versão do APK publicado pelo admin.
    public static final String DOC_APP_UPDATE = "config/app_update";

    // Considera o jogador "online" se o último heartbeat foi há menos que isso.
    public static final long ONLINE_THRESHOLD_MS = 90_000L; // 90s

    // Intervalo entre heartbeats enquanto o app está em primeiro plano.
    public static final long HEARTBEAT_INTERVAL_MS = 30_000L; // 30s

    public static boolean isAdminEmail(String email) {
        return email != null && email.equalsIgnoreCase(ADMIN_EMAIL);
    }
}
