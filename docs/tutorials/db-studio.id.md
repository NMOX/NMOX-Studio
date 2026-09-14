# Tutorial: Studio Basis Data

<!-- languages -->
[English](db-studio.md) · [Español](db-studio.es.md) · [Français](db-studio.fr.md) · [Deutsch](db-studio.de.md) · [Русский](db-studio.ru.md) · [Українська](db-studio.uk.md) · [Polski](db-studio.pl.md) · [Português (Brasil)](db-studio.pt.md) · **Bahasa Indonesia** · [Filipino](db-studio.tl.md) · [Tiếng Việt](db-studio.vi.md) · [简体中文](db-studio.zh.md) · [हिन्दी](db-studio.hi.md) · [עברית](db-studio.he.md) · [العربية](db-studio.ar.md)
<!-- /languages -->

Studio Basis Data adalah rangkaian alat basis data untuk SQLite, PostgreSQL,
MySQL/MariaDB, MongoDB, dan CouchDB — penggerak sudah disertakan, konsol yang
mengenali jenis mesinnya, dan kisi hasil yang bisa Anda sunting di tempat.
Tutorial ini memakai SQLite karena tidak butuh server.

![Sambungan SQLite, sebuah kueri, baris hidup di kisi — dan bilah status memberi alasan jujur ketika sebuah kisi hanya bisa dibaca](../images/db-studio.png)

## Membukanya

`⌥⌘7`, atau tab **Studio Basis Data**.

## Langkah-langkah

1. **Buat sambungan SQLite.** Klik **Tambah**, pilih **SQLite**, lalu pilih jalur
   berkas (pemilih bergaya simpan memungkinkan Anda membuat `.db` baru). Ia muncul
   di pohon sambungan.

2. **Jalankan sedikit SQL.** Di konsol, ketik dan jalankan:

   ```sql
   CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT, active BOOLEAN);
   INSERT INTO users (name, active) VALUES ('Ada', 1), ('Bob', 0);
   SELECT * FROM users;
   ```

   Setiap pernyataan mendapat kisi hasilnya sendiri di bawah, lengkap dengan waktunya.

3. **Sunting baris di kisi.** Klik ganda sel `name` milik Bob, ubah, lalu tekan
   **Terapkan**. Studio Basis Data hanya mengizinkan suntingan di kisi bila ia bisa
   menyusun `UPDATE` satu baris yang aman (satu tabel, ada kunci utama) — ia
   menunjukkan SQL persisnya sebelum dijalankan, lalu mengueri ulang demi
   kebenaran. Bila sebuah baris tidak bisa disunting dengan aman, ia mengatakan
   alasannya.

4. **Ekspor.** Klik kanan kisi mana pun → **Ekspor sebagai CSV / JSON**. Ekspor CSV
   menjinakkan injeksi rumus lembar kerja secara otomatis.

5. **EXPLAIN sebuah kueri.** Pilih sebuah `SELECT` dan tekan **EXPLAIN** untuk
   rencana kueri asli mesin itu.

6. **Biarkan KVASIR menjelaskan kegagalan.** Jalankan `SELECT * FROM user;`
   (perhatikan salah ketiknya). Di bawah pesan galat muncul tombol **Jelaskan…**.
   Tekan, dan dialog persetujuan menyebut persis apa yang akan dikirim — SQL yang
   Anda jalankan (termasuk nilai literal di dalamnya), pesan galat, dan jenis
   mesinnya; tidak pernah sambungan, kata sandi, atau baris apa pun. Setujui, dan
   KVASIR menjelaskan galatnya serta menyarankan perbaikan, di jendela percakapan
   yang menerima pertanyaan lanjutan.

## Yang baru saja Anda pelajari

- Kata sandi hanya di gantungan kunci sistem operasi, tidak pernah di `.nmoxdb.json`.
- Konsolnya mengenali jenis mesin: SQL untuk mesin SQL, konsol dokumen JSON untuk
  MongoDB/CouchDB.
- Riwayat dan kueri tersimpan bertahan per proyek; berkas `.env` menawarkan
  sambungan `DATABASE_URL`/`DB_*` miliknya secara otomatis.

## Berikutnya

- Menjalankan basis data di Docker? Studio Basis Data menawarkan sambungan untuknya —
  lihat [Panel Docker](docker-panel.id.md).
