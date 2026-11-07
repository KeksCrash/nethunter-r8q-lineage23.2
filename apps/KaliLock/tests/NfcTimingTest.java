package de.nethunter.flippernfc;
public final class NfcTimingTest {
 public static void main(String[] args){
  int[][] cases={{0,15000},{3000,15000},{9888,15000},{20000,20000},{60000,30000}};
  for(int[] c:cases)if(NfcTiming.apduTimeout(c[0])!=c[1])throw new AssertionError("Invalid ISO-DEP timeout");
  System.out.println("PASS 5 negotiated NFC timing floor/cap checks");
 }
}
