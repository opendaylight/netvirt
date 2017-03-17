/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.thrift.client;

import com.google.common.collect.ImmutableBiMap;
import java.util.Map;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.qbgpConstants;

public class BgpRouterException extends Exception {
    public static final int BGP_ERR_INITED = 101;
    public static final int BGP_ERR_NOT_INITED = 102;
    public static final int BGP_ERR_IN_ITER = 103;

    // the following consts are server-dictated. do not modify
    public static final int BGP_ERR_FAILED = qbgpConstants.BGP_ERR_FAILED;
    public static final int BGP_ERR_ACTIVE = qbgpConstants.BGP_ERR_ACTIVE;
    public static final int BGP_ERR_INACTIVE = qbgpConstants.BGP_ERR_INACTIVE;
    public static final int BGP_ERR_NOT_ITER = qbgpConstants.BGP_ERR_NOT_ITER;
    public static final int BGP_ERR_PARAM = qbgpConstants.BGP_ERR_PARAM;

    private static final Map<Integer, String> MESSAGES = ImmutableBiMap.<Integer, String>builder()
            .put(BGP_ERR_INITED, "(" + BGP_ERR_INITED + ") Attempt to reinitialize BgpRouter thrift client")
            .put(BGP_ERR_NOT_INITED, "(" + BGP_ERR_NOT_INITED + ") BgpRouter thrift client was not initialized")
            .put(BGP_ERR_FAILED, "(" + BGP_ERR_FAILED + ") Error reported by BGP, check qbgp.log")
            .put(BGP_ERR_ACTIVE, "(" + BGP_ERR_ACTIVE + ") Attempt to start router instance when already active")
            .put(BGP_ERR_INACTIVE, "(" + BGP_ERR_INACTIVE + ") Router instance is not active")
            .put(BGP_ERR_IN_ITER,
                    "(" + BGP_ERR_IN_ITER + ") Attempt to start route iteration when already in the middle of one")
            .put(BGP_ERR_NOT_ITER, "(" + BGP_ERR_NOT_ITER + ") Route iteration not initialized")
            .put(BGP_ERR_PARAM, "(" + BGP_ERR_PARAM + ") Parameter validation or Unknown error")
            .build();

    private final int errcode;

    public BgpRouterException(int cause) {
        errcode = cause;
    }

    public int getErrorCode() {
        return errcode;
    }

    public String toString() {
        String message = MESSAGES.get(errcode);
        if (message != null) {
            return message;
        }
        return "(" + errcode + ") Unknown error";
    }
}
