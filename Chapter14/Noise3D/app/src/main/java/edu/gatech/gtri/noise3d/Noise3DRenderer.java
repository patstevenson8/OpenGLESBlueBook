package edu.gatech.gtri.noise3d;

import android.content.Context;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;

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

public class Noise3DRenderer implements GLSurfaceView.Renderer
{
   ///
   // Constructor
   //
   public Noise3DRenderer ( Context context )
   {
      mContext = context;
   }

   void initNoiseTable()
   {
      int            i;
      float          a;
      float          x, y, z, r;
      float []       gradients = new float[256 * 3];

      Random rand = new Random();

      // build gradient table for 3D noise
      for ( i = 0; i < 256; i++ )
      {
         /*
          * calculate 1 - 2 * random number
          */
         a = ( rand.nextFloat() % 32768 ) / 32768.0f;
         z = ( 1.0f - 2.0f * a );

         r = (float) (Math.sqrt ( 1.0f - z * z )); // r is radius of circle

         a = ( rand.nextFloat() % 32768 ) / 32768.0f;
         x = ( r * (float) (Math.cos ( a )) );
         y = ( r * (float) (Math.sin ( a )) );

         gradients[i * 3] = x;
         gradients[i * 3 + 1] = y;
         gradients[i * 3 + 2] = z;
      }

      // use the index in the permutation table to load the
      // gradient values from gradients to gradientTable
      for ( i = 0; i < 256; i++ )
      {
         int indx = permTable[i];
         gradientTable[i * 3] = gradients[indx * 3];
         gradientTable[i * 3 + 1] = gradients[indx * 3 + 1];
         gradientTable[i * 3 + 2] = gradients[indx * 3 + 2];
      }
   }

   int FLOOR (float x)
   {
      return     (int) Math.floor(x);
   }

   float smoothstep(float t)
   {
      return ( t * t * t * (t * (t * 6.0f - 15.0f) + 10.0f ) );
   }

   //
   // generate the value of gradient noise for a given lattice point
   //
   // (ix, iy, iz) specifies the 3D lattice position
   // (fx, fy, fz) specifies the fractional part
   //
   float glattice3D ( int ix, int iy, int iz, float fx, float fy, float fz )
   {
      int   indx, y, z;

      z = permTable[iz & NOISE_TABLE_MASK];
      y = permTable[ ( iy + z ) & NOISE_TABLE_MASK];
      indx = ( ix + y ) & NOISE_TABLE_MASK;

      return ( gradientTable[indx * 3    ] * fx +
               gradientTable[indx * 3 + 1] * fy +
               gradientTable[indx * 3 + 2] * fz );
   }

   float lerp(float t, float a, float b)
   {
      return ( a + t * (b - a) );
   }

   //
   // generate the 3D noise value
   // f describes the input (x, y, z) position for which the noise value needs to be computed
   // noise3D returns the scalar noise value
   //
   float noise3D ( float [] f )
   {
      int   ix, iy, iz;
      float fx0, fx1, fy0, fy1, fz0, fz1;
      float wx, wy, wz;
      float vx0, vx1, vy0, vy1, vz0, vz1;

      ix = FLOOR ( f[0] );
      fx0 = f[0] - ix;
      fx1 = fx0 - 1;
      wx = smoothstep ( fx0 );

      iy = FLOOR ( f[1] );
      fy0 = f[1] - iy;
      fy1 = fy0 - 1;
      wy = smoothstep ( fy0 );

      iz = FLOOR ( f[2] );
      fz0 = f[2] - iz;
      fz1 = fz0 - 1;
      wz = smoothstep ( fz0 );

      vx0 = glattice3D ( ix, iy, iz, fx0, fy0, fz0 );
      vx1 = glattice3D ( ix + 1, iy, iz, fx1, fy0, fz0 );
      vy0 = lerp ( wx, vx0, vx1 );
      vx0 = glattice3D ( ix, iy + 1, iz, fx0, fy1, fz0 );
      vx1 = glattice3D ( ix + 1, iy + 1, iz, fx1, fy1, fz0 );
      vy1 = lerp ( wx, vx0, vx1 );
      vz0 = lerp ( wy, vy0, vy1 );

      vx0 = glattice3D ( ix, iy, iz + 1, fx0, fy0, fz1 );
      vx1 = glattice3D ( ix + 1, iy, iz + 1, fx1, fy0, fz1 );
      vy0 = lerp ( wx, vx0, vx1 );
      vx0 = glattice3D ( ix, iy + 1, iz + 1, fx0, fy1, fz1 );
      vx1 = glattice3D ( ix + 1, iy + 1, iz + 1, fx1, fy1, fz1 );
      vy1 = lerp ( wx, vx0, vx1 );
      vz1 = lerp ( wy, vy0, vy1 );

      return lerp ( wz, vz0, vz1 );
   }

