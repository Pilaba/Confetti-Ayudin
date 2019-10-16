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
import android.widget.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.crashlytics.android.Crashlytics
import com.github.nkzawa.socketio.client.IO
import com.github.nkzawa.socketio.client.Socket
import org.json.JSONArray
import org.json.JSONObject
import java.lang.Exception

class MyService : Service() {
    lateinit var mSocket      : Socket
    lateinit var handler      : Handler
    lateinit var serviceView  : View
    lateinit var templateError: LinearLayout
    val dataGrafica = arrayOf(0,0,0)
    var colorVerde                 : Int = 0x0B6623
    var colorGris                  : Int = 0xAAAAAA
    var banderaNaN                 : Boolean = false

    override fun onCreate() {
        super.onCreate()
        val channelId = "CHANNEL-01"
        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("AYUDIN").setContentText("ESTOY LISTO")
            .setOngoing(true).setSmallIcon(R.drawable.server_on)
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

        colorVerde = ContextCompat.getColor(this, R.color.FIRST)
        colorGris = ContextCompat.getColor(this, android.R.color.darker_gray)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        banderaNaN = false
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

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        params.format = PixelFormat.TRANSLUCENT
        params.gravity = Gravity.TOP

        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        serviceView = inflater.inflate(R.layout.overlay_view, null)

        val viewPregunta = serviceView.findViewById<TextView>(R.id.pregunta)
        val viewChart    = serviceView.findViewById<LinearLayout>(R.id.chart)
        val viewClose    = serviceView.findViewById<ImageView>(R.id.close)
        val viewEye      = serviceView.findViewById<ImageView>(R.id.eye)
        val viewStatus   = serviceView.findViewById<ImageView>(R.id.imageStatus)
        val serverStatus = serviceView.findViewById<TextView>(R.id.SERVER_STATUS)
        val viewGoogleHeader = serviceView.findViewById<WebView>(R.id.googleHeader)
        templateError    = serviceView.findViewById(R.id.templateError)

        //CHART VALUES PROGRESS BAR
        val CHARTA = serviceView.findViewById<TextView>(R.id.CHARTA)
        val CHARTB = serviceView.findViewById<TextView>(R.id.CHARTB)
        val CHARTC = serviceView.findViewById<TextView>(R.id.CHARTC)

        val PROGRESSA = serviceView.findViewById<ProgressBar>(R.id.PROGRESSA)
        val PROGRESSB = serviceView.findViewById<ProgressBar>(R.id.PROGRESSB)
        val PROGRESSC = serviceView.findViewById<ProgressBar>(R.id.PROGRESSC)

        //Listener buttons
        viewClose.setOnClickListener {
            try{
                stopForeground(true)
                stopSelf()
                windowManager.removeView(serviceView)
            }catch(ex: Exception){
                Crashlytics.logException(ex)
            }
        }

        viewEye.setOnClickListener {
            if(viewChart.visibility == View.VISIBLE){
                viewEye.setImageResource(R.drawable.eye_open)
                viewChart.visibility = View.GONE
                viewPregunta.visibility = View.GONE
                viewGoogleHeader.visibility = View.GONE
                templateError.visibility = View.GONE
            }else{
                viewEye.setImageResource(R.drawable.eye_close)
                viewChart.visibility = View.VISIBLE
                viewPregunta.visibility = View.VISIBLE
                if(banderaNaN){
                    templateError.visibility = View.VISIBLE
                }
            }
        }

        windowManager.addView(serviceView, params)

        //Configuracion grafica
        try{
            mSocket = IO.socket("http://chispitas.sytes.net")
            mSocket.on(Socket.EVENT_CONNECT) {
                runOnUiThread(Runnable {
                    viewStatus.setImageResource(R.drawable.server_on)
                    serverStatus.setText(R.string.SERVER_ON)
                })
            }.on(Socket.EVENT_DISCONNECT){
                runOnUiThread(Runnable {
                    viewStatus.setImageResource(R.drawable.server_off)
                    serverStatus.setText(R.string.SERVER_OFF)
                })
            }.on(Socket.EVENT_RECONNECTING){
                runOnUiThread(Runnable {
                    viewStatus.setImageResource(R.drawable.server_off)
                    serverStatus.setText(R.string.SERVER_OFF)
                })
            }.on("APP_QUESTION"){
                // RESET CHART VALUES
                dataGrafica[0] = 0
                dataGrafica[1] = 0
                dataGrafica[2] = 0

                val mainJSON = JSONObject(it.iterator().next().toString())

                val preg = mainJSON.getString("P")               // Pregunta
                val resp: JSONArray = mainJSON.getJSONArray("R") // Respuestas

                banderaNaN = resp.getString(0).equals("NaN", ignoreCase = true) or
                        resp.getString(1).equals("NaN", ignoreCase = true) or
                        resp.getString(2).equals("NaN", ignoreCase = true)

                runOnUiThread (Runnable {
                    viewGoogleHeader.visibility = View.GONE
                    viewPregunta.text   = preg

                    //CHART PROGRESS VALUES
                    CHARTA.text = resp.getString(0)
                    CHARTB.text = resp.getString(1)
                    CHARTC.text = resp.getString(2)
                })
            }.on("Grafica"){
                runOnUiThread(Runnable {
                    val arrayValores  = JSONObject(it.iterator().next().toString()).getJSONArray("array")
                    dataGrafica[2] += arrayValores.getString(0).toIntOrNull() ?: 0
                    dataGrafica[1] += arrayValores.getString(1).toIntOrNull() ?: 0
                    dataGrafica[0] += arrayValores.getString(2).toIntOrNull() ?: 0

                    val total = dataGrafica.sum()

                    //CALCULO DE PORCENTAJES
                    if(total >= 1){
                        //VOLTEAR GRAFICA EN CASO DE "NO"
                        if("\\bNO\\b".toRegex().findAll(viewPregunta.text).count() > 0) {
                            val A: Float    = 100 - (dataGrafica[2].toFloat() / total * 100)
                            val B: Float    = 100 - (dataGrafica[1].toFloat() / total * 100)
                            val C: Float    = 100 - (dataGrafica[0].toFloat() / total * 100)
                            val suma: Float = A + B + C
                            if(suma >= 1){
                                PROGRESSA.progress = (A / suma * 100).toInt()
                                PROGRESSB.progress = (B / suma * 100).toInt()
                                PROGRESSC.progress = (C / suma * 100).toInt()
                            }
                        }else{
                            PROGRESSA.progress = (dataGrafica[2].toFloat() / total * 100).toInt()
                            PROGRESSB.progress = (dataGrafica[1].toFloat() / total * 100).toInt()
                            PROGRESSC.progress = (dataGrafica[0].toFloat() / total * 100).toInt()
                        }
                    }
                })
            }.on("EachWordSearchMovil"){
                runOnUiThread(Runnable {
                    val arrayValores  = JSONObject(it.iterator().next().toString()).getJSONArray("array")
                    dataGrafica[2] += arrayValores.getString(0).toIntOrNull() ?: 0
                    dataGrafica[1] += arrayValores.getString(1).toIntOrNull() ?: 0
                    dataGrafica[0] += arrayValores.getString(2).toIntOrNull() ?: 0

                    val total = dataGrafica.sum()

                    //CALCULO DE PORCENTAJES
                    if(total >= 1){
                        //VOLTEAR GRAFICA EN CASO DE "NO"
                        if("\\bNO\\b".toRegex().findAll(viewPregunta.text).count() > 0) {
                            val A: Float    = 100 - (dataGrafica[2].toFloat() / total * 100)
                            val B: Float    = 100 - (dataGrafica[1].toFloat() / total * 100)
                            val C: Float    = 100 - (dataGrafica[0].toFloat() / total * 100)
                            val suma: Float = A + B + C
                            if(suma >= 1){
                                PROGRESSA.progress = (A / suma * 100).toInt()
                                PROGRESSB.progress = (B / suma * 100).toInt()
                                PROGRESSC.progress = (C / suma * 100).toInt()
                            }
                        }else{
                            PROGRESSA.progress = (dataGrafica[2].toFloat() / total * 100).toInt()
                            PROGRESSB.progress = (dataGrafica[1].toFloat() / total * 100).toInt()
                            PROGRESSC.progress = (dataGrafica[0].toFloat() / total * 100).toInt()
                        }
                    }
                })
            }.on("textoHeader"){
                var htmlStr  = JSONObject(it.iterator().next().toString()).getString("htmlTextoHeader")
                Log.d("XXXX", htmlStr)
                if(htmlStr !== "null" && htmlStr !== ""){
                    // HTML MATCH
                    htmlStr = CHARTA.text.toString().toRegex(RegexOption.IGNORE_CASE)
                        .replace(htmlStr, "<span style='color: green; font-size: 25px'>"+CHARTA.text+"</span>")

                    htmlStr = CHARTB.text.toString().toRegex(RegexOption.IGNORE_CASE)
                        .replace(htmlStr, "<span style='color: black; font-size: 25px'>"+CHARTB.text+"</span>")

                    htmlStr = CHARTC.text.toString().toRegex(RegexOption.IGNORE_CASE)
                        .replace(htmlStr, "<span style='color: red; font-size: 25px'>"+CHARTC.text+"</span>")

                    Log.d("XXXX", htmlStr)
                    runOnUiThread(Runnable {
                        viewGoogleHeader.visibility = View.VISIBLE
                        viewGoogleHeader.loadDataWithBaseURL(null, htmlStr,"text/html", "UTF-8", null)
                    })
                }
            }

            mSocket.connect()
        }catch (e: Exception){
            Crashlytics.logException(e)
        }

        return START_STICKY
    }

    fun runOnUiThread(runnable: Runnable) {
        try{
            if(banderaNaN){
                handler.post {
                    templateError.visibility = View.VISIBLE
                }
            }else{
                handler.post {
                    templateError.visibility = View.GONE
                }
                handler.post(runnable)
            }
        }catch (e: Exception){
            Log.d("xxxx", "dsadas")
            Crashlytics.logException(e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {  return null }
}
