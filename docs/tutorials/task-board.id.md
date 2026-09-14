# Tutorial: Papan Tugas dan sprint

<!-- languages -->
[English](task-board.md) · [Español](task-board.es.md) · [Français](task-board.fr.md) · [Deutsch](task-board.de.md) · [Русский](task-board.ru.md) · [Українська](task-board.uk.md) · [Polski](task-board.pl.md) · [Português (Brasil)](task-board.pt.md) · **Bahasa Indonesia** · [Filipino](task-board.tl.md) · [Tiếng Việt](task-board.vi.md) · [简体中文](task-board.zh.md) · [हिन्दी](task-board.hi.md) · [עברית](task-board.he.md) · [العربية](task-board.ar.md)
<!-- /languages -->

Papan Tugas adalah kanban per proyek yang tinggal di satu berkas —
`.nmoxtasks.json` di samping kode Anda — dan semua hal lain yang
dilakukan papan ini diturunkan dari berkas itu: dasbor, jam kerja,
standup harian, dan burndown sprint. Tidak ada pembukuan yang Anda
catat dengan tangan; cap waktu pada kartu itu sendirilah catatannya.
Tutorial ini membawa sebuah papan dari tiga kartu sampai sprint yang
ditutup, sekali duduk.

![Papan Tugas: tiga kolom, kartu yang jamnya berjalan, dan penghitung hidup di kepala papan](../images/task-board.png)

![Sebuah sprint di Ikhtisar papan — burndown di atas garis ideal](../images/sprint-overview.png)

## Sebelum mulai

Buka sebuah proyek (proyek apa saja — papan ini tidak peduli
perkakasnya). Bila proyeknya repositori git, Standup juga bisa membaca
komit Anda; bila tidak, bagian itu memang tidak pernah muncul.

## Langkah

1. **Buka papannya.** `⌥⌘1` (atau `Jendela ▸ Papan Tugas`). Tekan
   **Kartu Baru…** tiga kali dan beri judul pada setiap kartu. Kartu
   dipindahkan dengan menyeret, atau dengan papan tik: dengan kartu
   terpilih, **⌘←/⌘→** memindahkannya satu kolom dan **⌘↑/⌘↓**
   mengurutkannya ulang; **Enter** menyunting, **Delete** menghapus
   (setelah bertanya, dengan Tidak sebagai pilihan bawaan), **N**
   memulai kartu baru di kolom itu. Menu di kepala setiap kolom
   mengganti namanya, menetapkan **batas WIP anjuran** (kepalanya
   memerah bila terlampaui — ia tidak pernah menghalangi pemindahan),
   mengacaknya, atau menghapusnya.

2. **Mulai jam.** Seret satu kartu ke kolom tengah, klik kanan →
   **Mulai Jam**. Tanda ⏱ muncul di kartu dan kepala papan menampilkan
   waktu yang sedang berjalan. Hanya satu jam yang berjalan pada satu
   waktu — memulai jam di kartu lain menutup sesi ini — dan sesi yang
   lebih pendek dari semenit dibuang utuh, jadi klik yang tak sengaja
   tidak pernah terhitung sebagai kerja. **Hentikan Jam**
   menghentikannya.

3. **Tambahkan detail yang dibutuhkan standup.** Klik kanan sebuah
   kartu → **Atur Label…** untuk menandainya dengan sebuah epik, dan
   pada kartu lain **Tandai Terhambat…** — seorang pemilik dan tindakan
   yang melepaskan hambatannya (tindakan itu wajib: hambatan tanpa
   tindakan adalah keluhan, bukan rencana). Kartunya memakai ⛔;
   **Buka Hambatan** membersihkannya, begitu pula menyelesaikan kartu.

4. **Baca Ikhtisar.** Tekan **Ikhtisar** di bilah alat. Berkas yang
   sama menjadi dasbor: kartu di papan, **WIP SEKARANG** (hanya kolom
   tengah), yang selesai hari ini dan pekan ini, daftar WIP per kolom
   dengan putusan merah bila melebihi, **ALUR** 14 hari, kartu belum
   selesai yang paling tua beserta umurnya, legenda **EPIK** yang
   diturunkan dari label yang dipakai, **DAFTAR HAMBATAN** (yang paling
   lama tertahan lebih dulu), catatan **RETRO** tingkat papan
   (**Sunting Retro…**), dan laporan **WAKTU** — tercatat hari ini dan
   tujuh hari terakhir, lalu satu baris per kartu, yang terbanyak hari
   ini lebih dulu. Sesi yang melewati tengah malam dipotong per hari
   kalender, jadi angka hari ini adalah kerja hari ini.

