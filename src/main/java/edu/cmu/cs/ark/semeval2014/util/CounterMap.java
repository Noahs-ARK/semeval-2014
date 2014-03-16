package edu.cmu.cs.ark.semeval2014.util;
import java.util.*;
import java.util.Map.Entry;
 
public class CounterMap <S>{
	public Map<S,Double> map;
	double totalCount = 0;
	
	public CounterMap() {
		map = new HashMap<>();
	}
	
	public void increment(S term, double value) { 
		ensure0(term);
		map.put(term, map.get(term) + value);
		totalCount += value;
	}
	public void increment(S term) {
		increment(term, 1.0);
	}
 
	public void addInPlace(CounterMap<S> other) {
		for (S k : other.map.keySet()) {
			ensure0(k);
			increment(k, other.value(k));
		}
	}
	public double value(S term) {
		if (!map.containsKey(term)) return 0;
		return map.get(term);
	}
	public Set<S> support() {
		// tricky. it would be safer to check for zero-ness
		return map.keySet();
	}
	
	public CounterMap<S> copy() {
		CounterMap<S> ret = new CounterMap<S>();
		ret.map = new HashMap<>(this.map);
		ret.totalCount = this.totalCount;
		return ret;
	}
	
	/** helper: ensure that 'term' exists in the map */
	void ensure0(S term) {
		if (!map.containsKey(term)) {
			map.put(term, 0.0);
		}
	}
}