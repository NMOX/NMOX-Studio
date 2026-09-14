# Tutorial: KVASIR — penjelas galat berbasis AI

<!-- languages -->
[English](kvasir.md) · [Español](kvasir.es.md) · [Français](kvasir.fr.md) · [Deutsch](kvasir.de.md) · [Русский](kvasir.ru.md) · [Українська](kvasir.uk.md) · [Polski](kvasir.pl.md) · [Português (Brasil)](kvasir.pt.md) · **Bahasa Indonesia** · [Filipino](kvasir.tl.md) · [Tiếng Việt](kvasir.vi.md) · [简体中文](kvasir.zh.md) · [हिन्दी](kvasir.hi.md) · [עברית](kvasir.he.md) · [العربية](kvasir.ar.md)
<!-- /languages -->

KVASIR adalah perangkat rak yang membaca jalannya terakhir Anda yang gagal lalu
bertanya kepada AI Anda — Claude, ChatGPT, atau Gemini — apa yang salah. Ini
bantuan AI dengan cara rak: satu tombol, gerbang persetujuan yang jelas, dan LCD
yang jujur — tidak ada berkas proyek atau rahasia yang dikirim, hanya konteks
kegagalan yang terbatas.

![KVASIR menjelaskan jalannya yang benar-benar gagal: diagnosis yang dijaga persetujuan di panel depan dan langkah perbaikan lengkap di penampil](../images/kvasir-explain.png)

## Sebelum mulai

Anda butuh kunci API dari salah satu dari tiga penyedia yang dilayani KVASIR:
Anthropic (Claude), OpenAI (ChatGPT), atau Google (Gemini). Tekan **KEY…** di
panel depan untuk memilih penyedia dan menyimpan kuncinya di gantungan kunci
sistem operasi, atau ekspor variabel lingkungan milik penyedia itu —
`ANTHROPIC_API_KEY` / `CLAUDE_API_KEY`, `OPENAI_API_KEY` /
`CHATGPT_API_KEY`, atau `GEMINI_API_KEY` / `GOOGLE_API_KEY`. Pilihan penyedia
berlaku untuk setiap wajah KVASIR dan juga ada di Opsi ▸ Rack & Awan.

## Langkah-langkah

1. **Buat sebuah kegagalan.** Jalankan sesuatu yang gagal — bangunan dengan galat
   sintaks, uji yang melempar pengecualian. Perekam penerbangan rak menangkap
   perintahnya, kode keluarnya, dan sampai lima baris galat sampel.

2. **Pasang KVASIR** dari palet (kategori OBSERVE) dan tekan **EXPLAIN**.

3. **Beri persetujuan (pertama kali).** KVASIR punya dialog persetujuan sekali
   jalannya sendiri, per penyedia, yang menyebut vendor penerima data dan merinci
   persis apa yang meninggalkan mesin Anda: perintah yang gagal, kode keluarnya,
   ≤5 baris galat, nama perangkat, dan nama proyek — dan tidak ada yang lain (tanpa
   kode sumber, tanpa lingkungan, tanpa rahasia). Kepercayaan ruang kerja menjaga
   *menjalankan* kode; aliran data keluar ini punya gerbangnya sendiri.

4. **Baca putusannya.** Diagnosis singkat muncul di LCD multibaris; penjelasan
   lengkapnya terbuka di jendela sembul. Kenop **MODEL** memilih FAST (bawaan) atau
   DEEP — Haiku / Sonnet, GPT-5 mini / GPT-5, atau Gemini Flash / Pro, sesuai
   penyedia yang Anda pilih.

## Yang baru saja Anda pelajari

- KVASIR tidak memakan apa pun saat boot dan tidak melakukan panggilan jaringan tanpa
  tombol ditekan — gerbang kunci dan gerbang persetujuan sama-sama ditegakkan.
- Kuncinya hanya menumpang pada tajuk autentikasi penyedia (`x-api-key`,
  `Authorization: Bearer`, `x-goog-api-key`) — tidak pernah di URL, badan, atau log.
- Kunci tidak pernah berpindah antarpenyedia, dan persetujuan berlaku per penyedia:
  “ya” untuk Anthropic bukan “ya” untuk Google atau OpenAI.
- Penurunan kemampuannya jujur: tanpa kunci, tanpa persetujuan, tak ada yang perlu
  dijelaskan, luring, dan penolakan masing-masing menampilkan pesan LCD yang jelas.

## Berikutnya

- Rangkai tanpa tangan: kabel `VERITAS FAIL → KVASIR EXPLAIN` otomatis menjelaskan
  jalannya uji yang gagal (jalur kabel tidak pernah meminta konfirmasi dan dibatasi
  sekali per 30 detik); OUT-nya mengalir ke MONITOR/PHOSPHOR.
