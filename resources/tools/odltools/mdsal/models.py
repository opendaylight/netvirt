from mdsal.model import Model
from mdsal import elan
from mdsal import id_manager
from mdsal import ietf_interfaces
from mdsal import interface_service_bindings
from mdsal import itm_state
from mdsal import l3vpn
from mdsal import mip
from mdsal import network_topology
from mdsal import neutron
from mdsal import odl_fib
from mdsal import odl_interface_meta
from mdsal import odl_l3vpn
from mdsal import opendaylight_inventory


modelpath = "/tmp/robotjob/s1-t1_Create_VLAN_Network_net_1/models"
elan_elan_instances = None
elan_elan_interfaces = None
id_manager_id_pools = None
ietf_interfaces_interfaces = None
ietf_interfaces_interfaces_state = None
interface_service_bindings_service_bindings = None
itm_state_tunnels_state = None
l3vpn_vpn_interfaces = None
mip_mac = None
network_topology_network_topology_config = None
network_topology_network_topology_operational = None
neutron_neutron = None
odl_fib_fib_entries = None
odl_interface_meta_if_index_interface_map = None
odl_inventory_nodes_config = None
odl_inventory_nodes_operational = None
odl_l3vpn_vpn_instance_to_vpn_id = None


def get_all_dumps():
    global elan_elan_instances
    global elan_elan_interfaces
    global id_manager_id_pools
    global ietf_interfaces_interfaces
    global ietf_interfaces_interfaces_state
    global interface_service_bindings_service_bindings
    global itm_state_tunnels_state
    global l3vpn_vpn_interfaces
    global mip_mac
    global network_topology_network_topology_config
    global network_topology_network_topology_operational
    global neutron_neutron
    global odl_fib_fib_entries
    global odl_interface_meta_if_index_interface_map
    global odl_inventory_nodes_config
    global odl_inventory_nodes_operational
    global odl_l3vpn_vpn_instance_to_vpn_id

    elan_elan_instances = elan.elan_instances(Model.CONFIG, modelpath)
    elan_elan_interfaces = elan.elan_interfaces(Model.CONFIG, modelpath)
    id_manager_id_pools = id_manager.id_pools(Model.CONFIG, modelpath)
    ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, modelpath)
    ietf_interfaces_interfaces_state = ietf_interfaces.interfaces_state(Model.OPERATIONAL, modelpath)
    interface_service_bindings_service_bindings = interface_service_bindings.service_bindings(Model.CONFIG, modelpath)
    itm_state_tunnels_state = itm_state.tunnels_state(Model.OPERATIONAL, modelpath)
    l3vpn_vpn_interfaces = l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, modelpath)
    mip_mac = mip.mac(Model.CONFIG, modelpath)
    neutron_neutron = neutron.neutron(Model.CONFIG, modelpath)
    network_topology_network_topology_config = network_topology.network_topology(Model.CONFIG, modelpath)
    network_topology_network_topology_operational = network_topology.network_topology(Model.CONFIG, modelpath)
    neutron_neutron = neutron.neutron(Model.CONFIG, modelpath)
    odl_fib_fib_entries = odl_fib.fib_entries(Model.CONFIG, modelpath)
    odl_interface_meta_if_index_interface_map = odl_interface_meta.if_indexes_interface_map(Model.OPERATIONAL, modelpath)
    odl_inventory_nodes_config = opendaylight_inventory.nodes(Model.CONFIG, modelpath)
    odl_inventory_nodes_operational = opendaylight_inventory.nodes(Model.OPERATIONAL, modelpath)
    odl_l3vpn_vpn_instance_to_vpn_id = odl_l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, modelpath)
