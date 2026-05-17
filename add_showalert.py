#!/usr/bin/env python3
with open('E:/Vscode/SmartParkingSystem/src/main/java/com/parking/app/DashboardFactory.java', 'r', encoding='utf-8') as f:
    content = f.read()

# Find the location where we need to insert showAlert method
# Right after formatError method, before toChineseMessage

old_text = '''        return "\\u9519\\u8bef\\uff1a" + display; // 错误：
    }

    private String toChineseMessage(String msg) {'''

new_text = '''        return "\\u9519\\u8bef\\uff1a" + display; // 错误：
    }

    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("\\u63d0\\u793a"); // 提示
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    private String toChineseMessage(String msg) {'''

if old_text in content:
    content = content.replace(old_text, new_text)
    with open('E:/Vscode/SmartParkingSystem/src/main/java/com/parking/app/DashboardFactory.java', 'w', encoding='utf-8') as f:
        f.write(content)
    print("showAlert method added successfully")
else:
    print("Pattern not found")
    # Debug - find the line
    idx = content.find('private String toChineseMessage')
    print(f"toChineseMessage found at: {idx}")
    if idx > 0:
        print("Context:")
        print(repr(content[idx-200:idx+100]))