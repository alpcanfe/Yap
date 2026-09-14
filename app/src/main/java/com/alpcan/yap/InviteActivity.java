package com.alpcan.yap;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executor;

public class InviteActivity extends Activity {
    private static final int BG = Color.rgb(17, 16, 26);
    private static final int CARD = Color.rgb(29, 27, 42);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int MUTED = Color.rgb(170, 165, 189);

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private CredentialManager credentialManager;
    private Executor mainExecutor;
    private TextView message;
    private String inviteToken;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);

        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        mainExecutor = command -> runOnUiThread(command);

        inviteToken = extractInviteToken(getIntent() == null ? null : getIntent().getData());
        if (inviteToken == null || inviteToken.isEmpty()) {
            showInviteScreen("Davet bağlantısı geçersiz veya eksik.", false);
            return;
        }

        FirebaseUser current = auth.getCurrentUser();
        if (current != null && current.getEmail() != null) {
            showInviteScreen("Davet doğrulanıyor…", false);
            acceptInvite(current);
        } else {
            showInviteScreen("Davetin gönderildiği Google hesabıyla giriş yap.", true);
        }
    }

    private String extractInviteToken(Uri uri) {
        if (uri == null) return null;
        try {
            String direct = uri.getQueryParameter("token");
            if (direct != null && !direct.trim().isEmpty()) return direct.trim();

            String continueUrl = uri.getQueryParameter("continueUrl");
            if (continueUrl != null && !continueUrl.trim().isEmpty()) {
                Uri continueUri = Uri.parse(continueUrl);
                String token = continueUri.getQueryParameter("token");
                if (token != null && !token.trim().isEmpty()) return token.trim();
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void showInviteScreen(String text, boolean showGoogleButton) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(72), dp(24), dp(36));
        root.setBackgroundColor(BG);

        TextView logo = new TextView(this);
        logo.setText("Yap!");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(42);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(logo, matchWrap(dp(8)));

        TextView title = new TextView(this);
        title.setText("Şirket davetine katıl");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title, matchWrap(dp(20)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(CARD, dp(22)));
        root.addView(card, matchWrap(0));

        TextView info = new TextView(this);
        info.setText("Yönetici seni Yap! çalışma alanına davet etti. Güvenlik için yalnızca davetin gönderildiği Google hesabı kabul edilir.");
        info.setTextColor(MUTED);
        info.setTextSize(14);
        info.setLineSpacing(0, 1.12f);
        card.addView(info, matchWrap(dp(16)));

        message = new TextView(this);
        message.setText(text);
        message.setTextColor(Color.rgb(221, 214, 254));
        message.setTextSize(14);
        card.addView(message, matchWrap(dp(14)));

        if (showGoogleButton) {
            Button google = primaryButton("Google hesabıyla daveti kabul et");
            google.setOnClickListener(v -> startGoogleSignIn());
            card.addView(google, matchWrap(dp(8)));
        }

        Button close = secondaryButton("Kapat");
        close.setOnClickListener(v -> finish());
        card.addView(close, matchWrap(0));

        setContentView(root);
    }

    private void startGoogleSignIn() {
        setMessage("Google hesabı açılıyor…");
        try {
            if (credentialManager == null) credentialManager = CredentialManager.create(this);
            GetGoogleIdOption option = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(option)
                    .build();

            credentialManager.getCredentialAsync(
                    this,
                    request,
                    new CancellationSignal(),
                    mainExecutor,
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override
                        public void onResult(GetCredentialResponse result) {
                            handleGoogleCredential(result.getCredential());
                        }

                        @Override
                        public void onError(@NonNull GetCredentialException e) {
                            setMessage("Google hesabı seçilemedi. Tekrar deneyebilirsin.");
                        }
                    }
            );
        } catch (Exception e) {
            setMessage("Google girişi başlatılamadı.");
        }
    }

    private void handleGoogleCredential(Credential credential) {
        try {
            if (!(credential instanceof CustomCredential)) {
                setMessage("Google kimlik bilgisi alınamadı.");
                return;
            }
            CustomCredential custom = (CustomCredential) credential;
            if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(custom.getType())) {
                setMessage("Beklenmeyen Google kimlik bilgisi türü.");
                return;
            }

            GoogleIdTokenCredential google = GoogleIdTokenCredential.createFrom(custom.getData());
            AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(google.getIdToken(), null);
            auth.signInWithCredential(firebaseCredential).addOnCompleteListener(this, task -> {
                if (!task.isSuccessful()) {
                    setMessage("Google hesabıyla giriş yapılamadı.");
                    return;
                }
                FirebaseUser user = auth.getCurrentUser();
                if (user == null || user.getEmail() == null) {
                    auth.signOut();
                    setMessage("Google hesabının e-posta adresi alınamadı.");
                    return;
                }
                acceptInvite(user);
            });
        } catch (Exception e) {
            setMessage("Google hesabı doğrulanamadı.");
        }
    }

    private void acceptInvite(FirebaseUser user) {
        setMessage("Davet ve rol doğrulanıyor…");
        String signedEmail = safe(user.getEmail()).trim().toLowerCase(Locale.ROOT);
        DocumentReference inviteRef = db.collection("invites").document(inviteToken);

        inviteRef.get().addOnSuccessListener(invite -> {
            if (!invite.exists()) {
                auth.signOut();
                showInviteScreen("Bu davet artık geçerli değil.", true);
                return;
            }

            String invitedEmail = safe(invite.getString("email")).trim().toLowerCase(Locale.ROOT);
            String status = safe(invite.getString("status"));
            String orgId = safe(invite.getString("orgId"));
            String role = safe(invite.getString("role"));

            if (!"pending".equals(status)) {
                showInviteScreen("Bu davet daha önce kullanılmış veya iptal edilmiş.", false);
                return;
            }
            if (invitedEmail.isEmpty() || !invitedEmail.equals(signedEmail)) {
                auth.signOut();
                showInviteScreen("Seçtiğin Google hesabı davetin gönderildiği e-posta adresiyle eşleşmiyor. Doğru hesabı seçerek tekrar dene.", true);
                return;
            }
            if (orgId.isEmpty()) {
                showInviteScreen("Davette şirket bilgisi bulunamadı.", false);
                return;
            }
            if (role.isEmpty()) role = "Çalışan";

            Map<String, Object> member = new HashMap<>();
            member.put("uid", user.getUid());
            member.put("email", signedEmail);
            member.put("name", displayName(user));
            member.put("photo", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
            member.put("level", "member");
            member.put("role", role);
            member.put("inviteToken", inviteToken);
            member.put("joinedAt", FieldValue.serverTimestamp());

            Map<String, Object> profile = new HashMap<>();
            profile.put("uid", user.getUid());
            profile.put("email", signedEmail);
            profile.put("name", displayName(user));
            profile.put("activeOrgId", orgId);
            profile.put("updatedAt", FieldValue.serverTimestamp());

            Map<String, Object> accepted = new HashMap<>();
            accepted.put("status", "accepted");
            accepted.put("acceptedBy", user.getUid());
            accepted.put("acceptedAt", FieldValue.serverTimestamp());

            WriteBatch batch = db.batch();
            batch.set(db.collection("orgs").document(orgId).collection("members").document(user.getUid()), member, SetOptions.merge());
            batch.set(db.collection("users").document(user.getUid()), profile, SetOptions.merge());
            batch.set(inviteRef, accepted, SetOptions.merge());
            batch.commit().addOnCompleteListener(done -> {
                if (done.isSuccessful()) {
                    Intent openApp = new Intent(this, MainActivity.class);
                    openApp.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(openApp);
                    finish();
                } else {
                    setMessage("Davet kabul edilirken bir hata oluştu. Tekrar dene.");
                }
            });
        }).addOnFailureListener(error -> {
            auth.signOut();
            showInviteScreen("Bu Google hesabı davet edilen adresle eşleşmiyor veya davet okunamadı. Doğru hesabı seçerek tekrar dene.", true);
        });
    }

    private void setMessage(String text) {
        if (message != null) {
            message.setText(text);
            message.setVisibility(View.VISIBLE);
        }
    }

    private String displayName(FirebaseUser user) {
        if (user != null && user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) {
            return user.getDisplayName().trim();
        }
        String email = safe(user == null ? null : user.getEmail());
        if (email.contains("@")) return email.substring(0, email.indexOf('@'));
        return "Kullanıcı";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setBackground(rounded(PURPLE, dp(14)));
        return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setBackground(rounded(Color.rgb(39, 36, 58), dp(14)));
        return b;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        p.setMargins(0, 0, 0, bottomMargin);
        return p;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
