package jp.reader.manga;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/** SSID is an opt-in routing hint, not server authentication. */
final class SmbWifi {
  static boolean matches(Context context,String expected){
    if(expected==null||expected.isEmpty()||context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return false;
    try{
      WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      if(wifi==null||!wifi.isWifiEnabled())return false;WifiInfo info=wifi.getConnectionInfo();if(info==null||info.getNetworkId()<0)return false;
      String ssid=info.getSSID();if(ssid==null||ssid.equals("<unknown ssid>"))return false;
      if(ssid.length()>=2&&ssid.charAt(0)=='"'&&ssid.charAt(ssid.length()-1)=='"')ssid=ssid.substring(1,ssid.length()-1);
      return expected.equals(ssid);
    }catch(SecurityException e){return false;}
  }
}
