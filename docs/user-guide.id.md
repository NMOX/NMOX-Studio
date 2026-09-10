# NMOX Studio — Panduan pengguna

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · **Bahasa Indonesia** · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Terjemahan sebagian: bab 1–3 tersedia dalam bahasa Indonesia. Selebihnya, lihat [panduan lengkap dalam bahasa Inggris](user-guide.md).

Cara memakai produk ini. Panduan ini menyusuri fitur sesuai urutan yang akan Anda temui: pemasangan, peluncuran pertama, proyek, rak, studio, wisaya, dan jaring pengaman.

---

<a id="1-install"></a>
## 1. Pemasangan

**macOS (disarankan):**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Baris `brew trust` adalah konfirmasi sekali jalan dari Homebrew untuk tap pihak ketiga mana pun — Anda tidak akan ditanya lagi saat pembaruan. Aplikasi ini ditandatangani secara ad-hoc tetapi tidak dinotarisasi, jadi salinan yang dikarantina akan ditolak Gatekeeper pada peluncuran pertama: cask menghapus sendiri atribut karantina pada langkah `postflight` dan menyebutkannya di keluaran pemasangan. Tidak ada yang diam-diam.

**Selebihnya:** unduh berkas dari [rilis terbaru](https://github.com/NMOX/NMOX-Studio/releases/latest) — `.dmg` untuk macOS, `-setup.exe` untuk Windows, `.deb` untuk Debian/Ubuntu, `.tar.gz` umum untuk Linux. Keempatnya membawa lingkungan Java sendiri; tidak ada yang perlu dipasang lebih dulu. `-portable.zip` adalah satu-satunya artefak yang memakai Java Anda sendiri (perlu Java 21+ di PATH, atau jalankan dengan `--jdkhome <jalur-ke-jdk>`).

> **macOS, peluncuran pertama:** aplikasi ditandatangani secara ad-hoc tetapi tidak dinotarisasi, jadi Gatekeeper bertanya sebelum menjalankannya. Untuk pertama kali, **klik kanan aplikasi → Buka** lalu konfirmasi, atau jalankan
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`. Keduanya menyelesaikannya untuk seterusnya.

### Pembaruan

IDE memperbarui dirinya sendiri: **Alat ▸ Plugin ▸ Pembaruan** menawarkan modul dari rilis mana pun yang lebih baru. Pasang, mulai ulang saat diminta, selesai — tanpa mengunduh ulang seluruh aplikasi. Satu catatan jujur: lingkungan Java bawaan dan peluncurnya hanya berubah lewat pemasang lengkap, jadi untuk lompatan platform yang besar memasang ulang dari berkas rilis tetap langkah yang benar.

<a id="2-first-launch"></a>
## 2. Peluncuran pertama

Dari terminal, `nmoxstudio --open <folder>` menjalankan aplikasi dengan folder itu terbuka sebagai proyek dan rak yang mengarah ke sana — pintu yang sama dengan “Buka folder…” di halaman selamat datang.

IDE terbuka dengan semua tab rangkaian di samping area editor: **Selamat Datang → Rak Tugas → Studio Basis Data → Studio Kontrak → Perancang Infrastruktur → Studio API → Panel Docker** — setiap permukaan utama berjarak satu klik sejak menit pertama. Di panel kiri: **Studio Proyek** (pohon berkas dan templat), basis **Meja Kerja**, dan **Penjelajah NPM**. Folder `~/NMOX` dibuat sebagai ruang kerja bawaan; rak mengarah ke sana sampai Anda membuka sebuah proyek.

![Peluncuran pertama — halaman selamat datang dengan semua tab terbuka](images/welcome.png)

Pintasan yang layak dipelajari di hari pertama (semuanya juga tercantum di tab selamat datang):

| Pintasan | Membuka |
|---|---|
| **⌘I** | Pencarian cepat — menjangkau segalanya |
| **⌘9** | Rak Tugas |
| **⌥⌘0** | Meja Kerja |
| **⌥⌘3** | Klien obrolan IRC |
| **⌥⌘4** | Peramban (WebKit bawaan, dengan DevTools) |
| **⌥⌘5** | Studio Blok |
| **⌥⌘6** | Studio Kontrak |
| **⌥⌘7** | Studio Basis Data |
| **⌥⌘8** | Studio API |
| **⌥⌘9** | Perancang Infrastruktur |
| **⌘8** | Panel Docker |
| **⌘7** | Struktur berkas saat ini |
| **⇧⌘N / ⌥⌘O** | Proyek baru… / Buka folder… |
| **⇧⌘E / ⇧⌘L** | Eksperimen baru… / Ruang belajar baru… |

<a id="3-projects"></a>
## 3. Proyek

**Membuka:** folder mana pun yang memuat satu dari 60 manifes yang dikenali akan terbuka sebagai proyek sungguhan — `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `foundry.toml`, `bower.json`, `Gruntfile.js`, dan kerabatnya — termasuk manifes rantai kontrak: repositori Aiken (`aiken.toml`) atau Clarinet (`Clarinet.toml`) terbuka dengan jalur aslinya sudah terpasang. Folder biasa berisi HTML dengan tag `<script>` dan **tanpa** manifes juga terbuka, sebagai proyek STATIC: web klasik adalah warga kelas satu di sini, bukan sebuah kesalahan.

**Membuat:** *Proyek baru…* menawarkan kerangka sungguhan — Angular, Vue, Svelte, JavaScript polos, Elixir/Phoenix, PHP Web (LEMP), dan Web klasik (jQuery). Masing-masing datang dengan konfigurasi lint, format, dan uji yang sudah terpasang serta repositori git yang telah disiapkan: satu komit kerangka yang, ketika wisaya menjalankan pemasangan untuk Anda, turut memuat berkas kunci — sehingga `git status` pertama Anda bersih.

**Berpindah proyek itu aman:** jika ada perangkat yang berjalan (server pengembangan, pengamat), IDE bertanya sebelum berpindah dan mematikannya dengan rapi. Tidak ada yang terus berjalan di belakang Anda, tidak pernah. Bahkan menutup paksa IDE pun tidak bisa meninggalkan proses telantar.

**Eksperimen** adalah cara tercepat mencoba sebuah tumpukan teknologi. **Berkas ▸ Eksperimen baru…** (⇧⌘E) memilih templat dan membuat proyek sekali pakai di `~/.nmox/experiments`: tanpa git, tanpa daftar terkini, sudah dipercaya, dependensi terpasang — agar **Jalankan yang pertama langsung berhasil**. Ia terbuka pada panduan `EXPERIMENT.md` miliknya sendiri, yang memberi tahu apa yang harus ditekan, berkas mana yang diubah, dan di mana kecerdasan IDE untuk tumpukan itu berada. Simpan yang berkembang: **Berkas ▸ Eksperimen…** ▸ **Naikkan** memindahkannya keluar dan menyiapkan git, **Gandakan** membuat salinan di sampingnya untuk pendekatan kedua, **Buang** membereskan sisanya. Raknya menampilkan usia tiap eksperimen dan biaya diskanya yang terukur. Lebih suka jalur terpandu? Dialognya menampilkan 93 ruang belajar di depan.

![Rak ruang belajar — jumlah, biaya disk, usia, dan seluruh siklus hidupnya](images/spaces-shelf.png)

![Eksperimen Express yang baru dibuat: panduannya terbuka, dependensi terpasang, API sudah melayani](images/experiment-walkthrough.png)

**Jalankan, bangun, uji — dan hentikan:** tombol ▶ pada bilah (F6) menjalankan proyek sebagaimana perkakasnya menjalankannya: skrip `start` bila package.json memilikinya, `cargo run`, `go run`, `dotnet run`, dan untuk folder berisi HTML sebuah server statis kecil pada porta bebas pertama mulai 8080. Bangun, Uji, dan Bersihkan ada di sebelahnya dan di menu Jalankan. Server pengembangan yang mengumumkan alamatnya menyalakan tanda ⇄ di bilah status dan membuka halamannya di peramban bawaan. Semuanya melewati konfirmasi kepercayaan ruang kerja pada kali pertama. Sebuah jalannya yang gagal dimulai mengatakannya terus terang dan menawarkan membuka Dokter lingkungan. Untuk menghentikan: ■ di kanan Awakutu (⌥⌘.) menghentikan semua perintah yang berjalan sekaligus dan menyebutkan apa yang dihentikannya; **Jalankan ▸ Hentikan** menghentikan satu lalu menawarkan **Ulangi**. Si ■ melihat semua yang produk jalankan untuk Anda, termasuk pemasangan; saat disorot, keterangannya menyebut persis apa yang akan dihentikan sebuah tekanan, dan sejak kapan masing-masing berjalan.

**`.env` di mana-mana:** jika proyek Anda punya `.env`, perangkat yang diluncurkan dari rak menerima variabel itu. Suntinglah, dan bilah status mencatat bahwa mulai-ulang akan mengambilnya — proses yang sedang berjalan dengan jujur mempertahankan lingkungan lamanya.
