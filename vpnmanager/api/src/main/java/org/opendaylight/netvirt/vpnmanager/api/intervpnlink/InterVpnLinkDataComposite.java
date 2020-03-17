/*
 * Copyright © 2016, 2017 Ericsson India Global Services Pvt Ltd. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.vpnmanager.api.intervpnlink;

import static java.util.Collections.emptyList;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.eclipse.jdt.annotation.Nullable;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * It holds all info about an InterVpnLink, combining both configurational
 * and stateful.
 */
public class InterVpnLinkDataComposite {

    private static final Logger LOG = LoggerFactory.getLogger(InterVpnLinkDataComposite.class);

    private InterVpnLink interVpnLinkCfg;
    private InterVpnLinkState interVpnLinkState;

    public InterVpnLinkDataComposite(InterVpnLink interVpnLink) {
        this.interVpnLinkCfg = interVpnLink;
    }

    public InterVpnLinkDataComposite(InterVpnLinkState interVpnLinkState) {
        this.interVpnLinkState = interVpnLinkState;
    }

    public InterVpnLinkDataComposite(InterVpnLink interVpnLink, InterVpnLinkState interVpnLinkState) {
        this.interVpnLinkCfg = interVpnLink;
        this.interVpnLinkState = interVpnLinkState;
    }

    public InterVpnLink getInterVpnLinkConfig() {
        return this.interVpnLinkCfg;
    }

    public void setInterVpnLinkConfig(InterVpnLink interVpnLink) {
        this.interVpnLinkCfg = interVpnLink;
    }

    public InterVpnLinkState getInterVpnLinkState() {
        return this.interVpnLinkState;
    }

    public void setInterVpnLinkState(InterVpnLinkState interVpnLinkState) {
        this.interVpnLinkState = interVpnLinkState;
    }

    public boolean isComplete() {
        return interVpnLinkCfg != null && interVpnLinkState != null
                 && interVpnLinkState.getFirstEndpointState() != null
                 && interVpnLinkState.getSecondEndpointState() != null;
    }

    public Optional<InterVpnLinkState.State> getState() {
        return this.interVpnLinkState == null ? Optional.empty()
                                              : Optional.ofNullable(this.interVpnLinkState.getState());
    }

    public boolean isActive() {
        return isComplete() && getState().isPresent() && getState().get() == InterVpnLinkState.State.Active;
    }

    public boolean stepsOnDpn(BigInteger dpnId) {
        return getFirstEndpointDpns().contains(Uint64.valueOf(dpnId))
                    || getSecondEndpointDpns().contains(Uint64.valueOf(dpnId));
    }

    public boolean isBgpRoutesLeaking() {
        return this.interVpnLinkCfg != null && this.interVpnLinkCfg.isBgpRoutesLeaking();
    }

    public boolean isStaticRoutesLeaking() {
        return this.interVpnLinkCfg != null && this.interVpnLinkCfg.isStaticRoutesLeaking();
    }

    public boolean isConnectedRoutesLeaking() {
        return this.interVpnLinkCfg != null && this.interVpnLinkCfg.isConnectedRoutesLeaking();
    }

    public boolean isFirstEndpointVpnName(String vpnName) {
        return interVpnLinkCfg != null
               && interVpnLinkCfg.getFirstEndpoint().getVpnUuid().getValue().equals(vpnName);
    }

    public boolean isSecondEndpointVpnName(String vpnName) {
        return interVpnLinkCfg != null
               && interVpnLinkCfg.getSecondEndpoint().getVpnUuid().getValue().equals(vpnName);
    }

    public boolean isVpnLinked(String vpnName) {
        return isFirstEndpointVpnName(vpnName) || isSecondEndpointVpnName(vpnName);
    }

    public boolean isFirstEndpointIpAddr(String endpointIp) {
        return interVpnLinkCfg != null
               && interVpnLinkCfg.getFirstEndpoint().getIpAddress().getValue().equals(endpointIp);
    }

    public boolean isSecondEndpointIpAddr(String endpointIp) {
        return interVpnLinkCfg != null
               && interVpnLinkCfg.getSecondEndpoint().getIpAddress().getValue().equals(endpointIp);
    }

    public boolean isIpAddrTheOtherVpnEndpoint(String ipAddr, String vpnUuid) {
        return (vpnUuid.equals(getFirstEndpointVpnUuid().orElse(null))
                    && ipAddr.equals(getSecondEndpointIpAddr().orElse(null)))
               || (vpnUuid.equals(getSecondEndpointVpnUuid().orElse(null))
                     && ipAddr.equals(getFirstEndpointIpAddr().orElse(null)));
    }

    @Nullable
    public String getInterVpnLinkName() {
        return (interVpnLinkCfg != null) ? interVpnLinkCfg.getName() : interVpnLinkState.getInterVpnLinkName();
    }

