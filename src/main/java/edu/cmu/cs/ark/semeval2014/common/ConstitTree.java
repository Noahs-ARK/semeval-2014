package edu.cmu.cs.ark.semeval2014.common;

import java.util.*;

import util.U;

public class ConstitTree {
	/** [start,end] indexing, so the first POS is at [0][0] */
	public List<ConstitNode>[][] nodeBySpan;
	public ConstitNode root;
	
	public ConstitTree(int len) {
//		nodeBySpan = (List<ConstitNode>[][]) new Object[len][len];
		nodeBySpan = (List<ConstitNode>[][]) new ArrayList[len][len];
		for (int i=0; i<len; i++) {
			for (int j=0; j<len; j++) {
				nodeBySpan[i][j] = new ArrayList<ConstitNode>();
			}
		}
	}
	public void dumpIndex() {
		int len = nodeBySpan.length;
		for (int i=0; i<len; i++) {
			for (int j=0; j<len; j++) {
				if (nodeBySpan[i][j].size() > 0) {
					U.pf("SPAN %d %d\n",i,j);
					for (ConstitNode node : nodeBySpan[i][j]) {
						U.p("\t" + node);
					}
				}
			}
		}
	}
	////////////////
	public void indexSpans() {
		indexSpans(root);
	}
	public void indexSpans(ConstitNode node) {
		nodeBySpan[node.startIndex][node.lastIndex].add(node);
		for (ConstitNode child : node.children) {
			indexSpans(child);
		}
	}
}
