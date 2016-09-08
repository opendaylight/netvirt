/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager;

import java.net.InetAddress;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.datastoreutils.DataStoreJobCoordinator;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.alivenessmonitor.rev160411.AlivenessMonitorService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.NeutronVpnPortipPortData;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPort;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.neutron.vpn.portip.port.data.VpnPortipToPortKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ArpScheduler extends AsyncDataTreeChangeListenerBase<VpnPortipToPort,ArpScheduler> {
    private static final Logger LOG = LoggerFactory.getLogger(ArpScheduler.class);
    private final DataBroker dataBroker;
    private final OdlInterfaceRpcService interfaceRpc;
    private final AlivenessMonitorService alivenessManager;

    public ArpScheduler(final DataBroker dataBroker, final OdlInterfaceRpcService interfaceRpc,AlivenessMonitorService alivenessManager) {
        super(VpnPortipToPort.class, ArpScheduler.class);
        this.dataBroker = dataBroker;
        this.interfaceRpc = interfaceRpc;
        this.alivenessManager = alivenessManager;
    }

    public void start() {
        LOG.info("{} start", getClass().getSimpleName());
        registerListener(LogicalDatastoreType.OPERATIONAL, dataBroker);
    }

    @Override
    protected InstanceIdentifier<VpnPortipToPort> getWildCardPath() {
        return InstanceIdentifier.create(NeutronVpnPortipPortData.class).child(VpnPortipToPort.class);
    }

    public static InstanceIdentifier<VpnPortipToPort> getVpnPortipToPortInstanceOpDataIdentifier(String ip,String vpnName) {
        return InstanceIdentifier.builder(NeutronVpnPortipPortData.class)
                .child(VpnPortipToPort.class, new VpnPortipToPortKey(ip,vpnName)).build();
    }

    @Override
    protected ArpScheduler getDataTreeChangeListener() {
        return this;
    }

    @Override
    protected void update(InstanceIdentifier<VpnPortipToPort> id, VpnPortipToPort value,
            VpnPortipToPort dataObjectModificationAfter) {
        remove(id, value);
        add(id, dataObjectModificationAfter);
    }

    @Override
    protected void add(InstanceIdentifier<VpnPortipToPort> identifier, VpnPortipToPort value) {
        try {
            InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
            MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
            String vpnName =  value.getVpnName();
            String interfaceName =  value.getPortName();
            Boolean islearnt = value.isLearnt();
            if (islearnt) {
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(),vpnName),
                        new ArpAddCacheTask(srcInetAddr, srcMacAddress, vpnName,interfaceName, dataBroker,alivenessManager));
            }
        } catch (Exception e) {
            LOG.error("Error in deserializing packet {} with exception {}", value, e);
        }
    }

    @Override
    protected void remove(InstanceIdentifier<VpnPortipToPort> key, VpnPortipToPort value) {
        try {
            InetAddress srcInetAddr = InetAddress.getByName(value.getPortFixedip());
            MacAddress srcMacAddress = MacAddress.getDefaultInstance(value.getMacAddress());
            String vpnName =  value.getVpnName();
            String interfaceName =  value.getPortName();
            Boolean islearnt = value.isLearnt();
            MacEntry macEntry = new MacEntry(vpnName, srcMacAddress, srcInetAddr, interfaceName);
            if (islearnt) {
                Long monitorId = AlivenessMonitorUtils.getMonitorIdFromInterface(macEntry);
                if(monitorId != null)
                {
                    AlivenessMonitorUtils.stopArpMonitoring(alivenessManager, monitorId);
                }
                DataStoreJobCoordinator coordinator = DataStoreJobCoordinator.getInstance();
                coordinator.enqueueJob(buildJobKey(srcInetAddr.toString(),vpnName),
                        new ArpRemoveCacheTask(dataBroker,srcInetAddr.getHostAddress(), vpnName,interfaceName));
            }
        } catch (Exception e) {
            LOG.error("Error in deserializing packet {} with exception {}", value, e);
        }
    }

    static String buildJobKey(String ip, String vpnName) {
        return new StringBuilder(ArpConstants.ARPJOB).append(ip).append(vpnName).toString();
    }
}
