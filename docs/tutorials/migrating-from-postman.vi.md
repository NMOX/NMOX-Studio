# Hướng dẫn: Chuyển từ Postman sang (cả Insomnia, và từ trình duyệt)

<!-- languages -->
[English](migrating-from-postman.md) · [Español](migrating-from-postman.es.md) · [Français](migrating-from-postman.fr.md) · [Deutsch](migrating-from-postman.de.md) · [Русский](migrating-from-postman.ru.md) · [Українська](migrating-from-postman.uk.md) · [Polski](migrating-from-postman.pl.md) · [Português (Brasil)](migrating-from-postman.pt.md) · [Bahasa Indonesia](migrating-from-postman.id.md) · [Filipino](migrating-from-postman.tl.md) · **Tiếng Việt** · [简体中文](migrating-from-postman.zh.md) · [हिन्दी](migrating-from-postman.hi.md) · [עברית](migrating-from-postman.he.md) · [العربية](migrating-from-postman.ar.md)
<!-- /languages -->

Studio API đọc được những tệp bạn đã có: một bộ sưu tập hoặc môi trường
Postman, một bản xuất Insomnia v4 (cấu trúc không gian làm việc và
`{{ _.templates }}` được chuyển đổi), một bản ghi HAR từ devtools, một lệnh
curl, một tệp `.http`, một đặc tả OpenAPI.
Bài này đi trọn một bản xuất Postman thật từ đầu tới cuối — và chỉ ra điều mà
NMOX Studio cố ý làm khác: **bí mật về nằm trong chùm khóa của hệ điều hành,
không bao giờ trong một tệp đưa vào kho.**

![Studio API, nơi các bản nhập đáp xuống: cây bộ sưu tập, một yêu cầu đã gửi, và điểm tiêu đề bảo mật của nó](../images/api-studio.png)

## Trước khi bắt đầu

Xuất bộ sưu tập từ Postman: bộ sưu tập ▸ … ▸ Export ▸ **Collection v2.1**.
(Một bản xuất v1 sẽ bị từ chối kèm cách sửa nói rõ — hãy xuất lại dạng v2.1.)
Môi trường được xuất riêng và nhập qua **Nhập… ▸ Môi trường Postman…** — giá
trị thường được đưa vào, bản nhập trùng tên được gộp mà không ghi đè những gì
bạn đã đặt, còn những giá trị Postman đánh dấu *secret* thì bị để ngoài kèm một
ghi chú chỉ tới trường Auth dùng chùm khóa, vì môi trường của Studio API nằm
trong `.nmoxapi.json` vốn để đưa vào kho.

## Các bước

1. **Mở Studio API** (⌥⌘8) và nhấn **Nhập… ▸ Bộ sưu tập Postman…**. Chọn tệp
   `.json` bạn đã xuất.

2. **Kiểm tra những gì đã tới.** Thư mục giữ nguyên danh tính dưới dạng tên
   “Thư mục / Yêu cầu”. `{{variables}}` của Postman được nhập *nguyên văn* —
   chúng chính là cú pháp của Studio API — và biến của bộ sưu tập gia nhập môi
   trường đang dùng mà không ghi đè thứ gì bạn đã đặt. Biến đường dẫn `:id` trở
   thành `{{id}}`.

3. **Nhìn thẻ Auth của một yêu cầu từng có bearer token.** Token *vẫn ở đó* —
   nhưng nó vào qua trường Auth dùng chùm khóa, không phải một dòng tiêu đề. Cứ
   thoải mái đưa `.nmoxapi.json` vào kho; bí mật không nằm trong đó. Bất cứ thứ
   gì bản nhập không biểu diễn được (phần thân multipart, script) đều được gọi
   tên trên dòng trạng thái, không bao giờ bị làm hỏng lặng lẽ.

4. **Nhập một bản ghi từ trình duyệt.** Ở thẻ Network của devtools, chọn
   “Save all as HAR”, rồi **Nhập… ▸ Bản ghi HAR…**. Chỉ lưu lượng XHR/fetch của
   bạn được nhập (tài nguyên của trang được đếm và báo ra), cookie phiên bị bỏ
   — một cookie ghi lại được là một thông tin xác thực — và một tiêu đề
   `Authorization` đã ghi lại hoặc chuyển vào chùm khóa (Bearer/Basic) hoặc bị
   bỏ và được đếm (mọi thứ không rõ kiểu).

5. **Gửi thử một cái.** Chọn một yêu cầu đã nhập, đặt giá trị cho
   `{{baseUrl}}` trong môi trường nếu cần, nhấn **Gửi** — và nhân tiện đọc điểm
   tiêu đề bảo mật ở thẻ Tiêu chuẩn.

6. **Đi theo chiều ngược lại.** **Nhập… ▸ Xuất bộ sưu tập sang .http…** ghi cả
   bộ sưu tập theo phương ngữ REST Client cho bất kỳ trình soạn thảo hay trình
   chạy CI nào. Xác thực cố ý không có trong tệp; mỗi yêu cầu có xác thực mang
   một chú thích gọi tên thứ cần thêm lại.

## Bạn vừa học được gì

- Việc chuyển đổi chỉ gói trong một trình đơn: curl / `.http` / OpenAPI /
  Postman / HAR vào, `.http` ra.
- Luật về bí mật đứng vững ở mọi biên giới: vào chùm khóa, và ở lại chùm khóa.
- Mọi lời từ chối đều được gọi tên, không bao giờ im lặng — nếu có gì không
  nhập được, dòng trạng thái nói đó là gì và vì sao.
