package com.smarttouch;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.smarttouch.service.OkHttpWebSocketService;
import com.smarttouch.service.SmartAccessibilityService;

/**
 * 主Activity——简易控制面板
 * 输入服务器地址后连接/断开
 */
public class MainActivity extends Activity {

    private EditText etServerUrl;
    private EditText etDeviceUuid;
    private Button btnConnect;
    private Button btnDisconnect;
    private TextView tvStatus;

    private OkHttpWebSocketService wsService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(createLayout());

        // 检查无障碍服务是否已开启
        if (!isAccessibilityServiceEnabled()) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
    }

    /** 动态创建简易布局（避免依赖XML） */
    private View createLayout() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(48, 48, 48, 48);

        // 标题
        TextView title = new TextView(this);
        title.setText("SmartTouch 设备端");
        title.setTextSize(20);
        layout.addView(title);

        // 服务器地址
        TextView label1 = new TextView(this);
        label1.setText("服务器地址:");
        layout.addView(label1);

        etServerUrl = new EditText(this);
        etServerUrl.setText("ws://192.168.1.100:8080");
        layout.addView(etServerUrl);

        // 设备UUID
        TextView label2 = new TextView(this);
        label2.setText("设备标识:");
        layout.addView(label2);

        etDeviceUuid = new EditText(this);
        etDeviceUuid.setText(Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ANDROID_ID));
        layout.addView(etDeviceUuid);

        // 状态
        tvStatus = new TextView(this);
        tvStatus.setText("状态: 未连接");
        layout.addView(tvStatus);

        // 连接按钮
        btnConnect = new Button(this);
        btnConnect.setText("连接服务器");
        btnConnect.setOnClickListener(v -> {
            String serverUrl = etServerUrl.getText().toString().trim();
            String deviceUuid = etDeviceUuid.getText().toString().trim();

            SmartAccessibilityService service = SmartAccessibilityService.getInstance();
            if (service == null) {
                tvStatus.setText("状态: 请先开启无障碍服务");
                return;
            }

            wsService = new OkHttpWebSocketService(serverUrl, deviceUuid, service);
            wsService.connect();
            tvStatus.setText("状态: 连接中...");
            btnConnect.setEnabled(false);
            btnDisconnect.setEnabled(true);
        });
        layout.addView(btnConnect);

        // 断开按钮
        btnDisconnect = new Button(this);
        btnDisconnect.setText("断开连接");
        btnDisconnect.setEnabled(false);
        btnDisconnect.setOnClickListener(v -> {
            if (wsService != null) {
                wsService.disconnect();
            }
            tvStatus.setText("状态: 已断开");
            btnConnect.setEnabled(true);
            btnDisconnect.setEnabled(false);
        });
        layout.addView(btnDisconnect);

        return layout;
    }

    /** 检查无障碍服务是否已启用 */
    private boolean isAccessibilityServiceEnabled() {
        String serviceName = getPackageName() + "/.service.SmartAccessibilityService";
        String enabledServices = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(serviceName);
    }

    @Override
    protected void onDestroy() {
        if (wsService != null) {
            wsService.disconnect();
        }
        super.onDestroy();
    }
}
