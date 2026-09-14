# Hướng dẫn: Không gian học tập

<!-- languages -->
[English](learning-spaces.md) · [Español](learning-spaces.es.md) · [Français](learning-spaces.fr.md) · [Deutsch](learning-spaces.de.md) · [Русский](learning-spaces.ru.md) · [Українська](learning-spaces.uk.md) · [Polski](learning-spaces.pl.md) · [Português (Brasil)](learning-spaces.pt.md) · [Bahasa Indonesia](learning-spaces.id.md) · [Filipino](learning-spaces.tl.md) · **Tiếng Việt** · [简体中文](learning-spaces.zh.md) · [हिन्दी](learning-spaces.hi.md) · [עברית](learning-spaces.he.md) · [العربية](learning-spaces.ar.md)
<!-- /languages -->

![Bảng chọn Không gian học tập mới — tìm trong các bài hướng dẫn có sẵn, với phép dò cho biết ngay từ đầu máy này có công cụ của không gian đó hay không](../images/tabs/learning-spaces.png)

Một không gian học tập là một hộp cát khép kín để học một ngôn ngữ, framework
hay thư viện: NMOX Studio sinh ra mã mẫu, một bài hướng dẫn dắt tay, và một
giá đã đấu sẵn với **một REPL thật ngay trên giá** để bạn gõ vào. Có sẵn 93
không gian.

<!-- screenshot: a learning space open — sample code, the tutorial pane, and the REPL device with typed input -->

## Mở nó

`Tệp ▸ Không gian học tập mới…` (trình khởi chạy liệt kê mọi không gian có
sẵn).

## Các bước

1. **Chọn một không gian.** Chọn lấy một — Python, Rust, Solid, htmx,
   Solidity, Elm, một REPL cho ngôn ngữ hệ thống, một không gian
   E2E/Playwright, v.v. Bảng chọn dò trước xem trình thông dịch hay bộ công cụ
   có sẵn không.

2. **Để nó sinh ra.** NMOX Studio tạo không gian dưới
   `~/.nmox/learn/<slug>`: một mẫu tối giản chạy được cùng một bài hướng dẫn
   dắt bạn qua nó, chỉ tới đúng bảng điều khiển hay thiết bị liên quan.

3. **Gõ vào REPL.** Giá đấu sẵn có một thiết bị **REPL** với núm ENGINE đã
   đặt theo ngôn ngữ của không gian (26 engine, mỗi cái đã gieo sẵn cờ ép chế
   độ tương tác). Gõ một biểu thức, nhấn Enter — kết quả chảy ra màn hình
   REPL. Thiếu trình thông dịch? Nút **INSTALL** cài nó ngay từ giá.

4. **Làm theo bài hướng dẫn.** Đi qua từng bước; mã mẫu là thật và chạy
   được, và không gian là của bạn để sửa tùy thích.

## Bạn vừa học được gì

- Một không gian học tập là trọn một dự án + bài hướng dẫn + giá đã đấu dây,
  không chỉ là một đoạn mã.
- REPL là một tiến trình tương tác thật, không phải bản phát lại đóng hộp.
- Bạn có thể tự thêm: thả một tệp `*.json` vào `~/.nmox/learn-catalog.d/` và
  nó gia nhập bảng chọn (xem lược đồ ở
  [learning-spaces.md](../learning-spaces.md)).

## Tiếp theo

- Các không gian framework (Astro/SvelteKit/Nuxt/Next) chỉ tới bảng điều
  khiển tương ứng trên giá (COSMOS/KINETIC/NIMBUS/NEXUS).
