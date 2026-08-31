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
        super.onBackPressed();
        // Transição de volta (slide no sentido oposto ao de entrada), pra
        // cobrir também a navegação pelo botão/gesto de voltar do sistema
        // — que, sem isso, usa a transição padrão (sem animação) do Android.
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right);
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
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
    }

    /**
     * Igual ao navigateWithFade, mas com uma transição mais leve/sutil
     * (fade + leve zoom, sem slide) — usada especificamente ao entrar na
     * tela de "Connect with Google".
     */
    protected void navigateWithLightFade(Intent intent) {
        startActivity(intent);
        finish();
        overridePendingTransition(R.anim.light_fade_in, R.anim.light_fade_out);
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
