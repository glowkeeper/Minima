package org.minima.utils.mmr;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Random;

import org.minima.database.mmr.MMRData;
import org.minima.database.mmr.MMREntry;
import org.minima.objects.Coin;
import org.minima.objects.base.MiniByte;
import org.minima.objects.base.MiniData;
import org.minima.objects.base.MiniNumber;
import org.minima.objects.proofs.Proof.ProofChunk;
import org.minima.utils.Crypto;
import org.minima.utils.MinimaLogger;
import org.minima.utils.ObjectStack;
import org.minima.utils.Streamable;
import org.minima.utils.json.JSONArray;
import org.minima.utils.json.JSONObject;

public class MMR implements Streamable {
	
	/**
	 * Maximum number of rows in this set.. 2^160 max.. 
	 * Can be set higher - but takes more memory
	 */
	protected static int MAXROWS = 160;
	
	/**
	 * What Block time does this MMR represent. Each represents 1 block.
	 */
	protected MiniNumber mBlockTime = MiniNumber.ZERO;
	
	/**
	 * The parent MMR..
	 * 
	 * If you don't have it, ask your parent
	 */
	protected MMR mParent = null;
	
	/**
	 * What is the current entry number..
	 */
	protected MiniNumber mEntryNumber = MiniNumber.ZERO;
	
	/**
	 * All the entries in this set 
	 */
	protected Hashtable<String, MMREntry> mSetEntries;
	
	/**
	 * The maximum row used in this Set
	 */
	protected int mMaxRow = 0;
	
	/**
	 * The Max entries per row..
	 */
	protected MMREntry mMaxEntries[];
	
	/**
	 * Has the set been Finalized (Peaks / Root )
	 * No more changes after this.
	 */
	boolean mFinalized;
	MMRData mFinalizedRoot;
	ArrayList<MMREntry> mFinalizedPeaks;
	ArrayList<MMREntry> mFinalizedZeroRow;
	
	//HASH Function bit length.. ALWAYS 512 except when used in chainsha function
	int MMR_HASH_BITS=512;
	
	/**
	 * Main Constructor
	 */
	public MMR() {
		this(null, 512);
	}
	
	public MMR(int zBitLength) {
		this(null, zBitLength);
	}
	
	public MMR(int zBitLength, boolean zUseMMREntryDB) {
		this(null, zBitLength,zUseMMREntryDB);
	}
	
	public MMR(MMR zParent) {
		this(zParent, 512);
	}
	
	public MMR(MMR zParent, int zBitLength) {
		this(zParent,zBitLength,true);
	}
	
	public MMR(MMR zParent, int zBitLength, boolean zUseMMREntryDB) {
		//All the Entries in this set
		mSetEntries       = new Hashtable<>();
		
		//The Maximum Rows and entries
		mMaxEntries = new MMREntry[MAXROWS];
		mMaxRow     = 0;
		
		//Parent MMRSet
		mParent = zParent;
	
		//Not Finalized..
		mFinalized = false;
		
		//What HASH strength - ALL MMR database is 512
		MMR_HASH_BITS = zBitLength;
		
		//Now add the peaks..
		if(mParent != null) {
			if(!mParent.isFinalized()) {
				//Finalize the parent..
				mParent.finalizeSet();
			}
			
			//Set the Time.. 1 more than parent
			setBlockTime(mParent.getBlockTime().add(MiniNumber.ONE));
			
			//Calculate total entries..
			BigInteger tot = BigInteger.ZERO;
			BigInteger two = new BigInteger("2");
			
			ArrayList<MMREntry> peaks = mParent.getMMRPeaks();
			for(MMREntry peak : peaks) {
				//Add the peak
				setEntry(peak.getRow(), peak.getEntryNumber(), peak.getData());
			
				//Add to the total entries.. the peaks are the binary value
				tot = tot.add(two.pow(peak.getRow()));
			}
			
			//Set the Entry Number
			mEntryNumber = new MiniNumber(tot);
			
			//Check!
			if(!mEntryNumber.isEqual(mParent.getEntryNumber())) {
				MinimaLogger.log("SERIOUS ERROR - Entry Number Mismatch! "+mEntryNumber+"/"+mParent.mEntryNumber);
			}
		}
	}
	
