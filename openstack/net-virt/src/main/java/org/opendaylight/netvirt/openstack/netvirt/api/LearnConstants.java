/*
 * Copyright (c) 2016 NEC Corporation and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.openstack.netvirt.api;

import java.math.BigInteger;

public class LearnConstants {
    public enum NxmOfFieldType {
        NXM_OF_IN_PORT(0x0000, 0, 2, 16),
        NXM_OF_ETH_DST(0x0000, 1, 6, 48),
        NXM_OF_ETH_SRC(0x0000, 2, 6, 48),
        NXM_NX_TUN_ID(0x001, 16, 8, 64);

        long hexType;
        long flowModHeaderLen;

        NxmOfFieldType(long vendor, long field, long length, long flowModHeaderLen) {
            hexType = nxmHeader(vendor, field, length);
            this.flowModHeaderLen = flowModHeaderLen;
        }

        private static long nxmHeader(long vendor, long field, long length) {
            return ((vendor) << 16) | ((field) << 9) | (length);
        }

        public String getHexType() {
            return String.valueOf(hexType);
        }

        public String getFlowModHeaderLen() {
            return String.valueOf(flowModHeaderLen);
        }
    }

    public enum LearnFlowModsType {
        MATCH_FROM_FIELD, MATCH_FROM_VALUE, COPY_FROM_FIELD, COPY_FROM_VALUE, OUTPUT_TO_PORT;
    }
}
