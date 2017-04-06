/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin.creators;

import java.util.Collection;

import org.opendaylight.controller.md.sal.binding.api.DataTreeModification;
import org.opendaylight.yangtools.yang.binding.DataObject;

/**
 * Create {@link DataTreeModification} collection from parent subtree.
 *
 * @param <T>
 *            dataObject
 * @param <P>
 *            parent dataObject
 */
public interface FederationPluginModificationCreator<T extends DataObject, P extends DataObject> {

    Collection<DataTreeModification<T>> createDataTreeModifications(P parentDataObject);

}
