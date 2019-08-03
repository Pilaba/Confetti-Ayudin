package com.ayudin.confetti

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.widget.Button
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class INFOFragment : Fragment() {
    enum class MODALTYPE {
        TERMINOS, POLITICAS
    }
    private lateinit var modalInfo: modal

    override fun onCreateView( inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_info, container, false)

        view.findViewById<Button>(R.id.terminos).setOnClickListener {
            if(!modalInfo.isAdded){
                modalInfo.modalType = MODALTYPE.TERMINOS
                modalInfo.show(this.fragmentManager,"INFO_TERMINOS_CONDICIONES");
            }
        }
        view.findViewById<Button>(R.id.politicas).setOnClickListener {
            if(!modalInfo.isAdded){
                modalInfo.modalType = MODALTYPE.POLITICAS
                modalInfo.show(this.fragmentManager,"INFO_TERMINOS_CONDICIONES");
            }
        }

        return view
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modalInfo = modal()
    }

    class modal: BottomSheetDialogFragment() {
        var modalType: MODALTYPE = MODALTYPE.TERMINOS

        override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
            val view = inflater.inflate(R.layout.bottom_sheet, null)
            if(modalType == MODALTYPE.TERMINOS){
                view.findViewById<WebView>(R.id.webView).loadUrl("file:///android_res/raw/terminos.html")
            }else{
                view.findViewById<WebView>(R.id.webView).loadUrl("file:///android_res/raw/politicas.html")
            }
            return view
        }
    }
}


