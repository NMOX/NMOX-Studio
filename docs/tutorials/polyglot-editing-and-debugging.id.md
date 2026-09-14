# Tutorial: Menyunting banyak bahasa & debugging

<!-- languages -->
[English](polyglot-editing-and-debugging.md) · [Español](polyglot-editing-and-debugging.es.md) · [Français](polyglot-editing-and-debugging.fr.md) · [Deutsch](polyglot-editing-and-debugging.de.md) · [Русский](polyglot-editing-and-debugging.ru.md) · [Українська](polyglot-editing-and-debugging.uk.md) · [Polski](polyglot-editing-and-debugging.pl.md) · [Português (Brasil)](polyglot-editing-and-debugging.pt.md) · **Bahasa Indonesia** · [Filipino](polyglot-editing-and-debugging.tl.md) · [Tiếng Việt](polyglot-editing-and-debugging.vi.md) · [简体中文](polyglot-editing-and-debugging.zh.md) · [हिन्दी](polyglot-editing-and-debugging.hi.md) · [עברית](polyglot-editing-and-debugging.he.md) · [العربية](polyglot-editing-and-debugging.ar.md)
<!-- /languages -->

NMOX Studio menyunting 70+ bahasa dengan penyorotan sintaks sungguhan, kerangka
Navigator, dan kecerdasan server bahasa — dan ia men-debug JavaScript/TypeScript
(juga peramban) sejak awal dengan titik henti yang benar-benar berhenti. Tutorial
ini mengenai sebuah titik henti di aplikasi Node.

![Titik henti JavaScript tercapai: eksekusi berhenti, tumpukan panggilan Node, dan variabel V8 yang hidup](../images/debug-javascript.png)

## Sebelum mulai

Buka (atau buat kerangka) proyek Node kecil dengan skrip yang bisa Anda jalankan,
misalnya rute Express atau `node server.js` biasa.

## Langkah-langkah

1. **Buka sebuah berkas sumber.** Penyorotan, pencocokan kurung, pelipatan kode,
   dan penandaan kemunculan langsung aktif. **Navigator** menampilkan kerangka
   berkas; server bahasa (dipasang lewat petunjuk `Alat ▸ Dokter Lingkungan…`)
   menambahkan pelengkapan dan diagnostik.

2. **Pasang titik henti.** Klik margin penyunting pada sebuah baris di dalam
   handler Anda — sebuah titik henti muncul.

3. **Debug berkasnya.** Jalankan **Debug berkas (titik henti)** (atau
   **Debug di Chrome (titik henti)** untuk halaman HTML/JS). Konfirmasi kepercayaan
   ruang kerja sekali jalan menjaga peluncurannya; lalu adaptor `js-debug` yang
   disertakan menjalankan program Anda.

4. **Kenai titik hentinya.** Picu jalur kodenya (kirim permintaannya, atau biarkan
   skrip mencapai baris itu). Eksekusi **berhenti** di titik henti Anda — periksa
   variabel, telusuri tumpukan panggilan, langkahi atau masuki. Untuk debugging
   peramban, Chrome berprofil sekali pakai terbuka pada URL server pengembangan
   Anda yang hidup dan titik henti halaman dipetakan kembali ke IDE.

## Yang baru saja Anda pelajari

- Penyunting memperlakukan 70+ bahasa sebagai kelas satu (tata bahasa TextMate +
  CSL + LSP); berkas konfigurasi (YAML, TOML, Dockerfile, nginx…) juga tercakup.
- Debugging JS/TS sudah terpasang — sebuah pemultipleks sesi meratakan sesi anak
  js-debug agar debugger sesi tunggal milik platform bisa mengendalikannya.
- Setiap peluncuran debug dijaga kepercayaan dan dimatikan sebagai satu pohon proses
  utuh saat dihentikan (tanpa yatim).

## Berikutnya

- **Jalankan pengujian terfokus** menjalankan satu metode uji per bahasa.
- Diagnostik dari perkakas rak (eslint/tsc/phpstan) mendarat di jendela
  Action Items milik platform.
