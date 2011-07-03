/* Copyright (c) 2009, Nathan Freitas, Orbot / The Guardian Project - http://openideals.com/guardian */
/* See LICENSE for licensing information */
package org.torproject.android.service;

public interface TorServiceConstants {

	public final static String TAG = "ORBOT";

	public final static String TOR_APP_USERNAME = "org.torproject.android";
	
	public final static String ASSETS_BASE = "assets/";
	
	//home directory of Android application
	
	//name of the tor C binary
	public final static String TOR_BINARY_ASSET_KEY = "tor";	
	
	//torrc (tor config file)
	public final static String TORRC_ASSET_KEY = "torrc";
	public final static String TOR_CONTROL_COOKIE = "control_auth_cookie";
	
	//how to launch tor
//	public final static String TOR_COMMAND_LINE_ARGS = "-f " + TORRC_INSTALL_PATH  + " || exit\n";
		
	//privoxy
	public final static String PRIVOXY_ASSET_KEY = "privoxy";
	
	//privoxy.config
	public final static String PRIVOXYCONFIG_ASSET_KEY = "privoxy.config";
		
	//various console cmds
	public final static String SHELL_CMD_CHMOD = "chmod";
	public final static String SHELL_CMD_KILL = "kill -9";
	public final static String SHELL_CMD_RM = "rm";
	public final static String SHELL_CMD_PS = "ps";
	public final static String SHELL_CMD_PIDOF = "pidof";

	public final static String CHMOD_EXE_VALUE = "777";
	
	//path of the installed APK file
	//public final static String APK_PATH = "/data/app/org.torproject.android.apk";
	//public final static String APK_PATH_BASE = "/data/app";

	
	
	public final static int FILE_WRITE_BUFFER_SIZE = 2048;
	
	//HTTP Proxy server port
	public final static int PORT_HTTP = 8118; //just like Privoxy!
	
	//Socks port client connects to, server is the Tor binary
	public final static int PORT_SOCKS = 9050;
	
	//what is says!
	public final static String IP_LOCALHOST = "127.0.0.1";
	public final static int TOR_CONTROL_PORT = 9051;
	public final static int UPDATE_TIMEOUT = 1000;
	public final static int TOR_TRANSPROXY_PORT = 9040;
	public final static int STANDARD_DNS_PORT = 53;
	public final static int TOR_DNS_PORT = 5400;
	
	//path to check Tor against
	public final static String URL_TOR_CHECK = "https://check.torproject.org";
		
    //IPTABLES
//	public final static String CMD_IPTABLES_PREROUTING = "iptables -t nat -A OUTPUT -p tcp --dport 80 -j DNAT --to 127.0.0.1:8118 || exit\n";
	//public final static String CMD_IPTABLES_PREROUTING_FLUSH = "iptables -t nat -F || exit\n";

    //control port 
    public final static String TOR_CONTROL_PORT_MSG_BOOTSTRAP_DONE = "Bootstrapped 100%";
    
    public final static int STATUS_OFF = 0;
 //   public final static int STATUS_READY = 0;
    public final static int STATUS_ON = 1;
    public final static int STATUS_CONNECTING = 2;
    
    public final static int PROFILE_OFF = -1;
    public final static int PROFILE_ON = 1;

    public static final int STATUS_MSG = 1;
    public static final int ENABLE_TOR_MSG = 2;
    public static final int DISABLE_TOR_MSG = 3;
    public static final int LOG_MSG = 4;
	public static final int MESSAGE_TRAFFIC_COUNT = 5;

}
