package com.example.ftransisdkdemo_android;

import java.io.File;
import java.util.ArrayList;



import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.futronic.biometric.R;


public class SelectTemplateName extends Activity {
    // Return Intent extra
    public static String RET_SELECTED_TMPL_NAME = "selected_template_name";
    //
	private String mSelectedTmplName = null;
	private Button mButtonOK;
	private Button mButtonCancel;
	private EditText mEditDBFolder;
	private ListView mListTmpls;
	private String mDbDir;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
        setContentView(R.layout.selecttmpl);
        // Set result CANCELED in case the user backs out
        setResult(Activity.RESULT_CANCELED);
        //
        mDbDir = MainActivity.mDbDir;
        mButtonOK = (Button) findViewById(R.id.buttonSelectOK);
        mButtonCancel = (Button) findViewById(R.id.buttonSelectCancel);
        mEditDBFolder = (EditText)findViewById(R.id.editDBFolder);
        mEditDBFolder.setText(mDbDir);
        
        mButtonCancel.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
            	mSelectedTmplName = null;
            	finish();
            }
        });
        
        File DbDir;
        File[] files;
        
        // Read all records to identify
        DbDir = new File( mDbDir );
        files = DbDir.listFiles();
       
        mListTmpls = (ListView)findViewById(R.id.listTmpls);
        ArrayList<String> listItems=new ArrayList<String>();
        
        for( int iFiles = 0; iFiles < files.length; iFiles++)
        {
            File curFile = files[iFiles];
        	if( curFile.isFile() )
            {
            	listItems.add( curFile.getName() );
            }
       }
           
        mSelectedTmplName = null;
        
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, 
        	    android.R.layout.simple_list_item_checked, 
        	    listItems);  
        mListTmpls.setAdapter(adapter);
        mListTmpls.setOnItemClickListener(mTmplNameClickListener);
        
        mButtonOK.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                // Create the result Intent and include the MAC address
            	if( mSelectedTmplName != null && mSelectedTmplName.length() > 0 )
            	{
	                Intent intent = new Intent();
	                intent.putExtra(RET_SELECTED_TMPL_NAME, mSelectedTmplName);
	                // Set result and finish this Activity
	                setResult(Activity.RESULT_OK, intent);
	                finish();
            	}
            	else
                	Toast.makeText(getApplicationContext(), "Please select one template!", Toast.LENGTH_SHORT).show();
            }
        });
               
	}

    private OnItemClickListener mTmplNameClickListener = new OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
        	mSelectedTmplName = ((TextView) v).getText().toString();
        }
    };
}
