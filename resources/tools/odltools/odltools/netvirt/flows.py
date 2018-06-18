# Copyright 2018 Red Hat, Inc. and others. All Rights Reserved.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import collections
import logging
from odltools.mdsal.models import constants
from odltools.mdsal.models.model import Model
from odltools.mdsal.models.neutron import Neutron
from odltools.mdsal.models.opendaylight_inventory import Nodes
from odltools.netvirt import tables as tbls
from odltools.netvirt import utils
from odltools.netvirt import config
from odltools.netvirt import flow_parser

logger = logging.getLogger("netvirt.flows")

OPTIONALS = ['ifname', 'lport', 'elan-tag', 'mpls', 'vpnid', 'reason', 'serviceid',
             'dst-mac', 'src-mac', 'int-ip4', 'ext-ip4', 'int-mac', 'ext-mac',
             'ofport', 'vlanid']


def filter_flow(flow_dict, filter_list):
    if not filter_list:
        return True
    for flow_filter in filter_list:
        if flow_dict.get(flow_filter):
            return True
    return False


def get_all_flows(args, modules=None, filter_by=None):
    config.get_models(args, {
        "elan_elan_instances",
        "elan_elan_interfaces",
        "ietf_interfaces_interfaces",
        "ietf_interfaces_interfaces_state",
        "l3vpn_vpn_interfaces",
        "odl_fib_fib_entries",
        "odl_interface_meta_if_index_interface_map",
        "odl_l3vpn_vpn_instance_to_vpn_id",
        "odl_inventory_nodes"})

    modules = modules if modules else "all"
    filter_by = filter_by if filter_by else []
    if not modules:
        return 'No modules specified'
    ifaces = {}
    ifstates = {}
    ifindexes = {}
    # bindings = {}
    einsts = {}
    eifaces = {}
    fibentries = {}
    vpnids = {}
    vpninterfaces = {}
    groups = {}
    if 'all' in modules:
        table_list = list(range(0, 255))
    else:
        table_list = list(set([table for mod in modules for table in tbls.get_table_map(mod)]))
    of_nodes = config.gmodels.odl_inventory_nodes.get_clist_by_key()
    if 'ifm' in modules:
        ifaces = config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        ifstates = config.gmodels.ietf_interfaces_interfaces_state.get_clist_by_key()
    if 'l3vpn' in modules:
        ifaces = ifaces or config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        ifindexes = ifindexes or config.gmodels.odl_interface_meta_if_index_interface_map.get_clist_by_key()
        fibentries = fibentries or config.gmodels.odl_fib_fib_entries.get_vrf_entries_by_key()
        vpnids = vpnids or config.gmodels.odl_l3vpn_vpn_instance_to_vpn_id.get_clist_by_key()
        vpninterfaces = vpninterfaces or config.gmodels.l3vpn_vpn_interfaces.get_clist_by_key()
        groups = groups or config.gmodels.odl_inventory_nodes.get_groups(of_nodes)
    if 'acl' in modules:
        ifaces = ifaces or config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        ifindexes = ifindexes or config.gmodels.odl_interface_meta_if_index_interface_map.get_clist_by_key()
        einsts = einsts or config.gmodels.elan_elan_instances.get_clist_by_key()
        eifaces = eifaces or config.gmodels.elan_elan_interfaces.get_clist_by_key()
    if 'elan' in modules:
        ifaces = ifaces or config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        einsts = einsts or config.gmodels.elan_elan_instances.get_clist_by_key()
        eifaces = eifaces or config.gmodels.elan_elan_interfaces.get_clist_by_key()
        ifindexes = ifindexes or config.gmodels.odl_interface_meta_if_index_interface_map.get_clist_by_key()
    if 'all' in modules:
        groups = groups or config.gmodels.odl_inventory_nodes.get_groups(of_nodes)
        ifaces = ifaces or config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        ifstates = ifstates or config.gmodels.ietf_interfaces_interfaces_state.get_clist_by_key()
        ifindexes = ifindexes or config.gmodels.odl_interface_meta_if_index_interface_map.get_clist_by_key()
        fibentries = fibentries or config.gmodels.odl_fib_fib_entries.get_vrf_entries_by_key()
        vpnids = vpnids or config.gmodels.odl_l3vpn_vpn_instance_to_vpn_id.get_clist_by_key()
        vpninterfaces = vpninterfaces or config.gmodels.l3vpn_vpn_interfaces.get_clist_by_key()
        einsts = einsts or config.gmodels.elan_elan_instances.get_clist_by_key()
        eifaces = eifaces or config.gmodels.elan_elan_interfaces.get_clist_by_key()
    flows = []
    for node in of_nodes.values():
        tables = [x for x in node[Nodes.NODE_TABLE] if x['id'] in table_list]
        for table in tables:
            for flow in table.get('flow', []):
                flow_info = {'dpnid': Model.get_dpn_from_ofnodeid(node['id'])}
                flow_dict = get_any_flow(flow, flow_info, groups,
                                         ifaces, ifstates, ifindexes,
                                         fibentries, vpnids, vpninterfaces,
                                         einsts, eifaces)
                if flow_dict is not None and filter_flow(flow_dict, filter_by):
                    flows.append(flow_dict)
    return flows