	public JSONObject toJSON() {
		JSONObject ret = new JSONObject();
		
		ret.put("block", mBlockTime);
		ret.put("entrynumber", mEntryNumber);

		JSONArray jentry = new JSONArray();
		Enumeration<MMREntry> entries = mSetEntries.elements();
		while(entries.hasMoreElements()) {
			MMREntry entry = entries.nextElement();
			jentry.add(entry.toJSON());
		}
		ret.put("entries", jentry);
		ret.put("maxrow", mMaxRow);
		
		JSONArray maxentry = new JSONArray();
		for(MMREntry entry : mMaxEntries) {
			if(entry != null) {
				maxentry.add(entry.getRow()+":"+entry.getEntryNumber().toString());
			}
		}
		ret.put("maxentries", maxentry);
		
		return ret;
	}
	
	public void setParent(MMR zParent) {
		mParent = zParent;
	}
	
//	//Switch to the shared MMREntry DB
//	private void fixEntryDuplicates() {
//		//Do we use the duplicate DB
//		if(!mUseMMREntryDB) {
//			return;
//		}
//		
//		//Get the Duplicate Copy DB
//		MMREntryDB db = MMREntryDB.getDB();
//		
//		//Create Copies..
//		Hashtable<String, MMREntry> setEntries       = new Hashtable<>();
//		Hashtable<String, MMREntry> setEntriesCoinID = new Hashtable<>();
//		MMREntry maxEntries[] 						 = new MMREntry[MAXROWS];
//		
//		//Now cycle through and use the shared MMRENtry
//		Enumeration<MMREntry> entries = mSetEntries.elements();
//		while(entries.hasMoreElements()) {
//			//The Original
//			MMREntry entry = entries.nextElement();
//		
//			//Get Shared Version..
//			MMREntry sharedentry = db.getEntry(entry, mBlockTime);
//			
//			//Add it..
//			String name = getHashTableEntry(entry.getRow(), entry.getEntryNumber());
//			setEntries.put(name, sharedentry);
//		}
//		
//		Enumeration<MMREntry> entriescoinid = mSetEntriesCoinID.elements();
//		while(entriescoinid.hasMoreElements()) {
//			//The Original
//			MMREntry entry = entriescoinid.nextElement();
//		
//			//Get Shared Version..
//			MMREntry sharedentry = db.getEntry(entry, mBlockTime);
//			
//			//Add it..
//			String name = entry.getData().getCoin().getCoinID().to0xString();
//			setEntriesCoinID.put(name, sharedentry);
//		}
//		
//		for(int i=0;i<MAXROWS;i++) {
//			if(mMaxEntries[i] != null) {
//				//Get Shared Version..
//				MMREntry sharedentry = db.getEntry(mMaxEntries[i], mBlockTime);
//				
//				//Add it..
//				maxEntries[i] = sharedentry;
//			}
//		}
//		
//		//And now set these as the actual values
//		mSetEntries 		= setEntries;
//		mSetEntriesCoinID 	= setEntriesCoinID;
//		mMaxEntries	 		= maxEntries;
//	}
	
	public void finalizeSet() {
		//Fix Duplicates..
//		fixEntryDuplicates();
		
		//Reset
		mFinalized = false;
				
		//The peaks..
		mFinalizedPeaks = getMMRPeaks();
		
		//Create the final values..
		mFinalizedRoot = getMMRRoot();
		
		//get the zero row
		mFinalizedZeroRow = getRow(0);
		
		//We are now Finalized..
		mFinalized = true;
	}
	
	public boolean isFinalized() {
		return mFinalized;
	}
	
	private void setBlockTime(MiniNumber zTime) {
		mBlockTime = zTime;
	}
	
	public MiniNumber getBlockTime() {
		return mBlockTime;
	}
	
	public MMR getParent() {
		return mParent;
	}
	
	public MiniNumber getEntryNumber() {
		return mEntryNumber;
	}
	
	private void incrementEntryNumber() {
		mEntryNumber = mEntryNumber.increment();
	}
	
	private String getHashTableEntry(int zRow, MiniNumber zEntry) {
		return zRow+":"+zEntry.toString();
	}
	
	//This is NEVER an empty MMREntry
	private void addHashTableEntry(MMREntry zEntry) {
		//Add the entry to the total list HashTable
		String name = getHashTableEntry(zEntry.getRow(), zEntry.getEntryNumber());
		mSetEntries.put(name, zEntry);
		
		//Do we add to the CoinID Table..
		if(zEntry.getRow()==0) {
			if(!zEntry.getData().isHashOnly()) {
				String coinid = zEntry.getData().getCoin().getCoinID().to0xString();
				mSetEntriesCoinID.put(coinid, zEntry);
			}
		}
	}
	
