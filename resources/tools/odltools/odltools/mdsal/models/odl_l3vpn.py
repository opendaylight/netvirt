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

MODULE = "odl-l3vpn"


def vpn_id_to_vpn_instance(store, args):
    return VpnIdToVpnInstance(MODULE, store, args)


def vpn_instance_to_vpn_id(store, args):
    return VpnInstanceToVpnId(MODULE, store, args)


class VpnIdToVpnInstance(Model):
    CONTAINER = "vpn-id-to-vpn-instance"
    CLIST = "vpn-ids"
    CLIST_KEY = "vpn-id"


class VpnInstanceToVpnId(Model):
    CONTAINER = "vpn-instance-to-vpn-id"
    CLIST = "vpn-instance"
    CLIST_KEY = "vpn-id"
