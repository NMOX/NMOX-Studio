# NMOX Studio — Hướng dẫn sử dụng

<!-- languages -->
[English](user-guide.md) · [Español](user-guide.es.md) · [Français](user-guide.fr.md) · [Deutsch](user-guide.de.md) · [Русский](user-guide.ru.md) · [Українська](user-guide.uk.md) · [Polski](user-guide.pl.md) · [Português (Brasil)](user-guide.pt.md) · [Bahasa Indonesia](user-guide.id.md) · [Filipino](user-guide.tl.md) · **Tiếng Việt** · [简体中文](user-guide.zh.md) · [हिन्दी](user-guide.hi.md)
<!-- /languages -->

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

<a id="6-the-studios"></a>
## 6. Các studio

### Dùng bằng bàn phím và trình đọc màn hình

Mọi nút điều khiển trên giá đều có một tên đọc được, và điều đó được kiểm tra ở mỗi lần dựng. Các núm là thanh trượt nghe theo phím mũi tên, Home và End; các nút nghe theo phím cách và Enter, kể cả những nút đang mờ, vốn nói rõ vì sao chúng từ chối; đèn LED và màn hình đều công bố trạng thái của mình. Tab lật giá lại — trừ khi tiêu điểm đang nằm trên một nút điều khiển, lúc đó nó nhường cho việc chuyển tiêu điểm thông thường.

### Git, trên thanh trạng thái

Dấu **⎇ nhánh** cho biết bạn đang ở nhánh nào và bao nhiêu tệp đã đổi; nó được đọc từ đĩa nên không tốn một tiến trình nào. Một cú nhấp mở ra toàn bộ lịch sử, còn trình đơn mang theo **So sánh dự án**, **Chú giải**, các yêu cầu kéo qua chính `gh` của bạn, và **Soạn lời nhắn commit bằng KVASIR**.

### Bảng tác vụ (⌥⌘1)

Một bảng kanban cho mỗi dự án, lưu trong `.nmoxtasks.json` — nằm cạnh mã của bạn và được quản lý phiên bản cùng nó. Kéo các thẻ hoặc dời chúng bằng bàn phím: **⌘↑/⌘↓** sắp xếp lại, và thẻ vừa dời vẫn giữ tiêu điểm. Giới hạn việc đang làm là lời khuyên chứ không phải rào chắn: phần đầu cột đỏ lên, nhưng không có gì ngăn bạn. Nút **Tổng quan** đổi các cột lấy một bảng theo dõi — việc đang làm, xong hôm nay và trong tuần, dòng chảy theo ngày, những thẻ đang cũ dần — còn đồng hồ bấm giờ (**Chấm công**) đo thời gian thật theo từng thẻ, và cả bảng chỉ có duy nhất một đồng hồ chạy. **Họp nhanh** biến tất cả những thứ đó thành một bản báo cáo dán được ngay.

### Studio khối (⌥⌘5)

Ghép các Web Component thật từ những mảnh có kiểu khớp vào nhau, theo lối Scratch: những cách lồng không hợp lệ bị từ chối, mã sinh ra là một phần tử tùy biến đứng độc lập, và nhấp vào một mảnh sẽ tô sáng những dòng của nó. Một máy chủ xem trước trong bộ nhớ cho thấy thành phần ấy thật sự, ghép cùng những thành phần hợp lệ khác trong thư viện của bạn. Vòng đi về là chính xác: sinh lại thứ vừa đọc cho ra đúng cùng một tệp, từng byte một.

### Studio API (⌥⌘8)

Bộ sưu tập, yêu cầu, môi trường với `{{biến}}` và các bài kiểm thử, lưu trong `.nmoxapi.json` — còn bí mật thì chỉ nằm trong chùm khóa, không bao giờ trong tệp ấy. Mỗi phản hồi được chấm một điểm an toàn từ chính các tiêu đề của nó. Nhập từ curl, `.http`, OpenAPI, Postman, Insomnia và HAR; xuất ra `.http`, và chép thành curl hoặc thành `fetch`, với các biến đã được thay sẵn.

