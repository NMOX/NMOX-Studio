# NMOX Studio — Panduan pengguna

> Terjemahan sebagian: bab 1–2 tersedia dalam bahasa Indonesia. Selebihnya, lihat [panduan lengkap dalam bahasa Inggris](user-guide.md).

Cara memakai produk ini. Panduan ini menyusuri fitur sesuai urutan yang akan Anda temui: pemasangan, peluncuran pertama, proyek, rak, studio, wisaya, dan jaring pengaman.

---

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
