# Hướng dẫn: Studio dự án

<!-- languages -->
[English](project-studio.md) · [Español](project-studio.es.md) · [Français](project-studio.fr.md) · [Deutsch](project-studio.de.md) · [Русский](project-studio.ru.md) · [Українська](project-studio.uk.md) · [Polski](project-studio.pl.md) · [Português (Brasil)](project-studio.pt.md) · [Bahasa Indonesia](project-studio.id.md) · [Filipino](project-studio.tl.md) · **Tiếng Việt** · [简体中文](project-studio.zh.md) · [हिन्दी](project-studio.hi.md) · [עברית](project-studio.he.md) · [العربية](project-studio.ar.md)
<!-- /languages -->

Studio dự án là nơi các dự án ra đời và được quản lý: mẫu dự án, một cây tệp
của chính nền tảng, một trình sửa package.json, và các mẫu sẵn cho giá — cộng
với **Chạy / Dựng / Kiểm thử / Dọn** của IDE, những thao tác làm việc mà bạn
chẳng bao giờ phải mở cửa sổ dòng lệnh.

![Studio dự án ở khung bên trái — cây tệp của nền tảng và thanh công cụ dự án, với Giá tác vụ mở bên cạnh](../images/tabs/project-studio.png)

## Mở nó

Thẻ **Studio dự án**, neo cạnh Bàn làm việc, hoặc `Tệp ▸ Dự án mới…`.

## Các bước

1. **Dựng khung một dự án.** `Tệp ▸ Dự án mới…` → chọn một mẫu (Angular,
   Vue, Vanilla Web, Elixir/Phoenix, PHP LEMP, và nhiều nữa). Chọn nơi lưu (mặc
   định là `~/NMOX`) rồi hoàn tất. Dự án mở ra và giá nhắm vào nó.

2. **Duyệt cây tệp.** Cây tệp là một cây thật của nền tảng — biểu tượng đúng
   theo loại tệp, nhãn git `[branch]` trên thư mục gốc, và trọn trình đơn
   Mở/Cắt/Sao chép/Xóa/Đổi tên/Công cụ/Thuộc tính. Những thư mục nặng
   (`node_modules`, `.git`, `dist`) hiện ra không có con, nên một kho khổng lồ
   vẫn nhanh.

3. **Chạy nó — không cần cửa sổ dòng lệnh.** Dùng **Chạy** của IDE (hoặc nhấn
   IGNITE của IGNITION trên giá). Nó xác định trình quản lý gói từ chính tệp khóa
   hay pin corepack của dự án và chạy đúng lệnh; kết quả chảy vào giá.
   **Dựng**, **Kiểm thử** và **Dọn** hoạt động y như vậy.

4. **Sửa package.json.** Trình sửa có sẵn cho phép sửa script và phụ thuộc
   một cách có cấu trúc.

5. **Nạp một mẫu sẵn.** Trình đơn **Mẫu sẵn ▾** của Giá tác vụ đấu một giá dựng sẵn cho một
   luồng công việc — Canh thời gian hoạt động, Cổng phát hành, Web hiện đại,
   Làn Monorepo, Bàn thử Web3, và nhiều nữa — để bạn khỏi đấu bản đấu nối
   bằng tay.

## Bạn vừa học được gì

- Dự án mới được nhận ra qua bất kỳ trong 60 tên tệp kê khai (package.json,
  Cargo.toml, go.mod, pom.xml, gleam.toml, …) cộng bốn kiểu phát hiện theo mẫu
  tên (`.csproj`, `.fsproj`, `.sln`, `.nimble`) — ngay cả một trang dùng thẻ
  script không có tệp kê khai cũng mở ra như một dự án STATIC.
- Chạy/Dựng/Kiểm thử/Dọn và giá là **cùng một cơ chế**; lần đầu chúng chạy
  mã của dự án, bạn sẽ gặp lời hỏi tin cậy không gian làm việc.

## Tiếp theo

- Mở [Giá tác vụ](the-task-rack.vi.md) để xem mẫu sẵn đã đấu những gì.
- Thử một [Không gian học tập](learning-spaces.vi.md) để có hộp cát có hướng
  dẫn.
