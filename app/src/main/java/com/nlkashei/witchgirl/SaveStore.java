package com.nlkashei.witchgirl;

import android.content.Context;
import android.util.AtomicFile;
import android.webkit.JavascriptInterface;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

/** Native, atomic, app-private backup of Ruffle localStorage shared objects. */
public class SaveStore {
    private static final int MAX_SAVE_BYTES = 4 * 1024 * 1024;
    private final AtomicFile file;
    private final Object lock = new Object();

    public SaveStore(Context context) {
        file = new AtomicFile(new File(context.getFilesDir(), "witchgirl-flash-save.json"));
    }

    @JavascriptInterface
    public String load() {
        synchronized (lock) {
            if (!file.getBaseFile().exists()) return "";
            try (InputStream in = file.openRead()) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    if (output.size() > MAX_SAVE_BYTES) return "";
                }
                return output.toString("UTF-8");
            } catch (Exception e) {
                return "";
            }
        }
    }

    @JavascriptInterface
    public void save(String content) {
        saveChecked(content);
    }

    public boolean saveChecked(String content) {
        if (content == null) return false;
        byte[] data = content.getBytes(StandardCharsets.UTF_8);
        if (data.length < 20 || data.length > MAX_SAVE_BYTES) return false;
        try {
            JSONObject obj = new JSONObject(content);
            if (obj.optInt("version", 0) != 1 || obj.optJSONObject("items") == null) return false;
            for (java.util.Iterator<String> it = obj.getJSONObject("items").keys(); it.hasNext();) {
                String key = it.next();
                if (!(obj.getJSONObject("items").opt(key) instanceof String)) return false;
            }
        } catch (Exception e) {
            return false;
        }
        synchronized (lock) {
            FileOutputStream out = null;
            try {
                out = file.startWrite();
                out.write(data);
                file.finishWrite(out);
                return true;
            } catch (Exception e) {
                if (out != null) file.failWrite(out);
                return false;
            }
        }
    }
}
