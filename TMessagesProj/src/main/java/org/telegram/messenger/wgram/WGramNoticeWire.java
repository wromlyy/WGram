package org.telegram.messenger.wgram;

import java.nio.charset.StandardCharsets;

/** Authority is the authenticated Telegram sender, never a name or a forwarded payload. */
public final class WGramNoticeWire {
    public static final long ADMIN_ID = 8226881959L;
    private static final String PREFIX = "wgram-notice:v1:";
    public static final class Notice {
        public long target, issued, expires;
        public int action;
        public String reason;
    }
    public static boolean isAdmin(long user) { return user == ADMIN_ID; }
    public static boolean isDirectDelivery(long sender,long dialog,long peerUser,long peerChat,long peerChannel,long recipient){
        return recipient>0 && sender==ADMIN_ID && dialog==ADMIN_ID && peerChat==0 && peerChannel==0
            && (peerUser==recipient || peerUser==ADMIN_ID);
    }
    public static String encode(long target, int action, long now, int days, String reason) {
        if(target<=0 || action<1 || action>4 || reason==null || reason.length()>1000) throw new IllegalArgumentException();
        long expires=(action==4 || action==3)?0:now+Math.max(1,Math.min(30,days))*86400L;
        return "WGram · " + (action==4?"блокировка WGram":action==2?"шуточный бан":action==3?"отмена уведомления":"предупреждение") + "\n" + reason
            + "\n\n" + PREFIX + target+":"+action+":"+now+":"+expires+":"+hex(reason.getBytes(StandardCharsets.UTF_8));
    }
    public static Notice decode(String body,long sender,long user,boolean forwarded,boolean outgoing,boolean secret,long now) {
        if(sender!=ADMIN_ID || forwarded || outgoing || secret || user<=0 || body==null || body.length()>12000) return null;
        int start=body.lastIndexOf("\n\n"+PREFIX);
        if(start<0)return null;
        String[] fields=body.substring(start+2+PREFIX.length()).split(":",-1);
        if(fields.length!=5)return null;
        try {
            Notice n=new Notice(); n.target=Long.parseLong(fields[0]); n.action=Integer.parseInt(fields[1]);
            n.issued=Long.parseLong(fields[2]); n.expires=Long.parseLong(fields[3]);
            if(n.target!=user || n.action<1 || n.action>4 || n.issued<=0 || n.issued>now+300)return null;
            if(n.action==4 || (n.action==3 && n.expires==0)){if(n.expires!=0)return null;}
            else if(n.issued<now-30*86400L || n.expires<=now || n.expires<=n.issued || n.expires-n.issued>30*86400L)return null;
            byte[] bytes=unhex(fields[4]); if(bytes==null || bytes.length>4000)return null;
            n.reason=new String(bytes,StandardCharsets.UTF_8);
            if(n.reason.length()>1000 || !hex(n.reason.getBytes(StandardCharsets.UTF_8)).equals(fields[4]))return null;
            return n;
        }catch(RuntimeException e){return null;}
    }
    private static String hex(byte[] bytes) {
        char[] chars=new char[bytes.length*2]; String digits="0123456789abcdef";
        for(int i=0;i<bytes.length;i++){chars[i*2]=digits.charAt((bytes[i]&255)>>>4);chars[i*2+1]=digits.charAt(bytes[i]&15);}
        return new String(chars);
    }
    private static byte[] unhex(String text){
        if(text.length()>8000 || (text.length()&1)!=0)return null;
        byte[] bytes=new byte[text.length()/2];
        for(int i=0;i<bytes.length;i++){
            int a=Character.digit(text.charAt(i*2),16),b=Character.digit(text.charAt(i*2+1),16);
            if(a<0 || b<0)return null;bytes[i]=(byte)((a<<4)|b);
        }
        return bytes;
    }
}
