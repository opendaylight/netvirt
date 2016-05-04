/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.itm.cli;

import org.apache.commons.lang3.StringUtils;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.vpnservice.itm.api.IITMProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class which implements karaf command "vtep:schema-delete".
 */
@Command(scope = "vtep", name = "schema-delete", description = "Delete VTEP schema.")
public class VtepSchemaDelete extends OsgiCommandSupport {

    private static final String ALL = "all";

    @Argument(index = 0, name = ALL, description = "Delete all VTEP schemas", required = true, multiValued = false)
    String deleteAll = null;

    /** The Constant logger. */
    private static final Logger LOG = LoggerFactory.getLogger(VtepSchemaDelete.class);

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

    private void usage() {
        System.out.println("usage: vtep:schema-delete all");
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.karaf.shell.console.AbstractAction#doExecute()
     */
    @Override
    protected Object doExecute() {
        try {
            if (this.deleteAll == null || !StringUtils.equalsIgnoreCase(ALL, this.deleteAll)) {
                usage();
                return null;
            }
            LOG.debug("Executing vtep:schema-delete command\t {} ", this.deleteAll);
            this.itmProvider.deleteAllVtepSchemas();

        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            LOG.error("Exception occurred during execution of command \"vtep:schema-delete all\": ", e);
        }
        return null;
    }

}
