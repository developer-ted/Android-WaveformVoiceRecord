package com.ted.waveform.waveform.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.support.v4.content.ContextCompat
import android.util.AttributeSet
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import com.ted.waveform.waveform.R
import com.ted.waveform.waveform.file.SoundFile

open class TWaveformView(context: Context, attrs: AttributeSet) : View(context, attrs) {

    // Colors
    var mGridPaint: Paint = Paint(ContextCompat.getColor(context, R.color.grid_line))
    var mSelectedLinePaint: Paint = Paint(ContextCompat.getColor(context,
        R.color.waveform_selected
    ))
    var mUnselectedLinePaint: Paint = Paint(ContextCompat.getColor(context,
        R.color.waveform_unselected
    ))
    var mUnselectedBkgndLinePaint: Paint =
        Paint(ContextCompat.getColor(context, R.color.waveform_unselected_bkgnd_overlay))
    var mBorderLinePaint: Paint = Paint(ContextCompat.getColor(context,
        R.color.selection_border
    ))
    var mPlaybackLinePaint: Paint = Paint(ContextCompat.getColor(context,
        R.color.playback_indicator
    ))
    var mTimecodePaint: Paint = Paint(ContextCompat.getColor(context, R.color.timecode_shadow))

    private var mSoundFile: SoundFile? = null
    private var mLenByZoomLevel: IntArray? = null
    private var mValuesByZoomLevel: Array<DoubleArray?>? = null
    private var mZoomFactorByZoomLevel: DoubleArray? = null
    private var mHeightsAtThisZoomLevel: IntArray? = null
    private var mZoomLevel: Int = 0
    private var mNumZoomLevels: Int = 0
    private var mSampleRate: Int = 0
    private var mSamplesPerFrame: Int = 0
    private var mOffset: Int = 0
    private var mSelectionStart: Int = 0
    private var mSelectionEnd: Int = 0
    private var mPlaybackPos: Int = 0
    private var mDensity: Float = 0.toFloat()
    private var mInitialScaleSpan: Float = 0.toFloat()
    private var mListener: WaveformListener? = null
    private val mGestureDetector: GestureDetector
    private val mScaleGestureDetector: ScaleGestureDetector
    private var mInitialized: Boolean = false


    /**----------------------------------------------------
     * Initialize
     *----------------------------------------------------*/
    init {
        // We don't want keys, the markers get these
        isFocusable = false

        // init paints
        mGridPaint.isAntiAlias = false
        mSelectedLinePaint.isAntiAlias = false
        mUnselectedLinePaint.isAntiAlias = false
        mUnselectedBkgndLinePaint.isAntiAlias = false
        mBorderLinePaint.isAntiAlias = true
        mBorderLinePaint.strokeWidth = 1.5f
        mBorderLinePaint.pathEffect = DashPathEffect(floatArrayOf(3.0f, 2.0f), 0.0f)
        mPlaybackLinePaint.isAntiAlias = false
        mTimecodePaint.textSize = 12f
        mTimecodePaint.isAntiAlias = true
        mTimecodePaint.setShadowLayer(2f, 1f, 1f, ContextCompat.getColor(context,
            R.color.timecode_shadow
        ))

        // 변수 초기화
        mSoundFile = null
        mLenByZoomLevel = null
        mValuesByZoomLevel = null
        mHeightsAtThisZoomLevel = null
        mOffset = 0
        mPlaybackPos = -1
        mSelectionStart = 0
        mSelectionEnd = 0
        mDensity = 1.0f
        mInitialized = false


        // 제스쳐 이벤트
        mGestureDetector = GestureDetector(
            context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(e1: MotionEvent, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                    mListener?.waveformFling(vx)
                    return true
                }
            }
        )

        // Scale 제스쳐 이벤트
        mScaleGestureDetector = ScaleGestureDetector(
            context,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                    Log.v("WaveformView", "ScaleBegin: " + d.currentSpanX)
                    mInitialScaleSpan = Math.abs(d.currentSpanX)
                    return true
                }

