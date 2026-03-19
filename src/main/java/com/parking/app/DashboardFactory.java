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

    public Parent createMainView(User user, Runnable onLogout) {

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(10));

        Label userLabel = new Label("\u5f53\u524d\u7528\u6237\uff1a" + user.getUsername() + "\uff08" + roleLabel(user.getRole()) + "\uff09"); // 褰撳墠鐢ㄦ埛锛?| 锛?| 锛?
        Button logoutButton = new Button("\u9000\u51fa\u767b\u5f55"); // 閫€鍑虹櫥褰?
        logoutButton.setOnAction(e -> onLogout.run());
        root.setTop(new HBox(12, userLabel, logoutButton));

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
        tabs.getTabs().addAll(userTab(), parkingLotTab(), parkingSpaceTab(), pricingRuleTab(), settlementTab(), reportTab());
        return tabs;
    }

    private Parent ownerView(User user) {
        TextArea out = new TextArea();
        out.setEditable(false);

        TextField spaceId = new TextField();
        spaceId.setPromptText("\u8f66\u4f4dID"); // 杞︿綅ID
        TextField start = new TextField("08:00");
        TextField end = new TextField("22:00");

        Button mySpaces = new Button("\u6211\u7684\u8f66\u4f4d"); // 鎴戠殑杞︿綅
        mySpaces.setOnAction(e -> {
            try {
                List<ParkingSpace> rows = parkingSpaceService.queryMySpaces(user.getUserId(), 1, 30);
                out.appendText("\u6211\u7684\u8f66\u4f4d\uff1a\n\n"); // 鎴戠殑杞︿綅锛歕\n\\n
                for (ParkingSpace s : rows) {
                    out.appendText(
                            "\u8f66\u4f4dID=" + s.getSpaceId() // 杞︿綅ID=
                                    + "\uff0c\u8f66\u4f4d\u7f16\u53f7=" + s.getSpaceNumber() // 锛岃溅浣嶇紪鍙?
                                    + "\uff0c\u6240\u5c5e\u505c\u8f66\u573aID=" + s.getLotId() // 锛屾墍灞炲仠杞﹀満ID=
                                    + "\uff0c\u6240\u6709\u8005ID=" + s.getOwnerId() // 锛屾墍鏈夎€匢D=
                                    + "\uff0c\u7c7b\u578b=" + spaceTypeLabel(s.getType()) // 锛岀被鍨?
                                    + "\uff0c\u72b6\u6001=" + spaceStatusLabel(s.getStatus()) // 锛岀姸鎬?
                                    + "\uff0c\u5171\u4eab\u65f6\u95f4=" + s.getShareStartTime() + "~" + s.getShareEndTime() // 锛屽叡浜椂闂?
                                    + "\n\n"
                    );
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button setShare = new Button("\u8bbe\u7f6e\u5171\u4eab\u65f6\u6bb5"); // 璁剧疆鍏变韩鏃舵
        setShare.setOnAction(e -> {
            try {
                parkingSpaceService.updateMyShareWindow(
                        requireLong(spaceId, "\u8f66\u4f4dID"), // 杞︿綅ID
                        user.getUserId(),
                        requireTime(start, "\u5f00\u59cb\u65f6\u95f4"), // 寮€濮嬫椂闂?
                        requireTime(end, "\u7ed3\u675f\u65f6\u95f4") // 缁撴潫鏃堕棿
                );
                out.appendText("\u5171\u4eab\u65f6\u6bb5\u66f4\u65b0\u6210\u529f\n"); // 鍏变韩鏃舵鏇存柊鎴愬姛\\n

            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button ownerReservations = new Button("\u540d\u4e0b\u9884\u7ea6"); // 鍚嶄笅棰勭害
        ownerReservations.setOnAction(e -> {
            try {
                List<Reservation> rows = reservationService.getOwnerReservations(user.getUserId(), 1, 30);
                out.appendText("\n\u3010\u540d\u4e0b\u9884\u7ea6\u3011\n"); // \n銆愬悕涓嬮绾︺€慭n
                if (rows.isEmpty()) {
                    out.appendText("\u6682\u65e0\u9884\u7ea6\u8bb0\u5f55\n\n"); // 鏆傛棤棰勭害璁板綍\n\n
                    return;
                }
                for (Reservation r : rows) {
                    out.appendText(
                            "\u9884\u7ea6ID=" + r.getReservationId() // 棰勭害ID=
                                    + "\uff0c\u8f66\u4f4dID=" + r.getSpaceId() // 锛岃溅浣岻D=
                                    + "\uff0c\u8f66\u4e3bID=" + r.getUserId() // 锛岃溅涓籌D=
                                    + "\uff0c\u9884\u7ea6\u5f00\u59cb=" + fmtDateTime(r.getReserveStart()) // 锛岄绾﹀紑濮?
                                    + "\uff0c\u9884\u7ea6\u7ed3\u675f=" + fmtDateTime(r.getReserveEnd()) // 锛岄绾︾粨鏉?
                                    + "\uff0c\u72b6\u6001=" + r.getStatus() // 锛岀姸鎬?
                                    + "\uff0c\u521b\u5efa\u65f6\u95f4=" + fmtDateTime(r.getCreateTime()) // 锛屽垱寤烘椂闂?
                                    + "\n"
                    );
                }
                out.appendText("\n");
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button income = new Button("\u6536\u76ca\u660e\u7ec6"); // 鏀剁泭鏄庣粏
        income.setOnAction(e -> {
            try {
                List<Map<String, Object>> rows = revenueService.getOwnerIncomeDetail(user.getUserId(), 1, 30);
                out.appendText("\n\u3010\u6536\u76ca\u660e\u7ec6\u3011\n"); // \n銆愭敹鐩婃槑缁嗐€慭n
                for (Map<String, Object> row : rows) {
                    out.appendText(formatRowMap(row) + "\n");
                }
                BigDecimal total = revenueService.getOwnerIncomeTotal(user.getUserId());
                out.appendText("\u603b\u6536\u76ca=" + formatAmount(total) + "\n\n"); // 鎬绘敹鐩?\n\n
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        VBox box = new VBox(10,
                new Label("\u8f66\u4f4d\u6240\u6709\u8005\u5de5\u4f5c\u53f0"), // 杞︿綅鎵€鏈夎€呭伐浣滃彴
                new HBox(8, mySpaces, ownerReservations, income),
                new HBox(8, spaceId, new Label("\u5f00\u59cb"), start, new Label("\u7ed3\u675f"), end, setShare), // 寮€濮?| 缁撴潫
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

        spaceId.setPromptText("\u8f66\u4f4dID"); // 杞︿綅ID
        TextField start = new TextField(LocalDateTime.now().plusMinutes(10).withSecond(0).withNano(0).format(DT_SPACE_FMT));
        TextField end = new TextField(LocalDateTime.now().plusHours(2).withSecond(0).withNano(0).format(DT_SPACE_FMT));
        start.setPromptText("\u5f00\u59cb\uff08yyyy-MM-dd HH:mm \u6216 HH:mm\uff09"); // 寮€濮嬶紙yyyy-MM-dd HH:mm 鎴?HH:mm锛?
        end.setPromptText("\u7ed3\u675f\uff08yyyy-MM-dd HH:mm \u6216 HH:mm\uff09"); // 缁撴潫锛坹yyy-MM-dd HH:mm 鎴?HH:mm锛?
        TextArea out = new TextArea();

        Button queryAvailable = new Button("\u67e5\u8be2\u53ef\u7528\u8f66\u4f4d"); // 鏌ヨ鍙敤杞︿綅
        queryAvailable.setOnAction(e -> {
            try {
                List<ParkingSpace> rows = parkingSpaceService.queryAvailableSpaces(requireDateTime(start, "\u5f00\u59cb\u65f6\u95f4"), requireDateTime(end, "\u7ed3\u675f\u65f6\u95f4"), 1, 30); // 寮€濮嬫椂闂?| 缁撴潫鏃堕棿
                out.appendText("\u53ef\u7528\u8f66\u4f4d\u6570\u91cf=" + rows.size() + "\n\n"); // 鍙敤杞︿綅鏁伴噺=
                for (ParkingSpace s : rows) {
                    out.appendText(
                            "\u8f66\u4f4dID=" + s.getSpaceId() // 杞︿綅ID=
                                    + "\uff0c\u7f16\u53f7=" + s.getSpaceNumber() // 锛岀紪鍙?
                                    + "\uff0c\u505c\u8f66\u573aID=" + s.getLotId() // 锛屽仠杞﹀満ID=
                                    + "\uff0c\u7c7b\u578b=" + spaceTypeLabel(s.getType()) // 锛岀被鍨?
                                    + "\uff0c\u72b6\u6001=" + spaceStatusLabel(s.getStatus()) // 锛岀姸鎬?
                                    + "\uff0c\u5171\u4eab\u65f6\u95f4=" + s.getShareStartTime() + "~" + s.getShareEndTime() // 锛屽叡浜椂闂?
                                    + "\n\n"
                    );
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button reserve = new Button("\u63d0\u4ea4\u9884\u7ea6"); // 鎻愪氦棰勭害
        reserve.setOnAction(e -> {
            try {
                long rid = reservationService.reserve(user.getUserId(), requireLong(spaceId, "\u8f66\u4f4dID"), requireDateTime(start, "\u5f00\u59cb\u65f6\u95f4"), requireDateTime(end, "\u7ed3\u675f\u65f6\u95f4")); // 杞︿綅ID | 寮€濮嬫椂闂?| 缁撴潫鏃堕棿
                out.appendText("\u9884\u7ea6\u6210\u529f\uff0c\u9884\u7ea6ID=" + rid + "\n"); // 棰勭害鎴愬姛锛岄绾D=
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button cancel = new Button("\u53d6\u6d88\u9884\u7ea6"); // 鍙栨秷棰勭害
        cancel.setOnAction(e -> {
            try {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("\u53d6\u6d88\u9884\u7ea6"); // 鍙栨秷棰勭害
                dialog.setHeaderText("\u8bf7\u8f93\u5165\u8981\u53d6\u6d88\u7684\u9884\u7ea6ID"); // 璇疯緭鍏ヨ鍙栨秷鐨勯绾D
                dialog.setContentText("\u9884\u7ea6ID:"); // 棰勭害ID:
                Optional<String> input = dialog.showAndWait();
                if (input.isEmpty()) {
                    return;
                }
                long rid = Long.parseLong(input.get().trim());
                reservationService.cancel(rid, user.getUserId(), requireLong(spaceId, "\u8f66\u4f4dID")); // 杞︿綅ID
                out.appendText("\u53d6\u6d88\u6210\u529f\uff0c\u9884\u7ea6ID=" + rid + "\n"); // 鍙栨秷鎴愬姛锛岄绾D=
            } catch (NumberFormatException ex) {
                out.appendText("\u9519\u8bef\uff1a\u9884\u7ea6ID\u5fc5\u987b\u662f\u6570\u5b57\n"); // 閿欒锛氶绾D蹇呴』鏄暟瀛梊\n
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button myReservations = new Button("\u6211\u7684\u9884\u7ea6"); // 鎴戠殑棰勭害
        myReservations.setOnAction(e -> {
            try {
                List<Reservation> rows = reservationService.getMyReservations(user.getUserId(), 1, 30);
                for (Reservation r : rows) {
                    out.appendText(
                            "\u9884\u7ea6ID=" + r.getReservationId() // 棰勭害ID=
                                                                        + "\uff0c\u8f66\u4f4dID=" + r.getSpaceId() // 锛岃溅浣岻D=
                                    + "\uff0c\u72b6\u6001=" + r.getStatus() // 锛岀姸鎬?
                                    + "\uff0c\u9884\u7ea6\u5f00\u59cb=" + fmtDateTime(r.getReserveStart()) // 锛岄绾﹀紑濮?
                                    + "\uff0c\u9884\u7ea6\u7ed3\u675f=" + fmtDateTime(r.getReserveEnd()) // 锛岄绾︾粨鏉?
                                    + "\uff0c\u521b\u5efa\u65f6\u95f4=" + fmtDateTime(r.getCreateTime()) // 锛屽垱寤烘椂闂?
                                    + "\uff0c\u53d6\u6d88\u65f6\u95f4=" + fmtDateTime(r.getCancelTime()) // 锛屽彇娑堟椂闂?
                                    + "\n\n"
                    );
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        VBox body = new VBox(10,
                new HBox(8, spaceId),
                new HBox(8, new Label("\u5f00\u59cb"), start, new Label("\u7ed3\u675f"), end), // 寮€濮?| 缁撴潫
                new HBox(8, queryAvailable, reserve, cancel, myReservations),
                out);
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u9884\u7ea6\u7ba1\u7406", body); // 棰勭害绠＄悊
        tab.setClosable(false);
        return tab;
    }
    private Tab parkingTab(User user) {
        TextField reservationId = new TextField();
        reservationId.setPromptText("\u9884\u7ea6ID\uff08\u53ef\u9009\uff0c\u65e0\u9884\u7ea6\u53ef\u7559\u7a7a\uff09"); // 棰勭害ID锛堝彲閫夛紝鏃犻绾﹀彲鐣欑┖锛?
        TextField spaceId = new TextField();
        spaceId.setPromptText("\u8f66\u4f4dID\uff08\u5165\u573a\u5fc5\u586b\uff09"); // 杞︿綅ID锛堝叆鍦哄繀濉級
        TextField recordId = new TextField();
        recordId.setPromptText("\u505c\u8f66\u8bb0\u5f55ID"); // 鍋滆溅璁板綍ID
        TextArea out = new TextArea();

        Button entry = new Button("\u5165\u573a\u767b\u8bb0"); // 鍏ュ満鐧昏
        entry.setOnAction(e -> {
            try {
                Long reserveId = null;
                String reserveText = reservationId.getText() == null ? "" : reservationId.getText().trim();
                if (!reserveText.isEmpty()) {
                    reserveId = Long.parseLong(reserveText);
                }
                long sid = requireLong(spaceId, "\u8f66\u4f4dID"); // 杞︿綅ID
                long rid = parkingRecordService.entry(reserveId, user.getUserId(), sid, LocalDateTime.now());
                out.appendText("\u5165\u573a\u767b\u8bb0\u6210\u529f\uff0c\u505c\u8f66\u8bb0\u5f55ID=" + rid + "\n"); // 鍏ュ満鐧昏鎴愬姛锛屽仠杞﹁褰旾D=
            } catch (NumberFormatException ex) {
                out.appendText("\u9519\u8bef\uff1a\u9884\u7ea6ID\u5fc5\u987b\u662f\u6570\u5b57\n"); // 閿欒锛氶绾D蹇呴』鏄暟瀛梊\n
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button myRecords = new Button("\u6211\u7684\u505c\u8f66\u8bb0\u5f55"); // 鎴戠殑鍋滆溅璁板綍
        myRecords.setOnAction(e -> {
            try {
                List<ParkingRecord> rows = parkingRecordService.getMyParkingRecords(user.getUserId(), 1, 30);
                for (ParkingRecord r : rows) {
                    out.appendText(
                            "\u8bb0\u5f55ID=" + r.getRecordId() // 璁板綍ID=
                                    + "\uff0c\u9884\u7ea6ID=" + r.getReservationId() // 锛岄绾D=
                                    + "\uff0c\u8f66\u4f4dID=" + r.getSpaceId() // 锛岃溅浣岻D=
                                    + "\uff0c\u5165\u573a\u65f6\u95f4=" + fmtDateTime(r.getEntryTime()) // 锛屽叆鍦烘椂闂?
                                    + "\uff0c\u51fa\u573a\u65f6\u95f4=" + fmtDateTime(r.getExitTime()) // 锛屽嚭鍦烘椂闂?
                                    + "\uff0c\u505c\u8f66\u65f6\u957f(\u5206)=" + r.getDuration() // 锛屽仠杞︽椂闀?鍒?=
                                    + "\uff0c\u8d39\u7528=" + r.getFee() // 锛岃垂鐢?
                                    + "\n\n"
                    );
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button exitAndPay = new Button("\u51fa\u573a\u5e76\u652f\u4ed8"); // 鍑哄満骞舵敮浠?
        exitAndPay.setOnAction(e -> {
            try {
                BigDecimal fee = parkingRecordService.exitAndPay(requireLong(recordId, "\u505c\u8f66\u8bb0\u5f55ID"), "WECHAT"); // 鍋滆溅璁板綍ID
                out.appendText("\u652f\u4ed8\u6210\u529f\uff0c\u8d39\u7528=" + fee + "\n"); // 鏀粯鎴愬姛锛岃垂鐢?
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button myPayments = new Button("\u6211\u7684\u652f\u4ed8\u8bb0\u5f55"); // 鎴戠殑鏀粯璁板綍
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
                sectionBox("\u505c\u8f66\u5165\u573a", // 鍋滆溅鍏ュ満
                        new HBox(8, reservationId, spaceId, entry)),
                sectionBox("\u51fa\u573a\u7f34\u8d39", // 鍑哄満缂磋垂
                        new HBox(8, recordId, exitAndPay)),
                sectionBox("\u67e5\u8be2", // 鏌ヨ
                        new HBox(8, myRecords, myPayments)),
                out);
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u505c\u8f66\u4e0e\u652f\u4ed8", body); // 鍋滆溅涓庢敮浠?
        tab.setClosable(false);
        return tab;
    }
    private Tab userTab() {
        TableView<User> table = new TableView<>();
        TableColumn<User, String> id = new TableColumn<>("ID");
        id.setCellValueFactory(c -> new SimpleStringProperty(String.valueOf(c.getValue().getUserId())));
        TableColumn<User, String> username = new TableColumn<>("\u7528\u6237\u540d"); // 鐢ㄦ埛鍚?
        username.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getUsername()));
        TableColumn<User, String> role = new TableColumn<>("\u89d2\u8272"); // 瑙掕壊
        role.setCellValueFactory(c -> new SimpleStringProperty(roleLabel(c.getValue().getRole())));
        TableColumn<User, String> status = new TableColumn<>("\u72b6\u6001"); // 鐘舵€?
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus() == 1 ? "\u542f\u7528" : "\u7981\u7528")); // 鍚敤 | 绂佺敤
        TableColumn<User, String> realName = new TableColumn<>("\u771f\u5b9e\u59d3\u540d"); // 鐪熷疄濮撳悕
        realName.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getRealName()));
        TableColumn<User, String> phone = new TableColumn<>("\u624b\u673a\u53f7"); // 鎵嬫満鍙?
        phone.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getPhone()));
        TableColumn<User, String> createTime = new TableColumn<>("\u521b\u5efa\u65f6\u95f4"); // 鍒涘缓鏃堕棿
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
        userId.setPromptText("\u7528\u6237ID\uff08\u7528\u4e8e\u7981\u7528/\u542f\u7528/\u91cd\u7f6e/\u5220\u9664\uff09"); // 鐢ㄦ埛ID锛堢敤浜庣鐢?鍚敤/閲嶇疆/鍒犻櫎锛?
        table.getSelectionModel().selectedItemProperty().addListener((obs, oldV, selected) -> {
            if (selected != null && selected.getUserId() != null) {
                userId.setText(String.valueOf(selected.getUserId()));
            }
        });
        keyword.setPromptText("\u5173\u952e\u5b57\uff08ID/\u7528\u6237\u540d/\u59d3\u540d\uff1b\u624b\u673a\u53f7\u4ec5\u652f\u6301\u5b8c\u657411\u4f4d\u6216\u540e4\u4f4d\uff09"); // 鍏抽敭瀛楋紙ID/鐢ㄦ埛鍚?濮撳悕锛涙墜鏈哄彿浠呮敮鎸佸畬鏁?1浣嶆垨鍚?浣嶏級
        keyword.setPrefWidth(420);
        ComboBox<String> roleBox = new ComboBox<>(FXCollections.observableArrayList("\u5168\u90e8", "\u7ba1\u7406\u5458", "\u8f66\u4f4d\u6240\u6709\u8005", "\u8f66\u4e3b")); // 鍏ㄩ儴 | 绠＄悊鍛?| 杞︿綅鎵€鏈夎€?| 杞︿富
        roleBox.setValue("\u5168\u90e8"); // 鍏ㄩ儴
        roleBox.setPrefWidth(130);
        ComboBox<String> statusBox = new ComboBox<>(FXCollections.observableArrayList("\u5168\u90e8", "\u542f\u7528", "\u7981\u7528")); // 鍏ㄩ儴 | 鍚敤 | 绂佺敤
        statusBox.setValue("\u5168\u90e8"); // 鍏ㄩ儴
        statusBox.setPrefWidth(110);
        TextField newPwd = new TextField("123456");
        newPwd.setPromptText("\u65b0\u5bc6\u7801"); // 鏂板瘑鐮?
        TextField addUsername = new TextField();
        addUsername.setPromptText("\u65b0\u7528\u6237\u540d"); // 鏂扮敤鎴峰悕
        addUsername.setPrefWidth(140);
        TextField addPassword = new TextField();
        addPassword.setPromptText("\u521d\u59cb\u5bc6\u7801"); // 鍒濆瀵嗙爜
        addPassword.setPrefWidth(120);
        TextField addRealName = new TextField();
        addRealName.setPromptText("\u771f\u5b9e\u59d3\u540d\uff08\u53ef\u9009\uff09"); // 鐪熷疄濮撳悕锛堝彲閫夛級
        addRealName.setPrefWidth(140);
        TextField addPhone = new TextField();
        addPhone.setPromptText("\u624b\u673a\u53f7\uff0811\u4f4d\uff0c\u53ef\u9009\uff09"); // 鎵嬫満鍙凤紙11浣嶏紝鍙€夛級
        addPhone.setPrefWidth(170);
        ComboBox<String> addRole = new ComboBox<>(FXCollections.observableArrayList("\u8f66\u4e3b", "\u8f66\u4f4d\u6240\u6709\u8005")); // 杞︿富 | 杞︿綅鎵€鏈夎€?
        addRole.setValue("\u8f66\u4e3b"); // 杞︿富
        Label pageInfo = new Label("\u7b2c1\u9875"); // 绗?椤?
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
                pageInfo.setText("\u7b2c" + pageNo[0] + "\u9875\uff08\u6bcf\u9875" + pageSize + "\u6761\uff0c\u5f53\u524d" + rows.size() + "\u6761\uff09"); // 绗瑇椤碉紙姣忛〉x鏉★紝褰撳墠x鏉★級
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        };

        Button query = new Button("\u67e5\u8be2"); // 鏌ヨ
        query.setOnAction(e -> {
            pageNo[0] = 1;
            reload.run();
        });
        Button prevPage = new Button("\u4e0a\u4e00\u9875"); // 涓婁竴椤?
        prevPage.setOnAction(e -> {
            if (pageNo[0] <= 1) {
                out.appendText("\u63d0\u793a\uff1a\u5df2\u662f\u7b2c\u4e00\u9875\n"); // 鎻愮ず锛氬凡鏄涓€椤?
                return;
            }
            pageNo[0]--;
            reload.run();
        });
        Button nextPage = new Button("\u4e0b\u4e00\u9875"); // 涓嬩竴椤?
        nextPage.setOnAction(e -> {
            if (table.getItems() == null || table.getItems().size() < pageSize) {
                out.appendText("\u63d0\u793a\uff1a\u5df2\u662f\u6700\u540e\u4e00\u9875\n"); // 鎻愮ず锛氬凡鏄渶鍚庝竴椤?
                return;
            }
            pageNo[0]++;
            reload.run();
        });
        Button addUser = new Button("\u65b0\u589e\u7528\u6237"); // 鏂板鐢ㄦ埛
        addUser.setOnAction(e -> {
            try {
                User nu = new User();
                nu.setUsername(addUsername.getText() == null ? "" : addUsername.getText().trim());
                nu.setPassword(addPassword.getText() == null ? "" : addPassword.getText().trim());
                nu.setRealName(addRealName.getText() == null ? "" : addRealName.getText().trim());
                nu.setPhone(addPhone.getText() == null ? "" : addPhone.getText().trim());
                nu.setRole("\u8f66\u4f4d\u6240\u6709\u8005".equals(addRole.getValue()) ? "OWNER" : "CAR_OWNER"); // 杞︿綅鎵€鏈夎€?
                long uid = userAdminService.createUser(nu);
                out.appendText("\u65b0\u589e\u6210\u529f\uff0c\u7528\u6237ID\uff08\u81ea\u52a8\u751f\u6210\uff09=" + uid + "\n"); // 鏂板鎴愬姛锛岀敤鎴稩D锛堣嚜鍔ㄧ敓鎴愶級=
                addUsername.clear();
                addPassword.clear();
                addRealName.clear();
                addPhone.clear();
                addRole.setValue("\u8f66\u4e3b"); // 杞︿富
                pageNo[0] = 1;
                reload.run();
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });
        Button disable = new Button("\u7981\u7528"); // 绂佺敤
        disable.setOnAction(e -> run(() -> userAdminService.changeStatus(requireLong(userId, "\u7528\u6237ID"), 0), out, reload)); // 鐢ㄦ埛ID
        Button enable = new Button("\u542f\u7528"); // 鍚敤
        enable.setOnAction(e -> run(() -> userAdminService.changeStatus(requireLong(userId, "\u7528\u6237ID"), 1), out, reload)); // 鐢ㄦ埛ID
        Button reset = new Button("\u91cd\u7f6e\u5bc6\u7801"); // 閲嶇疆瀵嗙爜
        reset.setOnAction(e -> run(() -> userAdminService.resetPassword(requireLong(userId, "\u7528\u6237ID"), newPwd.getText().trim()), out, null)); // 鐢ㄦ埛ID
        Button deleteUser = new Button("\u5220\u9664\u7528\u6237"); // 鍒犻櫎鐢ㄦ埛
        deleteUser.setOnAction(e -> {
            try {
                out.clear();
                long uid = requireLong(userId, "\u7528\u6237ID"); // 鐢ㄦ埛ID
                if (uid == PROTECTED_ADMIN_ID) {
                    out.appendText("\u9519\u8bef\uff1a\u7ba1\u7406\u5458\u8d26\u53f7\u4e0d\u53ef\u5220\u9664\n"); // 閿欒锛氱鐞嗗憳璐﹀彿涓嶅彲鍒犻櫎\\n

                    return;
                }
                userAdminService.deleteUser(uid);
                out.appendText("\u5220\u9664\u6210\u529f\n"); // 鍒犻櫎鎴愬姛\\n

                reload.run();
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        HBox queryRow = new HBox(8, keyword, roleBox, statusBox, query);
        HBox.setHgrow(keyword, Priority.ALWAYS);
        HBox pageRow = new HBox(8, prevPage, nextPage, pageInfo);

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", queryRow, pageRow), // 鏌ヨ鍖?
                sectionBox("\u67e5\u8be2\u7ed3\u679c", table), // 鏌ヨ缁撴灉
                sectionBox("\u7ef4\u62a4\u533a\uff08\u65b0\u589e\uff1aID\u81ea\u52a8\u751f\u6210\uff1b\u7981\u7528/\u542f\u7528/\u91cd\u7f6e\u5bc6\u7801/\u5220\u9664\uff09", // 缁存姢鍖猴紙鏂板锛欼D鑷姩鐢熸垚锛涚鐢?鍚敤/閲嶇疆瀵嗙爜/鍒犻櫎锛?
                        new HBox(8,
                                new Label("\u7528\u6237\u540d"), addUsername, // 鐢ㄦ埛鍚?
                                new Label("\u5bc6\u7801"), addPassword, // 瀵嗙爜
                                new Label("\u771f\u5b9e\u59d3\u540d"), addRealName, // 鐪熷疄濮撳悕
                                new Label("\u624b\u673a\u53f7"), addPhone, // 鎵嬫満鍙?
                                new Label("\u89d2\u8272"), addRole, // 瑙掕壊
                                addUser),
                        new HBox(8, userId, newPwd),
                        new HBox(8, disable, enable, reset, deleteUser)),
                out);
        body.setPadding(new Insets(10));
        ScrollPane pageScroll = new ScrollPane(body);
        pageScroll.setFitToWidth(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        reload.run();
        Tab tab = new Tab("\u7528\u6237\u7ba1\u7406", pageScroll); // 鐢ㄦ埛绠＄悊
        tab.setClosable(false);
        return tab;
    }

    private Tab parkingLotTab() {
        TextArea out = new TextArea();
        TextField id = new TextField();
        TextField name = new TextField();
        TextField address = new TextField();
        TextField total = new TextField();
        TextField open = new TextField("08:00");
        TextField close = new TextField("22:00");
        TextField desc = new TextField();
        TextField keyword = new TextField();

        id.setPromptText("ID");
        name.setPromptText("\u505c\u8f66\u573a\u540d\u79f0"); // 鍋滆溅鍦哄悕绉?
        address.setPromptText("\u5730\u5740"); // 鍦板潃
        total.setPromptText("\u603b\u8f66\u4f4d\u6570"); // 鎬昏溅浣嶆暟
        open.setPromptText("\u5f00\u653e\u65f6\u95f4 HH:mm"); // 寮€鏀炬椂闂?HH:mm
        close.setPromptText("\u5173\u95ed\u65f6\u95f4 HH:mm"); // 鍏抽棴鏃堕棿 HH:mm
        desc.setPromptText("\u5907\u6ce8"); // 澶囨敞
        keyword.setPromptText("\u5173\u952e\u5b57\uff08\u7559\u7a7a=\u5168\u90e8\uff09"); // 鍏抽敭瀛楋紙鐣欑┖=鍏ㄩ儴锛?        keyword.setTooltip(new Tooltip("\u67e5\u8be2\u8bf4\u660e\uff1a1.\u8f93\u5165\u505c\u8f66\u573a\u540d\u79f0\u6216\u5730\u5740\u4efb\u4e00\u90e8\u5206\uff0c\u53ef\u6a21\u7cca\u67e5\u8be2\uff1b2.\u5173\u952e\u5b57\u7559\u7a7a\u540e\u70b9\u51fb\u201c\u67e5\u8be2\u201d\uff0c\u67e5\u8be2\u5168\u90e8\u505c\u8f66\u573a\u3002"));
        keyword.setPrefWidth(420);

        Button query = new Button("\u67e5\u8be2"); // 鏌ヨ
        query.setTooltip(new Tooltip("\u7559\u7a7a\u5173\u952e\u5b57\u5e76\u70b9\u51fb\u6b64\u6309\u94ae\uff0c\u53ef\u67e5\u8be2\u5168\u90e8\u505c\u8f66\u573a\u3002"));
        query.setOnAction(e -> {
            try {
                List<ParkingLot> rows = parkingLotService.queryLots(keyword.getText(), 1, 50);
                out.clear();
                for (ParkingLot lot : rows) {
                    out.appendText("\u505c\u8f66\u573aID=" + lot.getLotId() // 鍋滆溅鍦篒D=
                            + "\uff0c\u540d\u79f0=" + lot.getLotName() // 锛屽悕绉?
                            + "\uff0c\u5730\u5740=" + lot.getAddress() // 锛屽湴鍧€=
                            + "\uff0c\u603b\u8f66\u4f4d\u6570=" + lot.getTotalSpaces() // 锛屾€昏溅浣嶆暟=
                            + "\uff0c\u8425\u4e1a\u65f6\u95f4=" + lot.getOpenTime() + "~" + lot.getCloseTime() // 锛岃惀涓氭椂闂?
                            + "\uff0c\u5907\u6ce8=" + lot.getDescription() // 锛屽娉?
                            + "\n");
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });
        Label queryHint = new Label("\u8bf4\u660e\uff1a\u8f93\u5165\u5173\u952e\u5b57\u53ef\u6a21\u7cca\u67e5\u8be2\uff1b\u82e5\u9700\u67e5\u8be2\u5168\u90e8\uff0c\u8bf7\u5c06\u5173\u952e\u5b57\u7559\u7a7a\u540e\u70b9\u51fb\u201c\u67e5\u8be2\u201d\u3002"); // 璇存槑锛氳緭鍏ュ叧閿瓧鍙ā绯婃煡璇紱鑻ラ渶鏌ヨ鍏ㄩ儴锛岃灏嗗叧閿瓧鐣欑┖鍚庣偣鍑烩€滄煡璇⑩€濄€?        queryHint.setStyle("-fx-text-fill: #475569;");

        Button add = new Button("\u65b0\u589e"); // 鏂板
        add.setOnAction(e -> run(() -> parkingLotService.addLot(lotFrom(id, name, address, total, open, close, desc, false)), out, null));
        Button update = new Button("\u4fee\u6539"); // 淇敼
        update.setOnAction(e -> run(() -> parkingLotService.updateLot(lotFrom(id, name, address, total, open, close, desc, true)), out, null));
        Button delete = new Button("\u5220\u9664"); // 鍒犻櫎
        delete.setOnAction(e -> run(() -> parkingLotService.removeLot(requireLong(id, "ID")), out, null));

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", new HBox(8, keyword, query), queryHint), // 鏌ヨ鍖?                sectionBox("\u67e5\u8be2\u7ed3\u679c", out), // 鏌ヨ缁撴灉
                sectionBox("\u7ef4\u62a4\u533a\uff08\u65b0\u589e/\u4fee\u6539/\u5220\u9664\uff09", // 缁存姢鍖猴紙鏂板/淇敼/鍒犻櫎锛?                        new HBox(8, id, name, address, total),
                        new HBox(8, open, close, desc),
                        new HBox(8, add, update, delete)));
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u505c\u8f66\u573a\u7ba1\u7406", body); // 鍋滆溅鍦虹鐞?
        tab.setClosable(false);
        return tab;
    }

    private Tab parkingSpaceTab() {
        TextArea out = new TextArea();
        TextField id = new TextField();
        TextField lotId = new TextField("1");
        TextField ownerId = new TextField("2");
        TextField number = new TextField();
        TextField queryKeyword = new TextField();
        TextField queryOwnerId = new TextField();
        ComboBox<String> queryType = new ComboBox<>(FXCollections.observableArrayList("\u5168\u90e8\u7c7b\u578b", "\u5730\u4e0a", "\u5730\u4e0b")); // 鍏ㄩ儴绫诲瀷 | 鍦颁笂 | 鍦颁笅
        queryType.setValue("\u5168\u90e8\u7c7b\u578b"); // 鍏ㄩ儴绫诲瀷
        CheckBox showAll = new CheckBox("\u663e\u793a\u5168\u90e8\u8f66\u4f4d\uff08\u542b\u5df2\u9884\u7ea6/\u5360\u7528\uff09"); // 鏄剧ず鍏ㄩ儴杞︿綅锛堝惈宸查绾?鍗犵敤锛?
        ComboBox<String> type = new ComboBox<>(FXCollections.observableArrayList("\u5730\u4e0a", "\u5730\u4e0b")); // 鍦颁笂 | 鍦颁笅
        type.setValue("\u5730\u4e0a"); // 鍦颁笂
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("\u7a7a\u95f2", "\u5df2\u9884\u7ea6", "\u5360\u7528")); // 绌洪棽 | 宸查绾?| 鍗犵敤
        status.setValue("\u7a7a\u95f2"); // 绌洪棽
        TextField start = new TextField("08:00");
        TextField end = new TextField("22:00");

        id.setPromptText("\u8f66\u4f4dID"); // 杞︿綅ID
        lotId.setPromptText("\u505c\u8f66\u573aID"); // 鍋滆溅鍦篒D
        ownerId.setPromptText("\u6240\u6709\u8005ID"); // 鎵€鏈夎€匢D
        number.setPromptText("\u8f66\u4f4d\u7f16\u53f7\u5173\u952e\u5b57\uff08\u652f\u6301\u90e8\u5206\u5339\u914d\uff09"); // 杞︿綅缂栧彿鍏抽敭瀛楋紙鏀寔閮ㄥ垎鍖归厤锛?
        queryKeyword.setPromptText("\u67e5\u8be2\u5173\u952e\u5b57\uff08\u8f66\u4f4d\u7f16\u53f7\uff09"); // 鏌ヨ鍏抽敭瀛楋紙杞︿綅缂栧彿锛?
        queryOwnerId.setPromptText("\u67e5\u8be2\u62e5\u6709\u8005ID\uff08\u53ef\u9009\uff0c\u53ef\u67e5\u8be2\u8be5\u8f66\u4e3b\u6240\u6709\u8f66\u4f4d\uff09"); // 鏌ヨ鎷ユ湁鑰匢D锛堝彲閫夛紝鍙煡璇㈣杞︿富鎵€鏈夎溅浣嶏級
        start.setPromptText("\u67e5\u8be2\u65f6\u6bb5\u5f00\u59cb HH:mm\uff08\u7a7a\u95f2\u67e5\u8be2\u7528\uff09"); // 鏌ヨ鏃舵寮€濮?HH:mm锛堢┖闂叉煡璇㈢敤锛?
        end.setPromptText("\u67e5\u8be2\u65f6\u6bb5\u7ed3\u675f HH:mm\uff08\u7a7a\u95f2\u67e5\u8be2\u7528\uff09"); // 鏌ヨ鏃舵缁撴潫 HH:mm锛堢┖闂叉煡璇㈢敤锛?

        Button query = new Button("\u67e5\u8be2"); // 鏌ヨ
        query.setOnAction(e -> {
            try {
                String keyword = queryKeyword.getText();
                boolean allType = "\u5168\u90e8\u7c7b\u578b".equals(queryType.getValue()); // 鍏ㄩ儴绫诲瀷
                String typeCode = allType ? "" : spaceTypeCode(queryType.getValue());
                String statusCode = spaceStatusCode(status.getValue());
                Long ownerFilter = null;
                if (queryOwnerId.getText() != null && !queryOwnerId.getText().isBlank()) {
                    ownerFilter = requireLong(queryOwnerId, "\u62e5\u6709\u8005ID"); // 鎷ユ湁鑰匢D
                }

                List<ParkingSpace> rows;
                if (showAll.isSelected()) {
                    rows = parkingSpaceService.querySpaces("", "", null, 1, 500);
                } else if ("FREE".equalsIgnoreCase(statusCode)) {
                    LocalTime st = requireTime(start, "\u67e5\u8be2\u5f00\u59cb\u65f6\u95f4"); // 鏌ヨ寮€濮嬫椂闂?
                    LocalTime et = requireTime(end, "\u67e5\u8be2\u7ed3\u675f\u65f6\u95f4"); // 鏌ヨ缁撴潫鏃堕棿
                    LocalDateTime queryStart = LocalDateTime.now().withHour(st.getHour()).withMinute(st.getMinute()).withSecond(0).withNano(0);
                    LocalDateTime queryEnd = LocalDateTime.now().withHour(et.getHour()).withMinute(et.getMinute()).withSecond(0).withNano(0);
                    if (!queryStart.isBefore(queryEnd)) {
                        throw new IllegalArgumentException("\u67e5\u8be2\u7ed3\u675f\u65f6\u95f4\u5fc5\u987b\u665a\u4e8e\u5f00\u59cb\u65f6\u95f4"); // 鏌ヨ缁撴潫鏃堕棿蹇呴』鏅氫簬寮€濮嬫椂闂?
                    }
                    rows = parkingSpaceService.queryAvailableSpaces(queryStart, queryEnd, 1, 500);
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
                            ? "\u65f6\u6bb5\u5185\u53ef\u7528" // 鏃舵鍐呭彲鐢?
                            : spaceStatusLabel(s.getStatus());
                    out.appendText("\u8f66\u4f4dID=" + s.getSpaceId() // 杞︿綅ID=
                            + "\uff0c\u8f66\u4f4d\u7f16\u53f7=" + s.getSpaceNumber() // 锛岃溅浣嶇紪鍙?
                            + "\uff0c\u505c\u8f66\u573aID=" + s.getLotId() // 锛屽仠杞﹀満ID=
                            + "\uff0c\u6240\u6709\u8005ID=" + s.getOwnerId() // 锛屾墍鏈夎€匢D=
                            + "\uff0c\u7c7b\u578b=" + spaceTypeLabel(s.getType()) // 锛岀被鍨?
                            + "\uff0c\u72b6\u6001=" + displayStatus // 锛岀姸鎬?
                            + "\uff0c\u5171\u4eab\u65f6\u95f4=" + s.getShareStartTime() + "~" + s.getShareEndTime() // 锛屽叡浜椂闂?
                            + "\n");
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button add = new Button("\u65b0\u589e"); // 鏂板
        add.setOnAction(e -> run(() -> parkingSpaceService.addSpace(spaceFrom(id, lotId, ownerId, number, type, status, start, end, false)), out, null));
        Button update = new Button("\u4fee\u6539"); // 淇敼
        update.setOnAction(e -> run(() -> parkingSpaceService.updateSpace(spaceFrom(id, lotId, ownerId, number, type, status, start, end, true)), out, null));
        Button delete = new Button("\u5220\u9664"); // 鍒犻櫎
        delete.setOnAction(e -> run(() -> parkingSpaceService.removeSpace(requireLong(id, "ID")), out, null));

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", // 鏌ヨ鍖?
                        new HBox(8, queryKeyword, queryOwnerId, queryType, status, showAll, query),
                        new HBox(8, start, end)),
                sectionBox("\u67e5\u8be2\u7ed3\u679c", out), // 鏌ヨ缁撴灉
                sectionBox("\u7ef4\u62a4\u533a\uff08\u65b0\u589e/\u4fee\u6539/\u5220\u9664\uff09", // 缁存姢鍖猴紙鏂板/淇敼/鍒犻櫎锛?
                        new HBox(8, id, lotId, ownerId, number, type, status),
                        new HBox(8, start, end, add, update, delete)));
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u8f66\u4f4d\u7ba1\u7406", body); // 杞︿綅绠＄悊
        tab.setClosable(false);
        return tab;
    }

    private Tab pricingRuleTab() {
        TextField id = new TextField();
        TextField queryKeyword = new TextField();
        TextField name = new TextField();
        ComboBox<String> chargeType = new ComboBox<>(FXCollections.observableArrayList("\u6309\u65f6\u8ba1\u8d39", "\u6309\u6b21\u8ba1\u8d39")); // 鎸夋椂璁¤垂 | 鎸夋璁¤垂
        chargeType.setValue("\u6309\u65f6\u8ba1\u8d39"); // 鎸夋椂璁¤垂
        TextField unitPrice = new TextField();
        TextField unitTime = new TextField();
        TextField fixedPrice = new TextField();
        ComboBox<String> spaceType = new ComboBox<>(FXCollections.observableArrayList("\u5730\u4e0a", "\u5730\u4e0b")); // 鍦颁笂 | 鍦颁笅
        spaceType.setValue("\u5730\u4e0a"); // 鍦颁笂
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("\u542f\u7528", "\u7981\u7528")); // 鍚敤 | 绂佺敤
        status.setValue("\u542f\u7528"); // 鍚敤

        chargeType.setPrefWidth(130);
        spaceType.setPrefWidth(110);
        status.setPrefWidth(110);
        id.setPrefWidth(80);
        queryKeyword.setPrefWidth(260);
        name.setPrefWidth(220);
        unitPrice.setPrefWidth(140);
        unitTime.setPrefWidth(140);
        fixedPrice.setPrefWidth(140);

        id.setPromptText("\u89c4\u5219ID"); // 瑙勫垯ID
        queryKeyword.setPromptText("\u89c4\u5219ID/\u540d\u79f0\uff08\u7559\u7a7a=\u5168\u90e8\uff09"); // 瑙勫垯ID/鍚嶇О锛堢暀绌?鍏ㄩ儴锛?        queryKeyword.setTooltip(new Tooltip("\u67e5\u8be2\u8bf4\u660e\uff1a1.\u8f93\u5165\u89c4\u5219ID\u53ef\u6309ID\u67e5\u8be2\uff1b2.\u8f93\u5165\u540d\u79f0\u5173\u952e\u5b57\u53ef\u6a21\u7cca\u67e5\u8be2\uff1b3.\u7559\u7a7a\u67e5\u8be2\u5168\u90e8\u89c4\u5219\u3002"));
        name.setPromptText("\u89c4\u5219\u540d\u79f0\uff08\u4f8b\u5982\uff1a\u5730\u4e0a\u6309\u65f6\u8ba1\u8d39\uff09"); // 瑙勫垯鍚嶇О锛堜緥濡傦細鍦颁笂鎸夋椂璁¤垂锛?        unitPrice.setPromptText("\u5355\u4ef7\uff08\u5143\uff09"); // 鍗曚环锛堝厓锛?        unitTime.setPromptText("\u5355\u4f4d\u65f6\u957f\uff08\u5206\u949f\uff09"); // 鍗曚綅鏃堕暱锛堝垎閽燂級
        fixedPrice.setPromptText("\u56fa\u5b9a\u4ef7\u683c\uff08\u5143\uff09"); // 鍥哄畾浠锋牸锛堝厓锛?
        TextArea out = new TextArea();

        Button query = new Button("\u67e5\u8be2"); // 鏌ヨ
        query.setOnAction(e -> {
            try {
                // 鏌ヨ鏀寔涓ょ鏂瑰紡锛氳緭鍏ユ暟瀛楁寜瑙勫垯ID鏌ヨ锛涜緭鍏ユ枃鏈寜瑙勫垯鍚嶇О妯＄硦鏌ヨ锛涚暀绌哄垯鏌ヨ鍏ㄩ儴銆?                List<PricingRule> rows = pricingRuleService.queryRules(queryKeyword.getText(), "", null, 1, 30);
                out.clear();
                for (PricingRule r : rows) {
                    out.appendText("\u89c4\u5219ID=" + r.getRuleId() // 瑙勫垯ID=
                            + "\uff0c\u89c4\u5219\u540d\u79f0=" + r.getRuleName() // 锛岃鍒欏悕绉?
                            + "\uff0c\u8ba1\u8d39\u65b9\u5f0f=" + chargeTypeLabel(r.getChargeType()) // 锛岃璐规柟寮?
                            + "\uff0c\u5355\u4ef7=" + r.getUnitPrice() // 锛屽崟浠?
                            + "\uff0c\u5355\u4f4d\u65f6\u957f(\u5206\u949f)=" + r.getUnitTime() // 锛屽崟浣嶆椂闀?鍒嗛挓)=
                            + "\uff0c\u56fa\u5b9a\u4ef7=" + r.getFixedPrice() // 锛屽浐瀹氫环=
                            + "\uff0c\u9002\u7528\u8f66\u4f4d=" + spaceTypeLabel(r.getApplicableSpaceType()) // 锛岄€傜敤杞︿綅=
                            + "\uff0c\u72b6\u6001=" + enabledLabel(r.getStatus()) // 锛岀姸鎬?
                            + "\n");
                }
            } catch (Exception ex) {
                out.appendText(formatError(ex) + "\n");
            }
        });

        Button add = new Button("\u65b0\u589e"); // 鏂板
        add.setOnAction(e -> run(() -> pricingRuleService.addRule(ruleFrom(id, name, chargeType, unitPrice, unitTime, fixedPrice, spaceType, status, false)), out, null));
        Button update = new Button("\u4fee\u6539"); // 淇敼
        update.setOnAction(e -> run(() -> pricingRuleService.updateRule(ruleFrom(id, name, chargeType, unitPrice, unitTime, fixedPrice, spaceType, status, true)), out, null));
        Button delete = new Button("\u5220\u9664"); // 鍒犻櫎
        delete.setOnAction(e -> run(() -> pricingRuleService.removeRule(requireLong(id, "ID")), out, null));

        Label pricingHelp = new Label("\u8bf4\u660e\uff1a\u6309\u65f6\u8ba1\u8d39\u8bf7\u586b\u5199\u201c\u5355\u4ef7+\u5355\u4f4d\u65f6\u957f\u201d\uff1b\u6309\u6b21\u8ba1\u8d39\u8bf7\u586b\u5199\u201c\u56fa\u5b9a\u4ef7\u683c\u201d\u3002"); // 璇存槑锛氭寜鏃惰璐硅濉啓鈥滃崟浠?鍗曚綅鏃堕暱鈥濓紱鎸夋璁¤垂璇峰～鍐欌€滃浐瀹氫环鏍尖€濄€?        pricingHelp.setWrapText(true);
        Label pricingQueryHint = new Label("\u67e5\u8be2\u8bf4\u660e\uff1a\u8f93\u5165\u89c4\u5219ID\u53ef\u6309ID\u67e5\u8be2\uff1b\u8f93\u5165\u540d\u79f0\u5173\u952e\u5b57\u53ef\u6a21\u7cca\u67e5\u8be2\uff1b\u7559\u7a7a\u67e5\u8be2\u5168\u90e8\u3002"); // 鏌ヨ璇存槑锛氳緭鍏ヨ鍒橧D鍙寜ID鏌ヨ锛涜緭鍏ュ悕绉板叧閿瓧鍙ā绯婃煡璇紱鐣欑┖鏌ヨ鍏ㄩ儴銆?        pricingQueryHint.setStyle("-fx-text-fill: #475569;");

        VBox body = new VBox(10,
                sectionBox("\u67e5\u8be2\u533a", new HBox(8, queryKeyword, query), pricingQueryHint), // 鏌ヨ鍖?                sectionBox("\u67e5\u8be2\u7ed3\u679c", out), // 鏌ヨ缁撴灉
                sectionBox("\u7ef4\u62a4\u533a\uff08\u65b0\u589e/\u4fee\u6539/\u5220\u9664\uff09", // 缁存姢鍖猴紙鏂板/淇敼/鍒犻櫎锛?                        pricingHelp,
                        new HBox(8, id, name, chargeType, unitPrice, unitTime, fixedPrice, spaceType, status),
                        new HBox(8, add, update, delete)));
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u8ba1\u8d39\u89c4\u5219", body); // 璁¤垂瑙勫垯
        tab.setClosable(false);
        return tab;
    }
    private Tab settlementTab() {
        TextField revenueId = new TextField();
        revenueId.setPromptText("\u7ed3\u7b97ID"); // 缁撶畻ID
        ComboBox<String> status = new ComboBox<>(FXCollections.observableArrayList("\u5168\u90e8", "\u672a\u7ed3\u7b97", "\u5df2\u7ed3\u7b97")); // 鍏ㄩ儴 | 鏈粨绠?| 宸茬粨绠?        status.setValue("\u5168\u90e8"); // 鍏ㄩ儴
        TextArea out = new TextArea();

        Button query = new Button("\u67e5\u8be2\u7ed3\u7b97\u8bb0\u5f55"); // 鏌ヨ缁撶畻璁板綍
        query.setOnAction(e -> {
            try {
                List<Map<String, Object>> rows = revenueService.queryRevenueForAdmin(settleStatusCode(status.getValue()), 1, 50);
                out.clear();
                for (Map<String, Object> row : rows) out.appendText(formatRowMap(row) + "\n");
            } catch (Exception ex) { out.appendText(formatError(ex) + "\n"); }
        });
        Button approve = new Button("\u5ba1\u6838\u7ed3\u7b97"); // 瀹℃牳缁撶畻
        approve.setOnAction(e -> run(() -> revenueService.settleRevenue(requireLong(revenueId, "\u7ed3\u7b97ID")), out, null)); // 缁撶畻ID

        VBox body = new VBox(8, new HBox(8, status, query), new HBox(8, revenueId, approve), out);
        body.setPadding(new Insets(10));
        Tab tab = new Tab("\u7ed3\u7b97\u5ba1\u6838", body); // 缁撶畻瀹℃牳
        tab.setClosable(false);
        return tab;
    }

    private Tab reportTab() {
        TextArea out = new TextArea();
        Button income = new Button("\u6536\u5165\u6392\u884c"); // 鏀跺叆鎺掕
        income.setOnAction(e -> loadReport(out, "income"));
        Button reserve = new Button("\u9884\u7ea6\u6392\u884c"); // 棰勭害鎺掕
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
        Tab tab = new Tab("\u7edf\u8ba1\u62a5\u8868", body); // 缁熻鎶ヨ〃
        tab.setClosable(false);
        return tab;
    }


    private long requireLong(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 涓嶈兘涓虹┖
        }
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + "\u5fc5\u987b\u662f\u6570\u5b57"); // 蹇呴』鏄暟瀛?
        }
    }

    private int requireInt(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 涓嶈兘涓虹┖
        }
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException(name + "\u5fc5\u987b\u662f\u6570\u5b57"); // 蹇呴』鏄暟瀛?
        }
    }

    private LocalDateTime requireDateTime(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 涓嶈兘涓虹┖
        }
        try { return LocalDateTime.parse(v); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(v, DT_SPACE_FMT); } catch (DateTimeParseException ignored) {}
        try { return LocalDateTime.parse(v, DT_SLASH_FMT); } catch (DateTimeParseException ignored) {}
        try { return LocalDate.now().atTime(LocalTime.parse(v)); } catch (DateTimeParseException ignored) {}
        throw new IllegalArgumentException(name + "\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u4f7f\u7528 yyyy-MM-dd HH:mm \u6216 HH:mm"); // 鏍煎紡閿欒锛岃浣跨敤 yyyy-MM-dd HH:mm 鎴?HH:mm
    }

    private LocalTime requireTime(TextField field, String name) {
        String v = field.getText() == null ? "" : field.getText().trim();
        if (v.isEmpty()) {
            throw new IllegalArgumentException(name + "\u4e0d\u80fd\u4e3a\u7a7a"); // 涓嶈兘涓虹┖
        }
        try {
            return LocalTime.parse(v);
        } catch (java.time.format.DateTimeParseException ex) {
            throw new IllegalArgumentException(name + "\u683c\u5f0f\u9519\u8bef\uff0c\u8bf7\u4f7f\u7528 HH:mm"); // 鏍煎紡閿欒锛岃浣跨敤 HH:mm
        }
    }

    private String formatError(Exception ex) {
        if (ex instanceof IllegalArgumentException) {
            return "\u9519\u8bef\uff1a" + ex.getMessage(); // 閿欒锛?
        }
        if (ex instanceof NumberFormatException) {
            return "\u9519\u8bef\uff1a\u8f93\u5165\u683c\u5f0f\u4e0d\u6b63\u786e\uff0cID\u5fc5\u987b\u662f\u6570\u5b57"; // 閿欒锛氳緭鍏ユ牸寮忎笉姝ｇ‘锛孖D蹇呴』鏄暟瀛?
        }
        if (ex instanceof java.time.format.DateTimeParseException) {
            return "\u9519\u8bef\uff1a\u65f6\u95f4\u683c\u5f0f\u4e0d\u6b63\u786e\uff0c\u8bf7\u4f7f\u7528 yyyy-MM-ddTHH:mm \u6216 HH:mm"; // 閿欒锛氭椂闂存牸寮忎笉姝ｇ‘锛岃浣跨敤 yyyy-MM-ddTHH:mm 鎴?HH:mm
        }
        String msg = ex.getMessage();
        String display = (msg == null || msg.isBlank()) ? ex.getClass().getSimpleName() : toChineseMessage(msg);
        return "\u9519\u8bef\uff1a" + display; // 閿欒锛?
    }

    private String toChineseMessage(String msg) {

        if (msg == null || msg.isBlank()) return "\u53d1\u751f\u672a\u77e5\u9519\u8bef"; // 鍙戠敓鏈煡閿欒
        String m = msg.trim();
        String lower = m.toLowerCase();

        if (lower.contains("cannot delete or update a parent row") || lower.contains("foreign key constraint fails")) {
            return "\u5b58\u5728\u5173\u8054\u4e1a\u52a1\u8bb0\u5f55\uff0c\u4e0d\u80fd\u5220\u9664\u6216\u4fee\u6539\uff0c\u8bf7\u5148\u5904\u7406\u5173\u8054\u6570\u636e"; // 瀛樺湪鍏宠仈涓氬姟璁板綍锛屼笉鑳藉垹闄ゆ垨淇敼锛岃鍏堝鐞嗗叧鑱旀暟鎹?
        }



        if ("Invalid record or already completed".equalsIgnoreCase(m)) return "\u505c\u8f66\u8bb0\u5f55\u65e0\u6548\u6216\u5df2\u7ecf\u5b8c\u6210\u51fa\u573a"; // 鍋滆溅璁板綍鏃犳晥鎴栧凡缁忓畬鎴愬嚭鍦?
        if ("Parking space not found".equalsIgnoreCase(m)) return "\u672a\u627e\u5230\u5bf9\u5e94\u8f66\u4f4d"; // 鏈壘鍒板搴旇溅浣?
        if ("Failed to complete parking exit".equalsIgnoreCase(m)) return "\u505c\u8f66\u51fa\u573a\u5904\u7406\u5931\u8d25"; // 鍋滆溅鍑哄満澶勭悊澶辫触
        if ("Invalid reservation time range".equalsIgnoreCase(m)) return "\u9884\u7ea6\u65f6\u95f4\u533a\u95f4\u65e0\u6548"; // 棰勭害鏃堕棿鍖洪棿鏃犳晥
        if ("Invalid share time range".equalsIgnoreCase(m)) return "\u5171\u4eab\u65f6\u95f4\u533a\u95f4\u65e0\u6548"; // 鍏变韩鏃堕棿鍖洪棿鏃犳晥
        if ("Reservation failed: time conflict detected".equalsIgnoreCase(m)) return "\u9884\u7ea6\u5931\u8d25\uff1a\u65f6\u95f4\u6bb5\u51b2\u7a81"; // 棰勭害澶辫触锛氭椂闂存鍐茬獊
        if ("Cancel failed: reservation not found or cannot be canceled".equalsIgnoreCase(m)) return "\u53d6\u6d88\u5931\u8d25\uff1a\u672a\u627e\u5230\u53ef\u53d6\u6d88\u7684\u9884\u7ea6"; // 鍙栨秷澶辫触锛氭湭鎵惧埌鍙彇娑堢殑棰勭害


        if ("userId is required".equalsIgnoreCase(m)) return "\u7528\u6237ID\u4e0d\u80fd\u4e3a\u7a7a"; // 鐢ㄦ埛ID涓嶈兘涓虹┖
        if ("ownerId is required".equalsIgnoreCase(m)) return "\u6240\u6709\u8005ID\u4e0d\u80fd\u4e3a\u7a7a"; // 鎵€鏈夎€匢D涓嶈兘涓虹┖
        if ("spaceId and ownerId are required".equalsIgnoreCase(m)) return "\u8f66\u4f4dID\u548c\u6240\u6709\u8005ID\u4e0d\u80fd\u4e3a\u7a7a"; // 杞︿綅ID鍜屾墍鏈夎€匢D涓嶈兘涓虹┖
        if ("userId and spaceId are required".equalsIgnoreCase(m)) return "\u7528\u6237ID\u548c\u8f66\u4f4dID\u4e0d\u80fd\u4e3a\u7a7a"; // 鐢ㄦ埛ID鍜岃溅浣岻D涓嶈兘涓虹┖
        if ("Space id is required".equalsIgnoreCase(m) || "spaceId is required".equalsIgnoreCase(m)) return "\u8f66\u4f4dID\u4e0d\u80fd\u4e3a\u7a7a"; // 杞︿綅ID涓嶈兘涓虹┖
        if ("Lot id is required".equalsIgnoreCase(m) || "lotId is required".equalsIgnoreCase(m)) return "\u505c\u8f66\u573aID\u4e0d\u80fd\u4e3a\u7a7a"; // 鍋滆溅鍦篒D涓嶈兘涓虹┖
        if ("Space not found or no permission to update".equalsIgnoreCase(m)) return "\u672a\u627e\u5230\u8f66\u4f4d\u6216\u65e0\u6743\u4fee\u6539"; // 鏈壘鍒拌溅浣嶆垨鏃犳潈淇敼
        if ("Parking space not found".equalsIgnoreCase(m) || "Parking space is required".equalsIgnoreCase(m)) return "\u8f66\u4f4d\u4fe1\u606f\u9519\u8bef\u6216\u4e0d\u5b58\u5728"; // 杞︿綅淇℃伅閿欒鎴栦笉瀛樺湪


        if ("Username and password are required".equalsIgnoreCase(m)) return "\u7528\u6237\u540d\u548c\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"; // 鐢ㄦ埛鍚嶅拰瀵嗙爜涓嶈兘涓虹┖
        if ("User does not exist or is disabled".equalsIgnoreCase(m)) return "\u7528\u6237\u4e0d\u5b58\u5728\u6216\u5df2\u88ab\u7981\u7528"; // 鐢ㄦ埛涓嶅瓨鍦ㄦ垨宸茶绂佺敤
        if ("Wrong password".equalsIgnoreCase(m)) return "\u5bc6\u7801\u9519\u8bef"; // 瀵嗙爜閿欒
        if ("Username is required".equalsIgnoreCase(m)) return "\u7528\u6237\u540d\u4e0d\u80fd\u4e3a\u7a7a"; // 鐢ㄦ埛鍚嶄笉鑳戒负绌?
        if ("Password is required".equalsIgnoreCase(m)) return "\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"; // 瀵嗙爜涓嶈兘涓虹┖
        if ("Role is required".equalsIgnoreCase(m)) return "\u89d2\u8272\u4e0d\u80fd\u4e3a\u7a7a"; // 瑙掕壊涓嶈兘涓虹┖
        if ("Username already exists".equalsIgnoreCase(m)) return "\u7528\u6237\u540d\u5df2\u5b58\u5728"; // 鐢ㄦ埛鍚嶅凡瀛樺湪
        if ("User not found".equalsIgnoreCase(m) || "User not found".equalsIgnoreCase(m)) return "\u7528\u6237\u4e0d\u5b58\u5728"; // 鐢ㄦ埛涓嶅瓨鍦?
        if ("userId and newPassword are required".equalsIgnoreCase(m)) return "\u7528\u6237ID\u548c\u65b0\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"; // 鐢ㄦ埛ID鍜屾柊瀵嗙爜涓嶈兘涓虹┖
        if ("userId and status are required".equalsIgnoreCase(m)) return "\u7528\u6237ID\u548c\u72b6\u6001\u4e0d\u80fd\u4e3a\u7a7a"; // 鐢ㄦ埛ID鍜岀姸鎬佷笉鑳戒负绌?
        if ("status must be 0 or 1".equalsIgnoreCase(m)) return "\u72b6\u6001\u53ea\u80fd\u4e3a0\u62161"; // 鐘舵€佸彧鑳戒负0鎴?


        if ("Rule not found".equalsIgnoreCase(m)) return "\u8ba1\u8d39\u89c4\u5219\u4e0d\u5b58\u5728"; // 璁¤垂瑙勫垯涓嶅瓨鍦?
        if ("ruleId is required".equalsIgnoreCase(m)) return "\u89c4\u5219ID\u4e0d\u80fd\u4e3a\u7a7a"; // 瑙勫垯ID涓嶈兘涓虹┖
        if ("ruleName is required".equalsIgnoreCase(m)) return "\u89c4\u5219\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a"; // 瑙勫垯鍚嶇О涓嶈兘涓虹┖
        if ("chargeType is required".equalsIgnoreCase(m)) return "\u8ba1\u8d39\u65b9\u5f0f\u4e0d\u80fd\u4e3a\u7a7a"; // 璁¤垂鏂瑰紡涓嶈兘涓虹┖
        if ("Hourly rule requires unitPrice and unitTime".equalsIgnoreCase(m)) return "\u6309\u65f6\u8ba1\u8d39\u9700\u586b\u5199\u5355\u4ef7\u4e0e\u8ba1\u8d39\u65f6\u957f"; // 鎸夋椂璁¤垂闇€濉啓鍗曚环涓庤璐规椂闀?
        if ("Fixed rule requires fixedPrice".equalsIgnoreCase(m)) return "\u6309\u6b21\u8ba1\u8d39\u9700\u586b\u5199\u56fa\u5b9a\u4ef7"; // 鎸夋璁¤垂闇€濉啓鍥哄畾浠?
        if ("chargeType must be HOURLY or FIXED".equalsIgnoreCase(m)) return "\u8ba1\u8d39\u65b9\u5f0f\u53ea\u80fd\u4e3a HOURLY \u6216 FIXED"; // 璁¤垂鏂瑰紡鍙兘涓?HOURLY 鎴?FIXED
        if ("applicableSpaceType is required".equalsIgnoreCase(m)) return "\u9002\u7528\u8f66\u4f4d\u7c7b\u578b\u4e0d\u80fd\u4e3a\u7a7a"; // 閫傜敤杞︿綅绫诲瀷涓嶈兘涓虹┖
        if ("revenueId is required".equalsIgnoreCase(m)) return "\u7ed3\u7b97ID\u4e0d\u80fd\u4e3a\u7a7a"; // 缁撶畻ID涓嶈兘涓虹┖
        if ("Revenue not found or already settled".equalsIgnoreCase(m)) return "\u7ed3\u7b97\u8bb0\u5f55\u4e0d\u5b58\u5728\u6216\u5df2\u7ed3\u7b97"; // 缁撶畻璁板綍涓嶅瓨鍦ㄦ垨宸茬粨绠?


        if ("Insert parking lot failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u505c\u8f66\u573a\u5931\u8d25"; // 鏂板鍋滆溅鍦哄け璐?
        if ("Insert parking space failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u8f66\u4f4d\u5931\u8d25"; // 鏂板杞︿綅澶辫触
        if ("Insert parking entry failed".equalsIgnoreCase(m)) return "\u751f\u6210\u505c\u8f66\u5165\u573a\u8bb0\u5f55\u5931\u8d25"; // 鐢熸垚鍋滆溅鍏ュ満璁板綍澶辫触
        if ("Insert payment record failed".equalsIgnoreCase(m)) return "\u751f\u6210\u652f\u4ed8\u8bb0\u5f55\u5931\u8d25"; // 鐢熸垚鏀粯璁板綍澶辫触
        if ("Insert pricing rule failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u8ba1\u8d39\u89c4\u5219\u5931\u8d25"; // 鏂板璁¤垂瑙勫垯澶辫触
        if ("Insert revenue record failed".equalsIgnoreCase(m)) return "\u751f\u6210\u6536\u76ca\u8bb0\u5f55\u5931\u8d25"; // 鐢熸垚鏀剁泭璁板綍澶辫触
        if ("Insert user failed".equalsIgnoreCase(m)) return "\u65b0\u589e\u7528\u6237\u5931\u8d25"; // 鏂板鐢ㄦ埛澶辫触

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
                out.appendText("\u6682\u65e0\u5165\u573a\u8bb0\u5f55\n"); // 暂无入场记录
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
                .replace("UNSETTLED", "\u672a\u7ed3\u7b97") // 鏈粨绠?
                .replace("SETTLED", "\u5df2\u7ed3\u7b97") // 宸茬粨绠?
                .replace("UNPAID", "\u672a\u652f\u4ed8") // 鏈敮浠?
                .replace("PAID", "\u5df2\u652f\u4ed8") // 宸叉敮浠?
                .replace("PENDING", "\u5f85\u4f7f\u7528") // 寰呬娇鐢?
                .replace("ACTIVE", "\u4f7f\u7528\u4e2d") // 浣跨敤涓?
                .replace("CANCELED", "\u5df2\u53d6\u6d88") // 宸插彇娑?
                .replace("COMPLETED", "\u5df2\u5b8c\u6210") // 宸插畬鎴?
                .replace("GROUND", "\u5730\u4e0a") // 鍦颁笂
                .replace("UNDERGROUND", "\u5730\u4e0b") // 鍦颁笅
                .replace("FREE", "\u7a7a\u95f2") // 绌洪棽
                .replace("RESERVED", "\u5df2\u9884\u7ea6") // 宸查绾?
                .replace("OCCUPIED", "\u5360\u7528"); // 鍗犵敤
    }

    private String formatRowMap(Map<String, Object> row) {
        if (row == null || row.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Map.Entry<String, Object> e : row.entrySet()) {
            if (!first) sb.append("\uff0c "); // 锛?
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
            case "owner_id": return "\u6536\u76ca\u4ebaID"; // 鏀剁泭浜篒D
            case "payer_user_id": return "\u652f\u4ed8\u4ebaID"; // 鏀粯浜篒D
            case "payment_id": return "\u652f\u4ed8\u8bb0\u5f55ID"; // 鏀粯璁板綍ID
            case "revenue_id": return "\u6536\u76ca\u8bb0\u5f55ID"; // 鏀剁泭璁板綍ID
            case "settle_status": return "\u7ed3\u7b97\u72b6\u6001"; // 缁撶畻鐘舵€?
            case "space_id": return "\u8f66\u4f4dID"; // 杞︿綅ID
            case "settle_time": return "\u7ed3\u7b97\u65f6\u95f4"; // 缁撶畻鏃堕棿
            case "income_amount": return "\u6536\u76ca\u91d1\u989d"; // 鏀剁泭閲戦
            case "payment_status": return "\u652f\u4ed8\u72b6\u6001"; // 鏀粯鐘舵€?
            case "payment_method": return "\u652f\u4ed8\u65b9\u5f0f"; // 鏀粯鏂瑰紡
            case "payment_time": return "\u652f\u4ed8\u65f6\u95f4"; // 鏀粯鏃堕棿
            case "amount": return "\u91d1\u989d"; // 閲戦
            case "record_id": return "\u505c\u8f66\u8bb0\u5f55ID"; // 鍋滆溅璁板綍ID
            case "lot_id": return "\u505c\u8f66\u573aID"; // 鍋滆溅鍦篒D
            case "lot_name": return "\u505c\u8f66\u573a\u540d\u79f0"; // 鍋滆溅鍦哄悕绉?
            case "type": return "\u7c7b\u578b"; // 绫诲瀷
            case "status": return "\u72b6\u6001"; // 鐘舵€?
            case "space_number": return "\u8f66\u4f4d\u7f16\u53f7"; // 杞︿綅缂栧彿
            case "hour": return "\u5c0f\u65f6"; // 灏忔椂
            case "hour_slot": return "\u65f6\u6bb5"; // 鏃舵
            case "usage_count": return "\u4f7f\u7528\u6b21\u6570"; // 浣跨敤娆℃暟
            case "reservation_count": return "\u9884\u7ea6\u6b21\u6570"; // 棰勭害娆℃暟
            case "reserve_count": return "\u9884\u7ea6\u6b21\u6570"; // 棰勭害娆℃暟
            case "total_income": return "\u603b\u6536\u5165"; // 鎬绘敹鍏?
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
        lot.setTotalSpaces(requireInt(total, "\u603b\u8f66\u4f4d\u6570")); // 鎬昏溅浣嶆暟
        lot.setOpenTime(requireTime(open, "\u5f00\u59cb\u65f6\u95f4")); // 寮€濮嬫椂闂?
        lot.setCloseTime(requireTime(close, "\u7ed3\u675f\u65f6\u95f4")); // 缁撴潫鏃堕棿
        lot.setDescription(desc.getText() == null ? "" : desc.getText().trim());
        return lot;
    }

    private ParkingSpace spaceFrom(TextField id, TextField lotId, TextField ownerId, TextField number,
                                   ComboBox<String> type, ComboBox<String> status, TextField start, TextField end, boolean withId) {
        ParkingSpace s = new ParkingSpace();
        if (withId) s.setSpaceId(requireLong(id, "ID"));
        s.setLotId(requireLong(lotId, "\u505c\u8f66\u573aID")); // 鍋滆溅鍦篒D
        s.setOwnerId(requireLong(ownerId, "\u6240\u6709\u8005ID")); // 鎵€鏈夎€匢D
        s.setSpaceNumber(number.getText().trim());
        s.setType(spaceTypeCode(type.getValue()));
        s.setStatus(spaceStatusCode(status.getValue()));
        s.setShareStartTime(requireTime(start, "\u5f00\u59cb\u65f6\u95f4")); // 寮€濮嬫椂闂?
        s.setShareEndTime(requireTime(end, "\u7ed3\u675f\u65f6\u95f4")); // 缁撴潫鏃堕棿
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
            r.setUnitTime(requireInt(unitTime, "\u8ba1\u8d39\u5468\u671f\u5206\u949f")); // 璁¤垂鍛ㄦ湡鍒嗛挓
        } else {
            r.setFixedPrice(new BigDecimal(fixedPrice.getText().trim()));
        }
        r.setApplicableSpaceType(spaceTypeCode(spaceType.getValue()));
        r.setStatus(enabledStatusValue(status.getValue()));
        return r;
    }
    private String roleLabel(String roleCode) {
        if ("ADMIN".equalsIgnoreCase(roleCode)) return "\u7ba1\u7406\u5458"; // 绠＄悊鍛?
        if ("OWNER".equalsIgnoreCase(roleCode)) return "\u8f66\u4f4d\u6240\u6709\u8005"; // 杞︿綅鎵€鏈夎€?
        if ("CAR_OWNER".equalsIgnoreCase(roleCode)) return "\u8f66\u4e3b"; // 杞︿富
        return roleCode == null ? "" : roleCode;
    }

    private String roleCodeFromLabel(String label) {
        if ("\u7ba1\u7406\u5458".equals(label)) return "ADMIN"; // 绠＄悊鍛?
        if ("\u8f66\u4f4d\u6240\u6709\u8005".equals(label)) return "OWNER"; // 杞︿綅鎵€鏈夎€?
        if ("\u8f66\u4e3b".equals(label)) return "CAR_OWNER"; // 杞︿富
        return "";
    }

    private Integer statusFilterValue(String label) {
        if ("\u542f\u7528".equals(label)) return 1; // 鍚敤
        if ("\u7981\u7528".equals(label)) return 0; // 绂佺敤
        return null;
    }

    private String spaceTypeCode(String label) {
        return "\u5730\u4e0b".equals(label) ? "UNDERGROUND" : "GROUND"; // 鍦颁笅
    }

    private String spaceStatusCode(String label) {
        if ("\u5df2\u9884\u7ea6".equals(label)) return "RESERVED"; // 宸查绾?
        if ("\u5360\u7528".equals(label)) return "OCCUPIED"; // 鍗犵敤
        return "FREE";
    }

    private String chargeTypeCode(String label) {
        return "\u6309\u6b21\u8ba1\u8d39".equals(label) ? "FIXED" : "HOURLY"; // 鎸夋璁¤垂
    }

    private int enabledStatusValue(String label) {
        return "\u7981\u7528".equals(label) ? 0 : 1; // 绂佺敤
    }

    private String settleStatusCode(String label) {
        if ("\u5df2\u7ed3\u7b97".equals(label)) return "SETTLED"; // 宸茬粨绠?
        if ("\u672a\u7ed3\u7b97".equals(label)) return "UNSETTLED"; // 鏈粨绠?
        return "";
    }

    private String reserveFilterCode(String label) {
        if ("\u5df2\u9884\u7ea6".equals(label)) return "RESERVED"; // 宸查绾?
        if ("\u672a\u9884\u7ea6".equals(label)) return "NOT_RESERVED"; // 鏈绾?
        return "";
    }

    private String spaceTypeLabel(String code) {
        if ("UNDERGROUND".equalsIgnoreCase(code)) return "\u5730\u4e0b"; // 鍦颁笅
        if ("GROUND".equalsIgnoreCase(code)) return "\u5730\u4e0a"; // 鍦颁笂
        return code == null ? "" : code;
    }

    private String spaceStatusLabel(String code) {
        if ("RESERVED".equalsIgnoreCase(code)) return "\u5df2\u9884\u7ea6"; // 宸查绾?
        if ("OCCUPIED".equalsIgnoreCase(code)) return "\u5360\u7528"; // 鍗犵敤
        if ("FREE".equalsIgnoreCase(code)) return "\u7a7a\u95f2"; // 绌洪棽
        return code == null ? "" : code;
    }

    private String chargeTypeLabel(String code) {
        if ("FIXED".equalsIgnoreCase(code)) return "\u6309\u6b21\u8ba1\u8d39"; // 鎸夋璁¤垂
        if ("HOURLY".equalsIgnoreCase(code)) return "\u6309\u65f6\u8ba1\u8d39"; // 鎸夋椂璁¤垂
        return code == null ? "" : code;
    }

    private String enabledLabel(Integer status) {
        return status != null && status == 1 ? "\u542f\u7528" : "\u7981\u7528"; // 鍚敤 | 绂佺敤
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private void run(CheckedAction action, TextArea out, Runnable then) {
        try {
            action.run();
            out.appendText("\u64cd\u4f5c\u6210\u529f\n"); // 鎿嶄綔鎴愬姛\\n

            if (then != null) then.run();
        } catch (Exception ex) {
            out.appendText(formatError(ex) + "\n");
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

