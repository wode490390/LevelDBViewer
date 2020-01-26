package com.nukkitx.leveldbviewer.ui;

import com.nukkitx.leveldbviewer.util.LevelDBKey;
import com.nukkitx.leveldbviewer.util.Utils;
import com.nukkitx.nbt.NbtUtils;
import com.nukkitx.nbt.stream.NBTInputStream;
import com.nukkitx.leveldbviewer.LevelDBViewer;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.Unpooled;
import org.iq80.leveldb.Options;

import javax.swing.*;
import javax.swing.event.*;
import org.iq80.leveldb.*;
import static org.iq80.leveldb.impl.Iq80DBFactory.*;

import java.awt.*;
import java.io.*;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
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
        nbtValue.setEnabled(false);

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
                            item.getFormattedKey().contains(reg) //||
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
        private byte[] value;

        public DBItem(byte[] key, byte[] value) {
            this.key = key;
            this.dbKey = LevelDBKey.fromBytes(key);
            this.value = value;
        }

        public String getFormattedKey() {
            return dbKey != null ? dbKey.toString(key) : new String(key, StandardCharsets.UTF_8);
        }

        @Override
        public String toString() {
            return getFormattedKey();
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
        STRING{
            @Override
            public byte[] getBytes(String value) {
                return bytes(value);
            }

            @Override
            public String toString(byte[] bytes) {
                return new String(bytes, StandardCharsets.US_ASCII);
            }
        },
        HEX{
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
}
