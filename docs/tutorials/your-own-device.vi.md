# Hướng dẫn: tự viết một thiết bị cho giá

<!-- languages -->
[English](your-own-device.md) · [Español](your-own-device.es.md) · [Français](your-own-device.fr.md) · [Deutsch](your-own-device.de.md) · [Русский](your-own-device.ru.md) · [Українська](your-own-device.uk.md) · [Polski](your-own-device.pl.md) · [Português (Brasil)](your-own-device.pt.md) · [Bahasa Indonesia](your-own-device.id.md) · [Filipino](your-own-device.tl.md) · **Tiếng Việt** · [简体中文](your-own-device.zh.md) · [हिन्दी](your-own-device.hi.md) · [עברית](your-own-device.he.md) · [العربية](your-own-device.ar.md)
<!-- /languages -->

*Một buổi ngồi là xong. Bạn sẽ thêm một thiết bị vào giá chỉ bằng một
trình soạn thảo văn bản, nhấn nút của nó, xem nó chạy một lệnh thật, rồi
nối kết quả của nó vào MONITOR — không cần viết một dòng Java nào.*

Mới từ 2.0.0. Giá ra đời với năm mươi ba thiết bị và, cho tới giờ, chỉ có
một cách thêm thiết bị thứ năm mươi tư: viết một trình cắm NetBeans. Đây
là cách còn lại.

![Giá tác vụ: kệ thiết bị bên trái là nơi một thiết bị từ ~/.nmox/devices.d xuất hiện, bên cạnh các thiết bị có sẵn](../images/tabs/the-task-rack.png)

## 1. Tạo thư mục

```bash
mkdir -p ~/.nmox/devices.d
```

Đó là toàn bộ bước cài đặt. Giá chỉ đọc thư mục này khi cần, nên không có
gì phải khởi động lại.

## 2. Viết thiết bị

Đặt nội dung sau vào `~/.nmox/devices.d/counter.json`:

```json
{
  "id": "com.example.counter",
  "title": "COUNTER",
  "tagline": "counts the files in the project",
  "accent": "#7FB3D5",
  "category": "OBSERVE",
  "usage": "COUNT lists the project's files of the dialled KIND and shows how many.\nPatch OUT into MONITOR to read the list, or DONE onward to chain.",
  "knobs": [
    { "key": "kind", "label": "KIND", "options": ["js", "ts", "css", "md"] }
  ],
  "ports": [
    { "id": "count", "label": "COUNT", "direction": "IN", "signal": "TRIGGER" },
    { "id": "done", "label": "DONE", "direction": "OUT", "signal": "TRIGGER" },
    { "id": "out", "label": "OUT", "direction": "OUT", "signal": "DATA" }
  ],
  "buttons": [
    { "label": "COUNT", "role": "QUERY",
      "command": ["git", "ls-files", "*.{{kind}}"],
      "emit": "done", "trigger": "count" }
  ]
}
```

Mọi thứ trong đó đều có việc của nó: **núm** trở thành `{{kind}}` trong
lệnh, vai trò **QUERY** tô nút màu xanh dương (luật màu: xanh dương hỏi,
xanh lá làm, đỏ dừng), còn ba cổng giúp thiết bị nối dây được.

## 3. Gắn nó lên giá

Mở **Giá tác vụ** (`⌘9`, hoặc thẻ Giá tác vụ) và tìm trong ngăn **Quan sát**
của kệ. COUNTER nằm ở đó, dòng giới thiệu của bạn ngay bên dưới. Kéo nó
lên một thanh ray.

Nhấp chuột phải vào nó và chọn **Cách dùng COUNTER…** — đó chính là đoạn `usage` của bạn, và
cũng là lý do định dạng này đòi đủ hai dòng thật.

## 4. Nhấn nó

> Để ý là không có dòng `units`: kệ đo mặt máy và chọn chiều cao nhỏ
> nhất vừa khít (thiết bị này cần 2U cho cái núm). Chỉ khai báo `units`
> khi bạn muốn thêm chỗ.

Hướng giá vào một dự án git, vặn **KIND** sang `js`, rồi nhấn **COUNT**.

Lần nhấn đầu tiên bật lên lời hỏi **Tin cậy không gian làm việc**, vì một
tệp thiết bị chạy lệnh thật và chủ nhà canh mọi lần khởi chạy theo đúng
cách nó canh một thiết bị có sẵn. Cho phép, và màn hình LCD hiện lệnh, rồi
tới dòng kết quả cuối cùng. Giắc DONE nháy xanh lá.

Còn nếu từ chối thì không có gì được khởi chạy — lời từ chối chính là tính
năng.

## 5. Nối dây

Kéo một sợi dây từ **OUT** của COUNTER tới **IN** của MONITOR. Nhấn COUNT
lần nữa: mọi dòng đều hiện lên màn hình, vì một cổng `OUT`/`DATA` đã khai
báo sẽ nhận kết quả của lần chạy mà không cần cấu hình thêm.

Giờ kéo từ nhịp tick của TEMPO vào đầu vào **COUNT** của COUNTER. Thiết bị
bạn viết bằng trình soạn thảo văn bản giờ đã chạy theo đồng hồ.

## 6. Cố tình làm hỏng nó

Sửa tệp và đổi lệnh thành một thứ có dấu ống:

```json
"command": ["sh", "-c", "git ls-files | wc -l"]
```

Lưu lại, và COUNTER *biến mất* khỏi kệ. Đó là định dạng từ chối một dòng
shell: một lệnh là một mảng argv, để người đọc — chính bạn sáu tháng sau,
hay một đồng nghiệp đang duyệt tệp — thấy đúng thứ gì sẽ chạy. Nhật ký của
IDE cho biết tệp nào bị bỏ qua và vì sao:

```
device file counter.json skipped: button "COUNT" command token
"git ls-files | wc -l" contains "|" — commands are argv, never a shell line
```

Đưa dạng mảng trở lại là nó quay về. Điều tương tự cũng đúng với một công
cụ gọi theo đường dẫn (`./x.sh`), một `{{variable}}` không xác định, hay một
`usage` chỉ có một dòng: tệp bị bỏ qua trọn vẹn thay vì được nạp một nửa,
vì một thiết bị có nhãn nói dối còn tệ hơn là không có thiết bị nào.

## Bạn vừa học được gì

- Một thiết bị là một **tệp**: `~/.nmox/devices.d/*.json`, đọc khi cần,
  không khởi động lại, không phải dựng.
- Núm trở thành `{{variables}}`; vai trò quyết định màu; cổng giúp thiết bị
  nối dây được và kết quả của nó đọc được.
- **Chủ nhà giữ các luật** — tin cậy không gian làm việc ở mọi lần khởi
  chạy, luật màu, bộ từ vựng cổng, luật của kệ — nên một tệp thiết bị không
  thể diễn đạt một lệnh không qua kiểm soát hay một nút GO màu đỏ, dù có cố.
- Lời từ chối thì rõ ràng trong nhật ký và triệt để trong hiệu lực.

## Tiếp theo

- [device-files.md](../device-files.md) — tài liệu tham khảo đầy đủ
- [Giá tác vụ](the-task-rack.vi.md) — nối dây, cổng ENABLE và bộ dựng sẵn
- [device-spi.md](../device-spi.md) — SPI Java, cho những thiết bị cần
  trạng thái thật: tự vẽ, thăm dò định kỳ, kết nối lâu dài
