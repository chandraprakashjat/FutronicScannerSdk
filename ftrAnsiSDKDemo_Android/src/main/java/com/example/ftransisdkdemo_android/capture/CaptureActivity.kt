package com.example.ftransisdkdemo_android.capture

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.*
import android.provider.CalendarContract
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ftransisdkdemo_android.AppException
import com.example.ftransisdkdemo_android.util.CreateFingerBitmap
import com.example.ftransisdkdemo_android.util.GetDatabaseDir
import com.example.ftransisdkdemo_android.util.requestPermission
import com.example.ftransisdkdemo_android.verify.VerifyActivity
import com.futronic.biometric.R
import com.futronictech.AnsiSDKLib
import com.futronictech.UsbDeviceDataExchangeImpl
import kotlinx.android.synthetic.main.capture_layout.*
import java.io.File
import java.io.FileOutputStream
import android.net.Uri.fromParts
import android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
import androidx.core.app.ComponentActivity.ExtraData
import android.icu.lang.UCharacter.GraphemeClusterBreak.T
import androidx.core.content.ContextCompat.getSystemService
import android.R.attr.name
import android.net.Uri
import android.os.*
import android.provider.Settings


class CaptureActivity :AppCompatActivity()
{
    private var captureBiometricPath: String="";
    private var isCaptureSuccessfully: Boolean = false;

    private var usb_host_ctx: UsbDeviceDataExchangeImpl? = null
    val MESSAGE_SHOW_MSG = 1
    val MESSAGE_SHOW_IMAGE = 2
    val MESSAGE_SHOW_ERROR_MSG = 3
    val MESSAGE_END_OPERATION = 4

    //Pending operations
    private val OPERATION_CAPTURE = 1
    private var mPendingOperation:Int = 0
    private var mOperationThread: OperationThread? = null
    private var mBitmapFP: Bitmap? = null
    private var mSyncDir: File? = null;
    private val kAnsiTemplatePostfix = "(ANSI)"
    private val kIsoTemplatePostfix = "(ISO)"
    /**
     * A database directory name.
     */
    var mDbDir: String?=null;

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(com.futronic.biometric.R.layout.capture_layout)
        usb_host_ctx = UsbDeviceDataExchangeImpl(this, mHandler)
        mSyncDir = this.getExternalFilesDir(null)


        try {
            mDbDir = GetDatabaseDir(this)
        } catch (e: AppException) {
            Toast.makeText(applicationContext, "Initialization failed. Application will be close.\nError description: " + e.message, Toast.LENGTH_SHORT).show()
            System.exit(0)
        }

