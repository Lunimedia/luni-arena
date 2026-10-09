package br.com.knightjoga;

/** Run on a JDK without Android; exercises real per-device edge and axis arbitration. */
public final class ControllerInputTest {
    private static final int B=1,UP=1<<4,LEFT=1<<6,RIGHT=1<<7,A=1<<8,L=1<<10;
    private static void check(boolean condition,String message){
        if(!condition)throw new AssertionError(message);
    }
    public static void main(String[] ignored){
        ControllerInputState input=new ControllerInputState();
        check(input.key(1,101,0,true)==B&&input.key(2,101,0,true)==B,"two pads hold the same button");
        check(input.key(1,101,0,false)==B,"releasing pad 1 does not release pad 2");
        check(input.remove(1)==B&&input.remove(2)==0,"a disconnect clears only its own pad");

        input.key(1,106,6,true);
        check(input.axes(1,1f,0,0,0,0,0,.20f,.20f,0,0)==LEFT,
            "D-pad key takes priority over the opposite stick on one device");
        check(input.key(1,106,6,false)==RIGHT,"stick direction resumes on key release");
        check(input.axes(1,1f,0,-1f,0,0,0,.20f,.20f,0,0)==LEFT,
            "hat direction takes priority over stick");
        check(input.axes(1,1f,0,0,0,0,0,.20f,.20f,0,0)==RIGHT,
            "stick resumes when hat returns to center");
        input.key(1,108,8,true);
        check(input.cancelAxes(1)==A,"axis cancellation retains the face button");
        input.remove(1);

        float press=ControllerAxis.press(.14f,.19f);
        check(press>.19f,"analog press respects the controller's reported dead zone");
        check(input.axes(3,.22f,0,0,0,0,0,press,press,.19f,.19f)==RIGHT,
            "movement beyond the reported flat responds");
        check(input.axes(3,.19f,0,0,0,0,0,press,press,.19f,.19f)==0,
            "the reported flat is always neutral, even after a press");
        input.remove(3);
        check(input.axes(4,.21f,0,0,0,0,0,.20f,.20f,.08f,.08f)==RIGHT&&
              input.axes(4,.16f,0,0,0,0,0,.20f,.20f,.08f,.08f)==RIGHT&&
              input.axes(4,.12f,0,0,0,0,0,.20f,.20f,.08f,.08f)==0,
            "small analog fluctuations do not flicker around the threshold");
        input.remove(4);

        check(ControllerInputState.frameMask(0,B)==B,"short press and release survives until next frame");
        check(ControllerInputState.frameMask(RIGHT,LEFT)==RIGHT,
            "held direction wins over earlier quick opposite tap");
        check(ControllerInputState.frameMask(0,LEFT|RIGHT)==0,
            "rapid opposite taps do not send invalid directions");
        check(input.axes(5,0,0,0,-1f,0,0,.20f,.20f,0,0)==UP,
            "hat supports controllers without analog displacement");
        input.clear();
        check(input.axes(6,0,0,0,0,.7f,0,.20f,.20f,0,0)==L,
            "analog left trigger becomes the SNES L button");
        check(input.key(6,210,10,true)==L&&
              input.axes(6,0,0,0,0,0,0,.20f,.20f,0,0)==L,
            "duplicate key and axis reports do not release each other");
        check(input.key(6,211,10,true)==L&&input.key(6,210,10,false)==L,
            "L1 release does not clear L2 held on the same pad");
        check(input.key(6,211,10,false)==0,"last trigger release clears the button once");
        System.out.println("ControllerInputTest OK: Bluetooth/USB input, edges, per-device isolation and axes.");
    }
}
