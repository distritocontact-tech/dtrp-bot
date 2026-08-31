package com.distrito.online.launcher;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.os.Bundle;
import android.view.animation.LinearInterpolator;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.distrito.online.R;

/**
 * Primeira tela que o player vê ao abrir o app: a tela de carregamento
 * (loading_screen.xml). A barra de progresso anda de 0 a 100 em até
 * LOADING_DURATION_MS e, assim que termina, o app desliza, com uma
 * transição de slide, para:
 *  - LoginActivity, se for a primeira vez do player ou ele não tiver
 *    conta Google vinculada;
 *  - ConnectActivity diretamente, se a conta já estiver vinculada.
 */
public class EntryActivity extends BaseLauncherActivity {

    // TODO: substituir por progresso real de carregamento de arquivos/assets
    // (verificação de integridade, download de atualizações, etc.). Por
    // enquanto a barra apenas anima até 100% dentro desse tempo máximo.
    private static final long LOADING_DURATION_MS = 10_000;

    private LinkedAccountManager accountManager;
    private ObjectAnimator progressAnimator;
    private boolean loadingFinished = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        accountManager = new LinkedAccountManager(this);

        ProgressBar progressBar = findViewById(R.id.progress_bar);
        progressBar.setProgress(0);

        progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", 0, 100);
        progressAnimator.setDuration(LOADING_DURATION_MS);
        progressAnimator.setInterpolator(new LinearInterpolator());
        progressAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationStart(@NonNull Animator animation) {}

            @Override
            public void onAnimationEnd(@NonNull Animator animation) {
                // cancel() também dispara onAnimationEnd (comportamento padrão do
                // Animator do Android) — só queremos navegar quando a barra
                // realmente terminou os 10s, não quando foi cancelada (ex.: a
                // Activity sendo destruída antes da hora).
                if (!loadingFinished) {
                    loadingFinished = true;
                    goToNextScreen();
                }
            }

            @Override
            public void onAnimationCancel(@NonNull Animator animation) {
                loadingFinished = true;
            }

            @Override
            public void onAnimationRepeat(@NonNull Animator animation) {}
        });
        progressAnimator.start();
    }

    @Override
    protected void onDestroy() {
        if (progressAnimator != null) {
            progressAnimator.cancel();
        }
        super.onDestroy();
    }

    private void goToNextScreen() {
        Intent intent = accountManager.isLinked()
                ? new Intent(this, ConnectActivity.class)
                : new Intent(this, LoginActivity.class);
        if (accountManager.isLinked()) {
            navigateWithFade(intent);
        } else {
            navigateWithLightFade(intent);
        }
    }
}
