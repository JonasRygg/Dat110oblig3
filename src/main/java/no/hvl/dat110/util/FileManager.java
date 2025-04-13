package no.hvl.dat110.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.middleware.Message;
import no.hvl.dat110.rpc.interfaces.NodeInterface;

public class FileManager {

	private static final Logger logger = LogManager.getLogger(FileManager.class);

	private BigInteger[] replicafiles;
	private int numReplicas;
	private NodeInterface chordnode;
	private String filepath;
	private String filename;
	private BigInteger hash;
	private byte[] bytesOfFile;
	private String sizeOfByte;

	private Set<Message> activeNodesforFile = null;

	public FileManager(NodeInterface chordnode) throws RemoteException {
		this.chordnode = chordnode;
	}

	public FileManager(NodeInterface chordnode, int N) throws RemoteException {
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}

	public FileManager(NodeInterface chordnode, String filepath, int N) throws RemoteException {
		this.filepath = filepath;
		this.numReplicas = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}

	public void createReplicaFiles() {
		for (int i = 0; i < numReplicas; i++) {
			String replica = filename + i;
			replicafiles[i] = Hash.hashOf(replica);
		}
	}

	public int distributeReplicastoPeers() throws RemoteException {

		Random rnd = new Random();
		int index = rnd.nextInt(Util.numReplicas);  // now includes last index

		int counter = 0;
		createReplicaFiles();

		for (int i = 0; i < numReplicas; i++) {
			BigInteger replicaID = replicafiles[i];
			NodeInterface successor = chordnode.findSuccessor(replicaID);

			if (successor != null) {
				successor.addKey(replicaID);
				boolean isPrimary = (i == index);
				successor.saveFileContent(filename, replicaID, bytesOfFile, isPrimary);
				counter++;
			}
		}
		return counter;
	}

	public Set<Message> requestActiveNodesForFile(String filename) throws RemoteException {
		this.filename = filename;
		activeNodesforFile = new HashSet<>();
		createReplicaFiles();

		for (BigInteger replicaID : replicafiles) {
			NodeInterface successor = chordnode.findSuccessor(replicaID);
			if (successor != null) {
				Message metadata = successor.getFilesMetadata().get(replicaID);
				if (metadata != null) {
					activeNodesforFile.add(metadata);
				}
			}
		}
		return activeNodesforFile;
	}

	public NodeInterface findPrimaryOfItem() {
		for (Message msg : activeNodesforFile) {
			if (msg.isPrimaryServer()) {
				return Util.getProcessStub(msg.getNodeName(), msg.getPort());
			}
		}
		return null;
	}

	public void readFile() throws IOException, NoSuchAlgorithmException {
		File f = new File(filepath);
		byte[] bytesOfFile = new byte[(int) f.length()];
		FileInputStream fis = new FileInputStream(f);
		fis.read(bytesOfFile);
		fis.close();

		filename = f.getName().replace(".txt", "");
		hash = Hash.hashOf(filename);
		this.bytesOfFile = bytesOfFile;

		double size = (double) bytesOfFile.length / 1000;
		NumberFormat nf = new DecimalFormat();
		nf.setMaximumFractionDigits(3);
		sizeOfByte = nf.format(size);

		logger.info("filename=" + filename + " size=" + sizeOfByte);
	}

	public void printActivePeers() {
		activeNodesforFile.forEach(m -> {
			String peer = m.getNodeName();
			String id = m.getNodeID().toString();
			String name = m.getNameOfFile();
			String hash = m.getHashOfFile().toString();
			int size = m.getBytesOfFile().length;

			logger.info(peer + ": ID = " + id + " | filename = " + name + " | HashOfFile = " + hash + " | size =" + size);
		});
	}

	public int getNumReplicas() {
		return numReplicas;
	}

	public String getFilename() {
		return filename;
	}

	public void setFilename(String filename) {
		this.filename = filename;
	}

	public BigInteger getHash() {
		return hash;
	}

	public void setHash(BigInteger hash) {
		this.hash = hash;
	}

	public byte[] getBytesOfFile() {
		return bytesOfFile;
	}

	public void setBytesOfFile(byte[] bytesOfFile) {
		this.bytesOfFile = bytesOfFile;
	}

	public String getSizeOfByte() {
		return sizeOfByte;
	}

	public void setSizeOfByte(String sizeOfByte) {
		this.sizeOfByte = sizeOfByte;
	}

	public NodeInterface getChordnode() {
		return chordnode;
	}

	public Set<Message> getActiveNodesforFile() {
		return activeNodesforFile;
	}

	public BigInteger[] getReplicafiles() {
		return replicafiles;
	}

	public void setFilepath(String filepath) {
		this.filepath = filepath;
	}
}
