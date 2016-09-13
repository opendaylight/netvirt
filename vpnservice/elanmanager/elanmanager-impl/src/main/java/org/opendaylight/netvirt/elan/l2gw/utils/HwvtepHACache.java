package org.opendaylight.netvirt.elan.l2gw.utils;

import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Created by eaksahu on 9/13/2016.
 */
public class HwvtepHACache {
    static ConcurrentHashMap<InstanceIdentifier<Node>,Set<InstanceIdentifier<Node>>> haParentToChildMap = new ConcurrentHashMap<>();

    public static boolean isHAParentNode(InstanceIdentifier<Node> node) {
        return haParentToChildMap.containsKey(node);
    }

    public static Set<InstanceIdentifier<Node>> getChildrenForHANode(InstanceIdentifier<Node> parent) {
        if (parent != null && haParentToChildMap.containsKey(parent)) {
            return new HashSet(haParentToChildMap.get(parent));
        } else {
            return Collections.EMPTY_SET;
        }
    }
}
