package com.alpcan.yap;

import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivityV155 extends MainActivityV154 {
    private WebView currentWeb;

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (view instanceof WebView) {
            currentWeb = (WebView) view;
            currentWeb.addJavascriptInterface(new InviteBridge155(), "InviteNative155");
            scheduleFix(currentWeb);
        }
    }

    private void scheduleFix(WebView web) {
        int[] delays = new int[]{700, 1400, 2400, 3600, 4800};
        for (int delay : delays) {
            web.postDelayed(() -> injectFix(web), delay);
        }
    }

    private void injectFix(WebView web) {
        if (web == null) return;
        String js = "(function(){"
                + "if(typeof window.sendInvite!=='function')return;"
                + "window.sendInvite=function(){"
                + "var e=document.getElementById('inviteEmail'),r=document.getElementById('inviteRole');"
                + "var email=(e&&e.value||'').trim(),role=(r&&r.value||'Çalışan');"
                + "if(email.indexOf('@')<0){if(window.toast)toast('Geçerli bir e-posta adresi yaz.','error');return;}"
                + "InviteNative155.createInvite(email,role);"
                + "if(e)e.value='';"
                + "};"
                + "})();";
        web.evaluateJavascript(js, null);
    }

    public class InviteBridge155 {
        @JavascriptInterface
        public void createInvite(String email, String role) {
            runOnUiThread(() -> resolveManagerAndCreate(email, role));
        }
    }

    private void resolveManagerAndCreate(String email, String role) {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) {
            Toast.makeText(this, "Oturum bulunamadı. Tekrar giriş yap.", Toast.LENGTH_LONG).show();
            return;
        }

        db.collection("users").document(user.getUid()).get()
                .addOnSuccessListener(profile -> {
                    String orgId = profile.getString("activeOrgId");
                    if (orgId == null || orgId.trim().isEmpty()) {
                        Toast.makeText(this, "Şirket alanı bulunamadı.", Toast.LENGTH_LONG).show();
                        return;
                    }
                    db.collection("orgs").document(orgId).collection("members").document(user.getUid()).get()
                            .addOnSuccessListener(member -> {
                                String level = member.getString("level");
                                boolean manager = "owner".equals(level) || "admin".equals(level);
                                if (!manager) {
                                    Toast.makeText(this, "Bu hesap yönetici değil. Rol: " + (level == null ? "bilinmiyor" : level), Toast.LENGTH_LONG).show();
                                    return;
                                }
                                InviteCodeService.create(this, auth, db, orgId, true, email, role, new InviteCodeService.Callback() {
                                    @Override
                                    public void notice(String type, String message) {
                                        runOnUiThread(() -> Toast.makeText(MainActivityV155.this, message, Toast.LENGTH_LONG).show());
                                    }

                                    @Override
                                    public void refresh() {
                                        WebView web = currentWeb;
                                        if (web != null) {
                                            web.post(() -> web.evaluateJavascript("if(window.YapNative){YapNative.loadState();}", null));
                                        }
                                    }

                                    @Override
                                    public void joined(String orgId, String joinedRole) {
                                        runOnUiThread(() -> recreate());
                                    }
                                });
                            })
                            .addOnFailureListener(e -> Toast.makeText(this, "Yönetici bilgisi okunamadı.", Toast.LENGTH_LONG).show());
                })
                .addOnFailureListener(e -> Toast.makeText(this, "Şirket profili okunamadı.", Toast.LENGTH_LONG).show());
    }
}
