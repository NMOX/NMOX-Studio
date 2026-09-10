# NMOX Studio — Panduan pengguna

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · **Bahasa Indonesia** · [Filipino](user-guide.tl.md) · [Tiếng Việt](user-guide.vi.md) · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

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

IDE terbuka dengan tiga tab di samping area editor: **Selamat Datang → Rak Tugas → Peramban**. Setiap jendela lain berjarak satu pintasan ⌥⌘ dan tercantum di kolom TOOLING halaman selamat datang. Di panel kiri: **Studio Proyek** (pohon berkas dan templat), basis **Meja Kerja**, dan **Penjelajah NPM**. Folder `~/NMOX` dibuat sebagai ruang kerja bawaan; rak mengarah ke sana sampai Anda membuka sebuah proyek.

![Peluncuran pertama — halaman selamat datang dengan tiga tab](images/tabs/workbench.png)

Pintasan yang layak dipelajari di hari pertama (semuanya juga tercantum di tab selamat datang):

| Pintasan | Membuka |
|---|---|
| **⌘I** | Pencarian cepat — menjangkau segalanya |
| **⌘9** | Rak Tugas |
| **⌥⌘0** | Meja Kerja |
| **⌥⌘1** | Papan Tugas |
| **⌥⌘2** | Pengujian |
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

<a id="6-the-studios"></a>
## 6. Studio-studio

### Akses lewat papan tik dan pembaca layar

Setiap kendali di rak punya nama yang terbaca, dan itu diperiksa pada setiap kali membangun. Kenop adalah penggeser yang menuruti tombol panah, Home dan End; tombol menuruti spasi dan Enter, termasuk yang diredupkan, yang mengatakan mengapa ia menolak; LED dan layar mengumumkan keadaannya. Tab memutar rak — kecuali ketika fokus ada pada sebuah kendali, di situ ia mengalah pada penelusuran biasa.

### Git, di bilah status

Tanda **⎇ cabang** menunjukkan Anda di cabang mana dan berapa berkas yang berubah; ia dibaca dari cakram, jadi tak memakan proses sama sekali. Sekali klik membuka riwayat lengkapnya, dan menunya membawa **Beda proyek**, **Anotasi**, permintaan tarik lewat `gh` milik Anda sendiri, serta **Susun pesan komit dengan KVASIR**.

### Papan Tugas (⌥⌘1)

Sebuah kanban per proyek yang tersimpan di `.nmoxtasks.json` — di samping kode Anda dan berversi bersamanya. Seret kartunya atau pindahkan dengan papan tik: **⌘↑/⌘↓** mengurutkan ulang, dan kartu yang dipindahkan tetap memegang fokus. Batas pekerjaan berjalan itu nasihat, bukan penghalang: kepalanya memerah dan tak ada yang menahan Anda. Tombol **Ikhtisar** menukar kolomnya dengan sebuah dasbor — yang sedang berjalan, selesai hari ini dan pekan ini, alir per hari, kartu yang menua — dan jam kerjanya (**Absen masuk**) mengukur waktu yang sebenarnya per kartu, dengan hanya satu jam berjalan di seluruh papan. **Standup** mengubah semua itu menjadi laporan siap tempel.

### Studio Blok (⌥⌘5)

Susunlah Web Component yang sungguhan dari kepingan bertipe yang saling mengunci, dengan cara Scratch: sarang yang tak sah ditolak, kodenya lahir sebagai elemen kustom yang berdiri sendiri, dan mengeklik sebuah kepingan menyorot baris-barisnya. Sebuah server pratinjau di memori memperlihatkan komponennya sungguh-sungguh, tersusun bersama komponen sah lainnya di pustaka Anda. Bolak-baliknya persis: membangkitkan ulang apa yang baru dibaca menghasilkan berkas yang sama, bita demi bita.

### Studio API (⌥⌘8)

Koleksi, permintaan, lingkungan dengan `{{variabel}}`, dan uji, tersimpan di `.nmoxapi.json` — rahasianya hanya di gantungan kunci, tak pernah di berkas itu. Setiap tanggapan mendapat nilai keamanan dari tajuknya sendiri. Impor dari curl, `.http`, OpenAPI, Postman, Insomnia, dan HAR; ekspor ke `.http`, dan salin sebagai curl atau sebagai `fetch`, dengan variabelnya sudah terisi.

