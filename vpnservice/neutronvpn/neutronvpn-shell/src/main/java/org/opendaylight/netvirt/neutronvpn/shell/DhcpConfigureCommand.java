/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.shell;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;

import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.netvirt.dhcpservice.api.DhcpMConstants;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DhcpConfig;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.DhcpConfigBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.dhcp.config.Configs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.neutronvpn.rev150602.dhcp.config.ConfigsBuilder;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "dhcp-configure", description = "configuring parameters for DHCP Service")
public class DhcpConfigureCommand extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(DhcpConfigureCommand.class);

    @Option(name = "-ld", aliases = {"--leaseDuration"}, description = "Lease Duration", required = false,
        multiValued = false)
    Integer leaseDuration;

    @Option(name = "-dd", aliases = {"--defaultDomain"}, description = "Default Domain", required = false,
        multiValued = false)
    String defaultDomain;

    private DataBroker dataBroker;

    public void setDataBroker(DataBroker broker) {
        this.dataBroker = broker;
    }

    @Override
    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected Object doExecute() throws Exception {
        try {
            if ((defaultDomain == null) && (leaseDuration == null)) {
                session.getConsole().println(getHelp());
                return null;
            }
            Integer currLeaseDuration = DhcpMConstants.DEFAULT_LEASE_TIME;
            String currDefDomain = DhcpMConstants.DEFAULT_DOMAIN_NAME;
            ConfigsBuilder dccBuilder = new ConfigsBuilder();
            InstanceIdentifier<DhcpConfig> iid = InstanceIdentifier.create(DhcpConfig.class);
            DhcpConfig currentConfig = read(iid);
            if (currentConfig != null && currentConfig.getConfigs() != null
                && !currentConfig.getConfigs().isEmpty()) {
                Configs dhcpConfig = currentConfig.getConfigs().get(0);
                if (dhcpConfig.getLeaseDuration() != null) {
                    currLeaseDuration = dhcpConfig.getLeaseDuration();
                }
                if (dhcpConfig.getDefaultDomain() != null) {
                    currDefDomain = dhcpConfig.getDefaultDomain();
                }
            }

            dccBuilder.setLeaseDuration((leaseDuration == null) ? currLeaseDuration : leaseDuration);
            dccBuilder.setDefaultDomain((defaultDomain == null) ? currDefDomain : defaultDomain);

            List<Configs> configList = Collections.singletonList(dccBuilder.build());
            DhcpConfigBuilder dcBuilder = new DhcpConfigBuilder();
            dcBuilder.setConfigs(configList);
            write(iid, dcBuilder.build());
        } catch (Exception e) {
            session.getConsole().println("Failed to configure. Try again");
            LOG.error("Failed to configure DHCP parameters", e);
        }
        return null;
    }

    private void write(InstanceIdentifier<DhcpConfig> iid, DhcpConfig dhcpConfig) {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.put(LogicalDatastoreType.CONFIGURATION, iid, dhcpConfig);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error writing to datastore (path, data) : ({}, {})", iid, dhcpConfig);
            throw new RuntimeException(e.getMessage());
        }
    }

    // TODO Clean up the exception handling
    @SuppressWarnings("checkstyle:IllegalCatch")
    private DhcpConfig read(InstanceIdentifier<DhcpConfig> iid) {

        ReadOnlyTransaction tx = dataBroker.newReadOnlyTransaction();
        Optional<DhcpConfig> result = Optional.absent();
        try {
            result = tx.read(LogicalDatastoreType.CONFIGURATION, iid).get();
        } catch (Exception e) {
            LOG.debug("DhcpConfig not present");
            return null;
        }
        if (result.isPresent()) {
            return result.get();
        }
        return null;
    }

    private String getHelp() {
        StringBuilder help = new StringBuilder("Usage: ");

        help.append("exec dhcp-configure ");
        help.append("[-ld/--leaseDuration leaseTime] [-dd/--defaultDomain defaultDomain]");
        return help.toString();
    }

}
