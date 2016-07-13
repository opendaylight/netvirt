/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.dhcpservice;

import com.google.common.base.Optional;
import org.opendaylight.controller.md.sal.binding.api.ClusteredDataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncClusteredDataChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfacesState;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.Subnetmaps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.Subnetmap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.subnetmaps.SubnetmapKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.ports.rev150712.ports.attributes.ports.Port;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.rev150712.Neutron;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.Subnets;
import org.opendaylight.yang.gen.v1.urn.opendaylight.neutron.subnets.rev150712.subnets.attributes.subnets.Subnet;

import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.Iterator;
import java.util.List;


public class DhcpSubnetListener extends AsyncClusteredDataChangeListenerBase<Subnet, DhcpSubnetListener>
        implements AutoCloseable {
    private DataBroker dataBroker;
    private DhcpManager dhcpManager;
    private DhcpExternalTunnelManager dhcpExternalTunnelManager;
    private static final Logger LOG = LoggerFactory.getLogger(DhcpSubnetListener.class);

    public DhcpSubnetListener(final DhcpManager dhcpManager, final DhcpExternalTunnelManager
            dhcpExternalTunnelManager, final DataBroker
                                      broker) {
        super(Subnet.class, DhcpSubnetListener.class);
        this.dhcpManager = dhcpManager;
        this.dataBroker = broker;
        this.dhcpExternalTunnelManager = dhcpExternalTunnelManager;
    }

    @Override
    protected void add(InstanceIdentifier<Subnet> identifier, Subnet add) {

    }

    @Override
    protected void remove(InstanceIdentifier<Subnet> identifier, Subnet del) {

    }

    @Override
    public void onDataChanged(AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> changeEvent) {
        super.onDataChanged(changeEvent);
    }

    @Override
    protected InstanceIdentifier<Subnet> getWildCardPath() {
        return InstanceIdentifier.create(Neutron.class).child(Subnets.class).child(Subnet.class);
    }

    @Override
    protected AsyncDataBroker.DataChangeScope getDataChangeScope() {
        return AsyncDataBroker.DataChangeScope.SUBTREE;
    }

    @Override
    protected ClusteredDataChangeListener getDataChangeListener() {
        return DhcpSubnetListener.this;
    }

    @Override
    protected void update(InstanceIdentifier<Subnet> identifier, Subnet original, Subnet update) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("DhcpSubnetListener Update : Original dhcpstatus: " + original.isEnableDhcp() + ", " +
                    "Updated dhcpstatus" + update.isEnableDhcp());
        }

        if (original.isEnableDhcp() != update.isEnableDhcp()) {
            // write api to get port list
            SubnetmapBuilder subnetmapBuilder = getSubnetMapBuilder(dataBroker, update.getUuid());
            List<Uuid> portList = subnetmapBuilder.getPortList();
            List<Uuid> directPortList = subnetmapBuilder.getDirectPortList();

            if (update.isEnableDhcp()) {
                if (null != portList) {
                    //Install Entries for neutron ports
                    installNeutronPortEntries(portList);
                }
                if (null != directPortList) {
                    //install Entries for direct ports
                    installDirectPortEntries(directPortList);
                }
            } else {
                if (null != portList) {
                    //UnInstall Entries for neutron ports
                    uninstallNeutronPortEntries(portList);
                }
                if (null != directPortList) {
                    //Uninstall Entries for direct ports
                    uninstallDirectPortEntries(directPortList);
                }
            }
        }
    }

    private void installNeutronPortEntries(List<Uuid> portList) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("DhcpSubnetListener installNeutronPortEntries : portList: " + portList);
        }
        Uuid portIntf;
        for (Iterator<Uuid> portIter = portList.iterator(); portIter.hasNext(); ) {
            portIntf = portIter.next();
            NodeConnectorId nodeConnectorId = getNodeConnectorIdForPortIntf(portIntf);
            BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
            Port port = dhcpManager.getNeutronPort(portIntf.getValue());
            String vmMacAddress = port.getMacAddress().getValue();
            //check whether any changes have happened
            if (LOG.isTraceEnabled()) {
                LOG.trace("DhcpSubnetListener installNeutronPortEntries dpId: " + dpId + "vmMacAddress :" +
                        vmMacAddress);
            }
            //install the entriesd
            dhcpManager.installDhcpEntries(dpId, vmMacAddress);
        }
    }

    private void uninstallNeutronPortEntries(List<Uuid> portList) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("DhcpSubnetListener uninstallNeutronPortEntries : portList: " + portList);
        }
        Uuid portIntf;
        for (Iterator<Uuid> portIter = portList.iterator(); portIter.hasNext(); ) {
            portIntf = portIter.next();
            NodeConnectorId nodeConnectorId = getNodeConnectorIdForPortIntf(portIntf);
            BigInteger dpId = BigInteger.valueOf(MDSALUtil.getDpnIdFromPortName(nodeConnectorId));
            Port port = dhcpManager.getNeutronPort(portIntf.getValue());
            String vmMacAddress = port.getMacAddress().getValue();
            //check whether any changes have happened
            if (LOG.isTraceEnabled()) {
                LOG.trace("DhcpSubnetListener uninstallNeutronPortEntries dpId: " + dpId + "vmMacAddress :" +
                        vmMacAddress);
            }
            //install the entries
            dhcpManager.unInstallDhcpEntries(dpId, vmMacAddress);
        }
    }

    private void installDirectPortEntries(List<Uuid> directPortList) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("DhcpSubnetListener installDirectPortEntries : directPortList: " + directPortList);
        }
        Uuid portIntf;
        for (Iterator<Uuid> directPortIter = directPortList.iterator(); directPortIter.hasNext(); ) {
            portIntf = directPortIter.next();
            Port port = dhcpManager.getNeutronPort(portIntf.getValue());
            String vmMacAddress = port.getMacAddress().getValue();
            Uuid networkId = port.getNetworkId();
            //install the entries on designated dpnId
            List<BigInteger> listOfDpns = DhcpServiceUtils.getListOfDpns(dataBroker);
            IpAddress tunnelIp = dhcpExternalTunnelManager.getTunnelIpBasedOnElan(networkId.getValue(), vmMacAddress);
            if (null == tunnelIp) {
                LOG.warn("DhcpSubnetListener installDirectPortEntries tunnelIP is null for  port {}", portIntf);
                continue;
            }
            BigInteger designatedDpnId = dhcpExternalTunnelManager.readDesignatedSwitchesForExternalTunnel
                    (tunnelIp, networkId.getValue());
            if (LOG.isTraceEnabled()) {
                LOG.trace("CR-DHCP DhcpSubnetListener update Install DIRECT vmMacAddress:" + vmMacAddress + "  " +
                        " tunnelIp: " + tunnelIp + " designatedDpnId :" + designatedDpnId + " ListOf Dpn:" +
                        listOfDpns.toString());
            }
            dhcpExternalTunnelManager.installDhcpFlowsForVms(tunnelIp, networkId.getValue(), listOfDpns,
                    designatedDpnId, vmMacAddress);

        }
    }

    private void uninstallDirectPortEntries(List<Uuid> directPortList) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("DhcpSubnetListener uninstallDirectPortEntries : directPortList: " + directPortList);
        }
        Uuid portIntf;
        for (Iterator<Uuid> directPortIter = directPortList.iterator(); directPortIter.hasNext(); ) {
            portIntf = directPortIter.next();
            Port port = dhcpManager.getNeutronPort(portIntf.getValue());
            String vmMacAddress = port.getMacAddress().getValue();
            Uuid networkId = port.getNetworkId();
            List<BigInteger> listOfDpns = DhcpServiceUtils.getListOfDpns(dataBroker);
            if (LOG.isTraceEnabled()) {
                LOG.trace("DhcpSubnetListener uninstallDirectPortEntries  vmMacAddress:" + vmMacAddress + "  " +
                        "networkId: " + networkId + " ListOf Dpn:" + listOfDpns.toString());
            }
            dhcpExternalTunnelManager.unInstallDhcpFlowsForVms(networkId.getValue(), listOfDpns, vmMacAddress);
        }
    }


    private NodeConnectorId getNodeConnectorIdForPortIntf(Uuid interfaceName) {
        if (LOG.isTraceEnabled()) {
            LOG.trace("DhcpSubnetListener getNodeConnectorIdForPortIntf  interfaceName: " + interfaceName);
        }
        NodeConnectorId nodeConnectorId = null;
        InstanceIdentifier.InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(InterfacesState.class)
                        .child(Interface.class,
                                new org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces
                                        .rev140508.interfaces.state.InterfaceKey(interfaceName.getValue()));

        InstanceIdentifier<Interface> ifStateId = idBuilder.build();

        Optional<Interface> ifStateOptional = MDSALUtil.read(LogicalDatastoreType.OPERATIONAL, ifStateId, dataBroker);
        Interface interfaceState = null;
        if (ifStateOptional.isPresent()) {
            interfaceState = ifStateOptional.get();
        }
        if (interfaceState != null) {
            List<String> ofportIds = interfaceState.getLowerLayerIf();
            nodeConnectorId = new NodeConnectorId(ofportIds.get(0));
        }
        if (LOG.isTraceEnabled()) {
            LOG.trace("DhcpSubnetListener getNodeConnectorIdForPortIntf returned nodeConnectorId :" + nodeConnectorId
                    .getValue() +
                    "for the interface :" + interfaceName);
        }
        return nodeConnectorId;
    }

    private SubnetmapBuilder getSubnetMapBuilder(DataBroker broker, Uuid subnetId){
        SubnetmapBuilder builder = null ;
        InstanceIdentifier<Subnetmap> id = InstanceIdentifier.builder(Subnetmaps.class).
                child(Subnetmap.class, new SubnetmapKey(subnetId)).build();
        try {
            ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

            Optional<Subnetmap> sn ;
            try {
                sn = tx.read(LogicalDatastoreType.CONFIGURATION, id).get();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            if (sn.isPresent()) {
                builder = new SubnetmapBuilder(sn.get());
            } else {
                builder = new SubnetmapBuilder().setKey(new SubnetmapKey(subnetId)).setId(subnetId);
            }
        } catch (Exception e) {
            LOG.error("Updation of subnetMap failed for node: {}", subnetId.getValue());
        }
        return builder;
    }

    @Override
    public void close() throws Exception {

    }
}
