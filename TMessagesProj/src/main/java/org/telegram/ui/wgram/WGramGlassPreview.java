package org.telegram.ui.wgram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/** Lightweight live material preview; no timers, captures or background animations. */
final class WGramGlassPreview extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path panel = new Path();
    private final Rect bounds = new Rect();
    private final WGramGlassPainter glass = new WGramGlassPainter();
    WGramGlassPreview(Context context) {
        super(context); setContentDescription(LocaleController.getString(R.string.WGramGlassPreview));
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }
    @Override protected void onMeasure(int width, int height) {
        setMeasuredDimension(MeasureSpec.getSize(width), AndroidUtilities.dp(196));
    }
    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xFF5478CF,0xFF9B71C9,0xFF6CC9C4},null,Shader.TileMode.CLAMP));
    }
    @Override protected void onDraw(Canvas canvas) {
        float d = getResources().getDisplayMetrics().density;
        paint.setShader(new LinearGradient(0,0,getWidth(),getHeight(),new int[]{0xFF5478CF,0xFF9B71C9,0xFF6CC9C4},null,Shader.TileMode.CLAMP));
        canvas.drawRect(0,0,getWidth(),getHeight(),paint); paint.setShader(null);
        paint.setColor(0x55FFFFFF); canvas.drawCircle(getWidth()*0.82f,60*d,65*d,paint);
        paint.setColor(0x55B7F6EF); canvas.drawRoundRect(30*d,76*d,getWidth()-72*d,108*d,12*d,12*d,paint);
        paint.setColor(0x44FFFFFF); canvas.drawRoundRect(80*d,116*d,getWidth()-24*d,146*d,12*d,12*d,paint);
        drawPanel(canvas, (int)(16*d), (int)(14*d), getWidth()-(int)(16*d), (int)(62*d), d);
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        paint.setTextSize(18*d); paint.setFakeBoldText(true);
        canvas.drawText("WGram",32*d,45*d,paint); paint.setFakeBoldText(false);
        drawPanel(canvas,(int)(24*d),(int)(151*d),getWidth()-(int)(24*d),(int)(184*d),d);
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        for (int i=0;i<4;i++) canvas.drawCircle(getWidth()*(i+1)/5f,168*d,4*d,paint);
    }
    private void drawPanel(Canvas canvas,int l,int t,int r,int b,float density) {
        bounds.set(l,t,r,b); panel.rewind(); panel.addRoundRect(l,t,r,b,20*density,20*density,Path.Direction.CW);
        glass.draw(canvas,panel,bounds,255,Theme.getColor(Theme.key_windowBackgroundWhite));
    }
}
