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
import kotlin.math.sin
import kotlin.random.Random

class RacingGameView(context: Context) : SurfaceView(context), Runnable {
    private val holderRef: SurfaceHolder = holder
    private val prefs = context.getSharedPreferences("neon_sprint", Context.MODE_PRIVATE)

    @Volatile
    private var running = false
    private var gameThread: Thread? = null
    private var lastFrameTime = System.nanoTime()

    private enum class ScreenState { GARAGE, RACING, CRASHED }

    private data class OpponentCar(
        var lane: Float,
        var distance: Float,
        var speed: Float,
        var sway: Float,
        val spec: CarSpec,
    )

    private data class BoostRing(
        var lane: Float,
        var distance: Float,
        var pulse: Float,
    )

    private data class UiButton(
        val label: String,
        val rect: RectF,
    )

    private val garage = neonGarage
    private var selectedCarIndex = prefs.getInt("selected_car_index", 0).coerceIn(0, garage.lastIndex)
    private var bestScore = prefs.getFloat("best_score", 0f)
    private var state = ScreenState.GARAGE

    private var viewportWidth = 1080f
    private var viewportHeight = 1920f
    private var roadBottomWidth = viewportWidth * 0.82f
    private var roadTopWidth = viewportWidth * 0.24f
    private var horizonY = viewportHeight * 0.22f
    private var roadBottomY = viewportHeight * 0.95f

    private var playerLanePosition = 0f
    private var playerVelocityX = 0f
    private var playerHeading = 0f
    private var playerSpeed = 0f
    private var engineRpm = 1100f
    private var nitroCharge = 0.35f
    private var distanceTravelled = 0f
    private var raceScore = 0f
    private var elapsedTime = 0f
    private var roadAnimation = 0f
    private var crashFlash = 0f

    private var throttleInput = 0f
    private var brakeInput = 0f
    private var steeringInput = 0f
    private var steeringTouchId = -1
    private var pedalTouchId = -1

    private val opponents = mutableListOf<OpponentCar>()
    private val boosts = mutableListOf<BoostRing>()
    private var opponentSpawnTimer = 0f
    private var boostSpawnTimer = 1.4f
    private val stars = MutableList(80) {
        floatArrayOf(
            Random.nextFloat() * 2f - 1f,
            Random.nextFloat(),
            Random.nextFloat() * 30f + 2f,
        )
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lanePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shoulderPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ghostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val controlPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val controlStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val carBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boostPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brakePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isFocusable = true
        keepScreenOn = true

        cityPaint.color = Color.argb(160, 49, 85, 192)
        roadPaint.color = Color.rgb(18, 22, 44)
        lanePaint.color = Color.argb(165, 131, 204, 255)
        shoulderPaint.color = Color.rgb(255, 98, 122)
        textPaint.color = Color.WHITE
        textPaint.isFakeBoldText = true
        labelPaint.color = Color.rgb(148, 170, 255)
        panelPaint.color = Color.argb(185, 7, 10, 28)
        panelStrokePaint.style = Paint.Style.STROKE
        panelStrokePaint.color = Color.argb(90, 255, 255, 255)
        panelStrokePaint.strokeWidth = 3f
        ghostPaint.color = Color.argb(96, 0, 0, 0)
        controlPaint.color = Color.argb(84, 255, 255, 255)
        controlStrokePaint.style = Paint.Style.STROKE
        controlStrokePaint.strokeWidth = 3f
        controlStrokePaint.color = Color.argb(72, 255, 255, 255)
        buttonTextPaint.color = Color.WHITE
        buttonTextPaint.textAlign = Paint.Align.CENTER
        glassPaint.color = Color.argb(220, 18, 32, 72)
        boostPaint.color = Color.rgb(181, 255, 117)
        brakePaint.color = Color.argb(180, 255, 134, 134)
        barBgPaint.color = Color.argb(100, 255, 255, 255)
    }