def create_flow_dict(flow_info, flow):
    flow_dict = {'table': flow['table_id'], 'id': flow['id'],
                 'name': flow.get('flow-name'), 'flow': flow,
                 'dpnid': flow_info['dpnid']}
    for opt in OPTIONALS:
        if flow_info.get(opt):
            flow_dict[opt] = flow_info.get(opt)
    return flow_dict


def get_any_flow(flow, flow_info, groups, ifaces, ifstates, ifindexes,
                 fibentries, vpnids, vpninterfaces, einsts, eifaces):
    table = flow['table_id']
    if table in tbls.get_table_map('ifm'):
        return stale_ifm_flow(flow, flow_info, ifaces, ifstates)
    elif table in tbls.get_table_map('acl'):
        return stale_acl_flow(flow, flow_info, ifaces, ifindexes, einsts, eifaces)
    elif table in tbls.get_table_map('elan'):
        return stale_elan_flow(flow, flow_info, ifaces, ifindexes, einsts, eifaces)
    elif table in tbls.get_table_map('l3vpn'):
        return stale_l3vpn_flow(flow, flow_info, groups, ifaces, ifindexes, vpnids, vpninterfaces, fibentries)
    elif table in tbls.get_table_map('nat'):
        return stale_nat_flow(flow, flow_info, ifaces, ifindexes)
    else:
        flow_info = flow_parser.get_flow_info_from_any(flow_info, flow)
        iface = (get_iface_for_lport(ifaces, ifindexes, flow_info.get('lport'))
                 if flow_info.get('lport') else None)
        if iface and iface.get('name'):
            flow_info['ifname'] = iface['name']
    # Get generic fields in here if not already captured
    # flow_info = flow_parser.get_flow_info_from_any(flow_info, flow)
    return create_flow_dict(flow_info, flow)


