# Hướng dẫn: Giá tác vụ

<!-- languages -->
[English](the-task-rack.md) · [Español](the-task-rack.es.md) · [Français](the-task-rack.fr.md) · [Deutsch](the-task-rack.de.md) · [Русский](the-task-rack.ru.md) · [Українська](the-task-rack.uk.md) · [Polski](the-task-rack.pl.md) · [Português (Brasil)](the-task-rack.pt.md) · [Bahasa Indonesia](the-task-rack.id.md) · [Filipino](the-task-rack.tl.md) · **Tiếng Việt** · [简体中文](the-task-rack.zh.md) · [हिन्दी](the-task-rack.hi.md) · [עברית](the-task-rack.he.md) · [العربية](the-task-rack.ar.md)
<!-- /languages -->

Giá tác vụ là ý tưởng đặc trưng của NMOX Studio: các công cụ dựng/kiểm
thử/phục vụ của bạn được bày ra như một giá thiết bị phần cứng mà bạn đấu với
nhau bằng dây patch. Một thiết bị chạy một lệnh thật; một sợi dây mang một tín
hiệu thật. Bài này dựng một bản đấu nối nhỏ xíu — chạy một thứ gì đó và xem kết
quả của nó trên MONITOR — để ẩn dụ ấy thấm vào.

![Giá nhắm vào một dự án thật — các thiết bị đã lên giá và đang chạy](../images/task-rack.png)

![Tab lật giá lại — dây patch đấu các thiết bị ở mặt sau](../images/rack-rear.png)

## Trước khi bắt đầu

Mở một dự án (dự án Node nào cũng được; `Tệp ▸ Dự án mới…` → “Vanilla Web”
nếu bạn cần một cái). Mở một dự án sẽ **nhắm** giá vào nó, nên mọi thiết bị
đều chạy trong thư mục của dự án ấy.

## Các bước

1. **Mở giá.** Nhấp thẻ **Giá tác vụ** (hoặc nhấn `⌘9`). Giá khởi đầu chỉ có
   một **MONITOR** — thiết bị bảng điều khiển hiển thị kết quả lệnh và các
   dòng lỗi.

2. **Thêm một thiết bị chạy.** Kéo **IGNITION** từ **Kệ thiết bị** bên trái lên giá.
   IGNITION là thiết bị “chạy” đa ngôn ngữ; khi nhắm vào một dự án Node, nó
   chạy `npm run dev` (nó tự nhận ra trình quản lý gói và bộ công cụ của bạn).

3. **Đấu nó với MONITOR.** Nhấp **Mặt sau (Tab)** (hoặc nhấn Tab) để xem mặt
   sau, rồi nhấp giắc **OUT** của IGNITION và nhấp giắc **IN** của MONITOR — một sợi dây patch
   nối chúng lại. (Kéo giữa các giắc cũng được; nhấp thì dễ hơn khi giá rộng.)

4. **Kích hoạt.** Lật về mặt trước và nhấn nút **IGNITE** của IGNITION. Nó khởi
   chạy tiến trình; kết quả chảy vào MONITOR và các đèn trạng thái sáng lên.
   Nếu dự án chưa được tin cậy, bạn sẽ gặp một lời hỏi tin cậy không gian làm
   việc một lần trước đã — đó là cổng canh không cho một kho vừa clone về tự
   chạy script khi bạn chưa cho phép.

5. **Lưu bản đấu nối.** Nút **Lưu bản đấu nối** ghi
   `.nmoxrack.json` cạnh dự án của bạn. Mở lại dự án sau này và bản đấu nối —
   thiết bị, dây, vị trí các núm — trở lại y hệt.

## Bạn vừa học được gì

- **Thiết bị là công cụ có mặt máy.** Núm chọn tùy chọn, nút GO để chạy, đèn
  LED và màn hình LCD báo trạng thái — và mọi nút điều khiển đều thật (không có
  núm chết; một bài kiểm thử hợp đồng bắt buộc điều đó).
- **Dây điều phối các làn.** OUT→IN là sợi dây đơn giản nhất; cổng sẵn sàng
  (`ENABLE`), rào hợp làn (`QUORUM`) và dây kích hoạt cho phép bạn ghép cả một
  dây chuyền tự phản ứng với chính nó.
- **Mọi thứ được lưu lại.** Bản đấu nối là một tệp đưa vào kho được; giá thậm
  chí hồi sinh một phiên đang chạy sau khi sập.

## Tiếp theo

- Có 53 thiết bị — duyệt chúng ở [devices.md](../devices.md) hoặc trên
  **Kệ thiết bị** (nhấp chuột phải vào một thiết bị đã gắn để xem
  **Cách dùng…** của nó).
- Nhờ [KVASIR](kvasir.vi.md) giải thích một lần chạy hỏng.
- Xuất một bản đấu nối thành workflow GitHub Actions: nút **Xuất CI…** của giá.
