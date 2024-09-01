package org.example.project

import MalaysianTimeFormatter
import androidx.compose.material.SnackbarDuration
import androidx.compose.material.SnackbarHostState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.project.cache.SensorDataQueries
import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import java.time.LocalDateTime

class SerialViewModel(
    private val sensorDataQueries: SensorDataQueries,
    private val dataProcessor: DataProcess
) : ViewModel() {

    private val _snackbarHostState = MutableStateFlow(SnackbarHostState())
    val SnackbarHostState: StateFlow<SnackbarHostState> = _snackbarHostState.asStateFlow()

    // Holds the state of the SerialPort connection (connected/disconnected)
    private val _isSerialPortConnected = MutableStateFlow(false)
    val isSerialPortConnected: StateFlow<Boolean> = _isSerialPortConnected.asStateFlow()

    private val _serialData = MutableStateFlow<String>("EMPTY SERIAL DATA")
    val serialData: StateFlow<String> get() = _serialData

    // Holds the current COM Port
    private val _currentComPort = MutableStateFlow<String?>(null)
    var currentComPort: StateFlow<String?> = _currentComPort.asStateFlow()

    // Holds the state of the machine (on/off)
    private val _isMachineOn = MutableStateFlow(false)
    val isMachineOn: StateFlow<Boolean> = _isMachineOn.asStateFlow()

    // Holds the reference to the SerialPort
    private var serialPort: SerialPort? = null

    suspend fun showMessage(message: String, actionName: String? = null, action: () -> Unit = {}) {
        _snackbarHostState.value.showSnackbar(message)
    }

    // Function to list available COM ports
    fun listAvailableComPorts(): Array<String> {
        return SerialPort.getCommPorts().map { it.systemPortName }.toTypedArray()
    }

    // Function to open a connection to the selected COM port
    fun connectToSerialPort(comPortName: String): Boolean {
        val port = SerialPort.getCommPort(comPortName)
        port.baudRate = 115200 // You can adjust the baud rate as needed
        return if (port.openPort()) {
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);
            serialPort = port
            _isSerialPortConnected.value = true
            _currentComPort.value = comPortName
            startReadingSerialData()
            println(comPortName)
            true
        } else {
            println(port.isOpen)

            false
        }
    }

    // Function to close the connection to the SerialPort
    fun disconnectSerialPort() {
        serialPort?.let {
            it.closePort()
            _isSerialPortConnected.value = false
            _currentComPort.value = null
            serialPort = null
        }
    }

    // Send command to turn the machine on
    private fun turnOnMachine() {
        serialPort?.let {
            if (it.isOpen) {
                val buffer = "ON\n".toByteArray()
                it.writeBytes(buffer, buffer.size)
            }
        }
    }

    // Send command to turn the machine off
    private fun turnOffMachine() {
        serialPort?.let {
            if (it.isOpen) {
                val buffer = "OFF\n".toByteArray()
                it.writeBytes(buffer, buffer.size)
            }
        }
    }

    // Toggle machine state
    fun setMachineState(isOn: Boolean) {
        if (isOn) {
            turnOnMachine()
        } else {
            turnOffMachine()
        }
        _isMachineOn.value = isOn
    }

    fun resetMachineState() {
        serialPort?.let {
            if (it.isOpen) {
                val buffer = "RESET\n".toByteArray()
                it.writeBytes(buffer, buffer.size)
                _isMachineOn.value = false
            }
        }
    }

    fun exportToExcel() {
        disconnectSerialPort()
        setMachineState(false)
        CoroutineScope(Dispatchers.Default).launch {
            dataProcessor.exportSensorDataToExcel("data")
        }
    }

    fun resetDatabase() {
        disconnectSerialPort()
        setMachineState(false)
        CoroutineScope(Dispatchers.Default).launch {
            sensorDataQueries.clearSensorData()
        }
    }

    private fun startReadingSerialData() {
        viewModelScope.launch(Dispatchers.IO) {
            val buffer = StringBuilder()
            while (serialPort?.isOpen == true) {
                val inputStream = serialPort?.inputStream ?: continue
                val byteBuffer = ByteArray(4096)
                val bytesRead = inputStream.read(byteBuffer)
                if (bytesRead > 0) {
                    val receivedData = String(byteBuffer, 0, bytesRead)
                    buffer.append(receivedData)

                    // Check if buffer contains a newline
                    var newlineIndex = buffer.indexOf("\n")
                    while (newlineIndex != -1) {
                        // Extract the line up to the newline
                        val completeLine = buffer.substring(0, newlineIndex).trimEnd()

                        val data = handleReceivedData(completeLine.trim())
                        // Post the complete line to LiveData
                        _serialData.value = """
                                    Roll IMU1: ${data.roll_IMU1}
                                    Pitch IMU1: ${data.pitch_IMU1}
                                    Roll IMU2: ${data.roll_IMU2}
                                    Pitch IMU2: ${data.pitch_IMU2}
                                    RS775 Speed: ${data.rs775_speed}
                                    SPG Speed: ${data.spg_speed}
                                    RS775 Motor Voltage: ${data.rs775_motor_voltage}
                                    RS775 Current: ${data.rs775_current}
                                    RS775 Position: ${data.rs775_position}
                                    SPG Voltage: ${data.spg_voltage}
                                    SPG Current: ${data.spg_current}
                                    SPG Position: ${data.spg_position}
                                    Brake Status: ${data.brake_status}
                                    PID Proportional: ${data.PID_proportional}
                                    PID Integral: ${data.PID_integral}
                                    PID Derivative: ${data.PID_derivative}
                                """.trimIndent()

                        println(
                            """
                        Roll IMU1: ${data.roll_IMU1}
                        Pitch IMU1: ${data.pitch_IMU1}
                        Roll IMU2: ${data.roll_IMU2}
                        Pitch IMU2: ${data.pitch_IMU2}
                        RS775 Speed: ${data.rs775_speed}
                        SPG Speed: ${data.spg_speed}
                        RS775 Motor Voltage: ${data.rs775_motor_voltage}
                        RS775 Current: ${data.rs775_current}
                        RS775 Position: ${data.rs775_position}
                        SPG Voltage: ${data.spg_voltage}
                        SPG Current: ${data.spg_current}
                        SPG Position: ${data.spg_position}
                        Brake Status: ${data.brake_status}
                        PID Proportional: ${data.PID_proportional}
                        PID Integral: ${data.PID_integral}
                        PID Derivative: ${data.PID_derivative}
    """.trimIndent()
                        )


                        // Remove the processed line from the buffer
                        buffer.delete(0, newlineIndex + 1)

                        // Check for another newline in the remaining buffer
                        newlineIndex = buffer.indexOf("\n")
                    }
                }
            }
        }
    }


    private fun handleReceivedData(data: String): ArduinoSensorData {
        val dateTimeNow = LocalDateTime.now()
        val formatter = Json { ignoreUnknownKeys = true }

        if (validateJson(data)) {
            val arduinoSensorData = formatter.decodeFromString<ArduinoSensorData>(data)
            CoroutineScope(Dispatchers.IO).launch {
                sensorDataQueries.insertSensorData(
                    roll_IMU1 = arduinoSensorData.roll_IMU1,
                    pitch_IMU1 = arduinoSensorData.pitch_IMU1,
                    roll_IMU2 = arduinoSensorData.roll_IMU2,
                    pitch_IMU2 = arduinoSensorData.pitch_IMU2,
                    rs775_speed = arduinoSensorData.rs775_speed,
                    spg_speed = arduinoSensorData.spg_speed,
                    rs775_motor_voltage = arduinoSensorData.rs775_motor_voltage,
                    rs775_current = arduinoSensorData.rs775_current,
                    rs775_position = arduinoSensorData.rs775_position,
                    spg_current = arduinoSensorData.spg_current,
                    spg_voltage = arduinoSensorData.spg_voltage,
                    spg_position = arduinoSensorData.spg_position,
                    brake_status = arduinoSensorData.brake_status,
                    PID_proportional = arduinoSensorData.PID_proportional,
                    PID_integral = arduinoSensorData.PID_integral,
                    PID_derivative = arduinoSensorData.PID_derivative,
                    date = MalaysianTimeFormatter.getDateForFile(),
                    time = MalaysianTimeFormatter.getTimeForFile()
                )
            }
            return arduinoSensorData
        } else {
            println("Invalid JSON data: $data")
        }
        return ArduinoSensorData(
            roll_IMU1 = 0.0,
            pitch_IMU1 = 0.0,
            roll_IMU2 = 0.0,
            pitch_IMU2 = 0.0,
            rs775_speed = 0.0,
            spg_speed = 0.0,
            rs775_motor_voltage = 0.0,
            rs775_current = 0.0,
            rs775_position = 0.0,
            spg_voltage = 0.0,
            spg_current = 0.0,
            spg_position = 0.0,
            brake_status = 0,
            PID_proportional = 0.0,
            PID_integral = 0.0,
            PID_derivative = 0.0,
        )
    }

    private fun validateJson(data: String): Boolean {
        try {
            val jsonData = Json.parseToJsonElement(data)
            val requiredKeys = setOf(
                "roll_IMU1",
                "pitch_IMU1",
                "roll_IMU2",
                "pitch_IMU2",
                "rs775_speed",
                "spg_speed",
                "rs775_motor_voltage",
                "rs775_current",
                "rs775_position",
                "spg_voltage",
                "spg_current",
                "spg_position",
                "brake_status",
                "PID_propotional",
                "PID_integral",
                "PID_derivative"
            )
            val jsonKeys = jsonData.jsonObject.keys
            return requiredKeys.all { it in jsonKeys }
        } catch (e: Exception) {
            return false;
        }

    }

    fun viewExcelFile() {

    }

}
