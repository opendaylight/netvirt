/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import org.opendaylight.genius.mdsalutil.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;

import com.google.common.base.Optional;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.VpnInstanceOpDataEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.vpn.instance.op.data.vpn.instance.op.data.entry.VpnToDpnList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.ArrayList;


public class ExternalNetworkListener extends AbstractDataChangeListener<Networks> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExternalNetworkListener.class);
    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker broker;
    private IMdsalApiManager mdsalManager;

    public ExternalNetworkListener (final DataBroker db) {
        super(Networks.class);
        broker = db;
        //registerListener(db);
    }

    @Override
    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            } catch (final Exception e) {
                LOG.error("Error when cleaning up DataChangeListener.", e);
            }
            listenerRegistration = null;
        }
        LOG.info("ExternalNetwork Listener Closed");
    }

    private void registerListener(final DataBroker db) {
        try {
            listenerRegistration = db.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION,
                    getWildCardPath(), ExternalNetworkListener.this, AsyncDataBroker.DataChangeScope.SUBTREE);
        } catch (final Exception e) {
            LOG.error("External Network DataChange listener registration fail!", e);
            throw new IllegalStateException("External Network registration Listener failed.", e);
        }
    }

    private InstanceIdentifier<Networks> getWildCardPath() {
        return InstanceIdentifier.create(ExternalNetworks.class).child(Networks.class);
    }

    public void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    @Override
    protected void add(final InstanceIdentifier<Networks> identifier,
                       final Networks nw) {
        LOG.trace("External Network add mapping method - key: " + identifier + ", value=" + nw );
        processExternalNwAdd(identifier, nw);
    }

    @Override
    protected void remove(InstanceIdentifier<Networks> identifier, Networks nw) {
        LOG.trace("External Network remove mapping method - key: " + identifier + ", value=" + nw );
        processExternalNwDel(identifier, nw);
    }

    @Override
    protected void update(InstanceIdentifier<Networks> identifier, Networks original, Networks update) {
        LOG.trace("External Network update mapping method - key: " + identifier + ", original=" + original + ", update=" + update );
        //check if a new router has been added or an already existing router has been deleted from the external nw to router association
        List<Uuid> oldRtrs = original.getRouterIds();
        List<Uuid> newRtrs = update.getRouterIds();
        if (oldRtrs != newRtrs) {
            //handle both addition and removal of routers
            for (Uuid rtr : newRtrs) {
                if (oldRtrs.contains(rtr)) {
                    oldRtrs.remove(rtr);
                } else {
                    // new router case
                    //Routers added need to have the corresponding default Fib entry added to the switches in the router
                    String routerId = rtr.getValue();
                    addOrDelDefFibRouteToSNAT(routerId, true);

                }
            }

            //Routers removed need to have the corresponding default Fib entry removed from the switches in the router
            for (Uuid rtr : oldRtrs) {
                String routerId = rtr.getValue();
                addOrDelDefFibRouteToSNAT(routerId, false);
            }
        }
    }

    private void processExternalNwAdd(final InstanceIdentifier<Networks> identifier,
                                      final Networks network) {
        LOG.trace("Add event - key: {}, value: {}", identifier, network);
        List<Uuid> routerList = network.getRouterIds();

        if(routerList == null) {
            LOG.debug("No routers associated with external network {}", identifier);
            return;
        }

        for(Uuid router: routerList) {
            String routerId = router.getValue();
            addOrDelDefFibRouteToSNAT(routerId, true);
        }
    }

    private void processExternalNwDel(final InstanceIdentifier<Networks> identifier,
                                      final Networks network) {
        LOG.trace("Add event - key: {}, value: {}", identifier, network);
        List<Uuid> routerList = network.getRouterIds();

        for(Uuid router: routerList) {
            String routerId = router.getValue();
            addOrDelDefFibRouteToSNAT(routerId, false);
        }
    }

    private void addOrDelDefFibRouteToSNAT(String routerId, boolean create) {
        //Router ID is used as the internal VPN's name, hence the vrf-id in VpnInstance Op DataStore
        InstanceIdentifier<VpnInstanceOpDataEntry> id = NatUtil.getVpnInstanceOpDataIdentifier(routerId);
        Optional<VpnInstanceOpDataEntry> vpnInstOp = NatUtil.read(broker, LogicalDatastoreType.OPERATIONAL, id);
        if (vpnInstOp.isPresent()) {
            List<VpnToDpnList> dpnListInVpn = vpnInstOp.get().getVpnToDpnList();
            for (VpnToDpnList dpn : dpnListInVpn) {
                BigInteger dpnId = dpn.getDpnId();
                long vpnId = NatUtil.readVpnId(broker, vpnInstOp.get().getVrfId());
                if (create) {
                    installDefNATRouteInDPN(dpnId, vpnId);
                } else {
                    removeDefNATRouteInDPN(dpnId, vpnId);
                }
            }
        }
    }

    private FlowEntity buildDefNATFlowEntity(BigInteger dpId, long vpnId) {

        InetAddress defaultIP = null;

        try {
            defaultIP = InetAddress.getByName("0.0.0.0");

        } catch (UnknownHostException e) {
            LOG.error("UnknowHostException in buildDefNATFlowEntity. Failed  to build FIB Table Flow for Default Route to NAT table ");
            return null;
        }

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(new MatchInfo(MatchFieldType.eth_type,
                new long[] { 0x0800L }));

        //add match for default route "0.0.0.0/0"
        //matches.add(new MatchInfo(MatchFieldType.ipv4_src, new long[] {
        //        NatUtil.getIpAddress(defaultIP.getAddress()), 0 }));

        //add match for vrfid
        matches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                BigInteger.valueOf(vpnId), MetaDataUtil.METADATA_MASK_VRFID }));

        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionInfo(InstructionType.goto_table, new long[] { NwConstants.PSNAT_TABLE }));

        String flowRef = NatUtil.getFlowRef(dpId, NwConstants.L3_FIB_TABLE, defaultIP);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpId, NwConstants.L3_FIB_TABLE, flowRef,
                NatConstants.DEFAULT_DNAT_FLOW_PRIORITY, flowRef, 0, 0,
                NwConstants.COOKIE_DNAT_TABLE, matches, instructions);

        return flowEntity;


    }

    private void installDefNATRouteInDPN(BigInteger dpnId, long vpnId) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if(flowEntity == null) {
            LOG.error("Flow entity received is NULL. Cannot proceed with installation of Default NAT flow");
            return;
        }
        mdsalManager.installFlow(flowEntity);
    }

    private void removeDefNATRouteInDPN(BigInteger dpnId, long vpnId) {
        FlowEntity flowEntity = buildDefNATFlowEntity(dpnId, vpnId);
        if(flowEntity == null) {
            LOG.error("Flow entity received is NULL. Cannot proceed with installation of Default NAT flow");
            return;
        }
        mdsalManager.removeFlow(flowEntity);
    }


}