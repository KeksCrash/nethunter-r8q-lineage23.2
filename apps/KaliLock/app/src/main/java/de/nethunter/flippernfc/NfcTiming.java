package de.nethunter.flippernfc;
/** Do not shorten the negotiated ISO-DEP frame wait below slow FIDO operations. */
final class NfcTiming {
 static int apduTimeout(int negotiatedMs){return Math.max(15000,Math.min(30000,negotiatedMs));}
}
