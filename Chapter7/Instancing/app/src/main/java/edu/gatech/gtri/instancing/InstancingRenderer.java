package edu.gatech.gtri.instancing;

import static javax.microedition.khronos.opengles.GL11.GL_ELEMENT_ARRAY_BUFFER;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.os.SystemClock;

import java.io.IOException;
import java.io.InputStream;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Random;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import edu.gatech.gtri.common.ESShader;
import edu.gatech.gtri.common.ESShapes;
import edu.gatech.gtri.common.ESTransform;

public class InstancingRenderer implements GLSurfaceView.Renderer
{
   ///
   // Constructor
   //
   public InstancingRenderer ( Context context )
   {
      mContext = context;
   }

   ///
   // Initialize the shader and program object
   //
   public void onSurfaceCreated (GL10 glUnused, EGLConfig config )
   {
      String vShaderStr =
         "#version 300 es                            \n" +
            "layout(location = 0) in vec4 a_position;   \n" +
            "layout(location = 1) in vec4 a_color;      \n" +
            "layout(location = 2) in mat4 a_mvpMatrix;  \n" +
            "out vec4 v_color;                          \n" +
            "void main()                                \n" +
            "{                                          \n" +
            "    v_color = a_color;                     \n" +
            "    gl_Position = a_mvpMatrix * a_position;\n" +
            "}";


      String fShaderStr =
         "#version 300 es            \n" +
            "precision mediump float;   \n" +
            "in vec4 v_color;           \n" +
            "out vec4 outColor;         \n" +
            "void main()                \n" +
            "{                          \n" +
            "    outColor = v_color;    \n" +
            "}" ;

      // Load the shaders and get a linked program object
      mProgramObject = ESShader.loadProgram ( vShaderStr, fShaderStr );

      positionVBO[0] = 0;
      colorVBO[0] = 0;
      mvpVBO[0] = 0;
      indicesIBO[0] = 0;

      mCube.genCube ( 0.1f );

      // Index buffer object
      GLES30.glGenBuffers ( 1, indicesIBO, 0 );
      GLES30.glBindBuffer ( GLES30.GL_ELEMENT_ARRAY_BUFFER, indicesIBO[0] );
      GLES30.glBufferData ( GLES30.GL_ELEMENT_ARRAY_BUFFER, Short.BYTES * mCube.getNumIndices(), mCube.getIndices(), GLES30.GL_STATIC_DRAW );
      GLES30.glBindBuffer ( GLES30.GL_ELEMENT_ARRAY_BUFFER, 0 );

      // Position VBO for cube model
      GLES30.glGenBuffers ( 1, positionVBO, 0 );
      GLES30.glBindBuffer ( GLES30.GL_ARRAY_BUFFER, positionVBO[0] );
      GLES30.glBufferData ( GLES30.GL_ARRAY_BUFFER, 24 * Float.BYTES * 3, mCube.getVertices(), GLES30.GL_STATIC_DRAW );

      // Random color for each instance
      Random rand = new Random ( 0 );
      {
         int instance;
         float [] colors = new float[NUM_INSTANCES * Float.BYTES];
         FloatBuffer mColors = ByteBuffer.allocateDirect ( NUM_INSTANCES * colors.length * Float.BYTES).order ( ByteOrder.nativeOrder() ).asFloatBuffer();

         for ( instance = 0; instance < NUM_INSTANCES; instance++ )
         {
            colors[instance * Float.BYTES + 0] = (short) (rand.nextInt() % 255);
            colors[instance * Float.BYTES + 1] = (short) (rand.nextInt() % 255);
            colors[instance * Float.BYTES + 2] = (short) (rand.nextInt() % 255);
            colors[instance * Float.BYTES + 3] = 255;
         }
         mColors.put ( colors ).position ( 0 );

         GLES30.glGenBuffers ( 1, colorVBO, 0 );
         GLES30.glBindBuffer ( GLES30.GL_ARRAY_BUFFER, colorVBO[0] );
         GLES30.glBufferData ( GLES30.GL_ARRAY_BUFFER, NUM_INSTANCES * colors.length * Float.BYTES, mColors, GLES30.GL_STATIC_DRAW );
      }

      // Allocate storage to store MVP per instance
      {
         int instance;
         int numVertices = new ESTransform().get().length;
         int vtxStride = 4 * ( VERTEX_POS_SIZE );

         // Random angle for each instance, compute the MVP later
         for ( instance = 0; instance < NUM_INSTANCES; instance++ )
         {
            mAngle[instance] = ( float ) ( rand.nextFloat() % 32768 ) / 32767.0f * 360.0f;
         }

         GLES30.glGenBuffers ( 1, mvpVBO, 0 );
         GLES30.glBindBuffer ( GLES30.GL_ARRAY_BUFFER, mvpVBO[0] );
         GLES30.glBufferData ( GLES30.GL_ARRAY_BUFFER, vtxStride * numVertices * NUM_INSTANCES, null, GLES30.GL_DYNAMIC_DRAW );
      }
      GLES30.glBindBuffer ( GLES30.GL_ARRAY_BUFFER, 0 );

      // Clear color
      GLES30.glClearColor( 1.0f, 1.0f, 1.0f, 0.0f );
   }

