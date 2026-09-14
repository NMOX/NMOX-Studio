# Hướng dẫn: Image Kit (Web) — nén ảnh của bạn

<!-- languages -->
[English](image-kit.md) · [Español](image-kit.es.md) · [Français](image-kit.fr.md) · [Deutsch](image-kit.de.md) · [Русский](image-kit.ru.md) · [Українська](image-kit.uk.md) · [Polski](image-kit.pl.md) · [Português (Brasil)](image-kit.pt.md) · [Bahasa Indonesia](image-kit.id.md) · [Filipino](image-kit.tl.md) · **Tiếng Việt** · [简体中文](image-kit.zh.md) · [हिन्दी](image-kit.hi.md) · [עברית](image-kit.he.md) · [العربية](image-kit.ar.md)
<!-- /languages -->

Ảnh thường là thứ nặng nhất mà một trang web gửi đi. Image Kit tìm các tệp
JPEG và PNG trong dự án của bạn rồi nén chúng cho web: các bản `.min.jpg`
nhỏ hơn đặt cạnh tệp gốc, nhờ mã hóa lại bằng Java thuần (không phải cài gì),
tùy chọn thu nhỏ kích thước, và các bản `.webp` đi kèm qua chính `cwebp` của
bạn nếu đã cài. Trong lần chứng thực trực tiếp cho bản phát hành này, một hình
nền 17,8 MB đã thành một `.min.jpg` 347 KB và một `.webp` 342 KB — nhỏ hơn
98%.

## Những luật nó giữ

- **Tệp gốc không bao giờ bị đụng tới.** Kết quả là tệp đặt cạnh
  (`photo.min.jpg`, `photo.webp`), và một kết quả đã tồn tại sẽ được bỏ qua
  kèm lời báo — không bao giờ bị ghi đè.
- **Một “tối ưu” chẳng tiết kiệm gì sẽ bị bỏ đi**: một lần nén thu lại chưa
  tới 10% sẽ bị xóa và báo là *đã gọn sẵn*, thay vì gửi đi một tệp “đã tối ưu”
  còn to hơn. (Một kết quả đã đổi kích thước thì luôn được giữ — pixel nhỏ hơn
  mới là mục đích.)
- **Cố ý không mã hóa lại PNG.** ImageIO không thắng nổi một trình tối ưu PNG
  thật, nên với PNG, cái lợi trung thực là bản WebP đi kèm.

## Các bước

1. **Nhắm vào một dự án** và chọn **Tệp ▸ Thêm vào dự án ▸ Image Kit (Web)…**.
   Hộp thoại cho bạn biết nó tìm thấy bao nhiêu ảnh và tổng dung lượng
   (node_modules và các thư mục kết quả dựng được bỏ qua, cả những kết quả
   `.min.` của chính nó — nén lại một lần nén sẽ cộng dồn mất mát).

2. **Chọn cách nén.** Chất lượng JPEG (85 mắt thường không thấy mất / 80 mặc
   định cho web / 70 mạnh tay), một chiều rộng tối đa tùy chọn (2560 ảnh lớn
   cho màn retina / 1600 ảnh nội dung / 800 ảnh thu nhỏ), và — nếu `cwebp` có
   trong PATH — các bản WebP đi kèm. Nếu không có, ô đánh dấu nói rõ điều đó
   và chỗ lấy nó (`brew install webp`); Trình chẩn đoán môi trường cũng dò nó.

3. **Đọc bản báo cáo.** Với từng tệp: đã ghi ra gì, dung lượng trước → sau,
   hoặc lý do trung thực vì sao không có gì (“đã tồn tại”, “đã gọn sẵn”). Tổng
   số byte tiết kiệm được nằm ở đầu, cùng một đoạn `<picture>` sẵn để sao chép,
   phục vụ WebP ở nơi hỗ trợ và lùi về ảnh gốc ở nơi không.

## Bạn vừa học được gì

- Tối ưu ảnh cho web mà không cần công cụ nào — và dùng chính `cwebp` của
  bạn khi có.
- Luật không ghi đè và báo cáo trung thực của họ bộ công cụ cũng áp dụng cho
  pixel.
