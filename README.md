# Subtitle Converter Pro

![Version](https://img.shields.io/badge/version-2.1-blue.svg) ![Platform](https://img.shields.io/badge/platform-Windows%20%7C%20macOS%20%7C%20Linux-lightgrey.svg) ![Java](https://img.shields.io/badge/Java-11%2B-orange.svg)

Công cụ chuyên nghiệp giúp chuyển đổi giữa các định dạng phụ đề SRT, VTT và LRC. Hỗ trợ xử lý hàng loạt, dịch tự động bằng trí tuệ nhân tạo OpenAI, và giao diện trực quan với hai phiên bản UI (JavaFX & Swing).

![App Screenshot](https://raw.githubusercontent.com/nguyenthanhphong05/SubtitleConverter/main/screenshot.png)

## ✨ Tính năng nổi bật

- **Chuyển đổi đa định dạng:** SRT ↔️ VTT ↔️ LRC với độ chính xác cao.
- **Dịch phụ đề thông minh:** Tích hợp OpenAI API, hỗ trợ nhiều model GPT (`gpt-4o-mini`, `gpt-4o`, `gpt-4-turbo`...).
- **Xử lý hàng loạt:** Chuyển đổi và dịch nhiều file cùng lúc.
- **Giao diện kép:**
  - **JavaFX:** Giao diện hiện đại, mượt mà.
  - **Swing:** Giao diện cổ điển, tương thích rộng rãi.
- **Tùy chỉnh linh hoạt:**
  - Tự động đổi tên file (chỉ giữ lại số đầu tiên) (Don't ask why btw).
  - Chế độ Sáng/Tối (Dark Mode là mặc định).
  - Tùy chọn thư mục lưu file.
- **Xem trước trực quan:** Xem nội dung phụ đề gốc và bản dịch ngay trên giao diện trước khi xử lý.
- **Hỗ trợ Kéo-Thả:** Thêm file nhanh chóng bằng cách kéo thả vào cửa sổ ứng dụng.
- **Nhật ký (Log):** Theo dõi chi tiết quá trình dịch và các lỗi phát sinh.

## 📋 Yêu cầu

- **Java Development Kit (JDK) 11** trở lên.
- **JavaFX SDK** (nếu JDK của bạn không tích hợp sẵn).
- **Thư viện GSON:** Đã có sẵn trong thư mục `lib/`.
- **Kết nối Internet:** Bắt buộc cho tính năng dịch.
- **OpenAI API Key:** Bắt buộc cho tính năng dịch.

## 🚀 Cài đặt & Chạy ứng dụng

Vì dự án chưa được đóng gói thành file `.jar`, bạn cần biên dịch từ mã nguồn.

### 1. Tải mã nguồn

```bash
git clone https://github.com/nguyenthanhphong05/SubtitleConverter.git
cd SubtitleConverter
```

### 2. Biên dịch

Mở Terminal hoặc Command Prompt trong thư mục gốc của dự án và chạy lệnh sau.

**Đối với Windows (CMD/PowerShell):**
```bash
javac -d bin -cp "lib\gson-2.10.1.jar" src\App.java src\logic\*.java src\ui\*.java
```

**Đối với macOS/Linux:**
```bash
javac -d bin -cp "lib/gson-2.10.1.jar" src/App.java src/logic/*.java src/ui/*.java
```
Lệnh này sẽ biên dịch tất cả các file mã nguồn và lưu file `.class` vào thư mục `bin`.

### 3. Chạy ứng dụng

Sau khi biên dịch thành công, chạy ứng dụng bằng lệnh sau.

**Đối với Windows (CMD/PowerShell):**
```bash
java -cp "bin;lib\gson-2.10.1.jar" App
```

**Đối với macOS/Linux:**
```bash
java -cp "bin:lib/gson-2.10.1.jar" App
```

Khi chạy, một hộp thoại sẽ hiện ra cho phép bạn chọn phiên bản giao diện **JavaFX** hoặc **Swing**.

## 🖥️ Hướng dẫn sử dụng

1.  **Thêm file:** Nhấn nút `Add Files` hoặc kéo-thả file (`.srt`, `.vtt`, `.lrc`) vào bảng danh sách.
2.  **Cấu hình chuyển đổi:**
    *   **Convert to:** Chọn định dạng bạn muốn xuất ra.
    *   **Output folder:** Chọn nơi lưu file sau khi xử lý. Nếu để trống, file sẽ được lưu cùng thư mục với file gốc.
3.  **Cấu hình dịch (Tùy chọn):**
    *   **Translate to:** Chọn ngôn ngữ đích.
    *   **OpenAI API Key:** Dán API key của bạn vào.
    *   **AI Model:** Chọn model GPT phù hợp với nhu cầu (chất lượng/tốc độ/chi phí).
4.  **Xem trước:** Chọn một file trong danh sách để xem trước nội dung. Nhấn `Translate Preview` để dịch thử nội dung file đó.
5.  **Bắt đầu:** Nhấn `Convert All` để bắt đầu quá trình. Theo dõi tiến trình ở thanh trạng thái và log ở cửa sổ bên dưới.

## 📁 Cấu trúc dự án

```
SubtitleConverter/
├── src/                # Mã nguồn
│   ├── ui/             # Giao diện người dùng (MainApp.java, SwingMainApp.java)
│   ├── logic/          # Xử lý logic (Converter.java, Translator.java)
│   └── App.java        # Điểm vào ứng dụng (entry point)
├── lib/                # Thư viện (gson-2.10.1.jar)
├── bin/                # Chứa file .class sau khi biên dịch
└── user_config.properties  # File lưu cấu hình của bạn
```

## 🤝 Đóng góp

Mọi đóng góp đều được chào đón! Nếu bạn có ý tưởng cải thiện, vui lòng:

1.  Fork dự án.
2.  Tạo một nhánh mới (`git checkout -b feature/AmazingFeature`).
3.  Commit thay đổi của bạn (`git commit -m 'Add some AmazingFeature'`).
4.  Push lên nhánh (`git push origin feature/AmazingFeature`).
5.  Mở một Pull Request.

---

> Developed with ❤️ by @kiyosnguyen5 with GitHub Copilot (GPT-4.1)
>
> Hoàn hảo cho việc tạo lời karaoke, học ngoại ngữ và quản lý phụ đề cá nhân.