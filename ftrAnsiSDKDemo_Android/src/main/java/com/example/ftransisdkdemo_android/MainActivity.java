package com.example.ftransisdkdemo_android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.futronic.biometric.R;
import com.futronictech.AnsiSDKLib;
import com.futronictech.UsbDeviceDataExchangeImpl;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

public class MainActivity extends AppCompatActivity {

	public static final int MESSAGE_SHOW_MSG = 1;
	public static final int MESSAGE_SHOW_IMAGE = 2;
	public static final int MESSAGE_SHOW_ERROR_MSG = 3;
	public static final int MESSAGE_END_OPERATION = 4;

	//Pending operations
	private static final int OPERATION_CAPTURE = 1;
	private static final int OPERATION_CREATE = 2;
	private static final int OPERATION_VERIFY = 3;
	private static final int OPERATION_IDENTIFY = 4;

	// Intent request codes
	private static final int REQUEST_INPUT_TMPL_NAME = 1;
	private static final int REQUEST_SELECT_TMPL_NAME = 2;

	private static final String kAnsiTemplatePostfix = "(ANSI)";
	private static final String kIsoTemplatePostfix = "(ISO)";

	private Button mButtonCapture;
	private Button mButtonCreate;
	private Button mButtonVerify;
	private Button mButtonIdentify;

	private Button mButtonStop;

	private Button mButtonExit;

	private ImageView mFingerImage;
	private static Bitmap mBitmapFP = null;

	private TextView mTxtMessage;
	private TextView mErrMessage;

	private CheckBox mSaveIso;
	private CheckBox mSaveAnsi;

	private Spinner mFinger;
	private Spinner mMatchScore;

	private CheckBox mUsbHostMode;

	private int mPendingOperation = 0;

	private OperationThread mOperationThread = null;

	private float[] mMatchScoreValue = new float[6];

	private String mNewTmplName = null;
	private String captureFilePath;

	/**
	 * A database directory name.
	 */
	public static String mDbDir;

	private UsbDeviceDataExchangeImpl usb_host_ctx = null;
	private File mSyncDir = null;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mMatchScoreValue[0] = AnsiSDKLib.FTR_ANSISDK_MATCH_SCORE_LOW;
		mMatchScoreValue[1] = AnsiSDKLib.FTR_ANSISDK_MATCH_SCORE_LOW_MEDIUM;
		mMatchScoreValue[2] = AnsiSDKLib.FTR_ANSISDK_MATCH_SCORE_MEDIUM;
		mMatchScoreValue[3] = AnsiSDKLib.FTR_ANSISDK_MATCH_SCORE_HIGH_MEDIUM;
		mMatchScoreValue[4] = AnsiSDKLib.FTR_ANSISDK_MATCH_SCORE_HIGH;
		mMatchScoreValue[5] = AnsiSDKLib.FTR_ANSISDK_MATCH_SCORE_VERY_HIGH;

		mTxtMessage = (TextView) findViewById(R.id.txtMessage);
		mErrMessage = (TextView) findViewById(R.id.textError);

		mFingerImage = (ImageView) findViewById(R.id.imageViewFinger);
		mButtonCapture = (Button) findViewById(R.id.buttonCapture);
		mButtonCreate = (Button)findViewById(R.id.buttonCreate);
		mButtonVerify = (Button)findViewById(R.id.buttonVerify);
		mButtonIdentify = (Button)findViewById(R.id.buttonIdentify);

		mButtonStop = (Button)findViewById(R.id.buttonStop);

		mButtonExit = (Button)findViewById(R.id.buttonExit);

		mSaveAnsi = (CheckBox)findViewById(R.id.checkBoxSaveTmplAnsi);
		mSaveIso = (CheckBox)findViewById(R.id.checkBoxSaveTmplISO);

		mUsbHostMode = (CheckBox)findViewById(R.id.checkBoxUsbHost);

		mFinger = (Spinner) findViewById(R.id.spinnerFinger);
		ArrayAdapter<CharSequence> adapter1 = ArrayAdapter.createFromResource(this, R.array.FingerNameArray, android.R.layout.simple_spinner_item);
		adapter1.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mFinger.setAdapter(adapter1);
		mFinger.setSelection(0);

