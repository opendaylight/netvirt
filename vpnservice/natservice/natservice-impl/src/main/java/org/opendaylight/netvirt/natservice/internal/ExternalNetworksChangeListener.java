/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.natservice.internal;

import com.google.common.base.Optional;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.datastoreutils.AsyncDataTreeChangeListenerBase;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceInputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.GetDpidFromInterfaceOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.OdlInterfaceRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.ExternalNetworks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.IntextIpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.RouterPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.Ports;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.floating.ip.info.router.ports.ports.IpMapping;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMappingKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.ip.mapping.IpMap;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.external.networks.Networks;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.netvirt.bgpmanager.api.IBgpManager;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fib.rpc.rev160121.FibRpcService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.vpn.rpc.rev160201.VpnRpcService;

/**
 * Created by ESUMAMS on 1/21/2016.
 */
public class ExternalNetworksChangeListener extends AsyncDataTreeChangeListenerBase<Networks, ExternalNetworksChangeListener>
{
    private static final Logger LOG = LoggerFactory.getLogger( ExternalNetworksChangeListener.class);

    private ListenerRegistration<DataChangeListener> listenerRegistration;
    private final DataBroker dataBroker;
    private IMdsalApiManager mdsalManager;
    //private VpnFloatingIpHandler vpnFloatingIpHandler;
    private FloatingIPListener floatingIpListener;
    private ExternalRoutersListener externalRouterListener;
    private OdlInterfaceRpcService interfaceManager;
    private NaptManager naptManager;

    private IBgpManager bgpManager;
    private VpnRpcService vpnService;
    private FibRpcService fibService;


    private ExternalRoutersListener externalRoutersListener;

