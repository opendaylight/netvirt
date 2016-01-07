/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.impl;

import java.math.BigInteger;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.net.util.SubnetUtils;
import org.apache.commons.net.util.SubnetUtils.SubnetInfo;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.iana._if.type.rev140508.Tunnel;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.*;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.DpnEndpoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.DpnEndpointsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.DPNTEPsInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPoints;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPointsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.dpn.endpoints.dpn.teps.info.TunnelEndPointsKey;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.InterfaceType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice._interface.service.rev150602._interface.service.info.ServiceInfo;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.net.InetAddresses;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

public class ItmUtils {

    public static final String DUMMY_IP_ADDRESS = "0.0.0.0";
    public static final String TUNNEL_TYPE_VXLAN = "VXLAN";
    public static final String TUNNEL_TYPE_GRE = "GRE";

    private static final Logger LOG = LoggerFactory.getLogger(ItmUtils.class);

    public static final FutureCallback<Void> DEFAULT_CALLBACK = new FutureCallback<Void>() {
        public void onSuccess(Void result) {
            LOG.debug("Success in Datastore write operation");
        }

        public void onFailure(Throwable error) {
            LOG.error("Error in Datastore write operation", error);
        }
    };

    public static <T extends DataObject> Optional<T> read(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, DataBroker broker) {

        ReadOnlyTransaction tx = broker.newReadOnlyTransaction();

        Optional<T> result = Optional.absent();
        try {
            result = tx.read(datastoreType, path).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        return result;
    }

    public static <T extends DataObject> void asyncWrite(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, T data, DataBroker broker, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.put(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    public static <T extends DataObject> void asyncUpdate(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, T data, DataBroker broker, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.merge(datastoreType, path, data, true);
        Futures.addCallback(tx.submit(), callback);
    }

    public static <T extends DataObject> void asyncDelete(LogicalDatastoreType datastoreType,
                    InstanceIdentifier<T> path, DataBroker broker, FutureCallback<Void> callback) {
        WriteTransaction tx = broker.newWriteOnlyTransaction();
        tx.delete(datastoreType, path);
        Futures.addCallback(tx.submit(), callback);
    }

    public static String getInterfaceName(final BigInteger datapathid, final String portName, final Integer vlanId) {
        return String.format("%s:%s:%s", datapathid, portName, vlanId);
    }

    public static BigInteger getDpnIdFromInterfaceName(String interfaceName) {
        String[] dpnStr = interfaceName.split(":");
        BigInteger dpnId = new BigInteger(dpnStr[0]);
        return dpnId;
    }

    public static String getTrunkInterfaceName(String parentInterfaceName, String localHostName, String remoteHostName) {
        String trunkInterfaceName = String.format("%s:%s:%s", parentInterfaceName, localHostName, remoteHostName);
        return trunkInterfaceName;
    }

    public static InetAddress getInetAddressFromIpAddress(IpAddress ip) {
        return InetAddresses.forString(ip.getIpv4Address().getValue());
    }

    public static InstanceIdentifier<DPNTEPsInfo> getDPNTEPInstance(BigInteger dpIdKey) {
        InstanceIdentifier.InstanceIdentifierBuilder<DPNTEPsInfo> dpnTepInfoBuilder =
                        InstanceIdentifier.builder(DpnEndpoints.class).child(DPNTEPsInfo.class, new DPNTEPsInfoKey(dpIdKey));
        InstanceIdentifier<DPNTEPsInfo> dpnInfo = dpnTepInfoBuilder.build();
        return dpnInfo;
    }

    public static DPNTEPsInfo createDPNTepInfo(BigInteger dpId, List<TunnelEndPoints> endpoints) {

        return new DPNTEPsInfoBuilder().setKey(new DPNTEPsInfoKey(dpId)).setTunnelEndPoints(endpoints).build();
    }

    public static TunnelEndPoints createTunnelEndPoints(BigInteger dpnId, IpAddress ipAddress, String portName, int vlanId,
                    IpPrefix prefix, IpAddress gwAddress, String zoneName, Class<? extends TunnelTypeBase>  tunnel_type) {
        // when Interface Mgr provides support to take in Dpn Id
        return new TunnelEndPointsBuilder().setKey(new TunnelEndPointsKey(ipAddress, portName, vlanId))
                        .setSubnetMask(prefix).setGwIpAddress(gwAddress).setTransportZone(zoneName)
                        .setInterfaceName(ItmUtils.getInterfaceName(dpnId, portName, vlanId)).setTunnelType(tunnel_type).build();
    }

    public static DpnEndpoints createDpnEndpoints(List<DPNTEPsInfo> dpnTepInfo) {
        return new DpnEndpointsBuilder().setDPNTEPsInfo(dpnTepInfo).build();
    }

    public static InstanceIdentifier<Interface> buildId(String interfaceName) {
        InstanceIdentifierBuilder<Interface> idBuilder =
                InstanceIdentifier.builder(Interfaces.class).child(Interface.class, new InterfaceKey(interfaceName));
        InstanceIdentifier<Interface> id = idBuilder.build();
        return id;
    }

    public static Interface buildTunnelInterface(BigInteger dpn, String ifName, String desc, boolean enabled, Class<? extends TunnelTypeBase> tunType,
       IpAddress localIp, IpAddress remoteIp, IpAddress gatewayIp) {
       InterfaceBuilder builder = new InterfaceBuilder().setKey(new InterfaceKey(ifName)).setName(ifName)
       .setDescription(desc).setEnabled(enabled).setType(Tunnel.class);
       ParentRefs parentRefs = new ParentRefsBuilder().setDatapathNodeIdentifier(dpn).build();
       builder.addAugmentation(ParentRefs.class, parentRefs);
       IfTunnel tunnel = new IfTunnelBuilder().setTunnelDestination(remoteIp).setTunnelGateway(gatewayIp).setTunnelSource(localIp)
       .setTunnelInterfaceType( tunType).build();
       builder.addAugmentation(IfTunnel.class, tunnel);
       return builder.build();
    }

    public static List<DPNTEPsInfo> getTunnelMeshInfo(DataBroker dataBroker) {
        List<DPNTEPsInfo> dpnTEPs= null ;

        // Read the EndPoint Info from the operational database
        InstanceIdentifierBuilder<DpnEndpoints> depBuilder = InstanceIdentifier.builder( DpnEndpoints.class) ;
        InstanceIdentifier<DpnEndpoints> deps = depBuilder.build() ;
        Optional<DpnEndpoints> dpnEps = ItmUtils.read(LogicalDatastoreType.CONFIGURATION, deps, dataBroker);
        if( dpnEps.isPresent()) {
           DpnEndpoints tn= dpnEps.get() ;
           dpnTEPs = tn.getDPNTEPsInfo() ;
           LOG.debug( "Read from CONFIGURATION datastore - No. of Dpns " , dpnTEPs.size() );
        }else
            LOG.debug( "No Dpn information in CONFIGURATION datastore "  );
         return dpnTEPs ;
    }
}
