package org.telegram.ui.wgram;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.wgram.WGramNoticeWire;
import org.telegram.ui.ActionBar.BaseFragment;

public final class WGramAppeal {
    private WGramAppeal(){}
    public static void show(BaseFragment fragment,int account){
        WGramUi.input(fragment,LocaleController.getString(R.string.WGramAppeal),"",false,1000,text->{
            if(text.isEmpty())return;
            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(
                "WGram · обжалование блокировки\n\n"+text,WGramNoticeWire.ADMIN_ID));
        });
    }
}
