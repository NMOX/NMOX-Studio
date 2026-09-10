# NMOX Studio — Hướng dẫn sử dụng

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · **Tiếng Việt** · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

> Bản dịch một phần: chương 1–5 có tiếng Việt. Phần còn lại, xem [hướng dẫn đầy đủ bằng tiếng Anh](user-guide.md).

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

<a id="4-the-task-rack"></a>
## 4. Giá tác vụ

![Giá tác vụ](images/tabs/the-task-rack.png)

Giá là trái tim của sản phẩm. Mọi công cụ trong luồng làm việc của bạn — npm, trình đóng gói, trình chạy kiểm thử, máy chủ phát triển, trình soi lỗi, git, việc triển khai — đều là một thiết bị trong một giá: các núm chọn tác vụ, GO chạy nó, các đèn LED cho thấy trạng thái, và một màn hình LCD kể cho bạn bằng lời chuyện gì đã xảy ra.

![Giá hướng vào một trang jQuery cổ điển — bộ dựng sẵn Classic Web Bench: MAESTRO, CRATE, DYNAMO (núm TASK của nó đã đọc đúng tệp Gruntfile thật), IGNITION phục vụ tĩnh, VITALS canh chất lượng](images/task-rack.png)

**Những điều căn bản:**

- **Thêm thiết bị** bằng cách kéo chúng từ bảng chọn (bảng có phân loại và ô lọc tìm kiếm). Mỗi thiết bị đều mang theo thẻ *Cách dùng* của nó.
- **Chạy một thứ gì đó** bằng cách nhấn nút GO của một thiết bị. Hãy rê chuột lên trước: chú giải cho thấy đúng dòng lệnh sẽ được chạy. Không có phép màu nào cả.
- **Đấu một dây chuyền:** nhấn **Tab** để lật giá ra mặt sau. Kéo một sợi dây patch từ giắc **OK** của thiết bị này tới giắc **GO** của thiết bị kế tiếp. Giờ `cài → dựng → kiểm thử` chỉ còn một phím: dây chuyền tự chạy và dừng ngay ở lần hỏng đầu tiên. Kết quả trôi trên màn hình lân quang của thiết bị MONITOR.
- **Hoàn tác mọi thay đổi cấu trúc** bằng **⌘Z** — thêm, bớt, đấu lại. Gỡ một thiết bị đang chạy sẽ dừng tiến trình của nó trước.
- **Các bộ dựng sẵn** trao cho bạn cả một giá đã đấu dây chỉ với một cú nhấp — Ship Gate, Dev Intelligence, Monorepo Lanes, E2E Loop, LAMP Bench, Web3 Bench, Uptime Watch. Các sơ đồ được lưu theo từng dự án một cách tự động.

![Tab lật giá lại — những sợi dây patch dẫn MAESTRO qua CRATE, DYNAMO và IGNITION tới VITALS](images/rack-rear.png)

**Sự phối hợp, khi dây chuyền của bạn lớn dần:**

- **QUORUM** gộp các làn: nó chỉ kích hoạt khi *tất cả* các đầu vào đã đấu của nó đều thành công — cái kinh điển “chờ soi lỗi VÀ kiểm thử VÀ kiểm kiểu”.
- **Các cổng ENABLE** trên những thứ chạy lâu: đầu vào ENABLE của một máy chủ phát triển có nghĩa là “đừng khởi động cho tới khi cái này kích hoạt”.
- **REFLEX** trông chừng các tệp và định tuyến theo mẫu — `src/**/*.css` sang một dây chuyền, `**/*.ts` sang dây chuyền khác, theo từng làn trong một monorepo.
- **ROSETTA** chọn làn công cụ trong những kho lẫn lộn (giá nhận ra Node/Rust/Go/PHP/… theo từng thư mục và hướng mỗi thiết bị cho phù hợp).

**Những làn nói đúng bộ công cụ của bạn.** Ở AUTO, các thiết bị soi lỗi và định dạng (PURITY, GLOSS) nói bằng bộ công cụ của chính dự án thay vì với tay sang công cụ Node ở khắp nơi: một không gian Deno dùng `deno lint` và `deno fmt`, một dự án Cargo dùng `cargo clippy` và `cargo fmt`, một mô-đun Go dùng `go vet` (hoặc `golangci-lint` khi dự án mang theo cấu hình của nó) và `gofmt`. Một tệp `biome.json` chuyển các làn Node sang Biome, và vị trí núm đặt rõ ràng luôn thắng AUTO.

