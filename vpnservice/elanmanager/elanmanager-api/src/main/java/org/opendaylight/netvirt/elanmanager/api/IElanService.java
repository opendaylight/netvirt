/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.elanmanager.api;

import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.elan.instances.ElanInstance;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.elan.rev150602.forwarding.entries.MacEntry;
import org.opendaylight.netvirt.elanmanager.exceptions.MacNotFoundException;

import java.util.Collection;
import java.util.List;

public interface IElanService {
    boolean createElanInstance(String elanInstanceName, long MacTimeout, String description);
    void updateElanInstance(String elanInstanceName, long newMacTimout, String newDescription);
    boolean deleteElanInstance(String elanInstanceName);

    void addElanInterface(String elanInstanceName, String interfaceName, List<String> staticMacAddresses,
                          String description);
    void updateElanInterface(String elanInstanceName, String interfaceName, List<String> updatedStaticMacAddresses,
                             String newDescription);
    void deleteElanInterface(String elanInstanceName, String interfaceName);

    void addStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress);
    void deleteStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress) throws MacNotFoundException;
    Collection<MacEntry> getElanMacTable(String elanInstanceName);
    void flushMACTable(String elanInstanceName);
    ElanInstance getElanInstance(String elanInstanceName);
    List<ElanInstance> getElanInstances();
    List<String> getElanInterfaces(String elanInstanceName);

}
