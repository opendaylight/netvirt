package org.opendaylight.bgpmanager.api;

import java.util.Collection;

public interface IBgpManager {

    /**
     *
     * @param rd
     * @param importRts
     * @param exportRts
     */
    public void addVrf(String rd, Collection<String> importRts, Collection<String> exportRts);

    /**
     *
     * @param rd
     */
    public void deleteVrf(String rd);

    /**
     *
     * @param rd
     * @param prefix
     * @param nextHop
     * @param vpnLabel
     */
    public void addPrefix(String rd, String prefix, String nextHop, int vpnLabel);

    /**
     *
     * @param rd
     * @param prefix
     */
    public void deletePrefix(String rd, String prefix);

}