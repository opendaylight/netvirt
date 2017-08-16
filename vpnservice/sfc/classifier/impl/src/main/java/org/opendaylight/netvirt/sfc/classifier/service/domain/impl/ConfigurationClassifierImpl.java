/*
 * Copyright (c) 2017 Ericsson Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.sfc.classifier.service.domain.impl;

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
                .map(this::getEntries)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    public List<Acl> readAcls() {
        InstanceIdentifier<AccessLists> aclsIID = InstanceIdentifier.builder(AccessLists.class).build();
        com.google.common.base.Optional<AccessLists> acls =
                MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, aclsIID);
        LOG.trace("Acls read from datastore: {}", acls);
        // Don’t use Optional.transform() here, getAcl() can return null
        return acls.isPresent() ? acls.get().getAcl() : Collections.emptyList();
    }

    public Set<ClassifierRenderableEntry> getEntries(Ace ace) {

        LOG.trace("Get entries for Ace {}", ace);

        Matches matches = ace.getMatches();

        if (matches == null) {
            LOG.trace("Ace has no matches");
            return Collections.emptySet();
        }

        RenderedServicePath rsp = Optional.ofNullable(ace.getActions())
                .map(actions -> actions.getAugmentation(RedirectToSfc.class))
                .map(NetvirtsfcAclActions::getRspName)
                .flatMap(sfcProvider::getRenderedServicePath)
                .orElse(null);

        if (rsp == null) {
            LOG.trace("Ace has no valid SFC redirect action");
            return Collections.emptySet();
        }

        Long nsp = rsp.getPathId();
        Short nsi = rsp.getStartingIndex();

        if (nsp == null || nsi == null) {
            LOG.trace("RSP has no valid NSI or NSP");
            return Collections.emptySet();
        }

        DpnIdType firstHopDpn = sfcProvider.getFirstHopSfInterfaceFromRsp(rsp)
                .flatMap(geniusProvider::getDpnIdFromInterfaceName)
                .orElse(null);

        if (firstHopDpn == null) {
            LOG.error("RSP has no valid first hop DPN");
            return Collections.emptySet();
        }


        Map<NodeId, List<InterfaceKey>> nodeToInterfaces = new HashMap<>();
        NeutronNetwork neutronNetwork = matches.getAugmentation(NeutronNetwork.class);
        if (neutronNetwork != null) {
            for (String iface : netvirtProvider.getLogicalInterfacesFromNeutronNetwork(neutronNetwork)) {
                geniusProvider.getNodeIdFromLogicalInterface(iface).ifPresent(
                    nodeId -> nodeToInterfaces.computeIfAbsent(nodeId, key -> new ArrayList<>()).add(
                            new InterfaceKey(iface)));
            }
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

            // Egress services must bind to egress ports. Since we dont know before-hand what
            // the egress ports will be, we will bind on all switch ports. If the packet
            // doesnt have NSH, it will be returned to the the egress dispatcher table.
            List<String> interfaceUuidStrList = geniusProvider.getInterfacesFromNode(nodeId);
            interfaceUuidStrList.forEach(interfaceUuidStr -> {
                InterfaceKey interfaceKey = new InterfaceKey(interfaceUuidStr);
                Optional<String> remoteIp = geniusProvider.getRemoteIpAddress(interfaceUuidStr);
                entries.add(ClassifierEntry.buildEgressEntry(interfaceKey, remoteIp.orElse(nodeIp)));
            });
        });

        return entries;
    }
}
