/**
 * 
 */
package org.minima.utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import org.minima.system.brains.ConsensusHandler;
import org.minima.utils.messages.Message;

/**
 * @author Spartacus Rex
 *
 */
public class MinimaLogger {
	
	public static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.ENGLISH );
	
	public static boolean LOGGING_ON 	 = true;
	
	public static final int LOG_ERROR 	 = 0;
	public static final int LOG_INFO 	 = 1;
	
	/**
	 * Previous Output..
	 */
	public static int MAX_FULL_LEN =  250000;
	public static int CLIP_LEN     =  200000;
	private static StringBuffer mFullOutput = new StringBuffer();
	public static String getFullOutput() {
		return mFullOutput.toString();
	}

	/**
	 * Send LOG messages to all those listening...
	 */
	static ConsensusHandler mLogHandler = null;
	
	public static void setMainHandler(ConsensusHandler zLogHandler) {
		mLogHandler = zLogHandler;
	}
	
	public static void log(String zLog){
		if(LOGGING_ON){
			//Ensure max size.. for now just wipe once and start again..
			int len = mFullOutput.length();
			if(len>MAX_FULL_LEN) {
				mFullOutput = new StringBuffer(mFullOutput.substring(len-CLIP_LEN, len));
			}
			
			long mem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
		    
			String full_log = "Minima @ "+DATEFORMAT.format(new Date())+" ["+MiniFormat.formatSize(mem)+"] : "+zLog;
			System.out.println(full_log);
			
//			String full_log = "Minima @ "+DATEFORMAT.format(new Date())+" : "+zLog;
//			System.out.println(full_log);
	
			//Store..
			mFullOutput.append(full_log+"\n");
			
			//Forward to listeners..
			if(mLogHandler != null) {
				Message log = new Message(ConsensusHandler.CONSENSUS_NOTIFY_LOG);
				log.addString("msg", full_log);
				mLogHandler.updateListeners(log);
			}
		}
	}	
	
	public static void log(Exception zException){
		if(LOGGING_ON){
			//First the Full Exception
			MinimaLogger.log(zException.toString());
			
			//Now the Stack Trace
			for(StackTraceElement stack : zException.getStackTrace()) {
				//Print it..
				MinimaLogger.log("     "+stack.toString());
			}
		}
	}
	
	public static void log(String zTitle, Exception zException){
		if(LOGGING_ON){
			//A Title..
			MinimaLogger.log(zTitle);
			
			//First the Full Exception
			MinimaLogger.log(zException.toString());
			
			//Now the Stack Trace
			for(StackTraceElement stack : zException.getStackTrace()) {
				//Print it..
				MinimaLogger.log("     "+stack.toString());
			}
		}
	}
}
