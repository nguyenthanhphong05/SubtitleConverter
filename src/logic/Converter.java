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
}
