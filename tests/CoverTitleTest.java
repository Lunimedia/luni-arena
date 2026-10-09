package br.com.knightjoga;

/** Run with JDK 17: the art label never inherits region, website or long file metadata. */
public final class CoverTitleTest {
 private static void check(boolean result){if(!result)throw new AssertionError("CoverTitle");}
 public static void main(String[] args){
  check(CoverTitle.compact("Donkey Kong Country 2 - Diddy's Kong Quest (USA).sfc").equals("DKC 2"));
  check(CoverTitle.compact("Donkey Kong Country (USA)").equals("DKC 1"));
  check(CoverTitle.compact("Super Mario RPG - Legend of the Seven Stars [BR]").equals("MARIO RPG"));
  check(CoverTitle.compact("Super Bomberman 5 (USA)").equals("BOMBERMAN 5"));
  check(CoverTitle.compact("Top Gear 3000 (USA)").equals("TOP GEAR 3000"));
  check(CoverTitle.compact("Chrono Trigger (BR) - roms.example.com.br.sfc").equals("CHRONO TRIGGER"));
  check(CoverTitle.compact("Captain Commando").equals("CAPTAIN COMMANDO"));
  check(CoverTitle.compact("A Very Long Game Title With Metadata [USA]").length()<=19);
  System.out.println("CoverTitleTest OK");
 }
}
