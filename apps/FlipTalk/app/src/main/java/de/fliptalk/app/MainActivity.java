package de.fliptalk.app;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class MainActivity extends Activity {
 static final UUID SERVICE=UUID.fromString("8fe5b3d5-2e7f-4a98-2a48-7acc60fe0000"), RX=UUID.fromString("19ed82ae-ed21-4c9d-4145-228e62fe0000");
 BluetoothAdapter adapter; BluetoothLeScanner scanner; BluetoothGatt gatt; BluetoothGattCharacteristic rx;
 TextView status,feed; EditText username; LinearLayout root; final Handler ui=new Handler(Looper.getMainLooper()); final Queue<byte[]> queue=new ConcurrentLinkedQueue<>(); boolean busy=false; volatile boolean running=false;
 final int bg=0xff0a0e19, panel=0xff182138, mint=0xff35ebc4;
 public interface LiveCallback { void onEvent(int type,String user,String msg); void onError(String error); }
 @Override public void onCreate(Bundle b){super.onCreate(b); if(!Python.isStarted())Python.start(new AndroidPlatform(this)); adapter=((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter(); build(); requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},7);}
 TextView text(String t,int size,int color){TextView v=new TextView(this);v.setText(t);v.setTextSize(size);v.setTextColor(color);v.setPadding(12,14,12,14);return v;}
 void build(){ ScrollView scroll=new ScrollView(this);root=new LinearLayout(this);root.setOrientation(1);root.setPadding(22,25,22,20);root.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);
 TextView title=text("⚡ FlipTalk",30,mint);title.setTypeface(null,Typeface.BOLD);root.addView(title);root.addView(text("TIKTOK LIVE  •  FLIPPER ZERO",12,0xff9daac4));
 username=new EditText(this);username.setSingleLine(true);username.setHint("TikTok-Nutzername ohne @");username.setTextColor(Color.WHITE);username.setHintTextColor(0xff8793ad);username.setBackgroundTintList(android.content.res.ColorStateList.valueOf(mint));root.addView(username);
 android.content.SharedPreferences prefs=getSharedPreferences("settings",0);username.setText(prefs.getString("user",""));
 Button start=button("▶  Livestream starten",()->{String u=username.getText().toString().trim();if(u.isEmpty()){setStatus("Bitte Nutzernamen eingeben");return;}prefs.edit().putString("user",u).apply();if(running)return;running=true;setStatus("TikTok: Verbindung wird aufgebaut …");new Thread(()->Python.getInstance().getModule("live").callAttr("run",u,new LiveCallback(){public void onEvent(int type,String user,String msg){ui.post(()->event(type,user,msg));}public void onError(String error){ui.post(()->setStatus("TikTok: "+error));running=false;}})).start();});root.addView(start);
 root.addView(button("■  Stream stoppen",()->{running=false;new Thread(()->Python.getInstance().getModule("live").callAttr("stop")).start();setStatus("Stream wird beendet …");}));
 root.addView(button("ᛒ  FlipTok-Geräte suchen",this::scan));status=text("Bluetooth: Nicht verbunden",15,0xfff2bd73);root.addView(status);
 root.addView(text("LIVE-EREIGNISSE",15,mint));feed=text("Noch keine Ereignisse",14,Color.WHITE);feed.setBackgroundColor(panel);root.addView(feed);
 root.addView(text("Öffne FlipTok Live auf dem Flipper vor dem Bluetooth-Scan. Die normale Flipper-App-Verbindung ggf. trennen.",12,0xff9daac4)); }
 Button button(String label,Runnable action){Button b=new Button(this);b.setText(label);b.setTextColor(bg);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(mint));b.setOnClickListener(v->action.run());return b;}
 void setStatus(String s){status.setText(s);}
 void scan(){if(checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.BLUETOOTH_SCAN,Manifest.permission.BLUETOOTH_CONNECT},7);return;}if(adapter==null||!adapter.isEnabled()){setStatus("Bluetooth bitte aktivieren");return;}scanner=adapter.getBluetoothLeScanner();setStatus("Suche FlipTok …");scanner.startScan(scanCallback);ui.postDelayed(()->{try{scanner.stopScan(scanCallback);}catch(Exception ignored){}},12000);}
 final ScanCallback scanCallback=new ScanCallback(){@Override public void onScanResult(int type,ScanResult result){BluetoothDevice d=result.getDevice();String name=null;try{name=d.getName();}catch(SecurityException ignored){}if(name==null&&result.getScanRecord()!=null)name=result.getScanRecord().getDeviceName();if(name!=null&&name.startsWith("FlipTok")){try{scanner.stopScan(this);}catch(Exception ignored){}setStatus("Verbinde "+name+" …");gatt=d.connectGatt(MainActivity.this,false,gattCallback,BluetoothDevice.TRANSPORT_LE);}}@Override public void onScanFailed(int code){setStatus("Scanfehler: "+code);}};
 final BluetoothGattCallback gattCallback=new BluetoothGattCallback(){@Override public void onConnectionStateChange(BluetoothGatt g,int statusCode,int newState){ui.post(()->setStatus(newState==BluetoothProfile.STATE_CONNECTED?"BLE verbunden – suche Dienst …":"BLE getrennt ("+statusCode+")"));if(newState==BluetoothProfile.STATE_CONNECTED)g.discoverServices();else{rx=null;busy=false;}}@Override public void onServicesDiscovered(BluetoothGatt g,int s){BluetoothGattService service=g.getService(SERVICE);rx=service==null?null:service.getCharacteristic(RX);if(rx!=null){g.requestMtu(185);ui.post(()->setStatus("✓ FlipTok verbunden"));flush();}else ui.post(()->setStatus("FlipTok-RX-Dienst fehlt"));}@Override public void onCharacteristicWrite(BluetoothGatt g,BluetoothGattCharacteristic c,int s){busy=false;flush();}};
 void event(int type,String user,String msg){String prefix=new String[]{"💬","♥","🎁","★"}[type];String old=feed.getText().toString();if(old.equals("Noch keine Ereignisse"))old="";feed.setText(prefix+"  "+user+": "+msg+"\n\n"+(old.length()>2200?old.substring(0,2200):old));queue.add(packet(type,user,msg));flush();}
 byte[] packet(int type,String user,String msg){byte[] data=new byte[83];data[0]=(byte)type;writeAscii(data,1,16,user);writeAscii(data,18,64,msg);return data;}
 void writeAscii(byte[] dest,int offset,int limit,String value){byte[] ascii=value.replaceAll("[^\\x20-\\x7E]","?").getBytes(StandardCharsets.US_ASCII);System.arraycopy(ascii,0,dest,offset,Math.min(limit,ascii.length));}
 synchronized void flush(){if(busy||gatt==null||rx==null)return;byte[] p=queue.poll();if(p==null)return;busy=true;rx.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);rx.setValue(p);if(!gatt.writeCharacteristic(rx)){busy=false;ui.post(()->setStatus("BLE-Schreibfehler"));}}
 @Override protected void onDestroy(){super.onDestroy();try{if(scanner!=null)scanner.stopScan(scanCallback);}catch(Exception ignored){}try{if(gatt!=null)gatt.close();}catch(Exception ignored){}}
}
