package org.vena.qb.util.block;

import java.util.Arrays;

public final class ShortBlock extends PrimitiveBlockImpl {
	
	short[] storage;

	protected ShortBlock(int initialCapacity) {
		storage = EMPTY;
		accommodateIndex(initialCapacity);
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
	
	public short at(int index){ return storage[index]; }

	@Override
	public void set(int index, long value) throws ValueBeyondLimitException {
		set(index, (short)validate(value));
	}

	public void set(int index, short value) {
		if (index < 0 || index > population) {
			throw new IndexOutOfBoundsException(ShortBlock.class.getSimpleName() + ": index " + index + " is outside of 0.." + population);
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

	@Override protected boolean isValid(long value) { return ((short)value == value); }
	public static boolean  isValidValue(long value) { return ((short)value == value); }

	static final short[] EMPTY = new short[0];

	@Override
	public void shrinkwrap(int roomPercentage) {
		int newLength = shrinkwrappedLength(storage.length, population, roomPercentage);
		if (newLength != storage.length) {
			storage = Arrays.copyOf(storage, newLength);
		}
	}

}
