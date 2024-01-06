package com.nukkitx.leveldbviewer;

import com.nukkitx.leveldbviewer.ui.Viewer;

import javax.swing.*;
import java.math.BigInteger;

public class LevelDBViewer {
    public static boolean DEFAULT_SINGED = false;

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeLater(Viewer::new);
    }

    public static String toHexString(byte[] bytes){
        return DEFAULT_SINGED ? new BigInteger(bytes).toString(16) : new BigInteger(1, bytes).toString(16);
    }
}
