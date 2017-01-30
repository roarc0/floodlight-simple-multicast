package net.floodlightcontroller.multicast.resources;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.restlet.resource.Post;
import org.restlet.resource.ServerResource;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import net.floodlightcontroller.multicast.IMulticastREST;

// resource used to return informations of a single group
public class GroupInfoResource extends ServerResource {
	@Post
	public Map<String, Object> Info(String json) {
		Map<String, Object> ret;
		ObjectMapper mapper = new ObjectMapper();
			try {
				JsonNode root = mapper.readTree(json);

				String addr = root.get("group_addr").asText();
				IMulticastREST mc = (IMulticastREST)getContext().getAttributes().get(IMulticastREST.class.getCanonicalName());
				ret = mc.getGroupInfo(addr);
			} 
			catch (IOException e) {
				e.printStackTrace();
				return new HashMap<String, Object>();
			}

			return ret;
	}
}