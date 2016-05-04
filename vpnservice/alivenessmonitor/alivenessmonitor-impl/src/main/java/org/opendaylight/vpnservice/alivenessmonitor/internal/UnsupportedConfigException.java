/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.alivenessmonitor.internal;

/**
 * Exception indicating the config provided is not supported currently
 *
 *
 */
public class UnsupportedConfigException extends Exception {
    private static final long serialVersionUID = 1L;

    public UnsupportedConfigException(String message){
        super(message);
    }
}
