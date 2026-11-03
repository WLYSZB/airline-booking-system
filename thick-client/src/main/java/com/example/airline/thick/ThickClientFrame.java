package com.example.airline.thick;

import com.example.airline.model.AirportInfo;
import com.example.airline.model.BookedLeg;
import com.example.airline.model.Passenger;
import com.example.airline.model.Receipt;
import com.example.airline.model.SeatPreference;
import com.example.airline.model.SortMode;
import com.example.airline.model.TripOffer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ThickClientFrame extends JFrame {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final ThickBookingService service;
    private final String centralUrl;
    private final JComboBox<String> origin = new JComboBox<>(
            new String[]{"武汉 WUH", "北京 PEK", "上海 PVG", "广州 CAN", "成都 CTU"});
    private final JComboBox<String> destination = new JComboBox<>(
            new String[]{"北京 PEK", "武汉 WUH", "上海 PVG", "广州 CAN", "成都 CTU"});
    private final JTextField date = new JTextField("2026-11-15", 11);
    private final JSpinner passengers = new JSpinner(new SpinnerNumberModel(1, 1, 6, 1));
    private final JComboBox<String> sortMode = new JComboBox<>(
            new String[]{"价格优先", "总时长优先", "出发时间最早"});
    private final JCheckBox airlineA = new JCheckBox("楚天航空 A", true);
    private final JCheckBox airlineB = new JCheckBox("东湖航空 B", true);
    private final JCheckBox airlineC = new JCheckBox("长江航空 C", true);
    private final DefaultTableModel tableModel = new DefaultTableModel(
            new String[]{"行程", "承运航司", "出发", "总时长", "总价", "定价说明"}, 0) {
        @Override
        public boolean isCellEditable(int row, int column) {
            return false;
        }
    };
    private final JTable table = new JTable(tableModel);
    private final JButton searchButton = new JButton("查询行程");
    private final JButton bookButton = new JButton("预订选中行程");
    private final JLabel status = new JLabel("就绪");
    private final JTextField referenceField = new JTextField(18);
    private final JTextArea receiptArea = new JTextArea();
    private final List<TripOffer> currentTrips = new ArrayList<>();
    private Map<String, AirportInfo> airports = Map.of();

    public ThickClientFrame(ThickBookingService service, String centralUrl) {
        super("云翼机票 · Java Thick Client");
        this.service = service;
        this.centralUrl = centralUrl;
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1080, 760));
        setSize(1180, 820);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(12, 12));
        getContentPane().setBackground(new Color(242, 247, 250));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildCenter(), BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        bindActions();
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBorder(BorderFactory.createEmptyBorder(18, 22, 8, 22));
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("云翼机票 · 厚客户端");
        title.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 26));
        title.setForeground(new Color(17, 46, 68));
        JLabel subtitle = new JLabel("Java Stand-alone Client · 联程与预订协调在客户端执行");
        subtitle.setForeground(new Color(91, 112, 129));
        titleBox.add(title);
        titleBox.add(Box.createVerticalStrut(5));
        titleBox.add(subtitle);
        header.add(titleBox, BorderLayout.WEST);
        JLabel badge = new JLabel("协调逻辑：Java Client  |  航司直连：是  |  DB 凭据：无");
        badge.setOpaque(true);
        badge.setBackground(new Color(225, 240, 246));
        badge.setForeground(new Color(10, 94, 125));
        badge.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        header.add(badge, BorderLayout.EAST);
        return header;
    }

    private JPanel buildCenter() {
        JPanel center = new JPanel(new BorderLayout(12, 12));
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(0, 22, 0, 22));
        center.add(buildSearchPanel(), BorderLayout.NORTH);

        table.setRowHeight(36);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.getColumnModel().getColumn(0).setPreferredWidth(230);
        table.getColumnModel().getColumn(1).setPreferredWidth(90);
        table.getColumnModel().getColumn(2).setPreferredWidth(105);
        table.getColumnModel().getColumn(3).setPreferredWidth(90);
        table.getColumnModel().getColumn(4).setPreferredWidth(190);
        table.getColumnModel().getColumn(5).setPreferredWidth(200);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(BorderFactory.createLineBorder(new Color(218, 228, 235)));

        JPanel lower = new JPanel(new BorderLayout(10, 10));
        lower.setOpaque(false);
        JPanel bookingBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        bookingBar.setOpaque(false);
        bookingBar.add(bookButton);
        bookingBar.add(new JLabel("预订编号"));
        bookingBar.add(referenceField);
        JButton lookup = new JButton("查询收据");
        JButton cancel = new JButton("取消预订");
        bookingBar.add(lookup);
        bookingBar.add(cancel);
        lower.add(bookingBar, BorderLayout.NORTH);

        receiptArea.setEditable(false);
        receiptArea.setRows(7);
        receiptArea.setFont(new Font("Microsoft YaHei UI", Font.PLAIN, 13));
        receiptArea.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        receiptArea.setText("预订成功后，收据与航段状态显示在这里。\n取消操作由客户端逐个航司发起，再由中央服务登记最终状态。");
        lower.add(new JScrollPane(receiptArea), BorderLayout.CENTER);

        lookup.addActionListener(event -> lookupReceipt());
        cancel.addActionListener(event -> cancelReceipt());

        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, tableScroll, lower);
        splitPane.setResizeWeight(0.62);
        splitPane.setBorder(null);
        center.add(splitPane, BorderLayout.CENTER);
        return center;
    }

    private JPanel buildSearchPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(Color.WHITE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(218, 228, 235)),
                BorderFactory.createEmptyBorder(14, 16, 14, 16)));

        JPanel fields = new JPanel(new GridLayout(1, 5, 12, 0));
        fields.setOpaque(false);
        fields.add(field("出发城市", origin));
        fields.add(field("目的城市", destination));
        fields.add(field("出发日期", date));
        fields.add(field("乘客人数", passengers));
        fields.add(field("排序方式", sortMode));
        panel.add(fields);
        panel.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 0));
        actions.setOpaque(false);
        actions.add(new JLabel("航空公司："));
        actions.add(airlineA);
        actions.add(airlineB);
        actions.add(airlineC);
        actions.add(Box.createHorizontalStrut(18));
        searchButton.setFont(new Font("Microsoft YaHei UI", Font.BOLD, 13));
        actions.add(searchButton);
        panel.add(actions);
        return panel;
    }

    private JPanel field(String label, java.awt.Component component) {
        JPanel wrapper = new JPanel(new BorderLayout(0, 5));
        wrapper.setOpaque(false);
        JLabel caption = new JLabel(label);
        caption.setForeground(new Color(96, 112, 125));
        wrapper.add(caption, BorderLayout.NORTH);
        wrapper.add(component, BorderLayout.CENTER);
        return wrapper;
    }

    private JPanel buildStatusBar() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(2, 24, 13, 24));
        status.setForeground(new Color(75, 93, 108));
        panel.add(status, BorderLayout.WEST);
        JLabel endpoint = new JLabel("Central: " + centralUrl);
        endpoint.setForeground(new Color(130, 145, 156));
        panel.add(endpoint, BorderLayout.EAST);
        return panel;
    }

    private void bindActions() {
        searchButton.addActionListener(event -> search());
        bookButton.addActionListener(event -> openBookingDialog());
    }

    private void search() {
        String originCode = selectedAirportCode(origin);
        String destinationCode = selectedAirportCode(destination);
        if (originCode.equals(destinationCode)) {
            JOptionPane.showMessageDialog(this, "出发城市和目的城市不能相同。", "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        List<String> codes = new ArrayList<>();
        if (airlineA.isSelected()) codes.add("A");
        if (airlineB.isSelected()) codes.add("B");
        if (airlineC.isSelected()) codes.add("C");
        if (codes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "至少选择一家航空公司。", "输入错误", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setBusy(true, "客户端正在并行查询航司并组合联程…");
        int count = (Integer) passengers.getValue();
        SortMode mode = switch (sortMode.getSelectedIndex()) {
            case 1 -> SortMode.TIME;
            case 2 -> SortMode.DEPARTURE;
            default -> SortMode.PRICE;
        };
        new SwingWorker<ThickBookingService.SearchPayload, Void>() {
            @Override
            protected ThickBookingService.SearchPayload doInBackground() {
                return service.search(originCode, destinationCode, date.getText().trim(), count, codes, mode);
            }

            @Override
            protected void done() {
                try {
                    ThickBookingService.SearchPayload payload = get();
                    showTrips(payload);
                } catch (Exception exception) {
                    showError("查询失败", rootMessage(exception));
                } finally {
                    setBusy(false, "就绪");
                }
            }
        }.execute();
    }

    private String airportLabel(String code) {
        AirportInfo airport = airports.get(code);
        return airport == null ? code : airport.city() + " " + code;
    }

    private String selectedAirportCode(JComboBox<String> combo) {
        String label = String.valueOf(combo.getSelectedItem());
        int separator = label.lastIndexOf(' ');
        return separator >= 0 ? label.substring(separator + 1) : label;
    }

    private void showTrips(ThickBookingService.SearchPayload payload) {
        airports = payload.airports();
        currentTrips.clear();
        currentTrips.addAll(payload.trips());
        tableModel.setRowCount(0);
        for (TripOffer trip : payload.trips()) {
            String route = airportLabel(trip.legs().get(0).origin()) + " → "
                    + airportLabel(trip.legs().get(trip.legs().size() - 1).destination());
            String departure = DATE_TIME.format(trip.legs().get(0).departureTime());
            String duration = trip.totalDurationMinutes() / 60 + "h " + trip.totalDurationMinutes() % 60 + "m";
            String price = trip.passengers() > 1
                    ? "¥" + trip.totalPrice().toPlainString() + "（单人 ¥"
                    + trip.pricePerPassenger().toPlainString() + "×" + trip.passengers() + "）"
                    : "¥" + trip.totalPrice().toPlainString();
            tableModel.addRow(new Object[]{
                    route,
                    trip.airlinesLabel(),
                    departure,
                    duration,
                    price,
                    trip.pricingNote()
            });
        }
        String message = payload.trips().isEmpty()
                ? "没有符合条件的行程"
                : "客户端已组合 " + payload.trips().size() + " 个行程";
        if (!payload.warnings().isEmpty()) {
            message += "；警告：" + String.join("；", payload.warnings());
        }
        status.setText(message);
    }

    private void openBookingDialog() {
        int row = table.getSelectedRow();
        if (row < 0 || row >= currentTrips.size()) {
            JOptionPane.showMessageDialog(this, "请先在结果中选择一个行程。", "提示", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        TripOffer trip = currentTrips.get(row);
        int count = (Integer) passengers.getValue();
        JPanel form = new JPanel(new BorderLayout(8, 12));
        JPanel passengerPanel = new JPanel(new GridLayout(0, 4, 8, 8));
        List<JTextField> names = new ArrayList<>();
        List<JTextField> passports = new ArrayList<>();
        List<JComboBox<String>> seatPreferences = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            JTextField name = new JTextField();
            JTextField passport = new JTextField();
            JComboBox<String> seat = new JComboBox<>(
                    new String[]{"WINDOW 靠窗", "AISLE 过道", "MIDDLE 中间"});
            names.add(name);
            passports.add(passport);
            seatPreferences.add(seat);
            passengerPanel.add(new JLabel("乘客 " + (i + 1) + " 姓名"));
            passengerPanel.add(name);
            passengerPanel.add(passport);
            passengerPanel.add(seat);
        }
        form.add(new JLabel("航班：" + airportLabel(trip.legs().get(0).origin()) + " → "
                + airportLabel(trip.legs().get(trip.legs().size() - 1).destination())
                + " · " + trip.airlinesLabel() + " · ¥" + trip.totalPrice().toPlainString()
                + "（单人 ¥" + trip.pricePerPassenger().toPlainString() + "×" + trip.passengers() + "）"
                + " · " + trip.pricingNote()), BorderLayout.NORTH);
        form.add(passengerPanel, BorderLayout.CENTER);
        form.setPreferredSize(new Dimension(760, 105 + count * 42));

        int choice = JOptionPane.showConfirmDialog(this, form,
                "填写乘客信息（依次为姓名、护照号、个人座位偏好）",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (choice != JOptionPane.OK_OPTION) {
            return;
        }
        List<Passenger> passengerData = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String name = names.get(i).getText().trim();
            String passport = passports.get(i).getText().trim();
            if (name.isEmpty() || passport.isEmpty()) {
                JOptionPane.showMessageDialog(this, "乘客姓名和护照号不能为空。", "输入错误", JOptionPane.WARNING_MESSAGE);
                return;
            }
            SeatPreference seatPreference = SeatPreference.values()[seatPreferences.get(i).getSelectedIndex()];
            passengerData.add(new Passenger(name, passport, seatPreference));
        }
        setBusy(true, "客户端正在直连航司预占并协调预订…");
        new SwingWorker<Receipt, Void>() {
            @Override
            protected Receipt doInBackground() {
                return service.book(trip, passengerData);
            }

            @Override
            protected void done() {
                try {
                    Receipt receipt = get();
                    referenceField.setText(receipt.bookingReference());
                    renderReceipt(receipt);
                    JOptionPane.showMessageDialog(ThickClientFrame.this,
                            "预订成功：" + receipt.bookingReference(), "预订完成",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    showError("预订失败", rootMessage(exception));
                } finally {
                    setBusy(false, "就绪");
                }
            }
        }.execute();
    }

    private void lookupReceipt() {
        String reference = referenceField.getText().trim();
        if (reference.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入预订编号。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        setBusy(true, "读取中央服务中的收据…");
        new SwingWorker<Receipt, Void>() {
            @Override
            protected Receipt doInBackground() {
                return serviceLookup(reference);
            }

            @Override
            protected void done() {
                try {
                    renderReceipt(get());
                } catch (Exception exception) {
                    showError("查询失败", rootMessage(exception));
                } finally {
                    setBusy(false, "就绪");
                }
            }
        }.execute();
    }

    private Receipt serviceLookup(String reference) {
        try {
            return new CentralClient(centralUrl).booking(reference);
        } catch (RuntimeException exception) {
            throw new IllegalStateException(exception.getMessage(), exception);
        }
    }

    private void cancelReceipt() {
        String reference = referenceField.getText().trim();
        if (reference.isEmpty()) {
            JOptionPane.showMessageDialog(this, "请输入要取消的预订编号。", "提示", JOptionPane.WARNING_MESSAGE);
            return;
        }
        int choice = JOptionPane.showConfirmDialog(this,
                "取消将由 Java 客户端逐个通知航空公司，然后由中央服务登记结果。继续？",
                "确认取消", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
            return;
        }
        setBusy(true, "客户端正在协调各航司取消…");
        new SwingWorker<Receipt, Void>() {
            @Override
            protected Receipt doInBackground() {
                return service.cancel(reference);
            }

            @Override
            protected void done() {
                try {
                    renderReceipt(get());
                    JOptionPane.showMessageDialog(ThickClientFrame.this, "取消成功。", "完成",
                            JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception exception) {
                    showError("取消失败", rootMessage(exception));
                } finally {
                    setBusy(false, "就绪");
                }
            }
        }.execute();
    }

    private void renderReceipt(Receipt receipt) {
        StringBuilder text = new StringBuilder();
        text.append("预订编号: ").append(receipt.bookingReference()).append('\n');
        text.append("状态: ").append(receipt.status()).append('\n');
        text.append("总价: ¥").append(receipt.totalPrice())
                .append("（").append(receipt.passengers().size()).append(" 人）    ")
                .append(receipt.pricingNote()).append('\n');
        text.append("乘客:\n");
        for (int i = 0; i < receipt.passengers().size(); i++) {
            Passenger passenger = receipt.passengers().get(i);
            text.append("  ").append(i + 1).append(". ")
                    .append(passenger.name()).append(" (").append(passenger.passportNumber()).append(")")
                    .append("  座位偏好: ").append(seatPreferenceLabel(passenger.seatPreference()))
                    .append('\n');
        }
        text.append("\n航段:\n");
        for (BookedLeg leg : receipt.legs()) {
            text.append("  ").append(leg.airlineCode()).append(' ').append(leg.flightNumber())
                    .append("  ").append(airportLabel(leg.origin())).append(' ').append(TIME.format(leg.departureTime()))
                    .append(" → ").append(airportLabel(leg.destination())).append(' ').append(TIME.format(leg.arrivalTime()))
                    .append("  ¥").append(leg.fare()).append("  [").append(leg.status()).append("]\n");
        }
        text.append("\n").append(receipt.message() == null ? "" : receipt.message());
        receiptArea.setText(text.toString());
        receiptArea.setCaretPosition(0);
    }

    private String seatPreferenceLabel(SeatPreference preference) {
        return switch (preference) {
            case WINDOW -> "靠窗";
            case AISLE -> "过道";
            case MIDDLE -> "中间";
            case MIXED -> "混合";
        };
    }

    private void setBusy(boolean busy, String message) {
        searchButton.setEnabled(!busy);
        bookButton.setEnabled(!busy);
        status.setText(message);
        setCursor(busy ? java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.WAIT_CURSOR)
                : java.awt.Cursor.getDefaultCursor());
    }

    private void showError(String title, String message) {
        status.setText(title + "：" + message);
        JOptionPane.showMessageDialog(this, message, title, JOptionPane.ERROR_MESSAGE);
    }

    private String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage() == null ? current.getClass().getSimpleName() : current.getMessage();
    }
}
