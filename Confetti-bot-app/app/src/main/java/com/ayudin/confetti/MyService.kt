package com.ayudin.confetti

import android.app.Service
import android.content.Intent
import android.app.NotificationManager
import android.app.NotificationChannel
import android.content.Context
import android.graphics.*
import android.os.*
import android.util.Log
import android.view.*
import android.webkit.WebView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.crashlytics.android.Crashlytics
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
import java.lang.Exception
import java.net.URISyntaxException
import kotlin.math.ceil

class MyService : Service() {
    lateinit var mSocket : Socket
    lateinit var handler : Handler
    val dataGrafica = arrayOf(0,0,0)

    override fun onCreate() {
        super.onCreate()
        val channelId = "CHANNEL-01"
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AYUDIN").setContentText("ESTOY LISTO")
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
        val viewStatus   = view.findViewById<ImageView>(R.id.imageStatus)
        val serverStatus = view.findViewById<TextView>(R.id.SERVER_STATUS)
        val viewTabla    = view.findViewById<LinearLayout>(R.id.tabla)
        val viewGoogleHeader = view.findViewById<WebView>(R.id.googleHeader)

        val viewFirstText   = view.findViewById<TextView>(R.id.firstTEXT)
        val viewFirstValue  = view.findViewById<TextView>(R.id.firstVALUE)
        val viewSecondText  = view.findViewById<TextView>(R.id.secondTEXT)
        val viewSecondValue = view.findViewById<TextView>(R.id.secondVALUE)
        val viewThirdText   = view.findViewById<TextView>(R.id.thirdTEXT)
        val viewThirdValue  = view.findViewById<TextView>(R.id.thirdVALUE)

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
                viewGoogleHeader.visibility = View.GONE
                viewTabla.visibility = View.GONE
            }else{
                viewEye.setImageResource(R.drawable.eye_close)
                viewChart.visibility = View.VISIBLE
                viewPregunta.visibility = View.VISIBLE
                viewTabla.visibility = View.VISIBLE
            }
        }

        windowManager.addView(view, params)

        //Configuracion grafica
        try{
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

            //Sample data
            setSampleChartData(chart)

            mSocket = IO.socket("http://chispitas.sytes.net")
            mSocket.on(Socket.EVENT_CONNECT) {
                Log.d("XXXXXXX", "EVENT_CONNECT")
                runOnUiThread(Runnable {
                    viewStatus.setImageResource(R.drawable.server_on)
                    serverStatus.setText(R.string.SERVER_ON)
                })
            }.on(Socket.EVENT_DISCONNECT){
                Log.d("XXXXXXX", "EVENT_DISCONNECT")
                runOnUiThread(Runnable {
                    viewStatus.setImageResource(R.drawable.server_off)
                    serverStatus.setText(R.string.SERVER_OFF)
                })
            }.on(Socket.EVENT_RECONNECTING){
                Log.d("XXXXXXX", "EVENT_RECONNECTING")
                runOnUiThread(Runnable {
                    viewStatus.setImageResource(R.drawable.server_off)
                    serverStatus.setText(R.string.SERVER_OFF)
                })
            }.on("APP_QUESTION"){
                try{
                    // RESET CHART VALUES
                    dataGrafica[0] = 0
                    dataGrafica[1] = 0
                    dataGrafica[2] = 0

                    //RESET HEADER
                    for(item in it.iterator()){
                        val mainJSON = JSONObject(item.toString())

                        val preg = mainJSON.getString("P")               // Pregunta
                        val resp: JSONArray = mainJSON.getJSONArray("R") // Respuestas

                        runOnUiThread (Runnable {
                            viewGoogleHeader.visibility = View.GONE
                            viewPregunta.text   = preg
                            viewFirstText.text  = resp.getString(0)
                            viewSecondText.text = resp.getString(1)
                            viewThirdText.text  = resp.getString(2)
                        })

                        val dataSet = BarDataSet(
                            listOf(BarEntry(0f, 0f), BarEntry(1f, 0f), BarEntry(2f, 0f)), "1,2,3"
                        )
                        dataSet.colors = listOf(
                            ContextCompat.getColor(this, R.color.FIRST),
                            ContextCompat.getColor(this, R.color.SECOND),
                            ContextCompat.getColor(this, R.color.THIRD)
                        )
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
                }catch (ex: Exception){
                    Crashlytics.logException(ex)
                }
            }.on("Grafica"){
                try{
                    for(item in it.iterator()){
                        val mainJSON      = JSONObject(item.toString())
                        val arrayValores  = mainJSON.getJSONArray("array")

                        dataGrafica[2] += arrayValores.getString(0).toIntOrNull() ?: 0
                        dataGrafica[1] += arrayValores.getString(1).toIntOrNull() ?: 0
                        dataGrafica[0] += arrayValores.getString(2).toIntOrNull() ?: 0
                        val total = dataGrafica.sum()

                        //CALCULO DE PORCENTAJES
                        var dataSet = BarDataSet(listOf(
                            BarEntry(0f, dataGrafica[0].toFloat() / total * 100),
                            BarEntry(1f, dataGrafica[1].toFloat() / total * 100),
                            BarEntry(2f, dataGrafica[2].toFloat() / total * 100)), ""
                        )
                        dataSet.setDrawValues(false)

                        //VOLTEAR GRAFICA EN CASO DE "NO"
                        if("\\bNO\\b".toRegex().findAll(viewPregunta.text).count() > 0) {
                            val A = 100 - (dataGrafica[0].toFloat() / total * 100)
                            val B = 100 - (dataGrafica[1].toFloat() / total * 100)
                            val C = 100 - (dataGrafica[2].toFloat() / total * 100)

                            dataSet = BarDataSet(listOf(
                                BarEntry(0f, A / (A+B+C) * 100 ),
                                BarEntry(1f, B / (A+B+C) * 100 ),
                                BarEntry(2f, C / (A+B+C) * 100 )), ""
                            )
                        }

                        dataSet.colors = listOf(Color.RED, Color.DKGRAY, Color.GREEN)
                        chart.data = BarData(dataSet)
                        chart.invalidate()
                    }
                }catch (ex: Exception){
                    Crashlytics.logException(ex)
                }
            }.on("EachWordSeach"){
                try{
                    for (item in it.iterator()){
                        val arrayEachWord = JSONObject(item.toString()).getJSONArray("matriz")

                        for (i in 0 until arrayEachWord.length()) {
                            val jsonOption = arrayEachWord.getJSONArray(i)
                            var suma = 0

                            for (j in 0 until jsonOption.length()){
                                val jsonEach = jsonOption.getJSONObject(j)
                                for (k in 0 until jsonEach.length()){
                                    suma +=  jsonEach.getString("count").toIntOrNull() ?: 0
                                }
                            }

                            val valor: Float = suma / 2f / jsonOption.length()

                            runOnUiThread(Runnable {
                                when(i){
                                    0 ->  {
                                        val a: Float = valor.toString().toFloatOrNull() ?: 0f
                                        val b: Float = viewSecondValue.text.toString().toFloatOrNull() ?: 0f
                                        val c: Float = viewThirdValue.text.toString().toFloatOrNull() ?: 0f

                                        viewFirstValue.text = ceil(a / (a+b+c) * 100).toInt().toString()
                                    }
                                    1 -> {
                                        val a: Float = viewFirstValue.text.toString().toFloatOrNull()  ?: 0f
                                        val b: Float = valor.toString().toFloatOrNull() ?: 0f
                                        val c: Float = viewThirdValue.text.toString().toFloatOrNull() ?: 0f

                                        viewSecondValue.text = ceil(b / (a+b+c) * 100).toInt().toString()
                                    }
                                    else ->  {
                                        val a: Float = viewFirstValue.text.toString().toFloatOrNull()  ?: 0f
                                        val b: Float = viewSecondValue.text.toString().toFloatOrNull() ?: 0f
                                        val c: Float = valor.toString().toFloatOrNull()                ?: 0f

                                        viewThirdValue.text = ceil(c / (a+b+c) * 100).toInt().toString()
                                    }
                                }

                                val a: Float = viewFirstValue.text.toString().toFloatOrNull() ?: 0f
                                val b: Float = viewSecondValue.text.toString().toFloatOrNull() ?: 0f
                                val c: Float = viewThirdValue.text.toString().toFloatOrNull() ?: 0f

                                if(a >= b && a >= c){
                                    viewFirstValue.setTextColor(ContextCompat.getColor(this, R.color.FIRST))
                                    viewSecondValue.setTextColor(Color.GRAY)
                                    viewThirdValue.setTextColor(Color.GRAY)

                                    viewFirstText.setTextColor(ContextCompat.getColor(this, R.color.FIRST))
                                    viewSecondText.setTextColor(Color.GRAY)
                                    viewThirdText.setTextColor(Color.GRAY)
                                }else if(b >= a && b >= c){
                                    viewFirstValue.setTextColor(Color.GRAY)
                                    viewSecondValue.setTextColor(ContextCompat.getColor(this, R.color.FIRST))
                                    viewThirdValue.setTextColor(Color.GRAY)

                                    viewFirstText.setTextColor(Color.GRAY)
                                    viewSecondText.setTextColor(ContextCompat.getColor(this, R.color.FIRST))
                                    viewThirdText.setTextColor(Color.GRAY)
                                }else{
                                    viewFirstValue.setTextColor(Color.GRAY)
                                    viewSecondValue.setTextColor(Color.GRAY)
                                    viewThirdValue.setTextColor(ContextCompat.getColor(this, R.color.FIRST))

                                    viewFirstText.setTextColor(Color.GRAY)
                                    viewSecondText.setTextColor(Color.GRAY)
                                    viewThirdText.setTextColor(ContextCompat.getColor(this, R.color.FIRST))
                                }
                            })
                            Log.d("XXXXX", "$jsonOption $valor")
                        }
                    }
                }catch (e: Exception){
                    Crashlytics.logException(e)
                }
            }.on("textoHeader"){
                try{
                    for (item in it.iterator()){
                        val mainJSON = JSONObject(item.toString())
                        val htmlStr  = mainJSON.getString("htmlTextoHeader")

                        if(htmlStr !== "null" && htmlStr !== ""){
                            runOnUiThread(Runnable {
                                viewGoogleHeader.visibility = View.VISIBLE
                                viewGoogleHeader.loadData(htmlStr,"text/html", "UTF-8")
                            })
                        }
                    }
                }catch (e: Exception){
                    Crashlytics.logException(e)
                }
            }

            chart.invalidate()
            mSocket.connect()
        }catch (e: Exception){
            Crashlytics.logException(e)
        }

        return START_STICKY
    }

    fun setSampleChartData(chart: HorizontalBarChart){
        val data = BarDataSet( listOf(BarEntry(0f, 10f), BarEntry(1f, 40f), BarEntry(2f, 20f)), "1,2,3"  )
        data.setDrawValues(false)
        data.colors = listOf(
            ContextCompat.getColor(this, R.color.FIRST),
            ContextCompat.getColor(this, R.color.SECOND),
            ContextCompat.getColor(this, R.color.THIRD)
        )
        chart.data = BarData(data)
        chart.xAxis.valueFormatter = IAxisValueFormatter { value, _ ->
            when(value.toInt()){
                0   -> "Unos Calzoncillos"
                2   -> "Una papa"
                else -> "Disco de oro"
            }
        }
    }

    fun runOnUiThread(runnable: Runnable) {
        handler.post(runnable)
    }

    override fun onBind(intent: Intent?): IBinder? {  return null }
}
