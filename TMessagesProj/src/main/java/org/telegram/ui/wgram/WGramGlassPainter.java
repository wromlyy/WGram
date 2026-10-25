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
import org.telegram.ui.ActionBar.Theme;

/** Canvas-only glass for API 23+, with no RenderNode, RuntimeShader or bitmap capture. */
public final class WGramGlassPainter {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Rect lastBounds = new Rect();
    private int lastColor;
    private int lastOpacity;
    private LinearGradient gradient;

    public void draw(Canvas canvas, Path path, Rect bounds, int alpha, int backgroundColor) {
        if (bounds.isEmpty() || alpha == 0) return;
        boolean glass = WGramConfig.isGlassEnabled();
        int color = ColorUtils.setAlphaComponent(backgroundColor, 255);
        if (!glass) {
            fill.setShader(null);
            fill.setColor(color);
            fill.setAlpha(alpha);
            canvas.drawPath(path, fill);
            return;
        }
        int opacity = WGramConfig.getGlassOpacity();
        if (gradient == null || !lastBounds.equals(bounds) || lastColor != color || lastOpacity != opacity) {
            lastBounds.set(bounds);
            lastColor = color;
            lastOpacity = opacity;
            boolean dark = ColorUtils.calculateLuminance(color) < 0.5;
            int top = ColorUtils.blendARGB(color, Color.WHITE, dark ? 0.13f : 0.55f);
            int bottom = ColorUtils.blendARGB(color, dark ? Color.BLACK : 0xFFDCE8F8, 0.2f);
            int a = opacity * 255 / 100;
            gradient = new LinearGradient(bounds.left, bounds.top, bounds.right, bounds.bottom,
                new int[]{ColorUtils.setAlphaComponent(top, Math.min(255, a + 22)),
                    ColorUtils.setAlphaComponent(color, a), ColorUtils.setAlphaComponent(bottom, a)},
                new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);
        }
        fill.setColor(Color.WHITE);
        fill.setShader(gradient);
        fill.setAlpha(alpha);
        canvas.drawPath(path, fill);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(AndroidUtilities.dpf2(0.8f));
        rim.setColor(ColorUtils.setAlphaComponent(Color.WHITE,
            (Theme.isCurrentThemeDark() ? 55 : 155) * alpha / 255));
        canvas.drawPath(path, rim);
    }
}
