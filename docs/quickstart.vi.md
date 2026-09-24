# Bắt đầu nhanh: năm phút để dự án của bạn chạy

<!-- languages -->
[English](quickstart.md) · [Español](quickstart.es.md) · [Français](quickstart.fr.md) · [Deutsch](quickstart.de.md) · [Русский](quickstart.ru.md) · [Українська](quickstart.uk.md) · [Polski](quickstart.pl.md) · [Português (Brasil)](quickstart.pt.md) · [Bahasa Indonesia](quickstart.id.md) · [Filipino](quickstart.tl.md) · **Tiếng Việt** · [简体中文](quickstart.zh.md) · [हिन्दी](quickstart.hi.md) · [עברית](quickstart.he.md) · [العربية](quickstart.ar.md)
<!-- /languages -->

Trang này giúp một dự án của chính bạn chạy được trong NMOX Studio. Nó chỉ
nói những gì bạn cần cho việc đó. [Hướng dẫn sử dụng](user-guide.vi.md) là
cẩm nang đầy đủ. Nếu bạn dùng VS Code, hãy đọc tiếp
[Chuyển từ VS Code sang](coming-from-vscode.vi.md).

<a id="1-install-one-minute"></a>
## 1. Cài đặt (một phút)

**macOS, với Homebrew:**

```bash
brew trust --cask nmox/nmox-studio/nmox-studio
brew install nmox/nmox-studio/nmox-studio
```

Homebrew yêu cầu bạn chạy `brew trust` một lần cho mọi tap của bên thứ ba.
Nó sẽ không hỏi lại khi bạn cập nhật.

