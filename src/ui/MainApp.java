package ui;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.*;
import javafx.concurrent.Task;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import logic.Converter;
import logic.Translator;

import java.io.File;
import java.io.InputStream;  // Add this import
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainApp extends Application {
    private final ObservableList<File> fileList = FXCollections.observableArrayList();
    private final Label statusLabel = new Label("Ready");
    private final ProgressBar progressBar = new ProgressBar(0);
    private final CheckBox renameCheck = new CheckBox("Rename files (keep first number only)");
    private final CheckBox darkModeCheck = new CheckBox("Dark Mode");
    private final TextField outputPathField = new TextField();
    private final ComboBox<String> formatCombo = new ComboBox<>();
    private final ComboBox<String> translateCombo = new ComboBox<>();
    private final TextField apiKeyField = new TextField();
    private final ComboBox<String> modelCombo = new ComboBox<>(); // Thêm combobox cho model
    private TableView<File> table;
    private Scene scene;
    private boolean isDarkMode = true; // Mặc định là Dark Mode
    private static final String CONFIG_FILE = "user_config.properties";

    // Thêm biến để lưu log
    private final ObservableList<String> translationLogs = FXCollections.observableArrayList();

    // Biến instance để lưu trữ preview
    private ListView<SubtitleEntry> subtitlePreviewList;
    private ObservableList<SubtitleEntry> subtitleEntries = FXCollections.observableArrayList();
    private Label currentFileLabel;
    private Button translatePreviewBtn;

    @Override
    public void start(Stage stage) {
        loadConfig(); // Sửa đổi thành loadConfig
        stage.setTitle("Subtitle Converter Pro - JavaFX Edition");
        stage.setMinWidth(1200);
        stage.setMinHeight(700);

        createUI(stage);
        applyStyles();

        stage.setScene(scene);
        stage.show();

        Platform.runLater(() -> stage.requestFocus());
    }

    private void createUI(Stage stage) {
        // === TOP SECTION: SETTINGS ===
        VBox topSection = createTopSection(stage);
        
        // === CENTER SECTION: FILE TABLE ===
        VBox centerSection = createCenterSection();
        
        // === BOTTOM SECTION: ACTIONS & STATUS ===
        VBox bottomSection = createBottomSection();
        
        // === MAIN LAYOUT ===
        BorderPane root = new BorderPane();
        root.setTop(topSection);
        root.setCenter(centerSection);
        root.setBottom(bottomSection);
        root.setPadding(new Insets(15));
        
        scene = new Scene(root, 1200, 700);
        
        // Keyboard shortcuts
        scene.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.DELETE) removeSelectedFiles();
            if (e.isControlDown() && e.getCode() == KeyCode.O) addFiles(stage);
            if (e.isControlDown() && e.getCode() == KeyCode.ENTER) convertFiles();
            if (e.getCode() == KeyCode.F1) showAbout();
        });
    }

    private VBox createTopSection(Stage stage) {
        // Format & Translation selection
        Label formatLabel = new Label("Convert to:");
        formatCombo.getItems().addAll("LRC", "SRT", "VTT");
        formatCombo.setValue("LRC");
        formatCombo.setPrefWidth(100);
        
        Label translateLabel = new Label("Translate to:");
        translateCombo.getItems().addAll("None", "English", "Vietnamese");
        translateCombo.setValue("None");
        translateCombo.setPrefWidth(120);
        
        // Model selection
        Label modelLabel = new Label("AI Model:");
        modelCombo.getItems().addAll(Translator.getAvailableModels());
        modelCombo.setValue("gpt-4o-mini"); // Default option
        modelCombo.setPrefWidth(150);
        
        // API Key field for translation
        Label apiKeyLabel = new Label("OpenAI API Key:");
        apiKeyField.setPromptText("sk-xxxxxxxxxxxxxxxx (required for translation)");
        apiKeyField.setPrefWidth(250);
        
        // Output directory
        Label outputLabel = new Label("Output folder:");
        outputPathField.setPromptText("Same as source files");
        outputPathField.setPrefWidth(200);
        
        Button browseFolderBtn = new Button("📁 Browse");
        browseFolderBtn.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select Output Directory");
            File dir = dc.showDialog(stage);
            if (dir != null) outputPathField.setText(dir.getAbsolutePath());
        });
        
        // Settings
        renameCheck.setSelected(false);
        darkModeCheck.setSelected(true); // Dark mode by default
        darkModeCheck.setOnAction(e -> toggleDarkMode());
        
        // Layout
        HBox row1 = new HBox(10, formatLabel, formatCombo, new Separator(Orientation.VERTICAL), 
                            translateLabel, translateCombo, new Separator(Orientation.VERTICAL),
                            modelLabel, modelCombo);
        row1.setAlignment(Pos.CENTER_LEFT);
        
        HBox row2 = new HBox(10, apiKeyLabel, apiKeyField);
        row2.setAlignment(Pos.CENTER_LEFT);
        
        HBox row3 = new HBox(10, outputLabel, outputPathField, browseFolderBtn);
        row3.setAlignment(Pos.CENTER_LEFT);
        
        HBox row4 = new HBox(15, renameCheck, darkModeCheck);
        row4.setAlignment(Pos.CENTER_LEFT);
        
        VBox topSection = new VBox(8, row1, row2, row3, row4, new Separator());
        
        // Lưu API key và cấu hình khi người dùng nhập/chỉnh sửa
        apiKeyField.setOnAction(e -> saveConfig());
        apiKeyField.focusedProperty().addListener((obs, oldV, newV) -> {
            if (!newV) saveConfig();
        });
        modelCombo.valueProperty().addListener((obs, oldV, newV) -> saveConfig());
        
        return topSection;
    }

    private VBox createCenterSection() {
        // === FILE LIST SECTION ===
        table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        
        // Style cho các file đã dịch hoặc đã chuyển đổi
        table.setRowFactory(tv -> new TableRow<File>() {
            @Override
            protected void updateItem(File file, boolean empty) {
                super.updateItem(file, empty);
                if (file == null || empty) {
                    setStyle("");
                } else {
                    // Kiểm tra file đã được dịch chưa
                    File translatedFile = getTranslatedFile(file, 
                            translateCombo.getValue().equals("English") ? "en" : "vi");
                    
                    if (translatedFile.exists()) {
                        // File đã dịch - màu xanh lá
                        setStyle("-fx-background-color: #2a4a2a;");
                    } else if (getConvertedFile(file).exists() && !getConvertedFile(file).equals(file)) {
                        // File đã convert nhưng chưa dịch - màu xanh dương
                        setStyle("-fx-background-color: #2a3a4a;");
                    } else {
                        // File chưa xử lý - màu bình thường
                        setStyle("");
                    }
                }
            }
        });
        
        TableColumn<File, String> nameCol = new TableColumn<>("File Name");
        nameCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getName()));
        nameCol.setCellFactory(col -> new TableCell<File, String>() {
            @Override
            protected void updateItem(String name, boolean empty) {
                super.updateItem(name, empty);
                if (empty || name == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(name);
                    // Lấy file từ dòng hiện tại
                    TableRow<File> row = getTableRow();
                    if (row != null && row.getItem() != null) {
                        File file = row.getItem();
                        File translatedFile = getTranslatedFile(file, 
                                translateCombo.getValue().equals("English") ? "en" : "vi");
                        
                        if (translatedFile.exists()) {
                            // File đã dịch - màu xanh lá
                            setStyle("-fx-text-fill: #90EE90;");
                        } else if (getConvertedFile(file).exists() && !getConvertedFile(file).equals(file)) {
                            // File đã convert - màu xanh dương
                            setStyle("-fx-text-fill: #87CEFA;");
                        } else {
                            // File chưa xử lý
                            setStyle("");
                        }
                    }
                }
            }
        });
        nameCol.setPrefWidth(180);
        
        // Thêm cột Status
        TableColumn<File, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> {
            File file = data.getValue();
            File translatedFile = getTranslatedFile(file, 
                    translateCombo.getValue().equals("English") ? "en" : "vi");
            
            if (translatedFile.exists()) {
                return new SimpleStringProperty("✓ Translated");
            } else if (getConvertedFile(file).exists() && !getConvertedFile(file).equals(file)) {
                return new SimpleStringProperty("✓ Converted");
            } else {
                return new SimpleStringProperty("Waiting");
            }
        });
        statusCol.setCellFactory(col -> new TableCell<File, String>() {
            @Override
            protected void updateItem(String status, boolean empty) {
                super.updateItem(status, empty);
                if (empty || status == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(status);
                    if (status.contains("Translated")) {
                        setStyle("-fx-text-fill: #90EE90;");
                    } else if (status.contains("Converted")) {
                        setStyle("-fx-text-fill: #87CEFA;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        statusCol.setPrefWidth(100);
        
        TableColumn<File, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> {
            String ext = getFileExtension(data.getValue().getName()).toUpperCase();
            return new SimpleStringProperty(ext.isEmpty() ? "Unknown" : ext);
        });
        typeCol.setPrefWidth(60);
        
        TableColumn<File, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(data -> {
            long size = data.getValue().length();
            return new SimpleStringProperty(formatFileSize(size));
        });
        sizeCol.setPrefWidth(60);
        
        TableColumn<File, String> pathCol = new TableColumn<>("Path");
        pathCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getAbsolutePath()));
        pathCol.setPrefWidth(240);
        
        // Thêm cột mới vào đúng vị trí
        table.getColumns().setAll(nameCol, statusCol, typeCol, sizeCol, pathCol);
        table.setItems(fileList);
        table.setPlaceholder(new Label("🎬 Drag & drop subtitle files here\n(Supports: SRT, VTT, LRC)"));
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        
        // Drag & Drop
        setupDragAndDrop();
        
        // Context menu
        setupContextMenu();
        
        // === SUBTITLE PREVIEW SECTION (BÊN PHẢI) ===
        VBox previewBox = createSubtitlePreviewPane();
        
        // Vùng chứa phần bên trái (files + logs)
        VBox fileSection = new VBox(5);
        fileSection.getChildren().addAll(
            new Label("📂 Files to Process:"),
            table
        );
        VBox.setVgrow(table, Priority.ALWAYS);
        
        // === LOG VIEWER SECTION ===
        ListView<String> logView = new ListView<>(translationLogs);
        logView.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    // Highlight errors in red
                    if (item.contains("ERROR") || item.contains("Failed")) {
                        setStyle("-fx-text-fill: #ff4444;");
                    } else if (item.contains("Success")) {
                        setStyle("-fx-text-fill: #44ff44;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });
        
        // Auto-scroll to new log entries
        translationLogs.addListener((ListChangeListener.Change<? extends String> c) -> {
            if (c.next() && c.wasAdded()) {
                Platform.runLater(() -> logView.scrollTo(0));
            }
        });
        
        HBox logToolbar = new HBox(10);
        Button clearLogBtn = new Button("Clear Log");
        clearLogBtn.setOnAction(e -> translationLogs.clear());
        
        Button saveLogBtn = new Button("Save Log");
        saveLogBtn.setOnAction(e -> saveLogsToFile());
        
        logToolbar.getChildren().addAll(new Label("📋 Translation Log:"), new Region(), clearLogBtn, saveLogBtn);
        logToolbar.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(logToolbar.getChildren().get(1), Priority.ALWAYS);
        
        VBox logSection = new VBox(5, logToolbar, logView);
        VBox.setVgrow(logView, Priority.SOMETIMES);
        
        // Tạo SplitPane dọc cho phần bên trái (file list + log)
        SplitPane leftSplitPane = new SplitPane();
        leftSplitPane.setOrientation(Orientation.VERTICAL);
        leftSplitPane.getItems().addAll(fileSection, logSection);
        leftSplitPane.setDividerPositions(0.7); // 70% cho file list
        
        // Tạo SplitPane ngang cho toàn bộ (trái + phải)
        SplitPane mainSplitPane = new SplitPane();
        mainSplitPane.setOrientation(Orientation.HORIZONTAL);
        mainSplitPane.getItems().addAll(leftSplitPane, previewBox);
        mainSplitPane.setDividerPositions(0.6); // 60% cho phần bên trái
        
        VBox centerSection = new VBox();
        centerSection.getChildren().add(mainSplitPane);
        VBox.setVgrow(mainSplitPane, Priority.ALWAYS);
        
        // Bắt sự kiện khi chọn file
        table.getSelectionModel().selectedItemProperty().addListener(
            (obs, oldSelection, newSelection) -> {
                if (newSelection != null) {
                    loadSubtitlePreview(newSelection);
                }
            });
        
        return centerSection;
    }

    private VBox createSubtitlePreviewPane() {
        VBox previewPane = new VBox(10);
        previewPane.setPadding(new Insets(10));
        previewPane.setStyle("-fx-background-color: #1E1E1E;");
        
        // Toolbar cho preview panel
        HBox previewToolbar = new HBox(10);
        Label titleLabel = new Label("Subtitle Preview");
        titleLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px; -fx-font-weight: bold;");
        
        translatePreviewBtn = new Button("Translate Preview");
        translatePreviewBtn.setOnAction(e -> translateCurrentPreview());
        translatePreviewBtn.setDisable(true);
        
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        
        previewToolbar.getChildren().addAll(titleLabel, spacer, translatePreviewBtn);
        previewToolbar.setAlignment(Pos.CENTER_LEFT);
        
        // Hiển thị tên file đang preview
        currentFileLabel = new Label("Select a file to preview");
        currentFileLabel.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
        
        subtitlePreviewList = new ListView<>();
        subtitlePreviewList.setPrefWidth(400);
        subtitlePreviewList.setStyle("-fx-background-color: #1E1E1E;");
        
        subtitlePreviewList.setCellFactory(lv -> new ListCell<SubtitleEntry>() {
            @Override
            protected void updateItem(SubtitleEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                if (empty || entry == null) {
                    setText(null);
                    setGraphic(null);
                    setStyle("-fx-background-color: transparent;");
                } else {
                    VBox entryBox = new VBox(5);
                    entryBox.setPadding(new Insets(8));
                    entryBox.setStyle("-fx-background-color: #2D2D2D; -fx-background-radius: 5;");
                    
                    // Header với index và timestamp
                    HBox header = new HBox();
                    Label indexLabel = new Label(entry.index);
                    indexLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");
                    
                    Region headerSpacer = new Region();
                    HBox.setHgrow(headerSpacer, Priority.ALWAYS);
                    
                    Label timeLabel = new Label(entry.timestamp);
                    timeLabel.setStyle("-fx-text-fill: #888888; -fx-font-size: 12px;");
                    
                    header.getChildren().addAll(indexLabel, headerSpacer, timeLabel);
                    
                    // Original text
                    Label originalText = new Label(entry.originalText);
                    originalText.setStyle("-fx-text-fill: #CCCCCC; -fx-wrap-text: true;");
                    originalText.setWrapText(true);
                    
                    entryBox.getChildren().addAll(header, originalText);
                    
                    // Translated text (nếu có)
                    if (entry.translatedText != null && !entry.translatedText.isEmpty()) {
                        Separator divider = new Separator();
                        divider.setStyle("-fx-background-color: #555555;");
                        
                        Label translatedText = new Label(entry.translatedText);
                        translatedText.setStyle("-fx-text-fill: #90EE90; -fx-wrap-text: true; -fx-padding: 5 0 0 0;");
                        translatedText.setWrapText(true);
                        
                        entryBox.getChildren().addAll(divider, translatedText);
                    }
                    
                    setGraphic(entryBox);
                    setStyle("-fx-background-color: transparent;");
                }
            }
        });
        
        VBox.setVgrow(subtitlePreviewList, Priority.ALWAYS);
        previewPane.getChildren().addAll(previewToolbar, currentFileLabel, subtitlePreviewList);
        
        return previewPane;
    }

    // Lớp đối tượng để lưu trữ mỗi entry subtitle
    private class SubtitleEntry {
        String index;
        String timestamp;
        String originalText;
        String translatedText;
        
        public SubtitleEntry(String index, String timestamp, String originalText, String translatedText) {
            this.index = index;
            this.timestamp = timestamp;
            this.originalText = originalText;
            this.translatedText = translatedText;
        }
    }

    // Phương thức để load nội dung subtitle khi chọn file
    private void loadSubtitlePreview(File file) {
        if (file == null || !file.exists()) {
            clearSubtitlePreview();
            return;
        }
        
        translatePreviewBtn.setDisable(false);
        currentFileLabel.setText("File: " + file.getName());
        currentFileLabel.setStyle("-fx-text-fill: #CCCCCC;");
        
        // Kiểm tra xem file đã được dịch chưa
        File translatedFile = getTranslatedFile(file, translateCombo.getValue().equals("English") ? "en" : "vi");
        if (translatedFile.exists()) {
            // Nếu đã dịch, hiển thị nội dung dịch
            currentFileLabel.setText("File: " + file.getName() + " (Translated)");
            currentFileLabel.setStyle("-fx-text-fill: #90EE90;");
            
            // Tải nội dung file đã dịch
            try {
                loadTranslatedSubtitlePreview(file, translatedFile);
                return;
            } catch (Exception ex) {
                addTranslationLog("ERROR: Failed to load translated preview for " + file.getName());
                // Nếu lỗi, vẫn tiếp tục tải file gốc
            }
        }
        
        // Tải nội dung file gốc
        subtitleEntries.clear();
        try {
            String content = Files.readString(file.toPath());
            String ext = getFileExtension(file.getName()).toLowerCase();
            
            if (ext.equals(".lrc")) {
                loadLrcPreview(content);
            } else {
                // Existing code for SRT/VTT
                String[] blocks = content.split("\n\n");
                
                for (String block : blocks) {
                    if (block.trim().isEmpty()) continue;
                    
                    String[] lines = block.split("\n");
                    if (lines.length >= 3) {
                        String index = lines[0];
                        String timestamp = lines[1];
                        
                        StringBuilder textBuilder = new StringBuilder();
                        for (int i = 2; i < lines.length; i++) {
                            if (i > 2) textBuilder.append("\n");
                            textBuilder.append(lines[i]);
                        }
                        
                        // Tạo entry mới (chưa dịch)
                        subtitleEntries.add(new SubtitleEntry(index, timestamp, textBuilder.toString(), ""));
                    }
                }
            }
            
            subtitlePreviewList.setItems(subtitleEntries);
            
        } catch (Exception e) {
            e.printStackTrace();
            addTranslationLog("Error loading subtitle preview: " + e.getMessage());
        }
    }

    // Thêm phương thức tải file subtitle đã dịch
    private void loadTranslatedSubtitlePreview(File originalFile, File translatedFile) throws Exception {
        subtitleEntries.clear();
        
        String originalContent = Files.readString(originalFile.toPath());
        String translatedContent = Files.readString(translatedFile.toPath());
        
        String ext = getFileExtension(originalFile.getName()).toLowerCase();
        
        if (ext.equals(".lrc")) {
            loadLrcComparisonPreview(originalContent, translatedContent);
        } else {
            // Xử lý SRT/VTT
            String[] originalBlocks = originalContent.split("\n\n");
            String[] translatedBlocks = translatedContent.split("\n\n");
            
            int minSize = Math.min(originalBlocks.length, translatedBlocks.length);
            
            for (int i = 0; i < minSize; i++) {
                String[] originalLines = originalBlocks[i].split("\n");
                String[] translatedLines = translatedBlocks[i].split("\n");
                
                if (originalLines.length >= 3 && translatedLines.length >= 3) {
                    String index = originalLines[0];
                    String timestamp = originalLines[1];
                    
                    StringBuilder originalTextBuilder = new StringBuilder();
                    for (int j = 2; j < originalLines.length; j++) {
                        if (j > 2) originalTextBuilder.append("\n");
                        originalTextBuilder.append(originalLines[j]);
                    }
                    
                    StringBuilder translatedTextBuilder = new StringBuilder();
                    for (int j = 2; j < translatedLines.length; j++) {
                        if (j > 2) translatedTextBuilder.append("\n");
                        translatedTextBuilder.append(translatedLines[j]);
                    }
                    
                    subtitleEntries.add(new SubtitleEntry(
                        index, timestamp, 
                        originalTextBuilder.toString(),
                        translatedTextBuilder.toString()
                    ));
                }
            }
        }
        
        subtitlePreviewList.setItems(subtitleEntries);
    }

    // Phương thức để load file LRC so sánh
    private void loadLrcComparisonPreview(String originalContent, String translatedContent) {
        String[] originalLines = originalContent.split("\n");
        String[] translatedLines = translatedContent.split("\n");
        Pattern timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)");
        
        int index = 1;
        int translatedIndex = 0;
        
        for (String line : originalLines) {
            Matcher matcher = timePattern.matcher(line);
            if (matcher.matches()) {
                String min = matcher.group(1);
                String sec = matcher.group(2);
                String ms = matcher.group(3);
                String text = matcher.group(4).trim();
                
                String timestamp = min + ":" + sec + "." + ms;
                String displayTimestamp = "[" + timestamp + "]";
                
                // Tìm dòng dịch tương ứng
                String translatedText = "";
                if (translatedIndex < translatedLines.length) {
                    Matcher translatedMatcher = timePattern.matcher(translatedLines[translatedIndex]);
                    if (translatedMatcher.matches()) {
                        translatedText = translatedMatcher.group(4).trim();
                        translatedIndex++;
                    }
                }
                
                subtitleEntries.add(new SubtitleEntry(
                    String.valueOf(index++), 
                    displayTimestamp, 
                    text,
                    translatedText
                ));
            }
        }
    }

    // Phương thức clear preview
    private void clearSubtitlePreview() {
        subtitleEntries.clear();
        currentFileLabel.setText("Select a file to preview");
        currentFileLabel.setStyle("-fx-text-fill: #888888; -fx-font-style: italic;");
        translatePreviewBtn.setDisable(true);
    }

    // Thêm phương thức dịch preview hiện tại
    private void translateCurrentPreview() {
        if (apiKeyField.getText().trim().isEmpty()) {
            showAlert("API Key Required", "Please enter your OpenAI API key for translation.", Alert.AlertType.WARNING);
            return;
        }
        
        if (subtitleEntries.isEmpty()) {
            showAlert("No Content", "No subtitle content to translate in the preview.", Alert.AlertType.INFORMATION);
            return;
        }
        
        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Translator translator = new Translator(apiKeyField.getText().trim());
                String targetLang = translateCombo.getValue().equals("English") ? "en" : "vi";
                
                for (int i = 0; i < subtitleEntries.size(); i++) {
                    SubtitleEntry entry = subtitleEntries.get(i);
                    if (entry.translatedText == null || entry.translatedText.isEmpty()) {
                        updateMessage(String.format("Translating preview line %d/%d...", i + 1, subtitleEntries.size()));
                        
                        try {
                            String translated = translator.translateText(entry.originalText, targetLang);
                            final int index = i;
                            Platform.runLater(() -> {
                                subtitleEntries.get(index).translatedText = translated;
                                subtitlePreviewList.refresh();
                            });
                        } catch (Exception e) {
                            addTranslationLog("ERROR: Failed to translate line " + (i + 1) + " - " + e.getMessage());
                        }
                    }
                }
                updateMessage("Preview translation completed.");
                return null;
            }
        };
        
        statusLabel.textProperty().bind(task.messageProperty());
        
        task.setOnSucceeded(e -> {
            statusLabel.textProperty().unbind();
            setStatus("Preview translation completed successfully!");
        });
        
        task.setOnFailed(e -> {
            statusLabel.textProperty().unbind();
            setStatus("Preview translation failed");
            showAlert("Error", "Failed to translate preview: " + task.getException().getMessage(), Alert.AlertType.ERROR);
        });
        
        new Thread(task).start();
    }

    // Thêm phương thức đọc file LRC
    private void loadLrcPreview(String content) {
        String[] lines = content.split("\n");
        Pattern timePattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2})\\](.*)");
        
        int index = 1;
        for (String line : lines) {
            Matcher matcher = timePattern.matcher(line);
            if (matcher.matches()) {
                String min = matcher.group(1);
                String sec = matcher.group(2);
                String ms = matcher.group(3);
                String text = matcher.group(4).trim();
                
                String timestamp = min + ":" + sec + "." + ms;
                String displayTimestamp = "[" + timestamp + "]";
                
                // LRC không có số thứ tự, ta tự tạo
                subtitleEntries.add(new SubtitleEntry(
                    String.valueOf(index++), 
                    displayTimestamp, 
                    text,
                    ""
                ));
            }
        }
    }

    private VBox createBottomSection() {
        // Action buttons
        Button addBtn = new Button("➕ Add Files");
        addBtn.setOnAction(e -> addFiles((Stage) scene.getWindow()));
        
        Button removeBtn = new Button("➖ Remove Selected");
        removeBtn.setOnAction(e -> removeSelectedFiles());
        
        Button clearBtn = new Button("🗑️ Clear All");
        clearBtn.setOnAction(e -> clearAllFiles());
        
        Button convertBtn = new Button("🔄 Convert All");
        convertBtn.setDefaultButton(true);
        convertBtn.setOnAction(e -> convertFiles());
        
        Button aboutBtn = new Button("ℹ️ About");
        aboutBtn.setOnAction(e -> showAbout());
        
        // Progress section
        progressBar.setPrefWidth(200);
        progressBar.setVisible(false);
        
        statusLabel.setStyle("-fx-font-style: italic;");
        
        // Layout
        HBox buttonRow = new HBox(10, addBtn, removeBtn, clearBtn, new Region(), convertBtn, aboutBtn);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(buttonRow.getChildren().get(3), Priority.ALWAYS); // Spacer
        
        VBox progressSection = new VBox(5, progressBar, statusLabel);
        progressSection.setAlignment(Pos.CENTER);
        
        VBox bottomSection = new VBox(10, new Separator(), buttonRow, progressSection);
        return bottomSection;
    }

    private void setupDragAndDrop() {
        table.setOnDragOver(e -> {
            if (e.getGestureSource() != table && e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
            }
            e.consume();
        });
        
        table.setOnDragDropped(e -> {
            Dragboard db = e.getDragboard();
            boolean success = false;
            if (db.hasFiles()) {
                int added = 0;
                for (File f : db.getFiles()) {
                    if (isValidSubtitleFile(f) && !fileList.contains(f)) {
                        fileList.add(f);
                        added++;
                    }
                }
                setStatus(added > 0 ? "Added " + added + " file(s)" : "No valid subtitle files found");
                success = added > 0;
            }
            e.setDropCompleted(success);
            e.consume();
        });
    }

    private void setupContextMenu() {
        ContextMenu contextMenu = new ContextMenu();
        
        MenuItem openLocation = new MenuItem("📂 Open File Location");
        openLocation.setOnAction(e -> {
            File selected = table.getSelectionModel().getSelectedItem();
            if (selected != null) {
                try {
                    new ProcessBuilder("explorer", "/select," + selected.getAbsolutePath()).start();
                } catch (Exception ex) {
                    setStatus("Cannot open file location");
                }
            }
        });
        
        MenuItem removeItem = new MenuItem("➖ Remove from List");
        removeItem.setOnAction(e -> removeSelectedFiles());
        
        contextMenu.getItems().addAll(openLocation, removeItem);
        table.setContextMenu(contextMenu);
    }

    private void addFiles(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Select Subtitle Files");
        fc.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("All Subtitle Files", "*.srt", "*.vtt", "*.lrc"),
            new FileChooser.ExtensionFilter("SRT Files", "*.srt"),
            new FileChooser.ExtensionFilter("VTT Files", "*.vtt"),
            new FileChooser.ExtensionFilter("LRC Files", "*.lrc"),
            new FileChooser.ExtensionFilter("All Files", "*.*")
        );
        
        List<File> files = fc.showOpenMultipleDialog(stage);
        if (files != null) {
            int added = 0;
            for (File f : files) {
                if (!fileList.contains(f)) {
                    fileList.add(f);
                    added++;
                }
            }
            setStatus("Added " + added + " file(s)");
        }
    }

    private void removeSelectedFiles() {
        ObservableList<File> selected = table.getSelectionModel().getSelectedItems();
        if (!selected.isEmpty()) {
            fileList.removeAll(selected);
            setStatus("Removed " + selected.size() + " file(s)");
        }
    }

    private void clearAllFiles() {
        if (!fileList.isEmpty()) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Clear All Files");
            alert.setHeaderText("Remove all files from the list?");
            alert.setContentText("This action cannot be undone.");
            
            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                fileList.clear();
                setStatus("Cleared all files");
            }
        }
    }

    private void convertFiles() {
        if (fileList.isEmpty()) {
            setStatus("No files to convert");
            return;
        }

        // Check if translation is needed and API key is provided
        boolean needTranslation = !translateCombo.getValue().equals("None");
        if (needTranslation && apiKeyField.getText().trim().isEmpty()) {
            showAlert("API Key Required",
                    "Please enter your OpenAI API key for translation feature.",
                    Alert.AlertType.WARNING);
            return;
        }

        // Save config trước khi dịch
        if (needTranslation) saveConfig();

        Task<Void> task = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                int total = fileList.size();
                int success = 0;
                
                // Tạo translator với model đã chọn
                Translator translator = needTranslation ? 
                    new Translator(apiKeyField.getText().trim(), modelCombo.getValue()) : null;
                
                // Thiết lập callback ghi log
                if (translator != null) {
                    translator.setLogCallback(log -> addTranslationLog(log));
                    addTranslationLog("Starting translation job with model: " + modelCombo.getValue());
                }
                
                String targetLang = translateCombo.getValue().equals("English") ? "en" : "vi";
                
                for (int i = 0; i < total; i++) {
                    File inputFile = fileList.get(i);
                    updateMessage("Processing: " + inputFile.getName());
                    updateProgress(i, total);
                    
                    boolean converted = convertFile(inputFile);
                    if (converted && needTranslation) {
                        // Translate the converted file
                        File convertedFile = getConvertedFile(inputFile);
                        if (convertedFile.exists()) {
                            try {
                                updateMessage("Translating: " + inputFile.getName());
                                File translatedFile = getTranslatedFile(inputFile, targetLang);
                                translator.translateSrtFile(convertedFile, translatedFile, targetLang);
                            } catch (Exception e) {
                                updateMessage("Translation failed for: " + inputFile.getName());
                            }
                        }
                    }
                    
                    if (converted) success++;
                    
                    // Cập nhật UI để hiển thị file mới
                    Platform.runLater(() -> table.refresh());
                    
                    Thread.sleep(100); // Small delay for UI responsiveness
                }
                
                updateProgress(total, total);
                updateMessage("Processing completed: " + success + "/" + total + " files");
                
                // Nếu cần, thêm log khi hoàn tất
                if (translator != null) {
                    addTranslationLog("Translation job completed: " + success + "/" + total + " files");
                }
                
                return null;
            }
        };
        
        progressBar.progressProperty().bind(task.progressProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.setVisible(true);
        
        task.setOnSucceeded(e -> {
            progressBar.setVisible(false);
            showAlert("Processing Complete", 
                     "All files have been processed successfully!", 
                     Alert.AlertType.INFORMATION);
            table.refresh(); // Cập nhật lại màu sắc sau khi hoàn thành
        });
        
        task.setOnFailed(e -> {
            progressBar.setVisible(false);
            setStatus("Processing failed: " + task.getException().getMessage());
        });
        
        new Thread(task).start();
    }

    private File getConvertedFile(File inputFile) {
        String targetFormat = formatCombo.getValue().toLowerCase();
        File outputDir = outputPathField.getText().isEmpty() ? 
                       inputFile.getParentFile() : 
                       new File(outputPathField.getText());
        
        String newName = inputFile.getName().replaceAll("\\.(srt|vtt|lrc)$", "." + targetFormat);
        return new File(outputDir, newName);
    }

    private File getTranslatedFile(File inputFile, String targetLang) {
        String targetFormat = formatCombo.getValue().toLowerCase();
        File outputDir = outputPathField.getText().isEmpty() ? 
                       inputFile.getParentFile() : 
                       new File(outputPathField.getText());
        
        String newName = inputFile.getName().replaceAll("\\.(srt|vtt|lrc)$", "_" + targetLang + "." + targetFormat);
        return new File(outputDir, newName);
    }

    private boolean convertFile(File inputFile) {
        try {
            String ext = getFileExtension(inputFile.getName()).toLowerCase();
            String targetFormat = formatCombo.getValue().toLowerCase();
            
            // Determine output file
            File outputFile = getConvertedFile(inputFile);
            
            // Convert based on format
            if (ext.equals(".srt") && targetFormat.equals("lrc")) {
                Converter.convertSrtToLrc(inputFile, outputFile);
            } else if (ext.equals(".vtt") && targetFormat.equals("lrc")) {
                Converter.convertVttToLrc(inputFile, outputFile);
            } else if (ext.equals(".lrc") && targetFormat.equals("srt")) {
                Converter.convertLrcToSrt(inputFile, outputFile);
            } else if (ext.equals(".lrc") && targetFormat.equals("vtt")) {
                Converter.convertLrcToVtt(inputFile, outputFile);
            } else if (ext.equals(".srt") && targetFormat.equals("srt") ||
                      ext.equals(".vtt") && targetFormat.equals("vtt") ||
                      ext.equals(".lrc") && targetFormat.equals("lrc")) {
                // Same format, just copy
                Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                // Default case: just copy for now
                Files.copy(inputFile.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
            
            // Rename if requested
            if (renameCheck.isSelected()) {
                Converter.renameFileKeepFirstNumber(outputFile);
            }
            
            return true;
        } catch (Exception e) {
            Platform.runLater(() -> setStatus("Error converting " + inputFile.getName() + ": " + e.getMessage()));
            return false;
        }
    }

    private void toggleDarkMode() {
        isDarkMode = !isDarkMode;
        applyStyles();
    }

    private void applyStyles() {
        scene.getStylesheets().clear();
        
        if (isDarkMode) {
            scene.getRoot().setStyle("""
                -fx-base: #2b2b2b;
                -fx-background: #2b2b2b;
                -fx-control-inner-background: #3c3c3c;
                -fx-control-inner-background-alt: #404040;
                -fx-accent: #0096c9;
                -fx-default-button: #008cff;
                -fx-focus-color: #039ed3;
                -fx-faint-focus-color: #039ed322;
                -fx-text-fill: #ffffff;
                """);
        } else {
            scene.getRoot().setStyle("""
                -fx-base: #f4f4f4;
                -fx-background: #ffffff;
                -fx-control-inner-background: #ffffff;
                -fx-control-inner-background-alt: #f9f9f9;
                -fx-accent: #0096c9;
                -fx-default-button: #0078d4;
                -fx-focus-color: #005a9e;
                -fx-text-fill: #000000;
                """);
        }
    }

    private void showAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About Subtitle Converter Pro");
        alert.setHeaderText("Subtitle Converter Pro - JavaFX Edition");
        alert.setContentText("""
            Version: 2.1
            
            Features:
            • Convert SRT/VTT/LRC formats
            • Translate subtitles using OpenAI GPT
            • Batch conversion support
            • Drag & drop interface
            • Dark mode support
            • File renaming options
            • Real-time subtitle preview
            
            Translation:
            • Requires OpenAI API key
            • Supports English & Vietnamese
            • Uses GPT-4o-mini model
            
            Keyboard Shortcuts:
            • Ctrl+O: Add files
            • Ctrl+Enter: Convert
            • Delete: Remove selected
            • F1: About
            
            Created with ❤️ using JavaFX
            """);
        alert.showAndWait();
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean isValidSubtitleFile(File file) {
        if (file == null || !file.isFile()) return false;
        String ext = getFileExtension(file.getName()).toLowerCase();
        return ext.equals(".srt") || ext.equals(".vtt") || ext.equals(".lrc");
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot != -1) ? filename.substring(dot) : "";
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        return String.format("%.1f MB", size / (1024.0 * 1024.0));
    }

    private void setStatus(String message) {
        Platform.runLater(() -> statusLabel.setText(message));
    }

    // --- API Key Save/Load ---
    private void saveConfig() {
        try {
            Properties props = new Properties();
            props.setProperty("openai_api_key", apiKeyField.getText().trim());
            props.setProperty("app_mode", "addmode"); // Default app mode
            props.setProperty("model_name", modelCombo.getValue()); // Save selected model
            props.setProperty("dark_mode", String.valueOf(darkModeCheck.isSelected()));
            props.setProperty("output_path", outputPathField.getText());
            props.setProperty("format", formatCombo.getValue());
            props.setProperty("translate", translateCombo.getValue());
            
            try (OutputStream out = Files.newOutputStream(Path.of(CONFIG_FILE), 
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
                props.store(out, "User config");
            }
        } catch (Exception e) {
            System.err.println("Cannot save config: " + e.getMessage());
        }
    }

    private void loadConfig() {
        try {
            Properties props = new Properties();
            Path configPath = Path.of(CONFIG_FILE);
            if (Files.exists(configPath)) {
                try (InputStream in = Files.newInputStream(configPath)) {
                    props.load(in);
                }
            }
            // Load các cấu hình
            apiKeyField.setText(props.getProperty("openai_api_key", ""));
            String modelName = props.getProperty("model_name", "gpt-4o-mini");
            if (modelCombo.getItems().contains(modelName)) {
                modelCombo.setValue(modelName);
            }
            
            darkModeCheck.setSelected(Boolean.parseBoolean(props.getProperty("dark_mode", "true")));
            isDarkMode = darkModeCheck.isSelected();
            
            String outputPath = props.getProperty("output_path", "");
            if (!outputPath.isEmpty()) outputPathField.setText(outputPath);
            
            String format = props.getProperty("format", "LRC");
            if (formatCombo.getItems().contains(format)) {
                formatCombo.setValue(format);
            }
            
            String translate = props.getProperty("translate", "None");
            if (translateCombo.getItems().contains(translate)) {
                translateCombo.setValue(translate);
            }
        } catch (Exception e) {
            System.err.println("Cannot load config: " + e.getMessage());
        }
    }

    // Lưu log ra file
    private void saveLogsToFile() {
        try {
            FileChooser fc = new FileChooser();
            fc.setTitle("Save Translation Logs");
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Log Files", "*.log"));
            fc.setInitialFileName("translation_log.log");
            
            File file = fc.showSaveDialog(scene.getWindow());
            if (file != null) {
                StringBuilder content = new StringBuilder();
                for (String log : translationLogs) {
                    content.append(log).append("\n\n");
                }
                Files.writeString(file.toPath(), content.toString());
                setStatus("Logs saved to " + file.getName());
            }
        } catch (Exception e) {
            showAlert("Error", "Failed to save logs: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // Thêm log mới
    private void addTranslationLog(String message) {
        String timestamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String entry = "[" + timestamp + "] " + message;
        
        // Add to UI on JavaFX thread
        Platform.runLater(() -> {
            translationLogs.add(0, entry); // Add to top of list
            
            // Limit log entries to prevent memory issues
            if (translationLogs.size() > 500) {
                translationLogs.remove(500, translationLogs.size());
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}