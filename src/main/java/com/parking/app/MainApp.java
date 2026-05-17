package com.parking.app;

import com.parking.entity.User;
import com.parking.service.AuthService;
import com.parking.service.impl.AuthServiceImpl;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.event.ActionEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URL;
import java.util.Optional;

public class MainApp extends Application {

    private final AuthService authService = new AuthServiceImpl();

    @Override
    public void start(Stage stage) {
        stage.setTitle("\u667a\u80fd\u505c\u8f66\u4f4d\u5171\u4eab\u4e0e\u9884\u7ea6\u7ba1\u7406\u7cfb\u7edf"); // 智能停车位共享与预约管理系统
        showLogin(stage);
        stage.show();
    }

    private void showLogin(Stage stage) {
        TextField usernameField = new TextField();
        usernameField.setPromptText("\u8bf7\u8f93\u5165\u7528\u6237\u540d\u6216ID"); // 请输入用户名或ID

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText("\u8bf7\u8f93\u5165\u5bc6\u7801"); // 请输入密码
        TextField passwordVisibleField = new TextField();
        passwordVisibleField.textProperty().bindBidirectional(passwordField.textProperty());
        passwordVisibleField.setPromptText("\u8bf7\u8f93\u5165\u5bc6\u7801"); // 请输入密码
        passwordVisibleField.setVisible(false);
        passwordVisibleField.setManaged(false);
        StackPane loginPasswordPane = new StackPane(passwordField, passwordVisibleField);

        CheckBox showLoginPwd = new CheckBox("\u663e\u793a\u5bc6\u7801"); // 显示密码
        showLoginPwd.setOnAction(e -> {
            boolean show = showLoginPwd.isSelected();
            passwordField.setVisible(!show);
            passwordField.setManaged(!show);
            passwordVisibleField.setVisible(show);
            passwordVisibleField.setManaged(show);
        });

        TextArea log = new TextArea();
        log.setPrefRowCount(4);
        log.setEditable(false);

        Button loginBtn = new Button("\u767b\u5f55"); // 登录
        loginBtn.setOnAction(e -> {
            try {
                User user = authService.login(usernameField.getText().trim(), passwordField.getText().trim());
                showDashboard(stage, user);
            } catch (Exception ex) {
                log.appendText("\u767b\u5f55\u5931\u8d25\uff1a" + localizeErrorMessage(ex) + "\n"); // 登录失败：
            }
        });

        Button registerBtn = new Button("\u6ce8\u518c\u8d26\u53f7"); // 注册账号
        registerBtn.setOnAction(e -> showRegisterDialog(stage));

        GridPane loginForm = new GridPane();
        loginForm.setHgap(8);
        loginForm.setVgap(8);
        loginForm.addRow(0, new Label("\u8d26\u53f7\uff08\u7528\u6237\u540d/ID\uff09\uff1a"), usernameField); // 账号（用户名/ID）：
        loginForm.addRow(1, new Label("\u5bc6\u7801\uff1a"), loginPasswordPane, showLoginPwd); // 密码：

        VBox root = new VBox(12,
                new Label("\u667a\u80fd\u505c\u8f66\u4f4d\u5171\u4eab\u4e0e\u9884\u7ea6\u7ba1\u7406\u7cfb\u7edf"), // 智能停车位共享与预约管理系统
                new Label("\u9ed8\u8ba4\u7ba1\u7406\u5458\uff1aadmin"), // 默认管理员：admin
                loginForm,
                new HBox(8, loginBtn, registerBtn),
                log
        );
        root.setPadding(new Insets(16));
        root.setAlignment(Pos.CENTER);
        root.getStyleClass().add("login-root");

        Scene loginScene = new Scene(root, 480, 400);
        applyTheme(loginScene);
        stage.setScene(loginScene);
    }

private void showRegisterDialog(Stage parentStage) {
        Dialog<User> dialog = new Dialog<>();
        dialog.setTitle("\u7528\u6237\u6ce8\u518c"); // 用户注册
        dialog.setHeaderText("\u8bf7\u586b\u5199\u6ce8\u4fe1\u606f"); // 请填写注册信息

        TextField regUser = new TextField();
        regUser.setPromptText("\u7528\u6237\u540d"); // 用户名

        PasswordField regPwd = new PasswordField();
        regPwd.setPromptText("\u5bc6\u7801"); // 密码
        TextField regPwdVisibleField = new TextField();
        regPwdVisibleField.textProperty().bindBidirectional(regPwd.textProperty());
        regPwdVisibleField.setPromptText("\u5bc6\u7801"); // 密码
        regPwdVisibleField.setVisible(false);
        regPwdVisibleField.setManaged(false);
        StackPane regPasswordPane = new StackPane(regPwd, regPwdVisibleField);

        CheckBox showRegPwd = new CheckBox("\u663e\u793a\u5bc6\u7801"); // 显示密码
        showRegPwd.setOnAction(e -> {
            boolean show = showRegPwd.isSelected();
            regPwd.setVisible(!show);
            regPwd.setManaged(!show);
            regPwdVisibleField.setVisible(show);
            regPwdVisibleField.setManaged(show);
        });

        TextField regRealName = new TextField();
        regRealName.setPromptText("\u771f\u5b9e\u59d3\u540d"); // 真实姓名

        TextField regPhone = new TextField();
        regPhone.setPromptText("\u624b\u673a\u53f7\uff0811\u4f4d\uff09"); // 手机号（11位）

        ButtonType registerButtonType = new ButtonType("\u63d0\u4ea4\u6ce8\u518c", ButtonBar.ButtonData.OK_DONE); // 提交注册
        dialog.getDialogPane().getButtonTypes().addAll(registerButtonType, ButtonType.CANCEL);

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(16));

