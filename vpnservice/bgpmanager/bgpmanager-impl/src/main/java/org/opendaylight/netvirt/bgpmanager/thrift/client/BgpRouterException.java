/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.bgpmanager.thrift.client;

import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.opendaylight.netvirt.bgpmanager.thrift.gen.qbgpConstants;

public class BgpRouterException extends Exception {
    private static final long serialVersionUID = 1L;

    public static final int BGP_ERR_INITED = 101;
    public static final int BGP_ERR_NOT_INITED = 102;
    public static final int BGP_ERR_IN_ITER = 103;
    public static final int BGP_ERR_COMMON_FAILURE = 2;


    // the following consts are server-dictated. do not modify
    public static final int BGP_ERR_FAILED = qbgpConstants.BGP_ERR_FAILED;
    public static final int BGP_ERR_ACTIVE = qbgpConstants.BGP_ERR_ACTIVE;
    public static final int BGP_ERR_INACTIVE = qbgpConstants.BGP_ERR_INACTIVE;
    public static final int BGP_ERR_NOT_ITER = qbgpConstants.BGP_ERR_NOT_ITER;
    public static final int BGP_ERR_PARAM = qbgpConstants.BGP_ERR_PARAM;
    public static final int BGP_ERR_NOT_SUPPORTED = qbgpConstants.BGP_ERR_NOT_SUPPORTED;

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
            .put(BGP_ERR_NOT_SUPPORTED, "(" + BGP_ERR_NOT_SUPPORTED + ") Operation not supported")
            .build();

    private final int errcode;
    private final Function functionCode;

    public enum Function {
        DEFAULT(ImmutableMap.of()),
        SET_PEER_SECRET(ImmutableMap.of(
                    BGP_ERR_FAILED, "(" + BGP_ERR_FAILED + ") Attempt to set the MD5 secret of an unknown peer",
                    BGP_ERR_PARAM, "(" + BGP_ERR_PARAM + ") Attempt to set an invalid MD5 secret"));

        private final Map<Integer, String> messageMap;

        Function(Map<Integer, String> messages) {
            messageMap = messages;
        } // ctor

        public Map<Integer, String> messages() {
            return messageMap;
        } // messages getter
    } // enum Function

    public BgpRouterException(BgpRouterException.Function func, int cause) {
        functionCode = func;
        errcode = cause;
    } // public ctor

    public BgpRouterException(int cause) {
        this(Function.DEFAULT, cause);
    }

    public int getErrorCode() {
        return errcode;
    }

    public Function getFunctionCode() {
        return functionCode;
    } // getter getFunctionCode

    @Override
    public String toString() {
        String message = functionCode.messages().get(errcode);
        if (message == null) { // there is no function specific message
            message = MESSAGES.get(errcode);
        }
        if (message != null) {
            return message;
        }
        return "(" + errcode + ") Unknown error";
    }
}
