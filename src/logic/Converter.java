//using java to convert srt/vtt to lrc format, and a logic to rename the file by only keep the first number in the name (optional)
package logic;

import java.io.*;
import java.nio.file.*;
import java.util.regex.*;
import java.util.*;

public class Converter {

    // Chuyển đổi SRT sang LRC
    public static void convertSrtToLrc(File srtFile, File lrcFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(srtFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter(lrcFile));
        String line;
        Pattern timePattern = Pattern.compile("(\\d{2}):(\\d{2}):(\\d{2}),(\\d{3}) --> .*");
        while ((line = reader.readLine()) != null) {
            Matcher matcher = timePattern.matcher(line);
            if (matcher.matches()) {
                int min = Integer.parseInt(matcher.group(2));
                int sec = Integer.parseInt(matcher.group(3));
                int ms = Integer.parseInt(matcher.group(4));
                String lrcTime = String.format("[%02d:%02d.%02d]", min, sec, ms / 10);
                String lyric = reader.readLine();
                writer.write(lrcTime + lyric + "\n");
            }
        }
        reader.close();
        writer.close();
    }

    // Chuyển đổi VTT sang LRC
    public static void convertVttToLrc(File vttFile, File lrcFile) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(vttFile));
        BufferedWriter writer = new BufferedWriter(new FileWriter(lrcFile));
        String line;
        Pattern timePattern = Pattern.compile("(\\d{2}):(\\d{2})\\.(\\d{3}) --> .*");
        while ((line = reader.readLine()) != null) {
            Matcher matcher = timePattern.matcher(line);
            if (matcher.matches()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                int ms = Integer.parseInt(matcher.group(3));
                String lrcTime = String.format("[%02d:%02d.%02d]", min, sec, ms / 10);
                String lyric = reader.readLine();
                writer.write(lrcTime + lyric + "\n");
            }
        }
        reader.close();
        writer.close();
    }

    // Đổi tên file chỉ giữ số đầu tiên
    public static File renameFileKeepFirstNumber(File file) throws IOException {
        String name = file.getName();
        Matcher matcher = Pattern.compile("(\\d+)").matcher(name);
        if (matcher.find()) {
            String newName = matcher.group(1) + getFileExtension(name);
            File newFile = new File(file.getParent(), newName);
            Files.move(file.toPath(), newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return newFile;
        }
        return file;
    }

    private static String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex != -1) ? filename.substring(dotIndex) : "";
    }

    /**
     * Convert LRC to SRT format
     */
    public static void convertLrcToSrt(File lrcFile, File srtFile) throws IOException {
        List<String> lines = Files.readAllLines(lrcFile.toPath());
        List<LrcEntry> entries = new ArrayList<>();
        Pattern timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)");
        
        // Đọc tất cả các entry từ file LRC
        for (String line : lines) {
            Matcher matcher = timePattern.matcher(line);
            if (matcher.matches()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                int ms = Integer.parseInt(matcher.group(3)) * 10; // Convert to milliseconds
                String text = matcher.group(4).trim();
                
                long startTime = (min * 60 + sec) * 1000 + ms;
                entries.add(new LrcEntry(startTime, text));
            }
        }
        
        // Sắp xếp entries theo thời gian
        Collections.sort(entries);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(srtFile))) {
            for (int i = 0; i < entries.size(); i++) {
                LrcEntry current = entries.get(i);
                
                // Tính thời gian kết thúc (hoặc là thời gian bắt đầu của entry tiếp theo, hoặc + 5 giây)
                long endTime;
                if (i < entries.size() - 1) {
                    endTime = entries.get(i + 1).startTime;
                } else {
                    endTime = current.startTime + 5000; // Thêm 5 giây cho entry cuối
                }
                
                // Ghi vào file SRT
                writer.write(String.valueOf(i + 1));
                writer.newLine();
                
                // Format: 00:00:00,000 --> 00:00:00,000
                writer.write(String.format("%02d:%02d:%02d,%03d --> %02d:%02d:%02d,%03d",
                        current.startTime / 3600000,                            // Giờ bắt đầu
                        (current.startTime % 3600000) / 60000,                  // Phút bắt đầu
                        (current.startTime % 60000) / 1000,                     // Giây bắt đầu
                        current.startTime % 1000,                               // Milli bắt đầu
                        endTime / 3600000,                                      // Giờ kết thúc
                        (endTime % 3600000) / 60000,                            // Phút kết thúc
                        (endTime % 60000) / 1000,                               // Giây kết thúc
                        endTime % 1000                                          // Milli kết thúc
                ));
                writer.newLine();
                
                writer.write(current.text);
                writer.newLine();
                writer.newLine();
            }
        }
    }

    /**
     * Convert LRC to VTT format
     */
    public static void convertLrcToVtt(File lrcFile, File vttFile) throws IOException {
        List<String> lines = Files.readAllLines(lrcFile.toPath());
        List<LrcEntry> entries = new ArrayList<>();
        Pattern timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)");
        
        // Đọc tất cả các entry từ file LRC
        for (String line : lines) {
            Matcher matcher = timePattern.matcher(line);
            if (matcher.matches()) {
                int min = Integer.parseInt(matcher.group(1));
                int sec = Integer.parseInt(matcher.group(2));
                int ms = Integer.parseInt(matcher.group(3)) * 10; // Convert to milliseconds
                String text = matcher.group(4).trim();
                
                long startTime = (min * 60 + sec) * 1000 + ms;
                entries.add(new LrcEntry(startTime, text));
            }
        }
        
        // Sắp xếp entries theo thời gian
        Collections.sort(entries);
        
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(vttFile))) {
            // VTT header
            writer.write("WEBVTT");
            writer.newLine();
            writer.newLine();
            
            for (int i = 0; i < entries.size(); i++) {
                LrcEntry current = entries.get(i);
                
                // Tính thời gian kết thúc (hoặc là thời gian bắt đầu của entry tiếp theo, hoặc + 5 giây)
                long endTime;
                if (i < entries.size() - 1) {
                    endTime = entries.get(i + 1).startTime;
                } else {
                    endTime = current.startTime + 5000; // Thêm 5 giây cho entry cuối
                }
                
                // Ghi vào file VTT
                // Format: 00:00:00.000 --> 00:00:00.000
                writer.write(String.format("%02d:%02d:%02d.%03d --> %02d:%02d:%02d.%03d",
                        current.startTime / 3600000,                            // Giờ bắt đầu
                        (current.startTime % 3600000) / 60000,                  // Phút bắt đầu
                        (current.startTime % 60000) / 1000,                     // Giây bắt đầu
                        current.startTime % 1000,                               // Milli bắt đầu
                        endTime / 3600000,                                      // Giờ kết thúc
                        (endTime % 3600000) / 60000,                            // Phút kết thúc
                        (endTime % 60000) / 1000,                               // Giây kết thúc
                        endTime % 1000                                          // Milli kết thúc
                ));
                writer.newLine();
                
                writer.write(current.text);
                writer.newLine();
                writer.newLine();
            }
        }
    }
    
    /**
     * Lớp lưu trữ entry LRC
     */
    private static class LrcEntry implements Comparable<LrcEntry> {
        long startTime; // thời gian tính theo milliseconds
        String text;
        
        public LrcEntry(long startTime, String text) {
            this.startTime = startTime;
            this.text = text;
        }
        
        @Override
        public int compareTo(LrcEntry other) {
            return Long.compare(this.startTime, other.startTime);
        }
    }
}
