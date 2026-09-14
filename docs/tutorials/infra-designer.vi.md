# Hướng dẫn: Trình thiết kế hạ tầng

<!-- languages -->
[English](infra-designer.md) · [Español](infra-designer.es.md) · [Français](infra-designer.fr.md) · [Deutsch](infra-designer.de.md) · [Русский](infra-designer.ru.md) · [Українська](infra-designer.uk.md) · [Polski](infra-designer.pl.md) · [Português (Brasil)](infra-designer.pt.md) · [Bahasa Indonesia](infra-designer.id.md) · [Filipino](infra-designer.tl.md) · **Tiếng Việt** · [简体中文](infra-designer.zh.md) · [हिन्दी](infra-designer.hi.md) · [עברית](infra-designer.he.md) · [العربية](infra-designer.ar.md)
<!-- /languages -->

Trình thiết kế hạ tầng là một khung vẽ theo phong cách Node-RED cho hạ tầng
đám mây. Bạn kéo các nút (droplet, tường lửa, bản ghi DNS…), nối chúng lại,
rồi triển khai lên DigitalOcean, Hetzner hoặc Cloudflare — với chi phí bày
ra trước khi bạn tiêu bất cứ đồng nào. Bài này dựng một kế hoạch và chạy thử
nó, nên không có tiền nào mất đi.

![Một chồng tài nguyên đang thành hình — DNS, bộ cân bằng tải, droplet và một ổ đĩa cùng bảng thuộc tính; thanh công cụ báo giá thiết kế theo thời gian thực và nói rõ đang ở chế độ chạy thử](../images/infra-designer.png)

## Mở nó

`⌥⌘9`, hoặc thẻ **Trình thiết kế hạ tầng**.

## Các bước

1. **Thả một máy chủ.** Kéo một nút **Droplet** từ bảng chọn lên khung vẽ.
   Bảng thuộc tính bên phải cho bạn đặt vùng, kích cỡ và image. Ước tính chi
   phí cập nhật ngay khi bạn chọn.

2. **Thêm tường lửa.** Kéo một nút **Tường lửa** và nối nó với droplet bằng
   cách kéo giữa các cổng của chúng. Đặt một quy tắc chiều vào (ví dụ cho
   phép 22 và 443).

3. **Thêm cloud-init (tùy chọn).** Ở trường `user_data` của droplet, dán một
   đoạn script cloud-init ngắn — nó chạy ở lần khởi động đầu tiên.

4. **Chạy thử việc triển khai.** Nhấn nút đỏ **TRIỂN KHAI**. Khi chưa có
   token đám mây, mọi thứ vẫn chỉ là **chạy thử**: bạn thấy đúng kế hoạch gọi
   API theo thứ tự (tạo tường lửa, tạo droplet, gắn…) cùng chi phí, nhưng
   không có gì được tạo ra. Nhật ký triển khai hiển thị từng bước.

5. **Chạy thật (khi bạn sẵn sàng).** Thêm token của nhà cung cấp trong
   `Tùy chọn` (lưu trong chùm khóa của hệ điều hành), và TRIỂN KHAI sẽ thực
   hiện kế hoạch thật, giải các tham chiếu chéo giữa các nút (IP của droplet
   chảy vào bản ghi DNS) khi tài nguyên lần lượt được tạo.

## Bạn vừa học được gì

- Khung vẽ là một đồ thị phụ thuộc thật; bộ lập kế hoạch sắp thứ tự các lời
  gọi API và chuyển id/IP giữa các bước.
- Các hộp thoại phá hủy (Destroy Stack/Resource, Deploy) đặt phím Enter mặc
  định vào nút **an toàn** — một cú nhấn theo phản xạ không thể xóa một tài
  nguyên đang tính tiền.
- Tài nguyên đang chạy có thể được **đồng bộ** ngược về và làm mới để thấy
  chỗ lệch; kế hoạch được lưu trong `.nmoxinfra.json`.

## Tiếp theo

- Sao chép lệnh SSH của một nút ngay từ khung vẽ.
- Đa đám mây: cùng một khung vẽ điều khiển DO, Hetzner và Cloudflare.
