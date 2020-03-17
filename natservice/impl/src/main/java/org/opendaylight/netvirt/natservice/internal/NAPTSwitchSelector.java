/*
 * Copyright (c) 2016, 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.natservice.internal;

import java.util.Optional;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jdt.annotation.NonNull;
import org.opendaylight.mdsal.binding.api.DataBroker;
import org.opendaylight.mdsal.common.api.LogicalDatastoreType;
import org.opendaylight.mdsal.common.api.ReadFailedException;
import org.opendaylight.genius.cloudscaler.api.TombstonedNodeManager;
import org.opendaylight.genius.mdsalutil.MDSALUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.NaptSwitches;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitch;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.natservice.rev160111.napt.switches.RouterToNaptSwitchKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.Uint32;
import org.opendaylight.yangtools.yang.common.Uint64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class NAPTSwitchSelector {
    private static final Logger LOG = LoggerFactory.getLogger(NAPTSwitchSelector.class);
    private final DataBroker dataBroker;
    private final TombstonedNodeManager tombstonedNodeManager;

    @Inject
    public NAPTSwitchSelector(final DataBroker dataBroker, final TombstonedNodeManager tombstonedNodeManager) {
        this.dataBroker = dataBroker;
        this.tombstonedNodeManager = tombstonedNodeManager;
    }

    Uint64 selectNewNAPTSwitch(String routerName, List<Uint64> excludeDpns) {
        LOG.info("selectNewNAPTSwitch : Select a new NAPT switch for router {}", routerName);
        Map<Uint64, Integer> naptSwitchWeights = constructNAPTSwitches();
        List<Uint64> routerSwitches = getDpnsForVpn(routerName);
        if (routerSwitches.isEmpty()) {
            LOG.warn("selectNewNAPTSwitch : Delaying NAPT switch selection due to no dpns scenario for router {}",
                    routerName);
            return Uint64.ZERO;
        }
        try {
            if (excludeDpns != null) {
                routerSwitches.removeAll(excludeDpns);
            }
            LOG.debug("selectNewNAPTSwitch : routerSwitches before filtering : {}", routerSwitches);
            routerSwitches = tombstonedNodeManager.filterTombStoned(routerSwitches);
            LOG.debug("selectNewNAPTSwitch : routerSwitches after filtering : {}", routerSwitches);
        } catch (InterruptedException | ExecutionException ex) {
            LOG.error("selectNewNAPTSwitch : filterTombStoned Exception thrown", ex);
        }
        Set<SwitchWeight> switchWeights = new TreeSet<>();
        for (Uint64 dpn : routerSwitches) {
            if (naptSwitchWeights.get(dpn) != null) {
                switchWeights.add(new SwitchWeight(dpn, naptSwitchWeights.get(dpn)));
            } else {
                switchWeights.add(new SwitchWeight(dpn, 0));
            }
        }

        Uint64 primarySwitch;

        if (!switchWeights.isEmpty()) {

            LOG.debug("selectNewNAPTSwitch : Current switch weights for router {} - {}", routerName, switchWeights);

            RouterToNaptSwitchBuilder routerToNaptSwitchBuilder =
                new RouterToNaptSwitchBuilder().setRouterName(routerName);
            SwitchWeight firstSwitchWeight = switchWeights.iterator().next();
            primarySwitch = firstSwitchWeight.getSwitch();
            RouterToNaptSwitch id = routerToNaptSwitchBuilder.setPrimarySwitchId(primarySwitch).build();

            MDSALUtil.syncWrite(dataBroker, LogicalDatastoreType.CONFIGURATION,
                getNaptSwitchesIdentifier(routerName), id);

            LOG.debug("selectNewNAPTSwitch : successful addition of RouterToNaptSwitch to napt-switches container");
            return primarySwitch;
        } else {
            primarySwitch = Uint64.ZERO;

            LOG.debug("selectNewNAPTSwitch : switchWeights empty, primarySwitch: {} ", primarySwitch);
            return primarySwitch;
        }
    }

    private Map<Uint64, Integer> constructNAPTSwitches() {
        Optional<NaptSwitches> optNaptSwitches =
            MDSALUtil.read(dataBroker, LogicalDatastoreType.CONFIGURATION, getNaptSwitchesIdentifier());
        Map<Uint64, Integer> switchWeights = new HashMap<>();

        if (optNaptSwitches.isPresent()) {
            NaptSwitches naptSwitches = optNaptSwitches.get();

            for (RouterToNaptSwitch naptSwitch : naptSwitches.nonnullRouterToNaptSwitch()) {
                Uint64 primarySwitch = naptSwitch.getPrimarySwitchId();
                //update weight
                Integer weight = switchWeights.get(primarySwitch);
                if (weight == null) {
                    switchWeights.put(primarySwitch, 1);
                } else {
                    switchWeights.put(primarySwitch, ++weight);
                }
            }
        }
        return switchWeights;
    }

    private InstanceIdentifier<NaptSwitches> getNaptSwitchesIdentifier() {
        return InstanceIdentifier.create(NaptSwitches.class);
    }

    private InstanceIdentifier<RouterToNaptSwitch> getNaptSwitchesIdentifier(String routerName) {
        return InstanceIdentifier.builder(NaptSwitches.class)
            .child(RouterToNaptSwitch.class, new RouterToNaptSwitchKey(routerName)).build();
    }

    @NonNull
    public List<Uint64> getDpnsForVpn(String routerName) {
        LOG.debug("getDpnsForVpn: called for RouterName {}", routerName);
        Uint32 bgpVpnId = NatUtil.getBgpVpnId(dataBroker, routerName);
        // TODO Why?
        if (bgpVpnId != NatConstants.INVALID_ID) {
            return NatUtil.getDpnsForRouter(dataBroker, routerName);
        }
        return NatUtil.getDpnsForRouter(dataBroker, routerName);
    }

    private static class SwitchWeight implements Comparable<SwitchWeight> {
        private final Uint64 swich;
        private int weight;

        SwitchWeight(Uint64 swich, int weight) {
            this.swich = swich;
            this.weight = weight;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (swich == null ? 0 : swich.hashCode());
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
            SwitchWeight other = (SwitchWeight) obj;
            if (swich == null) {
                if (other.swich != null) {
                    return false;
                }
            } else if (!swich.equals(other.swich)) {
                return false;
            }
            return true;
        }

        public Uint64 getSwitch() {
            return swich;
        }

        public int getWeight() {
            return weight;
        }

        @Override
        public int compareTo(@NonNull SwitchWeight switchWeight) {
            return weight - switchWeight.getWeight();
        }
    }
}
