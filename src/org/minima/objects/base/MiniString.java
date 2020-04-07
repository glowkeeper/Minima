package org.minima.objects.base;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;

import org.minima.kissvm.Contract;
import org.minima.utils.Streamable;

public class MiniString extends MiniData {
	
	private MiniString() {
		super();
	}
	
	public MiniString(String zString) {
		super(Contract.cleanScript(zString).getBytes(Charset.forName("US-ASCII")));
	}
	
	@Override
	public String toString() {
		return new String(getData(),Charset.forName("US-ASCII"));
	}

	public static MiniString ReadFromStream(DataInputStream zIn){
		MiniString data = new MiniString();
		
		try {
			data.readDataStream(zIn);
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
		
		return data;
	}
}