**Thiết bị của riêng bạn.** Kệ có thể mở rộng chỉ bằng một trình soạn thảo văn bản: bất kỳ tệp `*.json` nào trong `~/.nmox/devices.d/` đều trở thành một thiết bị thật — núm, nút, đèn LED, cổng và dây, được lưu vào sơ đồ và với tới được từ ⌘I. Khai báo một lệnh dưới dạng mảng đối số, đặt tên một núm, và `{{núm}}` sẽ được thế vào khi nút được nhấn. Các luật ở lại với chủ nhà, không nằm trong tệp của bạn: **lòng tin không gian làm việc canh lần chạy đầu tiên y như với một thiết bị dựng sẵn**.

**Các cổng chất lượng** biến “trông có vẻ xong” thành “đã xong”:

- **VITALS** chạy Lighthouse trên máy chủ đang sống của bạn và đòi một mức sàn về hiệu năng, khả năng tiếp cận, thực hành tốt hoặc SEO.
- **VERITAS** giữ một mức sàn về độ phủ và chạy lại đúng những bài kiểm thử đã hỏng, theo tên.
- **GAUNTLET** dồn tải lên một điểm cuối và đòi một mức thông lượng tối thiểu. **PRISM** canh kích thước gói, **BEACON** canh chứng chỉ và tình trạng sống của một địa chỉ, còn **PREFLIGHT** là bản kiểm trước khi gửi đi — đấu đầu OK của nó vào thiết bị triển khai và việc triển khai đơn giản là không thể chạy chừng nào chưa xanh hết.
- **GOVERNOR** canh những bước lùi về phí gas trong công việc Solidity (`.gas-snapshot`).

**Mọi thứ khác:** **SOLDER** bọc bất kỳ lệnh shell nào thành một thiết bị đầy đủ — và cả giá **xuất ra GitHub Actions** (dây chuyền cục bộ của bạn và phần tích hợp của bạn là cùng một sơ đồ đấu nối). **HELM** chạy lệnh trên máy chủ từ xa qua ssh, **TAIL** dõi theo bất kỳ tệp nhật ký nào, và **PHOSPHOR** là một cửa sổ dòng lệnh ngay trong giá. Nếu lệnh in ra một địa chỉ cục bộ, dấu ⇄ sáng lên như với mọi thiết bị phục vụ, rồi tắt khi lần chạy kết thúc.

**Giá tự giữ mình đồng bộ.** Sửa `package.json` và núm kịch bản của NPM-9000 tự cập nhật tại chỗ. Sửa một `Gruntfile` và DYNAMO đọc lại các tác vụ của nó. Thêm một phụ thuộc và màn hình của CRATE tự làm mới. Không phải hướng lại, không có nút làm mới nào.

### KVASIR — giải thích lần hỏng gần nhất

![KVASIR đang giải thích một lần chạy hỏng thật: chẩn đoán đã được cho phép trên mặt máy và trọn các bước sửa trong ô xem](images/kvasir-explain.png)

**KVASIR** là trợ giúp AI theo cách của giá: một thiết bị giải thích cái lỗi đang nằm trên bus MONITOR, chứ không phải một khung trò chuyện bên lề. Khi một lần chạy hỏng, hãy nhấn **EXPLAIN** và KVASIR sẽ hỏi AI của bạn xem đã sai ở đâu và bước tiếp theo cụ thể là gì. Một phán quyết ngắn hiện lên màn hình; **VIEW** mở ra câu trả lời đầy đủ. **MODEL** chọn giữa **FAST** (nhanh, rẻ, mặc định) và **DEEP** (mạnh hơn). EXPLAIN màu xanh: nó đọc và hỏi, nó không bao giờ đụng vào dự án của bạn.

**Chọn AI của bạn, đặt khóa của bạn.** KVASIR làm việc với **Claude (Anthropic)**, **ChatGPT (OpenAI)** hoặc **Gemini (Google)** — khóa của bạn, lựa chọn của bạn. Nhấn **KEY…** để chọn nhà cung cấp và dán khóa của họ; lựa chọn được ghi nhớ, còn khóa chỉ sống trong chùm khóa của hệ điều hành. Các biến môi trường quen thuộc của từng nhà cung cấp cũng được đọc, và khóa đã lưu thắng khóa từ môi trường.

**KVASIR gửi đi những gì, và chỉ có vậy.** Lần đầu bạn nhấn EXPLAIN, một hộp thoại liệt kê đúng những gì sẽ rời khỏi máy bạn và những gì thì không; không có gì được gửi mà thiếu sự đồng ý ấy, và sự đồng ý được tính riêng cho từng nhà cung cấp. Sau một lần EXPLAIN thành công, nút **VIEW** mở câu trả lời như một cuộc trò chuyện — bạn có thể hỏi tiếp về đúng lần hỏng đó.

