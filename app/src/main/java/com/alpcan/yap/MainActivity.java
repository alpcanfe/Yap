package com.alpcan.yap;

import android.app.Activity;
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

import com.google.android.libraries.identity.googleid.GetGoogleIdOption;
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential;
import com.google.firebase.auth.AuthCredential;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.GoogleAuthProvider;

import org.json.JSONObject;

import java.util.concurrent.Executor;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(17, 16, 26);
    private static final int CARD = Color.rgb(29, 27, 42);
    private static final int PURPLE = Color.rgb(124, 58, 237);
    private static final int MUTED = Color.rgb(170, 165, 189);

    private FirebaseAuth auth;
    private CredentialManager credentialManager;
    private Executor mainExecutor;
    private WebView web;
    private EditText emailInput;
    private EditText passwordInput;
    private TextView authMessage;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        configureSystemBars();
        auth = FirebaseAuth.getInstance();
        credentialManager = CredentialManager.create(this);
        mainExecutor = command -> runOnUiThread(command);

        if (auth.getCurrentUser() != null) {
            showApp();
        } else {
            showAuthScreen();
        }
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
        root.setPadding(dp(24), dp(72), dp(24), dp(32));
        scroll.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView logo = new TextView(this);
        logo.setText("Yap!");
        logo.setTextColor(Color.WHITE);
        logo.setTextSize(42);
        logo.setGravity(Gravity.CENTER);
        logo.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(logo, matchWrap(dp(6)));

        TextView lead = new TextView(this);
        lead.setText("Görevlerine kaldığın yerden devam et.");
        lead.setTextColor(MUTED);
        lead.setTextSize(15);
        lead.setGravity(Gravity.CENTER);
        root.addView(lead, matchWrap(dp(24)));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(18), dp(18), dp(18));
        card.setBackground(rounded(CARD, dp(24)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        cardParams.setMargins(0, 0, 0, dp(12));
        root.addView(card, cardParams);

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

        Button reset = linkButton("Şifremi unuttum");
        reset.setOnClickListener(v -> resetPassword());
        card.addView(reset, matchWrap(dp(8)));

        Button google = secondaryButton("Google hesabıyla devam et");
        google.setOnClickListener(v -> startGoogleSignIn());
        card.addView(google, matchWrap(dp(8)));

        Button guest = linkButton("Misafir olarak devam et");
        guest.setOnClickListener(v -> showApp());
        card.addView(guest, matchWrap(0));

        setContentView(scroll);
    }

    private void emailLogin(boolean register) {
        String email = emailInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        if (!email.contains("@")) {
            showMessage("Geçerli bir e-posta adresi yaz.");
            return;
        }
        if (password.length() < 6) {
            showMessage("Şifre en az 6 karakter olmalı.");
            return;
        }
        showMessage(register ? "Hesap oluşturuluyor..." : "Giriş yapılıyor...");
        if (register) {
            auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) showApp();
                        else showMessage(authError(task.getException()));
                    });
        } else {
            auth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(this, task -> {
                        if (task.isSuccessful()) showApp();
                        else showMessage(authError(task.getException()));
                    });
        }
    }

    private void resetPassword() {
        String email = emailInput.getText().toString().trim();
        if (!email.contains("@")) {
            showMessage("Önce e-posta adresini yaz.");
            return;
        }
        auth.sendPasswordResetEmail(email).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) showMessage("Şifre yenileme bağlantısı gönderildi.");
            else showMessage(authError(task.getException()));
        });
    }

    private void startGoogleSignIn() {
        try {
            GetGoogleIdOption googleOption = new GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(getString(R.string.default_web_client_id))
                    .build();
            GetCredentialRequest request = new GetCredentialRequest.Builder()
                    .addCredentialOption(googleOption)
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
                            showMessage("Google hesabı seçilemedi. Lütfen tekrar dene.");
                        }
                    }
            );
        } catch (Exception e) {
            showMessage("Google girişi başlatılamadı.");
        }
    }

    private void handleGoogleCredential(Credential credential) {
        try {
            if (!(credential instanceof CustomCredential)) {
                showMessage("Google kimlik bilgisi alınamadı.");
                return;
            }
            CustomCredential customCredential = (CustomCredential) credential;
            if (!GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL.equals(customCredential.getType())) {
                showMessage("Beklenmeyen Google kimlik bilgisi türü.");
                return;
            }
            GoogleIdTokenCredential googleCredential =
                    GoogleIdTokenCredential.createFrom(customCredential.getData());
            AuthCredential firebaseCredential =
                    GoogleAuthProvider.getCredential(googleCredential.getIdToken(), null);
            auth.signInWithCredential(firebaseCredential).addOnCompleteListener(this, task -> {
                if (task.isSuccessful()) showApp();
                else showMessage(authError(task.getException()));
            });
        } catch (Exception e) {
            showMessage("Google hesabı doğrulanamadı. Lütfen tekrar dene.");
        }
    }

    private void showApp() {
        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        web.setBackgroundColor(BG);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleAppUrl(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleAppUrl(Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                injectCurrentUser();
            }
        });
        applySystemInsets(web);
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private boolean handleAppUrl(Uri uri) {
        if (!"yap".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if ("login".equalsIgnoreCase(host)) {
            showAuthScreen();
            return true;
        }
        if ("logout".equalsIgnoreCase(host)) {
            signOut();
            return true;
        }
        return true;
    }

    private void signOut() {
        auth.signOut();
        credentialManager.clearCredentialStateAsync(
                new ClearCredentialStateRequest(),
                new CancellationSignal(),
                mainExecutor,
                new CredentialManagerCallback<Void, ClearCredentialException>() {
                    @Override public void onResult(Void result) { showAuthScreen(); }
                    @Override public void onError(@NonNull ClearCredentialException e) { showAuthScreen(); }
                }
        );
    }

    private void injectCurrentUser() {
        FirebaseUser user = auth.getCurrentUser();
        JSONObject data = new JSONObject();
        try {
            data.put("loggedIn", user != null);
            data.put("email", user != null && user.getEmail() != null ? user.getEmail() : "");
            data.put("name", user != null && user.getDisplayName() != null ? user.getDisplayName() : "");
            data.put("photo", user != null && user.getPhotoUrl() != null ? user.getPhotoUrl().toString() : "");
        } catch (Exception ignored) {}
        web.evaluateJavascript("window.setNativeUser&&window.setNativeUser(" + data + ")", null);
    }

    private void showMessage(String text) {
        if (authMessage == null) return;
        authMessage.setText(text);
        authMessage.setVisibility(View.VISIBLE);
    }

    private String authError(Exception error) {
        String text = error == null || error.getMessage() == null ? "" : error.getMessage().toUpperCase();
        if (text.contains("ALREADY") && text.contains("EMAIL")) return "Bu e-posta adresi zaten kayıtlı.";
        if (text.contains("INVALID") && (text.contains("CREDENTIAL") || text.contains("PASSWORD"))) return "E-posta veya şifre hatalı.";
        if (text.contains("WEAK") && text.contains("PASSWORD")) return "Şifre en az 6 karakter olmalı.";
        if (text.contains("NETWORK") || text.contains("TIMEOUT")) return "İnternet bağlantısı kurulamadı.";
        return "İşlem tamamlanamadı. Lütfen tekrar dene.";
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
