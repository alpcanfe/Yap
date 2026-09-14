package com.alpcan.yap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.credentials.ClearCredentialStateRequest;
import androidx.credentials.Credential;
import androidx.credentials.CredentialManager;
import androidx.credentials.CredentialManagerCallback;
import androidx.credentials.CustomCredential;
import androidx.credentials.GetCredentialRequest;
import androidx.credentials.GetCredentialResponse;
import androidx.credentials.exceptions.ClearCredentialException;
import androidx.credentials.exceptions.GetCredentialException;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.auth.ActionCodeSettings;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QuerySnapshot;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(17, 16, 26);
    private static final int CARD = Color.rgb(29, 27, 42);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int MUTED = Color.rgb(170, 165, 189);

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private CredentialManager credentialManager;
    private Executor mainExecutor;
    private WebView web;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView authMessage;
    private EditText inviteEmailInput;

    private String currentOrgId;
    private String currentLevel;
    private String currentRole;
    private String pendingEmailLink;
    private String pendingInviteToken;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureSystemBars();
        FirebaseApp.initializeApp(this);
        auth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();
        mainExecutor = command -> runOnUiThread(command);
        handleIncomingIntent(getIntent());

        if (pendingEmailLink != null) {
            FirebaseUser current = auth.getCurrentUser();
            if (current != null && current.getEmail() != null) acceptInvite(pendingInviteToken, current.getEmail());
            else showAuthScreen();
        } else if (auth.getCurrentUser() != null) {
            ensureWorkspaceAndOpen();
        } else {
            showAuthScreen();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
        if (pendingEmailLink != null) {
            FirebaseUser current = auth.getCurrentUser();
            if (current != null && current.getEmail() != null) acceptInvite(pendingInviteToken, current.getEmail());
            else showAuthScreen();
        }
    }

    private void handleIncomingIntent(Intent intent) {
        if (intent == null || intent.getData() == null || auth == null) return;
        String link = intent.getData().toString();
        if (!auth.isSignInWithEmailLink(link)) return;
        pendingEmailLink = link;
        try {
            Uri linkUri = Uri.parse(link);
            String continueUrl = linkUri.getQueryParameter("continueUrl");
            if (continueUrl != null) pendingInviteToken = Uri.parse(continueUrl).getQueryParameter("token");
        } catch (Exception ignored) {}
    }

    private void showAuthScreen() {
        if (web != null) {
            web.stopLoading();
            web.destroy();
            web = null;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(64), dp(24), dp(30));
        scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

        TextView logo = new TextView(this);
        logo.setText("Yap!");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(42);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(logo, matchWrap(dp(6)));

        TextView lead = new TextView(this);
        lead.setText(pendingEmailLink != null ? "Şirket davetine katıl" : "Ekibinle işleri tek yerde yönet.");
        lead.setTextColor(MUTED);
        lead.setTextSize(15);
        lead.setGravity(Gravity.CENTER);
        root.addView(lead, matchWrap(dp(24)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(CARD, dp(24)));
        root.addView(card, matchWrap(dp(12)));

        if (pendingEmailLink != null) {
            TextView inviteInfo = new TextView(this);
            inviteInfo.setText("Davet gönderilen e-posta adresini doğrula. Rolün ve şirket alanın otomatik tanımlanacak.");
            inviteInfo.setTextColor(MUTED);
            inviteInfo.setTextSize(14);
            card.addView(inviteInfo, matchWrap(dp(14)));

            inviteEmailInput = field("Davet edilen e-posta adresi");
            inviteEmailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            card.addView(inviteEmailInput, matchWrap(dp(10)));

            authMessage = new TextView(this);
            authMessage.setTextColor(Color.rgb(255, 214, 220));
            authMessage.setTextSize(13);
            authMessage.setVisibility(View.GONE);
            card.addView(authMessage, matchWrap(dp(10)));

            Button accept = primaryButton("Daveti kabul et");
            accept.setOnClickListener(v -> completeEmailInvite());
            card.addView(accept, matchWrap(0));
            setContentView(scroll);
            return;
        }

        emailInput = field("E-posta adresin");
        emailInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
        card.addView(emailInput, matchWrap(dp(10)));

        passwordInput = field("Şifren");
        passwordInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        card.addView(passwordInput, matchWrap(dp(8)));

        authMessage = new TextView(this);
        authMessage.setTextColor(Color.rgb(255, 214, 220));
        authMessage.setTextSize(13);
        authMessage.setVisibility(View.GONE);
        card.addView(authMessage, matchWrap(dp(10)));

        Button login = primaryButton("Giriş yap");
        login.setOnClickListener(v -> emailLogin(false));
        card.addView(login, matchWrap(dp(8)));

        Button register = secondaryButton("Hesap oluştur");
        register.setOnClickListener(v -> emailLogin(true));
        card.addView(register, matchWrap(dp(8)));

        Button google = secondaryButton("Google hesabıyla devam et");
        google.setOnClickListener(v -> startGoogleSignIn());
        card.addView(google, matchWrap(dp(8)));

        Button reset = linkButton("Şifremi unuttum");
        reset.setOnClickListener(v -> resetPassword());
        card.addView(reset, matchWrap(0));

        setContentView(scroll);
    }

    private void completeEmailInvite() {
        String email = inviteEmailInput == null ? "" : inviteEmailInput.getText().toString().trim().toLowerCase(Locale.ROOT);
        if (!email.contains("@")) { showMessage("Davet gönderilen e-posta adresini yaz."); return; }
        showMessage("Davet doğrulanıyor...");
        auth.signInWithEmailLink(email, pendingEmailLink).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) acceptInvite(pendingInviteToken, email);
            else showMessage("Davet bağlantısı doğrulanamadı veya süresi dolmuş olabilir.");
        });
    }

    private void acceptInvite(String token, String email) {
        if (token == null || token.isEmpty()) { showAuthScreen(); showMessage("Davet kodu bulunamadı."); return; }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { showAuthScreen(); return; }
        final String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        DocumentReference inviteRef = db.collection("invites").document(token);
        inviteRef.get().addOnSuccessListener(invite -> {
            if (!invite.exists()) { showAuthScreen(); showMessage("Bu davet artık geçerli değil."); return; }
            String invitedEmail = safe(invite.getString("email")).toLowerCase(Locale.ROOT);
            if (!invitedEmail.equals(normalized)) { auth.signOut(); showAuthScreen(); showMessage("Bu davet farklı bir e-posta adresine gönderilmiş."); return; }
            if (!"pending".equals(safe(invite.getString("status")))) { showAuthScreen(); showMessage("Bu davet daha önce kullanılmış."); return; }
            String orgId = invite.getString("orgId");
            String role = safe(invite.getString("role"));
            if (orgId == null || orgId.isEmpty()) { showAuthScreen(); showMessage("Davet şirket bilgisi içermiyor."); return; }

            Map<String, Object> member = new HashMap<>();
            member.put("uid", user.getUid());
            member.put("email", normalized);
            member.put("name", displayName(user));
            member.put("photo", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
            member.put("level", "member");
            member.put("role", role.isEmpty() ? "Çalışan" : role);
            member.put("inviteToken", token);
            member.put("joinedAt", FieldValue.serverTimestamp());

            Map<String, Object> profile = new HashMap<>();
            profile.put("uid", user.getUid());
            profile.put("email", normalized);
            profile.put("name", displayName(user));
            profile.put("activeOrgId", orgId);
            profile.put("updatedAt", FieldValue.serverTimestamp());

            WriteBatch batch = db.batch();
            batch.set(db.collection("orgs").document(orgId).collection("members").document(user.getUid()), member, SetOptions.merge());
            batch.set(db.collection("users").document(user.getUid()), profile, SetOptions.merge());
            Map<String, Object> accepted = new HashMap<>();
            accepted.put("status", "accepted");
            accepted.put("acceptedBy", user.getUid());
            accepted.put("acceptedAt", FieldValue.serverTimestamp());
            batch.set(inviteRef, accepted, SetOptions.merge());
            batch.commit().addOnCompleteListener(result -> {
                if (result.isSuccessful()) {
                    pendingEmailLink = null;
                    pendingInviteToken = null;
                    currentOrgId = orgId;
                    currentLevel = "member";
                    currentRole = role.isEmpty() ? "Çalışan" : role;
                    showApp();
                } else {
                    showAuthScreen();
                    showMessage(firestoreError(result.getException()));
                }
            });
        }).addOnFailureListener(e -> { showAuthScreen(); showMessage(firestoreError(e)); });
    }

    private void emailLogin(boolean register) {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (!email.contains("@")) { showMessage("Geçerli bir e-posta adresi yaz."); return; }
        if (password.length() < 6) { showMessage("Şifre en az 6 karakter olmalı."); return; }
        showMessage(register ? "Hesap oluşturuluyor..." : "Giriş yapılıyor...");
        Task<?> task = register ? auth.createUserWithEmailAndPassword(email, password) : auth.signInWithEmailAndPassword(email, password);
        task.addOnCompleteListener(this, result -> {
            if (result.isSuccessful()) ensureWorkspaceAndOpen();
            else showMessage(authError(result.getException()));
        });
    }

    private void resetPassword() {
        String email = emailInput.getText().toString().trim();
        if (!email.contains("@")) { showMessage("Önce e-posta adresini yaz."); return; }
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) showMessage("Şifre yenileme bağlantısı gönderildi.");
            else showMessage(authError(task.getException()));
        });
    }

    private void startGoogleSignIn() {
        try {
            if (credentialManager == null) credentialManager = CredentialManager.create(this);
            GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder().addCredentialOption(googleOption).build();
            credentialManager.getCredentialAsync(this, request, new CancellationSignal(), mainExecutor,
                    new CredentialManagerCallback<GetCredentialResponse, GetCredentialException>() {
                        @Override public void onResult(GetCredentialResponse result) { handleGoogleCredential(result.getCredential()); }
                        @Override public void onError(@NonNull GetCredentialException e) { showMessage("Google hesabı seçilemedi. Lütfen tekrar dene."); }
                    });
        } catch (Exception e) {
            showMessage("Google girişi başlatılamadı.");
        }
    }

    private void handleGoogleCredential(Credential credential) {
        try {
            if (!(credential instanceof CustomCredential)) { showMessage("Google kimlik bilgisi alınamadı."); return; }
            CustomCredential customCredential = (CustomCredential) credential;
            if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) { showMessage("Beklenmeyen Google kimlik bilgisi türü."); return; }
            GoogleIdTokenCredential googleCredential = GoogleIdTokenCredential.createFrom(customCredential.getData());
            AuthCredential firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);
            auth.signInWithCredential(firebaseCredential).addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) ensureWorkspaceAndOpen();
                else showMessage(authError(task.getException()));
            });
        } catch (Exception e) {
            showMessage("Google hesabı doğrulanamadı. Lütfen tekrar dene.");
        }
    }

    private void ensureWorkspaceAndOpen() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { showAuthScreen(); return; }
        db.collection("users").document(user.getUid()).get().addOnSuccessListener(profile -> {
            String orgId = profile.getString("activeOrgId");
            if (orgId != null && !orgId.isEmpty()) loadMembershipAndOpen(orgId);
            else createOwnerWorkspace(user);
        }).addOnFailureListener(e -> { showAuthScreen(); showMessage(firestoreError(e)); });
    }

    private void createOwnerWorkspace(FirebaseUser user) {
        String orgId = user.getUid();
        DocumentReference orgRef = db.collection("orgs").document(orgId);
        Map<String, Object> org = new HashMap<>();
        org.put("name", "Yap! Ekip");
        org.put("ownerUid", user.getUid());
        org.put("createdAt", FieldValue.serverTimestamp());
        orgRef.set(org, SetOptions.merge()).addOnCompleteListener(first -> {
            if (!first.isSuccessful()) { showAuthScreen(); showMessage(firestoreError(first.getException())); return; }
            Map<String, Object> member = new HashMap<>();
            member.put("uid", user.getUid());
            member.put("email", safe(user.getEmail()).toLowerCase(Locale.ROOT));
            member.put("name", displayName(user));
            member.put("photo", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
            member.put("level", "owner");
            member.put("role", "Yönetici");
            member.put("joinedAt", FieldValue.serverTimestamp());
            orgRef.collection("members").document(user.getUid()).set(member, SetOptions.merge()).addOnCompleteListener(second -> {
                if (!second.isSuccessful()) { showAuthScreen(); showMessage(firestoreError(second.getException())); return; }
                Map<String, Object> profile = new HashMap<>();
                profile.put("uid", user.getUid());
                profile.put("email", safe(user.getEmail()).toLowerCase(Locale.ROOT));
                profile.put("name", displayName(user));
                profile.put("activeOrgId", orgId);
                profile.put("updatedAt", FieldValue.serverTimestamp());
                db.collection("users").document(user.getUid()).set(profile, SetOptions.merge());
                Map<String, Object> role = new HashMap<>();
                role.put("name", "Çalışan");
                role.put("createdAt", FieldValue.serverTimestamp());
                orgRef.collection("roles").document("calisan").set(role, SetOptions.merge());
                currentOrgId = orgId;
                currentLevel = "owner";
                currentRole = "Yönetici";
                showApp();
            });
        });
    }

    private void loadMembershipAndOpen(String orgId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { showAuthScreen(); return; }
        db.collection("orgs").document(orgId).collection("members").document(user.getUid()).get().addOnSuccessListener(member -> {
            if (!member.exists()) { createOwnerWorkspace(user); return; }
            currentOrgId = orgId;
            currentLevel = safe(member.getString("level"));
            currentRole = safe(member.getString("role"));
            showApp();
        }).addOnFailureListener(e -> { showAuthScreen(); showMessage(firestoreError(e)); });
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void showApp() {
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new CompanyBridge(), "YapNative");
        web.setBackgroundColor(BG);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) { return handleAppUrl(request.getUrl()); }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) { return handleAppUrl(Uri.parse(url)); }
            @Override public void onPageFinished(WebView view, String url) { injectCurrentUser(); }
        });
        applySystemInsets(web);
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private boolean handleAppUrl(Uri uri) {
        if (!"yap".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if ("login".equalsIgnoreCase(host)) { showAuthScreen(); return true; }
        if ("logout".equalsIgnoreCase(host)) { signOut(); return true; }
        return true;
    }

    private void signOut() {
        auth.signOut();
        currentOrgId = null;
        currentLevel = null;
        currentRole = null;
        if (credentialManager == null) credentialManager = CredentialManager.create(this);
        credentialManager.clearCredentialStateAsync(new ClearCredentialStateRequest(), new CancellationSignal(), mainExecutor,
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) { showAuthScreen(); }
                    @Override public void onError(@NonNull ClearCredentialException e) { showAuthScreen(); }
                });
    }

    private void injectCurrentUser() {
        FirebaseUser user = auth.getCurrentUser();
        JSONObject data = new JSONObject();
        try {
            data.put("loggedIn", user != null);
            data.put("uid", user == null ? "" : user.getUid());
            data.put("email", user != null && user.getEmail() != null ? user.getEmail() : "");
            data.put("name", user == null ? "" : displayName(user));
            data.put("photo", user != null && user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
            data.put("orgId", safe(currentOrgId));
            data.put("level", safe(currentLevel));
            data.put("role", safe(currentRole));
        } catch (Exception ignored) {}
        runJs("window.setNativeUser&&window.setNativeUser(" + data + ")");
    }

    public class CompanyBridge {
        @JavascriptInterface public void loadState() { runOnUiThread(MainActivity.this::loadCompanyState); }
        @JavascriptInterface public void createRole(String name) { runOnUiThread(() -> createRoleNative(name)); }
        @JavascriptInterface public void invite(String email, String role) { runOnUiThread(() -> sendInviteNative(email, role)); }
        @JavascriptInterface public void saveTask(String json) { runOnUiThread(() -> saveTaskNative(json)); }
        @JavascriptInterface public void deleteTask(String taskId) { runOnUiThread(() -> deleteTaskNative(taskId)); }
        @JavascriptInterface public void updateMemberRole(String uid, String role) { runOnUiThread(() -> updateMemberRoleNative(uid, role)); }
        @JavascriptInterface public void loadMessages(String taskId) { runOnUiThread(() -> loadMessagesNative(taskId)); }
        @JavascriptInterface public void sendMessage(String taskId, String text) { runOnUiThread(() -> sendMessageNative(taskId, text)); }
        @JavascriptInterface public void logout() { runOnUiThread(MainActivity.this::signOut); }
    }

    private boolean isManager() { return "owner".equals(currentLevel) || "admin".equals(currentLevel); }

    private void loadCompanyState() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || currentOrgId == null) { nativeNotice("error", "Oturum bilgisi bulunamadı."); return; }
        DocumentReference orgRef = db.collection("orgs").document(currentOrgId);
        Task<DocumentSnapshot> orgTask = orgRef.get();
        Task<QuerySnapshot> taskTask = isManager() ? orgRef.collection("tasks").get() : orgRef.collection("tasks").whereEqualTo("assigneeUid", user.getUid()).get();
        Task<QuerySnapshot> memberTask = isManager() ? orgRef.collection("members").get() : Tasks.forResult(null);
        Task<QuerySnapshot> roleTask = isManager() ? orgRef.collection("roles").get() : Tasks.forResult(null);
        Task<QuerySnapshot> inviteTask = isManager() ? db.collection("invites").whereEqualTo("orgId", currentOrgId).get() : Tasks.forResult(null);

        Tasks.whenAllComplete(orgTask, taskTask, memberTask, roleTask, inviteTask).addOnCompleteListener(done -> {
            if (!orgTask.isSuccessful() || !taskTask.isSuccessful()) {
                nativeNotice("error", firestoreError(!orgTask.isSuccessful() ? orgTask.getException() : taskTask.getException()));
                return;
            }
            JSONObject state = new JSONObject();
            try {
                state.put("orgName", safe(orgTask.getResult().getString("name")));
                state.put("isManager", isManager());
                state.put("level", safe(currentLevel));
                state.put("role", safe(currentRole));
                JSONArray tasks = new JSONArray();
                for (DocumentSnapshot doc : taskTask.getResult().getDocuments()) tasks.put(taskJson(doc));
                state.put("tasks", tasks);
                JSONArray members = new JSONArray();
                if (memberTask.isSuccessful() && memberTask.getResult() != null) for (DocumentSnapshot doc : memberTask.getResult().getDocuments()) members.put(memberJson(doc));
                state.put("members", members);
                JSONArray roles = new JSONArray();
                if (roleTask.isSuccessful() && roleTask.getResult() != null) for (DocumentSnapshot doc : roleTask.getResult().getDocuments()) roles.put(roleJson(doc));
                state.put("roles", roles);
                JSONArray invites = new JSONArray();
                if (inviteTask.isSuccessful() && inviteTask.getResult() != null) for (DocumentSnapshot doc : inviteTask.getResult().getDocuments()) if ("pending".equals(doc.getString("status"))) invites.put(inviteJson(doc));
                state.put("invites", invites);
            } catch (Exception ignored) {}
            runJs("window.onCompanyState&&window.onCompanyState(" + state + ")");
        });
    }

    private void createRoleNative(String name) {
        if (!isManager()) { nativeNotice("error", "Bu işlem için yönetici yetkisi gerekiyor."); return; }
        String clean = safe(name).trim();
        if (clean.length() < 2) { nativeNotice("error", "Rol adını yaz."); return; }
        Map<String, Object> role = new HashMap<>();
        role.put("name", clean);
        role.put("createdAt", FieldValue.serverTimestamp());
        db.collection("orgs").document(currentOrgId).collection("roles").add(role)
                .addOnSuccessListener(r -> { nativeNotice("success", "Rol eklendi."); loadCompanyState(); })
                .addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private void sendInviteNative(String email, String role) {
        if (!isManager()) { nativeNotice("error", "Bu işlem için yönetici yetkisi gerekiyor."); return; }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        String normalized = safe(email).trim().toLowerCase(Locale.ROOT);
        String roleName = safe(role).trim();
        if (!normalized.contains("@")) { nativeNotice("error", "Geçerli bir e-posta adresi yaz."); return; }
        if (roleName.isEmpty()) roleName = "Çalışan";
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> invite = new HashMap<>();
        invite.put("orgId", currentOrgId);
        invite.put("email", normalized);
        invite.put("role", roleName);
        invite.put("status", "pending");
        invite.put("createdBy", user.getUid());
        invite.put("createdAt", FieldValue.serverTimestamp());
        DocumentReference inviteRef = db.collection("invites").document(token);
        final String finalRoleName = roleName;
        inviteRef.set(invite).addOnSuccessListener(v -> {
            String continueUrl = "https://yap-todo-alpcan.firebaseapp.com/invite?token=" + Uri.encode(token);
            ActionCodeSettings settings = ActionCodeSettings.newBuilder()
                    .setUrl(continueUrl)
                    .setHandleCodeInApp(true)
                    .setAndroidPackageName(getPackageName(), false, null)
                    .build();
            auth.sendSignInLinkToEmail(normalized, settings).addOnCompleteListener(sent -> {
                if (sent.isSuccessful()) {
                    nativeNotice("success", normalized + " adresine " + finalRoleName + " rolüyle davet gönderildi.");
                    loadCompanyState();
                } else {
                    inviteRef.delete();
                    String msg = authError(sent.getException());
                    if (sent.getException() != null && safe(sent.getException().getMessage()).toUpperCase(Locale.ROOT).contains("OPERATION_NOT_ALLOWED")) msg = "Firebase Authentication'da E-posta bağlantısı ile giriş özelliğini etkinleştir.";
                    nativeNotice("error", msg);
                }
            });
        }).addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private void saveTaskNative(String json) {
        if (!isManager()) { nativeNotice("error", "Çalışanlar görev düzenleyemez."); return; }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        try {
            JSONObject j = new JSONObject(json);
            String id = j.optString("id", "").trim();
            String title = j.optString("title", "").trim();
            if (title.isEmpty()) { nativeNotice("error", "Görev başlığını yaz."); return; }
            DocumentReference ref = id.isEmpty() ? db.collection("orgs").document(currentOrgId).collection("tasks").document() : db.collection("orgs").document(currentOrgId).collection("tasks").document(id);
            Map<String, Object> task = new HashMap<>();
            task.put("title", title);
            task.put("note", j.optString("note", ""));
            task.put("priority", j.optString("priority", "normal"));
            task.put("start", j.optString("start", ""));
            task.put("end", j.optString("end", ""));
            task.put("assigneeUid", j.optString("assigneeUid", ""));
            task.put("assigneeName", j.optString("assigneeName", ""));
            task.put("assigneeEmail", j.optString("assigneeEmail", ""));
            task.put("updatedAt", FieldValue.serverTimestamp());
            task.put("updatedBy", user.getUid());
            if (id.isEmpty()) { task.put("createdAt", FieldValue.serverTimestamp()); task.put("createdBy", user.getUid()); }
            ref.set(task, SetOptions.merge()).addOnSuccessListener(v -> { nativeNotice("success", id.isEmpty() ? "Görev oluşturuldu." : "Görev güncellendi."); loadCompanyState(); }).addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
        } catch (Exception e) {
            nativeNotice("error", "Görev verisi okunamadı.");
        }
    }

    private void deleteTaskNative(String taskId) {
        if (!isManager()) { nativeNotice("error", "Çalışanlar görev silemez."); return; }
        if (safe(taskId).isEmpty()) return;
        db.collection("orgs").document(currentOrgId).collection("tasks").document(taskId).delete().addOnSuccessListener(v -> { nativeNotice("success", "Görev silindi."); loadCompanyState(); }).addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private void updateMemberRoleNative(String uid, String role) {
        if (!isManager()) { nativeNotice("error", "Bu işlem için yönetici yetkisi gerekiyor."); return; }
        if (safe(uid).isEmpty() || safe(role).isEmpty()) return;
        Map<String, Object> update = new HashMap<>();
        update.put("role", role.trim());
        update.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("orgs").document(currentOrgId).collection("members").document(uid).set(update, SetOptions.merge()).addOnSuccessListener(v -> { nativeNotice("success", "Rol güncellendi."); loadCompanyState(); }).addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private void loadMessagesNative(String taskId) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || safe(taskId).isEmpty()) return;
        DocumentReference taskRef = db.collection("orgs").document(currentOrgId).collection("tasks").document(taskId);
        taskRef.get().addOnSuccessListener(task -> {
            if (!task.exists()) return;
            String assigneeUid = safe(task.getString("assigneeUid"));
            if (!isManager() && !user.getUid().equals(assigneeUid)) { nativeNotice("error", "Bu görevin konuşmasına erişemezsin."); return; }
            taskRef.collection("messages").orderBy("createdAt", Query.Direction.ASCENDING).get().addOnSuccessListener(snap -> {
                JSONArray arr = new JSONArray();
                for (DocumentSnapshot doc : snap.getDocuments()) arr.put(messageJson(doc));
                runJs("window.onMessages&&window.onMessages(" + JSONObject.quote(taskId) + "," + arr + ")");
            }).addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
        }).addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private void sendMessageNative(String taskId, String text) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || safe(taskId).isEmpty()) return;
        String clean = safe(text).trim();
        if (clean.isEmpty()) return;
        DocumentReference taskRef = db.collection("orgs").document(currentOrgId).collection("tasks").document(taskId);
        taskRef.get().addOnSuccessListener(task -> {
            if (!task.exists()) return;
            String assigneeUid = safe(task.getString("assigneeUid"));
            if (!isManager() && !user.getUid().equals(assigneeUid)) { nativeNotice("error", "Bu görevin konuşmasına mesaj gönderemezsin."); return; }
            Map<String, Object> msg = new HashMap<>();
            msg.put("senderUid", user.getUid());
            msg.put("senderName", displayName(user));
            msg.put("senderLevel", isManager() ? "manager" : "member");
            msg.put("text", clean);
            msg.put("createdAt", FieldValue.serverTimestamp());
            taskRef.collection("messages").add(msg).addOnSuccessListener(v -> loadMessagesNative(taskId)).addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
        }).addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private JSONObject taskJson(DocumentSnapshot doc) {
        JSONObject j = new JSONObject();
        try {
            j.put("id", doc.getId()); j.put("title", safe(doc.getString("title"))); j.put("note", safe(doc.getString("note"))); j.put("priority", safe(doc.getString("priority"))); j.put("start", safe(doc.getString("start"))); j.put("end", safe(doc.getString("end"))); j.put("assigneeUid", safe(doc.getString("assigneeUid"))); j.put("assigneeName", safe(doc.getString("assigneeName"))); j.put("assigneeEmail", safe(doc.getString("assigneeEmail")));
        } catch (Exception ignored) {}
        return j;
    }

    private JSONObject memberJson(DocumentSnapshot doc) {
        JSONObject j = new JSONObject();
        try { j.put("uid", doc.getId()); j.put("name", safe(doc.getString("name"))); j.put("email", safe(doc.getString("email"))); j.put("role", safe(doc.getString("role"))); j.put("level", safe(doc.getString("level"))); j.put("photo", safe(doc.getString("photo"))); } catch (Exception ignored) {}
        return j;
    }

    private JSONObject roleJson(DocumentSnapshot doc) {
        JSONObject j = new JSONObject();
        try { j.put("id", doc.getId()); j.put("name", safe(doc.getString("name"))); } catch (Exception ignored) {}
        return j;
    }

    private JSONObject inviteJson(DocumentSnapshot doc) {
        JSONObject j = new JSONObject();
        try { j.put("id", doc.getId()); j.put("email", safe(doc.getString("email"))); j.put("role", safe(doc.getString("role"))); j.put("status", safe(doc.getString("status"))); } catch (Exception ignored) {}
        return j;
    }

    private JSONObject messageJson(DocumentSnapshot doc) {
        JSONObject j = new JSONObject();
        try {
            j.put("id", doc.getId()); j.put("senderUid", safe(doc.getString("senderUid"))); j.put("senderName", safe(doc.getString("senderName"))); j.put("senderLevel", safe(doc.getString("senderLevel"))); j.put("text", safe(doc.getString("text")));
            Timestamp ts = doc.getTimestamp("createdAt");
            j.put("createdAt", ts == null ? 0 : ts.toDate().getTime());
        } catch (Exception ignored) {}
        return j;
    }

    private void nativeNotice(String type, String message) {
        JSONObject j = new JSONObject();
        try { j.put("type", type); j.put("message", message); } catch (Exception ignored) {}
        runJs("window.onNativeNotice&&window.onNativeNotice(" + j + ")");
    }

    private void runJs(String js) {
        if (web == null) return;
        web.post(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }

    private void showMessage(String text) {
        if (authMessage == null) return;
        authMessage.setText(text);
        authMessage.setVisibility(View.VISIBLE);
    }

    private String displayName(FirebaseUser user) {
        if (user == null) return "Kullanıcı";
        if (user.getDisplayName() != null && !user.getDisplayName().trim().isEmpty()) return user.getDisplayName().trim();
        String email = safe(user.getEmail());
        if (email.contains("@")) return email.substring(0, email.indexOf('@'));
        return "Kullanıcı";
    }

    private String authError(Exception error) {
        String text = error == null || error.getMessage() == null ? "" : error.getMessage().toUpperCase(Locale.ROOT);
        if (text.contains("ALREADY") && text.contains("EMAIL")) return "Bu e-posta adresi zaten kayıtlı.";
        if (text.contains("INVALID") && (text.contains("CREDENTIAL") || text.contains("PASSWORD"))) return "E-posta veya şifre hatalı.";
        if (text.contains("WEAK") && text.contains("PASSWORD")) return "Şifre en az 6 karakter olmalı.";
        if (text.contains("NETWORK") || text.contains("TIMEOUT")) return "İnternet bağlantısı kurulamadı.";
        return "İşlem tamamlanamadı. Lütfen tekrar dene.";
    }

    private String firestoreError(Exception error) {
        String text = error == null || error.getMessage() == null ? "" : error.getMessage();
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.contains("PERMISSION_DENIED")) return "Firestore yetkisi reddedildi. Firestore kurallarını yayımla.";
        if (upper.contains("NOT_FOUND") || upper.contains("DATABASE")) return "Cloud Firestore veritabanını Firebase Console'dan oluştur.";
        if (upper.contains("NETWORK") || upper.contains("UNAVAILABLE")) return "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.";
        return text.isEmpty() ? "Şirket verileri alınamadı." : text;
    }

    private String safe(String value) { return value == null ? "" : value; }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint); e.setHintTextColor(MUTED); e.setTextColor(Color.WHITE); e.setTextSize(15); e.setSingleLine(true); e.setPadding(dp(14), dp(12), dp(14), dp(12)); e.setBackground(rounded(Color.rgb(21, 19, 31), dp(14))); return e;
    }

    private Button primaryButton(String text) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setTextSize(15); b.setBackground(rounded(PURPLE, dp(14))); return b;
    }

    private Button secondaryButton(String text) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.WHITE); b.setAllCaps(false); b.setTextSize(15); b.setBackground(rounded(Color.rgb(39, 36, 58), dp(14))); return b;
    }

    private Button linkButton(String text) {
        Button b = new Button(this); b.setText(text); b.setTextColor(Color.rgb(189, 156, 255)); b.setAllCaps(false); b.setBackgroundColor(Color.TRANSPARENT); return b;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(radius); return d;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT); p.setMargins(0, 0, 0, bottomMargin); return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private void configureSystemBars() {
        Window window = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false);
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) controller.setSystemBarsAppearance(0, 0);
        } else {
            window.setStatusBarColor(BG);
            window.setNavigationBarColor(BG);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    private void applySystemInsets(WebView target) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return;
        target.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
