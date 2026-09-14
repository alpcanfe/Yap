package com.alpcan.yap;

import android.content.Intent;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class MainActivityApp156 extends MainActivityV155 {
    private WebView appWeb;

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (view instanceof WebView) {
            appWeb = (WebView) view;
            appWeb.addJavascriptInterface(new App156Bridge(), "App156Native");
            int[] delays = new int[]{1200, 2200, 3400, 5000};
            for (int delay : delays) appWeb.postDelayed(() -> inject156(appWeb), delay);
        }
    }

    private void inject156(WebView web) {
        if (web == null) return;
        String js = "(function(){"
                + "if(window.__yapApp156)return;"
                + "if(typeof renderTeam!=='function'||!document.getElementById('profileSheet'))return;"
                + "window.__yapApp156=true;"
                + "var oldRender=renderTeam;"
                + "renderTeam=function(){oldRender();setTimeout(function(){"
                + "var cards=document.querySelectorAll('#inviteList .invite');"
                + "var list=(typeof state!=='undefined'&&state&&Array.isArray(state.invites))?state.invites:[];"
                + "for(var i=0;i<cards.length;i++){var inv=list[i];if(!inv||!inv.id)continue;"
                + "if(cards[i].querySelector('.cancelInvite156'))continue;"
                + "var row=document.createElement('div');row.style.marginTop='10px';"
                + "var btn=document.createElement('button');btn.className='chip cancelInvite156';btn.style.color='#fb7185';btn.textContent='İptal Et';"
                + "btn.onclick=(function(x){return function(){if(confirm('Bu davet iptal edilsin mi?'))App156Native.cancelInvite(x.id);};})(inv);"
                + "row.appendChild(btn);cards[i].appendChild(row);"
                + "}},0);};"
                + "var logout=document.querySelector('#profileSheet .danger');"
                + "if(logout){logout.onclick=function(){App156Native.logout();};}"
                + "try{renderTeam();}catch(e){}"
                + "})();";
        web.evaluateJavascript(js, null);
    }

    public class App156Bridge {
        @JavascriptInterface
        public void cancelInvite(String code) {
            runOnUiThread(() -> cancelInviteNative(code));
        }

        @JavascriptInterface
        public void logout() {
            runOnUiThread(MainActivityApp156.this::logoutToGate);
        }
    }

    private void cancelInviteNative(String code) {
        String clean = InviteCodeService.clean(code);
        if (clean.length() != 10) {
            toast("Davet kodu geçersiz.");
            return;
        }
        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { logoutToGate(); return; }

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(profile -> {
            String orgId = profile.getString("activeOrgId");
            if (orgId == null || orgId.isEmpty()) { toast("Aktif şirket bulunamadı."); return; }
            db.collection("orgs").document(orgId).collection("members").document(user.getUid()).get()
                    .addOnSuccessListener(member -> {
                        String level = member.getString("level");
                        boolean manager = "owner".equals(level) || "admin".equals(level);
                        if (!manager) { toast("Bu işlem için yönetici yetkisi gerekiyor."); return; }
                        db.collection("invites").document(clean).get().addOnSuccessListener(invite -> {
                            if (!invite.exists()) { toast("Davet bulunamadı."); return; }
                            String inviteOrg = invite.getString("orgId");
                            if (!orgId.equals(inviteOrg)) { toast("Bu davet başka bir şirkete ait."); return; }
                            if (!"pending".equals(invite.getString("status"))) { toast("Bu davet artık beklemede değil."); return; }
                            Map<String, Object> patch = new HashMap<>();
                            patch.put("status", "revoked");
                            patch.put("revokedBy", user.getUid());
                            patch.put("revokedAt", FieldValue.serverTimestamp());
                            db.collection("invites").document(clean).set(patch, SetOptions.merge())
                                    .addOnSuccessListener(v -> {
                                        toast("Davet iptal edildi.");
                                        if (appWeb != null) appWeb.post(() -> appWeb.evaluateJavascript("if(window.YapNative){YapNative.loadState();}", null));
                                    })
                                    .addOnFailureListener(e -> toast("Davet iptal edilemedi."));
                        }).addOnFailureListener(e -> toast("Davet bilgisi okunamadı."));
                    })
                    .addOnFailureListener(e -> toast("Yönetici bilgisi okunamadı."));
        }).addOnFailureListener(e -> toast("Şirket profili okunamadı."));
    }

    private void logoutToGate() {
        try { FirebaseAuth.getInstance().signOut(); } catch (Exception ignored) {}
        try {
            GoogleSignInOptions gso = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                    .requestIdToken(getString(R.string.default_web_client_id))
                    .requestEmail().build();
            GoogleSignIn.getClient(this, gso).signOut();
        } catch (Exception ignored) {}
        Intent i = new Intent(this, MainActivityV156.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }
}