### Studio cơ sở dữ liệu (⌥⌘7)

SQLite, PostgreSQL, MySQL, MariaDB, MongoDB và CouchDB, với trình điều khiển đi kèm và mật khẩu chỉ nằm trong chùm khóa. Bảng điều khiển hiểu từng loại máy, mỗi câu lệnh có lưới kết quả riêng, và các hàng sửa được ngay trong lưới khi có khóa chính — kèm bản xem trước đúng những câu UPDATE trước khi áp dụng, và một lý do trung thực khi thứ gì đó chỉ đọc được. Xuất ra CSV hoặc JSON, với công thức đã bị vô hiệu.

### Studio hợp đồng (⌥⌘6)

Cây tạo phẩm của Foundry và Hardhat, phần **Tương tác** do ABI dẫn đường với giá trị trả về và các lần hoàn tác đã giải mã, khung **Theo dõi** bám các khối và sự kiện, cùng phần **Giám sát** với bảng phí gas, phán quyết kích thước EIP-170 và sổ địa chỉ. **Không bao giờ có khóa riêng**: việc gửi đi dùng các tài khoản đã mở khóa của một mạng cục bộ, còn những địa chỉ bí mật thì sống trong chùm khóa.

### Trình thiết kế hạ tầng (⌥⌘9)

Một khung vẽ cho DigitalOcean, Hetzner và Cloudflare: đồng bộ với thứ thật sự đang tồn tại, làm mới để thấy chỗ lệch, và phá bỏ cả một chồng tài nguyên với chi phí bày ngay trước mắt. Những đường nối không hợp lệ từ chối ra tiếng và nói rõ vì sao, còn trong lúc một thao tác trên đám mây đang chạy thì khung vẽ khóa lại kèm một dải chữ nói đúng điều đó.

### IRC (⌥⌘3)

Một ứng dụng đầy đủ ngay trong IDE: TLS có kiểm tra tên thật sự, SASL, các mở rộng IRCv3, hoàn thành bằng phím Tab, các điểm nhấn, những địa chỉ mở ra trong trình duyệt tích hợp, ghi nhật ký xuống đĩa, bộ lọc của riêng bạn, và một danh sách kênh mà bạn lọc dần trong lúc gõ.

### Trang web đi kèm

**Trợ giúp ▸ Trang NMOX Studio (cục bộ)** phục vụ trang web của sản phẩm từ chính giá của nó, trên giao diện cục bộ. Nó nói đúng mười ba thứ tiếng mà IDE nói; bộ chọn nằm ở chân trang.

### Trình duyệt (⌥⌘4)

Một trình duyệt thật ngay trong IDE, với bộ công cụ nhà phát triển của riêng nó — bảng điều khiển, DOM, mạng, kho lưu, và các khung cho Vue, Svelte và Angular — bởi bộ máy không mang theo trình kiểm tra nào, và cái này là của chúng tôi. Nó biết mã nguồn của bạn: chọn một phần tử, mở đúng dòng đã sinh ra nó, đổi kiểu ngay tại chỗ, và khai báo ấy sẽ nằm vào đúng tệp kiểu gốc. Lưu một tệp là trang tự tải lại, và có sẵn những kích thước thiết bị thật để thử bố cục co giãn của bạn.

<a id="7-docker"></a>
## 7. Docker

Thẻ Docker là một bảng điều khiển: trạng thái của máy, các thùng chứa, ảnh, ổ đĩa và mạng, cùng với khởi động, dừng, nhật ký và dọn dẹp. Thiết bị HARBOR trên giá cho bạn ngần ấy chỉ trong một cái nhìn. Và như đã nói: chạy một thùng chứa Postgres, MySQL hay Mongo, và Xưởng cơ sở dữ liệu sẽ mời bạn một kết nối đã sẵn sàng.

