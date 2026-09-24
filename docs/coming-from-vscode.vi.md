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
| Tới định nghĩa | F12 | **⌘B** | F12 | **Ctrl+B** |
| Đổi tên một ký hiệu | F2 | **⌃R** | F2 | **Ctrl+R** |
| Tới dòng | ⌃G | **⌃G** | Ctrl+G | **Ctrl+G** |
| Bật/tắt chú thích dòng | ⌘/ | **⌘/** | Ctrl+/ | **Ctrl+/** |
| Hiện gợi ý | ⌃Space | **⌃Space** | Ctrl+Space | **Ctrl+Space** |
| Thêm lần xuất hiện kế tiếp vào vùng chọn | ⌘D | **⌘D** hoặc ⌘J | Ctrl+D | **Ctrl+D** hoặc Ctrl+J |
| Chọn mọi lần xuất hiện | ⇧⌘L | **⌃⇧⌘J** | Ctrl+Shift+L | **Ctrl+Alt+Shift+J** |
| Thêm con trỏ ở dòng trên / dưới | ⌥⌘↑ / ⌥⌘↓ | **⌥⌘↑ / ⌥⌘↓** | Ctrl+Alt+↑ / ↓ | **Alt+Shift+[ / ]** |
| Dời dòng lên / xuống | ⌥↑ / ⌥↓ | **⌃⇧↑ / ⌃⇧↓** | Alt+↑ / ↓ | **Alt+Shift+↑ / ↓** |
| Nhân bản dòng xuống dưới | ⇧⌥↓ | **⌥⇧↓** | Shift+Alt+↓ | **Ctrl+Shift+↓** |
| Xóa dòng | ⇧⌘K | **⌘E** | Ctrl+Shift+K | **Ctrl+E** |
| Định dạng tài liệu | ⇧⌥F | **⌃⇧F** | Shift+Alt+F | **Alt+Shift+F** |
| Đóng thẻ trình soạn thảo | ⌘W | **⌘W** | Ctrl+W | **Ctrl+W** |
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
- **Ctrl+, trên Windows và Linux** lùi lại qua lịch sử chỉnh sửa của bạn, như
  nó vẫn luôn làm trong NetBeans; phần cài đặt nằm trong Công cụ ▸ Tùy chọn
  (trên macOS là mục **Settings…** của trình đơn ứng dụng, ⌘,).

**Trợ giúp ▸ Phím tắt bàn phím…** liệt kê mọi tổ hợp phím NMOX trong hồ sơ phím
đang dùng, kể cả bốn tổ hợp của VS Code, đọc từ sơ đồ phím đang chạy nên nó
không thể lệch khỏi những gì các phím làm.


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
`--wait`, `--diff` và các cờ khác chỉ VS Code mới có sẽ bị từ chối kèm tên.
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
| **Extensions** | **Công cụ ▸ Plugin** cài và cập nhật các mô-đun, kể cả các bản cập nhật của chính NMOX. Phần lớn những gì một tiện ích mở rộng thêm vào VS Code thì ở đây là một **thiết bị trên giá** — và bạn có thể tự viết một thiết bị bằng một tệp JSON trong `~/.nmox/devices.d` ([tệp thiết bị](device-files.md)). |
| **`tasks.json`** | Tệp `.vscode/tasks.json` của kho mã được đọc: gõ tên một tác vụ vào Tìm kiếm nhanh (⇧⌘P hoặc ⌘I) và Enter trên *Chạy tác vụ: build — make all* sẽ chạy nó, với lời hỏi Tin cậy không gian làm việc đến trước ở một dự án bạn chưa tin cậy, đầu ra nằm trong cửa sổ Output và nút ■ trên thanh công cụ để dừng nó. Bên cạnh đó, các kịch bản của chính dự án chạy đúng như chúng được viết: Chạy / Dựng / Kiểm thử trên thanh công cụ (F6, F11, ⌃F6), **Chạy script** trên một dòng scripts của `package.json`, **Trình duyệt NPM**, và **Giá tác vụ** (⌘9), nơi tác vụ là các thiết bị mà bạn nối dây với nhau. |
| **`launch.json`** | Tệp `.vscode/launch.json` của kho mã được đọc: gõ tên một cấu hình vào Tìm kiếm nhanh (⇧⌘P hoặc ⌘I) và Enter trên *Gỡ lỗi: Launch Program — ${workspaceFolder}/server.js* sẽ khởi động trình gỡ lỗi với điểm dừng trên chương trình đó, với lời hỏi Tin cậy không gian làm việc đến trước. Các cấu hình Node (`node`, `pwa-node`) và Python (`python`, `debugpy`) gỡ lỗi `program` của chúng trong `cwd` của chúng, kèm `args` và `env` của chúng; các cấu hình Chrome (`chrome`, `pwa-chrome`) mở `url` (hoặc `file`) của chúng với `webRoot` của chúng. Khi không có `launch.json`, **Gỡ lỗi tệp** (⇧⌘F5) và nút gỡ lỗi trên thanh công cụ tự tìm ra thứ cần khởi chạy từ chính dự án — mục vào của kịch bản `start`, `main`, `index.js` — còn thiết bị **INSPECTOR** trên giá khởi chạy trình gỡ lỗi như một bước trong dây chuyền. |
| **Integrated terminal** | Cửa sổ **Terminal** (⌃\`): lần bấm đầu tiên khởi động một shell trong thư mục dự án, những lần sau đưa nó trở lại. |
| **`settings.json`** | Công cụ ▸ Tùy chọn (trên macOS là NMOX Studio ▸ Settings…). Tệp `.vscode/settings.json` của một kho mã cũng được đọc: `editor.tabSize`, `editor.insertSpaces` và `editor.indentSize` đặt cách thụt lề của nó khi bạn gõ, `files.trimTrailingWhitespace` và `files.insertFinalNewline` (khi là `true`) được áp dụng khi bạn lưu, và một khối ngôn ngữ như `"[typescript]"` ghi đè chúng cho ngôn ngữ của khối đó. Khi kho mã cũng có tệp `.editorconfig`, tệp `.editorconfig` thắng ở bất cứ chỗ nào cả hai cùng nói. |
| **Problems panel** | **Mục cần xử lý** (⌘6), hoặc nhấp vào con số **✕ ⚠** trên thanh trạng thái: lỗi và cảnh báo của các máy chủ ngôn ngữ, cùng các phát hiện về lint và kiểu từ các thiết bị PURITY và TYPEGUARD của giá. Như trong VS Code, có máy chủ chỉ báo cáo các tệp bạn đang mở; gopls báo cáo cả gói. |
| **Outline** | **Bộ điều hướng** (⌘7). |
| **Source Control** | Dấu git trên thanh trạng thái (nhánh và các thay đổi, một cú nhấp tới lịch sử) và trình đơn **Nhóm**. |
| **Workspace Trust** | Cùng một ý tưởng, được áp dụng trước khi bất cứ thứ gì một kho mã chọn được chạy: mở một dự án vừa clone về thì không có gì chạy cho tới khi bạn tin cậy nó. |
| **Keyboard Shortcuts editor** | Công cụ ▸ Tùy chọn ▸ Phím tắt (trên macOS là Settings… ▸ Phím tắt) — sửa bất kỳ tổ hợp phím nào, hoặc chuyển cả hồ sơ phím sang Eclipse, Emacs hoặc IntelliJ. |

Lần đầu bạn mở một kho mã có `.vscode/tasks.json`, `launch.json` hoặc
`settings.json`, một thông báo cho biết đã tìm thấy gì và nó nằm ở đâu;
nhấp vào đó để mở Tìm kiếm nhanh. Thông báo chỉ hiện một lần cho mỗi dự án.

<a id="what-is-honestly-different"></a>
## Những gì thật sự khác

- **⌘D thêm lần xuất hiện kế tiếp trong sơ đồ phím mặc định, chứ không phải
  trong mọi hồ sơ.** Hồ sơ Eclipse giữ ⌘D là *Delete Line* của Eclipse, và hồ
  sơ NetBeans 5.5 giữ nó là *Shift Line Left*; ở đó, ⌘J (Ctrl+J) là cùng thao
  tác ấy.
- **⌃\` mở và đặt tiêu điểm vào Terminal; nó không ẩn Terminal.** Và khi
  Terminal đang có tiêu điểm, các phím thuộc về shell của bạn, nên lần bấm thứ
  hai tới shell chứ không đưa bạn về trình soạn thảo.
- **`launch.json` được đọc, và những gì trình gỡ lỗi không đáp ứng được thì bị
  từ chối.** Trình gỡ lỗi ở đây truyền một chương trình, thư mục làm việc của
  nó, `args` của nó (một danh sách chuỗi) và `env` của nó (các chuỗi được
  thêm vào môi trường kế thừa), nên một cấu hình đặt `envFile`,
  `runtimeExecutable`, `runtimeArgs`, `preLaunchTask` hay bất kỳ trường nào
  khác mà nó chưa được dạy thì được liệt kê nhưng không được khởi chạy: Enter
  nêu tên các trường đó trên thanh trạng thái. Khởi chạy chương trình mà thiếu
  chúng sẽ là gỡ lỗi một thứ khác với những gì tệp nói. `args` viết thành một
  chuỗi duy nhất (VS Code trao chuỗi đó cho một shell) và một giá trị `null`
  trong `env` (vốn xóa một biến) bị từ chối theo cùng cách đó, và
  `"request": "attach"`, một mục `compounds`, một kiểu không có
  bộ chuyển ở đây (`go`, `msedge`, `cppdbg` và các kiểu khác), một giá trị mà
  chỉ VS Code mới cung cấp được (`${file}`, `${input:…}`), và một đường dẫn
  nằm ngoài dự án cũng vậy. Các trường chỉ định hình những gì trình gỡ lỗi hiển thị —
  `skipFiles`, `outFiles`, `sourceMaps`, `console`, `justMyCode`,
  `presentation` — được chấp nhận nhưng không được áp dụng; đầu ra của chương
  trình đi tới cửa sổ Output.
- **`tasks.json` được đọc, và những gì không chạy được đúng như đã viết thì
  bị từ chối.** Một tác vụ dùng giá trị mà chỉ VS Code mới cung cấp được (`${input:…}`, `${file}`,
  `${config:…}`, `${command:…}`) hoặc có `dependsOn` tới một tác vụ khác thì
  được liệt kê nhưng không chạy: Enter cho biết biến nào hoặc tác vụ nào trên
  thanh trạng thái. Chạy nó với giá trị để trống, hoặc thiếu tác vụ mà nó phụ
  thuộc, sẽ là chạy một thứ khác với những gì tệp nói. Một kiểu tác vụ do tiện
  ích mở rộng cung cấp (`gulp`, `typescript`) và một thư mục làm việc nằm
  ngoài dự án cũng vậy.
- **Một tác vụ `"type": "shell"` chạy trong shell mà VS Code sẽ dùng.** Trên
  macOS và Linux, đó là `$SHELL` của bạn với `-c` (zsh, bash hoặc fish trên
  macOS khởi động như một login shell, `-l`, như các hồ sơ mặc định của VS
  Code vẫn làm); trên Windows, đó là PowerShell, `pwsh` nếu đã cài.
  `options.shell` được tôn trọng theo cách của VS Code: chỉ định một
  `executable` thì nó chạy với đúng những `args` bạn đưa, nên bash cần
  `"args": ["-c"]`. Trên Windows, chỉ PowerShell (args kết thúc bằng
  `-Command`) và `cmd.exe` (args kết thúc bằng `/c`) được chạy; mọi shell khác
  ở đó bị từ chối kèm tên thay vì được trao một dòng lệnh được trích dẫn theo
  kiểu đoán mò.
- **Không có hồ sơ phím “VS Code”.** Các tổ hợp phím ở trên nằm trong hồ sơ
  mặc định và bốn hồ sơ còn lại. Có một ngoại lệ có chủ ý: trong hồ sơ
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