    override fun run() {
        while (running) {
            if (!holderRef.surface.isValid) continue
            val now = System.nanoTime()
            val delta = ((now - lastFrameTime) / 1_000_000_000f).coerceAtMost(0.033f)
            lastFrameTime = now
            update(delta)
            drawFrame(holderRef.lockCanvas())
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
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handlePointerMove(event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP,
            MotionEvent.ACTION_CANCEL -> handlePointerUp(event)
        }
        return true
    }

    private fun handlePointerDown(event: MotionEvent) {
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        when (state) {
            ScreenState.GARAGE -> {
                if (handleGarageTap(x, y)) return
            }
            ScreenState.CRASHED -> {
                if (handleCrashTap(x, y)) return
            }
            ScreenState.RACING -> {
                if (x < viewportWidth * 0.55f && steeringTouchId == -1) {
                    steeringTouchId = pointerId
                    steeringInput = steeringForX(x)
                } else if (pedalTouchId == -1) {
                    pedalTouchId = pointerId
                    updatePedals(x, y)
                }
            }
        }
    }

    private fun handlePointerMove(event: MotionEvent) {
        for (i in 0 until event.pointerCount) {
            val pointerId = event.getPointerId(i)
            val x = event.getX(i)
            val y = event.getY(i)
            if (pointerId == steeringTouchId) {
                steeringInput = steeringForX(x)
            }
            if (pointerId == pedalTouchId) {
                updatePedals(x, y)
            }
        }
    }

    private fun handlePointerUp(event: MotionEvent) {
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)
        if (pointerId == steeringTouchId) {
            steeringTouchId = -1
            steeringInput = 0f
        }
        if (pointerId == pedalTouchId) {
            pedalTouchId = -1
            throttleInput = 0f
            brakeInput = 0f
        }
    }

    private fun steeringForX(x: Float): Float {
        val center = viewportWidth * 0.275f
        return ((x - center) / (viewportWidth * 0.22f)).coerceIn(-1f, 1f)
    }

    private fun updatePedals(x: Float, y: Float) {
        throttleInput = 0f
        brakeInput = 0f
        if (x < viewportWidth * 0.55f) return
        if (y < viewportHeight * 0.72f) {
            throttleInput = 1f
        } else {
            brakeInput = 1f
        }
    }

    private fun handleGarageTap(x: Float, y: Float): Boolean {
        garage.forEachIndexed { index, _ ->
            val rect = garageCardRect(index)
            if (rect.contains(x, y)) {
                selectedCarIndex = index
                prefs.edit().putInt("selected_car_index", selectedCarIndex).apply()
                return true
            }
        }
        val startRect = RectF(viewportWidth * 0.12f, viewportHeight * 0.86f, viewportWidth * 0.88f, viewportHeight * 0.94f)
        if (startRect.contains(x, y)) {
            startRace()
            return true
        }
        return false
    }

    private fun handleCrashTap(x: Float, y: Float): Boolean {
        val retry = RectF(viewportWidth * 0.12f, viewportHeight * 0.66f, viewportWidth * 0.88f, viewportHeight * 0.74f)
        val garageRect = RectF(viewportWidth * 0.12f, viewportHeight * 0.77f, viewportWidth * 0.88f, viewportHeight * 0.85f)
        when {
            retry.contains(x, y) -> {
                startRace()
                return true
            }
            garageRect.contains(x, y) -> {
                state = ScreenState.GARAGE
                crashFlash = 0f
                return true
            }
        }
        return false
    }

    private fun startRace() {
        state = ScreenState.RACING
        playerLanePosition = 0f
        playerVelocityX = 0f
        playerHeading = 0f
        playerSpeed = garage[selectedCarIndex].topSpeed * 0.28f
        engineRpm = 2500f
        nitroCharge = 0.35f
        distanceTravelled = 0f
        raceScore = 0f
        elapsedTime = 0f
        roadAnimation = 0f
        crashFlash = 0f
        throttleInput = 0f
        brakeInput = 0f
        steeringInput = 0f
        opponents.clear()
        boosts.clear()
        opponentSpawnTimer = 0.8f
        boostSpawnTimer = 1.6f
    }

