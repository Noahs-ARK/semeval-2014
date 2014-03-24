package edu.cmu.cs.ark.semeval2014.common;

import java.util.*;

import util.BasicFileIO;
import util.U;

public class ConstitLoader {
	/** 'null' indicates there was no parse for this sentence.  (be careful! Java Map returns 'null' if key doesnt exist!  check with containsKey() first.)
	 * 
	 * as in InputAnnotatedSentence and StanfordNER, we do **not** include the hash in this version of the sentenceid.
	 */
	public static Map<String,ConstitTree> sentid2tree;
	
	static {
		sentid2tree = new HashMap<>();
	}
	
	/** loads the ConstitTree's from a file, and stores them in sentid2tree */
	public static void loadSexprFile(String filename) {
		int n=0;
		for (String line : BasicFileIO.openFileLines(filename)) {
			n++;
			String[] parts = line.split("\t");
			assert parts.length==2;
			String sentid = parts[0].replace("#","");
			String sexpr = parts[1];
			if (sexpr.equals("NOPARSE")) {
				sentid2tree.put(sentid,null);
				continue;
			}
			edu.stanford.nlp.trees.Tree stTree = StUtil.readTreeFromString(sexpr);
			stTree.setSpans(); // https://github.com/stanfordnlp/CoreNLP/issues/4#issuecomment-38504369
//			U.p("STTREE ||| " + stTree);
			ConstitTree tree = convertStanfordToOurTree(stTree);
//			U.p("MYTREE ||| " + tree.root);
//			tree.dumpIndex();
			
			assert ! sentid2tree.containsKey(sentid);
			sentid2tree.put(sentid, tree);
		}
		U.pf("Loaded %d trees (or noparse markers) from %s\n", n, filename);
	}
	static ConstitTree convertStanfordToOurTree(edu.stanford.nlp.trees.Tree stTree) {
		ConstitNode root = convertStanfordToOurNode(stTree);
		ConstitTree tree = new ConstitTree( stTree.getLeaves().size() );
		tree.root = root;
		tree.indexSpans();
		return tree;
	}
	static ConstitNode convertStanfordToOurNode(edu.stanford.nlp.trees.Tree stNode) {
//		U.p("NODE ||| " + stNode + " ||| isPre=" + stNode.isPreTerminal());
		assert ! stNode.isLeaf() : "stanford notion of a leaf is the bare word string, we should not have that here.";
		
		ConstitNode node = new ConstitNode();

		if (stNode.isPreTerminal()) {
			node.isPOSnode = true;
			node.children = new ConstitNode[0];
		}
		else {
			node.isPOSnode = false;
			int numchildren = stNode.numChildren();
			node.children = new ConstitNode[numchildren];
			for (int i=0; i<numchildren; i++) {
				node.children[i] = convertStanfordToOurNode(stNode.getChild(i));
				node.children[i].parent = node;
			}
		}
		node.tag = stNode.value();
		node.startIndex = stNode.getSpan().get(0);
		node.lastIndex = stNode.getSpan().get(1);
		return node;
	}
	public static void main(String[] args) {
		loadSexprFile(args[0]);
	}
}
