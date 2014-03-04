package mltools.classifier;

public class BinaryLabeledExample<T> {
	public T example;
	public boolean label;
	
	public BinaryLabeledExample(boolean label, T example) {
		this.example = example;
		this.label = label;
	}
	public static <S> BinaryLabeledExample<S> make(boolean label, S example) {
		return new BinaryLabeledExample<S>(label, example);
	}
}
