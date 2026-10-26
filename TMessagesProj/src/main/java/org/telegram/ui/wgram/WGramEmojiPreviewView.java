package org.telegram.ui.wgram;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AnimatedEmojiDrawable;

/** Read-only custom emoji display: viewing never depends on Premium entitlement. */
public final class WGramEmojiPreviewView extends View {
    private final AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable drawable;
    public WGramEmojiPreviewView(Context context, int account) {
        super(context);
        drawable = new AnimatedEmojiDrawable.SwapAnimatedEmojiDrawable(this,
            AndroidUtilities.dp(96), AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_LARGE);
        drawable.setCurrentAccount(account);
        setContentDescription(LocaleController.getString(R.string.WGramCustomEmojiPreview));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    public void setEmoji(long documentId) {
        if (documentId > 0) drawable.set(documentId, false);
        else drawable.set((android.graphics.drawable.Drawable) null, false);
        invalidate();
    }
    @Override protected void onMeasure(int width, int height) {
        setMeasuredDimension(MeasureSpec.getSize(width), resolveSize(AndroidUtilities.dp(144), height));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int size = AndroidUtilities.dp(96), left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        drawable.setBounds(left, top, left + size, top + size);
        drawable.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        drawable.draw(canvas);
    }
    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); drawable.attach(); }
    @Override protected void onDetachedFromWindow() { drawable.detach(); super.onDetachedFromWindow(); }
    public void release() { drawable.detach(); drawable.set((android.graphics.drawable.Drawable) null, false); }
}