    private fun update(delta: Float) {
        viewportWidth = width.toFloat().coerceAtLeast(1f)
        viewportHeight = height.toFloat().coerceAtLeast(1f)
        roadBottomWidth = viewportWidth * 0.82f
        roadTopWidth = viewportWidth * 0.24f
        horizonY = viewportHeight * 0.22f
        roadBottomY = viewportHeight * 0.95f
        textPaint.textSize = viewportWidth * 0.065f
        labelPaint.textSize = viewportWidth * 0.028f
        buttonTextPaint.textSize = viewportWidth * 0.055f

        stars.forEach { star ->
            star[2] -= if (state == ScreenState.RACING) playerSpeed * delta * 0.18f else 0.7f * delta
            if (star[2] < 1f) {
                star[0] = Random.nextFloat() * 2f - 1f
                star[1] = Random.nextFloat()
                star[2] = Random.nextFloat() * 30f + 12f
            }
        }

        if (state != ScreenState.RACING) {
            crashFlash = max(0f, crashFlash - delta * 1.8f)
            return
        }

        val spec = garage[selectedCarIndex]
        elapsedTime += delta
        roadAnimation += delta * playerSpeed * 12f

        val steeringTarget = steeringInput * spec.handling
        playerHeading += (steeringTarget - playerHeading) * min(1f, delta * 5.4f)

        val engineForce = throttleInput * spec.acceleration * (1f - playerSpeed / spec.topSpeed).coerceAtLeast(0.12f)
        val brakeForce = brakeInput * spec.braking
        val aeroDrag = 0.015f * playerSpeed * playerSpeed / spec.mass
        val rollingResistance = 1.6f + abs(playerHeading) * (5.4f - spec.grip)
        val longitudinalAccel = engineForce - brakeForce - aeroDrag - rollingResistance
        playerSpeed = (playerSpeed + longitudinalAccel * delta).coerceIn(0f, spec.topSpeed)

        if (throttleInput > 0f && nitroCharge > 0.01f && playerSpeed > spec.topSpeed * 0.52f) {
            nitroCharge = max(0f, nitroCharge - delta * 0.16f)
        } else {
            nitroCharge = min(1f, nitroCharge + delta * 0.045f)
        }

        val lateralGrip = spec.grip * 7.6f
        val driftFactor = 1f + spec.drift * 1.8f
        val lateralAccel = playerHeading * (14f + playerSpeed * 0.06f) - playerVelocityX * lateralGrip / driftFactor
        playerVelocityX += lateralAccel * delta
        playerVelocityX *= (1f - min(0.88f, delta * (2.8f - spec.drift)))
        playerLanePosition += playerVelocityX * delta

        val roadEdge = 1.34f
        if (abs(playerLanePosition) > roadEdge) {
            playerSpeed *= 1f - min(0.28f, delta * 2.3f)
            if (abs(playerLanePosition) > 1.62f) {
                onCrash()
                return
            }
        }

        engineRpm = 1100f + playerSpeed / spec.topSpeed * 7000f
        distanceTravelled += playerSpeed * delta
        raceScore = distanceTravelled * 12f + playerSpeed * 4f + nitroCharge * 40f

        opponentSpawnTimer -= delta
        boostSpawnTimer -= delta
        if (opponentSpawnTimer <= 0f) {
            spawnOpponent(spec)
            opponentSpawnTimer = max(0.36f, 1.1f - playerSpeed / spec.topSpeed * 0.48f)
        }
        if (boostSpawnTimer <= 0f) {
            boosts += BoostRing(
                lane = Random.nextFloat() * 2.1f - 1.05f,
                distance = 105f + Random.nextFloat() * 45f,
                pulse = Random.nextFloat() * 6f,
            )
            boostSpawnTimer = 2.4f + Random.nextFloat() * 1.8f
        }

        updateTraffic(delta)
        updateBoosts(delta, spec)
        crashFlash = max(0f, crashFlash - delta * 1.6f)
    }

    private fun spawnOpponent(playerSpec: CarSpec) {
        val spec = garage.random()
        val lane = listOf(-1f, 0f, 1f).random() + Random.nextFloat() * 0.18f - 0.09f
        if (opponents.count { abs(it.lane - lane) < 0.22f && it.distance > 65f } >= 2) return
        opponents += OpponentCar(
            lane = lane,
            distance = 90f + Random.nextFloat() * 55f,
            speed = playerSpec.topSpeed * (0.42f + Random.nextFloat() * 0.22f),
            sway = Random.nextFloat() * 8f,
            spec = spec,
        )
    }

