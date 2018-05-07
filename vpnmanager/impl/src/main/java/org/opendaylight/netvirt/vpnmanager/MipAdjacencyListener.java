/*
 * Copyright (c) 2018 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.util.Locale;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;
import javax.inject.Singleton;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataTreeChangeListenerBase;
import org.opendaylight.genius.infra.ManagedNewTransactionRunner;
import org.opendaylight.genius.infra.ManagedNewTransactionRunnerImpl;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventAction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.LearntVpnVipToPortEventData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.MipAdjacency;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEvent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEventBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.learnt.vpn.vip.to.port.event.data.LearntVpnVipToPortEventKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.mip.adjacency.MipParent;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.l3vpn.rev130911.mip.adjacency.mip.parent.MipIp;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class MipAdjacencyListener
        extends AsyncClusteredDataTreeChangeListenerBase<MipIp, MipAdjacencyListener> {
    private static final Logger LOG = LoggerFactory.getLogger(MipAdjacencyListener.class);
    private final DataBroker dataBroker;
    private final ManagedNewTransactionRunner txRunner;

    @Inject
    public MipAdjacencyListener(final DataBroker dataBroker) {
        super(MipIp.class, MipAdjacencyListener.class);
        this.dataBroker = dataBroker;
        txRunner  = new ManagedNewTransactionRunnerImpl(dataBroker);
    }

    @PostConstruct
    public void start() {
        registerListener(LogicalDatastoreType.CONFIGURATION, dataBroker);
    }

    @Override
    @PreDestroy
    public void close() {
        super.close();
    }

    @Override
    protected InstanceIdentifier<MipIp> getWildCardPath() {
        return InstanceIdentifier.create(MipAdjacency.class).child(MipParent.class).child(MipIp.class);
    }

    @Override
    protected MipAdjacencyListener getDataTreeChangeListener() {
        return this;
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    @Override
    protected void update(InstanceIdentifier<MipIp> id, MipIp before, MipIp after) {
        LOG.trace("update before {} after {}", before, after);
    }

    @Override
    protected void add(InstanceIdentifier<MipIp> identifier, MipIp value) {
        LOG.trace("add {}", value);
        txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeOperTxn -> {
            String srcIpToQuery = value.getIpAddress();
            String srcInterface = identifier.firstKeyOf(MipParent.class).getPortName();
            String vpnName = identifier.firstKeyOf(MipParent.class).getVpnName();
            String macAddress = value.getMacAddress();
            createLearntVpnVipToPortEvent(dataBroker, vpnName, srcIpToQuery, srcInterface, macAddress,
                    LearntVpnVipToPortEventAction.Add, writeOperTxn);
        });
    }

    @Override
    protected void remove(InstanceIdentifier<MipIp> identifier, MipIp value) {
        LOG.trace("remove {}", value);
        txRunner.callWithNewWriteOnlyTransactionAndSubmit(writeOperTxn -> {
            String srcIpToQuery = value.getIpAddress();
            String srcInterface = identifier.firstKeyOf(MipParent.class).getPortName();
            String vpnName = identifier.firstKeyOf(MipParent.class).getVpnName();
            String macAddress = value.getMacAddress();
            createLearntVpnVipToPortEvent(dataBroker, vpnName, srcIpToQuery, srcInterface, macAddress,
                    LearntVpnVipToPortEventAction.Delete, writeOperTxn);
        });
    }

    private void createLearntVpnVipToPortEvent(DataBroker broker, String vpnName, String srcIp, String portName,
                                               String macAddress, LearntVpnVipToPortEventAction action,
                                               WriteTransaction writeOperTxn) {
        String eventId = VpnUtil.MicroTimestamp.INSTANCE.get();
        InstanceIdentifier<LearntVpnVipToPortEvent> id = InstanceIdentifier.builder(LearntVpnVipToPortEventData.class)
                .child(LearntVpnVipToPortEvent.class, new LearntVpnVipToPortEventKey(eventId)).build();
        LearntVpnVipToPortEventBuilder builder = new LearntVpnVipToPortEventBuilder().setKey(
                new LearntVpnVipToPortEventKey(eventId)).setVpnName(vpnName).setSrcFixedip(srcIp).setPortName(portName)
                .setMacAddress(macAddress.toLowerCase(Locale.getDefault())).setEventAction(action);
        if (writeOperTxn != null) {
            writeOperTxn.delete(LogicalDatastoreType.OPERATIONAL, id);
        } else {
            MDSALUtil.syncWrite(broker, LogicalDatastoreType.OPERATIONAL, id, builder.build());
        }
        LOG.info("createLearntVpnVipToPortEvent: ARP learned for fixedIp: {}, vpn {}, interface {}, mac {},"
                + "added to VpnPortipToPort DS", srcIp, vpnName, portName, macAddress);
    }

}

