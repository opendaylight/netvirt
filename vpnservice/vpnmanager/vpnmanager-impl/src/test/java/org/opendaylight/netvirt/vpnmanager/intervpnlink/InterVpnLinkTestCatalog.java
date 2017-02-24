/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.vpnmanager.intervpnlink;

import com.google.common.base.Optional;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import org.opendaylight.netvirt.vpnmanager.api.intervpnlink.InterVpnLinkDataComposite;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.Uuid;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.InterVpnLinkStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.FirstEndpointStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointState;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.link.states.inter.vpn.link.state.SecondEndpointStateBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLink;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.InterVpnLinkBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.inter.vpn.link.FirstEndpoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.inter.vpn.link.FirstEndpointBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.inter.vpn.link.SecondEndpoint;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.netvirt.inter.vpn.link.rev160311.inter.vpn.links.inter.vpn.link.SecondEndpointBuilder;

/**
 * Gathers several InterVpnLinks that can be used for testing.
 */
public class InterVpnLinkTestCatalog {

    static InterVpnLinkDataComposite build(String ivpnLinkName, String vpn1Name, String vpn1IpAddr,
                                           String vpn2Name, String vpn2IpAddr, boolean bgpFlag, boolean staticFlag,
                                           boolean connectedFlag, List<BigInteger> vpn1Dpns, long vpn1LportTag,
                                           List<BigInteger> vpn2Dpns, long vpn2LportTag, InterVpnLinkState.State state,
                                           Optional<String> errMsg) {

        FirstEndpoint firstEndpoint =
            new FirstEndpointBuilder().setVpnUuid(new Uuid(vpn1Name)).setIpAddress(new Ipv4Address(vpn1IpAddr)).build();
        SecondEndpoint secondEndpoint =
            new SecondEndpointBuilder().setVpnUuid(new Uuid(vpn2Name)).setIpAddress(new Ipv4Address(vpn2IpAddr))
                                       .build();
        InterVpnLink ivpnLinkCfg =
            new InterVpnLinkBuilder().setName(ivpnLinkName).setFirstEndpoint(firstEndpoint)
                                     .setSecondEndpoint(secondEndpoint).setBgpRoutesLeaking(bgpFlag)
                                     .setStaticRoutesLeaking(staticFlag).setConnectedRoutesLeaking(connectedFlag)
                                     .build();

        FirstEndpointState firstEndpointState =
            new FirstEndpointStateBuilder().setVpnUuid(new Uuid(vpn1Name)).setLportTag(vpn1LportTag)
                                           .setDpId(vpn1Dpns).build();
        SecondEndpointState secondEndpointState =
            new SecondEndpointStateBuilder().setVpnUuid(new Uuid(vpn2Name)).setLportTag(vpn2LportTag)
                                            .setDpId(vpn2Dpns).build();
        InterVpnLinkState ivpnLinkState =
            new InterVpnLinkStateBuilder().setInterVpnLinkName(ivpnLinkName).setState(state)
                                          .setErrorDescription(errMsg.or(""))
                                          .setFirstEndpointState(firstEndpointState)
                                          .setSecondEndpointState(secondEndpointState).build();
        return new InterVpnLinkDataComposite(ivpnLinkCfg, ivpnLinkState);
    }

    static void updateEndpointDpns(InterVpnLinkDataComposite ivl, boolean is1stEndpoint, List<BigInteger> newDpns) {
        Optional<FirstEndpointState> new1stEpState = (is1stEndpoint)
            ? Optional.of(InterVpnLinkUtil.buildFirstEndpointState(ivl.getInterVpnLinkState().getFirstEndpointState(),
                                                                   Optional.of(newDpns), Optional.absent()))
            : Optional.absent();
        Optional<SecondEndpointState> new2ndEpState = (is1stEndpoint)
            ? Optional.absent()
            : Optional.of(InterVpnLinkUtil.buildSecondEndpointState(ivl.getInterVpnLinkState().getSecondEndpointState(),
                                                                    Optional.of(newDpns), Optional.absent()));
        InterVpnLinkState newIvlState =
            InterVpnLinkUtil.buildIvlStateFromOriginal(ivl.getInterVpnLinkState(), new1stEpState, new2ndEpState,
                                                          /*errMsg*/ Optional.absent());
        ivl.setInterVpnLinkState(newIvlState);
    }


    // InterVpnLink linking VPN_1 and VPN_2. Active. No automatic route leaking at all. Installed on DPN BigInteger.ONE
    static InterVpnLinkDataComposite I_VPN_LINK_12 =
        build("InterVpnLink VPN1-VPN2", L3VpnTestCatalog.VPN_1.vpnCfgData.getVpnInstanceName(), "1.1.1.1",
              L3VpnTestCatalog.VPN_2.vpnCfgData.getVpnInstanceName(), "2.2.2.2", false, false, false,
              Arrays.asList(BigInteger.ONE), 100001, Arrays.asList(BigInteger.ONE), 100002,
              InterVpnLinkState.State.Active, Optional.absent());

    // InterVpnLink linking VPN_1 and VPN_2. Erroneous.
    static InterVpnLinkDataComposite I_VPN_LINK_12_ERR =
        build("InterVpnLink VPN1-VPN2", L3VpnTestCatalog.VPN_1.vpnCfgData.getVpnInstanceName(), "1.1.1.1",
              L3VpnTestCatalog.VPN_2.vpnCfgData.getVpnInstanceName(), "2.2.2.2", false, false, false,
              Arrays.asList(BigInteger.ONE), 100001, Arrays.asList(BigInteger.ONE), 100002,
              InterVpnLinkState.State.Error, Optional.absent());

    // InterVpnLink linking VPN_3 and VPN_4. Active. No automatic route leaking at all. Installed on DPN BigInteger.ONE
    static InterVpnLinkDataComposite I_VPN_LINK_34 =
        build("InterVpnLink VPN3-VPN4", L3VpnTestCatalog.VPN_3.vpnCfgData.getVpnInstanceName(), "3.3.3.3",
              L3VpnTestCatalog.VPN_4.vpnCfgData.getVpnInstanceName(), "4.4.4.4", false, false, false,
              Arrays.asList(BigInteger.valueOf(2L)), 100003, Arrays.asList(BigInteger.valueOf(2L)), 100004,
              InterVpnLinkState.State.Active, Optional.absent());

    // InterVpnLink linking VPN_5 and VPN_6. Active. No automatic route leaking at all. Installed on DPN 2
    // Note that VPN5 has same iRTs than VPN1 and VPN6 has same iRTs thant VPN2. This means that this InterVpnLink
    // is 'similar' to I_VPN_LINK_12. Used to test co-location of InterVpnLinks
    static InterVpnLinkDataComposite I_VPN_LINK_56 =
        build("InterVpnLink VPN5-VPN6", L3VpnTestCatalog.VPN_5.vpnCfgData.getVpnInstanceName(), "5.5.5.5",
              L3VpnTestCatalog.VPN_6.vpnCfgData.getVpnInstanceName(), "6.6.6.6", false, false, false,
              Arrays.asList(BigInteger.valueOf(2L)), 100005, Arrays.asList(BigInteger.valueOf(2L)), 100006,
              InterVpnLinkState.State.Active, Optional.absent());

    static List<InterVpnLinkDataComposite> ALL_IVPN_LINKS =
        Arrays.asList(I_VPN_LINK_12, I_VPN_LINK_12_ERR, I_VPN_LINK_34, I_VPN_LINK_56);
}
