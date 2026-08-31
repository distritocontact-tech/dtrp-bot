package com.distrito.online.launcher.admin;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.distrito.online.R;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class AdminUserAdapter extends RecyclerView.Adapter<AdminUserAdapter.ViewHolder> {

    public interface ActionListener {
        void onToggleBan(PlayerAccount account);
        void onBanIp(PlayerAccount account);
    }

    private final List<PlayerAccount> accounts;
    private final ActionListener listener;

    public AdminUserAdapter(List<PlayerAccount> accounts, ActionListener listener) {
        this.accounts = accounts;
        this.listener = listener;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_admin_user, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        PlayerAccount acc = accounts.get(position);

        holder.txtEmail.setText(acc.email != null ? acc.email : "(sem e-mail)");

        boolean online = acc.isOnline();
        holder.dotStatus.setBackgroundResource(online
                ? R.drawable.admin_dot_online : R.drawable.admin_dot_offline);
        holder.txtStatusLabel.setText(online ? "online" : "offline");
        holder.txtStatusLabel.setTextColor(holder.itemView.getResources()
                .getColor(online ? R.color.launcher_success : R.color.launcher_text_muted));

        String lastSeen = acc.lastHeartbeat > 0
                ? DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(new Date(acc.lastHeartbeat))
                : "nunca";
        String ip = acc.ip != null ? acc.ip : "IP desconhecido";
        holder.txtDetails.setText(acc.uid + " • " + ip + " • visto: " + lastSeen);

        if (acc.banned) {
            holder.txtBanReason.setVisibility(View.VISIBLE);
            holder.txtBanReason.setText("Banido: " + (acc.banReason != null ? acc.banReason : "sem motivo informado"));
            holder.btnToggleBan.setText("Desbanir conta");
        } else {
            holder.txtBanReason.setVisibility(View.GONE);
            holder.btnToggleBan.setText("Banir conta");
        }

        holder.btnToggleBan.setOnClickListener(v -> {
            if (listener != null) listener.onToggleBan(acc);
        });
        holder.btnBanIp.setOnClickListener(v -> {
            if (listener != null) listener.onBanIp(acc);
        });
        holder.btnBanIp.setEnabled(acc.ip != null);
        holder.btnBanIp.setAlpha(acc.ip != null ? 1f : 0.4f);
    }

    @Override
    public int getItemCount() {
        return accounts.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        View dotStatus;
        TextView txtEmail, txtStatusLabel, txtDetails, txtBanReason, btnBanIp, btnToggleBan;

        ViewHolder(View itemView) {
            super(itemView);
            dotStatus = itemView.findViewById(R.id.dot_status);
            txtEmail = itemView.findViewById(R.id.txt_email);
            txtStatusLabel = itemView.findViewById(R.id.txt_status_label);
            txtDetails = itemView.findViewById(R.id.txt_details);
            txtBanReason = itemView.findViewById(R.id.txt_ban_reason);
            btnBanIp = itemView.findViewById(R.id.btn_ban_ip);
            btnToggleBan = itemView.findViewById(R.id.btn_toggle_ban);
        }
    }
}
