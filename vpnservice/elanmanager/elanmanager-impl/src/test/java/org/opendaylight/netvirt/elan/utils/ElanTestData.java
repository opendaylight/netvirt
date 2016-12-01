/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.utils;

import org.opendaylight.genius.interfacemanager.globals.InterfaceInfo;
import org.opendaylight.genius.itm.globals.ITMConstants;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.netvirt.elan.ElanException;
import org.opendaylight.netvirt.elan.utils.ElanUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.PhysAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.Table;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.TableKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.Flow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.ElanDpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesList;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.ElanDpnInterfacesListKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfacesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstanceBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterfaceBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.List;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;

public class ElanTestData {


    public static final PhysAddress elan222Vm1Mac = new PhysAddress("00:00:00:00:22:01");
    public static final PhysAddress elan222Vm2Mac = new PhysAddress("00:00:00:00:22:02");
    public static final PhysAddress elan222Vm3Mac = new PhysAddress("00:00:00:00:22:03");


    public static ElanUtils elanUtils;

	/////////////////////////////////////////////
    //  Statics for building test instances   //
    ////////////////////////////////////////////
    public static ElanTestData elan222 =
            new ElanTestData("elan222", 222L, 30L,
                             new BigInteger[] { ElanTestTopo.dpn1Id, ElanTestTopo.dpn2Id },
                             Arrays.asList(createElanInterface("elan222", ElanTestTopo.dpn11Iface, elan222Vm1Mac),
                                           createElanInterface("elan222", ElanTestTopo.dpn12Iface, elan222Vm2Mac),
                                           createElanInterface("elan222", ElanTestTopo.dpn21Iface, elan222Vm3Mac)),
                             Arrays.asList(createDpnInterfaces(ElanTestTopo.dpn1Id, ElanTestTopo.dpn11IfaceName,
                                                                                    ElanTestTopo.dpn12IfaceName),
                                           createDpnInterfaces(ElanTestTopo.dpn2Id, ElanTestTopo.dpn21IfaceName)));

    public static void setElanUtils(ElanUtils elanUtils) {
    	ElanTestData.elanUtils = elanUtils;
    }

    private static final ElanInterface createElanInterface(String elanName, Interface iface, PhysAddress mac) {
        ElanInterfaceBuilder builder = new ElanInterfaceBuilder().setElanInstanceName(elanName).setName(iface.getName());
        if ( mac != null ) {
            builder.setStaticMacEntries(Arrays.asList(mac));
        }
        return builder.build();
    }

    private static final DpnInterfaces createDpnInterfaces(BigInteger dpId, String... ifacesNames) {
        return new DpnInterfacesBuilder().setKey(new DpnInterfacesKey(dpId))
                                         .setInterfaces(Arrays.asList(ifacesNames))
                                         .build();
    }

    //////////////////////////////////
    //  Attributes ///////////////////
    //////////////////////////////////
    String elanName;
    BigInteger[] dpnIDs;
    ElanInstance elanInstance;
    List<ElanInterface> elanInterfaces;
    ElanDpnInterfacesList elanDpnIfacesList;
    List<DpnInterfaces> elanDpns;
    HashMap<String, ElanInterface> ifaceNameXElanInterfaceMap = new HashMap<String, ElanInterface>();
    HashMap<String, FlowId> smacFlowIDs = new HashMap<String, FlowId>();

    //////////////////////////////////
    //  Constructor //////////////////
    //////////////////////////////////
    public ElanTestData(String elanName, long elanTag, long macTimeout, BigInteger[] dpnIDs,
                        List<ElanInterface> elanInterfaces, List<DpnInterfaces> dpnInterfaces) {
        elanInstance =  new ElanInstanceBuilder().setElanTag(elanTag)
                                                 .setElanInstanceName(elanName)
                                                 .setDescription("test elan")
                                                 .setMacTimeout(1111L)
                                                 .build();
        this.elanName = elanName;
        this.elanInterfaces = elanInterfaces;
        this.elanDpns = dpnInterfaces;
        this.dpnIDs = dpnIDs;


        this.elanDpnIfacesList =
                new ElanDpnInterfacesListBuilder().setDpnInterfaces(dpnInterfaces).setElanInstanceName(elanName).build();

        // Building the map  interfaceName -> ElanInterface
        for ( ElanInterface elanIface : elanInterfaces ) {
            ifaceNameXElanInterfaceMap.put(elanIface.getName(), elanIface);
        }
    }