                override fun onScale(d: ScaleGestureDetector): Boolean {
                    val scale = Math.abs(d.currentSpanX)
                    Log.v("WaveformView", "Scale: " + (scale - mInitialScaleSpan))
                    if (scale - mInitialScaleSpan > 40) {
                        mListener?.waveformZoomIn()
                        mInitialScaleSpan = scale
                    }
                    if (scale - mInitialScaleSpan < -40) {
                        mListener?.waveformZoomOut()
                        mInitialScaleSpan = scale
                    }
                    return true
                }

                override fun onScaleEnd(d: ScaleGestureDetector) {
                    Log.v("WaveformView", "ScaleEnd: " + d.currentSpanX)
                }
            }
        )
    }


    /**----------------------------------------------------
     * On Draw
     *----------------------------------------------------*/
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (mSoundFile == null)
            return

        if (mHeightsAtThisZoomLevel == null)
            computeIntsForThisZoomLevel()

        // Draw waveform
        val measuredWidth = measuredWidth
        val measuredHeight = measuredHeight
        val start = mOffset
        var width = mHeightsAtThisZoomLevel!!.size - start
        val ctr = measuredHeight / 2

        if (width > measuredWidth)
            width = measuredWidth

        // Draw grid
        val onePixelInSecs = pixelsToSeconds(1)
        val onlyEveryFiveSecs = onePixelInSecs > 1.0 / 50.0
        var fractionalSecs = mOffset * onePixelInSecs
        var integerSecs = fractionalSecs.toInt()
        var i = 0
        while (i < width) {
            i++
            fractionalSecs += onePixelInSecs
            val integerSecsNew = fractionalSecs.toInt()
            if (integerSecsNew != integerSecs) {
                integerSecs = integerSecsNew
                if (!onlyEveryFiveSecs || 0 == integerSecs % 5) {
                    canvas.drawLine(i.toFloat(), 0f, i.toFloat(), measuredHeight.toFloat(), mGridPaint)
                }
            }
        }

        // Draw waveform
        i = 0
        while (i < width) {
            val paint: Paint = if (i + start in mSelectionStart until mSelectionEnd) {
                mSelectedLinePaint
            } else {
                drawWaveformLine(canvas, i, 0, measuredHeight, mUnselectedBkgndLinePaint)
                mUnselectedLinePaint
            }
            drawWaveformLine(canvas, i, ctr - mHeightsAtThisZoomLevel!![start + i],
                ctr + 1 + mHeightsAtThisZoomLevel!![start + i], paint)
            if (i + start == mPlaybackPos) {
                canvas.drawLine(i.toFloat(), 0f, i.toFloat(), measuredHeight.toFloat(), mPlaybackLinePaint)
            }
            i++
        }

        // If we can see the right edge of the waveform, draw the
        // non-waveform area to the right as unselected
        i = width
        while (i < measuredWidth) {
            drawWaveformLine(
                canvas, i, 0, measuredHeight,
                mUnselectedBkgndLinePaint
            )
            i++
        }

        // Draw borders
        canvas.drawLine(
            mSelectionStart - mOffset + 0.5f, 30f,
            mSelectionStart - mOffset + 0.5f, measuredHeight.toFloat(),
            mBorderLinePaint
        )
        canvas.drawLine(
            mSelectionEnd - mOffset + 0.5f, 0f,
            mSelectionEnd - mOffset + 0.5f, (measuredHeight - 30).toFloat(),
            mBorderLinePaint
        )

        // Draw timecode
        var timecodeIntervalSecs = 1.0
        if (timecodeIntervalSecs / onePixelInSecs < 50) {
            timecodeIntervalSecs = 5.0
        }
        if (timecodeIntervalSecs / onePixelInSecs < 50) {
            timecodeIntervalSecs = 15.0
        }

        // Draw grid
        fractionalSecs = mOffset * onePixelInSecs
        var integerTimeCode = (fractionalSecs / timecodeIntervalSecs).toInt()
        i = 0
        while (i < width) {
            i++
            fractionalSecs += onePixelInSecs
            integerSecs = fractionalSecs.toInt()
            val integerTimecodeNew = (fractionalSecs / timecodeIntervalSecs).toInt()
            if (integerTimecodeNew != integerTimeCode) {
                integerTimeCode = integerTimecodeNew

                // Turn, e.g. 67 seconds into "1:07"
                val timeCodeMinutes = "" + integerSecs / 60
                var timeCodeSeconds = "" + integerSecs % 60
                if (integerSecs % 60 < 10) {
                    timeCodeSeconds = "0$timeCodeSeconds"
                }
                val timeCodeStr = "$timeCodeMinutes:$timeCodeSeconds"
                val offset = (0.5 * mTimecodePaint.measureText(timeCodeStr)).toFloat()
                canvas.drawText(
                    timeCodeStr,
                    i - offset,
                    ((12 * mDensity).toInt()).toFloat(),
                    mTimecodePaint
                )
            }
        }

        mListener?.waveformDraw()
    }


    /**----------------------------------------------------
     * Touch event
     *----------------------------------------------------*/
    override fun onTouchEvent(event: MotionEvent): Boolean {
        mScaleGestureDetector.onTouchEvent(event)
        if (mGestureDetector.onTouchEvent(event)) {
            return true
        }

        when (event.action) {
            MotionEvent.ACTION_DOWN -> mListener?.waveformTouchStart(event.x)
            MotionEvent.ACTION_MOVE -> mListener?.waveformTouchMove(event.x)
            MotionEvent.ACTION_UP -> mListener?.waveformTouchEnd()
        }
        return true
    }


    /**----------------------------------------------------
     * Sound file
     *----------------------------------------------------*/
    fun hasSoundFile(): Boolean {
        return mSoundFile != null
    }

    fun setSoundFile(soundFile: SoundFile?) {
        mSoundFile = soundFile
        if (mSoundFile != null) {
            mSampleRate = mSoundFile!!.sampleRate
            mSamplesPerFrame = mSoundFile!!.samplesPerFrame
            computeDoublesForAllZoomLevels()
            mHeightsAtThisZoomLevel = null
        }
    }


    /**----------------------------------------------------
     * Time
     *----------------------------------------------------*/
    fun secondsToFrames(seconds: Double): Int {
        return (1.0 * seconds * mSampleRate / mSamplesPerFrame + 0.5) as Int
    }

    fun secondsToPixels(seconds: Double): Int {
        val z = mZoomFactorByZoomLevel!![mZoomLevel]
        return (z * seconds * mSampleRate / mSamplesPerFrame + 0.5).toInt()
    }

    fun pixelsToSeconds(pixels: Int): Double {
        val z = mZoomFactorByZoomLevel!![mZoomLevel]
        return pixels * mSamplesPerFrame.toDouble() / (mSampleRate * z)
    }

    fun millisecsToPixels(msecs: Int): Int {
        val z = mZoomFactorByZoomLevel!![mZoomLevel]
        return (msecs * 1.0 * mSampleRate * z / (1000.0 * mSamplesPerFrame) + 0.5).toInt()
    }

    fun pixelsToMillisecs(pixels: Int): Int {
        val z = mZoomFactorByZoomLevel!![mZoomLevel]
        return (pixels * (1000.0 * mSamplesPerFrame) / (mSampleRate * z) + 0.5).toInt()
    }

    fun setParameters(start: Int, end: Int, offset: Int) {
        mSelectionStart = start
        mSelectionEnd = end
        mOffset = offset
    }


    /**----------------------------------------------------
     * Zoom
     *----------------------------------------------------*/
    fun getZoomLevel(): Int {
        return mZoomLevel
    }

    fun setZoomLevel(zoomLevel: Int) {
        while (mZoomLevel > zoomLevel) {
            zoomIn()
        }
        while (mZoomLevel < zoomLevel) {
            zoomOut()
        }
    }

    fun canZoomIn(): Boolean {
        return mZoomLevel > 0
    }

    fun zoomIn() {
        if (canZoomIn()) {
            mZoomLevel--
            mSelectionStart *= 2
            mSelectionEnd *= 2
            mHeightsAtThisZoomLevel = null
            var offsetCenter = mOffset + measuredWidth / 2
            offsetCenter *= 2
            mOffset = offsetCenter - measuredWidth / 2
            if (mOffset < 0)
                mOffset = 0
            invalidate()
        }
    }

    fun canZoomOut(): Boolean {
        return mZoomLevel < mNumZoomLevels - 1
    }

    fun zoomOut() {
        if (canZoomOut()) {
            mZoomLevel++
            mSelectionStart /= 2
            mSelectionEnd /= 2
            var offsetCenter = mOffset + measuredWidth / 2
            offsetCenter /= 2
            mOffset = offsetCenter - measuredWidth / 2
            if (mOffset < 0)
                mOffset = 0
            mHeightsAtThisZoomLevel = null
            invalidate()
        }
    }

    fun maxPos(): Int {
        return mLenByZoomLevel!![mZoomLevel]
    }


    /**
     * Called once when a new sound file is added
     */
    private fun computeDoublesForAllZoomLevels() {
        val numFrames = mSoundFile?.numFrames
        val frameGains = mSoundFile?.frameGains
        val smoothedGains = DoubleArray(numFrames!!)
        if (frameGains != null) {
            when {
                numFrames == 1 -> smoothedGains[0] = frameGains[0].toDouble()
                numFrames == 2 -> {
                    smoothedGains[0] = frameGains[0].toDouble()
                    smoothedGains[1] = frameGains[1].toDouble()
                }
                numFrames > 2 -> {
                    smoothedGains[0] = frameGains[0] / 2.0 + frameGains[1] / 2.0
                    for (i in 1 until numFrames - 1) {
                        smoothedGains[i] = frameGains[i - 1] / 3.0 +
                                frameGains[i] / 3.0 +
                                frameGains[i + 1] / 3.0
                    }
                    smoothedGains[numFrames - 1] = frameGains[numFrames - 2] / 2.0 + frameGains[numFrames - 1] / 2.0
                }
            }
        }

        // Make sure the range is no more than 0 - 255
        var maxGain = 1.0
        for (i in 0 until numFrames) {
            if (smoothedGains[i] > maxGain) {
                maxGain = smoothedGains[i]
            }
        }
        var scaleFactor = 1.0
        if (maxGain > 255.0) {
            scaleFactor = 255 / maxGain
        }

        // Build histogram of 256 bins and figure out the new scaled max
        maxGain = 0.0
        val gainHist = IntArray(256)
        for (i in 0 until numFrames) {
            var smoothedGain = (smoothedGains[i] * scaleFactor).toInt()
            if (smoothedGain < 0)
                smoothedGain = 0
            if (smoothedGain > 255)
                smoothedGain = 255

            if (smoothedGain > maxGain)
                maxGain = smoothedGain.toDouble()

            gainHist[smoothedGain]++
        }

        // Re-calibrate the min to be 5%
        var minGain = 0.0
        var sum = 0
        while (minGain < 255 && sum < numFrames / 20) {
            sum += gainHist[minGain.toInt()]
            minGain++
        }

        // Re-calibrate the max to be 99%
        sum = 0
        while (maxGain > 2 && sum < numFrames / 100) {
            sum += gainHist[maxGain.toInt()]
            maxGain--
        }

        // Compute the heights
        val heights = DoubleArray(numFrames)
        val range = maxGain - minGain
        for (i in 0 until numFrames) {
            var value = (smoothedGains[i] * scaleFactor - minGain) / range
            if (value < 0.0)
                value = 0.0
            if (value > 1.0)
                value = 1.0
            heights[i] = value * value
        }

        mNumZoomLevels = 5
        mLenByZoomLevel = IntArray(5)
        mZoomFactorByZoomLevel = DoubleArray(5)
        mValuesByZoomLevel = arrayOfNulls(5)

        // Level 0 is doubled, with interpolated values
        mLenByZoomLevel!![0] = numFrames * 2
        mZoomFactorByZoomLevel!![0] = 2.0
        mValuesByZoomLevel!![0] = DoubleArray(mLenByZoomLevel!![0])
        if (numFrames > 0) {
            mValuesByZoomLevel!![0]?.set(0, 0.5 * heights[0])
            mValuesByZoomLevel!![0]?.set(1, heights[0])
        }
        for (i in 1 until numFrames) {
            mValuesByZoomLevel!![0]?.set(2 * i, 0.5 * (heights[i - 1] + heights[i]))
            mValuesByZoomLevel!![0]?.set(2 * i + 1, heights[i])
        }

        // Level 1 is normal
        mLenByZoomLevel!![1] = numFrames
        mValuesByZoomLevel!![1] = DoubleArray(mLenByZoomLevel!![1])
        mZoomFactorByZoomLevel!![1] = 1.0
        for (i in 0 until mLenByZoomLevel!![1]) {
            mValuesByZoomLevel!![1]?.set(i, heights[i])
        }

        // 3 more levels are each halved
        for (j in 2..4) {
            mLenByZoomLevel!![j] = mLenByZoomLevel!![j - 1] / 2
            mValuesByZoomLevel!![j] = DoubleArray(mLenByZoomLevel!![j])
            mZoomFactorByZoomLevel!![j] = mZoomFactorByZoomLevel!![j - 1] / 2.0
            for (i in 0 until mLenByZoomLevel!![j]) {
                mValuesByZoomLevel!![j]?.set(
                    i,
                    0.5 * (mValuesByZoomLevel!![j - 1]!![2 * i] + mValuesByZoomLevel!![j - 1]!![2 * i + 1])
                )
            }
        }

        mZoomLevel = when {
            numFrames > 5000 -> 3
            numFrames > 1000 -> 2
            numFrames > 300 -> 1
            else -> 0
        }

        mInitialized = true
    }

    /**
     * Called the first time we need to draw when the zoom level has changed
     * or the screen is resized
     */
    private fun computeIntsForThisZoomLevel() {
        val halfHeight = measuredHeight / 2 - 1
        mHeightsAtThisZoomLevel = mLenByZoomLevel?.get(mZoomLevel)?.let { IntArray(it) }
        for (i in 0 until mLenByZoomLevel!![mZoomLevel]) {
            mHeightsAtThisZoomLevel?.set(i, (mValuesByZoomLevel?.get(mZoomLevel)!![i] * halfHeight).toInt())
        }
    }


    /**----------------------------------------------------
     * Get / Set
     *----------------------------------------------------*/
    fun isInitialized(): Boolean {
        return mInitialized
    }

    fun getStart(): Int {
        return mSelectionStart
    }

    fun getEnd(): Int {
        return mSelectionEnd
    }

    fun getOffset(): Int {
        return mOffset
    }

    fun recomputeHeights(density: Float) {
        mHeightsAtThisZoomLevel = null
        mDensity = density
        mTimecodePaint.textSize = (12 * density).toInt().toFloat()

        invalidate()
    }

    protected fun drawWaveformLine(canvas: Canvas, x: Int, y0: Int, y1: Int, paint: Paint) {
        canvas.drawLine(x.toFloat(), y0.toFloat(), x.toFloat(), y1.toFloat(), paint)
    }

    fun setPlayback(pos: Int) {
        mPlaybackPos = pos
    }

    fun setListener(listener: WaveformListener) {
        mListener = listener
    }


    /**----------------------------------------------------
     * Listener
     *----------------------------------------------------*/
    interface WaveformListener {
        fun waveformTouchStart(x: Float)
        fun waveformTouchMove(x: Float)
        fun waveformTouchEnd()
        fun waveformFling(x: Float)
        fun waveformDraw()
        fun waveformZoomIn()
        fun waveformZoomOut()
    }
}