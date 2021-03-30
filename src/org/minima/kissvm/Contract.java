package org.minima.kissvm;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.StringTokenizer;

import org.minima.kissvm.exceptions.ExecutionException;
import org.minima.kissvm.functions.MinimaFunction;
import org.minima.kissvm.statements.StatementBlock;
import org.minima.kissvm.statements.StatementParser;
import org.minima.kissvm.tokens.Token;
import org.minima.kissvm.values.BooleanValue;
import org.minima.kissvm.values.HEXValue;
import org.minima.kissvm.values.NumberValue;
import org.minima.kissvm.values.ScriptValue;
import org.minima.kissvm.values.Value;
import org.minima.objects.StateVariable;
import org.minima.objects.Transaction;
import org.minima.objects.Witness;
import org.minima.objects.base.MiniData;
import org.minima.objects.base.MiniNumber;
import org.minima.utils.MinimaLogger;
import org.minima.utils.json.JSONObject;

public class Contract {
	
	//The Transaction this Contract is being run on
	Transaction mTransaction;
	
	//The Witness
	Witness mWitness;
		
	//The final version of the script
	String mRamScript; 
		
	//The Complete contract code, from root level
	StatementBlock mBlock;
	
	//A list of valid signatures
	ArrayList<HEXValue> mSignatures;
	
	//A list of all the user-defined variables
	Hashtable<String, Value> mVariables;
	
	//A list of all the global variables available to scripts, like Blocknumber etc..
	Hashtable<String, Value> mGlobals;
		
	//The previous state variables - accessed from the MMR data
	ArrayList<StateVariable> mPrevState = new ArrayList<StateVariable>();
	
	//A list of all the user-defined variables
	boolean mFloatingCoin = false;
	
	//Is this MonoTonic
	boolean mMonotonic = true;
	
	//Has the Script returned TRUE or FALSE
	private boolean mSuccess;
	private boolean mSuccessSet;
	
	/**
	 * Trace shows debug info as the program is parsed and executed
	 */
	boolean mMinimaLog = false;
	
	/**
	 * Did the contract script parse ok
	 */
	boolean mParseOK;
	
	/**
	 * Was there an Exception error
	 */
	boolean mException;
	String  mExceptionString;
	
	/**
	 * The Number Of Instructions!
	 */
	int mNumInstructions;
	public static final int MAX_INSTRUCTIONS = 512;
	
	/**
	 * A complete log of the contract execution
	 */
	String mCompleteLog="";
	
	/**
	 * Main Constructor
	 * @param zRamScript - the RamScript in ASCII
	 */
	public Contract(String zRamScript, String zSigs, Witness zWitness, Transaction zTransaction, ArrayList<StateVariable> zPrevState) {	
		this(zRamScript, zSigs, zWitness, zTransaction, zPrevState, false);
	}
	
