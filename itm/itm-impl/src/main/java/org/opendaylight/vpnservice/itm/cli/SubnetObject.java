/*
 * Copyright (c) 2016 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.vpnservice.itm.cli;

import java.util.Map;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.SubnetsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.vpnservice.itm.rev150701.transport.zones.transport.zone.SubnetsKey;
import org.opendaylight.yangtools.yang.binding.Augmentation;
import org.opendaylight.yangtools.yang.binding.DataObject;

public class SubnetObject {
    private IpAddress _gatewayIp;
    private SubnetsKey _key;
    private IpPrefix _prefix;
    private java.lang.Integer _vlanId;

    public SubnetObject(IpAddress gWIP, SubnetsKey key, IpPrefix mask, Integer vlanId) {
        _gatewayIp = gWIP;
        _key = key;
        _prefix = mask;
        try {
            if (vlanId != null) {
                checkVlanIdRange(vlanId);
            }
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid VlanID. expected: 0 to 4095");
        }
        _vlanId = vlanId;
    }

    public IpAddress get_gatewayIp() {
        return _gatewayIp;
    }

    public SubnetsKey get_key() {
        return _key;
    }

    public IpPrefix get_prefix() {
        return _prefix;
    }

    public java.lang.Integer get_vlanId() {
        return _vlanId;
    }

    private int hash = 0;
    private volatile boolean hashValid = false;

    @Override
    public int hashCode() {
        if (hashValid) {
            return hash;
        }

        final int prime = 31;
        int result = 1;
        result = prime * result + ((_gatewayIp == null) ? 0 : _gatewayIp.hashCode());
        result = prime * result + ((_key == null) ? 0 : _key.hashCode());
        result = prime * result + ((_prefix == null) ? 0 : _prefix.hashCode());
        result = prime * result + ((_vlanId == null) ? 0 : _vlanId.hashCode());
        hash = result;
        hashValid = true;
        return result;
    }

    @Override
    public boolean equals(java.lang.Object obj) {
        if (this == obj) {
            return true;
        }
        if (!(obj instanceof SubnetObject)) {
            return false;
        }
        SubnetObject other = (SubnetObject) obj;
        if (_gatewayIp == null) {
            if (other.get_gatewayIp() != null) {
                return false;
            }
        } else if (!_gatewayIp.equals(other.get_gatewayIp())) {
            return false;
        }
        if (_key == null) {
            if (other.get_key() != null) {
                return false;
            }
        } else if (!_key.equals(other.get_key())) {
            return false;
        }
        if (_prefix == null) {
            if (other.get_prefix() != null) {
                return false;
            }
        } else if (!_prefix.equals(other.get_prefix())) {
            return false;
        }
        if (_vlanId == null) {
            if (other.get_vlanId() != null) {
                return false;
            }
        } else if (!_vlanId.equals(other.get_vlanId())) {
            return false;
        }
        return true;
    }

    @Override
    public java.lang.String toString() {
        java.lang.StringBuilder builder = new java.lang.StringBuilder("Subnets [");
        boolean first = true;

        if (_gatewayIp != null) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append("_gatewayIp=");
            builder.append(_gatewayIp);
        }
        if (_key != null) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append("_key=");
            builder.append(_key);
        }
        if (_prefix != null) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append("_prefix=");
            builder.append(_prefix);
        }
        if (_vlanId != null) {
            if (first) {
                first = false;
            } else {
                builder.append(", ");
            }
            builder.append("_vlanId=");
            builder.append(_vlanId);
        }
        return builder.append(']').toString();
    }

    private static void checkVlanIdRange(final int value) {
        if (value >= 0 && value <= 4095) {
            return;
        }
        throw new IllegalArgumentException(String.format("Invalid range: %s, expected: [[0?4095]].", value));
    }
}
