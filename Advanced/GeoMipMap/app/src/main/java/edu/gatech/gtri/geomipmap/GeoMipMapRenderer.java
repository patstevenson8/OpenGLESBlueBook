package edu.gatech.gtri.geomipmap;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import edu.gatech.gtri.common.ESShader;
import edu.gatech.gtri.common.ESShapes;
import edu.gatech.gtri.common.ESTransform;

public class GeoMipMapRenderer implements GLSurfaceView.Renderer
{
    public GeoMipMapRenderer ( Context context )
    {
    }

    ///
    // Initialize the shader and program object
    //
    public void onSurfaceCreated (GL10 glUnused, EGLConfig config )
    {
        String vShaderStr =
                "#version 300 es                                \n" +
                        "uniform mat4 u_mvpMatrix;                   \n" +
                        "layout(location = 0) in vec4 a_position;    \n" +
                        "layout(location = 1) in vec4 a_color;       \n" +
                        "out vec4 v_color;                           \n" +
                        "void main()                                 \n" +
                        "{                                           \n" +
                        "   v_color = a_color;                       \n" +
                        "   gl_Position = u_mvpMatrix * a_position;  \n" +
                        "}                                           \n";

        String fShaderStr =
                "#version 300 es                                \n" +
                        "precision mediump float;                    \n" +
                        "in vec4 v_color;                            \n" +
                        "layout(location = 0) out vec4 outColor;     \n" +
                        "void main()                                 \n" +
                        "{                                           \n" +
                        "  outColor = v_color;                       \n" +
                        "}                                           \n";

        // Load the shaders and get a linked program object
        mProgramObject = ESShader.loadProgram ( vShaderStr, fShaderStr );

        // Get the uniform locations
        mMVPLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_mvpMatrix" );

        // Generate the vertex data
        mCube.genCube( 1.0f);

        // Set the clear color
        GLES30.glClearColor ( 1.0f, 1.0f, 1.0f, 0.0f );

        mGLThread = Thread.currentThread();
    }

    ///
    // Draw a triangle using the shader pair created in onSurfaceCreated()
    //
    public void onDrawFrame ( GL10 glUnused )
    {
        // Clear the color buffer
        GLES30.glClear ( GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT );

        // Use the program object
        GLES30.glUseProgram ( mProgramObject );

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

        // Load the MVP matrix
        FloatBuffer mMatrix = ByteBuffer.allocateDirect ( mMVPMatrix.length * Float.BYTES ).order ( ByteOrder.nativeOrder() ).asFloatBuffer();
        mMatrix.put ( mMVPMatrix ).position ( 0 );
        GLES30.glUniformMatrix4fv ( mMVPLoc, 1, false, mMatrix );

        // Load the vertex data
        GLES30.glVertexAttribPointer ( 0, 3, GLES30.GL_FLOAT, false, 0, mCube.getVertices() );
        GLES30.glEnableVertexAttribArray ( 0 );

        // Set the vertex color to red
        GLES30.glVertexAttrib4f ( 1, 1.0f, 0.0f, 0.0f, 1.0f );

        // Draw the cube
        GLES30.glDrawElements ( GLES30.GL_TRIANGLES, mCube.getNumIndices(), GLES30.GL_UNSIGNED_SHORT, mCube.getIndices() );
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
        double right = kNearClip*Math.tan( 60.0f /*FOVX*/ / 2.0 * Math.PI / 180.0 );
        double top = right/ratio;
        double bottom = -top;
        double left = -right;

        // This projection matrix is applied to object coordinates in the onDrawFrame() method
        Matrix.frustumM(mProjectionMatrix, 0, (float)left, (float)right, (float)bottom, (float)top, kNearClip, kFarClip);

        mViewportWidth = width;
        mViewportHeight = height;
    }

    private void checkGLThread(String method)
    {
        if(Thread.currentThread() != mGLThread)
            Log.w("GeoMipMapRenderer", method + " is not invoked on the GL thread!");
    }