   private void update()
   {
      FloatBuffer matrixBuf;
      ESTransform perspective = new ESTransform();
      float aspect;
      int instance = 0;
      int numRows;
      int numColumns;
      int numVertices = new ESTransform().get().length;
      int vtxStride = 4 * ( VERTEX_POS_SIZE );

      if ( mLastTime == 0 )
      {
         mLastTime = SystemClock.uptimeMillis();
      }

      long curTime = SystemClock.uptimeMillis();
      long elapsedTime = curTime - mLastTime;
      float deltaTime = elapsedTime / 2000.0f;
      mLastTime = curTime;

      // Compute the window aspect ratio
      aspect = ( float ) mWidth / ( float ) mHeight;

      // Generate a perspective matrix with a 60 degree FOV
      perspective.matrixLoadIdentity();
      perspective.perspective ( 60.0f, aspect, 1.0f, 20.0f );

      GLES30.glBindBuffer ( GLES30.GL_ARRAY_BUFFER, mvpVBO[0] );
      matrixBuf =
         ( ( ByteBuffer ) GLES30.glMapBufferRange (
            GLES30.GL_ARRAY_BUFFER, 0, vtxStride * numVertices * NUM_INSTANCES,
            GLES30.GL_MAP_WRITE_BIT )
         ).order ( ByteOrder.nativeOrder() ).asFloatBuffer();

      // Compute a per-instance MVP that translates and rotates each instance differnetly
      numRows = ( int ) Math.sqrt ( NUM_INSTANCES );
      numColumns = numRows;

      matrixBuf.position(0);

      for ( instance = 0; instance < NUM_INSTANCES; instance++ )
      {
         ESTransform finalMat = new ESTransform();
         ESTransform modelview = new ESTransform();
         float translateX = ((float) (instance % numRows) / (float) numRows) * 2.0f - 1.0f;
         float translateY = ((float) (instance / numColumns) / (float) numColumns) * 2.0f - 1.0f;

         // Generate a model view matrix to rotate/translate the cube
         modelview.matrixLoadIdentity();

         // Per-instance translation
         modelview.translate( translateX, translateY, -2.0f );

         // Compute a rotation angle based on time to rotate the cube
         mAngle[instance] += (deltaTime * 40.0f);

         if (mAngle[instance] >= 360.0f)
         {
            mAngle[instance] -= 360.0f;
         }

         // Rotate the cube
         modelview.rotate(mAngle[instance], 1.0f, 0.0f, 1.0f);

         // Compute the final MVP by multiplying the
         // modevleiw and perspective matrices together
         finalMat.matrixLoadIdentity();
         finalMat.matrixMultiply(modelview.get(), perspective.get());

         // Copy the data into the mapped buffer
         matrixBuf.put ( finalMat.getAsFloatBuffer() );
      }

      GLES30.glUnmapBuffer ( GLES30.GL_ARRAY_BUFFER );
   }

