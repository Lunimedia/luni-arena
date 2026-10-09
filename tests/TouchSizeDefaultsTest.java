package br.com.knightjoga;

/** An update upgrades untouched sizes once and honors later compact/custom choices. */
public final class TouchSizeDefaultsTest {
    private static void check(boolean condition,String label){
        if(!condition)throw new AssertionError(label);
    }
    private static boolean near(float a,float b){return Math.abs(a-b)<.0005f;}
    public static void main(String[] ignored){
        check(near(TouchSizeDefaults.migrate(1f,1f,1.2f,false),1.2f),
            "old face button becomes the larger default");
        check(near(TouchSizeDefaults.migrate(.92f,.92f,1.104f,false),1.104f),
            "old shoulder button becomes the larger default");
        check(near(TouchSizeDefaults.migrate(.82f,.82f,.984f,false),.984f),
            "old Start/Select also grows");
        check(near(TouchSizeDefaults.migrate(1.34f,1f,1.2f,false),1.34f),
            "customized size is not overwritten");
        check(near(TouchSizeDefaults.migrate(1f,1f,1.2f,true),1f),
            "an intentionally selected compact preset remains compact after migration");
        check(near(TouchSizeDefaults.migrate(1.2f,1f,1.2f,false),1.2f),
            "already enlarged controls do not grow twice");
        System.out.println("TouchSizeDefaultsTest OK: old sizes upgraded once, custom and compact preserved.");
    }
}
