package com.nlkashei.witchgirl;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
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

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Offline-only Ruffle player; all assets are served from the APK via
 * the synthetic appassets.androidplatform.net HTTPS origin.
 *
 * No INTERNET permission is requested. External requests are rejected.
 */
public class MainActivity extends Activity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String ENTRY = "https://" + HOST + "/assets/index.html";
    private FrameLayout root;
    private WebView game;

    private int dp(float n) {
        return (int) (n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private WebResourceResponse reply(int code, String message) {
        return new WebResourceResponse("text/plain", "UTF-8", code, message,
                new HashMap<>(), new ByteArrayInputStream(message.getBytes()));
    }

    private WebResourceResponse asset(Uri uri) {
        if (!HOST.equalsIgnoreCase(uri.getHost())
                || !"https".equalsIgnoreCase(uri.getScheme())) {
            return reply(403, "Offline only");
        }
        String path = uri.getPath();
        if (path == null || !path.startsWith("/assets/")) {
            return reply(404, "Not found");
        }
        path = path.substring("/assets/".length());
        if (path.isEmpty()) path = "index.html";
        if (path.contains("..") || path.startsWith("/")) {
            return reply(403, "Forbidden");
        }
        String mime = "application/octet-stream";
        String charset = null;
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
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

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

        game.setWebChromeClient(new WebChromeClient());
        game.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return asset(request.getUrl());
            }
        });
        root.addView(game, new FrameLayout.LayoutParams(-1, -1));

        // Only seven keys are used by the target game.
        addKey("↑", KeyEvent.KEYCODE_DPAD_UP, false, 69, 128);
        addKey("←", KeyEvent.KEYCODE_DPAD_LEFT, false, 12, 70);
        addKey("↓", KeyEvent.KEYCODE_DPAD_DOWN, false, 69, 12);
        addKey("→", KeyEvent.KEYCODE_DPAD_RIGHT, false, 126, 70);
        addKey("Z", KeyEvent.KEYCODE_Z, true, 134, 20);
        addKey("X", KeyEvent.KEYCODE_X, true, 72, 78);
        addKey("C", KeyEvent.KEYCODE_C, true, 10, 20);

        setContentView(root);
        game.loadUrl(ENTRY);
        game.requestFocus();
    }

    private void addKey(String text, int code, boolean fromRight, int side, int bottom) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextColor(Color.WHITE);
        button.setTextSize(20);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT_BOLD);
        button.setFocusable(false);
        button.setHapticFeedbackEnabled(false);
        button.setPadding(0, 0, 0, 0);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0x9630384B);
        bg.setCornerRadius(dp(15));
        bg.setStroke(dp(1), 0xAA90A1BA);
        button.setBackground(bg);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                dp(56), dp(56), Gravity.BOTTOM | (fromRight ? Gravity.RIGHT : Gravity.LEFT));
        if (fromRight) p.rightMargin = dp(side);
        else p.leftMargin = dp(side);
        p.bottomMargin = dp(bottom);
        button.setOnTouchListener((view, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                game.requestFocus();
                game.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, code));
                view.setAlpha(0.65f);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                game.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, code));
                view.setAlpha(1f);
                return true;
            }
            return true;
        });
        root.addView(button, p);
    }

    @Override
    protected void onPause() {
        if (game != null) game.onPause();
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
