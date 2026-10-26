package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.app.Activity;
import android.content.Intent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.wgram.WGramPlugins;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.io.File;
import java.util.ArrayList;

public final class WGramPluginsActivity extends UniversalFragment {
    private JSONArray plugins = new JSONArray();
    private boolean busy;
    private String error;
    public WGramPluginsActivity(int account) { setCurrentAccount(account); }
    @Override protected CharSequence getTitle() { return getString(R.string.WGramPlugins); }
    private void refresh() { if (listView != null) listView.adapter.update(true); }
    private void reload() {
        busy = true; refresh();
        WGramPlugins.execute("list_plugins", (json, failure) -> {
            busy = false; error = failure;
            if (failure == null) {
                try { plugins = new JSONArray(json); } catch (Exception e) { error = e.getMessage(); }
            }
            refresh();
        });
    }
    @Override public void onResume() { super.onResume(); reload(); }
    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCheck(1, getString(R.string.WGramPluginEngine)).setChecked(WGramPlugins.enabled()));
        items.add(UItem.asShadow(getString(R.string.WGramPluginTrust)));
        items.add(UItem.asButton(2, R.drawable.msg_add, getString(R.string.WGramPluginImport), busy ? getString(R.string.Loading) : ""));
        items.add(UItem.asShadow(getString(R.string.WGramPluginFormat)));
        if (error != null) items.add(UItem.asShadow(getString(R.string.WGramPluginFailed) + "\n" + error));
        items.add(UItem.asHeader(getString(R.string.WGramPluginInstalled)));
        if (plugins.length() == 0 && !busy) items.add(UItem.asShadow(getString(R.string.WGramPluginEmpty)));
        for (int i = 0; i < plugins.length(); i++) {
            JSONObject info = plugins.optJSONObject(i);
            if (info == null) continue;
            String status = getString(info.optBoolean("running") ? R.string.WGramOn : R.string.WGramOff);
            items.add(UItem.asButton(100 + i, info.optString("name"), status));
            String detail = info.optString("version") + " · " + info.optString("author") + "\n" + info.optString("description");
            if (!info.optString("error").isEmpty()) detail += "\n" + info.optString("error");
            items.add(UItem.asShadow(detail));
        }
    }
    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        if (busy) return;
        if (item.id == 1) {
            if (WGramPlugins.enabled()) { master(false); return; }
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramPluginEngine))
                .setMessage(getString(R.string.WGramPluginTrust)).setPositiveButton(getString(R.string.Enable), (d,w) -> master(true))
                .setNegativeButton(getString(R.string.Cancel), null).create());
        } else if (item.id == 2) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
            try { startActivityForResult(intent, 2404); }
            catch (Exception e) { WGramUi.info(this, getString(R.string.WGramPluginFailed)); }
        } else if (item.id >= 100 && item.id - 100 < plugins.length()) {
            JSONObject info = plugins.optJSONObject(item.id - 100);
            if (info == null) return;
            boolean enabled = info.optBoolean("enabled");
            showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(info.optString("name"))
                .setItems(new CharSequence[]{getString(enabled ? R.string.WGramPluginDisable : R.string.WGramPluginEnable), getString(R.string.Delete)}, (d,w) -> {
                    if (w == 0) {
                        if (enabled) change("enable", info.optString("id"), false);
                        else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(info.optString("name"))
                            .setMessage(getString(R.string.WGramPluginTrust)).setPositiveButton(getString(R.string.Enable), (dd,ww) -> change("enable", info.optString("id"), true))
                            .setNegativeButton(getString(R.string.Cancel), null).create());
                    } else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.Delete))
                        .setMessage(getString(R.string.WGramPluginDeleteQuestion)).setPositiveButton(getString(R.string.Delete), (dd,ww) -> change("remove", info.optString("id")))
                        .setNegativeButton(getString(R.string.Cancel), null).create());
                }).create());
        }
    }
    private void master(boolean value) {
        busy = true; refresh();
        WGramPlugins.setEnabled(value, (json, failure) -> {
            if (failure != null) WGramUi.info(this, getString(R.string.WGramPluginFailed) + "\n" + failure);
            reload();
        });
    }
    private void change(String operation, Object... args) {
        busy = true; refresh();
        WGramPlugins.execute(operation, (json, failure) -> {
            if (failure != null) WGramUi.info(this, getString(R.string.WGramPluginFailed) + "\n" + failure);
            reload();
        }, args);
    }
    @Override public void onActivityResultFragment(int requestCode, int resultCode, Intent data) {
        if (requestCode != 2404 || resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return;
        busy = true; refresh();
        WGramPlugins.inspect(data.getData(), (json, failure) -> {
            busy = false; refresh();
            if (failure != null) { WGramUi.info(this, getString(R.string.WGramPluginFailed) + "\n" + failure); return; }
            try {
                JSONObject info = new JSONObject(json);
                File staged = new File(info.getString("staged"));
                if (getParentActivity() == null) { staged.delete(); return; }
                AlertDialog dialog = new AlertDialog.Builder(getParentActivity()).setTitle(info.getString("name"))
                    .setMessage(info.optString("version") + " · " + info.optString("author") + "\n\n" + info.optString("description") + "\n\n" + getString(R.string.WGramPluginInstallInfo))
                    .setPositiveButton(getString(R.string.WGramPluginImport), (d,w) -> {
                        busy = true; refresh();
                        WGramPlugins.execute("install", (result, installError) -> {
                            staged.delete();
                            if (installError != null) WGramUi.info(this, getString(R.string.WGramPluginFailed) + "\n" + installError);
                            reload();
                        }, staged.getAbsolutePath(), info.optString("sha256"));
                    }).setNegativeButton(getString(R.string.Cancel), (d,w) -> staged.delete()).create();
                dialog.setOnCancelListener(d -> staged.delete());
                showDialog(dialog);
            } catch (Exception e) { FileLog.e(e); WGramUi.info(this, getString(R.string.WGramPluginFailed)); }
        });
    }
    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
}