    //
    // Zooms the camera along the vector to the current look-at position by the
    // specified relative factor.  A factor of <code>2.0f</code> would decrease
    // the distance between the camera and the look-at position by half; a
    // factor of <code>0.5f</code> would double the distance between the camera
    // and look-at position.
    //
    // @param relativeScale The relative scale factor
    //
    public void zoomCamera(final float relativeScale)
    {
        checkGLThread("zoomCamera");

        // Compute the vector from the camera to the look-at position
        float vx = sceneCameraViewPosition[0] - sceneCameraPosition[0];
        float vy = sceneCameraViewPosition[1] - sceneCameraPosition[1];
        float vz = sceneCameraViewPosition[2] - sceneCameraPosition[2];

        Log.d("GeoMipMapRenderer", "scale " + relativeScale);

        // Recompute the camera position by scaling the vector by the specified
        // relative scale value
        sceneCameraPosition[0] = sceneCameraViewPosition[0] - (vx/relativeScale);
        sceneCameraPosition[1] = sceneCameraViewPosition[1] - (vy/relativeScale);
        sceneCameraPosition[2] = sceneCameraViewPosition[2] - (vz/relativeScale);
    }

    public void multipleFingersDrag(float dx, float dy)
    {
        checkGLThread("multipleFingerDrag");

        // NOTE: Transforming the motion to convert relative movements along the
        //       Y-axis to relative motions along the Z-axis is currently not
        //       working when rotation is applied.  There must be something
        //       wrong with the axes of the rotation vector that gets used to
        //       rotate the motion vector from the XY plane onto the XZ plane.

        // This flag will determine how the change in Y value is to be
        // interpreted.  If 'true', drags along the Y axis will result in
        // pushing the camera and model along the Z axis. If 'false', drags
        // along the Y axis will result in the model and camera being translated
        // along the Y axis.
        final boolean dyPushesZAxis = false;

        // Effect the drag based on the configuration
        if(dyPushesZAxis)
            multipleFingersDragPushZ(dx, dy);
        else
            multipleFingersDragPushY(dx, dy);
    }

    private void multipleFingersDragPushY(float dx, float dy)
    {
        // Obtain the scene center position in Normalized Device Coordinates. We
        // do this to obtain the Z-coordinate on the plane that the look-at
        // position passes through.  We need this Z coordinate when computing
        // how many model units the scene should be dragged.
        float[] sceneCenterNdc = new float[4];
        Matrix.multiplyMV(sceneCenterNdc, 0, mMVPMatrix, 0, sceneCameraViewPosition, 0);

        // Convert the change in X and Y in screen coordinates to Normalized
        // Device Coordinates.  Set the Z value to the Z of the NDC coordinate
        // for the screen center, since this is the plane that we want to
        // compute the model coordinates on. Remember that the Y axes are
        // inverted between the android.view Coordinate System and OpenGL.
        final float normalizedDx = dx / mViewportWidth;
        final float normalizedDy = -dy / mViewportHeight;
        final float normalizedDz = sceneCenterNdc[2] / sceneCenterNdc[3];

        // Inverse the Model-View-Projection matrix. We will use this to convert
        // from NDC to model coordinates
        float[] invMVP = new float[16];
        Matrix.invertM(invMVP, 0, mMVPMatrix, 0);

        // Create an array to store computed NDC coordinates
        float[] ndc = new float[4];

        // Transform the NDC screen drag movement into model coordinates by
        // multiplying the NDC point, on the plane of the scene center, with
        // the inverse of the Model-View-Projection matrix.
        float[] sceneDrag = new float[4];
        ndc[0] = normalizedDx;
        ndc[1] = normalizedDy;
        ndc[2] = normalizedDz;
        ndc[3] = 1f;
        Matrix.multiplyMV(sceneDrag, 0, invMVP, 0, ndc, 0);
        sceneDrag[0] /= sceneDrag[3];
        sceneDrag[1] /= sceneDrag[3];
        sceneDrag[2] /= sceneDrag[3];
        sceneDrag[3] /= sceneDrag[3];

        // Transform the NDC of the screen center into model coordinates by
        // multiplying the NDC point, on the plane of the scene center, with
        // the inverse of the Model-View-Projection matrix.  We will subtract
        // this value from 'sceneDrag' to compute the relative translation in
        // model coordinates
        float[] sceneCenter = new float[4];
        ndc[0] = 0f;
        ndc[1] = 0f;
        ndc[2] = normalizedDz;
        ndc[3] = 1f;
        Matrix.multiplyMV(sceneCenter, 0, invMVP, 0, ndc, 0);
        sceneCenter[0] /= sceneCenter[3];
        sceneCenter[1] /= sceneCenter[3];
        sceneCenter[2] /= sceneCenter[3];
        sceneCenter[3] /= sceneCenter[3];

        // Compute the translation.  This will be the difference between the
        // scene center and the scene drag in model coordinates lying on the
        // plane containing the look-at position.
        final float tx = sceneCenter[0] - sceneDrag[0];
        final float ty = sceneCenter[1] - sceneDrag[1];
        final float tz = sceneCenter[2] - sceneDrag[2];

        // Adjust the camera and look-at positions by the computed translation.
        sceneCameraPosition[0] += tx;
        sceneCameraPosition[1] += ty;
        sceneCameraPosition[2] += tz;

        sceneCameraViewPosition[0] += tx;
        sceneCameraViewPosition[1] += ty;
        sceneCameraViewPosition[2] += tz;

        Log.i("GeoMipMapRenderer", "Drag (" + dx + "," + dy + ") tx=" + tx + " ty=" + ty + " tz=" + tz);
    }

