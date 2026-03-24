package com.parking.app;

import com.parking.entity.User;
import com.parking.service.AuthService;
import com.parking.service.impl.AuthServiceImpl;
import javafx.application.Application;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import java.net.URL;

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

        ComboBox<String> roleBox = new ComboBox<>();
        roleBox.getItems().addAll("\u8f66\u4e3b", "\u8f66\u4f4d\u6240\u6709\u8005"); // 车主 | 车位所有者
        roleBox.setValue("\u8f66\u4e3b"); // 车主

        TextField regRealName = new TextField();
        regRealName.setPromptText("\u771f\u5b9e\u59d3\u540d"); // 真实姓名
        regRealName.setPrefWidth(150);

        TextField regPhone = new TextField();
        regPhone.setPromptText("\u624b\u673a\u53f7\uff0811\u4f4d\uff09"); // 手机号（11位）
        regPhone.setPrefWidth(170);

        Button registerBtn = new Button("\u6ce8\u518c"); // 注册
        registerBtn.setOnAction(e -> {
            try {
                User u = new User();
                u.setUsername(regUser.getText().trim());
                u.setPassword(regPwd.getText().trim());
                u.setRealName(regRealName.getText() == null ? "" : regRealName.getText().trim());
                u.setPhone(regPhone.getText() == null ? "" : regPhone.getText().trim());
                u.setRole("\u8f66\u4f4d\u6240\u6709\u8005".equals(roleBox.getValue()) ? "OWNER" : "CAR_OWNER"); // 车位所有者
                long uid = authService.register(u);
                log.appendText("\u6ce8\u518c\u6210\u529f\uff0cuser_id=" + uid + "\n"); // 注册成功，user_id=
                regUser.clear();
                regPwd.clear();
                regPwdVisibleField.clear();
                regRealName.clear();
                regPhone.clear();
                roleBox.setValue("\u8f66\u4e3b"); // 车主
                showRegPwd.setSelected(false);
                regPwd.setVisible(true);
                regPwd.setManaged(true);
                regPwdVisibleField.setVisible(false);
                regPwdVisibleField.setManaged(false);
            } catch (Exception ex) {
                log.appendText("\u6ce8\u518c\u5931\u8d25\uff1a" + localizeErrorMessage(ex) + "\n"); // 注册失败：
            }
        });

        GridPane loginForm = new GridPane();
        loginForm.setHgap(8);
        loginForm.setVgap(8);
        loginForm.addRow(0, new Label("\u8d26\u53f7\uff08\u7528\u6237\u540d/ID\uff09\uff1a"), usernameField); // 账号（用户名/ID）：
        loginForm.addRow(1, new Label("\u5bc6\u7801\uff1a"), loginPasswordPane, showLoginPwd); // 密码：

        VBox root = new VBox(12,
                new Label("\u667a\u80fd\u505c\u8f66\u4f4d\u5171\u4eab\u4e0e\u9884\u7ea6\u7ba1\u7406\u7cfb\u7edf"), // 智能停车位共享与预约管理系统
                new Label("\u9ed8\u8ba4\u7ba1\u7406\u5458\uff1aadmin"), // 默认管理员：admin
                loginForm,
                new HBox(8, loginBtn),
                new Label("\u65b0\u7528\u6237\u6ce8\u518c"), // 新用户注册
                new HBox(8, regUser, regPasswordPane, showRegPwd, roleBox, registerBtn),
                new HBox(8, regRealName, regPhone),
                log
        );
        root.setPadding(new Insets(16));
        root.getStyleClass().add("login-root");

        Scene loginScene = new Scene(root, 820, 460);
        applyTheme(loginScene);
        stage.setScene(loginScene);
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
