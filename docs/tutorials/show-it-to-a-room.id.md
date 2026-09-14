# Tutorial: Mempertunjukkannya di depan ruangan

<!-- languages -->
[English](show-it-to-a-room.md) · [Español](show-it-to-a-room.es.md) · [Français](show-it-to-a-room.fr.md) · [Deutsch](show-it-to-a-room.de.md) · [Русский](show-it-to-a-room.ru.md) · [Українська](show-it-to-a-room.uk.md) · [Polski](show-it-to-a-room.pl.md) · [Português (Brasil)](show-it-to-a-room.pt.md) · **Bahasa Indonesia** · [Filipino](show-it-to-a-room.tl.md) · [Tiếng Việt](show-it-to-a-room.vi.md) · [简体中文](show-it-to-a-room.zh.md) · [हिन्दी](show-it-to-a-room.hi.md) · [עברית](show-it-to-a-room.he.md) · [العربية](show-it-to-a-room.ar.md)
<!-- /languages -->

Ada hari-hari ketika hasil kerjanya bukan kode — melainkan
*mempertunjukkannya*: proyektor, README, komentar di issue, slide. NMOX
Studio punya perlengkapan presentasi kecil untuk orang semacam itu, dan
setiap bagiannya menumpang pada sesuatu yang sudah dimiliki IDE, bukan
tempelan: zoom teks penyunting sendiri, satu kosakata bahasa yang
menamai pagar kode, dan lukisan milik bengkel dokumentasi. Tutorial ini
menyusuri semuanya sekali duduk, dari barisan belakang sampai papan
klip.

![Mode presentasi menyala: templat Angular dan jendela Output sama-sama +10 pt, dikembalikan persis ketika modenya dimatikan](../images/presentation-mode.png)

![Tab penyunting saja, disimpan dengan Simpan Tangkapan Layar Editor… pada 2x](../images/editor-screenshot-2x.png)

## Sebelum mulai

Buka proyek yang tinggal di repositori GitHub (gerakan tautan butuh
`origin` di GitHub — selain itu ditolak dengan lantang, bukan ditebak)
dan buka salah satu berkas sumbernya. Simpan berkasnya: gerakan tautan
juga menolak buffer dengan perubahan yang belum disimpan, karena blok
yang tidak cocok dengan tautannya adalah kebohongan. Untuk langkah 2,
jalankan juga proyeknya (▶ di bilah alat atau lewat rak) supaya ada
halaman di peramban bawaan dan keluaran di jendela Output.

## Langkah

1. **Buat ruangan bisa membacanya.** `Tampilan ▸ Mode presentasi`.
   Setiap penyunting yang terbuka membesar sepuluh poin, seketika,
   begitu pula penyunting yang Anda buka selama mode menyala. Butir
   menunya bercentang dan baris status menyebut penambahannya. Tidak
   ada yang ditulis ke pengaturan Anda — matikan (atau mulai ulang) dan
   hurufnya persis seperti semula, termasuk penyesuaian halus lewat
   ⌥-roda yang Anda tambahkan di atasnya.

2. **Lihat bagian IDE lainnya ikut.** Selama mode menyala, halaman di
   peramban bawaan diperbesar menjadi 150% dari zoom yang Anda pakai,
   teks jendela Output membesar sepuluh poin yang sama, dan setiap
   Terminal yang terbuka ikut membesar — masing-masing kembali ke
   ukurannya sendiri saat keluar. Demo aplikasi yang berjalan,
   keluarannya, dan shell tempat Anda mengetik terbaca dari barisan
   belakang, bukan hanya kodenya.

3. **Perlihatkan tangan Anda.** `Tampilan ▸ Tampilkan ketukan tombol`,
   lalu tekan `⌘S`. Sebuah pil gelap bertuliskan `⌘S` muncul besar di
   bagian bawah jendela sebentar (tekanan berulang terbaca `⌘Z ×3`).
   Sekarang ketik sebuah kata: tidak ada yang muncul. Hanya kombinasi
   dengan ⌘, ⌃ atau ⌥ serta tombol fungsi dan Escape yang pernah
   tampil — ketikan biasa tidak pernah, jadi kata sandi yang diketik di
   terminal tidak mungkin berakhir di proyektor.

