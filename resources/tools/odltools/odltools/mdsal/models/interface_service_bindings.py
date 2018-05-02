import collections

from odltools.mdsal.models.model import Model

MODULE = "interface-service-bindings"


def service_bindings(store, args):
    return ServiceBindings(MODULE, store, args)


class ServiceBindings(Model):
    CONTAINER = "service-bindings"
    CLIST = "services-info"
    CLSIT_KEY = "interface-name"

    def get_service_bindings(self):
        sb_dict = collections.defaultdict(dict)
        orphans_dict = collections.defaultdict(dict)
        sb_infos = self.get_clist()
        for sb_info in sb_infos:
            service_mode = sb_info['service-mode'][len('interface-service-bindings:'):]
            if sb_info.get('bound-services'):
                sb_dict[sb_info['interface-name']][service_mode] = sb_info
            else:
                orphans_dict[sb_info['interface-name']][service_mode] = sb_info
        return dict(sb_dict), dict(orphans_dict)
