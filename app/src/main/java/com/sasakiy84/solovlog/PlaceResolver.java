package com.sasakiy84.solovlog;

import android.content.Context;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.function.Consumer;

/** Uses the Android address service; coordinates and manual names remain usable offline. */
final class PlaceResolver {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<String,List<String>> cache=new java.util.LinkedHashMap<>();
    private static final Map<String,List<Consumer<List<String>>>> pending=new HashMap<>();
    PlaceResolver(Context context){this.context=context.getApplicationContext();}
    void resolve(GeoPoint point,Consumer<List<String>> callback){
        String key=String.format(Locale.ROOT,"%.4f,%.4f",point.latitude(),point.longitude());
        if(cache.containsKey(key)){callback.accept(cache.get(key));return;}
        if(!Geocoder.isPresent()){callback.accept(List.of());return;}
        if(pending.containsKey(key)){pending.get(key).add(callback);return;}
        List<Consumer<List<String>>> callbacks=new ArrayList<>(List.of(callback));pending.put(key,callbacks);
        main.postDelayed(()->{if(pending.get(key)==callbacks)finish(key,List.of());},8000);
        try{
            new Geocoder(context,Locale.JAPAN).getFromLocation(point.latitude(),point.longitude(),1,new Geocoder.GeocodeListener(){
                @Override public void onGeocode(List<Address> addresses){
                    LinkedHashSet<String> names=new LinkedHashSet<>();
                    if(!addresses.isEmpty()){
                        Address a=addresses.get(0);
                        String city=clean(a.getLocality());
                        if(city.isEmpty())city=clean(a.getSubAdminArea());
                        String region=clean(a.getAdminArea());
                        if(!city.isEmpty())names.add(city);
                        else if(!region.isEmpty())names.add(region);
                        String context=city.isEmpty()?region:city;
                        for(String detail:new String[]{a.getSubLocality(),a.getThoroughfare()}){
                            detail=clean(detail);
                            if(!detail.isEmpty()&&!context.isEmpty())names.add(detail.startsWith(context)?detail:context+detail);
                        }
                        if(!region.isEmpty())names.add(region);
                    }
                    finish(key,List.copyOf(names));
                }
                @Override public void onError(String message){finish(key,List.of());}
            });
        }catch(IllegalArgumentException|IllegalStateException|SecurityException e){android.util.Log.w("DaylogPlaces","Address lookup unavailable",e);finish(key,List.of());}
    }
    private static String clean(String value){return value==null?"":value.strip();}
    static String defaultCity(List<String> names){
        if(names.isEmpty())return "";
        String name=names.get(0);
        // Never substitute a prefecture or an isolated block number for a municipality.
        return name.matches(".*[都道府県]$")||name.matches("[0-9０-９一二三四五六七八九十]+丁目.*")?"":name;
    }
    private void finish(String key,List<String> names){main.post(()->{
        if(!names.isEmpty()){if(cache.size()>=64)cache.remove(cache.keySet().iterator().next());cache.put(key,names);}
        List<Consumer<List<String>>> callbacks=pending.remove(key);if(callbacks!=null)for(var callback:callbacks)callback.accept(names);
    });}
}
