# NusaFit Android v1.1.0 — Modern Professional

Versi ini memperbarui UI mengikuti konsep mockup modern NusaFit dan memperbaiki navigasi serta kestabilan tracking.

## Perbaikan utama
- Bottom navigation: Beranda, Riwayat, Peta, Statistik, Profil.
- Menu tidak lagi hanya bergantung pada dialog; setiap menu membuka layar yang sesuai.
- Peta dibuat ulang dengan fragment transaction yang aman dan `commitNowAllowingStateLoss`.
- Broadcast receiver didaftarkan/dilepas secara aman agar tidak menyebabkan crash.
- Tracking GPS menggunakan Foreground Service bertipe `location`.
- Tracking tetap berjalan ketika pengguna menekan tombol Home.
- Service tidak dihentikan ketika task aplikasi di-swipe dari Recents (`stopWithTask=false`).
- Status tracking disimpan sehingga UI dapat memulihkan status setelah Activity dibuat ulang.
- Notifikasi tracking permanen dapat diketuk untuk kembali ke NusaFit.
- Tombol pengaturan baterai membantu mengurangi pembatasan background dari sistem/ROM perangkat.
- Riwayat dan statistik dapat dibuka dari bottom navigation.
- Versi aplikasi dinaikkan ke 1.1.0 / versionCode 2.

## Catatan GPS di HP
Untuk tracking yang stabil:
1. Beri izin Lokasi.
2. Izinkan notifikasi jika diminta.
3. Mulai tracking ketika NusaFit sedang terbuka.
4. Setelah tracking dimulai, tekan Home. NusaFit akan tetap berjalan melalui Foreground Service dan menampilkan notifikasi tracking.
5. Pada perangkat tertentu seperti Vivo, aktifkan izin berjalan di latar belakang / tanpa pembatasan baterai untuk NusaFit jika sistem masih menghentikan layanan.

## Build GitHub Actions
Project tetap menggunakan GitHub Actions yang sudah ada. Workflow membangun Debug APK dan Signed Release APK menggunakan Gradle 8.13 dan JDK 17.

## Profil & Kalkulator Kesehatan v1.2.0
- Profil sekarang menyimpan jenis kelamin, tanggal lahir, usia yang dihitung otomatis, tinggi badan, dan berat badan.
- Halaman Profil menampilkan ringkasan BMI dengan visual gauge modern untuk pengguna dewasa.
- Untuk pengguna di bawah 18 tahun, aplikasi tidak memberikan target berat dewasa; penilaian pertumbuhan diarahkan ke BMI menurut usia/jenis kelamin dan kurva pertumbuhan yang sesuai.
