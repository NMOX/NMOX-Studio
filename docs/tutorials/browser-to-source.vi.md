# Từ Trình duyệt tới mã nguồn: chọn, nhảy, đổi kiểu

<!-- languages -->
[English](browser-to-source.md) · [Español](browser-to-source.es.md) · [Français](browser-to-source.fr.md) · [Deutsch](browser-to-source.de.md) · [Русский](browser-to-source.ru.md) · [Українська](browser-to-source.uk.md) · [Polski](browser-to-source.pl.md) · [Português (Brasil)](browser-to-source.pt.md) · [Bahasa Indonesia](browser-to-source.id.md) · [Filipino](browser-to-source.tl.md) · **Tiếng Việt** · [简体中文](browser-to-source.zh.md) · [हिन्दी](browser-to-source.hi.md) · [עברית](browser-to-source.he.md) · [العربية](browser-to-source.ar.md)
<!-- /languages -->

*Một buổi ngồi. Bạn sẽ nhấp một phần tử trong Trình duyệt tích hợp, đến
ngay tệp đã sinh ra nó, đổi kiểu của nó từ DevTools, và xem thay đổi đáp
xuống tệp biểu định kiểu của bạn — không phải gõ lại gì cả.*

Sự chia cắt lâu đời nhất trong phát triển web là trình duyệt và trình soạn
thảo biết những điều khác nhau: trình duyệt biết *bạn đang nói tới phần tử
nào*, trình soạn thảo biết *mã nằm ở đâu*, còn bạn thì tự tay mang thông
tin qua lại giữa hai bên. Trình duyệt của NMOX Studio khép lại sự chia cắt
ấy. Bài hướng dẫn này đi trọn vòng trên một trang mà bạn sẽ tạo trong hai
phút.

![Khung DOM của DevTools với một h1 được chọn trong trang: Chọn phần tử, Mở mã nguồn và Chỉnh sửa kiểu… bên cạnh cây trực tiếp](../images/story-06-devtools-pick.png)

## 1. Tạo một trang

Tạo một thư mục với hai tệp (lệnh Tệp mới của Studio dự án dùng được, hoặc
bất cứ cách nào bạn thích):

`index.html`

```html
<!doctype html>
<html>
<head>
    <title>Loop Demo</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <header class="hero">
        <h1 id="headline">Hello, loop</h1>
        <p class="tagline">watch this paragraph change color</p>
    </header>
</body>
</html>
```

`style.css`

```css
.hero {
    background: #222;
    color: white;
    padding: 2rem;
}

.tagline {
    color: gray;
    font-style: italic;
}
```

## 2. Mở nó trong Trình duyệt

Mở thẻ **Trình duyệt** (⌥⌘4), gõ đường dẫn của tệp vào thanh địa chỉ dưới
dạng URL `file://` — ví dụ
`file:///Users/you/NMOX/loopdemo/index.html` — rồi nhấn Return.

> Một trang được phục vụ bởi một thiết bị phục vụ trên giá (IGNITION,
> VELOCITY, HALO và các bạn của chúng) hoạt động y hệt — Trình duyệt biết
> một lần phục vụ đang chạy thuộc về dự án nào. Thứ **không** hoạt động là
> một trang web từ xa: vòng lặp chỉ tin những trang mà nó lần ra được tới
> tệp trên đĩa của bạn, và nó sẽ nói thẳng như vậy thay vì đoán.

Nhấp **DevTools** trên thanh công cụ của Trình duyệt rồi chọn thẻ **DOM**.

## 3. Chọn một phần tử trong trang

Nhấp **Chọn phần tử**. Con trỏ trên trang biến thành hình chữ thập. Giờ
nhấp vào dòng tiêu đề ngay trên trang.

Ba điều xảy ra cùng lúc: cú nhấp bị nuốt (không có điều hướng nào), cây DOM
chọn `h1#headline`, và một đường viền xanh dương bao quanh phần tử trong
trang. Khung chi tiết hiện đầy các thuộc tính và kiểu đã tính của nó — kể cả
phán quyết độ tương phản WCAG khi biết được cả hai màu.

## 4. Nhảy tới mã nguồn

Khi phần tử đang được chọn, nhấp **Mở mã nguồn** (nhấp đúp vào nút trên cây
cũng vậy). Trình soạn thảo mở `index.html` với con trỏ nằm đúng dòng đã sinh
ra phần tử.

