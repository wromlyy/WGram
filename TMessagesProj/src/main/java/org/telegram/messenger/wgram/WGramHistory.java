package org.telegram.messenger.wgram;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Text-only, opt-in history. Called exclusively on the account storage queue. */
public final class WGramHistory {
    public static final class Entry {
        public long dialog, sender, time;
        public int id, kind;
        public String text;
    }
    private WGramHistory() {}
    private static File file(long user) {
        return new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "wgram_history_" + user + ".db");
    }
    private static SQLiteDatabase open(long user) {
        SQLiteDatabase db = SQLiteDatabase.openOrCreateDatabase(file(user), null);
        db.execSQL("PRAGMA secure_delete=ON");
        db.execSQL("CREATE TABLE IF NOT EXISTS live (dialog INTEGER, mid INTEGER, channel INTEGER, sender INTEGER, text TEXT, time INTEGER, PRIMARY KEY(dialog,mid))");
        db.execSQL("CREATE TABLE IF NOT EXISTS history (seq INTEGER PRIMARY KEY AUTOINCREMENT, dialog INTEGER, mid INTEGER, sender INTEGER, text TEXT, kind INTEGER, time INTEGER)");
        return db;
    }
    private static void prune(SQLiteDatabase db, int account) {
        long cutoff = WGramHistoryPolicy.cutoff(System.currentTimeMillis()/1000, WGramConfig.getHistoryDays(account));
        for (String table : new String[]{"live", "history"}) {
            db.delete(table, "time < ?", new String[]{Long.toString(cutoff)});
            db.execSQL("DELETE FROM " + table + " WHERE rowid NOT IN (SELECT rowid FROM " + table + " ORDER BY time DESC, rowid DESC LIMIT " + WGramHistoryPolicy.MAX_ROWS + ")");
        }
    }
    private static void archive(SQLiteDatabase db, Cursor cursor, int kind) {
        ContentValues values = new ContentValues();
        values.put("dialog", cursor.getLong(0)); values.put("mid", cursor.getInt(1));
        values.put("sender", cursor.getLong(2)); values.put("text", cursor.getString(3));
        values.put("kind", kind); values.put("time", System.currentTimeMillis()/1000);
        db.insertOrThrow("history", null, values);
    }
    public static void observe(int account, List<TLRPC.Message> messages) {
        long user = UserConfig.getInstance(account).getClientUserId();
        if (user == 0 || !WGramConfig.isHistoryEnabled(account) || messages == null || messages.isEmpty()) return;
        try (SQLiteDatabase db = open(user)) {
            db.beginTransaction();
            try {
                for (TLRPC.Message m : messages) {
                    long dialog = MessageObject.getDialogId(m);
                    TLRPC.Chat chat = dialog < 0 ? MessagesController.getInstance(account).getChat(-dialog) : null;
                    boolean eligible = WGramHistoryPolicy.canKeep(dialog, m.id, m.message,
                        DialogObject.isEncryptedDialog(dialog) || m instanceof TLRPC.TL_message_secret,
                        m.ttl_period, m.media == null ? 0 : m.media.ttl_seconds,
                        m.noforwards || (dialog < 0 && chat == null) || (chat != null && chat.noforwards), MessageObject.isEphemeralAndNotWelcome(m));
                    if (!eligible) {
                        // A server edit may turn a formerly ordinary message into expiring content.
                        db.delete("live", "dialog=? AND mid=?", new String[]{""+dialog,""+m.id});
                        db.delete("history", "dialog=? AND mid=?", new String[]{""+dialog,""+m.id});
                        continue;
                    }
                    String text = WGramHistoryPolicy.clip(m.message);
                    try (Cursor c = db.rawQuery("SELECT dialog,mid,sender,text FROM live WHERE dialog=? AND mid=?", new String[]{""+dialog,""+m.id})) {
                        if (c.moveToFirst() && !text.equals(c.getString(3))) archive(db, c, 2);
                    }
                    ContentValues v = new ContentValues();
                    v.put("dialog", dialog); v.put("mid", m.id); v.put("channel", m.peer_id != null && m.peer_id.channel_id != 0 ? 1 : 0);
                    v.put("sender", m.from_id == null ? 0 : DialogObject.getPeerDialogId(m.from_id));
                    v.put("text", text); v.put("time", System.currentTimeMillis()/1000);
                    db.insertWithOnConflict("live", null, v, SQLiteDatabase.CONFLICT_REPLACE);
                }
                prune(db, account); db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (Exception e) { FileLog.e(e); }
    }
    public static void deleted(int account, long dialog, List<Integer> ids, int maxId) {
        long user = UserConfig.getInstance(account).getClientUserId();
        if (user == 0 || !WGramConfig.isHistoryEnabled(account) || !file(user).exists()) return;
        try (SQLiteDatabase db = open(user)) {
            db.beginTransaction();
            try {
                prune(db, account);
                if (maxId > 0) move(db, "dialog=? AND mid<=?", new String[]{""+dialog,""+maxId});
                else if (ids != null) for (Integer id : ids) {
                    String where = dialog == 0 ? "channel=0 AND mid=?" : "dialog=? AND mid=?";
                    String[] args = dialog == 0 ? new String[]{""+id} : new String[]{""+dialog,""+id};
                    move(db, where, args);
                }
                prune(db, account); db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (Exception e) { FileLog.e(e); }
    }
    private static void move(SQLiteDatabase db, String where, String[] args) {
        try (Cursor c = db.rawQuery("SELECT dialog,mid,sender,text FROM live WHERE " + where, args)) {
            while (c.moveToNext()) archive(db, c, 1);
        }
        db.delete("live", where, args);
    }
    public static ArrayList<Entry> list(int account, long dialog, String query, int filter) {
        ArrayList<Entry> result = new ArrayList<>();
        long user = UserConfig.getInstance(account).getClientUserId();
        if (user == 0 || !file(user).exists()) return result;
        try (SQLiteDatabase db = open(user)) {
            prune(db, account);
            ArrayList<String> args = new ArrayList<>(); String where = "1=1";
            if (dialog != 0) { where += " AND dialog=?"; args.add(""+dialog); }
            if (filter != 0) { where += " AND kind=?"; args.add(""+filter); }
            if (query != null && !query.isEmpty()) { where += " AND instr(lower(text),lower(?))>0"; args.add(query); }
            try (Cursor c = db.rawQuery("SELECT dialog,mid,sender,text,kind,time FROM history WHERE " + where + " ORDER BY seq DESC LIMIT 200", args.toArray(new String[0]))) {
                while (c.moveToNext()) {
                    Entry e = new Entry(); e.dialog=c.getLong(0); e.id=c.getInt(1); e.sender=c.getLong(2);
                    e.text=c.getString(3); e.kind=c.getInt(4); e.time=c.getLong(5); result.add(e);
                }
            }
        } catch (Exception e) { FileLog.e(e); }
        return result;
    }
    public static void clear(long user) {
        if (user == 0) return;
        SQLiteDatabase.deleteDatabase(file(user));
    }
}
