package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.ProfileActivity;
import java.util.ArrayList;

public final class WGramSettingsActivity extends UniversalFragment {
    private static final int GLASS = 1, MODE = 2, OPACITY = 3, APPLY = 4,
        PREMIUM = 5, COLOR = 6, EMOJI = 7, PROFILE = 8, EMOJIS = 9, CLEAR_EMOJI = 10;

    public WGramSettingsActivity(int account) { setCurrentAccount(account); }

    @Override protected CharSequence getTitle() { return getString(R.string.WGramSettings); }

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.WGramGlassHeader)));
        items.add(UItem.asCheck(GLASS, getString(R.string.WGramGlass)).setChecked(WGramConfig.isGlassEnabled()));
        items.add(UItem.asShadow(getString(R.string.WGramGlassInfo)));
        if (WGramConfig.isGlassEnabled()) {
            items.add(UItem.asButton(MODE, getString(R.string.WGramGlassMode),
                getString(WGramConfig.getGlassMode() == WGramConfig.GLASS_LIGHT ? R.string.WGramGlassLight : R.string.WGramGlassAuto)));
            items.add(UItem.asButton(OPACITY, getString(R.string.WGramGlassOpacity), WGramConfig.getGlassOpacity() + "%"));
        }
        items.add(UItem.asButton(APPLY, getString(R.string.WGramGlassApply)).accent());
        items.add(UItem.asShadow(getString(R.string.WGramGlassApplyInfo)));
        items.add(UItem.asHeader(getString(R.string.WGramProfileHeader)));
        items.add(UItem.asCheck(PREMIUM, getString(R.string.WGramVisualPremium)).setChecked(WGramConfig.isVisualPremiumEnabled(currentAccount)));
        if (WGramConfig.isVisualPremiumEnabled(currentAccount)) {
            items.add(UItem.asButton(COLOR, getString(R.string.WGramProfileColor)));
            items.add(UItem.asButton(EMOJI, getString(R.string.WGramProfileEmoji), getString(
                WGramConfig.getProfileEmoji(currentAccount) == 0 ? R.string.WGramProfileEmojiEmpty : R.string.WGramProfileEmojiChosen)));
            if (WGramConfig.getProfileEmoji(currentAccount) != 0) {
                items.add(UItem.asButton(CLEAR_EMOJI, getString(R.string.WGramRemoveEmoji)));
            }
        }
        items.add(UItem.asButton(PROFILE, getString(R.string.WGramProfilePreview)));
        items.add(UItem.asShadow(getString(R.string.WGramVisualPremiumInfo)));
        items.add(UItem.asHeader(getString(R.string.WGramEmojisHeader)));
        items.add(UItem.asCheck(EMOJIS, getString(R.string.WGramEmojis)).setChecked(WGramConfig.areEmojisEnabled(currentAccount)));
        items.add(UItem.asShadow(getString(R.string.WGramEmojisInfo)));
        items.add(UItem.asCenterShadow(getString(R.string.WGramAbout)));
    }

    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case GLASS:
                WGramConfig.setGlassEnabled(!WGramConfig.isGlassEnabled());
                break;
            case MODE:
                showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.WGramGlassMode))
                    .setItems(new CharSequence[]{getString(R.string.WGramGlassAuto), getString(R.string.WGramGlassLight)}, (dialog, which) -> {
                        WGramConfig.setGlassMode(which);
                        refresh();
                    }).create());
                return;
            case OPACITY:
                final int[] values = {60, 75, 90, 95};
                showDialog(new AlertDialog.Builder(getParentActivity())
                    .setTitle(getString(R.string.WGramGlassOpacity))
                    .setItems(new CharSequence[]{"60%", "75%", "90%", "95%"}, (dialog, which) -> {
                        WGramConfig.setGlassOpacity(values[which]);
                        refresh();
                    }).create());
                return;
            case APPLY:
                if (getParentLayout() != null) getParentLayout().rebuildAllFragmentViews(true, true);
                return;
            case PREMIUM:
                WGramConfig.setVisualPremiumEnabled(currentAccount, !WGramConfig.isVisualPremiumEnabled(currentAccount));
                break;
            case COLOR:
                showColors();
                return;
            case EMOJI:
                presentFragment(new WGramEmojiPickerActivity(currentAccount));
                return;
            case CLEAR_EMOJI:
                WGramConfig.setProfileEmoji(currentAccount, 0);
                break;
            case PROFILE:
                Bundle args = new Bundle();
                args.putLong("user_id", getUserConfig().getClientUserId());
                ProfileActivity profile = new ProfileActivity(args);
                profile.setCurrentAccount(currentAccount);
                presentFragment(profile);
                return;
            case EMOJIS:
                WGramConfig.setEmojisEnabled(currentAccount, !WGramConfig.areEmojisEnabled(currentAccount));
                break;
            default: return;
        }
        profileChanged();
        refresh();
    }

    private void showColors() {
        ArrayList<CharSequence> names = new ArrayList<>();
        ArrayList<Integer> ids = new ArrayList<>();
        names.add(getString(R.string.WGramDefault));
        ids.add(-1);
        MessagesController.PeerColors palette = getMessagesController().profilePeerColors;
        if (palette != null) {
            for (MessagesController.PeerColor color : palette.colors) {
                SpannableString swatch = new SpannableString("●  ●  ●");
                swatch.setSpan(new ForegroundColorSpan(color.getColor1(Theme.isCurrentThemeDark())), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                swatch.setSpan(new ForegroundColorSpan(color.getColor2(Theme.isCurrentThemeDark())), 3, 4, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                swatch.setSpan(new ForegroundColorSpan(color.getColor3(Theme.isCurrentThemeDark())), 6, 7, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                names.add(swatch);
                ids.add(color.id);
            }
        }
        showDialog(new AlertDialog.Builder(getParentActivity())
            .setTitle(getString(R.string.WGramProfileColor))
            .setItems(names.toArray(new CharSequence[0]), (dialog, which) -> {
                WGramConfig.setProfileColor(currentAccount, ids.get(which));
                profileChanged();
                refresh();
            }).create());
    }

    private void profileChanged() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
    }
    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        if (fragmentView != null) fragmentView.invalidate();
    }
    @Override public void onResume() { super.onResume(); profileChanged(); refresh(); }
    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
}
