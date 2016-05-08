package org.vena.qb.util.block;

import java.util.Arrays;

public final class LongBlock extends PrimitiveBlockImpl {
	
	long[] storage;

	protected LongBlock(int initialCapacity) {
		storage = EMPTY;
		accommodateIndex(initialCapacity);
	}
	
	public static LongBlock empty() {
		return new LongBlock(1);
	}

	@Override
	public void clear() {
		super.clear();
		storage = EMPTY;
	}

	@Override
	public long get(int index) {
		return storage[index];
	}

	@Override // Just to establish that this doesn't throw OverflowException
	public void add(long value) {
		set(size(), value);
	}

	@Override
	public void set(int index, long value) {
		if (index < 0 || index > population) {
			throw new IndexOutOfBoundsException(LongBlock.class.getSimpleName() + ": index " + index + " is outside of 0.." + population);
		}
		accommodateIndex(index);
		storage[index] = value;
		if (index == population) {
			population++;
		}
	}

	@Override
	protected void accommodateIndex(int index) {
		if (storage.length <= index) {
			storage = Arrays.copyOf(storage, newLengthFor(index));
		}
	}

	@Override
	protected boolean isValid(long value) {
		return true;
	}

	static final long[] EMPTY = new long[0];

	@Override
	public void shrinkwrap(int roomPercentage) {
		int newLength = shrinkwrappedLength(storage.length, population, roomPercentage);
		if (newLength != storage.length) {
			storage = Arrays.copyOf(storage, newLength);
		}
	}

}
