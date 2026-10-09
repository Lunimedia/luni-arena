package br.com.knightjoga;

/** One-time migration of the old untouched button sizes to the larger default. */
final class TouchSizeDefaults {
    private TouchSizeDefaults(){}

    static float migrate(float saved,float oldDefault,float largeDefault,boolean migrated){
        return !migrated&&Math.abs(saved-oldDefault)<.0005f?largeDefault:saved;
    }
}
