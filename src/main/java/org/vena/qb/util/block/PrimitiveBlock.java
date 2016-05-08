package org.vena.qb.util.block;

import java.util.PrimitiveIterator.OfLong;
import java.util.RandomAccess;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A "block" is like an ArrayList, except we have efficient versions for all
 * sizes of primitives, and if you set an element just past the end of the
 * block, the block will grow instead of throwing IndexOutOfBoundsException.
 * (This is not true for {@link get}.)
 * 
 * Uh, also, no deletion.
 * 
 * The primitive blocks operate on longs as a kind of lowest common denominator.
 * They generate LongStreams etc.  If a block can't accommodate a value that it
 * is asked to store, it will throw {@link ValueBeyondLimitException} describing what
 * went wrong.
 *
 * @author Patrick Doyle
 *
 */
public abstract class PrimitiveBlock implements RandomAccess {

	abstract public int size();
	abstract public long get(int index);
	abstract public void set(int index, long newValue) throws ValueBeyondLimitException;
	abstract protected void accommodateIndex(int index);
	abstract protected boolean isValid(long value);
	abstract public void shrinkwrap(int roomPercentage);

	public boolean isEmpty() { return size() == 0; }

	protected static int newLengthFor(int index) {
		// Avoid powers of two because they're not particularly efficient array lengths to allocate
		int shift = Integer.SIZE - Integer.numberOfLeadingZeros(index/SIZE_MULTIPLE);

		// TODO: Grow more slowly when memory is tight
		return SIZE_MULTIPLE << shift;
	}

	protected int shrinkwrappedLength(int currentLength, int desiredLength, int roomPercentage) {
		long newLengthLong = desiredLength * (100L+roomPercentage) / 100; // TODO: If we're being pedantic, this could overflow
		int newLength;
		try {
			newLength = Math.toIntExact(newLengthLong);
		} catch (ArithmeticException e) {
			newLength = Integer.MAX_VALUE;
		}
		if (newLength != currentLength) {
			LOGGER.info("shrinkwrapping " + this + " from " + currentLength + " to " + newLength);
		}
		return newLength;
	}

	/**
	 * Picking 5 for this has the nice side-benefit that array sizes all tend to
	 * be powers of two with a zero at the end, which are easy to recognize when
	 * debugging.
	 */
	private static final int SIZE_MULTIPLE = 5;

	protected long validate(long value) throws ValueBeyondLimitException {
		if (isValid(value))
			return value;
		else
			throw new ValueBeyondLimitException(value);
		
	}

	public void add(long value) throws ValueBeyondLimitException {
		set(size(), value);
	}

	public LongStream stream() {
		return IntStream.range(0, size()).mapToLong(i->get(i));
	}
	
	public OfLong iterator() {
		return stream().iterator();
	}

	@SuppressWarnings("serial")
	public static class ValueBeyondLimitException extends Exception {
		final long value;

		public long value(){ return value; }
	
		public ValueBeyondLimitException(long value) {
			super(""+value);
			this.value = value;
		}
	
		public ValueBeyondLimitException(String message, long value) {
			super(message);
			this.value = value;
		}
	
		public ValueBeyondLimitException(String message, long value, Throwable cause) {
			super(message, cause);
			this.value = value;
		}
	
		public ValueBeyondLimitException(long value, Throwable cause) {
			super(""+value, cause);
			this.value = value;
		}
		
	}
	
	public String toString() { return getClass().getSimpleName(); }
	
	static final Logger LOGGER = LoggerFactory.getLogger(PrimitiveBlock.class);

}