### Studio Basis Data (⌥⌘7)

SQLite, PostgreSQL, MySQL, MariaDB, MongoDB, dan CouchDB, dengan penggeraknya sudah menyatu dan kata sandinya hanya di gantungan kunci. Konsolnya mengenali mesinnya, tiap pernyataan punya kisi hasilnya sendiri, dan barisnya disunting di dalam kisi itu bila ada kunci utama — dengan pratinjau UPDATE yang persis sebelum diterapkan, dan alasan yang jujur ketika sesuatu hanya bisa dibaca. Ekspor ke CSV atau JSON, dengan rumusnya dijinakkan.

### Studio Kontrak (⌥⌘6)

Pohon artefak Foundry dan Hardhat, **Berinteraksi** yang dituntun ABI dengan kembalian dan pembatalan yang terbaca, panel **Awasi** yang mengikuti blok dan peristiwa, serta **Pengawasan** dengan tabel gas, putusan ukuran EIP-170, dan buku alamat. **Tidak pernah ada kunci privat**: pengiriman memakai akun tak terkunci sebuah jaringan lokal, dan URL rahasia tinggal di gantungan kunci.

### Perancang Infrastruktur (⌥⌘9)

Sebuah kanvas untuk DigitalOcean, Hetzner, dan Cloudflare: selaraskan dengan apa yang benar-benar ada, segarkan untuk melihat selisihnya, dan hancurkan setumpuk sumber daya dengan biayanya terpampang. Sambungan yang tak sah menolak dengan lantang dan menyebut sebabnya, dan selama sebuah operasi awan berjalan, kanvasnya terkunci dengan pita yang mengatakannya.

### IRC (⌥⌘3)

Sebuah klien penuh di dalam IDE: TLS dengan pemeriksaan nama yang sungguhan, SASL, perluasan IRCv3, pelengkapan dengan Tab, penyorotan, URL yang terbuka di peramban bawaan, pencatatan ke cakram, penyaring milik Anda sendiri, dan daftar kanal yang Anda saring sambil mengetik.

### Situs web yang menyertai

**Bantuan ▸ Situs NMOX Studio (lokal)** menyajikan situs produk ini dari raknya sendiri, pada antarmuka lokal. Ia berbicara dalam tiga belas bahasa yang sama seperti IDE-nya; pemilihnya ada di kaki halaman.

### Peramban (⌥⌘4)

Sebuah peramban sungguhan di dalam IDE, dengan perkakas pengembangnya sendiri — konsol, DOM, jaringan, penyimpanan, dan panel untuk Vue, Svelte, dan Angular — sebab mesinnya tak membawa pemeriksa apa pun, dan yang ini milik kita. Ia sadar akan sumbernya: pilih sebuah elemen, buka baris yang melahirkannya, ubah gayanya di tempat, dan deklarasinya mendarat di lembar gaya asalnya. Menyimpan sebuah berkas memuat ulang halamannya, dan ada ukuran perangkat yang sungguhan untuk menguji tata letak Anda yang lentur.

<a id="7-docker"></a>
## 7. Docker

Tab Docker adalah panel kendali: keadaan mesin, kontainer, image, volume, dan jaringan, lengkap dengan mulai, henti, log, dan bersih-bersih. Perangkat HARBOR di rak menunjukkan hal yang sama dalam sekali pandang. Dan seperti sudah disebut: jalankan kontainer Postgres, MySQL, atau Mongo, dan Studio Basis Data menawarkan sambungan yang sudah jadi.

Tab **Dockerize** membuat `Dockerfile` kelas produksi, `.dockerignore`, dan berkas komposisi yang disesuaikan dengan rantai perkakas proyek Anda — Node, PHP-FPM dengan nginx, dan lainnya.

<a id="8-wizards-and-kits"></a>
## 8. Pemandu dan kit

Semuanya ada di *Berkas Baru…* dan di menu konteks proyek, dan semuanya **idempoten serta tidak pernah menimpa**: menjalankannya lagi hanya memperbarui apa yang menjadi miliknya sendiri dan membiarkan suntingan Anda; apa yang tidak boleh ditulis ulang mendarat di sebelahnya sebagai berkas `.suggested`.

### Kit standar

`robots.txt`, `sitemap.xml`, manifes web, `security.txt` sesuai RFC 9116, dan `humans.txt`, dibuat dari jawaban Anda.

### Kit PWA

