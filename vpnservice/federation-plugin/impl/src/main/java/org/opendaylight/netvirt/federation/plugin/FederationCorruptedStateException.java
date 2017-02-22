/*
 * Copyright (c) 2017 Hewlett Packard Enterprise, Co. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.federation.plugin;

public class FederationCorruptedStateException extends Exception {

    private static final long serialVersionUID = -1577242292029134902L;

    public FederationCorruptedStateException() {
        super();
    }

    public FederationCorruptedStateException(String message, Throwable cause) {
        super(message, cause);
    }

    public FederationCorruptedStateException(String message) {
        super(message);
    }

    public FederationCorruptedStateException(Throwable cause) {
        super(cause);
    }


}
