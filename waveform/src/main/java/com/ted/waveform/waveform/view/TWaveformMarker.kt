package com.ted.waveform.waveform.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Rect
import android.support.v7.widget.AppCompatImageView
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.MotionEvent

open class TWaveformMarker(context: Context, attrs: AttributeSet) : AppCompatImageView(context, attrs) {

    private var mVelocity: Int = 0
    private var mListener: MarkerListener? = null

    init {
        // Make sure we get keys
        isFocusable = true

        mVelocity = 0
        mListener = null
    }

    fun setListener(listener: MarkerListener) {
        mListener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                requestFocus()
                // We use raw x because this window itself is going to
                // move, which will screw up the "local" coordinates
                mListener!!.markerTouchStart(this, event.rawX)
            }
            MotionEvent.ACTION_MOVE ->
                // We use raw x because this window itself is going to
                // move, which will screw up the "local" coordinates
                mListener!!.markerTouchMove(this, event.rawX)
            MotionEvent.ACTION_UP -> mListener!!.markerTouchEnd(this)
        }
        return true
    }

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        if (gainFocus && mListener != null)
            mListener!!.markerFocus(this)
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (mListener != null)
            mListener!!.markerDraw()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        mVelocity++
        val v = Math.sqrt((1 + mVelocity / 2).toDouble()).toInt()
        if (mListener != null) {
            when (keyCode) {
                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    mListener!!.markerLeft(this, v)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    mListener!!.markerRight(this, v)
                    return true
                }
                KeyEvent.KEYCODE_DPAD_CENTER -> {
                    mListener!!.markerEnter(this)
                    return true
                }
            }
        }

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        mVelocity = 0
        if (mListener != null)
            mListener!!.markerKeyUp()
        return super.onKeyDown(keyCode, event)
    }


    /**----------------------------------------------------
     * Listener
     *----------------------------------------------------*/
    interface MarkerListener {
        fun markerTouchStart(TWaveformMarker: TWaveformMarker, pos: Float)
        fun markerTouchMove(TWaveformMarker: TWaveformMarker, pos: Float)
        fun markerTouchEnd(TWaveformMarker: TWaveformMarker)
        fun markerFocus(TWaveformMarker: TWaveformMarker)
        fun markerLeft(TWaveformMarker: TWaveformMarker, velocity: Int)
        fun markerRight(TWaveformMarker: TWaveformMarker, velocity: Int)
        fun markerEnter(TWaveformMarker: TWaveformMarker)
        fun markerKeyUp()
        fun markerDraw()
    }
}