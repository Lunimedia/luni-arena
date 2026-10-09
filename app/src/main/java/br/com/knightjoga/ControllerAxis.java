package br.com.knightjoga;

/** Digital SNES directions from an analog axis with a short release hysteresis. */
final class ControllerAxis {
    private ControllerAxis(){}
    static int direction(float value,int negativeBit,int positiveBit,int previous,float press,float flat){
        // Android's device flat is always neutral, even while applying hysteresis.
        float release=Math.max(Math.max(0,flat)+.001f,press-.06f);
        if(value<=-((previous&(1<<negativeBit))!=0?release:press))return 1<<negativeBit;
        if(value>=((previous&(1<<positiveBit))!=0?release:press))return 1<<positiveBit;
        return 0;
    }
    static float press(float selected,float deviceFlat){
        return Math.min(.98f,Math.max(selected,Math.max(0,deviceFlat)+.02f));
    }
}
