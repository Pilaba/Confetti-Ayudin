package com.apps.confettibot

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import br.com.simplepass.loadingbutton.customViews.CircularProgressButton
import com.bumptech.glide.Glide
import com.bumptech.glide.request.RequestOptions
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.reward.RewardItem
import com.google.android.gms.ads.reward.RewardedVideoAd
import com.google.android.gms.ads.reward.RewardedVideoAdListener
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.functions.FirebaseFunctions
import es.dmoral.toasty.Toasty

class BOTFragment : Fragment(), RewardedVideoAdListener {
    private lateinit var mRewardedVideoAd : RewardedVideoAd
    private lateinit var functions        : FirebaseFunctions
    private lateinit var auth             : FirebaseAuth
    private lateinit var dbUserRef        : DatabaseReference
    private lateinit var adButton         : CircularProgressButton
    private var videoAdWatched = false
    private var DBUser                    : User? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mRewardedVideoAd = MobileAds.getRewardedVideoAdInstance(activity)
        mRewardedVideoAd.rewardedVideoAdListener = this
        functions = FirebaseFunctions.getInstance()

        auth = FirebaseAuth.getInstance()
        if(auth.currentUser == null){
            auth.signInAnonymously().addOnCompleteListener {
                if(it.isSuccessful){  setFirebaseListener()  }
            }
        }else{
            setFirebaseListener()
        }


    }

    private fun setFirebaseListener(){
        dbUserRef = FirebaseDatabase.getInstance().getReference("users/"+ FirebaseAuth.getInstance().currentUser?.uid)
        dbUserRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                DBUser = dataSnapshot.getValue(User::class.java)
                DBUser?.let {
                    if(it.watchVideo){
                        view?.findViewById<Button>(R.id.lock)?.visibility = View.GONE
                        view?.findViewById<Button>(R.id.GO)?.alpha = 1f
                    }else{
                        view?.findViewById<Button>(R.id.lock)?.visibility = View.VISIBLE
                        view?.findViewById<Button>(R.id.GO)?.alpha = 0.3f
                    }
                    view?.findViewById<Button>(R.id.GO)?.isEnabled = it.watchVideo
                }
            }
            override fun onCancelled(error: DatabaseError) { }
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val view = inflater.inflate(R.layout.fragment_bot, container, false)
        view.findViewById<Button>(R.id.GO).setOnClickListener {
            //permiso OVERLAY
            if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)){
                val permissionIntent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity?.packageName}"))
                startActivityForResult(permissionIntent, 2)
            }else{
                startService()
            }
        }

        val mAdView = view.findViewById<AdView>(R.id.adView)
        mAdView.loadAd(AdRequest.Builder().build())

        context?.let {
            Glide.with(it)
                .load(R.drawable.konfetti)
                .apply(RequestOptions.circleCropTransform())
                .into( view.findViewById(R.id.confettiImage))
            Glide.with(it)
                .load(R.drawable.mexico)
                .apply(RequestOptions.circleCropTransform())
                .into( view.findViewById(R.id.mxImage))
        }

        adButton = view.findViewById(R.id.adBtn)
        adButton.setOnClickListener {
            adButton.startAnimation()
            videoAdWatched = false
            mRewardedVideoAd.loadAd("ca-app-pub-3940256099942544/5224354917", AdRequest.Builder().build())
        }
        return view
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if(requestCode == 2 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)){
            startService()
        } else {
            context?.let{
                Toasty.error(it,
                    "Es necesario ese permiso para mostrar las posibles respuestas mientras juegas!", Toast.LENGTH_LONG, true).show()
            }
        }
    }

    private fun startService(){
        activity?.let {
            it.startService(Intent(it, MyService::class.java))
            it.finishAndRemoveTask()
        }
    }

    ///////////// CALLBACKS REWARD VIDEO /////////////////
    override fun onRewardedVideoAdLoaded() {
        if (mRewardedVideoAd.isLoaded) {
            mRewardedVideoAd.show()
        }
        adButton.revertAnimation()
    }
    override fun onRewarded(p0: RewardItem?) {
        videoAdWatched = true
        functions.getHttpsCallable("onvideoWatched").call().addOnSuccessListener {
            context?.let{
                Toasty.success(it,
                    "Felicidades, haz obtenido acceso para el proximo juego!", Toast.LENGTH_LONG, true).show()
            }
        }
    }
    override fun onRewardedVideoAdFailedToLoad(p0: Int) {
        context?.let{
            Toasty.error(it,
                "Error reproduciendo el video, Intenta más tarde", Toast.LENGTH_LONG, true).show()
        }
        adButton.revertAnimation()
    }

    override fun onRewardedVideoAdClosed() {
        if(!videoAdWatched && DBUser?.watchVideo == false){
            context?.let{
                Toasty.info(it,"Es necesario ver el video completo para obetener la recompensa", Toast.LENGTH_LONG, true).show()
            }
        }
        adButton.revertAnimation()
    }

    override fun onRewardedVideoCompleted() { }
    override fun onRewardedVideoAdLeftApplication() { }
    override fun onRewardedVideoAdOpened() {  }
    override fun onRewardedVideoStarted() { }


    data class User(
        var watchVideo: Boolean = false
    )
}
