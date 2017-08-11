/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.shell;

import com.google.common.base.Optional;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DhcpConfig;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "dhcp-show", description = "showing parameters for DHCP Service")
public class DhcpShowCommand extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpShowCommand.class);
    private DataBroker dataBroker;
    Integer leaseDuration = null;
    String defDomain = null;

    public void setDataBroker(DataBroker broker) {
        this.dataBroker = broker;
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected Object doExecute() throws Exception {
        try {
            InstanceIdentifier<DhcpConfig> iid = InstanceIdentifier.create(DhcpConfig.class);
            DhcpConfig dhcpConfig = read(iid);
            if (isDhcpConfigAvailable(dhcpConfig)) {
                leaseDuration = dhcpConfig.getConfigs().get(0).getLeaseDuration();
                defDomain = dhcpConfig.getConfigs().get(0).getDefaultDomain();
            }
            session.getConsole().println(
                    "Lease Duration: " + ((leaseDuration != null) ? leaseDuration : DhcpMConstants.DEFAULT_LEASE_TIME));
            session.getConsole().println(
                    "Default Domain: " + ((defDomain != null) ? defDomain : DhcpMConstants.DEFAULT_DOMAIN_NAME));
        } catch (Exception e) {
            session.getConsole().println("Failed to fetch configuration parameters. Try again");
            LOG.error("Failed to fetch DHCP parameters", e);
        }
        return null;
    }

    private boolean isDhcpConfigAvailable(DhcpConfig dhcpConfig) {
        return dhcpConfig != null && dhcpConfig.getConfigs() != null
                && !dhcpConfig.getConfigs().isEmpty();
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private DhcpConfig read(InstanceIdentifier<DhcpConfig> iid) {

        ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        Optional<DhcpConfig> result = Optional.absent();
        try {
            result = tx.read(LogicalDatastoreType.CONFIGURATION, iid).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (result.isPresent()) {
            return result.get();
        }
        return null;
    }

}
