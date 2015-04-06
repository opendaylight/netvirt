/*
 * Copyright (c) 2013 Ericsson AB.  All rights reserved.
 *
 */
package org.opendaylight.vpnservice.mdsalutil;

import java.io.Serializable;
import java.util.List;

import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;

public class BucketInfo implements Serializable {
    private static final long serialVersionUID = 1L;
    private final List<ActionInfo> m_listActionInfo;
    private Integer weight = 0;
    private Long watchPort = 0xffffffffL;
    private Long watchGroup = 0xffffffffL;

    public BucketInfo(List<ActionInfo> listActions) {
        m_listActionInfo = listActions;
    }

    public BucketInfo(List<ActionInfo> m_listActionInfo, Integer weight, Long watchPort, Long watchGroup) {
        super();
        this.m_listActionInfo = m_listActionInfo;
        this.weight = weight;
        this.watchPort = watchPort;
        this.watchGroup = watchGroup;
    }

    public void buildAndAddActions(List<Action> listActionOut) {
        int key = 0;
        if (m_listActionInfo != null) {
            for (ActionInfo actionInfo : m_listActionInfo) {
                actionInfo.setActionKey(key++);
                listActionOut.add(actionInfo.buildAction());
            }
        }
    }

    public void setWeight(Integer bucketWeight) {
        weight = bucketWeight;
    }

    public Integer getWeight() {
        return weight;
    }

    public List<ActionInfo> getActionInfoList() {
        return m_listActionInfo;
    }

    public Long getWatchPort() {
        return watchPort;
    }

    public void setWatchPort(Long watchPort) {
        this.watchPort = watchPort;
    }

    public Long getWatchGroup() {
        return watchGroup;
    }

    public void setWatchGroup(Long watchGroup) {
        this.watchGroup = watchGroup;
    }
}
