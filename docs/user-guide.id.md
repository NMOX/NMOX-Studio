# NMOX Studio — Panduan pengguna

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · **Bahasa Indonesia** · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Terjemahan sebagian: bab 1–5 tersedia dalam bahasa Indonesia. Selebihnya, lihat [panduan lengkap dalam bahasa Inggris](user-guide.md).

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

<a id="4-the-task-rack"></a>
## 4. Rak Tugas

![Rak Tugas](images/tabs/the-task-rack.png)

Rak adalah jantung produk ini. Setiap perkakas dalam alur kerja Anda — npm, pembundel, penjalan uji, server pengembangan, linter, git, penerapan — adalah sebuah perangkat di dalam rak: kenop memilih tugas, GO menjalankannya, LED menunjukkan keadaan, dan sebuah layar LCD memberi tahu Anda dengan kata-kata apa yang terjadi.

![Rak yang diarahkan ke situs jQuery klasik — prasetel Classic Web Bench: MAESTRO, CRATE, DYNAMO (kenop TASK-nya membaca Gruntfile yang sebenarnya), IGNITION menyajikan statis, VITALS menjaga mutu](images/task-rack.png)

**Dasar-dasarnya:**

- **Tambahkan perangkat** dengan menyeretnya dari palet (ada kategori dan penyaring pencarian). Setiap perangkat membawa kartu *Cara memakai*-nya sendiri.
- **Jalankan sesuatu** dengan menekan tombol GO sebuah perangkat. Arahkan kursor dulu ke atasnya: keterangannya menampilkan baris perintah persis yang akan dijalankan. Tidak ada sihir.
- **Rangkai sebuah alur:** tekan **Tab** untuk memutar rak ke sisi belakangnya. Tarik kabel patch dari jack **OK** satu perangkat ke jack **GO** perangkat berikutnya. Kini `pasang → bangun → uji` hanya satu tekanan: rantainya berjalan sendiri dan berhenti pada kegagalan pertama. Keluarannya bergulir di layar fosfor perangkat MONITOR.
- **Batalkan perubahan struktur apa pun** dengan **⌘Z** — menambah, membuang, merangkai ulang. Membuang perangkat yang sedang berjalan menghentikan prosesnya lebih dulu.
- **Prasetel** memberi Anda satu rak penuh yang sudah dirangkai dengan sekali klik — Ship Gate, Dev Intelligence, Monorepo Lanes, E2E Loop, LAMP Bench, Web3 Bench, Uptime Watch. Rangkaian tersimpan per proyek secara otomatis.

![Tab memutar rak — kabel patch menuntun MAESTRO melalui CRATE, DYNAMO dan IGNITION sampai ke VITALS](images/rack-rear.png)

**Koordinasi, ketika alur Anda membesar:**

- **QUORUM** menyatukan jalur: ia hanya memicu ketika *semua* masukan terangkainya berhasil — klasik “tunggu lint DAN uji DAN pemeriksaan tipe”.
- **Gerbang ENABLE** pada proses panjang: masukan ENABLE sebuah server pengembangan berarti “jangan mulai sebelum ini memicu”.
- **REFLEX** mengawasi berkas dan mengarahkan menurut pola — `src/**/*.css` ke satu rantai, `**/*.ts` ke rantai lain, per jalur di sebuah monorepo.
- **ROSETTA** memilih jalur perkakas di repositori campuran (rak mendeteksi Node/Rust/Go/PHP/… per direktori dan mengarahkan tiap perangkat sesuai itu).

**Jalur yang berbicara dengan perkakas Anda sendiri.** Pada AUTO, perangkat lint dan format (PURITY, GLOSS) berbicara dengan perkakas proyek itu sendiri alih-alih meraih perkakas Node di mana-mana: ruang kerja Deno memakai `deno lint` dan `deno fmt`, proyek Cargo memakai `cargo clippy` dan `cargo fmt`, modul Go memakai `go vet` (atau `golangci-lint` bila proyeknya membawa konfigurasinya) dan `gofmt`. Sebuah `biome.json` mengalihkan jalur Node ke Biome, dan posisi kenop yang eksplisit selalu mengalahkan AUTO.

