package com.codex.neonsprint

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

class RacingGameView(context: Context) : SurfaceView(context), Runnable {
    private val rng = Random.Default
    private val holderRef: SurfaceHolder = holder
    private val prefs = context.getSharedPreferences("neon_sprint", Context.MODE_PRIVATE)
    private val shaderCache = ShaderCache()
    private val audioController = AudioController()

    @Volatile
    private var running = false
    private var gameThread: Thread? = null
    private var lastFrameTime = System.nanoTime()

    private enum class ScreenState { GARAGE, OPTIONS, RACING, CRASHED }

    private data class OpponentCar(
        var lane: Float,
        var distance: Float,
        var speed: Float,
        var sway: Float,
        var laneTarget: Float,
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
    private val courses = neonCourses
    private var options = GameOptions.load(prefs)
    private var selectedCarIndex = prefs.getInt("selected_car_index", 0).coerceIn(0, garage.lastIndex)
    private var selectedCourseIndex = prefs.getInt("selected_course_index", 0).coerceIn(0, courses.lastIndex)
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
    private var roadCurve = 0f
    private var crashFlash = 0f
    private var collisionCooldown = 0f

    private var throttleInput = 0f
    private var brakeInput = 0f
    private var steeringInput = 0f
    private var steeringTouchId = -1
    private var pedalTouchId = -1

    private val opponents = mutableListOf<OpponentCar>()
    private val boosts = mutableListOf<BoostRing>()
    private var opponentSpawnTimer = 0f
    private var boostSpawnTimer = 1.4f
    private val stars = MutableList(96) {
        floatArrayOf(
            rng.nextFloat() * 2f - 1f,
            rng.nextFloat(),
            rng.nextFloat() * 30f + 2f,
        )
    }

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG)
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
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barBgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val barFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cardOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val miniMapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerTitlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerBodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leftStatPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val leftDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isFocusable = true
        keepScreenOn = true

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
        starPaint.color = Color.WHITE
        ringPaint.style = Paint.Style.STROKE
        centerTitlePaint.color = Color.WHITE
        centerTitlePaint.isFakeBoldText = true
        centerTitlePaint.textAlign = Paint.Align.CENTER
        centerBodyPaint.color = labelPaint.color
        centerBodyPaint.textAlign = Paint.Align.CENTER
        leftStatPaint.color = labelPaint.color
        leftDetailPaint.color = Color.WHITE
        audioController.applyOptions(options)
        applyCoursePalette(currentCourse())
    }

    override fun run() {
        while (running) {
            if (!holderRef.surface.isValid) continue
            val now = System.nanoTime()
            val rawDelta = ((now - lastFrameTime) / 1_000_000_000f).coerceAtMost(0.033f)
            lastFrameTime = now
            update(rawDelta.coerceAtLeast(0.001f))
            drawFrame(holderRef.lockCanvas())
        }
    }

    fun resume() {
        if (running) return
        running = true
        lastFrameTime = System.nanoTime()
        audioController.applyOptions(options)
        gameThread = Thread(this).also { it.start() }
    }

    fun pause() {
        running = false
        try {
            gameThread?.join(500)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        gameThread = null
        audioController.release()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_POINTER_DOWN -> handlePointerDown(event)
            MotionEvent.ACTION_MOVE -> handlePointerMove(event)
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_POINTER_UP -> handlePointerUp(event)
            MotionEvent.ACTION_CANCEL -> clearTouchControls()
        }
        return true
    }

    private fun handlePointerDown(event: MotionEvent) {
        val index = event.actionIndex
        val pointerId = event.getPointerId(index)
        val x = event.getX(index)
        val y = event.getY(index)
        when (state) {
            ScreenState.GARAGE -> if (handleGarageTap(x, y)) return
            ScreenState.OPTIONS -> if (handleOptionsTap(x, y)) return
            ScreenState.CRASHED -> if (handleCrashTap(x, y)) return
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
            if (pointerId == steeringTouchId) steeringInput = steeringForX(x)
            if (pointerId == pedalTouchId) updatePedals(x, y)
        }
    }

    private fun handlePointerUp(event: MotionEvent) {
        val pointerId = event.getPointerId(event.actionIndex)
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

    private fun clearTouchControls() {
        steeringTouchId = -1
        pedalTouchId = -1
        steeringInput = 0f
        throttleInput = 0f
        brakeInput = 0f
    }

    private fun steeringForX(x: Float): Float {
        val center = viewportWidth * 0.275f
        return ((x - center) / max(1f, viewportWidth * 0.22f)).coerceIn(-1f, 1f)
    }

    private fun updatePedals(x: Float, y: Float) {
        throttleInput = 0f
        brakeInput = 0f
        if (x < viewportWidth * 0.55f) return
        if (y < viewportHeight * 0.72f) throttleInput = 1f else brakeInput = 1f
    }

    private fun handleGarageTap(x: Float, y: Float): Boolean {
        garage.forEachIndexed { index, _ ->
            if (garageCardRect(index).contains(x, y)) {
                selectedCarIndex = index
                prefs.edit().putInt("selected_car_index", selectedCarIndex).apply()
                audioController.playMenu()
                return true
            }
        }
        courses.forEachIndexed { index, _ ->
            if (courseCardRect(index).contains(x, y)) {
                selectedCourseIndex = index
                prefs.edit().putInt("selected_course_index", selectedCourseIndex).apply()
                shaderCache.clear()
                applyCoursePalette(currentCourse())
                audioController.playMenu()
                return true
            }
        }
        return when {
            optionsButtonRect().contains(x, y) -> {
                state = ScreenState.OPTIONS
                audioController.playMenu()
                true
            }
            startButtonRect().contains(x, y) -> {
                audioController.playMenu()
                startRace()
                true
            }
            else -> false
        }
    }

    private fun handleOptionsTap(x: Float, y: Float): Boolean {
        return when {
            graphicsOptionRect().contains(x, y) -> {
                options = options.nextGraphicsQuality()
                persistOptions()
                true
            }
            shaderCacheOptionRect().contains(x, y) -> {
                options = options.toggleShaderCache()
                shaderCache.clear()
                persistOptions()
                true
            }
            soundOptionRect().contains(x, y) -> {
                options = options.toggleSound()
                persistOptions()
                true
            }
            volumeOptionRect().contains(x, y) -> {
                options = options.nextVolumeStep()
                persistOptions()
                true
            }
            optionsBackButtonRect().contains(x, y) -> {
                state = ScreenState.GARAGE
                audioController.playMenu()
                true
            }
            else -> false
        }
    }

    private fun persistOptions() {
        options.save(prefs)
        audioController.applyOptions(options)
        shaderCache.clear()
        audioController.playMenu()
    }

    private fun handleCrashTap(x: Float, y: Float): Boolean {
        return when {
            retryButtonRect().contains(x, y) -> {
                audioController.playMenu()
                startRace(); true
            }
            garageButtonRect().contains(x, y) -> {
                state = ScreenState.GARAGE
                crashFlash = 0f
                clearTouchControls()
                audioController.playMenu()
                true
            }
            crashOptionsButtonRect().contains(x, y) -> {
                state = ScreenState.OPTIONS
                audioController.playMenu()
                true
            }
            else -> false
        }
    }

    private fun startRace() {
        state = ScreenState.RACING
        audioController.applyOptions(options)
        applyCoursePalette(currentCourse())
        playerLanePosition = 0f
        playerVelocityX = 0f
        playerHeading = 0f
        playerSpeed = currentCar().topSpeed * 0.24f
        engineRpm = 2200f
        nitroCharge = 0.35f
        distanceTravelled = 0f
        raceScore = 0f
        elapsedTime = 0f
        roadAnimation = 0f
        roadCurve = 0f
        crashFlash = 0f
        collisionCooldown = 0f
        clearTouchControls()
        opponents.clear()
        boosts.clear()
        opponentSpawnTimer = 0.9f
        boostSpawnTimer = 1.5f
    }

    private fun update(delta: Float) {
        viewportWidth = width.toFloat().coerceAtLeast(1f)
        viewportHeight = height.toFloat().coerceAtLeast(1f)
        if (viewportWidth <= 1f || viewportHeight <= 1f) return
        roadBottomWidth = viewportWidth * 0.82f
        roadTopWidth = viewportWidth * 0.24f
        horizonY = viewportHeight * 0.22f
        roadBottomY = viewportHeight * 0.95f
        textPaint.textSize = viewportWidth * 0.065f
        labelPaint.textSize = viewportWidth * 0.028f
        buttonTextPaint.textSize = viewportWidth * 0.055f

        updateStars(delta)

        if (state != ScreenState.RACING) {
            crashFlash = max(0f, crashFlash - delta * 1.8f)
            return
        }

        val spec = currentCar()
        val course = currentCourse()
        elapsedTime += delta
        roadAnimation = (roadAnimation + delta * playerSpeed * 0.12f) % 16f
        collisionCooldown = max(0f, collisionCooldown - delta)

        val curveTarget = sin(elapsedTime * (0.45f + course.curvature) + distanceTravelled * 0.006f) * course.curvature
        roadCurve += (curveTarget - roadCurve) * min(1f, delta * 1.5f)

        val steeringTarget = (steeringInput - roadCurve * 0.9f) * spec.handling
        playerHeading += (steeringTarget - playerHeading) * min(1f, delta * (4.4f + spec.handling))

        val engineForce = throttleInput * spec.acceleration * (1f - playerSpeed / spec.topSpeed).coerceAtLeast(0.1f)
        val brakeForce = brakeInput * spec.braking
        val aeroDrag = 0.0145f * playerSpeed * playerSpeed / spec.mass * course.dragMultiplier
        val rollingResistance = 1.45f + abs(playerHeading) * (5.1f - spec.grip)
        val longitudinalAccel = engineForce - brakeForce - aeroDrag - rollingResistance
        playerSpeed = safeValue(playerSpeed + longitudinalAccel * delta).coerceIn(0f, spec.topSpeed)

        if (throttleInput > 0f && nitroCharge > 0.02f && playerSpeed > spec.topSpeed * 0.5f) {
            nitroCharge = max(0f, nitroCharge - delta * 0.14f)
        } else {
            nitroCharge = min(1f, nitroCharge + delta * 0.04f)
        }

        val lateralGrip = spec.grip * course.gripMultiplier * 7.8f
        val driftFactor = 1f + spec.drift * 1.65f
        val lateralAccel = playerHeading * (13.5f + playerSpeed * 0.055f) - playerVelocityX * lateralGrip / driftFactor
        playerVelocityX = safeValue(playerVelocityX + lateralAccel * delta)
        playerVelocityX *= (1f - min(0.86f, delta * (2.75f - spec.drift * 0.7f)))
        playerLanePosition = safeValue(playerLanePosition + (playerVelocityX + roadCurve * 0.7f) * delta).coerceIn(-1.85f, 1.85f)

        if (abs(playerLanePosition) > 1.30f) {
            playerSpeed *= 1f - min(0.32f, delta * (2.5f + course.dragMultiplier))
            playerVelocityX *= 0.92f
            if (abs(playerLanePosition) > 1.62f) {
                onCrash()
                return
            }
        }

        engineRpm = (1100f + playerSpeed / max(1f, spec.topSpeed) * 7000f).coerceIn(900f, 8200f)
        distanceTravelled += playerSpeed * delta
        raceScore = max(raceScore, distanceTravelled * 14f + playerSpeed * 3.5f + nitroCharge * 35f)

        opponentSpawnTimer -= delta * course.trafficDensity
        boostSpawnTimer -= delta * course.boostFrequency
        if (opponentSpawnTimer <= 0f) {
            spawnOpponent(spec, course)
            opponentSpawnTimer = max(0.34f, 1.08f - playerSpeed / max(1f, spec.topSpeed) * 0.42f)
        }
        if (boostSpawnTimer <= 0f && boosts.size < options.graphicsQuality.maxBoosts) {
            boosts += BoostRing(
                lane = rng.nextFloat() * 2.1f - 1.05f,
                distance = 95f + rng.nextFloat() * 45f,
                pulse = rng.nextFloat() * 6f,
            )
            boostSpawnTimer = 2.1f + rng.nextFloat() * 1.8f
        }

        updateTraffic(delta, course)
        updateBoosts(delta, spec)
        trimObjects()
        crashFlash = max(0f, crashFlash - delta * 1.6f)
    }

    private fun updateStars(delta: Float) {
        val movement = if (state == ScreenState.RACING) max(0.7f, playerSpeed * 0.18f) else 0.7f
        val activeStars = min(options.graphicsQuality.starCount, stars.size)
        for (index in 0 until activeStars) {
            val star = stars[index]
            star[2] -= movement * delta
            if (star[2] < 1f) {
                star[0] = rng.nextFloat() * 2f - 1f
                star[1] = rng.nextFloat()
                star[2] = rng.nextFloat() * 30f + 12f
            }
        }
    }

    private fun spawnOpponent(playerSpec: CarSpec, course: CourseSpec) {
        if (opponents.size >= options.graphicsQuality.maxOpponents) return
        val spec = garage[rng.nextInt(garage.size)]
        val lane = laneBaseChoices[rng.nextInt(laneBaseChoices.size)] + rng.nextFloat() * 0.14f - 0.07f
        if (opponents.count { abs(it.lane - lane) < 0.24f && it.distance > 58f } >= 2) return
        opponents += OpponentCar(
            lane = lane,
            distance = 88f + rng.nextFloat() * 60f,
            speed = playerSpec.topSpeed * (0.38f + rng.nextFloat() * 0.28f) / course.dragMultiplier,
            sway = rng.nextFloat() * 8f,
            laneTarget = lane,
            spec = spec,
        )
    }

    private fun updateTraffic(delta: Float, course: CourseSpec) {
        for (i in opponents.indices.reversed()) {
            val car = opponents[i]
            car.distance -= (playerSpeed - car.speed) * delta
            car.sway += delta * (0.65f + car.spec.handling)
            if (rng.nextFloat() < 0.008f) {
                car.laneTarget = (car.laneTarget + laneShiftChoices[rng.nextInt(laneShiftChoices.size)]).coerceIn(-1.05f, 1.05f)
            }
            car.lane += (car.laneTarget - car.lane) * min(1f, delta * (0.8f + car.spec.handling))
            car.lane += sin(car.sway) * delta * 0.012f * (car.spec.drift + course.curvature)
            car.lane = car.lane.coerceIn(-1.15f, 1.15f)

            if (car.distance < -8f) {
                opponents.removeAt(i)
                continue
            }

            val widthAllowance = 0.26f + car.spec.mass * 0.05f
            val closeEnough = car.distance in 2.2f..9.5f
            val laneOverlap = abs(car.lane - playerLanePosition) < widthAllowance
            if (collisionCooldown <= 0f && closeEnough && laneOverlap) {
                val closingSpeed = max(0f, playerSpeed - car.speed)
                playerSpeed = max(6f, playerSpeed - (14f + closingSpeed * 0.22f))
                playerVelocityX += if (playerLanePosition >= car.lane) 0.85f else -0.85f
                collisionCooldown = 0.45f
                crashFlash = 0.75f
                if (closingSpeed > 16f || abs(playerHeading) > 0.46f || abs(playerLanePosition) > 1.28f) {
                    onCrash()
                    return
                }
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
                nitroCharge = min(1f, nitroCharge + 0.34f)
                playerSpeed = min(spec.topSpeed, playerSpeed + 8f)
                raceScore += 140f
                audioController.playBoost()
                boosts.removeAt(i)
            }
        }
    }

    private fun trimObjects() {
        if (opponents.size > options.graphicsQuality.maxOpponents) {
            opponents.subList(options.graphicsQuality.maxOpponents, opponents.size).clear()
        }
        if (boosts.size > options.graphicsQuality.maxBoosts) {
            boosts.subList(options.graphicsQuality.maxBoosts, boosts.size).clear()
        }
    }

    private fun onCrash() {
        if (state != ScreenState.RACING) return
        bestScore = max(bestScore, raceScore)
        prefs.edit()
            .putFloat("best_score", bestScore)
            .putInt("selected_car_index", selectedCarIndex)
            .putInt("selected_course_index", selectedCourseIndex)
            .apply()
        clearTouchControls()
        crashFlash = 1f
        audioController.playCrash()
        state = ScreenState.CRASHED
    }

    private fun drawFrame(canvas: Canvas?) {
        if (canvas == null) return
        try {
            drawBackground(canvas)
            when (state) {
                ScreenState.GARAGE -> drawGarage(canvas)
                ScreenState.OPTIONS -> drawOptions(canvas)
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
        val course = currentCourse()
        backgroundPaint.shader = shaderCache.linearGradient(
            enabled = options.shaderCacheEnabled,
            id = "background-${course.id}",
            left = 0f,
            top = 0f,
            right = 0f,
            bottom = viewportHeight,
            colors = intArrayOf(course.skyTop, course.skyBottom, Color.BLACK),
            positions = floatArrayOf(0f, 0.52f, 1f),
        )
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, backgroundPaint)

        horizonPaint.shader = shaderCache.linearGradient(
            enabled = options.shaderCacheEnabled,
            id = "horizon-${course.id}",
            left = 0f,
            top = horizonY - viewportHeight * 0.08f,
            right = 0f,
            bottom = horizonY + viewportHeight * 0.1f,
            colors = intArrayOf(course.horizonGlow, Color.TRANSPARENT),
        )
        canvas.drawRect(0f, horizonY - viewportHeight * 0.08f, viewportWidth, horizonY + viewportHeight * 0.1f, horizonPaint)

        val activeStars = min(options.graphicsQuality.starCount, stars.size)
        for (index in 0 until activeStars) {
            val star = stars[index]
            val scale = 1f / (star[2] * 0.15f)
            val x = viewportWidth * 0.5f + star[0] * viewportWidth * 0.55f * scale
            val y = horizonY * (0.2f + star[1] * 1.45f)
            val radius = max(1.2f, 3.4f * scale)
            starPaint.alpha = (120 + scale * 120).toInt().coerceIn(80, 255)
            canvas.drawCircle(x, y, radius, starPaint)
        }

        val buildingWidth = viewportWidth / max(6f, options.graphicsQuality.buildingCount.toFloat())
        for (i in -1..options.graphicsQuality.buildingCount) {
            val left = i * buildingWidth - (roadAnimation * 24f % buildingWidth) + roadCurve * viewportWidth * 0.12f
            val heightFactor = 0.18f + (i.mod(4)) * 0.085f
            canvas.drawRoundRect(RectF(left, horizonY - viewportHeight * heightFactor, left + buildingWidth * 0.76f, horizonY), 14f, 14f, cityPaint)
        }
    }

    private fun drawTrack(canvas: Canvas) {
        val centerShift = roadCurve * viewportWidth * 0.12f
        val path = Path().apply {
            moveTo(viewportWidth * 0.39f + centerShift * 0.3f, horizonY)
            lineTo(viewportWidth * 0.61f + centerShift * 0.3f, horizonY)
            lineTo(viewportWidth * 0.91f + centerShift, roadBottomY)
            lineTo(viewportWidth * 0.09f + centerShift, roadBottomY)
            close()
        }
        canvas.drawPath(path, roadPaint)

        val shoulderWidth = viewportWidth * 0.018f
        canvas.drawRect(viewportWidth * 0.09f + centerShift, roadBottomY - 8f, viewportWidth * 0.09f + centerShift + shoulderWidth, roadBottomY, shoulderPaint)
        canvas.drawRect(viewportWidth * 0.91f + centerShift - shoulderWidth, roadBottomY - 8f, viewportWidth * 0.91f + centerShift, roadBottomY, shoulderPaint)

        for (segment in 0..options.graphicsQuality.roadSegments) {
            val depth = ((segment + roadAnimation) % 16f) / 3.4f + 0.35f
            val nextDepth = depth + 0.18f
            for (laneOffset in laneOffsets) {
                lanePaint.strokeWidth = max(3f, 14f / (depth + 0.2f))
                canvas.drawLine(projectedRoadX(laneOffset, depth), projectedRoadY(depth), projectedRoadX(laneOffset, nextDepth), projectedRoadY(nextDepth), lanePaint)
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
        glowPaint.color = Color.argb(110, 155, 255, 110)
        canvas.drawCircle(x, y - radius * 0.4f, radius * 1.25f, glowPaint)
        ringPaint.strokeWidth = max(4f, radius * 0.2f)
        ringPaint.color = boostPaint.color
        canvas.drawCircle(x, y - radius * 0.4f, radius, ringPaint)
    }

    private fun drawOpponent(canvas: Canvas, car: OpponentCar) {
        val x = projectedRoadX(car.lane, car.distance)
        val y = projectedRoadY(car.distance)
        val scale = projectedScale(car.distance)
        val width = viewportWidth * 0.22f * scale * car.spec.mass
        val height = viewportHeight * 0.17f * scale
        val rect = RectF(x - width / 2f, y - height, x + width / 2f, y)

        carBodyPaint.shader = shaderCache.linearGradient(
            enabled = options.shaderCacheEnabled,
            id = "opponent-${car.spec.id}-${width.toInt()}-${height.toInt()}",
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            colors = intArrayOf(car.spec.accent, car.spec.accentDark),
        )
        canvas.drawRoundRect(rect, 20f, 20f, carBodyPaint)
        glassPaint.alpha = 200
        canvas.drawRoundRect(RectF(rect.left + width * 0.18f, rect.top + height * 0.14f, rect.right - width * 0.18f, rect.top + height * 0.45f), 16f, 16f, glassPaint)
    }

    private fun drawPlayerCar(canvas: Canvas) {
        val spec = currentCar()
        val carWidth = viewportWidth * (0.18f + spec.mass * 0.03f)
        val carHeight = viewportHeight * 0.12f
        val x = viewportWidth * 0.5f + playerLanePosition * viewportWidth * 0.18f + roadCurve * viewportWidth * 0.03f
        val y = viewportHeight * 0.84f
        val lean = playerHeading * viewportWidth * 0.025f
        val rect = RectF(x - carWidth / 2f + lean, y - carHeight, x + carWidth / 2f + lean, y)

        canvas.drawOval(RectF(rect.left - 24f, rect.bottom - 20f, rect.right + 24f, rect.bottom + 18f), ghostPaint)
        carBodyPaint.shader = shaderCache.linearGradient(
            enabled = options.shaderCacheEnabled,
            id = "player-${spec.id}",
            left = rect.left,
            top = rect.top,
            right = rect.right,
            bottom = rect.bottom,
            colors = intArrayOf(spec.accent, spec.accentDark),
        )
        canvas.drawRoundRect(rect, 34f, 34f, carBodyPaint)
        canvas.drawRoundRect(RectF(rect.left + carWidth * 0.18f, rect.top + carHeight * 0.14f, rect.right - carWidth * 0.18f, rect.top + carHeight * 0.46f), 22f, 22f, glassPaint)

        if (crashFlash > 0f) {
            overlayPaint.color = Color.argb((crashFlash * 160).toInt().coerceIn(0, 160), 255, 255, 255)
            canvas.drawRoundRect(rect, 34f, 34f, overlayPaint)
        }
    }

    private fun drawRaceHud(canvas: Canvas) {
        val hudRect = RectF(28f, 34f, viewportWidth - 28f, 252f)
        canvas.drawRoundRect(hudRect, 36f, 36f, panelPaint)
        canvas.drawRoundRect(hudRect, 36f, 36f, panelStrokePaint)

        canvas.drawText("CAR", 56f, 84f, labelPaint)
        canvas.drawText(currentCar().name, 56f, 142f, textPaint)
        canvas.drawText("COURSE", viewportWidth * 0.36f, 84f, labelPaint)
        canvas.drawText(currentCourse().name, viewportWidth * 0.36f, 142f, textPaint)
        canvas.drawText("KM/H", viewportWidth * 0.68f, 84f, labelPaint)
        canvas.drawText((playerSpeed * 3.6f).toInt().toString(), viewportWidth * 0.68f, 142f, textPaint)
        canvas.drawText("BEST", viewportWidth * 0.84f, 84f, labelPaint)
        canvas.drawText(bestScore.toInt().toString(), viewportWidth * 0.84f, 142f, textPaint)

        drawStatBar(canvas, "RPM", engineRpm / 8200f, 56f, 176f, viewportWidth * 0.22f, Color.rgb(113, 216, 255))
        drawStatBar(canvas, "NITRO", nitroCharge, viewportWidth * 0.31f, 176f, viewportWidth * 0.18f, Color.rgb(162, 255, 113))
        drawStatBar(canvas, "GRIP", currentCar().grip * currentCourse().gripMultiplier / 1.25f, viewportWidth * 0.54f, 176f, viewportWidth * 0.14f, Color.rgb(255, 158, 108))
        drawStatBar(canvas, "CURVE", abs(roadCurve) / 0.35f, viewportWidth * 0.73f, 176f, viewportWidth * 0.12f, currentCourse().horizonGlow)

        val miniRect = RectF(viewportWidth - 190f, 272f, viewportWidth - 40f, 438f)
        canvas.drawRoundRect(miniRect, 30f, 30f, panelPaint)
        canvas.drawRoundRect(miniRect, 30f, 30f, panelStrokePaint)
        miniMapPaint.color = currentCourse().laneColor
        canvas.drawLine(miniRect.centerX(), miniRect.top + 18f, miniRect.centerX(), miniRect.bottom - 18f, miniMapPaint)
        markerPaint.color = currentCar().accent
        canvas.drawCircle(miniRect.centerX() + playerLanePosition * 28f, miniRect.bottom - 28f, 12f, markerPaint)
        opponents.take(options.graphicsQuality.minimapCars).forEach {
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

        markerPaint.color = currentCar().accent
        canvas.drawCircle(steeringRect.centerX() + steeringInput * steeringRect.width() * 0.34f, steeringRect.centerY(), steeringRect.height() * 0.22f, markerPaint)
        canvas.drawText("STEER", steeringRect.centerX(), steeringRect.top + 56f, buttonTextPaint)
        canvas.drawText("THROTTLE", throttleRect.centerX(), throttleRect.centerY() + 18f, buttonTextPaint)
        canvas.drawText("BRAKE", brakeRect.centerX(), brakeRect.centerY() + 18f, buttonTextPaint)
    }

    private fun drawGarage(canvas: Canvas) {
        centerTitlePaint.textSize = viewportWidth * 0.12f
        centerBodyPaint.textSize = viewportWidth * 0.045f
        canvas.drawText("NEON SPRINT", viewportWidth / 2f, viewportHeight * 0.1f, centerTitlePaint)
        canvas.drawText("マシンとコースを選んで安定した走りを作る", viewportWidth / 2f, viewportHeight * 0.15f, centerBodyPaint)

        garage.forEachIndexed { index, spec ->
            val rect = garageCardRect(index)
            drawSelectableCard(canvas, rect, index == selectedCarIndex, spec.accent)
            val carRect = RectF(rect.left + 22f, rect.top + 18f, rect.left + 180f, rect.bottom - 18f)
            carBodyPaint.shader = shaderCache.linearGradient(
                enabled = options.shaderCacheEnabled,
                id = "garage-car-${spec.id}",
                left = carRect.left,
                top = carRect.top,
                right = carRect.right,
                bottom = carRect.bottom,
                colors = intArrayOf(spec.accent, spec.accentDark),
            )
            canvas.drawRoundRect(carRect, 22f, 22f, carBodyPaint)
            canvas.drawText(spec.name, rect.left + 212f, rect.top + 54f, textPaint)
            leftStatPaint.textSize = viewportWidth * 0.026f
            leftStatPaint.textAlign = Paint.Align.LEFT
            canvas.drawText("TOP ${spec.topSpeed.toInt()}  ACC ${spec.acceleration.toInt()}  GRIP ${(spec.grip * 100).toInt()}", rect.left + 212f, rect.top + 92f, leftStatPaint)
        }

        leftStatPaint.textSize = viewportWidth * 0.034f
        canvas.drawText("COURSES", viewportWidth * 0.08f, viewportHeight * 0.67f, leftStatPaint)
        courses.forEachIndexed { index, course ->
            val rect = courseCardRect(index)
            drawSelectableCard(canvas, rect, index == selectedCourseIndex, course.horizonGlow)
            leftDetailPaint.textSize = viewportWidth * 0.03f
            canvas.drawText(course.name, rect.left + 24f, rect.top + 44f, leftDetailPaint)
            leftStatPaint.textSize = viewportWidth * 0.022f
            canvas.drawText("Grip ${(course.gripMultiplier * 100).toInt()}  Curve ${(course.curvature * 100).toInt()}", rect.left + 24f, rect.top + 78f, leftStatPaint)
        }

        val detailRect = RectF(viewportWidth * 0.08f, viewportHeight * 0.79f, viewportWidth * 0.92f, viewportHeight * 0.85f)
        canvas.drawRoundRect(detailRect, 28f, 28f, panelPaint)
        canvas.drawRoundRect(detailRect, 28f, 28f, panelStrokePaint)
        drawGarageBar(canvas, "Top Speed", currentCar().topSpeed / 110f, detailRect.left + 24f, detailRect.top + 28f, viewportWidth * 0.18f, currentCar().accent)
        drawGarageBar(canvas, "Accel", currentCar().acceleration / 36f, detailRect.left + 230f, detailRect.top + 28f, viewportWidth * 0.14f, currentCar().accent)
        drawGarageBar(canvas, "Grip", currentCar().grip * currentCourse().gripMultiplier / 1.25f, detailRect.left + 410f, detailRect.top + 28f, viewportWidth * 0.14f, currentCourse().horizonGlow)
        drawGarageBar(canvas, "Curve", currentCourse().curvature / 0.35f, detailRect.left + 590f, detailRect.top + 28f, viewportWidth * 0.12f, currentCourse().shoulderColor)

        drawOverlayButton(canvas, UiButton("START", startButtonRect()), currentCar().accent, currentCourse().horizonGlow)
        drawOverlayButton(canvas, UiButton("OPTIONS", optionsButtonRect()), currentCourse().horizonGlow, currentCourse().shoulderColor)
    }

    private fun drawOptions(canvas: Canvas) {
        centerTitlePaint.textSize = viewportWidth * 0.1f
        centerBodyPaint.textSize = viewportWidth * 0.04f
        canvas.drawText("OPTIONS", viewportWidth / 2f, viewportHeight * 0.12f, centerTitlePaint)
        canvas.drawText("音声・グラフィック・シェーダー設定", viewportWidth / 2f, viewportHeight * 0.17f, centerBodyPaint)

        val rows = listOf(
            Triple(graphicsOptionRect(), "Graphics / LOD", options.graphicsQuality.label),
            Triple(shaderCacheOptionRect(), "Shader Cache", if (options.shaderCacheEnabled) "Enabled" else "Disabled"),
            Triple(soundOptionRect(), "Sound FX", if (options.soundEnabled) "On" else "Off"),
            Triple(volumeOptionRect(), "Master Volume", "${options.masterVolume}%"),
        )
        rows.forEach { (rect, label, value) ->
            drawSelectableCard(canvas, rect, true, currentCourse().horizonGlow)
            leftDetailPaint.textSize = viewportWidth * 0.04f
            leftStatPaint.textSize = viewportWidth * 0.026f
            leftStatPaint.textAlign = Paint.Align.LEFT
            canvas.drawText(label, rect.left + 28f, rect.top + 52f, leftDetailPaint)
            canvas.drawText(value, rect.left + 28f, rect.top + 92f, leftStatPaint)
        }

        drawOverlayButton(canvas, UiButton("BACK", optionsBackButtonRect()), currentCar().accent, currentCourse().horizonGlow)
    }

    private fun drawCrashOverlay(canvas: Canvas) {
        overlayPaint.color = Color.argb((170 + crashFlash * 50).toInt().coerceIn(0, 220), 4, 8, 24)
        canvas.drawRect(0f, 0f, viewportWidth, viewportHeight, overlayPaint)
        val panel = RectF(viewportWidth * 0.1f, viewportHeight * 0.22f, viewportWidth * 0.9f, viewportHeight * 0.58f)
        canvas.drawRoundRect(panel, 40f, 40f, panelPaint)
        canvas.drawRoundRect(panel, 40f, 40f, panelStrokePaint)

        centerTitlePaint.textSize = viewportWidth * 0.11f
        centerBodyPaint.textSize = viewportWidth * 0.046f
        canvas.drawText("RACE OVER", viewportWidth / 2f, panel.top + 120f, centerTitlePaint)
        canvas.drawText("${currentCar().name} / ${currentCourse().name}", viewportWidth / 2f, panel.top + 190f, centerBodyPaint)
        canvas.drawText("SCORE ${raceScore.toInt()}", viewportWidth / 2f, panel.top + 280f, centerTitlePaint)
        canvas.drawText("BEST ${bestScore.toInt()}", viewportWidth / 2f, panel.top + 350f, centerBodyPaint)

        drawOverlayButton(canvas, UiButton("RETRY", retryButtonRect()), currentCar().accent, currentCourse().horizonGlow)
        drawOverlayButton(canvas, UiButton("GARAGE", garageButtonRect()), currentCourse().horizonGlow, currentCourse().shoulderColor)
        drawOverlayButton(canvas, UiButton("OPTIONS", crashOptionsButtonRect()), currentCourse().shoulderColor, currentCourse().horizonGlow)
    }

    private fun drawSelectableCard(canvas: Canvas, rect: RectF, active: Boolean, accent: Int) {
        cardFillPaint.color = if (active) Color.argb(220, 12, 20, 48) else Color.argb(175, 7, 10, 28)
        cardOutlinePaint.color = if (active) accent else panelStrokePaint.color
        cardOutlinePaint.style = Paint.Style.STROKE
        cardOutlinePaint.strokeWidth = if (active) 4f else 3f
        canvas.drawRoundRect(rect, 28f, 28f, cardFillPaint)
        canvas.drawRoundRect(rect, 28f, 28f, cardOutlinePaint)
    }

    private fun drawOverlayButton(canvas: Canvas, button: UiButton, startColor: Int, endColor: Int) {
        cardFillPaint.shader = shaderCache.linearGradient(
            enabled = options.shaderCacheEnabled,
            id = "button-${button.label}",
            left = button.rect.left,
            top = button.rect.top,
            right = button.rect.right,
            bottom = button.rect.bottom,
            colors = intArrayOf(startColor, endColor),
        )
        canvas.drawRoundRect(button.rect, 34f, 34f, cardFillPaint)
        cardFillPaint.shader = null
        canvas.drawText(button.label, button.rect.centerX(), button.rect.centerY() + 18f, buttonTextPaint)
    }

    private fun drawStatBar(canvas: Canvas, label: String, value: Float, left: Float, top: Float, width: Float, color: Int) {
        leftStatPaint.textSize = viewportWidth * 0.024f
        leftStatPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, left, top, leftStatPaint)
        val barRect = RectF(left, top + 14f, left + width, top + 34f)
        canvas.drawRoundRect(barRect, 12f, 12f, barBgPaint)
        barFillPaint.color = color
        canvas.drawRoundRect(RectF(barRect.left, barRect.top, barRect.left + barRect.width() * value.coerceIn(0f, 1f), barRect.bottom), 12f, 12f, barFillPaint)
    }

    private fun drawGarageBar(canvas: Canvas, label: String, value: Float, left: Float, top: Float, width: Float, color: Int) {
        leftStatPaint.textSize = viewportWidth * 0.023f
        leftStatPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(label, left, top, leftStatPaint)
        val bar = RectF(left, top + 12f, left + width, top + 28f)
        canvas.drawRoundRect(bar, 10f, 10f, barBgPaint)
        barFillPaint.color = color
        canvas.drawRoundRect(RectF(bar.left, bar.top, bar.left + bar.width() * value.coerceIn(0f, 1f), bar.bottom), 10f, 10f, barFillPaint)
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
        return RectF(startX + col * (cardWidth + gapX), startY + row * (cardHeight + gapY), startX + col * (cardWidth + gapX) + cardWidth, startY + row * (cardHeight + gapY) + cardHeight)
    }

    private fun courseCardRect(index: Int): RectF {
        val width = viewportWidth * 0.38f
        val height = viewportHeight * 0.08f
        val gapX = viewportWidth * 0.06f
        val gapY = viewportHeight * 0.015f
        val startX = viewportWidth * 0.09f
        val startY = viewportHeight * 0.69f
        val col = index % 2
        val row = index / 2
        return RectF(startX + col * (width + gapX), startY + row * (height + gapY), startX + col * (width + gapX) + width, startY + row * (height + gapY) + height)
    }

    private fun startButtonRect(): RectF = RectF(viewportWidth * 0.12f, viewportHeight * 0.89f, viewportWidth * 0.56f, viewportHeight * 0.96f)
    private fun optionsButtonRect(): RectF = RectF(viewportWidth * 0.60f, viewportHeight * 0.89f, viewportWidth * 0.88f, viewportHeight * 0.96f)
    private fun graphicsOptionRect(): RectF = RectF(viewportWidth * 0.1f, viewportHeight * 0.24f, viewportWidth * 0.9f, viewportHeight * 0.34f)
    private fun shaderCacheOptionRect(): RectF = RectF(viewportWidth * 0.1f, viewportHeight * 0.37f, viewportWidth * 0.9f, viewportHeight * 0.47f)
    private fun soundOptionRect(): RectF = RectF(viewportWidth * 0.1f, viewportHeight * 0.50f, viewportWidth * 0.9f, viewportHeight * 0.60f)
    private fun volumeOptionRect(): RectF = RectF(viewportWidth * 0.1f, viewportHeight * 0.63f, viewportWidth * 0.9f, viewportHeight * 0.73f)
    private fun optionsBackButtonRect(): RectF = RectF(viewportWidth * 0.12f, viewportHeight * 0.82f, viewportWidth * 0.88f, viewportHeight * 0.90f)
    private fun retryButtonRect(): RectF = RectF(viewportWidth * 0.12f, viewportHeight * 0.66f, viewportWidth * 0.88f, viewportHeight * 0.74f)
    private fun garageButtonRect(): RectF = RectF(viewportWidth * 0.12f, viewportHeight * 0.77f, viewportWidth * 0.88f, viewportHeight * 0.85f)
    private fun crashOptionsButtonRect(): RectF = RectF(viewportWidth * 0.12f, viewportHeight * 0.88f, viewportWidth * 0.88f, viewportHeight * 0.96f)

    private fun projectedScale(distance: Float): Float {
        val safeDistance = max(0f, distance)
        return 1.8f / (safeDistance * 0.035f + 1.1f)
    }

    private fun projectedRoadX(laneOffset: Float, distance: Float): Float {
        val safeDistance = max(0f, distance)
        val t = (1f / (safeDistance * 0.025f + 1f)).coerceIn(0f, 1f)
        val roadWidthAtDepth = roadTopWidth + (roadBottomWidth - roadTopWidth) * t
        val centerShift = roadCurve * viewportWidth * (0.03f + (1f - t) * 0.09f)
        return viewportWidth * 0.5f + centerShift + laneOffset * roadWidthAtDepth * 0.32f
    }

    private fun projectedRoadY(distance: Float): Float {
        val safeDistance = max(0f, distance)
        val t = (1f / (safeDistance * 0.025f + 1f)).coerceIn(0f, 1f)
        return horizonY + (roadBottomY - horizonY) * t
    }

    private fun currentCar(): CarSpec = garage[selectedCarIndex]
    private fun currentCourse(): CourseSpec = courses[selectedCourseIndex]

    private fun applyCoursePalette(course: CourseSpec) {
        cityPaint.color = course.sceneryColor
        roadPaint.color = course.roadColor
        lanePaint.color = course.laneColor
        shoulderPaint.color = course.shoulderColor
        boostPaint.color = course.horizonGlow
    }

    private fun safeValue(value: Float): Float = if (value.isFinite()) value else 0f

    private companion object {
        val laneOffsets = floatArrayOf(-0.34f, 0.34f)
        val laneBaseChoices = floatArrayOf(-1f, 0f, 1f)
        val laneShiftChoices = floatArrayOf(-0.6f, 0f, 0.6f)
    }
}
