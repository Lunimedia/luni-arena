package br.com.knightjoga;

/** Aspect calculations shared by full-frame and local split-screen display modes. */
final class VideoGeometry {
    private VideoGeometry(){}

    static float aspect(float frameAspect,int fullWidth,int fullHeight,int regionWidth,int regionHeight){
        if(frameAspect<=0||fullWidth<=0||fullHeight<=0||regionWidth<=0||regionHeight<=0)
            return 4f/3f;
        // The SNES framebuffer often uses non-square pixels. Calculate region size
        // from the display aspect instead of the raw pixel-width/pixel-height ratio.
        return frameAspect*(regionWidth/(float)fullWidth)/(regionHeight/(float)fullHeight);
    }

    static float cropWidthFraction(float regionAspect,float targetAspect){
        return regionAspect<=0||targetAspect<=0?1f:Math.min(1f,targetAspect/regionAspect);
    }

    static float cropHeightFraction(float regionAspect,float targetAspect){
        return regionAspect<=0||targetAspect<=0?1f:Math.min(1f,regionAspect/targetAspect);
    }
}
