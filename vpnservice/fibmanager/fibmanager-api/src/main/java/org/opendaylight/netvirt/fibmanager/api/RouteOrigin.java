/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.api;

  /* Usage:
   * RouteOrigin origin = RouteOrigin.value("b");
   * RouteOrigin origin = RouteOrigin.BGP;
   */

public enum RouteOrigin {
    UNDEFINED("undefined",          "-"),
    CONNECTED("directly connected", "c"),
    STATIC(   "static",             "s"),
    INTERVPN( "inter-vpn link",     "l"),
    SELF_IMPORTED( "self imported route","selfimp"),
    BGP(      "bgp",                "b"),
    IGP(      "igp",                "i");


    final String description;
    final String value;

    RouteOrigin(String description, String value) {
        this.description = description;
        this.value = value;
    }


    public static RouteOrigin value(String value) {
        RouteOrigin origin = UNDEFINED;
        switch (value) {
            case "c":
                origin = CONNECTED;
                break;
            case "s":
                origin = STATIC;
                break;
            case "l":
                origin = INTERVPN;
                break;
            case "selfimp":
                origin = SELF_IMPORTED;
                break;
            case "b":
                origin = BGP;
                break;
            case "i":
                origin = IGP;
                break;
        }

        return origin;
    }

    public String getValue(){
        return value;
    }

    public String getDescription(){
        return description;
    }

}