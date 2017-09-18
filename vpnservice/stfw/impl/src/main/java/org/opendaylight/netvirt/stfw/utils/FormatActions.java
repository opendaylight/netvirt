/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.utils;

import org.opendaylight.netvirt.stfw.simulator.ovs.OvsConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.DstChoice;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.dst.choice.grouping.dst.choice.DstNxRegCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.learn.grouping.NxLearn;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.reg.load.grouping.NxRegLoad;
import org.opendaylight.yang.gen.v1.urn.opendaylight.openflowplugin.extension.nicira.action.rev140714.nx.action.resubmit.grouping.NxResubmit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FormatActions {
    private static final Logger LOG = LoggerFactory.getLogger(FormatActions.class);

    public static void getAction(StringBuffer sb, NxRegLoad nxRegLoad) {
        sb.append("load:0x").append(nxRegLoad.getValue().toString(16)).append("->");
        DstChoice dstChoice = nxRegLoad.getDst().getDstChoice();
        if (dstChoice instanceof DstNxRegCase) {
            String regName = ((DstNxRegCase) dstChoice).getNxReg().getSimpleName();
            sb.append("NXM_NX_REG").append(regName.substring(regName.length() - 1));
        } else {
            sb.append("UNKNOWN_REG");
            LOG.debug("Unsupported destChoice for NxActionRgLoad: {}", dstChoice);
        }
        Integer regStart = nxRegLoad.getDst().getStart();
        Integer regEnd = nxRegLoad.getDst().getEnd();
        final String strStartEnd;
        if (regStart != 0 || regEnd != 31) {
            strStartEnd = "[" + regStart + ".." + regEnd + "],";
        } else {
            strStartEnd = "[],";
        }
        sb.append(strStartEnd);
    }

    public static void getAction(StringBuffer sb, NxResubmit nxResubmit) {
        sb.append("resubmit(");
        if (nxResubmit.getInPort() != null && nxResubmit.getInPort() != OvsConstants.OFPP_IN_PORT) {
            sb.append(nxResubmit.getInPort());
        }
        sb.append(",").append(nxResubmit.getTable()).append(")");
    }

    public static void getAction(StringBuffer sb, NxLearn nxLearn) {
        sb.append("learn(table=").append(nxLearn.getTableId());
        if (nxLearn.getHardTimeout() > 0) {
            sb.append(",hard_timeout=").append(nxLearn.getHardTimeout());
        }
        if (nxLearn.getIdleTimeout() > 0) {
            sb.append(",idle_timeout=").append(nxLearn.getHardTimeout());
        }
        if (nxLearn.getFinHardTimeout() > 0) {
            sb.append(",fin_hard_timeout=").append(nxLearn.getHardTimeout());
        }
        if (nxLearn.getFinIdleTimeout() > 0) {
            sb.append(",fin_idle_timeout=").append(nxLearn.getHardTimeout());
        }
        if (nxLearn.getPriority() != null) {
            sb.append(",priority=").append(nxLearn.getPriority());
        }
        sb.append(",TBD)");
        LOG.debug("TBD Learn Actions: {}", nxLearn);

    }

    public static void getAction(StringBuffer sb) {

    }
}
