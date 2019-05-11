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
import org.opendaylight.netvirt.sfc.classifier.utils.AclMatches;
import org.opendaylight.yang.gen.v1.urn.cisco.params.xml.ns.yang.sfc.rsp.rev140701.rendered.service.paths.RenderedServicePath;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.sfc.sff.logical.rev160620.DpnIdType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.AccessLists;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.Acl;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.AccessListEntries;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.Ace;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.access.control.list.rev160218.access.lists.acl.access.list.entries.ace.Matches;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.InterfaceKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.genius.interfacemanager.rpcs.rev160406.get.dpn._interface.list.output.Interfaces;
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
        String ruleName = ace.getRuleName();
        LOG.debug("Generating classifier entries for Ace: {}", ruleName);
        LOG.trace("Ace details: {}", ace);

        Optional<NetvirtsfcAclActions> sfcActions = Optional.ofNullable(ace.getActions())
                .map(actions -> actions.augmentation(RedirectToSfc.class));
        String rspName = sfcActions.map(NetvirtsfcAclActions::getRspName).map(Strings::emptyToNull).orElse(null);
        String sfpName = sfcActions.map(NetvirtsfcAclActions::getSfpName).map(Strings::emptyToNull).orElse(null);

        if (rspName == null && sfpName == null) {
            LOG.debug("Ace {} ignored: no valid SFC redirect action", ruleName);
            return Collections.emptySet();
        }
        if (rspName != null && sfpName != null) {
            LOG.warn("Ace {} ignored: both SFP and a RSP as redirect actions not supported", ruleName);
            return Collections.emptySet();
        }

        Matches matches = ace.getMatches();
        if (matches == null) {
            LOG.warn("Ace {} ignored: no matches", ruleName);
            return Collections.emptySet();
        }

        NeutronNetwork network = matches.augmentation(NeutronNetwork.class);
        if (sfpName != null && network != null) {
            LOG.warn("Ace {} ignored: SFP redirect action with neutron network match not supported", ruleName);
            return Collections.emptySet();
        }

        String sourcePort = Optional.ofNullable(matches.augmentation(NeutronPorts.class))
                .map(NeutronPorts::getSourcePortUuid)
                .map(Strings::emptyToNull)
                .orElse(null);
        String destinationPort = Optional.ofNullable(matches.augmentation(NeutronPorts.class))
                .map(NeutronPorts::getDestinationPortUuid)
                .map(Strings::emptyToNull)
                .orElse(null);

        if (rspName != null) {
            return getEntriesForRspRedirect(ruleName, sourcePort, destinationPort, network, rspName, matches);
        }

        return getEntriesForSfpRedirect(ruleName, sourcePort, destinationPort, sfpName, matches);
    }

    private Set<ClassifierRenderableEntry> getEntriesForRspRedirect(
            String ruleName,
            String sourcePort,
            String destinationPort,
            NeutronNetwork neutronNetwork,
            String rspName,
            Matches matches) {

        RenderedServicePath rsp = sfcProvider.getRenderedServicePath(rspName).orElse(null);
        if (rsp == null) {
            LOG.debug("Ace {} ignored: RSP {} not yet available", ruleName, rspName);
            return Collections.emptySet();
        }

        if (destinationPort != null) {
            LOG.warn("Ace {}: destination port is ignored combined with RSP redirect", ruleName);
        }

        List<String> interfaces = new ArrayList<>();
        if (neutronNetwork != null) {
            interfaces.addAll(netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork));
        }
        if (sourcePort != null) {
            interfaces.add(sourcePort);
        }

        if (interfaces.isEmpty()) {
            LOG.debug("Ace {} ignored: no interfaces to match against", ruleName);
            return Collections.emptySet();
        }

        return this.buildEntries(ruleName, interfaces, matches, rsp);
    }

    private Set<ClassifierRenderableEntry> getEntriesForSfpRedirect(
            String ruleName,
            String srcPort,
            String dstPort,
            String sfpName,
            Matches matches) {

        if (srcPort == null && dstPort == null) {
            LOG.warn("Ace {} ignored: no source or destination port to match against", ruleName);
            return Collections.emptySet();
        }

        if (Objects.equals(srcPort, dstPort)) {
            LOG.warn("Ace {} ignored: equal source and destination port not supported", ruleName);
            return Collections.emptySet();
        }

        List<RenderedServicePath> rsps = sfcProvider.readServicePathState(sfpName)
                .orElse(Collections.emptyList())
                .stream()
                .map(sfcProvider::getRenderedServicePath)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // The classifier might be configured at the same time as the SFP.
        // The RSPs that are automatically added from that SFP might still
        // be missing. It will be handled on a later listener event.
        if (rsps.isEmpty()) {
            LOG.debug("Ace {} ignored: no RSPs for SFP {} yet available", ruleName, sfpName);
            return Collections.emptySet();
        }

        // An SFP will have two RSPs associated if symmetric, one otherwise.
        if (rsps.size() > 2) {
            LOG.warn("Ace {} ignored: more than two RSPs associated to SFP {} not supported", ruleName, sfpName);
            return Collections.emptySet();
        }

        RenderedServicePath forwardRsp = rsps.stream()
                .filter(rsp -> !rsp.isReversePath())
                .findAny()
                .orElse(null);
        RenderedServicePath reverseRsp = rsps.stream()
                .filter(RenderedServicePath::isReversePath)
                .filter(rsp -> forwardRsp != null && Objects.equals(rsp.getSymmetricPathId(), forwardRsp.getPathId()))
                .findAny()
                .orElse(null);

        if (srcPort != null && forwardRsp == null) {
            LOG.debug("Ace {} ignored: no forward RSP yet available for SFP {} and source port {}",
                    ruleName, sfpName, srcPort);
            return Collections.emptySet();
        }

        if (dstPort != null && reverseRsp == null) {
            LOG.debug("Ace {} ignored: no reverse RSP yet available for SFP {} and destination port {}",
                    ruleName, sfpName, dstPort);
            return Collections.emptySet();
        }

        Set<ClassifierRenderableEntry> entries = new HashSet<>();
        if (srcPort != null) {
            entries.addAll(this.buildEntries(ruleName, Collections.singletonList(srcPort), matches, forwardRsp));
        }
        if (dstPort != null) {
            Matches invertedMatches = AclMatches.invertMatches(matches);
            entries.addAll(
                    this.buildEntries(ruleName, Collections.singletonList(dstPort), invertedMatches, reverseRsp));
        }

        return entries;
    }

    private Set<ClassifierRenderableEntry> buildEntries(
            String ruleName,
            @NonNull List<String> interfaces,
            @NonNull Matches matches,
            @NonNull RenderedServicePath rsp) {

        String rspName = rsp.getName().getValue();
        Long nsp = rsp.getPathId();
        Short nsi = rsp.getStartingIndex();
        Short nsl = rsp.getRenderedServicePathHop() == null ? null : (short) rsp.getRenderedServicePathHop().size();

        if (nsp == null || nsi == null || nsl == null) {
            LOG.warn("Ace {} RSP {} ignored: no valid NSI or NSP or length", ruleName, rspName);
            return Collections.emptySet();
        }

        DpnIdType firstHopDpn = sfcProvider.getFirstHopIngressInterfaceFromRsp(rsp)
                .flatMap(geniusProvider::getDpnIdFromInterfaceName)
                .orElse(null);

        if (firstHopDpn == null) {
            LOG.warn("Ace {} RSP {} ignored: no valid first hop DPN", ruleName, rspName);
            return Collections.emptySet();
        }

        String lastHopInterface = sfcProvider.getLastHopEgressInterfaceFromRsp(rsp).orElse(null);
        if (lastHopInterface == null) {
            LOG.warn("Ace {} RSP {} ignored: has no valid last hop interface", ruleName, rspName);
            return Collections.emptySet();
        }

        DpnIdType lastHopDpn = geniusProvider.getDpnIdFromInterfaceName(lastHopInterface).orElse(null);
        if (lastHopDpn == null) {
            LOG.warn("Ace {} RSP {} ignored: has no valid last hop DPN", ruleName, rspName);
            return Collections.emptySet();
        }

        Map<NodeId, List<InterfaceKey>> nodeToInterfaces = new HashMap<>();
        for (String iface : interfaces) {
            geniusProvider.getNodeIdFromLogicalInterface(iface).ifPresent(nodeId ->
                    nodeToInterfaces.computeIfAbsent(nodeId, key -> new ArrayList<>()).add(new InterfaceKey(iface)));
        }

        LOG.trace("Ace {} RSP {}: got classifier nodes and interfaces: {}", ruleName, rspName, nodeToInterfaces);

        String firstHopIp = geniusProvider.getIpFromDpnId(firstHopDpn).orElse(null);
        Set<ClassifierRenderableEntry> entries = new HashSet<>();
        nodeToInterfaces.forEach((nodeId, ifaces) -> {
            // Get node info
            DpnIdType nodeDpn = new DpnIdType(OpenFlow13Provider.getDpnIdFromNodeId(nodeId));

            if (firstHopIp == null && !nodeDpn.equals(firstHopDpn)) {
                LOG.warn("Ace {} RSP {} classifier {} ignored: no IP to reach first hop DPN {}",
                        ruleName, rspName, nodeId, firstHopDpn);
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
            String nodeIp = geniusProvider.getIpFromDpnId(nodeDpn).orElse(LOCAL_HOST_IP);
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
