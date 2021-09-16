package com.example.ftransisdkdemo_android;



import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

import com.futronic.biometric.R;


public class AskTemplateName extends Activity {
    // Return Intent extra
    public static String RET_TMPL_NAME = "tmpl_name";
    //
	private String mTmplName = null;
	private Button mButtonOK;
	private Button mButtonCancel;
	private EditText mEditTmplName;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    
        setContentView(R.layout.asktmplname);
        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);

        mButtonOK = (Button) findViewById(R.id.buttonOK);
        mButtonCancel = (Button) findViewById(R.id.buttonCancel);
        mEditTmplName = (EditText)findViewById(R.id.editTmplName);
        mTmplName = null;
        mButtonOK.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Create the result Intent and include the MAC address
            	mTmplName = mEditTmplName.getText().toString();
                Intent intent = new Intent();
                intent.putExtra(RET_TMPL_NAME, mTmplName);
                // Set result and finish this Activity
                setResult(Activity.RESULT_OK, intent);
                finish();
            }
        });
        
        mButtonCancel.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	mTmplName = null;
            	finish();
            }
        });
	}
}
