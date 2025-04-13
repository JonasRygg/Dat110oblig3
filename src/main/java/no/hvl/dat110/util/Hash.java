package no.hvl.dat110.util;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Hash {

	public static BigInteger hashOf(String entity) {
		try {
			// Use MD5 algorithm for hashing
			MessageDigest md = MessageDigest.getInstance("MD5");
			byte[] digest = md.digest(entity.getBytes());

			// Convert hash into hexadecimal format
			String hex = toHex(digest);

			// Convert hexadecimal string into BigInteger
			return new BigInteger(hex, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new RuntimeException("MD5 Algorithm not found", e);
		}
	}

	public static BigInteger addressSize() {
		// Address size = 2 ^ number of bits
		return BigInteger.valueOf(2).pow(bitSize());
	}

	public static int bitSize() {
		// MD5 produces a 128-bit hash value
		return 128;
	}

	public static String toHex(byte[] digest) {
		StringBuilder strbuilder = new StringBuilder();
		for (byte b : digest) {
			strbuilder.append(String.format("%02x", b & 0xff));
		}
		return strbuilder.toString();
	}
}
