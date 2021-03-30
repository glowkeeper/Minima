package org.minima.kissvm.functions.sha;

import org.minima.kissvm.Contract;
import org.minima.kissvm.exceptions.ExecutionException;
import org.minima.kissvm.functions.MinimaFunction;
import org.minima.kissvm.values.HEXValue;
import org.minima.kissvm.values.Value;
import org.minima.utils.Crypto;

public class SHA2 extends MinimaFunction {

	/**
	 * @param zName
	 */
	public SHA2() {
		super("SHA2");
	}
	
	/* (non-Javadoc)
	 * @see org.ramcash.ramscript.functions.Function#runFunction()
	 */
	@Override
	public Value runFunction(Contract zContract) throws ExecutionException {
		checkExactParamNumber(1);
		
		Value vv = getParameter(0).getValue(zContract);
		checkIsOfType(vv, Value.VALUE_HEX | Value.VALUE_STRING);
		
		//get the Input Data - HEX or SCRIPT
		HEXValue hex = (HEXValue)vv;
		byte[] data = hex.getRawData();

		//Perform the SHA2 Operation
		byte[] ans = Crypto.getInstance().hashSHA2(data);
		
		//return the New HEXValue
		return new HEXValue(ans);
	}
	
	@Override
	public MinimaFunction getNewFunction() {
		return new SHA2();
	}
}
