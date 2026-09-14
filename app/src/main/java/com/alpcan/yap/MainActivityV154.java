package com.alpcan.yap;

import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;
import android.widget.Toast;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

public class MainActivityV154 extends MainActivityStable {
    private WebView inviteWeb;

    @Override
    public void setContentView(View view) {
        if (view instanceof WebView) {
            WebView web = (WebView) view;
            inviteWeb = web;
            web.addJavascriptInterface(new InviteBridge(), "InviteNative");
            super.setContentView(view);
            scheduleInviteUi(web);
            return;
        }
        super.setContentView(view);
    }

    private void scheduleInviteUi(WebView web) {
        web.postDelayed(() -> injectInviteUi(web), 350);
        web.postDelayed(() -> injectInviteUi(web), 900);
        web.postDelayed(() -> injectInviteUi(web), 1800);
        web.postDelayed(() -> injectInviteUi(web), 3000);
    }

    private void injectInviteUi(WebView web) {
        if (web == null) return;
        String js = "(function(){"
                + "if(window.__yapInvite154)return;"
                + "if(typeof window.sendInvite!=='function'||!document.getElementById('profileSheet'))return;"
                + "window.__yapInvite154=true;"
                + "window.sendInvite=function(){"
                + "var e=document.getElementById('inviteEmail'),r=document.getElementById('inviteRole');"
                + "var email=(e&&e.value||'').trim(),role=(r&&r.value||'Çalışan');"
                + "if(email.indexOf('@')<0){if(window.toast)toast('Geçerli bir e-posta adresi yaz.','error');return;}"
                + "InviteNative.createInvite((window.authUser&&authUser.orgId)||'',!!(window.state&&state.isManager),email,role);"
                + "if(e)e.value='';"
                + "};"
                + "var team=document.getElementById('team');"
                + "if(team){var info=team.querySelector('.mutedbox');if(info)info.textContent='E-posta ve rolü seç, tek kullanımlık davet kodu oluştur. Kod yalnız davet edilen hesapta çalışır ve 7 gün geçerlidir.';"
                + "var bs=team.querySelectorAll('button');for(var i=0;i<bs.length;i++){if((bs[i].textContent||'').indexOf('Davet bağlantısını gönder')>=0)bs[i].textContent='Davet kodu oluştur ve paylaş';}}"
                + "var panel=document.querySelector('#profileSheet .panel');"
                + "if(panel&&!document.getElementById('joinInviteCode154')){"
                + "var wrap=document.createElement('div');wrap.id='joinInviteBox154';wrap.className='mutedbox';wrap.style.marginTop='14px';"
                + "var title=document.createElement('div');title.style.fontWeight='800';title.style.color='#fff';title.textContent='Davet koduyla ekibe katıl';wrap.appendChild(title);"
                + "var hint=document.createElement('div');hint.style.marginTop='6px';hint.textContent='Yöneticinin gönderdiği 10 karakterlik kodu gir.';wrap.appendChild(hint);"
                + "var input=document.createElement('input');input.id='joinInviteCode154';input.className='field';input.maxLength=11;input.placeholder='ABCDE-23456';input.style.marginTop='10px';wrap.appendChild(input);"
                + "var btn=document.createElement('button');btn.className='secondary';btn.textContent='Ekibe katıl';btn.onclick=function(){var c=(input.value||'').toUpperCase().replace(/[^A-Z0-9]/g,'');if(c.length!==10){if(window.toast)toast('10 karakterlik davet kodunu yaz.','error');return;}InviteNative.join(c);};wrap.appendChild(btn);"
                + "var logout=panel.querySelector('.danger');panel.insertBefore(wrap,logout);"
                + "}"
                + "var oldRender=window.renderTeam;"
                + "if(typeof oldRender==='function'){window.renderTeam=function(){oldRender();setTimeout(function(){var cards=document.querySelectorAll('#inviteList .invite');var list=(window.state&&state.invites)||[];for(var k=0;k<cards.length;k++){var inv=list[k];if(!inv||!inv.id||inv.id.length!==10||cards[k].querySelector('.inviteCode154'))continue;var box=document.createElement('div');box.className='inviteCode154';box.style.marginTop='10px';var code=document.createElement('span');code.className='badge';code.textContent=inv.id.slice(0,5)+'-'+inv.id.slice(5);box.appendChild(code);var sh=document.createElement('button');sh.className='chip';sh.style.marginLeft='8px';sh.textContent='Paylaş';sh.onclick=(function(x){return function(){InviteNative.share(x.id,x.email||'',x.role||'Çalışan');};})(inv);box.appendChild(sh);cards[k].appendChild(box);}},0);};try{window.renderTeam();}catch(e){}}"
                + "})();";
        web.evaluateJavascript(js, null);
    }

    private InviteCodeService.Callback callback() {
        return new InviteCodeService.Callback() {
            @Override
            public void notice(String type, String message) {
                runOnUiThread(() -> Toast.makeText(MainActivityV154.this, message, Toast.LENGTH_LONG).show());
            }

            @Override
            public void refresh() {
                WebView web = inviteWeb;
                if (web != null) web.post(() -> web.evaluateJavascript("if(window.YapNative){YapNative.loadState();}", null));
            }

            @Override
            public void joined(String orgId, String role) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivityV154.this, "Ekibe katıldın: " + role, Toast.LENGTH_LONG).show();
                    recreate();
                });
            }
        };
    }

    public class InviteBridge {
        @JavascriptInterface
        public void createInvite(String orgId, boolean manager, String email, String role) {
            runOnUiThread(() -> InviteCodeService.create(MainActivityV154.this,
                    FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(), orgId, manager, email, role, callback()));
        }

        @JavascriptInterface
        public void join(String code) {
            runOnUiThread(() -> InviteCodeService.accept(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance(), code, callback()));
        }

        @JavascriptInterface
        public void share(String code, String email, String role) {
            runOnUiThread(() -> InviteCodeService.share(MainActivityV154.this, code, email, role));
        }
    }
}
