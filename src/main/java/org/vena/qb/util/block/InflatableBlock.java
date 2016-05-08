package org.vena.qb.util.block;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InflatableBlock extends PrimitiveBlock {

	PrimitiveBlock block;
	final BlockFactory blockFactory;

	InflatableBlock(int initialCapacity, BlockFactory blockFactory) {
		this.blockFactory = blockFactory;
		this.block = blockFactory.newBlock(1, initialCapacity);
	}

	InflatableBlock(int initialCapacity) {
		this(initialCapacity, BITWISE_GROWTH);
	}

	public static InflatableBlock empty(){ return new InflatableBlock(1); }
	public static InflatableBlock empty(BlockFactory f){ return new InflatableBlock(1, f); }

	@Override public int size() { return block.size(); }
	@Override public long get(int index) { return block.get(index); }
	@Override protected void accommodateIndex(int index) { block.accommodateIndex(index); }
	@Override protected boolean isValid(long value) { return true; }

	@Override
	public void shrinkwrap(int roomPercentage) {
		if (block instanceof BitBlock) {
			/** In measurements, this typically doesn't help much relative to {@link InflatableBlock#BITWISE_GROWTH}.
			block = ((BitBlock) block).snug(roomPercentage);
			*/
		}
		block.shrinkwrap(roomPercentage);
	}

	@Override
	public void set(int index, long newValue) {
		try {
			block.set(index, newValue);
		} catch (ValueBeyondLimitException e) {
			PrimitiveBlock newBlock = blockFactory.newBlock(e.value(), 1+block.size());
			LOGGER.warn("Inflating {}[{}] to {} to accommodate value {}", block, block.size(), newBlock, e.value());
			block.stream().forEach(x -> {
				try {
					newBlock.add(x);
				} catch (ValueBeyondLimitException e1) {
					throw new AssertionError("newBlock is supposed to be able to accommodate all existing values", e1);
				}
			});
			try {
				newBlock.set(index, newValue);
			} catch (ValueBeyondLimitException e1) {
				throw new AssertionError("newBlock is supposed to be able to accommodate the new value", e1);
			}
			block = newBlock;
		}
	}

	@Override // Just to eliminate the "throws" clause because this doesn't throw
	public void add(long value) {
		set(size(), value);
	}

	static final Logger LOGGER = LoggerFactory.getLogger(InflatableBlock.class);
	
	public static interface BlockFactory {
		PrimitiveBlock newBlock(long valueToAccommodate, int capacity);
	}

	/**
	 * TODO: Use a BitSet for 1-bit entries, since the JIT knows about BitSet
	 * and optimizes it.
	 */
	public static final BlockFactory JAVA_PRIMITIVE_BLOCKS = (long valueToAccommodate, int capacity)-> {
		if (ByteBlock.isValidValue(valueToAccommodate)) {
			return new ByteBlock(capacity);
		} else if (ShortBlock.isValidValue(valueToAccommodate)) {
			return new ShortBlock(capacity);
		} else if (IntBlock.isValidValue(valueToAccommodate)) {
			return new IntBlock(capacity);
		} else {
			return new LongBlock(capacity);
		}
	};

	public static final BlockFactory BITWISE_GROWTH = (long valueToAccommodate, int capacity)-> {
		int numBitsRequired = BitBlock.UnsignedOrNull.bitsRequiredFor(valueToAccommodate);
		if (numBitsRequired <= 32) {
			return new BitBlock.UnsignedOrNull(BitBlock.smartEntrySize(numBitsRequired), capacity);
		} else {
			return new LongBlock(capacity);
		}
	};

	// Not thrilled about making this public, but it does make it easier to
	// report on the memory usage status.
	//
	public PrimitiveBlock currentStorageBlock() {
		return block;
	}

}
