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
                + "if(window.__yapApp157)return;"
                + "if(typeof renderTeam!=='function'||!document.getElementById('profileSheet'))return;"
                + "window.__yapApp157=true;"
                + "var oldRender=renderTeam;"
                + "renderTeam=function(){oldRender();setTimeout(function(){"
                + "var inviteCards=document.querySelectorAll('#inviteList .invite');"
                + "var invites=(typeof state!=='undefined'&&state&&Array.isArray(state.invites))?state.invites:[];"
                + "for(var i=0;i<inviteCards.length;i++){var inv=invites[i];if(!inv||!inv.id)continue;"
                + "if(inviteCards[i].querySelector('.cancelInvite157'))continue;"
                + "var irow=document.createElement('div');irow.style.marginTop='10px';"
                + "var ibtn=document.createElement('button');ibtn.className='chip cancelInvite157';ibtn.style.color='#fb7185';ibtn.textContent='İptal Et';"
                + "ibtn.onclick=(function(x){return function(){if(confirm('Bu davet iptal edilsin mi?'))App156Native.cancelInvite(x.id);};})(inv);"
                + "irow.appendChild(ibtn);inviteCards[i].appendChild(irow);"
                + "}"
                + "var memberCards=document.querySelectorAll('#memberList .member');"
                + "var members=(typeof state!=='undefined'&&state&&Array.isArray(state.members))?state.members:[];"
                + "for(var j=0;j<memberCards.length;j++){var m=members[j];if(!m||!m.uid||m.level==='owner')continue;"
                + "if(memberCards[j].querySelector('.removeMember157'))continue;"
                + "var mrow=document.createElement('div');mrow.style.marginTop='10px';"
                + "var mbtn=document.createElement('button');mbtn.className='chip removeMember157';mbtn.style.color='#fb7185';mbtn.textContent='Ekipten Çıkar';"
                + "mbtn.onclick=(function(x){return function(){var n=x.name||x.email||'Bu kişi';if(confirm(n+' ekipten çıkarılsın mı?'))App156Native.removeMember(x.uid);};})(m);"
                + "mrow.appendChild(mbtn);memberCards[j].appendChild(mrow);"
                + "}"
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
        public void removeMember(String uid) {
            runOnUiThread(() -> removeMemberNative(uid));
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
                                        removeInviteFromUi(clean);
                                    })
                                    .addOnFailureListener(e -> toast("Davet iptal edilemedi."));
                        }).addOnFailureListener(e -> toast("Davet bilgisi okunamadı."));
                    })
                    .addOnFailureListener(e -> toast("Yönetici bilgisi okunamadı."));
        }).addOnFailureListener(e -> toast("Şirket profili okunamadı."));
    }

    private void removeInviteFromUi(String code) {
        if (appWeb == null) return;
        String js = "try{if(typeof state!=='undefined'&&state&&Array.isArray(state.invites)){"
                + "state.invites=state.invites.filter(function(x){return String(x.id||'')!=='" + code + "';});"
                + "if(typeof renderTeam==='function')renderTeam();}}catch(e){}"
                + "if(window.YapNative){YapNative.loadState();}";
        appWeb.post(() -> appWeb.evaluateJavascript(js, null));
    }

    private void removeMemberNative(String uid) {
        String targetUid = uid == null ? "" : uid.trim();
        if (targetUid.isEmpty()) return;

        FirebaseAuth auth = FirebaseAuth.getInstance();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        FirebaseUser user = auth.getCurrentUser();
        if (user == null) { logoutToGate(); return; }
        if (user.getUid().equals(targetUid)) {
            toast("Kendi hesabını bu ekrandan ekipten çıkaramazsın.");
            return;
        }

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(profile -> {
            String orgId = profile.getString("activeOrgId");
            if (orgId == null || orgId.isEmpty()) { toast("Aktif şirket bulunamadı."); return; }

            db.collection("orgs").document(orgId).collection("members").document(user.getUid()).get()
                    .addOnSuccessListener(requester -> {
                        String requesterLevel = requester.getString("level");
                        boolean owner = "owner".equals(requesterLevel);
                        boolean admin = "admin".equals(requesterLevel);
                        if (!owner && !admin) {
                            toast("Bu işlem için yönetici yetkisi gerekiyor.");
                            return;
                        }

                        db.collection("orgs").document(orgId).collection("members").document(targetUid).get()
                                .addOnSuccessListener(target -> {
                                    if (!target.exists()) { toast("Ekip üyesi bulunamadı."); return; }
                                    String targetLevel = target.getString("level");
                                    if ("owner".equals(targetLevel)) {
                                        toast("Kurucu yönetici ekipten çıkarılamaz.");
                                        return;
                                    }
                                    if (admin && "admin".equals(targetLevel)) {
                                        toast("Bir yöneticiyi yalnız kurucu yönetici ekipten çıkarabilir.");
                                        return;
                                    }

                                    db.collection("orgs").document(orgId).collection("members").document(targetUid).delete()
                                            .addOnSuccessListener(v -> {
                                                toast("Ekip üyesi çıkarıldı.");
                                                removeMemberFromUi(targetUid);
                                            })
                                            .addOnFailureListener(e -> toast("Ekip üyesi çıkarılamadı."));
                                })
                                .addOnFailureListener(e -> toast("Ekip üyesi bilgisi okunamadı."));
                    })
                    .addOnFailureListener(e -> toast("Yönetici bilgisi okunamadı."));
        }).addOnFailureListener(e -> toast("Şirket profili okunamadı."));
    }

    private void removeMemberFromUi(String uid) {
        if (appWeb == null) return;
        String safeUid = uid.replace("'", "");
        String js = "try{if(typeof state!=='undefined'&&state&&Array.isArray(state.members)){"
                + "state.members=state.members.filter(function(x){return String(x.uid||'')!=='" + safeUid + "';});"
                + "if(typeof renderTeam==='function')renderTeam();}}catch(e){}"
                + "if(window.YapNative){YapNative.loadState();}";
        appWeb.post(() -> appWeb.evaluateJavascript(js, null));
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
