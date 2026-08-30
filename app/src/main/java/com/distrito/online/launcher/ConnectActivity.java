package com.distrito.online.launcher;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.distrito.online.R;

/**
 * Tela principal exibida depois do login (ou direto após o loading, para
 * quem já tem conta Google vinculada). Mostra o servidor, jogadores
 * online e o botão de atualizar. O botão "Trocar de conta" desvincula
 * a conta atual e devolve o player para a tela de login.
 */
public class ConnectActivity extends BaseLauncherActivity {

    private LinkedAccountManager accountManager;
    private GoogleSignInClient googleSignInClient;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connect_screen);

        accountManager = new LinkedAccountManager(this);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.btn_switch_account).setOnClickListener(v -> switchAccount());

        findViewById(R.id.btn_atualizar).setOnClickListener(v -> {
            // TODO: disparar a checagem/baixa de atualização do servidor.
        });

        // Exemplo de como atualizar os textos dinâmicos vindos do servidor:
        // TextView playersOnline = findViewById(R.id.txt_players_online);
        // playersOnline.setText(String.valueOf(quantidadeDeJogadores));
    }

    /**
     * Desvincula a conta atual e, depois que o Google confirma o sign-out,
     * volta para a LoginActivity para o player escolher outra conta.
     */
    private void switchAccount() {
        accountManager.unlinkAccount();
        googleSignInClient.signOut().addOnCompleteListener(task ->
                navigateWithFade(new Intent(this, LoginActivity.class)));
    }
}
