package com.nlkashei.witchgirl;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.Toast;

import org.json.JSONObject;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Entirely offline Flash app. Its one fixed HTTPS origin is served from APK assets.
 * Native saves are stored atomically under private /data/data/.../files.
 * No INTERNET permission is requested.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String ENTRY = "https://" + HOST + "/assets/index.html";
    private static final int EXPORT_SAVE = 1001, IMPORT_SAVE = 1002;
    private FrameLayout root;
    private WebView game;
    private SaveStore saves;

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private WebResourceResponse reply(int code, String message) {
        return new WebResourceResponse("text/plain", "UTF-8", code, message,
                new HashMap<>(), new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));
    }

    private WebResourceResponse asset(Uri uri) {
        if (!HOST.equalsIgnoreCase(uri.getHost()) || !"https".equalsIgnoreCase(uri.getScheme()))
            return reply(403, "Offline only");
        String path = uri.getPath();
        if (path == null || !path.startsWith("/assets/")) return reply(404, "Not found");
        path = path.substring("/assets/".length());
        if (path.isEmpty()) path = "index.html";
        if (path.contains("..") || path.startsWith("/")) return reply(403, "Forbidden");

        String mime = "application/octet-stream", charset = null;
        if (path.endsWith(".html")) { mime = "text/html"; charset = "UTF-8"; }
        else if (path.endsWith(".js") || path.endsWith(".mjs")) { mime = "application/javascript"; charset = "UTF-8"; }
        else if (path.endsWith(".wasm")) { mime = "application/wasm"; }
        else if (path.endsWith(".swf")) { mime = "application/x-shockwave-flash"; }
        else if (path.endsWith(".json")) { mime = "application/json"; charset = "UTF-8"; }
        else if (path.endsWith(".css")) { mime = "text/css"; charset = "UTF-8"; }
        else if (path.endsWith(".png")) { mime = "image/png"; }
        else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) { mime = "image/jpeg"; }
        else if (path.endsWith(".svg")) { mime = "image/svg+xml"; charset = "UTF-8"; }
        else if (path.endsWith(".wav")) { mime = "audio/wav"; }
        else if (path.endsWith(".mp3")) { mime = "audio/mpeg"; }
        try {
            InputStream data = getAssets().open(path);
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "max-age=31536000");
            headers.put("Cross-Origin-Resource-Policy", "same-origin");
            return new WebResourceResponse(mime, charset, 200, "OK", headers, data);
        } catch (Exception ex) {
            return reply(404, "Missing APK asset: " + path);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        saves = new SaveStore(this);
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        root.setMotionEventSplittingEnabled(true);
        game = new WebView(this);
        game.setBackgroundColor(Color.BLACK);
        game.setFocusable(true);
        game.setFocusableInTouchMode(true);
        WebSettings settings = game.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportZoom(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        game.addJavascriptInterface(saves, "AndroidSave");
        game.setWebChromeClient(new WebChromeClient());
        game.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return asset(request.getUrl());
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri url = request.getUrl();
                return !"https".equals(url.getScheme()) || !HOST.equalsIgnoreCase(url.getHost());
            }
        });

        // Leave dedicated touch-control gutters instead of overlaying the game.
        DisplayMetrics dm = getResources().getDisplayMetrics();
        float width = (float) dm.widthPixels / dm.density;
        float height = (float) dm.heightPixels / dm.density;
        int gutter = Math.max(156, Math.min(210, Math.round((width - height * 1.376f) / 2f)));
        FrameLayout.LayoutParams gameParams = new FrameLayout.LayoutParams(-1, -1);
        gameParams.leftMargin = dp(gutter);
        gameParams.rightMargin = dp(gutter);
        root.addView(game, gameParams);

        // Five-row? No: 4-way D-pad on the left, Z/X/C on the right.
        addKey("↑", KeyEvent.KEYCODE_DPAD_UP, false, 55, 80);
        addKey("←", KeyEvent.KEYCODE_DPAD_LEFT, false, 7, 26);
        addKey("↓", KeyEvent.KEYCODE_DPAD_DOWN, false, 55, 26);
        addKey("→", KeyEvent.KEYCODE_DPAD_RIGHT, false, 103, 26);
        addKey("Z", KeyEvent.KEYCODE_Z, true, 83, 85);
        addKey("X", KeyEvent.KEYCODE_X, true, 29, 85);
        addKey("C", KeyEvent.KEYCODE_C, true, 56, 31);

        Button saveButton = createButton("存档", 15, 0xA030384B, 0xAA92A2BA);
        FrameLayout.LayoutParams saveParams = new FrameLayout.LayoutParams(dp(72), dp(42), Gravity.RIGHT | Gravity.TOP);
        saveParams.topMargin = dp(12);
        saveParams.rightMargin = dp(14);
        root.addView(saveButton, saveParams);
        saveButton.setOnClickListener(v -> showSaveMenu());

        setContentView(root);
        game.loadUrl(ENTRY);
        game.requestFocus();
    }

    private Button createButton(String text, int textSize, int bgColor, int outline) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextColor(Color.WHITE);
        b.setTextSize(textSize);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setFocusable(false);
        b.setHapticFeedbackEnabled(false);
        b.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(13));
        bg.setStroke(dp(1), outline);
        b.setBackground(bg);
        return b;
    }

    private void addKey(String text, int key, boolean right, int side, int bottom) {
        Button b = createButton(text, 20, 0xA030384B, 0xAA90A1BA);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                dp(46), dp(46), Gravity.BOTTOM | (right ? Gravity.RIGHT : Gravity.LEFT));
        if (right) p.rightMargin = dp(side); else p.leftMargin = dp(side);
        p.bottomMargin = dp(bottom);
        b.setOnTouchListener((v, ev) -> {
            int a = ev.getActionMasked();
            if (a == MotionEvent.ACTION_DOWN) {
                game.requestFocus();
                game.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, key));
                v.setAlpha(.65f);
                return true;
            }
            if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) {
                game.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, key));
                v.setAlpha(1f);
                return true;
            }
            return true;
        });
        root.addView(b, p);
    }

    private void snapshot(Runnable next) {
        if (game == null) { next.run(); return; }
        game.evaluateJavascript("window.WitchSave && window.WitchSave.snapshot(true)", r -> next.run());
    }

    private void showSaveMenu() {
        String[] actions = {"立即备份现有存档", "导出存档到文件", "从文件导入存档", "重新启动游戏"};
        new AlertDialog.Builder(this)
            .setTitle("Witch Girl · 存档")
            .setItems(actions, (dialog, which) -> {
                switch (which) {
                    case 0:
                        snapshot(() -> toast("游戏存档已备份到应用私有目录"));
                        break;
                    case 1:
                        snapshot(this::createExportDocument);
                        break;
                    case 2:
                        importDocument();
                        break;
                    case 3:
                        snapshot(() -> game.reload());
                        break;
                }
            })
            .setNegativeButton("取消", null)
            .show();
    }

    private void createExportDocument() {
        if (saves.load().isEmpty()) {
            toast("没有发现可导出的存档");
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "WitchGirl-Save.json");
        startActivityForResult(intent, EXPORT_SAVE);
    }

    private void importDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        startActivityForResult(intent, IMPORT_SAVE);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (request == EXPORT_SAVE) {
                String backup = saves.load();
                if (backup.isEmpty()) { toast("当前存档为空"); return; }
                try (OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new Exception("Unable to open output file");
                    out.write(backup.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                }
                toast("存档已导出。重装前请保留这个 JSON 文件");
            } else if (request == IMPORT_SAVE) {
                String json;
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if (in == null) throw new Exception("Unable to open backup");
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    byte[] chunk = new byte[8192];
                    int n;
                    while ((n = in.read(chunk)) != -1) {
                        output.write(chunk, 0, n);
                        if (output.size() > 4 * 1024 * 1024) throw new Exception("Backup too large");
                    }
                    json = output.toString("UTF-8");
                }
                if (!saves.saveChecked(json)) throw new Exception("Invalid backup format");
                String expression = "window.WitchSave && window.WitchSave.importBackup(" + JSONObject.quote(json) + ")";
                game.evaluateJavascript(expression, answer -> {
                    if ("true".equals(answer)) {
                        toast("存档已导入，正在重新加载游戏");
                        game.reload();
                    } else {
                        toast("导入到 Flash 存储失败");
                    }
                });
            }
        } catch (Exception error) {
            toast("存档操作失败：" + error.getMessage());
        }
    }

    @Override
    protected void onPause() {
        if (game != null) {
            game.evaluateJavascript("window.WitchSave && window.WitchSave.snapshot(true)", null);
            game.onPause();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (game != null) game.onResume();
    }

    @Override
    protected void onDestroy() {
        if (game != null) game.destroy();
        super.onDestroy();
    }
}
