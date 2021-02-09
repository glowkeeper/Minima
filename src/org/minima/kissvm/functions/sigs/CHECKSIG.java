package org.minima.kissvm.functions.sigs;

import org.minima.kissvm.Contract;
import org.minima.kissvm.exceptions.ExecutionException;
import org.minima.kissvm.functions.MinimaFunction;
import org.minima.kissvm.values.BooleanValue;
import org.minima.kissvm.values.HEXValue;
import org.minima.kissvm.values.Value;
import org.minima.objects.base.MiniData;
import org.minima.objects.keys.MultiKey;

/**
 * for now only retur  true..
 * 
 * @author spartacusrex
 *
 */
public class CHECKSIG extends MinimaFunction {

	public CHECKSIG() {
		super("CHECKSIG");
	}
	
	@Override
	public Value runFunction(Contract zContract) throws ExecutionException {
		checkExactParamNumber(3);
		
		//Get the Pbkey
		HEXValue pubkey = zContract.getHEXParam(0, this);
		
		//get the data
		HEXValue data   = zContract.getHEXParam(1, this);
		
		//Get the signature
		HEXValue sig    = zContract.getHEXParam(2, this);
		
		//Check it..
		MiniData pubk = new MiniData(pubkey.getMiniData().getData());
		
		//Create a MultiKey to check the signature
		MultiKey checker = new MultiKey();
		checker.setPublicKey(pubk);
		
		//Check it..
		boolean ok = checker.verify(new MiniData(data.getRawData()), sig.getMiniData());
		
		return new BooleanValue(ok);
	}
	
	@Override
	public MinimaFunction getNewFunction() {
		return new CHECKSIG();
	}

}
