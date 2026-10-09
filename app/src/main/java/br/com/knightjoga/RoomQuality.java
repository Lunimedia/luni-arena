package br.com.knightjoga;

import java.util.Arrays;

/** Conservative input reserve from bounded handshake round-trip samples. */
final class RoomQuality {
 private RoomQuality(){}
 static int delayFrames(int[] roundTripsMs){
  if(roundTripsMs==null||roundTripsMs.length<3)return 4;
  int[] sorted=roundTripsMs.clone();Arrays.sort(sorted);
  int upper=sorted[sorted.length-1],lower=sorted[0];
  if(lower<0||upper>30000)return 6;
  // Approximate one-way transit and burst; three frames are the LAN minimum.
  int budget=(upper+(upper-lower))/2+12;
  return Math.max(3,Math.min(6,(budget+16)/17));
 }
}
