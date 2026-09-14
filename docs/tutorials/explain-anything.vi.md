# Hướng dẫn: Giải thích mọi thứ với KVASIR

<!-- languages -->
[English](explain-anything.md) · [Español](explain-anything.es.md) · [Français](explain-anything.fr.md) · [Deutsch](explain-anything.de.md) · [Русский](explain-anything.ru.md) · [Українська](explain-anything.uk.md) · [Polski](explain-anything.pl.md) · [Português (Brasil)](explain-anything.pt.md) · [Bahasa Indonesia](explain-anything.id.md) · [Filipino](explain-anything.tl.md) · **Tiếng Việt** · [简体中文](explain-anything.zh.md) · [हिन्दी](explain-anything.hi.md) · [עברית](explain-anything.he.md) · [العربية](explain-anything.ar.md)
<!-- /languages -->

KVASIR khởi đầu là một thiết bị trên giá giải thích những lần chạy hỏng. Giờ
nó với tới bốn nơi — giá, trình soạn thảo, Studio API và Studio cơ sở dữ liệu
— và gương mặt nào cũng theo đúng ba luật: **bạn thấy chính xác những gì sẽ
rời khỏi máy trước khi bất cứ thứ gì rời đi**, **mỗi bề mặt tự có sự đồng ý
của riêng nó** (đồng ý cho lỗi dựng không bao giờ đồng nghĩa với cho phép gửi mã
hay SQL), và **bí mật không thể đi kèm theo, ngay từ thiết kế** (phần tiết lộ do
chính studio sở hữu dữ liệu soạn ra, tiêu đề chứa thông tin xác thực bị loại bỏ
và mật khẩu thì không bao giờ nằm trong tầm với).

![KVASIR giải thích một lần chạy hỏng thật](../images/kvasir-explain.png)

## Trước khi bắt đầu

Một khóa dùng cho cả bốn gương mặt — từ nhà cung cấp nào bạn chọn: Claude
(Anthropic), ChatGPT (OpenAI) hoặc Gemini (Google). Nhấn **KEY…** trên mặt máy
KVASIR để chọn nhà cung cấp và lưu khóa của họ vào chùm khóa của hệ điều hành,
hoặc export `ANTHROPIC_API_KEY`, `OPENAI_API_KEY` hay `GEMINI_API_KEY`. Không
có khóa thì không có lời gọi — gương mặt nào cũng nói thật điều đó.

## Bốn gương mặt

1. **Một lần chạy hỏng (giá).** Gắn KVASIR, chạy thứ gì đó thất bại, nhấn
   **EXPLAIN**. Những gì được gửi: lệnh, mã thoát và tối đa năm dòng lỗi được
   lấy mẫu. Xem [bài hướng dẫn KVASIR](kvasir.vi.md) để đi trọn vẹn, kể cả sợi
   dây tự giải thích một lần hỏng của VERITAS mà không cần bấm.

2. **Mã của bạn (trình soạn thảo).** Chọn mã ở bất kỳ ngôn ngữ nào → nhấp
   chuột phải → **Hỏi KVASIR về vùng chọn…** rồi gõ câu hỏi. Những gì được gửi:
   vùng chọn đã giới hạn, tên tệp và ngôn ngữ — ngoài ra không có gì từ dự án
   của bạn. Gương mặt này có cổng đồng ý *riêng*, vì sự đồng ý của luồng lần
   hỏng đã hứa rõ ràng rằng mã nguồn không bao giờ rời khỏi máy.

3. **Một phản hồi API (Studio API).** Sau một lần gửi, nhấn **Giải thích…**.
   Những gì được gửi: phương thức, URL với giá trị truy vấn đã che, trạng thái,
   các tiêu đề với thông tin xác thực đã bị loại bỏ và được đếm, và một phần
   thân có giới hạn. Hữu ích ngay khi một lỗi 401 hay một tiêu đề CORS lạ xuất
   hiện.

4. **Một lỗi cơ sở dữ liệu (Studio cơ sở dữ liệu).** Một câu lệnh thất bại mọc
   ra nút **Giải thích…** dưới thông báo lỗi của nó. Những gì được gửi: câu SQL
   bạn đã chạy — *kể cả các giá trị hằng trong đó, và dòng đồng ý nói rõ điều
   này*, vì lỗi thường xoay quanh một giá trị hằng — cùng thông báo lỗi và loại
   máy. Không bao giờ có kết nối, mật khẩu hay các hàng dữ liệu.

Gương mặt nào cũng mở một cửa sổ hội thoại: hỏi thêm câu tiếp theo, và mô hình
thấy trọn lịch sử của cuộc trao đổi đó (giới hạn mười lượt, có ghi trong bản
hội thoại). Lựa chọn **Nhanh/Sâu** (mô hình nhanh và mô hình mạnh của nhà cung cấp đã
chọn) được ghi nhớ, và cố định cho
từng cuộc hội thoại để bản hội thoại không bao giờ nói sai ai đã trả lời.

## Thử trong hai phút

Studio cơ sở dữ liệu là gương mặt nhanh nhất để trình diễn: mở ⌥⌘7, tạo một kết
nối SQLite, chạy `SELECT * FROM user;` trên một cơ sở dữ liệu có bảng tên là
`users`, rồi nhấn **Giải thích…** ở lỗi. Đọc hộp thoại đồng ý trước khi chấp
nhận — đó là lời hứa của sản phẩm, gói trong một câu.

## Bạn vừa học được gì

- Bốn bề mặt, một đường nối: mỗi studio tự soạn phần tiết lộ của mình và hộp
  thoại đồng ý trích nguyên văn nó.
- Từ chối được tôn trọng lặng lẽ và trọn vẹn — không cửa sổ, không lời gọi.
- Một kết quả thuộc về không gian làm việc đã tạo ra nó: chuyển dự án sẽ xóa
  các phản hồi và thẻ kết quả, nên Giải thích không bao giờ tiết lộ được dữ liệu
  của một dự án trước đó.
