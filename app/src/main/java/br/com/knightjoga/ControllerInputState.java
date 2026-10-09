package br.com.knightjoga;

import java.util.HashMap;

/** Main-thread controller state. A released button on one device never releases another. */
final class ControllerInputState {
    private static final int LEFT=1<<6,RIGHT=1<<7,UP=1<<4,DOWN=1<<5;
    private static final int HORIZONTAL=LEFT|RIGHT,VERTICAL=UP|DOWN,L=1<<10,R=1<<11;
    private static final class Device {
        final HashMap<Integer,Integer> pressedCodes=new HashMap<>();
        int keys,stick,hat,triggers;
        int mask(){
            int horizontal=keys&HORIZONTAL;
            if(horizontal==0)horizontal=hat&HORIZONTAL;
            if(horizontal==0)horizontal=stick&HORIZONTAL;
            int vertical=keys&VERTICAL;
            if(vertical==0)vertical=hat&VERTICAL;
            if(vertical==0)vertical=stick&VERTICAL;
            return ((keys|triggers)&~(HORIZONTAL|VERTICAL))|horizontal|vertical;
        }
    }
    private final HashMap<Integer,Device> devices=new HashMap<>();
    private int combined;

    int mask(){return combined;}
    static int frameMask(int live,int shortPress){
        // Held directions win over a short tap in the opposite direction.
        if((live&HORIZONTAL)!=0)shortPress&=~HORIZONTAL;
        if((live&VERTICAL)!=0)shortPress&=~VERTICAL;
        if((shortPress&HORIZONTAL)==HORIZONTAL)shortPress&=~HORIZONTAL;
        if((shortPress&VERTICAL)==VERTICAL)shortPress&=~VERTICAL;
        return live|shortPress;
    }
    int key(int deviceId,int keyCode,int bit,boolean down){
        Device device=devices.get(deviceId);
        if(device==null){if(!down)return combined;device=new Device();devices.put(deviceId,device);}
        if(down){
            if(device.pressedCodes.containsKey(keyCode))return combined;
            device.pressedCodes.put(keyCode,1<<bit);
        }else if(device.pressedCodes.remove(keyCode)==null)return combined;
        device.keys=0;
        for(int pressed:device.pressedCodes.values())device.keys|=pressed;
        return recompute();
    }
    int axes(int deviceId,float x,float y,float hatX,float hatY,float leftTrigger,float rightTrigger,
             float pressX,float pressY,float flatX,float flatY){
        Device device=devices.get(deviceId);
        if(device==null){device=new Device();devices.put(deviceId,device);}
        int stick=ControllerAxis.direction(x,6,7,device.stick,pressX,flatX)|
                     ControllerAxis.direction(y,4,5,device.stick,pressY,flatY);
        int hat=(hatX<-.5f?LEFT:hatX>.5f?RIGHT:0)|
                   (hatY<-.5f?UP:hatY>.5f?DOWN:0);
        int triggers=(leftTrigger>=((device.triggers&L)!=0?.35f:.55f)?L:0)|
                     (rightTrigger>=((device.triggers&R)!=0?.35f:.55f)?R:0);
        if(stick==device.stick&&hat==device.hat&&triggers==device.triggers)return combined;
        device.stick=stick;device.hat=hat;device.triggers=triggers;
        return recompute();
    }
    int cancelAxes(int deviceId){
        Device device=devices.get(deviceId);
        if(device==null||device.stick==0&&device.hat==0&&device.triggers==0)return combined;
        device.stick=0;device.hat=0;device.triggers=0;
        return recompute();
    }
    int remove(int deviceId){return devices.remove(deviceId)==null?combined:recompute();}
    void clear(){devices.clear();combined=0;}
    private int recompute(){
        int result=0;
        for(Device device:devices.values())result|=device.mask();
        // Two pads driving P1 in opposite directions cancel, rather than sending an invalid pair.
        if((result&HORIZONTAL)==HORIZONTAL)result&=~HORIZONTAL;
        if((result&VERTICAL)==VERTICAL)result&=~VERTICAL;
        combined=result;
        return result;
    }
}
