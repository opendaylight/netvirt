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

MODULE = "elan"


def elan_instances(store, args):
    return ElanInstances(MODULE, store, args)


def elan_interfaces(store, args):
    return ElanInterfaces(MODULE, store, args)


class ElanInstances(Model):
    CONTAINER = "elan-instances"
    CLIST = "elan-instance"
    CLIST_KEY = "elan-instance-name"


class ElanInterfaces(Model):
    CONTAINER = "elan-interfaces"
    CLIST = "elan-interface"
    CLIST_KEY = "name"
