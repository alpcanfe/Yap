package com.alpcan.yap;

import android.app.Activity;
import android.content.Intent;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.firestore.WriteBatch;

import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public final class InviteCodeService {
    private static final String ABC = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final long VALID_MS = 7L * 24L * 60L * 60L * 1000L;
    private static final SecureRandom RNG = new SecureRandom();

    private InviteCodeService() {}

    public interface Callback {
        void notice(String type, String message);
        void refresh();
        void joined(String orgId, String role);
    }

    public static String clean(String raw) {
        return raw == null ? "" : raw.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    public static String display(String raw) {
        String c = clean(raw);
        return c.length() == 10 ? c.substring(0, 5) + "-" + c.substring(5) : c;
    }

    private static String code() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 10; i++) b.append(ABC.charAt(RNG.nextInt(ABC.length())));
        return b.toString();
    }

    public static void create(Activity activity, FirebaseAuth auth, FirebaseFirestore db, String orgId,
                              boolean manager, String email, String role, Callback cb) {
        if (!manager) { cb.notice("error", "Bu işlem için yönetici yetkisi gerekiyor."); return; }
        FirebaseUser u = auth.getCurrentUser();
        if (u == null) return;
        String mail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String r = role == null ? "" : role.trim();
        if (r.isEmpty()) r = "Çalışan";
        if (!mail.contains("@")) { cb.notice("error", "Geçerli bir e-posta adresi yaz."); return; }
        String self = u.getEmail() == null ? "" : u.getEmail().trim().toLowerCase(Locale.ROOT);
        if (mail.equals(self)) { cb.notice("error", "Kendine davet oluşturamazsın."); return; }

        String c = code();
        Map<String, Object> data = new HashMap<>();
        data.put("code", c);
        data.put("orgId", orgId);
        data.put("email", mail);
        data.put("role", r);
        data.put("status", "pending");
        data.put("createdBy", u.getUid());
        data.put("createdAt", FieldValue.serverTimestamp());
        data.put("expiresAtMillis", System.currentTimeMillis() + VALID_MS);
        String finalRole = r;
        db.collection("invites").document(c).set(data)
                .addOnSuccessListener(v -> {
                    cb.notice("success", "Davet kodu oluşturuldu: " + display(c));
                    cb.refresh();
                    share(activity, c, mail, finalRole);
                })
                .addOnFailureListener(e -> cb.notice("error", e.getMessage() == null ? "Davet oluşturulamadı." : e.getMessage()));
    }

    public static void accept(FirebaseAuth auth, FirebaseFirestore db, String raw, Callback cb) {
        String c = clean(raw);
        if (c.length() != 10) { cb.notice("error", "Geçerli bir davet kodu yaz."); return; }
        FirebaseUser current = auth.getCurrentUser();
        if (current == null) { cb.notice("error", "Önce davet edilen hesapla giriş yap."); return; }

        current.reload().addOnCompleteListener(reload -> {
            FirebaseUser user = auth.getCurrentUser();
            if (user == null) { cb.notice("error", "Oturum doğrulanamadı."); return; }
            if (!user.isEmailVerified()) {
                user.sendEmailVerification();
                cb.notice("error", "E-posta adresini doğrulaman gerekiyor. Doğrulama bağlantısı gönderildi.");
                return;
            }

            DocumentReference ref = db.collection("invites").document(c);
            ref.get().addOnSuccessListener(invite -> {
                if (!invite.exists() || !"pending".equals(invite.getString("status"))) {
                    cb.notice("error", "Davet kodu geçersiz veya daha önce kullanılmış.");
                    return;
                }
                Long expires = invite.getLong("expiresAtMillis");
                if (expires != null && System.currentTimeMillis() > expires) {
                    cb.notice("error", "Davet kodunun süresi dolmuş.");
                    return;
                }
                String invitedEmail = invite.getString("email") == null ? "" : invite.getString("email").trim().toLowerCase(Locale.ROOT);
                String signedEmail = user.getEmail() == null ? "" : user.getEmail().trim().toLowerCase(Locale.ROOT);
                if (!invitedEmail.equals(signedEmail)) {
                    cb.notice("error", "Bu kod giriş yaptığın e-posta hesabına ait değil.");
                    return;
                }
                String org = invite.getString("orgId");
                String role = invite.getString("role");
                if (role == null || role.isEmpty()) role = "Çalışan";
                if (org == null || org.isEmpty()) {
                    cb.notice("error", "Davet şirket bilgisi içermiyor.");
                    return;
                }

                Map<String, Object> member = new HashMap<>();
                member.put("uid", user.getUid());
                member.put("email", signedEmail);
                member.put("name", user.getDisplayName() == null ? signedEmail : user.getDisplayName());
                member.put("photo", user.getPhotoUrl() == null ? "" : user.getPhotoUrl().toString());
                member.put("level", "member");
                member.put("role", role);
                member.put("inviteToken", c);
                member.put("joinedAt", FieldValue.serverTimestamp());

                Map<String, Object> profile = new HashMap<>();
                profile.put("uid", user.getUid());
                profile.put("email", signedEmail);
                profile.put("name", member.get("name"));
                profile.put("activeOrgId", org);
                profile.put("updatedAt", FieldValue.serverTimestamp());

                Map<String, Object> accepted = new HashMap<>();
                accepted.put("status", "accepted");
                accepted.put("acceptedBy", user.getUid());
                accepted.put("acceptedAt", FieldValue.serverTimestamp());

                WriteBatch batch = db.batch();
                batch.set(db.collection("orgs").document(org).collection("members").document(user.getUid()), member, SetOptions.merge());
                batch.set(db.collection("users").document(user.getUid()), profile, SetOptions.merge());
                batch.set(ref, accepted, SetOptions.merge());
                String finalRole = role;
                batch.commit()
                        .addOnSuccessListener(v -> cb.joined(org, finalRole))
                        .addOnFailureListener(e -> cb.notice("error", e.getMessage() == null ? "Davet kabul edilemedi." : e.getMessage()));
            }).addOnFailureListener(e -> cb.notice("error", "Davet kodu bu hesap için geçerli değil veya okunamadı."));
        });
    }

    public static void share(Activity activity, String raw, String email, String role) {
        String c = clean(raw);
        if (c.length() != 10) return;
        String text = "Yap! ekibine davet edildin.\n\n"
                + "Davet kodun: " + display(c) + "\n"
                + "Davet edilen hesap: " + email + "\n"
                + "Rol: " + role + "\n\n"
                + "Yap! uygulamasını aç, davet kodunu giriş ekranına yaz ve bu e-posta hesabıyla giriş yap. Kod 7 gün geçerlidir.";
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text);
        activity.startActivity(Intent.createChooser(send, "Davet kodunu paylaş"));
    }
}
