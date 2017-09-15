/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

import com.google.common.base.Strings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.netvirt.sfc.classifier.providers.GeniusProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.NetvirtProvider;
import org.opendaylight.netvirt.sfc.classifier.providers.OpenFlow13Provider;
import org.opendaylight.netvirt.sfc.classifier.providers.SfcProvider;
import org.opendaylight.netvirt.sfc.classifier.service.domain.ClassifierEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierRenderableEntry;
import org.opendaylight.netvirt.sfc.classifier.service.domain.api.ClassifierState;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NetvirtsfcAclActions;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronNetwork;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.NeutronPorts;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.sfc.acl.rev150105.RedirectToSfc;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigurationClassifierImpl implements ClassifierState {

    private final SfcProvider sfcProvider;
    private final DataBroker dataBroker;
    private final GeniusProvider geniusProvider;
    private final NetvirtProvider netvirtProvider;
    private static final Logger LOG = LoggerFactory.getLogger(ConfigurationClassifierImpl.class);
    private static final String LOCAL_HOST_IP = "127.0.0.1";

    public ConfigurationClassifierImpl(GeniusProvider geniusProvider,
                                       NetvirtProvider netvirtProvider,
                                       SfcProvider sfcProvider,
                                       DataBroker dataBroker) {
        this.geniusProvider = geniusProvider;
        this.netvirtProvider = netvirtProvider;
        this.sfcProvider = sfcProvider;
        this.dataBroker = dataBroker;
    }

    @Override
    public Set<ClassifierRenderableEntry> getAllEntries() {
        return readAcls().stream()
                .map(Acl::getAccessListEntries)
                .filter(Objects::nonNull)
                .map(AccessListEntries::getAce)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(this::getEntriesForAce)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    private List<Acl> readAcls() {
        InstanceIdentifier<AccessLists> aclsIID = InstanceIdentifier.builder(AccessLists.class).build();
        Optional<AccessLists> acls;
        acls = MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, aclsIID).toJavaUtil();
        LOG.trace("Acls read from datastore: {}", acls);
        return acls.map(AccessLists::getAcl).orElse(Collections.emptyList());
    }

    private Set<ClassifierRenderableEntry> getEntriesForAce(Ace ace) {
        LOG.info("Generating classifier entries for Ace: {}", ace.getRuleName());
        LOG.trace("Ace details: {}", ace);

        Optional<NetvirtsfcAclActions> sfcActions = Optional.ofNullable(ace.getActions())
                .map(actions -> actions.getAugmentation(RedirectToSfc.class));
        String rspName = sfcActions.map(NetvirtsfcAclActions::getRspName).map(Strings::emptyToNull).orElse(null);
        String sfpName = sfcActions.map(NetvirtsfcAclActions::getSfpName).map(Strings::emptyToNull).orElse(null);

        if (rspName == null && sfpName == null) {
            LOG.debug("Ace has no valid SFC redirect action, ignoring");
            return Collections.emptySet();
        }
        if (rspName != null && sfpName != null) {
            LOG.error("Ace has both a SFP and a RSP as redirect actions, ignoring as not supported");
            return Collections.emptySet();
        }

        Matches matches = ace.getMatches();
        if (matches == null) {
            LOG.warn("Ace has no matches, ignoring");
            return Collections.emptySet();
        }

        NeutronNetwork network = matches.getAugmentation(NeutronNetwork.class);
        if (sfpName != null && network != null) {
            LOG.error("Ace has a SFP redirect action with a neutron network match, ignoring as not supported");
            return Collections.emptySet();
        }

        String sourcePort = Optional.ofNullable(matches.getAugmentation(NeutronPorts.class))
                .map(NeutronPorts::getSourcePortUuid)
                .map(Strings::emptyToNull)
                .orElse(null);
        String destinationPort = Optional.ofNullable(matches.getAugmentation(NeutronPorts.class))
                .map(NeutronPorts::getDestinationPortUuid)
                .map(Strings::emptyToNull)
                .orElse(null);

        if (rspName != null) {
            return getEntriesForRspRedirect(sourcePort, destinationPort, network, rspName, matches);
        }

        return getEntriesForSfpRedirect(sourcePort, destinationPort, sfpName, matches);
    }

    private Set<ClassifierRenderableEntry> getEntriesForRspRedirect(
            String sourcePort,
            String destinationPort,
            NeutronNetwork neutronNetwork,
            String rspName,
            Matches matches) {

        List<String> interfaces = new ArrayList<>();
        if (neutronNetwork != null) {
            interfaces.addAll(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork));
        }

        RenderedServicePath rsp = sfcProvider.getRenderedServicePath(rspName).orElse(null);
        if (rsp == null) {
            LOG.error("RSP {} could not be read from database", rspName);
            return Collections.emptySet();
        }

        if (rsp.isReversePath()) {
            interfaces.add(destinationPort);
            if (sourcePort != null) {
                LOG.warn("Source port ignored with redirect to reverse RSP");
            }
        } else {
            if (destinationPort != null) {
                LOG.warn("Destination port ignored with redirect to forward RSP");
            }
            interfaces.add(sourcePort);
        }

        if (interfaces.isEmpty()) {
            LOG.warn("Ace has no interfaces to match against");
            return Collections.emptySet();
        }

        return this.buildEntries(interfaces, matches, rsp);
    }

    private Set<ClassifierRenderableEntry> getEntriesForSfpRedirect(
            String sourcePort,
            String destinationPort,
            String sfpName,
            Matches matches) {

        if (sourcePort == null && destinationPort == null) {
            LOG.warn("Ace has no interfaces to match against");
            return Collections.emptySet();
        }

        if (Objects.equals(sourcePort, destinationPort)) {
            LOG.error("Specifying the same source and destination port is not valid configuration");
            return Collections.emptySet();
        }

        List<String> rspNames = sfcProvider.readServicePathState(sfpName).orElse(Collections.emptyList());
        if (rspNames.isEmpty()) {
            LOG.warn("There is currently no RSPs for SFP {}", sfpName);
            return Collections.emptySet();
        }

        Set<ClassifierRenderableEntry> entries = new HashSet<>();
        boolean haveAllRsps = false;
        RenderedServicePath forwardRsp = null;
        RenderedServicePath reverseRsp = null;
        for (String anRspName : rspNames) {
            RenderedServicePath rsp = sfcProvider.getRenderedServicePath(anRspName).orElse(null);
            if (rsp.isReversePath() && destinationPort != null) {
                reverseRsp = rsp;
                haveAllRsps = forwardRsp != null || sourcePort == null;
            }
            if (!rsp.isReversePath() && sourcePort != null) {
                forwardRsp = rsp;
                haveAllRsps = reverseRsp != null || destinationPort == null;
            }
            if (haveAllRsps) {
                break;
            }
        }

        if (reverseRsp != null) {
            entries.addAll(this.buildEntries(Collections.singletonList(destinationPort), matches, reverseRsp));
        } else if (destinationPort != null) {
            LOG.warn("No reverse RSP found for SFP {} and destination port {}", sfpName, destinationPort);
        }

        if (forwardRsp != null) {
            entries.addAll(this.buildEntries(Collections.singletonList(sourcePort), matches, forwardRsp));
        } else if (sourcePort != null) {
            LOG.warn("No forward RSP found for SFP {} and source port {}", sfpName, sourcePort);
        }

        return entries;
    }

    private Set<ClassifierRenderableEntry> buildEntries(
            @NonNull List<String> interfaces,
            @NonNull Matches matches,
            @NonNull RenderedServicePath rsp) {

        Long nsp = rsp.getPathId();
        Short nsi = rsp.getStartingIndex();
        Short nsl = rsp.getRenderedServicePathHop() == null ? null : (short) rsp.getRenderedServicePathHop().size();

        if (nsp == null || nsi == null || nsl == null) {
            LOG.error("RSP has no valid NSI or NSP or length");
            return Collections.emptySet();
        }

        DpnIdType firstHopDpn = sfcProvider.getFirstHopSfInterfaceFromRsp(rsp)
                .flatMap(geniusProvider::getDpnIdFromInterfaceName)
                .orElse(null);

        if (firstHopDpn == null) {
            LOG.error("RSP has no valid first hop DPN");
            return Collections.emptySet();
        }

        String lastHopInterface = sfcProvider.getLastHopSfInterfaceFromRsp(rsp).orElse(null);
        if (lastHopInterface == null) {
            LOG.error("RSP has no valid last hop interface");
            return Collections.emptySet();
        }

        DpnIdType lastHopDpn = geniusProvider.getDpnIdFromInterfaceName(lastHopInterface).orElse(null);
        if (lastHopDpn == null) {
            LOG.error("RSP has no valid last hop DPN");
            return Collections.emptySet();
        }

        Map<NodeId, List<InterfaceKey>> nodeToInterfaces = new HashMap<>();
        for (String iface : interfaces) {
            geniusProvider.getNodeIdFromLogicalInterface(iface).ifPresent(nodeId ->
                    nodeToInterfaces.computeIfAbsent(nodeId, key -> new ArrayList<>()).add(new InterfaceKey(iface)));
        }

        LOG.trace("Got classifier nodes and interfaces: {}", nodeToInterfaces);

        String firstHopIp = geniusProvider.getIpFromDpnId(firstHopDpn).orElse(null);
        Set<ClassifierRenderableEntry> entries = new HashSet<>();
        nodeToInterfaces.forEach((nodeId, ifaces) -> {
            // Get node info
            DpnIdType nodeDpn = new DpnIdType(OpenFlow13Provider.getDpnIdFromNodeId(nodeId));
            String nodeIp = geniusProvider.getIpFromDpnId(nodeDpn).orElse(LOCAL_HOST_IP);

            if (firstHopIp == null && !nodeDpn.equals(firstHopDpn)) {
                LOG.warn("Classifier on node {} has no IP to reach first hop on node {}", nodeDpn, firstHopDpn);
                return;
            }

            // Add entries that are not based on ingress or egress interface
            entries.add(ClassifierEntry.buildNodeEntry(nodeId));
            entries.add(ClassifierEntry.buildPathEntry(
                    nodeId,
                    nsp,
                    nsi,
                    nsl,
                    nodeDpn.equals(firstHopDpn) ? null : firstHopIp));

            // Add entries based on ingress interface
            ifaces.forEach(interfaceKey -> {
                entries.add(ClassifierEntry.buildIngressEntry(interfaceKey));
                entries.add(ClassifierEntry.buildMatchEntry(
                        nodeId,
                        geniusProvider.getNodeConnectorIdFromInterfaceName(interfaceKey.getName()).get(),
                        matches,
                        nsp,
                        nsi));
            });

            // To handle chain egress when origin, last SF and destination are on the same node,
            // we need to bind to the SF interface so that SFC pipeline to classifier pipeline
            // hand-off can happen through the dispatcher table
            if (nodeDpn.equals(lastHopDpn)) {
                entries.add(ClassifierEntry.buildIngressEntry(new InterfaceKey(lastHopInterface)));
            }

            // Egress services must bind to egress ports. Since we dont know before-hand what
            // the egress ports will be, we will bind on all switch ports. If the packet
            // doesnt have NSH, it will be returned to the the egress dispatcher table.
            List<Interfaces> interfaceUuidStrList = geniusProvider.getInterfacesFromNode(nodeId);
            interfaceUuidStrList.forEach(interfaceUuidStr -> {
                InterfaceKey interfaceKey = new InterfaceKey(interfaceUuidStr.getInterfaceName());
                Optional<String> remoteIp = geniusProvider.getRemoteIpAddress(interfaceUuidStr.getInterfaceName());
                entries.add(ClassifierEntry.buildEgressEntry(interfaceKey, remoteIp.orElse(nodeIp)));
            });
        });

        return entries;
    }
}
