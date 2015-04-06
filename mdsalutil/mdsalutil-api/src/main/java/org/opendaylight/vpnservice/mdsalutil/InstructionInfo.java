/*
 * Copyright (c) 2013 Ericsson AB.  All rights reserved.
 *
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;

public class InstructionInfo implements Serializable {
    private static final long serialVersionUID = 1L;

    private final InstructionType m_instructionType;
    private long[] m_alInstructionValues;
    private BigInteger[] m_alBigInstructionValues;
    private List<ActionInfo> m_actionInfos;

    // This constructor should be used incase of clearAction
    public InstructionInfo(InstructionType instructionType) {
        m_instructionType = instructionType;
    }

    public InstructionInfo(InstructionType instructionType, long[] instructionValues) {
        m_instructionType = instructionType;
        m_alInstructionValues = instructionValues;
    }

    public InstructionInfo(InstructionType instructionType, BigInteger[] instructionValues) {
        m_instructionType = instructionType;
        m_alBigInstructionValues = instructionValues;
    }

    public InstructionInfo(InstructionType instructionType, List<ActionInfo> actionInfos) {
        m_instructionType = instructionType;
        m_actionInfos = actionInfos;
    }

    public Instruction buildInstruction(int instructionKey) {
        return m_instructionType.buildInstruction(this, instructionKey);
    }

    public InstructionType getInstructionType() {
        return m_instructionType;
    }

    public long[] getInstructionValues() {
        return m_alInstructionValues;
    }

    public BigInteger[] getBigInstructionValues() {
        return m_alBigInstructionValues;
    }

    public List<ActionInfo> getActionInfos() {
        return m_actionInfos;
    }

    public void setInstructionValues(long[] m_alInstructionValues) {
        this.m_alInstructionValues = m_alInstructionValues;
    }

}