/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api.intervpnlink;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;

import com.google.common.base.Optional;

/**
 * It holds all info about an InterVpnLink, combining both configurational
 * and stateful
 */
public class InterVpnLinkDataComposite {

    private InterVpnLink interVpnLinkCfg;
    private InterVpnLinkState interVpnLinkState;

    public InterVpnLinkDataComposite(InterVpnLink iVpnLink) {
        this.interVpnLinkCfg = iVpnLink;
    }

    public InterVpnLinkDataComposite(InterVpnLinkState iVpnLinkState) {
        this.interVpnLinkState = iVpnLinkState;
    }

    public InterVpnLinkDataComposite(InterVpnLink iVpnLink, InterVpnLinkState iVpnLinkState) {
        this.interVpnLinkCfg = iVpnLink;
        this.interVpnLinkState = iVpnLinkState;
    }

    public InterVpnLink getInterVpnLinkConfig() {
        return this.interVpnLinkCfg;
    }

    public void setInterVpnLinkConfig(InterVpnLink iVpnLink) {
        this.interVpnLinkCfg = iVpnLink;
    }

    public InterVpnLinkState getInterVpnLinkState() {
        return this.interVpnLinkState;
    }

    public void setInterVpnLinkState(InterVpnLinkState iVpnLinkState) {
        this.interVpnLinkState = iVpnLinkState;
    }

    public boolean isComplete() {
        return interVpnLinkCfg != null && interVpnLinkState != null;
    }

    public Optional<InterVpnLinkState.State> getState() {
        return this.interVpnLinkState == null ? Optional.absent()
                                              : Optional.of(this.interVpnLinkState.getState());
    }

    public boolean isActive() {
        return isComplete() && getState().isPresent() && getState().get() == InterVpnLinkState.State.Active;
    }

    public boolean isFirstEndpointVpnName(String vpnName) {
        return interVpnLinkCfg != null
               && interVpnLinkCfg.getFirstEndpoint().getVpnUuid().getValue().equals(vpnName);
    }

    public boolean isSecondEndpointVpnName(String vpnName) {
        return interVpnLinkCfg != null
               && interVpnLinkCfg.getSecondEndpoint().getVpnUuid().getValue().equals(vpnName);
    }

    public boolean isFirstEndpointIpAddr(String endpointIp) {
        return interVpnLinkCfg != null
               && interVpnLinkCfg.getFirstEndpoint().getIpAddress().getValue().equals(endpointIp);
    }

    public boolean isSecondEndpointIpAddr(String endpointIp) {
        return interVpnLinkCfg != null
               &&interVpnLinkCfg.getSecondEndpoint().getIpAddress().getValue().equals(endpointIp);
    }

    public boolean isIpAddrTheOtherVpnEndpoint(String ipAddr, String vpnUuid) {
        return (vpnUuid.equals(getFirstEndpointVpnUuid().orNull())
                    && ipAddr.equals(getSecondEndpointIpAddr().orNull()))
               || ( vpnUuid.equals(getSecondEndpointVpnUuid().orNull())
                     && ipAddr.equals(getFirstEndpointIpAddr().orNull() ) );
    }

    public String getInterVpnLinkName() {
        return (interVpnLinkCfg != null) ? interVpnLinkCfg.getName() : interVpnLinkState.getInterVpnLinkName();
    }

    public Optional<String> getFirstEndpointVpnUuid() {
        if ( this.interVpnLinkCfg == null ) {
            return Optional.absent();
        }
        return Optional.of(this.interVpnLinkCfg.getFirstEndpoint().getVpnUuid().getValue());
    }

    public Optional<String> getFirstEndpointIpAddr() {
        if ( this.interVpnLinkCfg == null ) {
            return Optional.absent();
        }
        return Optional.of(this.interVpnLinkCfg.getFirstEndpoint().getIpAddress().getValue());
    }

    public Optional<String> getSecondEndpointVpnUuid() {
        if ( !isComplete() ) {
            return Optional.absent();
        }
        return Optional.of(this.interVpnLinkCfg.getSecondEndpoint().getVpnUuid().getValue());
    }

    public Optional<String> getSecondEndpointIpAddr() {
        if ( !isComplete() ) {
            return Optional.absent();
        }
        return Optional.of(this.interVpnLinkCfg.getSecondEndpoint().getIpAddress().getValue());
    }

    public Optional<Long> getEndpointLportTagByIpAddr(String endpointIp) {
        if ( !isComplete() ) {
            return Optional.absent();
        }

        return isFirstEndpointIpAddr(endpointIp) ? Optional.of(interVpnLinkState.getFirstEndpointState().getLportTag())
                                                 : Optional.of(interVpnLinkState.getSecondEndpointState().getLportTag());
    }

    public Optional<Long> getOtherEndpointLportTagByVpnName(String vpnName) {
        if ( !isComplete() ) {
            return Optional.absent();
        }

        return isFirstEndpointVpnName(vpnName) ? Optional.of(interVpnLinkState.getSecondEndpointState().getLportTag())
                                               : Optional.of(interVpnLinkState.getFirstEndpointState().getLportTag());
    }

    public List<BigInteger> getEndpointDpnsByVpnName(String vpnUuid) {
        List<BigInteger> result = new ArrayList<>();
        if ( !isComplete()) {
            return result;
        }

        return isFirstEndpointVpnName(vpnUuid) ? interVpnLinkState.getFirstEndpointState().getDpId()
                                               : interVpnLinkState.getSecondEndpointState().getDpId();
    }

    public List<BigInteger> getOtherEndpointDpnsByVpnName(String vpnUuid) {
        List<BigInteger> result = new ArrayList<>();
        if ( !isComplete()) {
            return result;
        }

        return isFirstEndpointVpnName(vpnUuid) ? interVpnLinkState.getSecondEndpointState().getDpId()
                                               : interVpnLinkState.getFirstEndpointState().getDpId();
    }

    public List<BigInteger> getEndpointDpnsByIpAddr(String endpointIp) {
        List<BigInteger> result = new ArrayList<>();
        if ( !isComplete()) {
            return result;
        }

        return isFirstEndpointIpAddr(endpointIp) ? this.interVpnLinkState.getFirstEndpointState().getDpId()
                                                 : this.interVpnLinkState.getSecondEndpointState().getDpId();
    }

    public List<BigInteger> getOtherEndpointDpnsByIpAddr(String endpointIp) {
        List<BigInteger> result = new ArrayList<>();
        if ( !isComplete()) {
            return result;
        }

        return isFirstEndpointIpAddr(endpointIp) ? this.interVpnLinkState.getSecondEndpointState().getDpId()
                                                 : this.interVpnLinkState.getFirstEndpointState().getDpId();
    }
}
