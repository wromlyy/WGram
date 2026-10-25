package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import org.telegram.messenger.R;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;

public final class WGramEmojiPickerActivity extends BaseFragment {
    private EmojiView emojiView;

    public WGramEmojiPickerActivity(int account) { setCurrentAccount(account); }

    @Override
    public View createView(Context context) {
        actionBar.setTitle(getString(R.string.WGramProfileEmoji));
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override public void onItemClick(int id) { if (id == -1) finishFragment(); }
        });
        FrameLayout root = new FrameLayout(context);
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        emojiView = new EmojiView(this, true, false, false, context, true, null, root, true, getResourceProvider(), false);
        emojiView.allowEmojisForNonPremium(true);
        emojiView.shouldDrawStickerSettings = false;
        emojiView.setDelegate(new EmojiView.EmojiViewDelegate() {
            @Override public void onEmojiSelected(String emoji) {
                Toast.makeText(context, getString(R.string.WGramChooseEmoji), Toast.LENGTH_SHORT).show();
            }
            @Override public void onCustomEmojiSelected(long documentId, TLRPC.Document document, String emoticon, boolean recent) {
                WGramConfig.setProfileEmoji(currentAccount, documentId);
                finishFragment();
            }
        });
        root.addView(emojiView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emojiView.setShowing(true);
        return fragmentView = root;
    }

    @Override public void onFragmentDestroy() {
        if (emojiView != null) { emojiView.setShowing(false); emojiView.onDestroy(); }
        super.onFragmentDestroy();
    }
}