	public ArrayList<MMREntry> getRow(int zRow){
		ArrayList<MMREntry> row = new ArrayList<>();
		
		Enumeration<MMREntry> entries = mSetEntries.elements();
		while(entries.hasMoreElements()) {
			MMREntry entry = entries.nextElement();
			if(entry.getRow() == zRow) {
				row.add(entry);
			}
		}
		
		return row;
	}
	
	public ArrayList<MMREntry> getZeroRow(){
		if(mFinalized) {
			return mFinalizedZeroRow;
		}else {
			return getRow(0);
		}
	}
	
	/**
	 * Search for the first valid unspent Address and Tokenid with AT LEAST Amount coin
	 * @param zCoinID
	 * @return
	 */
	public MMREntry searchAddress(MiniData zAddress, MiniNumber zAmount, MiniData zTokenID) {
		//Loop through all
		MMR current = this;
		
		//Cycle through them..
		while(current != null) {
			//Get the zero row - no parents..
			ArrayList<MMREntry> zero = current.getZeroRow();
			for(MMREntry entry : zero) {
				if(!entry.getData().isHashOnly()) {
					Coin cc = entry.getData().getCoin();
					
					boolean notspent  = !entry.getData().isSpent();
					boolean addr      = cc.getAddress().isEqual(zAddress);
					boolean amount    = cc.getAmount().isMoreEqual(zAmount);
					boolean tok       = cc.getTokenID().isEqual(zTokenID);
					
					if(addr && amount && tok && notspent){
						return entry;
					}
				}
			}
			
			//Search the parent..
			current = current.getParent();
		}
		
		return null;
	}
	
	/**
	 * Find an entry
	 * @param zCoinID
	 * @return
	 */
	public MMREntry findEntry(MiniData zCoinID) {
		//Loop through all
		MMR current = this;
		
		//Cycle through them..
		String coinid = zCoinID.to0xString();
		while(current != null) {
			MMREntry entry = mSetEntriesCoinID.get(coinid);
			if(entry != null) {
				return entry;
			}
			
			//Search the parent..
			current = current.getParent();
		}
		
		return null;
	}
	
	/**
	 * Sets the Entry value in THIS SET ONLY. Does not affect parents.
	 * @param zRow
	 * @param zEntry
	 * @param zData
	 * @return
	 */
	protected MMREntry setEntry(int zRow, MiniNumber zEntry, MMRData zData) {
		//Store the Maximum
		if(zRow>mMaxRow) {
			mMaxRow = zRow;
		}
		
		//Check if already added..
		String entryname = getHashTableEntry(zRow, zEntry);
		MMREntry entry   = mSetEntries.get(entryname);
		
		//Create and add if not found
		if(entry == null) {
			entry = new MMREntry(zRow, zEntry);
			entry.setBlockTime(getBlockTime());
			entry.setData(zData);
			
			//Add it to the hastables
			addHashTableEntry(entry);
		}else {
			//Set the correct data
			entry.setData(zData);
		}
		
		//Is it a MAX
		if(mMaxEntries[zRow] == null) {
			mMaxEntries[zRow] = entry;
		}else if(mMaxEntries[zRow].getEntryNumber().isLess(zEntry)) {
			mMaxEntries[zRow] = entry;
		}
		
		//Return
		return entry;
	}
	
	protected MMREntry getEntry(int zRow, MiniNumber zEntry) {
		return getEntry(zRow, zEntry, MiniNumber.ZERO);
	}
	
	protected MMREntry getEntry(int zRow, MiniNumber zEntry, MiniNumber zMaxBack) {
		//Cycle down through the MMR sets..
		MMR current = this;
		
		//Now Loop..
		String entryname = getHashTableEntry(zRow, zEntry);
		while(current != null) {
			//Check within the designated range
			if(current.getBlockTime().isLess(zMaxBack)) {
				break;
			}
			
			//Check if already added..
			MMREntry entry   = current.mSetEntries.get(entryname);
			if(entry!=null) {
				return entry;
			}
			
			//Check the parent Set
			current = current.getParent();	
		}
		
		//If you can't find it - return empty entry..
		MMREntry entry = new MMREntry(zRow, zEntry);
		entry.setBlockTime(getBlockTime());
		
		return entry;
	}
	
