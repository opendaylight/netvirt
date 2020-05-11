/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */


package org.opendaylight.netvirt.bgpmanager.commands;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.PrintStream;
import org.apache.felix.service.command.CommandSession;
import org.apache.karaf.shell.commands.Command;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.netvirt.bgpmanager.BgpUtil;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebfd.rev190219.BfdConfig;
import org.opendaylight.yang.gen.v1.urn.ericsson.params.xml.ns.yang.ebgp.rev150901.bgp.DcgwTepList;



@Command(scope = "odl", name = "bfd-cache",
        description = "Text dump of BFD config cache")
@SuppressFBWarnings("DM_DEFAULT_ENCODING")
public class BfdCache extends OsgiCommandSupport {
    private static final String MINRX = "min-rx";
    private static final String MINTX = "min-tx";
    private static final String DMULT = "detect-mult";
    private static final String MLHOP = "multi-hop";
    private static final String DCGWIP = "dcgw-ip";
    private static final String TEPIP = "tep-ip";

    private final BgpUtil bgpUtil;

    public BfdCache(BgpUtil bgpUtil) {
        this.bgpUtil = bgpUtil;
    }

    /*private Object usage() {
        session.getConsole().println("usage: bfd-cache ");
        return null;
    }*/

    public Object show(CommandSession session) throws Exception {
        this.session = session;
        return doExecute();
    }

    public Object show() throws Exception {
        return doExecute();
    }

    @SuppressWarnings("resource")
    @Override
    protected Object doExecute() throws Exception {

        PrintStream ps = session.getConsole();
        BfdConfig bfdConfig = bgpUtil.getBfdConfig();
        if (bfdConfig != null) {
            boolean bfdEnabled = bfdConfig.isBfdEnabled();
            ps.printf("%nbfd-enabled     %s%n", bfdEnabled ? "yes" : "no");
            int minrx = bfdConfig.getMinRx().intValue();
            int mintx = bfdConfig.getMinTx().intValue();
            int detectmult = bfdConfig.getDetectMult().intValue();
            boolean multihop = bfdConfig.isMultihop();
            ps.printf("%n\t%-15s  %d%n\t%-15s  %d%n\t%-15s  %d%n\t%-15s  %s%n",
                    MINRX, minrx, MINTX, mintx, DMULT, detectmult, MLHOP, multihop ? "yes" : "no");
        } else {
            ps.printf("%nbfd-enabled     %s%n", "no");
        }

        DcgwTepList dcgwTepList = bgpUtil.getDcgwTepConfig();
        if (dcgwTepList != null) {
            dcgwTepList.getDcgwTep().values().forEach(dcgwTep -> {
                ps.printf("%n%n%-15s  %s", DCGWIP, dcgwTep.getDcGwIp());
                dcgwTep.getTepIps().forEach(tep -> {
                    ps.printf("%n\t%-15s  %s", TEPIP, tep);
                });
            });
            ps.printf("%n");
        }
        return null;
    }
}
