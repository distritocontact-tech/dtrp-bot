package com.distrito.online.launcher;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.distrito.online.R;

import java.io.File;
import java.util.List;

/**
 * Tela principal exibida depois do login (ou direto após o loading, para
 * quem já tem conta Google vinculada). Mostra o servidor, jogadores
 * online e o botão de atualizar. O botão "Trocar de conta" desvincula
 * a conta atual e devolve o player para a tela de login.
 */
public class ConnectActivity extends BaseLauncherActivity {

    private static final String SERVER_QUERY_HOST = "172.96.140.62";
    private static final int SERVER_QUERY_PORT = 9671;

    private static final String DISCORD_URL = "https://discord.gg/CJ2Kc64pmp";
    private static final String YOUTUBE_URL = "https://youtube.com/@distritorpsamp?si=eD6P9B3GA9eKCgYe";
    private static final String TIKTOK_URL = "https://www.tiktok.com/@distritorpsamp?_r=1&_t=ZS-99Kcf4NS0Rx";

    private static final String INSTALL_FOLDER_NAME = "com.distrito.online";

    private LinkedAccountManager accountManager;
    private GoogleSignInClient googleSignInClient;

    private TextView txtServerName;
    private TextView txtServerSlots;
    private FileIntegrityManager integrityManager;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connect_screen);
        playEntryAnimationIfPending(findViewById(android.R.id.content));

        accountManager = new LinkedAccountManager(this);

        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .build();
        googleSignInClient = GoogleSignIn.getClient(this, gso);

        txtServerName = findViewById(R.id.txt_server_name);
        txtServerSlots = findViewById(R.id.txt_server_slots);

        findViewById(R.id.btn_switch_account).setOnClickListener(v ->
                playTapFeedback(v, this::switchAccount));

        integrityManager = new FileIntegrityManager(this, new File(getFilesDir(), INSTALL_FOLDER_NAME));

        findViewById(R.id.btn_atualizar).setOnClickListener(v ->
                playTapFeedback(v, this::onUpdateOrRepairClicked));

        findViewById(R.id.btn_discord).setOnClickListener(v -> playTapFeedback(v, () -> openUrl(DISCORD_URL)));
        findViewById(R.id.btn_tiktok).setOnClickListener(v -> playTapFeedback(v, () -> openUrl(TIKTOK_URL)));
        findViewById(R.id.btn_youtube).setOnClickListener(v -> playTapFeedback(v, () -> openUrl(YOUTUBE_URL)));

        refreshServerStatus();
        checkFileIntegrity();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Reavalia sempre que a tela volta a ficar visível (ex: player
        // saiu, mexeu em algo com um gerenciador de arquivos, voltou).
        if (integrityManager != null) checkFileIntegrity();
    }

    /**
     * Verifica se algum arquivo da pasta com.distrito.online foi apagado
     * ou alterado desde a última instalação/reparo válido. Se sim, marca
     * só esses arquivos como pendentes e troca o botão de "Atualizar"
     * para "Baixar" — o player não pode jogar até reinstalar o que foi
     * mexido, mas o resto da instalação não é afetado nem precisa ser
     * baixado de novo.
     */
    private void checkFileIntegrity() {
        new Thread(() -> {
            FileIntegrityManager.VerifyResult result = integrityManager.verify();
            boolean needsRepair = !result.isClean() && !result.manifestMissing;
            // manifestMissing quando não existe manifesto ainda (primeira
            // instalação, provavelmente em andamento) não conta como
            // adulteração — só quando a pasta já existia e o manifesto
            // some/não bate é que tratamos como violação.
            File installDir = new File(getFilesDir(), INSTALL_FOLDER_NAME);
            boolean folderExists = installDir.exists() && installDir.isDirectory()
                    && installDir.listFiles() != null && installDir.listFiles().length > 0;

            if (needsRepair || (result.manifestMissing && folderExists)) {
                List<String> broken = result.corruptedOrMissing;
                integrityManager.markPendingRepair(broken);
            }

            boolean pending = integrityManager.hasPendingRepair();
            runOnUiThread(() -> updateActionButton(pending));
        }, "integrity-check-thread").start();
    }

    private void updateActionButton(boolean repairPending) {
        TextView txt = findViewById(R.id.txt_atualizar);
        if (txt == null) return;
        // repairo pendente: força "BAIXAR" (arquivo específico foi mexido
        // e precisa ser reinstalado); do contrário mantém o rótulo normal
        // da tela (atualização geral do jogo).
        txt.setText(repairPending ? "BAIXAR" : "ATUALIZAR");
    }

    private void onUpdateOrRepairClicked() {
        Intent intent = new Intent(this, InstallActivity.class);
        if (integrityManager.hasPendingRepair()) {
            intent.putExtra(InstallActivity.EXTRA_REPAIR_MODE, true);
        }
        navigateWithFade(intent);
    }

    /**
     * Consulta o servidor de verdade (protocolo de query do SA-MP/open.mp)
     * e atualiza o nome + "jogadores/max" reais no card. Se a consulta
     * falhar (servidor offline, sem internet, etc), mostra "Offline" em
     * vez de deixar o placeholder "Consultando...".
     */
    private void refreshServerStatus() {
        txtServerName.setText("Consultando servidor...");
        txtServerSlots.setText("--/--");

        new ServerStatusManager(SERVER_QUERY_HOST, SERVER_QUERY_PORT).fetch(result -> {
            if (isFinishing() || isDestroyed()) return;

            if (result.success) {
                txtServerName.setText(result.serverName);
                txtServerSlots.setText(result.players + "/" + result.maxPlayers);
            } else {
                txtServerName.setText("Servidor offline");
                txtServerSlots.setText("--/--");
            }
        });
    }

    private void openUrl(String url) {
        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
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

