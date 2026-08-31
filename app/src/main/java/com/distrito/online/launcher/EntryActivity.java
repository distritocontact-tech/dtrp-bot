package com.distrito.online.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;

import com.distrito.online.R;

/**
 * Primeira tela que o player vê ao abrir o app: a tela de carregamento
 * (loading_screen.xml). A barra de progresso anda de 0 a 100 ao longo de
 * LOADING_DURATION_MS, com uma pausa proposital simulando um
 * carregamento real (trava em STALL_START_MS e volta a andar em
 * STALL_END_MS), e assim que termina, o app desliza, com uma transição,
 * para:
 *  - LoginActivity, se for a primeira vez do player ou ele não tiver
 *    conta Google vinculada;
 *  - ConnectActivity diretamente, se a conta já estiver vinculada.
 *
 * Importante: o avanço da barra é feito manualmente com Handler +
 * SystemClock.elapsedRealtime() (tempo real), em vez de ObjectAnimator.
 * Isso é proposital — a "Escala de animação do Animator" que existe nas
 * Opções do desenvolvedor do Android, quando o player/testador desativa
 * animações do sistema, faz QUALQUER ObjectAnimator/ValueAnimator
 * terminar instantaneamente, ignorando a duração configurada. Como o
 * tempo de carregamento é uma regra do app (não uma animação cosmética),
 * ele não pode depender dessa configuração do aparelho.
 */
public class EntryActivity extends BaseLauncherActivity {

    // TODO: substituir por progresso real de carregamento de arquivos/assets
    // (verificação de integridade, download de atualizações, etc.). Por
    // enquanto a barra apenas avança até 100% dentro desse tempo máximo.
    private static final long LOADING_DURATION_MS = 10_000;
    private static final long STALL_START_MS = 5_000;
    private static final long STALL_END_MS = 7_000;
    private static final long STALL_DURATION_MS = STALL_END_MS - STALL_START_MS;
    private static final long TICK_MS = 16; // ~60fps, pra barra andar suave sem travar

    private LinkedAccountManager accountManager;
    private ProgressBar progressBar;
    private Handler handler;
    private long startElapsedMs;
    private boolean loadingFinished = false;

    private final Runnable progressTick = new Runnable() {
        @Override
        public void run() {
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

            int progress = (int) Math.min(100, (progressElapsed * 100) / LOADING_DURATION_MS);
            progressBar.setProgress(progress);

            if (progressElapsed >= LOADING_DURATION_MS) {
                loadingFinished = true;
                goToNextScreen();
            } else {
                handler.postDelayed(this, TICK_MS);
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.loading_screen);

        accountManager = new LinkedAccountManager(this);
        progressBar = findViewById(R.id.progress_bar);
        progressBar.setProgress(0);

        handler = new Handler(Looper.getMainLooper());
        startElapsedMs = SystemClock.elapsedRealtime();
        handler.post(progressTick);
    }

    @Override
    protected void onDestroy() {
        loadingFinished = true;
        if (handler != null) {
            handler.removeCallbacks(progressTick);
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
