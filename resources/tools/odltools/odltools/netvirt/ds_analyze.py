import json
import utils

# Required
ifaces = None
ifstates = None

#Optional
ports = {}
tunnels = {}
confNodes = {}
operNodes = {}


def analyze_neutron_port(port, iface, ifstate):
    for flow in utils.sort(get_all_flows(['all']), 'table'):
        if ((flow.get('ifname') == port['uuid']) or
            (flow.get('lport') and ifstate and flow['lport'] == ifstate.get('if-index')) or
            (iface['name'] == flow.get('ifname'))):
                result = 'Table:{},FlowId:{}{}'.format(
                flow['table'], flow['id'],
                utils.show_optionals(flow))
                print result
                print 'Flow:', json.dumps(parse_flow(flow.get('flow')))


def analyze_inventory(nodeId, isConfig=True, ncId=None, ifName=None):
    if isConfig:
        nodes = odl_inventory_nodes_config.get_nodes_by_key()
        print "Inventory Config:"
    else:
        print "Inventory Operational:"
        nodes = odl_inventory_nodes_operational.get_nodes_by_key()
    node = nodes.get(nodeId)
    tables = node.get(const.NODE_TABLE)
    groups = node.get(const.NODE_GROUP)
    flow_list = []
    print "Flows:"
    for table in tables:
        for flow in table.get('flow', []):
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
