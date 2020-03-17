/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.shell;

import static org.opendaylight.mdsal.common.api.LogicalDatastoreType.CONFIGURATION;

import java.util.Collections;
import java.util.List;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.commands.Option;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.genius.datastoreutils.SingleTransactionDataBroker;
import org.opendaylight.mdsal.binding.api.DataBroker;
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
    protected Object doExecute() throws Exception {
        if (defaultDomain == null && leaseDuration == null) {
            session.getConsole().println(getHelp());
            return null;
        }
        Integer currLeaseDuration = DhcpMConstants.DEFAULT_LEASE_TIME;
        String currDefDomain = DhcpMConstants.DEFAULT_DOMAIN_NAME;
        ConfigsBuilder dccBuilder = new ConfigsBuilder();
        InstanceIdentifier<DhcpConfig> iid = InstanceIdentifier.create(DhcpConfig.class);
        DhcpConfig currentConfig = SingleTransactionDataBroker.syncRead(dataBroker, CONFIGURATION, iid);
        if (currentConfig != null && currentConfig.getConfigs() != null
            && !currentConfig.getConfigs().isEmpty()) {
            Configs dhcpConfig = currentConfig.getConfigs().get(0);
            if (dhcpConfig.getLeaseDuration() != null) {
                currLeaseDuration = dhcpConfig.getLeaseDuration();
            }
            if (dhcpConfig.getDefaultDomain() != null) {
                currDefDomain = dhcpConfig.getDefaultDomain();
            }
        } else {
            LOG.error("doExecute: DHCP config not present");
        }

        dccBuilder.setLeaseDuration(leaseDuration == null ? currLeaseDuration : leaseDuration);
        dccBuilder.setDefaultDomain(defaultDomain == null ? currDefDomain : defaultDomain);

        List<Configs> configList = Collections.singletonList(dccBuilder.build());
        DhcpConfigBuilder dcBuilder = new DhcpConfigBuilder();
        dcBuilder.setConfigs(configList);
        SingleTransactionDataBroker.syncWrite(dataBroker, CONFIGURATION, iid, dcBuilder.build());
        return null;
    }

    private String getHelp() {
        return "Usage: \n"
                + "exec dhcp-configure \n"
                + "[-ld/--leaseDuration leaseTime] [-dd/--defaultDomain defaultDomain]";
    }

}
