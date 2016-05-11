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
    public boolean createElanInstance(String elanInstanceName, long MacTimeout, String description);
    public void updateElanInstance(String elanInstanceName, long newMacTimout, String newDescription);
    public boolean deleteElanInstance(String elanInstanceName);

    public void addElanInterface(String elanInstanceName, String interfaceName, List<String> staticMacAddresses, String description);
    public void updateElanInterface(String elanInstanceName, String interfaceName, List<String> updatedStaticMacAddresses, String newDescription);
    public void deleteElanInterface(String elanInstanceName, String interfaceName);

    public void addStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress);
    public void deleteStaticMacAddress(String elanInstanceName, String interfaceName, String macAddress) throws MacNotFoundException;
    public Collection<MacEntry> getElanMacTable(String elanInstanceName);
    public void flushMACTable(String elanInstanceName);
    public ElanInstance getElanInstance(String elanInstanceName);
    public List<ElanInstance> getElanInstances();
    public List<String> getElanInterfaces(String elanInstanceName);

}
