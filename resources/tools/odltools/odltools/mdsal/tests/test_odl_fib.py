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
from odltools.mdsal.models.odl_fib import fib_entries
from odltools.mdsal.models.model import Model
from odltools.mdsal import tests


class TestOdlFib(unittest.TestCase):
    def setUp(self):
        logg.Logger(logging.INFO, logging.INFO)
        args = tests.Args(path=tests.get_resources_path())
        self.fib_entries = fib_entries(Model.CONFIG, args)

    def test_get_clist_by_key(self):
        d = self.fib_entries.get_clist_by_key()
        self.assertIsNotNone(d.get('c7922261-90ab-4757-bc03-93ef3bbbaa19'))

    def test_get_vrf_entries_by_key(self):
        d = self.fib_entries.get_vrf_entries_by_key()
        self.assertIsNotNone(d.get(100011))


if __name__ == '__main__':
    unittest.main()
