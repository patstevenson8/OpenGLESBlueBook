package edu.gatech.gtri.geomipmap;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class GeoMipMapRenderer implements GLSurfaceView.Renderer
{
    ///
    // Constructor
    //
    public GeoMipMapRenderer ( Context context )
    {
    }

    ///
    // Initialize the shader and program object
    //
    public void onSurfaceCreated ( GL10 glUnused, EGLConfig config )
    {
        // This will clear the background color to sky blue
        float skyColor[] = {0.75f, 0.75f, 1.0f, 1.0f};
        GLES30.glClearColor(skyColor[0], skyColor[1], skyColor[2], skyColor[3]);

        // A sample square to show origin
        mSquare = new Square();

        // Initialize the GeoMipMap algorithm
        gLand = new GeoMMLandscape();

        // Adjust environment based on data read for landscape
        sceneCameraPosition[2] = 1.5f * gLand.getWidth() * gLand.getSpacing();
        kFarClip = 4.0f * gLand.getWidth() * gLand.getSpacing();

        mGlThread = Thread.currentThread();
    }

    ///
    // Draw the terrain created in onSurfaceCreated()
    //
    public void onDrawFrame ( GL10 glUnused )
    {
        // Set the viewport
        GLES30.glViewport(0, 0, mWidth, mHeight);

        // Clear the color buffer
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0, sceneCameraPosition[0], sceneCameraPosition[1], sceneCameraPosition[2], sceneCameraViewPosition[0], sceneCameraViewPosition[1], sceneCameraViewPosition[2], 0.0f, 1.0f, 0.0f);

        // First, load the identity matrix into the model matrix
        Matrix.setIdentityM(mModelMatrix, 0);

        // This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);

        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);

        // Draw square, this also reflects the scene orientation
        mSquare.draw(mMVPMatrix);

        // Perform the actual rendering of the mesh
        gLand.render(mMVPMatrix);
    }

    ///
    // Handle surface changes
    //
    public void onSurfaceChanged (GL10 glUnused, int width, int height )
    {
        // Adjust the viewport based on geometry changes, such as screen rotation
        GLES20.glViewport(0, 0, width, height);
        float ratio = (float) width / height;

        // Produce the perspective projection
        double right = kNearClip*Math.tan(60.0/*FOVX*//2.0*Math.PI/180.0 );
        double top = right/ratio;
        double bottom = -top;
        double left = -right;

        // This projection matrix is applied to object coordinates in the onDrawFrame() method
        Matrix.frustumM(mProjectionMatrix, 0, (float)left, (float)right, (float)bottom, (float)top, kNearClip, kFarClip);

        mWidth = width;
        mHeight = height;
    }

    /**
     * Zooms the camera along the vector to the current look-at position by the
     * specified relative factor.  A factor of <code>2.0f</code> would decrease
     * the distance between the camera and the look-at position by half; a
     * factor of <code>0.5f</code> would double the distance between the camera
     * and look-at position.
     *
     * @param relativeScale The relative scale factor
     */
    public void zoomCamera(final float relativeScale)
    {
        // Compute the vector from the camera to the look-at position
        float vx = sceneCameraViewPosition[0] - sceneCameraPosition[0];
        float vy = sceneCameraViewPosition[1] - sceneCameraPosition[1];
        float vz = sceneCameraViewPosition[2] - sceneCameraPosition[2];

        // Recompute the camera position by scaling the vector by the specified
        // relative scale value
        sceneCameraPosition[0] = sceneCameraViewPosition[0] - (vx/relativeScale);
        sceneCameraPosition[1] = sceneCameraViewPosition[1] - (vy/relativeScale);
        sceneCameraPosition[2] = sceneCameraViewPosition[2] - (vz/relativeScale);
    }

    // Handle to a program object
    private int mProgramObject;

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mModelMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mMVPMatrix = new float[16];

    //	Map scale
    private static final float kAltScale = 1;

    //	For perspective calculation
    private float kNearClip = 1;
    private float kFarClip = 2;

    // Rotation Indexes
    private static final int ROTATE_PITCH = 0;
    private static final int ROTATE_YAW = 1;
    private static final int ROTATE_ROLL = 2;

    // Scene camera
    private float sceneCameraPosition[] = {0.0f, 0.0f, 0.0f, 1.0f};
    private float sceneCameraViewPosition[] = {0.0f, 0.0f, 0.0f, 1.0f};

    // A reference for a basic square along the origin
    private Square mSquare;

    //	A reference to our landscape rendering algorithm
    private GeoMMLandscape gLand;

    private Thread mGlThread = null;

    private float mViewportWidth = 1f;
    private float mViewportHeight = 1f;

    // Additional member variables
    private int mWidth;
    private int mHeight;
}
