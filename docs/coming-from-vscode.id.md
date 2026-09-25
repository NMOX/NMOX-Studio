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
| Ke definisi | F12 | **F12** atau ⌘B | F12 | **F12** atau Ctrl+B |
| Mencari referensi | ⇧F12 | **⇧F12** — Cari penggunaan | Shift+F12 | **Shift+F12** |
| Mengganti nama simbol | F2 | **F2** atau ⌃R | F2 | **F2** atau Ctrl+R |
| Perbaikan cepat | ⌘. | **⌘.** atau ⌃↩ | Ctrl+. | **Alt+Enter** |
| Ke baris | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Kembali / maju | ⌃- / ⌃⇧- | **⌃- / ⌃⇧-** | Alt+← / Alt+→ | **Alt+← / Alt+→** |
| Komentar baris | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Menampilkan saran | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Menambahkan kemunculan berikutnya ke seleksi | ⌘D | **⌘D** atau ⌘J | Ctrl+D | **Ctrl+D** atau Ctrl+J |
| Memilih setiap kemunculan | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Menambahkan kursor di atas / di bawah | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Memindahkan baris ke atas / ke bawah | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Menyalin baris ke bawah | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Menghapus baris | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Menjorokkan baris | ⌘] | **⌘]** | Ctrl+] | **Alt+Shift+→** |
| Mengganti | ⌥⌘F | **⌥⌘F** atau ⌘R | Ctrl+H | **Ctrl+H** |
| Memformat dokumen | ⇧⌥F | **⇧⌥F** atau ⌃⇧F | Shift+Alt+F | **Alt+Shift+F** |
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

### Setiap pintasan penyuntingan, diukur

Pintasan yang dicari tangan Anda dari VS Code saat menyunting,
masing-masing dicek di peta tombol yang dikirimkan untuk profil bawaan di
macOS. Di mana pintasan VS Code masih bebas di sini, sekarang pintasan itu
melakukan apa yang dilakukan VS Code (baris bertanda **Sama:**); di mana
pintasan itu sudah berarti sesuatu yang diandalkan pengguna NetBeans,
artinya dipertahankan dan barisnya menyebut di mana aksi VS Code berada.