	/**
	 * Add data - an UNSPENT coin
	 */
	public MMREntry addUnspentCoin(MMRData zData) {
		//Create a new entry
		MMREntry entry = setEntry(0, mEntryNumber, zData);
		MMREntry ret   = entry;
		
		//1 more entry
		incrementEntryNumber();
		
		//Now go up the tree..
		while(entry.isRight()) {
			//Get the Sibling.. will be the left
			MMREntry sibling = getEntry(entry.getRow(), entry.getSibling());
						
			//The New MMRData
			MMRData data = getParentMMRData(sibling, entry);
			
			//Set the Parent Entry
			entry = setEntry(entry.getParentRow(),entry.getParentEntry(),data);
		}
		
		return ret;
	}
	
	/**
	 * Utility function when creating an MMRtree based on simple Hash values and not coins.. 
	 */
	public MMREntry addLeafNode(MiniData zData){
		return addUnspentCoin(new MMRData(zData, MiniNumber.ZERO));
	}
	
	/**
	 * Add data - an UNSPENT coin - Must be added to the correct mmrset
	 */
	public MMREntry addExternalUnspentCoin(MMRProof zProof) {
		//The Details
		MiniNumber entrynum = zProof.getEntryNumber();
		MMRData proofdata    = zProof.getMMRData();
		
		//Do we already have this Entry..
		MMREntry entry = getEntry(0, entrynum);
		if(!entry.isEmpty() && !entry.getData().isHashOnly()) {
			//Make sure its a keeper
			addKeeper(entrynum);
			
			//We have it..
			return entry;
		}
		
		//Create a new entry
		entry = setEntry(0, entrynum, proofdata);
		MMREntry ret = entry;
		
		//Now go up the tree..
		int prooflen = zProof.getProofLen();
		int proofnum = 0;
		while(proofnum < prooflen) {
			MMREntry sibling = getEntry(entry.getRow(), entry.getSibling());
			
			//Do we add our own..
			ProofChunk chunk = zProof.getProofChunk(proofnum++);
			MMRData pdata = new MMRData(chunk.getHash(), chunk.getValue());
			if(sibling.isEmpty()) {
				//Set the data
				sibling = setEntry(sibling.getRow(), sibling.getEntryNumber(), pdata);
				
			}else {
				//Check the value is what we expect it to be
				if(!sibling.getData().getFinalHash().isEqual(pdata.getFinalHash())) {
					//Hmm..
					MinimaLogger.log("Sibling Inconsistency!! in MMR @ "+entrynum+" when hard adding proof");
					
					return null;
				}else {
					//We have all this allready!
					break;
				}
			}
			
			//Create the new combined value..
			MMRData data = null;
			if(entry.isLeft()) {
				data = getParentMMRData(entry, sibling);
			}else {
				data = getParentMMRData(sibling, entry);
			}
						
			//Check if we have it..
			MMREntry parent = getEntry(entry.getParentRow(),entry.getParentEntry());  
			if(!parent.isEmpty()) {
				if(!parent.getData().getFinalHash().isEqual(data.getFinalHash())) {
					//Hmm..
					MinimaLogger.log("Parent Inconsistency!! in MMR @ "+entrynum+" when hard adding proof");
					
					return null;
				}else {
					//We have this..!
					break;
				}
			}
			
			//Set the Parent Entry
			entry = setEntry(entry.getParentRow(),entry.getParentEntry(),data);
		}
		
		//Its a keeper..
		addKeeper(entrynum);
		
		return ret;
	}
	