Satu set ikon lengkap yang ditempa dari satu gambar, termasuk varian yang dapat dimasker; service worker yang mudah dibaca — cangkang aplikasi atau jaringan dahulu, Anda yang memilih —, halaman untuk saat tanpa jaringan, dan sambungan di `index.html` yang mengikat semuanya.

### Kit aksesibilitas

Aksesibilitas sebagai titik awal, bukan audit setelah semuanya jadi: `a11y.css` (cincin fokus yang terlihat, alat bantu untuk teks yang hanya dibaca pembaca layar, gaya untuk tautan lompat, dan blok bagi yang memilih lebih sedikit gerak), `A11Y-NOTES.md` berisi penelusuran lewat papan ketik dan pertanyaan yang tidak bisa dijawab otomatisasi mana pun, serta sambungan idempoten di `index.html` — bahasa, tautan lompat, lembar gaya. Viewport yang melarang perbesaran diperingatkan, tidak pernah ditulis ulang; apa yang tidak bisa diperbaiki kit ini disebutkan, bukan disentuh.

### Kit internasionalisasi

Dapat diterjemahkan sejak hari pertama, saudara kit aksesibilitas: `locales/en.json` dan `locales/es.json` (satu katalog per bahasa, kunci yang sama), `i18n.js` tanpa ketergantungan yang menerapkan katalog pada markah `data-i18n`, menjaga `<html lang>` tetap jujur, dan menampilkan kunci yang hilang sebagai dirinya sendiri, bukan sebagai kekosongan yang diam; ditambah `I18N-NOTES.md` — tanpa potongan yang disambung, `Intl` untuk tanggal dan angka, penelusuran kanan ke kiri, dan pseudolokalisasi.

### Kit kontrak (Web3)

Pilih satu rantai — Solidity dengan Foundry, Soroban, Solana, CosmWasm, ink!, Cairo, Move, Bitcoin dengan Miniscript, Clarity di Stacks, Cardano dengan Aiken, atau TON dengan Tact — dan sebuah nama kontrak, lalu kit menyiapkan awalan yang sudah terbukti langsung: manifes, kontrak, uji bawaan, dan CONTRACT-NOTES.md yang menyebut perangkat rak dan langkah sekali jalan. Kunci tidak pernah menyentuh IDE.

### Kit klasik

Tambahkan ke kode mana pun jQuery, MooTools, Prototype, Backbone dengan Underscore, atau Knockout, entah disertakan di dalam repositori (versi dipatok, sha256 dicatat) atau sebagai ketergantungan npm; ditambah kerangka webpack, grunt, gulp, atau bower.

<a id="9-quick-search-status-line-and-staying-oriented"></a>
## 9. Pencarian cepat, baris status, dan tetap tahu arah

### Tanda ⇄ melayani

Di baris status muncul tanda **⇄ melayani** setiap kali ada peladen yang hidup: jalannya IDE sendiri, perangkat yang melayani, dan perintah apa pun yang telah mencetak alamat lokal. Klik dan pilih satu: ia terbuka di peramban bawaan, atau di peramban sistem bila tab itu tidak sanggup menerimanya.

### ⌘I, pencari untuk segalanya

Satu kotak menjangkau proyek Anda (yang terkini dan yang dikenal), setiap perangkat rak — langsung ke kendalinya —, **peladen yang sedang hidup** (Enter membukanya di peramban), permintaan Studio API, sambungan dan tabel Studio Basis Data, kontrak, simpul infrastruktur, serta kartu Papan Tugas, dan hasilnya menyebut kolom tempat kartu itu berada.

### Baris status memberi tahu apa yang hidup

Di sebelah tanda peladen ada proyek yang sedang dibidik beserta rantai perkakasnya, dan cabang Git dengan jumlah berkas yang Anda ubah. Semua itu dibaca dari cakram atau dari catatan yang memang sudah disimpan produk ini: melihatnya tidak memakan proses sama sekali.

### Meja Kerja

