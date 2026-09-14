# Yap! Android

Yap! görev ve şirket içi ekip yönetimi uygulaması.

## Final sürüm
- Uygulama: Yap!
- Paket: `com.alpcan.yap`
- Sürüm: **1.5.1** (`versionCode 151`)
- Android: 7.0 ve üzeri (`minSdk 24`, `targetSdk 36`)
- Java: 17
- Kaynak commit: `4b8c25008a54d0356bca81fa12b68a0c61ce1b8e`
- Doğrulanan derleme: https://github.com/alpcanfe/Yap/actions/runs/34867197955
- Final APK SHA-256: `A8902A3F701BC020907CDAB0C8CF14BF49ADEF610CBA6A794AAD392FA4F91EBD`

## Şirket modu
Şirket alanı, personel davetleri, rol atama, personele görev atama ve görev içi mesajlaşma kaynaklarda bulunur. Cihaz üzerinde uçtan uca Google girişi ve davet testi bu final imzalama çalışmasında yapılmadı.

## Kalıcı release sertifikası
14 Eylül 2026 tarihinde yeni RSA 3072 bit release anahtarı oluşturuldu. Final APK v2/v3 imza doğrulamasını ve zipalign kontrolünü geçti.

- SHA-1: `BB:9F:5F:09:59:82:F7:C8:16:26:FA:66:4A:D1:59:29:45:87:4C:86`
- SHA-256: `93:43:4F:A9:2A:8E:73:EA:2E:7A:6C:22:1D:38:A2:A7:41:2D:E4:B0:D6:24:5E:24:53:6F:DA:C1:59:71:20:FE`
- Anahtar alias: `yap-release`
- Sertifika geçerliliği: 21 Ağustos 2126 tarihine kadar.

Firebase projesindeki `com.alpcan.yap` Android uygulamasına bu SHA değerleri eklenmelidir. Bu çalışma Firebase ayarlarını değiştirmedi.

Önceki sürümün SHA-1 değeri `50:76:42:50:AA:9F:48:6F:65:14:F7:CF:4A:68:13:32:86:71:BF:7C` idi. Eski özel anahtar mevcut olmadığı için yeni imzalı APK eski sertifikalı kurulumun üzerine güncellenemez. Kaldırma işleminden önce yerel veriler yedeklenmelidir.

## Derleme ve imzalama
GitHub Actions `assembleRelease` ile **imzasız** APK artifact'i üretir. Final APK bu artifact'tan indirilip yerel kalıcı anahtarla imzalandı. Sonraki sürümler de aynı anahtarla imzalanmalıdır; otomatik release imzalama henüz yapılandırılmadı.

Özel anahtar ve parolalar repoya, Actions loglarına veya herkese açık artifact'lara eklenmez.