	/**
	 * Set entry to SPENT
	 * 
	 * @param zProof
	 * @return
	 */
	public MMREntry updateSpentCoin(MMRProof zProof) {
		//The original MMRData..
		MMRData original = zProof.getMMRData();
		
		//The NEW spent MMRData..
		MMRData spentmmr = new MMRData(MiniByte.TRUE, 
										original.getCoin(),
										original.getInBlock(),
										original.getPrevState());
		
		//Get the current peaks..
		ArrayList<MMREntry> peaks=getMMRPeaks();
		
		//Create a new entry
		MMREntry entry = setEntry(0, zProof.getEntryNumber(), spentmmr);
		MMREntry ret   = entry;
		
		//How big is the proof
		int prooflen = zProof.getProofLen();
		
		//Start checking..
		int pcount = 0;
		while(true) {
			//Check..
			for(MMREntry peak : peaks) {
				if(entry.checkPosition(peak)) {
					return ret;
				}
			}
			
			//Get the sibling.. Not yet at a peak..
			MMREntry sibling = getEntry(entry.getRow(), entry.getSibling());
			
			//Is it empty - or do we use the proof value
			if(sibling.isEmpty()) {
				//The current proof..
				ProofChunk chunk = zProof.getProofChunk(pcount);
				MiniData phash   = chunk.getHash();
				MiniNumber pval  = chunk.getValue();
				
				//Set the Sibling data to the current proof
				sibling.setData(new MMRData(phash,pval));
			}else{
				//Is it older than the proof.. if newer leave it.. or if proof finished..
				if(sibling.getBlockTime().isLess(zProof.getBlockTime()) && pcount<prooflen) {
					//The current proof..
					ProofChunk chunk = zProof.getProofChunk(pcount);
					MiniData phash   = chunk.getHash();
					MiniNumber pval  = chunk.getValue();
					
					if(!sibling.getHashValue().isEqual(phash)) {
						//The proof is newer and different - set it..
						sibling.setData(new MMRData(phash,pval));
					}
				}
			}
			
			//Set the Sibling in this MMRSET!.. Could be a FULL non hash only.. this way the MMR peaks still work.. (as the max in a row MUST be on the left to be a peak ))
			sibling = setEntry(sibling.getRow(), sibling.getEntryNumber(), sibling.getData());
			
			//increase the count..
			pcount++;
			
			//Now calculate the parent
			MMRData parentdata = null;
			if(entry.isLeft()) {
				parentdata = getParentMMRData(entry, sibling);
			}else {
				parentdata = getParentMMRData(sibling, entry);
			}
			
			//Set the Parent
			entry = setEntry(entry.getParentRow(), entry.getParentEntry(), parentdata);
		}
	}
	
	/**
	 * Get An MMR Proof
	 */
	protected MMRProof getProofToPeak(MiniNumber zEntryNumber) {
		//First get the initial Entry.. check parents aswell..
		MMREntry entry = getEntry(0, zEntryNumber);
		
		//Now get all the hashes in the tree to a peak..
		MMRProof proof = new MMRProof(zEntryNumber, entry.getData(), mBlockTime);
		proof.setHashBitLength(MMR_HASH_BITS);
		
		//Go up to the MMR Peak..
		MMREntry sibling = getEntry(entry.getRow(), entry.getSibling());
		while(!sibling.isEmpty()) {
			//Add to our Proof..
			proof.addProofChunk(new MiniByte(sibling.isLeft()), sibling.getHashValue(), sibling.getData().getValueSum());	
			
			//Now get the Parent.. just need a reference even if is empty. To find the sibling.
			MMREntry parent = new MMREntry( sibling.getParentRow(), sibling.getParentEntry() );
			
			//And get the Sibling of the Parent..
			sibling = getEntry(parent.getRow(), parent.getSibling());
		}
		
		return proof;
	}
	
	/**
	 * Get Proof to ROOT
	 */
	protected MMRProof getPeakToRoot(MiniData zPeak) {
		//Sum of all the initial proofs...
		MMRProof totalproof = new MMRProof();
		totalproof.setHashBitLength(MMR_HASH_BITS);
		
		//Get the Peaks..
		ArrayList<MMREntry> peaks = getMMRPeaks();
		
		//Now take all those values and put THEM in an MMR..
		MiniData currentpeak    = zPeak;
		MMREntry keeper 		= null;
		while(peaks.size() > 1) {
			//Create a new MMR
			MMR newmmr = new MMR(MMR_HASH_BITS,false);
			
			//Add all the peaks to it..
			for(MMREntry peak : peaks) {
				//Create the new data..
				MMRData data = new MMRData(peak.getHashValue(), peak.getData().getValueSum());
				
				//Add this..
				MMREntry current = newmmr.addUnspentCoin(data);
				
				//Is this the one to follow..
				if(keeper==null && peak.getHashValue().isEqual(currentpeak)) {
					keeper = current;
				}
			}
			
			//Finalise..
			newmmr.finalizeSet();
			
			//MUST have found the desired peak..
			if(keeper == null) {
				MinimaLogger.log("ERROR MMR NO Peak to ROOT found..");
				return null;
			}
			
			//Now get the keeper proof..
			MMRProof proof = newmmr.getProofToPeak(keeper.getEntryNumber());
			
			//Now add that to the total proof..
			int len = proof.getProofLen();
			for(int i=0;i<len;i++) {
				totalproof.addProofChunk(proof.getProofChunk(i));
			}
			
			//Now get the peaks.. repeat..
			peaks = newmmr.getMMRPeaks();
			
			//What to follow..
			proof.setData(keeper.getHashValue(), keeper.getData().getValueSum());
			currentpeak = proof.getFinalHash();
			keeper      = null;
		}
		
		return totalproof;
	}
	
