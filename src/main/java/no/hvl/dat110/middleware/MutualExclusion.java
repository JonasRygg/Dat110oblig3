package no.hvl.dat110.middleware;

import java.rmi.RemoteException;
import java.util.*;


import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import no.hvl.dat110.rpc.interfaces.NodeInterface;
import no.hvl.dat110.util.LamportClock;
import no.hvl.dat110.util.Util;

public class MutualExclusion {

	private static final Logger logger = LogManager.getLogger(MutualExclusion.class);

	private boolean CS_BUSY = false;
	private boolean WANTS_TO_ENTER_CS = false;
	private List<Message> queueack;
	private List<Message> mutexqueue;

	private LamportClock clock;
	private Node node;

	public MutualExclusion(Node node) throws RemoteException {
		this.node = node;
		clock = new LamportClock();
		queueack = new ArrayList<>();
		mutexqueue = new ArrayList<>();
	}

	public synchronized void acquireLock() {
		CS_BUSY = true;
	}

	public void releaseLocks() {
		WANTS_TO_ENTER_CS = false;
		CS_BUSY = false;

		// Grant access to queued messages
		for (Message msg : mutexqueue) {
			try {
				NodeInterface stub = Util.getProcessStub(msg.getNodeName(), msg.getPort());
				msg.setAcknowledged(true);
				stub.onMutexAcknowledgementReceived(msg);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		mutexqueue.clear();
	}

	public boolean doMutexRequest(Message message, byte[] updates) throws RemoteException {
		logger.info(node.getNodeName() + " wants to access CS");

		LamportClock localClock = new LamportClock();
		localClock.increment();
		message.setClock(localClock.getClock());
		message.setNodeID(node.getNodeID());

		WANTS_TO_ENTER_CS = true;

		List<Message> queueack = new ArrayList<>();
		List<Message> mutexqueue = new ArrayList<>();

		List<Message> activenodes = removeDuplicatePeersBeforeVoting();

		if (activenodes.stream().noneMatch(m -> m.getNodeName().equals(node.getNodeName()))) {
			Message self = new Message(node.getNodeID(), node.getNodeName(), node.getPort());
			activenodes.add(self);
		}

		multicastMessage(message, activenodes, queueack, mutexqueue);

		boolean permission = false;
		int waited = 0, maxWait = 7000, interval = 200;

		while (waited < maxWait) {
			if (queueack.size() == activenodes.size()) {
				permission = true;
				break;
			}
			try {
				Thread.sleep(interval);
				waited += interval;
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		if (permission) {
			acquireLock();
			node.broadcastUpdatetoPeers(updates);
			multicastReleaseLocks(new HashSet<>(activenodes));
		}

		return permission;
	}


	private void multicastMessage(Message message, List<Message> activenodes,
								  List<Message> queueack, List<Message> mutexqueue) throws RemoteException {

		logger.info("Multicasting to " + activenodes.size() + " peers");

		for (Message peer : activenodes) {
			try {
				NodeInterface stub = Util.getProcessStub(peer.getNodeName(), peer.getPort());
				if (stub != null) {
					// Create a new message with the same Lamport clock
					Message msg = new Message(
							node.getNodeID(),
							node.getNodeName(),
							node.getPort()
					);
					msg.setClock(message.getClock());  // Set clock for comparison
					msg.setNodeName(node.getNodeName());
					msg.setPort(node.getPort());

					// Call the remote method
					Message reply = stub.onMutexRequestReceived(msg);

					// Check if access is granted
					if (reply != null && reply.isAcknowledged()) {
						queueack.add(reply);  // Acknowledged = YES vote
					} else {
						mutexqueue.add(reply);  // Denied = NO vote (still store for handling)
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}




	public synchronized boolean onMutexRequestReceived(Message message) throws RemoteException {
		clock.increment();

		int senderClock = message.getClock();
		int localClock = clock.getClock();

		boolean allowAccess = false;

		if (!CS_BUSY && !WANTS_TO_ENTER_CS) {
			allowAccess = true;
		} else if (CS_BUSY) {
			allowAccess = false;
			mutexqueue.add(message);
		} else if (WANTS_TO_ENTER_CS) {
			if (senderClock < localClock ||
					(senderClock == localClock && message.getNodeID().compareTo(node.getNodeID()) < 0)) {
				allowAccess = true;
			} else {
				allowAccess = false;
				mutexqueue.add(message);
			}
		}

		return allowAccess;
	}



	public void doDecisionAlgorithm(Message message, List<Message> queue, int condition) throws RemoteException {

		String procName = message.getNodeName();
		int port = message.getPort();

		switch (condition) {
			case 0: {
				try {
					NodeInterface stub = Util.getProcessStub(procName, port);
					message.setAcknowledged(true);
					stub.onMutexAcknowledgementReceived(message);
				} catch (Exception e) {
					e.printStackTrace();
				}
				break;
			}

			case 1: {
				queue.add(message);
				break;
			}

			case 2: {
				int senderClock = message.getClock();
				int ownClock = clock.getClock();

				boolean senderWins = false;

				if (senderClock < ownClock) {
					senderWins = true;
				} else if (senderClock == ownClock) {
					senderWins = message.getNodeID().compareTo(node.getNodeID()) < 0;
				}

				if (senderWins) {
					try {
						NodeInterface stub = Util.getProcessStub(procName, port);
						message.setAcknowledged(true);
						stub.onMutexAcknowledgementReceived(message);
					} catch (Exception e) {
						e.printStackTrace();
					}
				} else {
					queue.add(message);
				}
				break;
			}
		}
	}

	public void onMutexAcknowledgementReceived(Message message) throws RemoteException {
		logger.info(node.getNodeName() + " received ACK from " + message.getNodeName());

		synchronized (queueack) {
			boolean exists = queueack.stream()
					.anyMatch(m -> m.getNodeName().equals(message.getNodeName()));
			if (!exists) {
				queueack.add(message);
			}
		}
	}

	public void multicastReleaseLocks(Set<Message> activenodes) {
		logger.info("Releasing locks to " + activenodes.size() + " peers");

		for (Message msg : activenodes) {
			try {
				NodeInterface stub = Util.getProcessStub(msg.getNodeName(), msg.getPort());
				if (stub != null) {
					stub.releaseLocks();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private boolean areAllMessagesReturned(int numvoters) throws RemoteException {
		logger.info(node.getNodeName() + ": queueack = " + queueack.size() + "/" + numvoters);
		return queueack.size() == numvoters;
	}

	private List<Message> removeDuplicatePeersBeforeVoting() {
		List<Message> uniquepeer = new ArrayList<>();
		for (Message p : node.activenodesforfile) {
			boolean found = false;
			for (Message p1 : uniquepeer) {
				if (p.getNodeName().equals(p1.getNodeName())) {
					found = true;
					break;
				}
			}
			if (!found)
				uniquepeer.add(p);
		}
		return uniquepeer;
	}
}
