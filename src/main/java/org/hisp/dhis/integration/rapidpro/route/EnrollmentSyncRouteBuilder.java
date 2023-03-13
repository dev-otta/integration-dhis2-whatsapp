package org.hisp.dhis.integration.rapidpro.route;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

@Component
public class EnrollmentSyncRouteBuilder extends AbstractRouteBuilder {

    @Override
    protected void doConfigure() throws Exception {
        Map<String, String> queryParams = new HashMap<String, String>();
        from("direct:enrollmentSync")
        .routeId("Enrollment Sync")
        .setHeader("CamelDhis2.queryParams",constant(Map.of("program","JA1vLYT8htI","orgUnit","DFn0SHhghQp")))
        .to("dhis2://get/resource?path=tracker/enrollments&client=#dhis2Client")
        //.marshal().json()
        .log("${body}");
    }
    
}