# Agent Port (MCP)

<!-- languages -->
[English](agent-port.md) · [Español](agent-port.es.md) · [Français](agent-port.fr.md) · [Deutsch](agent-port.de.md) · [Русский](agent-port.ru.md) · [Українська](agent-port.uk.md) · [Polski](agent-port.pl.md) · [Português (Brasil)](agent-port.pt.md) · [Bahasa Indonesia](agent-port.id.md) · [Filipino](agent-port.tl.md) · **Tiếng Việt** · [简体中文](agent-port.zh.md) · [हिन्दी](agent-port.hi.md) · [עברית](agent-port.he.md) · [العربية](agent-port.ar.md)
<!-- /languages -->

*Hướng một tác nhân AI vào IDE của bạn — và để nó ĐỌC, không bao giờ chạy.*

![Hộp thoại Agent Port — điểm cuối loopback, token cấp cho mỗi lần khởi động (trong ảnh này là một giá trị giữ chỗ), và cấu hình máy khách soạn sẵn để sao chép](../images/tabs/agent-port.png)

NMOX Studio có sẵn một máy chủ Model Context Protocol. Bất kỳ tác nhân nào
nói MCP (Claude Code, một trợ lý trong trình soạn thảo, script của riêng
bạn) đều có thể kết nối và hỏi IDE những gì nó biết: dự án nào đang được
nhắm tới, cái gì đang phục vụ, cái gì đang chạy, bạn đang sửa gì, một cái
tên được khai báo ở đâu, lần gần nhất cái gì hỏng. Nó **chỉ đọc theo thiết
kế**: bản dựng sẽ thất bại nếu bất kỳ lớp nào trong gói Agent Port dù chỉ
gọi tên một cách để khởi chạy tiến trình, ghi tệp hay dừng một lần chạy.

## 1. Khởi động

**Làm:** Công cụ ▸ **Agent Port (MCP)…** ▸ **Khởi động**, rồi **Sao chép
cấu hình**.

**Thấy:** Một hộp thoại với điểm cuối (chỉ loopback, một cổng mới), một
bearer token riêng cho mỗi lần khởi động, và một cấu hình máy khách soạn
sẵn:

```json
{
  "mcpServers": {
    "nmox-studio": {
      "type": "http",
      "url": "http://127.0.0.1:PORT/mcp",
      "headers": { "Authorization": "Bearer TOKEN" }
    }
  }
}
```

Dán nó vào `.mcp.json` của tác nhân. Token chỉ tồn tại trong hộp thoại đó —
không bao giờ được ghi vào nhật ký hay lưu lại — và mất đi cùng với cổng.
**Dừng Agent Port** kết thúc nó; thoát IDE cũng vậy. Trong lúc nó lắng nghe,
thanh trạng thái hiện **⌁ agent port :N** — một cổng đọc được IDE của bạn
không bao giờ được vô hình; chú giải của dấu này đếm số tác nhân đang nhận
luồng, và một cú nhấp mở lại hộp thoại (cấu hình, hoặc Dừng).

## 2. Các công cụ

Mỗi công cụ trả lời bằng một đoạn văn cho người đọc VÀ một
`structuredContent` có kiểu theo một `outputSchema` đã khai báo (bản dựng
kiểm schema đối chiếu với kết quả thật), và được chú thích
`readOnlyHint: true`.

| Công cụ | Nó trả lời gì | Đối số |
|------|-----------------|-----------|
| `ide_context` | Toàn bộ ảnh chụp định hướng trong một lần gọi: dự án, bộ công cụ, máy chủ, lần chạy, tệp đang sửa, lần hỏng gần nhất, số lượng chẩn đoán | — |
| `project_state` | Dự án đang được nhắm tới: tên, thư mục, nhánh git, loại đã nhận ra, trình quản lý gói Node | — |
| `run_history` | Các lần khởi chạy và kết thúc trong bộ ghi chuyến bay, mới nhất trước, mỗi lần kết thúc kèm lệnh, mã và thời lượng; một lần chạy do chính bạn dừng ghi là `stopped`, không bao giờ là `failed` | `limit` |
| `live_servers` | Mọi máy chủ phát triển mà IDE biết đang phục vụ, kèm URL | — |
| `live_runs` | Mọi lệnh đang chạy ngay lúc này (thứ mà nút ■ trên thanh công cụ sẽ dừng), kèm thời điểm bắt đầu | — |
| `last_failure` | Lần chạy hỏng gần nhất: thiết bị, lệnh, mã thoát, tối đa năm dòng lỗi | — |
| `diagnostics` | Những gì các trình soi lỗi và trình kiểm tra đang báo | `file` (lọc theo chuỗi con) |
| `find_symbol` | Một cái tên được khai báo ở đâu — cùng chỉ mục với Đi tới ký hiệu (⌥⇧⌘O) | `query`, `limit` |
| `outline` | Cấu trúc của một tệp — chính các mục của Bộ điều hướng | `file` |
| `search_text` | Các dòng chứa một chuỗi nguyên văn, không phân biệt hoa thường, có giới hạn và báo mọi lần chạm trần; tệp `.env`, tệp rc của trình quản lý gói và khóa riêng không bao giờ được tìm | `query`, `limit` |
| `editor_state` | Tệp đang sửa (thẻ soạn thảo có tiêu điểm, nếu không thì thẻ đang hiện trong vùng soạn thảo) và mọi thẻ đang mở, thẻ chưa lưu được đánh dấu | — |
| `rack_devices` | Các thiết bị gắn trên giá tác vụ, theo thứ tự | — |

