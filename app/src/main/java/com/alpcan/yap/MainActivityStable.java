package com.alpcan.yap;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
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

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInClient;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
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

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class MainActivityStable extends Activity {
    private static final int BG = Color.rgb(17, 16, 26);
    private static final int CARD = Color.rgb(29, 27, 42);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int MUTED = Color.rgb(170, 165, 189);
    private static final int GOOGLE_REQUEST = 9401;

    private FirebaseAuth auth;
    private FirebaseFirestore db;
    private GoogleSignInClient googleClient;
    private WebView web;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView authMessage;

    private String currentOrgId;
    private String currentLevel;
    private String currentRole;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        try {
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
            if (auth.getCurrentUser() == null) showAuthScreen();
            else ensureWorkspaceAndOpen();
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    private void showAuthScreen() {
        try {
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
            root.setPadding(dp(24), dp(56), dp(24), dp(30));
            scroll.addView(root, new ScrollView.LayoutParams(ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));

            TextView logo = new TextView(this);
            logo.setText("Yap!");
            logo.setTextColor(Color.WHITE);
            logo.setTextSize(42);
            logo.setGravity(Gravity.CENTER);
            logo.setTypeface(null, android.graphics.Typeface.BOLD);
            root.addView(logo, matchWrap(dp(6)));

            TextView lead = new TextView(this);
            lead.setText("Ekibinle işleri tek yerde yönet.");
            lead.setTextColor(MUTED);
            lead.setTextSize(15);
            lead.setGravity(Gravity.CENTER);
            root.addView(lead, matchWrap(dp(24)));

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(18), dp(18), dp(18), dp(18));
            card.setBackground(rounded(CARD, dp(24)));
            root.addView(card, matchWrap(dp(12)));

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
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    private void emailLogin(boolean register) {
        if (auth == null || emailInput == null || passwordInput == null) return;
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
        if (auth == null || emailInput == null) return;
        String email = emailInput.getText().toString().trim();
        if (!email.contains("@")) { showMessage("Önce e-posta adresini yaz."); return; }
        showMessage("Bağlantı gönderiliyor...");
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) showMessage("Şifre yenileme bağlantısı gönderildi.");
            else showMessage(authError(task.getException()));
        });
    }

    private void startGoogleSignIn() {
        try {
            if (googleClient == null) {
                GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                        .requestIdToken(getString(R.string.default_web_client_id))
                        .requestEmail()
                        .build();
                googleClient = GoogleSignIn.getClient(this, gso);
            }
            showMessage("Google hesabı açılıyor...");
            startActivityForResult(googleClient.getSignInIntent(), GOOGLE_REQUEST);
        } catch (Throwable t) {
            showMessage("Google girişi başlatılamadı: " + simpleMessage(t));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != GOOGLE_REQUEST) return;
        try {
            Task<GoogleSignInAccount> task = GoogleSignIn.getSignedInAccountFromIntent(data);
            GoogleSignInAccount account = task.getResult(ApiException.class);
            if (account == null || account.getIdToken() == null) {
                showMessage("Google hesabından kimlik bilgisi alınamadı.");
                return;
            }
            AuthCredential credential = GoogleAuthProvider.getCredential(account.getIdToken(), null);
            auth.signInWithCredential(credential).addOnCompleteListener(this, firebaseTask -> {
                if (firebaseTask.isSuccessful()) ensureWorkspaceAndOpen();
                else showMessage(authError(firebaseTask.getException()));
            });
        } catch (ApiException e) {
            showMessage("Google girişi tamamlanamadı. Kod: " + e.getStatusCode());
        } catch (Throwable t) {
            showMessage("Google hesabı doğrulanamadı: " + simpleMessage(t));
        }
    }

    private void ensureWorkspaceAndOpen() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        if (user == null) { showAuthScreen(); return; }
        showLoading("Şirket alanın hazırlanıyor...");
        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(profile -> {
                    String orgId = profile.getString("activeOrgId");
                    if (orgId == null || orgId.trim().isEmpty()) createOwnerWorkspace(user);
                    else loadMembershipAndOpen(orgId);
                })
                .addOnFailureListener(e -> showBackendError(e));
    }

    private void createOwnerWorkspace(FirebaseUser user) {
        String orgId = user.getUid();
        DocumentReference orgRef = db.collection("orgs").document(orgId);
        Map<String, Object> org = new HashMap<>();
        org.put("name", "Yap! Ekip");
        org.put("ownerUid", user.getUid());
        org.put("createdAt", FieldValue.serverTimestamp());
        orgRef.set(org, SetOptions.merge()).addOnCompleteListener(first -> {
            if (!first.isSuccessful()) { showBackendError(first.getException()); return; }
            Map<String, Object> member = new HashMap<>();
            member.put("uid", user.getUid());
            member.put("email", safe(user.getEmail()).toLowerCase(Locale.ROOT));
            member.put("name", displayName(user));
            member.put("photo", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
            member.put("level", "owner");
            member.put("role", "Yönetici");
            member.put("joinedAt", FieldValue.serverTimestamp());
            orgRef.collection("members").document(user.getUid()).set(member, SetOptions.merge()).addOnCompleteListener(second -> {
                if (!second.isSuccessful()) { showBackendError(second.getException()); return; }
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
        db.collection("orgs").document(orgId).collection("members").document(user.getUid()).get()
                .addOnSuccessListener(member -> {
                    if (!member.exists()) {
                        if (orgId.equals(user.getUid())) createOwnerWorkspace(user);
                        else showBackendMessage("Bu hesaba ait şirket üyeliği bulunamadı.");
                        return;
                    }
                    currentOrgId = orgId;
                    currentLevel = safe(member.getString("level"));
                    currentRole = safe(member.getString("role"));
                    showApp();
                })
                .addOnFailureListener(this::showBackendError);
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void showApp() {
        try {
            web = new WebView(this);
            WebSettings settings = web.getSettings();
            settings.setJavaScriptEnabled(true);
            settings.setDomStorageEnabled(true);
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
            setContentView(web);
            web.loadUrl("file:///android_asset/index.html");
        } catch (Throwable t) {
            showFatal(t);
        }
    }

    private boolean handleAppUrl(Uri uri) {
        if (uri == null || !"yap".equalsIgnoreCase(uri.getScheme())) return false;
        if ("logout".equalsIgnoreCase(uri.getHost())) { signOut(); return true; }
        return true;
    }

    private void injectCurrentUser() {
        FirebaseUser user = auth == null ? null : auth.getCurrentUser();
        JSONObject data = new JSONObject();
        try {
            data.put("loggedIn", user != null);
            data.put("uid", user == null ? "" : user.getUid());
            data.put("email", user == null ? "" : safe(user.getEmail()));
            data.put("name", user == null ? "" : displayName(user));
            data.put("photo", user != null && user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
            data.put("orgId", safe(currentOrgId));
            data.put("level", safe(currentLevel));
            data.put("role", safe(currentRole));
        } catch (Exception ignored) {}
        runJs("window.setNativeUser&&window.setNativeUser(" + data + ")");
    }

    public class CompanyBridge {
        @JavascriptInterface public void loadState() { runOnUiThread(MainActivityStable.this::loadCompanyState); }
        @JavascriptInterface public void createRole(String name) { runOnUiThread(() -> createRoleNative(name)); }
        @JavascriptInterface public void invite(String email, String role) { runOnUiThread(() -> sendInviteNative(email, role)); }
        @JavascriptInterface public void saveTask(String json) { runOnUiThread(() -> saveTaskNative(json)); }
        @JavascriptInterface public void deleteTask(String taskId) { runOnUiThread(() -> deleteTaskNative(taskId)); }
        @JavascriptInterface public void updateMemberRole(String uid, String role) { runOnUiThread(() -> updateMemberRoleNative(uid, role)); }
        @JavascriptInterface public void loadMessages(String taskId) { runOnUiThread(() -> loadMessagesNative(taskId)); }
        @JavascriptInterface public void sendMessage(String taskId, String text) { runOnUiThread(() -> sendMessageNative(taskId, text)); }
        @JavascriptInterface public void logout() { runOnUiThread(MainActivityStable.this::signOut); }
    }

    private boolean isManager() { return "owner".equals(currentLevel) || "admin".equals(currentLevel); }

    private void loadCompanyState() {
        try {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null || currentOrgId == null) { nativeNotice("error", "Oturum bilgisi bulunamadı."); return; }
            DocumentReference orgRef = db.collection("orgs").document(currentOrgId);
            Task<DocumentSnapshot> orgTask = orgRef.get();
            Task<QuerySnapshot> taskTask = isManager() ? orgRef.collection("tasks").get() : orgRef.collection("tasks").whereEqualTo("assigneeUid", user.getUid()).get();
            Task<QuerySnapshot> memberTask = isManager() ? orgRef.collection("members").get() : Tasks.forResult((QuerySnapshot) null);
            Task<QuerySnapshot> roleTask = isManager() ? orgRef.collection("roles").get() : Tasks.forResult((QuerySnapshot) null);
            Task<QuerySnapshot> inviteTask = isManager() ? db.collection("invites").whereEqualTo("orgId", currentOrgId).get() : Tasks.forResult((QuerySnapshot) null);
            Tasks.whenAllComplete(orgTask, taskTask, memberTask, roleTask, inviteTask).addOnCompleteListener(done -> {
                if (!orgTask.isSuccessful() || !taskTask.isSuccessful()) {
                    nativeNotice("error", firestoreError(!orgTask.isSuccessful() ? orgTask.getException() : taskTask.getException()));
                    return;
                }
                JSONObject state = new JSONObject();
                try {
                    DocumentSnapshot org = orgTask.getResult();
                    state.put("orgName", org == null ? "Yap! Ekip" : safe(org.getString("name")));
                    state.put("isManager", isManager());
                    state.put("level", safe(currentLevel));
                    state.put("role", safe(currentRole));
                    JSONArray tasks = new JSONArray();
                    QuerySnapshot tq = taskTask.getResult();
                    if (tq != null) for (DocumentSnapshot doc : tq.getDocuments()) tasks.put(taskJson(doc));
                    state.put("tasks", tasks);
                    JSONArray members = new JSONArray();
                    if (memberTask.isSuccessful() && memberTask.getResult() != null) for (DocumentSnapshot doc : memberTask.getResult().getDocuments()) members.put(memberJson(doc));
                    state.put("members", members);
                    JSONArray roles = new JSONArray();
                    if (roleTask.isSuccessful() && roleTask.getResult() != null) for (DocumentSnapshot doc : roleTask.getResult().getDocuments()) roles.put(roleJson(doc));
                    state.put("roles", roles);
                    JSONArray invites = new JSONArray();
                    if (inviteTask.isSuccessful() && inviteTask.getResult() != null) {
                        for (DocumentSnapshot doc : inviteTask.getResult().getDocuments()) if ("pending".equals(safe(doc.getString("status")))) invites.put(inviteJson(doc));
                    }
                    state.put("invites", invites);
                } catch (Exception e) {
                    nativeNotice("error", "Şirket verileri hazırlanamadı.");
                    return;
                }
                runJs("window.onCompanyState&&window.onCompanyState(" + state + ")");
            });
        } catch (Throwable t) {
            nativeNotice("error", "Şirket verileri yüklenemedi: " + simpleMessage(t));
        }
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

    private void sendInviteNative(String email, String roleName) {
        if (!isManager()) { nativeNotice("error", "Bu işlem için yönetici yetkisi gerekiyor."); return; }
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        String normalized = safe(email).trim().toLowerCase(Locale.ROOT);
        String role = safe(roleName).trim();
        if (!normalized.contains("@")) { nativeNotice("error", "Geçerli bir e-posta adresi yaz."); return; }
        if (role.isEmpty()) role = "Çalışan";
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        Map<String, Object> invite = new HashMap<>();
        invite.put("orgId", currentOrgId);
        invite.put("email", normalized);
        invite.put("role", role);
        invite.put("status", "pending");
        invite.put("createdBy", user.getUid());
        invite.put("createdAt", FieldValue.serverTimestamp());
        DocumentReference inviteRef = db.collection("invites").document(token);
        String finalRole = role;
        inviteRef.set(invite).addOnSuccessListener(v -> {
            String continueUrl = "https://yap-todo-alpcan.firebaseapp.com/invite?token=" + Uri.encode(token);
            ActionCodeSettings settings = ActionCodeSettings.newBuilder()
                    .setUrl(continueUrl)
                    .setHandleCodeInApp(true)
                    .setAndroidPackageName(getPackageName(), false, null)
                    .build();
            auth.sendSignInLinkToEmail(normalized, settings).addOnCompleteListener(sent -> {
                if (sent.isSuccessful()) {
                    nativeNotice("success", normalized + " adresine " + finalRole + " rolüyle davet gönderildi.");
                    loadCompanyState();
                } else {
                    inviteRef.delete();
                    nativeNotice("error", authError(sent.getException()));
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
            Map<String, Object> data = new HashMap<>();
            data.put("title", title);
            data.put("note", j.optString("note", ""));
            data.put("priority", j.optString("priority", "normal"));
            data.put("start", j.optString("start", ""));
            data.put("end", j.optString("end", ""));
            data.put("assigneeUid", j.optString("assigneeUid", ""));
            data.put("assigneeName", j.optString("assigneeName", ""));
            data.put("assigneeEmail", j.optString("assigneeEmail", ""));
            data.put("updatedAt", FieldValue.serverTimestamp());
            DocumentReference tasks = db.collection("orgs").document(currentOrgId).collection("tasks").document(id.isEmpty() ? UUID.randomUUID().toString() : id);
            if (id.isEmpty()) {
                data.put("createdBy", user.getUid());
                data.put("createdAt", FieldValue.serverTimestamp());
            }
            tasks.set(data, SetOptions.merge())
                    .addOnSuccessListener(v -> { nativeNotice("success", id.isEmpty() ? "Görev eklendi." : "Görev güncellendi."); loadCompanyState(); })
                    .addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
        } catch (Exception e) {
            nativeNotice("error", "Görev kaydedilemedi.");
        }
    }

    private void deleteTaskNative(String taskId) {
        if (!isManager()) { nativeNotice("error", "Bu işlem için yönetici yetkisi gerekiyor."); return; }
        String id = safe(taskId).trim();
        if (id.isEmpty()) return;
        db.collection("orgs").document(currentOrgId).collection("tasks").document(id).delete()
                .addOnSuccessListener(v -> { nativeNotice("success", "Görev silindi."); loadCompanyState(); })
                .addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private void updateMemberRoleNative(String uid, String role) {
        if (!isManager()) { nativeNotice("error", "Bu işlem için yönetici yetkisi gerekiyor."); return; }
        String memberUid = safe(uid).trim();
        String roleName = safe(role).trim();
        if (memberUid.isEmpty() || roleName.isEmpty()) return;
        Map<String, Object> patch = new HashMap<>();
        patch.put("role", roleName);
        patch.put("updatedAt", FieldValue.serverTimestamp());
        db.collection("orgs").document(currentOrgId).collection("members").document(memberUid).set(patch, SetOptions.merge())
                .addOnSuccessListener(v -> { nativeNotice("success", "Rol güncellendi."); loadCompanyState(); })
                .addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private void loadMessagesNative(String taskId) {
        String id = safe(taskId).trim();
        if (id.isEmpty()) return;
        db.collection("orgs").document(currentOrgId).collection("tasks").document(id).collection("messages")
                .orderBy("createdAt", Query.Direction.ASCENDING).get()
                .addOnSuccessListener(q -> {
                    JSONArray arr = new JSONArray();
                    for (DocumentSnapshot d : q.getDocuments()) arr.put(messageJson(d));
                    runJs("window.onMessages&&window.onMessages(" + JSONObject.quote(id) + "," + arr + ")");
                })
                .addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private void sendMessageNative(String taskId, String text) {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) return;
        String id = safe(taskId).trim();
        String clean = safe(text).trim();
        if (id.isEmpty() || clean.isEmpty()) return;
        Map<String, Object> msg = new HashMap<>();
        msg.put("text", clean);
        msg.put("senderUid", user.getUid());
        msg.put("senderName", displayName(user));
        msg.put("senderLevel", isManager() ? "manager" : "member");
        msg.put("createdAt", FieldValue.serverTimestamp());
        db.collection("orgs").document(currentOrgId).collection("tasks").document(id).collection("messages").add(msg)
                .addOnSuccessListener(v -> loadMessagesNative(id))
                .addOnFailureListener(e -> nativeNotice("error", firestoreError(e)));
    }

    private JSONObject taskJson(DocumentSnapshot d) {
        JSONObject j = new JSONObject();
        try {
            j.put("id", d.getId());
            j.put("title", safe(d.getString("title")));
            j.put("note", safe(d.getString("note")));
            j.put("priority", safe(d.getString("priority")).isEmpty() ? "normal" : safe(d.getString("priority")));
            j.put("start", safe(d.getString("start")));
            j.put("end", safe(d.getString("end")));
            j.put("assigneeUid", safe(d.getString("assigneeUid")));
            j.put("assigneeName", safe(d.getString("assigneeName")));
            j.put("assigneeEmail", safe(d.getString("assigneeEmail")));
        } catch (Exception ignored) {}
        return j;
    }

    private JSONObject memberJson(DocumentSnapshot d) {
        JSONObject j = new JSONObject();
        try {
            j.put("uid", safe(d.getString("uid")).isEmpty() ? d.getId() : safe(d.getString("uid")));
            j.put("email", safe(d.getString("email")));
            j.put("name", safe(d.getString("name")));
            j.put("photo", safe(d.getString("photo")));
            j.put("level", safe(d.getString("level")));
            j.put("role", safe(d.getString("role")));
        } catch (Exception ignored) {}
        return j;
    }

    private JSONObject roleJson(DocumentSnapshot d) {
        JSONObject j = new JSONObject();
        try { j.put("id", d.getId()); j.put("name", safe(d.getString("name"))); } catch (Exception ignored) {}
        return j;
    }

    private JSONObject inviteJson(DocumentSnapshot d) {
        JSONObject j = new JSONObject();
        try { j.put("id", d.getId()); j.put("email", safe(d.getString("email"))); j.put("role", safe(d.getString("role"))); j.put("status", safe(d.getString("status"))); } catch (Exception ignored) {}
        return j;
    }

    private JSONObject messageJson(DocumentSnapshot d) {
        JSONObject j = new JSONObject();
        try {
            j.put("id", d.getId());
            j.put("text", safe(d.getString("text")));
            j.put("senderUid", safe(d.getString("senderUid")));
            j.put("senderName", safe(d.getString("senderName")));
            j.put("senderLevel", safe(d.getString("senderLevel")));
        } catch (Exception ignored) {}
        return j;
    }

    private void signOut() {
        try { if (auth != null) auth.signOut(); } catch (Exception ignored) {}
        try { if (googleClient != null) googleClient.signOut(); } catch (Exception ignored) {}
        currentOrgId = null;
        currentLevel = null;
        currentRole = null;
        showAuthScreen();
    }

    private void runJs(String js) {
        if (web == null) return;
        web.post(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }

    private void nativeNotice(String type, String message) {
        JSONObject n = new JSONObject();
        try { n.put("type", type); n.put("message", message); } catch (Exception ignored) {}
        runJs("window.onNativeNotice&&window.onNativeNotice(" + n + ")");
    }

    private void showLoading(String text) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setPadding(dp(28), dp(50), dp(28), dp(50));
        root.setBackgroundColor(BG);
        TextView logo = new TextView(this);
        logo.setText("Yap!");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(38);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(logo, matchWrap(dp(14)));
        TextView msg = new TextView(this);
        msg.setText(text);
        msg.setTextColor(MUTED);
        msg.setTextSize(15);
        msg.setGravity(Gravity.CENTER);
        root.addView(msg, matchWrap(0));
        setContentView(root);
    }

    private void showBackendError(Exception e) { showBackendMessage(firestoreError(e)); }

    private void showBackendMessage(String message) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(72), dp(24), dp(32));
        root.setBackgroundColor(BG);
        TextView logo = new TextView(this);
        logo.setText("Yap!");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(40);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(logo, matchWrap(dp(18)));
        TextView title = new TextView(this);
        title.setText("Bağlantı kurulamadı");
        title.setTextColor(Color.WHITE);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap(dp(10)));
        TextView info = new TextView(this);
        info.setText(message);
        info.setTextColor(MUTED);
        info.setTextSize(14);
        info.setGravity(Gravity.CENTER);
        root.addView(info, matchWrap(dp(18)));
        Button retry = primaryButton("Tekrar dene");
        retry.setOnClickListener(v -> ensureWorkspaceAndOpen());
        root.addView(retry, matchWrap(dp(8)));
        Button logout = secondaryButton("Oturumu kapat");
        logout.setOnClickListener(v -> signOut());
        root.addView(logout, matchWrap(0));
        setContentView(root);
    }

    private void showFatal(Throwable t) {
        try {
            LinearLayout root = new LinearLayout(this);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setGravity(Gravity.CENTER_HORIZONTAL);
            root.setPadding(dp(24), dp(64), dp(24), dp(32));
            root.setBackgroundColor(BG);
            TextView logo = new TextView(this);
            logo.setText("Yap!");
            logo.setTextColor(Color.WHITE);
            logo.setTextSize(40);
            logo.setTypeface(null, android.graphics.Typeface.BOLD);
            root.addView(logo, matchWrap(dp(16)));
            TextView title = new TextView(this);
            title.setText("Uygulama başlatılamadı");
            title.setTextColor(Color.WHITE);
            title.setTextSize(21);
            title.setGravity(Gravity.CENTER);
            root.addView(title, matchWrap(dp(10)));
            TextView detail = new TextView(this);
            detail.setText(simpleMessage(t));
            detail.setTextColor(Color.rgb(255, 190, 200));
            detail.setTextSize(13);
            detail.setGravity(Gravity.CENTER);
            root.addView(detail, matchWrap(dp(18)));
            Button retry = primaryButton("Tekrar dene");
            retry.setOnClickListener(v -> recreate());
            root.addView(retry, matchWrap(0));
            setContentView(root);
        } catch (Throwable ignored) {
            finish();
        }
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
        return error == null ? "İşlem tamamlanamadı." : "İşlem tamamlanamadı: " + simpleMessage(error);
    }

    private String firestoreError(Exception error) {
        String text = error == null || error.getMessage() == null ? "" : error.getMessage();
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.contains("PERMISSION_DENIED")) return "Firestore yetkisi reddedildi. Firebase Console'da Firestore kurallarını yayımla.";
        if (upper.contains("NOT_FOUND") || upper.contains("DATABASE")) return "Cloud Firestore veritabanı oluşturulmamış olabilir.";
        if (upper.contains("NETWORK") || upper.contains("UNAVAILABLE")) return "Sunucuya bağlanılamadı. İnternet bağlantını kontrol et.";
        return text.isEmpty() ? "Şirket verileri alınamadı." : text;
    }

    private String simpleMessage(Throwable t) {
        if (t == null) return "Bilinmeyen hata";
        String name = t.getClass().getSimpleName();
        String message = t.getMessage();
        if (message == null || message.trim().isEmpty()) return name;
        if (message.length() > 220) message = message.substring(0, 220);
        return name + ": " + message;
    }

    private String safe(String value) { return value == null ? "" : value; }

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

    private Button linkButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.rgb(189, 156, 255));
        b.setAllCaps(false);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private LinearLayout.LayoutParams matchWrap(int bottomMargin) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, bottomMargin);
        return p;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