5. **Selesaikan sesuatu.** Matikan **Ikhtisar** dan pindahkan sebuah
   kartu ke kolom terakhir. Saat itu dicap sebagai waktu selesai kartu
   (memindahkannya keluar lagi membatalkan selesainya, dan riwayat
   melupakannya). Setiap angka selesai di Ikhtisar berasal dari cap-cap
   ini.

6. **Mulai sprint.** Tekan **Sprint… ▸ Mulai Sprint…**, beri nama, dan
   terima rentang dua pekan (tanggal ditulis `YYYY-MM-DD`; rentang
   terbalik atau bukan tanggal ditolak dengan lantang dan tidak ada yang
   berubah). Beralih ke **Ikhtisar**: ia mendapat kepala sprint dan
   **burndown** yang disusun ulang dari cap selesai kartu — garis redup
   adalah idealnya, garis terang adalah yang terjadi, dan masa depan
   dibiarkan tak tergambar.

7. **Tulis standup.** Tekan **Standup…**. Laporannya terbuka sebagai
   markdown dengan tombol **Salin ke Papan Klip**: **Kemarin** dan
   **Hari ini** dari cap selesai dan sesi yang dipotong per hari (jam yang
   masih berjalan terbaca “jam berjalan”), **Hambatan** dari daftar
   hambatan, **Commit (sejak kemarin)** dari `git log`. Bagian yang
   tidak punya isi dihilangkan, tidak pernah ditampilkan kosong, dan
   kepalanya dibuka dengan sprint dan hitungan harinya (“Sprint 8 · hari
   3 dari 14”).

   ![Sekali klik mengubah papan menjadi laporan harian](../images/standup.png)

8. **Tutup sprint.** **Sprint… ▸ Laporan Sprint…** adalah saudara
   tinjauan bagi Standup — yang selesai, yang masih terbuka saat
   ditutup, yang masih terhambat, waktu tercatat dalam rentangnya,
   catatan retro — dan **Sprint… ▸ Tutup Sprint…** mengarsipkan
   rentang, jumlah selesai, dan retro untuk velositas. Kartu tetap
   persis di tempatnya: menutup itu pembukuan, bukan bersih-bersih.
   Penutupan lalu menawarkan sprint berikutnya yang sudah terisi (nama
   dinaikkan, rentang sama panjang yang dimulai sehari sesudahnya),
   sepenuhnya bisa disunting, dan Batal tidak memulai apa pun. Begitu
   ada riwayat, dialog Sprint menampilkan angka perencanaannya —
   “Velositas — 3 sprint terakhir: …” — dan laporannya mendapat baris
   velositas.

## Yang baru saja Anda pelajari

- **Satu berkas adalah seluruh catatannya.** Komit `.nmoxtasks.json`
  dan tim berbagi papan, retro, dan riwayat sprint; abaikan dan ia
  tetap pribadi. Judul kartu selalu ditampilkan sebagai karakter polos,
  jadi papan yang di-check-in tidak bisa menyelundupkan markup.
- **Papan mengikuti berkasnya ke dua arah.** Sunting dengan tangan,
  tarik push rekan, atau checkout cabang lain, dan papan yang terlihat
  diperbarui dalam sekitar satu setengah detik — suntingan dari luar
  mengalahkan gerakan yang basi, dan baris status mengatakannya.
- **Bahaya merge sembuh saat dimuat.** Id kartu ganda, sesi jam terbuka
  yang tersesat, dan rentang sprint yang rusak diperbaiki ketika
  berkasnya dibaca, jadi merge “simpan keduanya” tidak bisa
  menggelembungkan laporan atau meracuni seremoninya.
- **Semua yang diturunkan dinyatakan.** WIP, rentang selesai, burndown,
  dan pemotongan WAKTU adalah definisi yang bisa Anda baca di Panduan
  pengguna, bukan heuristik.

## Selanjutnya

- Judul kartu, label epik, dan kueri harfiah `blocked` semuanya bisa
  dijangkau dari `⌘I` — lihat [tutorial Meja Kerja](workbench.id.md)
  untuk kebiasaan mencari segalanya.
- Tempel Standup ke obrolan, lalu lanjutkan: [Mempertunjukkannya di
  depan ruangan](show-it-to-a-room.id.md) membahas Salin sebagai
  Markdown dan keluarga tangkapan layar.
- Definisi lengkapnya ada di [bagian Papan Tugas dalam Panduan
  pengguna](../user-guide.id.md).
