# Mulai cepat: lima menit sampai proyek Anda berjalan

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · **Bahasa Indonesia** · [Filipino](quickstart.tl.md) · [Tiếng Việt](quickstart.vi.md) · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

Halaman ini membuat salah satu proyek Anda sendiri berjalan di dalam NMOX
Studio. Isinya hanya apa yang Anda perlukan untuk itu.
[Panduan pengguna](user-guide.id.md) adalah manual lengkapnya. Jika Anda
memakai VS Code, baca [Beralih dari VS Code](coming-from-vscode.id.md)
sesudah ini.

<a id="1-install-one-minute"></a>
## 1. Pasang (satu menit)

**macOS, dengan Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew meminta Anda menjalankan `brew trust` sekali untuk tap pihak ketiga
mana pun. Ia tidak akan bertanya lagi saat Anda memperbarui.

**macOS, Windows, Linux, tanpa Homebrew:** unduh rilis terbaru untuk sistem
operasi Anda dari
[halaman rilis](https://github.com/NMOX/NMOX-Studio/releases/latest):

| Sistem operasi | Berkas | Lalu |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Seret aplikasinya ke Applications. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Jalankan pemasangnya. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Linux lainnya | `NMOX-Studio-<version>-linux.tar.gz` | Buka arsipnya dan jalankan `bin/nmoxstudio`. |

Setiap berkas ini membawa lingkungan Java-nya sendiri, jadi tidak ada lagi
yang perlu Anda pasang. Hanya zip portabel yang memerlukan Java 21 atau lebih
baru yang sudah ada di mesin.

Di macOS, aplikasi ini dinotarisasi oleh Apple. Saat pertama kali Anda
membukanya, macOS bertanya apakah akan membuka aplikasi yang diunduh dari
internet: klik **Buka** (*Open*).

<a id="2-open-your-project-one-minute"></a>
## 2. Buka proyek Anda (satu menit)

Jalankan **NMOX Studio**. Ia membuka tiga tab: **Selamat Datang**, **Rak
Tugas**, dan **Peramban**.

Untuk membuka proyek Anda, pilih **Berkas ▸ Buka Folder…** (⌥⌘O di macOS,
Ctrl+Alt+O di Windows dan Linux) lalu pilih foldernya. Anda juga bisa
melakukannya dari terminal, seperti dengan `code .`:

```bash
cd ~/code/my-app
nmox .
```

Perintah itu langsung kembali. Jika NMOX Studio sudah berjalan, ia menerima
foldernya; jika belum, ia dijalankan. Homebrew, pemasang Windows, dan paket
Linux memasukkan `nmox` ke PATH Anda. Untuk pemasangan dari DMG, lihat
[cara memasukkan `nmox` ke PATH Anda](user-guide.id.md#2-first-launch).

Sebuah folder dihitung sebagai proyek jika memuat `package.json`,
`Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `pyproject.toml`, atau
salah satu dari 57 berkas proyek lainnya. Folder berisi berkas HTML biasa
juga dihitung.

Tiga hal terjadi ketika Anda membuka sebuah proyek:

- **Studio Proyek**, di sebelah kiri, menampilkan berkas Anda.
- Baris status, di bagian bawah, menampilkan cabang git Anda dan jumlah
  berkas yang berubah.
- **Rak Tugas** disiapkan sesuai jenis proyeknya. Proyek Vite mendapat
  konsol Vite, proyek Cargo mendapat jalur jalankan, debug, dan uji, dan
  seterusnya.

<a id="3-run-it-one-minute"></a>
## 3. Jalankan (satu menit)

Tekan **▶** di bilah alat, atau F6. Ia menjalankan proyek Anda sebagaimana
perkakasnya menjalankannya: skrip `dev`, `start`, atau `serve` dari
`package.json`, `cargo run`, `go run`. Ia memakai pengelola paket proyek Anda
sendiri: npm, pnpm, atau yarn, atau bun untuk proyek Bun.

Pertama kali Anda menjalankan apa pun di sebuah proyek, NMOX Studio bertanya
apakah Anda memercayai folder itu. Proyek yang belum Anda percayai tidak
menjalankan kodenya sendiri sama sekali: tidak ada skrip, build, atau uji.
Klik **Percayai Ruang Kerja** untuk kode Anda sendiri.

Jika proyek Anda adalah server pengembangan, alamatnya muncul di baris status
di samping simbol **⇄**, dan halamannya terbuka di tab **Peramban**. Sunting
sebuah berkas lalu simpan, dan halamannya dimuat ulang.

Untuk menghentikan semua yang sedang berjalan, tekan **■** di samping ▶, atau
⌥⌘. (Option, Command, dan titik).

Jika tidak terjadi apa-apa, lihat tab **Keluaran** di bagian bawah. Tab itu
menjelaskan mengapa jalannya tidak bisa dimulai, misalnya karena sebuah
perkakas belum terpasang atau dependensinya belum dipasang, dan menawarkan
untuk memperbaikinya. **Alat ▸ Dokter Lingkungan…** mendaftar setiap perkakas
yang bisa dipakai NMOX Studio dan menunjukkan mana yang sudah terpasang.

<a id="4-find-anything-thirty-seconds"></a>
## 4. Temukan apa saja (tiga puluh detik)

Tekan **⌘I** (Ctrl+I di Windows dan Linux) lalu mengetiklah. Pencarian Cepat
menemukan berkas, tindakan menu, simbol, perangkat rak, server dan perintah
yang sedang berjalan, serta skrip `package.json` Anda. Tekan Enter untuk
membuka atau menjalankan hasilnya.

Tekan **⌘P** untuk membuka berkas menurut namanya.

<a id="5-test-it-thirty-seconds"></a>
## 5. Uji (tiga puluh detik)

Tekan **⌃F6** (Ctrl+F6) untuk menjalankan uji proyek Anda. Untuk melihat
setiap uji di proyek sebelum Anda menjalankan satu pun, buka jendela
**Pengujian** dengan ⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## Jika Anda tidak punya proyek di tangan

- **Berkas ▸ Proyek Baru…** membuat proyek sungguhan dari sebuah templat
  (Angular, Vue, Svelte, React dengan Vite, JavaScript polos, PHP, Phoenix,
  dan lainnya). Ia membuat berkasnya, menyiapkan git, dan memasang
  dependensinya.
- **Berkas ▸ Ruang Belajar Baru…** membuka tutorial yang dituntun. *Halaman
  web pertama Anda* ada paling atas di daftar.

<a id="where-to-go-next"></a>
## Ke mana sesudah ini

- **[Rak Tugas](user-guide.id.md#4-the-task-rack)**. Setiap perkakas yang
  Anda jalankan adalah sebuah perangkat di rak, dan kabel di antara perangkat
  merangkainya: misalnya, jalankan uji setiap kali build berhasil.
- **[Penyunting](user-guide.id.md#5-the-editor)**. Termasuk Emmet, contoh
  warna, debugging dengan titik henti untuk Node dan Chrome, dan templat
  Angular.
- **[Studio-studio](user-guide.id.md#6-the-studios)**. Studio API, Studio
  Basis Data, Studio Kontrak dan Studio Blok, serta Papan Tugas.
- **[Glosarium](glossary.id.md)** menjelaskan kata-kata khas produk ini: rak,
  patch, jack, jalur, mengarahkan, KVASIR.
