package com.apps.confettibot

import android.app.Service
import android.content.Intent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.graphics.*
import android.os.*
import android.util.Log
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IAxisValueFormatter
import com.github.nkzawa.socketio.client.IO
import com.github.nkzawa.socketio.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.net.URISyntaxException

class MyService : Service() {
    lateinit var mSocket : Socket
    lateinit var handler : Handler
    val dataGrafica = arrayOf(0,0,0)

    override fun onCreate() {
        super.onCreate()
        val channelId = "CHANNEL-01"
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("BOT").setContentText("ESTOY LISTO")
            .setOngoing(true).setSmallIcon(R.drawable.green_icon)
            .setWhen(System.currentTimeMillis())
        val mNotificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val mChannel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_DEFAULT)
            mNotificationManager.createNotificationChannel(mChannel)
            builder.setChannelId(channelId) // Channel ID
        }
        startForeground(1, builder.build())
        handler = Handler(Looper.getMainLooper())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        //Create floating widget
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val params: WindowManager.LayoutParams = WindowManager.LayoutParams()
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.width = ViewGroup.LayoutParams.MATCH_PARENT

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            params.type = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_SECURE
        params.format = PixelFormat.TRANSLUCENT
        params.gravity = Gravity.TOP

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        val view = inflater.inflate(R.layout.overlay_view, null)

        val viewPregunta = view.findViewById<TextView>(R.id.pregunta)
        val viewChart    = view.findViewById<HorizontalBarChart>(R.id.chart)
        val viewClose    = view.findViewById<ImageView>(R.id.close)
        val viewEye      = view.findViewById<ImageView>(R.id.eye)

        //Listener buttons
        viewClose.setOnClickListener {
            windowManager.removeView(view)
            stopForeground(true)
            stopSelf()
        }

        viewEye.setOnClickListener {
            if(viewChart.visibility == View.VISIBLE){
                viewEye.setImageResource(R.drawable.eye_open)
                viewChart.visibility = View.GONE
                viewPregunta.visibility = View.GONE
            }else{
                viewEye.setImageResource(R.drawable.eye_close)
                viewChart.visibility = View.VISIBLE
                viewPregunta.visibility = View.VISIBLE
            }
        }

        windowManager.addView(view, params)

        //Configuracion grafica
        val chart = view.findViewById<HorizontalBarChart>(R.id.chart)
        chart.xAxis.position = XAxis.XAxisPosition.TOP_INSIDE
        chart.xAxis.granularity = 1f
        chart.xAxis.setDrawGridLines(false)
        chart.xAxis.textColor = ContextCompat.getColor(this, R.color.colorAccent)

        chart.axisLeft.setDrawGridLines(false)
        chart.axisRight.setDrawGridLines(false)
        chart.axisRight.setDrawLabels(false)

        chart.legend.isEnabled = false
        chart.description.text = ""
        chart.description.isEnabled = false
        chart.setNoDataText("SIN DATOS PARA MOSTRAR")
        chart.setDrawValueAboveBar(true)
        chart.setFitBars(true)
        chart.setTouchEnabled(false)
        chart.setPinchZoom(false)

        try {
            mSocket = IO.socket("http://chispitas.sytes.net")
            mSocket.on(Socket.EVENT_CONNECT) {
                Log.d("XXXXXXX", "CONNECTED")
            }.on(Socket.EVENT_DISCONNECT){
                Log.d("XXXXXXX", "DISCONNECT")
            }.on(Socket.EVENT_RECONNECTING){
                Log.d("XXXXXXX", "RECONECTING")
            }.on("APP_QUESTION"){
                // RESET CHART VALUES
                dataGrafica[0] = 0
                dataGrafica[1] = 0
                dataGrafica[2] = 0

                for(item in it.iterator()){
                    val mainJSON = JSONObject(item.toString())

                    val preg = mainJSON.getString("P")               // Pregunta
                    val resp: JSONArray = mainJSON.getJSONArray("R") // Respuestas

                    runOnUiThread (Runnable {
                        viewPregunta.text = preg
                    })

                    val dataSet = BarDataSet(
                        listOf(BarEntry(0f, 10f), BarEntry(1f, 30f), BarEntry(2f, 20f)), "1,2,3"
                    )
                    dataSet.colors = listOf(Color.RED, Color.DKGRAY, Color.GREEN)
                    chart.data = BarData(dataSet)
                    chart.xAxis.valueFormatter = IAxisValueFormatter { value, _ ->
                        resp.getString(
                            when(value.toInt()){
                                0   -> 2
                                2   -> 0
                                else -> value.toInt()
                            }
                        )
                    }
                }
            }.on("Grafica"){
                for(item in it.iterator()){
                    val mainJSON      = JSONObject(item.toString())
                    Log.d("XXXXXXX", "Grafica $mainJSON")

                    val arrayValores  = mainJSON.getJSONArray("array")
                    dataGrafica[2]   += arrayValores.getString(0).toInt()
                    dataGrafica[1]   += arrayValores.getString(1).toInt()
                    dataGrafica[0]   += arrayValores.getString(2).toInt()

                    val dataSet = BarDataSet(listOf(
                            BarEntry(0f, dataGrafica[0].toFloat()),
                            BarEntry(1f, dataGrafica[1].toFloat()),
                            BarEntry(2f, dataGrafica[2].toFloat())), ""
                    )
                    dataSet.colors = listOf(Color.RED, Color.DKGRAY, Color.GREEN)
                    chart.data = BarData(dataSet)
                    chart.invalidate()
                    Log.d("XXXXXXX", "Grafica $mainJSON")
                }
            }.on("EachWordSeach"){
                for(item in it.iterator()){
                    Log.d("XXXXXXX", "EachWordSeach $item")
                }
            }

            chart.invalidate()
            mSocket.connect()
        } catch (e: URISyntaxException) {
            Log.d("XXXXXXX", e.message)
        }

        return START_STICKY
    }

    fun runOnUiThread(runnable: Runnable) {
        handler.post(runnable);
    }


    override fun onBind(intent: Intent?): IBinder? {  return null }
}
