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
         'ifconfig':['iface-config.log','config','ietf-interfaces:interfaces','interfaces','interface'],
         'ifstate':['ifstate.log','operational','ietf-interfaces:interfaces-state','interfaces-state','interface'],
         'bindings':['service-bindings.log','config','interface-service-bindings:service-bindings','service-bindings','services-info'],
         'inventory':['inventory-config.log','config','opendaylight-inventory:nodes','nodes','node'],
         'neutronports':['neutron-ports.log','config','neutron:neutron/ports','ports','port'],
         'tunstate':['tunnnel-state.log','operational','itm-state:tunnels_state','tunnels_state','state-tunnel-list']
        }