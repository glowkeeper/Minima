package org.minima.system.input.functions;

import org.minima.system.brains.ConsensusHandler;
import org.minima.system.input.CommandFunction;
import org.minima.system.txpow.TxPoWMiner;

public class debugtester extends CommandFunction{

	public debugtester() {
		super("debugtester");
		setHelp("", "My own debug test", "");
	}
	
	@Override
	public void doFunction(String[] zInput) throws Exception {
		getMainHandler().getConsensusHandler().PostMessage(getResponseMessage(ConsensusHandler.CONSENSUS_DEBUGTEST));
	}

	@Override
	public CommandFunction getNewFunction() {
		// TODO Auto-generated method stub
		return new debugtester();
	}
}
