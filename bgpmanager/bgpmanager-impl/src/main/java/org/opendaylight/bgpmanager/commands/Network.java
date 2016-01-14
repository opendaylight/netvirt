/*
 * Copyright (c) 2015 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.bgpmanager.commands;

import org.apache.karaf.shell.commands.*;
import org.apache.karaf.shell.console.OsgiCommandSupport;
import org.opendaylight.bgpmanager.BgpManager;
import org.opendaylight.bgpmanager.thrift.gen.qbgpConstants;

@Command(scope = "odl", name = "bgp-network", 
         description = "Add or delete BGP static routes")
public class Network extends OsgiCommandSupport {
    private static final String RD = "--rd";
    private static final String PFX = "--prefix";
    private static final String NH = "--nexthop";
    private static final String LB = "--label";

    @Argument(name="add|del", description="The desired operation", 
              required=true, multiValued = false)
    private String action = null;

    @Option(name=RD, aliases={"-r"}, 
            description="Route distinguisher", 
            required=false, multiValued=false)
    private String rd = null;

    @Option(name=PFX, aliases={"-p"},
            description="prefix/length", 
            required=false, multiValued=false)
    private String pfx = null;

    @Option(name=NH, aliases={"-n"},
            description="Nexthop", 
            required=false, multiValued=false)
    private String nh = null;

    @Option(name=LB, aliases={"-l"},
            description="Label", 
            required=false, multiValued=false)
    private String lbl = null;

    private Object usage() {
        System.err.println(
            "usage: bgp-network ["+RD+" rd] ["+PFX+" prefix/len] ["
            +NH+" nexthop] ["+LB+" label] <add|del>");
        return null;
    }       

    @Override
    protected Object doExecute() throws Exception {
        if (!Commands.bgpRunning()) {
            return null;
        }
        BgpManager bm = Commands.getBgpManager();
        switch (action) {
            case "add" : 
                int label = qbgpConstants.LBL_EXPLICIT_NULL;
                if (pfx == null ) {
                    System.err.println("error: "+PFX+" is needed");
                    return null;
                }
                if (nh == null) {
                    System.err.println("error: "+NH+" is needed");
                    return null;
                }
                //todo: syntactic validation of prefix
                if (!Commands.isValid(nh, Commands.IPADDR, NH)) {
                    return null;
                }
                if (lbl != null) {
                    if (!Commands.isValid(lbl, Commands.INT, LB)) {
                        return null;
                    } else {
                        label = Integer.valueOf(lbl);
                    } 
                } else if (rd == null) {
                    System.err.println("error: "+RD+" is needed");
                    return null;
                }
                bm.addPrefix(rd, pfx, nh, label); 
                break;
            case "del" :  
                if (pfx == null) {
                    System.err.println("error: "+PFX+" is needed");
                    return null;
                }
                if (nh != null || lbl != null) {
                    System.err.println("note: some option(s) not needed; ignored");
                }
                bm.deletePrefix(rd, pfx);
                break;
            default :  
                return usage();
        }
        return null;
    }
}
