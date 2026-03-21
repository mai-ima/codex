package com.codex.neonsprint

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager

class MainActivity : Activity() {
    private lateinit var gameView: RacingGameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        gameView = RacingGameView(this)
        setContentView(gameView)
    }

    override fun onResume() {
        super.onResume()
        gameView.resume()
    }

    override fun onPause() {
        gameView.pause()
        super.onPause()
    }
}