**Perangkat Anda sendiri.** Raknya bisa diperluas dengan penyunting teks: `*.json` mana pun di `~/.nmox/devices.d/` menjadi perangkat sungguhan — kenop, tombol, LED, porta dan kabel, tersimpan dalam rangkaian dan terjangkau dari ⌘I. Nyatakan sebuah perintah sebagai larik argumen, beri nama sebuah kenop, dan `{{kenop}}` akan menggantikannya saat tombol ditekan. Hukumnya tetap pada tuan rumah, bukan pada berkas Anda: **kepercayaan ruang kerja menjaga peluncuran pertama persis seperti pada perangkat bawaan**.

**Gerbang mutu** mengubah “kelihatannya selesai” menjadi “memang selesai”:

- **VITALS** menjalankan Lighthouse terhadap server hidup Anda dan menuntut ambang untuk kinerja, keteraksesan, praktik baik, atau SEO.
- **VERITAS** menegakkan ambang cakupan dan menjalankan ulang persis uji yang gagal, menurut namanya.
- **GAUNTLET** membebani sebuah endpoint dan menuntut keluaran minimum. **PRISM** menjaga ukuran bundel, **BEACON** menjaga sertifikat dan ketersediaan sebuah URL, dan **PREFLIGHT** adalah daftar periksa sebelum berangkat — rangkaikan OK-nya ke perangkat penerapan Anda, dan penerapan secara fisik tidak bisa berjalan sebelum semuanya hijau.
- **GOVERNOR** menjaga regresi gas dalam pekerjaan Solidity (`.gas-snapshot`).

**Selebihnya:** **SOLDER** membungkus perintah shell apa pun menjadi perangkat sepenuhnya — dan seluruh rak **diekspor ke GitHub Actions** (alur lokal Anda dan integrasi Anda adalah rangkaian yang sama). **HELM** menjalankan perintah di server jauh lewat ssh, **TAIL** mengikuti berkas log mana pun, dan **PHOSPHOR** adalah terminal di dalam rak. Bila perintahnya mencetak alamat lokal, tanda ⇄ menyala seperti pada perangkat penyaji mana pun, dan padam ketika jalannya berakhir.

**Rak menjaga dirinya tetap selaras.** Sunting `package.json` dan kenop skrip NPM-9000 memperbarui dirinya di tempat. Sunting sebuah `Gruntfile` dan DYNAMO membaca ulang tugas-tugasnya. Tambahkan sebuah dependensi dan tampilan CRATE menyegarkan diri. Tanpa mengarahkan ulang, tanpa tombol segarkan.

### KVASIR — menjelaskan kegagalan terakhir

![KVASIR menjelaskan jalannya yang benar-benar gagal: diagnosis yang telah diizinkan di panel depan dan langkah perbaikan lengkap di penampil](images/kvasir-explain.png)

**KVASIR** adalah bantuan AI dengan cara rak: sebuah perangkat yang menjelaskan galat yang sedang ada di bus MONITOR, bukan bilah obrolan di samping. Ketika sebuah jalannya gagal, tekan **EXPLAIN** dan KVASIR bertanya kepada AI Anda apa yang salah dan apa langkah berikutnya yang konkret. Putusan singkat mendarat di layar; **VIEW** membuka jawaban selengkapnya. **MODEL** memilih **FAST** (cepat dan murah, bawaan) atau **DEEP** (lebih kuat). EXPLAIN berwarna biru: ia membaca dan bertanya, ia tidak pernah menyentuh proyek Anda.

**Pilih AI Anda, pasang kunci Anda.** KVASIR bekerja dengan **Claude (Anthropic)**, **ChatGPT (OpenAI)** atau **Gemini (Google)** — kunci Anda, pilihan Anda. Tekan **KEY…** untuk memilih penyedia dan menempelkan kuncinya; pilihannya diingat, dan kuncinya hanya tinggal di gantungan kunci sistem operasi. Variabel lingkungan yang lazim bagi tiap penyedia juga dibaca, dan kunci yang tersimpan mengalahkan kunci dari lingkungan.

**Apa yang KVASIR kirim, dan itu seluruhnya.** Pertama kali Anda menekan EXPLAIN, sebuah dialog merinci persis apa yang akan meninggalkan mesin Anda dan apa yang tidak; tidak ada yang dikirim tanpa persetujuan itu, dan persetujuannya berlaku per penyedia. Setelah EXPLAIN yang berhasil, tombol **VIEW** membuka jawabannya sebagai percakapan — Anda bisa terus bertanya tentang kegagalan yang sama.

