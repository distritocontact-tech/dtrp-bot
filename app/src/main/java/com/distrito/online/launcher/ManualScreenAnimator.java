package com.distrito.online.launcher;

import android.view.Choreographer;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * Anima a entrada da tela "na mão", frame a frame, via Choreographer.
 *
 * Por quê isso existe: o overridePendingTransition() padrão do Android
 * depende das opções de desenvolvedor "Escala de animação de transição" /
 * "Escala de animação de janela". Quando o usuário desativa essas opções
 * (comum em quem quer o aparelho mais "rápido"), o sistema simplesmente
 * PULA a animação da troca de Activity — não importa o quão certo o
 * código esteja. Isso não é um bug do app, é um comportamento do SO.
 *
 * Esse animador não usa Animation nem Animator (ambos sujeitos às escalas
 * do sistema): ele calcula o progresso manualmente a partir do tempo
 * decorrido e aplica alpha/translationX direto na view, então roda mesmo
 * com as escalas do desenvolvedor zeradas.
 */
final class ManualScreenAnimator {

    private ManualScreenAnimator() {}

    interface OnDone {
        void run();
    }

    /** Desliza a view a partir da direita (translationX) + fade in. */
    static void slideInFromRight(View target, long durationMs) {
        run(target, durationMs, new DecelerateInterpolator(1.5f), progress -> {
            float dx = target.getWidth() > 0 ? target.getWidth() : 1080f;
            target.setTranslationX(dx * (1f - progress));
            target.setAlpha(progress);
        }, null);
    }

    /** Fade + leve "zoom in" (usado saindo da tela de loading pro login). */
    static void lightFadeIn(View target, long durationMs) {
        run(target, durationMs, new DecelerateInterpolator(1.5f), progress -> {
            target.setAlpha(progress);
            float scale = 0.96f + 0.04f * progress;
            target.setScaleX(scale);
            target.setScaleY(scale);
        }, null);
    }

    private interface Step {
        void apply(float progress);
    }

    private static void run(View target, long durationMs, Interpolator interpolator, Step step, OnDone onDone) {
        target.setVisibility(View.VISIBLE);
        final long startTime = System.nanoTime();
        final long durationNanos = durationMs * 1_000_000L;

        Choreographer.getInstance().postFrameCallback(new Choreographer.FrameCallback() {
            @Override
            public void doFrame(long frameTimeNanos) {
                long elapsed = System.nanoTime() - startTime;
                float rawProgress = Math.min(1f, elapsed / (float) durationNanos);
                float progress = interpolator.getInterpolation(rawProgress);
                step.apply(progress);

                if (rawProgress < 1f) {
                    Choreographer.getInstance().postFrameCallback(this);
                } else {
                    // garante estado final exato
                    step.apply(1f);
                    target.setTranslationX(0f);
                    target.setScaleX(1f);
                    target.setScaleY(1f);
                    target.setAlpha(1f);
                    if (onDone != null) onDone.run();
                }
            }
        });
    }
}
