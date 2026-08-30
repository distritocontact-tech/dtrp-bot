package com.distrito.online.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignInAccount;

/**
 * Sistema de vínculo de conta do launcher. Guarda localmente os dados
 * da conta Google conectada (email, nome, foto) para que o player não
 * precise logar de novo toda vez que abrir o app, e oferece o método
 * para desvincular a conta quando o player quiser trocar.
 */
public class LinkedAccountManager {

    private static final String PREFS_NAME = "distrito_prefs";
    private static final String KEY_LINKED = "google_linked";
    private static final String KEY_EMAIL = "google_email";
    private static final String KEY_NAME = "google_name";
    private static final String KEY_PHOTO_URL = "google_photo_url";

    private final SharedPreferences prefs;

    public LinkedAccountManager(Context context) {
        prefs = context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Salva a conta Google recém-conectada como a conta vinculada do player. */
    public void linkAccount(GoogleSignInAccount account) {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(KEY_LINKED, true);
        editor.putString(KEY_EMAIL, account.getEmail());
        editor.putString(KEY_NAME, account.getDisplayName());
        editor.putString(KEY_PHOTO_URL, account.getPhotoUrl() != null ? account.getPhotoUrl().toString() : null);
        editor.apply();
    }

    /** Remove a conta vinculada (usado quando o player clica em "Trocar de conta"). */
    public void unlinkAccount() {
        prefs.edit().clear().apply();
    }

    public boolean isLinked() {
        return prefs.getBoolean(KEY_LINKED, false);
    }

    @Nullable
    public String getEmail() {
        return prefs.getString(KEY_EMAIL, null);
    }

    @Nullable
    public String getDisplayName() {
        return prefs.getString(KEY_NAME, null);
    }

    @Nullable
    public String getPhotoUrl() {
        return prefs.getString(KEY_PHOTO_URL, null);
    }
}
