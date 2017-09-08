package edu.cmu.cs.ark.semeval2014.common;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import util.BasicFileIO;
import util.U;

//import com.google.common.base.Function;

import edu.stanford.nlp.ling.CoreAnnotations;
import edu.stanford.nlp.ling.CoreAnnotations.IndexAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Label;
import edu.stanford.nlp.trees.EnglishGrammaticalStructureFactory;
import edu.stanford.nlp.trees.LabeledScoredTreeFactory;
import edu.stanford.nlp.trees.PennTreeReader;
import edu.stanford.nlp.trees.PennTreebankLanguagePack;
import edu.stanford.nlp.trees.SemanticHeadFinder;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.TreeFactory;
import edu.stanford.nlp.trees.TreePrint;
import edu.stanford.nlp.trees.TreeReader;
import edu.stanford.nlp.trees.TreebankLanguagePack;
import edu.stanford.nlp.util.IntPair;

/** wrappers around stanfordnlp linguistic utilities, plus convenience wrappers to hide the parts of the stanford api that seem weird to me */
public class StUtil {
	
	public static TreeFactory tree_factory;
	public static TreebankLanguagePack ptbLP;
	public static EnglishGrammaticalStructureFactory englishGSF;
	public static SemanticHeadFinder semHF;
	
	static {
		tree_factory = new LabeledScoredTreeFactory();
		ptbLP = new PennTreebankLanguagePack();
		englishGSF = (EnglishGrammaticalStructureFactory) ptbLP.grammaticalStructureFactory();
		semHF = new SemanticHeadFinder();
	}
	
	/** Returns zero-based index.  Only can call on leaves, I think.
	 * Assumes Tree#indexLeaves() has already been called.
	 */
	public static int getIndex(Tree node) {
		return ((CoreLabel)node.label()).get(CoreAnnotations.IndexAnnotation.class) - 1;
	}
	public static boolean hasStartEndIndex(Tree node) {
		Integer x = ((CoreLabel)node.label()).get(CoreAnnotations.BeginIndexAnnotation.class);
		return x != null;
	}
	/** Returns start of [start,end) span.  Zero-based index. 
	 * 
	 * SEEMS UNRELIABLE
	 * 
	 * Assumes Tree#indexSpans() has already been called.
	 */
	public static int getStartIndex(Tree node) {
		Integer x = ((CoreLabel)node.label()).get(CoreAnnotations.BeginIndexAnnotation.class);
		assert x != null : "doesn't have BeginIndexAnnotation: " + node;
		return x;
//		return ((CoreLabel)node.label()).get(CoreAnnotations.BeginIndexAnnotation.class);
	}
	/** Returns end of [start,end) span.  Zero-based index.
	 * 
	 * SEEMS UNRELIABLE
	 * 
	 * Assumes Tree#indexSpans() has already been called.
	 */
	public static int getEndIndex(Tree node) {
		return ((CoreLabel)node.label()).get(CoreAnnotations.EndIndexAnnotation.class);
	}

	/** e.g. NP or NNP.  i want to know the correct way to get this */
	public static String nodeLabel(Tree node) {
		String fullLabel = node.label().toString();
		return fullLabel.split("-")[0];
	}

	/** find the node that has the given span.  If there are multiple nodes for this span, return the top-most one.
	 * (When is this possible? unary productions?)
	 */
	public static Tree findNodeForSpan(Tree root, int start, int end) {
		List<Tree> candidates = findNodesForSpan(root,start,end);
		if (candidates.size() > 0)
			return candidates.get(candidates.size()-1);
		else
			return null;
	}
	/** find the node that has the given span.  If there are multiple nodes for this span, return the top-most one.
	 * (When is this possible? unary productions?)
	 */
	public static List<Tree> findNodesForSpan(Tree root, int start, int end) {
		List<Tree> leaves = root.getLeaves();
		List<Tree> candidates = new ArrayList<>();
		Tree node = leaves.get(start);
//		assert StUtil.getStartIndex(node) == start;
		for (int i=0; i<10000; i++) {
			if (i==9999) assert false : "too much depth";
			assert node != null;
			if (node==root)
				break;
			IntPair span = getSpanLameApproach(node);
			int curstart=span.get(0), curend=span.get(1);
			if (curstart<start || curend>end)   // i think then we're past the point of no return
				break;
			if (curstart==start && curend==end)
				candidates.add(node);
//			if (StUtil.hasStartEndIndex(node)) {
//				int curstart=getStartIndex(node), curend=getEndIndex(node);
//				if (curstart<start || curend>end)   // i think then we're past the point of no return
//					break;
//				if (curstart==start && curend==end)
//					candidates.add(node);
//			} else {
//				// wtf!
//			}
			node = node.parent(root);
		}
		return candidates;
	}

	public static Tree readTreeFromString(String parseStr) {
		//read in the input into a Tree data structure
		TreeReader treeReader = new PennTreeReader(new StringReader(parseStr), tree_factory);
		Tree inputTree = null;
		try{
			inputTree = treeReader.readTree();
			
		}catch(IOException e){
			e.printStackTrace();
		}
		return inputTree;
	}

	public static void prettyPrint(Tree parseTree) {
		TreePrint tp = new TreePrint("penn");
		tp.printTree(parseTree);
	}
	
	public static void main(String[] args) {
		for (String line : BasicFileIO.STDIN_LINES) {
			Tree t = readTreeFromString(line);
			prettyPrint(t);
//			t.indexSpans();
//			U.p(findNodeForSpan(t, Integer.parseInt(args[0]), Integer.parseInt(args[1])));
		}
	}
	
//	
//	/**
//	 * supposed to work like python sorted(seq, key=fn),
//	 * e.g.
//	 * 	Specify a lexicographic ordering ....
//	 * 	sorted(seq, key=lambda x: (x.a, x.c))
//	 * 	Specify priority tiers ....
//	 *     sorted(seq, key=lambda x: 0 if x=='goodone' else 999)
//	 *     
//	 * Much easier to use than comparator crap
//	 *  
//	 * TODO move this to myutil
//	 * TODO add caching
//	 */
//	public static <T,V extends Comparable<V>> void 
//	sortByValue(List <T> items, final Function<T,V> valueFn) {
//		Collections.sort(items, new Comparator<T>() {
//			@Override
//			public int compare(T o1, T o2) {
//				V value1 = valueFn.apply(o1);
//				V value2 = valueFn.apply(o2);
//				return value1.compareTo(value2);
//			}
//		});
//	}
	/**
	 * stanford's coref system uses this instead of spans or begin/end indexes.
	 * i think because spans and begin/end are sometimes null (!!)
	 */
	public static IntPair getSpanLameApproach(Tree node) {
		List<Tree> mLeaves = node.getLeaves();
		int beginIdx = ((CoreLabel)mLeaves.get(0).label()).get(CoreAnnotations.IndexAnnotation.class)-1;
		int endIdx = ((CoreLabel)mLeaves.get(mLeaves.size()-1).label()).get(CoreAnnotations.IndexAnnotation.class);
		return new IntPair(beginIdx, endIdx);
	}
}
