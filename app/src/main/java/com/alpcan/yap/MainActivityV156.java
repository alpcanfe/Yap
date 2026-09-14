package com.alpcan.yap;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MainActivityV156 extends Activity {
    private static final int BG = Color.rgb(17, 16, 26);
    private static final int CARD = Color.rgb(29, 27, 42);
    private static final int CARD2 = Color.rgb(39, 36, 58);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int MUTED = Color.rgb(170, 165, 189);
    private static final int GOOGLE_REQUEST = 9561;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleClient;
    private EditText emailInput;
    private EditText passwordInput;
    private EditText companyInput;
    private EditText inviteInput;
    private TextView messageView;
    private String legacyOrgId;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build();
        googleClient = GoogleSignIn.getClient(this, gso);
        route();
    }

    private void route() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            legacyOrgId = null;
            showLogin();
        } else {
            inspectMembership(user);
        }
    }

    private void inspectMembership(FirebaseUser user) {
        showLoading("Hesabın kontrol ediliyor...");
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(profile -> {
                    String orgId = profile.getString("activeOrgId");
                    if (orgId == null || orgId.trim().isEmpty()) {
                        legacyOrgId = null;
                        showWorkspaceChoice();
                        return;
                    }
                    DocumentReference orgRef = db.collection("orgs").document(orgId);
                    orgRef.get().addOnSuccessListener(org -> {
                        if (!org.exists()) {
                            legacyOrgId = null;
                            showWorkspaceChoice();
                            return;
                        }
                        orgRef.collection("members").document(user.getUid()).get()
                                .addOnSuccessListener(member -> {
                                    if (!member.exists()) {
                                        legacyOrgId = null;
                                        showWorkspaceChoice();
                                        return;
                                    }
                                    String level = member.getString("level");
                                    String ownerUid = org.getString("ownerUid");
                                    Boolean setupComplete = org.getBoolean("setupComplete");
                                    boolean legacyAutoOwner = "owner".equals(level)
                                            && user.getUid().equals(ownerUid)
                                            && !Boolean.TRUE.equals(setupComplete);
                                    if (legacyAutoOwner) {
                                        legacyOrgId = orgId;
                                        showWorkspaceChoice();
                                    } else {
                                        launchApp();
                                    }
                                })
                                .addOnFailureListener(e -> showError("Üyelik bilgisi alınamadı."));
                    }).addOnFailureListener(e -> showError("Şirket bilgisi alınamadı."));
                })
                .addOnFailureListener(e -> showError("Hesap bilgisi alınamadı."));
    }

    private void showLogin() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(38), dp(24), dp(30));
        scroll.addView(root);

        root.addView(title("Yap!", 42), match(dp(6)));
        TextView lead = text("Şirketini oluştur veya ekibinin davet koduyla katıl.", MUTED, 15);
        lead.setGravity(Gravity.CENTER);
        root.addView(lead, match(dp(24)));

        LinearLayout card = card();
        root.addView(card, match(dp(12)));
        emailInput = field("E-posta adresin");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(emailInput, match(dp(10)));
        passwordInput = field("Şifren");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(passwordInput, match(dp(8)));
        messageView = message();
        card.addView(messageView, match(dp(10)));

        Button login = primary("Giriş yap");
        login.setOnClickListener(v -> emailLogin(false));
        card.addView(login, match(dp(8)));
        Button register = secondary("Hesap oluştur");
        register.setOnClickListener(v -> emailLogin(true));
        card.addView(register, match(dp(8)));
        Button google = secondary("Google hesabıyla devam et");
        google.setOnClickListener(v -> startGoogle());
        card.addView(google, match(dp(8)));
        Button reset = link("Şifremi unuttum");
        reset.setOnClickListener(v -> resetPassword());
        card.addView(reset, match(0));
        setSafeContent(scroll);
    }

    private void showWorkspaceChoice() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { showLogin(); return; }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = column();
        root.setPadding(dp(24), dp(30), dp(24), dp(34));
        scroll.addView(root);

        root.addView(title("Yap!", 38), match(dp(4)));
        TextView email = text(safe(user.getEmail()), MUTED, 13);
        root.addView(email, match(dp(22)));
        root.addView(title("Nasıl devam edeceksin?", 24), match(dp(8)));
        root.addView(text("Yeni bir şirket oluşturabilir veya yöneticinin gönderdiği davet koduyla mevcut bir şirkete katılabilirsin.", MUTED, 14), match(dp(18)));

        if (legacyOrgId != null) {
            TextView legacy = text("Eski sürümde otomatik oluşturulmuş çalışma alanını bulduk. Şirket oluşturursan mevcut görevlerin korunur ve bu alan senin şirketin olur.", Color.rgb(216, 205, 255), 13);
            legacy.setBackground(rounded(Color.rgb(44, 37, 74), dp(16)));
            legacy.setPadding(dp(14), dp(13), dp(14), dp(13));
            root.addView(legacy, match(dp(16)));
        }

        LinearLayout createCard = card();
        root.addView(createCard, match(dp(14)));
        createCard.addView(title("Şirket oluştur", 19), match(dp(6)));
        createCard.addView(text("Şirketi oluşturan hesap Kurucu Yönetici (owner) olur.", MUTED, 13), match(dp(10)));
        companyInput = field("Şirket / ekip adı");
        createCard.addView(companyInput, match(dp(8)));
        Button create = primary("Şirketimi oluştur");
        create.setOnClickListener(v -> createCompany());
        createCard.addView(create, match(0));

        LinearLayout joinCard = card();
        root.addView(joinCard, match(dp(14)));
        joinCard.addView(title("Davet koduyla katıl", 19), match(dp(6)));
        joinCard.addView(text("Kod yalnız davet edilen e-posta hesabında çalışır. Şirkete çalışan olarak eklenirsin; rolün davetteki gibi atanır.", MUTED, 13), match(dp(10)));
        inviteInput = field("ABCDE-23456");
        inviteInput.setAllCaps(true);
        joinCard.addView(inviteInput, match(dp(8)));
        Button join = secondary("Ekibe katıl");
        join.setOnClickListener(v -> acceptInvite());
        joinCard.addView(join, match(0));

        messageView = message();
        root.addView(messageView, match(dp(10)));
        Button logout = link("Başka hesapla giriş yap");
        logout.setOnClickListener(v -> signOut());
        root.addView(logout, match(0));
        setSafeContent(scroll);
    }

    private void emailLogin(boolean register) {
        String email = emailInput == null ? "" : emailInput.getText().toString().trim();
        String password = passwordInput == null ? "" : passwordInput.getText().toString();
        if (!email.contains("@")) { showMessage("Geçerli bir e-posta adresi yaz."); return; }
        if (password.length() < 6) { showMessage("Şifre en az 6 karakter olmalı."); return; }
        showMessage(register ? "Hesap oluşturuluyor..." : "Giriş yapılıyor...");
        Task<?> task = register
                ? auth.createUserWithEmailAndPassword(email, password)
                : auth.signInWithEmailAndPassword(email, password);
        task.addOnCompleteListener(this, result -> {
            if (result.isSuccessful()) {
                FirebaseUser user = auth.getCurrentUser();
                if (register && user != null && !user.isEmailVerified()) user.sendEmailVerification();
                route();
            } else {
                showMessage(authError(result.getException()));
            }
        });
    }

    private void startGoogle() {
        showMessage("Google hesabı açılıyor...");
        startActivityForResult(googleClient.getSignInIntent(), GOOGLE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != GOOGLE_REQUEST) return;
        try {
            GoogleSignInAccount account = GoogleSignIn.getSignedInAccountFromIntent(data).getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                showMessage("Google hesabından kimlik bilgisi alınamadı.");
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            auth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) route();
                else showMessage(authError(task.getException()));
            });
        } catch (ApiException e) {
            showMessage("Google girişi tamamlanamadı. Kod: " + e.getStatusCode());
        }
    }

    private void resetPassword() {
        String email = emailInput == null ? "" : emailInput.getText().toString().trim();
        if (!email.contains("@")) { showMessage("Önce e-posta adresini yaz."); return; }
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this, task ->
                showMessage(task.isSuccessful() ? "Şifre yenileme bağlantısı gönderildi." : authError(task.getException())));
    }

    private void createCompany() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { showLogin(); return; }
        if (!user.isEmailVerified()) {
            user.sendEmailVerification();
            showMessage("Önce e-posta adresini doğrula. Doğrulama bağlantısını yeniden gönderdim.");
            return;
        }
        String name = companyInput == null ? "" : companyInput.getText().toString().trim();
        if (name.length() < 2) { showMessage("Şirket / ekip adını yaz."); return; }
        showMessage("Şirket oluşturuluyor...");

        String orgId = legacyOrgId;
        DocumentReference orgRef = orgId == null
                ? db.collection("orgs").document()
                : db.collection("orgs").document(orgId);
        orgId = orgRef.getId();

        Map<String, Object> org = new HashMap<>();
        org.put("name", name);
        org.put("ownerUid", user.getUid());
        org.put("setupComplete", true);
        org.put("updatedAt", FieldValue.serverTimestamp());
        if (legacyOrgId == null) org.put("createdAt", FieldValue.serverTimestamp());

        String finalOrgId = orgId;
        orgRef.set(org, SetOptions.merge()).addOnSuccessListener(v -> {
            Map<String, Object> member = new HashMap<>();
            member.put("uid", user.getUid());
            member.put("email", safe(user.getEmail()).toLowerCase(Locale.ROOT));
            member.put("name", displayName(user));
            member.put("photo", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
            member.put("level", "owner");
            member.put("role", "Yönetici");
            member.put("joinedAt", FieldValue.serverTimestamp());
            orgRef.collection("members").document(user.getUid()).set(member, SetOptions.merge())
                    .addOnSuccessListener(x -> {
                        Map<String, Object> profile = new HashMap<>();
                        profile.put("uid", user.getUid());
                        profile.put("email", safe(user.getEmail()).toLowerCase(Locale.ROOT));
                        profile.put("name", displayName(user));
                        profile.put("activeOrgId", finalOrgId);
                        profile.put("updatedAt", FieldValue.serverTimestamp());
                        db.collection("users").document(user.getUid()).set(profile, SetOptions.merge())
                                .addOnSuccessListener(y -> {
                                    Map<String, Object> role = new HashMap<>();
                                    role.put("name", "Çalışan");
                                    role.put("createdAt", FieldValue.serverTimestamp());
                                    orgRef.collection("roles").document("calisan").set(role, SetOptions.merge());
                                    launchApp();
                                })
                                .addOnFailureListener(e -> showMessage("Profil şirketle eşleştirilemedi."));
                    })
                    .addOnFailureListener(e -> showMessage("Kurucu yönetici kaydı oluşturulamadı."));
        }).addOnFailureListener(e -> showMessage("Şirket oluşturulamadı: " + simple(e)));
    }

    private void acceptInvite() {
        String code = inviteInput == null ? "" : inviteInput.getText().toString();
        showMessage("Davet kontrol ediliyor...");
        InviteCodeService.accept(auth, db, code, new InviteCodeService.Callback() {
            @Override public void notice(String type, String message) { runOnUiThread(() -> showMessage(message)); }
            @Override public void refresh() { }
            @Override public void joined(String orgId, String role) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivityV156.this, "Ekibe katıldın: " + role, Toast.LENGTH_LONG).show();
                    launchApp();
                });
            }
        });
    }

    private void launchApp() {
        Intent i = new Intent(this, MainActivityApp156.class);
        startActivity(i);
        finish();
    }

    private void signOut() {
        try { auth.signOut(); } catch (Exception ignored) {}
        try { googleClient.signOut(); } catch (Exception ignored) {}
        legacyOrgId = null;
        showLogin();
    }

    private void showLoading(String text) {
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(40), dp(28), dp(40));
        root.setBackgroundColor(BG);
        root.addView(title("Yap!", 38), match(dp(14)));
        TextView msg = text(text, MUTED, 15);
        msg.setGravity(Gravity.CENTER);
        root.addView(msg, match(0));
        setSafeContent(root);
    }

    private void showError(String text) {
        LinearLayout root = column();
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(54), dp(24), dp(32));
        root.setBackgroundColor(BG);
        root.addView(title("Yap!", 38), match(dp(16)));
        root.addView(title("Bağlantı kurulamadı", 22), match(dp(10)));
        TextView info = text(text, MUTED, 14);
        info.setGravity(Gravity.CENTER);
        root.addView(info, match(dp(18)));
        Button retry = primary("Tekrar dene");
        retry.setOnClickListener(v -> route());
        root.addView(retry, match(dp(8)));
        Button logout = secondary("Oturumu kapat");
        logout.setOnClickListener(v -> signOut());
        root.addView(logout, match(0));
        setSafeContent(root);
    }

    private void setSafeContent(View root) {
        final int l = root.getPaddingLeft();
        final int t = root.getPaddingTop();
        final int r = root.getPaddingRight();
        final int b = root.getPaddingBottom();
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int left, top, right, bottom;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                left = safe.left; top = safe.top; right = safe.right; bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
            }
            v.setPadding(l + left, t + top, r + right, b + bottom);
            return insets;
        });
        setContentView(root);
        root.requestApplyInsets();
    }

    private LinearLayout column() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundColor(BG);
        return l;
    }

    private LinearLayout card() {
        LinearLayout c = column();
        c.setPadding(dp(18), dp(18), dp(18), dp(18));
        c.setBackground(rounded(CARD, dp(22)));
        return c;
    }

    private TextView title(String s, int size) {
        TextView t = text(s, Color.WHITE, size);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private TextView text(String s, int color, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(size);
        return t;
    }

    private TextView message() {
        TextView t = text("", Color.rgb(255, 205, 214), 13);
        t.setVisibility(View.GONE);
        return t;
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setHintTextColor(MUTED);
        e.setTextColor(Color.WHITE);
        e.setTextSize(15);
        e.setSingleLine(true);
        e.setPadding(dp(14), dp(12), dp(14), dp(12));
        e.setBackground(rounded(Color.rgb(21, 19, 31), dp(14)));
        return e;
    }

    private Button primary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackground(rounded(PURPLE, dp(14)));
        return b;
    }

    private Button secondary(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(15);
        b.setBackground(rounded(CARD2, dp(14)));
        return b;
    }

    private Button link(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(Color.rgb(189, 156, 255));
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private LinearLayout.LayoutParams match(int bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, bottom);
        return p;
    }

    private void showMessage(String s) {
        if (messageView == null) return;
        messageView.setText(s);
        messageView.setVisibility(View.VISIBLE);
    }

    private String displayName(FirebaseUser user) {
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) return user.getDisplayName().trim();
        String email = safe(user.getEmail());
        return email.contains("@") ? email.substring(0, email.indexOf('@')) : "Kullanıcı";
    }

    private String authError(Exception e) {
        String s = e == null || e.getMessage() == null ? "" : e.getMessage().toUpperCase(Locale.ROOT);
        if (s.contains("ALREADY") && s.contains("EMAIL")) return "Bu e-posta adresi zaten kayıtlı.";
        if (s.contains("INVALID") && (s.contains("CREDENTIAL") || s.contains("PASSWORD"))) return "E-posta veya şifre hatalı.";
        if (s.contains("WEAK") && s.contains("PASSWORD")) return "Şifre en az 6 karakter olmalı.";
        if (s.contains("NETWORK") || s.contains("TIMEOUT")) return "İnternet bağlantısı kurulamadı.";
        return e == null ? "İşlem tamamlanamadı." : "İşlem tamamlanamadı: " + simple(e);
    }

    private String simple(Throwable t) {
        if (t == null) return "Bilinmeyen hata";
        String m = t.getMessage();
        return m == null || m.trim().isEmpty() ? t.getClass().getSimpleName() : m;
    }

    private String safe(String s) { return s == null ? "" : s; }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