   void Create3DNoiseTexture ( )
   {
      int textureSize = 64; // Size of the 3D nosie texture
      float frequency = 5.0f; // Frequency of the noise.
      float [] texBuf = new float [ 4 * textureSize * textureSize * textureSize ];
      ShortBuffer texBufUbyte = ByteBuffer.allocateDirect ( 2 * textureSize * textureSize * textureSize ).order ( ByteOrder.nativeOrder() ).asShortBuffer();
      int x, y, z;
      int index = 0;
      float min = 1000;
      float max = -1000;
      float range;

      initNoiseTable();

      for ( z = 0; z < textureSize; z++ )
      {
         for ( y = 0; y < textureSize; y++ )
         {
            for ( x = 0; x < textureSize; x++ )
            {
               float noiseVal;

               float [] pos = new float [] { ( float ) x / ( float ) textureSize, ( float ) y / ( float ) textureSize, ( float ) z  / ( float ) textureSize };
               pos[0] *= frequency;
               pos[1] *= frequency;
               pos[2] *= frequency;
               noiseVal = noise3D ( pos );

               if ( noiseVal < min )
               {
                  min = noiseVal;
               }

               if ( noiseVal > max )
               {
                  max = noiseVal;
               }

               texBuf[ index++ ] = noiseVal;
            }
         }
      }

      // Normalize to the [0, 1] range
      range = ( max - min );
      index = 0;

      for ( z = 0; z < textureSize; z++ )
      {
         for ( y = 0; y < textureSize; y++ )
         {
            for ( x = 0; x < textureSize; x++ )
            {
               float noiseVal = texBuf[index];
               noiseVal = ( noiseVal - min ) / range;
               texBufUbyte.put ( (short) (noiseVal * 255.0f) ).position ( index++ );
            }
         }
      }

      GLES30.glGenTextures ( 1, textureId, 0 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_3D, textureId[0] );
      GLES30.glTexImage3D ( GLES30.GL_TEXTURE_3D, 0, GLES30.GL_R8, textureSize, textureSize, textureSize, 0, GLES30.GL_RED, GLES30.GL_UNSIGNED_BYTE, texBufUbyte );

      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_MIRRORED_REPEAT );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_MIRRORED_REPEAT );
      GLES30.glTexParameteri ( GLES30.GL_TEXTURE_3D, GLES30.GL_TEXTURE_WRAP_R, GLES30.GL_MIRRORED_REPEAT );

      GLES30.glBindTexture ( GLES30.GL_TEXTURE_3D, 0 );
   }

   ///
   // Initialize the shader and program object
   //
   public void onSurfaceCreated (GL10 glUnused, EGLConfig config )
   {
      // Create the 3D texture
      Create3DNoiseTexture ( );

      // Load shaders from 'assets' and get a linked program object
      mProgramObject = ESShader.loadProgramFromAsset ( mContext,
         "shaders/vertexShader.vert",
         "shaders/fragmentShader.frag" );

      // Get the uniform locations
      mvpLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_mvpMatrix" );
      mvLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_mvMatrix" );
      noiseTexLoc = GLES30.glGetUniformLocation ( mProgramObject, "s_noiseTex" );
      fogMinDistLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_fogMinDist" );
      fogMaxDistLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_fogMaxDist" );
      fogColorLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_fogColor" );
      timeLoc = GLES30.glGetUniformLocation ( mProgramObject, "u_time" );

      // Generate the position and indices of a cube
      mCube.genCube ( 3.0f );

      // Starting rotation angle for the cube
      mAngle = 0.0f;
      curTime = 0.0f;

      GLES30.glEnable( GLES30.GL_DEPTH_TEST );

      // Clear color
      GLES30.glClearColor( 1.0f, 1.0f, 1.0f, 0.0f );
   }

   private void update()
   {
      ESTransform perspective = new ESTransform();
      float aspect;

      if ( mLastTime == 0 )
      {
         mLastTime = SystemClock.uptimeMillis();
      }

      long curTime = SystemClock.uptimeMillis();
      long elapsedTime = curTime - mLastTime;
      float deltaTime = elapsedTime / 2000.0f;
      mLastTime = curTime;

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
      perspective.perspective ( 60.0f, aspect, 0.1f, 20.0f );

      // Generate a model view matrix to rotate/translate the terrain
      mvMatrix.matrixLoadIdentity();

      // Center the cube
      mvMatrix.translate ( 0.0f, -0.5f, -4.5f );

      // Rotate
      mvMatrix.rotate ( mAngle, 1.0f, 0.0f, 1.0f );

      // Compute the final MVP by multiplying the
      // modelview and perspective matrices together
      mvpMatrix.matrixMultiply ( mvMatrix.get(), perspective.get() );
   }

   ///
   // Draw a cube
   //
   public void onDrawFrame ( GL10 glUnused )
   {
      update();

      // Set the view-port
      GLES30.glViewport ( 0, 0, mWidth, mHeight );

      // Clear the color buffer
      GLES30.glClear ( GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT );

      // Use the program object
      GLES30.glUseProgram ( mProgramObject );

      // Load the vertex position
      GLES30.glVertexAttribPointer ( ATTRIB_LOCATION_POS, 3, GLES30.GL_FLOAT, false, 0, mCube.getVertices() );
      GLES30.glEnableVertexAttribArray ( ATTRIB_LOCATION_POS );

      // Set the vertex color to red
      GLES30.glVertexAttrib4f ( ATTRIB_LOCATION_COLOR, 1.0f, 0.0f, 0.0f, 1.0f );

      // Load the texture coordinate
      GLES30.glVertexAttribPointer ( ATTRIB_LOCATION_TEXCOORD, 2, GLES30.GL_FLOAT, false, 0, mCube.getTexCoords() );
      GLES30.glEnableVertexAttribArray ( ATTRIB_LOCATION_TEXCOORD );

      // Bind the texture
      GLES30.glActiveTexture ( GLES30.GL_TEXTURE0 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_2D, textureId[0] );

      // Load the matrices
      GLES30.glUniformMatrix4fv ( mvpLoc, 1, false, mvpMatrix.getAsFloatBuffer() );
      GLES30.glUniformMatrix4fv ( mvLoc, 1, false, mvMatrix.getAsFloatBuffer() );

      // Load other uniforms
      {
         float [] fogColor = new float[] { 1.0f, 1.0f, 1.0f, 1.0f };
         FloatBuffer fogColorBuffer = ByteBuffer.allocateDirect ( 4 * 4 ).order ( ByteOrder.nativeOrder() ).asFloatBuffer();
         fogColorBuffer.put ( fogColor ).position ( 0 );
         float fogMinDist = 2.75f;
         float fogMaxDist = 4.0f;
         GLES30.glUniform1f ( fogMinDistLoc, fogMinDist );
         GLES30.glUniform1f ( fogMaxDistLoc, fogMaxDist );

         GLES30.glUniform4fv ( fogColorLoc, 1, fogColorBuffer );
         GLES30.glUniform1f ( timeLoc, mLastTime * 0.1f );
      }

      // Bind the 3D texture
      GLES30.glUniform1i ( noiseTexLoc, 0 );
      GLES30.glBindTexture ( GLES30.GL_TEXTURE_3D, textureId[0] );

      // Draw the cube
      GLES30.glDrawElements ( GLES30.GL_TRIANGLES, mCube.getNumIndices(), GLES30.GL_UNSIGNED_SHORT, mCube.getIndices() );
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
   private int mvpLoc;
   private int mvLoc;
   private int fogMinDistLoc;
   private int fogMaxDistLoc;
   private int fogColorLoc;
   private int noiseTexLoc;
   private int timeLoc;

   // Texture handle
   private final int [] textureId = new int[1];

   // Vertex data
   private final ESShapes mCube = new ESShapes();

   // Rotation angle
   private float mAngle;

   // Time
   float curTime;

   // MVP matrix
   private final ESTransform mvpMatrix = new ESTransform();
   private final ESTransform mvMatrix = new ESTransform();

   // lattice gradients 3D noise
   private final float [] gradientTable = new float[256 * 3];

   // permTable describes a random permutatin of 8-bit values from 0 to 255.
   private final short [] permTable = new short[]
      {
         0xE1, 0x9B, 0xD2, 0x6C, 0xAF, 0xC7, 0xDD, 0x90, 0xCB, 0x74, 0x46, 0xD5, 0x45, 0x9E, 0x21, 0xFC,
         0x05, 0x52, 0xAD, 0x85, 0xDE, 0x8B, 0xAE, 0x1B, 0x09, 0x47, 0x5A, 0xF6, 0x4B, 0x82, 0x5B, 0xBF,
         0xA9, 0x8A, 0x02, 0x97, 0xC2, 0xEB, 0x51, 0x07, 0x19, 0x71, 0xE4, 0x9F, 0xCD, 0xFD, 0x86, 0x8E,
         0xF8, 0x41, 0xE0, 0xD9, 0x16, 0x79, 0xE5, 0x3F, 0x59, 0x67, 0x60, 0x68, 0x9C, 0x11, 0xC9, 0x81,
         0x24, 0x08, 0xA5, 0x6E, 0xED, 0x75, 0xE7, 0x38, 0x84, 0xD3, 0x98, 0x14, 0xB5, 0x6F, 0xEF, 0xDA,
         0xAA, 0xA3, 0x33, 0xAC, 0x9D, 0x2F, 0x50, 0xD4, 0xB0, 0xFA, 0x57, 0x31, 0x63, 0xF2, 0x88, 0xBD,
         0xA2, 0x73, 0x2C, 0x2B, 0x7C, 0x5E, 0x96, 0x10, 0x8D, 0xF7, 0x20, 0x0A, 0xC6, 0xDF, 0xFF, 0x48,
         0x35, 0x83, 0x54, 0x39, 0xDC, 0xC5, 0x3A, 0x32, 0xD0, 0x0B, 0xF1, 0x1C, 0x03, 0xC0, 0x3E, 0xCA,
         0x12, 0xD7, 0x99, 0x18, 0x4C, 0x29, 0x0F, 0xB3, 0x27, 0x2E, 0x37, 0x06, 0x80, 0xA7, 0x17, 0xBC,
         0x6A, 0x22, 0xBB, 0x8C, 0xA4, 0x49, 0x70, 0xB6, 0xF4, 0xC3, 0xE3, 0x0D, 0x23, 0x4D, 0xC4, 0xB9,
         0x1A, 0xC8, 0xE2, 0x77, 0x1F, 0x7B, 0xA8, 0x7D, 0xF9, 0x44, 0xB7, 0xE6, 0xB1, 0x87, 0xA0, 0xB4,
         0x0C, 0x01, 0xF3, 0x94, 0x66, 0xA6, 0x26, 0xEE, 0xFB, 0x25, 0xF0, 0x7E, 0x40, 0x4A, 0xA1, 0x28,
         0xB8, 0x95, 0xAB, 0xB2, 0x65, 0x42, 0x1D, 0x3B, 0x92, 0x3D, 0xFE, 0x6B, 0x2A, 0x56, 0x9A, 0x04,
         0xEC, 0xE8, 0x78, 0x15, 0xE9, 0xD1, 0x2D, 0x62, 0xC1, 0x72, 0x4E, 0x13, 0xCE, 0x0E, 0x76, 0x7F,
         0x30, 0x4F, 0x93, 0x55, 0x1E, 0xCF, 0xDB, 0x36, 0x58, 0xEA, 0xBE, 0x7A, 0x5F, 0x43, 0x8F, 0x6D,
         0x89, 0xD6, 0x91, 0x5D, 0x5C, 0x64, 0xF5, 0x00, 0xD8, 0xBA, 0x3C, 0x53, 0x69, 0x61, 0xCC, 0x34,
      };

   // Additional member variables
   private int mWidth;
   private int mHeight;
   private long mLastTime = 0;

   final int ATTRIB_LOCATION_POS = 0;
   final int ATTRIB_LOCATION_COLOR = 1;
   final int ATTRIB_LOCATION_TEXCOORD = 2;
   final int NOISE_TABLE_MASK = 255;

   private final Context mContext;
}
