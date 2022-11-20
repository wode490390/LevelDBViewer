package com.nukkitx.leveldbviewer.ui;

import com.intellij.uiDesigner.core.GridConstraints;
import com.intellij.uiDesigner.core.GridLayoutManager;
import com.nukkitx.leveldbviewer.util.LevelDBKey;
import com.nukkitx.leveldbviewer.util.Utils;
import com.nukkitx.nbt.NBTInputStream;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.leveldbviewer.LevelDBViewer;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.iq80.leveldb.Options;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.*;
import javax.swing.plaf.FontUIResource;
import javax.swing.text.StyleContext;

import org.iq80.leveldb.*;
import static org.iq80.leveldb.impl.Iq80DBFactory.*;

import java.awt.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.StringJoiner;

public class Viewer {

    private JTextField findField;
    private JList<DBItem> dataList;

    private JFrame frame = new JFrame();

    private JPanel pane;
    private JTextField key;
    private JTextField value;
    private JButton putButton;
    private JTextArea hexValue;
    private JTextArea nbtValue;
    private JLabel lengthLabel;
    private JLabel keyLength;
    private JLabel valueLength;
    private JTextArea hexKey;
    private JTextArea stringKey;
    private JButton deleteButton;
    private JLabel notice;
    private JComboBox<PutType> putType;
    private JCheckBox signedBox;
    private JMenuBar menuBar;
    private JMenu fileMenu;
    private JMenuItem openMenuItem;
    private JMenuItem saveMenuItem;
    private JMenu databaseMenu;
    private JMenuItem putMenuItem;
    private JMenuItem deleteMenuItem;

    private boolean isSet = false;

    private JFileChooser leveldbStore = new JFileChooser();

    private Options options = null;

    public Viewer() {
        leveldbStore.setMultiSelectionEnabled(false);
        leveldbStore.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);

        String os = System.getProperty("os.name");
        if ("Windows 10".equals(os)) {
            String appData = System.getenv("LocalAppData");
            File worldsDir = new File(appData + "\\Packages\\Microsoft.MinecraftUWP_8wekyb3d8bbwe\\LocalState\\games\\com.mojang\\minecraftWorlds");
            if (worldsDir.exists() && worldsDir.isDirectory()) {
                leveldbStore.setCurrentDirectory(worldsDir);
            }
        }
//        nbtValue.setEnabled(false);

        putButton.setEnabled(false);
        key.setEnabled(false);
        value.setEnabled(false);
        findField.setEnabled(false);
        deleteButton.setEnabled(false);
        saveMenuItem.setEnabled(false);
        putType.setEnabled(false);
        putType.setEditable(false);
        signedBox.setEnabled(false);

        openMenuItem.addActionListener(e -> {
            if (openMenuItem.isEnabled()) {
                openMenuItem.setEnabled(false);
                new Thread(() -> {
                    if (leveldbStore.showOpenDialog(pane) == JFileChooser.APPROVE_OPTION) {
                        File select = leveldbStore.getSelectedFile();
                        if (select.isDirectory()) {
                            new OpenLevelDBDialog(Viewer.this, select);
                            openDatabase(select);
                            frame.setTitle("LevelDB Viewer (" + select.getAbsolutePath() + ")");
                        } else {
                            JOptionPane.showMessageDialog(pane, "The selecting item must be a directory", "Unable to load database", JOptionPane.WARNING_MESSAGE);
                        }
                    } else {
                        openMenuItem.setEnabled(true);
                    }
                }).start();
            }
        });

        deleteButton.addActionListener(e -> {
            if (dataList.getSelectedValue() != null) {
                delete(dataList.getSelectedValue().key);
            }
            openDatabase(leveldbStore.getSelectedFile());
        });

        putButton.addActionListener(e -> {
            put(((PutType) putType.getSelectedItem()).getBytes(key.getText()), ((PutType) putType.getSelectedItem()).getBytes(value.getText()));
            openDatabase(leveldbStore.getSelectedFile());
        });

