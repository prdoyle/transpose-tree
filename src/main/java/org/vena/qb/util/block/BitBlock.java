package org.vena.qb.util.block;

import java.util.function.LongToIntFunction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Growable array of entries, each possessing a given number of bits.
 * 
 * @author Patrick Doyle
 *
 */
public abstract class BitBlock extends PrimitiveBlockImpl {
	
	final LongBlock containers; // TODO: Maybe extend a protected version of this to save memory

	/** Packs multiple descriptor fields into one 4-byte int to save space.
	 */
	final int shape;
	public int bitsPerEntry(){ return shape & 0x7f; }
	int entriesPerContainer(){ return shape >> 8; }

	BitBlock(int entrySize, int initialCapacity) {
		if (entrySize < 1 || entrySize > 32)
			throw new IllegalArgumentException(BitBlock.class.getSimpleName() + " accommodates entries between 1 and 32 bits (" + entrySize + " requested)");
		int entriesPerContainer = 64 / entrySize;
		shape = (entriesPerContainer << 8) | entrySize;
		containers = new LongBlock((initialCapacity+entriesPerContainer-1) / entriesPerContainer); // TODO: Protect against overflow near Intever.MAX_VALUE
	}

	@Override
	public long get(int index) {
		long result = valueForBits(extractEntry(index, containers.get(containerIndex(index))));
		assert isValid(result);
		return result;
	}

	@Override
	public void set(int index, long newValue) throws ValueBeyondLimitException {
		int containerIndex = containerIndex(index);
		containers.accommodateIndex(containerIndex);
		containers.set(containerIndex, replaceEntry(index, containers.get(containerIndex), bitsForValue(validate(newValue))));
		if (index == population) {
			population++;
		}
	}

	@Override
	public void shrinkwrap(int roomPercentage) {
		containers.shrinkwrap(roomPercentage);
	}

	/**
	 * @param value that returns true for {@link #isValid(long)}.
	 * @return a number between 0 (inclusive) and 1 << {@link #bitsPerEntry()} (exclusive).
	 */
	abstract long bitsForValue(long value);

	/**
	 * Inverse of {@link #bitsForValue(long)}.
	 */
	abstract long valueForBits(long bits);
	
	abstract public long lowerLimit();
	abstract public long upperLimit();

	@Override
	protected void accommodateIndex(int index) {
		containers.accommodateIndex(containerIndex(index));
	}

	long entryMask(){ return ~(-1L << bitsPerEntry()); }

	int containerIndex(int index) { return index / entriesPerContainer(); }
	
	int entryShift(int index){ return bitsPerEntry() * (index % entriesPerContainer()); }

	long extractEntry(int index, long container) {
		return (container >> entryShift(index)) & entryMask();
	}

	long replaceEntry(int index, long oldBits, long value) {
		int shift = entryShift(index);
		long mask = ~(entryMask() << shift);
		long result = (oldBits & mask) | (value << shift);
		LOGGER.trace("replaceEntry({}, {}, {}) = {}", index, oldBits, value, result);
		return result;
	}

	@Override
	public String toString(){ return "BitBlock." + getClass().getSimpleName() + "(" + bitsPerEntry() + ")"; }

	static final Logger LOGGER = LoggerFactory.getLogger(BitBlock.class);
	
	//
	// Variations on bit value interpretations
	//
	// TODO: Everything other than Unsigned needs more testing.
	//

	public static class Unsigned extends BitBlock {
		public Unsigned(int entrySize, int initialCapacity) { super(entrySize, initialCapacity); }

		@Override
		protected boolean isValid(long value) {
			return (value & ~entryMask()) == 0;
		}

		@Override long bitsForValue(long value) { return value; } 
		@Override long valueForBits(long value) { return value; }
		@Override public long lowerLimit(){ return 0; }
		@Override public long upperLimit(){ return entryMask(); }

		static int bitsRequiredFor(long value) {
			if (value < 0)
				return Integer.MAX_VALUE;
			else
				return Long.SIZE - Long.numberOfLeadingZeros(value);
		}
	}

	public static class Signed extends BitBlock {
		public Signed(int entrySize, int initialCapacity) { super(entrySize, initialCapacity); }
		
		/* We could have implemented this just by adding a bias to the value.  For example,
		 * a 2-bit BitBlock.Signed could store values like this:
		 *
		 * value   bits
		 *  -2      0
		 *  -1      1
		 *   0      2
		 *   1      3
		 *
		 * However, for now, we're doing it in two's complement because that was thought to
		 * be less confusing during debugging.  It does make this class more complicated,
		 * though, so we might reverse this decision at some point.
		 */

		@Override
		protected boolean isValid(long value) {
			// Shift off all but the sign bit
			//
			long signBits = value >> (bitsPerEntry() - 1);
			
			// The resulting bits must be -1 or 0.  Either way, doing an int add
			// with the bottom bit results in zero.
			//
			return signBits + (signBits & 1) == 0;
		}

