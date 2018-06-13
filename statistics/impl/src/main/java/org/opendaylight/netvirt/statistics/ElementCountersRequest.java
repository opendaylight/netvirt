/*
 * Copyright (c) 2017 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.statistics;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;

public class ElementCountersRequest {

    private ElementCountersDirection direction;
    private String portId;
    private BigInteger dpn;
    private int lportTag;
    private Map<String, Map<String, String>> filters;

    public ElementCountersRequest(String portId) {
        this.portId = portId;
        filters = new HashMap<>();
    }

    public void setElementCountersDirection(ElementCountersDirection elementCountersDirection) {
        this.direction = elementCountersDirection;
    }

    public String getPortId() {
        return portId;
    }

    public ElementCountersDirection getDirection() {
        return direction;
    }

    public Map<String, Map<String, String>> getFilters() {
        return filters;
    }

    public void addFilterGroup(String filterGroupName) {
        filters.put(filterGroupName, new HashMap<>());
    }

    public Map<String, String> getFilterGroup(String filterGroupName) {
        return filters.get(filterGroupName);
    }

    public void addFilterToFilterGroup(String filterGroupName, String filterName, String value) {
        if (filters.get(filterGroupName) == null) {
            addFilterGroup(filterGroupName);
        }
        filters.get(filterGroupName).put(filterName, value);
    }

    public String getFilterFromFilterGroup(String filterGroupName, String filterName) {
        if (filters.containsKey(filterGroupName)) {
            return filters.get(filterGroupName).get(filterName);
        }

        return null;
    }

    public int getLportTag() {
        return lportTag;
    }

    public void setLportTag(int lportTag) {
        this.lportTag = lportTag;
    }

    public BigInteger getDpn() {
        return dpn;
    }

    public void setDpn(BigInteger dpn) {
        this.dpn = dpn;
    }

    public boolean isFilterGroupExist(String filterGroupName) {
        return filters.containsKey(filterGroupName);
    }

    public boolean isFilterExist(String filterGroupName, String filterName) {
        if (!isFilterGroupExist(filterGroupName)) {
            return false;
        }

        return filters.get(filterGroupName).containsKey(filterName);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((direction == null) ? 0 : direction.hashCode());
        result = prime * result + ((dpn == null) ? 0 : dpn.hashCode());
        result = prime * result + ((filters == null) ? 0 : filters.hashCode());
        result = prime * result + lportTag;
        result = prime * result + ((portId == null) ? 0 : portId.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        ElementCountersRequest other = (ElementCountersRequest) obj;
        if (direction != other.direction) {
            return false;
        }
        if (dpn == null) {
            if (other.dpn != null) {
                return false;
            }
        } else if (!dpn.equals(other.dpn)) {
            return false;
        }
        if (filters == null) {
            if (other.filters != null) {
                return false;
            }
        } else if (!filters.equals(other.filters)) {
            return false;
        }
        if (lportTag != other.lportTag) {
            return false;
        }
        if (portId == null) {
            if (other.portId != null) {
                return false;
            }
        } else if (!portId.equals(other.portId)) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "ElementCountersRequest [direction=" + direction + ", portId=" + portId + ", dpn=" + dpn + ", lportTag="
                + lportTag + ", filters=" + filters + "]";
    }
}
