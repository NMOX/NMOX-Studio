# Tutorial: Image Kit (Web) — mampatkan gambar Anda

<!-- languages -->
[English](image-kit.md) · [Español](image-kit.es.md) · [Français](image-kit.fr.md) · [Deutsch](image-kit.de.md) · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · [Polski](image-kit.pl.md) · [Português (Brasil)](image-kit.pt.md) · **Bahasa Indonesia** · [Filipino](image-kit.tl.md) · [Tiếng Việt](image-kit.vi.md) · [简体中文](image-kit.zh.md) · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

Gambar biasanya hal terberat yang dikirim sebuah situs. Image Kit menemukan
JPEG dan PNG proyek Anda lalu memampatkannya untuk web: saudara `.min.jpg` yang
lebih kecil lewat penyandian ulang Java murni (tak ada yang perlu dipasang),
pengecilan ukuran opsional, dan saudara `.webp` lewat `cwebp` milik Anda sendiri
bila terpasang. Dalam pembuktian langsung untuk rilis ini, sebuah wallpaper
17,8 MB menjadi `.min.jpg` 347 KB dan `.webp` 342 KB — 98% lebih kecil.

## Hukum yang dipegangnya

- **Berkas asli tidak pernah disentuh.** Keluarannya adalah saudara
  (`photo.min.jpg`, `photo.webp`), dan keluaran yang sudah ada dilewati dan
  disebutkan — tidak pernah ditimpa.
- **“Optimasi” yang tidak menghemat apa-apa dibuang**: pemampatan yang
  menghemat kurang dari 10% dihapus dan dilaporkan sebagai *sudah padat*,
  alih-alih mengirim berkas “teroptimasi” yang lebih besar. (Keluaran yang
  diubah ukurannya tetap disimpan — piksel yang lebih sedikit memang tujuannya.)
- **Penyandian ulang PNG sengaja tidak ada.** ImageIO tidak bisa mengalahkan
  pengoptimal PNG sungguhan, jadi untuk PNG kemenangan yang jujur adalah saudara WebP.

## Langkah-langkah

1. **Bidik sebuah proyek** lalu pilih **Berkas ▸ Tambahkan ke Proyek ▸ Image Kit (Web)…**.
   Dialognya memberi tahu berapa gambar yang ditemukan dan total bobotnya
   (node_modules dan keluaran bangunan dilewati, begitu pula keluaran `.min.`
   miliknya sendiri — memampatkan hasil pemampatan akan menumpuk kehilangan).

2. **Pilih pemampatan Anda.** Kualitas JPEG (85 tak terlihat hilang / 80 bawaan
   web / 70 agresif), lebar maksimum opsional (2560 hero retina / 1600 konten /
   800 gambar mini), dan — bila `cwebp` ada di PATH Anda — saudara WebP. Bila
   tidak ada, kotak centangnya mengatakannya dan di mana mendapatkannya
   (`brew install webp`); Dokter Lingkungan juga menjajakinya.

3. **Baca laporannya.** Per berkas: apa yang ditulis, ukuran sebelum → sesudah,
   atau alasan jujur mengapa tidak ada yang ditulis (“sudah ada”, “sudah padat”).
   Total bita yang dihemat ada di atas, bersama potongan `<picture>` siap salin
   yang menyajikan WebP bila didukung dan kembali ke berkas asli bila tidak.

## Yang baru saja Anda pelajari

- Optimasi gambar web tanpa perkakas wajib — dan `cwebp` milik Anda sendiri
  bila Anda memilikinya.
- Hukum keluarga kit, tidak pernah menimpa dan laporan yang jujur, berlaku
  juga untuk piksel.