def stale_l3vpn_flow(flow, flow_info, groups, ifaces, ifindexes,
                     vpnids, vpninterfaces, fibentries):
    flow_parser.get_flow_info_from_l3vpn_table(flow_info, flow)
    flow_info['reason'] = None
    lport = flow_info.get('lport')
    iface = get_iface_for_lport(ifaces, ifindexes, lport)
    if lport and not iface:
        flow_info['reason'] = 'Interface for lport not found'
        return create_flow_dict(flow_info, flow)
    if iface:
        flow_info['ifname'] = iface['name']
    vpninterface = vpninterfaces.get(iface.get('name')) if iface else None
    if not vpninterfaces:
        flow_info['reason'] = 'VpnInterface for Lport not found'
        return create_flow_dict(flow_info, flow)
    vpnid = flow_info.get('vpnid')
    if vpnid and not vpnids.get(vpnid):
        flow_info['reason'] = 'VpnInstance for VpnId not found'
        return create_flow_dict(flow_info, flow)
    if vpnid and vpninterface and vpnids.get(vpnid):
        if (vpninterface.get('vpn-instance-name') !=
                vpnids[vpnid]['vpn-instance-name']):
            flow_info['reason'] = 'Lport VpnId mismatch'
            return create_flow_dict(flow_info, flow)
    label = flow_info.get('label')
    fibentry = fibentries.get(label) if label else None
    if label and not fibentry:
        flow_info['reason'] = 'Fibentry for MplsLabel not found'
        return create_flow_dict(flow_info, flow)
    # Label check for group
    prefix = fibentry.get('destPrefix') if fibentry else None
    if prefix and flow_info.get('group-id'):
        gid = flow_info.get('group-id')
        if groups.get(gid) and (
                    groups.get(gid).get('group-name', '') != prefix):
            flow_info['reason'] = 'DestPrefix mismatch for label and group'
            return create_flow_dict(flow_info, flow)
    return create_flow_dict(flow_info, flow)


def stale_elan_flow(flow, flow_info, ifaces, ifindexes, einsts, eifaces):
    # hex(int(mask, 16) & int(hexa, 16))
    flow_parser.get_flow_info_from_elan_table(flow_info, flow)
    flow_info['reason'] = None
    lport = flow_info.get('lport')
    eltag = flow_info.get('elan-tag')
    iface = get_iface_for_lport(ifaces, ifindexes, lport)
    if lport and not iface:
        flow_info['reason'] = 'Interface for lport not found'
        return create_flow_dict(flow_info, flow)
    if iface:
        flow_info['ifname'] = iface['name']
    if not is_tunnel_iface(iface) and not is_elantag_valid(eltag, eifaces, einsts, iface):
        flow_info['reason'] = 'Lport Elantag mismatch'
        return create_flow_dict(flow_info, flow)
    return create_flow_dict(flow_info, flow)


def stale_acl_flow(flow, flow_info, ifaces, ifindexes, einsts, eifaces):
    flow_parser.get_flow_info_from_acl_table(flow_info, flow)
    flow_info['reason'] = None
    lport = flow_info.get('lport')
    eltag = flow_info.get('elan-tag')
    iface = get_iface_for_lport(ifaces, ifindexes, lport)
    if lport and not iface:
        flow_info['reason'] = 'Interface for lport not found'
        return create_flow_dict(flow_info, flow)
    if iface:
        flow_info['ifname'] = iface['name']
    if not is_elantag_valid(eltag, eifaces, einsts, iface):
        flow_info['reason'] = 'Lport Elantag mismatch'
        return create_flow_dict(flow_info, flow)
    return create_flow_dict(flow_info, flow)


def stale_nat_flow(flow, flow_info, ifaces, ifindexes):
    # WiP - Vishal Thapar
    flow_parser.get_flow_info_from_nat_table(flow_info, flow)
    flow_info['reason'] = None
    return create_flow_dict(flow_info, flow)


def is_elantag_valid(eltag, eifaces, einsts, iface):
    if iface and eltag and eltag != get_eltag_for_iface(eifaces, einsts, iface):
        return False
    return True


def is_tunnel_iface(iface):
    if iface and iface.get('type', '') == constants.IFTYPE_TUNNEL:
        return True
    return False


