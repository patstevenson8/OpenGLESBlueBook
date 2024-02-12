package edu.gatech.gtri.geomipmap;

// This is a common interface used by Level-Of-Detail landscape rendering algorithms
public interface LODLandscape
{
    // Drawing Modes
    int kUseTexture = 0;
    int kUseFlatShading = 1;
    int kUseGauradShading = 2;
    int kUseWireFrame = 3;

    // Set the drawing mode to one of the constants defined in this class
    public void setDrawMode(int mode);

    // Return the drawing mode used by this landscape
    // Returns one of the drawing mode constants defined in this class
    public int getDrawMode();

    //  Return the level-of-detail threshold, or triangle count,
    //  or whatever is required to set the level of detail used
    //  by this landscape rendering algorithm
    public int getLevelOfDetail();

    //  Set the level-of-detail threshold, or triangle count,
    //  or whatever is required to set the level of detail used
    //  by this landscape rendering algorithm
    public void setLevelOfDetail(int number);

    //  Returns the actual number of triangles rendered during
    //  the last pass through the render() method
    public int numTrianglesRendered();

    /**
     *  Updates the visibility of regions of the terrain for the current view
     *  field and updates any level of detail information required by this
     *  algorithm (such as taking the current view point into consideration
     *  when tesselating).
     *
     *  @param  fovX         The field of view in degrees.
     *  @param  viewPosition The location of the camera in model coordinates.
     *  @param  clipAngle    The direction the camera is pointing.
     **/
    public void update(float fovX, float[] viewPosition, float clipAngle);

    // Render the landscape
    public void render(float[] mvpMatrix);
}
