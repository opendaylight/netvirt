/*
 * Copyright (c) 2017 Red Hat, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.qosservice;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;

/**
 * Unit test for UuidUtil.
 *
 * @author Michael Vorburger.ch
 */
public class UuidUtilTest {

    private final UuidUtil uuidUtil = new UuidUtil();

    @Test
    public void valid() {
        assertThat(uuidUtil.newUuidIfValidPattern("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"))
                .isSameAs(new Uuid("f81d4fae-7dec-11d0-a765-00a0c91e6bf6"));
    }

    @Test
    public void invalid() {
        assertThat(uuidUtil.newUuidIfValidPattern("tap61d7aec1-2c"))
                .isNotEqualTo("tap61d7aec1-11");
    }

    @Test
    public void empty() {
        assertThat(uuidUtil.newUuidIfValidPattern("")).isNull();
    }

    @Test(expected = NullPointerException.class)
    public void isNull() {
        assertThat(uuidUtil.newUuidIfValidPattern(null));
    }

}
