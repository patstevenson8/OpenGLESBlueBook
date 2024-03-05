package edu.gatech.gtri.geomipmap;

import static android.view.MotionEvent.INVALID_POINTER_ID;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;

public class GeoMipMapView extends GLSurfaceView
{
    private final int CONTEXT_CLIENT_VERSION = 3;

    public GeoMipMapView(Context context)
    {
        super(context);

        // Tell the surface view we want to create an OpenGL ES 3.0-compatible
        // context, and set an OpenGL ES 3.0-compatible renderer.
        setEGLContextClientVersion ( CONTEXT_CLIENT_VERSION );

        // Set the Renderer for drawing on the GLSurfaceView
        mRenderer = new GeoMipMapRenderer(getContext());
        setRenderer(mRenderer);

        // A scale detector is a convenient way to detect multiple finger gestures
        // like pinch with two fingers
        mScaleDetector = new ScaleGestureDetector(context, new ScaleListener());
    }

    @Override
    public boolean onTouchEvent(MotionEvent e)
    {
        // MotionEvent reports input details from the touch screen
        // and other input controls. In this case, you are only
        // interested in events where the touch position changed.

        // Let the ScaleGestureDetector inspect all events when we are in a
        // Pan/Zoom
        if(mMotionState == MotionState.PanZoom)
            mScaleDetector.onTouchEvent(e);

        switch(e.getActionMasked())
        {
            case MotionEvent.ACTION_DOWN:
            {
                final int pointerIndex = e.getActionIndex();
                final float x = e.getX(pointerIndex);
                final float y = e.getY(pointerIndex);

                // Remember where we started (for dragging)
                mPreviousX0 = x;
                mPreviousY0 = y;

                // Save the ID of this pointer (for dragging)
                mPrimaryPointerId = e.getPointerId(pointerIndex);

                break;
            }
            case MotionEvent.ACTION_POINTER_DOWN:
            {
                final int pointerIndex = e.getActionIndex();
                final float x = e.getX(pointerIndex);
                final float y = e.getY(pointerIndex);

                // Remember where we started (for dragging)
                mPreviousX1 = x;
                mPreviousY1 = y;

                // Save the ID of this pointer (for dragging)
                mSecondaryPointerId = e.getPointerId(pointerIndex);

                break;
            }
            case MotionEvent.ACTION_MOVE:
            {
                // The screen touch has turned into a drag. If no motion state
                // has been computed, determine the allowed motion
                // interpretation based on the number of pointers used.
                if(mMotionState == MotionState.None)
                {
                    if(e.getPointerCount() > 1)
                        mMotionState = MotionState.PanZoom;
                    else
                        mMotionState = MotionState.Rotate;
                }

                // Check on how many pointers are involved
                if(e.getPointerCount() > 1)
                {
                    // Promote the motion state to Pan/Zoom.
                    if(mMotionState == MotionState.Rotate)
                        mMotionState = MotionState.PanZoom;

                    // Find the indices of pointers and fetch positions
                    final int primaryPointerIndex = e.findPointerIndex(mPrimaryPointerId);
                    final int secondaryPointerIndex = e.findPointerIndex(mSecondaryPointerId);

                    if(primaryPointerIndex != -1 && secondaryPointerIndex != -1)
                    {
                        // Multiple fingers moving
                        final float x0 = e.getX(primaryPointerIndex);
                        final float y0 = e.getY(primaryPointerIndex);
                        final float x1 = e.getX(secondaryPointerIndex);
                        final float y1 = e.getY(secondaryPointerIndex);

                        // Calculate the distance moved
                        final float dx0 = x0 - mPreviousX0;
                        final float dy0 = y0 - mPreviousY0;
                        final float dx1 = x1 - mPreviousX1;
                        final float dy1 = y1 - mPreviousY1;

                        invalidate();

                        // Remember these touch positions for the next move event
                        mPreviousX0 = x0;
                        mPreviousY0 = y0;
                        mPreviousX1 = x1;
                        mPreviousY1 = y1;

                        // Determine if a vertical or horizontal drag is
                        // occuring. If both pointers are moving in the same
                        // relative direction, the product of the deltas will be
                        // positive.
                        final boolean isDragVertical = (dy1*dy0) > 0;
                        final boolean isDragHorizontal = (dx1*dx0) > 0;

                        // Test for drag by checking direction of moves
                        //if(false)
                        if(isDragVertical || isDragHorizontal)
                        {
                            // Determine the drag values that will be sent in to
                            // the renderer to interpret and effect the drag on
                            // the scene.  If a drag along the axes occured,
                            // select the greatest value.
                            final float dragDx;
                            if(isDragHorizontal)
                            {
                                // Determine the maximum dx value based on the
                                // absolute values, then multiply by the result
                                // of signum to obtain the correctly signed
                                //value
                                dragDx = Math.signum(dx0)*
                                        Math.max(Math.abs(dx0), Math.abs(dx1));
                            }
                            else
                            {
                                dragDx = 0;
                            }

                            final float dragDy;
                            if(isDragVertical)
                            {
                                // Determine the maximum dx value based on the
                                // absolute values, then multiply by the result
                                // of signum to obtain the correctly signed
                                //value
                                dragDy = Math.signum(dy0)*
                                        Math.max(Math.abs(dy0), Math.abs(dy1));
                            }
                            else
                            {
                                dragDy = 0;
                            }

                            // Send the maximum deltas into the renderer which
                            // will interpret and effect the drag on the scene.
                            queueEvent(new Runnable() {
                                public void run() {
                                    mRenderer.multipleFingersDrag(dragDx, dragDy);
                                }
                            });
                        }
                    }
                }
                else if(mMotionState == MotionState.Rotate)
                {
                    // Find the index of primary pointer and fetch position
                    final int primaryPointerIndex = e.findPointerIndex(mPrimaryPointerId);

                    if(primaryPointerIndex != -1)
                    {
                        final float x = e.getX(primaryPointerIndex);
                        final float y = e.getY(primaryPointerIndex);

                        // Calculate the distance moved
                        final float dx = x - mPreviousX0;
                        final float dy = y - mPreviousY0;

                        invalidate();

                        // Remember this touch position for the next move event
                        mPreviousX0 = x;
                        mPreviousY0 = y;

                        // Single finger moving
                        queueEvent(new Runnable() {
                            @Override
                            public void run() {
                                mRenderer.singleFingerDrag(dx, dy);
                            }
                        });
                    }
                }
                requestRender();
                break;
            }
            case MotionEvent.ACTION_UP:
            {
                mPrimaryPointerId = INVALID_POINTER_ID;
                mPreviousX0 = -32768;
                mPreviousY0 = -32768;

                // The screen touch has ended, reset the motion state to None.
                mMotionState = MotionState.None;
                break;
            }
            case MotionEvent.ACTION_POINTER_UP:
            {
                final int pointerIndex = e.getActionIndex();
                final int pointerId = e.getPointerId(pointerIndex);

                if(pointerId == mPrimaryPointerId)
                {
                    // This was our primary pointer going up. Choose a new
                    // primary pointer and adjust accordingly.
                    final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                    mPreviousX0 = e.getX(newPointerIndex);
                    mPreviousY0 = e.getY(newPointerIndex);
                    mPrimaryPointerId = e.getPointerId(newPointerIndex);

                    // With a new primary pointer, need to reset the secondary
                    mSecondaryPointerId = INVALID_POINTER_ID;
                    mPreviousX1 = -32768;
                    mPreviousY1 = -32768;
                }

                break;
            }
            case MotionEvent.ACTION_CANCEL:
            {
                mPrimaryPointerId = INVALID_POINTER_ID;
                mSecondaryPointerId = INVALID_POINTER_ID;
                break;
            }
        }
        return true;
    }