	public Contract(String zRamScript, String zSigs, Witness zWitness, Transaction zTransaction, ArrayList<StateVariable> zPrevState, boolean zMinimalog) {
		//Trace?
		mCompleteLog ="";
		mMinimaLog     = zMinimalog;
		
		//Clean the RamScript
		mRamScript = cleanScript(zRamScript);
	
		mTransaction = zTransaction;
		mWitness     = zWitness;
	
		mSignatures = new ArrayList<>();
		mVariables  = new Hashtable<>();
		mGlobals    = new Hashtable<>();

		mFloatingCoin = false;
		
		mBlock      = null;
		mSuccess    = false;
		mSuccessSet = false;
		mParseOK    = false;
		mException  = false;
		mExceptionString = "";
		
		mNumInstructions = 0;
		
		mMonotonic = true;
		
		//Begin..
		traceLog("Contract   : "+mRamScript);
		traceLog("Size       : "+mRamScript.length());
		
		//Load the Signatures
		StringTokenizer strtok = new StringTokenizer(zSigs, "#");
		while(strtok.hasMoreTokens()) {
			String sig = strtok.nextToken().trim();
			traceLog("Signature : "+sig);
			mSignatures.add( (HEXValue)Value.getValue(sig) );
		}
		
		//Transaction..
		traceLog("Transaction   : "+mTransaction.toString());
		traceLog("Witness       : "+mWitness.toString());
		
		//State Variables
		ArrayList<StateVariable> svs = mTransaction.getCompleteState();
		for(StateVariable sv : svs) {
			traceLog("State["+sv.getPort()+"] : "+sv.getValue().toString());
		}

		//PREVSTATE
		if(zPrevState == null) {
			mPrevState = new ArrayList<StateVariable>();	
		}else {
			mPrevState = zPrevState;
			for(StateVariable sv : mPrevState) {
				traceLog("PrevState["+sv.getPort()+"] : "+sv.getValue().toString());
			}	
		}
		
		//Parse the tokens
		try {
			//Tokenize the script
			List<Token> tokens = Token.tokenize(mRamScript);
			
			int count=0;
			for(Token tok : tokens) {
				traceLog((count++)+") Token : ["+tok.getTokenTypeString()+"] "+tok.getToken());
			}
		
			//Convert this list of Tokens into a list of Statements
			mBlock = StatementParser.parseTokens(tokens);
			
			traceLog("Script token parse OK.");
			mParseOK = true;
			
		} catch (Exception e) {
			mException  = true;
			mExceptionString = e.toString();
			
			traceLog("PARSE ERROR : "+mExceptionString);
		}
	}
	
	public void setGlobalVariable(String zGlobal, Value zValue) {
		mGlobals.put(zGlobal, zValue);
		traceLog("Global ["+zGlobal+"] : "+zValue);
	}
	
	public Hashtable<String, Value> getGlobalVariables() {
		return mGlobals;
	}
	
	public void setAllGlobalVariables(Hashtable<String, Value> zGlobals) {
		mGlobals = zGlobals;
	}
	
	public boolean isParseOK() {
		return mParseOK;
	}
	
	public boolean isException() {
		return mException;
	}
	
	public String getException() {
		return mExceptionString;
	}
	
	public boolean isLogged() {
		return mMinimaLog;
	}
	
	public void traceLog(String zLog) {
		if(isLogged()) {
			MinimaLogger.log("INST["+mNumInstructions+"] - "+zLog);
		}
		
		//Store the complete Log
		mCompleteLog += "INST["+mNumInstructions+"] - "+zLog+"\n";
	}
	
	public String getCompleteTraceLog() {
		return mCompleteLog;
	}
	
	public void incrementInstructions() throws ExecutionException {
		mNumInstructions++;
		if(mNumInstructions > MAX_INSTRUCTIONS) {
			throw new ExecutionException("MAX instruction number reached! "+mNumInstructions);
		}
	}
	
	public int getNumberOfInstructions() {
		return mNumInstructions;
	}
	
	public void run() {
		if(!mParseOK) {
			traceLog("Script parse FAILED. Please fix and retry.");
			return;
		}
		
		//Run the code block
		try {
			traceLog("Start executing the contract");
			
			mBlock.run(this);
			
		} catch (Exception e) {
			if(mMinimaLog) {
				e.printStackTrace();
			}
			
			mException = true;
			mExceptionString = e.toString();
			
			//AUTOMATIC FAIL
			traceLog("Execution Error - "+e);
			
			mSuccess 	= false;
			mSuccessSet = true;
		}
		
		//How'd it go..
		traceLog("Contract instructions : "+mNumInstructions);
		traceLog("Contract finished     : "+mSuccess);
	}
	
	public void setRETURNValue(boolean zSUCCESS) {
		if(!mSuccessSet) {
			mSuccess 	= zSUCCESS;
			mSuccessSet = true;
		}
	}
	
	public boolean isSuccess() {
		return mSuccess;
	}
	
