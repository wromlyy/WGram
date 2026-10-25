package org.telegram.ui.wgram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.ui.ActionBar.Theme;

/** Lightweight example of the same rounded/flat message surfaces used by the chat renderer. */
public final class WGramChatStylePreview extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect=new RectF();
    public WGramChatStylePreview(Context context){super(context);setContentDescription(LocaleController.getString(R.string.WGramChatExample));}
    @Override protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),AndroidUtilities.dp(154));}
    @Override protected void onDraw(Canvas canvas){
        int style=WGramConfig.getChatStyle();float d=AndroidUtilities.density;
        paint.setColor(Theme.getColor(Theme.key_windowBackgroundGray));canvas.drawColor(paint.getColor());
        paint.setTextSize(12*d);paint.setColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        canvas.drawText(LocaleController.getString(R.string.WGramChatExample),18*d,22*d,paint);
        draw(canvas,18*d,36*d,getWidth()-70*d,82*d,false,style);
        draw(canvas,64*d,92*d,getWidth()-18*d,138*d,true,style);
    }
    private void draw(Canvas c,float left,float top,float right,float bottom,boolean out,int style){
        rect.set(left,top,right,bottom);
        paint.setColor(Theme.getColor(out?Theme.key_chat_outBubble:Theme.key_chat_inBubble));
        float radius=AndroidUtilities.dp(style==1?3:style==2?22:style==3?8:17);
        c.drawRoundRect(rect,radius,radius,paint);
        paint.setColor(Theme.getColor(out?Theme.key_chat_messageTextOut:Theme.key_chat_messageTextIn));paint.setTextSize(14*AndroidUtilities.density);
        c.drawText(LocaleController.getString(out?R.string.WGramChatExampleReply:R.string.WGramChatExampleMessage),left+12*AndroidUtilities.density,top+28*AndroidUtilities.density,paint);
    }
}
