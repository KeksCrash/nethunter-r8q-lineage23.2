package de.nethunter.smartunlock;
final class State {
 final boolean error,on,trust; final String label,detail,raw;
 private State(boolean e,boolean o,boolean t,String l,String d,String r){error=e;on=o;trust=t;label=l;detail=d;raw=r;}
 static State parse(String s){
  if(s==null || s.contains("ERROR:") || !s.contains("PROFILE:"))return new State(true,false,false,"FEHLER","Profil konnte nicht gelesen werden",s==null?"":s);
  String profile="";for(String line:s.split("\n"))if(line.startsWith("PROFILE:")){profile=line.substring(8).trim();break;}
  boolean on="Smart Unlock".equalsIgnoreCase(profile),trust=s.contains("TRUST: TRUSTED");
  if(on)return new State(false,true,trust,trust?"AKTIV":"PROFIL AKTIV",trust?"Wischen statt PIN möglich":"Android verlangt ggf. PIN",s);
  return new State(false,false,trust,"DEAKTIVIERT",profile.isEmpty()?"Kein aktives Profil":"Profil: "+profile,s);
 }
}
