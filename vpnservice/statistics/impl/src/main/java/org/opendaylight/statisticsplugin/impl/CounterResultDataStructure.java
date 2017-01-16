/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.statisticsplugin.impl;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CounterResultDataStructure {

    private Map<String, Map<String, Map<String, BigInteger>>> results;

    public CounterResultDataStructure() {
        results = new HashMap<>();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((results == null) ? 0 : results.hashCode());
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
        CounterResultDataStructure other = (CounterResultDataStructure) obj;
        if (!results.equals(other.results)) {
            return false;
        }
        return true;
    }

    public Map<String, Map<String, Map<String, BigInteger>>> getResults() {
        return results;
    }

    public void addCounterResult(String id, Map<String, Map<String, BigInteger>> counterResult) {
        results.put(id, counterResult);
    }

    public void addCounterResult(String id) {
        results.put(id, new HashMap<>());
    }

    public void addCounterGroup(String id, String groupName, Map<String, BigInteger> counters) {
        if (results.get(id) != null) {
            results.get(id).put(groupName, counters);
        }
    }

    public Map<String, Map<String, BigInteger>> getGroups(String id) {
        return results.get(id);
    }

    public Set<String> getGroupNames() {
        if (results.isEmpty()) {
            return null;
        }

        return results.get(results.keySet().iterator().next()).keySet();
    }

    public void addCounterToGroup(String id, String groupName, String counterName, BigInteger counterValue) {
        if (results.get(id) == null) {
            return;
        }

        if (results.get(id).get(groupName) == null) {
            results.get(id).put(groupName, new HashMap<>());
        }

        results.get(id).get(groupName).put(counterName, counterValue);
    }

    public Set<String> getGroupCounterNames(String groupName) {
        if (results.isEmpty()) {
            return null;
        }

        Map<String, BigInteger> group = results.get(results.keySet().iterator().next()).get(groupName);
        if (group == null) {
            return null;
        }

        return group.keySet();
    }

    public boolean isEmpty() {
        return results.isEmpty();
    }

}
