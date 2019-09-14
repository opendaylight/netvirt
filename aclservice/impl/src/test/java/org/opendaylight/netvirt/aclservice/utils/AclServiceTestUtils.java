/*
 * Copyright (c) 2016 Hewlett Packard Enterprise, Co. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.aclservice.utils;

import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Assert;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.genius.mdsalutil.matches.MatchEthernetType;
import org.opendaylight.genius.mdsalutil.matches.MatchIpProtocol;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Destination;
import org.opendaylight.genius.mdsalutil.matches.MatchIpv4Source;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.AceIpBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.matches.ace.type.ace.ip.ace.ip.version.AceIpv4Builder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.DestinationPortRangeBuilder;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.packet.fields.rev160218.acl.transport.header.fields.SourcePortRangeBuilder;

public final class AclServiceTestUtils {
    private AclServiceTestUtils() {

    }

    public static void verifyGeneralFlows(List<MatchInfoBase> srcFlowMatches, String protocol, String srcIpv4Net,
            String dstIpv4Net, String mask) {
        assertTrue(srcFlowMatches.contains(MatchEthernetType.IPV4));
        assertTrue(srcFlowMatches.contains(new MatchIpProtocol(Short.parseShort(protocol))));
        assertTrue(srcFlowMatches.contains(new MatchIpv4Source(srcIpv4Net, mask)));
        assertTrue(srcFlowMatches.contains(new MatchIpv4Destination(dstIpv4Net, mask)));
    }

    public static AceIpBuilder prepareAceIpBuilder(String srcIpv4Net, String dstIpv4Net, String lowerPort,
            String upperPort, short protocol) {
        AceIpBuilder builder = new AceIpBuilder();
        AceIpv4Builder v4builder = new AceIpv4Builder();
        if (srcIpv4Net != null) {
            v4builder.setSourceIpv4Network(new Ipv4Prefix(srcIpv4Net));
        } else {
            v4builder.setSourceIpv4Network(null);
        }

        if (dstIpv4Net != null) {
            v4builder.setDestinationIpv4Network(new Ipv4Prefix(dstIpv4Net));
        } else {
            v4builder.setDestinationIpv4Network(null);
        }
        builder.setAceIpVersion(v4builder.build());
        if (lowerPort != null && upperPort != null) {
            SourcePortRangeBuilder srcPortBuilder = new SourcePortRangeBuilder();
            srcPortBuilder.setLowerPort(PortNumber.getDefaultInstance(lowerPort));
            srcPortBuilder.setUpperPort(PortNumber.getDefaultInstance(upperPort));
            builder.setSourcePortRange(srcPortBuilder.build());
            DestinationPortRangeBuilder dstPortBuilder = new DestinationPortRangeBuilder();
            dstPortBuilder.setLowerPort(PortNumber.getDefaultInstance(lowerPort));
            dstPortBuilder.setUpperPort(PortNumber.getDefaultInstance(upperPort));
            builder.setDestinationPortRange(dstPortBuilder.build());
        }
        builder.setProtocol(protocol);
        return builder;
    }

    public static void verifyMatchFieldTypeDontExist(List<MatchInfoBase> flowMatches,
            Class<? extends MatchInfoBase> matchType) {
        Assert.assertFalse("unexpected match type " + matchType.getSimpleName(), flowMatches.stream().anyMatch(
            item -> matchType.isAssignableFrom(item.getClass())));
    }
}