        mButtonCapture.setOnClickListener(View.OnClickListener {

           var boolean= requestPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE,this);

            if (!boolean)
            {
                Toast.makeText(applicationContext, "Please enable Storage permission!", Toast.LENGTH_SHORT).show()

                return@OnClickListener
            }




            if (Build.VERSION.SDK_INT >= 30)
            {

                if(!Environment.isExternalStorageManager())
                {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", packageName, null)
                    intent.setData(uri)
                    startActivity(intent)
                    Toast.makeText(applicationContext, "Please enable Manage External Storage permission!", Toast.LENGTH_SHORT).show()

                    return@OnClickListener
                }
            }



            if (true) {
                if (usb_host_ctx?.OpenDevice(0, true)==true) {
                    StartCapture()
                } else {
                    if (usb_host_ctx?.IsPendingOpen()==true) {
                        mPendingOperation = OPERATION_CAPTURE
                    } else {

                        setInfoText(getString(com.futronic.biometric.R.string.no_capture),true);
                    }
                }
            } else {
                StartCapture()
            }
        })
    }



    private fun setInfoText(text:String,isError:Boolean)
    {
        textView_error.text=(text)
       // var icon:Int;
        if (isError)
        {
            textView_error.setTextColor(ContextCompat.getColor(this, com.futronic.biometric.R.color.red));
            //icon=R.drawable.error
        }
        else
        {
            textView_error.setTextColor(ContextCompat.getColor(this, com.futronic.biometric.R.color.black));
            //icon= 0
        }
        textView_error.setCompoundDrawablesWithIntrinsicBounds(0, 0,0, 0)


    }



    private val mHandler = object : Handler() {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MESSAGE_SHOW_MSG -> {
                    val showMsg = msg.obj as String
                    setInfoText(showMsg,false);
                }

                MESSAGE_SHOW_ERROR_MSG -> {
                    val showErr = msg.obj as String
                    setInfoText(showErr,true);
                }

                MESSAGE_SHOW_IMAGE ->
                {
                    imageView_biometric.setImageBitmap(mBitmapFP)

                }
                MESSAGE_END_OPERATION -> EndOperation()

                UsbDeviceDataExchangeImpl.MESSAGE_ALLOW_DEVICE -> {
                    if (usb_host_ctx?.ValidateContext()==true) {
                        when (mPendingOperation) {
                            OPERATION_CAPTURE -> StartCapture()
                        }
                    } else {
                        setInfoText(getString(com.futronic.biometric.R.string.cant_open),true);
                    }
                }

                UsbDeviceDataExchangeImpl.MESSAGE_DENY_DEVICE -> {
                    setInfoText(getString(com.futronic.biometric.R.string.deny_device),true);
                }
            }
        }
    }




    private fun StartCapture() {
        isCaptureSuccessfully=false;
        PrepareOperation()
        mOperationThread = CaptureThread(true)
        mOperationThread?.start()
    }


    private fun PrepareOperation() {
        setInfoText(getString(com.futronic.biometric.R.string.put_finger),false);
        EnableControls(false)
    }

    private fun EnableControls(enable: Boolean) {
        mButtonCapture.isEnabled = enable
    }


    private open inner class OperationThread : Thread() {
        private var mCanceled = false

        fun IsCanceled(): Boolean {
            return mCanceled
        }

        fun Cancel() {
            mCanceled = true

            try {
                this.join()    //5sec timeout
            } catch (e: InterruptedException) {
                // TODO Auto-generated catch block
                e.printStackTrace()
            }

        }
    }


    private inner class CaptureThread(useUsbHost: Boolean) : OperationThread() {
        private var ansi_lib: AnsiSDKLib? = null
        private var mUseUsbHost = true

        init {
            ansi_lib = AnsiSDKLib()
            if (!ansi_lib!!.SetGlobalSyncDir(mSyncDir.toString())) {
                mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()

            }
            mUseUsbHost = useUsbHost
        }

        override fun run() {
            var dev_open = false

            try {
                if (mUseUsbHost) {
                    if (!ansi_lib!!.OpenDeviceCtx(usb_host_ctx)) {
                        mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                        mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
                        return
                    }
                } else {
                    if (!ansi_lib!!.OpenDevice(0)) {
                        mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                        mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
                        return
                    }
                }

                dev_open = true

                if (!ansi_lib!!.FillImageSize()) {
                    mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                    mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
                    return
                }


                while (true) {
                    if (IsCanceled()) {
                        break
                    }

                    val img_buffer = ByteArray(ansi_lib!!.GetImageSize())


                    val lT1 = SystemClock.uptimeMillis()
                    if (ansi_lib!!.CaptureImage(img_buffer)) {
                        val op_time = SystemClock.uptimeMillis() - lT1

                        val op_info = String.format("Capture done. Time is %d(ms)", op_time)
                        mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, op_info).sendToTarget()

                        mBitmapFP = CreateFingerBitmap(
                                ansi_lib!!.GetImageWidth(),
                                ansi_lib!!.GetImageHeight(),
                                img_buffer)

                        isCaptureSuccessfully=true;
                        mHandler.obtainMessage(MESSAGE_SHOW_IMAGE).sendToTarget()
                        break
                    } else {
                        val lastError = ansi_lib!!.GetErrorCode()

                        if (lastError == AnsiSDKLib.FTR_ERROR_EMPTY_FRAME ||
                                lastError == AnsiSDKLib.FTR_ERROR_NO_FRAME ||
                                lastError == AnsiSDKLib.FTR_ERROR_MOVABLE_FINGER) {
                            Thread.sleep(100)
                            continue
                        } else {
                            val error = String.format("Capture failed. Error: %s.", ansi_lib!!.GetErrorMessage())
                            mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget()
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, e.message).sendToTarget()
            }

            if (dev_open) {
                ansi_lib!!.CloseDevice()
            }

            mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()


            if(isCaptureSuccessfully)
            {
               StartCreate();
            }
        }

    }




    private fun EndOperation() {
        EnableControls(true)
    }





///////////////////////

    private fun StartCreate() {
        var tmplName =  getFilename(System.currentTimeMillis().toString());

        PrepareOperation()
        mOperationThread = CreateThread(
                true,
                0,
                true,
                false,
                tmplName)
        mOperationThread?.start()
    }


    private inner class CreateThread(useUsbHost: Boolean, finger: Int, saveAnsi: Boolean, saveIso: Boolean, tmplName: String) : OperationThread() {
        private var ansi_lib: AnsiSDKLib? = null
        private var mUseUsbHost = true
        private var mFinger = 0
        private var mSaveAnsi = true
        private var mSaveIso = false
        private var mTmplName = ""

        init {
            ansi_lib = AnsiSDKLib()
            if (!ansi_lib!!.SetGlobalSyncDir(mSyncDir.toString())) {
                mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
            }
            mUseUsbHost = useUsbHost

            mFinger = finger
            mSaveAnsi = saveAnsi
            mSaveIso = saveIso
            mTmplName = tmplName
        }

        override fun run() {
            var dev_open = false

            try {
                if (mUseUsbHost) {
                    if (!ansi_lib!!.OpenDeviceCtx(usb_host_ctx)) {
                        mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                        mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
                        return
                    }
                } else {
                    if (!ansi_lib!!.OpenDevice(0)) {
                        mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                        mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
                        return
                    }
                }

                dev_open = true

                if (!ansi_lib!!.FillImageSize()) {
                    mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                    mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
                    return
                }

                val img_buffer = ByteArray(ansi_lib!!.GetImageSize())

                while (true) {
                    if (IsCanceled()) {
                        break
                    }

                    val tmplSize = ansi_lib!!.GetMaxTemplateSize()
                    val template = ByteArray(tmplSize)
                    val templateIso = ByteArray(tmplSize)
                    val realSize = IntArray(1)
                    val realIsoSize = IntArray(1)

                    val lT1 = SystemClock.uptimeMillis()
                    if (ansi_lib!!.CreateTemplate(mFinger, img_buffer, template, realSize)) {
                        val op_time = SystemClock.uptimeMillis() - lT1

                        val op_info = String.format("Create done. Time is %d(ms)", op_time)
                        mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, op_info).sendToTarget()

                        mBitmapFP = CreateFingerBitmap(
                                ansi_lib!!.GetImageWidth(),
                                ansi_lib!!.GetImageHeight(),
                                img_buffer)
                        mHandler.obtainMessage(MESSAGE_SHOW_IMAGE).sendToTarget()

                        if (mSaveAnsi) {
                            SaveTemplate(mTmplName + kAnsiTemplatePostfix, template, realSize[0])
                        }

                        if (mSaveIso) {
                            realIsoSize[0] = tmplSize
                            if (ansi_lib!!.ConvertAnsiTemplateToIso(template, templateIso, realIsoSize)) {
                                SaveTemplate(mTmplName + kIsoTemplatePostfix, templateIso, realIsoSize[0])
                            } else {
                                val error = String.format("Conver to failed. Error: %s.", ansi_lib!!.GetErrorMessage())
                                mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget()
                            }
                        }

                        break
                    } else {
                        val lastError = ansi_lib!!.GetErrorCode()

                        if (lastError == AnsiSDKLib.FTR_ERROR_EMPTY_FRAME ||
                                lastError == AnsiSDKLib.FTR_ERROR_NO_FRAME ||
                                lastError == AnsiSDKLib.FTR_ERROR_MOVABLE_FINGER) {
                            Thread.sleep(100)
                            continue
                        } else {
                            val error = String.format("Create failed. Error: %s.", ansi_lib!!.GetErrorMessage())
                            mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget()
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, e.message).sendToTarget()
            }

            if (dev_open) {
                ansi_lib!!.CloseDevice()
            }

            mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
        }

        private fun SaveTemplate(name: String, template: ByteArray, size: Int) {

            var fs: FileOutputStream? = null
            var f: File? = null

            try {
                f = File(name)
                fs = FileOutputStream(f)

                val writeTemplate = ByteArray(size)
                System.arraycopy(template, 0, writeTemplate, 0, size)
                fs.write(writeTemplate)
                fs.close()
                captureBiometricPath = name
                sendPatAndExit(11)
            } catch (e: Exception) {
                val error = String.format("Failed to save template to file %s. Error: %s.", name, e.toString())
                mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget()
            }

        }
    }
    fun onSuccessfullyCapture() {
        val dlgAlert = AlertDialog.Builder(this)
        dlgAlert.setMessage("Biometric Capture successfully.!")
        dlgAlert.setTitle("Biometric Capture Successfully")
        dlgAlert.setPositiveButton("OK",
                DialogInterface.OnClickListener { dialog, whichButton ->
                    sendPatAndExit(11);

                }
        )
        dlgAlert.setCancelable(false)
        dlgAlert.create().show()
    }
    private fun sendPatAndExit(requestCode:Int)
    {

        if(captureBiometricPath!=null && captureBiometricPath.isNotEmpty())
        {
            val intent = Intent()
            val bundle = Bundle()
            bundle.putBoolean("success", true)
            bundle.putInt("code", 0)
            bundle.putString("path", captureBiometricPath)
            intent.putExtras(bundle)
            setResult(requestCode, intent)
            finish();
        }else
        {
            Toast.makeText(this,"Biometric File not stored. Please Enable Storage permission and Capture Again",Toast.LENGTH_LONG).show();
        }




    }


    override fun onDestroy() {
        super.onDestroy()
        if (mOperationThread != null) {
            mOperationThread?.Cancel()
        }

        if (usb_host_ctx != null) {
            usb_host_ctx?.CloseDevice()
            usb_host_ctx?.Destroy()
            usb_host_ctx = null
        }

        System.exit(0)
    }


    private fun getFilename(s: String): String {
        val filepath = Environment.getExternalStorageDirectory().path
        val file = File(filepath, "BiometricScanner")

        if (!file.exists()) {
            file.mkdirs()
        }

        return file.absolutePath + "/" + s
    }

}