Mọi danh sách đều có giới hạn và nói rõ điều đó: `find_symbol` và `outline`
báo khi chỉ mục chưa đầy đủ, `search_text` báo `truncated` chỉ khi còn kết
quả khớp nữa, `run_history` báo khi các sự kiện cũ hơn bị bỏ ra.

## 3. Tài nguyên, lời nhắc và luồng

Cùng những câu trả lời ấy có thể duyệt dưới dạng tài nguyên mà một tác nhân
đính kèm làm ngữ cảnh — `nmox://context`, `nmox://project`, `nmox://history`,
`nmox://servers`, `nmox://runs`, `nmox://editor`,
`nmox://last-failure`, `nmox://diagnostics`, `nmox://devices` — cộng thêm
hai mẫu cho các công cụ nhận đối số:
`nmox://outline/{file}` và `nmox://search/{query}` (mã hóa phần trăm).
Văn bản của một tài nguyên chính là JSON có cấu trúc của công cụ tương ứng,
giống từng byte.

Một tác nhân muốn được báo hơn là phải hỏi lại có thể **đăng ký theo dõi**:
`resources/subscribe` trên bất kỳ URI nào kể trên, và luồng GET của cổng
(kênh từ máy chủ tới máy khách của Streamable HTTP, `Accept:
text/event-stream`, cùng token, không có `Origin`) mang một khung
`notifications/resources/updated` ngay khi thứ phía sau nó thay đổi — một
lần chạy bắt đầu thì `nmox://runs` được báo, một máy chủ bắt đầu phục vụ thì
`nmox://servers`, một trình soi lỗi báo cáo thì `nmox://diagnostics`, một
thẻ đổi hay một tệp được lưu thì `nmox://editor`; `nmox://context` đi theo
tất cả. Khung chỉ nêu URI và không gì khác; tác nhân tự đọc lại thứ nó quan
tâm. Một dàn ý mà tác nhân đã đính kèm cũng đi theo tệp của nó: đăng ký
`nmox://outline/src/app.ts` và cổng sẽ báo URI đó khi tệp thay đổi trên đĩa
(một lần lưu, một lần định dạng, một trình sinh mã), và thêm một lần nữa nếu
tệp biến mất — phải là một tệp thông thường bên trong dự án đang nhắm tới,
tối đa ba mươi hai tệp, thăm dò mỗi hai giây; một đường dẫn nằm ngoài dự án
nhận `-32002`, không bao giờ được đọc.

Ba lời nhắc gộp trạng thái đang diễn ra vào một câu hỏi: `diagnose_failure`
(lần hỏng gần nhất), `review_setup` (toàn bộ ngữ cảnh), và `where_is`
— lời nhắc duy nhất nhận một đối số, `name` — gộp các kết quả ký hiệu cho
cái tên đó.

Một tác nhân đang điền đối số đó, hay `{file}` của mẫu dàn ý, có thể hỏi
trước: `completion/complete` (nguyên thủy thứ tư của đặc tả) trả lời `name`
của `where_is` từ chỉ mục ký hiệu (cùng các kết quả mà `find_symbol` trả về,
không trùng lặp, khớp tiền tố trước) và `{file}` từ chính các tệp của dự án
(khớp tiền tố, rồi tới chứa chuỗi; danh sách bỏ qua của lượt tìm kiếm vẫn áp
dụng, nên `node_modules` không bao giờ được gợi ý) — tối đa 100 giá trị,
`hasMore` khi mức trần đã cắt bớt, và `total` chỉ khi con số là chính xác
(danh sách tệp thì luôn chính xác; vượt trần thì chỉ mục ký hiệu chỉ trả lời
được một mức sàn, nên thà không đưa con số nào còn hơn đưa một con số sai).
Chuỗi nguyên văn của mẫu tìm kiếm có thể là bất cứ gì, nên nó không gợi ý gì
cả; một tên lời nhắc, mẫu hay đối số không xác định bị từ chối với `-32602`.

Cùng luồng đó mang các **thông điệp nhật ký**: mỗi dòng mà mỗi lần chạy in ra
tới dưới dạng `notifications/message` với lần chạy làm `logger` — vòng đời ở
mức `info` (`$ npm run build`, `[exit 0]`, `[exit 143]
stopped`; một lần thoát lỗi ở mức `error`), stderr ở `warning`, kết quả thông
thường ở `debug`. Mức ban đầu là `info`, nên tác nhân nghe thấy các lần chạy
bắt đầu và kết thúc và không gì khác cho tới khi nó yêu cầu:
`logging/setLevel` với `debug` mở toàn bộ dòng chảy. Một bản dựng in nhanh
hơn tốc độ máy khách đọc không bao giờ làm bộ nhớ của cổng phình lên — quá
một nghìn dòng chưa ghi, phần tràn được đếm và báo thành một dòng `warning`,
không bao giờ mất đi lặng lẽ. Một mức mà đặc tả không đặt tên bị từ chối với
`-32602`.

