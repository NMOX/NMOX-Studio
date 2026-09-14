# Tutorial: Jelaskan apa saja dengan KVASIR

<!-- languages -->
[English](explain-anything.md) · [Español](explain-anything.es.md) · [Français](explain-anything.fr.md) · [Deutsch](explain-anything.de.md) · [Русский](explain-anything.ru.md) · [Українська](explain-anything.uk.md) · [Polski](explain-anything.pl.md) · [Português (Brasil)](explain-anything.pt.md) · **Bahasa Indonesia** · [Filipino](explain-anything.tl.md) · [Tiếng Việt](explain-anything.vi.md) · [简体中文](explain-anything.zh.md) · [हिन्दी](explain-anything.hi.md) · [עברית](explain-anything.he.md) · [العربية](explain-anything.ar.md)
<!-- /languages -->

KVASIR bermula sebagai perangkat rak yang menjelaskan jalannya yang gagal. Kini
ia menjangkau empat tempat — rak, penyunting, Studio API, dan Studio Basis Data —
dan setiap wajahnya mengikuti tiga hukum yang sama: **Anda melihat persis apa yang
akan meninggalkan mesin Anda sebelum apa pun keluar**, **setiap permukaan meminta
persetujuannya sendiri** (menyetujui galat bangunan tidak pernah mengizinkan
pengiriman kode atau SQL), dan **rahasia tidak mungkin ikut terbawa menurut
rancangannya** (pengungkapannya disusun oleh studio pemilik data, dengan tajuk
kredensial dibuang dan kata sandi tidak pernah terjangkau).

![KVASIR menjelaskan jalannya yang benar-benar gagal](../images/kvasir-explain.png)

## Sebelum mulai

Satu kunci mencakup keempat wajah — dari penyedia mana pun yang Anda pilih:
Claude (Anthropic), ChatGPT (OpenAI), atau Gemini (Google). Tekan **KEY…** di
panel depan KVASIR untuk memilih penyedia dan menyimpan kuncinya di gantungan
kunci sistem operasi Anda, atau ekspor `ANTHROPIC_API_KEY`, `OPENAI_API_KEY`, atau
`GEMINI_API_KEY`. Tanpa kunci, tanpa panggilan — setiap wajah mengatakannya dengan jujur.

## Empat wajahnya

1. **Jalannya yang gagal (rak).** Pasang KVASIR, jalankan sesuatu yang gagal,
   tekan **EXPLAIN**. Yang dikirim: perintah, kode keluar, dan sampai lima baris
   galat sampel. Lihat [tutorial
   KVASIR](kvasir.id.md) untuk panduan lengkapnya, termasuk kabel yang otomatis
   menjelaskan kegagalan VERITAS tanpa tangan.

2. **Kode Anda (penyunting).** Pilih kode dalam bahasa apa pun → klik kanan →
   **Ask KVASIR Tentang Seleksi…** lalu ketik pertanyaan. Yang dikirim: pilihan yang
   dibatasi, nama berkas, dan bahasanya — tidak ada lagi dari proyek Anda. Wajah ini
   punya gerbang persetujuan *sendiri*, karena persetujuan alur kegagalan secara
   tegas menjanjikan kode sumber tidak pernah meninggalkan mesin.

3. **Tanggapan API (Studio API).** Setelah mengirim, tekan
   **Jelaskan…**. Yang dikirim: metode, URL dengan nilai kueri disamarkan, status, tajuk
   dengan kredensial dibuang dan dihitung, serta badan yang dibatasi. Berguna begitu
   muncul 401 atau tajuk CORS yang aneh.

4. **Galat basis data (Studio Basis Data).** Pernyataan yang gagal memunculkan tombol
   **Jelaskan…** di bawah pesan galatnya. Yang dikirim: SQL yang Anda jalankan —
   *termasuk nilai literalnya, dan baris persetujuan mengatakannya*, karena galatnya
   biasanya justru tentang sebuah literal — ditambah pesan galat dan jenis mesinnya.
   Tidak pernah sambungan, kata sandi, atau baris data.

Setiap wajah membuka jendela percakapan: ajukan pertanyaan lanjutan, dan model
melihat seluruh riwayat percakapan itu (dibatasi sepuluh putaran, disebutkan di
transkrip). Pilihan **Fast/Deep** (Haiku/Sonnet) diingat, dan tetap per percakapan
sehingga transkrip tidak pernah berbohong tentang siapa yang menjawab.

## Coba dalam dua menit

Studio Basis Data adalah wajah yang paling cepat didemokan: buka ⌥⌘7, buat sambungan
SQLite, jalankan `SELECT * FROM user;` terhadap basis data yang tabelnya bernama
`users`, lalu tekan **Jelaskan…** pada galatnya. Baca dialog persetujuannya sebelum
menyetujui — itulah janji produk ini, dalam satu kalimat.

## Yang baru saja Anda pelajari

- Empat permukaan, satu sambungan: setiap studio menyusun pengungkapannya sendiri
  dan dialog persetujuan mengutipnya kata demi kata.
- Menolak dihormati tanpa suara dan sepenuhnya — tanpa jendela, tanpa panggilan.
- Sebuah hasil milik ruang kerja yang menghasilkannya: berpindah proyek membersihkan
  tanggapan dan tab hasil, sehingga Jelaskan tidak pernah bisa mengungkap data
  proyek sebelumnya.
