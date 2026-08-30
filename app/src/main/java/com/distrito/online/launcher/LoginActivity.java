package com.distrito.online.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.distrito.online.R;

/**
 * Tela exibida somente no primeiro acesso do player, ou quando ele ainda
 * não tem uma conta Google vinculada ao app. Mostra o seletor de idioma
 * e o botão "Connect with Google". Assim que o login é concluído com
 * sucesso, a conta é vinculada (LinkedAccountManager) e o player é
 * levado, com fade, para a ConnectActivity.
 */
public class LoginActivity extends BaseLauncherActivity {

    private static final int RC_SIGN_IN = 9001;

    private GoogleSignInClient googleSignInClient;
    private LinkedAccountManager accountManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (isFinishing()) return;
        setContentView(R.layout.login_screen);

        accountManager = new LinkedAccountManager(this);

        // Segurança extra: se por algum motivo essa tela for aberta
        // com uma conta já vinculada, pula direto pra ConnectActivity.
        if (accountManager.isLinked()) {
            navigateWithFade(new Intent(this, ConnectActivity.class));
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.btn_connect_google).setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

        findViewById(R.id.btn_language).setOnClickListener(v ->
                Toast.makeText(this, "Seletor de idioma ainda não implementado", Toast.LENGTH_SHORT).show());
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == RC_SIGN_IN) {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            handleSignInResult(task);
        }
    }

    private void handleSignInResult(Task<GoogleSignInAccount> completedTask) {
        try {
            GoogleSignInAccount account = completedTask.getResult(ApiException.class);
            onGoogleLoginSuccess(account);
        } catch (ApiException e) {
            Toast.makeText(this, "Não foi possível conectar com o Google. Tente novamente.", Toast.LENGTH_SHORT).show();
        }
    }

    private void onGoogleLoginSuccess(GoogleSignInAccount account) {
        accountManager.linkAccount(account);
        navigateWithFade(new Intent(this, ConnectActivity.class));
    }
}
