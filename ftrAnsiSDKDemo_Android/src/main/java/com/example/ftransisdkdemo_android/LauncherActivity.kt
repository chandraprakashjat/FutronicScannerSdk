package com.example.ftransisdkdemo_android

import android.content.Context
import android.net.wifi.WifiManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle



import android.widget.Toast



class LauncherActivity : AppCompatActivity()
{

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(com.futronic.biometric.R.layout.activity_launcher)
        Toast.makeText(applicationContext,getString(com.futronic.biometric.R.string.launch_from_fabric),Toast.LENGTH_LONG).show()


       finish();


    }



}
