import constants as const
import ds_get_data as dsg
import netvirt_utils as utils


# Required
ifaces = None
ifstates = None

#Optional
ports = {}
tunnels = {}
confNodes = {}
operNodes = {}


def by_ifname(ifname):
    ifstate = ifstates.get(ifname)
    iface = ifaces.get(ifname)
    port = None
    tunnel = None
    tunState = None
    if iface.get('type') == const.IFTYPE_VLAN:
        ports = dsg.get_neutron_ports()
        port = ports.get(ifname)
    elif iface.get('type') == const.IFTYPE_TUNNEL:
        tunnels = dsg.get_config_tunnels()
        tunnel = tunnels.get(ifname)
        tunStates = dsg.get_tunnel_states()
        tunState = tunStates.get(ifname)
    else:
        print "UNSUPPORTED IfType"
    return iface,ifstate,port,tunnel,tunState




def print_keys():
    print "InterfaceNames: ", ifaces.keys()
    print
    print "IfStateNames: ", ifstates.keys()


def analyze_interface(ifname=None):
    print
    if not ifname:
        print_keys()
        exit(1)
    iface,ifstate,port,tunnel,tunState = by_ifname(ifname)
    print "InterfaceConfig: "
    utils.pretty_print(iface)
    print "InterfaceState: "
    utils.pretty_print(ifstate)
    if port:
        print "NeutronPort: "
        utils.pretty_print(port)
    if tunnel:
        print "Tunnel: "
        utils.pretty_print(tunnel)
    if tunState:
        print "TunState: "
        utils.pretty_print(tunState)
    if ifstate:
        ncId = ifstate.get('lower-layer-if')[0]
        nodeId = ncId[:ncId.rindex(':')]
        #analyze_inventory(nodeId, True, ncId, ifname)
        #analyze_inventory(nodeId, False, ncId, ifname)


def analyze_inventory(nodeId, isConfig=True, ncId=None, ifName=None):
    if isConfig:
        nodes = dsg.get_inventory_config()
        print "Inventory Config:"
    else:
        print "Inventory Operational:"
        nodes = dsg.get_inventory_oper()
    node = nodes.get(nodeId)
    tables = node.get(const.NODE_TABLE)
    groups = node.get(const.NODE_GROUP)
    flow_list = []
    print "Flows:"
    for table in tables:
        for flow in table.get('flow'):
            if not ifName or ifName in utils.nstr(flow.get('flow-name')):
                flow_dict = {}
                flow_dict['table'] = table['id']
                flow_dict['id'] = flow['id']
                flow_dict['name'] = flow.get('flow-name')
                flow_dict['flow'] = flow
                flow_list.append(flow_dict)
    flows = sorted(flow_list, key=lambda x: x['table'])
    for flow in flows:
        print 'Table:', flow['table']
        print 'FlowId:', flow['id'], 'FlowName:', flow.get('name')


def main(args=None):
    utils.parse_args()
    ifaces = dsg.get_config_interfaces()
    utils.pretty_print(ifaces)
    '''
    ifstates = dsg.get_interface_states()
    '''



if __name__ == '__main__':
    import sys
    main()
