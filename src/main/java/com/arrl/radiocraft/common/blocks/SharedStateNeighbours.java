package com.arrl.radiocraft.common.blocks;

import com.google.common.collect.Table;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The wire blocks' state neighbour tables, shared (2026-11-16, the 1.14 performance plan, doc/Plan-1.14.md step 2a).
 * <p>
 * Vanilla gives every block state its own table of the states one property away
 * ({@code StateHolder#populateNeighbours}: a Guava {@code ArrayTable} with two maps of its own and a 2-D array). The
 * wire blocks have 3,840 states each, 52 blocks, about 200,000 states: those tables were most of the 170-340 MB the
 * wire states cost (measured 2026-11-15). Here each state keeps only its index among its block's states, and one array
 * of the states per block answers every lookup by arithmetic: the neighbour on property p is this index plus (new
 * value's digit - current digit) x p's stride. {@code mixin.MixinStateHolder} puts these in place for
 * {@link WireBlock} states; it is left out when FerriteCore is installed, which does the same for every block.
 * <p>
 * Only {@link Table#get} (all {@code StateHolder#setValue} and {@code trySetValue} use) and the read-only queries are
 * supported, like the vanilla table it stands in for, which nothing else changes.
 */
public final class SharedStateNeighbours {

	private SharedStateNeighbours() {}

	/** The block whose states are being set up: its states' tables share one {@link Block}. Only while it is built. */
	private static WeakReference<Map<?, ?>> building = new WeakReference<>(null);
	@Nullable
	private static Block buildingBlock;

	/**
	 * The neighbour table for the state with {@code values}, one of {@code possible} (its block's states by their values,
	 * as {@code StateDefinition} hands every state of the block in turn).
	 */
	public static synchronized <S> Table<Property<?>, Comparable<?>, S> of(Map<Map<Property<?>, Comparable<?>>, S> possible, Map<Property<?>, Comparable<?>> values) {
		if(building.get() != possible || buildingBlock == null) {
			buildingBlock = new Block(possible);
			building = new WeakReference<>(possible);
		}
		return new Neighbours<>(buildingBlock, buildingBlock.index(values));
	}

	/** One block's states, by index: the properties as digits of a mixed-radix number. */
	static final class Block {
		final Property<?>[] properties;
		final int[] strides, radices;
		final List<Map<Comparable<?>, Integer>> digits;
		final Object[] states;

		Block(Map<? extends Map<Property<?>, Comparable<?>>, ?> possible) {
			Map<Property<?>, Comparable<?>> any = possible.keySet().iterator().next();
			properties = any.keySet().toArray(new Property<?>[0]);
			strides = new int[properties.length];
			radices = new int[properties.length];
			digits = new java.util.ArrayList<>(properties.length);
			int stride = 1;
			for(int p = 0; p < properties.length; p++) {
				Map<Comparable<?>, Integer> digit = new HashMap<>();
				for(Comparable<?> value : properties[p].getPossibleValues())
					digit.put(value, digit.size());
				digits.add(digit);
				radices[p] = digit.size();
				strides[p] = stride;
				stride *= digit.size();
			}
			states = new Object[stride];
			for(Map.Entry<? extends Map<Property<?>, Comparable<?>>, ?> entry : possible.entrySet())
				states[index(entry.getKey())] = entry.getValue();
			for(Object state : states)
				if(state == null)
					throw new IllegalStateException("A state is missing from " + possible.size() + " of " + stride);
		}

		int index(Map<Property<?>, Comparable<?>> values) {
			int index = 0;
			for(int p = 0; p < properties.length; p++) {
				Integer digit = digits.get(p).get(values.get(properties[p]));
				if(digit == null)
					throw new IllegalArgumentException("No " + properties[p] + " in " + values);
				index += digit * strides[p];
			}
			return index;
		}

		int property(@Nullable Object property) {
			for(int p = 0; p < properties.length; p++)
				if(properties[p] == property)
					return p;
			return -1;
		}
	}

	/** One state's table: its block's states and its own index among them. */
	static final class Neighbours<S> implements Table<Property<?>, Comparable<?>, S> {

		private final Block block;
		private final int index;

		Neighbours(Block block, int index) {
			this.block = block;
			this.index = index;
		}

		/** The state with {@code property} set to {@code value}, or null for the value it has (as vanilla's table) or one it cannot. */
		@Override
		@Nullable
		@SuppressWarnings("unchecked")
		public S get(@Nullable Object property, @Nullable Object value) {
			int p = block.property(property);
			if(p < 0)
				return null;
			Integer digit = block.digits.get(p).get(value);
			if(digit == null)
				return null;
			int current = index / block.strides[p] % block.radices[p];
			return digit == current ? null : (S)block.states[index + (digit - current) * block.strides[p]];
		}

		@Override
		public boolean contains(@Nullable Object property, @Nullable Object value) {
			return get(property, value) != null;
		}

		@Override
		public boolean containsRow(@Nullable Object property) {
			int p = block.property(property);
			return p >= 0 && block.radices[p] > 1;
		}

		@Override
		public boolean containsColumn(@Nullable Object value) {
			for(Property<?> property : block.properties)
				if(contains(property, value))
					return true;
			return false;
		}

		@Override
		public boolean containsValue(@Nullable Object state) {
			for(Property<?> property : block.properties)
				for(Comparable<?> value : property.getPossibleValues())
					if(state != null && get(property, value) == state)
						return true;
			return false;
		}

		@Override
		public boolean isEmpty() {
			return size() == 0;
		}

		@Override
		public int size() {
			int size = 0;
			for(int radix : block.radices)
				size += radix - 1;
			return size;
		}

		private static UnsupportedOperationException readOnly() {
			return new UnsupportedOperationException("A shared wire state neighbour table (RadioCraft) is read only");
		}

		@Override
		public void clear() {
			throw readOnly();
		}

		@Override
		public S put(Property<?> property, Comparable<?> value, S state) {
			throw readOnly();
		}

		@Override
		public void putAll(Table<? extends Property<?>, ? extends Comparable<?>, ? extends S> table) {
			throw readOnly();
		}

		@Override
		public S remove(@Nullable Object property, @Nullable Object value) {
			throw readOnly();
		}

		@Override
		public Map<Comparable<?>, S> row(Property<?> property) {
			throw readOnly();
		}

		@Override
		public Map<Property<?>, S> column(Comparable<?> value) {
			throw readOnly();
		}

		@Override
		public Set<Cell<Property<?>, Comparable<?>, S>> cellSet() {
			throw readOnly();
		}

		@Override
		public Set<Property<?>> rowKeySet() {
			throw readOnly();
		}

		@Override
		public Set<Comparable<?>> columnKeySet() {
			throw readOnly();
		}

		@Override
		public Collection<S> values() {
			throw readOnly();
		}

		@Override
		public Map<Property<?>, Map<Comparable<?>, S>> rowMap() {
			throw readOnly();
		}

		@Override
		public Map<Comparable<?>, Map<Property<?>, S>> columnMap() {
			throw readOnly();
		}
	}
}
