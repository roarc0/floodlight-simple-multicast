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
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActions;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
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
import net.floodlightcontroller.packet.IPacket;
import net.floodlightcontroller.packet.IPv4;
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
	protected IRoutingService routingService;
	
	protected MulticastDb groupDb;
	
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
		// This class manages group status
		groupDb = new MulticastDb();

		log.info("Startup MULTICAST module");
	}

	@Override
	public void startUp(FloodlightModuleContext context) throws FloodlightModuleException {
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		restApiService.addRestletRoutable(new MulticastWebRoutable());
	}

	@Override
	public net.floodlightcontroller.core.IListener.Command receive(IOFSwitch sw, OFMessage msg,
			FloodlightContext cntx) {
			Ethernet eth = IFloodlightProviderService.bcStore.get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
			IPacket pkt = eth.getPayload();
			DatapathId dpid = sw.getId();
			OFPacketIn pi = (OFPacketIn) msg;
            OFPort inPort = pi.getMatch().get(MatchField.IN_PORT);

			if (! (eth.isMulticast()  && (pkt instanceof IPv4))) {
				return Command.CONTINUE;
			}

			IPv4 pktIPv4 = (IPv4) pkt;
			IPv4Address destAddr = pktIPv4.getDestinationAddress();
			IPv4Address hostAddr = pktIPv4.getSourceAddress();
			MacAddress hostMac = eth.getSourceMACAddress();
			
			log.info("IPv4 Multicast packet: \"" + hostAddr.toString() + "\" >> \"" + destAddr.toString() +"\"");

			if (destAddr.equals(IPv4Address.of("224.0.0.22"))) {
				ByteBuffer bbIGMP = ByteBuffer.wrap(pktIPv4.getPayload().serialize());
				byte igmpType = bbIGMP.get();
				short numRecords = bbIGMP.getShort(6);
				log.info("IGMP_MEMBERSHIP_REPORT_v3 numRecords: " + String.valueOf(numRecords));
				
			    switch(igmpType) {
					case 0x22:
						for (int i = 0; i<numRecords; i++) {
							byte groupRecordType = bbIGMP.get(i*8 + 8);
							IPv4Address groupAddr = IPv4Address.of(bbIGMP.getInt(i*8 + 12));
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
						log.warn(String.format("IGMP %02X not handled", igmpType));
						break;
				}

				return Command.STOP;
			}
			else if (destAddr.isMulticast() && (pkt.getPayload() instanceof UDP)) {
				UDP pktUDP = (UDP) pktIPv4.getPayload();
				int port =  pktUDP.getDestinationPort().getPort();
				
				if (! groupDb.checkHost(destAddr, hostAddr)) {
					log.warn("UDP_PACKET: Host " + hostAddr.toString() + " >> " +
				             destAddr.toString() + ":" + port + " error : not part of the group");
				}
				else {
					log.warn("UDP_PACKET: Host " + hostAddr.toString() + " >> " +
							destAddr.toString() + ":" + port + " info  : computing port list");
					List<OFPort> portList = getPorts(destAddr, dpid, inPort);
					if (portList.size() > 0) {
						newFlowMod(sw, pi, hostAddr, hostMac, destAddr, portList);
					}
				}
				return Command.STOP;
			}

			return Command.CONTINUE;
	}

	public  List<OFPort> getPorts (IPv4Address groupAddr, DatapathId dpid, OFPort port) {
		 List<OFPort> ports = new ArrayList<OFPort>();
		 MulticastGroup group = groupDb.getGroup(groupAddr);
		 	 
		 if (group != null) {
			 for (MCHost host : group.getHosts()) {
				 if (host.dpid == dpid) {
					 // add to the list if it's not sender port
					 if (port != host.port && !ports.contains(host.port))
						 ports.add(host.port);
				 }
				 else {
					 OFPort destPort = routingService.getPath(dpid, host.dpid).getPath().get(0).getPortId();
					 //log.info("first hop: " + path.getPath().get(0).getNodeId() + "#" + path.getPath().get(0).getPortId());
					 if ( port != destPort && !ports.contains(destPort))
					 	ports.add(destPort);
				 }
			 }
		 }
		 log.info("Port List: " + Arrays.toString(ports.toArray()));

		 return ports;
	}
	
	public void newFlowMod(IOFSwitch sw, OFPacketIn pi, IPv4Address host, MacAddress hostMac, IPv4Address dest, List<OFPort> ports) {
		if (ports == null || ports.size() == 0 ) {
			log.info("Can't add flow mod port is empty");
			return;
		}

		OFFlowAdd.Builder fmb = sw.getOFFactory().buildFlowAdd();
		
		fmb.setIdleTimeout(IDLE_TIMEOUT);
		fmb.setHardTimeout(HARD_TIMEOUT);
		fmb.setBufferId(OFBufferId.NO_BUFFER);
		fmb.setOutPort(OFPort.ANY);
		fmb.setCookie(U64.of(0));
		fmb.setPriority(FlowModUtils.PRIORITY_MAX);

		// Create the match structure  
		Match.Builder mb = sw.getOFFactory().buildMatch();
		mb.setExact(MatchField.ETH_TYPE, EthType.IPv4)
		.setExact(MatchField.IPV4_DST, dest)
		.setExact(MatchField.IPV4_SRC, host)
		.setExact(MatchField.ETH_SRC, hostMac);
				
		log.info("NEW FLOW MOD: match " + host + ":" + hostMac + " -> " + dest);
		
		OFActions actions = sw.getOFFactory().actions();
		ArrayList<OFAction> actionList = new ArrayList<OFAction>();	

		OFActionOutput output = null;
		if (ports != null)
			for (OFPort port : ports) {
				output = 
						actions.buildOutput().setMaxLen(0xFFffFFff)
						.setPort(port).build();
				actionList.add(output);
			}

		fmb.setActions(actionList);
		fmb.setMatch(mb.build());

		sw.write(fmb.build());
		
		returnPacketToSwitch(sw, pi, actionList);
	}
	
	public void returnPacketToSwitch(IOFSwitch sw, OFPacketIn pi, ArrayList<OFAction> actionList) {
		OFPacketOut.Builder po = sw.getOFFactory().buildPacketOut();
		po.setBufferId(pi.getBufferId());
		po.setInPort(OFPort.ANY);
		
		//OFPort inPort = pi.getMatch().get(MatchField.IN_PORT);	
		//OFActionOutput.Builder actionBuilder = sw.getOFFactory().actions().buildOutput();
		// set the same port of PacketIn
		//actionBuilder.setPort(inPort);
		po.setActions(actionList);
		
		// Packet might be buffered in the switch or encapsulated in Packet-In 
		if (pi.getBufferId() == OFBufferId.NO_BUFFER) {
			//Packet is encapsulated -> send it back
            byte[] packetData = pi.getData();
            po.setData(packetData);
		} 

		log.info("sending packetOut back to switch");
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
	public void setLifetime(int value) {
		// TODO STUB
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