Cách nó tìm ra dòng, và khi nào nó từ chối:

- Một phần tử **có id** được tìm theo id đó — id là duy nhất, nên kết quả
  chính xác.
- Một phần tử **không có id** được tìm như lần xuất hiện thứ N của thẻ ấy
  theo thứ tự tài liệu, bỏ qua chú thích và phần thân `<script>`/`<style>`
  (một `<div>` nằm trong chú thích hay trong một chuỗi JS không phải là phần
  tử).
- Một phần tử **chỉ tồn tại vì một script đã tạo ra nó** thì hoàn toàn không
  có trong mã nguồn của bạn — thanh trạng thái báo “có thể do script tạo ra”
  thay vì nhảy tới một chỗ sai.
- Một trang không dựa trên tệp cục bộ — một trang từ xa, một máy chủ phát
  triển lạ — sẽ từ chối với “không được phục vụ từ dự án nào ở đây”.

Lời từ chối chính là mấu chốt: một cú nhảy có thể sai còn tệ hơn không nhảy
chút nào.

## 5. Đổi kiểu — và xem mã nguồn thay đổi

Chọn dòng giới thiệu (`p.tagline`) — chọn nó trên trang hoặc nhấp nó trên
cây — rồi nhấn **Chỉnh sửa kiểu…**. Trong hộp thoại, chọn thuộc tính
`color`, gõ giá trị `tomato`, rồi nhấn OK.

Hai điều xảy ra, theo thứ tự:

1. **Trang vẽ lại ngay lập tức.** Thay đổi được áp dụng nội tuyến trước,
   nên bạn luôn thấy đúng thứ mình yêu cầu.
2. **Tệp biểu định kiểu gốc thay đổi.** Thanh trạng thái báo
   `Đã lưu vào style.css (.tagline)` — mở `style.css` ra và
   `color: gray;` đã thành `color: tomato;`, ngay tại chỗ, mọi byte khác
   đều không bị đụng tới.

Quy tắc cần sửa được chọn bằng cách hỏi chính *trang* xem những quy tắc
biểu định kiểu nào đã khớp với phần tử — câu trả lời của chính cơ chế
cascade, quy tắc khớp sau cùng thắng — nên việc ghi đáp đúng vào quy tắc
đang thực sự tạo kiểu cho thứ bạn thấy, kể cả khi cùng một bộ chọn xuất hiện
hai lần trong một tệp.

## 6. Những giới hạn thẳng thắn

Chỉnh sửa kiểu… từ chối, kèm lý do trên thanh trạng thái, bất cứ khi nào
việc ghi sẽ là đoán mò hoặc sẽ phá hỏng công sức. Bản xem trước nội tuyến
vẫn được áp dụng trong mọi trường hợp — bạn thấy thay đổi; thông báo cho bạn
biết vì sao nó không được lưu.

| Tình huống | Nó nói gì |
|-----------|--------------|
| Quy tắc nằm trong một khối `<style>` nội tuyến | “quy tắc nằm trong `<style>` nội tuyến, không phải trong tệp biểu định kiểu” |
| Tệp biểu định kiểu ở xa hoặc đến từ một máy chủ lạ | “không được phục vụ từ dự án nào ở đây” |
| Tệp `.css` có một tệp anh em `.scss`/`.less`/`.sass` | “là kết quả biên dịch — hãy sửa mã nguồn của bộ tiền xử lý” (ghi vào đây sẽ mất ở lần biên dịch tới) |
| Tệp có thay đổi chưa lưu trong một trình soạn thảo | “có thay đổi chưa lưu trong trình soạn thảo — hãy lưu trước” |
| Không có quy tắc biểu định kiểu nào khớp với phần tử | “Chỉ áp dụng trong trang — không có quy tắc biểu định kiểu nào khớp với phần tử này” |

## 7. Khép vòng

Nếu trang đang được một thiết bị trên giá phục vụ, bạn thậm chí không cần
tải lại: tính năng lưu là tải lại của Trình duyệt theo dõi việc lưu các tệp
web và tự làm mới các trang cục bộ. Chọn → chỉnh → mã nguồn được cập nhật →
trang tải lại từ chính mã nguồn đó. Trình duyệt và trình soạn thảo, chung
một bề mặt.
