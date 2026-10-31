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

/** Uses the Android address service; cached and manual names remain usable offline. */
final class PlaceResolver {
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private static final Map<String,PlaceSuggestions> cache=new java.util.LinkedHashMap<>();
    private static final Map<String,List<Consumer<PlaceSuggestions>>> pending=new HashMap<>();
    PlaceResolver(Context context){this.context=context.getApplicationContext();}
    void resolve(GeoPoint point,Consumer<PlaceSuggestions> callback){
        String key=String.format(Locale.ROOT,"%.4f,%.4f",point.latitude(),point.longitude());
        if(cache.containsKey(key)){callback.accept(cache.get(key));return;}
        if(!Geocoder.isPresent()){callback.accept(PlaceSuggestions.EMPTY);return;}
        if(pending.containsKey(key)){pending.get(key).add(callback);return;}
        List<Consumer<PlaceSuggestions>> callbacks=new ArrayList<>(List.of(callback));pending.put(key,callbacks);
        main.postDelayed(()->{if(pending.get(key)==callbacks)finish(key,PlaceSuggestions.EMPTY);},8000);
        try{
            new Geocoder(context,Locale.JAPAN).getFromLocation(point.latitude(),point.longitude(),5,new Geocoder.GeocodeListener(){
                @Override public void onGeocode(List<Address> addresses){
                    if(addresses.isEmpty()){finish(key,PlaceSuggestions.EMPTY);return;}
                    Address a=addresses.get(0);String city=clean(a.getLocality());if(city.isEmpty()||city.matches(".*[都道府県]$"))city=clean(a.getSubAdminArea());String region=clean(a.getAdminArea());
                    LinkedHashSet<String> alternatives=new LinkedHashSet<>();String area=city.isEmpty()?region:city;
                    for(String detail:new String[]{a.getSubLocality(),a.getThoroughfare()}){detail=clean(detail);if(!detail.isEmpty()&&!area.isEmpty())alternatives.add(detail.startsWith(area)?detail:area+detail);}
                    if(!region.isEmpty())alternatives.add(region);
                    LinkedHashSet<String> facilities=new LinkedHashSet<>();
                    for(Address candidate:addresses){String feature=facilityName(candidate);if(!feature.isEmpty())facilities.add(feature);}
                    if(city.matches(".*[都道府県]$")||city.matches("[0-9０-９一二三四五六七八九十]+丁目.*"))city="";
                    finish(key,new PlaceSuggestions(city,facilities.stream().limit(2).toList(),List.copyOf(alternatives)));
                }
                @Override public void onError(String message){finish(key,PlaceSuggestions.EMPTY);}
            });
        }catch(IllegalArgumentException|IllegalStateException|SecurityException e){android.util.Log.w("DaylogPlaces","Address lookup unavailable",e);finish(key,PlaceSuggestions.EMPTY);}
    }
    private static String facilityName(Address address){
        for(String value:new String[]{address.getPremises(),address.getFeatureName()}){
            String name=clean(value);if(name.isEmpty()||name.matches("[0-9０-９一二三四五六七八九十丁目番地号\\-ー ]+"))continue;
            boolean area=false;for(String part:new String[]{address.getLocality(),address.getSubLocality(),address.getSubAdminArea(),address.getAdminArea(),address.getThoroughfare(),address.getCountryName()})if(name.equals(clean(part))){area=true;break;}
            if(!area)return name;
        }
        return "";
    }
    private static String clean(String value){return value==null?"":value.strip();}
    private void finish(String key,PlaceSuggestions result){main.post(()->{
        if(!result.equals(PlaceSuggestions.EMPTY)){if(cache.size()>=64)cache.remove(cache.keySet().iterator().next());cache.put(key,result);}
        List<Consumer<PlaceSuggestions>> callbacks=pending.remove(key);if(callbacks!=null)for(var callback:callbacks)callback.accept(result);
    });}
}
