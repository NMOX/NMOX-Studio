# Tutorial: Studio API

<!-- languages -->
[English](api-studio.md) · [Español](api-studio.es.md) · [Français](api-studio.fr.md) · [Deutsch](api-studio.de.md) · [Русский](api-studio.ru.md) · [Українська](api-studio.uk.md) · [Polski](api-studio.pl.md) · [Português (Brasil)](api-studio.pt.md) · **Bahasa Indonesia** · [Filipino](api-studio.tl.md) · [Tiếng Việt](api-studio.vi.md) · [简体中文](api-studio.zh.md) · [हिन्दी](api-studio.hi.md) · [עברית](api-studio.he.md) · [العربية](api-studio.ar.md)
<!-- /languages -->

Studio API adalah meja kerja REST ala Postman yang menyatu di dalam IDE. Anda
menyusun permintaan, menjalankan asersi terhadap tanggapannya, dan — yang tidak
dimiliki alat lain — setiap tanggapan dinilai terhadap standar tajuk keamanan web.

![200 yang hidup dalam 331 ms — dan tab Standar menilai tajuk keamanan tanggapannya](../images/api-studio.png)

## Membukanya

`⌥⌘8`, atau baris **Studio API** di kolom PERKAKAS halaman Selamat Datang.

## Langkah-langkah

1. **Buat permintaan.** Di penyusun permintaan, setel metode ke `GET` dan URL ke
   `https://httpbin.org/json`. Tekan **Kirim**. Badan tanggapan tiba dalam format
   rapi; baris status menampilkan kode, waktu, dan ukuran. (Tanggapan yang tak
   terkendali tidak bisa mencelakai Anda — badannya dialirkan lewat batas 8 MB.)

2. **Tambahkan asersi.** Di tab **Pengujian** tambahkan `Status is 200` dan
   `Body contains slideshow`. Kirim lagi — setiap asersi menampilkan ✓ hijau atau
   ✗ merah beserta nilai sebenarnya.

3. **Baca nilai keamanannya.** Buka tab **Standar**. Studio API menilai HSTS, CSP,
   X-Content-Type-Options, perlindungan clickjacking, Referrer-Policy, dan lainnya,
   lalu memberi nilai huruf — pemeriksaan yang dijalankan pengembang web 2026 di
   securityheaders.com, menyatu dalam setiap pengiriman.

4. **Pakai variabel.** Buat lingkungan dengan `base =
   https://httpbin.org`, lalu setel URL permintaan ke `{{base}}/get`.
   Ganti lingkungan untuk mengarahkan ulang semua permintaan sekaligus. Bila rak
   punya server pengembangan yang hidup, Studio API bahkan menawarkan URL-nya
   sebagai `{{baseUrl}}`.

5. **Tambahkan autentikasi dengan aman.** Di tab **Auth** pilih Bearer atau Basic
   dan masukkan token. Token itu **tidak pernah** ditulis ke `.nmoxapi.json` yang
   bisa dikomit — ia tinggal di gantungan kunci sistem operasi, terikat pada
   permintaannya.

6. **Impor yang sudah Anda punya.** Tombol **Impor…** membaca perintah curl yang
   ditempel (“Copy as cURL” dari devtools peramban), berkas permintaan
   `.http`/`.rest`, atau spesifikasi OpenAPI 3 (JSON atau YAML) — masing-masing
   menjadi permintaan sungguhan, dan tajuk `Authorization` diangkat langsung ke
   medan Auth yang didukung gantungan kunci alih-alih mendarat di berkas ruang
   kerja Anda. **Salin curl** berjalan ke arah sebaliknya: perintah persis yang
   akan dijalankan Kirim, di papan klip Anda.

7. **Tanyakan tanggapan yang buruk kepada KVASIR.** Ketika sebuah pengiriman kembali
   dengan hasil yang salah, tekan **Jelaskan…**. Dialog persetujuan lebih
   dulu memberi tahu persis apa yang akan meninggalkan mesin Anda — metode, URL
   dengan *nilai* kueri disamarkan, status, tajuk yang aman (tajuk kredensial
   sudah dibuang dan dihitung), dan badan yang dibatasi — dan tidak ada yang dikirim
   sampai Anda setuju. Tolak, dan tidak ada yang berjalan; setujui, dan
   penjelasannya terbuka sebagai percakapan tempat Anda bisa bertanya lanjutan.

## Yang baru saja Anda pelajari

- Permintaan, lingkungan, dan asersi bertahan per proyek di `.nmoxapi.json`
  (tanpa rahasia).
- Nilai keamanan mengubah “apakah berhasil” menjadi “apakah aman”.
- Pengiriman bisa dibatalkan (tombol Kirim berubah menjadi **Batal**) dan tidak
  pernah menghalangi bagian IDE lainnya.

## Berikutnya

- Arahkan permintaan ke server rak yang sedang berjalan lewat tawaran `{{baseUrl}}`.
- Lihat [Studio Basis Data](db-studio.id.md) untuk padanannya di basis data.
