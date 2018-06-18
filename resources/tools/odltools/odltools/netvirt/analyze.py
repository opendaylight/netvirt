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

from odltools.netvirt import config
from odltools.netvirt import flow_parser
from odltools.netvirt import flows
from odltools.mdsal.models import constants
from odltools.mdsal.models.neutron import Neutron
from odltools.mdsal.models.opendaylight_inventory import Nodes
from odltools.mdsal.models.model import Model
from odltools.netvirt import utils


def print_keys(args, ifaces, ifstates):
    print("InterfaceNames: {}\n".format(utils.format_json(args, ifaces.keys())))
    print("IfStateNames: {}".format(utils.format_json(args, ifstates.keys())))


def by_ifname(args, ifname, ifstates, ifaces):
    config.get_models(args, {
        "itm_state_tunnels_state",
        "neutron_neutron"})
    ifstate = ifstates.get(ifname)
    iface = ifaces.get(ifname)
    port = None
    tunnel = None
    tun_state = None
    if iface and iface.get('type') == constants.IFTYPE_VLAN:
        ports = config.gmodels.neutron_neutron.get_objects_by_key(obj=Neutron.PORTS)
        port = ports.get(ifname)
    elif iface and iface.get('type') == constants.IFTYPE_TUNNEL:
        tun_states = config.gmodels.itm_state_tunnels_state.get_clist_by_key()
        tun_state = tun_states.get(ifname)
    else:
        print("UNSUPPORTED IfType")
    return iface, ifstate, port, tunnel, tun_state


def analyze_interface(args):
    config.get_models(args, {
        "ietf_interfaces_interfaces",
        "ietf_interfaces_interfaces_state"})
    ifaces = config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
    ifstates = config.gmodels.ietf_interfaces_interfaces_state.get_clist_by_key()

    if not args.ifname:
        print_keys(args, ifaces, ifstates)
        return

    ifname = args.ifname
    iface, ifstate, port, tunnel, tunState = by_ifname(args, ifname, ifstates, ifaces)
    print("InterfaceConfig: \n{}".format(utils.format_json(args, iface)))
    print("InterfaceState: \n{}".format(utils.format_json(args, ifstate)))
    if port:
        print("NeutronPort: \n{}".format(utils.format_json(args, port)))
        # analyze_neutron_port(port, iface, ifstate)
        return
    if tunnel:
        print("Tunnel: \n{}".format(utils.format_json(args, tunnel)))
    if tunState:
        print("TunState: \n{}".format(utils.format_json(args, tunState)))
    # if ifstate:
        # ncid = ifstate.get('lower-layer-if')[0]
        # nodeid = ncid[:ncid.rindex(':')]
        # analyze_inventory(nodeid, True, ncid, ifname)
        # analyze_inventory(nodeid, False, ncid, ifname)


def analyze_trunks(args):
    config.get_models(args, {
        "ietf_interfaces_interfaces",
        # "ietf_interfaces_interfaces_state",
        "l3vpn_vpn_interfaces",
        "neutron_neutron"})

    vpninterfaces = config.gmodels.l3vpn_vpn_interfaces.get_clist_by_key()
    ifaces = config.gmodels.ietf_interfaces_interfaces.get_clist_by_key()
    # ifstates = config.gmodels.ietf_interfaces_interfaces_state.get_clist_by_key()
    nports = config.gmodels.neutron_neutron.get_objects_by_key(obj=Neutron.PORTS)
    ntrunks = config.gmodels.neutron_neutron.get_trunks_by_key()
    subport_dict = {}
    for v in ntrunks.values():
        nport = nports.get(v.get('port-id'))
        s_subports = []
        for subport in v.get('sub-ports'):
            sport_id = subport.get('port-id')
            snport = nports.get(sport_id)
            svpniface = vpninterfaces.get(sport_id)
            siface = ifaces.get(sport_id)
            # sifstate = ifstates.get(sport_id)
            subport['SubNeutronPort'] = 'Correct' if snport else 'Wrong'
            subport['SubVpnInterface'] = 'Correct' if svpniface else 'Wrong'
            subport['ofport'] = Model.get_ofport_from_ncid()
            if siface:
                vlan_mode = siface.get('odl-interface:l2vlan-mode')
                parent_iface_id = siface.get('odl-interface:parent-interface')
                if vlan_mode != 'trunk-member':
                    subport['SubIface'] = 'WrongMode'
                elif parent_iface_id != v.get('port-id'):
                    subport['SubIface'] = 'WrongParent'
                elif siface.get('odl-interface:vlan-id') != subport.get('segmentation-id'):
                    subport['SubIface'] = 'WrongVlanId'
                else:
                    subport['SubIface'] = 'Correct'
            else:
                subport['SubIface'] = 'Wrong'
                # s_subport = 'SegId:{}, PortId:{}, SubNeutronPort:{}, SubIface:{}, SubVpnIface:{}'.format(
                #     subport.get('segmentation-id'), subport.get('port-id'),
                #     subport.get('SubNeutronPort'),
                #     subport.get('SubIface'),
                #     subport.get('SubVpnInterface'))
            s_subports.append(subport)
            subport_dict[subport['port-id']] = subport
            s_trunk = 'TrunkName:{}, TrunkId:{}, PortId:{}, NeutronPort:{}, SubPorts:{}'.format(
                v.get('name'), v.get('uuid'), v.get('port-id'),
                'Correct' if nport else 'Wrong', utils.format_json(args, s_subports))
            print(s_trunk)
            print("\n------------------------------------")
            print("Analyzing Flow status for SubPorts")
            print("------------------------------------")
            for flow in utils.sort(flows.get_all_flows(args, ['ifm'], ['vlanid']), 'ifname'):
                subport = subport_dict.get(flow.get('ifname')) or None
                vlanid = subport.get('segmentation-id') if subport else None
                ofport = subport.get('ofport') if subport else None
                flow_status = 'Okay'
                if flow.get('ofport') and flow.get('ofport') != ofport:
                    flow_status = 'OfPort mismatch for SubPort:{} and Flow:{}'.format(subport, flow.get('flow'))
                if flow.get('vlanid') and flow.get('vlanid') != vlanid:
                    flow_status = 'VlanId mismatch for SubPort:{} and Flow:{}'.format(subport, flow.get('flow'))
                if subport:
                    print("SubPort:{},Table:{},FlowStatus:{}".format(
                        subport.get('port-id'), flow.get('table'), flow_status))