        grid.add(new Label("\u7528\u6237\u540d\uff1a"), 0, 0); // 用户名：
        grid.add(regUser, 1, 0);

        grid.add(new Label("\u5bc6\u7801\uff1a"), 0, 1); // 密码：
        HBox pwdRow = new HBox(8, regPasswordPane, showRegPwd);
        grid.add(pwdRow, 1, 1);

        grid.add(new Label("\u771f\u5b9e\u59d3\u540d\uff1a"), 0, 2); // 真实姓名：
        grid.add(regRealName, 1, 2);

        grid.add(new Label("\u624b\u673a\u53f7\uff1a"), 0, 3); // 手机号：
        grid.add(regPhone, 1, 3);

        Label roleLabel = new Label("\u89d2\u8272\u9009\u62e9\uff1a"); // 角色选择：
        javafx.scene.control.ComboBox<String> roleBox = new javafx.scene.control.ComboBox<>();
        roleBox.getItems().addAll("\u8f66\u4e3b", "\u8f66\u4f4d\u6240\u6709\u8005"); // 车主 | 车位所有者
        roleBox.setValue("\u8f66\u4e3b"); // 车主
        HBox roleRow = new HBox(8, roleBox);
        grid.add(roleLabel, 0, 4);
        grid.add(roleRow, 1, 4);

        dialog.getDialogPane().setContent(grid);

        Button registerBtn = (Button) dialog.getDialogPane().lookupButton(registerButtonType);
        registerBtn.addEventFilter(ActionEvent.ACTION, e -> {
            e.consume();
            try {
                User u = new User();
                u.setUsername(regUser.getText().trim());
                u.setPassword(regPwd.getText().trim());
                u.setRealName(regRealName.getText() == null ? "" : regRealName.getText().trim());
                u.setPhone(regPhone.getText() == null ? "" : regPhone.getText().trim());
                u.setRole("\u8f66\u4f4d\u6240\u6709\u8005".equals(roleBox.getValue()) ? "OWNER" : "CAR_OWNER");
                long uid = authService.register(u);
                dialog.setResult(u);
                dialog.close();
                showRegistrationSuccessDialog(uid);
            } catch (Exception ex) {
                showRegistrationErrorDialog(ex);
            }
        });

        dialog.showAndWait();
    }

    private void showRegistrationSuccessDialog(long userId) {
        Dialog<Void> successDialog = new Dialog<>();
        successDialog.setTitle("\u6ce8\u518c\u6210\u529f"); // 注册成功
        successDialog.setHeaderText("\u6ce8\u518c\u6210\u529f\uff01"); // 注册成功！
        successDialog.setContentText("\u60a8\u7684\u7528\u6237ID\u4e3a\uff1a" + userId + "\n\u8bf7\u52fe\u8d77\u767b\u5f55\u8fdb\u884c\u64cd\u4f5c\u3002"); // 您的用户ID为：请勾起登录进行操作。

        ButtonType okButton = new ButtonType("\u786e\u5b9a", ButtonBar.ButtonData.OK_DONE); // 确定
        successDialog.getDialogPane().getButtonTypes().add(okButton);
        successDialog.showAndWait();
    }

