package org.vena.qb.util.block;

import java.util.stream.IntStream;

/**
 * In some contexts, we want ints; for example, if they're being used as array indexes.
 * In that case, it's awkward always to get longs back from InflatableBlock, so this
 * is a subclass that restricts insertions to be within the Integer range, and in exchange,
 * returns ints with no fuss.
 * 
 * @author Patrick Doyle
 *
 */
public class InflatableIntBlock extends InflatableBlock {

	protected InflatableIntBlock(int initialCapacity) {
		super(initialCapacity);
	}

	protected InflatableIntBlock(int initialCapacity, BlockFactory factory) {
		super(initialCapacity, factory);
	}

	public static InflatableIntBlock empty(){ return new InflatableIntBlock(1); }
	public static InflatableIntBlock empty(BlockFactory factory){ return new InflatableIntBlock(1, factory); }

	public int getInt(int index){ return (int)get(index); }
	
	public IntStream intStream(){ return stream().mapToInt(i -> (int)i); }

	@Override
	public void set(int index, long newValue) {
		if (IntBlock.isValidValue(newValue))
			super.set(index, newValue);
		else
			throw new IllegalArgumentException("Hey, you promised you'd only insert ints (value: " + newValue + ")");
	}

}
