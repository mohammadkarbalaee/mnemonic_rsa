package de.post.ident.internal_autoid.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.SizeF
import android.view.View

class TiltingOverlay: View, ValueAnimator.AnimatorUpdateListener {
    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    private val outlineStrokeWidth = 8.0f
    private val outlineCornerRadius = 75.0f
    private val outlineMinMarginHorizontal = 80f
    private val outlineMinMarginVertical = 35f
    private val offsetVertical = 16f
    private val axisOverlap = 25f
    private val documentTypeSizes = mapOf(
        DocumentType.ID_CARD to SizeF(54f, 85f),
        DocumentType.PASSPORT to SizeF(90f, 123f)
    )

    private val outlinePaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = outlineStrokeWidth
        isAntiAlias = true
    }
    private val innerPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
        color = Color.TRANSPARENT
    }
    private val outerPaint = Paint()

    private val outlineRectPath by lazy { createOutlineRect() }

    enum class TiltMode {
        HORIZONTAL,
        VERTICAL
    }

    var listener: AnimationValueListener? = null
    var tiltMode: TiltMode = TiltMode.HORIZONTAL
        private set
    var degrees: Float = 6f
    var duration: Long = 1000L
        private set
    var documentType: DocumentType = DocumentType.ID_CARD

    private val outlineAspectRatioWidth: Float
        get() = documentTypeSizes.getValue(documentType).width
    private val outlineAspectRatioHeight
        get() = documentTypeSizes.getValue(documentType).height

    fun switchTiltMode() = startAnimation(
        if (tiltMode == TiltMode.HORIZONTAL)
            TiltMode.VERTICAL
        else
            TiltMode.HORIZONTAL
    )

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas?) {
        super.onDraw(canvas)
        drawOuter(canvas)
        drawInner(canvas)
        drawOutline(canvas)
        drawAxis(canvas)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        outerPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            measuredHeight.toFloat(),
            IntArray(2).apply {
                set(0, Color.parseColor("#bbcca400"))
                set(1, Color.parseColor("#bbffffff"))
            },
            null,
            Shader.TileMode.CLAMP
        )
    }

    private val valueAnimator = ValueAnimator.ofFloat(-1f, 1f).apply {
        duration = this.duration
        addUpdateListener(this@TiltingOverlay)
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
    }

    override fun onAnimationUpdate(p0: ValueAnimator) {
        val animationValue = valueAnimator?.animatedValue as Float
        outlineRectPath.tilt(animationValue)
        invalidate()

        listener?.onAnimationValue(animationValue)
    }

    fun startAnimation(
        mode: TiltMode,
        degrees: Float = this.degrees,
        duration: Long = this.duration
    ) {
        this.tiltMode = mode
        this.degrees = degrees
        this.duration = duration
        outlineRectPath.tiltMode = mode
        if (degrees > 0) valueAnimator.setFloatValues(-degrees, degrees)
        valueAnimator.duration = duration
        valueAnimator.start()
    }

    fun stopAnimation() {
        valueAnimator.cancel()
    }

    private fun drawOutline(canvas: Canvas?) = canvas?.drawPath(
        outlineRectPath,
        outlinePaint
    )

    private fun drawInner(canvas: Canvas?) = canvas?.drawPath(
        outlineRectPath,
        innerPaint
    )

    private fun drawOuter(canvas: Canvas?) = canvas?.drawPaint(outerPaint)

    private fun drawAxis(canvas: Canvas?) {
        val outlineRect = createOutlineRect()
        if (tiltMode == TiltMode.HORIZONTAL) {
            canvas?.drawLine(
                outlineRect.left - axisOverlap,
                height / 2f,
                outlineRect.right + axisOverlap,
                height / 2f,
                outlinePaint
            )
        } else {
            canvas?.drawLine(
                width / 2f,
                outlineRect.top - axisOverlap,
                width / 2f,
                outlineRect.bottom + axisOverlap,
                outlinePaint
            )
        }
    }

    private fun createOutlineRect() : RoundedRectPath {
        val outlineMarginHorizontalPx = outlineMinMarginHorizontal * context.getResources().getDisplayMetrics().density
        val outlineMarginVerticalPx = outlineMinMarginVertical * context.getResources().getDisplayMetrics().density
        val offsetVerticalPx = offsetVertical * context.getResources().getDisplayMetrics().density
        var rectWidth = width - (outlineMarginHorizontalPx * 2f)
        var rectHeight = rectWidth * (outlineAspectRatioHeight/outlineAspectRatioWidth)
        if (rectHeight > (height - (outlineMarginVerticalPx * 2f))) {
            rectHeight = height - (outlineMarginVerticalPx * 2f)
            rectWidth = rectHeight * (outlineAspectRatioWidth/outlineAspectRatioHeight)
        }

        val marginVertical = ((height - rectHeight) / 2f) + offsetVerticalPx
        val marginHorizontal = (width - rectWidth) / 2f

        return RoundedRectPath(
            marginHorizontal,
            marginVertical,
            rectWidth + marginHorizontal,
            rectHeight + marginVertical,
            outlineCornerRadius
        )
    }

    interface AnimationValueListener {
        fun onAnimationValue(value: Float)
    }
}

