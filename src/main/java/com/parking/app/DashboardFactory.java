package com.parking.app;

import com.parking.entity.*;
import com.parking.service.*;
import com.parking.service.impl.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.control.cell.ComboBoxTableCell;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.StackedBarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;


//角色分流
public class DashboardFactory {

    private static final DateTimeFormatter DT_SPACE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DT_SLASH_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");
    private static final String LOG_ADD = "ADD";
    private static final String LOG_DELETE = "DELETE";
    private static final String LOG_UPDATE = "UPDATE";
    private static final String LOG_LOGIN = "LOGIN";

    private static final long PROTECTED_ADMIN_ID = 1L;
    private final UserAdminService userAdminService = new UserAdminServiceImpl();
    private final ParkingLotService parkingLotService = new ParkingLotServiceImpl();
    private final ParkingSpaceService parkingSpaceService = new ParkingSpaceServiceImpl();
    private final PricingRuleService pricingRuleService = new PricingRuleServiceImpl();
    private final ReservationService reservationService = new ReservationServiceImpl();
    private final ParkingRecordService parkingRecordService = new ParkingRecordServiceImpl();
    private final PaymentService paymentService = new PaymentServiceImpl();
    private final RevenueService revenueService = new RevenueServiceImpl();
    private final ReportService reportService = new ReportServiceImpl();
    private final OperationLogService operationLogService = new OperationLogServiceImpl();
    private User currentUser;

    private static class RollbackEntry {
        final String label;
        final String moduleName;
        final Runnable restore;
        RollbackEntry(String label, String moduleName, Runnable restore) {
            this.label = label; this.moduleName = moduleName; this.restore = restore;
        }
    }
    private final Deque<RollbackEntry> rollbackStack = new ArrayDeque<>();
    private static final int MAX_ROLLBACK = 3;

