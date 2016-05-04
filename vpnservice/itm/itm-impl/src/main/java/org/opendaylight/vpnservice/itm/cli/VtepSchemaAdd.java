/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.itm.cli;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.opendaylight.vpnservice.itm.globals.ITMConstants;
import org.opendaylight.vpnservice.itm.impl.ItmUtils;
import org.opendaylight.vpnservice.itm.cli.ItmCliUtils;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.config.rev151102.vtep.config.schemas.VtepConfigSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class which implements karaf command "vtep:schema-add".
 */
@Command(scope = "vtep", name = "schema-add", description = "Adding a VTEP schema.")
public class VtepSchemaAdd extends OsgiCommandSupport {

    private static final String SCHEMA_NAME = "--schema-name";
    private static final String PORT_NAME = "--port-name";
    private static final String VLAN_ID = "--vlan-id";
    private static final String SUBNET_CIDR = "--subnet-cidr";
    private static final String TRANSPORT_ZONE = "--transport-zone";
    private static final String DPN_IDS = "--dpn-ids";
    private static final String GATEWAY_IP = "--gateway-ip";
    private static final String TUNNEL_TYPE = "--tunnel-type";
    private static final String EXCLUDE_IP_FILTER = "--exclude-ip-filter";

    /** The schema name. */
    @Option(name = SCHEMA_NAME, aliases = { "-s" }, description = "Schema name", required = true, multiValued = false)
    private String schemaName;

    /** The port name. */
    @Option(name = PORT_NAME, aliases = { "-p" }, description = "Port name", required = true, multiValued = false)
    private String portName;

    /** The vlan id. */
    @Option(name = VLAN_ID, aliases = { "-v" }, description = "VLAN ID", required = true, multiValued = false)
    private Integer vlanId;

    /** The subnet mask. */
    @Option(name = SUBNET_CIDR, aliases = {
            "-sc" }, description = "Subnet Mask in CIDR-notation string, e.g. 10.0.0.0/24", required = true, multiValued = false)
    private String subnetCIDR;

    /** The transport zone. */
    @Option(name = TRANSPORT_ZONE, aliases = {
            "-tz" }, description = "Transport zone", required = true, multiValued = false)
    private String transportZone;

    /** The dpn ids. */
    @Option(name = DPN_IDS, aliases = {
            "-d" }, description = "DPN ID's in comma separated values. e.g: 2,3,10", required = false, multiValued = false)
    private String dpnIds;

    /** The gateway ip. */
    @Option(name = GATEWAY_IP, aliases = {
            "-g" }, description = "Gateway IP address", required = false, multiValued = false)
    private String gatewayIp;

    /** The tunnel type. */
    @Option(name = TUNNEL_TYPE, aliases = {
            "-t" }, description = "Tunnel type. Value: VXLAN | GRE. Default: VXLAN", required = false, multiValued = false)
    private String tunnelType;

    /** The exclude ip filter. */
    @Option(name = EXCLUDE_IP_FILTER, aliases = {
            "-ex" }, description = "IP Addresses which needs to be excluded from the specified subnet. IP address range or comma separated IP addresses can to be specified. e.g: 10.0.0.1-10.0.0.20,10.0.0.30,10.0.0.35", required = false, multiValued = false)
    private String excludeIpFilter;

    /** The Constant logger. */
    private static final Logger LOG = LoggerFactory.getLogger(VtepSchemaAdd.class);

    /** The itm provider. */
    private IITMProvider itmProvider;

    /**
     * Sets the itm provider.
     *
     * @param itmProvider
     *            the new itm provider
     */
    public void setItmProvider(IITMProvider itmProvider) {
        this.itmProvider = itmProvider;
    }

    /**
     * Command Usage.
     */
    private void usage() {
        System.out.println(String.format(
                "usage: vtep:schema-add [%s schema-name] [%s port-name] [%s vlan-id] [%s subnet-cidr] [%s transport-zone] [%s dpn-ids] [%s gateway-ip] [%s tunnel-type] [%s exclude-ip-filter]",
                SCHEMA_NAME, PORT_NAME, VLAN_ID, SUBNET_CIDR, TRANSPORT_ZONE, DPN_IDS, GATEWAY_IP, TUNNEL_TYPE,
                EXCLUDE_IP_FILTER));
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.karaf.shell.console.AbstractAction#doExecute()
     */
    @Override
    protected Object doExecute() {
        try {
            if (this.schemaName == null || this.portName == null || this.vlanId == null || this.subnetCIDR == null
                    || this.transportZone == null) {
                usage();
                return null;
            }
            LOG.debug("Executing vtep:schema-add command\t {} \t {} \t {} \t {} \t {} \t {} \t {} \t {} \t {}", schemaName,
                    portName, vlanId, subnetCIDR, gatewayIp, transportZone, tunnelType, dpnIds, excludeIpFilter);

            if( null == tunnelType) {
                tunnelType = ITMConstants.TUNNEL_TYPE_VXLAN ;
            }
            VtepConfigSchema schema = ItmUtils.constructVtepConfigSchema(schemaName, portName, vlanId, subnetCIDR,
                    gatewayIp, transportZone, tunnelType, ItmCliUtils.constructDpnIdList(dpnIds), excludeIpFilter);
            this.itmProvider.addVtepConfigSchema(schema);

        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            LOG.error("Exception occurred during execution of command \"vtep:schema-add\": ", e);
        }
        return null;
    }

}
