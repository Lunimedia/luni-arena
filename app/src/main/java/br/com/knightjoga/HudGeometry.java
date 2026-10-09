package br.com.knightjoga;

/** Keep normalized HUD positions intact when Android resizes or rotates the game view. */
final class HudGeometry {
    private static final float FACE_SPACING=1.9f;
    private HudGeometry(){}
    static float visible(float relative,int length,float halfSize){
        if(length<=0)return 0;
        float edge=Math.min(length*.46f,halfSize);
        return Math.max(edge,Math.min(length-edge,relative*length));
    }
    static float visible(float position,int length,float halfSize,int startInset,int endInset){
        if(length<=0)return 0;
        float min=Math.max(0,startInset)+halfSize;
        float max=length-Math.max(0,endInset)-halfSize;
        if(max<min)return length*.5f;
        return Math.max(min,Math.min(max,position));
    }
    /** Place the unedited left controls in the side gutter when there is room. */
    static float leftCluster(int width,float imageLeft,float padRadius,int inset,float density){
        float margin=8*density;
        float edge=Math.max(0,inset)+padRadius+margin;
        float center=Math.max(edge,Math.min(width*.13f,imageLeft-padRadius-margin));
        return visible(center,width,padRadius,inset,0);
    }
    /** Side insets only move buttons; a narrow gutter cannot silently shrink the user's size. */
    static float faceRadius(float preferred,float shoulderBottom,float startTop,float density){
        return Math.min(preferred,faceVerticalRadius(shoulderBottom,startTop,density));
    }
    /** Leave room for L/R and Select/Start above and below the four face buttons. */
    static float faceVerticalRadius(float shoulderBottom,float startTop,float density){
        return Math.max(1f,(startTop-shoulderBottom-16*density)/(2*(1+FACE_SPACING)));
    }
    /** Adjacent drawn circles and their normal touch targets must remain distinct. */
    static float faceStep(int width,float imageRight,float radius,int inset,float density){
        return radius*FACE_SPACING;
    }
    static float rightCluster(int width,float imageRight,float radius,int inset,float density){
        return width-Math.max(0,inset)-8*density-radius-
            faceStep(width,imageRight,radius,inset,density);
    }
    static float faceHitRadius(float radius,float step,float density){
        return Math.min(Math.max(24*density,radius*1.3f),step*.70f);
    }
    static float faceCenterY(int height,float shoulderBottom,float startTop,float radius,float step,float density){
        float lower=shoulderBottom+8*density+radius+step;
        float upper=startTop-8*density-radius-step;
        if(upper<lower)return (upper+lower)*.5f;
        return Math.max(lower,Math.min(upper,height*.65f));
    }
    static float constrain(float relative,int length,float halfSize){
        return length<=0?relative:visible(relative,length,halfSize)/length;
    }
}
