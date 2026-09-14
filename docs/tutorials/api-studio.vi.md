# Hướng dẫn: Studio API

<!-- languages -->
[English](api-studio.md) · [Español](api-studio.es.md) · [Français](api-studio.fr.md) · [Deutsch](api-studio.de.md) · [Русский](api-studio.ru.md) · [Українська](api-studio.uk.md) · [Polski](api-studio.pl.md) · [Português (Brasil)](api-studio.pt.md) · [Bahasa Indonesia](api-studio.id.md) · [Filipino](api-studio.tl.md) · **Tiếng Việt** · [简体中文](api-studio.zh.md) · [हिन्दी](api-studio.hi.md) · [עברית](api-studio.he.md) · [العربية](api-studio.ar.md)
<!-- /languages -->

Studio API là một bàn làm việc REST theo phong cách Postman nằm ngay trong IDE.
Bạn dựng yêu cầu, chạy các khẳng định trên phản hồi, và — điều không nơi nào
khác có — mỗi phản hồi đều được chấm điểm theo các chuẩn tiêu đề bảo mật của
web.

![Một phản hồi 200 thật trong 331ms — và thẻ Tiêu chuẩn chấm điểm các tiêu đề bảo mật của phản hồi](../images/api-studio.png)

## Mở nó

`⌥⌘8`, hoặc dòng **Studio API** trong cột CÔNG CỤ của trang Chào mừng.

## Các bước

1. **Tạo một yêu cầu.** Trong trình dựng yêu cầu, đặt phương thức là `GET` và
   URL là `https://httpbin.org/json`. Nhấn **Gửi**. Phần thân phản hồi tới nơi
   đã được định dạng đẹp; dòng trạng thái cho thấy mã, thời gian và kích thước.
   (Một phản hồi mất kiểm soát không làm hại được bạn — phần thân chảy qua một
   giới hạn 8 MB.)

2. **Thêm một khẳng định.** Ở thẻ **Kiểm thử**, thêm `Status is 200` và
   `Body contains slideshow`. Gửi lại — mỗi khẳng định hiện dấu ✓ xanh hoặc ✗
   đỏ kèm giá trị thực tế.

3. **Đọc điểm bảo mật.** Mở thẻ **Tiêu chuẩn**. Studio API chấm HSTS, CSP,
   X-Content-Type-Options, chống clickjacking, Referrer-Policy và hơn thế, rồi
   cho một điểm bằng chữ — đúng phép kiểm mà một nhà phát triển web năm 2026
   chạy ở securityheaders.com, gắn sẵn vào mỗi lần gửi.

4. **Dùng một biến.** Tạo một môi trường với `base =
   https://httpbin.org`, rồi đặt URL của một yêu cầu là `{{base}}/get`. Đổi môi
   trường là trỏ lại mọi yêu cầu cùng lúc. Nếu giá có một máy chủ phát triển
   đang chạy, Studio API còn mời địa chỉ của nó dưới dạng `{{baseUrl}}`.

5. **Thêm xác thực một cách an toàn.** Ở thẻ **Auth**, chọn Bearer hoặc Basic
   rồi nhập token. Token **không bao giờ** được ghi vào `.nmoxapi.json` vốn để
   đưa vào kho — nó sống trong chùm khóa của hệ điều hành, gắn với yêu cầu đó.

6. **Nhập những gì bạn đã có.** Nút **Nhập…** đọc một lệnh curl được dán vào
   (“Copy as cURL” của devtools trình duyệt), một tệp yêu cầu `.http`/`.rest`,
   hoặc một đặc tả OpenAPI 3 (JSON hoặc YAML) — mỗi thứ đều thành các yêu cầu
   thật, và tiêu đề `Authorization` được nhấc thẳng vào trường Auth dùng chùm
   khóa thay vì nằm lại trong tệp không gian làm việc của bạn. **Sao chép curl**
   đi theo chiều ngược lại: đúng lệnh mà Gửi sẽ chạy, nằm sẵn trong khay nhớ tạm.

7. **Hỏi KVASIR về một phản hồi bất thường.** Khi một lần gửi trả về kết quả
   sai, nhấn **Giải thích…**. Trước tiên một hộp thoại đồng ý cho bạn biết đúng
   những gì sẽ rời máy — phương thức, URL với *giá trị* truy vấn đã che, trạng
   thái, các tiêu đề an toàn (tiêu đề chứa thông tin xác thực đã bị loại bỏ và
   được đếm), và một phần thân có giới hạn — và không có gì được gửi cho tới khi
   bạn đồng ý. Từ chối thì chẳng có gì chạy; đồng ý thì lời giải thích mở ra
   thành một cuộc hội thoại để bạn hỏi tiếp.

## Bạn vừa học được gì

- Yêu cầu, môi trường và khẳng định được lưu theo từng dự án trong
  `.nmoxapi.json` (không kèm bí mật).
- Điểm bảo mật biến câu hỏi “chạy được chưa” thành “có an toàn không”.
- Lần gửi hủy được (nút Gửi đổi thành **Hủy**) và không bao giờ chặn phần còn
  lại của IDE.

## Tiếp theo

- Trỏ một yêu cầu vào máy chủ đang chạy trên giá nhờ lời mời `{{baseUrl}}`.
- Xem [Studio cơ sở dữ liệu](db-studio.vi.md) để có thứ tương tự cho cơ sở dữ
  liệu.