**Tanyakan kode Anda kepada KVASIR.** Asisten yang sama menjangkau penyunting: pilih kode lalu pilih **Tanya KVASIR tentang pilihan…**, atau **Sunting dengan KVASIR…** untuk mengatakan apa yang harus diubah dan melihat sebelum dan sesudahnya sebelum apa pun diterapkan. **⌥⌘G** melengkapi di kursor dengan teks bayangan yang hanya masuk bila Anda menekan Tab, dan tanda cabang git dapat menyusun pesan komit Anda.

**Arahkan sebuah agen ke IDE Anda.** Alat ▸ Agent Port (MCP)… membuka titik akhir MCP yang bisa ditanyai asisten dari luar: ia **hanya-baca menurut rancangannya**, mati sampai Anda menyalakannya, hanya mendengarkan pada antarmuka lokal, dan menuntut token yang dibuat saat ia dinyalakan.

Rak ini dapat diperluas: plugin pihak ketiga bisa menambahkan perangkat (pasang NBM mereka lewat Alat ▸ Plugin). Untuk menulis satu, lihat [device-spi.md](device-spi.md).

<a id="5-the-editor"></a>
## 5. Penyunting

![Kode jQuery dalam palet NMOX Phosphor, strukturnya di Penjelajah](images/editor.png)

Lebih dari 70 bahasa disorot sebagaimana mestinya — tumpukan modern, tumpukan klasik (termasuk CoffeeScript), dan seluruh lapisan konfigurasi, sampai ke `.env`, `.editorconfig`, konfigurasi nginx dan Apache, Dockerfile, serta berkas kunci.

- **Pelengkapan** mengenali konteks, dan juga *pustaka klasik*: bila proyek Anda membawa jQuery, MooTools, Prototype, Backbone/Underscore, atau Knockout (lewat dependensi npm *atau* tag `<script>` biasa), API mereka muncul saat melengkapi. Proyek jQuery 1.x dan 2.x mendapat penanda akhir masa pakai yang jujur, bukan omelan.
- **Kerangka Penjelajah (⌘7)** menampilkan struktur berkas untuk 58 jenis; klik untuk melompat.
- **Peta mini** — siluet seluruh berkas di samping bilah gulir tiap penyunting; klik atau seret untuk menggulir. Seluruh dokumen selalu muat dalam bilah itu: barisnya menyusut seiring berkas membesar. Tampilan ▸ Peta mini menyalakan dan mematikannya sekaligus di semua penyunting yang terbuka.
- **Gulir lengket** — deklarasi yang melingkupi bagian atas tampilan (kelasnya, lalu metode yang Anda masuki) tetap tersemat di atas teks, sampai tiga baris dari kode itu sendiri; klik salah satunya untuk melompat ke sana. Bilahnya lenyap ketika tak ada yang melingkupi baris teratas.
- **Ke simbol (⌥⇧⌘O)** melompat ke fungsi, kelas, aturan, atau judul mana pun di seluruh proyek dengan mengetik namanya — cocok menurut awalan, huruf besar di tengah kata, atau kartu bebas. Indeksnya terbatas dan jujur: `node_modules` dilewati, dan pada proyek yang sangat besar dialognya berkata bahwa ia mengindeks 2.000 berkas pertama, bukannya berpura-pura membaca semuanya.
- **Jendela uji (⌥⌘2)** menampilkan setiap uji dalam proyek *sebelum apa pun dijalankan*, dan menjalankan satu uji, satu berkas, atau semuanya.

### Bentangkan singkatan (⌥⌘E)

Ketik sebuah singkatan Emmet lalu tekan **⌥⌘E**: `ul>li*3` menjadi daftar yang lengkap. Ini bekerja di HTML, di templat Angular, dan — dalam bentuk CSS-nya — di dalam blok `<style>` dan atribut `style`, dengan potongannya dibatasi pada wilayah itu sehingga tak pernah menelan markah di sekitarnya. Singkatan yang tak dikenali produk ini ditolak dan meninggalkan teks Anda apa adanya.

### Token rancangan (properti kustom)

