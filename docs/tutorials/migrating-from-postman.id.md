# Tutorial: Pindah dari Postman (juga Insomnia, dan peramban)

<!-- languages -->
[English](migrating-from-postman.md) · [Español](migrating-from-postman.es.md) · [Français](migrating-from-postman.fr.md) · [Deutsch](migrating-from-postman.de.md) · [Русский](migrating-from-postman.ru.md) · [Українська](migrating-from-postman.uk.md) · [Polski](migrating-from-postman.pl.md) · [Português (Brasil)](migrating-from-postman.pt.md) · **Bahasa Indonesia** · [Filipino](migrating-from-postman.tl.md) · [Tiếng Việt](migrating-from-postman.vi.md) · [简体中文](migrating-from-postman.zh.md) · [हिन्दी](migrating-from-postman.hi.md) · [עברית](migrating-from-postman.he.md) · [العربية](migrating-from-postman.ar.md)
<!-- /languages -->

Studio API membaca berkas yang sudah Anda punya: koleksi atau lingkungan Postman,
ekspor Insomnia v4 (struktur ruang kerja dan `{{ _.templates }}` diterjemahkan),
tangkapan HAR dari devtools, perintah curl, berkas `.http`, spesifikasi OpenAPI.
Panduan ini membawa ekspor Postman sungguhan dari awal sampai akhir — dan
menunjukkan satu hal yang sengaja dilakukan NMOX Studio secara berbeda:
**rahasia mendarat di gantungan kunci sistem operasi Anda, tidak pernah di berkas
yang bisa dikomit.**

![Studio API, tempat hasil impor mendarat: pohon koleksi, permintaan yang terkirim, dan nilai tajuk keamanannya](../images/api-studio.png)

## Sebelum mulai

Ekspor koleksi Anda dari Postman: collection ▸ … ▸ Export ▸
**Collection v2.1**. (Ekspor v1 ditolak dengan perbaikannya dijelaskan —
ekspor ulang sebagai v2.1.) Lingkungan diekspor terpisah dan diimpor lewat
**Impor… ▸ Lingkungan Postman…** — nilai biasa ikut masuk, impor dengan nama yang
sama digabung tanpa menimpa apa yang sudah Anda setel, dan nilai yang ditandai
Postman sebagai *secret* tetap di luar dengan catatan yang menunjuk ke medan Auth
yang didukung gantungan kunci, karena lingkungan Studio API tinggal di
`.nmoxapi.json` yang bisa dikomit.

## Langkah-langkah

1. **Buka Studio API** (⌥⌘8) dan tekan **Impor… ▸ Koleksi Postman…**. Pilih `.json`
   hasil ekspor Anda.

2. **Periksa apa yang masuk.** Folder mempertahankan identitasnya sebagai nama
   “Folder / Permintaan”. `{{variables}}` Postman diimpor *apa adanya* — itu memang
   sintaks Studio API sendiri — dan variabel koleksi bergabung ke lingkungan aktif
   Anda tanpa menimpa apa pun yang sudah Anda setel. Variabel jalur `:id` menjadi
   `{{id}}`.

3. **Lihat tab Auth pada permintaan yang tadinya punya token bearer.** Tokennya
   *ada* — tetapi ia masuk lewat medan Auth yang didukung gantungan kunci, bukan
   baris tajuk. Komit `.nmoxapi.json` dengan tenang; rahasianya tidak ada di sana.
   Apa pun yang tidak bisa diwakili impornya (badan multipart, skrip) disebutkan di
   baris status, tidak pernah dirusak diam-diam.

4. **Impor tangkapan peramban.** Di tab Network devtools, “Save all as HAR”, lalu
   **Impor… ▸ Tangkapan HAR…**. Hanya lalu lintas XHR/fetch Anda yang diimpor (aset
   halaman dihitung dan disebutkan), cookie sesi dibuang — cookie yang tertangkap
   adalah kredensial — dan `Authorization` yang terekam entah dipindah ke gantungan
   kunci (Bearer/Basic) atau dibuang dan dihitung (apa pun yang buram).

5. **Kirim satu.** Pilih permintaan hasil impor, isi `{{baseUrl}}` di lingkungan Anda
   bila perlu, tekan **Kirim** — dan sekalian baca nilai tajuk keamanannya di tab
   Standar.

6. **Ke arah sebaliknya.** **Impor… ▸ Ekspor koleksi ke .http…** menulis seluruh
   koleksi dalam dialek REST Client untuk penyunting atau runner CI mana pun.
   Autentikasi sengaja tidak dimasukkan ke berkas; setiap permintaan yang
   berautentikasi membawa komentar yang menyebut apa yang perlu ditambahkan lagi.

## Yang baru saja Anda pelajari

- Pindah cukup lewat satu menu: curl / `.http` / OpenAPI / Postman / HAR masuk,
  `.http` keluar.
- Hukum rahasia berlaku di setiap perbatasan: masuk ke gantungan kunci, tetap di
  gantungan kunci.
- Penolakan disebutkan, tidak pernah diam — bila ada yang tidak terimpor, baris
  status mengatakan apa dan mengapa.
