/* Copyright (c) 2009-2011, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */
/* See LICENSE for licensing information */
/*
 * Code for iptables binary management taken from DroidWall GPLv3
 * Copyright (C) 2009-2010  Rodrigo Zechin Rosauro
 */

package org.torproject.android.service;


import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

import net.freehaven.tor.control.ConfigEntry;
import net.freehaven.tor.control.EventHandler;
import net.freehaven.tor.control.TorControlConnection;

import org.torproject.android.Orbot;
import org.torproject.android.R;
import org.torproject.android.TorConstants;
import org.torproject.android.Utils;
import org.torproject.android.settings.AppManager;

import android.app.Application;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcelable;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.preference.PreferenceManager;
import android.util.Log;

public class TorService extends Service implements TorServiceConstants, TorConstants, Runnable, EventHandler
{
	
	public static boolean ENABLE_DEBUG_LOG = true;
	
	private static int currentStatus = STATUS_OFF;
		
	private TorControlConnection conn = null;
	private Socket torConnSocket = null;
	
	private static TorService _torInstance;
	
	private static final int NOTIFY_ID = 1;
	private static final int TRANSPROXY_NOTIFY_ID = 2;
	private static final int ERROR_NOTIFY_ID = 3;
	private static final int HS_NOTIFY_ID = 3;
	
	private boolean prefPersistNotifications = true;
	
	private static final int MAX_START_TRIES = 3;

    private ArrayList<String> configBuffer = null;
    private ArrayList<String> resetBuffer = null;
     
   
   //   private String appHome;
    private File appBinHome;
    private File appCacheHome;
    
    private File fileTor;
    private File filePrivoxy;
    private File fileObfsProxy;
    
    
    public static void logMessage(String msg)
    {
    	if (ENABLE_DEBUG_LOG)
    		Log.d(TAG,msg);
    }
    
    public static void logException(String msg, Exception e)
    {
    	if (ENABLE_DEBUG_LOG)
    		Log.e(TAG,msg,e);
    }
    
    
    private boolean findExistingProc ()
    {
    	 int procId = TorServiceUtils.findProcessId(fileTor.getAbsolutePath());

 		if (procId != -1)
 		{
 			logNotice("Found existing Tor process");
 			
            sendCallbackLogMessage (getString(R.string.found_existing_tor_process));

 			try {
 				currentStatus = STATUS_CONNECTING;
				
 				initControlConnection();
				
				currentStatus = STATUS_ON;
				
				return true;
 						
			} catch (RuntimeException e) {
				Log.d(TAG,"Unable to connect to existing Tor instance,",e);
				currentStatus = STATUS_OFF;
				
			} catch (Exception e) {
				Log.d(TAG,"Unable to connect to existing Tor instance,",e);
				currentStatus = STATUS_OFF;
				
				
			}
 		}
 		
 		return false;
    	 
    }
    

    /* (non-Javadoc)
	 * @see android.app.Service#onLowMemory()
	 */
	public void onLowMemory() {
		super.onLowMemory();
		
		logNotice( "Low Memory Warning!");
		
	}


	/* (non-Javadoc)
	 * @see android.app.Service#onUnbind(android.content.Intent)
	 */
	public boolean onUnbind(Intent intent) {
		
	//	logNotice( "onUnbind Called: " + intent.getAction());
		
		
		
		return super.onUnbind(intent);
		
		
	}

	public int getTorStatus ()
    {
    	
    	return currentStatus;
    	
    }
	
	private void clearNotifications ()
	{
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		mNotificationManager.cancelAll();
		
		
	}
   
	private void showToolbarNotification (String notifyMsg, int notifyId, int icon, int flags)
	{
	
		
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

		
		CharSequence tickerText = notifyMsg;
		long when = System.currentTimeMillis();

		Notification notification = new Notification(icon, tickerText, when);
		
		if (prefPersistNotifications && flags != -1)			
			notification.flags |= flags;

		Context context = getApplicationContext();
		CharSequence contentTitle = getString(R.string.app_name);
		CharSequence contentText = notifyMsg;
		
		Intent notificationIntent = new Intent(this, Orbot.class);
		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);