4. **Bagikan kodenya.** Pilih beberapa baris lalu pilih `Edit ▸ Salin
   sebagai Markdown` (atau klik kanan di penyunting). Tempel ke README,
   issue, atau obrolan: blok berpagar yang diberi label bahasa berkasnya
   (` ```html `, ` ```typescript `, ` ```bash `…), diakhiri tepat satu
   baris baru, dengan pagar yang lebih panjang bila potongannya sendiri
   memuat tiga backtick sehingga tampil utuh. Bila tak ada yang dipilih,
   seluruh berkas disalin. Baris status menyebut berapa baris dan label
   apa.

5. **Katakan di mana ia berada.** Pilihan yang sama, `Edit ▸ Salin
   sebagai Markdown dengan tautan` (atau klik kanan). Hasil tempelnya
   adalah blok yang sama diikuti
   `[src/app/app.ts#L3-L12](https://github.com/you/repo/blob/main/src/app/app.ts#L3-L12)`
   — cabang yang sedang Anda checkout (HEAD terlepas menaut lewat
   komit), karena komit lokal yang tak pernah di-push akan menjadi 404
   yang menyamar sebagai permalink. Berkas di luar repositori,
   repositori tanpa `origin`, origin yang bukan GitHub, atau perubahan
   yang belum disimpan: baris status menolak dan tidak menyalin apa pun.

6. **Ambil gambarnya.** `Alat ▸ Salin Tangkapan Layar Editor` menaruh
   tab terpilih di area penyunting — bilah alat, gutter, kode, bilah
   samping, tanpa bingkai IDE — ke papan klip sebagai gambar 2x;
   tempelkan langsung ke obrolan atau slide. Ia mengambil tab yang
   sedang Anda lihat bahkan saat fokus ada di Navigator, dan bila tak
   ada yang terbuka di area penyunting ia mengatakannya alih-alih
   menyalin gambar kosong. `Alat ▸ Simpan Tangkapan Layar Editor…`
   menyimpan bidikan yang sama sebagai PNG yang dinamai menurut
   dokumennya (`app.ts-<stamp>.png`), dan `Alat ▸ Simpan Tangkapan
   Layar…` menyimpan seluruh jendela IDE (`nmox-studio-<stamp>.png`,
   secara bawaan di Pictures). Karena IDE melukis dirinya sendiri, tidak
   ada izin perekaman layar yang perlu diberikan, tidak ada desktop di
   dalam bingkai, dan tidak ada yang perlu dipotong.

7. **Tempel pohonnya.** `Alat ▸ Salin Pohon Proyek sebagai Markdown`.
   Tata letak proyek yang dituju mendarat sebagai pohon bergaris kotak
   berpagar seperti yang ditampilkan README: direktori lebih dulu,
   `node_modules/ …` dan saudara-saudaranya yang berat disebut tetapi
   tidak pernah dimasuki, pohon yang dalam atau sangat besar dibatasi
   dengan sisanya dihitung alih-alih dibuang diam-diam, dan berkas
   `.nmox*.json` milik IDE sendiri ditinggalkan karena itu milik
   produk, bukan milik proyek.

8. **Tinggalkan panggung.** `Tampilan ▸ Mode presentasi` sekali lagi.
   Penyunting, peramban, Output, dan setiap terminal kembali persis
   seperti semula; matikan `Tampilan ▸ Tampilkan ketukan tombol`, dan
   pilnya lenyap.

## Yang baru saja Anda pelajari

- **Presentasi adalah keadaan, bukan pengaturan.** Mode presentasi
  hidup dan tidak pernah disimpan — mulai ulang berarti kembali normal —
  dan ia adalah satu keadaan di seluruh produk yang dibalik penyunting
  dan boleh diikuti jendela mana pun.
- **Lapisan itu sengaja sempit.** Tampilkan ketukan tombol hanya
  memantulkan kombinasi dan tombol fungsi; apa yang Anda ketik tidak
  pernah ditampilkan.
- **Salinan yang tidak bisa menjamin dirinya tidak menyalin apa pun.**
  Salin sebagai Markdown dengan tautan menolak setiap anak tangga yang
  tidak bisa ia verifikasi — tanpa origin, bukan GitHub, buffer belum
  disimpan — di baris status, alih-alih menempelkan tautan yang
  berbohong.
- **Setiap berbagi itu terbatas dan polos.** Pohonnya tidak pernah
  mengikuti symlink, tidak pernah masuk ke direktori berat, membatasi
  apa yang ia daftar dan menghitung sisanya; tangkapan layar penyunting
  adalah gambar dan hanya gambar.

## Selanjutnya

- Pertunjukkan juga aplikasi yang berjalan dari barisan belakang:
  [Dari peramban ke sumber](browser-to-source.id.md) menyusuri peramban
  bawaan dan DevTools-nya.
- Standup yang Anda tempel ke obrolan berasal dari [Papan Tugas dan
  sprint](task-board.id.md).
- Catatan rilis untuk sebuah unggahan dimulai dari `Bantuan ▸ Apa yang
  Baru…`, lalu tombol **Salin sebagai Markdown**-nya; seluruh bagian
  tentang presentasi ada di [Panduan
  pengguna](../user-guide.id.md).
