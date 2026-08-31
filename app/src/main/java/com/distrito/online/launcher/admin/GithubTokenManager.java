package com.distrito.online.launcher.admin;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

/**
 * Guarda o token do GitHub (usado só pra buscar releases automaticamente
 * no painel admin) EXCLUSIVAMENTE no SharedPreferences local do aparelho.
 * Esse valor nunca é escrito no Firestore, nunca sobe pra nenhum
 * servidor nosso e nunca é lido por nenhuma outra Activity que não seja
 * o painel admin — cada instalação do app (inclusive a do próprio
 * admin em outro celular) tem que digitar o token de novo.
 */
public class GithubTokenManager {

    private static final String PREFS_NAME = "distrito_admin_local_only";
    private static final String KEY_TOKEN = "github_token";

    private final SharedPreferences prefs;

    public GithubTokenManager(Context context) {
        prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public void saveToken(String token) {
        prefs.edit().putString(KEY_TOKEN, token).apply();
    }

    public void clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply();
    }

    @Nullable
    public String getToken() {
        return prefs.getString(KEY_TOKEN, null);
    }

    public boolean hasToken() {
        String t = getToken();
        return t != null && !t.trim().isEmpty();
    }
}
