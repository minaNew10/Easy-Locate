package com.minabeshara.easylocate

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import com.google.android.gms.ads.*
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

class MainActivity : AppCompatActivity() {

    private var mInterstitialAd: InterstitialAd? = null
    private var TAG = "MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MobileAds.initialize(this) {}
        var adRequest = AdRequest.Builder().build()

        InterstitialAd.load(
            this,
            "ca-app-pub-3940256099942544/1033173712",
            adRequest,
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(p0: InterstitialAd) {
                    Log.d(TAG, "Ad was Loaded $p0")
                    p0.show(this@MainActivity)
                    p0.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            Log.d(TAG, "Ad was dismissed.")
                            setContentView(R.layout.activity_main)

                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError?) {
                            Log.d(TAG, "Ad failed to show.")
                        }

                        override fun onAdShowedFullScreenContent() {
                            Log.d(TAG, "Ad showed fullscreen content.")
                            mInterstitialAd = null
                        }


                    }
                }

                override fun onAdFailedToLoad(p0: LoadAdError) {
                    Log.d(TAG, p0.message)
                    mInterstitialAd = null
                }
            })
        if (mInterstitialAd != null) {
            mInterstitialAd?.show(this)
        } else {
            Log.d("TAG", "The interstitial ad wasn't ready yet.")
        }


    }
}