# Tuần thứ hai

<!-- languages -->
[English](the-second-week.md) · [Español](the-second-week.es.md) · [Français](the-second-week.fr.md) · [Deutsch](the-second-week.de.md) · [Русский](the-second-week.ru.md) · [Українська](the-second-week.uk.md) · [Polski](the-second-week.pl.md) · [Português (Brasil)](the-second-week.pt.md) · [Bahasa Indonesia](the-second-week.id.md) · [Filipino](the-second-week.tl.md) · **Tiếng Việt** · [简体中文](the-second-week.zh.md) · [हिन्दी](the-second-week.hi.md) · [עברית](the-second-week.he.md) · [العربية](the-second-week.ar.md)
<!-- /languages -->

*Commit, xem lại, giải quyết, đề xuất — mà không phải chuyển sang công cụ khác.*

Giờ đầu tiên là mở một dự án và chạy nó. Tuần thứ hai là mọi thứ xung
quanh mã nguồn: hai mươi commit mỗi ngày, một bản diff cần đọc trước mỗi
commit, một xung đột sau khi pull, một pull request sau khi push, một
stack trace cần lần theo, một README cần giữ cho trung thực. Hướng dẫn này
là một buổi ngồi trong một kho git bạn đã có sẵn, và mỗi bước là việc bạn
sẽ làm lại vào ngày mai.

## 1. Biến NMOX Studio thành trình soạn thảo của git

**Làm:** Nhóm ▸ **Dùng NMOX Studio với Git…**

**Thấy:** sáu thiết lập git toàn cục biến NMOX Studio thành trình soạn
thảo, difftool và mergetool của git, mỗi thiết lập nằm cạnh giá trị nó
đang có **bây giờ**, để không có gì bị thay thế mà bạn không thấy.
**Áp dụng** đặt chúng (**Đóng** là nút mặc định, vì thao tác này ghi vào
cấu hình git toàn cục của bạn); **Sao chép lệnh** thì thay vào đó đưa các
dòng `git config` vào bảng tạm. Khi git đã dùng NMOX Studio, hộp thoại nói
như vậy và không đưa ra Áp dụng.

Cũng những dòng đó, nếu bạn thích dùng terminal hơn:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

## 2. Commit

**Làm:** sửa một tệp, rồi trong một terminal:

```bash
git commit -a
```

**Thấy:** thông điệp commit mở ra trong NMOX Studio, và dòng trạng thái
cho biết có một terminal đang chờ nó. Các dòng `#` của git là chú thích;
chỉ những gì bạn viết mới được kiểm tra chính tả; một dòng tóm tắt dài hơn
72 ký tự, chỗ mà chính các công cụ của git cắt nó, nhận cảnh báo từ ký tự
thứ 73. Lưu, đóng thẻ, và commit được tạo — terminal đã chờ cho đến khi
bạn làm vậy. Thoát IDE khi thông điệp vẫn đang mở cũng trả nó lại cho git,
với những gì đã được lưu.

`git rebase -i` mở danh sách của nó theo cùng cách: mỗi lệnh và mỗi commit
được tô sáng, và **Bật tắt chú thích** bỏ một dòng mà không xóa nó.

## 3. Biết mình đang ở đâu

**Thấy:** **dấu ⎇** trên dòng trạng thái — `⎇ main ±3 ↑2 ↓1` là nhánh của
bạn, ba tệp đã đổi, hai commit cần push và một commit cần pull (các mũi
tên chỉ xuất hiện khi có gì đó để push hoặc pull). Trình đơn của nó bắt
đầu bằng **Chuyển nhánh…**, **Commit…**, **Pull…** và **Push…**.

**Làm:** đặt con trỏ lên bất kỳ dòng nào của một tệp được theo dõi.

**Thấy:** cạnh dấu đó, ai đã sửa dòng ấy lần cuối, cách đây bao lâu và vì
sao: `Ada Lovelace, 3 ngày trước · Fix the parser`. Một dòng bạn chưa
commit sẽ nói như vậy, và một tệp có thay đổi chưa lưu sẽ nói điều đó thay
vì nêu sai tác giả. Nhấp vào ghi chú để xem chú giải của cả tệp;
**Xem ▸ Tác giả dòng** tắt nó đi.

## 4. Xem lại một bản diff

**Làm:**

```bash
git difftool
```

