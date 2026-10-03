# Chuyển từ VS Code sang

<!-- languages -->
[English](coming-from-vscode.md) · [Español](coming-from-vscode.es.md) · [Français](coming-from-vscode.fr.md) · [Deutsch](coming-from-vscode.de.md) · [Русский](coming-from-vscode.ru.md) · [Українська](coming-from-vscode.uk.md) · [Polski](coming-from-vscode.pl.md) · [Português (Brasil)](coming-from-vscode.pt.md) · [Bahasa Indonesia](coming-from-vscode.id.md) · [Filipino](coming-from-vscode.tl.md) · **Tiếng Việt** · [简体中文](coming-from-vscode.zh.md) · [हिन्दी](coming-from-vscode.hi.md) · [עברית](coming-from-vscode.he.md) · [العربية](coming-from-vscode.ar.md)
<!-- /languages -->

Đôi tay bạn đã biết mọi thứ nằm ở đâu. Trang này là tấm bản đồ đưa những
thói quen đó sang NMOX Studio: trước hết là các tổ hợp phím, rồi tới chỗ mỗi ý
tưởng của VS Code nằm ở đây, rồi tới những gì thật sự khác.

Bốn tổ hợp phím đầu tiên mà một người dùng VS Code bấm đều làm đúng điều họ
mong đợi: **⇧⌘P** mở bảng lệnh, **⇧⌘E** mở cây tệp, **⇧⌘X** mở phần plugin, và
**⌃\`** mở Terminal. Chúng được đăng ký trong cả năm hồ sơ phím mà nền tảng đi
kèm, và một cổng kiểm tra lúc dựng phân giải từng tổ hợp qua sơ đồ phím đã
lắp ráp trên macOS, Windows và Linux, để không có gì khác chạy thế chỗ chúng.

<a id="the-chords"></a>
## Các tổ hợp phím

Các cột macOS dùng ký hiệu của thanh trình đơn (⌃ Control, ⌥ Option, ⇧ Shift,
⌘ Command); các cột Windows và Linux là cùng tổ hợp đó trên bàn phím PC.

| Bạn muốn | VS Code, macOS | NMOX, macOS | VS Code, Win/Linux | NMOX, Win/Linux |
|---|---|---|---|---|
| Bảng lệnh | ⇧⌘P | **⇧⌘P** (hoặc ⌘I) — Tìm kiếm nhanh | Ctrl+Shift+P | **Ctrl+Shift+P** (hoặc Ctrl+I) |
| Mở một tệp theo tên | ⌘P | **⌘P** — Đi tới tệp | Ctrl+P | **Ctrl+P** |
| Cây tệp | ⇧⌘E | **⇧⌘E** — Studio dự án | Ctrl+Shift+E | **Ctrl+Shift+E** |
| Tiện ích mở rộng | ⇧⌘X | **⇧⌘X** — Công cụ ▸ Plugin | Ctrl+Shift+X | **Ctrl+Shift+X** |
| Terminal, trong thư mục dự án | ⌃\` | **⌃\`** | Ctrl+\` | **Ctrl+\`** |
| Mở một dự án gần đây | ⌃R | **⌥⌘P** — Chuyển dự án… | Ctrl+R | **Ctrl+Alt+P** |
| Tới một ký hiệu trong dự án | ⌘T | **⌥⇧⌘O** | Ctrl+T | **Ctrl+Alt+Shift+O** |
| Tới định nghĩa | F12 | **F12** hoặc ⌘B | F12 | **F12** hoặc Ctrl+B |
| Tìm tham chiếu | ⇧F12 | **⇧F12** — Tìm nơi sử dụng | Shift+F12 | **Shift+F12** |
| Đổi tên một ký hiệu | F2 | **F2** hoặc ⌃R | F2 | **F2** hoặc Ctrl+R |
| Sửa nhanh | ⌘. | **⌘.** hoặc ⌃↩ | Ctrl+. | **Alt+Enter** |
| Tới dòng | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Lùi / tiến | ⌃- / ⌃⇧- | **⌃- / ⌃⇧-** | Alt+← / Alt+→ | **Alt+← / Alt+→** |
| Bật/tắt chú thích dòng | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Bật/tắt chú thích khối | ⇧⌥A | **⇧⌥A** | Shift+Alt+A (Ctrl+Shift+A trên Linux) | **Shift+Alt+A** (và Ctrl+Shift+A trên Linux) |
| Bật/tắt ngắt dòng | ⌥Z | **⌥Z** — Xem ▸ Ngắt dòng | Alt+Z | **Alt+Z** |
| Hiện gợi ý | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Thêm lần xuất hiện kế tiếp vào vùng chọn | ⌘D | **⌘D** hoặc ⌘J | Ctrl+D | **Ctrl+D** hoặc Ctrl+J |
| Chọn mọi lần xuất hiện | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Thêm con trỏ ở dòng trên / dưới | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Dời dòng lên / xuống | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Nhân bản dòng xuống dưới | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Xóa dòng | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Thụt lề dòng | ⌘] | **⌘]** | Ctrl+] | **Alt+Shift+→** |
| Thay thế | ⌥⌘F | **⌥⌘F** hoặc ⌘R | Ctrl+H | **Ctrl+H** |
| Định dạng tài liệu | ⇧⌥F | **⇧⌥F** hoặc ⌃⇧F | Shift+Alt+F | **Alt+Shift+F** |
| Đóng thẻ trình soạn thảo | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
| Sao chép đường dẫn của tệp đang sửa | ⌥⌘C | **⌥⌘C** — Chỉnh sửa ▸ Sao chép đường dẫn | Shift+Alt+C | **Ctrl+Alt+C** |
| Sao chép đường dẫn tương đối của nó | ⇧⌥⌘C | Chỉnh sửa ▸ Sao chép đường dẫn tương đối (không có tổ hợp phím) | Ctrl+K Ctrl+Shift+Alt+C | Chỉnh sửa ▸ Sao chép đường dẫn tương đối (không có tổ hợp phím) |
| Bảng Problems | ⇧⌘M | **⌘6** — Action Items (ở đây ⇧⌘M là Bật/tắt dấu trang) | Ctrl+Shift+M | **Ctrl+6** |
| Bật/tắt điểm dừng | F9 | **⌘F8** | F9 | **Ctrl+F8** |
| Bắt đầu gỡ lỗi | F5 | **⇧⌘F5** — Gỡ lỗi tệp | F5 | **Ctrl+Shift+F5** |
| Chạy không gỡ lỗi | ⌃F5 | **F6** — Chạy Dự án | Ctrl+F5 | **F6** |
| Cài đặt | ⌘, | **⌘,** — NMOX Studio ▸ Settings… | Ctrl+, | Công cụ ▸ Tùy chọn (không có tổ hợp phím) |

Mọi tổ hợp phím NMOX trong bảng đều được đọc ra từ sơ đồ phím đã phát hành,
chứ không phải nhớ lại (⌘, là của chính trình đơn ứng dụng trên macOS). Vài
điều mà một ô trong bảng không nói hết được:

- **F5 đã có việc khác khi đang gỡ lỗi.** Ở đây nó nghĩa là *Continue* (tiếp
  tục), như trong mọi IDE thuộc họ NetBeans, nên một lượt gỡ lỗi bắt đầu từ
  **⇧⌘F5** (Ctrl+Shift+F5) và chạy tiếp bằng F5.
- **⌃R ở đây là Đổi tên**, vì vậy *Chuyển dự án* nằm ở ⌥⌘P thay vì tổ hợp
  Open Recent của VS Code. Đổi tên hoạt động ở nơi ngôn ngữ đứng sau tệp hỗ
  trợ nó.
- **Sao chép đường dẫn tương đối không có tổ hợp phím.** ⇧⌥⌘C của VS
  Code là Ctrl+Alt+Shift+C trên PC, tức là *Clear Split* của nền tảng
  trong mọi hồ sơ phím tắt; mục này nằm trong menu Chỉnh sửa, và ⇧⌘P tìm
  thấy nó qua chính tiêu đề của VS Code, *File: Copy Relative Path of
  Active File*.
- **Ctrl+, trên Windows và Linux** lùi lại qua lịch sử chỉnh sửa của bạn, như
  nó vẫn luôn làm trong NetBeans; phần cài đặt nằm trong Công cụ ▸ Tùy chọn
  (trên macOS là mục **Settings…** của trình đơn ứng dụng, ⌘,).

**Trợ giúp ▸ Phím tắt bàn phím…** liệt kê mọi tổ hợp phím NMOX trong hồ sơ phím
đang dùng, kể cả bốn tổ hợp của VS Code, đọc từ sơ đồ phím đang chạy nên nó
không thể lệch khỏi những gì các phím làm.

### Mọi tổ hợp phím soạn thảo, đã đo

Những tổ hợp phím mà tay bạn quen VS Code tìm tới khi soạn thảo, mỗi tổ
hợp được tra trong sơ đồ phím đi kèm của hồ sơ mặc định trên macOS. Chỗ
nào tổ hợp phím của VS Code còn trống ở đây, giờ nó làm đúng việc VS Code
làm (các dòng ghi **Như nhau:**); chỗ nào nó đã mang một nghĩa mà người
dùng NetBeans dựa vào, nó giữ nghĩa đó và dòng đó cho biết hành động của
VS Code nằm ở đâu.

| VS Code, macOS | VS Code làm gì | Trong NMOX Studio |
|---|---|---|
| F12 | Go to Definition | **Như nhau:** Đi tới khai báo, như ⌘B |
| ⇧F12 | Go to References | **Như nhau:** Tìm nơi sử dụng, như ⌃F7 |
| F2 | Rename Symbol | **Như nhau:** Đổi tên, như ⌃R |
| ⌘. | Quick Fix | **Như nhau:** các cách sửa cho dòng đó, như ⌃↩ hiện ra |
| ⌥↑ / ⌥↓ | Move Line Up / Down | Lần xuất hiện được đánh dấu trước / kế tiếp; di chuyển dòng là ⌃⇧↑ / ⌃⇧↓ |
| ⇧⌥↑ / ⇧⌥↓ | Copy Line Up / Down | Như nhau, như xưa nay |
| ⇧⌘K | Delete Line | Chèn từ khớp kế tiếp (hoàn tất từ dựa trên tệp); xóa dòng là ⌘E |
| ⌘L | Expand Line Selection | Chọn định danh, hành động vẫn giữ tổ hợp phím này trong sơ đồ phím mặc định; Mở rộng vùng chọn theo dòng (Expand Line Selection) có trong Tìm kiếm nhanh dưới tên đó, và nằm ngay trên ⌘L trong các hồ sơ phím IntelliJ và Emacs, vốn để trống tổ hợp phím này |
| ⇧⌘L | Select All Occurrences | Dán thành dòng trong trình soạn thảo; chọn mọi lần xuất hiện là ⌃⇧⌘J |
| ⌘/ | Toggle Line Comment | Như nhau, như xưa nay |
| ⇧⌥A | Toggle Block Comment | **Như nhau:** bọc vùng chọn, hoặc dòng có con trỏ, trong cặp dấu chú thích khối của ngôn ngữ, rồi gỡ chúng ra lại; một vùng chọn đã chứa sẵn một dấu như vậy thì bị từ chối thay vì bị làm hỏng |
| ⌘] | Indent Line | **Như nhau:** Dịch sang phải |
| ⌘[ | Outdent Line | Nhảy tới dấu ngoặc tương ứng, như xưa nay; bỏ thụt lề là ⇧Tab hoặc ⌃⇧← |
| ⌥Z | Toggle Word Wrap | **Như nhau:** Xem ▸ Ngắt dòng. Nó bật/tắt ngắt dòng cho mọi trình soạn thảo của ngôn ngữ mà tệp dùng, chứ không phải cho một thẻ, và lựa chọn được lưu lại |
| ⌘B | Toggle Sidebar | Đi tới khai báo; ⇧⌘↩ chỉ hiện trình soạn thảo, ⇧Esc phóng to cửa sổ bạn đang ở |
| ⌘J | Toggle Panel | Thêm lần xuất hiện kế tiếp trong trình soạn thảo (như ⌘D); cửa sổ Output là ⌘4 |
| ⌘\ | Split Editor | Hoàn tất mã trong trình soạn thảo; chia đôi trình soạn thảo là ⌃⇧⌘V |
| ⇧⌘T | Reopen Closed Editor | Như nhau, như xưa nay: tổ hợp phím của Mở tệp gần đây mở lại tệp vừa đóng |
| ⌃- / ⌃⇧- | Go Back / Go Forward | **Như nhau:** Lùi và Tiến qua những chỗ bạn vừa sửa, như ⌃← / ⌃→ (các tổ hợp mà macOS thường giữ để chuyển màn hình nền) |
| ⌘G / ⇧⌘G | Find Next / Previous | Như nhau, như xưa nay |
| ⌥⌘F | Replace | **Như nhau:** Thay thế, như ⌘R |
| ⇧⌘F | Find in Files | Như nhau, như xưa nay: Tìm trong dự án |
| ⇧⌘O | Go to Symbol in Editor | Mở dự án; các ký hiệu của tệp có trong Tìm kiếm nhanh: ⌘I, rồi `@name` như trong VS Code (Điều hướng ▸ Đi tới ký hiệu trong tệp này… gõ sẵn `@` cho bạn), và ở dạng cây trong Bộ điều hướng (⌘7) |
| ⌘T | Go to Symbol in Workspace | Hoán đổi hai chữ cái quanh con trỏ trong trình soạn thảo; ký hiệu của dự án là ⌥⇧⌘O |
| ⌃G | Go to Line | Như nhau, như xưa nay |
| ⌘K ⌘S | Keyboard Shortcuts | ⌘K là Chèn từ khớp trước đó; bảng liệt kê là **Trợ giúp ▸ Phím tắt bàn phím…** |
| ⌘, | Settings | Như nhau, như xưa nay: NMOX Studio ▸ Settings… |
| ⇧⌥F | Format Document | **Như nhau:** Định dạng, như ⌃⇧F |

Các tổ hợp ghi **Như nhau:** được gán trong mọi hồ sơ phím để trống chúng,
và hồ sơ nào đã cho một tổ hợp trong số đó nghĩa riêng thì giữ nghĩa ấy:
F12 trong các hồ sơ Eclipse, Emacs và NetBeans 5.5, F2 trong mọi hồ sơ trừ
hồ sơ mặc định, ⇧F12 trong Emacs và NetBeans 5.5, ⌃- và ⌃⇧- trong Emacs và
IntelliJ, ⇧⌥F trong IntelliJ. Trên Windows và Linux, F12, ⇧F12 và F2 hoạt
động giống vậy; các tổ hợp khác của VS Code ở đó thì khác, và bảng ở trên
ghi cả hai.

Bật/tắt chú thích khối và Bật/tắt ngắt dòng cũng mang tổ hợp phím VS Code
của chúng trên Windows và Linux. Ngắt dòng là Alt+Z trong mọi hồ sơ. Bật/tắt
chú thích khối là Shift+Alt+A trong các hồ sơ mặc định, Emacs và IntelliJ
(các hồ sơ Eclipse và NetBeans 5.5 giữ Alt+Shift+A của riêng chúng ở đó), và
trên Linux nó còn là Ctrl+Shift+A, tổ hợp phím của VS Code trên Linux, trong
cả năm hồ sơ. Mở rộng vùng chọn theo dòng là Ctrl+L trên Windows và Linux
chỉ trong hồ sơ IntelliJ; trong hồ sơ Emacs, Ctrl+L ở đó vẫn là lệnh
recenter của chính Emacs.

Một ngôn ngữ không có chú thích khối, Python chẳng hạn, sẽ nói điều đó trên
thanh trạng thái. Trong một tệp HTML, Vue hoặc Svelte, một khối `<script>`
hoặc `<style>` được chú thích theo ngôn ngữ của chính nó, và phần frontmatter
của một thành phần Astro cũng vậy; một vùng chọn sẽ mang một chú thích vào
trong hoặc ra khỏi một khối như thế thì bị từ chối kèm tên thay vì làm hỏng
tệp. Khi không có vùng chọn, tổ hợp phím bật/tắt chú thích cho dòng; nó không đi tìm một chú thích
chỉ đơn thuần bao quanh con trỏ, nên để gỡ một chú thích dài nhiều dòng, hãy
chọn nó.

Trong Tìm kiếm nhanh, các ký hiệu của tệp là danh mục **Ký hiệu trong tệp
này**. Chỉ gõ `@` sẽ liệt kê chúng từ đầu tệp; `m name` (chữ cái đó, một dấu
cách, rồi tên) tìm trong danh mục đó và không danh mục nào khác.

### Hồ sơ phím VS Code

Các bảng ở trên là hồ sơ mặc định, nơi một tổ hợp phím mà người dùng
NetBeans dựa vào vẫn giữ nghĩa của nó. Nếu đôi tay bạn muốn trọn sơ đồ phím
của VS Code hơn, hãy đổi hồ sơ: gõ *Dùng sơ đồ phím VS Code* vào Tìm kiếm
nhanh (⇧⌘P hoặc ⌘I; dòng kết quả là *Preferences: Use the VS Code Keymap*),
hoặc chọn **VS Code** trong Công cụ ▸ Tùy chọn ▸ Phím tắt ▸ Profile
(trên macOS là NMOX Studio ▸ Settings… ▸ Phím tắt ▸ Profile). Thanh trạng
thái cho biết bạn đang ở hồ sơ nào và cách quay lại; không có gì tự đổi hồ
sơ thay bạn.

Trong hồ sơ đó, các tổ hợp phím mặc định của VS Code làm đúng việc chúng làm
trong VS Code, trên macOS cũng như trên Windows và Linux, ở bất cứ đâu sản
phẩm này có hành động tương ứng: ⌥↑ / ⌥↓ di chuyển dòng, ⇧⌘K xóa dòng, ⌘L mở
rộng vùng chọn theo dòng, ⇧⌘L chọn mọi lần xuất hiện, ⌘[ / ⌘] bỏ thụt lề và
thụt lề, ⌘↩ chèn một dòng bên dưới, ⇧⌘\\ nhảy tới dấu ngoặc, ⌥⌘[ / ⌥⌘] thu
gọn và mở rộng, ⌘K ⌘0 / ⌘K ⌘J thu gọn và mở rộng tất cả, ⌘K ⌘X cắt khoảng
trắng cuối dòng, ⌘J hiện cửa sổ Output, ⌘\\ chia đôi trình soạn thảo, ⌘T và
⇧⌘O tới một ký hiệu trong dự án và trong tệp, ⇧⌘M hiện Mục cần xử lý, ⇧⌘D
cửa sổ của trình gỡ lỗi, ⌘K ⌘S bảng Phím tắt bàn phím, ⌘K ⌘W đóng mọi trình
soạn thảo, ⌘K ⌘O mở một thư mục, ⌃R mở một dự án gần đây, ⇧⌘B dựng, F1 là
bảng lệnh, và các phím của trình gỡ lỗi là của VS Code: F5 bắt đầu gỡ lỗi dự
án hoặc tiếp tục một lượt đang tạm dừng, ⇧F5 dừng, ⌃F5 chạy không gỡ lỗi, F9
bật/tắt một điểm dừng, F10, F11 và ⇧F11 bước qua, bước vào và bước ra. Mọi
tổ hợp phím của sản phẩm mà VS Code không dùng (họ ⌥⌘ mở cửa sổ, ⌥⌘E của
Emmet, ⌥⌘G) vẫn nằm nguyên chỗ cũ.

Những gì vẫn khác trong hồ sơ VS Code, nêu đích danh:

- **Hoàn toàn không có tổ hợp phím**, vì ở đây không có gì là hành động đó:
  ⌘B (*Toggle Primary Side Bar*), ⇧⌘W (*Close Window*), ⇧⌘F5 (*Restart*
  gỡ lỗi), F8 / ⇧F8 (*Go to Next / Previous Problem in Files*), ⌥F12 (*Peek
  Definition*), ⇧⌘↩ (*Insert Line Above*) và ⌘U (*Cursor Undo*). Các phím
  này không làm gì cả, thay vì làm một việc khác.
- **Lưu tất cả không có tổ hợp phím trên Windows và Linux** (Ctrl+K S của
  VS Code); trên macOS nó là ⌥⌘S.
- **Những gì các tổ hợp phím của VS Code lấy đi của NetBeans.** F1 không còn
  mở trợ giúp, ⌘B không còn đi tới khai báo (F12 làm việc đó), các phép đổi
  chữ hoa/thường của ⌘U, hoàn tất từ bằng ⌘K / ⇧⌘K, hoán đổi bằng ⌘T và
  lịch sử khay nhớ tạm của ⇧⌘D không có tổ hợp phím trong hồ sơ này, và Gỡ
  lỗi tệp, Bật/tắt dấu trang, Chỉ hiện trình soạn thảo và Mở dự án cũng vậy.
  Danh sách đầy đủ, cho mọi hệ điều hành, là
  `scripts/vscode-keymap/displaced.txt` trong mã nguồn.


<a id="from-the-terminal"></a>
## Từ dòng lệnh

`code .` thành `nmox .`:

```bash
cd ~/code/my-app
nmox .          # open this folder (manifest or not) and aim the IDE at it
nmox src/app.ts # open one file
nmox src/app.ts:42  # open it at line 42 (code -g's form; -g itself is accepted)
nmox            # just start the IDE
```

Lệnh trả về ngay, và một lệnh `nmox` thứ hai trao thư mục của nó cho IDE đang
chạy. Cột cũng được chấp nhận (`src/app.ts:42:7`) và trình soạn thảo mở ở đầu
dòng; một tên không tồn tại sẽ bị từ chối ngay trên dòng lệnh thay vì khởi
động bất cứ thứ gì. `-r` được chấp nhận, `-n` mở trong cửa sổ duy nhất, còn
`-a` và `-v` sẽ bị từ chối kèm tên.

`-w` (`--wait`) mở một tệp và chờ đến khi bạn đóng thẻ của nó, còn `-d`
(`--diff`) so sánh hai tệp cạnh nhau, nên NMOX Studio có thể làm trình soạn
thảo, difftool và mergetool của git, giống như `code --wait`:

```bash
git config --global core.editor "nmox -w"
git config --global diff.tool nmox
git config --global difftool.nmox.cmd 'nmox -w -d "$LOCAL" "$REMOTE"'
git config --global merge.tool nmox
git config --global mergetool.nmox.cmd 'nmox -w "$MERGED"'
git config --global mergetool.nmox.trustExitCode false
```

Khi đó `git commit` mở thông điệp trong IDE; lưu lại, đóng thẻ, và git sẽ tiếp
tục. Thoát IDE khi tệp vẫn còn mở cũng trả nó về, với những gì đã được lưu.
`git mergetool` mở từng tệp bị xung đột theo cùng cách đó. Ở chỗ VS Code đặt
*Accept Current Change | Accept Incoming Change | Accept Both Changes* phía
trên một xung đột, NMOX Studio tô màu hai phía và đặt một cảnh báo ở dòng
`<<<<<<<`; bóng đèn ở lề, hoặc bản sửa nhanh khi con trỏ nằm trên dòng đó (⌘.
trên Mac, Alt+Enter ở nơi khác), đưa ra đúng ba lựa chọn ấy, mỗi lựa chọn là
một lần sửa có thể hoàn tác. Lưu, đóng thẻ, và git chuyển sang tệp kế tiếp.
Màu tô và ba lựa chọn có mặt trong mọi tệp có dấu xung đột, dù có dùng
`git mergetool` hay không.
**Nhóm ▸ Dùng NMOX Studio với Git…** đặt giúp bạn đúng những dòng đó, sau khi
cho thấy mỗi dòng hiện đang có giá trị gì.
Homebrew, trình cài đặt Windows (*Add "nmox" to PATH*) và
các gói Linux đưa nó vào PATH của bạn; với bản cài từ DMG,
[hướng dẫn sử dụng](user-guide.vi.md#2-first-launch) chỉ cách tạo liên kết
bằng một dòng lệnh.

<a id="where-each-vs-code-idea-lives"></a>
## Mỗi ý tưởng của VS Code nằm ở đâu

| Trong VS Code | Trong NMOX Studio |
|---|---|
| **Explorer** | **Studio dự án** (⇧⌘E) — cây tệp (nhấp chuột phải vào một tệp để có Sao chép đường dẫn, Sao chép đường dẫn tương đối và Hiện trong Finder), các mẫu, và trình soạn `package.json` của dự án. **Bàn làm việc** (⌥⌘0) là cơ sở của bạn: tệp đang mở, tệp gần đây, dự án gần đây, và mọi thứ đang chạy. |
| **Command Palette** | **Tìm kiếm nhanh** (⇧⌘P hoặc ⌘I) — hành động, tệp, dự án gần đây, thiết bị trên giá, máy chủ đang chạy, yêu cầu của Studio API, ký hiệu. Tên lệnh riêng của VS Code cũng dùng được: *Format Document*, *Toggle Terminal*, *Git: Commit* hoặc *Open Settings* liệt kê hành động làm cùng việc đó ở đây, dưới **Lệnh VS Code**, kèm tên và tổ hợp phím riêng của nó. |
| **Extensions** | **Công cụ ▸ Plugin** cài và cập nhật các mô-đun, kể cả các bản cập nhật của chính NMOX. Tiện ích mở rộng của VS Code không cài được ở đây, nên **Công cụ ▸ Tiện ích mở rộng VS Code được khuyến nghị…** trả lời câu hỏi mà tệp `.vscode/extensions.json` của một kho mã đặt ra: với mỗi tiện ích mở rộng mà tệp khuyến nghị, thứ gì làm việc đó trong NMOX Studio — một tính năng có sẵn, một cửa sổ mà nó mở được, một thiết bị trên giá, một máy chủ ngôn ngữ (và máy chủ đó đã được cài hay chưa), hoặc không có gì — còn một tiện ích mở rộng mà nó không biết thì được nói rõ là không biết chứ không đoán mò. Phần lớn những gì một tiện ích mở rộng thêm vào VS Code thì ở đây là một **thiết bị trên giá** — và bạn có thể tự viết một thiết bị bằng một tệp JSON trong `~/.nmox/devices.d` ([tệp thiết bị](device-files.md)). |
| **`tasks.json`** | Tệp `.vscode/tasks.json` của kho mã được đọc: gõ tên một tác vụ vào Tìm kiếm nhanh (⇧⌘P hoặc ⌘I) và Enter trên *Chạy tác vụ: build — make all* sẽ chạy nó — hoặc chọn nó từ danh sách mà **Chạy ▸ Chạy tác vụ…** hiển thị — với lời hỏi Tin cậy không gian làm việc đến trước ở một dự án bạn chưa tin cậy, đầu ra nằm trong cửa sổ Output và nút ■ trên thanh công cụ để dừng nó. Các tác vụ mà nó `dependsOn` chạy trước, `${file}` là tệp đang mở trong trình soạn thảo, và `${input:…}` hỏi bạn trước khi bất cứ thứ gì khởi động. `problemMatcher` của một tác vụ biến đầu ra của nó thành các vấn đề trong Mục cần xử lý và các đường gợn sóng trong trình soạn thảo (`$tsc`, `$eslint-stylish` và các matcher có sẵn khác của VS Code theo tên, hoặc một matcher viết trực tiếp), và một trình theo dõi chạy nền như `tsc -w` được chờ cho tới khi matcher của nó cho biết một chu kỳ đã xong. Bên cạnh đó, các kịch bản của chính dự án chạy đúng như chúng được viết: Chạy / Dựng / Kiểm thử trên thanh công cụ (F6, F11, ⌃F6), **Chạy script** trên một dòng scripts của `package.json`, **Trình duyệt NPM**, và **Giá tác vụ** (⌘9), nơi tác vụ là các thiết bị mà bạn nối dây với nhau. |
| **`launch.json`** | Tệp `.vscode/launch.json` của kho mã được đọc: gõ tên một cấu hình vào Tìm kiếm nhanh (⇧⌘P hoặc ⌘I) và Enter trên *Gỡ lỗi: Launch Program — ${workspaceFolder}/server.js* sẽ khởi động trình gỡ lỗi với điểm dừng trên chương trình đó — **Gỡ lỗi ▸ Bắt đầu gỡ lỗi…** liệt kê cùng những cấu hình ấy — với lời hỏi Tin cậy không gian làm việc đến trước. Các cấu hình Node (`node`, `pwa-node`) gỡ lỗi `program` của chúng trong `cwd` của chúng, kèm `args`, `env` và `envFile` của chúng, dưới `runtimeExecutable` và `runtimeArgs` của chúng — nên một cấu hình `npm run dev`, `tsx` hay `--experimental-strip-types` khởi động đúng như nó được viết — và một `"request": "attach"` của Node gắn vào một tiến trình `node --inspect` trên máy này. Các cấu hình Python (`python`, `debugpy`) gỡ lỗi `program` của chúng với `args`, `env`, `envFile` và trình thông dịch mà `python` của chúng chỉ định; các cấu hình Chrome (`chrome`, `pwa-chrome`) mở `url` (hoặc `file`) của chúng với `webRoot` của chúng. `"program": "${file}"` gỡ lỗi tệp mà trình soạn thảo của bạn đang hiển thị, và một `preLaunchTask` nêu tên một tác vụ trong `tasks.json` của bạn sẽ chạy trước: trình gỡ lỗi khởi động khi tác vụ đã thành công. Khi không có `launch.json`, **Gỡ lỗi tệp** (⇧⌘F5) và nút gỡ lỗi trên thanh công cụ tự tìm ra thứ cần khởi chạy từ chính dự án — mục vào của kịch bản `start`, `main`, `index.js` — còn thiết bị **INSPECTOR** trên giá khởi chạy trình gỡ lỗi như một bước trong dây chuyền. |
| **Integrated terminal** | Cửa sổ **Terminal** (⌃\`): lần bấm đầu tiên khởi động một shell trong thư mục dự án, những lần sau đưa nó trở lại. |
| **`settings.json`** | Công cụ ▸ Tùy chọn (trên macOS là NMOX Studio ▸ Settings…). Tệp `.vscode/settings.json` của một kho mã cũng được đọc: `editor.tabSize`, `editor.insertSpaces` và `editor.indentSize` đặt cách thụt lề của nó khi bạn gõ, `files.trimTrailingWhitespace` và `files.insertFinalNewline` (khi là `true`) được áp dụng khi bạn lưu, `files.eol` là kiểu kết thúc dòng mà các tệp được ghi ra, `"editor.formatOnSave": false` ngăn một lần lưu định dạng lại tệp, phần tử đầu tiên của `editor.rulers` là chỗ trình soạn thảo vẽ đường lề phải (một danh sách rỗng thì không vẽ đường nào), `editor.wordWrap` `"on"` hoặc `"off"` ngắt dòng các tệp của dự án đó hoặc giữ chúng không ngắt, `files.exclude` ẩn những gì nó nêu khỏi các cây dự án, và **Tìm trong dự án** bỏ qua những gì `files.exclude` và `search.exclude` nêu; một khối ngôn ngữ như `"[typescript]"` ghi đè chúng cho ngôn ngữ của khối đó. Khi kho mã cũng có tệp `.editorconfig`, tệp `.editorconfig` thắng ở bất cứ chỗ nào cả hai cùng nói. Cài đặt VS Code của riêng bạn được chuyển sang một lần, khi bạn yêu cầu: **Công cụ ▸ Nhập cài đặt VS Code…** |
| **Problems panel** | **Mục cần xử lý** (⌘6), hoặc nhấp vào con số **✕ ⚠** trên thanh trạng thái: lỗi và cảnh báo của các máy chủ ngôn ngữ, cùng các phát hiện về lint và kiểu từ các thiết bị PURITY và TYPEGUARD của giá. Như trong VS Code, có máy chủ chỉ báo cáo các tệp bạn đang mở; gopls báo cáo cả gói. |
| **Search view** (`search.useIgnoreFiles`) | **Tìm trong dự án** (⇧⌘F). Như trong VS Code, nó bỏ qua những gì các tệp `.gitignore` của kho và `.git/info/exclude` bỏ qua, nên `node_modules` và `dist/` nằm ngoài kết quả khi `.gitignore` liệt kê chúng; bên ngoài một kho, nó bỏ qua theo tên `node_modules`, `dist`, `build` và các thư mục build khác. Hãy đánh dấu **Tìm trong nguồn được sinh ra** trong hộp thoại của nó để tìm cả trong đó. Tệp loại trừ git toàn cục của bạn không được đọc. |
| **Outline** | **Bộ điều hướng** (⌘7). |
| **Snippets** (`.vscode/*.code-snippets`) | Các tệp đoạn mã mẫu (snippet) mà nhóm của bạn đã commit được đọc nguyên như chúng vốn có: gõ một tiền tố, nhấn ⌃Space, và đoạn mã mẫu được liệt kê dưới dạng *tiền tố — Tên (mô tả)* kèm tệp của nó bên cạnh, giới hạn trong những ngôn ngữ mà `scope` của nó nêu. Chấp nhận nó sẽ chèn phần thân cùng các điểm dừng tab, các bản sao đồng bộ (mirror), các biến (`TM_FILENAME`, `CURRENT_YEAR`, `UUID` và những biến còn lại) và các phép biến đổi `/regex/format/`; Tab đi vòng qua các điểm dừng, còn Enter đi tiếp và sau điểm dừng cuối cùng thì đáp xuống `$0`. |
| **Auto Save** (`files.autoSave`) | **Tệp ▸ Tự động lưu** bật và tắt nó; lưu thường xuyên đến mức nào, và có lưu cả khi một tệp mất tiêu điểm hay không, nằm ở thẻ Tự động lưu của mục Trình soạn thảo trong phần cài đặt. Đây là tùy chọn của riêng bạn: `files.autoSave` của một kho mã không được đọc. |
| **Markdown preview** | Thẻ **Xem trước** ở đầu một trình soạn thảo Markdown, bên cạnh **Mã nguồn**. |
| **Timeline** (local history) | Thẻ **Lịch sử** ở đầu mọi trình soạn thảo: các phiên bản của tệp mà IDE đã giữ lại mỗi khi bạn lưu, mỗi phiên bản đều so sánh được với tệp như hiện giờ và khôi phục được. |
| **Breadcrumbs** | **Xem ▸ Hiện đường dẫn điều hướng**. |
| **Source Control** | Dấu git trên thanh trạng thái (nhánh và các thay đổi, một cú nhấp tới lịch sử) và trình đơn **Nhóm**. |
| **Workspace Trust** | Cùng một ý tưởng, được áp dụng trước khi bất cứ thứ gì một kho mã chọn được chạy: mở một dự án vừa clone về thì không có gì chạy cho tới khi bạn tin cậy nó. |
| **Keyboard Shortcuts editor** | Công cụ ▸ Tùy chọn ▸ Phím tắt (trên macOS là Settings… ▸ Phím tắt) — sửa bất kỳ tổ hợp phím nào, hoặc chuyển cả hồ sơ phím sang VS Code, Eclipse, Emacs hoặc IntelliJ. |

Lần đầu bạn mở một kho mã có `.vscode/tasks.json`, `launch.json`,
`settings.json` hoặc `extensions.json`, một thông báo cho biết đã tìm thấy gì
và nó nằm ở đâu; nhấp vào đó để mở Tìm kiếm nhanh (hoặc, với các tiện ích mở
rộng, để mở bảng cho biết thứ gì đảm nhận việc của từng tiện ích). Thông báo
chỉ hiện một lần cho mỗi dự án.

<a id="what-is-honestly-different"></a>
## Những gì thật sự khác

- **⌘D thêm lần xuất hiện kế tiếp trong sơ đồ phím mặc định, chứ không
  phải trong mọi hồ sơ.** Hồ sơ Eclipse giữ ⌘D là *Delete Line* của
  Eclipse, hồ sơ NetBeans 5.5 giữ nó là *Shift Line Left* và hồ sơ Emacs
  giữ nó là *kill word* (còn Ctrl+D là *delete character* trên Windows và
  Linux); hồ sơ IntelliJ có ⌘D trên macOS và giữ Ctrl+D là *Duplicate
  Line* trên Windows và Linux. Tổ hợp phím còn lại của thao tác này cũng
  khác theo hồ sơ: ⌘J (Ctrl+J) trong hồ sơ mặc định, ⌃J (Alt+J) trong
  Eclipse và IntelliJ, và không có trong Emacs và NetBeans 5.5, nơi Phím
  tắt có thể gán cho nó một tổ hợp.
- **⌃\` mở và đặt tiêu điểm vào Terminal; nó không ẩn Terminal.** Và khi
  Terminal đang có tiêu điểm, các phím thuộc về shell của bạn, nên lần bấm thứ
  hai tới shell chứ không đưa bạn về trình soạn thảo.
- **`launch.json` được đọc, và những gì trình gỡ lỗi không đáp ứng được thì bị
  từ chối.** Trình gỡ lỗi ở đây truyền một chương trình, thư mục làm việc của
  nó, `args` của nó (một danh sách chuỗi), `env` và `envFile` của nó, và
  runtime dùng để khởi động nó (`runtimeExecutable` và `runtimeArgs` với
  Node, `python` với Python); với Node, nó còn gắn vào một tiến trình đang
  chạy sẵn. Một cấu hình đặt `postDebugTask`, `restart` hay bất kỳ trường nào
  khác mà nó chưa được dạy thì được liệt kê nhưng không được khởi chạy: Enter
  nêu tên các trường đó trên thanh trạng thái. Khởi chạy chương trình mà
  thiếu chúng sẽ là gỡ lỗi một thứ khác với những gì tệp nói. `args` hoặc
  `runtimeArgs` viết thành một chuỗi duy nhất (VS Code trao chuỗi đó cho một
  shell) và một giá trị `null` trong `env` (vốn xóa một biến) bị từ chối theo
  cùng cách đó, và một mục `compounds`, một kiểu không có bộ chuyển ở đây
  (`go`, `msedge`, `cppdbg` và các kiểu khác), một lượt gắn vào bất cứ thứ gì
  không phải Node, một giá trị mà chỉ VS Code mới cung cấp được
  (`${input:…}`, `${command:…}`), và một chương trình, thư mục làm việc hoặc
  `envFile` nằm ngoài dự án cũng vậy. Các trường chỉ định hình những gì trình
  gỡ lỗi hiển thị — `skipFiles`, `outFiles`, `sourceMaps`, `console`,
  `justMyCode`, `presentation` — được chấp nhận nhưng không được áp dụng; đầu
  ra của chương trình đi tới cửa sổ Output. Có năm điều đáng biết trước khi
  bạn nhấn Enter:
  - **Một `preLaunchTask` chạy trước, và phải thành công.** Tác vụ được chạy
    đúng như Enter trên nó trong Tìm kiếm nhanh sẽ chạy — các tác vụ mà nó
    `dependsOn`, các câu hỏi của nó, thẻ Output riêng của nó — và trình gỡ
    lỗi khởi động khi tác vụ đã kết thúc với mã thoát 0; ở chỗ VS Code hỏi
    *Debug Anyway?* sau một tác vụ thất bại, ở đây thanh trạng thái cho biết
    cấu hình đã không được khởi động. Một `program` do tác vụ dựng ra
    (`dist/server.js`) được tìm sau khi tác vụ chạy, chứ không phải trước.
    Một nhãn mà `tasks.json` không định nghĩa, một nhãn mà hai tác vụ dùng
    chung và dạng đối tượng (`{"type": "npm", "script": "build"}`) bị từ
    chối kèm tên trước khi bất cứ thứ gì chạy. Một tác vụ nền
    (`"isBackground": true`, một trình theo dõi không bao giờ kết thúc) được
    chờ cho tới khi problem matcher của nó báo một chu kỳ đã xong, rồi tiếp
    tục chạy, và nhấn Gỡ lỗi lần nữa sẽ dùng lại nó; một tác vụ nền không có
    matcher nào báo được khi nào nó sẵn sàng thì bị từ chối kèm tên.
  - **Một `envFile` không có ở đó thì bị từ chối**, trong khi VS Code khởi
    động chương trình mà không có nó. Các biến của nó được thêm vào môi
    trường và một mục `env` thắng tệp, như trong VS Code. Tệp được đọc thành
    các dòng `NAME=value` đơn giản (dòng chú thích, `export`, và một cặp dấu
    nháy quanh một giá trị đều được); một dòng mà VS Code sẽ đọc khác đi —
    một ký tự thoát bên trong dấu nháy kép, một dấu `#` sau một giá trị, một
    dấu nháy ngược, và trong một cấu hình Python là một `export` hoặc một
    `${NAME}` — bị từ chối kèm tên tệp và số dòng, không bao giờ kèm giá trị.
  - **`${file}`, `${relativeFile}`, `${fileBasename}`,
    `${fileBasenameNoExtension}`, `${fileExtname}`, `${fileDirname}` và các
    biến tệp khác nghĩa là tệp của trình soạn thảo đang hoạt động**: thẻ đang
    có tiêu điểm khi nó là một trình soạn thảo, nếu không thì là thẻ đang
    hiện trong vùng soạn thảo — cùng tệp mà `${file}` của một tác vụ chỉ tới.
    Khi không có tệp nào đang mở, cấu hình bị từ chối kèm tên biến, và một
    `${file}` nằm ngoài dự án bị từ chối như mọi chương trình khác ở đó.
  - **Một `runtimeExecutable` là một cái tên hoặc một đường dẫn tuyệt đối.**
    Một cái tên (`npm`, `tsx`, `nodemon`) được tìm trên PATH của bạn rồi
    trong `node_modules/.bin` của dự án; `${workspaceFolder}/node_modules/.bin/tsx`
    thì phải có ở đó; một đường dẫn tương đối bị từ chối, vì VS Code sẽ tìm
    nó như một cái tên. Khi runtime là toàn bộ câu lệnh (`npm run dev` mà
    không có `program`), script mà nó khởi động được gỡ lỗi như một phiên
    riêng, được liệt kê trong cửa sổ Sessions.
  - **Gắn là gắn vào máy này.** `port` (9229 nếu không ghi) và một `address`
    là `localhost`, `127.0.0.1` hoặc `::1`; mọi địa chỉ khác bị từ chối, vì
    đây không phải là phát triển từ xa. Nếu không có gì đang lắng nghe, thanh
    trạng thái sẽ nói điều đó, và kết thúc phiên vẫn để chương trình của bạn
    chạy tiếp.
- **`tasks.json` được đọc, và những gì không chạy được đúng như đã viết thì
  bị từ chối.** Một tác vụ chạy với các tác vụ mà nó `dependsOn` chạy trước
  (cùng lúc, hoặc lần lượt từng tác vụ với `"dependsOrder": "sequence"`), với
  `${file}`, `${relativeFile}`, `${lineNumber}`, `${selectedText}` và phần còn
  lại của họ biến đó được điền từ tệp đang mở trong trình soạn thảo, và với
  các câu hỏi `${input:…}` của nó (`promptString`, `pickString`) được hỏi
  trước khi bất cứ thứ gì khởi động. Cả lượt chạy được quyết định trước: nếu
  một tác vụ trong đó không chạy được đúng như đã viết thì không có gì chạy,
  và Enter cho biết tác vụ nào và vì sao trên thanh trạng thái. Điều đó bao
  gồm một nhãn `dependsOn` mà tệp không định nghĩa, một phụ thuộc là tác vụ
  nền không có problem matcher nào báo được khi nào nó sẵn sàng, `${file}`
  khi không có tệp nào đang mở, và một câu hỏi mà bạn
  hủy. Một phụ thuộc thất bại sẽ dừng lượt chạy ngay tại đó. Vẫn bị từ chối
  kèm tên: một giá trị mà chỉ VS Code mới cung cấp được (`${config:…}`,
  `${command:…}`, một đầu vào có `"type": "command"`), một phụ thuộc viết
  thành một đối tượng (`{"type": "npm", …}`) thay vì một nhãn, một kiểu tác
  vụ do tiện ích mở rộng cung cấp (`gulp`, `typescript`), và một thư mục làm
  việc nằm ngoài dự án.
- **Đoạn mã mẫu là của kho mã của bạn, và có ba điều khác.** Một lựa chọn
  (`${1|a,b|}`) bắt đầu bằng phương án đầu tiên của nó, không có danh sách để
  chọn; một chỗ giữ chỗ lồng bên trong giá trị mặc định của một chỗ giữ chỗ
  khác trở thành một phần của giá trị mặc định đó; và một đoạn mã mẫu có phép
  biến đổi không thể chạy an toàn (một biểu thức chính quy mà Java không biên
  dịch được, hoặc một biểu thức có thể chạy mãi không dừng) bị loại ra và
  được nêu tên trong nhật ký thay vì bị chèn dở dang. Gõ một tiền tố không tự
  mở danh sách: ⌃Space mới mở. Các đoạn mã mẫu không có `prefix`, đoạn mã mẫu
  cấp người dùng và `isFileTemplate` không được đọc. Các tệp đoạn mã mẫu chỉ
  được đọc cho những tệp nằm trong kho mã chứa chúng, và một đoạn mã mẫu sẽ
  chèn hơn một triệu ký tự thì không được chèn.
- **`problemMatcher` của một tác vụ được đọc.** Mỗi lượt chạy thay thế các
  vấn đề trước đó của tác vụ, và một lượt chạy sạch sẽ xóa chúng. Các matcher
  có sẵn của VS Code dùng được theo tên (`$tsc`, `$tsc-watch`, `$tsgo-watch`,
  `$eslint-stylish`, `$eslint-compact`, `$jshint`, `$jshint-stylish`,
  `$msCompile`, `$lessCompile`, `$gulp-tsc`, `$go`, `$lessc`), và `$gcc`
  cùng `$rustc` từ các tiện ích mở rộng C/C++ và rust-analyzer cũng vậy; các
  matcher viết trực tiếp và `{"base": "$tsc", …}` cũng dùng được, kể cả các
  mẫu nhiều dòng có `loop`, với mọi `fileLocation` trừ `"search"`. Một
  matcher mà IDE này không có, hoặc có biểu thức chính quy mang nghĩa khác ở
  đây, không chặn tác vụ: tác vụ vẫn chạy, và thanh trạng thái nêu tên
  matcher không được áp dụng, một lần.
- **Định dạng định dạng JavaScript và TypeScript bằng Prettier của dự án.**
  Mã nguồn ▸ Định dạng (⇧⌥F) chạy Prettier mà dự án của bạn cấu hình, hỏi
  Tin cậy không gian làm việc trước khi Prettier đó là của chính dự án; một
  dự án không có cấu hình Prettier sẽ nói điều đó trên thanh trạng thái thay
  vì để nguyên tệp mà không nói một lời.
- **Cài đặt của riêng bạn được chuyển sang một lần, khi bạn yêu cầu.**
  **Công cụ ▸ Nhập cài đặt VS Code…** (hoặc *import vs code settings* trong
  Tìm kiếm nhanh) đọc `settings.json` cá nhân của VS Code — từ VS Code,
  Insiders hoặc VSCodium — và liệt kê mỗi cài đặt mà nó nhận ra cùng với thứ
  mà cài đặt đó trở thành ở đây: độ rộng tab và dấu cách, ngắt dòng, thước
  lề, cách hiển thị khoảng trắng, cắt khoảng trắng khi lưu, Định dạng khi
  lưu, bản đồ thu nhỏ, cuộn dính và tự động lưu. Những cài đặt mang đúng
  cùng một nghĩa ở đây được đánh dấu sẵn; những cài đặt gần giống thì cho
  biết chúng khác ở đâu và không được đánh dấu sẵn. Áp dụng ghi những cài
  đặt được đánh dấu và chúng có hiệu lực ngay; Hủy không ghi gì. Phông chữ
  của trình soạn thảo được đặt trong phần cài đặt Phông chữ và màu sắc, còn
  phím tắt là các hồ sơ phím. Mọi thứ khác trong tệp của bạn — token, đường
  dẫn, cài đặt của tiện ích mở rộng — chỉ được đếm, không bao giờ được hiển
  thị hay sao chép.
- **`files.associations` không được đọc.** Nền tảng quyết định kiểu của một
  tệp một lần rồi giữ nguyên, nên một ánh xạ trong `settings.json` không thể
  được tôn trọng đúng như viết; một mục loại trừ có điều kiện (`"when"`), các
  thước lề sau thước đầu tiên cùng màu của chúng, và ngắt dòng tại một cột
  cũng không được đọc.
- **Một tác vụ `"type": "shell"` chạy trong shell mà VS Code sẽ dùng.** Trên
  macOS và Linux, đó là `$SHELL` của bạn với `-c` (zsh, bash hoặc fish trên
  macOS khởi động như một login shell, `-l`, như các hồ sơ mặc định của VS
  Code vẫn làm); trên Windows, đó là PowerShell, `pwsh` nếu đã cài.
  `options.shell` được tôn trọng theo cách của VS Code: chỉ định một
  `executable` thì nó chạy với đúng những `args` bạn đưa, nên bash cần
  `"args": ["-c"]`. Trên Windows, chỉ PowerShell (args kết thúc bằng
  `-Command`) và `cmd.exe` (args kết thúc bằng `/c`) được chạy; mọi shell khác
  ở đó bị từ chối kèm tên thay vì được trao một dòng lệnh được trích dẫn theo
  kiểu đoán mò. Một tên tệp, vùng chọn hoặc một câu trả lời đầu vào nằm trong
  `command` của một tác vụ shell được trích dẫn cho shell đó, nên
  `"command": "python ${file}"` chạy được với mọi tên tệp, kể cả tên có dấu
  cách hay dấu `$`; trong `cmd.exe`, một giá trị chứa `%`, `!`, dấu nháy kép
  hoặc dấu xuống dòng bị từ chối kèm tên, vì ở đó không cách trích dẫn nào
  làm những ký tự ấy vô hại.
- **Các bảng ở trên mô tả hồ sơ phím mặc định.** Trong hồ sơ **VS Code**
  (xem *Hồ sơ phím VS Code* ở trên), các tổ hợp phím riêng của VS Code thắng
  ở bất cứ đâu sản phẩm này có hành động tương ứng. Bốn tổ hợp phím đầu tiên
  có mặt trong mọi hồ sơ. Có một ngoại lệ có chủ ý: trong hồ sơ
  **Eclipse**, ⇧⌘E vẫn là *Switch to Editor* của chính Eclipse, và bên trong
  trình soạn thảo ⇧⌘P và ⇧⌘X giữ nghĩa của Eclipse (ngoặc tương ứng, chữ in
  hoa) — người đã chọn Eclipse mong đợi Eclipse.
- **Trên Linux, Ctrl+\` mở Terminal, không phải trình chuyển cửa sổ.** Trình
  chuyển cửa sổ nằm ở Ctrl+Tab. Trên một môi trường desktop chiếm Ctrl+Tab cho
  riêng nó (KDE chẳng hạn), **Cửa sổ ▸ Tài liệu…** liệt kê các tệp đang mở
  thay vào đó.
- **Các tổ hợp Ctrl+Alt có thể va với AltGr.** Trên Windows, các bố cục bàn
  phím gõ ký tự bằng AltGr (tiếng Ba Lan chẳng hạn) gửi Ctrl+Alt cho phím đó.
  Nếu Ctrl+Alt+P hoặc Ctrl+Alt+K gõ ra một ký tự, hãy dời *Chuyển dự án* hoặc
  các tổ hợp thử nghiệm sang phím khác trong Phím tắt.
- **Tiện ích mở rộng của VS Code không cài được ở đây.** Trí thông minh về
  ngôn ngữ đến từ các máy chủ ngôn ngữ mà NMOX biết (Trình chẩn đoán môi
  trường liệt kê những gì còn thiếu và cách cài), từ các ngữ pháp riêng của
  trình soạn thảo, và từ các plugin viết cho NetBeans Platform.
