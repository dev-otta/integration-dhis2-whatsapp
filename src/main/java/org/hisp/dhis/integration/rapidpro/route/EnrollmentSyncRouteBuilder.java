package org.hisp.dhis.integration.rapidpro.route;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

@Component
public class EnrollmentSyncRouteBuilder extends AbstractRouteBuilder {

    @Override
    protected void doConfigure() throws Exception {
        from( "servlet:tasks/enrollmentSync?muteException=true" )
            .precondition( "{{sync.dhis2.enrollments}}" )
            .removeHeaders( "*" )
            .to("direct:enrollmentSync")
            .setHeader( Exchange.CONTENT_TYPE, constant( "application/json" ) )
            .setBody( constant( Map.of("status", "success", "data", "Synchronised RapidPro contacts with DHIS2 Enrollments") ) )
            .marshal().json();

        from( "quartz://enrollmentSync?cron={{sync.schedule.expression:0 0/30 * * * ?}}&stateful=true" )
            .precondition( "{{sync.dhis2.enrollments}}" )
            .to( "direct:enrollmentSync" );
        from("direct:enrollmentSync")
        .routeId("Enrollment Sync")
        .setHeader("CamelDhis2.queryParams",constant(Map.of("program","JA1vLYT8htI","orgUnit","DFn0SHhghQp")))
        .to("dhis2://get/resource?path=tracker/trackedEntities&fields=createdAt,trackedEntity,attributes&client=#dhis2Client")
        .log("${body}");
    }
    
}