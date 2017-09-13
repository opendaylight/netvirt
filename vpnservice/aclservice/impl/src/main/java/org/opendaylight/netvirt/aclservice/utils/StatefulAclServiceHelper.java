/*
 * Copyright (c) 2017, NEC Corporation and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.utils;

import java.util.ArrayList;
import java.util.List;

import org.opendaylight.genius.mdsalutil.ActionInfo;
import org.opendaylight.genius.mdsalutil.InstructionInfo;
import org.opendaylight.genius.mdsalutil.NwConstants;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack;
import org.opendaylight.genius.mdsalutil.actions.ActionNxConntrack.NxCtAction;
import org.opendaylight.genius.mdsalutil.instructions.InstructionApplyActions;
import org.opendaylight.genius.mdsalutil.instructions.InstructionGotoTable;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIp;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.aclservice.config.rev160806.AclserviceConfig;

public abstract class StatefulAclServiceHelper {

    /**
     * Returns the idle timeout based on the protocol when a ACL rule removed from the instance.
     * It will returns the timeout configured in the {@link AclserviceConfig} class.
     *
     * @param ace the ace
     * @param aclServiceUtils acl service utils
     * @return the idle time out
     */
    public static Integer getIdleTimoutForApplyStatefulChangeOnExistingTraffic(Ace ace,
            AclServiceUtils aclServiceUtils) {
        int idleTimout = AclConstants.SECURITY_GROUP_ICMP_IDLE_TIME_OUT;
        Matches matches = ace.getMatches();
        AceIp acl = (AceIp) matches.getAceType();
        Short protocol = acl.getProtocol();
        if (protocol == null) {
            return idleTimout;
        } else if (protocol == NwConstants.IP_PROT_TCP) {
            idleTimout = aclServiceUtils.getConfig().getSecurityGroupTcpIdleTimeout();
        } else if (protocol == NwConstants.IP_PROT_UDP) {
            idleTimout = aclServiceUtils.getConfig().getSecurityGroupUdpIdleTimeout();
        }
        return idleTimout;
    }

    /**
     * This method creates and returns the ct_mark instruction when a ACL rule removed from the
     * instance. This instruction will reset the ct_mark value and stops the existing traffics.
     *
     * @param filterTable the filterTable
     * @param elanId the Elan id
     * @return list of instruction
     */
    public static List<InstructionInfo> createCtMarkInstructionForNewState(Short filterTable, Long elanId) {

        List<InstructionInfo> instructions = new ArrayList<>();
        List<ActionInfo> actionsInfos = new ArrayList<>();
        List<NxCtAction> ctActionsList = new ArrayList<>();
        NxCtAction nxCtMarkClearAction = new ActionNxConntrack.NxCtMark(AclConstants.CT_MARK_NEW_STATE);
        ctActionsList.add(nxCtMarkClearAction);

        ActionNxConntrack actionNxConntrack = new ActionNxConntrack(2, 1, 0, elanId.intValue(),
            (short) 255, ctActionsList);
        actionsInfos.add(actionNxConntrack);
        instructions.add(new InstructionApplyActions(actionsInfos));
        instructions.add(new InstructionGotoTable(filterTable));

        return instructions;
    }
}
