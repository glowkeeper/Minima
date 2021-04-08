package org.minima.kissvm.functions.number;

import org.minima.kissvm.Contract;
import org.minima.kissvm.exceptions.ExecutionException;
import org.minima.kissvm.functions.MinimaFunction;
import org.minima.kissvm.values.NumberValue;
import org.minima.kissvm.values.Value;

public class INC extends MinimaFunction {

	public INC() {
		super("INC");
	}
	
	@Override
	public Value runFunction(Contract zContract) throws ExecutionException {
		checkExactParamNumber(1);
		
		NumberValue number = zContract.getNumberParam(0, this);

		return new NumberValue(number.getNumber().increment());
	}
	
	@Override
	public MinimaFunction getNewFunction() {
		return new INC();
	}
}
