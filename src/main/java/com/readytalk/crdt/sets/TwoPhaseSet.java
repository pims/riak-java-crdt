package com.readytalk.crdt.sets;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Inject;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

/**
 * Supports both add and remove operations, but only allows for a single remove.
 * 
 */
public class TwoPhaseSet<E> implements
		CRDTSet<E, ImmutableSet<E>, TwoPhaseSet<E>> {

	private static final String ADD_TOKEN = "a";
	private static final String REMOVE_TOKEN = "r";

	private final ObjectMapper serializer;
	private final GSet<E> adds;
	private final GSet<E> removals;

	private final TypeReference<Map<String, JsonNode>> ref = new TypeReference<Map<String, JsonNode>>() {

	};

	@Inject
	public TwoPhaseSet(final ObjectMapper mapper) {
		serializer = mapper;
		adds = new GSet<E>(mapper);
		removals = new GSet<E>(mapper);
	}

	public TwoPhaseSet(final ObjectMapper mapper, final byte[] value) {
		serializer = mapper;

		try {

			Map<String, JsonNode> retval = mapper.readValue(value, ref);

			adds = new GSet<E>(mapper, mapper.writeValueAsBytes(retval
					.get(ADD_TOKEN)));
			removals = new GSet<E>(mapper, mapper.writeValueAsBytes(retval
					.get(REMOVE_TOKEN)));

		} catch (IOException ioe) {
			throw new IllegalArgumentException("Invalid JSON.", ioe);
		}
	}

	private TwoPhaseSet(final ObjectMapper mapper, final GSet<E> adds,
			final GSet<E> removals) {
		serializer = mapper;
		this.adds = adds;
		this.removals = removals;
	}

	@Override
	public final TwoPhaseSet<E> merge(final TwoPhaseSet<E> other) {
		GSet<E> a = new GSet<E>(serializer);
		GSet<E> r = new GSet<E>(serializer);

		a.addAll(adds);
		r.addAll(removals);

		a.addAll(other.adds);
		r.addAll(other.removals);

		return new TwoPhaseSet<E>(serializer, a, r);
	}

	@Override
	public final ImmutableSet<E> value() {
		Set<E> retval = Sets.newHashSet();

		retval.addAll(adds);
		retval.removeAll(removals);

		return ImmutableSet.copyOf(retval);
	}

	@Override
	public final byte[] payload() {
		try {
			Map<String, JsonNode> retval = Maps.newHashMap();

			retval.put(ADD_TOKEN, serializer.readTree(adds.payload()));
			retval.put(REMOVE_TOKEN, serializer.readTree(removals.payload()));

			return serializer.writeValueAsBytes(retval);
		} catch (IOException ioe) {
			throw new IllegalStateException("Could not serialize.", ioe);
		}
	}

	@Override
	public final boolean add(final E obj) {
		if (removals.contains(obj)) {
			throw new IllegalArgumentException(
					"Cannot add to a group that has had the value removed.");
		}
		return adds.add(obj);
	}

	@Override
	public final boolean addAll(final Collection<? extends E> col) {
		Set<E> s = Sets.intersection(removals, Sets.newHashSet(col));

		if (s.size() > 0) {
			throw new IllegalArgumentException(String.format(
					"%d Elements have already been removed.", s.size()));
		}

		return adds.addAll(col);
	}

	@Override
	public final void clear() {
		removals.addAll(adds);

	}

	@Override
	public final boolean contains(final Object obj) {
		return !removals.contains(obj) && adds.contains(obj);
	}

	@Override
	public final boolean containsAll(final Collection<?> col) {
		Set<E> s = Sets.intersection(removals, Sets.newHashSet(col));
		return s.isEmpty() && adds.containsAll(col);
	}

	@Override
	public final boolean isEmpty() {
		return removals.containsAll(adds);
	}

	@Override
	public final Iterator<E> iterator() {
		return this.value().iterator();
	}

	@SuppressWarnings("unchecked")
	@Override
	public final boolean remove(final Object obj) {
		if (removals.contains(obj) || !adds.contains(obj)) {
			return false;
		}

		removals.add((E) obj);

		return true;
	}

	@Override
	public final boolean removeAll(final Collection<?> col) {

		boolean retval = this.value().containsAll(col);

		for (Object o : col) {
			this.remove(o);
		}
		return retval;
	}

	@Override
	public final boolean retainAll(final Collection<?> col) {
		throw new UnsupportedOperationException();
	}

	@Override
	public final int size() {
		return this.adds.size() - this.removals.size();
	}

	@Override
	public final Object[] toArray() {
		return this.value().toArray();
	}

	@Override
	public final <T> T[] toArray(final T[] arg) {
		return this.value().toArray(arg);
	}

	@Override
	public final boolean equals(@Nullable final Object o) {
		if (!(o instanceof TwoPhaseSet)) {
			return false;
		}

		TwoPhaseSet<?> t = (TwoPhaseSet<?>) o;

		if (this == t) {
			return true;
		} else {
			return this.value().equals(t.value());
		}
	}

	@Override
	public final int hashCode() {
		return this.value().hashCode();
	}
}
