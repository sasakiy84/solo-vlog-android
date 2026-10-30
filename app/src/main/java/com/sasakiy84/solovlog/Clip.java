package com.sasakiy84.solovlog;

/** Originals stay independent of editable export captions and current day settings. */
public record Clip(String id, long capturedAt, long durationMs, int plannedSeconds,
                   boolean landscape, boolean included, String zoneId, String caption,
                   GeoPoint location, String place) {
    public Clip(String id,long capturedAt,long durationMs,int plannedSeconds,boolean landscape,boolean included,String zoneId){
        this(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,"");
    }
    public Clip(String id,long capturedAt,long durationMs,int plannedSeconds,boolean landscape,boolean included,String zoneId,String caption){
        this(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,caption,null,"");
    }
    public Clip { if(caption==null)caption="";if(place==null)place=""; }
    public Clip withCaption(String value){return new Clip(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,value.strip(),location,place);}
    public Clip withPlace(String value){return new Clip(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,caption,location,value.strip());}
    public Clip withLocation(GeoPoint value){return new Clip(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,caption,value,place);}
}
