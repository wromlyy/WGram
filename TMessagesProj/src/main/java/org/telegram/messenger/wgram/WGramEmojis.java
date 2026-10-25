package org.telegram.messenger.wgram;

import org.telegram.messenger.DialogObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import java.util.ArrayList;

public final class WGramEmojis {
    private WGramEmojis() {}
    public static boolean canCompose(int account, long dialogId) {
        return WGramConfig.areEmojisEnabled(account) && !DialogObject.isEncryptedDialog(dialogId);
    }
    /** Convert only the outgoing copy; preserve the original message/draft entities. */
    public static ArrayList<TLRPC.MessageEntity> forSending(int account, long dialogId,
            CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || UserConfig.getInstance(account).isPremium() || !canCompose(account, dialogId)) return entities;
        ArrayList<TLRPC.MessageEntity> result = new ArrayList<>(entities.size());
        for (TLRPC.MessageEntity entity : entities) {
            if (entity instanceof TLRPC.TL_messageEntityCustomEmoji) {
                if (!WGramEmojiWire.validRange(text, entity.offset, entity.length)) continue;
                long id = ((TLRPC.TL_messageEntityCustomEmoji) entity).document_id;
                if (id > 0) {
                    TLRPC.TL_messageEntityTextUrl link = new TLRPC.TL_messageEntityTextUrl();
                    link.offset = entity.offset;
                    link.length = entity.length;
                    link.url = WGramEmojiWire.encode(id);
                    result.add(link);
                    continue;
                }
                continue; // Never send an invalid custom emoji entity to Telegram.
            }
            result.add(entity);
        }
        return result;
    }
    public static boolean isEmojiLink(TLRPC.MessageEntity entity, CharSequence text) {
        return entity instanceof TLRPC.TL_messageEntityTextUrl
            && WGramEmojiWire.decode(entity.url) != 0 && WGramEmojiWire.validRange(text, entity.offset, entity.length);
    }
    /** Restore display entities without changing the stored Telegram message. */
    public static ArrayList<TLRPC.MessageEntity> forDisplay(CharSequence text, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null) return null;
        ArrayList<TLRPC.MessageEntity> result = null;
        for (int i = 0; i < entities.size(); i++) {
            TLRPC.MessageEntity entity = entities.get(i);
            boolean invalid = entity instanceof TLRPC.TL_messageEntityCustomEmoji
                && (!WGramEmojiWire.validRange(text, entity.offset, entity.length)
                    || ((TLRPC.TL_messageEntityCustomEmoji) entity).document_id <= 0);
            if (invalid || isEmojiLink(entity, text)) {
                if (result == null) result = new ArrayList<>(entities.subList(0, i));
                if (invalid) continue;
                TLRPC.TL_messageEntityCustomEmoji emoji = new TLRPC.TL_messageEntityCustomEmoji();
                emoji.offset = entity.offset;
                emoji.length = entity.length;
                emoji.document_id = WGramEmojiWire.decode(entity.url);
                result.add(emoji);
            } else if (result != null) result.add(entity);
        }
        return result == null ? entities : result;
    }
}
