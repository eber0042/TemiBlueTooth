package com.temi.temiSDK

import android.util.Log
import com.robotemi.sdk.Robot
import com.robotemi.sdk.listeners.OnRobotReadyListener
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Singleton
import kotlin.system.exitProcess


@Module
@InstallIn(SingletonComponent::class)
object RobotModule {
    @Provides
    @Singleton
    fun provideRobotController() = RobotController()
}

class RobotController():
    OnRobotReadyListener
{
    private val robot = Robot.getInstance() //This is needed to reference the data coming from Temi


    init {
        robot.addOnRobotReadyListener(this)
    }

    /**
     * Called when connection with robot was established.
     *
     * @param isReady `true` when connection is open. `false` otherwise.
     */
    override fun onRobotReady(isReady: Boolean) {
        if (!isReady) return

//        initialYaw = robot.getPosition().yaw

        robot.setDetectionModeOn(on = true, distance = 1.0f) // Set how far it can detect stuff
        robot.setKioskModeOn(on = false)
    }
}