def is_correct_elan_flow(flow_info, mmac, einsts, flow_host):
    flow = flow_info.get('flow')
    flow_etag = flow_info.get('elan-tag')
    for k, v in mmac.iteritems():
        mac_host = v.get('compute')
        if einsts.get(k):
            einst_tag = einsts.get(k).get('elan-tag')
            # print einst_tag, flow_etag, mac_host
            if flow_etag and einst_tag and flow_etag == einst_tag:
                if mac_host.startswith(flow_host):
                    act_resubmit = flow_parser.get_act_resubmit(flow)
                    if act_resubmit and act_resubmit.get('table') == 220:
                        return 'Correct'
                else:
                    act_tunnel = flow_parser.get_act_set_tunnel(flow)
                    if act_tunnel:
                        return 'Correct'
                return 'Wrong'
    return 'Wrong'


def get_iface_for_lport(ifaces, ifindexes, lport):
    if lport:
        if ifindexes.get(lport):
            ifname = ifindexes.get(lport).get('interface-name')
            if ifname and ifaces.get(ifname):
                return ifaces.get(ifname)
    return None


def get_eltag_for_iface(eifaces, einsts, iface):
    ifname = iface.get('name') if iface else None
    eiface = eifaces.get(ifname) if ifname else None
    einst_name = eiface.get('elan-instance-name') if eiface else None
    einst = einsts.get(einst_name) if einst_name else None
    return einst.get('elan-tag') if einst else None


def get_stale_flows(modules=['ifm']):
    if not modules:
        return 'No modules specified'
    ifaces = {}
    ifstates = {}
    ifindexes = {}
    # bindings = {}
    einsts = {}
    eifaces = {}
    fibentries = {}
    vpnids = {}
    vpninterfaces = {}
    groups = {}
    table_list = list(set([table for module in modules for table in tbls.get_table_map(module)]))
    # table_list = [214, 244]

    of_nodes = config.gmodels.odl_inventory_nodes.get_clist_by_key()
    if 'ifm' in modules:
        ifaces = config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        ifstates = config.gmodels.ietf_interfaces_interfaces_state.get_clist_by_key()
    if 'l3vpn' in modules:
        ifaces = ifaces or config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        ifindexes = ifindexes or config.gmodels.odl_interface_meta_if_index_interface_map.get_clist_by_key()
        fibentries = fibentries or config.gmodels.odl_fib_fib_entries.get_vrf_entries_by_key()
        vpnids = vpnids or config.gmodels.odl_l3vpn_vpn_instance_to_vpn_id.get_clist_by_key()
        vpninterfaces = vpninterfaces or config.gmodels.l3vpn_vpn_interfaces.get_clist_by_key()
        groups = groups or config.gmodels.odl_inventory_nodes.get_groups(of_nodes)
    if 'acl' in modules:
        ifaces = ifaces or config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        ifindexes = ifindexes or config.gmodels.odl_interface_meta_if_index_interface_map.get_clist_by_key()
        einsts = einsts or config.gmodels.elan_elan_instances.get_clist_by_key()
        eifaces = eifaces or config.gmodels.elan_elan_interfaces.get_clist_by_key()
    if 'elan' in modules:
        ifaces = ifaces or config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
        einsts = einsts or config.gmodels.elan_elan_instances.get_clist_by_key()
        eifaces = eifaces or config.gmodels.elan_elan_interfaces.get_clist_by_key()
        ifindexes = ifindexes or config.gmodels.odl_interface_meta_if_index_interface_map.get_clist_by_key()
    stale_flows = []
    for node in of_nodes.values():
        tables = [x for x in node[Nodes.NODE_TABLE] if x['id'] in table_list]
        for table in tables:
            for flow in table.get('flow', []):
                flow_dict = None
                flow_info = {'dpnid': Model.get_dpn_from_ofnodeid(node['id'])}
                if 'ifm' in modules and table['id'] in tbls.get_table_map('ifm'):
                    flow_dict = stale_ifm_flow(flow, flow_info, ifaces, ifstates)
                if 'l3vpn' in modules and table['id'] in tbls.get_table_map('l3vpn'):
                    flow_dict = stale_l3vpn_flow(flow, flow_info, groups, ifaces, ifindexes, vpnids,
                                                 vpninterfaces, fibentries)
                if 'elan' in modules and table['id'] in tbls.get_table_map('elan'):
                    flow_dict = stale_elan_flow(flow, flow_info, ifaces, ifindexes, einsts, eifaces)
                if 'acl' in modules and table['id'] in tbls.get_table_map('acl'):
                    flow_dict = stale_acl_flow(flow, flow_info, ifaces, ifindexes, einsts, eifaces)
                if 'nat' in modules and table['id'] in tbls.get_table_map('nat'):
                    flow_dict = stale_nat_flow(flow, flow_info, ifaces, ifstates)
                if flow_dict.get('reason'):
                    stale_flows.append(flow_dict)
    return stale_flows


