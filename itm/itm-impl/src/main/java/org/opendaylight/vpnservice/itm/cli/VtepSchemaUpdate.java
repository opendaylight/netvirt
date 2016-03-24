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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Class which implements karaf command "vtep:schema-update".
 */
@Command(scope = "vtep", name = "schema-update", description = "Update VTEP schema.")
public class VtepSchemaUpdate extends OsgiCommandSupport {

    private static final String SCHEMA_NAME = "--schema-name";
    private static final String AD = "--add-dpn-ids";
    private static final String DD = "--del-dpn-ids";

    /** The schema name. */
    @Option(name = SCHEMA_NAME, aliases = { "-s" }, description = "Schema name", required = true, multiValued = false)
    private String schemaName;

    /** The dpn ids for add. */
    @Option(name = AD, aliases = {
            "-ad" }, description = "DPN ID's to be added to schema in a comma separated value format. e.g: 2,3,10", required = false, multiValued = false)
    private String dpnIdsForAdd;

    /** The dpn ids for delete. */
    @Option(name = DD, aliases = {
            "-dd" }, description = "DPN ID's to be deleted from schema in a comma separated value format. e.g: 2,3,10", required = false, multiValued = false)
    private String dpnIdsForDelete;

    /** The Constant logger. */
    private static final Logger LOG = LoggerFactory.getLogger(VtepSchemaUpdate.class);

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
        System.out.println(
                String.format("usage: vtep:schema-update [%s schema-name] [%s dpn-ids-for-add] [%s dpn-ids-for-delete]",
                        SCHEMA_NAME, AD, DD));
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.apache.karaf.shell.console.AbstractAction#doExecute()
     */
    @Override
    protected Object doExecute() {
        try {
            if (this.dpnIdsForAdd == null && this.dpnIdsForDelete == null) {
                System.out.println(String.format("Atleast one of the parameters [%s or %s] is mandatory", AD, DD));
                usage();
                return null;
            }
            LOG.debug("Executing vtep:schema-update command\t {} \t {} \t {} ", this.schemaName, this.dpnIdsForAdd,
                    this.dpnIdsForDelete);

            this.itmProvider.updateVtepSchema(this.schemaName, ItmCliUtils.constructDpnIdList(this.dpnIdsForAdd),
                    ItmCliUtils.constructDpnIdList(this.dpnIdsForDelete));

        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
        } catch (Exception e) {
            LOG.error("Exception occurred during execution of command \"vtep:schema-update\": ", e);
        }
        return null;
    }
}
