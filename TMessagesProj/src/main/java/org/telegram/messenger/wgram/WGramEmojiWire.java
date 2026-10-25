package org.telegram.messenger.wgram;

/** Versioned text-link format. Offsets and lengths use Telegram/Java UTF-16 units. */
public final class WGramEmojiWire {
    private static final String PREFIX = "https://t.me/WGramnews?wgram_emoji=v1_";
    private static final String LEGACY_PREFIX = "wgram://emoji/v1/";
    private WGramEmojiWire() {}
    public static String encode(long documentId) {
        if (documentId <= 0) throw new IllegalArgumentException("An emoji document ID must be positive");
        return PREFIX + documentId;
    }
    public static long decode(String url) {
        if (url == null) return 0;
        final String prefix = url.startsWith(PREFIX) ? PREFIX : url.startsWith(LEGACY_PREFIX) ? LEGACY_PREFIX : null;
        if (prefix == null) return 0;
        String digits = url.substring(prefix.length());
        if (digits.length() == 0 || digits.length() > 19 || digits.charAt(0) == '0') return 0;
        for (int i = 0; i < digits.length(); i++) {
            if (digits.charAt(i) < '0' || digits.charAt(i) > '9') return 0;
        }
        try {
            long id = Long.parseLong(digits);
            return id > 0 ? id : 0;
        } catch (NumberFormatException ignored) { return 0; }
    }
    public static boolean validRange(CharSequence text, int offset, int length) {
        if (text == null || offset < 0 || length <= 0 || length > 32
            || offset >= text.length() || length > text.length() - offset) return false;
        int end = offset + length;
        return !(offset > 0 && Character.isHighSurrogate(text.charAt(offset - 1)) && Character.isLowSurrogate(text.charAt(offset)))
            && !(end < text.length() && Character.isHighSurrogate(text.charAt(end - 1)) && Character.isLowSurrogate(text.charAt(end)));
    }
}