def show_link_flow_binding(args):
    stale_ids, bindings = get_stale_bindings(args)
    flows = get_stale_flows()
    print(len(stale_ids), len(flows))
    for flow in flows:
        if flow['ifname'] in stale_ids and 'bound-services' in bindings[flow['ifname']]:
            print("Flow with binding: {}".format(flow['ifname']))
        else:
            print("Flow without binding: {}".format(flow['ifname']))


def show_stale_flows(args, sort_by='table'):
    config.get_models(args, {
        "elan_elan_instances",
        "elan_elan_interfaces",
        "ietf_interfaces_interfaces",
        "ietf_interfaces_interfaces_state",
        "interface_service_bindings_service_bindings",
        "l3vpn_vpn_interfaces",
        # "mip_mac",
        "neutron_neutron",
        "odl_fib_fib_entries",
        "odl_interface_meta_if_index_interface_map",
        "odl_l3vpn_vpn_instance_to_vpn_id",
        "odl_inventory_nodes",
        "odl_inventory_nodes_operational"})
    compute_map = config.gmodels.odl_inventory_nodes_operational.get_dpn_host_mapping()
    nports = config.gmodels.neutron_neutron.get_objects_by_key(obj=Neutron.PORTS)
    modules = [args.modules] if args.modules else tbls.get_all_modules()
    for flow in utils.sort(get_stale_flows(modules), sort_by):
        host = compute_map.get(flow.get('dpnid'), flow.get('dpnid'))
        ip_list = get_ips_for_iface(nports, flow.get('ifname'))
        if ip_list:
            flow['iface-ips'] = ip_list
        flow['host'] = host
        result = utils.show_all(flow)
        print(result)
        # path = get_data_path('flows', flow)
        # print("http://192.168.2.32:8383/restconf/config/{}".format(path))
        if not args.metaonly:
            print("Flow: ", utils.format_json(args, flow_parser.parse_flow(flow['flow'])))


def show_elan_flows(args):
    config.get_models(args, {
        "elan_elan_instances",
        "elan_elan_interfaces",
        "ietf_interfaces_interfaces",
        "ietf_interfaces_interfaces_state",
        "odl_interface_meta_if_index_interface_map",
        "odl_inventory_nodes",
        "odl_inventory_nodes_operational"})
    compute_map = config.gmodels.odl_inventory_nodes_operational.get_dpn_host_mapping()
    for flow in utils.sort(get_all_flows(args, modules=['elan']), 'id'):
        host = compute_map.get(flow.get('dpnid'), flow.get('dpnid'))
        flow['host'] = host
        result = "{}, Flow:{}".format(utils.show_all(flow),
                                      utils.format_json(args, flow_parser.parse_flow(flow['flow'])))
        print(result)
        # print("Flow: {}".format(utils.format_json(args, flow_parser.parse_flow(flow['flow']))))


def get_matchstr(args, flow):
    if flow and flow.get('flow') and flow.get('flow').get('match'):
        return utils.format_json(args, flow.get('flow').get('match', None))


def get_key_for_dup_detect(args, flow):
    result = '{}:{}:{}'.format(flow.get('dpnid'), flow.get('table'), get_matchstr(args, flow))
    return result


