package com.distrito.online.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.distrito.online.R;

/**
 * Primeira tela que o player vê ao abrir o app: a tela de carregamento
 * (loading_screen.xml). Espera o carregamento simulado e então desliza,
 * com uma transição de slide, para:
 *  - LoginActivity, se for a primeira vez do player ou ele não tiver
 *    conta Google vinculada;
 *  - ConnectActivity diretamente, se a conta já estiver vinculada.
 */
public class EntryActivity extends BaseLauncherActivity {

    // TODO: substituir por tempo real de carregamento de arquivos/assets
    // (verificação de integridade, download de atualizações, etc.).
    private static final long LOADING_DURATION_MS = 2500;

    private LinkedAccountManager accountManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        accountManager = new LinkedAccountManager(this);

        new Handler(Looper.getMainLooper()).postDelayed(this::goToNextScreen, LOADING_DURATION_MS);
    }

    private void goToNextScreen() {
        Intent intent = accountManager.isLinked()
                ? new Intent(this, ConnectActivity.class)
                : new Intent(this, LoginActivity.class);
        navigateWithFade(intent);
    }
}