Thẻ **Dockerize** sinh ra một `Dockerfile` đạt chuẩn sản xuất, một `.dockerignore` và một tệp soạn thảo hợp với bộ công cụ của dự án bạn — Node, PHP-FPM cùng nginx, và nhiều thứ khác.

<a id="8-wizards-and-kits"></a>
## 8. Trình hướng dẫn và bộ công cụ

Tất cả nằm trong *Tệp mới…* và trong trình đơn ngữ cảnh của dự án, và tất cả đều **luỹ đẳng và không bao giờ ghi đè**: chạy lại một bộ chỉ làm mới những gì thuộc về chính nó và để yên các sửa đổi của bạn; thứ không được phép viết lại sẽ đáp xuống bên cạnh dưới dạng tệp `.suggested`.

### Bộ chuẩn mực

`robots.txt`, `sitemap.xml`, bản kê khai web, `security.txt` theo RFC 9116 và `humans.txt`, được sinh ra từ những câu trả lời của bạn.

### Bộ PWA

Một bộ biểu tượng đầy đủ rèn từ một tấm ảnh duy nhất, kể cả các biến thể che được; một service worker dễ đọc — vỏ ứng dụng hoặc mạng trước, bạn chọn —, một trang cho lúc mất mạng, và phần nối dây trong `index.html` buộc tất cả lại với nhau.

### Bộ tiếp cận

Khả năng tiếp cận là điểm khởi đầu, không phải cuộc kiểm tra muộn màng: `a11y.css` (một vòng tiêu điểm nhìn thấy được, một tiện ích cho phần chữ chỉ trình đọc màn hình mới đọc, kiểu dáng cho liên kết nhảy qua, và một khối cho ai muốn ít chuyển động hơn), `A11Y-NOTES.md` với lượt đi bằng bàn phím và những câu hỏi không máy móc nào trả lời được, cùng phần nối dây luỹ đẳng trong `index.html` — ngôn ngữ, liên kết nhảy qua, bảng kiểu. Một viewport cấm phóng to sẽ được cảnh báo chứ không bao giờ bị viết lại; điều bộ này không sửa được thì nó nói ra, chứ không đụng vào.

### Bộ quốc tế hoá

Dịch được ngay từ ngày đầu, anh em với bộ tiếp cận: `locales/en.json` và `locales/es.json` (mỗi ngôn ngữ một danh mục, cùng bộ khoá), một `i18n.js` không phụ thuộc gì, áp danh mục lên phần đánh dấu `data-i18n`, giữ cho `<html lang>` nói thật, và hiện một khoá thiếu bằng chính nó chứ không phải một khoảng trống lặng lẽ; thêm `I18N-NOTES.md` — không ghép mảnh, `Intl` cho ngày tháng và con số, lượt đi từ phải sang trái, và giả bản địa hoá.

### Bộ hợp đồng (Web3)

Chọn một chuỗi — Solidity với Foundry, Soroban, Solana, CosmWasm, ink!, Cairo, Move, Bitcoin với Miniscript, Clarity trên Stacks, Cardano với Aiken hoặc TON với Tact — và một tên hợp đồng, rồi bộ sẽ dựng phần khởi đầu đã được chứng thực trực tiếp: bản kê khai, hợp đồng, bài kiểm tra bản địa và một CONTRACT-NOTES.md gọi tên các thiết bị trên giá cùng những bước chỉ làm một lần. Khoá không bao giờ chạm tới IDE.

### Bộ cổ điển

Thêm vào bất cứ mã nguồn nào jQuery, MooTools, Prototype, Backbone cùng Underscore, hoặc Knockout, hoặc để ngay trong kho (phiên bản ghim chặt, sha256 ghi lại) hoặc làm phụ thuộc npm; thêm khung sườn của webpack, grunt, gulp hay bower.

