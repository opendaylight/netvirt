/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.GoToTableCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.WriteMetadataCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ClearActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ClearActionsCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.apply.actions._case.ApplyActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.go.to.table._case.GoToTableBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.actions._case.WriteActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.actions._case.WriteActionsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.write.metadata._case.WriteMetadataBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.InstructionKey;

public enum InstructionType {
    apply_actions {
        @Override
        public Instruction buildInstruction(InstructionInfo instructionInfo, int instructionKey) {
            List<ActionInfo> mkActions = instructionInfo.getActionInfos();
            List<Action> listAction = new ArrayList <Action> ();
            int actionKey = 0 ;
            for(ActionInfo mkAction: mkActions) {
                ActionType actionType = mkAction.getActionType();
                mkAction.setActionKey(actionKey++);
                listAction.add(actionType.buildAction(mkAction));
            }
            ApplyActions applyActions = new ApplyActionsBuilder().setAction(listAction).build();
            ApplyActionsCase applyActionsCase = new ApplyActionsCaseBuilder().setApplyActions(applyActions).build();
            InstructionBuilder instructionBuilder = new InstructionBuilder();

            instructionBuilder.setInstruction(applyActionsCase);
            instructionBuilder.setKey(new InstructionKey(instructionKey));

            return instructionBuilder.build();
        }
    },

    goto_table {
        @Override
        public Instruction buildInstruction(InstructionInfo instructionInfo, int instructionKey) {
            short tableId = (short) instructionInfo.getInstructionValues()[0];

            return new InstructionBuilder()
                    .setInstruction(
                            new GoToTableCaseBuilder().setGoToTable(
                                    new GoToTableBuilder().setTableId(Short.valueOf(tableId)).build()).build())
                    .setKey(new InstructionKey(instructionKey)).build();
        }
    },

    write_actions {
        @Override
        public Instruction buildInstruction(InstructionInfo instructionInfo, int instructionKey) {
            List<ActionInfo> mkActions = instructionInfo.getActionInfos();
            List<Action> listAction = new ArrayList <Action> ();
            int actionKey = 0 ;
            for(ActionInfo mkAction: mkActions) {
                ActionType actionType = mkAction.getActionType();
                mkAction.setActionKey(actionKey++);
                listAction.add(actionType.buildAction(mkAction));
            }
            WriteActions writeActions = new WriteActionsBuilder().setAction(listAction).build();
            WriteActionsCase writeActionsCase = new WriteActionsCaseBuilder().setWriteActions(writeActions).build();
            InstructionBuilder instructionBuilder = new InstructionBuilder();

            instructionBuilder.setInstruction(writeActionsCase);
            instructionBuilder.setKey(new InstructionKey(instructionKey));

            return instructionBuilder.build();
        }
    },

    clear_actions {
        @Override
        public Instruction buildInstruction(InstructionInfo instructionInfo, int instructionKey) {
            
            ClearActionsCase clearActionsCase = new ClearActionsCaseBuilder().build();

            InstructionBuilder instructionBuilder = new InstructionBuilder();
            instructionBuilder.setInstruction(clearActionsCase);
            instructionBuilder.setKey(new InstructionKey(instructionKey));

            return instructionBuilder.build();
        }
    },

    write_metadata {
        @Override
        public Instruction buildInstruction(InstructionInfo instructionInfo, int instructionKey) {
            BigInteger[] metadataValues = instructionInfo.getBigInstructionValues();
            BigInteger metadata = metadataValues[0];
            BigInteger mask = metadataValues[1];

            return new InstructionBuilder()
                    .setInstruction(
                            new WriteMetadataCaseBuilder().setWriteMetadata(
                                    new WriteMetadataBuilder().setMetadata(metadata).setMetadataMask(mask).build())
                                    .build()).setKey(new InstructionKey(instructionKey)).build();
        }
    };

    public abstract Instruction buildInstruction(InstructionInfo instructionInfo, int instructionKey);
}
