/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.bgpmanager.globals;


public class BgpConfiguration {

    long asNum;
    String bgpServer = "";
    int bgpPort;
    String routerId = "";
    String neighbourIp = "";
    long neighbourAsNum = 0;
    boolean configUpdated = false;

    public BgpConfiguration() {
    }

    public String getBgpServer() {
        return bgpServer;
    }

    public void setBgpServer(String bgpServer) {
        this.bgpServer = bgpServer;
    }

    public int getBgpPort() {
        return bgpPort;
    }

    public void setBgpPort(int bgpPort) {
        this.bgpPort = bgpPort;
    }

    public long getAsNum() {
        return asNum;
    }

    public void setAsNum(long asNum) {
        this.asNum = asNum;
    }

    public String getRouterId() {
        return routerId;
    }

    public void setRouterId(String routerId) {
        this.routerId = routerId;
    }

    public String getNeighbourIp() {
        return neighbourIp;
    }

    public void setNeighbourIp(String neighbourIp) {
        this.neighbourIp = neighbourIp;
    }

    public long getNeighbourAsNum() {
        return neighbourAsNum;
    }

    public void setNeighbourAsNum(long neighbourAsNum) {
        this.neighbourAsNum = neighbourAsNum;
    }

    public void setConfigUpdated() { this.configUpdated = true; }

    public void unsetConfigUpdated() { this.configUpdated = false; }

    public boolean isConfigUpdated() { return this.configUpdated; }

    @Override
    public String toString() {
        return "BgpConfiguration{" +
            "asNum=" + asNum +
            ", bgpServer='" + bgpServer + '\'' +
            ", bgpPort=" + bgpPort +
            ", routerId='" + routerId + '\'' +
            ", neighbourIp='" + neighbourIp + '\'' +
            ", neighbourAsNum=" + neighbourAsNum +
            '}';
    }

}
