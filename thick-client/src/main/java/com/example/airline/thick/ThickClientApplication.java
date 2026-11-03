package com.example.airline.thick;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.Font;

public class ThickClientApplication {

    public static void main(String[] args) {
        String centralUrl = System.getProperty("central.url",
                System.getenv().getOrDefault("CENTRAL_URL", "http://localhost:8080"));
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                UIManager.put("Label.font", new Font("Microsoft YaHei UI", Font.PLAIN, 13));
                UIManager.put("Button.font", new Font("Microsoft YaHei UI", Font.BOLD, 13));
                UIManager.put("Table.font", new Font("Microsoft YaHei UI", Font.PLAIN, 13));
                UIManager.put("TableHeader.font", new Font("Microsoft YaHei UI", Font.BOLD, 13));
            } catch (Exception ignored) {
                // Fall back to the default Swing look and feel.
            }
            ThickClientFrame frame = new ThickClientFrame(
                    new ThickBookingService(new CentralClient(centralUrl)), centralUrl);
            frame.setVisible(true);
        });
    }
}
