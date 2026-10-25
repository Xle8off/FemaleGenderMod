package com.wildfire.client.overhead;
import java.util.*;
public final class CameraSubscriptionsCheck {
 public static void main(String[] args){
  var requested=new LinkedHashMap<UUID,UUID>(16,.75f,true);var self=UUID.randomUUID();requested.put(self,UUID.randomUUID());
  for(int i=0;i<7;i++)requested.put(UUID.randomUUID(),UUID.randomUUID());
  var expected=Map.copyOf(requested);
  for(int tick=0;tick<10000;tick++){
   var result=new HashMap<UUID,UUID>();
   for(var e:CameraSubscriptions.snapshot(requested))if(!e.getKey().equals(self)&&e.getValue().equals(requested.get(e.getKey())))result.put(e.getKey(),e.getValue());
   if(result.size()!=7||result.containsKey(self))throw new AssertionError("Missing/duplicate subscription");
   for(var e:result.entrySet())if(!e.getValue().equals(expected.get(e.getKey())))throw new AssertionError("Wrong session");
  }
  var snapshot=CameraSubscriptions.snapshot(requested);requested.clear();if(snapshot.size()!=8)throw new AssertionError("Not detached");
  try{snapshot.getFirst().setValue(UUID.randomUUID());throw new AssertionError("Mutable entry");}catch(UnsupportedOperationException expectedError){}
  System.out.println("PASS: 10,000 ticks, 8 cameras, access-order lookups, self exclusion, detached immutable entries");
 }
}
