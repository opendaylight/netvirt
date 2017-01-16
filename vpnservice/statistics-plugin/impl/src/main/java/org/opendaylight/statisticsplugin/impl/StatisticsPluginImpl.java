/*
 * Copyright (c) 2016 HPE, Inc. and others. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.statisticsplugin.impl;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.netvirt.vpnmanager.utilities.InterfaceUtils;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.interfaces.rev140508.interfaces.state.Interface;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.GetNodeAggregatedCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.GetNodeAggregatedCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.GetNodeConnectorCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.GetNodeConnectorCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.GetNodeConnectorCountersOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.GetNodeCountersInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.GetNodeCountersOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.StatisticsPluginService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.result.CounterResult;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.result.CounterResultBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.result.counterresult.Groups;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.result.counterresult.GroupsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.result.counterresult.groups.Counters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.statistics.plugin.rev150105.result.counterresult.groups.CountersBuilder;
import org.opendaylight.yangtools.yang.common.RpcError.ErrorType;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@SuppressWarnings("deprecation")
public class StatisticsPluginImpl implements StatisticsPluginService {
    private static final Logger LOG = LoggerFactory.getLogger(StatisticsPluginImpl.class);
    private final DataBroker db;
    private final CounterRetriever counterRetriever;

    public StatisticsPluginImpl(DataBroker db, CounterRetriever counterRetriever) {
        this.db = db;
        this.counterRetriever = counterRetriever;
    }
    
    @Override
    public Future<RpcResult<GetNodeCountersOutput>> getNodeCounters(GetNodeCountersInput input) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @Override
    public Future<RpcResult<GetNodeAggregatedCountersOutput>> getNodeAggregatedCounters(
            GetNodeAggregatedCountersInput input) {
        // TODO Auto-generated method stub
        return null;
    }


    @Override
    @SuppressWarnings("checkstyle:illegalCatch")
    public Future<RpcResult<GetNodeConnectorCountersOutput>> getNodeConnectorCounters(GetNodeConnectorCountersInput input) {
        String portId = input.getPortId();
        LOG.debug("getting port counter of port {}", portId);
        GetNodeConnectorCountersOutputBuilder gpcob = new GetNodeConnectorCountersOutputBuilder();
        
        Interface interfaceState = InterfaceUtils.getInterfaceStateFromOperDS(db, portId);
        if (interfaceState == null) {
            LOG.warn("trying to get counters for non exist port {}", portId);
            return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed().buildFuture();
        }
        
        BigInteger dpId = InterfaceUtils.getDpIdFromInterface(interfaceState);
        if(interfaceState.getLowerLayerIf() == null || interfaceState.getLowerLayerIf().size() == 0){
            LOG.warn("Lowe layer if wasn't found for port {}", portId);
            return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed().buildFuture();
        }
        
        String portNumber = interfaceState.getLowerLayerIf().get(0);
        portNumber = portNumber.split(":")[2];
        List<CounterResult> counterResults = new ArrayList<>();
        
        try {
            if (!getNodeConnectorResult(counterResults, dpId, portNumber)) {
                return RpcResultBuilder.<GetNodeConnectorCountersOutput>failed()
                        .withError(ErrorType.APPLICATION, "failed to get port counters").buildFuture();
            }
        } catch (Exception e) {
            LOG.warn("failed to get counter result for port " + portId, e);
        }
        
        gpcob.setCounterResult(counterResults);
        return RpcResultBuilder.success(gpcob.build()).buildFuture();
    }

    
    
    private boolean getNodeConnectorResult(List<CounterResult> counterResults, BigInteger dpId, String portNumber)
            throws InterruptedException, ExecutionException {
        Map<String, Map<String, BigInteger>> result = counterRetriever.getNodeConnectorCounters(dpId, portNumber);
        if(result == null || result.isEmpty()){
            return false;
        }
        
        CounterResultBuilder crb = new CounterResultBuilder();
        crb.setId(portNumber);
        
        List<Groups> groups = new ArrayList<>();
        for(String groupName : result.keySet()){
            groups.add(createGroupsResult(groupName, result.get(groupName)));
        }
        if(groups.isEmpty()){
            return false;
        }
        crb.setGroups(groups);
        counterResults.add(crb.build());
                
        return !counterResults.isEmpty();
    }

    private Groups createGroupsResult(String groupName, Map<String, BigInteger> countersMap){
        GroupsBuilder gb = new GroupsBuilder();
        gb.setName(groupName);
        
        Map<String, Counters> counters = new HashMap<>();
        List<Counters> countersList = new ArrayList<>();
        for (String counterName : countersMap.keySet()) {
            addCountersToMap(countersMap, counters, counterName);
        }
        for (Counters counter : counters.values()) {
            countersList.add(counter);
        }
        
        gb.setCounters(countersList);
        return gb.build();
    }

    private Counters buildCounter(String counterName, BigInteger value, Counters prevCounter) {
        BigInteger prevValue = BigInteger.ZERO;
        if (prevCounter != null) {
            prevValue = prevCounter.getValue();
        }
        CountersBuilder cb = new CountersBuilder();
        cb.setName(counterName);
        cb.setValue(value.add(prevValue));
        return cb.build();
    }

    private void addCountersToMap(Map<String, BigInteger> result, Map<String, Counters> counters, String counterName) {
        counters.put(counterName, buildCounter(counterName, result.get(counterName), counters.get(counterName)));
    }
}
