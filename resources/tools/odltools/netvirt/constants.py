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
    'bindings': ['service-bindings.json', 'config',
                 'interface-service-bindings:service-bindings',
                 'service-bindings', 'services-info'],
    'dpnendpoints': ['dpn-endpoints.json', 'config', 'itm-state:dpn-endpoints',
                     'dpn-endpoints', 'DPN-TEPs-info'],
    'elaninstances': ['elan-instances.json', 'config', 'elan:elan-instances',
                      'elan-instances', 'elan-instance'],
    'elaninterfaces': ['elan-interfaces.json', 'config', 'elan:elan-interfaces',
                       'elan-interfaces', 'elan-interface'],
    'fibentries': ['fibentries.json', 'config', 'odl-fib:fibEntries',
                   'fibEntries', 'vrfTables'],
    'idpools': ['idpools.json', 'config', 'id-manager:id-pools',
                      'id-pools', 'id-pool'],
    'ifconfig': ['iface-config.json', 'config', 'ietf-interfaces:interfaces',
                 'interfaces', 'interface'],
    'itmconfig': ['itm-config.json', 'config', 'itm-state:dpn-teps-state',
                  'dpn-teps-state', 'dpns-teps'],
    'ifindexes': ['ifindexes.json', 'operational',
                  'odl-interface-meta:if-indexes-interface-map',
                  'if-indexes-interface-map', 'if-index-interface'],
    'ifstate': ['ifstate.json', 'operational',
                'ietf-interfaces:interfaces-state',
                'interfaces-state', 'interface'],
    'inventory': ['inventory-config.json', 'config',
                  'opendaylight-inventory:nodes', 'nodes', 'node'],

    'nat-extrouters': ['nat-ext-routers.json', 'config', 'odl-nat:ext-routers',
                     'ext-routers', 'routers'],
    'nat-extnetworks': ['nat-external-networks.json', 'config', 'odl-nat:external-networks',
                     'external-networks', 'networks'],
    'nat-extsubnets': ['nat-external-subnets.json', 'config', 'odl-nat:external-subnets',
                     'external-subnets', 'subnets'],
    'nat-naptswitches': ['nat-napt-switches.json', 'config', 'odl-nat:napt-switches',
                     'napt-switches', 'router-to-napt-switch'],
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
    'neutronvpn-portip': ['neutronvpn-portip-port.json', 'config',
                          'neutronvpn:neutron-vpn-portip-port-data',
                          'neutron-vpn-portip-port-data',
                          'vpn-portip-to-port'],
    'tunconfig-external': ['tunnel-config-external.json', 'config',
                           'itm-state:external-tunnel-list',
                           'external-tunnel-list', 'external-tunnel'],
    'tunconfig': ['tunnel-config.json', 'config', 'itm-state:tunnel-list',
                  'tunnel-list', 'internal-tunnel'],
    'tunstate': ['tunnel-state.json', 'operational', 'itm-state:tunnels_state',
                 'tunnels_state', 'state-tunnel-list'],
    'vpninstance-to-vpnid': ['vpninstance-to-vpnid.json', 'config',
                             'odl-l3vpn:vpn-instance-to-vpn-id',
                             'vpn-instance-to-vpn-id', 'vpn-instance'],
    'vpnid-to-vpninstance': ['vpnid-to-vpninstance.json', 'config',
                             'odl-l3vpn:vpn-id-to-vpninstance',
                             'vpn-id-to-vpninstance', 'vpn-ids'],
    'vpninterfaces': ['vpn-interfaces.json', 'config', 'l3vpn:vpn-interfaces',
                      'vpn-interfaces', 'vpn-interface'],
    'vpn-routerinterfaces': ['router-interfaces.json', 'config', 'l3vpn:router-interfaces',
                      'router-interfaces', 'router-interface']
}

TABLE_MAP = {
    'ifm': [0, 17, 220],
    'l3vpn': [19, 20, 21, 22, 36, 81],
    'elan': [50, 51, 52, 55],
    'acl': [211, 212, 213, 214, 215, 241, 242, 243, 244, 245]
}
