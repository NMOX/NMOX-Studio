# Hướng dẫn: Studio hợp đồng (Web3)

<!-- languages -->
[English](contract-studio.md) · [Español](contract-studio.es.md) · [Français](contract-studio.fr.md) · [Deutsch](contract-studio.de.md) · [Русский](contract-studio.ru.md) · [Українська](contract-studio.uk.md) · [Polski](contract-studio.pl.md) · [Português (Brasil)](contract-studio.pt.md) · [Bahasa Indonesia](contract-studio.id.md) · [Filipino](contract-studio.tl.md) · **Tiếng Việt** · [简体中文](contract-studio.zh.md) · [हिन्दी](contract-studio.hi.md) · [עברית](contract-studio.he.md) · [العربية](contract-studio.ar.md)
<!-- /languages -->

Studio hợp đồng là một bàn làm việc đầy đủ cho hợp đồng thông minh: cây tạo
phẩm Foundry/Hardhat, tương tác do ABI dẫn đường với giá trị trả về và các
lần revert đã giải mã, trình theo dõi khối và sự kiện đang sống, cùng khung
giám sát gas và kích thước — với một luật cứng: **không bao giờ có khóa riêng
nào chạm tới IDE**.

Đây là chuyến tham quan nhanh. Muốn một ví dụ làm trọn vẹn — viết một hợp
đồng ký quỹ, kiểm thử nó, và chạy nó trên một chuỗi cục bộ — xem
[making-a-smart-contract.md](../making-a-smart-contract.md).

![ANVIL đang chạy trên giá và Studio hợp đồng tự kết nối tới nó — chuỗi 31337, hợp đồng trong cây tạo phẩm cùng mức dùng kích thước EIP-170](../images/contract-studio.png)

## Mở nó

`⌥⌘6`, hoặc thẻ **Studio hợp đồng**. Bạn sẽ cần Foundry (`anvil`, `forge`)
đã cài; kiểm tra bằng `Công cụ ▸ Trình chẩn đoán môi trường…`.

## Các bước

1. **Khởi động một chuỗi cục bộ.** Trên giá, gắn **ANVIL** và nhấn GO — nó
   chạy một mạng thử EVM cục bộ với các tài khoản đã mở khóa, nạp sẵn tiền.
   Studio hợp đồng tự kết nối tới nó.

2. **Dựng tạo phẩm.** Trong một dự án Foundry, chạy `forge build` (thiết bị
   **FORGE**, hoặc Dựng của IDE). Cây tạo phẩm của Studio hợp đồng đầy dần
   những hợp đồng đã biên dịch.

3. **Triển khai và tương tác.** Chọn một hợp đồng, nhấn **Triển khai** (nó
   dùng một tài khoản anvil đã mở khóa — không phải nhập khóa), rồi dùng khung
   **Tương tác**: `CALL` một hàm view và xem giá trị trả về đã giải mã; `SEND`
   một giao dịch và theo dõi biên nhận. Revert và lỗi tùy biến được giải mã
   thành chữ đọc được.

4. **Theo dõi chuỗi.** Khung **Theo dõi** thăm dò khối mới vài giây một lần và
   giải mã nhật ký sự kiện theo ABI của bạn. Khung **Giám sát** hiển thị bảng
   gas, phán quyết kích thước EIP-170 và một sổ địa chỉ triển khai.

## Bạn vừa học được gì

- Việc gửi đi dùng **các tài khoản đã mở khóa** của mạng thử — IDE không giữ
  chút khóa nào và không có mã ký.
- Địa chỉ RPC bí mật chỉ nằm trong chùm khóa và không bao giờ bị ghi ra tệp.
- Một lời xác nhận canh mọi lần gửi tới điểm cuối **không phải loopback**,
  nên bạn không thể lỡ tay phát lên một chuỗi thật.

## Tiếp theo

- Trọn bài ký quỹ:
  [making-a-smart-contract.md](../making-a-smart-contract.md).
- GOVERNOR (cổng gas) và mẫu sẵn Bàn thử Web3 nằm trên giá.
