package org.telegram.messenger;
public class UserConfig {
    public static final int MAX_ACCOUNT_COUNT = 4;
    private static final UserConfig[] instances = {new UserConfig(), new UserConfig(), new UserConfig(), new UserConfig()};
    public long userId;
    public boolean premium;
    public static UserConfig getInstance(int account) { return instances[account]; }
    public long getClientUserId() { return userId; }
    public boolean isPremium() { return premium; }
}
