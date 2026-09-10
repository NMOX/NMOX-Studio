# NMOX Studio — Hướng dẫn sử dụng

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · **Tiếng Việt** · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Bản dịch một phần: chương 1–3 có tiếng Việt. Phần còn lại, xem [hướng dẫn đầy đủ bằng tiếng Anh](user-guide.md).

Cách dùng sản phẩm. Hướng dẫn này đi qua các tính năng theo thứ tự bạn sẽ gặp: cài đặt, lần chạy đầu tiên, dự án, giá, các studio, các trình hướng dẫn và các lưới an toàn.

---

<a id="1-install"></a>
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

<a id="2-first-launch"></a>
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

<a id="3-projects"></a>
## 3. Dự án

**Mở:** bất kỳ thư mục nào mang một trong 60 tệp kê khai được nhận biết đều mở ra như một dự án thật — `package.json`, `Cargo.toml`, `go.mod`, `pom.xml`, `composer.json`, `foundry.toml`, `bower.json`, `Gruntfile.js` và những họ hàng của chúng — kể cả các tệp kê khai của những chuỗi hợp đồng: một kho Aiken (`aiken.toml`) hay Clarinet (`Clarinet.toml`) mở ra với đúng các làn của nó đã được nối sẵn. Một thư mục HTML thuần với thẻ `<script>` và **không** có tệp kê khai cũng mở được, dưới dạng dự án STATIC: web cổ điển ở đây là công dân hạng nhất, không phải một lỗi.

**Tạo:** *Dự án mới…* đưa ra những bộ khung thật — Angular, Vue, Svelte, JavaScript thuần, Elixir/Phoenix, PHP Web (LEMP) và Web cổ điển (jQuery). Mỗi bộ đến với cấu hình lint, định dạng và kiểm thử đã nối sẵn cùng một kho git đã khởi tạo: một commit khung duy nhất mà, khi trình hướng dẫn chạy phần cài đặt giúp bạn, cũng mang theo tệp khóa — nên lần `git status` đầu tiên của bạn sạch sẽ.

**Chuyển dự án là an toàn:** nếu có thiết bị đang chạy (một máy chủ phát triển, một trình theo dõi), IDE hỏi trước khi chuyển và tắt chúng gọn ghẽ. Không có gì chạy tiếp sau lưng bạn, không bao giờ. Ngay cả việc buộc thoát IDE cũng không thể bỏ lại một tiến trình mồ côi.

**Thử nghiệm** là cách nhanh nhất để thử một bộ công nghệ. **Tệp ▸ Thử nghiệm mới…** (⇧⌘E) chọn một mẫu và tạo một dự án dùng một lần trong `~/.nmox/experiments`: không git, không danh sách gần đây, đã được tin cậy, các phụ thuộc đã cài — để **lần Chạy đầu tiên chạy được ngay**. Nó mở ra ở chính bản hướng dẫn `EXPERIMENT.md` của mình, nói cho bạn biết nhấn gì, sửa tệp nào, và trí thông minh của IDE dành cho bộ công nghệ ấy nằm ở đâu. Giữ lại thứ thành hình: **Tệp ▸ Thử nghiệm…** ▸ **Nâng lên** đưa nó ra ngoài và khởi tạo git, **Nhân bản** tạo một bản sao bên cạnh để thử cách thứ hai, **Bỏ đi** dọn phần còn lại. Kệ hiển thị tuổi của từng cái và chi phí đĩa đo được. Bạn thích con đường có hướng dẫn hơn? Hộp thoại đưa 93 không gian học lên trước.

![Kệ không gian học — số lượng, chi phí đĩa, tuổi và trọn vòng đời](images/spaces-shelf.png)

![Một thử nghiệm Express mới tinh: bản hướng dẫn đang mở, các phụ thuộc đã cài, API đã phục vụ](images/experiment-walkthrough.png)

**Chạy, dựng, kiểm thử — và dừng:** nút ▶ trên thanh công cụ (F6) chạy dự án theo đúng cách bộ công cụ của nó chạy: một kịch bản `start` nếu package.json có, `cargo run`, `go run`, `dotnet run`, và với một thư mục HTML thì một máy chủ tĩnh nhỏ trên cổng trống đầu tiên kể từ 8080. Dựng, Kiểm thử và Dọn nằm ngay cạnh và trong trình đơn Chạy. Một máy chủ phát triển thông báo địa chỉ của mình sẽ thắp dấu ⇄ trên thanh trạng thái và mở trang trong trình duyệt tích hợp. Mọi thứ lần đầu đều đi qua lời hỏi tin cậy không gian làm việc. Một lần chạy không khởi động được sẽ nói thẳng và mời mở Bác sĩ môi trường. Để dừng: nút ■ bên phải Gỡ lỗi (⌥⌘.) dừng mọi lệnh đang chạy cùng lúc và nói nó đã dừng những gì; **Chạy ▸ Dừng** dừng một lệnh rồi mời **Lặp lại**. Nút ■ thấy mọi thứ sản phẩm khởi chạy giúp bạn, kể cả các lần cài đặt; khi rê chuột, chú giải nêu đúng thứ một cú nhấn sẽ dừng, và mỗi thứ đã chạy từ bao giờ.

**`.env` ở khắp nơi:** nếu dự án của bạn có `.env`, các thiết bị khởi chạy từ giá sẽ nhận những biến đó. Sửa nó và thanh trạng thái ghi nhận rằng những lần khởi động lại sẽ nhận — các tiến trình đang chạy trung thực giữ nguyên môi trường cũ của chúng.
