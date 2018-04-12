/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.api;

import java.math.BigInteger;
import java.util.Collection;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.netvirt.elan.arp.responder.ArpResponderInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.dpn.interfaces.elan.dpn.interfaces.list.DpnInterfaces;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

public interface IElanService extends IEtreeService {

    boolean createElanInstance(String elanInstanceName, long macTimeout, String description);

    void updateElanInstance(String elanInstanceName, long newMacTimout, String newDescription);

    boolean deleteElanInstance(String elanInstanceName);

    void addElanInterface(String elanInstanceName, String interfaceName, List<String> staticMacAddresses,
            String description);

    void updateElanInterface(String elanInstanceName, String interfaceName, List<String> updatedStaticMacAddresses,
            String newDescription);

    void deleteElanInterface(String interfaceName);

    void addStaticMacAddress(String interfaceName, String macAddress);

    void deleteStaticMacAddress(String interfaceName, String macAddress);

    Collection<MacEntry> getElanMacTable(String elanInstanceName);

    void flushMACTable(String elanInstanceName);

    ElanInstance getElanInstance(String elanInstanceName);

    List<ElanInstance> getElanInstances();

    List<String> getElanInterfaces(String elanInstanceName);

    void createExternalElanNetwork(ElanInstance elanInstance);

    void updateExternalElanNetwork(ElanInstance elanInstance);

    void createExternalElanNetworks(Node node);

    void updateExternalElanNetworks(Node origNode, Node updatedNode);

    void deleteExternalElanNetwork(ElanInstance elanInstance);

    void deleteExternalElanNetworks(Node node);

    Collection<String> getExternalElanInterfaces(String elanInstanceName);

    String getExternalElanInterface(String elanInstanceName, BigInteger dpnId);

    boolean isExternalInterface(String interfaceName);

    ElanInterface getElanInterfaceByElanInterfaceName(String interfaceName);

    @Deprecated
    void handleKnownL3DmacAddress(String macAddress, String elanInstanceName, int addOrRemove);

    void addKnownL3DmacAddress(String macAddress, String elanInstanceName);

    void addKnownL3DmacAddress(WriteTransaction confTx, String macAddress, String elanInstanceName);

    void removeKnownL3DmacAddress(String macAddress, String elanInstanceName);

    List<MatchInfoBase> getEgressMatchesForElanInstance(String elanInstanceName);

    Boolean isOpenStackVniSemanticsEnforced();

    /**
     * Add ARP Responder Flow on the given dpn for the ingress interface.
     *
     * @param arpResponderInput
     *            ArpResponder Input parameters
     * @see ArpResponderInput
     */
    void addArpResponderFlow(ArpResponderInput arpResponderInput);

    /**
     * Add ARP Responder Flow on the given dpn for the SR-IOV VMs ingress interface.
     *
     * @param arpResponderInput
     *            ArpResponder Input parameters
     * @see ArpResponderInput
     * @param elanInstanceName
     *           The elanInstance  corresponding to the interface
     */
    void addExternalTunnelArpResponderFlow(ArpResponderInput arpResponderInput, String elanInstanceName);

    /**
     * Remove ARP Responder flow from the given dpn for the ingress interface.
     *
     * @param arpResponderInput
     *            ArpResponder Input parameters
     * @see ArpResponderInput
     */
    void removeArpResponderFlow(ArpResponderInput arpResponderInput);

    Long retrieveNewElanTag(String idKey);

    InstanceIdentifier<DpnInterfaces> getElanDpnInterfaceOperationalDataPath(String elanInstanceName, BigInteger dpnId);

    DpnInterfaces getElanInterfaceInfoByElanDpn(String elanInstanceName, BigInteger dpId);
}
