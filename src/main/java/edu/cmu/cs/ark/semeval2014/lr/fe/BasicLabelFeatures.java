package edu.cmu.cs.ark.semeval2014.lr.fe;

public class BasicLabelFeatures {
	/** Extracts the name of the label */
	public static class PassThroughFe implements FE.LabelFE {
		@Override public void features(String label, FE.FeatureAdder fa) {
			fa.add(label);
		}
	}

	/** Extracts label features specific to the DM formalism: */
	public static class DmFe implements FE.LabelFE {
		@Override public void features(String label, FE.FeatureAdder fa) {
			fa.add("IsCore=" + label.startsWith("ARG"));
			fa.add("EndsWith_c=" + label.endsWith("_c"));
		}
	}

	/** Extracts label features specific to the PAS formalism: */
	public static class PasFe implements FE.LabelFE {
		@Override public void features(String label, FE.FeatureAdder fa) {
			final String[] postagAndRole = label.split("_", 2);
			if (postagAndRole.length > 1) {
				fa.add("Pos=" + postagAndRole[0]);
				fa.add("Role=" + postagAndRole[1]);
				fa.add("IsCore=" + postagAndRole[1].startsWith("ARG"));
			}
		}
	}

	/** Extracts label features specific to the PCEDT formalism */
	public static class PcedtFE implements FE.LabelFE {
		@Override public void features(String label, FE.FeatureAdder fa) {
			fa.add("EndsWith.member=" + label.endsWith(".member"));
		}
	}
}
