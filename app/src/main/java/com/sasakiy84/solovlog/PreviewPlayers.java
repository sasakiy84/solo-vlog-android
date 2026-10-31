package com.sasakiy84.solovlog;

import android.content.Context;
import android.os.Handler;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.DefaultRenderersFactory;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.function.Consumer;

/** Local short clips need little startup buffering and must not remain stuck after codec failure. */
@UnstableApi
final class PreviewPlayers {
    static ExoPlayer create(Context context,Consumer<PlaybackException> failed){
        ExoPlayer.Builder builder=new ExoPlayer.Builder(context,new DefaultRenderersFactory(context).setEnableDecoderFallback(true))
            .setLoadControl(new DefaultLoadControl.Builder().setBufferDurationsMs(1000,5000,100,250).build());
        ExoPlayer player=builder.build();
        Handler main=new Handler(player.getApplicationLooper());
        player.addListener(new Player.Listener(){
            private boolean retried;
            private final Runnable stalled=()->recover(new PlaybackException("Preview buffering timed out",null,PlaybackException.ERROR_CODE_TIMEOUT));
            private void recover(PlaybackException error){
                main.removeCallbacks(stalled);if(player.isReleased())return;
                if(retried){
                    try{
                        org.json.JSONObject detail=new org.json.JSONObject().put("at",System.currentTimeMillis()).put("code",error.getErrorCodeName())
                            .put("positionMs",player.getCurrentPosition()).put("state",player.getPlaybackState())
                            .put("version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName);
                        String stack=android.util.Log.getStackTraceString(error);detail.put("error",stack.substring(0,Math.min(stack.length(),4000)));
                        context.getSharedPreferences("daylog",0).edit().putString("lastPlaybackFailure",detail.toString()).apply();
                    }catch(Exception e){android.util.Log.w("DaylogPreview","Could not keep playback failure",e);}
                    failed.accept(error);return;
                }
                retried=true;
                main.postDelayed(()->{if(!player.isReleased()){
                    int index=player.getCurrentMediaItemIndex();long position=player.getCurrentPosition();
                    player.stop();player.seekTo(index,position);player.prepare();player.play();
                }},250);
            }
            private void watch(){main.removeCallbacks(stalled);if(!player.isReleased()&&player.getPlayWhenReady()&&player.getPlaybackState()==Player.STATE_BUFFERING)main.postDelayed(stalled,8000);}
            @Override public void onPlaybackStateChanged(int state){watch();}
            @Override public void onPlayWhenReadyChanged(boolean ready,int reason){watch();}
            @Override public void onPlayerError(PlaybackException error){recover(error);}
        });
        return player;
    }
}
