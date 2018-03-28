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
    'bindings': ['service-bindings.log', 'config',
                 'interface-service-bindings:service-bindings',
                 'service-bindings', 'services-info'],
    'dpnendpoints': ['dpn-endpoints.log', 'config', 'itm-state:dpn-endpoints',
                     'dpn-endpoints', 'DPN-TEPs-info'],
    'elaninstances': ['elan-instances.log', 'config', 'elan:elan-instances',
                      'elan-instances', 'elan-instance'],
    'elaninterfaces': ['elan-interfaces.log', 'config', 'elan:elan-interfaces',
                       'elan-interfaces', 'elan-interface'],
    'fibentries': ['fibentries.log', 'config', 'odl-fib:fibEntries',
                   'fibEntries', 'vrfTables'],
    'idpools': ['idpools.log', 'config', 'id-manager:id-pools',
                      'id-pools', 'id-pool'],
    'ifconfig': ['iface-config.log', 'config', 'ietf-interfaces:interfaces',
                 'interfaces', 'interface'],
    'itmconfig': ['itm-config.log', 'config' 'itm-state:dpn-teps-state',
                  'dpn-teps-state', 'dpns-teps'],
    'ifindexes': ['ifindexes.log', 'operational',
                  'odl-interface-meta:if-indexes-interface-map',
                  'if-indexes-interface-map', 'if-index-interface'],
    'ifstate': ['ifstate.log', 'operational',
                'ietf-interfaces:interfaces-state',
                'interfaces-state', 'interface'],
    'inventory': ['inventory-config.log', 'config',
                  'opendaylight-inventory:nodes', 'nodes', 'node'],
    'neutronports': ['neutron-ports.log', 'config', 'neutron:neutron/ports',
                     'ports', 'port'],
    'neutronvpn-portip': ['neutronvpn-portip-port.log', 'config',
                          'neutronvpn:neutron-vpn-portip-port-data',
                          'neutron-vpn-portip-port-data',
                          'vpn-portip-to-port'],
    'neutrontrunks': ['neutron-trunks.log', 'config', 'neutron:neutron/trunks',
                     'trunks', 'trunk'],
    'tunconfig-external': ['tunnel-config-external.log', 'config',
                           'itm-state:external-tunnel-list',
                           'external-tunnel-list', 'external-tunnel'],
    'tunconfig': ['tunnel-config.log', 'config', 'itm-state:tunnel-list',
                  'tunnel-list', 'internal-tunnel'],
    'tunstate': ['tunnel-state.log', 'operational', 'itm-state:tunnels_state',
                 'tunnels_state', 'state-tunnel-list'],
    'vpninstance-to-vpnid': ['vpninstance-to-vpnid.log', 'config',
                             'odl-l3vpn:vpn-instance-to-vpn-id',
                             'vpn-instance-to-vpn-id', 'vpn-instance'],
    'vpninterfaces': ['vpn-interfaces.log', 'config', 'l3vpn:vpn-interfaces',
                      'vpn-interfaces', 'vpn-interface']
}

TABLE_MAP = {
    'ifm': [0, 17, 220],
    'l3vpn': [19, 20, 21, 22, 36, 81],
    'elan': [50, 51, 52, 55],
    'acl': [211, 212, 213, 214, 215, 241, 242, 243, 244, 245]
}
