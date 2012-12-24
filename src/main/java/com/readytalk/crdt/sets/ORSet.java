package com.readytalk.crdt.sets;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.readytalk.crdt.AbstractCRDT;

public class ORSet<E> extends AbstractCRDT<ImmutableSet<E>, ORSet<E>> implements
		CRDTSet<E, ImmutableSet<E>, ORSet<E>> {

	private final Multimap<E, UUID> elements = LinkedHashMultimap.create();
	private final Multimap<E, UUID> tombstones = LinkedHashMultimap.create();

	public ORSet(final ObjectMapper mapper) {
		super(mapper);
	}
	
	public ORSet(final ObjectMapper mapper, final byte [] value) {
		super(mapper);
		
		TypeReference<Map<String, Map<E, Collection<UUID>>>> ref = new TypeReference<Map<String, Map<E, Collection<UUID>>>>() {

		};
		
		try {
			Map<String, Map<E, Collection<UUID>>> s1 = mapper.readValue(value, ref);
			
			Map<E, Collection<UUID>> e = s1.get("e");
			Map<E, Collection<UUID>> t = s1.get("t");
			
			for (Map.Entry<E, Collection<UUID>> o : e.entrySet()) {
				elements.putAll(o.getKey(), o.getValue());
			}
			
			for (Map.Entry<E, Collection<UUID>> o : t.entrySet()) {
				tombstones.putAll(o.getKey(), o.getValue());
			}
			
		} catch (IOException ex) {
			throw new IllegalArgumentException("Unable to deserialize.");
		}
		
	}
	
	@Override
	public boolean add(E value) {
		UUID uuid = UUID.randomUUID();
		boolean retval = !elements.containsKey(value);
		
		elements.put(value, uuid);
		
		return retval;
	}

	@Override
	public boolean addAll(Collection<? extends E> values) {
		
		boolean retval = false;
		
		for (E o : values) {
			retval |= this.add(o);
		}
		
		return retval;
	}

	@Override
	public void clear() {
		this.tombstones.putAll(this.elements);
		this.elements.clear();

	}

	@Override
	public boolean contains(Object value) {
		return this.elements.containsKey(value);
	}

	@Override
	public boolean containsAll(Collection<?> values) {
		return this.value().containsAll(values);
	}

	@Override
	public boolean isEmpty() {
		return elements.isEmpty();
	}

	@Override
	public Iterator<E> iterator() {
		return Iterators.unmodifiableIterator(this.elements.keySet().iterator());
	}

	@SuppressWarnings("unchecked")
	@Override
	public boolean remove(Object value) {
		Preconditions.checkNotNull(value);
		
		this.tombstones.putAll((E)value, elements.get((E)value));
		
		return elements.removeAll(value).size() > 0;
		
	}

	@Override
	public boolean removeAll(final Collection<?> values) {
		Preconditions.checkNotNull(values);
		
		
		Multimap<E, UUID> subset = Multimaps.filterKeys(elements, new Predicate<E>() {

			@Override
			public boolean apply(@Nullable E input) {
				
				Preconditions.checkNotNull(input);
				
				return values.contains(input);
			}
		});
		
		if (subset.isEmpty()) {
			return false;
		}
		
		for (E o : subset.keySet()) {
			Collection<UUID> result = this.elements.removeAll(o);
			
			this.tombstones.putAll(o, result);
		}
		
		
		return true;
	}

	@Override
	public boolean retainAll(Collection<?> values) {
		throw new UnsupportedOperationException();
	}

	@Override
	public int size() {
		return elements.size();
	}

	@Override
	public Object[] toArray() {
		return elements.keySet().toArray();
	}

	@Override
	public <T> T[] toArray(T[] arg) {
		return elements.keySet().toArray(arg);
	}

	@Override
	public ORSet<E> merge(ORSet<E> other) {
		ORSet<E> retval = new ORSet<E>(serializer());
		
		retval.elements.putAll(this.elements);
		retval.elements.putAll(other.elements);
		retval.tombstones.putAll(this.tombstones);
		retval.tombstones.putAll(other.elements);
		
		retval.elements.removeAll(retval.tombstones);
		
		return retval;
	}

	@Override
	public ImmutableSet<E> value() {
		return ImmutableSet.copyOf(elements.keySet());
	}

	@Override
	public byte[] payload() {
		Map<String, Object> retval = Maps.newLinkedHashMap();
		
		retval.put("e", elements.asMap());
		retval.put("t", tombstones.asMap());
		try {
			return serializer().writeValueAsBytes(retval);
		} catch (IOException ex) {
			throw new IllegalStateException("Unable to serialize object.", ex);
		}
	}

	@Override
	public final boolean equals(@Nullable final Object o) {
		if (!(o instanceof ORSet)) {
			return false;
		}

		ORSet<?> t = (ORSet<?>) o;

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
