# Minggu Kedua

<!-- languages -->
[English](the-second-week.md) · [Español](the-second-week.es.md) · [Français](the-second-week.fr.md) · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · [Português (Brasil)](the-second-week.pt.md) · **Bahasa Indonesia** · [Filipino](the-second-week.tl.md) · [Tiếng Việt](the-second-week.vi.md) · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Commit, tinjau, selesaikan, usulkan — tanpa pindah ke alat lain.*

Jam pertama adalah membuka proyek lalu menjalankannya. Minggu kedua
adalah segala sesuatu di sekitar kode: dua puluh commit sehari, diff yang
dibaca sebelum setiap commit, konflik setelah pull, pull request setelah
push, stack trace yang harus ditelusuri, README yang harus tetap jujur.
Penelusuran ini adalah satu sesi di repositori git yang sudah Anda punya,
dan setiap langkahnya adalah hal yang akan Anda lakukan lagi besok.

## 1. Jadikan NMOX Studio editor git

**Lakukan:** Tim ▸ **Gunakan NMOX Studio dengan Git…**

**Lihat:** enam pengaturan git global yang menjadikan NMOX Studio editor,
difftool, dan mergetool git, masing-masing di samping nilai yang
dimilikinya **sekarang**, sehingga tak ada yang diganti tanpa Anda lihat.
**Terapkan** menetapkannya (**Tutup** adalah tombol bawaan, karena ini
menulis konfigurasi git global Anda); **Salin Perintah** justru menaruh
baris `git config` di papan klip. Bila git sudah memakai NMOX Studio,
dialog mengatakannya dan tidak menawarkan Terapkan.

Baris yang sama, jika Anda lebih suka terminal:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Commit

**Lakukan:** ubah sebuah berkas, lalu di terminal:

```bash
git commit -a
```

**Lihat:** pesan commit terbuka di NMOX Studio, dan baris status
mengatakan ada terminal yang menunggunya. Baris `#` milik git adalah
komentar; hanya yang Anda tulis yang diperiksa ejaannya; baris ringkasan
yang lebih dari 72 karakter, tempat alat git sendiri memotongnya, mendapat
peringatan setelah karakter ke-72. Simpan, tutup tab, dan commit pun
terjadi — terminal menunggu sampai Anda melakukannya. Keluar dari IDE
selagi pesan masih terbuka juga menyerahkannya kembali ke git, dengan apa
pun yang sudah disimpan.

`git rebase -i` membuka daftarnya dengan cara yang sama: setiap perintah
dan setiap commit disorot, dan **Alihkan komentar** membuang sebuah baris
tanpa menghapusnya.

## 3. Ketahui posisi Anda

**Lihat:** **tanda ⎇** di baris status — `⎇ main ±3 ↑2 ↓1` adalah branch
Anda, tiga berkas berubah, dua commit untuk di-push dan satu untuk
di-pull (panah hanya muncul bila ada yang perlu di-push atau di-pull).
Menunya diawali dengan **Ganti Branch…** dan **Commit…**.

**Lakukan:** letakkan kursor di baris mana pun dari berkas yang dilacak.

**Lihat:** di samping tanda itu, siapa yang terakhir mengubah baris
tersebut, kapan, dan mengapa: `Ada Lovelace, 3 hari yang lalu · Fix the parser`.
Baris yang belum Anda commit mengatakannya, dan berkas dengan perubahan
yang belum disimpan mengatakan hal itu alih-alih menyebut penulis yang
salah. Klik catatan itu untuk anotasi seluruh berkas; **Tampilan ▸
Penulis baris** mematikannya.

## 4. Tinjau diff

**Lakukan:**

```bash
git difftool
```

