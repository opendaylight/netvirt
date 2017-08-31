def debug_print(text1, data, debug=0):
    if debug:
        print "request: {}".format(text1)
        print "{}".format(data)


# OFPST_FLOW reply (OF1.3) (xid=0x2):
#  cookie=0x8000001, duration=5675.599s, table=0, n_packets=16948, n_bytes=1700009, priority=5,in_port=4
# actions=write_metadata:0xe0000000001/0xfffff0000000001,goto_table:36
def get_from_file(filename, debug=0):
    lines = []
    with open(filename, 'r') as fp:
        for line in fp:
            # strip leading spaces - ever flow line has a leading space: " cookie=..."
            lines.append(line.lstrip())
    debug_print(filename, lines, debug)
    return lines


def write_file(filename, lines):
    with open(filename, 'w') as fp:
        fp.writelines(lines)
