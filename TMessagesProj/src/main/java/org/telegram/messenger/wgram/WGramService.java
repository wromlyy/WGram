package org.telegram.messenger.wgram;

import android.content.SharedPreferences;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DispatchQueue;
import org.telegram.messenger.UserConfig;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/** HTTPS transport. The bot token and Telegram login credentials never enter the client. */
public final class WGramService {
    public interface Result { void done(JSONObject data,String error); }
    public interface BytesResult { void done(byte[] data,String error); }
    private static final DispatchQueue queue=new DispatchQueue("wgram-service");
    private static final int LIMIT=2100000;
    private WGramService(){}
    public static String address(int account){return WGramConfig.account(account).getString("service_url","");}
    public static boolean connected(int account){return !address(account).isEmpty()&&!WGramConfig.account(account).getString("service_token","").isEmpty();}
    public static String bot(int account){return WGramConfig.account(account).getString("service_bot","");}
    public static boolean pending(int account){return WGramConfig.account(account).getLong("pair_until",0)>System.currentTimeMillis();}
    public static String pairLink(int account){
        String username=bot(account),pair=WGramConfig.account(account).getString("pair_id","");
        return username.matches("[A-Za-z0-9_]{5,32}")&&pair.matches("[A-Za-z0-9_-]{20,64}")?"https://t.me/"+username+"?start=wgram_"+pair:"";
    }
    public static void setAddress(int account,String value)throws Exception{
        value=value.trim();while(value.endsWith("/"))value=value.substring(0,value.length()-1);
        URL url=new URL(value);
        if(!"https".equals(url.getProtocol())||url.getHost().isEmpty()||url.getUserInfo()!=null||url.getQuery()!=null||url.getRef()!=null
            ||(!url.getPath().isEmpty()&&!"/".equals(url.getPath()))||value.length()>200)throw new IllegalArgumentException();
        if(!value.equals(address(account))){
            WGramConfig.account(account).edit().putString("service_url",value).remove("service_token").remove("service_bot")
                .remove("pair_id").remove("pair_proof").remove("pair_until").apply();
            WGramSharedProfiles.get(account).clear();
        }
    }
    public static void disconnect(int account){
        request(account,"/v1/auth/logout",new JSONObject(),true,(d,e)->{});
        WGramConfig.account(account).edit().remove("service_token").remove("pair_id").remove("pair_proof").remove("pair_until").apply();
        WGramSharedProfiles.get(account).clear();
    }
    public static void start(int account,Result callback){
        try{
            request(account,"/v1/auth/start",new JSONObject().put("uid",UserConfig.getInstance(account).getClientUserId()),false,(data,error)->{
                if(error==null){
                    String name=data.optString("bot_username"),id=data.optString("id"),proof=data.optString("proof");
                    if(!name.matches("[A-Za-z0-9_]{5,32}")||!id.matches("[A-Za-z0-9_-]{20,64}")||proof.length()<32){callback.done(null,"INVALID_SERVICE");return;}
                    WGramConfig.account(account).edit().putString("service_bot",name).putString("pair_id",id).putString("pair_proof",proof)
                        .putLong("pair_until",System.currentTimeMillis()+300000).apply();
                }
                callback.done(data,error);
            });
        }catch(Exception e){callback.done(null,"INVALID_SERVICE");}
    }
    public static void check(int account,Result callback){
        SharedPreferences prefs=WGramConfig.account(account);
        if(!pending(account)){callback.done(null,"PAIR_EXPIRED");return;}
        try{
            request(account,"/v1/auth/check",new JSONObject().put("id",prefs.getString("pair_id",""))
                .put("proof",prefs.getString("pair_proof","")),false,(data,error)->{
                if(error==null&&!data.optBoolean("pending")){
                    String token=data.optString("token");
                    if(data.optLong("uid")!=UserConfig.getInstance(account).getClientUserId()||token.length()<32){callback.done(null,"INVALID_SERVICE");return;}
                    WGramConfig.account(account).edit().putString("service_token",token).remove("pair_id").remove("pair_proof").remove("pair_until").apply();
                    WGramSharedProfiles.get(account).clear();
                }
                callback.done(data,error);
            });
        }catch(Exception e){callback.done(null,"INVALID_SERVICE");}
    }
    public static void request(int account,String path,JSONObject data,boolean auth,Result callback){
        byte[] body=data==null?null:data.toString().getBytes(StandardCharsets.UTF_8);
        transport(account,path,body,auth,(bytes,error)->{
            if(error!=null){callback.done(null,error);return;}
            JSONObject parsed;
            try{parsed=new JSONObject(new String(bytes,StandardCharsets.UTF_8));}
            catch(Exception e){callback.done(null,"INVALID_SERVICE");return;}
            callback.done(parsed,null);
        });
    }
    public static void banner(int account,long user,BytesResult callback){transport(account,"/v1/profiles/"+user+"/banner",null,true,callback);}
    private static void transport(int account,String path,byte[] body,boolean auth,BytesResult callback){
        final long user=UserConfig.getInstance(account).getClientUserId();
        final String endpoint=address(account),token=WGramConfig.account(account).getString("service_token","");
        if(user<=0||endpoint.isEmpty()||(auth&&token.isEmpty())){callback.done(null,"CONNECT_BOT_FIRST");return;}
        queue.postRunnable(()->{
            byte[] result=null;String error=null;HttpURLConnection connection=null;
            try{
                URL url=new URL(endpoint+path);if(!"https".equals(url.getProtocol()))throw new IllegalArgumentException();
                connection=(HttpURLConnection)url.openConnection();connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(12000);connection.setReadTimeout(15000);connection.setRequestProperty("Accept","application/json");
                if(auth)connection.setRequestProperty("Authorization","Bearer "+token);
                if(body!=null){
                    if(body.length>LIMIT)throw new IllegalArgumentException();
                    connection.setRequestMethod("POST");connection.setDoOutput(true);connection.setRequestProperty("Content-Type","application/json");
                    connection.setFixedLengthStreamingMode(body.length);
                    try(java.io.OutputStream out=connection.getOutputStream()){out.write(body);}
                }
                int status=connection.getResponseCode();
                if(status==401)error=auth?"CONNECT_BOT_FIRST":"PAIR_EXPIRED";
                else if(status==403)error="OWNER_ONLY";
                else if(status==429)error="TRY_LATER";
                else if(status<200||status>=300)error=status==404?"NOT_FOUND":"SERVICE_UNAVAILABLE";
                else{
                    int max=path.endsWith("/banner")?1500000:65536;
                    if(connection.getContentLength()>max)throw new IllegalArgumentException();
                    try(InputStream input=connection.getInputStream();ByteArrayOutputStream output=new ByteArrayOutputStream()){
                        byte[] buffer=new byte[8192];int count,total=0;
                        while((count=input.read(buffer))!=-1){if((total+=count)>max)throw new IllegalArgumentException();output.write(buffer,0,count);}
                        result=output.toByteArray();
                    }
                }
            }catch(Exception e){error="SERVICE_UNAVAILABLE";}
            finally{if(connection!=null)connection.disconnect();}
            final byte[] response=result;final String failure=error;
            AndroidUtilities.runOnUIThread(()->{
                // Never deliver a previous account or service's response to the current session.
                if(user!=UserConfig.getInstance(account).getClientUserId()||!endpoint.equals(address(account)))return;
                if(auth&&!token.equals(WGramConfig.account(account).getString("service_token","")))return;
                if(auth&&"CONNECT_BOT_FIRST".equals(failure)){
                    WGramConfig.account(account).edit().remove("service_token").apply();WGramSharedProfiles.get(account).clear();
                }
                callback.done(response,failure);
            });
        });
    }
}
