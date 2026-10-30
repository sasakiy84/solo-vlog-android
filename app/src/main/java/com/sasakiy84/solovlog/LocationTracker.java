package com.sasakiy84.solovlog;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Looper;
import android.os.SystemClock;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

/** Only the visible camera owns updates; snapshots never reuse a stale travel position. */
final class LocationTracker {
    private final Context context;
    private final LocationManager manager;
    private final Runnable changed;
    private final List<String> registered=new ArrayList<>();
    private Location latest;
    private final LocationListener listener=this::accept;
    LocationTracker(Context context,Runnable changed){this.context=context;this.manager=context.getSystemService(LocationManager.class);this.changed=changed;}
    boolean granted(){return ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED||ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;}
    boolean enabled(){return manager!=null&&manager.isLocationEnabled();}
    void start(){
        if(manager==null||!granted()||!enabled()||!registered.isEmpty())return;
        boolean fine=ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED;
        Location known=null;
        for(String provider:fine?new String[]{LocationManager.GPS_PROVIDER,LocationManager.NETWORK_PROVIDER}:new String[]{LocationManager.NETWORK_PROVIDER}){
            try{
                if(!manager.isProviderEnabled(provider))continue;
                Location candidate=manager.getLastKnownLocation(provider);
                if(candidate!=null&&fresh(candidate)&&(known==null||prefer(accuracy(candidate),candidate.getElapsedRealtimeNanos(),accuracy(known),known.getElapsedRealtimeNanos(),SystemClock.elapsedRealtimeNanos())))known=candidate;
                manager.requestLocationUpdates(provider,5000L,5f,listener,Looper.getMainLooper());registered.add(provider);
            }catch(SecurityException e){android.util.Log.w("DaylogLocation","Permission changed",e);}
            catch(IllegalArgumentException e){android.util.Log.w("DaylogLocation","Provider unavailable",e);}
        }
        accept(known);
    }
    void stop(){if(manager!=null)try{manager.removeUpdates(listener);}catch(SecurityException e){android.util.Log.w("DaylogLocation","Permission changed",e);}registered.clear();}
    private void accept(Location location){
        if(location==null||!fresh(location)||point(location)==null)return;
        if(latest==null||prefer(accuracy(location),location.getElapsedRealtimeNanos(),accuracy(latest),latest.getElapsedRealtimeNanos(),SystemClock.elapsedRealtimeNanos()))latest=new Location(location);
        changed.run();
    }
    private static float accuracy(Location location){return location.hasAccuracy()?location.getAccuracy():Float.MAX_VALUE;}
    static boolean fresh(long time,long now){long age=now-time;return time>0&&age>=0&&age<=30_000_000_000L;}
    static boolean prefer(float accuracy,long time,float oldAccuracy,long oldTime,long now){
        if(!fresh(time,now))return false;if(!fresh(oldTime,now))return true;
        if(time<oldTime)return oldTime-time<=10_000_000_000L&&accuracy<oldAccuracy*.8f;
        return accuracy<=oldAccuracy*1.25f||time-oldTime>=10_000_000_000L;
    }
    private boolean fresh(Location location){return fresh(location.getElapsedRealtimeNanos(),SystemClock.elapsedRealtimeNanos());}
    Location snapshot(){return granted()&&enabled()&&latest!=null&&fresh(latest)?new Location(latest):null;}
    static GeoPoint point(Location location){
        if(location==null)return null;
        try{return new GeoPoint(location.getLatitude(),location.getLongitude(),location.hasAccuracy()?location.getAccuracy():0);}
        catch(IllegalArgumentException invalid){android.util.Log.w("DaylogLocation","Invalid provider fix",invalid);return null;}
    }
}
