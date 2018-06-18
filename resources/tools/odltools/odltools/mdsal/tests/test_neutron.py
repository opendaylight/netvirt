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

import logging
import unittest
from odltools import logg
from odltools.mdsal.models.neutron import Neutron
from odltools.mdsal.models.neutron import neutron
from odltools.mdsal.models.model import Model
from odltools.mdsal import tests


class TestNeutron(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        args = tests.Args(path=tests.get_resources_path())
        self.neutron = neutron(Model.CONFIG, args)

    def test_get_objects_by_key(self):
        d = self.neutron.get_objects_by_key(obj=Neutron.NETWORKS)
        self.assertIsNotNone(d.get('bd8db3a8-2b30-4083-a8b3-b3fd46401142'))
        d = self.neutron.get_objects_by_key(obj=Neutron.PORTS)
        self.assertIsNotNone(d.get('8e3c262e-7b45-4222-ac4e-528db75e5516'))


if __name__ == '__main__':
    unittest.main()
