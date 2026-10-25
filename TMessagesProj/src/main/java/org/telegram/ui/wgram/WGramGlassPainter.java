package org.telegram.ui.wgram;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import androidx.core.graphics.ColorUtils;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.wgram.WGramConfig;

/** Static, allocation-free after bounds/settings changes; works from Android 6. */
public final class WGramGlassPainter {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect lastBounds = new Rect();
    private int lastKey;
    private LinearGradient gradient, sheen;
    private static final int[] TINTS = {0, 0xFF71C9FF, 0xFFB49AFF, 0xFF72E6C2, 0xFFFFB68C};

    public static int tint(int color) {
        int index = WGramConfig.getGlassTint();
        return index == 0 ? color : ColorUtils.blendARGB(color, TINTS[index], 0.16f);
    }

    public void draw(Canvas canvas, Path path, Rect bounds, int alpha, int backgroundColor) {
        if (bounds.isEmpty() || alpha == 0) return;
        if (!WGramConfig.isGlassEnabled()) {
            fill.setShader(null); fill.setColor(backgroundColor); fill.setAlpha(alpha);
            canvas.drawPath(path, fill); return;
        }
        prepare(bounds, backgroundColor);
        fill.setShader(gradient); fill.setColor(Color.WHITE); fill.setAlpha(alpha);
        canvas.drawPath(path, fill);
        drawOverlay(canvas, path, bounds, alpha, backgroundColor);
    }

    public void drawOverlay(Canvas canvas, Path path, Rect bounds, int alpha, int color) {
        if (!WGramConfig.isGlassEnabled() || bounds.isEmpty() || alpha == 0) return;
        prepare(bounds, color);
        fill.setShader(sheen); fill.setColor(Color.WHITE); fill.setAlpha(alpha);
        canvas.drawPath(path, fill);
        if (WGramConfig.hasGlassRim()) {
            rim.setStyle(Paint.Style.STROKE); rim.setStrokeWidth(AndroidUtilities.dpf2(0.85f));
            rim.setShader(sheen); rim.setColor(Color.WHITE); rim.setAlpha(Math.min(255, alpha * 2));
            canvas.save(); canvas.clipPath(path); canvas.drawPath(path, rim); canvas.restore();
        }
    }

    private void prepare(Rect bounds, int background) {
        int color = tint(ColorUtils.setAlphaComponent(background, 255));
        int opacity = WGramConfig.getGlassOpacity(), highlight = WGramConfig.getGlassHighlight();
        int key = color * 31 + opacity * 101 + highlight;
        if (gradient != null && lastBounds.equals(bounds) && key == lastKey) return;
        lastBounds.set(bounds); lastKey = key;
        boolean dark = ColorUtils.calculateLuminance(color) < 0.5;
        int top = ColorUtils.blendARGB(color, Color.WHITE, dark ? 0.12f : 0.45f);
        int bottom = ColorUtils.blendARGB(color, dark ? Color.BLACK : 0xFFDCE8F8, 0.2f);
        int a = opacity * 255 / 100;
        gradient = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            new int[]{ColorUtils.setAlphaComponent(top, Math.min(255, a + 16)),
                ColorUtils.setAlphaComponent(color, a), ColorUtils.setAlphaComponent(bottom, a)},
            new float[]{0, 0.48f, 1}, Shader.TileMode.CLAMP);
        int h = (dark ? 55 : 100) * highlight / 100;
        sheen = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
            new int[]{ColorUtils.setAlphaComponent(Color.WHITE, h), 0x00FFFFFF,
                ColorUtils.setAlphaComponent(Color.WHITE, h / 6), ColorUtils.setAlphaComponent(Color.WHITE, h / 2)},
            new float[]{0, 0.35f, 0.75f, 1}, Shader.TileMode.CLAMP);
    }
}
