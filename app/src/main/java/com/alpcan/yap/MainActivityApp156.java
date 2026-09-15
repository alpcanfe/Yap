package com.alpcan.yap;

import android.content.Intent;
import android.net.Uri;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;

import org.json.JSONObject;

import java.util.Locale;

public class MainActivityApp156 extends MainActivityV155 {
    private WebView appWeb;

    @Override
    public void setContentView(View view) {
        super.setContentView(view);
        if (!(view instanceof WebView)) return;

        appWeb = (WebView) view;
        appWeb.addJavascriptInterface(new App156Bridge(), "App156Native");
        appWeb.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        appWeb.clearCache(true);

        appWeb.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleUrl(request == null ? null : request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return handleUrl(url == null ? null : Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                installTeamUi158(view);
                injectCurrentUser158(view);
            }
        });
    }

    private boolean handleUrl(Uri uri) {
        if (uri == null) return false;
        if ("yap".equalsIgnoreCase(uri.getScheme()) && "logout".equalsIgnoreCase(uri.getHost())) {
            logoutToGate();
            return true;
        }
        return false;
    }

    private void installTeamUi158(WebView web) {
        if (web == null) return;
        String js = "(function(){"
                + "window.__yapInvite154=true;"
                + "window.__decorateTeam158=function(){try{"
                + "var inviteCards=document.querySelectorAll('#inviteList .invite');"
                + "var invites=(typeof state!=='undefined'&&state&&Array.isArray(state.invites))?state.invites:[];"
                + "for(var i=0;i<inviteCards.length;i++){var inv=invites[i];if(!inv||!inv.id)continue;var card=inviteCards[i];"
                + "if(card.querySelector('.inviteActions158'))continue;"
                + "var row=document.createElement('div');row.className='inviteActions158';row.style.marginTop='10px';row.style.display='flex';row.style.gap='8px';row.style.flexWrap='wrap';"
                + "var share=document.createElement('button');share.className='chip';share.textContent='Paylaş';share.onclick=(function(x){return function(){if(window.InviteNative){InviteNative.share(x.id,x.email||'',x.role||'Çalışan');}};})(inv);row.appendChild(share);"
                + "var cancel=document.createElement('button');cancel.className='chip';cancel.style.color='#fb7185';cancel.textContent='İptal Et';cancel.onclick=(function(x){return function(){if(confirm('Bu davet iptal edilsin mi?'))App156Native.cancelInvite(x.id);};})(inv);row.appendChild(cancel);card.appendChild(row);"
                + "}"
                + "var memberCards=document.querySelectorAll('#memberList .member');"
                + "var members=(typeof state!=='undefined'&&state&&Array.isArray(state.members))?state.members:[];"
                + "for(var j=0;j<memberCards.length;j++){var m=members[j];if(!m||!m.uid||m.level==='owner')continue;var mcard=memberCards[j];"
                + "if(mcard.querySelector('.memberActions158'))continue;"
                + "var mrow=document.createElement('div');mrow.className='memberActions158';mrow.style.marginTop='10px';"
                + "var remove=document.createElement('button');remove.className='chip';remove.style.color='#fb7185';remove.textContent='Ekipten Çıkar';remove.onclick=(function(x){return function(){var n=x.name||x.email||'Bu kişi';if(confirm(n+' ekipten çıkarılsın mı?'))App156Native.removeMember(x.uid);};})(m);mrow.appendChild(remove);mcard.appendChild(mrow);"
                + "}"
                + "}catch(e){}};"
                + "if(!window.__yapRender158&&typeof window.renderTeam==='function'){window.__yapRender158=true;var baseRenderTeam158=window.renderTeam;window.renderTeam=function(){baseRenderTeam158();setTimeout(window.__decorateTeam158,0);};}"
                + "setTimeout(window.__decorateTeam158,0);"
                + "var logout=document.querySelector('#profileSheet .danger');if(logout){logout.onclick=function(){App156Native.logout();};}"
                + "})();";
        web.evaluateJavascript(js, null);
    }

    private void injectCurrentUser158(WebView web) {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        if (user == null || web == null) {
            logoutToGate();
            return;
        }

        db.collection("users").document(user.getUid()).get().addOnSuccessListener(profile -> {
            String orgId = profile.getString("activeOrgId");
            if (orgId == null) orgId = "";
            String finalOrgId = orgId;

            if (finalOrgId.isEmpty()) {
                pushUserToWeb(web, user, "", "", "");
                return;
            }

            db.collection("orgs").document(finalOrgId).collection("members").document(user.getUid()).get()
                    .addOnSuccessListener(member -> pushUserToWeb(
                            web,
                            user,
                            finalOrgId,
                            member.getString("level"),
                            member.getString("role")))
                    .addOnFailureListener(e -> pushUserToWeb(web, user, finalOrgId, "", ""));
        }).addOnFailureListener(e -> pushUserToWeb(web, user, "", "", ""));
    }

    private void pushUserToWeb(WebView web, FirebaseUser user, String orgId, String level, String role) {
        JSONObject data = new JSONObject();
        try {
            data.put("loggedIn", true);
            data.put("uid", user.getUid());
            data.put("email", user.getEmail() == null ? "" : user.getEmail());
            data.put("name", user.getDisplayName() == null ? "" : user.getDisplayName());
            data.put("photo", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
            data.put("orgId", orgId == null ? "" : orgId);
            data.put("level", level == null ? "" : level);
            data.put("role", role == null ? "" : role);
        } catch (Exception ignored) {}

        String script = "window.setNativeUser&&window.setNativeUser(" + data + ");"
                + "setTimeout(function(){if(window.__decorateTeam158)window.__decorateTeam158();},250);";
        web.post(() -> web.evaluateJavascript(script, null));
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
                            if (!invite.exists()) {
                                toast("Davet bulunamadı.");
                                removeInviteFromUiByCode(clean);
                                return;
                            }

                            String inviteOrg = invite.getString("orgId");
                            if (!orgId.equals(inviteOrg)) { toast("Bu davet başka bir şirkete ait."); return; }

                            String invitedEmail = invite.getString("email") == null
                                    ? ""
                                    : invite.getString("email").trim().toLowerCase(Locale.ROOT);

                            db.collection("invites").whereEqualTo("orgId", orgId).get()
                                    .addOnSuccessListener(allInvites -> {
                                        WriteBatch batch = db.batch();
                                        int deleteCount = 0;

                                        for (DocumentSnapshot doc : allInvites.getDocuments()) {
                                            String status = doc.getString("status");
                                            String email = doc.getString("email") == null
                                                    ? ""
                                                    : doc.getString("email").trim().toLowerCase(Locale.ROOT);

                                            if ("pending".equals(status) && invitedEmail.equals(email)) {
                                                batch.delete(doc.getReference());
                                                deleteCount++;
                                            }
                                        }

                                        if (deleteCount == 0) {
                                            removeInvitesForEmailFromUi(invitedEmail);
                                            toast("Bekleyen davet zaten kaldırılmış.");
                                            return;
                                        }

                                        batch.commit()
                                                .addOnSuccessListener(v -> {
                                                    removeInvitesForEmailFromUi(invitedEmail);
                                                    toast("Davet iptal edildi ve bekleyenlerden tamamen kaldırıldı.");
                                                })
                                                .addOnFailureListener(e -> toast("Davet sistemden silinemedi."));
                                    })
                                    .addOnFailureListener(e -> toast("Bekleyen davetler okunamadı."));
                        }).addOnFailureListener(e -> toast("Davet bilgisi okunamadı."));
                    })
                    .addOnFailureListener(e -> toast("Yönetici bilgisi okunamadı."));
        }).addOnFailureListener(e -> toast("Şirket profili okunamadı."));
    }

    private void removeInvitesForEmailFromUi(String email) {
        if (appWeb == null) return;
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String quotedEmail = JSONObject.quote(normalized);
        String js = "try{if(typeof state!=='undefined'&&state&&Array.isArray(state.invites)){"
                + "var target=" + quotedEmail + ";"
                + "state.invites=state.invites.filter(function(x){return String(x.email||'').trim().toLowerCase()!==target;});"
                + "if(typeof renderTeam==='function')renderTeam();}}catch(e){}"
                + "if(window.YapNative){YapNative.loadState();}";
        appWeb.post(() -> appWeb.evaluateJavascript(js, null));
    }

    private void removeInviteFromUiByCode(String code) {
        if (appWeb == null) return;
        String safeCode = code == null ? "" : code.replace("'", "");
        String js = "try{if(typeof state!=='undefined'&&state&&Array.isArray(state.invites)){"
                + "state.invites=state.invites.filter(function(x){return String(x.id||'')!=='" + safeCode + "';});"
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
                                    if (!target.exists()) { toast("Ekip üyesi bulunamadı."); removeMemberFromUi(targetUid); return; }
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
        String safeUid = uid == null ? "" : uid.replace("'", "");
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
                    .requestEmail()
                    .build();
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
