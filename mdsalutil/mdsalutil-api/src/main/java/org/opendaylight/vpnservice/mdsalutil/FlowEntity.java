/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowCookie;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.tables.table.FlowKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.FlowModFlags;

public class FlowEntity extends AbstractSwitchEntity {
    private static final long serialVersionUID = 1L;

    private short m_shTableId;
    private String m_sFlowId;
    private int m_nPriority;
    private String m_sFlowName;
    private int m_nIdleTimeOut;
    private int m_nHardTimeOut;
    private BigInteger m_biCookie;
    private List<MatchInfo> m_listMatchInfo;
    private List<InstructionInfo> m_listInstructionInfo;

    private boolean m_bStrictFlag;
    private boolean m_bSendFlowRemFlag;

    private transient FlowBuilder m_flowBuilder;

    public FlowEntity(BigInteger dpnId) {
        super(dpnId);
    }

    @Override
    public String toString() {
        return "FlowEntity [m_shTableId=" + m_shTableId + ", m_sFlowId=" + m_sFlowId + ", m_nPriority=" + m_nPriority
                + ", m_sFlowName=" + m_sFlowName + ", m_nIdleTimeOut=" + m_nIdleTimeOut + ", m_nHardTimeOut="
                + m_nHardTimeOut + ", m_biCookie=" + m_biCookie + ", m_listMatchInfo=" + m_listMatchInfo
                + ", m_listInstructionInfo=" + m_listInstructionInfo + ", m_bStrictFlag=" + m_bStrictFlag
                + ", m_bSendFlowRemFlag=" + m_bSendFlowRemFlag + ", toString()=" + super.toString() + "]";
    }

    public BigInteger getCookie() {
        return m_biCookie;
    }

    public String getFlowId() {
        return m_sFlowId;
    }

    public String getFlowName() {
        return m_sFlowName;
    }

    public int getHardTimeOut() {
        return m_nHardTimeOut;
    }

    public int getIdleTimeOut() {
        return m_nIdleTimeOut;
    }

    public List<InstructionInfo> getInstructionInfoList() {
        return m_listInstructionInfo;
    }

    public List<MatchInfo> getMatchInfoList() {
        return m_listMatchInfo;
    }

    public int getPriority() {
        return m_nPriority;
    }

    public boolean getSendFlowRemFlag() {
        return m_bSendFlowRemFlag;
    }

    public boolean getStrictFlag() {
        return m_bStrictFlag;
    }

    public short getTableId() {
        return m_shTableId;
    }

    public void setCookie(BigInteger biCookie) {
        m_biCookie = biCookie;
        m_flowBuilder = null;
    }

    public FlowBuilder getFlowBuilder() {
        if (m_flowBuilder == null) {
            m_flowBuilder = new FlowBuilder();

            m_flowBuilder.setKey(new FlowKey(new FlowId(getFlowId())));

            m_flowBuilder.setTableId(getTableId());
            m_flowBuilder.setPriority(getPriority());
            m_flowBuilder.setFlowName(getFlowName());
            m_flowBuilder.setIdleTimeout(getIdleTimeOut());
            m_flowBuilder.setHardTimeout(getHardTimeOut());
            m_flowBuilder.setCookie(new FlowCookie(getCookie()));
            m_flowBuilder.setMatch(MDSALUtil.buildMatches(getMatchInfoList()));
            m_flowBuilder.setInstructions(MDSALUtil.buildInstructions(getInstructionInfoList()));

            m_flowBuilder.setStrict(getStrictFlag());
            // TODO Fix Me
            //m_flowBuilder.setResyncFlag(getResyncFlag());
            if (getSendFlowRemFlag()) {
                m_flowBuilder.setFlags(new FlowModFlags(false, false, false, false, true));
            }

            m_flowBuilder.setBarrier(false);
            m_flowBuilder.setInstallHw(true);
        }

        return m_flowBuilder;
    }

    public void setFlowId(String sFlowId) {
        m_sFlowId = sFlowId;
        if (m_flowBuilder != null) {
            m_flowBuilder.setKey(new FlowKey(new FlowId(sFlowId)));
        }
    }

    public void setFlowName(String sFlowName) {
        m_sFlowName = sFlowName;
        m_flowBuilder = null;
    }

    public void setHardTimeOut(int nHardTimeOut) {
        m_nHardTimeOut = nHardTimeOut;
        m_flowBuilder = null;
    }

    public void setIdleTimeOut(int nIdleTimeOut) {
        m_nIdleTimeOut = nIdleTimeOut;
        m_flowBuilder = null;
    }

    public void setInstructionInfoList(List<InstructionInfo> listInstructionInfo) {
        m_listInstructionInfo = listInstructionInfo;
        m_flowBuilder = null;
    }

    public void setMatchInfoList(List<MatchInfo> listMatchInfo) {
        m_listMatchInfo = listMatchInfo;
        m_flowBuilder = null;
    }

    public void setPriority(int nPriority) {
        m_nPriority = nPriority;
        m_flowBuilder = null;
    }

    public void setSendFlowRemFlag(boolean bSendFlowRemFlag) {
        m_bSendFlowRemFlag = bSendFlowRemFlag;
        m_flowBuilder = null;
    }

    public void setStrictFlag(boolean bStrictFlag) {
        m_bStrictFlag = bStrictFlag;
        m_flowBuilder = null;
    }

    public void setTableId(short shTableId) {
        m_shTableId = shTableId;
        m_flowBuilder = null;
    }
}
