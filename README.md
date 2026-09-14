# Yap! Android

**Yap!** görev ve yapılacaklar uygulamasının Android kaynak kodu.

## Sürüm
- Uygulama: **Yap!**
- Paket: `com.alpcan.yap`
- Sürüm: `1.4.0`
- `minSdk`: 24
- `targetSdk`: 36
- Java: 17

## Giriş sistemi
Google girişi Android Credential Manager + Google ID + Firebase Authentication ile çalışır. E-posta/şifre ile giriş, kayıt ve şifre sıfırlama da Firebase Authentication kullanır.

Firebase Android uygulaması: `com.alpcan.yap`

Kalıcı imza fingerprint'leri:
- SHA-1: `50:76:42:50:AA:9F:48:6F:65:14:F7:CF:4A:68:13:32:86:71:BF:7C`
- SHA-256: `12:D4:43:F3:B2:CC:03:8B:95:CB:E2:46:70:0A:06:23:DA:BB:A6:D9:77:79:34:6E:00:E2:2F:F9:9B:16:5F:B3`

## Derleme
Önerilen ortam: JDK 17, Android SDK 36 ve Gradle 8.13.

GitHub Actions her push'ta projeyi derleyip APK artifact'i üretir. Üretim imza anahtarı güvenlik nedeniyle repoya konulmaz.

## Güvenlik
`*.keystore`, `*.jks` ve imza parolaları **repoya eklenmez**. Uygulama imza anahtarı özel tutulmalıdır.
