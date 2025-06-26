<<<<<<< HEAD
# SubtitleConverter
Just a small java project to convert vtt/srt to lrc format, and the opposite way
=======
# Subtitle Converter

Ứng dụng Java chuyển đổi phụ đề SRT/VTT sang định dạng LRC, hỗ trợ kéo-thả file, xử lý hàng loạt (batch), và đổi tên file đầu ra chỉ giữ số đầu tiên nếu muốn.

## Tính năng

- **Chuyển đổi SRT/VTT sang LRC**: Hỗ trợ cả file đơn lẻ và nhiều file cùng lúc.
- **Kéo-thả file**: Thả file trực tiếp vào giao diện để thêm vào danh sách chuyển đổi.
- **Batch convert**: Chuyển đổi nhiều file cùng lúc.
- **Lưu file LRC tại thư mục gốc**: File LRC sẽ được lưu cùng thư mục với file phụ đề gốc.
- **Đổi tên file LRC**: Tùy chọn đổi tên file LRC chỉ giữ số đầu tiên trong tên file.

## Cách sử dụng

1. **Build project**:  
   - Mở terminal tại thư mục dự án, chạy:
     ```
     javac -d bin -sourcepath src src/ui/mainframe.java
     ```
2. **Chạy ứng dụng**:  
   - Chạy lệnh:
     ```
     java -cp bin ui.mainframe
     ```
3. **Sử dụng giao diện**:  
   - Chọn file hoặc kéo-thả file vào danh sách.
   - Nhấn "Convert" để chuyển đổi.
   - Chọn checkbox nếu muốn đổi tên file LRC.

## Cấu trúc thư mục

- `src`: Chứa mã nguồn Java
- `lib`: Chứa thư viện phụ thuộc (nếu có)
- `bin`: Chứa file biên dịch

## Đóng góp

Pull request và issue luôn được chào đón!


---

> Project Java Swing đơn giản, thích hợp cho nhu cầu chuyển đổi phụ đề sang LRC cho karaoke, học ngoại ngữ, hoặc lưu trữ cá nhân.
> credit @kiyosnguyen5 - coded with copilot (gpt4.1)
>>>>>>> 3c50a21 (Initial commit)
