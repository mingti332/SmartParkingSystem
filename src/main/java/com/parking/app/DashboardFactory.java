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
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;


//瑙掕壊鍒嗘祦
public class DashboardFactory {

    private static final DateTimeFormatter DT_SPACE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter DT_SLASH_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm");

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

    public Parent createMainView(User user, Runnable onLogout) {
        this.currentUser = user;

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label userLabel = new Label("\u5f53\u524d\u7528\u6237\uff1a" + user.getUsername() + "\uff08" + roleLabel(user.getRole()) + "\uff09"); // 当前用户： | （ | ）
        Button logoutButton = new Button("\u9000\u51fa\u767b\u5f55"); // 退出登录
        logoutButton.setOnAction(e -> onLogout.run());
        root.setTop(new HBox(12, userLabel, logoutButton));
        addOperationLog("\u767b\u5f55", "\u7528\u6237\u767b\u5f55\u7cfb\u7edf\uff1a" + user.getUsername()); // 登录 | 用户登录系统：

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
        TextArea out = new TextArea();
        out.setEditable(false);

        TextField spaceId = new TextField();
        spaceId.setPromptText("\u8f66\u4f4dID"); // 车位ID
        TextField start = new TextField("08:00");
        TextField end = new TextField("22:00");

        Button mySpaces = new Button("\u6211\u7684\u8f66\u4f4d"); // 我的车位
        mySpaces.setOnAction(e -> {
            try {
                List<ParkingSpace> rows = parkingSpaceService.queryMySpaces(user.getUserId(), 1, 30);
                out.appendText("\u6211\u7684\u8f66\u4f4d\uff1a\n\n"); // 我的车位：\\n\\n
                for (ParkingSpace s : rows) {
                    out.appendText(
                            "\u8f66\u4f4dID=" + s.getSpaceId() // 车位ID=
                                    + "\uff0c\u8f66\u4f4d\u7f16\u53f7=" + s.getSpaceNumber() // ，车位编号=
                                    + "\uff0c\u6240\u5c5e\u505c\u8f66\u573aID=" + s.getLotId() // ，所属停车场ID=
                                    + "\uff0c\u6240\u6709\u8005ID=" + s.getOwnerId() // ，所有者ID=
                                    + "\uff0c\u7c7b\u578b=" + spaceTypeLabel(s.getType()) // ，类型=
                                    + "\uff0c\u72b6\u6001=" + spaceStatusLabel(s.getStatus()) // ，状态=
                                    + "\uff0c\u5171\u4eab\u65f6\u95f4=" + s.getShareStartTime() + "~" + s.getShareEndTime() // ，共享时间=
                                    + "\n\n"
                    );
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button setShare = new Button("\u8bbe\u7f6e\u5171\u4eab\u65f6\u6bb5"); // 设置共享时段
        setShare.setOnAction(e -> {
            try {
                parkingSpaceService.updateMyShareWindow(
                        requireLong(spaceId, "\u8f66\u4f4dID"), // 车位ID
                        user.getUserId(),
                        requireTime(start, "\u5f00\u59cb\u65f6\u95f4"), // 开始时间
                        requireTime(end, "\u7ed3\u675f\u65f6\u95f4") // 结束时间
                );
                out.appendText("\u5171\u4eab\u65f6\u6bb5\u66f4\u65b0\u6210\u529f\n"); // 共享时段更新成功\\n

            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button ownerReservations = new Button("\u540d\u4e0b\u9884\u7ea6"); // 名下预约
        ownerReservations.setOnAction(e -> {
            try {
                List<Reservation> rows = reservationService.getOwnerReservations(user.getUserId(), 1, 30);
                out.appendText("\n\u3010\u540d\u4e0b\u9884\u7ea6\u3011\n"); // \\n【名下预约】\\n
                if (rows.isEmpty()) {
                    out.appendText("\u6682\u65e0\u9884\u7ea6\u8bb0\u5f55\n\n"); // 暂无预约记录\\n\\n
                    return;
                }
                for (Reservation r : rows) {
                    out.appendText(
                            "\u9884\u7ea6ID=" + r.getReservationId() // 预约ID=
                                    + "\uff0c\u8f66\u4f4dID=" + r.getSpaceId() // ，车位ID=
                                    + "\uff0c\u8f66\u4e3bID=" + r.getUserId() // ，车主ID=
                                    + "\uff0c\u9884\u7ea6\u5f00\u59cb=" + fmtDateTime(r.getReserveStart()) // ，预约开始=
                                    + "\uff0c\u9884\u7ea6\u7ed3\u675f=" + fmtDateTime(r.getReserveEnd()) // ，预约结束=
                                    + "\uff0c\u72b6\u6001=" + r.getStatus() // ，状态=
                                    + "\uff0c\u521b\u5efa\u65f6\u95f4=" + fmtDateTime(r.getCreateTime()) // ，创建时间=
                                    + "\n"
                    );
                }
                out.appendText("\n");
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button income = new Button("\u6536\u76ca\u660e\u7ec6"); // 收益明细
        income.setOnAction(e -> {
            try {
                List<Map<String, Object>> rows = revenueService.getOwnerIncomeDetail(user.getUserId(), 1, 30);
                out.appendText("\n\u3010\u6536\u76ca\u660e\u7ec6\u3011\n"); // \\n【收益明细】\\n
                for (Map<String, Object> row : rows) {
                    out.appendText(formatRowMap(row) + "\n");
                }
                BigDecimal total = revenueService.getOwnerIncomeTotal(user.getUserId());
                out.appendText("\u603b\u6536\u76ca=" + formatAmount(total) + "\n\n"); // 总收益=
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        VBox box = new VBox(10,
                new Label("\u8f66\u4f4d\u6240\u6709\u8005\u5de5\u4f5c\u53f0"), // 车位所有者工作台
                new HBox(8, mySpaces, ownerReservations, income),
                new HBox(8, spaceId, new Label("\u5f00\u59cb"), start, new Label("\u7ed3\u675f"), end, setShare), // 开始 | 结束
                out);
        box.setPadding(new Insets(10));
        return box;
    }

    private Parent carOwnerView(User user) {
        TabPane tabs = new TabPane();
        tabs.getTabs().addAll(reservationTab(user), parkingTab(user));
        return tabs;
    }
    private Tab reservationTab(User user) {
        TextField spaceId = new TextField();

        spaceId.setPromptText("\u8f66\u4f4dID"); // 车位ID
        TextField start = new TextField(LocalDateTime.now().plusMinutes(10).withSecond(0).withNano(0).format(DT_SPACE_FMT));
        TextField end = new TextField(LocalDateTime.now().plusHours(2).withSecond(0).withNano(0).format(DT_SPACE_FMT));
        start.setPromptText("\u5f00\u59cb\uff08yyyy-MM-dd HH:mm \u6216 HH:mm\uff09"); // 开始（yyyy-MM-dd HH:mm 或 HH:mm）
        end.setPromptText("\u7ed3\u675f\uff08yyyy-MM-dd HH:mm \u6216 HH:mm\uff09"); // 结束（yyyy-MM-dd HH:mm 或 HH:mm）
        TextArea out = new TextArea();

        Button queryAvailable = new Button("\u67e5\u8be2\u53ef\u7528\u8f66\u4f4d"); // 查询可用车位
        queryAvailable.setOnAction(e -> {
            try {
                List<ParkingSpace> rows = parkingSpaceService.queryAvailableSpaces(requireDateTime(start, "\u5f00\u59cb\u65f6\u95f4"), requireDateTime(end, "\u7ed3\u675f\u65f6\u95f4"), 1, 30); // 开始时间 | 结束时间
                out.appendText("\u53ef\u7528\u8f66\u4f4d\u6570\u91cf=" + rows.size() + "\n\n"); // 可用车位数量=
                for (ParkingSpace s : rows) {
                    out.appendText(
                            "\u8f66\u4f4dID=" + s.getSpaceId() // 车位ID=
                                    + "\uff0c\u7f16\u53f7=" + s.getSpaceNumber() // ，编号=
                                    + "\uff0c\u505c\u8f66\u573aID=" + s.getLotId() // ，停车场ID=
                                    + "\uff0c\u7c7b\u578b=" + spaceTypeLabel(s.getType()) // ，类型=
                                    + "\uff0c\u72b6\u6001=" + spaceStatusLabel(s.getStatus()) // ，状态=
                                    + "\uff0c\u5171\u4eab\u65f6\u95f4=" + s.getShareStartTime() + "~" + s.getShareEndTime() // ，共享时间=
                                    + "\n\n"
                    );
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button reserve = new Button("\u63d0\u4ea4\u9884\u7ea6"); // 提交预约
        reserve.setOnAction(e -> {
            try {
                long rid = reservationService.reserve(user.getUserId(), requireLong(spaceId, "\u8f66\u4f4dID"), requireDateTime(start, "\u5f00\u59cb\u65f6\u95f4"), requireDateTime(end, "\u7ed3\u675f\u65f6\u95f4")); // 车位ID | 开始时间 | 结束时间
                out.appendText("\u9884\u7ea6\u6210\u529f\uff0c\u9884\u7ea6ID=" + rid + "\n"); // 预约成功，预约ID=
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button cancel = new Button("\u53d6\u6d88\u9884\u7ea6"); // 取消预约
        cancel.setOnAction(e -> {
            try {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("\u53d6\u6d88\u9884\u7ea6"); // 取消预约
                dialog.setHeaderText("\u8bf7\u8f93\u5165\u8981\u53d6\u6d88\u7684\u9884\u7ea6ID"); // 请输入要取消的预约ID
                dialog.setContentText("\u9884\u7ea6ID:"); // 预约ID:
                Optional<String> input = dialog.showAndWait();
                if (input.isEmpty()) {
                    return;
                }
                long rid = Long.parseLong(input.get().trim());
                reservationService.cancel(rid, user.getUserId(), requireLong(spaceId, "\u8f66\u4f4dID")); // 车位ID
                out.appendText("\u53d6\u6d88\u6210\u529f\uff0c\u9884\u7ea6ID=" + rid + "\n"); // 取消成功，预约ID=
            } catch (NumberFormatException ex) {
                out.appendText("\u9519\u8bef\uff1a\u9884\u7ea6ID\u5fc5\u987b\u662f\u6570\u5b57\n"); // 错误：预约ID必须是数字\\n
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button myReservations = new Button("\u6211\u7684\u9884\u7ea6"); // 我的预约
        myReservations.setOnAction(e -> {
            try {
                List<Reservation> rows = reservationService.getMyReservations(user.getUserId(), 1, 30);
                for (Reservation r : rows) {
                    out.appendText(
                            "\u9884\u7ea6ID=" + r.getReservationId() // 预约ID=
                                                                        + "\uff0c\u8f66\u4f4dID=" + r.getSpaceId() // ，车位ID=
                                    + "\uff0c\u72b6\u6001=" + r.getStatus() // ，状态=
                                    + "\uff0c\u9884\u7ea6\u5f00\u59cb=" + fmtDateTime(r.getReserveStart()) // ，预约开始=
                                    + "\uff0c\u9884\u7ea6\u7ed3\u675f=" + fmtDateTime(r.getReserveEnd()) // ，预约结束=
                                    + "\uff0c\u521b\u5efa\u65f6\u95f4=" + fmtDateTime(r.getCreateTime()) // ，创建时间=
                                    + "\uff0c\u53d6\u6d88\u65f6\u95f4=" + fmtDateTime(r.getCancelTime()) // ，取消时间=
                                    + "\n\n"
                    );
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        VBox body = new VBox(10,
                new HBox(8, spaceId),
                new HBox(8, new Label("\u5f00\u59cb"), start, new Label("\u7ed3\u675f"), end), // 开始 | 结束
                new HBox(8, queryAvailable, reserve, cancel, myReservations),
                out);
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u9884\u7ea6\u7ba1\u7406", body); // 预约管理
        tab.setClosable(false);
        return tab;
    }
    private Tab parkingTab(User user) {
        TextField reservationId = new TextField();
        reservationId.setPromptText("\u9884\u7ea6ID\uff08\u53ef\u9009\uff0c\u65e0\u9884\u7ea6\u53ef\u7559\u7a7a\uff09"); // 预约ID（可选，无预约可留空）
        TextField spaceId = new TextField();
        spaceId.setPromptText("\u8f66\u4f4dID\uff08\u5165\u573a\u5fc5\u586b\uff09"); // 车位ID（入场必填）
        TextField recordId = new TextField();
        recordId.setPromptText("\u505c\u8f66\u8bb0\u5f55ID"); // 停车记录ID
        TextArea out = new TextArea();

        Button entry = new Button("\u5165\u573a\u767b\u8bb0"); // 入场登记
        entry.setOnAction(e -> {
            try {
                Long reserveId = null;
                String reserveText = reservationId.getText() == null ? "" : reservationId.getText().trim();
                if (!reserveText.isEmpty()) {
                    reserveId = Long.parseLong(reserveText);
                }
                long sid = requireLong(spaceId, "\u8f66\u4f4dID"); // 车位ID
                long rid = parkingRecordService.entry(reserveId, user.getUserId(), sid, LocalDateTime.now());
                out.appendText("\u5165\u573a\u767b\u8bb0\u6210\u529f\uff0c\u505c\u8f66\u8bb0\u5f55ID=" + rid + "\n"); // 入场登记成功，停车记录ID=
            } catch (NumberFormatException ex) {
                out.appendText("\u9519\u8bef\uff1a\u9884\u7ea6ID\u5fc5\u987b\u662f\u6570\u5b57\n"); // 错误：预约ID必须是数字\\n
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button myRecords = new Button("\u6211\u7684\u505c\u8f66\u8bb0\u5f55"); // 我的停车记录
        myRecords.setOnAction(e -> {
            try {
                List<ParkingRecord> rows = parkingRecordService.getMyParkingRecords(user.getUserId(), 1, 30);
                for (ParkingRecord r : rows) {
                    out.appendText(
                            "\u8bb0\u5f55ID=" + r.getRecordId() // 记录ID=
                                    + "\uff0c\u9884\u7ea6ID=" + r.getReservationId() // ，预约ID=
                                    + "\uff0c\u8f66\u4f4dID=" + r.getSpaceId() // ，车位ID=
                                    + "\uff0c\u5165\u573a\u65f6\u95f4=" + fmtDateTime(r.getEntryTime()) // ，入场时间=
                                    + "\uff0c\u51fa\u573a\u65f6\u95f4=" + fmtDateTime(r.getExitTime()) // ，出场时间=
                                    + "\uff0c\u505c\u8f66\u65f6\u957f(\u5206)=" + r.getDuration() // ，停车时长(分)=
                                    + "\uff0c\u8d39\u7528=" + r.getFee() // ，费用=
                                    + "\n\n"
                    );
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button exitAndPay = new Button("\u51fa\u573a\u5e76\u652f\u4ed8"); // 出场并支付
        exitAndPay.setOnAction(e -> {
            try {
                BigDecimal fee = parkingRecordService.exitAndPay(requireLong(recordId, "\u505c\u8f66\u8bb0\u5f55ID"), "WECHAT"); // 停车记录ID
                out.appendText("\u652f\u4ed8\u6210\u529f\uff0c\u8d39\u7528=" + fee + "\n"); // 支付成功，费用=
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button myPayments = new Button("\u6211\u7684\u652f\u4ed8\u8bb0\u5f55"); // 我的支付记录
        myPayments.setOnAction(e -> {
            try {
                List<Map<String, Object>> rows = paymentService.getMyPayments(user.getUserId(), "", 1, 30);
                for (Map<String, Object> row : rows) {
                    out.appendText(formatRowMap(row) + "\n\n");
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        VBox body = new VBox(10,
                sectionBox("\u505c\u8f66\u5165\u573a", // 停车入场
                        new HBox(8, reservationId, spaceId, entry)),
                sectionBox("\u51fa\u573a\u7f34\u8d39", // 出场缴费
                        new HBox(8, recordId, exitAndPay)),
                sectionBox("\u67e5\u8be2", // 查询
                        new HBox(8, myRecords, myPayments)),
                out);
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u505c\u8f66\u4e0e\u652f\u4ed8", body); // 停车与支付
        tab.setClosable(false);
        return tab;
    }
    private Tab userTab() {
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
        TextArea out = new TextArea();

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
                addOperationLog("\u7528\u6237\u7ba1\u7406", "\u65b0\u589e\u7528\u6237ID=" + uid); // 用户管理 | 新增用户ID=
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
        disable.setOnAction(e -> run(() -> userAdminService.changeStatus(requireLong(userId, "\u7528\u6237ID"), 0), out, reload)); // 用户ID
        Button enable = new Button("\u542f\u7528"); // 启用
        enable.setOnAction(e -> run(() -> userAdminService.changeStatus(requireLong(userId, "\u7528\u6237ID"), 1), out, reload)); // 用户ID
        Button reset = new Button("\u91cd\u7f6e\u5bc6\u7801"); // 重置密码
        reset.setOnAction(e -> run(() -> userAdminService.resetPassword(requireLong(userId, "\u7528\u6237ID"), newPwd.getText().trim()), out, null)); // 用户ID
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
                addOperationLog("\u7528\u6237\u7ba1\u7406", "\u5220\u9664\u7528\u6237ID=" + uid); // 用户管理 | 删除用户ID=

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

        FlowPane actionRow = new FlowPane(8, 8, disable, enable, reset, deleteUser);
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
        TextField keyword = new TextField();
        TextField addName = new TextField();
        TextField addAddress = new TextField();
        TextField addTotal = new TextField();
        TextField addOpen = new TextField("08:00");
        TextField addClose = new TextField("22:00");
        TextField addDesc = new TextField();
        TextField editId = new TextField();
        TextField editName = new TextField();
        TextField editAddress = new TextField();
        TextField editTotal = new TextField();
        TextField editOpen = new TextField("08:00");
        TextField editClose = new TextField("22:00");
        TextField editDesc = new TextField();

        keyword.setPromptText("\u5173\u952e\u5b57\uff08\u7559\u7a7a=\u5168\u90e8\uff09"); // 关键字（留空=全部） | 查询说明：1.输入停车场名称或地址任一部分，可模糊查询；2.关键字留空后点击“查询”，查询全部停车场。
        keyword.setPrefWidth(420);
        addName.setPromptText("\u505c\u8f66\u573a\u540d\u79f0"); // 停车场名称
        addAddress.setPromptText("\u5730\u5740"); // 地址
        addTotal.setPromptText("\u603b\u8f66\u4f4d\u6570"); // 总车位数
        addOpen.setPromptText("\u5f00\u653e\u65f6\u95f4 HH:mm"); // 开放时间 HH:mm
        addClose.setPromptText("\u5173\u95ed\u65f6\u95f4 HH:mm"); // 关闭时间 HH:mm
        addDesc.setPromptText("\u5907\u6ce8"); // 备注
        editId.setPromptText("ID");
        editName.setPromptText("\u505c\u8f66\u573a\u540d\u79f0"); // 停车场名称
        editAddress.setPromptText("\u5730\u5740"); // 地址
        editTotal.setPromptText("\u603b\u8f66\u4f4d\u6570"); // 总车位数
        editOpen.setPromptText("\u5f00\u653e\u65f6\u95f4 HH:mm"); // 开放时间 HH:mm
        editClose.setPromptText("\u5173\u95ed\u65f6\u95f4 HH:mm"); // 关闭时间 HH:mm
        editDesc.setPromptText("\u5907\u6ce8"); // 备注

        addName.setPrefWidth(180);
        addAddress.setPrefWidth(220);
        addTotal.setPrefWidth(120);
        addOpen.setPrefWidth(120);
        addClose.setPrefWidth(120);
        addDesc.setPrefWidth(200);
        editId.setPrefWidth(90);
        editName.setPrefWidth(180);
        editAddress.setPrefWidth(220);
        editTotal.setPrefWidth(120);
        editOpen.setPrefWidth(120);
        editClose.setPrefWidth(120);
        editDesc.setPrefWidth(200);

        Button query = new Button("\u67e5\u8be2"); // 查询
        query.setTooltip(new Tooltip("\u7559\u7a7a\u5173\u952e\u5b57\u5e76\u70b9\u51fb\u6b64\u6309\u94ae\uff0c\u53ef\u67e5\u8be2\u5168\u90e8\u505c\u8f66\u573a\u3002")); // 留空关键字并点击此按钮，可查询全部停车场。
        query.setOnAction(e -> {
            try {
                List<ParkingLot> rows = parkingLotService.queryLots(keyword.getText(), 1, 50);
                out.clear();
                for (ParkingLot lot : rows) {
                    out.appendText("\u505c\u8f66\u573aID=" + lot.getLotId() // 停车场ID=
                            + "\uff0c\u540d\u79f0=" + lot.getLotName() // ，名称=
                            + "\uff0c\u5730\u5740=" + lot.getAddress() // ，地址=
                            + "\uff0c\u603b\u8f66\u4f4d\u6570=" + lot.getTotalSpaces() // ，总车位数=
                            + "\uff0c\u8425\u4e1a\u65f6\u95f4=" + lot.getOpenTime() + "~" + lot.getCloseTime() // ，营业时间=
                            + "\uff0c\u5907\u6ce8=" + lot.getDescription() // ，备注=
                            + "\n");
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });
        Label queryHint = new Label("\u8bf4\u660e\uff1a\u8f93\u5165\u5173\u952e\u5b57\u53ef\u6a21\u7cca\u67e5\u8be2\uff1b\u82e5\u9700\u67e5\u8be2\u5168\u90e8\uff0c\u8bf7\u5c06\u5173\u952e\u5b57\u7559\u7a7a\u540e\u70b9\u51fb\u201c\u67e5\u8be2\u201d\u3002"); // 说明：输入关键字可模糊查询；若需查询全部，请将关键字留空后点击“查询”。

        Button add = new Button("\u65b0\u589e"); // 新增
        add.setOnAction(e -> {
            try {
                long newId = parkingLotService.addLot(lotFrom(editId, addName, addAddress, addTotal, addOpen, addClose, addDesc, false));
                out.appendText("\u65b0\u589e\u6210\u529f\uff0c\u505c\u8f66\u573aID=" + newId + "\n"); // 新增成功，停车场ID=
                addOperationLog("\u505c\u8f66\u573a\u7ba1\u7406", "\u65b0\u589e\u505c\u8f66\u573aID=" + newId); // 停车场管理 | 新增停车场ID=
                addName.clear();
                addAddress.clear();
                addTotal.clear();
                addDesc.clear();
                addOpen.setText("08:00");
                addClose.setText("22:00");
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });
        Button update = new Button("\u4fee\u6539"); // 修改
        update.setOnAction(e -> run(() -> parkingLotService.updateLot(lotFrom(editId, editName, editAddress, editTotal, editOpen, editClose, editDesc, true)), out, null));
        Button delete = new Button("\u5220\u9664"); // 删除
        delete.setOnAction(e -> run(() -> parkingLotService.removeLot(requireLong(editId, "ID")), out, null));

        FlowPane addRow = new FlowPane(8, 8, addName, addAddress, addTotal, addOpen, addClose, addDesc, add);
        addRow.setPrefWrapLength(1200);
        FlowPane editRow = new FlowPane(8, 8, editId, editName, editAddress, editTotal, editOpen, editClose, editDesc, update, delete);
        editRow.setPrefWrapLength(1200);

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", new HBox(8, keyword, query), queryHint), // 查询区 | 查询结果
                sectionBox("\u65b0\u589e\u533a\uff08ID\u81ea\u52a8\u751f\u6210\uff09", addRow), // 新增区（ID自动生成）
                sectionBox("\u4fee\u6539/\u5220\u9664\u533a\uff08\u9700\u8f93\u5165\u505c\u8f66\u573aID\uff09", editRow), // 修改/删除区（需输入停车场ID）
                out);
        body.setPadding(new Insets(10));
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        Tab tab = new Tab("\u505c\u8f66\u573a\u7ba1\u7406", pageScroll); // 停车场管理
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
        TextField queryKeyword = new TextField();
        TextField queryOwnerId = new TextField();
        ComboBox<String> queryType = new ComboBox<>(FXCollections.observableArrayList("\u5168\u90e8\u7c7b\u578b", "\u5730\u4e0a", "\u5730\u4e0b")); // 全部类型 | 地上 | 地下
        queryType.setValue("\u5168\u90e8\u7c7b\u578b"); // 全部类型
        ComboBox<String> queryStatus = new ComboBox<>(FXCollections.observableArrayList("\u7a7a\u95f2", "\u5df2\u9884\u7ea6", "\u5360\u7528")); // 空闲 | 已预约 | 占用
        queryStatus.setValue("\u7a7a\u95f2"); // 空闲
        CheckBox showAll = new CheckBox("\u663e\u793a\u5168\u90e8\u8f66\u4f4d\uff08\u542b\u5df2\u9884\u7ea6/\u5360\u7528\uff09"); // 显示全部车位（含已预约/占用）
        TextField queryStart = new TextField("08:00");
        TextField queryEnd = new TextField("22:00");

        TextField addLotId = new TextField("1");
        TextField addOwnerId = new TextField("2");
        TextField addNumber = new TextField();
        ComboBox<String> addType = new ComboBox<>(FXCollections.observableArrayList("\u5730\u4e0a", "\u5730\u4e0b")); // 地上 | 地下
        addType.setValue("\u5730\u4e0a"); // 地上
        ComboBox<String> addStatus = new ComboBox<>(FXCollections.observableArrayList("\u7a7a\u95f2", "\u5df2\u9884\u7ea6", "\u5360\u7528")); // 空闲 | 已预约 | 占用
        addStatus.setValue("\u7a7a\u95f2"); // 空闲
        TextField addStart = new TextField("08:00");
        TextField addEnd = new TextField("22:00");

        TextField editId = new TextField();
        TextField editLotId = new TextField("1");
        TextField editOwnerId = new TextField("2");
        TextField editNumber = new TextField();
        ComboBox<String> editType = new ComboBox<>(FXCollections.observableArrayList("\u5730\u4e0a", "\u5730\u4e0b")); // 地上 | 地下
        editType.setValue("\u5730\u4e0a"); // 地上
        ComboBox<String> editStatus = new ComboBox<>(FXCollections.observableArrayList("\u7a7a\u95f2", "\u5df2\u9884\u7ea6", "\u5360\u7528")); // 空闲 | 已预约 | 占用
        editStatus.setValue("\u7a7a\u95f2"); // 空闲
        TextField editStart = new TextField("08:00");
        TextField editEnd = new TextField("22:00");

        queryKeyword.setPromptText("\u67e5\u8be2\u5173\u952e\u5b57\uff08\u8f66\u4f4d\u7f16\u53f7\uff09"); // 查询关键字（车位编号）
        queryOwnerId.setPromptText("\u67e5\u8be2\u62e5\u6709\u8005ID\uff08\u53ef\u9009\uff0c\u53ef\u67e5\u8be2\u8be5\u8f66\u4e3b\u6240\u6709\u8f66\u4f4d\uff09"); // 查询拥有者ID（可选，可查询该车主所有车位）
        queryStart.setPromptText("\u67e5\u8be2\u5f00\u59cb\u65f6\u95f4\uff08\u4f8b\u5982\uff1a08:00\uff09"); // 查询开始时间（例如：08:00）
        queryEnd.setPromptText("\u67e5\u8be2\u7ed3\u675f\u65f6\u95f4\uff08\u4f8b\u5982\uff1a22:00\uff09"); // 查询结束时间（例如：22:00）
        addLotId.setPromptText("\u505c\u8f66\u573aID\uff08\u4f8b\u5982\uff1a1\uff09"); // 停车场ID（例如：1）
        addOwnerId.setPromptText("\u6240\u6709\u8005ID\uff08\u4f8b\u5982\uff1a2\uff09"); // 所有者ID（例如：2）
        addNumber.setPromptText("\u8f66\u4f4d\u7f16\u53f7\uff08\u624b\u52a8\u8f93\u5165\uff09"); // 车位编号（手动输入）
        addStart.setPromptText("\u5171\u4eab\u5f00\u59cb\uff08\u4f8b\u5982\uff1a08:00\uff09"); // 共享开始（例如：08:00）
        addEnd.setPromptText("\u5171\u4eab\u7ed3\u675f\uff08\u4f8b\u5982\uff1a22:00\uff09"); // 共享结束（例如：22:00）
        editId.setPromptText("\u8f66\u4f4dID\uff08\u4f8b\u5982\uff1a3\uff09"); // 车位ID（例如：3）
        editLotId.setPromptText("\u505c\u8f66\u573aID\uff08\u4f8b\u5982\uff1a1\uff09"); // 停车场ID（例如：1）
        editOwnerId.setPromptText("\u6240\u6709\u8005ID\uff08\u4f8b\u5982\uff1a2\uff09"); // 所有者ID（例如：2）
        editNumber.setPromptText("\u8f66\u4f4d\u7f16\u53f7\uff08\u624b\u52a8\u8f93\u5165\uff09"); // 车位编号（手动输入）
        editStart.setPromptText("\u5171\u4eab\u5f00\u59cb\uff08\u4f8b\u5982\uff1a08:00\uff09"); // 共享开始（例如：08:00）
        editEnd.setPromptText("\u5171\u4eab\u7ed3\u675f\uff08\u4f8b\u5982\uff1a22:00\uff09"); // 共享结束（例如：22:00）

        queryKeyword.setPrefWidth(260);
        queryOwnerId.setPrefWidth(260);
        queryType.setPrefWidth(120);
        queryStatus.setPrefWidth(110);
        queryStart.setPrefWidth(160);
        queryEnd.setPrefWidth(160);
        addLotId.setPrefWidth(120);
        addOwnerId.setPrefWidth(120);
        addNumber.setPrefWidth(240);
        addType.setPrefWidth(110);
        addStatus.setPrefWidth(110);
        addStart.setPrefWidth(140);
        addEnd.setPrefWidth(140);
        editId.setPrefWidth(100);
        editLotId.setPrefWidth(120);
        editOwnerId.setPrefWidth(120);
        editNumber.setPrefWidth(240);
        editType.setPrefWidth(110);
        editStatus.setPrefWidth(110);
        editStart.setPrefWidth(140);
        editEnd.setPrefWidth(140);

        Runnable resetAddForm = () -> {
            addLotId.clear();
            addOwnerId.clear();
            addNumber.clear();
            addType.setValue("\u5730\u4e0a"); // 地上
            addStatus.setValue("\u7a7a\u95f2"); // 空闲
            addStart.clear();
            addEnd.clear();
        };
        Runnable resetEditForm = () -> {
            editId.clear();
            editLotId.clear();
            editOwnerId.clear();
            editNumber.clear();
            editType.setValue("\u5730\u4e0a"); // 地上
            editStatus.setValue("\u7a7a\u95f2"); // 空闲
            editStart.clear();
            editEnd.clear();
        };

        Button query = new Button("\u67e5\u8be2"); // 查询
        query.setOnAction(e -> {
            try {
                String keyword = queryKeyword.getText();
                boolean allType = "\u5168\u90e8\u7c7b\u578b".equals(queryType.getValue()); // 全部类型
                String typeCode = allType ? "" : spaceTypeCode(queryType.getValue());
                String statusCode = spaceStatusCode(queryStatus.getValue());
                Long ownerFilter = null;
                if (queryOwnerId.getText() != null && !queryOwnerId.getText().isBlank()) {
                    ownerFilter = requireLong(queryOwnerId, "\u62e5\u6709\u8005ID"); // 拥有者ID
                }

                List<ParkingSpace> rows;
                if (showAll.isSelected()) {
                    rows = parkingSpaceService.querySpaces("", "", null, 1, 500);
                } else if ("FREE".equalsIgnoreCase(statusCode)) {
                    LocalTime st = requireTime(queryStart, "\u67e5\u8be2\u5f00\u59cb\u65f6\u95f4"); // 查询开始时间
                    LocalTime et = requireTime(queryEnd, "\u67e5\u8be2\u7ed3\u675f\u65f6\u95f4"); // 查询结束时间
                    LocalDateTime startAt = LocalDateTime.now().withHour(st.getHour()).withMinute(st.getMinute()).withSecond(0).withNano(0);
                    LocalDateTime endAt = LocalDateTime.now().withHour(et.getHour()).withMinute(et.getMinute()).withSecond(0).withNano(0);
                    if (!startAt.isBefore(endAt)) {
                        throw new IllegalArgumentException("\u67e5\u8be2\u7ed3\u675f\u65f6\u95f4\u5fc5\u987b\u665a\u4e8e\u5f00\u59cb\u65f6\u95f4"); // 查询结束时间必须晚于开始时间
                    }
                    rows = parkingSpaceService.queryAvailableSpaces(startAt, endAt, 1, 500);
                } else {
                    rows = parkingSpaceService.querySpaces(keyword, statusCode, null, 1, 500);
                }

                out.clear();
                for (ParkingSpace s : rows) {
                    if (!showAll.isSelected()) {
                        if (!allType && !typeCode.equalsIgnoreCase(s.getType())) {
                            continue;
                        }
                        if (keyword != null && !keyword.isBlank() && (s.getSpaceNumber() == null || !s.getSpaceNumber().contains(keyword.trim()))) {
                            continue;
                        }
                    }
                    if (ownerFilter != null && (s.getOwnerId() == null || !ownerFilter.equals(s.getOwnerId()))) {
                        continue;
                    }

                    String displayStatus = ("FREE".equalsIgnoreCase(statusCode) && "RESERVED".equalsIgnoreCase(s.getStatus()))
                            ? "\u65f6\u6bb5\u5185\u53ef\u7528" // 时段内可用
                            : spaceStatusLabel(s.getStatus());
                    out.appendText("\u8f66\u4f4dID=" + s.getSpaceId() // 车位ID=
                            + "\uff0c\u8f66\u4f4d\u7f16\u53f7=" + s.getSpaceNumber() // ，车位编号=
                            + "\uff0c\u505c\u8f66\u573aID=" + s.getLotId() // ，停车场ID=
                            + "\uff0c\u6240\u6709\u8005ID=" + s.getOwnerId() // ，所有者ID=
                            + "\uff0c\u7c7b\u578b=" + spaceTypeLabel(s.getType()) // ，类型=
                            + "\uff0c\u72b6\u6001=" + displayStatus // ，状态=
                            + "\uff0c\u5171\u4eab\u65f6\u95f4=" + s.getShareStartTime() + "~" + s.getShareEndTime() // ，共享时间=
                            + "\n");
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button add = new Button("\u65b0\u589e"); // 新增
        add.setOnAction(e -> {
            try {
                long newId = parkingSpaceService.addSpace(spaceFrom(editId, addLotId, addOwnerId, addNumber, addType, addStatus, addStart, addEnd, false));
                out.appendText("\u65b0\u589e\u6210\u529f\uff0c\u8f66\u4f4dID=" + newId + "\n"); // 新增成功，车位ID=
                addOperationLog("\u8f66\u4f4d\u7ba1\u7406", "\u65b0\u589e\u8f66\u4f4dID=" + newId); // 车位管理 | 新增车位ID=
                resetAddForm.run();
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });
        Button update = new Button("\u4fee\u6539"); // 修改
        update.setOnAction(e -> run(
                () -> parkingSpaceService.updateSpace(spaceFrom(editId, editLotId, editOwnerId, editNumber, editType, editStatus, editStart, editEnd, true)),
                out,
                resetEditForm
        ));
        Button delete = new Button("\u5220\u9664"); // 删除
        delete.setOnAction(e -> run(
                () -> parkingSpaceService.removeSpace(requireLong(editId, "ID")),
                out,
                resetEditForm
        ));

        FlowPane queryRow = new FlowPane(8, 8, queryKeyword, queryOwnerId, queryType, queryStatus, showAll, query);
        queryRow.setPrefWrapLength(1200);
        FlowPane queryTimeRow = new FlowPane(8, 8, queryStart, queryEnd);
        queryTimeRow.setPrefWrapLength(1200);
        FlowPane addRow = new FlowPane(8, 8, addLotId, addOwnerId, addNumber, addType, addStatus, addStart, addEnd, add);
        addRow.setPrefWrapLength(1200);
        FlowPane editRow = new FlowPane(8, 8, editId, editLotId, editOwnerId, editNumber, editType, editStatus, editStart, editEnd, update, delete);
        editRow.setPrefWrapLength(1200);

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", // 查询区
                        queryRow,
                        queryTimeRow),
                sectionBox("\u67e5\u8be2\u7ed3\u679c", out), // 查询结果
                sectionBox("\u65b0\u589e\u533a\uff08ID\u81ea\u52a8\u751f\u6210\uff09", addRow), // 新增区（ID自动生成）
                sectionBox("\u4fee\u6539/\u5220\u9664\u533a\uff08\u9700\u8f93\u5165\u8f66\u4f4dID\uff09", editRow)); // 修改/删除区（需输入车位ID）
        body.setPadding(new Insets(10));
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        Tab tab = new Tab("\u8f66\u4f4d\u7ba1\u7406", pageScroll); // 车位管理
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                pageScroll.setVvalue(0);
            }
        });
        return tab;
    }

    private Tab pricingRuleTab() {
        TextField queryKeyword = new TextField();
        TextField addName = new TextField();
        ComboBox<String> addChargeType = new ComboBox<>(FXCollections.observableArrayList("\u6309\u65f6\u8ba1\u8d39", "\u6309\u6b21\u8ba1\u8d39")); // 按时计费 | 按次计费
        addChargeType.setValue("\u6309\u65f6\u8ba1\u8d39"); // 按时计费
        TextField addUnitPrice = new TextField();
        TextField addUnitTime = new TextField();
        TextField addFixedPrice = new TextField();
        ComboBox<String> addSpaceType = new ComboBox<>(FXCollections.observableArrayList("\u5730\u4e0a", "\u5730\u4e0b")); // 地上 | 地下
        addSpaceType.setValue("\u5730\u4e0a"); // 地上
        ComboBox<String> addStatus = new ComboBox<>(FXCollections.observableArrayList("\u542f\u7528", "\u7981\u7528")); // 启用 | 禁用
        addStatus.setValue("\u542f\u7528"); // 启用

        TextField editId = new TextField();
        TextField editName = new TextField();
        ComboBox<String> editChargeType = new ComboBox<>(FXCollections.observableArrayList("\u6309\u65f6\u8ba1\u8d39", "\u6309\u6b21\u8ba1\u8d39")); // 按时计费 | 按次计费
        editChargeType.setValue("\u6309\u65f6\u8ba1\u8d39"); // 按时计费
        TextField editUnitPrice = new TextField();
        TextField editUnitTime = new TextField();
        TextField editFixedPrice = new TextField();
        ComboBox<String> editSpaceType = new ComboBox<>(FXCollections.observableArrayList("\u5730\u4e0a", "\u5730\u4e0b")); // 地上 | 地下
        editSpaceType.setValue("\u5730\u4e0a"); // 地上
        ComboBox<String> editStatus = new ComboBox<>(FXCollections.observableArrayList("\u542f\u7528", "\u7981\u7528")); // 启用 | 禁用
        editStatus.setValue("\u542f\u7528"); // 启用

        queryKeyword.setPrefWidth(260);
        addName.setPrefWidth(220);
        addChargeType.setPrefWidth(130);
        addUnitPrice.setPrefWidth(140);
        addUnitTime.setPrefWidth(140);
        addFixedPrice.setPrefWidth(140);
        addSpaceType.setPrefWidth(110);
        addStatus.setPrefWidth(110);
        editId.setPrefWidth(80);
        editName.setPrefWidth(220);
        editChargeType.setPrefWidth(130);
        editUnitPrice.setPrefWidth(140);
        editUnitTime.setPrefWidth(140);
        editFixedPrice.setPrefWidth(140);
        editSpaceType.setPrefWidth(110);
        editStatus.setPrefWidth(110);

        queryKeyword.setPromptText("\u89c4\u5219ID/\u540d\u79f0\uff08\u7559\u7a7a=\u5168\u90e8\uff09"); // 规则ID/名称（留空=全部） | 查询说明：1.输入规则ID可按ID查询；2.输入名称关键字可模糊查询；3.留空查询全部规则。
        addName.setPromptText("\u89c4\u5219\u540d\u79f0\uff08\u4f8b\u5982\uff1a\u5730\u4e0a\u6309\u65f6\u8ba1\u8d39\uff09"); // 规则名称（例如：地上按时计费）
        addUnitPrice.setPromptText("\u5355\u4ef7\uff08\u5143\uff09"); // 单价（元）
        addUnitTime.setPromptText("\u5355\u4f4d\u65f6\u957f\uff08\u5206\u949f\uff09"); // 单位时长（分钟）
        addFixedPrice.setPromptText("\u56fa\u5b9a\u4ef7\u683c\uff08\u5143\uff09"); // 固定价格（元）
        editId.setPromptText("\u89c4\u5219ID"); // 规则ID
        editName.setPromptText("\u89c4\u5219\u540d\u79f0\uff08\u4f8b\u5982\uff1a\u5730\u4e0a\u6309\u65f6\u8ba1\u8d39\uff09"); // 规则名称（例如：地上按时计费）
        editUnitPrice.setPromptText("\u5355\u4ef7\uff08\u5143\uff09"); // 单价（元）
        editUnitTime.setPromptText("\u5355\u4f4d\u65f6\u957f\uff08\u5206\u949f\uff09"); // 单位时长（分钟）
        editFixedPrice.setPromptText("\u56fa\u5b9a\u4ef7\u683c\uff08\u5143\uff09"); // 固定价格（元）
        TextArea out = new TextArea();
        out.setEditable(false);
        out.setPrefRowCount(10);

        Button query = new Button("\u67e5\u8be2"); // 查询
        Runnable reload = () -> {
            try {
                // 查询支持两种方式：输入数字按规则ID查询；输入文本按规则名称模糊查询；留空则查询全部。
                List<PricingRule> rows = pricingRuleService.queryRules(queryKeyword.getText(), "", null, 1, 30);
                out.clear();
                for (PricingRule r : rows) {
                    out.appendText("\u89c4\u5219ID=" + r.getRuleId() // 规则ID=
                            + "\uff0c\u89c4\u5219\u540d\u79f0=" + r.getRuleName() // ，规则名称=
                            + "\uff0c\u8ba1\u8d39\u65b9\u5f0f=" + chargeTypeLabel(r.getChargeType()) // ，计费方式=
                            + "\uff0c\u5355\u4ef7=" + r.getUnitPrice() // ，单价=
                            + "\uff0c\u5355\u4f4d\u65f6\u957f(\u5206\u949f)=" + r.getUnitTime() // ，单位时长(分钟)=
                            + "\uff0c\u56fa\u5b9a\u4ef7=" + r.getFixedPrice() // ，固定价=
                            + "\uff0c\u9002\u7528\u8f66\u4f4d=" + spaceTypeLabel(r.getApplicableSpaceType()) // ，适用车位=
                            + "\uff0c\u72b6\u6001=" + enabledLabel(r.getStatus()) // ，状态=
                            + "\n");
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        };
        query.setOnAction(e -> reload.run());

        Button add = new Button("\u65b0\u589e"); // 新增
        add.setOnAction(e -> {
            try {
                long newId = pricingRuleService.addRule(ruleFrom(editId, addName, addChargeType, addUnitPrice, addUnitTime, addFixedPrice, addSpaceType, addStatus, false));
                out.appendText("\u65b0\u589e\u6210\u529f\uff0c\u89c4\u5219ID=" + newId + "\n"); // 新增成功，规则ID=
                addOperationLog("\u8ba1\u8d39\u89c4\u5219", "\u65b0\u589e\u8ba1\u8d39\u89c4\u5219ID=" + newId); // 计费规则 | 新增计费规则ID=
                addName.clear();
                addUnitPrice.clear();
                addUnitTime.clear();
                addFixedPrice.clear();
                addChargeType.setValue("\u6309\u65f6\u8ba1\u8d39"); // 按时计费
                addSpaceType.setValue("\u5730\u4e0a"); // 地上
                addStatus.setValue("\u542f\u7528"); // 启用
                reload.run();
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });
        Button update = new Button("\u4fee\u6539"); // 修改
        update.setOnAction(e -> run(() -> pricingRuleService.updateRule(ruleFrom(editId, editName, editChargeType, editUnitPrice, editUnitTime, editFixedPrice, editSpaceType, editStatus, true)), out, reload));
        Button delete = new Button("\u5220\u9664"); // 删除
        delete.setOnAction(e -> run(() -> pricingRuleService.removeRule(requireLong(editId, "ID")), out, reload));

        Label pricingHelp = new Label("\u8bf4\u660e\uff1a\u6309\u65f6\u8ba1\u8d39\u8bf7\u586b\u5199\u201c\u5355\u4ef7+\u5355\u4f4d\u65f6\u957f\u201d\uff1b\u6309\u6b21\u8ba1\u8d39\u8bf7\u586b\u5199\u201c\u56fa\u5b9a\u4ef7\u683c\u201d\u3002"); // 说明：按时计费请填写“单价+单位时长”；按次计费请填写“固定价格”。
        Label pricingQueryHint = new Label("\u67e5\u8be2\u8bf4\u660e\uff1a\u8f93\u5165\u89c4\u5219ID\u53ef\u6309ID\u67e5\u8be2\uff1b\u8f93\u5165\u540d\u79f0\u5173\u952e\u5b57\u53ef\u6a21\u7cca\u67e5\u8be2\uff1b\u7559\u7a7a\u67e5\u8be2\u5168\u90e8\u3002"); // 查询说明：输入规则ID可按ID查询；输入名称关键字可模糊查询；留空查询全部。
        FlowPane addRow = new FlowPane(8, 8, addName, addChargeType, addUnitPrice, addUnitTime, addFixedPrice, addSpaceType, addStatus, add);
        addRow.setPrefWrapLength(1200);
        FlowPane editRow = new FlowPane(8, 8, editId, editName, editChargeType, editUnitPrice, editUnitTime, editFixedPrice, editSpaceType, editStatus, update, delete);
        editRow.setPrefWrapLength(1200);

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", new HBox(8, queryKeyword, query), pricingQueryHint), // 查询区 | 查询结果
                sectionBox("\u67e5\u8be2\u7ed3\u679c", out), // 查询结果
                sectionBox("\u65b0\u589e\u533a\uff08ID\u81ea\u52a8\u751f\u6210\uff09", addRow, pricingHelp), // 新增区（ID自动生成）
                sectionBox("\u4fee\u6539/\u5220\u9664\u533a\uff08\u9700\u8f93\u5165\u89c4\u5219ID\uff09", editRow)); // 修改/删除区（需输入规则ID）
        body.setPadding(new Insets(10));
        reload.run();
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        Tab tab = new Tab("\u8ba1\u8d39\u89c4\u5219", pageScroll); // 计费规则
        tab.setClosable(false);
        tab.setOnSelectionChanged(e -> {
            if (tab.isSelected()) {
                pageScroll.setVvalue(0);
            }
        });
        return tab;
    }
    private Tab settlementTab() {
        TextField revenueId = new TextField();
        revenueId.setPromptText("\u7ed3\u7b97ID"); // 结算ID
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("\u5168\u90e8", "\u672a\u7ed3\u7b97", "\u5df2\u7ed3\u7b97")); // 全部 | 未结算 | 已结算
        status.setValue("\u5168\u90e8"); // 全部
        TextArea out = new TextArea();
        out.setEditable(false);

        Button query = new Button("\u67e5\u8be2\u7ed3\u7b97\u8bb0\u5f55"); // 查询结算记录
        Runnable reload = () -> {
            try {
                List<Map<String, Object>> rows = revenueService.queryRevenueForAdmin(settleStatusCode(status.getValue()), 1, 50);
                out.clear();
                for (Map<String, Object> row : rows) out.appendText(formatRowMap(row) + "\n");
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        };
        query.setOnAction(e -> reload.run());

        Button approve = new Button("\u5ba1\u6838\u7ed3\u7b97"); // 审核结算
        approve.setOnAction(e -> run(() -> revenueService.settleRevenue(requireLong(revenueId, "\u7ed3\u7b97ID")), out, reload)); // 结算ID

        Label queryHint = new Label("\u67e5\u8be2\u8bf4\u660e\uff1a\u53ef\u6309\u7ed3\u7b97\u72b6\u6001\u7b5b\u9009\uff0c\u70b9\u51fb\u201c\u67e5\u8be2\u7ed3\u7b97\u8bb0\u5f55\u201d\u540e\u5217\u51fa\u7ed3\u679c\u3002"); // 查询说明：可按结算状态筛选，点击“查询结算记录”后列出结果。
        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", new HBox(8, status, query), queryHint), // 查询区
                sectionBox("\u7ef4\u62a4\u533a\uff08\u5ba1\u6838\u7ed3\u7b97\uff09", new HBox(8, revenueId, approve)), // 维护区（审核结算）
                sectionBox("\u67e5\u8be2\u7ed3\u679c", out) // 查询结果
        );
        body.setPadding(new Insets(10));
        reload.run();

        Tab tab = new Tab("\u7ed3\u7b97\u5ba1\u6838", body); // 结算审核
        tab.setClosable(false);
        return tab;
    }

    private Tab reportTab() {
        TextArea out = new TextArea();
        Button income = new Button("\u6536\u5165\u6392\u884c"); // 收入排行
        income.setOnAction(e -> loadReport(out, "income"));
        Button reserve = new Button("\u9884\u7ea6\u6392\u884c"); // 预约排行
        reserve.setOnAction(e -> loadReport(out, "reserve"));
        ComboBox<String> usageMode = new ComboBox<>(FXCollections.observableArrayList(
                "\u5168\u90e8\u5165\u573a\u6b21\u6570", // 全部入场次数
                "\u5165\u573a\u6b21\u6570\u524d\u4e09", // 入场次数前三
                "\u5165\u573a\u6b21\u6570\u5012\u6570\u524d\u4e09" // 入场次数倒数前三
        ));
        usageMode.setValue("\u5168\u90e8\u5165\u573a\u6b21\u6570"); // 全部入场次数
        usageMode.setPrefWidth(180);
        Button usage = new Button("\u5165\u573a\u7edf\u8ba1"); // 入场统计
        usage.setOnAction(e -> loadUsageReport(out, usageMode.getValue()));

        VBox body = new VBox(8, new HBox(8, income, reserve, usageMode, usage), out);
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u7edf\u8ba1\u62a5\u8868", body); // 统计报表
        tab.setClosable(false);
        return tab;
    }

    private Tab operationLogTab() {
        TextField keyword = new TextField();
        keyword.setPromptText("\u5173\u952e\u5b57\uff08\u65e5\u5fd7ID/\u7528\u6237ID/\u7528\u6237\u540d/\u64cd\u4f5c\u7c7b\u578b/\u64cd\u4f5c\u63cf\u8ff0\uff09"); // 关键字（日志ID/用户ID/用户名/操作类型/操作描述）
        keyword.setPrefWidth(420);

        TextArea out = new TextArea();
        out.setEditable(false);

        final int pageSize = 20;
        final int[] pageNo = {1};
        Label pageInfo = new Label("\u7b2c1\u9875"); // 第1页

        Runnable reload = () -> {
            try {
                List<Map<String, Object>> rows = operationLogService.queryLogs(keyword.getText(), pageNo[0], pageSize);
                out.clear();
                if (rows.isEmpty()) {
                    out.appendText("\u6682\u65e0\u64cd\u4f5c\u65e5\u5fd7\u8bb0\u5f55\n"); // 暂无操作日志记录
                }
                for (Map<String, Object> row : rows) {
                    out.appendText(formatRowMap(row) + "\n");
                }
                pageInfo.setText("\u7b2c" + pageNo[0] + "\u9875\uff08\u6bcf\u9875" + pageSize + "\u6761\uff0c\u5f53\u524d" + rows.size() + "\u6761\uff09"); // 第x页（每页x条，当前x条）
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        };

        Button query = new Button("\u67e5\u8be2"); // 查询
        query.setOnAction(e -> {
            pageNo[0] = 1;
            reload.run();
        });
        Button prev = new Button("\u4e0a\u4e00\u9875"); // 上一页
        prev.setOnAction(e -> {
            if (pageNo[0] <= 1) {
                out.appendText("\u63d0\u793a\uff1a\u5df2\u662f\u7b2c\u4e00\u9875\n"); // 提示：已是第一页
                return;
            }
            pageNo[0]--;
            reload.run();
        });
        Button next = new Button("\u4e0b\u4e00\u9875"); // 下一页
        next.setOnAction(e -> {
            pageNo[0]++;
            reload.run();
        });

        Label hint = new Label("\u8bf4\u660e\uff1a\u7ba1\u7406\u5458\u53ef\u6309\u5173\u952e\u5b57\u67e5\u770b\u64cd\u4f5c\u65e5\u5fd7\uff0c\u7528\u4e8e\u5ba1\u8ba1\u8ffd\u6eaf\u3002"); // 说明：管理员可按关键字查看操作日志，用于审计追溯。
        HBox queryRow = new HBox(8, keyword, query);
        HBox.setHgrow(keyword, Priority.ALWAYS);
        HBox pageRow = new HBox(8, prev, next, pageInfo);

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", queryRow, pageRow, hint), // 查询区
                sectionBox("\u65e5\u5fd7\u7ed3\u679c", out) // 日志结果
        );
        body.setPadding(new Insets(10));
        reload.run();

        Tab tab = new Tab("\u64cd\u4f5c\u65e5\u5fd7", body); // 操作日志
        tab.setClosable(false);
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
            return "\u9519\u8bef\uff1a" + ex.getMessage(); // 错误：
        }
        if (ex instanceof NumberFormatException) {
            return "\u9519\u8bef\uff1a\u8f93\u5165\u683c\u5f0f\u4e0d\u6b63\u786e\uff0cID\u5fc5\u987b\u662f\u6570\u5b57"; // 错误：输入格式不正确，ID必须是数字
        }
        if (ex instanceof java.time.format.DateTimeParseException) {
            return "\u9519\u8bef\uff1a\u65f6\u95f4\u683c\u5f0f\u4e0d\u6b63\u786e\uff0c\u8bf7\u4f7f\u7528 yyyy-MM-ddTHH:mm \u6216 HH:mm"; // 错误：时间格式不正确，请使用 yyyy-MM-ddTHH:mm 或 HH:mm
        }
        String msg = ex.getMessage();
        String display = (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : toChineseMessage(msg);
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
        if ("revenueId is required".equalsIgnoreCase(m)) return "\u7ed3\u7b97ID\u4e0d\u80fd\u4e3a\u7a7a"; // 结算ID不能为空
        if ("Revenue not found or already settled".equalsIgnoreCase(m)) return "\u7ed3\u7b97\u8bb0\u5f55\u4e0d\u5b58\u5728\u6216\u5df2\u7ed3\u7b97"; // 结算记录不存在或已结算


        if ("Insert parking lot failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u505c\u8f66\u573a\u5931\u8d25"; // 新增停车场失败
        if ("Insert parking space failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u8f66\u4f4d\u5931\u8d25"; // 新增车位失败
        if ("Insert parking entry failed".equalsIgnoreCase(m)) return "\u751f\u6210\u505c\u8f66\u5165\u573a\u8bb0\u5f55\u5931\u8d25"; // 生成停车入场记录失败
        if ("Insert payment record failed".equalsIgnoreCase(m)) return "\u751f\u6210\u652f\u4ed8\u8bb0\u5f55\u5931\u8d25"; // 生成支付记录失败
        if ("Insert pricing rule failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u8ba1\u8d39\u89c4\u5219\u5931\u8d25"; // 新增计费规则失败
        if ("Insert revenue record failed".equalsIgnoreCase(m)) return "\u751f\u6210\u6536\u76ca\u8bb0\u5f55\u5931\u8d25"; // 生成收益记录失败
        if ("Insert user failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u7528\u6237\u5931\u8d25"; // 新增用户失败
        if ("operationType is required".equalsIgnoreCase(m)) return "\u64cd\u4f5c\u65e5\u5fd7\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a"; // 操作日志类型不能为空

        return m;
    }

    private void loadReport(TextArea out, String type) {
        try {
            List<Map<String, Object>> rows;
            if ("income".equals(type)) rows = reportService.incomeByLot();
            else rows = reportService.reservationCountBySpace();
            out.clear();
            for (Map<String, Object> row : rows) out.appendText(formatRowMap(row) + "\n");
        } catch (SQLException ex) {
            out.appendText(formatError(ex) + "\n");
        }
    }

    private void loadUsageReport(TextArea out, String usageMode) {
        try {
            String mode = usageMode == null ? "\u5168\u90e8\u5165\u573a\u6b21\u6570" : usageMode.trim(); // 全部入场次数
            List<Map<String, Object>> rows = new ArrayList<>(reportService.usageByHour());
            if ("\u5165\u573a\u6b21\u6570\u524d\u4e09".equals(mode)) { // 入场次数前三
                rows.sort(
                        Comparator
                                .comparingInt((Map<String, Object> r) -> safeToInt(r.get("usage_count"))).reversed()
                                .thenComparingInt(r -> safeToInt(r.get("hour_slot")))
                );
                if (rows.size() > 3) rows = new ArrayList<>(rows.subList(0, 3));
            } else if ("\u5165\u573a\u6b21\u6570\u5012\u6570\u524d\u4e09".equals(mode)) { // 入场次数倒数前三
                rows.sort(
                        Comparator
                                .comparingInt((Map<String, Object> r) -> safeToInt(r.get("usage_count")))
                                .thenComparingInt(r -> safeToInt(r.get("hour_slot")))
                );
                if (rows.size() > 3) rows = new ArrayList<>(rows.subList(0, 3));
            } else {
                rows.sort(Comparator.comparingInt(r -> safeToInt(r.get("hour_slot"))));
            }

            out.clear();
            out.appendText("\u7edf\u8ba1\u6a21\u5f0f\uff1a" + mode + "\n"); // 统计模式：
            if (rows.isEmpty()) {
                out.appendText("\u6682\u65e0\u5165\u573a\u8bb0\u5f55\n"); // 暂无入场记录\\n
                return;
            }
            for (Map<String, Object> row : rows) out.appendText(formatRowMap(row) + "\n");
        } catch (SQLException ex) {
            out.appendText(formatError(ex) + "\n");
        }
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
                .replace("GROUND", "\u5730\u4e0a") // 地上
                .replace("UNDERGROUND", "\u5730\u4e0b") // 地下
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

    private PricingRule ruleFrom(TextField id, TextField name, ComboBox<String> chargeType, TextField unitPrice,
                                 TextField unitTime, TextField fixedPrice, ComboBox<String> spaceType, ComboBox<String> status, boolean withId) {
        PricingRule r = new PricingRule();
        if (withId) r.setRuleId(requireLong(id, "ID"));
        r.setRuleName(name.getText().trim());
        r.setChargeType(chargeTypeCode(chargeType.getValue()));
        if ("HOURLY".equalsIgnoreCase(r.getChargeType())) {
            r.setUnitPrice(new BigDecimal(unitPrice.getText().trim()));
            r.setUnitTime(requireInt(unitTime, "\u8ba1\u8d39\u5468\u671f\u5206\u949f")); // 计费周期分钟
        } else {
            r.setFixedPrice(new BigDecimal(fixedPrice.getText().trim()));
        }
        r.setApplicableSpaceType(spaceTypeCode(spaceType.getValue()));
        r.setStatus(enabledStatusValue(status.getValue()));
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
        try {
            action.run();
            out.appendText("\u64cd\u4f5c\u6210\u529f\n"); // 操作成功\\n
            addOperationLog("\u754c\u9762\u64cd\u4f5c", "\u7528\u6237\u6267\u884c\u4e86\u4e00\u6b21\u7ba1\u7406\u64cd\u4f5c"); // 界面操作 | 用户执行了一次管理操作

            if (then != null) then.run();
        } catch (Exception ex) {
            out.appendText(formatError(ex) + "\n");
        }
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
        header.setStyle("-fx-font-weight: bold; -fx-text-fill: #1f2d3d;");
        VBox box = new VBox(8);
        box.getChildren().add(header);
        box.getChildren().addAll(content);
        box.setPadding(new Insets(10));
        box.setStyle("-fx-background-color: #f8fafc; -fx-border-color: #d0d7de; -fx-border-radius: 6; -fx-background-radius: 6;");
        return box;
    }
}

