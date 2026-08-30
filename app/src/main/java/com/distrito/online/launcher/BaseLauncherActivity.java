package com.distrito.online.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.distrito.online.R;

/**
 * Base para as telas do launcher. Cuida de duas coisas:
 *  - Fazer a tela inteira (o layout raiz) surgir com um fade-in leve
 *    assim que a Activity abre, em vez de simplesmente "estalar" na tela.
 *  - Padronizar a transição animada (fade) ao trocar de tela, tanto ao
 *    entrar na próxima quanto ao sair da atual.
 */
public abstract class BaseLauncherActivity extends AppCompatActivity {

    private static final int ROOT_FADE_IN_DURATION_MS = 500;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    /**
     * Chame depois de setContentView(), passando o id do layout raiz
     * (normalmente main_layout) para animar a entrada de toda a tela.
     */
    protected void fadeInRoot(int rootViewId) {
        View root = findViewById(rootViewId);
        if (root == null) return;

        root.setAlpha(0f);
        AlphaAnimation anim = new AlphaAnimation(0f, 1f);
        anim.setDuration(ROOT_FADE_IN_DURATION_MS);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.setFillAfter(true);
        root.startAnimation(anim);
        root.setAlpha(1f);
    }

    /**
     * Abre a próxima Activity com uma transição de fade suave e fecha
     * a atual, também com fade. Substitui o startActivity()+finish() cru.
     */
    protected void navigateWithFade(Intent intent) {
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
    }
}
