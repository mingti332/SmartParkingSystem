import pathlib

path = 'E:/Vscode/SmartParkingSystem/src/main/java/com/parking/app/DashboardFactory.java'
content = pathlib.Path(path).read_text(encoding='utf-8')

# --- reservationTab replacement ---
m1 = '    private Tab reservationTab(User user) {'
m2 = '    private Tab parkingTab(User user) {'
s1 = content.find(m1)
s2 = content.find(m2)
if s1 < 0 or s2 < 0:
    print(f'ERROR: reservationTab markers: {s1}, {s2}')
    exit(1)

new_reservation = r'''    private Tab reservationTab(User user) {
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
                if (!cb.getItems().isEmpty() && !cb.getItems().contains(cb.getEditor().getText())) {
                    // keep text but show filtered dropdown
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
        TableColumn<ParkingSpace, String> gIdCol = col("ID", c -> String.valueOf(c.getValue().getSpaceId()), 50);
        TableColumn<ParkingSpace, String> gNumCol = col("编号", c -> c.getValue().getSpaceNumber(), 100);
        TableColumn<ParkingSpace, String> gStatusCol = col("状态", c -> spaceStatusLabel(c.getValue().getStatus()), 70);
        TableColumn<ParkingSpace, String> gTimeCol = col("共享时间", c -> (c.getValue().getShareStartTime() != null ? c.getValue().getShareStartTime().toString() : "") + "~" + (c.getValue().getShareEndTime() != null ? c.getValue().getShareEndTime().toString() : ""), 150);
        groundTable.getColumns().addAll(gIdCol, gNumCol, gStatusCol, gTimeCol);
        groundTable.setFixedCellSize(26);
        double gth = groundTable.getFixedCellSize() * 6 + 34;
        groundTable.setPrefHeight(gth); groundTable.setMinHeight(gth); groundTable.setMaxHeight(gth);

        TableView<ParkingSpace> underTable = new TableView<>();
        underTable.setEditable(false);
        TableColumn<ParkingSpace, String> uIdCol = col("ID", c -> String.valueOf(c.getValue().getSpaceId()), 50);
        TableColumn<ParkingSpace, String> uNumCol = col("编号", c -> c.getValue().getSpaceNumber(), 100);
        TableColumn<ParkingSpace, String> uStatusCol = col("状态", c -> spaceStatusLabel(c.getValue().getStatus()), 70);
        TableColumn<ParkingSpace, String> uTimeCol = col("共享时间", c -> (c.getValue().getShareStartTime() != null ? c.getValue().getShareStartTime().toString() : "") + "~" + (c.getValue().getShareEndTime() != null ? c.getValue().getShareEndTime().toString() : ""), 150);
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
                // if end <= start, assume next day
                if (!endDt.isAfter(startDt)) endDt = endDt.plusDays(1);

                ReservationService.AutoReserveResult result = reservationService.reserveByLotAndType(
                        user.getUserId(), lid, typeCode, startDt, endDt);

                // Get parking lot info
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
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        // === My Reservations table ===
        TableView<Reservation> myResvTable = new TableView<>();
        myResvTable.setEditable(false);
        TableColumn<Reservation, String> mrId = col("预约ID", c -> String.valueOf(c.getValue().getReservationId()), 60);
        TableColumn<Reservation, String> mrSpace = col("车位ID", c -> String.valueOf(c.getValue().getSpaceId()), 60);
        TableColumn<Reservation, String> mrStart = col("开始", c -> fmtDateTime(c.getValue().getReserveStart()), 130);
        TableColumn<Reservation, String> mrEnd = col("结束", c -> fmtDateTime(c.getValue().getReserveEnd()), 130);
        TableColumn<Reservation, String> mrStatus = col("状态", c -> c.getValue().getStatus(), 70);
        TableColumn<Reservation, String> mrCreate = col("创建时间", c -> fmtDateTime(c.getValue().getCreateTime()), 130);
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

        // Cancel button
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

        // Parking lot info button
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

    // Helper for creating simple read-only TableColumn
    private <S> TableColumn<S, String> col(String title, java.util.function.Function<javafx.scene.control.TableColumn.CellDataFeatures<S, String>, String> factory, int width) {
        TableColumn<S, String> c = new TableColumn<>(title);
        c.setCellValueFactory(cd -> new SimpleStringProperty(factory.apply(cd)));
        c.setEditable(false);
        c.setSortable(false);
        c.setPrefWidth(width);
        return c;
    }

'''
content = content[:s1] + new_reservation + content[s2:]
print('reservationTab replaced')
pathlib.Path(path).write_text(content, encoding='utf-8')
print('Done')
