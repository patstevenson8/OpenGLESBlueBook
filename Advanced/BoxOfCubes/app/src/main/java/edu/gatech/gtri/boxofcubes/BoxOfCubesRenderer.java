package edu.gatech.gtri.boxofcubes;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;

import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import edu.gatech.gtri.common.ESShader;
import edu.gatech.gtri.common.ESShapes;
import edu.gatech.gtri.common.ESTransform;

public class BoxOfCubesRenderer implements GLSurfaceView.Renderer
{

   ///
   // Constructor
   //
   public BoxOfCubesRenderer ( Context context )
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

      // Generate the vertex data for a box of cubes
      CreateBoxOfCubes();

      // Starting rotation angle for the cube
      mAngle = 45.0f;

      // To use alpha transparency
      GLES30.glEnable( GLES30.GL_BLEND );
      GLES30.glBlendColor( 1.0f, 1.0f, 1.0f, 0.0f );
      GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA);

      // Set the clear color
      GLES30.glClearColor ( 1.0f, 1.0f, 1.0f, 0.0f );
   }

   private void CreateBoxOfCubes()
   {
      int i = 0;
      // Loop through the entire set of cubes based on texture size of 128x128x16
      for ( int x = 0; x < xLength; x++ )
      {
         for ( int y = 0; y < yLength; y++ )
         {
            for ( int z = 0; z < zLength; z++ )
            {
               mCubes[ i ] = new ESShapes();
               mCubes[ i ].genCube ( 1.0f );
               MoveCube ( mCubes[ i ], x * 1.0f, y * 1.0f, z * 1.0f );
               i++;
            }
         }
      }
   }

   private void MoveCube ( ESShapes shape, float xOffset, float yOffset, float zOffset )
   {
      int i = 0;
      for ( i = 0; i < shape.getVertices().capacity(); i+=3 )
      {
         shape.getVertices().put ( i,     shape.getVertices().get ( i )     + xOffset );
         shape.getVertices().put ( i + 1, shape.getVertices().get ( i + 1 ) + yOffset );
         shape.getVertices().put ( i + 2, shape.getVertices().get ( i + 2 ) + zOffset);
      }
   }

   private void update()
   {
      if ( mLastTime == 0 )
      {
         mLastTime = SystemClock.uptimeMillis();
      }

      long curTime = SystemClock.uptimeMillis();
      long elapsedTime = curTime - mLastTime;
      float deltaTime = elapsedTime / 1000.0f;
      mLastTime = curTime;

      ESTransform perspective = new ESTransform();
      ESTransform modelview = new ESTransform();
      float aspect;

      // Compute a rotation angle based on time to rotate the cube
      mAngle += ( deltaTime * 40.0f );

      if ( mAngle >= 360.0f )
      {
         mAngle -= 360.0f;
      }

      // Compute the window aspect ratio
      aspect = ( float ) mWidth / ( float ) mHeight;

      // Generate a perspective matrix with a 60 degree FOV
      perspective.matrixLoadIdentity();
      perspective.perspective ( 60.0f, aspect, 1.0f, 200.0f );

      // Generate a model view matrix to rotate/translate the cube
      modelview.matrixLoadIdentity();

      // Translate away from the viewer
      modelview.translate ( 0.0f, 0.0f, -100.0f );

      // Rotate the cube
      modelview.rotate ( mAngle, 0.0f, 1.0f, 0.0f );

      // Compute the final MVP by multiplying the
      // modevleiw and perspective matrices together
      mMVPMatrix.matrixMultiply ( modelview.get(), perspective.get() );
   }

   ///
   // Draw a triangle using the shader pair created in onSurfaceCreated()
   //
   public void onDrawFrame ( GL10 glUnused )
   {
      update();

      // Set the viewport
      GLES30.glViewport ( 0, 0, mWidth, mHeight );

      // Clear the color buffer
      GLES30.glClear ( GLES30.GL_COLOR_BUFFER_BIT );

      // Use the program object
      GLES30.glUseProgram ( mProgramObject );

      // Load the MVP matrix
      GLES30.glUniformMatrix4fv ( mMVPLoc, 1, false, mMVPMatrix.getAsFloatBuffer() );

      // Load the vertex data and draw
      DrawCubes();
   }

   public void DrawCubes()
   {
      for (int i = 0; i < mCubes.length; i++)
      {
         // Set the vertex color to alphas in red
         GLES30.glVertexAttrib4f ( 1, 1.0f, 0.0f, 0.0f, 0.3f );
         GLES30.glVertexAttribPointer ( 0, 3, GLES30.GL_FLOAT, false, 0, mCubes[i].getVertices() );
         GLES30.glEnableVertexAttribArray ( 0 );
         GLES30.glDrawElements ( GLES30.GL_TRIANGLES, mCubes[i].getNumIndices(), GLES30.GL_UNSIGNED_SHORT, mCubes[i].getIndices() );
      }
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

   // Uniform locations
   private int mMVPLoc;

   // Vertex data
   private int xLength = 64;
   private int yLength = 64;
   private int zLength = 16;
   private ESShapes mCube = new ESShapes();
   private ESShapes [] mCubes = new ESShapes[ xLength * yLength * zLength ];

   // Rotation angle
   private float mAngle;

   // MVP matrix
   private ESTransform mMVPMatrix = new ESTransform();

   // Additional Member variables
   private int mWidth;
   private int mHeight;
   private long mLastTime = 0;
}
