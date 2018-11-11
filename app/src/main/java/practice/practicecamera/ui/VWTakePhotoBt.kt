package practice.practicecamera.ui

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import practice.practicecamera.R

/**
 * The button to snap photos with.
 */
class VWTakePhotoBt : View
{
    //region Private Properties

    private val buttonColor = Color.WHITE
    private val highlightedButtonColor = Color.CYAN //TODO
    private val borderPaint = Paint()
    private val buttonPaint = Paint()
    private val spacingFromOutsideOuterRingToInnerBt = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 7f, resources.displayMetrics
    )
    private val borderWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
    )

    //endregion
    //region Lifecycle

    constructor(context: Context) : super(context)
    {
        init(null, 0)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    {
        init(attrs, 0)
    }

    constructor(context: Context, attrs: AttributeSet, defStyle: Int) :
            super(context, attrs, defStyle)
    {
        init(attrs, defStyle)
    }

    private fun init(attrs: AttributeSet?, defStyle: Int)
    {
        //Load attributes
        val a: TypedArray? = context.obtainStyledAttributes(
                attrs, R.styleable.VWTakePhotoBt, defStyle, 0
        )
        a?.recycle()

        //Set up paint objects
        borderPaint.strokeWidth = borderWidth
        borderPaint.color = Color.WHITE
        borderPaint.style = Paint.Style.STROKE
        borderPaint.isAntiAlias = true
        buttonPaint.color = buttonColor
        buttonPaint.style = Paint.Style.FILL
        buttonPaint.isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas)
    {
        super.onDraw(canvas)
        canvas.drawCircle(width / 2f, height / 2f,
                          width / 2f - borderPaint.strokeWidth,
                          borderPaint)
        canvas.drawCircle(width / 2f, height / 2f,
                          width / 2f - spacingFromOutsideOuterRingToInnerBt,
                          buttonPaint)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean
    {
        when (event?.action)
        {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE ->
            {
                highlightUI()
            }
            MotionEvent.ACTION_UP                            ->
            {
                unhighlightUI()
                performClick()
            }
            else                                             ->
            {
                unhighlightUI()
            }
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean
    {
        super.performClick()
        return true
    }

    //endregion
    //region Private Functions

    private fun highlightUI()
    {
        buttonPaint.color = highlightedButtonColor
    }

    private fun unhighlightUI()
    {
        buttonPaint.color = buttonColor
    }

    //endregion
}
