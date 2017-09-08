/*
 * Copyright © 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.tests;


public class TunnelInterface {
    String src;
    String dst;
    String dstIp;
    String name;
    int lportTag;
    int portno;

    public TunnelInterface(String src, String dst, String dstIp, int lportTag, String name, int portno) {
        this.dst = dst;
        this.dstIp = dstIp;
        this.lportTag = lportTag;
        this.name = name;
        this.portno = portno;
        this.src = src;
    }
}
