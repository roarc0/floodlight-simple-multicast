package net.floodlightcontroller.multicast;

import org.restlet.Context;
import org.restlet.Restlet;
import org.restlet.routing.Router;

import net.floodlightcontroller.core.web.ControllerSummaryResource;
import net.floodlightcontroller.core.web.ControllerSwitchesResource;
import net.floodlightcontroller.core.web.LoadedModuleLoaderResource;
import net.floodlightcontroller.multicast.resources.*;
import net.floodlightcontroller.restserver.RestletRoutable;

public class MulticastWebRoutable implements RestletRoutable {

	@Override
	public Restlet getRestlet(Context context) {
		Router router = new Router(context);
		router.attach("/controller/summary/json", ControllerSummaryResource.class);
		router.attach("/module/loaded/json", LoadedModuleLoaderResource.class);
		router.attach("/controller/switches/json", ControllerSwitchesResource.class);

		router.attach("/groups_info/json", GroupsInfoResource.class);
		//router.attach("/group_info/json", GroupsInfoResource.class);
		router.attach("/group_create/json", GroupCreateResource.class);

		return router;
	}

	@Override
	public String basePath() {
		return "/mc";
	}
}
