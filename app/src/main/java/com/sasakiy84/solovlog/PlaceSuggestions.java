package com.sasakiy84.solovlog;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

record PlaceSuggestions(String city,List<String> facilities,List<String> alternatives) {
    static final PlaceSuggestions EMPTY=new PlaceSuggestions("",List.of(),List.of());
    PlaceSuggestions { facilities=List.copyOf(facilities);alternatives=List.copyOf(alternatives); }
    boolean isEmpty(){return city.isBlank()&&facilities.isEmpty()&&alternatives.isEmpty();}
    PlaceSuggestions withFallback(PlaceSuggestions saved){return new PlaceSuggestions(city.isBlank()?saved.city():city,facilities.isEmpty()?saved.facilities():facilities,alternatives.isEmpty()?saved.alternatives():alternatives);}
    List<String> primary(){LinkedHashSet<String> names=new LinkedHashSet<>();if(!city.isBlank())names.add(city);facilities.stream().filter(name->!name.isBlank()&&!name.equals(city)).limit(2).forEach(names::add);return List.copyOf(names);}
    List<String> others(Clip clip,ClipRepository repository){LinkedHashSet<String> names=new LinkedHashSet<>();if(!clip.place().isBlank())names.add(clip.place());names.addAll(repository.nearbyPlaces(clip.location()));names.addAll(alternatives);if(clip.location()!=null)names.add(clip.location().label());names.removeAll(primary());return List.copyOf(names);}
    String json(){try{return new JSONObject().put("city",city).put("facilities",new JSONArray(facilities)).put("alternatives",new JSONArray(alternatives)).toString();}catch(JSONException e){throw new IllegalStateException("Invalid place suggestions",e);}}
    static PlaceSuggestions parse(String source) throws JSONException {JSONObject json=new JSONObject(source);return new PlaceSuggestions(json.getString("city"),strings(json.getJSONArray("facilities")),strings(json.getJSONArray("alternatives")));}
    private static List<String> strings(JSONArray values) throws JSONException {List<String> result=new ArrayList<>();for(int i=0;i<values.length();i++)result.add(values.getString(i));return result;}
}