Inilah pangkalan: proyek berjalan, berkas yang terbuka dan yang terkini, proyek terkini, dan peluncur untuk setiap permukaan. Selama ada yang berjalan, bagian **SEDANG BERJALAN** memimpin halaman — setiap perintah yang produk ini mulai untuk Anda, dengan alamatnya bila ia mengumumkan satu dan sejak jam berapa ia berjalan, ditambah setiap peladen yang dilayani perangkat rak. Setiap baris punya tombol **Buka** dan **Hentikan** yang sungguhan, terjangkau papan ketik maupun pembaca layar, sehingga satu jalan dapat dihentikan tanpa menjatuhkan yang lain. Semua judul di Meja Kerja adalah tombol sungguhan: Tab sampai ke sana, Enter membukanya. ⌘I menjangkau jalan yang sama: ketik «hentikan» lalu Enter menghentikan tepat yang itu. Yang Anda hentikan sendiri terbaca *dihentikan* di mana pun hasilnya dilaporkan, tidak pernah sebagai kegagalan.

### Pintasan Emacs (juga Eclipse dan IntelliJ)

Alat ▸ Opsi ▸ Peta Tombol mengganti seluruh profil: gerakan serta potong dan tempel ala Emacs di setiap penyunting, atau kumpulan Eclipse dan IDEA bila di sanalah ingatan jari Anda. Setiap pintasan NMOX terdaftar di kelima profil, jadi berganti profil tidak pernah merenggut pintasan studio dari Anda.

<a id="10-the-safety-nets-things-you-dont-have-to-do-anything-for"></a>
## 10. Jaring pengaman (yang tidak perlu Anda lakukan apa-apa untuk mendapatkannya)

### Kebangkitan sesi

Rak memotret apa yang sedang berjalan setiap beberapa detik. Penutupan paksa, kegagalan, `kill -9` — saat dijalankan lagi, sebuah pesan menawarkan untuk memulihkan persis sesi yang hilang, dengan satu klik.

### Jaminan tanpa yatim

Keluar dari IDE mematikan setiap proses yang ia mulai — peladen pengembangan, REPL, rantai, pengawas —, TERM dahulu, KILL kalau membangkang, keturunannya sekalian.

### BLACKBOX dan SONAR

Pasang **BLACKBOX** di rak Anda dan Anda punya perekam penerbangan: setiap awal dan setiap akhir, dengan lamanya, kecenderungannya, dan apa yang berubah sejak bangunan hijau terakhir. Yang Anda hentikan sendiri terbaca DIHENTIKAN — bukan hijau, bukan kegagalan, dan tidak pernah menjadi hal yang diminta dijelaskan kepada KVASIR. **SONAR** menunjukkan siapa yang menguasai porta Anda, disilangkan dengan Docker, dan dengan satu klik mengusir yang menduduki 3000.

### Berkas yang tidak pernah ditimpa

Keempat berkas kerja studio (`.nmoxapi.json`, `.nmoxdb.json`, `.nmoxweb3.json`, `.nmoxinfra.json`) dimuat ulang bila Anda menyuntingnya di luar IDE — tetapi bila ada perubahan yang belum disimpan, Anda ditanya, bukan ditimpa. Berkas yang rusak disingkirkan sebagai `.bak` dan dilaporkan, tidak pernah diganti diam-diam.

### TypeScript tanpa membangun

Proyek yang pintu masuknya `index.ts`, `main.ts`, atau `src/index.ts` berjalan dari IGNITION dengan pelucutan tipe milik Node sendiri (`--experimental-strip-types`, sejak Node 22.6; bawaan sejak 23.6 dan 22.18 LTS). Penolakan dari Node yang lebih tua diterjemahkan menjadi kalimat yang menyebut ambang itu.

### Bahasa Anda

NMOX Studio berbicara dalam tiga belas bahasa: English, Español, Français, Deutsch, Русский, Українська, Polski, Português (Brasil), Bahasa Indonesia, Filipino, Tiếng Việt, 简体中文, dan हिन्दी. Pilih bahasa Anda di **Opsi ▸ Umum ▸ Bahasa** — masing-masing tertulis dengan namanya sendiri, agar Anda selalu menemukan milik Anda. Pilihan itu ditulis ke pengaturan peluncuran Anda (`etc/nmoxstudio.conf`, sebagai argumen `--locale`) dan juga berlaku seketika. Yang berubah: menu, dialog, tip alat, baris status, layar sambutan, dan opsi. Yang tetap: kosakata panel depan rak (GO, STOP, EXPLAIN — itu label perangkat, seperti pada synthesizer), serta dialog platform yang lebih dalam, yang belum punya terjemahan. Mungkin Anda tidak pernah perlu memilih: pemasangan yang baru sudah berbicara dalam bahasa sistem Anda, bahkan dari negeri yang tidak pernah kami sebut — Taiwan, Singapura, Portugal, dan Quebec mendarat pada bahasanya sendiri, bukan pada bahasa Inggris, karena katalognya diberi nama menurut bahasa dan tidak pernah menurut negara.

