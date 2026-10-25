package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.messenger.wgram.WGramHistory;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

public final class WGramHistoryActivity extends UniversalFragment {
    private ArrayList<WGramHistory.Entry> entries = new ArrayList<>();
    private String query = "";
    private int filter;
    private final long dialog;
    private int generation;
    public WGramHistoryActivity(int account, long dialog) { setCurrentAccount(account); this.dialog=dialog; }
    @Override protected CharSequence getTitle() { return getString(R.string.WGramHistory); }
    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asButton(1,getString(R.string.Search),query));
        items.add(UItem.asButton(2,getString(R.string.WGramHistoryFilter),filterName()));
        items.add(UItem.asShadow(getString(R.string.WGramHistoryInfo)));
        if (entries.isEmpty()) items.add(UItem.asShadow(getString(R.string.WGramHistoryEmpty)));
        for (WGramHistory.Entry e : entries) {
            TextView text = new TextView(getContext());
            text.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            text.setTextSize(15); text.setPadding(AndroidUtilities.dp(20),AndroidUtilities.dp(14),AndroidUtilities.dp(20),AndroidUtilities.dp(14));
            text.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            String title = title(e.dialog) + " · " + getString(e.kind==1?R.string.WGramDeleted:R.string.WGramEdited);
            String date = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.MEDIUM).format(new Date(e.time*1000));
            text.setText(title + "\n" + date + "\n\n" + e.text);
            text.setOnClickListener(v -> showEntry(e));
            text.setOnLongClickListener(v -> { AndroidUtilities.addToClipboard(e.text); return true; });
            items.add(UItem.asCustom(text));
            items.add(UItem.asShadow(null));
        }
    }
    private String title(long id) {
        if (id>0 && getMessagesController().getUser(id)!=null) return UserObject.getUserName(getMessagesController().getUser(id));
        if (id<0 && getMessagesController().getChat(-id)!=null) return getMessagesController().getChat(-id).title;
        return "ID " + id;
    }
    private String filterName() { return getString(filter==1?R.string.WGramDeleted:filter==2?R.string.WGramEdited:R.string.WGramAll); }
    private void showEntry(WGramHistory.Entry e) {
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(title(e.dialog)).setMessage(e.text)
            .setPositiveButton(getString(R.string.Copy), (d,w)->AndroidUtilities.addToClipboard(e.text))
            .setNeutralButton(getString(R.string.WGramOpenChat), (d,w)-> {
                Bundle args = new Bundle();
                if (e.dialog>0) args.putLong("user_id",e.dialog); else args.putLong("chat_id",-e.dialog);
                ChatActivity chat = new ChatActivity(args); chat.setCurrentAccount(currentAccount); presentFragment(chat);
            }).setNegativeButton(getString(R.string.Close),null).create());
    }
    private void load() {
        final int request = ++generation;
        final String q = query; final int f = filter;
        final long user = getUserConfig().getClientUserId();
        getMessagesStorage().getStorageQueue().postRunnable(()-> {
            ArrayList<WGramHistory.Entry> data = WGramHistory.list(currentAccount,dialog,q,f);
            AndroidUtilities.runOnUIThread(()-> {
                if (request != generation || user != getUserConfig().getClientUserId()) return;
                entries=data; if(listView!=null) listView.adapter.update(true);
            });
        });
    }
    @Override protected void onClick(UItem item, View view,int position,float x,float y) {
        if(item.id==1) WGramUi.input(this,getString(R.string.Search),query,false,100,t->{query=t;load();});
        if(item.id==2) showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramHistoryFilter))
            .setItems(new CharSequence[]{getString(R.string.WGramAll),getString(R.string.WGramDeleted),getString(R.string.WGramEdited)},(d,w)->{filter=w;load();}).create());
    }
    @Override public void onResume(){super.onResume();load();}
    @Override public void onFragmentDestroy(){generation++;super.onFragmentDestroy();}
    @Override protected boolean onLongClick(UItem item,View view,int position,float x,float y){return false;}
}
