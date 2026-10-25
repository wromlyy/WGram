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
import java.util.HashSet;
import java.util.List;

/** Text-only, opt-in history. Database work runs on the account storage queue. */
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
        // This PRAGMA returns a row; Android rejects it through execSQL().
        try (Cursor pragma = db.rawQuery("PRAGMA secure_delete=ON", null)) {
            pragma.moveToFirst();
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS live (dialog INTEGER, mid INTEGER, channel INTEGER, sender INTEGER, text TEXT, time INTEGER, PRIMARY KEY(dialog,mid))");
        db.execSQL("CREATE TABLE IF NOT EXISTS history (seq INTEGER PRIMARY KEY AUTOINCREMENT, dialog INTEGER, mid INTEGER, sender INTEGER, text TEXT, kind INTEGER, time INTEGER)");
        if (db.getVersion() < 2) {
            db.beginTransaction();
            try {
                db.execSQL("ALTER TABLE live ADD COLUMN date INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE live ADD COLUMN outgoing INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE live ADD COLUMN topic INTEGER NOT NULL DEFAULT 0");
                db.execSQL("CREATE INDEX IF NOT EXISTS history_dialog_mid ON history(dialog,mid,kind)");
                db.setVersion(2);
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        db.execSQL("CREATE TABLE IF NOT EXISTS deleted (dialog INTEGER, mid INTEGER, sender INTEGER, text TEXT, time INTEGER, date INTEGER, outgoing INTEGER, topic INTEGER, PRIMARY KEY(dialog,mid))");
        return db;
    }
    private static void prune(SQLiteDatabase db, int account) {
        long cutoff = WGramHistoryPolicy.cutoff(System.currentTimeMillis()/1000, WGramConfig.getHistoryDays(account));
        for (String table : new String[]{"live", "history", "deleted"}) {
            db.delete(table, "time < ?", new String[]{Long.toString(cutoff)});
            db.execSQL("DELETE FROM " + table + " WHERE rowid NOT IN (SELECT rowid FROM " + table + " ORDER BY time DESC, rowid DESC LIMIT " + WGramHistoryPolicy.MAX_ROWS + ")");
        }
    }
    public static boolean eligible(int account, TLRPC.Message m) {
        if (m == null) return false;
        long dialog = MessageObject.getDialogId(m);
        TLRPC.Chat chat = dialog < 0 ? MessagesController.getInstance(account).getChat(-dialog) : null;
        return WGramHistoryPolicy.canKeep(dialog, m.id, m.message,
            DialogObject.isEncryptedDialog(dialog) || m instanceof TLRPC.TL_message_secret,
            m.ttl_period, m.media == null ? 0 : m.media.ttl_seconds,
            m.noforwards || (dialog < 0 && chat == null) || (chat != null && chat.noforwards), MessageObject.isEphemeralAndNotWelcome(m));
    }
    /** Live UI keeps ordinary text only; media captions are restored as text from the archive. */
    public static boolean canShowInline(int account, MessageObject object) {
        return WGramConfig.isHistoryEnabled(account) && object != null && eligible(account, object.messageOwner)
            && object.type == MessageObject.TYPE_TEXT && object.getGroupId() == 0;
    }
    private static void archive(SQLiteDatabase db, Cursor c, int kind) {
        ContentValues v = new ContentValues();
        v.put("dialog", c.getLong(0)); v.put("mid", c.getInt(1));
        v.put("sender", c.getLong(2)); v.put("text", c.getString(3));
        v.put("kind", kind); v.put("time", System.currentTimeMillis()/1000);
        db.insertOrThrow("history", null, v);
        if (kind == 1 && c.getInt(4) > 0) {
            v.remove("kind"); v.put("date", c.getInt(4)); v.put("outgoing", c.getInt(5)); v.put("topic", c.getInt(6));
            db.insertWithOnConflict("deleted", null, v, SQLiteDatabase.CONFLICT_REPLACE);
        }
    }
    public static void observe(int account, List<TLRPC.Message> messages) {
        long user = UserConfig.getInstance(account).getClientUserId();
        if (user == 0 || !WGramConfig.isHistoryEnabled(account) || messages == null || messages.isEmpty()) return;
        try (SQLiteDatabase db = open(user)) {
            db.beginTransaction();
            try {
                for (TLRPC.Message m : messages) {
                    long dialog = MessageObject.getDialogId(m);
                    String[] key = {""+dialog,""+m.id};
                    if (!eligible(account,m)) {
                        for (String table : new String[]{"live","history","deleted"}) db.delete(table,"dialog=? AND mid=?",key);
                        continue;
                    }
                    // A cache/server replay of the old text cannot resurrect a locally archived message.
                    try (Cursor c=db.rawQuery("SELECT mid FROM deleted WHERE dialog=? AND mid=?",key)) { if(c.moveToFirst()) continue; }
                    String text = WGramHistoryPolicy.clip(m.message);
                    try (Cursor c = db.rawQuery("SELECT dialog,mid,sender,text,date,outgoing,topic FROM live WHERE dialog=? AND mid=?", key)) {
                        if (c.moveToFirst() && !text.equals(c.getString(3))) archive(db,c,2);
                    }
                    ContentValues v = new ContentValues();
                    v.put("dialog",dialog); v.put("mid",m.id); v.put("channel",m.peer_id != null && m.peer_id.channel_id != 0 ? 1 : 0);
                    v.put("sender",m.from_id == null ? 0 : DialogObject.getPeerDialogId(m.from_id));
                    v.put("text",text); v.put("time",System.currentTimeMillis()/1000); v.put("date",m.date); v.put("outgoing",m.out?1:0);
                    v.put("topic",m.reply_to==null?0:m.reply_to.reply_to_top_id!=0?m.reply_to.reply_to_top_id:m.reply_to.forum_topic?m.reply_to.reply_to_msg_id:0);
                    db.insertWithOnConflict("live",null,v,SQLiteDatabase.CONFLICT_REPLACE);
                }
                prune(db,account); db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (Exception e) { FileLog.e(e); }
    }
    public static void deleted(int account, long dialog, List<Integer> ids, int maxId) {
        long user = UserConfig.getInstance(account).getClientUserId();
        if (user == 0 || !WGramConfig.isHistoryEnabled(account) || !file(user).exists()) return;
        try (SQLiteDatabase db = open(user)) {
            db.beginTransaction();
            try {
                prune(db,account);
                if (maxId > 0) move(db,"dialog=? AND mid<=?",new String[]{""+dialog,""+maxId});
                else if (ids != null) for (Integer id : ids) {
                    move(db,dialog==0?"channel=0 AND mid=?":"dialog=? AND mid=?",dialog==0?new String[]{""+id}:new String[]{""+dialog,""+id});
                }
                prune(db,account); db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (Exception e) { FileLog.e(e); }
    }
    private static void move(SQLiteDatabase db, String where, String[] args) {
        try (Cursor c = db.rawQuery("SELECT dialog,mid,sender,text,date,outgoing,topic FROM live WHERE " + where,args)) {
            while (c.moveToNext()) archive(db,c,1);
        }
        db.delete("live",where,args);
    }
    /** Queue a snapshot when the deletion reached the visible chat before the storage queue. */
    public static void saveVisibleDeleted(int account, TLRPC.Message message) {
        final long session=UserConfig.getInstance(account).getClientUserId();
        org.telegram.messenger.MessagesStorage.getInstance(account).getStorageQueue().postRunnable(()->{
            if(session==0 || session!=UserConfig.getInstance(account).getClientUserId() || !WGramConfig.isHistoryEnabled(account)) return;
            observe(account,java.util.Collections.singletonList(message));
            deleted(account,MessageObject.getDialogId(message),java.util.Collections.singletonList(message.id),0);
        });
    }
    private static MessageObject restore(int account,long dialog,Cursor c) {
        TLRPC.TL_message m=new TLRPC.TL_message();
        m.id=c.getInt(0);m.message=c.getString(2);m.date=c.getInt(3);m.out=c.getInt(4)!=0;
        long sender=c.getLong(1);if(sender==0)sender=m.out?UserConfig.getInstance(account).getClientUserId():dialog;
        m.from_id=peer(sender,sender<0 && org.telegram.messenger.ChatObject.isChannel(MessagesController.getInstance(account).getChat(-sender)));
        m.peer_id=peer(dialog,dialog<0 && org.telegram.messenger.ChatObject.isChannel(MessagesController.getInstance(account).getChat(-dialog)));
        m.dialog_id=dialog;m.unread=false;m.media_unread=false;m.send_state=MessageObject.MESSAGE_SEND_STATE_SENT;
        int topic=c.getInt(5);
        if(topic!=0){m.reply_to=new TLRPC.TL_messageReplyHeader();m.reply_to.forum_topic=true;m.reply_to.reply_to_top_id=topic;m.reply_to.reply_to_msg_id=topic;}
        MessageObject object=new MessageObject(account,m,true,false);object.wgramDeleted=true;return object;
    }
    private static TLRPC.Peer peer(long id,boolean channel) {
        if(id>0){TLRPC.TL_peerUser p=new TLRPC.TL_peerUser();p.user_id=id;return p;}
        if(channel){TLRPC.TL_peerChannel p=new TLRPC.TL_peerChannel();p.channel_id=-id;return p;}
        TLRPC.TL_peerChat p=new TLRPC.TL_peerChat();p.chat_id=-id;return p;
    }
    /** Merge bounded local tombstones into a loaded page, never into Telegram's main message DB. */
    public static void mergeInto(int account,long dialog,ArrayList<MessageObject> objects,int maxId,int loadType,boolean end) {
        long user=UserConfig.getInstance(account).getClientUserId();
        if(user==0 || !WGramConfig.isHistoryEnabled(account) || !file(user).exists() || DialogObject.isEncryptedDialog(dialog))return;
        TLRPC.Chat chat=dialog<0?MessagesController.getInstance(account).getChat(-dialog):null;
        if(dialog<0 && (chat==null || chat.noforwards))return;
        int lower=Integer.MAX_VALUE,upper=0;
        for(MessageObject o:objects)if(o.getId()>0){lower=Math.min(lower,o.getId());upper=Math.max(upper,o.getId());}
        if(upper==0){
            lower=0;upper=Integer.MAX_VALUE;
            if(maxId>0){
                if(loadType==MessagesController.LOAD_BACKWARD)upper=maxId-1;
                else if(loadType==MessagesController.LOAD_FORWARD)lower=maxId==Integer.MAX_VALUE?maxId:maxId+1;
                else lower=upper=maxId;
            }
        }else if(loadType==MessagesController.LOAD_BACKWARD){
            upper=maxId>0?maxId-1:Integer.MAX_VALUE;if(end)lower=0;
        }else if(loadType==MessagesController.LOAD_FORWARD&&maxId>0){
            lower=maxId==Integer.MAX_VALUE?maxId:maxId+1;if(end)upper=Integer.MAX_VALUE;
        }
        if(lower>upper)return;
        try(SQLiteDatabase db=open(user)){
            prune(db,account);
            ArrayList<MessageObject> archived=new ArrayList<>();
            try(Cursor c=db.rawQuery("SELECT mid,sender,text,date,outgoing,topic FROM deleted WHERE dialog=? AND mid>=? AND mid<=? ORDER BY mid " + (loadType==MessagesController.LOAD_FORWARD?"ASC":"DESC") + " LIMIT 100",
                    new String[]{""+dialog,""+lower,""+upper})){
                while(c.moveToNext())archived.add(restore(account,dialog,c));
            }
            HashSet<Integer> ids=new HashSet<>();for(MessageObject o:archived)ids.add(o.getId());
            for(int i=objects.size()-1;i>=0;i--)if(ids.contains(objects.get(i).getId()))objects.remove(i);
            objects.addAll(archived);objects.sort((a,b)->{
                int left=a.getId(),right=b.getId();
                if(left>0&&right>0)return Integer.compare(right,left);
                if(left<0&&right<0)return Integer.compare(left,right);
                return Integer.compare(b.messageOwner.date,a.messageOwner.date);
            });
        }catch(Exception e){FileLog.e(e);}
    }
    public static ArrayList<Entry> list(int account, long dialog, String query, int filter) {
        ArrayList<Entry> result = new ArrayList<>();
        long user = UserConfig.getInstance(account).getClientUserId();
        if (user == 0 || !WGramConfig.isHistoryEnabled(account) || !file(user).exists()) return result;
        try (SQLiteDatabase db = open(user)) {
            prune(db,account);
            ArrayList<String> args = new ArrayList<>(); String where = "1=1";
            if (dialog != 0) { where += " AND dialog=?"; args.add(""+dialog); }
            if (filter != 0) { where += " AND kind=?"; args.add(""+filter); }
            if (query != null && !query.isEmpty()) { where += " AND instr(lower(text),lower(?))>0"; args.add(query); }
            try (Cursor c = db.rawQuery("SELECT dialog,mid,sender,text,kind,time FROM history WHERE " + where + " ORDER BY seq DESC LIMIT 200",args.toArray(new String[0]))) {
                while (c.moveToNext()) {
                    Entry e = new Entry(); e.dialog=c.getLong(0); e.id=c.getInt(1); e.sender=c.getLong(2);
                    e.text=c.getString(3); e.kind=c.getInt(4); e.time=c.getLong(5); result.add(e);
                }
            }
        } catch (Exception e) { FileLog.e(e); }
        return result;
    }
    public static void clear(long user) {
        if(user!=0)SQLiteDatabase.deleteDatabase(file(user));
    }
}
