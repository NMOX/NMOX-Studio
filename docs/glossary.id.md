# Glosarium

<!-- languages -->
[English](glossary.md) · [Español](glossary.es.md) · [Français](glossary.fr.md) · [Deutsch](glossary.de.md) · [Русский](glossary.ru.md) · [Українська](glossary.uk.md) · [Polski](glossary.pl.md) · [Português (Brasil)](glossary.pt.md) · **Bahasa Indonesia** · [Filipino](glossary.tl.md) · [Tiếng Việt](glossary.vi.md) · [简体中文](glossary.zh.md) · [हिन्दी](glossary.hi.md) · [עברית](glossary.he.md) · [العربية](glossary.ar.md)
<!-- /languages -->

Kata-kata yang dipakai NMOX Studio tetapi tidak dipakai IDE lain, ditambah
istilah NetBeans yang ikut terlihat. Setiap entri menjelaskan arti kata itu di
sini dan di mana Anda bisa membaca lebih lanjut.

<a id="the-rack"></a>
## Rak

**Rak Tugas** (⌘9) — Jendela tempat perkakas Anda berjalan. Setiap tugas (pasang, build, uji, sajikan,
lint, deploy) adalah sebuah *perangkat* yang dipasang di rak, seperti perangkat keras di sebuah studio.
[Panduan pengguna §4](user-guide.id.md#4-the-task-rack).

**Perangkat** — Satu perkakas di rak, misalnya VELOCITY (Vite), VERITAS (uji), atau
PURITY (lint). Sebuah perangkat punya panel depan dengan kenop, tombol, lampu,
dan layar kecil, serta panel belakang dengan *jack*. Ada 53 perangkat bawaan,
tercantum di [referensi perangkat](devices.md). Anda bisa menambahkan perangkat
sendiri sebagai berkas JSON di `~/.nmox/devices.d/`
([berkas perangkat](device-files.md)).

**Panel depan** (*faceplate*) — Sisi depan sebuah perangkat. Tekan **Tab** di rak untuk membaliknya dan melihat
panel belakang.

**Jack** — Colokan di panel belakang sebuah perangkat. Jack keluaran mengirim sinyal dan jack
masukan menerimanya. Ada tiga jenis sinyal:
- **Pemicu** (*trigger*) adalah satu denyut: “build selesai”, “OK”, “FAIL”.
- **Gerbang** (*gate*) tetap menyala atau mati: “server sedang aktif”.
- **Data** membawa teks, seperti URL atau sebaris keluaran.

**Kabel** — Sambungan dari jack keluaran ke jack masukan. Sambungkan jack OK sebuah build
ke jack RUN milik penjalan uji, dan uji berjalan setiap kali build berhasil.
Untuk menyambungkan dua jack, seret dari satu ke yang lain, atau klik satu lalu
klik yang lain.

**Patch** — Satu rak utuh: perangkatnya, pengaturannya, dan kabelnya. Disimpan di samping
proyek sebagai `.nmoxrack.json`, jadi layak dikomit.

**Preset** — Patch siap pakai yang bisa Anda muat dari menu **Preset ▾** di rak, misalnya
*Ship Gate* atau *E2E Loop*. Simpan patch apa pun ke `~/.nmox/presets.d/` dan
patch itu juga muncul di menu tersebut.

**Rak awal** — Patch yang didapat sebuah proyek saat pertama kali Anda membukanya, dipilih menurut
jenis proyeknya: konsol Vite untuk aplikasi Vite, jalur jalankan, debug, dan uji
untuk sebuah crate Cargo, dan seterusnya.

**Jalur** (*lane*) — Dua arti, keduanya tentang menjalankan sesuatu:
- Sebuah **alur**: rantai perangkat yang disambung dengan kabel, seperti pasang →
  build → uji. Beberapa jalur bisa berjalan berdampingan, dan QUORUM menunggu
  semuanya selesai.
- **Jalur AUTO** sebuah perangkat: perintah yang dipilihnya untuk proyek ini. Pada
  AUTO, perangkat uji menjalankan `npm test` di proyek Node dan
  `cargo test` di proyek Rust.

**Bagikan… / Impor…** — Simpan sebuah rak ke berkas untuk orang lain, atau muat rak dari mereka. Sebelum
apa pun dipasang, Impor menampilkan semua isi berkas itu, dan setiap perangkat
datang dalam keadaan mati.

**Galeri Rak** — **Alat ▸ Galeri Rak…** mendaftar rak komunitas, preset, rak awal, dan rak
yang Anda simpan sendiri. Setiap entri menunjukkan kegunaannya dan perkakas yang
dibutuhkannya. [Rak komunitas](racks.md).

<a id="projects-and-running"></a>
## Proyek dan menjalankan

**Mengarahkan** / **proyek yang diarahkan** (*aim*) — Proyek yang sedang dikerjakan IDE. Membuka sebuah proyek berarti
mengarahkannya: rak, studio, baris status, dan Jalankan semuanya mengikuti proyek
yang diarahkan. Mengarahkan proyek lain mengalihkan semuanya, dan apa pun yang
masih berjalan dihentikan lebih dulu, setelah Anda ditanya.

**Kepercayaan Ruang Kerja** — Pertanyaan yang diajukan NMOX Studio sebelum pertama kali menjalankan kode milik
sebuah proyek (skrip, build, uji). Jika Anda menjawab **Tetap Aman**, tidak ada
apa pun dari proyek itu yang dijalankan. Jawaban Anda diingat per folder.

**▶ dan ■** — Jalankan dan Hentikan di bilah alat. ▶ (F6) menjalankan proyek yang diarahkan. ■ (⌥⌘.)
menghentikan setiap perintah yang dimulai NMOX Studio untuk Anda.

**Tanda ⇄** / **menyajikan** — Ketika sesuatu yang Anda jalankan mencetak alamat lokal, seperti
`http://localhost:5173/`, alamat itu muncul di baris status sesudah simbol ⇄
(**⇄ menyajikan:**). Server yang berjalan itu adalah sebuah *penyajian*. Klik
alamatnya untuk membukanya di Peramban. Pencarian Cepat mendaftar penyajian di
bawah *Server Aktif*.

**Eksperimen** — Proyek sekali pakai yang dibuat dari sebuah templat di `~/.nmox/experiments`.
Dependensinya sudah terpasang dan ia sudah dipercaya. **Promosikan…** untuk
menyimpannya, atau **Buang…**. **Berkas ▸ Eksperimen Baru…**.

**Ruang Belajar** — Tutorial yang dituntun untuk sebuah bahasa atau kerangka kerja. Ia membuat
proyek sungguhan, sebuah penelusuran, dan rak yang sudah disiapkan dengan REPL
hidup, dan **Berkas ▸ Periksa Hasil Kerja** memeriksa latihan Anda. Ada 93.
**Berkas ▸ Ruang Belajar Baru…**.

**PREFLIGHT** — Perangkat pemeriksa sebelum rilis. Ia menjalankan pemeriksaan yang ditetapkan proyek
Anda (lint, tipe, uji, build) sebagai satu hasil lulus atau gagal.

**LANGKAH PERTAMA** (*First Steps*) — Daftar periksa di tab Selamat Datang. Langkah-langkahnya tercentang sendiri
saat Anda melakukannya dan tidak pernah kembali kosong.

<a id="the-windows"></a>
## Jendela-jendela

**Studio** — Jendela dengan perkakasnya sendiri untuk satu jenis pekerjaan. Ada
lima: **Studio API** (⌥⌘8), **Studio Basis Data** (⌥⌘7), **Studio Kontrak** (⌥⌘6,
kontrak pintar), **Studio Blok** (⌥⌘5, komponen web yang disusun dari blok),
dan **Perancang Infrastruktur** (⌥⌘9, infrastruktur awan). Masing-masing menyimpan
pekerjaannya di samping proyek dalam sebuah berkas `.nmox*.json`. **Studio Proyek**
memakai nama yang sama tetapi berisi pohon berkas dan templat proyek.

**Meja Kerja** (⌥⌘0) — Pangkalan Anda: apa yang sedang berjalan, apa yang terbuka, serta proyek dan
berkas terkini Anda.

**Papan Tugas** (⌥⌘1) — Papan kanban untuk setiap proyek, dengan sprint dan jam kerja, disimpan sebagai
`.nmoxtasks.json`.

**Selamat Datang** — Tab awal: tindakan untuk memulai, proyek terkini, kolom *PERKAKAS*
yang mendaftar setiap jendela, dan LANGKAH PERTAMA.

<a id="ai"></a>
## AI

**KVASIR** — Nama fitur-fitur AI NMOX Studio: bertanya, menyunting, melengkapi, menjelaskan,
dan menyusun pesan komit. Ia bekerja dengan Claude, ChatGPT, atau Gemini memakai
kunci API Anda sendiri, yang disimpan di gantungan kunci sistem operasi. Setiap
fitur meminta persetujuan Anda sekali dan menyebut persis apa yang akan dikirimnya.
Tidak ada yang dikirim sampai Anda memakai sebuah fitur. Rilis-rilis sebelumnya
menyebutnya ORACLE.

**Agent Port** — **Alat ▸ Agent Port (MCP)…** memberi agen AI yang berjalan di mesin Anda,
seperti asisten pemrograman, akses hanya-baca ke keadaan IDE lewat MCP: berkas
yang terbuka, diagnostik, jalannya perintah, simbol. Ia hanya-baca menurut
rancangannya dan hanya mendengarkan di mesin Anda sendiri.
[Tutorial](tutorials/agent-port.md).

<a id="netbeans-terms-you-may-see"></a>
## Istilah NetBeans yang mungkin Anda lihat

NMOX Studio dibangun di atas NetBeans Platform, dan beberapa katanya ikut
terlihat.

**Modul** / **NBM** — Satu bagian dari aplikasi. *NBM* adalah berkas tempat sebuah modul dikirimkan.
**Alat ▸ Plugin** memasang pembaruan modul demi modul.

**Pusat pembaruan** — Tempat **Alat ▸ Plugin ▸ Pembaruan** mengambil versi baru modul-modul NMOX
Studio. Ia membaca katalog yang diterbitkan bersama setiap rilis GitHub.

**userdir** — Folder tempat NMOX Studio menyimpan pengaturan, tata letak jendela, log, dan
pembaruan yang terpasang. Untuk menemukannya, buka **Bantuan ▸ About**. Lognya ada di
`var/log/messages.log`. Untuk mulai dengan pengaturan yang bersih, jalankan dengan
`--userdir <an empty folder>`.

**Opsi** / **Settings…** — Dialog preferensi. Letaknya di **Alat ▸ Opsi** di Windows dan Linux,
dan di **NMOX Studio ▸ Settings…** di macOS.

**Item tindakan** — Jendela yang mendaftar masalah yang ditemukan di proyek, termasuk hasil lint
dan pemeriksaan tipe dari rak. Klik sebuah masalah untuk menuju baris itu.
