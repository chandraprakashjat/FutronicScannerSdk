package com.example.ftransisdkdemo_android

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import com.futronic.biometric.R

class LauncherActivity : AppCompatActivity()
{

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launcher)
        Toast.makeText(applicationContext,getString(R.string.launch_from_fabric),Toast.LENGTH_LONG).show()
        finish()
    }
}
