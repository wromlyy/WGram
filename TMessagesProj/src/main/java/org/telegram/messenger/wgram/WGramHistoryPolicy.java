package org.telegram.messenger.wgram;

/** Shared eligibility rules; transient/protected content is never retained. */
public final class WGramHistoryPolicy {
    public static final int MAX_ROWS = 2000;
    public static final int MAX_TEXT = 4096;
    private WGramHistoryPolicy() {}
    public static boolean canKeep(long dialog, int id, String text, boolean secret,
            int messageTtl, int mediaTtl, boolean protectedContent, boolean ephemeral) {
        return dialog != 0 && id > 0 && text != null && !text.isEmpty() && !secret
            && messageTtl == 0 && mediaTtl == 0 && !protectedContent && !ephemeral;
    }
    public static String clip(String text) {
        if (text.length() <= MAX_TEXT) return text;
        int end = MAX_TEXT;
        if (Character.isHighSurrogate(text.charAt(end - 1))) end--;
        return text.substring(0, end);
    }
    public static long cutoff(long now, int days) { return now - (days == 30 ? 30L : 7L) * 86400L; }
}