        findField.addActionListener(e -> openDatabase(leveldbStore.getSelectedFile()));
        findField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                openDatabase(leveldbStore.getSelectedFile());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                openDatabase(leveldbStore.getSelectedFile());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                openDatabase(leveldbStore.getSelectedFile());
            }
        });
        findField.getDocument().addUndoableEditListener(e -> openDatabase(leveldbStore.getSelectedFile()));

        hexKey.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update(hexKey);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update(hexKey);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update(hexKey);
            }
        });
        hexKey.getDocument().addUndoableEditListener(e -> update(hexKey));

        stringKey.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update(stringKey);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update(stringKey);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update(stringKey);
            }
        });
        stringKey.getDocument().addUndoableEditListener(e -> update(stringKey));

        hexValue.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update(hexValue);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update(hexValue);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update(hexValue);
            }
        });
        hexValue.getDocument().addUndoableEditListener(e -> update(hexValue));

        nbtValue.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                update(nbtValue);
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                update(nbtValue);
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                update(nbtValue);
            }
        });
        nbtValue.getDocument().addUndoableEditListener(e -> update(nbtValue));

        saveMenuItem.addActionListener(e -> {
            save();
            openDatabase(leveldbStore.getSelectedFile());
        });

        dataList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        dataList.addListSelectionListener(e -> {
            DBItem item = dataList.getSelectedValue();
            if (item != null) {
                try (ByteArrayInputStream inputStream = new ByteArrayInputStream(item.value);
                     NBTInputStream stream = NbtUtils.createReaderLE(inputStream)) {
                    if (item.dbKey == LevelDBKey.SUBCHUNK_PREFIX) {
                        throw new UnsupportedOperationException();
                    }
                    StringJoiner joiner = new StringJoiner("\n--------------------------------------------\n");
                    while (inputStream.available() > 0) {
                        joiner.add(stream.readTag().toString());
                    }
                    nbtValue.setText(joiner.toString());
                    nbtValue.setVisible(true);
                } catch (Exception ex) {
                    nbtValue.setVisible(false);
                    //nbtValue.setText(cutToLine(new String(item.value), 64));
                }
                hexValue.setText(ByteBufUtil.prettyHexDump(Unpooled.wrappedBuffer(item.value)));
                hexKey.setText(cutToLine(LevelDBViewer.toHexString(item.key), 64));
                stringKey.setText(cutToLine(new String(item.key), 64));

                lengthLabel.setText(String.valueOf(item.value.length + item.key.length));
                keyLength.setText(String.valueOf(item.key.length));
                valueLength.setText(String.valueOf(item.value.length));
            }
        });

        signedBox.addActionListener(e -> {
            LevelDBViewer.DEFAULT_SINGED = signedBox.isSelected();
            int i = dataList.getSelectedIndex();
            dataList.clearSelection();
            dataList.updateUI();
            dataList.setSelectedIndex(i);
            update(hexKey);
            update(hexValue);
        });

        for (PutType t : PutType.values()) {
            putType.addItem(t);
        }
        putType.setSelectedItem(PutType.STRING);
        putType.addItemListener(e -> openDatabase(leveldbStore.getSelectedFile()));
        putType.addActionListener(e -> openDatabase(leveldbStore.getSelectedFile()));

        frame.setLocationByPlatform(true);
        frame.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        frame.setContentPane(pane);
        frame.setTitle("LevelDB Viewer");
        frame.pack();
        frame.setVisible(true);
    }

    public void update(JTextArea area) {
        DBItem item = dataList.getSelectedValue();
        if (item != null && !isSet) {
            isSet = true;
            try {
                if (area == hexKey) {
                    if (area.getText().isEmpty()) {
                        return;
                    }
                    stringKey.setText(cutToLine(new String(item.key), 64));
                    dataList.updateUI();
                } else if (area == stringKey) {
                    if (area.getText().isEmpty()) {
                        return;
                    }
                    hexKey.setText(cutToLine(LevelDBViewer.toHexString(item.key), 64));
                    dataList.updateUI();
                }
                notice.setVisible(false);
                notice.setText("");
                saveMenuItem.setEnabled(true);
            } catch (Exception e) {
                notice.setVisible(true);
                notice.setText("Invalid number!");
            } finally {
                lengthLabel.setText(String.valueOf(item.value.length + item.key.length));
                keyLength.setText(String.valueOf(item.key.length));
                valueLength.setText(String.valueOf(item.value.length));
                isSet = false;
            }
        }
    }

    public void save() {
        boolean isNoticed = false;
        if (!notice.isVisible()) {
            isNoticed = true;
            notice.setVisible(true);
            notice.setText("Saving...");
        }
        if (getOptions() != null) {
            DB database = null;
            try {
                database = factory.open(this.leveldbStore.getSelectedFile(), getOptions());
                DBIterator iterator = database.iterator();
                HashSet<byte[]> keys = new HashSet<>();

                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    keys.add(iterator.peekNext().getKey());
                }
                iterator.close();

                for (byte[] key : keys) {
                    database.delete(key);
                }

                for (int i = 0; i < dataList.getModel().getSize(); ++i) {
                    DBItem item = dataList.getModel().getElementAt(i);
                    database.put(item.key, item.value);
                }
            } catch (Exception e) {
                JOptionPane.showMessageDialog(pane, "Unable to open database:\n" + e);
                e.printStackTrace();
            } finally {
                if (database != null) {
                    try {
                        database.close();
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(pane, "Unable to close database:\n" + e);
                        e.printStackTrace();
                    }
                }
                saveMenuItem.setEnabled(false);
            }
        }
        if (isNoticed) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            notice.setVisible(false);
            notice.setText("");
        }
    }

    public void openDatabase(File select) {
        if (getOptions() != null) {
            DB database = null;
            try {
                database = factory.open(select, getOptions());
                DBIterator iterator = database.iterator();

                ArrayList<DBItem> data = new ArrayList<>();

                String reg = findField.getText().trim();

                for (iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
                    DBItem item = new DBItem(iterator.peekNext().getKey(), iterator.peekNext().getValue());
                    if (reg.isEmpty() ||
//                            new BigInteger(iterator.peekNext().getKey()).toString(16).contains(reg) ||
//                            LevelDBViewer.toHexString(iterator.peekNext().getKey()).contains(reg) ||
//                            new BigInteger(iterator.peekNext().getValue()).toString(16).contains(reg) ||
//                            LevelDBViewer.toHexString(iterator.peekNext().getValue()).contains(reg) ||
                            item.getFormattedKey().toLowerCase().contains(reg.toLowerCase()) //||
//                            new String(iterator.peekNext().getValue()).contains(reg)
                    ) {
                        data.add(item);
                    }
                }

                iterator.close();

                // data.sort(null);

                frame.getRootPane().setDefaultButton(putButton);

                dataList.getSelectionModel().clearSelection();
                dataList.setListData(data.toArray(new DBItem[data.size()]));

                putButton.setEnabled(true);
                key.setEnabled(true);
                value.setEnabled(true);
                findField.setEnabled(true);
                deleteButton.setEnabled(true);
                putType.setEnabled(true);
                signedBox.setEnabled(true);
                //saveButton.setEnabled(true);

                hexValue.setText("");
                nbtValue.setText("");
                hexKey.setText("");
                stringKey.setText("");

                lengthLabel.setText("");
                keyLength.setText("");
                valueLength.setText("");
            } catch (Exception e) {
                JOptionPane.showMessageDialog(pane, "Unable to open database:\n" + e);
                e.printStackTrace();
            } finally {
                if (database != null) {
                    try {
                        database.close();
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(pane, "Unable to close database:\n" + e);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void put(byte[] key, byte[] value) {
        if (getOptions() != null) {
            DB database = null;
            try {
                database = factory.open(this.leveldbStore.getSelectedFile(), getOptions());
                database.put(key, value);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(pane, "Unable to open database:\n" + e);
                e.printStackTrace();
            } finally {
                if (database != null) {
                    try {
                        database.close();
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(pane, "Unable to close database:\n" + e);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public void delete(byte[] key) {
        if (getOptions() != null) {
            DB database = null;
            try {
                database = factory.open(this.leveldbStore.getSelectedFile(), getOptions());
                database.delete(key);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(pane, "Unable to open database:\n" + e);
                e.printStackTrace();
            } finally {
                if (database != null) {
                    try {
                        database.close();
                    } catch (IOException e) {
                        JOptionPane.showMessageDialog(pane, "Unable to close database:\n" + e);
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    public Options getOptions() {
        return options;
    }

    public void setOptions(Options options) {
        this.options = options;
    }

    public String cutToLine(String str, int lineChars) {
        String eol = "\n";
        str = str.replace("\n", "\\n");
        StringBuilder builder = new StringBuilder();
        int pointer = 0;
        for (char c : str.toCharArray()) {
            if ((++pointer % lineChars) == 0) {
                builder.append(eol);
            }
            builder.append(c);
        }
        return builder.toString();
    }

    public static class DBItem implements Comparable<DBItem> {

        private byte[] key;
        private LevelDBKey dbKey;
        private byte dimension;
        private byte[] value;

        public DBItem(byte[] key, byte[] value) {
            this.key = key;
            this.dbKey = LevelDBKey.fromBytes(key);
            this.dimension = LevelDBKey.getDimension(key);
            this.value = value;
        }

        public String getFormattedKey() {
            return dbKey != null ? dbKey.toString(key) : new String(key, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return dimension + " " + getFormattedKey();
        }

        @Override
        public int compareTo(DBItem that) {
            int compare = Objects.compare(this.dbKey, that.dbKey, Utils.nullComparator());
            if (compare == 0) {
                if (dbKey != null) {
                    compare = Long.compare(this.dbKey.getChunkXZ(key), that.dbKey.getChunkXZ(key));
                    if (compare == 0) {
                        return this.dbKey.compareTo(that.dbKey);
                    }
                } else {
                    String thisKey = new String(this.key, StandardCharsets.UTF_8);
                    String thatKey = new String(that.key, StandardCharsets.UTF_8);
                    return thisKey.compareTo(thatKey);
                }
            }
            return compare;
        }
    }

    public enum PutType {
        STRING {
            @Override
            public byte[] getBytes(String value) {
                return bytes(value);
            }

            @Override
            public String toString(byte[] bytes) {
                return new String(bytes, StandardCharsets.US_ASCII);
            }
        },
        HEX {
            @Override
            public byte[] getBytes(String value) {
                return new BigInteger(value, 16).toByteArray();
            }

            @Override
            public String toString(byte[] bytes) {
                return LevelDBViewer.toHexString(bytes);
            }
        };

        public abstract byte[] getBytes(String value);

        public abstract String toString(byte[] bytes);
    }

    {
// GUI initializer generated by IntelliJ IDEA GUI Designer
// >>> IMPORTANT!! <<<
// DO NOT EDIT OR ADD ANY CODE HERE!
        $$$setupUI$$$();
    }

    /**
     * Method generated by IntelliJ IDEA GUI Designer
     * >>> IMPORTANT!! <<<
     * DO NOT edit this method OR call it in your code!
     *
     * @noinspection ALL
     */
    private void $$$setupUI$$$() {
        pane = new JPanel();
        pane.setLayout(new BorderLayout(0, 0));
        final JPanel panel1 = new JPanel();
        panel1.setLayout(new GridLayoutManager(1, 9, new Insets(0, 0, 0, 0), -1, -1));
        pane.add(panel1, BorderLayout.NORTH);
        key = new JTextField();
        panel1.add(key, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JLabel label1 = new JLabel();
        label1.setText("Key");
        panel1.add(label1, new GridConstraints(0, 2, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label2 = new JLabel();
        label2.setText("Value");
        panel1.add(label2, new GridConstraints(0, 3, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        value = new JTextField();
        panel1.add(value, new GridConstraints(0, 4, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        putButton = new JButton();
        putButton.setEnabled(false);
        putButton.setText("Put");
        panel1.add(putButton, new GridConstraints(0, 5, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        deleteButton = new JButton();
        deleteButton.setEnabled(false);
        deleteButton.setText("Delete");
        panel1.add(deleteButton, new GridConstraints(0, 6, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        putType = new JComboBox();
        putType.setEnabled(false);
        panel1.add(putType, new GridConstraints(0, 7, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        signedBox = new JCheckBox();
        signedBox.setEnabled(false);
        signedBox.setText("Signed");
        panel1.add(signedBox, new GridConstraints(0, 8, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        menuBar = new JMenuBar();
        menuBar.setLayout(new FlowLayout(FlowLayout.LEFT, 0, 0));
        panel1.add(menuBar, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        fileMenu = new JMenu();
        fileMenu.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));
        fileMenu.setText("File");
        menuBar.add(fileMenu);
        openMenuItem = new JMenuItem();
        openMenuItem.setText("Open");
        fileMenu.add(openMenuItem);
        saveMenuItem = new JMenuItem();
        saveMenuItem.setText("Save");
        fileMenu.add(saveMenuItem);
        databaseMenu = new JMenu();
        databaseMenu.setLayout(new FlowLayout(FlowLayout.CENTER, 10, 5));
        databaseMenu.setText("Database");
        menuBar.add(databaseMenu);
        putMenuItem = new JMenuItem();
        putMenuItem.setText("Put");
        databaseMenu.add(putMenuItem);
        deleteMenuItem = new JMenuItem();
        deleteMenuItem.setText("Delete");
        databaseMenu.add(deleteMenuItem);
        final JSplitPane splitPane1 = new JSplitPane();
        pane.add(splitPane1, BorderLayout.CENTER);
        final JPanel panel2 = new JPanel();
        panel2.setLayout(new GridLayoutManager(4, 1, new Insets(10, 0, 0, 10), -1, -1));
        panel2.setFocusTraversalPolicyProvider(false);
        splitPane1.setRightComponent(panel2);
        panel2.setBorder(BorderFactory.createTitledBorder(null, "Detail", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel3 = new JPanel();
        panel3.setLayout(new GridLayoutManager(3, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel3, new GridConstraints(3, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label3 = new JLabel();
        label3.setText("Total Length:");
        panel3.add(label3, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        lengthLabel = new JLabel();
        lengthLabel.setText("0");
        panel3.add(lengthLabel, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label4 = new JLabel();
        label4.setText("Key Length:");
        panel3.add(label4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        keyLength = new JLabel();
        keyLength.setText("0");
        panel3.add(keyLength, new GridConstraints(1, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JLabel label5 = new JLabel();
        label5.setText("Value Length:");
        panel3.add(label5, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        valueLength = new JLabel();
        valueLength.setText("0");
        panel3.add(valueLength, new GridConstraints(2, 1, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JPanel panel4 = new JPanel();
        panel4.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        panel2.add(panel4, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        panel4.setBorder(BorderFactory.createTitledBorder(null, "Key", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, null));
        final JPanel panel5 = new JPanel();
        panel5.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(panel5, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label6 = new JLabel();
        label6.setText("Hex   ");
        panel5.add(label6, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane1 = new JScrollPane();
        panel5.add(scrollPane1, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        hexKey = new JTextArea();
        hexKey.setEditable(true);
        scrollPane1.setViewportView(hexKey);
        final JPanel panel6 = new JPanel();
        panel6.setLayout(new GridLayoutManager(1, 2, new Insets(0, 0, 0, 0), -1, -1));
        panel4.add(panel6, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label7 = new JLabel();
        label7.setText("String");
        panel6.add(label7, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JScrollPane scrollPane2 = new JScrollPane();
        panel6.add(scrollPane2, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        stringKey = new JTextArea();
        stringKey.setEditable(true);
        scrollPane2.setViewportView(stringKey);
        notice = new JLabel();
        notice.setText("");
        notice.setVisible(true);
        panel2.add(notice, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_WEST, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        final JTabbedPane tabbedPane1 = new JTabbedPane();
        tabbedPane1.setEnabled(true);
        tabbedPane1.setTabLayoutPolicy(0);
        panel2.add(tabbedPane1, new GridConstraints(2, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, new Dimension(200, 200), null, 0, false));
        final JScrollPane scrollPane3 = new JScrollPane();
        tabbedPane1.addTab("Hex", scrollPane3);
        hexValue = new JTextArea();
        scrollPane3.setViewportView(hexValue);
        final JScrollPane scrollPane4 = new JScrollPane();
        tabbedPane1.addTab("NBT", scrollPane4);
        nbtValue = new JTextArea();
        scrollPane4.setViewportView(nbtValue);
        final JPanel panel7 = new JPanel();
        panel7.setLayout(new GridLayoutManager(2, 1, new Insets(0, 0, 0, 0), -1, -1));
        splitPane1.setLeftComponent(panel7);
        final JPanel panel8 = new JPanel();
        panel8.setLayout(new GridLayoutManager(1, 2, new Insets(5, 5, 0, 5), -1, -1));
        panel7.add(panel8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, 1, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_CAN_GROW, null, null, null, 0, false));
        final JLabel label8 = new JLabel();
        label8.setText("Find:");
        panel8.add(label8, new GridConstraints(0, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_NONE, GridConstraints.SIZEPOLICY_FIXED, GridConstraints.SIZEPOLICY_FIXED, null, null, null, 0, false));
        findField = new JTextField();
        findField.setText("");
        findField.setToolTipText("Database key");
        panel8.add(findField, new GridConstraints(0, 1, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_HORIZONTAL, GridConstraints.SIZEPOLICY_CAN_GROW, GridConstraints.SIZEPOLICY_FIXED, null, new Dimension(150, -1), null, 0, false));
        final JScrollPane scrollPane5 = new JScrollPane();
        panel7.add(scrollPane5, new GridConstraints(1, 0, 1, 1, GridConstraints.ANCHOR_CENTER, GridConstraints.FILL_BOTH, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, GridConstraints.SIZEPOLICY_CAN_SHRINK | GridConstraints.SIZEPOLICY_WANT_GROW, null, null, null, 0, false));
        dataList = new JList();
        Font dataListFont = this.$$$getFont$$$("DialogInput", -1, -1, dataList.getFont());
        if (dataListFont != null) dataList.setFont(dataListFont);
        dataList.setVisibleRowCount(20);
        scrollPane5.setViewportView(dataList);
    }

    /**
     * @noinspection ALL
     */
    private Font $$$getFont$$$(String fontName, int style, int size, Font currentFont) {
        if (currentFont == null) return null;
        String resultName;
        if (fontName == null) {
            resultName = currentFont.getName();
        } else {
            Font testFont = new Font(fontName, Font.PLAIN, 10);
            if (testFont.canDisplay('a') && testFont.canDisplay('1')) {
                resultName = fontName;
            } else {
                resultName = currentFont.getName();
            }
        }
        Font font = new Font(resultName, style >= 0 ? style : currentFont.getStyle(), size >= 0 ? size : currentFont.getSize());
        boolean isMac = System.getProperty("os.name", "").toLowerCase(Locale.ENGLISH).startsWith("mac");
        Font fontWithFallback = isMac ? new Font(font.getFamily(), font.getStyle(), font.getSize()) : new StyleContext().getFont(font.getFamily(), font.getStyle(), font.getSize());
        return fontWithFallback instanceof FontUIResource ? fontWithFallback : new FontUIResource(fontWithFallback);
    }

    /**
     * @noinspection ALL
     */
    public JComponent $$$getRootComponent$$$() {
        return pane;
    }
}
