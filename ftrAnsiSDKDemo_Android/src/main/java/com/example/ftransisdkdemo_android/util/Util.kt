package com.example.ftransisdkdemo_android.util

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import com.example.ftransisdkdemo_android.AppException
import java.io.File
import androidx.core.app.ActivityCompat.requestPermissions
import android.os.Build
import android.os.Environment
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


fun CreateFingerBitmap(imgWidth: Int, imgHeight: Int, imgBytes: ByteArray): Bitmap {
    val pixels = IntArray(imgWidth * imgHeight)
    for (i in 0 until imgWidth * imgHeight) {
        pixels[i] = imgBytes[i].toInt()
    }

    val emptyBmp = Bitmap.createBitmap(pixels, imgWidth, imgHeight, Bitmap.Config.RGB_565)

    val width: Int
    val height: Int
    height = emptyBmp.height
    width = emptyBmp.width

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
    val c = Canvas(result)
    val paint = Paint()
    val cm = ColorMatrix()
    cm.setSaturation(0f)
    val f = ColorMatrixColorFilter(cm)
    paint.colorFilter = f
    c.drawBitmap(emptyBmp, 0f, 0f, paint)

    return result
}

@Throws(AppException::class)
 fun GetDatabaseDir(context: Context): String {
    val szDbDir: String
    val extStorageDirectory = context.getExternalFilesDir(null)
    val Dir = File(extStorageDirectory, "Android//FtrAnsiSdkDb")
    if (Dir.exists()) {
        if (!Dir.isDirectory) {
            throw AppException("Can not create database directory " + Dir.absolutePath +
                    ". File with the same name already exist.")
        }
    } else {
        try {
            Dir.mkdirs()
        } catch (e: SecurityException) {
            throw AppException("Can not create database directory " + Dir.absolutePath +
                    ". Access denied.")
        }

    }
    szDbDir = Dir.absolutePath
    return szDbDir
}

private fun getFilename(s: String): String {
    val filepath = Environment.getExternalStorageDirectory().path
    val file = File(filepath, "BiometricScanner")

    if (!file.exists()) {
        file.mkdirs()
    }

    return file.absolutePath + "/" + s
}


fun requestPermission(permission: String,context: Activity): Boolean {
    val isGranted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    if (!isGranted) {
        ActivityCompat.requestPermissions(
                context,
                arrayOf(permission),
                11
        )
    }
    return isGranted
}