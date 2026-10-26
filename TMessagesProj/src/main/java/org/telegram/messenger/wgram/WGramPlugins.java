package org.telegram.messenger.wgram;

import android.content.Context;
import android.net.Uri;
import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.DialogObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** Trusted, opt-in code running with the application's permissions. */
public final class WGramPlugins {
    private static PyObject module;
    private static final java.util.concurrent.ExecutorService executor = java.util.concurrent.Executors.newSingleThreadExecutor(r -> new Thread(r, "WGramPlugins"));
    public interface Result { void done(String json, String error); }
    private WGramPlugins() {}

    public static boolean enabled() {
        return ApplicationLoader.applicationContext.getSharedPreferences("wgram_plugins", Context.MODE_PRIVATE)
            .getBoolean("enabled", false);
    }
    private static void storeEnabled(boolean value) {
        ApplicationLoader.applicationContext.getSharedPreferences("wgram_plugins", Context.MODE_PRIVATE)
            .edit().putBoolean("enabled", value).commit();
    }
    private static synchronized PyObject runtime() {
        if (module == null) {
            if (!Python.isStarted()) Python.start(new AndroidPlatform(ApplicationLoader.applicationContext));
            PyObject candidate = Python.getInstance().getModule("wgram_plugins");
            candidate.callAttr("initialize", new File(ApplicationLoader.getFilesDirFixed(), "wgram_plugins").getAbsolutePath(), enabled());
            module = candidate;
        }
        return module;
    }
    public static void start() {
        if (enabled()) execute("list_plugins", (json, error) -> {});
    }
    public static void setEnabled(boolean value, Result result) {
        executor.execute(() -> {
            try {
                synchronized (WGramPlugins.class) {
                    runtime().callAttr("call", "set_master", value);
                    storeEnabled(value);
                }
                AndroidUtilities.runOnUIThread(() -> result.done("null", null));
            } catch (Throwable error) { fail(error, result); }
        });
    }
    public static void execute(String operation, Result result, Object... arguments) {
        executor.execute(() -> {
            try {
                Object[] parameters = new Object[arguments.length + 1];
                parameters[0] = operation;
                System.arraycopy(arguments, 0, parameters, 1, arguments.length);
                final String json;
                synchronized (WGramPlugins.class) {
                    json = runtime().callAttr("call", parameters).toString();
                }
                AndroidUtilities.runOnUIThread(() -> result.done(json, null));
            } catch (Throwable error) { fail(error, result); }
        });
    }
    private static void fail(Throwable error, Result result) {
        FileLog.e(error);
        String message = error.getMessage();
        final String detail = message == null ? error.getClass().getSimpleName() : message.substring(0, Math.min(message.length(), 1200));
        AndroidUtilities.runOnUIThread(() -> result.done(null, detail));
    }
    public static void inspect(Uri uri, Result result) {
        executor.execute(() -> {
            File staged = null;
            try {
                staged = File.createTempFile("wgram-plugin-", ".py", ApplicationLoader.applicationContext.getCacheDir());
                try (InputStream input = ApplicationLoader.applicationContext.getContentResolver().openInputStream(uri);
                     FileOutputStream output = new FileOutputStream(staged)) {
                    if (input == null) throw new IllegalArgumentException("Cannot open plugin");
                    byte[] buffer = new byte[8192];
                    int count, total = 0;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > 1024 * 1024) throw new IllegalArgumentException("Plugin is larger than 1 MB");
                        output.write(buffer, 0, count);
                    }
                }
                final JSONObject info;
                synchronized (WGramPlugins.class) {
                    info = new JSONObject(runtime().callAttr("call", "inspect", staged.getAbsolutePath()).toString());
                }
                info.put("staged", staged.getAbsolutePath());
                AndroidUtilities.runOnUIThread(() -> result.done(info.toString(), null));
            } catch (Throwable error) {
                if (staged != null) staged.delete();
                fail(error, result);
            }
        });
    }
    private static void sendStopped() {
        AndroidUtilities.runOnUIThread(() -> android.widget.Toast.makeText(ApplicationLoader.applicationContext,
            org.telegram.messenger.LocaleController.getString(org.telegram.messenger.R.string.WGramPluginSendStopped), android.widget.Toast.LENGTH_LONG).show());
    }
    /** Only new ordinary text messages; no retries, secret chats or captions. */
    public static boolean beforeSend(int account, SendMessagesHelper.SendMessageParams params) {
        if (!enabled() || params.message == null || params.retryMessageObject != null
                || params.caption != null || params.ttl != 0 || params.photo != null || params.document != null
                || params.location != null || params.poll != null || params.todo != null || params.game != null
                || params.invoice != null || params.user != null || params.sendingStory != null
                || DialogObject.isEncryptedDialog(params.peer)) return true;
        try {
            java.util.concurrent.Future<JSONObject> task = executor.submit(() -> {
                synchronized (WGramPlugins.class) {
                    return new JSONObject(runtime().callAttr("call", "outgoing", account, params.peer, params.message).toString());
                }
            });
            final JSONObject result;
            try {
                result = task.get(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException timeout) {
                task.cancel(true);
                throw timeout;
            }
            if (result.optBoolean("cancel")) { sendStopped(); return false; }
            String text = result.getString("message");
            if (!text.equals(params.message)) {
                params.message = text;
                // Entities and previews refer to the original text offsets/URL.
                params.entities = null;
                params.webPage = null;
                params.mediaWebPage = null;
            }
            return true;
        } catch (Throwable error) {
            FileLog.e(error);
            storeEnabled(false);
            sendStopped();
            return false;
        }
    }
}
