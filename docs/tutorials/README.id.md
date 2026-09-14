# Tutorial NMOX Studio

<!-- languages -->
[English](README.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · [Українська](README.uk.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt.md) · **Bahasa Indonesia** · [Filipino](README.tl.md) · [Tiếng Việt](README.vi.md) · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

Panduan singkat yang Anda kerjakan sendiri untuk sistem-sistem yang membuat
NMOX Studio berbeda dari IDE biasa. Masing-masing selesai dalam sekali duduk:
buka jendelanya, ikuti langkahnya, dan Anda sudah memakai fiturnya sungguhan.

Untuk rujukan yang lebih luas (pemasangan, setiap menu, setiap jaring pengaman)
lihat [Panduan pengguna](../user-guide.id.md). Untuk daftar perangkat lengkap lihat
[devices.md](../devices.md).

## Sistem-sistemnya

| Tutorial | Yang akan Anda lakukan | Dibuka dengan |
|----------|----------------|------------|
| [Rak Tugas](the-task-rack.id.md) | Merangkai patch jalankan→monitor dan melihatnya memicu | ⌘9 / tab Rak Tugas |
| [Tulis perangkat Anda sendiri](your-own-device.id.md) | Menambahkan perangkat rak dengan penyunting teks — tanpa Java, tanpa mulai ulang | `~/.nmox/devices.d/` |
| [Meja Kerja](workbench.id.md) | Memakai pangkalan untuk berpindah antara proyek dan perkakas | ⌥⌘0 |
| [Studio Proyek](project-studio.id.md) | Membuat kerangka proyek dan menjalankannya tanpa terminal | tab Studio Proyek |
| [Studio API](api-studio.id.md) | Mengirim permintaan, menguji hasilnya, membaca nilai keamanannya | ⌥⌘8 |
| [Studio Basis Data](db-studio.id.md) | Menyambung ke SQLite dan menyunting baris di kisi | ⌥⌘7 |
| [Studio Kontrak](contract-studio.id.md) | Mengompilasi, menerapkan ke rantai lokal, dan memanggil kontrak | ⌥⌘6 (Web3) |
| [Perancang Infrastruktur](infra-designer.id.md) | Menggambar droplet + firewall dan menguji penerapan tanpa efek | ⌥⌘9 |
| [Studio Blok](block-studio.id.md) | Membangun Web Component dari blok yang saling mengunci | ⌥⌘5 |
| [Menyunting banyak bahasa & debugging](polyglot-editing-and-debugging.id.md) | Memasang titik henti di aplikasi Node dan mengenainya | buka proyek apa saja |
| [Dari peramban ke sumber](browser-to-source.id.md) | Mengeklik elemen di halaman, mendarat di sumbernya, mengubah gayanya dari DevTools | ⌥⌘4 → DevTools → DOM |
| [Agent Port (MCP)](agent-port.id.md) | Mengarahkan agen AI ke keadaan hidup IDE — hanya-baca menurut rancangannya | Alat ▸ Agent Port (MCP)… |
| [Panel Docker](docker-panel.id.md) | Memeriksa kontainer dan men-dockerize proyek | tab Panel Docker |
| [Papan Tugas dan sprint](task-board.id.md) | Menjalankan kanban dengan jam kerja, standup sekali klik, dan burndown sprint dari satu berkas yang dikomit | ⌥⌘1 |
| [Tunjukkan ke seisi ruangan](show-it-to-a-room.id.md) | Mempresentasikan, berbagi, dan mengambil tangkapan layar dari dalam IDE — dari Mode presentasi sampai Salin Pohon Proyek sebagai Markdown | Tampilan ▸ Mode presentasi |
| [KVASIR](kvasir.id.md) | Bertanya kepada AI mengapa sebuah jalannya gagal | Rak → KVASIR |
| [Jelaskan apa saja](explain-anything.id.md) | Memakai empat wajah KVASIR: jalannya, kode, tanggapan API, galat basis data | di mana pun ada yang gagal |
| [Pindah dari Postman](migrating-from-postman.id.md) | Mengimpor koleksi, tangkapan HAR, dan lainnya — rahasia masuk ke gantungan kunci | ⌥⌘8 → Impor… |
| [Image Kit (Web)](image-kit.id.md) | Memampatkan gambar proyek: JPEG yang lebih kecil, saudara WebP, laporan yang jujur | Berkas ▸ Tambahkan ke Proyek ▸ Image Kit (Web)… |
| [Ruang belajar](learning-spaces.id.md) | Menyiapkan kotak pasir terpandu dengan REPL yang hidup | Ruang Belajar Baru… |
| [Wisaya & kit](wizards-and-kits.id.md) | Menambahkan PWA, berkas standar, atau kerangka web klasik | Berkas ▸ Tambahkan ke Proyek |

> **Catatan tentang pintasan.** Di macOS studio-studio tinggal di keluarga
> `⌥⌘` (Option-Command) — `⌥⌘6`–`⌥⌘9`, `⌥⌘5`, `⌥⌘0` — karena kombinasi
> `⇧⌘` biasa sudah dipakai platform. Di Linux/Windows pengubahnya adalah
> `Alt+`; menunya (Jendela ▸ …) selalu bekerja apa pun keadaannya.

Peluncuran pertama menampilkan tiga tab — Selamat Datang, Rak Tugas, dan
Peramban — dengan Studio Proyek, Meja Kerja, dan Penjelajah NPM tertambat di
sampingnya. Setiap jendela lain berjarak satu pintasan dan tercantum di kolom
PERKAKAS halaman Selamat Datang.
