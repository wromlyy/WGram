package org.telegram.messenger.wgram;
import org.telegram.tgnet.TLRPC;
public final class WGramSharedProfiles {
 public static final class Profile { public long badgeEmoji, emoji; public boolean premium; public int color=-1; }
 public static WGramSharedProfiles get(int a){return new WGramSharedProfiles();}
 public Profile peek(long id){return null;}
 public static boolean hasPremium(int a, TLRPC.User u){return false;}
 public static long status(int a, TLRPC.User u){return 0;}
 public static int color(int a, TLRPC.User u){return -1;}
}
