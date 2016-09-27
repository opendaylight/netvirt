/*
 * Copyright (c) 2016 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.tests.infra;

import org.eclipse.xtext.xbase.lib.Pair;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.concepts.Identifiable;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Builder of an {@link InstanceIdentifier} -> {@link Identifiable}  Pair.
 *
 * @param <T> DataObject type
 *
 * @see DataTreeIdentifierIdentifiableBuilder
 *
 * @author Michael Vorburger
 */
public interface IdentifierIdentifiableBuilder<T extends DataObject> extends Builder<Pair<InstanceIdentifier<T>, T>> {
    // TODO Use java.util.AbstractMap.SimpleEntry<K, V> -or- (TODO my other Gerrit) instead of xbase.lib.Pair
    // TODO propose this into org.opendaylight.yangtools.concepts

    InstanceIdentifier<T> identifier();

    T identifiable();

    @Override
    default Pair<InstanceIdentifier<T>, T> build() {
        return Pair.of(identifier(), identifiable());
    }
}
