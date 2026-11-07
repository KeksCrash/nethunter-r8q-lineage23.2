package de.nethunter.flippernfc;
final class PinAuto {
 static boolean configured(android.content.Context c){return true;}
 static byte[] readPasswordBytes(android.content.Context c){return "synthetic Android password".getBytes(java.nio.charset.StandardCharsets.UTF_8);}
 static void prepareBootIme(){}
 static boolean submitValidatedPassword(android.content.Context c,String secret){throw new AssertionError("Enrollment must not deliver a password");}
}
