package net.floodlightcontroller.multicast.resources;

import java.util.Map;

import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;

import net.floodlightcontroller.multicast.IMulticastREST;

public class GroupsInfoResource extends ServerResource {
	@Get("json")
	public Map<String, Object> GroupInfo() {
		IMulticastREST mc = (IMulticastREST)getContext().getAttributes().get(IMulticastREST.class.getCanonicalName());
		return mc.getGroupsInfo();
	}
}
