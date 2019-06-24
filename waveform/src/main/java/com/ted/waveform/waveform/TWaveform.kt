package com.ted.waveform.waveform

import android.app.Activity
import android.app.AlertDialog
import android.app.ProgressDialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Handler
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.util.DisplayMetrics
import android.util.Log
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.RelativeLayout
import com.ted.waveform.waveform.file.SongMetadataReader
import com.ted.waveform.waveform.file.SoundFile
import com.ted.waveform.waveform.player.SamplePlayer
import com.ted.waveform.waveform.view.TWaveformMarker
import com.ted.waveform.waveform.view.TWaveformView
import kotlinx.android.synthetic.main.view_t_wave_form.view.*
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

//TWaveformView.WaveformListener
class TWaveform(context: Context, attrs: AttributeSet) : FrameLayout(context, attrs),
    TWaveformMarker.MarkerListener {

    private var mLoadingLastUpdateTime: Long = 0
    private var mLoadingKeepGoing: Boolean = false
    private var mRecordingLastUpdateTime: Long = 0
    private var mRecordingKeepGoing: Boolean = false
    private var mRecordingTime: Double = 0.toDouble()
    private var mFinishActivity: Boolean = false
    private var mAlertDialog: AlertDialog? = null
    private var mProgressDialog: ProgressDialog? = null
    private var mSoundFile: SoundFile? = null
    private var mFile: File? = null
    private var mFilename: String? = ""
    private var mArtist: String? = ""
    private var mTitle: String? = ""
    private var mNewFileKind: Int = 0
    private var mWasGetContentIntent: Boolean = false
    private var mInfoContent: String? = ""
    private var mKeyDown: Boolean = false
    private var mCaption = ""
    private var mWidth: Int = 0
    private var mMaxPos: Int = 0
    private var mStartPos: Int = 0
    private var mEndPos: Int = 0
    private var mStartVisible: Boolean = false
    private var mEndVisible: Boolean = false
    private var mLastDisplayedStartPos: Int = 0
    private var mLastDisplayedEndPos: Int = 0
    private var mOffset: Int = 0
    private var mOffsetGoal: Int = 0
    private var mFlingVelocity: Int = 0
    private var mPlayStartMsec: Int = 0
    private var mPlayEndMsec: Int = 0
    private var mHandler: Handler? = null
    private var mIsPlaying: Boolean = false
    private var mPlayer: SamplePlayer? = null
    private var mTouchDragging: Boolean = false
    private var mTouchStart: Float = 0.toFloat()
    private var mTouchInitialOffset: Int = 0
    private var mTouchInitialStartPos: Int = 0
    private var mTouchInitialEndPos: Int = 0
    private var mWaveformTouchStartMsec: Long = 0
    private var mDensity: Float = 0.toFloat()
    private var mMarkerLeftInset: Int = 0
    private var mMarkerRightInset: Int = 0
    private var mMarkerTopOffset: Int = 0
    private var mMarkerBottomOffset: Int = 0

    private var mLoadSoundFileThread: Thread? = null
    private var mRecordAudioThread: Thread? = null
    private var mSaveSoundFileThread: Thread? = null

    // Result codes
    private val REQUEST_CODE_CHOOSE_CONTACT = 1


    /**----------------------------------------------------
     * Initialize
     *----------------------------------------------------*/
    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_t_wave_form, this, true)

    }

    fun constructor() {
        mPlayer = null
        mIsPlaying = false

        mAlertDialog = null
        mProgressDialog = null

        mLoadSoundFileThread = null
        mRecordAudioThread = null
        mSaveSoundFileThread = null

//        val intent = intent

        // If the Ringdroid media select activity was launched via a
        // GET_CONTENT intent, then we shouldn't display a "saved"
        // message when the user saves, we should just return whatever
        // they create.
//        mWasGetContentIntent = intent.getBooleanExtra("was_get_content_intent", false)

//        mFilename = intent.data!!.toString().replaceFirst("file://".toRegex(), "").replace("%20".toRegex(), " ")
        mFilename = "record"
        mSoundFile = null
        mKeyDown = false

        mHandler = Handler()

        loadGui()

        mHandler!!.postDelayed(mTimerRunnable, 100)

        if (mFilename != "record") {
            loadFromFile()
        } else {
            recordAudio()
        }
    }





    /**----------------------------------------------------
     * File
     *----------------------------------------------------*/
    private fun loadFromFile() {
        mFile = File(mFilename)

        val metadataReader = SongMetadataReader(context, mFilename)
//        val metadataReader = SongMetadataReader(
//            this@TWaveform, mFilename
//        )
        mTitle = metadataReader.mTitle
        mArtist = metadataReader.mArtist

        var titleLabel = mTitle
        if (mArtist != null && mArtist!!.isNotEmpty()) {
            titleLabel += " - $mArtist"
        }
        // FIXME Activity Title
//        title = titleLabel

        mLoadingLastUpdateTime = getCurrentTime()
        mLoadingKeepGoing = true
        mFinishActivity = false
        mProgressDialog = ProgressDialog(context)
        mProgressDialog!!.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL)
        mProgressDialog!!.setTitle("Loading…")
        mProgressDialog!!.setCancelable(true)
        mProgressDialog!!.setOnCancelListener {
            mLoadingKeepGoing = false
            mFinishActivity = true
        }
        mProgressDialog!!.show()

        val listener = SoundFile.ProgressListener { fractionComplete ->
            val now = getCurrentTime()
            if (now - mLoadingLastUpdateTime > 100) {
                mProgressDialog!!.progress = (mProgressDialog!!.getMax() * fractionComplete).toInt()
                mLoadingLastUpdateTime = now
            }
            mLoadingKeepGoing
        }

        // Load the sound file in a background thread
        mLoadSoundFileThread = object : Thread() {
            override fun run() {
                try {
                    mSoundFile = SoundFile.create(mFile!!.absolutePath, listener)

                    if (mSoundFile == null) {
                        mProgressDialog!!.dismiss()
                        val name = mFile!!.getName().toLowerCase()
                        val components = name.split(("\\.").toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        val err: String
                        err = if (components.size < 2) {
                            "Sorry, Ringdroid can\\'t edit a file if it doesn\\'t have an extension like .mp3 or .wav"
                        } else {
                            "Sorry, Ringdroid is not yet able to edit files of type " + components[components.size - 1]
                        }
                        val runnable = Runnable { showFinalAlert(Exception(), err) }
                        mHandler?.post(runnable)
                        return
                    }
                    mPlayer = SamplePlayer(mSoundFile)
                } catch (e: Exception) {
                    mProgressDialog!!.dismiss()
                    e.printStackTrace()
                    mInfoContent = e.toString()
                    // FIXME
                    info.text = mInfoContent
//                    context.runOnUiThread { info.setText(mInfoContent) }

                    val runnable = Runnable { showFinalAlert(e, "Error reading file") }
                    mHandler?.post(runnable)
                    return
                }

                mProgressDialog!!.dismiss()
                if (mLoadingKeepGoing) {
                    val runnable = Runnable { finishOpeningSoundFile() }
                    mHandler?.post(runnable)
                } else if (mFinishActivity) {
                    // FIXME 앱 종료
//                    this@TWaveform.finish()
                }
            }
        }
        mLoadSoundFileThread?.start()
    }

    private fun recordAudio() {
        mFile = null
        mTitle = null
        mArtist = null

        mRecordingLastUpdateTime = getCurrentTime()
        mRecordingKeepGoing = true
        mFinishActivity = false

        // FIXME
        val adBuilder = AlertDialog.Builder(context)
        adBuilder.setTitle("Recording…")
        adBuilder.setCancelable(true)
        adBuilder.setNegativeButton("Cancel") { _, _ ->
            mRecordingKeepGoing = false
            mFinishActivity = true
        }
        adBuilder.setPositiveButton("Stop") { _, _ ->
            mRecordingKeepGoing = false
        }

        // TODO(nfaralli): try to use a FrameLayout and pass it to the following inflate call.
        // Using null, android:layout_width etc. may not work (hence text is at the top of view).
        // On the other hand, if the text is big enough, this is good enough.
        
        // FIXME record_audio
//        adBuilder.setView(layoutInflater.inflate(R.layout.record_audio, null))
        mAlertDialog = adBuilder.show()

        val listener = SoundFile.ProgressListener { elapsedTime ->
            val now = getCurrentTime()
            if (now - mRecordingLastUpdateTime > 5) {
                mRecordingTime = elapsedTime
                // FIXME Only UI thread can update Views such as TextViews.
//                runOnUiThread {
//                    val min = (mRecordingTime / 60).toInt()
//                    val sec = (mRecordingTime - 60 * min).toFloat()
//                    // FIXME
////                    record_audio_timer.text = String.format("%d:%05.2f", min, sec)
//                }
                mRecordingLastUpdateTime = now
            }
            mRecordingKeepGoing
        }

        // Record the audio stream in a background thread
        mRecordAudioThread = object : Thread() {
            override fun run() {
                try {
                    mSoundFile = SoundFile.record(listener)
                    if (mSoundFile == null) {
                        this@TWaveform.mAlertDialog?.dismiss()
                        val runnable = Runnable {
                            showFinalAlert(Exception(), "Error recording audio")
                        }
                        mHandler?.post(runnable)
                        return
                    }
                    mPlayer = SamplePlayer(mSoundFile)
                } catch (e: Exception) {
                    this@TWaveform.mAlertDialog?.dismiss()
                    e.printStackTrace()
                    mInfoContent = e.toString()
                    // FIXME
//                    runOnUiThread { info.text = mInfoContent }
                    info.text = mInfoContent

                    val runnable = Runnable { showFinalAlert(e, "Error recording audio") }
                    mHandler?.post(runnable)
                    return
                }

                this@TWaveform.mAlertDialog?.dismiss()
                if (mFinishActivity) {
                    // FIXME 앱 종료
//                    this@TWaveform.finish()
                } else {
                    val runnable = Runnable { finishOpeningSoundFile() }
                    mHandler?.post(runnable)
                }
            }
        }
        mRecordAudioThread?.start()
    }



    private fun finishOpeningSoundFile() {
        waveform.setSoundFile(mSoundFile)
        waveform.recomputeHeights(mDensity)

        mMaxPos = waveform.maxPos()
        mLastDisplayedStartPos = -1
        mLastDisplayedEndPos = -1

        mTouchDragging = false

        mOffset = 0
        mOffsetGoal = 0
        mFlingVelocity = 0
        resetPositions()
        if (mEndPos > mMaxPos)
            mEndPos = mMaxPos

        mCaption = (mSoundFile?.filetype + ", " +
                mSoundFile?.sampleRate + " Hz, " +
                mSoundFile?.avgBitrateKbps + " kbps, " +
                formatTime(mMaxPos) + " " +
                "seconds")
        info.text = mCaption

        updateDisplay()
    }


    /**----------------------------------------------------
     * GUI
     *----------------------------------------------------*/
    /**
     * Called from both onCreate and onConfigurationChanged
     * (if the user switched layouts)
     */
    private fun loadGui() {
        // Inflate our UI from its XML layout description.
        // FIXME
//        setContentView(R.layout.editor)

        val metrics = DisplayMetrics()
        // FIXME
//        windowManager.defaultDisplay.getMetrics(metrics)
        mDensity = metrics.density

        mMarkerLeftInset = (46 * mDensity).toInt()
        mMarkerRightInset = (48 * mDensity).toInt()
        mMarkerTopOffset = (10 * mDensity).toInt()
        mMarkerBottomOffset = (10 * mDensity).toInt()

        starttext.addTextChangedListener(mTextWatcher)
        endtext.addTextChangedListener(mTextWatcher)

        play.setOnClickListener {
            onPlay(mStartPos)
        }

        rew.setOnLongClickListener {
            if (mIsPlaying) {
                var newPos = mPlayer?.getCurrentPosition()?.minus(5000)
                if (newPos != null) {
                    if (newPos < mPlayStartMsec)
                        newPos = mPlayStartMsec
                }
                // FIXME
//                newPos?.let { it1: Int ->
//                    mPlayer?.seekTo(it1)
//                }
            } else {
                startmarker.requestFocus()
                markerFocus(startmarker)
            }
        }

        ffwd.setOnClickListener {
            if (mIsPlaying) {
                var newPos = 5000 + mPlayer?.currentPosition!!
                if (newPos > mPlayEndMsec)
                    newPos = mPlayEndMsec
                mPlayer!!.seekTo(newPos)
            } else {
                endmarker.requestFocus()
                markerFocus(endmarker)
            }
        }


//        play.setOnClickListener(mPlayListener)
//        rew.setOnClickListener(mRewindListener)
//        ffwd.setOnClickListener(mFfwdListener)

        mark_start.setOnClickListener(mMarkStartListener)
        mark_end.setOnClickListener(mMarkEndListener)

        enableDisableButtons()

        waveform.setListener(this)

        info.setText(mCaption)

        mMaxPos = 0
        mLastDisplayedStartPos = -1
        mLastDisplayedEndPos = -1

        if (mSoundFile != null && !waveform.hasSoundFile()) {
            waveform.setSoundFile(mSoundFile)
            waveform.recomputeHeights(mDensity)
            mMaxPos = waveform.maxPos()
        }

        startmarker.setListener(this)
        startmarker.setAlpha(1f)
        startmarker.setFocusable(true)
        startmarker.setFocusableInTouchMode(true)
        mStartVisible = true

        endmarker.setListener(this)
        endmarker.setAlpha(1f)
        endmarker.setFocusable(true)
        endmarker.setFocusableInTouchMode(true)
        mEndVisible = true

        updateDisplay()
    }

    @Synchronized
    private fun updateDisplay() {
        if (mIsPlaying) {
            val now = mPlayer?.currentPosition
            val frames = now?.let { waveform.millisecsToPixels(it) }
            if (frames != null) {
                waveform.setPlayback(frames)
            }
            if (frames != null) {
                setOffsetGoalNoUpdate(frames - mWidth / 2)
            }
            if (now != null) {
                if (now >= mPlayEndMsec) {
                    handlePause()
                }
            }
        }

        if (!mTouchDragging) {
            var offsetDelta: Int

            if (mFlingVelocity != 0) {
                offsetDelta = mFlingVelocity / 30
                if (mFlingVelocity > 80) {
                    mFlingVelocity -= 80
                } else if (mFlingVelocity < -80) {
                    mFlingVelocity += 80
                } else {
                    mFlingVelocity = 0
                }

                mOffset += offsetDelta

                if (mOffset + mWidth / 2 > mMaxPos) {
                    mOffset = mMaxPos - mWidth / 2
                    mFlingVelocity = 0
                }
                if (mOffset < 0) {
                    mOffset = 0
                    mFlingVelocity = 0
                }
                mOffsetGoal = mOffset
            } else {
                offsetDelta = mOffsetGoal - mOffset

                offsetDelta = when {
                    offsetDelta > 10 -> offsetDelta / 10
                    offsetDelta > 0 -> 1
                    offsetDelta < -10 -> offsetDelta / 10
                    offsetDelta < 0 -> -1
                    else -> 0
                }

                mOffset += offsetDelta
            }
        }

        waveform.setParameters(mStartPos, mEndPos, mOffset)
        waveform.invalidate()

        startmarker.contentDescription =
            ((resources.getText(R.string.start_marker)).toString() + " " + formatTime(mStartPos))
        endmarker.contentDescription = ((resources.getText(R.string.end_marker)).toString() + " " + formatTime(mEndPos))

        var startX = mStartPos - mOffset - mMarkerLeftInset
        if (startX + startmarker.getWidth() >= 0) {
            if (!mStartVisible) {
                // Delay this to avoid flicker
                mHandler?.postDelayed(object : Runnable {
                    override fun run() {
                        mStartVisible = true
                        startmarker.setAlpha(1f)
                    }
                }, 0)
            }
        } else {
            if (mStartVisible) {
                startmarker.setAlpha(0f)
                mStartVisible = false
            }
            startX = 0
        }

        var endX = mEndPos - mOffset - endmarker.getWidth() + mMarkerRightInset
        if (endX + endmarker.getWidth() >= 0) {
            if (!mEndVisible) {
                // Delay this to avoid flicker
                mHandler?.postDelayed(object : Runnable {
                    override fun run() {
                        mEndVisible = true
                        endmarker.setAlpha(1f)
                    }
                }, 0)
            }
        } else {
            if (mEndVisible) {
                endmarker.setAlpha(0f)
                mEndVisible = false
            }
            endX = 0
        }

        var params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(
            startX,
            mMarkerTopOffset,
            -startmarker.getWidth(),
            -startmarker.getHeight()
        )
        startmarker.setLayoutParams(params)

        params = RelativeLayout.LayoutParams(
            RelativeLayout.LayoutParams.WRAP_CONTENT,
            RelativeLayout.LayoutParams.WRAP_CONTENT
        )
        params.setMargins(
            endX,
            waveform.getMeasuredHeight() - endmarker.getHeight() - mMarkerBottomOffset,
            -startmarker.getWidth(),
            -startmarker.getHeight()
        )
        endmarker.setLayoutParams(params)
    }





    /**----------------------------------------------------
     * Event
     *----------------------------------------------------*/
    override fun markerFocus(marker: TWaveformMarker) {
        mKeyDown = false
        if (marker === startmarker) {
            setOffsetGoalStartNoUpdate()
        } else {
            setOffsetGoalEndNoUpdate()
        }

        // Delay updaing the display because if this focus was in
        // response to a touch event, we want to receive the touch
        // event too before updating the display.
        mHandler?.postDelayed(Runnable { updateDisplay() }, 100)
    }




    /**----------------------------------------------------
     * Text
     *----------------------------------------------------*/
    private val mTextWatcher = object : TextWatcher {
        override fun beforeTextChanged(
            s: CharSequence, start: Int,
            count: Int, after: Int
        ) {
        }

        override fun onTextChanged(
            s: CharSequence,
            start: Int, before: Int, count: Int
        ) {
        }

        override fun afterTextChanged(s: Editable) {
            if (starttext.hasFocus()) {
                try {
                    mStartPos = waveform.secondsToPixels(
                        java.lang.Double.parseDouble(
                            starttext.text.toString()
                        )
                    )
                    updateDisplay()
                } catch (e: NumberFormatException) {
                }

            }
            if (endtext.hasFocus()) {
                try {
                    mEndPos = waveform.secondsToPixels(
                        java.lang.Double.parseDouble(
                            endtext.text.toString()
                        )
                    )
                    updateDisplay()
                } catch (e: NumberFormatException) {
                }

            }
        }
    }



    /**----------------------------------------------------
     * Time
     *----------------------------------------------------*/
    private val mTimerRunnable = object : Runnable {
        override fun run() {
            // Updating an EditText is slow on Android.  Make sure
            // we only do the update if the text has actually changed.
            if ((mStartPos != mLastDisplayedStartPos && !starttext.hasFocus())) {
                starttext.setText(formatTime(mStartPos))
                mLastDisplayedStartPos = mStartPos
            }

            if ((mEndPos != mLastDisplayedEndPos && !endtext.hasFocus())) {
                endtext.setText(formatTime(mEndPos))
                mLastDisplayedEndPos = mEndPos
            }

            mHandler?.postDelayed(this, 100)
        }
    }

    private fun formatTime(pixels: Int): String {
        return if (waveform != null && waveform.isInitialized()) {
            formatDecimal(waveform.pixelsToSeconds(pixels))
        } else {
            ""
        }
    }

    private fun formatDecimal(x: Double): String {
        var xWhole = x.toInt()
        var xFrac = (100 * (x - xWhole) + 0.5).toInt()

        if (xFrac >= 100) {
            xWhole++ //Round up
            xFrac -= 100 //Now we need the remainder after the round up
            if (xFrac < 10) {
                xFrac *= 10 //we need a fraction that is 2 digits long
            }
        }

        return if (xFrac < 10)
            (xWhole).toString() + ".0" + xFrac
        else
            (xWhole).toString() + "." + xFrac
    }





    /**----------------------------------------------------
     * Offset
     *----------------------------------------------------*/
    private fun resetPositions() {
        mStartPos = waveform.secondsToPixels(0.0)
        mEndPos = waveform.secondsToPixels(15.0)
    }

    private fun trap(pos: Int): Int {
        if (pos < 0)
            return 0
        return if (pos > mMaxPos) mMaxPos else pos
    }

    private fun setOffsetGoalStart() {
        setOffsetGoal(mStartPos - mWidth / 2)
    }

    private fun setOffsetGoalStartNoUpdate() {
        setOffsetGoalNoUpdate(mStartPos - mWidth / 2)
    }

    private fun setOffsetGoalEnd() {
        setOffsetGoal(mEndPos - mWidth / 2)
    }

    private fun setOffsetGoalEndNoUpdate() {
        setOffsetGoalNoUpdate(mEndPos - mWidth / 2)
    }

    private fun setOffsetGoal(offset: Int) {
        setOffsetGoalNoUpdate(offset)
        updateDisplay()
    }

    private fun setOffsetGoalNoUpdate(offset: Int) {
        if (mTouchDragging) {
            return
        }

        mOffsetGoal = offset
        if (mOffsetGoal + mWidth / 2 > mMaxPos)
            mOffsetGoal = mMaxPos - mWidth / 2
        if (mOffsetGoal < 0)
            mOffsetGoal = 0
    }



    /**----------------------------------------------------
     * Time
     *----------------------------------------------------*/
    private fun getCurrentTime(): Long {
        return System.nanoTime() / 1000000
    }


    /**----------------------------------------------------
     * Error
     *----------------------------------------------------*/
    private fun getStackTrace(e: Exception): String {
        val writer = StringWriter()
        e.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }



    /**----------------------------------------------------
     * Alert
     *----------------------------------------------------*/
    private fun showFinalAlert(e: Exception, message: String) {
        // FIXME Alert
        showFinalAlert(e, message)
    }

    /**
     * Show a "final" alert dialog that will exit the activity
     * after the user clicks on the OK button.  If an exception
     * is passed, it's assumed to be an error condition, and the
     * dialog is presented as an error, and the stack trace is
     * logged.  If there's no exception, it's a success message.
     */
    private fun showFinalAlert(e: Exception?, message: CharSequence) {
        val title: CharSequence
        if (e != null) {
            Log.e("Ringdroid", "Error: $message")
            Log.e("Ringdroid", getStackTrace(e!!))
            title = "Error"
            // FIXME
//            setResult(Activity.RESULT_CANCELED, Intent())
        } else {
            Log.v("Ringdroid", "Success: $message")
            title = "Success"
        }

        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(
                "OK"
            ) { _, _ ->
                // FIXME 앱 종료
//                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun showFinalAlert(e: Exception, messageResourceId: Int) {
        showFinalAlert(e, resources.getText(messageResourceId))
    }






    /**----------------------------------------------------
     * Video
     *----------------------------------------------------*/
    @Synchronized
    private fun handlePause() {
        if (mPlayer != null && mPlayer!!.isPlaying()) {
            mPlayer!!.pause()
        }
        waveform.setPlayback(-1)
        mIsPlaying = false
        enableDisableButtons()
    }

    @Synchronized
    private fun onPlay(startPosition: Int) {
        if (mIsPlaying) {
            handlePause()
            return
        }

        if (mPlayer == null) {
            // Not initialized yet
            return
        }

        try {
            mPlayStartMsec = waveform.pixelsToMillisecs(startPosition)
            if (startPosition < mStartPos) {
                mPlayEndMsec = waveform.pixelsToMillisecs(mStartPos)
            } else if (startPosition > mEndPos) {
                mPlayEndMsec = waveform.pixelsToMillisecs(mMaxPos)
            } else {
                mPlayEndMsec = waveform.pixelsToMillisecs(mEndPos)
            }
            mPlayer!!.setOnCompletionListener(object : SamplePlayer.OnCompletionListener {
                override fun onCompletion() {
                    handlePause()
                }
            })
            mIsPlaying = true

            mPlayer!!.seekTo(mPlayStartMsec)
            mPlayer!!.start()
            updateDisplay()
            enableDisableButtons()
        } catch (e: Exception) {
            showFinalAlert(e, "Unable to play this media file")
            return
        }

    }

    private fun enableDisableButtons() {
        if (mIsPlaying) {
            play.setImageResource(android.R.drawable.ic_media_pause)
            play.contentDescription = "Stop"
        } else {
            play.setImageResource(android.R.drawable.ic_media_play)
            play.contentDescription = "Play"
        }
    }









    /**----------------------------------------------------
     * TWaveformMarker.MarkerListener
     *----------------------------------------------------*/
    override fun markerTouchStart(TWaveformMarker: TWaveformMarker, pos: Float) {
        mTouchDragging = true
        mTouchStart = x
        mTouchInitialStartPos = mStartPos
        mTouchInitialEndPos = mEndPos
    }

    override fun markerTouchMove(marker: TWaveformMarker, x: Float) {
        val delta = x - mTouchStart

        if (marker === startmarker) {
            mStartPos = trap((mTouchInitialStartPos + delta).toInt())
            mEndPos = trap((mTouchInitialEndPos + delta).toInt())
        } else {
            mEndPos = trap((mTouchInitialEndPos + delta).toInt())
            if (mEndPos < mStartPos)
                mEndPos = mStartPos
        }

        updateDisplay()
    }

    override fun markerTouchEnd(marker: TWaveformMarker) {
        mTouchDragging = false
        if (marker === startmarker) {
            setOffsetGoalStart()
        } else {
            setOffsetGoalEnd()
        }
    }




    override fun markerLeft(marker: TWaveformMarker, velocity: Int) {
        mKeyDown = true

        if (marker === startmarker) {
            val saveStart = mStartPos
            mStartPos = trap(mStartPos - velocity)
            mEndPos = trap(mEndPos - (saveStart - mStartPos))
            setOffsetGoalStart()
        }

        if (marker === endmarker) {
            if (mEndPos == mStartPos) {
                mStartPos = trap(mStartPos - velocity)
                mEndPos = mStartPos
            } else {
                mEndPos = trap(mEndPos - velocity)
            }

            setOffsetGoalEnd()
        }

        updateDisplay()
    }

    override fun markerRight(marker: TWaveformMarker, velocity: Int) {
        mKeyDown = true

        if (marker === startmarker) {
            val saveStart = mStartPos
            mStartPos += velocity
            if (mStartPos > mMaxPos)
                mStartPos = mMaxPos
            mEndPos += (mStartPos - saveStart)
            if (mEndPos > mMaxPos)
                mEndPos = mMaxPos

            setOffsetGoalStart()
        }

        if (marker === endmarker) {
            mEndPos += velocity
            if (mEndPos > mMaxPos)
                mEndPos = mMaxPos

            setOffsetGoalEnd()
        }

        updateDisplay()
    }

    override fun markerEnter(marker: TWaveformMarker) {}
    override fun markerDraw() {}
    override fun markerKeyUp() {
        mKeyDown = false
        updateDisplay()
    }

}