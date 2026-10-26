package org.telegram.ui.wgram;

import static org.telegram.messenger.LocaleController.getString;
import android.view.View;
import org.telegram.messenger.R;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import java.util.ArrayList;

public final class WGramSettingsActivity extends UniversalFragment {
    private static final int GLASS = 1, MODE = 2, OPACITY = 3, APPLY = 4,
        EMOJIS = 9,
        TINT=11, HIGHLIGHT=12, REFRACTION=13, RIM=14, SHADOW=15, RESET=16, PRESET=17,
        HISTORY=20, OPEN_HISTORY=21, RETENTION=22, CLEAR_HISTORY=23,
        GHOST_READ=30, GHOST_TYPING=31, CALLS=32, SECONDS=33, COPY_ID=34, GHOST_ONLINE=35, GHOST_STORIES=36, ADMIN=40, NEWS=60, GLASS_PAGE=101, HISTORY_PAGE=102, GHOST_PAGE=103, EXTRAS_PAGE=104, ADVANCED=105, CHAT_STYLE=106;
    private static final int PLUGINS=61, ADS=70, HIDE_PHONE=71, HIDE_STORY_BAR=72,
        REAR_CAMERA=73, KEEP_SCREEN=74, BIG_EMOJI=75, STICKER_HINTS=76,
        INAPP_CAMERA=77, PAUSE_MUSIC=78, RAISE_LISTEN=79;
    private int page;
    private WGramChatStylePreview chatPreview;
    private boolean advanced;
    private WGramGlassPreview preview;

    public WGramSettingsActivity(int account) { this(account, 0); }
    public WGramSettingsActivity(int account, int page) { setCurrentAccount(account); this.page=page; }

    @Override protected CharSequence getTitle(){
        return getString(page==GLASS_PAGE?R.string.WGramGlassHeader:page==HISTORY_PAGE?R.string.WGramMessagesPrivacy:
            page==GHOST_PAGE?R.string.WGramGhost:page==EXTRAS_PAGE?R.string.WGramExtras:R.string.WGramSettings);
    }

    @Override protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if(page==0){
            items.add(UItem.asHeader("WGram 0.4"));
            items.add(UItem.asShadow(getString(R.string.WGramSettingsWelcome)));
            items.add(UItem.asButton(GLASS_PAGE,R.drawable.wgram_logo,getString(R.string.WGramGlassHeader)));
            items.add(UItem.asButton(50,R.drawable.msg_settings,getString(R.string.WGramCustomizeProfile)));
            items.add(UItem.asButton(HISTORY_PAGE,R.drawable.msg_recent,getString(R.string.WGramMessagesPrivacy)));
            items.add(UItem.asButton(EXTRAS_PAGE,R.drawable.msg_settings,getString(R.string.WGramExtras)));
            items.add(UItem.asButton(PLUGINS,R.drawable.msg_add,getString(R.string.WGramPlugins)));
            if(org.telegram.messenger.wgram.WGramNoticeWire.isAdmin(getUserConfig().getClientUserId())){
                items.add(UItem.asButton(ADMIN,R.drawable.wgram_logo,getString(R.string.WGramAdmin)));
            }
            items.add(UItem.asShadow(""));
            items.add(UItem.asButton(NEWS,R.drawable.wgram_logo,getString(R.string.WGramNews),"@WGramnews"));
            return;
        }
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
        if(page==EXTRAS_PAGE){
        items.add(UItem.asHeader(getString(R.string.WGramExtras)));
        items.add(UItem.asCheck(CALLS,getString(R.string.WGramConfirmCalls)).setChecked(WGramConfig.confirmCalls(currentAccount)));
        items.add(UItem.asCheck(SECONDS,getString(R.string.WGramTimeSeconds)).setChecked(WGramConfig.showSeconds(currentAccount)));
        items.add(UItem.asButton(COPY_ID,getString(R.string.WGramMyId),""+getUserConfig().getClientUserId()));
            items.add(UItem.asCheck(EMOJIS,getString(R.string.WGramEmojis)).setChecked(WGramConfig.areEmojisEnabled(currentAccount)));
            items.add(UItem.asShadow(getString(R.string.WGramEmojisInfo)));
        }

