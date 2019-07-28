package com.apps.confettibot

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentPagerAdapter
import kotlinx.android.synthetic.main.activity_main.*
import android.view.Menu
import android.view.MenuItem
import com.google.android.gms.ads.MobileAds
import android.content.Intent

class MainActivity : AppCompatActivity() {
    val TAB_TITLES = arrayOf( R.string.tab_text_1, R.string.tab_text_2 )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(findViewById(R.id.my_toolbar))
        MobileAds.initialize(this, "ca-app-pub-5846226462716541~1162981272")

        // Viewpager
        view_pager.adapter = object : FragmentPagerAdapter(supportFragmentManager) {
            override fun getItem(position: Int): Fragment {
                return when (position) {
                    0 -> BOTFragment()
                    else -> INFOFragment()
                }
            }
            override fun getPageTitle(position: Int): CharSequence? {
                return resources.getString(TAB_TITLES[position])
            }
            override fun getCount(): Int {
                return 2
            }
        }
        tabs.setupWithViewPager(view_pager)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.my_menu, menu)
        return  true
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        val sharingIntent = Intent(Intent.ACTION_SEND)
        sharingIntent.putExtra(Intent.EXTRA_SUBJECT, "Compartir app")
        sharingIntent.putExtra(Intent.EXTRA_TEXT, "market://details?id=$packageName")
        sharingIntent.type = "text/plain"
        startActivity(Intent.createChooser(sharingIntent, "Compartir con"))
        return true
    }
}