    public Optional<String> getFirstEndpointVpnUuid() {
        if (this.interVpnLinkCfg == null) {
            return Optional.empty();
        }
        return Optional.of(this.interVpnLinkCfg.getFirstEndpoint().getVpnUuid().getValue());
    }

    public Optional<String> getFirstEndpointIpAddr() {
        if (this.interVpnLinkCfg == null) {
            return Optional.empty();
        }
        return Optional.of(this.interVpnLinkCfg.getFirstEndpoint().getIpAddress().getValue());
    }

    public Optional<Uint32> getFirstEndpointLportTag() {
        return (!isComplete() || this.interVpnLinkState.getFirstEndpointState().getLportTag() == null)
                   ? Optional.empty()
                   : Optional.of(this.interVpnLinkState.getFirstEndpointState().getLportTag());
    }

    public List<Uint64> getFirstEndpointDpns() {
        return (!isComplete() || this.interVpnLinkState.getFirstEndpointState().getDpId() == null)
                   ? emptyList()
                   : this.interVpnLinkState.getFirstEndpointState().getDpId();
    }

    public Optional<String> getSecondEndpointVpnUuid() {
        if (!isComplete()) {
            return Optional.empty();
        }
        return Optional.of(this.interVpnLinkCfg.getSecondEndpoint().getVpnUuid().getValue());
    }

    public Optional<String> getSecondEndpointIpAddr() {
        if (!isComplete()) {
            return Optional.empty();
        }
        return Optional.of(this.interVpnLinkCfg.getSecondEndpoint().getIpAddress().getValue());
    }

    public Optional<Uint32> getSecondEndpointLportTag() {
        return (!isComplete() || this.interVpnLinkState.getSecondEndpointState().getLportTag() == null)
            ? Optional.empty()
            : Optional.of(this.interVpnLinkState.getSecondEndpointState().getLportTag());
    }

    public List<Uint64> getSecondEndpointDpns() {
        return (!isComplete() || this.interVpnLinkState.getSecondEndpointState().getDpId() == null)
                    ? emptyList()
                    : this.interVpnLinkState.getSecondEndpointState().getDpId();
    }

    @Nullable
    public String getVpnNameByIpAddress(String endpointIpAddr) {
        if (!isFirstEndpointIpAddr(endpointIpAddr) && !isSecondEndpointIpAddr(endpointIpAddr)) {
            LOG.debug("Endpoint IpAddress {} does not participate in InterVpnLink {}",
                      endpointIpAddr, getInterVpnLinkName());
            return null;
        }
        return isFirstEndpointIpAddr(endpointIpAddr) ? getFirstEndpointVpnUuid().orElse(null)
                                                     : getSecondEndpointVpnUuid().orElse(null);
    }

    @Nullable
    public String getOtherEndpoint(String vpnUuid) {
        if (!isFirstEndpointVpnName(vpnUuid) && !isSecondEndpointVpnName(vpnUuid)) {
            LOG.debug("VPN {} does not participate in InterVpnLink {}", vpnUuid, getInterVpnLinkName());
            return null;
        }

        return isFirstEndpointVpnName(vpnUuid) ? getSecondEndpointIpAddr().orElse(null)
                                               : getFirstEndpointIpAddr().orElse(null);
    }

    @Nullable
    public String getOtherVpnNameByIpAddress(String endpointIpAddr) {
        if (!isFirstEndpointIpAddr(endpointIpAddr) && !isSecondEndpointIpAddr(endpointIpAddr)) {
            LOG.debug("Endpoint IpAddress {} does not participate in InterVpnLink {}",
                      endpointIpAddr, getInterVpnLinkName());
            return null;
        }
        return isFirstEndpointIpAddr(endpointIpAddr) ? getSecondEndpointVpnUuid().orElse(null)
                                                     : getFirstEndpointVpnUuid().orElse(null);
    }

    public Optional<Uint32> getEndpointLportTagByVpnName(String vpnName) {
        if (!isComplete()) {
            return Optional.empty();
        }

        return isFirstEndpointVpnName(vpnName) ? Optional.of(interVpnLinkState.getFirstEndpointState().getLportTag())
                                               : Optional.of(interVpnLinkState.getSecondEndpointState().getLportTag());
    }

    public Optional<Uint32> getEndpointLportTagByIpAddr(String endpointIp) {
        if (!isComplete()) {
            return Optional.empty();
        }

        return isFirstEndpointIpAddr(endpointIp)
                    ? Optional.ofNullable(interVpnLinkState.getFirstEndpointState().getLportTag())
                    : Optional.ofNullable(interVpnLinkState.getSecondEndpointState().getLportTag());
    }