		mMatchScore = (Spinner) findViewById(R.id.spinnerMatchScore);
		ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this, R.array.MatchScoreArray, android.R.layout.simple_spinner_item);
		adapter2.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mMatchScore.setAdapter(adapter2);
		mMatchScore.setSelection(2);

		mUsbHostMode.setChecked(true);
		mSaveAnsi.setChecked(true);
		mButtonStop.setEnabled(false);

		try {
			captureFilePath = getIntent().getExtras().getString("fingerfile");
		}catch (Exception e)
		{

		}
		// Get database folder
		try
		{
			mDbDir = GetDatabaseDir();
		}
		catch( AppException e )
		{
			Toast.makeText(getApplicationContext(), "Initialization failed. Application will be close.\nError description: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			System.exit( 0 );
		}

		usb_host_ctx = new UsbDeviceDataExchangeImpl(this, mHandler);
		mSyncDir = this.getExternalFilesDir(null);

		mButtonCapture.setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				if(mUsbHostMode.isChecked())
				{
					if(usb_host_ctx.OpenDevice(0, true))
					{
						StartCapture();
					}
					else
					{
						if(usb_host_ctx.IsPendingOpen())
						{
							mPendingOperation = OPERATION_CAPTURE;
						}
						else
						{
							mErrMessage.setText("Can not start capture operation.\nCan't open scanner device");
						}
					}
				}
				else
				{
					StartCapture();
				}
			}
		});

		mButtonCreate.setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				callForCreate();
			}
		});

		mButtonVerify.setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				if(mUsbHostMode.isChecked())
				{
					if(usb_host_ctx.OpenDevice(0, true))
					{
						StartVerify();
					}
					else
					{
						if(usb_host_ctx.IsPendingOpen())
						{
							mPendingOperation = OPERATION_VERIFY;
						}
						else
						{
							mErrMessage.setText("Can not start verify template operation.\nCan't open scanner device");
						}
					}
				}
				else
				{
					StartVerify();
				}
			}
		});

		mButtonIdentify.setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				if(mUsbHostMode.isChecked())
				{
					if(usb_host_ctx.OpenDevice(0, true))
					{
						StartIdentify();
					}
					else
					{
						if(usb_host_ctx.IsPendingOpen())
						{
							mPendingOperation = OPERATION_IDENTIFY;
						}
						else
						{
							mErrMessage.setText("Can not start identify operation.\nCan't open scanner device");
						}
					}
				}
				else
				{
					StartIdentify();
				}
			}
		});

		mButtonStop.setOnClickListener(new OnClickListener() {
			public void onClick(View v)
			{
				if(mOperationThread != null)
				{
					mOperationThread.Cancel();
				}
			}
		});

		mButtonExit.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				ExitActivity();
			}
		});
	}

	private void callForCreate() {
		if(mUsbHostMode.isChecked())
		{
			if(usb_host_ctx.OpenDevice(0, true))
			{
				StartCreate();
			}
			else
			{
				if(usb_host_ctx.IsPendingOpen())
				{
					mPendingOperation = OPERATION_CREATE;
				}
				else
				{
					mErrMessage.setText("Can not start create template operation.\nCan't open scanner device");
				}
			}
		}
		else
		{
			StartCreate();
		}
	}

	private final Handler mHandler = new Handler()
	{
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
				case MESSAGE_SHOW_MSG:
					String showMsg = (String) msg.obj;
					mTxtMessage.setText(showMsg);
					break;

				case MESSAGE_SHOW_ERROR_MSG:
					String showErr = (String) msg.obj;
					mErrMessage.setText(showErr);
					mTxtMessage.setText("");
					break;

				case MESSAGE_SHOW_IMAGE:
					mFingerImage.setImageBitmap(mBitmapFP);
					break;
				case MESSAGE_END_OPERATION:
					EndOperation();
					break;

				case UsbDeviceDataExchangeImpl.MESSAGE_ALLOW_DEVICE:
				{
					if(usb_host_ctx.ValidateContext())
					{
						switch(mPendingOperation)
						{
							case OPERATION_CAPTURE:
								StartCapture();
								break;

							case OPERATION_CREATE:
								StartCreate();
								break;

							case OPERATION_VERIFY:
								StartVerify();
								break;

							case OPERATION_IDENTIFY:
								StartIdentify();
								break;
						}
					}
					else
					{
						mErrMessage.setText("Can't open scanner device");
					}

					break;
				}

				case UsbDeviceDataExchangeImpl.MESSAGE_DENY_DEVICE:
				{
					mErrMessage.setText("User deny scanner device");
					break;
				}

			}
		}
	};

	private String GetDatabaseDir() throws AppException
	{
		String szDbDir;
		File extStorageDirectory = this.getExternalFilesDir(null);
		File Dir = new File(extStorageDirectory, "Android//FtrAnsiSdkDb");
		if( Dir.exists() )
		{
			if( !Dir.isDirectory() )
			{
				throw new AppException( "Can not create database directory " + Dir.getAbsolutePath() +
						". File with the same name already exist." );
			}
		}
		else
		{
			try
			{
				Dir.mkdirs();
			}
			catch( SecurityException e )
			{
				throw new AppException( "Can not create database directory " + Dir.getAbsolutePath() +
						". Access denied.");
			}
		}
		szDbDir = Dir.getAbsolutePath();
		return szDbDir;
	}

	private Bitmap CreateFingerBitmap(int imgWidth, int imgHeight, byte[] imgBytes)
	{
		int[] pixels = new int[imgWidth*imgHeight];
		for( int i=0; i<imgWidth*imgHeight; i++)
		{
			pixels[i] = imgBytes[i];
		}

		Bitmap emptyBmp = Bitmap.createBitmap(pixels, imgWidth, imgHeight, Config.RGB_565);

		int width, height;
		height = emptyBmp.getHeight();
		width = emptyBmp.getWidth();

		Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565);
		Canvas c = new Canvas(result);
		Paint paint = new Paint();
		ColorMatrix cm = new ColorMatrix();
		cm.setSaturation(0);
		ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
		paint.setColorFilter(f);
		c.drawBitmap(emptyBmp, 0, 0, paint);

		return result;
	}

	private void StartCapture()
	{
		PrepareOperation();
		mOperationThread = new CaptureThread(mUsbHostMode.isChecked());
		mOperationThread.start();
	}

	private void StartCreateTmplate(String tmplName)
	{
		if(!mSaveAnsi.isChecked()&&!mSaveIso.isChecked())
		{
			mErrMessage.setText("Select any save template options");
			return;
		}

		PrepareOperation();
		mOperationThread = new CreateThread(
				mUsbHostMode.isChecked(),
				mFinger.getSelectedItemPosition(),
				mSaveAnsi.isChecked(),
				mSaveIso.isChecked(),
				tmplName);
		mOperationThread.start();
	}

	private void StartVerify()
	{
		StartVerifyTemplate(captureFilePath);
	}

	private void StartCreate()
	{
		CheckNewTmplName(mDbDir + "/" + System.currentTimeMillis());
	}

	private void StartVerifyTemplate(String tmplName)
	{
		byte[] templateContent = null;
		FileInputStream fs = null;
		File f = null;

		try
		{
			f = new File( tmplName );
			if( !f.exists() || !f.canRead() )
				throw new FileNotFoundException();

			long nFileSize = f.length();
			fs = new FileInputStream( f );

			byte[] fileContent = new byte[(int)nFileSize];
			fs.read( fileContent );
			fs.close();

			templateContent = fileContent;
		}
		catch( Exception e)
		{
			String error = String.format("Failed to load template from file %s. Error: %s.", tmplName, e.toString());
			mErrMessage.setText(error);
		}

		if(templateContent!=null)
		{
			PrepareOperation();
			mOperationThread = new VerifyThread(mUsbHostMode.isChecked(),mFinger.getSelectedItemPosition(),templateContent,mMatchScoreValue[mMatchScore.getSelectedItemPosition()]);
			mOperationThread.start();
		}
	}

	void StartIdentify()
	{
		PrepareOperation();
		mOperationThread = new IdentifyThread(
				mUsbHostMode.isChecked(),
				mFinger.getSelectedItemPosition(),
				mMatchScoreValue[mMatchScore.getSelectedItemPosition()],
				mDbDir);
		mOperationThread.start();
	}

	private class OperationThread extends Thread
	{
		private boolean mCanceled = false;

		public OperationThread()
		{

		}

		public boolean IsCanceled()
		{
			return mCanceled;
		}

		public void Cancel()
		{
			mCanceled = true;

			try
			{
				this.join();	//5sec timeout
			}
			catch (InterruptedException e)
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	private class CaptureThread extends OperationThread
	{
		private AnsiSDKLib ansi_lib = null;
		private boolean mUseUsbHost = true;

		public CaptureThread(boolean useUsbHost)
		{
			ansi_lib = new AnsiSDKLib();
			if( !ansi_lib.SetGlobalSyncDir(mSyncDir.toString()) )
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
				mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
				return;
			}
			mUseUsbHost = useUsbHost;
		}

		public void run()
		{
			boolean dev_open = false;

			try
			{
				if(mUseUsbHost)
				{
					if(!ansi_lib.OpenDeviceCtx(usb_host_ctx))
					{
						mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
						mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
						return;
					}
				}
				else
				{
					if(!ansi_lib.OpenDevice(0))
					{
						mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
						mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
						return;
					}
				}

				dev_open = true;

				if(!ansi_lib.FillImageSize())
				{
					mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
					mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
					return;
				}

				byte[] img_buffer = new byte[ansi_lib.GetImageSize()];

				for(;;)
				{
					if( IsCanceled() )
					{
						break;
					}

					long lT1 = SystemClock.uptimeMillis();
					if(ansi_lib.CaptureImage(img_buffer))
					{
						long op_time = SystemClock.uptimeMillis() - lT1;

						String op_info = String.format("Capture done. Time is %d(ms)",  op_time);
						mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, op_info ).sendToTarget();

						mBitmapFP = CreateFingerBitmap(
								ansi_lib.GetImageWidth(),
								ansi_lib.GetImageHeight(),
								img_buffer);
						mHandler.obtainMessage(MESSAGE_SHOW_IMAGE).sendToTarget();


						break;
					}
					else
					{
						int lastError = ansi_lib.GetErrorCode();

						if(lastError == AnsiSDKLib.FTR_ERROR_EMPTY_FRAME ||
								lastError == AnsiSDKLib.FTR_ERROR_NO_FRAME ||
								lastError == AnsiSDKLib.FTR_ERROR_MOVABLE_FINGER )
						{
							Thread.sleep(100);
							continue;
						}
						else
						{
							String error = String.format("Capture failed. Error: %s.",  ansi_lib.GetErrorMessage());
							mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget();
							break;
						}
					}
				}
			}
			catch(Exception e)
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, e.getMessage()).sendToTarget();
			}

			if(dev_open)
			{
				ansi_lib.CloseDevice();
			}

			mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();



			//callForCreate();
		}
	}

	private class CreateThread extends OperationThread
	{
		private AnsiSDKLib ansi_lib = null;
		private boolean mUseUsbHost = true;
		private int mFinger = 0;
		private boolean mSaveAnsi = true;
		private boolean mSaveIso = false;
		private String mTmplName = "";

		public CreateThread(boolean useUsbHost, int finger,boolean saveAnsi,boolean saveIso,String tmplName)
		{
			ansi_lib = new AnsiSDKLib();
			if( !ansi_lib.SetGlobalSyncDir(mSyncDir.toString()) )
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
				mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
				return;
			}
			mUseUsbHost = useUsbHost;

			mFinger = finger;
			mSaveAnsi = saveAnsi;
			mSaveIso = saveIso;
			mTmplName = tmplName;
		}

		public void run()
		{
			boolean dev_open = false;

			try
			{
				if(mUseUsbHost)
				{
					if(!ansi_lib.OpenDeviceCtx(usb_host_ctx))
					{
						mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
						mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
						return;
					}
				}
				else
				{
					if(!ansi_lib.OpenDevice(0))
					{
						mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
						mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
						return;
					}
				}

				dev_open = true;

				if(!ansi_lib.FillImageSize())
				{
					mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
					mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
					return;
				}

				byte[] img_buffer = new byte[ansi_lib.GetImageSize()];

				for(;;)
				{
					if( IsCanceled() )
					{
						break;
					}

					int tmplSize = ansi_lib.GetMaxTemplateSize();
					byte[] template = new byte[tmplSize];
					byte[] templateIso = new byte[tmplSize];
					int[] realSize = new int[1];
					int[] realIsoSize = new int[1];

					long lT1 = SystemClock.uptimeMillis();
					if(ansi_lib.CreateTemplate(mFinger,img_buffer,template,realSize))
					{
						long op_time = SystemClock.uptimeMillis() - lT1;

						String op_info = String.format("Create done. Time is %d(ms)", op_time);
						mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, op_info).sendToTarget();

						mBitmapFP = CreateFingerBitmap(
								ansi_lib.GetImageWidth(),
								ansi_lib.GetImageHeight(),
								img_buffer);
						mHandler.obtainMessage(MESSAGE_SHOW_IMAGE).sendToTarget();

						if (mSaveAnsi) {
							SaveTemplate(mTmplName + kAnsiTemplatePostfix, template, realSize[0]);
						}

						if (mSaveIso) {
							realIsoSize[0] = tmplSize;
							if (ansi_lib.ConvertAnsiTemplateToIso(template, templateIso, realIsoSize)) {
								SaveTemplate(mTmplName + kIsoTemplatePostfix, templateIso, realIsoSize[0]);
							} else {
								String error = String.format("Conver to failed. Error: %s.", ansi_lib.GetErrorMessage());
								mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget();
							}
						}

						break;
					}
					else
					{
						int lastError = ansi_lib.GetErrorCode();

						if(lastError == AnsiSDKLib.FTR_ERROR_EMPTY_FRAME ||
								lastError == AnsiSDKLib.FTR_ERROR_NO_FRAME ||
								lastError == AnsiSDKLib.FTR_ERROR_MOVABLE_FINGER )
						{
							Thread.sleep(100);
							continue;
						}
						else
						{
							String error = String.format("Create failed. Error: %s.",  ansi_lib.GetErrorMessage());
							mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget();
							break;
						}
					}
				}
			}
			catch(Exception e)
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, e.getMessage()).sendToTarget();
			}

			if(dev_open)
			{
				ansi_lib.CloseDevice();
			}

			mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
		}

		private void SaveTemplate(String name, byte[] template, int size)
		{
			captureFilePath = name;
			FileOutputStream fs = null;
			File f = null;

			try
			{
				f = new File( name );
				fs = new FileOutputStream( f );

				byte[] writeTemplate = new byte[size];
				System.arraycopy(template, 0, writeTemplate, 0, size);
				fs.write(writeTemplate);
				fs.close();
			}
			catch(Exception e)
			{
				String error = String.format("Failed to save template to file %s. Error: %s.", name, e.toString());
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget();
			}
		}
	}

	private class VerifyThread extends OperationThread
	{
		private AnsiSDKLib ansi_lib = null;
		private boolean mUseUsbHost = true;
		private int mFinger = 0;
		private byte[] mTmpl = null;
		private float mMatchScore = 0;

		public VerifyThread(boolean useUsbHost, int finger, byte[] template, float matchScore)
		{
			ansi_lib = new AnsiSDKLib();
			if( !ansi_lib.SetGlobalSyncDir(mSyncDir.toString()) )
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
				mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
				return;
			}
			mUseUsbHost = useUsbHost;
			mFinger = finger;
			mTmpl = template;
			mMatchScore = matchScore;
		}

		public void run()
		{
			boolean dev_open = false;

			try
			{
				if(mUseUsbHost)
				{
					if(!ansi_lib.OpenDeviceCtx(usb_host_ctx))
					{
						mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
						mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
						return;
					}
				}
				else
				{
					if(!ansi_lib.OpenDevice(0))
					{
						mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
						mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
						return;
					}
				}

				dev_open = true;

				if(!ansi_lib.FillImageSize())
				{
					mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
					mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
					return;
				}

				byte[] img_buffer = new byte[ansi_lib.GetImageSize()];

				for(;;)
				{
					if( IsCanceled() )
					{
						break;
					}

					float[] matchResult = new float[1];
					long lT1 = SystemClock.uptimeMillis();
					if(ansi_lib.VerifyTemplate(mFinger,mTmpl,img_buffer,matchResult))
					{
						long op_time = SystemClock.uptimeMillis() - lT1;

						String op_info = String.format("Verify done. Result: %s(%f). Time is %d(ms)", matchResult[0]>mMatchScore ? "OK" : "FAILED", matchResult[0], op_time);
						mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, op_info ).sendToTarget();

						mBitmapFP = CreateFingerBitmap(
								ansi_lib.GetImageWidth(),
								ansi_lib.GetImageHeight(),
								img_buffer);
						mHandler.obtainMessage(MESSAGE_SHOW_IMAGE).sendToTarget();
						break;
					}
					else
					{
						int lastError = ansi_lib.GetErrorCode();

						if(lastError == AnsiSDKLib.FTR_ERROR_EMPTY_FRAME ||
								lastError == AnsiSDKLib.FTR_ERROR_NO_FRAME ||
								lastError == AnsiSDKLib.FTR_ERROR_MOVABLE_FINGER )
						{
							Thread.sleep(100);
							continue;
						}
						else
						{
							String error = String.format("Verify failed. Error: %s.",  ansi_lib.GetErrorMessage());
							mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget();
							break;
						}
					}
				}
			}
			catch(Exception e)
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, e.getMessage()).sendToTarget();
			}

			if(dev_open)
			{
				ansi_lib.CloseDevice();
			}

			mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
		}
	}

	private class IdentifyThread extends OperationThread
	{
		private AnsiSDKLib ansi_lib = null;
		private boolean mUseUsbHost = true;
		private int mFinger = 0;
		private float mMatchScore = 0;
		private String mTemplateStore = "";


		public IdentifyThread(boolean useUsbHost, int finger,float matchScore, String templateStore)
		{
			ansi_lib = new AnsiSDKLib();
			if( !ansi_lib.SetGlobalSyncDir(mSyncDir.toString()) )
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
				mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
				return;
			}
			mUseUsbHost = useUsbHost;

			mFinger = finger;
			mMatchScore = matchScore;
		}

		public void run()
		{
			boolean dev_open = false;

			try
			{
				if(mUseUsbHost)
				{
					if(!ansi_lib.OpenDeviceCtx(usb_host_ctx))
					{
						mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
						mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
						return;
					}
				}
				else
				{
					if(!ansi_lib.OpenDevice(0))
					{
						mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
						mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
						return;
					}
				}

				dev_open = true;

				if(!ansi_lib.FillImageSize())
				{
					mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, ansi_lib.GetErrorMessage()).sendToTarget();
					mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
					return;
				}

				byte[] img_buffer = new byte[ansi_lib.GetImageSize()];

				for(;;)
				{
					if( IsCanceled() )
					{
						break;
					}

					int tmplSize = ansi_lib.GetMaxTemplateSize();
					byte[] templateBase = new byte[tmplSize];
					int[] realSize = new int[1];

					long lT1 = SystemClock.uptimeMillis();
					if(ansi_lib.CreateTemplate(mFinger,img_buffer,templateBase,realSize))
					{
						long op_time = SystemClock.uptimeMillis() - lT1;

						String op_info = String.format("Create done. Time is %d(ms)",  op_time);
						mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, op_info ).sendToTarget();

						mBitmapFP = CreateFingerBitmap(
								ansi_lib.GetImageWidth(),
								ansi_lib.GetImageHeight(),
								img_buffer);
						mHandler.obtainMessage(MESSAGE_SHOW_IMAGE).sendToTarget();

						FindTemplate(templateBase);

						break;
					}
					else
					{
						int lastError = ansi_lib.GetErrorCode();

						if(lastError == AnsiSDKLib.FTR_ERROR_EMPTY_FRAME ||
								lastError == AnsiSDKLib.FTR_ERROR_NO_FRAME ||
								lastError == AnsiSDKLib.FTR_ERROR_MOVABLE_FINGER )
						{
							Thread.sleep(100);
							continue;
						}
						else
						{
							String error = String.format("Create failed. Error: %s.",  ansi_lib.GetErrorMessage());
							mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, error).sendToTarget();
							break;
						}
					}
				}
			}
			catch(Exception e)
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, e.getMessage()).sendToTarget();
			}

			if(dev_open)
			{
				ansi_lib.CloseDevice();
			}

			mHandler.obtainMessage(MESSAGE_END_OPERATION).sendToTarget();
		}

		private void FindTemplate(byte[] baseTemplate)
		{
			long lT1 = SystemClock.uptimeMillis();

			File DbDir;
			File[] files;

			// Read all records to identify
			DbDir = new File( mDbDir );
			files = DbDir.listFiles();

			float[] matchResult = new float[1];

			boolean found = false;
			for( int iFiles = 0; iFiles < files.length; iFiles++)
			{
				File curFile = files[iFiles];
				if( curFile.isFile() )
				{
					byte[] template = ReadTemplate(curFile);

					if( template != null )
					{
						if(ansi_lib.MatchTemplates(baseTemplate, template, matchResult ) &&
								matchResult[0] > mMatchScore)
						{
							long op_time = SystemClock.uptimeMillis() - lT1;
							String message = String.format("Template found.\nName: %s(%d:%d).\nTime: %d(ms)", curFile.getName(),iFiles + 1,files.length,op_time);
							mHandler.obtainMessage(MESSAGE_SHOW_MSG, -1, -1, message).sendToTarget();

							found = true;

							break;
						}
					}
				}
			}

			if(!found)
			{
				mHandler.obtainMessage(MESSAGE_SHOW_ERROR_MSG, -1, -1, "Template not found").sendToTarget();
			}
		}

		private byte[] ReadTemplate(File templateFile)
		{
			byte[] templateContent = null;
			FileInputStream fs = null;

			try
			{
				long nFileSize = templateFile.length();
				fs = new FileInputStream( templateFile );

				byte[] fileContent = new byte[(int)nFileSize];
				fs.read( fileContent );
				fs.close();

				templateContent = fileContent;
			}
			catch( Exception e )
			{

			}

			return templateContent;

		}
	}

	private void EnableControls(boolean enable)
	{
		mButtonCapture.setEnabled(enable);
		mButtonCreate.setEnabled(enable);
		mButtonVerify.setEnabled(enable);
		mButtonIdentify.setEnabled(enable);

		mUsbHostMode.setEnabled(enable);

		mSaveAnsi.setEnabled(enable);
		mSaveIso.setEnabled(enable);

		mFinger.setEnabled(enable);
		mMatchScore.setEnabled(enable);

		mButtonStop.setEnabled(!enable);
	}

	private void PrepareOperation()
	{
		mTxtMessage.setText("Put finger on scanner");
		mErrMessage.setText("");
		EnableControls(false);
	}

	private void EndOperation()
	{
		EnableControls(true);
	}

	private void ExitActivity()
	{
		if( mOperationThread != null )
		{
			mOperationThread.Cancel();
		}

		if(usb_host_ctx != null)
		{
			usb_host_ctx.CloseDevice();
			usb_host_ctx.Destroy();
			usb_host_ctx = null;
		}

		System.exit(0);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		if( mOperationThread != null )
		{
			mOperationThread.Cancel();
		}

		if(usb_host_ctx != null)
		{
			usb_host_ctx.CloseDevice();
			usb_host_ctx.Destroy();
			usb_host_ctx = null;
		}

		System.exit(0);
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch (requestCode)
		{
			case REQUEST_INPUT_TMPL_NAME:
				if (resultCode == Activity.RESULT_OK)
				{
					String szTmplName = data.getExtras().getString(AskTemplateName.RET_TMPL_NAME);
					if( szTmplName == null)
					{
						mTxtMessage.setText("You must enter the template name.");
						return;
					}

					CheckNewTmplName(mDbDir + "/" + szTmplName);
				}
				else
				{
					mTxtMessage.setText("Canceled.");
				}
				break;
			case REQUEST_SELECT_TMPL_NAME:
				if (resultCode == Activity.RESULT_OK)
				{
					String szTmplName = data.getExtras().getString(SelectTemplateName.RET_SELECTED_TMPL_NAME);
					if( szTmplName == null)
					{
						mTxtMessage.setText("Template not selected.");
						return;
					}
					StartVerifyTemplate(mDbDir + "/" + szTmplName);
				}

				break;
		}
	}

	private void CheckNewTmplName(String tmplName)
	{
		File ansiFile = new File(tmplName + kAnsiTemplatePostfix);
		File isoFile = new File(tmplName + kIsoTemplatePostfix);
		if(( ansiFile.exists() && mSaveAnsi.isChecked() ) ||
				( isoFile.exists() && mSaveIso.isChecked() )	)
		{
			mNewTmplName = tmplName;

			DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
				@Override
				public void onClick(DialogInterface dialog, int which) {
					switch (which){
						case DialogInterface.BUTTON_POSITIVE:
							StartCreateTmplate(mNewTmplName);
							break;

						case DialogInterface.BUTTON_NEGATIVE:
							//No button clicked
							break;
					}
				}
			};

			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("New template name").setMessage("Template already exists. Do you want replace it?").setPositiveButton("Yes", dialogClickListener)
					.setNegativeButton("No", dialogClickListener).show();
		}
		else
		{
			StartCreateTmplate(tmplName);
		}

	}


}
