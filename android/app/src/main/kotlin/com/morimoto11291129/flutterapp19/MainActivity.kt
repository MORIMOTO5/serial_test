package com.morimoto11291129.flutterapp19

import android.app.PendingIntent
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.BatteryManager
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.widget.Toast
import androidx.annotation.NonNull
import com.hoho.android.usbserial.driver.UsbSerialDriver
import com.hoho.android.usbserial.driver.UsbSerialPort
import com.hoho.android.usbserial.driver.UsbSerialProber
import com.hoho.android.usbserial.util.SerialInputOutputManager
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.concurrent.Executors
import kotlin.collections.ArrayList


internal class ListItem(var device: UsbDevice, var port: Int, var driver: UsbSerialDriver)

private enum class UsbPermission {
    Unknown, Requested, Granted, Denied
}

private const val INTENT_ACTION_GRANT_USB = BuildConfig.APPLICATION_ID + ".GRANT_USB"
private const val WRITE_WAIT_MILLIS = 2000
private const val READ_WAIT_MILLIS = 2000

class MainActivity: FlutterActivity() {

    private val listItems: ArrayList<ListItem> = ArrayList()

    private val CHANNEL = "samples.flutter.dev/battery"

    private val deviceId = 0
    private  var portNum:Int = 0
    private  var baudRate:Int = 115200
    private val withIoManager = false

    private var usbIoManager: SerialInputOutputManager? = null
    private var usbSerialPort: UsbSerialPort? = null
    private var usbPermission = UsbPermission.Unknown
    private var connected = false

    private var current_status:Int = 0;
    private var current_status2:String = "no";

    private var mainLooper: Handler? = null

    override fun configureFlutterEngine(@NonNull flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler {
            // Note: this method is invoked on the main thread.
            call, result ->
            if (call.method == "getBatteryLevel") {
                val batteryLevel = getBatteryLevel()

                if (batteryLevel != -1) {
                    result.success(batteryLevel)
                } else {
                    result.error("UNAVAILABLE", "Battery level not available.", null)
                }
            } else if(call.method == "getBatteryLevel2") {
                val batteryLevel = getBatteryLevel2()

                if (batteryLevel != "no") {
                    result.success(batteryLevel)
                } else {
                    result.error("UNAVAILABLE", "Battery level not available.", null)
                }
            } else if(call.method == "getBatteryLevel3") {
                val batteryLevel = getBatteryLevel3()

                if (batteryLevel != "no") {
                    result.success(batteryLevel)
                } else {
                    result.error("UNAVAILABLE", "Battery level not available.", null)
                }
            }
            else {
                result.notImplemented()
            }
        }

        mainLooper = Handler(Looper.getMainLooper())
    }

    private fun getBatteryLevel(): Int {
        var batteryLevel: Int
        if (VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            val batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            batteryLevel = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        } else {
            val intent = ContextWrapper(applicationContext).registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            batteryLevel = intent!!.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) * 100 / intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        }


        connect()
        send("000000")



        if(connected)
        {
            batteryLevel = 62
        }
        else
        {
            batteryLevel = current_status
        }