    public Optional<Uint32> getOtherEndpointLportTagByVpnName(String vpnName) {
        if (!isComplete()) {
            return Optional.empty();
        }

        return isFirstEndpointVpnName(vpnName) ? Optional.of(interVpnLinkState.getSecondEndpointState().getLportTag())
                                               : Optional.of(interVpnLinkState.getFirstEndpointState().getLportTag());
    }

    @Nullable
    public String getOtherVpnName(String vpnName) {
        if (!isFirstEndpointVpnName(vpnName) && !isSecondEndpointVpnName(vpnName)) {
            LOG.debug("VPN {} does not participate in InterVpnLink {}", vpnName, getInterVpnLinkName());
            return null;
        }

        return isFirstEndpointVpnName(vpnName) ? getSecondEndpointVpnUuid().orElse(null)
                                               : getFirstEndpointVpnUuid().orElse(null);
    }

    @Nullable
    public String getOtherEndpointIpAddr(String vpnUuid) {
        if (!isFirstEndpointVpnName(vpnUuid) && !isSecondEndpointVpnName(vpnUuid)) {
            LOG.debug("VPN {} does not participate in InterVpnLink {}", vpnUuid, getInterVpnLinkName());
            return null;
        }

        return isFirstEndpointVpnName(vpnUuid) ? getSecondEndpointIpAddr().orElse(null)
                                               : getFirstEndpointIpAddr().orElse(null);
    }

    @Nullable
    public String getEndpointIpAddr(String vpnUuid) {
        if (!isFirstEndpointVpnName(vpnUuid) && !isSecondEndpointVpnName(vpnUuid)) {
            LOG.debug("VPN {} does not participate in InterVpnLink {}", vpnUuid, getInterVpnLinkName());
            return null;
        }

        return isFirstEndpointVpnName(vpnUuid) ? getFirstEndpointIpAddr().orElse(null)
                                               : getSecondEndpointIpAddr().orElse(null);
    }

    public List<Uint64> getEndpointDpnsByVpnName(String vpnUuid) {
        if (!isComplete()) {
            return emptyList();
        }

        List<Uint64> dpns = isFirstEndpointVpnName(vpnUuid) ? interVpnLinkState.getFirstEndpointState().getDpId()
                                                                : interVpnLinkState.getSecondEndpointState().getDpId();
        return dpns == null ? emptyList() : dpns;
    }

    public List<Uint64> getOtherEndpointDpnsByVpnName(String vpnUuid) {
        if (!isComplete()) {
            return emptyList();
        }

        List<Uint64> dpns = isFirstEndpointVpnName(vpnUuid) ? interVpnLinkState.getSecondEndpointState().getDpId()
                                                                : interVpnLinkState.getFirstEndpointState().getDpId();
        return dpns == null ? emptyList() : dpns;
    }

    public List<Uint64> getEndpointDpnsByIpAddr(String endpointIp) {
        if (!isComplete()) {
            return emptyList();
        }

        List<Uint64> dpns =
            isFirstEndpointIpAddr(endpointIp) ? this.interVpnLinkState.getFirstEndpointState().getDpId()
                                              : this.interVpnLinkState.getSecondEndpointState().getDpId();
        return dpns == null ? emptyList() : dpns;
    }

    public List<Uint64> getOtherEndpointDpnsByIpAddr(String endpointIp) {
        if (!isComplete()) {
            return emptyList();
        }

        List<Uint64> dpns =
            isFirstEndpointIpAddr(endpointIp) ? this.interVpnLinkState.getSecondEndpointState().getDpId()
                                              : this.interVpnLinkState.getFirstEndpointState().getDpId();
        return dpns == null ? emptyList() : dpns;
    }

    @Override
    public String toString() {
        final String ns = "Not specified";
        return "InterVpnLink " + getInterVpnLinkName() + " 1stEndpoint=[vpn=" + getFirstEndpointVpnUuid().orElse(ns)
            + " ipAddr=" + getFirstEndpointIpAddr().orElse(ns) + " dpn=" + getFirstEndpointDpns() + "]  "
            + "2ndEndpoint=[vpn=" + getSecondEndpointVpnUuid().orElse(ns) + " ipAddr="
            + getSecondEndpointIpAddr().orElse(ns) + " dpn=" + getSecondEndpointDpns() + "]";
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        InterVpnLinkDataComposite other = (InterVpnLinkDataComposite) obj;
        String none = "";
        return getInterVpnLinkName().equals(other.getInterVpnLinkName())
            && getFirstEndpointVpnUuid().orElse(none).equals(other.getFirstEndpointVpnUuid().orElse(none))
            && getFirstEndpointIpAddr().orElse(none).equals(other.getFirstEndpointIpAddr().orElse(none));
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.interVpnLinkCfg, this.interVpnLinkState);
    }
}
