import elan
import id_manager
import ietf_interfaces
import interface_service_bindings
import itm_state
import l3vpn
import mip
import network_topology
import neutron
import odl_fib
import odl_interface_meta
import odl_l3vpn
import opendaylight_inventory
from model import Model


class Singleton(object):
    def __new__(cls, *args, **kwds):
        it = cls.__dict__.get("__it__")
        if it is not None:
            return it
        cls.__it__ = it = object.__new__(cls)
        it.init(*args, **kwds)
        return it

    def init(self, *args, **kwds):
        pass


def singleton(cls):
    instances = {}

    def getinstance():
        if cls not in instances:
            instances[cls] = cls()
        return instances[cls]
    return getinstance


@singleton
class Models:

    def __init__(self):
        self.args = None
        self.elan_elan_instances = None
        self.elan_elan_interfaces = None
        self.id_manager_id_pools = None
        self.ietf_interfaces_interfaces = None
        self.ietf_interfaces_interfaces_state = None
        self.interface_service_bindings_service_bindings = None
        self.itm_state_tunnels_state = None
        self.l3vpn_vpn_interfaces = None
        self.mip_mac = None
        self.network_topology_network_topology_config = None
        self.network_topology_network_topology_operational = None
        self.neutron_neutron = None
        self.odl_fib_fib_entries = None
        self.odl_interface_meta_if_index_interface_map = None
        self.odl_inventory_nodes_config = None
        self.odl_inventory_nodes_operational = None
        self.odl_l3vpn_vpn_instance_to_vpn_id = None

    def get_all_models(self, args):
        self.args = args
        self.elan_elan_instances = elan.elan_instances(Model.CONFIG, args)
        self.elan_elan_interfaces = elan.elan_interfaces(Model.CONFIG, args)
        self.id_manager_id_pools = id_manager.id_pools(Model.CONFIG, args)
        self.ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, args)
        self.ietf_interfaces_interfaces_state = ietf_interfaces.interfaces_state(Model.OPERATIONAL, args)
        self.interface_service_bindings_service_bindings = interface_service_bindings.service_bindings(Model.CONFIG, args)
        self.itm_state_tunnels_state = itm_state.tunnels_state(Model.OPERATIONAL, args)
        self.l3vpn_vpn_interfaces = l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, args)
        self.mip_mac = mip.mac(Model.CONFIG, args)
        self.network_topology_network_topology_config = network_topology.network_topology(Model.CONFIG, args)
        self.network_topology_network_topology_operational = network_topology.network_topology(Model.CONFIG, args)
        self.neutron_neutron = neutron.neutron(Model.CONFIG, args)
        self.odl_fib_fib_entries = odl_fib.fib_entries(Model.CONFIG, args)
        self.odl_interface_meta_if_index_interface_map = odl_interface_meta.if_indexes_interface_map(Model.OPERATIONAL, args)
        self.odl_inventory_nodes_config = opendaylight_inventory.nodes(Model.CONFIG, args)
        self.odl_inventory_nodes_operational = opendaylight_inventory.nodes(Model.OPERATIONAL, args)
        self.odl_l3vpn_vpn_instance_to_vpn_id = odl_l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, args)

    def get_models(self, args, models):
        self.args = args

        if "elan_elan_instances" in models:
            self.elan_elan_instances = elan.elan_instances(Model.CONFIG, args)
        if "elan_elan_interfaces" in models:
            self.elan_elan_interfaces = elan.elan_interfaces(Model.CONFIG, args)
        if "id_manager_id_pools" in models:
            self.id_manager_id_pools = id_manager.id_pools(Model.CONFIG, args)
        if "ietf_interfaces_interfaces" in models:
            self.ietf_interfaces_interfaces = ietf_interfaces.interfaces(Model.CONFIG, args)
        if "ietf_interfaces_interfaces_state" in models:
            self.ietf_interfaces_interfaces_state = ietf_interfaces.interfaces_state(Model.OPERATIONAL, args)
        if "interface_service_bindings_service_bindings" in models:
            self.interface_service_bindings_service_bindings = interface_service_bindings.service_bindings(Model.CONFIG, args)
        if "itm_state_tunnels_state" in models:
            self.itm_state_tunnels_state = itm_state.tunnels_state(Model.OPERATIONAL, args)
        if "l3vpn_vpn_interfaces" in models:
            self.l3vpn_vpn_interfaces = l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, args)
        if "mip_mac" in models:
            self.mip_mac = mip.mac(Model.CONFIG, args)
        if "network_topology_network_topology_config" in models:
            self.neutron_neutron = neutron.neutron(Model.CONFIG, args)
        if "network_topology_network_topology_config" in models:
            self.network_topology_network_topology_config = network_topology.network_topology(Model.CONFIG, args)
        if "network_topology_network_topology_operational" in models:
            self.network_topology_network_topology_operational = network_topology.network_topology(Model.CONFIG, args)
        if "neutron_neutron" in models:
            self.neutron_neutron = neutron.neutron(Model.CONFIG, args)
        if "odl_fib_fib_entries" in models:
            self.odl_fib_fib_entries = odl_fib.fib_entries(Model.CONFIG, args)
        if "odl_interface_meta_if_index_interface_map" in models:
            self.odl_interface_meta_if_index_interface_map = odl_interface_meta.if_indexes_interface_map(Model.OPERATIONAL, args)
        if "odl_inventory_nodes_config" in models:
            self.odl_inventory_nodes_config = opendaylight_inventory.nodes(Model.CONFIG, args)
        if "odl_inventory_nodes_operational" in models:
            self.odl_inventory_nodes_operational = opendaylight_inventory.nodes(Model.OPERATIONAL, args)
        if "odl_l3vpn_vpn_instance_to_vpn_id" in models:
            self.odl_l3vpn_vpn_instance_to_vpn_id = odl_l3vpn.vpn_instance_to_vpn_id(Model.CONFIG, args)