**Hỏi KVASIR về mã của bạn.** Cũng chính trợ lý ấy với tới trình soạn thảo: chọn một đoạn mã rồi chọn **Hỏi KVASIR về phần đã chọn…**, hoặc **Sửa bằng KVASIR…** để nói cần đổi gì và xem một bản trước và một bản sau trước khi bất cứ gì được áp dụng. **⌥⌘G** gợi ý ngay tại con trỏ bằng chữ mờ, chỉ chèn vào nếu bạn nhấn Tab, và dấu nhánh git có thể soạn giúp bạn lời nhắn commit.

**Hướng một tác nhân vào IDE của bạn.** Công cụ ▸ Agent Port (MCP)… mở một điểm cuối MCP mà một trợ lý bên ngoài có thể hỏi: nó **chỉ đọc theo thiết kế**, tắt cho tới khi bạn bật, chỉ lắng nghe trên giao diện cục bộ, và đòi đúng thẻ được sinh ra lúc khởi động.

Giá có thể mở rộng: các trình cắm của bên thứ ba có thể thêm thiết bị (cài tệp NBM của họ qua Công cụ ▸ Trình cắm). Để tự viết một cái, xem [device-spi.md](device-spi.md).

<a id="5-the-editor"></a>
## 5. Trình soạn thảo

![Mã jQuery trong bảng màu NMOX Phosphor, cấu trúc nằm trong Trình duyệt cấu trúc](images/editor.png)

Hơn 70 ngôn ngữ được tô màu đúng cách — bộ hiện đại, bộ cổ điển (kể cả CoffeeScript) và trọn lớp cấu hình, xuống tới `.env`, `.editorconfig`, cấu hình nginx và Apache, các tệp Dockerfile và các tệp khóa phiên bản.

- **Gợi ý hoàn thành** hiểu ngữ cảnh, và cũng hiểu *các thư viện cổ điển*: nếu dự án của bạn mang jQuery, MooTools, Prototype, Backbone/Underscore hay Knockout (qua phụ thuộc npm *hoặc* thẻ `<script>` thuần), API của chúng sẽ xuất hiện khi gợi ý. Dự án dùng jQuery 1.x hay 2.x nhận một dấu hết vòng đời trung thực, chứ không phải một lời nhắc dai dẳng.
- **Dàn ý trong Trình duyệt cấu trúc (⌘7)** cho thấy cấu trúc tệp với 58 kiểu tệp; nhấp để nhảy tới.
- **Bản đồ thu nhỏ** — bóng dáng của cả tệp nằm cạnh thanh cuộn của mỗi trình soạn thảo; nhấp hoặc kéo để cuộn. Cả tài liệu luôn vừa trong dải đó: các hàng co lại khi tệp lớn dần. Xem ▸ Bản đồ thu nhỏ bật tắt nó cho mọi trình soạn thảo đang mở cùng lúc.
- **Cuộn dính** — những khai báo bao lấy phần trên của khung nhìn (lớp, rồi tới phương thức bạn vừa cuộn vào) được ghim lại phía trên phần chữ, tối đa ba dòng của chính mã nguồn; nhấp một dòng để nhảy tới đó. Thanh này biến mất khi không có gì bao lấy dòng trên cùng.
- **Tới ký hiệu (⌥⇧⌘O)** nhảy tới bất kỳ hàm, lớp, quy tắc hay tiêu đề nào trong cả dự án bằng cách gõ tên nó — khớp theo tiền tố, theo chữ hoa giữa từ, hoặc theo ký tự đại diện. Chỉ mục có giới hạn và trung thực: `node_modules` bị bỏ qua, và với một dự án rất lớn hộp thoại nói rằng nó đã lập chỉ mục 2.000 tệp đầu tiên thay vì giả vờ đã đọc hết.
- **Cửa sổ kiểm thử (⌥⌘2)** cho thấy mọi bài kiểm thử trong dự án *trước khi bất cứ gì chạy*, và chạy một bài, một tệp, hoặc tất cả.

### Bung tắt tự (⌥⌘E)

Gõ một tắt tự Emmet rồi nhấn **⌥⌘E**: `ul>li*3` trở thành cả danh sách. Nó chạy trong HTML, trong khuôn mẫu Angular, và — ở dạng CSS — bên trong khối `<style>` và thuộc tính `style`, nơi phần cắt bị giới hạn trong vùng đó nên không bao giờ nuốt mất phần đánh dấu xung quanh. Một tắt tự mà sản phẩm không nhận ra sẽ bị từ chối và để nguyên chữ bạn đã gõ.

### Token thiết kế (thuộc tính tùy biến)

