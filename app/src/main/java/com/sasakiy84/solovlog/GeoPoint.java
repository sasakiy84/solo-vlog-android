package com.sasakiy84.solovlog;

import java.util.Locale;

public record GeoPoint(double latitude,double longitude,float accuracyMeters) {
    public GeoPoint {
        if(!Double.isFinite(latitude)||!Double.isFinite(longitude)||latitude< -90||latitude>90||longitude< -180||longitude>180||!Float.isFinite(accuracyMeters)||accuracyMeters<0)
            throw new IllegalArgumentException("Invalid location");
    }
    public String label(){return String.format(Locale.ROOT,accuracyMeters==0||accuracyMeters>1000?"%.3f, %.3f":"%.4f, %.4f",latitude,longitude);}
    public boolean near(GeoPoint other){return other!=null&&Math.abs(latitude-other.latitude)<.0001&&Math.abs(longitude-other.longitude)<.0001;}
    public double distanceMeters(GeoPoint other){
        double lat=Math.toRadians(other.latitude-latitude),lon=Math.toRadians(other.longitude-longitude);
        double a=Math.sin(lat/2)*Math.sin(lat/2)+Math.cos(Math.toRadians(latitude))*Math.cos(Math.toRadians(other.latitude))*Math.sin(lon/2)*Math.sin(lon/2);
        return 6371000*2*Math.atan2(Math.sqrt(a),Math.sqrt(Math.max(0,1-a)));
    }
}
