/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.api;
import java.math.BigInteger;
import java.util.List;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.vpnservice.interfacemgr.exceptions.InterfaceNotFoundException;
import org.opendaylight.vpnservice.mdsalutil.ActionInfo;
import org.opendaylight.vpnservice.mdsalutil.MatchInfo;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema; 
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.op.rev150701.TunnelList ;

public interface IITMProvider {
	// APIs used by i
    public void createLocalCache(BigInteger dpnId, String portName, Integer vlanId, String ipAddress, String subnetMask, String gatewayIp, String transportZone);

    public void commitTeps();

    public DataBroker getDataBroker();

    public void showTeps();
    public void showState(TunnelList tunnels);

    public void deleteVtep(BigInteger dpnId, String portName, Integer vlanId, String ipAddress, String subnetMask,
                    String gatewayIp, String transportZone);
   // public void showState(TunnelsState tunnelsState);
    public void configureTunnelType(String transportZone, String tunnelType);

    
    /**
     * Adds the vtep config schema.
     *
     * @param vtepConfigSchema
     *            the vtep config schema
     */
    public void addVtepConfigSchema(VtepConfigSchema vtepConfigSchema);

    /**
     * Gets the vtep config schema.
     *
     * @param schemaName
     *            the schema name
     * @return the vtep config schema
     */
    public VtepConfigSchema getVtepConfigSchema(String schemaName);

    /**
     * Gets the all vtep config schemas.
     *
     * @return the all vtep config schemas
     */
    public List<VtepConfigSchema> getAllVtepConfigSchemas();

    /**
     * Update VTEP schema.
     *
     * @param schemaName
     *            the schema name
     * @param lstDpnsForAdd
     *            the lst dpns for add
     * @param lstDpnsForDelete
     *            the lst dpns for delete
     */
    public void updateVtepSchema(String schemaName, List<BigInteger> lstDpnsForAdd, List<BigInteger> lstDpnsForDelete);

    /**
     * Delete all vtep schemas.
     */
    public void deleteAllVtepSchemas();

    public void configureTunnelMonitorEnabled(boolean monitorEnabled);

    public void configureTunnelMonitorInterval(int interval);
}