		mNotificationManager.notify(notifyId, notification);


	}
    
    /* (non-Javadoc)
	 * @see android.app.Service#onRebind(android.content.Intent)
	 */
	public void onRebind(Intent intent) {
		super.onRebind(intent);
		
		initTorPaths();
		
		
	}


	/* (non-Javadoc)
	 * @see android.app.Service#onStart(android.content.Intent, int)
	 */
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);

		_torInstance = this;
		
		initTorPaths();
		
		new Thread ()
		{
			
			public void run ()
			{
				try {
					checkTorBinaries (false);
				} catch (Exception e) {
	
					logNotice("unable to find tor binaries: " + e.getMessage());
			    	showToolbarNotification(getString(R.string.error_installing_binares), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr, -1);
	
					Log.e(TAG, "error checking tor binaries", e);
				}
			}
		}.start();
		
		

		if (intent != null && intent.getAction()!=null && intent.getAction().equals("onboot"))
		{
			
			SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
			
			boolean startOnBoot = prefs.getBoolean("pref_start_boot",false);
			
			if (startOnBoot)
			{
				setTorProfile(PROFILE_ON);
			}
		}
	}
	 
	public void run ()
	{
		
		if (currentStatus == STATUS_CONNECTING)
		{
			boolean isRunning = _torInstance.findExistingProc ();
			
			if (!isRunning)
			{
		     try
		     {
			   initTor();
			   isRunning = true;
			   IntentFilter mNetworkStateFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
			   registerReceiver(mNetworkStateReceiver , mNetworkStateFilter);
		     }
		     catch (Exception e)
		     {
		    	 currentStatus = STATUS_OFF;
		    	 this.showToolbarNotification(getString(R.string.status_disabled), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr, -1);
		    	 Log.d(TAG,"Unable to start Tor: " + e.getMessage(),e);
		     }
			}
		}
		else if (currentStatus == STATUS_OFF)
		{

			_torInstance.stopTor();

		}
	}

	
    public void onDestroy ()
    {
    	super.onDestroy();
    	
    	Log.d(TAG,"onDestroy called");
    	
    	  // Unregister all callbacks.
        mCallbacks.kill();
      
    }
    
    private void stopTor ()
    {
    	currentStatus = STATUS_OFF;
    	
    	try
    	{	
    		killTorProcess ();
    		
    		//stop the foreground priority and make sure to remove the persistant notification
    		stopForeground(true);
    			
    		currentStatus = STATUS_OFF;
    
    		clearNotifications();
    		
    		sendCallbackStatusMessage(getString(R.string.status_disabled));

    		disableTransparentProxy();
    	}
    	catch (Exception e)
    	{
    		Log.d(TAG, "An error occured stopping Tor",e);
    		logNotice("An error occured stopping Tor: " + e.getMessage());
    		sendCallbackStatusMessage(getString(R.string.something_bad_happened));

    	}
    }
    
 
   
    /*
    public void reloadConfig ()
    {
    	try
		{
	    	if (conn == null)
			{
				initControlConnection ();
			}
		
			if (conn != null)
			{
				 conn.signal("RELOAD");
			}
		}
    	catch (Exception e)
    	{
    		Log.d(TAG,"Unable to reload configuration",e);
    	}
    }*/
    
    
    
	private void getHiddenServiceHostname ()
	{

    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
        boolean enableHiddenServices = prefs.getBoolean("pref_hs_enable", false);

        if (enableHiddenServices)
        {
	    	File file = new File(appCacheHome, "hostname");
	    	
	    	if (file.exists())
	    	{
		    	try {
					String onionHostname = Utils.readString(new FileInputStream(file));
					showToolbarNotification(getString(R.string.hidden_service_on) + ' ' + onionHostname, HS_NOTIFY_ID, R.drawable.ic_stat_notify, Notification.FLAG_ONGOING_EVENT);
					Editor pEdit = prefs.edit();
					pEdit.putString("pref_hs_hostname",onionHostname);
					pEdit.commit();
				
					
				} catch (FileNotFoundException e) {
					logException("unable to read onion hostname file",e);
					showToolbarNotification(getString(R.string.unable_to_read_hidden_service_name), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr, -1);
					return;
				}
	    	}
	    	else
	    	{
				showToolbarNotification(getString(R.string.unable_to_read_hidden_service_name), HS_NOTIFY_ID, R.drawable.ic_stat_notifyerr, -1);
	
	    		
	    	}
        }
        
        return;
	}
	
    
    private void killTorProcess () throws Exception
    {
		//android.os.Debug.waitForDebugger();
    	
    	StringBuilder log = new StringBuilder();
    	int procId = -1;
    	
    	if (conn != null)
		{
    		logNotice("Using control port to shutdown Tor");
    		
    		
			try {
				logNotice("sending SHUTDOWN signal to Tor process");
				conn.shutdownTor("SHUTDOWN");
				
				
			} catch (Exception e) {
				Log.d(TAG,"error shutting down Tor via connection",e);
			}
			
			conn = null;
		}
    	
    	int killDelayMs = 300;
    	
		while ((procId = TorServiceUtils.findProcessId(fileTor.getAbsolutePath())) != -1)
		{
			
			logNotice("Found Tor PID=" + procId + " - killing now...");
			
			String[] cmd = { SHELL_CMD_KILL + ' ' + procId + "" };
			TorServiceUtils.doShellCommand(cmd,log, false, false);
			try { Thread.sleep(killDelayMs); }
			catch (Exception e){}
		}

		while ((procId = TorServiceUtils.findProcessId(filePrivoxy.getAbsolutePath())) != -1)
		{
			
			logNotice("Found Privoxy PID=" + procId + " - killing now...");
			String[] cmd = { SHELL_CMD_KILL + ' ' + procId + "" };

			TorServiceUtils.doShellCommand(cmd,log, false, false);
			try { Thread.sleep(killDelayMs); }
			catch (Exception e){}
		}
		
		while ((procId = TorServiceUtils.findProcessId(fileObfsProxy.getAbsolutePath())) != -1)
		{
			
			logNotice("Found ObfsProxy PID=" + procId + " - killing now...");
			String[] cmd = { SHELL_CMD_KILL + ' ' + procId + "" };

			TorServiceUtils.doShellCommand(cmd,log, false, false);
			try { Thread.sleep(killDelayMs); }
			catch (Exception e){}
		}
		
    }
   
    private void logNotice (String msg)
    {
    	if (msg != null && msg.trim().length() > 0)
    	{
    		if (ENABLE_DEBUG_LOG)        	
        		Log.d(TAG, msg);
    	
    		sendCallbackLogMessage(msg);
    	}
    }
    
    private void initTorPaths ()
    {
    	
    	appBinHome = getDir("bin",Application.MODE_PRIVATE);
    	appCacheHome = getDir("data",Application.MODE_PRIVATE);
    	
    	fileTor = new File(appBinHome, TOR_BINARY_ASSET_KEY);
		filePrivoxy = new File(appBinHome, PRIVOXY_ASSET_KEY);
		fileObfsProxy = new File(appBinHome, OBFSPROXY_ASSET_KEY);

		
    }

    public boolean checkTorBinaries (boolean forceInstall) throws Exception
    {
    	//check and install iptables
    	TorBinaryInstaller.assertIpTablesBinaries(this, true);

    	initTorPaths();
		
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	String currTorBinary = prefs.getString(TorServiceConstants.PREF_BINARY_TOR_VERSION_INSTALLED, null);
    	String currPrivoxyBinary = prefs.getString(TorServiceConstants.PREF_BINARY_PRIVOXY_VERSION_INSTALLED, null);
    	
    	StringBuilder cmdLog = new StringBuilder();
    	int exitCode = -1;
    	
    	if (currTorBinary == null || (!currTorBinary.equals(TorServiceConstants.BINARY_TOR_VERSION)))
    		if (fileTor.exists())
    		{
    			if (currentStatus != STATUS_OFF)
    				stopTor();
    			
    			String[] cmds = {"rm " + fileTor.getAbsolutePath()};
    			exitCode = TorServiceUtils.doShellCommand(cmds, cmdLog, false, true);

    		}
    
    	if (currPrivoxyBinary == null || (!currPrivoxyBinary.equals(TorServiceConstants.BINARY_PRIVOXY_VERSION)))
    		if (filePrivoxy.exists())
    		{
    			if (currentStatus != STATUS_OFF)
    				stopTor();
    			
    			
    			String[] cmds = {"rm " + filePrivoxy.getAbsolutePath()};
    			exitCode = TorServiceUtils.doShellCommand(cmds, cmdLog, false, true);

    		}
    	
    	

		logNotice( "checking Tor binaries");
		
		if ((!(fileTor.exists() && filePrivoxy.exists())) || forceInstall)
		{
			if (currentStatus != STATUS_OFF)
				stopTor();
			
			TorBinaryInstaller installer = new TorBinaryInstaller(this, appBinHome); 
			boolean success = installer.installFromRaw();
			
    		if (success)
    		{
    			
    			Editor edit = prefs.edit();
    			edit.putString(TorServiceConstants.PREF_BINARY_TOR_VERSION_INSTALLED, TorServiceConstants.BINARY_TOR_VERSION);
    			edit.putString(TorServiceConstants.PREF_BINARY_PRIVOXY_VERSION_INSTALLED, TorServiceConstants.BINARY_PRIVOXY_VERSION);
    			edit.commit();
    			
    			logNotice(getString(R.string.status_install_success));
    	
    			//showToolbarNotification(getString(R.string.status_install_success), NOTIFY_ID, R.drawable.ic_stat_notify);

    		}
    		else
    		{
    		
    			logNotice(getString(R.string.status_install_fail));

    			sendCallbackStatusMessage(getString(R.string.status_install_fail));
    			
    			return false;
    		}
    		
		}
	
		StringBuilder log = new StringBuilder ();
		
		logNotice("(re)Setting permission on Tor binary");
		String[] cmd1 = {SHELL_CMD_CHMOD + ' ' + CHMOD_EXE_VALUE + ' ' + fileTor.getAbsolutePath()};
		TorServiceUtils.doShellCommand(cmd1, log, false, true);
		
		logNotice("(re)Setting permission on Privoxy binary");
		String[] cmd2 = {SHELL_CMD_CHMOD + ' ' + CHMOD_EXE_VALUE + ' ' + filePrivoxy.getAbsolutePath()};
		TorServiceUtils.doShellCommand(cmd2, log, false, true);
		
		logNotice("(re)Setting permission on Obfsproxy binary");
		String[] cmd3 = {SHELL_CMD_CHMOD + ' ' + CHMOD_EXE_VALUE + ' ' + fileObfsProxy.getAbsolutePath()};
		TorServiceUtils.doShellCommand(cmd3, log, false, true);
		
		return true;
    }
    
    public void initTor () throws Exception
    {
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	ENABLE_DEBUG_LOG = prefs.getBoolean("pref_enable_logging",false);
    	Log.i(TAG,"debug logging:" + ENABLE_DEBUG_LOG);
    	
		currentStatus = STATUS_CONNECTING;

		logNotice(getString(R.string.status_starting_up));
		
		sendCallbackStatusMessage(getString(R.string.status_starting_up));
		
		killTorProcess ();
		
		try {

    		enableTransparentProxy();
    		runTorShellCmd();
    		runPrivoxyShellCmd();

		} catch (Exception e) {
	    	logException("Unable to start Tor: " + e.getMessage(),e);	
	    	sendCallbackStatusMessage(getString(R.string.unable_to_start_tor) + ' ' + e.getMessage());
	    	
	    } 
    		
    }
    
    /*
     * activate means whether to apply the users preferences
     * or clear them out
     * 
     * the idea is that if Tor is off then transproxy is off
     */
    private boolean enableTransparentProxy () throws Exception
 	{
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	
 		boolean hasRoot = prefs.getBoolean(PREF_HAS_ROOT,false);
 		boolean enableTransparentProxy = prefs.getBoolean("pref_transparent", false);
 		
 		TorTransProxy ttProxy = new TorTransProxy();
 		
 		if (hasRoot && enableTransparentProxy)
    	{
	 		
	 		boolean transProxyAll = prefs.getBoolean("pref_transparent_all", false);
	 		boolean transProxyPortFallback = prefs.getBoolean("pref_transparent_port_fallback", false);
	 		boolean transProxyTethering = prefs.getBoolean("pref_transparent_tethering", false);
	 		
	     	TorService.logMessage ("Transparent Proxying: " + enableTransparentProxy);
	     	
	     	String portProxyList = prefs.getString("pref_port_list", "");
	
	 		
 			//TODO: Find a nice place for the next (commented) line
			//TorTransProxy.setDNSProxying(); 
			
			int code = 0; // Default state is "okay"
				
			if(transProxyPortFallback)
			{
				
				showToolbarNotification(getString(R.string.setting_up_port_based_transparent_proxying_), TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify, -1);
				StringTokenizer st = new StringTokenizer(portProxyList, ",");
				int status = code;
				while (st.hasMoreTokens())
				{
					status = ttProxy.setTransparentProxyingByPort(this, Integer.parseInt(st.nextToken()));
					if(status != 0)
						code = status;
				}
			}
			else
			{
				if(transProxyAll)
				{
					showToolbarNotification(getString(R.string.setting_up_full_transparent_proxying_), TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify, -1);

					code = ttProxy.setTransparentProxyingAll(this);
				}
				else
				{
					showToolbarNotification(getString(R.string.setting_up_app_based_transparent_proxying_), TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify, -1);

					code = ttProxy.setTransparentProxyingByApp(this,AppManager.getApps(this));
				}
				
			}
		
			TorService.logMessage ("TorTransProxy resp code: " + code);
			
			if (code == 0)
			{
				showToolbarNotification(getString(R.string.transparent_proxying_enabled), TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify, -1);

				if (transProxyTethering)
				{
					showToolbarNotification(getString(R.string.transproxy_enabled_for_tethering_), TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify, -1);

					ttProxy.enableTetheringRules(this);
					  
				}
			}
			else
			{
				showToolbarNotification(getString(R.string.warning_error_starting_transparent_proxying_), TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify, -1);

			}
		
			return true;
    	}
 		else
 			return false;
 	}
    
    /*
     * activate means whether to apply the users preferences
     * or clear them out
     * 
     * the idea is that if Tor is off then transproxy is off
     */
    private boolean disableTransparentProxy () throws Exception
 	{
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	
 		boolean hasRoot = prefs.getBoolean(PREF_HAS_ROOT,false);
 		boolean enableTransparentProxy = prefs.getBoolean("pref_transparent", false);
 		
 		
 		if (hasRoot && enableTransparentProxy)
    	{
	 		
	     	TorService.logMessage ("Clearing TransProxy rules");
	     	
	     	new TorTransProxy().flushIptables(this);
	     	
			showToolbarNotification(getString(R.string.transproxy_rules_cleared), TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify, -1);

	     	
	     	return true;
    	}
 		else
 			return false;
 	}
    
    private void runTorShellCmd() throws Exception
    {
    	
    	StringBuilder log = new StringBuilder();
		
		String torrcPath = new File(appBinHome, TORRC_ASSET_KEY).getAbsolutePath();
		
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		boolean transProxyTethering = prefs.getBoolean("pref_transparent_tethering", false);
 		
		if (transProxyTethering)
		{
			torrcPath = new File(appBinHome, TORRC_TETHER_KEY).getAbsolutePath();
		}
		
		String[] torCmd = {
				"export HOME=" + appBinHome.getAbsolutePath(),
				fileTor.getAbsolutePath() + " DataDirectory " + appCacheHome.getAbsolutePath() + " -f " + torrcPath  + " || exit\n"
				};
		
		boolean runAsRootFalse = false;
		boolean waitForProcess = false;
		
		int procId = -1;
		int attempts = 0;

		int torRetryWaitTimeMS = 2000;
		
		while (procId == -1 && attempts < MAX_START_TRIES)
		{
			log = new StringBuilder();
			
			sendCallbackStatusMessage(getString(R.string.status_starting_up));
			
			TorServiceUtils.doShellCommand(torCmd, log, runAsRootFalse, waitForProcess);
		
			Thread.sleep(torRetryWaitTimeMS);
			
			procId = TorServiceUtils.findProcessId(fileTor.getAbsolutePath());
			
			if (procId == -1)
			{
				Thread.sleep(torRetryWaitTimeMS);
				procId = TorServiceUtils.findProcessId(fileTor.getAbsolutePath());
				attempts++;
			}
			else
			{
				logNotice("got tor proc id: " + procId);
				
			}
		}
		
		if (procId == -1)
		{

			logNotice(log.toString());
			sendCallbackStatusMessage(getString(R.string.couldn_t_start_tor_process_));
			
			throw new Exception ("Unable to start Tor");
		}
		else
		{
		
			logNotice("Tor process id=" + procId);
			
			//showToolbarNotification(getString(R.string.status_starting_up), NOTIFY_ID, R.drawable.ic_stat_notify);
			
			initControlConnection ();

			updateTorConfiguration();
	    }
    }
    
    private void runPrivoxyShellCmd () throws Exception
    {
    	
    	logNotice( "Starting privoxy process");
    	
			int privoxyProcId = TorServiceUtils.findProcessId(filePrivoxy.getAbsolutePath());

			StringBuilder log = null;
			
			int attempts = 0;
			
    		if (privoxyProcId == -1)
    		{
    			log = new StringBuilder();
    			
    			String privoxyConfigPath = new File(appBinHome, PRIVOXYCONFIG_ASSET_KEY).getAbsolutePath();
    			
    			String[] cmds = 
    			{ filePrivoxy.getAbsolutePath() + " " + privoxyConfigPath + " &" };
    			
    			logNotice (cmds[0]); 
    			
    			boolean runAsRoot = false;
    			boolean waitFor = false;
    			
    			TorServiceUtils.doShellCommand(cmds, log, runAsRoot, waitFor);
    			
    			//wait one second to make sure it has started up
    			Thread.sleep(1000);
    			
    			while ((privoxyProcId = TorServiceUtils.findProcessId(filePrivoxy.getAbsolutePath())) == -1  && attempts < MAX_START_TRIES)
    			{
    				logNotice("Couldn't find Privoxy process... retrying...\n" + log);
    				Thread.sleep(3000);
    				attempts++;
    			}
    			
    			
    			logNotice(log.toString());
    		}
    		
			sendCallbackLogMessage(getString(R.string.privoxy_is_running_on_port_) + PORT_HTTP);
			
    		logNotice("Privoxy process id=" + privoxyProcId);
			
    		
    		
    }
    
    /*
	public String generateHashPassword ()
	{
		
		PasswordDigest d = PasswordDigest.generateDigest();
	      byte[] s = d.getSecret(); // pass this to authenticate
	      String h = d.getHashedPassword(); // pass this to the Tor on startup.

		return null;
	}*/
	
	private void initControlConnection () throws Exception, RuntimeException
	{
			while (true)
			{
				try
				{
					logNotice( "Connecting to control port: " + TOR_CONTROL_PORT);
					
					
					torConnSocket = new Socket(IP_LOCALHOST, TOR_CONTROL_PORT);
			        conn = TorControlConnection.getConnection(torConnSocket);
			        
			      //  conn.authenticate(new byte[0]); // See section 3.2

					logNotice( "SUCCESS connected to control port");
			        
			        File fileCookie = new File(appCacheHome, TOR_CONTROL_COOKIE);
			        
			        if (fileCookie.exists())
			        {
				        byte[] cookie = new byte[(int)fileCookie.length()];
				        new FileInputStream(fileCookie).read(cookie);
				        conn.authenticate(cookie);
				        		
				        logNotice( "SUCCESS authenticated to control port");
				        
						sendCallbackStatusMessage(getString(R.string.tor_process_starting) + ' ' + getString(R.string.tor_process_complete));
	
				        addEventHandler();
				        
			        }
			        
			        break; //don't need to retry
				}
				catch (Exception ce)
				{
					conn = null;
					Log.d(TAG,"Attempt: Error connecting to control port: " + ce.getLocalizedMessage(),ce);
					
					sendCallbackStatusMessage(getString(R.string.tor_process_waiting));

					Thread.sleep(1000);
										
				}	
			}
		
		

	}
	
	
	/*
	private void getTorStatus () throws IOException
	{
		try
		{
			 
			if (conn != null)
			{
				 // get a single value.
			      
			       // get several values
			       
			       if (currentStatus == STATUS_CONNECTING)
			       {
				       //Map vals = conn.getInfo(Arrays.asList(new String[]{
				         // "status/bootstrap-phase", "status","version"}));
			
				       String bsPhase = conn.getInfo("status/bootstrap-phase");
				       Log.d(TAG, "bootstrap-phase: " + bsPhase);
				       
				       
			       }
			       else
			       {
			    	 //  String status = conn.getInfo("status/circuit-established");
			    	 //  Log.d(TAG, "status/circuit-established=" + status);
			       }
			}
		}
		catch (Exception e)
		{
			Log.d(TAG, "Unable to get Tor status from control port");
			currentStatus = STATUS_UNAVAILABLE;
		}
		
	}*/
	
	
	public void addEventHandler () throws IOException
	{
	       // We extend NullEventHandler so that we don't need to provide empty
	       // implementations for all the events we don't care about.
	       // ...
		logNotice( "adding control port event handler");

		conn.setEventHandler(this);
	    
		conn.setEvents(Arrays.asList(new String[]{
	          "ORCONN", "CIRC", "NOTICE", "WARN", "ERR","BW"}));
	      // conn.setEvents(Arrays.asList(new String[]{
	        //  "DEBUG", "INFO", "NOTICE", "WARN", "ERR"}));

		logNotice( "SUCCESS added control port event handler");
	    
	    

	}
	
		/**
		 * Returns the port number that the HTTP proxy is running on
		 */
		public int getHTTPPort() throws RemoteException {
			return TorServiceConstants.PORT_HTTP;
		}

		/**
		 * Returns the port number that the SOCKS proxy is running on
		 */
		public int getSOCKSPort() throws RemoteException {
			return TorServiceConstants.PORT_SOCKS;
		}


		
		
		public int getProfile() throws RemoteException {
			//return mProfile;
			return PROFILE_ON;
		}
		
		public void setTorProfile(int profile)  {
			logNotice("Tor profile set to " + profile);
			
			if (profile == PROFILE_ON)
			{
 				currentStatus = STATUS_CONNECTING;
	            sendCallbackStatusMessage (getString(R.string.status_starting_up));

	            Thread thread = new Thread(this);
	            thread.start();
	           
			}
			else if (profile == PROFILE_OFF)
			{
				currentStatus = STATUS_OFF;
	            sendCallbackStatusMessage (getString(R.string.status_shutting_down));
	          
	            Thread thread = new Thread(this);
	            thread.start();
	            
				
			}
		}


		private void startNotification ()
		{
			
			Notification notice = new Notification(R.drawable.ic_stat_notify, getString(R.string.status_activated), System.currentTimeMillis());
			
			//This constructor is deprecated. Use Notification.Builder instead
			//Notification notice = new Notification(R.drawable.iocipher, "Active: " + mIpAddress, System.currentTimeMillis());

			Intent intent = new Intent(TorService.this, Orbot.class);

			PendingIntent pendIntent = PendingIntent.getActivity(TorService.this, 0, intent, 0);

			//This method is deprecated. Use Notification.Builder instead.
			notice.setLatestEventInfo(TorService.this,getString(R.string.app_name), getString(R.string.status_activated), pendIntent);

			notice.flags |= Notification.FLAG_NO_CLEAR;
			notice.flags |= Notification.FLAG_ONGOING_EVENT;
			
			startForeground(NOTIFY_ID,notice);
		
			
		}
		

	public void message(String severity, String msg) {
		
		
		logNotice(  "[Tor Control Port] " + severity + ": " + msg);
          
          if (msg.indexOf(TOR_CONTROL_PORT_MSG_BOOTSTRAP_DONE)!=-1)
          {
        	  currentStatus = STATUS_ON;

  			if (prefPersistNotifications)
  			{
        	  startNotification();
  			}
  			else
  			{
  				showToolbarNotification (getString(R.string.status_activated),NOTIFY_ID,R.drawable.ic_stat_notify, Notification.FLAG_ONGOING_EVENT);

  			}
  			
   		   	getHiddenServiceHostname ();
   		   
          }
        
      	
    		 
          sendCallbackStatusMessage (msg);
          
	}

	//showToolbarNotification(msg, TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify);
	
	public void newDescriptors(List<String> orList) {
		
	}


	public void orConnStatus(String status, String orName) {
		
		if (ENABLE_DEBUG_LOG)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("orConnStatus (");
			sb.append((orName) );
			sb.append("): ");
			sb.append(status);
			
			logNotice(sb.toString());
		}
	}


	public void streamStatus(String status, String streamID, String target) {
		
		if (ENABLE_DEBUG_LOG)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("StreamStatus (");
			sb.append((streamID));
			sb.append("): ");
			sb.append(status);
			
			logNotice(sb.toString());
		}
	}


	public void unrecognized(String type, String msg) {
		
		if (ENABLE_DEBUG_LOG)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("Message (");
			sb.append(type);
			sb.append("): ");
			sb.append(msg);
			
			logNotice(sb.toString());
		}
		
	}
	
	public void bandwidthUsed(long read, long written) {
		
		if (ENABLE_DEBUG_LOG)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("Bandwidth used: ");
			sb.append(read/1000);
			sb.append("kb read / ");
			sb.append(written/1000);
			sb.append("kb written");
	   		
			logNotice(sb.toString());
		}
		
		sendCallbackStatusMessage(written, read); 
			
		

	}
	
   	
	public void circuitStatus(String status, String circID, String path) {
		
		if (ENABLE_DEBUG_LOG)
		{
			StringBuilder sb = new StringBuilder();
			sb.append("Circuit (");
			sb.append((circID));
			sb.append("): ");
			sb.append(status);
			sb.append("; ");
			sb.append(path);
			
			logNotice(sb.toString());
		}
		
	}
	
    public IBinder onBind(Intent intent) {
        
    	
    	_torInstance = this;
    	initTorPaths();
    	
    	//android.os.Debug.waitForDebugger();
    	
    	//if Tor was deleted for some reason, do this again!
		if (!fileTor.exists())
		{
			new Thread ()
			{				
				public void run ()
				{
					try {
						checkTorBinaries (false);
					} catch (Exception e) {
		
						logNotice("unable to find tor binaries: " + e.getMessage());
				    	showToolbarNotification(getString(R.string.error_installing_binares), ERROR_NOTIFY_ID, R.drawable.ic_stat_notifyerr, -1);
		
						Log.e(TAG, "error checking tor binaries", e);
					}
				}
			}.start();
		}
    	
    	
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
    	
    	ENABLE_DEBUG_LOG = prefs.getBoolean("pref_enable_logging",false);
    	Log.i(TAG,"debug logging:" + ENABLE_DEBUG_LOG);
    	
    	prefPersistNotifications = prefs.getBoolean(TorConstants.PREF_PERSIST_NOTIFICATIONS, true);
    	
    	
    	new Thread ()
    	{
    		
    		public void run ()
    		{
    			findExistingProc ();
    		}
    	}.start();
    	
    	if (ITorService.class.getName().equals(intent.getAction())) {
            return mBinder;
        }
    	else
    		return null;
    }
	
    /**
     * This is a list of callbacks that have been registered with the
     * service.  Note that this is package scoped (instead of private) so
     * that it can be accessed more efficiently from inner classes.
     */
    final RemoteCallbackList<ITorServiceCallback> mCallbacks
            = new RemoteCallbackList<ITorServiceCallback>();


    /**
     * The IRemoteInterface is defined through IDL
     */
    private final ITorService.Stub mBinder = new ITorService.Stub() {
    	
        
		public void registerCallback(ITorServiceCallback cb) {
            if (cb != null) mCallbacks.register(cb);
        }
        public void unregisterCallback(ITorServiceCallback cb) {
            if (cb != null) mCallbacks.unregister(cb);
        }
        public int getStatus () {
        	return getTorStatus();
        }
        
        public void setProfile (int profile)
        {
        	setTorProfile(profile);
        	
        }
        
        public void processSettings ()
        {
        	
        	
        	try {
        		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(TorService.this);
            	
            	ENABLE_DEBUG_LOG = prefs.getBoolean("pref_enable_logging",false);
            	Log.i(TAG,"debug logging:" + ENABLE_DEBUG_LOG);
            	
            	prefPersistNotifications = prefs.getBoolean(TorConstants.PREF_PERSIST_NOTIFICATIONS, true);
            	
        		updateTorConfiguration();

		        if (currentStatus == STATUS_ON)
		        {
		        	//reset iptables rules in active mode
		        
					try
					{
						disableTransparentProxy();
			    		enableTransparentProxy();
					}
					catch (Exception e)
					{
						logException("unable to setup transproxy",e);
					}
		        }
		        
				
			} catch (RemoteException e) {
				logException ("error applying prefs",e);
			}
        	
        }
 

    	public String getInfo (String key) {
    		try
    		{
    			if(conn !=null)
    			{
    				String m = conn.getInfo(key);
					return m;
					
    			}
    		}
    		catch(IOException ioe)
    		{
    			Log.e(TAG,"Unable to get Tor information",ioe);
    			logNotice("Unable to get Tor information"+ioe.getMessage());
    		}
			return null;
        }
        
        public String getConfiguration (String name)
        {
        	try
        	{
	        	if (conn != null)
	        	{
	        		StringBuffer result = new StringBuffer();
	        		
	        		List<ConfigEntry> listCe = conn.getConf(name);
	        		
	        		Iterator<ConfigEntry> itCe = listCe.iterator();
	        		ConfigEntry ce = null;
	        		
	        		while (itCe.hasNext())
	        		{
	        			ce = itCe.next();
	        			
	        			result.append(ce.key);
	        			result.append(' ');
	        			result.append(ce.value);
	        			result.append('\n');
	        		}
	        		
	   	       		return result.toString();
	        	}
        	}
        	catch (IOException ioe)
        	{
        		Log.e(TAG, "Unable to update Tor configuration", ioe);
        		logNotice("Unable to update Tor configuration: " + ioe.getMessage());
        	}
        	
        	return null;
        }
        
        /**
         * Set configuration
         **/
        public boolean updateConfiguration (String name, String value, boolean saveToDisk)
        {
        	if (configBuffer == null)
        		configBuffer = new ArrayList<String>();
	        
        	if (resetBuffer == null)
        		resetBuffer = new ArrayList<String>();
	        
        	if (value == null || value.length() == 0)
        	{
        		resetBuffer.add(name);
        		
        	}
        	else
        	{
        		configBuffer.add(name + ' ' + value);
        	}
	        
        	return false;
        }
         
	    public boolean saveConfiguration ()
	    {
	    	try
        	{
	        	if (conn != null)
	        	{
	        		
	        		 if (resetBuffer != null && resetBuffer.size() > 0)
				        {	
				        	conn.resetConf(resetBuffer);
				        	resetBuffer = null;
				        }
	   	       
	        		 if (configBuffer != null && configBuffer.size() > 0)
				        {
	        			 	
				        	conn.setConf(configBuffer);
				        	configBuffer = null;
				        }
	   	       
	   	       		// Flush the configuration to disk.
	        		//this is doing bad things right now NF 22/07/10
	   	       		//conn.saveConf();
	
	   	       		return true;
	        	}
        	}
        	catch (Exception ioe)
        	{
        		Log.e(TAG, "Unable to update Tor configuration", ioe);
        		logNotice("Unable to update Tor configuration: " + ioe.getMessage());
        	}
        	
        	return false;
        	
	    }
    };
    
    private ArrayList<String> callbackBuffer = new ArrayList<String>();
    private boolean inCallbackStatus = false;
    private boolean inCallback = false;
    
    private synchronized void sendCallbackStatusMessage (String newStatus)
    {
    	 
    	if (mCallbacks == null)
    		return;
    	
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        
        inCallback = true;
        
        if (N > 0)
        {
        	 for (int i=0; i<N; i++) {
		            try {
		                mCallbacks.getBroadcastItem(i).statusChanged(newStatus);
		                
		                
		            } catch (RemoteException e) {
		                // The RemoteCallbackList will take care of removing
		                // the dead object for us.
		            }
		        }
        }
        
        mCallbacks.finishBroadcast();
        inCallback = false;
    }
   
    private synchronized void sendCallbackStatusMessage (long upload, long download)
    {
    	 
    	if (mCallbacks == null)
    		return;
    	
        // Broadcast to all clients the new value.
        final int N = mCallbacks.beginBroadcast();
        
        inCallback = true;
        
        if (N > 0)
        {
        	 for (int i=0; i<N; i++) {
		            try {
		                mCallbacks.getBroadcastItem(i).updateBandwidth(upload, download);
		                
		                
		            } catch (RemoteException e) {
		                // The RemoteCallbackList will take care of removing
		                // the dead object for us.
		            }
		        }
        }
        
        mCallbacks.finishBroadcast();
        inCallback = false;
    }
    
    
    private synchronized void sendCallbackLogMessage (String logMessage)
    {
    	 
    	if (mCallbacks == null)
    		return;
    	
    	callbackBuffer.add(logMessage);

    	if (!inCallback)
    	{

	        inCallback = true;
	        // Broadcast to all clients the new value.
	        final int N = mCallbacks.beginBroadcast();
	        
	
	        if (N > 0)
	        {
	        
	        	Iterator<String> it = callbackBuffer.iterator();
	        	String status = null;
	        	
	        	while (it.hasNext())
	        	{
	        		status = it.next();
	        		
			        for (int i=0; i<N; i++) {
			            try {
			                mCallbacks.getBroadcastItem(i).logMessage(status);
			                
			            } catch (RemoteException e) {
			                // The RemoteCallbackList will take care of removing
			                // the dead object for us.
			            }
			        }
	        	}
		        
		        callbackBuffer.clear();
	        }
	        
	        mCallbacks.finishBroadcast();
	        inCallback = false;
    	}
    	
    }
    
    /*
     *  Another way to do this would be to use the Observer pattern by defining the 
     *  BroadcastReciever in the Android manifest.
     */
    private final BroadcastReceiver mNetworkStateReceiver = new BroadcastReceiver() {
    	@Override
    	public void onReceive(Context context, Intent intent) {
    		boolean noConnectivity = intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false);
    		try {
				mBinder.updateConfiguration("DisableNetwork", noConnectivity ? "1" : "0", false);
				mBinder.saveConfiguration();
    		} catch (RemoteException e) {
				logException ("error applying prefs",e);
			}
    	}
    };

    private boolean updateTorConfiguration () throws RemoteException
    {
    	
    	SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		
    	ENABLE_DEBUG_LOG = prefs.getBoolean("pref_enable_logging",true);
    	Log.i(TAG,"debug logging:" + ENABLE_DEBUG_LOG);
    		
		boolean useBridges = prefs.getBoolean(TorConstants.PREF_BRIDGES_ENABLED, false);
		
		//boolean autoUpdateBridges = prefs.getBoolean(TorConstants.PREF_BRIDGES_UPDATED, false);

        boolean becomeRelay = prefs.getBoolean(TorConstants.PREF_OR, false);
        boolean ReachableAddresses = prefs.getBoolean(TorConstants.PREF_REACHABLE_ADDRESSES,false);
        boolean enableHiddenServices = prefs.getBoolean("pref_hs_enable", false);

        boolean enableStrictNodes = prefs.getBoolean("pref_strict_nodes", false);
        String entranceNodes = prefs.getString("pref_entrance_nodes", "");
        String exitNodes = prefs.getString("pref_exit_nodes", "");
        String excludeNodes = prefs.getString("pref_exclude_nodes", "");
        
        String proxyType = prefs.getString("pref_proxy_type", null);
        if (proxyType != null)
        {
        	String proxyHost = prefs.getString("pref_proxy_host", null);
        	String proxyPort = prefs.getString("pref_proxy_port", null);
        	
        	if (proxyHost != null && proxyPort != null)
        	{
        		mBinder.updateConfiguration(proxyType + "Proxy", proxyHost + ':' + proxyPort, false);
        	}
        }
        
        if (entranceNodes.length() > 0 || exitNodes.length() > 0 || excludeNodes.length() > 0)
        {
        	//only apple GeoIP if you need it
	        File fileGeoIP = new File(appBinHome,"geoip");
	        
	        try
	        {
		        if (!fileGeoIP.exists())
		        {
		        	TorBinaryInstaller installer = new TorBinaryInstaller(this, appBinHome); 
					boolean success = installer.installGeoIP();	
		        }
		        
		        mBinder.updateConfiguration("GeoIPFile", fileGeoIP.getAbsolutePath(), false);
	        }
	        catch (IOException e)
	        {
	       	  showToolbarNotification (getString(R.string.error_installing_binares),ERROR_NOTIFY_ID,R.drawable.ic_stat_notify, Notification.FLAG_ONGOING_EVENT);

	        	return false;
	        }
        }

        mBinder.updateConfiguration("EntryNodes", entranceNodes, false);
        mBinder.updateConfiguration("ExitNodes", exitNodes, false);
		mBinder.updateConfiguration("ExcludeNodes", excludeNodes, false);
		mBinder.updateConfiguration("StrictNodes", enableStrictNodes ? "1" : "0", false);
        
		if (useBridges)
		{
			String bridgeList = prefs.getString(TorConstants.PREF_BRIDGES_LIST,getString(R.string.default_bridges));

			if (bridgeList == null || bridgeList.length() == 0)
			{
				String msgBridge = getString(R.string.bridge_requires_ip) +
						getString(R.string.send_email_for_bridges);
				showToolbarNotification(msgBridge, ERROR_NOTIFY_ID, R.drawable.ic_stat_notify, -1);

			
				return false;
			}
			
			mBinder.updateConfiguration("UseBridges", "1", false);
				
			String bridgeDelim = "\n";
			
			if (bridgeList.indexOf(",") != -1)
			{
				bridgeDelim = ",";
			}
			
			showToolbarNotification(getString(R.string.notification_using_bridges) + ": " + bridgeList, TRANSPROXY_NOTIFY_ID, R.drawable.ic_stat_notify, -1);

			boolean obfsBridges = prefs.getBoolean(TorConstants.PREF_BRIDGES_OBFUSCATED, false);
			String bridgeCfgKey = "bridge";

			if (obfsBridges)
			{
				bridgeCfgKey = bridgeCfgKey + " obfs2";
			}

			StringTokenizer st = new StringTokenizer(bridgeList,bridgeDelim);
			while (st.hasMoreTokens())
			{

				mBinder.updateConfiguration(bridgeCfgKey, st.nextToken(), false);

			}

			if (obfsBridges)
			{
				mBinder.updateConfiguration("ClientTransportPlugin","obfs2 exec " + fileObfsProxy.getAbsolutePath() + " --managed", false);
			}

			mBinder.updateConfiguration("UpdateBridgesFromAuthority", "0", false);
		}
		else
		{
			mBinder.updateConfiguration("UseBridges", "0", false);

		}

        try
        {
            if (ReachableAddresses)
            {
                String ReachableAddressesPorts =
                    prefs.getString(TorConstants.PREF_REACHABLE_ADDRESSES_PORTS, "*:80,*:443");
                
                mBinder.updateConfiguration("ReachableAddresses", ReachableAddressesPorts, false);

            }
            else
            {
                mBinder.updateConfiguration("ReachableAddresses", "", false);
            }
        }
        catch (Exception e)
        {
     	  showToolbarNotification (getString(R.string.your_reachableaddresses_settings_caused_an_exception_),ERROR_NOTIFY_ID,R.drawable.ic_stat_notify, Notification.FLAG_ONGOING_EVENT);

           return false;
        }

        try
        {
            if (becomeRelay && (!useBridges) && (!ReachableAddresses))
            {
                int ORPort =  Integer.parseInt(prefs.getString(TorConstants.PREF_OR_PORT, "9001"));
                String nickname = prefs.getString(TorConstants.PREF_OR_NICKNAME, "Orbot");

                String dnsFile = writeDNSFile ();
                
                mBinder.updateConfiguration("ServerDNSResolvConfFile", dnsFile, false);
                mBinder.updateConfiguration("ORPort", ORPort + "", false);
    			mBinder.updateConfiguration("Nickname", nickname, false);
    			mBinder.updateConfiguration("ExitPolicy", "reject *:*", false);

            }
            else
            {
            	mBinder.updateConfiguration("ORPort", "", false);
    			mBinder.updateConfiguration("Nickname", "", false);
    			mBinder.updateConfiguration("ExitPolicy", "", false);
            }
        }
        catch (Exception e)
        {
       	  showToolbarNotification (getString(R.string.your_relay_settings_caused_an_exception_),ERROR_NOTIFY_ID,R.drawable.ic_stat_notify, Notification.FLAG_ONGOING_EVENT);

          
            return false;
        }

        if (enableHiddenServices)
        {
        	mBinder.updateConfiguration("HiddenServiceDir",appCacheHome.getAbsolutePath(), false);
        	
        	String hsPorts = prefs.getString("pref_hs_ports","");
        	
        	StringTokenizer st = new StringTokenizer (hsPorts,",");
        	String hsPortConfig = null;
        	
        	while (st.hasMoreTokens())
        	{
        		hsPortConfig = st.nextToken();
        		
        		if (hsPortConfig.indexOf(":")==-1) //setup the port to localhost if not specifed
        		{
        			hsPortConfig = hsPortConfig + " 127.0.0.1:" + hsPortConfig;
        		}
        		
        		mBinder.updateConfiguration("HiddenServicePort",hsPortConfig, false);
        	}
        	
        	
        }
        else
        {
        	mBinder.updateConfiguration("HiddenServiceDir","", false);
        	
        }

        mBinder.saveConfiguration();
	
        return true;
    }
    
    //using Google DNS for now as the public DNS server
    private String writeDNSFile () throws IOException
    {
    	File file = new File(appBinHome,"resolv.conf");
    	
    	PrintWriter bw = new PrintWriter(new FileWriter(file));
    	bw.println("nameserver 8.8.8.8");
    	bw.println("nameserver 8.8.4.4");
    	bw.close();
    
    	return file.getAbsolutePath();
    }
   
   
}
