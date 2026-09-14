# Tutorial: menulis perangkat rak Anda sendiri

<!-- languages -->
[English](your-own-device.md) · [Español](your-own-device.es.md) · [Français](your-own-device.fr.md) · [Deutsch](your-own-device.de.md) · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · [Polski](your-own-device.pl.md) · [Português (Brasil)](your-own-device.pt.md) · **Bahasa Indonesia** · [Filipino](your-own-device.tl.md) · [Tiếng Việt](your-own-device.vi.md) · [简体中文](your-own-device.zh.md) · [हिन्दी](your-own-device.hi.md) · [עברית](your-own-device.he.md) · [العربية](your-own-device.ar.md)
<!-- /languages -->

*Sekali duduk. Anda akan menambahkan perangkat ke rak dengan penyunting
teks, menekan tombolnya, melihatnya menjalankan perintah sungguhan, dan
merangkai keluarannya ke MONITOR — tanpa menulis satu baris Java pun.*

Baru di 2.0.0. Rak ini hadir dengan lima puluh tiga perangkat dan,
sampai sekarang, hanya satu cara untuk menambahkan yang kelima puluh
empat: membuat plugin NetBeans. Inilah cara yang lain.

![Rak Tugas: rak perangkat di sebelah kiri adalah tempat perangkat dari ~/.nmox/devices.d muncul, di samping perangkat bawaan](../images/tabs/the-task-rack.png)

## 1. Buat foldernya

```bash
mkdir -p ~/.nmox/devices.d
```

Itulah seluruh langkah pemasangannya. Rak membaca folder ini saat
dibutuhkan, jadi tidak ada yang perlu dimulai ulang.

## 2. Tulis perangkatnya

Simpan ini di `~/.nmox/devices.d/counter.json`:

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

Setiap bagiannya punya tugas: **kenop** menjadi `{{kind}}` di dalam
perintah, peran **QUERY** mewarnai tombolnya biru (hukum warna: biru
bertanya, hijau berbuat, merah menghentikan), dan dua porta membuatnya
bisa dirangkai.

## 3. Pasang di rak

Buka **Rak Tugas** (`⌘9`, atau tab Rak Tugas) dan lihat laci **Amati**
di rak perangkat. COUNTER ada di sana, dengan tagline Anda di bawahnya.
Seret ke sebuah rel.

Arahkan kursor ke kartu *Cara memakai*-nya — itulah teks `usage` Anda,
dan karena itulah formatnya menuntut dua baris sungguhan.

## 4. Tekan tombolnya

> Perhatikan tidak ada baris `units`: rak mengukur mukanya dan memilih
> tinggi terkecil yang muat (yang ini butuh 2U untuk kenopnya).
> Nyatakan `units` hanya bila Anda menginginkan ruang lebih.

Arahkan rak ke sebuah proyek git, putar **KIND** ke `js`, lalu tekan
**COUNT**.

Tekanan pertama memunculkan konfirmasi **Kepercayaan Ruang Kerja**,
karena berkas perangkat menjalankan perintah sungguhan dan tuan rumah
menjaga setiap peluncuran dengan cara yang sama seperti perangkat
bawaan. Izinkan, dan LCD menampilkan perintahnya, lalu baris terakhir
keluarannya. Jack DONE berdenyut hijau.

Tolak, dan tidak ada yang diluncurkan — penolakan itulah fiturnya.

## 5. Rangkai

Seret kabel dari **OUT** milik COUNTER ke **TAP** milik MONITOR. Tekan
COUNT sekali lagi: setiap baris mendarat di monitor, karena porta
`OUT`/`DATA` yang dinyatakan menerima keluaran jalannya tanpa
konfigurasi tambahan.

Sekarang seret dari tick milik TEMPO ke masukan **COUNT** milik
COUNTER. Perangkat yang Anda tulis di penyunting teks kini berjalan
mengikuti jam.

## 6. Rusak dengan sengaja

Sunting berkasnya dan ganti perintahnya dengan sesuatu yang memakai
pipa:

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

Simpan, dan COUNTER *menghilang* dari rak perangkat. Itulah format
yang menolak baris shell: sebuah perintah adalah larik argv, supaya
pembacanya — Anda enam bulan lagi, atau rekan yang meninjau berkasnya —
bisa melihat persis apa yang akan dijalankan. Log IDE menyebut berkas
mana yang dilewati dan mengapa:

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

Kembalikan bentuk larik, dan perangkatnya kembali. Hal yang sama
berlaku untuk perkakas yang dinamai lewat jalur (`./x.sh`),
`{{variable}}` yang tidak dikenal, atau `usage` satu baris: berkasnya
dilewati utuh alih-alih dimuat setengah, karena perangkat yang labelnya
berbohong lebih buruk daripada tidak ada perangkat.

## Yang baru saja Anda pelajari

- Perangkat adalah sebuah **berkas**: `~/.nmox/devices.d/*.json`,
  dibaca saat dibutuhkan, tanpa mulai ulang, tanpa build.
- Kenop menjadi `{{variables}}`; peran memilih warna; porta membuatnya
  bisa dirangkai dan keluarannya terbaca.
- **Tuan rumah memegang hukumnya** — kepercayaan ruang kerja pada
  setiap peluncuran, hukum warna, kosakata porta, hukum rak perangkat —
  sehingga berkas perangkat tidak bisa menyatakan perintah tanpa
  penjaga atau GO berwarna merah, sekeras apa pun ia mencoba.
- Penolakan terdengar lantang di log dan berlaku total.

## Selanjutnya

- [device-files.md](../device-files.md) — referensi lengkap
- [Rak Tugas](the-task-rack.id.md) — merangkai, gerbang, dan prasetel
- [device-spi.md](../device-spi.md) — SPI Java, untuk perangkat yang
  butuh keadaan sungguhan: lukisan kustom, polling, sambungan jangka
  panjang
