/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.l2gw.ha.commands;

import org.opendaylight.controller.md.sal.binding.api.ReadWriteTransaction;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.concepts.Builder;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public interface IMergeCommand<T extends DataObject, BuilderType extends Builder, AugType extends DataObject> {

    public void mergeOperationalData(BuilderType dst,
                                     AugType existingData,
                                     AugType src,
                                     InstanceIdentifier<Node> nodePath);

    public void mergeConfigData(BuilderType dst,
                                AugType src,
                                InstanceIdentifier<Node> nodePath);

    public void mergeConfigUpdate(AugType existingData,
                                  AugType updated,
                                  AugType orig,
                                  InstanceIdentifier<Node> nodePath,
                                  ReadWriteTransaction tx);

    public void mergeOpUpdate(BuilderType dst,
                              AugType existingData,
                              AugType updatedSrc,
                              AugType origSrc,
                              InstanceIdentifier<Node> nodePath,
                              ReadWriteTransaction tx);

}
