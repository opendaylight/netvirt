/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.arp.responder;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;

public class ArpResponderInput {

    private BigInteger dpId;
    private String interfaceName;
    private String spa;
    private String sha;
    private int lportTag;
    private List<Instruction> instructions;

    public BigInteger getDpId() {
        return dpId;
    }

    public String getInterfaceName() {
        return interfaceName;
    }

    public String getSpa() {
        return spa;
    }

    public String getSha() {
        return sha;
    }

    public int getLportTag() {
        return lportTag;
    }

    public List<Instruction> getInstructions() {
        if (instructions == null) {
            instructions = Collections.emptyList();
        }
        return instructions;
    }

    public static class ArpReponderInputBuilder {

        private ArpResponderInput input;

        public ArpReponderInputBuilder() {
            input = new ArpResponderInput();
        }

        public ArpResponderInput build() {
            return input;
        }

        public ArpReponderInputBuilder(ArpResponderInput input) {
            super();
            this.input = input;
        }

        public ArpReponderInputBuilder setDpId(BigInteger dpId) {
            input.dpId = dpId;
            return this;
        }

        public ArpReponderInputBuilder setInterfaceName(String interfaceName) {
            input.interfaceName = interfaceName;
            return this;
        }

        public ArpReponderInputBuilder setSpa(String spa) {
            input.spa = spa;
            return this;
        }

        public ArpReponderInputBuilder setSha(String sha) {
            input.sha = sha;
            return this;
        }

        public ArpReponderInputBuilder setLportTag(int lportTag) {
            input.lportTag = lportTag;
            return this;
        }

        public ArpReponderInputBuilder setInstructions(List<Instruction> instructions) {
            input.instructions = instructions == null ? Collections.emptyList() : instructions;
            return this;
        }

    }

}
