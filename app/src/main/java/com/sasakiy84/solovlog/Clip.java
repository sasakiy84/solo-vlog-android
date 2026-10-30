package com.sasakiy84.solovlog;

/** Originals stay independent of editable export captions and current day settings. */
public record Clip(String id, long capturedAt, long durationMs, int plannedSeconds,
                   boolean landscape, boolean included, String zoneId, String caption) {
    public Clip(String id,long capturedAt,long durationMs,int plannedSeconds,boolean landscape,boolean included,String zoneId){
        this(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,"");
    }
    public Clip { if(caption==null)caption=""; }
    public Clip withCaption(String value){return new Clip(id,capturedAt,durationMs,plannedSeconds,landscape,included,zoneId,value.strip());}
}
