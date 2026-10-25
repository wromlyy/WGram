package org.telegram.messenger.wgram;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

/** Presentation preferences; never changes Telegram account entitlements. */
public final class WGramConfig {
    public static final int GLASS_AUTO = 0;
    public static final int GLASS_LIGHT = 1;
    private WGramConfig() {}

    private static SharedPreferences global() {
        return ApplicationLoader.applicationContext.getSharedPreferences("wgram", Context.MODE_PRIVATE);
    }
    private static SharedPreferences account(int account) {
        // Slots can be reused after logout: isolate preferences by actual Telegram user ID.
        long userId = UserConfig.getInstance(account).getClientUserId();
        return ApplicationLoader.applicationContext.getSharedPreferences("wgram_user_" + userId, Context.MODE_PRIVATE);
    }
    public static boolean isGlassEnabled() { return global().getBoolean("glass_enabled", true); }
    public static void setGlassEnabled(boolean enabled) { global().edit().putBoolean("glass_enabled", enabled).apply(); }
    public static int getGlassMode() { return global().getInt("glass_mode", GLASS_AUTO); }
    public static void setGlassMode(int mode) {
        global().edit().putInt("glass_mode", mode == GLASS_LIGHT ? GLASS_LIGHT : GLASS_AUTO).apply();
    }
    public static boolean useLightGlass() {
        return getGlassMode() == GLASS_LIGHT || Build.VERSION.SDK_INT < 31
            || SharedConfig.getDevicePerformanceClass() == SharedConfig.PERFORMANCE_CLASS_LOW;
    }
    public static boolean useRefraction() {
        return isGlassEnabled() && !useLightGlass() && Build.VERSION.SDK_INT >= 33;
    }
    public static int getGlassOpacity() { return Math.max(60, Math.min(95, global().getInt("glass_opacity", 75))); }
    public static void setGlassOpacity(int percent) {
        global().edit().putInt("glass_opacity", Math.max(60, Math.min(95, percent))).apply();
    }
    public static boolean areEmojisEnabled(int account) { return account(account).getBoolean("shared_emojis", true); }
    public static void setEmojisEnabled(int account, boolean enabled) {
        account(account).edit().putBoolean("shared_emojis", enabled).apply();
    }
    public static boolean isVisualPremiumEnabled(int account) {
        return UserConfig.getInstance(account).getClientUserId() != 0
            && account(account).getBoolean("visual_premium", false);
    }
    public static void setVisualPremiumEnabled(int account, boolean enabled) {
        account(account).edit().putBoolean("visual_premium", enabled).apply();
    }
    public static boolean hasLocalBadge(int account, TLRPC.User user) {
        return user != null && user.id == UserConfig.getInstance(account).getClientUserId()
            && isVisualPremiumEnabled(account);
    }
    public static int getProfileColor(int account) { return account(account).getInt("profile_color", -1); }
    public static void setProfileColor(int account, int color) { account(account).edit().putInt("profile_color", color).apply(); }
    public static long getProfileEmoji(int account) { return account(account).getLong("profile_emoji", 0); }
    public static void setProfileEmoji(int account, long documentId) {
        account(account).edit().putLong("profile_emoji", Math.max(0, documentId)).apply();
    }
    public static TLRPC.EmojiStatus getDisplayedStatus(int account, TLRPC.User user) {
        if (hasLocalBadge(account, user) && getProfileEmoji(account) != 0) {
            TLRPC.TL_emojiStatus status = new TLRPC.TL_emojiStatus();
            status.document_id = getProfileEmoji(account);
            return status;
        }
        return user == null ? null : user.emoji_status;
    }
    public static int findLocalProfileAccount(TLRPC.User user) {
        if (user != null) {
            for (int i = 0; i < UserConfig.MAX_ACCOUNT_COUNT; i++) {
                if (UserConfig.getInstance(i).getClientUserId() == user.id && isVisualPremiumEnabled(i)) return i;
            }
        }
        return -1;
    }
}
