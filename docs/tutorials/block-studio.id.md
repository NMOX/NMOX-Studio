# Tutorial: Studio Blok

<!-- languages -->
[English](block-studio.md) · [Español](block-studio.es.md) · [Français](block-studio.fr.md) · [Deutsch](block-studio.de.md) · [Русский](block-studio.ru.md) · [Українська](block-studio.uk.md) · [Polski](block-studio.pl.md) · [Português (Brasil)](block-studio.pt.md) · **Bahasa Indonesia** · [Filipino](block-studio.tl.md) · [Tiếng Việt](block-studio.vi.md) · [简体中文](block-studio.zh.md) · [हिन्दी](block-studio.hi.md) · [עברית](block-studio.he.md) · [العربية](block-studio.ar.md)
<!-- /languages -->

Studio Blok adalah penyusun ala Scratch untuk Web Component yang **sungguhan**.
Anda merangkai blok bertipe, dan ia membangkitkan elemen kustom yang berdiri
sendiri (shadow DOM, state, listener) — beserta server pratinjau hidup agar Anda
melihatnya berjalan. Klik sebuah blok untuk menyorot baris persis yang
dihasilkannya.

![Studio Blok — palet kepingan, kanvas dengan akar komponen, dan elemen kustom yang dibangkitkan dengan pemetaan klik-kepingan ke kode](../images/tabs/block-studio.png)

## Membukanya

`⌥⌘5`, atau tab **Studio Blok**.

## Langkah-langkah

1. **Beri nama elemen Anda.** Setiap elemen kustom butuh tag bertanda hubung.
   Mulai sebuah komponen dan beri tag seperti `hello-badge`.

2. **Tambahkan blok dari palet.** Seret blok **Element** (sebuah simpul DOM),
   beri teks; tambahkan medan **State**; tambahkan blok **Saat peristiwa**
   dengan **Alihkan kelas** di dalamnya. Hanya susunan sarang yang sah yang diizinkan —
   kanvas menampilkan slot jatuh yang sah dan menolak yang tidak sah, bahkan saat
   memuat.

3. **Baca kodenya.** Panel tengah menampilkan `text/javascript` yang dibangkitkan —
   sebuah elemen kustom yang lengkap. Klik blok mana pun dan baris yang
   dihasilkannya tersorot; pemetaannya persis.

4. **Lihat hidupnya.** Tekan **Pratinjau**. Studio Blok menyajikan komponen dari
   server di memori dan merendernya; `⇄` dan Pencarian cepat menampilkan URL
   hidupnya. Komponen dalam ruang kerja yang sama bahkan bisa saling memakai.

5. **Simpan.** **Simpan Komponen** menulis `src/components/<tag>.js` — atomik,
   tidak pernah menimpa berkas yang disunting tangan. Seluruh ruang kerja tinggal
   di `.nmoxblocks.json`; **Buka Komponen…** mengimpor ulang berkas yang Anda
   (atau studio) tulis, selama masih dalam dialek blok.

## Yang baru saja Anda pelajari

- Keluarannya adalah elemen kustom sungguhan tanpa kerangka kerja yang siap Anda kirim.
- Pemetaan blok↔kode berlaku dua arah: suntingan dalam dialek terimpor ulang dengan rapi.
- Satu ruang kerja memuat banyak komponen; berpindah komponen adalah batas urungkan.

## Berikutnya

- Susun komponen dari komponen — blok yang menyebut tag komponen saudaranya
  merendernya bersarang di pratinjau.