    private fun updateTraffic(delta: Float) {
        for (i in opponents.indices.reversed()) {
            val car = opponents[i]
            car.distance -= (playerSpeed - car.speed) * delta
            car.sway += delta * (0.8f + car.spec.handling)
            car.lane += sin(car.sway) * delta * 0.015f * car.spec.drift

            if (car.distance < -8f) {
                opponents.removeAt(i)
                continue
            }

            val widthAllowance = 0.28f + car.spec.mass * 0.04f
            val closeEnough = car.distance in 2.5f..10f
            val laneOverlap = abs(car.lane - playerLanePosition) < widthAllowance
            if (closeEnough && laneOverlap) {
                val closingSpeed = max(0f, playerSpeed - car.speed)
                playerSpeed = max(8f, playerSpeed - (18f + closingSpeed * 0.25f))
                if (closingSpeed > 18f || abs(playerHeading) > 0.42f) {
                    onCrash()
                    return
                }
                playerVelocityX += if (playerLanePosition >= car.lane) 1.2f else -1.2f
                crashFlash = 0.7f
            }
        }
    }

    private fun updateBoosts(delta: Float, spec: CarSpec) {
        for (i in boosts.indices.reversed()) {
            val ring = boosts[i]
            ring.distance -= playerSpeed * delta
            ring.pulse += delta * 4.4f
            if (ring.distance < -5f) {
                boosts.removeAt(i)
                continue
            }
            if (ring.distance < 7f && abs(ring.lane - playerLanePosition) < 0.24f) {
                nitroCharge = min(1f, nitroCharge + 0.38f)
                playerSpeed = min(spec.topSpeed, playerSpeed + 10f)
                raceScore += 150f
                boosts.removeAt(i)
            }
        }
    }

    private fun onCrash() {
        bestScore = max(bestScore, raceScore)
        prefs.edit()
            .putFloat("best_score", bestScore)
            .putInt("selected_car_index", selectedCarIndex)
            .apply()
        throttleInput = 0f
        brakeInput = 0f
        steeringInput = 0f
        crashFlash = 1f
        state = ScreenState.CRASHED
    }

    private fun drawFrame(canvas: Canvas?) {
        if (canvas == null) return
        try {
            drawBackground(canvas)
            when (state) {
                ScreenState.GARAGE -> drawGarage(canvas)
                ScreenState.RACING -> {
                    drawTrack(canvas)
                    drawWorldObjects(canvas)
                    drawPlayerCar(canvas)
                    drawRaceHud(canvas)
                    drawControls(canvas)
                }
                ScreenState.CRASHED -> {
                    drawTrack(canvas)
                    drawWorldObjects(canvas)
                    drawPlayerCar(canvas)
                    drawRaceHud(canvas)
                    drawCrashOverlay(canvas)
                }
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
            intArrayOf(Color.rgb(14, 28, 78), Color.rgb(5, 8, 22), Color.BLACK),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, backgroundPaint)

        stars.forEach { star ->
            val scale = 1f / (star[2] * 0.15f)
            val x = viewportWidth * 0.5f + star[0] * viewportWidth * 0.55f * scale
            val y = horizonY * (0.2f + star[1] * 1.45f)
            val radius = max(1.3f, 3.6f * scale)
            val alpha = (120 + scale * 120).toInt().coerceIn(80, 255)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 223, 233, 255)
            }
            canvas.drawCircle(x, y, radius, paint)
        }

