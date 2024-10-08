package org.example.project

import kotlinx.serialization.Serializable

@Serializable
data class ArduinoSensorData(
     val roll_IMU1: Double?,
     val pitch_IMU1: Double?,
     val roll_IMU2: Double?,
     val pitch_IMU2: Double?,
     val rs775_speed: Double?,
     val spg_speed: Double?,
    val rs775_motor_voltage: Double?,
     val rs775_current: Double?,
     val rs775_position: Double?,
    val spg_voltage: Double?,
    val spg_current: Double?,
     val spg_position : Double?,
     val brake_status : Long?,
     val PID_proportional:Double?,
     val PID_integral:Double?,
     val PID_derivative:Double?,
     @kotlinx.serialization.Transient
     var date: String? = null,
     @kotlinx.serialization.Transient
     var time: String? = null,
)
