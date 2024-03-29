// Copyright 2023 The Bazel Authors. All rights reserved.
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

package net.starlark.java.eval;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;

/** An immutable implementation of a {@code StarlarkList}. */
final class ImmutableStarlarkList<E> extends StarlarkList<E> {
  final Object[] elems;

  ImmutableStarlarkList(Object[] elems) {
    Preconditions.checkArgument(elems.getClass() == Object[].class);
    this.elems = elems;
  }

  @Override
  public boolean isImmutable() {
    return true;
  }

  @Override
  public boolean updateIteratorCount(int delta) {
    return false;
  }

  @Override
  public Mutability mutability() {
    return Mutability.IMMUTABLE;
  }

  @Override
  public void unsafeShallowFreeze() {
    Mutability.Freezable.checkUnsafeShallowFreezePrecondition(this);
  }

  @Override
  public ImmutableList<E> getImmutableList() {
    // Optimization: a frozen array needn't be copied.
    // If the entire array is full, we can wrap it directly.
    return Tuple.wrapImmutable(elems);
  }

  @Override
  @SuppressWarnings("unchecked")
  public E get(int i) {
    Preconditions.checkElementIndex(i, elems.length);
    return (E) elems[i]; // unchecked
  }

  @Override
  public int size() {
    return elems.length;
  }

  @Override
  public boolean contains(Object o) {
    // StarlarkList contains only valid Starlark objects (which are non-null)
    if (o == null) {
      return false;
    }
    int size = elems.length;
    for (int i = 0; i < size; i++) {
      Object elem = elems[i];
      if (o.equals(elem)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public void addElement(E element) throws EvalException {
    Starlark.checkMutable(this);
  }

  @Override
  public void addElementAt(int index, E element) throws EvalException {
    Starlark.checkMutable(this);
  }

  @Override
  public void addElements(Iterable<? extends E> elements) throws EvalException {
    Starlark.checkMutable(this);
  }

  @Override
  public void removeElementAt(int index) throws EvalException {
    Starlark.checkMutable(this);
  }

  @Override
  public void setElementAt(int index, E value) throws EvalException {
    Starlark.checkMutable(this);
  }

  @Override
  public void clearElements() throws EvalException {
    Starlark.checkMutable(this);
  }

  /** Returns a new array of class Object[] containing the list elements. */
  @Override
  public Object[] toArray() {
    return elems.length != 0 ? Arrays.copyOf(elems, elems.length, Object[].class) : EMPTY_ARRAY;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T[] toArray(T[] a) {
    if (a.length < elems.length) {
      return (T[]) Arrays.copyOf(elems, elems.length, a.getClass());
    } else {
      System.arraycopy(elems, 0, a, 0, elems.length);
      Arrays.fill(a, elems.length, a.length, null);
      return a;
    }
  }

  @Override
  Object[] elems() {
    return elems;
  }
}