        return batteryLevel
    }

    private fun getBatteryLevel2(): String {
        var batteryLevel: String
        batteryLevel = "0"

        connect()
        send("000000")

        if(connected)
        {
            //batteryLevel = "62"
            send("000000")
        }
        else
        {

        }

        batteryLevel = current_status2
        return batteryLevel
    }

    private fun getBatteryLevel3(): String {
        var batteryLevel: String
        batteryLevel = "0"

        connect()
        var received_data: ByteArray = byteArrayOf()
        receive(received_data)

        if(connected)
        {
            //batteryLevel = "62"
            //receive(received_data)
            //batteryLevel = received_data.toString()
            send("000000")

            val buffer = ByteArray(8192)
            val len: Int = usbSerialPort!!.read(buffer, READ_WAIT_MILLIS)
            receive(Arrays.copyOf(buffer, len))

            batteryLevel = buffer[0].toString()
        }
        else
        {
            batteryLevel = "1"
        }

        //batteryLevel = current_status2
        return batteryLevel
    }

    private fun connect() {
        var device: UsbDevice? = null
        val usbManager = activity.getSystemService(Context.USB_SERVICE) as UsbManager
        //for (v in usbManager.deviceList.values) if (v.deviceId == deviceId) device = v
        for(v in usbManager.deviceList.values)
        {
            device = v
        }
        if (device == null) {
            status("connection failed: device not found")
            current_status = 1
            return
        }
        var driver = UsbSerialProber.getDefaultProber().probeDevice(device)
        if (driver == null) {
            driver = CustomProber.getCustomProber().probeDevice(device)
        }
        if (driver == null) {
            status("connection failed: no driver for device")
            current_status = 2
            return
        }
        if (driver.ports.size < portNum) {
            status("connection failed: not enough ports at device")
            current_status = 3
            return
        }
        usbSerialPort = driver.ports[portNum]
        val usbConnection = usbManager.openDevice(driver.device)
        if (usbConnection == null && usbPermission === UsbPermission.Unknown && !usbManager.hasPermission(driver.device)) {
            usbPermission = UsbPermission.Requested
            val usbPermissionIntent = PendingIntent.getBroadcast(activity, 0, Intent(INTENT_ACTION_GRANT_USB), 0)
            usbManager.requestPermission(driver.device, usbPermissionIntent)
            current_status = 8
            return
        }
        if (usbConnection == null) {
            if (!usbManager.hasPermission(driver.device))
            {
                status("connection failed: permission denied")
                current_status = 4
            } else
            {
                status("connection failed: open failed")
                current_status = 5
            }
            return
        }
        try {
            current_status = 9
            usbSerialPort?.open(usbConnection)
            current_status = 10
            usbSerialPort?.setParameters(baudRate, 8, UsbSerialPort.STOPBITS_1, UsbSerialPort.PARITY_NONE)
            current_status = 11
            if (withIoManager) {
                current_status = 12
                usbIoManager = SerialInputOutputManager(usbSerialPort, mListener)
                current_status = 13
                Executors.newSingleThreadExecutor().submit(usbIoManager)
                current_status = 14
            }
            status("connected")
            current_status = 6
            connected = true
            //controlLines.start()
        } catch (e: Exception) {
            status("connection failed: " + e.message)
            current_status2 = e.message.toString()
            //current_status = 7
            disconnect()
        }
    }

    private fun disconnect() {
        connected = false
        //controlLines.stop()
        if (usbIoManager != null) usbIoManager?.stop()
        usbIoManager = null
        try {
            usbSerialPort?.close()
        } catch (ignored: IOException) {
        }
        usbSerialPort = null
    }

    private fun send(str: String) {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        current_status2 = "63"
        try {
            val data = """$str
""".toByteArray()
            val spn = SpannableStringBuilder()
            spn.append("""send ${data.size} bytes
""")
            spn.append("""
    ${HexDump.dumpHexString(data)}
    
    """.trimIndent())
            //spn.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorSendText)), 0, spn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            //receiveText.append(spn)
            current_status2 = "64"
            //var data2 = byteArrayOf(64, 1, 1, 0, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0)
            //var data2 = byteArrayOf(64, 1, 3, 0, 0, 0, 11, 0, 1, 0, 0, 0, 1, 0, 0)
            //var data2 = byteArrayOf(64, 1, 200.toByte(), 0, 0, 0, 1, 0, 1, 0, 0, 0, 10, 0, 255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(), 255.toByte(), 2, 0, 0, 0, 2)
            var data2 = byteArrayOf(64, 1);
                    //var data2 = byteArrayOf(48, 48, 48, 48)
            current_status2 = "65"
            usbSerialPort?.write(data2, WRITE_WAIT_MILLIS)
            current_status2 = "66"
        } catch (e: java.lang.Exception) {
            //onRunError(e)
            current_status2 = "67"
        }
    }

    private fun read() {
        if (!connected) {
            Toast.makeText(activity, "not connected", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val buffer = ByteArray(8192)
            val len: Int = usbSerialPort!!.read(buffer, READ_WAIT_MILLIS)
            receive(Arrays.copyOf(buffer, len))
        } catch (e: IOException) {
            // when using read with timeout, USB bulkTransfer returns -1 on timeout _and_ errors
            // like connection loss, so there is typically no exception thrown here on error
            //status("connection lost: " + e.getMessage())
            disconnect()
        }
    }

    private fun receive(data: ByteArray) {
        val spn = SpannableStringBuilder()
        spn.append("""receive ${data.size} bytes
""")
        if (data.size > 0) spn.append("""
    ${HexDump.dumpHexString(data)}
    
    """.trimIndent())
        //receiveText.append(spn)
    }

    fun status(str: String) {
        val spn = SpannableStringBuilder("""
    $str
    
    """.trimIndent())
        //spn.setSpan(ForegroundColorSpan(resources.getColor(R.color.colorStatusText)), 0, spn.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        //receiveText.append(spn)
    }

    private val mListener: SerialInputOutputManager.Listener = object : SerialInputOutputManager.Listener {
        override fun onRunError(e: java.lang.Exception) {
            //Log.d(TAG, "Runner stopped.")
            current_status = 21
            disconnect();
        }

        override fun onNewData(data: ByteArray) {
            current_status = 22
            receive(data);

            val result = StringBuilder()
            for (i in 0 until data.size - 2) {
                //if (data[i] > ' ' && data[i] < '~') {
                    result.append(String(byteArrayOf(data[i])))
                //} else {
                //    result.append(".")
                //}
            }
            val datas = String(data, StandardCharsets.UTF_8)
            //Log.i(TAG, "reading GPS: $datas")

            current_status2 = datas
        }
    }


}
