package com.smarttouch.service;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.smarttouch.handler.CommandHandler;
import com.smarttouch.model.DeviceCommand;

import org.json.JSONObject;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

/**
 * OkHttp WebSocket 长连接服务
 * 连接SmartTouch Server，接收指令并回传执行结果
 */
public class OkHttpWebSocketService {

    private static final String TAG = "SmartTouch-WS";

    /** 服务端WebSocket地址，启动时通过Intent传入或配置文件指定 */
    private String serverUrl;

    /** Android设备唯一标识 */
    private String deviceUuid;

    private OkHttpClient client;
    private WebSocket webSocket;
    private CommandHandler commandHandler;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 心跳间隔15秒 */
    private static final int HEARTBEAT_INTERVAL = 15000;

    /** 重连间隔5秒 */
    private static final int RECONNECT_INTERVAL = 5000;

    private boolean isConnected = false;
    private boolean shouldReconnect = true;

    /** 心跳定时任务 */
    private Runnable heartbeatRunnable = new Runnable() {
        @Override
        public void run() {
            sendHeartbeat();
            mainHandler.postDelayed(this, HEARTBEAT_INTERVAL);
        }
    };

    public OkHttpWebSocketService(String serverUrl, String deviceUuid,
                                  SmartAccessibilityService accessibilityService) {
        this.serverUrl = serverUrl;
        this.deviceUuid = deviceUuid;
        this.commandHandler = new CommandHandler(accessibilityService, this);
    }

    /** 建立WebSocket连接 */
    public void connect() {
        String wsUrl = serverUrl + "/ws/device?deviceUuid=" + deviceUuid;

        client = new OkHttpClient.Builder()
                .readTimeout(0, TimeUnit.MILLISECONDS)  // 长连接不超时
                .pingInterval(HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS)
                .build();

        Request request = new Request.Builder().url(wsUrl).build();

        webSocket = client.newWebSocket(request, new WebSocketListener() {
            @Override
            public void onOpen(WebSocket webSocket, Response response) {
                isConnected = true;
                Log.i(TAG, "WebSocket已连接: " + wsUrl);
                mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL);
            }

            @Override
            public void onMessage(WebSocket webSocket, String text) {
                Log.d(TAG, "收到服务端消息: " + text);
                commandHandler.handleCommand(text);
            }

            @Override
            public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                isConnected = false;
                Log.e(TAG, "WebSocket连接断开: " + t.getMessage());
                mainHandler.removeCallbacks(heartbeatRunnable);

                // 自动重连
                if (shouldReconnect) {
                    mainHandler.postDelayed(() -> connect(), RECONNECT_INTERVAL);
                }
            }

            @Override
            public void onClosed(WebSocket webSocket, int code, String reason) {
                isConnected = false;
                Log.i(TAG, "WebSocket已关闭: code=" + code + ", reason=" + reason);
                mainHandler.removeCallbacks(heartbeatRunnable);
            }
        });
    }

    /** 发送心跳包 */
    private void sendHeartbeat() {
        if (!isConnected) return;
        try {
            JSONObject heartbeat = new JSONObject();
            heartbeat.put("type", "heartbeat");
            heartbeat.put("deviceUuid", deviceUuid);
            heartbeat.put("deviceName", android.os.Build.MODEL);
            heartbeat.put("resolution", getScreenResolution());
            heartbeat.put("timestamp", System.currentTimeMillis());

            webSocket.send(heartbeat.toString());
        } catch (Exception e) {
            Log.e(TAG, "心跳发送失败", e);
        }
    }

    /** 发送指令回执给服务端 */
    public void sendAck(String commandId, String result, String screenshotBase64, String error) {
        if (!isConnected) return;
        try {
            JSONObject ack = new JSONObject();
            ack.put("type", "command_ack");
            ack.put("deviceUuid", deviceUuid);
            ack.put("commandId", commandId);
            ack.put("result", result);         // success/fail/timeout
            ack.put("screenshotBase64", screenshotBase64 != null ? screenshotBase64 : "");
            ack.put("error", error != null ? error : "");
            ack.put("timestamp", System.currentTimeMillis());

            webSocket.send(ack.toString());
        } catch (Exception e) {
            Log.e(TAG, "回执发送失败", e);
        }
    }

    /** 断开连接 */
    public void disconnect() {
        shouldReconnect = false;
        mainHandler.removeCallbacks(heartbeatRunnable);
        if (webSocket != null) {
            webSocket.close(1000, "客户端主动关闭");
        }
    }

    /** 连接状态 */
    public boolean isConnected() {
        return isConnected;
    }

    /** 获取屏幕分辨率 */
    private String getScreenResolution() {
        android.view.DisplayMetrics metrics =
                android.content.res.Resources.getSystem().getDisplayMetrics();
        return metrics.widthPixels + "x" + metrics.heightPixels;
    }
}
