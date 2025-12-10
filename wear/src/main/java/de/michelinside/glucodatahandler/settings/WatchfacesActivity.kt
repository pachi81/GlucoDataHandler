package de.michelinside.glucodatahandler.settings

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import de.michelinside.glucodatahandler.common.utils.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import de.michelinside.glucodatahandler.R

class WatchfacesActivity : AppCompatActivity() {
    private val LOG_ID = "GDH.Main.Watchfaces"

    override fun onCreate(savedInstanceState: Bundle?) {
        try {
            Log.v(LOG_ID, "onCreate called")
            super.onCreate(savedInstanceState)
            setContentView(R.layout.activity_watchfaces)

            findViewById<Button>(R.id.btnDMMWatchfaces)?.setOnClickListener {
                try {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(this.resources.getText(de.michelinside.glucodatahandler.common.R.string.playstore_dmm_watchfaces).toString())
                    )
                    this.startActivity(browserIntent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "setOnClickListener exception for watchfaces" + exc.toString())
                }
            }

            findViewById<Button>(R.id.btnGDCWatchfaces)?.setOnClickListener {
                try {
                    val browserIntent = Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(this.resources.getText(de.michelinside.glucodatahandler.common.R.string.playstore_gdc_watchfaces).toString())
                    )
                    this.startActivity(browserIntent)
                } catch (exc: Exception) {
                    Log.e(LOG_ID, "setOnClickListener exception for watchfaces" + exc.toString())
                }
            }

        } catch( exc: Exception ) {
            Log.e(LOG_ID, exc.message + "\n" + exc.stackTraceToString())
        }
    }

}