    private class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener
    {
        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector)
        {
            return true;
        }

        @Override
        public boolean onScale(ScaleGestureDetector detector)
        {
            // Effect the relative scale on the renderer
            final float relativeSceleFactor = detector.getScaleFactor();
            queueEvent(new Runnable() {
                @Override
                public void run() {
                    mRenderer.zoomCamera(relativeSceleFactor);
                }
            });

            // Invalidate the drawing area so render will happen
            invalidate();
            return true;

        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector)
        {
        }
    }

    // Enumeration of allowed motions
    private enum MotionState
    {
        None,
        Rotate,
        PanZoom
    }

    // For touches and gestures
    private final float TOUCH_SCALE_FACTOR = 180.0f / 320;
    private float mPreviousX0 = -32768;
    private float mPreviousY0 = -32768;
    private float mPreviousX1 = -32768;
    private float mPreviousY1 = -32768;

    // We have a primary and possibly secondary pointers during touches
    private int mPrimaryPointerId = INVALID_POINTER_ID;
    private int mSecondaryPointerId = INVALID_POINTER_ID;

    // For pinching and spreading use scale detector
    private ScaleGestureDetector mScaleDetector;

    // The current allowed motion
    private MotionState mMotionState = MotionState.None;

    private final GeoMipMapRenderer mRenderer;
}
