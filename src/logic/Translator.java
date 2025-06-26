package logic;

import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.util.regex.*;
import com.google.gson.*;

public class Translator {
    private static final String OPENAI_API_URL = "https://api.openai.com/v1/chat/completions";
    private String apiKey;
    private String modelName; // Thêm biến để lưu model được chọn
    
    // Constructor ban đầu với model mặc định
    public Translator(String apiKey) {
        this.apiKey = apiKey;
        this.modelName = "gpt-4o-mini"; // Mặc định là gpt-4o-mini
    }
    
    // Constructor mới cho phép chỉ định model
    public Translator(String apiKey, String modelName) {
        this.apiKey = apiKey;
        this.modelName = modelName;
    }
    
    // Getter/setter cho model name
    public String getModelName() {
        return modelName;
    }
    
    public void setModelName(String modelName) {
        this.modelName = modelName;
    }
    
    // Các phương thức dịch từng từ
    public String translateText(String text, String targetLanguage) throws IOException, InterruptedException {
        String languageName = targetLanguage.equals("en") ? "English" : "Vietnamese";
        String prompt = String.format(
            "Translate the following text to %s. Keep the same tone and meaning:\n\n%s",
            languageName, text
        );

        // Tạo JSON request
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName);
        requestBody.addProperty("max_tokens", 1024);
        requestBody.addProperty("temperature", 0.3);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);

        // Gửi request
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("OpenAI API error: " + response.statusCode() + " - " + response.body());
        }

        // Parse response
        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = responseJson.getAsJsonArray("choices");

        if (choices.size() == 0) {
            throw new IOException("No translation received from OpenAI");
        }

        return choices.get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString().trim();
    }

    /**
     * Dịch file SRT sang ngôn ngữ đích
     * @param inputFile File SRT gốc
     * @param outputFile File SRT đã dịch
     * @param targetLanguage Ngôn ngữ đích ("en" hoặc "vi")
     * @throws IOException
     */
    public void translateSrtFile(File inputFile, File outputFile, String targetLanguage) throws IOException {
        String content = Files.readString(inputFile.toPath());
        String[] subtitleBlocks = content.split("\n\n");

        StringBuilder translatedContent = new StringBuilder();
        int chunkSize = 30; // Số block mỗi chunk, có thể điều chỉnh
        int totalBlocks = subtitleBlocks.length;

        for (int i = 0; i < totalBlocks; i += chunkSize) {
            int end = Math.min(i + chunkSize, totalBlocks);
            StringBuilder chunkBuilder = new StringBuilder();
            // Gom các block thành 1 đoạn lớn, giữ nguyên số thứ tự và timestamp
            for (int j = i; j < end; j++) {
                chunkBuilder.append(subtitleBlocks[j].trim()).append("\n\n");
            }

            String chunk = chunkBuilder.toString().trim();
            String translatedChunk;
            try {
                translatedChunk = translateChunk(chunk, targetLanguage);
            } catch (Exception e) {
                log("Lỗi dịch chunk: " + e.getMessage());
                // Nếu lỗi, giữ nguyên đoạn gốc
                translatedChunk = chunk;
            }

            // Ghép vào kết quả cuối
            translatedContent.append(translatedChunk).append("\n\n");
            // Delay nhẹ để tránh rate limit
            try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
        }

        Files.writeString(outputFile.toPath(), translatedContent.toString().trim());
    }
    
    /**
     * Dịch một chunk subtitle (nhiều block) và trả về kết quả đã format lại đúng chuẩn SRT
     */
    private String translateChunk(String chunk, String targetLanguage) throws IOException, InterruptedException {
        String languageName = targetLanguage.equals("en") ? "English" : "Vietnamese";
        String prompt = String.format(
            "You are a professional subtitle translator. " +
            "Translate the following SRT subtitles to %s. " +
            "Keep the subtitle format (number, timestamp, line breaks). " +
            "Do not add any explanations, just return the translated SRT content:\n\n%s",
            languageName, chunk
        );

        log("Sending chunk for translation using model: " + modelName + " (" + chunk.length() + " chars)");
        
        // Tạo JSON request
        JsonObject requestBody = new JsonObject();
        requestBody.addProperty("model", modelName); // Sử dụng model đã chọn
        requestBody.addProperty("max_tokens", 2048);
        requestBody.addProperty("temperature", 0.3);

        JsonArray messages = new JsonArray();
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.addProperty("content", prompt);
        messages.add(message);
        requestBody.add("messages", messages);
        
        log("Request payload created");

        // Gửi request
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(OPENAI_API_URL))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build();

        log("Sending request to OpenAI API...");
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            String error = "OpenAI API error: " + response.statusCode() + " - " + response.body();
            log("API ERROR: " + error);
            throw new IOException(error);
        }

        log("Response received (status: " + response.statusCode() + ")");
        
        // Parse response
        JsonObject responseJson = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray choices = responseJson.getAsJsonArray("choices");

        if (choices.size() == 0) {
            log("ERROR: No translation received from OpenAI");
            throw new IOException("No translation received from OpenAI");
        }

        String translatedText = choices.get(0).getAsJsonObject()
                .getAsJsonObject("message")
                .get("content").getAsString().trim();
        
        log("Translation received successfully (" + translatedText.length() + " chars)");
        return translatedText;
    }
    
    // Phương thức tiện ích để lấy danh sách các model có sẵn
    public static String[] getAvailableModels() {
        return new String[] {
            "gpt-4o-mini",  // Nhanh và rẻ hơn
            "gpt-4o",       // Cân bằng giữa chất lượng và chi phí
            "gpt-4-turbo",  // Tên cũ của gpt-4-0125-preview
            "gpt-4-1106-preview", // GPT-4.1 (GPT-4 Turbo phiên bản cũ hơn)
            "gpt-4-vision-preview" // GPT-4 với khả năng nhìn hình ảnh
        };
    }
    
    /**
     * Dịch batch nhiều file SRT
     */
    public void translateBatch(File[] inputFiles, String outputDir, String targetLanguage, 
                             ProgressCallback callback) throws IOException {
        for (int i = 0; i < inputFiles.length; i++) {
            File inputFile = inputFiles[i];
            String fileName = inputFile.getName().replaceAll("\\.srt$", "_" + targetLanguage + ".srt");
            File outputFile = new File(outputDir, fileName);
            
            if (callback != null) {
                callback.onProgress(i + 1, inputFiles.length, "Translating: " + inputFile.getName());
            }
            
            try {
                translateSrtFile(inputFile, outputFile, targetLanguage);
            } catch (Exception e) {
                log("Lỗi dịch file " + inputFile.getName() + ": " + e.getMessage());
            }
        }
        
        if (callback != null) {
            callback.onProgress(inputFiles.length, inputFiles.length, "Translation completed!");
        }
    }
    
    /**
     * Interface để callback progress
     */
    public interface ProgressCallback {
        void onProgress(int current, int total, String message);
    }
    
    /**
     * Test method
     */
    public static void main(String[] args) {
        try {
            String apiKey = "your-openai-api-key-here";
            Translator translator = new Translator(apiKey);
            
            File inputFile = new File("test.srt");
            File outputFile = new File("test_vi.srt");
            
            translator.translateSrtFile(inputFile, outputFile, "vi");
            System.out.println("Translation completed!");
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Thêm vào class Translator
    public interface LogCallback {
        void onLog(String message);
    }

    private LogCallback logCallback;

    public void setLogCallback(LogCallback callback) {
        this.logCallback = callback;
    }

    // Log message
    private void log(String message) {
        if (logCallback != null) {
            logCallback.onLog(message);
        }
    }
}