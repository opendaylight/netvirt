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

import org.opendaylight.genius.mdsalutil.MatchInfoBase;
import org.opendaylight.netvirt.elanmanager.exceptions.MacNotFoundException;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.interfaces.ElanInterface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;

public interface IElanService extends IEtreeService {

    boolean createElanInstance(String elanInstanceName, long macTimeout, String description);

    void updateElanInstance(String elanInstanceName, long newMacTimout, String newDescription);

    boolean deleteElanInstance(String elanInstanceName);

    void addElanInterface(String elanInstanceName, String interfaceName, List<String> staticMacAddresses,
            String description);

    void updateElanInterface(String elanInstanceName, String interfaceName, List<String> updatedStaticMacAddresses,
            String newDescription);

    void deleteElanInterface(String elanInstanceName, String interfaceName);

    void addStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress);

    void deleteStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress)
            throws MacNotFoundException;

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

    void handleKnownL3DmacAddress(String macAddress, String elanInstanceName, int addOrRemove);

    List<MatchInfoBase> getEgressMatchesForElanInstance(String elanInstanceName);

    Boolean isOpenStackVniSemanticsEnforced();

    /**
     * Add ARP Responder Flow on the given dpn for the ingress interface. LPort
     * tag is optional, if lport tag is present then flow will per interface
     * else flow would per subnet.
     *
     * @param dpnId
     *            DPN on which flow to be added
     * @param ingressInterfaceName
     *            ingress interface
     * @param ipAddress
     *            ip address for which ARP response to be generated
     * @param macAddress
     *            mac address where IP is present
     * @param lportTag
     *            LPort Tag of the ingress interface
     * @param instructions
     *            Custom instruction to be add before the ARP actions to be added
     */
    void addArpResponderFlow(BigInteger dpnId, String ingressInterfaceName, String ipAddress,
            String macAddress, java.util.Optional<Integer> lportTag, List<Instruction> instructions);

    /**
     * Remove ARP Responder flow from the given dpn for the ingress interface.
     * Lport tag is optional if lport is present then flow will per interface
     * else flow would per subnet.
     *
     * @param dpnId
     *            DPN on which flow to be removed
     * @param ingressInterfaceName
     *            ingress interface
     * @param ipAddress
     *            ip address for which ARP responder flow to be removed
     * @param lportTag
     *            LPort Tag of the ingress interface, optional field
     */
    void removeArpResponderFlow(BigInteger dpnId, String ingressInterfaceName, String ipAddress,
            java.util.Optional<Integer> lportTag);
}
