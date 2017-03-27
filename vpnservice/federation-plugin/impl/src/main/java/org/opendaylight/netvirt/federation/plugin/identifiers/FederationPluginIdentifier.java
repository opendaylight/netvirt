/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.identifiers;

import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * {@link DataObject} hierarchical instance identifiers.
 *
 * @param <T>
 *            dataObject
 * @param <P>
 *            parent dataObject
 * @param <R>
 *            root dataObject
 */
public interface FederationPluginIdentifier<T extends DataObject, P extends DataObject, R extends DataObject> {

    InstanceIdentifier<T> getInstanceIdentifier();

    InstanceIdentifier<P> getParentInstanceIdentifier();

    InstanceIdentifier<R> getSubtreeInstanceIdentifier();

}
