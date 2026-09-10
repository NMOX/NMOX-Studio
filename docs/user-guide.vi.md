# NMOX Studio — Hướng dẫn sử dụng

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · **Tiếng Việt** · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Bản dịch một phần: chương 1–2 có tiếng Việt. Phần còn lại, xem [hướng dẫn đầy đủ bằng tiếng Anh](user-guide.md).

Cách dùng sản phẩm. Hướng dẫn này đi qua các tính năng theo thứ tự bạn sẽ gặp: cài đặt, lần chạy đầu tiên, dự án, giá, các studio, các trình hướng dẫn và các lưới an toàn.

---

## 1. Cài đặt

**macOS (khuyến nghị):**
```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Dòng `brew trust` là xác nhận một lần của Homebrew cho mọi tap của bên thứ ba — các lần cập nhật sau sẽ không hỏi lại. Ứng dụng được ký ad-hoc nhưng không được công chứng, nên một bản sao bị cách ly sẽ bị Gatekeeper từ chối ở lần chạy đầu: cask tự gỡ thuộc tính cách ly trong một bước `postflight` và nói rõ điều đó trong kết quả cài đặt. Không có gì diễn ra lặng lẽ.

**Mọi thứ khác:** tải một tệp từ [bản phát hành mới nhất](https://github.com/NMOX/NMOX-Studio/releases/latest) — `.dmg` cho macOS, `-setup.exe` cho Windows, `.deb` cho Debian/Ubuntu, `.tar.gz` thông thường cho Linux. Cả bốn đều mang sẵn môi trường chạy Java; không cần cài gì trước. `-portable.zip` là tạo phẩm duy nhất dùng Java của chính bạn (cần Java 21+ trong PATH, hoặc chạy với `--jdkhome <đường-dẫn-jdk>`).

> **macOS, lần chạy đầu tiên:** ứng dụng được ký ad-hoc nhưng không được công chứng, nên Gatekeeper hỏi trước khi chạy. Lần đầu, **nhấp chuột phải vào ứng dụng → Mở** rồi xác nhận, hoặc chạy
> `xattr -d com.apple.quarantine "/Applications/NMOX Studio.app"`. Cách nào cũng giải quyết vĩnh viễn.

### Cập nhật

IDE tự cập nhật: **Công cụ ▸ Trình cắm ▸ Cập nhật** đưa ra các mô-đun của mọi bản phát hành mới hơn. Cài, khởi động lại khi được nhắc, xong — không phải tải lại toàn bộ ứng dụng. Một lưu ý thẳng thắn: môi trường chạy Java đi kèm và trình khởi chạy chỉ đổi cùng một bộ cài đầy đủ, nên với những bước nhảy lớn của nền tảng, cài lại từ một tệp phát hành vẫn là cách đúng.

## 2. Lần chạy đầu tiên

Từ dòng lệnh, `nmoxstudio --open <thư-mục>` khởi động ứng dụng với thư mục đó mở ra như một dự án và giá hướng vào nó — cùng một cánh cửa mà “Mở thư mục…” trên trang chào mừng mở ra.

IDE mở ra với tất cả các thẻ của bộ nằm cạnh vùng soạn thảo: **Chào mừng → Giá tác vụ → Studio cơ sở dữ liệu → Studio hợp đồng → Trình thiết kế hạ tầng → Studio API → Bảng Docker** — mỗi bề mặt chính chỉ cách một cú nhấp ngay từ phút đầu. Ở khung bên trái: **Studio dự án** (cây tệp và mẫu), nền **Bàn làm việc** và **Trình duyệt NPM**. Một thư mục `~/NMOX` được tạo làm không gian làm việc mặc định; giá hướng vào đó cho tới khi bạn mở một dự án.

![Lần chạy đầu tiên — trang chào mừng với mọi thẻ đang mở](images/welcome.png)

Những phím tắt đáng học trong ngày đầu (tất cả cũng có trên thẻ chào mừng):

| Phím tắt | Mở |
|---|---|
| **⌘I** | Tìm nhanh — với tới mọi thứ |
| **⌘9** | Giá tác vụ |
| **⌥⌘0** | Bàn làm việc |
| **⌥⌘3** | Ứng dụng trò chuyện IRC |
| **⌥⌘4** | Trình duyệt (WebKit tích hợp, có DevTools) |
| **⌥⌘5** | Studio khối |
| **⌥⌘6** | Studio hợp đồng |
| **⌥⌘7** | Studio cơ sở dữ liệu |
| **⌥⌘8** | Studio API |
| **⌥⌘9** | Trình thiết kế hạ tầng |
| **⌘8** | Bảng Docker |
| **⌘7** | Cấu trúc tệp hiện tại |
| **⇧⌘N / ⌥⌘O** | Dự án mới… / Mở thư mục… |
| **⇧⌘E / ⇧⌘L** | Thử nghiệm mới… / Không gian học mới… |