		@Override
		long bitsForValue(long value) {
			int shift = Long.SIZE - bitsPerEntry();
			return (value << shift) >>> shift; // zero-extending right-shift
		}

		@Override
		long valueForBits(long value) {
			int shift = Long.SIZE - bitsPerEntry();
			return (value << shift) >> shift; // sign-extending right-shift
		}

		@Override public long lowerLimit(){ return ~upperLimit(); }
		@Override public long upperLimit(){ return entryMask() >> 1; }
		
		static int bitsRequiredFor(long value) {
			// 1 for the sign bit
			if (value < 0)
				return bitsRequiredFor(~value);
			else
				return 1 + Long.SIZE - Long.numberOfLeadingZeros(value);
		}
	}

	public static class UnsignedOrNull extends Unsigned {
		public UnsignedOrNull(int entrySize, int initialCapacity) { super(entrySize, initialCapacity); }

		/* Same as {@link Unsigned} except the highest value is treated as -1,
		 * which can be used as a null value in data structures that use
		 * BitBlocks to store super-compressed references.
		 * 
		 * We could have implemented this easily by storing value+1 as an
		 * unsigned number, but that was considered too confusing during debugging.
		 */

		@Override
		protected boolean isValid(long value) {
			return super.isValid(value+1); // overflow is ok
		}
		
		final long nullValue(){ return entryMask(); }

		@Override long bitsForValue(long value) { return (value == -1)? nullValue() : value; } 
		@Override long valueForBits(long value) { return (value == nullValue())? -1 : value; }
		@Override public long lowerLimit(){ return -1; }
		@Override public long upperLimit(){ return super.upperLimit()-1; }

		static int bitsRequiredFor(long value) {
			return Unsigned.bitsRequiredFor(value+1); // overflow is ok
		}
	}
	
	/**
	 * Sometimes it's possible to accommodate larger entries without using any more memory.
	 * @param minSize The minimum number of bits required to hold each entry
	 * @return A value at least as large as minSize that would make a good entry size.
	 */
	public static int smartEntrySize(int minSize) {
		// No point in using artificially small entry sizes if they save no storage.
		// This will use every size up to 10, then 12, 16, 21, and 32.
		//
		return Long.SIZE / (Long.SIZE / minSize);
	}
	
	/**
	 * Even better than {@link #shrinkwrap(int)}, this not only reduces the size
	 * of the underlying arrays; it returns a new {@link BitBlock} that uses fewer bits
	 * per entry.
	 */
	public BitBlock snug(int roomPercentage) {
		if (size() == 0) {
			// Special case: we can't snug the storage down based on the
			// contents, so just leave it alone.
			return this;
		}
		
		// Find the extreme values
		//
		Statistics stats = new Statistics();
		stream().forEach(value->stats.process(value));
		int uBits  = stats.bitsRequired(v -> Unsigned.bitsRequiredFor(v));
		int sBits  = stats.bitsRequired(v -> Signed.bitsRequiredFor(v));
		int unBits = stats.bitsRequired(v -> UnsignedOrNull.bitsRequiredFor(v));
		
		// Instantiate the best BitBlock
		//
		BitBlock result;
		if (unBits <= uBits && unBits <= sBits && unBits <= 32) {
			// Prefer UnsignedOrNull if it doesn't use more bits
			result = new BitBlock.UnsignedOrNull(unBits, size());
		} else if (unBits <= sBits && unBits <= 32) {
			// Prefer Unsigned if all values are non-negative
			result = new BitBlock.Unsigned(uBits, size());
		} else if (sBits <= 32) {
			result = new BitBlock.Signed(sBits, size());
		} else {
			LOGGER.error("Snug: {} for range [{}..{}] unsigned:{} signed:{} unsignedOrNull:{}", this, stats.min, stats.max, uBits, sBits, unBits);
			throw new AssertionError("Any " + BitBlock.class.getSimpleName() + " must have a way to be snug");
		}
		
		if (this.getClass() == result.getClass() && this.bitsPerEntry() == result.bitsPerEntry()) {
			// Can't do any better than what we already have
			return this;
		}
		
		LOGGER.info("Snug: {} into {} for range [{}..{}]", this, result, stats.min, stats.max);

		// Copy the data over
		//
		stream().forEachOrdered(value->{
			try {
				result.add(value);
			} catch (ValueBeyondLimitException e) {
				throw new AssertionError(result + " should accommodate " + e.value(), e);
			}
		});
		
		result.shrinkwrap(roomPercentage); // TODO: Shouldn't need a second pass for this

		return result;
	}

	private static class Statistics {
		long min = Long.MAX_VALUE;
		long max = Long.MIN_VALUE;
		
		void process(long value) {
			min = Math.min(min, value);
			max = Math.max(max, value);
		}
		
		int bitsRequired(LongToIntFunction x) {
			assert min <= max;
			return smartEntrySize(Math.max(x.applyAsInt(min), x.applyAsInt(max)));
		}
		
		@Override public String toString(){ return "min:" + min + " max:" + max; }
	}
	
}
