# Hướng dẫn thực hành NMOX Studio

<!-- languages -->
[English](README.md) · [Español](README.es.md) · [Français](README.fr.md) · [Deutsch](README.de.md) · [Русский](README.ru.md) · [Українська](README.uk.md) · [Polski](README.pl.md) · [Português (Brasil)](README.pt.md) · [Bahasa Indonesia](README.id.md) · [Filipino](README.tl.md) · **Tiếng Việt** · [简体中文](README.zh.md) · [हिन्दी](README.hi.md) · [עברית](README.he.md) · [العربية](README.ar.md)
<!-- /languages -->

Những bài thực hành ngắn, tự tay làm, cho các hệ thống khiến NMOX Studio
khác với một IDE thông thường. Mỗi bài chỉ cần ngồi một lần: mở cửa sổ,
làm theo các bước, và bạn đã dùng tính năng ấy thật sự.

Muốn tra cứu rộng hơn (cài đặt, mọi trình đơn, mọi lưới an toàn), xem
[Hướng dẫn sử dụng](../user-guide.vi.md). Danh sách thiết bị đầy đủ nằm ở
[devices.md](../devices.md).

## Các hệ thống

| Bài hướng dẫn | Bạn sẽ làm gì | Mở bằng |
|----------|----------------|------------|
| [Giá tác vụ](the-task-rack.vi.md) | Đấu một bản đấu nối chạy→theo dõi và xem nó kích hoạt | ⌘9 / thẻ Giá tác vụ |
| [Tự viết thiết bị của bạn](your-own-device.vi.md) | Thêm một thiết bị vào giá chỉ bằng trình soạn thảo văn bản — không Java, không khởi động lại | `~/.nmox/devices.d/` |
| [Bàn làm việc](workbench.vi.md) | Dùng bến nhà để nhảy giữa các dự án và công cụ | ⌥⌘0 |
| [Studio dự án](project-studio.vi.md) | Dựng khung một dự án và chạy nó mà không cần cửa sổ dòng lệnh | thẻ Studio dự án |
| [Studio API](api-studio.vi.md) | Gửi một yêu cầu, kiểm tra kết quả, đọc điểm an toàn | ⌥⌘8 |
| [Studio cơ sở dữ liệu](db-studio.vi.md) | Kết nối tới SQLite và sửa một hàng ngay trong lưới | ⌥⌘7 |
| [Studio hợp đồng](contract-studio.vi.md) | Biên dịch, triển khai lên một chuỗi cục bộ và gọi một hợp đồng | ⌥⌘6 (Web3) |
| [Trình thiết kế hạ tầng](infra-designer.vi.md) | Vẽ một droplet cùng tường lửa và chạy thử việc triển khai | ⌥⌘9 |
| [Studio khối](block-studio.vi.md) | Dựng một Web Component từ những khối khớp vào nhau | ⌥⌘5 |
| [Soạn thảo và gỡ lỗi đa ngôn ngữ](polyglot-editing-and-debugging.vi.md) | Đặt điểm dừng trong một ứng dụng Node và dừng đúng ở đó | mở dự án bất kỳ |
| [Từ trình duyệt tới mã nguồn](browser-to-source.vi.md) | Nhấp một phần tử trên trang, đáp xuống mã nguồn của nó, đổi kiểu từ DevTools | ⌥⌘4 → DevTools → DOM |
| [Agent Port (MCP)](agent-port.vi.md) | Hướng một tác nhân AI vào trạng thái đang sống của IDE — chỉ đọc theo thiết kế | Công cụ ▸ Agent Port (MCP)… |
| [Tuần thứ hai](the-second-week.vi.md) | Commit, xem lại diff, giải quyết xung đột, mở pull request và lần theo stack trace — các bước của chính git, ngay trong cửa sổ bạn đang làm việc | Nhóm ▸ Dùng NMOX Studio với Git… |
| [Bảng điều khiển Docker](docker-panel.vi.md) | Xem các container và dockerize một dự án | thẻ Bảng điều khiển Docker |
| [Bảng công việc và sprint](task-board.vi.md) | Chạy một kanban có đồng hồ chấm công, bản họp nhanh một cú nhấp và biểu đồ burndown của sprint, tất cả từ một tệp đưa vào kho | ⌥⌘1 |
| [Trình bày trước cả phòng](show-it-to-a-room.vi.md) | Trình bày, chia sẻ và chụp màn hình ngay trong IDE — từ Chế độ trình chiếu tới Sao chép cây dự án dưới dạng Markdown | Xem ▸ Chế độ trình chiếu |
| [KVASIR](kvasir.vi.md) | Hỏi AI vì sao một lần chạy hỏng | Giá → KVASIR |
| [Giải thích mọi thứ](explain-anything.vi.md) | Dùng bốn gương mặt của KVASIR: lần chạy, mã, phản hồi API, lỗi cơ sở dữ liệu | bất cứ đâu có thứ hỏng |
| [Chuyển từ Postman sang](migrating-from-postman.vi.md) | Nhập bộ sưu tập, bản ghi HAR và nhiều thứ khác — bí mật về nằm trong chùm khóa | ⌥⌘8 → Nhập… |
| [Image Kit (Web)](image-kit.vi.md) | Nén ảnh của dự án: JPEG nhỏ hơn, bản WebP đi kèm, bản báo cáo trung thực | Tệp ▸ Thêm vào dự án ▸ Image Kit (Web)… |
| [Không gian học tập](learning-spaces.vi.md) | Dựng nhanh một hộp cát có hướng dẫn với REPL đang sống | Không gian học tập mới… |
| [Trình hướng dẫn và bộ công cụ](wizards-and-kits.vi.md) | Thêm PWA, các tệp chuẩn mực web hoặc khung sườn web cổ điển | Tệp ▸ Thêm vào dự án |

> **Một lưu ý về phím tắt.** Trên macOS, các studio nằm ở họ phím `⌥⌘`
> (Option-Command) — `⌥⌘6`–`⌥⌘9`, `⌥⌘5`, `⌥⌘0` — vì những tổ hợp `⇧⌘`
> đơn giản đã bị nền tảng chiếm. Trên Linux/Windows phím bổ trợ là `Alt+`;
> còn các trình đơn (Cửa sổ ▸ …) thì lúc nào cũng dùng được.

Lần chạy đầu tiên hiện ba thẻ — Chào mừng, Giá tác vụ và Trình duyệt — với
Studio dự án, Bàn làm việc và Trình duyệt NPM neo ngay bên cạnh. Mọi cửa sổ
khác chỉ cách một tổ hợp phím và đều có trong cột CÔNG CỤ của trang Chào mừng.
