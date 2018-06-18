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

from odltools.mdsal.models.model import Model

MODULE = "odl-fib"


def fib_entries(store, args):
    return FibEntries(MODULE, store, args)


class FibEntries(Model):
    CONTAINER = "fibEntries"
    CLIST = "vrfTables"
    CLIST_KEY = "routeDistinguisher"
    VRFENTRY = "vrfEntry"
    ROUTEDISTINGUISHER = "routeDistinguisher"
    ROUTEPATHS = "route-paths"
    RD = "rd"

    def get_vrf_entries_by_key(self, key="label"):
        d = {}
        vrf_tables = self.get_clist()
        for vrf_table in vrf_tables:
            for vrf_entry in vrf_table.get(self.VRFENTRY, []):
                for route_paths in vrf_entry.get(FibEntries.ROUTEPATHS, {}):
                    if route_paths.get(key):
                        vrf_entry[self.RD] = vrf_table[self.ROUTEDISTINGUISHER]
                        d[route_paths.get(key)] = vrf_entry
        return d