    void setMdsalManager(IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    void setInterfaceManager(OdlInterfaceRpcService interfaceManager) {
        this.interfaceManager = interfaceManager;
    }

    void setFloatingIpListener(FloatingIPListener floatingIpListener) {
        this.floatingIpListener = floatingIpListener;
    }

    void setExternalRoutersListener(ExternalRoutersListener externalRoutersListener) {
        this.externalRouterListener = externalRoutersListener;
    }

    public void setBgpManager(IBgpManager bgpManager) {
        this.bgpManager = bgpManager;
    }

    public void setNaptManager(NaptManager naptManager) {
        this.naptManager = naptManager;
    }

    public void setVpnService(VpnRpcService vpnService) {
        this.vpnService = vpnService;
    }

    public void setFibService(FibRpcService fibService) {
        this.fibService = fibService;
    }

    public void setListenerRegistration(ListenerRegistration<DataChangeListener> listenerRegistration) {
        this.listenerRegistration = listenerRegistration;
    }

    public ExternalNetworksChangeListener(final DataBroker dataBroker ) {
        super( Networks.class, ExternalNetworksChangeListener.class );
        this.dataBroker = dataBroker;
    }


    protected InstanceIdentifier<Networks> getWildCardPath() {
        return InstanceIdentifier.create(ExternalNetworks.class).child(Networks.class);
    }


    @Override
    protected void add(InstanceIdentifier<Networks> identifier, Networks networks) {

    }

    @Override
    protected ExternalNetworksChangeListener getDataTreeChangeListener() {
        return ExternalNetworksChangeListener.this;
    }

    @Override
    protected void remove(InstanceIdentifier<Networks> identifier, Networks networks) {
        if( identifier == null || networks == null || networks.getRouterIds().isEmpty() ) {
            LOG.info( "ExternalNetworksChangeListener:remove:: returning without processing since networks/identifier is null"  );
            return;
        }

        for( Uuid routerId: networks.getRouterIds() ) {
            String routerName = routerId.toString();

            InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitchInstanceIdentifier =
                    getRouterToNaptSwitchInstanceIdentifier( routerName);

            MDSALUtil.syncDelete( dataBroker, LogicalDatastoreType.OPERATIONAL, routerToNaptSwitchInstanceIdentifier );

            LOG.debug( "ExternalNetworksChangeListener:delete:: successful deletion of data in napt-switches container" );
        }
    }

    private static InstanceIdentifier<RouterToNaptSwitch> getRouterToNaptSwitchInstanceIdentifier( String routerName ) {

        return  InstanceIdentifier.builder( NaptSwitches.class )
                        .child( RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();

    }

    public void close() throws Exception {
        if (listenerRegistration != null) {
            try {
                listenerRegistration.close();
            }
            catch (final Exception e) {
                LOG.error("Error when cleaning up ExternalNetworksChangeListener.", e);
            }

            listenerRegistration = null;
        }
        LOG.debug("ExternalNetworksChangeListener Closed");
    }


    @Override
    protected void update(InstanceIdentifier<Networks> identifier, Networks original, Networks update) {
        //Check for VPN disassociation
        Uuid originalVpn = original.getVpnid();
        Uuid updatedVpn = update.getVpnid();
        if(originalVpn == null && updatedVpn != null) {
            //external network is dis-associated from L3VPN instance
            associateExternalNetworkWithVPN(update);
        } else if(originalVpn != null && updatedVpn == null) {
            //external network is associated with vpn
            disassociateExternalNetworkFromVPN(update, originalVpn.getValue());
            //Remove the SNAT entries
            removeSnatEntries(original, original.getId());
        }
    }

    private void removeSnatEntries(Networks original, Uuid networkUuid) {
        List<Uuid> routerUuids = original.getRouterIds();
        for (Uuid routerUuid : routerUuids) {
            Long routerId = NatUtil.getVpnId(dataBroker, routerUuid.getValue());
            if (routerId == NatConstants.INVALID_ID) {
                LOG.error("NAT Service : Invalid routerId returned for routerName {}", routerUuid.getValue());
                return;
            }
            List<String> externalIps = NatUtil.getExternalIpsForRouter(dataBroker,routerId);
            externalRouterListener.handleDisableSnatInternetVpn(routerUuid.getValue(), networkUuid, externalIps, false, original.getVpnid().getValue());
        }
    }

    private void associateExternalNetworkWithVPN(Networks network) {
        List<Uuid> routerIds = network.getRouterIds();
        for(Uuid routerId : routerIds) {
            //long router = NatUtil.getVpnId(dataBroker, routerId.getValue());

            InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerId.getValue());
            Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsId);
            if(!optRouterPorts.isPresent()) {
                LOG.debug("Could not read Router Ports data object with id: {} to handle associate ext nw {}", routerId, network.getId());
                continue;
            }
            RouterPorts routerPorts = optRouterPorts.get();
            List<Ports> interfaces = routerPorts.getPorts();
            for(Ports port : interfaces) {
                String portName = port.getPortName();
                BigInteger dpnId = getDpnForInterface(interfaceManager, portName);
                if(dpnId.equals(BigInteger.ZERO)) {
                    LOG.debug("DPN not found for {}, skip handling of ext nw {} association", portName, network.getId());
                    continue;
                }
                List<IpMapping> ipMapping = port.getIpMapping();
                for(IpMapping ipMap : ipMapping) {
                    String externalIp = ipMap.getExternalIp();
                    //remove all VPN related entries
                    floatingIpListener.createNATFlowEntries(dpnId, portName, routerId.getValue(), network.getId(), ipMap.getInternalIp(), externalIp);
                }
            }
        }

        // SNAT
        for(Uuid routerId : routerIds) {
            LOG.debug("NAT Service : associateExternalNetworkWithVPN() for routerId {}",  routerId);
            Uuid networkId = network.getId();
            if(networkId == null) {
                LOG.error("NAT Service : networkId is null for the router ID {}", routerId);
                return;
            }
            final String vpnName = network.getVpnid().getValue();
            if(vpnName == null) {
                LOG.error("NAT Service : No VPN associated with ext nw {} for router {}", networkId, routerId);
                return;
            }

            BigInteger dpnId = new BigInteger("0");
            InstanceIdentifier<RouterToNaptSwitch> routerToNaptSwitch = NatUtil.buildNaptSwitchRouterIdentifier(routerId.getValue());
            Optional<RouterToNaptSwitch> rtrToNapt = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, routerToNaptSwitch );
            if(rtrToNapt.isPresent()) {
                dpnId = rtrToNapt.get().getPrimarySwitchId();
            }
            LOG.debug("NAT Service : got primarySwitch as dpnId{} ", dpnId);

            Long routerIdentifier = NatUtil.getVpnId(dataBroker, routerId.getValue());
            InstanceIdentifierBuilder<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMapping> idBuilder =
                            InstanceIdentifier.builder(IntextIpMap.class).child(org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMapping.class, new org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMappingKey(routerIdentifier));
            InstanceIdentifier<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMapping> id = idBuilder.build();
            Optional<org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.intext.ip.map.IpMapping> ipMapping = MDSALUtil.read(dataBroker, LogicalDatastoreType.OPERATIONAL, id);
            if (ipMapping.isPresent()) {
                  List<IpMap> ipMaps = ipMapping.get().getIpMap();
                  for (IpMap ipMap : ipMaps) {
                      String externalIp = ipMap.getExternalIp();
                      LOG.debug("NAT Service : got externalIp as {}", externalIp);
                      LOG.debug("NAT Service : About to call advToBgpAndInstallFibAndTsFlows for dpnId {}, vpnName {} and externalIp {}", dpnId, vpnName, externalIp);
                      externalRouterListener.advToBgpAndInstallFibAndTsFlows(dpnId, NatConstants.INBOUND_NAPT_TABLE, vpnName, NatUtil.getVpnId(dataBroker, routerId.getValue()), externalIp, vpnService, fibService, bgpManager, dataBroker, LOG);
                  }
            } else {
                LOG.warn("NAT Service : No ipMapping present fot the routerId {}", routerId);
            }

            long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
            // Install 47 entry to point to 21
            if(vpnId != -1) {
                LOG.debug("NAT Service : Calling externalRouterListener installNaptPfibEntry for donId {} and vpnId {}", dpnId, vpnId);
                externalRouterListener.installNaptPfibEntry(dpnId, vpnId);
            }

        }

    }

    private void disassociateExternalNetworkFromVPN(Networks network, String vpnName) {
        List<Uuid> routerIds = network.getRouterIds();

        //long vpnId = NatUtil.getVpnId(dataBroker, vpnName);
        for(Uuid routerId : routerIds) {
            //long router = NatUtil.getVpnId(dataBroker, routerId.getValue());

            InstanceIdentifier<RouterPorts> routerPortsId = NatUtil.getRouterPortsId(routerId.getValue());
            Optional<RouterPorts> optRouterPorts = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, routerPortsId);
            if(!optRouterPorts.isPresent()) {
                LOG.debug("Could not read Router Ports data object with id: {} to handle disassociate ext nw {}", routerId, network.getId());
                continue;
            }
            RouterPorts routerPorts = optRouterPorts.get();
            List<Ports> interfaces = routerPorts.getPorts();
            for(Ports port : interfaces) {
                String portName = port.getPortName();
                BigInteger dpnId = getDpnForInterface(interfaceManager, portName);
                if(dpnId.equals(BigInteger.ZERO)) {
                    LOG.debug("DPN not found for {}, skip handling of ext nw {} disassociation", portName, network.getId());
                    continue;
                }
                List<IpMapping> ipMapping = port.getIpMapping();
                for(IpMapping ipMap : ipMapping) {
                    String externalIp = ipMap.getExternalIp();
                    floatingIpListener.removeNATFlowEntries(dpnId, portName, vpnName, routerId.getValue(), network.getId(), ipMap.getInternalIp(), externalIp);
                }
            }
        }
    }

    public static BigInteger getDpnForInterface(OdlInterfaceRpcService interfaceManagerRpcService, String ifName) {
        BigInteger nodeId = BigInteger.ZERO;
        try {
            GetDpidFromInterfaceInput
                    dpIdInput =
                    new GetDpidFromInterfaceInputBuilder().setIntfName(ifName).build();
            Future<RpcResult<GetDpidFromInterfaceOutput>>
                    dpIdOutput =
                    interfaceManagerRpcService.getDpidFromInterface(dpIdInput);
            RpcResult<GetDpidFromInterfaceOutput> dpIdResult = dpIdOutput.get();
            if (dpIdResult.isSuccessful()) {
                nodeId = dpIdResult.getResult().getDpid();
            } else {
                LOG.error("Could not retrieve DPN Id for interface {}", ifName);
            }
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Exception when getting dpn for interface {}", ifName,  e);
        }
        return nodeId;
    }

}