### Pemeriksaan pembaruan harian

Tenang, sekali sehari: bila ada rilis yang lebih baru, sebuah pemberitahuan membawa Anda ke pengelola modul, pada tab pembaruannya, tempat pusat pembaruan memasang modul baru di tempatnya. Matikan di Opsi ▸ Umum.

<a id="11-learning-spaces"></a>
## 11. Ruang belajar

### Periksa pekerjaan Anda

Sebagian ruang membawa titik pemeriksaan: pilih salah satunya, dan **Berkas ▸ Periksa Pekerjaan Saya** benar-benar memeriksa latihannya — apa yang dinyatakan berkas diperiksa dengan Java murni, termasuk pemeriksaan *ketidakhadiran*, satu-satunya cara memastikan «Anda mengubah judulnya»: teks asli contoh itu harus sudah lenyap. Apa yang dinyatakan perintah lewat rantai perkakas ruang itu sendiri. Setiap ✗ menjawab dengan petunjuk ruang itu, dan bila gagal laporannya menawarkan **Jelaskan dengan KVASIR…**: titik yang gagal dan, untuk pemeriksaan berkas, berkas Anda sendiri, dibatasi dan di bawah izin yang menyebut persis apa yang keluar. Jawabannya terbaca seperti seorang tutor: apa yang harus diubah, lalu periksa lagi.

### Tutorial Anda sendiri

Letakkan berkas `*.json` di `~/.nmox/learn-catalog.d/` dan ia ikut masuk ke pemilih, dengan skema yang sama dengan bawaan; `slug` yang sama menggantikan yang bawaan. Sedang mengajar? Menulislah sambil membangun: jadikan latihan itu proyek biasa, lalu **Berkas ▸ Ekspor sebagai Ruang Belajar…** menyusun berkas itu untuk Anda — berkas contoh, `TUTORIAL.md` Anda, penggerak jalannya, dan titik pemeriksaan Anda — sudah diuji dengan pengurai milik pemilih itu sendiri sebelum ditulis, sehingga yang Anda serahkan kepada murid persis sama dengan yang akan dimuat pemilih mereka.

### Katalognya

*Ruang Belajar Baru…* menawarkan 93 tutorial bawaan — bahasa, kerangka kerja, dan pustaka. Masing-masing membuat proyek contoh kecil, tutorial yang dituntun, dan rak yang sudah terpasang **penafsir sungguhan**: Anda mengetik di rak dan penafsir yang hidup menjawab. Kenop ENGINE memilih di antara 37 penafsir; bila salah satu tidak ada, tombol INSTALL memasangnya di tempat itu juga sambil menampilkan kemajuannya di layar. Ruang-ruang itu tinggal di `~/.nmox/learn`, terpisah dari pekerjaan Anda yang sebenarnya.

### Langkah pertama, di layar sambutan

Kolom keempat mendaftar enam gerakan pertama — membuka proyek, menjalankan sesuatu di rak, melihat sebuah peladen hidup, bertanya kepada KVASIR tentang kode, mencoba ruang belajar, mengarahkan agen ke IDE — dan mencentang masing-masing dari catatan yang memang sudah disimpan produk ini. Setiap baris adalah pintu: sekali klik, jendela atau tindakan itu terbuka. Centang tidak pernah kembali kosong; kolomnya hilang bila keenamnya sudah selesai, atau bila Anda menekan **Sembunyikan daftar ini**.

### Tiga jawaban menu Bantuan

**Apa yang Baru…** memuat catatan rilis yang sedang Anda jalankan, disertakan dalam bangunannya sendiri; pada mula pertama setelah pembaruan, catatan itu terbuka sendiri berisi rilis yang belum pernah dilihat pemasangan Anda. **Laporkan Masalah…** menyusun laporan berisi lingkungan Anda dan empat puluh baris terakhir log, sudah disunting — folder rumah Anda menjadi `~`, nama masuk Anda `<user>`, dan apa pun yang menyerupai kredensial menjadi `[redacted]` —; Anda menyuntingnya, lalu **Buka di GitHub** mengisikan sebuah isu yang Anda kirim sendiri, atau Anda salin. Produk ini tidak pernah mengirim apa pun atas kemauannya sendiri. **Pintasan Papan Ketik…** mendaftar setiap pintasan NMOX di profil Anda yang aktif, dibaca dari peta tombol yang sedang berjalan, sehingga ia tidak mungkin menyimpang dari apa yang dilakukan menu.

