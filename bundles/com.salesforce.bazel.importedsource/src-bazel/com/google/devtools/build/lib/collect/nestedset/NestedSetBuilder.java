// Copyright 2014 The Bazel Authors. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//    http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.devtools.build.lib.collect.nestedset;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.collect.Iterables.getOnlyElement;

import java.util.Set;
import java.util.concurrent.ExecutionException;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.guava.CaffeinatedGuava;
import com.google.common.base.Preconditions;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.devtools.build.lib.collect.compacthashset.CompactHashSet;
import com.google.devtools.build.lib.collect.nestedset.NestedSet.InterruptStrategy;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

/**
 * A builder for nested sets.
 *
 * <p>The builder supports the standard builder interface (that is, {@code #add}, {@code #addAll}
 * and {@code #addTransitive} followed by {@code build}), in addition to shortcut method
 * {@code #wrap}. Any duplicate elements will be inserted as-is, and pruned later on during the
 * traversal of the actual NestedSet.
 */
public final class NestedSetBuilder<E> {

  private final Order order;
  private CompactHashSet<E> items;
  private CompactHashSet<NestedSet<E>> transitiveSets;

  public NestedSetBuilder(Order order) {
    this.order = order;
  }

  /**
   * Returns the order used by this builder.
   *
   * <p>This is useful for testing for incompatibilities (via {@link Order#isCompatible}) without
   * catching an unchecked exception from {@link #addTransitive}.
   */
  public Order getOrder() {
    return order;
  }

  /** Returns whether the set to be built is empty. */
  public boolean isEmpty() {
    return items == null && transitiveSets == null;
  }

  /**
   * Adds a direct member to the set to be built.
   *
   * <p>The relative left-to-right order of direct members is preserved from the sequence of calls
   * to {@link #add} and {@link #addAll}. Since the traversal {@link Order} controls whether direct
   * members appear before or after transitive ones, the interleaving of {@link #add}/{@link
   * #addAll} with {@link #addTransitive} does not matter.
   *
   * @param element item to add; must not be null
   * @return the builder
   */
  @CanIgnoreReturnValue
  public NestedSetBuilder<E> add(E element) {
    Preconditions.checkNotNull(element);
    if (items == null) {
      items = CompactHashSet.create();
    }
    items.add(element);
    return this;
  }

  /**
   * Adds a sequence of direct members to the set to be built. Equivalent to invoking {@link #add}
   * for each item in {@code elements}, in order.
   *
   * <p>The relative left-to-right order of direct members is preserved from the sequence of calls
   * to {@link #add} and {@link #addAll}. Since the traversal {@link Order} controls whether direct
   * members appear before or after transitive ones, the interleaving of {@link #add}/{@link
   * #addAll} with {@link #addTransitive} does not matter.
   *
   * @param elements the sequence of items to add; must not be null
   * @return the builder
   */
  @CanIgnoreReturnValue
  public NestedSetBuilder<E> addAll(Iterable<? extends E> elements) {
    Preconditions.checkNotNull(elements);
    if (items == null) {
      int n = Iterables.size(elements);
      if (n == 0) {
        return this; // avoid allocating an empty set
      }
      items = CompactHashSet.createWithExpectedSize(n);
    }
    Iterables.addAll(items, elements);
    return this;
  }

  /**
   * Adds a nested set as a transitive member to the set to be built.
   *
   * <p>The relative left-to-right order of transitive members is preserved from the sequence of
   * calls to {@link #addTransitive}. Since the traversal {@link Order} controls whether direct
   * members appear before or after transitive ones, the interleaving of {@link #add}/{@link
   * #addAll} with {@link #addTransitive} does not matter.
   *
   * <p>The {@link Order} of the added set must be compatible with the order of this builder (see
   * {@link Order#isCompatible}). This is true even if the added set is empty. Strictly speaking, it
   * is not technically necessary that two nested sets have compatible orders for them to be
   * combined as part of one larger set. But checking for it helps readability and protects against
   * bugs. Since {@link Order#STABLE_ORDER} is compatible with everything, it effectively disables
   * the check. This can be used as an escape hatch to mix and match the set arbitrarily, including
   * sharing the set as part of multiple other larger sets that have disagreeing orders.
   *
   * <p>The relative order of the elements of an added set are preserved, unless it has duplicates
   * or overlaps with other added sets, or its order is different from that of the builder.
   *
   * @param subset the set to add as a transitive member; must not be null
   * @return the builder
   * @throws IllegalArgumentException if the order of {@code subset} is not compatible with the
   *     order of this builder
   */
  @CanIgnoreReturnValue
  @SuppressWarnings("unchecked") // Cast to NestedSet<E> is safe because NestedSet is immutable.
  public NestedSetBuilder<E> addTransitive(NestedSet<? extends E> subset) {
    Preconditions.checkNotNull(subset);
    Preconditions.checkArgument(
        order.isCompatible(subset.getOrder()),
        "Order mismatch: %s != %s",
        subset.getOrder().getStarlarkName(),
        order.getStarlarkName());
    if (!subset.isEmpty()) {
      if (transitiveSets == null) {
        transitiveSets = CompactHashSet.create();
      }
      transitiveSets.add((NestedSet<E>) subset);
    }
    return this;
  }

