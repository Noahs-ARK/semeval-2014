package edu.cmu.cs.ark.semeval2014.lr;

import java.lang.management.ManagementFactory;
import java.util.Collection;
import java.util.Iterator;

public class MiscUtil {
	/** http://stackoverflow.com/questions/35842/how-can-a-java-program-get-its-own-process-id */
	public static String getProcessId(final String fallback) {
	    // Note: may fail in some JVM implementations
	    // therefore fallback has to be provided

	    // something like '<pid>@<hostname>', at least in SUN / Oracle JVMs
	    final String jvmName = ManagementFactory.getRuntimeMXBean().getName();
	    final int index = jvmName.indexOf('@');

	    if (index < 1) {
	        // part before '@' empty (index = 0) / '@' not found (index = -1)
	        return fallback;
	    }

	    try {
	        return Long.toString(Long.parseLong(jvmName.substring(0, index)));
	    } catch (NumberFormatException e) {
	        // ignore
	    }
	    return fallback;
	}

	public static int[] unbox(Collection<Integer> integers) {
		final int[] ints = new int[integers.size()];
		final Iterator<Integer> iterator = integers.iterator();
		for (int i = 0; i < integers.size(); i++) {
			ints[i] = iterator.next();
		}
		return ints;
	}
}
