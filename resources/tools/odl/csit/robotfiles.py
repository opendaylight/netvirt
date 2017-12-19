import argparse
import logging
import sys
import os
from os import path
import re
import xml.etree.cElementTree as ET
from subprocess import Popen
#sys.path.append('../')
#sys.path.append('../ovs')
#sys.path.append(path.dirname( path.dirname( path.abspath(__file__))))


logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class RobotFiles:
    JOBTAG = "job"
    TMP = "/tmp"
    CHUNK_SIZE = 65536
    DUMP_FLOWS = "sudo ovs-ofctl dump-flows br-int -OOpenFlow13"

    def __init__(self, infile, outdir, jobtag):
        if outdir is None:
            outdir = RobotFiles.TMP
        if jobtag is None:
            jobtag = RobotFiles.JOBTAG
        self.outdir = "{}/{}".format(outdir, jobtag)
        self.datafilepath = infile
        self.pdata = {}
        self.re_normalize_text = re.compile(r"( \n)|(\[A\[C.*)")
        self.re_uri = re.compile(r"uri=(?P<uri>.*),")
        self.re_model = re.compile(r"uri=(?P<uri>.*),")

    def set_log_level(self, level):
        logger.setLevel(level)

    def gunzip_output_file(self):
        infile = self.datafilepath
        basename = os.path.splitext(os.path.basename(self.datafilepath))[0]
        self.datafilepath = "{}/{}".format(self.outdir, basename)
        Popen("gunzip -cfk {} > {}".format(infile, self.datafilepath), shell=True).wait()

    def mkdir(self, path):
        try:
            os.makedirs(path)
        except OSError:
            if not os.path.isdir(path):
                raise

    def mkdir_job_path(self):
        self.mkdir(self.outdir)

    def read_chunks(self, fp):
        while True:
            data = fp.read(RobotFiles.CHUNK_SIZE)
            if not data:
                break
            yield data

    def parse_data_file(self):
        re_st = re.compile(r"dump-flows")
        cnt = 0
        with open(self.datafilepath, 'rb') as fp:
            for chunk in self.read_chunks(fp):
                for m in re_st.finditer(chunk):
                    print('%02d-%02d: %s' % (m.start(), m.end(), m.group(0)))
                    cnt += 1
        print "total matches: {}".format(cnt)

    class State:
        def __init__(self):
            self.state = "init"
            self.pdata = {}
            self.test_id = None
            self.node = None
            self.command = None
            self.nodes = {}
            self.models = {}

        def reset(self):
            self.state = "init"
            self.pdata = {}
            self.test_id = None
            self.node = None
            self.command = None
            self.nodes = {}
            self.models = {}

    def normalize(self, intext):
        outtext = self.re_normalize_text.sub("", intext)
        return outtext

    def print_config(self):
        logger.info("datafilepath: %s, outdir: %s", self.datafilepath, self.outdir)

    def process_element(self, state, event, element):
        tag = element.tag
        text = element.text
        attribs = element.attrib
        if logger.isEnabledFor(logging.DEBUG):
            logger.debug("%s - %s - %s - %s - %s - %s", state.state, state.command, event, tag, (text is not None), attribs)
        if event == "end":
            if element.tag == "test":
                state.pdata['nodes'] = state.nodes
                state.pdata['models'] = state.models
                self.pdata[state.test_id] = state.pdata
                state.reset()
                return
            elif element.tag != "msg":
                return

        if event == "start" and state.state == "init":
            # <test id="s1-s1-s1-t1" name="Create VLAN Network (l2_network_1)">
            if element.tag == "test":
                state.test_id = element.get("id")
                state.pdata["name"] = element.get("name")
                state.state = "test"
        elif event == "start" and state.state == "test":
            # <kw type="teardown" name="Get Test Teardown Debugs" library="OpenStackOperations">
            if element.tag == "kw" and element.get("name") == "Get Test Teardown Debugs":
                state.state = "debugs"
        elif event == "start" and state.state == "debugs":
            # <arg>Get DumpFlows And Ovsconfig</arg>
            if element.tag == "arg" and element.text == "Get DumpFlows And Ovsconfig":
                state.state = "nodes"
            # <arg>Get Model Dump</arg>
            if element.tag == "arg" and element.text == "Get Model Dump":
                state.state = "models"
        elif event == "start" and state.state == "nodes":
            # <arg>${OS_CONTROL_NODE_IP}</arg>
            if element.tag == "arg" and element.text is not None and "${OS_" in element.text:
                state.node = element.text[element.text.find("{") + 1:element.text.find("}")]
                state.nodes[state.node] = {}
                state.state = "nodes2"
        elif event == "start" and state.state == "nodes2":
            # <kw name="Write Commands Until Expected Prompt" library="Utils">
            if element.tag == "kw" and element.get("name") == "Write Commands Until Expected Prompt":
                state.state = "kw"
        elif event == "start" and state.state == "kw":
            # <arg>ip -o link</arg>
            if element.tag == "arg" and element.text is not None:
                state.command = element.text
                state.state = "command"
                # only use the string before the ${...} since we don't know the ...
                # <arg>sudo ip netns exec ${line} ip -o link</arg>
                command_split = state.command.split("$")
                if len(command_split) > 1:
                    state.command = command_split[0]
        elif event == "start" and state.state == "command":
            # <msg timestamp="20170414 07:31:21.769" level="INFO">ip -o link</msg>
            if element.tag == "msg" and element.text is not None:
                text = self.normalize(element.text)
                if text.find(state.command) != -1:
                    # <msg timestamp="20170414 07:31:34.425" level="INFO">sudo ip netns exec
                    # [jenkins@rele ^Mng-36618-350-devstack-newton-0 ~]&gt; ip -o link</msg>
                    if text.find("jenkins") != -1:
                        state.state = "nodes2"
                    else:
                        state.command = text
                        state.state = "msg"
        elif state.state == "msg":
            # <msg timestamp="20170414 07:31:21.786" level="INFO">
            if element.tag == "msg" and element.text is not None:
                state.nodes[state.node][state.command] = element.text
                # are we at the end of the debugs for the node?
                # this command is the last one
                if state.command == "sudo ovs-ofctl dump-group-stats br-int -OOpenFlow13":
                    state.state = "debugs"
                else:
                    # still more debugs for this node
                    state.state = "nodes2"
        elif state.state == "models2":
            # <msg timestamp="20170813 08:20:11.806" level="INFO">Get Request using : alias=model_dump_session,
            # uri=restconf/config/interface-service-bindings:service-bindings, headers=None json=None</msg>
            if element.tag == "msg" and element.text is not None and element.text.find("uri") != -1:
                uri = self.re_uri.search(element.text)
                if uri is not None and "uri" in uri.group():
                    state.command = uri.group("uri")
                    state.state = "uri"
        elif event == "start" and state.state == "models":
            # <kw type="foritem" name="${model} = config/neutronvpn:router-interfaces-map">
            if element.tag == "kw" and "name" in element.attrib:
                name_split = element.attrib["name"].split("${model} = ", 1)
                model = None
                if len(name_split) == 2:
                    model = name_split[1]
                if model is not None:
                    state.command = model
                    state.state = "uri"
        elif state.state == "uri":
            if element.tag == "msg" and element.text is not None and element.text.find("pretty_output") != -1:
                state.state = "dump"
        elif state.state == "dump":
            if element.tag == "msg" and element.text is not None:
                state.models[state.command] = element.text
                if state.command == "restconf/operational/rendered-service-path:rendered-service-path":
                    state.state = "done"
                else:
                    state.state = "models"

    def parse_xml_data_file(self):
        state = self.State()
        with open(self.datafilepath, 'rb') as fp:
            iterparser = ET.iterparse(fp, events=("start", "end"))
            _, root = iterparser.next()
            for event, element in iterparser:
                self.process_element(state, event, element)
                element.clear()
                # if "s1-s1-s1-t1" in self.pdata:
                #    break
            root.clear()

    def write_pdata(self):
        for tindex, (testid, test) in enumerate(self.pdata.items()):
            tdir = self.outdir + "/" + testid + "_" + test["name"].replace(" ", "_")
            self.mkdir(tdir)
            for nindex, (nodeid, node) in enumerate(test['nodes'].items()):
                ndir = tdir + "/" + nodeid
                self.mkdir(ndir)
                for cindex, (cmdid, cmd) in enumerate(node.items()):
                    filename = ndir + "/" + self.fix_command_names(cmdid) + ".txt"
                    with open(filename, 'w') as fp:
                        if cmd is not None:
                            fp.writelines(cmd)
            mdir = tdir + "/models"
            self.mkdir(mdir)
            for mindex, (model, mdata) in enumerate(test['models'].items()):
                filename = mdir + "/" + self.fix_model_name(model) + ".txt"
                with open(filename, 'w') as fp:
                    if mdata is not None:
                        fp.writelines(mdata)

    def write_debug_pdata(self):
        for tindex, (testid, test) in enumerate(self.pdata.items()):
            tdir = self.outdir + "/" + testid + "_" + test["name"].replace(" ", "_")
            for nindex, (nodeid, node) in enumerate(test['nodes'].items()):
                ndir = tdir + "/" + nodeid
                if RobotFiles.DUMP_FLOWS not in node:
                    continue
                filename = ndir + "/" + self.fix_command_names(RobotFiles.DUMP_FLOWS) + ".f.txt"
                logger.info("Processing: %s", filename)
                dump_flows = node[RobotFiles.DUMP_FLOWS]
                flows = Flows(dump_flows, logging.DEBUG)
                flows.write_fdata(filename)

    def fix_command_names(self, cmd):
        return cmd.replace(" ", "_")

    def fix_model_name(self, model):
        return model.replace("/", "_")


def run(args):
    robotfile = RobotFiles(args.infile, args.outdir, args.jobtag)
    robotfile.print_config()
    robotfile.parse_xml_data_file()
    robotfile.mkdir_job_path()
    robotfile.write_pdata()


def create_parser():
    parser = argparse.ArgumentParser(description="OpenStack CLI Mock")
    parser.add_argument("-i", "--infile")
    parser.add_argument("-o", "--outdir")
    parser.add_argument("-j", "--jobtag")
    parser.add_argument("-g", "--gunzip", action="store_true")
    parser.set_defaults(func=run)

    return parser


def parse_args():
    parser = create_parser()
    args = parser.parse_args()

    return args


def main():
    args = parse_args()
    args.func(args)

if __name__ == "__main__":
    if __package__ is None:
        import sys
        from os import path

        sys.path.append(path.dirname(path.dirname(path.abspath(__file__))))
        from ovs.flows import Flows
    else:
        from ..ovs.flows import Flows
    main()
