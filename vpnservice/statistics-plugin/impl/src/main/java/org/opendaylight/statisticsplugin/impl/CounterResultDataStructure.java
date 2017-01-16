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

public class CounterResultDataStructure {

    private Map<String, Map<String, Map<String, BigInteger>>> results;

    public CounterResultDataStructure() {
        results = new HashMap<>();
    }

    public Map<String, Map<String, Map<String, BigInteger>>> getResults() {
        return results;
    }

    public void addCounterResult(String id, Map<String, Map<String, BigInteger>> counterResult) {
        results.put(id, counterResult);
    }
    
    public void addCounterResult(String id){
        results.put(id, new HashMap<>());
    }

    public void addCounterGroup(String id, String groupName, Map<String, BigInteger> counters) {
        if(results.get(id) != null){
            results.get(id).put(groupName, counters);
        }
    }
    
    public Map<String, Map<String, BigInteger>> getGrups(String id){
        return results.get(id);
    }

}
