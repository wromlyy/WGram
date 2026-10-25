import java.util.ArrayList;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.messenger.wgram.WGramEmojiWire;
import org.telegram.messenger.wgram.WGramEmojis;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.TLRPC;

public final class WGramCoreTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        UserConfig.getInstance(0).userId = 1001;
        UserConfig.getInstance(1).userId = 1002;
        for (long id : new long[]{1, 5368324170671202286L, Long.MAX_VALUE}) {
            check(WGramEmojiWire.decode(WGramEmojiWire.encode(id)) == id, "lossless 64-bit document ID");
        }
        String prefix = "wgram://emoji/v1/";
        String[] invalid = {null, "", prefix, prefix+"0", prefix+"-1", prefix+"+1", prefix+"01",
            prefix+"9223372036854775808", prefix+"1?x=2", prefix+"1/", prefix+"%31", prefix+"1#x",
            "https://example.com/emoji/v1/1", "wgram://emoji/v2/1", prefix+"١", prefix+"1\n"};
        for (String url : invalid) check(WGramEmojiWire.decode(url) == 0, "reject malformed URL: " + url);
        check(WGramEmojiWire.encode(42).startsWith("https://"), "new transport uses supported HTTPS URLs");
        check(WGramEmojiWire.decode("wgram://emoji/v1/42") == 42, "old messages still display");
        check(WGramEmojiWire.decode("https://t.me/WGramnews?wgram_emoji=v1_42") == 42, "HTTPS receive");
        check(WGramEmojiWire.decode("https://t.me/WGramnews?wgram_emoji=v1_42&x=1") == 0, "reject extra URL parameters");
        try { WGramEmojiWire.encode(0); throw new AssertionError("zero ID accepted"); }
        catch (IllegalArgumentException expected) { checks++; }
        String text = "hi 😀!";
        check(WGramEmojiWire.validRange(text, 3, 2), "UTF-16 emoji range");
        check(!WGramEmojiWire.validRange(text, 3, 1), "reject half surrogate at end");
        check(!WGramEmojiWire.validRange(text, 4, 1), "reject half surrogate at start");
        check(!WGramEmojiWire.validRange(text, Integer.MAX_VALUE, 2), "offset overflow");
        check(!WGramEmojiWire.validRange(text, 3, Integer.MAX_VALUE), "length overflow");
        check(!WGramEmojiWire.validRange(text, -1, 2), "negative offset");
        check(!WGramEmojiWire.validRange(text, 3, 0), "zero length");
        check(!WGramEmojiWire.validRange(null, 0, 1), "null text");
        check(!WGramEmojiWire.validRange(text, text.length(), 1), "out of range");

        TLRPC.TL_messageEntityCustomEmoji emoji = new TLRPC.TL_messageEntityCustomEmoji();
        emoji.offset=3; emoji.length=2; emoji.document_id=5368324170671202286L;
        TLRPC.TL_messageEntityBold bold = new TLRPC.TL_messageEntityBold(); bold.offset=0; bold.length=2;
        ArrayList<TLRPC.MessageEntity> original = new ArrayList<>(); original.add(bold); original.add(emoji);
        ArrayList<TLRPC.MessageEntity> wire = WGramEmojis.forSending(0, 2001, text, original);
        check(wire != original, "outgoing entities copied");
        check(original.get(1) == emoji, "draft emoji unchanged");
        check(wire.get(0) == bold, "ordinary formatting preserved");
        check(wire.get(1) instanceof TLRPC.TL_messageEntityTextUrl, "wire uses normal text link");
        check(wire.get(1).offset == 3 && wire.get(1).length == 2, "wire preserves UTF-16 positions");
        ArrayList<TLRPC.MessageEntity> display = WGramEmojis.forDisplay(text, wire);
        check(display != wire, "display entities copied");
        check(wire.get(1) instanceof TLRPC.TL_messageEntityTextUrl, "stored message unchanged");
        check(display.get(1) instanceof TLRPC.TL_messageEntityCustomEmoji, "recipient restores emoji");
        check(((TLRPC.TL_messageEntityCustomEmoji)display.get(1)).document_id == emoji.document_id, "recipient document ID preserved");
        check(WGramEmojis.forDisplay(text, original) == original, "ordinary message fast path");
        check(WGramEmojis.forDisplay(text, null) == null, "null entities supported");
        check(WGramEmojis.forSending(0, 2001, text, wire).get(1) == wire.get(1), "resend is idempotent");
        UserConfig.getInstance(0).premium=true;
        check(WGramEmojis.forSending(0, 2001, text, original) == original, "real premium uses native transport");
        UserConfig.getInstance(0).premium=false;
        long secret = 0x4000000000000001L;
        check(!WGramEmojis.canCompose(0, secret), "secret chats keep native behavior");
        check(WGramEmojis.forSending(0, secret, text, original) == original, "no custom transport in secret chats");
        WGramConfig.setEmojisEnabled(0, false);
        check(WGramEmojis.forSending(0, 2001, text, original) == original, "switch disables custom transport");
        check(WGramEmojis.canCompose(1, 2001), "emoji preferences isolated by account");
        WGramConfig.setEmojisEnabled(0, true);
        TLRPC.TL_messageEntityTextUrl bad = new TLRPC.TL_messageEntityTextUrl();
        bad.offset=Integer.MAX_VALUE; bad.length=2; bad.url=WGramEmojiWire.encode(1);
        check(!WGramEmojis.isEmojiLink(bad,text), "malformed received range ignored");

        TLRPC.TL_messageEntityCustomEmoji corrupt = new TLRPC.TL_messageEntityCustomEmoji();
        corrupt.document_id=42; corrupt.offset=-1; corrupt.length=2;
        ArrayList<TLRPC.MessageEntity> corrupted = new ArrayList<>(); corrupted.add(corrupt); corrupted.add(bold);
        check(WGramEmojis.forDisplay(text, corrupted).size()==1, "negative emoji offset cannot reach renderer");
        check(WGramEmojis.forSending(0,2001,text,corrupted).size()==1, "invalid ranges not sent");
        corrupt.offset=3; corrupt.length=Integer.MAX_VALUE;
        check(WGramEmojis.forDisplay(text,corrupted).size()==1, "overflow cannot reach renderer");
        corrupt.length=1;
        check(WGramEmojis.forDisplay(text,corrupted).size()==1, "split surrogate cannot reach renderer");
        corrupt.length=2; corrupt.document_id=0;
        check(WGramEmojis.forDisplay(text,corrupted).size()==1, "invalid document cannot reach renderer");
        check(WGramEmojis.forSending(0,2001,text,corrupted).size()==1, "invalid document not sent");
        check(corrupted.size()==2, "validation preserves stored entities");
        String adjacent="😀😀";
        TLRPC.TL_messageEntityCustomEmoji second = new TLRPC.TL_messageEntityCustomEmoji();
        second.offset=2; second.length=2; second.document_id=43;
        corrupt.offset=0; corrupt.document_id=42;
        ArrayList<TLRPC.MessageEntity> pair = new ArrayList<>();pair.add(corrupt);pair.add(second);
        ArrayList<TLRPC.MessageEntity> restored=WGramEmojis.forDisplay(adjacent,WGramEmojis.forSending(0,2001,adjacent,pair));
        check(restored.size()==2 && restored.get(1).offset==2 && restored.get(0).length==2, "adjacent emojis retain positions");

        WGramConfig.setVisualPremiumEnabled(0,true);
        WGramConfig.setProfileColor(0,7);
        WGramConfig.setProfileEmoji(0,123456);
        check(!UserConfig.getInstance(0).isPremium(), "cosmetics never modify account entitlement");
        TLRPC.User self=new TLRPC.User(); self.id=1001;
        TLRPC.User other=new TLRPC.User(); other.id=2001;
        check(WGramConfig.hasLocalBadge(0,self), "local self badge");
        check(!WGramConfig.hasLocalBadge(0,other), "no badge on unrelated users");
        check(((TLRPC.TL_emojiStatus)WGramConfig.getDisplayedStatus(0,self)).document_id==123456, "local status override");
        check(WGramConfig.getDisplayedStatus(0,other)==null, "other user status unaffected");
        check(!WGramConfig.isVisualPremiumEnabled(1), "visual preferences isolated");
        UserConfig.getInstance(0).userId=1003;
        check(!WGramConfig.isVisualPremiumEnabled(0), "reusing account slot cannot leak previous profile");
        check(WGramConfig.getProfileEmoji(0)==0, "reusing account slot cannot leak previous status");
        UserConfig.getInstance(0).userId=1001;
        check(WGramConfig.getProfileColor(0)==7, "original user preferences restored");
        WGramConfig.setVisualPremiumEnabled(0,false);
        check(((TLRPC.TL_emojiStatus)WGramConfig.getDisplayedStatus(0,self)).document_id==123456, "emoji status works independently of visual Premium");
        WGramConfig.setProfileEmoji(0,0);
        check(WGramConfig.getDisplayedStatus(0,self)==self.emoji_status, "clearing local status restores native status");
        WGramConfig.setGlassOpacity(1000); check(WGramConfig.getGlassOpacity()==95, "opacity upper bound");
        WGramConfig.setGlassOpacity(-100); check(WGramConfig.getGlassOpacity()==60, "opacity lower bound");
        WGramConfig.setGlassEnabled(true); WGramConfig.setGlassMode(WGramConfig.GLASS_AUTO);
        for (int sdk : new int[]{23,24,29,30}) {
            android.os.Build.VERSION.SDK_INT=sdk;
            check(WGramConfig.useLightGlass(), "old API uses Canvas glass");
            check(!WGramConfig.useRefraction(), "old API cannot use RuntimeShader");
        }
        android.os.Build.VERSION.SDK_INT=33;
        SharedConfig.performance=SharedConfig.PERFORMANCE_CLASS_LOW;
        check(WGramConfig.useLightGlass() && !WGramConfig.useRefraction(), "low-end device uses lightweight renderer");
        SharedConfig.performance=2;
        check(WGramConfig.useRefraction(), "modern device selects native refraction");
        WGramConfig.setGlassMode(WGramConfig.GLASS_LIGHT);
        check(!WGramConfig.useRefraction(), "manual light mode wins");
        WGramConfig.setGlassEnabled(false);
        check(!WGramConfig.useRefraction(), "glass switch disables refraction");
        System.out.println("PASS: " + checks + " WGram core checks (actual production classes, host stubs)");
    }
}