def show_dup_flows(args):
    config.get_models(args, {
        "elan_elan_instances",
        "elan_elan_interfaces",
        "ietf_interfaces_interfaces",
        "ietf_interfaces_interfaces_state",
        "interface_service_bindings_service_bindings",
        "l3vpn_vpn_interfaces",
        # "mip_mac",
        "odl_fib_fib_entries",
        "odl_interface_meta_if_index_interface_map",
        "odl_l3vpn_vpn_instance_to_vpn_id",
        "odl_inventory_nodes",
        "odl_inventory_nodes_operational"})
    mmac = {}  # config.gmodels.mip_mac.get_entries_by_key()
    einsts = config.gmodels.elan_elan_instances.get_clist_by_key()
    compute_map = config.gmodels.odl_inventory_nodes_operational.get_dpn_host_mapping()

    flows = utils.sort(get_all_flows(args, ['elan']), 'table')
    matches = collections.defaultdict(list)
    for flow in flows:
        dup_key = get_key_for_dup_detect(args, flow)
        if dup_key:
            if matches and matches.get(dup_key):
                matches[dup_key].append(flow)
            else:
                matches[dup_key].append(flow)
    for k, v in matches.iteritems():
        if len(v) > 1:
            dpnid = k.split(':')[0]
            host = compute_map.get(dpnid, dpnid)
            result = "Host:{}, FlowCount:{}, MatchKey:{}, ElanTag:{}".format(host, len(v), k, v[0].get('elan-tag'))
            print(result)
            for idx, flow in enumerate(v):
                result = "Duplicate"
                mac_addr = flow.get('dst-mac')
                if mac_addr and mmac.get(mac_addr):
                    result = is_correct_elan_flow(flow, mmac.get(mac_addr), einsts, host)
                print("    {} Flow-{}:{}".format(result, idx,
                                                 utils.format_json(args, flow_parser.parse_flow(flow.get('flow')))))


def show_learned_mac_flows(args):
    config.get_models(args, {
        "elan_elan_instances",
        "elan_elan_interfaces",
        "ietf_interfaces_interfaces",
        "ietf_interfaces_interfaces_state",
        "interface_service_bindings_service_bindings",
        "l3vpn_vpn_interfaces",
        # "mip_mac",
        "neutron_neutron",
        "odl_fib_fib_entries",
        "odl_interface_meta_if_index_interface_map",
        "odl_l3vpn_vpn_instance_to_vpn_id",
        "odl_inventory_nodes",
        "odl_inventory_nodes_operational"})
    # nports = config.gmodels.neutron_neutron.get_ports_by_key(key='mac-address')
    nports = config.gmodels.neutron_neutron.get_objects_by_key(obj=Neutron.PORTS, key='mac-address')
    compute_map = config.gmodels.odl_inventory_nodes_operational.get_dpn_host_mapping()

    flows = utils.sort(get_all_flows(args, ['elan']), 'table')
    for flow_info in flows:
        flow = flow_info.get('flow')
        dpnid = flow_info.get('dpnid')
        host = compute_map.get(dpnid, dpnid)
        if ((flow_info.get('table') == 50 and flow.get('idle-timeout') == 300
             and not nports.get(flow_info.get('src-mac')))
            or (flow_info.get('table') == 51 and not nports.get(flow_info.get('dst-mac')))):  # NOQA

            flow['host'] = host
            result = utils.show_all(flow_info)
            print(result)
            print("Flow: {}".format(utils.format_json(args, flow_parser.parse_flow(flow))))


def get_stale_bindings(args):
    # ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, args)
    # interface_service_bindings_service_bindings = interface_service_bindings.service_bindings(Model.CONFIG, args)
    ifaces = config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
    bindings, orphans = config.gmodels.interface_service_bindings_service_bindings.get_service_bindings()
    return set(bindings.keys()) - set(ifaces.keys()), bindings