        if(page==HISTORY_PAGE){
            items.add(UItem.asCheck(ADS,getString(R.string.WGramHideAds)).setChecked(WGramConfig.hideAds(currentAccount)));
            items.add(UItem.asCheck(HIDE_PHONE,getString(R.string.WGramHidePhone)).setChecked(WGramConfig.hidePhone(currentAccount)));
            items.add(UItem.asShadow(getString(R.string.WGramHidePhoneInfo)));
        }
        if(page==EXTRAS_PAGE){
            items.add(UItem.asHeader(getString(R.string.WGramInterface)));
            items.add(UItem.asCheck(HIDE_STORY_BAR,getString(R.string.WGramHideStoryBar)).setChecked(WGramConfig.hideStoryBar(currentAccount)));
            items.add(UItem.asCheck(KEEP_SCREEN,getString(R.string.WGramKeepScreen)).setChecked(WGramConfig.keepScreenOn(currentAccount)));
            items.add(UItem.asCheck(BIG_EMOJI,getString(R.string.WGramBigEmoji)).setChecked(org.telegram.messenger.SharedConfig.allowBigEmoji));
            items.add(UItem.asCheck(STICKER_HINTS,getString(R.string.WGramStickerHints)).setChecked(org.telegram.messenger.SharedConfig.suggestStickers!=2));
            items.add(UItem.asHeader(getString(R.string.WGramMedia)));
            items.add(UItem.asCheck(REAR_CAMERA,getString(R.string.WGramRearCamera)).setChecked(WGramConfig.rearVideoCamera()));
            items.add(UItem.asCheck(INAPP_CAMERA,getString(R.string.WGramInAppCamera)).setChecked(org.telegram.messenger.SharedConfig.inappCamera));
            items.add(UItem.asCheck(PAUSE_MUSIC,getString(R.string.WGramPauseMusic)).setChecked(org.telegram.messenger.SharedConfig.pauseMusicOnRecord));
            items.add(UItem.asCheck(RAISE_LISTEN,getString(R.string.WGramRaiseListen)).setChecked(org.telegram.messenger.SharedConfig.raiseToListen));
            items.add(UItem.asShadow(getString(R.string.WGramGlobalMediaInfo)));
        }
    }

    @Override protected void onClick(UItem item, View view, int position, float x, float y) {
        switch (item.id) {
            case GLASS_PAGE: case HISTORY_PAGE: case GHOST_PAGE: case EXTRAS_PAGE:
                presentFragment(new WGramSettingsActivity(currentAccount,item.id));return;
            case PLUGINS: presentFragment(new WGramPluginsActivity(currentAccount)); return;
            case ADS: WGramConfig.setHideAds(currentAccount,!WGramConfig.hideAds(currentAccount)); break;
            case HIDE_PHONE: WGramConfig.setHidePhone(currentAccount,!WGramConfig.hidePhone(currentAccount)); break;
            case HIDE_STORY_BAR: WGramConfig.setHideStoryBar(currentAccount,!WGramConfig.hideStoryBar(currentAccount)); break;
            case KEEP_SCREEN: WGramConfig.setKeepScreenOn(currentAccount,!WGramConfig.keepScreenOn(currentAccount)); break;
            case REAR_CAMERA: WGramConfig.setRearVideoCamera(!WGramConfig.rearVideoCamera()); break;
            case BIG_EMOJI: org.telegram.messenger.SharedConfig.toggleBigEmoji(); break;
            case STICKER_HINTS: org.telegram.messenger.SharedConfig.setSuggestStickers(org.telegram.messenger.SharedConfig.suggestStickers==2?0:2); break;
            case INAPP_CAMERA: org.telegram.messenger.SharedConfig.toggleInappCamera(); break;
            case PAUSE_MUSIC: org.telegram.messenger.SharedConfig.togglePauseMusicOnRecord(); break;
            case RAISE_LISTEN: org.telegram.messenger.SharedConfig.toggleRaiseToListen(); break;
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
            case EMOJIS:
                WGramConfig.setEmojisEnabled(currentAccount, !WGramConfig.areEmojisEnabled(currentAccount));
                break;
            default: return;
        }
        refresh();
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
        WGramConfig.historyCleared(currentAccount);
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

    private void refresh() {
        if (listView != null && listView.adapter != null) listView.adapter.update(true);
        if (fragmentView != null) fragmentView.invalidate();
        if (preview != null) preview.invalidate();
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    @Override protected boolean onLongClick(UItem item, View view, int position, float x, float y) { return false; }
}