    private void multipleFingersDragPushZ(float dx, float dy)
    {
        // Obtain the scene center position in Normalized Device Coordinates. We
        // do this to obtain the Z-coordinate on the plane that the look-at
        // position passes through.  We need this Z coordinate when computing
        // how many model units the scene should be dragged.
        float[] sceneCenterNdc = new float[4];
        Matrix.multiplyMV(sceneCenterNdc, 0, mMVPMatrix, 0, sceneCameraViewPosition, 0);

        // Convert the change in X and Y in screen coordinates to Normalized
        // Device Coordinates.  Set the Z value to the Z of the NDC coordinate
        // for the screen center, since this is the plane that we want to
        // compute the model coordinates on. Remember that the Y axes are
        // inverted between the android.view Coordinate System and OpenGL.
        final float normalizedDx = dx / mViewportWidth;
        final float normalizedDy = -dy / mViewportHeight;
        final float normalizedDz = sceneCenterNdc[2] / sceneCenterNdc[3];

        // Inverse the Model-View-Projection matrix. We will use this to convert
        // from NDC to model coordinates
        float[] invMVP = new float[16];
        Matrix.invertM(invMVP, 0, mMVPMatrix, 0);

        // Create an array to store computed NDC coordinates
        float[] ndc = new float[4];

        // Transform the NDC screen drag movement into model coordinates by
        // multiplying the NDC point, on the plane of the scene center, with
        // the inverse of the Model-View-Projection matrix.
        float[] sceneDrag = new float[4];
        ndc[0] = normalizedDx;
        ndc[1] = normalizedDy;
        ndc[2] = normalizedDz;
        ndc[3] = 1f;
        Matrix.multiplyMV(sceneDrag, 0, invMVP, 0, ndc, 0);
        sceneDrag[0] /= sceneDrag[3];
        sceneDrag[1] /= sceneDrag[3];
        sceneDrag[2] /= sceneDrag[3];
        sceneDrag[3] /= sceneDrag[3];

        // Transform the NDC of the screen center into model coordinates by
        // multiplying the NDC point, on the plane of the scene center, with
        // the inverse of the Model-View-Projection matrix.  We will subtract
        // this value from 'sceneDrag' to compute the relative translation in
        // model coordinates
        float[] sceneCenter = new float[4];
        ndc[0] = 0f;
        ndc[1] = 0f;
        ndc[2] = normalizedDz;
        ndc[3] = 1f;
        Matrix.multiplyMV(sceneCenter, 0, invMVP, 0, ndc, 0);
        sceneCenter[0] /= sceneCenter[3];
        sceneCenter[1] /= sceneCenter[3];
        sceneCenter[2] /= sceneCenter[3];
        sceneCenter[3] /= sceneCenter[3];

        // Compute the translation relative to the X and Y axes.  This will be
        // the difference between the scene center and the scene drag in model
        // coordinates lying on the plane containing the look-at position.
        float[] translationRelativeXY = new float[4];
        translationRelativeXY[0] = sceneCenter[0] - sceneDrag[0];
        translationRelativeXY[1] = sceneCenter[1] - sceneDrag[1];
        translationRelativeXY[2] = sceneCenter[2] - sceneDrag[2];
        translationRelativeXY[3] = 1f;

        // Rotate the translation vector about the X axis to convert motion
        // relative to the XY plane as motion relative to the XZ plane. This
        // should result in relative motion along the Y axis being transformed
        // into relative motion along the Z axis while preserving relative
        // motion along the X axis.
        float[] txRotation = new float[16];
        Matrix.setRotateM(txRotation, 0, -90, 1f, 0f, 0f);

        float[] translationRelativeXZ = new float[4];
        Matrix.multiplyMV(translationRelativeXZ, 0, txRotation, 0, translationRelativeXY, 0);
        translationRelativeXZ[0] /= translationRelativeXZ[3];
        translationRelativeXZ[1] /= translationRelativeXZ[3];
        translationRelativeXZ[2] /= translationRelativeXZ[3];
        translationRelativeXZ[3] /= translationRelativeXZ[3];

        final float tx = translationRelativeXZ[0];
        final float ty = translationRelativeXZ[1];
        final float tz = translationRelativeXZ[2];

        // Adjust the camera and look-at positions by the computed translation.
        sceneCameraPosition[0] += tx;
        sceneCameraPosition[1] += ty;
        sceneCameraPosition[2] += tz;

        sceneCameraViewPosition[0] += tx;
        sceneCameraViewPosition[1] += ty;
        sceneCameraViewPosition[2] += tz;

        Log.i("GeoMipMapRenderer", "Drag (" + dx + "," + dy + ") tx=" + tx + " ty=" + ty + " tz=" + tz);
    }

