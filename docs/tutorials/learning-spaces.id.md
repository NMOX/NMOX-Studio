# Tutorial: Ruang belajar

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · **Bahasa Indonesia** · [Filipino](learning-spaces.tl.md) · [Tiếng Việt](learning-spaces.vi.md) · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![Pemilih Ruang Belajar Baru — cari di antara tutorial bawaan, dengan penjajakan ketersediaan yang memberi tahu sejak awal apakah mesin ini punya perkakas ruang tersebut](../images/tabs/learning-spaces.png)

Ruang belajar adalah kotak pasir mandiri untuk mempelajari sebuah bahasa,
kerangka kerja, atau pustaka: NMOX Studio membangkitkan kode contoh, tutorial
yang dituntun, dan rak yang sudah terangkai dengan **REPL sungguhan di dalam rak**
tempat Anda mengetik. Ada 93 yang bawaan.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## Membukanya

`Berkas ▸ Ruang Belajar Baru…` (peluncurnya mendaftar setiap ruang bawaan).

## Langkah-langkah

1. **Pilih sebuah ruang.** Pilih salah satu — Python, Rust, Solid, htmx, Solidity,
   Elm, REPL untuk bahasa sistem, ruang E2E/Playwright, dan seterusnya. Pemilihnya
   lebih dulu menjajaki apakah penafsir/rantai perkakasnya tersedia.

2. **Biarkan ia membangkitkan.** NMOX Studio membuat ruang itu di
   `~/.nmox/learn/<slug>`: contoh minimal yang berjalan ditambah tutorial yang
   menuntun Anda melaluinya, dengan rujukan ke konsol atau perangkat yang relevan.

3. **Ketik di REPL.** Rak yang sudah terangkai memuat perangkat **REPL** yang kenop
   ENGINE-nya disetel untuk bahasa ruang tersebut (satu mesin per bahasa REPL di katalog, masing-masing dengan
   bendera paksa-interaktif yang sudah terisi). Ketik sebuah ungkapan, tekan
   Enter — keluarannya mengalir ke layar REPL. Penafsirnya tidak ada? Tombol
   **INSTALL** memasangnya dari rak.

4. **Ikuti tutorialnya.** Kerjakan langkah-langkahnya; kode contohnya nyata dan bisa
   dijalankan, dan ruang itu bebas Anda ubah.

## Yang baru saja Anda pelajari

- Ruang belajar adalah proyek utuh + tutorial + rak terangkai, bukan sekadar potongan kode.
- REPL-nya adalah proses interaktif sungguhan, bukan putar ulang yang direkam.
- Anda bisa menambahkan milik Anda sendiri: letakkan `*.json` di
  `~/.nmox/learn-catalog.d/` dan ia ikut masuk ke pemilih (lihat
  [learning-spaces.md](../learning-spaces.md) untuk skemanya).

## Berikutnya

- Ruang kerangka kerja (Astro/SvelteKit/Nuxt/Next) mengarah ke konsol raknya
  (COSMOS/KINETIC/NIMBUS/NEXUS).
