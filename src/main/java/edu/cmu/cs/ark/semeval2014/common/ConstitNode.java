package edu.cmu.cs.ark.semeval2014.common;

public class ConstitNode {
	public ConstitNode parent;
	public ConstitNode children[];
	public String tag;
	public boolean isPOSnode;
	public int startIndex; // INCLUSIVE
	public int lastIndex;  // INCLUSIVE
	
	public int spanLength() {
		return lastIndex - startIndex + 1;
	}
	
	public String toString() {
		if (isPOSnode) {
			return "(" + tag + " _)";
		}
		else {
			StringBuilder sb = new StringBuilder();
			for (ConstitNode child : children) {
				sb.append(" ");
				sb.append(child.toString());
			}
			return "(" + tag + sb.toString() + ")";
		}
	}
}
