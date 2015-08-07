/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.bgpmanager.thrift.client.globals;


public class Route {

    private int prefixlen;
    private int label;
    private String rd;
    private String prefix;
    private String nexthop;

    public Route(String rd, String prefix, int prefixlen, String nexthop, int label) {
        this.rd = rd;
        this.prefix = prefix;
        this.prefixlen = prefixlen;
        this.nexthop = nexthop;
        this.label = label;
    }

    public String getRd() {
        return this.rd;
    }

    public String getPrefix() {
        return new StringBuilder().append(this.prefix).append("/").append(this.prefixlen).toString();
    }

    public String getNexthop() {
        return this.nexthop;
    }

    public int getLabel() {
        return this.label;
    }

    public void setRd(String rd) {
        this.rd = rd;
    }

    public void setPrefix(String prefix) {
        String[] splitStr = prefix.split("/");
        this.prefix = splitStr[0];
        this.prefixlen = Integer.parseInt(splitStr[1]);
    }

    public void setNexthop(String nextHop) {
        this.nexthop = nextHop;
    }

    public void setLabel(int label) {
        this.label = label;
    }
}
