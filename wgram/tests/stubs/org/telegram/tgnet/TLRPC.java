package org.telegram.tgnet;
public class TLRPC {
    public static class MessageEntity { public int offset, length; public String url; }
    public static class TL_messageEntityTextUrl extends MessageEntity {}
    public static class TL_messageEntityCustomEmoji extends MessageEntity { public long document_id; }
    public static class TL_messageEntityBold extends MessageEntity {}
    public static class EmojiStatus {}
    public static class TL_emojiStatus extends EmojiStatus { public long document_id; }
    public static class User { public long id; public EmojiStatus emoji_status; }
}
