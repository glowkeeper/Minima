package org.minima.system.input.functions;

import org.minima.system.brains.ConsensusPrint;
import org.minima.system.input.CommandFunction;
import org.minima.utils.messages.Message;

public class txpowsearch extends CommandFunction{

	public txpowsearch() {
		super("txpowsearch");
		
		setHelp("(input:address) (output:address) (tokenid:tokenid) (block:blocknumber) (state:statevars)", "Search for TXPOW","");
	}
	
	@Override
	public void doFunction(String[] zInput) throws Exception {
		int len = zInput.length;
		if(len == 1) {
			getResponseStream().endStatus(false, "MUST specify some criteria for search..");
			return;
		}
		
		//The extra data
		String input        = "";
		String output       = "";
		String token        = "";
		String block    	= "";
		String state    	= "";
		
		//Cycle through..
		for(int i=1;i<len;i++) {
			String param = zInput[i];
			
			if(param.startsWith("input:")) {
				input = param.substring(6);
			}else if(param.startsWith("output:")) {
				output = param.substring(7);
			}else if(param.startsWith("tokenid:")) {
				token = param.substring(8);
			}else if(param.startsWith("block:")) {
				block = param.substring(6);
			}else if(param.startsWith("state:")) {
				state = param.substring(6);
			}
		}
		
		//Create the message
		Message sender = getResponseMessage(ConsensusPrint.CONSENSUS_TXPOWSEARCH);
		sender.addString("input", input);
		sender.addString("output", output);
		sender.addString("tokenid", token);
		sender.addString("block", block);
		sender.addString("state", state);
		
		//Send it to the miner..
		getMainHandler().getConsensusHandler().PostMessage(sender);
	}
	
	@Override
	public CommandFunction getNewFunction() {
		// TODO Auto-generated method stub
		return new txpowsearch();
	}
}
