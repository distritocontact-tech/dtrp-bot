package com.distrito.online.launcher;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import com.distrito.online.R;

/**
 * Base para as telas do launcher. Cuida de duas coisas:
 *  - Deixar a tela realmente em tela cheia (edge-to-edge), cobrindo
 *    barra de status E barra de navegação, para não sobrar nenhuma
 *    faixa branca/preta do sistema nas bordas.
 *  - Padronizar a transição animada entre telas (slide), trocando de
 *    Activity para Activity sem duplicar animação de fade.
 */
public abstract class BaseLauncherActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyImmersiveFullScreen();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveFullScreen();
        }
    }

    @Override
    public void onBackPressed() {
        // Aqui a transição PRECISA ser registrada antes do finish() que
        // roda dentro de super.onBackPressed() — mesmo motivo do
        // navigateWithFade acima. Mesmo crossfade das outras telas.
        overridePendingTransition(R.anim.light_fade_in, R.anim.light_fade_out);
        super.onBackPressed();
    }

    /**
     * Faz o conteúdo ocupar a tela inteira (edge-to-edge) e esconde tanto
     * a barra de status quanto a barra de navegação, em modo imersivo
     * "sticky" (o usuário consegue puxar as barras de volta com um swipe,
     * mas elas somem de novo sozinhas). É isso que elimina a faixa
     * branca/cinza que sobrava do lado do app.
     */
    private void applyImmersiveFullScreen() {
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);

        View decorView = getWindow().getDecorView();
        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(getWindow(), decorView);
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
        controller.hide(WindowInsetsCompat.Type.systemBars());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            getWindow().getAttributes().layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        }
    }

    /**
     * Abre a próxima Activity com uma transição de slide (a nova tela
     * entra deslizando da direita, a atual desliza levemente pra
     * esquerda) e fecha a atual. Substitui o antigo fade duplo — que
     * rodava o fade da Activity ao mesmo tempo que um fade manual no
     * layout raiz e causava a sobreposição/"fantasma" das telas.
     */
    protected void navigateWithFade(Intent intent) {
        intent.putExtra(EXTRA_ENTRY_ANIM, ENTRY_ANIM_LIGHT_FADE);
        startActivity(intent);
        // overridePendingTransition fica como fallback: em aparelhos com as
        // escalas de animação do sistema ligadas, ele ainda roda. Mas quem
        // realmente garante a animação, independente dessas opções do
        // desenvolvedor, é o lightFadeIn() chamado no onCreate() da tela de
        // destino (ver playEntryAnimationIfPending()) — mesmo crossfade
        // usado em todas as telas agora, pra manter consistência.
        overridePendingTransition(R.anim.light_fade_in, R.anim.light_fade_out);
        finish();
    }

    /**
     * Igual ao navigateWithFade, mas com uma transição mais leve/sutil
     * (fade + leve zoom, sem slide) — usada especificamente ao sair da
     * tela de loading para a tela de login.
     */
    protected void navigateWithLightFade(Intent intent) {
        intent.putExtra(EXTRA_ENTRY_ANIM, ENTRY_ANIM_LIGHT_FADE);
        startActivity(intent);
        overridePendingTransition(R.anim.light_fade_in, R.anim.light_fade_out);
        finish();
    }

    private static final String EXTRA_ENTRY_ANIM = "entry_anim";
    private static final int ENTRY_ANIM_SLIDE = 0;
    private static final int ENTRY_ANIM_LIGHT_FADE = 1;

    /**
     * Roda a animação de entrada "na mão" (independente das opções de
     * desenvolvedor de escala de animação). Chame isso no onCreate() de
     * cada Activity, depois de setContentView(), passando a view raiz do
     * layout.
     */
    protected void playEntryAnimationIfPending(View rootView) {
        int animType = getIntent().getIntExtra(EXTRA_ENTRY_ANIM, ENTRY_ANIM_SLIDE);
        if (animType == ENTRY_ANIM_LIGHT_FADE) {
            // ~400ms, igual ao crossfade do vídeo de referência.
            ManualScreenAnimator.lightFadeIn(rootView, 400);
        } else {
            ManualScreenAnimator.slideInFromRight(rootView, 260);
        }
    }

    /**
     * Dá um "tap feedback" (encolhe levemente e volta ao tamanho normal
     * com um pequeno overshoot) num botão/pill antes de rodar {@code onFinished}.
     * Usado nos botões que abrem uma nova tela (ex: "Baixar"), pra sempre
     * haver alguma animação visível entre o toque e a troca de Activity —
     * mesmo em telas onde a transição de entrada é rápida.
     */
    protected void playTapFeedback(View target, Runnable onFinished) {
        target.animate().cancel();
        target.animate()
                .scaleX(0.92f)
                .scaleY(0.92f)
                .setDuration(90)
                .withEndAction(() -> target.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160)
                        .setInterpolator(new OvershootInterpolator(3f))
                        .withEndAction(onFinished)
                        .start())
                .start();
    }
}
