package de.nethunter.smartunlock;
import android.app.PendingIntent;import android.appwidget.AppWidgetManager;import android.appwidget.AppWidgetProvider;import android.content.ComponentName;import android.content.Context;import android.content.Intent;import android.graphics.Color;import android.widget.RemoteViews;import android.widget.Toast;
public class Widget extends AppWidgetProvider {
 static final String ACTION="de.nethunter.smartunlock.TOGGLE",REFRESH="de.nethunter.smartunlock.REFRESH";
 static void update(Context c,State s){AppWidgetManager m=AppWidgetManager.getInstance(c);paint(c,m,Widget.class,R.layout.widget_small,s);paint(c,m,LargeWidget.class,R.layout.widget_large,s);paintIcon(c,m,s);}
 static void paint(Context c,AppWidgetManager m,Class<?> cls,int layout,State s){
  int color=s.error?Color.YELLOW:s.on?Color.rgb(255,54,72):Color.LTGRAY;
  for(int id:m.getAppWidgetIds(new ComponentName(c,cls))){RemoteViews v=new RemoteViews(c.getPackageName(),layout);
   v.setTextViewText(R.id.widget_status,s.label);v.setTextColor(R.id.widget_status,color);v.setTextViewText(R.id.widget_detail,s.detail);
   v.setTextViewText(R.id.widget_symbol,s.error?"!":s.on?"●":"○");v.setTextColor(R.id.widget_symbol,color);
   v.setTextViewText(R.id.widget_button,s.on?"AUSSCHALTEN":"EINSCHALTEN");
   v.setOnClickPendingIntent(R.id.widget_button,PendingIntent.getBroadcast(c,id,new Intent(c,cls).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));
   v.setOnClickPendingIntent(R.id.widget_status,PendingIntent.getBroadcast(c,10000+id,new Intent(c,Widget.class).setAction(REFRESH),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);
  }
 }
 static void paintIcon(Context c,AppWidgetManager m,State s){for(int id:m.getAppWidgetIds(new ComponentName(c,IconWidget.class))){RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.widget_icon);boolean ready=s.on&&s.trust;
  v.setTextViewText(R.id.icon_glyph,s.error?"!":ready?"🔓":s.on?"◐":"🔒");v.setTextViewText(R.id.icon_caption,s.error?"FEHLER":ready?"AN":s.on?"PIN":"AUS");
  v.setInt(R.id.icon_root,"setBackgroundResource",s.error?R.drawable.neon_icon_unknown:s.on?R.drawable.neon_icon_on:R.drawable.neon_icon_off);
  v.setOnClickPendingIntent(R.id.icon_root,PendingIntent.getBroadcast(c,200000+id,new Intent(c,IconWidget.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));m.updateAppWidget(id,v);}}
 static void refresh(Context c){Context app=c.getApplicationContext();RootExec.run(app,"status",r->{State s=State.parse(r);update(app,s);TrustMonitor.sync(app,s);});}
 static void toggle(Context c){Context app=c.getApplicationContext();RootExec.run(app,"toggle",r->{if(r.contains("ERROR:")){update(app,State.parse(r));Toast.makeText(app,"Smart Unlock: Fehler",Toast.LENGTH_LONG).show();return;}RootExec.run(app,"status",s->{State state=State.parse(s);update(app,state);TrustMonitor.sync(app,state);Toast.makeText(app,state.on?"Smart Unlock: AN":"Smart Unlock: AUS",Toast.LENGTH_SHORT).show();});});}
 @Override public void onUpdate(Context c,AppWidgetManager m,int[] ids){refresh(c);}
 @Override public void onReceive(Context c,Intent i){super.onReceive(c,i);String a=i.getAction();if(ACTION.equals(a))toggle(c);else if(REFRESH.equals(a)||"lineageos.platform.intent.action.PROFILE_SELECTED".equals(a))refresh(c);}
}
