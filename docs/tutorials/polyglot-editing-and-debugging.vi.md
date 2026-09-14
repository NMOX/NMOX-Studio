# Hướng dẫn: Soạn thảo và gỡ lỗi đa ngôn ngữ

<!-- languages -->
[English](polyglot-editing-and-debugging.md) · [Español](polyglot-editing-and-debugging.es.md) · [Français](polyglot-editing-and-debugging.fr.md) · [Deutsch](polyglot-editing-and-debugging.de.md) · [Русский](polyglot-editing-and-debugging.ru.md) · [Українська](polyglot-editing-and-debugging.uk.md) · [Polski](polyglot-editing-and-debugging.pl.md) · [Português (Brasil)](polyglot-editing-and-debugging.pt.md) · [Bahasa Indonesia](polyglot-editing-and-debugging.id.md) · [Filipino](polyglot-editing-and-debugging.tl.md) · **Tiếng Việt** · [简体中文](polyglot-editing-and-debugging.zh.md) · [हिन्दी](polyglot-editing-and-debugging.hi.md) · [עברית](polyglot-editing-and-debugging.he.md) · [العربية](polyglot-editing-and-debugging.ar.md)
<!-- /languages -->

NMOX Studio soạn thảo hơn 70 ngôn ngữ với tô màu cú pháp thật, dàn ý trong
Bộ điều hướng và trí thông minh của máy chủ ngôn ngữ — và nó gỡ lỗi
JavaScript/TypeScript (cả trình duyệt) ngay khi cài xong, với những điểm dừng
thật sự dừng lại. Bài này dừng ở một điểm dừng trong một ứng dụng Node.

![Một điểm dừng JavaScript đã dừng: chương trình tạm dừng, ngăn xếp lời gọi của Node, và các biến V8 đang sống](../images/debug-javascript.png)

## Trước khi bắt đầu

Mở (hoặc dựng khung) một dự án Node nhỏ có một script bạn chạy được, ví dụ
một route Express hay một `node server.js` đơn thuần.

## Các bước

1. **Mở một tệp mã nguồn.** Tô màu, khớp ngoặc, gập mã và đánh dấu các lần
   xuất hiện đều tự bật. **Bộ điều hướng** hiển thị dàn ý của tệp; các máy chủ
   ngôn ngữ (cài theo gợi ý của `Công cụ ▸ Trình chẩn đoán môi trường…`) thêm
   gợi ý hoàn thành và chẩn đoán.

2. **Đặt điểm dừng.** Nhấp vào lề của trình soạn thảo ở một dòng bên trong
   hàm xử lý — một chấm điểm dừng hiện ra.

3. **Gỡ lỗi tệp.** Chạy **Gỡ lỗi tệp (điểm dừng)** (hoặc “Gỡ lỗi trong Chrome
   (điểm dừng)” với một trang HTML/JS). Một lời hỏi tin cậy không gian làm việc
   chỉ một lần canh việc khởi chạy; sau đó bộ điều hợp `js-debug` đi kèm khởi
   động chương trình của bạn.

4. **Dừng ở điểm dừng.** Kích hoạt nhánh mã (gửi yêu cầu, hoặc để script chạy
   tới dòng đó). Chương trình **dừng lại** ở điểm dừng của bạn — xem biến, đi
   dọc ngăn xếp lời gọi, bước qua/bước vào. Với gỡ lỗi trình duyệt, một Chrome
   dùng hồ sơ tạm mở ra ở địa chỉ máy chủ phát triển đang chạy, và các điểm
   dừng trên trang được ánh xạ ngược về IDE.

## Bạn vừa học được gì

- Trình soạn thảo coi hơn 70 ngôn ngữ là công dân hạng nhất (ngữ pháp
  TextMate + CSL + LSP); các tệp cấu hình (YAML, TOML, Dockerfile, nginx…)
  cũng được lo.
- Gỡ lỗi JS/TS có sẵn — một bộ ghép phiên làm phẳng các phiên con của
  js-debug để trình gỡ lỗi một phiên của nền tảng điều khiển được nó.
- Mọi lần khởi chạy gỡ lỗi đều qua cổng tin cậy và bị kết thúc trọn cả cây
  tiến trình khi dừng (không để lại mồ côi).

## Tiếp theo

- **Chạy kiểm thử đang chọn** chạy một phương thức kiểm thử duy nhất, cho
  từng ngôn ngữ.
- Chẩn đoán từ các công cụ trên giá (eslint/tsc/phpstan) đổ về cửa sổ
  Mục cần xử lý của nền tảng.
