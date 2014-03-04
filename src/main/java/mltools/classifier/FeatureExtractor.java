package mltools.classifier;

/** 
 * ExampleType is user-specified. You're reponsible for defining feature extractors for the same types of objects you're gonna classify later.
 * Technically, we don't require it to implement InputExampleInterface, though at trainingtime this is necessary.
 * 
 */
abstract public class FeatureExtractor<ExampleType> {
	abstract public void computeFeatures(ExampleType ex, FeatureAdder fa);
	
	/** a subclass of this gets passed to the feature extractor as a callback for it to compute features. */
	public static abstract class FeatureAdder {
		public void add(String featurename) {
			add(featurename, 1.0);
		}
		abstract public void add(String featurename, double value);
		
	}
}