**Thấy:** mỗi tệp đã đổi hiện cạnh nhau trong khung diff của NMOX Studio,
với **Khác biệt trước / Khác biệt tiếp theo** và “Khác biệt 2 trên 5” ở
phía trên. Một tệp được thêm hoặc bị xóa hiển thị phía còn thiếu thành một
ô trống (“không có tệp”); một tệp nhị phân hiện là nhị phân, và thanh cho
biết hai tệp nhị phân có khác nhau không. Đóng thẻ và git chuyển sang tệp
tiếp theo.

## 5. Giải quyết xung đột

**Làm:** merge một nhánh bị xung đột, rồi:

```bash
git mergetool
```

**Thấy:** tệp bị xung đột trong trình soạn thảo, với phía hiện tại và phía
đến được tô màu, và một cảnh báo trên mỗi dòng `<<<<<<<`. Đặt con trỏ lên
đó rồi nhấn ⌘. (Alt+Enter ở nơi khác), hoặc dùng **Mã nguồn ▸ Sửa mã…**:
**Chấp nhận thay đổi hiện tại**, **Chấp nhận thay đổi đến** hoặc **Chấp
nhận cả hai thay đổi**, mỗi lựa chọn là một lần sửa có thể hoàn tác. Một
khối đã thay đổi kể từ lúc được đề xuất sẽ bị từ chối chứ không bị đoán.
Lưu, đóng thẻ và trả lời git.

## 6. Đề xuất nó

**Làm:** push, rồi Nhóm ▸ **Pull request mới trên GitHub** (cũng có trong
trình đơn của dấu).

**Thấy:** chính trang New Pull Request của GitHub cho nhánh của bạn, trong
trình duyệt của bạn, nơi bạn đã đăng nhập. Từ một trình soạn thảo,
**Chỉnh sửa ▸ Mở trên GitHub** và **Sao chép liên kết GitHub** cho ra
dòng hoặc các dòng bạn đang đứng; trên cây của Studio dự án, chúng cho ra
một tệp hoặc một thư mục.

## 7. Lần theo một lỗi

**Làm:** chạy các bài kiểm thử của bạn trong Terminal (⌃\`) cho đến khi có
một bài thất bại.

**Thấy:** một vị trí trong kết quả — `src/app.ts:42:7`, một khung stack
`(/abs/app.js:10:5)`, `--> src/main.rs:3:5`, `File "x.py", line 12` — mở
tại đúng dòng và cột đó khi ⌘-nhấp (Ctrl-nhấp trên Windows và Linux). Một
URL hay `localhost:3000` không bao giờ là liên kết, và một đường dẫn không
tồn tại sẽ bị từ chối kèm tên của nó chứ không bị đoán.

## 8. Giữ README trung thực

**Làm:** Công cụ ▸ **Kiểm tra liên kết Markdown…**

**Thấy:** mọi liên kết tương đối và hình ảnh trong Markdown của dự án được
kiểm tra theo cách GitHub hiển thị chúng — tệp phải tồn tại, và một
`#heading` phải là tiêu đề của tệp đó. Một liên kết chết là lỗi, một tiêu
đề bị thiếu là cảnh báo, cả hai hiện thành đường lượn sóng và trong Mục
cần xử lý, kèm một câu trên dòng trạng thái. Không gì rời khỏi máy của
bạn: một liên kết có scheme thì không được kiểm tra.

## 9. Giao cho một tác nhân

**Làm:** Công cụ ▸ **Agent Port (MCP)…**, đánh dấu **Giữ địa chỉ và mã
thông báo này**, rồi **Sao chép cho Claude Code**, và chạy dòng đã sao
chép một lần.

**Thấy:** một tác nhân có thể đọc những gì IDE biết — dự án đang được
nhắm tới, cái gì đang phục vụ và đang chạy, bạn đang sửa gì, lần thất bại
gần nhất — và ngày mai vẫn kết nối được, vì mã thông báo được giữ trong
chuỗi khóa của hệ thống và cổng được dùng lại. Cổng vẫn chỉ đọc theo thiết
kế. Bỏ đánh dấu **Giữ địa chỉ và mã thông báo này** sẽ xóa mục trong chuỗi
khóa.

## Những gì bạn đã làm

Bạn đã viết một thông điệp commit, đọc một bản diff, giải quyết một xung
đột, mở một pull request, tìm ra ai đã viết một dòng, lần theo một stack
trace và kiểm tra một README — tất cả trong cửa sổ bạn vốn đang ở. Không
việc nào trong số đó thay thế git: mỗi bước là bước của chính git, được mở
ở nơi bạn làm việc.