**macOS, Windows, Linux, không dùng Homebrew:** tải bản phát hành mới nhất
cho hệ điều hành của bạn từ
[trang các bản phát hành](https://github.com/NMOX/NMOX-Studio/releases/latest):

| Hệ điều hành | Tệp | Sau đó |
|---|---|---|
| macOS | `NMOX-Studio-<version>-macos.dmg` | Kéo ứng dụng vào Applications. |
| Windows | `NMOX-Studio-<version>-windows-setup.exe` | Chạy trình cài đặt. |
| Debian, Ubuntu | `nmox-studio_<version>_amd64.deb` | `sudo apt install ./nmox-studio_<version>_amd64.deb` |
| Linux khác | `NMOX-Studio-<version>-linux.tar.gz` | Giải nén rồi chạy `bin/nmoxstudio`. |

Mỗi tệp trên đều mang sẵn môi trường chạy Java của riêng nó, nên bạn không
phải cài thêm gì. Chỉ bản zip di động mới cần Java 21 trở lên có sẵn trên
máy.

Trên macOS, ứng dụng đã được Apple công chứng. Lần đầu bạn mở nó, macOS hỏi
có mở một ứng dụng tải về từ internet không: bấm **Mở** (Open).

<a id="2-open-your-project-one-minute"></a>
## 2. Mở dự án của bạn (một phút)

Khởi động **NMOX Studio**. Nó mở ba thẻ: **Chào mừng**, **Giá tác vụ** và
**Trình duyệt**.

Để mở dự án, chọn **Tệp ▸ Mở thư mục…** (⌥⌘O trên macOS, Ctrl+Alt+O trên
Windows và Linux) rồi chọn thư mục của nó. Bạn cũng có thể làm việc này từ
dòng lệnh, giống như với `code .`:

```bash
cd ~/code/my-app
nmox .
```

Lệnh trả về ngay. Nếu NMOX Studio đang chạy, nó nhận thư mục đó; nếu chưa,
nó sẽ khởi động. Homebrew, trình cài đặt Windows và các gói Linux đưa `nmox`
vào PATH của bạn. Với bản cài từ DMG, xem
[đưa `nmox` vào PATH](user-guide.vi.md#2-first-launch).

Một thư mục được coi là dự án nếu nó có `package.json`, `Cargo.toml`,
`go.mod`, `pom.xml`, `composer.json`, `pyproject.toml` hoặc một trong 57 tệp
dự án khác. Một thư mục toàn tệp HTML thuần cũng được tính.

Khi bạn mở một dự án, ba việc xảy ra:

- **Studio dự án**, ở bên trái, hiện các tệp của bạn.
- Thanh trạng thái, ở phía dưới, hiện nhánh git của bạn và số tệp đã thay
  đổi.
- **Giá tác vụ** được dựng sẵn theo loại dự án. Một dự án Vite nhận một
  bảng điều khiển Vite, một dự án Cargo nhận các làn chạy, gỡ lỗi và kiểm
  thử, và cứ thế.

<a id="3-run-it-one-minute"></a>
## 3. Chạy nó (một phút)

Nhấn **▶** trên thanh công cụ, hoặc F6. Nó chạy dự án của bạn theo đúng cách
các công cụ của dự án chạy nó: kịch bản `dev`, `start` hoặc `serve` trong
`package.json`, `cargo run`, `go run`. Nó dùng chính trình quản lý gói của
dự án: npm, pnpm hoặc yarn, hoặc bun với một dự án Bun.

Lần đầu bạn chạy bất cứ thứ gì trong một dự án, NMOX Studio hỏi bạn có tin
cậy thư mục đó không. Một dự án bạn chưa tin cậy không chạy chút mã nào của
chính nó: không kịch bản, không lần dựng, không kiểm thử. Bấm **Tin cậy
không gian làm việc** cho mã của chính bạn.

Nếu dự án của bạn là một máy chủ phát triển, địa chỉ của nó hiện trên thanh
trạng thái cạnh ký hiệu **⇄**, và trang mở ra trong thẻ **Trình duyệt**. Sửa
một tệp rồi lưu, trang sẽ tải lại.

Để dừng mọi thứ đang chạy, nhấn **■** cạnh ▶, hoặc ⌥⌘. (Option, Command và
dấu chấm).

Nếu không có gì xảy ra, hãy xem thẻ **Output** ở phía dưới. Nó giải thích
vì sao lượt chạy không khởi động được, chẳng hạn một công cụ chưa được cài
hoặc các phụ thuộc chưa được cài, và đề nghị sửa giúp. **Công cụ ▸ Trình
chẩn đoán môi trường…** liệt kê mọi công cụ NMOX Studio có thể dùng và cho
biết cái nào đã được cài.

<a id="4-find-anything-thirty-seconds"></a>
## 4. Tìm bất cứ thứ gì (ba mươi giây)

Nhấn **⌘I** (Ctrl+I trên Windows và Linux) rồi gõ. Tìm kiếm nhanh tìm ra
tệp, hành động trong trình đơn, ký hiệu, thiết bị trên giá, các máy chủ và
lệnh đang chạy, và các kịch bản trong `package.json` của bạn. Nhấn Enter để
mở hoặc chạy kết quả.

Nhấn **⌘P** để mở một tệp theo tên.

<a id="5-test-it-thirty-seconds"></a>
## 5. Kiểm thử nó (ba mươi giây)

Nhấn **⌃F6** (Ctrl+F6) để chạy các bài kiểm thử của dự án. Để thấy mọi bài
kiểm thử trong dự án trước khi chạy bài nào, hãy mở cửa sổ **Kiểm thử** bằng
⌥⌘2.

<a id="if-you-have-no-project-handy"></a>
## Nếu bạn chưa có sẵn dự án nào

- **Tệp ▸ Dự án mới…** tạo một dự án thật từ một mẫu (Angular, Vue, Svelte,
  React với Vite, JavaScript thuần, PHP, Phoenix và nhiều nữa). Nó tạo các
  tệp, thiết lập git và cài các phụ thuộc.
- **Tệp ▸ Không gian học tập mới…** mở một bài hướng dẫn có dắt tay. *Trang
  web đầu tiên của bạn* đứng đầu danh sách.

<a id="where-to-go-next"></a>
## Đi đâu tiếp theo

- **[Giá tác vụ](user-guide.vi.md#4-the-task-rack)**. Mỗi công cụ bạn chạy
  là một thiết bị trên giá, và dây nối giữa các thiết bị xâu chúng thành
  chuỗi: chẳng hạn, chạy các bài kiểm thử mỗi khi bản dựng thành công.
- **[Trình soạn thảo](user-guide.vi.md#5-the-editor)**. Có Emmet, ô màu,
  gỡ lỗi bằng điểm dừng cho Node và Chrome, và khuôn mẫu Angular.
- **[Các studio](user-guide.vi.md#6-the-studios)**. Studio API, Studio cơ sở
  dữ liệu, Studio hợp đồng và Studio khối, cùng Bảng công việc.
- **[Bảng thuật ngữ](glossary.vi.md)** giải thích những từ riêng của sản
  phẩm: giá, bản đấu nối, giắc, làn, nhắm, KVASIR.
