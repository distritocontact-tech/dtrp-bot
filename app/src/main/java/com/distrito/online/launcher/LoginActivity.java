package com.distrito.online.launcher;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.distrito.online.R;
import com.distrito.online.launcher.admin.AccountDirectory;

/**
 * Tela exibida somente no primeiro acesso do player, ou quando ele ainda
 * não tem uma conta Google vinculada ao app. Mostra o seletor de idioma
 * e o botão "Connect with Google". Assim que o login é concluído com
 * sucesso, a conta é vinculada (LinkedAccountManager) e o player é
 * levado, com fade, para a ConnectActivity.
 */
public class LoginActivity extends BaseLauncherActivity {

    private static final String TAG = "DTRP-GoogleSignIn";
    private static final int RC_SIGN_IN = 9001;

    // Client ID "web" do google-services.json — necessário pra pedir o
    // idToken que o FirebaseAuth usa pra autenticar de verdade (é isso
    // que faz request.auth.token.email existir nas Firestore Security
    // Rules e permitir o painel admin checar quem está logado).
    private static final String WEB_CLIENT_ID =
            "1011962422766-p14bn9rdrtbof6pup7q3r32qrf5khsvv.apps.googleusercontent.com";

    private GoogleSignInClient googleSignInClient;
    private LinkedAccountManager accountManager;
    private FirebaseAuth firebaseAuth;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_screen);
        playEntryAnimationIfPending(findViewById(android.R.id.content));

        accountManager = new LinkedAccountManager(this);
        firebaseAuth = FirebaseAuth.getInstance();

        // Segurança extra: se por algum motivo essa tela for aberta
        // com uma conta já vinculada, pula direto pra ConnectActivity.
        if (accountManager.isLinked()) {
            navigateWithFade(new Intent(this, ConnectActivity.class));
            return;
        }

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(WEB_CLIENT_ID)
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        findViewById(R.id.btn_connect_google).setOnClickListener(v -> {
            Intent signInIntent = googleSignInClient.getSignInIntent();
            startActivityForResult(signInIntent, RC_SIGN_IN);
        });

        findViewById(R.id.btn_language).setOnClickListener(v ->
                Toast.makeText(this, "Em desenvolvimento", Toast.LENGTH_SHORT).show());
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
            // Código 10 (DEVELOPER_ERROR) quase sempre significa que o SHA-1
            // usado pra assinar esse build não está cadastrado no projeto do
            // Firebase/Google Cloud pra este app — veja o build.gradle: não
            // há signingConfig definido, então cada máquina/CI assina com um
            // keystore de debug diferente. Rode "gradlew signingReport" pra
            // pegar o SHA-1 atual e cadastre em
            // https://console.firebase.google.com > Configurações do projeto
            // > seu app Android > Adicionar impressão digital, depois baixe
            // o google-services.json atualizado.
            Log.e(TAG, "Falha no login Google — código " + e.getStatusCode() + ": " + e.getMessage(), e);
            Toast.makeText(this,
                    "Não foi possível conectar com o Google (erro " + e.getStatusCode() + "). Tente novamente.",
                    Toast.LENGTH_LONG).show();
        }
    }

    private void onGoogleLoginSuccess(GoogleSignInAccount account) {
        // Autentica no Firebase com o mesmo login do Google — sem isso o
        // Firestore não sabe quem é o usuário e as Security Rules (que
        // são a trava de verdade do painel admin e dos bans) não têm
        // como funcionar.
        AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
        firebaseAuth.signInWithCredential(credential).addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Log.e(TAG, "Falha ao autenticar no Firebase", task.getException());
                // Sem FirebaseAuth não dá pra checar ban nem sincronizar a
                // conta, mas não travamos o player por causa disso — ele
                // ainda consegue jogar, só não aparece no painel admin.
                accountManager.linkAccount(account);
                navigateWithFade(new Intent(this, ConnectActivity.class));
                return;
            }

            FirebaseUser user = firebaseAuth.getCurrentUser();
            new AccountDirectory().syncAccountAndCheckAccess(user, (allowed, reason) -> runOnUiThread(() -> {
                if (!allowed) {
                    firebaseAuth.signOut();
                    Toast.makeText(this,
                            "Acesso bloqueado" + (reason != null ? ": " + reason : "."),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                accountManager.linkAccount(account);
                navigateWithFade(new Intent(this, ConnectActivity.class));
            }));
        });
    }
}
