/*
 * Copyright (c) 2016 Inocybe Technologies and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.vpnservice.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.nic.utils.MdsalUtils;
import org.opendaylight.vpnservice.utils.IidFactory;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.MplsLabels;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.labels.Label;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.labels.LabelBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.labels.LabelKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.vpnintent.rev150105.vpn.intent.Endpoint;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MplsLabelManagerService {

    private static final Logger LOG = LoggerFactory.getLogger(MplsLabelManagerService.class);
    private static final Integer MAX_MPLS_LABEL = 524288;
    private static InstanceIdentifier<Label> LABEL_IID = null;
    public static final InstanceIdentifier<MplsLabels> MPLS_LABELS_IID = IidFactory.getMplsLabelsIid();
    private final DataBroker dataBroker;
    private final Random random = new Random();
    private final MdsalUtils mdsal;

    public MplsLabelManagerService(DataBroker dataBroker) {
        this.dataBroker = dataBroker;
        this.mdsal = new MdsalUtils(this.dataBroker);
    }

    /**
     * Generate a unique Mpls Label
     * Mpls label is of length 20 bits maximum
     * @return :next unique Mpls label value
     */
    public Long getUniqueLabel(Endpoint endpoint)  {
        Long nextUniqueLabel = (long) random.nextInt(MAX_MPLS_LABEL);
        while(checkIsLabelUsed(nextUniqueLabel)) {
            nextUniqueLabel = (long) random.nextInt(MAX_MPLS_LABEL);
        }
        updateToLabelStore(nextUniqueLabel, endpoint, true);
        return nextUniqueLabel;
    }

    /**
     * Delete label from datastore
     * @param endpoint :endpoint whose label needs to be deleted
     */
    public void deleteLabel(Endpoint endpoint)  {
        Map<Long, String> labelMap = getAllLabels();
        for (Map.Entry<Long, String> labelEntry : labelMap.entrySet()) {
            if(labelEntry.getValue().equalsIgnoreCase(endpoint.getSiteName())) {
                updateToLabelStore(labelEntry.getKey(), endpoint, false);
            }
        }
    }

    /**
     * Update the model for Labels with used Mpls label values
     * @param label :mpls label allocated to an endpoint
     * @param endpoint :endpoint to which mpls label is allocated to
     * @param add :true if add label to datastore, false to delete label from datastore
     */
    private void updateToLabelStore(Long label, Endpoint endpoint, boolean add) {
        LABEL_IID = IidFactory.getLabelIid(label);
        Label mplsLabel = new LabelBuilder().
                setKey(new LabelKey(label)).
                setLabelId(label).
                setSiteName(endpoint.getSiteName()).
                setIpPrefix(endpoint.getIpPrefix()).
                setSwitchPortId(endpoint.getSwitchPortId()).build();

        if(add) {
            mdsal.put(LogicalDatastoreType.OPERATIONAL, LABEL_IID, mplsLabel);
            LOG.info("Add mpls label to operational datastore: {} for endpoint: {}", label, endpoint.getSiteName());
        } else {
            mdsal.delete(LogicalDatastoreType.OPERATIONAL, LABEL_IID);
            LOG.info("Delete mpls label from operational datastore: {} for endpoint: {}", label, endpoint.getSiteName());
        }
    }

    /**
     * Check if label is already allocated to any endpoint
     * @param nextUniqueLabel :value of mpls label
     * @return :true is label is already used else false
     */
    private boolean checkIsLabelUsed(Long nextUniqueLabel) {
        Map<Long, String> labelMap = getAllLabels();
        if(labelMap.containsKey(nextUniqueLabel)) {
            return true;
        }
        return false;
    }

    /**
     * Get a map of all the labels allocated to the endpoints
     * @return :hashmap of labels as key, site names as value
     */
    private Map<Long, String> getAllLabels() {
        Map<Long, String> labelMap = new HashMap<>();
        MplsLabels mplsLabels = mdsal.read(LogicalDatastoreType.OPERATIONAL, MPLS_LABELS_IID);
        if(mplsLabels != null) {
            for (Label label : mplsLabels.getLabel()) {
                labelMap.put(label.getLabelId(), label.getSiteName());
            }
        }
        return labelMap;
    }
}
