import collections

from odltools.mdsal.models.model import Model

NAME = "interface-service-bindings"


def service_bindings(store, args):
    return ServiceBindings(NAME, ServiceBindings.CONTAINER, store, args)


class ServiceBindings(Model):
    CONTAINER = "service-bindings"
    SERVICES_INFO = "services-info"

    def get_services_infos(self):
        return self.data[self.CONTAINER][self.SERVICES_INFO]

    def get_service_bindings(self):
        sb_dict = collections.defaultdict(dict)
        orphans_dict = collections.defaultdict(dict)
        sb_infos = self.get_services_infos()
        if sb_infos is None:
            return None
        for sb_info in sb_infos:
            service_mode = sb_info['service-mode'][len('interface-service-bindings:'):]
            if sb_info.get('bound-services'):
                sb_dict[sb_info['interface-name']][service_mode] = sb_info
            else:
                orphans_dict[sb_info['interface-name']][service_mode] = sb_info
        return dict(sb_dict), dict(orphans_dict)