**Lihat:** setiap berkas yang berubah berdampingan di tampilan diff NMOX
Studio, dengan **Perbedaan Sebelumnya / Perbedaan Berikutnya** dan
“Perbedaan 2 dari 5” di atasnya. Berkas yang ditambahkan atau dihapus
menampilkan sisi yang hilang sebagai panel kosong (“tidak ada berkas”);
berkas biner tampil sebagai biner, dan bilahnya mengatakan apakah dua
berkas biner berbeda. Tutup tab dan git beralih ke berkas berikutnya.

## 5. Selesaikan konflik

**Lakukan:** gabungkan branch yang berkonflik, lalu:

```bash
git mergetool
```

**Lihat:** berkas yang berkonflik di editor dengan sisi saat ini dan sisi
masuk diwarnai, serta peringatan di setiap baris `<<<<<<<`. Letakkan kursor
di sana dan tekan ⌘. (Alt+Enter di sistem lain), atau gunakan **Kode Sumber
▸ Perbaiki kode…**: **Terima perubahan saat ini**, **Terima perubahan
masuk**, atau **Terima kedua perubahan**, masing-masing satu suntingan
yang bisa dibatalkan. Blok yang berubah sejak tawaran itu ditolak, bukan
ditebak. Simpan, tutup tab, dan jawab git.

## 6. Usulkan

**Lakukan:** push, lalu Tim ▸ **Pull Request Baru di GitHub** (juga ada di
menu tanda itu).

**Lihat:** halaman New Pull Request milik GitHub sendiri untuk branch Anda,
di peramban Anda sendiri, tempat Anda sudah masuk. Dari editor, **Edit ▸
Buka di GitHub** dan **Salin tautan GitHub** memberikan baris atau
baris-baris tempat Anda berada; di pohon Studio Proyek, keduanya memberikan
sebuah berkas atau folder.

## 7. Telusuri kegagalan

**Lakukan:** jalankan pengujian Anda di Terminal (⌃\`) sampai ada yang gagal.

**Lihat:** sebuah lokasi di keluaran — `src/app.ts:42:7`, bingkai stack
`(/abs/app.js:10:5)`, `--> src/main.rs:3:5`, `File "x.py", line 12` —
terbuka di baris dan kolom itu dengan ⌘-klik (Ctrl-klik di Windows dan
Linux). URL atau `localhost:3000` tidak pernah menjadi tautan, dan jalur
yang tidak ada ditolak dengan namanya, bukan ditebak.

## 8. Jaga README tetap jujur

**Lakukan:** Alat ▸ **Periksa Tautan Markdown…**

**Lihat:** setiap tautan relatif dan gambar di Markdown proyek diperiksa
seperti GitHub menampilkannya — berkasnya harus ada, dan `#heading` harus
berupa judul di berkas itu. Tautan mati adalah galat, judul yang hilang
adalah peringatan, keduanya sebagai garis bergelombang dan di Item
tindakan, dengan satu kalimat di baris status. Tak ada yang keluar dari
mesin Anda: tautan dengan skema tidak diperiksa.

## 9. Serahkan kepada agen

**Lakukan:** Alat ▸ **Agent Port (MCP)…**, centang **Simpan alamat dan
token ini**, lalu **Salin untuk Claude Code**, dan jalankan baris yang
disalin sekali.

**Lihat:** agen yang bisa membaca apa yang diketahui IDE — proyek yang
dibidik, apa yang disajikan dan berjalan, apa yang sedang Anda sunting,
kegagalan terakhir — dan masih bisa terhubung besok, karena token disimpan
di gantungan kunci sistem Anda dan porta dipakai ulang. Porta itu tetap
hanya-baca menurut rancangannya. Menghapus centang **Simpan alamat dan
token ini** menghapus entri di gantungan kunci.

## Yang Anda lakukan

Anda menulis pesan commit, membaca diff, menyelesaikan konflik, membuka
pull request, mencari tahu siapa yang menulis sebuah baris, menelusuri
stack trace, dan memeriksa README — semuanya di jendela tempat Anda
sudah berada. Tak satu pun menggantikan git: setiap langkah adalah langkah
git sendiri, dibuka di tempat Anda bekerja.
