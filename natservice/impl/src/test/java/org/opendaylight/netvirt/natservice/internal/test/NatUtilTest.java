/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal.test;

import org.junit.Test;
import org.opendaylight.genius.mdsalutil.FlowEntityBuilder;
import org.opendaylight.netvirt.natservice.internal.NatUtil;

import org.opendaylight.yangtools.yang.common.Uint64;

public class NatUtilTest {

    @Test
    public void testFlowEntityBuilder() {
        new FlowEntityBuilder()
                .setDpnId(Uint64.valueOf("123").intern())
                .setTableId((short) 0)
                .setFlowId("ID")
                .build();
    }

    @Test
    public void testBuildFlowEntity1() {
        NatUtil.buildFlowEntity(Uint64.valueOf("123").intern(), (short) 0, "ID");
    }

    @Test
    public void testBuildFlowEntity2() {
        NatUtil.buildFlowEntity(Uint64.valueOf("123").intern(), (short) 0, Uint64.valueOf("789").intern(), "ID");
    }

}