<a id="9-quick-search-status-line-and-staying-oriented"></a>
## 9. Tìm nhanh, thanh trạng thái, và giữ được phương hướng

### Dấu ⇄ đang phục vụ

Trên thanh trạng thái hiện dấu **⇄ đang phục vụ** mỗi khi có máy chủ đang chạy: lượt chạy của chính IDE, các thiết bị đang phục vụ, và bất cứ lệnh nào đã in ra một địa chỉ nội bộ. Bấm rồi chọn một cái: nó mở trong trình duyệt tích hợp, hoặc trong trình duyệt của hệ thống khi thẻ kia không kham nổi.

### ⌘I, cái tìm cho mọi thứ

Một ô duy nhất với tới các dự án của bạn (gần đây và đã biết), tới từng thiết bị trên giá — nhảy thẳng đến bộ điều khiển của nó —, tới các **máy chủ đang chạy** (Enter mở nó trong trình duyệt), tới các yêu cầu của Xưởng API, tới các kết nối và bảng của Xưởng cơ sở dữ liệu, tới các hợp đồng, tới các nút hạ tầng, và tới các thẻ của Bảng công việc, mà kết quả còn gọi tên cột thẻ đang đứng.

### Thanh trạng thái cho biết cái gì còn sống

Bên cạnh dấu máy chủ là dự án đang nhắm cùng bộ công cụ của nó, và nhánh Git kèm số tệp bạn đã đổi. Tất cả đọc từ đĩa hoặc từ những ghi chép mà sản phẩm vốn đã giữ: nhìn một cái không tốn tiến trình nào.

### Bàn làm việc

Đây là bến nhà: dự án hiện thời, các tệp đang mở và gần đây, các dự án gần đây, và lối vào cho từng bề mặt. Chừng nào còn thứ gì chạy, mục **ĐANG CHẠY** dẫn đầu trang — mỗi lệnh mà sản phẩm đã khởi động thay bạn, kèm địa chỉ nếu nó có báo, và từ mấy giờ nó chạy, cộng thêm mọi máy chủ mà một thiết bị trên giá đang phục vụ. Mỗi dòng có nút **Mở** và **Dừng** thật, với tới được bằng bàn phím lẫn trình đọc màn hình, nên một lượt chạy có thể dừng mà không kéo đổ những lượt còn lại. Mọi tiêu đề trên Bàn làm việc đều là nút thật: Tab tới được, Enter mở ra. ⌘I cũng với tới đúng những lượt chạy ấy: gõ «dừng» rồi Enter dừng đúng cái đó. Cái bạn tự tay dừng sẽ đọc là *đã dừng* ở bất cứ đâu kết cục của nó được kể lại, chứ không bao giờ là thất bại.

### Phím tắt của Emacs (và của Eclipse, và của IntelliJ)

Công cụ ▸ Tuỳ chọn ▸ Sơ đồ phím đổi cả hồ sơ: các phím di chuyển và cắt dán của Emacs trong mọi trình soạn thảo, hoặc bộ của Eclipse và IDEA nếu trí nhớ ngón tay bạn nằm ở đó. Mọi phím tắt của NMOX đều có mặt trong cả năm hồ sơ, nên đổi hồ sơ không bao giờ khiến bạn mất các phím tắt của những xưởng.

<a id="10-the-safety-nets-things-you-dont-have-to-do-anything-for"></a>
## 10. Những tấm lưới an toàn (thứ bạn chẳng phải làm gì để có)

### Hồi sinh phiên làm việc

Cứ vài giây một lần, giá lại chụp lại những gì đang chạy. Một lần tắt cưỡng bức, một lần sập, một `kill -9` — lần mở sau, một lời nhắn mời bạn dựng lại đúng phiên đã mất, chỉ một cú bấm.

### Bảo đảm không để lại mồ côi

