package org.torproject.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class TipsAndTricks extends Activity implements TorConstants {

	private Context context;
	
	protected void onCreate(Bundle savedInstanceState)
	{
        super.onCreate(savedInstanceState);
        context = this;

	}
	
	@Override
	protected void onStart() {
		
		super.onStart();
		setContentView(R.layout.layout_wizard_tips);
		
		stepFive();
        
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	
		
	}
	
	void stepFive(){
		
		String title = context.getString(R.string.wizard_tips_title);
		TextView txtTitle  = ((TextView)findViewById(R.id.WizardTextTitle));
		txtTitle.setText(title);
		 
        Button btn1 = (Button)findViewById(R.id.WizardRootButtonInstallGibberbot);
        
        btn1.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View view) {

				String url = context.getString(R.string.gibberbot_apk_url);
				context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));

			}
		});
        
        Button btn2 = (Button)findViewById(R.id.WizardRootButtonInstallFirefox);

        btn2.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View view) {
				
				String url = context.getString(R.string.firefox_apk_url);
				context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));

			}
		});
        
        Button btn3 = (Button)findViewById(R.id.WizardRootButtonInstallProxyMob);
        
        btn3.setOnClickListener(new OnClickListener() {
			
			@Override
			public void onClick(View view) {

				String url = context.getString(R.string.proxymob_url);
				context.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
			}
		});
        
        Button back = ((Button)findViewById(R.id.btnWizard1));
        Button next = ((Button)findViewById(R.id.btnWizard2));
        
        back.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				
				startActivityForResult(new Intent(getBaseContext(), Permissions.class), 1);
			}
		});
    	
    	next.setOnClickListener(new View.OnClickListener() {
			
			@Override
			public void onClick(View v) {
				showWizardFinal();
			}
		});
        
	}
	
	private void showWizardFinal ()
	{
		String title = null;
		String msg = null;
		
		
		title = context.getString(R.string.wizard_final);
		msg = context.getString(R.string.wizard_final_msg);
		
		DialogInterface.OnClickListener ocListener = new DialogInterface.OnClickListener() {
			
			@Override
			public void onClick(DialogInterface dialog, int which) {
				context.startActivity(new Intent(context, Orbot.class));

			}
		};
	
		
		 new AlertDialog.Builder(context)
		.setIcon(R.drawable.icon)
        .setTitle(title)
        .setPositiveButton(R.string.button_close, ocListener)
        .setMessage(msg)
        .show();
	
	
				
		
	}
}