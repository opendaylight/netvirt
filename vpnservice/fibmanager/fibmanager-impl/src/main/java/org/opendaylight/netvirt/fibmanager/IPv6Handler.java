/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;
import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.FlowEntity;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.genius.mdsalutil.MatchInfo;
import org.opendaylight.genius.mdsalutil.MetaDataUtil;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationEth;
import org.opendaylight.genius.mdsalutil.actions.ActionMoveSourceDestinationIpv6;
import org.opendaylight.genius.mdsalutil.actions.ActionNxLoadInPort;
import org.opendaylight.genius.mdsalutil.actions.ActionNxResubmit;
import org.opendaylight.genius.mdsalutil.actions.ActionSetFieldEthernetSource;
import org.opendaylight.genius.mdsalutil.actions.ActionSetIcmpv6Type;
import org.opendaylight.genius.mdsalutil.actions.ActionSetSourceIpv6;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.interfaces.IMdsalApiManager;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIcmpv6;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv6Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchMetadata;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.MacAddress;

@Singleton
public class IPv6Handler {
    private final IMdsalApiManager mdsalManager;

    @Inject
    public IPv6Handler(final IMdsalApiManager mdsalManager) {
        this.mdsalManager = mdsalManager;
    }

    public void installPing6ResponderFlowEntry(BigInteger dpnId, long vpnId, String routerInternalIp,
            MacAddress routerMac, long label, int addOrRemove) {

        List<MatchInfo> matches = new ArrayList<>();
        matches.add(MatchIpProtocol.ICMPV6);
        matches.add(new MatchMetadata(MetaDataUtil.getVpnIdMetadata(vpnId), MetaDataUtil.METADATA_MASK_VRFID));
        matches.add(new MatchIcmpv6((short) 128, (short) 0));
        matches.add(MatchEthernetType.IPV6);
        matches.add(new MatchIpv6Destination(routerInternalIp));

        List<ActionInfo> actionsInfos = new ArrayList<>();
        // Set Eth Src and Eth Dst
        actionsInfos.add(new ActionMoveSourceDestinationEth());
        actionsInfos.add(new ActionSetFieldEthernetSource(routerMac));

        // Move Ipv6 Src to Ipv6 Dst
        actionsInfos.add(new ActionMoveSourceDestinationIpv6());
        actionsInfos.add(new ActionSetSourceIpv6(new Ipv6Prefix(routerInternalIp)));

        // Set the ICMPv6 type to 129 (echo reply)
        actionsInfos.add(new ActionSetIcmpv6Type((short) 129));
        actionsInfos.add(new ActionNxLoadInPort(BigInteger.ZERO));
        actionsInfos.add(new ActionNxResubmit(NwConstants.L3_FIB_TABLE));
        List<InstructionInfo> instructions = new ArrayList<>();
        instructions.add(new InstructionApplyActions(actionsInfos));

        int priority = FibConstants.DEFAULT_FIB_FLOW_PRIORITY + FibConstants.DEFAULT_IPV6_PREFIX_LENGTH;
        String flowRef = FibUtil.getFlowRef(dpnId, NwConstants.L3_FIB_TABLE, label, priority);

        FlowEntity flowEntity = MDSALUtil.buildFlowEntity(dpnId, NwConstants.L3_FIB_TABLE, flowRef, priority, flowRef,
                0, 0, NwConstants.COOKIE_VM_FIB_TABLE, matches, instructions);

        if (addOrRemove == NwConstants.ADD_FLOW) {
            mdsalManager.installFlow(flowEntity);
        } else {
            mdsalManager.removeFlow(flowEntity);
        }
    }
}
