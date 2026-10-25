import org.telegram.messenger.wgram.WGramHistoryPolicy;
import org.telegram.messenger.wgram.WGramNoticeWire;
import org.telegram.messenger.wgram.WGramConfig;
import org.telegram.messenger.UserConfig;
public final class WGramPrivacyTest {
    static int checks;
    static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
    public static void main(String[] args){
        long now=1900000000L, user=1234567890123L, admin=WGramNoticeWire.ADMIN_ID;
        check(WGramNoticeWire.isDirectDelivery(admin,admin,user,0,0,user),"full incoming private message accepted");
        check(WGramNoticeWire.isDirectDelivery(admin,admin,admin,0,0,user),"compact incoming private message accepted");
        check(!WGramNoticeWire.isDirectDelivery(admin,admin,admin,12,0,user),"group transport rejected");
        check(!WGramNoticeWire.isDirectDelivery(admin,admin,admin,0,12,user),"channel transport rejected");
        check(!WGramNoticeWire.isDirectDelivery(admin,admin,user+1,0,0,user),"other peer rejected");
        check(!WGramNoticeWire.isDirectDelivery(admin,-12,admin,0,0,user),"non-admin dialog rejected");
        String body=WGramNoticeWire.encode(user,2,now,7,"Помощь проекту 😀");
        WGramNoticeWire.Notice notice=WGramNoticeWire.decode(body,admin,user,false,false,false,now);
        check(notice!=null && notice.reason.equals("Помощь проекту 😀") && notice.target==user,"64-bit IDs and UTF-8 reason roundtrip");
        check(WGramNoticeWire.decode(body,admin+1,user,false,false,false,now)==null,"untrusted sender rejected");
        check(WGramNoticeWire.decode(body,admin,user+1,false,false,false,now)==null,"other recipient rejected");
        check(WGramNoticeWire.decode(body,admin,user,true,false,false,now)==null,"forward rejected");
        check(WGramNoticeWire.decode(body,admin,user,false,true,false,now)==null,"outgoing self-trigger rejected");
        check(WGramNoticeWire.decode(body,admin,user,false,false,true,now)==null,"secret transport rejected");
        check(WGramNoticeWire.decode(body,admin,user,false,false,false,now+7*86400L)==null,"expired notice rejected");
        check(WGramNoticeWire.decode(body,admin,user,false,false,false,now-301)==null,"future issue time rejected");
        check(WGramNoticeWire.decode(body+"junk",admin,user,false,false,false,now)==null,"malformed trailer rejected");
        check(WGramNoticeWire.decode(body.replace(":"+user+":",":0:"),admin,user,false,false,false,now)==null,"zero target rejected");
        check(WGramNoticeWire.decode(WGramNoticeWire.encode(user,3,now,1,"Отмена"),admin,user,false,false,false,now).action==3,"revoke recognized");
        check(WGramNoticeWire.decode(WGramNoticeWire.encode(user,1,now,999,"test"),admin,user,false,false,false,now).expires==now+30*86400L,"duration capped");
        String lock=WGramNoticeWire.encode(user,4,now,0,"Свой текст блокировки");
        check(WGramNoticeWire.decode(lock,admin,user,false,false,false,now+365*86400L).action==4,"persistent block valid after a year");
        check(WGramNoticeWire.decode(lock,admin+1,user,false,false,false,now)==null,"forged persistent block rejected");
        check(WGramNoticeWire.decode(lock,admin,user+1,false,false,false,now)==null,"persistent block bound to recipient");
        check(WGramNoticeWire.decode(lock,admin,user,false,false,false,now).expires==0,"persistent block has no expiry");
        check(WGramHistoryPolicy.canKeep(1,1,"text",false,0,0,false,false),"ordinary received text eligible");
        check(!WGramHistoryPolicy.canKeep(1,1,"text",true,0,0,false,false),"secret excluded");
        check(!WGramHistoryPolicy.canKeep(1,1,"text",false,86400,0,false,false),"auto-delete excluded");
        check(!WGramHistoryPolicy.canKeep(1,1,"text",false,0,Integer.MAX_VALUE,false,false),"view-once excluded");
        check(!WGramHistoryPolicy.canKeep(1,1,"text",false,0,0,true,false),"protected excluded");
        check(!WGramHistoryPolicy.canKeep(1,1,"text",false,0,0,false,true),"ephemeral excluded");
        check(!WGramHistoryPolicy.canKeep(1,-1,"text",false,0,0,false,false),"unsent draft excluded");
        check(!WGramHistoryPolicy.canKeep(1,1,"",false,0,0,false,false),"empty text excluded");
        StringBuilder text=new StringBuilder();for(int i=0;i<4095;i++)text.append('x');text.append("😀tail");
        String clipped=WGramHistoryPolicy.clip(text.toString());
        check(clipped.length()==4095&&!Character.isHighSurrogate(clipped.charAt(clipped.length()-1)),"safe UTF16 archive cap");
        check(WGramHistoryPolicy.cutoff(now,7)==now-7*86400L,"retention cutoff");
        UserConfig.getInstance(0).userId=9001;UserConfig.getInstance(1).userId=9002;
        check(!WGramConfig.hideRead(0)&&!WGramConfig.hideOnline(0)&&!WGramConfig.hideStories(0)&&!WGramConfig.hideTyping(0),"ghost defaults off");
        check(!WGramConfig.isHistoryEnabled(0),"archive default off");
        WGramConfig.setHideRead(0,true);WGramConfig.setHideOnline(0,true);WGramConfig.setHistoryEnabled(0,true);
        check(!WGramConfig.hideRead(1)&&!WGramConfig.hideOnline(1)&&!WGramConfig.isHistoryEnabled(1),"privacy preferences isolate accounts");
        UserConfig.getInstance(0).userId=9003;
        check(!WGramConfig.hideRead(0)&&!WGramConfig.isHistoryEnabled(0),"slot reuse does not leak privacy preferences");
        WGramConfig.setGlassHighlight(-10);WGramConfig.setGlassRefraction(1000);WGramConfig.setGlassTint(100);
        check(WGramConfig.getGlassHighlight()==0&&WGramConfig.getGlassRefraction()==100&&WGramConfig.getGlassTint()==4,"glass bounds");
        WGramConfig.resetGlass();check(WGramConfig.getGlassTint()==0&&WGramConfig.getGlassHighlight()==65&&WGramConfig.hasGlassRim(),"glass reset");
        System.out.println("PASS: "+checks+" history, sender authentication, retention and account checks");
    }
}
