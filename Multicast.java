package net.floodlightcontroller.multicast;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.PacketType;
import org.projectfloodlight.openflow.types.U64;

import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.multicast.multicastdb.MulticastDb;
import net.floodlightcontroller.multicast.multicastdb.MulticastGroup;
import net.floodlightcontroller.multicast.multicastdb.MulticastGroup.MCHost;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.ICMP;
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.restserver.IRestApiService;
import net.floodlightcontroller.routing.IRoutingService;
import net.floodlightcontroller.util.FlowModUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Multicast implements IOFMessageListener, IFloodlightModule, IMulticastREST {
	protected static final Logger log = LoggerFactory.getLogger(Multicast.class);
	protected IFloodlightProviderService floodlightProvider;
	protected IRestApiService restApiService;
	// we need routing informations to know the ports where to forward packets
	protected IRoutingService routingService;
	
	// store groups,hosts in this class
	protected MulticastDb groupDb;
	
	// flow mod rules timeouts
	private static short IDLE_TIMEOUT = 20;
	private static short HARD_TIMEOUT = 40;

	@Override
	public String getName() {
		return Multicast.class.getSimpleName();
	}

	@Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		return false;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleServices() {
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IMulticastREST.class);
		return l;
	}

	@Override
	public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
		Map<Class<? extends IFloodlightService>, IFloodlightService> m =
				new HashMap<Class<? extends IFloodlightService>, IFloodlightService>();
		m.put(IMulticastREST.class, this);
		return m;
	}

	@Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		Collection<Class<? extends IFloodlightService>> l = new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IRestApiService.class);
		l.add(IRoutingService.class);
		return l;
	}

	@Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		restApiService = context.getServiceImpl(IRestApiService.class);
		routingService = context.getServiceImpl(IRoutingService.class);
		// create an empty db
		groupDb = new MulticastDb();
		log.info("Startup MULTICAST module");
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restApiService.addRestletRoutable(new MulticastWebRoutable());
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext ctx) {
		Ethernet eth = IFloodlightProviderService.bcStore.get(ctx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		IPacket pkt = eth.getPayload();
		DatapathId dpid = sw.getId();
		OFPacketIn pi = (OFPacketIn) msg;
		OFPort inPort = pi.getMatch().get(MatchField.IN_PORT);

		// this module handles only IPv4 multicast
		if (! (eth.isMulticast()  && (pkt instanceof IPv4)))
			return Command.CONTINUE;

		// extract informations from packet-in
		IPv4 pktIPv4 = (IPv4) pkt;
		IPv4Address destAddr = pktIPv4.getDestinationAddress();
		IPv4Address hostAddr = pktIPv4.getSourceAddress();
		MacAddress hostMac = eth.getSourceMACAddress();

		// if packet is addressed to 224.0.0.22 it should be an IGMP
		if (destAddr.equals(IPv4Address.of("224.0.0.22"))) {
			// quick way to extract needed informations. instead of extending basepacket from floodlight
			ByteBuffer bbIGMP = ByteBuffer.wrap(pktIPv4.getPayload().serialize());
			byte igmpType = bbIGMP.get();
			short numRecords = bbIGMP.getShort(6);

			switch (igmpType) {
				// we handle only this case
				case 0x22:
					// it might contain more records
					for (int i = 0; i<numRecords; i++) {
						byte groupRecordType = bbIGMP.get(i*8 + 8);
						IPv4Address groupAddr = IPv4Address.of(bbIGMP.getInt(i*8 + 12));
						// see recordType to tell if it is a join or leave
						switch(groupRecordType) {
							case 0x04:
								log.info("IGMP_MEMBERSHIP_REPORT_v3 JOIN : " + hostAddr.toString() + " >> " + groupAddr.toString());
								groupDb.joinGroup(groupAddr, hostAddr, hostMac, dpid, inPort);
								break;
							case 0x03:
								log.info("IGMP_MEMBERSHIP_REPORT_v3 LEAVE : " + hostAddr.toString() + " << " + groupAddr.toString());
								groupDb.leaveGroup(groupAddr, hostAddr);
								break;
							default:
								log.info(String.format("IGMP_MEMBERSHIP_REPORT_v3 ??? %02X : subtype not handled or malformed", groupRecordType));
						}
					}
					break;
				default:
					log.warn(String.format("IGMP_TYPE %02X not handled", igmpType));
					break;
			}

			return Command.STOP;
		}
		// Otherwise if it is UDP
		else if (destAddr.isMulticast() &&
				( (pkt.getPayload() instanceof UDP) || (pkt.getPayload() instanceof ICMP) )) {
			List<OFPort> portList = null;
			boolean isUDP = pkt.getPayload() instanceof UDP;
			String sPType = "ICMP"; // default if not UDP
			String sExtra = ""; // UDP has extra port number
			if (isUDP) {
				sPType = "UDP";
				UDP pktUDP = (UDP) pktIPv4.getPayload();
				sExtra = ":" + pktUDP.getDestinationPort().getPort();
			}

			// Host is trying to send UDP/ICMP packets to the group but it's not subscribed
			if (! groupDb.checkHost(destAddr, hostAddr)) {
				log.warn( sPType + "_PACKET : " + hostAddr.toString() + " >> " +
				destAddr.toString() + sExtra + " : host is not part of the group, dropping");
			}
			else {
				log.info(sPType + "_PACKET : " + hostAddr.toString() + " >> " +
						destAddr.toString() + sExtra + " : generating flow mod...");
				// generate a list containing the ports on this switch where to forward packets.
				portList = getPorts(destAddr, dpid, inPort);
			}
			/* generate flow mod using that list :
			 * if host is in db the port list shouldn't be null.
			 * if host is not in db the port list is null and it will be dropped.
			 * */
			newFlowMod(sw, pi, hostAddr, hostMac, destAddr, portList);
			return Command.STOP;
		}
		else if (destAddr.isMulticast())
		{
			/* for any other packet addressed to IPv4 multicast
			 * we define a new flow mod to drop these packets */
			log.info("***_PACKET : " + hostAddr.toString() + " >> " + destAddr.toString() + ": droping host...");
			newFlowMod(sw, pi, hostAddr, hostMac, destAddr, null);
			return Command.STOP;
		}
		return Command.CONTINUE;
	}

	public  List<OFPort> getPorts (IPv4Address groupAddr, DatapathId srcDpid, OFPort srcPort) {
		 List<OFPort> ports = new ArrayList<OFPort>();
		 MulticastGroup group = groupDb.getGroup(groupAddr);

		 if (group != null) {
			 /* for each host in group check if we have to include the port on the switch
			  * in order to reach it */
			 for (MCHost host : group.getHosts()) {
				// hosts in db is attached to the same switch
				if (host.dpid == srcDpid) {
					/* add if port is not already in list and it is different 
					 * from the port from which the packet-in came */
					if (srcPort != host.port && !ports.contains(host.port))
						ports.add(host.port);
				}
				else {
					/* Hosts attached to different switches access to routing info to find
					 * the port on the current switch where to find the host in db. */
					OFPort destPort = routingService.getPath(srcDpid, host.dpid).getPath().get(0).getPortId();
					/* add if port is not already in list and it is different 
					 * from the port from which the packet-in came */
					if (srcPort != destPort && !ports.contains(destPort))
						ports.add(destPort);
				}
			}
		}
		return ports;
	}

	public void newFlowMod(IOFSwitch sw, OFPacketIn pi, IPv4Address host, MacAddress hostMac, IPv4Address dest, List<OFPort> ports) {
		if (ports != null)
			log.info("FLOW_MOD   : " + host + " >> " + dest + " switch ports: " + Arrays.toString(ports.toArray()));
		else
			log.info("FLOW_MOD   : " + host + " >> " + dest + " drop host");
		
		// Set parameters of flow mod and timeouts.
		OFFlowAdd.Builder fmb = sw.getOFFactory().buildFlowAdd();
		fmb.setIdleTimeout(IDLE_TIMEOUT).setHardTimeout(HARD_TIMEOUT)
		.setBufferId(OFBufferId.NO_BUFFER).setOutPort(OFPort.ANY)
		.setCookie(U64.of(0)).setPriority(FlowModUtils.PRIORITY_MAX);

		// Create the match structure   
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IPV4_DST, dest)
		.setExact(MatchField.IPV4_SRC, host)
		.setExact(MatchField.ETH_SRC, hostMac)
		.setExact(MatchField.IN_PORT, pi.getMatch().get(MatchField.IN_PORT));

		// translate the list into a list of actions
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();
		if (ports != null) {
			if (ports.size() != 0) {
				for (OFPort port : ports)
					actionList.add(
						sw.getOFFactory().actions().buildOutput().setMaxLen(0xFFffFFff).setPort(port).build());
			}
			else
				return;
		}

		sw.write(fmb.setActions(actionList).setMatch(mb.build()).build());

		/* Send back the packet to the switch 
		 * it must be returned even if the flow mod has no action
		 * which translates to dropping the packet.
		 * The switch doesn't know if controller wants to drop it;
		 * it may be keeping it buffered. */
		if (ports != null && ports.size() != 0)
			returnPacketToSwitch(sw, pi, actionList);
	}
	
	public void returnPacketToSwitch(IOFSwitch sw, OFPacketIn pi, ArrayList<OFAction> actionList) {
		OFPacketOut.Builder po = sw.getOFFactory().buildPacketOut();
		po.setBufferId(pi.getBufferId()).setInPort(OFPort.ANY).setActions(actionList);

		// Packet might be buffered in the switch or encapsulated in Packet-In 
		if (pi.getBufferId() == OFBufferId.NO_BUFFER) {
			//Packet is encapsulated -> send it back
			byte[] packetData = pi.getData();
			po.setData(packetData);
		}

		log.info("PACKETOUT : returning packet back to the switch");
		sw.write(po.build());
	}

	// #### RESTful interface functions

	@Override
	public Map<String, Object> getGroupsInfo() {
		return groupDb.getGroupsDescription();
	}

	@Override
	public Map<String, Object> getGroupInfo(String groupAddr) {
		return groupDb.getGroupDescription(groupAddr);
	}

	@Override
	public boolean createGroup(String groupAddr) {
		return groupDb.createGroup(groupAddr);
	}

	@Override
	public boolean deleteGroup(String groupAddr) {
		return groupDb.deleteGroup(groupAddr);
	}
}
