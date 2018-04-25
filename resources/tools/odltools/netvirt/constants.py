VIF_TYPE_TO_PREFIX = {
                      'ovs':'tap',
                      'vhost_user':'vhu'
                      }

VIF_TYPE = 'neutron-binding:vif-type'
IFACE_PARENT = 'odl-interface:parent-interface'
VIF_TYPE = 'neutron-binding:vif-type'
IFTYPE_VLAN = 'iana-if-type:l2vlan'
IFTYPE_TUNNEL = 'iana-if-type:tunnel'

NODE_GROUP = 'flow-node-inventory:group'
NODE_TABLE = 'flow-node-inventory:table'

DSM_FILE = 0
DSM_DSTYPE = 1
DSM_PATH = 2
DSM_ROOT1 = 3
DSM_ROOT2 = 4
"""
   source map to fetch data from data store.
   Format is:
   resource-key-name:[filename,datastore_type,resource-url,container-name,list-title]
"""

DSMAP = {
    'acls': ['acls.json', 'config','ietf-access-control-list:access-lists',
             'access-lists', 'acls'],
    'bindings': ['service-bindings.json', 'config', 'interface-service-bindings:service-bindings',
                 'service-bindings', 'services-info'],
    'bound-servicesstate': ['bound-services-state-list.json', 'operational',
                            'interface-service-bindings:bound-services-state-list',
                            'bound-services-state-list', 'bound-services-state'],
    'dpnendpoints': ['dpn-endpoints.json', 'config', 'itm-state:dpn-endpoints',
                     'dpn-endpoints', 'DPN-TEPs-info'],
    'bgp': ['ebgp-bgp.json', 'config', 'ebgp:bgp',
            'bgp', 'tbd'],
    'elan-dpninterfaces': ['elan-dpn-interfaces.json', 'operational', 'elan:elan-dpn-interfaces',
                           'elan-dpn-interfaces', 'elan-dpn-interfaces-list'],
    'elan-forwardingtables': ['elan-forwarding-tables.json', 'operational', 'elan:elan-forwarding-tables',
                              'elan-forwarding-tables', 'mac-tables'],
    'elaninstances': ['elan-instances.json', 'config', 'elan:elan-instances',
                      'elan-instances', 'elan-instance'],
    'elaninterfaces': ['elan-interfaces.json', 'config', 'elan:elan-interfaces',
                       'elan-interfaces', 'elan-interface'],
    'elanstate': ['elan-state.json', 'config', 'elan:elan-state',
                  'elan-instances', 'elan-instance'],
    'fibentries': ['fibentries.json', 'config', 'odl-fib:fibEntries',
                   'fibEntries', 'vrfTables'],
    'idpools': ['idpools.json', 'config', 'id-manager:id-pools',
                'id-pools', 'id-pool'],
    'ifconfig': ['iface-config.json', 'config', 'ietf-interfaces:interfaces',
                 'interfaces', 'interface'],
    'itmconfig': ['itm-config.json', 'config', 'itm-state:dpn-teps-state',
                  'dpn-teps-state', 'dpns-teps'],
    'ifchildinfo': ['ifchildinfo.json', 'config', 'odl-interface-meta:interface-child-info',
                    'interface-child-info', 'interface-parent-entry'],
    'ifindexes': ['ifindexes.json', 'operational', 'odl-interface-meta:if-indexes-interface-map',
                  'if-indexes-interface-map', 'if-index-interface'],
    'ifstate': ['ifstate.json', 'operational', 'ietf-interfaces:interfaces-state',
                'interfaces-state', 'interface'],
    'inventory': ['inventory-config.json', 'config',
                  'opendaylight-inventory:nodes', 'nodes', 'node'],
    'inventory-oper': ['inventory-oper.json', 'oper',
                       'opendaylight-inventory:nodes', 'nodes', 'node'],
    'l3nexthop': ['l3nexthop.json', 'operational', 'l3nexthop:l3nexthop',
                  'l3nexthop', 'vpnNextHops'],
    'labelroutemap': ['label-route-map.json', 'operational', 'odl-fib:label-route-map',
                      'label-route-map', 'tbd'],
    'learntvpnvip-to-port': ['learnt-vpn-vip-to-port-data.json', 'operational',
                             'odl-l3vpn:learnt-vpn-vip-to-port-data',
                             'learnt-vpn-vip-to-port-data', 'learnt-vpn-vip-to-port'],
    'nat-extrouters': ['nat-ext-routers.json', 'config', 'odl-nat:ext-routers',
                       'ext-routers', 'routers'],
    'nat-extnetworks': ['nat-external-networks.json', 'config', 'odl-nat:external-networks',
                        'external-networks', 'networks'],
    'nat-extsubnets': ['nat-external-subnets.json', 'config', 'odl-nat:external-subnets',
                       'external-subnets', 'subnets'],
    'nat-flowtingip': ['nat-floating-ip-info.json', 'config', 'odl-nat:floating-ip-info',
                       'floating-ip-info', 'router-ports'],
    'nat-flowtingip-oper': ['nat-floating-ip-info-oper.json', 'operational', 'odl-nat:floating-ip-info',
                            'floating-ip-info', 'router-ports'],
    'nat-intextip-map': ['nat-intext-ip-map.json', 'operational', 'odl-nat:intext-ip-map',
                         'intext-ip-port-map', 'tbd'],
    'nat-intextip-portmap': ['nat-intext-ip-port-map.json', 'config', 'odl-nat:intext-ip-port-map',
                             'intext-ip-port-map', 'tbd'],
    'nat-naptswitches': ['nat-napt-switches.json', 'config', 'odl-nat:napt-switches',
                         'napt-switches', 'router-to-napt-switch'],
    'nat-routeridname': ['nat-router-id-name.json', 'config', 'odl-nat:router-id-name',
                         'router-id-name', 'routerIds'],
    'nat-snatintip-portmap': ['nat-snatint-ip-port-map.json', 'config', 'odl-nat:snatint-ip-port-map',
                              'snatint-ip-port-map', 'tbd'],
    'neutron-routerdpns': ['neutron-router-dpns.json', 'operational', 'odl-l3vpn:neutron-router-dpns',
                           'neutron-router-dpns', 'router-dpn-list'],
    'neutronnetworks': ['neutron-networks.json', 'config', 'neutron:neutron/networks',
                        'networks', 'network'],
    'neutronports': ['neutron-ports.json', 'config', 'neutron:neutron/ports',
                     'ports', 'port'],
    'neutronrouters': ['neutron-routers.json', 'config', 'neutron:neutron/routers',
                       'routers', 'router'],
    'neutronsubnets': ['neutron-subnets.json', 'config', 'neutron:neutron/subnets',
                       'subnets', 'subnet'],
    'neutrontrunks': ['neutron-trunks.json', 'config', 'neutron:neutron/trunks',
                      'trunks', 'trunk'],
    'neutronvpn-networkmap': ['neutronvpn-networkmaps.json', 'config', 'neutronvpn:networkMaps',
                              'networkMaps', 'networkMap'],
    'neutronvpn-portip': ['neutronvpn-portip-port.json', 'config', 'neutronvpn:neutron-vpn-portip-port-data',
                          'neutron-vpn-portip-port-data', 'vpn-portip-to-port'],
    'neutronvpn-routerinterfaces-map': ['neutronvpn-routerinterfacesmap.json', 'config',
                                        'neutronvpn:router-interfaces-map',
                                        'router-interfaces-map', 'tbd'],
    'neutronvpn-subnetmap': ['neutronvpn-sunbnetmaps.json', 'config', 'neutronvpn:subnetMaps',
                             'subnetMaps', 'subnetMap'],
    'neutronvpn-vpnmap': ['neutronvpn-vpnmaps.json', 'config', 'neutronvpn:vpnMaps',
                          'vpnMaps', 'vpnMap'],
    'ovsdb': ['ovsdb-config.json', 'config', 'network-topology:network-topology',
              'network-topology', 'topology'],
    'ovsdb-oper': ['ovsdb-oper.json', 'operational', 'network-topology:network-topology',
                   'network-topology', 'topology'],
    'portopdata': ['port-op-data.json', 'operational', 'odl-l3vpn:port-op-data',
                   'port-op-data', 'port-op-data-entry'],
    'prefix-to-interface': ['prefix-to-interface.json', 'operational', 'odl-l3vpn:prefix-to-interface',
                            'prefix-to-interface', 'vpnids'],
    'subnetopdata': ['subnet-op-data.json', 'operational', 'odl-l3vpn:subnet-op-data',
                     'subnet-op-data', 'subnet-op-data-entry'],
    'transportzone': ['transport-zones.json', 'config', 'itm:transport-zones',
                      'transport-zones', 'transport-zone'],
    'tunconfig-external': ['tunnel-config-external.json', 'config', 'itm-state:external-tunnel-list',
                           'external-tunnel-list', 'external-tunnel'],
    'tunconfig': ['tunnel-config.json', 'config', 'itm-state:tunnel-list',
                  'tunnel-list', 'internal-tunnel'],
    'tunstate': ['tunnel-state.json', 'operational', 'itm-state:tunnels_state',
                 'tunnels_state', 'state-tunnel-list'],
    'vpninstance-opdata': ['vpn-instance-op-data.json', 'operational', 'odl-l3vpn:vpn-instance-op-data',
                           'vpn-instance-op-data', 'vpn-instance-op-data-entry'],
    'vpninterface-opdata': ['vpn-interface-op-data.json', 'operational', 'odl-l3vpn:vpn-interface-op-data',
                            'vpn-interface-op-data', 'vpn-interface-op-data-entry'],
    'vpninstance-to-vpnid': ['vpninstance-to-vpnid.json', 'config', 'odl-l3vpn:vpn-instance-to-vpn-id',
                             'vpn-instance-to-vpn-id', 'vpn-instance'],
    'vpntoextraroute': ['vpn-to-extraroute.json', 'operational', 'odl-l3vpn:vpn-to-extraroute',
                        'vpn-to-extraroute', 'tbd'],
    'vpnid-to-vpninstance': ['vpnid-to-vpninstance.json', 'config', 'odl-l3vpn:vpn-id-to-vpninstance',
                             'vpn-id-to-vpninstance', 'vpn-ids'],
    'vpninterfaces': ['vpn-interfaces.json', 'config', 'l3vpn:vpn-interfaces',
                      'vpn-interfaces', 'vpn-interface'],
    'vpn-routerinterfaces': ['router-interfaces.json', 'config', 'odl-l3vpn:router-interfaces',
                             'router-interfaces', 'router-interface']
}

TABLE_MAP = {
    'ifm': [0, 17, 220],
    'l3vpn': [19, 20, 21, 22, 36, 81],
    'elan': [50, 51, 52, 55],
    'acl': [211, 212, 213, 214, 215, 241, 242, 243, 244, 245]
}
