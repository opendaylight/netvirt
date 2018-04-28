import json


def format_json(args, data):
    if args.pretty_print:
        return json.dumps(data, indent=4, separators=(',', ': '))
    else:
        return json.dumps(data)