Mengetik `var(` menawarkan token yang dideklarasikan di lembar gaya proyek Anda yang sesungguhnya, masing-masing dengan contoh warnanya dan tempat ia dideklarasikan. **⌘-klik** pada pemakaian `var(--token)` melompat ke deklarasinya. Warna dilukis sebagai warna yang sebenarnya — hex, `rgb()`, `hsl()`, nama, dan juga `oklch()`, `lab()`, dan `color-mix()` — dan **⌘-klik** pada sebuah literal warna membuka pemilih yang menggantinya dalam bentuk persis seperti yang Anda tulis.

### Atribut class mengenal lembar gaya Anda

Mengetik di dalam `class="…"` menawarkan kelas yang benar-benar didefinisikan proyek Anda, sekaligus lembar gaya asalnya; **⌘-klik** pada sebuah kelas melompat ke aturannya, dan **⌘-klik** pada pemilih `.kelas` melompat ke pemakaian pertamanya dalam markah. **Ganti nama kelas…** mengganti nama di seluruh proyek — hanya token utuh, dengan jumlah per berkas — dan menolak dengan lantang bila nama barunya sudah ada atau masih ada perubahan yang belum disimpan.

### Jalankan skrip, dari kursor

Di bagian `scripts` sebuah `package.json`, **Jalankan skrip** menjalankan baris tempat kursor berada — lewat konfirmasi kepercayaan ruang kerja yang sama dan ■ yang sama seperti jalannya yang lain.

### Kunci lingkungan, kelas satu

Mengetik `process.env.` atau `import.meta.env.` menawarkan kunci yang benar-benar didefinisikan keluarga berkas `.env` Anda, dan **⌘-klik** melompat ke baris yang mendeklarasikan kunci itu. Nilainya ditampilkan terpotong: pengingatnya ada, rahasianya tidak.

### Templat Angular, kelas satu

Berkas `.component.html` terbuka dengan penyorotan templatnya sendiri, dengan blok `@if`/`@for` dan direktif struktural dalam pelengkapan. Pasang Angular Language Service dan pemeriksaan tipe templat benar-benar sampai: salah mengetik nama properti, dan kompiler Angular sendiri menyarankan yang benar. **⌘B** di dalam templat melompat ke deklarasinya, dan menu konteks berpindah antara komponen, templatnya, gayanya, dan ujinya.

### Komponen Vue dan Svelte, kelas satu

Berkas `.vue` dan `.svelte` terbuka dengan penyorotannya sendiri, pelengkapannya sendiri (termasuk rune bertitik Svelte 5), dan Emmet di dalam blok templatnya. Diagnostik Vue benar-benar sampai ke penyunting, lewat server bahasa Vue sendiri.

### Awakutu dengan titik henti yang sungguhan

Klik di margin kiri, pilih **Awakutu berkas (titik henti)**, dan programnya berhenti di sana — lengkap dengan tumpukan, variabel, dan penilaian ungkapan. JavaScript dan TypeScript jalan sejak awal berkat adaptor bawaan; Python memakai debugpy dan Go memakai delve, yang Anda pasang sendiri. **Awakutu di Chrome** melakukan hal yang sama untuk sebuah halaman: titik henti di sumber Anda berhenti di dalam IDE sementara peramban berjalan pada profil sekali pakai. Semuanya lebih dulu melewati konfirmasi kepercayaan ruang kerja.

### Mempertunjukkan dan berbagi

**Tampilan ▸ Mode presentasi** sekaligus memperbesar setiap penyunting yang terbuka, halaman di peramban bawaan, jendela keluaran, dan terminal — lalu mengembalikan semuanya persis seperti semula saat Anda keluar. **Tampilan ▸ Tampilkan ketikan tombol** menampilkan besar-besar kombinasi yang baru Anda tekan, tetapi tidak pernah apa yang Anda ketik. **Sunting ▸ Salin sebagai Markdown** menyalin pilihan sebagai blok berpagar dengan label bahasa yang tepat, dan variannya **dengan tautan** menambahkan tautan GitHub ke baris yang sama. **Alat ▸ Simpan tangkapan layar…** melukis seluruh jendela pada ukuran ganda, dengan varian untuk tab penyunting saja, untuk papan klip, dan untuk menyalin pohon proyek sebagai Markdown.
