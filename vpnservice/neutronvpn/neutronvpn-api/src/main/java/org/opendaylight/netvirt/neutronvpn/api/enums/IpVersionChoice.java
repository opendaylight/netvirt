/*
 * Copyright (c) 2017 6WIND, Inc. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.neutronvpn.api.enums;

/**this enum is used to make a choice between IPv4 or IPv6 or both.
 */
public enum IpVersionChoice {
    UNDEFINED(0),
    IPV4(4),
    IPV6(6),
    IPV4AND6(10);
    public final int choice;

    IpVersionChoice(int value) {
        choice = value;
    }

    public boolean isIpVersionChosen(IpVersionChoice arg) {
        if (this.choice == IpVersionChoice.IPV4AND6.choice || this.choice == arg.choice) {
            return true;
        }
        return false;
    }

    /**used to get an association of ipv4 and/or ipv6 choice if it is possible.
     * @param ipVers the IP version to combine with this
     * @return the new IpVersionChoice
     */
    public IpVersionChoice addVersion(IpVersionChoice ipVers) {
        if (ipVers == null) {
            return this;
        }
        if (this.choice == UNDEFINED.choice) {
            return ipVers;
        }
        int newChoice = this.choice + ipVers.choice;
        switch (newChoice) {
            case 4:  return IpVersionChoice.IPV4;
            case 8:  return IpVersionChoice.IPV4;

            case 6:  return IpVersionChoice.IPV6;
            case 12:  return IpVersionChoice.IPV6;

            case 10:  return IpVersionChoice.IPV4AND6;

            default:  return IpVersionChoice.UNDEFINED;
        }
    }
}
