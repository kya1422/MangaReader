package jp.reader.manga;
class TestSmbAddress {public static void main(String[] x){
 for(String s:new String[]{"192.168.10.10","10.0.0.1","172.16.0.1","172.31.255.254"})if(!SmbAddress.privateIpv4(s))throw new AssertionError(s);
 for(String s:new String[]{"127.0.0.1","172.15.0.1","172.32.0.1","8.8.8.8","192.168.1.256","010.0.0.1","example.com","::1","192.168.1.1:445"})if(SmbAddress.privateIpv4(s))throw new AssertionError(s);
 System.out.println("PASS SMB accepts only unambiguous private IPv4 addresses");}}
