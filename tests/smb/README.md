# Optional local SMB fixture

`tests/run-smb-fixture.sh` exercises listing, Unicode names, offset reads, tail reads, streaming, authentication failure and root confinement against a disposable SMB2 test server. It does not access a real user's Samba share. The fixture expects an SMB2 server on localhost port 1445, share `books`, test username `smbtest`, test password `test-password`, and a share containing `作品/01.zip` with ASCII `0123456789abcdefghijklmnopqrstuvwxyz`. For example, configure an isolated impacket SimpleSMBServer with SMB2 support and these throwaway credentials. Do not use production credentials.

Download SMBJ 0.15.0 and its runtime dependencies separately, then set `SMBJ_JARS` to a Java classpath containing them and run `bash tests/run-smb-fixture.sh`. Android/BOOX and a real Samba server are still separate tests.
