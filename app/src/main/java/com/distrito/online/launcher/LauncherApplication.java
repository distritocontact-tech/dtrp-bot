package com.distrito.online.launcher;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Detecta quando o app volta pra frente depois de ter sido totalmente
 * parado em background (usuário apertou Home, trocou de app, etc. e o
 * processo continuou vivo) e força reiniciar o fluxo pela tela de
 * carregamento (EntryActivity) — mesmo que a Activity que estava em
 * foco (login/connect) nem tenha sido destruída pelo sistema.
 *
 * Importante: nesse caso o Android NÃO chama onCreate() de novo (a
 * Activity já existe em memória), só onRestart()/onStart(). Por isso a
 * checagem não pode morar no onCreate de cada Activity — ela precisa
 * ficar aqui, no callback de ciclo de vida do processo inteiro, que
 * dispara sempre que qualquer Activity entra em onStart(), tenha sido
 * recriada ou não.
 *
 * A contagem de Activities "started" só chega a zero quando NENHUMA tela
 * do app está visível (app totalmente em background). Quando ela volta
 * de 0 para 1, é sinal de retorno real ao app — diferente de uma simples
 * troca de tela dentro do próprio app, onde a próxima Activity é iniciada
 * antes da anterior parar (a contagem nunca chega a zero nesse caso).
 */
public class LauncherApplication extends Application implements Application.ActivityLifecycleCallbacks {

    private int startedActivityCount = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(this);
    }

    @Override
    public void onActivityStarted(@NonNull Activity activity) {
        if (startedActivityCount == 0 && !(activity instanceof EntryActivity)) {
            Intent restart = new Intent(activity, EntryActivity.class);
            restart.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(restart);
            activity.finish();
        }
        startedActivityCount++;
    }

    @Override
    public void onActivityStopped(@NonNull Activity activity) {
        startedActivityCount = Math.max(0, startedActivityCount - 1);
    }

    public static LauncherApplication from(Context context) {
        return (LauncherApplication) context.getApplicationContext();
    }

    @Override
    public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle savedInstanceState) {}

    @Override
    public void onActivityResumed(@NonNull Activity activity) {}

    @Override
    public void onActivityPaused(@NonNull Activity activity) {}

    @Override
    public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle outState) {}

    @Override
    public void onActivityDestroyed(@NonNull Activity activity) {}
}
