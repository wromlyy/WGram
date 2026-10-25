package org.telegram.ui.wgram;

import android.graphics.Canvas;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.wgram.WGramSharedProfiles;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

/** A measured strip of up to three awards. Uses the same attachment lifecycle as a status. */
public final class WGramBadgeDrawable extends AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable {
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable[] extra = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable[2];
    private final int size, gap;
    private int count;

    public WGramBadgeDrawable(View parent, int size) {
        this(parent, size, AnimatedEmojiDrawable.CACHE_TYPE_EMOJI_STATUS);
    }
    public WGramBadgeDrawable(View parent, int size, int cacheType) {
        super(parent, size, cacheType);
        this.size = size;
        gap = AndroidUtilities.dp(2);
        for (int i = 0; i < extra.length; i++) extra[i] = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(parent, size, cacheType);
    }
    /** The first award is already provided through getDisplayedStatus; append the other awards. */
    public boolean setBadges(int account, TLRPC.User user) {
        setCurrentAccount(account);
        WGramSharedProfiles.Profile profile = user == null ? null : WGramSharedProfiles.get(account).peek(user.id);
        int next = profile == null ? 0 : Math.min(extra.length, Math.max(0, profile.badges.size() - 1));
        boolean resized = next != count;
        count = next;
        for (int i = 0; i < extra.length; i++) {
            if (i < count) extra[i].set(profile.badges.get(i + 1).emoji, false);
            else extra[i].set((android.graphics.drawable.Drawable) null, false);
        }
        return resized;
    }
    @Override public void setCurrentAccount(int account) {
        super.setCurrentAccount(account);
        if (extra != null) for (AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable child : extra) if (child != null) child.setCurrentAccount(account);
    }
    @Override public int getIntrinsicWidth() { return size + count * (size + gap); }
    @Override public int getIntrinsicHeight() { return size; }
    @Override public void draw(Canvas canvas) {
        int left = getBounds().left, originalTop = getBounds().top, right = getBounds().right, bottom = getBounds().bottom;
        int top = (originalTop + bottom) / 2 - size / 2;
        int first = left + (LocaleController.isRTL ? count * (size + gap) : 0);
        // SwapDrawable reads its own bounds. Mutate/restore the rectangle directly:
        // setBounds() here would invalidate the parent twice on every frame.
        getBounds().set(first, top, first + size, top + size);
        try { super.draw(canvas); }
        finally { getBounds().set(left, originalTop, right, bottom); }
        for (int i = 0; i < count; i++) {
            int x = left + (LocaleController.isRTL ? count - i - 1 : i + 1) * (size + gap);
            extra[i].setBounds(x, top, x + size, top + size);
            extra[i].draw(canvas);
        }
    }
    @Override public void setColor(Integer color) {
        super.setColor(color);
        if (extra != null) for (AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable child : extra) if (child != null) child.setColor(color);
    }
    @Override public void setAlpha(int alpha) {
        super.setAlpha(alpha);
        if (extra != null) for (AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable child : extra) if (child != null) child.setAlpha(alpha);
    }
    @Override public void attach() {
        super.attach();
        if (extra != null) for (AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable child : extra) if (child != null) child.attach();
    }
    @Override public void detach() {
        super.detach();
        if (extra != null) for (AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable child : extra) if (child != null) child.detach();
    }
}
