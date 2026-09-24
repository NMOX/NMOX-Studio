# Beralih dari VS Code

<!-- languages -->
[English](coming-from-vscode.md) · [Español](coming-from-vscode.es.md) · [Français](coming-from-vscode.fr.md) · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · [Português (Brasil)](coming-from-vscode.pt.md) · **Bahasa Indonesia** · [Filipino](coming-from-vscode.tl.md) · [Tiếng Việt](coming-from-vscode.vi.md) · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

Tangan Anda sudah tahu letak segala sesuatu. Halaman ini memetakan kebiasaan
itu ke NMOX Studio: pintasannya lebih dulu, lalu di mana setiap gagasan VS Code
berada di sini, lalu apa yang memang berbeda.

Empat pintasan pertama yang ditekan pengguna VS Code melakukan apa yang ia
harapkan: **⇧⌘P** membuka palet perintah, **⇧⌘E** pohon berkas, **⇧⌘X**
plugin, dan **⌃\`** terminal. Keempatnya terdaftar di kelima profil peta tombol
yang dibawa platform, dan sebuah gerbang build menelusuri masing-masing lewat
peta tombol yang sudah dirakit di macOS, Windows, dan Linux, sehingga tidak ada
yang lain yang terpicu sebagai gantinya.

<a id="the-chords"></a>
## Pintasan

Kolom macOS memakai simbol bilah menu (⌃ Control, ⌥ Option, ⇧ Shift,
⌘ Command); kolom Windows dan Linux adalah pintasan yang sama pada papan ketik
PC.

| Anda ingin | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| Palet perintah | ⇧⌘P | **⇧⌘P** (atau ⌘I) — Pencarian Cepat | Ctrl+Shift+P | **Ctrl+Shift+P** (atau Ctrl+I) |
| Membuka berkas menurut nama | ⌘P | **⌘P** — Buka berkas | Ctrl+P | **Ctrl+P** |
| Pohon berkas | ⇧⌘E | **⇧⌘E** — Studio Proyek | Ctrl+Shift+E | **Ctrl+Shift+E** |
| Ekstensi | ⇧⌘X | **⇧⌘X** — Alat ▸ Plugin | Ctrl+Shift+X | **Ctrl+Shift+X** |
| Terminal, di folder proyek | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| Membuka proyek terkini | ⌃R | **⌥⌘P** — Ganti Proyek… | Ctrl+R | **Ctrl+Alt+P** |
| Ke simbol di proyek | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| Ke definisi | F12 | **⌘B** | F12 | **Ctrl+B** |
| Mengganti nama simbol | F2 | **⌃R** | F2 | **Ctrl+R** |
| Ke baris | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Komentar baris | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Menampilkan saran | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Menambahkan kemunculan berikutnya ke seleksi | ⌘D | **⌘J** | Ctrl+D | **Ctrl+J** |
| Memilih setiap kemunculan | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Menambahkan kursor di atas / di bawah | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Memindahkan baris ke atas / ke bawah | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Menyalin baris ke bawah | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Menghapus baris | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Memformat dokumen | ⇧⌥F | **⌃⇧F** | Shift+Alt+F | **Alt+Shift+F** |
| Menutup tab penyunting | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| Panel Problems | ⇧⌘M | **⌘6** — Item tindakan (di sini ⇧⌘M adalah *Alihkan markah*) | Ctrl+Shift+M | **Ctrl+6** |
| Menyalakan/mematikan titik henti | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| Mulai awakutu | F5 | **⇧⌘F5** — Awakutu berkas | F5 | **Ctrl+Shift+F5** |
| Menjalankan tanpa awakutu | ⌃F5 | **F6** — Jalankan Proyek | Ctrl+F5 | **F6** |
| Pengaturan | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | Alat ▸ Opsi (tanpa pintasan) |

Setiap pintasan NMOX di tabel ini dibaca dari peta tombol yang dikirimkan, bukan
dari ingatan (⌘, adalah milik menu aplikasi macOS sendiri). Ada beberapa hal
yang tidak bisa dikatakan tabel di dalam sebuah sel:

- **F5 sudah terpakai selama awakutu.** Di sini artinya *Lanjutkan*, seperti di
  setiap IDE keluarga NetBeans, jadi sebuah jalannya awakutu dimulai dari
  **⇧⌘F5** (Ctrl+Shift+F5) dan dilanjutkan dari F5.
- **⌃R di sini adalah Ganti Nama**, itulah sebabnya *Ganti Proyek* ada di ⌥⌘P,
  bukan di pintasan Open Recent milik VS Code. Ganti Nama bekerja di tempat
  bahasa di balik berkas itu mendukungnya.
- **Ctrl+, di Windows dan Linux** mundur menelusuri riwayat suntingan Anda,
  seperti yang selalu dilakukannya di NetBeans; pengaturannya ada di bawah
  Alat ▸ Opsi (di macOS, **Settings…** di menu aplikasi, ⌘,).

**Bantuan ▸ Pintasan Papan Ketik…** mendaftar setiap pintasan NMOX di peta tombol
Anda yang aktif, termasuk keempat pintasan VS Code, dibaca dari peta tombol yang
sedang berjalan sehingga ia tidak mungkin menyimpang dari apa yang dilakukan
tombol-tombolnya.


<a id="from-the-terminal"></a>
## Dari terminal

`code .` menjadi `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox            # just start the IDE
```

Perintah itu langsung kembali, dan `nmox` yang kedua menyerahkan foldernya
kepada IDE yang sudah berjalan. Homebrew, pemasang Windows (*Add "nmox" to
PATH*), dan paket Linux memasukkannya ke PATH Anda; untuk pemasangan dari DMG,
[panduan pengguna](user-guide.id.md#2-first-launch) menunjukkan tautan satu
baris itu.

<a id="where-each-vs-code-idea-lives"></a>
## Di mana setiap gagasan VS Code berada

| Di VS Code | Di NMOX Studio |
|---|---|
| **Explorer** | **Studio Proyek** (⇧⌘E) — pohon berkas, templat, dan penyunting `package.json` proyek. **Meja Kerja** (⌥⌘0) adalah pangkalan: berkas yang terbuka, berkas terkini, proyek terkini, dan semua yang sedang berjalan. |
| **Command Palette** | **Pencarian Cepat** (⇧⌘P atau ⌘I) — tindakan, berkas, proyek terkini, perangkat rak, server aktif, permintaan Studio API, simbol. |
| **Extensions** | **Alat ▸ Plugin** memasang dan memperbarui modul, termasuk pembaruan NMOX sendiri. Banyak hal yang ditambahkan sebuah ekstensi di VS Code adalah sebuah **perangkat rak** di sini — dan Anda bisa menulisnya sebagai berkas JSON di `~/.nmox/devices.d` ([berkas perangkat](device-files.md)). |
| **`tasks.json`** | Skrip proyek Anda sendiri, dijalankan sebagaimana ditulis: Jalankan / Bangun / Uji di bilah alat (F6, F11, ⌃F6), **Jalankan Skrip** pada baris `scripts` di `package.json`, **Penjelajah NPM**, dan **Rak Tugas** (⌘9), tempat tugas adalah perangkat yang Anda rangkai bersama. |
| **`launch.json`** | **Awakutu berkas** (⇧⌘F5) dan tombol awakutu di bilah alat menentukan apa yang diluncurkan dari proyek itu sendiri — titik masuk skrip `start`, `main`, `index.js` — dan perangkat rak **INSPECTOR** meluncurkan pengawakutu sebagai satu langkah di sebuah alur. |
| **Terminal terpadu** | Jendela **Terminal** (⌃\`): tekanan pertama memulai shell di folder proyek, tekanan berikutnya memunculkannya kembali. |
| **`settings.json`** | Alat ▸ Opsi (di macOS, NMOX Studio ▸ Settings…). `.editorconfig` proyek Anda berlaku saat Anda mengetik dan saat menyimpan. |
| **Panel Problems** | **Item tindakan** (⌘6): galat dan peringatan dari server bahasa, serta temuan lint dan tipe dari perangkat PURITY dan TYPEGUARD di rak. Seperti di VS Code, sebagian server hanya melaporkan berkas yang sedang Anda buka; gopls melaporkan seluruh paket. |
| **Outline** | **Navigator** (⌘7). |
| **Source Control** | Tanda git di baris status (cabang dan perubahan, sekali klik ke riwayat) dan menu **Tim**. |
| **Workspace Trust** | Gagasan yang sama, **Kepercayaan Ruang Kerja**, ditegakkan sebelum apa pun yang dipilih sebuah repositori dijalankan: membuka proyek hasil klon tidak menjalankan apa pun sampai Anda memercayainya. |
| **Keyboard Shortcuts editor** | Alat ▸ Opsi ▸ Pintasan keyboard (di macOS, Settings… ▸ Pintasan keyboard) — sunting pintasan apa pun, atau ganti seluruh profil ke Eclipse, Emacs, atau IntelliJ. |

<a id="what-is-honestly-different"></a>
## Apa yang memang berbeda

- **⌘D bukan multikursor di sini.** Gerakan yang sama adalah **⌘J** (Ctrl+J);
  ⌘D sendiri tidak terikat ke apa pun. Ikat ulang di Pintasan keyboard jika jari
  Anda bersikeras.
- **⌃\` membuka dan memfokuskan Terminal; ia tidak menyembunyikannya.** Dan
  selama Terminal punya fokus, tombol-tombolnya milik shell Anda, sehingga
  tekanan kedua sampai ke shell alih-alih membawa Anda kembali ke penyunting.
- **`.vscode/tasks.json` dan `launch.json` tidak dibaca.** Sebuah tugas adalah
  perintah yang dipilih sebuah repositori, dan membacanya layak mendapat
  rancangannya sendiri di seputar Kepercayaan Ruang Kerja; sampai saat itu,
  skrip proyek sendiri dan aturan titik masuk awakutu di atas yang
  mengerjakannya.
- **Tidak ada profil peta tombol “VS Code”.** Pintasan di atas menumpang pada
  profil bawaan dan keempat profil lainnya. Satu pengecualian disengaja: di
  profil **Eclipse**, ⇧⌘E tetap *Switch to Editor* milik Eclipse sendiri, dan di
  dalam penyunting ⇧⌘P dan ⇧⌘X mempertahankan arti Eclipse-nya (kurung yang
  berpasangan, huruf besar) — orang yang memilih Eclipse mengharapkan Eclipse.
- **Di Linux, Ctrl+\` membuka Terminal, bukan pengalih jendela.** Platform
  menaruh pengalih kedua di sana untuk desktop (KDE) yang merebut Ctrl+Tab;
  pengalihnya ada di Ctrl+Tab.
- **Pintasan Ctrl+Alt bisa bertabrakan dengan AltGr.** Di Windows, tata letak
  papan ketik yang mengetik karakter dengan AltGr (misalnya Polandia)
  mengirimkan Ctrl+Alt untuknya. Jika Ctrl+Alt+P atau Ctrl+Alt+K mengetik sebuah
  karakter untuk Anda, pindahkan *Ganti Proyek* atau pintasan eksperimen di
  Pintasan keyboard.
- **Ekstensi VS Code tidak terpasang di sini.** Kecerdasan bahasa datang dari
  server bahasa yang dikenal NMOX (Dokter Lingkungan mendaftar apa yang belum ada
  dan cara memasangnya), dari tata bahasa penyunting sendiri, dan dari plugin
  yang dibuat untuk NetBeans Platform.