  /**
   * Builds the actual nested set.
   *
   * <p>This method may be called multiple times with interleaved {@link #add}, {@link #addAll} and
   * {@link #addTransitive} calls.
   */
  public NestedSet<E> build() {
    try {
      return buildInternal(InterruptStrategy.CRASH);
    } catch (InterruptedException e) {
      throw new IllegalStateException("Cannot throw with InterruptStrategy.CRASH", e);
    }
  }

  /**
   * Similar to {@link #build} except that if any subset is based on a deserialization future and an
   * interrupt is observed, {@link InterruptedException} is propagated.
   */
  public NestedSet<E> buildInterruptibly() throws InterruptedException {
    return buildInternal(InterruptStrategy.PROPAGATE);
  }

  private NestedSet<E> buildInternal(InterruptStrategy interruptStrategy)
      throws InterruptedException {
    if (isEmpty()) {
      return order.emptySet();
    }

    Set<E> direct = firstNonNull(items, ImmutableSet.of());
    Set<NestedSet<E>> transitive = firstNonNull(transitiveSets, ImmutableSet.of());

    // When there is exactly one transitive set, we can reuse it if its order matches and either
    // there are no direct members, or the only direct member equals the transitive set's singleton.
    if (transitive.size() == 1 && direct.size() <= 1) {
      NestedSet<E> candidate = getOnlyElement(transitiveSets);
      if (candidate.getOrder() == order
          && (direct.isEmpty()
              || getOnlyElement(direct).equals(candidate.getChildrenInterruptibly()))) {
        return candidate;
      }
    }

    return new NestedSet<>(order, direct, transitive, interruptStrategy);
  }

  private static final LoadingCache<ImmutableList<?>, NestedSet<?>> stableOrderImmutableListCache =
      CaffeinatedGuava.build(
          Caffeine.newBuilder()
              .initialCapacity(16)
              .weakKeys(),
          list -> new NestedSetBuilder<>(Order.STABLE_ORDER).addAll(list).build());

  /** Creates a nested set from a given list of items. */
  @SuppressWarnings("unchecked")
  public static <E> NestedSet<E> wrap(Order order, Iterable<? extends E> wrappedItems) {
    if (Iterables.isEmpty(wrappedItems)) {
      return order.emptySet();
    } else if (order == Order.STABLE_ORDER && wrappedItems instanceof ImmutableList) {
      ImmutableList<E> wrappedList = (ImmutableList<E>) wrappedItems;
      if (wrappedList.size() > 1) {
        try {
            return (NestedSet<E>) stableOrderImmutableListCache.get(wrappedList);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
      }
    }
    return new NestedSetBuilder<E>(order).addAll(wrappedItems).build();
  }

  /**
   * Creates a nested set with the given list of items as its elements.
   */
  public static <E> NestedSet<E> create(Order order, E... elems) {
    return wrap(order, ImmutableList.copyOf(elems));
  }

  /**
   * Creates an empty nested set.
   */
  public static <E> NestedSet<E> emptySet(Order order) {
    return order.emptySet();
  }

  /**
   * Creates a builder for stable order nested sets.
   */
  public static <E> NestedSetBuilder<E> stableOrder() {
    return new NestedSetBuilder<>(Order.STABLE_ORDER);
  }

  /**
   * Creates a builder for compile order nested sets.
   */
  public static <E> NestedSetBuilder<E> compileOrder() {
    return new NestedSetBuilder<>(Order.COMPILE_ORDER);
  }

  /**
   * Creates a builder for link order nested sets.
   */
  public static <E> NestedSetBuilder<E> linkOrder() {
    return new NestedSetBuilder<>(Order.LINK_ORDER);
  }

  /**
   * Creates a builder for naive link order nested sets.
   */
  public static <E> NestedSetBuilder<E> naiveLinkOrder() {
    return new NestedSetBuilder<>(Order.NAIVE_LINK_ORDER);
  }

  public static <E> NestedSetBuilder<E> fromNestedSet(NestedSet<? extends E> set) {
    return new NestedSetBuilder<E>(set.getOrder()).addTransitive(set);
  }

  /**
   * Creates a Builder with the contents of 'sets'.
   *
   * <p>If 'sets' is empty, a stable-order empty NestedSet is returned.
   */
  public static <E> NestedSetBuilder<E> fromNestedSets(Iterable<NestedSet<E>> sets) {
    NestedSet<?> firstSet = Iterables.getFirst(sets, null /* defaultValue */);
    if (firstSet == null) {
      return stableOrder();
    }
    NestedSetBuilder<E> result = new NestedSetBuilder<>(firstSet.getOrder());
    sets.forEach(result::addTransitive);
    return result;
  }
}