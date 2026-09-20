# NusaFit — build Android APK

## Ikon dan splash
Project sudah memakai gambar logo NusaFit yang diberikan sebagai:
- ikon launcher Android;
- ikon adaptive Android 8+;
- ikon splash Android 12+.

Jadi setelah APK dipasang, launcher akan memakai logo tersebut untuk membuka NusaFit.

## Debug APK
Workflow **NusaFit Android Build** membangun APK debug dengan JDK 17 dan Gradle 8.13.

## Release APK — signing yang aman
Jangan membuat keystore baru setiap build. Jika keystore berubah, Android dapat menolak instalasi update karena tanda tangan APK berbeda.

Tambahkan 4 repository secrets berikut di GitHub:

- `NUSAFIT_KEYSTORE_B64` — isi Base64 dari keystore release permanen.
- `NUSAFIT_STORE_PASSWORD` — password keystore.
- `NUSAFIT_KEY_ALIAS` — alias key.
- `NUSAFIT_KEY_PASSWORD` — password key.

Contoh membuat Base64 di Windows PowerShell:

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\nusafit-release.keystore"))
```

Simpan hasilnya sebagai secret `NUSAFIT_KEYSTORE_B64`. Jangan commit keystore atau password ke repository.

Workflow release akan:
1. Checkout project.
2. Menyiapkan JDK 17 dan Gradle 8.13.
3. Memakai keystore permanen dari GitHub Secrets.
4. Build `assembleRelease`.
5. Memverifikasi signature APK dengan `apksigner`.
6. Mengunggah APK sebagai artifact `NusaFit-Signed-Release-APK`.

## Google Maps
Tambahkan repository secret `GOOGLE_MAPS_API_KEY` berisi API key Google Maps Android yang valid. Workflow menginjeksikannya saat build ke resource `maps_key`; key tidak perlu ditulis ke source code. Jika secret kosong, APK tetap dapat dibangun tetapi peta Google Maps tidak akan tampil.

Tanpa API key yang valid, fungsi tracking GPS tetap dapat menyimpan titik lokasi, tetapi tampilan tile Google Maps tidak akan tersedia.