	/**
	 * Get the full proof to the root of the MMR
	 * 
	 * @param zEntry
	 * @return
	 */
	public MMRProof getProof(MiniNumber zEntry) {
		//Get the Basic Proof..
		MMRProof proof = getProofToPeak(zEntry);
		
		//Now get the peak this points to..
		MiniData peak = proof.getFinalHash();
		
		//Now find the path to root for this peak
		MMRProof rootproof = getPeakToRoot(peak);
		
		//Now add the two..
		int len = rootproof.getProofLen();
		for(int i=0;i<len;i++) {
			ProofChunk chunk = rootproof.getProofChunk(i);
			proof.addProofChunk(chunk.getLeft(), chunk.getHash(), chunk.getValue());
		}
		
		return proof;
	}
	
	/**
	 * Check this is a valid UNSPENT output.
	 * 
	 * The Proof can point to a previous block. But must 
	 * be within the range that everyone store.. 
	 * 
	 * @return
	 */
	public boolean checkProof(MMRProof zProof) {
		//MUST have data to be checked
		if(zProof.getMMRData().isHashOnly()) {
			MinimaLogger.log("Invalid PROOF check HASHONLY! : "+zProof);
			return false;
		}
		
		//Check is not spent.. 
		if(zProof.getMMRData().isSpent()) {
			MinimaLogger.log("Invalid PROOF is SPENT! : "+zProof);
			return false;
		}
		
		//Get the MMRSet at the time this proof was made.. must be a recent proof..
		MMR proofset = getParentAtTime(zProof.getBlockTime());
		
		//The proof is it too old.. we can't check it. It's invalid.
		if(proofset == null) {
			MinimaLogger.log("ERROR Proof too Old "+zProof);
			return false;
		}
		
		//Check the root..
		MMRData root = proofset.getMMRRoot();
		
		//Check the merkle proof
		if(!zProof.getFinalHash().isEqual(root.getFinalHash())) {
			MinimaLogger.log("ERROR Proof does not match root "+zProof);
			return false;
		}
		
		//So the proof was valid at that time.. if it has been SPENT, it will have been AFTER this block - and in our MMR
		MMREntry entry = getEntry(0, zProof.getEntryNumber(), zProof.getBlockTime().increment());
		
		//Is it there ?
		if(!entry.isEmpty()) {
			if(!entry.getHashValue().isEqual(zProof.getMMRData().getFinalHash())) {
				MinimaLogger.log("ERROR Proof Coin value changed since proof created "+zProof);
				return false;
			}
		}
		
		//It was valid at the parent.. there is NO SPEND since.. so it's Valid!
		return true;
	}
	
	/**
	 * Get the MMR peaks of this Set
	 * @return
	 */
	public ArrayList<MMREntry> getMMRPeaks(){
		//Are we final 
		if(mFinalized) {
			return mFinalizedPeaks;
		}
		
		//Create from scratch
		ArrayList<MMREntry> peaks = new ArrayList<>();
		for(int i=mMaxRow;i>=0;i--) {
			//Get the MAX entry for the row..
			MMREntry max = mMaxEntries[i];
				
			//Is there an Entry..
			if(max != null) {
				//Is it a peak ? - ALL peaks are LEFT siblings..
				if(max.isLeft()) {
					peaks.add(max);
				}
			}
		}
			
		return peaks;
	}
	
	/**
	 * Get the ROOT of the Whole MMRSet
	 * 
	 * @return
	 */
	public MMRData getMMRRoot() {
		//Are we final
		if(mFinalized) {
			return mFinalizedRoot;
		}
		
		//Get the Peaks..
		ArrayList<MMREntry> peaks = getMMRPeaks();
		
		//Now take all those values and put THEM in an MMR..
		while(peaks.size() > 1) {
		
			//Create a new MMR
			MMR newmmr = new MMR(MMR_HASH_BITS,false);
			
			//Add all the peaks to it..
			for(MMREntry peak : peaks) {
				newmmr.addUnspentCoin(new MMRData(peak.getHashValue(), peak.getData().getValueSum()));	
			}
			
			//Now get the peaks.. repeat..
			peaks = newmmr.getMMRPeaks();
		}
		
		return peaks.get(0).getData();
	}
	
	/**
	 * Add a Keeper - once only..
	 * @param zEntry
	 */
	public boolean addKeeper(MiniNumber zEntry) {
		if(!isKeptAllready(zEntry)) {
			mKeepers.add(zEntry);
			return true;
		}
		
		return false;
	}
	
