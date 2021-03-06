package com.example.ftransisdkdemo_android.verify

import android.Manifest
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.View
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import android.widget.Toast.makeText
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.ftransisdkdemo_android.AppException
import com.example.ftransisdkdemo_android.util.CreateFingerBitmap
import com.example.ftransisdkdemo_android.util.GetDatabaseDir
import com.example.ftransisdkdemo_android.util.requestPermission
import com.futronic.biometric.R
import com.futronictech.AnsiSDKLib
import com.futronictech.AnsiSDKLib.FTR_ANSISDK_MATCH_SCORE_MEDIUM
import com.futronictech.UsbDeviceDataExchangeImpl
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.capture_layout.*
import kotlinx.android.synthetic.main.capture_layout.textView_error
import kotlinx.android.synthetic.main.verify_activity.*
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream

class VerifyActivity : AppCompatActivity()
{
    private var mTmplName: String="";
    private var usb_host_ctx: UsbDeviceDataExchangeImpl? = null
    val MESSAGE_SHOW_MSG = 1
    val MESSAGE_SHOW_IMAGE = 2
    val MESSAGE_SHOW_ERROR_MSG = 3
    val MESSAGE_END_OPERATION = 4
    val MESSAGE_SUCCESSFULL =5

    //Pending operations

    private val OPERATION_VERIFY = 3
    private var mPendingOperation:Int = 0
    private var mOperationThread: OperationThread? = null
    private var mBitmapFP: Bitmap? = null
    private var mSyncDir: File? = null;
    /**
     * A database directory name.
     */
    var mDbDir: String?=null;
    var captureFilePath:String?=null;


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.verify_activity)
        usb_host_ctx = UsbDeviceDataExchangeImpl(this, mHandler)

        captureFilePath = intent.extras?.getString("fingerfile")?:"";


            if(captureFilePath?.isEmpty()==true)
            {
                Toast.makeText(applicationContext, "File Not Found", Toast.LENGTH_SHORT).show()
                finish()
            }


        mSyncDir = this.getExternalFilesDir(null)


        try {
            mDbDir = GetDatabaseDir(this)
        } catch (e: AppException) {
            Toast.makeText(applicationContext, "Initialization failed. Application will be close.\nError description: " + e.message, Toast.LENGTH_SHORT).show()
            System.exit(0)
        }

        mButtonVerify.setOnClickListener(View.OnClickListener {

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
                    StartVerifyTemplate()
                } else {
                    if (usb_host_ctx?.IsPendingOpen()==true) {
                        mPendingOperation = OPERATION_VERIFY
                    } else {
                        setInfoText(getString(R.string.infor_msg),true)
                    }
                }
            } else {
                StartVerifyTemplate()
            }
        })
    }



    private fun setInfoText(text:String,isError:Boolean)
    {
        textView_error.text=(text)
        // var icon:Int;
        if (isError)
        {
            textView_error.setTextColor(ContextCompat.getColor(this, R.color.red));
            //icon=R.drawable.error
        }
        else
        {
            textView_error.setTextColor(ContextCompat.getColor(this, R.color.black));
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
                    imageView_verify.setImageBitmap(mBitmapFP)

                }

                MESSAGE_SUCCESSFULL -> onSuccessfullyVerify()
                MESSAGE_END_OPERATION -> {EndOperation()
                     }

                UsbDeviceDataExchangeImpl.MESSAGE_ALLOW_DEVICE ->
                {
                    if (usb_host_ctx?.ValidateContext()==true) {
                        when (mPendingOperation) {
                            OPERATION_VERIFY -> StartVerifyTemplate()

                        }
                    } else {
                        setInfoText(getString(R.string.cant_open),true);
                    }

                }

                UsbDeviceDataExchangeImpl.MESSAGE_DENY_DEVICE -> {
                    setInfoText(getString(R.string.deny_device),true);

                }
            }
        }
    }


    private fun StartVerifyTemplate() {
        var tmplName = captureFilePath;
        var templateContent: ByteArray? = null
        var fs: FileInputStream? = null
        var f: File? = null

        try {
            f = File(tmplName)
            if (!f.exists() || !f.canRead())
                throw FileNotFoundException()

            val nFileSize = f.length()
            fs = FileInputStream(f)

            val fileContent = ByteArray(nFileSize.toInt())
            fs.read(fileContent)
            fs.close()

            templateContent = fileContent
        } catch (e: Exception) {
            val error = String.format("Failed to load template from file %s. Error: %s.", tmplName, e.toString())
            setInfoText(error,true);
        }

        if (templateContent != null) {
            PrepareOperation()
            mOperationThread = VerifyThread( templateContent, FTR_ANSISDK_MATCH_SCORE_MEDIUM)
            mOperationThread?.start()
        }
    }


    private fun PrepareOperation() {
        setInfoText(getString(R.string.put_finger),false);
        EnableControls(false)
    }

    private fun EnableControls(enable: Boolean) {
        mButtonVerify.isEnabled = enable
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


    private inner class VerifyThread( template: ByteArray,matchScore:Float) : OperationThread() {
        private var ansi_lib: AnsiSDKLib? = null
        private var mUseUsbHost = true
        private var mFinger = 0
        private var mTmpl: ByteArray? = null
        private var mMatchScore = 0f

        init {
            ansi_lib = AnsiSDKLib()
            if (!ansi_lib!!.SetGlobalSyncDir(mSyncDir.toString())) {
                mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib!!.GetErrorMessage()).sendToTarget()
                mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()

            }

            mTmpl = template
            mMatchScore = matchScore;

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

                    val matchResult = FloatArray(1)
                    val lT1 = SystemClock.uptimeMillis()
                    if (ansi_lib!!.VerifyTemplate(mFinger, mTmpl, img_buffer, matchResult)) {
                        val op_time = SystemClock.uptimeMillis() - lT1

                        val op_info = String.format("Verify done. Result: %s(%f). Time is %d(ms)",
                                if (matchResult[0] > mMatchScore) "OK" else "FAILED", matchResult[0], op_time ,"Please Try again.")
                        mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, op_info).sendToTarget()

                        mBitmapFP = CreateFingerBitmap(
                                ansi_lib!!.GetImageWidth(),
                                ansi_lib!!.GetImageHeight(),
                                img_buffer)
                        mHandler.obtainMessage(MESSAGE_SHOW_IMAGE).sendToTarget()

                        if (matchResult[0] > mMatchScore)
                        {
                            mHandler.obtainMessage(MESSAGE_SUCCESSFULL).sendToTarget()
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
                            val error = String.format("Verify failed. Error: %s.", ansi_lib!!.GetErrorMessage())
                            mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget()
                            break
                        }
                    }
                }
            } catch (e: Exception) {
               Toast.makeText(applicationContext,e.toString(),Toast.LENGTH_SHORT).show()
                mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, e.message).sendToTarget()
            }

            if (dev_open) {
                ansi_lib!!.CloseDevice()
            }

            mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget()
        }
    }




    private fun EndOperation() {
        EnableControls(true)
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


    fun onSuccessfullyVerify() {
        val dlgAlert = AlertDialog.Builder(this)
        dlgAlert.setMessage("Biometric Verify successfully.!")
        dlgAlert.setTitle("Biometric Verify Successfully")
        dlgAlert.setPositiveButton("OK",
                DialogInterface.OnClickListener { dialog, whichButton ->
                    sendPatAndExit(12);

                }
        )
        dlgAlert.setCancelable(false)
        dlgAlert.create().show()
    }
    private fun sendPatAndExit(requestCode:Int)
    {
        val intent = Intent()
        val bundle = Bundle()
        bundle.putBoolean("success", true)
        bundle.putInt("code", requestCode)
        bundle.putString("path", mTmplName)
        intent.putExtras(bundle)
        setResult(requestCode, intent)
        finish();
    }
}