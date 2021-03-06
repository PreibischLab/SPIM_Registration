package poissonSimulator;

public class ConstantGenerator <T extends Number> implements NumberGenerator<T> {

	 private final T constant;

	    /**
	     * Creates a number generator that always returns the same
	     * values.
	     * @param constant The value to be returned by all invocations
	     * of the {@link #nextValue()} method.
	     */
	    public ConstantGenerator(T constant)
	    {
	        this.constant = constant;
	    }

	    /**
	     * @return The constant value specified when the generator was constructed.
	     */
	    public T nextValue()
	    {
	        return constant;
	    }

}