Thoát khỏi IDE là giết mọi tiến trình mà nó đã khởi động — máy chủ phát triển, REPL, chuỗi lệnh, kẻ canh chừng —, TERM trước, KILL nếu chúng cứng đầu, kể cả con cháu.

### BLACKBOX và SONAR

Đặt **BLACKBOX** lên giá và bạn có một hộp đen: mỗi lần khởi động và mỗi lần thoát, kèm thời lượng, xu hướng, và những gì đã đổi kể từ lần dựng xanh gần nhất. Cái bạn tự tay dừng đọc là ĐÃ DỪNG — không xanh, không thất bại, và không bao giờ là thứ đem hỏi KVASIR. **SONAR** cho thấy ai đang giữ các cổng của bạn, đối chiếu với Docker, và một cú bấm là đuổi kẻ ngồi lì trên 3000.

### Những tệp không bao giờ bị đè

Bốn tệp làm việc của các xưởng (`.nmoxapi.json`, `.nmoxdb.json`, `.nmoxweb3.json`, `.nmoxinfra.json`) sẽ nạp lại khi bạn sửa chúng bên ngoài IDE — nhưng nếu bạn còn thay đổi chưa lưu thì bạn được hỏi, chứ không bị đè. Một tệp hỏng được để riêng thành `.bak` và báo cho bạn biết, không bao giờ bị thay lặng lẽ.

### TypeScript không cần dựng

Dự án có cửa vào là `index.ts`, `main.ts` hay `src/index.ts` chạy thẳng từ IGNITION bằng chính cơ chế bóc kiểu của Node (`--experimental-strip-types`, từ Node 22.6; mặc định từ 23.6 và 22.18 LTS). Lời từ chối của một Node cũ hơn được dịch thành câu gọi tên đúng cái ngưỡng ấy.

### Ngôn ngữ của bạn

NMOX Studio nói mười ba thứ tiếng: English, Español, Français, Deutsch, Русский, Українська, Polski, Português (Brasil), Bahasa Indonesia, Filipino, Tiếng Việt, 简体中文 và हिन्दी. Chọn tiếng của bạn ở **Tuỳ chọn ▸ Chung ▸ Ngôn ngữ** — mỗi thứ tiếng viết bằng chính tên nó, để bạn luôn tìm được tiếng mình. Lựa chọn ấy được ghi vào phần cài đặt khởi động của bạn (`etc/nmoxstudio.conf`, dưới dạng đối số `--locale`) và cũng có hiệu lực ngay. Thay đổi: trình đơn, hộp thoại, chú giải, thanh trạng thái, màn hình chào và phần tuỳ chọn. Giữ nguyên: từ vựng trên mặt các thiết bị của giá (GO, STOP, EXPLAIN — đó là nhãn máy, như trên một cây đàn tổng hợp), và những hộp thoại sâu hơn của nền tảng, vốn chưa có bản dịch.

### Việc dò bản mới mỗi ngày

Lặng lẽ, mỗi ngày một lần: nếu có bản mới hơn, một thông báo dẫn bạn tới trình quản lý mô-đun, ở thẻ cập nhật của nó, nơi trung tâm cập nhật cài các mô-đun mới ngay tại chỗ. Tắt nó ở Tuỳ chọn ▸ Chung.

<a id="11-learning-spaces"></a>
## 11. Không gian học

### Kiểm bài của bạn

Một số không gian có điểm kiểm: chọn lấy một, rồi **Tệp ▸ Kiểm bài của tôi** sẽ kiểm các bài tập thật sự — điều các tệp khẳng định được kiểm bằng Java thuần, kể cả các phép kiểm *vắng mặt*, thứ duy nhất chứng thực được «bạn đã đổi tiêu đề»: chữ gốc trong mẫu phải biến mất. Điều các lệnh khẳng định thì đi qua chính bộ công cụ của không gian ấy. Mỗi dấu ✗ đáp lại bằng gợi ý của chính không gian đó, và khi có chỗ hỏng, bản báo mời **Nhờ KVASIR giải thích…**: những điểm hỏng và, với phép kiểm tệp, chính tệp của bạn, có giới hạn và dưới một lời đồng ý nói rõ cái gì rời đi. Câu trả lời đọc như của một gia sư: đổi gì, rồi kiểm lại.

