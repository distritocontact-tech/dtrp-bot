package com.distrito.online.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.Choreographer;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;

import com.distrito.online.R;

/**
 * Primeira tela que o player vê ao abrir o app: a tela de carregamento
 * (loading_screen.xml). A barra de progresso anda de 0 a 100% ao longo
 * de LOADING_DURATION_MS, com uma pausa proposital simulando um
 * carregamento real (trava em STALL_START_MS, ~75% do progresso, e volta
 * a andar em STALL_END_MS), e assim que termina, o app desliza, com uma
 * transição, para:
 *  - LoginActivity, se for a primeira vez do player ou ele não tiver
 *    conta Google vinculada;
 *  - ConnectActivity diretamente, se a conta já estiver vinculada.
 *
 * Importante, duas coisas sobre como a barra é animada:
 *
 *  1) O avanço é calculado com SystemClock.elapsedRealtime() (tempo
 *     real) e não com ObjectAnimator. Isso é proposital — a "Escala de
 *     animação do Animator" das Opções do desenvolvedor do Android,
 *     quando desativada, faz QUALQUER ObjectAnimator/ValueAnimator
 *     terminar instantaneamente, ignorando a duração configurada. Como
 *     o tempo de carregamento é uma regra do app, não pode depender
 *     dessa configuração do aparelho.
 *
 *  2) O ProgressBar usa max=10000 (não 100) — veja loading_screen.xml.
 *     Com max=100 a barra só tem 100 posições possíveis ao longo de
 *     toda a largura da tela, então mesmo atualizando a cada frame ela
 *     "pula" em saltos grandes e visíveis. Com max=10000 cada frame
 *     avança uma fração pequena o suficiente pra parecer fluida.
 *     Os cálculos abaixo trabalham nessa escala de 0 a PROGRESS_MAX.
 */
public class EntryActivity extends BaseLauncherActivity {

    // TODO: substituir por progresso real de carregamento de arquivos/assets
    // (verificação de integridade, download de atualizações, etc.). Por
    // enquanto a barra apenas avança até 100% dentro desse tempo máximo.
    private static final long LOADING_DURATION_MS = 10_000;
    private static final int PROGRESS_MAX = 10_000; // deve bater com android:max do XML

    // A trava acontece quando o progresso chega em 75% do tempo total
    // (ou seja, em 75% da barra) e fica ali por STALL_DURATION_MS antes
    // de voltar a andar.
    private static final long STALL_START_MS = (long) (LOADING_DURATION_MS * 0.75f);
    private static final long STALL_DURATION_MS = 2_000;
    private static final long STALL_END_MS = STALL_START_MS + STALL_DURATION_MS;

    private LinkedAccountManager accountManager;
    private ProgressBar progressBar;
    private Choreographer choreographer;
    private long startElapsedMs;
    private boolean loadingFinished = false;

    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        @Override
        public void doFrame(long frameTimeNanos) {
            if (loadingFinished) return;

            long elapsed = SystemClock.elapsedRealtime() - startElapsedMs;

            long progressElapsed;
            if (elapsed < STALL_START_MS) {
                progressElapsed = elapsed;
            } else if (elapsed < STALL_END_MS) {
                // "trava" aqui — carregamento pausado simulando trabalho real
                progressElapsed = STALL_START_MS;
            } else {
                progressElapsed = elapsed - STALL_DURATION_MS;
            }

            int progress = (int) Math.min(PROGRESS_MAX, (progressElapsed * PROGRESS_MAX) / LOADING_DURATION_MS);
            progressBar.setProgress(progress);

            if (progressElapsed >= LOADING_DURATION_MS) {
                loadingFinished = true;
                goToNextScreen();
            } else {
                choreographer.postFrameCallback(this);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);
        playEntryAnimationIfPending(findViewById(android.R.id.content));

        accountManager = new LinkedAccountManager(this);
        progressBar = findViewById(R.id.progress_bar);
        progressBar.setMax(PROGRESS_MAX);
        progressBar.setProgress(0);

        choreographer = Choreographer.getInstance();
        startElapsedMs = SystemClock.elapsedRealtime();
        choreographer.postFrameCallback(frameCallback);
    }

    @Override
    protected void onDestroy() {
        loadingFinished = true;
        if (choreographer != null) {
            choreographer.removeFrameCallback(frameCallback);
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
