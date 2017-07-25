/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.internal;

import com.google.common.util.concurrent.ListenableFuture;
import java.math.BigInteger;
import java.util.List;
import org.opendaylight.controller.md.sal.binding.api.DataTreeChangeListener;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.itm.op.rev160406.external.tunnel.list.ExternalTunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.group.types.rev131018.group.buckets.Bucket;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

/**
 * Class in charge of handling creations, modifications and removals of
 * ElanInterfaces.
 *
 * @see org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface
 */
public interface ElanInterfaceManager extends DataTreeChangeListener<ElanInterface> {
    void add(InstanceIdentifier<ElanInterface> identifier, ElanInterface elanInterfaceAdded);

    void addElanInterface(List<ListenableFuture<Void>> futures, ElanInterface elanInterface,
            InterfaceInfo interfaceInfo, ElanInstance elanInstance) throws ElanException;

    List<Bucket> getRemoteBCGroupBuckets(ElanInstance elanInfo, DpnInterfaces dpnInterfaces, BigInteger dpnId,
            int bucketId, long elanTag);

    void handleExternalTunnelStateEvent(ExternalTunnel externalTunnel, Interface intrf) throws ElanException;

    void handleInternalTunnelStateEvent(BigInteger srcDpId, BigInteger dstDpId) throws ElanException;

    List<ListenableFuture<Void>> handleunprocessedElanInterfaces(ElanInstance elanInstance) throws ElanException;

    void removeElanInterface(List<ListenableFuture<Void>> futures, ElanInstance elanInfo, String interfaceName,
            InterfaceInfo interfaceInfo, boolean isInterfaceStateRemoved);

    void removeEntriesForElanInterface(List<ListenableFuture<Void>> futures, ElanInstance elanInfo, InterfaceInfo
            interfaceInfo, String interfaceName, boolean isInterfaceStateRemoved, boolean isLastElanInterface);

    void setupElanBroadcastGroups(ElanInstance elanInfo, BigInteger dpnId);

    void setupEntriesForElanInterface(List<ListenableFuture<Void>> futures, ElanInstance elanInstance,
            ElanInterface elanInterface, InterfaceInfo interfaceInfo, boolean isFirstInterfaceInDpn)
            throws ElanException;

    void unbindService(String interfaceName, WriteTransaction tx);

    void updateRemoteBroadcastGroupForAllElanDpns(ElanInstance elanInfo);
}
