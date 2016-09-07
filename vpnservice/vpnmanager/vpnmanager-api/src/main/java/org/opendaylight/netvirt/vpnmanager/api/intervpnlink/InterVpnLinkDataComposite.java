/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api.intervpnlink;

import java.math.BigInteger;
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
        return this.interVpnLinkState == null ? Optional.<InterVpnLinkState.State>absent()
                                              : Optional.of(this.interVpnLinkState.getState());
    }

    public boolean isActive() {
        return isComplete() && getState().isPresent() && getState().get() == InterVpnLinkState.State.Active;
    }

    public boolean isFirstEndpoint(String endpointIp) {
        return this.interVpnLinkCfg.getFirstEndpoint().getIpAddress().getValue().equals(endpointIp);
    }

    public boolean isSecondEndpoint(String endpointIp) {
        return this.interVpnLinkCfg.getSecondEndpoint().getIpAddress().getValue().equals(endpointIp);
    }

    public boolean isIpAddrTheOtherVpnEndpoint(String ipAddr, String vpnUuid) {
        return vpnUuid.equals(getFirstEndpointVpnUuid().orNull())
                    && ipAddr.equals(getSecondEndpointIpAddr().orNull())
                || vpnUuid.equals(getSecondEndpointVpnUuid().orNull())
                     && ipAddr.equals(getFirstEndpointIpAddr().orNull() );
    }

    public Optional<String> getInterVpnLinkName() {
        return interVpnLinkCfg != null ? Optional.of(interVpnLinkCfg.getName())
                                       : interVpnLinkState != null ? Optional.of(interVpnLinkState.getInterVpnLinkName())
                                                                   : Optional.<String>absent();
    }

    public Optional<String> getFirstEndpointVpnUuid() {
        if ( this.interVpnLinkCfg == null ) {
            return Optional.<String>absent();
        }
        return Optional.of(this.interVpnLinkCfg.getFirstEndpoint().getVpnUuid().getValue());
    }

    public Optional<String> getFirstEndpointIpAddr() {
        if ( this.interVpnLinkCfg == null ) {
            return Optional.<String>absent();
        }
        return Optional.of(this.interVpnLinkCfg.getFirstEndpoint().getIpAddress().getValue());
    }

    public Optional<String> getSecondEndpointVpnUuid() {
        if ( !isComplete() ) {
            return Optional.<String>absent();
        }
        return Optional.of(this.interVpnLinkCfg.getSecondEndpoint().getVpnUuid().getValue());
    }

    public Optional<String> getSecondEndpointIpAddr() {
        if ( !isComplete() ) {
            return Optional.<String>absent();
        }
        return Optional.of(this.interVpnLinkCfg.getSecondEndpoint().getIpAddress().getValue());
    }

    public Optional<Long> getEndpointLportTag(String endpointIp) {
        if ( !isComplete() ) {
            return Optional.<Long>absent();
        }

        return isFirstEndpoint(endpointIp) ? Optional.of(this.interVpnLinkState.getFirstEndpointState().getLportTag())
                                           : Optional.of(this.interVpnLinkState.getSecondEndpointState().getLportTag());
    }

    public Optional<List<BigInteger>> getEndpointDpns(String endpointIp) {
        if ( !isComplete()) {
            return Optional.<List<BigInteger>>absent();
        }

        return isFirstEndpoint(endpointIp) ? Optional.of(this.interVpnLinkState.getFirstEndpointState().getDpId())
                                           : Optional.of(this.interVpnLinkState.getSecondEndpointState().getDpId());
    }
}
