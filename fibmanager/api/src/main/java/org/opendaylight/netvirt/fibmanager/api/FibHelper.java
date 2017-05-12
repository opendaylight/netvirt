/*
 * Copyright (c) 2017 Ericsson India Global Services Pvt Ltd. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.netvirt.fibmanager.api;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.FibEntries;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTables;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.fibentries.VrfTablesKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntry;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentries.VrfEntryKey;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePaths;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePathsBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.netvirt.fibmanager.rev150330.vrfentrybase.RoutePathsKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.InstanceIdentifierBuilder;

public final class FibHelper {

    private FibHelper() { }

    public static RoutePaths buildRoutePath(String nextHop, Long label) {
        RoutePathsBuilder builder = new RoutePathsBuilder()
                .setKey(new RoutePathsKey(nextHop))
                .setNexthopAddress(nextHop);
        if (label != null) {
            builder.setLabel(label);
        }
        return builder.build();
    }

    public static VrfEntryBuilder getVrfEntryBuilder(String prefix, RouteOrigin origin, String parentVpnRd) {
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setOrigin(origin.getValue()).setParentVpnRd(parentVpnRd);
    }

    public static VrfEntryBuilder getVrfEntryBuilder(String prefix, List<RoutePaths> routePaths,
            RouteOrigin origin, String parentVpnRd) {
        return new VrfEntryBuilder().setKey(new VrfEntryKey(prefix)).setDestPrefix(prefix)
                .setRoutePaths(routePaths).setOrigin(origin.getValue()).setParentVpnRd(parentVpnRd);
    }

    public static VrfEntryBuilder getVrfEntryBuilder(String prefix, long label, String nextHop, RouteOrigin origin,
            String parentVpnRd) {
        if (nextHop != null) {
            RoutePaths routePath = buildRoutePath(nextHop, label);
            return getVrfEntryBuilder(prefix, Arrays.asList(routePath), origin, parentVpnRd);
        } else {
            return getVrfEntryBuilder(prefix, origin, parentVpnRd);
        }
    }

    public static VrfEntryBuilder getVrfEntryBuilder(VrfEntry vrfEntry, long label,
            List<String> nextHopList, RouteOrigin origin, String parentvpnRd) {
        List<RoutePaths> routePaths =
                nextHopList.stream().map(nextHop -> buildRoutePath(nextHop, label))
                        .collect(toList());
        return getVrfEntryBuilder(vrfEntry.getDestPrefix(), routePaths, origin, parentvpnRd);
    }

    public static InstanceIdentifier<RoutePaths> buildRoutePathId(String rd, String prefix, String nextHop) {
        InstanceIdentifierBuilder<RoutePaths> idBuilder =
                InstanceIdentifier.builder(FibEntries.class)
                        .child(VrfTables.class, new VrfTablesKey(rd))
                        .child(VrfEntry.class, new VrfEntryKey(prefix))
                        .child(RoutePaths.class, new RoutePathsKey(nextHop));
        return idBuilder.build();
    }

    public static boolean isControllerManagedRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.STATIC
                || routeOrigin == RouteOrigin.CONNECTED
                || routeOrigin == RouteOrigin.LOCAL
                || routeOrigin == RouteOrigin.INTERVPN;
    }

    public static boolean isControllerManagedNonInterVpnLinkRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.STATIC
                || routeOrigin == RouteOrigin.CONNECTED
                || routeOrigin == RouteOrigin.LOCAL;
    }

    public static boolean isControllerManagedVpnInterfaceRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.STATIC
                || routeOrigin == RouteOrigin.LOCAL;
    }

    public static boolean isControllerManagedSelfImportedRoute(RouteOrigin routeOrigin) {
        return routeOrigin == RouteOrigin.SELF_IMPORTED;
    }

    public static void sortIpAddress(List<RoutePaths> routePathList) {
        if (routePathList != null) {
            routePathList.sort(comparing(RoutePaths::getNexthopAddress));
        }
    }

    public static InstanceIdentifier<RoutePaths> getRoutePathsIdentifier(String rd, String prefix, String nh) {
        return InstanceIdentifier.builder(FibEntries.class)
                .child(VrfTables.class,new VrfTablesKey(rd)).child(VrfEntry.class,new VrfEntryKey(prefix))
                .child(RoutePaths.class, new RoutePathsKey(nh)).build();
    }

    public static List<String> getNextHopListFromRoutePaths(final VrfEntry vrfEntry) {
        List<RoutePaths> routePaths = vrfEntry.getRoutePaths();
        if (routePaths == null || routePaths.isEmpty()) {
            return new ArrayList<>();
        }
        return routePaths.stream()
                .map(RoutePaths::getNexthopAddress)
                .collect(Collectors.toList());
    }

    public static com.google.common.base.Optional<VrfEntry> getVrfEntry(DataBroker broker, String rd, String ipPrefix) {
        InstanceIdentifier<VrfEntry> vrfEntryId = InstanceIdentifier.builder(FibEntries.class)
            .child(VrfTables.class, new VrfTablesKey(rd))
            .child(VrfEntry.class, new VrfEntryKey(ipPrefix)).build();
        return read(broker, LogicalDatastoreType.CONFIGURATION, vrfEntryId);
    }

    private static <T extends DataObject> com.google.common.base.Optional<T> read(DataBroker broker,
            LogicalDatastoreType datastoreType, InstanceIdentifier<T> path) {
        try (ReadOnlyTransaction tx = broker.newReadOnlyTransaction()) {
            return tx.read(datastoreType, path).get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    /** get true if this prefix is an IPv4 version, false otherwise.
     * @param prefix the prefix as (x.x.x.x/nn) to find if it is in IP version 4
     * @return true if it is an IPv4 or false if it is not.
     */
    public static boolean isIpv4Prefix(String prefix) {
        boolean rep = false;
        if (prefix == null || prefix.length() < 7) {
            return rep;
        }
        try {
            String ip = getIpFromPrefix(prefix);
            java.net.Inet4Address.getByName(ip);
            rep = true;
        } catch (SecurityException | UnknownHostException | ClassCastException e) {
            rep = false;
            return rep;
        }
        return rep;
    }

    /** get true if this prefix is an IPv6 version, false otherwise.
     * @param prefix the prefix as ( x:x:x::/nn) to find if it is in IP version 6
     * @return true if it is an IPv4 or false if it is not.
     */
    public static boolean isIpv6Prefix(String prefix) {
        boolean rep = false;
        if (prefix == null || prefix.length() < 2) {
            return rep;
        }
        try {
            String ip = getIpFromPrefix(prefix);
            java.net.Inet6Address.getByName(ip);
            rep = true;
        } catch (SecurityException | UnknownHostException | ClassCastException e) {
            rep = false;
            return rep;
        }
        return rep;
    }

    /**get String format IP from prefix as x.x.....x/nn.
     * @param prefix the prefix as IPv4 or IPv6 as x.....x/nn
     * @return prefix if "/" is unfindable or the IP only as x.x...x from x.x......x/nn
     */
    public static String getIpFromPrefix(String prefix) {
        if (prefix == null || prefix.length() < 2) {
            return null;
        }
        String rep = prefix;
        String[] prefixValues = prefix.split("/");
        if (prefixValues.length > 0) {
            rep = prefixValues[0];
        }
        return rep;
    }

    /**Return true if this prefix or subnet is belonging the specified subnetwork.
     * @param prefixToTest the prefix which could be in the subnet
     * @param subnet the subnet that have to contain the prefixToTest to return true
     * @return true if the param subnet contained the prefixToTest false otherwise
     */
    public static boolean isBelongingPrefix(String prefixToTest, String subnet) {
        return doesPrefixBelongToSubnet(prefixToTest, subnet, true);
    }

    /**Return true if this prefix or subnet is belonging the specified subnetwork.
     * @param prefixToTest the prefix which could be in the subnet
     * @param subnet the subnet that have to contain the prefixToTest to return true
     * @param exactMatch boolean set to true if exact match is expected
     * @return true if the param subnet contained the prefixToTest false otherwise
     */
    public static boolean doesPrefixBelongToSubnet(String prefixToTest, String subnet, boolean exactMatch) {
        if (prefixToTest == null || prefixToTest.length() < 7 || subnet == null || subnet.length() < 7) {
            return false;
        }
        if (isIpv4Prefix(prefixToTest) && isIpv4Prefix(subnet)
                || isIpv6Prefix(prefixToTest) && isIpv6Prefix(subnet)) {

            int ipVersion = 4;
            if (isIpv6Prefix(prefixToTest)) {
                ipVersion = 6;
            }

            String ipPref = getIpFromPrefix(prefixToTest);
            String ipSub = getIpFromPrefix(subnet);
            String maskSubString = subnet.substring(subnet.indexOf("/") + 1);
            String maskPrefString;
            if (prefixToTest.contains("/")) {
                maskPrefString = prefixToTest.substring(prefixToTest.indexOf("/") + 1);
            } else if (ipVersion == 4) {
                maskPrefString = "32";
            } else {
                maskPrefString = "128";
            }

            try {
                int maskPref = Integer.parseInt(maskPrefString);
                int maskSub = Integer.parseInt(maskSubString);
                if (exactMatch && maskPref != maskSub) {
                 /*because the mask must be exactly the same between them, the return type is false. This behavior could
                  * be changed to ignored it in including a boolean options to force or not the same mask control*/
                    return false;
                }
                BigInteger maskSubBig = getMaskNetwork(ipVersion, maskSub);
                byte[] byteIpSub = InetAddress.getByName(ipSub).getAddress();
                byte[] byteIpPref = InetAddress.getByName(ipPref).getAddress();
                BigInteger netFromIpSub = packBigInteger(byteIpSub).and(maskSubBig);
                BigInteger netFromIpPref = packBigInteger(byteIpPref).and(maskSubBig);
                return netFromIpSub.compareTo(netFromIpPref) == 0;
            } catch (NumberFormatException | UnknownHostException e) {
                return false;
            }
        }
        return false;
    }

    /**This method converts a byte[] to a BigInteger value.
     * @param bytes the byte[] of IP
     * @return (big) integer value of IP_bytes
     */
    public static BigInteger packBigInteger(byte[] bytes) {
        BigInteger val = BigInteger.valueOf(0);
        for (byte b : bytes) {
            val = val.shiftLeft(8);
            val = val.add(BigInteger.valueOf(b & 0xff));
        }
        return val;
    }

    /**This methode return the bytes representation from an IP.
     * @param ipBigInteger value integer of IP to convert
     * @param isIpv4 if is ipv4 setup to true if ipv6 setup to false
     * @return byte[] which contained the representation of bytes from th ip value
     */
    public static byte[] unpackBigInteger(BigInteger ipBigInteger, boolean isIpv4) {
        int sizeBloc = 4;
        if (!isIpv4) {
            sizeBloc = 128 / 8;// if ipv6 size of dataIP is 128 bits
        }
        byte[] res = new byte[sizeBloc];
        for (int i = 0 ; i < sizeBloc ; i++) {
            BigInteger bigInt = ipBigInteger.shiftRight(i * 8);
            bigInt = bigInt.and(BigInteger.valueOf(0xFF));
            res[sizeBloc - 1 - i] = bigInt.byteValue();
        }
        return res;
    }

    /**get the bits cache mask of net for a ip version type.
     * @param ipVersion version of ip must be 4 or 6
     * @param mask the lengh of the mask of net as 24 from this representation 10.1.1.0/24 or 64 for 2001::1/64
     * @return the bit mask of net ex: x.x.x.x/24 return a BigInteger == 0xFFFFFFotherwise null if any error
     */
    public static BigInteger getMaskNetwork(int ipVersion, int mask) {
        int lenghBitsIp = 0;
        if (ipVersion == 6) {
            lenghBitsIp = 128 - 1;
        } else if (ipVersion == 4) {
            lenghBitsIp = 32 - 1;
        } else {
            return null;//ip version is unknown
        }
        BigInteger bb = BigInteger.ZERO;
        for (int i = 0 ; i < mask; i++) {
            bb = bb.setBit(lenghBitsIp - i);
        }
        return bb;
    }
}
