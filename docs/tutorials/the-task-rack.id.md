# Tutorial: Rak Tugas

<!-- languages -->
[English](the-task-rack.md) · [Español](the-task-rack.es.md) · [Français](the-task-rack.fr.md) · [Deutsch](the-task-rack.de.md) · [Русский](the-task-rack.ru.md) · [Українська](the-task-rack.uk.md) · [Polski](the-task-rack.pl.md) · [Português (Brasil)](the-task-rack.pt.md) · **Bahasa Indonesia** · [Filipino](the-task-rack.tl.md) · [Tiếng Việt](the-task-rack.vi.md) · [简体中文](the-task-rack.zh.md) · [हिन्दी](the-task-rack.hi.md) · [עברית](the-task-rack.he.md) · [العربية](the-task-rack.ar.md)
<!-- /languages -->

Rak Tugas adalah gagasan khas NMOX Studio: perkakas bangun/uji/sajikan Anda
ditata sebagai rak perangkat keras yang Anda sambungkan dengan kabel patch.
Sebuah perangkat menjalankan perintah sungguhan; sebuah kabel membawa sinyal
sungguhan. Tutorial ini menyusun patch kecil — jalankan sesuatu, lalu lihat
keluarannya di monitor — agar metaforanya langsung terasa.

![Rak yang dibidikkan ke proyek sungguhan — perangkat terpasang dan berjalan](../images/task-rack.png)

![Tab membalik rak — kabel patch menyambungkan perangkat di sisi belakang](../images/rack-rear.png)

## Sebelum mulai

Buka sebuah proyek (proyek Node apa pun bisa; `Berkas ▸ Proyek Baru…` →
“Vanilla Web” bila Anda perlu satu). Membuka proyek **membidikkan** rak ke sana,
sehingga setiap perangkat berjalan di direktori proyek itu.

## Langkah-langkah

1. **Buka raknya.** Klik tab **Rak Tugas** (atau tekan `⌘9`). Rak awal berisi
   satu **MONITOR** — perangkat konsol yang menampilkan keluaran perintah dan
   baris galat.

2. **Tambahkan pelari.** Seret **IGNITION** dari rak perangkat di kiri ke rak. IGNITION
   adalah perangkat “jalankan” untuk banyak bahasa; bila dibidikkan ke proyek
   Node ia menjalankan `npm run dev` (ia mendeteksi sendiri pengelola paket dan
   rantai perkakas Anda).

3. **Sambungkan ke monitor.** Klik **Belakang (Tab)** (atau tekan Tab) untuk melihat sisi
   belakang, lalu klik jack **OUT** milik IGNITION dan klik jack **IN** milik
   MONITOR — sebuah kabel patch menyambungkan keduanya. (Menyeret di antara jack
   juga bisa; mengeklik lebih mudah bila raknya lebar.)

4. **Picu.** Balik lagi ke depan dan tekan tombol **IGNITE** milik IGNITION. Ia
   meluncurkan prosesnya; keluaran mengalir ke MONITOR, dan LED status menyala.
   Bila proyeknya belum dipercaya, Anda lebih dulu mendapat konfirmasi kepercayaan
   ruang kerja sekali jalan — itulah penjaga yang mencegah repositori hasil kloning
   menjalankan skripnya tanpa izin Anda.

5. **Simpan patch-nya.** Tombol **Simpan Patch** menulis
   `.nmoxrack.json` di samping proyek Anda. Buka proyeknya lagi nanti dan patch-nya —
   perangkat, kabel, posisi kenop — kembali persis seperti semula.

## Yang baru saja Anda pelajari

- **Perangkat adalah perkakas dengan panel depan.** Kenop memilih opsi, tombol GO
  menjalankan, LED dan LCD melaporkan keadaan — dan setiap kendali sungguhan (tak
  ada kenop mati; sebuah uji kontrak menegakkannya).
- **Kabel mengoordinasikan jalur.** OUT→IN adalah sambungan paling sederhana;
  gerbang kesiapan (`ENABLE`), penghalang gabung (`QUORUM`), dan kabel pemicu
  memungkinkan Anda menyusun seluruh alur yang bereaksi pada dirinya sendiri.
- **Semuanya bertahan.** Patch-nya adalah berkas yang bisa dikomit; rak bahkan
  membangkitkan kembali sesi yang berjalan setelah crash.

## Berikutnya

- Ada 53 perangkat — telusuri di [devices.md](../devices.md) atau rak
  perangkat (klik kanan perangkat yang terpasang untuk **Cara memakai…**-nya).
- Minta [KVASIR](kvasir.id.md) menjelaskan jalannya yang gagal.
- Ekspor patch ke workflow GitHub Actions: **ekspor CI** milik rak.
