# Yap! Android

Yap! görev ve şirket içi ekip yönetimi uygulaması.

## Güncel sürüm
- Uygulama: Yap!
- Paket: `com.alpcan.yap`
- Sürüm: **1.5.2** (`versionCode 152`)
- Android: 7.0 ve üzeri (`minSdk 24`, `targetSdk 36`)
- Java: 17

## Firebase
Firebase Android uygulaması `com.alpcan.yap` için güncel `google-services.json` kullanılır. Yeni release sertifikası SHA değerleri Firebase Console'da kayıtlı olmalıdır.

## Şirket modu
Şirket alanı, personel davetleri, rol atama, personele görev atama ve görev içi mesajlaşma kaynaklarda bulunur.

## Derleme
GitHub Actions `assembleRelease` ile imzasız APK artifact'i üretir. Dağıtılacak APK ayrıca kalıcı bir release anahtarıyla imzalanmalıdır.