## 4. Tự tay đi một lượt

Với token nằm trong một biến shell (không bao giờ trên một dòng lệnh mà bạn
có thể dán ở đâu đó):

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"find_symbol","arguments":{"query":"checkout"}}}'
```

**Thấy:** `checkout (function) — src/cart.js:12`, và cùng nội dung đó trong
`structuredContent.hits[0]`.

Luồng, bằng tay: mở nó trong một shell và đăng ký từ một shell khác —

```bash
curl -N -s "$URL" -H "Authorization: Bearer $TOKEN" -H "Accept: text/event-stream"
```

```bash
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":2,"method":"resources/subscribe","params":{"uri":"nmox://runs"}}'
curl -s -X POST "$URL" -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" -H "Accept: application/json" \
  -d '{"jsonrpc":"2.0","id":3,"method":"logging/setLevel","params":{"level":"debug"}}'
```

**Thấy:** `: connected`, rồi `: keepalive` mỗi mười lăm giây; nhấn ▶ và shell
thứ nhất in `notifications/resources/updated` cho `nmox://runs` cùng mọi
dòng lần chạy in ra dưới dạng `notifications/message` (`$ npm run dev` ở
`info`, kết quả ở `debug`); nhấn ■ và `[exit 143] stopped` tới ở mức `info`.

Cùng lượt đi đó với **máy khách chính thức**, mọi nguyên thủy cùng lúc, có
sẵn trong kho: `scripts/agent-port-walk.mjs` (phần đầu tệp nói cách cài
`@modelcontextprotocol/sdk` trong một thư mục nháp và đặt URL cùng token ở
đâu — biến shell, không bao giờ trên dòng lệnh). Nó in mỗi bước một dòng và
kết thúc bằng WALK CLEAN hoặc lấy số điều bất ngờ làm mã thoát (một bước từ
chối thì tính một CÂU TRẢ LỜI là điều bất ngờ), để một job CI có thể đọc
được; nhấn ▶ và ■ trong IDE trong lúc nó lắng nghe và các thông điệp nhật ký
sẽ tới.

## 5. Lời từ chối là tính năng

| Bạn làm | Cổng nói |
|--------|---------------|
| Gọi mà không có token, hoặc với token đã cũ | `401` — không gì khác, kể cả danh sách công cụ |
| Gọi từ một trang trong trình duyệt (bất kỳ `Origin` nào) | `403` |
| Một `GET` thông thường | `405` — cổng không phải là một trang; chỉ `GET` SSE (với `Accept: text/event-stream`) được phục vụ, làm luồng đăng ký |
| Đăng ký `nmox://nonesuch`, hoặc một dàn ý nằm ngoài dự án | JSON-RPC `-32002` (không tìm thấy tài nguyên) |
| Đăng ký dàn ý thứ ba mươi ba | `-32602`, nêu rõ mức trần |
| Đọc `nmox://nonesuch` | JSON-RPC `-32002` (không tìm thấy tài nguyên) |
| Hỏi `where_is` mà không có `name` | `-32602`, nêu tên đối số còn thiếu |
| Xin một tệp nằm ngoài dự án (`../../.zshrc`) | `outline` từ chối — *nằm ngoài dự án đang nhắm tới* — và không bao giờ đọc nó |
| Tìm một giá trị nằm trong `.env` (hoặc `app.env`), `.npmrc`, `.htpasswd`, `secrets.yaml`, `credentials.json`, hay một `.pem` — hoặc xin dàn ý của chúng | không gì cả — các tệp đó không bao giờ được tìm, không bao giờ được đếm, không bao giờ được gợi ý, và `outline` từ chối chúng theo tên; luật env của chính IDE (tên của khóa, không bao giờ là giá trị) cũng áp dụng cho tác nhân |
| Đặt mức nhật ký thành `loud` | `-32602`, nêu tên tám mức |
| Yêu cầu nó chạy, ghi hay dừng bất cứ thứ gì | không có công cụ nào như vậy; bài kiểm tra sổ cái giữ cho điều đó luôn đúng |

Dòng cuối đó chính là thiết kế. Một tác nhân chạy được máy chủ của bạn thì
cũng dừng được nó, và một tác nhân ghi được thì cũng xóa được; Agent Port vẫn
là một cách để HỎI. Nếu một phiên bản tương lai thêm khả năng thực thi, nó
sẽ đến cùng thiết kế đồng thuận riêng, như luồng dữ liệu đi ra ngoài của
KVASIR đã từng.

Xem thêm: trạm 24 của Kitchen Sink và đoạn về Agent Port trong hướng dẫn sử
dụng.
