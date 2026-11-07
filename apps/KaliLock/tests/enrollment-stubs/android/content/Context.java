package android.content;
public abstract class Context {
 public static final String USER_SERVICE="user",KEYGUARD_SERVICE="keyguard";
 public Context createDeviceProtectedStorageContext(){return this;}
 public abstract SharedPreferences getSharedPreferences(String name,int mode);
 public Object getSystemService(String name){return new android.os.UserManager();}
 public android.content.pm.ApplicationInfo getApplicationInfo(){return new android.content.pm.ApplicationInfo();}
}
