/*
 * Copyright (c) 2015 - 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.interfacemgr.globals;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.vpnservice.mdsalutil.FlowInfoKey;
import org.opendaylight.vpnservice.mdsalutil.GroupInfoKey;
import org.opendaylight.vpnservice.mdsalutil.MDSALUtil;
import org.opendaylight.vpnservice.mdsalutil.MatchFieldType;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.vpnservice.mdsalutil.MetaDataUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.Interfaces;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.Interface;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfo;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfoBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.ServicesInfoKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.interfacemgr.rev150331.IfL2vlan;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServices;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServicesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.ServiceTypeFlowBased;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.StypeOpenflow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.StypeOpenflowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.servicebinding.rev151015.service.bindings.services.info.BoundServicesKey;

import com.google.common.base.Optional;

public class InterfaceServiceUtil {

    public static ServicesInfo buildServiceInfo(String serviceName, short serviceIndex, int servicePriority,
            BigInteger cookie, List<Instruction> instructions) {
        List<BoundServices>  boundService = new ArrayList<BoundServices>();
        boundService.add(new BoundServicesBuilder().setServicePriority((short)servicePriority).setServiceName(serviceName).build());
        return new ServicesInfoBuilder().setBoundServices(boundService).setKey(new ServicesInfoKey(serviceName)).build();
    }

    public static BoundServices getBoundServices(String serviceName, short servicePriority, int flowPriority,
                                                 BigInteger cookie, List<Instruction> instructions) {
        StypeOpenflowBuilder augBuilder = new StypeOpenflowBuilder().setFlowCookie(cookie).setFlowPriority(flowPriority).setInstruction(instructions);
        return new BoundServicesBuilder().setKey(new BoundServicesKey(servicePriority))
                .setServiceName(serviceName).setServicePriority(servicePriority)
                .setServiceType(ServiceTypeFlowBased.class).addAugmentation(StypeOpenflow.class, augBuilder.build()).build();
    }

    public static ServicesInfo buildServiceInfo(String serviceName, short serviceIndex, int servicePriority,
            BigInteger cookie) {
        List<BoundServices>  boundService = new ArrayList<BoundServices>();
        boundService.add(new BoundServicesBuilder().setServicePriority((short)servicePriority).setServiceName(serviceName).build());
        return new ServicesInfoBuilder().setBoundServices(boundService).setKey(new ServicesInfoKey(serviceName)).build();
    }

    public static List<MatchInfo> getMatchInfoForVlanLPort(BigInteger dpId, long portNo, long vlanId, boolean isVlanTransparent) {
        List<MatchInfo> matches = new ArrayList<MatchInfo>();
        matches.add(new MatchInfo(MatchFieldType.in_port, new BigInteger[] {dpId, BigInteger.valueOf(portNo)}));
        if (vlanId != 0 && !isVlanTransparent) {
            matches.add(new MatchInfo(MatchFieldType.vlan_vid, new long[] { vlanId }));
        }
        return matches;
    }

    public static short getVlanId(String interfaceName, DataBroker broker) {
        InstanceIdentifier<Interface> id = InstanceIdentifier.builder(Interfaces.class)
                .child(Interface.class, new InterfaceKey(interfaceName)).build();
        Optional<Interface> ifInstance = MDSALUtil.read(LogicalDatastoreType.CONFIGURATION, id, broker);
        if (ifInstance.isPresent()) {
            IfL2vlan vlanIface =ifInstance.get().getAugmentation(IfL2vlan.class);
            short vlanId = vlanIface.getVlanId() == null ? 0 : vlanIface.getVlanId().getValue().shortValue();
            return vlanId;
        }
        return -1;
    }

    public static Set<Object> getStatRequestKeys(BigInteger dpId, short tableId, List<MatchInfo> matches, String flowId, long groupId) {
        Set<Object> statRequestKeys = new HashSet<Object>();
        statRequestKeys.add(getFlowStatisticsKey(dpId, tableId, matches, flowId));
        statRequestKeys.add(getGroupStatisticsKey(dpId, groupId));
        return statRequestKeys;
    }

    public static GroupInfoKey getGroupStatisticsKey(BigInteger dpId, long groupId) {
        return new GroupInfoKey(dpId, groupId);
    }

    public static FlowInfoKey getFlowStatisticsKey(BigInteger dpId, short tableId, List<MatchInfo> matches, String flowId) {
        return new FlowInfoKey(dpId, tableId, MDSALUtil.buildMatches(matches), flowId);
    }

    public static List<MatchInfo> getLPortDispatcherMatches(short serviceIndex, int interfaceTag) {
        List<MatchInfo> mkMatches = new ArrayList<MatchInfo>();
        mkMatches.add(new MatchInfo(MatchFieldType.metadata, new BigInteger[] {
                 MetaDataUtil.getMetaDataForLPortDispatcher(interfaceTag, serviceIndex),
                 MetaDataUtil.getMetaDataMaskForLPortDispatcher() }));
        return mkMatches;
    }

}
