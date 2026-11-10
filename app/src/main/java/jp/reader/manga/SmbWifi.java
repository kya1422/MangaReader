package jp.reader.manga;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/** SSID is an opt-in routing hint, not server authentication. */
final class SmbWifi {
  static boolean matches(Context context,String expected){
    if(expected==null||expected.isEmpty())return false;
    return expected.equals(currentSsid(context));
  }
  /** null means permission, system settings or Wi-Fi state prevented identification. */
  static String currentSsid(Context context){
    if(context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED)return null;
    try{
      WifiManager wifi=(WifiManager)context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
      if(wifi==null||!wifi.isWifiEnabled())return null;WifiInfo info=wifi.getConnectionInfo();if(info==null||info.getNetworkId()<0)return null;
      String ssid=info.getSSID();if(ssid==null||ssid.equals("<unknown ssid>"))return null;
      if(ssid.length()>=2&&ssid.charAt(0)=='"'&&ssid.charAt(ssid.length()-1)=='"')ssid=ssid.substring(1,ssid.length()-1);
      return ssid.isEmpty()?null:ssid;
    }catch(SecurityException e){return null;}
  }
}