Gõ `var(` sẽ đưa ra những token được khai báo trong chính các tệp kiểu của dự án bạn, mỗi cái kèm ô màu và nơi nó được khai báo. **⌘-nhấp** vào một chỗ dùng `var(--token)` sẽ nhảy tới khai báo của nó. Màu được vẽ đúng như màu chúng là — hex, `rgb()`, `hsl()`, tên màu, và cả `oklch()`, `lab()` lẫn `color-mix()` — còn **⌘-nhấp** vào một giá trị màu sẽ mở bảng chọn thay nó vào đúng dạng bạn đã viết.

### Thuộc tính class biết các tệp kiểu của bạn

Gõ bên trong `class="…"` sẽ đưa ra những lớp mà dự án bạn thực sự định nghĩa, kèm tệp kiểu chúng đến từ đâu; **⌘-nhấp** vào một lớp nhảy tới quy tắc của nó, còn **⌘-nhấp** vào bộ chọn `.lớp` nhảy tới chỗ dùng đầu tiên trong phần đánh dấu. **Đổi tên lớp…** đổi tên trong cả dự án — chỉ những token trọn vẹn, kèm số lượng theo từng tệp — và từ chối ra tiếng nếu tên mới đã có hoặc còn thay đổi chưa lưu.

### Chạy kịch bản, ngay từ con trỏ

Trong phần `scripts` của một `package.json`, lệnh **Chạy kịch bản** chạy đúng dòng có con trỏ — qua cùng lời hỏi tin cậy không gian làm việc và cùng nút ■ như mọi lần chạy khác.

### Khóa môi trường, hạng nhất

Gõ `process.env.` hay `import.meta.env.` sẽ đưa ra những khóa mà họ tệp `.env` của bạn thực sự định nghĩa, và **⌘-nhấp** nhảy tới dòng khai báo khóa ấy. Giá trị hiển thị bị cắt bớt: lời nhắc thì có, bí mật thì không.

### Khuôn mẫu Angular, hạng nhất

Tệp `.component.html` mở ra với cách tô màu khuôn mẫu riêng, với các khối `@if`/`@for` và các chỉ thị cấu trúc trong gợi ý. Hãy cài Angular Language Service và việc kiểm kiểu cho khuôn mẫu thực sự tới nơi: gõ sai tên một thuộc tính, chính trình biên dịch của Angular sẽ gợi tên đúng. **⌘B** trong khuôn mẫu nhảy tới khai báo, còn trình đơn ngữ cảnh chuyển qua lại giữa thành phần, khuôn mẫu, tệp kiểu và bài kiểm thử của nó.

### Thành phần Vue và Svelte, hạng nhất

Tệp `.vue` và `.svelte` mở ra với cách tô màu riêng, gợi ý riêng (kể cả các rune có dấu chấm của Svelte 5) và Emmet bên trong khối khuôn mẫu của chúng. Chẩn đoán của Vue thực sự tới được trình soạn thảo, qua chính máy chủ ngôn ngữ của Vue.

### Gỡ lỗi với điểm dừng thật

Nhấp vào lề trái, chọn **Gỡ lỗi tệp (điểm dừng)** và chương trình sẽ dừng ngay đó — kèm ngăn xếp, các biến và việc tính biểu thức. JavaScript và TypeScript chạy được ngay nhờ bộ chuyển đi kèm; Python dùng debugpy còn Go dùng delve, do bạn tự cài. **Gỡ lỗi trong Chrome** làm y như vậy với một trang: các điểm dừng trong mã nguồn của bạn dừng lại ngay trong IDE trong khi trình duyệt chạy trên một hồ sơ dùng một lần. Mọi thứ đều đi qua lời hỏi tin cậy không gian làm việc trước.

### Trình bày và chia sẻ

**Xem ▸ Chế độ trình bày** phóng to cùng lúc mọi trình soạn thảo đang mở, trang trong trình duyệt tích hợp, cửa sổ kết xuất và cửa sổ dòng lệnh — rồi trả lại mọi thứ đúng như cũ khi bạn thoát. **Xem ▸ Hiện phím bấm** hiện thật lớn tổ hợp phím bạn vừa bấm, nhưng không bao giờ hiện thứ bạn gõ. **Sửa ▸ Chép dạng Markdown** chép phần đã chọn thành khối có rào kèm đúng nhãn ngôn ngữ, còn biến thể **kèm liên kết** thêm liên kết GitHub tới chính những dòng ấy. **Công cụ ▸ Lưu ảnh chụp…** vẽ cả cửa sổ ở kích thước gấp đôi, cùng các biến thể cho riêng thẻ soạn thảo, cho khay nhớ tạm, và để chép cây dự án ra dạng Markdown.
