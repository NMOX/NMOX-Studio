# Bảng thuật ngữ

<!-- languages -->
[English](glossary.md) · [Español](glossary.es.md) · [Français](glossary.fr.md) · [Deutsch](glossary.de.md) · [Русский](glossary.ru.md) · [Українська](glossary.uk.md) · [Polski](glossary.pl.md) · [Português (Brasil)](glossary.pt.md) · [Bahasa Indonesia](glossary.id.md) · [Filipino](glossary.tl.md) · **Tiếng Việt** · [简体中文](glossary.zh.md) · [हिन्दी](glossary.hi.md) · [עברית](glossary.he.md) · [العربية](glossary.ar.md)
<!-- /languages -->

Những từ NMOX Studio dùng mà một IDE khác không dùng, cùng các thuật ngữ
NetBeans lộ ra ngoài. Mỗi mục cho biết từ đó nghĩa là gì ở đây và đọc thêm ở
đâu.

<a id="the-rack"></a>
## Giá

**Giá tác vụ** (⌘9) — Cửa sổ nơi các công cụ của bạn chạy. Mỗi tác vụ (cài
đặt, dựng, kiểm thử, phục vụ, soi lỗi, triển khai) là một *thiết bị* gắn trên
giá, giống như phần cứng trong một phòng thu.
[Hướng dẫn sử dụng §4](user-guide.vi.md#4-the-task-rack).

**Thiết bị** — Một công cụ trên giá, ví dụ VELOCITY (Vite), VERITAS (kiểm
thử) hoặc PURITY (soi lỗi). Một thiết bị có mặt trước (*mặt máy*) với núm,
nút, đèn và một màn hình nhỏ, và mặt sau với các *giắc*. Có 53 thiết bị dựng
sẵn, liệt kê trong [bảng tra cứu thiết bị](devices.md). Bạn có thể tự thêm
thiết bị bằng một tệp JSON trong `~/.nmox/devices.d/`
([tệp thiết bị](device-files.md)).

**Mặt máy** — Mặt trước của một thiết bị. Nhấn **Tab** trong giá để lật giá và
xem mặt sau.

**Giắc** — Một ổ cắm ở mặt sau của thiết bị. Giắc ra gửi tín hiệu, giắc vào
nhận tín hiệu. Có ba loại tín hiệu:
- **Kích hoạt** (trigger) là một xung duy nhất: “bản dựng đã xong”, “OK”,
  “FAIL”.
- **Cổng** (gate) giữ trạng thái bật hoặc tắt: “máy chủ đang chạy”.
- **Dữ liệu** (data) mang văn bản, như một URL hoặc một dòng kết quả.

**Dây** — Một kết nối từ giắc ra tới giắc vào. Nối giắc OK của một bản dựng
vào giắc RUN của trình chạy kiểm thử, và các bài kiểm thử sẽ chạy mỗi khi bản
dựng thành công. Để nối hai giắc, kéo từ giắc này sang giắc kia, hoặc nhấp
giắc này rồi nhấp giắc kia.

**Bản đấu nối** — Cả một giá: các thiết bị, cài đặt của chúng và các dây. Được
lưu cạnh dự án thành `.nmoxrack.json`, nên đáng đưa vào kho.

**Mẫu sẵn** — Một bản đấu nối dựng sẵn mà bạn nạp từ trình đơn **Mẫu sẵn** của
giá, ví dụ *Cổng phát hành* hoặc *Vòng lặp E2E*. Lưu bất kỳ bản đấu nối nào
vào `~/.nmox/presets.d/` và nó cũng xuất hiện trong trình đơn.

**Giá khởi đầu** — Bản đấu nối mà một dự án nhận được lần đầu bạn mở nó, chọn
theo loại dự án: một bảng điều khiển Vite cho một ứng dụng Vite, các làn chạy,
gỡ lỗi và kiểm thử cho một crate Cargo, và cứ thế.

**Làn** — Hai nghĩa, đều nói về việc chạy:
- Một **dây chuyền**: một chuỗi thiết bị nối với nhau bằng dây, như cài đặt →
  dựng → kiểm thử. Nhiều làn có thể chạy song song, và QUORUM chờ tất cả
  chúng xong.
- **Làn AUTO** của một thiết bị: lệnh mà nó chọn cho dự án này. Ở AUTO,
  thiết bị kiểm thử chạy `npm test` trong một dự án Node và `cargo test`
  trong một dự án Rust.

**Chia sẻ… / Nhập…** — Lưu một giá thành tệp để trao cho người khác, hoặc nạp
một giá từ họ. Trước khi gắn bất cứ thứ gì, Nhập cho thấy mọi thứ tệp chứa, và
mọi thiết bị đến nơi ở trạng thái tắt.

**Bộ sưu tập giá** — **Công cụ ▸ Bộ sưu tập giá…** liệt kê các giá cộng đồng,
mẫu sẵn, giá khởi đầu và các giá bạn đã lưu. Mỗi mục cho biết nó dùng để làm
gì và cần những công cụ nào. [Các giá cộng đồng](racks.md).

<a id="projects-and-running"></a>
## Dự án và việc chạy

**Nhắm** / **dự án đang nhắm** — Dự án mà IDE đang làm việc cùng. Mở một dự án
là nhắm vào nó: giá, các studio, thanh trạng thái và lệnh Chạy đều theo dự án
đang nhắm. Nhắm sang dự án khác thì tất cả chuyển theo, và những gì còn đang
chạy sẽ được dừng trước, sau khi hỏi bạn.

**Tin cậy không gian làm việc** — Câu hỏi NMOX Studio đặt ra trước lần đầu
chạy mã của chính một dự án (kịch bản, lần dựng, kiểm thử). Nếu bạn trả lời
**Giữ an toàn**, không có gì từ dự án được chạy. Câu trả lời của bạn được ghi
nhớ theo từng thư mục.

**▶ và ■** — Chạy và Dừng trên thanh công cụ. ▶ (F6) chạy dự án đang nhắm. ■
(⌥⌘.) dừng mọi lệnh mà NMOX Studio đã khởi động giúp bạn.

**Dấu ⇄** / **đang phục vụ** — Khi thứ bạn chạy in ra một địa chỉ cục bộ,
chẳng hạn `http://localhost:5173/`, địa chỉ đó hiện trên thanh trạng thái sau
ký hiệu ⇄. Một máy chủ đang chạy như thế là một máy chủ *đang phục vụ*. Bấm
địa chỉ để mở nó trong Trình duyệt. Tìm kiếm nhanh liệt kê chúng dưới mục
*Máy chủ đang chạy*.

**Thử nghiệm** — Một dự án dùng một lần tạo từ mẫu trong
`~/.nmox/experiments`. Các phụ thuộc của nó đã được cài và nó đã được tin cậy.
**Nâng cấp…** để giữ lại, hoặc **Loại bỏ…**. **Tệp ▸ Thử nghiệm mới…**.

**Không gian học tập** — Một bài hướng dẫn có dắt tay cho một ngôn ngữ hay
khung làm việc. Nó tạo một dự án thật, một bài hướng dẫn từng bước và một giá
dựng sẵn với một REPL đang chạy, và **Tệp ▸ Kiểm tra bài làm** kiểm các bài
tập của bạn. Có 93 không gian. **Tệp ▸ Không gian học tập mới…**.

**PREFLIGHT** — Thiết bị kiểm tra trước khi phát hành. Nó chạy các phép kiểm
mà dự án của bạn định nghĩa (soi lỗi, kiểm kiểu, kiểm thử, dựng) thành một kết
quả đạt hoặc không đạt duy nhất.

**Bước đầu tiên** — Danh sách việc trên thẻ Chào mừng, dưới tiêu đề BƯỚC ĐẦU
TIÊN. Các bước tự đánh dấu khi bạn làm xong và không bao giờ bỏ dấu.

<a id="the-windows"></a>
## Các cửa sổ

**Studio** — Một cửa sổ có công cụ riêng cho một loại việc. Có năm: **Studio
API** (⌥⌘8), **Studio cơ sở dữ liệu** (⌥⌘7), **Studio hợp đồng** (⌥⌘6, hợp
đồng thông minh), **Studio khối** (⌥⌘5, web component dựng từ các khối) và
**Trình thiết kế hạ tầng** (⌥⌘9, hạ tầng đám mây). Mỗi cái lưu công việc cạnh
dự án trong một tệp `.nmox*.json`. **Studio dự án** trùng tên nhưng là cây tệp
và các mẫu dự án.

**Bàn làm việc** (⌥⌘0) — Cơ sở của bạn: những gì đang chạy, những gì đang mở,
và các dự án cùng tệp gần đây.

**Bảng công việc** (⌥⌘1) — Một bảng kanban cho mỗi dự án, có sprint và đồng hồ
bấm giờ, lưu thành `.nmoxtasks.json`.

**Chào mừng** — Thẻ khởi đầu: các hành động để bắt đầu, dự án gần đây, cột
*CÔNG CỤ* liệt kê mọi cửa sổ, và Bước đầu tiên.

<a id="ai"></a>
## AI

**KVASIR** — Tên chung cho các tính năng AI của NMOX Studio: Hỏi, Sửa, Hoàn
thiện, Giải thích, Soạn thông điệp commit. Nó làm việc với Claude, ChatGPT
hoặc Gemini bằng khóa API của chính bạn, được cất trong chùm khóa của hệ điều
hành. Mỗi tính năng hỏi sự đồng ý của bạn một lần và nêu đúng những gì nó sẽ
gửi. Không có gì được gửi đi cho tới khi bạn dùng một tính năng. Các bản phát
hành trước gọi nó là ORACLE.

**Agent Port** — **Công cụ ▸ Agent Port (MCP)…** cho một tác nhân AI chạy trên
máy của bạn, chẳng hạn một trợ lý lập trình, quyền chỉ đọc vào trạng thái của
IDE qua MCP: tệp đang mở, chẩn đoán, lượt chạy, ký hiệu. Nó chỉ đọc theo thiết
kế và chỉ lắng nghe trên máy của chính bạn.
[Bài hướng dẫn](tutorials/agent-port.md).

<a id="netbeans-terms-you-may-see"></a>
## Thuật ngữ NetBeans bạn có thể gặp

NMOX Studio được xây trên NetBeans Platform, và vài từ của nó lộ ra ngoài.

**Mô-đun** / **NBM** — Một phần của ứng dụng. *NBM* là tệp dùng để phân phối
một mô-đun. **Công cụ ▸ Plugin** cài cập nhật theo từng mô-đun.

**Trung tâm cập nhật** — Nơi **Công cụ ▸ Plugin ▸ Cập nhật** lấy phiên bản mới
của các mô-đun NMOX Studio. Nó đọc một danh mục được công bố cùng mỗi bản phát
hành trên GitHub.

**userdir** — Thư mục nơi NMOX Studio giữ cài đặt, bố cục cửa sổ, nhật ký và
các bản cập nhật đã cài. Hộp thoại Giới thiệu cho biết nó nằm ở đâu (trong
trình đơn Trợ giúp trên Windows và Linux, trong trình đơn NMOX Studio trên
macOS). Nhật ký nằm ở
`var/log/messages.log`. Để khởi động với cài đặt mới tinh, chạy kèm
`--userdir <một thư mục trống>`.

**Tùy chọn** / **Settings…** — Hộp thoại tùy chỉnh. Đó là **Công cụ ▸ Tùy
chọn** trên Windows và Linux, và **NMOX Studio ▸ Settings…** trên macOS.

**Action Items** (Mục cần xử lý) — Cửa sổ liệt kê các vấn đề tìm thấy trong dự
án: lỗi và cảnh báo của các máy chủ ngôn ngữ, cùng kết quả soi lỗi và kiểm kiểu
của giá. Trong trình đơn Cửa sổ, nó có
tên Mục cần xử lý. Bấm một vấn đề để tới đúng dòng đó.