        val buildingWidth = viewportWidth / 10f
        for (i in -1..10) {
            val left = i * buildingWidth - (roadAnimation * 5f % buildingWidth)
            val heightFactor = 0.18f + (i.mod(4)) * 0.085f
            canvas.drawRoundRect(
                RectF(left, horizonY - viewportHeight * heightFactor, left + buildingWidth * 0.76f, horizonY),
                14f,
                14f,
                cityPaint,
            )
        }
    }

    private fun drawTrack(canvas: Canvas) {
        val path = Path().apply {
            moveTo(viewportWidth * 0.39f, horizonY)
            lineTo(viewportWidth * 0.61f, horizonY)
            lineTo(viewportWidth * 0.91f, roadBottomY)
            lineTo(viewportWidth * 0.09f, roadBottomY)
            close()
        }
        canvas.drawPath(path, roadPaint)

        val shoulderWidth = viewportWidth * 0.018f
        canvas.drawRect(viewportWidth * 0.09f, roadBottomY - 8f, viewportWidth * 0.09f + shoulderWidth, roadBottomY, shoulderPaint)
        canvas.drawRect(viewportWidth * 0.91f - shoulderWidth, roadBottomY - 8f, viewportWidth * 0.91f, roadBottomY, shoulderPaint)

        for (segment in 0..15) {
            val depth = ((segment + roadAnimation) % 16f) / 3.4f + 0.35f
            val nextDepth = depth + 0.18f
            for (laneOffset in listOf(-0.34f, 0.34f)) {
                val left = projectedRoadX(laneOffset, depth)
                val leftNext = projectedRoadX(laneOffset, nextDepth)
                lanePaint.strokeWidth = max(3f, 14f / (depth + 0.2f))
                canvas.drawLine(left, projectedRoadY(depth), leftNext, projectedRoadY(nextDepth), lanePaint)
            }
        }
    }

    private fun drawWorldObjects(canvas: Canvas) {
        boosts.sortedByDescending { it.distance }.forEach { drawBoost(canvas, it) }
        opponents.sortedByDescending { it.distance }.forEach { drawOpponent(canvas, it) }
    }

    private fun drawBoost(canvas: Canvas, boost: BoostRing) {
        val x = projectedRoadX(boost.lane, boost.distance)
        val y = projectedRoadY(boost.distance)
        val scale = projectedScale(boost.distance)
        val radius = viewportWidth * 0.11f * scale * (1f + 0.08f * sin(boost.pulse))
        val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(110, 155, 255, 110)
        }
        canvas.drawCircle(x, y - radius * 0.4f, radius * 1.25f, glow)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = max(4f, radius * 0.2f)
            color = boostPaint.color
        }
        canvas.drawCircle(x, y - radius * 0.4f, radius, ringPaint)
    }

    private fun drawOpponent(canvas: Canvas, car: OpponentCar) {
        val x = projectedRoadX(car.lane, car.distance)
        val y = projectedRoadY(car.distance)
        val scale = projectedScale(car.distance)
        val width = viewportWidth * 0.22f * scale * car.spec.mass
        val height = viewportHeight * 0.17f * scale
        val rect = RectF(x - width / 2f, y - height, x + width / 2f, y)

        carBodyPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            car.spec.accent,
            car.spec.accentDark,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, 20f, 20f, carBodyPaint)

        glassPaint.alpha = 200
        canvas.drawRoundRect(
            RectF(rect.left + width * 0.18f, rect.top + height * 0.14f, rect.right - width * 0.18f, rect.top + height * 0.45f),
            16f,
            16f,
            glassPaint,
        )
    }

    private fun drawPlayerCar(canvas: Canvas) {
        val spec = garage[selectedCarIndex]
        val carWidth = viewportWidth * (0.18f + spec.mass * 0.03f)
        val carHeight = viewportHeight * 0.12f
        val x = viewportWidth * 0.5f + playerLanePosition * viewportWidth * 0.18f
        val y = viewportHeight * 0.84f
        val lean = playerHeading * viewportWidth * 0.025f
        val rect = RectF(x - carWidth / 2f + lean, y - carHeight, x + carWidth / 2f + lean, y)

        canvas.drawOval(RectF(rect.left - 24f, rect.bottom - 20f, rect.right + 24f, rect.bottom + 18f), ghostPaint)

        carBodyPaint.shader = LinearGradient(
            rect.left,
            rect.top,
            rect.right,
            rect.bottom,
            spec.accent,
            spec.accentDark,
            Shader.TileMode.CLAMP,
        )
        canvas.drawRoundRect(rect, 34f, 34f, carBodyPaint)

        canvas.drawRoundRect(
            RectF(rect.left + carWidth * 0.18f, rect.top + carHeight * 0.14f, rect.right - carWidth * 0.18f, rect.top + carHeight * 0.46f),
            22f,
            22f,
            glassPaint,
        )

        if (crashFlash > 0f) {
            val flashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb((crashFlash * 160).toInt().coerceIn(0, 160), 255, 255, 255)
            }
            canvas.drawRoundRect(rect, 34f, 34f, flashPaint)
        }
    }

    private fun drawRaceHud(canvas: Canvas) {
        val hudRect = RectF(28f, 34f, viewportWidth - 28f, 236f)
        canvas.drawRoundRect(hudRect, 36f, 36f, panelPaint)
        canvas.drawRoundRect(hudRect, 36f, 36f, panelStrokePaint)

        canvas.drawText("CAR", 56f, 86f, labelPaint)
        canvas.drawText(garage[selectedCarIndex].name, 56f, 146f, textPaint)
        canvas.drawText("KM/H", viewportWidth * 0.55f, 86f, labelPaint)
        canvas.drawText((playerSpeed * 3.6f).toInt().toString(), viewportWidth * 0.55f, 146f, textPaint)
        canvas.drawText("BEST", viewportWidth * 0.79f, 86f, labelPaint)
        canvas.drawText(bestScore.toInt().toString(), viewportWidth * 0.79f, 146f, textPaint)

        drawStatBar(canvas, "RPM", engineRpm / 8100f, 56f, 174f, viewportWidth * 0.34f, Color.rgb(113, 216, 255))
        drawStatBar(canvas, "NITRO", nitroCharge, viewportWidth * 0.42f, 174f, viewportWidth * 0.24f, Color.rgb(162, 255, 113))
        drawStatBar(canvas, "GRIP", garage[selectedCarIndex].grip / 1.2f, viewportWidth * 0.69f, 174f, viewportWidth * 0.18f, Color.rgb(255, 158, 108))

        val miniRect = RectF(viewportWidth - 180f, 260f, viewportWidth - 40f, 420f)
        canvas.drawRoundRect(miniRect, 30f, 30f, panelPaint)
        canvas.drawRoundRect(miniRect, 30f, 30f, panelStrokePaint)
        val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(120, 131, 204, 255) }
        canvas.drawLine(miniRect.centerX(), miniRect.top + 18f, miniRect.centerX(), miniRect.bottom - 18f, mapPaint)
        val playerMarkerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = garage[selectedCarIndex].accent }
        canvas.drawCircle(miniRect.centerX() + playerLanePosition * 28f, miniRect.bottom - 28f, 12f, playerMarkerPaint)
        opponents.take(4).forEach {
            val markerY = (miniRect.bottom - 28f - it.distance * 1.1f).coerceIn(miniRect.top + 20f, miniRect.bottom - 30f)
            canvas.drawCircle(miniRect.centerX() + it.lane * 28f, markerY, 9f, brakePaint)
        }
    }

    private fun drawControls(canvas: Canvas) {
        val steeringRect = RectF(32f, viewportHeight - 270f, viewportWidth * 0.52f, viewportHeight - 44f)
        val throttleRect = RectF(viewportWidth * 0.68f, viewportHeight - 300f, viewportWidth - 32f, viewportHeight - 170f)
        val brakeRect = RectF(viewportWidth * 0.68f, viewportHeight - 160f, viewportWidth - 32f, viewportHeight - 44f)

        canvas.drawRoundRect(steeringRect, 40f, 40f, controlPaint)
        canvas.drawRoundRect(steeringRect, 40f, 40f, controlStrokePaint)
        canvas.drawRoundRect(throttleRect, 34f, 34f, controlPaint)
        canvas.drawRoundRect(throttleRect, 34f, 34f, controlStrokePaint)
        canvas.drawRoundRect(brakeRect, 34f, 34f, controlPaint)
        canvas.drawRoundRect(brakeRect, 34f, 34f, controlStrokePaint)

        val centerX = steeringRect.centerX() + steeringInput * steeringRect.width() * 0.34f
        val padPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = garage[selectedCarIndex].accent
        }
        canvas.drawCircle(centerX, steeringRect.centerY(), steeringRect.height() * 0.22f, padPaint)
        canvas.drawText("STEER", steeringRect.centerX(), steeringRect.top + 56f, buttonTextPaint)
        canvas.drawText("THROTTLE", throttleRect.centerX(), throttleRect.centerY() + 18f, buttonTextPaint)
        canvas.drawText("BRAKE", brakeRect.centerX(), brakeRect.centerY() + 18f, buttonTextPaint)
    }

    private fun drawGarage(canvas: Canvas) {
        val titlePaint = Paint(textPaint).apply {
            textAlign = Paint.Align.CENTER
            textSize = viewportWidth * 0.12f
        }
        val subPaint = Paint(labelPaint).apply {
            textAlign = Paint.Align.CENTER
            textSize = viewportWidth * 0.045f
        }
        canvas.drawText("NEON SPRINT", viewportWidth / 2f, viewportHeight * 0.1f, titlePaint)
        canvas.drawText("ガレージでマシンを選んでレース開始", viewportWidth / 2f, viewportHeight * 0.15f, subPaint)

        garage.forEachIndexed { index, spec ->
            val rect = garageCardRect(index)
            val active = index == selectedCarIndex
            val fillPaint = Paint(panelPaint).apply {
                color = if (active) Color.argb(220, 12, 20, 48) else Color.argb(175, 7, 10, 28)
            }
            val outline = Paint(panelStrokePaint).apply {
                color = if (active) spec.accent else panelStrokePaint.color
                strokeWidth = if (active) 4f else 3f
            }
            canvas.drawRoundRect(rect, 28f, 28f, fillPaint)
            canvas.drawRoundRect(rect, 28f, 28f, outline)

            val carRect = RectF(rect.left + 22f, rect.top + 18f, rect.left + 180f, rect.bottom - 18f)
            carBodyPaint.shader = LinearGradient(carRect.left, carRect.top, carRect.right, carRect.bottom, spec.accent, spec.accentDark, Shader.TileMode.CLAMP)
            canvas.drawRoundRect(carRect, 22f, 22f, carBodyPaint)
            canvas.drawText(spec.name, rect.left + 212f, rect.top + 54f, textPaint)
            val statsPaint = Paint(labelPaint).apply { textSize = viewportWidth * 0.026f }
            canvas.drawText("TOP ${spec.topSpeed.toInt()}  ACC ${spec.acceleration.toInt()}  GRIP ${(spec.grip * 100).toInt()}", rect.left + 212f, rect.top + 92f, statsPaint)
        }

        val detailRect = RectF(viewportWidth * 0.08f, viewportHeight * 0.79f, viewportWidth * 0.92f, viewportHeight * 0.85f)
        canvas.drawRoundRect(detailRect, 28f, 28f, panelPaint)
        canvas.drawRoundRect(detailRect, 28f, 28f, panelStrokePaint)
        val spec = garage[selectedCarIndex]
        drawGarageBar(canvas, "Top Speed", spec.topSpeed / 110f, detailRect.left + 24f, detailRect.top + 28f, viewportWidth * 0.22f, spec.accent)
        drawGarageBar(canvas, "Accel", spec.acceleration / 36f, detailRect.left + 290f, detailRect.top + 28f, viewportWidth * 0.18f, spec.accent)
        drawGarageBar(canvas, "Grip", spec.grip / 1.2f, detailRect.left + 520f, detailRect.top + 28f, viewportWidth * 0.16f, spec.accent)

        val startRect = RectF(viewportWidth * 0.12f, viewportHeight * 0.86f, viewportWidth * 0.88f, viewportHeight * 0.94f)
        val startPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(startRect.left, startRect.top, startRect.right, startRect.bottom, spec.accent, spec.accentDark, Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(startRect, 34f, 34f, startPaint)
        canvas.drawText("START RACE", startRect.centerX(), startRect.centerY() + 18f, buttonTextPaint)
    }

    private fun drawCrashOverlay(canvas: Canvas) {
        val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb((170 + crashFlash * 50).toInt().coerceIn(0, 220), 4, 8, 24)
        }
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, overlayPaint)
        val panel = RectF(viewportWidth * 0.1f, viewportHeight * 0.22f, viewportWidth * 0.9f, viewportHeight * 0.58f)
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
        canvas.drawText("RACE OVER", viewportWidth / 2f, panel.top + 120f, titlePaint)
        canvas.drawText(garage[selectedCarIndex].name, viewportWidth / 2f, panel.top + 190f, bodyPaint)
        canvas.drawText("SCORE ${raceScore.toInt()}", viewportWidth / 2f, panel.top + 280f, titlePaint)
        canvas.drawText("BEST ${bestScore.toInt()}", viewportWidth / 2f, panel.top + 350f, bodyPaint)

        drawOverlayButton(canvas, UiButton("RETRY", RectF(viewportWidth * 0.12f, viewportHeight * 0.66f, viewportWidth * 0.88f, viewportHeight * 0.74f)), garage[selectedCarIndex].accent)
        drawOverlayButton(canvas, UiButton("GARAGE", RectF(viewportWidth * 0.12f, viewportHeight * 0.77f, viewportWidth * 0.88f, viewportHeight * 0.85f)), Color.rgb(94, 122, 220))
    }

    private fun drawOverlayButton(canvas: Canvas, button: UiButton, color: Int) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(button.rect.left, button.rect.top, button.rect.right, button.rect.bottom, color, Color.rgb(21, 35, 92), Shader.TileMode.CLAMP)
        }
        canvas.drawRoundRect(button.rect, 34f, 34f, fill)
        canvas.drawText(button.label, button.rect.centerX(), button.rect.centerY() + 18f, buttonTextPaint)
    }

    private fun drawStatBar(canvas: Canvas, label: String, value: Float, left: Float, top: Float, width: Float, color: Int) {
        val barRect = RectF(left, top + 14f, left + width, top + 34f)
        val labelPaintLocal = Paint(labelPaint).apply { textSize = viewportWidth * 0.024f }
        canvas.drawText(label, left, top, labelPaintLocal)
        canvas.drawRoundRect(barRect, 12f, 12f, barBgPaint)
        barFillPaint.color = color
        canvas.drawRoundRect(RectF(barRect.left, barRect.top, barRect.left + barRect.width() * value.coerceIn(0f, 1f), barRect.bottom), 12f, 12f, barFillPaint)
    }

    private fun drawGarageBar(canvas: Canvas, label: String, value: Float, left: Float, top: Float, width: Float, color: Int) {
        val statPaint = Paint(labelPaint).apply { textSize = viewportWidth * 0.023f }
        canvas.drawText(label, left, top, statPaint)
        val bar = RectF(left, top + 12f, left + width, top + 28f)
        canvas.drawRoundRect(bar, 10f, 10f, barBgPaint)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        canvas.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * value.coerceIn(0f, 1f), bar.bottom), 10f, 10f, fill)
    }

    private fun garageCardRect(index: Int): RectF {
        val columns = 2
        val cardWidth = viewportWidth * 0.38f
        val cardHeight = viewportHeight * 0.11f
        val gapX = viewportWidth * 0.06f
        val gapY = viewportHeight * 0.018f
        val startX = viewportWidth * 0.09f
        val startY = viewportHeight * 0.23f
        val col = index % columns
        val row = index / columns
        val left = startX + col * (cardWidth + gapX)
        val top = startY + row * (cardHeight + gapY)
        return RectF(left, top, left + cardWidth, top + cardHeight)
    }

    private fun projectedScale(distance: Float): Float {
        return 1.8f / (distance * 0.035f + 1.1f)
    }

    private fun projectedRoadX(laneOffset: Float, distance: Float): Float {
        val t = (1f / (distance * 0.025f + 1f)).coerceIn(0f, 1f)
        val roadWidthAtDepth = roadTopWidth + (roadBottomWidth - roadTopWidth) * t
        return viewportWidth * 0.5f + laneOffset * roadWidthAtDepth * 0.32f
    }

    private fun projectedRoadY(distance: Float): Float {
        val t = (1f / (distance * 0.025f + 1f)).coerceIn(0f, 1f)
        return horizonY + (roadBottomY - horizonY) * t
    }
}
