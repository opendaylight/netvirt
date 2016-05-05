/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fcapsapp.portinfo;

import java.lang.String;
import java.util.Map;

public interface PortNameMappingMBean {
    public Map<String,String> getPortIdtoPortNameMap();
    public String getPortName(String portId);
}