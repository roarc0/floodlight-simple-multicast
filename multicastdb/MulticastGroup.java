package net.floodlightcontroller.multicast.multicastdb;

import java.util.ArrayList;
import java.util.List;

import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// class used to describe a single group
public class MulticastGroup {
	protected static final Logger log = LoggerFactory.getLogger(MulticastGroup.class);

	private IPv4Address addr;
	
	// class used to identify a single host
	public class MCHost {
		public IPv4Address addr; // host address
		public MacAddress  mac;  // host mac address
		public DatapathId  dpid; // host is attached to this switch
		public OFPort port;      // host is attached to this port of the switch
		
		MCHost(IPv4Address addr, MacAddress mac, DatapathId  dpid, OFPort port) {
			this.addr = addr;
			this.mac  = mac;
			this.dpid = dpid;
			this.port = port;
		}
	};
	
	private List<MCHost> hosts;

	public MulticastGroup(String group_addr) {
		addr = IPv4Address.of(group_addr);
		hosts = new ArrayList<MCHost>();
	}

	public IPv4Address getAddr() {
		return addr;
	}

	public MulticastGroup setAddr(IPv4Address addr) {
		this.addr = addr;
		return this;
	}

	// retuns all hosts
	public List<MCHost> getHosts() {
		return hosts;
	}

	// returns a MCHost given the address
	public MCHost getHost(IPv4Address addr) {
		MCHost h_ret = null;
		for(MCHost h : hosts) {
			if (addr.compareTo(h.addr) == 0) {
				h_ret = h;
				break;
			}
		}
		return h_ret;
	}
	
	// add host to group
	public MulticastGroup addHost(IPv4Address addr, MacAddress mac, DatapathId dpid, OFPort port) {
		hosts.add(new MCHost(addr, mac, dpid, port));
		return this;
	}

	// delete host from group
	public MulticastGroup deleteHost(IPv4Address addr) {
		MCHost h_del = null;
		for(MCHost h : hosts) {
			if (h.addr.toString().equals(addr.toString())) {
				h_del = h;
				break;
			}
		}
		if (h_del != null)
			hosts.remove(h_del);	

		return this;
	}
	
	public MulticastGroup clearHosts() {
		hosts.clear();
		return this;
	}
}