	public boolean isSuccessSet() {
		return mSuccessSet;
	}
	
	public boolean isMonotonic() {
		return mMonotonic;
	}
		
	public String getMiniScript() {
		return mRamScript;
	}
	
	public Transaction getTransaction() {
		return mTransaction;
	}
	
	public Witness getWitness() {
		return mWitness;
	}
	
	public JSONObject getAllVariables() {
		JSONObject variables = new JSONObject();
		
		//First the normal variables
		Enumeration<String> keys = mVariables.keys();
		while(keys.hasMoreElements()) {
			//Get the Key
			String key = keys.nextElement();
			
			//Get the Value
			Value val = mVariables.get(key);
			
			//Remove the commas for JSON formating
			if(key.contains(",")) {
				key = key.replace(",", " ");
				key = "( "+key.trim()+" )";
			}
			
			variables.put(key, val.toString());
//			if(val.getValueType() == ScriptValue.VALUE_SCRIPT) {
//				variables.put(key, "[ "+val.toString()+" ]");
//			}else{
//				variables.put(key, val.toString());
//			}
		}
		
		return variables;
	}
	
	public Value getVariable(String zName) throws ExecutionException {
		Value ret = mVariables.get(zName);
		return ret;
	}
	
	public void setVariable(String zName, Value zValue) {
		mVariables.put(zName, zValue);
		traceVariables();
	}
	
	/**
	 * Get the Parameter Value as a MiniNumber or throw exception if not a NUmber 
	 * @throws ExecutionException 
	 */
	public NumberValue getNumberParam(int zParamNumber, MinimaFunction zFunction) throws ExecutionException {
		Value vv = zFunction.getParameter(zParamNumber).getValue(this);
		if(vv.getValueType() != Value.VALUE_NUMBER) {
			throw new ExecutionException("Incorrect Parameter type - should be NumberValue @ "+zParamNumber+" "+zFunction.getName());
		}
		return (NumberValue)vv;
	}
	
	public HEXValue getHEXParam(int zParamNumber, MinimaFunction zFunction) throws ExecutionException {
		Value vv = zFunction.getParameter(zParamNumber).getValue(this);
		if(vv.getValueType() != Value.VALUE_HEX) {
			throw new ExecutionException("Incorrect Parameter type - should be HEXValue @ "+zParamNumber+" "+zFunction.getName());
		}
		return (HEXValue)vv;
	}
	
	public ScriptValue getScriptParam(int zParamNumber, MinimaFunction zFunction) throws ExecutionException {
		Value vv = zFunction.getParameter(zParamNumber).getValue(this);
		if(vv.getValueType() != Value.VALUE_SCRIPT) {
			throw new ExecutionException("Incorrect Parameter type - should be ScriptValue @ "+zParamNumber+" "+zFunction.getName());
		}
		return (ScriptValue)vv;
	}
	
	public BooleanValue getBoolParam(int zParamNumber, MinimaFunction zFunction) throws ExecutionException {
		Value vv = zFunction.getParameter(zParamNumber).getValue(this);
		if(vv.getValueType() != Value.VALUE_BOOLEAN) {
			throw new ExecutionException("Incorrect Parameter type - should be BooleanValue @ "+zParamNumber+" "+zFunction.getName());
		}
		return (BooleanValue)vv;
	}
	
	/**
	 * DYN State
	 */
	public void setFloating(boolean zFloating) {
		if(zFloating) {
			mMonotonic = false;
		}
		
		mFloatingCoin = zFloating;
	}
	
	public Value getState(int zStateNum) throws ExecutionException {
		if(!mTransaction.stateExists(zStateNum)) {
			throw new ExecutionException("State Variable does not exist "+zStateNum);
		}

		//Get it from the Transaction..
		String stateval = mTransaction.getStateValue(zStateNum).getValue().toString();
		
		//Clean it
		String clean = cleanScript(stateval);
		
		//Clean it..
		return Value.getValue(clean);
	}
	
