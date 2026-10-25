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
        PREMIUM = 5, COLOR = 6, EMOJI = 7, PROFILE = 8, EMOJIS = 9, CLEAR_EMOJI = 10,
        TINT=11, HIGHLIGHT=12, REFRACTION=13, RIM=14, SHADOW=15, RESET=16, PRESET=17,
        HISTORY=20, OPEN_HISTORY=21, RETENTION=22, CLEAR_HISTORY=23,
        GHOST_READ=30, GHOST_TYPING=31, CALLS=32, SECONDS=33, COPY_ID=34, GHOST_ONLINE=35, GHOST_STORIES=36, ADMIN=40, NEWS=60, GLASS_PAGE=101, HISTORY_PAGE=102, GHOST_PAGE=103, EXTRAS_PAGE=104, ADVANCED=105, CHAT_STYLE=106;
    private int page;
    private WGramChatStylePreview chatPreview;
    private boolean advanced;
    private WGramGlassPreview preview;

    public WGramSettingsActivity(int account) { this(account, 0); }
    public WGramSettingsActivity(int account, int page) { setCurrentAccount(account); this.page=page; }

    @Override protected CharSequence getTitle(){return getString(R.string.WGramSettings);}

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader("WGram 0.3"));
        items.add(UItem.asShadow(getString(R.string.WGramSettingsWelcome)));
        items.add(UItem.asButton(GLASS_PAGE,R.drawable.wgram_logo,getString(R.string.WGramGlassHeader),getString(WGramConfig.isGlassEnabled()?R.string.WGramOn:R.string.WGramOff)));
        items.add(UItem.asButton(50,R.drawable.msg_settings,getString(R.string.WGramCustomizeProfile)));
        items.add(UItem.asButton(HISTORY_PAGE,R.drawable.msg_recent,getString(R.string.WGramMessagesPrivacy),getString(WGramConfig.isHistoryEnabled(currentAccount)?R.string.WGramOn:R.string.WGramOff)));
        if(org.telegram.messenger.wgram.WGramNoticeWire.isAdmin(getUserConfig().getClientUserId())){
            items.add(UItem.asButton(ADMIN,R.drawable.wgram_logo,getString(R.string.WGramAdmin)));
            items.add(UItem.asShadow(getString(R.string.WGramAdminHomeInfo)));
        }
        items.add(UItem.asButton(NEWS,R.drawable.wgram_logo,getString(R.string.WGramNews),"@WGramnews"));
        items.add(UItem.asShadow(getString(R.string.WGramNewsInfo)));

        if(page==GLASS_PAGE){
        if (preview == null && getContext() != null) preview = new WGramGlassPreview(getContext());
        if (preview != null) items.add(UItem.asCustomShadow(preview));
        items.add(UItem.asHeader(getString(R.string.WGramGlassHeader)));
        items.add(UItem.asCheck(GLASS, getString(R.string.WGramGlass)).setChecked(WGramConfig.isGlassEnabled()));
        items.add(UItem.asShadow(getString(R.string.WGramGlassInfo)));
        if (WGramConfig.isGlassEnabled()) {
            items.add(UItem.asButton(MODE, getString(R.string.WGramGlassMode),
                getString(WGramConfig.getGlassMode() == WGramConfig.GLASS_LIGHT ? R.string.WGramGlassLight : R.string.WGramGlassAuto)));
            items.add(UItem.asButton(PRESET, getString(R.string.WGramGlassPresets)));
            items.add(UItem.asCheck(ADVANCED,getString(R.string.WGramAdvanced)).setChecked(advanced));
            if(advanced){
            items.add(UItem.asButton(OPACITY, getString(R.string.WGramGlassOpacity), WGramConfig.getGlassOpacity() + "%"));
            items.add(UItem.asButton(TINT, getString(R.string.WGramGlassTint), tintNames()[WGramConfig.getGlassTint()]));
            items.add(UItem.asButton(HIGHLIGHT, getString(R.string.WGramGlassHighlight), WGramConfig.getGlassHighlight()+"%"));
            items.add(UItem.asButton(REFRACTION, getString(R.string.WGramGlassRefraction), WGramConfig.getGlassRefraction()+"%"));
            items.add(UItem.asCheck(RIM,getString(R.string.WGramGlassRim)).setChecked(WGramConfig.hasGlassRim()));
            items.add(UItem.asCheck(SHADOW,getString(R.string.WGramGlassShadow)).setChecked(WGramConfig.hasGlassShadow()));
            items.add(UItem.asButton(RESET,getString(R.string.WGramGlassReset)));
            }
        }
        items.add(UItem.asButton(APPLY, getString(R.string.WGramGlassApply)).accent());
        items.add(UItem.asShadow(getString(R.string.WGramGlassApplyInfo)));
        items.add(UItem.asHeader(getString(R.string.WGramChatStyles)));
        if(chatPreview==null && getContext()!=null)chatPreview=new WGramChatStylePreview(getContext());
        if(chatPreview!=null)items.add(UItem.asCustomShadow(chatPreview));
        items.add(UItem.asButton(CHAT_STYLE,getString(R.string.WGramChatStyles),styleNames()[WGramConfig.getChatStyle()]));
        items.add(UItem.asShadow(getString(R.string.WGramChatStylesInfo)));
        }
        if(page==HISTORY_PAGE){
        items.add(UItem.asHeader(getString(R.string.WGramHistory)));
        items.add(UItem.asCheck(HISTORY,getString(R.string.WGramSaveHistory)).setChecked(WGramConfig.isHistoryEnabled(currentAccount)));
        items.add(UItem.asButton(OPEN_HISTORY,getString(R.string.WGramOpenHistory)));
        items.add(UItem.asButton(RETENTION,getString(R.string.WGramRetention),WGramConfig.getHistoryDays(currentAccount)+" "+getString(R.string.WGramDays)));
        items.add(UItem.asButton(CLEAR_HISTORY,getString(R.string.WGramClearHistory)));
        items.add(UItem.asShadow(getString(R.string.WGramHistoryInlineInfo)));
        items.add(UItem.asShadow(getString(R.string.WGramHistoryPrivacy)));
        }
        if(page==HISTORY_PAGE || page==GHOST_PAGE){
        items.add(UItem.asHeader(getString(R.string.WGramGhost)));
        items.add(UItem.asCheck(GHOST_READ,getString(R.string.WGramGhostRead)).setChecked(WGramConfig.hideRead(currentAccount)));
        items.add(UItem.asCheck(GHOST_TYPING,getString(R.string.WGramGhostTyping)).setChecked(WGramConfig.hideTyping(currentAccount)));
        items.add(UItem.asCheck(GHOST_ONLINE,getString(R.string.WGramGhostOnline)).setChecked(WGramConfig.hideOnline(currentAccount)));
        items.add(UItem.asCheck(GHOST_STORIES,getString(R.string.WGramGhostStories)).setChecked(WGramConfig.hideStories(currentAccount)));
        items.add(UItem.asShadow(getString(R.string.WGramGhostInfo)));
        }
        if(page==HISTORY_PAGE || page==EXTRAS_PAGE){
        items.add(UItem.asHeader(getString(R.string.WGramExtras)));
        items.add(UItem.asCheck(CALLS,getString(R.string.WGramConfirmCalls)).setChecked(WGramConfig.confirmCalls(currentAccount)));
        items.add(UItem.asCheck(SECONDS,getString(R.string.WGramTimeSeconds)).setChecked(WGramConfig.showSeconds(currentAccount)));
        items.add(UItem.asButton(COPY_ID,getString(R.string.WGramMyId),""+getUserConfig().getClientUserId()));
            items.add(UItem.asCheck(EMOJIS,getString(R.string.WGramEmojis)).setChecked(WGramConfig.areEmojisEnabled(currentAccount)));
            items.add(UItem.asShadow(getString(R.string.WGramEmojisInfo)));
        }

    }

    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case GLASS_PAGE: case HISTORY_PAGE: case GHOST_PAGE: case EXTRAS_PAGE:
                page=page==item.id?0:item.id;refresh();return;
            case CHAT_STYLE:
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramChatStyles))
                    .setItems(styleNames(),(d,w)->{WGramConfig.setChatStyle(w);if(chatPreview!=null)chatPreview.invalidate();refresh();}).create());return;
            case ADVANCED: advanced=!advanced;refresh();return;
            case NEWS: org.telegram.messenger.browser.Browser.openUrl(getParentActivity(),"https://t.me/WGramnews");return;
            case 50: presentFragment(new WGramProfileEditorActivity(currentAccount)); return;
            case TINT:
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramGlassTint))
                    .setItems(tintNames(),(d,w)->{WGramConfig.setGlassTint(w);refresh();}).create()); return;
            case HIGHLIGHT: choosePercent(true); return;
            case REFRACTION: choosePercent(false); return;
            case RIM: WGramConfig.setGlassRim(!WGramConfig.hasGlassRim()); break;
            case SHADOW: WGramConfig.setGlassShadow(!WGramConfig.hasGlassShadow()); break;
            case RESET: WGramConfig.resetGlass(); break;
            case PRESET:
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramGlassPresets))
                    .setItems(new CharSequence[]{getString(R.string.WGramCrystal),getString(R.string.WGramAurora),getString(R.string.WGramSoft)},(d,w)->{
                        WGramConfig.resetGlass();
                        WGramConfig.setGlassOpacity(w==0?60:w==1?75:90);
                        WGramConfig.setGlassHighlight(w==0?100:w==1?75:25);
                        WGramConfig.setGlassTint(w==1?2:0);
                        WGramConfig.setGlassRefraction(w==0?100:w==1?75:25); refresh();
                    }).create()); return;
            case HISTORY:
                boolean enabled = !WGramConfig.isHistoryEnabled(currentAccount);
                WGramConfig.setHistoryEnabled(currentAccount,enabled);
                if(!enabled) clearHistory();
                break;
            case OPEN_HISTORY: presentFragment(new WGramHistoryActivity(currentAccount,0)); return;
            case RETENTION:
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramRetention))
                    .setItems(new CharSequence[]{"7 "+getString(R.string.WGramDays),"30 "+getString(R.string.WGramDays)},(d,w)->{WGramConfig.setHistoryDays(currentAccount,w==0?7:30);refresh();}).create());return;
            case CLEAR_HISTORY:
                showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramClearHistory))
                    .setMessage(getString(R.string.WGramClearHistoryQuestion)).setPositiveButton(getString(R.string.Delete),(d,w)->clearHistory())
                    .setNegativeButton(getString(R.string.Cancel),null).create());return;
            case GHOST_READ: toggleGhost(true); return;
            case GHOST_TYPING: toggleGhost(false); return;
            case GHOST_ONLINE: toggleExtraGhost(true); return;
            case GHOST_STORIES: toggleExtraGhost(false); return;
            case CALLS: WGramConfig.setConfirmCalls(currentAccount,!WGramConfig.confirmCalls(currentAccount)); break;
            case SECONDS: WGramConfig.setShowSeconds(currentAccount,!WGramConfig.showSeconds(currentAccount)); break;
            case COPY_ID: org.telegram.messenger.AndroidUtilities.addToClipboard(""+getUserConfig().getClientUserId()); return;
            case ADMIN: presentFragment(new WGramAdminActivity(currentAccount)); return;
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
        if(item.id==PREMIUM || item.id==CLEAR_EMOJI)profileChanged();
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

    private CharSequence[] styleNames(){return new CharSequence[]{getString(R.string.WGramChatClassic),getString(R.string.WGramChatDiscord),getString(R.string.WGramChatRound),getString(R.string.WGramChatCompact)};}
    private CharSequence[] tintNames() {
        return new CharSequence[]{getString(R.string.WGramNeutral),getString(R.string.WGramBlue),getString(R.string.WGramLilac),getString(R.string.WGramMint),getString(R.string.WGramPeach)};
    }
    private void choosePercent(boolean highlight) {
        final int[] values={0,25,50,75,100};
        showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(highlight?R.string.WGramGlassHighlight:R.string.WGramGlassRefraction))
            .setItems(new CharSequence[]{"0%","25%","50%","75%","100%"},(d,w)->{
                if(highlight)WGramConfig.setGlassHighlight(values[w]);else WGramConfig.setGlassRefraction(values[w]);refresh();
            }).create());
    }
    private void toggleExtraGhost(boolean online) {
        boolean value=online?WGramConfig.hideOnline(currentAccount):WGramConfig.hideStories(currentAccount);
        Runnable toggle=()->{if(online)WGramConfig.setHideOnline(currentAccount,!value);else WGramConfig.setHideStories(currentAccount,!value);refresh();};
        if(value)toggle.run();else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramGhost))
            .setMessage(getString(R.string.WGramGhostInfo)).setPositiveButton(getString(R.string.Enable),(d,w)->toggle.run()).setNegativeButton(getString(R.string.Cancel),null).create());
    }
    private void clearHistory() {
        final long user=getUserConfig().getClientUserId();
        getMessagesStorage().getStorageQueue().postRunnable(()->org.telegram.messenger.wgram.WGramHistory.clear(user));
    }
    private void toggleGhost(boolean read) {
        boolean value=read?WGramConfig.hideRead(currentAccount):WGramConfig.hideTyping(currentAccount);
        Runnable toggle=()->{if(read)WGramConfig.setHideRead(currentAccount,!value);else WGramConfig.setHideTyping(currentAccount,!value);refresh();};
        if(value)toggle.run();
        else showDialog(new AlertDialog.Builder(getParentActivity()).setTitle(getString(R.string.WGramGhost))
            .setMessage(getString(R.string.WGramGhostInfo)).setPositiveButton(getString(R.string.Enable),(d,w)->toggle.run())
            .setNegativeButton(getString(R.string.Cancel),null).create());
    }

    private void profileChanged() {
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
    }
    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        if (fragmentView != null) fragmentView.invalidate();
        if (preview != null) preview.invalidate();
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
}
