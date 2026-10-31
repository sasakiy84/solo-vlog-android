package com.sasakiy84.solovlog;

/** Originals stay independent of editable export captions and current day settings. */
public record Clip(String id, long capturedAt, long durationMs, int plannedSeconds,
                   boolean landscape, boolean included, String zoneId, String caption,
                   GeoPoint location, String place, long trimStartMs, long trimEndMs) {
    public static final long MIN_TRIM_MS=100;
    public Clip(String id,long capturedAt,long durationMs,int plannedSeconds,boolean landscape,boolean included,String zoneId){
        this(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,"");
    }
    public Clip(String id,long capturedAt,long durationMs,int plannedSeconds,boolean landscape,boolean included,String zoneId,String caption){
        this(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,caption,null,"",0,Math.min(durationMs,plannedSeconds*1000L));
    }
    public Clip {
        if(caption==null)caption="";if(place==null)place="";
        if(trimStartMs<0||trimEndMs>durationMs||trimEndMs-trimStartMs<MIN_TRIM_MS)throw new IllegalArgumentException("Invalid trim range");
    }
    public Clip withCaption(String value){return new Clip(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,value.strip(),location,place,trimStartMs,trimEndMs);}
    public Clip withPlace(String value){return new Clip(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,caption,location,value.strip(),trimStartMs,trimEndMs);}
    public Clip withLocation(GeoPoint value){return new Clip(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,caption,value,place,trimStartMs,trimEndMs);}
    public Clip withTrim(long startMs,long endMs){return new Clip(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,caption,location,place,startMs,endMs);}
}
