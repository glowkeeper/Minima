package org.minima.kissvm.functions.cast;

import org.minima.kissvm.Contract;
import org.minima.kissvm.exceptions.ExecutionException;
import org.minima.kissvm.functions.MinimaFunction;
import org.minima.kissvm.values.HEXValue;
import org.minima.kissvm.values.NumberValue;
import org.minima.kissvm.values.StringValue;
import org.minima.kissvm.values.Value;

public class HEX extends MinimaFunction{

	public HEX() {
		super("HEX");
	}
	
	@Override
	public Value runFunction(Contract zContract) throws ExecutionException {
		checkExactParamNumber(1);
		
		//Get the Value..
		Value val = getParameter(0).getValue(zContract);
		
		if(val.getValueType() == Value.VALUE_BOOLEAN) {
			if(val.isTrue()) {
				return new HEXValue("0x01");
			}else{
				return new HEXValue("0x00");
			}
		
		}else if(val.getValueType() == Value.VALUE_NUMBER) {
			NumberValue nv = (NumberValue)val;
			
			try {
				HEXValue hex = new HEXValue(nv.getNumber());
				return hex;
			}catch(NumberFormatException nexc) {
				throw new ExecutionException(nexc.toString());
			}
	
		}else if(val.getValueType() == Value.VALUE_STRING) {
			StringValue nv = (StringValue)val;
			return new HEXValue(nv.getMiniString().getData());
		}
		
		return val;
	}

	@Override
	public MinimaFunction getNewFunction() {
		return new HEX();
	}
}
