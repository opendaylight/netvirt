/*
 * Copyright (c) 2016 Inocybe and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.AbstractDataChangeListener;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterface;
import org.opendaylight.yang.gen.v1.urn.huawei.params.xml.ns.yang.l3vpn.rev140815.vpn.interfaces.VpnInterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.Adjacencies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.adjacency.list.Adjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.prefix.to._interface.vpn.ids.Prefixes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VpnInterfaceOpListener extends AbstractDataChangeListener<VpnInterface> implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(VpnInterfaceOpListener.class);

    private final DataBroker broker;
    private final VpnInterfaceManager vpnInterfaceManager;

    private ListenerRegistration<DataChangeListener> opListenerRegistration;

    public VpnInterfaceOpListener(final DataBroker dataBroker, final VpnInterfaceManager vpnInterfaceManager) {
        super(VpnInterface.class);
        this.broker = dataBroker;
        this.vpnInterfaceManager = vpnInterfaceManager;
    }

    /**
     * Blueprint start method.
     */
    public void start() {
        opListenerRegistration = broker.registerDataChangeListener(LogicalDatastoreType.OPERATIONAL,
                VpnInterfaceManager.VPN_INTERFACE_IID, this, DataChangeScope.SUBTREE);
    }

    /**
     * Blueprint close method.
     */
    @Override
    public void close() throws Exception {
        if (opListenerRegistration != null) {
            opListenerRegistration.close();
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VpnInterface> identifier, VpnInterface del) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class);
        String interfaceName = key.getName();
        String vpnName = del.getVpnInstanceName();

        LOG.trace("VpnInterfaceOpListener removed: interface name {} vpnName {}", interfaceName, vpnName);
        //decrement the vpn interface count in Vpn Instance Op Data
        InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance>
                id = VpnUtil.getVpnInstanceToVpnIdIdentifier(vpnName);
        Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.to.vpn.id.VpnInstance> vpnInstance
                = VpnUtil.read(broker, LogicalDatastoreType.CONFIGURATION, id);

        if (vpnInstance.isPresent()) {
            String rd = null;
            rd = vpnInstance.get().getVrfId();
            //String rd = getRouteDistinguisher(del.getVpnInstanceName());

            VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(broker, rd);
            LOG.trace("VpnInterfaceOpListener removed: interface name {} rd {} vpnName {} in Vpn Op Instance {}",
                    interfaceName, rd, vpnName, vpnInstOp);

            if (vpnInstOp != null) {
                // Vpn Interface removed => No more adjacencies from it.
                // Hence clean up interface from vpn-dpn-interface list.
                Adjacency adjacency = del.getAugmentation(Adjacencies.class).getAdjacency().get(0);
                Optional<Prefixes> prefixToInterface = Optional.absent();
                prefixToInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                        VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                VpnUtil.getIpPrefix(adjacency.getIpAddress())));
                if (!prefixToInterface.isPresent()) {
                    prefixToInterface = VpnUtil.read(broker, LogicalDatastoreType.OPERATIONAL,
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                    VpnUtil.getIpPrefix(adjacency.getNextHopIp())));
                }
                if (prefixToInterface.isPresent()) {
                    VpnUtil.delete(broker, LogicalDatastoreType.OPERATIONAL,
                            VpnUtil.getPrefixToInterfaceIdentifier(vpnInstOp.getVpnId(),
                                    prefixToInterface.get().getIpAddress()),
                            VpnUtil.DEFAULT_CALLBACK);
                    vpnInterfaceManager.updateDpnDbs(prefixToInterface.get().getDpnId(), del.getVpnInstanceName(), interfaceName, false);
                }
                Long ifCnt = 0L;
                ifCnt = vpnInstOp.getVpnInterfaceCount();
                LOG.trace("VpnInterfaceOpListener removed: interface name {} rd {} vpnName {} Intf count {}",
                        interfaceName, rd, vpnName, ifCnt);
                if ((ifCnt != null) && (ifCnt > 0)) {
                    VpnUtil.asyncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                            VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                            VpnUtil.updateIntfCntInVpnInstOpData(ifCnt - 1, rd), VpnUtil.DEFAULT_CALLBACK);
                }
            }
        } else {
            LOG.error("rd not retrievable as vpninstancetovpnid for vpn {} is absent, trying rd as ", vpnName, vpnName);
        }
        vpnInterfaceManager.notifyTaskIfRequired(interfaceName);
    }

    @Override
    protected void update(InstanceIdentifier<VpnInterface> identifier, VpnInterface original, VpnInterface update) {
    }

    @Override
    protected void add(InstanceIdentifier<VpnInterface> identifier, VpnInterface add) {
        final VpnInterfaceKey key = identifier.firstKeyOf(VpnInterface.class);
        String interfaceName = key.getName();

        //increment the vpn interface count in Vpn Instance Op Data
        Long ifCnt = 0L;
        String rd = vpnInterfaceManager.getRouteDistinguisher(add.getVpnInstanceName());
        if(rd == null || rd.isEmpty()) rd = add.getVpnInstanceName();
        VpnInstanceOpDataEntry vpnInstOp = VpnUtil.getVpnInstanceOpData(broker, rd);
        if(vpnInstOp != null &&  vpnInstOp.getVpnInterfaceCount() != null) {
            ifCnt = vpnInstOp.getVpnInterfaceCount();
        }

        LOG.trace("VpnInterfaceOpListener add: interface name {} rd {} interface count in Vpn Op Instance {}", interfaceName, rd, ifCnt);

        VpnUtil.asyncUpdate(broker, LogicalDatastoreType.OPERATIONAL,
                VpnUtil.getVpnInstanceOpDataIdentifier(rd),
                VpnUtil.updateIntfCntInVpnInstOpData(ifCnt + 1, rd), VpnUtil.DEFAULT_CALLBACK);


    }
}