package com.theprogrammingturkey.comz.util;

import com.theprogrammingturkey.comz.COMZombies;

import java.util.function.Predicate;

public class Util
{
	private static final char[] VALID_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

	public static String genRandId()
	{
		StringBuilder id = new StringBuilder();
		for(int i = 0; i < 8; i++)
			id.append(VALID_CHARS[COMZombies.rand.nextInt(VALID_CHARS.length)]);
		return id.toString();
	}

	/** First id {@code prefix<n>} not taken, starting the search at {@code n = start}. */
	public static String nextFreeId(String prefix, int start, Predicate<String> taken)
	{
		int n = Math.max(1, start);
		while(taken.test(prefix + n))
			n++;
		return prefix + n;
	}
}