class RoundedRectPath(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val cornerRadius: Float
) : Path() {
    private val coordLeftTop: PointF
    private val coordLeftBottom: PointF
    private val coordTopLeft: PointF
    private val coordTopRight: PointF
    private val coordRightTop: PointF
    private val coordRightBottom: PointF
    private val coordBottomLeft: PointF
    private val coordBottomRight: PointF

    var tiltMode: TiltingOverlay.TiltMode = TiltingOverlay.TiltMode.HORIZONTAL

    init {
        coordLeftTop = PointF(left, top + cornerRadius)
        coordLeftBottom = PointF(left, bottom - cornerRadius)
        coordTopLeft = PointF(left + cornerRadius, top)
        coordTopRight = PointF(right - cornerRadius, top)
        coordRightTop = PointF(right, top + cornerRadius)
        coordRightBottom = PointF(right, bottom - cornerRadius)
        coordBottomLeft = PointF(left + cornerRadius, bottom)
        coordBottomRight = PointF(right - cornerRadius, bottom)
        create()
    }

    private fun create() {
        reset()
        moveTo(coordLeftBottom.x, coordLeftBottom.y)
        lineTo(coordLeftTop.x, coordLeftTop.y)
        arcTo(
            RectF(
                coordLeftTop.x,
                coordTopLeft.y,
                coordLeftTop.x + cornerRadius,
                coordTopLeft.y + cornerRadius
            ),
            180f,
            90f
        )
        lineTo(coordTopRight.x, coordTopRight.y)
        arcTo(
            RectF(
                coordRightTop.x - cornerRadius,
                coordTopRight.y,
                coordRightTop.x,
                coordTopRight.y + cornerRadius
            ),
            270f,
            90f
        )
        lineTo(coordRightBottom.x, coordRightBottom.y)
        arcTo(
            RectF(
                coordRightBottom.x - cornerRadius,
                coordBottomRight.y - cornerRadius,
                coordRightBottom.x,
                coordBottomRight.y
            ),
            0f,
            90f
        )
        lineTo(coordBottomLeft.x, coordBottomLeft.y)
        arcTo(
            RectF(
                coordLeftBottom.x,
                coordBottomLeft.y - cornerRadius,
                coordLeftBottom.x + cornerRadius,
                coordBottomLeft.y
            ),
            90f,
            90f
        )
        close()
    }

    fun tilt(degrees: Float) {
        create()
        val matrix = Matrix()
        val camera = Camera()
        val bounds = RectF()
        computeBounds(bounds, true)
        camera.save()
        var tiltValue = degrees
        if (tiltValue < 0) {
            tiltValue = 360f + tiltValue
        }

        when (tiltMode) {
            TiltingOverlay.TiltMode.HORIZONTAL -> camera.rotateX(tiltValue)
            TiltingOverlay.TiltMode.VERTICAL -> camera.rotateY(tiltValue)
        }
        camera.getMatrix(matrix)
        camera.restore()
        val translationCorrectionX = when (tiltMode) {
            TiltingOverlay.TiltMode.HORIZONTAL -> 0f
            TiltingOverlay.TiltMode.VERTICAL -> degrees
        }
        val translationCorrectionY = when (tiltMode) {
            TiltingOverlay.TiltMode.HORIZONTAL -> degrees * 6f //multiplier to smooth out implicit translation due to camera rotation
            TiltingOverlay.TiltMode.VERTICAL -> 0f
        }
        matrix.preTranslate(-bounds.centerX() + translationCorrectionX, -bounds.centerY() - translationCorrectionY)
        matrix.postTranslate(bounds.centerX(), bounds.centerY())
        transform(matrix)
    }
}