    //////////////////////////////////
    //  Getters    ///////////////////
    //////////////////////////////////
    public String getElanInstanceName() {
        return this.elanName;
    }

    public Long getElanTag() {
        return this.elanInstance.getElanTag();
    }

    public Long getMacTimeout() {
        return this.elanInstance.getMacTimeout();
    }

    public ElanInstance getElanInstance() {
        return elanInstance;
    }
    
    public ElanInterface getElanInterface(String infName) {
    	return elanInterfaces.stream().filter( s -> s.getName().equals(infName)).findFirst().get();
    }

    public ElanDpnInterfacesList getElanDpnInterfacesList() {
        return elanDpnIfacesList;
    }

    public InstanceIdentifier<ElanInstance> getElanInstanceConfigurationDataPath() {
        return ElanUtils.getElanInstanceConfigurationDataPath(getElanInstanceName());
    }

    public String getMacAddress(ElanInterface elanIf) {
        PhysAddress mac = elanIf.getStaticMacEntries().get(0);
        return ( mac != null ) ? mac.getValue() : null;
    }

    public String getMacAddress(String ifName) {
        ElanInterface elanInterface = ifaceNameXElanInterfaceMap.get(ifName);
        return getMacAddress(elanInterface);
    }


    public InstanceIdentifier<ElanDpnInterfacesList> getElanDpnInterfacesListIId() {
        return InstanceIdentifier.builder(ElanDpnInterfaces.class)
                                 .child(ElanDpnInterfacesList.class,
                                        new ElanDpnInterfacesListKey(getElanInstanceName()))
                                 .build();
    }


    //////////////////////////////////
    //  SMAC Table ///////////////////
    //////////////////////////////////

    public InstanceIdentifier<Flow> getSmacFlowIId(String ifaceName) {
        InterfaceInfo ifaceInfo = ElanTestTopo.getInterfaceInfo(ifaceName);
        ElanInterface elanInterface = ifaceNameXElanInterfaceMap.get(ifaceName);
        PhysAddress mac = elanInterface.getStaticMacEntries().get(0);
        StringBuilder sb =
                new StringBuilder(String.valueOf(NwConstants.ELAN_SMAC_TABLE)).append(getElanTag())
                                                                                .append(ifaceInfo.getDpId())
                                                                                .append(ifaceInfo.getInterfaceTag())
                                                                                .append(mac.getValue() );
        NodeKey nodeKey = new NodeKey(new NodeId(ifaceInfo.getDpId().toString()));
        return InstanceIdentifier.builder(Nodes.class).child(Node.class, nodeKey).augmentation(FlowCapableNode.class)
                .child(Table.class, new TableKey(NwConstants.ELAN_SMAC_TABLE))
                .child(Flow.class, new FlowKey(new FlowId(sb.toString()))).build();
    }

    public FlowEntity getSmacFlowEntity(String ifaceName) {
        InterfaceInfo ifaceInfo = ElanTestTopo.getInterfaceInfo(ifaceName);
        ElanInterface elanInterface = ifaceNameXElanInterfaceMap.get(ifaceName);
        String macAddress = elanInterface.getStaticMacEntries() != null
                                      ? elanInterface.getStaticMacEntries().get(0).getValue() : null;
        return elanUtils.buildKnownSmacFlow(elanInstance, ifaceInfo, getMacTimeout(), macAddress);
    }

