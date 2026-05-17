#!/usr/bin/env python3
import re

# Read the file
with open('src/main/java/com/parking/app/DashboardFactory.java', 'r', encoding='utf-8') as f:
    content = f.read()

# The code to insert after reload Runnable definition
inline_edit_code = '''
        // 添加内联编辑处理器
        username.setCellFactory(TextFieldTableCell.forTableColumn());
        username.setOnEditCommit(e -> {
            User user = e.getRowValue();
            String newValue = e.getNewValue() == null ? "" : e.getNewValue().trim();
            if (newValue.isEmpty()) {
                showAlert("用户名不能为空");
                return;
            }
            try {
                userAdminService.updateUserField(user.getUserId(), "username", newValue);
                addOperationLog(LOG_UPDATE, formatModuleLog("用户管理", "修改用户名 userId=" + user.getUserId() + " " + user.getUsername() + "->" + newValue));
                out.appendText("修改成功：" + user.getUsername() + " -> " + newValue + "\\n");
                reload.run();
            } catch (Exception ex) {
                showAlert("修改失败：" + ex.getMessage());
                reload.run();
            }
        });

        realName.setCellFactory(TextFieldTableCell.forTableColumn());
        realName.setOnEditCommit(e -> {
            User user = e.getRowValue();
            String newValue = e.getNewValue() == null ? "" : e.getNewValue().trim();
            try {
                userAdminService.updateUserField(user.getUserId(), "real_name", newValue);
                addOperationLog(LOG_UPDATE, formatModuleLog("用户管理", "修改真实姓名 userId=" + user.getUserId() + " " + user.getRealName() + "->" + newValue));
                out.appendText("修改成功：" + user.getRealName() + " -> " + newValue + "\\n");
                reload.run();
            } catch (Exception ex) {
                showAlert("修改失败：" + ex.getMessage());
                reload.run();
            }
        });

        phone.setCellFactory(TextFieldTableCell.forTableColumn());
        phone.setOnEditCommit(e -> {
            User user = e.getRowValue();
            String newValue = e.getNewValue() == null ? "" : e.getNewValue().trim();
            if (!newValue.isEmpty() && !newValue.matches("\\\\d{11}")) {
                showAlert("手机号必须为11位数字，请检查后重新输入");
                return;
            }
            try {
                userAdminService.updateUserField(user.getUserId(), "phone", newValue);
                addOperationLog(LOG_UPDATE, formatModuleLog("用户管理", "修改手机号 userId=" + user.getUserId() + " " + user.getPhone() + "->" + newValue));
                out.appendText("修改成功：" + user.getPhone() + " -> " + newValue + "\\n");
                reload.run();
            } catch (Exception ex) {
                showAlert("修改失败：" + ex.getMessage());
                reload.run();
            }
        });

        // 角色下拉框
        Callback<TableColumn<User, String>, TableCell<User, String>> rolesCellFactory = col -> {
            ComboBoxTableCell<User, String> cell = new ComboBoxTableCell<>(FXCollections.observableArrayList("车主", "车位所有者"));
            cell.setComboBoxEditable(true);
            return cell;
        };
        role.setCellFactory(rolesCellFactory);
        role.setOnEditCommit(e -> {
            User user = e.getRowValue();
            String newValue = e.getNewValue();
            if (newValue == null || newValue.equals(roleLabel(user.getRole()))) {
                return;
            }
            String oldValue = roleLabel(user.getRole());
            String roleCode = "车位所有者".equals(newValue) ? "OWNER" : "CAR_OWNER";
            try {
                userAdminService.updateUserField(user.getUserId(), "role", roleCode);
                addOperationLog(LOG_UPDATE, formatModuleLog("用户管理", "修改角色 userId=" + user.getUserId() + " " + oldValue + "->" + newValue));
                out.appendText("修改成功：" + oldValue + " -> " + newValue + "\\n");
                reload.run();
            } catch (Exception ex) {
                showAlert("修改失败：" + ex.getMessage());
                reload.run();
            }
        });

        // 状态下拉框
        Callback<TableColumn<User, String>, TableCell<User, String>> statusCellFactory = col -> {
            ComboBoxTableCell<User, String> cell = new ComboBoxTableCell<>(FXCollections.observableArrayList("启用", "禁用"));
            cell.setComboBoxEditable(true);
            return cell;
        };
        status.setCellFactory(statusCellFactory);
        status.setOnEditCommit(e -> {
            User user = e.getRowValue();
            String newValue = e.getNewValue();
            if (newValue == null || newValue.equals(user.getStatus() == 1 ? "启用" : "禁用")) {
                return;
            }
            String oldValue = user.getStatus() == 1 ? "启用" : "禁用";
            int statusCode = "启用".equals(newValue) ? 1 : 0;
            try {
                userAdminService.updateUserField(user.getUserId(), "status", String.valueOf(statusCode));
                addOperationLog(LOG_UPDATE, formatModuleLog("用户管理", "修改状态 userId=" + user.getUserId() + " " + oldValue + "->" + newValue));
                out.appendText("修改成功：" + oldValue + " -> " + newValue + "\\n");
                reload.run();
            } catch (Exception ex) {
                showAlert("修改失败：" + ex.getMessage());
                reload.run();
            }
        });
'''

# Find the location to insert: after "};", before "Button query = new Button"
# The pattern is the end of reload Runnable
pattern = r'(\t+Runnable reload = \(\) -> \{[^}]+\};)\s*\n\s*\n\s*(Button query = new Button)'

# Check if pattern exists
if re.search(pattern, content):
    print("Pattern found, replacing...")
    new_content = re.sub(pattern, r'\1\n' + inline_edit_code + r'\n\n        \2', content)
    with open('src/main/java/com/parking/app/DashboardFactory.java', 'w', encoding='utf-8') as f:
        f.write(new_content)
    print("Done!")
else:
    print("Pattern not found, trying alternative...")
    # Try alternative pattern with fewer constraints
    pattern2 = '        };\n\n        Button query = new Button'
    if pattern2 in content:
        print("Found alternative pattern")
        idx = content.index(pattern2)
        print(f"Index: {idx}")