    public Parent createMainView(User user, Runnable onLogout) {
        this.currentUser = user;

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));
        root.getStyleClass().add("app-root");

        Label userLabel = new Label("\u5f53\u524d\u7528\u6237\uff1a" + user.getUsername() + "\uff08" + roleLabel(user.getRole()) + "\uff09"); // 当前用户： | （ | ）
        userLabel.getStyleClass().add("top-user-label");
        Button logoutButton = new Button("\u9000\u51fa\u767b\u5f55"); // 退出登录
        logoutButton.setOnAction(e -> onLogout.run());
        HBox topBar = new HBox(12, userLabel, logoutButton);
        topBar.getStyleClass().add("top-bar");
        root.setTop(topBar);
        addOperationLog(LOG_LOGIN, formatModuleLog("登录", "用户登录系统：" + user.getUsername())); // 登录 | 用户登录系统：

        String role = user.getRole() == null ? "" : user.getRole().toUpperCase();
        if ("ADMIN".equals(role)) {
            root.setCenter(adminView());
        } else if ("OWNER".equals(role)) {
            root.setCenter(ownerView(user));
        } else {
            root.setCenter(carOwnerView(user));
        }
        return root;
    }

    private Parent adminView() {
        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(
                userTab(),
                parkingLotTab(),
                parkingSpaceTab(),
                pricingRuleTab(),
                settlementTab(),
                reportTab(),
                operationLogTab()
        );
        return tabs;
    }

    private Parent ownerView(User user) {
        // Tab 1: My Spaces
        TableView<ParkingSpace> spacesTable = new TableView<>();
        spacesTable.setEditable(true);

        TableColumn<ParkingSpace, String> sIdCol = new TableColumn<>("车位ID");
        sIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSpaceId())));
        sIdCol.setEditable(false); sIdCol.setSortable(false); sIdCol.setPrefWidth(60);

        TableColumn<ParkingSpace, String> sNumCol = new TableColumn<>("车位编号");
        sNumCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSpaceNumber()));
        sNumCol.setCellFactory(TextFieldTableCell.forTableColumn());
        sNumCol.setOnEditCommit(e -> {
            try { parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "space_number", e.getNewValue()); }
            catch (Exception ex) { /* ignore */ }
        });
        sNumCol.setSortable(false); sNumCol.setPrefWidth(100);

        TableColumn<ParkingSpace, String> sLotCol = new TableColumn<>("停车场ID");
        sLotCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getLotId())));
        sLotCol.setCellFactory(TextFieldTableCell.forTableColumn());
        sLotCol.setOnEditCommit(e -> {
            try { parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "lot_id", e.getNewValue()); }
            catch (Exception ex) { /* ignore */ }
        });
        sLotCol.setSortable(false); sLotCol.setPrefWidth(80);

        TableColumn<ParkingSpace, String> sTypeCol = new TableColumn<>("类型");
        sTypeCol.setCellValueFactory(c -> new SimpleStringProperty(spaceTypeLabel(c.getValue().getType())));
        sTypeCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("地上", "地下")));
        sTypeCol.setOnEditCommit(e -> {
            try { parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "type", spaceTypeCode(e.getNewValue())); }
            catch (Exception ex) { /* ignore */ }
        });
        sTypeCol.setSortable(false); sTypeCol.setPrefWidth(70);

        TableColumn<ParkingSpace, String> sStatusCol = new TableColumn<>("状态");
        sStatusCol.setCellValueFactory(c -> new SimpleStringProperty(spaceStatusLabel(c.getValue().getStatus())));
        sStatusCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("空闲", "已预约", "占用")));
        sStatusCol.setOnEditCommit(e -> {
            try { parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "status", spaceStatusCode(e.getNewValue())); }
            catch (Exception ex) { /* ignore */ }
        });
        sStatusCol.setSortable(false); sStatusCol.setPrefWidth(70);

        java.util.List<String> timeOpts = java.util.List.of("00:00","06:00","07:00","08:00","09:00","10:00","12:00","14:00","16:00","18:00","20:00","22:00","23:59");

        TableColumn<ParkingSpace, String> sStartCol = new TableColumn<>("共享开始");
        sStartCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getShareStartTime() != null ? c.getValue().getShareStartTime().toString() : ""));
        sStartCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(timeOpts)));
        sStartCol.setOnEditCommit(e -> {
            try { parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "share_start_time", e.getNewValue()); }
            catch (Exception ex) { /* ignore */ }
        });
        sStartCol.setSortable(false); sStartCol.setPrefWidth(90);

        TableColumn<ParkingSpace, String> sEndCol = new TableColumn<>("共享结束");
        sEndCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getShareEndTime() != null ? c.getValue().getShareEndTime().toString() : ""));
        sEndCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(timeOpts)));
        sEndCol.setOnEditCommit(e -> {
            try { parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "share_end_time", e.getNewValue()); }
            catch (Exception ex) { /* ignore */ }
        });
        sEndCol.setSortable(false); sEndCol.setPrefWidth(90);

        spacesTable.getColumns().addAll(sIdCol, sNumCol, sLotCol, sTypeCol, sStatusCol, sStartCol, sEndCol);
        spacesTable.setFixedCellSize(28);
        double sh = spacesTable.getFixedCellSize() * 10 + 34;
        spacesTable.setPrefHeight(sh); spacesTable.setMinHeight(sh); spacesTable.setMaxHeight(sh);

        Button loadSpaces = new Button("刷新我的车位");
        loadSpaces.setOnAction(e -> {
            try {
                List<ParkingSpace> rows = parkingSpaceService.queryMySpaces(user.getUserId(), 1, 100);
                spacesTable.setItems(FXCollections.observableArrayList(rows));
            } catch (Exception ex) { /* ignore */ }
        });
        VBox spacesTab = new VBox(10, loadSpaces, spacesTable);
        spacesTab.setPadding(new Insets(10));

        // Tab 2: My Reservations
        TableView<Reservation> resvTable = new TableView<>();
        resvTable.setEditable(true);

        TableColumn<Reservation, String> rIdCol = new TableColumn<>("预约ID");
        rIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getReservationId())));
        rIdCol.setEditable(false); rIdCol.setSortable(false); rIdCol.setPrefWidth(60);

        TableColumn<Reservation, String> rSpaceCol = new TableColumn<>("车位ID");
        rSpaceCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSpaceId())));
        rSpaceCol.setEditable(false); rSpaceCol.setSortable(false); rSpaceCol.setPrefWidth(60);

        TableColumn<Reservation, String> rUserCol = new TableColumn<>("车主ID");
        rUserCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getUserId())));
        rUserCol.setEditable(false); rUserCol.setSortable(false); rUserCol.setPrefWidth(60);

        TableColumn<Reservation, String> rStartCol = new TableColumn<>("预约开始");
        rStartCol.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getReserveStart())));
        rStartCol.setEditable(false); rStartCol.setSortable(false); rStartCol.setPrefWidth(130);

        TableColumn<Reservation, String> rEndCol = new TableColumn<>("预约结束");
        rEndCol.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getReserveEnd())));
        rEndCol.setEditable(false); rEndCol.setSortable(false); rEndCol.setPrefWidth(130);

        TableColumn<Reservation, String> rStatusCol = new TableColumn<>("状态");
        rStatusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        rStatusCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("PENDING", "CANCELED")));
        rStatusCol.setOnEditCommit(e -> {
            try {
                if ("CANCELED".equalsIgnoreCase(e.getNewValue())) {
                    reservationService.cancel(e.getRowValue().getReservationId(), user.getUserId());
                }
            } catch (Exception ex) { /* ignore */ }
        });
        rStatusCol.setSortable(false); rStatusCol.setPrefWidth(80);

        TableColumn<Reservation, String> rCreateCol = new TableColumn<>("创建时间");
        rCreateCol.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getCreateTime())));
        rCreateCol.setEditable(false); rCreateCol.setSortable(false); rCreateCol.setPrefWidth(130);

        resvTable.getColumns().addAll(rIdCol, rSpaceCol, rUserCol, rStartCol, rEndCol, rStatusCol, rCreateCol);
        resvTable.setFixedCellSize(28);
        double rh = resvTable.getFixedCellSize() * 10 + 34;
        resvTable.setPrefHeight(rh); resvTable.setMinHeight(rh); resvTable.setMaxHeight(rh);

        Button loadResv = new Button("刷新名下预约");
        loadResv.setOnAction(e -> {
            try {
                List<Reservation> rows = reservationService.getOwnerReservations(user.getUserId(), 1, 100);
                resvTable.setItems(FXCollections.observableArrayList(rows));
            } catch (Exception ex) { /* ignore */ }
        });
        VBox resvTab = new VBox(10, loadResv, resvTable);
        resvTab.setPadding(new Insets(10));

        // Tab 3: Revenue Detail
        TableView<Map<String, Object>> revenueTable = new TableView<>();
        revenueTable.setEditable(false);

        TableColumn<Map<String, Object>, String> revIdCol = new TableColumn<>("收益ID");
        revIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("revenue_id"))));
        revIdCol.setSortable(false); revIdCol.setPrefWidth(60);

        TableColumn<Map<String, Object>, String> revSpaceCol = new TableColumn<>("车位ID");
        revSpaceCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("space_id"))));
        revSpaceCol.setSortable(false); revSpaceCol.setPrefWidth(60);

        TableColumn<Map<String, Object>, String> revPayCol = new TableColumn<>("支付ID");
        revPayCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("payment_id"))));
        revPayCol.setSortable(false); revPayCol.setPrefWidth(60);

        TableColumn<Map<String, Object>, String> revAmtCol = new TableColumn<>("收益金额");
        revAmtCol.setCellValueFactory(c -> {
            Object v = c.getValue().get("income_amount");
            return new SimpleStringProperty(v != null ? v.toString() : "");
        });
        revAmtCol.setSortable(false); revAmtCol.setPrefWidth(80);

        TableColumn<Map<String, Object>, String> revStatusCol = new TableColumn<>("结算状态");
        revStatusCol.setCellValueFactory(c -> {
            Object v = c.getValue().get("settle_status");
            String s = v != null ? v.toString() : "";
            return new SimpleStringProperty("SETTLED".equals(s) ? "已结算" : "未结算");
        });
        revStatusCol.setSortable(false); revStatusCol.setPrefWidth(70);

        TableColumn<Map<String, Object>, String> revTimeCol = new TableColumn<>("结算时间");
        revTimeCol.setCellValueFactory(c -> {
            Object v = c.getValue().get("settle_time");
            return new SimpleStringProperty(v != null ? v.toString() : "");
        });
        revTimeCol.setSortable(false); revTimeCol.setPrefWidth(130);

        TableColumn<Map<String, Object>, String> revPayTimeCol = new TableColumn<>("支付时间");
        revPayTimeCol.setCellValueFactory(c -> {
            Object v = c.getValue().get("payment_time");
            return new SimpleStringProperty(v != null ? v.toString() : "");
        });
        revPayTimeCol.setSortable(false); revPayTimeCol.setPrefWidth(130);

        revenueTable.getColumns().addAll(revIdCol, revSpaceCol, revPayCol, revAmtCol, revStatusCol, revTimeCol, revPayTimeCol);
        revenueTable.setFixedCellSize(28);
        double revh = revenueTable.getFixedCellSize() * 10 + 34;
        revenueTable.setPrefHeight(revh); revenueTable.setMinHeight(revh); revenueTable.setMaxHeight(revh);

        Label totalIncomeLabel = new Label("总收益: --");
        Button loadRevenue = new Button("刷新收益明细");
        loadRevenue.setOnAction(e -> {
            try {
                List<Map<String, Object>> rows = revenueService.getOwnerIncomeDetail(user.getUserId(), 1, 100);
                revenueTable.setItems(FXCollections.observableArrayList(rows));
                BigDecimal total = revenueService.getOwnerIncomeTotal(user.getUserId());
                totalIncomeLabel.setText("总收益=" + formatAmount(total));
            } catch (Exception ex) { /* ignore */ }
        });
        VBox revenueTab = new VBox(10, loadRevenue, revenueTable, totalIncomeLabel);
        revenueTab.setPadding(new Insets(10));

        TabPane tabs = new TabPane();
        Tab t1 = new Tab("我的车位", spacesTab); t1.setClosable(false);
        Tab t2 = new Tab("名下预约", resvTab); t2.setClosable(false);
        Tab t3 = new Tab("收益明细", revenueTab); t3.setClosable(false);
        tabs.getTabs().addAll(t1, t2, t3);

        return tabs;
    }

    private Parent carOwnerView(User user) {
        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(reservationTab(user), parkingTab(user));
        return tabs;
    }
    private Tab reservationTab(User user) {
        TextArea out = new TextArea();
        out.setEditable(false);
        out.setPrefRowCount(6);

        // Load all parking lots for filterable dropdowns
        java.util.List<String> allLotItems = new java.util.ArrayList<>();
        java.util.Map<String, Long> lotNameToId = new java.util.HashMap<>();
        try {
            List<ParkingLot> lots = parkingLotService.queryLots("", 1, 500);
            for (ParkingLot lot : lots) {
                String label = lot.getLotName() + " (ID=" + lot.getLotId() + ")";
                allLotItems.add(label);
                lotNameToId.put(label, lot.getLotId());
            }
        } catch (Exception ex) { /* ignore */ }

        // Helper to create filterable ComboBox
        java.util.function.Function<java.util.List<String>, ComboBox<String>> createFilterableCombo = (items) -> {
            ComboBox<String> cb = new ComboBox<>(FXCollections.observableArrayList(items));
            cb.setEditable(true);
            cb.setPrefWidth(240);
            cb.getEditor().textProperty().addListener((obs, oldVal, newVal) -> {
                if (newVal == null || newVal.isEmpty()) {
                    cb.setItems(FXCollections.observableArrayList(items));
                } else {
                    String filter = newVal.toLowerCase();
                    java.util.List<String> filtered = new java.util.ArrayList<>();
                    for (String item : items) {
                        if (item.toLowerCase().contains(filter)) filtered.add(item);
                    }
                    cb.setItems(FXCollections.observableArrayList(filtered));
                }
                cb.show();
            });
            return cb;
        };

        // === Query section: dual tables for ground/underground ===
        ComboBox<String> queryLotCombo = createFilterableCombo.apply(allLotItems);
        if (!allLotItems.isEmpty()) queryLotCombo.setValue(allLotItems.get(0));

        TableView<ParkingSpace> groundTable = new TableView<>();
        groundTable.setEditable(false);
        TableColumn<ParkingSpace, String> gIdCol = new TableColumn<>("车位ID");
        gIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSpaceId()))); gIdCol.setSortable(false); gIdCol.setPrefWidth(50);
        TableColumn<ParkingSpace, String> gNumCol = new TableColumn<>("编号");
        gNumCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSpaceNumber())); gNumCol.setSortable(false); gNumCol.setPrefWidth(100);
        TableColumn<ParkingSpace, String> gStatusCol = new TableColumn<>("状态");
        gStatusCol.setCellValueFactory(c -> new SimpleStringProperty(spaceStatusLabel(c.getValue().getStatus()))); gStatusCol.setSortable(false); gStatusCol.setPrefWidth(70);
        TableColumn<ParkingSpace, String> gTimeCol = new TableColumn<>("共享时间");
        gTimeCol.setCellValueFactory(c -> new SimpleStringProperty(
            (c.getValue().getShareStartTime() != null ? c.getValue().getShareStartTime().toString() : "") + "~" +
            (c.getValue().getShareEndTime() != null ? c.getValue().getShareEndTime().toString() : "")));
        gTimeCol.setSortable(false); gTimeCol.setPrefWidth(150);
        groundTable.getColumns().addAll(gIdCol, gNumCol, gStatusCol, gTimeCol);
        groundTable.setFixedCellSize(26);
        double gth = groundTable.getFixedCellSize() * 6 + 34;
        groundTable.setPrefHeight(gth); groundTable.setMinHeight(gth); groundTable.setMaxHeight(gth);

        TableView<ParkingSpace> underTable = new TableView<>();
        underTable.setEditable(false);
        TableColumn<ParkingSpace, String> uIdCol = new TableColumn<>("车位ID");
        uIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSpaceId()))); uIdCol.setSortable(false); uIdCol.setPrefWidth(50);
        TableColumn<ParkingSpace, String> uNumCol = new TableColumn<>("编号");
        uNumCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSpaceNumber())); uNumCol.setSortable(false); uNumCol.setPrefWidth(100);
        TableColumn<ParkingSpace, String> uStatusCol = new TableColumn<>("状态");
        uStatusCol.setCellValueFactory(c -> new SimpleStringProperty(spaceStatusLabel(c.getValue().getStatus()))); uStatusCol.setSortable(false); uStatusCol.setPrefWidth(70);
        TableColumn<ParkingSpace, String> uTimeCol = new TableColumn<>("共享时间");
        uTimeCol.setCellValueFactory(c -> new SimpleStringProperty(
            (c.getValue().getShareStartTime() != null ? c.getValue().getShareStartTime().toString() : "") + "~" +
            (c.getValue().getShareEndTime() != null ? c.getValue().getShareEndTime().toString() : "")));
        uTimeCol.setSortable(false); uTimeCol.setPrefWidth(150);
        underTable.getColumns().addAll(uIdCol, uNumCol, uStatusCol, uTimeCol);
        underTable.setFixedCellSize(26);
        double uth = underTable.getFixedCellSize() * 6 + 34;
        underTable.setPrefHeight(uth); underTable.setMinHeight(uth); underTable.setMaxHeight(uth);

        Button queryBtn = new Button("查询可用车位");
        queryBtn.setOnAction(e -> {
            try {
                String sel = queryLotCombo.getValue();
                if (sel == null || !lotNameToId.containsKey(sel)) {
                    out.appendText("请选择有效的停车场\n");
                    return;
                }
                long lid = lotNameToId.get(sel);
                List<ParkingSpace> all = loadAllSpacesByLot(lid);
                List<ParkingSpace> ground = new java.util.ArrayList<>();
                List<ParkingSpace> under = new java.util.ArrayList<>();
                for (ParkingSpace s : all) {
                    if ("GROUND".equalsIgnoreCase(s.getType())) ground.add(s);
                    else under.add(s);
                }
                groundTable.setItems(FXCollections.observableArrayList(ground));
                underTable.setItems(FXCollections.observableArrayList(under));
                out.appendText("停车场: " + sel + "  地上=" + ground.size() + "个  地下=" + under.size() + "个\n");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        // === Reserve section: 4 dropdowns + 1 button ===
        ComboBox<String> resvLotCombo = createFilterableCombo.apply(allLotItems);
        if (!allLotItems.isEmpty()) resvLotCombo.setValue(allLotItems.get(0));

        ComboBox<String> resvTypeCombo = new ComboBox<>(FXCollections.observableArrayList("地上", "地下"));
        resvTypeCombo.setValue("地上");
        resvTypeCombo.setPrefWidth(100);

        java.util.List<String> hourItems = new java.util.ArrayList<>();
        for (int h = 0; h < 24; h++) hourItems.add(String.format("%02d:00", h));

        ComboBox<String> startTimeCombo = new ComboBox<>(FXCollections.observableArrayList(hourItems));
        startTimeCombo.setValue("08:00");
        startTimeCombo.setPrefWidth(90);

        ComboBox<String> endTimeCombo = new ComboBox<>(FXCollections.observableArrayList(hourItems));
        endTimeCombo.setValue("10:00");
        endTimeCombo.setPrefWidth(90);

        Button reserveBtn = new Button("提交预约");
        reserveBtn.setOnAction(e -> {
            try {
                String sel = resvLotCombo.getValue();
                if (sel == null || !lotNameToId.containsKey(sel)) {
                    out.appendText("请选择有效的停车场\n");
                    return;
                }
                long lid = lotNameToId.get(sel);
                String typeCode = spaceTypeCode(resvTypeCombo.getValue());
                LocalTime st = LocalTime.parse(startTimeCombo.getValue());
                LocalTime et = LocalTime.parse(endTimeCombo.getValue());
                LocalDate today = LocalDate.now();
                LocalDateTime startDt = LocalDateTime.of(today, st);
                LocalDateTime endDt = LocalDateTime.of(today, et);
                if (!endDt.isAfter(startDt)) endDt = endDt.plusDays(1);

                ReservationService.AutoReserveResult result = reservationService.reserveByLotAndType(
                        user.getUserId(), lid, typeCode, startDt, endDt);

                List<ParkingLot> lots = parkingLotService.queryLots(String.valueOf(lid), 1, 1);
                String lotName = lots.isEmpty() ? String.valueOf(lid) : lots.get(0).getLotName();
                String address = lots.isEmpty() ? "" : lots.get(0).getAddress();

                out.clear();
                out.appendText("预约成功!\n");
                out.appendText("预约ID: " + result.getReservationId() + "\n");
                out.appendText("分配车位ID: " + result.getSpaceId() + "\n");
                out.appendText("停车场: " + lotName + "\n");
                out.appendText("地址: " + address + "\n");
                out.appendText("类型: " + resvTypeCombo.getValue() + "\n");
                out.appendText("时间: " + fmtDateTime(startDt) + " ~ " + fmtDateTime(endDt) + "\n");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        // === My Reservations table ===
        TableView<Reservation> myResvTable = new TableView<>();
        myResvTable.setEditable(false);
        TableColumn<Reservation, String> mrId = new TableColumn<>("预约ID");
        mrId.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getReservationId()))); mrId.setSortable(false); mrId.setPrefWidth(60);
        TableColumn<Reservation, String> mrSpace = new TableColumn<>("车位ID");
        mrSpace.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSpaceId()))); mrSpace.setSortable(false); mrSpace.setPrefWidth(60);
        TableColumn<Reservation, String> mrStart = new TableColumn<>("开始");
        mrStart.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getReserveStart()))); mrStart.setSortable(false); mrStart.setPrefWidth(130);
        TableColumn<Reservation, String> mrEnd = new TableColumn<>("结束");
        mrEnd.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getReserveEnd()))); mrEnd.setSortable(false); mrEnd.setPrefWidth(130);
        TableColumn<Reservation, String> mrStatus = new TableColumn<>("状态");
        mrStatus.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus())); mrStatus.setSortable(false); mrStatus.setPrefWidth(70);
        TableColumn<Reservation, String> mrCreate = new TableColumn<>("创建时间");
        mrCreate.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getCreateTime()))); mrCreate.setSortable(false); mrCreate.setPrefWidth(130);
        myResvTable.getColumns().addAll(mrId, mrSpace, mrStart, mrEnd, mrStatus, mrCreate);
        myResvTable.setFixedCellSize(26);
        double mrh = myResvTable.getFixedCellSize() * 8 + 34;
        myResvTable.setPrefHeight(mrh); myResvTable.setMinHeight(mrh); myResvTable.setMaxHeight(mrh);

        Button myResvBtn = new Button("刷新我的预约");
        myResvBtn.setOnAction(e -> {
            try {
                List<Reservation> rows = reservationService.getMyReservations(user.getUserId(), 1, 100);
                myResvTable.setItems(FXCollections.observableArrayList(rows));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        Button cancelBtn = new Button("取消预约");
        cancelBtn.setOnAction(e -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("取消预约");
            dialog.setHeaderText("请输入要取消的预约ID");
            Optional<String> input = dialog.showAndWait();
            if (input.isEmpty()) return;
            try {
                long rid = Long.parseLong(input.get().trim());
                reservationService.cancel(rid, user.getUserId());
                out.appendText("取消成功，预约ID=" + rid + "\n");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        Button lotInfoBtn = new Button("停车场信息");
        lotInfoBtn.setOnAction(e -> {
            try {
                String sel = resvLotCombo.getValue();
                if (sel == null || !lotNameToId.containsKey(sel)) {
                    out.appendText("请先在预约区选择停车场\n");
                    return;
                }
                long lid = lotNameToId.get(sel);
                List<ParkingLot> lots = parkingLotService.queryLots(String.valueOf(lid), 1, 1);
                if (lots.isEmpty()) { out.appendText("未找到停车场\n"); return; }
                ParkingLot lot = lots.get(0);
                List<ParkingSpace> spaces = loadAllSpacesByLot(lid);
                long ground = spaces.stream().filter(s -> "GROUND".equalsIgnoreCase(s.getType())).count();
                long under = spaces.stream().filter(s -> "UNDERGROUND".equalsIgnoreCase(s.getType())).count();
                long free = spaces.stream().filter(s -> "FREE".equalsIgnoreCase(s.getStatus())).count();
                long reserved = spaces.stream().filter(s -> "RESERVED".equalsIgnoreCase(s.getStatus())).count();
                long occupied = spaces.stream().filter(s -> "OCCUPIED".equalsIgnoreCase(s.getStatus())).count();
                out.clear();
                out.appendText("停车场: " + lot.getLotName() + "\n");
                out.appendText("地址: " + lot.getAddress() + "\n");
                out.appendText("营业时间: " + lot.getOpenTime() + "~" + lot.getCloseTime() + "\n");
                out.appendText("总车位数: " + lot.getTotalSpaces() + "\n");
                out.appendText("地上=" + ground + " 地下=" + under + " 空闲=" + free + " 已预约=" + reserved + " 占用=" + occupied + "\n");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        HBox queryTop = new HBox(8, new Label("停车场"), queryLotCombo, queryBtn);
        Label groundLabel = new Label("地上可用车位");
        groundLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #4CAF50;");
        Label underLabel = new Label("地下可用车位");
        underLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #FF9800;");
        HBox resvTop = new HBox(8, new Label("停车场"), resvLotCombo, new Label("类型"), resvTypeCombo,
                new Label("开始"), startTimeCombo, new Label("结束"), endTimeCombo, reserveBtn);
        HBox actionRow = new HBox(8, myResvBtn, cancelBtn, lotInfoBtn);

        VBox body = new VBox(10,
                sectionBox("查询可用车位", queryTop, groundLabel, groundTable, underLabel, underTable),
                sectionBox("提交预约", resvTop),
                sectionBox("预约结果", out),
                sectionBox("我的预约", actionRow, myResvTable));
        body.setPadding(new Insets(10));

        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab tab = new Tab("预约管理", pageScroll);
        tab.setClosable(false);
        return tab;
    }

    private Tab parkingTab(User user) {
        TextField reservationId = new TextField();
        reservationId.setPromptText("预约ID（可选，无预约可留空）");
        reservationId.setPrefWidth(320);
        TextField lotId = new TextField();
        lotId.setPromptText("停车场ID（必填）");
        lotId.setPrefWidth(190);
        ComboBox<String> entryType = new ComboBox<>(FXCollections.observableArrayList("地上", "地下"));
        entryType.setValue("地上");
        entryType.setPrefWidth(110);
        TextField recordId = new TextField();
        recordId.setPromptText("停车记录ID");
        TextArea out = new TextArea();
        out.setEditable(false);
        out.setPrefRowCount(4);

        Button entry = new Button("入场登记");
        entry.setMinWidth(110);
        entry.setOnAction(e -> {
            try {
                Long reserveId = null;
                String reserveText = reservationId.getText() == null ? "" : reservationId.getText().trim();
                if (!reserveText.isEmpty()) reserveId = Long.parseLong(reserveText);
                ParkingRecordService.AutoEntryResult result = parkingRecordService.entryByLotAndType(
                        reserveId, user.getUserId(), requireLong(lotId, "停车场ID"),
                        spaceTypeCode(entryType.getValue()), LocalDateTime.now());
                out.appendText("入场登记成功，停车记录ID=" + result.getRecordId() + "，分配车位ID=" + result.getSpaceId() + "\n");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        Button exitAndPay = new Button("出场并支付");
        exitAndPay.setOnAction(e -> {
            try {
                BigDecimal fee = parkingRecordService.exitAndPay(requireLong(recordId, "停车记录ID"), "WECHAT");
                out.appendText("支付成功，费用=" + fee + "\n");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        // Parking records table
        TableView<ParkingRecord> recordsTable = new TableView<>();
        recordsTable.setEditable(false);
        TableColumn<ParkingRecord, String> rIdCol = new TableColumn<>("记录ID");
        rIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getRecordId()))); rIdCol.setSortable(false); rIdCol.setPrefWidth(60);
        TableColumn<ParkingRecord, String> rResvCol = new TableColumn<>("预约ID");
        rResvCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getReservationId()))); rResvCol.setSortable(false); rResvCol.setPrefWidth(60);
        TableColumn<ParkingRecord, String> rSpaceCol = new TableColumn<>("车位ID");
        rSpaceCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSpaceId()))); rSpaceCol.setSortable(false); rSpaceCol.setPrefWidth(60);
        TableColumn<ParkingRecord, String> rEntryCol = new TableColumn<>("入场时间");
        rEntryCol.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getEntryTime()))); rEntryCol.setSortable(false); rEntryCol.setPrefWidth(130);
        TableColumn<ParkingRecord, String> rExitCol = new TableColumn<>("出场时间");
        rExitCol.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getExitTime()))); rExitCol.setSortable(false); rExitCol.setPrefWidth(130);
        TableColumn<ParkingRecord, String> rDurCol = new TableColumn<>("时长(分)");
        rDurCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDuration() != null ? String.valueOf(c.getValue().getDuration()) : "")); rDurCol.setSortable(false); rDurCol.setPrefWidth(80);
        TableColumn<ParkingRecord, String> rFeeCol = new TableColumn<>("费用");
        rFeeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFee() != null ? c.getValue().getFee().toString() : "")); rFeeCol.setSortable(false); rFeeCol.setPrefWidth(80);
        recordsTable.getColumns().addAll(rIdCol, rResvCol, rSpaceCol, rEntryCol, rExitCol, rDurCol, rFeeCol);
        recordsTable.setFixedCellSize(26);
        double rth = recordsTable.getFixedCellSize() * 8 + 34;
        recordsTable.setPrefHeight(rth); recordsTable.setMinHeight(rth); recordsTable.setMaxHeight(rth);

        Button myRecords = new Button("我的停车记录");
        myRecords.setOnAction(e -> {
            try {
                List<ParkingRecord> rows = parkingRecordService.getMyParkingRecords(user.getUserId(), 1, 100);
                recordsTable.setItems(FXCollections.observableArrayList(rows));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        // Payment records table
        TableView<Map<String, Object>> paymentsTable = new TableView<>();
        paymentsTable.setEditable(false);
        TableColumn<Map<String, Object>, String> pIdCol = new TableColumn<>("支付ID");
        pIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("payment_id"))));
        pIdCol.setSortable(false); pIdCol.setPrefWidth(60);
        TableColumn<Map<String, Object>, String> pAmtCol = new TableColumn<>("金额");
        pAmtCol.setCellValueFactory(c -> { Object v = c.getValue().get("amount"); return new SimpleStringProperty(v != null ? v.toString() : ""); });
        pAmtCol.setSortable(false); pAmtCol.setPrefWidth(70);
        TableColumn<Map<String, Object>, String> pStatusCol = new TableColumn<>("支付状态");
        pStatusCol.setCellValueFactory(c -> { Object v = c.getValue().get("payment_status"); String s = v != null ? v.toString() : ""; return new SimpleStringProperty("PAID".equals(s) ? "已支付" : s); });
        pStatusCol.setSortable(false); pStatusCol.setPrefWidth(70);
        TableColumn<Map<String, Object>, String> pTimeCol = new TableColumn<>("支付时间");
        pTimeCol.setCellValueFactory(c -> { Object v = c.getValue().get("payment_time"); return new SimpleStringProperty(v != null ? v.toString() : ""); });
        pTimeCol.setSortable(false); pTimeCol.setPrefWidth(130);
        paymentsTable.getColumns().addAll(pIdCol, pAmtCol, pStatusCol, pTimeCol);
        paymentsTable.setFixedCellSize(26);
        double pth = paymentsTable.getFixedCellSize() * 8 + 34;
        paymentsTable.setPrefHeight(pth); paymentsTable.setMinHeight(pth); paymentsTable.setMaxHeight(pth);

        Button myPayments = new Button("我的支付记录");
        myPayments.setOnAction(e -> {
            try {
                List<Map<String, Object>> rows = paymentService.getMyPayments(user.getUserId(), "", 1, 100);
                paymentsTable.setItems(FXCollections.observableArrayList(rows));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        VBox body = new VBox(10,
                sectionBox("停车入场",
                        new HBox(8, reservationId, lotId, entryType, entry)),
                sectionBox("出场缴费",
                        new HBox(8, recordId, exitAndPay), out),
                sectionBox("停车记录", myRecords, recordsTable),
                sectionBox("支付记录", myPayments, paymentsTable));
        body.setPadding(new Insets(10));

        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab tab = new Tab("停车与支付", pageScroll);
        tab.setClosable(false);
        return tab;
    }

    private Tab userTab() {
        final TextArea out = new TextArea();
        final Runnable[] reloadRef = new Runnable[1];
        TableView<User> table = new TableView<>();
        TableColumn<User, String> id = new TableColumn<>("ID");
        id.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getUserId())));
        TableColumn<User, String> username = new TableColumn<>("\u7528\u6237\u540d"); // 用户名
        username.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        TableColumn<User, String> role = new TableColumn<>("\u89d2\u8272"); // 角色
        role.setCellValueFactory(c -> new SimpleStringProperty(roleLabel(c.getValue().getRole())));
        TableColumn<User, String> status = new TableColumn<>("\u72b6\u6001"); // 状态
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus() == 1 ? "\u542f\u7528" : "\u7981\u7528")); // 启用 | 禁用
        TableColumn<User, String> realName = new TableColumn<>("\u771f\u5b9e\u59d3\u540d"); // 真实姓名
        realName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRealName()));
        TableColumn<User, String> phone = new TableColumn<>("\u624b\u673a\u53f7"); // 手机号
        phone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        TableColumn<User, String> createTime = new TableColumn<>("\u521b\u5efa\u65f6\u95f4"); // 创建时间
        createTime.setCellValueFactory(c -> new SimpleStringProperty(fmtDateTime(c.getValue().getCreateTime())));
        table.getColumns().add(id);
        table.getColumns().add(username);
        table.getColumns().add(role);
        table.getColumns().add(status);
        table.getColumns().add(realName);
        table.getColumns().add(phone);
        table.getColumns().add(createTime);
        id.setSortable(false);
        username.setSortable(false);
        role.setSortable(false);
        status.setSortable(false);
        realName.setSortable(false);
        phone.setSortable(false);
        createTime.setSortable(false);

        final int pageSize = 8;
        final int[] pageNo = {1};
        table.setFixedCellSize(32);
        double tableHeight = table.getFixedCellSize() * pageSize + 34;
        table.setPrefHeight(tableHeight);
        table.setMinHeight(tableHeight);
        table.setMaxHeight(tableHeight);

        TextField userId = new TextField();
        TextField keyword = new TextField();
        userId.setPromptText("\u7528\u6237ID\uff08\u7528\u4e8e\u7981\u7528/\u542f\u7528/\u91cd\u7f6e/\u5220\u9664\uff09"); // 用户ID（用于禁用/启用/重置/删除）
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
            if (selected != null && selected.getUserId() != null) {
                userId.setText(String.valueOf(selected.getUserId()));
            }
        });
        keyword.setPromptText("\u5173\u952e\u5b57\uff08ID/\u7528\u6237\u540d/\u59d3\u540d\uff1b\u624b\u673a\u53f7\u4ec5\u652f\u6301\u5b8c\u657411\u4f4d\u6216\u540e4\u4f4d\uff09"); // 关键字（ID/用户名/姓名；手机号仅支持完整11位或后4位）
        keyword.setPrefWidth(420);
        ComboBox<String> roleBox = new ComboBox<>(FXCollections.observableArrayList("\u5168\u90e8", "\u7ba1\u7406\u5458", "\u8f66\u4f4d\u6240\u6709\u8005", "\u8f66\u4e3b")); // 全部 | 管理员 | 车位所有者 | 车主
        roleBox.setValue("\u5168\u90e8"); // 全部
        roleBox.setPrefWidth(130);
        ComboBox<String> statusBox = new ComboBox<>(FXCollections.observableArrayList("\u5168\u90e8", "\u542f\u7528", "\u7981\u7528")); // 全部 | 启用 | 禁用
        statusBox.setValue("\u5168\u90e8"); // 全部
        statusBox.setPrefWidth(110);
        TextField newPwd = new TextField("123456");
        newPwd.setPromptText("\u65b0\u5bc6\u7801"); // 新密码
        TextField addUsername = new TextField();
        addUsername.setPromptText("\u65b0\u7528\u6237\u540d"); // 新用户名
        addUsername.setPrefWidth(140);
        TextField addPassword = new TextField();
        addPassword.setPromptText("\u521d\u59cb\u5bc6\u7801"); // 初始密码
        addPassword.setPrefWidth(120);
        TextField addRealName = new TextField();
        addRealName.setPromptText("\u771f\u5b9e\u59d3\u540d\uff08\u53ef\u9009\uff09"); // 真实姓名（可选）
        addRealName.setPrefWidth(140);
        TextField addPhone = new TextField();
        addPhone.setPromptText("\u624b\u673a\u53f7\uff0811\u4f4d\uff0c\u53ef\u9009\uff09"); // 手机号（11位，可选）
        addPhone.setPrefWidth(170);
        ComboBox<String> addRole = new ComboBox<>(FXCollections.observableArrayList("\u8f66\u4e3b", "\u8f66\u4f4d\u6240\u6709\u8005")); // 车主 | 车位所有者
        addRole.setValue("\u8f66\u4e3b"); // 车主
        Label pageInfo = new Label("\u7b2c1\u9875"); // 第1页
        Runnable reload = () -> {
            try {
                Integer st = statusFilterValue(statusBox.getValue());
                String roleCode = roleCodeFromLabel(roleBox.getValue());
                List<User> rows = userAdminService.queryUsers(keyword.getText(), roleCode, st, pageNo[0], pageSize);
                if (rows.isEmpty() && pageNo[0] > 1) {
                    pageNo[0]--;
                    rows = userAdminService.queryUsers(keyword.getText(), roleCode, st, pageNo[0], pageSize);
                }
                table.setItems(FXCollections.observableArrayList(rows));
                pageInfo.setText("\u7b2c" + pageNo[0] + "\u9875\uff08\u6bcf\u9875" + pageSize + "\u6761\uff0c\u5f53\u524d" + rows.size() + "\u6761\uff09"); // 第 | 页（每页 | 条，当前 | 条）
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        };
        reloadRef[0] = reload;

        TableColumn<User, Void> userActionCol = createActionColumn("操作", user -> {
            if (user.getUserId() == PROTECTED_ADMIN_ID) {
                showAlert("管理员账号不可删除"); return;
            }
            User snapshot = user;
            pushRollback("用户ID=" + user.getUserId(), "用户管理", () -> {
                try {
                    snapshot.setPassword("123456");
                    long newId = userAdminService.createUser(snapshot);
                    out.appendText("用户已恢复，新ID=" + newId + "，临时密码=123456，请尽快修改\n");
                } catch (Exception ex) { showAlert("回滚失败：" + ex.getMessage()); }
            });
            try {
                userAdminService.deleteUser(user.getUserId());
                out.appendText("删除成功\n");
                addOperationLog(LOG_DELETE, formatModuleLog("用户管理", "删除用户ID=" + user.getUserId()));
                reload.run();
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        table.getColumns().add(userActionCol);

        Button query = new Button("\u67e5\u8be2"); // 查询
        query.setOnAction(e -> {
            pageNo[0] = 1;
            reload.run();
        });
        Button prevPage = new Button("\u4e0a\u4e00\u9875"); // 上一页
        prevPage.setOnAction(e -> {
            if (pageNo[0] <= 1) {
                out.appendText("\u63d0\u793a\uff1a\u5df2\u662f\u7b2c\u4e00\u9875\n"); // 提示：已是第一页\\n
                return;
            }
            pageNo[0]--;
            reload.run();
        });
        Button nextPage = new Button("\u4e0b\u4e00\u9875"); // 下一页
        nextPage.setOnAction(e -> {
            if (table.getItems() == null || table.getItems().size() < pageSize) {
                out.appendText("\u63d0\u793a\uff1a\u5df2\u662f\u6700\u540e\u4e00\u9875\n"); // 提示：已是最后一页\\n
                return;
            }
            pageNo[0]++;
            reload.run();
        });
        Button addUser = new Button("\u65b0\u589e\u7528\u6237"); // 新增用户
        Runnable resetManageForm = () -> {
            userId.clear();
            newPwd.clear();
        };
        addUser.setOnAction(e -> {
            try {
                User nu = new User();
                nu.setUsername(addUsername.getText() == null ? "" : addUsername.getText().trim());
                nu.setPassword(addPassword.getText() == null ? "" : addPassword.getText().trim());
                nu.setRealName(addRealName.getText() == null ? "" : addRealName.getText().trim());
                nu.setPhone(addPhone.getText() == null ? "" : addPhone.getText().trim());
                nu.setRole("\u8f66\u4f4d\u6240\u6709\u8005".equals(addRole.getValue()) ? "OWNER" : "CAR_OWNER"); // 车位所有者
                long uid = userAdminService.createUser(nu);
                out.appendText("\u65b0\u589e\u6210\u529f\uff0c\u7528\u6237ID\uff08\u81ea\u52a8\u751f\u6210\uff09=" + uid + "\n"); // 新增成功，用户ID（自动生成）=
                addOperationLog(LOG_ADD, formatModuleLog("用户管理", "新增用户ID=" + uid)); // 用户管理 | 新增用户ID=
                addUsername.clear();
                addPassword.clear();
                addRealName.clear();
                addPhone.clear();
                addRole.setValue("\u8f66\u4e3b"); // 车主
                pageNo[0] = 1;
                reload.run();
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });
        Button disable = new Button("\u7981\u7528"); // 禁用
        disable.setOnAction(e -> run(
                () -> userAdminService.changeStatus(requireLong(userId, "\u7528\u6237ID"), 0),
                out,
                () -> {
                    resetManageForm.run();
                    reload.run();
                },
                LOG_UPDATE,
                "用户管理",
                () -> "禁用用户ID=" + (userId.getText() == null ? "" : userId.getText().trim())
        )); // 用户ID
        Button enable = new Button("\u542f\u7528"); // 启用
        enable.setOnAction(e -> run(
                () -> userAdminService.changeStatus(requireLong(userId, "\u7528\u6237ID"), 1),
                out,
                () -> {
                    resetManageForm.run();
                    reload.run();
                },
                LOG_UPDATE,
                "用户管理",
                () -> "启用用户ID=" + (userId.getText() == null ? "" : userId.getText().trim())
        )); // 用户ID
        Button reset = new Button("\u91cd\u7f6e\u5bc6\u7801"); // 重置密码
        reset.setOnAction(e -> run(
                () -> userAdminService.resetPassword(requireLong(userId, "\u7528\u6237ID"), newPwd.getText().trim()),
                out,
                resetManageForm,
                LOG_UPDATE,
                "用户管理",
                () -> "重置密码用户ID=" + (userId.getText() == null ? "" : userId.getText().trim())
        )); // 用户ID
        Button deleteUser = new Button("\u5220\u9664\u7528\u6237"); // 删除用户
        deleteUser.setOnAction(e -> {
            try {
                out.clear();
                long uid = requireLong(userId, "\u7528\u6237ID"); // 用户ID
                if (uid == PROTECTED_ADMIN_ID) {
                    out.appendText("\u9519\u8bef\uff1a\u7ba1\u7406\u5458\u8d26\u53f7\u4e0d\u53ef\u5220\u9664\n"); // 错误：管理员账号不可删除\\n

                    return;
                }
                userAdminService.deleteUser(uid);
                out.appendText("\u5220\u9664\u6210\u529f\n"); // 删除成功\\n
                addOperationLog(LOG_DELETE, formatModuleLog("用户管理", "删除用户ID=" + uid)); // 用户管理 | 删除用户ID=
                resetManageForm.run();

                reload.run();
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        FlowPane queryRow = new FlowPane(8, 8, keyword, roleBox, statusBox, query);
        queryRow.setPrefWrapLength(1200);
        HBox.setHgrow(keyword, Priority.ALWAYS);
        HBox pageRow = new HBox(8, prevPage, nextPage, pageInfo);

        FlowPane addRow = new FlowPane(8, 8,
                new Label("\u7528\u6237\u540d"), addUsername, // 用户名
                new Label("\u5bc6\u7801"), addPassword, // 密码
                new Label("\u771f\u5b9e\u59d3\u540d"), addRealName, // 真实姓名
                new Label("\u624b\u673a\u53f7"), addPhone, // 手机号
                new Label("\u89d2\u8272"), addRole, // 角色
                addUser);
        addRow.setPrefWrapLength(1200);

        FlowPane manageRow = new FlowPane(8, 8,
                new Label("\u7528\u6237ID"), userId, // 用户ID
                new Label("\u65b0\u5bc6\u7801"), newPwd); // 新密码
        manageRow.setPrefWrapLength(1200);

        Button rollbackBtn = createRollbackButton(out, reload);
        FlowPane actionRow = new FlowPane(8, 8, disable, enable, reset, deleteUser, rollbackBtn);
        actionRow.setPrefWrapLength(1200);

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", queryRow, pageRow), // 查询区
                sectionBox("\u67e5\u8be2\u7ed3\u679c", table), // 查询结果
                sectionBox("\u65b0\u589e\u533a\uff08ID\u81ea\u52a8\u751f\u6210\uff09", addRow), // 新增区（ID自动生成）
                sectionBox("\u4fee\u6539/\u5220\u9664\u533a\uff08\u6309\u7528\u6237ID\u64cd\u4f5c\uff09", manageRow, actionRow), // 修改/删除区（按用户ID操作）
                out);
        body.setPadding(new Insets(10));
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reload.run();
        Tab tab = new Tab("\u7528\u6237\u7ba1\u7406", pageScroll); // 用户管理
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                pageScroll.setVvalue(0);
            }
        });
        return tab;
    }

    private Tab parkingLotTab() {
        TextArea out = new TextArea();
        out.setEditable(false);

        TableView<ParkingLot> table = new TableView<>();
        table.setEditable(true);

        TableColumn<ParkingLot, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getLotId())));
        idCol.setEditable(false);
        idCol.setSortable(false);
        idCol.setPrefWidth(50);

        TableColumn<ParkingLot, String> nameCol = new TableColumn<>("名称");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLotName()));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> {
            try {
                parkingLotService.updateLotField(e.getRowValue().getLotId(), "lot_name", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("停车场管理", "修改名称 停车场ID=" + e.getRowValue().getLotId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        nameCol.setSortable(false);
        nameCol.setPrefWidth(140);

        TableColumn<ParkingLot, String> addrCol = new TableColumn<>("地址");
        addrCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getAddress()));
        addrCol.setCellFactory(TextFieldTableCell.forTableColumn());
        addrCol.setOnEditCommit(e -> {
            try {
                parkingLotService.updateLotField(e.getRowValue().getLotId(), "address", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("停车场管理", "修改地址 停车场ID=" + e.getRowValue().getLotId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        addrCol.setSortable(false);
        addrCol.setPrefWidth(200);

        TableColumn<ParkingLot, String> totalCol = new TableColumn<>("总车位数");
        totalCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getTotalSpaces() != null ? String.valueOf(c.getValue().getTotalSpaces()) : ""));
        totalCol.setCellFactory(TextFieldTableCell.forTableColumn());
        totalCol.setOnEditCommit(e -> {
            try {
                parkingLotService.updateLotField(e.getRowValue().getLotId(), "total_spaces", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("停车场管理", "修改总车位数 停车场ID=" + e.getRowValue().getLotId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        totalCol.setSortable(false);
        totalCol.setPrefWidth(90);

        java.util.List<String> timeOpts = java.util.List.of("00:00", "06:00", "07:00", "08:00", "09:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00", "22:00", "23:59");

        TableColumn<ParkingLot, String> openCol = new TableColumn<>("开放时间");
        openCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getOpenTime() != null ? c.getValue().getOpenTime().toString() : ""));
        openCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(timeOpts)));
        openCol.setOnEditCommit(e -> {
            try {
                parkingLotService.updateLotField(e.getRowValue().getLotId(), "open_time", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("停车场管理", "修改开放时间 停车场ID=" + e.getRowValue().getLotId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        openCol.setSortable(false);
        openCol.setPrefWidth(90);

        TableColumn<ParkingLot, String> closeCol = new TableColumn<>("关闭时间");
        closeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getCloseTime() != null ? c.getValue().getCloseTime().toString() : ""));
        closeCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(timeOpts)));
        closeCol.setOnEditCommit(e -> {
            try {
                parkingLotService.updateLotField(e.getRowValue().getLotId(), "close_time", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("停车场管理", "修改关闭时间 停车场ID=" + e.getRowValue().getLotId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        closeCol.setSortable(false);
        closeCol.setPrefWidth(90);

        TableColumn<ParkingLot, String> descCol = new TableColumn<>("备注");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDescription() != null ? c.getValue().getDescription() : ""));
        descCol.setCellFactory(TextFieldTableCell.forTableColumn());
        descCol.setOnEditCommit(e -> {
            try {
                parkingLotService.updateLotField(e.getRowValue().getLotId(), "description", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("停车场管理", "修改备注 停车场ID=" + e.getRowValue().getLotId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        descCol.setSortable(false);
        descCol.setPrefWidth(150);

        table.getColumns().addAll(idCol, nameCol, addrCol, totalCol, openCol, closeCol, descCol);

        final int pageSize = 8;
        final int[] pageNo = {1};
        table.setFixedCellSize(32);
        double tableHeight = table.getFixedCellSize() * pageSize + 34;
        table.setPrefHeight(tableHeight);
        table.setMinHeight(tableHeight);
        table.setMaxHeight(tableHeight);

        TextField keyword = new TextField();
        keyword.setPromptText("输入ID或名称/地址关键字（留空=全部）");
        keyword.setPrefWidth(420);

        Label pageInfo = new Label("第1页");
        Runnable reload = () -> {
            try {
                List<ParkingLot> rows = parkingLotService.queryLots(keyword.getText(), pageNo[0], pageSize);
                if (rows.isEmpty() && pageNo[0] > 1) {
                    pageNo[0]--;
                    rows = parkingLotService.queryLots(keyword.getText(), pageNo[0], pageSize);
                }
                table.setItems(FXCollections.observableArrayList(rows));
                pageInfo.setText("第" + pageNo[0] + "页（每页" + pageSize + "条，当前" + rows.size() + "条）");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        };

        TableColumn<ParkingLot, Void> actionCol = createActionColumn("操作", lot -> {
            ParkingLot snapshot = new ParkingLot();
            snapshot.setLotName(lot.getLotName());
            snapshot.setAddress(lot.getAddress());
            snapshot.setTotalSpaces(lot.getTotalSpaces());
            snapshot.setOpenTime(lot.getOpenTime());
            snapshot.setCloseTime(lot.getCloseTime());
            snapshot.setDescription(lot.getDescription());
            pushRollback("停车场ID=" + lot.getLotId(), "停车场管理", () -> {
                try {
                    long newId = parkingLotService.addLot(snapshot);
                    out.appendText("停车场已恢复，新ID=" + newId + "\n");
                } catch (Exception ex) { showAlert("回滚失败：" + ex.getMessage()); }
            });
            try {
                parkingLotService.removeLot(lot.getLotId());
                out.appendText("删除成功\n");
                addOperationLog(LOG_DELETE, formatModuleLog("停车场管理", "删除停车场ID=" + lot.getLotId()));
                reload.run();
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        table.getColumns().add(actionCol);

        Button query = new Button("查询");
        query.setOnAction(e -> { pageNo[0] = 1; reload.run(); });
        Button prevPage = new Button("上一页");
        prevPage.setOnAction(e -> {
            if (pageNo[0] <= 1) { out.appendText("提示：已是第一页\n"); return; }
            pageNo[0]--;
            reload.run();
        });
        Button nextPage = new Button("下一页");
        nextPage.setOnAction(e -> {
            if (table.getItems() == null || table.getItems().size() < pageSize) {
                out.appendText("提示：已是最后一页\n"); return;
            }
            pageNo[0]++;
            reload.run();
        });

        TextField addName = new TextField();
        TextField addAddress = new TextField();
        TextField addTotal = new TextField();
        ComboBox<String> addOpen = new ComboBox<>(FXCollections.observableArrayList(timeOpts));
        addOpen.setValue("08:00");
        ComboBox<String> addClose = new ComboBox<>(FXCollections.observableArrayList(timeOpts));
        addClose.setValue("22:00");
        TextField addDesc = new TextField();

        addName.setPromptText("停车场名称（必填）");
        addAddress.setPromptText("地址（必填）");
        addTotal.setPromptText("总车位数（例如：200）");
        addDesc.setPromptText("备注（可选）");

        addName.setPrefWidth(160);
        addAddress.setPrefWidth(200);
        addTotal.setPrefWidth(150);
        addOpen.setPrefWidth(110);
        addClose.setPrefWidth(110);
        addDesc.setPrefWidth(180);

        Button addBtn = new Button("新增");
        addBtn.setOnAction(e -> {
            try {
                ParkingLot lot = new ParkingLot();
                lot.setLotName(addName.getText().trim());
                lot.setAddress(addAddress.getText().trim());
                lot.setTotalSpaces(requireInt(addTotal, "总车位数"));
                lot.setOpenTime(LocalTime.parse(addOpen.getValue()));
                lot.setCloseTime(LocalTime.parse(addClose.getValue()));
                String desc = addDesc.getText() == null ? "" : addDesc.getText().trim();
                if (!desc.isEmpty()) lot.setDescription(desc);
                long newId = parkingLotService.addLot(lot);
                out.appendText("新增成功，停车场ID=" + newId + "\n");
                addOperationLog(LOG_ADD, formatModuleLog("停车场管理", "新增停车场ID=" + newId));
                addName.clear(); addAddress.clear(); addTotal.clear(); addDesc.clear();
                addOpen.setValue("08:00"); addClose.setValue("22:00");
                pageNo[0] = 1;
                reload.run();
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        Button rollbackBtn = createRollbackButton(out, reload);

        HBox queryRow = new HBox(8, keyword, query);
        HBox.setHgrow(keyword, Priority.ALWAYS);
        HBox pageRow = new HBox(8, prevPage, nextPage, pageInfo);

        FlowPane addRow = new FlowPane(8, 8,
                new Label("名称"), addName,
                new Label("地址"), addAddress,
                new Label("总车位数"), addTotal,
                new Label("开放时间"), addOpen,
                new Label("关闭时间"), addClose,
                new Label("备注"), addDesc,
                addBtn);
        addRow.setPrefWrapLength(1200);

        VBox body = new VBox(10,
                sectionBox("查询区", queryRow, pageRow),
                sectionBox("查询结果", table),
                sectionBox("新增区（ID自动生成）", addRow),
                rollbackBtn,
                out);
        body.setPadding(new Insets(10));
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reload.run();
        Tab tab = new Tab("停车场管理", pageScroll);
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                pageScroll.setVvalue(0);
            }
        });
        return tab;
    }

    private Tab parkingSpaceTab() {
        TextArea out = new TextArea();
        out.setEditable(false);

        TableView<ParkingSpace> table = new TableView<>();
        table.setEditable(true);

        TableColumn<ParkingSpace, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getSpaceId())));
        idCol.setEditable(false);
        idCol.setSortable(false);
        idCol.setPrefWidth(50);

        TableColumn<ParkingSpace, String> numberCol = new TableColumn<>("车位编号");
        numberCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSpaceNumber()));
        numberCol.setCellFactory(TextFieldTableCell.forTableColumn());
        numberCol.setOnEditCommit(e -> {
            try {
                parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "space_number", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("车位管理", "修改编号 车位ID=" + e.getRowValue().getSpaceId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        numberCol.setSortable(false);
        numberCol.setPrefWidth(110);

        TableColumn<ParkingSpace, String> lotIdCol = new TableColumn<>("停车场ID");
        lotIdCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getLotId() != null ? String.valueOf(c.getValue().getLotId()) : ""));
        lotIdCol.setCellFactory(TextFieldTableCell.forTableColumn());
        lotIdCol.setOnEditCommit(e -> {
            try {
                parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "lot_id", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("车位管理", "修改停车场ID 车位ID=" + e.getRowValue().getSpaceId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        lotIdCol.setSortable(false);
        lotIdCol.setPrefWidth(90);

        TableColumn<ParkingSpace, String> ownerIdCol = new TableColumn<>("所有者ID");
        ownerIdCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getOwnerId() != null ? String.valueOf(c.getValue().getOwnerId()) : ""));
        ownerIdCol.setCellFactory(TextFieldTableCell.forTableColumn());
        ownerIdCol.setOnEditCommit(e -> {
            try {
                parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "owner_id", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("车位管理", "修改所有者ID 车位ID=" + e.getRowValue().getSpaceId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        ownerIdCol.setSortable(false);
        ownerIdCol.setPrefWidth(90);

        TableColumn<ParkingSpace, String> typeCol = new TableColumn<>("类型");
        typeCol.setCellValueFactory(c -> new SimpleStringProperty(spaceTypeLabel(c.getValue().getType())));
        typeCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("地上", "地下")));
        typeCol.setOnEditCommit(e -> {
            try {
                parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "type", spaceTypeCode(e.getNewValue()));
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("车位管理", "修改类型 车位ID=" + e.getRowValue().getSpaceId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        typeCol.setSortable(false);
        typeCol.setPrefWidth(80);

        TableColumn<ParkingSpace, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(spaceStatusLabel(c.getValue().getStatus())));
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("空闲", "已预约", "占用")));
        statusCol.setOnEditCommit(e -> {
            try {
                parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "status", spaceStatusCode(e.getNewValue()));
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("车位管理", "修改状态 车位ID=" + e.getRowValue().getSpaceId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        statusCol.setSortable(false);
        statusCol.setPrefWidth(80);

        java.util.List<String> timeOpts = java.util.List.of("00:00", "06:00", "07:00", "08:00", "09:00", "10:00", "12:00", "14:00", "16:00", "18:00", "20:00", "22:00", "23:59");

        TableColumn<ParkingSpace, String> startCol = new TableColumn<>("共享开始");
        startCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getShareStartTime() != null ? c.getValue().getShareStartTime().toString() : ""));
        startCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(timeOpts)));
        startCol.setOnEditCommit(e -> {
            try {
                parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "share_start_time", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("车位管理", "修改共享开始 车位ID=" + e.getRowValue().getSpaceId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        startCol.setSortable(false);
        startCol.setPrefWidth(90);

        TableColumn<ParkingSpace, String> endCol = new TableColumn<>("共享结束");
        endCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getShareEndTime() != null ? c.getValue().getShareEndTime().toString() : ""));
        endCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList(timeOpts)));
        endCol.setOnEditCommit(e -> {
            try {
                parkingSpaceService.updateSpaceField(e.getRowValue().getSpaceId(), "share_end_time", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("车位管理", "修改共享结束 车位ID=" + e.getRowValue().getSpaceId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        endCol.setSortable(false);
        endCol.setPrefWidth(90);

        table.getColumns().addAll(idCol, numberCol, lotIdCol, ownerIdCol, typeCol, statusCol, startCol, endCol);

        final int pageSize = 8;
        final int[] pageNo = {1};
        table.setFixedCellSize(32);
        double tableHeight = table.getFixedCellSize() * pageSize + 34;
        table.setPrefHeight(tableHeight);
        table.setMinHeight(tableHeight);
        table.setMaxHeight(tableHeight);

        TextField keyword = new TextField();
        keyword.setPromptText("查询关键字（车位编号/ID）");
        keyword.setPrefWidth(260);
        ComboBox<String> queryType = new ComboBox<>(FXCollections.observableArrayList("全部类型", "地上", "地下"));
        queryType.setValue("全部类型");
        queryType.setPrefWidth(110);
        ComboBox<String> queryStatus = new ComboBox<>(FXCollections.observableArrayList("全部状态", "空闲", "已预约", "占用"));
        queryStatus.setValue("全部状态");
        queryStatus.setPrefWidth(110);

        Label pageInfo = new Label("第1页");
        Runnable reload = () -> {
            try {
                String statusCode = "全部状态".equals(queryStatus.getValue()) ? "" : spaceStatusCode(queryStatus.getValue());
                List<ParkingSpace> rows = parkingSpaceService.querySpaces(keyword.getText(), statusCode, null, pageNo[0], pageSize);
                String typeFilter = "全部类型".equals(queryType.getValue()) ? "" : spaceTypeCode(queryType.getValue());
                if (!typeFilter.isEmpty()) {
                    rows = rows.stream().filter(s -> typeFilter.equalsIgnoreCase(s.getType())).collect(java.util.stream.Collectors.toList());
                }
                if (rows.isEmpty() && pageNo[0] > 1) {
                    pageNo[0]--;
                    rows = parkingSpaceService.querySpaces(keyword.getText(), statusCode, null, pageNo[0], pageSize);
                    if (!typeFilter.isEmpty()) {
                        String tf = typeFilter;
                        rows = rows.stream().filter(s -> tf.equalsIgnoreCase(s.getType())).collect(java.util.stream.Collectors.toList());
                    }
                }
                table.setItems(FXCollections.observableArrayList(rows));
                pageInfo.setText("第" + pageNo[0] + "页（每页" + pageSize + "条，当前" + rows.size() + "条）");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        };

        TableColumn<ParkingSpace, Void> actionCol = createActionColumn("操作", space -> {
            ParkingSpace snapshot = new ParkingSpace();
            snapshot.setLotId(space.getLotId());
            snapshot.setOwnerId(space.getOwnerId());
            snapshot.setSpaceNumber(space.getSpaceNumber());
            snapshot.setType(space.getType());
            snapshot.setStatus(space.getStatus());
            snapshot.setShareStartTime(space.getShareStartTime());
            snapshot.setShareEndTime(space.getShareEndTime());
            pushRollback("车位ID=" + space.getSpaceId(), "车位管理", () -> {
                try {
                    long newId = parkingSpaceService.addSpace(snapshot);
                    out.appendText("车位已恢复，新ID=" + newId + "\n");
                } catch (Exception ex) { showAlert("回滚失败：" + ex.getMessage()); }
            });
            try {
                parkingSpaceService.removeSpace(space.getSpaceId());
                out.appendText("删除成功\n");
                addOperationLog(LOG_DELETE, formatModuleLog("车位管理", "删除车位ID=" + space.getSpaceId()));
                reload.run();
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        table.getColumns().add(actionCol);

        Button query = new Button("查询");
        query.setOnAction(e -> { pageNo[0] = 1; reload.run(); });
        Button prevPage = new Button("上一页");
        prevPage.setOnAction(e -> {
            if (pageNo[0] <= 1) { out.appendText("提示：已是第一页\n"); return; }
            pageNo[0]--;
            reload.run();
        });
        Button nextPage = new Button("下一页");
        nextPage.setOnAction(e -> {
            if (table.getItems() == null || table.getItems().size() < pageSize) {
                out.appendText("提示：已是最后一页\n"); return;
            }
            pageNo[0]++;
            reload.run();
        });

        TextField addLotId = new TextField();
        TextField addOwnerId = new TextField();
        TextField addNumber = new TextField();
        ComboBox<String> addType = new ComboBox<>(FXCollections.observableArrayList("地上", "地下"));
        addType.setValue("地上");
        ComboBox<String> addStatus = new ComboBox<>(FXCollections.observableArrayList("空闲", "已预约", "占用"));
        addStatus.setValue("空闲");
        ComboBox<String> addStart = new ComboBox<>(FXCollections.observableArrayList(timeOpts));
        addStart.setValue("08:00");
        ComboBox<String> addEnd = new ComboBox<>(FXCollections.observableArrayList(timeOpts));
        addEnd.setValue("22:00");

        addLotId.setPromptText("停车场ID（例如：1）");
        addOwnerId.setPromptText("所有者ID（例如：2）");
        addNumber.setPromptText("车位编号");
        addLotId.setPrefWidth(110);
        addOwnerId.setPrefWidth(110);
        addNumber.setPrefWidth(150);
        addType.setPrefWidth(90);
        addStatus.setPrefWidth(90);
        addStart.setPrefWidth(110);
        addEnd.setPrefWidth(110);

        Button addBtn = new Button("新增");
        addBtn.setOnAction(e -> {
            try {
                ParkingSpace space = new ParkingSpace();
                space.setLotId(requireLong(addLotId, "停车场ID"));
                space.setOwnerId(requireLong(addOwnerId, "所有者ID"));
                space.setSpaceNumber(addNumber.getText().trim());
                space.setType(spaceTypeCode(addType.getValue()));
                space.setStatus(spaceStatusCode(addStatus.getValue()));
                space.setShareStartTime(LocalTime.parse(addStart.getValue()));
                space.setShareEndTime(LocalTime.parse(addEnd.getValue()));
                long newId = parkingSpaceService.addSpace(space);
                out.appendText("新增成功，车位ID=" + newId + "\n");
                addOperationLog(LOG_ADD, formatModuleLog("车位管理", "新增车位ID=" + newId));
                addLotId.clear(); addOwnerId.clear(); addNumber.clear();
                addType.setValue("地上"); addStatus.setValue("空闲");
                addStart.setValue("08:00"); addEnd.setValue("22:00");
                pageNo[0] = 1;
                reload.run();
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        Button rollbackBtn = createRollbackButton(out, reload);

        HBox queryRow = new HBox(8, keyword, queryType, queryStatus, query);
        HBox.setHgrow(keyword, Priority.ALWAYS);
        HBox pageRow = new HBox(8, prevPage, nextPage, pageInfo);

        FlowPane addRow = new FlowPane(8, 8,
                new Label("停车场ID"), addLotId,
                new Label("所有者ID"), addOwnerId,
                new Label("编号"), addNumber,
                new Label("类型"), addType,
                new Label("状态"), addStatus,
                new Label("共享开始"), addStart,
                new Label("共享结束"), addEnd,
                addBtn);
        addRow.setPrefWrapLength(1200);

        VBox body = new VBox(10,
                sectionBox("查询区", queryRow, pageRow),
                sectionBox("查询结果", table),
                sectionBox("新增区（ID自动生成）", addRow),
                rollbackBtn,
                out);
        body.setPadding(new Insets(10));
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reload.run();
        Tab tab = new Tab("车位管理", pageScroll);
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                pageScroll.setVvalue(0);
            }
        });
        return tab;
    }

    private Tab pricingRuleTab() {
        TextArea out = new TextArea();
        out.setEditable(false);

        TableView<PricingRule> table = new TableView<>();
        table.setEditable(true);

        TableColumn<PricingRule, String> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getRuleId())));
        idCol.setEditable(false);
        idCol.setSortable(false);
        idCol.setPrefWidth(50);

        TableColumn<PricingRule, String> nameCol = new TableColumn<>("规则名称");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRuleName()));
        nameCol.setCellFactory(TextFieldTableCell.forTableColumn());
        nameCol.setOnEditCommit(e -> {
            try {
                pricingRuleService.updateRuleField(e.getRowValue().getRuleId(), "rule_name", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("计费规则", "修改名称 规则ID=" + e.getRowValue().getRuleId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        nameCol.setSortable(false);
        nameCol.setPrefWidth(150);

        TableColumn<PricingRule, String> chargeTypeCol = new TableColumn<>("计费方式");
        chargeTypeCol.setCellValueFactory(c -> new SimpleStringProperty(chargeTypeLabel(c.getValue().getChargeType())));
        chargeTypeCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("按时计费", "按次计费")));
        chargeTypeCol.setOnEditCommit(e -> {
            try {
                pricingRuleService.updateRuleField(e.getRowValue().getRuleId(), "charge_type", chargeTypeCode(e.getNewValue()));
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("计费规则", "修改计费方式 规则ID=" + e.getRowValue().getRuleId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        chargeTypeCol.setSortable(false);
        chargeTypeCol.setPrefWidth(90);

        TableColumn<PricingRule, String> unitPriceCol = new TableColumn<>("单价");
        unitPriceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUnitPrice() != null ? c.getValue().getUnitPrice().toString() : ""));
        unitPriceCol.setCellFactory(TextFieldTableCell.forTableColumn());
        unitPriceCol.setOnEditCommit(e -> {
            try {
                pricingRuleService.updateRuleField(e.getRowValue().getRuleId(), "unit_price", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("计费规则", "修改单价 规则ID=" + e.getRowValue().getRuleId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        unitPriceCol.setSortable(false);
        unitPriceCol.setPrefWidth(80);

        TableColumn<PricingRule, String> unitTimeCol = new TableColumn<>("单位时长(分)");
        unitTimeCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUnitTime() != null ? String.valueOf(c.getValue().getUnitTime()) : ""));
        unitTimeCol.setCellFactory(TextFieldTableCell.forTableColumn());
        unitTimeCol.setOnEditCommit(e -> {
            try {
                pricingRuleService.updateRuleField(e.getRowValue().getRuleId(), "unit_time", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("计费规则", "修改单位时长 规则ID=" + e.getRowValue().getRuleId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        unitTimeCol.setSortable(false);
        unitTimeCol.setPrefWidth(100);

        TableColumn<PricingRule, String> fixedPriceCol = new TableColumn<>("固定价");
        fixedPriceCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getFixedPrice() != null ? c.getValue().getFixedPrice().toString() : ""));
        fixedPriceCol.setCellFactory(TextFieldTableCell.forTableColumn());
        fixedPriceCol.setOnEditCommit(e -> {
            try {
                pricingRuleService.updateRuleField(e.getRowValue().getRuleId(), "fixed_price", e.getNewValue());
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("计费规则", "修改固定价 规则ID=" + e.getRowValue().getRuleId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        fixedPriceCol.setSortable(false);
        fixedPriceCol.setPrefWidth(80);

        TableColumn<PricingRule, String> spaceTypeCol = new TableColumn<>("适用车位");
        spaceTypeCol.setCellValueFactory(c -> new SimpleStringProperty(spaceTypeLabel(c.getValue().getApplicableSpaceType())));
        spaceTypeCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("地上", "地下")));
        spaceTypeCol.setOnEditCommit(e -> {
            try {
                pricingRuleService.updateRuleField(e.getRowValue().getRuleId(), "applicable_space_type", spaceTypeCode(e.getNewValue()));
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("计费规则", "修改适用车位 规则ID=" + e.getRowValue().getRuleId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        spaceTypeCol.setSortable(false);
        spaceTypeCol.setPrefWidth(80);

        TableColumn<PricingRule, String> statusCol = new TableColumn<>("状态");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(enabledLabel(c.getValue().getStatus())));
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("启用", "禁用")));
        statusCol.setOnEditCommit(e -> {
            try {
                pricingRuleService.updateRuleField(e.getRowValue().getRuleId(), "status", String.valueOf(enabledStatusValue(e.getNewValue())));
                out.appendText("修改成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("计费规则", "修改状态 规则ID=" + e.getRowValue().getRuleId()));
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        statusCol.setSortable(false);
        statusCol.setPrefWidth(70);

        table.getColumns().addAll(idCol, nameCol, chargeTypeCol, unitPriceCol, unitTimeCol, fixedPriceCol, spaceTypeCol, statusCol);

        final int pageSize = 8;
        final int[] pageNo = {1};
        table.setFixedCellSize(32);
        double tableHeight = table.getFixedCellSize() * pageSize + 34;
        table.setPrefHeight(tableHeight);
        table.setMinHeight(tableHeight);
        table.setMaxHeight(tableHeight);

        TextField keyword = new TextField();
        keyword.setPromptText("规则ID/名称（留空=全部）");
        keyword.setPrefWidth(260);
        ComboBox<String> queryStatus = new ComboBox<>(FXCollections.observableArrayList("全部", "启用", "禁用"));
        queryStatus.setValue("全部");
        queryStatus.setPrefWidth(100);

        Label pageInfo = new Label("第1页");
        Runnable reload = () -> {
            try {
                Integer st = "全部".equals(queryStatus.getValue()) ? null : enabledStatusValue(queryStatus.getValue());
                List<PricingRule> rows = pricingRuleService.queryRules(keyword.getText(), "", st, pageNo[0], pageSize);
                if (rows.isEmpty() && pageNo[0] > 1) {
                    pageNo[0]--;
                    rows = pricingRuleService.queryRules(keyword.getText(), "", st, pageNo[0], pageSize);
                }
                table.setItems(FXCollections.observableArrayList(rows));
                pageInfo.setText("第" + pageNo[0] + "页（每页" + pageSize + "条，当前" + rows.size() + "条）");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        };

        TableColumn<PricingRule, Void> actionCol = createActionColumn("操作", rule -> {
            PricingRule snapshot = new PricingRule();
            snapshot.setRuleName(rule.getRuleName());
            snapshot.setChargeType(rule.getChargeType());
            snapshot.setUnitPrice(rule.getUnitPrice());
            snapshot.setUnitTime(rule.getUnitTime());
            snapshot.setFixedPrice(rule.getFixedPrice());
            snapshot.setApplicableSpaceType(rule.getApplicableSpaceType());
            snapshot.setStatus(rule.getStatus());
            pushRollback("规则ID=" + rule.getRuleId(), "计费规则", () -> {
                try {
                    long newId = pricingRuleService.addRule(snapshot);
                    out.appendText("规则已恢复，新ID=" + newId + "\n");
                } catch (Exception ex) { showAlert("回滚失败：" + ex.getMessage()); }
            });
            try {
                pricingRuleService.removeRule(rule.getRuleId());
                out.appendText("删除成功\n");
                addOperationLog(LOG_DELETE, formatModuleLog("计费规则", "删除规则ID=" + rule.getRuleId()));
                reload.run();
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        table.getColumns().add(actionCol);

        Button query = new Button("查询");
        query.setOnAction(e -> { pageNo[0] = 1; reload.run(); });
        Button prevPage = new Button("上一页");
        prevPage.setOnAction(e -> {
            if (pageNo[0] <= 1) { out.appendText("提示：已是第一页\n"); return; }
            pageNo[0]--;
            reload.run();
        });
        Button nextPage = new Button("下一页");
        nextPage.setOnAction(e -> {
            if (table.getItems() == null || table.getItems().size() < pageSize) {
                out.appendText("提示：已是最后一页\n"); return;
            }
            pageNo[0]++;
            reload.run();
        });

        TextField addName = new TextField();
        ComboBox<String> addChargeType = new ComboBox<>(FXCollections.observableArrayList("按时计费", "按次计费"));
        addChargeType.setValue("按时计费");
        TextField addUnitPrice = new TextField();
        TextField addUnitTime = new TextField();
        TextField addFixedPrice = new TextField();
        ComboBox<String> addSpaceType = new ComboBox<>(FXCollections.observableArrayList("地上", "地下"));
        addSpaceType.setValue("地上");
        ComboBox<String> addStatus = new ComboBox<>(FXCollections.observableArrayList("启用", "禁用"));
        addStatus.setValue("启用");

        addName.setPromptText("规则名称（例如：地上按时计费）");
        addUnitPrice.setPromptText("单价(元)");
        addUnitTime.setPromptText("单位时长(分)");
        addFixedPrice.setPromptText("固定价格(元)");

        addName.setPrefWidth(180);
        addChargeType.setPrefWidth(110);
        addUnitPrice.setPrefWidth(100);
        addUnitTime.setPrefWidth(110);
        addFixedPrice.setPrefWidth(110);
        addSpaceType.setPrefWidth(80);
        addStatus.setPrefWidth(80);

        Label pricingHelp = new Label("说明：按时计费请填写[单价+单位时长]；按次计费请填写[固定价格]。");

        Button addBtn = new Button("新增");
        addBtn.setOnAction(e -> {
            try {
                long newId = pricingRuleService.addRule(ruleFromForCreate(addName, addChargeType, addUnitPrice, addUnitTime, addFixedPrice, addSpaceType, addStatus));
                out.appendText("新增成功，规则ID=" + newId + "\n");
                addOperationLog(LOG_ADD, formatModuleLog("计费规则", "新增规则ID=" + newId));
                addName.clear(); addUnitPrice.clear(); addUnitTime.clear(); addFixedPrice.clear();
                addChargeType.setValue("按时计费"); addSpaceType.setValue("地上"); addStatus.setValue("启用");
                pageNo[0] = 1;
                reload.run();
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });

        Button rollbackBtn = createRollbackButton(out, reload);

        HBox queryRow = new HBox(8, keyword, queryStatus, query);
        HBox.setHgrow(keyword, Priority.ALWAYS);
        HBox pageRow = new HBox(8, prevPage, nextPage, pageInfo);

        FlowPane addRow = new FlowPane(8, 8,
                new Label("名称"), addName,
                new Label("计费方式"), addChargeType,
                new Label("单价"), addUnitPrice,
                new Label("时长(分)"), addUnitTime,
                new Label("固定价"), addFixedPrice,
                new Label("适用车位"), addSpaceType,
                new Label("状态"), addStatus,
                addBtn);
        addRow.setPrefWrapLength(1200);

        VBox body = new VBox(10,
                sectionBox("查询区", queryRow, pageRow),
                sectionBox("查询结果", table),
                sectionBox("新增区（ID自动生成）", addRow, pricingHelp),
                rollbackBtn,
                out);
        body.setPadding(new Insets(10));
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reload.run();
        Tab tab = new Tab("计费规则", pageScroll);
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                pageScroll.setVvalue(0);
            }
        });
        return tab;
    }

    private Tab settlementTab() {
        TextArea out = new TextArea();
        out.setEditable(false);

        TableView<Map<String, Object>> table = new TableView<>();
        table.setEditable(true);

        final Runnable[] reloadRef = new Runnable[1];

        TableColumn<Map<String, Object>, String> revenueIdCol = new TableColumn<>("收益记录ID");
        revenueIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("revenue_id"))));
        revenueIdCol.setEditable(false);
        revenueIdCol.setSortable(false);
        revenueIdCol.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> ownerIdCol = new TableColumn<>("收益人ID");
        ownerIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("owner_id"))));
        ownerIdCol.setEditable(false);
        ownerIdCol.setSortable(false);
        ownerIdCol.setPrefWidth(90);

        TableColumn<Map<String, Object>, String> payerIdCol = new TableColumn<>("支付人ID");
        payerIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("payer_user_id"))));
        payerIdCol.setEditable(false);
        payerIdCol.setSortable(false);
        payerIdCol.setPrefWidth(90);

        TableColumn<Map<String, Object>, String> spaceIdCol = new TableColumn<>("车位ID");
        spaceIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("space_id"))));
        spaceIdCol.setEditable(false);
        spaceIdCol.setSortable(false);
        spaceIdCol.setPrefWidth(70);

        TableColumn<Map<String, Object>, String> paymentIdCol = new TableColumn<>("支付记录ID");
        paymentIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("payment_id"))));
        paymentIdCol.setEditable(false);
        paymentIdCol.setSortable(false);
        paymentIdCol.setPrefWidth(100);

        TableColumn<Map<String, Object>, String> amountCol = new TableColumn<>("收益金额");
        amountCol.setCellValueFactory(c -> {
            Object val = c.getValue().get("income_amount");
            return new SimpleStringProperty(val != null ? val.toString() : "");
        });
        amountCol.setEditable(false);
        amountCol.setSortable(false);
        amountCol.setPrefWidth(90);

        TableColumn<Map<String, Object>, String> statusCol = new TableColumn<>("结算状态");
        statusCol.setCellValueFactory(c -> {
            Object val = c.getValue().get("settle_status");
            return new SimpleStringProperty("SETTLED".equals(val) ? "已结算" : "未结算");
        });
        statusCol.setCellFactory(ComboBoxTableCell.forTableColumn(FXCollections.observableArrayList("未结算", "已结算")));
        statusCol.setOnEditCommit(e -> {
            try {
                Object revenueIdObj = e.getRowValue().get("revenue_id");
                long rid = ((Number) revenueIdObj).longValue();
                if ("已结算".equals(e.getNewValue())) {
                    revenueService.settleRevenue(rid);
                }
                out.appendText("审核成功\n");
                addOperationLog(LOG_UPDATE, formatModuleLog("结算审核", "审核收益记录ID=" + rid));
                reloadRef[0].run();
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        statusCol.setSortable(false);
        statusCol.setPrefWidth(90);

        TableColumn<Map<String, Object>, String> settleTimeCol = new TableColumn<>("结算时间");
        settleTimeCol.setCellValueFactory(c -> {
            Object val = c.getValue().get("settle_time");
            return new SimpleStringProperty(val != null ? val.toString() : "");
        });
        settleTimeCol.setEditable(false);
        settleTimeCol.setSortable(false);
        settleTimeCol.setPrefWidth(140);

        table.getColumns().addAll(revenueIdCol, ownerIdCol, payerIdCol, spaceIdCol, paymentIdCol, amountCol, statusCol, settleTimeCol);

        final int pageSize = 8;
        final int[] pageNo = {1};
        table.setFixedCellSize(32);
        double tableHeight = table.getFixedCellSize() * pageSize + 34;
        table.setPrefHeight(tableHeight);
        table.setMinHeight(tableHeight);
        table.setMaxHeight(tableHeight);

        ComboBox<String> queryStatus = new ComboBox<>(FXCollections.observableArrayList("全部", "未结算", "已结算"));
        queryStatus.setValue("全部");
        queryStatus.setPrefWidth(120);

        Label pageInfo = new Label("第1页");
        Runnable reload = () -> {
            try {
                List<Map<String, Object>> rows = revenueService.queryRevenueForAdmin(settleStatusCode(queryStatus.getValue()), pageNo[0], pageSize);
                if (rows.isEmpty() && pageNo[0] > 1) {
                    pageNo[0]--;
                    rows = revenueService.queryRevenueForAdmin(settleStatusCode(queryStatus.getValue()), pageNo[0], pageSize);
                }
                table.setItems(FXCollections.observableArrayList(rows));
                pageInfo.setText("第" + pageNo[0] + "页（每页" + pageSize + "条，当前" + rows.size() + "条）");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        };
        reloadRef[0] = reload;

        // Action column with approve button (not delete)
        TableColumn<Map<String, Object>, Void> actionCol = new TableColumn<>("操作");
        actionCol.setCellFactory(c -> new TableCell<>() {
            private final Button approveBtn = new Button("审核");
            {
                approveBtn.setStyle("-fx-background-color: #28a745; -fx-text-fill: white; -fx-font-size: 11px;");
                approveBtn.setOnAction(e -> {
                    Map<String, Object> row = getTableView().getItems().get(getIndex());
                    if (row != null) {
                        try {
                            Object revenueIdObj = row.get("revenue_id");
                            long rid = ((Number) revenueIdObj).longValue();
                            revenueService.settleRevenue(rid);
                            out.appendText("审核成功，收益记录ID=" + rid + "\n");
                            addOperationLog(LOG_UPDATE, formatModuleLog("结算审核", "审核收益记录ID=" + rid));
                            reload.run();
                        } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
                    }
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                if (empty) { setGraphic(null); return; }
                Map<String, Object> row = getTableView().getItems().get(getIndex());
                if (row != null && "SETTLED".equals(row.get("settle_status"))) {
                    setGraphic(null); // already settled, hide button
                } else {
                    setGraphic(approveBtn);
                }
            }
        });
        actionCol.setSortable(false);
        actionCol.setPrefWidth(60);
        table.getColumns().add(actionCol);

        Button query = new Button("查询结算记录");
        query.setOnAction(e -> { pageNo[0] = 1; reload.run(); });
        Button prevPage = new Button("上一页");
        prevPage.setOnAction(e -> {
            if (pageNo[0] <= 1) { out.appendText("提示：已是第一页\n"); return; }
            pageNo[0]--;
            reload.run();
        });
        Button nextPage = new Button("下一页");
        nextPage.setOnAction(e -> {
            if (table.getItems() == null || table.getItems().size() < pageSize) {
                out.appendText("提示：已是最后一页\n"); return;
            }
            pageNo[0]++;
            reload.run();
        });

        HBox queryRow = new HBox(8, queryStatus, query);
        HBox.setHgrow(queryStatus, Priority.ALWAYS);
        HBox pageRow = new HBox(8, prevPage, nextPage, pageInfo);

        Label queryHint = new Label("说明：可按结算状态筛选；在[结算状态]列下拉可直接审核；已结算的记录不显示审核按钮。");

        VBox body = new VBox(10,
                sectionBox("查询区", queryRow, pageRow, queryHint),
                sectionBox("查询结果", table),
                out);
        body.setPadding(new Insets(10));
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reload.run();
        Tab tab = new Tab("结算审核", pageScroll);
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                pageScroll.setVvalue(0);
            }
        });
        return tab;
    }

    private Tab reportTab() {
        Label incomePlaceholder = new Label("请点击[收入排行]查看图表");
        incomePlaceholder.setWrapText(true);
        ScrollPane incomeChartPane = new ScrollPane(incomePlaceholder);
        incomeChartPane.setFitToHeight(true);
        incomeChartPane.setFitToWidth(true);
        incomeChartPane.setPrefHeight(380);
        incomeChartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        incomeChartPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        incomeChartPane.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0;");

        Label reservePlaceholder = new Label("请点击[预约排行]查看图表");
        reservePlaceholder.setWrapText(true);
        ScrollPane reserveChartPane = new ScrollPane(reservePlaceholder);
        reserveChartPane.setFitToHeight(true);
        reserveChartPane.setFitToWidth(true);
        reserveChartPane.setPrefHeight(380);
        reserveChartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reserveChartPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        reserveChartPane.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0;");

        Label usagePlaceholder = new Label("请选择模式后点击[入场统计]查看图表");
        usagePlaceholder.setWrapText(true);
        ScrollPane usageChartPane = new ScrollPane(usagePlaceholder);
        usageChartPane.setFitToHeight(true);
        usageChartPane.setFitToWidth(true);
        usageChartPane.setPrefHeight(380);
        usageChartPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        usageChartPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        usageChartPane.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0;");

        Button income = new Button("收入排行");
        income.setOnAction(e -> {
            try {
                List<Map<String, Object>> rows = reportService.incomeByLot();
                incomeChartPane.setContent(createIncomeChart(rows));
            } catch (Exception ex) {
                incomeChartPane.setContent(new Label(formatError(ex)));
            }
        });

        Button reserve = new Button("预约排行");
        reserve.setOnAction(e -> {
            try {
                List<Map<String, Object>> rows = reportService.reservationCountBySpace();
                reserveChartPane.setContent(createReservationChart(rows));
            } catch (Exception ex) {
                reserveChartPane.setContent(new Label(formatError(ex)));
            }
        });

        ComboBox<String> usageMode = new ComboBox<>(FXCollections.observableArrayList(
                "全部入场次数",
                "入场次数前三",
                "入场次数倒数前三"
        ));
        usageMode.setValue("全部入场次数");
        usageMode.setPrefWidth(220);

        Button usage = new Button("入场统计");
        usage.setPrefWidth(110);
        usage.setOnAction(e -> {
            try {
                String mode = usageMode.getValue() == null ? "全部入场次数" : usageMode.getValue().trim();
                List<Map<String, Object>> rows = new ArrayList<>(reportService.usageByHour());
                if ("入场次数前三".equals(mode)) {
                    rows.sort(Comparator
                            .comparingInt((Map<String, Object> r) -> safeToInt(r.get("usage_count"))).reversed()
                            .thenComparingInt(r -> safeToInt(r.get("hour_slot"))));
                    if (rows.size() > 3) rows = new ArrayList<>(rows.subList(0, 3));
                } else if ("入场次数倒数前三".equals(mode)) {
                    rows.sort(Comparator
                            .comparingInt((Map<String, Object> r) -> safeToInt(r.get("usage_count")))
                            .thenComparingInt(r -> safeToInt(r.get("hour_slot"))));
                    if (rows.size() > 3) rows = new ArrayList<>(rows.subList(0, 3));
                } else {
                    rows.sort(Comparator.comparingInt(r -> safeToInt(r.get("hour_slot"))));
                }
                usageChartPane.setContent(createUsageChart(rows, mode));
            } catch (Exception ex) {
                usageChartPane.setContent(new Label(formatError(ex)));
            }
        });

        Label hint = new Label("说明：入场统计可选择全部/前三/倒数前三。");
        hint.setStyle("-fx-text-fill: #475569;");

        HBox rankRow = new HBox(10, income, reserve);
        HBox usageRow = new HBox(10, new Label("入场统计模式"), usageMode, usage);
        HBox.setHgrow(usageMode, Priority.ALWAYS);
        usageMode.setMaxWidth(Double.MAX_VALUE);

        VBox body = new VBox(14,
                sectionBox("排行统计", rankRow, incomeChartPane, reserveChartPane),
                sectionBox("入场统计", usageRow, hint, usageChartPane));
        body.setPadding(new Insets(10));

        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab tab = new Tab("统计报表", pageScroll);
        tab.setClosable(false);
        return tab;
    }

    private BarChart<String, Number> createIncomeChart(List<Map<String, Object>> rows) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("停车场");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("总收入(元)");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("停车场收入排行");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setBarGap(4);
        chart.setCategoryGap(20);
        chart.setMinWidth(Math.max(600, rows.size() * 90));

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map<String, Object> row : rows) {
            String lotName = String.valueOf(row.get("lot_name"));
            double income = ((Number) row.get("total_income")).doubleValue();
            series.getData().add(new XYChart.Data<>(lotName, income));
        }
        chart.getData().add(series);
        return chart;
    }

    private StackedBarChart<String, Number> createReservationChart(List<Map<String, Object>> rows) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("停车场");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("预约次数");

        StackedBarChart<String, Number> chart = new StackedBarChart<>(xAxis, yAxis);
        chart.setTitle("停车场预约次数排行（地上/地下）");
        chart.setAnimated(false);
        chart.setCategoryGap(20);

        java.util.Set<String> allLots = new java.util.LinkedHashSet<>();
        java.util.Map<String, Long> groundMap = new java.util.HashMap<>();
        java.util.Map<String, Long> underMap = new java.util.HashMap<>();

        for (Map<String, Object> row : rows) {
            String lotName = String.valueOf(row.get("lot_name"));
            String type = String.valueOf(row.get("type"));
            long count = ((Number) row.get("reserve_count")).longValue();
            allLots.add(lotName);
            if ("UNDERGROUND".equalsIgnoreCase(type)) {
                underMap.merge(lotName, count, Long::sum);
            } else {
                groundMap.merge(lotName, count, Long::sum);
            }
        }

        XYChart.Series<String, Number> groundSeries = new XYChart.Series<>();
        groundSeries.setName("地上");
        XYChart.Series<String, Number> underSeries = new XYChart.Series<>();
        underSeries.setName("地下");

        for (String lot : allLots) {
            groundSeries.getData().add(new XYChart.Data<>(lot, groundMap.getOrDefault(lot, 0L)));
            underSeries.getData().add(new XYChart.Data<>(lot, underMap.getOrDefault(lot, 0L)));
        }

        chart.getData().addAll(groundSeries, underSeries);
        chart.setMinWidth(Math.max(600, allLots.size() * 100));

        // Apply colors: green for ground, orange for underground
        for (XYChart.Data<String, Number> d : groundSeries.getData()) {
            d.nodeProperty().addListener((obs, old, n) -> {
                if (n != null) n.setStyle("-fx-bar-fill: #4CAF50;");
            });
        }
        for (XYChart.Data<String, Number> d : underSeries.getData()) {
            d.nodeProperty().addListener((obs, old, n) -> {
                if (n != null) n.setStyle("-fx-bar-fill: #FF9800;");
            });
        }

        return chart;
    }

    private BarChart<String, Number> createUsageChart(List<Map<String, Object>> rows, String mode) {
        CategoryAxis xAxis = new CategoryAxis();
        xAxis.setLabel("时段");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("入场次数");

        BarChart<String, Number> chart = new BarChart<>(xAxis, yAxis);
        chart.setTitle("入场统计 (" + mode + ")");
        chart.setLegendVisible(false);
        chart.setAnimated(false);
        chart.setBarGap(4);
        chart.setCategoryGap(10);
        chart.setMinWidth(700);

        XYChart.Series<String, Number> series = new XYChart.Series<>();
        for (Map<String, Object> row : rows) {
            int hour = safeToInt(row.get("hour_slot"));
            String label = String.format("%02d:00-%02d:59", hour, hour);
            double count = ((Number) row.get("usage_count")).doubleValue();
            series.getData().add(new XYChart.Data<>(label, count));
        }
        chart.getData().add(series);
        return chart;
    }

    private Tab operationLogTab() {
        TextField keyword = new TextField();
        keyword.setPromptText("关键字（日志ID/用户ID/用户名/操作类型/操作描述）");
        keyword.setPrefWidth(420);
        ComboBox<String> categoryBox = new ComboBox<>(FXCollections.observableArrayList("全部", "新增", "删除", "修改", "登录"));
        categoryBox.setValue("全部");
        categoryBox.setPrefWidth(110);

        TableView<Map<String, Object>> table = new TableView<>();
        table.setEditable(false);

        TableColumn<Map<String, Object>, String> logIdCol = new TableColumn<>("日志ID");
        logIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("log_id"))));
        logIdCol.setSortable(false);
        logIdCol.setPrefWidth(60);

        TableColumn<Map<String, Object>, String> userIdCol = new TableColumn<>("用户ID");
        userIdCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("user_id"))));
        userIdCol.setSortable(false);
        userIdCol.setPrefWidth(60);

        TableColumn<Map<String, Object>, String> usernameCol = new TableColumn<>("用户名");
        usernameCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("username"))));
        usernameCol.setSortable(false);
        usernameCol.setPrefWidth(80);

        TableColumn<Map<String, Object>, String> opTypeCol = new TableColumn<>("操作类型");
        opTypeCol.setCellValueFactory(c -> {
            Object val = c.getValue().get("operation_type");
            String s = val != null ? val.toString() : "";
            switch (s) {
                case "LOGIN": return new SimpleStringProperty("登录");
                case "ADD": return new SimpleStringProperty("新增");
                case "DELETE": return new SimpleStringProperty("删除");
                case "UPDATE": return new SimpleStringProperty("修改");
                default: return new SimpleStringProperty(s);
            }
        });
        opTypeCol.setSortable(false);
        opTypeCol.setPrefWidth(70);

        TableColumn<Map<String, Object>, String> descCol = new TableColumn<>("操作描述");
        descCol.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().get("operation_desc"))));
        descCol.setSortable(false);
        descCol.setPrefWidth(300);

        TableColumn<Map<String, Object>, String> timeCol = new TableColumn<>("创建时间");
        timeCol.setCellValueFactory(c -> {
            Object val = c.getValue().get("create_time");
            return new SimpleStringProperty(val != null ? val.toString() : "");
        });
        timeCol.setSortable(false);
        timeCol.setPrefWidth(140);

        table.getColumns().addAll(logIdCol, userIdCol, usernameCol, opTypeCol, descCol, timeCol);

        final int[] pageSize = {20};
        final int[] pageNo = {1};
        table.setFixedCellSize(28);

        Runnable updateTableHeight = () -> {
            double h = table.getFixedCellSize() * pageSize[0] + 34;
            table.setPrefHeight(h);
            table.setMinHeight(h);
            table.setMaxHeight(h);
        };
        updateTableHeight.run();

        Label pageInfo = new Label("第1页");

        Runnable reload = () -> {
            try {
                List<Map<String, Object>> rows = operationLogService.queryLogs(keyword.getText(), logCategoryCode(categoryBox.getValue()), pageNo[0], pageSize[0]);
                if (rows.isEmpty() && pageNo[0] > 1) {
                    pageNo[0]--;
                    rows = operationLogService.queryLogs(keyword.getText(), logCategoryCode(categoryBox.getValue()), pageNo[0], pageSize[0]);
                }
                table.setItems(FXCollections.observableArrayList(rows));
                pageInfo.setText("第" + pageNo[0] + "页（每页" + pageSize[0] + "条，当前" + rows.size() + "条）");
            } catch (Exception ex) {
                // show error in a label or just log to console
            }
        };

        Button query = new Button("查询");
        query.setOnAction(e -> {
            pageNo[0] = 1;
            reload.run();
        });
        Button prev = new Button("上一页");
        prev.setOnAction(e -> {
            if (pageNo[0] <= 1) return;
            pageNo[0]--;
            reload.run();
        });
        Button next = new Button("下一页");
        next.setOnAction(e -> {
            if (table.getItems() == null || table.getItems().size() < pageSize[0]) return;
            pageNo[0]++;
            reload.run();
        });
        Button clearLogs = new Button("清空日志");
        clearLogs.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("清空日志");
            confirm.setHeaderText("确认清空日志吗？");
            confirm.setContentText("当前将按分类【" + categoryBox.getValue() + "】清空，操作不可恢复。");
            Optional<ButtonType> result = confirm.showAndWait();
            if (result.isEmpty() || result.get() != ButtonType.OK) {
                return;
            }
            try {
                int cnt = operationLogService.clearLogs(logCategoryCode(categoryBox.getValue()));
                pageNo[0] = 1;
                reload.run();
            } catch (Exception ex) {
                // silently ignore
            }
        });

        ComboBox<Integer> pageSizeBox = new ComboBox<>(FXCollections.observableArrayList(10, 20, 30, 50));
        pageSizeBox.setValue(20);
        pageSizeBox.setPrefWidth(70);
        pageSizeBox.setOnAction(e -> {
            pageSize[0] = pageSizeBox.getValue();
            updateTableHeight.run();
            pageNo[0] = 1;
            reload.run();
        });

        Label hint = new Label("说明：可按关键字和分类（全部/新增/删除/修改/登录）筛选日志，操作描述会标注【模块名称】。");
        HBox queryRow = new HBox(8, keyword, categoryBox, query, clearLogs);
        HBox.setHgrow(keyword, Priority.ALWAYS);
        HBox pageRow = new HBox(8, prev, next, pageInfo, new Label("每页"), pageSizeBox, new Label("条"));

        VBox body = new VBox(10,
                sectionBox("查询区", queryRow, pageRow, hint),
                sectionBox("日志结果", table));
        body.setPadding(new Insets(10));
        reload.run();

        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        Tab tab = new Tab("操作日志", pageScroll);
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                pageScroll.setVvalue(0);
            }
        });
        return tab;
    }

    private long requireLong(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 不能为空
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + "\u5fc5\u987b\u662f\u6570\u5b57"); // 必须是数字
        }
    }

    private int requireInt(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 不能为空
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + "\u5fc5\u987b\u662f\u6570\u5b57"); // 必须是数字
        }
    }

    private LocalDateTime requireDateTime(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 不能为空
        }
        try { return LocalDateTime.parse(v); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(v, DT_SPACE_FMT); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(v, DT_SLASH_FMT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.now().atTime(LocalTime.parse(v)); } catch (DateTimeParseException ignored) {}
        throw new IllegalArgumentException(name + "\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u4f7f\u7528 yyyy-MM-dd HH:mm \u6216 HH:mm"); // 格式错误，请使用 yyyy-MM-dd HH:mm 或 HH:mm
    }

    private LocalTime requireTime(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 不能为空
        }
        try {
            return LocalTime.parse(v);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException(name + "\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u4f7f\u7528 HH:mm"); // 格式错误，请使用 HH:mm
        }
    }

    private String formatError(Exception ex) {
        if (ex instanceof IllegalArgumentException) {
            String msg = ex.getMessage();
            if (msg == null || msg.isBlank() || "null".equalsIgnoreCase(msg.trim())) {
                return "\u9519\u8bef\uff1a\u8f93\u5165\u6709\u8bef\uff0c\u8bf7\u68c0\u67e5\u5fc5\u586b\u9879\u4e0e\u6570\u5b57\u683c\u5f0f"; // 错误：输入有误，请检查必填项与数字格式
            }
            return "\u9519\u8bef\uff1a" + toChineseMessage(msg); // 错误：
        }
        if (ex instanceof NumberFormatException) {
            return "\u9519\u8bef\uff1a\u8f93\u5165\u683c\u5f0f\u4e0d\u6b63\u786e\uff0c\u8bf7\u586b\u5199\u6709\u6548\u6570\u5b57"; // 错误：输入格式不正确，请填写有效数字
        }
        if (ex instanceof java.time.format.DateTimeParseException) {
            return "\u9519\u8bef\uff1a\u65f6\u95f4\u683c\u5f0f\u4e0d\u6b63\u786e\uff0c\u8bf7\u4f7f\u7528 yyyy-MM-ddTHH:mm \u6216 HH:mm"; // 错误：时间格式不正确，请使用 yyyy-MM-ddTHH:mm 或 HH:mm
        }
                if (ex instanceof java.sql.SQLException || ex.getCause() instanceof java.sql.SQLException) {
            String sqlMsg = ex.getMessage() != null ? ex.getMessage() : "";
            return "数据库操作失败，请联系管理员。详情：" + (sqlMsg.isEmpty() ? "未知数据库错误" : sqlMsg);
        }
        String msg = ex.getMessage();
        String display = (msg == null || msg.isBlank() || "null".equalsIgnoreCase(msg.trim()))
                ? "\u8f93\u5165\u6709\u8bef\uff0c\u8bf7\u68c0\u67e5\u5fc5\u586b\u9879\u4e0e\u6570\u5b57\u683c\u5f0f" // 输入有误，请检查必填项与数字格式
                : toChineseMessage(msg);
        return "\u9519\u8bef\uff1a" + display; // 错误：
    }

    private String toChineseMessage(String msg) {

        if (msg == null || msg.isBlank()) return "\u53d1\u751f\u672a\u77e5\u9519\u8bef"; // 发生未知错误
        String m = msg.trim();
        String lower = m.toLowerCase();

        if (lower.contains("cannot delete or update a parent row") || lower.contains("foreign key constraint fails")) {
            return "\u5b58\u5728\u5173\u8054\u4e1a\u52a1\u8bb0\u5f55\uff0c\u4e0d\u80fd\u5220\u9664\u6216\u4fee\u6539\uff0c\u8bf7\u5148\u5904\u7406\u5173\u8054\u6570\u636e"; // 存在关联业务记录，不能删除或修改，请先处理关联数据
        }



        if ("Invalid record or already completed".equalsIgnoreCase(m)) return "\u505c\u8f66\u8bb0\u5f55\u65e0\u6548\u6216\u5df2\u7ecf\u5b8c\u6210\u51fa\u573a"; // 停车记录无效或已经完成出场
        if ("Parking space not found".equalsIgnoreCase(m)) return "\u672a\u627e\u5230\u5bf9\u5e94\u8f66\u4f4d"; // 未找到对应车位
        if ("Failed to complete parking exit".equalsIgnoreCase(m)) return "\u505c\u8f66\u51fa\u573a\u5904\u7406\u5931\u8d25"; // 停车出场处理失败
        if ("Invalid reservation time range".equalsIgnoreCase(m)) return "\u9884\u7ea6\u65f6\u95f4\u533a\u95f4\u65e0\u6548"; // 预约时间区间无效
        if ("Invalid share time range".equalsIgnoreCase(m)) return "\u5171\u4eab\u65f6\u95f4\u533a\u95f4\u65e0\u6548"; // 共享时间区间无效
        if ("Reservation failed: time conflict detected".equalsIgnoreCase(m)) return "\u9884\u7ea6\u5931\u8d25\uff1a\u65f6\u95f4\u6bb5\u51b2\u7a81"; // 预约失败：时间段冲突
        if ("Cancel failed: reservation not found or cannot be canceled".equalsIgnoreCase(m)) return "\u53d6\u6d88\u5931\u8d25\uff1a\u672a\u627e\u5230\u53ef\u53d6\u6d88\u7684\u9884\u7ea6"; // 取消失败：未找到可取消的预约


        if ("userId is required".equalsIgnoreCase(m)) return "\u7528\u6237ID\u4e0d\u80fd\u4e3a\u7a7a"; // 用户ID不能为空
        if ("ownerId is required".equalsIgnoreCase(m)) return "\u6240\u6709\u8005ID\u4e0d\u80fd\u4e3a\u7a7a"; // 所有者ID不能为空
        if ("spaceId and ownerId are required".equalsIgnoreCase(m)) return "\u8f66\u4f4dID\u548c\u6240\u6709\u8005ID\u4e0d\u80fd\u4e3a\u7a7a"; // 车位ID和所有者ID不能为空
        if ("userId and spaceId are required".equalsIgnoreCase(m)) return "\u7528\u6237ID\u548c\u8f66\u4f4dID\u4e0d\u80fd\u4e3a\u7a7a"; // 用户ID和车位ID不能为空
        if ("Space id is required".equalsIgnoreCase(m) || "spaceId is required".equalsIgnoreCase(m)) return "\u8f66\u4f4dID\u4e0d\u80fd\u4e3a\u7a7a"; // 车位ID不能为空
        if ("Lot id is required".equalsIgnoreCase(m) || "lotId is required".equalsIgnoreCase(m)) return "\u505c\u8f66\u573aID\u4e0d\u80fd\u4e3a\u7a7a"; // 停车场ID不能为空
        if ("Space not found or no permission to update".equalsIgnoreCase(m)) return "\u672a\u627e\u5230\u8f66\u4f4d\u6216\u65e0\u6743\u4fee\u6539"; // 未找到车位或无权修改
        if ("Parking space not found".equalsIgnoreCase(m) || "Parking space is required".equalsIgnoreCase(m)) return "\u8f66\u4f4d\u4fe1\u606f\u9519\u8bef\u6216\u4e0d\u5b58\u5728"; // 车位信息错误或不存在


        if ("Username and password are required".equalsIgnoreCase(m)) return "\u7528\u6237\u540d\u548c\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"; // 用户名和密码不能为空
        if ("User does not exist or is disabled".equalsIgnoreCase(m)) return "\u7528\u6237\u4e0d\u5b58\u5728\u6216\u5df2\u88ab\u7981\u7528"; // 用户不存在或已被禁用
        if ("Wrong password".equalsIgnoreCase(m)) return "\u5bc6\u7801\u9519\u8bef"; // 密码错误
        if ("Username is required".equalsIgnoreCase(m)) return "\u7528\u6237\u540d\u4e0d\u80fd\u4e3a\u7a7a"; // 用户名不能为空
        if ("Password is required".equalsIgnoreCase(m)) return "\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"; // 密码不能为空
        if ("Role is required".equalsIgnoreCase(m)) return "\u89d2\u8272\u4e0d\u80fd\u4e3a\u7a7a"; // 角色不能为空
        if ("Username already exists".equalsIgnoreCase(m)) return "\u7528\u6237\u540d\u5df2\u5b58\u5728"; // 用户名已存在
        if ("User not found".equalsIgnoreCase(m) || "User not found".equalsIgnoreCase(m)) return "\u7528\u6237\u4e0d\u5b58\u5728"; // 用户不存在
        if ("userId and newPassword are required".equalsIgnoreCase(m)) return "\u7528\u6237ID\u548c\u65b0\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"; // 用户ID和新密码不能为空
        if ("userId and status are required".equalsIgnoreCase(m)) return "\u7528\u6237ID\u548c\u72b6\u6001\u4e0d\u80fd\u4e3a\u7a7a"; // 用户ID和状态不能为空
        if ("status must be 0 or 1".equalsIgnoreCase(m)) return "\u72b6\u6001\u53ea\u80fd\u4e3a0\u62161"; // 状态只能为0或1


        if ("Rule not found".equalsIgnoreCase(m)) return "\u8ba1\u8d39\u89c4\u5219\u4e0d\u5b58\u5728"; // 计费规则不存在
        if ("ruleId is required".equalsIgnoreCase(m)) return "\u89c4\u5219ID\u4e0d\u80fd\u4e3a\u7a7a"; // 规则ID不能为空
        if ("ruleName is required".equalsIgnoreCase(m)) return "\u89c4\u5219\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a"; // 规则名称不能为空
        if ("chargeType is required".equalsIgnoreCase(m)) return "\u8ba1\u8d39\u65b9\u5f0f\u4e0d\u80fd\u4e3a\u7a7a"; // 计费方式不能为空
        if ("Hourly rule requires unitPrice and unitTime".equalsIgnoreCase(m)) return "\u6309\u65f6\u8ba1\u8d39\u9700\u586b\u5199\u5355\u4ef7\u4e0e\u8ba1\u8d39\u65f6\u957f"; // 按时计费需填写单价与计费时长
        if ("Fixed rule requires fixedPrice".equalsIgnoreCase(m)) return "\u6309\u6b21\u8ba1\u8d39\u9700\u586b\u5199\u56fa\u5b9a\u4ef7"; // 按次计费需填写固定价
        if ("chargeType must be HOURLY or FIXED".equalsIgnoreCase(m)) return "\u8ba1\u8d39\u65b9\u5f0f\u53ea\u80fd\u4e3a HOURLY \u6216 FIXED"; // 计费方式只能为 HOURLY 或 FIXED
        if ("applicableSpaceType is required".equalsIgnoreCase(m)) return "\u9002\u7528\u8f66\u4f4d\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a"; // 适用车位类型不能为空
        if ("revenueId is required".equalsIgnoreCase(m)) return "\u6536\u76ca\u8bb0\u5f55ID\u4e0d\u80fd\u4e3a\u7a7a"; // 收益记录ID不能为空
        if ("Revenue not found or already settled".equalsIgnoreCase(m)) return "\u6536\u76ca\u8bb0\u5f55\u4e0d\u5b58\u5728\u6216\u5df2\u7ed3\u7b97"; // 收益记录不存在或已结算


        if ("Insert parking lot failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u505c\u8f66\u573a\u5931\u8d25"; // 新增停车场失败
        if ("Insert parking space failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u8f66\u4f4d\u5931\u8d25"; // 新增车位失败
        if ("Insert parking entry failed".equalsIgnoreCase(m)) return "\u751f\u6210\u505c\u8f66\u5165\u573a\u8bb0\u5f55\u5931\u8d25"; // 生成停车入场记录失败
        if ("Insert payment record failed".equalsIgnoreCase(m)) return "\u751f\u6210\u652f\u4ed8\u8bb0\u5f55\u5931\u8d25"; // 生成支付记录失败
        if ("Insert pricing rule failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u8ba1\u8d39\u89c4\u5219\u5931\u8d25"; // 新增计费规则失败
        if ("Insert revenue record failed".equalsIgnoreCase(m)) return "\u751f\u6210\u6536\u76ca\u8bb0\u5f55\u5931\u8d25"; // 生成收益记录失败
        if ("Insert user failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u7528\u6237\u5931\u8d25"; // 新增用户失败
        if ("operationType is required".equalsIgnoreCase(m)) return "\u64cd\u4f5c\u65e5\u5fd7\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a"; // 操作日志类型不能为空
        if ("User info is required".equalsIgnoreCase(m)) return "用户信息不能为空";
        if ("Phone must be 11 digits".equalsIgnoreCase(m)) return "手机号必须为11位";
        if ("fieldName is required".equalsIgnoreCase(m)) return "字段名不能为空";
        if ("Protected admin user cannot be deleted".equalsIgnoreCase(m)) return "受保护的管理员账号不可删除";
        if ("User still owns parking spaces".equalsIgnoreCase(m)) return "该用户仍拥有车位，请先处理车位后再删除";
        if ("User has active parking records".equalsIgnoreCase(m)) return "该用户存在活跃的停车记录，请先处理";
        if ("User has related reservation/parking/payment/revenue data".equalsIgnoreCase(m)) return "该用户存在关联业务数据（预约/停车/支付/收益），无法删除";
        if ("Delete user failed".equalsIgnoreCase(m)) return "删除用户失败，可能存在关联数据";
        if ("create time is required".equalsIgnoreCase(m)) return "创建时间不能为空";
        if ("create time format must be yyyy-MM-dd HH:mm".equalsIgnoreCase(m)) return "创建时间格式必须为 yyyy-MM-dd HH:mm";
        if ("Real name is required".equalsIgnoreCase(m)) return "真实姓名不能为空";
        if ("Phone is required".equalsIgnoreCase(m)) return "手机号不能为空";
        if ("Old password is required".equalsIgnoreCase(m)) return "旧密码不能为空";
        if ("User id is required".equalsIgnoreCase(m)) return "用户ID不能为空";
        if (lower.contains("unsupported user field")) return "不支持的字段名：" + m;
        if (lower.startsWith("username already exists:")) return "用户名已存在：" + m.substring("username already exists:".length()).trim();
        if (lower.contains("wrong password, locked for")) return "密码错误，账号已锁定，请稍后再试。详情：" + m;
        if (lower.startsWith("wrong password,") && lower.contains("attempt")) return "密码错误，" + m.substring("wrong password,".length()).trim();
        if (lower.startsWith("cooldown:")) return "登录冷却中，剩余" + m.substring("cooldown:".length()).trim() + "秒";
        if (lower.contains("校验车位关联信息失败")) return m;
        if (lower.contains("character array contains no digits")) return "\u8f93\u5165\u4e0d\u80fd\u4e3a\u7a7a\uff0c\u8bf7\u586b\u5199\u6570\u5b57"; // 输入不能为空，请填写数字
        if (lower.contains("for input string")) return "\u8f93\u5165\u7684\u6570\u5b57\u683c\u5f0f\u4e0d\u6b63\u786e"; // 输入的数字格式不正确

        return m;
    }

    private int safeToInt(Object value) {
        if (value == null) return 0;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    private List<ParkingSpace> queryAvailableByLotAndType(LocalDateTime start, LocalDateTime end, Long lotId, String typeCode) throws SQLException {
        final int pageSize = 200;
        int pageNo = 1;
        List<ParkingSpace> all = new ArrayList<>();
        while (true) {
            List<ParkingSpace> page = parkingSpaceService.queryAvailableSpaces(start, end, pageNo, pageSize);
            if (page == null || page.isEmpty()) {
                break;
            }
            for (ParkingSpace s : page) {
                if (lotId.equals(s.getLotId()) && typeCode.equalsIgnoreCase(s.getType())) {
                    all.add(s);
                }
            }
            if (page.size() < pageSize) {
                break;
            }
            pageNo++;
            if (pageNo > 200) break;
        }
        all.sort(Comparator.comparingLong(s -> s.getSpaceId() == null ? Long.MAX_VALUE : s.getSpaceId()));
        return all;
    }

    private List<ParkingSpace> loadAllSpacesByLot(Long lotId) throws SQLException {
        final int pageSize = 200;
        int pageNo = 1;
        List<ParkingSpace> all = new ArrayList<>();
        while (true) {
            List<ParkingSpace> page = parkingSpaceService.querySpaces("", "", lotId, pageNo, pageSize);
            if (page == null || page.isEmpty()) {
                break;
            }
            all.addAll(page);
            if (page.size() < pageSize) {
                break;
            }
            pageNo++;
            if (pageNo > 200) break;
        }
        return all;
    }

    private String localizeRowText(String text) {
        if (text == null) return "";
        return text
                .replace("UNSETTLED", "\u672a\u7ed3\u7b97") // 未结算
                .replace("SETTLED", "\u5df2\u7ed3\u7b97") // 已结算
                .replace("UNPAID", "\u672a\u652f\u4ed8") // 未支付
                .replace("PAID", "\u5df2\u652f\u4ed8") // 已支付
                .replace("PENDING", "\u5f85\u4f7f\u7528") // 待使用
                .replace("ACTIVE", "\u4f7f\u7528\u4e2d") // 使用中
                .replace("CANCELED", "\u5df2\u53d6\u6d88") // 已取消
                .replace("COMPLETED", "\u5df2\u5b8c\u6210") // 已完成
                .replace("LOGIN", "\u767b\u5f55") // 登录
                .replace("ADD", "\u65b0\u589e") // 新增
                .replace("DELETE", "\u5220\u9664") // 删除
                .replace("UPDATE", "\u4fee\u6539") // 修改
                .replace("UNDERGROUND", "\u5730\u4e0b") // 地下
                .replace("GROUND", "\u5730\u4e0a") // 地上
                .replace("FREE", "\u7a7a\u95f2") // 空闲
                .replace("RESERVED", "\u5df2\u9884\u7ea6") // 已预约
                .replace("OCCUPIED", "\u5360\u7528"); // 占用
    }

    private String formatRowMap(Map<String, Object> row) {
        if (row == null || row.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (!first) sb.append("\uff0c "); // ， 
            first = false;
            String k = localizeKey(e.getKey());
            String v = formatRowValue(e.getKey(), e.getValue());
            sb.append(k).append("=").append(v);
        }
        return sb.toString();
    }

    private String formatRowValue(String key, Object value) {
        if (value == null) return "null";
        if ("hour_slot".equalsIgnoreCase(key) || "hour".equalsIgnoreCase(key)) {
            return formatHourSlot(value);
        }
        return localizeRowText(String.valueOf(value));
    }

    private String formatHourSlot(Object value) {
        Integer hour = null;
        if (value instanceof Number) {
            hour = ((Number) value).intValue();
        } else {
            String s = String.valueOf(value).trim();
            try {
                hour = Integer.parseInt(s);
            } catch (NumberFormatException ignore) {
                return localizeRowText(String.valueOf(value));
            }
        }
        if (hour == null || hour < 0 || hour > 23) {
            return localizeRowText(String.valueOf(value));
        }
        return String.format("%02d:00-%02d:59", hour, hour);
    }

    private String localizeKey(String key) {
        if (key == null) return "";
        switch (key) {
            case "log_id": return "\u65e5\u5fd7ID"; // 日志ID
            case "user_id": return "\u7528\u6237ID"; // 用户ID
            case "username": return "\u7528\u6237\u540d"; // 用户名
            case "operation_type": return "\u64cd\u4f5c\u7c7b\u578b"; // 操作类型
            case "operation_desc": return "\u64cd\u4f5c\u63cf\u8ff0"; // 操作描述
            case "create_time": return "\u521b\u5efa\u65f6\u95f4"; // 创建时间
            case "owner_id": return "\u6536\u76ca\u4ebaID"; // 收益人ID
            case "payer_user_id": return "\u652f\u4ed8\u4ebaID"; // 支付人ID
            case "payment_id": return "\u652f\u4ed8\u8bb0\u5f55ID"; // 支付记录ID
            case "revenue_id": return "\u6536\u76ca\u8bb0\u5f55ID"; // 收益记录ID
            case "settle_status": return "\u7ed3\u7b97\u72b6\u6001"; // 结算状态
            case "space_id": return "\u8f66\u4f4dID"; // 车位ID
            case "settle_time": return "\u7ed3\u7b97\u65f6\u95f4"; // 结算时间
            case "income_amount": return "\u6536\u76ca\u91d1\u989d"; // 收益金额
            case "payment_status": return "\u652f\u4ed8\u72b6\u6001"; // 支付状态
            case "payment_method": return "\u652f\u4ed8\u65b9\u5f0f"; // 支付方式
            case "payment_time": return "\u652f\u4ed8\u65f6\u95f4"; // 支付时间
            case "amount": return "\u91d1\u989d"; // 金额
            case "record_id": return "\u505c\u8f66\u8bb0\u5f55ID"; // 停车记录ID
            case "lot_id": return "\u505c\u8f66\u573aID"; // 停车场ID
            case "lot_name": return "\u505c\u8f66\u573a\u540d\u79f0"; // 停车场名称
            case "type": return "\u7c7b\u578b"; // 类型
            case "status": return "\u72b6\u6001"; // 状态
            case "space_number": return "\u8f66\u4f4d\u7f16\u53f7"; // 车位编号
            case "hour": return "\u5c0f\u65f6"; // 小时
            case "hour_slot": return "\u65f6\u6bb5"; // 时段
            case "usage_count": return "\u4f7f\u7528\u6b21\u6570"; // 使用次数
            case "reservation_count": return "\u9884\u7ea6\u6b21\u6570"; // 预约次数
            case "reserve_count": return "\u9884\u7ea6\u6b21\u6570"; // 预约次数
            case "total_income": return "\u603b\u6536\u5165"; // 总收入
            default: return key;
        }
    }

    private String fmtDateTime(LocalDateTime dt) {
        if (dt == null) return "null";
        return dt.format(DT_SPACE_FMT);
    }

    private String formatAmount(BigDecimal amount) {
        BigDecimal v = amount == null ? BigDecimal.ZERO : amount;
        return v.stripTrailingZeros().toPlainString();
    }

    private ParkingLot lotFrom(TextField id, TextField name, TextField address, TextField total,
                               TextField open, TextField close, TextField desc, boolean withId) {
        ParkingLot lot = new ParkingLot();
        if (withId) lot.setLotId(requireLong(id, "ID"));
        lot.setLotName(name.getText().trim());
        lot.setAddress(address.getText().trim());
        lot.setTotalSpaces(requireInt(total, "\u603b\u8f66\u4f4d\u6570")); // 总车位数
        lot.setOpenTime(requireTime(open, "\u5f00\u59cb\u65f6\u95f4")); // 开始时间
        lot.setCloseTime(requireTime(close, "\u7ed3\u675f\u65f6\u95f4")); // 结束时间
        lot.setDescription(desc.getText() == null ? "" : desc.getText().trim());
        return lot;
    }

    private ParkingLot lotFromForPartialUpdate(TextField id, TextField name, TextField address, TextField total,
                                               TextField open, TextField close, TextField desc) {
        ParkingLot lot = new ParkingLot();
        lot.setLotId(requireLong(id, "ID"));

        String nameText = name.getText() == null ? "" : name.getText().trim();
        if (!nameText.isEmpty()) lot.setLotName(nameText);

        String addressText = address.getText() == null ? "" : address.getText().trim();
        if (!addressText.isEmpty()) lot.setAddress(addressText);

        Integer totalSpaces = optionalInt(total, "\u603b\u8f66\u4f4d\u6570"); // 总车位数
        if (totalSpaces != null) lot.setTotalSpaces(totalSpaces);

        LocalTime openTime = optionalTime(open, "\u5f00\u59cb\u65f6\u95f4"); // 开始时间
        if (openTime != null) lot.setOpenTime(openTime);

        LocalTime closeTime = optionalTime(close, "\u7ed3\u675f\u65f6\u95f4"); // 结束时间
        if (closeTime != null) lot.setCloseTime(closeTime);

        String descText = desc.getText() == null ? "" : desc.getText().trim();
        if (!descText.isEmpty()) lot.setDescription(descText);
        return lot;
    }

    private Integer optionalInt(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) return null;
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + "\u5fc5\u987b\u662f\u6570\u5b57"); // 必须是数字
        }
    }

    private BigDecimal requireBigDecimal(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 不能为空
        }
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + "\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u586b\u5199\u6570\u5b57"); // 格式错误，请填写数字
        }
    }

    private BigDecimal optionalBigDecimal(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) return null;
        try {
            return new BigDecimal(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + "\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u586b\u5199\u6570\u5b57"); // 格式错误，请填写数字
        }
    }

    private LocalTime optionalTime(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) return null;
        try {
            return LocalTime.parse(v);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(name + "\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u4f7f\u7528 HH:mm"); // 格式错误，请使用 HH:mm
        }
    }

    private Long optionalLong(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + "\u5fc5\u987b\u662f\u6570\u5b57"); // 必须是数字
        }
    }

    private ParkingSpace spaceFrom(TextField id, TextField lotId, TextField ownerId, TextField number,
                                   ComboBox<String> type, ComboBox<String> status, TextField start, TextField end, boolean withId) {
        ParkingSpace s = new ParkingSpace();
        if (withId) s.setSpaceId(requireLong(id, "ID"));
        s.setLotId(requireLong(lotId, "\u505c\u8f66\u573aID")); // 停车场ID
        s.setOwnerId(requireLong(ownerId, "\u6240\u6709\u8005ID")); // 所有者ID
        s.setSpaceNumber(number.getText().trim());
        s.setType(spaceTypeCode(type.getValue()));
        s.setStatus(spaceStatusCode(status.getValue()));
        s.setShareStartTime(requireTime(start, "\u5f00\u59cb\u65f6\u95f4")); // 开始时间
        s.setShareEndTime(requireTime(end, "\u7ed3\u675f\u65f6\u95f4")); // 结束时间
        return s;
    }

    private ParkingSpace spaceFromForPartialUpdate(TextField id, TextField lotId, TextField ownerId, TextField number,
                                                   ComboBox<String> type, ComboBox<String> status, TextField start, TextField end) {
        ParkingSpace s = new ParkingSpace();
        s.setSpaceId(requireLong(id, "\u8f66\u4f4dID")); // 车位ID

        Long lot = optionalLong(lotId, "\u505c\u8f66\u573aID"); // 停车场ID
        if (lot != null) s.setLotId(lot);

        Long owner = optionalLong(ownerId, "\u6240\u6709\u8005ID"); // 所有者ID
        if (owner != null) s.setOwnerId(owner);

        String num = number.getText() == null ? "" : number.getText().trim();
        if (!num.isEmpty()) s.setSpaceNumber(num);

        String typeLabel = type.getValue();
        if (typeLabel != null && !"\u4e0d\u4fee\u6539".equals(typeLabel)) { // 不修改
            s.setType(spaceTypeCode(typeLabel));
        }

        String statusLabel = status.getValue();
        if (statusLabel != null && !"\u4e0d\u4fee\u6539".equals(statusLabel)) { // 不修改
            s.setStatus(spaceStatusCode(statusLabel));
        }

        LocalTime startTime = optionalTime(start, "\u5171\u4eab\u5f00\u59cb\u65f6\u95f4"); // 共享开始时间
        if (startTime != null) s.setShareStartTime(startTime);

        LocalTime endTime = optionalTime(end, "\u5171\u4eab\u7ed3\u675f\u65f6\u95f4"); // 共享结束时间
        if (endTime != null) s.setShareEndTime(endTime);
        return s;
    }

    private PricingRule ruleFromForCreate(TextField name, ComboBox<String> chargeType, TextField unitPrice,
                                          TextField unitTime, TextField fixedPrice, ComboBox<String> spaceType, ComboBox<String> status) {
        PricingRule r = new PricingRule();
        String nameValue = name.getText() == null ? "" : name.getText().trim();
        if (nameValue.isEmpty()) {
            throw new IllegalArgumentException("\u89c4\u5219\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a"); // 规则名称不能为空
        }
        r.setRuleName(nameValue);

        String chargeTypeValue = chargeTypeCode(chargeType.getValue());
        r.setChargeType(chargeTypeValue);
        if ("HOURLY".equalsIgnoreCase(chargeTypeValue)) {
            r.setUnitPrice(requireBigDecimal(unitPrice, "\u5355\u4ef7")); // 单价
            r.setUnitTime(requireInt(unitTime, "\u5355\u4f4d\u65f6\u957f\uff08\u5206\u949f\uff09")); // 单位时长（分钟）
            r.setFixedPrice(null);
        } else {
            r.setFixedPrice(requireBigDecimal(fixedPrice, "\u56fa\u5b9a\u4ef7\u683c")); // 固定价格
            r.setUnitPrice(null);
            r.setUnitTime(null);
        }

        r.setApplicableSpaceType(spaceTypeCode(spaceType.getValue()));
        r.setStatus(enabledStatusValue(status.getValue()));
        return r;
    }

    private PricingRule ruleFromForPartialUpdate(TextField id, TextField name, ComboBox<String> chargeType, TextField unitPrice,
                                                 TextField unitTime, TextField fixedPrice, ComboBox<String> spaceType, ComboBox<String> status) {
        PricingRule r = new PricingRule();
        r.setRuleId(requireLong(id, "\u89c4\u5219ID")); // 规则ID

        String nameValue = name.getText() == null ? "" : name.getText().trim();
        if (!nameValue.isEmpty()) r.setRuleName(nameValue);

        String chargeTypeLabel = chargeType.getValue();
        if (chargeTypeLabel != null && !"\u4e0d\u4fee\u6539".equals(chargeTypeLabel)) { // 不修改
            r.setChargeType(chargeTypeCode(chargeTypeLabel));
        }

        BigDecimal unitPriceValue = optionalBigDecimal(unitPrice, "\u5355\u4ef7"); // 单价
        if (unitPriceValue != null) r.setUnitPrice(unitPriceValue);

        Integer unitTimeValue = optionalInt(unitTime, "\u5355\u4f4d\u65f6\u957f\uff08\u5206\u949f\uff09"); // 单位时长（分钟）
        if (unitTimeValue != null) r.setUnitTime(unitTimeValue);

        BigDecimal fixedPriceValue = optionalBigDecimal(fixedPrice, "\u56fa\u5b9a\u4ef7\u683c"); // 固定价格
        if (fixedPriceValue != null) r.setFixedPrice(fixedPriceValue);

        String spaceTypeLabel = spaceType.getValue();
        if (spaceTypeLabel != null && !"\u4e0d\u4fee\u6539".equals(spaceTypeLabel)) { // 不修改
            r.setApplicableSpaceType(spaceTypeCode(spaceTypeLabel));
        }

        String statusLabel = status.getValue();
        if (statusLabel != null && !"\u4e0d\u4fee\u6539".equals(statusLabel)) { // 不修改
            r.setStatus(enabledStatusValue(statusLabel));
        }
        return r;
    }
    private String roleLabel(String roleCode) {
        if ("ADMIN".equalsIgnoreCase(roleCode)) return "\u7ba1\u7406\u5458"; // 管理员
        if ("OWNER".equalsIgnoreCase(roleCode)) return "\u8f66\u4f4d\u6240\u6709\u8005"; // 车位所有者
        if ("CAR_OWNER".equalsIgnoreCase(roleCode)) return "\u8f66\u4e3b"; // 车主
        return roleCode == null ? "" : roleCode;
    }

    private String roleCodeFromLabel(String label) {
        if ("\u7ba1\u7406\u5458".equals(label)) return "ADMIN"; // 管理员
        if ("\u8f66\u4f4d\u6240\u6709\u8005".equals(label)) return "OWNER"; // 车位所有者
        if ("\u8f66\u4e3b".equals(label)) return "CAR_OWNER"; // 车主
        return "";
    }

    private Integer statusFilterValue(String label) {
        if ("\u542f\u7528".equals(label)) return 1; // 启用
        if ("\u7981\u7528".equals(label)) return 0; // 禁用
        return null;
    }

    private String spaceTypeCode(String label) {
        return "\u5730\u4e0b".equals(label) ? "UNDERGROUND" : "GROUND"; // 地下
    }

    private String spaceStatusCode(String label) {
        if ("\u5df2\u9884\u7ea6".equals(label)) return "RESERVED"; // 已预约
        if ("\u5360\u7528".equals(label)) return "OCCUPIED"; // 占用
        return "FREE";
    }

    private String chargeTypeCode(String label) {
        return "\u6309\u6b21\u8ba1\u8d39".equals(label) ? "FIXED" : "HOURLY"; // 按次计费
    }

    private int enabledStatusValue(String label) {
        return "\u7981\u7528".equals(label) ? 0 : 1; // 禁用
    }

    private String settleStatusCode(String label) {
        if ("\u5df2\u7ed3\u7b97".equals(label)) return "SETTLED"; // 已结算
        if ("\u672a\u7ed3\u7b97".equals(label)) return "UNSETTLED"; // 未结算
        return "";
    }

    private String reserveFilterCode(String label) {
        if ("\u5df2\u9884\u7ea6".equals(label)) return "RESERVED"; // 已预约
        if ("\u672a\u9884\u7ea6".equals(label)) return "NOT_RESERVED"; // 未预约
        return "";
    }

    private String logCategoryCode(String label) {
        if ("\u65b0\u589e".equals(label)) return "ADD"; // 新增
        if ("\u5220\u9664".equals(label)) return "DELETE"; // 删除
        if ("\u4fee\u6539".equals(label)) return "UPDATE"; // 修改
        if ("\u767b\u5f55".equals(label)) return "LOGIN"; // 登录
        return "ALL";
    }

    private String spaceTypeLabel(String code) {
        if ("UNDERGROUND".equalsIgnoreCase(code)) return "\u5730\u4e0b"; // 地下
        if ("GROUND".equalsIgnoreCase(code)) return "\u5730\u4e0a"; // 地上
        return code == null ? "" : code;
    }

    private String spaceStatusLabel(String code) {
        if ("RESERVED".equalsIgnoreCase(code)) return "\u5df2\u9884\u7ea6"; // 已预约
        if ("OCCUPIED".equalsIgnoreCase(code)) return "\u5360\u7528"; // 占用
        if ("FREE".equalsIgnoreCase(code)) return "\u7a7a\u95f2"; // 空闲
        return code == null ? "" : code;
    }

    private String chargeTypeLabel(String code) {
        if ("FIXED".equalsIgnoreCase(code)) return "\u6309\u6b21\u8ba1\u8d39"; // 按次计费
        if ("HOURLY".equalsIgnoreCase(code)) return "\u6309\u65f6\u8ba1\u8d39"; // 按时计费
        return code == null ? "" : code;
    }

    private String enabledLabel(Integer status) {
        return status != null && status == 1 ? "\u542f\u7528" : "\u7981\u7528"; // 启用 | 禁用
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private void run(CheckedAction action, TextArea out, Runnable then) {
        run(action, out, then, null, null, null);
    }

    private void run(CheckedAction action, TextArea out, Runnable then, String logType, String moduleName, Supplier<String> detailSupplier) {
        try {
            action.run();
            if (logType != null && !logType.isBlank()) {
                String detail = detailSupplier == null ? "执行操作" : detailSupplier.get();
                addOperationLog(logType, formatModuleLog(moduleName, detail));
            }
            if (then != null) then.run();
            out.appendText("\u64cd\u4f5c\u6210\u529f\n"); // 操作成功\\n
        } catch (Exception ex) {
            out.appendText(formatError(ex) + "\n");
        }
    }

    private String formatModuleLog(String moduleName, String detail) {
        String module = (moduleName == null || moduleName.isBlank()) ? "未分类模块" : moduleName.trim();
        String d = detail == null ? "" : detail.trim();
        return "【" + module + "】" + d;
    }

    private void addOperationLog(String operationType, String operationDesc) {
        try {
            Long userId = currentUser == null ? null : currentUser.getUserId();
            operationLogService.addLog(userId, operationType, operationDesc);
        } catch (Exception ignored) {
            // 日志写入失败不影响主业务
        }
    }

    private VBox sectionBox(String title, Node... content) {
        Label header = new Label(title);
        header.getStyleClass().add("section-title");
        VBox box = new VBox(8);
        box.getChildren().add(header);
        box.getChildren().addAll(content);
        box.setPadding(new Insets(10));
        box.getStyleClass().add("section-card");
        return box;
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("提示");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
    private void pushRollback(String label, String moduleName, Runnable restore) {
        rollbackStack.push(new RollbackEntry(label, moduleName, restore));
        while (rollbackStack.size() > MAX_ROLLBACK) {
            rollbackStack.removeLast();
        }
    }
    private Button createRollbackButton(TextArea out, Runnable reload) {
        Button btn = new Button("回滚");
        btn.setTooltip(new Tooltip("撤销最近删除（最多" + MAX_ROLLBACK + "条）"));
        btn.setOnAction(e -> {
            if (rollbackStack.isEmpty()) { showAlert("没有可回滚的删除操作"); return; }
            RollbackEntry entry = rollbackStack.pop();
            try {
                entry.restore.run();
                out.appendText("回滚成功：" + entry.label + " 已恢复\n");
                addOperationLog(LOG_ADD, formatModuleLog(entry.moduleName, "回滚恢复 " + entry.label));
                reload.run();
            } catch (Exception ex) {
                out.appendText("回滚失败：" + formatError(ex) + "\n");
                rollbackStack.push(entry);
            }
        });
        return btn;
    }
    private <T> TableColumn<T, Void> createActionColumn(String header, Consumer<T> onDelete) {
        TableColumn<T, Void> col = new TableColumn<>(header);
        col.setCellFactory(c -> new TableCell<>() {
            private final Button delBtn = new Button("删除");
            {
                delBtn.setStyle("-fx-background-color: #dc3545; -fx-text-fill: white; -fx-font-size: 11px;");
                delBtn.setOnAction(e -> {
                    T item = getTableView().getItems().get(getIndex());
                    if (item != null) onDelete.accept(item);
                });
            }
            @Override
            protected void updateItem(Void v, boolean empty) {
                super.updateItem(v, empty);
                setGraphic(empty ? null : delBtn);
            }
        });
        col.setSortable(false);
        col.setPrefWidth(60);
        return col;
    }
}