	/**
	 * Get the Keeper
	 */
	public ArrayList<MiniNumber> getKeepers() {
		return mKeepers;
	}
	
	/**
	 * Do we already keep this entry..
	 * 
	 * @param zNumber
	 * @return
	 */
	public boolean isKeptAllready(MiniNumber zNumber) {
		for(MiniNumber keep : mKeepers) {
			if(keep.isEqual(zNumber)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Used when Pruning the MMR tree..
	 * 
	 * All the Keepers are moved Up one level..
	 */
	public void copyParentKeepers() {
		//First get the Keepers..
		ArrayList<MiniNumber> parentkeepers = new ArrayList<>();
		if(mParent!=null) {
			parentkeepers = mParent.getKeepers();
		}
		
		//Cycle through the current crop..
		ArrayList<MiniNumber> newkeepers = new ArrayList<>();
		for(MiniNumber keep : mKeepers) {
			//Get that LATEST entry and all the entries it uses on the way up..
			MMREntry entry = getEntry(0, keep);
			if(!entry.getData().isSpent()) {
				newkeepers.add(keep);
			}
		}
		
		//Reset
		mKeepers = newkeepers;
		
		//Cycle through the Keepers..
		for(MiniNumber keep : parentkeepers) {
			//Get that LATEST entry and all the entries it uses on the way up..
			MMREntry entry = getEntry(0, keep);
			
			//Check valid.. SHOULD NOT HAPPEN
			if(entry.isEmpty() || entry.getData().isHashOnly()) {
				MinimaLogger.log("copyKeepers on NULL Keeper Entry! "+keep);
				continue;
			}
			
			//If it's spent we don't keep it..
			if(entry.getData().isSpent()) {
				continue;
			}
			
			//Keep it..
			boolean added = addKeeper(keep);
			
			//Has it already been added..
//			if(added) {
				//Add it.. to THIS set.. not the parent..
				entry = setEntry(0, keep, entry.getData());
				
				//And now go go up the tree..
				MMREntry sibling = getEntry(entry.getRow(), entry.getSibling());
				while(!sibling.isEmpty()) {
					//Add to our Set..
					setEntry(sibling.getRow(), sibling.getEntryNumber(), sibling.getData());
					
					//Now get the Parent.. just need a reference even if is empty. To find the sibling.
					MMREntry parent = new MMREntry( sibling.getParentRow(), sibling.getParentEntry() );
					
					//And get the Sibling of the Parent..
					sibling = getEntry(parent.getRow(), parent.getSibling());
				}
//			}
		}
		
		//Now we have all the data stored for the keeper coins.. We can remove the parent..		
		mParent = null;
		
		//Re-finalise..
		finalizeSet();
	}

	/**
	 * Recursively copy the parents..
	 * 
	 * Returns the minimum block copied..
	 */
	public MiniNumber copyAllParentKeepers(MiniNumber zCascade) {
		//Start at this point..
		MMR curr = this;
		
		//Minimum block copied
		MiniNumber minblock = zCascade;
		
		//Store all the pparents..
		ObjectStack stack = new ObjectStack();
		while(curr.getBlockTime().isMore(zCascade)) {
			//Add to the stack..
			stack.push(curr);
			
			//Get the parent..
			curr = curr.getParent();
		}
		
		//Now run through the stack..
		while(!stack.isEmpty()) {
			//Get the parent MMR..
			MMR mmr = (MMR) stack.pop();
			
			//Store it..
			if(mmr.getParent() != null) {
				MiniNumber pblock = mmr.getParent().getBlockTime();
				if(minblock == null) {
					minblock = pblock;
				}else if(pblock.isLess(minblock)) {
					minblock = pblock;
				}
			}
			
			//Copy the parents MMR keepers..
			mmr.copyParentKeepers();
		}
		
		//Return minimum block..
		return minblock;
	}
	
	/**
	 * Get a Parent block at a certain time..
	 */
	public MMR getParentAtTime(MiniNumber zTime) {
		MMR current = this;
		
		while(current != null) {
			if(current.getBlockTime().isEqual(zTime)) {
				return current;
			}
			
			//Too far.. only goes back in time further..
			if(current.getBlockTime().isLess(zTime)) {
				return null;
			}
			
			current = current.getParent();
		}

		return null;
	}
	
	/**
	 * Return the Parent of 2 sibling children
	 * @param zLeftChild
	 * @param zRightChild
	 * @return
	 */
	private MMRData getParentMMRData(MMREntry zLeftChild, MMREntry zRightChild) {
		//Combine the Values..
		MiniNumber sumvalue   = zLeftChild.getData().getValueSum().add(zRightChild.getData().getValueSum());
		
		//Make the unique MMRData Hash
//		MiniData combined = Crypto.getInstance().hashAllObjects( MMR_HASH_BITS,
//				zLeftChild.getHashValue(),zRightChild.getHashValue(),sumvalue);
		MiniData combined = Crypto.getInstance().hashAllObjects( MMR_HASH_BITS,
				zLeftChild.getHashValue(),zRightChild.getHashValue(),sumvalue);
		
		//Create a new data proof
		return new MMRData(combined,sumvalue);
	}
	
	/**
	 * Write out this MMR set
	 * 
	 * When a user syncs to the network a section of these will bootstrap the MMR DB.
	 */
	@Override
	public void writeDataStream(DataOutputStream zOut) throws IOException {
		//Write the Block Time.
		mBlockTime.writeDataStream(zOut);
		
		//EntryNumber..
		mEntryNumber.writeDataStream(zOut);
		
		//How many..
		int len = mSetEntries.size();
		zOut.writeInt(len);
		
		//Now write out each row..
		Enumeration<MMREntry> entries = mSetEntries.elements();
		while(entries.hasMoreElements()) {
			MMREntry entry = entries.nextElement();
			entry.writeDataStream(zOut);
		}
	}

	@Override
	public void readDataStream(DataInputStream zIn) throws IOException {
		mBlockTime   = MiniNumber.ReadFromStream(zIn);
		mEntryNumber = MiniNumber.ReadFromStream(zIn);
		
		//Now the Entries..
		mSetEntries       = new Hashtable<>();
		mSetEntriesCoinID = new Hashtable<>();
		mMaxEntries       = new MMREntry[MAXROWS];
		mMaxRow = 0;
		
		int len = zIn.readInt();
		for(int i=0;i<len;i++) {
			MMREntry entry = new MMREntry(0, null);
			entry.readDataStream(zIn);
			entry.setBlockTime(mBlockTime);
			
			if(!entry.isEmpty()) {
				//Now do the max..
				int row = entry.getRow();
				if(row > mMaxRow) {
					mMaxRow = row;	
				}
				
				if(mMaxEntries[row] == null) {
					mMaxEntries[row] = entry;
				}else if(mMaxEntries[row].getEntryNumber().isLess(entry.getEntryNumber())) {
					mMaxEntries[row] = entry;
				}
				
				//And add..
				addHashTableEntry(entry);
			}
		}
		
		//Finalize..
		finalizeSet();
	}
	
public static void main(String[] zARgs) {
		
		System.out.println("Start Tests");

//		//New set for each test
//		MMRSet mmrset = new MMRSet(160, false);
//		int num = 6;
//		
//		for(int i=0;i<num;i++) {
//			mmrset.addLeafNode(new MiniData("0xFF"));
//		}
//		
//		for(int i=0;i<num;i++) {
//			MMREntry peak = mmrset.getProofPeak(new MiniNumber(i));
//			System.out.println(i+" "+peak);
//		}
		
		
		//RUN THROUGH MANY SIZES
		for(int testsize=1;testsize<16;testsize++) {
			
//			int testsize = 8;
			
			//New set for each test
			MMR testset = new MMR(160, false);
			
			Random rr = new Random();
			//Add the leaf nodes
			for(int nodes=0;nodes<testsize;nodes++) {
				testset.addUnspentCoin(new MMRData(new MiniData("0xFF"), new MiniNumber(rr.nextInt())));
//				testset.addUnspentCoin(new MMRData(new MiniData("0xFF"), new MiniNumber(0)));
//				testset.addLeafNode();
			}
			
			//Get the root of the tree
			MiniData root = testset.getMMRRoot().getFinalHash();
			System.out.println("ROOT @ Testsize:"+testsize+" peaks:"+testset.getMMRPeaks().size()+" "+testset.getMMRRoot().getValueSum()+" "+root);
			
			//Now check all the proofs point to that
			for(int nodes=0;nodes<testsize;nodes++) {
//				int nodes=6;
//				System.out.println("Proof : "+nodes);
				
				MMRProof proof = testset.getProof(new MiniNumber(nodes));
//				System.out.println(proof.toJSON());
				
				MiniData hash  = proof.getFinalHash();
				if(!hash.equals(root)) {
					System.out.println("ERROR @ Testsize:"+testsize+" node:"+nodes);
				}
				
			}
			
		}
		System.out.println("Finished Tests");
		
	}
}
