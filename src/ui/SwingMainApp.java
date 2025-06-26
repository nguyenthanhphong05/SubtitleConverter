package ui;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import logic.Converter;
import logic.Translator;

import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.*;
import java.nio.file.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SwingMainApp extends JFrame {
    private static final long serialVersionUID = 1L;
    private static final String CONFIG_FILE = "user_config.properties";
    
    // UI Components
    private JTable fileTable;
    private DefaultTableModel fileTableModel;
    private JTextField apiKeyField;
    private JTextField outputPathField;
    private JComboBox<String> formatCombo;
    private JComboBox<String> translateCombo;
    private JComboBox<String> modelCombo;
    private JCheckBox renameCheck;
    private JCheckBox darkModeCheck;
    private JProgressBar progressBar;
    private JLabel statusLabel;
    private JButton translatePreviewBtn;
    private JLabel currentFileLabel;
    private JPanel subtitlePreviewPanel;
    private JScrollPane subtitlePreviewScrollPane;
    private DefaultListModel<String> logListModel;
    private JList<String> logList;
    private JSplitPane mainSplitPane;
    private JSplitPane leftSplitPane;
    
    // Data
    private java.util.List<File> fileList = new ArrayList<>();
    private java.util.List<SubtitleEntry> subtitleEntries = new ArrayList<>();
    private boolean isDarkMode = false;  // Mặc định là Dark Mode
    
    public SwingMainApp() {
        setTitle("Subtitle Converter Pro - Swing Edition");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1200, 700);
        setMinimumSize(new Dimension(1000, 600));
        
        loadConfig();
        initUI();
        applyTheme();
        setupDragAndDrop();
        
        setLocationRelativeTo(null);
    }
    
    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout(10, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        
        // TOP SECTION
        mainPanel.add(createTopPanel(), BorderLayout.NORTH);
        
        // CENTER SECTION
        mainPanel.add(createCenterPanel(), BorderLayout.CENTER);
        
        // BOTTOM SECTION
        mainPanel.add(createBottomPanel(), BorderLayout.SOUTH);
        
        // Add keyboard shortcuts
        addKeyboardShortcuts();
        
        setContentPane(mainPanel);
    }
    
    private JPanel createTopPanel() {
        JPanel topPanel = new JPanel();
        topPanel.setLayout(new BoxLayout(topPanel, BoxLayout.Y_AXIS));
        
        // Row 1: Format and Translation
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel formatLabel = new JLabel("Convert to:");
        formatCombo = new JComboBox<>(new String[]{"LRC", "SRT", "VTT"});
        formatCombo.setPreferredSize(new Dimension(100, 25));
        
        JLabel translateLabel = new JLabel("Translate to:");
        translateCombo = new JComboBox<>(new String[]{"None", "English", "Vietnamese"});
        translateCombo.setPreferredSize(new Dimension(120, 25));
        
        JLabel modelLabel = new JLabel("AI Model:");
        modelCombo = new JComboBox<>(Translator.getAvailableModels());
        modelCombo.setSelectedItem("gpt-4o-mini"); // Default model
        modelCombo.setPreferredSize(new Dimension(150, 25));
        
        row1.add(formatLabel);
        row1.add(formatCombo);
        row1.add(Box.createHorizontalStrut(20));
        row1.add(new JSeparator(SwingConstants.VERTICAL));
        row1.add(Box.createHorizontalStrut(20));
        row1.add(translateLabel);
        row1.add(translateCombo);
        row1.add(Box.createHorizontalStrut(20));
        row1.add(new JSeparator(SwingConstants.VERTICAL));
        row1.add(Box.createHorizontalStrut(20));
        row1.add(modelLabel);
        row1.add(modelCombo);
        
        // Row 2: API Key
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel apiKeyLabel = new JLabel("OpenAI API Key:");
        apiKeyField = new JTextField(25);
        apiKeyField.setToolTipText("sk-xxxxxxxxxxxxxxxx (required for translation)");
        
        row2.add(apiKeyLabel);
        row2.add(apiKeyField);
        
        // Row 3: Output folder
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JLabel outputLabel = new JLabel("Output folder:");
        outputPathField = new JTextField(20);
        outputPathField.setToolTipText("Same as source files");
        JButton browseFolderBtn = new JButton("📁 Browse");
        browseFolderBtn.addActionListener(e -> browseOutputFolder());
        
        row3.add(outputLabel);
        row3.add(outputPathField);
        row3.add(browseFolderBtn);
        
        // Row 4: Settings
        JPanel row4 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        renameCheck = new JCheckBox("Rename files (keep first number only)");
        darkModeCheck = new JCheckBox("Dark Mode");
        darkModeCheck.setSelected(isDarkMode);
        darkModeCheck.addActionListener(e -> toggleDarkMode());
        
        row4.add(renameCheck);
        row4.add(Box.createHorizontalStrut(15));
        row4.add(darkModeCheck);
        
        // Add all rows to top panel
        topPanel.add(row1);
        topPanel.add(row2);
        topPanel.add(row3);
        topPanel.add(row4);
        topPanel.add(new JSeparator());
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        return topPanel;
    }
    
    private JPanel createCenterPanel() {
        JPanel centerPanel = new JPanel(new BorderLayout());
        
        // File table setup
        fileTableModel = new DefaultTableModel() {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        
        fileTableModel.addColumn("File Name");
        fileTableModel.addColumn("Status");
        fileTableModel.addColumn("Type");
        fileTableModel.addColumn("Size");
        fileTableModel.addColumn("Path");
        
        fileTable = new JTable(fileTableModel);
        fileTable.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        fileTable.setRowHeight(25);
        fileTable.getTableHeader().setReorderingAllowed(false);
        fileTable.setFillsViewportHeight(true);
        fileTable.setShowGrid(true);
        fileTable.setGridColor(new Color(60, 60, 60));
        
        // Tùy chỉnh renderer cho các cột
        fileTable.getColumnModel().getColumn(0).setPreferredWidth(180);
        fileTable.getColumnModel().getColumn(1).setPreferredWidth(100);
        fileTable.getColumnModel().getColumn(2).setPreferredWidth(60);
        fileTable.getColumnModel().getColumn(3).setPreferredWidth(60);
        fileTable.getColumnModel().getColumn(4).setPreferredWidth(240);
        
        // Tạo custom cell renderer để hiển thị màu sắc dựa trên trạng thái
        fileTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private static final long serialVersionUID = 1L;
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value, 
                    boolean isSelected, boolean hasFocus, int row, int column) {
                
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                
                if (!isSelected) {
                    File file = fileList.get(row);
                    String status = (String) table.getValueAt(row, 1);
                    
                    if (status.equals("✓ Translated")) {
                        c.setBackground(new Color(42, 74, 42)); // Dark green for translated
                        if (column == 0) c.setForeground(new Color(144, 238, 144)); // Light green text
                    } else if (status.equals("✓ Converted")) {
                        c.setBackground(new Color(42, 58, 74)); // Dark blue for converted
                        if (column == 0) c.setForeground(new Color(135, 206, 250)); // Light blue text
                    } else {
                        if (isDarkMode) {
                            c.setBackground(new Color(60, 63, 65));
                            c.setForeground(Color.WHITE);
                        } else {
                            c.setBackground(Color.WHITE);
                            c.setForeground(Color.BLACK);
                        }
                    }
                    
                    if (column == 1) { // Status column
                        if (status.equals("✓ Translated")) {
                            c.setForeground(new Color(144, 238, 144)); // Light green
                        } else if (status.equals("✓ Converted")) {
                            c.setForeground(new Color(135, 206, 250)); // Light blue
                        }
                    }
                }
                return c;
            }
        });

        // File table listener
        fileTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int selectedRow = fileTable.getSelectedRow();
                    if (selectedRow != -1) {
                        File selectedFile = fileList.get(selectedRow);
                        loadSubtitlePreview(selectedFile);
                    } else {
                        clearSubtitlePreview();
                    }
                }
            }
        });

        // Context menu
        setupContextMenu();
        
        // Scroll pane for file table
        JScrollPane fileScrollPane = new JScrollPane(fileTable);
        fileScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        
        JPanel filePanel = new JPanel(new BorderLayout());
        filePanel.add(new JLabel("📂 Files to Process:"), BorderLayout.NORTH);
        filePanel.add(fileScrollPane, BorderLayout.CENTER);
        
        // Translation log panel
        logListModel = new DefaultListModel<>();
        logList = new JList<>(logListModel);
        logList.setCellRenderer(new LogCellRenderer());
        
        JScrollPane logScrollPane = new JScrollPane(logList);
        logScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS);
        
        JButton clearLogBtn = new JButton("Clear Log");
        clearLogBtn.addActionListener(e -> logListModel.clear());
        
        JButton saveLogBtn = new JButton("Save Log");
        saveLogBtn.addActionListener(e -> saveLogsToFile());
        
        JPanel logToolbar = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logToolbar.add(new JLabel("📋 Translation Log:"));
        logToolbar.add(Box.createHorizontalGlue());
        logToolbar.add(clearLogBtn);
        logToolbar.add(saveLogBtn);
        
        JPanel logPanel = new JPanel(new BorderLayout());
        logPanel.add(logToolbar, BorderLayout.NORTH);
        logPanel.add(logScrollPane, BorderLayout.CENTER);
        
        // Subtitle preview panel
        JPanel previewPanel = createSubtitlePreviewPanel();
        
        // Split panes
        leftSplitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, filePanel, logPanel);
        leftSplitPane.setResizeWeight(0.7); // 70% cho file list
        
        mainSplitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftSplitPane, previewPanel);
        mainSplitPane.setResizeWeight(0.6); // 60% cho phần bên trái
        
        centerPanel.add(mainSplitPane, BorderLayout.CENTER);
        
        return centerPanel;
    }
    
    private JPanel createSubtitlePreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        panel.setBackground(new Color(30, 30, 30));
        
        // Preview toolbar
        JPanel toolbarPanel = new JPanel(new BorderLayout());
        toolbarPanel.setOpaque(false);
        
        JLabel titleLabel = new JLabel("Subtitle Preview");
        titleLabel.setForeground(Color.WHITE);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 16f));
        
        translatePreviewBtn = new JButton("Translate Preview");
        translatePreviewBtn.setEnabled(false);
        translatePreviewBtn.addActionListener(e -> translateCurrentPreview());
        
        toolbarPanel.add(titleLabel, BorderLayout.WEST);
        toolbarPanel.add(translatePreviewBtn, BorderLayout.EAST);
        
        // Current file label
        currentFileLabel = new JLabel("Select a file to preview");
        currentFileLabel.setForeground(new Color(136, 136, 136));
        currentFileLabel.setFont(currentFileLabel.getFont().deriveFont(Font.ITALIC));
        
        // Preview content panel
        subtitlePreviewPanel = new JPanel();
        subtitlePreviewPanel.setLayout(new BoxLayout(subtitlePreviewPanel, BoxLayout.Y_AXIS));
        subtitlePreviewPanel.setBackground(new Color(30, 30, 30));
        
        subtitlePreviewScrollPane = new JScrollPane(subtitlePreviewPanel);
        subtitlePreviewScrollPane.getVerticalScrollBar().setUnitIncrement(16);
        subtitlePreviewScrollPane.setBorder(null);
        
        // Add components to main panel
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setOpaque(false);
        topPanel.add(toolbarPanel, BorderLayout.NORTH);
        topPanel.add(currentFileLabel, BorderLayout.SOUTH);
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));
        
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(subtitlePreviewScrollPane, BorderLayout.CENTER);
        
        return panel;
    }
    
    private JPanel createBottomPanel() {
        JPanel bottomPanel = new JPanel(new BorderLayout(10, 10));
        bottomPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        
        // Action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        
        JButton addBtn = new JButton("➕ Add Files");
        addBtn.addActionListener(e -> addFiles());
        
        JButton removeBtn = new JButton("➖ Remove Selected");
        removeBtn.addActionListener(e -> removeSelectedFiles());
        
        JButton clearBtn = new JButton("🗑️ Clear All");
        clearBtn.addActionListener(e -> clearAllFiles());
        
        JButton convertBtn = new JButton("🔄 Convert All");
        convertBtn.addActionListener(e -> convertFiles());
        
        JButton aboutBtn = new JButton("ℹ️ About");
        aboutBtn.addActionListener(e -> showAbout());
        
        buttonPanel.add(addBtn);
        buttonPanel.add(removeBtn);
        buttonPanel.add(clearBtn);
        buttonPanel.add(Box.createHorizontalGlue());
        buttonPanel.add(convertBtn);
        buttonPanel.add(aboutBtn);
        
        // Progress section
        JPanel progressPanel = new JPanel(new BorderLayout(5, 5));
        progressBar = new JProgressBar(0, 100);
        progressBar.setPreferredSize(new Dimension(200, 20));
        progressBar.setStringPainted(true);
        progressBar.setVisible(false);
        
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.ITALIC));
        statusLabel.setHorizontalAlignment(JLabel.CENTER);
        
        progressPanel.add(progressBar, BorderLayout.NORTH);
        progressPanel.add(statusLabel, BorderLayout.SOUTH);
        
        // Add all sections to bottom panel
        bottomPanel.add(new JSeparator(), BorderLayout.NORTH);
        bottomPanel.add(buttonPanel, BorderLayout.CENTER);
        bottomPanel.add(progressPanel, BorderLayout.SOUTH);
        
        return bottomPanel;
    }
    
    private void setupContextMenu() {
        JPopupMenu contextMenu = new JPopupMenu();
        
        JMenuItem openLocation = new JMenuItem("📂 Open File Location");
        openLocation.addActionListener(e -> {
            int selectedRow = fileTable.getSelectedRow();
            if (selectedRow != -1) {
                File selected = fileList.get(selectedRow);
                try {
                    ProcessBuilder pb;
                    if (System.getProperty("os.name").toLowerCase().contains("windows")) {
                        pb = new ProcessBuilder("explorer", "/select," + selected.getAbsolutePath());
                    } else {
                        pb = new ProcessBuilder("xdg-open", selected.getParent());
                    }
                    pb.start();
                } catch (Exception ex) {
                    setStatus("Cannot open file location");
                }
            }
        });
        
        JMenuItem removeItem = new JMenuItem("➖ Remove from List");
        removeItem.addActionListener(e -> removeSelectedFiles());
        
        contextMenu.add(openLocation);
        contextMenu.add(removeItem);
        
        fileTable.setComponentPopupMenu(contextMenu);
    }
    
    private void setupDragAndDrop() {
        new DropTarget(fileTable, new DropTargetAdapter() {
            @Override
            public void drop(DropTargetDropEvent dtde) {
                try {
                    dtde.acceptDrop(DnDConstants.ACTION_COPY);
                    @SuppressWarnings("unchecked")
                    List<File> droppedFiles = (List<File>) dtde.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor);
                    
                    int added = 0;
                    for (File f : droppedFiles) {
                        if (isValidSubtitleFile(f) && !fileList.contains(f)) {
                            addFileToTable(f);
                            added++;
                        }
                    }
                    setStatus(added > 0 ? "Added " + added + " file(s)" : "No valid subtitle files found");
                    dtde.dropComplete(true);
                } catch (Exception ex) {
                    dtde.dropComplete(false);
                    ex.printStackTrace();
                }
            }
        });
    }
    
    private void addKeyboardShortcuts() {
        // Add keyboard shortcuts
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK), "addFiles");
        getRootPane().getActionMap().put("addFiles", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                addFiles();
            }
        });
        
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), "removeFiles");
        getRootPane().getActionMap().put("removeFiles", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                removeSelectedFiles();
            }
        });
        
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.CTRL_DOWN_MASK), "convertFiles");
        getRootPane().getActionMap().put("convertFiles", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                convertFiles();
            }
        });
        
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_F1, 0), "showAbout");
        getRootPane().getActionMap().put("showAbout", new AbstractAction() {
            private static final long serialVersionUID = 1L;
            @Override
            public void actionPerformed(ActionEvent e) {
                showAbout();
            }
        });
    }
    
    // --- ACTIONS ---
    
    private void browseOutputFolder() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Output Directory");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        
        int result = chooser.showDialog(this, "Select");
        if (result == JFileChooser.APPROVE_OPTION) {
            outputPathField.setText(chooser.getSelectedFile().getAbsolutePath());
        }
    }
    
    private void addFiles() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Select Subtitle Files");
        chooser.setMultiSelectionEnabled(true);
        
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("All Subtitle Files", "srt", "vtt", "lrc"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("SRT Files", "srt"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("VTT Files", "vtt"));
        chooser.addChoosableFileFilter(new FileNameExtensionFilter("LRC Files", "lrc"));
        chooser.setAcceptAllFileFilterUsed(true);
        
        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            int added = 0;
            for (File file : chooser.getSelectedFiles()) {
                if (isValidSubtitleFile(file) && !fileList.contains(file)) {
                    addFileToTable(file);
                    added++;
                }
            }
            setStatus("Added " + added + " file(s)");
        }
    }
    
    private void addFileToTable(File file) {
        // Add file to master list
        fileList.add(file);
        
        // Get status for this file
        String status = "Waiting";
        File translatedFile = getTranslatedFile(file, translateCombo.getSelectedItem().equals("English") ? "en" : "vi");
        if (translatedFile.exists()) {
            status = "✓ Translated";
        } else if (getConvertedFile(file).exists() && !getConvertedFile(file).equals(file)) {
            status = "✓ Converted";
        }
        
        // Add to table with file details
        String ext = getFileExtension(file.getName()).toUpperCase();
        String size = formatFileSize(file.length());
        
        fileTableModel.addRow(new Object[] { 
            file.getName(), 
            status,
            ext,
            size,
            file.getAbsolutePath()
        });
    }
    
    private void removeSelectedFiles() {
        int[] selectedRows = fileTable.getSelectedRows();
        if (selectedRows.length == 0) return;
        
        // Remove in reverse order to maintain correct indices
        Arrays.sort(selectedRows);
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            fileList.remove(selectedRows[i]);
            fileTableModel.removeRow(selectedRows[i]);
        }
        
        setStatus("Removed " + selectedRows.length + " file(s)");
    }
    
    private void clearAllFiles() {
        if (fileList.isEmpty()) return;
        
        int option = JOptionPane.showConfirmDialog(
            this,
            "Remove all files from the list?",
            "Clear All Files",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
        
        if (option == JOptionPane.OK_OPTION) {
            fileList.clear();
            fileTableModel.setRowCount(0);
            setStatus("Cleared all files");
        }
    }
    
    private void convertFiles() {
        if (fileList.isEmpty()) {
            setStatus("No files to convert");
            return;
        }
        
        // Check if translation is needed and API key is provided
        boolean needTranslation = !translateCombo.getSelectedItem().equals("None");
        if (needTranslation && apiKeyField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter your OpenAI API key for translation feature.",
                "API Key Required",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        // Save API key before translating
        if (needTranslation) saveConfig();
        
        // Enable progress display
        progressBar.setValue(0);
        progressBar.setVisible(true);
        
        // Create worker to perform conversion
        ConversionWorker worker = new ConversionWorker(
            needTranslation,
            translateCombo.getSelectedItem().equals("English") ? "en" : "vi"
        );
        
        worker.addPropertyChangeListener(evt -> {
            if ("progress".equals(evt.getPropertyName())) {
                int progress = (Integer) evt.getNewValue();
                progressBar.setValue(progress);
            }
        });
        
        worker.execute();
    }
    
    private void toggleDarkMode() {
        isDarkMode = darkModeCheck.isSelected();
        applyTheme();
    }
    
    private void applyTheme() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            
            if (isDarkMode) {
                UIManager.put("Panel.background", new Color(45, 45, 45));
                UIManager.put("OptionPane.background", new Color(45, 45, 45));
                UIManager.put("OptionPane.messageForeground", Color.WHITE);
                UIManager.put("TextField.background", new Color(60, 60, 60));
                UIManager.put("TextField.foreground", Color.WHITE);
                UIManager.put("TextArea.background", new Color(60, 60, 60));
                UIManager.put("TextArea.foreground", Color.WHITE);
                UIManager.put("List.background", new Color(60, 60, 60));
                UIManager.put("List.foreground", Color.WHITE);
                UIManager.put("Table.background", new Color(60, 63, 65));
                UIManager.put("Table.foreground", Color.WHITE);
                UIManager.put("Table.selectionBackground", new Color(0, 150, 201));
                UIManager.put("Table.gridColor", new Color(80, 80, 80));
                UIManager.put("TableHeader.background", new Color(60, 60, 60));
                UIManager.put("TableHeader.foreground", Color.WHITE);
                UIManager.put("ScrollPane.background", new Color(45, 45, 45));
                UIManager.put("ComboBox.background", new Color(60, 60, 60));
                UIManager.put("ComboBox.foreground", Color.WHITE);
                UIManager.put("Button.background", new Color(70, 70, 70));
                UIManager.put("Button.foreground", Color.WHITE);
                UIManager.put("CheckBox.background", new Color(45, 45, 45));
                UIManager.put("CheckBox.foreground", Color.WHITE);
                UIManager.put("Label.foreground", Color.WHITE);
            } else {
                UIManager.put("Panel.background", new Color(240, 240, 240));
                UIManager.put("TextField.background", Color.WHITE);
                UIManager.put("TextField.foreground", Color.BLACK);
                UIManager.put("TextArea.background", Color.WHITE);
                UIManager.put("TextArea.foreground", Color.BLACK);
                UIManager.put("List.background", Color.WHITE);
                UIManager.put("List.foreground", Color.BLACK);
                UIManager.put("Table.background", Color.WHITE);
                UIManager.put("Table.foreground", Color.BLACK);
                UIManager.put("Table.selectionBackground", new Color(51, 153, 255));
                UIManager.put("Table.gridColor", new Color(240, 240, 240));
                UIManager.put("TableHeader.background", new Color(240, 240, 240));
                UIManager.put("TableHeader.foreground", Color.BLACK);
                UIManager.put("ScrollPane.background", Color.WHITE);
                UIManager.put("ComboBox.background", Color.WHITE);
                UIManager.put("ComboBox.foreground", Color.BLACK);
                UIManager.put("Button.background", new Color(240, 240, 240));
                UIManager.put("Button.foreground", Color.BLACK);
                UIManager.put("CheckBox.background", new Color(240, 240, 240));
                UIManager.put("CheckBox.foreground", Color.BLACK);
                UIManager.put("Label.foreground", Color.BLACK);
            }
            
            SwingUtilities.updateComponentTreeUI(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showAbout() {
        JOptionPane.showMessageDialog(
            this,
            "Subtitle Converter Pro - Swing Edition\n\n" +
            "Version: 2.1\n\n" +
            "Features:\n" +
            "• Convert SRT/VTT/LRC formats\n" +
            "• Translate subtitles using OpenAI GPT\n" +
            "• Batch conversion support\n" +
            "• Drag & drop interface\n" +
            "• Dark mode support\n" +
            "• File renaming options\n" +
            "• Real-time subtitle preview\n\n" +
            "Translation:\n" +
            "• Requires OpenAI API key\n" +
            "• Supports English & Vietnamese\n" +
            "• Uses GPT-4o-mini model\n\n" +
            "Keyboard Shortcuts:\n" +
            "• Ctrl+O: Add files\n" +
            "• Ctrl+Enter: Convert\n" +
            "• Delete: Remove selected\n" +
            "• F1: About\n\n" +
            "Created with ❤️ using Java Swing",
            "About Subtitle Converter Pro",
            JOptionPane.INFORMATION_MESSAGE
        );
    }
    
    // --- SUBTITLE PREVIEW FUNCTIONS ---
    
    private void loadSubtitlePreview(File file) {
        if (file == null || !file.exists()) {
            clearSubtitlePreview();
            return;
        }
        
        translatePreviewBtn.setEnabled(true);
        currentFileLabel.setText("File: " + file.getName());
        currentFileLabel.setForeground(new Color(204, 204, 204));
        
        // Check for translated file
        File translatedFile = getTranslatedFile(file, 
                translateCombo.getSelectedItem().equals("English") ? "en" : "vi");
                
        if (translatedFile.exists()) {
            // If translated, show comparison
            currentFileLabel.setText("File: " + file.getName() + " (Translated)");
            currentFileLabel.setForeground(new Color(144, 238, 144));
            
            try {
                loadTranslatedSubtitlePreview(file, translatedFile);
                return;
            } catch (Exception ex) {
                addTranslationLog("ERROR: Failed to load translated preview for " + file.getName());
                // If error, continue to load original file
            }
        }
        
        // Load original file content
        subtitleEntries.clear();
        subtitlePreviewPanel.removeAll();
        
        try {
            String content = Files.readString(file.toPath());
            String ext = getFileExtension(file.getName()).toLowerCase();
            
            if (ext.equals(".lrc")) {
                loadLrcPreview(content);
            } else {
                // For SRT/VTT
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
                        
                        SubtitleEntry entry = new SubtitleEntry(
                            index, timestamp, textBuilder.toString(), ""
                        );
                        
                        subtitleEntries.add(entry);
                        subtitlePreviewPanel.add(createSubtitleEntryPanel(entry));
                    }
                }
            }
            
            subtitlePreviewPanel.revalidate();
            subtitlePreviewPanel.repaint();
            SwingUtilities.invokeLater(() -> {
                subtitlePreviewScrollPane.getVerticalScrollBar().setValue(0);
            });
            
        } catch (Exception e) {
            e.printStackTrace();
            addTranslationLog("Error loading subtitle preview: " + e.getMessage());
        }
    }
    
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
                
                SubtitleEntry entry = new SubtitleEntry(
                    String.valueOf(index++), 
                    displayTimestamp, 
                    text,
                    ""
                );
                
                subtitleEntries.add(entry);
                subtitlePreviewPanel.add(createSubtitleEntryPanel(entry));
            }
        }
    }
    
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
                
                // Find corresponding translated text
                String translatedText = "";
                if (translatedIndex < translatedLines.length) {
                    Matcher translatedMatcher = timePattern.matcher(translatedLines[translatedIndex]);
                    if (translatedMatcher.matches()) {
                        translatedText = translatedMatcher.group(4).trim();
                        translatedIndex++;
                    }
                }
                
                SubtitleEntry entry = new SubtitleEntry(
                    String.valueOf(index++), 
                    displayTimestamp, 
                    text,
                    translatedText
                );
                
                subtitleEntries.add(entry);
                subtitlePreviewPanel.add(createSubtitleEntryPanel(entry));
            }
        }
    }
    
    private void loadTranslatedSubtitlePreview(File originalFile, File translatedFile) throws Exception {
        subtitleEntries.clear();
        subtitlePreviewPanel.removeAll();
        
        String originalContent = Files.readString(originalFile.toPath());
        String translatedContent = Files.readString(translatedFile.toPath());
        
        String ext = getFileExtension(originalFile.getName()).toLowerCase();
        
        if (ext.equals(".lrc")) {
            loadLrcComparisonPreview(originalContent, translatedContent);
        } else {
            // For SRT/VTT
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
                    
                    SubtitleEntry entry = new SubtitleEntry(
                        index, timestamp, 
                        originalTextBuilder.toString(),
                        translatedTextBuilder.toString()
                    );
                    
                    subtitleEntries.add(entry);
                    subtitlePreviewPanel.add(createSubtitleEntryPanel(entry));
                }
            }
        }
        
        subtitlePreviewPanel.revalidate();
        subtitlePreviewPanel.repaint();
        SwingUtilities.invokeLater(() -> {
            subtitlePreviewScrollPane.getVerticalScrollBar().setValue(0);
        });
    }
    
    private JPanel createSubtitleEntryPanel(SubtitleEntry entry) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(new Color(45, 45, 45));
        panel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createEmptyBorder(5, 5, 5, 5),
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(60, 60, 60), 1),
                BorderFactory.createEmptyBorder(8, 8, 8, 8)
            )
        ));
        panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, panel.getPreferredSize().height));
        
        // Header with index and timestamp
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);
        
        JLabel indexLabel = new JLabel(entry.index);
        indexLabel.setForeground(Color.WHITE);
        indexLabel.setFont(indexLabel.getFont().deriveFont(Font.BOLD));
        
        JLabel timeLabel = new JLabel(entry.timestamp);
        timeLabel.setForeground(new Color(136, 136, 136));
        timeLabel.setFont(timeLabel.getFont().deriveFont(Font.PLAIN, 12f));
        
        header.add(indexLabel, BorderLayout.WEST);
        header.add(timeLabel, BorderLayout.EAST);
        
        // Original text
        JTextArea originalText = new JTextArea(entry.originalText);
        originalText.setEditable(false);
        originalText.setWrapStyleWord(true);
        originalText.setLineWrap(true);
        originalText.setBackground(new Color(45, 45, 45));
        originalText.setForeground(new Color(204, 204, 204));
        originalText.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
        originalText.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        
        panel.add(header);
        panel.add(Box.createVerticalStrut(5));
        panel.add(originalText);
        
        // Add translated text if available
        if (entry.translatedText != null && !entry.translatedText.isEmpty()) {
            JSeparator separator = new JSeparator();
            separator.setForeground(new Color(85, 85, 85));
            
            JTextArea translatedTextArea = new JTextArea(entry.translatedText);
            translatedTextArea.setEditable(false);
            translatedTextArea.setWrapStyleWord(true);
            translatedTextArea.setLineWrap(true);
            translatedTextArea.setBackground(new Color(45, 45, 45));
            translatedTextArea.setForeground(new Color(144, 238, 144)); // Light green
            translatedTextArea.setBorder(BorderFactory.createEmptyBorder(5, 0, 0, 0));
            translatedTextArea.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            
            panel.add(Box.createVerticalStrut(5));
            panel.add(separator);
            panel.add(Box.createVerticalStrut(5));
            panel.add(translatedTextArea);
        }
        
        // Set full width
        panel.setAlignmentX(Component.LEFT_ALIGNMENT);
        originalText.setAlignmentX(Component.LEFT_ALIGNMENT);
        
        return panel;
    }
    
    private void clearSubtitlePreview() {
        subtitleEntries.clear();
        subtitlePreviewPanel.removeAll();
        subtitlePreviewPanel.revalidate();
        subtitlePreviewPanel.repaint();
        
        currentFileLabel.setText("Select a file to preview");
        currentFileLabel.setForeground(new Color(136, 136, 136));
        translatePreviewBtn.setEnabled(false);
    }
    
    private void translateCurrentPreview() {
        if (apiKeyField.getText().trim().isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "Please enter your OpenAI API key for translation.",
                "API Key Required",
                JOptionPane.WARNING_MESSAGE
            );
            return;
        }
        
        if (subtitleEntries.isEmpty()) {
            JOptionPane.showMessageDialog(
                this,
                "No subtitle content to translate in the preview.",
                "No Content",
                JOptionPane.INFORMATION_MESSAGE
            );
            return;
        }
        
        // Create worker for translation
        TranslatePreviewWorker worker = new TranslatePreviewWorker();
        worker.execute();
    }
    
    // --- FILES & CONVERSION ---
    
    private File getConvertedFile(File inputFile) {
        String targetFormat = formatCombo.getSelectedItem().toString().toLowerCase();
        File outputDir = outputPathField.getText().isEmpty() ? 
                       inputFile.getParentFile() : 
                       new File(outputPathField.getText());
        
        String newName = inputFile.getName().replaceAll("\\.(srt|vtt|lrc)$", "." + targetFormat);
        return new File(outputDir, newName);
    }
    
    private File getTranslatedFile(File inputFile, String targetLang) {
        String targetFormat = formatCombo.getSelectedItem().toString().toLowerCase();
        File outputDir = outputPathField.getText().isEmpty() ? 
                       inputFile.getParentFile() : 
                       new File(outputPathField.getText());
        
        String newName = inputFile.getName().replaceAll("\\.(srt|vtt|lrc)$", "_" + targetLang + "." + targetFormat);
        return new File(outputDir, newName);
    }
    
    private boolean convertFile(File inputFile) {
        try {
            String ext = getFileExtension(inputFile.getName()).toLowerCase();
            String targetFormat = formatCombo.getSelectedItem().toString().toLowerCase();
            
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
            setStatus("Error converting " + inputFile.getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    // --- LOG FUNCTIONALITY ---
    
    private void addTranslationLog(String message) {
        String timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        String entry = "[" + timestamp + "] " + message;
        
        SwingUtilities.invokeLater(() -> {
            logListModel.add(0, entry);
            
            // Limit log entries
            if (logListModel.size() > 500) {
                logListModel.removeRange(500, logListModel.size() - 1);
            }
        });
    }
    
    private void saveLogsToFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Save Translation Logs");
        fileChooser.setSelectedFile(new File("translation_log.log"));
        
        int result = fileChooser.showSaveDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            try {
                File file = fileChooser.getSelectedFile();
                StringBuilder content = new StringBuilder();
                
                for (int i = 0; i < logListModel.getSize(); i++) {
                    content.append(logListModel.getElementAt(i)).append("\n\n");
                }
                
                Files.writeString(file.toPath(), content.toString());
                setStatus("Logs saved to " + file.getName());
            } catch (Exception e) {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to save logs: " + e.getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
    
    // --- HELPERS ---
    
    private void setStatus(String message) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(message));
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
    
    // --- CONFIG SAVING/LOADING ---
    
    private void saveConfig() {
        try {
            Properties props = new Properties();
            props.setProperty("openai_api_key", apiKeyField.getText().trim());
            props.setProperty("output_path", outputPathField.getText());
            props.setProperty("format", (String) formatCombo.getSelectedItem());
            props.setProperty("translate", (String) translateCombo.getSelectedItem());
            props.setProperty("rename", String.valueOf(renameCheck.isSelected()));
            props.setProperty("dark_mode", String.valueOf(darkModeCheck.isSelected()));
            props.setProperty("app_mode", "addmode"); // Default app mode
            
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
                    
                    apiKeyField.setText(props.getProperty("openai_api_key", ""));
                    outputPathField.setText(props.getProperty("output_path", ""));
                    
                    String format = props.getProperty("format", "LRC");
                    for (int i = 0; i < formatCombo.getItemCount(); i++) {
                        if (formatCombo.getItemAt(i).equals(format)) {
                            formatCombo.setSelectedIndex(i);
                            break;
                        }
                    }
                    
                    String translate = props.getProperty("translate", "None");
                    for (int i = 0; i < translateCombo.getItemCount(); i++) {
                        if (translateCombo.getItemAt(i).equals(translate)) {
                            translateCombo.setSelectedIndex(i);
                            break;
                        }
                    }
                    
                    renameCheck.setSelected(Boolean.parseBoolean(props.getProperty("rename", "false")));
                    isDarkMode = Boolean.parseBoolean(props.getProperty("dark_mode", "true"));
                    darkModeCheck.setSelected(isDarkMode);
                }
            }
        } catch (Exception e) {
            System.err.println("Cannot load config: " + e.getMessage());
        }
    }
    
    // --- INNER CLASSES ---
    
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
    
    private class LogCellRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, 
                int index, boolean isSelected, boolean cellHasFocus) {
            
            Component c = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            String text = (String) value;
            
            if (!isSelected) {
                if (text.contains("ERROR") || text.contains("Failed")) {
                    c.setForeground(new Color(255, 0, 0)); // Bright red for errors
                } else if (text.contains("Success")) {
                    c.setForeground(new Color(68, 255, 68)); // Bright green for success
                } else {
                    c.setForeground(isDarkMode ? Color.WHITE : Color.BLACK);
                }
            }
            
            return c;
        }
    }
    
    private class ConversionWorker extends SwingWorker<Integer, String> {
        private boolean needTranslation;
        private String targetLang;
        
        public ConversionWorker(boolean needTranslation, String targetLang) {
            this.needTranslation = needTranslation;
            this.targetLang = targetLang;
        }

        @Override
        protected Integer doInBackground() throws Exception {
            int total = fileList.size();
            int success = 0;
            
            // Create translator with selected model
            Translator translator = needTranslation ? 
                    new Translator(apiKeyField.getText().trim(), modelCombo.getSelectedItem().toString()) : null;
            
            // Set up logging
            if (translator != null) {
                translator.setLogCallback(message -> {
                    publish(message);
                });
                publish("Starting translation job with model: " + modelCombo.getSelectedItem().toString());
            }
            
            for (int i = 0; i < total; i++) {
                // Update progress
                setProgress((i * 100) / total);
                File inputFile = fileList.get(i);
                publish("Processing: " + inputFile.getName());
                
                // Convert file
                boolean converted = convertFile(inputFile);
                if (converted && needTranslation) {
                    // Translate the converted file
                    File convertedFile = getConvertedFile(inputFile);
                    if (convertedFile.exists()) {
                        try {
                            publish("Translating: " + inputFile.getName());
                            File translatedFile = getTranslatedFile(inputFile, targetLang);
                            translator.translateSrtFile(convertedFile, translatedFile, targetLang);
                        } catch (Exception e) {
                            publish("Translation failed for: " + inputFile.getName() + " - " + e.getMessage());
                        }
                    }
                }
                
                if (converted) success++;
                
                // Update UI with file status
                final int row = i;
                SwingUtilities.invokeLater(() -> {
                    String status = "Waiting";
                    File file = fileList.get(row);
                    File translatedFile = getTranslatedFile(file, targetLang);
                    
                    if (translatedFile.exists()) {
                        status = "✓ Translated";
                    } else if (getConvertedFile(file).exists() && !getConvertedFile(file).equals(file)) {
                        status = "✓ Converted";
                    }
                    
                    fileTableModel.setValueAt(status, row, 1);
                    fileTable.repaint();
                });
                
                // Small delay for UI responsiveness
                Thread.sleep(100);
            }
            
            // Set final progress
            setProgress(100);
            
            // Log completion
            String message = "Processing completed: " + success + "/" + total + " files successful.";
            publish(message);
            
            return success;
        }
        
        @Override
        protected void process(List<String> chunks) {
            for (String message : chunks) {
                addTranslationLog(message);
                setStatus(message);
            }
        }
        
        @Override
        protected void done() {
            try {
                int success = get();
                progressBar.setVisible(false);
                JOptionPane.showMessageDialog(
                    SwingMainApp.this,
                    "All files have been processed successfully!",
                    "Processing Complete",
                    JOptionPane.INFORMATION_MESSAGE
                );
            } catch (InterruptedException | ExecutionException e) {
                progressBar.setVisible(false);
                setStatus("Processing failed: " + e.getCause().getMessage());
                JOptionPane.showMessageDialog(
                    SwingMainApp.this,
                    "An error occurred during processing. Check logs for details.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
    
    private class TranslatePreviewWorker extends SwingWorker<Void, String> {
        @Override
        protected Void doInBackground() throws Exception {
            Translator translator = new Translator(apiKeyField.getText().trim());
            String targetLang = translateCombo.getSelectedItem().equals("English") ? "en" : "vi";
            
            for (int i = 0; i < subtitleEntries.size(); i++) {
                SubtitleEntry entry = subtitleEntries.get(i);
                if (entry.translatedText == null || entry.translatedText.isEmpty()) {
                    publish(String.format("Translating preview line %d/%d...", i + 1, subtitleEntries.size()));
                    
                    try {
                        String translated = translator.translateText(entry.originalText, targetLang);
                        entry.translatedText = translated;
                        
                        // Update UI for this entry
                        final int index = i;
                        SwingUtilities.invokeLater(() -> {
                            subtitlePreviewPanel.removeAll();
                            for (SubtitleEntry e : subtitleEntries) {
                                subtitlePreviewPanel.add(createSubtitleEntryPanel(e));
                            }
                            subtitlePreviewPanel.revalidate();
                            subtitlePreviewPanel.repaint();
                        });
                        
                    } catch (Exception e) {
                        addTranslationLog("ERROR: Failed to translate line " + (i + 1) + " - " + e.getMessage());
                    }
                }
            }
            
            publish("Preview translation completed.");
            return null;
        }
        
        @Override
        protected void process(List<String> chunks) {
            if (!chunks.isEmpty()) {
                setStatus(chunks.get(chunks.size() - 1));
            }
        }
        
        @Override
        protected void done() {
            try {
                get();
                setStatus("Preview translation completed successfully!");
            } catch (InterruptedException | ExecutionException e) {
                setStatus("Preview translation failed");
                JOptionPane.showMessageDialog(
                    SwingMainApp.this,
                    "Failed to translate preview: " + e.getCause().getMessage(),
                    "Error",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Mặc định là dark mode
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                new SwingMainApp().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}