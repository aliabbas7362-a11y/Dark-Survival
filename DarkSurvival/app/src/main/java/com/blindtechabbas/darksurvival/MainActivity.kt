package com.blindtechabbas.darksurvival

import android.os.Bundle
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.viewModels
import androidx.compose.ui.platform.ComposeView
import com.blindtechabbas.darksurvival.game.GameViewModel
import com.blindtechabbas.darksurvival.ui.navigation.AppNavHost
import com.blindtechabbas.darksurvival.ui.theme.DarkSurvivalTheme
import com.blindtechabbas.darksurvival.util.CrashLogger
import com.blindtechabbas.darksurvival.util.TiltSensor

/**
 * MainActivity v4. All the work is in GameViewModel; the activity only
 *  - keeps the screen on,
 *  - tells the game when TalkBack (touch exploration) turns on or off,
 *  - passes the phone tilt on,
 *  - tells the game when the app goes to the background and comes back.
 */
class MainActivity : ComponentActivity() {

    private val vm: GameViewModel by viewModels()
    private lateinit var am: AccessibilityManager
    private var tilt: TiltSensor? = null

    private val touchExplorationListener =
        AccessibilityManager.TouchExplorationStateChangeListener { on -> vm.onAccessibilityState(on) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLogger.init(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        am = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager
        tilt = TiltSensor(this) { fwd, right -> vm.onTilt(fwd, right) }
        vm.onAccessibilityState(am.isTouchExplorationEnabled)

        // MULTI-TOUCH: the left hand walks while the right hand presses buttons.
        val composeView = ComposeView(this)
        val rootView = FrameLayout(this).apply {
            isMotionEventSplittingEnabled = true
            addView(composeView)
        }
        setContentView(rootView)
        composeView.setContent {
            DarkSurvivalTheme {
                AppNavHost(vm)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        am.addTouchExplorationStateChangeListener(touchExplorationListener)
        vm.onAccessibilityState(am.isTouchExplorationEnabled)
        tilt?.start()
        vm.onAppResume()
    }

    override fun onPause() {
        am.removeTouchExplorationStateChangeListener(touchExplorationListener)
        tilt?.stop()
        vm.onAppPause()
        super.onPause()
    }
}
