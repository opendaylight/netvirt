/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.stfw.api;

import java.math.BigInteger;

public interface IStfwService {

    void createNetwork(int count, boolean createVpn);

    void deleteNetwork();

    void createOvsSwitches(int count, boolean autoMesh);

    void deleteOvsSwitches();

    void dumpSwitch(BigInteger count);

    void createVMs(int count);

    void deleteVMs();

    void displayFlowCount();

    void displayInterfacesCount();

    void createVpns();

    void deleteVpns();
}