### Bài học của riêng bạn

Thả một tệp `*.json` vào `~/.nmox/learn-catalog.d/` là nó gia nhập bảng chọn, cùng một lược đồ với những bài có sẵn; một `slug` trùng sẽ thay thế bài của nhà. Bạn đang dạy? Hãy viết bằng cách dựng: biến bài tập thành một dự án bình thường, rồi **Tệp ▸ Xuất thành Không gian học…** sẽ soạn tệp ấy hộ bạn — các tệp mẫu, `TUTORIAL.md` của bạn, trình chạy, và các điểm kiểm của bạn — đã được thẩm lại bằng chính bộ phân tích của bảng chọn trước khi ghi, nên thứ bạn trao cho học trò đúng là thứ bảng chọn của họ sẽ nạp.

### Danh mục

*Không gian học mới…* mời bạn 93 bài có sẵn — ngôn ngữ, khung làm việc và thư viện. Mỗi bài sinh ra một dự án mẫu nhỏ, một bài hướng dẫn có dắt tay, và một giá đã gắn sẵn **một trình thông dịch thật**: bạn gõ vào giá và một trình thông dịch đang sống trả lời. Núm ENGINE chọn trong 37 trình thông dịch; thiếu cái nào thì nút INSTALL cài ngay tại chỗ, tiến độ hiện lên màn hình. Các không gian ở trong `~/.nmox/learn`, tách khỏi công việc thật của bạn.

### Những bước đầu, trên màn hình chào

Một cột thứ tư liệt kê sáu động tác đầu tiên — mở một dự án, chạy thứ gì đó trên giá, thấy một máy chủ sống dậy, hỏi KVASIR về mã, thử một không gian học, chĩa một tác nhân vào IDE — và đánh dấu từng cái dựa trên những ghi chép mà sản phẩm vốn đã giữ. Mỗi dòng là một cánh cửa: bấm vào là cửa sổ hay hành động ấy mở ra. Dấu đã đánh không bao giờ mất đi; cột ấy biến mất khi cả sáu đã xong, hoặc khi bạn bấm **Ẩn danh sách này**.

### Ba câu trả lời của trình đơn Trợ giúp

**Có gì mới…** đưa ra ghi chú của bản bạn đang chạy, gói ngay trong bản dựng; lần khởi động đầu sau một lần cập nhật, nó tự mở với những bản mà máy bạn chưa từng thấy. **Báo lỗi…** soạn một bản báo gồm môi trường của bạn và bốn mươi dòng nhật ký cuối, đã che bớt — thư mục nhà của bạn thành `~`, tên đăng nhập thành `<user>`, thứ gì trông như một bí mật thành `[redacted]` —; bạn sửa lại, rồi **Mở trên GitHub** điền sẵn một phiếu mà chính bạn gửi đi, hoặc bạn sao chép nó. Sản phẩm không bao giờ tự mình gửi gì cả. **Phím tắt…** liệt kê mọi phím tắt của NMOX trong hồ sơ đang dùng, đọc từ sơ đồ phím đang chạy, nên nó không thể lệch khỏi những gì trình đơn làm.

<a id="12-when-somethings-wrong"></a>
## 12. Khi có gì đó không ổn

### Bác sĩ môi trường

Trong trình đơn Công cụ, nó dò trực tiếp 66 công cụ bên ngoài — node, npm, docker, forge, composer, gopls… — và cho thấy bản đã tìm ra cùng câu lệnh cài đặt cho những thứ còn thiếu.

### Những bức tường có cửa