   ///
   // Draw
   //
   public void onDrawFrame ( GL10 glUnused )
   {
      update();

      // Set the view-port
      GLES30.glViewport ( 0, 0, mWidth, mHeight );

      // Clear the color buffer
      GLES30.glClear ( GLES30.GL_COLOR_BUFFER_BIT );

      GLES30.glUseProgram(mProgramObject);

      // Load the vertex position
      GLES30.glBindBuffer ( GLES30.GL_ARRAY_BUFFER, positionVBO[0] );
      GLES30.glVertexAttribPointer ( POSITION_LOC, 3, GLES30.GL_FLOAT, false, 3 * Float.BYTES, 0 );
      GLES30.glEnableVertexAttribArray ( POSITION_LOC );

      // Load the instance color buffer
      GLES30.glBindBuffer ( GLES30.GL_ARRAY_BUFFER, colorVBO[0] );
      GLES30.glVertexAttribPointer ( COLOR_LOC, 4, GLES30.GL_FLOAT, true, 4 * Float.BYTES, 0 );
      GLES30.glEnableVertexAttribArray ( COLOR_LOC );
      GLES30.glVertexAttribDivisor ( COLOR_LOC, 1 ); // One color per instance

      // Load the instance MVP buffer
      GLES30.glBindBuffer ( GLES30.GL_ARRAY_BUFFER, mvpVBO[0] );

      // Load each matrix row of the MVP.  Each row gets an increasing attribute location.
      GLES30.glVertexAttribPointer ( MVP_LOC + 0, 4, GLES30.GL_FLOAT, false, 16, 0 );
      GLES30.glVertexAttribPointer ( MVP_LOC + 1, 4, GLES30.GL_FLOAT, false, 16, Float.BYTES * 4 );
      GLES30.glVertexAttribPointer ( MVP_LOC + 2, 4, GLES30.GL_FLOAT, false, 16, Float.BYTES * 8 );
      GLES30.glVertexAttribPointer ( MVP_LOC + 3, 4, GLES30.GL_FLOAT, false, 16, Float.BYTES * 12 );
      GLES30.glEnableVertexAttribArray ( MVP_LOC + 0 );
      GLES30.glEnableVertexAttribArray ( MVP_LOC + 1 );
      GLES30.glEnableVertexAttribArray ( MVP_LOC + 2 );
      GLES30.glEnableVertexAttribArray ( MVP_LOC + 3 );

      // One MVP per instance
      GLES30.glVertexAttribDivisor ( MVP_LOC + 0, 1 );
      GLES30.glVertexAttribDivisor ( MVP_LOC + 1, 1 );
      GLES30.glVertexAttribDivisor ( MVP_LOC + 2, 1 );
      GLES30.glVertexAttribDivisor ( MVP_LOC + 3, 1 );

      // Bind the index buffer
      GLES30.glBindBuffer ( GL_ELEMENT_ARRAY_BUFFER, indicesIBO[0] );

      // Draw the cubes
      GLES30.glDrawElementsInstanced ( GLES30.GL_TRIANGLES, mCube.getNumIndices(), GLES30.GL_UNSIGNED_SHORT, 0, NUM_INSTANCES );
   }

   ///
   // Handle surface changes
   //
   public void onSurfaceChanged (GL10 glUnused, int width, int height )
   {
      mWidth = width;
      mHeight = height;
   }

   // Handle to a program object
   private int mProgramObject;

   // VBOs
   private int [] positionVBO = new int[1];
   private int [] colorVBO = new int[1];
   private int [] mvpVBO = new int[1];
   private int [] indicesIBO = new int[1];

   private final int NUM_INSTANCES = 100;
   private final int POSITION_LOC = 0;
   private final int COLOR_LOC = 1;
   private final int MVP_LOC = 2;

   final int VERTEX_POS_SIZE = 3; // x, y and z

   // Vertex data
   private final ESShapes mCube = new ESShapes();

   // Rotation angle
   private float [] mAngle = new float[NUM_INSTANCES];

   // Additional member variables
   private int mWidth;
   private int mHeight;

   private final Context mContext;

   // Additional member variables
   private long mLastTime = 0;
}