| VS Code, macOS | Yang dilakukan VS Code | Di NMOX Studio |
|---|---|---|
| F12 | Go to Definition | **Sama:** Ke deklarasi, seperti ⌘B |
| ⇧F12 | Go to References | **Sama:** Cari penggunaan, seperti ⌃F7 |
| F2 | Rename Symbol | **Sama:** Ganti nama, seperti ⌃R |
| ⌘. | Quick Fix | **Sama:** perbaikan untuk baris itu, seperti yang ditampilkan ⌃↩ |
| ⌥↑ / ⌥↓ | Move Line Up / Down | Kemunculan bertanda sebelumnya / berikutnya; memindahkan baris adalah ⌃⇧↑ / ⌃⇧↓ |
| ⇧⌥↑ / ⇧⌥↓ | Copy Line Up / Down | Sama, seperti selama ini |
| ⇧⌘K | Delete Line | Sisipkan kata cocok berikutnya (melengkapi kata dari berkas); menghapus baris adalah ⌘E |
| ⌘L | Expand Line Selection | Pilih pengenal; memilih baris tidak punya pintasan |
| ⇧⌘L | Select All Occurrences | Tempel sebagai baris di editor; memilih semua kemunculan adalah ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | Sama, seperti selama ini |
| ⇧⌥A | Toggle Block Comment | Tidak ada: tidak ada aksi komentar blok tersendiri, dan ⌘/ menyalakan atau mematikan komentar |
| ⌘] | Indent Line | **Sama:** Geser ke kanan |
| ⌘[ | Outdent Line | Lompat ke kurung pasangannya, seperti selama ini; mengurangi indentasi adalah ⇧Tab atau ⌃⇧← |
| ⌘B | Toggle Sidebar | Ke deklarasi; ⇧⌘↩ hanya menampilkan editor, ⇧Esc memaksimalkan jendela tempat Anda berada |
| ⌘J | Toggle Panel | Menambahkan kemunculan berikutnya di editor (seperti ⌘D); jendela Output adalah ⌘4 |
| ⌘\ | Split Editor | Lengkapi kode di editor; membelah editor adalah ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | Sama, seperti selama ini: pintasan Buka berkas terkini membuka lagi berkas yang terakhir ditutup |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **Sama:** Kembali dan Maju melalui tempat-tempat yang tadi Anda sunting, seperti ⌃← / ⌃→ (pintasan yang biasanya dipakai macOS untuk berpindah desktop) |
| ⌘G / ⇧⌘G | Find Next / Previous | Sama, seperti selama ini |
| ⌥⌘F | Replace | **Sama:** Ganti, seperti ⌘R |
| ⇧⌘F | Find in Files | Sama, seperti selama ini: Cari di proyek |
| ⇧⌘O | Go to Symbol in Editor | Buka Proyek; simbol berkas ada di Navigator (⌘7) |
| ⌘T | Go to Symbol in Workspace | Menukar dua huruf di sekitar kursor di editor; simbol proyek adalah ⌥⇧⌘O |
| ⌃G | Go to Line | Sama, seperti selama ini |
| ⌘K ⌘S | Keyboard Shortcuts | ⌘K adalah Sisipkan kata cocok sebelumnya; daftarnya ada di **Bantuan ▸ Pintasan Papan Ketik…** |
| ⌘, | Settings | Sama, seperti selama ini: NMOX Studio ▸ Settings… |
| ⇧⌥F | Format Document | **Sama:** Format, seperti ⌃⇧F |

Pintasan bertanda **Sama:** dipasang di setiap profil peta tombol yang
membiarkannya bebas, dan profil yang memberi salah satunya arti sendiri
tetap mempertahankan arti itu: F12 di profil Eclipse, Emacs, dan NetBeans
5.5, F2 di setiap profil kecuali profil bawaan, ⇧F12 di Emacs dan NetBeans
5.5, ⌃- dan ⌃⇧- di Emacs dan IntelliJ, ⇧⌥F di IntelliJ. Di Windows dan
Linux, F12, ⇧F12, dan F2 bekerja dengan cara yang sama; pintasan VS Code
lainnya berbeda di sana, dan tabel di atas memberikan keduanya.


<a id="from-the-terminal"></a>
## Dari terminal

`code .` menjadi `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

Perintah itu langsung kembali, dan `nmox` yang kedua menyerahkan foldernya
kepada IDE yang sudah berjalan. Kolom (`src/app.ts:42:7`) juga diterima, dan
penyunting terbuka di awal baris; nama yang tidak ada ditolak di terminal
alih-alih menjalankan apa pun. Opsi `-r` diterima, `-n` membuka di
satu-satunya jendela, dan `-a` serta `-v` ditolak dengan menyebut namanya.

`-w` (`--wait`) membuka sebuah berkas dan menunggu sampai Anda menutup tabnya,
dan `-d` (`--diff`) membandingkan dua berkas berdampingan, sehingga NMOX Studio
bisa menjadi penyunting dan difftool untuk git, seperti `code --wait`:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
```

Setelah itu `git commit` membuka pesannya di IDE; simpan, tutup tabnya, dan git
melanjutkan. Keluar dari IDE selagi berkas masih terbuka juga
mengembalikannya, dengan isi yang sudah disimpan.
**Tim ▸ Gunakan NMOX Studio dengan Git…** mengatur tiga baris yang sama itu untuk Anda,
setelah menunjukkan nilai masing-masing saat ini.
Homebrew, pemasang Windows (*Add "nmox" to
PATH*), dan paket Linux memasukkannya ke PATH Anda; untuk pemasangan dari DMG,
[panduan pengguna](user-guide.id.md#2-first-launch) menunjukkan tautan satu
baris itu.

<a id="where-each-vs-code-idea-lives"></a>
## Di mana setiap gagasan VS Code berada

| Di VS Code | Di NMOX Studio |
|---|---|
| **Explorer** | **Studio Proyek** (⇧⌘E) — pohon berkas (klik kanan sebuah berkas untuk Salin Jalur, Salin Jalur Relatif, dan Tampilkan di Finder), templat, dan penyunting `package.json` proyek. **Meja Kerja** (⌥⌘0) adalah pangkalan: berkas yang terbuka, berkas terkini, proyek terkini, dan semua yang sedang berjalan. |
| **Command Palette** | **Pencarian Cepat** (⇧⌘P atau ⌘I) — tindakan, berkas, proyek terkini, perangkat rak, server aktif, permintaan Studio API, simbol. Nama perintah VS Code sendiri juga berfungsi: *Format Document*, *Toggle Terminal*, *Git: Commit*, atau *Open Settings* menampilkan tindakan yang melakukan hal yang sama di sini, di bawah **Perintah VS Code**, dengan nama dan pintasannya sendiri. |
| **Extensions** | **Alat ▸ Plugin** memasang dan memperbarui modul, termasuk pembaruan NMOX sendiri. Banyak hal yang ditambahkan sebuah ekstensi di VS Code adalah sebuah **perangkat rak** di sini — dan Anda bisa menulisnya sebagai berkas JSON di `~/.nmox/devices.d` ([berkas perangkat](device-files.md)). |
| **`tasks.json`** | `.vscode/tasks.json` di repositori Anda dibaca: ketik nama sebuah tugas di Pencarian Cepat (⇧⌘P atau ⌘I), dan Enter pada *Jalankan tugas: build — make all* menjalankannya, dengan Kepercayaan Ruang Kerja bertanya lebih dulu pada proyek yang belum Anda percayai, keluarannya di jendela Output, dan ■ di bilah alat untuk menghentikannya. Di sampingnya, skrip proyek Anda sendiri dijalankan sebagaimana ditulis: Jalankan / Bangun / Uji di bilah alat (F6, F11, ⌃F6), **Jalankan Skrip** pada baris `scripts` di `package.json`, **Penjelajah NPM**, dan **Rak Tugas** (⌘9), tempat tugas adalah perangkat yang Anda rangkai bersama. |
| **`launch.json`** | `.vscode/launch.json` di repositori Anda dibaca: ketik nama sebuah konfigurasi di Pencarian Cepat (⇧⌘P atau ⌘I), dan Enter pada *Debug: Launch Program — ${workspaceFolder}/server.js* memulai pengawakutu titik henti pada program itu, dengan Kepercayaan Ruang Kerja bertanya lebih dulu. Konfigurasi Node (`node`, `pwa-node`) dan Python (`python`, `debugpy`) mengawakutu `program`-nya di dalam `cwd`-nya, dengan `args` dan `env`-nya; konfigurasi Chrome (`chrome`, `pwa-chrome`) membuka `url` (atau `file`) miliknya dengan `webRoot`-nya. Tanpa `launch.json`, **Awakutu berkas** (⇧⌘F5) dan tombol awakutu di bilah alat menentukan apa yang diluncurkan dari proyek itu sendiri — titik masuk skrip `start`, `main`, `index.js` — dan perangkat rak **INSPECTOR** meluncurkan pengawakutu sebagai satu langkah di sebuah alur. |
| **Terminal terpadu** | Jendela **Terminal** (⌃\`): tekanan pertama memulai shell di folder proyek, tekanan berikutnya memunculkannya kembali. |
| **`settings.json`** | Alat ▸ Opsi (di macOS, NMOX Studio ▸ Settings…). `.vscode/settings.json` sebuah repositori juga dibaca: `editor.tabSize`, `editor.insertSpaces`, dan `editor.indentSize` mengatur indentasi berkas-berkasnya saat Anda mengetik, `files.trimTrailingWhitespace` dan `files.insertFinalNewline` (bila bernilai `true`) diterapkan saat menyimpan, dan blok bahasa seperti `"[typescript]"` menimpanya untuk bahasa itu. Jika repositori itu juga punya `.editorconfig`, `.editorconfig` itulah yang menang di mana pun keduanya mengatur hal yang sama. |
| **Panel Problems** | **Item tindakan** (⌘6), atau klik hitungan **✕ ⚠** di baris status: galat dan peringatan dari server bahasa, serta temuan lint dan tipe dari perangkat PURITY dan TYPEGUARD di rak. Seperti di VS Code, sebagian server hanya melaporkan berkas yang sedang Anda buka; gopls melaporkan seluruh paket. |
| **Outline** | **Navigator** (⌘7). |
| **Source Control** | Tanda git di baris status (cabang dan perubahan, sekali klik ke riwayat) dan menu **Tim**. |
| **Workspace Trust** | Gagasan yang sama, **Kepercayaan Ruang Kerja**, ditegakkan sebelum apa pun yang dipilih sebuah repositori dijalankan: membuka proyek hasil klon tidak menjalankan apa pun sampai Anda memercayainya. |
| **Keyboard Shortcuts editor** | Alat ▸ Opsi ▸ Pintasan keyboard (di macOS, Settings… ▸ Pintasan keyboard) — sunting pintasan apa pun, atau ganti seluruh profil ke Eclipse, Emacs, atau IntelliJ. |

Pertama kali Anda membuka repositori yang membawa `.vscode/tasks.json`,
`launch.json`, atau `settings.json`, sebuah pemberitahuan menyebutkan apa
yang ditemukan dan di mana letaknya; klik untuk membuka Pencarian Cepat.
Pemberitahuan itu muncul sekali per proyek.

<a id="what-is-honestly-different"></a>
## Apa yang memang berbeda

- **⌘D menambahkan kemunculan berikutnya di peta tombol bawaan, tetapi
  tidak di setiap profil.** Profil Eclipse mempertahankan ⌘D sebagai
  *Delete Line* milik Eclipse, profil NetBeans 5.5 sebagai *Shift Line
  Left*, dan profil Emacs sebagai *kill word* (serta Ctrl+D sebagai
  *delete character* di Windows dan Linux); profil IntelliJ punya ⌘D di
  macOS dan mempertahankan Ctrl+D sebagai *Duplicate Line* di Windows dan
  Linux. Pintasan kedua untuk gerakan ini juga berbeda per profil: ⌘J
  (Ctrl+J) di profil bawaan, ⌃J (Alt+J) di Eclipse dan IntelliJ, dan tidak
  ada di Emacs dan NetBeans 5.5, tempat Pintasan keyboard bisa memberinya
  satu.
- **⌃\` membuka dan memfokuskan Terminal; ia tidak menyembunyikannya.** Dan
  selama Terminal punya fokus, tombol-tombolnya milik shell Anda, sehingga
  tekanan kedua sampai ke shell alih-alih membawa Anda kembali ke penyunting.
- **`launch.json` dibaca, dan apa yang tidak bisa dipenuhi pengawakutu
  ditolak.** Pengawakutu di sini meneruskan sebuah program, folder kerjanya,
  `args`-nya (daftar string), dan `env`-nya (string yang ditambahkan ke
  lingkungan warisan), jadi konfigurasi yang mengatur `envFile`,
  `runtimeExecutable`, `runtimeArgs`, `preLaunchTask`, atau bidang lain yang
  belum diajarkan kepadanya tetap terdaftar tetapi tidak dimulai: Enter
  menyebutkan bidang-bidang itu di baris status. Memulai program tanpa
  semua itu berarti mengawakutu sesuatu yang lain daripada yang dikatakan
  berkas itu. `args` yang ditulis sebagai satu string (VS Code
  menyerahkannya ke shell) dan nilai `env` berupa `null` (yang menghapus
  sebuah variabel) ditolak dengan cara yang sama, begitu pula
  `"request": "attach"`, sebuah entri `compounds`, tipe yang tidak punya adaptor di sini (`go`,
  `msedge`, `cppdbg`, dan lainnya), nilai yang hanya bisa diberikan VS Code
  (`${file}`, `${input:…}`), dan jalur di luar proyek. Bidang yang hanya
  membentuk apa yang ditampilkan pengawakutu — `skipFiles`, `outFiles`,
  `sourceMaps`, `console`, `justMyCode`, `presentation` — diterima tetapi
  tidak diterapkan; keluaran program masuk ke jendela Output.
- **`tasks.json` dibaca, dan apa yang tidak bisa dijalankan sebagaimana
  ditulis ditolak.** Tugas yang memakai nilai yang
  hanya bisa diberikan VS Code (`${input:…}`, `${file}`, `${config:…}`,
  `${command:…}`) atau yang `dependsOn` tugas lain tetap terdaftar tetapi
  tidak dijalankan: Enter menyebutkan variabel atau tugas yang mana di baris
  status. Menjalankannya dengan nilai yang dibiarkan kosong, atau tanpa tugas
  yang menjadi sandarannya, berarti menjalankan sesuatu yang lain daripada
  yang dikatakan berkas itu. Begitu pula tipe tugas yang disediakan sebuah
  ekstensi (`gulp`, `typescript`), dan folder kerja di luar proyek.
- **Tugas `"type": "shell"` berjalan di shell yang akan dipakai VS Code.** Di
  macOS dan Linux itu adalah `$SHELL` Anda dengan `-c` (zsh, bash, atau fish
  di macOS dimulai sebagai shell login, `-l`, seperti profil bawaan VS Code);
  di Windows itu PowerShell, `pwsh` bila terpasang. `options.shell` dihormati
  dengan cara VS Code: sebutkan sebuah `executable` dan tugas berjalan dengan
  persis `args` yang Anda berikan, jadi bash memerlukan `"args": ["-c"]`. Di
  Windows hanya PowerShell (argumen yang diakhiri `-Command`) dan `cmd.exe`
  (argumen yang diakhiri `/c`) yang dijalankan; shell lain apa pun di sana
  ditolak dengan menyebut namanya alih-alih diberi baris perintah yang
  dikutip secara tebak-tebakan.
- **Tidak ada profil peta tombol “VS Code”.** Pintasan di atas menumpang pada
  profil bawaan dan keempat profil lainnya. Satu pengecualian disengaja: di
  profil **Eclipse**, ⇧⌘E tetap *Switch to Editor* milik Eclipse sendiri, dan di
  dalam penyunting ⇧⌘P dan ⇧⌘X mempertahankan arti Eclipse-nya (kurung yang
  berpasangan, huruf besar) — orang yang memilih Eclipse mengharapkan Eclipse.
- **Di Linux, Ctrl+\` membuka Terminal, bukan pengalih jendela.**
  Pengalihnya ada di Ctrl+Tab. Di desktop yang mengambil Ctrl+Tab untuk
  dirinya sendiri (KDE, misalnya), **Jendela ▸ Dokumen…** mendaftar berkas
  yang terbuka sebagai gantinya.
- **Pintasan Ctrl+Alt bisa bertabrakan dengan AltGr.** Di Windows, tata letak
  papan ketik yang mengetik karakter dengan AltGr (misalnya Polandia)
  mengirimkan Ctrl+Alt untuknya. Jika Ctrl+Alt+P atau Ctrl+Alt+K mengetik sebuah
  karakter untuk Anda, pindahkan *Ganti Proyek* atau pintasan eksperimen di
  Pintasan keyboard.
- **Ekstensi VS Code tidak terpasang di sini.** Kecerdasan bahasa datang dari
  server bahasa yang dikenal NMOX (Dokter Lingkungan mendaftar apa yang belum ada
  dan cara memasangnya), dari tata bahasa penyunting sendiri, dan dari plugin
  yang dibuat untuk NetBeans Platform.