Thiếu một máy chủ ngôn ngữ hay một công cụ, IDE nói cho bạn biết phải chạy lệnh nào, hoặc đề nghị chạy hộ; không bao giờ là một lời từ chối trơ trọi. Một bức tường có cửa riêng: TypeScript 7 không mang theo tsserver, nên nếu TypeScript tìm được là bản 7, trình soạn thảo nói một lần rồi mời cài dòng 5 — cũng chính là dòng nó tự cài vì lẽ ấy. Nếu một cổng đã bị chiếm, thông báo lỗi gọi tên tiến trình đang ngồi đó, và SONAR đuổi kẻ ấy đi.

### Một nút GO chẳng làm gì

Hãy nhìn màn hình của nó: các thiết bị tự giải thích bằng lời, và chú giải trên nút GO cho thấy đúng câu lệnh nó sẽ chạy, để bạn có thể thử nó trong một cửa sổ dòng lệnh.

### Ứng dụng mở ra chẳng có gì (macOS)

Không cửa sổ, không lỗi, ở lần chạy đầu sau khi cài: đó là kiểm dịch của Gatekeeper — xem ghi chú ở chương 1. Bấm chuột phải rồi Mở, một lần thôi, là xong vĩnh viễn. Nhật ký nằm dưới `~/Library/Application Support/nmoxstudio/…/var/log/` nếu bạn cần mở một phiếu báo.

<a id="appendix-the-files-nmox-studio-writes-and-what-to-commit"></a>
## Phụ lục: những tệp NMOX Studio ghi ra (và tệp nào nên đưa vào kho)

Tất cả những gì IDE giữ lại về một dự án đều là tệp JSON đọc được nằm ở gốc dự án, làm ra để chia sẻ với đội của bạn.

| Tệp | Bên trong có gì | Đưa vào kho? |
|---|---|---|
| `.nmoxapi.json` | Bộ sưu tập, yêu cầu, môi trường và bài kiểm của Xưởng API | **Có** — đồng đội nhận được trọn bàn làm việc của bạn |
| `.nmoxdb.json` | Kết nối, truy vấn đã lưu và lịch sử | **Có** — mật khẩu *không bao giờ* nằm trong đó (chỉ ở chùm khoá) |
| `.nmoxweb3.json` | Mạng lưới và sổ địa chỉ của Xưởng hợp đồng | **Có** — địa chỉ bí mật *không bao giờ* nằm trong đó (chỉ ở chùm khoá) |
| `.nmoxinfra.json` | Khung vẽ hạ tầng: nút, dây nối, thuộc tính | **Có** — mã thông hành *không bao giờ* nằm trong đó (chỉ ở chùm khoá) |
| `.nmoxtasks.json` | Bảng công việc: cột, thẻ, hạn mức | **Có** — cả đội dùng chung một bảng; bỏ ra nếu muốn giữ riêng |
| `.gas-snapshot` | Mốc gas theo từng bài kiểm của Foundry (GOVERNOR canh nó) | **Có** — đó là cách bắt được gas đi lùi lúc duyệt mã |
| `.env` | Các biến môi trường của bạn | **Không** — đó chính là lý do `.env` tồn tại |
| `*.bak` | Một tệp làm việc không đọc nổi, được giữ lại cho bạn | Không — lấy ra thứ cần rồi xoá |

Sửa bất kỳ tệp nào trong bốn tệp `.nmox*.json` ở ngoài IDE, hoặc kéo về thay đổi của đồng đội, và xưởng tương ứng sẽ tự nạp lại — trừ khi bạn còn thay đổi chưa lưu ở đó, khi ấy nó hỏi trước.

Ngoài dự án: `~/NMOX` là bàn làm việc mặc định, các thí nghiệm ở trong `~/.nmox/experiments`, các không gian học ở `~/.nmox/learn`, còn trạng thái của chính IDE — cách bày cửa sổ, các bản vá của giá, các tuỳ chọn — nằm trong thư mục người dùng của nền tảng.
