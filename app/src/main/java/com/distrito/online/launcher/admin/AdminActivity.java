package com.distrito.online.launcher.admin;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.firebase.auth.FirebaseAuth;
import com.distrito.online.R;
import com.distrito.online.launcher.BaseLauncherActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Painel admin. Só chega até aqui quem tem o botão liberado na
 * ConnectActivity (e-mail == AdminConfig.ADMIN_EMAIL), mas a checagem
 * abaixo é uma segunda trava do lado do app — a trava de verdade é a
 * Firestore Security Rule, que rejeita qualquer escrita nas coleções
 * administrativas vinda de outro e-mail, então mesmo alguém entrando
 * nesta tela na unha (ex: adb) não consegue realmente fazer nada aqui.
 */
public class AdminActivity extends BaseLauncherActivity {

    private AccountDirectory directory;
    private GithubTokenManager tokenManager;

    private final List<PlayerAccount> accounts = new ArrayList<>();
    private AdminUserAdapter adapter;

    private SwitchCompat switchEntryClosed;
    private EditText editClosedMessage;
    private EditText editGithubToken;
    private EditText editApkUrl;
    private EditText editVersionCode;
    private EditText editVersionName;
    private TextView txtAccountsSummary;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin);
        playEntryAnimationIfPending(findViewById(android.R.id.content));

        String currentEmail = FirebaseAuth.getInstance().getCurrentUser() != null
                ? FirebaseAuth.getInstance().getCurrentUser().getEmail() : null;
        if (!AdminConfig.isAdminEmail(currentEmail)) {
            Toast.makeText(this, "Sem acesso.", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        directory = new AccountDirectory();
        tokenManager = new GithubTokenManager(this);

        findViewById(R.id.btn_admin_back).setOnClickListener(v -> onBackPressed());

        switchEntryClosed = findViewById(R.id.switch_entry_closed);
        editClosedMessage = findViewById(R.id.edit_closed_message);
        editGithubToken = findViewById(R.id.edit_github_token);
        editApkUrl = findViewById(R.id.edit_apk_url);
        editVersionCode = findViewById(R.id.edit_version_code);
        editVersionName = findViewById(R.id.edit_version_name);
        txtAccountsSummary = findViewById(R.id.txt_accounts_summary);

        String savedToken = tokenManager.getToken();
        if (savedToken != null) editGithubToken.setText(savedToken);
        editGithubToken.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) tokenManager.saveToken(editGithubToken.getText().toString());
        });

        findViewById(R.id.btn_save_entry_state).setOnClickListener(v -> saveEntryState());
        findViewById(R.id.btn_publish_update).setOnClickListener(v -> publishUpdate());
        findViewById(R.id.btn_fetch_github_release).setOnClickListener(v -> fetchGithubRelease());

        RecyclerView recycler = findViewById(R.id.recycler_accounts);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminUserAdapter(accounts, new AdminUserAdapter.ActionListener() {
            @Override
            public void onToggleBan(PlayerAccount account) {
                toggleBanAccount(account);
            }

            @Override
            public void onBanIp(PlayerAccount account) {
                banIpDialog(account);
            }
        });
        recycler.setAdapter(adapter);

        SwipeRefreshLayout swipeRefresh = findViewById(R.id.swipe_refresh);
        swipeRefresh.setOnRefreshListener(() -> loadAccounts(swipeRefresh));

        loadEntryState();
        loadAccounts(swipeRefresh);
    }

    // -----------------------------------------------------------------
    // Fechar entrada
    // -----------------------------------------------------------------

    private void loadEntryState() {
        directory.getEntryState((closed, message) -> {
            switchEntryClosed.setChecked(closed);
            if (message != null) editClosedMessage.setText(message);
        });
    }

    private void saveEntryState() {
        boolean closed = switchEntryClosed.isChecked();
        String message = editClosedMessage.getText().toString().trim();
        directory.setEntryClosed(closed, message.isEmpty() ? null : message, (success, error) ->
                runOnUiThread(() -> Toast.makeText(this,
                        success ? "Salvo." : "Falha ao salvar.", Toast.LENGTH_SHORT).show()));
    }

    // -----------------------------------------------------------------
    // Atualização
    // -----------------------------------------------------------------

    private void fetchGithubRelease() {
        String token = editGithubToken.getText().toString().trim();
        if (!token.isEmpty()) tokenManager.saveToken(token);

        Toast.makeText(this, "Buscando release...", Toast.LENGTH_SHORT).show();
        GithubReleaseHelper.fetchLatestRelease(token, (info, error) -> runOnUiThread(() -> {
            if (error != null || info == null) {
                Toast.makeText(this, "Falha ao buscar release: "
                        + (error != null ? error.getMessage() : "desconhecida"), Toast.LENGTH_LONG).show();
                return;
            }
            if (info.apkDownloadUrl != null) editApkUrl.setText(info.apkDownloadUrl);
            if (info.tagName != null) editVersionName.setText(info.tagName);
            Toast.makeText(this, "Release encontrada. Confira o versionCode antes de publicar.",
                    Toast.LENGTH_LONG).show();
        }));
    }

    private void publishUpdate() {
        String apkUrl = editApkUrl.getText().toString().trim();
        String versionCodeStr = editVersionCode.getText().toString().trim();
        String versionName = editVersionName.getText().toString().trim();

        if (apkUrl.isEmpty() || versionCodeStr.isEmpty()) {
            Toast.makeText(this, "Preencha o link do APK e o versionCode.", Toast.LENGTH_SHORT).show();
            return;
        }
        int versionCode;
        try {
            versionCode = Integer.parseInt(versionCodeStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "versionCode precisa ser um número.", Toast.LENGTH_SHORT).show();
            return;
        }

        directory.publishUpdate(apkUrl, versionCode, versionName.isEmpty() ? null : versionName,
                (success, error) -> runOnUiThread(() -> Toast.makeText(this,
                        success ? "Atualização publicada pros players." : "Falha ao publicar.",
                        Toast.LENGTH_SHORT).show()));
    }

    // -----------------------------------------------------------------
    // Contas
    // -----------------------------------------------------------------

    private void loadAccounts(@Nullable SwipeRefreshLayout swipeRefresh) {
        if (swipeRefresh != null) swipeRefresh.setRefreshing(true);
        directory.fetchAllUsers(result -> runOnUiThread(() -> {
            accounts.clear();
            accounts.addAll(result);
            adapter.notifyDataSetChanged();

            long online = 0;
            for (PlayerAccount a : accounts) if (a.isOnline()) online++;
            txtAccountsSummary.setText(accounts.size() + " conta(s) registrada(s) • " + online + " online agora");

            if (swipeRefresh != null) swipeRefresh.setRefreshing(false);
        }));
    }

    private void toggleBanAccount(PlayerAccount account) {
        if (account.banned) {
            directory.unbanUser(account.uid, (success, error) -> runOnUiThread(() -> {
                Toast.makeText(this, success ? "Conta desbanida." : "Falha ao desbanir.", Toast.LENGTH_SHORT).show();
                loadAccounts(null);
            }));
            return;
        }

        EditText input = new EditText(this);
        input.setHint("Motivo do ban");
        new AlertDialog.Builder(this)
                .setTitle("Banir " + account.email)
                .setView(input)
                .setPositiveButton("Banir", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    directory.banUser(account.uid, reason.isEmpty() ? "Sem motivo informado" : reason,
                            (success, error) -> runOnUiThread(() -> {
                                Toast.makeText(this, success ? "Conta banida." : "Falha ao banir.",
                                        Toast.LENGTH_SHORT).show();
                                loadAccounts(null);
                            }));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void banIpDialog(PlayerAccount account) {
        if (account.ip == null) return;
        EditText input = new EditText(this);
        input.setHint("Motivo do ban de IP");
        new AlertDialog.Builder(this)
                .setTitle("Banir IP " + account.ip)
                .setView(input)
                .setPositiveButton("Banir IP", (dialog, which) -> {
                    String reason = input.getText().toString().trim();
                    directory.banIp(account.ip, reason.isEmpty() ? "Sem motivo informado" : reason,
                            (success, error) -> runOnUiThread(() -> Toast.makeText(this,
                                    success ? "IP banido." : "Falha ao banir IP.", Toast.LENGTH_SHORT).show()));
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }
}