    public void singleFingerDrag(float dx, float dy)
    {
        checkGLThread("singleFingerDrag");

        Log.d("GeoMipMapRenderer", "rotate " + ((float) dx * 0.5f));

        // Create a matrix that will rotate a point about the look-at position
        float[] mx = new float[16];
        Matrix.setIdentityM(mx, 0);
        Matrix.translateM(mx, 0, sceneCameraViewPosition[0], sceneCameraViewPosition[1], sceneCameraViewPosition[2]);
        Matrix.rotateM(mx, 0, dy * -0.5f, 1f, 0f, 0f);
        Matrix.rotateM(mx, 0, dx * -0.5f, 0f, 1f, 0f);
        Matrix.translateM(mx, 0, -sceneCameraViewPosition[0], -sceneCameraViewPosition[1], -sceneCameraViewPosition[2]);

        // Transform the current camera position by our computed rotation matrix
        // to compute the new position of the camera relative to the look-at
        // point following the rotation
        float[] cam = new float[4];
        cam[0] = sceneCameraPosition[0];
        cam[1] = sceneCameraPosition[1];
        cam[2] = sceneCameraPosition[2];
        cam[3] = 1f;
        float[] result = new float[4];
        Matrix.multiplyMV(result, 0, mx, 0, cam, 0);

        // Update the camera position
        sceneCameraPosition[0] = result[0] / result[3];
        sceneCameraPosition[1] = result[1] / result[3];
        sceneCameraPosition[2] = result[2] / result[3];
    }

    // Handle to a program object
    private int mProgramObject;

    // Uniform locations
    private int mMVPLoc;

    // Vertex data
    private ESShapes mCube = new ESShapes();

    // Rotation angle
    private float mAngleX = 45.0f;
    private float mAngleY = 0.0f;
    private float mAngleZ = 0.0f;

    // Additional Member variables
    private float mViewportWidth = 1.0f;
    private float mViewportHeight = 1.0f;
    private Thread mGLThread = null;

    // mMVPMatrix is an abbreviation for "Model View Projection Matrix"
    private final float[] mModelMatrix = new float[16];
    private final float[] mViewMatrix = new float[16];
    private final float[] mProjectionMatrix = new float[16];
    private final float[] mMVPMatrix = new float[16];

    //	Map scale
    private static final float kAltScale = 1;

    //	For perspective calculation
    private float kNearClip = 1.0f;
    private float kFarClip = 20.0f;

    // Rotation Indexes
    private static final int ROTATE_PITCH = 0;
    private static final int ROTATE_YAW = 1;
    private static final int ROTATE_ROLL = 2;

    // Scene camera
    private float sceneCameraPosition[] = {0.0f, 0.0f,-3.0f, 1.0f};
    private float sceneCameraViewPosition[] = {0.0f, 0.0f, 0.0f, 1.0f};
}