	public Value getPrevState(int zPrev) throws ExecutionException {
		//Get the state variable..
		for(StateVariable sv : mPrevState) {
			if(sv.getPort() == zPrev) {
				//Clean it..
				String stateval = cleanScript(sv.getValue().toString());
				
				//Work it out
				return Value.getValue(stateval);
			}
		}
		
		throw new ExecutionException("PREVSTATE Missing : "+zPrev);
	}
	
	/**
	 * Could use the JSON but this looks better as no quotes.. ;p
	 */
	public void traceVariables() {
		//Output..
		String varlist = "{ ";
		
		//First the normal variables
		Enumeration<String> keys = mVariables.keys();
		while(keys.hasMoreElements()) {
			//Get the Key
			String key = keys.nextElement();
			
			//Get the Value
			Value val = mVariables.get(key);
			
			//Remove the commas for JSON formating
			if(key.contains(",")) {
				key = key.replace(",", " ");
				key = "( "+key.trim()+" )";
			}
			
			//Log it.. 
			int type = val.getValueType();
			varlist += key+" = "+val+", ";
//			switch (type)  {
//				case BooleanValue.VALUE_BOOLEAN :
//					varlist += key+" = "+Boolean.toString(val.isTrue()).toUpperCase()+", ";
//				break;
//				case ScriptValue.VALUE_SCRIPT : xx
//					varlist += key+" = [ "+val+" ], ";
//				break;
//				default:
//					varlist += key+" = "+val+", ";
//				break;
//			}		
		}
		
		traceLog(varlist+"}");
	}
	
	/**
	 * Get a Global value
	 * 
	 * @param zGlobal
	 * @return the value
	 * @throws ExecutionException
	 */
	public Value getGlobal(String zGlobal) throws ExecutionException {
		mMonotonic = false;
		
		Value ret = mGlobals.get(zGlobal);
		if(ret==null) {
			throw new ExecutionException("Global not found - "+zGlobal);
		}
		
		return ret;
	}
	
	/**
	 * Check if this transaction has been signed by this public key
	 * @param zSignature
	 * @return
	 */
	public boolean checkSignature(HEXValue zSignature) {
		MiniData checksig = zSignature.getMiniData();
		
		for(HEXValue sig : mSignatures) {
			if(sig.getMiniData().isEqual(checksig)) {
				return true;
			}
		}
		
		return false;
	}
	