def analyze_neutron_port(args, port, iface, ifstate):
    for flow in utils.sort(flows.get_all_flows(args, ['all']), 'table'):
        if ((flow.get('ifname') == port['uuid']) or
                (flow.get('lport') and ifstate and flow['lport'] == ifstate.get('if-index')) or
                (iface['name'] == flow.get('ifname'))):
            result = utils.show_all(flow)
            print(result)
            print("Flow: {}".format(utils.format_json(None, flow_parser.parse_flow(flow.get('flow')))))


def analyze_inventory(args):
    config.get_models(args, {
        "odl_inventory_nodes",
        "odl_inventory_nodes_operational"})

    if args.store == "config":
        nodes = config.gmodels.odl_inventory_nodes.get_clist_by_key()
        print("Inventory Config:")
    else:
        print("Inventory Operational:")
        nodes = config.gmodels.odl_inventory_nodes_operational.get_clist_by_key()
    node = nodes.get("openflow:" + args.nodeid)
    if node is None:
        print("node: {} was not found".format("openflow:" + args.nodeid))
        return
    tables = node.get(Nodes.NODE_TABLE)
    # groups = node.get(Nodes.NODE_GROUP)
    flow_list = []
    print("Flows: ")
    for table in tables:
        for flow in table.get('flow', []):
            if not args.ifname or args.ifname in utils.nstr(flow.get('flow-name')):
                flow_dict = {'table': table['id'], 'id': flow['id'], 'name': flow.get('flow-name'), 'flow': flow}
                flow_list.append(flow_dict)
    flowlist = sorted(flow_list, key=lambda x: x['table'])
    for flow in flowlist:
        print("Table: {}".format(flow['table']))
        print("FlowId: {}, FlowName: {} ".format(flow['id'], 'FlowName:', flow.get('name')))


def analyze_nodes(args):
    config.update_gnodes(args)
    gnodes = config.get_gnodes()

    print("\nnodes\n")
    for nodeid, node in sorted(gnodes.items()):
        dpn_id = node.get("dpn_id")
        ip = node.get("ip")
        print("dpnid: {}, ip: {}".format(dpn_id, ip))

    for nodeid, node in sorted(gnodes.items()):
        dpn_id = nodeid
        ip = node.get("ip")
        ovs_version = node.get("ovs_version")
        hostname = node.get("hostname")
        print("\ndpnid: {}, dpid: {:016x}, ip: {}, version: {}\n"
              "hostname: {}"
              .format(dpn_id, int(dpn_id), ip, ovs_version, hostname))
        pline = "{:3} {:17} {:14} {:15} {:36} {:17}"
        print(pline.format("of#", "mac", "name", "ip", "uuid", "nmac"))
        print(pline.format("-"*3, "-"*17, "-"*14, "-"*15, "-"*36, "-"*17))
        ports = node.get("ports", {})
        for portno, port in sorted(ports.items()):
            print(pline.format(
                port.get("portno"), port.get("mac"), port.get("name"),
                port.get("ip"), port.get("uuid"), port.get("nmac")))
