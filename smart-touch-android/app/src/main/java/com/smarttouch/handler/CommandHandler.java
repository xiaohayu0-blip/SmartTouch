package com.smarttouch.handler;

import android.graphics.Bitmap;
import android.util.Log;

import com.smarttouch.model.DeviceCommand;
import com.smarttouch.service.OkHttpWebSocketService;
import com.smarttouch.service.SmartAccessibilityService;

import org.json.JSONObject;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 指令处理器
 * 解析服务端下发的JSON指令，分发到对应的执行器
 */
public class CommandHandler {

    private static final String TAG = "SmartTouch-CMD";

    private final SmartAccessibilityService accessibilityService;
    private final OkHttpWebSocketService wsService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public CommandHandler(SmartAccessibilityService accessibilityService,
                          OkHttpWebSocketService wsService) {
        this.accessibilityService = accessibilityService;
        this.wsService = wsService;
    }

    /** 处理服务端下发的指令（在子线程执行，避免阻塞WebSocket回调） */
    public void handleCommand(String jsonText) {
        executor.submit(() -> {
            try {
                JSONObject json = new JSONObject(jsonText);
                String commandId = json.getString("commandId");
                String action = json.getString("action");
                JSONObject params = json.optJSONObject("params");

                Log.d(TAG, "收到指令: commandId=" + commandId + ", action=" + action);

                // 分发到具体执行器
                String result = "fail";
                String screenshotBase64 = null;
                String error = null;

                try {
                    switch (action) {
                        case "click":
                            result = executeClick(params) ? "success" : "fail";
                            break;

                        case "swipe":
                            result = executeSwipe(params) ? "success" : "fail";
                            break;

                        case "type":
                            result = executeInput(params) ? "success" : "fail";
                            break;

                        case "screenshot":
                            screenshotBase64 = executeScreenshot();
                            result = screenshotBase64 != null ? "success" : "fail";
                            if (screenshotBase64 == null) {
                                error = "截图失败";
                            }
                            break;

                        case "wait":
                            result = executeWait(params) ? "success" : "fail";
                            break;

                        default:
                            error = "未知动作: " + action;
                            Log.w(TAG, error);
                    }
                } catch (Exception e) {
                    error = e.getMessage();
                    Log.e(TAG, "指令执行异常: " + action, e);
                }

                // 动作执行后再截一帧作为执行后截图
                if (screenshotBase64 == null && !"screenshot".equals(action)) {
                    screenshotBase64 = accessibilityService.captureScreenshotBase64();
                }

                // 发送回执
                wsService.sendAck(commandId, result, screenshotBase64, error);

            } catch (Exception e) {
                Log.e(TAG, "指令解析失败", e);
            }
        });
    }

    /** 执行点击 */
    private boolean executeClick(JSONObject params) {
        int x = params.getInt("x");
        int y = params.getInt("y");
        return accessibilityService.performClick(x, y);
    }

    /** 执行滑动 */
    private boolean executeSwipe(JSONObject params) {
        int x1 = params.getInt("x1");
        int y1 = params.getInt("y1");
        int x2 = params.getInt("x2");
        int y2 = params.getInt("y2");
        return accessibilityService.performSwipe(x1, y1, x2, y2);
    }

    /** 执行文本输入 */
    private boolean executeInput(JSONObject params) {
        String text = params.getString("text");
        return accessibilityService.performInput(text);
    }

    /** 执行截图 */
    private String executeScreenshot() {
        return accessibilityService.captureScreenshotBase64();
    }

    /** 执行等待（在服务端已执行，这里仅作标记） */
    private boolean executeWait(JSONObject params) {
        int ms = params.optInt("ms", 1000);
        try {
            Thread.sleep(Math.min(ms, 5000)); // 设备端最多等5秒
        } catch (InterruptedException ignored) {}
        return true;
    }
}
