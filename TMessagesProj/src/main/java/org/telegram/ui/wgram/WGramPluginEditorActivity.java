package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.AtomicFile;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.wgram.WGramPlugins;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/** Edits trusted Python source; validating, saving and exporting never execute it. */
public final class WGramPluginEditorActivity extends BaseFragment {
    private final String pluginId;
    private final AtomicFile draft;
    private EditText code;
    private TextView status;
    private String source, exportSource;
    private boolean busy, installed;
    private final Runnable draftSave = this::saveDraft;
    public WGramPluginEditorActivity(int account) { this(account, null); }
    public WGramPluginEditorActivity(int account, String id) {
        setCurrentAccount(account);
        if (id != null && !id.matches("[A-Za-z][A-Za-z0-9_-]{1,31}")) throw new IllegalArgumentException("Invalid plugin ID");
        pluginId = id;
        draft = new AtomicFile(new File(new File(ApplicationLoader.getFilesDirFixed(), "wgram_plugin_drafts"),
            id == null ? "new.py" : "edit_" + id + ".py"));
    }
    @Override public View createView(Context context) {
        actionBar.setTitle(getString(R.string.WGramPluginEditor));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        status = new TextView(context);
        status.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        status.setTextSize(13);
        status.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        status.setText(getString(R.string.WGramPluginDraftInfo));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));
        code = new EditText(context);
        code.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        code.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        code.setTypeface(Typeface.MONOSPACE);
        code.setTextSize(13);
        code.setGravity(Gravity.TOP | Gravity.START);
        code.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        code.setHorizontallyScrolling(true);
        code.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8), AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        code.setFilters(new InputFilter[]{new InputFilter.LengthFilter(1024 * 1024)});
        code.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override public void afterTextChanged(Editable text) {
                source = text.toString();
                AndroidUtilities.cancelRunOnUIThread(draftSave);
                AndroidUtilities.runOnUIThread(draftSave, 500);
            }
        });
        root.addView(code, new LinearLayout.LayoutParams(-1, 0, 1));
        LinearLayout actions = new LinearLayout(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addButton(context, actions, getString(R.string.WGramPluginValidate), () -> validate(false));
        addButton(context, actions, getString(R.string.Save), () -> validate(true));
        addButton(context, actions, getString(R.string.WGramPluginExport), this::export);
        root.addView(actions, new LinearLayout.LayoutParams(-1, -2));
        fragmentView = root;
        if (source != null) code.setText(source); else loadSource();
        return root;
    }
    private void addButton(Context context, LinearLayout row, String label, Runnable action) {
        Button button = new Button(context);
        button.setText(label); button.setAllCaps(false); button.setTextSize(13);
        button.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText));
        button.setOnClickListener(v -> { if (!busy && source != null) action.run(); });
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
    }
    private void working(boolean value) { busy = value; if (code != null) code.setEnabled(!value); }
    private void problem(String message) {
        if (isFinished) return;
        working(false);
        status.setText(getString(R.string.WGramPluginFailed) + "\n" + message);
    }
    private void showSource(String text) {
        if (isFinished) return;
        working(false); source = text; code.setText(text); code.setSelection(0);
    }
    private void loadSource() {
        working(true);
        Utilities.globalQueue.postRunnable(() -> {
            String text = null;
            try (InputStream input = draft.openRead(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] bytes = new byte[8192]; int count;
                while ((count = input.read(bytes)) != -1) {
                    if (output.size() + count > 4 * 1024 * 1024) throw new IllegalArgumentException("Draft too large");
                    output.write(bytes, 0, count);
                }
                text = new String(output.toByteArray(), StandardCharsets.UTF_8);
            } catch (java.io.FileNotFoundException ignored) {
            } catch (Exception e) { FileLog.e(e); }
            final String saved = text;
            AndroidUtilities.runOnUIThread(() -> {
                if (isFinished) return;
                if (saved != null) { showSource(saved); return; }
                WGramPlugins.Result result = (json, error) -> {
                    if (isFinished) return;
                    if (error != null) { problem(error); return; }
                    try { showSource(new JSONArray("[" + json + "]").getString(0)); }
                    catch (Exception e) { problem(e.toString()); }
                };
                if (pluginId == null) WGramPlugins.execute("new_source", result);
                else WGramPlugins.execute("source", result, pluginId);
            });
        });
    }
    private void saveDraft() {
        if (source == null || installed) return;
        final byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
        Utilities.globalQueue.postRunnable(() -> {
            FileOutputStream output = null;
            try {
                draft.getBaseFile().getParentFile().mkdirs();
                output = draft.startWrite(); output.write(bytes); draft.finishWrite(output);
            } catch (Exception e) {
                if (output != null) draft.failWrite(output);
                FileLog.e(e);
                AndroidUtilities.runOnUIThread(() -> problem(getString(R.string.WGramPluginDraftFailed)));
            }
        });
    }
    private void validate(boolean save) {
        final String selected = source;
        working(true);
        WGramPlugins.execute("inspect_source", (json, error) -> {
            if (isFinished) return;
            working(false);
            if (error != null) { problem(error); return; }
            if (!save) { status.setText(getString(R.string.WGramPluginValid)); return; }
            try {
                JSONObject info = new JSONObject(json);
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(info.getString("name"))
                    .setMessage(getString(R.string.WGramPluginInstallInfo))
                    .setPositiveButton(getString(R.string.Save), (d, w) -> {
                        working(true);
                        WGramPlugins.execute("install_source", (result, failure) -> {
                            if (failure != null) { problem(failure); return; }
                            installed = true;
                            AndroidUtilities.cancelRunOnUIThread(draftSave);
                            Utilities.globalQueue.postRunnable(draft::delete);
                            if (!isFinished) {
                                android.widget.Toast.makeText(getParentActivity(), getString(R.string.WGramPluginSaved), android.widget.Toast.LENGTH_LONG).show();
                                finishFragment();
                            }
                        }, selected, info.optString("sha256"), pluginId);
                    }).setNegativeButton(getString(R.string.Cancel), null).create());
            } catch (Exception e) { problem(e.toString()); }
        }, selected);
    }
    private void export() {
        exportSource = source;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("text/x-python");
        intent.putExtra(Intent.EXTRA_TITLE, (pluginId == null ? "my_plugin" : pluginId) + ".py");
        try { startActivityForResult(intent, 2405); }
        catch (Exception e) { exportSource = null; problem(e.toString()); }
    }
    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != 2405) return;
        final String selected = exportSource; exportSource = null;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null || selected == null) return;
        working(true);
        Utilities.globalQueue.postRunnable(() -> {
            String failure = null;
            try (OutputStream output = ApplicationLoader.applicationContext.getContentResolver().openOutputStream(data.getData(), "wt")) {
                if (output == null) throw new IllegalArgumentException("Cannot open destination");
                output.write(selected.getBytes(StandardCharsets.UTF_8));
            } catch (Exception e) { failure = e.toString(); }
            final String error = failure;
            AndroidUtilities.runOnUIThread(() -> {
                if (isFinished) return;
                working(false);
                if (error != null) problem(error); else status.setText(getString(R.string.WGramPluginExported));
            });
        });
    }
    @Override public void onPause() {
        AndroidUtilities.cancelRunOnUIThread(draftSave); saveDraft(); super.onPause();
    }
    @Override public void onFragmentDestroy() {
        AndroidUtilities.cancelRunOnUIThread(draftSave); saveDraft(); super.onFragmentDestroy();
    }
}
