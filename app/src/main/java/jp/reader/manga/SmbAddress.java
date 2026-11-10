package jp.reader.manga;
/** Restricts SMB destinations to literal RFC1918 IPv4 addresses. */
final class SmbAddress {
  static boolean privateIpv4(String host){
    if(host==null||!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))return false;
    String[] a=host.split("\\.");int[] n=new int[4];for(int i=0;i<4;i++){if(a[i].length()>1&&a[i].startsWith("0"))return false;n[i]=Integer.parseInt(a[i]);if(n[i]>255)return false;}
    return n[0]==10||(n[0]==172&&n[1]>=16&&n[1]<=31)||(n[0]==192&&n[1]==168);
  }
}
