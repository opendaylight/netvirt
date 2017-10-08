/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.elan.arp.responder;

import com.google.common.base.Strings;

import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;

public final class ArpResponderInput {

    private BigInteger dpId;
    private String interfaceName;
    private String spa;
    private String sha;
    private int lportTag;
    private List<Instruction> instructions;


    private ArpResponderInput() {}

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

        public ArpResponderInput buildForInstallFlow() {

            if (input.dpId == null || Strings.isNullOrEmpty(input.interfaceName) || Strings.isNullOrEmpty(input.spa)
                    || Strings.isNullOrEmpty(input.sha) || input.lportTag == 0 || input.instructions.isEmpty()) {
                throw new AssertionError("Missing mandatory fields for ARP Responder Install Flow");
            }

            return input;
        }

        public ArpResponderInput buildForRemoveFlow() {

            if (input.dpId == null || Strings.isNullOrEmpty(input.interfaceName) || Strings.isNullOrEmpty(input.spa)
                    || input.lportTag == 0) {
                throw new AssertionError("Missing mandatory fields for ARP Responder Install Flow");
            }

            return input;
        }

        public ArpReponderInputBuilder(ArpResponderInput input) {
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