<a id="12-when-somethings-wrong"></a>
## 12. Ketika ada yang tidak beres

### Dokter Lingkungan

Di menu Alat, ia menjajaki langsung 66 perkakas luar — node, npm, docker, forge, composer, gopls… — dan menunjukkan versi yang ditemukan serta perintah pemasangan bagi yang tidak ada.

### Tembok yang punya pintu

Bila peladen bahasa atau sebuah perkakas tidak ada, IDE memberi tahu perintah mana yang harus dijalankan, atau menawarkan menjalankannya; tidak pernah sekadar kegagalan. Satu tembok punya pintunya sendiri: TypeScript 7 tidak membawa tsserver, jadi bila TypeScript yang ditemukan adalah 7, penyunting mengatakannya sekali dan menawarkan jalur 5 — jalur yang ia pasang sendiri karena alasan yang sama. Bila sebuah porta sudah terpakai, pesan galat menyebut proses yang menduduki, dan SONAR mengusirnya.

### GO yang tidak melakukan apa-apa

Lihat layarnya: perangkat menjelaskan dirinya dengan kata-kata, dan tip pada tombol GO menampilkan perintah persis yang akan ia jalankan, supaya Anda bisa mencobanya di terminal.

### Aplikasi terbuka tanpa apa pun (macOS)

Tanpa jendela, tanpa galat, pada mula pertama setelah pemasangan: itu karantina Gatekeeper — lihat catatan di bab 1. Klik kanan lalu Buka, cukup sekali, dan beres selamanya. Log berada di bawah `~/Library/Application Support/nmoxstudio/…/var/log/` bila Anda perlu membuka isu.

<a id="appendix-the-files-nmox-studio-writes-and-what-to-commit"></a>
## Lampiran: berkas yang ditulis NMOX Studio (dan mana yang layak dikomit)

Semua yang disimpan IDE tentang sebuah proyek adalah berkas JSON yang terbaca di akar proyek, dirancang untuk dibagi dengan tim Anda.

| Berkas | Isinya | Dikomit? |
|---|---|---|
| `.nmoxapi.json` | Koleksi, permintaan, lingkungan, dan uji Studio API | **Ya** — rekan Anda menerima seluruh meja kerja Anda |
| `.nmoxdb.json` | Sambungan, kueri tersimpan, dan riwayat | **Ya** — kata sandi *tidak pernah* ada di sana (hanya di gantungan kunci) |
| `.nmoxweb3.json` | Jaringan dan buku alamat Studio Kontrak | **Ya** — alamat rahasia *tidak pernah* ada di sana (hanya di gantungan kunci) |
| `.nmoxinfra.json` | Kanvas infrastruktur: simpul, sambungan, sifat | **Ya** — token *tidak pernah* ada di sana (hanya di gantungan kunci) |
| `.nmoxtasks.json` | Papan Tugas: kolom, kartu, batas | **Ya** — tim berbagi satu papan; abaikan bila ingin pribadi |
| `.gas-snapshot` | Patokan gas per uji dari Foundry (GOVERNOR menjaganya) | **Ya** — begitulah kemunduran gas tertangkap saat tinjauan |
| `.env` | Peubah lingkungan Anda | **Tidak** — justru itulah gunanya `.env` |
| `*.bak` | Berkas kerja yang gagal diurai, disimpan untuk Anda | Tidak — ambil yang Anda perlukan, lalu hapus |

Sunting salah satu dari empat berkas `.nmox*.json` di luar IDE, atau tarik perubahan rekan Anda, dan studio yang bersangkutan memuat ulang dengan sendirinya — kecuali bila Anda punya perubahan yang belum disimpan di sana, maka ia bertanya lebih dahulu.

Di luar proyek: `~/NMOX` adalah meja kerja bawaan, percobaan tinggal di `~/.nmox/experiments`, ruang belajar di `~/.nmox/learn`, dan keadaan IDE sendiri — tata letak jendela, patch rak, preferensi — di direktori pengguna platform.