def dump_flows(args, modules=None, sort_by='table', filter_by=None):
    config.get_models(args, {
        "neutron_neutron",
        "odl_inventory_nodes_operational",
        "network_topology_network_topology_operational"})
    filter_by = filter_by if filter_by else []
    compute_map = config.gmodels.odl_inventory_nodes_operational.get_dpn_host_mapping()
    node_map = config.gmodels.network_topology_network_topology_operational.get_dpn_host_mapping()
    nports = config.gmodels.neutron_neutron.get_objects_by_key(obj=Neutron.PORTS)
    for flow in utils.sort(get_all_flows(args, modules, filter_by), sort_by):
        dpnid = flow.get('dpnid')
        host = compute_map.get(dpnid)
        if not host:
            host = node_map.get(int(dpnid))
        ip_list = get_ips_for_iface(nports, flow.get('ifname'))
        if ip_list:
            flow['iface-ips'] = ip_list
        flow['host'] = host
        result = utils.show_all(flow)
        print(result)
        if not args.metaonly:
            print("Flow: {}".format(utils.format_json(args, flow_parser.parse_flow(flow['flow']))))


def show_all_flows(args):
    config.get_models(args, {
        "elan_elan_instances",
        "elan_elan_interfaces",
        "ietf_interfaces_interfaces",
        "ietf_interfaces_interfaces_state",
        "interface_service_bindings_service_bindings",
        "l3vpn_vpn_interfaces",
        "neutron_neutron",
        "odl_fib_fib_entries",
        "odl_interface_meta_if_index_interface_map",
        "odl_l3vpn_vpn_instance_to_vpn_id",
        "odl_inventory_nodes",
        "odl_inventory_nodes_operational"})
    modules = [args.modules] if args.modules else tbls.get_all_modules()
    dump_flows(args, modules)


def get_ips_for_iface(nports, ifname):
    ips = []
    port = nports.get(ifname) if ifname else None
    fixed_ips = port.get('fixed-ips', []) if port else []
    for fixed_ip in fixed_ips:
        ips.append(fixed_ip['ip-address'])
    return ips


def stale_ifm_flow(flow, flow_info, ifaces, ifstates):
    flow_parser.get_flow_info_from_ifm_table(flow_info, flow)
    flow_ifname = flow_info['ifname']
    iface = ifaces.get(flow_ifname)
    flow_info['reason'] = None
    if flow_ifname is not None and not iface:
        flow_info['reason'] = 'Interface doesnt exist'
        return create_flow_dict(flow_info, flow)
    elif flow_ifname and ifstates.get(flow_ifname):
        ifstate = ifstates.get(flow_ifname)
        ncid_list = ifstate.get('lower-layer-if')
        ncid = ncid_list[0] if ncid_list else None
        dpn = Model.get_dpn_from_ofnodeid(ncid)
        if dpn and dpn != flow_info['dpnid']:
            flow_info['reason'] = 'DpnId mismatch for flow and Interface'
            return create_flow_dict(flow_info, flow)
        if flow_info.get('lport') and ifstate.get('if-index') and flow_info['lport'] != ifstate['if-index']:
            flow_info['reason'] = 'Lport and IfIndex mismatch'
            return create_flow_dict(flow_info, flow)
        if (flow_info.get('ofport') and ifstate.get('lower-layer-if')
            and flow_info['ofport'] != Model.get_ofport_from_ncid(ifstate.get('lower-layer-if')[0])):  # NOQA
            flow_info['reason'] = 'OfPort mismatch'
            return create_flow_dict(flow_info, flow)
        if (flow_info.get('vlanid') and iface.get('odl-interface:vlan-id')
            and flow_info['vlanid'] != iface.get('odl-interface:vlan-id')):  # NOQA
            flow_info['reason'] = 'VlanId mismatch'
            return create_flow_dict(flow_info, flow)
    return create_flow_dict(flow_info, flow)