    //////////////////////////////////
    //  Internal Tunnel Table ////////
    //////////////////////////////////
    public InstanceIdentifier<Flow> getInternalTunnelTableFlowIId(String ifaceName) {
        InterfaceInfo ifaceInfo = ElanTestTopo.getInterfaceInfo(ifaceName);
        NodeKey nodeKey = new NodeKey(new NodeId(ifaceInfo.getDpId().toString()));
        FlowKey TunnelTermflowKey = new FlowKey( new FlowId(String.valueOf( NwConstants.INTERNAL_TUNNEL_TABLE
                                                            + ifaceInfo.getInterfaceTag())));
        return InstanceIdentifier.builder(Nodes.class).child(Node.class, nodeKey).augmentation(FlowCapableNode.class)
                                                      .child(Table.class, new TableKey(NwConstants.INTERNAL_TUNNEL_TABLE))
                                                      .child(Flow.class, TunnelTermflowKey).build();
    }

    public Flow getInternalTunnelTableFlow(String ifaceName) {
        InterfaceInfo ifaceInfo = ElanTestTopo.getInterfaceInfo(ifaceName);
        String dpId = ifaceInfo.getDpId().toString();
        int lportTag = ifaceInfo.getInterfaceTag();
        String flowId  = ElanUtils.getIntTunnelTableFlowRef(NwConstants.INTERNAL_TUNNEL_TABLE, lportTag);
        Flow flow = MDSALUtil.buildFlowNew(NwConstants.INTERNAL_TUNNEL_TABLE,
                                           flowId,
                                           5,
                                           String.format("%s:%d","ITM Flow Entry ", lportTag),
                                           0,
                                           0,
                                           ITMConstants.COOKIE_ITM.add(BigInteger.valueOf(lportTag)),
                                           ElanUtils.getTunnelIdMatchForFilterEqualsLPortTag(lportTag),
                                           elanUtils.getInstructionsInPortForOutGroup(ifaceName));
        return flow;

    }

    //////////////////////////////////
    //  DMAC Table ///////////////////
    //////////////////////////////////
    public InstanceIdentifier<Flow> getDmacFlowIId(String ifaceName) {
        InterfaceInfo ifaceInfo = ElanTestTopo.getInterfaceInfo(ifaceName);
        String dpId = ifaceInfo.getDpId().toString();
        String macAddress = getMacAddress(ifaceName);
        NodeKey nodeKey = new NodeKey(new NodeId(dpId));
        StringBuilder flowId = new StringBuilder(NwConstants.ELAN_DMAC_TABLE).append(getElanInstanceName())
                                                                               .append(dpId)
                                                                               .append(ifaceInfo.getInterfaceTag())
                                                                               .append(macAddress);

        FlowKey localDmacflowKey = new FlowKey( new FlowId( flowId.toString() ) );
        InstanceIdentifier<Flow> localDmacFlowIId =
                InstanceIdentifier.builder(Nodes.class)
                                  .child(Node.class, nodeKey)
                                  .augmentation(FlowCapableNode.class)
                                  .child(Table.class, new TableKey(NwConstants.ELAN_DMAC_TABLE))
                                  .child(Flow.class, localDmacflowKey).build();
        return localDmacFlowIId;
    }

    public Flow getLocalDmacFlow(String ifaceName) {
        InterfaceInfo ifaceInfo = ElanTestTopo.getInterfaceInfo(ifaceName);
        String macAddr = getMacAddress(ifaceName);
        Flow flow = elanUtils.buildLocalDmacFlowEntry(getElanTag(), ifaceInfo.getDpId(),
                                                      ifaceName, macAddr, /*displayName*/getElanInstanceName(),
                                                      ifaceInfo.getInterfaceTag());
        return flow;
    }

    public Flow getRemoteDmacFlow(BigInteger remoteDpnId, String ifaceName) {
        InterfaceInfo ifaceInfo = ElanTestTopo.getInterfaceInfo(ifaceName);
        ElanInterface elanInterface = ifaceNameXElanInterfaceMap.get(ifaceName);
        String macAddress = getMacAddress(elanInterface);
        Flow flow = null;
		try {
			flow = elanUtils.buildRemoteDmacFlowEntry(remoteDpnId, ifaceInfo.getDpId(), ifaceInfo.getInterfaceTag(),
			                                               getElanTag(), macAddress, /*displayName*/getElanInstanceName(), elanInstance);
		} catch (ElanException e) {
			e.printStackTrace();
		}
        return flow;
    }

}