	/**
	 * Convert a SCRIPT into the Required Format for MiniScript.
	 * @param zScript
	 * @return The Converted Script
	 */
	public static String cleanScript(String zScript) {
		//Quick check for empty..
		if(zScript.equals("")) {
			return "";
		}
		
		//Start cleaning..
		String script = new String(" "+zScript.toLowerCase()+" ");
		
		//Replace whitespace with a single space
		script = script.replaceAll("\\s+"," ");
		
		//Remove comments /* .. */
		int comment = script.indexOf("/*");
		while(comment != -1) {
			int endcomment = script.indexOf("*/",comment);
			int len = script.length();
			script = " "+script.substring(0,comment)+" "+script.substring(endcomment+2, len)+" ";
			comment = script.indexOf("/*");
		}
		
		//Incase this is a 'param' string
		script = script.replaceAll(",", " , ");
		script = script.replaceAll(";", " ; ");
		script = script.replaceAll(":", " : ");
		script = script.replaceAll("#", " # ");
		
		//Double up the spaces.. in case of double NOT 
		script = script.replaceAll(" ", "  ");
		
		//STILL NEED TO DO - .. minus.. ignoring numbers..
//		script = script.replaceAll("\\-[a-z]", " - ");
		
		//Operators
		script = script.replaceAll("\\(", " ( ");
		script = script.replaceAll("\\)", " ) ");
		script = script.replaceAll("\\[", " [ ");
		script = script.replaceAll("\\]", " ] ");
		script = script.replaceAll("<<", " << ");
		script = script.replaceAll(">>", " >> ");
		script = script.replaceAll("\\&" , " & ");
		script = script.replaceAll("\\|" , " | ");
		script = script.replaceAll("\\^" , " ^ ");
		script = script.replaceAll("\\*", " * ");
		script = script.replaceAll("\\+", " + ");
		script = script.replaceAll("\\=", " = ");
		script = script.replaceAll("\\%", " % ");
			
		//Boolean
		script = script.replaceAll(" nand ", " NAND ");
		script = script.replaceAll(" nxor ", " NXOR ");
		script = script.replaceAll(" nor ", " NOR ");
		script = script.replaceAll(" and ", " AND ");
		script = script.replaceAll(" xor ", " XOR ");
		script = script.replaceAll(" or ", " OR ");
		script = script.replaceAll(" not ", " NOT ");
		script = script.replaceAll(" neg ", " NEG ");
		script = script.replaceAll(" neq ", " NEQ ");
		script = script.replaceAll(" gte ", " GTE ");
		script = script.replaceAll(" lte ", " LTE ");
		script = script.replaceAll(" gt ", " GT ");
		script = script.replaceAll(" eq ", " EQ ");
		script = script.replaceAll(" lt ", " LT ");
		
		//Commands
		String[] allcommands = Token.TOKENS_COMMAND;
		for(int i=0;i<allcommands.length;i++) {
			String find = " "+allcommands[i].toLowerCase()+" ";
			String repl = " "+allcommands[i]+" ";
			script = script.replaceAll(find,repl);
		}
		
		script = script.replaceAll(" true ", " TRUE ");
		script = script.replaceAll(" false ", " FALSE ");
		
		//@Globals
		script = script.replaceAll(" @blknum "	    , " @BLKNUM ");
		script = script.replaceAll(" @blktime "	    , " @BLKTIME ");
		script = script.replaceAll(" @prevblkhash " , " @PREVBLKHASH ");
		script = script.replaceAll(" @input "	    , " @INPUT ");
		script = script.replaceAll(" @address "	    , " @ADDRESS ");
		script = script.replaceAll(" @amount "	    , " @AMOUNT "); 
		script = script.replaceAll(" @coinid "	    , " @COINID "); 
		script = script.replaceAll(" @script "	    , " @SCRIPT "); 
		script = script.replaceAll(" @tokenid "	    , " @TOKENID "); 
		script = script.replaceAll(" @tokenscript "	, " @TOKENSCRIPT "); 
		script = script.replaceAll(" @tokentotal"	, " @TOKENTOTAL"); 
		script = script.replaceAll(" @floating "	, " @FLOATING"); 
		script = script.replaceAll(" @totin "	    , " @TOTIN "); 
		script = script.replaceAll(" @totout " 	    , " @TOTOUT ");
		script = script.replaceAll(" @inblknum "    , " @INBLKNUM ");
		script = script.replaceAll(" @blkdiff "     , " @BLKDIFF ");
		
		//And now do all the functions
		for(MinimaFunction func : MinimaFunction.ALL_FUNCTIONS) {
			//Name
			String name = func.getName();
			
			//replace
			script = script.replaceAll(" "+name.toLowerCase()+" ", " "+name+" ");
		}
			
		//Convert the HEX to upper case..
		String finalstring = "";
		StringTokenizer strtok = new StringTokenizer(script," ");
		while(strtok.hasMoreTokens()) {
			String tok = strtok.nextToken();
			if(tok.startsWith("0x")) {
				finalstring = finalstring.concat(" 0x"+tok.substring(2).toUpperCase());
			}else {
				finalstring = finalstring.concat(" "+tok);
			}
		}
		
		//Remove all the excess white space
		script = script.replaceAll("\\s+"," ").trim();
				
		//Boom..
		return finalstring.trim();
	}
}
