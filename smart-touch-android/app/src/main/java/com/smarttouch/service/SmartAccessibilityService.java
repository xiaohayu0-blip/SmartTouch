package com.smarttouch.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Bitmap;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.view.Display;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;

/**
 * 无障碍服务（AccessibilityService）
 * 核心功能：执行点击/滑动/输入/截图等自动化操作
 *
 * 需要在 res/xml/accessibility_service_config.xml 中配置
 * 需要在系统设置 → 无障碍 → SmartTouch 中手动开启
 */
public class SmartAccessibilityService extends AccessibilityService {

    private static final String TAG = "SmartTouch-AS";

    private static SmartAccessibilityService instance;
    private Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 截图相关 */
    private MediaProjectionManager projectionManager;
    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private int screenWidth;
    private int screenHeight;
    private int screenDensity;

    /** 最近一次截图结果 */
    private Bitmap latestScreenshot;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        Log.i(TAG, "无障碍服务已创建");
    }

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "无障碍服务已连接");

        // 获取屏幕参数
        Display display = getSystemService(android.hardware.display.DisplayManager.class)
                .getDisplay(Display.DEFAULT_DISPLAY);
        android.util.DisplayMetrics metrics = new android.util.DisplayMetrics();
        display.getRealMetrics(metrics);
        screenWidth = metrics.widthPixels;
        screenHeight = metrics.heightPixels;
        screenDensity = metrics.densityDpi;
        Log.i(TAG, "屏幕分辨率: " + screenWidth + "x" + screenHeight + ", dpi=" + screenDensity);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // 监听窗口变化等事件（暂不处理，由服务端主动下发指令）
    }

    @Override
    public void onInterrupt() {
        Log.w(TAG, "无障碍服务被中断");
    }

    @Override
    public void onDestroy() {
        instance = null;
        super.onDestroy();
        Log.i(TAG, "无障碍服务已销毁");
    }

    /** 获取单例 */
    public static SmartAccessibilityService getInstance() {
        return instance;
    }

    // ==================== 动作执行 ====================

    /** 点击指定坐标 */
    public boolean performClick(int x, int y) {
        Log.d(TAG, "执行点击: (" + x + ", " + y + ")");

        Path clickPath = new Path();
        clickPath.moveTo(x, y);

        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(clickPath, 0, 1));

        return dispatchGesture(gestureBuilder.build(), null, null);
    }

    /** 滑动操作 */
    public boolean performSwipe(int x1, int y1, int x2, int y2) {
        Log.d(TAG, "执行滑动: (" + x1 + "," + y1 + ") → (" + x2 + "," + y2 + ")");

        Path swipePath = new Path();
        swipePath.moveTo(x1, y1);
        swipePath.lineTo(x2, y2);

        // 滑动持续时间300ms，模拟真实操作
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(new GestureDescription.StrokeDescription(swipePath, 0, 300));

        return dispatchGesture(gestureBuilder.build(), null, null);
    }

    /** 在焦点输入框输入文本 */
    public boolean performInput(String text) {
        Log.d(TAG, "执行文本输入: " + text);

        // 方法1：通过焦点节点设置文本
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root != null) {
            AccessibilityNodeInfo focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            if (focused != null) {
                Bundle arguments = new Bundle();
                arguments.putCharSequence(
                        AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
                boolean result = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments);
                focused.recycle();
                root.recycle();
                if (result) return true;
            }
            root.recycle();
        }

        // 方法2：通过剪贴板粘贴
        android.content.ClipboardManager clipboard =
                (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        android.content.ClipData clip = android.content.ClipData.newPlainText("smarttouch", text);
        clipboard.setPrimaryClip(clip);

        // 模拟长按粘贴（在屏幕中央，由服务端指定粘贴位置时发送click后自行粘贴）
        return performClick(screenWidth / 2, screenHeight / 2);
    }

    /** 截图（使用MediaProjection方式，需要用户首次授权） */
    public Bitmap captureScreenshot() {
        try {
            // 使用 screencap 命令获取截图（无需MediaProjection授权）
            Process process = Runtime.getRuntime().exec("screencap -p");
            Bitmap bitmap = android.graphics.BitmapFactory.decodeStream(process.getInputStream());
            process.waitFor();
            if (bitmap != null) {
                // 压缩截图以减少传输体积
                int maxWidth = 720;  // 最大宽度720px
                if (bitmap.getWidth() > maxWidth) {
                    int newHeight = bitmap.getHeight() * maxWidth / bitmap.getWidth();
                    bitmap = Bitmap.createScaledBitmap(bitmap, maxWidth, newHeight, true);
                }
                latestScreenshot = bitmap;
            }
            return bitmap;
        } catch (Exception e) {
            Log.e(TAG, "截图失败", e);
            // fallback: 从当前窗口截图
            return takeScreenshotFromWindow();
        }
    }

    /** 从当前窗口截图（通过AccessibilityService） */
    private Bitmap takeScreenshotFromWindow() {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return null;

        // 通过PixelCopy或SurfaceControl截图（需要API 24+）
        // 这里返回null，由上层处理
        return null;
    }

    /** 将Bitmap转为base64字符串 */
    public String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return "";
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 50, baos); // JPEG质量50%
        byte[] bytes = baos.toByteArray();
        Log.d(TAG, "截图大小: " + (bytes.length / 1024) + "KB (压缩后)");
        return Base64.encodeToString(bytes, Base64.NO_WRAP);
    }

    /** 截取当前屏幕并返回base64 */
    public String captureScreenshotBase64() {
        Bitmap bitmap = captureScreenshot();
        return bitmap != null ? bitmapToBase64(bitmap) : null;
    }

    /** 获取屏幕宽度 */
    public int getScreenWidth() { return screenWidth; }

    /** 获取屏幕高度 */
    public int getScreenHeight() { return screenHeight; }
}
