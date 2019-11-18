services = {
    0: None,
    1: 'ACL',
    2: 'IN_CTRS',
    3: 'DHCP',
    4: 'QoS',
    5: 'IPv6',
    6: 'COE',
    7: 'L3VPN',
    8: 'ELAN',
    9: 'L3VPN6'
}


def get_service_name(service_id):
    if service_id and service_id in services:
        return services[service_id]
    else:
        return None
