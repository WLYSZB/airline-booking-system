package com.example.airline.thick;

import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JSpinner;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableModel;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.Field;

/**
 * Development-only UI check: launches the real Swing frame, clicks the search
 * button on the EDT, waits for the result table, then captures the window.
 */
public final class ThickClientUiDemo {

    private ThickClientUiDemo() {
    }

    public static void main(String[] args) throws Exception {
        String centralUrl = System.getProperty("central.url",
                System.getenv().getOrDefault("CENTRAL_URL", "http://localhost:8080"));
        String outputPath = System.getProperty("ui.screenshot",
                "D:/软件/WorkBuddy工作空间/体系结构/airline-booking-system/docs/thick-client.png");

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            UIManager.put("Label.font", new Font("Microsoft YaHei UI", Font.PLAIN, 13));
            UIManager.put("Button.font", new Font("Microsoft YaHei UI", Font.BOLD, 13));
            UIManager.put("Table.font", new Font("Microsoft YaHei UI", Font.PLAIN, 13));
            UIManager.put("TableHeader.font", new Font("Microsoft YaHei UI", Font.BOLD, 13));
        } catch (Exception ignored) {
            // Use the default look and feel when the system one is unavailable.
        }

        ThickClientFrame[] frameRef = new ThickClientFrame[1];
        SwingUtilities.invokeAndWait(() -> {
            ThickClientFrame frame = new ThickClientFrame(
                    new ThickBookingService(new CentralClient(centralUrl)), centralUrl);
            frame.setVisible(true);
            frame.toFront();
            frameRef[0] = frame;
        });
        ThickClientFrame frame = frameRef[0];

        Thread.sleep(800);
        Field passengersField = ThickClientFrame.class.getDeclaredField("passengers");
        passengersField.setAccessible(true);
        JSpinner passengerSpinner = (JSpinner) passengersField.get(frame);
        Field sortField = ThickClientFrame.class.getDeclaredField("sortMode");
        sortField.setAccessible(true);
        @SuppressWarnings("unchecked")
        JComboBox<String> sortCombo = (JComboBox<String>) sortField.get(frame);
        SwingUtilities.invokeAndWait(() -> {
            passengerSpinner.setValue(2);
            sortCombo.setSelectedIndex(2);
        });

        Field buttonField = ThickClientFrame.class.getDeclaredField("searchButton");
        buttonField.setAccessible(true);
        JButton searchButton = (JButton) buttonField.get(frame);
        SwingUtilities.invokeAndWait(searchButton::doClick);

        Field modelField = ThickClientFrame.class.getDeclaredField("tableModel");
        modelField.setAccessible(true);
        DefaultTableModel model = (DefaultTableModel) modelField.get(frame);

        long deadline = System.currentTimeMillis() + 20_000;
        int rows = 0;
        while (System.currentTimeMillis() < deadline) {
            int[] current = new int[1];
            SwingUtilities.invokeAndWait(() -> current[0] = model.getRowCount());
            rows = current[0];
            if (rows > 0) {
                break;
            }
            Thread.sleep(250);
        }
        if (rows == 0) {
            throw new IllegalStateException("Thick client returned no rows");
        }

        Thread.sleep(500);
        BufferedImage image = new BufferedImage(
                frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_ARGB);
        SwingUtilities.invokeAndWait(() -> {
            Graphics2D graphics = image.createGraphics();
            try {
                frame.printAll(graphics);
            } finally {
                graphics.dispose();
            }
        });
        File output = new File(outputPath);
        if (output.getParentFile() != null) {
            output.getParentFile().mkdirs();
        }
        ImageIO.write(image, "png", output);
        System.out.println("THICK_CLIENT_UI_READY rows=" + rows + " screenshot=" + output.getAbsolutePath());
        // Keep the frame open for visual inspection; the AWT thread holds the JVM.
    }
}
