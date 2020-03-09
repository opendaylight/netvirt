/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.neutronvpn.shell;

import com.google.common.util.concurrent.CheckedFuture;
import java.util.concurrent.ExecutionException;
import org.apache.karaf.shell.commands.Argument;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.TransactionCommitFailedException;
import org.opendaylight.controller.sal.core.api.model.SchemaService;
import org.opendaylight.mdsal.binding.dom.codec.api.BindingNormalizedNodeSerializer;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Command(scope = "vpnservice", name = "clear-op-ds", description = "Clear Operational DS")
public class ClearOperDS extends OsgiCommandSupport {

    private static final Logger LOG = LoggerFactory.getLogger(ClearOperDS.class);
    private DataBroker dataBroker;
    private SchemaService schemaService;
    private BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer;
    private final InstanceIdentifierCodec iidCodec = new InstanceIdentifierCodec();

    public void setDataBroker(DataBroker broker) {
        this.dataBroker = broker;
    }

    public void setSchemaService(SchemaService schemaService) {
        this.schemaService = schemaService;
        iidCodec.registerSchemaContextListener(schemaService);
    }

    public void setBindingNormalizedNodeSerializer(BindingNormalizedNodeSerializer bindingNormalizedNodeSerializer) {
        this.bindingNormalizedNodeSerializer = bindingNormalizedNodeSerializer;
        iidCodec.setBindingNormalizedNodeSerializer(bindingNormalizedNodeSerializer);
    }

    @Argument(index = 0, name = "iid", description = "InstanceIdentifier for path to be cleared",
        required = false, multiValued = false)
    String strIid;

    @Override
    @SuppressWarnings("checkstyle:IllegalCatch")
    protected Object doExecute() throws Exception {
        if (dataBroker == null) {
            session.getConsole().println("DataBroker not initialized");
            return null;
        }
        if (schemaService == null) {
            session.getConsole().println("SchemaService not initialized");
            return null;
        }
        if (bindingNormalizedNodeSerializer == null) {
            session.getConsole().println("BindingNormalizedNodeSerializer not initialized");
            return null;
        }
        if (strIid != null) {
            InstanceIdentifier<?> iid = iidCodec.bindingDeserializerOrNull(strIid);
            LOG.debug("StringIid {} converted to {}", strIid, iid);
            try {
                delete(iid);
            } catch (Exception e) {
                session.getConsole().println("Failed to clear operational DS entry.");
                return null;
            }
            session.getConsole().println("Operational DS cleared");

        } else {
            session.getConsole().println("Instance identifier argument is required");
            session.getConsole().println(getHelp());
        }

        return null;
    }

    @SuppressWarnings("all")
    private void delete(InstanceIdentifier<?> iid) throws Exception {
        WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
        tx.delete(LogicalDatastoreType.OPERATIONAL, iid);
        CheckedFuture<Void, TransactionCommitFailedException> futures = tx.submit();
        try {
            futures.get();
        } catch (InterruptedException | ExecutionException e) {
            LOG.error("Error deleting operational data at path {}", iid);
            throw e;
        }
    }

    private String getHelp() {
        StringBuilder help = new StringBuilder("Usage:");
        help.append("exec clear-oper-ds <path>\n");
        return help.toString();
    }

}
