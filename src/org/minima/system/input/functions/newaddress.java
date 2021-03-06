package org.minima.system.input.functions;

import org.minima.system.brains.ConsensusUser;
import org.minima.system.input.CommandFunction;
import org.minima.utils.messages.Message;

public class newaddress extends CommandFunction{

	public newaddress() {
		super("newaddress");
		setHelp("(bitlength keys levels)", "Create a new address to receive funds", "");
	}
	
	@Override
	public void doFunction(String[] zInput) throws Exception {
		//Get a response message
		Message msg = getResponseMessage(ConsensusUser.CONSENSUS_NEWSIMPLE);
				
		if(zInput.length>1) {
			int bits = Integer.parseInt(zInput[1]);
			msg.addInteger("bitlength", bits);
			
			int keys = Integer.parseInt(zInput[2]);
			msg.addInteger("keys", keys);
			
			int levels = Integer.parseInt(zInput[3]);
			msg.addInteger("levels", levels);
		}
		
		//Post a new Message
		getMainHandler().getConsensusHandler().PostMessage(msg);
	}

	@Override
	public CommandFunction getNewFunction() {
		// TODO Auto-generated method stub
		return new newaddress();
	}
}