private void showRegistrationErrorDialog(Exception ex) {
        Alert errorAlert = new Alert(Alert.AlertType.ERROR);
        errorAlert.setTitle("\u6ce8\u518c\u5931\u8d25"); // 注册失败
        errorAlert.setHeaderText("\u6ce8\u518c\u5931\u8d25"); // 注册失败
        errorAlert.setContentText(localizeErrorMessage(ex));
        errorAlert.showAndWait();
    }

    private void showDashboard(Stage stage, User user) {
        DashboardFactory factory = new DashboardFactory();
        Scene scene = new Scene(factory.createMainView(user, () -> showLogin(stage)), 1100, 760);
        applyTheme(scene);
        stage.setScene(scene);
    }

    private void applyTheme(Scene scene) {
        URL css = MainApp.class.getResource("/ui/app-theme.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
    }

    private String localizeErrorMessage(Exception ex) {
        if (ex == null || ex.getMessage() == null) return "\u672a\u77e5\u9519\u8bef"; // 未知错误
        String m = ex.getMessage().trim();
        if (m.startsWith("Cooldown:")) {
            int seconds = Integer.parseInt(m.substring("Cooldown:".length()).trim());
            return "\u767b\u5f55\u5df2\u88ab\u9501\u5b9a\uff0c\u8bf7\u7a0d\u5019" + seconds + "\u79d2\u540e\u518d\u8bd5\u3002"; // 登录已被锁定，请稍候秒后再试。
        }
        if (m.startsWith("Wrong password, locked for")) {
            int seconds = Integer.parseInt(m.substring("Wrong password, locked for".length()).replace("seconds", "").trim());
            return "\u5bc6\u7801\u9519\u8bef\uff0c\u5df2\u8fdb\u5165\u51b7\u5374\u671f\uff0c\u8bf7\u7a0d\u5019" + seconds + "\u79d2\u540e\u518d\u8bd5\u3002"; // 密码错误，已进入冷却期，请稍候秒后再试。
        }
        if (m.startsWith("Wrong password, ") && m.contains("attempts remaining")) {
            String remaining = m.substring("Wrong password, ".length(), m.indexOf(" attempts remaining"));
            return "\u5bc6\u7801\u9519\u8bef\uff0c\u8f93\u5165\u6b21\u6570\u8fd8\u6709" + remaining + "\u6b21"; // 密码错误，输入次数还有次
        }
        if ("Username and password are required".equalsIgnoreCase(m)) return "\u8d26\u53f7\uff08\u7528\u6237\u540d/ID\uff09\u548c\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"; // 账号（用户名/ID）和密码不能为空
        if ("User does not exist or is disabled".equalsIgnoreCase(m)) return "\u7528\u6237\u4e0d\u5b58\u5728\u6216\u5df2\u88ab\u7981\u7528"; // 用户不存在或已被禁用
        if ("Wrong password".equalsIgnoreCase(m)) return "\u5bc6\u7801\u9519\u8bef"; // 密码错误
        if ("Username is required".equalsIgnoreCase(m)) return "\u7528\u6237\u540d\u4e0d\u80fd\u4e3a\u7a7a"; // 用户名不能为空
        if ("Password is required".equalsIgnoreCase(m)) return "\u5bc6\u7801\u4e0d\u80fd\u4e3a\u7a7a"; // 密码不能为空
        if ("Role is required".equalsIgnoreCase(m)) return "\u89d2\u8272\u4e0d\u80fd\u4e3a\u7a7a"; // 角色不能为空
        if ("Real name is required".equalsIgnoreCase(m)) return "\u771f\u5b9e\u59d3\u540d\u4e0d\u80fd\u4e3a\u7a7a"; // 真实姓名不能为空
        if ("Phone is required".equalsIgnoreCase(m)) return "\u624b\u673a\u53f7\u4e0d\u80fd\u4e3a\u7a7a"; // 手机号不能为空
        if ("Phone must be 11 digits".equalsIgnoreCase(m)) return "\u624b\u673a\u53f7\u5fc5\u987b\u4e3a11\u4f4d\u6570\u5b57"; // 手机号必须为11位数字
        if ("Username already exists".equalsIgnoreCase(m)) return "\u7528\u6237\u540d\u5df2\u5b58\u5728"; // 用户名已存在
        if (m.toLowerCase().startsWith("username already exists:")) {
            return "\u7528\u6237\u540d\u5df2\u5b58\u5728\uff1a" + m.substring("Username already exists:".length()).trim(); // 用户名已存在：
        }
        if ("User not found".equalsIgnoreCase(m)) return "\u7528\u6237\u4e0d\u5b58\u5728"; // 用户不存在
        return m;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
