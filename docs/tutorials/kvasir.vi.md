# Hướng dẫn: KVASIR — trình giải thích lỗi bằng AI

<!-- languages -->
[English](kvasir.md) · [Español](kvasir.es.md) · [Français](kvasir.fr.md) · [Deutsch](kvasir.de.md) · [Русский](kvasir.ru.md) · [Українська](kvasir.uk.md) · [Polski](kvasir.pl.md) · [Português (Brasil)](kvasir.pt.md) · [Bahasa Indonesia](kvasir.id.md) · [Filipino](kvasir.tl.md) · **Tiếng Việt** · [简体中文](kvasir.zh.md) · [हिन्दी](kvasir.hi.md) · [עברית](kvasir.he.md) · [العربية](kvasir.ar.md)
<!-- /languages -->

KVASIR là một thiết bị trên giá, đọc lần chạy hỏng gần nhất của bạn rồi hỏi AI
của bạn — Claude, ChatGPT hoặc Gemini — xem đã sai ở đâu. Đây là trợ giúp AI
theo đúng ẩn dụ của giá: một nút bấm, một cổng đồng ý rõ ràng và một màn hình
LCD trung thực — không tệp dự án hay bí mật nào bị gửi đi, chỉ có phần ngữ
cảnh lỗi đã được giới hạn.

![KVASIR giải thích một lần chạy hỏng thật: chẩn đoán đã qua cổng đồng ý trên mặt máy và trọn các bước sửa trong ô xem](../images/vi/kvasir-explain.png)

## Trước khi bắt đầu

Bạn cần một khóa API từ một trong ba nhà cung cấp mà KVASIR nói chuyện được:
Anthropic (Claude), OpenAI (ChatGPT) hoặc Google (Gemini). Nhấn **KEY…** trên
mặt máy để chọn nhà cung cấp và lưu khóa của họ vào chùm khóa của hệ điều hành,
hoặc export biến môi trường của nhà cung cấp —
`ANTHROPIC_API_KEY` / `CLAUDE_API_KEY`, `OPENAI_API_KEY` /
`CHATGPT_API_KEY`, hoặc `GEMINI_API_KEY` / `GOOGLE_API_KEY`. Lựa chọn nhà cung
cấp áp dụng cho mọi gương mặt của KVASIR và cũng nằm ở Tùy chọn ▸ Giá & Đám mây.

## Các bước

1. **Gây ra một lần hỏng.** Chạy thứ gì đó thất bại — một lần dựng có lỗi cú
   pháp, một bài kiểm thử ném ngoại lệ. Hộp đen của giá ghi lại lệnh, mã thoát
   và tối đa năm dòng lỗi được lấy mẫu.

2. **Gắn KVASIR** từ bảng chọn (nhóm OBSERVE) và nhấn **EXPLAIN**.

3. **Đồng ý (lần đầu).** KVASIR có hộp thoại đồng ý một lần của riêng nó, theo
   từng nhà cung cấp, gọi tên bên sẽ nhận dữ liệu và nói rõ đúng những gì rời
   khỏi máy bạn: lệnh bị hỏng, mã thoát của nó, tối đa 5 dòng lỗi, tên thiết bị
   và tên dự án — ngoài ra không có gì (không mã nguồn, không biến môi trường,
   không bí mật). Lòng tin không gian làm việc canh việc *chạy* mã; còn luồng dữ
   liệu đi ra ngoài này có cổng riêng.

4. **Đọc phán quyết.** Một chẩn đoán ngắn hiện trên màn hình LCD nhiều dòng;
   nhấn **VIEW** để mở phần giải thích đầy đủ trong một cửa sổ hội thoại. Núm **MODEL** chọn FAST
   (mặc định) hoặc DEEP — Haiku / Sonnet, GPT-5 mini / GPT-5, hoặc Gemini
   Flash / Pro, tùy nhà cung cấp bạn đã chọn.

## Bạn vừa học được gì

- KVASIR không tốn gì lúc khởi động và không gọi mạng nếu chưa nhấn nút — cả
  cổng khóa lẫn cổng đồng ý đều được thực thi.
- Khóa chỉ đi trong tiêu đề xác thực của nhà cung cấp (`x-api-key`,
  `Authorization: Bearer`, `x-goog-api-key`) — không bao giờ trong URL, phần
  thân hay nhật ký.
- Khóa không bao giờ đi lạc sang nhà cung cấp khác, và sự đồng ý tính theo
  từng nhà cung cấp: đồng ý với Anthropic không có nghĩa là đồng ý với Google
  hay OpenAI.
- Khi có trục trặc, nó nói thật: thiếu khóa, chưa đồng ý, không có gì để giải
  thích, mất mạng và bị từ chối, mỗi trường hợp đều có một thông báo LCD rõ ràng.

## Tiếp theo

- Đấu dây để khỏi phải bấm: một dây `VERITAS FAIL → KVASIR EXPLAIN` tự giải
  thích một lần chạy kiểm thử hỏng (đường dây không bao giờ hỏi và bị giới hạn
  một lần mỗi 30 giây); đầu ra OUT của nó cấp cho MONITOR/PHOSPHOR.
