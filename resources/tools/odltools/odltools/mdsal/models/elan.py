from odltools.mdsal.models.model import Model


NAME = "elan"


def elan_instances(store, args):
    return ElanInstances(NAME, ElanInstances.CONTAINER, store, args)


def elan_interfaces(store, args):
    return ElanInstances(NAME, ElanInstances.CONTAINER, store, args)


class ElanInstances(Model):
    CONTAINER = "elan-instances"
    ELAN_INSTANCE = "elan-instance"

    def get_elan_instances(self):
        return self.data[self.CONTAINER][self.ELAN_INSTANCE]

    def get_elan_instances_by_key(self, key="elan-instance-name"):
        d = {}
        instances = self.get_elan_instances()
        for instance in instances:
            d[instance[key]] = instance
        return d


class ElanInterfaces(Model):
    CONTAINER = "elan-interfaces"
    ELAN_INSTANCE = "elan-instance"

    def get_elan_interfaces(self):
        return self.data[self.CONTAINER][self.ELAN_INSTANCE]

    def get_elan_interfaces_by_key(self, key="name"):
        d = {}
        ifaces = self.get_elan_interfaces()
        for iface in ifaces:
            d[iface[key]] = iface
        return d
