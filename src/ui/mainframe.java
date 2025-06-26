package ui;

import logic.Converter;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.dnd.*;
import java.awt.event.*;
import java.io.File;
import java.util.List;

public class mainframe extends JFrame {
    private JTextField fileField;
    private JButton browseBtn, convertBtn;
    private JCheckBox renameCheck;
    private DefaultListModel<File> fileListModel;
    private JList<File> fileList;

    public mainframe() {
        setTitle("Subtitle Converter");
        setSize(500, 300);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        fileField = new JTextField(20);
        browseBtn = new JButton("Browse...");
        convertBtn = new JButton("Convert");
        renameCheck = new JCheckBox("Rename file (keep first number)");

        fileListModel = new DefaultListModel<>();
        fileList = new JList<>(fileListModel);
        fileList.setVisibleRowCount(6);
        fileList.setFixedCellWidth(400);
        JScrollPane listScroll = new JScrollPane(fileList);

        // Drag & Drop support
        fileList.setDropTarget(new DropTarget() {
            public synchronized void drop(DropTargetDropEvent evt) {
                try {
                    evt.acceptDrop(DnDConstants.ACTION_COPY);
                    List<File> droppedFiles = (List<File>)
                        evt.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
                    for (File file : droppedFiles) {
                        if (!fileListModel.contains(file)) {
                            fileListModel.addElement(file);
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        });

        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());

        JPanel topPanel = new JPanel();
        topPanel.add(new JLabel("Subtitle file:"));
        topPanel.add(fileField);
        topPanel.add(browseBtn);

        JPanel midPanel = new JPanel();
        midPanel.setLayout(new BorderLayout());
        midPanel.add(new JLabel("Batch files (drag & drop here):"), BorderLayout.NORTH);
        midPanel.add(listScroll, BorderLayout.CENTER);

        JPanel bottomPanel = new JPanel();
        bottomPanel.add(renameCheck);
        bottomPanel.add(convertBtn);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(midPanel, BorderLayout.CENTER);
        panel.add(bottomPanel, BorderLayout.SOUTH);

        add(panel);

        browseBtn.addActionListener(e -> {
            JFileChooser chooser = new JFileChooser();
            chooser.setMultiSelectionEnabled(true);
            int result = chooser.showOpenDialog(this);
            if (result == JFileChooser.APPROVE_OPTION) {
                File[] files = chooser.getSelectedFiles();
                if (files.length == 1) {
                    fileField.setText(files[0].getAbsolutePath());
                }
                for (File file : files) {
                    if (!fileListModel.contains(file)) {
                        fileListModel.addElement(file);
                    }
                }
            }
        });

        convertBtn.addActionListener(e -> {
            // Nếu có file trong danh sách batch thì xử lý batch
            if (!fileListModel.isEmpty()) {
                for (int i = 0; i < fileListModel.size(); i++) {
                    File inputFile = fileListModel.get(i);
                    convertFile(inputFile);
                }
                JOptionPane.showMessageDialog(this, "Batch conversion completed!");
                return;
            }
            // Nếu không thì xử lý fileField
            String path = fileField.getText();
            if (path.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select a file.");
                return;
            }
            File inputFile = new File(path);
            convertFile(inputFile);
        });
    }

    private void convertFile(File inputFile) {
        String ext = getFileExtension(inputFile.getName()).toLowerCase();
        File lrcFile = new File(inputFile.getParent(), inputFile.getName().replaceAll("\\.(srt|vtt)$", ".lrc"));
        try {
            if (ext.equals(".srt")) {
                Converter.convertSrtToLrc(inputFile, lrcFile);
            } else if (ext.equals(".vtt")) {
                Converter.convertVttToLrc(inputFile, lrcFile);
            } else {
                JOptionPane.showMessageDialog(this, "Only SRT or VTT files are supported: " + inputFile.getName());
                return;
            }
            if (renameCheck.isSelected()) {
                Converter.renameFileKeepFirstNumber(lrcFile);
            }
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Error: " + ex.getMessage());
        }
    }

    private String getFileExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return (dot != -1) ? filename.substring(dot) : "";
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new mainframe().setVisible(true));
    }
}
