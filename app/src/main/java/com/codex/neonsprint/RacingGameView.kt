package com.codex.neonsprint

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class RacingGameView(context: Context) : SurfaceView(context), Runnable {
    private val holderRef: SurfaceHolder = holder
    @Volatile
    private var running = false
    private var gameThread: Thread? = null

    private var viewportWidth = 1080f
    private var viewportHeight = 1920f
    private var roadWidth = viewportWidth * 0.82f
    private var laneWidth = roadWidth / 3f
    private var lastFrameTime = System.nanoTime()

    private var raceActive = true
    private var score = 0f
    private var bestScore = 0f
    private var speed = 0.72f
    private var distance = 0f
    private var roadOffset = 0f

    private var playerLane = 1
    private var targetLane = 1
    private var playerShake = 0f

    private val opponents = mutableListOf<Car>()
    private val boosts = mutableListOf<BoostPad>()
    private var spawnTimer = 0f
    private var boostTimer = 0f

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shoulderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val enemyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val controlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val controlTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class Car(
        var lane: Int,
        var z: Float,
        var wobble: Float,
        var color: Int,
    )

    private data class BoostPad(
        var lane: Int,
        var z: Float,
        var pulse: Float,
    )

    init {
        isFocusable = true
        keepScreenOn = true
        bestScore = context.getSharedPreferences("neon_sprint", Context.MODE_PRIVATE)
            .getFloat("best_score", 0f)

        roadPaint.color = Color.rgb(18, 22, 44)
        shoulderPaint.color = Color.rgb(255, 93, 111)
        lanePaint.color = Color.argb(180, 120, 190, 255)
        textPaint.color = Color.WHITE
        textPaint.textSize = 74f
        textPaint.isFakeBoldText = true
        labelPaint.color = Color.rgb(140, 166, 255)
        labelPaint.textSize = 30f
        panelPaint.color = Color.argb(185, 5, 10, 28)
        panelStrokePaint.style = Paint.Style.STROKE
        panelStrokePaint.strokeWidth = 3f
        panelStrokePaint.color = Color.argb(120, 255, 255, 255)
        playerPaint.color = Color.rgb(101, 247, 255)
        enemyPaint.color = Color.rgb(255, 140, 120)
        boostPaint.color = Color.rgb(170, 255, 120)
        glowPaint.maskFilter = null
        controlPaint.color = Color.argb(110, 255, 255, 255)
        controlTextPaint.color = Color.WHITE
        controlTextPaint.textAlign = Paint.Align.CENTER
        controlTextPaint.textSize = 72f
    }

    override fun run() {
        while (running) {
            if (!holderRef.surface.isValid) {
                continue
            }
            val now = System.nanoTime()
            val delta = ((now - lastFrameTime) / 1_000_000_000f).coerceAtMost(0.033f)
            lastFrameTime = now

            update(delta)
            drawGame(holderRef.lockCanvas())
        }
    }

    fun resume() {
        if (running) return
        running = true
        lastFrameTime = System.nanoTime()
        gameThread = Thread(this).also { it.start() }
    }

    fun pause() {
        running = false
        gameThread?.join(500)
        gameThread = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_MOVE) {
            if (!raceActive) {
                resetGame()
                return true
            }
            when {
                event.x < width * 0.33f -> movePlayer(-1)
                event.x > width * 0.66f -> movePlayer(1)
                else -> speed = min(1.25f, speed + 0.08f)
            }
            return true
        }
        return super.onTouchEvent(event)
    }

    private fun movePlayer(direction: Int) {
        targetLane = (targetLane + direction).coerceIn(0, 2)
    }

    private fun resetGame() {
        raceActive = true
        score = 0f
        speed = 0.72f
        distance = 0f
        roadOffset = 0f
        playerLane = 1
        targetLane = 1
        playerShake = 0f
        spawnTimer = 0.5f
        boostTimer = 1.2f
        opponents.clear()
        boosts.clear()
    }

    private fun endRace() {
        raceActive = false
        bestScore = max(bestScore, score)
        context.getSharedPreferences("neon_sprint", Context.MODE_PRIVATE)
            .edit()
            .putFloat("best_score", bestScore)
            .apply()
    }

    private fun update(delta: Float) {
        viewportWidth = width.toFloat().coerceAtLeast(1f)
        viewportHeight = height.toFloat().coerceAtLeast(1f)
        roadWidth = viewportWidth * 0.82f
        laneWidth = roadWidth / 3f
        controlTextPaint.textSize = viewportWidth * 0.07f
        textPaint.textSize = viewportWidth * 0.07f
        labelPaint.textSize = viewportWidth * 0.028f

        if (!raceActive) {
            playerShake *= 0.94f
            return
        }

        roadOffset += delta * speed * 900f
        distance += delta * speed * 60f
        score += delta * speed * 100f
        speed = min(1.9f, speed + delta * 0.018f)
        playerLane += when {
            playerLane < targetLane -> 1
            playerLane > targetLane -> -1
            else -> 0
        }
        playerShake = (playerShake + delta * 10f) % 1f

        spawnTimer -= delta
        boostTimer -= delta
        if (spawnTimer <= 0f) {
            spawnOpponent()
            spawnTimer = max(0.28f, 1.05f - speed * 0.25f)
        }
        if (boostTimer <= 0f) {
            spawnBoost()
            boostTimer = Random.nextFloat() * 2.2f + 1.8f
        }

        for (i in opponents.indices.reversed()) {
            val car = opponents[i]
            car.z -= delta * (speed * 2.4f + 0.9f)
            car.wobble += delta * 3.2f
            if (car.z < 0.18f) {
                if (car.lane == playerLane) {
                    endRace()
                    return
                }
                opponents.removeAt(i)
            }
        }

        for (i in boosts.indices.reversed()) {
            val boost = boosts[i]
            boost.z -= delta * (speed * 2.4f + 0.9f)
            boost.pulse += delta * 4.8f
            if (boost.z < 0.22f) {
                if (boost.lane == playerLane) {
                    score += 120f
                    speed = min(2.2f, speed + 0.22f)
                }
                boosts.removeAt(i)
            }
        }
    }

    private fun spawnOpponent() {
        val lane = Random.nextInt(0, 3)
        if (opponents.count { it.lane == lane && it.z > 0.9f } >= 2) return
        opponents += Car(
            lane = lane,
            z = 3.4f + Random.nextFloat() * 1.8f,
            wobble = Random.nextFloat() * 10f,
            color = Color.rgb(255, Random.nextInt(110, 180), Random.nextInt(110, 180)),
        )
    }

    private fun spawnBoost() {
        boosts += BoostPad(
            lane = Random.nextInt(0, 3),
            z = 4.2f + Random.nextFloat() * 1.6f,
            pulse = Random.nextFloat() * 6f,
        )
    }

    private fun laneCenterX(lane: Int, depth: Float): Float {
        val horizon = viewportHeight * 0.24f
        val roadCenter = viewportWidth / 2f
        val perspectiveRoadWidth = roadWidth / (depth + 0.35f)
        val left = roadCenter - perspectiveRoadWidth / 2f
        return left + perspectiveRoadWidth * (lane + 0.5f) / 3f
    }

    private fun scaleForDepth(depth: Float): Float {
        return 1f / (depth + 0.25f)
    }

    private fun yForDepth(depth: Float): Float {
        val horizon = viewportHeight * 0.24f
        return horizon + (viewportHeight * 0.9f - horizon) * (1f - 1f / (depth + 0.55f))
    }

    private fun drawGame(canvas: Canvas?) {
        if (canvas == null) return
        try {
            drawBackground(canvas)
            drawRoad(canvas)
            boosts.sortedByDescending { it.z }.forEach { drawBoost(canvas, it) }
            opponents.sortedByDescending { it.z }.forEach { drawOpponent(canvas, it) }
            drawPlayer(canvas)
            drawHud(canvas)
            if (!raceActive) {
                drawGameOver(canvas)
            }
        } finally {
            holderRef.unlockCanvasAndPost(canvas)
        }
    }

    private fun drawBackground(canvas: Canvas) {
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            viewportHeight,
            intArrayOf(Color.rgb(15, 28, 74), Color.rgb(5, 8, 22), Color.BLACK),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, backgroundPaint)

        val cityPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(170, 62, 104, 220)
        }
        val horizon = viewportHeight * 0.24f
        val buildingWidth = viewportWidth / 12f
        for (i in 0..12) {
            val left = i * buildingWidth - (roadOffset * 0.05f % buildingWidth)
            val heightFactor = 0.2f + (i % 4) * 0.1f
            canvas.drawRoundRect(
                RectF(left, horizon - viewportHeight * heightFactor, left + buildingWidth * 0.72f, horizon),
                12f,
                12f,
                cityPaint,
            )
        }
    }

    private fun drawRoad(canvas: Canvas) {
        val horizonY = viewportHeight * 0.24f
        val bottomY = viewportHeight * 0.94f
        val roadPath = Path().apply {
            moveTo(viewportWidth * 0.36f, horizonY)
            lineTo(viewportWidth * 0.64f, horizonY)
            lineTo(viewportWidth * 0.91f, bottomY)
            lineTo(viewportWidth * 0.09f, bottomY)
            close()
        }
        canvas.drawPath(roadPath, roadPaint)

        val shoulderWidth = viewportWidth * 0.018f
        canvas.drawRect(viewportWidth * 0.09f, bottomY - 10f, viewportWidth * 0.09f + shoulderWidth, bottomY, shoulderPaint)
        canvas.drawRect(viewportWidth * 0.91f - shoulderWidth, bottomY - 10f, viewportWidth * 0.91f, bottomY, shoulderPaint)

        for (lane in 1..2) {
            for (segment in 0..14) {
                val depth = ((segment + (roadOffset / 180f)) % 15f) / 3.4f + 0.28f
                val nextDepth = depth + 0.18f
                val x = laneCenterX(lane - 1, depth) + laneWidth * 0.02f
                val x2 = laneCenterX(lane - 1, nextDepth) + laneWidth * 0.02f
                val y = yForDepth(depth)
                val y2 = yForDepth(nextDepth)
                lanePaint.strokeWidth = max(3f, 18f * scaleForDepth(depth))
                canvas.drawLine(x, y, x2, y2, lanePaint)
            }
        }
    }

    private fun drawPlayer(canvas: Canvas) {
        val depth = 0.16f
        val centerX = laneCenterX(playerLane, depth)
        val centerY = yForDepth(depth)
        val scale = scaleForDepth(depth)
        val carWidth = viewportWidth * 0.14f * scale * 2.2f
        val carHeight = viewportHeight * 0.09f * scale * 2.1f
        val shakeOffset = (playerShake - 0.5f) * viewportWidth * 0.008f
        val carRect = RectF(
            centerX - carWidth / 2f + shakeOffset,
            centerY - carHeight,
            centerX + carWidth / 2f + shakeOffset,
            centerY,
        )

        val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(90, 0, 0, 0)
        }
        canvas.drawOval(RectF(carRect.left - 18f, carRect.bottom - 14f, carRect.right + 18f, carRect.bottom + 14f), shadowPaint)

        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                carRect.left,
                carRect.top,
                carRect.right,
                carRect.bottom,
                Color.rgb(168, 255, 252),
                Color.rgb(37, 134, 255),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRoundRect(carRect, 28f, 28f, bodyPaint)

        val windshield = RectF(
            carRect.left + carWidth * 0.2f,
            carRect.top + carHeight * 0.16f,
            carRect.right - carWidth * 0.2f,
            carRect.top + carHeight * 0.5f,
        )
        val windshieldPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(220, 15, 30, 70)
        }
        canvas.drawRoundRect(windshield, 20f, 20f, windshieldPaint)
    }

    private fun drawOpponent(canvas: Canvas, car: Car) {
        val centerX = laneCenterX(car.lane, car.z)
        val centerY = yForDepth(car.z)
        val scale = scaleForDepth(car.z)
        val wobbleOffset = kotlin.math.sin(car.wobble) * viewportWidth * 0.01f * scale
        val width = viewportWidth * 0.2f * scale
        val height = viewportHeight * 0.16f * scale
        val rect = RectF(
            centerX - width / 2f + wobbleOffset,
            centerY - height,
            centerX + width / 2f + wobbleOffset,
            centerY,
        )

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(rect.left, rect.top, rect.right, rect.bottom, car.color, Color.rgb(110, 10, 40), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(rect, 20f, 20f, paint)

        val lightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(240, 255, 240, 170)
        }
        canvas.drawRoundRect(RectF(rect.left + 8f, rect.top + 10f, rect.left + width * 0.26f, rect.top + 24f), 8f, 8f, lightPaint)
        canvas.drawRoundRect(RectF(rect.right - width * 0.26f, rect.top + 10f, rect.right - 8f, rect.top + 24f), 8f, 8f, lightPaint)
    }

    private fun drawBoost(canvas: Canvas, boost: BoostPad) {
        val centerX = laneCenterX(boost.lane, boost.z)
        val centerY = yForDepth(boost.z)
        val scale = scaleForDepth(boost.z)
        val radius = viewportWidth * 0.11f * scale * (1f + 0.1f * kotlin.math.sin(boost.pulse))

        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(120, 170, 255, 110)
        }
        canvas.drawCircle(centerX, centerY - radius * 0.6f, radius * 1.15f, glow)

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(3f, radius * 0.22f)
            color = boostPaint.color
        }
        canvas.drawCircle(centerX, centerY - radius * 0.6f, radius, ring)
    }

    private fun drawHud(canvas: Canvas) {
        val panel = RectF(28f, 36f, viewportWidth - 28f, 180f)
        canvas.drawRoundRect(panel, 30f, 30f, panelPaint)
        canvas.drawRoundRect(panel, 30f, 30f, panelStrokePaint)

        canvas.drawText("SCORE", 58f, 86f, labelPaint)
        canvas.drawText(score.toInt().toString(), 58f, 148f, textPaint)
        canvas.drawText("BEST", viewportWidth * 0.62f, 86f, labelPaint)
        canvas.drawText(bestScore.toInt().toString(), viewportWidth * 0.62f, 148f, textPaint)

        val leftControl = RectF(40f, viewportHeight - 220f, viewportWidth * 0.32f, viewportHeight - 60f)
        val rightControl = RectF(viewportWidth * 0.68f, viewportHeight - 220f, viewportWidth - 40f, viewportHeight - 60f)
        canvas.drawRoundRect(leftControl, 36f, 36f, controlPaint)
        canvas.drawRoundRect(rightControl, 36f, 36f, controlPaint)
        canvas.drawText("◀", leftControl.centerX(), leftControl.centerY() + 24f, controlTextPaint)
        canvas.drawText("▶", rightControl.centerX(), rightControl.centerY() + 24f, controlTextPaint)

        val centerLabelPaint = Paint(labelPaint).apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("中央を長押しで加速", viewportWidth / 2f, viewportHeight - 100f, centerLabelPaint)
    }

    private fun drawGameOver(canvas: Canvas) {
        val overlayPaint = Paint().apply {
            color = Color.argb(190, 3, 6, 20)
        }
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, overlayPaint)

        val panel = RectF(viewportWidth * 0.08f, viewportHeight * 0.22f, viewportWidth * 0.92f, viewportHeight * 0.62f)
        canvas.drawRoundRect(panel, 40f, 40f, panelPaint)
        canvas.drawRoundRect(panel, 40f, 40f, panelStrokePaint)

        val titlePaint = Paint(textPaint).apply {
            textAlign = Paint.Align.CENTER
            textSize = viewportWidth * 0.11f
        }
        val bodyPaint = Paint(labelPaint).apply {
            textAlign = Paint.Align.CENTER
            textSize = viewportWidth * 0.046f
        }
        canvas.drawText("NEON SPRINT", viewportWidth / 2f, panel.top + 120f, titlePaint)
        canvas.drawText("クラッシュしました", viewportWidth / 2f, panel.top + 200f, bodyPaint)
        canvas.drawText("スコア ${score.toInt()}", viewportWidth / 2f, panel.top + 290f, titlePaint)
        canvas.drawText("タップして再スタート", viewportWidth / 2f, panel.top + 370f, bodyPaint)
    }
}
