package org.telegram.ui.Components.blur3.drawable;

import android.graphics.Canvas;

import androidx.annotation.NonNull;

import org.telegram.ui.Components.blur3.source.BlurredBackgroundSource;
import org.telegram.ui.Components.blur3.source.BlurredBackgroundSourceWrapped;

public class BlurredBackgroundDrawableSource extends BlurredBackgroundDrawable {
    private final BlurredBackgroundSource source;

    public BlurredBackgroundDrawableSource(BlurredBackgroundSource source) {
        this.source = source;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        drawSource(canvas, source);
        if (org.telegram.messenger.wgram.WGramConfig.isGlassEnabled()
            && !org.telegram.messenger.wgram.WGramConfig.useLightGlass()
            && !org.telegram.messenger.LiteMode.isPowerSaverApplied()) drawWGramOverlay(canvas);
    }

    @Override
    public BlurredBackgroundSource getSource() {
        return source;
    }
}
