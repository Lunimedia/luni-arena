package br.com.knightjoga;

import java.util.Arrays;

public final class CoreChoiceTest {
 public static void main(String[] args){
  byte[] ordinary=new byte[0x10000];
  Arrays.fill(ordinary,0x7fc0,0x7fc0+21,(byte)' ');
  byte[] title="SUPER BOMBERMAN 5".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
  System.arraycopy(title,0,ordinary,0x7fc0,title.length);
  ordinary[0x7fc0+0x16]=0x02;
  if(CoreChoice.preferMesen(ordinary,"sfc"))throw new AssertionError("Ordinary SNES game must retain the faster core");
  ordinary[0x7fc0+0x16]=(byte)0xf3;
  if(!CoreChoice.preferMesen(ordinary,"sfc"))throw new AssertionError("Cx4 cartridge must use compatibility core");
  byte[] withCopier=new byte[ordinary.length+512];
  System.arraycopy(ordinary,0,withCopier,512,ordinary.length);
  if(!CoreChoice.preferMesen(withCopier,"smc"))throw new AssertionError("512-byte copier header must be recognized");
  ordinary[0x7fc0+0x16]=0x02;
  if(!CoreChoice.preferMesen(ordinary,"fig")||!CoreChoice.preferMesen(ordinary,"SWC")||
     !CoreChoice.preferMesen(ordinary,"bs"))throw new AssertionError("Alternate formats need a compatible core");
  if(CoreChoice.preferMesen(ordinary,"smc"))throw new AssertionError("SMC should retain default core");
  System.out.println("CoreChoice: 5 selection checks passed");
 }
}
