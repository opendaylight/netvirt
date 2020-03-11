/*
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.netvirt.fibmanager;

public enum FibCounterUtils {

    vrfentry_add("vrfentry.add"),
    vrfentry_update("vrfentry.update"),
    vrfentry_remove("vrfentry.remove");

    private static final String PROJECT = "netvirt";
    private static final String MODULE = "fibmanager";
    private static final String VRF_COUNTER_ID = "vrfentry";


    public static String getProject() {
        return PROJECT;
    }

    public static String getModule() {
        return MODULE;
    }

    public static String getVrfCounterId() {
        return VRF_COUNTER_ID;
    }



    String label;
    FibCounterUtils(String label) {
        this.label = label;

    }

    @Override
    public String toString() {
        return label;
    }
}
