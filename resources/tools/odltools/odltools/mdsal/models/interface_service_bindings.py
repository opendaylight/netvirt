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
